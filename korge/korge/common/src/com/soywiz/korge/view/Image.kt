package com.soywiz.korge.view

import com.soywiz.korim.bitmap.*
import com.soywiz.korma.geom.*

inline fun Container.image(
	texture: BmpSlice, anchorX: Double = 0.0, anchorY: Double = 0.0, callback: @ViewsDslMarker Image.() -> Unit = {}
): Image = Image(texture, anchorX, anchorY).addTo(this).apply(callback)

inline fun Container.image(
	texture: Bitmap, anchorX: Double = 0.0, anchorY: Double = 0.0, callback: @ViewsDslMarker Image.() -> Unit = {}
): Image = Image(texture, anchorX, anchorY).addTo(this).apply(callback)

//typealias Sprite = Image

open class Image(
	bitmap: BmpSlice,
	anchorX: Double = 0.0,
	anchorY: Double = anchorX,
	hitShape: VectorPath? = null,
	smoothing: Boolean = true
) : RectBase(anchorX, anchorY, hitShape, smoothing) {
	constructor(
		bitmap: Bitmap,
		anchorX: Double = 0.0,
		anchorY: Double = anchorX,
		hitShape: VectorPath? = null,
		smoothing: Boolean = true
	) : this(bitmap.slice(), anchorX, anchorY, hitShape, smoothing)

	var bitmap: BmpSlice get() = baseBitmap; set(v) = run { baseBitmap = v }
	var texture: BmpSlice get() = baseBitmap; set(v) = run { baseBitmap = v }

	init {
		this.baseBitmap = bitmap
	}

	override val bwidth: Double get() = bitmap.width.toDouble()
	override val bheight: Double get() = bitmap.height.toDouble()

	override fun createInstance(): View = Image(bitmap, anchorX, anchorY, hitShape, smoothing)
}

inline fun <T : Image> T.anchor(ax: Number, ay: Number): T =
	this.apply { anchorX = ax.toDouble() }.apply { anchorY = ay.toDouble() }
