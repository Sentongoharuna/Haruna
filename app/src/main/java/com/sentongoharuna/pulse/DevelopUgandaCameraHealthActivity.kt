package com.sentongoharuna.pulse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaCodecList
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.speech.SpeechRecognizer
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.DynamicRange
import androidx.camera.core.ImageCapture
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalCamera2Interop::class)
class DevelopUgandaCameraHealthActivity :
    AppCompatActivity() {

    private lateinit var host: LinearLayout

    private val ink =
        DevelopUgandaFivemods8Theme.surface

    private val panel =
        DevelopUgandaFivemods8Theme.surface

    private val white =
        DevelopUgandaFivemods8Theme.content

    private val muted =
        DevelopUgandaFivemods8Theme.contentDim

    private val green =
        DevelopUgandaFivemods8Theme.contentDim

    private val amber =
        DevelopUgandaFivemods8Theme.accent

    private val cyan =
        DevelopUgandaFivemods8Theme.accent

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        buildUi()
        refresh()
    }

    private fun buildUi() {
        val root =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    ink
                )

                setPadding(
                    dp(12),
                    dp(18),
                    dp(12),
                    dp(12)
                )
            }

        root.addView(
            label(
                "develop.uganda • CAMERA HEALTH V227",
                18f,
                DevelopUgandaFivemods8Theme.accent,
                true
            )
        )

        root.addView(
            label(
                "REAL DEVICE CAPABILITIES • NO INVENTED LENSES • SESSION-DEPENDENT ITEMS ARE LABELLED",
                8f,
                muted,
                true
            ).apply {
                setPadding(
                    0,
                    dp(3),
                    0,
                    dp(8)
                )
            }
        )

        val refresh =
            Button(
                this
            ).apply {
                text =
                    "REFRESH CAMERA HEALTH"

                isAllCaps =
                    false

                setTextColor(
                    white
                )

                typeface =
                    Typeface.DEFAULT_BOLD

                background =
                    rounded(
                        DevelopUgandaFivemods8Theme.surfaceRaised,
                        cyan
                    )

                setOnClickListener {
                    refresh()
                }
            }

        root.addView(
            refresh,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )

        val scroll =
            ScrollView(
                this
            )

        host =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    0,
                    dp(8),
                    0,
                    dp(20)
                )
            }

        scroll.addView(
            host
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(
            root
        )
    }

    private fun refresh() {
        host.removeAllViews()

        addSystemCard()

        val future =
            ProcessCameraProvider.getInstance(
                this
            )

        future.addListener(
            {
                try {
                    val provider =
                        future.get()

                    addCameraSummary(
                        provider
                    )
                } catch (
                    e: Exception
                ) {
                    addCard(
                        "CAMERA QUERY",
                        listOf(
                            "CameraX provider unavailable • ${e.javaClass.simpleName}"
                        ),
                        false
                    )
                }
            },
            ContextCompat.getMainExecutor(
                this
            )
        )
    }

    private fun addSystemCard() {
        val speech =
            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.S
            ) {
                try {
                    if (
                        SpeechRecognizer.isOnDeviceRecognitionAvailable(
                            this
                        )
                    ) {
                        "ON-DEVICE TRANSCRIPTION • AVAILABLE"
                    } else {
                        "ON-DEVICE TRANSCRIPTION • UNAVAILABLE"
                    }
                } catch (_: Exception) {
                    "ON-DEVICE TRANSCRIPTION • CHECK FAILED"
                }
            } else {
                "ON-DEVICE TRANSCRIPTION • REQUIRES ANDROID 12+ API CHECK / V226 PRERECORDED PATH REQUIRES API 33+"
            }

        val location =
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                "LOCATION / GPS • PERMISSION READY"
            } else {
                "LOCATION / GPS • PERMISSION NOT GRANTED"
            }

        val thermal =
            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
            ) {
                val pm =
                    getSystemService(
                        Context.POWER_SERVICE
                    ) as PowerManager

                "THERMAL MONITOR • API READY • STATUS ${pm.currentThermalStatus}"
            } else {
                "THERMAL MONITOR • LIMITED ON THIS ANDROID VERSION"
            }

        val free =
            try {
                val bytes =
                    StatFs(
                        Environment.getExternalStorageDirectory().path
                    ).availableBytes

                "STORAGE ACCESS • READY • FREE " +
                    formatBytes(
                        bytes
                    )
            } catch (_: Exception) {
                "STORAGE ACCESS • CHECK FAILED"
            }

        val codec =
            encoderStatus()

        addCard(
            "SYSTEM / NEWSROOM HEALTH",
            listOf(
                speech,
                location,
                thermal,
                free,
                codec,
                "SOCIAL EXPORT • MEDIA3 TRANSFORMER INSTALLED",
                "NEWSROOM INTAKE • V226 FIX2 MEDIASTORE PIPELINE RETAINED"
            ),
            true
        )
    }

    private fun addCameraSummary(
        provider: ProcessCameraProvider
    ) {
        val devices =
            DevelopUgandaLensIntelligence.devices(
                provider
            )

        addCard(
            "REAL CAMERA MAP",
            buildList {
                add(
                    "CAMERAX EXPOSED CAMERAS • ${devices.size}"
                )

                if (
                    devices.isEmpty()
                ) {
                    add(
                        "No Camera2 IDs were exposed through CameraX"
                    )
                } else {
                    devices.forEach {
                        add(
                            it.label()
                        )
                    }
                }
            },
            devices.isNotEmpty()
        )

        provider.availableCameraInfos
            .forEachIndexed {
                    index,
                    info ->
                try {
                    val camera2 =
                        Camera2CameraInfo.from(
                            info
                        )

                    val id =
                        camera2.cameraId

                    val caps =
                        Recorder.getVideoCapabilities(
                            info
                        )

                    val sdr =
                        caps.getSupportedQualities(
                            DynamicRange.SDR
                        )

                    val hdr =
                        caps.supportedDynamicRanges.contains(
                            DynamicRange.HLG_10_BIT
                        )

                    val uhd =
                        sdr.contains(
                            Quality.UHD
                        )

                    val stabilization =
                        caps.isStabilizationSupported

                    val fpsRanges =
                        camera2.getCameraCharacteristic(
                            CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
                        )
                            ?.toList()
                            ?: emptyList()

                    val has60Range =
                        fpsRanges.any {
                            it.upper >=
                                60
                        }

                    val photoFormats =
                        try {
                            ImageCapture
                                .getImageCaptureCapabilities(
                                    info
                                )
                                .supportedOutputFormats
                                .toSet()
                        } catch (_: Exception) {
                            emptySet()
                        }

                    val ultraHdr =
                        photoFormats.contains(
                            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR
                        )

                    val raw =
                        photoFormats.contains(
                            ImageCapture.OUTPUT_FORMAT_RAW
                        )

                    val rawJpeg =
                        photoFormats.contains(
                            ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                        )

                    val facing =
                        camera2.getCameraCharacteristic(
                            CameraCharacteristics.LENS_FACING
                        )

                    val facingLabel =
                        when (
                            facing
                        ) {
                            CameraCharacteristics.LENS_FACING_BACK ->
                                "BACK"

                            CameraCharacteristics.LENS_FACING_FRONT ->
                                "FRONT"

                            CameraCharacteristics.LENS_FACING_EXTERNAL ->
                                "EXTERNAL"

                            else ->
                                "UNKNOWN"
                        }

                    val focal =
                        camera2
                            .getCameraCharacteristic(
                                CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
                            )
                            ?.joinToString(
                                "/"
                            ) {
                                String.format(
                                    Locale.US,
                                    "%.1fmm",
                                    it
                                )
                            }
                            ?: "--"

                    addCard(
                        "CAMERA $index • $facingLabel • ID $id",
                        listOf(
                            "FOCAL LENGTHS • $focal",
                            "UHD / 4K SDR • ${yesNo(uhd)}",
                            "HLG 10-BIT HDR • ${yesNo(hdr)}",
                            "VIDEO STABILIZATION • ${yesNo(stabilization)}",
                            "60 FPS HARDWARE AE RANGE • ${yesNo(has60Range)} • ACTIVE SESSION STILL CONFIRMS AT BIND",
                            "JPEG • ${yesNo(photoFormats.contains(ImageCapture.OUTPUT_FORMAT_JPEG))}",
                            "ULTRA HDR JPEG_R • ${yesNo(ultraHdr)}",
                            "RAW DNG • ${yesNo(raw)}",
                            "RAW + JPEG • ${yesNo(rawJpeg)}",
                            "FPS RANGES • " +
                                if (
                                    fpsRanges.isEmpty()
                                ) {
                                    "--"
                                } else {
                                    fpsRanges.joinToString(
                                        " • "
                                    ) {
                                        "${it.lower}-${it.upper}"
                                    }
                                }
                        ),
                        true
                    )
                } catch (
                    e: Exception
                ) {
                    addCard(
                        "CAMERA $index",
                        listOf(
                            "Capability query failed • ${e.javaClass.simpleName}"
                        ),
                        false
                    )
                }
            }
    }

    private fun encoderStatus(): String {
        return try {
            val infos =
                MediaCodecList(
                    MediaCodecList.ALL_CODECS
                )
                    .codecInfos

            val h264 =
                infos.any {
                    it.isEncoder &&
                        it.supportedTypes.any {
                                type ->
                            type.equals(
                                "video/avc",
                                ignoreCase =
                                    true
                            )
                        }
                }

            val aac =
                infos.any {
                    it.isEncoder &&
                        it.supportedTypes.any {
                                type ->
                            type.equals(
                                "audio/mp4a-latm",
                                ignoreCase =
                                    true
                            )
                        }
                }

            "SOCIAL CODECS • H.264 ${yesNo(h264)} • AAC ${yesNo(aac)}"
        } catch (_: Exception) {
            "SOCIAL CODECS • QUERY FAILED"
        }
    }

    private fun addCard(
        title: String,
        lines: List<String>,
        healthy: Boolean
    ) {
        val card =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(11),
                    dp(10),
                    dp(11),
                    dp(10)
                )

                background =
                    rounded(
                        panel,
                        if (
                            healthy
                        ) {
                            green
                        } else {
                            amber
                        }
                    )
            }

        card.addView(
            label(
                title,
                11f,
                white,
                true
            )
        )

        lines.forEach {
            card.addView(
                label(
                    it,
                    8f,
                    if (
                        it.contains(
                            "UNAVAILABLE"
                        ) ||
                        it.contains(
                            "FAILED"
                        ) ||
                        it.contains(
                            "NOT GRANTED"
                        )
                    ) {
                        amber
                    } else {
                        muted
                    },
                    false
                ).apply {
                    setPadding(
                        0,
                        dp(3),
                        0,
                        0
                    )
                }
            )
        }

        host.addView(
            card,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin =
                    dp(7)
            }
        )
    }

    private fun label(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean
    ): TextView =
        TextView(
            this
        ).apply {
            text =
                value

            textSize =
                sp

            setTextColor(
                color
            )

            typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    if (
                        bold
                    ) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
        }

    private fun rounded(
        fill: Int,
        stroke: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius =
                dp(17).toFloat()

            setColor(
                fill
            )

            setStroke(
                dp(1),
                stroke
            )
        }

    private fun yesNo(
        value: Boolean
    ): String =
        if (
            value
        ) {
            "SUPPORTED"
        } else {
            "NOT REPORTED"
        }

    private fun formatBytes(
        value: Long
    ): String {
        val gb =
            value.toDouble() /
                (
                    1024.0 *
                        1024.0 *
                        1024.0
                    )

        return String.format(
            Locale.US,
            "%.1f GB",
            gb
        )
    }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources.displayMetrics.density
            ).roundToInt()
}
