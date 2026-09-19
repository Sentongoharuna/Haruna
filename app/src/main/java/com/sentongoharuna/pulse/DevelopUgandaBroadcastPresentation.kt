package com.sentongoharuna.pulse

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.TypefaceSpan
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import java.util.Locale

enum class DevelopUgandaButtonWeight { PRIMARY, SECONDARY, TERTIARY, DESTRUCTIVE }

/**
 * Shared presentation primitives for workflow pages. This object changes view
 * chrome only; it never receives a PreviewView or encoder surface.
 */
object DevelopUgandaBroadcastPresentation {
    private val numericToken = Regex("(?<![A-Za-z])[-+]?\\d+(?:[.,:]\\d+)*(?:%|GB|MB|KB|K|FPS|DBFS|DB|MS|S|M|KM/H)?", RegexOption.IGNORE_CASE)
    private val unitToken = Regex("(?<=\\d)(%|GB|MB|KB|K|FPS|DBFS|DB|MS|MIN|S|M|KM/H)\\b", RegexOption.IGNORE_CASE)

    fun motionLevel(context: Context): DevelopUgandaMotionLevel {
        if (systemReduceMotion(context)) return DevelopUgandaMotionLevel.OFF
        return DevelopUgandaMotionPreferences.level(context)
    }

    fun setMotionLevel(context: Context, level: DevelopUgandaMotionLevel) {
        DevelopUgandaMotionPreferences.setLevel(context, level)
    }

    fun decorate(root: View, profile: DevelopUgandaModeProfile? = null) {
        val clickables = mutableListOf<TextView>()
        walk(root) { view ->
            view.elevation = 0f
            view.stateListAnimator = null
            if (view is TextView) {
                view.setShadowLayer(0f, 0f, 0f, DevelopUgandaFivemods8Theme.transparent)
                applyTypography(view)
                if ((view.isClickable || view is Button) && view !is EditText) clickables += view
                if (view.contentDescription.isNullOrBlank()) {
                    view.contentDescription = view.text?.toString()?.replace('\n', ' ')?.trim()
                }
            }
        }
        val primary = clickables.firstOrNull { it.tag == "du_primary_action" }
            ?: clickables.firstOrNull { isPrimaryVerb(it.text?.toString().orEmpty()) }
        clickables.forEach { view ->
            val text = view.text?.toString().orEmpty()
            val weight = when {
                view === primary -> DevelopUgandaButtonWeight.PRIMARY
                isDestructive(text) -> DevelopUgandaButtonWeight.DESTRUCTIVE
                looksLikeStage(text) -> DevelopUgandaButtonWeight.TERTIARY
                else -> DevelopUgandaButtonWeight.SECONDARY
            }
            styleButton(view, weight, profile?.chromeAccentColor ?: DevelopUgandaFivemods8Theme.accent)
        }
        DevelopUgandaFivemods8Theme.enforceTouchTargets(root)
    }

    /** Flat hardware chrome only. Safe for camera pages: no layout or preview properties change. */
    fun flattenChrome(root: View) {
        walk(root) { view ->
            view.elevation = 0f
            view.translationZ = 0f
            view.stateListAnimator = null
            (view.background as? GradientDrawable)?.cornerRadius =
                DevelopUgandaFivemods8Theme.radiusPx.toFloat()
            if (view is TextView) {
                view.setShadowLayer(0f, 0f, 0f, DevelopUgandaFivemods8Theme.transparent)
            }
        }
    }

    /** Typography-only pass for ordinary pages; it never replaces controls. */
    fun applyReadability(root: View) {
        flattenChrome(root)
        walk(root) { view ->
            if (view is TextView) {
                applyTypography(view)
            }
        }
    }

    fun styleButton(
        view: TextView,
        weight: DevelopUgandaButtonWeight,
        modeAccent: Int = DevelopUgandaFivemods8Theme.accent,
        disabledReason: String? = null,
        latched: Boolean = false,
        busy: Boolean = false,
    ) {
        val content = when (weight) {
            DevelopUgandaButtonWeight.PRIMARY,
            DevelopUgandaButtonWeight.DESTRUCTIVE -> DevelopUgandaFivemods8Theme.surface
            DevelopUgandaButtonWeight.SECONDARY -> DevelopUgandaFivemods8Theme.content
            DevelopUgandaButtonWeight.TERTIARY -> DevelopUgandaFivemods8Theme.contentDim
        }
        styleActionSurface(view, weight, modeAccent, latched)
        val resolvedContent = if (latched) DevelopUgandaFivemods8Theme.surface else content
        view.setTextColor(if (view.isEnabled) resolvedContent else DevelopUgandaFivemods8Theme.contentDim)
        val pad = DevelopUgandaFivemods8Theme.spacingUnitPx
        view.setPadding(pad, maxOf(1, pad / 2), pad, maxOf(1, pad / 2))
        view.isAllCaps = false
        val baseDescription = view.text?.toString()?.replace('\n', ' ')?.trim().orEmpty()
        view.contentDescription = buildString {
            append(baseDescription)
            if (latched) append(". On")
            if (busy) append(". Working")
            disabledReason?.takeIf { it.isNotBlank() }?.let { append(". Disabled: ").append(it) }
        }
        if (!view.isEnabled && !disabledReason.isNullOrBlank() && !baseDescription.contains(disabledReason, true)) {
            view.text = "$baseDescription\n$disabledReason"
        }
    }

    fun styleActionSurface(
        view: View,
        weight: DevelopUgandaButtonWeight,
        modeAccent: Int = DevelopUgandaFivemods8Theme.accent,
        latched: Boolean = false,
    ) {
        val fill = when (weight) {
            DevelopUgandaButtonWeight.PRIMARY -> modeAccent
            DevelopUgandaButtonWeight.SECONDARY -> DevelopUgandaFivemods8Theme.surfaceRaised
            DevelopUgandaButtonWeight.TERTIARY -> DevelopUgandaFivemods8Theme.transparent
            DevelopUgandaButtonWeight.DESTRUCTIVE -> DevelopUgandaFivemods8Theme.record
        }
        // ON cards remain dark with an accent border. Only the compact ON pill
        // (a TextView/Button) receives the accent fill, so white titles never
        // sit on gold.
        val resolvedFill = if (latched && view is TextView) modeAccent else fill
        val border = when (weight) {
            DevelopUgandaButtonWeight.TERTIARY -> DevelopUgandaFivemods8Theme.transparent
            else -> if (latched) modeAccent else DevelopUgandaFivemods8Theme.outline
        }
        view.background = RippleDrawable(
            ColorStateList.valueOf(ColorUtils.setAlphaComponent(DevelopUgandaFivemods8Theme.content, 56)),
            rounded(resolvedFill, border),
            null,
        )
        DevelopUgandaFivemods8Theme.expandTouchTarget(view)
        view.stateListAnimator = null
        view.elevation = 0f
        view.setOnTouchListener { pressedView, event ->
            val motion = motionLevel(pressedView.context)
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    pressedView.alpha = 0.78f
                    if (motion == DevelopUgandaMotionLevel.FULL) {
                        pressedView.scaleX = 0.98f
                        pressedView.scaleY = 0.98f
                    }
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    if (motion == DevelopUgandaMotionLevel.OFF) {
                        pressedView.animate().cancel()
                        pressedView.alpha = 1f
                        pressedView.scaleX = 1f
                        pressedView.scaleY = 1f
                    } else {
                        pressedView.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .useDevelopUgandaMotion(pressedView.context).start()
                    }
                }
            }
            false
        }
    }

    fun thresholdColor(value: Double?, cautionBelow: Double, alarmBelow: Double): Int = when {
        value == null -> DevelopUgandaFivemods8Theme.contentDim
        value <= alarmBelow -> DevelopUgandaFivemods8Theme.record
        value <= cautionBelow -> DevelopUgandaFivemods8Theme.warning
        else -> DevelopUgandaFivemods8Theme.content
    }

    fun rejected(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    private fun applyTypography(view: TextView) {
        val raw = view.text?.toString().orEmpty()
        val numericMatches = numericToken.findAll(raw).toList()
        val telemetry = view.tag == "du_telemetry_value" ||
            view is DevelopUgandaLiveMetricTextView ||
            (numericMatches.size == 1 && numericMatches.single().value == raw.trim())
        val requested = when {
            view.tag == "du_record_tally" -> 40f
            view.tag == "du_hero_value" -> 22f
            view is Button -> 11f
            view.tag == "du_page_title" -> 13f
            view.tag == "du_section_label" -> 11f
            raw.startsWith("develop.uganda", ignoreCase = true) -> 13f
            telemetry -> 13f
            view.typeface?.isBold == true && raw.length < 54 -> 13f
            else -> 11f
        }
        DevelopUgandaFivemods8Theme.applyTypeScale(view, requested)
        view.includeFontPadding = false
        view.setLineSpacing(0f, 1f)
        if (view.tag == "du_section_label") {
            view.text = raw.uppercase(Locale.US)
            view.letterSpacing = 0.08f
            view.setTextColor(DevelopUgandaFivemods8Theme.contentDim)
        }
        if (numericMatches.isNotEmpty()) {
            val styled = SpannableString(view.text)
            if (telemetry) {
                view.typeface = Typeface.create(
                    Typeface.MONOSPACE,
                    if (view.typeface?.isBold == true) Typeface.BOLD else Typeface.NORMAL,
                )
            } else {
                numericMatches.forEach { match ->
                    styled.setSpan(
                        TypefaceSpan("monospace"),
                        match.range.first,
                        match.range.last + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
            unitToken.findAll(raw).forEach { match ->
                styled.setSpan(RelativeSizeSpan(0.72f), match.range.first, match.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                styled.setSpan(ForegroundColorSpan(DevelopUgandaFivemods8Theme.contentDim), match.range.first, match.range.last + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            view.text = styled
        }
    }

    private fun systemReduceMotion(context: Context): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    private fun rounded(fill: Int, border: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = DevelopUgandaFivemods8Theme.radiusPx.toFloat()
        setColor(fill)
        if (border != DevelopUgandaFivemods8Theme.transparent) {
            setStroke(maxOf(1, DevelopUgandaFivemods8Theme.spacingUnitPx / 8), border)
        }
    }

    private fun walk(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) walk(view.getChildAt(index), action)
    }

    private fun isPrimaryVerb(text: String): Boolean {
        val first = text.trim().substringBefore(' ').substringBefore('\n').uppercase(Locale.US)
        return first in setOf("START", "OPEN", "LOCK", "SEND", "VERIFY", "SHOOT", "RECORD")
    }

    private fun isDestructive(text: String): Boolean {
        val upper = text.uppercase(Locale.US)
        return upper.contains("DELETE") || upper.contains("CLEAR") || upper.contains("STOP") || upper.contains("DISCARD")
    }

    private fun looksLikeStage(text: String): Boolean {
        val upper = text.uppercase(Locale.US)
        return Regex("^\\d+\\s+(READY|LIGHT|SOUND|FRAME|TRACK|RECORD|PROTECT|RATE|PREPARE|SHOOT|DIRECT|REVIEW|DELIVER)").containsMatchIn(upper)
    }
}
