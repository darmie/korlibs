package com.soywiz.korge.view

import com.soywiz.kmem.*
import com.soywiz.korge.render.*
import com.soywiz.korim.bitmap.*

open class Mesh(
	var texture: BmpSlice? = null,
	var vertices: Float32Buffer = Float32BufferAlloc(0),
	var uvs: Float32Buffer = Float32BufferAlloc(0),
	var indices: Uint16Buffer = Uint16BufferAlloc(0),
	var drawMode: DrawModes = DrawModes.Triangles
) : View() {
	enum class DrawModes { Triangles, TriangleStrip }

	val textureNN get() = texture ?: Bitmaps.white
	var dirty: Int = 0
	var indexDirty: Int = 0

	var pivotX: Double = 0.0
	var pivotY: Double = 0.0

	override fun render(ctx: RenderContext) {
		// @TODO: Render in one batch without matrix multiplication in CPU
		val m = renderMatrix
		val cmul = this.colorMul
		val cadd = this.colorAdd
		val tva = TexturedVertexArray(vertices.size / 2, IntArray(indices.size))
		for (n in 0 until tva.indices.size) tva.indices[n] = indices[n]
		for (n in 0 until tva.vcount) {
			val x = vertices[n * 2 + 0].toDouble() + pivotX
			val y = vertices[n * 2 + 1].toDouble() + pivotY
			tva.select(n)
				.xy(m.transformX(x, y), m.transformY(x, y))
				.uv(uvs[n * 2 + 0], uvs[n * 2 + 1])
				.cols(cmul, cadd)
		}
		ctx.batch.drawVertices(tva, ctx.getTex(textureNN).base, true, this.blendMode.factors)
	}
}

fun <T : Mesh> T.pivot(x: Double, y: Double): T = this.apply { this.pivotX = x }.also { this.pivotY = y }
