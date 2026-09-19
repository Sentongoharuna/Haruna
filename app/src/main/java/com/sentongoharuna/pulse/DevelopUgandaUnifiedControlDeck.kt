package com.sentongoharuna.pulse

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

/**
 * develop.uganda V233 • Unified Live Control Deck.
 *
 * V233 does not replace CameraX capture, V232 Live Grade, Director/QC,
 * telemetry, integrity, social master, or the 17^3 color-master pipeline.
 * It unifies the operator controls on the CAMERA SCREEN:
 *  - one glass control language
 *  - LUT/palette-linked accent colors
 *  - a compact REPORT deck with advanced controls moved into LIVE TOOLS
 *  - all original button listeners retained by moving the actual buttons
 *  - RECORD remains visually dominant and untouched
 */
object DevelopUgandaUnifiedControlDeck {

    enum class Mode {
        REPORT,
        LIVE
    }

    private const val TAG = "develop_uganda_v233_unified_live_control_deck"
    private const val TOOLS_TAG = "develop_uganda_v233_tools_chip"

    fun attach(
        activity: AppCompatActivity,
        root: FrameLayout,
        scopeProvider: () -> String,
        hintProvider: () -> String,
        mode: Mode
    ) {
        if (root.findViewWithTag<View>(TAG) != null) {
            return
        }

        Controller(
            activity = activity,
            root = root,
            scopeProvider = scopeProvider,
            hintProvider = hintProvider,
            mode = mode
        ).attach()
    }

    private class Controller(
        private val activity: AppCompatActivity,
        private val root: FrameLayout,
        private val scopeProvider: () -> String,
        private val hintProvider: () -> String,
        private val mode: Mode
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var lastThemeSignature = ""
        private var compacted = false
        private var drawerOpen = false

        private lateinit var marker: View
        private var toolsChip: Button? = null
        private var toolsDrawer: LinearLayout? = null
        private var toolsHost: LinearLayout? = null
        private var toolsHeader: TextView? = null

        private val refreshRunnable =
            object : Runnable {
                override fun run() {
                    if (activity.isFinishing || activity.isDestroyed) {
                        return
                    }

                    if (mode == Mode.REPORT && !compacted) {
                        compactReportControls()
                    }

                    refreshThemeIfNeeded()
                    handler.postDelayed(this, 650L)
                }
            }

        fun attach() {
            marker = View(activity).apply {
                tag = TAG
                visibility = View.GONE
            }
            root.addView(marker, FrameLayout.LayoutParams(1, 1))

            if (mode == Mode.REPORT) {
                buildToolsDrawer()
            }

            root.post {
                if (mode == Mode.REPORT) {
                    compactReportControls()
                }
                styleAllButtons(force = true)
            }

            handler.postDelayed(refreshRunnable, 650L)
        }

        private fun buildToolsDrawer() {
            val chip = Button(activity).apply {
                tag = TOOLS_TAG
                text = "TOOLS\nLIVE ▸"
                textSize = 8.0f
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
                setPadding(dp(5), 0, dp(5), 0)
                setOnClickListener {
                    drawerOpen = !drawerOpen
                    toolsDrawer?.visibility =
                        if (drawerOpen) View.VISIBLE else View.GONE
                    text =
                        if (drawerOpen) {
                            "TOOLS\nCLOSE ×"
                        } else {
                            "TOOLS\nLIVE ▸"
                        }
                }
            }
            toolsChip = chip

            root.addView(
                chip,
                FrameLayout.LayoutParams(
                    dp(88),
                    dp(48)
                ).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    leftMargin = dp(8)
                }
            )

            val drawer = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                visibility = View.GONE
                elevation = 0f
            }
            toolsDrawer = drawer

            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val title = TextView(activity).apply {
                text = "● V235 • UNIFIED LIVE TOOLS\nADVANCED CAMERA CONTROLS • CAMERA SCREEN"
                textSize = 8.6f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
            }
            toolsHeader = title
            header.addView(
                title,
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )

            val close = Button(activity).apply {
                text = "×"
                textSize = 18f
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
                setOnClickListener {
                    drawerOpen = false
                    drawer.visibility = View.GONE
                    toolsChip?.text = "TOOLS\nLIVE ▸"
                }
            }
            header.addView(close, LinearLayout.LayoutParams(dp(42), dp(38)))
            drawer.addView(header)

            drawer.addView(
                TextView(activity).apply {
                    text = "Tap a tool and see its change immediately on the camera. COLOR remains in the V232/V233 Live Grade panel; RECORD / LENS / LIGHT stay permanently visible."
                    textSize = 7.0f
                    setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                    setPadding(0, dp(3), 0, dp(5))
                }
            )

            val scroll = ScrollView(activity).apply {
                isFillViewport = true
            }
            val host = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            toolsHost = host
            scroll.addView(host)
            drawer.addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )

            root.addView(
                drawer,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(350)
                ).apply {
                    gravity = Gravity.BOTTOM
                    leftMargin = dp(8)
                    rightMargin = dp(8)
                    bottomMargin = dp(78)
                }
            )
        }

        /**
         * REPORT V232 showed five dense rows of advanced controls at all times.
         * V233 keeps the quick shooting row + LENS/RECORD/LIGHT on screen and moves
         * the actual advanced buttons into the live drawer. Their existing touch
         * listeners are retained because the same Button objects are re-parented.
         */
        private fun compactReportControls() {
            val host = toolsHost ?: return
            if (compacted) return

            val candidates =
                collectButtons(root)
                    .filter {
                        !isOwnControl(it) &&
                            !isGradePanelControl(it) &&
                            isAdvancedReportControl(it)
                    }

            if (candidates.isEmpty()) {
                return
            }

            val oldParents = linkedSetOf<ViewGroup>()
            candidates.forEach { button ->
                (button.parent as? ViewGroup)?.let { parent ->
                    oldParents.add(parent)
                    parent.removeView(button)
                }
            }

            // All identified parents are the legacy advanced-control rows.
            // Hide the emptied rows so the live camera image gets that space back.
            oldParents.forEach {
                it.visibility = View.GONE
            }

            candidates.chunked(2).forEach { pair ->
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(3), 0, dp(3))
                }

                pair.forEachIndexed { index, button ->
                    button.minHeight = 0
                    button.minWidth = 0
                    button.textSize = 7.2f
                    button.setPadding(dp(4), 0, dp(4), 0)
                    row.addView(
                        button,
                        LinearLayout.LayoutParams(
                            0,
                            dp(42),
                            1f
                        ).apply {
                            if (index > 0) {
                                marginStart = dp(6)
                            }
                        }
                    )
                }

                if (pair.size == 1) {
                    row.addView(
                        View(activity),
                        LinearLayout.LayoutParams(
                            0,
                            dp(42),
                            1f
                        ).apply {
                            marginStart = dp(6)
                        }
                    )
                }

                host.addView(row)
            }

            toolsChip?.text = "TOOLS • ${candidates.size}\nLIVE ▸"
            compacted = true
            styleAllButtons(force = true)
        }

        private fun isAdvancedReportControl(button: Button): Boolean {
            val value = normalized(button)
            return listOf(
                "VIEW ",
                "SETTINGS",
                "GUIDES",
                "RESET CAM",
                "AUTO UI",
                "LOCK ",
                "VERIFY",
                "CAMERA CAPS",
                "CAPABILITIES",
                "CLEAN ",
                "ASSIST",
                "HUD SIZE",
                "HUD CONTRAST",
                "HUD BACKING",
                "PRESET",
                "AUTO DIRECTOR",
                "DIRECTOR",
                "MATCH LAST",
                "CONTINUITY",
                "CAMERA HEALTH",
                "BRAND"
            ).any {
                value.startsWith(it)
            }
        }

        private fun isOwnControl(button: Button): Boolean =
            button.tag == TOOLS_TAG

        private fun isGradePanelControl(button: Button): Boolean {
            val value = normalized(button)
            if (
                value.startsWith("GRADE ") ||
                value == "×" ||
                value.contains("HOLD ORIGINAL")
            ) {
                return true
            }

            val parent = button.parent as? ViewGroup ?: return false
            var hasCompare = false
            for (index in 0 until parent.childCount) {
                val sibling = parent.getChildAt(index) as? Button ?: continue
                if (normalized(sibling).contains("HOLD ORIGINAL")) {
                    hasCompare = true
                    break
                }
            }

            return hasCompare &&
                (value.startsWith("LOOK ") || value == "RESET")
        }

        private fun refreshThemeIfNeeded() {
            val signature =
                collectButtons(root).joinToString("|") {
                        "${normalized(it)}:${it.isSelected}"
                    }

            if (signature != lastThemeSignature) {
                styleAllButtons(force = true)
                lastThemeSignature = signature
            }
        }

        private fun styleAllButtons(force: Boolean) {
            if (!force) return

            val buttons = collectButtons(root)

            buttons.forEach { button ->
                val value = normalized(button)

                // RECORD remains the primary red safety/action control.
                if (value.contains("RECORD")) {
                    return@forEach
                }

                val active =
                    button.isSelected ||
                        value.startsWith("COLOR ") ||
                        value.startsWith("GRADE ") ||
                        value.startsWith("TOOLS ") ||
                        value.endsWith(" ON") ||
                        value.contains(" ACTIVE")

                val fill =
                    if (active) {
                        DevelopUgandaFivemods8Theme.surfaceRaised
                    } else {
                        DevelopUgandaFivemods8Theme.surface
                    }
                val stroke =
                    if (active) {
                        DevelopUgandaFivemods8Theme.accent
                    } else {
                        DevelopUgandaFivemods8Theme.outline
                    }

                button.background =
                    rounded(
                        fill,
                        stroke,
                        if (value == "×") 12 else 15
                    )
                button.setTextColor(DevelopUgandaFivemods8Theme.content)
                button.typeface = Typeface.DEFAULT_BOLD
                button.isAllCaps = false

                if (button.tag == TOOLS_TAG) {
                    button.textSize = 8.0f
                }
            }

            toolsDrawer?.background =
                rounded(
                    DevelopUgandaFivemods8Theme.surfaceRaised,
                    DevelopUgandaFivemods8Theme.outline,
                    18
                )
            toolsHeader?.setTextColor(DevelopUgandaFivemods8Theme.content)
        }

        private fun accentFor(
            value: String,
            palette: List<Int>,
            fallbackIndex: Int
        ): Int = DevelopUgandaFivemods8Theme.accent

        private fun defaultPalette(): List<Int> =
            listOf(
                DevelopUgandaFivemods8Theme.accent,
                DevelopUgandaFivemods8Theme.accent,
                DevelopUgandaFivemods8Theme.accent,
                DevelopUgandaFivemods8Theme.accent
            )

        private fun collectButtons(view: View): MutableList<Button> {
            val result = mutableListOf<Button>()

            fun walk(node: View) {
                if (node is Button) {
                    result.add(node)
                }
                if (node is ViewGroup) {
                    for (index in 0 until node.childCount) {
                        walk(node.getChildAt(index))
                    }
                }
            }

            walk(view)
            return result
        }

        private fun normalized(button: Button): String =
            button.text
                ?.toString()
                .orEmpty()
                .replace("\n", " ")
                .replace(Regex("\\s+"), " ")
                .trim()
                .uppercase()

        private fun rounded(
            fill: Int,
            stroke: Int,
            radiusDp: Int
        ): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fill)
                cornerRadius = dp(radiusDp).toFloat()
                setStroke(dp(1), stroke)
            }

        private fun scope(): String =
            try {
                scopeProvider().ifBlank { "GLOBAL" }
            } catch (_: Exception) {
                "GLOBAL"
            }

        private fun hint(): String =
            try {
                hintProvider().ifBlank { "GENERAL" }
            } catch (_: Exception) {
                "GENERAL"
            }

        private fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density).roundToInt()
    }
}

// UNIFIED LIVE CONTROL DECK
