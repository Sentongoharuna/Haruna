package com.sentongoharuna.pulse

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.SystemClock
import android.provider.MediaStore
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pedro.common.ConnectChecker
import com.pedro.common.StreamingStatsReport
import com.pedro.common.Throughput
import com.pedro.library.base.recording.RecordController
import com.pedro.library.rtmp.RtmpStream
import com.pedro.library.util.QueueAwareBitrateAdapter
import java.io.File
import java.security.KeyStore
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties

data class DevelopUgandaStreamConfig(val endpoint: String, val key: String)

/** Stream keys are encrypted by Android Keystore and are never returned to UI. */
object DevelopUgandaSecureStreamConfig {
    private const val PREFS = "develop_uganda_secure_stream"
    private const val ALIAS = "develop_uganda_rtmps_stream_key_v1"

    fun save(context: Context, endpoint: String, key: String) {
        val cleanEndpoint = endpoint.trim().trimEnd('/')
        require(cleanEndpoint.startsWith("rtmps://", ignoreCase = true)) {
            "RTMPS endpoint required"
        }
        require(!cleanEndpoint.contains('\n') && !cleanEndpoint.contains('\r')) {
            "Invalid RTMPS endpoint"
        }
        require(key.isNotBlank() && !key.contains('\n') && !key.contains('\r')) {
            "Stream key required"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(key.toByteArray(Charsets.UTF_8))
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("endpoint", cleanEndpoint)
            .putString("key_iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("key_ciphertext", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .apply()
    }

    fun load(context: Context): DevelopUgandaStreamConfig? = runCatching {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val endpoint = prefs.getString("endpoint", null)?.takeIf {
            it.startsWith("rtmps://", ignoreCase = true)
        } ?: return@runCatching null
        val iv = Base64.decode(prefs.getString("key_iv", null) ?: return@runCatching null, Base64.NO_WRAP)
        val encrypted = Base64.decode(
            prefs.getString("key_ciphertext", null) ?: return@runCatching null,
            Base64.NO_WRAP,
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        val key = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        DevelopUgandaStreamConfig(endpoint, key.takeIf { it.isNotBlank() } ?: return@runCatching null)
    }.getOrNull()

    fun endpointLabel(context: Context): String =
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("endpoint", null)
            ?.let { Uri.parse(it).host }
            ?.takeIf { it.isNotBlank() }
            ?: "NOT SET"

    fun clear(context: Context) {
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    /** One-way security migration from the legacy plain SharedPreferences key. */
    fun migrateLegacyPlaintextKey(context: Context) {
        val legacy = context.duSharedPreferences("develop_uganda_stream", Context.MODE_PRIVATE)
        if (!legacy.contains("key")) return
        val endpoint = legacy.getString("url", "").orEmpty().trim()
        val plaintext = legacy.getString("key", "").orEmpty()
        if (endpoint.startsWith("rtmps://", ignoreCase = true) && plaintext.isNotBlank()) {
            runCatching { save(context, endpoint, plaintext) }
        }
        // Whether migration succeeds or not, the insecure copy must not remain.
        legacy.edit().remove("key").apply()
    }

    fun redactedFailure(value: String?): String = value.orEmpty()
        .replace(Regex("(?i)rtmps?://\\S+"), "RTMPS_ENDPOINT")
        .replace(Regex("(?i)(key|token|secret)=\\S+"), "$1=REDACTED")
        .take(180)
        .ifBlank { "UNKNOWN CONNECTION ERROR" }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }
}

/**
 * LIVE-only RTMPS camera. RootEncoder is used because it exposes the encoded
 * stream queue, real transmitted bitrate and dropped-frame counters while also
 * muxing an always-on local CLEAN backup. Its logging is disabled before any
 * endpoint is supplied. The other four CameraX modes are not touched.
 */
class DevelopUgandaRtmpsLiveActivity : AppCompatActivity(), ConnectChecker, SurfaceHolder.Callback {
    private data class Tier(val label: String, val width: Int, val height: Int, val bitrate: Int)

    private val tiers = listOf(
        Tier("720P", 1280, 720, 2_500_000),
        Tier("540P", 960, 540, 1_500_000),
        Tier("480P", 854, 480, 900_000),
    )
    private val audioBitrate = 128_000
    private lateinit var root: FrameLayout
    private lateinit var preview: SurfaceView
    private lateinit var stateView: TextView
    private lateinit var detailView: TextView
    private lateinit var bitrateView: DevelopUgandaLiveMetricTextView
    private lateinit var droppedView: DevelopUgandaLiveMetricTextView
    private lateinit var startButton: TextView
    private lateinit var setupButton: TextView
    private lateinit var stopButton: TextView
    private lateinit var markButton: TextView
    private var stream: RtmpStream? = null
    private var prepared = false
    private var broadcasting = false
    private var adapting = false
    private var tierIndex = 0
    private var congestedReports = 0
    private var lastReport: StreamingStatsReport? = null
    private var cumulativeDropped = 0L
    private var segmentIndex = 0
    private var segmentStartedElapsed = 0L
    private var localFile: File? = null
    private var protectedTake: DevelopUgandaCrashSafeTake? = null
    private var takeId = ""
    private var configuration: DevelopUgandaStreamConfig? = null
    private lateinit var adapter: QueueAwareBitrateAdapter
    private var reconnectAttempt = 0
    private var f12ProtectionStopRequested = false
    private var f12LastAudioRouteCheckMs = 0L
    private var f12AudioRouteStopped = false
    private var f12SafetyOverrideApprovedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopUgandaSecureStreamConfig.migrateLegacyPlaintextKey(this)
        buildUi()
        configuration = DevelopUgandaSecureStreamConfig.load(this)
        refreshConfigurationState()
        val recovery = DevelopUgandaCrashSafeTake.recoverOnLaunch(this)
        if (recovery.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("INTERRUPTED LIVE BACKUP")
                .setMessage(recovery.joinToString("\n") { it.message } + "\n\nRAW FRAGMENTS PRESERVED")
                .setPositiveButton("ACKNOWLEDGE", null)
                .show()
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(DevelopUgandaFivemods8Theme.surface) }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
        }
        preview = SurfaceView(this).apply {
            holder.addCallback(this@DevelopUgandaRtmpsLiveActivity)
            contentDescription = "LIVE RTMPS camera preview"
        }
        body.addView(preview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ))

        val console = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(unit(), unit(), unit(), unit())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(DevelopUgandaFivemods8Theme.surface)
                cornerRadius = DevelopUgandaFivemods8Theme.radiusPx.toFloat()
                setStroke(maxOf(1, resources.displayMetrics.density.toInt()), DevelopUgandaFivemods8Theme.outline)
            }
        }
        stateView = label("${DevelopUgandaFivemods12Identity.forPage(DevelopUgandaCameraPage.LIVE).badge} • RTMPS DISCONNECTED", 20f, DevelopUgandaFivemods8Theme.content)
        detailView = label("DESTINATION • NOT SET", 11f, DevelopUgandaFivemods8Theme.contentDim)
        console.addView(stateView)
        console.addView(detailView)
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bitrateView = metric("UPLINK\nUNKNOWN")
        droppedView = metric("DROPPED\nUNKNOWN")
        metrics.addView(bitrateView, LinearLayout.LayoutParams(0, unit() * 7, 1f))
        metrics.addView(droppedView, LinearLayout.LayoutParams(0, unit() * 7, 1f))
        console.addView(metrics)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        setupButton = action("SET OUTPUT", DevelopUgandaButtonWeight.SECONDARY) { showSetup() }
        startButton = action("START LIVE", DevelopUgandaButtonWeight.PRIMARY) { startBroadcast() }
        stopButton = action("STOP", DevelopUgandaButtonWeight.DESTRUCTIVE) { confirmStop() }.apply {
            isEnabled = false
            alpha = 0.45f
        }
        actions.addView(setupButton, LinearLayout.LayoutParams(0, unit() * 6, 1f))
        actions.addView(startButton, LinearLayout.LayoutParams(0, unit() * 6, 1f).apply {
            marginStart = unit()
            marginEnd = unit()
        })
        actions.addView(stopButton, LinearLayout.LayoutParams(0, unit() * 6, 1f))
        console.addView(actions)
        markButton = action("MARK • LIVE REQUIRED", DevelopUgandaButtonWeight.SECONDARY) { addMark() }.apply {
            isEnabled = false
            alpha = 0.45f
        }
        console.addView(
            markButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, unit() * 6).apply {
                topMargin = unit()
            },
        )
        body.addView(console, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { setMargins(unit(), unit(), unit(), unit()) })
        root.addView(body)
        setContentView(root)
    }

    private fun label(value: String, step: Float, color: Int) = TextView(this).apply {
        text = value
        setTextColor(color)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 2
        contentDescription = value.replace('\n', ' ')
        DevelopUgandaFivemods8Theme.applyTypeScale(this, step)
    }

    private fun metric(value: String) = DevelopUgandaLiveMetricTextView(this).apply {
        text = value
        setTextColor(DevelopUgandaFivemods8Theme.contentDim)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        contentDescription = value.replace('\n', ' ')
        DevelopUgandaFivemods8Theme.applyTypeScale(this, 16f)
    }

    private fun action(value: String, weight: DevelopUgandaButtonWeight, action: () -> Unit) =
        TextView(this).apply {
            text = value
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            contentDescription = value
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 13f)
            DevelopUgandaBroadcastPresentation.styleButton(
                this,
                weight,
                DevelopUgandaModeProfiles.forPage(DevelopUgandaCameraPage.LIVE).chromeAccentColor,
            )
            setOnClickListener { action() }
        }

    private fun showSetup() {
        if (broadcasting) {
            rejected("STOP LIVE BEFORE CHANGING OUTPUT")
            return
        }
        val endpoint = EditText(this).apply {
            hint = "rtmps://server/app"
            setText(configuration?.endpoint.orEmpty())
            setSingleLine(true)
            contentDescription = "RTMPS server endpoint"
        }
        val key = EditText(this).apply {
            hint = if (configuration != null) "Saved key • enter to replace" else "Stream key"
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            contentDescription = "Stream key. Existing key is never displayed."
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(unit(), unit(), unit(), unit())
            addView(endpoint)
            addView(key)
        }
        AlertDialog.Builder(this)
            .setTitle("SECURE RTMPS OUTPUT")
            .setMessage("The stream key is encrypted with Android Keystore. It is never displayed, logged, exported or added to crash reports.")
            .setView(box)
            .setNegativeButton("CANCEL", null)
            .setNeutralButton("CLEAR") { _, _ ->
                DevelopUgandaSecureStreamConfig.clear(this)
                configuration = null
                refreshConfigurationState()
            }
            .setPositiveButton("SAVE", null)
            .create()
            .also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val old = configuration
                        val entered = key.text.toString()
                        val keyToSave = entered.takeIf { it.isNotBlank() } ?: old?.key
                        runCatching {
                            DevelopUgandaSecureStreamConfig.save(
                                this,
                                endpoint.text.toString(),
                                requireNotNull(keyToSave) { "Stream key required" },
                            )
                        }.onSuccess {
                            configuration = DevelopUgandaSecureStreamConfig.load(this)
                            key.text?.clear()
                            dialog.dismiss()
                            refreshConfigurationState()
                        }.onFailure { rejected(it.message ?: "OUTPUT CONFIGURATION INVALID") }
                    }
                }
                dialog.show()
            }
    }

    private fun refreshConfigurationState() {
        detailView.text = "DESTINATION • ${DevelopUgandaSecureStreamConfig.endpointLabel(this)} • KEY ${if (configuration == null) "ABSENT" else "SECURED"}"
        detailView.contentDescription = detailView.text
    }

    private fun startBroadcast() {
        if (broadcasting) return
        val config = configuration ?: run {
            rejected("NO STREAM KEY • SET OUTPUT")
            return
        }
        val networkFailure = validatedNetworkFailure()
        if (networkFailure != null) {
            rejected(networkFailure)
            return
        }
        val fieldPreflight = DevelopUgandaFivemods12Preflight.snapshot(this, DevelopUgandaCameraPage.LIVE)
        if (!fieldPreflight.mayRecord) {
            rejected(fieldPreflight.items.filter { it.state == DevelopUgandaFivemods12Availability.UNAVAILABLE }.joinToString(" • ") { "${it.label}: ${it.reason}" })
            return
        }
        val preflight = DevelopUgandaV276RecordingSafety.recordingPreflight(
            this,
            DevelopUgandaCameraPage.LIVE,
        )
        if (!preflight.mayStart) {
            f12SafetyOverrideApprovedOnce = false
            rejected(preflight.blocked.joinToString(" • "))
            return
        }
        if (preflight.needsOperatorOverride && !f12SafetyOverrideApprovedOnce) {
            val overrideDialog = AlertDialog.Builder(this)
                .setTitle("LIVE PREFLIGHT • WARNING")
                .setMessage(
                    preflight.overridable.joinToString("\n") { "• $it" } +
                        "\n\nThis is measured but not a critical stop. START ANYWAY records this override in the take sidecar.",
                )
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("START ANYWAY") { _, _ ->
                    f12SafetyOverrideApprovedOnce = true
                    startBroadcast()
                }
                .create()
            DevelopUgandaDialogStyler.show(overrideDialog, DevelopUgandaCameraPage.LIVE)
            return
        }
        val proceededWarnings = buildList {
            if (f12SafetyOverrideApprovedOnce) {
                preflight.overridable.forEach { add("$it • OPERATOR START ANYWAY") }
            }
            fieldPreflight.items
                .filter {
                    it.state == DevelopUgandaFivemods12Availability.LIMITED ||
                        it.state == DevelopUgandaFivemods12Availability.UNKNOWN
                }
                .forEach { add("${it.label} • ${it.stateLabel} • ${it.reason}") }
            addAll(preflight.warnings)
        }.distinct()
        f12SafetyOverrideApprovedOnce = false
        if (proceededWarnings.isNotEmpty()) {
            DevelopUgandaV276RecordingSafety.addEvent(
                this,
                "PREFLIGHT WARNING PROCEEDED • ${proceededWarnings.joinToString(" • ")}",
            )
        }
        if (!prepared && !prepareStream()) {
            rejected("ENCODER PREPARE FAILED")
            return
        }
        takeId = DevelopUgandaFivemods12Identity.prefixedStem(
            DevelopUgandaCameraPage.LIVE,
            "RTMPS_${System.currentTimeMillis()}",
        )
        protectedTake = runCatching {
            DevelopUgandaCrashSafeTake.begin(
                this,
                takeId,
                DevelopUgandaCameraPage.LIVE,
                proceededWarnings,
            )
        }.getOrNull()
        if (protectedTake == null || !startLocalSegment()) {
            rejected("LOCAL CLEAN BACKUP NOT READY • LIVE REFUSED")
            return
        }
        broadcasting = true
        f12ProtectionStopRequested = false
        f12AudioRouteStopped = false
        f12LastAudioRouteCheckMs = 0L
        DevelopUgandaV272FieldSoundContinuity.armAudioLock(this)
        reconnectAttempt = 0
        adapting = false
        congestedReports = 0
        adapter.reset()
        setControls(active = true)
        state("CONNECTING • LOCAL CLEAN RECORDING", DevelopUgandaFivemods8Theme.warning)
        val target = config.endpoint.trimEnd('/') + "/" + config.key.trimStart('/')
        runCatching { stream?.startStream(target) }
            .onFailure {
                stopBroadcast("DISCONNECTED • ${DevelopUgandaSecureStreamConfig.redactedFailure(it.message)}")
            }
    }

    private fun prepareStream(): Boolean {
        releaseStream()
        val tier = tiers[tierIndex]
        val candidate = RtmpStream(this, this)
        candidate.getStreamClient().setLogs(false)
        candidate.getStreamClient().setReTries(3)
        candidate.getStreamClient().setCheckServerAlive(true)
        adapter = QueueAwareBitrateAdapter(
            tier.bitrate + audioBitrate,
            (tier.bitrate / 3).coerceAtLeast(400_000),
        ) { wireBitrate ->
            val video = (wireBitrate - audioBitrate).coerceAtLeast(300_000)
            runCatching { candidate.setVideoBitrateOnFly(video) }
        }
        prepared = runCatching {
            candidate.prepareVideo(
                tier.width,
                tier.height,
                tier.bitrate,
                30,
                2,
                0,
                -1,
                -1,
                1920,
                1080,
                8_000_000,
            ) && candidate.prepareAudio(44_100, true, audioBitrate, true, true)
        }.getOrDefault(false)
        if (!prepared) {
            candidate.release()
            return false
        }
        stream = candidate
        if (preview.holder.surface?.isValid == true) candidate.startPreview(preview)
        return true
    }

    private fun startLocalSegment(): Boolean {
        val active = stream ?: return false
        segmentIndex += 1
        val directory = File(
            getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            "develop.uganda/LiveRaw",
        ).apply { mkdirs() }
        val file = File(directory, String.format(java.util.Locale.US, "%s_S%04d_CLEAN.mp4", takeId, segmentIndex))
        localFile = file
        protectedTake?.onSegmentStarting(file.nameWithoutExtension, file.absolutePath)
        segmentStartedElapsed = SystemClock.elapsedRealtime()
        f12LastAudioRouteCheckMs = 0L
        return runCatching {
            active.startRecord(
                file.absolutePath,
                RecordController.RecordTracks.ALL,
                RecordController.Listener { status ->
                    runOnUiThread {
                        if (status == RecordController.Status.RECORDING) {
                            stateView.text = if (active.isStreaming) "LIVE • ${tiers[tierIndex].label}" else "CONNECTING • LOCAL CLEAN RECORDING"
                        }
                    }
                },
            )
            DevelopUgandaV276RecordingSafety.onRecordingStarted(
                this,
                file.nameWithoutExtension,
                "CLEAN",
                "LIVE RTMPS",
                "LIVE",
                1,
                segmentIndex,
            )
            true
        }.getOrDefault(false)
    }

    private fun rotateLocalSegment() {
        if (!broadcasting || localFile == null) return
        stopLocalSegment(interrupted = false)
        if (!startLocalSegment()) {
            state("LOCAL BACKUP FAILED • STREAM STOPPED", DevelopUgandaFivemods8Theme.record)
            stream?.stopStream()
            broadcasting = false
            setControls(active = false)
        }
    }

    private fun stopLocalSegment(interrupted: Boolean) {
        val active = stream
        val file = localFile ?: return
        runCatching { if (active?.isRecording == true) active.stopRecord() }
        val durationMs = mediaDuration(file)
        val bytes = file.length().coerceAtLeast(0L)
        val uri = publishBackup(file)
        protectedTake?.onSegmentFinalized(uri, durationMs, bytes, uri == null || interrupted)
        DevelopUgandaV276RecordingSafety.onRecordingFinalized(
            this,
            uri?.toString().orEmpty(),
            durationMs,
            bytes,
            uri == null || interrupted,
            emptyList(),
        )
        if (uri != null) {
            DevelopUgandaFivemods12Identity.writeCleanSidecar(
                this,
                DevelopUgandaCameraPage.LIVE,
                file.name,
                durationMs > 0L && bytes > 1_024L,
                durationMs,
                bytes,
            )
            DevelopUgandaV274MediaVaultStore.onClipFinalized(
                this,
                file.nameWithoutExtension,
                uri.toString(),
                durationMs,
                bytes,
                "CLEAN",
                "LIVE RTMPS",
                "LIVE",
                1,
                segmentIndex,
                emptyList(),
                DevelopUgandaCameraPage.LIVE,
            )
            DevelopUgandaSealRegistry.sealAsync(
                this,
                uri,
                file.nameWithoutExtension,
                DevelopUgandaCameraPage.LIVE,
            )
        }
        localFile = null
    }

    private fun publishBackup(file: File): Uri? = runCatching {
        if (!file.isFile || file.length() <= 1024L) return@runCatching null
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/develop.uganda/Live")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@runCatching null
        try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { input -> input.copyTo(output, 256 * 1024) }
            } ?: error("Backup publish stream unavailable")
            contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) },
                null,
                null,
            )
            uri
        } catch (failure: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw failure
        }
    }.getOrNull()

    private fun mediaDuration(file: File): Long = runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
        } finally {
            retriever.release()
        }
    }.getOrDefault(0L)

    private fun adaptResolution() {
        if (adapting || tierIndex >= tiers.lastIndex || !broadcasting) return
        adapting = true
        val previous = tiers[tierIndex]
        cumulativeDropped += stream?.getStreamClient()?.getDroppedVideoFrames() ?: 0L
        tierIndex += 1
        val next = tiers[tierIndex]
        state("DEGRADED • ${previous.label} → ${next.label}", DevelopUgandaFivemods8Theme.warning)
        val config = configuration
        runCatching { stream?.stopStream() }
        stopLocalSegment(interrupted = false)
        if (!prepareStream() || !startLocalSegment() || config == null) {
            broadcasting = false
            adapting = false
            protectedTake?.finish(interrupted = true)
            state("DISCONNECTED • ADAPTATION FAILED • CLEAN SEGMENT SAFE", DevelopUgandaFivemods8Theme.record)
            setControls(active = false)
            return
        }
        adapter.reset()
        val target = config.endpoint.trimEnd('/') + "/" + config.key.trimStart('/')
        stream?.startStream(target)
        adapting = false
    }

    private fun confirmStop() {
        if (!broadcasting) return
        AlertDialog.Builder(this)
            .setTitle("STOP LIVE")
            .setMessage("The network stream will close, then the current CLEAN backup segment will be finalised.")
            .setNegativeButton("KEEP LIVE", null)
            .setPositiveButton("STOP AND SAVE") { _, _ -> stopBroadcast("DISCONNECTED • CLEAN BACKUP SAFE") }
            .show()
    }

    private fun addMark() {
        val take = protectedTake
        if (!broadcasting || take == null || takeId.isBlank()) {
            rejected("START LIVE BEFORE MARK")
            return
        }
        val mark = runCatching {
            DevelopUgandaTakeMarks.add(
                this,
                takeId,
                DevelopUgandaCameraPage.LIVE,
                take.elapsedMs(),
            )
        }.getOrElse {
            rejected("MARK SIDECAR WRITE FAILED")
            return
        }
        markButton.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        state("MARK ${DevelopUgandaTakeMarks.format(mark.elapsedMs)} • LIVE CONTINUES", DevelopUgandaFivemods8Theme.content)
    }

    private fun stopBroadcast(message: String) {
        broadcasting = false
        adapting = false
        runCatching { if (stream?.isStreaming == true) stream?.stopStream() }
        stopLocalSegment(interrupted = false)
        protectedTake?.finish(interrupted = false)
        protectedTake = null
        state(message, DevelopUgandaFivemods8Theme.content)
        setControls(active = false)
    }

    private fun releaseStream() {
        runCatching { stream?.release() }
        stream = null
        prepared = false
    }

    private fun validatedNetworkFailure(): String? {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return "NO NETWORK"
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return "NO INTERNET CAPABILITY"
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return "NETWORK NOT VALIDATED"
        val thermal = DevelopUgandaV276RecordingSafety.thermalStatusMeasured(this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            DevelopUgandaV276RecordingSafety.thermalBlocksTake(thermal)) {
            return "THERMAL MEASURED ${DevelopUgandaFivemods12Preflight.thermalLabel(thermal)} • SEVERE START-BLOCK THRESHOLD CROSSED"
        }
        return null
    }

    private fun setControls(active: Boolean) {
        setupButton.isEnabled = !active
        setupButton.alpha = if (active) 0.45f else 1f
        startButton.isEnabled = !active
        startButton.alpha = if (active) 0.45f else 1f
        startButton.text = if (active) "LIVE ACTIVE" else "START LIVE"
        stopButton.isEnabled = active
        stopButton.alpha = if (active) 1f else 0.45f
        markButton.isEnabled = active
        markButton.alpha = if (active) 1f else 0.45f
        markButton.text = if (active) "MARK" else "MARK • LIVE REQUIRED"
    }

    private fun state(value: String, color: Int) = runOnUiThread {
        stateView.text = value
        stateView.setTextColor(color)
        stateView.contentDescription = value
    }

    private fun rejected(reason: String) {
        state(reason.uppercase(), DevelopUgandaFivemods8Theme.record)
        DevelopUgandaBroadcastPresentation.rejected(stateView)
    }

    override fun onConnectionStarted(url: String) {
        state("CONNECTING • LOCAL CLEAN RECORDING", DevelopUgandaFivemods8Theme.warning)
    }

    override fun onConnectionSuccess() {
        reconnectAttempt = 0
        state("LIVE • ${tiers[tierIndex].label} • CLEAN BACKUP ON", DevelopUgandaFivemods8Theme.record)
    }

    override fun onConnectionFailed(reason: String) {
        val safe = DevelopUgandaSecureStreamConfig.redactedFailure(reason)
        reconnectAttempt = (reconnectAttempt + 1).coerceAtMost(20)
        val delayMs = (1_500L * (1L shl (reconnectAttempt - 1).coerceIn(0, 4))).coerceAtMost(30_000L)
        val retrying = broadcasting && (stream?.getStreamClient()?.reTry(delayMs, reason, null) == true)
        state(
            if (retrying) "RETRY ${reconnectAttempt} IN ${delayMs / 1_000L}s • $safe • CLEAN BACKUP ON" else "DISCONNECTED • $safe • CLEAN BACKUP ON",
            DevelopUgandaFivemods8Theme.record,
        )
    }

    override fun onStreamingStats(report: StreamingStatsReport) {
        lastReport = report
        adapter.onStreamingStats(report)
        val active = stream ?: return
        val dropped: Long = cumulativeDropped + active.getStreamClient().getDroppedVideoFrames()
        val elapsedMs = (SystemClock.elapsedRealtime() - segmentStartedElapsed).coerceAtLeast(0L)
        val recordedBytes = localFile?.length()?.coerceAtLeast(0L) ?: 0L
        DevelopUgandaV276RecordingSafety.onRecordingStatus(
            this,
            elapsedMs * 1_000_000L,
            recordedBytes,
            DevelopUgandaCameraPage.LIVE,
        )
        val protection = DevelopUgandaFivemods12RecordingProtection.state(this, DevelopUgandaCameraPage.LIVE)
        if (!f12ProtectionStopRequested && protection.stopReason != null) {
            f12ProtectionStopRequested = true
            runOnUiThread { stopBroadcast("${protection.stopReason} • CLEAN BACKUP FINALISED") }
            return
        }
        if (!f12AudioRouteStopped && elapsedMs - f12LastAudioRouteCheckMs >= 1_500L) {
            f12LastAudioRouteCheckMs = elapsedMs
            if (DevelopUgandaV272FieldSoundContinuity.audioRouteChanged(this)) {
                f12AudioRouteStopped = true
                f12ProtectionStopRequested = true
                DevelopUgandaV276RecordingSafety.addEvent(this, "INTERRUPTION • RTMPS MICROPHONE ROUTE CHANGED • FINALISE CLEAN")
                runOnUiThread { stopBroadcast("MIC DISCONNECTED / ROUTE CHANGED • CLEAN BACKUP SAFE") }
                return
            }
        }
        runOnUiThread {
            DevelopUgandaMetricReadouts.bind(
                this,
                bitrateView,
                DevelopUgandaMetricReadout(
                    "live_uplink",
                    "UPLINK",
                    report.smoothedBitrate.toDouble(),
                    { String.format(java.util.Locale.US, "%.2f Mbps", it / 1_000_000.0) },
                    "The measured bitrate delivered to the RTMPS sender.",
                    if (report.throughput == Throughput.INSUFFICIENT) "The stream is adapting; reduce competing network traffic." else "No action required.",
                    DevelopUgandaMetricOrigin.MEASURED,
                    "RootEncoder RTMPS send-queue statistics",
                    report.throughput == Throughput.INSUFFICIENT,
                ),
                animate = true,
            )
            DevelopUgandaMetricReadouts.bind(
                this,
                droppedView,
                DevelopUgandaMetricReadout(
                    "live_dropped_frames",
                    "DROPPED FRAMES",
                    dropped.toDouble(),
                    { "${it.toLong()} frames" },
                    "Video frames the network sender could not deliver.",
                    if (dropped > 0) "Check uplink stability; the local CLEAN backup is unaffected." else "No action required.",
                    DevelopUgandaMetricOrigin.MEASURED,
                    "RootEncoder RTMPS sender counter",
                    dropped > 0,
                ),
                animate = true,
            )
        }
        if (report.throughput == Throughput.INSUFFICIENT || report.queueCongestionPercent >= 50f) {
            congestedReports += 1
        } else {
            congestedReports = 0
        }
        if (congestedReports >= 3) {
            congestedReports = 0
            runOnUiThread { adaptResolution() }
        }
        if (SystemClock.elapsedRealtime() - segmentStartedElapsed >= DevelopUgandaCrashSafeTake.SEGMENT_DURATION_MS) {
            runOnUiThread { rotateLocalSegment() }
        }
    }

    override fun onNewBitrate(bitrate: Long) = Unit

    override fun onDisconnect() {
        if (!adapting && broadcasting) {
            state("DISCONNECTED • CLEAN BACKUP ON", DevelopUgandaFivemods8Theme.record)
        }
    }

    override fun onAuthError() {
        state("AUTH REJECTED • CHECK STREAM KEY • CLEAN BACKUP ON", DevelopUgandaFivemods8Theme.record)
        runCatching { stream?.stopStream() }
    }

    override fun onAuthSuccess() {
        state("AUTHENTICATED • CONNECTING", DevelopUgandaFivemods8Theme.warning)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (!prepared) prepareStream()
        if (stream?.isOnPreview == false) stream?.startPreview(preview)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        runCatching { if (stream?.isOnPreview == true) stream?.stopPreview() }
    }

    override fun onDestroy() {
        if (broadcasting || localFile != null) stopBroadcast("DISCONNECTED • CLEAN BACKUP SAFE")
        releaseStream()
        super.onDestroy()
    }

    override fun onStop() {
        if (broadcasting || localFile != null) {
            DevelopUgandaV276RecordingSafety.addEvent(this, "INTERRUPTION • RTMPS APP BACKGROUND/LOCK • FINALISE CLEAN")
            stopBroadcast("INTERRUPTED • CLEAN BACKUP SAFE")
        }
        super.onStop()
    }

    private fun unit() = DevelopUgandaFivemods8Theme.spacingUnitPx
}
