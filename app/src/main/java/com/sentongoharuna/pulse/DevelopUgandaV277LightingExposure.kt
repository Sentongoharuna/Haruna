package com.sentongoharuna.pulse

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import java.util.ArrayDeque
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * V277 PRO CAM • LIGHTING + EXPOSURE INTELLIGENCE.
 *
 * Cumulative screen-only analysis layer. It samples PreviewView bitmaps already
 * used by the Director/monitor pipeline, so it does not alter CameraX recording
 * or burn any exposure graphics into the saved master. Values are guidance,
 * not a replacement for a calibrated light meter.
 */
object DevelopUgandaV277LightingExposure {
    const val PREFS = "develop_uganda_v277_lighting_exposure"

    data class Snapshot(
        val timestampMs: Long = 0L,
        val meanLuma: Int = -1,
        val highlightPct: Int = 0,
        val shadowPct: Int = 0,
        val centerLuma: Int = -1,
        val faceLuma: Int = -1,
        val spotLuma: Int = -1,
        val warmCool: String = "UNKNOWN",
        val flickerRisk: Boolean = false,
        val backlightRisk: Boolean = false,
        val code: String = "",
        val message: String = "",
        val fixLabel: String = ""
    ) {
        fun isFresh(now: Long = System.currentTimeMillis()): Boolean = timestampMs > 0L && now - timestampMs < 30_000L
        fun shortStrip(): String {
            if (meanLuma < 0) return "LIGHT • OPEN CAMERA TO ANALYSE"
            val face = if (faceLuma >= 0) "FACE $faceLuma" else "MID $meanLuma"
            val hi = if (highlightPct >= 8) "HIGHLIGHTS ${highlightPct}%" else "HIGHLIGHTS SAFE"
            val flicker = if (flickerRisk) "FLICKER RISK" else "FLICKER OK"
            return "$face • $hi • $warmCool • $flicker"
        }
    }

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bool(context: Context, key: String, defaultValue: Boolean): Boolean = prefs(context).getBoolean(key, defaultValue)

    fun toggle(context: Context, key: String, defaultValue: Boolean): Boolean {
        val next = !bool(context, key, defaultValue)
        prefs(context).edit().putBoolean(key, next).apply()
        return next
    }

    fun setRegion(context: Context, value: String) {
        val next = if (value.trim() == "60") "60" else "50"
        prefs(context).edit().putString("anti_flicker_region", next).apply()
    }

    fun region(context: Context): String = prefs(context).getString("anti_flicker_region", "50") ?: "50"

    fun setSpot(context: Context, xNorm: Float, yNorm: Float) {
        prefs(context).edit()
            .putFloat("spot_x", xNorm.coerceIn(0f, 1f))
            .putFloat("spot_y", yNorm.coerceIn(0f, 1f))
            .putBoolean("spot_has_point", true)
            .apply()
    }

    fun clearSpot(context: Context) {
        prefs(context).edit().putBoolean("spot_has_point", false).apply()
    }

    private val lumaHistory = ArrayDeque<Pair<Long, Int>>()

    @Synchronized
    fun analysePreview(
        context: Context,
        bitmap: Bitmap,
        faceX: Float?,
        faceY: Float?,
        faceArea: Float?
    ): Snapshot {
        if (!bool(context, "lighting_coach", true)) return lastSnapshot(context)
        if (bitmap.width <= 1 || bitmap.height <= 1) return lastSnapshot(context)

        val sampleW = 40
        val sampleH = 24
        val small = try {
            Bitmap.createScaledBitmap(bitmap, sampleW, sampleH, true)
        } catch (_: Exception) {
            return lastSnapshot(context)
        }

        var sum = 0L
        var sumR = 0L
        var sumB = 0L
        var high = 0
        var shadow = 0
        var count = 0
        var centerSum = 0L
        var centerCount = 0

        val spotOn = bool(context, "spot_meter", true) && prefs(context).getBoolean("spot_has_point", false)
        val spotX = prefs(context).getFloat("spot_x", 0.5f)
        val spotY = prefs(context).getFloat("spot_y", 0.5f)
        var spotSum = 0L
        var spotCount = 0

        val faceOn = bool(context, "face_meter", true) && faceX != null && faceY != null && faceArea != null && faceArea > 0.003f
        val faceRadius = if (faceOn) (sqrt(faceArea!!.toDouble()).toFloat() * 0.36f).coerceIn(0.06f, 0.22f) else 0f
        var faceSum = 0L
        var faceCount = 0

        for (y in 0 until sampleH) {
            val yn = (y + 0.5f) / sampleH.toFloat()
            for (x in 0 until sampleW) {
                val xn = (x + 0.5f) / sampleW.toFloat()
                val c = small.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                val y8 = ((54 * r + 183 * g + 19 * b) shr 8).coerceIn(0, 255)
                sum += y8
                sumR += r
                sumB += b
                count++
                if (y8 >= 242) high++
                if (y8 <= 22) shadow++

                if (xn in 0.33f..0.67f && yn in 0.30f..0.70f) {
                    centerSum += y8
                    centerCount++
                }

                if (spotOn && abs(xn - spotX) <= 0.055f && abs(yn - spotY) <= 0.055f) {
                    spotSum += y8
                    spotCount++
                }

                if (faceOn && abs(xn - faceX!!) <= faceRadius && abs(yn - faceY!!) <= faceRadius * 1.22f) {
                    faceSum += y8
                    faceCount++
                }
            }
        }

        if (small !== bitmap) {
            try { small.recycle() } catch (_: Exception) {}
        }

        if (count <= 0) return lastSnapshot(context)
        val mean = (sum / count).toInt().coerceIn(0, 255)
        val center = if (centerCount > 0) (centerSum / centerCount).toInt() else mean
        val face = if (faceCount > 0) (faceSum / faceCount).toInt() else -1
        val spot = if (spotCount > 0) (spotSum / spotCount).toInt() else -1
        val hiPct = (high * 100f / count).roundToInt().coerceIn(0, 100)
        val shPct = (shadow * 100f / count).roundToInt().coerceIn(0, 100)
        val avgR = sumR.toFloat() / count
        val avgB = sumB.toFloat() / count
        val rb = avgR - avgB
        val warmCool = when {
            rb > 18f -> "WARM LIGHT"
            rb < -18f -> "COOL LIGHT"
            else -> "NEUTRAL LIGHT"
        }

        val now = System.currentTimeMillis()
        synchronized(lumaHistory) {
            lumaHistory.addLast(now to mean)
            while (lumaHistory.size > 10) lumaHistory.removeFirst()
            while (lumaHistory.isNotEmpty() && now - lumaHistory.first().first > 7000L) lumaHistory.removeFirst()
        }
        val flickerRisk = if (bool(context, "flicker_assist", true)) detectFlickerRisk() else false
        val backlightRisk = face >= 0 && face + 32 < center && hiPct >= 4

        val highlightProtection = bool(context, "highlight_protection", true)
        val shadowProtection = bool(context, "shadow_protection", true)
        val faceMeter = bool(context, "face_meter", true)

        val guidance = when {
            highlightProtection && hiPct >= 14 -> Triple("HIGHLIGHT_CLIP", "HIGHLIGHTS CLIPPING • ${hiPct}% OF FRAME", "PROTECT HIGHLIGHTS")
            faceMeter && face >= 0 && face <= 72 -> Triple("FACE_DARK", "FACE TOO DARK • METER $face/255", "METER FACE")
            backlightRisk -> Triple("BACKLIGHT", "BACKLIGHT • FACE IS DARKER THAN BACKGROUND", "METER FACE")
            shadowProtection && shPct >= 28 && mean < 86 -> Triple("SHADOW_LOW", "SHADOW DETAIL LOW • ${shPct}% NEAR BLACK", "EXPOSURE HELP")
            flickerRisk -> Triple("FLICKER_RISK", "ARTIFICIAL-LIGHT FLICKER RISK • ${region(context)}Hz MODE", "FLICKER HELP")
            spot >= 0 && spot <= 45 -> Triple("SPOT_DARK", "SPOT METER DARK • $spot/255", "EXPOSURE HELP")
            spot >= 0 && spot >= 235 -> Triple("SPOT_BRIGHT", "SPOT METER VERY BRIGHT • $spot/255", "PROTECT HIGHLIGHTS")
            else -> Triple("", "", "")
        }

        val snapshot = Snapshot(
            timestampMs = now,
            meanLuma = mean,
            highlightPct = hiPct,
            shadowPct = shPct,
            centerLuma = center,
            faceLuma = face,
            spotLuma = spot,
            warmCool = warmCool,
            flickerRisk = flickerRisk,
            backlightRisk = backlightRisk,
            code = guidance.first,
            message = guidance.second,
            fixLabel = guidance.third
        )
        saveSnapshot(context, snapshot)
        return snapshot
    }

    @Synchronized
    private fun detectFlickerRisk(): Boolean {
        if (lumaHistory.size < 6) return false
        val values = lumaHistory.map { it.second }
        val range = (values.maxOrNull() ?: 0) - (values.minOrNull() ?: 0)
        if (range < 20) return false
        var directionChanges = 0
        var previousDelta = 0
        for (i in 1 until values.size) {
            val delta = values[i] - values[i - 1]
            if (abs(delta) < 4) continue
            if (previousDelta != 0 && delta * previousDelta < 0) directionChanges++
            previousDelta = delta
        }
        return directionChanges >= 3
    }

    private fun saveSnapshot(context: Context, s: Snapshot) {
        prefs(context).edit()
            .putLong("snap_time", s.timestampMs)
            .putInt("snap_mean", s.meanLuma)
            .putInt("snap_hi", s.highlightPct)
            .putInt("snap_shadow", s.shadowPct)
            .putInt("snap_center", s.centerLuma)
            .putInt("snap_face", s.faceLuma)
            .putInt("snap_spot", s.spotLuma)
            .putString("snap_warm", s.warmCool)
            .putBoolean("snap_flicker", s.flickerRisk)
            .putBoolean("snap_backlight", s.backlightRisk)
            .putString("snap_code", s.code)
            .putString("snap_message", s.message)
            .putString("snap_fix", s.fixLabel)
            .apply()
    }

    fun lastSnapshot(context: Context): Snapshot {
        val p = prefs(context)
        return Snapshot(
            timestampMs = p.getLong("snap_time", 0L),
            meanLuma = p.getInt("snap_mean", -1),
            highlightPct = p.getInt("snap_hi", 0),
            shadowPct = p.getInt("snap_shadow", 0),
            centerLuma = p.getInt("snap_center", -1),
            faceLuma = p.getInt("snap_face", -1),
            spotLuma = p.getInt("snap_spot", -1),
            warmCool = p.getString("snap_warm", "UNKNOWN") ?: "UNKNOWN",
            flickerRisk = p.getBoolean("snap_flicker", false),
            backlightRisk = p.getBoolean("snap_backlight", false),
            code = p.getString("snap_code", "") ?: "",
            message = p.getString("snap_message", "") ?: "",
            fixLabel = p.getString("snap_fix", "") ?: ""
        )
    }

    fun coachSnapshot(context: Context): DevelopUgandaV271LiveCoach.CoachSnapshot {
        if (!bool(context, "lighting_coach", true)) return DevelopUgandaV271LiveCoach.CoachSnapshot("", "")
        val s = lastSnapshot(context)
        if (!s.isFresh() || s.code.isBlank()) return DevelopUgandaV271LiveCoach.CoachSnapshot("", "")
        return DevelopUgandaV271LiveCoach.CoachSnapshot(s.code, s.message, s.fixLabel)
    }

    fun lightStatus(context: Context): String {
        val s = lastSnapshot(context)
        if (!s.isFresh()) return "LIGHT • OPEN CAMERA TO ANALYSE • ${region(context)}Hz GUIDE"
        val condition = when {
            s.highlightPct >= 14 -> "LIGHT CHECK"
            s.faceLuma in 0..72 -> "FACE DARK"
            s.shadowPct >= 28 && s.meanLuma < 86 -> "SHADOW LOW"
            s.flickerRisk -> "FLICKER RISK"
            else -> "LIGHT GOOD"
        }
        return "$condition • ${s.shortStrip()}"
    }

    fun needsAttention(context: Context): Boolean {
        val s = lastSnapshot(context)
        return s.isFresh() && s.code.isNotBlank()
    }

    fun hasFreshReading(context: Context): Boolean = lastSnapshot(context).isFresh()

    fun verificationSummary(context: Context): String = buildString {
        append("WORKING • preview luminance / highlight / shadow sampling\n")
        append("WORKING • face-region exposure guidance when ML Kit face position is available\n")
        append("WORKING • tap spot meter sampling on the live preview\n")
        append("WORKING • warm/cool bias guidance from preview RGB balance\n")
        append("GUIDANCE • flicker risk uses short preview-luminance variation; it is not a calibrated mains-frequency meter\n")
        append("PRESERVED • V271–V276 output, sound, continuity, motion, vault, dual settings and recovery\n")
        append("SCREEN ONLY • no V277 meter graphics are burned into Clean Master")
    }

    fun shutterGuidance(context: Context, fps: Int): String {
        val region = region(context)
        return if (region == "50") {
            if (fps == 25 || fps == 50) "50Hz GUIDE • 25/50 FPS pairs naturally with 1/50 or 1/100 style shutter timing where the device exposes it."
            else "50Hz GUIDE • if artificial lights flicker, try a 25/50 FPS family or a compatible shutter timing supported by the phone."
        } else {
            if (fps == 30 || fps == 60) "60Hz GUIDE • 30/60 FPS pairs naturally with 1/60 or 1/120 style shutter timing where the device exposes it."
            else "60Hz GUIDE • if artificial lights flicker, try a 30/60 FPS family or a compatible shutter timing supported by the phone."
        }
    }
}

class DevelopUgandaV277LightingExposureActivity : DevelopUgandaV275SettingsBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, page) = basePage(
            "LIGHTING + EXPOSURE • V277",
            "Screen-only exposure intelligence. Preview sampling guides the operator; saved masters remain untouched unless an existing capture control is deliberately changed."
        )

        info(page, "LIVE LIGHT STATUS", DevelopUgandaV277LightingExposure.lightStatus(this), green)

        section(page, "INTELLIGENT EXPOSURE COACH")
        toggle(page, "Lighting / Exposure Coach", "Temporary FACE DARK, HIGHLIGHT, SHADOW, BACKLIGHT and flicker-risk hints.", { DevelopUgandaV277LightingExposure.bool(this, "lighting_coach", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "lighting_coach", true)
        }
        toggle(page, "Face Exposure Meter", "Uses the existing ML Kit primary-face position to sample the face region separately from the whole preview.", { DevelopUgandaV277LightingExposure.bool(this, "face_meter", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "face_meter", true)
        }
        toggle(page, "Highlight Protection", "Warn before a large part of the preview reaches near-white clipping.", { DevelopUgandaV277LightingExposure.bool(this, "highlight_protection", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "highlight_protection", true)
        }
        toggle(page, "Shadow Protection", "Warn when a large part of the preview is near black and overall exposure is low.", { DevelopUgandaV277LightingExposure.bool(this, "shadow_protection", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "shadow_protection", true)
        }
        toggle(page, "Spot Meter", "When ON, an ordinary tap-to-focus point also becomes a screen-only brightness spot reading.", { DevelopUgandaV277LightingExposure.bool(this, "spot_meter", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "spot_meter", true)
        }
        action(page, "CLEAR SPOT METER", "Return exposure guidance to whole-frame / face analysis only.", cyan) {
            DevelopUgandaV277LightingExposure.clearSpot(this)
            Toast.makeText(this, "V277 SPOT METER CLEARED", Toast.LENGTH_SHORT).show()
            recreate()
        }

        section(page, "FLICKER + REGIONAL LIGHTING GUIDE")
        toggle(page, "Flicker Risk Assist", "Looks for repeated preview brightness variation and warns cautiously; it does not claim laboratory flicker measurement.", { DevelopUgandaV277LightingExposure.bool(this, "flicker_assist", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "flicker_assist", true)
        }
        choices(page, "Lighting Frequency Guide", "Choose the local mains-lighting family used for shutter guidance.", listOf("50", "60"), { DevelopUgandaV277LightingExposure.region(this) }) {
            DevelopUgandaV277LightingExposure.setRegion(this, it)
        }
        info(page, "SHUTTER / FLICKER GUIDE", DevelopUgandaV277LightingExposure.shutterGuidance(this, 30), cyan)

        section(page, "EXISTING CAMERA ASSISTS • PRESERVED")
        toggle(page, "Face Exposure Priority", "Writes the existing V251 face AE/AWB preference; takes effect in the Field Camera.", {
            duSharedPreferences("develop_uganda_v251_pro_assist", MODE_PRIVATE).getBoolean("face_exposure", true)
        }) {
            val p = duSharedPreferences("develop_uganda_v251_pro_assist", MODE_PRIVATE)
            p.edit().putBoolean("face_exposure", !p.getBoolean("face_exposure", true)).apply()
        }
        toggle(page, "Smart Exposure Assist", "Keeps the existing primary-face smart metering path enabled.", {
            duSharedPreferences("develop_uganda_v259_smart_shoot_media", MODE_PRIVATE).getBoolean("smart_exposure", true)
        }) {
            val p = duSharedPreferences("develop_uganda_v259_smart_shoot_media", MODE_PRIVATE)
            p.edit().putBoolean("smart_exposure", !p.getBoolean("smart_exposure", true)).apply()
        }
        toggle(page, "False Color • Screen Only", "Uses the existing V255 false-color monitor. It never enters the CameraX saved output overlay.", {
            duSharedPreferences("develop_uganda_v255_pro_monitor_tracking", MODE_PRIVATE).getBoolean("false_color", false)
        }) {
            val p = duSharedPreferences("develop_uganda_v255_pro_monitor_tracking", MODE_PRIVATE)
            p.edit().putBoolean("false_color", !p.getBoolean("false_color", false)).apply()
        }

        section(page, "DIRECT ROUTES")
        action(page, "OPEN FIELD CAMERA", "Open the cumulative V277 camera and collect a fresh live lighting reading.", gold) {
            startActivity(Intent(this, DevelopUgandaAllProCameraActivity::class.java))
        }
        action(page, "CAMERA SETTINGS", "Return to the categorized camera settings page.", cyan) {
            startActivity(Intent(this, DevelopUgandaV275CameraSettingsActivity::class.java))
        }

        section(page, "VERIFICATION")
        info(page, "V277 STATUS", DevelopUgandaV277LightingExposure.verificationSummary(this), green)
        setContentView(scroll)
    }
}
