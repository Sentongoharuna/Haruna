package com.sentongoharuna.pulse

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.SensorManager
import android.view.Gravity
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

data class DevelopUgandaBroadcastStatus(
    val format: String?,
    val codec: String?,
    val timecode: String?,
    val battery: Int?,
    val freeStorageGb: Long?,
    val audio: String?,
    val recording: Boolean,
)

/**
 * Screen-only camera chrome. It never receives a capture Effect, never draws a
 * filled preview layer and never changes camera binding or requested rotation.
 */
object DevelopUgandaBroadcastCameraChrome {
    fun attach(
        activity: Activity,
        root: FrameLayout,
        preview: View,
        profile: DevelopUgandaModeProfile,
        onMark: (() -> Unit)? = null,
    ): Controller = Controller(activity, root, preview, profile, onMark).also { it.attach() }

    class Controller internal constructor(
        private val activity: Activity,
        private val root: FrameLayout,
        private val preview: View,
        private val profile: DevelopUgandaModeProfile,
        private val onMark: (() -> Unit)?,
    ) {
        private val prefs = activity.duSharedPreferences(
            "develop_uganda_broadcast_camera_chrome_${profile.page.name.lowercase()}",
            Context.MODE_PRIVATE,
        )
        private val originalVisibility = LinkedHashMap<View, Int>()
        private lateinit var guide: GuideView
        private lateinit var shield: View
        private lateinit var controlRail: LinearLayout
        private lateinit var cleanButton: TextView
        private lateinit var lockButton: TextView
        private lateinit var guideButton: TextView
        private lateinit var handButton: TextView
        private lateinit var nightButton: TextView
        private lateinit var dimButton: TextView
        private lateinit var markButton: TextView
        private var clean = false
        private var locked = false
        private var recording = false
        private var leftHanded = prefs.getBoolean("left_handed", false)
        private var night = prefs.getBoolean("dark_adapt", false)
        private var dimmed = prefs.getBoolean("screen_dim", false)
        private var orientationDegrees = 0f
        private val orientationListener = object : OrientationEventListener(activity, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
                val target = when (orientation) {
                    in 45..134 -> 270f
                    in 135..224 -> 180f
                    in 225..314 -> 90f
                    else -> 0f
                }
                if (target == orientationDegrees) return
                orientationDegrees = target
                // Rotate controls in place. The FIVEMODS 12 status strip is
                // the sole owner of camera readouts.
                listOf(cleanButton, lockButton).forEach { it.rotation = target }
            }
        }

        internal fun attach() {
            for (index in 0 until root.childCount) {
                root.getChildAt(index).let { originalVisibility[it] = it.visibility }
            }
            guide = GuideView(activity, profile).apply {
                isClickable = false
                visibility = if (prefs.getBoolean("guides", false)) View.VISIBLE else View.GONE
                contentDescription = "Screen-only framing guides for ${profile.displayName}"
            }
            root.addView(guide, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))

            shield = View(activity).apply {
                setBackgroundColor(DevelopUgandaFivemods8Theme.transparent)
                isClickable = true
                isFocusable = true
                visibility = View.GONE
                contentDescription = "Controls locked. Hold unlock to restore touch controls."
                setOnClickListener { DevelopUgandaBroadcastPresentation.rejected(this) }
            }
            root.addView(shield, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))

            controlRail = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setBackgroundColor(DevelopUgandaFivemods8Theme.transparent)
            }
            cleanButton = control("CLEAN") { toggleClean() }
            lockButton = control("LOCK") { lock() }.apply {
                setOnLongClickListener {
                    if (locked) unlock()
                    true
                }
            }
            guideButton = control(if (guide.visibility == View.VISIBLE) "GUIDES ON" else "GUIDES") {
                guide.visibility = if (guide.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                prefs.edit().putBoolean("guides", guide.visibility == View.VISIBLE).apply()
                guideButton.text = if (guide.visibility == View.VISIBLE) "GUIDES ON" else "GUIDES"
            }
            handButton = control(if (leftHanded) "LEFT" else "RIGHT") {
                leftHanded = !leftHanded
                prefs.edit().putBoolean("left_handed", leftHanded).apply()
                handButton.text = if (leftHanded) "LEFT" else "RIGHT"
                placeRail()
            }
            nightButton = control(if (night) "NIGHT ON" else "NIGHT") {
                night = !night
                prefs.edit().putBoolean("dark_adapt", night).apply()
                nightButton.text = if (night) "NIGHT ON" else "NIGHT"
                applyChromeColor()
            }
            dimButton = control(if (dimmed) "DIM ON" else "DIM") {
                dimmed = !dimmed
                prefs.edit().putBoolean("screen_dim", dimmed).apply()
                dimButton.text = if (dimmed) "DIM ON" else "DIM"
                applyScreenDim()
            }
            markButton = control("MARK • REC") {
                if (!recording || onMark == null) {
                    DevelopUgandaBroadcastPresentation.rejected(markButton)
                } else {
                    onMark.invoke()
                }
            }
            listOf(cleanButton, lockButton, markButton, guideButton, handButton, nightButton, dimButton).forEach {
                controlRail.addView(it, LinearLayout.LayoutParams(unit() * 11, unit() * 6).apply {
                    bottomMargin = unit()
                })
            }
            root.addView(controlRail)
            placeRail()
            applyChromeColor()
            applyScreenDim()
            root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = onResume()
                override fun onViewDetachedFromWindow(view: View) = onPause()
            })
            onResume()
        }

        fun update(value: DevelopUgandaBroadcastStatus) {
            recording = value.recording
            if (!recording && locked) unlock()
            lockButton.text = when {
                locked -> "HOLD UNLOCK"
                recording -> "LOCK"
                else -> "LOCK • REC"
            }
            lockButton.contentDescription = when {
                locked -> "Controls locked. Hold to unlock."
                recording -> "Lock touch controls for this recording"
                else -> "Control lock is available while recording"
            }
            markButton.text = if (recording) "MARK" else "MARK • REC"
            markButton.contentDescription = if (recording) {
                "Stamp the current recording time into the take sidecar"
            } else {
                "Start recording before adding a mark"
            }
            markButton.alpha = if (recording && onMark != null) 1f else 0.55f
        }

        fun onResume() {
            if (orientationListener.canDetectOrientation()) orientationListener.enable()
        }

        fun onPause() {
            orientationListener.disable()
        }

        private fun control(label: String, action: () -> Unit): TextView = TextView(activity).apply {
            text = label
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = label
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 11f)
            DevelopUgandaBroadcastPresentation.styleButton(
                this,
                DevelopUgandaButtonWeight.TERTIARY,
                profile.chromeAccentColor,
            )
            setOnClickListener { action() }
        }

        private fun toggleClean() {
            clean = !clean
            originalVisibility.forEach { (view, visibility) ->
                if (view !== preview) view.visibility = if (clean) View.GONE else visibility
            }
            guide.visibility = if (clean) View.GONE else if (prefs.getBoolean("guides", false)) View.VISIBLE else View.GONE
            shield.visibility = if (locked) View.VISIBLE else View.GONE
            listOf(lockButton, markButton, guideButton, handButton, nightButton, dimButton).forEach {
                it.visibility = if (clean) View.GONE else View.VISIBLE
            }
            cleanButton.text = if (clean) "RESTORE" else "CLEAN"
            cleanButton.contentDescription = if (clean) "Restore camera controls" else "Hide camera overlays"
        }

        private fun lock() {
            if (!recording) {
                DevelopUgandaBroadcastPresentation.rejected(lockButton)
                return
            }
            if (locked) {
                DevelopUgandaBroadcastPresentation.rejected(lockButton)
                return
            }
            locked = true
            shield.visibility = View.VISIBLE
            lockButton.text = "HOLD UNLOCK"
            lockButton.contentDescription = "Controls locked. Hold to unlock."
        }

        private fun unlock() {
            locked = false
            shield.visibility = View.GONE
            lockButton.text = if (recording) "LOCK" else "LOCK • REC"
            lockButton.contentDescription = "Touch controls unlocked"
        }

        private fun placeRail() {
            controlRail.layoutParams = FrameLayout.LayoutParams(
                unit() * 12,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                (if (leftHanded) Gravity.START else Gravity.END) or Gravity.CENTER_VERTICAL,
            ).apply {
                marginStart = unit()
                marginEnd = unit()
                topMargin = unit() * 5
            }
        }

        private fun applyChromeColor() {
            val color = chromeContent()
            listOf(cleanButton, lockButton, markButton, guideButton, handButton, nightButton, dimButton).forEach {
                it.setTextColor(color)
            }
            guide.night = night
            guide.invalidate()
        }

        private fun applyScreenDim() {
            val params = activity.window.attributes
            // Brightness floor is deliberate: DIM saves power/heat but never
            // makes the camera preview unusable. Capture pixels are unaffected.
            params.screenBrightness = if (dimmed) 0.55f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = params
        }

        private fun chromeContent(): Int =
            if (night) DevelopUgandaFivemods8Theme.record else DevelopUgandaFivemods8Theme.content

        private fun unit(): Int = DevelopUgandaFivemods8Theme.spacingUnitPx
    }

    private class GuideView(
        context: Context,
        private val profile: DevelopUgandaModeProfile,
    ) : View(context) {
        var night: Boolean = false
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, resources.displayMetrics.density)
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            textSize = TextView(context).also {
                DevelopUgandaFivemods8Theme.applyTypeScale(it, 11f)
            }.textSize
            typeface = android.graphics.Typeface.MONOSPACE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val color = if (night) DevelopUgandaFivemods8Theme.record else DevelopUgandaFivemods8Theme.outline
            paint.color = color
            textPaint.color = color
            drawInset(canvas, 0.05f, "ACTION SAFE")
            drawInset(canvas, 0.10f, "TITLE SAFE")
            when (profile.page) {
                DevelopUgandaCameraPage.INTERVIEW -> {
                    val targetHeight = width * 9f / 16f
                    val top = (height - targetHeight) / 2f
                    canvas.drawRect(0f, top, width.toFloat(), top + targetHeight, paint)
                    canvas.drawLine(width * 0.15f, top + targetHeight * 0.30f, width * 0.85f, top + targetHeight * 0.30f, paint)
                    canvas.drawText("16:9 ARCHIVE • HEADROOM", unit().toFloat(), top + unit() * 2f, textPaint)
                }
                DevelopUgandaCameraPage.TIKTOK,
                DevelopUgandaCameraPage.STATUS -> {
                    // No current platform overlay measurements exist in this
                    // source; exposing ABSENT is safer than inventing margins.
                    canvas.drawText("PLATFORM UI COVERAGE • ABSENT", unit().toFloat(), height * 0.50f, textPaint)
                }
                else -> Unit
            }
        }

        private fun drawInset(canvas: Canvas, fraction: Float, label: String) {
            val dx = width * fraction
            val dy = height * fraction
            canvas.drawRect(RectF(dx, dy, width - dx, height - dy), paint)
            canvas.drawText(label, dx + unit(), dy + unit() * 2f, textPaint)
        }

        private fun unit(): Int = DevelopUgandaFivemods8Theme.spacingUnitPx
    }
}
