package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.view.View
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Interview Cam's preview-only instrument panel. It is intentionally not a
 * control surface: every value is read from a camera, recorder, storage,
 * battery, thermal or on-device observation source.  A missing source yields
 * no numeric read-out; it is never filled with a simulated value.
 */
class DevelopUgandaInterviewHudView(context: Context) : View(context) {

    data class Telemetry(
        val iso: Int? = null,
        val shutterNs: Long? = null,
        val whiteBalanceKelvin: Int? = null,
        val exposureEv: Float? = null,
        val aeState: String? = null,
        val awbState: String? = null,
        val audioDbfs: Double? = null,
        val peakDbfs: Double? = null,
        val headroomDb: Double? = null,
        val activeMic: String? = null,
        val timecode: String? = null,
        val takeNumber: Int? = null,
        val remainingMinutes: Long? = null,
        val batteryPercent: Int? = null,
        val thermalState: String? = null,
        val currentSilenceMs: Long? = null
    )

    private val backing = context.getColor(R.color.du_viewfinder_indicator_backing)
    private val content = context.getColor(R.color.du_content)
    private val dim = context.getColor(R.color.du_content_dim)
    private val accent = context.getColor(R.color.du_accent)
    private val success = context.getColor(R.color.du_record)
    private val warning = context.getColor(R.color.du_warning)
    private val outline = context.getColor(R.color.du_outline)

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = backing }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = outline }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent }
    private val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = success
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = content
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = dp(12f)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = dim
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        textSize = dp(8.5f)
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = content
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        textSize = dp(9f)
    }

    private var observation: DevelopUgandaInterviewObservationEngine.InterviewObservationSnapshot? = null
    private var telemetry = Telemetry()
    private var analysisEnabled = true
    private var subjectId: String? = null
    private var subjectLockedAtMs = 0L
    private var lastObservationAtMs = 0L
    private val trend = DevelopUgandaRollingMetricBuffer(
        capacity = 96,
        windowMs = 10_000L,
        minimumSampleIntervalMs = 125L,
    )
    private var lastInvalidateAtMs = 0L

    fun setSnapshot(value: DevelopUgandaInterviewObservationEngine.InterviewObservationSnapshot) {
        observation = value
        val now = SystemClock.elapsedRealtime()
        val locked = value.subjectId.takeIf { it.isNotBlank() && it != "--" }
        if (locked == null) {
            subjectId = null
            subjectLockedAtMs = 0L
            trend.clear()
        }
        if (locked != null && locked != subjectId) {
            subjectId = locked
            subjectLockedAtMs = now
            trend.clear()
        }
        if (locked != null) {
            lastObservationAtMs = now
            value.channels["COMPOSURE INDEX"]?.let { composure ->
                trend.offer(composure.coerceIn(0, 100).toDouble(), now)
            }
        }
        invalidateAtInstrumentRate()
    }

    fun setTelemetry(value: Telemetry) {
        telemetry = value
        invalidateAtInstrumentRate()
    }

    fun setAnalysisEnabled(value: Boolean) {
        analysisEnabled = value
        if (!value) {
            subjectId = null
            subjectLockedAtMs = 0L
            trend.clear()
        }
        invalidateAtInstrumentRate(force = true)
    }

    // Kept only as a compatibility no-op while FIVEMODS 9 moves these former
    // bottom-deck controls to the settings sheet. They never alter this HUD.
    fun setBurnInEnabled(@Suppress("UNUSED_PARAMETER") value: Boolean) = Unit
    fun setTwoShotEnabled(@Suppress("UNUSED_PARAMETER") value: Boolean) = Unit

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val safeBottom = height.toFloat() - max(dp(204f), height * 0.20f)
        val top = dp(68f)
        if (safeBottom <= top + dp(80f)) return
        canvas.save()
        canvas.clipRect(0f, top, width.toFloat(), safeBottom)

        val gap = dp(7f)
        val left = dp(8f)
        val panelWidth = ((width - dp(24f)) * 0.5f).coerceAtLeast(dp(170f))
        val right = left + panelWidth + gap
        val rightWidth = width - right - dp(8f)
        var leftY = top
        var rightY = top

        if (analysisEnabled) {
            drawExposureBlock(canvas, left, leftY, panelWidth)?.let { leftY += it + gap }
            drawAudioBlock(canvas, left, leftY, panelWidth)?.let { leftY += it + gap }
            drawTakeBlock(canvas, left, leftY, panelWidth)?.let { leftY += it + gap }
            drawSystemBlock(canvas, left, leftY, panelWidth)?.let { leftY += it + gap }
            drawSubjectBlock(canvas, left, leftY, panelWidth)?.let { leftY += it + gap }
            drawChannelBlock(canvas, right, rightY, rightWidth)?.let { rightY += it + gap }
            drawTrendBlock(canvas, right, rightY, rightWidth)
        } else {
            drawPanel(canvas, left, leftY, panelWidth, dp(42f))
            labelPaint.color = warning
            canvas.drawText("ANALYSIS OFF", left + dp(8f), leftY + dp(17f), labelPaint)
            labelPaint.color = dim
            canvas.drawText("FRAMING REMAINS ACTIVE", left + dp(8f), leftY + dp(31f), labelPaint)
        }

        canvas.restore()
        // This disclaimer remains verbatim and stays above the mode strip and
        // record row; no observation state is drawn below it.
        drawPanel(canvas, left, safeBottom + dp(4f), width - dp(16f), dp(36f))
        footerPaint.color = content
        canvas.drawText("Observed physical signals only.", left, safeBottom + dp(18f), footerPaint)
        canvas.drawText("Not an indicator of truthfulness.", left, safeBottom + dp(34f), footerPaint)
    }

    private fun drawExposureBlock(canvas: Canvas, x: Float, y: Float, w: Float): Float? {
        val lines = mutableListOf<String>()
        telemetry.iso?.let { lines += "ISO ${it} ${telemetry.aeState.orEmpty()}".trim() }
        telemetry.shutterNs?.takeIf { it > 0L }?.let { ns ->
            val denominator = max(1L, (1_000_000_000L + ns / 2L) / ns)
            lines += "SH 1/$denominator ${telemetry.aeState.orEmpty()}".trim()
        }
        telemetry.exposureEv?.let { ev ->
            lines += String.format(Locale.US, "EV %+.1f %s", ev, telemetry.aeState.orEmpty()).trim()
        }
        telemetry.whiteBalanceKelvin?.let { kelvin ->
            lines += "WB ${kelvin}K ${telemetry.awbState.orEmpty()}".trim()
        }
        if (lines.isEmpty()) return null
        return drawTextBlock(canvas, x, y, w, "EXPOSURE", lines)
    }

    private fun drawAudioBlock(canvas: Canvas, x: Float, y: Float, w: Float): Float? {
        val lines = mutableListOf<String>()
        telemetry.audioDbfs?.let { level ->
            val peak = telemetry.peakDbfs?.let { String.format(Locale.US, " PK %.1f", it) }.orEmpty()
            lines += String.format(Locale.US, "IN %.1f dBFS%s", level, peak)
        }
        telemetry.headroomDb?.let { lines += String.format(Locale.US, "HEADROOM %.1f dB", it) }
        telemetry.activeMic?.takeIf { it.isNotBlank() }?.let { lines += "MIC $it" }
        if (lines.isEmpty()) return null
        val h = drawTextBlock(canvas, x, y, w, "AUDIO", lines)
        telemetry.audioDbfs?.let { level ->
            val barLeft = x + dp(8f)
            val barTop = y + h - dp(10f)
            val barWidth = w - dp(16f)
            canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + barWidth, barTop + dp(4f)), dp(2f), dp(2f), trackPaint)
            val normalized = ((level + 60.0) / 60.0).toFloat().coerceIn(0f, 1f)
            fillPaint.color = if (level > -3.0) warning else accent
            canvas.drawRoundRect(RectF(barLeft, barTop, barLeft + barWidth * normalized, barTop + dp(4f)), dp(2f), dp(2f), fillPaint)
        }
        return h
    }

    private fun drawTakeBlock(canvas: Canvas, x: Float, y: Float, w: Float): Float? {
        val lines = mutableListOf<String>()
        telemetry.timecode?.takeIf { it.isNotBlank() }?.let { lines += "TC $it" }
        telemetry.takeNumber?.let { lines += "TAKE $it" }
        telemetry.remainingMinutes?.let { lines += "FREE ~${it} MIN" }
        telemetry.currentSilenceMs?.let { lines += "PAUSE ${formatDuration(it)}" }
        if (lines.isEmpty()) return null
        return drawTextBlock(canvas, x, y, w, "TAKE", lines)
    }

    private fun drawSystemBlock(canvas: Canvas, x: Float, y: Float, w: Float): Float? {
        val lines = mutableListOf<String>()
        telemetry.batteryPercent?.let { lines += "BAT $it%" }
        telemetry.thermalState?.takeIf {
            it.isNotBlank() && it !in setOf("NONE", "NORMAL", "LIGHT", "UNAVAILABLE", "UNKNOWN")
        }?.let {
            lines += "THERM $it"
        }
        if (lines.isEmpty()) return null
        return drawTextBlock(canvas, x, y, w, "POWER", lines)
    }

    private fun drawSubjectBlock(canvas: Canvas, x: Float, y: Float, w: Float): Float? {
        val locked = isSubjectLocked()
        val lines = if (!locked) {
            listOf("INACTIVE • NO SUBJECT LOCK")
        } else {
            val quality = trackingQuality()
            listOf(
                "LOCK ${formatDuration(SystemClock.elapsedRealtime() - subjectLockedAtMs)}",
                "TRACK QUALITY $quality%"
            )
        }
        return drawTextBlock(canvas, x, y, w, "SUBJECT", lines)
    }

    private fun drawChannelBlock(canvas: Canvas, x: Float, y: Float, w: Float): Float? {
        val locked = isSubjectLocked()
        val current = observation
        val entries: List<Pair<String, String?>> = if (!locked) {
            // SELF-TOUCH and FACIAL TENSION are not computed by the real
            // observation engine, so they are not advertised. Pace and pause
            // are derived only from an operator-confirmed transcript.
            listOf(
                "MOVE" to "INACTIVE",
                "POST" to "INACTIVE",
                "GAZE" to "INACTIVE",
                "BLINK" to "INACTIVE",
                "EXPR" to "INACTIVE",
                "PACE" to "AFTER REVIEW",
                "PAUSE" to "AFTER REVIEW",
                "COMP" to "INACTIVE",
            )
        } else {
            listOf(
                "MOVE" to current?.channels?.get("MOVEMENT ENERGY")?.toString(),
                "POST" to current?.channels?.get("POSTURAL SHIFT")?.toString(),
                "GAZE" to current?.channels?.get("GAZE")?.toString(),
                "BLINK" to current?.channels?.get("BLINK RATE")?.toString(),
                "EXPR" to current?.channels?.get("EXPRESSION MIX")?.toString(),
                "PACE" to "AFTER REVIEW",
                "PAUSE" to "AFTER REVIEW",
                "COMP" to current?.channels?.get("COMPOSURE INDEX")?.toString(),
            ).filter { it.second != null }
        }
        if (entries.isEmpty()) return null
        val row = dp(18f)
        val h = dp(22f) + row * entries.size + dp(7f)
        drawPanel(canvas, x, y, w, h)
        labelPaint.color = dim
        canvas.drawText("CHANNELS", x + dp(8f), y + dp(14f), labelPaint)
        entries.forEachIndexed { index, (name, value) ->
            val rowY = y + dp(20f) + row * index
            labelPaint.color = dim
            canvas.drawText(name, x + dp(8f), rowY + dp(11f), labelPaint)
            val score = value?.toIntOrNull()
            if (score == null) {
                valuePaint.color = dim
                canvas.drawText(value ?: "INACTIVE", x + dp(57f), rowY + dp(11f), valuePaint)
            } else {
                valuePaint.color = content
                canvas.drawText(score.toString().padStart(3, '0'), x + dp(57f), rowY + dp(11f), valuePaint)
                val barLeft = x + dp(88f)
                val barWidth = (w - dp(97f)).coerceAtLeast(dp(12f))
                canvas.drawRoundRect(RectF(barLeft, rowY + dp(4f), barLeft + barWidth, rowY + dp(11f)), dp(3f), dp(3f), trackPaint)
                fillPaint.color = if (name == "COMP") success else accent
                canvas.drawRoundRect(RectF(barLeft, rowY + dp(4f), barLeft + barWidth * (score / 100f), rowY + dp(11f)), dp(3f), dp(3f), fillPaint)
            }
        }
        return h
    }

    private fun drawTrendBlock(canvas: Canvas, x: Float, y: Float, w: Float): Float? {
        if (!isSubjectLocked()) return null
        val samples = trend.snapshot()
        if (samples.size < 2) return null
        val h = dp(66f)
        drawPanel(canvas, x, y, w, h)
        labelPaint.color = dim
        canvas.drawText("COMP • 10S ROLLING", x + dp(8f), y + dp(14f), labelPaint)
        val minX = x + dp(8f)
        val maxX = x + w - dp(8f)
        val minY = y + dp(24f)
        val maxY = y + h - dp(8f)
        val duration = (samples.last().elapsedRealtimeMs - samples.first().elapsedRealtimeMs).coerceAtLeast(1L)
        var previousX = minX
        var previousY = maxY - (samples.first().value.toFloat() / 100f) * (maxY - minY)
        samples.drop(1).forEach { sample ->
            val nx = minX + ((sample.elapsedRealtimeMs - samples.first().elapsedRealtimeMs).toFloat() / duration) * (maxX - minX)
            val ny = maxY - (sample.value.toFloat() / 100f) * (maxY - minY)
            canvas.drawLine(previousX, previousY, nx, ny, trendPaint)
            previousX = nx
            previousY = ny
        }
        return h
    }

    private fun drawTextBlock(canvas: Canvas, x: Float, y: Float, w: Float, heading: String, lines: List<String>): Float {
        val h = dp(22f) + lines.size * dp(16f) + dp(6f)
        drawPanel(canvas, x, y, w, h)
        labelPaint.color = dim
        canvas.drawText(heading, x + dp(8f), y + dp(14f), labelPaint)
        lines.forEachIndexed { index, line ->
            valuePaint.color = content
            canvas.drawText(line.take(34), x + dp(8f), y + dp(30f) + index * dp(16f), valuePaint)
        }
        return h
    }

    private fun drawPanel(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        panelPaint.color = backing
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), dp(7f), dp(7f), panelPaint)
    }

    private fun isSubjectLocked(): Boolean =
        analysisEnabled && subjectId != null && SystemClock.elapsedRealtime() - lastObservationAtMs <= 2_000L

    private fun trackingQuality(): Int {
        val age = (SystemClock.elapsedRealtime() - lastObservationAtMs).coerceAtLeast(0L)
        val freshness = (100 - (age * 100L / 2_000L).toInt()).coerceIn(0, 100)
        val channels = observation?.channels?.values?.count { it != null } ?: 0
        val channelEvidence = (channels * 14).coerceAtMost(70)
        val faceEvidence = when (observation?.faceCount) {
            1 -> 30
            2 -> 18
            else -> 8
        }
        return min(100, (freshness * 0.45f + channelEvidence * 0.35f + faceEvidence * 0.20f).toInt())
    }

    private fun invalidateAtInstrumentRate(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (force || now - lastInvalidateAtMs >= 125L) {
            lastInvalidateAtMs = now
            postInvalidateOnAnimation()
        }
    }

    private fun formatDuration(ms: Long): String {
        val seconds = (ms.coerceAtLeast(0L) / 1_000L)
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
