package com.sentongoharuna.pulse

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.Locale

/**
 * The only application entry point for SharedPreferences.
 *
 * FIVEMODS 12.1 fixes the LIVE crash at the first Boolean read in
 * DevelopUgandaLiveActivity: `develop_uganda_live_camera/audio` can be present
 * as the legacy String value "ON", while the current LIVE camera code reads a
 * Boolean. The retained repository starts after that type transition (both the
 * FIVEMODS 11 and 12 archives already read Boolean), so the exact older writer
 * build is not available and is deliberately not guessed. Android's native
 * getter throws ClassCastException in that situation. This proxy repairs
 * compatible legacy values, falls back safely for every other type mismatch,
 * and records only key/type metadata (never a value, URL or stream key).
 */
fun Context.duSharedPreferences(name: String, mode: Int): SharedPreferences {
    val native = getSharedPreferences(name, mode)
    return if (native is DevelopUgandaGuardedPreferences) native
    else DevelopUgandaGuardedPreferences(applicationContext, name, native)
}

private class DevelopUgandaGuardedPreferences(
    private val context: Context,
    private val preferenceName: String,
    private val native: SharedPreferences,
) : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = native.all

    override fun getString(key: String, defValue: String?): String? {
        val value = native.all[key] ?: return defValue
        if (value is String) return value
        mismatch(key, "String", value)
        native.edit().putString(key, defValue).apply()
        return defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? {
        val value = native.all[key] ?: return defValues
        @Suppress("UNCHECKED_CAST")
        if (value is Set<*> && value.all { it is String }) return (value as Set<String>).toMutableSet()
        mismatch(key, "StringSet", value)
        native.edit().putStringSet(key, defValues).apply()
        return defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        val value = native.all[key] ?: return defValue
        val repaired = when (value) {
            is Int -> return value
            is Number -> value.toInt()
            is String -> value.trim().toIntOrNull()
            else -> null
        }
        mismatch(key, "Int", value)
        val result = repaired ?: defValue
        native.edit().putInt(key, result).apply()
        return result
    }

    override fun getLong(key: String, defValue: Long): Long {
        val value = native.all[key] ?: return defValue
        val repaired = when (value) {
            is Long -> return value
            is Number -> value.toLong()
            is String -> value.trim().toLongOrNull()
            else -> null
        }
        mismatch(key, "Long", value)
        val result = repaired ?: defValue
        native.edit().putLong(key, result).apply()
        return result
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val value = native.all[key] ?: return defValue
        val repaired = when (value) {
            is Float -> return value
            is Number -> value.toFloat()
            is String -> value.trim().toFloatOrNull()
            else -> null
        }
        mismatch(key, "Float", value)
        val result = repaired?.takeIf { it.isFinite() } ?: defValue
        native.edit().putFloat(key, result).apply()
        return result
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val value = native.all[key] ?: return defValue
        val repaired = when (value) {
            is Boolean -> return value
            is String -> when (value.trim().uppercase(Locale.US)) {
                "TRUE", "ON", "YES", "1" -> true
                "FALSE", "OFF", "NO", "0" -> false
                else -> null
            }
            is Number -> value.toInt() != 0
            else -> null
        }
        mismatch(key, "Boolean", value)
        val result = repaired ?: defValue
        native.edit().putBoolean(key, result).apply()
        return result
    }

    override fun contains(key: String): Boolean = native.contains(key)
    override fun edit(): SharedPreferences.Editor = native.edit()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
        native.registerOnSharedPreferenceChangeListener(listener)
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) =
        native.unregisterOnSharedPreferenceChangeListener(listener)

    private fun mismatch(key: String, expected: String, actual: Any) {
        val message = "PREFERENCE TYPE REPAIRED • $preferenceName/$key • ${actual.javaClass.simpleName} → $expected"
        Log.e("DU_PREF_GUARD", message)
        // This dedicated write-only journal avoids recursion if the runtime
        // diagnostic preferences themselves ever contain a wrong type.
        context.getSharedPreferences(FAULT_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString("last_fault", message)
            .putLong("last_fault_ms", System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val FAULT_PREFS = "develop_uganda_guarded_preferences_faults"
    }
}

/** Versioned, idempotent launch migration for keys used on the LIVE hot path. */
object DevelopUgandaPreferenceMigration {
    private const val MIGRATION_PREFS = "develop_uganda_preference_migrations"
    private const val MIGRATION_KEY = "fivemods_12_1_typed_preferences"

    fun run(context: Context) {
        val state = context.duSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (state.getBoolean(MIGRATION_KEY, false)) return

        val safety = context.duSharedPreferences(
            DevelopUgandaV276RecordingSafety.PREFS,
            Context.MODE_PRIVATE,
        )
        linkedMapOf(
            "session_journal" to true,
            "health_monitor" to true,
            "finalize_verify" to true,
            "capture_active" to false,
            "needs_recovery_review" to false,
            "event_log" to true,
            "lock_controls_while_rec" to true,
        ).forEach { (key, defaultValue) -> safety.getBoolean(key, defaultValue) }

        val live = context.duSharedPreferences("develop_uganda_live_camera", Context.MODE_PRIVATE)
        // `audio` is first because it is the crashing LIVE read identified
        // from the on-device fault and the current load order.
        linkedMapOf(
            "audio" to true,
            "graphics" to true,
            "countdown" to true,
        ).forEach { (key, defaultValue) -> live.getBoolean(key, defaultValue) }

        val recovery = context.duSharedPreferences("develop_uganda_live_recovery", Context.MODE_PRIVATE)
        recovery.getBoolean("active", false)
        recovery.getBoolean("incomplete", false)

        state.edit().putBoolean(MIGRATION_KEY, true).apply()
    }
}
