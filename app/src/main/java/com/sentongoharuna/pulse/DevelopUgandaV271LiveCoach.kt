package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * V271 LIVE COACH + OUTPUT MASTER.
 *
 * This layer is deliberately screen-only. It never draws into CameraX output.
 * Saved-output policy is handled by DevelopUgandaCameraActivity's reporter overlay
 * through the V271 output mode preference.
 */
object DevelopUgandaV271LiveCoach {
    const val PREFS = "develop_uganda_v271_live_coach_output_master"
    const val MODE_CLEAN = "CLEAN MASTER"
    const val MODE_REPORTER = "REPORTER MASTER"
    const val MODE_BRANDED = "BRANDED MASTER"

    data class CoachSnapshot(val code: String, val message: String, val fixLabel: String = "")

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun outputMode(context: Context): String = when (prefs(context).getString("output_mode", MODE_CLEAN)?.uppercase(Locale.US)) {
        MODE_REPORTER -> MODE_REPORTER
        MODE_BRANDED -> MODE_BRANDED
        else -> MODE_CLEAN
    }

    fun setOutputMode(context: Context, value: String): String {
        val next = when (value.trim().uppercase(Locale.US)) {
            MODE_REPORTER -> MODE_REPORTER
            MODE_BRANDED -> MODE_BRANDED
            else -> MODE_CLEAN
        }
        prefs(context).edit().putString("output_mode", next).apply()
        return "OUTPUT MASTER • $next"
    }

    fun bool(context: Context, key: String, defaultValue: Boolean): Boolean = prefs(context).getBoolean(key, defaultValue)

    fun toggle(context: Context, key: String, defaultValue: Boolean, label: String): String {
        val next = !bool(context, key, defaultValue)
        prefs(context).edit().putBoolean(key, next).apply()
        return "$label ${if (next) "ON" else "OFF"}"
    }

    fun interfaceLevel(context: Context): String = when (prefs(context).getString("interface_level", "BEGINNER")?.uppercase(Locale.US)) {
        "PRO" -> "PRO"
        else -> "BEGINNER"
    }

    fun setInterfaceLevel(context: Context, value: String): String {
        val next = if (value.trim().uppercase(Locale.US) == "PRO") "PRO" else "BEGINNER"
        prefs(context).edit().putString("interface_level", next).apply()
        return "INTERFACE • $next"
    }

    fun status(context: Context): String = buildString {
        append(outputMode(context))
        append(" • COACH ").append(if (bool(context, "live_coach", true)) "ON" else "OFF")
        append(" • TAP FIX ").append(if (bool(context, "tap_fix", true)) "ON" else "OFF")
        append(" • ").append(interfaceLevel(context))
    }

    fun verificationSummary(context: Context): String = buildString {
        append("OUTPUT MODE • WORKING")
        append("\nLIVE COACH • ").append(if (bool(context, "live_coach", true)) "WORKING" else "OFF")
        append("\nTAP-TO-FIX • ").append(if (bool(context, "tap_fix", true)) "WORKING WHERE DEVICE CONTROL EXISTS" else "OFF")
        append("\nFACE METERING • DEVICE LIMITED")
        append("\nMULTI-CAM / PROXY • NEEDS CONNECTION")
        append("\nHARDWARE HDR / FPS • DEVICE LIMITED")
    }

    fun attach(activity: DevelopUgandaCameraActivity, root: FrameLayout) {
        if (root.findViewWithTag<View>("v271_live_coach_chip") != null) return
        val handler = Handler(Looper.getMainLooper())

        val chip = TextView(activity).apply {
            tag = "v271_live_coach_chip"
            textSize = 9.2f
            setTextColor(DevelopUgandaFivemods8Theme.content)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(dp(activity, 12), dp(activity, 7), dp(activity, 12), dp(activity, 7))
            background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(217), DevelopUgandaFivemods8Theme.accent, 18)
            elevation = 0f
            alpha = 0f
            visibility = View.GONE
            setOnLongClickListener {
                DevelopUgandaV270Guidance.showFeature(
                    activity,
                    "V271 LIVE COACH",
                    "Temporary camera hints explain a problem without becoming part of the saved recording. Tap a hint only when a safe device-side correction is available."
                )
                true
            }
        }
        root.addView(chip, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = dp(activity, 136)
            leftMargin = dp(activity, 16)
            rightMargin = dp(activity, 16)
        })

        var lastCode = ""
        var lastShownAt = 0L
        val ticker = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed || chip.parent == null) return
                if (!bool(activity, "live_coach", true)) {
                    chip.animate().cancel()
                    chip.visibility = View.GONE
                    handler.postDelayed(this, 2200L)
                    return
                }

                val snapshot = activity.v271CoachSnapshot()
                val now = System.currentTimeMillis()
                if (snapshot.code.isBlank() || snapshot.message.isBlank()) {
                    if (chip.visibility == View.VISIBLE && now - lastShownAt > 1600L) {
                        chip.animate().alpha(0f).setDuration(180L).withEndAction { chip.visibility = View.GONE }.start()
                    }
                } else if (snapshot.code != lastCode || now - lastShownAt > 5200L) {
                    lastCode = snapshot.code
                    lastShownAt = now
                    chip.text = if (snapshot.fixLabel.isBlank()) snapshot.message else "${snapshot.message}  •  ${snapshot.fixLabel}"
                    chip.setOnClickListener {
                        if (snapshot.fixLabel.isBlank() || !bool(activity, "tap_fix", true)) {
                            DevelopUgandaV270Guidance.showFeature(activity, "LIVE COACH", snapshot.message)
                        } else {
                            chip.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            activity.v271ApplyCoachFix(snapshot.code)
                        }
                    }
                    chip.visibility = View.VISIBLE
                    chip.alpha = 0f
                    chip.translationY = -dp(activity, 4).toFloat()
                    chip.animate().alpha(1f).translationY(0f).setDuration(180L).start()
                }
                handler.postDelayed(this, 1300L)
            }
        }

        chip.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) { handler.removeCallbacksAndMessages(null) }
        })
        handler.postDelayed(ticker, 1100L)
    }

    fun haptic(activity: DevelopUgandaCameraActivity, kind: String) {
        if (!bool(activity, "haptic_language", true)) return
        val constant = when (kind.uppercase(Locale.US)) {
            "WARNING" -> HapticFeedbackConstants.LONG_PRESS
            "LOCK" -> HapticFeedbackConstants.KEYBOARD_TAP
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        activity.window.decorView.performHapticFeedback(constant)
    }

    fun speak(activity: DevelopUgandaCameraActivity, phrase: String) {
        if (!bool(activity, "voice_confirmations", false)) return
        var engine: TextToSpeech? = null
        engine = TextToSpeech(activity.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.getDefault()
                engine?.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "v271_${System.nanoTime()}")
            }
            Handler(Looper.getMainLooper()).postDelayed({ engine?.shutdown() }, 2200L)
        }
    }

    fun toast(context: Context, message: String) = Toast.makeText(context, message, Toast.LENGTH_SHORT).show()

    private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke(1, stroke)
        cornerRadius = radius.toFloat()
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
