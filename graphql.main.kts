#!/usr/bin/env kotlin

@file:DependsOn("net.mbonnin.bare-graphql:bare-graphql:0.0.2")
@file:DependsOn("com.squareup.okhttp3:logging-interceptor:4.10.0")
@file:DependsOn("com.apollographql.apollo3:apollo-ast:3.7.5")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:3.5.2")

import com.apollographql.apollo3.ast.GQLOperationDefinition
import com.apollographql.apollo3.ast.parseAsGQLDocument
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import net.mbonnin.bare.graphql.toJsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import okio.Buffer
import okio.buffer
import okio.sink
import java.io.File


fun String.extensions(): Map<String, Any> = mapOf(
    "persistedQuery" to mapOf(
        "version" to 1,
        "sha256Hash" to Buffer().writeUtf8(this).readByteString().sha256().hex()
    )
)

val variables = emptyMap<String, Any>()


fun String.operationName(): String {
    return Buffer().writeUtf8(this).parseAsGQLDocument()
        .valueAssertNoErrors()
        .definitions
        .filterIsInstance<GQLOperationDefinition>()
        .single()
        .name ?: error("Cannot find operationName")
}

fun hashedRequest(url: String, operation: String): Request {
    return Request.Builder()
        .get()
        .url(
            url.toHttpUrl().newBuilder()
                .addQueryParameter("operationName", operation.operationName())
                .addQueryParameter("extensions", operation.extensions().toJsonElement().toString())
                .build()
        )
        .build()
}

fun fullRequest(url: String, operation: String): Request {
    return Request.Builder()
        .post(
            mapOf(
                "query" to operation,
                "extensions" to operation.extensions(),
                "variables" to variables,
                "operationName" to operation.operationName()
            ).toJsonElement()
                .toString()
                .toRequestBody("application/json".toMediaType())
        )
        .url(url)
        .build()
}

val command = object : CliktCommand() {
    val hashed by option().flag()
    val operation by argument()
    val header by option().multiple()
    val url by option().default("https://confetti-app.dev/graphql")
    val logLevel by option().choice(
        "body" to HttpLoggingInterceptor.Level.BODY,
        "headers" to HttpLoggingInterceptor.Level.HEADERS,
        "basic" to HttpLoggingInterceptor.Level.BASIC
    ).default(HttpLoggingInterceptor.Level.HEADERS)
    val out by option()

    override fun run() {
        val query = File(operation).readText()
        val request = if (hashed) {
            hashedRequest(url, query)
        } else {
            fullRequest(url, query)
        }
        val response = request.newBuilder()
            .apply {
                header.forEach {
                    val c = it.split(":").map { it.trim() }
                    addHeader(c[0], c[1])
                }
            }
            .build()
            .let {
                val client = OkHttpClient.Builder()
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = logLevel
                    })
                    .build()

                client.newCall(it).execute()
            }

        if (out != null) {
            response.body?.source()?.use {source ->
                File(out).sink().buffer().use {
                    it.writeAll(source)
                }
            }
        }
        response.isSuccessful
    }
}

command.main(args)
