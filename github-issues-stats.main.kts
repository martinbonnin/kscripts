#! /usr/bin/env kotlin

@file:DependsOn("com.squareup.okhttp3:okhttp:4.9.0")
@file:DependsOn("com.squareup.moshi:moshi-kotlin:1.11.0")
@file:DependsOn("com.squareup.okhttp3:logging-interceptor:4.9.0")

import com.squareup.moshi.Moshi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.text.SimpleDateFormat
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.hours

val token = System.getenv("GITHUB_TOKEN") ?: throw IllegalArgumentException("please set the GITHUB_TOKEN env variable")
val (owner, repoName) = args.getOrNull(0)?.split("/")?.let { it[0] to it[1] }
    ?: throw IllegalArgumentException("usage: github-issues-stats.main.kts [owner/repoName]")

val okHttpClient = OkHttpClient.Builder().addInterceptor(HttpLoggingInterceptor().apply {
  //level = HttpLoggingInterceptor.Level.BODY
}).build()

val issuesFile = File("issues.json")

val adapter = Moshi.Builder().build().adapter(Any::class.java)!!

if (issuesFile.exists()) {
  println("reusing ${issuesFile.path}")
} else {
  var i = 0
  var after: String? = null
  val issues = mutableListOf<Any>()

  while (true) {
    println("Getting page $i")
    val query = """
            query { 
              repository(name: "$repoName", owner: "$owner") {
                issues(first: 100, after: $after) {
                  nodes {
                    title
                    publishedAt
                    closedAt
                    number
                    author {
                      login
                    } 
                    comments(first: 20) {
                      nodes {
                        publishedAt
                        author {
                          login
                        } 
                      }
                    }
                  }
                  pageInfo {
                    endCursor
                    hasNextPage
                  }
                }
              }
            }
        """.trimIndent()

    val body = mapOf("query" to query, "variables" to emptyMap<String, String>()).let {
      adapter.toJson(it)
    }
    println(body)
    val response = body.toRequestBody("application/json".toMediaType()).let {
      Request.Builder()
          .url("https://api.github.com/graphql")
          .header("Authorization", "bearer $token")
          .post(it)
          .build()
    }.let {
      okHttpClient.newCall(it).execute()
    }

    if (!response.isSuccessful || response.body == null) {
      throw IOException("Cannot get issues after '$after' (${response.code}): ${response.body?.string()}")
    }

    val root = adapter.fromJson(response.body!!.string())

    issues.addAll(root!!.getObject("data")!!.getObject("repository")!!.getObject("issues")!!.getList("nodes")!!)
    i++
    if (root.getObject("data")!!.getObject("repository")!!.getObject("issues")!!.getObject("pageInfo")!!
            .getBoolean("hasNextPage")!!
    ) {
      after = root.getObject("data")!!.getObject("repository")!!.getObject("issues")!!.getObject("pageInfo")!!
          .getString("endCursor").let { "\"$it\"" }
    } else {
      break
    }
  }

  issuesFile.writeText(adapter.toJson(issues))
}

val issues = adapter.fromJson(issuesFile.readText()) as List<Any>


fun forEachIssue(from: String, to: String, block: (Any) -> Unit) {
  val rangeStart = from.toDate().time
  val rangeEnd = to.toDate().time

  issues.forEach { issue ->
    val publishedAt = issue.getString("publishedAt")!!.toDate().time
    if (publishedAt in rangeStart..rangeEnd) {
      block(issue)
    }
  }
}

fun responseTime() {
  var opened = 0
  var closed = 0
  val responseTimes = mutableListOf<Long>()

  val rangeStart = "2020-07-01T00:00:00Z".toDate().time
  val rangeEnd = "2020-12-31T23:59:59Z".toDate().time
  issues.forEach { issue ->
    val publishedAt = issue.getString("publishedAt")!!.toDate().time
    if (publishedAt in rangeStart..rangeEnd) {
      opened++
      val commentPublishedAt = issue.getObject("comments")!!.getList("nodes")!!.firstOrNull { comment ->
        comment.getObject("author")!!.getObject("login") == "martinbonnin"
      }?.getString("publishedAt")

      if (commentPublishedAt != null && issue.getObject("author")!!.getObject("login") != "martinbonnin") {
        val responseTime = (commentPublishedAt.toDate().time - publishedAt) / 1000 / 3600
        println(
            String.format(
                "%4d, %8d, %s",
                (issue.getObject("number") as Number).toInt(),
                responseTime,
                issue.getObject("title")
            )
        )
        responseTimes.add(responseTime)

      }
    }
    val closedAt = issue.getString("closedAt")?.toDate()?.time
    if (closedAt != null && closedAt in rangeStart..rangeEnd) {
      closed++
    }
  }

  println("$opened issues opened")
  println("$closed issues closed")

  @OptIn(ExperimentalTime::class)
  val avg = responseTimes.average()
  println("${responseTimes.size} issues answered. Average response time: $avg")
  println("${responseTimes.filter { it <= 1 }.size} issues answered in less than 2 hours")
}

fun mostIssuesOpened() {
  val openers = mutableMapOf<String, Int>()
  val commenters = mutableMapOf<String, Int>()
  issues.forEach {
    val author = it.getObject("author")?.getString("login")
    if (author != null) {
      val value = openers.get(author) ?: 0
      openers.put(author, value + 1)
    } else {
      println("no opener for ${it.getString("title")}")
    }

    it.getObject("comments")?.getList("nodes")?.forEach {
      val commenter = it.getObject("author")?.getString("login")
      if (commenter != null) {
        val value = commenters.get(commenter) ?: 0
        commenters.put(commenter, value + 1)
      }
    }
  }


  File("openers").writeText(buildString {
    openers.entries.sortedByDescending { it.value }.forEach {
      appendLine(String.format("%20s: %d", it.key, it.value))
    }
  })
  File("commenters").writeText(buildString {
    commenters.entries.sortedByDescending { it.value }.forEach {
      appendLine(String.format("%20s: %d", it.key, it.value))
    }
  })

  println("total issues processed: ${issues.size}.")
  println("total openers: ${openers.values.sum()}.")
  println("total different openers: ${openers.keys.size}.")
}

mostIssuesOpened()

fun String.toDate() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(
    this
        .replace("T", " ")
        .replace("Z", "")
)

fun Any.getString(key: String) = (this as Map<*, *>).get(key) as String?
fun Any.getBoolean(key: String) = (this as Map<*, *>).get(key) as Boolean?
fun Any.getObject(key: String) = (this as Map<*, *>).get(key) as Any?
fun Any.getList(key: String) = (this as Map<*, *>).get(key) as List<Any>?
