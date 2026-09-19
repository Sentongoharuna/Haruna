package com.sentongoharuna.pulse

import android.app.Activity
import android.content.Context
import java.util.Locale
import kotlin.math.max

/**
 * V280/19 FIELD RELIABILITY + PRODUCTION QA MASTER.
 *
 * This layer observes the proven V280/18 stack. It does not own CameraX,
 * microphone capture, video finalization, LUT processing, Remote Director,
 * Multi-Cam or Live Cut. Its job is to make repeated field use visible:
 * session age, returns, route health, current production readiness and faults.
 */
object DevelopUgandaV28019FieldReliability {
    private const val PREFS = "develop_uganda_v28019_field_reliability"
    private const val RUNTIME_PREFS = "develop_uganda_v28012_runtime_guard"

    data class Report(
        val score: Int,
        val state: String,
        val uptime: String,
        val hubResumes: Int,
        val routeReturns: Int,
        val lastRoute: String,
        val faultState: String,
        val summary: String
    )

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun runtime(context: Context) = context.duSharedPreferences(RUNTIME_PREFS, Context.MODE_PRIVATE)

    fun beginSession(context: Context) {
        val p = prefs(context)
        p.edit()
            .putLong("session_started_ms", System.currentTimeMillis())
            .putInt("activity_launches", p.getInt("activity_launches", 0) + 1)
            .putInt("hub_resumes", 0)
            .putInt("route_returns", 0)
            .putString("last_returned_route", "NONE YET")
            .apply()
    }

    /** Call before RuntimeGuard.markReturnedToHub(), while pending_label still exists. */
    fun markHubResume(context: Context) {
        val p = prefs(context)
        val r = runtime(context)
        val pending = (r.all["pending_label"]?.toString() ?: "").trim()
        val editor = p.edit().putInt("hub_resumes", p.getInt("hub_resumes", 0) + 1)
        if (pending.isNotBlank()) {
            editor
                .putInt("route_returns", p.getInt("route_returns", 0) + 1)
                .putString("last_returned_route", pending.take(90))
                .putLong("last_route_return_ms", System.currentTimeMillis())
        }
        editor.apply()
    }

    fun resetSession(context: Context) {
        prefs(context).edit()
            .putLong("session_started_ms", System.currentTimeMillis())
            .putInt("hub_resumes", 0)
            .putInt("route_returns", 0)
            .putString("last_returned_route", "NONE YET")
            .apply()
    }

    private fun uptime(context: Context): String {
        val start = prefs(context).getLong("session_started_ms", System.currentTimeMillis())
        val total = max(0L, System.currentTimeMillis() - start) / 1000L
        val h = total / 3600L
        val m = (total % 3600L) / 60L
        val s = total % 60L
        return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    private fun sessionFault(context: Context): String {
        val start = prefs(context).getLong("session_started_ms", 0L)
        val r = runtime(context)
        val crashMs = DevelopUgandaV28012RuntimeGuard.safeLong(r.all["last_crash_ms"], 0L)
        val uiMs = DevelopUgandaV28012RuntimeGuard.safeLong(r.all["last_ui_ms"], 0L)
        return when {
            crashMs >= start && start > 0L -> {
                val route = DevelopUgandaV28012RuntimeGuard.safeString(r.all["last_crash_route"], "UNKNOWN")
                val ex = DevelopUgandaV28012RuntimeGuard.safeString(r.all["last_crash_exception"], "RuntimeError").substringAfterLast('.')
                "CRASH RECORDED • $route • $ex"
            }
            uiMs >= start && start > 0L -> {
                val area = DevelopUgandaV28012RuntimeGuard.safeString(r.all["last_ui_area"], "UI")
                val ex = DevelopUgandaV28012RuntimeGuard.safeString(r.all["last_ui_error"], "UIError")
                "ACTION FAULT BLOCKED • $area • $ex"
            }
            else -> "NO RUNTIME FAULT THIS SESSION"
        }
    }

    private fun audioOk(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): Boolean {
        val u = s.audioLock.uppercase(Locale.US)
        val input = s.audio.uppercase(Locale.US)
        return !u.contains("NOT ARMED") && !u.contains("WARN") && !u.contains("CHANGED") &&
            !u.contains("UNKNOWN") && !input.contains("UNKNOWN") &&
            !input.contains("PERMISSION") && !input.contains("NONE")
    }

    private fun thermalOk(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): Boolean {
        val u = s.thermal.uppercase(Locale.US)
        return "UNKNOWN" !in u && listOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN").none { it in u }
    }

    fun report(context: Context, s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): Report {
        val p = prefs(context)
        val fault = sessionFault(context)
        val telemetryKnown = s.readiness >= 0 && s.freeGb >= 0L && s.battery >= 0 &&
            !s.thermal.contains("UNKNOWN", true) && !s.audio.contains("UNKNOWN", true)
        val checks = listOf(
            fault.startsWith("NO RUNTIME"),
            !s.recoveryNeeded,
            s.readiness >= 55,
            s.freeGb > 2L,
            s.battery !in 0..10,
            audioOk(s),
            thermalOk(s)
        )
        val score = if (telemetryKnown) (checks.count { it } * 100) / checks.size else -1
        val state = when {
            fault.startsWith("CRASH") -> "RECOVERY CHECK"
            score < 0 -> "FIELD STATE UNKNOWN"
            score >= 90 -> "FIELD STABLE"
            score >= 70 -> "FIELD READY • WATCH"
            else -> "FIELD CHECK REQUIRED"
        }
        val resumes = p.getInt("hub_resumes", 0)
        val returns = p.getInt("route_returns", 0)
        val lastRoute = p.getString("last_returned_route", "NONE YET") ?: "NONE YET"
        val qa = if (score >= 0) "$score%" else "UNKNOWN"
        val storage = if (s.freeGb < 0L) "UNKNOWN" else if (s.freeGb > 2L) "OK" else "LOW"
        val summary = "$state • QA $qa • RETURNS $returns • AUDIO ${if (audioOk(s)) "OK" else "CHECK"} • STORAGE $storage • THERMAL ${if (thermalOk(s)) "OK" else "CHECK"}"
        return Report(score, state, uptime(context), resumes, returns, lastRoute, fault, summary)
    }

    fun fullReport(activity: Activity, s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String {
        val r = report(activity, s)
        val routes = DevelopUgandaV270Guidance.auditRoutes(activity)
        return buildString {
            append("V280/19 FIELD RELIABILITY + PRODUCTION QA\n")
            append(r.state).append(" • SCORE ").append(if (r.score >= 0) "${r.score}%" else "UNKNOWN").append('\n')
            append("SESSION • ").append(r.uptime).append(" • HUB RESUMES ").append(r.hubResumes).append(" • ROUTE RETURNS ").append(r.routeReturns).append('\n')
            append("LAST RETURN • ").append(r.lastRoute).append('\n')
            append(routes.shortLabel()).append('\n')
            append("FAULT • ").append(r.faultState).append('\n')
            append("CAMERA HEALTH • ").append(if (s.readiness >= 55 && !s.recoveryNeeded) "OK" else "CHECK").append('\n')
            append("AUDIO CONTINUITY • ").append(if (audioOk(s)) "OK" else "CHECK").append('\n')
            append("STORAGE • ").append(if (s.freeGb >= 0L) "${s.freeGb} GB FREE" else "UNKNOWN").append(" • ").append(if (s.freeGb < 0L) "UNKNOWN" else if (s.freeGb > 2L) "OK" else "LOW").append('\n')
            append("BATTERY • ").append(if (s.battery >= 0) "${s.battery}%" else "UNKNOWN").append('\n')
            append("THERMAL • ").append(s.thermal).append('\n')
            append("MEDIA • ").append(s.clipCount).append(" CLIPS • ").append(s.bestCount).append(" BEST • LAST ").append(s.lastRating).append('\n')
            append("STORY • ").append(s.storyProgress).append("% • COVERAGE ").append(s.coverage).append("% • DELIVERY ").append(s.deliveryQueue).append('\n')
            append("\nFIELD TEST STILL REQUIRED • long recording • repeated REC start/stop • mic unplug/replug • low storage • thermal load • Remote Director reconnect • Multi-Cam reconnect.")
        }
    }
}
