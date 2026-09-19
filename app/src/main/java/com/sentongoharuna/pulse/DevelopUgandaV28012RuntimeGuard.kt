package com.sentongoharuna.pulse

import android.app.Activity
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * V280/12 FIX1 runtime guard.
 *
 * Records the exact route that was opened and the last uncaught runtime fault so
 * the next launch can identify which destination failed. The guard never changes
 * camera media, recording settings or master files.
 */
object DevelopUgandaV28012RuntimeGuard {
    private const val PREFS = "develop_uganda_v28012_runtime_guard"
    private const val MAX_STACK = 7000
    private const val UI_FAULTS = "ui_fault_ring"
    private const val MAX_UI_FAULTS = 20
    @Volatile private var installed = false

    data class UiFault(
        val area: String,
        val exception: String,
        val message: String,
        val timestampMs: Long,
    )

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun install(context: Context) {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val app = context.applicationContext
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                runCatching { recordCrash(app, thread, error) }
                previous?.uncaughtException(thread, error)
            }
            installed = true
        }
    }

    fun markRoute(activity: Activity, destination: Class<out Activity>) {
        prefs(activity).edit()
            .putString("pending_route", destination.name)
            .putString("pending_label", destination.simpleName)
            .putLong("pending_ms", System.currentTimeMillis())
            .apply()
    }

    fun clearPendingRoute(context: Context) {
        prefs(context).edit()
            .remove("pending_route")
            .remove("pending_label")
            .remove("pending_ms")
            .apply()
    }

    fun markReturnedToHub(context: Context) {
        val p = prefs(context)
        val route = safeString(p.all["pending_label"], "")
        if (route.isNotBlank()) {
            p.edit()
                .putString("last_returned_route", route)
                .putLong("last_returned_ms", System.currentTimeMillis())
                .remove("pending_route")
                .remove("pending_label")
                .remove("pending_ms")
                .apply()
        }
    }

    @Synchronized
    fun recordUiFault(context: Context, area: String, error: Throwable): UiFault {
        val fault = UiFault(
            area = area.take(80),
            exception = error.javaClass.name.take(180),
            message = (error.message ?: "no message").take(500),
            timestampMs = System.currentTimeMillis(),
        )
        val retained = uiFaults(context).takeLast(MAX_UI_FAULTS - 1) + fault
        val encoded = JSONArray().apply {
            retained.forEach { item ->
                put(JSONObject().apply {
                    put("area", item.area)
                    put("exception", item.exception)
                    put("message", item.message)
                    put("timestamp_ms", item.timestampMs)
                })
            }
        }.toString()
        prefs(context).edit()
            .putString(UI_FAULTS, encoded)
            // Keep the legacy last-fault fields readable by older installs/tools.
            .putString("last_ui_area", fault.area)
            .putString("last_ui_error", fault.exception.substringAfterLast('.'))
            .putString("last_ui_message", fault.message)
            .putLong("last_ui_ms", fault.timestampMs)
            .apply()
        return fault
    }

    fun uiFaults(context: Context): List<UiFault> {
        val raw = prefs(context).getString(UI_FAULTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        UiFault(
                            area = item.optString("area", "UNKNOWN"),
                            exception = item.optString("exception", "RuntimeException"),
                            message = item.optString("message", "no message"),
                            timestampMs = item.optLong("timestamp_ms", 0L),
                        )
                    )
                }
            }.takeLast(MAX_UI_FAULTS)
        }.getOrElse { emptyList() }
    }

    fun latestUiFault(context: Context, area: String? = null): UiFault? {
        val faults = uiFaults(context)
        return if (area == null) faults.lastOrNull()
        else faults.lastOrNull { it.area.equals(area, ignoreCase = true) }
    }

    private fun recordCrash(context: Context, thread: Thread, error: Throwable) {
        val p = prefs(context)
        val route = safeString(p.all["pending_label"], "HOME / UNKNOWN")
        val stack = error.stackTraceToString().take(MAX_STACK)
        p.edit()
            .putString("last_crash_route", route)
            .putString("last_crash_exception", error.javaClass.name)
            .putString("last_crash_message", (error.message ?: "no message").take(700))
            .putString("last_crash_thread", thread.name.take(80))
            .putString("last_crash_stack", stack)
            .putLong("last_crash_ms", System.currentTimeMillis())
            .apply()
    }

    fun lastCrashSummary(context: Context): String? {
        val p = prefs(context)
        val whenMs = safeLong(p.all["last_crash_ms"], 0L)
        if (whenMs <= 0L) return null
        val age = System.currentTimeMillis() - whenMs
        if (age > 7L * 24L * 60L * 60L * 1000L) return null
        val route = safeString(p.all["last_crash_route"], "UNKNOWN")
        val ex = safeString(p.all["last_crash_exception"], "RuntimeError").substringAfterLast('.')
        val msg = safeString(p.all["last_crash_message"], "no message").replace('\n', ' ').take(180)
        return "LAST RUNTIME FAULT • $route • $ex • $msg"
    }

    fun fullCrashReport(context: Context): String {
        val p = prefs(context)
        return buildString {
            append("V280/12 RUNTIME REPORT\n")
            append("ROUTE: ").append(safeString(p.all["last_crash_route"], "UNKNOWN")).append('\n')
            append("EXCEPTION: ").append(safeString(p.all["last_crash_exception"], "UNKNOWN")).append('\n')
            append("MESSAGE: ").append(safeString(p.all["last_crash_message"], "no message")).append('\n')
            append("THREAD: ").append(safeString(p.all["last_crash_thread"], "unknown")).append('\n')
            append("STACK:\n").append(safeString(p.all["last_crash_stack"], "not recorded"))
            append("\n\nUI FAULTS (newest last, capped at ").append(MAX_UI_FAULTS).append("):\n")
            val faults = uiFaults(context)
            if (faults.isEmpty()) {
                append("none recorded")
            } else {
                faults.forEachIndexed { index, fault ->
                    append(index + 1).append(". ")
                        .append(fault.timestampMs).append(" • ")
                        .append(fault.area).append(" • ")
                        .append(fault.exception.substringAfterLast('.')).append(" • ")
                        .append(fault.message.replace('\n', ' ')).append('\n')
                }
            }
        }
    }

    fun safeString(value: Any?, fallback: String): String = when (value) {
        is String -> value
        null -> fallback
        else -> value.toString()
    }

    fun safeInt(value: Any?, fallback: Int): Int = when (value) {
        is Int -> value
        is Long -> value.toInt()
        is Float -> value.toInt()
        is Double -> value.toInt()
        is String -> value.trim().toIntOrNull() ?: fallback
        else -> fallback
    }

    fun safeLong(value: Any?, fallback: Long): Long = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Float -> value.toLong()
        is Double -> value.toLong()
        is String -> value.trim().toLongOrNull() ?: fallback
        else -> fallback
    }

    fun safeBoolean(value: Any?, fallback: Boolean): Boolean = when (value) {
        is Boolean -> value
        is String -> when (value.trim().uppercase(Locale.US)) {
            "TRUE", "ON", "YES", "1" -> true
            "FALSE", "OFF", "NO", "0" -> false
            else -> fallback
        }
        is Number -> value.toInt() != 0
        else -> fallback
    }
}
