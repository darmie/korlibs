package com.soywiz.korio.vfs

import com.soywiz.korio.async.*
import kotlin.test.*

class VfsFileTest {
	@Test
	fun name() = suspendTest {
		val file = MemoryVfs()["C:\\this\\is\\a\\test.txt"]
		assertEquals("C:/this/is/a", file.parent.fullname)
	}

	@Test
	fun memoryNonExists() = suspendTest {
		val file = MemoryVfs()
		assertEquals(false, file["test"].exists())
	}

	@Test
	fun testCaseSensitiveAccess() = suspendTest {
		val file = MemoryVfs(caseSensitive = true)
		file["test.tXt"].writeString("hello world")
		assertEquals(true, file["test.tXt"].exists())
		assertEquals(false, file["test.txt"].exists())
		assertEquals(false, file["test.TXT"].exists())
	}

	@Test
	fun testCaseInsensitiveAccess() = suspendTest {
		val file = MemoryVfs(caseSensitive = false)
		file["test.tXt"].writeString("hello world")
		assertEquals(true, file["test.txt"].exists())
		assertEquals(true, file["test.tXt"].exists())
		assertEquals(true, file["test.TXT"].exists())
	}

	@Test
	fun redirected() = suspendTest {
		var out = ""
		val file = MemoryVfsMix(
			"hello.txt" to "yay!",
			"hello.bin" to "NEVER-HERE"
		).redirected {
			out += this[it].readString()
			PathInfo(it).pathWithExtension("txt")
		}

		assertEquals("yay!", file["hello.txt"].readString())
		assertEquals("yay!", out)
		assertEquals("yay!", file["hello.bin"].readString())
		assertEquals("yay!NEVER-HERE", out)
		//assertEquals("ay", file["hello.bin"].readRangeBytes(1L .. 2L).toString(Charsets.UTF_8)) // CompilationException in Kotlin 1.1.1 -> Couldn't transform method node (probably related to long)
		assertEquals("ay", file["hello.bin"].readRangeBytes(1..2).toString(Charsets.UTF_8))

		assertEquals("ay!", file["hello.bin"].readRangeBytes(1..200).toString(Charsets.UTF_8))
	}

	@Test
	fun avoidStats() = suspendTest {
		val log = LogVfs(MemoryVfsMix("hello.txt" to "yay!"))
		val root = log.root
		root["hello.txt"].readBytes()
		assertEquals(
			"[readRange(/hello.txt, 0..9223372036854775807)]",
			log.logstr
		)
	}
}