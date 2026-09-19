package com.sentongoharuna.pulse

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Locale
import kotlin.math.max

enum class DevelopUgandaFivemods12Availability {
    READY,
    LIMITED,
    UNAVAILABLE,
    UNKNOWN,
}

enum class DevelopUgandaFivemods12Fix {
    NONE,
    REQUEST_CAMERA,
    REQUEST_MICROPHONE,
    REQUEST_LOCATION,
    OPEN_APP_PERMISSIONS,
    OPEN_LOCATION_SETTINGS,
    OPEN_NETWORK_SETTINGS,
    FREE_STORAGE,
    CONNECT_POWER,
    COOL_DEVICE,
}

data class DevelopUgandaFivemods12PreflightItem(
    val id: String,
    val label: String,
    val state: DevelopUgandaFivemods12Availability,
    val reason: String,
    val fix: DevelopUgandaFivemods12Fix,
) {
    val stateLabel: String get() = state.name
}

data class DevelopUgandaFivemods12PreflightSnapshot(
    val mode: DevelopUgandaCameraPage,
    val items: List<DevelopUgandaFivemods12PreflightItem>,
    val measuredAtMs: Long = System.currentTimeMillis(),
) {
    val readyCount: Int get() = items.count { it.state == DevelopUgandaFivemods12Availability.READY }
    val mayRecord: Boolean
        get() = items.none {
            // UNKNOWN is never a refusal. Only a positively measured
            // UNAVAILABLE state on a capture-critical item may stop REC.
            it.state == DevelopUgandaFivemods12Availability.UNAVAILABLE &&
                it.id in setOf("camera", "microphone", "storage", "permissions", "battery", "temperature")
        }
    val summary: String get() = "$readyCount/${items.size} READY"
}

/** Eight honest, platform-backed checks. No value is synthesized. */
object DevelopUgandaFivemods12Preflight {
    private const val STORAGE_RESERVE_BYTES = 256L * 1024L * 1024L
    private const val STORAGE_WARNING_BYTES = 1_024L * 1024L * 1024L

    fun snapshot(
        context: Context,
        mode: DevelopUgandaCameraPage,
        log: Boolean = true,
    ): DevelopUgandaFivemods12PreflightSnapshot {
        val items = listOf(
            camera(context),
            microphone(context),
            storage(context, mode),
            permissions(context),
            gps(context),
            network(context),
            battery(context),
            temperature(context),
        )
        return DevelopUgandaFivemods12PreflightSnapshot(mode, items).also { result ->
            if (log) DevelopUgandaFivemods12SessionLog.recordPreflight(context, result)
        }
    }

    private fun camera(context: Context): DevelopUgandaFivemods12PreflightItem {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        val count = runCatching {
            (context.getSystemService(Context.CAMERA_SERVICE) as CameraManager).cameraIdList.size
        }.getOrNull()
        return when {
            count == null -> item("camera", "CAMERA", DevelopUgandaFivemods12Availability.UNKNOWN, "Camera service did not return a hardware list.", DevelopUgandaFivemods12Fix.OPEN_APP_PERMISSIONS)
            count == 0 -> item("camera", "CAMERA", DevelopUgandaFivemods12Availability.UNAVAILABLE, "No camera is reported by this phone.", DevelopUgandaFivemods12Fix.NONE)
            !permission -> item("camera", "CAMERA", DevelopUgandaFivemods12Availability.LIMITED, "$count camera device(s) found; permission is off.", DevelopUgandaFivemods12Fix.REQUEST_CAMERA)
            else -> item("camera", "CAMERA", DevelopUgandaFivemods12Availability.READY, "$count camera device(s) reported and permission is on.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    private fun microphone(context: Context): DevelopUgandaFivemods12PreflightItem {
        val feature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val route = runCatching {
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) audio.getDevices(AudioManager.GET_DEVICES_INPUTS).size else null
        }.getOrNull()
        return when {
            !feature -> item("microphone", "MICROPHONE", DevelopUgandaFivemods12Availability.UNAVAILABLE, "This phone reports no microphone feature.", DevelopUgandaFivemods12Fix.NONE)
            !permission -> item("microphone", "MICROPHONE", DevelopUgandaFivemods12Availability.LIMITED, "Microphone exists; record-audio permission is off.", DevelopUgandaFivemods12Fix.REQUEST_MICROPHONE)
            route == null -> item("microphone", "MICROPHONE", DevelopUgandaFivemods12Availability.UNKNOWN, "Permission is on; the input route could not be read.", DevelopUgandaFivemods12Fix.OPEN_APP_PERMISSIONS)
            route <= 0 -> item("microphone", "MICROPHONE", DevelopUgandaFivemods12Availability.UNAVAILABLE, "No active input device is reported.", DevelopUgandaFivemods12Fix.OPEN_APP_PERMISSIONS)
            else -> item("microphone", "MICROPHONE", DevelopUgandaFivemods12Availability.READY, "$route input device(s) reported.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    private fun storage(context: Context, mode: DevelopUgandaCameraPage): DevelopUgandaFivemods12PreflightItem {
        val measurement = DevelopUgandaV276RecordingSafety.storageMeasurement(context)
        val free = measurement.freeBytes
            ?: return item(
                "storage",
                "STORAGE",
                DevelopUgandaFivemods12Availability.UNKNOWN,
                "Free space could not be measured • ${measurement.reason}",
                DevelopUgandaFivemods12Fix.FREE_STORAGE,
            )
        val minutes = DevelopUgandaV276RecordingSafety.estimatedRecordMinutes(context, mode)
        val freeLabel = String.format(Locale.US, "%.1f GB free", free / 1_073_741_824.0)
        return when {
            free <= STORAGE_RESERVE_BYTES -> item("storage", "STORAGE", DevelopUgandaFivemods12Availability.UNAVAILABLE, "$freeLabel measured; at or below the 256 MB finalization threshold.", DevelopUgandaFivemods12Fix.FREE_STORAGE)
            minutes != null && minutes <= 2 -> item("storage", "STORAGE", DevelopUgandaFivemods12Availability.UNAVAILABLE, "$freeLabel measured; $minutes minute(s) remain, at or below the 2-minute critical threshold.", DevelopUgandaFivemods12Fix.FREE_STORAGE)
            free <= STORAGE_WARNING_BYTES || (minutes != null && minutes <= 10) -> item("storage", "STORAGE", DevelopUgandaFivemods12Availability.LIMITED, "$freeLabel; ${minutes?.let { "about $it recording minute(s) remain" } ?: "recording time is UNKNOWN until a bitrate is measured"}.", DevelopUgandaFivemods12Fix.FREE_STORAGE)
            minutes == null -> item(
                "storage",
                "STORAGE",
                DevelopUgandaFivemods12Availability.UNKNOWN,
                "$freeLabel measured; TAKE CAPACITY UNKNOWN • NO MEASURED ${mode.name} BITRATE.",
                DevelopUgandaFivemods12Fix.NONE,
            )
            else -> item("storage", "STORAGE", DevelopUgandaFivemods12Availability.READY, "$freeLabel; about $minutes recording minute(s) remain at the measured rate.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    private fun permissions(context: Context): DevelopUgandaFivemods12PreflightItem {
        val camera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val location = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return when {
            !camera || !mic -> item("permissions", "PERMISSIONS", DevelopUgandaFivemods12Availability.UNAVAILABLE, "Required camera${if (!camera && !mic) " and microphone" else if (!mic) " microphone" else ""} permission is off.", DevelopUgandaFivemods12Fix.OPEN_APP_PERMISSIONS)
            !location -> item("permissions", "PERMISSIONS", DevelopUgandaFivemods12Availability.LIMITED, "Camera and microphone are allowed; location is off.", DevelopUgandaFivemods12Fix.REQUEST_LOCATION)
            else -> item("permissions", "PERMISSIONS", DevelopUgandaFivemods12Availability.READY, "Camera, microphone and location permissions are allowed.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    private fun gps(context: Context): DevelopUgandaFivemods12PreflightItem {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!permission) return item("gps", "GPS", DevelopUgandaFivemods12Availability.LIMITED, "Location permission is off; position is unavailable.", DevelopUgandaFivemods12Fix.REQUEST_LOCATION)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return item("gps", "GPS", DevelopUgandaFivemods12Availability.UNKNOWN, "Location service is unavailable.", DevelopUgandaFivemods12Fix.OPEN_LOCATION_SETTINGS)
        val enabled = runCatching { manager.getProviders(true).isNotEmpty() }.getOrDefault(false)
        if (!enabled) return item("gps", "GPS", DevelopUgandaFivemods12Availability.UNAVAILABLE, "No location provider is enabled.", DevelopUgandaFivemods12Fix.OPEN_LOCATION_SETTINGS)
        val last = latestLocation(manager)
            ?: return item("gps", "GPS", DevelopUgandaFivemods12Availability.UNKNOWN, "Provider is on; no real position has arrived yet.", DevelopUgandaFivemods12Fix.OPEN_LOCATION_SETTINGS)
        val ageMs = max(0L, System.currentTimeMillis() - last.time)
        val age = ageLabel(ageMs)
        return if (ageMs > 120_000L) {
            item("gps", "GPS", DevelopUgandaFivemods12Availability.LIMITED, "STALE • last known position is $age old.", DevelopUgandaFivemods12Fix.OPEN_LOCATION_SETTINGS)
        } else {
            val accuracy = if (last.hasAccuracy()) " • ±${last.accuracy.toInt()} m" else ""
            item("gps", "GPS", DevelopUgandaFivemods12Availability.READY, "Live/last position is $age old$accuracy.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    private fun latestLocation(manager: LocationManager): Location? = runCatching {
        manager.getProviders(true).asSequence()
            .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
    }.getOrNull()

    private fun network(context: Context): DevelopUgandaFivemods12PreflightItem {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return item("network", "NETWORK", DevelopUgandaFivemods12Availability.UNKNOWN, "Connectivity service is unavailable.", DevelopUgandaFivemods12Fix.OPEN_NETWORK_SETTINGS)
        val capabilities = runCatching { manager.getNetworkCapabilities(manager.activeNetwork) }.getOrNull()
            ?: return item("network", "NETWORK", DevelopUgandaFivemods12Availability.UNAVAILABLE, "No active network; local recording still works.", DevelopUgandaFivemods12Fix.OPEN_NETWORK_SETTINGS)
        val transport = transportLabel(capabilities)
        val internet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val validated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return when {
            !internet -> item("network", "NETWORK", DevelopUgandaFivemods12Availability.UNAVAILABLE, "$transport has no internet capability; local recording still works.", DevelopUgandaFivemods12Fix.OPEN_NETWORK_SETTINGS)
            !validated -> item("network", "NETWORK", DevelopUgandaFivemods12Availability.LIMITED, "$transport internet is not validated; local recording still works.", DevelopUgandaFivemods12Fix.OPEN_NETWORK_SETTINGS)
            else -> item("network", "NETWORK", DevelopUgandaFivemods12Availability.READY, "$transport internet is validated.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    private fun battery(context: Context): DevelopUgandaFivemods12PreflightItem {
        val value = DevelopUgandaV276RecordingSafety.batteryPctMeasured(context)
            ?: return item("battery", "BATTERY", DevelopUgandaFivemods12Availability.UNKNOWN, "Battery percentage could not be measured.", DevelopUgandaFivemods12Fix.CONNECT_POWER)
        return when {
            value <= 3 -> item("battery", "BATTERY", DevelopUgandaFivemods12Availability.UNAVAILABLE, "$value% measured; at or below the 3% critical threshold.", DevelopUgandaFivemods12Fix.CONNECT_POWER)
            value <= 15 -> item("battery", "BATTERY", DevelopUgandaFivemods12Availability.LIMITED, "$value% remaining; connect power for a field take.", DevelopUgandaFivemods12Fix.CONNECT_POWER)
            else -> item("battery", "BATTERY", DevelopUgandaFivemods12Availability.READY, "$value% measured.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    private fun temperature(context: Context): DevelopUgandaFivemods12PreflightItem {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return item("temperature", "TEMPERATURE", DevelopUgandaFivemods12Availability.UNKNOWN, "Android thermal status is not exposed on this OS version.", DevelopUgandaFivemods12Fix.NONE)
        }
        val status = DevelopUgandaV276RecordingSafety.thermalStatusMeasured(context)
        val label = thermalLabel(status)
        return when {
            status < 0 -> item("temperature", "TEMPERATURE", DevelopUgandaFivemods12Availability.UNKNOWN, "Phone thermal status could not be read.", DevelopUgandaFivemods12Fix.COOL_DEVICE)
            DevelopUgandaV276RecordingSafety.thermalBlocksTake(status) -> item("temperature", "TEMPERATURE", DevelopUgandaFivemods12Availability.UNAVAILABLE, "$label measured; SEVERE start-block threshold crossed. Cool the phone before recording.", DevelopUgandaFivemods12Fix.COOL_DEVICE)
            status >= PowerManager.THERMAL_STATUS_MODERATE -> item("temperature", "TEMPERATURE", DevelopUgandaFivemods12Availability.LIMITED, "$label measured; caution only, recording remains allowed and no quality setting was changed.", DevelopUgandaFivemods12Fix.COOL_DEVICE)
            else -> item("temperature", "TEMPERATURE", DevelopUgandaFivemods12Availability.READY, "$label thermal status.", DevelopUgandaFivemods12Fix.NONE)
        }
    }

    fun openFix(context: Context, fix: DevelopUgandaFivemods12Fix) {
        val action = when (fix) {
            DevelopUgandaFivemods12Fix.OPEN_LOCATION_SETTINGS -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            DevelopUgandaFivemods12Fix.OPEN_NETWORK_SETTINGS -> Settings.ACTION_WIRELESS_SETTINGS
            DevelopUgandaFivemods12Fix.FREE_STORAGE -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            DevelopUgandaFivemods12Fix.REQUEST_CAMERA,
            DevelopUgandaFivemods12Fix.REQUEST_MICROPHONE,
            DevelopUgandaFivemods12Fix.REQUEST_LOCATION,
            DevelopUgandaFivemods12Fix.OPEN_APP_PERMISSIONS -> Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            else -> null
        } ?: return
        val intent = Intent(action).apply {
            if (action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
                data = Uri.parse("package:${context.packageName}")
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun shortNetworkLabel(context: Context): String =
        network(context).let { "${it.stateLabel} • ${it.reason.substringBefore(';').substringBefore('.')}" }

    fun shortGpsLabel(context: Context): String =
        gps(context).let { "${it.stateLabel} • ${it.reason.substringBefore('.')}" }

    fun thermalLabel(status: Int): String = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "NORMAL"
        PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
        PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
        PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
        PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
        else -> "UNKNOWN"
    }

    private fun transportLabel(capabilities: NetworkCapabilities): String = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Network"
    }

    private fun ageLabel(ageMs: Long): String = when {
        ageMs < 5_000L -> "now"
        ageMs < 60_000L -> "${ageMs / 1_000L} s"
        else -> "${ageMs / 60_000L} min"
    }

    private fun item(
        id: String,
        label: String,
        state: DevelopUgandaFivemods12Availability,
        reason: String,
        fix: DevelopUgandaFivemods12Fix,
    ) = DevelopUgandaFivemods12PreflightItem(id, label, state, reason, fix)
}

data class DevelopUgandaFivemods12ProtectionState(
    val storageMinutes: Int?,
    val batteryPercent: Int?,
    val thermalStatus: String,
    val warnings: List<String>,
    val stopReason: String?,
)

/** Read-only guard decisions; callers keep ownership of their existing recorder. */
object DevelopUgandaFivemods12RecordingProtection {
    private const val FINALIZE_RESERVE_BYTES = 256L * 1024L * 1024L

    fun state(context: Context, mode: DevelopUgandaCameraPage): DevelopUgandaFivemods12ProtectionState {
        val free = DevelopUgandaV276RecordingSafety.freeStorageBytesMeasured(context)
        val minutes = DevelopUgandaV276RecordingSafety.estimatedRecordMinutes(context, mode)
        val battery = DevelopUgandaV276RecordingSafety.batteryPctMeasured(context)
        val thermal = DevelopUgandaV276RecordingSafety.thermalStatusMeasured(context)
        val warnings = mutableListOf<String>()
        val stop = when {
            free != null && free <= FINALIZE_RESERVE_BYTES -> "STORAGE RESERVE REACHED"
            minutes != null && minutes <= 0 -> "RECORDING TIME EXHAUSTED"
            battery != null && battery <= 3 -> "BATTERY CRITICAL"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermal >= PowerManager.THERMAL_STATUS_CRITICAL -> "THERMAL CRITICAL"
            else -> null
        }
        if (minutes != null && minutes <= 5) warnings += "STORAGE ABOUT $minutes MIN"
        if (battery != null && battery <= 15) warnings += "BATTERY $battery%"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && thermal >= PowerManager.THERMAL_STATUS_MODERATE) {
            warnings += "THERMAL ${DevelopUgandaFivemods12Preflight.thermalLabel(thermal)}"
        }
        return DevelopUgandaFivemods12ProtectionState(
            minutes,
            battery,
            DevelopUgandaFivemods12Preflight.thermalLabel(thermal),
            warnings.distinct(),
            stop,
        )
    }
}

enum class DevelopUgandaFivemods12RecoveryState(val label: String) {
    RECOVERED("RECOVERED"),
    PARTLY_RECOVERED("PARTLY RECOVERED"),
    COULD_NOT_RECOVER("COULD NOT RECOVER"),
}

object DevelopUgandaFivemods12Recovery {
    fun state(result: DevelopUgandaCrashSafeTake.Companion.RecoveryResult): DevelopUgandaFivemods12RecoveryState =
        when {
            result.unfinishedSegments == 0 && result.playableSegments > 0 -> DevelopUgandaFivemods12RecoveryState.RECOVERED
            result.playableSegments > 0 || result.rawFragmentsPreserved > 0 -> DevelopUgandaFivemods12RecoveryState.PARTLY_RECOVERED
            else -> DevelopUgandaFivemods12RecoveryState.COULD_NOT_RECOVER
        }

    fun summary(context: Context): String {
        val results = DevelopUgandaCrashSafeTake.recoveryResults(context)
        if (results.isEmpty()) return "FILES FOUND 0 • NOTHING TO RECOVER"
        return buildString {
            append("FILES FOUND ").append(results.size)
            results.forEachIndexed { index, result ->
                append('\n').append(index + 1).append(" • ").append(state(result).label)
                append(" • playable ").append(result.playableSegments)
                append(" • unfinished ").append(result.unfinishedSegments)
                append(" • originals preserved")
            }
        }
    }
}

/** A bounded, structured log that never stores coordinates, names, URLs or keys. */
object DevelopUgandaFivemods12SessionLog {
    private const val PREFS = "develop_uganda_fivemods12_session_log"
    private const val EVENTS = "events"
    private const val LIMIT = 96

    fun recordPreflight(context: Context, snapshot: DevelopUgandaFivemods12PreflightSnapshot) {
        val detail = snapshot.items.joinToString(",") { "${it.id}:${it.state.name}" }
        record(context, "PREFLIGHT", snapshot.mode, snapshot.summary, detail)
    }

    fun recordRecovery(context: Context, state: DevelopUgandaFivemods12RecoveryState, playable: Int, unfinished: Int) {
        record(context, "RECOVERY", null, state.label, "playable=$playable,unfinished=$unfinished,originals=preserved")
    }

    fun recordPreview(context: Context, page: DevelopUgandaCameraPage, state: String, attempt: Int) {
        record(context, "PREVIEW", page, state, "attempt=${attempt.coerceAtLeast(0)}")
    }

    fun recordError(context: Context, page: DevelopUgandaCameraPage?, area: String, failure: Throwable) {
        record(context, "ERROR", page, safe(area), failure.javaClass.simpleName.ifBlank { "UNKNOWN" })
    }

    fun record(
        context: Context,
        type: String,
        page: DevelopUgandaCameraPage?,
        state: String,
        detail: String,
    ) {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = runCatching { JSONArray(prefs.getString(EVENTS, "[]")) }.getOrElse { JSONArray() }
        val next = JSONArray().apply {
            put(JSONObject().apply {
                put("utc", Instant.now().toString())
                put("type", safe(type))
                put("mode", page?.let { DevelopUgandaFivemods12Identity.forPage(it).code } ?: "NONE")
                put("state", safe(state))
                put("detail", safe(detail))
            })
            for (index in 0 until minOf(current.length(), LIMIT - 1)) put(current.opt(index))
        }
        prefs.edit().putString(EVENTS, next.toString()).apply()
    }

    fun events(context: Context): JSONArray = runCatching {
        JSONArray(context.duSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EVENTS, "[]"))
    }.getOrElse { JSONArray() }

    fun export(context: Context): Uri? = runCatching {
        val body = JSONObject().apply {
            put("schema", "develop.uganda.fivemods12.diagnostics.v1")
            put("generatedUtc", Instant.now().toString())
            put("privacy", "No names, coordinates, media URIs, endpoints, stream keys or free-text exception messages.")
            put("events", events(context))
        }.toString(2)
        val name = "FIVEMODS12_DIAGNOSTICS_${System.currentTimeMillis()}.json"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/develop.uganda/Diagnostics")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Diagnostics destination unavailable")
            context.contentResolver.openOutputStream(uri, "w")?.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            } ?: error("Diagnostics output unavailable")
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        } else {
            val directory = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "develop.uganda/Diagnostics").apply { mkdirs() }
            val file = File(directory, name)
            file.writeText(body, Charsets.UTF_8)
            Uri.fromFile(file)
        }
    }.getOrNull()

    private fun safe(value: String): String = value
        .replace(Regex("(?i)(rtmps?|https?)://\\S+"), "[REDACTED_URL]")
        .replace(Regex("(?i)(stream[_ -]?key|token|password)\\s*[:=]?\\s*\\S+"), "${'$'}1=[REDACTED]")
        .replace(Regex("[-+]?\\d{1,3}\\.\\d{4,}"), "[REDACTED_COORDINATE]")
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .take(320)
}
