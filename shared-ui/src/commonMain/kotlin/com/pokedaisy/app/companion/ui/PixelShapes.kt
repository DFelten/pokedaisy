package com.pokedaisy.app.companion.ui

import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The app's corners are pixel art, never smooth arcs: every "rounded" rect is
 * a staircase of whole GBA pixels ([gbaPixelPx] screen pixels each - the same
 * unit as [GbaTextMetrics.px]), like the game's own window corners.
 */

/** One GBA pixel in screen pixels at this density (matches [rememberGbaTextMetrics]). */
fun Density.gbaPixelPx(): Float = (20.dp.toPx() / 16f).roundToInt().coerceAtLeast(2).toFloat()

/**
 * A pixel-stepped rounded rect path. [radius] is coerced to half the smaller
 * side (so a huge radius makes a pixel pill / circle) and to whole [step]s.
 */
fun pixelRoundRectPath(size: Size, radius: Float, step: Float, offset: Offset = Offset.Zero): Path =
    pixelCornersPath(size, radius, radius, radius, radius, step, offset)

/**
 * [pixelRoundRectPath] with a radius per corner (top-left, top-right, bottom-right, bottom-left): a shoulder button's
 * one big corner. Each is coerced like the single radius.
 */
fun pixelCornersPath(
    size: Size, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float, step: Float, offset: Offset = Offset.Zero,
): Path {
    val w = size.width
    val h = size.height
    // A smooth skin (CompanionSkin): the same corners as true arcs.
    if (CompanionSkins.current.smooth) {
        fun r(v: Float) = androidx.compose.ui.geometry.CornerRadius(v.coerceAtMost(minOf(w, h) / 2f).coerceAtLeast(0f))
        return Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(
                offset.x, offset.y, offset.x + w, offset.y + h,
                topLeftCornerRadius = r(topLeft), topRightCornerRadius = r(topRight),
                bottomRightCornerRadius = r(bottomRight), bottomLeftCornerRadius = r(bottomLeft),
            ))
        }
    }
    // Row j of a corner (from the outer edge) is inset insets[j] steps: a
    // quarter circle of radius n, sampled at each row's outer edge.
    class Corner(radius: Float) {
        val n = floor(radius.coerceAtMost(minOf(w, h) / 2f).coerceAtLeast(0f) / step).toInt()
        private val insets = IntArray(n) { j -> n - floor(sqrt((n * n - (n - j) * (n - j)).toFloat())).toInt() }
        fun inset(j: Int) = if (j < n) insets[j] * step else 0f
    }
    val tl = Corner(topLeft)
    val tr = Corner(topRight)
    val br = Corner(bottomRight)
    val bl = Corner(bottomLeft)
    val x0 = offset.x
    val y0 = offset.y
    return Path().apply {
        moveTo(x0 + tl.inset(0), y0)
        lineTo(x0 + w - tr.inset(0), y0)
        // Top-right, walking down.
        for (j in 0 until tr.n) {
            lineTo(x0 + w - tr.inset(j), y0 + (j + 1) * step)
            lineTo(x0 + w - tr.inset(j + 1), y0 + (j + 1) * step)
        }
        lineTo(x0 + w, y0 + h - br.n * step)
        // Bottom-right, walking down.
        for (j in br.n - 1 downTo 0) {
            lineTo(x0 + w - br.inset(j), y0 + h - (j + 1) * step)
            lineTo(x0 + w - br.inset(j), y0 + h - j * step)
        }
        lineTo(x0 + bl.inset(0), y0 + h)
        // Bottom-left, walking up.
        for (j in 0 until bl.n) {
            lineTo(x0 + bl.inset(j), y0 + h - (j + 1) * step)
            lineTo(x0 + bl.inset(j + 1), y0 + h - (j + 1) * step)
        }
        lineTo(x0, y0 + tl.n * step)
        // Top-left, walking up.
        for (j in tl.n - 1 downTo 0) {
            lineTo(x0 + tl.inset(j), y0 + (j + 1) * step)
            lineTo(x0 + tl.inset(j), y0 + j * step)
        }
        close()
    }
}

/** [RoundedCornerShape]'s pixel-art stand-in. [radius] Dp.Infinity = a pill / circle. */
class PixelRoundedShape(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val step = density.gbaPixelPx()
        val r = if (radius == Dp.Infinity) Float.MAX_VALUE else with(density) { radius.toPx() }
        return Outline.Generic(pixelRoundRectPath(size, r, step))
    }

    override fun equals(other: Any?) = other is PixelRoundedShape && other.radius == radius
    override fun hashCode() = radius.hashCode()

    /** The same corners smooth: a rounded rectangle, which every renderer clips to on the GPU ([pixelClip]). */
    val smooth: Shape get() = if (radius == Dp.Infinity) androidx.compose.foundation.shape.RoundedCornerShape(50) else androidx.compose.foundation.shape.RoundedCornerShape(radius)
}

/**
 * Clips to [shape], but while LIGHT COMPANION is on to its smooth twin ([PixelRoundedShape.smooth]). Android 11's
 * renderer can't clip to a path on the GPU: each redraw drew the clip as a mask on the CPU and uploaded it - the whole
 * companion window's took a Galaxy Tab A 160 ms a frame (2026-11-08). A rounded rectangle it clips without one.
 * Backgrounds that only need the shape's corners draw it ([androidx.compose.foundation.background] with the shape) and
 * clip nothing.
 */
fun androidx.compose.ui.Modifier.pixelClip(shape: Shape): androidx.compose.ui.Modifier =
    clip(if (shape is PixelRoundedShape && com.pokedaisy.app.companion.LightCompanion.on) shape.smooth else shape)

/** A pixel pill (or circle, when square) - replaces `CircleShape` / `RoundedCornerShape(50)`. */
val PixelPillShape = PixelRoundedShape(Dp.Infinity)

/** drawRoundRect, pixel-stepped. [radius] and [step] in px. Drawn as rows of rectangles ([Stairs]), not a path. */
fun DrawScope.drawPixelRoundRect(
    color: Color,
    topLeft: Offset = Offset.Zero,
    size: Size = this.size,
    radius: Float,
    step: Float = gbaPixelPx(),
) {
    if (size.width <= 0f || size.height <= 0f) return
    // A smooth skin's corners are arcs: a rounded rectangle, which the GPU fills by itself.
    if (CompanionSkins.current.smooth) {
        drawPath(pixelRoundRectPath(size, radius, step, topLeft), color)
        return
    }
    drawStairs(color, Stairs(size, radius, radius, radius, radius, step), topLeft, null, 0f)
}

/** [drawPixelRoundRect] with a radius per corner (top-left, top-right, bottom-right, bottom-left): a panel's header band
 * rounding with its window's top only. */
fun DrawScope.drawPixelCornersRect(
    color: Color, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float,
    at: Offset = Offset.Zero, size: Size = this.size, step: Float = gbaPixelPx(),
) {
    if (size.width <= 0f || size.height <= 0f) return
    if (CompanionSkins.current.smooth) {
        drawPath(pixelCornersPath(size, topLeft, topRight, bottomRight, bottomLeft, step, at), color)
        return
    }
    drawStairs(color, Stairs(size, topLeft, topRight, bottomRight, bottomLeft, step), at, null, 0f)
}

/** A pixel-stepped rounded frame [width] px thick: the outer shape minus the inner one. */
fun DrawScope.drawPixelRoundFrame(
    color: Color,
    topLeft: Offset,
    size: Size,
    radius: Float,
    width: Float,
    step: Float = gbaPixelPx(),
) {
    if (size.width <= 2 * width || size.height <= 2 * width) {
        drawPixelRoundRect(color, topLeft, size, radius, step)
        return
    }
    val innerSize = Size(size.width - 2 * width, size.height - 2 * width)
    val innerRadius = (radius - width).coerceAtLeast(0f)
    if (CompanionSkins.current.smooth) {
        val outer = pixelRoundRectPath(size, radius, step, topLeft)
        val inner = pixelRoundRectPath(innerSize, innerRadius, step, Offset(topLeft.x + width, topLeft.y + width))
        drawPath(Path().apply { op(outer, inner, PathOperation.Difference) }, color)
        return
    }
    drawStairs(
        color, Stairs(size, radius, radius, radius, radius, step), topLeft,
        Stairs(innerSize, innerRadius, innerRadius, innerRadius, innerRadius, step), width,
    )
}

/**
 * A pixel-cornered rect as the rows it's made of. A staircase corner is a path the GPU can't fill by itself: with
 * anti-aliasing, Android's renderer paints each one into a mask on the CPU and uploads it as a texture, a window's
 * whole size, every time its layer is drawn again. On the Retroid Pocket Classic (2026-11-22) SETTINGS spent ~9 ms
 * of its RenderThread and ~15 ms of GPU a frame on them (77 MB of masks), so the menus stuttered at a low CPU
 * reading. The same shape as a few rectangles, one per run of rows with the same insets, the GPU draws directly.
 */
private class Stairs(size: Size, topLeft: Float, topRight: Float, bottomRight: Float, bottomLeft: Float, val step: Float) {
    val w = size.width
    val h = size.height
    private val limit = minOf(w, h) / 2f
    private fun steps(radius: Float) = floor(radius.coerceAtMost(limit).coerceAtLeast(0f) / step).toInt()
    private val tl = staircase(steps(topLeft))
    private val tr = staircase(steps(topRight))
    private val br = staircase(steps(bottomRight))
    private val bl = staircase(steps(bottomLeft))

    /** The left / right edge of the row through [y] (both from the shape's own top left). */
    fun left(y: Float): Float = inset(y, tl, bl) * step
    fun right(y: Float): Float = w - inset(y, tr, br) * step

    private fun inset(y: Float, top: IntArray, bottom: IntArray): Int {
        val fromTop = floor(y / step).toInt()
        if (fromTop < top.size) return top[fromTop]
        val fromBottom = floor((h - y) / step).toInt()
        return if (fromBottom < bottom.size) bottom[fromBottom] else 0
    }

    /** Every y (from [dy]) where a row's insets can change: the edges and each corner row's. */
    fun breaks(into: FloatList, dy: Float) {
        into.add(dy)
        into.add(dy + h)
        for (j in 1..maxOf(tl.size, tr.size)) into.add(dy + j * step)
        for (j in 1..maxOf(bl.size, br.size)) into.add(dy + h - j * step)
    }
}

/** Row j of a corner of [n] steps (from the outer edge) is inset this many steps: a quarter circle of radius n, sampled at each row's outer edge. */
private fun staircase(n: Int) = IntArray(n) { j -> n - floor(sqrt((n * n - (n - j) * (n - j)).toFloat())).toInt() }

/** A growable float array, so a draw makes no boxed Floats. */
private class FloatList {
    var data = FloatArray(32)
    var size = 0
    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }
}

/**
 * No anti-aliasing, so rows meeting at a fraction of a pixel leave no seam. A spare one kept for reuse (bitmaps are drawn
 * off the UI thread too: a draw takes it, or makes its own while another thread has it, and gives it back), so drawing
 * allocates none in the usual case. Common code: no ThreadLocal.
 */
private val stairPaintSpare = kotlinx.atomicfu.atomic<androidx.compose.ui.graphics.Paint?>(null)

/**
 * Fills [outer] at [at], less [inner] at [at] + ([inset], [inset]) when there is one (a frame): row by row, rows with
 * the same spans merged into one rectangle each.
 */
private fun DrawScope.drawStairs(color: Color, outer: Stairs, at: Offset, inner: Stairs?, inset: Float) {
    val ys = FloatList()
    outer.breaks(ys, 0f)
    inner?.breaks(ys, inset)
    ys.data.sort(0, ys.size)
    val stairPaint = stairPaintSpare.getAndSet(null) ?: androidx.compose.ui.graphics.Paint().apply { isAntiAlias = false }
    stairPaint.color = color
    val canvas = drawContext.canvas
    // The run being built: its top, and its spans (b2 < a2 when there is only one).
    var runTop = Float.NaN
    var runBottom = 0f
    var a1 = 0f; var b1 = 0f; var a2 = 0f; var b2 = -1f
    fun flush() {
        if (runTop.isNaN()) return
        if (b1 > a1) canvas.drawRect(at.x + a1, at.y + runTop, at.x + b1, at.y + runBottom, stairPaint)
        if (b2 > a2) canvas.drawRect(at.x + a2, at.y + runTop, at.x + b2, at.y + runBottom, stairPaint)
    }
    var y0 = ys.data[0]
    for (i in 1 until ys.size) {
        val y1 = ys.data[i]
        if (y1 - y0 < 0.01f) continue
        val mid = (y0 + y1) / 2f
        val ol = outer.left(mid)
        val or = outer.right(mid)
        var n1 = ol; var m1 = or; var n2 = 0f; var m2 = -1f
        if (inner != null && mid > inset && mid < inset + inner.h) {
            val il = inset + inner.left(mid - inset)
            val ir = inset + inner.right(mid - inset)
            if (ir > il) { m1 = il; n2 = ir; m2 = or }
        }
        if (!runTop.isNaN() && n1 == a1 && m1 == b1 && n2 == a2 && m2 == b2) {
            runBottom = y1
        } else {
            flush()
            runTop = y0; runBottom = y1
            a1 = n1; b1 = m1; a2 = n2; b2 = m2
        }
        y0 = y1
    }
    flush()
    stairPaintSpare.value = stairPaint
}
