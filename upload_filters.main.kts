#!/usr/bin/env kotlin

@file:DependsOn("com.squareup.okio:okio-jvm:3.6.0")

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.buffer
import okio.sink
import okio.source
import java.io.File
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import kotlin.system.exitProcess


/**
 * Takes a sieve filter file as input and:
 * - connects to the mailbox.org IMAP server on port 993 and creates a folder for detected folder in the rules
 * - connects to the mailbox.org MANAGESIEVE server on port 4190, uploads the rules and mark them as active
 *
 * References
 * - SIEVE protocol: https://datatracker.ietf.org/doc/html/rfc5804
 * - IMAP protocol: https://datatracker.ietf.org/doc/html/rfc3501
 */

val username = System.getenv("MAILBOX_USERNAME") ?: error("add MAILBOX_USERNAME to your env")
val password = System.getenv("MAILBOX_PASSWORD") ?: error("add MAILBOX_PASSWORD to your env")

val sslFactory = (SSLSocketFactory.getDefault() as SSLSocketFactory)

val filterPath = "/Users/mbonnin/git/notes/email/sieve_filters" // args.getOrNull(0) ?: error("upload_filters.main.kts [FILTER_FILE]")
val filterFile = File(filterPath)
check (filterFile.isFile) {
  "$filterPath does not exist"
}

//createImapFolders()
setSieveFilter()


fun createImapFolders() {
  val folders = filterFile.readLines()
      .mapNotNull { Regex(".*fileinto \"(.*)\";").matchEntire(it) }
      .map { it.groupValues[1] }

  var index = 0
  val socket = sslFactory.createSocket("imap.mailbox.org", 993)

  socket.getInputStream().source().buffer().use { source ->
    socket.getOutputStream().sink().buffer().use { sink ->
      source.awaitLine { it.startsWith("* OK")}

      sink.sendCommand("a$index LOGIN $username $password")
      source.awaitLine { it.startsWith("a$index OK")}
      index++

      folders.forEach {
        sink.sendCommand("a$index CREATE $it")
        source.awaitLine { it.startsWith("a$index OK") || it.startsWith("a$index NO [ALREADYEXISTS]")}
        index++
      }
    }
  }
}


fun setSieveFilter() {
  val socket = Socket("imap.mailbox.org", 4190)

  socket.getInputStream().source().buffer().use { source ->
    socket.getOutputStream().sink().buffer().use { sink ->
      source.awaitLine("OK \"Dovecot ready.\"")

      sink.sendCommand("STARTTLS")

      source.awaitLine("OK \"Begin TLS negotiation now.\"")

      val tlsSocket = sslFactory.createSocket(
          socket,
          socket.inetAddress.hostAddress,
          socket.port,
          false) as SSLSocket

      tlsSocket.inputStream.source().buffer().use { tlsSource ->
        tlsSocket.outputStream.sink().buffer().use { tlsSink ->
          uploadFilters(tlsSource, tlsSink, filterFile)
        }
      }
    }
  }
}

fun uploadFilters(source: BufferedSource, sink: BufferedSink, filterFile: File) {
  val credentials = Buffer().writeUtf8CodePoint(0)
      .writeUtf8(username)
      .writeUtf8CodePoint(0)
      .writeUtf8(password)
      .readByteString()
      .base64()

  source.awaitLine("OK \"TLS negotiation successful.\"")

  sink.sendCommand("AUTHENTICATE \"PLAIN\" \"$credentials\"")

  source.awaitLine("OK \"Logged in.\"")

  val script = filterFile.readBytes()

  sink.sendCommand("PUTSCRIPT \"martin\" {${script.size}+}")
  println(">${script.decodeToString()}")
  sink.write(script).flush()
  sink.sendCommand("")

  source.awaitLine("OK \"PUTSCRIPT completed.\"")

  sink.sendCommand("SETACTIVE \"martin\"")
  makeInteractive(source, sink)
}

fun BufferedSink.sendCommand(command: String) {
  println("> $command")
  writeUtf8("$command\r\n").flush()
}

fun BufferedSource.awaitLine(expected: String) = awaitLine { it == expected }

fun BufferedSource.awaitLine(predicate: (String) -> Boolean) {
  while (true) {
    val line = readUtf8Line()
    println(line)
    if (line == null) {
      println("Connection closed.")
      exitProcess(1)
    } else if (predicate(line)) {
      break
    }
  }
}
fun makeInteractive(source: BufferedSource, sink: BufferedSink) {
  println("the script is now interactive")
  Thread {
    source.awaitLine("world peace")
  }.start()

  while (true) {
    readLine()?.let {
      sink.sendCommand(it)
    }
  }
}