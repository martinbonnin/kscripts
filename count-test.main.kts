#! /usr/bin/env kotlin
@file:DependsOn("net.mbonnin.xoxo:xoxo:0.3")

import xoxo.toXmlDocument
import java.io.File

File(".").walk().filter { it.name == "test-results" && it.isDirectory }
    .toList()
    .flatMap {
      it.walk().filter { it.isFile && it.extension == "xml" }.toList()
    }
    .map {
      val attributes = it.toXmlDocument()
          .root
          .attributes

      //println(it.name)
      println("${attributes["name"]}: ${attributes["tests"]}")
      attributes["tests"]!!.toInt()
    }
    .sum()

