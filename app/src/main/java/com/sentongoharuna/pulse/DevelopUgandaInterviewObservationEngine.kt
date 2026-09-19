package com.sentongoharuna.pulse

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * A fusion/read-out layer, not a second face or pose engine. It consumes the
 * existing director overlay's on-device measurements and records only what it
 * can actually observe. Unavailable channels remain unavailable instead of
 * being guessed or turned into a claim about a person.
 */
class DevelopUgandaInterviewObservationEngine(
    private val context: Context,
    private val onSnapshot: (InterviewObservationSnapshot) -> Unit,
    private val onSidecarSaved: (String) -> Unit
) {

    data class InterviewObservationSnapshot(
        val subjectId: String,
        val label: String,
        val degree: Int,
        val channels: Map<String, Int?>,
        val drivingChannels: List<String>,
        val baselineReady: Boolean,
        val faceCount: Int
    )

    /** A real, timestamped observation sample for the separate annotated copy. */
    data class InterviewOverlaySample(
        val elapsedMs: Long,
        val label: String,
        val degree: Int,
        val channels: Map<String, Int?>,
        val drivingChannels: List<String>
    )

    /**
     * Immutable hand-off made when the CameraX master is finalized.  This is
     * deliberately a read-out of the samples already written to the sidecar,
     * not an additional perception pass over a person or their recording.
     */
    data class InterviewRecordingSummary(
        val samples: List<InterviewOverlaySample>,
        val analysisWasEnabled: Boolean,
        val burnInRequested: Boolean
    )

    private data class SubjectState(
        var lastAtMs: Long = 0L,
        var lastX: Float = 0f,
        var lastY: Float = 0f,
        var lastArea: Float = 0f,
        var lastEyeOpen: Float? = null,
        var blinks: Int = 0,
        var firstAtMs: Long = 0L
    )

    private data class Baseline(
        var startedAtMs: Long = 0L,
        var count: Int = 0,
        var movementSum: Float = 0f,
        var postureSum: Float = 0f,
        var gazeSum: Float = 0f,
        var blinkSum: Float = 0f,
        var expressionSum: Float = 0f,
        var xSum: Float = 0f,
        var ySum: Float = 0f,
        var areaSum: Float = 0f,
        var yawSum: Float = 0f,
        var composureSum: Float = 0f,
        var ready: Boolean = false
    ) {
        fun average(value: Float): Float =
            if (count == 0) value else value / count.toFloat()
    }

    private val subjectStates = mutableMapOf<String, SubjectState>()
    private val baselines = mutableMapOf<String, Baseline>()
    private var events = JSONArray()
    private var flags = JSONArray()
    private var questions = JSONArray()
    private var overlaySamples = mutableListOf<InterviewOverlaySample>()

    private var primarySubjectId: String? = null
    private var requireExplicitSubjectSelection = false
    private var analysisEnabled = true
    private var burnInEnabled = false
    private var recordingActive = false
    private var recordingId = ""
    private var recordingStartedAtMs = 0L
    private var analysisDisabledDuringRecording = false
    private var lastLoggedAtMs = 0L
    private var lastComposure = 0

    fun setAnalysisEnabled(value: Boolean) {
        analysisEnabled = value
        if (!value && recordingActive) {
            analysisDisabledDuringRecording = true
        }
        if (!value) {
            onSnapshot(
                InterviewObservationSnapshot(
                    subjectId = primarySubjectId ?: "--",
                    label = "SETTLED",
                    degree = 0,
                    channels = emptyMap(),
                    drivingChannels = listOf("ANALYSIS OFF • FRAMING ACTIVE"),
                    baselineReady = false,
                    faceCount = 0
                )
            )
        }
    }

    fun setBurnInEnabled(value: Boolean) {
        burnInEnabled = value
    }

    fun selectSubject(subjectId: String?) {
        primarySubjectId = subjectId
    }

    /**
     * Two-shot mode may frame more than one person, but it must not silently
     * change who is being read.  Until the operator taps a face, observations
     * are withheld rather than assigned to the largest detected face.
     */
    fun setRequireExplicitSubjectSelection(value: Boolean) {
        requireExplicitSubjectSelection = value
        if (value) {
            primarySubjectId = null
            onSnapshot(
                InterviewObservationSnapshot(
                    subjectId = "--",
                    label = "BASELINE DRIFT",
                    degree = 0,
                    channels = emptyMap(),
                    drivingChannels = listOf("TAP SUBJECT TO READ"),
                    baselineReady = false,
                    faceCount = 0
                )
            )
        }
    }

    /** Arms the next tapped face as the single read-out subject. */
    fun armTappedSubjectSelection() {
        primarySubjectId = null
        requireExplicitSubjectSelection = false
    }

    fun beginRecording(id: String) {
        recordingId = id
        recordingActive = true
        recordingStartedAtMs = android.os.SystemClock.elapsedRealtime()
        analysisDisabledDuringRecording = !analysisEnabled
        lastLoggedAtMs = 0L
        lastComposure = 0
        events = JSONArray()
        flags = JSONArray()
        questions = JSONArray()
        overlaySamples = mutableListOf()
    }

    fun markQuestion() {
        if (!recordingActive) return
        questions.put(
            JSONObject()
                .put("timestamp_utc", Instant.now().toString())
                .put(
                    "recording_elapsed_ms",
                    (android.os.SystemClock.elapsedRealtime() - recordingStartedAtMs)
                        .coerceAtLeast(0L)
                )
                .put("type", "question_marker")
        )
    }

    fun observe(observation: DevelopUgandaFaceObservation) {
        if (!analysisEnabled) return

        val selected = primarySubjectId
        if (selected == null) {
            if (requireExplicitSubjectSelection) return
            primarySubjectId = observation.subjectId
        } else if (selected != observation.subjectId) {
            return
        }

        val state = subjectStates.getOrPut(observation.subjectId) { SubjectState() }
        val baseline = baselines.getOrPut(observation.subjectId) { Baseline() }
        val now = observation.observedAtMs
        if (state.firstAtMs == 0L) state.firstAtMs = now
        if (baseline.startedAtMs == 0L) baseline.startedAtMs = now

        val elapsedMs = (now - state.lastAtMs).coerceAtLeast(0L)
        val distance =
            if (state.lastAtMs == 0L) {
                0f
            } else {
                sqrt(
                    ((observation.centerX - state.lastX) *
                        (observation.centerX - state.lastX) +
                        (observation.centerY - state.lastY) *
                        (observation.centerY - state.lastY)).toDouble()
                ).toFloat()
            }
        val movement =
            if (elapsedMs == 0L) 0f else (distance * 1000f / elapsedMs.toFloat()) * 160f

        val eyeOpen =
            listOfNotNull(observation.leftEyeOpen, observation.rightEyeOpen)
                .average()
                .takeIf { !it.isNaN() }
                ?.toFloat()

        if (
            state.lastEyeOpen != null &&
                eyeOpen != null &&
                state.lastEyeOpen!! > 0.70f &&
                eyeOpen < 0.30f
        ) {
            state.blinks += 1
        }

        val postureRaw =
            if (baseline.count == 0) {
                0f
            } else {
                abs(observation.centerX - baseline.average(baseline.xSum)) * 90f +
                    abs(observation.centerY - baseline.average(baseline.ySum)) * 120f +
                    abs(observation.areaRatio - baseline.average(baseline.areaSum)) * 520f +
                    abs(observation.yawDegrees - baseline.average(baseline.yawSum)) * 1.6f
            }
        val gazeRaw = abs(observation.yawDegrees) * 2.2f
        val blinkRate =
            if (state.firstAtMs == 0L || now <= state.firstAtMs) {
                0f
            } else {
                state.blinks * 60_000f / (now - state.firstAtMs).toFloat()
            }
        val expressionRaw =
            observation.smileProbability
                ?.let { abs(it - 0.5f) * 100f }
                ?: 0f

        val movementScore = movement.coerceIn(0f, 100f)
        val postureScore = postureRaw.coerceIn(0f, 100f)
        val gazeScore = gazeRaw.coerceIn(0f, 100f)
        val blinkScore = blinkRate.coerceIn(0f, 100f)
        val expressionScore = expressionRaw.coerceIn(0f, 100f)
        val physicalComposure =
            (movementScore * 0.35f +
                postureScore * 0.30f +
                gazeScore * 0.15f +
                blinkScore * 0.10f +
                expressionScore * 0.10f)
                .roundToInt()
                .coerceIn(0, 100)

        if (!baseline.ready) {
            baseline.count += 1
            baseline.movementSum += movementScore
            baseline.postureSum += postureScore
            baseline.gazeSum += gazeScore
            baseline.blinkSum += blinkScore
            baseline.expressionSum += expressionScore
            baseline.xSum += observation.centerX
            baseline.ySum += observation.centerY
            baseline.areaSum += observation.areaRatio
            baseline.yawSum += observation.yawDegrees
            baseline.composureSum += physicalComposure.toFloat()
            baseline.ready = now - baseline.startedAtMs >= 10_000L && baseline.count >= 3
        }

        val channels =
            linkedMapOf<String, Int?>(
                "MOVEMENT ENERGY" to movementScore.roundToInt(),
                "POSTURAL SHIFT" to postureScore.roundToInt(),
                "SELF-TOUCH" to null,
                "GAZE" to gazeScore.roundToInt(),
                "BLINK RATE" to blinkScore.roundToInt(),
                "EXPRESSION MIX" to expressionScore.roundToInt(),
                "FACIAL TENSION" to null,
                "SPEECH PACE" to null,
                "PAUSE" to null,
                "COMPOSURE INDEX" to physicalComposure
            )

        val driving =
            channels
                .filterValues { it != null && it >= 35 }
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

        val label =
            when {
                !baseline.ready -> "BASELINE DRIFT"
                physicalComposure >= 70 -> "AGITATED"
                physicalComposure >= 42 -> "ACTIVATED"
                else -> "SETTLED"
            }

        val snapshot =
            InterviewObservationSnapshot(
                subjectId = observation.subjectId,
                label = label,
                degree = physicalComposure,
                channels = channels,
                drivingChannels = driving,
                baselineReady = baseline.ready,
                faceCount = observation.faceCount
            )

        state.lastAtMs = now
        state.lastX = observation.centerX
        state.lastY = observation.centerY
        state.lastArea = observation.areaRatio
        state.lastEyeOpen = eyeOpen

        if (recordingActive && now - lastLoggedAtMs >= 950L) {
            lastLoggedAtMs = now
            appendLog(snapshot, observation)
            overlaySamples +=
                InterviewOverlaySample(
                    elapsedMs = (now - recordingStartedAtMs).coerceAtLeast(0L),
                    label = snapshot.label,
                    degree = snapshot.degree,
                    channels = LinkedHashMap(snapshot.channels),
                    drivingChannels = snapshot.drivingChannels.toList()
                )
            if (baseline.ready && abs(physicalComposure - lastComposure) > 25) {
                flags.put(
                    JSONObject()
                        .put("timestamp_utc", Instant.now().toString())
                        .put(
                            "recording_elapsed_ms",
                            (now - recordingStartedAtMs).coerceAtLeast(0L)
                        )
                        .put("subject_id", observation.subjectId)
                        .put("type", "composure_shift")
                        .put("delta", physicalComposure - lastComposure)
                )
            }
            lastComposure = physicalComposure
        }

        onSnapshot(snapshot)
    }

    private fun appendLog(
        snapshot: InterviewObservationSnapshot,
        observation: DevelopUgandaFaceObservation
    ) {
        val values = JSONObject()
        snapshot.channels.forEach { (name, value) ->
            values.put(name, value ?: JSONObject.NULL)
        }

        events.put(
            JSONObject()
                .put("timestamp_utc", Instant.now().toString())
                .put(
                    "recording_elapsed_ms",
                    (observation.observedAtMs - recordingStartedAtMs).coerceAtLeast(0L)
                )
                .put("subject_id", snapshot.subjectId)
                .put("label", snapshot.label)
                .put("degree", snapshot.degree)
                .put("channels", values)
                .put("driving_channels", JSONArray(snapshot.drivingChannels))
                .put("face_count", observation.faceCount)
                .put("baseline_ready", snapshot.baselineReady)
        )
    }

    fun finishRecording(): InterviewRecordingSummary? {
        if (!recordingActive) return null
        recordingActive = false

        val summary =
            InterviewRecordingSummary(
                samples = overlaySamples.toList(),
                analysisWasEnabled = !analysisDisabledDuringRecording,
                burnInRequested = burnInEnabled
            )

        val payload =
            JSONObject()
                .put("recording_id", recordingId)
                .put("created_utc", Instant.now().toString())
                .put("processing", "on_device")
                .put(
                    "notice",
                    "Observed physical signals only. Not an indicator of truthfulness."
                )
                .put("burn_in_requested", burnInEnabled)
                .put("annotated_sample_count", summary.samples.size)
                .put("events", events)
                .put("auto_flags", flags)
                .put("question_markers", questions)

        Thread {
            try {
                val name =
                    "DEVELOP_UGANDA_INTERVIEW_${recordingId.ifBlank { "LOG" }}.json"
                val destination = writeSidecar(name, payload.toString(2))
                onSidecarSaved(destination)
            } catch (_: Exception) {
                onSidecarSaved("Interview sidecar could not be written")
            }
        }.start()

        return summary
    }

    private fun writeSidecar(name: String, content: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values =
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, name)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, "Download/develop.uganda/Interview")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
            val uri =
                context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Could not create Interview JSON")
            try {
                context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                    output.write(content.toByteArray())
                } ?: error("Could not write Interview JSON")
                context.contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Downloads.IS_PENDING, 0)
                    },
                    null,
                    null
                )
                return uri.toString()
            } catch (failure: Exception) {
                runCatching { context.contentResolver.delete(uri, null, null) }
                throw failure
            }
        }

        val directory =
            File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
                    ?: context.filesDir,
                "develop.uganda/Interview"
            ).apply { mkdirs() }
        val output = File(directory, name)
        FileOutputStream(output).use { stream ->
            stream.write(content.toByteArray())
        }
        return output.absolutePath
    }
}
