#!/usr/bin/env kotlin

import java.io.File
import kotlin.system.exitProcess

check(args.size == 2) {
  println("Usage: find-renames.main.kts <repoBefore> <repoAfter>")
  exitProcess(1)
}

val redirects = mapOf(
    "" to ""
)

val before = File(args[0])
val after = File(args[1])

fun classes(repo: File): Set<String> {
  return repo.walk().filter {
    it.isFile && it.parentFile.name == "api"
  }.filter {
    it.extension == "api"
  }.flatMap {
    it.findClasses()
  }.toSet()
}

fun File.findClasses(): Set<String> {
  return readLines().mapNotNull {
    val regex = Regex("[^ ].*class ([a-zA-Z0-9${'$'}/]*) [\\{:].*")
    val matchResult = regex.matchEntire(it)
    matchResult?.groupValues?.get(1).also {
      //println("Found class: $it")
    }
  }.toSet()
}

val beforeClasses = classes(before)
val afterClasses = classes(after)

val intersection = beforeClasses.intersect(afterClasses)

val beforeMap = (beforeClasses - intersection).associateBy { it.substringAfterLast("/") }
val afterMap = (afterClasses - intersection).associateBy { it.substringAfterLast("/") }


beforeMap.forEach { className, fqcn ->
  println("`${fqcn.prettify()}` => `${afterMap[className]?.prettify()}`")
}

fun String.prettify() = replace("/", ".").replace("${'$'}", ".")