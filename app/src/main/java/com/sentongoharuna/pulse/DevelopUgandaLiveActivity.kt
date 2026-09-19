package com.sentongoharuna.pulse

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Environment
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.StatFs
import android.provider.MediaStore
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.AspectRatio
import androidx.camera.core.DynamicRange
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.text.SimpleDateFormat
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin

class DevelopUgandaLiveActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var livePreviewToneView: View
    private lateinit var liveDirectorOverlayView: DevelopUgandaDirectorOverlayView

    private lateinit var liveBadge: TextView
    private lateinit var liveTitle: TextView
    private lateinit var liveSubTitle: TextView
    private lateinit var livePreviewMeta: TextView
    private lateinit var livePreviewTech: TextView
    private lateinit var liveAutoViewDescriptionView: TextView
    private lateinit var netLamp: TextView
    private lateinit var gpsLamp: TextView
    private lateinit var micLamp: TextView
    private lateinit var camLamp: TextView
    private lateinit var recLamp: TextView
    private lateinit var batteryLamp: TextView

    private lateinit var profileButton: Button
    private lateinit var qualityButton: Button
    private lateinit var audioButton: Button
    private lateinit var graphicsButton: Button
    private lateinit var lensButton: Button
    private lateinit var lightButton: Button
    private lateinit var outputButton: Button
    private lateinit var viewModeButton: Button
    private lateinit var settingsButton: Button
    private lateinit var identityButton: Button
    private lateinit var resetButton: Button
    private lateinit var countdownButton: Button
    private lateinit var markButton: Button
    private lateinit var styleButton: Button
    private lateinit var liveLockButton: Button
    private lateinit var liveHudSizeButton: Button
    private lateinit var liveHudContrastButton: Button
    private lateinit var liveHudBackingButton: Button
    private lateinit var liveEffectButton: Button
    private lateinit var liveColorButton: Button
    private lateinit var livePresetButton: Button
    private lateinit var liveSafeInfoButton: Button
    private lateinit var countdownView: TextView

    private val liveHudLabels =
        arrayOf(
            "COMPACT",
            "STANDARD",
            "LARGE"
        )

    private val liveHudScales =
        floatArrayOf(
            1.04f,
            1.16f,
            1.28f
        )

    private var liveHudSizeIndex = 1

    private val liveHudContrastLabels =
        arrayOf(
            "SOFT",
            "BALANCED",
            "STRONG"
        )

    private var liveHudContrastIndex = 1

    private val liveHudBackingLabels =
        arrayOf(
            "NONE",
            "SOFT",
            "STRONG"
        )

    private var liveHudBackingIndex = 1

    private val liveEffectLabels =
        arrayOf(
            "CLEAN",
            "NATURAL",
            "WARM",
            "COOL",
            "TEAL",
            "GOLD",
            "SOFT",
            "NIGHT"
        )

    private var liveEffectIndex = 0

    private val livePresetLabels =
        arrayOf(
            "CUSTOM",
            "BREAKING",
            "INTERVIEW",
            "EVENT",
            "COMMUNITY"
        )

    private var livePresetIndex = 0
    private lateinit var recordButton: LiveRecordButtonView

    private lateinit var timerView: TextView
    private lateinit var outputStatus: TextView
    private var broadcastCameraChrome: DevelopUgandaBroadcastCameraChrome.Controller? = null
    private var f12CameraShell: DevelopUgandaFivemods12CameraShell.Controller? = null
    private var f12ProtectionStopRequested = false
    private var f12PreflightWarnings = emptyList<String>()
    private var livePreflightOverrideWarnings = emptyList<String>()
    private var f12SafetyOverrideApprovedOnce = false

    private var liveCameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var overlayEffect: OverlayEffect? = null

    private var useFront = false
    private var audioEnabled = true
    private var graphicsEnabled = true

    private var quality = Quality.FHD
    private var qualityLabel = "SOCIAL 30"

    private val liveQualityProfiles =
        arrayOf(
            "SOCIAL 30",
            "SOCIAL 60",
            "UHD 30",
            "UHD 60",
            "HDR UHD",
            "SOCIAL HDR",
            "ACTION STAB",
            "ACTION 60",
            "LOW LIGHT",
            "HD FAST"
        )

    private var liveQualityIndex = 0
    private var liveActiveFpsLabel = "AUTO FPS"
    private var liveActiveStabilizationLabel = "STAB AUTO"
    private var liveActiveDynamicRangeLabel = "SDR"

    private val profiles =
        arrayOf(
            "BREAKING",
            "INTERVIEW",
            "EVENT",
            "TRAFFIC",
            "COMMUNITY"
        )

    private var profileIndex = 0
    private var halfPreviewMode = false

    private val lowerThirdStyles = DevelopUgandaBrandIdentity.lowerThirdStyles

    private var lowerThirdStyleIndex = 0
    private var countdownEnabled = true
    private var countdownRunning = false
    private var liveControlsLocked = false
    private val liveMarkers =
        mutableListOf<Long>()

    private var recordStartMs = 0L
    private var liveRecordStartUtc = "--"
    private var liveRecordingName = ""

    private val f9LiveDualOutputExporter by lazy {
        DevelopUgandaFivemods9DualOutputExporter(
            context = this,
            recordingActive = { recording != null },
            onFinished = { outcome ->
                if (outcome.complete) {
                    liveSubTitle.text = "LIVE STUDIO • DUAL SAVED • CLEAN + BRAND"
                    outputStatus.text = "DUAL • CLEAN + BRAND SAVED TO GALLERY"
                    toast("LIVE: CLEAN + BRAND saved to Gallery")
                } else {
                    liveSubTitle.text = "LIVE STUDIO • CLEAN SAVED • BRAND MISSING"
                    outputStatus.text = "DUAL • CLEAN SAVED • BRAND MISSING"
                    toast("LIVE: BRAND MISSING • CLEAN is in Gallery")
                }
            },
        )
    }
    private var f9LiveTake: DevelopUgandaFivemods9DualOutputExporter.TakeMetadata? = null
    @Volatile private var f9LiveActualIso: Int? = null
    @Volatile private var f9LiveActualShutterNs: Long? = null
    private val f9LiveCaptureCallback =
        object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: android.hardware.camera2.CameraCaptureSession,
                request: android.hardware.camera2.CaptureRequest,
                result: android.hardware.camera2.TotalCaptureResult
            ) {
                f9LiveActualIso =
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)
                f9LiveActualShutterNs =
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME)
                f12CameraShell?.onFrame()
            }
        }
    private var liveAudioAmplitude = 0.0
    private var liveAudioPeakAmplitude = 0.0
    private var liveBlinkOn = true
    private var livePreflightApprovedOnce = false
    private lateinit var liveAutoViewLabeler: ImageLabeler
    private var liveAutoViewBusy = false
    private var liveAutoViewSummary = "AUTO VIEW • analysing scene"
    private var lastV233LiveColorMonitorKey = ""
    private var v229LiveColorOverlayLabel = "AUTO"

    private lateinit var livePowerManager: PowerManager
    @Volatile private var liveThermalStatus =
        PowerManager.THERMAL_STATUS_NONE
    private var liveThermalListenerRegistered =
        false

    private val liveThermalStatusListener =
        PowerManager.OnThermalStatusChangedListener {
                status ->
            liveThermalStatus =
                status

            runOnUiThread {
                updateSignals()
            }
        }

    private var reporterName = "CITIZEN"
    private var storyId = "--"
    private var headline = "LIVE REPORT"

    private val uiHandler =
        Handler(Looper.getMainLooper())

    private val liveAutoViewRunnable =
        object : Runnable {
            override fun run() {
                analyzeLiveAutoViewFrame()

                uiHandler.postDelayed(
                    this,
                    3500L
                )
            }
        }

    private val liveDirectorRunnable =
        object : Runnable {
            override fun run() {
                if (
                    ::previewView.isInitialized &&
                    ::liveDirectorOverlayView.isInitialized &&
                    previewView.width > 0 &&
                    previewView.height > 0
                ) {
                    val bitmap =
                        try {
                            previewView.bitmap
                        } catch (_: Exception) {
                            null
                        }

                    if (
                        bitmap != null
                    ) {
                        liveDirectorOverlayView.submitFrame(
                            bitmap,
                            liveDirectorPeopleMode()
                        )
                    }
                }

                uiHandler.postDelayed(
                    this,
                    1200L
                )
            }
        }

    private val uiTicker =
        object : Runnable {
            override fun run() {
                updateSignals()
                updateTimer()
                updateBlink()

                uiHandler.postDelayed(
                    this,
                    500L
                )
            }
        }

    // Protected recorded-output palette.  Keep byte-for-byte separate from
    // FIVEMODS 8's on-screen chrome tokens.
    private val red = 0xFFC76D73.toInt()
    private val green = 0xFF91B6A0.toInt()
    private val amber = 0xFFAEBDEB.toInt()
    private val cyan = 0xFF8FA8E8.toInt()
    private val white = 0xFFF1F3F8.toInt()
    private val panel = DevelopUgandaFivemods8Theme.surfaceScrim(217)

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        livePowerManager =
            getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {
            liveThermalStatus =
                livePowerManager.currentThermalStatus

            try {
                livePowerManager.addThermalStatusListener(
                    liveThermalStatusListener
                )

                liveThermalListenerRegistered =
                    true
            } catch (_: Exception) {
                liveThermalListenerRegistered =
                    false
            }
        }

        loadLiveIdentity()
        loadLiveCameraPreferences()
        buildLiveUi()
        showLiveRecoveryNoticeIfNeeded()
        startLiveAutoViewDescription()
        uiHandler.postDelayed(
            liveDirectorRunnable,
            1200L
        )
        requestNeededPermissions()

        uiHandler.post(
            uiTicker
        )
    }

    override fun onResume() {
        super.onResume()
        broadcastCameraChrome?.onResume()
        f12CameraShell?.onResume()
    }

    override fun onPause() {
        if (recording != null) {
            DevelopUgandaV276RecordingSafety.addEvent(this, "INTERRUPTION • LIVE APP BACKGROUND/LOCK • FINALISE CLEAN")
            liveSubTitle.text = "LIVE STUDIO • INTERRUPTED • FINALISING CLEAN"
            runCatching { recording?.stop() }
        }
        f12CameraShell?.onPause()
        broadcastCameraChrome?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        broadcastCameraChrome?.onPause()
        uiHandler.removeCallbacksAndMessages(
            null
        )

        if (
            ::liveAutoViewLabeler.isInitialized
        ) {
            try {
                liveAutoViewLabeler.close()
            } catch (_: Exception) {
            }
        }

        recording?.stop()
        recording = null

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
            liveThermalListenerRegistered
        ) {
            try {
                livePowerManager.removeThermalStatusListener(
                    liveThermalStatusListener
                )
            } catch (_: Exception) {
            }

            liveThermalListenerRegistered =
                false
        }

        super.onDestroy()
    }

    /** Release Live while idle before opening another camera mode. */
    private fun releaseLiveCameraForModeSwitch() {
        if (recording != null) return
        try {
            liveCameraProvider?.unbindAll()
        } catch (_: Exception) {
        }
        try {
            overlayEffect?.close()
        } catch (_: Exception) {
        }
        liveCameraProvider = null
        overlayEffect = null
        camera = null
        videoCapture = null
    }

    private fun loadLiveIdentity() {
        val reporterPrefs =
            duSharedPreferences(
                "develop_uganda_reporter",
                Context.MODE_PRIVATE
            )

        reporterName =
            reporterPrefs.getString(
                "reporter_name",
                "CITIZEN"
            )
                ?.trim()
                ?.ifBlank {
                    "CITIZEN"
                }
                ?: "CITIZEN"

        storyId =
            reporterPrefs.getString(
                "story_id",
                "--"
            )
                ?.trim()
                ?.ifBlank {
                    "--"
                }
                ?: "--"

        val newsroomPrefs =
            duSharedPreferences(
                "develop_uganda_newsroom",
                Context.MODE_PRIVATE
            )

        headline =
            newsroomPrefs.getString(
                "headline",
                "LIVE REPORT"
            )
                ?.trim()
                ?.ifBlank {
                    "LIVE REPORT"
                }
                ?: "LIVE REPORT"
    }


    private fun startLiveAutoViewDescription() {
        if (
            ::liveAutoViewLabeler.isInitialized
        ) {
            return
        }

        liveAutoViewLabeler =
            ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(
                        0.62f
                    )
                    .build()
            )

        uiHandler.removeCallbacks(
            liveAutoViewRunnable
        )

        uiHandler.postDelayed(
            liveAutoViewRunnable,
            1700L
        )
    }

    private fun analyzeLiveAutoViewFrame() {
        if (
            liveAutoViewBusy ||
            !::previewView.isInitialized ||
            previewView.width <= 0 ||
            previewView.height <= 0
        ) {
            return
        }

        val bitmap =
            try {
                previewView.bitmap
            } catch (_: Exception) {
                null
            } ?: return

        liveAutoViewBusy =
            true

        liveAutoViewLabeler.process(
            InputImage.fromBitmap(
                bitmap,
                0
            )
        )
            .addOnSuccessListener {
                    labels ->
                val top =
                    labels
                        .sortedByDescending {
                            it.confidence
                        }
                        .filter {
                            it.confidence >= 0.62f
                        }
                        .take(3)
                        .map {
                            it.text.trim()
                        }
                        .filter {
                            it.isNotBlank()
                        }

                liveAutoViewSummary =
                    if (
                        top.isEmpty()
                    ) {
                        "AUTO VIEW • scene not confidently identified"
                    } else {
                        "AUTO VIEW • likely " +
                            top.joinToString(
                                " • "
                            )
                    }

                if (
                    ::liveAutoViewDescriptionView.isInitialized
                ) {
                    liveAutoViewDescriptionView.text =
                        liveAutoViewSummary
                }
            }
            .addOnFailureListener {
                liveAutoViewSummary =
                    "AUTO VIEW • analysing scene"

                if (
                    ::liveAutoViewDescriptionView.isInitialized
                ) {
                    liveAutoViewDescriptionView.text =
                        liveAutoViewSummary
                }
            }
            .addOnCompleteListener {
                liveAutoViewBusy =
                    false
            }
    }

    private fun buildLiveUi() {
        root =
            FrameLayout(this).apply {
                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surface
                )
            }

        previewView =
            PreviewView(this).apply {
                implementationMode =
                    PreviewView.ImplementationMode.COMPATIBLE

                scaleType =
                    PreviewView.ScaleType.FIT_CENTER
            }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        livePreviewToneView =
            View(this).apply {
                isClickable =
                    false

                isFocusable =
                    false

                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.transparent
                )
            }

        root.addView(
            livePreviewToneView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        liveDirectorOverlayView =
            DevelopUgandaDirectorOverlayView(
                this
            )

        root.addView(
            liveDirectorOverlayView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val topPanel =
            LinearLayout(this).apply {
                tag = "v237_live_top_panel"
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(10),
                    dp(14),
                    dp(10)
                )

                background =
                    rounded(
                        DevelopUgandaFivemods8Theme.surfaceScrim(58),
                        DevelopUgandaFivemods8Theme.transparent,
                        0
                    )
            }

        val header =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        liveBadge =
            label(
                "● LIVE",
                18f,
                DevelopUgandaModeProfiles.live.chromeAccentColor,
                true
            )

        liveTitle =
            label(
                DevelopUgandaBrandMetadataStore.previewTitle(
                    this,
                    "V238"
                ),
                20f,
                DevelopUgandaModeProfiles.live.chromeAccentColor,
                true
            ).apply {
                setPadding(
                    dp(12),
                    0,
                    0,
                    0
                )
            }

        header.addView(
            liveBadge
        )

        header.addView(
            liveTitle
        )

        topPanel.addView(
            header
        )

        liveSubTitle =
            label(
                "LIVE STUDIO • ${profiles[profileIndex]} • CONTINUOUS AF/AE/AWB • READY",
                10f,
                DevelopUgandaFivemods8Theme.content,
                true
            ).apply {
                tag = "v237_live_subtitle"
                setPadding(
                    0,
                    dp(3),
                    0,
                    dp(7)
                )
            }

        topPanel.addView(
            liveSubTitle
        )

        livePreviewMeta =
            label(
                "$reporterName • STORY $storyId • $headline",
                9f,
                DevelopUgandaFivemods8Theme.content,
                true
            ).apply {
                maxLines = 1
                isSingleLine = true
            }

        livePreviewTech =
            label(
                "TC 00:00:00 • $qualityLabel • MIC ON",
                8.5f,
                DevelopUgandaFivemods8Theme.accent,
                true
            ).apply {
                maxLines = 1
                isSingleLine = true

                setPadding(
                    0,
                    dp(2),
                    0,
                    dp(3)
                )
            }

        topPanel.addView(
            livePreviewMeta
        )

        topPanel.addView(
            livePreviewTech
        )

        liveAutoViewDescriptionView =
            label(
                "AUTO VIEW • analysing scene",
                8f,
                DevelopUgandaFivemods8Theme.accent,
                true
            ).apply {
                tag = "v237_live_autoview"
                maxLines = 1
                isSingleLine = true

                setPadding(
                    0,
                    dp(2),
                    0,
                    dp(3)
                )
            }

        topPanel.addView(
            liveAutoViewDescriptionView
        )

        val signalRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        netLamp =
            signal("● NET")

        gpsLamp =
            signal("● GPS")

        micLamp =
            signal("● MIC")

        camLamp =
            signal("● CAM")

        recLamp =
            signal("● REC")

        batteryLamp =
            signal("● BAT")

        listOf(
            netLamp,
            gpsLamp,
            micLamp,
            camLamp,
            recLamp,
            batteryLamp
        ).forEach {
            signalRow.addView(
                it,
                LinearLayout.LayoutParams(
                    0,
                    dp(26),
                    1f
                )
            )
        }

        topPanel.addView(
            signalRow
        )

        timerView =
            label(
                "00:00:00",
                13f,
                DevelopUgandaFivemods8Theme.content,
                true
            ).apply {
                tag = "v237_live_timer"
                typeface =
                    Typeface.MONOSPACE

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }

        topPanel.addView(
            timerView
        )

        val topPanelParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity =
                    Gravity.TOP

                // Screen-only safety inset. Recorded graphics keep their own
                // output-safe geometry and are not moved by this value.
                topMargin =
                    dp(42)

                leftMargin =
                    dp(8)

                rightMargin =
                    dp(8)
            }

        // The old LIVE top panel duplicated mode, format, timecode, battery
        // and audio. Keep its state objects for existing logic, but leave the
        // panel detached: the FIVEMODS 12 strip is the sole visible owner.
        topPanel.layoutParams = topPanelParams

        countdownView =
            label(
                "",
                72f,
                DevelopUgandaFivemods8Theme.content,
                true
            ).apply {
                gravity =
                    Gravity.CENTER

                visibility =
                    View.GONE

            }

        root.addView(
            countdownView,
            FrameLayout.LayoutParams(
                dp(190),
                dp(190),
                Gravity.CENTER
            )
        )

        val liveDeck =
            LinearLayout(this).apply {
                tag = "v237_live_deck"
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(10),
                    dp(9),
                    dp(10),
                    dp(10)
                )

                background =
                    rounded(
                        panel,
                        DevelopUgandaFivemods8Theme.transparent,
                        24
                    )
            }

        outputStatus =
            label(
                "OUTPUT • LOCAL LIVE CAPTURE • STREAM DESTINATION NOT CONNECTED",
                9f,
                DevelopUgandaFivemods8Theme.contentDim,
                true
            ).apply {
                tag = "v237_live_output_status"
                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    dp(8)
                )
            }

        liveDeck.addView(
            outputStatus
        )

        val row1 =
            LinearLayout(this).apply {
                tag = "v237_live_row1"
                orientation =
                    LinearLayout.HORIZONTAL
            }

        profileButton =
            liveSettingButton(
                "PROFILE ▾\n${profiles[profileIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveProfileDropdown(
                    profileButton
                )
            }

        qualityButton =
            liveSettingButton(
                "QUALITY ▾\n${liveQualityProfiles[liveQualityIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveQualityDropdown(
                    qualityButton
                )
            }

        audioButton =
            liveSettingButton(
                "AUDIO ▾\nON",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveAudioDropdown(
                    audioButton
                )
            }

        row1.addView(
            profileButton,
            weight()
        )

        row1.addView(
            qualityButton,
            weight()
        )

        row1.addView(
            audioButton,
            weight()
        )

        liveDeck.addView(
            row1
        )

        val row2 =
            LinearLayout(this).apply {
                tag = "v237_live_row2"
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(6),
                    0,
                    0
                )
            }

        graphicsButton =
            liveSettingButton(
                "GRAPHICS ▾\nON",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveGraphicsDropdown(
                    graphicsButton
                )
            }

        lensButton =
            liveSettingButton(
                "LENS ▾\nBACK",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveLensDropdown(
                    lensButton
                )
            }

        lightButton =
            liveSettingButton(
                "LIGHT ▾\nOFF",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveLightDropdown(
                    lightButton
                )
            }

        row2.addView(
            graphicsButton,
            weight()
        )

        row2.addView(
            lensButton,
            weight()
        )

        row2.addView(
            lightButton,
            weight()
        )

        liveDeck.addView(
            row2
        )

        val row3 =
            LinearLayout(this).apply {
                tag = "v237_live_row3"
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(6),
                    0,
                    0
                )
            }

        outputButton =
            liveSettingButton(
                "OUTPUT\nSETUP",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showOutputSetup()
            }

        val headlineButton =
            liveSettingButton(
                "LOWER THIRD\nEDIT",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLowerThirdEditor()
            }

        val infoButton =
            liveSettingButton(
                "SIGNALS\nINFO",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showSignalInfo()
            }

        row3.addView(
            outputButton,
            weight()
        )

        row3.addView(
            headlineButton,
            weight()
        )

        row3.addView(
            infoButton,
            weight()
        )

        liveDeck.addView(
            row3
        )

        val row4 =
            LinearLayout(this).apply {
                tag = "v237_live_row4"
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }

        viewModeButton =
            liveSettingButton(
                "VIEW ▾\nFULL",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveViewDropdown(
                    viewModeButton
                )
            }

        settingsButton =
            liveSettingButton(
                "SETTINGS\nLIVE",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveDetailedSettings()
            }

        identityButton =
            liveSettingButton(
                "REPORTER\nID",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveIdentityEditor()
            }

        resetButton =
            liveSettingButton(
                "RESET\nLIVE",
                DevelopUgandaFivemods8Theme.accent
            ) {
                resetLiveSettings()
            }

        listOf(
            viewModeButton,
            settingsButton,
            identityButton,
            resetButton
        ).forEachIndexed { index, button ->
            row4.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(42),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart = dp(4)
                    }
                }
            )
        }

        liveDeck.addView(
            row4
        )

        val row5 =
            LinearLayout(this).apply {
                tag = "v237_live_row5"
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }

        countdownButton =
            liveSettingButton(
                "COUNTDOWN ▾\n3 SEC",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveCountdownDropdown(
                    countdownButton
                )
            }.apply {
                isSelected =
                    true
            }

        markButton =
            liveSettingButton(
                "MARK\n0",
                DevelopUgandaFivemods8Theme.accent
            ) {
                addLiveMarker()
            }

        styleButton =
            liveSettingButton(
                "LOWER STYLE ▾\n${lowerThirdStyles[lowerThirdStyleIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveStyleDropdown(
                    styleButton
                )
            }

        liveLockButton =
            liveSettingButton(
                "LOCK\nOFF",
                DevelopUgandaFivemods8Theme.accent
            ) {
                liveControlsLocked =
                    !liveControlsLocked

                liveLockButton.text =
                    "LOCK\n" +
                        if (liveControlsLocked) {
                            "ON"
                        } else {
                            "OFF"
                        }

                liveLockButton.isSelected =
                    liveControlsLocked
            }

        listOf(
            countdownButton,
            markButton,
            styleButton,
            liveLockButton
        ).forEachIndexed { index, button ->
            row5.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(39),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart =
                            dp(4)
                    }
                }
            )
        }

        liveDeck.addView(
            row5
        )

        val displayRow =
            LinearLayout(this).apply {
                tag = "v237_live_display_row"
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }

        liveHudSizeButton =
            liveSettingButton(
                "HUD SIZE ▾\n${liveHudLabels[liveHudSizeIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveHudSizeDropdown(
                    liveHudSizeButton
                )
            }.apply {
                isSelected =
                    true
            }

        liveColorButton =
            liveSettingButton(
                "COLOR ▾\n${v229LiveColorDeckLabel()}",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showV233LiveColorDropdown(
                    liveColorButton
                )
            }

        liveSafeInfoButton =
            liveSettingButton(
                "OUTPUT\nSAFE",
                DevelopUgandaFivemods8Theme.contentDim
            ) {
                showLiveSafeAreaInfo()
            }

        liveHudContrastButton =
            liveSettingButton(
                "HUD CONTRAST ▾\n${liveHudContrastLabels[liveHudContrastIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveHudContrastDropdown(
                    liveHudContrastButton
                )
            }.apply {
                isSelected =
                    true
            }

        livePresetButton =
            liveSettingButton(
                "PRESET ▾\n${livePresetLabels[livePresetIndex]}",
                DevelopUgandaFivemods8Theme.contentDim
            ) {
                showLivePresetDropdown(
                    livePresetButton
                )
            }.apply {
                isSelected =
                    livePresetIndex !=
                        0
            }

        listOf(
            liveHudSizeButton,
            liveHudContrastButton,
            livePresetButton
        ).forEachIndexed { index, button ->
            displayRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(40),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart =
                            dp(6)
                    }
                }
            )
        }

        liveDeck.addView(
            displayRow
        )

        val outputRow =
            LinearLayout(this).apply {
                tag = "v237_live_output_row"
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    dp(5),
                    0,
                    0
                )
            }

        liveHudBackingButton =
            liveSettingButton(
                "HUD BACKING ▾\n${liveHudBackingLabels[liveHudBackingIndex]}",
                DevelopUgandaFivemods8Theme.contentDim
            ) {
                showLiveHudBackingDropdown(
                    liveHudBackingButton
                )
            }.apply {
                isSelected =
                    liveHudBackingIndex !=
                        0
            }

        liveEffectButton =
            liveSettingButton(
                "VIDEO FX ▾\n${liveEffectLabels[liveEffectIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ) {
                showLiveEffectDropdown(
                    liveEffectButton
                )
            }.apply {
                isSelected =
                    liveEffectIndex !=
                        0
            }

        listOf(
            liveHudBackingButton,
            liveEffectButton,
            liveColorButton,
            liveSafeInfoButton
        ).forEachIndexed { index, button ->
            outputRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(40),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart =
                            dp(6)
                    }
                }
            )
        }

        liveDeck.addView(
            outputRow
        )

        val recordArea =
            FrameLayout(this).apply {
                tag = "v237_live_record_area"
                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }

        recordButton =
            LiveRecordButtonView(
                this
            ).apply {
                setOnClickListener {
                    if (
                        recording != null
                    ) {
                        toggleRecording()
                    } else {
                        beginLiveRecordSequence()
                    }
                }
            }

        recordArea.addView(
            recordButton,
            FrameLayout.LayoutParams(
                dp(108),
                dp(108),
                Gravity.CENTER
            )
        )

        liveDeck.addView(
            recordArea,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(118)
            )
        )

        // Additive navigation only.  The existing Live controls and its
        // CameraX/telemetry pipeline are left in their original order.
        liveDeck.addView(
            DevelopUgandaCameraModeNavigator.create(
                activity = this,
                current = DevelopUgandaCameraPage.LIVE,
                dp = ::dp,
                canNavigate = { recording == null },
                releaseCamera = ::releaseLiveCameraForModeSwitch
            )
        )

        root.addView(
            liveDeck,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(
            root
        )
        DevelopUgandaLiveGradePanel.attach(
            activity = this,
            root = root,
            previewView = previewView,
            scopeProvider = { v229LiveColorScope() },
            hintProvider = { v229LiveColorHint() }
        )
        DevelopUgandaUnifiedControlDeck.attach(
            activity = this,
            root = root,
            scopeProvider = { v229LiveColorScope() },
            hintProvider = { v229LiveColorHint() },
            mode = DevelopUgandaUnifiedControlDeck.Mode.LIVE
        )
        DevelopUgandaFieldIntelligencePanel.attach(
            activity = this,
            root = root,
            previewView = previewView
        )
        DevelopUgandaAdaptiveFormatUi.attach(
            activity = this,
            root = root,
            role = DevelopUgandaAdaptiveFormatUi.Role.LIVE
        )
        DevelopUgandaOperatorExperience.attach(
            activity = this,
            root = root,
            role = DevelopUgandaOperatorExperience.Role.LIVE
        )
        broadcastCameraChrome = DevelopUgandaBroadcastCameraChrome.attach(
            activity = this,
            root = root,
            preview = previewView,
            profile = DevelopUgandaModeProfiles.live,
            onMark = ::addLiveMarker,
        )
        f12CameraShell = DevelopUgandaFivemods12CameraShell.attach(
            activity = this,
            root = root,
            preview = previewView,
            page = DevelopUgandaCameraPage.LIVE,
            isRecording = { recording != null },
            recordingDurationMs = {
                if (recordStartMs > 0L) (SystemClock.elapsedRealtime() - recordStartMs).coerceAtLeast(0L) else 0L
            },
            audioAmplitude = { if (recording != null) liveAudioAmplitude else null },
            restartSession = ::bindCamera,
        )
    }

    private fun togglePreviewMode() {
        halfPreviewMode =
            !halfPreviewMode

        val height =
            if (halfPreviewMode) {
                (
                    resources.displayMetrics.heightPixels *
                        0.50f
                    ).roundToInt()
            } else {
                ViewGroup.LayoutParams.MATCH_PARENT
            }

        val params =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
            ).apply {
                gravity = Gravity.TOP
            }

        previewView.layoutParams =
            params

        previewView.scaleType =
            if (halfPreviewMode) {
                PreviewView.ScaleType.FIT_CENTER
            } else {
                PreviewView.ScaleType.FILL_CENTER
            }

        viewModeButton.text =
            "VIEW ▾\n" +
                if (halfPreviewMode) {
                    "HALF"
                } else {
                    "FULL"
                }

        // The top narration is screen UI. Keep it inside the visible camera
        // area in either mode without changing the saved-video overlay.
        val top =
            liveBadge.parent
                ?.parent as?
                View

        val topLayout =
            top?.layoutParams as?
                FrameLayout.LayoutParams

        if (topLayout != null) {
            topLayout.topMargin =
                if (halfPreviewMode) {
                    dp(32)
                } else {
                    dp(42)
                }

            top.layoutParams =
                topLayout
        }

        toast(
            if (halfPreviewMode) {
                "Half-screen LIVE view"
            } else {
                "Full-screen LIVE camera behind controls"
            }
        )
    }

    private fun showLiveIdentityEditor() {
        val box =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(8),
                    dp(18),
                    dp(4)
                )
            }

        val reporterField =
            EditText(this).apply {
                hint = "Reporter name"
                setText(reporterName)
            }

        val storyField =
            EditText(this).apply {
                hint = "Story ID"
                setText(storyId)
            }

        box.addView(reporterField)
        box.addView(storyField)

        AlertDialog.Builder(this)
            .setTitle("LIVE REPORTER ID")
            .setView(box)
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->
                reporterName =
                    reporterField.text
                        .toString()
                        .trim()
                        .ifBlank {
                            "CITIZEN"
                        }

                storyId =
                    storyField.text
                        .toString()
                        .trim()
                        .ifBlank {
                            "--"
                        }

                duSharedPreferences(
                    "develop_uganda_reporter",
                    Context.MODE_PRIVATE
                )
                    .edit()
                    .putString(
                        "reporter_name",
                        reporterName
                    )
                    .putString(
                        "story_id",
                        storyId
                    )
                    .apply()

                toast("LIVE reporter identity saved")
            }
            .setNegativeButton(
                "CANCEL",
                null
            )
            .show()
    }

    private fun resetLiveSettings() {
        if (recording != null) {
            toast("Stop LIVE REC before reset")
            return
        }

        profileIndex = 0
        quality = Quality.FHD
        qualityLabel = "FHD"
        audioEnabled = true
        graphicsEnabled = true
        useFront = false
        halfPreviewMode = false

        profileButton.text =
            "PROFILE\\n${profiles[profileIndex]}"

        qualityButton.text =
            "QUALITY\\nFHD"

        audioButton.text =
            "AUDIO\\nON"

        graphicsButton.text =
            "GRAPHICS\\nON"

        lensButton.text =
            "LENS\\nBACK"

        viewModeButton.text =
            "VIEW\\nFULL"

        previewView.layoutParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.TOP
            }

        previewView.scaleType =
            PreviewView.ScaleType.FILL_CENTER

        bindCamera()
        toast("LIVE STUDIO reset")
    }

    private fun showLiveDetailedSettings() {
        val streamPrefs =
            duSharedPreferences(
                "develop_uganda_stream",
                Context.MODE_PRIVATE
            )

        val destination =
            streamPrefs.getString(
                "url",
                ""
            )
                ?.trim()
                .orEmpty()

        val summary =
            buildString {
                append("LIVE-ONLY SETTINGS • V188\n\n")

                append("PROFILE: ")
                append(
                    profiles[
                        profileIndex
                    ]
                )
                append("\n")

                append("QUALITY: ")
                append(
                    qualityLabel
                )
                append("\n")

                append("AUDIO: ")
                append(
                    if (audioEnabled) {
                        "ON"
                    } else {
                        "OFF"
                    }
                )
                append("\n")

                append("GRAPHICS: ")
                append(
                    if (graphicsEnabled) {
                        "ON"
                    } else {
                        "OFF"
                    }
                )
                append("\n")

                append("LENS: ")
                append(
                    if (useFront) {
                        "FRONT"
                    } else {
                        "BACK"
                    }
                )
                append("\n")

                append("COUNTDOWN: ")
                append(
                    if (countdownEnabled) {
                        "3 SEC"
                    } else {
                        "OFF"
                    }
                )
                append("\n")

                append("HUD SIZE: ")
                append(
                    liveHudLabels[
                        liveHudSizeIndex
                    ]
                )
                append("\n")

                append("HUD CONTRAST: ")
                append(
                    liveHudContrastLabels[
                        liveHudContrastIndex
                    ]
                )
                append("\n")

                append("HUD BACKING: ")
                append(
                    liveHudBackingLabels[
                        liveHudBackingIndex
                    ]
                )
                append("\n")

                append("VIDEO FX: ")
                append(
                    liveEffectLabels[
                        liveEffectIndex
                    ]
                )
                append("\n")

                append("CREATOR ENGINE: ${liveQualityProfiles[liveQualityIndex]} • $liveActiveFpsLabel • $liveActiveStabilizationLabel • $liveActiveDynamicRangeLabel • 9:16 SOCIAL SAFE\n")

                append("PRESET: ")
                append(
                    livePresetLabels[
                        livePresetIndex
                    ]
                )
                append("\n")

                append("SETTINGS MEMORY: ON\n")

                append("LOWER STYLE: ")
                append(
                    lowerThirdStyles[
                        lowerThirdStyleIndex
                    ]
                )
                append("\n")

                append("MARKERS: ")
                append(
                    liveMarkers.size
                )
                append("\n")

                append("PREVIEW: ")
                append(
                    if (halfPreviewMode) {
                        "HALF SCREEN"
                    } else {
                        "FULL SCREEN"
                    }
                )
                append("\n")

                append("LOWER THIRD: ")
                append(
                    headline.ifBlank {
                        "LIVE REPORT"
                    }
                )
                append("\n")

                append("OUTPUT: ")
                append(
                    if (destination.isBlank()) {
                        "LOCAL GALLERY"
                    } else {
                        "DESTINATION SAVED"
                    }
                )
                append("\n\n")

                append("SIGNALS: NET • GPS • MIC • CAM • REC • BAT\n")
                append("RECORD GRAPHICS: develop.uganda LIVE + profile + reporter + story + lower third\n")
                append("STREAMING: destination may be stored, but internet streaming remains inactive until an RTMP/SRT/WebRTC engine is connected.")
            }

        AlertDialog.Builder(this)
            .setTitle(
                "LIVE STUDIO SETTINGS"
            )
            .setMessage(
                summary
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }

    private fun requestNeededPermissions() {
        val wanted =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION
            )

        val missing =
            wanted.filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) !=
                    PackageManager.PERMISSION_GRANTED
            }

        if (
            missing.isEmpty()
        ) {
            bindCamera()
        } else {
            requestPermissions(
                missing.toTypedArray(),
                1841
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
            1841
        ) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                bindCamera()
            } else {
                toast(
                    "Camera permission is required"
                )
            }
        }
    }

    private fun bindCamera() {
        applyLiveThermalSafeProfileIfNeeded()

        val future =
            ProcessCameraProvider.getInstance(
                this
            )

        future.addListener(
            {
                val provider =
                    future.get()

                liveCameraProvider = provider
                provider.unbindAll()

                val selector =
                    if (useFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }

                val selectedCameraInfo =
                    try {
                        provider.getCameraInfo(
                            selector
                        )
                    } catch (_: Exception) {
                        null
                    }

                liveActiveFpsLabel =
                    if (
                        liveQualityProfiles[
                            liveQualityIndex
                        ] == "LOW LIGHT"
                    ) {
                        "AUTO LOW-LIGHT FPS"
                    } else {
                        "AUTO FPS"
                    }
                liveActiveStabilizationLabel =
                    "STAB OFF"
                liveActiveDynamicRangeLabel =
                    "SDR"

                val previewBuilder = Preview.Builder()
                f9AttachLiveCaptureTelemetry(previewBuilder)
                val preview =
                    previewBuilder
                        .build()
                        .also {
                            it.setSurfaceProvider(
                                previewView.surfaceProvider
                            )
                        }

                var selectedDynamicRange =
                    DynamicRange.SDR
                var enableVideoStabilization =
                    false
                var selectedQuality =
                    liveSelectedQuality()

                if (selectedCameraInfo != null) {
                    try {
                        val capabilities =
                            Recorder.getVideoCapabilities(
                                selectedCameraInfo
                            )

                        if (
                            liveWantsHdr() &&
                            capabilities.supportedDynamicRanges.contains(
                                DynamicRange.HLG_10_BIT
                            )
                        ) {
                            val hdrQualities =
                                capabilities.getSupportedQualities(
                                    DynamicRange.HLG_10_BIT
                                )

                            selectedQuality =
                                when {
                                    hdrQualities.contains(
                                        Quality.UHD
                                    ) -> Quality.UHD
                                    hdrQualities.contains(
                                        Quality.FHD
                                    ) -> Quality.FHD
                                    hdrQualities.contains(
                                        Quality.HD
                                    ) -> Quality.HD
                                    else -> selectedQuality
                                }

                            if (hdrQualities.isNotEmpty()) {
                                selectedDynamicRange =
                                    DynamicRange.HLG_10_BIT
                                liveActiveDynamicRangeLabel =
                                    "HLG10 HDR"
                            } else {
                                liveActiveDynamicRangeLabel =
                                    "SDR HDR-FALLBACK"
                            }
                        } else if (liveWantsHdr()) {
                            liveActiveDynamicRangeLabel =
                                "SDR HDR-FALLBACK"
                        }

                        enableVideoStabilization =
                            liveWantsStabilization() &&
                                capabilities.isStabilizationSupported

                        liveActiveStabilizationLabel =
                            if (enableVideoStabilization) {
                                "STAB ON"
                            } else if (liveWantsStabilization()) {
                                "STAB UNSUPPORTED"
                            } else {
                                "STAB OFF"
                            }
                    } catch (_: Exception) {
                        liveActiveDynamicRangeLabel =
                            if (liveWantsHdr()) {
                                "SDR HDR-FALLBACK"
                            } else {
                                "SDR"
                            }
                        liveActiveStabilizationLabel =
                            "STAB AUTO"
                    }
                }

                val recorder =
                    Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(
                                selectedQuality
                            )
                        )
                        .setAspectRatio(
                            AspectRatio.RATIO_16_9
                        )
                        .setTargetVideoEncodingBitRate(
                            liveTargetBitrate()
                        )
                        .build()

                val videoBuilder =
                    VideoCapture.Builder(
                        recorder
                    )

                if (
                    selectedDynamicRange !=
                    DynamicRange.SDR
                ) {
                    videoBuilder.setDynamicRange(
                        selectedDynamicRange
                    )
                }

                if (enableVideoStabilization) {
                    videoBuilder.setVideoStabilizationEnabled(
                        true
                    )
                }

                videoCapture =
                    videoBuilder.build()

                overlayEffect =
                    OverlayEffect(
                        CameraEffect.PREVIEW,
                        0,
                        Handler(
                            Looper.getMainLooper()
                        )
                    ) { throwable ->
                        toast(
                            "LIVE graphics warning: " +
                                (throwable.message ?: "unknown")
                        )
                    }.also { effect ->
                        effect.setOnDrawListener { frame ->
                            drawLiveBroadcastOverlay(
                                frame
                            )
                            true
                        }
                    }

                var session =
                    SessionConfig.Builder(
                        preview,
                        videoCapture!!
                    )
                        .addEffect(
                            overlayEffect!!
                        )
                        .build()

                if (selectedCameraInfo != null) {
                    val requestedFps =
                        liveRequestedFps()

                    if (requestedFps > 0) {
                        try {
                            val supportedRanges =
                                selectedCameraInfo
                                    .getSupportedFrameRateRanges(
                                        session
                                    )

                            val exactRange =
                                supportedRanges.firstOrNull {
                                    it.lower == requestedFps &&
                                        it.upper == requestedFps
                                }

                            val compatibleRange =
                                exactRange
                                    ?: supportedRanges
                                        .filter {
                                            it.lower <= requestedFps &&
                                                it.upper >= requestedFps
                                        }
                                        .minByOrNull {
                                            it.upper - it.lower
                                        }

                            if (compatibleRange != null) {
                                session =
                                    SessionConfig.Builder(
                                        preview,
                                        videoCapture!!
                                    )
                                        .addEffect(
                                            overlayEffect!!
                                        )
                                        .setFrameRateRange(
                                            compatibleRange
                                        )
                                        .build()

                                liveActiveFpsLabel =
                                    if (exactRange != null) {
                                        "${requestedFps} FPS"
                                    } else {
                                        "${compatibleRange.lower}-${compatibleRange.upper} FPS FALLBACK"
                                    }
                            } else {
                                liveActiveFpsLabel =
                                    "AUTO FPS FALLBACK"
                            }
                        } catch (_: Exception) {
                            liveActiveFpsLabel =
                                "AUTO FPS"
                        }
                    } else {
                        liveActiveFpsLabel =
                            "AUTO LOW-LIGHT FPS"
                    }
                }

                camera =
                    provider.bindToLifecycle(
                        this,
                        selector,
                        session
                    )

                camera
                    ?.cameraInfo
                    ?.exposureState
                    ?.let { exposure ->
                        if (
                            exposure.isExposureCompensationSupported
                        ) {
                            camera
                                ?.cameraControl
                                ?.setExposureCompensationIndex(
                                    0.coerceIn(
                                        exposure.exposureCompensationRange.lower,
                                        exposure.exposureCompensationRange.upper
                                    )
                                )
                        }
                    }

                quality =
                    selectedQuality
                qualityLabel =
                    liveQualityProfiles[
                        liveQualityIndex
                    ]

                camLamp.setTextColor(
                    DevelopUgandaFivemods8Theme.accent
                )

                updateTimer()
            },
            ContextCompat.getMainExecutor(
                this
            )
        )
    }

    private fun drawLiveVideoEffect(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        val color =
            when (
                liveEffectLabels[
                    liveEffectIndex
                ]
            ) {
                "NATURAL" ->
                    0x05FFF4E8

                "WARM" ->
                    0x0BFF9555

                "COOL" ->
                    0x0A3E7EFF

                "TEAL" ->
                    0x0B00A7A1

                "GOLD" ->
                    0x0CF2B43C

                "SOFT" ->
                    0x08FFFFFF

                "NIGHT" ->
                    0x12092346

                else ->
                    Color.TRANSPARENT
            }

        if (
            color ==
            Color.TRANSPARENT
        ) {
            return
        }

        val grade =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                this.color =
                    color
            }

        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            grade
        )
    }

    private fun drawLiveBroadcastOverlay(
        frame: Frame
    ) {
        val canvas =
            frame.overlayCanvas

        val crop =
            frame.cropRect

        if (
            crop.width() <=
                0 ||
            crop.height() <=
                0
        ) {
            return
        }

        canvas.drawColor(
            Color.TRANSPARENT,
            android.graphics.PorterDuff.Mode.CLEAR
        )

        val rotation =
            (
                (
                    frame.rotationDegrees %
                        360
                    ) +
                    360
                ) %
                360

        val finalWidth =
            if (
                rotation ==
                    90 ||
                rotation ==
                    270
            ) {
                crop.height().toFloat()
            } else {
                crop.width().toFloat()
            }

        val finalHeight =
            if (
                rotation ==
                    90 ||
                rotation ==
                    270
            ) {
                crop.width().toFloat()
            } else {
                crop.height().toFloat()
            }

        val l =
            crop.left.toFloat()

        val t =
            crop.top.toFloat()

        val r =
            crop.right.toFloat()

        val b =
            crop.bottom.toFloat()

        val nonMirrored =
            when (
                rotation
            ) {
                90 ->
                    floatArrayOf(
                        l, b,
                        l, t,
                        r, t,
                        r, b
                    )

                180 ->
                    floatArrayOf(
                        r, b,
                        l, b,
                        l, t,
                        r, t
                    )

                270 ->
                    floatArrayOf(
                        r, t,
                        r, b,
                        l, b,
                        l, t
                    )

                else ->
                    floatArrayOf(
                        l, t,
                        r, t,
                        r, b,
                        l, b
                    )
            }

        val destination =
            if (
                frame.isMirroring
            ) {
                floatArrayOf(
                    nonMirrored[2],
                    nonMirrored[3],
                    nonMirrored[0],
                    nonMirrored[1],
                    nonMirrored[6],
                    nonMirrored[7],
                    nonMirrored[4],
                    nonMirrored[5]
                )
            } else {
                nonMirrored
            }

        val source =
            floatArrayOf(
                0f,
                0f,
                finalWidth,
                0f,
                finalWidth,
                finalHeight,
                0f,
                finalHeight
            )

        val matrix =
            Matrix()

        if (
            !matrix.setPolyToPoly(
                source,
                0,
                destination,
                0,
                4
            )
        ) {
            return
        }

        canvas.save()
        canvas.concat(
            matrix
        )

        drawLiveVideoEffect(
            canvas,
            finalWidth,
            finalHeight
        )

        if (
            !graphicsEnabled
        ) {
            canvas.restore()
            return
        }

        val brandConfig =
            DevelopUgandaBrandMetadataStore
                .snapshot(
                    this
                )

        val u =
            minOf(
                finalWidth,
                finalHeight
            ) /
                1000f *
                liveHudScales[
                    liveHudSizeIndex
                ]

        val safeLeft =
            finalWidth *
                0.16f

        val safeTop =
            finalHeight *
                0.075f

        val safeRight =
            finalWidth *
                0.84f

        val paint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                typeface =
                    Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                    )

            }

        val liveOn =
            recording !=
                null

        val blink =
            (
                SystemClock.elapsedRealtime() /
                    500L
                ) %
                2L ==
                0L

        val brandX =
            safeLeft +
                (
                    23f *
                        u
                    )

        var brandWidth =
            0f

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .BRAND
            )
        ) {
            paint.textSize =
                38f *
                    u

            paint.color =
                amber

            drawStrongLiveText(
                canvas,
                brandConfig.displayName,
                brandX,
                safeTop,
                paint
            )

            brandWidth =
                paint.measureText(
                    brandConfig.displayName
                )

            if (
                brandConfig.organization
                    .isNotBlank()
            ) {
                paint.textSize =
                    10.8f *
                        u

                paint.color =
                    white

                drawFitText(
                    canvas,
                    brandConfig.organization,
                    brandX,
                    safeTop +
                        (
                            15f *
                                u
                            ),
                    safeRight -
                        brandX,
                    paint,
                    8.8f *
                        u
                )
            }
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .VERSION
            )
        ) {
            val buildText =
                "V238"

            val buildLeft =
                if (
                    brandWidth >
                        0f
                ) {
                    brandX +
                        brandWidth +
                        (
                            13f *
                                u
                            )
                } else {
                    brandX
                }

            val buildTop =
                safeTop -
                    (
                        25f *
                            u
                        )

            val buildRight =
                buildLeft +
                    (
                        72f *
                            u
                        )

            val buildBottom =
                safeTop +
                    (
                        4f *
                            u
                        )

            val buildPlate =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color =
                        0xD9163B5A.toInt()

                    style =
                        Paint.Style.FILL
                }

            val buildStroke =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color =
                        cyan

                    style =
                        Paint.Style.STROKE

                    strokeWidth =
                        1.4f *
                            u
                }

            canvas.drawRoundRect(
                buildLeft,
                buildTop,
                buildRight,
                buildBottom,
                8f *
                    u,
                8f *
                    u,
                buildPlate
            )

            canvas.drawRoundRect(
                buildLeft,
                buildTop,
                buildRight,
                buildBottom,
                8f *
                    u,
                8f *
                    u,
                buildStroke
            )

            val buildPaint =
                Paint(
                    Paint.ANTI_ALIAS_FLAG
                ).apply {
                    color =
                        white

                    textSize =
                        12.4f *
                            u

                    typeface =
                        Typeface.create(
                            Typeface.MONOSPACE,
                            Typeface.BOLD
                        )
                }

            canvas.drawText(
                buildText,
                buildLeft +
                    (
                        14f *
                            u
                        ),
                safeTop -
                    (
                        6f *
                            u
                        ),
                buildPaint
            )
        }

        // LIVE/REC is a broadcast-state indicator, not optional metadata.
        // Keep it in the separate V227 lane so it never overlaps the brand/build.
        val badgeLeft =
            safeRight -
                (
                    160f *
                        u
                    )

        val badgeTop =
            safeTop +
                (
                    17f *
                        u
                    )

        val badgeRight =
            safeRight

        val badgeBottom =
            safeTop +
                (
                    51f *
                        u
                    )


        val badgeBackground =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        if (
                            liveOn &&
                            blink
                        ) {
                            245
                        } else if (
                            liveOn
                        ) {
                            145
                        } else {
                            105
                        },
                        184,
                        48,
                        44
                    )

                style =
                    Paint.Style.FILL
            }

        canvas.drawRoundRect(
            badgeLeft,
            badgeTop,
            badgeRight,
            badgeBottom,
            11f *
                u,
            11f *
                u,
            badgeBackground
        )

        val badgeCenterY =
            (
                badgeTop +
                    badgeBottom
                ) /
                2f

        val signalDot =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        if (
                            liveOn &&
                            blink
                        ) {
                            255
                        } else {
                            160
                        },
                        255,
                        255,
                        255
                    )

                style =
                    Paint.Style.FILL
            }

        canvas.drawCircle(
            badgeLeft +
                (
                    15f *
                        u
                    ),
            badgeCenterY,
            5.1f *
                u,
            signalDot
        )

        val pulseRing =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        if (
                            liveOn &&
                            blink
                        ) {
                            230
                        } else {
                            92
                        },
                        255,
                        255,
                        255
                    )

                style =
                    Paint.Style.STROKE

                strokeWidth =
                    1.5f *
                        u
            }

        canvas.drawCircle(
            badgeLeft +
                (
                    15f *
                        u
                    ),
            badgeCenterY,
            if (
                liveOn &&
                blink
            ) {
                9.2f *
                    u
            } else {
                7.0f *
                    u
            },
            pulseRing
        )

        val badgeText =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        if (
                            liveOn &&
                            blink
                        ) {
                            255
                        } else {
                            188
                        },
                        255,
                        255,
                        255
                    )

                textSize =
                    14.2f *
                        u

                typeface =
                    Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                    )
            }

        canvas.drawText(
            if (
                liveOn
            ) {
                "LIVE  •  REC"
            } else {
                "LIVE  •  READY"
            },
            badgeLeft +
                (
                    29f *
                        u
                    ),
            badgeCenterY +
                (
                    5f *
                        u
                    ),
            badgeText
        )

        val rule =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    0xB0FF3B32.toInt()

                strokeWidth =
                    1.5f *
                        u
            }

        canvas.drawLine(
            safeLeft,
            safeTop +
                (
                    63f *
                        u
                    ),
            safeRight,
            safeTop +
                (
                    63f *
                        u
                    ),
            rule
        )

        var rowY =
            safeTop +
                (
                    84f *
                        u
                    )

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .CAMERA_MODE
            ) ||
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .REPORTER
            )
        ) {
            val parts =
                mutableListOf<String>()

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .CAMERA_MODE
                )
            ) {
                parts.add(
                    "LIVE PROFILE • ${profiles[profileIndex]}"
                )
            }

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .REPORTER
                )
            ) {
                parts.add(
                    "REPORTER • $reporterName"
                )
            }

            paint.textSize =
                12f *
                    u

            paint.color =
                white

            drawFitText(
                canvas,
                parts.joinToString(
                    "   |   "
                ),
                safeLeft,
                rowY,
                safeRight -
                    safeLeft,
                paint,
                10.4f *
                    u
            )

            rowY +=
                20f *
                    u
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .STORY
            ) ||
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .AUDIO
            )
        ) {
            val parts =
                mutableListOf<String>()

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .STORY
                )
            ) {
                parts.add(
                    "STORY • $storyId"
                )
            }

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .AUDIO
                )
            ) {
                parts.add(
                    if (
                        audioEnabled
                    ) {
                        "AUDIO ON"
                    } else {
                        "AUDIO OFF"
                    }
                )
            }

            paint.textSize =
                13.5f *
                    u

            paint.color =
                white

            drawFitText(
                canvas,
                parts.joinToString(
                    "   |   "
                ),
                safeLeft,
                rowY,
                safeRight -
                    safeLeft,
                paint,
                10.2f *
                    u
            )

            rowY +=
                20f *
                    u
        }

        val statusParts =
            mutableListOf(
                if (
                    liveOn
                ) {
                    "ON AIR"
                } else {
                    "READY"
                },
                "TIMECODE • ${liveTimecode()}"
            )

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .CAMERA_MODE
            )
        ) {
            statusParts.add(
                "MODE • ${liveQualityProfiles[liveQualityIndex]}"
            )

            statusParts.add(
                "LOOK • ${liveEffectLabels[liveEffectIndex]}"
            )

            statusParts.add(
                "COLOR • $v229LiveColorOverlayLabel"
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .VERSION
            )
        ) {
            statusParts.add(
                "V238"
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .THERMAL
            )
        ) {
            statusParts.add(
                "THERMAL • ${liveThermalStateLabel()}"
            )
        }

        paint.textSize =
            15.4f *
                u

        paint.color =
            cyan

        drawFitText(
            canvas,
            statusParts.joinToString(
                "   |   "
            ),
            safeLeft,
            rowY,
            safeRight -
                safeLeft,
            paint,
            10.1f *
                u
        )

        val lowerY =
            finalHeight *
                0.76f

        val lowerStyle =
            lowerThirdStyles[
                lowerThirdStyleIndex
            ]

        val lowerAccent =
            when (
                lowerStyle
            ) {
                "CLEAN" ->
                    cyan

                "URGENT" ->
                    0xFFFF8A00.toInt()

                "MINIMAL" ->
                    white

                else ->
                    red
            }

        val lowerBg =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    when (
                        lowerStyle
                    ) {
                        "MINIMAL" ->
                            0x8205080A.toInt()

                        "CLEAN" ->
                            0x8F05080A.toInt()

                        else ->
                            0x8405080A.toInt()
                    }

                style =
                    Paint.Style.FILL
            }

        val lowerH =
            102f *
                u

        canvas.drawRoundRect(
            safeLeft,
            lowerY,
            safeRight,
            lowerY +
                lowerH,
            12f *
                u,
            12f *
                u,
            lowerBg
        )

        val redRail =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    lowerAccent

                strokeWidth =
                    5f *
                        u
            }

        canvas.drawLine(
            safeLeft +
                (
                    8f *
                        u
                    ),
            lowerY +
                (
                    10.6f *
                        u
                    ),
            safeLeft +
                (
                    8f *
                        u
                    ),
            lowerY +
                lowerH -
                (
                    12.0f *
                        u
                    ),
            redRail
        )

        paint.color =
            lowerAccent

        paint.textSize =
            12f *
                u

        canvas.drawText(
            if (
                liveOn
            ) {
                "● LIVE NOW • $lowerStyle"
            } else {
                "LIVE READY • $lowerStyle"
            },
            safeLeft +
                (
                    22f *
                        u
                    ),
            lowerY +
                (
                    24f *
                        u
                    ),
            paint
        )

        paint.color =
            white

        paint.textSize =
            25.0f *
                u

        drawFitText(
            canvas,
            headline,
            safeLeft +
                (
                    22f *
                        u
                    ),
            lowerY +
                (
                    60f *
                        u
                    ),
            safeRight -
                safeLeft -
                (
                    40f *
                        u
                    ),
            paint,
            13f *
                u
        )

        val lowerParts =
            mutableListOf<String>()

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .CAMERA_MODE
            )
        ) {
            lowerParts.add(
                "CAMERA • LIVE STUDIO"
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .REPORTER
            )
        ) {
            lowerParts.add(
                "REPORTER • $reporterName"
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .STORY
            )
        ) {
            lowerParts.add(
                "STORY • $storyId"
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .BRAND
            )
        ) {
            lowerParts.add(
                brandConfig.displayName
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .VERSION
            )
        ) {
            lowerParts.add(
                "V238"
            )
        }

        val credit =
            brandConfig.creditLine()

        if (
            credit.isNotBlank()
        ) {
            lowerParts.add(
                credit
            )
        }

        if (
            lowerParts.isNotEmpty()
        ) {
            paint.color =
                0xFFD0D8DC.toInt()

            paint.textSize =
                10f *
                    u

            drawFitText(
                canvas,
                lowerParts.joinToString(
                    "   |   "
                ),
                safeLeft +
                    (
                        22f *
                            u
                        ),
                lowerY +
                    (
                        84f *
                            u
                        ),
                safeRight -
                    safeLeft -
                    (
                        40f *
                            u
                        ),
                paint,
                8.8f *
                    u
            )
        }

        canvas.restore()
    }


    private fun drawStrongLiveText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        drawLiveTextBackplate(
            canvas,
            value,
            x,
            y,
            paint
        )

        val savedStyle =
            paint.style

        val savedColor =
            paint.color

        val savedStroke =
            paint.strokeWidth

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            paint.textSize *
                liveHudOutlineScale()

        paint.color =
            liveHudOutlineColor()

        canvas.drawText(
            value,
            x,
            y,
            paint
        )

        paint.style =
            Paint.Style.FILL

        paint.strokeWidth =
            savedStroke

        paint.color =
            savedColor

        canvas.drawText(
            value,
            x,
            y,
            paint
        )

        paint.style =
            savedStyle
    }

    private fun drawFitText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        maxWidth: Float,
        paint: Paint,
        minSize: Float
    ) {
        val original =
            paint.textSize

        var size =
            original

        while (
            paint.measureText(
                value
            ) >
                maxWidth &&
            size >
                minSize
        ) {
            size -=
                0.7f

            paint.textSize =
                size
        }

        drawLiveTextBackplate(
            canvas,
            value,
            x,
            y,
            paint
        )

        val savedStyle =
            paint.style

        val savedColor =
            paint.color

        val savedStroke =
            paint.strokeWidth

        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            paint.textSize *
                liveHudOutlineScale()

        paint.color =
            liveHudOutlineColor()

        canvas.drawText(
            value,
            x,
            y,
            paint
        )

        paint.style =
            Paint.Style.FILL

        paint.strokeWidth =
            savedStroke

        paint.color =
            savedColor

        canvas.drawText(
            value,
            x,
            y,
            paint
        )

        paint.style =
            savedStyle

        paint.textSize =
            original
    }

    private fun liveTimecode(): String {
        if (
            recording == null ||
            recordStartMs == 0L
        ) {
            return "00:00:00"
        }

        val total =
            (
                SystemClock.elapsedRealtime() -
                    recordStartMs
                ) /
                1000L

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            total / 3600L,
            (total / 60L) % 60L,
            total % 60L
        )
    }

    private fun liveSelectedQuality(): Quality {
        return when (
            liveQualityProfiles[
                liveQualityIndex
            ]
        ) {
            "UHD 30",
            "UHD 60",
            "HDR UHD" -> Quality.UHD
            "SOCIAL HDR" -> Quality.FHD
            "HD FAST" -> Quality.HD
            else -> Quality.FHD
        }
    }

    private fun liveEstimatedRecordingTimeText(): String {
        val minutes = DevelopUgandaV276RecordingSafety.estimatedRecordMinutes(
            this,
            DevelopUgandaCameraPage.LIVE,
        ) ?: return "EST REC UNKNOWN • NO MEASURED BITRATE"
        return if (minutes >= 60) {
            String.format(Locale.US, "EST REC %dh %02dm", minutes / 60, minutes % 60)
        } else {
            String.format(Locale.US, "EST REC %dm", minutes)
        }
    }

    private fun liveDirectorPeopleMode(): Boolean {
        return profiles[
            profileIndex
        ] ==
            "INTERVIEW" ||
            livePresetLabels[
                livePresetIndex
            ] ==
                "INTERVIEW"
    }

    private fun liveTargetBitrate(): Int {
        return when (
            liveQualityProfiles[
                liveQualityIndex
            ]
        ) {
            "SOCIAL 30" -> 24_000_000
            "SOCIAL 60" -> 42_000_000
            "UHD 30" -> 64_000_000
            "UHD 60" -> 90_000_000
            "HDR UHD" -> 72_000_000
            "SOCIAL HDR" -> 34_000_000
            "ACTION STAB" -> 30_000_000
            "ACTION 60" -> 48_000_000
            "LOW LIGHT" -> 28_000_000
            "HD FAST" -> 12_000_000
            else -> 24_000_000
        }
    }

    private fun liveRequestedFps(): Int {
        return when (
            liveQualityProfiles[
                liveQualityIndex
            ]
        ) {
            "SOCIAL 60",
            "UHD 60",
            "ACTION 60" -> 60
            "LOW LIGHT" -> 0
            else -> 30
        }
    }

    private fun liveWantsHdr(): Boolean {
        return liveQualityProfiles[
            liveQualityIndex
        ] in
            setOf(
                "HDR UHD",
                "SOCIAL HDR"
            )
    }

    private fun liveWantsStabilization(): Boolean {
        return when (
            liveQualityProfiles[
                liveQualityIndex
            ]
        ) {
            "SOCIAL 30",
            "ACTION STAB",
            "ACTION 60",
            "LOW LIGHT" -> true
            else -> false
        }
    }

    private fun syncLiveQualityState() {
        quality =
            liveSelectedQuality()
        qualityLabel =
            liveQualityProfiles[
                liveQualityIndex
            ]
    }

    private fun cycleProfile() {
        if (
            recording != null
        ) {
            toast(
                "Stop LIVE REC before changing profile"
            )
            return
        }

        profileIndex =
            (
                profileIndex +
                    1
                ) %
                profiles.size

        profileButton.text =
            "PROFILE ▾\n${profiles[profileIndex]}"

        liveSubTitle.text =
            "LIVE STUDIO • ${profiles[profileIndex]} • CONTINUOUS AF/AE/AWB • READY"
    }

    private fun cycleQuality() {
        if (recording != null) {
            toast(
                "Stop LIVE REC before changing quality"
            )
            return
        }

        liveQualityIndex =
            (liveQualityIndex + 1) %
                liveQualityProfiles.size

        syncLiveQualityState()

        qualityButton.text =
            "QUALITY ▾\n${liveQualityProfiles[liveQualityIndex]}"

        saveLiveCameraPreferences()
        bindCamera()
    }

    private fun toggleTorch() {
        val state =
            camera
                ?.cameraInfo
                ?.torchState
                ?.value
                ?: 0

        val enable =
            state !=
                androidx.camera.core.TorchState.ON

        camera
            ?.cameraControl
            ?.enableTorch(
                enable
            )

        lightButton.text =
            "LIGHT\n" +
                if (
                    enable
                ) {
                    "ON"
                } else {
                    "OFF"
                }
    }

    private data class LivePreflight(
        val critical: List<String>,
        val warnings: List<String>,
        val ready: List<String>
    )

    private fun liveFreeStorageGb(): Long? {
        return DevelopUgandaV276RecordingSafety.freeStorageBytesMeasured(this)
            ?.div(1024L * 1024L * 1024L)
    }

    private fun liveBatteryPct(): Int? {
        val batteryManager =
            getSystemService(
                BATTERY_SERVICE
            ) as BatteryManager

        return batteryManager.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        ).takeIf {
            it >=
                0
        }
    }

    private fun liveNetworkReady(): Boolean {
        val cm =
            getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network =
            cm.activeNetwork
                ?: return false

        val caps =
            cm.getNetworkCapabilities(
                network
            ) ?: return false

        return caps.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    private fun buildLivePreflight(): LivePreflight {
        val critical =
            mutableListOf<String>()

        val warnings =
            mutableListOf<String>()

        val ready =
            mutableListOf<String>()

        if (
            camera ==
                null
        ) {
            critical.add(
                "CAMERA NOT READY"
            )
        } else {
            ready.add(
                "CAM READY"
            )
        }

        val storage =
            liveFreeStorageGb()

        when {
            storage ==
                null ->
                    warnings.add(
                        "SPACE UNKNOWN"
                    )

            storage <=
                1L ->
                    critical.add(
                        "STORAGE CRITICAL ${storage}GB"
                    )

            storage <=
                4L ->
                    warnings.add(
                        "STORAGE LOW ${storage}GB"
                    )

            else ->
                ready.add(
                    "SPACE ${storage}GB"
                )
        }

        val battery =
            liveBatteryPct()

        when {
            battery ==
                null ->
                    warnings.add(
                        "BATTERY UNKNOWN"
                    )

            battery <=
                3 ->
                    critical.add(
                        "BATTERY CRITICAL $battery%"
                    )

            battery <=
                10 ->
                    warnings.add(
                        "BATTERY LOW $battery%"
                    )

            else ->
                ready.add(
                    "BATTERY $battery%"
                )
        }

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
            liveThermalStatus >=
                PowerManager.THERMAL_STATUS_SEVERE
        ) {
            critical.add(
                "THERMAL MEASURED ${liveThermalStateLabel()} • SEVERE START-BLOCK THRESHOLD CROSSED"
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            liveThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE) {
            warnings.add(
                "THERMAL ${liveThermalStateLabel()} • CAUTION • RECORDING ALLOWED"
            )
        } else {
            ready.add(
                "THERMAL ${liveThermalStateLabel()}"
            )
        }

        val micReady =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) ==
                PackageManager.PERMISSION_GRANTED

        if (
            audioEnabled &&
            micReady
        ) {
            ready.add(
                "MIC OK"
            )
        } else if (
            audioEnabled
        ) {
            warnings.add(
                "MIC OFF"
            )
        } else {
            ready.add(
                "AUDIO DISABLED"
            )
        }

        if (
            liveNetworkReady()
        ) {
            ready.add(
                "NET READY"
            )
        } else {
            warnings.add(
                "NET OFFLINE"
            )
        }

        return LivePreflight(
            critical =
                critical.distinct(),
            warnings =
                warnings.distinct(),
            ready =
                ready.distinct()
        )
    }

    private fun runLivePreflightBeforeCountdown(): Boolean {
        if (
            livePreflightApprovedOnce
        ) {
            livePreflightApprovedOnce =
                false
            return true
        }

        val result =
            buildLivePreflight()

        if (
            result.critical.isEmpty() &&
            result.warnings.isEmpty()
        ) {
            livePreflightOverrideWarnings = emptyList()
            outputStatus.text =
                "PREFLIGHT GOOD • " +
                    result.ready.joinToString(
                        " • "
                    )
            return true
        }

        if (
            result.critical.isNotEmpty()
        ) {
            livePreflightOverrideWarnings = emptyList()
            val blockedDialog = AlertDialog.Builder(
                this
            )
                .setTitle(
                    "LIVE PREFLIGHT • BLOCKED"
                )
                .setMessage(
                    result.critical.joinToString(
                        "\n"
                    ) {
                        "• $it"
                    } +
                        "\n\n" +
                        result.warnings.joinToString(
                            "\n"
                        ) {
                            "• $it"
                        }
                )
                .setPositiveButton(
                    "OK",
                    null
                )
                .create()
            DevelopUgandaDialogStyler.show(blockedDialog, DevelopUgandaCameraPage.LIVE)
            return false
        }

        val warningDialog = AlertDialog.Builder(
            this
        )
            .setTitle(
                "LIVE RECORDING PREFLIGHT"
            )
            .setMessage(
                result.warnings.joinToString(
                    "\n"
                ) {
                    "• $it"
                } +
                    "\n\nReady: " +
                    result.ready.joinToString(
                        " • "
                    )
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "START ANYWAY"
            ) { _, _ ->
                livePreflightOverrideWarnings = result.warnings.map {
                    "LIVE WARNING • $it • OPERATOR START ANYWAY"
                }
                livePreflightApprovedOnce =
                    true
                beginLiveRecordSequence()
            }
            .create()
        DevelopUgandaDialogStyler.show(warningDialog, DevelopUgandaCameraPage.LIVE)

        return false
    }

    private fun liveRecoveryPrefs() =
        duSharedPreferences(
            "develop_uganda_live_recovery",
            Context.MODE_PRIVATE
        )

    private fun markLiveJournalStarted() {
        liveRecoveryPrefs()
            .edit()
            .putBoolean(
                "active",
                true
            )
            .putBoolean(
                "incomplete",
                false
            )
            .putString(
                "name",
                liveRecordingName
            )
            .putLong(
                "started_elapsed",
                recordStartMs
            )
            .putString(
                "preflight_warnings_proceeded",
                f12PreflightWarnings.joinToString(" • ")
            )
            .apply()
    }

    private fun markLiveJournalFinished(
        hadError: Boolean
    ) {
        liveRecoveryPrefs()
            .edit()
            .putBoolean(
                "active",
                false
            )
            .putBoolean(
                "incomplete",
                hadError
            )
            .apply()
    }

    private fun showLiveRecoveryNoticeIfNeeded() {
        val prefs =
            liveRecoveryPrefs()

        if (
            !prefs.getBoolean(
                "active",
                false
            ) &&
            !prefs.getBoolean(
                "incomplete",
                false
            )
        ) {
            return
        }

        val recoveryDialog = AlertDialog.Builder(
            this
        )
            .setTitle(
                "RECOVERED / INCOMPLETE LIVE CLIP"
            )
            .setMessage(
                "The previous LIVE recording did not reach a clean completion record.\n\nFILE • " +
                    (
                        prefs.getString(
                            "name",
                            "--"
                        ) ?: "--"
                        ) +
                    ".mp4\n\nInspect the Gallery file if present. This journal identifies an interrupted session; it does not claim a damaged video was repaired."
            )
            .setNegativeButton(
                "KEEP NOTICE",
                null
            )
            .setPositiveButton(
                "ACKNOWLEDGE"
            ) { _, _ ->
                prefs.edit()
                    .putBoolean(
                        "active",
                        false
                    )
                    .putBoolean(
                        "incomplete",
                        false
                    )
                    .apply()
            }
            .create()
        DevelopUgandaDialogStyler.show(recoveryDialog, DevelopUgandaCameraPage.LIVE)
    }

    private fun beginLiveRecordSequence() {
        if (
            countdownRunning
        ) {
            return
        }

        if (
            recording == null &&
            !runLivePreflightBeforeCountdown()
        ) {
            return
        }

        if (
            !countdownEnabled
        ) {
            toggleRecording()
            return
        }

        countdownRunning =
            true

        recordButton.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP
        )

        runCountdownStep(
            3
        )
    }

    private fun runCountdownStep(
        value: Int
    ) {
        if (
            value <= 0
        ) {
            countdownView.visibility =
                View.GONE

            countdownRunning =
                false

            toggleRecording()
            return
        }

        countdownView.text =
            value.toString()

        countdownView.visibility =
            View.VISIBLE

        countdownView.alpha =
            1f

        countdownView.animate()
            .alpha(
                0.25f
            )
            .setDuration(
                700L
            )
            .start()

        uiHandler.postDelayed(
            {
                runCountdownStep(
                    value -
                        1
                )
            },
            1000L
        )
    }

    private fun addLiveMarker() {
        if (
            recording == null ||
            recordStartMs == 0L
        ) {
            toast(
                "Start LIVE REC before adding a marker"
            )
            return
        }

        val elapsed =
            (
                SystemClock.elapsedRealtime() -
                    recordStartMs
                )
                .coerceAtLeast(
                    0L
                )

        liveMarkers.add(
            elapsed
        )

        runCatching {
            DevelopUgandaTakeMarks.add(
                this,
                liveRecordingName,
                DevelopUgandaCameraPage.LIVE,
                elapsed,
            )
        }.onFailure {
            toast("MARK SIDECAR WRITE FAILED")
            return
        }

        markButton.text =
            "MARK\\n${liveMarkers.size}"

        markButton.performHapticFeedback(
            HapticFeedbackConstants.KEYBOARD_TAP
        )

        toast(
            "MARK ${formatMarkerTime(elapsed)}"
        )
    }

    private fun formatMarkerTime(
        elapsedMs: Long
    ): String {
        val total =
            elapsedMs /
                1000L

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            total / 3600L,
            (total / 60L) % 60L,
            total % 60L
        )
    }

    private fun saveLiveMarkers(
        recordingName: String
    ) {
        if (
            liveMarkers.isEmpty()
        ) {
            return
        }

        val snapshot =
            liveMarkers.toList()

        Thread {
            try {
                val content =
                    buildString {
                        append(
                            "develop.uganda LIVE MARKERS\\n"
                        )
                        append(
                            "FILE $recordingName.mp4\\n"
                        )
                        append(
                            "PROFILE ${profiles[profileIndex]}\\n"
                        )
                        append(
                            "REPORTER $reporterName\\n"
                        )
                        append(
                            "STORY $storyId\\n\\n"
                        )

                        snapshot.forEachIndexed {
                                index,
                                value ->
                            append(
                                "MARK "
                            )
                            append(
                                index +
                                    1
                            )
                            append(
                                " "
                            )
                            append(
                                formatMarkerTime(
                                    value
                                )
                            )
                            append(
                                "\\n"
                            )
                        }
                    }

                val dir =
                    File(
                        getExternalFilesDir(
                            Environment.DIRECTORY_DOCUMENTS
                        ),
                        "develop.uganda/LiveMarkers"
                    )

                dir.mkdirs()

                FileOutputStream(
                    File(
                        dir,
                        "${recordingName}_MARKERS.txt"
                    )
                ).use {
                    it.write(
                        content.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
            } catch (_: Exception) {
            }
        }.start()
    }


    private fun f9AttachLiveCaptureTelemetry(previewBuilder: Preview.Builder) {
        try {
            androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                .setSessionCaptureCallback(f9LiveCaptureCallback)
        } catch (_: Exception) {
            // Omit unsupported hardware fields rather than guessing.
        }
    }

    private fun f9LiveLocationSnapshot(): android.location.Location? {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine != PackageManager.PERMISSION_GRANTED && coarse != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        return try {
            val manager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            manager.getProviders(true)
                .asSequence()
                .mapNotNull { provider ->
                    runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
                }
                .maxByOrNull { it.time }
        } catch (_: Exception) {
            null
        }
    }

    private fun f9CreateLiveTake(takeId: String): DevelopUgandaFivemods9DualOutputExporter.TakeMetadata {
        val location = f9LiveLocationSnapshot()
        return DevelopUgandaFivemods9DualOutputExporter.TakeMetadata(
            takeId = takeId,
            mode = "LIVE",
            capturedAtMs = System.currentTimeMillis(),
            operator = reporterName,
            locationName = null,
            latitude = location?.latitude,
            longitude = location?.longitude,
            altitudeM = location?.takeIf { it.hasAltitude() }?.altitude,
            weather = null,
            shutterNs = f9LiveActualShutterNs,
            iso = f9LiveActualIso,
            interviewDisclaimer = false
        )
    }

    private fun toggleRecording() {
        if (
            recording != null
        ) {
            recording?.stop()
            return
        }

        val fieldPreflight = DevelopUgandaFivemods12Preflight.snapshot(
            this,
            DevelopUgandaCameraPage.LIVE,
        )
        if (!fieldPreflight.mayRecord) {
            val fieldDialog = AlertDialog.Builder(this)
                .setTitle("FIVEMODS 12 PREFLIGHT • UNAVAILABLE")
                .setMessage(fieldPreflight.items.joinToString("\n") { "${it.label} • ${it.stateLabel} • ${it.reason}" })
                .setNegativeButton("CLOSE", null)
                .setPositiveButton("OPEN FIELD CONSOLE") { _, _ ->
                    startActivity(Intent(this, DevelopUgandaFivemods12FieldConsoleActivity::class.java).putExtra(DevelopUgandaFivemods12FieldConsoleActivity.EXTRA_MODE, DevelopUgandaCameraPage.LIVE.name))
                }
                .create()
            DevelopUgandaDialogStyler.show(fieldDialog, DevelopUgandaCameraPage.LIVE)
            return
        }

        val safetyPreflight = DevelopUgandaV276RecordingSafety.recordingPreflight(
            this,
            DevelopUgandaCameraPage.LIVE,
        )
        if (!safetyPreflight.mayStart) {
            f12SafetyOverrideApprovedOnce = false
            val refusedDialog = AlertDialog.Builder(this)
                .setTitle("RECORDING PREFLIGHT • REFUSED")
                .setMessage(safetyPreflight.blocked.joinToString("\n") { "• $it" })
                .setPositiveButton("ACKNOWLEDGE", null)
                .create()
            DevelopUgandaDialogStyler.show(refusedDialog, DevelopUgandaCameraPage.LIVE)
            return
        }
        if (safetyPreflight.needsOperatorOverride && !f12SafetyOverrideApprovedOnce) {
            val overrideDialog = AlertDialog.Builder(this)
                .setTitle("RECORDING PREFLIGHT • WARNING")
                .setMessage(
                    safetyPreflight.overridable.joinToString("\n") { "• $it" } +
                        "\n\nThis is measured but not a critical stop. START ANYWAY records this override in the take sidecar.",
                )
                .setNegativeButton("CANCEL", null)
                .setPositiveButton("START ANYWAY") { _, _ ->
                    f12SafetyOverrideApprovedOnce = true
                    toggleRecording()
                }
                .create()
            DevelopUgandaDialogStyler.show(overrideDialog, DevelopUgandaCameraPage.LIVE)
            return
        }
        f12PreflightWarnings = buildList {
            addAll(livePreflightOverrideWarnings)
            if (f12SafetyOverrideApprovedOnce) {
                safetyPreflight.overridable.forEach {
                    add("$it • OPERATOR START ANYWAY")
                }
            }
            fieldPreflight.items
                .filter {
                    it.state == DevelopUgandaFivemods12Availability.LIMITED ||
                        it.state == DevelopUgandaFivemods12Availability.UNKNOWN
                }
                .forEach { add("${it.label} • ${it.stateLabel} • ${it.reason}") }
            addAll(safetyPreflight.warnings)
        }.distinct()
        f12SafetyOverrideApprovedOnce = false
        if (f12PreflightWarnings.isNotEmpty()) {
            DevelopUgandaV276RecordingSafety.addEvent(
                this,
                "PREFLIGHT WARNING PROCEEDED • ${f12PreflightWarnings.joinToString(" • ")}",
            )
        }

        val capture =
            videoCapture
                ?: run {
                    toast(
                        "Camera is not ready"
                    )
                    return
                }

        val stamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(
                Date()
            )

        val f9LiveTakeId = DevelopUgandaFivemods12Identity.prefixedStem(
            DevelopUgandaCameraPage.LIVE,
            "TAKE_${stamp}_${SystemClock.elapsedRealtime()}",
        )
        liveRecordingName = "${f9LiveTakeId}_CLEAN"

        liveMarkers.clear()

        if (
            ::markButton.isInitialized
        ) {
            markButton.text =
                "MARK\n0"
        }

        val values =
            ContentValues().apply {
                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    "$liveRecordingName.mp4"
                )

                put(
                    MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4"
                )

                if (
                    android.os.Build.VERSION.SDK_INT >=
                    29
                ) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/develop.uganda/Live"
                    )
                }
            }

        val output =
            MediaStoreOutputOptions.Builder(
                contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            )
                .setContentValues(
                    values
                )
                .build()

        var pending =
            capture.output
                .prepareRecording(
                    this,
                    output
                )

        if (
            audioEnabled &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            pending =
                pending.withAudioEnabled()
        }

        var f12LastAudioRouteCheckMs = 0L
        var f12AudioRouteStopped = false
        recording =
            pending.start(
                ContextCompat.getMainExecutor(
                    this
                )
            ) { event ->
                when (
                    event
                ) {
                    is VideoRecordEvent.Start -> {
                        f12ProtectionStopRequested = false
                        recordStartMs =
                            SystemClock.elapsedRealtime()

                        liveRecordStartUtc =
                            Instant.now().toString()
                        f9LiveTake = f9CreateLiveTake(f9LiveTakeId)

                        markLiveJournalStarted()
                        DevelopUgandaV272FieldSoundContinuity.armAudioLock(this@DevelopUgandaLiveActivity)

                        DevelopUgandaV276RecordingSafety.onRecordingStarted(
                            this@DevelopUgandaLiveActivity,
                            liveRecordingName,
                            "CLEAN + BRAND",
                            storyId,
                            "LIVE",
                            1,
                            1,
                        )

                        recordButton.setRecordingState(
                            true
                        )

                        liveSubTitle.text =
                            "LIVE STUDIO • ${profiles[profileIndex]} • RECORDING"

                        recLamp.setTextColor(
                            DevelopUgandaFivemods8Theme.record
                        )

                        toast(
                            "LIVE REC started"
                        )
                    }

                    is VideoRecordEvent.Status -> {
                        DevelopUgandaV276RecordingSafety.onRecordingStatus(
                            this@DevelopUgandaLiveActivity,
                            event.recordingStats.recordedDurationNanos,
                            event.recordingStats.numBytesRecorded,
                            DevelopUgandaCameraPage.LIVE,
                        )
                        liveAudioAmplitude =
                            event.recordingStats
                                .audioStats
                                .audioAmplitude
                                .coerceIn(
                                    0.0,
                                    1.0
                                )

                        liveAudioPeakAmplitude =
                            maxOf(
                                liveAudioPeakAmplitude *
                                    0.985,
                                liveAudioAmplitude
                            )

                        val protection = DevelopUgandaFivemods12RecordingProtection.state(
                            this@DevelopUgandaLiveActivity,
                            DevelopUgandaCameraPage.LIVE,
                        )
                        if (!f12ProtectionStopRequested && protection.stopReason != null) {
                            f12ProtectionStopRequested = true
                            liveSubTitle.text = "${protection.stopReason} • FINALISING CLEAN"
                            DevelopUgandaV276RecordingSafety.addEvent(
                                this@DevelopUgandaLiveActivity,
                                "PROTECTION STOP • ${protection.stopReason} • CLEAN FINALISE REQUESTED",
                            )
                            runCatching { recording?.stop() }
                        }

                        val routeNow = SystemClock.elapsedRealtime()
                        if (!f12AudioRouteStopped && routeNow - f12LastAudioRouteCheckMs >= 1_500L) {
                            f12LastAudioRouteCheckMs = routeNow
                            if (DevelopUgandaV272FieldSoundContinuity.audioRouteChanged(this@DevelopUgandaLiveActivity)) {
                                f12AudioRouteStopped = true
                                f12ProtectionStopRequested = true
                                liveSubTitle.text = "MIC DISCONNECTED / ROUTE CHANGED • FINALISING CLEAN"
                                DevelopUgandaV276RecordingSafety.addEvent(
                                    this@DevelopUgandaLiveActivity,
                                    "INTERRUPTION • LIVE MICROPHONE ROUTE CHANGED • CLEAN FINALISE REQUESTED",
                                )
                                runCatching { recording?.stop() }
                            }
                        }

                        if (
                            audioEnabled &&
                            liveAudioAmplitude >
                                0.0
                        ) {
                            micLamp.setTextColor(
                                DevelopUgandaFivemods8Theme.accent
                            )
                        }
                    }

                    is VideoRecordEvent.Finalize -> {
                        recording =
                            null

                        recordButton.setRecordingState(
                            false
                        )

                        liveSubTitle.text =
                            "LIVE STUDIO • ${profiles[profileIndex]} • QC CHECK"

                        recLamp.setTextColor(
                            DevelopUgandaFivemods8Theme.contentDim
                        )

                        recordStartMs =
                            0L

                        liveAudioAmplitude =
                            0.0

                        liveAudioPeakAmplitude =
                            0.0

                        markLiveJournalFinished(
                            event.hasError()
                        )

                        val f12SafetyResult = DevelopUgandaV276RecordingSafety.onRecordingFinalized(
                            this@DevelopUgandaLiveActivity,
                            event.outputResults.outputUri.toString(),
                            event.recordingStats.recordedDurationNanos / 1_000_000L,
                            event.recordingStats.numBytesRecorded,
                            event.hasError(),
                            f12PreflightWarnings,
                        )
                        f12PreflightWarnings = emptyList()
                        livePreflightOverrideWarnings = emptyList()

                        if (!event.hasError() && event.outputResults.outputUri != Uri.EMPTY) {
                            DevelopUgandaFivemods12Identity.writeCleanSidecar(
                                this@DevelopUgandaLiveActivity,
                                DevelopUgandaCameraPage.LIVE,
                                "$liveRecordingName.mp4",
                                f12SafetyResult.ok,
                                event.recordingStats.recordedDurationNanos / 1_000_000L,
                                event.recordingStats.numBytesRecorded,
                            )
                        }

                        if (
                            !event.hasError()
                        ) {
                            val f9Take = f9LiveTake?.let { initial ->
                                initial.copy(
                                    shutterNs = f9LiveActualShutterNs ?: initial.shutterNs,
                                    iso = f9LiveActualIso ?: initial.iso
                                )
                            }
                            f9LiveTake = null
                            if (f9Take == null) {
                                liveSubTitle.text = "LIVE STUDIO • CLEAN SAVED • BRAND MISSING"
                                toast("LIVE: BRAND MISSING • CLEAN is in Gallery")
                            } else {
                                liveSubTitle.text = "LIVE STUDIO • CLEAN SAVED • BRAND SAVING"
                                outputStatus.text = "DUAL • CLEAN SAVED • BRAND SAVING"
                                f9LiveDualOutputExporter.enqueue(event.outputResults.outputUri, f9Take)
                            }
                            DevelopUgandaStoryPackager.createVideoPackage(
                                this@DevelopUgandaLiveActivity,
                                event.outputResults.outputUri,
                                DevelopUgandaStoryPackager.StoryMetadata(
                                    packageId = liveRecordingName.ifBlank {
                                        "LIVE_${System.currentTimeMillis()}"
                                    },
                                    camera = "LIVE STUDIO",
                                    reporter = reporterName,
                                    storyId = storyId,
                                    title = headline,
                                    place = null,
                                    latitude = null,
                                    longitude = null,
                                    gpsAccuracyM = null,
                                    startedUtc = liveRecordStartUtc,
                                    finishedUtc = Instant.now().toString(),
                                    scene = profiles[profileIndex],
                                    look = liveEffectLabels[liveEffectIndex],
                                    quality = qualityLabel,
                                    autoView = liveAutoViewSummary,
                                    warnings = buildLivePreflight().warnings,
                                    sourceKind = "LIVE",
                                    autoTranscribe =
                                        profiles[profileIndex] == "INTERVIEW" ||
                                            livePresetLabels[livePresetIndex] == "INTERVIEW",
                                    expectSocialMaster = false
                                )
                            )
                        }

                        if (
                            !event.hasError()
                        ) {
                            scheduleV233LiveColorMaster(
                                event.outputResults.outputUri,
                                liveRecordingName.ifBlank {
                                    "LIVE_${System.currentTimeMillis()}"
                                }
                            )

                            DevelopUgandaClipQc.inspect(
                                this@DevelopUgandaLiveActivity,
                                event.outputResults.outputUri,
                                liveRecordingName
                            ) {
                                    result ->
                                liveSubTitle.text =
                                    if (
                                        result.playableFrame &&
                                        result.hasVideo &&
                                        result.sourceReadable
                                    ) {
                                        "LIVE STUDIO • ${profiles[profileIndex]} • CONTINUOUS AF/AE/AWB • READY"
                                    } else {
                                        "LIVE STUDIO • ${profiles[profileIndex]} • QC CHECK"
                                    }

                                DevelopUgandaInstantReviewDialog.show(
                                    this@DevelopUgandaLiveActivity,
                                    result,
                                    liveRecordingName,
                                    false,
                                    null
                                )
                            }
                        }

                        if (
                            event.hasError()
                        ) {
                            f9LiveTake = null
                            liveSubTitle.text = "LIVE STUDIO • CLEAN MISSING"
                            toast(
                                "LIVE REC failed • CLEAN MISSING"
                            )
                        } else {
                            saveLiveMarkers(
                                liveRecordingName
                            )

                            toast(
                                "LIVE recording saved"
                            )
                        }
                    }
                }
            }
    }

    private fun showLowerThirdEditor() {
        val box =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(18),
                    dp(8),
                    dp(18),
                    dp(4)
                )
            }

        val headlineField =
            EditText(this).apply {
                hint =
                    "Live headline"

                setText(
                    headline
                )
            }

        box.addView(
            headlineField
        )

        AlertDialog.Builder(this)
            .setTitle(
                "LIVE LOWER THIRD"
            )
            .setView(
                box
            )
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->
                headline =
                    headlineField.text
                        .toString()
                        .trim()
                        .ifBlank {
                            "LIVE REPORT"
                        }

                duSharedPreferences(
                    "develop_uganda_newsroom",
                    Context.MODE_PRIVATE
                )
                    .edit()
                    .putString(
                        "headline",
                        headline
                    )
                    .apply()
            }
            .setNegativeButton(
                "CANCEL",
                null
            )
            .show()
    }

    private fun showOutputSetup() {
        DevelopUgandaSecureStreamConfig.migrateLegacyPlaintextKey(this)
        AlertDialog.Builder(this)
            .setTitle(
                "RTMPS LIVE OUTPUT"
            )
            .setMessage(
                "Open the dedicated LIVE camera to configure an encrypted stream key, run measured pre-flight checks and start RTMPS with an always-on local CLEAN backup."
            )
            .setPositiveButton(
                "OPEN RTMPS CAMERA"
            ) { _, _ ->
                startActivity(
                    Intent(
                        this,
                        DevelopUgandaRtmpsLiveActivity::class.java
                    )
                )
            }
            .setNegativeButton(
                "CANCEL",
                null
            )
            .show()
    }

    private fun showSignalInfo() {
        AlertDialog.Builder(this)
            .setTitle(
                "LIVE SIGNALS"
            )
            .setMessage(
                "GREEN = ready/healthy. AMBER = limited or permission dependent. " +
                    "RED = unavailable or actively recording for REC. " +
                    "NET reports internet capability, GPS reports permission readiness, MIC reports audio readiness, CAM reports the bound camera, and BAT changes by remaining charge."
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }

    private fun livePreviewToneColor(): Int {
        return when (
            liveEffectLabels[
                liveEffectIndex
            ]
        ) {
            "WARM" ->
                0x10FF8A3D.toInt()

            "COOL" ->
                0x10007AFF.toInt()

            "TEAL" ->
                0x1000A7A0.toInt()

            "GOLD" ->
                0x10D6A83A.toInt()

            "SOFT" ->
                0x0CF0D8D0.toInt()

            "NIGHT" ->
                0x12173363.toInt()

            "NATURAL" ->
                0x0600A070.toInt()

            else ->
                Color.TRANSPARENT
        }
    }

    private fun liveModeAccentColor(): Int =
        DevelopUgandaFivemods8Theme.accent

    private fun updateLiveModePreviewTuning() {
        if (
            ::livePreviewToneView.isInitialized
        ) {
            livePreviewToneView.setBackgroundColor(
                livePreviewToneColor()
            )
        }

        if (
            ::liveSubTitle.isInitialized
        ) {
            liveSubTitle.setTextColor(
                liveModeAccentColor()
            )

            liveSubTitle.text =
                "LIVE STUDIO • ${profiles[profileIndex]} • ${liveQualityProfiles[liveQualityIndex]} • LOOK ${liveEffectLabels[liveEffectIndex]} • MANUAL LIVE • V217"
        }
    }

    private fun liveThermalStateLabel(): String {
        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.Q
        ) {
            return "UNAVAILABLE"
        }

        return when (
            liveThermalStatus
        ) {
            PowerManager.THERMAL_STATUS_NONE ->
                "NORMAL"

            PowerManager.THERMAL_STATUS_LIGHT ->
                "LIGHT"

            PowerManager.THERMAL_STATUS_MODERATE ->
                "MODERATE"

            PowerManager.THERMAL_STATUS_SEVERE ->
                "SEVERE"

            PowerManager.THERMAL_STATUS_CRITICAL ->
                "CRITICAL"

            PowerManager.THERMAL_STATUS_EMERGENCY ->
                "EMERGENCY"

            PowerManager.THERMAL_STATUS_SHUTDOWN ->
                "SHUTDOWN"

            else ->
                "UNKNOWN"
        }
    }

    private fun applyLiveThermalSafeProfileIfNeeded() {
        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.Q ||
            liveThermalStatus <
                PowerManager.THERMAL_STATUS_SEVERE ||
            recording !=
                null
        ) {
            return
        }

        val current =
            liveQualityProfiles[
                liveQualityIndex
            ]

        val highDemand =
            current in
                setOf(
                    "UHD 30",
                    "UHD 60",
                    "HDR UHD",
                    "SOCIAL HDR",
                    "ACTION 60"
                )

        if (!highDemand) {
            return
        }

        val safeIndex =
            liveQualityProfiles.indexOf(
                "SOCIAL 30"
            )

        if (
            safeIndex >=
                0 &&
            safeIndex !=
                liveQualityIndex
        ) {
            liveQualityIndex =
                safeIndex

            syncLiveQualityState()

            if (
                ::qualityButton.isInitialized
            ) {
                qualityButton.text =
                    "QUALITY ▾\n${liveQualityProfiles[liveQualityIndex]}"
            }

            toast(
                "THERMAL ${liveThermalStateLabel()} • LIVE switched to SOCIAL 30"
            )
        }
    }

    private fun liveVerifiedStateText(): String {
        return buildString {
            append("V217 VERIFIED")
            append(" • ")
            append(
                liveQualityProfiles[
                    liveQualityIndex
                ]
            )
            append(" • LOOK ")
            append(
                liveEffectLabels[
                    liveEffectIndex
                ]
            )
            append(" • ")
            append(liveActiveFpsLabel)
            append(" • ")
            append(liveActiveStabilizationLabel)
            append(" • ")
            append(liveActiveDynamicRangeLabel)
            append(" • AUDIO ")
            append(liveAudioGuardLabel())
            append(" • THERMAL ")
            append(
                liveThermalStateLabel()
            )
        }
    }

    private fun liveAudioGuardLabel(): String {
        if (!audioEnabled) {
            return "OFF"
        }

        if (
            recording ==
                null
        ) {
            return "READY"
        }

        val level =
            liveAudioAmplitude.coerceIn(
                0.0,
                1.0
            )

        return when {
            level <
                0.015 ->
                    "LOW"

            level <
                0.70 ->
                    "GOOD"

            level <
                0.90 ->
                    "HOT"

            else ->
                "CLIP RISK"
        }
    }

    private fun updateSignals() {
        refreshV233LiveColorMonitor()
        updateLiveModePreviewTuning()

        netLamp.setTextColor(
            if (
                isNetworkConnected()
            ) {
                DevelopUgandaFivemods8Theme.accent
            } else {
                DevelopUgandaFivemods8Theme.warning
            }
        )

        gpsLamp.setTextColor(
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                DevelopUgandaFivemods8Theme.accent
            } else {
                DevelopUgandaFivemods8Theme.warning
            }
        )

        micLamp.setTextColor(
            if (
                audioEnabled &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.RECORD_AUDIO
                ) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                DevelopUgandaFivemods8Theme.accent
            } else {
                DevelopUgandaFivemods8Theme.warning
            }
        )

        camLamp.setTextColor(
            if (
                camera != null
            ) {
                DevelopUgandaFivemods8Theme.accent
            } else {
                DevelopUgandaFivemods8Theme.warning
            }
        )

        recLamp.setTextColor(
            if (
                recording !=
                null
            ) {
                DevelopUgandaFivemods8Theme.record
            } else {
                DevelopUgandaFivemods8Theme.contentDim
            }
        )

        val battery =
            getSystemService(
                Context.BATTERY_SERVICE
            ) as BatteryManager

        val percent =
            battery.getIntProperty(
                BatteryManager.BATTERY_PROPERTY_CAPACITY
            )

        batteryLamp.text =
            "● BAT $percent%"

        batteryLamp.setTextColor(
            when {
                percent >= 30 ->
                    DevelopUgandaFivemods8Theme.contentDim

                percent >= 15 ->
                    DevelopUgandaFivemods8Theme.warning

                else ->
                    DevelopUgandaFivemods8Theme.record
            }
        )
        val f12Status = DevelopUgandaBroadcastStatus(
                // LIVE predates additive profile data; report only the actual
                // selected quality and rate labels already maintained here.
                format = "$qualityLabel • $liveActiveFpsLabel",
                codec = null,
                timecode = liveTimecode(),
                battery = percent.takeIf { it in 0..100 },
                freeStorageGb = liveFreeStorageGb(),
                audio = liveAudioGuardLabel(),
                recording = recording != null,
            )
        broadcastCameraChrome?.update(f12Status)
        f12CameraShell?.updateBroadcastStatus(f12Status)
    }

    private fun updateTimer() {
        timerView.text =
            liveTimecode()

        if (
            ::livePreviewMeta.isInitialized
        ) {
            livePreviewMeta.text =
                "${profiles[profileIndex]} • REPORTER $reporterName • STORY $storyId • $headline"

            val audioPercent =
                (
                    liveAudioAmplitude *
                        100.0
                    ).roundToInt()

            val battery =
                (
                    getSystemService(
                        Context.BATTERY_SERVICE
                    ) as BatteryManager
                    )
                    .getIntProperty(
                        BatteryManager.BATTERY_PROPERTY_CAPACITY
                    )

            val netReady =
                isNetworkConnected()

            val health =
                when {
                    battery in 0..9 ->
                        "CRITICAL"

                    !netReady ->
                        "NET OFF"

                    audioEnabled &&
                        recording != null &&
                        audioPercent <
                        1 ->
                        "CHECK MIC"

                    else ->
                        "GOOD"
                }

            livePreviewTech.text =
                "${liveVerifiedStateText()} • TC ${liveTimecode()} • AUDIO ${audioPercent}% • NET ${
                    if (netReady) {
                        "READY"
                    } else {
                        "OFF"
                    }
                } • HEALTH $health • ${liveEstimatedRecordingTimeText()}"
        }
    }

    private fun updateBlink() {
        if (
            recording ==
            null
        ) {
            liveBadge.alpha =
                0.85f

            return
        }

        liveBlinkOn =
            !liveBlinkOn

        liveBadge.alpha =
            if (
                liveBlinkOn
            ) {
                1f
            } else {
                0.25f
            }
    }

    private fun isNetworkConnected(): Boolean {
        val cm =
            getSystemService(
                Context.CONNECTIVITY_SERVICE
            ) as ConnectivityManager

        val network =
            cm.activeNetwork
                ?: return false

        val caps =
            cm.getNetworkCapabilities(
                network
            )
                ?: return false

        return caps.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_INTERNET
        )
    }

    private fun liveSettingButton(
        value: String,
        accent: Int,
        action: () -> Unit
    ): Button {
        return LiveOutlineButton(
            this,
            accent
        ).apply {
            text =
                value

            textSize =
                6.4f

            isAllCaps =
                false

            setTextColor(
                DevelopUgandaFivemods8Theme.content
            )

            gravity =
                Gravity.CENTER

            includeFontPadding =
                false

            background =
                ColorDrawable(
                    DevelopUgandaFivemods8Theme.transparent
                )

            stateListAnimator =
                null

            setOnClickListener {
                if (
                    liveControlsLocked &&
                    this !== liveLockButton &&
                    this !== markButton
                ) {
                    toast(
                        "LIVE controls locked"
                    )
                } else {
                    action.invoke()
                }
            }
        }
    }

    private fun livePillFillColor(
        accent: Int,
        selected: Boolean = false
    ): Int {
        return if (selected) {
            DevelopUgandaFivemods8Theme.surfaceRaised
        } else {
            DevelopUgandaFivemods8Theme.surface
        }
    }

    private fun liveSolidPillBackground(
        accent: Int,
        selected: Boolean = false
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape =
                GradientDrawable.RECTANGLE

            cornerRadius =
                dp(20).toFloat()

            setColor(
                livePillFillColor(
                    accent,
                    selected
                )
            )

            setStroke(
                dp(
                    if (selected) {
                        2
                    } else {
                        1
                    }
                ),
                accent
            )
        }
    }

    private fun liveOptionAccent(
        index: Int
    ): Int = DevelopUgandaFivemods8Theme.accent

    private fun v229LiveColorScope(): String =
        "LIVE_STUDIO"

    private fun v229LiveColorHint(): String {
        return buildString {
            append("LIVE • ")
            append(profiles[profileIndex])
            append(" • ")
            append(liveQualityProfiles[liveQualityIndex])
            append(" • ")
            append(liveEffectLabels[liveEffectIndex])
            append(" • ")
            append(headline)
        }
    }

    private fun v229LiveColorResolved(): DevelopUgandaColorEngine.ResolvedSelection =
        DevelopUgandaColorEngine.resolve(
            this,
            v229LiveColorScope(),
            v229LiveColorHint()
        )

    private fun v229LiveColorDeckLabel(): String {
        val value = v229LiveColorResolved()
        return when {
            !value.enabled ->
                "ORIGINAL"

            value.autoResolved ->
                "AUTO " +
                    value.label
                        .removePrefix("DU ")
                        .take(10)

            else ->
                value.label
                    .removePrefix("DU ")
                    .take(12)
        }
    }

    private fun refreshV233LiveColorMonitor() {
        if (
            !::previewView.isInitialized ||
            !::liveColorButton.isInitialized
        ) {
            return
        }

        val value =
            v229LiveColorResolved()

        v229LiveColorOverlayLabel =
            if (
                value.enabled
            ) {
                value.label
            } else {
                "ORIGINAL"
            }

        liveColorButton.text =
            "COLOR ▾\n${v229LiveColorDeckLabel()}"

        liveColorButton.isSelected =
            value.enabled

        val key =
            "${value.requestedId}:${value.label}:${value.strength}:${DevelopUgandaColorEngine.monitorEnabled(this)}"

        if (
            key !=
                lastV233LiveColorMonitorKey
        ) {
            lastV233LiveColorMonitorKey =
                key

            DevelopUgandaColorEngine.applyPreviewMonitor(
                previewView,
                value,
                v229LiveColorScope()
            )
        }
    }

    private fun showV233LiveColorDropdown(
        anchor: View
    ) {
        if (
            recording !=
                null
        ) {
            toast(
                "Choose the V233 color profile before recording"
            )
            return
        }

        val options =
            DevelopUgandaColorEngine.menuLabels()
                .toMutableList()

        options.add(
            "COLOR STUDIO • STRENGTH / MONITOR"
        )

        showLivePillDropdown(
            anchor,
            "V233 PROFESSIONAL COLOR",
            options.toTypedArray(),
            DevelopUgandaColorEngine.selectedMenuIndex(
                this,
                v229LiveColorScope()
            )
        ) {
                picked ->
            if (
                picked >=
                    options.lastIndex
            ) {
                openV233LiveColorStudio()
                return@showLivePillDropdown
            }

            DevelopUgandaColorEngine.setSelectedMenuIndex(
                this,
                v229LiveColorScope(),
                picked
            )

            lastV233LiveColorMonitorKey =
                ""

            refreshV233LiveColorMonitor()

            toast(
                "V233 COLOR • ${v229LiveColorResolved().statusLabel()}"
            )
        }
    }

    private fun openV233LiveColorStudio() {
        startActivity(
            android.content.Intent(
                this,
                DevelopUgandaColorStudioActivity::class.java
            ).apply {
                putExtra(
                    DevelopUgandaColorStudioActivity.EXTRA_SCOPE,
                    v229LiveColorScope()
                )
                putExtra(
                    DevelopUgandaColorStudioActivity.EXTRA_HINT,
                    v229LiveColorHint()
                )
            }
        )
    }

    private fun scheduleV233LiveColorMaster(
        sourceUri: android.net.Uri,
        packageId: String
    ) {
        val selection =
            v229LiveColorResolved()

        if (
            !selection.enabled
        ) {
            DevelopUgandaStoryPackager.markColorMasterSkipped(
                applicationContext,
                packageId,
                "ORIGINAL selected • no V233 color master requested"
            )
            return
        }

        val scopeSnapshot =
            v229LiveColorScope()
        val hintSnapshot =
            v229LiveColorHint()

        fun waitForPackage(
            attempt: Int
        ) {
            val entry =
                DevelopUgandaStoryPackager.listRegistry(
                    applicationContext
                )
                    .firstOrNull {
                        it.packageId ==
                            packageId
                    }

            val busy =
                entry ==
                    null ||
                    entry.state.contains(
                        "BUILDING",
                        ignoreCase = true
                    )

            if (
                busy &&
                attempt <
                    180
            ) {
                uiHandler.postDelayed(
                    {
                        waitForPackage(
                            attempt +
                                1
                        )
                    },
                    1000L
                )
                return
            }

            DevelopUgandaStoryPackager.markColorMasterBuilding(
                applicationContext,
                packageId,
                selection.label,
                selection.strength
            )

            DevelopUgandaColorEngine.exportVideoMaster(
                applicationContext,
                sourceUri,
                packageId,
                scopeSnapshot,
                hintSnapshot
            ) {
                    outcome ->
                if (
                    outcome.success &&
                    outcome.uri !=
                        null
                ) {
                    DevelopUgandaStoryPackager.attachColorMaster(
                        applicationContext,
                        packageId,
                        outcome.uri,
                        outcome.profileLabel,
                        outcome.strength,
                        outcome.width,
                        outcome.height,
                        outcome.durationMs,
                        outcome.bitrate
                    )

                    runOnUiThread {
                        toast(
                            "V233 LIVE COLOR MASTER READY • ${outcome.profileLabel}"
                        )
                    }
                } else {
                    DevelopUgandaStoryPackager.markColorMasterFailed(
                        applicationContext,
                        packageId,
                        outcome.message
                    )
                }
            }
        }

        uiHandler.postDelayed(
            {
                waitForPackage(
                    0
                )
            },
            1000L
        )
    }

    private fun showLivePillDropdown(
        anchor: View,
        title: String,
        options: Array<String>,
        selectedIndex: Int,
        onPick: (Int) -> Unit
    ) {
        val panel =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(7),
                    dp(7),
                    dp(7),
                    dp(7)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(18).toFloat()

                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(242)
                        )

                        setStroke(
                            dp(1),
                            DevelopUgandaFivemods8Theme.recordScrim(96)
                        )
                    }
            }

        panel.addView(
            label(
                title,
                9f,
                DevelopUgandaFivemods8Theme.content,
                true
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(28)
            )
        )

        val popup =
            PopupWindow(
                panel,
                dp(190),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable =
                    true

                elevation = 0f

                setBackgroundDrawable(
                    ColorDrawable(
                        DevelopUgandaFivemods8Theme.transparent
                    )
                )
            }

        options.forEachIndexed {
                index,
                option ->

            val accent =
                liveOptionAccent(
                    index
                )

            val pill =
                LiveOutlineButton(
                    this,
                    accent
                ).apply {
                    text =
                        if (
                            index ==
                            selectedIndex
                        ) {
                            "✓  $option"
                        } else {
                            option
                        }

                    textSize =
                        8f

                    isAllCaps =
                        false

                    setTextColor(
                        DevelopUgandaFivemods8Theme.content
                    )

                    gravity =
                        Gravity.CENTER

                    isSelected =
                        index ==
                            selectedIndex

                    background =
                        ColorDrawable(
                            DevelopUgandaFivemods8Theme.transparent
                        )

                    setOnClickListener {
                        onPick(
                            index
                        )
                        popup.dismiss()
                    }
                }

            panel.addView(
                pill,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(38)
                ).apply {
                    topMargin =
                        dp(4)
                }
            )
        }

        popup.showAsDropDown(
            anchor,
            0,
            dp(4)
        )
    }

    private fun showLiveProfileDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Stop LIVE REC before changing profile"
            )
            return
        }

        showLivePillDropdown(
            anchor,
            "LIVE PROFILE",
            profiles,
            profileIndex
        ) { picked ->
            profileIndex =
                picked

            markLivePresetCustom()

            profileButton.text =
                "PROFILE ▾\n${profiles[profileIndex]}"

            liveSubTitle.text =
                "LIVE STUDIO • ${profiles[profileIndex]} • CONTINUOUS AF/AE/AWB • READY"

            saveLiveCameraPreferences()
        }
    }

    private fun showLiveQualityDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Stop LIVE REC before changing quality"
            )
            return
        }

        showLivePillDropdown(
            anchor,
            "CREATOR QUALITY",
            liveQualityProfiles,
            liveQualityIndex
        ) { picked ->
            liveQualityIndex =
                picked

            syncLiveQualityState()

            qualityButton.text =
                "QUALITY ▾\n${liveQualityProfiles[liveQualityIndex]}"

            saveLiveCameraPreferences()
            bindCamera()
        }
    }

    private fun showLiveAudioDropdown(
        anchor: View
    ) {
        showLivePillDropdown(
            anchor,
            "AUDIO",
            arrayOf(
                "ON",
                "OFF"
            ),
            if (audioEnabled) 0 else 1
        ) { picked ->
            audioEnabled =
                picked ==
                    0

            markLivePresetCustom()

            audioButton.text =
                "AUDIO ▾\n" +
                    if (audioEnabled) {
                        "ON"
                    } else {
                        "OFF"
                    }

            saveLiveCameraPreferences()
        }
    }

    private fun showLiveGraphicsDropdown(
        anchor: View
    ) {
        showLivePillDropdown(
            anchor,
            "LIVE GRAPHICS",
            arrayOf(
                "ON",
                "OFF"
            ),
            if (graphicsEnabled) 0 else 1
        ) { picked ->
            graphicsEnabled =
                picked ==
                    0

            markLivePresetCustom()

            graphicsButton.text =
                "GRAPHICS ▾\n" +
                    if (graphicsEnabled) {
                        "ON"
                    } else {
                        "OFF"
                    }

            saveLiveCameraPreferences()
        }
    }

    private fun showLiveLensDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Stop LIVE REC before changing lens"
            )
            return
        }

        showLivePillDropdown(
            anchor,
            "LENS",
            arrayOf(
                "BACK",
                "FRONT"
            ),
            if (useFront) 1 else 0
        ) { picked ->
            useFront =
                picked ==
                    1

            lensButton.text =
                "LENS ▾\n" +
                    if (useFront) {
                        "FRONT"
                    } else {
                        "BACK"
                    }

            bindCamera()
        }
    }

    private fun showLiveLightDropdown(
        anchor: View
    ) {
        val isOn =
            camera
                ?.cameraInfo
                ?.torchState
                ?.value ==
                androidx.camera.core.TorchState.ON

        showLivePillDropdown(
            anchor,
            "LIGHT",
            arrayOf(
                "OFF",
                "ON"
            ),
            if (isOn) 1 else 0
        ) { picked ->
            val wantOn =
                picked ==
                    1

            camera
                ?.cameraControl
                ?.enableTorch(
                    wantOn
                )

            lightButton.text =
                "LIGHT ▾\n" +
                    if (wantOn) {
                        "ON"
                    } else {
                        "OFF"
                    }
        }
    }

    private fun showLiveViewDropdown(
        anchor: View
    ) {
        showLivePillDropdown(
            anchor,
            "VIEW",
            arrayOf(
                "FULL SCREEN",
                "HALF SCREEN"
            ),
            if (halfPreviewMode) 1 else 0
        ) { picked ->
            val wantHalf =
                picked ==
                    1

            if (
                wantHalf !=
                halfPreviewMode
            ) {
                togglePreviewMode()
            }
        }
    }

    private fun showLiveCountdownDropdown(
        anchor: View
    ) {
        showLivePillDropdown(
            anchor,
            "COUNTDOWN",
            arrayOf(
                "3 SEC",
                "OFF"
            ),
            if (countdownEnabled) 0 else 1
        ) { picked ->
            countdownEnabled =
                picked ==
                    0

            markLivePresetCustom()

            countdownButton.text =
                "COUNTDOWN ▾\n" +
                    if (countdownEnabled) {
                        "3 SEC"
                    } else {
                        "OFF"
                    }

            countdownButton.isSelected =
                countdownEnabled

            saveLiveCameraPreferences()
        }
    }

    private fun showLiveStyleDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Change lower-third style before recording"
            )
            return
        }

        showLivePillDropdown(
            anchor,
            "LOWER THIRD STYLE",
            lowerThirdStyles,
            lowerThirdStyleIndex
        ) { picked ->
            lowerThirdStyleIndex =
                picked

            markLivePresetCustom()

            styleButton.text =
                "LOWER STYLE ▾\n${lowerThirdStyles[lowerThirdStyleIndex]}"

            saveLiveCameraPreferences()
        }
    }

    private fun loadLiveCameraPreferences() {
        val prefs =
            duSharedPreferences(
                "develop_uganda_live_camera",
                Context.MODE_PRIVATE
            )

        liveQualityIndex =
            prefs.getInt(
                "live_quality_index",
                liveQualityIndex
            )
                .coerceIn(
                    0,
                    liveQualityProfiles.lastIndex
                )

        syncLiveQualityState()

        profileIndex =
            prefs.getInt(
                "profile_index",
                profileIndex
            )
                .coerceIn(
                    0,
                    profiles.lastIndex
                )

        lowerThirdStyleIndex =
            prefs.getInt(
                "lower_style",
                lowerThirdStyleIndex
            )
                .coerceIn(
                    0,
                    lowerThirdStyles.lastIndex
                )

        liveHudSizeIndex =
            prefs.getInt(
                "hud_size",
                liveHudSizeIndex
            )
                .coerceIn(
                    0,
                    liveHudLabels.lastIndex
                )

        liveHudContrastIndex =
            prefs.getInt(
                "hud_contrast",
                liveHudContrastIndex
            )
                .coerceIn(
                    0,
                    liveHudContrastLabels.lastIndex
                )

        liveHudBackingIndex =
            prefs.getInt(
                "hud_backing",
                liveHudBackingIndex
            )
                .coerceIn(
                    0,
                    liveHudBackingLabels.lastIndex
                )

        liveEffectIndex =
            prefs.getInt(
                "video_fx",
                liveEffectIndex
            )
                .coerceIn(
                    0,
                    liveEffectLabels.lastIndex
                )

        livePresetIndex =
            prefs.getInt(
                "preset_index",
                livePresetIndex
            )
                .coerceIn(
                    0,
                    livePresetLabels.lastIndex
                )

        audioEnabled =
            prefs.getBoolean(
                "audio",
                audioEnabled
            )

        graphicsEnabled =
            prefs.getBoolean(
                "graphics",
                graphicsEnabled
            )

        countdownEnabled =
            prefs.getBoolean(
                "countdown",
                countdownEnabled
            )
    }

    private fun saveLiveCameraPreferences() {
        duSharedPreferences(
            "develop_uganda_live_camera",
            Context.MODE_PRIVATE
        )
            .edit()
            .putInt(
                "live_quality_index",
                liveQualityIndex
            )
            .putInt(
                "profile_index",
                profileIndex
            )
            .putInt(
                "lower_style",
                lowerThirdStyleIndex
            )
            .putInt(
                "hud_size",
                liveHudSizeIndex
            )
            .putInt(
                "hud_contrast",
                liveHudContrastIndex
            )
            .putInt(
                "hud_backing",
                liveHudBackingIndex
            )
            .putInt(
                "video_fx",
                liveEffectIndex
            )
            .putInt(
                "preset_index",
                livePresetIndex
            )
            .putBoolean(
                "audio",
                audioEnabled
            )
            .putBoolean(
                "graphics",
                graphicsEnabled
            )
            .putBoolean(
                "countdown",
                countdownEnabled
            )
            .apply()
    }

    private fun liveHudBackingAlpha(): Int {
        return when (
            liveHudBackingIndex
        ) {
            0 ->
                0

            2 ->
                58

            else ->
                32
        }
    }

    private fun drawLiveTextBackplate(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        val alpha =
            liveHudBackingAlpha()

        if (
            alpha <=
            0
        ) {
            return
        }

        val metrics =
            paint.fontMetrics

        val padX =
            paint.textSize *
                0.22f

        val padY =
            paint.textSize *
                0.12f

        val background =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    Color.argb(
                        alpha,
                        0,
                        0,
                        0
                    )

                style =
                    Paint.Style.FILL
            }

        canvas.drawRoundRect(
            x -
                padX,
            y +
                metrics.ascent -
                padY,
            x +
                paint.measureText(
                    value
                ) +
                padX,
            y +
                metrics.descent +
                padY,
            paint.textSize *
                0.22f,
            paint.textSize *
                0.22f,
            background
        )
    }

    private fun liveHudOutlineScale(): Float {
        return when (
            liveHudContrastIndex
        ) {
            0 ->
                0.014f

            2 ->
                0.032f

            else ->
                0.022f
        }
    }

    private fun liveHudOutlineColor(): Int {
        return when (
            liveHudContrastIndex
        ) {
            0 ->
                0x26000000

            2 ->
                0x52000000

            else ->
                0x38000000
        }
    }

    private fun liveHudShadowRadius(
        u: Float
    ): Float {
        return when (
            liveHudContrastIndex
        ) {
            0 ->
                0.35f * u

            2 ->
                1.0f * u

            else ->
                0.65f * u
        }
    }

    private fun showLiveEffectDropdown(
        anchor: View
    ) {
        if (
            recording !=
            null
        ) {
            toast(
                "Stop LIVE REC before changing video effect"
            )
            return
        }

        showLivePillDropdown(
            anchor,
            "SAVED VIDEO EFFECT",
            liveEffectLabels,
            liveEffectIndex
        ) { picked ->
            liveEffectIndex =
                picked

            markLivePresetCustom()

            liveEffectButton.text =
                "VIDEO FX ▾\n${liveEffectLabels[liveEffectIndex]}"

            liveEffectButton.isSelected =
                liveEffectIndex !=
                    0

            saveLiveCameraPreferences()

            toast(
                "VIDEO FX ${liveEffectLabels[liveEffectIndex]}"
            )
        }
    }

    private fun showLiveHudBackingDropdown(
        anchor: View
    ) {
        showLivePillDropdown(
            anchor,
            "RECORDED HUD BACKING",
            liveHudBackingLabels,
            liveHudBackingIndex
        ) { picked ->
            liveHudBackingIndex =
                picked

            markLivePresetCustom()

            liveHudBackingButton.text =
                "HUD BACKING ▾\n${liveHudBackingLabels[liveHudBackingIndex]}"

            liveHudBackingButton.isSelected =
                liveHudBackingIndex !=
                    0

            saveLiveCameraPreferences()

            toast(
                "LIVE backing ${liveHudBackingLabels[liveHudBackingIndex]}"
            )
        }
    }

    private fun markLivePresetCustom() {
        if (
            livePresetIndex !=
            0
        ) {
            livePresetIndex =
                0

            if (
                ::livePresetButton.isInitialized
            ) {
                livePresetButton.text =
                    "PRESET ▾\nCUSTOM"

                livePresetButton.isSelected =
                    false
            }
        }
    }

    private fun liveArrayIndex(
        values: Array<String>,
        wanted: String,
        fallback: Int = 0
    ): Int {
        val index =
            values.indexOf(
                wanted
            )

        return if (
            index >=
            0
        ) {
            index
        } else {
            fallback.coerceIn(
                0,
                values.lastIndex
            )
        }
    }

    private fun applyLivePreset(
        picked: Int
    ) {
        livePresetIndex =
            picked.coerceIn(
                0,
                livePresetLabels.lastIndex
            )

        when (
            livePresetLabels[
                livePresetIndex
            ]
        ) {
            "BREAKING" -> {
                profileIndex =
                    liveArrayIndex(
                        profiles,
                        "BREAKING"
                    )

                lowerThirdStyleIndex =
                    liveArrayIndex(
                        lowerThirdStyles,
                        "BREAKING"
                    )

                liveHudSizeIndex =
                    1

                liveHudContrastIndex =
                    2

                audioEnabled =
                    true

                graphicsEnabled =
                    true

                countdownEnabled =
                    true

                liveHudBackingIndex =
                    1

                liveEffectIndex =
                    liveEffectLabels.indexOf(
                        "CLEAN"
                    ).coerceAtLeast(
                        0
                    )
            }

            "INTERVIEW" -> {
                profileIndex =
                    liveArrayIndex(
                        profiles,
                        "INTERVIEW"
                    )

                lowerThirdStyleIndex =
                    liveArrayIndex(
                        lowerThirdStyles,
                        "CLEAN"
                    )

                liveHudSizeIndex =
                    0

                liveHudContrastIndex =
                    1

                audioEnabled =
                    true

                graphicsEnabled =
                    true

                countdownEnabled =
                    false

                liveHudBackingIndex =
                    1

                liveEffectIndex =
                    liveEffectLabels.indexOf(
                        "NATURAL"
                    ).coerceAtLeast(
                        0
                    )
            }

            "EVENT" -> {
                profileIndex =
                    liveArrayIndex(
                        profiles,
                        "EVENT"
                    )

                lowerThirdStyleIndex =
                    liveArrayIndex(
                        lowerThirdStyles,
                        "CLEAN"
                    )

                liveHudSizeIndex =
                    1

                liveHudContrastIndex =
                    1

                audioEnabled =
                    true

                graphicsEnabled =
                    true

                countdownEnabled =
                    true

                liveHudBackingIndex =
                    1

                liveEffectIndex =
                    liveEffectLabels.indexOf(
                        "WARM"
                    ).coerceAtLeast(
                        0
                    )
            }

            "COMMUNITY" -> {
                profileIndex =
                    liveArrayIndex(
                        profiles,
                        "COMMUNITY"
                    )

                lowerThirdStyleIndex =
                    liveArrayIndex(
                        lowerThirdStyles,
                        "MINIMAL"
                    )

                liveHudSizeIndex =
                    1

                liveHudContrastIndex =
                    1

                audioEnabled =
                    true

                graphicsEnabled =
                    true

                countdownEnabled =
                    true
            }

            else -> {
                // CUSTOM leaves current values untouched.

                liveHudBackingIndex =
                    1

                liveEffectIndex =
                    liveEffectLabels.indexOf(
                        "NATURAL"
                    ).coerceAtLeast(
                        0
                    )
            }
        }

        livePresetButton.text =
            "PRESET ▾\n${livePresetLabels[livePresetIndex]}"

        livePresetButton.isSelected =
            livePresetIndex !=
                0

        profileButton.text =
            "PROFILE ▾\n${profiles[profileIndex]}"

        styleButton.text =
            "LOWER STYLE ▾\n${lowerThirdStyles[lowerThirdStyleIndex]}"

        liveHudSizeButton.text =
            "HUD SIZE ▾\n${liveHudLabels[liveHudSizeIndex]}"

        liveHudContrastButton.text =
            "HUD CONTRAST ▾\n${liveHudContrastLabels[liveHudContrastIndex]}"

        liveHudBackingButton.text =
            "HUD BACKING ▾\n${liveHudBackingLabels[liveHudBackingIndex]}"

        liveHudBackingButton.isSelected =
            liveHudBackingIndex !=
                0

        liveEffectButton.text =
            "VIDEO FX ▾\n${liveEffectLabels[liveEffectIndex]}"

        liveEffectButton.isSelected =
            liveEffectIndex !=
                0

        audioButton.text =
            "AUDIO ▾\n" +
                if (
                    audioEnabled
                ) {
                    "ON"
                } else {
                    "OFF"
                }

        graphicsButton.text =
            "GRAPHICS ▾\n" +
                if (
                    graphicsEnabled
                ) {
                    "ON"
                } else {
                    "OFF"
                }

        countdownButton.text =
            "COUNTDOWN ▾\n" +
                if (
                    countdownEnabled
                ) {
                    "3 SEC"
                } else {
                    "OFF"
                }

        countdownButton.isSelected =
            countdownEnabled

        liveSubTitle.text =
            "LIVE STUDIO • ${profiles[profileIndex]} • CONTINUOUS AF/AE/AWB • READY"

        saveLiveCameraPreferences()

        toast(
            "LIVE preset ${livePresetLabels[livePresetIndex]}"
        )
    }

    private fun showLivePresetDropdown(
        anchor: View
    ) {
        if (
            recording !=
            null
        ) {
            toast(
                "Stop LIVE REC before changing preset"
            )
            return
        }

        showLivePillDropdown(
            anchor,
            "LIVE PRESET",
            livePresetLabels,
            livePresetIndex
        ) { picked ->
            applyLivePreset(
                picked
            )
        }
    }

    private fun showLiveHudContrastDropdown(
        anchor: View
    ) {
        showLivePillDropdown(
            anchor,
            "RECORDED HUD CONTRAST",
            liveHudContrastLabels,
            liveHudContrastIndex
        ) { picked ->
            liveHudContrastIndex =
                picked

            markLivePresetCustom()

            liveHudContrastButton.text =
                "HUD CONTRAST ▾\n${liveHudContrastLabels[liveHudContrastIndex]}"

            liveHudContrastButton.isSelected =
                true

            saveLiveCameraPreferences()

            toast(
                "LIVE contrast ${liveHudContrastLabels[liveHudContrastIndex]}"
            )
        }
    }

    private fun showLiveHudSizeDropdown(
        anchor: View
    ) {
        showLivePillDropdown(
            anchor,
            "RECORDED HUD SIZE",
            liveHudLabels,
            liveHudSizeIndex
        ) { picked ->
            liveHudSizeIndex =
                picked

            markLivePresetCustom()

            liveHudSizeButton.text =
                "HUD SIZE ▾\n${liveHudLabels[liveHudSizeIndex]}"

            liveHudSizeButton.isSelected =
                true

            saveLiveCameraPreferences()

            toast(
                "LIVE HUD ${liveHudLabels[liveHudSizeIndex]}"
            )
        }
    }

    private fun showLiveSafeAreaInfo() {
        AlertDialog.Builder(this)
            .setTitle(
                "LIVE OUTPUT SAFE AREA"
            )
            .setMessage(
                "The operator controls stay screen-only. Saved-video branding, ON AIR status and lower-third graphics remain inside the protected 9:16 output area."
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }

    private fun signal(
        value: String
    ): TextView {
        return label(
            value,
            9f,
            DevelopUgandaFivemods8Theme.contentDim,
            true
        ).apply {
            gravity =
                Gravity.CENTER_VERTICAL
        }
    }

    private fun label(
        value: String,
        size: Float,
        color: Int,
        bold: Boolean
    ): TextView {
        return TextView(this).apply {
            text =
                value

            textSize =
                size

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
    }

    private fun rounded(
        fill: Int,
        stroke: Int,
        radius: Int
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape =
                GradientDrawable.RECTANGLE

            cornerRadius =
                dp(radius).toFloat()

            setColor(
                fill
            )

            if (
                stroke !=
                DevelopUgandaFivemods8Theme.transparent
            ) {
                setStroke(
                    dp(1),
                    stroke
                )
            }
        }
    }

    private fun weight():
        LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            dp(40),
            1f
        ).apply {
            marginStart =
                dp(4)

            marginEnd =
                dp(4)
        }
    }

    private fun dp(
        value: Int
    ): Int {
        return (
            value *
                resources.displayMetrics.density
            ).roundToInt()
    }

    private fun toast(
        value: String
    ) {
        Toast.makeText(
            this,
            value,
            Toast.LENGTH_SHORT
        ).show()
    }

    private class LiveOutlineButton(
        context: Context,
        private val accent: Int
    ) : Button(context) {

        private val density =
            context.resources
                .displayMetrics
                .density

        private val ringPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    3.8f *
                        density

                color =
                    DevelopUgandaFivemods8Theme.outline
            }

        private val idleFill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    DevelopUgandaFivemods8Theme.surfaceScrim(82)
            }

        private val pressedFill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    DevelopUgandaFivemods8Theme.accentScrim(90)
            }

        private val selectedFill =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    DevelopUgandaFivemods8Theme.accent
            }

        init {
            gravity =
                Gravity.CENTER

            background =
                ColorDrawable(
                    DevelopUgandaFivemods8Theme.transparent
                )

            stateListAnimator =
                null
        }

        private fun selectedWordColor(): Int =
            DevelopUgandaFivemods8Theme.surface

        override fun drawableStateChanged() {
            super.drawableStateChanged()

            setTextColor(
                if (
                    isSelected
                ) {
                    selectedWordColor()
                } else {
                    DevelopUgandaFivemods8Theme.content
                }
            )

            invalidate()
        }

        override fun onDraw(
            canvas: Canvas
        ) {
            val inset =
                4.5f *
                    density

            val radius =
                (
                    height -
                        inset *
                            2f
                    ) /
                    2f

            ringPaint.color =
                if (isSelected) {
                    DevelopUgandaFivemods8Theme.accent
                } else {
                    DevelopUgandaFivemods8Theme.outline
                }

            canvas.drawRoundRect(
                inset,
                inset,
                width -
                    inset,
                height -
                    inset,
                radius,
                radius,
                when {
                    isSelected ->
                        selectedFill

                    isPressed ->
                        pressedFill

                    else ->
                        idleFill
                }
            )

            canvas.drawRoundRect(
                inset,
                inset,
                width -
                    inset,
                height -
                    inset,
                radius,
                radius,
                ringPaint
            )

            super.onDraw(
                canvas
            )
        }
    }

    private class LiveRecordButtonView(
        context: Context
    ) : View(context) {

        private val ring =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE

                strokeWidth =
                    5f

                color =
                    DevelopUgandaFivemods8Theme.record
            }

        private val center =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    DevelopUgandaFivemods8Theme.surface
            }

        private val centerMark =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    DevelopUgandaFivemods8Theme.content
            }

        private var isRecording =
            false

        init {
            isClickable =
                true
        }

        fun setRecordingState(
            value: Boolean
        ) {
            isRecording =
                value

            center.color =
                if (
                    value
                ) {
                    DevelopUgandaFivemods8Theme.record
                } else {
                    DevelopUgandaFivemods8Theme.surface
                }

            invalidate()
        }

        override fun onDraw(
            canvas: Canvas
        ) {
            super.onDraw(
                canvas
            )

            val cx =
                width / 2f

            val cy =
                height / 2f

            val base =
                minOf(
                    width,
                    height
                ) *
                    0.29f

            canvas.drawCircle(
                cx,
                cy,
                base +
                    7f,
                ring
            )

            canvas.drawCircle(
                cx,
                cy,
                base,
                center
            )

            if (
                isRecording
            ) {
                val half =
                    base *
                        0.28f

                canvas.drawRoundRect(
                    cx -
                        half,
                    cy -
                        half,
                    cx +
                        half,
                    cy +
                        half,
                    half *
                        0.28f,
                    half *
                        0.28f,
                    centerMark
                )
            } else {
                canvas.drawCircle(
                    cx,
                    cy,
                    base *
                        0.17f,
                    centerMark
                )
            }
        }
    }
}
