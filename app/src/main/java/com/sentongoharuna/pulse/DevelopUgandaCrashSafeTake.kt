package com.sentongoharuna.pulse

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * Durable manifest for a take made from short, independently finalised MP4 segments.
 *
 * CameraX's Recorder produces one ordinary MP4 and exposes the output only after its
 * Finalize event. It does not expose a supported API for periodically flushing the
 * MP4 sample tables/moov atom. A crash before that callback can therefore leave the
 * current container unplayable. The protection used here is bounded segmentation:
 * every completed segment is a normal playable CLEAN master and the manifest records
 * the actual seam. The raw current fragment is never deleted or overwritten.
 */
class DevelopUgandaCrashSafeTake(
    private val context: Context,
    val takeId: String,
    val mode: DevelopUgandaCameraPage,
    private val preflightWarnings: List<String> = emptyList(),
) {
    companion object {
        /** At most this much of the current segment is exposed to an abrupt process loss. */
        const val SEGMENT_DURATION_MS = 15_000L
        private const val DIRECTORY = "recording-recovery"
        private const val FORMAT_VERSION = 1

        data class RecoveryResult(
            val takeId: String,
            val playableSegments: Int,
            val playableDurationMs: Long,
            val unfinishedSegments: Int,
            val rawFragmentsPreserved: Int,
            val message: String,
        )

        fun begin(
            context: Context,
            takeId: String,
            mode: DevelopUgandaCameraPage,
            preflightWarnings: List<String> = emptyList(),
        ): DevelopUgandaCrashSafeTake {
            require(takeId.isNotBlank()) { "Crash-safe take id is blank" }
            return DevelopUgandaCrashSafeTake(
                context.applicationContext,
                takeId,
                mode,
                preflightWarnings.distinct(),
            ).also {
                it.ensureManifest()
            }
        }

        /**
         * Marks every manifest left ACTIVE by a previous process as interrupted and
         * verifies all already-finalised segment URIs. This operation is deliberately
         * non-destructive: it never deletes, truncates, renames or rewrites a fragment.
         */
        fun recoverOnLaunch(context: Context): List<RecoveryResult> {
            val directory = File(context.filesDir, DIRECTORY)
            if (!directory.isDirectory) return emptyList()
            return directory.listFiles { file -> file.extension == "json" }
                .orEmpty()
                .mapNotNull { file ->
                    val root = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
                        ?: return@mapNotNull null
                    if (root.optString("state") != "ACTIVE") return@mapNotNull null
                    val segments = root.optJSONArray("segments") ?: JSONArray()
                    var playable = 0
                    var duration = 0L
                    var unfinished = 0
                    var rawPreserved = 0
                    var repairedCopies = 0
                    for (index in 0 until segments.length()) {
                        val segment = segments.optJSONObject(index) ?: continue
                        val uri = segment.optString("uri")
                        val rawPath = segment.optString("rawPath")
                        val verified = uri.isNotBlank() && isPlayable(context, Uri.parse(uri))
                        if (verified) {
                            playable += 1
                            duration += segment.optLong("durationMs", 0L).coerceAtLeast(0L)
                            segment.put("recoveryState", "PLAYABLE")
                        } else {
                            unfinished += 1
                            val raw = rawPath.takeIf { it.isNotBlank() }?.let(::File)
                            if (raw?.exists() == true && raw.length() > 0L) {
                                rawPreserved += 1
                                val repaired = recoverRawCopy(raw)
                                if (repaired != null) repairedCopies += 1
                                segment.put(
                                    "recoveryState",
                                    if (repaired != null) "RECOVERED_COPY_PLAYABLE" else "RAW_FRAGMENT_PRESERVED",
                                )
                                segment.put("rawBytes", raw.length())
                                repaired?.let { segment.put("recoveredCopy", it.absolutePath) }
                            } else {
                                segment.put("recoveryState", "CURRENT_FRAGMENT_UNAVAILABLE")
                            }
                        }
                    }
                    root.put("state", "INTERRUPTED")
                    root.put("recoveredUtc", Instant.now().toString())
                    root.put("playableSegments", playable)
                    root.put("playableDurationMs", duration)
                    root.put("unfinishedSegments", unfinished)
                    root.put("rawFragmentsPreserved", rawPreserved)
                    root.put("repairedCopies", repairedCopies)
                    atomicWrite(file, root.toString(2))
                    val takeId = root.optString("takeId", file.nameWithoutExtension)
                    val message = buildString {
                        append("$takeId • salvaged $playable playable segment")
                        if (playable != 1) append('s')
                        append(" / ${formatDuration(duration)}")
                        if (unfinished > 0) append(" • $unfinished unfinished segment")
                        if (unfinished != 1) append('s')
                        if (rawPreserved > 0) append(" • $rawPreserved raw fragment preserved")
                        if (repairedCopies > 0) append(" • $repairedCopies playable recovery copy")
                    }
                    DevelopUgandaV276RecordingSafety.addEvent(context, "RECOVERY • $message")
                    RecoveryResult(takeId, playable, duration, unfinished, rawPreserved, message)
                }
        }

        fun recoveryResults(context: Context): List<RecoveryResult> {
            val directory = File(context.filesDir, DIRECTORY)
            if (!directory.isDirectory) return emptyList()
            return directory.listFiles { file -> file.extension == "json" }
                .orEmpty()
                .sortedByDescending { it.lastModified() }
                .mapNotNull { file ->
                    val root = runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
                        ?: return@mapNotNull null
                    if (root.optString("state") != "INTERRUPTED") return@mapNotNull null
                    val playable = root.optInt("playableSegments", 0)
                    val duration = root.optLong("playableDurationMs", 0L)
                    val unfinished = root.optInt("unfinishedSegments", 0)
                    val raw = root.optInt("rawFragmentsPreserved", 0)
                    RecoveryResult(
                        root.optString("takeId", file.nameWithoutExtension),
                        playable,
                        duration,
                        unfinished,
                        raw,
                        "$playable playable • ${formatDuration(duration)} • $unfinished unfinished • $raw raw preserved",
                    )
                }
        }

        private fun isPlayable(context: Context, uri: Uri): Boolean = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length in 0L..1024L) return@runCatching false
            } ?: return@runCatching false
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
                duration > 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(false)

        /**
         * A raw fragment is never edited. If it already contains a complete moov
         * atom, make a separate copy and let the existing fast-start implementation
         * relocate that atom. A fragment without sample tables remains preserved and
         * is reported as not repairable; no duration is invented.
         */
        private fun recoverRawCopy(raw: File): File? {
            val recovered = File(raw.parentFile, raw.name + ".recovered.mp4")
            if (recovered.exists()) return recovered.takeIf(::isPlayableFile)
            return runCatching {
                raw.copyTo(recovered, overwrite = false)
                DevelopUgandaFivemods7Mp4FastStart.ensure(recovered)
                require(isPlayableFile(recovered)) { "Recovered MP4 is not playable" }
                recovered
            }.getOrElse {
                runCatching { if (recovered.exists()) recovered.delete() }
                null
            }
        }

        private fun isPlayableFile(file: File): Boolean = runCatching {
            if (!file.isFile || file.length() <= 1024L) return@runCatching false
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L) > 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(false)

        private fun atomicWrite(target: File, text: String) {
            target.parentFile?.mkdirs()
            val pending = File(target.parentFile, "${target.name}.pending")
            pending.writeText(text, Charsets.UTF_8)
            if (target.exists() && !target.delete()) error("Could not replace take manifest")
            if (!pending.renameTo(target)) error("Could not commit take manifest")
        }

        private fun formatDuration(ms: Long): String {
            val seconds = (ms / 1_000L).coerceAtLeast(0L)
            return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
        }
    }

    private val manifestFile = File(File(context.filesDir, DIRECTORY), "$takeId.json")
    private var lastProgressWriteMs = 0L

    fun nextSegmentName(): String {
        val root = readManifest()
        val next = (root.optJSONArray("segments")?.length() ?: 0) + 1
        return String.format(Locale.US, "%s_S%04d", takeId, next)
    }

    fun onSegmentStarting(segmentName: String, rawPath: String? = null) {
        update { root ->
            val segments = root.optJSONArray("segments") ?: JSONArray().also { root.put("segments", it) }
            val previous = segments.optJSONObject(segments.length() - 1)
            val now = System.currentTimeMillis()
            val seamMs = previous?.optLong("finalizedWallMs", 0L)?.takeIf { it > 0L }?.let { now - it }
            segments.put(JSONObject().apply {
                put("name", segmentName)
                put("state", "RECORDING")
                put("startedUtc", Instant.now().toString())
                put("startedWallMs", now)
                put("rawPath", rawPath.orEmpty())
                if (seamMs != null) put("measuredRestartGapMs", seamMs.coerceAtLeast(0L))
            })
            root.put("state", "ACTIVE")
            root.put("activeSegment", segmentName)
        }
    }

    fun onSegmentProgress(durationNs: Long, bytes: Long) {
        val now = System.currentTimeMillis()
        if (now - lastProgressWriteMs < 1_000L) return
        lastProgressWriteMs = now
        update { root ->
            val segments = root.optJSONArray("segments") ?: return@update
            val segment = segments.optJSONObject(segments.length() - 1) ?: return@update
            segment.put("durationMs", (durationNs / 1_000_000L).coerceAtLeast(0L))
            segment.put("bytes", bytes.coerceAtLeast(0L))
        }
    }

    fun onSegmentFinalized(uri: Uri?, durationMs: Long, bytes: Long, hadError: Boolean) {
        update { root ->
            val segments = root.optJSONArray("segments") ?: return@update
            val segment = segments.optJSONObject(segments.length() - 1) ?: return@update
            val actualUri = uri?.toString().orEmpty()
            val playable = !hadError && actualUri.isNotBlank() && isPlayable(context, uri!!)
            segment.put("state", if (playable) "PLAYABLE" else "UNFINISHED")
            segment.put("uri", actualUri)
            segment.put("durationMs", durationMs.coerceAtLeast(0L))
            segment.put("bytes", bytes.coerceAtLeast(0L))
            segment.put("finalizedUtc", Instant.now().toString())
            segment.put("finalizedWallMs", System.currentTimeMillis())
            segment.put("playable", playable)
            if (hadError) segment.put("error", "CAMERA_FINALIZE_ERROR")
            root.put("lastSegmentPlayable", playable)
        }
    }

    /** Sum of recorder-reported segment durations; no wall-clock estimate. */
    fun elapsedMs(): Long {
        val segments = readManifest().optJSONArray("segments") ?: return 0L
        var total = 0L
        for (index in 0 until segments.length()) {
            total += segments.optJSONObject(index)?.optLong("durationMs", 0L)?.coerceAtLeast(0L) ?: 0L
        }
        return total
    }

    fun finish(interrupted: Boolean = false) {
        update { root ->
            root.put("state", if (interrupted) "INTERRUPTED" else "COMPLETE")
            root.put("finishedUtc", Instant.now().toString())
            val segments = root.optJSONArray("segments") ?: JSONArray()
            var totalDuration = 0L
            var totalBytes = 0L
            var playable = 0
            var measuredGaps = 0L
            for (index in 0 until segments.length()) {
                val segment = segments.optJSONObject(index) ?: continue
                if (segment.optBoolean("playable", false)) playable += 1
                totalDuration += segment.optLong("durationMs", 0L).coerceAtLeast(0L)
                totalBytes += segment.optLong("bytes", 0L).coerceAtLeast(0L)
                measuredGaps += segment.optLong("measuredRestartGapMs", 0L).coerceAtLeast(0L)
            }
            root.put("playableSegments", playable)
            root.put("totalDurationMs", totalDuration)
            root.put("totalBytes", totalBytes)
            root.put("measuredRestartGapMs", measuredGaps)
            // Costs are recorded honestly: duration/bytes/seams are measured from
            // callbacks; battery delta cannot be isolated by Android and is UNKNOWN.
            root.put("frameCost", "MEASURED_BY_RESTART_GAP_MS")
            root.put("batteryCost", "UNKNOWN_NOT_MEASURABLE_PER_TAKE")
            root.put("fileSizeCost", "MEASURED_AS_PER_SEGMENT_CONTAINER_BYTES")
        }
    }

    private fun ensureManifest() {
        if (manifestFile.isFile) return
        val root = JSONObject().apply {
            put("formatVersion", FORMAT_VERSION)
            put("strategy", "BOUNDED_SEGMENTED_CAPTURE")
            put("segmentDurationMs", SEGMENT_DURATION_MS)
            put("takeId", takeId)
            put("mode", mode.name)
            put("state", "ACTIVE")
            put("createdUtc", Instant.now().toString())
            put("segments", JSONArray())
            put("preflightWarningsProceeded", JSONArray(preflightWarnings))
            put("preflightOverridePolicy", "WARNING_OR_UNKNOWN_PROCEEDS; MEASURED_CRITICAL_FAILURE_BLOCKS")
            put("rawFragmentsPolicy", "PRESERVE_NEVER_OVERWRITE_OR_DELETE")
            put("fastStartImplementation", DevelopUgandaFivemods7Mp4FastStart::class.java.simpleName)
        }
        atomicWrite(manifestFile, root.toString(2))
    }

    private fun readManifest(): JSONObject =
        runCatching { JSONObject(manifestFile.readText(Charsets.UTF_8)) }
            .getOrElse { error("Crash-safe take manifest is unreadable: ${it.javaClass.simpleName}") }

    private inline fun update(block: (JSONObject) -> Unit) {
        val root = readManifest()
        block(root)
        atomicWrite(manifestFile, root.toString(2))
    }
}
