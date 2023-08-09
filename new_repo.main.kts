#!/usr/bin/env kotlin

import java.io.File

check(args.size == 1) {
  "new_repo.main.kts REPO_NAME"
}
val name = args[0]

val root = File(name)

check (!root.exists()) {
    "$root already exists"
}
root.mkdirs()

root.resolve("build.gradle.kts").writeText("""
  plugins {
    id("org.jetbrains.kotlin.jvm").version("1.9.0")
  }
""".trimIndent())

root.resolve("settings.gradle.kts").writeText("""
  pluginManagement {
    listOf(repositories, dependencyResolutionManagement.repositories).forEach {
      it.apply {
        mavenCentral()
        google()
      }
    }
  }
""".trimIndent())

root.resolve(".gitignore").writeText("""
  .idea
  .gradle
  build
""".trimIndent())

root.resolve("src/main/kotlin").apply {
    mkdirs()
    resolve("Main.kt").createNewFile()
}

ProcessBuilder()
    .command("git", "init")
    .directory(root)
    .inheritIO()
    .start()
    .waitFor()

ProcessBuilder()
    .command("git", "add", ".")
    .directory(root)
    .inheritIO()
    .start()
    .waitFor()

ProcessBuilder()
    .command("git", "commit", "-a", "-m", "initial commit")
    .directory(root)
    .inheritIO()
    .start()
    .waitFor()

println("All good, you can now go to the new directory:\ncd $name")