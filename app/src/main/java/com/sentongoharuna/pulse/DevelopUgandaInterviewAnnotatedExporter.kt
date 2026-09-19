package com.sentongoharuna.pulse

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Produces the optional, separate Interview review render. The CameraX file
 * stays clean and is never opened for overwrite; only the timestamped samples
 * already collected by the on-device observation read-out are rendered.
 */
@OptIn(UnstableApi::class)
class DevelopUgandaInterviewAnnotatedExporter(
    private val context: Context,
    private val onResult: (String) -> Unit
) {

    private data class Request(
        val inputUri: Uri,
        val recordingId: String,
        val timeline: DevelopUgandaInterviewObservationEngine.InterviewRecordingSummary
    )

    private val pending = ArrayDeque<Request>()
    private var active = false
    private var transformer: Transformer? = null

    fun enqueue(
        inputUri: Uri,
        recordingId: String,
        timeline: DevelopUgandaInterviewObservationEngine.InterviewRecordingSummary
    ) {
        pending.addLast(Request(inputUri, recordingId, timeline))
        startNext()
    }

    private fun startNext() {
        if (active) return
        val request = pending.pollFirst() ?: return
        active = true

        val directory =
            File(context.cacheDir, "v281_interview_annotated_exports").apply { mkdirs() }
        val temp =
            File(directory, "interview_${System.currentTimeMillis()}.mp4").apply {
                if (exists()) delete()
            }

        val overlaySettings =
            StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(-0.97f, -0.90f)
                .setOverlayFrameAnchor(-1f, -1f)
                .setScale(0.72f, 0.72f)
                .build()
        val positionedOverlay = InterviewTimelineTextOverlay(request.timeline, overlaySettings)

        val effects =
            Effects(
                emptyList(),
                listOf(
                    Presentation.createForWidthAndHeight(
                        1080,
                        1920,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    ),
                    // The first overlay carries the dynamic read-out; the
                    // second is an independently persistent footer.
                    OverlayEffect(
                        listOf(
                            positionedOverlay,
                            TextOverlay.createStaticTextOverlay(
                                styled("INTV • INTERVIEW CAM"),
                                StaticOverlaySettings.Builder()
                                    .setBackgroundFrameAnchor(-0.97f, 0.94f)
                                    .setOverlayFrameAnchor(-1f, 1f)
                                    .setScale(0.50f, 0.50f)
                                    .build()
                            ),
                            TextOverlay.createStaticTextOverlay(
                                persistentNotice(),
                                StaticOverlaySettings.Builder()
                                    .setBackgroundFrameAnchor(0f, -0.98f)
                                    .setOverlayFrameAnchor(0f, -1f)
                                    .setScale(0.64f, 0.64f)
                                    .build()
                            )
                        )
                    )
                )
            )

        val edited =
            EditedMediaItem.Builder(MediaItem.Builder().setUri(request.inputUri).build())
                .setFrameRate(30)
                .setEffects(effects)
                .build()
        val composition =
            Composition.Builder(
                listOf(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited)))
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
                }
            }.build()

        val encoderFactory =
            DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    VideoEncoderSettings.Builder()
                        .setBitrate(18_000_000)
                        .setiFrameIntervalSeconds(2f)
                        .build()
                )
                .setRequestedAudioEncoderSettings(
                    AudioEncoderSettings.Builder()
                        .setBitrate(128_000)
                        .build()
                )
                .build()

        transformer =
            Transformer.Builder(context)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            result: ExportResult
                        ) {
                            transformer = null
                            try {
                                val uri = publish(temp, request.recordingId)
                                temp.delete()
                                complete("INTERVIEW ANNOTATED COPY READY • $uri")
                            } catch (_: Exception) {
                                temp.delete()
                                complete("Interview annotated copy failed • clean master is safe")
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            result: ExportResult,
                            exception: ExportException
                        ) {
                            transformer = null
                            temp.delete()
                            complete("Interview annotated copy failed • clean master is safe")
                        }
                    }
                )
                .build()
                .also { it.start(composition, temp.absolutePath) }
    }

    private fun complete(message: String) {
        active = false
        onResult(message)
        startNext()
    }

    private fun publish(temp: File, recordingId: String): Uri {
        if (!temp.exists() || temp.length() <= 0L) {
            error("Annotated Interview render is empty")
        }

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeId = recordingId.replace(Regex("[^A-Za-z0-9_-]"), "_").takeLast(42)
        val values =
            ContentValues().apply {
                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    "INTV_DEVELOP_UGANDA_INTERVIEW_ANNOTATED_${safeId}_${stamp}.mp4"
                )
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/develop.uganda/Interview Annotated"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
        val uri =
            context.contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("Could not create Interview annotated output")

        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(temp).use { input -> input.copyTo(output, 1024 * 1024) }
            } ?: error("Could not write Interview annotated output")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    },
                    null,
                    null
                )
            }
            return uri
        } catch (failure: Exception) {
            runCatching { context.contentResolver.delete(uri, null, null) }
            throw failure
        }
    }

    private class InterviewTimelineTextOverlay(
        private val timeline: DevelopUgandaInterviewObservationEngine.InterviewRecordingSummary,
        private val settings: StaticOverlaySettings = StaticOverlaySettings.Builder().build()
    ) : TextOverlay() {

        override fun getText(presentationTimeUs: Long): SpannableString {
            val elapsedMs = (presentationTimeUs / 1_000L).coerceAtLeast(0L)
            val sample =
                timeline.samples.lastOrNull { it.elapsedMs <= elapsedMs }
                    ?: timeline.samples.firstOrNull()

            val contents =
                if (sample == null) {
                    if (timeline.analysisWasEnabled) {
                        "INTERVIEW OBSERVATION • NO FACE SAMPLE\n" +
                            "See the sidecar JSON for timestamped available channels."
                    } else {
                        "INTERVIEW OBSERVATION • ANALYSIS OFF\n" +
                            "Framing continued without observation read-out."
                    }
                } else {
                    fun value(name: String): String =
                        sample.channels[name]?.toString()?.padStart(2, '0') ?: "--"

                    buildString {
                        append("OBSERVED ")
                        append(sample.label)
                        append(' ')
                        append(sample.degree.toString().padStart(2, '0'))
                        append('\n')
                        append("MOVE ").append(value("MOVEMENT ENERGY"))
                        append("  POST ").append(value("POSTURAL SHIFT"))
                        append("  GAZE ").append(value("GAZE"))
                        append('\n')
                        append("BLINK ").append(value("BLINK RATE"))
                        append("  EXP ").append(value("EXPRESSION MIX"))
                        append("  COMP ").append(value("COMPOSURE INDEX"))
                        append('\n')
                        append("SIDE-CAR: TIMESTAMPED CHANNEL LOG")
                    }
                }
            return styled(contents)
        }

        override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
    }

    private companion object {
        fun persistentNotice(): SpannableString =
            styled("Observed physical signals only. Not an indicator of truthfulness.")

        fun styled(text: String): SpannableString =
            SpannableString(text).apply {
                setSpan(
                    ForegroundColorSpan(Color.WHITE),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    BackgroundColorSpan(0xB9031829.toInt()),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    AbsoluteSizeSpan(40),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    TypefaceSpan("monospace"),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
    }
}
