package com.soywiz.korio.serialization.json

import com.soywiz.korio.*
import com.soywiz.korio.error.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.serialization.*
import com.soywiz.korio.util.*
import kotlin.collections.Iterable
import kotlin.collections.LinkedHashMap
import kotlin.collections.Map
import kotlin.collections.arrayListOf
import kotlin.collections.plusAssign
import kotlin.collections.set
import kotlin.collections.toList
import kotlin.collections.withIndex
import kotlin.math.*
import kotlin.reflect.*

object Json {
	inline fun <reified T : Any> encodePretty(obj: T, mapper: ObjectMapper = Mapper) = stringifyPretty<T>(obj, mapper)
	inline fun <reified T : Any> encode(obj: T?, mapper: ObjectMapper = Mapper, pretty: Boolean = false): String =
		stringify(obj, mapper, pretty)

	inline fun <reified T : Any> stringifyPretty(obj: T, mapper: ObjectMapper = Mapper) =
		stringify<T>(obj, mapper, pretty = true)

	inline fun <reified T : Any> stringify(obj: T?, mapper: ObjectMapper = Mapper, pretty: Boolean = false): String {
		return if (pretty) {
			encodePrettyUntyped(mapper.toUntyped(T::class, obj))
		} else {
			encodeUntyped(mapper.toUntyped(T::class, obj))
		}
	}

	fun parse(@Language("json") s: String): Any? = StrReader(s).decode()
	inline fun <reified T : Any> parseTyped(@Language("json") s: String, mapper: ObjectMapper = Mapper): T =
		decodeToType(T::class, s, mapper)

	fun invalidJson(msg: String = "Invalid JSON"): Nothing = throw com.soywiz.korio.IOException(msg)

	fun decode(@Language("json") s: String): Any? = StrReader(s).decode()

	@Deprecated("Not compatible with Kotlin.JS (for now)")
	inline fun <reified T : Any> decodeToType(@Language("json") s: String, mapper: ObjectMapper = Mapper): T =
		decodeToType(T::class, s, mapper)

	@Suppress("UNCHECKED_CAST")
	@Deprecated("Put class first")
	fun <T : Any> decodeToType(@Language("json") s: String, clazz: KClass<T>, mapper: ObjectMapper = Mapper): T =
		mapper.toTyped(clazz, decode(s))

	@Suppress("UNCHECKED_CAST")
	fun <T : Any> decodeToType(clazz: KClass<T>, @Language("json") s: String, mapper: ObjectMapper = Mapper): T =
		mapper.toTyped(clazz, decode(s))

	fun StrReader.decode(): Any? {
		val ic = skipSpaces().read()
		when (ic) {
			'{' -> {
				val out = LinkedHashMap<String, Any?>()
				obj@ while (true) {
					when (skipSpaces().read()) {
						'}' -> break@obj; ',' -> continue@obj; else -> unread()
					}
					val key = decode() as String
					skipSpaces().expect(':')
					val value = decode()
					out[key] = value
				}
				return out
			}
			'[' -> {
				val out = arrayListOf<Any?>()
				array@ while (true) {
					when (skipSpaces().read()) {
						']' -> break@array; ',' -> continue@array; else -> unread()
					}
					val item = decode()
					out += item
				}
				return out
			}
			'-', '+', in '0'..'9' -> {
				unread()
				val res = readWhile { (it in '0'..'9') || it == '.' || it == 'e' || it == 'E' || it == '-' || it == '+' }
				val dres = res.toDouble()
				return if (dres.toInt().toDouble() == dres) dres.toInt() else dres
			}
			't', 'f', 'n' -> {
				unread()
				if (tryRead("true")) return true
				if (tryRead("false")) return false
				if (tryRead("null")) return null
				invalidJson()
			}
			'"' -> {
				unread()
				return readStringLit()
			}
			else -> invalidJson("Not expected '$ic'")
		}
	}

	@Language("json")
	fun encodeUntyped(obj: Any?) = StringBuilder().apply { encodeUntyped(obj, this) }.toString()

	fun encodeUntyped(obj: Any?, b: StringBuilder) {
		when (obj) {
			null -> b.append("null")
			is Boolean -> b.append(if (obj) "true" else "false")
			is Map<*, *> -> {
				b.append('{')
				for ((i, v) in obj.entries.withIndex()) {
					if (i != 0) b.append(',')
					encodeUntyped(v.key, b)
					b.append(':')
					encodeUntyped(v.value, b)
				}
				b.append('}')
			}
			is Iterable<*> -> {
				b.append('[')
				for ((i, v) in obj.withIndex()) {
					if (i != 0) b.append(',')
					encodeUntyped(v, b)
				}
				b.append(']')
			}
			is Enum<*> -> encodeString(obj.name, b)
			is String -> encodeString(obj, b)
			is Number -> b.append("$obj")
			is CustomJsonSerializer -> obj.encodeToJson(b)
			else -> {
				invalidOp("Don't know how to serialize $obj")
				//encode(ClassFactory(obj::class).toMap(obj), b)
			}
		}
	}

	fun encodePrettyUntyped(obj: Any?, indentChunk: String = "\t"): String = Indenter().apply {
		encodePrettyUntyped(obj, this)
	}.toString(doIndent = true, indentChunk = indentChunk)

	fun encodePrettyUntyped(obj: Any?, b: Indenter) {
		when (obj) {
			null -> b.inline("null")
			is Boolean -> b.inline(if (obj) "true" else "false")
			is Map<*, *> -> {
				b.line("{")
				b.indent {
					val entries = obj.entries
					for ((i, v) in entries.withIndex()) {
						if (i != 0) b.line(",")
						b.inline(encodeString("" + v.key))
						b.inline(": ")
						encodePrettyUntyped(v.value, b)
						if (i == entries.size - 1) b.line("")
					}
				}
				b.inline("}")
			}
			is Iterable<*> -> {
				b.line("[")
				b.indent {
					val entries = obj.toList()
					for ((i, v) in entries.withIndex()) {
						if (i != 0) b.line(",")
						encodePrettyUntyped(v, b)
						if (i == entries.size - 1) b.line("")
					}
				}
				b.inline("]")
			}
			is String -> b.inline(encodeString(obj))
			is Number -> b.inline("$obj")
		//else -> encodePretty(ClassFactory(obj::class).toMap(obj), b)
			is CustomJsonSerializer -> {
				b.inline(StringBuilder().apply { obj.encodeToJson(this) }.toString())
			}
			else -> {
				invalidOp("Don't know how to serialize $obj")
				//encode(ClassFactory(obj::class).toMap(obj), b)
			}
		}
	}

	private fun encodeString(str: String) = StringBuilder().apply { encodeString(str, this) }.toString()

	private fun encodeString(str: String, b: StringBuilder) {
		b.append('"')
		for (c in str) {
			when (c) {
				'\\' -> b.append("\\\\"); '/' -> b.append("\\/"); '\'' -> b.append("\\'")
				'"' -> b.append("\\\""); '\b' -> b.append("\\b"); '\u000c' -> b.append("\\f")
				'\n' -> b.append("\\n"); '\r' -> b.append("\\r"); '\t' -> b.append("\\t")
				else -> b.append(c)
			}
		}
		b.append('"')
	}
}

interface CustomJsonSerializer {
	fun encodeToJson(b: StringBuilder)
}

fun Map<*, *>.toJson(mapper: ObjectMapper) = Json.encode(this, mapper)
fun Map<*, *>.toJsonUntyped() = Json.encodeUntyped(this)
