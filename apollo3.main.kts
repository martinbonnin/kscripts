#! /usr/bin/env kotlin
import java.io.File

val root = File("/Users/mbonnin/git/apollo-android")

when (args.getOrNull(0)) {
  "folders" -> renameFolders()
  "text" -> replaceText()
  else -> throw IllegalStateException("migrate.main.kts [folders|text]")
}

fun renameFolders() {
  root.walk().onEnter {
    it.name != "build" && !it.name.startsWith(".")
  }.filter {
    it.name == "apollo"
  }.forEach {
    it.renameTo(File(it.parentFile, "apollo3"))
  }
}

fun replaceText() {
  val allFiles = root.walk().onEnter {
    it.name != "build" && !it.name.startsWith(".")
  }.filter {
    it.isFile
        && it.name != "migrate.main.kts"
        && (
        it.name.endsWith(".kt")
            || it.name.endsWith(".kts")
            || it.name.endsWith(".java")
            || it.name == "AndroidManifest.xml"
            || it.name == "dependencies.gradle"
            || it.name.endsWith(".properties")
            || it.name.endsWith(".template")
            || it.name.endsWith(".g4")
            || it.name.endsWith(".md")
            || it.name.endsWith(".mdx")
            || it.name.endsWith(".pbxproj")
        )
  }.toList()

  allFiles.forEachIndexed { index, file ->
    println("$index/${allFiles.size}: ${file.name}")
    /*var text = file.readText()
    text = Regex("com.apollographql.apollo([\n\\.])").replace(text, "package com.apollographql.apollo3$1")
    text = Regex("com.apollographql.apollo([\n\\.])").replace(text, "import com.apollographql.apollo3$1")*/
    file.writeText(
        file.readText()
          .replace("com.apollographql.apollo", "com.apollographql.apollo3")
          .replace("com/apollographql/apollo/", "com/apollographql/apollo3/")
    )
  }
}
