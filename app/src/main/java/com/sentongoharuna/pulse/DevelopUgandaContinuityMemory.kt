package com.sentongoharuna.pulse

import android.content.Context

object DevelopUgandaContinuityMemory {

    data class Snapshot(
        val sceneIndex: Int,
        val lookIndex: Int,
        val qualityIndex: Int,
        val presetIndex: Int,
        val useFront: Boolean,
        val cameraDeviceId: String?,
        val zoomRatio: Float,
        val exposureCompensation: Int,
        val savedUtc: String
    )

    private const val PREFS =
        "develop_uganda_v227_continuity"

    fun save(
        context: Context,
        experienceId: String,
        snapshot: Snapshot
    ) {
        val key =
            experienceId
                .trim()
                .uppercase()
                .ifBlank {
                    "REPORT"
                }

        context.duSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putInt(
                "${key}_scene",
                snapshot.sceneIndex
            )
            .putInt(
                "${key}_look",
                snapshot.lookIndex
            )
            .putInt(
                "${key}_quality",
                snapshot.qualityIndex
            )
            .putInt(
                "${key}_preset",
                snapshot.presetIndex
            )
            .putBoolean(
                "${key}_front",
                snapshot.useFront
            )
            .putString(
                "${key}_camera_id",
                snapshot.cameraDeviceId
            )
            .putFloat(
                "${key}_zoom",
                snapshot.zoomRatio
            )
            .putInt(
                "${key}_exposure",
                snapshot.exposureCompensation
            )
            .putString(
                "${key}_utc",
                snapshot.savedUtc
            )
            .putBoolean(
                "${key}_exists",
                true
            )
            .apply()
    }

    fun load(
        context: Context,
        experienceId: String
    ): Snapshot? {
        val key =
            experienceId
                .trim()
                .uppercase()
                .ifBlank {
                    "REPORT"
                }

        val prefs =
            context.duSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        if (
            !prefs.getBoolean(
                "${key}_exists",
                false
            )
        ) {
            return null
        }

        return Snapshot(
            sceneIndex =
                prefs.getInt(
                    "${key}_scene",
                    0
                ),
            lookIndex =
                prefs.getInt(
                    "${key}_look",
                    0
                ),
            qualityIndex =
                prefs.getInt(
                    "${key}_quality",
                    0
                ),
            presetIndex =
                prefs.getInt(
                    "${key}_preset",
                    0
                ),
            useFront =
                prefs.getBoolean(
                    "${key}_front",
                    false
                ),
            cameraDeviceId =
                prefs.getString(
                    "${key}_camera_id",
                    null
                ),
            zoomRatio =
                prefs.getFloat(
                    "${key}_zoom",
                    1f
                ),
            exposureCompensation =
                prefs.getInt(
                    "${key}_exposure",
                    0
                ),
            savedUtc =
                prefs.getString(
                    "${key}_utc",
                    "--"
                ) ?: "--"
        )
    }
}
