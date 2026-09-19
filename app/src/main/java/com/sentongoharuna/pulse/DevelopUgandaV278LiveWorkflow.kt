package com.sentongoharuna.pulse

import android.content.Context
import java.util.Locale

/**
 * V278 • LIVE WORKFLOW HOME.
 *
 * Screen-only orchestration for the first page. It reads the already-working V271–V277
 * modules and turns them into a chronological operator path. It does not fake sensor state,
 * does not overwrite masters and does not force camera settings the device has not exposed.
 * Shot Memory is a reference/match tool: it remembers a working setup and scores the current
 * setup against it so repeat shoots can be made more consistent.
 */
object DevelopUgandaV278LiveWorkflow {
    private const val PREFS = "develop_uganda_v278_live_workflow_home"

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stages(context: Context): List<DevelopUgandaModeStage> =
        DevelopUgandaModeProfiles.selected(context).stages

    fun simpleStage(detailedStage: String): String = when (detailedStage.uppercase(Locale.US)) {
        "PREPARE" -> "READY"
        "LIGHT" -> "LIGHT"
        "SOUND" -> "SOUND"
        "CONTINUITY", "FRAME", "FOCUS" -> "FRAME"
        "REHEARSE" -> "MOTION"
        "RECORD" -> "RECORD"
        "VERIFY" -> "SAFETY"
        "REVIEW", "ORGANIZE", "DELIVER" -> "REVIEW"
        else -> "UNKNOWN"
    }

    private fun audioToken(context: Context): String {
        val raw = try {
            DevelopUgandaV272FieldSoundContinuity.audioInputSummary(context)
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V278 AUDIO READ", error)
            "AUDIO UNKNOWN"
        }
        return when {
            raw.contains("UNKNOWN", true) -> "AUDIO UNKNOWN"
            raw.contains("PERMISSION", true) -> "MIC PERMISSION"
            raw.contains("NONE", true) -> "MIC CHECK"
            else -> "AUDIO ROUTE KNOWN"
        }
    }

    private fun lightToken(context: Context): String = when {
        !DevelopUgandaV277LightingExposure.hasFreshReading(context) -> "LIGHT CHECK"
        DevelopUgandaV277LightingExposure.needsAttention(context) -> "LIGHT ATTENTION"
        else -> "LIGHT GOOD"
    }

    private fun safetyToken(context: Context): String =
        if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(context)) "SAFETY CHECK" else "REC SAFE"

    fun liveStatus(context: Context, detailedStage: String, readinessScore: Int): String {
        val stage = simpleStage(detailedStage)
        val selectedMode = DevelopUgandaModeProfiles.selected(context).shortName
        val motion = currentShot(context) ?: "MOTION UNKNOWN"
        val clips = try {
            DevelopUgandaV274MediaVaultStore.clips(context).size.toString()
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V278 CLIP COUNT", error)
            "UNKNOWN"
        }
        val ready = if (readinessScore >= 0) readinessScore.coerceAtMost(100).toString() else "UNKNOWN"
        return "● $selectedMode $stage • READY $ready • ${lightToken(context)} • ${audioToken(context)} • $motion • ${safetyToken(context)} • CLIPS $clips"
    }

    fun nextBestAction(context: Context, detailedStage: String): String = when (detailedStage.uppercase(Locale.US)) {
        "PREPARE" -> "NEXT BEST ACTION • Run the ready check: battery, storage, thermal state and project identity."
        "LIGHT" -> "NEXT BEST ACTION • Fix exposure first. Check face/spot meter, highlights, shadows and flicker guidance."
        "SOUND" -> "NEXT BEST ACTION • Confirm microphone route and run Audio Check before framing the take."
        "CONTINUITY" -> "NEXT BEST ACTION • Save or match continuity before changing composition."
        "FRAME" -> "NEXT BEST ACTION • Lock aspect/LUT preview, horizon and composition before movement."
        "REHEARSE" -> "NEXT BEST ACTION • Rehearse motion; save START/END movement, focus and zoom intent."
        "FOCUS" -> "NEXT BEST ACTION • Confirm tracking / focus A-B / metering, then record."
        "RECORD" -> "NEXT BEST ACTION • Ready to record. REC stays available even if guidance is unfinished."
        "VERIFY" -> "NEXT BEST ACTION • Verify the finalized recording or review the interrupted session before another critical take."
        "REVIEW" -> "NEXT BEST ACTION • Rate the newest take BEST / KEEP / RETAKE while the shot is fresh."
        "ORGANIZE" -> "NEXT BEST ACTION • Name, protect and organize the selected masters in Media Vault."
        "DELIVER" -> "NEXT BEST ACTION • Check the delivery queue; render-required formats stay clearly marked."
        else -> "NEXT BEST ACTION • Open Camera and follow the highlighted workflow stage."
    }

    private fun currentProject(context: Context): String? = try {
        val p = context.duSharedPreferences("develop_uganda_v260_project_control_failsafe", Context.MODE_PRIVATE)
        (p.getString("project_name", "FIELD PROJECT") ?: "FIELD PROJECT").trim().ifBlank { "FIELD PROJECT" }
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V278 PROJECT READ", error)
        null
    }

    private fun currentProfile(context: Context): String? = try {
        DevelopUgandaV275ControlSurface.selectedProfile(context)
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V278 PROFILE READ", error)
        null
    }

    private fun currentOutput(context: Context): String? = try {
        DevelopUgandaV271LiveCoach.outputMode(context)
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V278 OUTPUT READ", error)
        null
    }

    private fun currentShot(context: Context): String? = try {
        DevelopUgandaV273MotionShotControl.shotMode(context)
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V278 MOTION READ", error)
        null
    }

    private fun currentLight(context: Context): String? = try {
        val s = DevelopUgandaV277LightingExposure.lastSnapshot(context)
        if (s.isFresh()) s.warmCool.takeIf { it.isNotBlank() } else null
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V278 LIGHT READ", error)
        null
    }

    fun saveShotMemory(context: Context): String {
        val profile = currentProfile(context)
        val output = currentOutput(context)
        val shot = currentShot(context)
        val light = currentLight(context)
        val audio = audioToken(context).takeUnless { it.contains("UNKNOWN") }
        val project = currentProject(context)
        val missing = listOfNotNull(
            "PROJECT".takeIf { project == null },
            "PROFILE".takeIf { profile == null },
            "OUTPUT".takeIf { output == null },
            "MOTION".takeIf { shot == null },
            "LIGHT".takeIf { light == null },
            "AUDIO".takeIf { audio == null },
        )
        if (missing.isNotEmpty()) return "SHOT MEMORY NOT SAVED • ${missing.joinToString(" / ")} UNKNOWN"
        val p = prefs(context)
        val count = p.getInt("memory_count", 0) + 1
        p.edit()
            .putBoolean("has_memory", true)
            .putInt("memory_count", count)
            .putLong("saved_at", System.currentTimeMillis())
            .putString("project", project)
            .putString("profile", profile)
            .putString("output", output)
            .putString("shot", shot)
            .putString("light", light)
            .putString("audio", audio)
            .apply()
        return "SHOT MEMORY %02d SAVED".format(Locale.US, count.coerceAtMost(99))
    }

    fun clearShotMemory(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun hasShotMemory(context: Context): Boolean = prefs(context).getBoolean("has_memory", false)

    fun matchScore(context: Context): Int {
        val p = prefs(context)
        if (!p.getBoolean("has_memory", false)) return -1
        val project = currentProject(context)
        val profile = currentProfile(context)
        val output = currentOutput(context)
        val shot = currentShot(context)
        val light = currentLight(context)
        val audio = audioToken(context).takeUnless { it.contains("UNKNOWN") }
        if (listOf(project, profile, output, shot, light, audio).any { it == null }) return -1
        var score = 0
        if (p.getString("project", "") == project) score += 10
        if (p.getString("profile", null) == profile) score += 20
        if (p.getString("output", null) == output) score += 20
        if (p.getString("shot", null) == shot) score += 20
        if (p.getString("light", null) == light) score += 15
        if (p.getString("audio", null) == audio) score += 15
        return score.coerceIn(0, 100)
    }

    fun memoryStatus(context: Context): String {
        val p = prefs(context)
        if (!p.getBoolean("has_memory", false)) return "SHOT MEMORY • no reference saved • tap MEM SAVE when a setup is right"
        val count = p.getInt("memory_count", 1)
        val profile = p.getString("profile", null) ?: "PROFILE UNKNOWN"
        val shot = p.getString("shot", null) ?: "MOTION UNKNOWN"
        val output = p.getString("output", null) ?: "OUTPUT UNKNOWN"
        val memoryCode = count.coerceIn(0, 99).toString().padStart(2, '0')
        val match = matchScore(context)
        val matchText = if (match >= 0) "$match%" else "UNKNOWN"
        return "SHOT MEMORY $memoryCode • MATCH $matchText • $profile • $shot • $output"
    }
}
