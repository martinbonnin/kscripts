#!/usr/bin/env kotlin

@file:DependsOn("com.apollographql.apollo3:apollo-ast:3.8.2")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.5.1")

@file:OptIn(ApolloExperimental::class)

import com.apollographql.apollo3.annotations.*
import com.apollographql.apollo3.ast.*
import com.apollographql.apollo3.ast.introspection.*
import kotlinx.serialization.json.*
import okio.*
import java.io.*

check(args.size == 1) {
	"count_used_fields.main.kts DIRECTORY"
}

val dir = File(args[0])

val schemaFile = dir.walk().filter { it.extension == "graphqls" || it.extension == "json" }
	.firstOrNull()

check(schemaFile != null) {
	"No schema found in ${dir.path}"
}

val schema = schemaFile!!.toSchema()

val usages = dir.walk().filter { it.extension == "graphql" }
	.map { it.source().buffer().parseAsGQLDocument().valueAssertNoErrors() }
	.flatMap {
		it.definitions.map {
			when (it) {
				is GQLOperationDefinition -> mapOf(it.name to it.fieldUsages().toSortedMap())
				is GQLFragmentDefinition -> mapOf(it.name to it.fieldUsages().toSortedMap())
				else -> error("Non-executable definition: $it")
			}
		}
	}.toList().toJsonElement()

println(usages.toString())

fun Any?.toJsonElement(): JsonElement = when (this) {
	is Map<*, *> -> JsonObject((this as Map<String, Any?>).mapValues { it.value.toJsonElement() })
	is List<*> -> JsonArray(map { it.toJsonElement() })
	is Boolean -> JsonPrimitive(this)
	is Number -> JsonPrimitive(this)
	is String -> JsonPrimitive(this)
	null -> JsonNull
	else -> error("cannot convert $this to JsonElement")
}

fun GQLOperationDefinition.fieldUsages(): Map<String, Int> {
	val usages = mutableMapOf<String, Int>()
	selectionSet.walk(usages, schema.rootTypeNameFor(operationType))

	return usages
}

fun Map<String, Int>.mergeWith(other: Map<String, Int>): MutableMap<String, Int> {
	val result = mutableMapOf<String, Int>()
	keys.plus(other.keys).forEach {
		result.put(it, (get(it) ?: 0) + (other[it] ?: 0))
	}
	return result
}

fun GQLFragmentDefinition.fieldUsages(): Map<String, Int> {
	val usages = mutableMapOf<String, Int>()
	selectionSet.walk(usages, schema.rootTypeNameFor(typeCondition.name))

	return usages
}

fun GQLSelectionSet.walk(usages: MutableMap<String, Int>, parentType: String) {
	selections.forEach {
		when (it) {
			is GQLField -> {
				val coordinate = "$parentType.${it.name}"
				val fieldDefinition =
					it.definitionFromScope(schema, parentType) ?: error("Cannot find type definition for $coordinate")
				usages.compute(coordinate) { k, v ->
					(v ?: 0).plus(1)
				}
				it.selectionSet?.walk(usages, fieldDefinition.type.rawType().name)
			}
			is GQLInlineFragment -> {
				it.selectionSet.walk(usages, it.typeCondition.name)
			}
			is GQLFragmentSpread -> {
				// fragment fields are read in fragment definitions
				Unit
			}
		}
	}
}