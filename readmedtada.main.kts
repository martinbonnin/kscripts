#!/usr/bin/env kotlin

@file:Repository("file:///Users/mbonnin/git/apollo-kotlin/build/localMaven")
@file:Repository("https://repo.maven.apache.org/maven2/")
@file:DependsOn("com.apollographql.apollo3:apollo-api-jvm:4.0.0-alpha.3-SNAPSHOT")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.6.2")

import kotlinx.metadata.Flag
import kotlinx.metadata.jvm.JvmFlag
import kotlinx.metadata.jvm.KotlinClassMetadata

val annotation = com.apollographql.apollo3.api.Query::class.java.annotations[0] as Metadata
val metadata = KotlinClassMetadata.read(annotation) as KotlinClassMetadata.Class
val kmClass = metadata.toKmClass()
println(kmClass.flags)
println("HAS_METHOD_BODIES" + JvmFlag.Class.HAS_METHOD_BODIES_IN_INTERFACE(kmClass.flags).toString())
println("IS_COMPILED_IN_COMPATIBILITY_MODE" + JvmFlag.Class.IS_COMPILED_IN_COMPATIBILITY_MODE(kmClass.flags).toString())

println(kmClass.versionRequirements.map { it.version.toString() })