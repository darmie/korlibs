package com.soywiz.korio

import kotlinx.cinterop.*
import platform.posix.*
import platform.zlib.*
import kotlin.math.*

val CHUNK = 64 * 1024

interface ZlibInput {
	fun read(out: ByteArray, size: Int): Int
}

interface ZlibOutput {
	fun write(out: ByteArray, size: Int): Unit
}

class ByteArrayZlibOutput(expectedSize: Int) : ZlibOutput {
	var array = ByteArray(expectedSize)
	var pos = 0

	fun ensure(size: Int) {
		if (pos + size >= array.size) {
			array = array.copyOf(max(pos + size, array.size * 2))
		}
	}

	override fun write(out: ByteArray, size: Int) {
		ensure(size)
		out.copyRangeTo(array, 0, size, pos)
		pos += size
	}

	fun toByteArray() = array.copyOf(pos)
}

class ByteArrayZlibInput(val ba: ByteArray) : ZlibInput {
	var pos = 0

	override fun read(out: ByteArray, size: Int): Int {
		val remaining = ba.size - pos
		val toRead = min(size, remaining)
		this.ba.copyRangeTo(out, pos, pos + toRead, 0)
		pos += toRead
		return toRead
	}
}

fun zlibInflate(input: ByteArray, hintSize: Int): ByteArray {
	return zlibInflate(ByteArrayZlibInput(input), ByteArrayZlibOutput(hintSize)).toByteArray()
}

fun <T : ZlibOutput> zlibInflate(input: ZlibInput, output: T): T {
	memScoped {
		val strm: z_stream = alloc()

		var ret: Int

		val inpArray = ByteArray(CHUNK)
		val outArray = ByteArray(CHUNK)

		try {
			inpArray.usePinned { _inp ->
				outArray.usePinned { _out ->
					val inp = _inp.addressOf(0)
					val out = _out.addressOf(0)
					strm.zalloc = null
					strm.zfree = null
					strm.opaque = null
					strm.avail_in = 0
					strm.next_in = null
					ret = inflateInit2_(strm.ptr, 15 + 32, zlibVersion()?.toKString(), sizeOf<z_stream>().toInt());
					if (ret != Z_OK)
						return@memScoped ret

					do {
						strm.avail_in = input.read(inpArray, CHUNK)
						if (strm.avail_in == 0) break
						strm.next_in = inp

						do {
							strm.avail_out = CHUNK
							strm.next_out = out
							ret = inflate(strm.ptr, Z_NO_FLUSH)
							assert(ret != Z_STREAM_ERROR)
							when (ret) {
								Z_NEED_DICT -> ret = Z_DATA_ERROR
								Z_DATA_ERROR, Z_MEM_ERROR  -> error("data/mem error")
							}
							val have = CHUNK - strm.avail_out
							output.write(outArray, have)
						} while (strm.avail_out == 0)
					} while (ret != Z_STREAM_END)
				}
			}
		} finally {
			inflateEnd(strm.ptr)
		}
	}
	return output
}
