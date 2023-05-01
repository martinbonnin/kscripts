#!/usr/bin/env kotlin

import java.io.*

val path = args[0]


val regex = Regex(".*major version: ([0-9]*).*")

File(path).walk().filter {
	it.extension == "class"
}.forEach { file ->

	runCommand("javap", "-v", file.absolutePath).split("\n").mapNotNull {
		//println(it)
		regex.matchEntire(it)?.groupValues?.get(1)
	}.forEach {
		println("$it: ${file.absolutePath}")
	}
}

println("done")

fun runCommand(vararg args: String): String {
	val builder = ProcessBuilder(*args)
		.redirectError(ProcessBuilder.Redirect.INHERIT)

	val process = builder.start()


	val output = process.inputStream.bufferedReader().readText()

	val thread = Thread {
		val ret = process.waitFor()
		if (ret != 0) {
			throw java.lang.Exception("command ${args.joinToString(" ")} failed:\n$output")
		}
	}

	thread.start()

	thread.join()

	return output.trim()
}