package com.soywiz.korag

import com.soywiz.kgl.*

actual object AGOpenglFactory {
	actual fun create(nativeComponent: Any?): AGFactory = AGFactoryNative
	actual val isTouchDevice: Boolean = false
}

object AGFactoryNative : AGFactory {
	override val supportsNativeFrame: Boolean = false
	override fun create(nativeControl: Any?): AG = AGNative()
	override fun createFastWindow(title: String, width: Int, height: Int): AGWindow = TODO()
}

class AGNative : AGOpengl() {
	override val nativeComponent = Any()
	override val gl: KmlGl = KmlGlNative()
}
