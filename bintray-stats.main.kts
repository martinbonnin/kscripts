#!/usr/bin/env kotlin

@file:DependsOn("com.opencsv:opencsv:4.1")

import java.io.File

val outputDir = args[0]

val lines = File(outputDir).walk().filter { it.isFile && it.name.endsWith(".csv") }
    .toList()
    .flatMap {
      val reader = com.opencsv.CSVReader(it.bufferedReader())
      reader.readAll().map {
        it[10]
      }
    }

lines.mapNotNull { artifact ->
  val regex = Regex(".*/com/apollographql/apollo/(.*)/(.*)/(.*)")
  val matchResult = regex.matchEntire(artifact)
  if (matchResult == null) {
    println("$artifact does not match")
    null
  } else {
    val artifactId = matchResult.groupValues[1]
    val version = matchResult.groupValues[2]
    artifactId to version
  }
}.groupBy { it.first }
    .entries
    .sortedByDescending { it.value.size }
    .forEach {
      println("${it.key}, ${it.value.size}")
    }