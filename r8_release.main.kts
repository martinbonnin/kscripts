#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.okhttp3:okhttp:4.9.0")
@file:DependsOn("eu.jrie.jetbrains:kotlin-shell-core:0.2.1")
@file:DependsOn("org.slf4j:slf4j-simple:1.7.32")
@file:DependsOn("com.github.ajalt.clikt:clikt-jvm:3.2.0")
@file:DependsOn("net.mbonnin.vespene:vespene-lib:0.5")
@file:DependsOn("com.squareup.retrofit2:converter-moshi:2.9.0")
@file:DependsOn("org.bouncycastle:bcprov-jdk15on:1.64")
@file:DependsOn("org.bouncycastle:bcpg-jdk15on:1.64")
@file:Suppress("EXPERIMENTAL_API_USAGE")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import eu.jrie.jetbrains.kotlinshell.processes.process.Process
import eu.jrie.jetbrains.kotlinshell.shell.shell
import kotlinx.coroutines.runBlocking
import net.mbonnin.vespene.lib.NexusStagingClient
import net.mbonnin.vespene.lib.md5
import net.mbonnin.vespene.sign
import okio.buffer
import okio.source
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

check(File("tools/create_maven_release.py").exists()) {
  "r8_release.main.kts needs to be run from the r8 root"
}

listOf(
    "GPG_PRIVATE_KEY",
    "GPG_PRIVATE_KEY_PASSWORD",
    "SONATYPE_NEXUS_USERNAME",
    "SONATYPE_NEXUS_PASSWORD",
    "NET_MBONNIN_PROFILE_ID"
).forEach {
  check(System.getenv(it) != null) { "$it is missing from env" }
}
//System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");

object : CliktCommand() {
  val local by option(help = "Deploy to maven local").flag()
  val version by option().help("The version to use in the published artifacts").required()
  val versionToOverWrite by option().help("The version produced by the R8 scripts. If you're on a tag, this shouldn't be required")


  override fun run() {
    withFixedFile(".zip") { releaseFile ->
      withFixedDir { tmpDir ->
        makeRelease(tmpDir, releaseFile, version, local)
      }
    }
  }

  private fun Process.throwOnError() {
    if (this.pcb.exitCode != 0) {
      throw Exception("${this.name} failed with status ${this.pcb.exitCode}")
    }
  }

  private fun makeRelease(tmpDir: File, releaseFile: File, version: String, local: Boolean) {
    shell {
      "tools/create_maven_release.py --out ${releaseFile.absolutePath}"().throwOnError()
      "unzip -d ${tmpDir.absolutePath} ${releaseFile.absolutePath}"().throwOnError()
    }

    val actualVersion = versionToOverWrite ?: version
    tmpDir.walk(direction = FileWalkDirection.BOTTOM_UP).forEach {
      if (it.name.contains(actualVersion)) {
        val newName = it.name.replace(actualVersion, version)
        it.renameTo(File(it.parentFile, newName))
      }
    }
    tmpDir.walk(direction = FileWalkDirection.BOTTOM_UP).forEach {
      if (it.extension == "pom") {
        it.writeText(it.readText()
            .replace(actualVersion, version)
            .replace("<groupId>com.android.tools</groupId>", "<groupId>net.mbonnin.r8</groupId>")
        )
      }
    }
    File(tmpDir, "net/mbonnin/r8").mkdirs()
    File(tmpDir, "com/android/tools/r8").copyRecursively(File(tmpDir, "net/mbonnin/r8/r8"), overwrite = true)
    File(tmpDir, "com").deleteRecursively()

    val sourcesJar = File(tmpDir, "net/mbonnin/r8/r8/$version/r8-$version-sources.jar")
    val javadocJar = File(tmpDir, "net/mbonnin/r8/r8/$version/r8-$version-javadoc.jar")
    createEmptyZip(sourcesJar)
    createEmptyZip(javadocJar)


    if (local) {
      val mavenLocal = File("${System.getenv("HOME")}/.m2/")
      mavenLocal.mkdirs()
      tmpDir.copyRecursively(File(mavenLocal, "repository"), overwrite = true)
    } else {
      val privateKey = System.getenv("GPG_PRIVATE_KEY") ?: throw IllegalArgumentException("Please specify GPG_PRIVATE_KEY")
      val privateKeyPassword = System.getenv("GPG_PRIVATE_KEY_PASSWORD")
          ?: throw IllegalArgumentException("Please specify GPG_PRIVATE_KEY_PASSWORD")

      val (artifacs, other) = File(tmpDir, "net/mbonnin/r8/r8/$version/").listFiles()!!.filter {
        it.isFile
      }.partition { it.extension in listOf("jar", "pom") }

      other.forEach { it.delete() }
      artifacs.forEach { file ->
        File(file.absolutePath + ".md5").writeText(file.source().buffer().md5())
        File(file.absolutePath + ".asc").writeText(file.source().buffer().sign(privateKey, privateKeyPassword))
      }

      val nexusClient = NexusStagingClient(
          username = System.getenv("SONATYPE_NEXUS_USERNAME") ?: error("Specify SONATYPE_NEXUS_USERNAME or --local to publish locally"),
          password = System.getenv("SONATYPE_NEXUS_PASSWORD") ?: error("Specify SONATYPE_NEXUS_PASSWORD or --local to publish locally")
      )

      tmpDir.walk()
          .filter {
            it.isFile
          }
          .toList()
          .forEach {
            println("preparing to upload $it")
          }

      runBlocking {
        println("creating staging repo...")
        val repositoryId = nexusClient.upload(
            directory = tmpDir,
            profileId = System.getenv("NET_MBONNIN_PROFILE_ID") ?: error("Specify NET_MBONNIN_PROFILE_ID or --local to publish locally"),
            comment = "R8 mirror $version"
        ) { cur, total, _ ->
          print("\ruploading $cur / $total")
        }

        println("")
        println("Closing...")
        nexusClient.closeRepositories(listOf(repositoryId))
      }
    }
    println("done")
    // Force exit to kill the OkHttp threadpools
    exitProcess(0)
  }
}.main(args)


fun withTmpDir(block: (File) -> Unit) {
  val tmpDir = Files.createTempDirectory("r8-release").toFile()
  try {
    block(tmpDir)
  } finally {
    tmpDir.deleteRecursively()
  }
}

fun withTmpFile(suffix: String? = null, block: (File) -> Unit) {
  val tmpFile = File.createTempFile("r8-release", suffix)
  try {
    block(tmpFile)
  } finally {
    tmpFile.deleteRecursively()
  }
}

fun withFixedDir(block: (File) -> Unit) {
  val tmpDir = File("/Users/mbonnin/tmp")
  tmpDir.deleteRecursively()
  block(tmpDir)
}

fun withFixedFile(suffix: String? = null, block: (File) -> Unit) {
  val tmpFile = File("/Users/mbonnin/r8$suffix")
  block(tmpFile)
}

fun createEmptyZip(dst: File) {
  val zipOutStream = ZipOutputStream(dst.outputStream())
  zipOutStream.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
  zipOutStream.write("Manifest-Version: 1.0\n\n".toByteArray())
  zipOutStream.closeEntry()
  zipOutStream.close()
}