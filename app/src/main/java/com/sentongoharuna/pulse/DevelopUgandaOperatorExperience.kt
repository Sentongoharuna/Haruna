package com.sentongoharuna.pulse

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object DevelopUgandaOperatorExperience {

    enum class Role { REPORT, LIVE }

    private const val TAG_PREFIX = "develop_uganda_v238_operator_"
    private const val FIELD_CHIP_TAG = "develop_uganda_v236_field_intelligence"
    private const val GRADE_CHIP_TAG = "develop_uganda_v235_live_grade_panel"

    fun attach(activity: AppCompatActivity, root: FrameLayout, role: Role) {
        val tag = TAG_PREFIX + role.name.lowercase(Locale.US)
        if (root.findViewWithTag<View>(tag) != null) return
        val marker = View(activity).apply { this.tag = tag; visibility = View.GONE }
        root.addView(marker, FrameLayout.LayoutParams(1, 1))
        Controller(activity, root, role, marker).attach()
    }

    private class Controller(
        private val activity: AppCompatActivity,
        private val root: FrameLayout,
        private val role: Role,
        private val marker: View
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var hudVisible = true
        private var transientUntil = 0L
        private lateinit var readyChip: Button
        private val baselineVisibility = linkedMapOf<String, Int>()
        private var fieldBaseline = View.VISIBLE
        private var gradeBaseline = View.VISIBLE
        private val autoHide = Runnable { setHudVisible(false, haptic = false) }
        private val tick = object : Runnable {
            override fun run() { updateReadyChip(); handler.postDelayed(this, 450L) }
        }

        fun attach() {
            readyChip = Button(activity).apply {
                text = "SHOT READY • HUD"
                textSize = 7.6f
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
                setPadding(dp(7), 0, dp(7), 0)
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(15).toFloat()
                    setColor(DevelopUgandaFivemods8Theme.surfaceScrim(230))
                    setStroke(dp(1), DevelopUgandaFivemods8Theme.contentDim)
                }
                setOnClickListener { setHudVisible(!hudVisible, haptic = true) }
                setOnLongClickListener {
                    setHudVisible(false, haptic = true)
                    transient("CLEAN VIEW • EDGE EXP / ZOOM", 1500L)
                    true
                }
            }
            root.addView(
                readyChip,
                FrameLayout.LayoutParams(dp(154), dp(34)).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    topMargin = 0
                    rightMargin = dp(8)
                }
            )
            if (role == Role.REPORT) {
                root.addView(edgeView(true), FrameLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.START })
                root.addView(edgeView(false), FrameLayout.LayoutParams(dp(18), ViewGroup.LayoutParams.MATCH_PARENT).apply { gravity = Gravity.END })
            }
            marker.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) { handler.removeCallbacksAndMessages(null) }
            })
            scheduleAutoHide()
            handler.post(tick)
        }

        private fun edgeView(exposure: Boolean): View {
            var downY = 0f
            var startProgress = 0
            var activeSeek: SeekBar? = null
            return View(activity).apply {
                setBackgroundColor(DevelopUgandaFivemods8Theme.transparent)
                setOnTouchListener { _, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            activeSeek = root.findViewWithTag(if (exposure) "v238_exposure_seek" else "v238_zoom_seek") as? SeekBar
                            val seek = activeSeek ?: return@setOnTouchListener false
                            downY = event.rawY
                            startProgress = seek.progress
                            true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val seek = activeSeek ?: return@setOnTouchListener false
                            val usable = root.height.coerceAtLeast(1).toFloat()
                            val delta = ((downY - event.rawY) / usable * seek.max * 1.9f).roundToInt()
                            seek.progress = (startProgress + delta).coerceIn(0, seek.max)
                            if (exposure) {
                                val ev = seek.progress - 6
                                transient("EXP ${if (ev >= 0) "+" else ""}$ev", 700L)
                            } else transient("ZOOM ${seek.progress}%", 700L)
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            performTick(); scheduleAutoHide(); activeSeek = null; true
                        }
                        else -> false
                    }
                }
            }
        }

        private fun scheduleAutoHide() {
            handler.removeCallbacks(autoHide)
            handler.postDelayed(autoHide, 3000L)
        }

        private fun setHudVisible(visible: Boolean, haptic: Boolean) {
            hudVisible = visible
            val tags = when (role) {
                Role.REPORT -> listOf(
                    "v237_camera_mode_row", "v237_camera_identity_row", "v237_camera_tools_row",
                    "v237_camera_advanced_row", "v237_camera_display_row", "v237_camera_output_row",
                    "v237_camera_director_row", "v237_camera_zoom_row", "v237_camera_exposure_row",
                    "v237_camera_settings_summary"
                )
                Role.LIVE -> listOf(
                    "v237_live_output_status", "v237_live_row1", "v237_live_row2", "v237_live_row3",
                    "v237_live_row4", "v237_live_row5", "v237_live_display_row", "v237_live_output_row",
                    "v237_live_subtitle", "v237_live_autoview", "v237_live_timer"
                )
            }
            if (!visible) {
                baselineVisibility.clear()
                tags.forEach { tag ->
                    root.findViewWithTag<View>(tag)?.let { view ->
                        baselineVisibility[tag] = view.visibility
                        view.visibility = View.GONE
                    }
                }
                root.findViewWithTag<View>(FIELD_CHIP_TAG)?.let {
                    fieldBaseline = View.VISIBLE
                    it.visibility = View.VISIBLE
                }
                root.findViewWithTag<View>(GRADE_CHIP_TAG)?.let {
                    gradeBaseline = View.VISIBLE
                    it.visibility = View.VISIBLE
                }
            } else {
                tags.forEach { tag ->
                    root.findViewWithTag<View>(tag)?.visibility = baselineVisibility[tag] ?: View.GONE
                }
                root.findViewWithTag<View>(FIELD_CHIP_TAG)?.visibility = fieldBaseline
                root.findViewWithTag<View>(GRADE_CHIP_TAG)?.visibility = gradeBaseline
            }
            if (haptic) performTick()
            if (visible) scheduleAutoHide() else handler.removeCallbacks(autoHide)
            transient(if (visible) "HUD ON • AUTO-HIDE 3s" else "CLEAN VIEW • TAP FOR HUD", 1000L)
        }

        private fun updateReadyChip() {
            if (System.currentTimeMillis() < transientUntil) return
            val field = DevelopUgandaFieldIntelligencePanel.snapshotJson(activity)
            val roll = field.optDouble("horizon_roll_deg", 0.0)
            val satUsed = field.optInt("satellites_used", 0)
            val accuracy = if (field.isNull("accuracy_m")) null else field.optDouble("accuracy_m")
            val allText = collectText(root).uppercase(Locale.US)
            val tooDark = allText.contains("TOO DARK")
            val thermal = allText.contains("THERMAL") && allText.contains("HOT")
            val micBad = allText.contains("MIC") && (allText.contains("TOO LOW") || allText.contains("TOO LOUD"))
            val label = when {
                thermal -> "CHECK HEAT"
                tooDark -> "CHECK LIGHT"
                micBad -> "CHECK AUDIO"
                abs(roll) > 2.2 -> "LEVEL ${String.format(Locale.US, "%.1f", roll)}°"
                satUsed == 0 -> "READY • GPS --"
                accuracy != null && accuracy > 15.0 -> "READY • GPS ±${accuracy.roundToInt()}m"
                else -> "READY ✓ • SAT $satUsed"
            }
            readyChip.text = if (hudVisible) "$label • HUD" else "$label • CLEAN"
        }

        private fun collectText(view: View): String {
            val out = StringBuilder()
            fun walk(v: View) {
                if (v is TextView) out.append(' ').append(v.text ?: "")
                if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
            }
            walk(view)
            return out.toString()
        }

        private fun transient(text: String, durationMs: Long) { transientUntil = System.currentTimeMillis() + durationMs; readyChip.text = text }
        private fun performTick() { readyChip.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
        private fun dp(value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()
    }
}
