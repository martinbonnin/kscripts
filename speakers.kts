#!/usr/bin/env kscript

@file:DependsOn("com.github.ajalt:clikt:2.5.0")

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file


object Merge: CliktCommand() {
  val files by argument(help = "files containing the emails to merge. Either one email by line or coma separated").file(mustExist = true).multiple()
  override fun run() {
    val emailsList = files.map {
      it.readText()
          .lines()
          .flatMap { it.split(",") }
          .map { it.trim() }
    }
    val set = mutableSetOf<String>()

    emailsList.forEach {
      set.addAll(it)
    }

    files.forEachIndexed { index, file ->
      println("${file.name} contains ${emailsList[index].size} speakers")
    }
    println("unique speakers: ${set.size}")
    println(set.joinToString(","))

    val diff = (set - emailsList[1].toSet())
    println("new speakers: ${diff.size}")
    println(diff.joinToString(","))
  }
}

object: CliktCommand() {
  override fun run() {
  }
}.subcommands(Merge)
    .main(args)

