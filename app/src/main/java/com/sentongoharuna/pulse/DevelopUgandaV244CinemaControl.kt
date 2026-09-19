package com.sentongoharuna.pulse

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

object DevelopUgandaV244CinemaControl {
    private const val TAG = "develop_uganda_v244_cinema_control"

    fun attach(activity: DevelopUgandaCameraActivity, root: FrameLayout) {
        if (root.findViewWithTag<View>(TAG) != null) return
        Controller(activity, root).attach()
    }

    private class Controller(
        private val activity: DevelopUgandaCameraActivity,
        private val root: FrameLayout
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private lateinit var marker: View
        private lateinit var strip: TextView
        private lateinit var scrim: View
        private lateinit var drawer: LinearLayout
        private lateinit var falseColor: FalseColorView
        private lateinit var waveform: WaveformView
        private val v248StateBindings =
            mutableListOf<Pair<TextView, () -> String>>()

        private val tick = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return
                strip.text = activity.v244CinemaStatusText()
                updateV248StateBadges()

                if (falseColor.visibility == View.VISIBLE || waveform.visibility == View.VISIBLE) {
                    activity.v244PreviewBitmap()?.let { bitmap ->
                        if (falseColor.visibility == View.VISIBLE) falseColor.submit(bitmap)
                        if (waveform.visibility == View.VISIBLE) waveform.submit(bitmap)
                    }
                }
                handler.postDelayed(this, 450L)
            }
        }

        fun attach() {
            marker = View(activity).apply {
                tag = TAG
                visibility = View.GONE
            }
            root.addView(marker, FrameLayout.LayoutParams(1, 1))
            buildMonitors()
            buildScrim()
            buildDrawer()
            buildStatusStrip()

            marker.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) {
                        handler.removeCallbacksAndMessages(null)
                    }
                }
            )
            handler.post(tick)
        }

        private fun buildMonitors() {
            falseColor = FalseColorView(activity).apply {
                tag = "v244_false_color"
                visibility = View.GONE
                isClickable = false
            }
            root.addView(
                falseColor,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            waveform = WaveformView(activity).apply {
                tag = "v244_waveform"
                visibility = View.GONE
                isClickable = false
            }
            root.addView(
                waveform,
                FrameLayout.LayoutParams(dp(146), dp(84)).apply {
                    gravity = Gravity.START or Gravity.BOTTOM
                    leftMargin = dp(9)
                    bottomMargin = dp(176)
                }
            )
        }

        private fun buildScrim() {
            scrim = View(activity).apply {
                setBackgroundColor(0x55000000)
                visibility = View.GONE
                setOnClickListener { setDrawer(false) }
            }
            root.addView(
                scrim,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        private fun buildStatusStrip() {
            strip = TextView(activity).apply {
                tag = "v244_cinema_status_strip"
                text = activity.v244CinemaStatusText()
                textSize = 6.6f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                setPadding(dp(6), 0, dp(6), 0)
                background = rounded(
                    0xD9031829.toInt(),
                    0xB873B7D9.toInt(),
                    12
                )
                isClickable = true
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    setDrawer(true)
                }
            }

            root.addView(
                strip,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(30)
                ).apply {
                    gravity = Gravity.TOP
                    topMargin = dp(72)
                    leftMargin = dp(92)
                    rightMargin = dp(112)
                }
            )
        }

        private fun buildDrawer() {
            drawer = LinearLayout(activity).apply {
                tag = "v244_cinema_drawer"
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(12), dp(11), dp(10))
                background = rounded(
                    0xFC031829.toInt(),
                    0xFF73B7D9.toInt(),
                    20
                )
                visibility = View.GONE
                elevation = 0f
            }

            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            header.addView(
                TextView(activity).apply {
                    text = "V273 • MOTION + SHOT CONTROL"
                    textSize = 11f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            header.addView(
                card("CLOSE ×", "Return to camera") { setDrawer(false) },
                LinearLayout.LayoutParams(dp(96), dp(58))
            )
            drawer.addView(header)

            drawer.addView(
                TextView(activity).apply {
                    text =
                        "Real Camera2 manual requests only when supported • False Color and Waveform are screen-only."
                    textSize = 7.5f
                    setTextColor(0xFFB8CBD6.toInt())
                    setPadding(0, dp(6), 0, dp(9))
                }
            )

            val body = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }

            section(body, "EXPOSURE")
            body.addView(stateCard(
                "SHUTTER ANGLE",
                "AUTO → 180° → 90° → 360° • real sensor exposure where supported",
                { activity.v248ShutterTag() }
            ) { result(activity.v244CycleShutterAngle()) })
            body.addView(stateCard(
                "ISO / SENSOR GAIN",
                "AUTO → supported ISO values inside the real sensitivity range",
                { activity.v248IsoTag() }
            ) { result(activity.v244CycleIso()) })
            body.addView(stateCard(
                "WHITE BALANCE",
                "AUTO → DAYLIGHT → CLOUDY → TUNGSTEN → FLUORESCENT",
                { activity.v248WhiteBalanceTag() }
            ) { result(activity.v244CycleWhiteBalance()) })
            body.addView(card(
                "RESET AUTO",
                "Return shutter, ISO and white balance to automatic operation"
            ) { result(activity.v244ResetCinemaControls()) })

            section(body, "IMAGE ENGINE")

            body.addView(stateCard(
                "CINEMA IMAGE ENGINE",
                "Hardware-supported noise reduction, controlled edge detail and lens/shading correction",
                { activity.v248ImageEngineTag() }
            ) {
                    result(
                        activity.v246ToggleImageEngine()
                    )
                }
            )

            body.addView(stateCard(
                "HIGHLIGHT PROTECT",
                "Modest exposure protection for bright windows, skies and faces",
                { activity.v248HighlightTag() }
            ) {
                    result(
                        activity.v246ToggleHighlightProtect()
                    )
                }
            )

            body.addView(stateCard(
                "10-BIT / HDR CAPABILITY",
                "Reports the real dynamic-range capability; never labels an 8-bit recording as 10-bit",
                { activity.v248HdrTag() }
            ) {
                    result(
                        activity.v246TenBitHdrStatus()
                    )
                }
            )

            body.addView(stateCard(
                "ADAPTIVE DETAIL",
                "Daylight preserves texture; low-light and motion avoid maximum noise-reduction smearing",
                { activity.v248AdaptiveDetailTag() }
            ) {
                    result(
                        activity.v247ToggleAdaptiveDetail()
                    )
                }
            )

            body.addView(
                card(
                    "MOTION / SHUTTER GUIDE",
                    "Reads camera motion and recommends 180° cinema or 90° action shutter without faking unsupported control"
                ) {
                    result(
                        activity.v247MotionShutterGuide()
                    )
                }
            )

            section(body, "MONITORING")
            body.addView(stateCard(
                "FALSE COLOR",
                "Screen-only exposure map for shadows, midtones, skin range and clipping",
                { if (falseColor.visibility == View.VISIBLE) "ON" else "OFF" }
            ) {
                falseColor.visibility =
                    if (falseColor.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                result(if (falseColor.visibility == View.VISIBLE) "FALSE COLOR ON" else "FALSE COLOR OFF")
            })
            body.addView(stateCard(
                "WAVEFORM",
                "Compact screen-only luma waveform",
                { if (waveform.visibility == View.VISIBLE) "ON" else "OFF" }
            ) {
                waveform.visibility =
                    if (waveform.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                result(if (waveform.visibility == View.VISIBLE) "WAVEFORM ON" else "WAVEFORM OFF")
            })
            body.addView(card(
                "ZEBRA / FOCUS ASSIST",
                "Use the existing V238/V242 professional shot assist"
            ) {
                findClickableText("ASSIST")?.performClick()
                    ?: result("SHOT ASSIST REMAINS IN PRO SETTINGS")
            })

            body.addView(
                stateCard(
                    "TAGS / BURN-IN",
                    "Quick ON/OFF before recording • your previous tag selection is remembered",
                    { activity.v249TagStateCompact() }
                ) {
                    result(
                        activity.v249ToggleReporterTags()
                    )
                }
            )

            body.addView(
                card(
                    "EDIT TAG DETAILS",
                    "Open the existing Brand + Metadata page to choose exactly which tags are enabled"
                ) {
                    result(
                        activity.v248OpenReporterTagSettings()
                    )
                }
            )

            section(body, "MASTER + COLOR")
            body.addView(stateCard(
                "4K MASTER / QUALITY",
                "Open the real quality selector • saved Gallery quality verification remains active",
                { activity.v248MasterTag() }
            ) {
                root.findViewWithTag<View>("v237_quality_button")?.performClick()
                    ?: result("4K MASTER REMAINS IN PRO SETTINGS")
            })
            body.addView(card(
                "LUT MONITOR / COLOR MASTER",
                "Original CameraX master stays safe • separate V235 17³ LUT master remains available"
            ) {
                root.findViewWithTag<View>("develop_uganda_v235_live_grade_panel")?.performClick()
                    ?: result("LUTS REMAIN AT TOP-LEFT")
            })

            val scroll = ScrollView(activity).apply {
                addView(
                    body,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            drawer.addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            drawer.addView(
                TextView(activity).apply {
                    text = "LIVE STATE • GREEN ON • GREY OFF • GOLD AUTO • BLUE LOCK"
                    textSize = 7.1f
                    setTextColor(0xFF91B6A0.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(7), 0, 0)
                }
            )

            val width =
                (activity.resources.displayMetrics.widthPixels * 0.86f)
                    .toInt()
                    .coerceAtMost(dp(420))

            root.addView(
                drawer,
                FrameLayout.LayoutParams(
                    width,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity = Gravity.END
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                    rightMargin = dp(4)
                }
            )
        }

        private fun setDrawer(open: Boolean) {
            drawer.animate().cancel()
            scrim.animate().cancel()
            strip.animate().cancel()

            if (open) {
                scrim.alpha = 0f
                scrim.visibility = View.VISIBLE
                drawer.alpha = 0f
                drawer.translationX = dp(26).toFloat()
                drawer.visibility = View.VISIBLE
                strip.visibility = View.GONE

                scrim.animate().alpha(1f).setDuration(130L).start()
                drawer.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(180L)
                    .start()

                scrim.bringToFront()
                drawer.bringToFront()
                updateV248StateBadges()
            } else {
                drawer.animate()
                    .alpha(0f)
                    .translationX(dp(22).toFloat())
                    .setDuration(150L)
                    .withEndAction {
                        drawer.visibility = View.GONE
                        drawer.alpha = 1f
                        drawer.translationX = 0f
                    }
                    .start()

                scrim.animate()
                    .alpha(0f)
                    .setDuration(120L)
                    .withEndAction {
                        scrim.visibility = View.GONE
                        scrim.alpha = 1f
                    }
                    .start()

                strip.alpha = 0f
                strip.visibility = View.VISIBLE
                strip.animate().alpha(1f).setDuration(140L).start()

                if (falseColor.visibility == View.VISIBLE) falseColor.bringToFront()
                if (waveform.visibility == View.VISIBLE) waveform.bringToFront()
                strip.bringToFront()
            }
        }

        private fun section(host: LinearLayout, value: String) {
            host.addView(
                TextView(activity).apply {
                    text = value
                    textSize = 8f
                    setTextColor(0xFFD0B06F.toInt())
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(9), 0, dp(4))
                }
            )
        }

        private fun card(
            title: String,
            detail: String,
            action: () -> Unit
        ): LinearLayout =
            LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(9), dp(11), dp(9))
                background = rounded(
                    0xEE092236.toInt(),
                    0xFF456983.toInt(),
                    15
                )
                isClickable = true
                isFocusable = true
                addView(
                    TextView(activity).apply {
                        text = "$title   ›"
                        textSize = 8.7f
                        setTextColor(Color.WHITE)
                        typeface = Typeface.DEFAULT_BOLD
                    }
                )
                addView(
                    TextView(activity).apply {
                        text = detail
                        textSize = 7f
                        setTextColor(0xFFB6C4CE.toInt())
                        setPadding(0, dp(3), 0, 0)
                    }
                )
                setOnClickListener { view ->
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    DevelopUgandaV28012SafeActions.run(view.context, "CINEMA CONTROL ACTION • $title") { action() }
                }
                layoutParams =
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dp(5)
                    }
            }

        private fun stateCard(
            title: String,
            detail: String,
            state: () -> String,
            action: () -> Unit
        ): LinearLayout {
            val badge =
                TextView(activity).apply {
                    textSize = 7.1f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setPadding(dp(8), dp(3), dp(8), dp(3))
                    minWidth = dp(54)
                }

            val titleText =
                TextView(activity).apply {
                    text = "$title   ›"
                    textSize = 8.7f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                }

            val titleRow =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(
                        titleText,
                        LinearLayout.LayoutParams(
                            0,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                    )
                    addView(
                        badge,
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            dp(28)
                        )
                    )
                }

            val row =
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(11), dp(9), dp(11), dp(9))
                    background = rounded(
                        0xEE092236.toInt(),
                        0xFF456983.toInt(),
                        15
                    )
                    isClickable = true
                    isFocusable = true
                    addView(titleRow)
                    addView(
                        TextView(activity).apply {
                            text = detail
                            textSize = 7f
                            setTextColor(0xFFB6C4CE.toInt())
                            setPadding(0, dp(3), 0, 0)
                        }
                    )
                    setOnClickListener {
                        animate()
                            .scaleX(0.992f)
                            .scaleY(0.992f)
                            .setDuration(55L)
                            .withEndAction {
                                animate()
                                    .scaleX(1f)
                                    .scaleY(1f)
                                    .setDuration(90L)
                                    .start()
                            }
                            .start()
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        action()
                        updateV248StateBadges()
                    }
                    layoutParams =
                        LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            bottomMargin = dp(5)
                        }
                }

            v248StateBindings.add(badge to state)
            applyV248Badge(badge, state())
            return row
        }

        private fun updateV248StateBadges() {
            v248StateBindings.forEach { (view, provider) ->
                applyV248Badge(
                    view,
                    try {
                        provider()
                    } catch (_: Exception) {
                        "CHECK"
                    }
                )
            }
        }

        private fun applyV248Badge(
            view: TextView,
            raw: String
        ) {
            val value = raw.trim().ifBlank { "CHECK" }
            val upper = value.uppercase()

            val fill =
                when {
                    upper.startsWith("ON") -> 0xFF235B43.toInt()
                    upper == "OFF" -> 0xFF394550.toInt()
                    upper.contains("AUTO") -> 0xFF6B5930.toInt()
                    upper.contains("LOCK") || upper.contains("10-BIT") -> 0xFF195875.toInt()
                    upper.contains("WARN") || upper.contains("NO") -> 0xFF793E43.toInt()
                    else -> 0xFF385166.toInt()
                }

            val stroke =
                when {
                    upper.startsWith("ON") -> 0xFF91B6A0.toInt()
                    upper.contains("LOCK") || upper.contains("10-BIT") -> 0xFF73B7D9.toInt()
                    upper.contains("AUTO") -> 0xFFD0B06F.toInt()
                    else -> 0xFF8294A1.toInt()
                }

            view.text = value
            view.setTextColor(Color.WHITE)
            view.background = rounded(fill, stroke, 10)
        }

        private fun result(value: String) {
            strip.text = activity.v244CinemaStatusText()
            updateV248StateBadges()
            Toast.makeText(activity, value, Toast.LENGTH_SHORT).show()
        }

        private fun findClickableText(word: String): View? {
            var found: View? = null
            val needle = word.uppercase()

            fun walk(view: View) {
                if (found != null) return
                if (view is TextView) {
                    val value = view.text?.toString()?.uppercase() ?: ""
                    if (value.contains(needle) && view.isClickable) {
                        found = view
                        return
                    }
                }
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        walk(view.getChildAt(i))
                        if (found != null) return
                    }
                }
            }

            walk(root)
            return found
        }

        private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fill)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }

        private fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density).toInt()
    }

    private class FalseColorView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cols = 20
        private val rows = 34
        private var values = IntArray(cols * rows)

        init {
            paint.style = Paint.Style.FILL
            alpha = 0.82f
        }

        fun submit(bitmap: Bitmap) {
            if (bitmap.width <= 0 || bitmap.height <= 0) return
            val scaled = Bitmap.createScaledBitmap(bitmap, cols, rows, true)
            val next = IntArray(cols * rows)

            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val c = scaled.getPixel(x, y)
                    val luma =
                        (
                            0.2126f * Color.red(c) +
                            0.7152f * Color.green(c) +
                            0.0722f * Color.blue(c)
                        ) / 255f

                    next[y * cols + x] =
                        when {
                            luma < 0.03f -> 0xFF3A0B56.toInt()
                            luma < 0.10f -> 0xFF163A9B.toInt()
                            luma < 0.22f -> 0xFF00A6D6.toInt()
                            luma < 0.42f -> 0xFF35A853.toInt()
                            luma < 0.58f -> 0xFF777777.toInt()
                            luma < 0.72f -> 0xFFD9829B.toInt()
                            luma < 0.88f -> 0xFFF0C43A.toInt()
                            luma < 0.97f -> 0xFFFF7A1A.toInt()
                            else -> 0xFFFF2424.toInt()
                        }
                }
            }

            values = next
            scaled.recycle()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cw = width.toFloat() / cols
            val ch = height.toFloat() / rows

            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    paint.color = values[y * cols + x]
                    canvas.drawRect(
                        x * cw,
                        y * ch,
                        (x + 1) * cw + 1f,
                        (y + 1) * ch + 1f,
                        paint
                    )
                }
            }

            paint.color = Color.WHITE
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = 8f * resources.displayMetrics.scaledDensity
            canvas.drawText(
                "FALSE COLOR • SCREEN ONLY",
                10f,
                18f * resources.displayMetrics.density,
                paint
            )
        }
    }

    private class WaveformView(context: android.content.Context) : View(context) {
        private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val cols = 48
        private val rows = 24
        private var luma = FloatArray(cols * rows)

        init {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(0xCB031829.toInt())
                cornerRadius = 12f * resources.displayMetrics.density
                setStroke(
                    resources.displayMetrics.density.roundToInt().coerceAtLeast(1),
                    0xFF73B7D9.toInt()
                )
            }
            pointPaint.color = 0xCCF2F5F8.toInt()
            pointPaint.strokeWidth = resources.displayMetrics.density
            gridPaint.color = 0x5573B7D9
            gridPaint.strokeWidth = resources.displayMetrics.density
            labelPaint.color = Color.WHITE
            labelPaint.textSize = 7f * resources.displayMetrics.scaledDensity
            labelPaint.typeface = Typeface.DEFAULT_BOLD
        }

        fun submit(bitmap: Bitmap) {
            if (bitmap.width <= 0 || bitmap.height <= 0) return
            val scaled = Bitmap.createScaledBitmap(bitmap, cols, rows, true)
            val next = FloatArray(cols * rows)

            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val c = scaled.getPixel(x, y)
                    next[y * cols + x] =
                        (
                            (
                                0.2126f * Color.red(c) +
                                0.7152f * Color.green(c) +
                                0.0722f * Color.blue(c)
                            ) / 255f
                        ).coerceIn(0f, 1f)
                }
            }

            luma = next
            scaled.recycle()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val d = resources.displayMetrics.density
            val left = 8f * d
            val right = width - left
            val top = 14f * d
            val bottom = height - 8f * d
            val uw = (right - left).coerceAtLeast(1f)
            val uh = (bottom - top).coerceAtLeast(1f)

            for (i in 0..4) {
                val y = top + uh * (i / 4f)
                canvas.drawLine(left, y, right, y, gridPaint)
            }

            for (x in 0 until cols) {
                val px = left + uw * (x.toFloat() / (cols - 1).coerceAtLeast(1))
                for (y in 0 until rows step 2) {
                    val py = bottom - uh * luma[y * cols + x]
                    canvas.drawPoint(px, py, pointPaint)
                }
            }

            canvas.drawText("WAVEFORM", left, 10f * d, labelPaint)
        }
    }
}
