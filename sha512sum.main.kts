#! /usr/bin/env kotlin
@file:DependsOn("com.squareup.okio:okio-jvm:3.3.0")

import okio.buffer
import okio.source
import java.io.File


println(File(args[0]).source().buffer().readByteString().sha512().base64())

// "sha512-FVCV2//UVo1qJ3Kg6kkHLe0Hg+IJhjrGa+aYHh8xD4KmwbbjthIzvaAcCJsQgA43+k+6u7HqORKXMyMt82Srfw=="