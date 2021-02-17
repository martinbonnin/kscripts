#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.okhttp3:okhttp:4.9.0")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:3.1.0")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import okhttp3.OkHttpClient
import okhttp3.Request

MainCommand().main(args)
class MainCommand: CliktCommand() {
  val version by option().required()
  val repositoryId by option().required()

  override fun run() {
    val jcenter = getFile("https://jcenter.bintray.com/com/apollographql/apollo/apollo-api/$version/apollo-api-$version.jar")
    val maven = getFile("https://oss.sonatype.org/content/repositories/comapollographql-$repositoryId/com/apollographql/apollo/apollo-api/$version/apollo-api-$version.jar")

    check(jcenter.size == maven.size) {
      "Different sizes: ${jcenter.size} != ${maven.size}"
    }

    for (i in jcenter.indices) {
      check(jcenter[i] == maven[i])
    }
    println("Files are identical")
  }

  private fun getFile(url: String): ByteArray {
    return Request.Builder().get().url(url).build().let {
      OkHttpClient().newCall(it).execute()
    }.let {
      check(it.isSuccessful) {
        "HTTP error on $url: ${it.body?.string()}"
      }
      it.body!!.bytes()
    }
  }

}