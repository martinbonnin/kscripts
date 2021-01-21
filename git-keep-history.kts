#!/usr/bin/env kscript

import java.io.File
import java.nio.file.CopyOption
import java.nio.file.Files
import java.nio.file.StandardCopyOption

fun run(workingDir: File = File("."), vararg args: String) {
    ProcessBuilder().command(*args)
        .directory(workingDir)
        .inheritIO()
        .start()
        .waitFor()
        .let {
            require(it == 0) {
                "Command ${args.joinToString(",")} failed with status code: $it"
            }
        }
}

fun FileTreeWalk.relative(base: File): Sequence<String> {
    return filter {
        it.isFile && !Files.isSymbolicLink(it.toPath())
    }.map {
        it.relativeTo(base).path
    }
        .filter {
            if (it.startsWith(".idea")) {
                return@filter false
            }
            if (it.startsWith(".git")) {
                return@filter false
            }

            true
        }
}

val tmpDir = File("tmp")
tmpDir.deleteRecursively()
tmpDir.mkdirs()

val beforeDir = File(tmpDir, "before")
val afterDir = File(tmpDir, "after")

run(tmpDir, "git", "clone", "--depth", "10", "-b", "convert-top-level-api-kotlin", "https://github.com/sav007/apollo-android", beforeDir.name)
run(beforeDir, "git", "checkout", "-b", "convert-to-kt-keep-history", "15ade55fe3823c16ef4de5fa7e816ed8389d5d74")

run(tmpDir, "git", "clone", "--depth", "1", "-b", "convert-top-level-api-kotlin", "https://github.com/sav007/apollo-android", afterDir.name)

val movedFiles = beforeDir.walk()
    .relative(beforeDir)
    .filter {
        val after = File(afterDir, it)
        !after.exists()
    }

println("movedFiles: ${movedFiles.joinToString("\n")}")

movedFiles.forEach {
    val newName = it.replace(".java", ".kt")
    run(beforeDir, "git", "mv", it, newName)
}

run(beforeDir, "git", "config", "user.name", "Martin Bonnin")
run(beforeDir, "git", "config", "user.email", "martin@mbonnin.net")

run(beforeDir, "git", "commit", "-a", "-m", "moving java files to kotlin files without changing content so that git can follow the history")

/**
 * copy all the changes
 */
afterDir.walk()
    .relative(afterDir)
    .forEach {
      println("copy $it")
        Files.copy(File(afterDir, it).toPath(), File(beforeDir, it).toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING)
    }

run(beforeDir, "git", "commit", "-a", "-m", "actually convert to kotlin")
