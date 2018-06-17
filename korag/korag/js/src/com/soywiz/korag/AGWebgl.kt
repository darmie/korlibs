package com.soywiz.korag

import com.soywiz.klogger.*
import com.soywiz.kmem.*
import com.soywiz.korag.shader.*
import com.soywiz.korag.shader.gl.*
import com.soywiz.korim.bitmap.*
import com.soywiz.korim.color.*
import com.soywiz.korim.format.*
import com.soywiz.korio.error.*
import com.soywiz.korio.lang.*
import com.soywiz.korio.util.*
import com.soywiz.korma.*
import org.khronos.webgl.*
import org.w3c.dom.*
import org.w3c.dom.events.*
import kotlin.browser.*
import org.khronos.webgl.WebGLRenderingContext as GL

actual object AGFactoryFactory {
	actual fun create(): AGFactory = AGFactoryWebgl
	actual val isTouchDevice: Boolean get() {
		return js("('ontouchstart' in window || navigator.maxTouchPoints)").unsafeCast<Boolean>()
	}
}

object AGFactoryWebgl : AGFactory {
	override val supportsNativeFrame: Boolean = true
	override fun create(): AG = AGWebgl()
	override fun createFastWindow(title: String, width: Int, height: Int): AGWindow {
		TODO()
	}
}

fun jsEmptyObject(): dynamic = js("({})")

fun jsObject(vararg pairs: Pair<String, Any?>): dynamic {
	val out = jsEmptyObject()
	for ((k, v) in pairs) if (v != null) out[k] = v
	//for ((k, v) in pairs) out[k] = v
	return out
}

class AGWebgl : AG(), AGContainer {
	companion object {
		val log = Logger("AGWebgl")
	}

	override val ag: AG = this

	val canvas = document.createElement("canvas") as HTMLCanvasElement
	val glOpts = jsObject(
		"premultipliedAlpha" to false,
		"alpha" to false,
		"stencil" to true
	)
	//val gl: GL = (canvas.getContext("webgl", glOpts) ?: canvas.getContext("experimental-webgl", glOpts)) as GL
	val gl: GL = (canvas.getContext("webgl", glOpts) ?: canvas.getContext("experimental-webgl", glOpts)).asDynamic()

	init {
		(window.asDynamic()).ag = this
		//(window.asDynamic()).gl = gl
	}

	override val nativeComponent: Any = canvas
	val tDevicePixelRatio get() = window.devicePixelRatio.toDouble();
	override val devicePixelRatio get() = when {
		tDevicePixelRatio <= 0.0 -> 1.0
		tDevicePixelRatio.isNaN() -> 1.0
		tDevicePixelRatio.isInfinite() -> 1.0
		else -> tDevicePixelRatio
	}
	val onReadyOnce = Once()

	init {
		canvas.addEventListener("webglcontextlost", { e ->
			//contextVersion++
			e.preventDefault()
		}, false);

		canvas.addEventListener("webglcontextrestored", { e ->
			contextVersion++
			//e.preventDefault()
		}, false);
	}

	override fun repaint() {
		onReadyOnce { ready() }
		onRender(this)
	}

	override fun setViewport(x: Int, y: Int, width: Int, height: Int) {
		super.setViewport(x, y, width, height)
		checkErrors { gl.viewport(x, y, width, height) }
	}

	override fun resized() {
		//println("RESIZED: ${canvas.width}x${canvas.height}")
		setViewport(0, 0, canvas.width, canvas.height)
		onResized(Unit)
	}

	override fun dispose() {
		// https://www.khronos.org/webgl/wiki/HandlingContextLost
		// https://gist.github.com/mattdesl/9995467
	}

	override fun clear(
		color: Int,
		depth: Float,
		stencil: Int,
		clearColor: Boolean,
		clearDepth: Boolean,
		clearStencil: Boolean
	) {
		var bits = 0
		gl.disable(GL.SCISSOR_TEST)
		if (clearColor) {
			bits = bits or GL.COLOR_BUFFER_BIT
			gl.clearColor(RGBA.getRf(color), RGBA.getGf(color), RGBA.getBf(color), RGBA.getAf(color))
		}
		if (clearDepth) {
			bits = bits or GL.DEPTH_BUFFER_BIT
			gl.clearDepth(depth)
		}
		if (clearStencil) {
			bits = bits or GL.STENCIL_BUFFER_BIT
			gl.stencilMask(-1)
			gl.clearStencil(stencil)
		}
		gl.clear(bits)
	}

	data class MyUniformLocation(val location: WebGLUniformLocation?)

	inner class WebglProgram(val p: Program) : Closeable {
		var program = gl.createProgram()
		var cachedVersion = -1
		var vertex: WebGLShader? = null
		var fragment: WebGLShader? = null

		// @TODO: Optimize (IntIntMap or just an array)
		val uniformLocations = HashMap<Uniform, MyUniformLocation>()

		fun createShader(type: Int, source: String): WebGLShader? {
			val shader = gl.createShader(type)
			gl.shaderSource(shader, source)
			gl.compileShader(shader)

			val success: dynamic = gl.getShaderParameter(shader, GL.COMPILE_STATUS)
			if (!success) {
				val error = gl.getShaderInfoLog(shader)
				log.error { "$shader" }
				log.error { source }
				log.error { "Could not compile WebGL shader: " + error }
				throw RuntimeException(error)
			}
			return shader
		}

		private fun ensure() {
			if (cachedVersion != contextVersion) {
				cachedVersion = contextVersion
				vertex = createShader(GL.VERTEX_SHADER, p.vertex.toGlSlString())
				fragment = createShader(GL.FRAGMENT_SHADER, p.fragment.toGlSlString())
				gl.attachShader(program, vertex)
				gl.attachShader(program, fragment)
				gl.linkProgram(program)

				val linkStatus: dynamic = gl.getProgramParameter(program, GL.LINK_STATUS)
				if (!linkStatus) {
					val info = gl.getProgramInfoLog(program)
					log.error { "Could not compile WebGL program: $info" }
				}
			}
		}

		fun bind() {
			ensure()
			gl.useProgram(this.program)
		}

		fun unbind() {
			gl.useProgram(null)
		}

		override fun close() {
			ensure()
			gl.deleteShader(this.vertex)
			gl.deleteShader(this.fragment)
			gl.deleteProgram(this.program)
		}

		fun uniformLocation(uniform: Uniform): WebGLUniformLocation? {
			val location =
				uniformLocations.getOrPut(uniform) { MyUniformLocation(gl.getUniformLocation(program, uniform.name)) }
			return location?.location
		}
	}

	inner class WebglTexture(override val premultiplied: Boolean) : Texture() {
		var cachedVersion = -1
		private var _tex: WebGLTexture? = null
		val tex: WebGLTexture?
			get() {
				if (cachedVersion != contextVersion) {
					cachedVersion = contextVersion
					invalidate()
					_tex = gl.createTexture()
				}
				return _tex
			}

		override fun actualSyncUpload(source: BitmapSourceBase, bmp: Bitmap?, requestMipmaps: Boolean) {
			when (bmp) {
				null -> {
				}
				is CanvasNativeImage -> {
					val type = GL.RGBA
					//println("Uploading native image!")

					checkErrors { gl.pixelStorei(GL.UNPACK_PREMULTIPLY_ALPHA_WEBGL, if (premultiplied) 1 else 0) }
					checkErrors { gl.texImage2D(GL.TEXTURE_2D, 0, type, type, GL.UNSIGNED_BYTE, bmp.canvas) }
					//gl.pixelStorei(GL.UNPACK_COLORSPACE_CONVERSION_WEBGL, 0)
				}
				is Bitmap32, is Bitmap8 -> {
					val width = bmp.width
					val height = bmp.height
					val rgba = bmp is Bitmap32
					val Bpp = if (rgba) 4 else 1
					val data: dynamic =
						(bmp as? Bitmap32)?.data ?: ((bmp as? Bitmap8)?.data ?: ByteArray(width * height * Bpp))
					val rdata = Uint8Array(data.buffer, 0, width * height * Bpp)
					val type = if (rgba) GL.RGBA else GL.LUMINANCE
					checkErrors { gl.pixelStorei(GL.UNPACK_PREMULTIPLY_ALPHA_WEBGL, if (premultiplied xor bmp.premult) 1 else 0) }
					//gl.pixelStorei(GL.UNPACK_COLORSPACE_CONVERSION_WEBGL, 0)
					checkErrors { gl.texImage2D(GL.TEXTURE_2D, 0, type, width, height, 0, type, GL.UNSIGNED_BYTE, rdata) }
				}
			}

			this.mipmaps = false

			if (requestMipmaps) {
				bind()
				setFilter(true)
				setWrapST()
				checkErrors { gl.generateMipmap(GL.TEXTURE_2D) }
				this.mipmaps = true
			}
		}

		val rtex get() = if (!closed && tex !== undefined) tex else null

		override fun bind(): Unit = run {
			checkErrors { gl.bindTexture(GL.TEXTURE_2D, rtex) }
		}
		override fun unbind(): Unit = run { checkErrors { gl.bindTexture(GL.TEXTURE_2D, null) } }

		private var closed = false
		override fun close(): Unit = run {
			super.close()
			if (!closed) {
				closed = true
				checkErrors { gl.deleteTexture(tex) }
			}
		}

		fun setFilter(linear: Boolean) {
			if (rtex == null) return

			val minFilter = if (this.mipmaps) {
				if (linear) GL.LINEAR_MIPMAP_NEAREST else GL.NEAREST_MIPMAP_NEAREST
			} else {
				if (linear) GL.LINEAR else GL.NEAREST
			}
			val magFilter = if (linear) GL.LINEAR else GL.NEAREST

			setWrapST()
			setMinMag(minFilter, magFilter)
		}

		private fun setWrapST() {
			if (rtex == null) return
			checkErrors { gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_S, GL.CLAMP_TO_EDGE) }
			checkErrors { gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_WRAP_T, GL.CLAMP_TO_EDGE) }
		}

		private fun setMinMag(min: Int, mag: Int) {
			if (rtex == null) return
			checkErrors { gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, min) }
			checkErrors { gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, mag) }
		}
	}

	inner class WebglBuffer(kind: Kind) : Buffer(kind) {
		var cachedVersion = -1
		var buffer: WebGLBuffer? = null
		val target = if (kind == Kind.INDEX) GL.ELEMENT_ARRAY_BUFFER else GL.ARRAY_BUFFER

		override fun afterSetMem() {
			//bind()
			//if (mem != null) {
			//	val buffer = mem.asJsDynamic()["buffer"]
			//	val typedArray = jsNew("Int8Array", buffer, memOffset, memLength)
			//	//console.methods["log"](target)
			//	//console.methods["log"](typedArray)
			//	gl.bufferData(this.target, typedArray, GL.STATIC_DRAW)
			//}
		}

		fun bind() {
			if (cachedVersion != contextVersion) {
				cachedVersion = contextVersion
				buffer = null
				dirty = true
			}

			if (buffer == null) {
				buffer = gl.createBuffer()
			}

			checkErrors { gl.bindBuffer(this.target, this.buffer) }

			if (dirty) {
				val mem2: FastMemory = mem!!
				val buffer = mem2.i8
				@Suppress("CAST_NEVER_SUCCEEDS")
				val typedArray = Int8Array((buffer as Int8Buffer).buffer, memOffset, memLength)
				//console.methods["log"](target)
				//console.methods["log"](typedArray)
				checkErrors { gl.bufferData(this.target, typedArray, GL.STATIC_DRAW) }
			}
		}

		override fun close() {
			if (buffer != null) {
				checkErrors { gl.deleteBuffer(buffer) }
			}
			buffer = null
		}
	}

	override fun createTexture(premultiplied: Boolean): Texture = WebglTexture(premultiplied)
	override fun createBuffer(kind: Buffer.Kind): Buffer = WebglBuffer(kind)

	private val programs = hashMapOf<String, WebglProgram>()

	fun getProgram(program: Program): WebglProgram = programs.getOrPut(program.name) { WebglProgram(program) }

	val VarType.webglElementType: Int
		get() = when (this.kind) {
			VarKind.BYTE -> GL.BYTE
			VarKind.UNSIGNED_BYTE -> GL.UNSIGNED_BYTE
			VarKind.SHORT -> GL.SHORT
			VarKind.UNSIGNED_SHORT -> GL.UNSIGNED_SHORT
			VarKind.INT -> GL.INT
			VarKind.FLOAT -> GL.FLOAT
		}

	val DrawType.glDrawMode: Int
		get() = when (this) {
			DrawType.POINTS -> GL.POINTS
			DrawType.LINE_STRIP -> GL.LINE_STRIP
			DrawType.LINE_LOOP -> GL.LINE_LOOP
			DrawType.LINES -> GL.LINES
			DrawType.TRIANGLE_STRIP -> GL.TRIANGLE_STRIP
			DrawType.TRIANGLE_FAN -> GL.TRIANGLE_FAN
			DrawType.TRIANGLES -> GL.TRIANGLES

		}

	private fun BlendEquation.toGl(): Int = when (this) {
		BlendEquation.ADD -> GL.FUNC_ADD
		BlendEquation.SUBTRACT -> GL.FUNC_SUBTRACT
		BlendEquation.REVERSE_SUBTRACT -> GL.FUNC_REVERSE_SUBTRACT
	}

	private fun BlendFactor.toGl(): Int = when (this) {
		BlendFactor.DESTINATION_ALPHA -> GL.DST_ALPHA
		BlendFactor.DESTINATION_COLOR -> GL.DST_COLOR
		BlendFactor.ONE -> GL.ONE
		BlendFactor.ONE_MINUS_DESTINATION_ALPHA -> GL.ONE_MINUS_DST_ALPHA
		BlendFactor.ONE_MINUS_DESTINATION_COLOR -> GL.ONE_MINUS_DST_COLOR
		BlendFactor.ONE_MINUS_SOURCE_ALPHA -> GL.ONE_MINUS_SRC_ALPHA
		BlendFactor.ONE_MINUS_SOURCE_COLOR -> GL.ONE_MINUS_SRC_COLOR
		BlendFactor.SOURCE_ALPHA -> GL.SRC_ALPHA
		BlendFactor.SOURCE_COLOR -> GL.SRC_COLOR
		BlendFactor.ZERO -> GL.ZERO
	}

	fun TriangleFace.toGl() = when (this) {
		TriangleFace.FRONT -> GL.FRONT
		TriangleFace.BACK -> GL.BACK
		TriangleFace.FRONT_AND_BACK -> GL.FRONT_AND_BACK
		TriangleFace.NONE -> GL.FRONT
	}

	fun CompareMode.toGl() = when (this) {
		CompareMode.ALWAYS -> GL.ALWAYS
		CompareMode.EQUAL -> GL.EQUAL
		CompareMode.GREATER -> GL.GREATER
		CompareMode.GREATER_EQUAL -> GL.GEQUAL
		CompareMode.LESS -> GL.LESS
		CompareMode.LESS_EQUAL -> GL.LEQUAL
		CompareMode.NEVER -> GL.NEVER
		CompareMode.NOT_EQUAL -> GL.NOTEQUAL
	}

	fun StencilOp.toGl() = when (this) {
		StencilOp.DECREMENT_SATURATE -> GL.DECR
		StencilOp.DECREMENT_WRAP -> GL.DECR_WRAP
		StencilOp.INCREMENT_SATURATE -> GL.INCR
		StencilOp.INCREMENT_WRAP -> GL.INCR_WRAP
		StencilOp.INVERT -> GL.INVERT
		StencilOp.KEEP -> GL.KEEP
		StencilOp.SET -> GL.REPLACE
		StencilOp.ZERO -> GL.ZERO
	}

	override fun draw(
		vertices: Buffer,
		program: Program,
		type: DrawType,
		vertexLayout: VertexLayout,
		vertexCount: Int,
		indices: Buffer?,
		offset: Int,
		blending: Blending,
		uniforms: Map<Uniform, Any>,
		stencil: StencilState,
		colorMask: ColorMaskState,
		renderState: RenderState
	) {
		val mustFreeIndices = indices == null
		val aindices = indices ?: createIndexBuffer((0 until vertexCount).map(Int::toShort).toShortArray())
		checkBuffers(vertices, aindices)
		val glProgram = getProgram(program)
		(vertices as WebglBuffer).bind()
		(aindices as WebglBuffer).bind()
		checkErrors { glProgram.bind() }

		val totalSize = vertexLayout.totalSize
		for (n in vertexLayout.attributePositions.indices) {
			val att = vertexLayout.attributes[n]
			if (att.active) {
				val off = vertexLayout.attributePositions[n]
				val loc = gl.getAttribLocation(glProgram.program, att.name)
				val glElementType = att.type.webglElementType
				val elementCount = att.type.elementCount
				if (loc >= 0) {
					checkErrors { gl.enableVertexAttribArray(loc) }
					checkErrors { gl.vertexAttribPointer(loc, elementCount, glElementType, att.normalized, totalSize, off) }
				}
			}
		}
		var textureUnit = 0
		for ((uniform, value) in uniforms) {
			val location = glProgram.uniformLocation(uniform) ?: continue
			when (uniform.type) {
				VarType.TextureUnit -> {
					val unit = value as TextureUnit
					checkErrors { gl.activeTexture(GL.TEXTURE0 + textureUnit) }
					val tex = (unit.texture as WebglTexture?)
					tex?.bindEnsuring()
					tex?.setFilter(unit.linear)
					checkErrors { gl.uniform1i(location, textureUnit) }
					textureUnit++
				}
				VarType.Mat4 -> {
					checkErrors { glUniformMatrix4fv(location, false, (value as Matrix4).data) }
				}
				VarType.Float1 -> {
					checkErrors { gl.uniform1f(location, (value as Number).toFloat()) }
				}
				VarType.Float2 -> {
					val fa = value as FloatArray
					checkErrors { gl.uniform2f(location, fa[0], fa[1]) }
				}
				VarType.Float3 -> {
					val fa = value as FloatArray
					checkErrors { gl.uniform3f(location, fa[0], fa[1], fa[2]) }
				}
				VarType.Float4 -> {
					val fa = value as FloatArray
					checkErrors { gl.uniform4f(location, fa[0], fa[1], fa[2], fa[3]) }
				}
				else -> invalidOp("Don't know how to set uniform ${uniform.type}")
			}
		}

		if (blending.disabled) {
			checkErrors { gl.disable(GL.BLEND) }
		} else {
			checkErrors { gl.enable(GL.BLEND) }
			checkErrors { gl.blendEquationSeparate(blending.eqRGB.toGl(), blending.eqA.toGl()) }
			checkErrors { gl.blendFuncSeparate(
				blending.srcRGB.toGl(),
				blending.dstRGB.toGl(),
				blending.srcA.toGl(),
				blending.dstA.toGl()
			) }
		}

		checkErrors { gl.depthMask(renderState.depthMask) }

		checkErrors { gl.depthRange(renderState.depthNear, renderState.depthFar) }

		checkErrors { gl.lineWidth(renderState.lineWidth) }

		if (renderState.depthFunc != CompareMode.ALWAYS) {
			checkErrors { gl.enable(GL.DEPTH_TEST) }
			checkErrors { gl.depthFunc(renderState.depthFunc.toGl()) }
		} else {
			checkErrors { gl.disable(GL.DEPTH_TEST) }
		}

		checkErrors { gl.colorMask(colorMask.red, colorMask.green, colorMask.blue, colorMask.alpha) }

		if (stencil.enabled) {
			gl.enable(GL.STENCIL_TEST)
			gl.stencilFunc(stencil.compareMode.toGl(), stencil.referenceValue, stencil.readMask)
			gl.stencilOp(
				stencil.actionOnDepthFail.toGl(),
				stencil.actionOnDepthPassStencilFail.toGl(),
				stencil.actionOnBothPass.toGl()
			)
			gl.stencilMask(stencil.writeMask)
		} else {
			gl.disable(GL.STENCIL_TEST)
			gl.stencilMask(0)
		}

		//GL.drawArrays(type.glDrawMode, 0, 3)
		gl.drawElements(type.glDrawMode, vertexCount, GL.UNSIGNED_SHORT, offset)

		gl.activeTexture(GL.TEXTURE0)
		for (att in vertexLayout.attributes.filter { it.active }) {
			val loc = gl.getAttribLocation(glProgram.program, att.name)
			if (loc >= 0) {
				gl.disableVertexAttribArray(loc)
			}
		}
		if (mustFreeIndices) aindices.close()
	}

	private fun glUniformMatrix4fv(location: WebGLUniformLocation, b: Boolean, values: FloatArray) {
		gl.uniformMatrix4fv(location, b, (values as Float32Array))
	}

	val tempTextures = arrayListOf<Texture>()

	override fun disposeTemporalPerFrameStuff() {
		for (tt in tempTextures) tt.close()
		tempTextures.clear()
	}

	override fun flipInternal() {
	}

	inner class WebglRenderBuffer() : RenderBuffer() {
		var cachedVersion = -1
		val wtex get() = tex as WebglTexture

		var renderbuffer: WebGLRenderbuffer? = null
		var framebuffer: WebGLFramebuffer? = null
		var oldViewport = IntArray(4)

		override fun start(width: Int, height: Int) {
			if (cachedVersion != contextVersion) {
				cachedVersion = contextVersion
				renderbuffer = gl.createRenderbuffer()
				framebuffer = gl.createFramebuffer()
			}

			getViewport(oldViewport)
			//println("oldViewport:${oldViewport.toList()}")
			gl.bindTexture(GL.TEXTURE_2D, wtex.tex)
			gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MAG_FILTER, GL.LINEAR)
			gl.texParameteri(GL.TEXTURE_2D, GL.TEXTURE_MIN_FILTER, GL.LINEAR)
			gl.texImage2D(GL.TEXTURE_2D, 0, GL.RGBA, width, height, 0, GL.RGBA, GL.UNSIGNED_BYTE, null)
			gl.bindTexture(GL.TEXTURE_2D, null)
			gl.bindRenderbuffer(GL.RENDERBUFFER, renderbuffer)
			gl.bindFramebuffer(GL.FRAMEBUFFER, framebuffer)
			gl.framebufferTexture2D(GL.FRAMEBUFFER, GL.COLOR_ATTACHMENT0, GL.TEXTURE_2D, wtex.tex, 0)
			gl.renderbufferStorage(GL.RENDERBUFFER, GL.DEPTH_COMPONENT16, width, height)
			gl.framebufferRenderbuffer(GL.FRAMEBUFFER, GL.DEPTH_ATTACHMENT, GL.RENDERBUFFER, renderbuffer)
			setViewport(0, 0, width, height)
		}

		override fun end() {
			gl.flush()
			gl.bindTexture(GL.TEXTURE_2D, null)
			gl.bindRenderbuffer(GL.RENDERBUFFER, null)
			gl.bindFramebuffer(GL.FRAMEBUFFER, null)
			setViewport(oldViewport)
		}

		override fun close() {
			gl.deleteFramebuffer(framebuffer)
			gl.deleteRenderbuffer(renderbuffer)
		}
	}

	override fun createRenderBuffer(): RenderBuffer = WebglRenderBuffer()

	inline fun <T> checkErrors(callback: () -> T): T = callback()

	//inline fun <T> checkErrors(callback: () -> T): T {
	//	val res = callback()
	//	if (checkErrors) {
	//		val error =
	//			gl.getError() // @TODO: Kotlin.JS bug? Generates WebGLRenderingContext$Companion!! Just for this because of the inline.
	//		//if (error != GL.NO_ERROR) {
	//		if (error != 0) {
	//			Console.error("OpenGL error: $error")
	//			//System.err.println(Throwable().stackTrace)
	//			Throwable().printStackTrace()
	//			//throw RuntimeException("OpenGL error: $error")
	//		}
	//	}
	//	return res
	//}

	override fun readColor(bitmap: Bitmap32) {
		gl.readPixels(
			0,
			0,
			bitmap.width,
			bitmap.height,
			GL.RGBA,
			GL.UNSIGNED_BYTE,
			Uint8Array(bitmap.data.unsafeCast<Int32Array>().buffer)
		)
	}

	override fun readDepth(width: Int, height: Int, out: FloatArray) {
		gl.readPixels(0, 0, width, height, GL.DEPTH_COMPONENT, GL.FLOAT, out.unsafeCast<Float32Array>())
	}
}