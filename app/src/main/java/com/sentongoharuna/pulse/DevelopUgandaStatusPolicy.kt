package com.sentongoharuna.pulse

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.widget.EditText

/**
 * WhatsApp does not expose a reliable device API for its current Status-video
 * limit.  Rather than bake an assumed value into the APK, Status Cam asks the
 * operator to confirm the limit shown by their installed WhatsApp version.
 * The value is stored only for Status Cam and drives automatic segmentation.
 */
object DevelopUgandaStatusPolicy {

    // This is the shared engine's normal per-experience preference store for
    // V281 Status.  The key below is new, so no existing value is renamed or
    // repurposed.
    private const val PREFS = "develop_uganda_report_camera_v281_status"
    private const val KEY_LIMIT_SECONDS = "confirmed_whatsapp_status_limit_seconds"

    fun segmentDurationMs(context: Context): Long? {
        val seconds =
            context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_LIMIT_SECONDS, 0)

        return seconds
            .takeIf { it > 0 }
            ?.times(1_000L)
    }

    fun requireConfirmedLimit(
        activity: Activity,
        onConfirmed: () -> Unit
    ) {
        if (segmentDurationMs(activity) != null) {
            onConfirmed()
            return
        }

        val input =
            EditText(activity).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                hint = "Seconds shown by WhatsApp"
                setSelectAllOnFocus(true)
            }

        AlertDialog.Builder(activity)
            .setTitle("CONFIRM WHATSAPP STATUS LIMIT")
            .setMessage(
                "Enter the current maximum video length shown by WhatsApp Status on this phone. " +
                    "Status Cam will segment each continuing recording at that value. " +
                    "No assumed limit is built into this app."
            )
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE LIMIT") { _, _ ->
                val seconds = input.text.toString().trim().toIntOrNull()
                if (seconds == null || seconds <= 0) {
                    DevelopUgandaStatusPolicy.requireConfirmedLimit(activity, onConfirmed)
                } else {
                    activity.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY_LIMIT_SECONDS, seconds)
                        .apply()
                    onConfirmed()
                }
            }
            .show()
    }
}
