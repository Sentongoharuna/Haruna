package com.sentongoharuna.pulse

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Presentation
import androidx.media3.exoplayer.ExoPlayer
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
import androidx.media3.ui.PlayerView
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * V265 proxy workflow.
 *
 * The camera master is never uploaded or altered. Media3 creates a separate low-bitrate H.264/AAC
 * review proxy after a successful CameraX finalize event. The LAN director can pull that proxy only
 * while the camera is not recording. Full-quality masters remain on their camera phones.
 */
@OptIn(UnstableApi::class)
object DevelopUgandaV265ProxyManager {
    private const val PREFS = "develop_uganda_v265_proxy"
    @Volatile private var activeTransformer: Transformer? = null

    data class BuildInfo(
        val sourceBase: String,
        val sourceUri: Uri,
        val startedUtc: String,
        val scene: Int,
        val take: Int,
        val cameraName: String
    )

    fun enabled(context: Context): Boolean = prefs(context).getBoolean("proxy_enabled", true)
    fun quality(context: Context): String = prefs(context).getString("proxy_quality", "BALANCED")?.uppercase(Locale.US) ?: "BALANCED"
    fun latestFile(context: Context): File? {
        val path = prefs(context).getString("latest_proxy_path", null) ?: return null
        return File(path).takeIf { it.exists() && it.length() > 0L }
    }
    fun latestMeta(context: Context): String = prefs(context).getString("latest_proxy_meta", "") ?: ""
    fun state(context: Context): String = prefs(context).getString("proxy_state", "IDLE") ?: "IDLE"

    fun createProxy(context: Context, info: BuildInfo) {
        if (!enabled(context)) return
        if (activeTransformer != null) {
            prefs(context).edit().putString("proxy_state", "WAIT • PREVIOUS PROXY ENCODING").apply()
            return
        }

        val q = quality(context)
        val sourceSize = sourceDimensions(context, info.sourceUri)
        val portrait = sourceSize.second > sourceSize.first
        val spec = when (q) {
            "LOW" -> Triple(if (portrait) 360 else 640, if (portrait) 640 else 360, 700_000)
            "HIGH" -> Triple(if (portrait) 720 else 1280, if (portrait) 1280 else 720, 3_000_000)
            else -> Triple(if (portrait) 540 else 960, if (portrait) 960 else 540, 1_500_000)
        }

        val dir = File(context.filesDir, "v265_proxies").apply { mkdirs() }
        val safe = info.sourceBase.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        val out = File(dir, "${safe}_${q}_${System.currentTimeMillis()}.mp4")
        if (out.exists()) out.delete()

        prefs(context).edit().putString("proxy_state", "ENCODING • $q • ${spec.first}×${spec.second}").apply()

        val edited = EditedMediaItem.Builder(MediaItem.fromUri(info.sourceUri))
            .setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        Presentation.createForWidthAndHeight(spec.first, spec.second, Presentation.LAYOUT_SCALE_TO_FIT),
                        Brightness(0.0001f)
                    )
                )
            )
            .build()
        val composition = Composition.Builder(
            listOf(EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited)))
        ).build()

        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder().setBitrate(spec.third).setiFrameIntervalSeconds(2f).build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder().setBitrate(96_000).build()
            )
            .build()

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, result: ExportResult) {
                activeTransformer = null
                if (!out.exists() || out.length() <= 0L) {
                    prefs(context).edit().putString("proxy_state", "ERROR • EMPTY PROXY").apply()
                    return
                }
                val duration = durationMs(out)
                val startedEpoch = runCatching { Instant.parse(info.startedUtc).toEpochMilli() }.getOrDefault(0L)
                val meta = JSONObject().apply {
                    put("protocol", "DU265_PROXY_1")
                    put("sourceBase", info.sourceBase)
                    put("fileName", out.name)
                    put("quality", q)
                    put("width", spec.first)
                    put("height", spec.second)
                    put("videoBitrate", spec.third)
                    put("audioBitrate", 96_000)
                    put("bytes", out.length())
                    put("durationMs", duration)
                    put("startedUtc", info.startedUtc)
                    put("startedEpochMs", startedEpoch)
                    put("createdEpochMs", System.currentTimeMillis())
                    put("scene", info.scene)
                    put("take", info.take)
                    put("camera", info.cameraName)
                }.toString()
                prefs(context).edit()
                    .putString("latest_proxy_path", out.absolutePath)
                    .putString("latest_proxy_meta", meta)
                    .putString("proxy_state", "READY • $q • ${out.length() / 1_048_576L}MB")
                    .apply()
                trimOld(dir, out)
            }

            override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                activeTransformer = null
                out.delete()
                prefs(context).edit().putString("proxy_state", "ERROR • ${exception.errorCodeName}").apply()
            }
        }

        activeTransformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()
            .also { it.start(composition, out.absolutePath) }
    }

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun sourceDimensions(context: Context, uri: Uri): Pair<Int, Int> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            w to h
        } catch (_: Exception) {
            1920 to 1080
        } finally {
            runCatching { r.release() }
        }
    }

    private fun durationMs(file: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { r.release() }
        }
    }

    private fun trimOld(dir: File, keep: File) {
        dir.listFiles()?.filter { it.extension.equals("mp4", true) && it != keep }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(11)
            ?.forEach { runCatching { it.delete() } }
    }
}

class DevelopUgandaV265ProxySyncReviewActivity : AppCompatActivity() {
    private val navy = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val line = DevelopUgandaFivemods8Theme.outline
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val gold = DevelopUgandaFivemods8Theme.accent
    private val green = DevelopUgandaFivemods8Theme.accent
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val red = DevelopUgandaFivemods8Theme.record

    private data class ProxySlot(
        val index: Int,
        val name: String,
        val host: EditText,
        val pin: EditText,
        val status: TextView,
        val progress: ProgressBar,
        var file: File? = null,
        var meta: JSONObject? = null,
        var manualOffsetMs: Long = 0L
    )

    private val io = Executors.newFixedThreadPool(3)
    private val handler = Handler(Looper.getMainLooper())
    private val slots = mutableListOf<ProxySlot>()
    private lateinit var overall: TextView
    private lateinit var playerView: PlayerView
    private lateinit var reviewStatus: TextView
    private lateinit var notes: EditText
    private var player: ExoPlayer? = null
    private var selected = 0
    private var alive = false

    private val multiPrefs by lazy { duSharedPreferences("develop_uganda_v263_multicam", Context.MODE_PRIVATE) }
    private val prefs by lazy { duSharedPreferences("develop_uganda_v265_proxy", Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        alive = true
        if (prefs.getBoolean("auto_transfer", true)) handler.postDelayed({ pullReadyProxies() }, 900L)
    }

    override fun onPause() {
        alive = false
        super.onPause()
    }

    override fun onDestroy() {
        alive = false
        player?.release()
        player = null
        io.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(24))
            setBackgroundColor(navy)
        }
        page.addView(label("V265 • PROXY SYNC + REVIEW", 22f, white, true))
        page.addView(label("LOW-BANDWIDTH REVIEW COPIES • FULL MASTERS STAY ON CAMERA PHONES", 9f, gold, true).apply { setPadding(0, dp(3), 0, dp(10)) })

        page.addView(card().apply {
            overall = label("0/4 PROXIES • STANDBY", 10.5f, white, true).apply { typeface = Typeface.MONOSPACE }
            addView(overall)
            val row = LinearLayout(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("PULL READY PROXIES", cyan) { pullReadyProxies() }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(5) })
                addView(action("REFRESH", gold) { refreshAllMeta() }, LinearLayout.LayoutParams(0, dp(46), 1f))
            }
            addView(row)
            addView(label("Transfers pause when a camera reports RECORDING. Local master recording always has priority.", 8.2f, muted, false).apply { setPadding(0, dp(6), 0, 0) })
        })

        page.addView(label("CAMERA PROXY QUEUE", 10f, gold, true).apply { setPadding(dp(2), dp(7), 0, dp(5)) })
        for (i in 0 until 4) page.addView(buildSlot(i))

        page.addView(card().apply {
            addView(label("SYNCED ANGLE REVIEW", 11f, cyan, true))
            playerView = PlayerView(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
                useController = true
            }
            addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply { topMargin = dp(6) })
            reviewStatus = label("NO LOCAL PROXY SELECTED", 9f, white, true).apply { typeface = Typeface.MONOSPACE; setPadding(0, dp(6), 0, dp(5)) }
            addView(reviewStatus)
            val angles = LinearLayout(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                for (i in 0 until 4) addView(action("CAM ${('A'.code + i).toChar()}", if (i == 0) gold else line) { switchAngle(i) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (i < 3) rightMargin = dp(3) })
            }
            addView(angles)
            val nudge = LinearLayout(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("−20ms", line) { nudgeSelected(-20L) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(3) })
                addView(action("RESET SYNC", cyan) { resetSelectedOffset() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(3) })
                addView(action("+20ms", line) { nudgeSelected(20L) }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            addView(nudge)
        })

        page.addView(card().apply {
            addView(label("TAKE RATING + NOTES", 11f, cyan, true))
            val ratings = LinearLayout(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("BEST", "KEEP", "RETAKE", "REJECT").forEachIndexed { idx, rating ->
                    addView(action(rating, if (rating == "BEST") green else line) { saveRating(rating) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (idx < 3) rightMargin = dp(3) })
                }
            }
            addView(ratings)
            notes = EditText(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                hint = "Director notes for this Scene / Take"
                setHintTextColor(muted)
                setTextColor(white)
                textSize = 10f
                minLines = 2
                background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(85), line, 10, 1)
                setPadding(dp(10), dp(8), dp(10), dp(8))
            }
            addView(notes, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
            addView(action("SAVE TAKE NOTES", gold) { saveNotes() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(5) })
        })

        page.addView(card().apply {
            addView(label("V265 REVIEW POLICY", 10f, gold, true))
            addView(label("V265 creates separate H.264/AAC review proxies. Angle switching keeps a shared review timeline using each proxy's recording start time plus manual offset. It is post-recording review sync, not hardware genlock. Masters are never transferred, deleted or changed by this page.", 8.6f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
        })

        val scroll = ScrollView(this).apply { isFillViewport = false; addView(page) }
        return scroll
    }

    private fun buildSlot(index: Int): View {
        val name = "CAM ${('A'.code + index).toChar()}"
        val host = edit("IP", multiPrefs.getString("slot_${index}_host", "") ?: "")
        val pin = edit("PIN", multiPrefs.getString("slot_${index}_pin", "") ?: "")
        val status = label("NO PROXY CHECKED", 8.5f, muted, false).apply { typeface = Typeface.MONOSPACE; maxLines = 4 }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0 }
        val slot = ProxySlot(index, name, host, pin, status, progress)
        slots.add(slot)
        restoreLocal(slot)

        return card().apply {
            addView(label(name, 10.5f, white, true))
            val row = LinearLayout(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(host, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(4) })
                addView(pin, LinearLayout.LayoutParams(0, dp(38), 0.55f))
            }
            addView(row)
            addView(status)
            addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).apply { topMargin = dp(4) })
            val controls = LinearLayout(this@DevelopUgandaV265ProxySyncReviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("CHECK", line) { checkMeta(slot) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(3) })
                addView(action("PULL", cyan) { pullOne(slot) }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(3) })
                addView(action("REVIEW", gold) { switchAngle(index) }, LinearLayout.LayoutParams(0, dp(38), 1f))
            }
            addView(controls)
        }
    }

    private fun refreshAllMeta() = slots.forEach { checkMeta(it) }

    private fun pullReadyProxies() {
        slots.forEach { slot ->
            saveConnection(slot)
            val host = slot.host.text.toString().trim()
            val pin = slot.pin.text.toString().trim()
            if (host.isBlank() || pin.length < 4) return@forEach
            io.execute {
                val status = requestText(host, pin, "STATUS")
                if (status?.contains("RECORDING • ON") == true) {
                    runOnUiThread { slot.status.text = "WAIT • CAMERA RECORDING • MASTER PRIORITY" }
                    return@execute
                }
                val metaText = requestText(host, pin, "PROXY_META")
                if (metaText.isNullOrBlank() || !metaText.trim().startsWith("{")) {
                    runOnUiThread { slot.status.text = metaText ?: "NO PROXY READY"; updateOverall() }
                    return@execute
                }
                val remote = runCatching { JSONObject(metaText) }.getOrNull() ?: return@execute
                val currentName = slot.meta?.optString("fileName", "") ?: ""
                if (slot.file?.exists() == true && currentName == remote.optString("fileName")) {
                    runOnUiThread { slot.status.text = compactMeta(remote, true); updateOverall() }
                } else {
                    runOnUiThread { slot.status.text = "QUEUE • ${remote.optString("fileName")}" }
                    pullOne(slot, remote)
                }
            }
        }
    }

    private fun checkMeta(slot: ProxySlot) {
        saveConnection(slot)
        val host = slot.host.text.toString().trim()
        val pin = slot.pin.text.toString().trim()
        if (host.isBlank() || pin.length < 4) { slot.status.text = "ENTER IP + PIN"; return }
        io.execute {
            val text = requestText(host, pin, "PROXY_META")
            val meta = text?.let { runCatching { JSONObject(it) }.getOrNull() }
            runOnUiThread {
                slot.status.text = if (meta != null) compactMeta(meta, false) else (text ?: "OFFLINE")
                updateOverall()
            }
        }
    }

    private fun pullOne(slot: ProxySlot, knownMeta: JSONObject? = null) {
        saveConnection(slot)
        val host = slot.host.text.toString().trim()
        val pin = slot.pin.text.toString().trim()
        if (host.isBlank() || pin.length < 4) { slot.status.text = "ENTER IP + PIN"; return }
        io.execute {
            val status = requestText(host, pin, "STATUS")
            if (status?.contains("RECORDING • ON") == true) {
                runOnUiThread { slot.status.text = "WAIT • CAMERA RECORDING • MASTER PRIORITY" }
                return@execute
            }
            val meta = knownMeta ?: requestText(host, pin, "PROXY_META")?.let { runCatching { JSONObject(it) }.getOrNull() }
            if (meta == null) { runOnUiThread { slot.status.text = "NO PROXY READY" }; return@execute }
            val dir = File(filesDir, "v265_received").apply { mkdirs() }
            val remoteName = meta.optString("fileName", "proxy_${slot.name.replace(" ", "_")}.mp4")
            val out = File(dir, "${slot.name.replace(" ", "_")}_${remoteName}")
            val ok = downloadProxy(host, pin, out) { percent -> runOnUiThread { slot.progress.progress = percent } }
            runOnUiThread {
                val integrityOk = !prefs.getBoolean("proxy_integrity", true) || verify(out)
                if (ok && integrityOk) {
                    slot.file = out
                    slot.meta = meta
                    persistLocal(slot)
                    slot.progress.progress = 100
                    slot.status.text = compactMeta(meta, true)
                    updateOverall()
                    if (selected == slot.index) switchAngle(slot.index)
                } else {
                    out.delete()
                    slot.status.text = "TRANSFER / INTEGRITY ERROR"
                    slot.progress.progress = 0
                }
            }
        }
    }

    private fun switchAngle(index: Int) {
        val target = slots.getOrNull(index) ?: return
        val file = target.file?.takeIf { it.exists() } ?: run { target.status.text = "PULL PROXY FIRST"; return }
        val old = player
        val commonPosition = if (old != null) old.currentPosition + slotTimelineOffset(slots.getOrNull(selected)) else 0L
        selected = index
        val targetPosition = max(0L, commonPosition - slotTimelineOffset(target))
        if (player == null) {
            player = ExoPlayer.Builder(this).build().also { playerView.player = it }
        }
        player?.apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            seekTo(targetPosition)
            playWhenReady = old?.playWhenReady ?: false
        }
        if (prefs.getBoolean("review_motion", true)) {
            playerView.animate().alpha(0.82f).scaleX(0.992f).scaleY(0.992f).setDuration(80L).withEndAction {
                playerView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(130L).start()
            }.start()
        }
        val m = target.meta
        reviewStatus.text = "${target.name} • ${m?.optString("quality", "--") ?: "--"} • OFFSET ${signed(slotTimelineOffset(target))}ms • ${takeLabel(target)}"
        loadTakeNotes(target)
    }

    private fun slotTimelineOffset(slot: ProxySlot?): Long {
        if (slot == null) return 0L
        val starts = slots.mapNotNull { it.meta?.optLong("startedEpochMs")?.takeIf { v -> v > 0L } }
        val base = starts.minOrNull() ?: 0L
        val start = slot.meta?.optLong("startedEpochMs") ?: base
        return (start - base) + slot.manualOffsetMs
    }

    private fun nudgeSelected(delta: Long) {
        val slot = slots.getOrNull(selected) ?: return
        slot.manualOffsetMs = (slot.manualOffsetMs + delta).coerceIn(-2000L, 2000L)
        prefs.edit().putLong("slot_${selected}_manual_offset", slot.manualOffsetMs).apply()
        reviewStatus.text = "${slot.name} • MANUAL SYNC ${signed(slot.manualOffsetMs)}ms"
    }

    private fun resetSelectedOffset() {
        val slot = slots.getOrNull(selected) ?: return
        slot.manualOffsetMs = 0L
        prefs.edit().remove("slot_${selected}_manual_offset").apply()
        reviewStatus.text = "${slot.name} • MANUAL SYNC RESET"
    }

    private fun saveRating(rating: String) {
        val key = currentTakeKey() ?: return
        prefs.edit().putString("rating_$key", rating).apply()
        reviewStatus.text = "${slots[selected].name} • $key • $rating"
    }

    private fun saveNotes() {
        val key = currentTakeKey() ?: return
        prefs.edit().putString("notes_$key", notes.text.toString().trim()).apply()
        reviewStatus.text = "NOTES SAVED • $key"
    }

    private fun loadTakeNotes(slot: ProxySlot) {
        val scene = slot.meta?.optInt("scene", 0) ?: 0
        val take = slot.meta?.optInt("take", 0) ?: 0
        if (scene <= 0 || take <= 0) return
        val key = "S${scene}_T${take}"
        notes.setText(prefs.getString("notes_$key", "") ?: "")
    }

    private fun currentTakeKey(): String? {
        val slot = slots.getOrNull(selected) ?: return null
        val scene = slot.meta?.optInt("scene", 0) ?: 0
        val take = slot.meta?.optInt("take", 0) ?: 0
        return if (scene > 0 && take > 0) "S${scene}_T${take}" else null
    }

    private fun takeLabel(slot: ProxySlot): String {
        val s = slot.meta?.optInt("scene", 0) ?: 0
        val t = slot.meta?.optInt("take", 0) ?: 0
        return if (s > 0 && t > 0) "S%03d/T%03d".format(Locale.US, s, t) else "TAKE --"
    }

    private fun updateOverall() {
        if (!::overall.isInitialized) return
        val ready = slots.count { it.file?.exists() == true }
        val takeGroups = slots.mapNotNull { s -> s.meta?.let { "${it.optInt("scene", 0)}:${it.optInt("take", 0)}" } }.filter { it != "0:0" }.distinct().size
        val missing = slots.count { it.host.text.toString().isNotBlank() && it.file?.exists() != true }
        overall.text = "$ready/${slots.size} PROXIES • $takeGroups TAKE GROUPS • $missing MISSING"
    }

    private fun compactMeta(meta: JSONObject, local: Boolean): String {
        val mb = meta.optLong("bytes", 0L).toDouble() / 1_048_576.0
        val duration = meta.optLong("durationMs", 0L) / 1000L
        val prefix = if (local) "LOCAL READY" else "REMOTE READY"
        return String.format(Locale.US, "%s • %s • %.1fMB • %ds • %s", prefix, meta.optString("quality", "--"), mb, duration, takeLabelFromMeta(meta))
    }

    private fun takeLabelFromMeta(meta: JSONObject): String {
        val s = meta.optInt("scene", 0); val t = meta.optInt("take", 0)
        return if (s > 0 && t > 0) "S%03d/T%03d".format(Locale.US, s, t) else "TAKE --"
    }

    private fun requestText(host: String, pin: String, action: String): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, DevelopUgandaV262Network.PORT), 2200)
                socket.soTimeout = 4500
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                out.writeUTF(DevelopUgandaV262Network.MAGIC)
                out.writeUTF(pin)
                out.writeUTF(action)
                out.flush()
                when (input.readUTF()) {
                    "TEXT" -> input.readUTF()
                    else -> null
                }
            }
        } catch (_: Exception) { null }
    }

    private fun downloadProxy(host: String, pin: String, destination: File, onProgress: (Int) -> Unit): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, DevelopUgandaV262Network.PORT), 2500)
                socket.soTimeout = 20_000
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                out.writeUTF(DevelopUgandaV262Network.MAGIC)
                out.writeUTF(pin)
                out.writeUTF("PROXY")
                out.flush()
                val type = input.readUTF()
                if (type == "TEXT") return false
                if (type != "FILE") return false
                val total = input.readLong()
                if (total <= 0L || total > 2_000_000_000L) return false
                FileOutputStream(destination).use { fos ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (done < total) {
                        val need = minOf(buffer.size.toLong(), total - done).toInt()
                        val n = input.read(buffer, 0, need)
                        if (n <= 0) break
                        fos.write(buffer, 0, n)
                        done += n
                        onProgress(((done * 100L) / total).toInt().coerceIn(0, 100))
                    }
                    fos.flush()
                    done == total
                }
            }
        } catch (_: Exception) { false }
    }

    private fun verify(file: File): Boolean {
        if (!file.exists() || file.length() < 16_384L) return false
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(file.absolutePath)
            val duration = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            duration > 0L
        } catch (_: Exception) { false } finally { runCatching { r.release() } }
    }

    private fun persistLocal(slot: ProxySlot) {
        prefs.edit()
            .putString("slot_${slot.index}_file", slot.file?.absolutePath)
            .putString("slot_${slot.index}_meta", slot.meta?.toString())
            .apply()
    }

    private fun restoreLocal(slot: ProxySlot) {
        val path = prefs.getString("slot_${slot.index}_file", null)
        slot.file = path?.let { File(it).takeIf(File::exists) }
        slot.meta = prefs.getString("slot_${slot.index}_meta", null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        slot.manualOffsetMs = prefs.getLong("slot_${slot.index}_manual_offset", 0L)
        if (slot.file != null && slot.meta != null) {
            slot.status.text = compactMeta(slot.meta!!, true)
            slot.progress.progress = 100
        }
    }

    private fun saveConnection(slot: ProxySlot) {
        multiPrefs.edit()
            .putString("slot_${slot.index}_host", slot.host.text.toString().trim())
            .putString("slot_${slot.index}_pin", slot.pin.text.toString().trim())
            .apply()
    }

    private fun signed(value: Long): String = if (value >= 0L) "+$value" else value.toString()

    private fun label(textValue: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = textValue; textSize = size; setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun edit(hintValue: String, value: String) = EditText(this).apply {
        hint = hintValue; setText(value); textSize = 9f; setTextColor(white); setHintTextColor(muted); setSingleLine(true)
        background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(85), line, 10, 1); setPadding(dp(8), 0, dp(8), 0)
    }

    private fun action(title: String, stroke: Int, block: () -> Unit) = Button(this).apply {
        text = title; textSize = 8.5f; setTextColor(white); isAllCaps = true; background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(68), stroke, 11, 1)
        setOnClickListener { view ->
            animate().scaleX(0.97f).scaleY(0.97f).setDuration(70L).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                DevelopUgandaV28012SafeActions.run(view.context, "PROXY REVIEW BUTTON") { block() }
            }.start()
        }
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10)); background = rounded(panel, line, 15, 1)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(7) }
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int, strokeDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; setColor(fill); cornerRadius = dp(radiusDp).toFloat(); if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
