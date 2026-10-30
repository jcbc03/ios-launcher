package com.jcbc03.ioslauncher

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Superellipse (n=5) – continuous-corner rounded square, original geometry. */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val n = 5.0
        val a = size.width / 2f
        val b = size.height / 2f
        val path = Path()
        val steps = 96
        for (i in 0..steps) {
            val t = 2 * Math.PI * i / steps
            val c = cos(t); val s = sin(t)
            val x = (abs(c).pow(2 / n) * a * sign(c)).toFloat() + a
            val y = (abs(s).pow(2 / n) * b * sign(s)).toFloat() + b
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
