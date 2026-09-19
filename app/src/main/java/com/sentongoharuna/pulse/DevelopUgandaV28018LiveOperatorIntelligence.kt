package com.sentongoharuna.pulse

import java.util.Locale

/**
 * V280/18 LIVE OPERATOR INTELLIGENCE + CONTROL POLISH.
 *
 * Read-only production intelligence layered on the proven V280/13 + V280/12 FIX3
 * stable stack. It does not own CameraX, recording, microphones, LUT processing,
 * Remote Director networking, Multi-Cam, Live Cut or media finalization.
 */
object DevelopUgandaV28018LiveOperatorIntelligence {

    private fun lightNeedsAttention(value: String): Boolean {
        val u = value.uppercase(Locale.US)
        return "FACE DARK" in u || "LIGHT CHECK" in u || "SHADOW LOW" in u ||
            "FLICKER RISK" in u || "CLIP" in u
    }

    private fun thermalRisk(value: String): Boolean {
        val u = value.uppercase(Locale.US)
        return "SEVERE" in u || "CRITICAL" in u || "EMERGENCY" in u || "SHUTDOWN" in u
    }

    fun sessionBar(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String {
        val bat = if (s.battery >= 0) "${s.battery}%" else "UNKNOWN"
        val input = s.audio.removePrefix("INPUT • ").replace('\n', ' ').take(22)
        val scene = s.scene.coerceIn(0, 999).toString().padStart(3, '0')
        val take = s.take.coerceIn(0, 999).toString().padStart(3, '0')
        val free = if (s.freeGb >= 0L) "${s.freeGb}GB" else "UNKNOWN"
        val ready = if (s.readiness >= 0) "${s.readiness}%" else "UNKNOWN"
        return "${s.project} • ${s.camera} • S$scene/T$take • MIC $input • BAT $bat • $free • READY $ready"
    }

    fun alert(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String = when {
        s.readiness < 0 || s.freeGb < 0L || s.battery < 0 ||
            s.thermal.contains("UNKNOWN", true) || s.audio.contains("UNKNOWN", true) ->
            "UNKNOWN • CAMERA HEALTH TELEMETRY FAILED"
        s.recoveryNeeded -> "HOLD • RECOVERY REVIEW REQUIRED BEFORE ANOTHER TAKE"
        s.readiness < 55 -> "HOLD • CAMERA HEALTH NOT READY"
        s.freeGb <= 2L -> "HOLD • STORAGE CRITICAL • FREE SPACE ${s.freeGb}GB"
        s.battery in 0..10 -> "CHECK • BATTERY ${s.battery}% • PREPARE POWER"
        thermalRisk(s.thermal) -> "HOLD • THERMAL ${s.thermal} • PROTECT THE PHONE"
        lightNeedsAttention(s.light) -> "CHECK • LIGHT / EXPOSURE NEEDS ATTENTION"
        s.audioLock.uppercase(Locale.US).contains("NOT ARMED") -> "CHECK • AUDIO ROUTE NOT LOCKED"
        s.lastRating == "UNRATED" && s.clipCount > 0 -> "REVIEW • RATE LAST TAKE BEFORE NEXT STORY DECISION"
        s.storyProgress < 100 -> "SHOOT • STORY NEXT ${s.storyShot}"
        s.coverage < 100 -> "DIRECT • COVERAGE NEXT ${s.directorShot}"
        s.deliveryQueue > 0 -> "DELIVER • ${s.deliveryQueue} ITEM(S) WAITING"
        else -> "READY • SYSTEMS NOMINAL • FOLLOW NEXT ACTION"
    }

    fun directorBrief(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String =
        "DIRECTOR • COVERAGE ${s.coverage}% • NEXT ${s.directorShot} • STORY ${s.storyProgress}% • ${s.storyShot}"

    fun continuityBrief(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String =
        "CONTINUITY • SCENE ${s.scene} TAKE ${s.take} • ${s.clipCount} CLIPS • ${s.bestCount} BEST • LAST ${s.lastRating}"

    fun operatorPriority(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String = when {
        s.readiness < 0 -> "PRIORITY UNKNOWN • CHECK CAMERA HEALTH"
        s.recoveryNeeded || s.readiness < 55 -> "PRIORITY 1 • PROTECT / PREPARE"
        s.lastRating == "UNRATED" && s.clipCount > 0 -> "PRIORITY 2 • REVIEW LAST TAKE"
        s.storyProgress < 100 || s.coverage < 100 -> "PRIORITY 3 • CAPTURE MISSING COVERAGE"
        s.deliveryQueue > 0 -> "PRIORITY 4 • REVIEW DELIVERY QUEUE"
        else -> "PRIORITY • READY FOR DELIVERY"
    }

    fun masterSummary(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String = buildString {
        append("V280/18 LIVE OPERATOR MASTER\n")
        append(alert(s)).append('\n')
        append(operatorPriority(s)).append('\n')
        append(directorBrief(s)).append('\n')
        append(continuityBrief(s)).append('\n')
        append("LOCKED CORE • CameraX audio • V276 safety • V272 continuity • LUTS • rulers • Remote Director • Multi-Cam • Live Cut • Proxy Review • FIX3 guards")
    }
}
