package com.sentongoharuna.pulse

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioAttributes
import android.media.AudioTrack
import android.media.MediaRecorder
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.io.File
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * V272 FIELD SOUND + CONTINUITY ENGINE.
 *
 * Real functions in this layer:
 * - Enumerates currently detected Android audio-input devices.
 * - Performs an actual short AudioRecord microphone test when permission allows.
 * - Stores/restores extended camera-continuity metadata.
 * - Captures a real preview reference frame and can display it as a screen-only ghost overlay.
 *
 * Deliberately NOT faked:
 * - The safety pair is a real companion WAV (limited primary + -12 dB channel),
 *   never a label applied to CameraX's unchanged embedded audio track.
 * - True encoded pre-roll is not claimed until the recording pipeline owns a verified buffer.
 */
object DevelopUgandaV272FieldSoundContinuity {
    const val PREFS = "develop_uganda_v272_field_sound_continuity"
    private const val REF_FILE = "v272_continuity_reference.jpg"
    private const val GHOST_TAG = "v272_ghost_overlay"
    private const val SAFETY_SAMPLE_RATE = 48_000
    private const val SAFETY_ATTENUATION = 0.25118864f // -12 dB
    @Volatile private var safetyRecorder: SafetyAudioRecorder? = null

    data class SafetyAudioState(
        val active: Boolean,
        val source: String,
        val primaryDbfs: Double?,
        val safetyDbfs: Double?,
        val peakHoldDbfs: Double?,
        val windHandling: String,
        val outputPath: String?,
        val failure: String?,
        val monitoring: String = "MONITOR UNKNOWN",
    ) {
        fun hudLine(): String = buildString {
            append(source)
            append(" • PRIMARY ").append(primaryDbfs?.let { String.format(Locale.US, "%.1fdBFS", it) } ?: "UNKNOWN")
            append(" • SAFETY ").append(safetyDbfs?.let { String.format(Locale.US, "%.1fdBFS", it) } ?: "UNKNOWN")
            append(" • ").append(windHandling)
            append(" • ").append(monitoring)
            failure?.let { append(" • ").append(it) }
        }
    }

    private class SafetyAudioRecorder(
        private val context: Context,
        private val takeId: String,
    ) {
        private val running = AtomicBoolean(false)
        @Volatile private var state = SafetyAudioState(
            active = false,
            source = "INPUT UNKNOWN",
            primaryDbfs = null,
            safetyDbfs = null,
            peakHoldDbfs = null,
            windHandling = "WIND UNKNOWN",
            outputPath = null,
            failure = null,
        )
        private var worker: Thread? = null
        private var audioRecord: AudioRecord? = null
        private var monitorTrack: AudioTrack? = null
        private var partFile: File? = null
        private var bytesWritten = 0L

        fun snapshot(): SafetyAudioState = state

        fun start(): SafetyAudioState {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                state = state.copy(failure = "MIC PERMISSION REQUIRED")
                return state
            }
            val minBuffer = AudioRecord.getMinBufferSize(
                SAFETY_SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            if (minBuffer <= 0) {
                state = state.copy(failure = "SAFETY INPUT UNSUPPORTED")
                return state
            }
            return try {
                val preferred = preferredInput(context)
                val record = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAFETY_SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(maxOf(minBuffer * 3, 16_384))
                    .build()
                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    error("AudioRecord did not initialise")
                }
                val preferenceAccepted = preferred?.let(record::setPreferredDevice) ?: true
                val directory = File(
                    context.getExternalFilesDir(null) ?: context.filesDir,
                    "develop.uganda/SafetyAudio",
                ).apply { mkdirs() }
                val file = File(directory, "${takeId}_SAFETY.wav.part")
                FileOutputStream(file, false).use { it.write(ByteArray(44)) }
                partFile = file
                audioRecord = record
                val monitor = createWiredMonitor(context)
                monitorTrack = monitor?.first
                running.set(true)
                val requestedSource = preferred?.let(::deviceLabel) ?: "INPUT UNKNOWN"
                state = state.copy(
                    active = true,
                    source = if (preferenceAccepted) requestedSource else "$requestedSource • ROUTE REQUEST REJECTED",
                    outputPath = file.absolutePath,
                    monitoring = monitor?.second ?: "MONITOR ABSENT",
                )
                worker = thread(name = "du-safety-audio", isDaemon = true) { captureLoop(record, file) }
                state
            } catch (failure: Throwable) {
                runCatching { audioRecord?.release() }
                audioRecord = null
                running.set(false)
                state = state.copy(active = false, failure = "SAFETY TRACK ${failure.javaClass.simpleName.uppercase(Locale.US)}")
                state
            }
        }

        fun stop(): SafetyAudioState {
            running.set(false)
            runCatching { audioRecord?.stop() }
            runCatching { worker?.join(1_500L) }
            runCatching { audioRecord?.release() }
            runCatching { monitorTrack?.stop() }
            runCatching { monitorTrack?.release() }
            audioRecord = null
            monitorTrack = null
            worker = null
            val raw = partFile
            if (raw != null && raw.isFile && bytesWritten > 0L) {
                runCatching {
                    patchWaveHeader(raw, bytesWritten)
                    val complete = File(raw.parentFile, raw.name.removeSuffix(".part"))
                    if (complete.exists()) error("Safety output already exists")
                    if (!raw.renameTo(complete)) error("Could not finalise safety WAV")
                    state = state.copy(active = false, outputPath = complete.absolutePath)
                }.onFailure {
                    state = state.copy(active = false, failure = "SAFETY FINALIZE ${it.javaClass.simpleName.uppercase(Locale.US)}")
                }
            } else {
                state = state.copy(active = false, failure = state.failure ?: "SAFETY TRACK EMPTY")
            }
            return state
        }

        private fun captureLoop(record: AudioRecord, file: File) {
            val input = ShortArray(4_096)
            val output = ByteArray(input.size * 4)
            val monitor = ShortArray(input.size)
            var peakHold = 0.0
            var lowPass = 0.0
            try {
                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) error("Microphone is busy")
                val routedSource = record.routedDevice?.let(::deviceLabel)
                if (routedSource != null) state = state.copy(source = routedSource)
                BufferedOutputStream(FileOutputStream(file, true), 64 * 1024).use { stream ->
                    while (running.get()) {
                        val read = record.read(input, 0, input.size, AudioRecord.READ_BLOCKING)
                        if (read <= 0) continue
                        var sumSq = 0.0
                        var lowSq = 0.0
                        var peak = 0.0
                        var outAt = 0
                        repeat(read) { index ->
                            val normalized = input[index] / 32768.0
                            // Smooth saturation is the primary protection path; it has no
                            // hard discontinuity and caps below full scale.
                            val limited = (tanh(normalized * 1.8) / tanh(1.8) * 0.98)
                            val safety = (normalized * SAFETY_ATTENUATION).coerceIn(-0.98, 0.98)
                            val primaryShort = (limited * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                            val safetyShort = (safety * 32767.0).toInt().coerceIn(-32768, 32767).toShort()
                            monitor[index] = primaryShort
                            output[outAt++] = primaryShort.toInt().toByte()
                            output[outAt++] = (primaryShort.toInt() shr 8).toByte()
                            output[outAt++] = safetyShort.toInt().toByte()
                            output[outAt++] = (safetyShort.toInt() shr 8).toByte()
                            sumSq += normalized * normalized
                            lowPass += 0.018 * (normalized - lowPass)
                            lowSq += lowPass * lowPass
                            peak = maxOf(peak, abs(normalized))
                        }
                        stream.write(output, 0, outAt)
                        monitorTrack?.write(monitor, 0, read, AudioTrack.WRITE_NON_BLOCKING)
                        bytesWritten += outAt.toLong()
                        val rms = sqrt(sumSq / read.coerceAtLeast(1))
                        val lowRatio = if (sumSq > 0.0000001) (lowSq / sumSq).coerceIn(0.0, 1.0) else 0.0
                        peakHold = maxOf(peak, peakHold * 0.985)
                        val primaryDb = dbfs(rms)
                        val wind = when {
                            rms < 0.003 -> "WIND INACTIVE"
                            lowRatio >= 0.62 -> "WIND/HANDLING HIGH"
                            lowRatio >= 0.38 -> "WIND/HANDLING WATCH"
                            else -> "WIND/HANDLING CLEAR"
                        }
                        state = state.copy(
                            active = true,
                            primaryDbfs = primaryDb,
                            safetyDbfs = (primaryDb - 12.0).coerceAtLeast(-120.0),
                            peakHoldDbfs = dbfs(peakHold),
                            windHandling = wind,
                            failure = null,
                        )
                    }
                }
            } catch (failure: Throwable) {
                state = state.copy(active = false, failure = "SAFETY TRACK ${failure.javaClass.simpleName.uppercase(Locale.US)}")
            } finally {
                running.set(false)
            }
        }
    }

    private fun prefs(context: Context) = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bool(context: Context, key: String, defaultValue: Boolean): Boolean =
        prefs(context).getBoolean(key, defaultValue)

    fun toggle(context: Context, key: String, defaultValue: Boolean, label: String): String {
        val next = !bool(context, key, defaultValue)
        prefs(context).edit().putBoolean(key, next).apply()
        return "$label ${if (next) "ON" else "OFF"}"
    }

    fun preRollSeconds(context: Context): Int =
        prefs(context).getInt("pre_roll_seconds", 0).let { if (it in listOf(0,3,5,10)) it else 0 }

    fun setPreRollSeconds(context: Context, seconds: Int): String {
        val value = if (seconds in listOf(0,3,5,10)) seconds else 0
        prefs(context).edit().putInt("pre_roll_seconds", value).apply()
        return if (value == 0) "PRE-ROLL REQUEST • OFF" else "PRE-ROLL REQUEST • ${value}s • NOT YET VERIFIED"
    }

    fun saveState(context: Context, json: JSONObject) {
        prefs(context).edit()
            .putString("reference_state", json.toString())
            .putLong("reference_saved_ms", System.currentTimeMillis())
            .apply()
    }

    fun loadState(context: Context): JSONObject? = try {
        prefs(context).getString("reference_state", null)?.let(::JSONObject)
    } catch (_: Exception) { null }

    fun hasReference(context: Context): Boolean =
        referenceFile(context).exists() && loadState(context) != null

    fun referenceFile(context: Context): File = File(context.filesDir, REF_FILE)

    fun saveReferenceBitmap(context: Context, bitmap: Bitmap?): Boolean {
        if (bitmap == null || bitmap.width < 8 || bitmap.height < 8) return false
        return try {
            FileOutputStream(referenceFile(context)).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 84, out)
            }
            true
        } catch (_: Exception) { false }
    }

    fun attach(activity: DevelopUgandaCameraActivity, root: FrameLayout) {
        if (root.findViewWithTag<View>(GHOST_TAG) != null) return
        val ghost = ImageView(activity).apply {
            tag = GHOST_TAG
            scaleType = ImageView.ScaleType.CENTER_CROP
            alpha = 0.18f
            visibility = View.GONE
            isClickable = false
            isFocusable = false
            contentDescription = "V272 continuity reference overlay"
        }
        val params = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        // Preview is child 0 and preview-tone is child 1 in the camera deck.
        // Insert above those but below HUD/controls so this remains a true camera-image aid.
        root.addView(ghost, minOf(2, root.childCount), params)
        refreshGhost(activity, root)
    }

    fun refreshGhost(activity: DevelopUgandaCameraActivity, root: FrameLayout? = null) {
        val host = root ?: activity.findViewById<FrameLayout>(android.R.id.content)?.getChildAt(0) as? FrameLayout
        val ghost = host?.findViewWithTag<ImageView>(GHOST_TAG) ?: return
        val enabled = bool(activity, "ghost_overlay", false)
        val file = referenceFile(activity)
        if (!enabled || !file.exists()) {
            ghost.setImageDrawable(null)
            ghost.visibility = View.GONE
            return
        }
        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            if (bitmap != null) {
                ghost.setImageBitmap(bitmap)
                ghost.alpha = prefs(activity).getInt("ghost_alpha", 18).coerceIn(8, 45) / 100f
                ghost.visibility = View.VISIBLE
            } else {
                ghost.visibility = View.GONE
            }
        } catch (_: Exception) {
            ghost.visibility = View.GONE
        }
    }

    fun setGhostAlpha(context: Context, percent: Int): String {
        val value = percent.coerceIn(8,45)
        prefs(context).edit().putInt("ghost_alpha", value).apply()
        return "GHOST STRENGTH • $value%"
    }

    fun ghostAlpha(context: Context): Int = prefs(context).getInt("ghost_alpha",18).coerceIn(8,45)

    fun audioInputSummary(context: Context): String {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return "MIC PERMISSION • REQUIRED"
        }
        return try {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            if (inputs.isEmpty()) return "INPUT • NONE DETECTED"
            val labels = inputs.map { deviceLabel(it) }.distinct().take(3)
            "INPUT • ${labels.joinToString(" + ")}"
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V272 AUDIO INPUT READ", error)
            "INPUT • UNKNOWN • READ FAILED"
        }
    }

    private fun deviceLabel(info: AudioDeviceInfo): String = when (info.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "PHONE MIC"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED MIC"
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB MIC"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH MIC"
        else -> "MIC ${info.type}"
    }

    private fun preferredInput(context: Context): AudioDeviceInfo? = runCatching {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val priority = mapOf(
            AudioDeviceInfo.TYPE_USB_DEVICE to 0,
            AudioDeviceInfo.TYPE_USB_HEADSET to 1,
            AudioDeviceInfo.TYPE_WIRED_HEADSET to 2,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO to 3,
            AudioDeviceInfo.TYPE_BUILTIN_MIC to 4,
        )
        manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            .filter { it.isSource }
            .minByOrNull { priority[it.type] ?: 20 }
    }.getOrNull()

    /**
     * Monitoring is enabled only when Android exposes a wired or USB sink.
     * Non-blocking writes keep it off the CameraX encoder path; phone speakers
     * are deliberately rejected to prevent an acoustic feedback loop.
     */
    private fun createWiredMonitor(context: Context): Pair<AudioTrack, String>? = runCatching {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val output = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { it.isSink }
            .minByOrNull {
                when (it.type) {
                    AudioDeviceInfo.TYPE_USB_HEADSET -> 0
                    AudioDeviceInfo.TYPE_USB_DEVICE -> 1
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 2
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> 3
                    else -> 20
                }
            }
            ?.takeIf {
                it.type in setOf(
                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                )
            }
            ?: return@runCatching null
        val minBytes = AudioTrack.getMinBufferSize(
            SAFETY_SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBytes <= 0) return@runCatching null
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAFETY_SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(minBytes * 2, 16_384))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (track.state != AudioTrack.STATE_INITIALIZED || !track.setPreferredDevice(output)) {
            track.release()
            return@runCatching null
        }
        track.play()
        track to "MONITOR ${deviceOutputLabel(output)}"
    }.getOrNull()

    private fun deviceOutputLabel(info: AudioDeviceInfo): String = when (info.type) {
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE -> "USB"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED"
        else -> "ABSENT"
    }

    @Synchronized
    fun startSafetyTrack(context: Context, takeId: String): SafetyAudioState {
        safetyRecorder?.let {
            val current = it.snapshot()
            if (current.active) return current.copy(failure = "SAFETY TRACK ALREADY ACTIVE")
        }
        return SafetyAudioRecorder(context.applicationContext, takeId).also { safetyRecorder = it }.start()
    }

    @Synchronized
    fun stopSafetyTrack(): SafetyAudioState {
        val current = safetyRecorder
            ?: return SafetyAudioState(false, "INPUT UNKNOWN", null, null, null, "WIND UNKNOWN", null, "SAFETY TRACK NOT ACTIVE")
        val result = current.stop()
        safetyRecorder = null
        return result
    }

    fun safetyAudioState(): SafetyAudioState = safetyRecorder?.snapshot()
        ?: SafetyAudioState(false, "INPUT UNKNOWN", null, null, null, "WIND INACTIVE", null, null)

    /**
     * Copies and repairs WAV headers left by process death. The `.part` source is
     * retained byte-for-byte; only the recovered copy is modified.
     */
    fun recoverSafetyFragments(context: Context): List<String> {
        val directory = File(
            context.getExternalFilesDir(null) ?: context.filesDir,
            "develop.uganda/SafetyAudio",
        )
        if (!directory.isDirectory) return emptyList()
        return directory.listFiles { file -> file.name.endsWith(".wav.part") }
            .orEmpty()
            .filter { it.length() > 44L }
            .mapNotNull { raw ->
                val recovered = File(raw.parentFile, raw.name.removeSuffix(".part") + ".recovered.wav")
                if (recovered.exists()) return@mapNotNull null
                runCatching {
                    raw.copyTo(recovered, overwrite = false)
                    patchWaveHeader(recovered, recovered.length() - 44L)
                    "${raw.name} • raw preserved • recovered copy ${recovered.name}"
                }.getOrElse { failure ->
                    runCatching { if (recovered.exists()) recovered.delete() }
                    "${raw.name} • raw preserved • recovery failed ${failure.javaClass.simpleName}"
                }
            }
    }

    private fun patchWaveHeader(file: File, audioBytes: Long) {
        require(audioBytes in 1..0xFFFF_FFF0L) { "Safety WAV length is unsupported" }
        RandomAccessFile(file, "rw").use { out ->
            out.seek(0L)
            out.writeBytes("RIFF")
            writeLittleEndianInt(out, (36L + audioBytes).toInt())
            out.writeBytes("WAVEfmt ")
            writeLittleEndianInt(out, 16)
            writeLittleEndianShort(out, 1)
            writeLittleEndianShort(out, 2)
            writeLittleEndianInt(out, SAFETY_SAMPLE_RATE)
            writeLittleEndianInt(out, SAFETY_SAMPLE_RATE * 4)
            writeLittleEndianShort(out, 4)
            writeLittleEndianShort(out, 16)
            out.writeBytes("data")
            writeLittleEndianInt(out, audioBytes.toInt())
        }
    }

    private fun writeLittleEndianInt(out: RandomAccessFile, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 24) and 0xFF)
    }

    private fun writeLittleEndianShort(out: RandomAccessFile, value: Int) {
        out.write(value and 0xFF)
        out.write((value ushr 8) and 0xFF)
    }

    private fun dbfs(amplitude: Double): Double =
        (20.0 * log10(amplitude.coerceAtLeast(0.000001))).coerceAtLeast(-120.0)

    fun runAudioTest(activity: DevelopUgandaCameraActivity, callback: (String) -> Unit) {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback("MIC TEST • PERMISSION REQUIRED")
            return
        }
        Thread {
            val sampleRate = 16000
            val min = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (min <= 0) {
                activity.runOnUiThread { callback("MIC TEST • DEVICE UNAVAILABLE") }
                return@Thread
            }
            var record: AudioRecord? = null
            var result = "MIC TEST • FAILED"
            try {
                val bufferSize = maxOf(min * 2, 4096)
                record = AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .build()
                if (record.state != AudioRecord.STATE_INITIALIZED) throw IllegalStateException("AudioRecord not initialized")
                val data = ShortArray(bufferSize / 2)
                record.startRecording()
                val deadline = System.currentTimeMillis() + 1400L
                var peak = 0
                var sumSq = 0.0
                var samples = 0L
                while (System.currentTimeMillis() < deadline) {
                    val read = record.read(data, 0, data.size)
                    if (read <= 0) continue
                    for (i in 0 until read) {
                        val a = kotlin.math.abs(data[i].toInt())
                        if (a > peak) peak = a
                        val n = data[i] / 32768.0
                        sumSq += n * n
                    }
                    samples += read
                }
                val rms = if (samples > 0) sqrt(sumSq / samples) else 0.0
                val peakNorm = (peak / 32768.0).coerceAtLeast(0.000001)
                val peakDb = 20.0 * log10(peakNorm)
                val rmsDb = 20.0 * log10(rms.coerceAtLeast(0.000001))
                val state = when {
                    peakDb > -1.5 -> "CLIP RISK"
                    rmsDb < -48.0 -> "VERY LOW"
                    rmsDb < -34.0 -> "LOW"
                    else -> "VOICE OK"
                }
                result = String.format(Locale.US, "MIC TEST • %s • RMS %.1fdBFS • PEAK %.1fdBFS", state, rmsDb, peakDb)
            } catch (e: Exception) {
                result = "MIC TEST • ${e.javaClass.simpleName.uppercase(Locale.US)}"
            } finally {
                try { record?.stop() } catch (_: Exception) {}
                try { record?.release() } catch (_: Exception) {}
            }
            prefs(activity).edit().putString("last_audio_test", result).apply()
            activity.runOnUiThread { callback(result) }
        }.start()
    }

    fun lastAudioTest(context: Context): String = prefs(context).getString("last_audio_test", "MIC TEST • NOT RUN") ?: "MIC TEST • NOT RUN"

    fun armAudioLock(context: Context) {
        if (!bool(context, "audio_lock", true)) return
        prefs(context).edit().putString("locked_input", audioInputSummary(context)).apply()
    }

    fun audioRouteChanged(context: Context): Boolean {
        if (!bool(context, "audio_lock", true)) return false
        val locked = prefs(context).getString("locked_input", "").orEmpty()
        if (locked.isBlank()) return false
        return locked != audioInputSummary(context)
    }

    fun lockedAudioInput(context: Context): String = prefs(context).getString("locked_input", "NOT ARMED") ?: "NOT ARMED"

    fun verificationSummary(context: Context): String = buildString {
        append("LIVE AUDIO METER • WORKING DURING CameraX RECORD\n")
        append("INPUT DETECTION • WORKING • ").append(audioInputSummary(context)).append('\n')
        append("REAL MIC TEST • WORKING WITH PERMISSION\n")
        append("REFERENCE FRAME • ").append(if (hasReference(context)) "WORKING" else "READY / NO REFERENCE").append('\n')
        append("GHOST OVERLAY • ").append(if (bool(context,"ghost_overlay",false)) "ON • SCREEN ONLY" else "OFF").append('\n')
        append("CONTINUITY RESTORE • WORKING FOR APP-CONTROLLABLE SETTINGS\n")
        val safety = safetyAudioState()
        append("SECOND SAFETY AUDIO TRACK • ").append(if (safety.active) safety.hudLine() else "READY / STARTS WITH RECORD").append('\n')
        append("SAFETY FORMAT • STEREO WAV • PRIMARY SOFT LIMITER • RIGHT -12dB\n")
        val pre = preRollSeconds(context)
        append("ENCODED PRE-ROLL • ").append(if (pre == 0) "OFF / NOT CLAIMED" else "${pre}s REQUESTED • NOT YET VERIFIED")
    }

    fun hubSummary(context: Context): String {
        val input = audioInputSummary(context).removePrefix("INPUT • ")
        val ref = if (hasReference(context)) "REF READY" else "NO REF"
        val pre = preRollSeconds(context)
        return "SOUND $input • CONTINUITY $ref • PRE-ROLL ${if (pre == 0) "OFF" else "${pre}s REQUEST"}"
    }

    fun showVerification(activity: DevelopUgandaCameraActivity) {
        AlertDialog.Builder(activity)
            .setTitle("V272 • VERIFICATION CENTER")
            .setMessage(verificationSummary(activity))
            .setPositiveButton("DONE", null)
            .show()
    }
}
