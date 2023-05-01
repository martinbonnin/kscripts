#! /usr/bin/env kotlin
@file:DependsOn("com.squareup.okio:okio-jvm:3.3.0")

import okio.Buffer

val input = "Bonjour, en ce jour particulier, j'ai amené quelques viennoiseries. Venez vous servir. Marcellin"

val str = """42 6F 6E 6A 6F 75 72 2C 20 65 6E 20 63 65 20 6A 6F 75 72 20 70 61 72 74 69 63 75 6C 69 65 72 2C 20 6A 27 61 69 20 61 6D 65 6E 65 20 71 75 65 6C 71 75 65 73 20 76 69 65 6E 6E 6F 69 73 65 72 69 65 73 2E 20 56 65 6E 65 7A 20 76 6F 75 73 20 73 65 72 76 69 72 2E 20 4D 61 72 63 65 6C 6C 69 6E 20"""


//str.replace(" ", "").chunked(2).forEach {
//    print(it.toInt(16).toChar())
//}

Buffer().writeUtf8(input).readByteArray().forEach {
    print(String.format("%02x ", it.toInt().and(0xff)))
}

println()