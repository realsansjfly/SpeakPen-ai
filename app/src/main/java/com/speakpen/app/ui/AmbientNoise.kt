package com.speakpen.app.ui

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

/** Scalar Kotlin adaptation of Ashima Arts' MIT-licensed noise2D.glsl.
 * Copyright (C) 2011 Ashima Arts. Full license: assets/licenses/webgl-noise-MIT.txt.
 * No GPU shader/API requirement: only a handful of samples drive bounded soft color fields.
 */
internal object AmbientNoise {
    private fun mod289(x: Double) = x - floor(x / 289.0) * 289.0
    private fun permute(x: Double) = mod289(((x * 34.0) + 10.0) * x)
    private fun corner(x: Double, y: Double, permutation: Double): Double {
        var m = max(.5 - x * x - y * y, 0.0); m *= m; m *= m
        val q = permutation / 41.0
        val gradient = 2.0 * (q - floor(q)) - 1.0
        val h = abs(gradient) - .5
        val a = gradient - floor(gradient + .5)
        return m * (1.79284291400159 - .85373472095314 * (a * a + h * h)) * (a * x + h * y)
    }
    fun sample(x: Double, y: Double): Float {
        val skew = (x + y) * .366025403784439
        val i = floor(x + skew); val j = floor(y + skew)
        val unskew = (i + j) * .211324865405187
        val x0 = x - i + unskew; val y0 = y - j + unskew
        val ix = if (x0 > y0) 1.0 else 0.0; val iy = 1.0 - ix
        val a = mod289(i); val b = mod289(j)
        val n = corner(x0, y0, permute(permute(b) + a)) +
            corner(x0 - ix + .211324865405187, y0 - iy + .211324865405187, permute(permute(b + iy) + a + ix)) +
            corner(x0 - .577350269189626, y0 - .577350269189626, permute(permute(b + 1.0) + a + 1.0))
        return (130.0 * n).toFloat().coerceIn(-1f, 1f)
    }
}
