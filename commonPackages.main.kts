#!/usr/bin/env kotlin
import java.io.File
import kotlin.system.exitProcess


class Node(val value: Char) {
  val children = mutableListOf<Node>()
  var isEnd = false
}

class Trie {
  val root = Node('$')

  fun put(str: String) {
    var node = root
    str.forEach { char ->
      var child = node.children.find { it.value == char }
      if (child == null) {
        child = Node(char)
        node.children.add(child)
      }
      node = child
    }
    node.isEnd = true
  }
}

val trie = Trie()

if (args.isEmpty()) {
  println("directory required")
  exitProcess(1)
}

val dir  = args[0]
val classPaths = File(dir).walk().filter { it.isFile }.map {
  it.parentFile.relativeTo(File(dir)).path.replace("/", ".")
}.distinct().sorted().forEach {
  println(it)
  trie.put(it)
}

val commonPrefixes = trie.root.children.map { rootNode ->
  val builder = StringBuilder()

  var node = rootNode

  while (true) {
    if (node.isEnd) {
      break
    }
    builder.append(node.value)
    node = node.children.first()
  }
  builder.append(node.value)
  builder.toString()
}

println(commonPrefixes.joinToString("\n"))
