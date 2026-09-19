package com.sentongoharuna.pulse

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognitionPart
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

object DevelopUgandaTranscriptEngine {

    data class TranscriptResult(
        val status: String,
        val transcript: String,
        val srtDraft: String?,
        val detail: String
    )

    private data class PcmInfo(
        val file: File,
        val sampleRate: Int,
        val channelCount: Int
    )

    private data class TimedWord(
        val timeMs: Long,
        val text: String
    )

    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun transcribe(
        context: Context,
        videoUri: Uri,
        onComplete: (TranscriptResult) -> Unit
    ) {
        val appContext = context.applicationContext

        if (Build.VERSION.SDK_INT < 33) {
            onComplete(
                TranscriptResult(
                    status = "UNAVAILABLE",
                    transcript = "",
                    srtDraft = null,
                    detail = "Android 13 / API 33+ is required for prerecorded-audio injection."
                )
            )
            return
        }

        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
            onComplete(
                TranscriptResult(
                    status = "UNAVAILABLE",
                    transcript = "",
                    srtDraft = null,
                    detail = "This phone does not report an on-device SpeechRecognizer. No cloud recognizer was substituted."
                )
            )
            return
        }

        worker.execute {
            val pcm = try {
                decodeAudioToPcm(appContext, videoUri)
            } catch (e: Exception) {
                null
            }

            if (pcm == null) {
                onComplete(
                    TranscriptResult(
                        status = "FAILED",
                        transcript = "",
                        srtDraft = null,
                        detail = "Audio could not be decoded to PCM for transcription. Original video is unchanged."
                    )
                )
                return@execute
            }

            main.post {
                recognizePcm(
                    appContext,
                    pcm,
                    onComplete
                )
            }
        }
    }

    private fun decodeAudioToPcm(
        context: Context,
        videoUri: Uri
    ): PcmInfo? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val outputFile = File(
            context.cacheDir,
            "du_transcript_${System.currentTimeMillis()}.pcm"
        )

        try {
            extractor.setDataSource(
                context,
                videoUri,
                null
            )

            var audioTrack = -1
            var sourceFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    sourceFormat = format
                    break
                }
            }

            if (audioTrack < 0 || sourceFormat == null) {
                return null
            }

            extractor.selectTrack(audioTrack)
            val mime = sourceFormat.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(sourceFormat, null, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false
            val info = MediaCodec.BufferInfo()
            var outputFormat: MediaFormat = sourceFormat

            FileOutputStream(outputFile).use { output ->
                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(10_000)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            if (inputBuffer != null) {
                                inputBuffer.clear()
                                val sampleSize = extractor.readSampleData(inputBuffer, 0)
                                if (sampleSize < 0) {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        0,
                                        0L,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                    )
                                    inputDone = true
                                } else {
                                    codec.queueInputBuffer(
                                        inputIndex,
                                        0,
                                        sampleSize,
                                        extractor.sampleTime,
                                        extractor.sampleFlags
                                    )
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            outputFormat = codec.outputFormat
                        }

                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        }

                        else -> {
                            if (outputIndex >= 0) {
                                val outputBuffer = codec.getOutputBuffer(outputIndex)
                                if (outputBuffer != null && info.size > 0) {
                                    outputBuffer.position(info.offset)
                                    outputBuffer.limit(info.offset + info.size)
                                    val bytes = ByteArray(info.size)
                                    outputBuffer.get(bytes)
                                    output.write(bytes)
                                }

                                outputDone =
                                    info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0

                                codec.releaseOutputBuffer(outputIndex, false)
                            }
                        }
                    }
                }
            }

            val sampleRate = try {
                outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } catch (_: Exception) {
                sourceFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            }

            val channels = try {
                outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } catch (_: Exception) {
                sourceFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }

            val pcmEncoding = if (
                Build.VERSION.SDK_INT >= 24 &&
                outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)
            ) {
                outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
            } else {
                AudioFormat.ENCODING_PCM_16BIT
            }

            if (pcmEncoding != AudioFormat.ENCODING_PCM_16BIT) {
                outputFile.delete()
                return null
            }

            if (!outputFile.exists() || outputFile.length() <= 0L) {
                return null
            }

            return PcmInfo(
                file = outputFile,
                sampleRate = sampleRate,
                channelCount = max(1, channels)
            )
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            try {
                codec?.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun recognizePcm(
        context: Context,
        pcm: PcmInfo,
        onComplete: (TranscriptResult) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < 33) {
            pcm.file.delete()
            return
        }

        val completed = AtomicBoolean(false)
        val segments = mutableListOf<String>()
        val timedWords = mutableListOf<TimedWord>()
        val recognizer = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } catch (_: Exception) {
            pcm.file.delete()
            onComplete(
                TranscriptResult(
                    "UNAVAILABLE",
                    "",
                    null,
                    "On-device SpeechRecognizer could not be created."
                )
            )
            return
        }

        val pfd = try {
            ParcelFileDescriptor.open(
                pcm.file,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
        } catch (_: Exception) {
            recognizer.destroy()
            pcm.file.delete()
            onComplete(
                TranscriptResult(
                    "FAILED",
                    "",
                    null,
                    "Decoded PCM source could not be opened."
                )
            )
            return
        }

        fun collectResults(bundle: Bundle) {
            bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { text ->
                    if (segments.lastOrNull() != text) {
                        segments.add(text)
                    }
                }

            if (Build.VERSION.SDK_INT >= 34) {
                @Suppress("DEPRECATION")
                val parts = bundle.getParcelableArrayList<RecognitionPart>(
                    SpeechRecognizer.RECOGNITION_PARTS
                )

                parts?.forEach { part ->
                    val raw = part.rawText.trim()
                    if (raw.isNotBlank()) {
                        val item = TimedWord(part.timestampMillis, raw)
                        if (timedWords.none {
                                it.timeMs == item.timeMs && it.text == item.text
                            }) {
                            timedWords.add(item)
                        }
                    }
                }
            }
        }

        fun finish(status: String, detail: String) {
            if (!completed.compareAndSet(false, true)) return

            try {
                pfd.close()
            } catch (_: Exception) {
            }
            try {
                recognizer.destroy()
            } catch (_: Exception) {
            }
            pcm.file.delete()

            val transcript = segments.joinToString("\n\n").trim()
            val srt = if (Build.VERSION.SDK_INT >= 34 && timedWords.isNotEmpty()) {
                buildSrt(timedWords.sortedBy { it.timeMs })
            } else {
                null
            }

            onComplete(
                TranscriptResult(
                    status = status,
                    transcript = transcript,
                    srtDraft = srt,
                    detail = detail + if (srt == null) {
                        "\nSubtitle timing was not produced. API 34+ word timing is required and the recognizer must return timing parts."
                    } else {
                        "\nSUBTITLES_DRAFT.srt uses recognizer word timing and must still be reviewed before burn-in."
                    }
                )
            )
        }

        recognizer.setRecognitionListener(
            object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    if (
                        error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        finish(
                            "NO SPEECH DETECTED",
                            "The phone's on-device recognizer did not confidently detect spoken words in this saved video. Quiet footage, construction noise, distant speech, music, or unsupported language/accent can produce this result. Original video is unchanged."
                        )
                        return
                    }

                    finish(
                        "FAILED",
                        "On-device recognizer error ${speechErrorLabel(error)}. Original video is unchanged."
                    )
                }

                override fun onResults(results: Bundle?) {
                    if (results != null) {
                        collectResults(results)
                    }
                    main.postDelayed(
                        {
                            finish(
                                if (segments.isNotEmpty()) "COMPLETE • DRAFT" else "NO SPEECH RESULT",
                                "Transcript was generated from the saved video's decoded audio using the phone's on-device SpeechRecognizer. Review before publication."
                            )
                        },
                        700L
                    )
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}

                override fun onSegmentResults(segmentResults: Bundle) {
                    collectResults(segmentResults)
                }

                override fun onEndOfSegmentedSession() {
                    finish(
                        if (segments.isNotEmpty()) "COMPLETE • DRAFT" else "NO SPEECH RESULT",
                        "Transcript was generated from the saved video's decoded audio using the phone's on-device SpeechRecognizer. Review before publication."
                    )
                }
            }
        )

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pfd)
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, pcm.channelCount)
            putExtra(
                RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING,
                AudioFormat.ENCODING_PCM_16BIT
            )
            putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, pcm.sampleRate)
            putExtra(
                RecognizerIntent.EXTRA_SEGMENTED_SESSION,
                RecognizerIntent.EXTRA_AUDIO_SOURCE
            )
            if (Build.VERSION.SDK_INT >= 34) {
                putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_TIMING, true)
                putExtra(RecognizerIntent.EXTRA_REQUEST_WORD_CONFIDENCE, true)
            }
        }

        try {
            recognizer.startListening(intent)
        } catch (_: Exception) {
            finish(
                "FAILED",
                "The on-device recognizer rejected prerecorded audio injection. No microphone/cloud fallback was used."
            )
        }
    }

    private fun buildSrt(words: List<TimedWord>): String {
        val groups = words.chunked(8)
        return buildString {
            groups.forEachIndexed { index, group ->
                val start = group.first().timeMs.coerceAtLeast(0L)
                val nextStart = groups.getOrNull(index + 1)?.firstOrNull()?.timeMs
                val end = (nextStart?.minus(80L) ?: (group.last().timeMs + 2_000L))
                    .coerceAtLeast(start + 700L)

                append(index + 1)
                append('\n')
                append(formatSrtTime(start))
                append(" --> ")
                append(formatSrtTime(end))
                append('\n')
                append(group.joinToString(" ") { it.text })
                append("\n\n")
            }
        }
    }

    private fun formatSrtTime(ms: Long): String {
        val total = ms.coerceAtLeast(0L)
        val hours = total / 3_600_000L
        val minutes = (total % 3_600_000L) / 60_000L
        val seconds = (total % 60_000L) / 1_000L
        val millis = total % 1_000L
        return String.format(
            Locale.US,
            "%02d:%02d:%02d,%03d",
            hours,
            minutes,
            seconds,
            millis
        )
    }

    private fun speechErrorLabel(error: Int): String =
        when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
            SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISSION"
            SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK TIMEOUT"
            SpeechRecognizer.ERROR_NO_MATCH -> "NO MATCH"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "BUSY"
            SpeechRecognizer.ERROR_SERVER -> "SERVER"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH TIMEOUT"
            else -> "CODE $error"
        }
}
