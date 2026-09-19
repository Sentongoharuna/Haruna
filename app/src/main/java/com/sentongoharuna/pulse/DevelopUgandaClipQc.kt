package com.sentongoharuna.pulse

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import java.util.concurrent.Executors

object DevelopUgandaClipQc {

    data class Result(
        val sourceUri: Uri,
        val playableFrame: Boolean,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val rotation: Int?,
        val hasVideo: Boolean,
        val hasAudio: Boolean,
        val fileSizeBytes: Long?,
        val sourceReadable: Boolean,
        val storyPackageState: String
    ) {
        fun lines(): List<String> {
            return listOf(
                "PLAYABLE FRAME " +
                    if (
                        playableFrame
                    ) {
                        "✓"
                    } else {
                        "CHECK"
                    },
                "DURATION " +
                    durationLabel(
                        durationMs
                    ) +
                    if (
                        durationMs !=
                            null &&
                        durationMs >
                            0L
                    ) {
                        " ✓"
                    } else {
                        " CHECK"
                    },
                "VIDEO " +
                    if (
                        width !=
                            null &&
                        height !=
                            null
                    ) {
                        "${width}×${height} " +
                            if (
                                hasVideo
                            ) {
                                "✓"
                            } else {
                                "CHECK"
                            }
                    } else {
                        "-- CHECK"
                    },
                "AUDIO TRACK " +
                    if (
                        hasAudio
                    ) {
                        "✓"
                    } else {
                        "NONE"
                    },
                "ROTATION " +
                    (
                        rotation
                            ?: 0
                        ) +
                    "° ✓",
                "SOURCE URI " +
                    if (
                        sourceReadable
                    ) {
                        "✓"
                    } else {
                        "CHECK"
                    },
                "FILE SIZE " +
                    sizeLabel(
                        fileSizeBytes
                    ),
                "STORY PACKAGE " +
                    storyPackageState
            )
        }

        companion object {
            private fun durationLabel(
                value: Long?
            ): String {
                if (
                    value ==
                        null ||
                    value <=
                        0L
                ) {
                    return "--"
                }

                val total =
                    value /
                        1000L

                return String.format(
                    java.util.Locale.US,
                    "%02d:%02d",
                    total /
                        60L,
                    total %
                        60L
                )
            }

            private fun sizeLabel(
                value: Long?
            ): String {
                if (
                    value ==
                        null ||
                    value <=
                        0L
                ) {
                    return "--"
                }

                val mb =
                    value.toDouble() /
                        (
                            1024.0 *
                                1024.0
                            )

                return if (
                    mb >=
                        1024.0
                ) {
                    String.format(
                        java.util.Locale.US,
                        "%.1f GB",
                        mb /
                            1024.0
                    )
                } else {
                    String.format(
                        java.util.Locale.US,
                        "%.0f MB",
                        mb
                    )
                }
            }
        }
    }

    private val worker =
        Executors.newSingleThreadExecutor()

    private val main =
        Handler(
            Looper.getMainLooper()
        )

    fun inspect(
        context: Context,
        sourceUri: Uri,
        packageId: String,
        callback: (Result) -> Unit
    ) {
        val appContext =
            context.applicationContext

        worker.execute {
            var retriever:
                MediaMetadataRetriever? =
                null

            var playable =
                false

            var duration:
                Long? =
                null

            var width:
                Int? =
                null

            var height:
                Int? =
                null

            var rotation:
                Int? =
                null

            var hasVideo =
                false

            var hasAudio =
                false

            try {
                retriever =
                    MediaMetadataRetriever()

                retriever.setDataSource(
                    appContext,
                    sourceUri
                )

                duration =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_DURATION
                        )
                        ?.toLongOrNull()

                width =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                        )
                        ?.toIntOrNull()

                height =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                        )
                        ?.toIntOrNull()

                rotation =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
                        )
                        ?.toIntOrNull()

                hasVideo =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                        )
                        ?.equals(
                            "yes",
                            ignoreCase =
                                true
                        ) ==
                        true

                hasAudio =
                    retriever
                        .extractMetadata(
                            MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                        )
                        ?.equals(
                            "yes",
                            ignoreCase =
                                true
                        ) ==
                        true

                val frameTimeUs =
                    when {
                        duration ==
                            null ->
                                0L

                        duration!! <
                            1500L ->
                                0L

                        else ->
                            minOf(
                                1_000_000L,
                                duration!! *
                                    500L
                            )
                    }

                val frame =
                    retriever.getFrameAtTime(
                        frameTimeUs,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                    )

                playable =
                    frame !=
                        null

                try {
                    frame?.recycle()
                } catch (_: Exception) {
                }
            } catch (_: Exception) {
            } finally {
                try {
                    retriever?.release()
                } catch (_: Exception) {
                }
            }

            val readable =
                try {
                    appContext.contentResolver
                        .openFileDescriptor(
                            sourceUri,
                            "r"
                        )
                        ?.use {
                            true
                        }
                        ?: false
                } catch (_: Exception) {
                    false
                }

            val size =
                try {
                    appContext.contentResolver.query(
                        sourceUri,
                        arrayOf(
                            MediaStore.MediaColumns.SIZE
                        ),
                        null,
                        null,
                        null
                    )?.use {
                        if (
                            it.moveToFirst()
                        ) {
                            it.getLong(
                                0
                            )
                        } else {
                            null
                        }
                    }
                } catch (_: Exception) {
                    null
                }

            var packageState =
                "QUEUED"

            repeat(
                5
            ) {
                val entry =
                    DevelopUgandaStoryPackager
                        .listRegistry(
                            appContext
                        )
                        .firstOrNull {
                            it.packageId ==
                                packageId
                        }

                if (
                    entry !=
                        null
                ) {
                    packageState =
                        when {
                            entry.state.contains(
                                "ERROR",
                                ignoreCase =
                                    true
                            ) ->
                                "CHECK"

                            entry.state.contains(
                                "BUILDING",
                                ignoreCase =
                                    true
                            ) ->
                                "BUILDING"

                            else ->
                                "✓"
                        }

                    return@repeat
                }

                try {
                    Thread.sleep(
                        180L
                    )
                } catch (_: Exception) {
                }
            }

            val result =
                Result(
                    sourceUri =
                        sourceUri,
                    playableFrame =
                        playable,
                    durationMs =
                        duration,
                    width =
                        width,
                    height =
                        height,
                    rotation =
                        rotation,
                    hasVideo =
                        hasVideo,
                    hasAudio =
                        hasAudio,
                    fileSizeBytes =
                        size,
                    sourceReadable =
                        readable,
                    storyPackageState =
                        packageState
                )

            main.post {
                callback.invoke(
                    result
                )
            }
        }
    }
}
