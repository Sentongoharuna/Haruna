package com.sentongoharuna.pulse

import android.content.Context
import java.util.Locale

/**
 * V280/13 PRO STABLE MASTER REFINEMENT.
 *
 * Pure coordination/presentation intelligence. It does not own CameraX,
 * microphones, recording, LUT processing, Remote Director networking or media.
 * The proven V280/12 FIX3 systems remain authoritative.
 */
object DevelopUgandaV28013StableMasterRefinement {

    private fun attentionLight(light: String): Boolean {
        val u = light.uppercase(Locale.US)
        return "FACE DARK" in u || "LIGHT CHECK" in u || "SHADOW LOW" in u || "FLICKER RISK" in u
    }

    fun productionState(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String = when {
        s.readiness < 0 || s.freeGb < 0L || s.battery < 0 ||
            s.thermal.contains("UNKNOWN", true) || s.audio.contains("UNKNOWN", true) -> "UNKNOWN • TELEMETRY FAILED"
        s.recoveryNeeded -> "HOLD • RECOVERY REVIEW"
        s.readiness < 55 -> "HOLD • CAMERA HEALTH"
        s.readiness < 75 -> "CHECK • PRE-SHOOT"
        attentionLight(s.light) -> "READY • LIGHT CHECK"
        s.lastRating == "UNRATED" && s.clipCount > 0 -> "REVIEW • RATE TAKE"
        s.storyProgress >= 100 && s.coverage >= 100 -> "READY • DELIVER"
        else -> "READY • PRODUCTION"
    }

    fun operatorCue(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String = when {
        s.readiness < 0 || s.freeGb < 0L || s.battery < 0 ||
            s.thermal.contains("UNKNOWN", true) || s.audio.contains("UNKNOWN", true) ->
            "Readiness could not be measured. Open Camera Health before treating the camera as ready."
        s.recoveryNeeded -> "Open Recovery Center before another take. Protect the last recording first."
        s.readiness < 55 -> "Do not roll yet. Fix storage, battery or thermal risk shown in PREPARE."
        s.readiness < 75 -> "Run the PREPARE checks, then return to SHOOT when readiness is green."
        attentionLight(s.light) -> "Lighting needs attention. Use Lighting Intelligence or the exposure ruler before REC."
        s.clipCount == 0 -> "Start with the opening / establishing shot, then build coverage in sequence."
        s.lastRating == "UNRATED" -> "Review and rate the last take now so Smart Story can choose the next useful shot."
        s.storyProgress < 100 -> "Next story requirement • ${s.storyShot}. Keep Scene/Take identity continuous."
        s.coverage < 100 -> "Director coverage next • ${s.directorShot}. Avoid repeating an angle already covered."
        s.deliveryQueue > 0 -> "Delivery queue has ${s.deliveryQueue} item(s). Review package state before final export."
        else -> "Coverage is complete. Move to EDIT / DELIVER and keep the Clean Master untouched."
    }

    fun operatorReason(s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String =
        "STORY ${s.storyProgress}% • COVERAGE ${s.coverage}% • ${s.clipCount} CLIPS • ${s.bestCount} BEST • LAST ${s.lastRating}"

    fun progressLine(context: Context, s: DevelopUgandaV28012ProWorkflowConsole.Snapshot): String {
        val auto = DevelopUgandaV28012ProWorkflowConsole.autoStage(s)
        val active = DevelopUgandaV28012ProWorkflowConsole.activeStage(context, s)
        fun mark(stage: Int): String = when {
            stage == active -> "●"
            stage < auto -> "✓"
            else -> "○"
        }
        return buildString {
            append(mark(1)).append(" PREP ")
            append(mark(2)).append(" SHOOT ")
            append(mark(3)).append(" DIRECT ")
            append(mark(4)).append(" REVIEW ")
            append(mark(5)).append(" DELIVER")
        }
    }

    fun masterSummary(context: Context): String {
        val s = DevelopUgandaV28012ProWorkflowConsole.snapshot(context)
        return buildString {
            append("V280/13 PRO STABLE MASTER\n")
            append(productionState(s)).append('\n')
            append("NEXT • ").append(DevelopUgandaV28012ProWorkflowConsole.nextAction(s)).append('\n')
            append("CUE • ").append(operatorCue(s)).append('\n')
            append(operatorReason(s)).append('\n')
            append("LOCKED • CameraX audio • V276 safety • V272 continuity • LUTS • rulers • Director stack • FIX3 action guards")
        }
    }
}
