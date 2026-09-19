package com.sentongoharuna.pulse

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * V237 Adaptive Multi-Format UI.
 *
 * The V236 capture-format/GNSS layer owns orientation and safe frames.
 * This class only reorganizes screen-only operator controls so each format
 * has a layout that fits without overlaps. Recording, V235 LUT processing,
 * Story Packages and CameraX output are not modified here.
 */
object DevelopUgandaAdaptiveFormatUi {

    enum class Role {
        REPORT,
        LIVE
    }

    private const val TAG_PREFIX = "develop_uganda_v237_adaptive_"
    private const val FIELD_CHIP_TAG = "develop_uganda_v236_field_intelligence"
    private const val GRADE_CHIP_TAG = "develop_uganda_v235_live_grade_panel"
    private const val TOOLS_CHIP_TAG = "develop_uganda_v233_tools_chip"

    fun attach(
        activity: AppCompatActivity,
        root: FrameLayout,
        role: Role
    ) {
        val markerTag = TAG_PREFIX + role.name.lowercase()
        if (root.findViewWithTag<View>(markerTag) != null) return

        val marker = View(activity).apply {
            tag = markerTag
            visibility = View.GONE
        }
        root.addView(marker, FrameLayout.LayoutParams(1, 1))

        Controller(activity, root, role, marker).start()
    }

    private class Controller(
        private val activity: AppCompatActivity,
        private val root: FrameLayout,
        private val role: Role,
        private val marker: View
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var lastKey = ""

        private val tick = object : Runnable {
            override fun run() {
                val formatId =
                    DevelopUgandaFieldIntelligencePanel.activeFormatId(activity)
                val orientation = activity.resources.configuration.orientation
                val key = "$formatId:$orientation:${activity.resources.displayMetrics.widthPixels}x${activity.resources.displayMetrics.heightPixels}"

                if (key != lastKey) {
                    lastKey = key
                    apply(formatId)
                }

                handler.postDelayed(this, 350L)
            }
        }

        fun start() {
            marker.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) {
                        handler.removeCallbacksAndMessages(null)
                    }
                }
            )
            root.post { apply(DevelopUgandaFieldIntelligencePanel.activeFormatId(activity)) }
            handler.post(tick)
        }

        private fun apply(formatId: String) {
            val landscape =
                formatId == "YOUTUBE_16_9" ||
                    formatId == "CINEMA_239" ||
                    formatId == "DUAL_SAFE" ||
                    activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

            when (role) {
                Role.REPORT -> applyReport(formatId, landscape)
                Role.LIVE -> applyLive(formatId, landscape)
            }

            placeSideChips(landscape)
        }

        private fun applyReport(
            formatId: String,
            landscape: Boolean
        ) {
            val deck = view<LinearLayout>("v237_camera_deck") ?: return
            val mode = view<View>("v237_camera_mode_row")
            val identity = view<View>("v237_camera_identity_row")
            val tools = view<View>("v237_camera_tools_row")
            val advanced = view<View>("v237_camera_advanced_row")
            val display = view<View>("v237_camera_display_row")
            val output = view<View>("v237_camera_output_row")
            val director = view<View>("v237_camera_director_row")
            val zoom = view<View>("v237_camera_zoom_row")
            val exposure = view<View>("v237_camera_exposure_row")
            val action = view<View>("v237_camera_action_row")
            val summary = view<View>("v237_camera_settings_summary")

            val scene = view<View>("v237_scene_button")
            val look = view<View>("v237_look_button")
            val quality = view<View>("v237_quality_button")
            val capture = view<View>("v237_capture_button")
            val color = view<View>("v237_color_button")

            // V256 CLEAN CAM: reporter identity is metadata/HUD only; the legacy
            // full-width bottom identity row must never reappear during rotation.
            identity?.visibility = View.GONE

            // Restore the five primary controls before format-specific pruning.
            listOf(scene, look, quality, capture, color).forEach { it?.visibility = View.VISIBLE }
            action?.visibility = View.VISIBLE
            mode?.visibility = View.VISIBLE

            if (!landscape) {
                deck.setPadding(dp(10), dp(4), dp(10), dp(10))
                when (formatId) {
                    "INSTAGRAM_4_5", "SQUARE_1_1" -> {
                        identity?.visibility = View.GONE
                        tools?.visibility = View.VISIBLE
                        advanced?.visibility = View.VISIBLE
                        display?.visibility = View.GONE
                        output?.visibility = View.GONE
                        director?.visibility = View.GONE
                        zoom?.visibility = View.VISIBLE
                        exposure?.visibility = View.VISIBLE
                    }
                    else -> {
                        identity?.visibility = View.GONE
                        tools?.visibility = View.VISIBLE
                        advanced?.visibility = View.VISIBLE
                        display?.visibility = View.VISIBLE
                        output?.visibility = View.VISIBLE
                        director?.visibility = View.VISIBLE
                        zoom?.visibility = View.VISIBLE
                        exposure?.visibility = View.VISIBLE
                    }
                }
                summary?.visibility = View.GONE
                toolsChip()?.visibility = View.VISIBLE
                return
            }

            // LANDSCAPE: only controls needed while the shot is being made.
            deck.setPadding(dp(10), dp(2), dp(10), dp(4))
            identity?.visibility = View.GONE
            tools?.visibility = View.GONE
            advanced?.visibility = View.GONE
            display?.visibility = View.GONE
            output?.visibility = View.GONE
            director?.visibility = View.GONE
            summary?.visibility = View.GONE
            toolsChip()?.visibility = View.GONE

            when (formatId) {
                "CINEMA_239" -> {
                    scene?.visibility = View.GONE
                    capture?.visibility = View.GONE
                    zoom?.visibility = View.GONE
                    exposure?.visibility = View.VISIBLE
                }
                "DUAL_SAFE" -> {
                    scene?.visibility = View.GONE
                    zoom?.visibility = View.VISIBLE
                    exposure?.visibility = View.VISIBLE
                }
                else -> { // YouTube 16:9
                    zoom?.visibility = View.VISIBLE
                    exposure?.visibility = View.VISIBLE
                }
            }

            compactChildren(mode, dp(38))
            compactChildren(action, dp(54))
        }

        private fun applyLive(
            formatId: String,
            landscape: Boolean
        ) {
            val deck = view<LinearLayout>("v237_live_deck") ?: return
            val outputStatus = view<View>("v237_live_output_status")
            val row1 = view<View>("v237_live_row1")
            val row2 = view<View>("v237_live_row2")
            val row3 = view<View>("v237_live_row3")
            val row4 = view<View>("v237_live_row4")
            val row5 = view<View>("v237_live_row5")
            val display = view<View>("v237_live_display_row")
            val output = view<View>("v237_live_output_row")
            val recordArea = view<ViewGroup>("v237_live_record_area")
            val subtitle = view<View>("v237_live_subtitle")
            val autoView = view<View>("v237_live_autoview")
            val timer = view<View>("v237_live_timer")

            row1?.visibility = View.VISIBLE
            row2?.visibility = View.VISIBLE
            recordArea?.visibility = View.VISIBLE

            if (!landscape) {
                deck.setPadding(dp(10), dp(9), dp(10), dp(10))
                outputStatus?.visibility = View.VISIBLE
                subtitle?.visibility = View.VISIBLE
                autoView?.visibility = View.VISIBLE
                timer?.visibility = View.VISIBLE

                if (formatId == "INSTAGRAM_4_5" || formatId == "SQUARE_1_1") {
                    row3?.visibility = View.GONE
                    row4?.visibility = View.VISIBLE
                    row5?.visibility = View.GONE
                    display?.visibility = View.GONE
                    output?.visibility = View.GONE
                } else {
                    row3?.visibility = View.VISIBLE
                    row4?.visibility = View.VISIBLE
                    row5?.visibility = View.VISIBLE
                    display?.visibility = View.VISIBLE
                    output?.visibility = View.VISIBLE
                }
                resizeRecordArea(recordArea, 118, 108)
                return
            }

            // LIVE landscape keeps source/profile, lens/light and record only.
            deck.setPadding(dp(8), dp(3), dp(8), dp(5))
            outputStatus?.visibility = View.GONE
            row3?.visibility = View.GONE
            row4?.visibility = View.GONE
            row5?.visibility = View.GONE
            display?.visibility = View.GONE
            output?.visibility = View.GONE
            subtitle?.visibility = View.GONE
            autoView?.visibility = View.GONE
            timer?.visibility = View.GONE
            resizeRecordArea(recordArea, 72, 64)
            compactChildren(row1, dp(38))
            compactChildren(row2, dp(38))
        }

        private fun placeSideChips(landscape: Boolean) {
            placeChip(
                FIELD_CHIP_TAG,
                start = false,
                landscape = landscape,
                portraitWidth = 112,
                portraitHeight = 54,
                landscapeWidth = 92,
                landscapeHeight = 44
            )
            placeChip(
                GRADE_CHIP_TAG,
                start = true,
                landscape = landscape,
                portraitWidth = 96,
                portraitHeight = 50,
                landscapeWidth = 86,
                landscapeHeight = 44
            )
        }

        private fun placeChip(
            tag: String,
            start: Boolean,
            landscape: Boolean,
            portraitWidth: Int,
            portraitHeight: Int,
            landscapeWidth: Int,
            landscapeHeight: Int
        ) {
            val chip = root.findViewWithTag<View>(tag) ?: return
            val lp = chip.layoutParams as? FrameLayout.LayoutParams ?: return
            lp.width = dp(if (landscape) landscapeWidth else portraitWidth)
            lp.height = dp(if (landscape) landscapeHeight else portraitHeight)
            lp.gravity = Gravity.TOP or (if (start) Gravity.START else Gravity.END)
            lp.leftMargin = if (start) dp(if (landscape) 5 else 8) else 0
            lp.rightMargin = if (!start) dp(if (landscape) 5 else 8) else 0
            lp.topMargin = dp(if (landscape) 8 else 10)
            lp.bottomMargin = 0
            chip.layoutParams = lp
            if (chip is Button) {
                chip.textSize = if (landscape) 7.1f else 8.1f
                chip.setPadding(dp(4), 0, dp(4), 0)
            }
        }

        private fun toolsChip(): View? =
            root.findViewWithTag(TOOLS_CHIP_TAG)

        private fun resizeRecordArea(
            area: ViewGroup?,
            areaHeightDp: Int,
            buttonSizeDp: Int
        ) {
            if (area == null) return
            val lp = area.layoutParams
            lp.height = dp(areaHeightDp)
            area.layoutParams = lp
            for (i in 0 until area.childCount) {
                val child = area.getChildAt(i)
                val childLp = child.layoutParams as? FrameLayout.LayoutParams ?: continue
                childLp.width = dp(buttonSizeDp)
                childLp.height = dp(buttonSizeDp)
                childLp.gravity = Gravity.CENTER
                child.layoutParams = childLp
            }
        }

        private fun compactChildren(container: View?, heightPx: Int) {
            val group = container as? ViewGroup ?: return
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                val lp = child.layoutParams
                if (lp.height > 1) {
                    lp.height = heightPx
                    child.layoutParams = lp
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T : View> view(tag: String): T? =
            root.findViewWithTag<View>(tag) as? T

        private fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density).toInt()
    }
}
