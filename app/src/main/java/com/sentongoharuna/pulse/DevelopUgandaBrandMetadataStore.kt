package com.sentongoharuna.pulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object DevelopUgandaBrandMetadataStore {

    enum class Tag(
        val key: String,
        val label: String
    ) {
        BRAND("brand", "Brand / Display Name"),
        REPORTER("reporter", "Reporter"),
        VERSION("version", "App Version / Camera Build"),
        STORY("story", "Story / Report ID"),
        DATE_TIME("date_time", "Date / Local Time / UTC"),
        LOCATION("location", "Location Name"),
        GPS_COORDS("gps_coords", "GPS Latitude / Longitude"),
        GPS_ACCURACY("gps_accuracy", "GPS Accuracy / Satellites / Fix Age"),
        ALTITUDE("altitude", "Altitude"),
        COMPASS("compass", "Compass / Heading"),
        SPEED_MOTION("speed_motion", "Speed / Motion / Distance"),
        HORIZON("horizon", "Horizon / Level / Tilt"),
        WEATHER("weather", "Weather"),
        AUDIO("audio", "Microphone / Audio"),
        BATTERY_STORAGE("battery_storage", "Battery / Storage"),
        NETWORK("network", "Network / Upload"),
        SHOT_GUARD("shot_guard", "Shot Guard Warnings"),
        CAMERA_MODE("camera_mode", "Camera / Quality / Scene / Look"),
        THERMAL("thermal", "Thermal State"),
        INTEGRITY("integrity", "Integrity / SHA-256 Reference")
    }

    data class Snapshot(
        val displayName: String,
        val organization: String,
        val preset: String,
        val outputProfile: String,
        val appCredit: Boolean,
        val enabled: Set<Tag>
    ) {
        fun show(
            tag: Tag
        ): Boolean =
            tag in enabled

        fun publicPrivacyMode(): Boolean =
            outputProfile ==
                OUTPUT_PUBLIC_SOCIAL

        fun creditLine(): String =
            if (
                appCredit &&
                !displayName.equals(
                    "develop.uganda",
                    ignoreCase = true
                )
            ) {
                "Recorded with develop.uganda • V235"
            } else {
                ""
            }

        fun reportPanelHeightUnits(): Float {
            var units =
                74f

            if (
                show(
                    Tag.BRAND
                )
            ) {
                units +=
                    if (
                        organization.isNotBlank()
                    ) {
                        36f
                    } else {
                        22f
                    }
            }

            // REC / TC is always retained as a recording-state line.
            units +=
                20f

            if (
                show(Tag.CAMERA_MODE)
            ) {
                units +=
                    54f
            }

            if (
                show(Tag.DATE_TIME)
            ) {
                units +=
                    22f
            }

            if (
                show(Tag.LOCATION)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.GPS_COORDS)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.GPS_ACCURACY)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.COMPASS) ||
                show(Tag.SPEED_MOTION)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.HORIZON)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.WEATHER)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.AUDIO)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.BATTERY_STORAGE)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.NETWORK)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.SHOT_GUARD)
            ) {
                units +=
                    36f
            }

            if (
                show(Tag.INTEGRITY)
            ) {
                units +=
                    22f
            }

            return units.coerceIn(
                150f,
                620f
            )
        }

        fun toJson(): JSONObject {
            return JSONObject()
                .put(
                    "display_name",
                    displayName
                )
                .put(
                    "organization",
                    organization
                )
                .put(
                    "preset",
                    preset
                )
                .put(
                    "output_profile",
                    outputProfile
                )
                .put(
                    "app_credit",
                    appCredit
                )
                .put(
                    "visible_tags",
                    JSONArray(
                        enabled
                            .map {
                                it.key
                            }
                            .sorted()
                    )
                )
        }
    }

    const val PRESET_FULL_FORENSIC =
        "FULL FORENSIC"

    const val PRESET_NEWS_REPORT =
        "NEWS REPORT"

    const val PRESET_SOCIAL_CLEAN =
        "SOCIAL CLEAN"

    const val PRESET_CONSTRUCTION =
        "CONSTRUCTION"

    const val PRESET_INTERVIEW =
        "INTERVIEW"

    const val PRESET_PRIVATE_MASTER =
        "PRIVATE MASTER"

    const val PRESET_CUSTOM =
        "CUSTOM"

    const val OUTPUT_VERIFIED_MASTER =
        "VERIFIED MASTER"

    const val OUTPUT_PUBLIC_SOCIAL =
        "PUBLIC / SOCIAL"

    val presetLabels =
        listOf(
            PRESET_FULL_FORENSIC,
            PRESET_NEWS_REPORT,
            PRESET_SOCIAL_CLEAN,
            PRESET_CONSTRUCTION,
            PRESET_INTERVIEW,
            PRESET_PRIVATE_MASTER,
            PRESET_CUSTOM
        )

    private const val PREFS =
        "develop_uganda_brand_metadata_v228"

    private const val KEY_NAME =
        "display_name"

    private const val KEY_ORGANIZATION =
        "organization"

    private const val KEY_PRESET =
        "preset"

    private const val KEY_OUTPUT =
        "output_profile"

    private const val KEY_APP_CREDIT =
        "app_credit"

    private const val KEY_TAGS =
        "enabled_tags"

    fun snapshot(
        context: Context
    ): Snapshot {
        val prefs =
            context.duSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

        val storedTags =
            prefs.getStringSet(
                KEY_TAGS,
                null
            )

        val enabled =
            if (
                storedTags ==
                    null
            ) {
                // Existing V227 users get every existing telemetry category
                // on first V228 launch, so the update drops nothing by default.
                Tag.values().toSet()
            } else {
                Tag.values()
                    .filter {
                        it.key in
                            storedTags
                    }
                    .toSet()
            }

        return Snapshot(
            displayName =
                prefs.getString(
                    KEY_NAME,
                    "develop.uganda"
                )
                    ?.trim()
                    ?.ifBlank {
                        "develop.uganda"
                    }
                    ?: "develop.uganda",
            organization =
                prefs.getString(
                    KEY_ORGANIZATION,
                    ""
                )
                    ?.trim()
                    .orEmpty(),
            preset =
                prefs.getString(
                    KEY_PRESET,
                    PRESET_FULL_FORENSIC
                )
                    ?: PRESET_FULL_FORENSIC,
            outputProfile =
                prefs.getString(
                    KEY_OUTPUT,
                    OUTPUT_VERIFIED_MASTER
                )
                    ?: OUTPUT_VERIFIED_MASTER,
            appCredit =
                prefs.getBoolean(
                    KEY_APP_CREDIT,
                    true
                ),
            enabled =
                enabled
        )
    }

    fun saveCustom(
        context: Context,
        displayName: String,
        organization: String,
        appCredit: Boolean,
        outputProfile: String,
        tags: Set<Tag>
    ) {
        save(
            context,
            Snapshot(
                displayName =
                    displayName
                        .trim()
                        .ifBlank {
                            "develop.uganda"
                        },
                organization =
                    organization.trim(),
                preset =
                    PRESET_CUSTOM,
                outputProfile =
                    outputProfile,
                appCredit =
                    appCredit,
                enabled =
                    tags
            )
        )
    }

    fun applyPreset(
        context: Context,
        preset: String
    ) {
        val current =
            snapshot(
                context
            )

        val enabled =
            tagsForPreset(
                preset
            )

        val output =
            if (
                preset ==
                    PRESET_SOCIAL_CLEAN
            ) {
                OUTPUT_PUBLIC_SOCIAL
            } else {
                current.outputProfile
            }

        save(
            context,
            current.copy(
                preset =
                    preset,
                outputProfile =
                    output,
                enabled =
                    enabled
            )
        )
    }

    fun applyOutputProfile(
        context: Context,
        outputProfile: String
    ) {
        val current =
            snapshot(
                context
            )

        val preset =
            if (
                outputProfile ==
                    OUTPUT_PUBLIC_SOCIAL
            ) {
                PRESET_SOCIAL_CLEAN
            } else {
                PRESET_FULL_FORENSIC
            }

        save(
            context,
            current.copy(
                preset =
                    preset,
                outputProfile =
                    outputProfile,
                enabled =
                    tagsForPreset(
                        preset
                    )
            )
        )
    }

    fun previewTitle(
        context: Context,
        build: String
    ): String {
        val value =
            snapshot(
                context
            )

        return if (
            value.show(
                Tag.BRAND
            )
        ) {
            "${value.displayName} • $build"
        } else {
            build
        }
    }

    fun exactGpsVisible(
        context: Context
    ): Boolean =
        snapshot(
            context
        )
            .show(
                Tag.GPS_COORDS
            )

    private fun save(
        context: Context,
        value: Snapshot
    ) {
        context.duSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
            .edit()
            .putString(
                KEY_NAME,
                value.displayName
            )
            .putString(
                KEY_ORGANIZATION,
                value.organization
            )
            .putString(
                KEY_PRESET,
                value.preset
            )
            .putString(
                KEY_OUTPUT,
                value.outputProfile
            )
            .putBoolean(
                KEY_APP_CREDIT,
                value.appCredit
            )
            .putStringSet(
                KEY_TAGS,
                value.enabled
                    .map {
                        it.key
                    }
                    .toSet()
            )
            .apply()
    }

    private fun tagsForPreset(
        preset: String
    ): Set<Tag> {
        return when (
            preset
        ) {
            PRESET_NEWS_REPORT ->
                setOf(
                    Tag.BRAND,
                    Tag.REPORTER,
                    Tag.VERSION,
                    Tag.STORY,
                    Tag.DATE_TIME,
                    Tag.LOCATION,
                    Tag.CAMERA_MODE,
                    Tag.HORIZON,
                    Tag.SHOT_GUARD
                )

            PRESET_SOCIAL_CLEAN ->
                setOf(
                    Tag.BRAND,
                    Tag.REPORTER,
                    Tag.VERSION,
                    Tag.STORY,
                    Tag.DATE_TIME,
                    Tag.LOCATION,
                    Tag.CAMERA_MODE
                )

            PRESET_CONSTRUCTION ->
                setOf(
                    Tag.BRAND,
                    Tag.REPORTER,
                    Tag.VERSION,
                    Tag.STORY,
                    Tag.DATE_TIME,
                    Tag.LOCATION,
                    Tag.COMPASS,
                    Tag.HORIZON,
                    Tag.CAMERA_MODE,
                    Tag.SHOT_GUARD
                )

            PRESET_INTERVIEW ->
                setOf(
                    Tag.BRAND,
                    Tag.REPORTER,
                    Tag.VERSION,
                    Tag.STORY,
                    Tag.DATE_TIME,
                    Tag.LOCATION,
                    Tag.AUDIO,
                    Tag.CAMERA_MODE,
                    Tag.SHOT_GUARD
                )

            PRESET_PRIVATE_MASTER ->
                setOf(
                    Tag.BRAND,
                    Tag.VERSION,
                    Tag.STORY,
                    Tag.DATE_TIME,
                    Tag.CAMERA_MODE
                )

            PRESET_CUSTOM ->
                snapshotForCustomFallback()

            else ->
                Tag.values().toSet()
        }
    }

    private fun snapshotForCustomFallback(): Set<Tag> =
        Tag.values().toSet()
}
