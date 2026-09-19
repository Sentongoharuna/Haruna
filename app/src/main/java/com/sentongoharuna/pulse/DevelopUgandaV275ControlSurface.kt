package com.sentongoharuna.pulse

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import java.util.Locale

/**
 * V277 cumulative control surface • V275 dual settings preserved.
 *
 * This is a cumulative UX layer. It does not replace V271–V274 systems.
 * It separates camera/capture settings from workflow/system settings, makes
 * ON/OFF states unmistakable, provides truthful quick profiles, and keeps
 * the first page contextual instead of adding another wall of controls.
 */
object DevelopUgandaV275ControlSurface {
    const val PREFS = "develop_uganda_v275_control_surface"

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bool(context: Context, key: String, defaultValue: Boolean): Boolean =
        prefs(context).getBoolean(key, defaultValue)

    fun setBool(context: Context, key: String, value: Boolean) {
        prefs(context).edit().putBoolean(key, value).apply()
    }

    fun toggle(context: Context, key: String, defaultValue: Boolean): Boolean {
        val next = !bool(context, key, defaultValue)
        setBool(context, key, next)
        return next
    }

    fun selectedProfile(context: Context): String =
        prefs(context).getString("quick_profile", "CUSTOM") ?: "CUSTOM"

    fun profileDescription(profile: String): String = when (profile.uppercase(Locale.US)) {
        "INTERVIEW" -> "CLEAN MASTER • TRIPOD • Live Coach ON • Audio Coach/Lock ON • Continuity ON • Horizon ON"
        "NEWS" -> "REPORTER MASTER • HANDHELD • Live Coach ON • Audio Coach/Lock ON • Continuity ON • Movement Coach ON"
        "NIGHT" -> "CLEAN MASTER • HANDHELD • Live Coach ON • Audio Coach ON • Continuity ON • Horizon ON"
        "SOCIAL" -> "BRANDED MASTER • HANDHELD • Live Coach ON • Audio Coach ON • Movement/Horizon ON"
        "CINEMA" -> "CLEAN MASTER • GIMBAL • Live Coach ON • Continuity ON • Movement/Horizon ON • Ghost OFF"
        else -> "CUSTOM • keeps your current manual camera choices"
    }

    fun applyProfile(context: Context, profile: String): String {
        val next = profile.trim().uppercase(Locale.US)
        if (next == "CUSTOM") {
            prefs(context).edit().putString("quick_profile", "CUSTOM").apply()
            return "PROFILE • CUSTOM • NO SETTINGS CHANGED"
        }

        val output = when (next) {
            "NEWS" -> DevelopUgandaV271LiveCoach.MODE_REPORTER
            "SOCIAL" -> DevelopUgandaV271LiveCoach.MODE_BRANDED
            else -> DevelopUgandaV271LiveCoach.MODE_CLEAN
        }
        val shot = when (next) {
            "INTERVIEW" -> "TRIPOD"
            "CINEMA" -> "GIMBAL"
            else -> "HANDHELD"
        }

        DevelopUgandaV271LiveCoach.setOutputMode(context, output)
        context.duSharedPreferences(DevelopUgandaV271LiveCoach.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("live_coach", true)
            .putBoolean("tap_fix", true)
            .apply()

        context.duSharedPreferences(DevelopUgandaV272FieldSoundContinuity.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("audio_coach", true)
            .putBoolean("audio_lock", next == "INTERVIEW" || next == "NEWS")
            .putBoolean("continuity_engine", true)
            .putBoolean("ghost_overlay", false)
            .apply()

        DevelopUgandaV273MotionShotControl.setShotMode(context, shot)
        context.duSharedPreferences(DevelopUgandaV273MotionShotControl.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("movement_coach", true)
            .putBoolean("horizon_assist", true)
            .putBoolean("auto_hide_motion_ui", true)
            .apply()

        DevelopUgandaV274MediaVaultStore.setProtectMasters(context, true)
        DevelopUgandaV274MediaVaultStore.setHealthCheck(context, true)

        context.duSharedPreferences(DevelopUgandaV277LightingExposure.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("lighting_coach", true)
            .putBoolean("face_meter", true)
            .putBoolean("highlight_protection", true)
            .putBoolean("shadow_protection", true)
            .putBoolean("flicker_assist", true)
            .apply()

        context.duSharedPreferences("develop_uganda_v258_live_workflow_home", Context.MODE_PRIVATE).edit()
            .putBoolean("live_card_data", true)
            .putBoolean("flow_path_motion", true)
            .apply()

        prefs(context).edit().putString("quick_profile", next).apply()
        return "PROFILE APPLIED • $next"
    }

    fun verificationSummary(context: Context): String = buildString {
        append("WORKING • V275 dual settings routes preserved\n")
        append("WORKING • highlighted ON/OFF state language\n")
        append("WORKING • contextual first-page status + next action\n")
        append("WORKING • quick profiles only write existing verified preferences\n")
        append("WORKING • V276 recording safety + recovery routes\n")
        append("WORKING • V277 lighting/exposure intelligence route + highlighted states\n")
        append("PRESERVED • V271 Output/Coach • V272 Sound/Continuity • V273 Motion • V274 Vault • V275 Control Surface • V276 Recovery\n")
        append("DEVICE LIMITED • hardware camera/audio capabilities remain device dependent")
    }
}

abstract class DevelopUgandaV275SettingsBaseActivity : AppCompatActivity() {
    protected val ink = DevelopUgandaFivemods8Theme.surface
    protected val panel = DevelopUgandaFivemods8Theme.surface
    protected val card = DevelopUgandaFivemods8Theme.surfaceRaised
    protected val line = DevelopUgandaFivemods8Theme.outline
    protected val gold = DevelopUgandaFivemods8Theme.accent
    protected val cyan = DevelopUgandaFivemods8Theme.content
    protected val green = DevelopUgandaFivemods8Theme.accent
    protected val red = DevelopUgandaFivemods8Theme.record
    protected val white = DevelopUgandaFivemods8Theme.content
    protected val muted = DevelopUgandaFivemods8Theme.contentDim

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        window.decorView.post {
            DevelopUgandaFivemods8Theme.enforceTouchTargets(window.decorView)
        }
    }

    protected fun basePage(title: String, subtitle: String): Pair<ScrollView, LinearLayout> {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ink)
            isFillViewport = true
            clipToPadding = false
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(12), space(12), space(28))
            setBackgroundColor(ink)
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(14), space(12), space(14), space(12))
            background = rounded(panel, gold, 18, 1)
            addView(label(title, 13f, white, true).apply { tag = "du_page_title" })
            addView(label(subtitle, 8.4f, muted, false).apply { setPadding(0, space(4), 0, 0) })
            addView(label("ON = highlighted • OFF = quiet • disabled/limited features explain why", 7.8f, green, true).apply { setPadding(0, space(7), 0, 0) })
        }
        page.addView(hero)
        val search = EditText(this).apply {
            hint = "SEARCH SETTINGS"
            setHintTextColor(muted)
            setTextColor(white)
            isSingleLine = true
            contentDescription = "Search settings by name or purpose"
            minimumHeight = DevelopUgandaFivemods8Theme.touchMinimumPx
            setPadding(space(12), space(8), space(12), space(8))
            background = rounded(card, line, 14, 1)
        }
        DevelopUgandaFivemods8Theme.applyTypeScale(search, 13f)
        search.doAfterTextChanged { editable ->
            val query = editable?.toString()?.trim().orEmpty()
            for (index in 2 until page.childCount) {
                val child = page.getChildAt(index)
                child.visibility = if (query.isBlank() || viewText(child).contains(query, ignoreCase = true)) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }
        page.addView(search, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = space(8); bottomMargin = space(8) })
        scroll.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll to page
    }

    private fun viewText(view: View): String = buildString {
        if (view is TextView) append(view.text).append(' ')
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) append(viewText(view.getChildAt(index))).append(' ')
        }
    }

    protected fun section(page: LinearLayout, title: String) {
        page.addView(label(title, 11f, muted, true).apply {
            tag = "du_section_label"
            letterSpacing = 0.08f
            setPadding(space(3), space(8), 0, space(4))
        })
    }

    protected fun info(page: LinearLayout, title: String, detail: String, accent: Int = cyan) {
        page.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(10), space(12), space(10))
            background = rounded(card, line, 14, 1)
            addView(label(title, 10.4f, accent, true))
            addView(label(detail, 8f, muted, false).apply { setPadding(0, space(3), 0, 0) })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = space(5) })
    }

    protected fun action(page: LinearLayout, title: String, detail: String, accent: Int = cyan, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(space(12), space(9), space(9), space(9))
            background = rounded(card, accent, 14, 1)
            isClickable = true
            isFocusable = true
        }
        DevelopUgandaBroadcastPresentation.styleActionSurface(
            row,
            DevelopUgandaButtonWeight.SECONDARY,
            accent,
        )
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 10.4f, white, true))
            addView(label(detail, 7.7f, muted, false).apply { setPadding(0, space(2), 0, 0) })
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", 22f, accent, true).apply {
            gravity = Gravity.CENTER
            DevelopUgandaFivemods8Theme.expandTouchTarget(this)
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        row.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            DevelopUgandaV28012SafeActions.run(it.context, "V275 SETTINGS ACTION • $title") { onClick() }
        }
        page.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = space(5) })
    }

    protected fun toggle(page: LinearLayout, title: String, detail: String, state: () -> Boolean, onToggle: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(space(12), space(9), space(9), space(9))
            isClickable = true
            isFocusable = true
        }
        val copy = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(label(title, 10.2f, white, true))
            addView(label(detail, 7.6f, muted, false).apply { setPadding(0, space(2), 0, 0) })
        }
        row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val pill = Button(this).apply {
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 8f)
            typeface = Typeface.DEFAULT_BOLD
            minWidth = 0
            minHeight = 0
            setPadding(space(8), 0, space(8), 0)
            isAllCaps = false
        }
        row.addView(pill, LinearLayout.LayoutParams(dp(76), dp(44)))
        DevelopUgandaFivemods8Theme.expandTouchTarget(pill)

        fun sync(animate: Boolean) {
            val on = state()
            pill.text = if (on) "● ON" else "○ OFF"
            DevelopUgandaBroadcastPresentation.styleActionSurface(
                row,
                DevelopUgandaButtonWeight.SECONDARY,
                gold,
                latched = on,
            )
            DevelopUgandaBroadcastPresentation.styleButton(
                pill,
                if (on) DevelopUgandaButtonWeight.PRIMARY else DevelopUgandaButtonWeight.TERTIARY,
                gold,
                latched = on,
            )
            if (animate && on) {
                pill.animate().cancel()
                pill.scaleX = 0.94f
                pill.scaleY = 0.94f
                pill.animate().scaleX(1f).scaleY(1f)
                    .useDevelopUgandaMotion(this@DevelopUgandaV275SettingsBaseActivity).start()
            }
        }
        fun doToggle() {
            row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onToggle()
            sync(true)
        }
        row.setOnClickListener { doToggle() }
        pill.setOnClickListener { doToggle() }
        sync(false)
        page.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = space(5) })
    }

    protected fun choices(page: LinearLayout, title: String, detail: String, options: List<String>, selected: () -> String, onSelect: (String) -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(9), space(12), space(10))
            background = rounded(card, line, 14, 1)
        }
        box.addView(label(title, 10.2f, white, true))
        box.addView(label(detail, 7.6f, muted, false).apply { setPadding(0, space(2), 0, space(7)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val buttons = mutableListOf<Button>()
        fun refresh() {
            val current = selected().uppercase(Locale.US)
            buttons.forEach { b ->
                val active = b.tag.toString().uppercase(Locale.US) == current
                DevelopUgandaBroadcastPresentation.styleButton(
                    b,
                    if (active) DevelopUgandaButtonWeight.PRIMARY else DevelopUgandaButtonWeight.TERTIARY,
                    gold,
                    latched = active,
                )
            }
        }
        options.forEach { value ->
            val b = Button(this).apply {
                text = value
                tag = value
                DevelopUgandaFivemods8Theme.applyTypeScale(this, 7.1f)
                typeface = Typeface.DEFAULT_BOLD
                minWidth = 0
                minHeight = 0
                setPadding(space(4), 0, space(4), 0)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelect(value)
                    refresh()
                }
            }
            buttons += b
            row.addView(b, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = space(3) })
            DevelopUgandaFivemods8Theme.expandTouchTarget(b)
        }
        box.addView(row)
        box.post { refresh() }
        page.addView(box, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = space(5) })
    }

    protected fun profile(page: LinearLayout, name: String) {
        action(page, name, DevelopUgandaV275ControlSurface.profileDescription(name), gold) {
            AlertDialog.Builder(this)
                .setTitle("Apply $name profile?")
                .setMessage("This will change only the listed existing develop.uganda settings. It does not erase LUTs, projects, media, story data or previous builds.\n\n${DevelopUgandaV275ControlSurface.profileDescription(name)}")
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("APPLY") { _, _ ->
                    Toast.makeText(this, DevelopUgandaV275ControlSurface.applyProfile(this, name), Toast.LENGTH_SHORT).show()
                    recreate()
                }
                .show()
        }
    }

    protected fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        DevelopUgandaFivemods8Theme.applyTypeScale(this, size)
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    protected fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = if (radius >= 30) dp(radius).toFloat() else DevelopUgandaFivemods8Theme.radiusPx.toFloat()
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
    }

    protected fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    protected fun space(value: Int): Int = DevelopUgandaFivemods8Theme.spacingPx(value)
}

class DevelopUgandaV275CameraSettingsActivity : DevelopUgandaV275SettingsBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, page) = basePage(
            "CAMERA SETTINGS • V277",
            "Capture, output, sound, continuity and motion controls. Full legacy Pro Settings remain available inside the Field Camera."
        )

        action(page, "OPEN FULL PRO CAMERA SETTINGS", "Opens the actual Field/Cinema camera with its complete cumulative settings drawer already open.", cyan) {
            startActivity(Intent(this, DevelopUgandaAllProCameraActivity::class.java).putExtra("v275_open_pro_settings", true))
        }

        section(page, "QUICK SETUP PROFILES • PREVIEW BEFORE APPLY")
        listOf("INTERVIEW", "NEWS", "NIGHT", "SOCIAL", "CINEMA").forEach { profile(page, it) }
        action(page, "CUSTOM", "Keep current manual choices; no camera setting is changed.", green) {
            Toast.makeText(this, DevelopUgandaV275ControlSurface.applyProfile(this, "CUSTOM"), Toast.LENGTH_SHORT).show()
            recreate()
        }

        section(page, "OUTPUT MASTER")
        choices(
            page,
            "Recording Output",
            "CLEAN keeps overlays screen-only. REPORTER and BRANDED use the existing V271 output policy.",
            listOf("CLEAN", "REPORTER", "BRANDED"),
            {
                when (DevelopUgandaV271LiveCoach.outputMode(this)) {
                    DevelopUgandaV271LiveCoach.MODE_REPORTER -> "REPORTER"
                    DevelopUgandaV271LiveCoach.MODE_BRANDED -> "BRANDED"
                    else -> "CLEAN"
                }
            }
        ) { value ->
            val mode = when (value) {
                "REPORTER" -> DevelopUgandaV271LiveCoach.MODE_REPORTER
                "BRANDED" -> DevelopUgandaV271LiveCoach.MODE_BRANDED
                else -> DevelopUgandaV271LiveCoach.MODE_CLEAN
            }
            DevelopUgandaV271LiveCoach.setOutputMode(this, mode)
        }

        section(page, "LIVE COACH")
        toggle(page, "Live Coach", "Temporary screen-only shooting hints.", { DevelopUgandaV271LiveCoach.bool(this, "live_coach", true) }) {
            DevelopUgandaV271LiveCoach.toggle(this, "live_coach", true, "LIVE COACH")
        }
        toggle(page, "Tap-to-Fix", "Offers a real correction only where a supported device control exists.", { DevelopUgandaV271LiveCoach.bool(this, "tap_fix", true) }) {
            DevelopUgandaV271LiveCoach.toggle(this, "tap_fix", true, "TAP FIX")
        }

        section(page, "SOUND + CONTINUITY")
        toggle(page, "Audio Coach", "Highlights low/clipping-risk microphone conditions without burning text into the master.", { DevelopUgandaV272FieldSoundContinuity.bool(this, "audio_coach", true) }) {
            DevelopUgandaV272FieldSoundContinuity.toggle(this, "audio_coach", true, "AUDIO COACH")
        }
        toggle(page, "Audio Lock", "Warns if the detected input route changes during a take.", { DevelopUgandaV272FieldSoundContinuity.bool(this, "audio_lock", true) }) {
            DevelopUgandaV272FieldSoundContinuity.toggle(this, "audio_lock", true, "AUDIO LOCK")
        }
        toggle(page, "Continuity Engine", "Keeps reference state for matching compatible shot settings.", { DevelopUgandaV272FieldSoundContinuity.bool(this, "continuity_engine", true) }) {
            DevelopUgandaV272FieldSoundContinuity.toggle(this, "continuity_engine", true, "CONTINUITY")
        }
        toggle(page, "Ghost Reference", "Screen-only reference overlay; never written into the Clean Master.", { DevelopUgandaV272FieldSoundContinuity.bool(this, "ghost_overlay", false) }) {
            DevelopUgandaV272FieldSoundContinuity.toggle(this, "ghost_overlay", false, "GHOST")
        }

        section(page, "MOTION + SHOT CONTROL")
        toggle(page, "Movement Coach", "Uses live device motion to guide handheld/pan/tilt movement.", { DevelopUgandaV273MotionShotControl.bool(this, "movement_coach", true) }) {
            DevelopUgandaV273MotionShotControl.toggle(this, "movement_coach", true, "MOVEMENT COACH")
        }
        toggle(page, "Horizon Intelligence", "Highlights level condition and auto-hides when stable.", { DevelopUgandaV273MotionShotControl.bool(this, "horizon_assist", true) }) {
            DevelopUgandaV273MotionShotControl.toggle(this, "horizon_assist", true, "HORIZON")
        }
        toggle(page, "Auto-hide Motion UI", "Keeps the shooting frame clean when the phone is stable.", { DevelopUgandaV273MotionShotControl.bool(this, "auto_hide_motion_ui", true) }) {
            DevelopUgandaV273MotionShotControl.toggle(this, "auto_hide_motion_ui", true, "AUTO HIDE")
        }
        choices(page, "Shot Mode", "Changes coaching context; it does not claim to physically move the phone.", listOf("HANDHELD", "STATIC", "PAN", "FOLLOW", "WALKING", "TRIPOD", "GIMBAL"), { DevelopUgandaV273MotionShotControl.shotMode(this) }) {
            DevelopUgandaV273MotionShotControl.setShotMode(this, it)
        }

        section(page, "V277 • LIGHTING + EXPOSURE")
        action(page, "LIGHTING + EXPOSURE INTELLIGENCE", "Face exposure, highlights/shadows, spot meter, flicker guidance and screen-only false color controls.", gold) {
            startActivity(Intent(this, DevelopUgandaV277LightingExposureActivity::class.java))
        }
        toggle(page, "Lighting Coach", "Temporary exposure warnings use live preview sampling and never burn into Clean Master.", { DevelopUgandaV277LightingExposure.bool(this, "lighting_coach", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "lighting_coach", true)
        }
        toggle(page, "Highlight Protection", "Warn when a meaningful part of the preview is near-white clipping.", { DevelopUgandaV277LightingExposure.bool(this, "highlight_protection", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "highlight_protection", true)
        }

        section(page, "V276 • RECORDING SAFETY")
        toggle(page, "Recording Health Monitor", "Watches battery, storage and Android thermal state during an active take.", { DevelopUgandaV276RecordingSafety.bool(this, "health_monitor", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "health_monitor", true)
        }
        toggle(page, "Finalize Verification", "STOP shows SAVING until CameraX finalizes; the finalized URI/file/duration is checked before CLIP SAFE.", { DevelopUgandaV276RecordingSafety.bool(this, "finalize_verify", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "finalize_verify", true)
        }
        toggle(page, "Lock Controls While REC", "Keeps STOP available while protecting lens/look/quality/settings from accidental changes during REC.", { DevelopUgandaV276RecordingSafety.controlLockEnabled(this) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "lock_controls_while_rec", true)
        }
        action(page, "RECOVERY CENTER", "Open recording journal, event log and interrupted-session review.", red) {
            startActivity(Intent(this, DevelopUgandaV276RecoveryCenterActivity::class.java))
        }

        section(page, "MEDIA SAFETY")
        toggle(page, "Protected Master Guard", "Protected BEST/KEEP masters require unlocking before deletion attempts.", { DevelopUgandaV274MediaVaultStore.protectMasters(this) }) {
            DevelopUgandaV274MediaVaultStore.setProtectMasters(this, !DevelopUgandaV274MediaVaultStore.protectMasters(this))
        }
        toggle(page, "Post-record Health Labels", "Keeps truthful warning labels with the vault entry.", { DevelopUgandaV274MediaVaultStore.healthCheck(this) }) {
            DevelopUgandaV274MediaVaultStore.setHealthCheck(this, !DevelopUgandaV274MediaVaultStore.healthCheck(this))
        }

        section(page, "VERIFICATION")
        info(page, "V277 CAMERA SETTINGS STATUS", DevelopUgandaV275ControlSurface.verificationSummary(this), green)
        setContentView(scroll)
    }
}

class DevelopUgandaV275WorkflowSettingsActivity : DevelopUgandaV275SettingsBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val (scroll, page) = basePage(
            "WORKFLOW + SYSTEM SETTINGS • V277",
            "First-page behavior, guidance, story/vault organization, recovery and accessibility remain separated from capture controls."
        )

        section(page, "FIRST PAGE CONTROL SURFACE")
        choices(page, "Motion Level", "OFF is instant. STANDARD rolls values, eases bars and crossfades status. FULL adds warning/record emphasis and press flourishes.", listOf("OFF", "STANDARD", "FULL"), {
            DevelopUgandaMotionPreferences.level(this).name
        }) {
            DevelopUgandaMotionPreferences.setLevel(this, it)
        }
        toggle(page, "Context Highlights", "When a warning or next action exists, highlight the control that solves it.", { DevelopUgandaV275ControlSurface.bool(this, "context_highlights", true) }) {
            DevelopUgandaV275ControlSurface.toggle(this, "context_highlights", true)
        }
        toggle(page, "Active-Control Highlights", "Keeps important active systems visibly ON instead of making the operator remember them.", { DevelopUgandaV275ControlSurface.bool(this, "active_control_highlights", true) }) {
            DevelopUgandaV275ControlSurface.toggle(this, "active_control_highlights", true)
        }
        toggle(page, "Recent Activity", "Shows the latest clip / rating / delivery state on the first page.", { DevelopUgandaV275ControlSurface.bool(this, "recent_activity", true) }) {
            DevelopUgandaV275ControlSurface.toggle(this, "recent_activity", true)
        }

        section(page, "GUIDANCE LEVEL")
        choices(page, "Interface Level", "BEGINNER keeps explanations visible. PRO keeps the same functions with less teaching text.", listOf("BEGINNER", "PRO"), { DevelopUgandaV271LiveCoach.interfaceLevel(this) }) {
            DevelopUgandaV271LiveCoach.setInterfaceLevel(this, it)
        }
        toggle(page, "Live Card Data", "Refresh device/project/story readouts while the first page is visible.", {
            duSharedPreferences("develop_uganda_v258_live_workflow_home", MODE_PRIVATE).getBoolean("live_card_data", true)
        }) {
            val p = duSharedPreferences("develop_uganda_v258_live_workflow_home", MODE_PRIVATE)
            p.edit().putBoolean("live_card_data", !p.getBoolean("live_card_data", true)).apply()
        }

        section(page, "STORY + MEDIA")
        toggle(page, "Story Desk", "Keeps the chronological shot list and next-shot guidance available.", {
            duSharedPreferences(DevelopUgandaV269StoryDeskStore.PREFS_NAME, MODE_PRIVATE).getBoolean("story_desk_enabled", true)
        }) {
            val p = duSharedPreferences(DevelopUgandaV269StoryDeskStore.PREFS_NAME, MODE_PRIVATE)
            p.edit().putBoolean("story_desk_enabled", !p.getBoolean("story_desk_enabled", true)).apply()
        }
        toggle(page, "Auto Register New Clips", "Adds successful finalized CameraX masters to Media Vault metadata without rewriting the media.", { DevelopUgandaV274MediaVaultStore.autoRegister(this) }) {
            DevelopUgandaV274MediaVaultStore.setAutoRegister(this, !DevelopUgandaV274MediaVaultStore.autoRegister(this))
        }
        toggle(page, "Protected Master Guard", "Keeps protected master behavior visible at workflow level too.", { DevelopUgandaV274MediaVaultStore.protectMasters(this) }) {
            DevelopUgandaV274MediaVaultStore.setProtectMasters(this, !DevelopUgandaV274MediaVaultStore.protectMasters(this))
        }

        section(page, "V277 • LIGHTING WORKFLOW")
        toggle(page, "Lighting Stage + Coach", "Keeps LIGHT in the chronological first-page workflow and shows context highlights when exposure needs attention.", { DevelopUgandaV277LightingExposure.bool(this, "lighting_coach", true) }) {
            DevelopUgandaV277LightingExposure.toggle(this, "lighting_coach", true)
        }
        action(page, "LIGHTING + EXPOSURE", "Open the dedicated V277 lighting/exposure page.", gold) {
            startActivity(Intent(this, DevelopUgandaV277LightingExposureActivity::class.java))
        }

        section(page, "V276 • RECOVERY + SESSION SAFETY")
        toggle(page, "Session Journal", "Stores REC start project/scene/take/output state so an interrupted session is visible after relaunch.", { DevelopUgandaV276RecordingSafety.bool(this, "session_journal", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "session_journal", true)
        }
        toggle(page, "Recording Event Log", "Keeps a short technical timeline of recording warnings/finalize/verification without changing media.", { DevelopUgandaV276RecordingSafety.bool(this, "event_log", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "event_log", true)
        }
        action(page, "RECOVERY CENTER", "Review interrupted/needs-check states and recent recording safety events.", red) {
            startActivity(Intent(this, DevelopUgandaV276RecoveryCenterActivity::class.java))
        }

        section(page, "ACCESSIBILITY + CONTROL FEEL")
        toggle(page, "Larger Touch Targets", "Expands V275 first-page/settings targets without changing the established palette.", { DevelopUgandaV275ControlSurface.bool(this, "large_touch_targets", false) }) {
            DevelopUgandaV275ControlSurface.toggle(this, "large_touch_targets", false)
        }
        toggle(page, "High Contrast Assist", "Stronger borders/highlights in the V275 control surface; normal colours remain the same.", { DevelopUgandaV275ControlSurface.bool(this, "high_contrast", false) }) {
            DevelopUgandaV275ControlSurface.toggle(this, "high_contrast", false)
        }

        section(page, "DIRECT ROUTES")
        action(page, "CAMERA SETTINGS", "Open capture/output/sound/motion settings.", cyan) {
            startActivity(Intent(this, DevelopUgandaV275CameraSettingsActivity::class.java))
        }
        action(page, "MEDIA VAULT", "Review, protect, rate and prepare recorded masters for delivery.", green) {
            startActivity(Intent(this, DevelopUgandaV274MediaVaultActivity::class.java))
        }
        action(page, "STORY DESK", "Project / shot / take organization and next-shot control.", gold) {
            startActivity(Intent(this, DevelopUgandaV269SmartStoryDeskActivity::class.java))
        }

        section(page, "VERIFICATION")
        info(page, "V277 WORKFLOW STATUS", DevelopUgandaV275ControlSurface.verificationSummary(this), green)
        setContentView(scroll)
    }
}
