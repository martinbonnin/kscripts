#!/usr/bin/env kotlin

val builder = ProcessBuilder()
builder.command("git", "rev-list", "--all")
val process = builder.start()

val allRevs = process.inputStream.reader().readLines()

var i = 0
for (rev in allRevs) {
  print("\rRev $rev ($i/${allRevs.size})")
  System.out.flush()

  ProcessBuilder().command("git", "--no-pager", "grep", "-F",  "metadata", rev)
      .inheritIO()
      .start()
      .waitFor()
  i++
}
println("")
