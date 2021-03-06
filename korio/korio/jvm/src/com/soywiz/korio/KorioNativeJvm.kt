package com.soywiz.korio

import com.soywiz.korio.async.*
import com.soywiz.korio.compression.deflate.*
import com.soywiz.korio.file.*
import com.soywiz.korio.file.std.*
import com.soywiz.korio.net.*
import com.soywiz.korio.net.http.*
import com.soywiz.korio.net.ws.*
import com.soywiz.korio.util.*
import kotlinx.coroutines.experimental.*
import org.java_websocket.handshake.*
import java.io.*
import java.lang.*
import java.nio.*
import java.security.*
import java.util.*
import java.util.zip.*
import javax.crypto.*
import javax.crypto.spec.*
import kotlin.coroutines.experimental.*
import kotlin.reflect.*

actual typealias Synchronized = kotlin.jvm.Synchronized
actual typealias JvmField = kotlin.jvm.JvmField
actual typealias JvmStatic = kotlin.jvm.JvmStatic
actual typealias JvmOverloads = kotlin.jvm.JvmOverloads
actual typealias Transient = kotlin.jvm.Transient

//actual typealias Language = org.intellij.lang.annotations.Language

actual typealias IOException = java.io.IOException
actual typealias EOFException = java.io.EOFException
actual typealias FileNotFoundException = java.io.FileNotFoundException

actual typealias RuntimeException = java.lang.RuntimeException
actual typealias IllegalStateException = java.lang.IllegalStateException

actual class Semaphore actual constructor(initial: Int) {
	val jsema = java.util.concurrent.Semaphore(initial)
	//var initial: Int
	actual fun acquire() = jsema.acquire()

	actual fun release() = jsema.release()
}

val currentThreadId: Long get() = KorioNative.currentThreadId


actual object KorioNative {
	actual val currentThreadId: Long get() = Thread.currentThread().id

	actual fun getClassSimpleName(clazz: KClass<*>): String = clazz.java.simpleName

	actual suspend fun <T> executeInWorker(callback: suspend () -> T): T {
		return withContext(newSingleThreadContext("worker")) {
        callback()
    }
	}

	actual fun asyncEntryPoint(context: CoroutineContext, callback: suspend () -> Unit) = runBlocking(context) { callback() }

	actual abstract class NativeThreadLocal<T> {
		actual abstract fun initialValue(): T

		val jthreadLocal = object : ThreadLocal<T>() {
			override fun initialValue(): T = this@NativeThreadLocal.initialValue()
		}

		actual fun get(): T = jthreadLocal.get()
		actual fun set(value: T) = jthreadLocal.set(value)
	}

	actual val systemLanguageStrings get() = listOf(Locale.getDefault().getISO3Language())

	actual val platformName: String = "jvm"
	actual val rawOsName: String by lazy { System.getProperty("os.name") }

	private val secureRandom: SecureRandom by lazy { SecureRandom.getInstanceStrong() }

	actual fun getRandomValues(data: ByteArray): Unit {
		secureRandom.nextBytes(data)
	}

	actual val httpFactory: HttpFactory by lazy {
		object : HttpFactory {
			init {
				System.setProperty("http.keepAlive", "false")
			}

			override fun createClient(): HttpClient = HttpClientJvm()
			override fun createServer(): HttpServer = KorioNativeDefaults.createServer()
		}
	}

	actual class SimplerMessageDigest actual constructor(name: String) {
		val md = MessageDigest.getInstance(name)

		actual suspend fun update(data: ByteArray, offset: Int, size: Int) =
			executeInWorker { md.update(data, offset, size) }

		actual suspend fun digest(): ByteArray = executeInWorker { md.digest() }
	}

	actual class SimplerMac actual constructor(name: String, key: ByteArray) {
		val mac = Mac.getInstance(name).apply { init(SecretKeySpec(key, name)) }
		actual suspend fun update(data: ByteArray, offset: Int, size: Int) =
			executeInWorker { mac.update(data, offset, size) }

		actual suspend fun finalize(): ByteArray = executeInWorker { mac.doFinal() }
	}

	actual fun Thread_sleep(time: Long) = Thread.sleep(time)

	actual val asyncSocketFactory: AsyncSocketFactory by lazy { JvmAsyncSocketFactory() }
	actual val websockets: WebSocketClientFactory by lazy { JvmWebSocketClientFactory() }
	actual val File_separatorChar: Char by lazy { File.separatorChar }

	actual fun uncompress(input: ByteArray, outputHint: Int, method: String): ByteArray =
		FastDeflate.uncompress(input, outputHint, method)

	private val absoluteCwd = File(".").absolutePath

	actual fun rootLocalVfs(): VfsFile = localVfs(absoluteCwd)
	actual fun applicationVfs(): VfsFile = localVfs(absoluteCwd)
	actual fun applicationDataVfs(): VfsFile = localVfs(absoluteCwd)
	actual fun cacheVfs(): VfsFile = MemoryVfs()
	actual fun externalStorageVfs(): VfsFile = localVfs(absoluteCwd)
	actual fun userHomeVfs(): VfsFile = localVfs(absoluteCwd)
	actual fun tempVfs(): VfsFile = localVfs(tmpdir)
	actual fun localVfs(path: String): VfsFile = LocalVfsJvm()[path]

	actual val ResourcesVfs: VfsFile by lazy { ResourcesVfsProviderJvm()().root }

	val tmpdir: String get() = System.getProperty("java.io.tmpdir")

	actual fun enterDebugger() = Unit
	actual fun printStackTrace(e: Throwable) = e.printStackTrace()

	actual fun getenv(key: String): String? {
		return System.getenv(key)
	}

	actual fun suspendTest(callback: suspend () -> Unit): Unit {
		runBlocking { callback() }
	}
}

class JvmWebSocketClientFactory : WebSocketClientFactory() {
	override suspend fun create(
		url: String,
		protocols: List<String>?,
		origin: String?,
		wskey: String?,
		debug: Boolean
	): WebSocketClient {
		return object : WebSocketClient(url, protocols, false) {
			val that = this

			val client = object : org.java_websocket.client.WebSocketClient(java.net.URI(url)) {
				override fun onOpen(handshakedata: ServerHandshake) {
					that.onOpen(Unit)
				}

				override fun onClose(code: Int, reason: String, remote: Boolean) {
					that.onClose(Unit)
				}

				override fun onMessage(message: String) {
					that.onStringMessage(message)
					that.onAnyMessage(message)
				}

				override fun onMessage(bytes: ByteBuffer) {
					val rbytes = bytes.toByteArray()
					that.onBinaryMessage(rbytes)
					that.onAnyMessage(rbytes)
				}

				override fun onError(ex: Exception) {
					that.onError(ex)
				}
			}

			suspend fun init() {
				client.connect()
			}

			override fun close(code: Int, reason: String) {
				client.close(code, reason)
			}

			override suspend fun send(message: String) {
				client.send(message)
			}

			override suspend fun send(message: ByteArray) {
				client.send(message)
			}
		}.apply {
			init()
			val res = listOf(onOpen, onError).waitOne()
			if (res is Throwable) throw res
		}
	}

}