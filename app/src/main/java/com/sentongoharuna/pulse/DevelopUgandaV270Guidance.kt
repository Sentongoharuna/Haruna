package com.sentongoharuna.pulse

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/**
 * V271 user guidance + action guard (class name retained for compatibility).
 *
 * The goal is deliberately simple: any advanced control can explain itself in
 * plain language, route buttons are checked before opening, and the launcher
 * can run a real manifest/action audit rather than pretending every feature is
 * available on every phone.
 */
object DevelopUgandaV270Guidance {
    const val PREFS_NAME = "develop_uganda_v270_guided_workflow"

    data class AuditResult(
        val available: Int,
        val total: Int,
        val missing: List<String>
    ) {
        val ok: Boolean get() = missing.isEmpty()
        fun shortLabel(): String = if (ok) "ROUTES $available/$total • VERIFIED" else "ROUTES $available/$total • CHECK ${missing.size}"
    }

    private val ink = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val line = DevelopUgandaFivemods8Theme.outline
    private val gold = DevelopUgandaFivemods8Theme.accent
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val green = DevelopUgandaFivemods8Theme.accent
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun guidedHelpEnabled(context: Context): Boolean = prefs(context).getBoolean("guided_help", true)
    fun firstUseCoachEnabled(context: Context): Boolean = prefs(context).getBoolean("first_use_coach", true)
    fun actionGuardEnabled(context: Context): Boolean = prefs(context).getBoolean("action_guard", true)

    fun toggleGuidedHelp(context: Context): String {
        val next = !guidedHelpEnabled(context)
        prefs(context).edit().putBoolean("guided_help", next).apply()
        return "GUIDED HELP ${if (next) "ON" else "OFF"}"
    }

    fun toggleFirstUseCoach(context: Context): String {
        val next = !firstUseCoachEnabled(context)
        prefs(context).edit().putBoolean("first_use_coach", next).apply()
        return "FIRST-USE COACH ${if (next) "ON" else "OFF"}"
    }

    fun toggleActionGuard(context: Context): String {
        val next = !actionGuardEnabled(context)
        prefs(context).edit().putBoolean("action_guard", next).apply()
        return "ACTION GUARD ${if (next) "ON" else "OFF"}"
    }

    fun maybeShowFirstUse(activity: Activity) {
        if (!guidedHelpEnabled(activity) || !firstUseCoachEnabled(activity)) return
        if (DevelopUgandaV271LiveCoach.interfaceLevel(activity) == "PRO") return
        val key = "coach_seen_271"
        if (prefs(activity).getBoolean(key, false)) return
        prefs(activity).edit().putBoolean(key, true).apply()
        showWorkflow(activity)
    }

    fun showWorkflow(activity: Activity) {
        val body = """
            1 • PREPARE
            Set the story/project, check battery/storage/heat/audio, then choose the recording profile.

            2 • FRAME / FOCUS / AUDIO
            Open Field Camera, frame the subject, confirm focus/exposure/audio, and use the Live Coach only when a hint is useful.

            3 • RECORD
            Choose CLEAN MASTER, REPORTER MASTER or BRANDED MASTER before REC. CLEAN keeps operator graphics off the saved video; REPORTER uses selected report tags; BRANDED adds only a small develop.uganda mark.

            4 • REVIEW
            Rate the take, check clip health, compare multi-camera/proxy material if used, and mark BEST / KEEP / RETAKE.

            5 • FINISH + DELIVER
            Apply the intended LUT/color workflow, edit/package the story, choose social/export intent, then share the finished output.

            HELP
            Touch the small ⓘ beside a card or setting to see what it does, how to use it and what result you should expect.
        """.trimIndent()
        showDialog(activity, "V273 • GUIDED WORKFLOW", body)
    }

    fun showFeature(activity: Activity, title: String, detail: String, controlHint: String = "") {
        val upper = title.uppercase(Locale.US)
        val how = when {
            upper.contains("CLEAN MASTER") -> "Keep it ON when you want a clean saved video. The operator HUD can remain visible on the phone while the recorded master stays free of those graphics."
            upper.contains("TIMECODE") -> "Open the row, choose REC RUN, FREE RUN or TIME OF DAY, then use that same mode across the cameras that need to be aligned."
            upper.contains("PROJECT") || upper.contains("SLATE") -> "Enter the project/camera name first, then set Scene and Take before recording. Do this before the first clip so filenames and metadata stay organized."
            upper.contains("STORY") || upper.contains("SHOT LIST") -> "Define the story, choose the NEXT shot, record it, then rate the take. The checklist is meant to guide the shoot in order rather than replace your judgement."
            upper.contains("AUDIO") || upper.contains("MIC") -> "Confirm the active microphone before recording. Watch the meter/guard for clipping and change the input or level option only if the phone exposes that control."
            upper.contains("WAVE") || upper.contains("FALSE") || upper.contains("VECTOR") || upper.contains("PARADE") -> "Turn the monitor ON only when judging exposure or color. These are screen-only monitoring aids and do not need to be visible for every shot."
            upper.contains("FOCUS") || upper.contains("TRACK") -> "Select or tap the subject, confirm focus lock/peaking, then record. Use slower tracking for interviews and faster response for moving subjects."
            upper.contains("EXPOSURE") || upper.contains("SHUTTER") || upper.contains("ISO") || upper.contains("WHITE BALANCE") || upper.contains("WB") -> "Set exposure in this order: frame rate → shutter/angle → ISO/exposure → white balance. Lock only after the image is where you want it."
            upper.contains("LUT") || upper.contains("COLOR") -> "Use the LUT/look as a monitoring or finishing choice. Keep the original master preserved unless the selected mode explicitly says the look is recorded."
            upper.contains("PROXY") || upper.contains("REVIEW") -> "Record the full master first. Let the proxy/review copy be created afterward, then use it for fast transfer, sync and director review."
            upper.contains("REMOTE") || upper.contains("MULTI-CAM") || upper.contains("DIRECTOR") -> "Connect cameras on the same Wi‑Fi/hotspot, confirm each camera status, then control only the selected/group cameras. The local master continues on each camera."
            upper.contains("HEALTH") || upper.contains("THERMAL") || upper.contains("STORAGE") -> "Run this before a long take. Fix low storage, critical battery or high thermal warnings before pressing REC."
            upper.contains("MASTER OUTPUT") || upper.contains("QUALITY") || upper.contains("BITRATE") || upper.contains("CODEC") -> "Choose the recording format before the take. Higher quality usually needs more storage, bandwidth and thermal headroom."
            else -> controlHint.ifBlank { "Tap the row/card to use this function. If it has a moving ON/OFF switch, enable only when needed; if it expands, choose one of the shown options." }
        }
        val result = when {
            upper.contains("CLEAN MASTER") -> "RESULT • A clean master clip with operator-only monitoring kept off the saved picture."
            upper.contains("STORY") || upper.contains("SHOT LIST") -> "RESULT • A shoot that is easier to follow, review and package because the required shots and takes are organized."
            upper.contains("PROXY") -> "RESULT • Faster multi-camera review while the full-quality masters remain on the camera phones."
            upper.contains("REMOTE") || upper.contains("MULTI-CAM") -> "RESULT • One director device can coordinate compatible camera actions without pretending to provide hardware genlock."
            upper.contains("HEALTH") || upper.contains("THERMAL") || upper.contains("STORAGE") -> "RESULT • Fewer avoidable recording failures caused by storage, heat, power or unsupported device modes."
            else -> "RESULT • ${detail.trim().ifBlank { "The selected function changes only the part of the workflow described by this control." }}"
        }
        showDialog(
            activity,
            "ⓘ $title",
            "WHAT IT DOES\n${detail.trim()}\n\nHOW TO USE\n$how\n\n$result"
        )
    }

    fun safeOpen(activity: Activity, destination: Class<out Activity>): Boolean {
        DevelopUgandaV28012RuntimeGuard.install(activity)
        DevelopUgandaV28012RuntimeGuard.markRoute(activity, destination)
        val intent = Intent(activity, destination)
        if (actionGuardEnabled(activity)) {
            try {
                activity.packageManager.getActivityInfo(ComponentName(activity, destination), 0)
            } catch (_: PackageManager.NameNotFoundException) {
                DevelopUgandaV28012RuntimeGuard.clearPendingRoute(activity)
                Toast.makeText(activity, "THIS ACTION IS NOT AVAILABLE IN THIS BUILD", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return try {
            activity.startActivity(intent)
            true
        } catch (t: Throwable) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(activity, "OPEN ${destination.simpleName}", t)
            DevelopUgandaV28012RuntimeGuard.clearPendingRoute(activity)
            Toast.makeText(activity, "COULD NOT OPEN ${destination.simpleName}: ${t.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            false
        }
    }

    fun auditRoutes(activity: Activity): AuditResult {
        val routes = listOf(
            DevelopUgandaAllProCameraActivity::class.java,
            DevelopUgandaSocialMediaCameraActivity::class.java,
            DevelopUgandaLiveActivity::class.java,
            DevelopUgandaV269SmartStoryDeskActivity::class.java,
            DevelopUgandaColorStudioActivity::class.java,
            DevelopUgandaEditorActivity::class.java,
            DevelopUgandaStoryPackagesActivity::class.java,
            DevelopUgandaV274MediaVaultActivity::class.java,
            DevelopUgandaV275CameraSettingsActivity::class.java,
            DevelopUgandaV275WorkflowSettingsActivity::class.java,
            DevelopUgandaV276RecoveryCenterActivity::class.java,
            DevelopUgandaV277LightingExposureActivity::class.java,
            DevelopUgandaBrandMetadataActivity::class.java,
            DevelopUgandaV264LiveCutDirectorActivity::class.java,
            DevelopUgandaV265ProxySyncReviewActivity::class.java,
            DevelopUgandaCameraHealthActivity::class.java,
            DevelopUgandaNewsroomActivity::class.java,
            DevelopUgandaFocusAssistCameraActivity::class.java,
            DevelopUgandaMeteringLockCameraActivity::class.java,
            DevelopUgandaHorizonCameraActivity::class.java,
            DevelopUgandaSteadyShotCameraActivity::class.java,
            DevelopUgandaNightIntelligenceCameraActivity::class.java,
            DevelopUgandaAudioGuardCameraActivity::class.java,
            DevelopUgandaVerifiedCameraActivity::class.java,
            DevelopUgandaThermalSafeCameraActivity::class.java,
            DevelopUgandaModeSignatureCameraActivity::class.java,
            DevelopUgandaAutoDirectorCameraActivity::class.java,
            DevelopUgandaPhotoProCameraActivity::class.java,
            DevelopUgandaBuildingPhotoCameraActivity::class.java,
            DevelopUgandaPeoplePhotoCameraActivity::class.java,
            DevelopUgandaNightPhotoCameraActivity::class.java,
            DevelopUgandaVerifiedPhotoCameraActivity::class.java
        )
        val missing = routes.mapNotNull { cls ->
            try {
                activity.packageManager.getActivityInfo(ComponentName(activity, cls), 0)
                null
            } catch (_: PackageManager.NameNotFoundException) {
                cls.simpleName
            }
        }
        return AuditResult(routes.size - missing.size, routes.size, missing)
    }

    fun showAudit(activity: Activity) {
        val r = auditRoutes(activity)
        val state = if (r.ok) {
            "PASS • ${r.available}/${r.total} launcher workflow destinations are present in this installed build.\n\nSettings controls are direct code paths, so missing method references are compile failures rather than silent buttons. Hardware-dependent results still need the real phone/camera/network to support them."
        } else {
            "CHECK • ${r.available}/${r.total} workflow destinations are present.\n\nMissing:\n${r.missing.joinToString("\n") { "• $it" }}"
        }
        showDialog(activity, "V277 • FUNCTION AUDIT", state)
    }

    private fun showDialog(activity: Activity, title: String, bodyText: String) {
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 10))
            background = rounded(panel, line, dp(activity, 18), dp(activity, 1))
        }
        shell.addView(TextView(activity).apply {
            text = title
            textSize = 13f
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
        })
        shell.addView(TextView(activity).apply {
            text = bodyText
            textSize = 9f
            setTextColor(muted)
            setPadding(0, dp(activity, 8), 0, 0)
            setLineSpacing(0f, 1.08f)
        })
        shell.addView(TextView(activity).apply {
            text = "develop.uganda • V277 • tap DONE to return"
            textSize = 7.4f
            gravity = Gravity.END
            setTextColor(cyan)
            setPadding(0, dp(activity, 10), 0, 0)
        })
        AlertDialog.Builder(activity)
            .setView(shell)
            .setPositiveButton("DONE", null)
            .show()
    }

    private fun rounded(fill: Int, stroke: Int, radiusPx: Int, widthPx: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radiusPx.toFloat()
            setStroke(widthPx.coerceAtLeast(1), stroke)
        }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
}
