package com.sentongoharuna.pulse

import android.content.Context
import java.util.Locale
import kotlin.math.roundToInt

/**
 * V279 • ACTIVE SHOOTING INTELLIGENCE.
 *
 * This layer intentionally works from telemetry and workflow state already exposed by the
 * cumulative camera stack. It does not claim unsupported computer-vision tracking or silently
 * force Camera2 parameters. Instead it prioritizes operator actions, watches light/audio/safety/
 * continuity state, records take assessments and remembers operator intent for repeatable shoots.
 */
object DevelopUgandaV279ActiveShooting {
    private const val PREFS = "develop_uganda_v279_active_shooting_intelligence"

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stages(context: Context): List<DevelopUgandaModeStage> =
        DevelopUgandaModeProfiles.selected(context).stages

    fun stageFromDetailed(detailed: String): String = when (detailed.uppercase(Locale.US)) {
        "PREPARE" -> "READY"
        "LIGHT" -> "LIGHT"
        "SOUND" -> "SOUND"
        "CONTINUITY", "FRAME" -> "FRAME"
        "REHEARSE", "FOCUS" -> "TRACK"
        "RECORD" -> "RECORD"
        "VERIFY" -> "PROTECT"
        "REVIEW", "ORGANIZE", "DELIVER" -> "RATE"
        else -> "UNKNOWN"
    }

    private fun audioSummary(context: Context): String = try {
        DevelopUgandaV272FieldSoundContinuity.audioInputSummary(context)
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V279 AUDIO READ", error)
        "AUDIO UNKNOWN"
    }

    private fun audioPenalty(context: Context): Int? {
        val raw = audioSummary(context)
        return when {
            raw.contains("PERMISSION", true) -> 24
            raw.contains("NONE", true) -> 16
            raw.contains("UNKNOWN", true) -> null
            else -> 0
        }
    }

    private fun lightPenalty(context: Context): Int? = when {
        !DevelopUgandaV277LightingExposure.hasFreshReading(context) -> null
        DevelopUgandaV277LightingExposure.needsAttention(context) -> 22
        else -> 0
    }

    private fun safetyPenalty(context: Context): Int =
        if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(context)) 30 else 0

    private fun continuityPenalty(context: Context): Int? {
        if (!DevelopUgandaV278LiveWorkflow.hasShotMemory(context)) return 0
        val score = DevelopUgandaV278LiveWorkflow.matchScore(context)
        if (score < 0) return null
        return when {
            score >= 85 -> 0
            score >= 65 -> 5
            score >= 45 -> 10
            else -> 16
        }
    }

    fun recordingHealthScore(context: Context, readiness: Int): Int {
        if (readiness < 0) return -1
        val light = lightPenalty(context) ?: return -1
        val audio = audioPenalty(context) ?: return -1
        val continuity = continuityPenalty(context) ?: return -1
        val telemetryPenalty = light + audio + safetyPenalty(context) + continuity
        val readinessWeighted = (readiness.coerceIn(0, 100) * 0.55f).roundToInt()
        val telemetryWeighted = ((100 - telemetryPenalty.coerceAtMost(85)) * 0.45f).roundToInt()
        return (readinessWeighted + telemetryWeighted).coerceIn(0, 100)
    }

    fun healthBar(context: Context, readiness: Int): String {
        val score = recordingHealthScore(context, readiness)
        if (score < 0) return "RECORDING HEALTH • UNKNOWN • READINESS TELEMETRY UNAVAILABLE"
        val filled = (score / 10f).roundToInt().coerceIn(0, 10)
        val bar = buildString {
            repeat(10) { append(if (it < filled) "▰" else "▱") }
        }
        val state = when {
            score >= 90 -> "EXCELLENT"
            score >= 78 -> "GOOD"
            score >= 62 -> "WATCH"
            else -> "FIX FIRST"
        }
        return "RECORDING HEALTH • $bar • $score/100 • $state"
    }

    fun priorityAction(context: Context, detailedStage: String, readiness: Int): String {
        if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(context)) {
            return "NEXT BEST ACTION • PROTECT • review the interrupted/finalized recording before another critical take."
        }
        if (!DevelopUgandaV277LightingExposure.hasFreshReading(context)) {
            return "NEXT BEST ACTION • LIGHT • get a fresh exposure reading before locking the shot."
        }
        if (DevelopUgandaV277LightingExposure.needsAttention(context)) {
            return "NEXT BEST ACTION • LIGHT • protect highlights / face exposure before composition changes."
        }
        val audio = audioSummary(context)
        if (audio.contains("PERMISSION", true) || audio.contains("NONE", true) || audio.contains("UNKNOWN", true)) {
            return "NEXT BEST ACTION • SOUND • confirm the microphone route before the take."
        }
        if (DevelopUgandaV278LiveWorkflow.hasShotMemory(context) && DevelopUgandaV278LiveWorkflow.matchScore(context) < 65) {
            return "NEXT BEST ACTION • CONTINUITY • Shot Memory drift is high; restore the reference before recording."
        }
        val score = recordingHealthScore(context, readiness)
        if (score < 0) return "NEXT BEST ACTION • CHECK • recording health is unknown because readiness telemetry failed."
        return when {
            score >= 85 -> "NEXT BEST ACTION • RECORD • conditions are strong; capture the take while the setup matches."
            detailedStage.equals("RECORD", true) -> "NEXT BEST ACTION • RECORD • usable, but watch the health strip during the take."
            else -> DevelopUgandaV278LiveWorkflow.nextBestAction(context, detailedStage)
        }
    }

    fun continuityStatus(context: Context): String {
        if (!DevelopUgandaV278LiveWorkflow.hasShotMemory(context)) {
            return "CONTINUITY • NO SHOT MEMORY • save a reference when framing/exposure are right"
        }
        val match = DevelopUgandaV278LiveWorkflow.matchScore(context)
        if (match < 0) return "CONTINUITY • MATCH UNKNOWN • LIVE REFERENCE UNAVAILABLE"
        val drift = 100 - match
        val state = when {
            match >= 90 -> "MATCHED"
            match >= 75 -> "MINOR DRIFT"
            match >= 55 -> "CHECK SETUP"
            else -> "HIGH DRIFT"
        }
        return "CONTINUITY • $match% MATCH • $drift% DRIFT • $state"
    }

    private fun lightToken(context: Context): String {
        if (!DevelopUgandaV277LightingExposure.hasFreshReading(context)) return "UNKNOWN"
        return try {
            val snap = DevelopUgandaV277LightingExposure.lastSnapshot(context)
            snap.warmCool.ifBlank { "UNKNOWN" }
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V279 LIGHT READ", error)
            "UNKNOWN"
        }
    }

    /** Detects a meaningful lighting-category change between refresh cycles. */
    fun sceneChangeStatus(context: Context): String {
        val p = prefs(context)
        val now = lightToken(context)
        if (now == "UNKNOWN") return "SCENE WATCH • LIGHT UNKNOWN"
        val before = p.getString("last_light_token", null)
        if (before == null) {
            p.edit().putString("last_light_token", now).apply()
            return "SCENE WATCH • establishing lighting reference"
        }
        if (before != now && now != "UNKNOWN") {
            p.edit().putString("last_light_token", now).putLong("scene_change_at", System.currentTimeMillis()).apply()
            return "SCENE CHANGE • LIGHT SHIFT $before → $now • re-check exposure"
        }
        val changedAt = p.getLong("scene_change_at", 0L)
        if (changedAt > 0L && System.currentTimeMillis() - changedAt < 7000L) {
            return "SCENE CHANGE • recent lighting shift • confirm exposure before REC"
        }
        return "SCENE WATCH • stable"
    }

    fun subjectPriorityEnabled(context: Context): Boolean = prefs(context).getBoolean("subject_priority", false)

    fun toggleSubjectPriority(context: Context): Boolean {
        val next = !subjectPriorityEnabled(context)
        prefs(context).edit().putBoolean("subject_priority", next).apply()
        return next
    }

    fun silentAlertsEnabled(context: Context): Boolean = prefs(context).getBoolean("silent_alerts", false)

    fun toggleSilentAlerts(context: Context): Boolean {
        val next = !silentAlertsEnabled(context)
        prefs(context).edit().putBoolean("silent_alerts", next).apply()
        return next
    }

    fun assistStatus(context: Context): String {
        val priority = if (subjectPriorityEnabled(context)) "SUBJECT PRIORITY ON" else "SUBJECT PRIORITY OFF"
        val alerts = if (silentAlertsEnabled(context)) "SILENT ALERTS ON" else "SILENT ALERTS OFF"
        val motion = try {
            DevelopUgandaV273MotionShotControl.shotMode(context)
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V279 MOTION READ", error)
            "MOTION UNKNOWN"
        }
        return "ACTIVE ASSIST • $priority • $alerts • $motion"
    }

    fun takeQualityEstimate(context: Context, readiness: Int): Int {
        var score = recordingHealthScore(context, readiness)
        if (score < 0) return -1
        if (DevelopUgandaV278LiveWorkflow.hasShotMemory(context)) {
            val match = DevelopUgandaV278LiveWorkflow.matchScore(context)
            if (match < 0) return -1
            score = ((score * 0.8f) + (match * 0.2f)).roundToInt()
        }
        return score.coerceIn(0, 100)
    }

    private fun clipCount(context: Context): Int = try {
        DevelopUgandaV274MediaVaultStore.clips(context).size
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V279 CLIP COUNT", error)
        throw IllegalStateException("Media Vault clip count unavailable", error)
    }

    fun markTake(context: Context, tag: String, readiness: Int): String {
        val normalized = tag.trim().uppercase(Locale.US).ifBlank { "KEEP" }.take(18)
        val score = takeQualityEstimate(context, readiness)
        val count = clipCount(context)
        prefs(context).edit()
            .putString("last_take_tag", normalized)
            .putInt("last_take_score", score)
            .putInt("last_take_clip_count", count)
            .putLong("last_take_at", System.currentTimeMillis())
            .apply()
        val scoreText = if (score >= 0) "$score/100" else "QUALITY UNKNOWN"
        return "TAKE MARKED • $normalized • $scoreText • CLIPS $count"
    }

    fun markBestTake(context: Context, readiness: Int): String {
        val score = takeQualityEstimate(context, readiness)
        val count = clipCount(context)
        prefs(context).edit()
            .putInt("best_take_clip_count", count)
            .putInt("best_take_score", score)
            .putLong("best_take_at", System.currentTimeMillis())
            .apply()
        val scoreText = if (score >= 0) "$score/100" else "QUALITY UNKNOWN"
        return if (count > 0) "BEST TAKE • CLIP $count • $scoreText" else "BEST TAKE ARMED • record a clip, then mark it"
    }

    fun takeStatus(context: Context, readiness: Int): String {
        val p = prefs(context)
        val estimate = takeQualityEstimate(context, readiness)
        val lastTag = p.getString("last_take_tag", null)
        val lastScore = p.getInt("last_take_score", -1)
        val bestClip = p.getInt("best_take_clip_count", 0)
        val bestScore = p.getInt("best_take_score", -1)
        val live = if (estimate >= 0) "TAKE INTELLIGENCE • LIVE ESTIMATE $estimate/100"
        else "TAKE INTELLIGENCE • LIVE ESTIMATE UNKNOWN"
        val last = if (lastTag != null && lastScore >= 0) " • LAST $lastTag $lastScore" else ""
        val best = if (bestClip > 0 && bestScore >= 0) " • BEST CLIP $bestClip $bestScore" else ""
        return live + last + best
    }
}
