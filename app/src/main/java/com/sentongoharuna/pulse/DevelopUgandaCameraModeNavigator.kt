package com.sentongoharuna.pulse

import android.app.Activity
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast

/**
 * The one routing table for the five camera faces. The hub uses the same
 * guarded activity targets through its route cards; the strip makes those
 * destinations reachable without returning to the Workflow/Home screen.
 */
object DevelopUgandaCameraModeRoutes {
    fun all(): List<DevelopUgandaModeProfile> = DevelopUgandaModeProfiles.all()

    fun launch(activity: Activity, destination: DevelopUgandaModeProfile) {
        // Use the exact guarded Activity routing used by Workflow/Home cards.
        // This is called only after the departing CameraX session has had time
        // to release its hardware surface.
        DevelopUgandaModeProfiles.rememberSelected(activity, destination.page)
        if (DevelopUgandaV270Guidance.safeOpen(activity, destination.destination)) {
            activity.finish()
        }
    }
}

/**
 * The shared, icon-only selector. It is appended as the final child of every
 * camera deck. Its lower safe-area padding is part of the strip, so no screen
 * furniture can render below the five mode glyphs.
 */
object DevelopUgandaCameraModeNavigator {

    // CameraX releases the use cases synchronously, but a physical device can
    // keep its previous surface for a little longer. This bounded pause gives
    // the next mode a clean preview surface without changing either camera
    // engine or any recording profile.
    private const val CAMERA_HANDOFF_SETTLE_MS = 850L

    fun create(
        activity: Activity,
        current: DevelopUgandaCameraPage,
        dp: (Int) -> Int,
        canNavigate: () -> Boolean,
        releaseCamera: () -> Unit
    ): View {
        var handoffInFlight = false
        val outer =
            FrameLayout(activity).apply {
                tag = "fivemods_8_icon_strip"
                setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceScrim(240))
                // Main and Live's existing decks retain a 10dp lower pad. This
                // cancels that legacy pad while this strip supplies the real
                // system-bar inset below its own icon row.
                translationY = dp(10).toFloat()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setOnApplyWindowInsetsListener { view, insets ->
                        view.setPadding(
                            0,
                            0,
                            0,
                            insets.getInsets(WindowInsets.Type.systemBars()).bottom
                        )
                        insets
                    }
                } else {
                    @Suppress("DEPRECATION")
                    setOnApplyWindowInsetsListener { view, insets ->
                        view.setPadding(0, 0, 0, insets.systemWindowInsetBottom)
                        insets
                    }
                }
            }

        outer.addView(
            View(activity).apply {
                setBackgroundColor(DevelopUgandaFivemods8Theme.outlineScrim(53))
            },
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(1),
                Gravity.TOP
            )
        )

        val strip =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(4), dp(8), dp(4))
            }
        outer.addView(
            strip,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dp(56),
                Gravity.BOTTOM
            )
        )

        DevelopUgandaCameraModeRoutes.all().forEach { destination ->
            val active = destination.page == current
            val tab =
                DevelopUgandaCameraModeGlyphView(
                    activity = activity,
                    glyph = destination.glyph,
                    active = active,
                    accent = destination.chromeAccentColor,
                    code = DevelopUgandaFivemods12Identity.forPage(destination.page).code,
                ).apply {
                    contentDescription = DevelopUgandaFivemods12Identity.forPage(destination.page).badge
                    isClickable = !active
                    isFocusable = !active
                    if (!active) {
                        setOnClickListener {
                            if (handoffInFlight) return@setOnClickListener
                            if (!canNavigate()) {
                                Toast.makeText(
                                    activity,
                                    "Stop recording before changing camera mode",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@setOnClickListener
                            }

                            // CameraX releases the use cases immediately, but
                            // the physical camera/device surface closes a beat
                            // later. Starting the next mode in the same tap can
                            // leave its PreviewView holding the last frame.
                            handoffInFlight = true
                            isEnabled = false
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            DevelopUgandaFiveModesDiagnostics.markSwitchRequested(
                                activity,
                                current,
                                destination.page
                            )
                            releaseCamera()
                            outer.postDelayed(
                                {
                                    if (!activity.isFinishing && !activity.isDestroyed) {
                                        DevelopUgandaCameraModeRoutes.launch(activity, destination)
                                    }
                                },
                                CAMERA_HANDOFF_SETTLE_MS
                            )
                        }
                    }
                }
            strip.addView(
                tab,
                LinearLayout.LayoutParams(
                    0,
                    dp(48),
                    1f
                ).apply {
                    if (destination.page != DevelopUgandaCameraPage.MAIN) {
                        marginStart = dp(8)
                    }
                }
            )
        }

        return outer
    }
}

/** Drawn locally so the five glyphs do not add an icon-library dependency. */
private class DevelopUgandaCameraModeGlyphView(
    activity: Activity,
    private val glyph: DevelopUgandaModeGlyph,
    private val active: Boolean,
    private val accent: Int,
    private val code: String,
) : View(activity) {

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val unit = minOf(width, height).toFloat()
        if (unit <= 0f) return

        val color =
            if (active) {
                accent
            } else {
                DevelopUgandaFivemods8Theme.contentDim
            }
        val body = RectF(
            width * 0.13f,
            height * 0.13f,
            width * 0.87f,
            height * 0.87f
        )
        backgroundPaint.color =
            if (active) {
                DevelopUgandaFivemods8Theme.surfaceRaised
            } else {
                DevelopUgandaFivemods8Theme.transparent
            }
        canvas.drawRoundRect(body, unit * 0.20f, unit * 0.20f, backgroundPaint)

        iconPaint.color = color
        iconPaint.strokeWidth = unit * 0.065f
        // Selection changes colour/backing, never glyph weight.
        iconPaint.style = Paint.Style.STROKE

        when (glyph) {
            DevelopUgandaModeGlyph.MAIN_CAMERA -> drawCamera(canvas, unit, false)
            DevelopUgandaModeGlyph.LIVE_CAMERA -> drawCamera(canvas, unit, true)
            DevelopUgandaModeGlyph.TIKTOK_NOTE -> drawMusicNote(canvas, unit)
            DevelopUgandaModeGlyph.STATUS_BUBBLE -> drawStatusBubble(canvas, unit)
            DevelopUgandaModeGlyph.INTERVIEW_MIC -> drawMicrophone(canvas, unit)
        }

        if (active) {
            backgroundPaint.color = accent
            canvas.drawCircle(
                width * 0.5f,
                height * 0.86f,
                unit * 0.042f,
                backgroundPaint
            )
        }
        codePaint.color = color
        codePaint.textSize = unit * 0.13f
        canvas.drawText(code, width * 0.5f, height * 0.98f, codePaint)
    }

    private fun drawCamera(canvas: Canvas, u: Float, live: Boolean) {
        val body = RectF(width * 0.25f, height * 0.36f, width * 0.75f, height * 0.68f)
        canvas.drawRoundRect(body, u * 0.07f, u * 0.07f, iconPaint)
        canvas.drawCircle(width * 0.5f, height * 0.52f, u * 0.105f, iconPaint)
        iconPaint.color =
            if (active) {
                accent
            } else {
                DevelopUgandaFivemods8Theme.contentDim
            }
        iconPaint.style = Paint.Style.STROKE
        canvas.drawRoundRect(
            RectF(width * 0.36f, height * 0.28f, width * 0.52f, height * 0.38f),
            u * 0.025f,
            u * 0.025f,
            iconPaint
        )
        if (live) {
            canvas.drawArc(
                RectF(width * 0.62f, height * 0.22f, width * 0.94f, height * 0.54f),
                -65f,
                130f,
                false,
                iconPaint
            )
            canvas.drawArc(
                RectF(width * 0.69f, height * 0.29f, width * 0.87f, height * 0.47f),
                -65f,
                130f,
                false,
                iconPaint
            )
        }
    }

    private fun drawMusicNote(canvas: Canvas, u: Float) {
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = u * 0.075f
        canvas.drawLine(width * 0.60f, height * 0.25f, width * 0.60f, height * 0.66f, iconPaint)
        canvas.drawLine(width * 0.60f, height * 0.25f, width * 0.79f, height * 0.31f, iconPaint)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawOval(
            RectF(width * 0.27f, height * 0.59f, width * 0.51f, height * 0.78f),
            iconPaint
        )
    }

    private fun drawStatusBubble(canvas: Canvas, u: Float) {
        val bubble = RectF(width * 0.24f, height * 0.28f, width * 0.76f, height * 0.66f)
        canvas.drawRoundRect(bubble, u * 0.14f, u * 0.14f, iconPaint)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawLine(width * 0.39f, height * 0.66f, width * 0.34f, height * 0.78f, iconPaint)
        canvas.drawLine(width * 0.34f, height * 0.78f, width * 0.49f, height * 0.69f, iconPaint)
        iconPaint.strokeWidth = u * 0.048f
        canvas.drawLine(width * 0.35f, height * 0.47f, width * 0.65f, height * 0.47f, iconPaint)
    }

    private fun drawMicrophone(canvas: Canvas, u: Float) {
        val mic = RectF(width * 0.40f, height * 0.22f, width * 0.60f, height * 0.57f)
        canvas.drawRoundRect(mic, u * 0.12f, u * 0.12f, iconPaint)
        iconPaint.style = Paint.Style.STROKE
        canvas.drawArc(
            RectF(width * 0.31f, height * 0.37f, width * 0.69f, height * 0.73f),
            0f,
            180f,
            false,
            iconPaint
        )
        canvas.drawLine(width * 0.50f, height * 0.73f, width * 0.50f, height * 0.82f, iconPaint)
        canvas.drawLine(width * 0.38f, height * 0.82f, width * 0.62f, height * 0.82f, iconPaint)
    }
}
