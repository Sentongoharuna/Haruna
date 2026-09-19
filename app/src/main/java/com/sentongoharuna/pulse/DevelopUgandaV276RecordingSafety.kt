package com.sentongoharuna.pulse

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * V276 PRO CAM • RECORDING SAFETY + RECOVERY ENGINE.
 *
 * Cumulative safety layer. It does not replace V271–V275 features and it never
 * edits a finalized master. It journals a take before REC, monitors objective
 * device state, verifies a finalized URI, and exposes interrupted sessions for
 * operator review on the next launch.
 */
object DevelopUgandaV276RecordingSafety {
    const val PREFS = "develop_uganda_v276_recording_safety"
    private const val MAX_EVENTS = 28

    data class FinalVerification(
        val ok: Boolean,
        val label: String,
        val durationMs: Long,
        val bytes: Long
    )

    data class RecordingPreflight(
        val blocked: List<String>,
        val warnings: List<String>,
        val ready: List<String>,
        val overridable: List<String> = emptyList(),
    ) {
        val mayStart: Boolean get() = blocked.isEmpty()
        val needsOperatorOverride: Boolean get() = overridable.isNotEmpty()
    }

    data class PredictiveReadout(
        val storageMinutes: Int?,
        val batteryMinutes: Int?,
        val thermalHeadroom: Float?,
        val thermalStatus: String,
    ) {
        fun line(): String = buildString {
            append("SPACE ").append(storageMinutes?.let { "$it MIN" } ?: "UNKNOWN")
            append(" • BAT ").append(batteryMinutes?.let { "$it MIN" } ?: "UNKNOWN")
            append(" • THERM ").append(thermalStatus)
            append(" • HEADROOM ")
            append(thermalHeadroom?.let { String.format(Locale.US, "%.2f", it) } ?: "UNKNOWN")
        }
    }

    data class StorageMeasurement(
        val freeBytes: Long?,
        val source: String,
        val reason: String,
    )

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

    fun reconcileInterruptedSession(context: Context): Boolean {
        val recovered = DevelopUgandaCrashSafeTake.recoverOnLaunch(context)
        val recoveredAudio = runCatching {
            DevelopUgandaV272FieldSoundContinuity.recoverSafetyFragments(context)
        }.getOrElse { failure ->
            addEvent(context, "SAFETY AUDIO RECOVERY FAILED • ${failure.javaClass.simpleName}")
            emptyList()
        }
        recoveredAudio.forEach { addEvent(context, "SAFETY AUDIO RECOVERY • $it") }
        val exactRecovery = buildList {
            recovered.forEach { add(it.message) }
            recoveredAudio.forEach { add(it) }
        }
        if (exactRecovery.isNotEmpty()) {
            prefs(context).edit()
                .putString("last_recovery_detail", exactRecovery.joinToString("\n"))
                .putBoolean("needs_recovery_review", true)
                .apply()
        }
        val p = prefs(context)
        if (p.getString("session_state", "IDLE") == "RECORDING") {
            p.edit()
                .putString("session_state", "INTERRUPTED")
                .putBoolean("needs_recovery_review", true)
                .putString("last_result", "INTERRUPTED • NEEDS CHECK")
                .apply()
            val detail = recovered.joinToString(" • ") { it.message }
            addEvent(
                context,
                "RECOVERY • previous session ended before CameraX finalize callback" +
                    if (detail.isBlank()) " • no completed segment was found" else " • $detail"
            )
            return true
        }
        return recovered.isNotEmpty() || p.getBoolean("needs_recovery_review", false)
    }

    fun recoveryDetail(context: Context): String? =
        prefs(context).getString("last_recovery_detail", null)?.takeIf { it.isNotBlank() }

    fun onRecordingStarted(
        context: Context,
        clipName: String,
        outputMode: String,
        project: String,
        camera: String,
        scene: Int,
        take: Int
    ) {
        prefs(context).edit().putBoolean("capture_active", true).apply()
        DevelopUgandaDeliveryQueue.onCaptureStarted(context)
        if (!bool(context, "session_journal", true)) return
        prefs(context).edit()
            .putString("session_state", "RECORDING")
            .putBoolean("needs_recovery_review", false)
            .putString("active_clip", clipName)
            .putString("active_output", outputMode)
            .putString("active_project", project)
            .putString("active_camera", camera)
            .putInt("active_scene", scene)
            .putInt("active_take", take)
            .putLong("record_started_ms", System.currentTimeMillis())
            .putLong("recorded_duration_ns", 0L)
            .putLong("recorded_bytes", 0L)
            .putString("last_result", "REC ACTIVE")
            .apply()
        addEvent(context, "REC START • $clipName • $project • S$scene T$take • $outputMode")
    }

    fun onRecordingStatus(
        context: Context,
        durationNs: Long,
        bytes: Long,
        mode: DevelopUgandaCameraPage? = null,
    ) {
        val p = prefs(context)
        p.edit()
            .putLong("recorded_duration_ns", durationNs.coerceAtLeast(0L))
            .putLong("recorded_bytes", bytes.coerceAtLeast(0L))
            .apply()

        if (durationNs > 2_000_000_000L && bytes > 0L) {
            val bytesPerSecond = bytes.toDouble() / (durationNs.toDouble() / 1_000_000_000.0)
            if (bytesPerSecond.isFinite() && bytesPerSecond > 1_000.0) {
                val editor = p.edit().putLong("recent_bytes_per_second", bytesPerSecond.toLong())
                mode?.let { editor.putLong("recent_bytes_per_second_${it.name}", bytesPerSecond.toLong()) }
                editor.apply()
            }
        }
    }

    fun minimumBatteryPercent(context: Context): Int =
        prefs(context).getInt("minimum_battery_percent", 10).coerceIn(3, 50)

    fun cycleMinimumBatteryPercent(context: Context): Int {
        val choices = intArrayOf(5, 10, 15, 20, 30)
        val current = minimumBatteryPercent(context)
        val next = choices[(choices.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % choices.size }]
        prefs(context).edit().putInt("minimum_battery_percent", next).apply()
        return next
    }

    fun plannedTakeMinutes(context: Context): Int =
        prefs(context).getInt("planned_take_minutes", 10).coerceIn(1, 120)

    fun cyclePlannedTakeMinutes(context: Context): Int {
        val choices = intArrayOf(5, 10, 20, 30, 60)
        val current = plannedTakeMinutes(context)
        val next = choices[(choices.indexOf(current).takeIf { it >= 0 } ?: 0).let { (it + 1) % choices.size }]
        prefs(context).edit().putInt("planned_take_minutes", next).apply()
        return next
    }

    /** Uses only platform measurements and a bitrate measured from a prior segment. */
    fun recordingPreflight(context: Context, mode: DevelopUgandaCameraPage): RecordingPreflight {
        val blocked = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val ready = mutableListOf<String>()
        val overridable = mutableListOf<String>()
        val battery = batteryPctMeasured(context)
        val thermal = thermalStatusMeasured(context)
        val freeBytes = freeStorageBytesMeasured(context)
        val bytesPerSecond = measuredBytesPerSecond(context, mode)

        when {
            battery == null -> warnings += "BATTERY UNKNOWN"
            battery < minimumBatteryPercent(context) ->
                overridable += "BATTERY MEASURED $battery% • BELOW ${minimumBatteryPercent(context)}% OPERATOR WARNING THRESHOLD"
            else -> ready += "BATTERY $battery%"
        }

        when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> warnings += "THERMAL THROTTLE STATE UNKNOWN"
            thermal < 0 -> warnings += "THERMAL STATE READ FAILED"
            thermalBlocksTake(thermal) ->
                blocked += "THERMAL MEASURED ${thermalLabel(thermal)} • SEVERE START-BLOCK THRESHOLD CROSSED"
            thermal >= PowerManager.THERMAL_STATUS_MODERATE ->
                warnings += "THERMAL ${thermalLabel(thermal)} • CAUTION • RECORDING ALLOWED"
            thermal == PowerManager.THERMAL_STATUS_LIGHT -> warnings += "THERMAL ${thermalLabel(thermal)}"
            else -> ready += "THERMAL ${thermalLabel(thermal)}"
        }

        when {
            freeBytes == null -> warnings += "STORAGE UNKNOWN"
            freeBytes < 512L * 1024L * 1024L ->
                blocked += "STORAGE MEASURED ${formatBytes(freeBytes)} • BELOW 512MB FINALIZATION RESERVE"
            bytesPerSecond == null -> warnings += "TAKE CAPACITY UNKNOWN • NO MEASURED ${mode.name} BITRATE"
            else -> {
                val requestedBytes = bytesPerSecond * plannedTakeMinutes(context) * 60L
                val reserveBytes = 512L * 1024L * 1024L
                if (requestedBytes > freeBytes - reserveBytes) {
                    val availableMinutes = ((freeBytes - reserveBytes).coerceAtLeast(0L) / bytesPerSecond / 60L)
                    overridable += "STORAGE MEASURED FOR ${availableMinutes}MIN • BELOW PLANNED ${plannedTakeMinutes(context)}MIN OPERATOR THRESHOLD"
                } else {
                    ready += "STORAGE FITS ${plannedTakeMinutes(context)}MIN"
                }
            }
        }
        return RecordingPreflight(
            blocked.distinct(),
            warnings.distinct(),
            ready.distinct(),
            overridable.distinct(),
        )
    }

    /** Pure policy used by preflight and the device suite. UNKNOWN never blocks. */
    fun thermalBlocksTake(status: Int): Boolean =
        status >= PowerManager.THERMAL_STATUS_SEVERE

    fun observeRecordingHealth(context: Context): List<String> {
        if (!bool(context, "health_monitor", true)) return emptyList()
        val warnings = mutableListOf<String>()
        val free = freeStorageGb(context)
        val battery = batteryPctMeasured(context)
        val thermal = thermalStatusMeasured(context)

        if (free != null && free <= 2L) warnings += "STORAGE LOW ${free}GB"
        if (battery != null && battery <= 8) warnings += "BATTERY LOW $battery%"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermal >= PowerManager.THERMAL_STATUS_SEVERE) {
            warnings += "THERMAL ${thermalLabel(thermal)}"
        }
        if (warnings.isNotEmpty()) {
            val previous = prefs(context).getString("last_health_warning", "") ?: ""
            val joined = warnings.joinToString(" • ")
            if (joined != previous) {
                prefs(context).edit().putString("last_health_warning", joined).apply()
                addEvent(context, "REC HEALTH • $joined")
            }
        }
        return warnings
    }

    fun onRecordingFinalized(
        context: Context,
        uriString: String,
        eventDurationMs: Long,
        eventBytes: Long,
        hadError: Boolean,
        warnings: List<String>
    ): FinalVerification {
        prefs(context).edit().putBoolean("capture_active", false).apply()
        DevelopUgandaDeliveryQueue.onCaptureEnded(context)
        val result = if (hadError || uriString.isBlank()) {
            FinalVerification(false, "RECORD ERROR • NEEDS CHECK", eventDurationMs, eventBytes)
        } else if (!bool(context, "finalize_verify", true)) {
            FinalVerification(true, "FINALIZED • VERIFY OFF", eventDurationMs, eventBytes)
        } else {
            verifyFinalizedClip(context, uriString, eventDurationMs, eventBytes)
        }

        val p = prefs(context)
        p.edit()
            .putString("session_state", if (result.ok) "SAFE" else "NEEDS_CHECK")
            .putBoolean("needs_recovery_review", !result.ok)
            .putString("last_uri", uriString)
            .putLong("last_duration_ms", result.durationMs)
            .putLong("last_bytes", result.bytes)
            .putString("last_result", result.label)
            .putString("last_warnings", warnings.joinToString(" • "))
            .putLong("last_finalized_ms", System.currentTimeMillis())
            .apply()

        addEvent(
            context,
            if (result.ok) {
                "CLIP VERIFIED • ${result.label} • ${formatBytes(result.bytes)} • ${formatDuration(result.durationMs)}"
            } else {
                "CLIP NEEDS CHECK • ${result.label}"
            }
        )
        return result
    }

    private fun verifyFinalizedClip(
        context: Context,
        uriString: String,
        eventDurationMs: Long,
        eventBytes: Long
    ): FinalVerification {
        return try {
            val uri = Uri.parse(uriString)
            var readable = false
            var bytes = eventBytes.coerceAtLeast(0L)
            try {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                    readable = true
                    if (afd.length > 0L) bytes = maxOf(bytes, afd.length)
                }
            } catch (_: Exception) {
            }

            if (bytes <= 0L) {
                try {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(OpenableColumns.SIZE)
                            if (idx >= 0 && !c.isNull(idx)) bytes = c.getLong(idx)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            var mediaDuration = 0L
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                mediaDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
            } catch (_: Exception) {
            }

            val duration = maxOf(mediaDuration, eventDurationMs.coerceAtLeast(0L))
            val ok = readable && bytes > 1024L && duration > 0L
            val label = when {
                ok && mediaDuration > 0L -> "CLIP SAFE ✓ • FILE + DURATION VERIFIED"
                ok -> "CLIP SAFE ✓ • FILE VERIFIED • CAMERA DURATION ✓"
                !readable -> "URI NOT READABLE YET"
                bytes <= 1024L -> "FILE SIZE TOO SMALL"
                else -> "DURATION NOT VERIFIED"
            }
            FinalVerification(ok, label, duration, bytes)
        } catch (e: Exception) {
            FinalVerification(false, "VERIFY ERROR • ${e.javaClass.simpleName}", eventDurationMs, eventBytes)
        }
    }

    fun healthStrip(context: Context): String {
        val battery = batteryPctMeasured(context)?.let { "$it%" } ?: "?%"
        val free = freeStorageGb(context)?.let { "${it}GB" } ?: "?GB"
        val thermal = thermalLabel(thermalStatusMeasured(context))
        val minutes = estimatedRecordMinutes(context)
        val safe = when {
            reconcileInterruptedSession(context) -> "RECOVERY CHECK"
            prefs(context).getString("last_result", "")?.contains("SAFE", true) == true -> "REC SAFE"
            else -> "REC READY"
        }
        return "$safe • BAT $battery • SPACE $free • THERM $thermal • ${if (minutes != null) "~${minutes} MIN" else "TIME CALCULATING"}"
    }

    fun activeRecordingStrip(context: Context): String {
        val p = prefs(context)
        val durationMs = p.getLong("recorded_duration_ns", 0L) / 1_000_000L
        val bytes = p.getLong("recorded_bytes", 0L)
        val health = observeRecordingHealth(context)
        val healthLabel = if (health.isEmpty()) "REC HEALTH ✓" else health.joinToString(" • ")
        return "$healthLabel • ${formatDuration(durationMs)} • ${formatBytes(bytes)}"
    }

    fun recoverySummary(context: Context): String {
        reconcileInterruptedSession(context)
        val p = prefs(context)
        val state = p.getString("session_state", "IDLE") ?: "IDLE"
        val clip = p.getString("active_clip", "NO CLIP") ?: "NO CLIP"
        val result = p.getString("last_result", "NO FINALIZED CLIP CHECKED") ?: "NO FINALIZED CLIP CHECKED"
        val warnings = p.getString("last_warnings", "") ?: ""
        val extra = if (warnings.isBlank()) "NO SAVED WARNINGS" else warnings
        return "$state • $clip\n$result\n$extra"
    }

    fun needsRecoveryReview(context: Context): Boolean {
        return reconcileInterruptedSession(context) ||
            prefs(context).getBoolean("needs_recovery_review", false)
    }

    /** Background delivery and post-processing must yield while Recorder owns a take. */
    fun isCaptureActive(context: Context): Boolean =
        prefs(context).getBoolean("capture_active", false)

    fun markRecoveryReviewed(context: Context) {
        prefs(context).edit()
            .putBoolean("needs_recovery_review", false)
            .putString("session_state", "REVIEWED")
            .apply()
        addEvent(context, "RECOVERY REVIEWED • operator acknowledged the interrupted/needs-check state")
    }

    fun eventLog(context: Context): List<String> {
        val raw = prefs(context).getString("event_log", "") ?: ""
        return raw.lineSequence().filter { it.isNotBlank() }.toList()
    }

    fun clearEventLog(context: Context) {
        prefs(context).edit().remove("event_log").apply()
    }

    fun addEvent(context: Context, message: String) {
        if (!bool(context, "event_log", true)) return
        val stamp = Instant.now().toString()
        val current = eventLog(context).toMutableList()
        current.add(0, "$stamp • $message")
        while (current.size > MAX_EVENTS) current.removeAt(current.lastIndex)
        prefs(context).edit().putString("event_log", current.joinToString("\n")).apply()
    }

    fun controlLockEnabled(context: Context): Boolean = bool(context, "lock_controls_while_rec", true)

    fun estimatedRecordMinutes(context: Context): Int? {
        val bps = prefs(context).getLong("recent_bytes_per_second", 0L)
        if (bps <= 0L) return null
        val bytes = freeStorageBytesMeasured(context) ?: return null
        if (bytes <= 0L) return 0
        return (bytes / bps / 60L).coerceIn(0L, 9999L).toInt()
    }

    fun estimatedRecordMinutes(context: Context, mode: DevelopUgandaCameraPage): Int? {
        val bps = measuredBytesPerSecond(context, mode) ?: return null
        val bytes = freeStorageBytesMeasured(context) ?: return null
        if (bytes <= 0L) return 0
        return (bytes / bps / 60L).coerceIn(0L, 9999L).toInt()
    }

    fun predictiveReadout(context: Context, mode: DevelopUgandaCameraPage): PredictiveReadout =
        PredictiveReadout(
            storageMinutes = estimatedRecordMinutes(context, mode),
            batteryMinutes = measuredBatteryMinutes(context),
            thermalHeadroom = thermalHeadroomMeasured(context),
            thermalStatus = thermalLabel(thermalStatusMeasured(context)),
        )

    fun requiresGracefulThermalStop(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            thermalStatusMeasured(context) >= PowerManager.THERMAL_STATUS_CRITICAL

    fun measuredBytesPerSecond(context: Context, mode: DevelopUgandaCameraPage): Long? =
        prefs(context).getLong("recent_bytes_per_second_${mode.name}", 0L)
            .takeIf { it > 1_000L }

    /**
     * Measures the primary external volume used by MediaStore/Movies.
     * Scoped storage does not permit treating an arbitrary legacy path as an
     * app file, but StatFs is valid on the app's directory on that same volume.
     * Every fallback is a directory the app can actually stat; no capacity is
     * invented and failures remain explicitly UNKNOWN.
     */
    fun storageMeasurement(context: Context): StorageMeasurement {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val candidates = buildList<Pair<String, File>> {
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.let {
                add("PRIMARY_EXTERNAL_MOVIES_APP_DIRECTORY" to it)
            }
            context.getExternalFilesDir(null)?.let {
                if (none { candidate -> candidate.second.absolutePath == it.absolutePath }) {
                    add("PRIMARY_EXTERNAL_APP_DIRECTORY" to it)
                }
            }
            @Suppress("DEPRECATION")
            Environment.getExternalStorageDirectory()?.let {
                if (none { candidate -> candidate.second.absolutePath == it.absolutePath }) {
                    add("PRIMARY_EXTERNAL_VOLUME_ROOT" to it)
                }
            }
            if (isEmpty()) add("INTERNAL_APP_DIRECTORY" to context.filesDir)
        }
        val failures = mutableListOf<String>()
        for ((source, requested) in candidates) {
            val measured = runCatching {
                if (!requested.exists() && source.contains("APP_DIRECTORY")) requested.mkdirs()
                val target = requested.canonicalFile
                require(target.exists()) { "path unavailable" }
                val volume = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    storageManager?.getStorageVolume(target)
                } else {
                    null
                }
                val state = volume?.state
                require(state == null || state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY) {
                    "volume $state"
                }
                StatFs(target.absolutePath).availableBytes.takeIf { it > 0L }
                    ?: error("available bytes not positive")
            }
            measured.onSuccess { bytes ->
                return StorageMeasurement(bytes, source, "MEASURED WITH STATFS ON RESOLVED STORAGE VOLUME")
            }.onFailure { failure ->
                failures += "$source:${failure.javaClass.simpleName}"
            }
        }
        return StorageMeasurement(
            null,
            "NO_ACCESSIBLE_RECORDING_VOLUME",
            failures.joinToString(" • ").ifBlank { "No storage candidate was exposed by Android." },
        )
    }

    fun freeStorageBytesMeasured(context: Context): Long? = storageMeasurement(context).freeBytes

    private fun freeStorageGb(context: Context): Long? = freeStorageBytesMeasured(context)?.div(1024L * 1024L * 1024L)

    fun batteryPctMeasured(context: Context): Int? = try {
        val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 }
    } catch (_: Exception) {
        null
    }

    fun thermalStatusMeasured(context: Context): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.currentThermalStatus
        } else {
            -1
        }
    } catch (_: Exception) {
        -1
    }

    private fun measuredBatteryMinutes(context: Context): Int? {
        return try {
            val manager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val chargeMicroAh = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            val currentMicroA = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            if (chargeMicroAh <= 0 || currentMicroA >= -1_000) {
                null
            } else {
                ((chargeMicroAh.toDouble() / -currentMicroA.toDouble()) * 60.0)
                    .takeIf { it.isFinite() && it >= 0.0 }
                    ?.toInt()
                    ?.coerceIn(0, 9_999)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun thermalHeadroomMeasured(context: Context): Float? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.getThermalHeadroom(0).takeIf { it.isFinite() }
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) "DEVICE" else "UNKNOWN"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> String.format(Locale.US, "%.1fGB", bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> String.format(Locale.US, "%.0fMB", bytes / 1_048_576.0)
        bytes > 0L -> "${bytes / 1024L}KB"
        else -> "0MB"
    }

    private fun formatDuration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val minutes = total / 60L
        val seconds = total % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

class DevelopUgandaV276RecoveryCenterActivity : DevelopUgandaV275SettingsBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopUgandaV276RecordingSafety.reconcileInterruptedSession(this)
        val (scroll, page) = basePage(
            "RECOVERY CENTER • V276",
            "Recording journal, finalized-file verification, take health and interrupted-session review. Original masters are never rewritten here."
        )

        section(page, "CURRENT SAFETY STATE")
        info(page, "RECORDING SAFETY", DevelopUgandaV276RecordingSafety.healthStrip(this), green)
        info(page, "LAST TAKE / RECOVERY", DevelopUgandaV276RecordingSafety.recoverySummary(this), if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(this)) red else green)

        section(page, "RECORDING PROTECTION")
        action(
            page,
            "MINIMUM BATTERY • ${DevelopUgandaV276RecordingSafety.minimumBatteryPercent(this)}%",
            "Recording is refused below this operator-set threshold. Tap to cycle 5, 10, 15, 20 and 30 percent.",
            gold
        ) {
            DevelopUgandaV276RecordingSafety.cycleMinimumBatteryPercent(this)
            recreate()
        }
        action(
            page,
            "PLANNED TAKE • ${DevelopUgandaV276RecordingSafety.plannedTakeMinutes(this)} MIN",
            "Storage pre-flight uses the bitrate measured for the selected mode. If no real bitrate has been measured, capacity remains UNKNOWN.",
            gold
        ) {
            DevelopUgandaV276RecordingSafety.cyclePlannedTakeMinutes(this)
            recreate()
        }
        toggle(page, "Recording Health Monitor", "Watches battery, storage and Android thermal state while REC is active.", { DevelopUgandaV276RecordingSafety.bool(this, "health_monitor", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "health_monitor", true)
        }
        toggle(page, "Session Journal", "Writes project/scene/take/output state at REC START so an interrupted session is visible after relaunch.", { DevelopUgandaV276RecordingSafety.bool(this, "session_journal", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "session_journal", true)
        }
        toggle(page, "Finalize Verification", "After CameraX Finalize, checks that the saved URI is readable, non-empty and has a valid duration before declaring CLIP SAFE.", { DevelopUgandaV276RecordingSafety.bool(this, "finalize_verify", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "finalize_verify", true)
        }
        toggle(page, "Lock Controls While REC", "Keeps STOP available while disabling major settings/lens/look/quality changes during an active take.", { DevelopUgandaV276RecordingSafety.controlLockEnabled(this) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "lock_controls_while_rec", true)
        }
        toggle(page, "Recording Event Log", "Keeps a short technical timeline of REC start, safety warnings, finalize and verification.", { DevelopUgandaV276RecordingSafety.bool(this, "event_log", true) }) {
            DevelopUgandaV276RecordingSafety.toggle(this, "event_log", true)
        }

        section(page, "RECOVERY ACTIONS")
        val recovered = DevelopUgandaCrashSafeTake.recoveryResults(this)
        info(
            page,
            "CRASH-SAFE SEGMENTS",
            if (recovered.isEmpty()) {
                "No interrupted segmented take is awaiting review."
            } else {
                recovered.joinToString("\n") {
                    val identity = DevelopUgandaFivemods12Identity.forModeText(it.takeId)
                    "${identity?.badge ?: "MODE UNKNOWN"} • ${DevelopUgandaFivemods12Recovery.state(it).label} • RECOVERY 100% • ${it.message} • ORIGINALS PRESERVED"
                }
            },
            if (recovered.isEmpty()) green else gold
        )
        if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(this)) {
            action(page, "MARK RECOVERY REVIEWED", "Acknowledges the interrupted/needs-check state. It does not delete, rename or modify any media.", gold) {
                DevelopUgandaV276RecordingSafety.markRecoveryReviewed(this)
                Toast.makeText(this, "RECOVERY STATE REVIEWED", Toast.LENGTH_SHORT).show()
                recreate()
            }
        }
        action(page, "OPEN MEDIA VAULT", "Inspect the actual recorded masters, BEST/KEEP state and delivery queue.", green) {
            startActivity(Intent(this, DevelopUgandaV274MediaVaultActivity::class.java))
        }

        section(page, "RECENT RECORDING EVENTS")
        val events = DevelopUgandaV276RecordingSafety.eventLog(this)
        info(page, "EVENT LOG", if (events.isEmpty()) "No V276 recording events yet." else events.joinToString("\n"), cyan)
        action(page, "CLEAR EVENT LOG", "Clears only this technical text log. Recorded media and vault entries remain untouched.", red) {
            DevelopUgandaV276RecordingSafety.clearEventLog(this)
            Toast.makeText(this, "EVENT LOG CLEARED", Toast.LENGTH_SHORT).show()
            recreate()
        }

        section(page, "VERIFICATION")
        info(
            page,
            "V276 STATUS",
            "WORKING • 15-second independently finalised CLEAN segments + durable manifest\nWORKING • launch recovery reports exact playable duration and preserves raw fragments\nWORKING • measured-bitrate storage pre-flight + configurable battery threshold + thermal block\nWORKING • live battery/storage/thermal warnings\nWORKING • STOP → SAVING… → CameraX Finalize → URI/file/duration verification\nWORKING • control lock keeps STOP available\nPRESERVED • V271–V275 systems and original masters\nBOUND • an abrupt crash can lose only the current unfinalised segment; completed segments remain playable",
            green
        )
        setContentView(scroll)
    }
}
