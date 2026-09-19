package com.sentongoharuna.pulse

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
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
import kotlin.math.abs
import kotlin.math.max

/**
 * The FIVEMODS 9 delivery contract:
 *
 * - CameraX owns and writes the untouched CLEAN file directly to MediaStore.
 * - This worker reads that finalized file once and composites every BRAND mark
 *   in a single Media3 pass.
 * - A BRAND result is only published after duration, timeline-start and video
 *   sample-count checks agree with CLEAN.  Otherwise the callback says BRAND
 *   is missing; it never reports a dual export that did not complete.
 */
@OptIn(UnstableApi::class)
class DevelopUgandaFivemods9DualOutputExporter(
    private val context: Context,
    private val recordingActive: () -> Boolean,
    private val onFinished: (Outcome) -> Unit
) {

    data class TakeMetadata(
        val takeId: String,
        val mode: String,
        val capturedAtMs: Long,
        val operator: String,
        val locationName: String?,
        val latitude: Double?,
        val longitude: Double?,
        val altitudeM: Double?,
        val weather: String?,
        val shutterNs: Long?,
        val iso: Int?,
        val interviewDisclaimer: Boolean,
        val composureTrend: List<Int> = emptyList(),
        val statusSegment: Boolean = false
    )

    data class Outcome(
        val take: TakeMetadata,
        val cleanUri: Uri,
        val brandUri: Uri?,
        val detail: String
    ) {
        val complete: Boolean
            get() = brandUri != null
    }

    data class AssemblyClip(
        val cleanUri: Uri,
        val displayName: String,
        val mode: String,
        val trimStartMs: Long,
        val trimEndMs: Long,
    )

    data class AssemblyOutcome(
        val brandUri: Uri?,
        val detail: String,
        val clipCount: Int,
        val durationMs: Long,
    ) {
        val complete: Boolean get() = brandUri != null
    }

    private data class Request(
        val cleanUri: Uri,
        val take: TakeMetadata
    )

    private data class Probe(
        val width: Int,
        val height: Int,
        val durationMs: Long,
        val bitrate: Long,
        val mime: String?,
        val frameRate: Int?,
        val firstVideoTimeUs: Long,
        val lastVideoTimeUs: Long,
        val videoSamples: Long,
        val timelineFingerprint: Long
    )

    private data class PreparedAssemblyClip(
        val clip: AssemblyClip,
        val source: Probe,
        val startMs: Long,
        val endMs: Long,
        val localCues: List<DevelopUgandaCaptionCue>,
    )

    private data class AssemblyExpectation(
        val durationMs: Long,
        val firstVideoTimeUs: Long,
        val lastVideoTimeUs: Long,
        val videoSamples: Long,
        val timelineFingerprint: Long,
        val frameRate: Int,
    )

    private val main = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Request>()
    private var active = false
    private var transformer: Transformer? = null

    fun enqueue(cleanUri: Uri, take: TakeMetadata) {
        queue.addLast(Request(cleanUri, take))
        main.post { startNext() }
    }

    fun cancel() {
        transformer?.cancel()
        transformer = null
        queue.clear()
        active = false
    }

    /**
     * Multi-clip BRAND assembly uses this same Media3 Transformer and overlay
     * chain. Every source URI is read-only; no CLEAN master is opened for
     * writing, renamed, deleted or replaced.
     */
    fun assembleBrand(
        clips: List<AssemblyClip>,
        assemblyId: String,
        onComplete: (AssemblyOutcome) -> Unit,
    ) {
        if (active || transformer != null) {
            onComplete(AssemblyOutcome(null, "ASSEMBLY BUSY", clips.size, 0L))
            return
        }
        if (recordingActive()) {
            onComplete(AssemblyOutcome(null, "ASSEMBLY REFUSED • RECORDING ACTIVE", clips.size, 0L))
            return
        }
        if (clips.size < 2) {
            onComplete(AssemblyOutcome(null, "ASSEMBLY NEEDS AT LEAST TWO CLEAN CLIPS", clips.size, 0L))
            return
        }
        if (clips.any { DevelopUgandaFivemods12Identity.forModeText(it.mode) == null }) {
            onComplete(AssemblyOutcome(null, "ASSEMBLY REFUSED • MODE IDENTITY UNKNOWN", clips.size, 0L))
            return
        }
        active = true
        Thread {
            val prepared = runCatching {
                val verified = clips.map { clip ->
                    val source = probeUri(clip.cleanUri)
                    require(source.durationMs > 0L) { "Source duration unavailable" }
                    val latestValidStart = (source.durationMs - 1L).coerceAtLeast(0L)
                    val start = clip.trimStartMs.coerceIn(0L, latestValidStart)
                    val end = clip.trimEndMs.coerceIn(start + 1L, source.durationMs)
                    val confirmed = DevelopUgandaTranscriptArchive.confirmedForMedia(
                        context,
                        clip.cleanUri,
                        clip.displayName,
                    )
                    val localCues = confirmed?.let { entry ->
                        DevelopUgandaTranscriptArchive.retimeForAssembly(entry.cues, start, end, 0L)
                    }.orEmpty()
                    PreparedAssemblyClip(clip, source, start, end, localCues)
                }
                verified to buildAssemblyExpectation(verified)
            }
            main.post {
                prepared.fold(
                    onSuccess = { (verified, expectation) ->
                        startAssemblyTransform(verified, expectation, assemblyId, onComplete)
                    },
                    onFailure = {
                        active = false
                        onComplete(AssemblyOutcome(null, "ASSEMBLY SOURCE VERIFICATION FAILED", clips.size, 0L))
                        startNext()
                    },
                )
            }
        }.start()
    }

    private fun startNext() {
        if (active) return
        val request = queue.pollFirst() ?: return
        active = true

        Thread {
            val cleanProbe = runCatching { probeUri(request.cleanUri) }
            main.post {
                cleanProbe.fold(
                    onSuccess = { probe -> prepareReviewedCaptions(request, probe) },
                    onFailure = {
                        complete(
                            Outcome(
                                request.take,
                                request.cleanUri,
                                null,
                                "CLEAN SAVED • BRAND MISSING • source verification failed"
                            )
                        )
                    }
                )
            }
        }.start()
    }

    private fun prepareReviewedCaptions(request: Request, cleanProbe: Probe) {
        DevelopUgandaCaptionReview.prepare(
            context = context,
            cleanUri = request.cleanUri,
            takeId = request.take.takeId,
            mode = request.take.mode,
            recordingActive = recordingActive,
        ) { confirmedCues ->
            // A null list means captions are off, unavailable, untimed or
            // explicitly declined. It can never mean an unreviewed draft.
            startTransform(request, cleanProbe, confirmedCues)
        }
    }

    private fun startTransform(
        request: Request,
        cleanProbe: Probe,
        confirmedCues: List<DevelopUgandaCaptionCue>?,
    ) {
        val exportDirectory = File(context.cacheDir, "fivemods9_dual_output").apply { mkdirs() }
        val temp = File(exportDirectory, "${request.take.takeId}_${System.currentTimeMillis()}.mp4")
        if (temp.exists()) temp.delete()

        val overlays: MutableList<TextureOverlay> = mutableListOf()
        overlays += DevelopUgandaBrandIdentity.overlays(
            context = context,
            identity = DevelopUgandaFivemods12Identity.forModeText(request.take.mode),
            takeId = request.take.takeId,
            durationMs = cleanProbe.durationMs,
            includeEndCard = true,
        )
        overlays += TextOverlay.createStaticTextOverlay(
            styled(brandTechnicalLine(request.take, cleanProbe)),
            StaticOverlaySettings.Builder()
                .setBackgroundFrameAnchor(-0.97f, 0.89f)
                .setOverlayFrameAnchor(-1f, 1f)
                .setScale(0.54f, 0.54f)
                .build()
        )
        if (request.take.interviewDisclaimer) {
            overlays += TextOverlay.createStaticTextOverlay(
                styled("Observed physical signals only.\nNot an indicator of truthfulness."),
                StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0.45f, 0.89f)
                    .setOverlayFrameAnchor(0f, 1f)
                    .setScale(0.46f, 0.46f)
                    .build()
            )
        }
        request.take.composureTrend.takeIf { it.size >= 2 }?.let { trend ->
            overlays += TextOverlay.createStaticTextOverlay(
                styled("COMP  ${sparkline(trend)}"),
                StaticOverlaySettings.Builder()
                    .setBackgroundFrameAnchor(0.96f, -0.92f)
                    .setOverlayFrameAnchor(1f, -1f)
                    .setScale(0.52f, 0.52f)
                    .build()
            )
        }
        confirmedCues?.takeIf { it.isNotEmpty() }?.let { cues ->
            // The existing one-pass BRAND overlay chain gains a timed text
            // overlay. CLEAN remains the finalized CameraX MediaStore file and
            // is never opened for writing.
            overlays += DevelopUgandaReviewedCaptionOverlay(cues)
        }

        val edited = EditedMediaItem.Builder(MediaItem.Builder().setUri(request.cleanUri).build())
            // No Presentation, rate conversion, crop, grade or speed effect:
            // the BRAND stream keeps CLEAN's source timeline and geometry.
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(overlays))))
            .build()
        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited)))
        ).build()

        val videoBitrate = cleanProbe.bitrate
            .takeIf { it > 0L }
            ?.coerceIn(2_000_000L, 80_000_000L)
            ?.toInt()
            ?: 18_000_000
        val videoMime = cleanProbe.mime?.takeIf { it.startsWith("video/") } ?: MimeTypes.VIDEO_H264
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(videoBitrate)
                    .setiFrameIntervalSeconds(2f)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder().setBitrate(128_000).build()
            )
            .build()

        transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(videoMime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    transformer = null
                    Thread {
                        val outcome = runCatching {
                            val brandProbe = probeFile(temp)
                            verifyFrameAlignment(cleanProbe, brandProbe)
                            val brandUri = publish(temp, request.take.takeId)
                            Outcome(
                                request.take,
                                request.cleanUri,
                                brandUri,
                                if (confirmedCues.isNullOrEmpty()) {
                                    "DUAL SAVED • CLEAN + BRAND • CAPTIONS NOT BURNED"
                                } else {
                                    "DUAL SAVED • CLEAN + BRAND • REVIEWED CAPTIONS"
                                }
                            )
                        }.getOrElse {
                            Outcome(
                                request.take,
                                request.cleanUri,
                                null,
                                "CLEAN SAVED • BRAND MISSING • render verification failed"
                            )
                        }
                        temp.delete()
                        main.post { complete(outcome) }
                    }.start()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    transformer = null
                    temp.delete()
                    complete(
                        Outcome(
                            request.take,
                            request.cleanUri,
                            null,
                            "CLEAN SAVED • BRAND MISSING • render failed"
                        )
                    )
                }
            })
            .build()
            .also { it.start(composition, temp.absolutePath) }
    }

    private fun startAssemblyTransform(
        clips: List<PreparedAssemblyClip>,
        expectation: AssemblyExpectation,
        assemblyId: String,
        onComplete: (AssemblyOutcome) -> Unit,
    ) {
        val exportDirectory = File(context.cacheDir, "fivemods9_dual_output").apply { mkdirs() }
        val temp = File(exportDirectory, "${assemblyId}_${System.currentTimeMillis()}_assembly.mp4")
        if (temp.exists()) temp.delete()

        val editedItems = clips.mapIndexed { index, prepared ->
            val duration = prepared.endMs - prepared.startMs
            val identity = DevelopUgandaFivemods12Identity.forModeText(prepared.clip.mode)
            val overlays = DevelopUgandaBrandIdentity.overlays(
                context = context,
                identity = identity,
                takeId = prepared.clip.displayName.substringBeforeLast('.'),
                durationMs = duration,
                includeEndCard = index == clips.lastIndex,
            ).toMutableList()
            if (prepared.localCues.isNotEmpty()) {
                // These cues came only from a CONFIRMED timed sidecar. Their
                // trim origin is now zero for this item; sequence concatenation
                // places them at the corresponding joined-timeline offset.
                overlays += DevelopUgandaReviewedCaptionOverlay(prepared.localCues)
            }
            val clipped = MediaItem.Builder()
                .setUri(prepared.clip.cleanUri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(prepared.startMs)
                        .setEndPositionMs(prepared.endMs)
                        .build(),
                )
                .build()
            EditedMediaItem.Builder(clipped)
                .setEffects(Effects(emptyList(), listOf(OverlayEffect(overlays))))
                .build()
        }
        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.withAudioAndVideoFrom(editedItems)),
        ).build()

        val firstProbe = clips.first().source
        val videoBitrate = clips.maxOf { it.source.bitrate }
            .takeIf { it > 0L }
            ?.coerceIn(2_000_000L, 80_000_000L)
            ?.toInt()
            ?: 18_000_000
        val videoMime = firstProbe.mime?.takeIf { it.startsWith("video/") } ?: MimeTypes.VIDEO_H264
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(videoBitrate)
                    .setiFrameIntervalSeconds(2f)
                    .build(),
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder().setBitrate(128_000).build(),
            )
            .build()

        transformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(videoMime)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    transformer = null
                    Thread {
                        val outcome = runCatching {
                            val brandProbe = probeFile(temp)
                            verifyAssemblyAlignment(expectation, brandProbe)
                            val brandUri = publish(temp, assemblyId)
                            val joinedCues = mutableListOf<DevelopUgandaCaptionCue>()
                            var offset = 0L
                            clips.forEach { prepared ->
                                prepared.localCues.forEach { cue ->
                                    joinedCues += cue.copy(
                                        index = joinedCues.size + 1,
                                        startMs = cue.startMs + offset,
                                        endMs = cue.endMs + offset,
                                    )
                                }
                                offset += prepared.endMs - prepared.startMs
                            }
                            if (joinedCues.isNotEmpty()) {
                                DevelopUgandaTranscriptArchive.saveConfirmed(
                                    context,
                                    assemblyId,
                                    clips.first().clip.mode,
                                    brandUri,
                                    joinedCues,
                                )
                            }
                            AssemblyOutcome(
                                brandUri,
                                if (joinedCues.isEmpty()) {
                                    "ASSEMBLY BRAND SAVED • ${clips.size} CLIPS • NO REVIEWED CAPTIONS AVAILABLE"
                                } else {
                                    "ASSEMBLY BRAND SAVED • ${clips.size} CLIPS • REVIEWED CAPTIONS RETIMED"
                                },
                                clips.size,
                                expectation.durationMs,
                            )
                        }.getOrElse {
                            AssemblyOutcome(
                                null,
                                "CLEAN SOURCES PRESERVED • ASSEMBLY BRAND MISSING • VERIFICATION FAILED",
                                clips.size,
                                expectation.durationMs,
                            )
                        }
                        temp.delete()
                        main.post {
                            active = false
                            onComplete(outcome)
                            startNext()
                        }
                    }.start()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException,
                ) {
                    transformer = null
                    temp.delete()
                    active = false
                    onComplete(
                        AssemblyOutcome(
                            null,
                            "CLEAN SOURCES PRESERVED • ASSEMBLY BRAND MISSING • RENDER FAILED",
                            clips.size,
                            expectation.durationMs,
                        ),
                    )
                    startNext()
                }
            })
            .build()
            .also { it.start(composition, temp.absolutePath) }
    }

    private fun complete(outcome: Outcome) {
        active = false
        onFinished(outcome)
        startNext()
    }

    private fun brandHeader(take: TakeMetadata, probe: Probe): String = buildString {
        val identity = DevelopUgandaFivemods12Identity.forModeText(take.mode)
        append("DEVELOP UGANDA  |  ")
            .append(identity?.burnLabel ?: "MODE IDENTITY UNKNOWN")
            .append('\n')
        append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date(take.capturedAtMs)))
        take.locationName?.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
        if (take.latitude != null && take.longitude != null) {
            append('\n').append(String.format(Locale.US, "GPS %.5f, %.5f", take.latitude, take.longitude))
        }
        take.altitudeM?.let { append('\n').append(String.format(Locale.US, "ALT %.0f m", it)) }
        take.weather?.takeIf { it.isNotBlank() }?.let { append('\n').append("WX ").append(it) }
        append('\n').append("TAKE ").append(take.takeId)
    }

    private fun brandTechnicalLine(take: TakeMetadata, probe: Probe): String = buildString {
        append("TECH ")
        if (probe.width > 0 && probe.height > 0) append("${probe.width}x${probe.height}")
        probe.frameRate?.takeIf { it > 0 }?.let { append(" | ${it}FPS") }
        take.shutterNs?.takeIf { it > 0L }?.let { ns ->
            val denominator = max(1L, (1_000_000_000L + ns / 2L) / ns)
            append(" | 1/").append(denominator)
        }
        take.iso?.takeIf { it > 0 }?.let { append(" | ISO ").append(it) }
        probe.mime?.removePrefix("video/")?.uppercase(Locale.US)?.let { append(" | ").append(it) }
        probe.bitrate.takeIf { it > 0L }?.let {
            append(String.format(Locale.US, " | %.1f Mbps", it / 1_000_000.0))
        }
        take.operator
            .takeIf { it.isNotBlank() && !it.equals("CITIZEN", ignoreCase = true) }
            ?.let { append('\n').append("OP ").append(it) }
    }

    private fun verifyFrameAlignment(clean: Probe, brand: Probe) {
        if (clean.videoSamples <= 0L || brand.videoSamples <= 0L) error("No video samples")
        if (clean.videoSamples != brand.videoSamples) error("Video sample count differs")
        val fps = clean.frameRate?.takeIf { it > 0 } ?: 30
        val durationToleranceMs = max(40L, 1_000L / fps.toLong())
        if (abs(clean.durationMs - brand.durationMs) > durationToleranceMs) {
            error("Duration differs")
        }
        if (abs(clean.firstVideoTimeUs - brand.firstVideoTimeUs) > 1_000L) {
            error("Timeline origin differs")
        }
        val frameToleranceUs = durationToleranceMs * 1_000L
        if (abs(clean.lastVideoTimeUs - brand.lastVideoTimeUs) > frameToleranceUs) {
            error("Timeline end differs")
        }
        if (clean.timelineFingerprint != brand.timelineFingerprint) {
            error("Frame timestamp sequence differs")
        }
    }

    private fun buildAssemblyExpectation(clips: List<PreparedAssemblyClip>): AssemblyExpectation {
        var samples = 0L
        var firstUs = -1L
        var lastUs = -1L
        var fingerprint = 1_125_899_906_842_597L
        var outputOffsetUs = 0L
        var frameRate = clips.firstNotNullOfOrNull { it.source.frameRate?.takeIf { rate -> rate > 0 } } ?: 30

        clips.forEach { prepared ->
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, prepared.clip.cleanUri, null)
                var videoTrack = -1
                for (index in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(index)
                    if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                        videoTrack = index
                        break
                    }
                }
                require(videoTrack >= 0) { "Assembly source has no video track" }
                extractor.selectTrack(videoTrack)
                val startUs = prepared.startMs * 1_000L
                val endUs = prepared.endMs * 1_000L
                var firstSourceUs = -1L
                while (true) {
                    val sourceTimeUs = extractor.sampleTime
                    if (sourceTimeUs < 0L) break
                    if (sourceTimeUs in startUs until endUs) {
                        if (firstSourceUs < 0L) firstSourceUs = sourceTimeUs
                        val globalUs = outputOffsetUs + sourceTimeUs - firstSourceUs
                        if (firstUs < 0L) firstUs = globalUs
                        lastUs = globalUs
                        samples += 1L
                        fingerprint = fingerprint * 31L + globalUs
                    }
                    if (!extractor.advance()) break
                }
                require(firstSourceUs >= 0L) { "Assembly trim contains no video samples" }
            } finally {
                runCatching { extractor.release() }
            }
            outputOffsetUs += (prepared.endMs - prepared.startMs) * 1_000L
        }
        require(samples > 0L) { "Assembly has no measured video samples" }
        return AssemblyExpectation(
            durationMs = clips.sumOf { it.endMs - it.startMs },
            firstVideoTimeUs = firstUs,
            lastVideoTimeUs = lastUs,
            videoSamples = samples,
            timelineFingerprint = fingerprint,
            frameRate = frameRate,
        )
    }

    private fun verifyAssemblyAlignment(expected: AssemblyExpectation, brand: Probe) {
        if (expected.videoSamples <= 0L || brand.videoSamples <= 0L) error("No video samples")
        if (expected.videoSamples != brand.videoSamples) error("Video sample count differs")
        val durationToleranceMs = max(40L, 1_000L / expected.frameRate.toLong())
        if (abs(expected.durationMs - brand.durationMs) > durationToleranceMs) {
            error("Duration differs")
        }
        if (abs(expected.firstVideoTimeUs - brand.firstVideoTimeUs) > 1_000L) {
            error("Timeline origin differs")
        }
        val frameToleranceUs = durationToleranceMs * 1_000L
        if (abs(expected.lastVideoTimeUs - brand.lastVideoTimeUs) > frameToleranceUs) {
            error("Timeline end differs")
        }
        if (expected.timelineFingerprint != brand.timelineFingerprint) {
            error("Frame timestamp sequence differs")
        }
    }

    private fun probeUri(uri: Uri): Probe {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        return try {
            retriever.setDataSource(context, uri)
            extractor.setDataSource(context, uri, null)
            buildProbe(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L,
                extractor
            )
        } finally {
            runCatching { retriever.release() }
            runCatching { extractor.release() }
        }
    }

    private fun probeFile(file: File): Probe {
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()
        return try {
            retriever.setDataSource(file.absolutePath)
            extractor.setDataSource(file.absolutePath)
            buildProbe(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L,
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L,
                extractor
            )
        } finally {
            runCatching { retriever.release() }
            runCatching { extractor.release() }
        }
    }

    private fun buildProbe(
        width: Int,
        height: Int,
        durationMs: Long,
        bitrate: Long,
        extractor: MediaExtractor
    ): Probe {
        var track = -1
        var mime: String? = null
        var frameRate: Int? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            val candidate = format.getString(MediaFormat.KEY_MIME)
            if (candidate?.startsWith("video/") == true) {
                track = index
                mime = candidate
                frameRate = if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    format.getInteger(MediaFormat.KEY_FRAME_RATE)
                } else {
                    null
                }
                break
            }
        }
        if (track < 0) error("Video track unavailable")
        extractor.selectTrack(track)
        var samples = 0L
        var firstUs = -1L
        var lastUs = -1L
        var fingerprint = 1_125_899_906_842_597L
        while (true) {
            val timeUs = extractor.sampleTime
            if (timeUs < 0L) break
            if (firstUs < 0L) firstUs = timeUs
            lastUs = timeUs
            samples += 1L
            fingerprint = fingerprint * 31L + timeUs
            if (!extractor.advance()) break
        }
        return Probe(
            width,
            height,
            durationMs,
            bitrate,
            mime,
            frameRate,
            firstUs,
            lastUs,
            samples,
            fingerprint
        )
    }

    private fun publish(temp: File, takeId: String): Uri {
        if (!temp.exists() || temp.length() <= 0L) error("Brand render is empty")
        val safeId = takeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${safeId}_BRAND.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/develop.uganda")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Could not create BRAND output")
        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(temp).use { input -> input.copyTo(output, 1024 * 1024) }
            } ?: error("Could not write BRAND output")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
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

    private companion object {
        fun styled(value: String): SpannableString = SpannableString(value).apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(0xD9000000.toInt()), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(34), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan("monospace"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        fun sparkline(values: List<Int>): String {
            val glyphs = "▁▂▃▄▅▆▇█"
            return values.takeLast(24).joinToString("") { value ->
                glyphs[((value.coerceIn(0, 100) * (glyphs.length - 1)) / 100)].toString()
            }
        }
    }
}
