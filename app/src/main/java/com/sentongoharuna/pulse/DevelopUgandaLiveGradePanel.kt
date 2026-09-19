package com.sentongoharuna.pulse

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import java.util.Locale

/**
 * V235 Uganda LUT Mixer Pro.
 *
 * Operator-only live grading:
 * - original CameraX MP4 remains untouched
 * - authored Uganda Scene palette controls remain 0–200%
 * - six plain-language colors are added to every LUT, also 0–200%
 * - HOLD ORIGINAL gives instant comparison
 * - final COLOR_MASTER uses the real tuned 17^3 Media3 LUT
 */
object DevelopUgandaLiveGradePanel {

    private const val TAG = "develop_uganda_v235_live_grade_panel"

    fun attach(
        activity: AppCompatActivity,
        root: FrameLayout,
        previewView: PreviewView,
        scopeProvider: () -> String,
        hintProvider: () -> String
    ) {
        if (root.findViewWithTag<View>(TAG) != null) {
            return
        }

        Controller(
            activity,
            root,
            previewView,
            scopeProvider,
            hintProvider
        ).attach()
    }

    private class Controller(
        private val activity: AppCompatActivity,
        private val root: FrameLayout,
        private val previewView: PreviewView,
        private val scopeProvider: () -> String,
        private val hintProvider: () -> String
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private var bypass = false
        private var expanded = false
        private var loading = false
        private var lastSignature = ""

        private lateinit var chip: Button
        private lateinit var card: LinearLayout
        private lateinit var active: TextView
        private lateinit var masterLabel: TextView
        private lateinit var masterSeek: SeekBar
        private lateinit var scenePaletteHost: LinearLayout
        private lateinit var everydayHost: LinearLayout
        private lateinit var lookButton: Button
        private lateinit var compareButton: Button

        private val refreshRunnable =
            object : Runnable {
                override fun run() {
                    if (activity.isFinishing || activity.isDestroyed) {
                        return
                    }

                    refreshIfNeeded()
                    handler.postDelayed(this, 650L)
                }
            }

        fun attach() {
            previewView.implementationMode =
                PreviewView.ImplementationMode.COMPATIBLE

            DevelopUgandaColorEngine.setMonitorEnabled(
                activity,
                true
            )

            buildChip()
            buildCard()
            applyNow()
            refreshUi(force = true)
            handler.postDelayed(refreshRunnable, 650L)
        }

        private fun buildChip() {
            chip = Button(activity).apply {
                tag = TAG
                text = "LUTS • V235\nMIX ▸"
                textSize = 8.2f
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
                background = rounded(
                    DevelopUgandaFivemods8Theme.surfaceScrim(230),
                    DevelopUgandaFivemods8Theme.accent,
                    16
                )
                setOnClickListener {
                    expanded = !expanded
                    card.visibility =
                        if (expanded) View.VISIBLE else View.GONE
                    refreshUi(force = true)
                }
            }

            root.addView(
                chip,
                FrameLayout.LayoutParams(
                    dp(96),
                    dp(50)
                ).apply {
                    gravity = Gravity.END or Gravity.CENTER_VERTICAL
                    rightMargin = dp(8)
                }
            )
        }

        private fun buildCard() {
            card = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(9), dp(10), dp(9))
                background = rounded(
                    DevelopUgandaFivemods8Theme.surfaceScrim(247),
                    DevelopUgandaFivemods8Theme.outline,
                    18
                )
                visibility = View.GONE
                elevation = 0f
            }

            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            active = label(
                "V235 • UGANDA LUT MIXER",
                9.5f,
                DevelopUgandaFivemods8Theme.content,
                true
            ).apply {
                maxLines = 2
            }

            header.addView(
                active,
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
                setTextColor(DevelopUgandaFivemods8Theme.content)
                background = rounded(
                    DevelopUgandaFivemods8Theme.surfaceRaised,
                    DevelopUgandaFivemods8Theme.accent,
                    12
                )
                setOnClickListener {
                    expanded = false
                    card.visibility = View.GONE
                }
            }

            header.addView(
                close,
                LinearLayout.LayoutParams(
                    dp(42),
                    dp(38)
                )
            )

            card.addView(header)

            val quick = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(5), 0, dp(4))
            }

            lookButton =
                smallButton("LOOK ▾", DevelopUgandaFivemods8Theme.accent).apply {
                    setOnClickListener { chooseLook() }
                }

            compareButton =
                smallButton(
                    "HOLD ORIGINAL",
                    DevelopUgandaFivemods8Theme.accent
                ).apply {
                    setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                bypass = true
                                DevelopUgandaColorEngine.clearPreviewMonitor(
                                    previewView
                                )
                                text = "ORIGINAL • HOLD"
                                true
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                bypass = false
                                text = "HOLD ORIGINAL"
                                applyNow()
                                true
                            }

                            else -> true
                        }
                    }
                }

            val reset =
                smallButton("RESET ALL", DevelopUgandaFivemods8Theme.contentDim).apply {
                    setOnClickListener { resetCurrent() }
                }

            quick.addView(lookButton, weight())
            quick.addView(space(dp(5)), fixed(dp(5), 1))
            quick.addView(compareButton, weight())
            quick.addView(space(dp(5)), fixed(dp(5), 1))
            quick.addView(reset, weight())

            card.addView(quick)

            masterLabel =
                label(
                    "MASTER LUT STRENGTH • 0%",
                    8f,
                    DevelopUgandaFivemods8Theme.content,
                    true
                )

            card.addView(masterLabel)

            masterSeek = SeekBar(activity).apply {
                max = 100
                progressTintList =
                    ColorStateList.valueOf(DevelopUgandaFivemods8Theme.accent)
                thumbTintList =
                    ColorStateList.valueOf(DevelopUgandaFivemods8Theme.accent)

                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            masterLabel.text =
                                "MASTER LUT STRENGTH • $progress%"

                            if (fromUser && !loading) {
                                DevelopUgandaColorEngine.setStrength(
                                    activity,
                                    scope(),
                                    progress
                                )
                                applyNow()
                                updateHeader()
                            }
                        }

                        override fun onStartTrackingTouch(
                            seekBar: SeekBar?
                        ) { }

                        override fun onStopTrackingTouch(
                            seekBar: SeekBar?
                        ) { }
                    }
                )
            }

            card.addView(masterSeek)

            val scrollBody = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }

            scrollBody.addView(
                sectionTitle(
                    "SCENE PALETTE • AUTHORED LUT COLORS • 0–200%"
                )
            )

            scenePaletteHost = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            scrollBody.addView(scenePaletteHost)

            scrollBody.addView(
                sectionTitle(
                    "EVERYDAY COLORS • SAME 6 ON EVERY LUT • 0–200%"
                ).apply {
                    setPadding(0, dp(8), 0, dp(2))
                }
            )

            scrollBody.addView(
                label(
                    "GREEN • RED • YELLOW • BLUE • ORANGE • BROWN\n100% keeps the designed LUT. Move only the colors you want.",
                    7.0f,
                    DevelopUgandaFivemods8Theme.contentDim,
                    false
                ).apply {
                    setPadding(0, 0, 0, dp(4))
                }
            )

            everydayHost = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            scrollBody.addView(everydayHost)

            scrollBody.addView(
                label(
                    "AUTO-SAVED PER LUT • ORIGINAL MP4 stays clean. COLOR_MASTER.mp4 receives the final V235 tuned 17³ LUT.",
                    7.1f,
                    DevelopUgandaFivemods8Theme.contentDim,
                    true
                ).apply {
                    setPadding(0, dp(7), 0, dp(4))
                }
            )

            val scroll = ScrollView(activity).apply {
                isFillViewport = false
                addView(
                    scrollBody,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }

            card.addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )

            val displayHeight =
                activity.resources.displayMetrics.heightPixels
            val desiredHeight =
                (displayHeight * 0.66f)
                    .toInt()
                    .coerceIn(
                        dp(390),
                        dp(640)
                    )

            root.addView(
                card,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    desiredHeight
                ).apply {
                    gravity = Gravity.BOTTOM
                    leftMargin = dp(8)
                    rightMargin = dp(8)
                    bottomMargin = dp(76)
                }
            )
        }

        private fun chooseLook() {
            val labels =
                DevelopUgandaColorEngine.menuLabels()
            val selected =
                DevelopUgandaColorEngine.selectedMenuIndex(
                    activity,
                    scope()
                )

            DevelopUgandaNavySheet.show(
                activity = activity,
                title = "V235 • UGANDA COLOR LOOK",
                subtitle =
                    "SELECT LUT • NUMBER = LOOK VERSION • SCROLL-SAFE NAVY SHEET",
                labels = labels,
                selectedIndex = selected
            ) { which ->
                DevelopUgandaColorEngine.setSelectedMenuIndex(
                    activity,
                    scope(),
                    which
                )
                lastSignature = ""
                applyNow()
                refreshUi(force = true)
            }
        }

        private fun resetCurrent() {
            val selection = resolve()
            val profile = selection.profile ?: return

            DevelopUgandaColorTuner.reset(
                activity,
                scope(),
                profile.id
            )

            DevelopUgandaEverydayColorMixer.reset(
                activity,
                scope(),
                profile.id
            )

            DevelopUgandaColorEngine.setStrength(
                activity,
                scope(),
                profile.defaultStrength
            )

            lastSignature = ""
            applyNow()
            refreshUi(force = true)
        }

        private fun rebuildControls(
            selection: DevelopUgandaColorEngine.ResolvedSelection
        ) {
            scenePaletteHost.removeAllViews()
            everydayHost.removeAllViews()

            val profile = selection.profile

            if (profile == null) {
                val raw =
                    label(
                        "ORIGINAL • LIVE GRADE BYPASSED",
                        8f,
                        DevelopUgandaFivemods8Theme.contentDim,
                        true
                    )

                scenePaletteHost.addView(raw)
                return
            }

            rebuildScenePalette(profile)
            rebuildEverydayColors(profile)
        }

        private fun rebuildScenePalette(
            profile: DevelopUgandaColorEngine.Profile
        ) {
            val style =
                DevelopUgandaColorTuner.style(profile.id)
                    ?: return

            val tuning =
                DevelopUgandaColorTuner.load(
                    activity,
                    scope(),
                    profile.id
                )

            style.slots.forEachIndexed { index, slot ->
                val row =
                    mixerRow(
                        number = index + 1,
                        name = slot.name,
                        example = slot.role.name.lowercase(),
                        hex = slot.hex,
                        value = tuning.value(index)
                    ) { progress ->
                        DevelopUgandaColorTuner.set(
                            activity,
                            scope(),
                            profile.id,
                            index,
                            progress
                        )
                    }

                scenePaletteHost.addView(row)
            }
        }

        private fun rebuildEverydayColors(
            profile: DevelopUgandaColorEngine.Profile
        ) {
            val tuning =
                DevelopUgandaEverydayColorMixer.load(
                    activity,
                    scope(),
                    profile.id
                )

            DevelopUgandaEverydayColorMixer.families
                .forEachIndexed { index, family ->
                    val row =
                        mixerRow(
                            number = index + 1,
                            name = family.name,
                            example = family.example,
                            hex = family.hex,
                            value = tuning.value(index)
                        ) { progress ->
                            DevelopUgandaEverydayColorMixer.set(
                                activity,
                                scope(),
                                profile.id,
                                index,
                                progress
                            )
                        }

                    everydayHost.addView(row)
                }
        }

        private fun mixerRow(
            number: Int,
            name: String,
            example: String,
            hex: String,
            value: Int,
            save: (Int) -> Unit
        ): LinearLayout {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(1), 0, dp(1))
            }

            val swatch = TextView(activity).apply {
                text = ""
                background = rounded(
                    Color.parseColor(hex),
                    DevelopUgandaFivemods8Theme.content,
                    8
                )
            }
            row.addView(
                swatch,
                fixed(dp(23), dp(23))
            )

            val valueLabel =
                label(
                    "%02d %s • %d%%\n%s".format(
                        Locale.US,
                        number,
                        name,
                        value,
                        example
                    ),
                    6.8f,
                    DevelopUgandaFivemods8Theme.content,
                    true
                ).apply {
                    setPadding(
                        dp(6),
                        0,
                        dp(3),
                        0
                    )
                    maxLines = 2
                }

            row.addView(
                valueLabel,
                LinearLayout.LayoutParams(
                    dp(138),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            val seek = SeekBar(activity).apply {
                max = 200
                progress = value
                progressTintList =
                    ColorStateList.valueOf(
                        Color.parseColor(hex)
                    )
                thumbTintList =
                    ColorStateList.valueOf(
                        Color.parseColor(hex)
                    )

                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            valueLabel.text =
                                "%02d %s • %d%%\n%s".format(
                                    Locale.US,
                                    number,
                                    name,
                                    progress,
                                    example
                                )

                            if (fromUser && !loading) {
                                save(progress)
                                applyNow()
                                updateHeader()
                            }
                        }

                        override fun onStartTrackingTouch(
                            seekBar: SeekBar?
                        ) { }

                        override fun onStopTrackingTouch(
                            seekBar: SeekBar?
                        ) { }
                    }
                )
            }

            row.addView(
                seek,
                LinearLayout.LayoutParams(
                    0,
                    dp(35),
                    1f
                )
            )

            return row
        }

        private fun applyNow() {
            if (bypass) {
                return
            }

            DevelopUgandaColorEngine.setMonitorEnabled(
                activity,
                true
            )

            DevelopUgandaColorEngine.applyPreviewMonitor(
                previewView,
                resolve(),
                scope()
            )
        }

        private fun resolve():
            DevelopUgandaColorEngine.ResolvedSelection =
            DevelopUgandaColorEngine.resolve(
                activity,
                scope(),
                hint()
            )

        private fun refreshIfNeeded() {
            val value = signature()

            if (value != lastSignature) {
                refreshUi(force = true)
                applyNow()
            }
        }

        private fun refreshUi(
            force: Boolean
        ) {
            if (!force && signature() == lastSignature) {
                return
            }

            loading = true

            val selection = resolve()

            masterSeek.progress =
                selection.strength.coerceIn(0, 100)

            masterLabel.text =
                "MASTER LUT STRENGTH • ${selection.strength}%"

            rebuildControls(selection)
            updateHeader()

            loading = false
            lastSignature = signature()
        }

        private fun updateHeader() {
            val selection = resolve()

            val name =
                selection.profile?.let {
                    DevelopUgandaColorTuner.displayName(
                        it.id,
                        it.label
                    )
                } ?: "ORIGINAL"

            val custom =
                selection.profile?.let { profile ->
                    val sceneCustom =
                        !DevelopUgandaColorTuner
                            .load(
                                activity,
                                scope(),
                                profile.id
                            )
                            .isDefault()

                    val everydayCustom =
                        !DevelopUgandaEverydayColorMixer
                            .load(
                                activity,
                                scope(),
                                profile.id
                            )
                            .isDefault()

                    when {
                        sceneCustom && everydayCustom ->
                            "SCENE + COLOR CUSTOM"
                        everydayCustom ->
                            "COLOR CUSTOM"
                        sceneCustom ->
                            "SCENE CUSTOM"
                        else ->
                            "BASE LUT"
                    }
                } ?: "RAW"

            active.text =
                "● V235 LIVE GRADE • $name\n${selection.strength}% • $custom • HOLD ORIGINAL TO COMPARE"

            chip.text =
                "LUTS • ${selection.strength}%\n${name.take(12)} ▸"

            lookButton.text =
                "LOOK ▾\n${name.take(16)}"
        }

        private fun signature(): String {
            val selection = resolve()
            val profile = selection.profile

            val scene =
                if (profile == null) {
                    "RAW"
                } else {
                    DevelopUgandaColorTuner
                        .load(
                            activity,
                            scope(),
                            profile.id
                        )
                        .values
                        .joinToString(",")
                }

            val everyday =
                if (profile == null) {
                    "RAW"
                } else {
                    DevelopUgandaEverydayColorMixer
                        .load(
                            activity,
                            scope(),
                            profile.id
                        )
                        .values
                        .joinToString(",")
                }

            return "${scope()}:${selection.requestedId}:${profile?.id}:${selection.strength}:$scene:$everyday"
        }

        private fun scope(): String =
            scopeProvider().ifBlank {
                "GLOBAL"
            }

        private fun hint(): String =
            try {
                hintProvider()
            } catch (_: Exception) {
                "REPORTER"
            }

        private fun smallButton(
            textValue: String,
            stroke: Int
        ): Button =
            Button(activity).apply {
                text = textValue
                textSize = 7.1f
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
                background =
                    rounded(
                        DevelopUgandaFivemods8Theme.surfaceRaised,
                        stroke,
                        12
                    )
                setPadding(
                    dp(3),
                    0,
                    dp(3),
                    0
                )
            }

        private fun sectionTitle(
            value: String
        ): TextView =
            label(
                value,
                7.5f,
                DevelopUgandaFivemods8Theme.accent,
                true
            ).apply {
                setPadding(
                    0,
                    dp(2),
                    0,
                    dp(2)
                )
            }

        private fun label(
            value: String,
            size: Float,
            color: Int,
            bold: Boolean
        ): TextView =
            TextView(activity).apply {
                text = value
                textSize = size
                setTextColor(color)
                if (bold) {
                    typeface = Typeface.DEFAULT_BOLD
                }
            }

        private fun rounded(
            fill: Int,
            stroke: Int,
            radius: Int
        ): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fill)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }

        private fun space(
            width: Int
        ): View =
            View(activity).apply {
                layoutParams =
                    LinearLayout.LayoutParams(
                        width,
                        1
                    )
            }

        private fun weight():
            LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                0,
                dp(42),
                1f
            )

        private fun fixed(
            width: Int,
            height: Int
        ): LinearLayout.LayoutParams =
            LinearLayout.LayoutParams(
                width,
                height
            )

        private fun dp(
            value: Int
        ): Int =
            (
                value *
                    activity.resources.displayMetrics.density
                ).toInt()
    }
}
