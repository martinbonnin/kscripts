@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.5.0")
@file:DependsOn("com.squareup.okhttp3:okhttp:4.10.0")
@file:DependsOn("net.mbonnin.bare-graphql:bare-graphql:0.0.2")

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request


val url = "https://sessionize.com/api/v2/72i2tw4v/view/All"

val jsonElement = Request.Builder()
    .get()
    .url(url)
    .build()
    .let {
        OkHttpClient()
            .newCall(it)
            .execute()
    }.let {
        Json.parseToJsonElement(it.body!!.string())
    }

val result: Map<String, Any?> = mapOf<String, Any?>(
    "sessions" to jsonElement.jsonObject.get("sessions")!!.jsonArray.map { 
        it.jsonObject.let { sessionizeSession ->
            mapOf(
                sessionizeSession.get("id")!!.jsonPrimitive.content to mapOf<String, Any?>(
                    "speakers" to sessionizeSession.get("speakers")!!.jsonArray.map { it.jsonPrimitive.content },
                    "tags" to emptyList(), // fix me
                    "title" to sessionizeSession.get("title")!!.jsonPrimitive.content,
                    "id" to sessionizeSession.get("id")!!.jsonPrimitive.content,

                    
                )
            )
        }
    }.ass
)