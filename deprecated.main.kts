#!/usr/bin/env kotlin

@file:DependsOn("com.apollographql.apollo3:apollo-ast:4.0.0-alpha.2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.5.1")

@file:OptIn(ApolloExperimental::class)

import com.apollographql.apollo3.annotations.*
import com.apollographql.apollo3.ast.*
import com.apollographql.apollo3.ast.introspection.*
import kotlinx.serialization.json.*
import okio.*
import java.io.*
import kotlin.system.exitProcess

check(args.size == 1) {
    "./deprecated.main.kts DIRECTORY"
}

val dir = File(args[0])

val schemaFile = dir.walk().filter { it.extension == "graphqls" || it.extension == "json" }
    .firstOrNull()

check(schemaFile != null) {
    "No schema found in ${dir.path}"
}

val schema = schemaFile!!.toSchema()

val operations = mutableListOf<GQLOperationDefinition>()
val fragments = mutableListOf<GQLFragmentDefinition>()

dir.walk().filter { it.extension == "graphql" }
    .map { it.source().buffer().parseAsGQLDocument(it.path).getOrThrow() }
    .forEach {
        it.definitions.forEach {
            when (it) {
                is GQLOperationDefinition -> operations.add(it)
                is GQLFragmentDefinition -> fragments.add(it)
                else -> error("Non-executable definition: $it")
            }
        }
    }

val usages = operations.flatMap {
    it.validate(schema, fragments.associateBy { it.name })
        .filter { it is Issue.DeprecatedUsage }
}

fun Issue.pretty(): String {
    return "${sourceLocation.pretty()}: $message"
}

if (usages.isNotEmpty()) {
    println("deprecated usage(s) found:")
    println(usages.joinToString("\n") { it.pretty() })
    exitProcess(1)
} else {
    exitProcess(0)
}
