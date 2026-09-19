package com.sentongoharuna.pulse

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import org.json.JSONObject
import java.time.Instant

object DevelopUgandaMediaInbox {

    data class RecordedVideo(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val dateAddedSeconds: Long,
        val durationMs: Long?,
        val sizeBytes: Long?
    )

    fun findUnpackaged(
        context: Context,
        limit: Int = 30
    ): List<RecordedVideo> {
        val packagedSources =
            packagedSourceUris(
                context
            )

        val projection =
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE
            )

        val found =
            mutableListOf<RecordedVideo>()

        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                DevelopUgandaFivemods12Identity.mediaStoreNameWhere(MediaStore.Video.Media.DISPLAY_NAME),
                DevelopUgandaFivemods12Identity.mediaStoreNameArgs(),
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use {
                    cursor ->
                val idCol =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media._ID
                    )

                val nameCol =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DISPLAY_NAME
                    )

                val dateCol =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DATE_ADDED
                    )

                val durationCol =
                    cursor.getColumnIndex(
                        MediaStore.Video.Media.DURATION
                    )

                val sizeCol =
                    cursor.getColumnIndex(
                        MediaStore.Video.Media.SIZE
                    )

                while (
                    cursor.moveToNext() &&
                    found.size <
                        limit
                ) {
                    val id =
                        cursor.getLong(
                            idCol
                        )

                    val name =
                        cursor.getString(
                            nameCol
                        ) ?: continue

                    if (
                        shouldIgnore(
                            name
                        )
                    ) {
                        continue
                    }

                    val uri =
                        ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id
                        )

                    if (
                        uri.toString() in
                            packagedSources
                    ) {
                        continue
                    }

                    val duration =
                        if (
                            durationCol >=
                                0 &&
                            !cursor.isNull(
                                durationCol
                            )
                        ) {
                            cursor.getLong(
                                durationCol
                            )
                        } else {
                            null
                        }

                    val size =
                        if (
                            sizeCol >=
                                0 &&
                            !cursor.isNull(
                                sizeCol
                            )
                        ) {
                            cursor.getLong(
                                sizeCol
                            )
                        } else {
                            null
                        }

                    found.add(
                        RecordedVideo(
                            id =
                                id,
                            uri =
                                uri,
                            displayName =
                                name,
                            dateAddedSeconds =
                                cursor.getLong(
                                    dateCol
                                ),
                            durationMs =
                                duration,
                            sizeBytes =
                                size
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        return found
    }

    fun packageVideo(
        context: Context,
        video: RecordedVideo
    ) {
        val finished =
            Instant.ofEpochSecond(
                video.dateAddedSeconds
                    .coerceAtLeast(
                        1L
                    )
            ).toString()

        val camera =
            inferCamera(
                video.displayName
            )

        DevelopUgandaStoryPackager.createVideoPackage(
            context =
                context,
            sourceUri =
                video.uri,
            metadata =
                DevelopUgandaStoryPackager.StoryMetadata(
                    packageId =
                        "LIB_${video.id}_${video.dateAddedSeconds}",
                    camera =
                        camera,
                    reporter =
                        "MEDIA LIBRARY",
                    storyId =
                        "",
                    title =
                        "RECORDED VIDEO",
                    place =
                        null,
                    latitude =
                        null,
                    longitude =
                        null,
                    gpsAccuracyM =
                        null,
                    startedUtc =
                        finished,
                    finishedUtc =
                        finished,
                    scene =
                        inferScene(
                            video.displayName
                        ),
                    look =
                        "RECORDED",
                    quality =
                        inferQuality(
                            video.displayName
                        ),
                    autoView =
                        "AUTO VIEW • unavailable for library backfill",
                    warnings =
                        emptyList(),
                    sourceKind =
                        "MEDIASTORE_BACKFILL",
                    autoTranscribe =
                        shouldAutoTranscribe(
                            video.displayName
                        ),
                    expectSocialMaster =
                        video.displayName.contains(
                            "V222",
                            ignoreCase =
                                true
                        )
                )
        )
    }

    private fun packagedSourceUris(
        context: Context
    ): Set<String> {
        val values =
            linkedSetOf<String>()

        DevelopUgandaStoryPackager
            .listRegistry(
                context
            )
            .forEach {
                    entry ->
                val manifest =
                    DevelopUgandaStoryPackager
                        .findPackageFile(
                            context,
                            entry.packageId,
                            "MANIFEST.json"
                        )
                        ?: return@forEach

                val raw =
                    DevelopUgandaStoryPackager
                        .readText(
                            context,
                            manifest
                        )
                        ?: return@forEach

                try {
                    val source =
                        JSONObject(
                            raw
                        )
                            .optString(
                                "source_uri"
                            )

                    if (
                        source.isNotBlank()
                    ) {
                        values.add(
                            source
                        )
                    }
                } catch (_: Exception) {
                }
            }

        return values
    }

    private fun shouldIgnore(
        name: String
    ): Boolean {
        val upper =
            name.uppercase()

        return upper.contains(
            "_SM_POST_"
        ) ||
            upper.contains(
                "SOCIAL_MASTER"
            ) ||
            upper.startsWith(
                "DEVELOP_UGANDA_EDIT_"
            )
    }

    private fun inferCamera(
        name: String
    ): String {
        val upper =
            name.uppercase()

        return when {
            upper.contains(
                "V205"
            ) ->
                "V205 • PEOPLE / FOCUS"

            upper.contains(
                "V206"
            ) ->
                "V206 • METERING"

            upper.contains(
                "V207"
            ) ->
                "V207 • BUILDINGS & LEVEL"

            upper.contains(
                "V208"
            ) ->
                "V208 • STEADYSHOT"

            upper.contains(
                "V209"
            ) ->
                "V209 • NIGHT"

            upper.contains(
                "V210"
            ) ->
                "V210 • EVERYDAY PRO"

            upper.contains(
                "V211"
            ) ->
                "V211 • INTERVIEW / AUDIO"

            upper.contains(
                "V212"
            ) ->
                "V212 • VERIFIED REPORT"

            upper.contains(
                "V213"
            ) ->
                "V213 • THERMAL SAFE"

            upper.contains(
                "V214"
            ) ->
                "V214 • CINEMATIC"

            upper.contains(
                "V215"
            ) ->
                "V215 • SMART AUTO"

            upper.contains(
                "V222"
            ) ->
                "V222 • SOCIAL MEDIA CAM"

            upper.contains(
                "LIVE"
            ) ->
                "LIVE STUDIO"

            else ->
                "develop.uganda • IMPORTED RECORDING"
        }
    }

    private fun inferScene(
        name: String
    ): String {
        val upper =
            name.uppercase()

        return when {
            upper.contains(
                "NIGHT"
            ) ->
                "NIGHT"

            upper.contains(
                "INTERVIEW"
            ) ->
                "INTERVIEW"

            upper.contains(
                "CINEMA"
            ) ->
                "CINEMA"

            upper.contains(
                "OUTDOOR"
            ) ->
                "OUTDOOR"

            upper.contains(
                "INDOOR"
            ) ->
                "INDOOR"

            else ->
                "RECORDED"
        }
    }

    private fun inferQuality(
        name: String
    ): String {
        val upper =
            name.uppercase()

        return when {
            upper.contains(
                "SOCIAL"
            ) ->
                "SOCIAL"

            upper.contains(
                "UHD"
            ) ->
                "UHD"

            upper.contains(
                "FHD"
            ) ->
                "FHD"

            else ->
                "SOURCE QUALITY"
        }
    }

    private fun shouldAutoTranscribe(
        name: String
    ): Boolean {
        val upper =
            name.uppercase()

        return upper.contains(
            "V211"
        ) ||
            upper.contains(
                "INTERVIEW"
            )
    }
}
