package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * V273 MOTION + SHOT CONTROL ENGINE.
 *
 * Built on top of V272 without replacing earlier camera systems.
 * Real functions in this layer:
 * - Uses the phone rotation-vector/motion values already sampled by the camera activity.
 * - Draws a screen-only horizon/movement guide that auto-hides when the shot is steady.
 * - Stores shot-move/rehearsal bookends.
 * - Stores and runs real CameraX zoom and exposure-compensation ramps through activity methods.
 * - Surfaces the existing real V255 subject tracking and Camera2 manual focus A/B engine.
 *
 * Deliberately not faked:
 * - The phone is never claimed to physically pan/tilt/orbit itself.
 * - Face/object tracking confidence is not invented when ML Kit has no measured confidence.
 * - Optical stabilization support is reported from the active camera path only.
 */
object DevelopUgandaV273MotionShotControl {
    const val PREFS = "develop_uganda_v273_motion_shot_control"
    private const val GUIDE_TAG = "v273_motion_guide"

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bool(context: Context, key: String, defaultValue: Boolean): Boolean =
        prefs(context).getBoolean(key, defaultValue)

    fun toggle(context: Context, key: String, defaultValue: Boolean, label: String): String {
        val next = !bool(context, key, defaultValue)
        prefs(context).edit().putBoolean(key, next).apply()
        return "$label ${if (next) "ON" else "OFF"}"
    }

    fun shotMode(context: Context): String =
        prefs(context).getString("shot_mode", "HANDHELD") ?: "HANDHELD"

    fun setShotMode(context: Context, value: String): String {
        val next = when (value.trim().uppercase(Locale.US)) {
            "STATIC", "PAN", "TILT", "PUSH-IN", "PULL-OUT", "FOLLOW", "ORBIT", "WALKING", "TRIPOD", "GIMBAL" -> value.trim().uppercase(Locale.US)
            else -> "HANDHELD"
        }
        prefs(context).edit().putString("shot_mode", next).apply()
        return "SHOT MOVE • $next"
    }

    fun zoomStart(context: Context): Float = prefs(context).getFloat("zoom_start", 1.0f).coerceIn(0.5f, 20f)
    fun zoomEnd(context: Context): Float = prefs(context).getFloat("zoom_end", 2.0f).coerceIn(0.5f, 20f)
    fun zoomDurationMs(context: Context): Int = prefs(context).getInt("zoom_duration_ms", 5000).coerceIn(800, 12000)
    fun setZoomStart(context: Context, value: Float): String { val v=value.coerceIn(0.5f,20f); prefs(context).edit().putFloat("zoom_start",v).apply(); return String.format(Locale.US,"ZOOM START • %.1f×",v) }
    fun setZoomEnd(context: Context, value: Float): String { val v=value.coerceIn(0.5f,20f); prefs(context).edit().putFloat("zoom_end",v).apply(); return String.format(Locale.US,"ZOOM END • %.1f×",v) }
    fun setZoomDuration(context: Context, ms: Int): String { val v=ms.coerceIn(800,12000); prefs(context).edit().putInt("zoom_duration_ms",v).apply(); return String.format(Locale.US,"ZOOM RAMP • %.1fs",v/1000f) }

    fun exposureStart(context: Context): Int = prefs(context).getInt("exp_start", 0).coerceIn(-6,6)
    fun exposureEnd(context: Context): Int = prefs(context).getInt("exp_end", 0).coerceIn(-6,6)
    fun exposureDurationMs(context: Context): Int = prefs(context).getInt("exp_duration_ms", 5000).coerceIn(800,12000)
    fun setExposureStart(context: Context, value: Int): String { val v=value.coerceIn(-6,6); prefs(context).edit().putInt("exp_start",v).apply(); return "EXPOSURE START • $v" }
    fun setExposureEnd(context: Context, value: Int): String { val v=value.coerceIn(-6,6); prefs(context).edit().putInt("exp_end",v).apply(); return "EXPOSURE END • $v" }
    fun setExposureDuration(context: Context, ms: Int): String { val v=ms.coerceIn(800,12000); prefs(context).edit().putInt("exp_duration_ms",v).apply(); return String.format(Locale.US,"EXPOSURE RAMP • %.1fs",v/1000f) }

    fun saveRehearsal(context: Context, which: String, state: JSONObject): String {
        val key = if (which.uppercase(Locale.US) == "END") "rehearsal_end" else "rehearsal_start"
        prefs(context).edit().putString(key, state.toString()).putLong("${key}_ms", System.currentTimeMillis()).apply()
        return "REHEARSAL ${if (key.endsWith("end")) "END" else "START"} SAVED"
    }

    fun rehearsal(context: Context, which: String): JSONObject? = try {
        val key = if (which.uppercase(Locale.US) == "END") "rehearsal_end" else "rehearsal_start"
        prefs(context).getString(key, null)?.let(::JSONObject)
    } catch (_: Exception) { null }

    fun rehearsalStatus(context: Context): String {
        val a = rehearsal(context,"START") != null
        val b = rehearsal(context,"END") != null
        return when {
            a && b -> "REHEARSAL • START + END READY"
            a -> "REHEARSAL • START SAVED • END NEEDED"
            else -> "REHEARSAL • NOT SAVED"
        }
    }

    fun attach(activity: DevelopUgandaCameraActivity, root: FrameLayout) {
        if (root.findViewWithTag<View>(GUIDE_TAG) != null) return
        val guide = MotionGuideView(activity).apply {
            tag = GUIDE_TAG
            isClickable = false
            isFocusable = false
            contentDescription = "V273 screen-only horizon and motion guide"
        }
        root.addView(guide, minOf(3, root.childCount), FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    fun refresh(activity: DevelopUgandaCameraActivity, root: FrameLayout) {
        root.findViewWithTag<MotionGuideView>(GUIDE_TAG)?.invalidate()
    }

    fun hubSummary(context: Context): String {
        val p = context as? DevelopUgandaGeneralHubActivity
        val mode = shotMode(context)
        return if (p == null) "MOTION • $mode" else p.v273HubMotionSummary(mode)
    }

    fun verificationSummary(activity: DevelopUgandaCameraActivity): String = buildString {
        append("WORKING • horizon/motion guide uses live rotation-vector + motion score\n")
        append("WORKING • subject tracking uses existing ML Kit face tracking + CameraX AF/AE/AWB\n")
        append("WORKING WHEN LENS SUPPORTS • manual Camera2 focus-distance A/B pull\n")
        append("WORKING • smooth CameraX zoom ramp within current lens zoom range\n")
        append("WORKING WHEN EXPOSURE COMP SUPPORTED • timed exposure-compensation ramp\n")
        append("WORKING • rehearsal start/end memory + restore of compatible camera state\n")
        append("SCREEN ONLY • motion/horizon/rehearsal guides are never burned into Clean Master\n")
        append("DEVICE LIMITED • stabilization/lens behavior depends on active phone camera\n")
        append("NOT CLAIMED • app cannot physically pan, tilt, orbit or gimbal the phone")
    }

    private class MotionGuideView(private val activity: DevelopUgandaCameraActivity) : View(activity) {
        private val handler = Handler(Looper.getMainLooper())
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 2.0f; color = DevelopUgandaFivemods8Theme.outline }
        private val center = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3.0f; color = DevelopUgandaFivemods8Theme.accent }
        private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DevelopUgandaFivemods8Theme.content; textSize = 24f; typeface = Typeface.DEFAULT_BOLD
        }
        private val tick = object: Runnable { override fun run() { if (isAttachedToWindow) { invalidate(); handler.postDelayed(this,120L) } } }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); handler.removeCallbacks(tick); handler.post(tick) }
        override fun onDetachedFromWindow() { handler.removeCallbacks(tick); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val horizon = bool(activity,"horizon_assist",true)
            val coach = bool(activity,"movement_coach",true)
            if (!horizon && !coach) return
            val roll = activity.v273RollDeg() ?: return
            val shake = activity.v273MotionScore()
            val autoHide = bool(activity,"auto_hide_motion_ui",true)
            if (autoHide && abs(roll) < 0.55f && shake < 7f) return
            val cx = width/2f; val cy = height*0.47f
            if (horizon) {
                val half = width*0.23f
                val rad = Math.toRadians((-roll).toDouble())
                val dx=(cos(rad)*half).toFloat(); val dy=(sin(rad)*half).toFloat()
                canvas.drawLine(cx-dx,cy-dy,cx+dx,cy+dy,line)
                canvas.drawLine(cx-16f,cy,cx+16f,cy,center)
            }
            if (coach) {
                val state = when {
                    shake >= 58f -> "MOVE FAST"
                    shake >= 30f -> "MOVE MED"
                    else -> "STEADY"
                }
                val label = String.format(Locale.US,"%s • LEVEL %+.1f° • %s %.0f", shotMode(activity), roll, state, shake)
                text.textAlign = Paint.Align.CENTER
                canvas.drawText(label,cx,height*0.16f,text)
            }
        }
    }
}
