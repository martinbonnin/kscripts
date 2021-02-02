#! /usr/bin/env kotlin

import java.io.File

File(args[0]).walk().filter {
    it.name.endsWith(".issues")
}.forEach {
    it.renameTo(File(it.parent, it.name.replace(".issues", ".expected")))
}
