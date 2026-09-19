package com.sentongoharuna.pulse

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.StatFs
import java.util.Locale

/**
 * V280/12 PRO WORKFLOW CONSOLE + FUNCTION DEEPENING.
 *
 * A truthful first-page state engine that connects the already-preserved camera,
 * director, story, safety, sound, lighting, review and delivery systems into one
 * chronological operator workflow. It does not replace those systems or alter
 * Clean Master media; it only summarizes their real state and points the operator
 * to the correct existing tool.
 */
object DevelopUgandaV28012ProWorkflowConsole {
    const val PREFS = "develop_uganda_v28012_pro_workflow_console"
    const val STAGE_PREPARE = 1
    const val STAGE_SHOOT = 2
    const val STAGE_DIRECT = 3
    const val STAGE_REVIEW = 4
    const val STAGE_DELIVER = 5

    fun modeStages(context: Context): List<DevelopUgandaModeStage> =
        DevelopUgandaModeProfiles.selected(context).stages

    fun shellStage(stage: DevelopUgandaModeStage): Int = when (stage.id) {
        "READY", "STREAM_KEY", "CONNECTION", "SUBJECT_LOCK", "SOUND" -> STAGE_PREPARE
        "FRAME", "SEGMENT", "RECORD" -> STAGE_SHOOT
        "MARKERS" -> STAGE_DIRECT
        "REVIEW", "TRANSCRIPT", "CAPTIONS" -> STAGE_REVIEW
        "DELIVER", "DELIVERY", "SHARE" -> STAGE_DELIVER
        else -> STAGE_PREPARE
    }

    data class Snapshot(
        val project: String,
        val camera: String,
        val scene: Int,
        val take: Int,
        val profile: String,
        val outputMode: String,
        val timecode: String,
        val battery: Int,
        val freeGb: Long,
        val thermal: String,
        val thermalStatus: Int,
        val readiness: Int,
        val audio: String,
        val audioLock: String,
        val light: String,
        val safety: String,
        val recoveryNeeded: Boolean,
        val clipCount: Int,
        val bestCount: Int,
        val lastRating: String,
        val storyProgress: Int,
        val storyShot: String,
        val coverage: Int,
        val directorShot: String,
        val deliveryPreset: String,
        val deliveryQueue: Int,
    )

    private fun prefs(context: Context) =
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun stageLabel(stage: Int): String = when (stage) {
        STAGE_PREPARE -> "PREPARE"
        STAGE_SHOOT -> "SHOOT"
        STAGE_DIRECT -> "DIRECT"
        STAGE_REVIEW -> "REVIEW"
        STAGE_DELIVER -> "DELIVER"
        else -> "WORKFLOW"
    }

    fun selectStage(context: Context, stage: Int): Int {
        val safe = stage.coerceIn(STAGE_PREPARE, STAGE_DELIVER)
        prefs(context).edit().putInt("manual_stage", safe).apply()
        return safe
    }

    fun clearManualStage(context: Context) {
        prefs(context).edit().remove("manual_stage").apply()
    }

    fun manualStage(context: Context): Int? {
        val value = prefs(context).getInt("manual_stage", 0)
        return value.takeIf { it in STAGE_PREPARE..STAGE_DELIVER }
    }

    private fun projectPrefs(context: Context) =
        context.duSharedPreferences("develop_uganda_v260_project_control_failsafe", Context.MODE_PRIVATE)

    private fun timecodePrefs(context: Context) =
        context.duSharedPreferences("develop_uganda_v256_tactile_clean_cam", Context.MODE_PRIVATE)

    private fun batteryPct(context: Context): Int = try {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val value = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (value !in 0..100) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(
                context,
                "V28012 BATTERY READ",
                IllegalStateException("Battery capacity unavailable: $value"),
            )
            -1
        } else value
    } catch (error: Exception) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V28012 BATTERY READ", error)
        -1
    }

    private fun freeGb(context: Context): Long =
        StatFs(context.filesDir.absolutePath).availableBytes / 1_000_000_000L

    private fun thermal(context: Context): Pair<String, Int> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "UNKNOWN" to -1
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val status = pm.currentThermalStatus
            val label = when (status) {
                PowerManager.THERMAL_STATUS_NONE -> "COOL"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "UNKNOWN"
            }
            label to status
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V28012 THERMAL READ", error)
            "UNKNOWN" to -1
        }
    }

    private fun readiness(context: Context, battery: Int, freeGb: Long, thermalStatus: Int): Int {
        if (battery < 0 || freeGb < 0L || thermalStatus < 0) return -1
        var score = 100
        if (battery in 0..5) score -= 28 else if (battery in 6..15) score -= 14
        if (freeGb <= 1L) score -= 36 else if (freeGb <= 4L) score -= 20 else if (freeGb <= 8L) score -= 9
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            score -= when {
                thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> 42
                thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 28
                thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 10
                else -> 0
            }
        }
        if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(context)) score -= 22
        if (DevelopUgandaV277LightingExposure.needsAttention(context)) score -= 6
        return score.coerceIn(0, 100)
    }

    fun snapshot(context: Context): Snapshot = try {
        snapshotUnsafe(context)
    } catch (error: Throwable) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "PRO WORKFLOW SNAPSHOT", error)
        throw SnapshotUnavailableException(error)
    }

    class SnapshotUnavailableException(cause: Throwable) :
        IllegalStateException("The live workflow snapshot could not be read", cause)

    private fun snapshotUnsafe(context: Context): Snapshot {
        val p = projectPrefs(context)
        val projectMode = p.getBoolean("project_mode", true)
        val project = if (projectMode) p.getString("project_name", "FIELD PROJECT") ?: "FIELD PROJECT" else "PROJECT OFF"
        val camera = if (projectMode) p.getString("camera_name", "CAM A") ?: "CAM A" else "CAM"
        val scene = p.getInt("scene_number", 1).coerceIn(1, 999)
        val take = p.getInt("take_number", 1).coerceIn(1, 999)
        val battery = batteryPct(context)
        val free = freeGb(context)
        val thermal = thermal(context)
        val storyClips = DevelopUgandaV269StoryDeskStore.clipCount(context)
        val vaultClips = DevelopUgandaV274MediaVaultStore.clips(context)
        val clips = maxOf(storyClips, vaultClips.size)
        val best = maxOf(
            DevelopUgandaV269StoryDeskStore.bestCount(context),
            vaultClips.count { it.rating == "BEST" }
        )
        return Snapshot(
            project = project,
            camera = camera,
            scene = scene,
            take = take,
            profile = DevelopUgandaV275ControlSurface.selectedProfile(context),
            outputMode = DevelopUgandaV271LiveCoach.outputMode(context),
            timecode = (timecodePrefs(context).getString("timecode_mode", "REC RUN") ?: "REC RUN").uppercase(Locale.US),
            battery = battery,
            freeGb = free,
            thermal = thermal.first,
            thermalStatus = thermal.second,
            readiness = readiness(context, battery, free, thermal.second),
            audio = DevelopUgandaV272FieldSoundContinuity.audioInputSummary(context),
            audioLock = DevelopUgandaV272FieldSoundContinuity.lockedAudioInput(context),
            light = DevelopUgandaV277LightingExposure.lightStatus(context),
            safety = DevelopUgandaV276RecordingSafety.healthStrip(context),
            recoveryNeeded = DevelopUgandaV276RecordingSafety.needsRecoveryReview(context),
            clipCount = clips,
            bestCount = best,
            lastRating = DevelopUgandaV269StoryDeskStore.lastRating(context),
            storyProgress = DevelopUgandaV269StoryDeskStore.progress(context),
            storyShot = DevelopUgandaV269StoryDeskStore.activeShot(context),
            coverage = DevelopUgandaV280SmartDirector.coveragePercent(context),
            directorShot = DevelopUgandaV280SmartDirector.currentShot(context),
            deliveryPreset = DevelopUgandaV274MediaVaultStore.selectedPreset(context),
            deliveryQueue = DevelopUgandaV274MediaVaultStore.queue(context).size,
        )
    }

    fun autoStage(snapshot: Snapshot): Int = when {
        snapshot.recoveryNeeded || snapshot.readiness < 75 -> STAGE_PREPARE
        snapshot.clipCount == 0 -> STAGE_SHOOT
        snapshot.lastRating == "UNRATED" -> STAGE_REVIEW
        snapshot.storyProgress < 100 || snapshot.coverage < 100 -> STAGE_SHOOT
        else -> STAGE_DELIVER
    }

    fun activeStage(context: Context, snapshot: Snapshot = snapshot(context)): Int =
        manualStage(context) ?: autoStage(snapshot)

    fun statusRibbon(snapshot: Snapshot): String {
        val bat = if (snapshot.battery >= 0) "${snapshot.battery}%" else "UNKNOWN"
        val free = if (snapshot.freeGb >= 0L) "${snapshot.freeGb}GB" else "UNKNOWN"
        val ready = if (snapshot.readiness >= 0) "${snapshot.readiness}%" else "UNKNOWN"
        val input = snapshot.audio.removePrefix("INPUT • ").replace('\n', ' ').take(24)
        // Values such as a project name are saved user text, not a format string.
        // Passing them as arguments keeps '%' characters in that text literal.
        return String.format(
            Locale.US,
            "%s • %s • S%03d T%03d | MIC %s | BAT %s | %s | THERM %s | READY %s",
            snapshot.project,
            snapshot.camera,
            snapshot.scene,
            snapshot.take,
            input,
            bat,
            free,
            snapshot.thermal,
            ready
        )
    }

    fun readinessHeadline(snapshot: Snapshot): String = when {
        snapshot.readiness < 0 -> "READINESS UNKNOWN • CHECK TELEMETRY"
        snapshot.recoveryNeeded -> "CHECK RECOVERY BEFORE NEXT TAKE"
        snapshot.readiness < 55 -> "NOT READY • FIX CAMERA HEALTH"
        snapshot.readiness < 75 -> "CHECK BEFORE SHOOTING"
        snapshot.light.hasAttentionToken() -> "READY WITH LIGHTING CHECK"
        else -> "READY FOR PRODUCTION"
    }

    // Kept pure so tests/audits can verify the light interpretation without needing a Context.
    private fun String.hasAttentionToken(): Boolean {
        val u = uppercase(Locale.US)
        return "FACE DARK" in u || "LIGHT CHECK" in u || "SHADOW LOW" in u || "FLICKER RISK" in u
    }


    fun nextAction(snapshot: Snapshot): String = when (autoStage(snapshot)) {
        STAGE_PREPARE -> when {
            snapshot.recoveryNeeded -> "OPEN RECOVERY CENTER"
            snapshot.readiness < 75 -> "RUN PRE-SHOOT CHECK"
            else -> "PREPARE CAMERA"
        }
        STAGE_SHOOT -> "SHOOT ${if (snapshot.storyProgress < 100) snapshot.storyShot else snapshot.directorShot}"
        STAGE_REVIEW -> "REVIEW + RATE LAST TAKE"
        STAGE_DELIVER -> "EDIT / DELIVER STORY"
        else -> "OPEN WORKFLOW"
    }

    fun nextActionDetail(snapshot: Snapshot): String = when (autoStage(snapshot)) {
        STAGE_PREPARE -> "${snapshot.safety} • ${snapshot.audio} • ${snapshot.light}"
        STAGE_SHOOT -> "${snapshot.outputMode} • TC ${snapshot.timecode} • DIRECTOR NEXT ${snapshot.directorShot} • COVERAGE ${snapshot.coverage}%"
        STAGE_REVIEW -> "${snapshot.clipCount} CLIPS • ${snapshot.bestCount} BEST • LAST ${snapshot.lastRating} • MASTER PROTECTION ON"
        STAGE_DELIVER -> "STORY ${snapshot.storyProgress}% • DELIVERY ${snapshot.deliveryPreset} • QUEUE ${snapshot.deliveryQueue}"
        else -> "FUNCTION STACK UNKNOWN"
    }

    fun stageSummary(snapshot: Snapshot, stage: Int): String = when (stage) {
        STAGE_PREPARE -> "${snapshot.profile} • READY ${if (snapshot.readiness >= 0) "${snapshot.readiness}%" else "UNKNOWN"} • ${snapshot.audio.removePrefix("INPUT • ")}"
        STAGE_SHOOT -> "${snapshot.outputMode} • NEXT ${snapshot.storyShot} • TC ${snapshot.timecode}"
        STAGE_DIRECT -> "LOCAL MONITOR • REMOTE • MULTI-CAM • LIVE CUT • NEXT ${snapshot.directorShot}"
        STAGE_REVIEW -> "${snapshot.clipCount} CLIPS • ${snapshot.bestCount} BEST • LAST ${snapshot.lastRating}"
        STAGE_DELIVER -> "STORY ${snapshot.storyProgress}% • ${snapshot.deliveryPreset} • QUEUE ${snapshot.deliveryQueue}"
        else -> "UNKNOWN"
    }

    fun stageState(context: Context, snapshot: Snapshot, stage: Int): String {
        if (snapshot.readiness < 0) return "UNKNOWN"
        val active = activeStage(context, snapshot)
        return when {
            stage == active -> "ACTIVE"
            stage < autoStage(snapshot) -> "DONE"
            else -> "READY"
        }
    }

    fun verificationSummary(context: Context): String {
        val s = snapshot(context)
        return buildString {
            append("V280/12 PRO WORKFLOW CONSOLE\n")
            append("PROJECT / SCENE / TAKE • ").append(s.project).append(" • ").append(s.camera).append(" • S").append(s.scene).append(" T").append(s.take).append('\n')
            append("PREPARE • real battery/storage/thermal/audio/light/safety state\n")
            append("SHOOT • existing Clean Master / camera / Smart Director next-shot path\n")
            append("DIRECT • local monitor + V262 Remote + V263 Multi-Cam + V264 Live Cut routes\n")
            append("REVIEW • V274 Media Vault + V265 Proxy Review + Color Studio routes\n")
            append("DELIVER • Story Desk + Editor + V274 delivery queue routes\n")
            append("NO MASTER OVERWRITE • console only coordinates preserved systems")
        }
    }
}
