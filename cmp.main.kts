#!/usr/bin/env kotlin

import java.io.File

fun check(dir1: File, dir2: File) {
  dir1.walk().forEach {
    println("checking $it")
    val file2 = dir2.resolve(it.relativeTo(dir1).path)
    check(file2.exists()) {
      "$file2 doesn't exist"
    }
  }
}

check(File(args[0]), File(args[1]))
check(File(args[1]), File(args[0]))

