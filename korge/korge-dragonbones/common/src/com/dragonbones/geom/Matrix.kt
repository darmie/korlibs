package com.dragonbones.geom

import com.soywiz.kds.*
import com.soywiz.korma.*
import kotlin.math.*

/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2012-2018 DragonBones team and other contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

/**
 * - 2D Transform matrix.
 * @version DragonBones 3.0
 * @language en_US
 */
/**
 * - 2D 转换矩阵。
 * @version DragonBones 3.0
 * @language zh_CN
 */
class Matrix {
	/**
	 * - The value that affects the positioning of pixels along the x axis when scaling or rotating an image.
	 * @default 1.0
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 缩放或旋转图像时影响像素沿 x 轴定位的值。
	 * @default 1.0
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	var a: Double
	/**
	 * - The value that affects the positioning of pixels along the y axis when rotating or skewing an image.
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 旋转或倾斜图像时影响像素沿 y 轴定位的值。
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	var b: Double
	/**
	 * - The value that affects the positioning of pixels along the x axis when rotating or skewing an image.
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 旋转或倾斜图像时影响像素沿 x 轴定位的值。
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	var c: Double
	/**
	 * - The value that affects the positioning of pixels along the y axis when scaling or rotating an image.
	 * @default 1.0
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 缩放或旋转图像时影响像素沿 y 轴定位的值。
	 * @default 1.0
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	var d: Double
	/**
	 * - The distance by which to translate each point along the x axis.
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 沿 x 轴平移每个点的距离。
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	var tx: Double
	/**
	 * - The distance by which to translate each point along the y axis.
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 沿 y 轴平移每个点的距离。
	 * @default 0.0
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	var ty: Double

	/**
	 * @private
	 */
	constructor(
		a: Double = 1.0, b: Double = 0.0,
		c: Double = 0.0, d: Double = 1.0,
		tx: Double = 0.0, ty: Double = 0.0
	) {
		this.a = a
		this.b = b
		this.c = c
		this.d = d
		this.tx = tx
		this.ty = ty
	}

	override fun toString(): String {
		return "[object dragonBones.Matrix] a:" + this.a + " b:" + this.b + " c:" + this.c + " d:" + this.d + " tx:" + this.tx + " ty:" + this.ty
	}

	/**
	 * @private
	 */
	fun copyFrom(value: Matrix): Matrix {
		this.a = value.a
		this.b = value.b
		this.c = value.c
		this.d = value.d
		this.tx = value.tx
		this.ty = value.ty

		return this
	}

	/**
	 * @private
	 */
	fun copyFromArray(value: DoubleArray, offset: Int = 0): Matrix {
		this.a = value[offset]
		this.b = value[offset + 1]
		this.c = value[offset + 2]
		this.d = value[offset + 3]
		this.tx = value[offset + 4]
		this.ty = value[offset + 5]

		return this
	}

	fun copyFromArray(value: DoubleArrayList, offset: Int = 0): Matrix = copyFromArray(value.data, offset)

	/**
	 * - Convert to unit matrix.
	 * The resulting matrix has the following properties: a=1, b=0, c=0, d=1, tx=0, ty=0.
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 转换为单位矩阵。
	 * 该矩阵具有以下属性：a=1、b=0、c=0、d=1、tx=0、ty=0。
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	fun identity(): Matrix {
		this.a = 1.0
		this.b = 0.0
		this.d = 1.0
		this.c = 0.0
		this.tx = 0.0
		this.ty = 0.0

		return this
	}
	/**
	 * - Multiplies the current matrix with another matrix.
	 * @param value - The matrix that needs to be multiplied.
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 将当前矩阵与另一个矩阵相乘。
	 * @param value - 需要相乘的矩阵。
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	fun concat(value: Matrix): Matrix {
		var aA = this.a * value.a
		var bA = 0.0
		var cA = 0.0
		var dA = this.d * value.d
		var txA = this.tx * value.a + value.tx
		var tyA = this.ty * value.d + value.ty

		if (this.b != 0.0 || this.c != 0.0) {
			aA += this.b * value.c
			bA += this.b * value.d
			cA += this.c * value.a
			dA += this.c * value.b
		}

		if (value.b != 0.0 || value.c != 0.0) {
			bA += this.a * value.b
			cA += this.d * value.c
			txA += this.ty * value.c
			tyA += this.tx * value.b
		}

		this.a = aA
		this.b = bA
		this.c = cA
		this.d = dA
		this.tx = txA
		this.ty = tyA

		return this
	}
	/**
	 * - Convert to inverse matrix.
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 转换为逆矩阵。
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	fun invert(): Matrix {
		var aA = this.a
		var bA = this.b
		var cA = this.c
		var dA = this.d
		val txA = this.tx
		val tyA = this.ty

		if (bA == 0.0 && cA == 0.0) {
			this.b = 0.0
			this.c = 0.0
			if (aA == 0.0 || dA == 0.0) {
				this.a = 0.0
				this.b = 0.0
				this.tx = 0.0
				this.ty = 0.0
			} else {
				aA = 1.0 / aA
				dA = 1.0 / dA
				this.a = aA
				this.d = dA
				this.tx = -aA * txA
				this.ty = -dA * tyA
			}

			return this
		}

		var determinant = aA * dA - bA * cA
		if (determinant == 0.0) {
			this.a = 1.0
			this.b = 0.0
			this.c = 0.0
			this.d = 1.0
			this.tx = 0.0
			this.ty = 0.0

			return this
		}

		determinant = 1.0 / determinant
		val k = dA * determinant
		this.a = k
		bA = -bA * determinant
		this.b = bA
		cA = -cA * determinant
		this.c = cA
		dA = aA * determinant
		this.d = dA
		this.tx = -(k * txA + cA * tyA)
		this.ty = -(bA * txA + dA * tyA)

		return this
	}
	/**
	 * - Apply a matrix transformation to a specific point.
	 * @param x - X coordinate.
	 * @param y - Y coordinate.
	 * @param result - The point after the transformation is applied.
	 * @param delta - Whether to ignore tx, ty's conversion to point.
	 * @version DragonBones 3.0
	 * @language en_US
	 */
	/**
	 * - 将矩阵转换应用于特定点。
	 * @param x - 横坐标。
	 * @param y - 纵坐标。
	 * @param result - 应用转换之后的点。
	 * @param delta - 是否忽略 tx，ty 对点的转换。
	 * @version DragonBones 3.0
	 * @language zh_CN
	 */
	fun transformPoint(x: Double, y: Double, result: XY, delta: Boolean = false): Unit {
		result.x = this.a * x + this.c * y
		result.y = this.b * x + this.d * y

		if (!delta) {
			result.x += this.tx
			result.y += this.ty
		}
	}

	fun transformX(x: Double, y: Double): Double = (this.a * x + this.c * y + this.tx)
	fun transformY(x: Double, y: Double): Double = (this.b * x + this.d * y + this.ty)

	/**
	 * @private
	 */
	fun transformRectangle(rectangle: Rectangle, delta: Boolean = false): Unit {
		val a = this.a
		val b = this.b
		val c = this.c
		val d = this.d
		val tx = if (delta) 0.0 else this.tx
		val ty = if (delta) 0.0 else this.ty

		val x = rectangle.x
		val y = rectangle.y
		val xMax = x + rectangle.width
		val yMax = y + rectangle.height

		var x0 = a * x + c * y + tx
		var y0 = b * x + d * y + ty
		var x1 = a * xMax + c * y + tx
		var y1 = b * xMax + d * y + ty
		var x2 = a * xMax + c * yMax + tx
		var y2 = b * xMax + d * yMax + ty
		var x3 = a * x + c * yMax + tx
		var y3 = b * x + d * yMax + ty

		var tmp = 0.0

		if (x0 > x1) {
			tmp = x0
			x0 = x1
			x1 = tmp
		}
		if (x2 > x3) {
			tmp = x2
			x2 = x3
			x3 = tmp
		}

		rectangle.x = floor(if (x0 < x2) x0 else x2)
		rectangle.width = ceil((if (x1 > x3) x1 else x3) - rectangle.x)

		if (y0 > y1) {
			tmp = y0
			y0 = y1
			y1 = tmp
		}
		if (y2 > y3) {
			tmp = y2
			y2 = y3
			y3 = tmp
		}

		rectangle.y = floor(if (y0 < y2) y0 else y2)
		rectangle.height = ceil((if (y1 > y3) y1 else y3) - rectangle.y)
	}

	fun toMatrix2d(m: Matrix2d) {
		m.a = this.a
		m.b = this.b
		m.c = this.c
		m.d = this.d
		m.tx = this.tx
		m.ty = this.ty
	}
}
