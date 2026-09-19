package com.sentongoharuna.pulse

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LineBackgroundSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Locale

/** A real recognizer-timed cue. No cue is constructed without source timing. */
data class DevelopUgandaCaptionCue(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

data class DevelopUgandaTranscriptArchiveEntry(
    val takeId: String,
    val mode: String,
    val videoUri: Uri,
    val confirmedAtUtc: String,
    val cues: List<DevelopUgandaCaptionCue>,
    val paceWordsPerMinute: Double?,
    val answerPausesMs: List<Long>,
)

data class DevelopUgandaTranscriptSearchHit(
    val entry: DevelopUgandaTranscriptArchiveEntry,
    val cue: DevelopUgandaCaptionCue,
)

/**
 * Confirmed transcripts live in their own app-private sidecars. The video and
 * its SHA-256 sidecar are never opened for writing, so editing words cannot
 * alter or invalidate the footage seal.
 */
object DevelopUgandaTranscriptArchive {
    private const val DIRECTORY = "develop_uganda_confirmed_transcripts"

    fun saveDraft(
        context: Context,
        takeId: String,
        mode: String,
        videoUri: Uri,
        transcript: String,
        srt: String?,
        detail: String,
    ) {
        val json = JSONObject()
            .put("state", "DRAFT_REVIEW_REQUIRED")
            .put("take_id", takeId)
            .put("mode", mode)
            .put("video_uri", videoUri.toString())
            .put("transcript", transcript)
            .put("srt", srt ?: JSONObject.NULL)
            .put("recognizer_detail", detail)
            .put("word_confidence", JSONObject.NULL)
            .put("word_confidence_detail", "On-device recognizer did not expose per-word confidence to this engine")
            .put("updated_utc", Instant.now().toString())
        file(context, takeId, "draft").writeText(json.toString(2) + "\n")
    }

    fun saveConfirmed(
        context: Context,
        takeId: String,
        mode: String,
        videoUri: Uri,
        cues: List<DevelopUgandaCaptionCue>,
    ): DevelopUgandaTranscriptArchiveEntry {
        require(cues.isNotEmpty()) { "A confirmed transcript needs at least one timed cue" }
        val first = cues.minOf { it.startMs }
        val last = cues.maxOf { it.endMs }
        val durationMs = (last - first).takeIf { it > 0L }
        val words = cues.sumOf { cue -> cue.text.trim().split(Regex("\\s+")).count { it.isNotBlank() } }
        val pace = durationMs?.let { words * 60_000.0 / it.toDouble() }
        val pauses = cues.zipWithNext()
            .mapNotNull { (before, after) -> (after.startMs - before.endMs).takeIf { it >= 500L } }
        val confirmedAt = Instant.now().toString()
        val payload = JSONObject()
            .put("state", "CONFIRMED")
            .put("take_id", takeId)
            .put("mode", mode)
            .put("video_uri", videoUri.toString())
            .put("confirmed_utc", confirmedAt)
            .put("video_sha256_changed", false)
            .put("seal_scope", "VIDEO FILE ONLY; TRANSCRIPT SIDECAR IS SEPARATE")
            .put("pace_source", "DERIVED FROM CONFIRMED WORD COUNT AND CONFIRMED CUE TIMING")
            .put("pace_words_per_minute", pace ?: JSONObject.NULL)
            .put("pause_source", "DERIVED FROM GAPS BETWEEN CONFIRMED TIMED CUES")
            .put("answer_pauses_ms", JSONArray(pauses))
            .put("review_timeline", JSONArray().apply {
                pace?.let {
                    put(JSONObject()
                        .put("at_ms", first)
                        .put("channel", "PACE")
                        .put("value_wpm", it)
                        .put("origin", "DERIVED_FROM_CONFIRMED_TRANSCRIPT"))
                }
                cues.zipWithNext().forEach { (before, after) ->
                    val pause = after.startMs - before.endMs
                    if (pause >= 500L) {
                        put(JSONObject()
                            .put("at_ms", before.endMs)
                            .put("channel", "PAUSE")
                            .put("duration_ms", pause)
                            .put("origin", "DERIVED_FROM_CONFIRMED_CUE_GAP"))
                    }
                }
            })
            .put("cues", JSONArray().apply {
                cues.forEach { cue ->
                    put(JSONObject()
                        .put("index", cue.index)
                        .put("start_ms", cue.startMs)
                        .put("end_ms", cue.endMs)
                        .put("text", cue.text))
                }
            })
        file(context, takeId, "confirmed").writeText(payload.toString(2) + "\n")
        return DevelopUgandaTranscriptArchiveEntry(
            takeId,
            mode,
            videoUri,
            confirmedAt,
            cues,
            pace,
            pauses,
        )
    }

    fun saveConfirmedUntimed(
        context: Context,
        takeId: String,
        mode: String,
        videoUri: Uri,
        transcript: String,
    ) {
        require(transcript.isNotBlank()) { "An untimed transcript cannot be empty" }
        val payload = JSONObject()
            .put("state", "CONFIRMED_UNTIMED")
            .put("take_id", takeId)
            .put("mode", mode)
            .put("video_uri", videoUri.toString())
            .put("confirmed_utc", Instant.now().toString())
            .put("transcript", transcript)
            .put("timing", JSONObject.NULL)
            .put("timing_detail", "RECOGNITION_PARTS timing was unavailable; no cue timing was fabricated")
            .put("caption_burn_allowed", false)
            .put("video_sha256_changed", false)
            .put("seal_scope", "VIDEO FILE ONLY; TRANSCRIPT SIDECAR IS SEPARATE")
        file(context, takeId, "confirmed-untimed").writeText(payload.toString(2) + "\n")
    }

    fun all(context: Context): List<DevelopUgandaTranscriptArchiveEntry> =
        directory(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".confirmed.json") }
            ?.mapNotNull { runCatching { parseEntry(JSONObject(it.readText())) }.getOrNull() }
            ?.sortedByDescending { it.confirmedAtUtc }
            ?: emptyList()

    /** Only explicitly CONFIRMED, timed sidecars are eligible for assembly. */
    fun confirmedForMedia(
        context: Context,
        videoUri: Uri,
        displayName: String,
    ): DevelopUgandaTranscriptArchiveEntry? {
        val stem = displayName.substringBeforeLast('.')
        return all(context).firstOrNull { entry ->
            entry.videoUri == videoUri ||
                stem == entry.takeId ||
                stem.startsWith("${entry.takeId}_") ||
                entry.takeId.startsWith("${stem}_")
        }?.takeIf { entry ->
            DevelopUgandaCaptionReview.burnAllowed("CONFIRMED", entry.cues)
        }
    }

    /**
     * Re-times reviewed cues after trimming and concatenation. Draft and
     * CONFIRMED_UNTIMED sidecars never reach this function.
     */
    fun retimeForAssembly(
        cues: List<DevelopUgandaCaptionCue>,
        trimStartMs: Long,
        trimEndMs: Long,
        outputOffsetMs: Long,
    ): List<DevelopUgandaCaptionCue> = cues.mapNotNull { cue ->
        val clippedStart = maxOf(cue.startMs, trimStartMs)
        val clippedEnd = minOf(cue.endMs, trimEndMs)
        if (clippedEnd <= clippedStart) return@mapNotNull null
        DevelopUgandaCaptionCue(
            index = cue.index,
            startMs = outputOffsetMs + clippedStart - trimStartMs,
            endMs = outputOffsetMs + clippedEnd - trimStartMs,
            text = cue.text,
        )
    }

    fun search(context: Context, query: String): List<DevelopUgandaTranscriptSearchHit> {
        val needle = query.trim()
        if (needle.isBlank()) return emptyList()
        return all(context).flatMap { entry ->
            entry.cues.filter { it.text.contains(needle, ignoreCase = true) }
                .map { DevelopUgandaTranscriptSearchHit(entry, it) }
        }
    }

    fun stateForMedia(context: Context, videoUri: Uri, displayName: String): String {
        val matches = directory(context).listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".confirmed.json") || it.name.endsWith(".confirmed-untimed.json")) }
            ?.mapNotNull { file -> runCatching { JSONObject(file.readText()) }.getOrNull() }
            ?.filter { json ->
                val takeId = json.optString("take_id", "")
                val safeTakeId = takeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
                json.optString("video_uri", "") == videoUri.toString() ||
                    (takeId.isNotBlank() && displayName == "${safeTakeId}_BRAND.mp4")
            }
            .orEmpty()
        val timed = matches.firstOrNull { it.optString("state") == "CONFIRMED" }
        if (timed != null) return "TRANSCRIPT CONFIRMED • ${timed.optJSONArray("cues")?.length() ?: 0} CUES"
        if (matches.any { it.optString("state") == "CONFIRMED_UNTIMED" }) {
            return "TRANSCRIPT CONFIRMED • TIMING ABSENT"
        }
        return "NO TRANSCRIPT"
    }

    private fun parseEntry(json: JSONObject): DevelopUgandaTranscriptArchiveEntry {
        val cuesJson = json.getJSONArray("cues")
        val cues = buildList {
            for (index in 0 until cuesJson.length()) {
                val cue = cuesJson.getJSONObject(index)
                add(DevelopUgandaCaptionCue(
                    cue.getInt("index"),
                    cue.getLong("start_ms"),
                    cue.getLong("end_ms"),
                    cue.getString("text"),
                ))
            }
        }
        val pausesJson = json.optJSONArray("answer_pauses_ms")
        val pauses = buildList {
            if (pausesJson != null) for (index in 0 until pausesJson.length()) add(pausesJson.getLong(index))
        }
        return DevelopUgandaTranscriptArchiveEntry(
            json.getString("take_id"),
            json.getString("mode"),
            Uri.parse(json.getString("video_uri")),
            json.getString("confirmed_utc"),
            cues,
            if (json.isNull("pace_words_per_minute")) null else json.getDouble("pace_words_per_minute"),
            pauses,
        )
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun file(context: Context, takeId: String, kind: String): File {
        val safe = takeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(directory(context), "$safe.$kind.json")
    }
}

/**
 * The only gate from recognizer output to a burned caption. A recognizer draft
 * can be saved, corrected or declined, but cannot become an overlay until the
 * operator explicitly confirms it.
 */
object DevelopUgandaCaptionReview {
    fun defaultEnabled(mode: String): Boolean =
        mode.equals("TIKTOK", true) || mode.equals("STATUS", true)

    /** Pure contract used by CI: a recognizer draft or untimed confirmation can never burn. */
    fun burnAllowed(reviewState: String, cues: List<DevelopUgandaCaptionCue>): Boolean =
        reviewState == "CONFIRMED" && cues.isNotEmpty() && validate(cues) == null

    fun prepare(
        context: Context,
        cleanUri: Uri,
        takeId: String,
        mode: String,
        recordingActive: () -> Boolean,
        onComplete: (List<DevelopUgandaCaptionCue>?) -> Unit,
    ) {
        if (!defaultEnabled(mode)) {
            onComplete(null)
            return
        }
        DevelopUgandaTranscriptEngine.transcribe(context, cleanUri) { result ->
            val activity = context as? Activity
            val deliver = {
                DevelopUgandaTranscriptArchive.saveDraft(
                    context,
                    takeId,
                    mode,
                    cleanUri,
                    result.transcript,
                    result.srtDraft,
                    result.detail,
                )
                val cues = result.srtDraft?.let(::parseSrt).orEmpty()
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    onComplete(null)
                } else if (recordingActive()) {
                    Toast.makeText(
                        activity,
                        "CAPTION DRAFT SAVED • BRAND CONTINUES WITHOUT CAPTIONS DURING RECORDING",
                        Toast.LENGTH_LONG,
                    ).show()
                    onComplete(null)
                } else if (cues.isEmpty() && result.transcript.isNotBlank()) {
                    showUntimedEditor(activity, cleanUri, takeId, mode, result.transcript, onComplete)
                } else if (cues.isEmpty()) {
                    Toast.makeText(activity, result.detail, Toast.LENGTH_LONG).show()
                    onComplete(null)
                } else {
                    showEditor(activity, cleanUri, takeId, mode, cues, onComplete)
                }
            }
            if (activity != null) activity.runOnUiThread { deliver() } else deliver()
        }
    }

    private fun showUntimedEditor(
        activity: Activity,
        cleanUri: Uri,
        takeId: String,
        mode: String,
        transcript: String,
        onComplete: (List<DevelopUgandaCaptionCue>?) -> Unit,
    ) {
        val editor = EditText(activity).apply {
            setText(transcript)
            setTextColor(DevelopUgandaFivemods8Theme.content)
            setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceRaised)
            minLines = 5
            contentDescription = "Untimed transcript text"
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("TIMING ABSENT • REVIEW TRANSCRIPT")
            .setMessage("This phone did not return SDK 34+ RECOGNITION_PARTS timing. Corrected text can be saved, but it cannot be burned as captions without real cue times.")
            .setView(editor)
            .setNegativeButton("BRAND WITHOUT CAPTIONS") { _, _ -> onComplete(null) }
            .setPositiveButton("SAVE UNTIMED TRANSCRIPT", null)
            .setOnCancelListener { onComplete(null) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val corrected = editor.text.toString().trim().replace(Regex("\\s+"), " ")
                if (corrected.isBlank()) {
                    Toast.makeText(activity, "TRANSCRIPT IS EMPTY", Toast.LENGTH_LONG).show()
                } else {
                    DevelopUgandaTranscriptArchive.saveConfirmedUntimed(
                        activity,
                        takeId,
                        mode,
                        cleanUri,
                        corrected,
                    )
                    dialog.dismiss()
                    onComplete(null)
                }
            }
        }
        dialog.show()
    }

    fun showPlayback(context: Context, hit: DevelopUgandaTranscriptSearchHit) {
        context.startActivity(Intent(context, DevelopUgandaStoryPlayerActivity::class.java).apply {
            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_URI, hit.entry.videoUri.toString())
            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_LABEL, hit.entry.takeId)
            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_TAKE_ID, hit.entry.takeId)
            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_START_MS, hit.cue.startMs)
        })
    }

    private fun showEditor(
        activity: Activity,
        cleanUri: Uri,
        takeId: String,
        mode: String,
        source: List<DevelopUgandaCaptionCue>,
        onComplete: (List<DevelopUgandaCaptionCue>?) -> Unit,
    ) {
        val rows = mutableListOf<Pair<DevelopUgandaCaptionCue, EditText>>()
        val player = VideoView(activity).apply {
            setVideoURI(cleanUri)
            minimumHeight = DevelopUgandaFivemods8Theme.touchMinimumPx * 3
            contentDescription = "Playback for caption review"
        }
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                DevelopUgandaFivemods8Theme.spacingUnitPx,
                DevelopUgandaFivemods8Theme.spacingUnitPx,
                DevelopUgandaFivemods8Theme.spacingUnitPx,
                DevelopUgandaFivemods8Theme.spacingUnitPx,
            )
            addView(player, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                DevelopUgandaFivemods8Theme.touchMinimumPx * 3,
            ))
            addView(TextView(activity).apply {
                text = "REVIEW REQUIRED • WORD CONFIDENCE UNAVAILABLE"
                setTextColor(DevelopUgandaFivemods8Theme.warning)
                DevelopUgandaFivemods8Theme.applyTypeScale(this, 13f)
                contentDescription = text
            })
            source.forEach { cue ->
                val stamp = TextView(activity).apply {
                    text = "PLAY ${formatTime(cue.startMs)}"
                    setTextColor(DevelopUgandaFivemods8Theme.accent)
                    gravity = Gravity.CENTER_VERTICAL
                    isClickable = true
                    isFocusable = true
                    minimumHeight = DevelopUgandaFivemods8Theme.touchMinimumPx
                    contentDescription = "Play caption ${cue.index} from ${formatTime(cue.startMs)}"
                    setOnClickListener {
                        player.seekTo(cue.startMs.toInt())
                        player.start()
                    }
                }
                DevelopUgandaFivemods8Theme.applyTypeScale(stamp, 11f)
                addView(stamp)
                val editor = EditText(activity).apply {
                    setText(cue.text)
                    setTextColor(DevelopUgandaFivemods8Theme.content)
                    setHintTextColor(DevelopUgandaFivemods8Theme.contentDim)
                    setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceRaised)
                    minLines = 1
                    maxLines = 2
                    contentDescription = "Caption ${cue.index} text"
                }
                DevelopUgandaFivemods8Theme.applyTypeScale(editor, 16f)
                addView(editor, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = DevelopUgandaFivemods8Theme.spacingUnitPx })
                rows += cue to editor
            }
        }
        val scroll = ScrollView(activity).apply { addView(body) }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("CONFIRM CAPTIONS • $mode")
            .setView(scroll)
            .setNegativeButton("BRAND WITHOUT CAPTIONS") { _, _ -> onComplete(null) }
            .setPositiveButton("CONFIRM CAPTIONS", null)
            .setOnCancelListener { onComplete(null) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val edited = rows.map { (cue, editor) ->
                    cue.copy(text = editor.text.toString().trim().replace(Regex("\\s+"), " "))
                }
                val fault = validate(edited)
                if (fault != null) {
                    Toast.makeText(activity, fault, Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                DevelopUgandaTranscriptArchive.saveConfirmed(activity, takeId, mode, cleanUri, edited)
                player.stopPlayback()
                dialog.dismiss()
                onComplete(edited.takeIf { burnAllowed("CONFIRMED", it) })
            }
        }
        dialog.setOnDismissListener { player.stopPlayback() }
        dialog.show()
    }

    private fun validate(cues: List<DevelopUgandaCaptionCue>): String? {
        for (cue in cues) {
            if (cue.text.isBlank()) return "CAPTION ${cue.index} IS EMPTY"
            if (cue.endMs <= cue.startMs) return "CAPTION ${cue.index} HAS INVALID SOURCE TIMING"
            if (wrapTwoLines(cue.text) == null) {
                return "CAPTION ${cue.index} EXCEEDS TWO LINES • SHORTEN OR SPLIT IT"
            }
        }
        return null
    }

    fun parseSrt(value: String): List<DevelopUgandaCaptionCue> = value
        .trim()
        .split(Regex("\\r?\\n\\s*\\r?\\n"))
        .mapNotNull { block ->
            val lines = block.lines().filter { it.isNotBlank() }
            if (lines.size < 3) return@mapNotNull null
            val index = lines.first().trim().toIntOrNull() ?: return@mapNotNull null
            val timing = lines[1].split("-->").map { it.trim() }
            if (timing.size != 2) return@mapNotNull null
            val start = parseTime(timing[0]) ?: return@mapNotNull null
            val end = parseTime(timing[1]) ?: return@mapNotNull null
            DevelopUgandaCaptionCue(index, start, end, lines.drop(2).joinToString(" ").trim())
        }

    fun wrapTwoLines(value: String, maxCharacters: Int = 34): String? {
        val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.any { it.length > maxCharacters }) return null
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isBlank()) word else "$line $word"
            if (candidate.length <= maxCharacters) {
                line = candidate
            } else {
                lines += line
                line = word
            }
        }
        if (line.isNotBlank()) lines += line
        return lines.takeIf { it.size in 1..2 }?.joinToString("\n")
    }

    private fun parseTime(value: String): Long? {
        val parts = value.split(':', ',')
        if (parts.size != 4) return null
        val h = parts[0].toLongOrNull() ?: return null
        val m = parts[1].toLongOrNull() ?: return null
        val s = parts[2].toLongOrNull() ?: return null
        val ms = parts[3].toLongOrNull() ?: return null
        return h * 3_600_000L + m * 60_000L + s * 1_000L + ms
    }

    private fun formatTime(ms: Long): String = String.format(
        Locale.US,
        "%02d:%02d.%03d",
        ms / 60_000L,
        (ms % 60_000L) / 1_000L,
        ms % 1_000L,
    )
}

/**
 * Dynamic Media3 text overlay used only by the existing BRAND transform.
 * Empty text outside a confirmed cue means no pixels are added to that frame.
 */
@OptIn(UnstableApi::class)
class DevelopUgandaReviewedCaptionOverlay(
    private val cues: List<DevelopUgandaCaptionCue>,
) : TextOverlay() {
    private val placement: OverlaySettings = StaticOverlaySettings.Builder()
        // Platform UI safe-area measurements were not available in source.
        // Keep reviewed captions in the central lower field, away from edge UI.
        .setBackgroundFrameAnchor(0f, 0.34f)
        .setOverlayFrameAnchor(0f, 0f)
        .setScale(0.70f, 0.70f)
        .build()

    override fun getText(presentationTimeUs: Long): SpannableString {
        val timeMs = presentationTimeUs / 1_000L
        val text = cues.firstOrNull { timeMs in it.startMs until it.endMs }
            ?.text
            ?.let { DevelopUgandaCaptionReview.wrapTwoLines(it) }
            .orEmpty()
        return SpannableString(text).apply {
            if (isEmpty()) return@apply
            setSpan(ForegroundColorSpan(DevelopUgandaFivemods8Theme.content), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(DevelopUgandaCaptionBackingSpan(), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            // 40sp is the existing du_type_40 step; no sixth caption size is introduced.
            setSpan(AbsoluteSizeSpan(40, true), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan("sans-serif-medium"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = placement
}

/** Token-backed rounded line backing used by the existing Media3 text overlay. */
private class DevelopUgandaCaptionBackingSpan : LineBackgroundSpan {
    override fun drawBackground(
        canvas: Canvas,
        paint: Paint,
        left: Int,
        right: Int,
        top: Int,
        baseline: Int,
        bottom: Int,
        text: CharSequence,
        start: Int,
        end: Int,
        lineNumber: Int,
    ) {
        val original = paint.color
        paint.color = DevelopUgandaFivemods8Theme.surface
        val radius = DevelopUgandaFivemods8Theme.radiusPx.toFloat()
        val pad = DevelopUgandaFivemods8Theme.spacingUnitPx.toFloat()
        canvas.drawRoundRect(
            left - pad,
            top.toFloat(),
            right + pad,
            bottom.toFloat(),
            radius,
            radius,
            paint,
        )
        paint.color = original
    }
}
