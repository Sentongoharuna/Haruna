package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * V261 screen-only Director Monitor.
 *
 * Waveform, false colour, RGB parade, vectorscope and advanced guides are
 * generated only from PreviewView snapshots / Canvas drawing. They are never
 * connected to CameraX video or image output, so Clean Master stays clean.
 */
class DevelopUgandaV255ProMonitorView(
    context: Context
) : View(context) {

    private val worker = Executors.newSingleThreadExecutor()
    private val busy = AtomicBoolean(false)

    @Volatile private var waveformEnabled = false
    @Volatile private var falseColorEnabled = false
    @Volatile private var falseColorStrength = 0.40f
    @Volatile private var rgbParadeEnabled = false
    @Volatile private var vectorscopeEnabled = false
    @Volatile private var skinToneReferenceEnabled = true
    @Volatile private var highlightShadowAssistEnabled = true
    @Volatile private var frameGuidesEnabled = true
    @Volatile private var frameGuideAspect = "2.39:1"
    @Volatile private var directorHudMode = "STANDARD"

    private val waveCols = 64
    private val waveRows = 48
    @Volatile private var waveform = IntArray(waveCols * waveRows)
    @Volatile private var waveformMax = 1

    private val falseCols = 18
    private val falseRows = 12
    @Volatile private var falseGrid = IntArray(falseCols * falseRows) { 128 }

    private val paradeCols = 48
    private val paradeRows = 38
    @Volatile private var paradeR = IntArray(paradeCols * paradeRows)
    @Volatile private var paradeG = IntArray(paradeCols * paradeRows)
    @Volatile private var paradeB = IntArray(paradeCols * paradeRows)
    @Volatile private var paradeMax = 1

    private val scopeSize = 52
    @Volatile private var vectorScope = IntArray(scopeSize * scopeSize)
    @Volatile private var vectorMax = 1

    @Volatile private var highlightPct = 0f
    @Volatile private var shadowPct = 0f
    @Volatile private var midPct = 0f

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD6031829.toInt()
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x8873B7D9.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF73B7D9.toInt()
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x33456983
        strokeWidth = 1f
    }
    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xCCD0B06F.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val guideSoftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x7773B7D9
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFB8CBD6.toInt()
        textSize = 15f
    }

    fun setWaveformEnabled(value: Boolean) {
        waveformEnabled = value
        postInvalidateOnAnimation()
    }

    fun setFalseColorEnabled(value: Boolean) {
        falseColorEnabled = value
        postInvalidateOnAnimation()
    }

    fun setFalseColorStrength(value: Float) {
        falseColorStrength = value.coerceIn(0.18f, 0.72f)
        postInvalidateOnAnimation()
    }

    fun setRgbParadeEnabled(value: Boolean) {
        rgbParadeEnabled = value
        postInvalidateOnAnimation()
    }

    fun setVectorscopeEnabled(value: Boolean) {
        vectorscopeEnabled = value
        postInvalidateOnAnimation()
    }

    fun setSkinToneReferenceEnabled(value: Boolean) {
        skinToneReferenceEnabled = value
        postInvalidateOnAnimation()
    }

    fun setHighlightShadowAssistEnabled(value: Boolean) {
        highlightShadowAssistEnabled = value
        postInvalidateOnAnimation()
    }

    fun setFrameGuidesEnabled(value: Boolean) {
        frameGuidesEnabled = value
        postInvalidateOnAnimation()
    }

    fun setFrameGuideAspect(value: String) {
        frameGuideAspect = value
        postInvalidateOnAnimation()
    }

    fun setDirectorHudMode(value: String) {
        directorHudMode = when (value.uppercase(Locale.US)) {
            "MINIMAL" -> "MINIMAL"
            "FULL" -> "FULL"
            else -> "STANDARD"
        }
        postInvalidateOnAnimation()
    }

    fun needsFrames(): Boolean =
        waveformEnabled || falseColorEnabled || rgbParadeEnabled || vectorscopeEnabled || highlightShadowAssistEnabled

    fun submitFrame(source: Bitmap) {
        if (!needsFrames() || source.width <= 0 || source.height <= 0 || !busy.compareAndSet(false, true)) {
            return
        }

        worker.execute {
            var scaled: Bitmap? = null
            try {
                val targetWidth = minOf(420, source.width).coerceAtLeast(120)
                val targetHeight = max(
                    96,
                    (source.height.toFloat() * targetWidth.toFloat() / source.width.toFloat()).roundToInt()
                )
                scaled = Bitmap.createScaledBitmap(source, targetWidth, targetHeight, false)
                analyse(scaled)
                postInvalidateOnAnimation()
            } catch (_: Exception) {
            } finally {
                try { scaled?.recycle() } catch (_: Exception) { }
                busy.set(false)
            }
        }
    }

    private fun analyse(bitmap: Bitmap) {
        val wave = IntArray(waveCols * waveRows)
        val sums = LongArray(falseCols * falseRows)
        val counts = IntArray(falseCols * falseRows)
        val pr = IntArray(paradeCols * paradeRows)
        val pg = IntArray(paradeCols * paradeRows)
        val pb = IntArray(paradeCols * paradeRows)
        val vector = IntArray(scopeSize * scopeSize)
        var maxWave = 1
        var maxParade = 1
        var maxVector = 1
        var highlight = 0L
        var shadow = 0L
        var mid = 0L
        var samples = 0L

        val step = 4
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)
                val luma = ((r * 54 + g * 183 + b * 19) / 256).coerceIn(0, 255)

                if (waveformEnabled) {
                    val wx = (x * waveCols / bitmap.width).coerceIn(0, waveCols - 1)
                    val wy = ((255 - luma) * waveRows / 256).coerceIn(0, waveRows - 1)
                    val wi = wy * waveCols + wx
                    wave[wi] += 1
                    if (wave[wi] > maxWave) maxWave = wave[wi]
                }

                if (falseColorEnabled) {
                    val gx = (x * falseCols / bitmap.width).coerceIn(0, falseCols - 1)
                    val gy = (y * falseRows / bitmap.height).coerceIn(0, falseRows - 1)
                    val gi = gy * falseCols + gx
                    sums[gi] += luma.toLong()
                    counts[gi] += 1
                }

                if (rgbParadeEnabled) {
                    val px = (x * paradeCols / bitmap.width).coerceIn(0, paradeCols - 1)
                    fun add(channel: Int, target: IntArray) {
                        val py = ((255 - channel) * paradeRows / 256).coerceIn(0, paradeRows - 1)
                        val i = py * paradeCols + px
                        target[i] += 1
                        if (target[i] > maxParade) maxParade = target[i]
                    }
                    add(r, pr)
                    add(g, pg)
                    add(b, pb)
                }

                if (vectorscopeEnabled) {
                    // BT.601-style chroma projection for a truthful preview scope.
                    val u = (-0.14713 * r - 0.28886 * g + 0.43600 * b + 128.0).roundToInt().coerceIn(0, 255)
                    val v = ( 0.61500 * r - 0.51499 * g - 0.10001 * b + 128.0).roundToInt().coerceIn(0, 255)
                    val sx = (u * scopeSize / 256).coerceIn(0, scopeSize - 1)
                    val sy = ((255 - v) * scopeSize / 256).coerceIn(0, scopeSize - 1)
                    val i = sy * scopeSize + sx
                    vector[i] += 1
                    if (vector[i] > maxVector) maxVector = vector[i]
                }

                if (highlightShadowAssistEnabled) {
                    samples += 1
                    when {
                        luma >= 238 -> highlight += 1
                        luma <= 20 -> shadow += 1
                        luma in 70..190 -> mid += 1
                    }
                }
                x += step
            }
            y += step
        }

        if (waveformEnabled) {
            waveform = wave
            waveformMax = maxWave.coerceAtLeast(1)
        }
        if (falseColorEnabled) {
            val grid = IntArray(falseCols * falseRows)
            for (i in grid.indices) {
                grid[i] = if (counts[i] > 0) (sums[i] / counts[i]).toInt().coerceIn(0, 255) else 128
            }
            falseGrid = grid
        }
        if (rgbParadeEnabled) {
            paradeR = pr
            paradeG = pg
            paradeB = pb
            paradeMax = maxParade.coerceAtLeast(1)
        }
        if (vectorscopeEnabled) {
            vectorScope = vector
            vectorMax = maxVector.coerceAtLeast(1)
        }
        if (highlightShadowAssistEnabled && samples > 0) {
            highlightPct = highlight.toFloat() * 100f / samples.toFloat()
            shadowPct = shadow.toFloat() * 100f / samples.toFloat()
            midPct = mid.toFloat() * 100f / samples.toFloat()
        }
    }

    private fun falseColorFor(luma: Int): Int {
        return when {
            luma <= 28 -> 0xFF031829.toInt()
            luma <= 62 -> 0xFF456983.toInt()
            luma <= 170 -> 0xFF91B6A0.toInt()
            luma <= 228 -> 0xFFD0B06F.toInt()
            else -> 0xFFC76D73.toInt()
        }
    }

    private fun drawWaveform(canvas: Canvas, w: Float, h: Float) {
        val panelWidth = minOf(360f, w * 0.43f)
        val left = (w - panelWidth - 18f).coerceAtLeast(18f)
        val top = (h * 0.57f).coerceIn(190f, h - 300f)
        val right = w - 18f
        val bottom = top + 92f
        canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, panelPaint)
        canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, borderPaint)
        val pad = 10f
        val graphLeft = left + pad
        val graphTop = top + 28f
        val graphRight = right - pad
        val graphBottom = bottom - 10f
        for (i in 1..3) {
            val gy = graphTop + (graphBottom - graphTop) * i / 4f
            canvas.drawLine(graphLeft, gy, graphRight, gy, gridPaint)
        }
        val wave = waveform
        val maxV = waveformMax.coerceAtLeast(1)
        val dx = (graphRight - graphLeft) / waveCols.toFloat()
        val dy = (graphBottom - graphTop) / waveRows.toFloat()
        for (wy in 0 until waveRows) {
            for (wx in 0 until waveCols) {
                val value = wave[wy * waveCols + wx]
                if (value <= 0) continue
                val frac = (value.toFloat() / maxV.toFloat()).coerceIn(0f, 1f)
                wavePaint.alpha = (65 + frac * 190f).roundToInt().coerceIn(65, 255)
                val px = graphLeft + wx * dx
                val py = graphTop + wy * dy
                canvas.drawRect(px, py, px + max(1f, dx * 0.72f), py + max(1f, dy * 0.72f), wavePaint)
            }
        }
        wavePaint.alpha = 255
        canvas.drawText("WAVEFORM • LUMA", left + 10f, top + 21f, labelPaint)
    }

    private fun drawRgbParade(canvas: Canvas, w: Float, h: Float) {
        val panelW = minOf(380f, w * 0.48f)
        val left = 18f
        val top = (h * 0.57f).coerceIn(190f, h - 300f)
        val right = left + panelW
        val bottom = top + 110f
        canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, panelPaint)
        canvas.drawRoundRect(left, top, right, bottom, 16f, 16f, borderPaint)
        canvas.drawText("RGB PARADE", left + 10f, top + 20f, labelPaint)
        val gap = 6f
        val graphTop = top + 30f
        val graphBottom = bottom - 9f
        val totalW = right - left - 20f
        val eachW = (totalW - gap * 2f) / 3f
        val arrays = arrayOf(paradeR, paradeG, paradeB)
        val colors = intArrayOf(0xFFE77B82.toInt(), 0xFF91B6A0.toInt(), 0xFF73B7D9.toInt())
        val labels = arrayOf("R", "G", "B")
        val maxV = paradeMax.coerceAtLeast(1)
        for (c in 0..2) {
            val gx0 = left + 10f + c * (eachW + gap)
            val gx1 = gx0 + eachW
            canvas.drawRect(gx0, graphTop, gx1, graphBottom, gridPaint)
            val dx = eachW / paradeCols.toFloat()
            val dy = (graphBottom - graphTop) / paradeRows.toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[c]; style = Paint.Style.FILL }
            val data = arrays[c]
            for (py in 0 until paradeRows) {
                for (px in 0 until paradeCols) {
                    val value = data[py * paradeCols + px]
                    if (value <= 0) continue
                    val frac = (value.toFloat() / maxV.toFloat()).coerceIn(0f, 1f)
                    p.alpha = (55 + frac * 200f).roundToInt().coerceIn(55, 255)
                    val x = gx0 + px * dx
                    val y = graphTop + py * dy
                    canvas.drawRect(x, y, x + max(1f, dx * 0.8f), y + max(1f, dy * 0.8f), p)
                }
            }
            smallPaint.color = colors[c]
            canvas.drawText(labels[c], gx0 + 2f, graphBottom - 2f, smallPaint)
        }
        smallPaint.color = 0xFFB8CBD6.toInt()
    }

    private fun drawVectorscope(canvas: Canvas, w: Float, h: Float) {
        val size = minOf(220f, w * 0.36f)
        val left = w - size - 18f
        val top = 72f
        val right = left + size
        val bottom = top + size
        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, panelPaint)
        canvas.drawRoundRect(left, top, right, bottom, 18f, 18f, borderPaint)
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f + 7f
        val radius = size * 0.39f
        canvas.drawCircle(cx, cy, radius, guideSoftPaint)
        canvas.drawLine(cx - radius, cy, cx + radius, cy, gridPaint)
        canvas.drawLine(cx, cy - radius, cx, cy + radius, gridPaint)

        if (skinToneReferenceEnabled) {
            // Conventional skin-tone reference direction, shown as an app-gold guide.
            val angle = Math.toRadians(-57.0)
            val ex = cx + cos(angle).toFloat() * radius * 0.92f
            val ey = cy + sin(angle).toFloat() * radius * 0.92f
            guidePaint.strokeWidth = 1.6f
            canvas.drawLine(cx, cy, ex, ey, guidePaint)
            guidePaint.strokeWidth = 2f
        }

        val data = vectorScope
        val maxV = vectorMax.coerceAtLeast(1)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF73B7D9.toInt(); style = Paint.Style.FILL }
        val cell = radius * 2f / scopeSize.toFloat()
        for (sy in 0 until scopeSize) {
            for (sx in 0 until scopeSize) {
                val value = data[sy * scopeSize + sx]
                if (value <= 0) continue
                val frac = (value.toFloat() / maxV.toFloat()).coerceIn(0f, 1f)
                p.alpha = (45 + frac * 210f).roundToInt().coerceIn(45, 255)
                val x = cx - radius + sx * cell
                val y = cy - radius + sy * cell
                canvas.drawCircle(x, y, max(1.2f, cell * 0.38f), p)
            }
        }
        canvas.drawText("VECTORSCOPE", left + 10f, top + 21f, labelPaint)
        if (skinToneReferenceEnabled) {
            smallPaint.color = 0xFFD0B06F.toInt()
            canvas.drawText("SKIN LINE", left + 10f, bottom - 10f, smallPaint)
            smallPaint.color = 0xFFB8CBD6.toInt()
        }
    }

    private fun guideRatio(label: String): Float =
        when (label.uppercase(Locale.US)) {
            "1.85:1" -> 1.85f
            "16:9" -> 16f / 9f
            "9:16" -> 9f / 16f
            "4:5" -> 4f / 5f
            else -> 2.39f
        }

    private fun drawFrameGuides(canvas: Canvas, w: Float, h: Float) {
        val ratio = guideRatio(frameGuideAspect)
        val maxW = w * 0.90f
        val maxH = h * 0.86f
        val guideW: Float
        val guideH: Float
        if (maxW / maxH > ratio) {
            guideH = maxH
            guideW = guideH * ratio
        } else {
            guideW = maxW
            guideH = guideW / ratio
        }
        val left = (w - guideW) / 2f
        val top = (h - guideH) / 2f
        val right = left + guideW
        val bottom = top + guideH
        canvas.drawRoundRect(left, top, right, bottom, 8f, 8f, guidePaint)

        val safeX = guideW * 0.05f
        val safeY = guideH * 0.05f
        canvas.drawRect(left + safeX, top + safeY, right - safeX, bottom - safeY, guideSoftPaint)
        val thirdW = guideW / 3f
        val thirdH = guideH / 3f
        canvas.drawLine(left + thirdW, top, left + thirdW, bottom, guideSoftPaint)
        canvas.drawLine(left + thirdW * 2f, top, left + thirdW * 2f, bottom, guideSoftPaint)
        canvas.drawLine(left, top + thirdH, right, top + thirdH, guideSoftPaint)
        canvas.drawLine(left, top + thirdH * 2f, right, top + thirdH * 2f, guideSoftPaint)
        canvas.drawText(frameGuideAspect, left + 8f, top - 8f, smallPaint)
    }

    private fun drawExposureAssist(canvas: Canvas, w: Float) {
        if (!highlightShadowAssistEnabled) return
        val warnHighlight = highlightPct >= 3.0f
        val warnShadow = shadowPct >= 18.0f
        if (directorHudMode == "MINIMAL" && !warnHighlight && !warnShadow) return

        val left = 18f
        val top = 72f
        val right = minOf(w - 18f, left + 330f)
        val bottom = top + if (directorHudMode == "FULL") 76f else 54f
        canvas.drawRoundRect(left, top, right, bottom, 14f, 14f, panelPaint)
        canvas.drawRoundRect(left, top, right, bottom, 14f, 14f, borderPaint)
        val hi = if (warnHighlight) 0xFFC76D73.toInt() else 0xFFD0B06F.toInt()
        val sh = if (warnShadow) 0xFF73B7D9.toInt() else 0xFF91B6A0.toInt()
        smallPaint.color = hi
        canvas.drawText(String.format(Locale.US, "HIGHLIGHT %.1f%%", highlightPct), left + 10f, top + 22f, smallPaint)
        smallPaint.color = sh
        canvas.drawText(String.format(Locale.US, "SHADOW %.1f%%", shadowPct), left + 160f, top + 22f, smallPaint)
        if (directorHudMode == "FULL") {
            smallPaint.color = 0xFFB8CBD6.toInt()
            canvas.drawText(String.format(Locale.US, "MID DETAIL %.1f%%", midPct), left + 10f, top + 48f, smallPaint)
        }
        smallPaint.color = 0xFFB8CBD6.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (falseColorEnabled) {
            val alpha = (falseColorStrength * 190f).roundToInt().coerceIn(35, 150)
            val cellW = w / falseCols.toFloat()
            val cellH = h / falseRows.toFloat()
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            val grid = falseGrid
            for (gy in 0 until falseRows) {
                for (gx in 0 until falseCols) {
                    val c = falseColorFor(grid[gy * falseCols + gx])
                    p.color = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))
                    canvas.drawRect(
                        gx * cellW,
                        gy * cellH,
                        (gx + 1) * cellW + 1f,
                        (gy + 1) * cellH + 1f,
                        p
                    )
                }
            }
        }

        if (frameGuidesEnabled) drawFrameGuides(canvas, w, h)
        drawExposureAssist(canvas, w)
        if (vectorscopeEnabled) drawVectorscope(canvas, w, h)
        if (waveformEnabled) drawWaveform(canvas, w, h)
        if (rgbParadeEnabled) drawRgbParade(canvas, w, h)

        if (directorHudMode == "FULL") {
            smallPaint.color = 0xFFD0B06F.toInt()
            canvas.drawText("V261 • DIRECTOR MONITOR", 18f, h - 32f, smallPaint)
            smallPaint.color = 0xFFB8CBD6.toInt()
        }
    }

    override fun onDetachedFromWindow() {
        try { worker.shutdownNow() } catch (_: Exception) { }
        super.onDetachedFromWindow()
    }
}
