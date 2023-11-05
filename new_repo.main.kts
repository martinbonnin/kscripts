#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains:annotations:24.0.0")

import org.intellij.lang.annotations.Language
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

@Language("kotlin")
val buildContents = """
  plugins {
    id("org.jetbrains.kotlin.multiplatform").version("1.9.20")
  }
  
  kotlin {
    jvm()
    
    sourceSets {
      getByName("jvmTest") {
        dependencies {
          implementation(kotlin("test"))
        }
      }
    }
  }
""".trimIndent()

root.resolve("build.gradle.kts").writeText(buildContents)

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

root.resolve("src/jvmTest/kotlin").apply {
    mkdirs()
    resolve("MainTest.kt").writeText("""
        import kotlin.test.Test
        
        class MainTest {
          @Test
          fun testStuff() {
          }
        }
    """.trimIndent())
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