package com.sentongoharuna.pulse

import android.content.Context
import java.util.Locale

/**
 * V280 • SMART DIRECTOR.
 *
 * Builds a practical shot-sequence director on top of V279. It plans coverage, remembers the
 * current target shot, advances when a new Media Vault clip appears (or when the operator marks
 * SHOT DONE), and reports missing coverage. It deliberately avoids pretending that unsupported
 * scene understanding or autonomous camera movement exists.
 */
object DevelopUgandaV280SmartDirector {
    private const val PREFS = "develop_uganda_v280_smart_director"
    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val directorPresets = listOf("GENERAL", "NEWS", "TIKTOK", "PROPERTY", "CONSTRUCTION", "INTERVIEW", "CINEMA")
    fun modeStages(context: Context): List<DevelopUgandaModeStage> =
        DevelopUgandaModeProfiles.selected(context).stages

    private val presetPlans = mapOf(
        "GENERAL" to listOf("WIDE", "MEDIUM", "CLOSE", "DETAIL", "MOVING", "OUTRO"),
        "NEWS" to listOf("ESTABLISH", "REPORTER", "ACTION", "DETAIL", "INTERVIEW", "OUTRO"),
        "TIKTOK" to listOf("HOOK", "MEDIUM", "CLOSE", "DETAIL", "MOVING", "OUTRO"),
        "PROPERTY" to listOf("EXTERIOR", "ENTRY", "WIDE ROOM", "FEATURE", "WINDOW", "EXIT"),
        "CONSTRUCTION" to listOf("SITE WIDE", "FLOOR WIDE", "WORK ACTION", "DETAIL", "PROGRESS", "OUTRO"),
        "INTERVIEW" to listOf("ROOM WIDE", "SUBJECT MEDIUM", "SUBJECT CLOSE", "CUTAWAY", "DETAIL", "OUTRO"),
        "CINEMA" to listOf("ESTABLISH", "WIDE", "MEDIUM", "CLOSE", "DETAIL", "MOVING", "OUTRO")
    )

    fun directorPreset(context: Context): String = prefs(context).getString("mode", "GENERAL")
        ?.uppercase(Locale.US)?.takeIf { directorPresets.contains(it) } ?: "GENERAL"

    fun setDirectorPreset(context: Context, preset: String): String {
        val next = preset.uppercase(Locale.US).takeIf { directorPresets.contains(it) } ?: "GENERAL"
        prefs(context).edit()
            .putString("mode", next)
            .putInt("target_index", 0)
            .putInt("completed_count", 0)
            .putString("history", "")
            .putInt("last_clip_count", clipCount(context))
            .apply()
        return "DIRECTOR PRESET • $next • ${plan(context).size} SHOTS PLANNED"
    }

    fun cycleDirectorPreset(context: Context): String {
        val current = directorPresets.indexOf(directorPreset(context)).coerceAtLeast(0)
        return setDirectorPreset(context, directorPresets[(current + 1) % directorPresets.size])
    }

    fun plan(context: Context): List<String> = presetPlans[directorPreset(context)] ?: presetPlans.getValue("GENERAL")

    fun targetIndex(context: Context): Int {
        val size = plan(context).size
        return prefs(context).getInt("target_index", 0).coerceIn(0, (size - 1).coerceAtLeast(0))
    }

    fun currentShot(context: Context): String = plan(context).getOrElse(targetIndex(context)) { "SHOT UNKNOWN" }

    fun setTarget(context: Context, index: Int): String {
        val p = plan(context)
        val safe = index.coerceIn(0, (p.size - 1).coerceAtLeast(0))
        prefs(context).edit().putInt("target_index", safe).apply()
        return "TARGET SHOT • ${safe + 1}/${p.size} • ${p[safe]}"
    }

    private fun completedCount(context: Context): Int =
        prefs(context).getInt("completed_count", 0).coerceIn(0, plan(context).size)

    private fun clipCount(context: Context): Int = try {
        DevelopUgandaV274MediaVaultStore.clips(context).size
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V280 CLIP COUNT", error)
        throw IllegalStateException("Media Vault clip count unavailable", error)
    }

    private fun appendHistory(context: Context, shot: String) {
        val old = prefs(context).getString("history", "").orEmpty()
        val item = shot.uppercase(Locale.US).take(28)
        val next = if (old.isBlank()) item else "$old|$item"
        prefs(context).edit().putString("history", next.takeLast(420)).apply()
    }

    private fun completeCurrent(context: Context, source: String): String {
        val p = plan(context)
        if (p.isEmpty()) return "DIRECTOR • NO SHOT PLAN"
        val idx = targetIndex(context)
        val shot = p[idx]
        val completed = (completedCount(context) + 1).coerceAtMost(p.size)
        val nextIndex = if (completed >= p.size) idx else (idx + 1).coerceAtMost(p.lastIndex)
        appendHistory(context, "$shot@$source")
        prefs(context).edit()
            .putInt("completed_count", completed)
            .putInt("target_index", nextIndex)
            .apply()
        return if (completed >= p.size) {
            "SEQUENCE COMPLETE • ${p.size}/${p.size} • review BEST takes"
        } else {
            "SHOT COMPLETE • $shot • NEXT ${p[nextIndex]}"
        }
    }

    fun markShotDone(context: Context): String = completeCurrent(context, "MANUAL")

    /**
     * Advances at most one planned shot when Media Vault reports a newly saved clip.
     * This is real application state, not inferred computer vision.
     */
    fun reconcileNewClip(context: Context): String? {
        val p = prefs(context)
        val now = clipCount(context)
        val before = p.getInt("last_clip_count", -1)
        if (before < 0) {
            p.edit().putInt("last_clip_count", now).apply()
            return null
        }
        if (now > before) {
            p.edit().putInt("last_clip_count", now).apply()
            return completeCurrent(context, "VAULT")
        }
        if (now < before) p.edit().putInt("last_clip_count", now).apply()
        return null
    }

    fun coveragePercent(context: Context): Int {
        val total = plan(context).size.coerceAtLeast(1)
        return ((completedCount(context) * 100f) / total).toInt().coerceIn(0, 100)
    }

    fun coverageStatus(context: Context): String {
        val p = plan(context)
        val done = completedCount(context)
        val pct = coveragePercent(context)
        val filled = ((pct + 9) / 10).coerceIn(0, 10)
        val bar = buildString { repeat(10) { append(if (it < filled) "▰" else "▱") } }
        return "COVERAGE • $bar • $pct% • $done/${p.size} PLANNED SHOTS"
    }

    fun missingShots(context: Context): List<String> {
        val p = plan(context)
        val done = completedCount(context)
        return if (done >= p.size) emptyList() else p.drop(done)
    }

    fun nextShotGuidance(context: Context): String {
        val p = plan(context)
        val done = completedCount(context)
        if (done >= p.size) return "NEXT SHOT • COVERAGE COMPLETE • REVIEW → BEST → EDIT"
        val shot = currentShot(context)
        val guidance = when (shot) {
            "ESTABLISH", "EXTERIOR", "SITE WIDE", "FLOOR WIDE", "ROOM WIDE", "WIDE", "WIDE ROOM" -> "hold steady 5–8s; show geography before detail"
            "REPORTER", "SUBJECT MEDIUM", "MEDIUM", "INTERVIEW" -> "protect face exposure + audio; keep clean headroom"
            "SUBJECT CLOSE", "CLOSE" -> "lock attention on eyes/face; keep movement controlled"
            "DETAIL", "FEATURE", "WINDOW", "PROGRESS" -> "isolate one clear subject; hold 3–5s for edit handles"
            "MOVING", "WORK ACTION", "ACTION" -> "start still → move smoothly → finish still"
            "HOOK" -> "make the first 1–2 seconds visually clear and immediate"
            "ENTRY", "EXIT", "OUTRO" -> "give the sequence a clean transition and 2–3s end handle"
            else -> "capture a steady usable take with clean start/end handles"
        }
        return "NEXT SHOT • ${done + 1}/${p.size} • $shot • $guidance"
    }

    fun shotName(context: Context): String {
        val preset = directorPreset(context)
        val idx = targetIndex(context) + 1
        val shot = currentShot(context).replace(' ', '_')
        return "%s_%02d_%s".format(Locale.US, preset, idx, shot)
    }

    fun sessionStatus(context: Context): String {
        val missing = missingShots(context)
        val missingText = if (missing.isEmpty()) "NONE" else missing.take(3).joinToString(" • ")
        return "SESSION • ${directorPreset(context)} • NAME ${shotName(context)} • MISSING $missingText • V279 TAKE INTELLIGENCE PRESERVED"
    }

    fun setPreviousTarget(context: Context): String = setTarget(context, targetIndex(context) - 1)
    fun setNextTarget(context: Context): String = setTarget(context, targetIndex(context) + 1)

    fun resetSession(context: Context): String {
        prefs(context).edit()
            .putInt("target_index", 0)
            .putInt("completed_count", 0)
            .putString("history", "")
            .putInt("last_clip_count", clipCount(context))
            .apply()
        return "DIRECTOR SESSION RESET • ${directorPreset(context)} • START ${currentShot(context)}"
    }
}
