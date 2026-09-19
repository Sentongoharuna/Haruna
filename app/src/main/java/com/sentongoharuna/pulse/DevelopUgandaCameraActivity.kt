package com.sentongoharuna.pulse

import android.Manifest
import android.app.AlertDialog
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
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.location.Address
import android.location.Geocoder
import android.location.GnssStatus
import android.location.LocationManager
import android.media.MediaCodecInfo
import android.media.MediaMetadataRetriever
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.AspectRatio
import androidx.camera.core.DynamicRange
import androidx.camera.core.ExposureState
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.TorchState
import androidx.camera.core.ZoomState
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.effects.Frame
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.OverlayEffect as Media3OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.ArrayDeque
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sin

@OptIn(UnstableApi::class)
open class DevelopUgandaCameraActivity : AppCompatActivity(), SensorEventListener {

    private companion object {
        const val ACTION_SCENE = 1
        const val ACTION_LOOK = 2
        const val ACTION_QUALITY = 3
        const val ACTION_CAPTURE_MODE = 4
        const val ACTION_LENS = 5
        const val ACTION_TORCH = 6
        const val ACTION_RECORD = 7
        const val ACTION_IDENTITY = 8
        const val ACTION_VIEW_MODE = 9
        const val ACTION_SETTINGS = 10
        const val ACTION_GUIDES = 11
        const val ACTION_RESET = 12
        const val ACTION_AUTO_UI = 13
        const val ACTION_LOCK = 14
        const val ACTION_INTEGRITY = 15
        const val ACTION_CAPABILITIES = 16
        const val ACTION_CLEAN = 17
        const val ACTION_HUD_SIZE = 18
        const val ACTION_HUD_CONTRAST = 19
        const val ACTION_REPORT_PRESET = 20
        const val ACTION_HUD_BACKING = 21
        const val ACTION_AUTO_DIRECTOR = 22
        const val ACTION_SHOT_ASSIST = 23
        const val ACTION_DIRECTOR = 24
        const val ACTION_CONTINUITY = 25
        const val ACTION_HEALTH = 26
        const val ACTION_BRAND_METADATA = 27
        const val ACTION_COLOR_ENGINE = 28
    }


    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var guidesView: GuidesView

    // V187: dedicated screen-space narration. This is visible to the
    // operator but is NOT burned into the recording. The recorded overlay
    // keeps its existing safe geometry independently.
    private lateinit var previewNarrationPanel: LinearLayout
    private lateinit var previewBrandView: TextView
    private lateinit var previewTagView: TextView
    private lateinit var previewIdentityView: TextView
    private lateinit var previewClockView: TextView
    private lateinit var previewModeView: TextView
    private lateinit var previewPlaceView: TextView
    private lateinit var previewGpsView: TextView
    private lateinit var previewNavView: TextView
    private lateinit var previewSystemView: TextView
    private lateinit var previewHealthView: TextView
    private lateinit var cameraExperienceBannerView: TextView
    private var broadcastCameraChrome: DevelopUgandaBroadcastCameraChrome.Controller? = null
    private var f12CameraShell: DevelopUgandaFivemods12CameraShell.Controller? = null
    private lateinit var autoViewDescriptionView: TextView
    private lateinit var shotQualityGuardView: TextView
    private lateinit var shotAssistView: DevelopUgandaShotAssistView
    private lateinit var directorOverlayView: DevelopUgandaDirectorOverlayView
    private var interviewObservationEngine: DevelopUgandaInterviewObservationEngine? = null
    private var interviewAnnotatedExporter: DevelopUgandaInterviewAnnotatedExporter? = null

    // FIVEMODS 9 dual-delivery and truthful capture telemetry.
    private val f9DualOutputExporter by lazy {
        DevelopUgandaFivemods9DualOutputExporter(
            context = this,
            recordingActive = { recording != null },
            onFinished = ::f9HandleDualOutput,
        )
    }
    private var f9ActiveTake: DevelopUgandaFivemods9DualOutputExporter.TakeMetadata? = null
    @Volatile private var f9ActualIso: Int? = null
    @Volatile private var f9ActualShutterNs: Long? = null
    @Volatile private var f9AeReadout = ""
    @Volatile private var f9AwbReadout = ""
    @Volatile private var f9AudioPeakDbfs: Double? = null
    @Volatile private var f9ActiveMic: String? = null
    private var f9SilenceStartedAtMs = 0L

    private val f9CaptureCallback =
        object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
            override fun onCaptureCompleted(
                session: android.hardware.camera2.CameraCaptureSession,
                request: android.hardware.camera2.CaptureRequest,
                result: android.hardware.camera2.TotalCaptureResult
            ) {
                f9ActualIso =
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_SENSITIVITY)
                f9ActualShutterNs =
                    result.get(android.hardware.camera2.CaptureResult.SENSOR_EXPOSURE_TIME)
                f9AeReadout = f9AeReadout(
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AE_STATE),
                    request.get(android.hardware.camera2.CaptureRequest.CONTROL_AE_LOCK)
                )
                f9AwbReadout = f9AwbReadout(
                    result.get(android.hardware.camera2.CaptureResult.CONTROL_AWB_STATE),
                    request.get(android.hardware.camera2.CaptureRequest.CONTROL_AWB_LOCK)
                )
                f12CameraShell?.onFrame()
            }
        }
    private var interviewHudView: DevelopUgandaInterviewHudView? = null
    private lateinit var v255MonitorView: DevelopUgandaV255ProMonitorView
    private lateinit var focusReticleView: TextView
    private lateinit var horizonGuardView: TextView
    private lateinit var motionGuardView: TextView
    private lateinit var lightAdvisorView: TextView
    private lateinit var audioGuardView: TextView
    private lateinit var thermalGuardView: TextView
    private lateinit var previewModeToneView: View

    private lateinit var brandView: TextView
    private lateinit var statusView: TextView
    private lateinit var timecodeView: TextView
    private lateinit var formatView: TextView
    private lateinit var locationView: TextView
    private lateinit var weatherView: TextView
    private lateinit var systemView: TextView
    private lateinit var recordButton: Button
    private lateinit var lensButton: Button
    private lateinit var torchButton: Button
    private lateinit var zoomSeek: SeekBar
    private lateinit var exposureSeek: SeekBar
    private lateinit var sceneButton: Button
    private lateinit var lookButton: Button
    private lateinit var qualityButton: Button
    private lateinit var captureModeButton: Button
    private lateinit var colorButton: Button
    private lateinit var identityButton: Button
    private lateinit var viewModeButton: Button
    private lateinit var settingsButton: Button
    private lateinit var guidesButton: Button
    private lateinit var resetButton: Button
    private lateinit var settingsSummaryView: TextView
    private lateinit var reportRecordState: ReportRecordStateView

    private lateinit var bottomDeck: LinearLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var identityRow: LinearLayout
    private lateinit var reportToolsRow: LinearLayout
    private lateinit var reportAdvancedRow: LinearLayout
    private lateinit var zoomRow: LinearLayout
    private lateinit var exposureRow: LinearLayout
    private lateinit var actionRow: LinearLayout

    private lateinit var autoUiButton: Button
    private lateinit var lockButton: Button
    private lateinit var integrityButton: Button
    private lateinit var capabilitiesButton: Button
    private lateinit var cleanModeButton: Button
    private lateinit var hudSizeButton: Button
    private lateinit var hudContrastButton: Button
    private lateinit var hudBackingButton: Button
    private lateinit var reportPresetButton: Button
    private lateinit var autoDirectorButton: Button
    private lateinit var assistButton: Button
    private lateinit var directorButton: Button
    private lateinit var continuityButton: Button
    private lateinit var healthButton: Button
    private lateinit var brandMetadataButton: Button
    private lateinit var reportDisplayRow: LinearLayout
    private lateinit var reportOutputRow: LinearLayout
    private lateinit var reportDirectorRow: LinearLayout

    private val reportPresetLabels =
        arrayOf(
            "CUSTOM",
            "FIELD",
            "OUTDOOR",
            "NIGHT",
            "INTERVIEW",
            "CINEMA"
        )

    private var reportPresetIndex = 5

    private var autoDirectorEnabled = false
    private var autoDirectorLastSwitchMs = 0L
    private var autoDirectorReason = "MANUAL"

    private val reportHudLabels =
        arrayOf(
            "COMPACT",
            "STANDARD",
            "LARGE"
        )

    private val reportHudScales =
        floatArrayOf(
            1.04f,
            1.16f,
            1.28f
        )

    private var reportHudSizeIndex = 1

    private val reportHudContrastLabels =
        arrayOf(
            "SOFT",
            "BALANCED",
            "STRONG"
        )

    private var reportHudContrastIndex = 1

    private val reportHudBackingLabels =
        arrayOf(
            "NONE",
            "SOFT",
            "STRONG"
        )

    private var reportHudBackingIndex = 1

    private var halfPreviewMode = false
    private var detailedSettingsVisible = false
    private var previewGuidesEnabled = true
    private var autoHideOperatorUi = true
    private var operatorLocked = false
    private var integrityEnabled = true
    private var operatorControlsHidden = false
    private var cleanModeEnabled = false

    // V251 PRO ASSIST ENGINE
    // Screen aids and saved-media policy are intentionally independent:
    // the operator can keep a full HUD while the saved master stays clean.
    private var v251CleanMasterRecord = true
    private val v271SettingHistory = java.util.ArrayDeque<Pair<String, String>>()
    private var v251FaceExposurePriority = true
    private var v251ProAssistEnabled = false
    private var v251LastFaceMeterMs = 0L

    // V277 LIGHTING + EXPOSURE INTELLIGENCE
    // Normalized primary-face position from the existing screen-only ML Kit overlay.
    // Used only to sample the preview for exposure guidance; never written to media.
    private var v277PrimaryFaceX: Float? = null
    private var v277PrimaryFaceY: Float? = null
    private var v277PrimaryFaceArea: Float? = null
    private var v277LastFaceSeenMs: Long = 0L


    // V255 PRO MONITOR + TRACKING
    // New features remain opt-in and screen-only unless they control the real
    // CameraX / Camera2 focus, metering or thermal policy explicitly.
    private var v255SubjectTracking = "OFF"
    private var v255TrackingResponseState = "NORMAL"
    private var v255WaveformOn = false
    private var v255FalseColorOn = false
    private var v255FalseColorStrengthState = 40
    private var v255AudioHeadroomOn = true
    private var v255AudioHeadroomTargetState = -12
    private var v255PerformanceMonitorOn = true
    private var v255ThermalPolicyState = "AUTO SAFE"
    private var v255FocusPullOn = false
    private var v255FocusAState = 20
    private var v255FocusBState = 75
    private var v255FocusPullMsState = 1200
    private var v255FocusPullGeneration = 0
    private var v255FocusPullRunning = false
    private var v255LastTrackMeterMs = 0L
    @Volatile private var v255RecordedDurationNs = 0L
    @Volatile private var v255RecordedBytes = 0L

    // V256 TACTILE CONTROL + CLEAN CAM
    // These preferences only affect operator control feel and timecode behaviour.
    // They never change the V252/V253/V254/V255 camera colour palette.
    private var v256SwitchHapticsOn = true
    private var v256SwitchMotionOn = true
    private var v256TimecodeModeState = "REC RUN"
    private var v256FreeRunAnchorRealtime = SystemClock.elapsedRealtime()

    // V257 RECORD CORE + MOTION UI
    // Recording-core settings are real CameraX-facing controls where the public API supports them.
    // Home motion is operator-only and never changes recorded pixels.
    private var v257BitrateModeState = "STANDARD"
    private var v257RecordingWatchdogOn = true
    private var v257ClipSafeFeedbackOn = true
    private var v257HomeMotionOn = true
    private var v257HomeMotionStyleState = "SUBTLE"
    private var v257ReadyPulseOn = true
    private var v257ReadyPulsePhase = false
    private var v257LastWatchdogNotice = ""

    // V259 SMART SHOOT + MEDIA INTELLIGENCE
    // Decision support remains truthful: scores are derived from device state,
    // existing shot warnings and real ML scene labels. Nothing is burned into media.
    private var v259SmartExposureAssistOn = true
    private var v259SmartRecordCheckOn = true
    private var v259ConfidenceMeterOn = true
    private var v259ConfidenceProfileState = "NORMAL"
    private var v259SceneSuggestionsOn = true
    private var v259HorizonAssistOn = true
    private var v259StabilityAssistOn = true
    private var v259TakeFlagsOn = true

    // V260 PROJECT CONTROL + FAILSAFE
    // Project/slate state is metadata + file organization only. It does not alter recorded pixels.
    // Storage reservation and thermal strategy affect preflight safety thresholds truthfully.
    private var v260ProjectModeOn = true
    private var v260ProjectNameState = "FIELD PROJECT"
    private var v260CameraNameState = "CAM A"
    private var v260SceneNumberState = 1
    private var v260TakeNumberState = 1
    private var v260AutoTakeCounterOn = true
    private var v260StorageReservationOn = true
    private var v260StorageReserveGbState = 2
    private var v260ThermalStrategyState = "BALANCED"
    private var v260ActiveSceneNumber = 1
    private var v260ActiveTakeNumber = 1

    // V261 PRO CAM • DIRECTOR MONITOR
    // Scope and framing tools are screen-only. Shutter angle uses the existing
    // real Camera2 manual exposure path only where the lens exposes manual sensor control.
    private var v261RgbParadeOn = false
    private var v261VectorscopeOn = false
    private var v261SkinToneReferenceOn = true
    private var v261HighlightShadowAssistOn = true
    private var v261FrameGuidesOn = true
    private var v261FrameGuideAspectState = "2.39:1"
    private var v261DirectorHudModeState = "STANDARD"
    private var v261AnamorphicRatioState = "OFF"

    // V262 PRO CAM • REMOTE DIRECTOR + MULTI-CAM FOUNDATION
    // The camera phone hosts a PIN-authenticated local-LAN control/preview service.
    // Remote preview is deliberately low bandwidth and never replaces the local CameraX master.
    private var v262RemoteHostOn = false
    private var v262RemotePreviewOn = true
    private var v262RemoteControlOn = true
    private var v262RecordPriorityOn = true
    private var v262AutoReconnectOn = true
    private var v262DirectorMarkersOn = true
    private var v262PreviewQualityState = "BALANCED"
    private var v262PairingPinState = "2626"
    private var v262RemoteServer: DevelopUgandaV262RemoteServer? = null
    private val v262RemoteMarkers = mutableListOf<String>()

    // V263 PRO CAM • MULTI-CAM DIRECTOR
    // These preferences control only director-side grouping, sync-command behaviour and UI.
    // They never reduce or redirect the local CameraX master recording.
    private var v263SyncRecordOn = true
    private var v263TallyOn = true
    private var v263AutoPreviewQualityOn = true
    private var v263SyncSlateOn = true
    private var v263GridMotionOn = true
    private var v263MaxCamerasState = 4
    private var v263DefaultGroupState = "ALL"

    // V264 PRO CAM • LIVE CUT + AUTO-SYNC
    // Director-side edit decisions, clock maps and diagnostics never replace local masters.
    private var v264CutRecordingOn = true
    private var v264ProgramTallyOn = true
    private var v264AutoClockSyncOn = true
    private var v264PushCameraSettingsOn = true
    private var v264HandoffSuggestionOn = true
    private var v264NetworkDiagnosticsOn = true
    private var v264DirectorMotionOn = true
    private var v264ExportFormatState = "JSON"
    private var v264TransportModeState = "AUTO"

    // V265 PRO CAM • PROXY SYNC + REVIEW
    // Separate low-bitrate review proxies are created only after a successful master finalize.
    // Masters stay untouched on each camera phone.
    private var v265ProxyEnabledState = true
    private var v265AutoTransferState = true
    private var v265ProxyQualityState = "BALANCED"
    private var v265ProxyIntegrityState = true
    private var v265ReviewMotionState = true

    private var gestureDownX = 0f
    private var gestureDownY = 0f
    private var focusPunchIn = false
    private var gestureStartZoom = 1f
    private var gestureStartExposure = 0
    private var gestureMoved = false
    private var lastPreviewTapMs = 0L
    private var focusLongPressTriggered = false
    private var focusLockActive = false
    private var focusAttempted = false
    private var focusSuccessful: Boolean? = null
    private var preflightApprovedOnce = false
    private var f12SafetyOverrideApprovedOnce = false
    private var shotAssistModeIndex = DevelopUgandaShotAssistView.MODE_OFF
    private val shotAssistModeLabels = arrayOf("OFF", "PEAK", "ZEBRA", "BOTH")
    private val recordingWarningsSeen = linkedSetOf<String>()
    private lateinit var autoViewLabeler: ImageLabeler
    private var autoViewBusy = false
    private var autoViewSummary = "AUTO VIEW • analysing scene"

    private val directorRunnable =
        object : Runnable {
            override fun run() {
                if (
                    ::directorOverlayView.isInitialized
                ) {
                    directorOverlayView.visibility =
                        if (
                            (directorEnabled || v255SubjectTracking != "OFF") &&
                            !cleanModeEnabled
                        ) {
                            View.VISIBLE
                        } else {
                            View.GONE
                        }

                    directorOverlayView.setDirectorEnabled(
                        directorEnabled || v251FaceExposurePriority || v259SmartExposureAssistOn || v255SubjectTracking != "OFF"
                    )
                }

                if (::v255MonitorView.isInitialized) {
                    v255MonitorView.visibility =
                        if (v261MonitorVisible() && !cleanModeEnabled) View.VISIBLE else View.GONE
                }

                if (
                    (directorEnabled || v251FaceExposurePriority || v259SmartExposureAssistOn || v255SubjectTracking != "OFF" || v255MonitorNeedsFrames() || DevelopUgandaV277LightingExposure.bool(this@DevelopUgandaCameraActivity, "lighting_coach", true)) &&
                    !cleanModeEnabled &&
                    ::previewView.isInitialized &&
                    ::directorOverlayView.isInitialized &&
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
                        if (directorEnabled || v251FaceExposurePriority || v259SmartExposureAssistOn || v255SubjectTracking != "OFF") {
                            directorOverlayView.submitFrame(
                                bitmap,
                                isDirectorPeopleMode()
                            )
                        }

                        if (
                            ::v255MonitorView.isInitialized &&
                            v255MonitorNeedsFrames()
                        ) {
                            v255MonitorView.submitFrame(bitmap)
                        }

                        // V277: reuse the already-acquired PreviewView bitmap for a
                        // screen-only exposure/light reading. No CameraX use case is
                        // added and no V277 guide can be burned into the saved master.
                        try {
                            DevelopUgandaV277LightingExposure.analysePreview(
                                this@DevelopUgandaCameraActivity,
                                bitmap,
                                v277PrimaryFaceX.takeIf { SystemClock.elapsedRealtime() - v277LastFaceSeenMs < 1800L },
                                v277PrimaryFaceY.takeIf { SystemClock.elapsedRealtime() - v277LastFaceSeenMs < 1800L },
                                v277PrimaryFaceArea.takeIf { SystemClock.elapsedRealtime() - v277LastFaceSeenMs < 1800L }
                            )
                        } catch (_: Exception) {
                        }
                    }
                }

                uiHandler.postDelayed(
                    this,
                    if (v255SubjectTracking != "OFF" || v255MonitorNeedsFrames() || DevelopUgandaV277LightingExposure.bool(this@DevelopUgandaCameraActivity, "lighting_coach", true)) 620L else 1100L
                )
            }
        }

    private val hideFocusReticleRunnable =
        Runnable {
            if (
                ::focusReticleView.isInitialized &&
                !focusLockActive
            ) {
                focusReticleView.visibility =
                    View.GONE
            }
        }

    private val focusLockRunnable =
        Runnable {
            if (
                !gestureMoved &&
                !operatorLocked &&
                ::previewView.isInitialized
            ) {
                focusLongPressTriggered =
                    true

                togglePersistentFocusLock(
                    gestureDownX,
                    gestureDownY
                )
            }
        }

    private val v257HomeMotionRunnable =
        object : Runnable {
            override fun run() {
                if (
                    !v257HomeMotionOn ||
                    !v257ReadyPulseOn ||
                    recording != null ||
                    !::recordButton.isInitialized
                ) {
                    if (::recordButton.isInitialized) {
                        recordButton.animate().cancel()
                        recordButton.scaleX = 1f
                        recordButton.scaleY = 1f
                        recordButton.alpha = 1f
                    }
                    return
                }

                val scale =
                    when (v257HomeMotionStyleState) {
                        "NORMAL" -> 1.035f
                        "REDUCED" -> 1.012f
                        else -> 1.022f
                    }
                val duration =
                    when (v257HomeMotionStyleState) {
                        "NORMAL" -> 680L
                        "REDUCED" -> 980L
                        else -> 820L
                    }
                val target = if (v257ReadyPulsePhase) 1f else scale
                val targetAlpha = if (v257ReadyPulsePhase) 1f else 0.94f
                v257ReadyPulsePhase = !v257ReadyPulsePhase

                recordButton.animate()
                    .scaleX(target)
                    .scaleY(target)
                    .alpha(targetAlpha)
                    .setDuration(duration)
                    .start()

                uiHandler.postDelayed(this, duration + 80L)
            }
        }

    private val v257RecordingWatchdogRunnable =
        object : Runnable {
            override fun run() {
                if (recording == null || !v257RecordingWatchdogOn) return

                val free = freeStorageGb()
                val battery = batteryPct()
                val criticalStorage = free != null && free <= 0L
                val criticalBattery = battery != null && battery <= 2

                if (criticalStorage || criticalBattery) {
                    val reason = if (criticalStorage) "STORAGE CRITICAL" else "BATTERY CRITICAL"
                    if (v257LastWatchdogNotice != reason) {
                        v257LastWatchdogNotice = reason
                        toast("$reason • FINALIZING CLIP SAFELY")
                    }
                    try {
                        recording?.stop()
                    } catch (_: Exception) {
                    }
                    return
                }

                val v276Warnings = DevelopUgandaV276RecordingSafety.observeRecordingHealth(this@DevelopUgandaCameraActivity)
                if (v276Warnings.isNotEmpty()) recordingWarningsSeen.addAll(v276Warnings)

                val warning =
                    when {
                        free != null && free <= 2L -> "STORAGE LOW • ${free}GB"
                        battery != null && battery <= 5 -> "BATTERY LOW • ${battery}%"
                        thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> "THERMAL ${thermalStateLabel()}"
                        v276Warnings.isNotEmpty() -> v276Warnings.first()
                        else -> ""
                    }

                if (warning.isNotBlank() && warning != v257LastWatchdogNotice) {
                    v257LastWatchdogNotice = warning
                    toast("RECORD WATCHDOG • $warning")
                }

                uiHandler.postDelayed(this, 1500L)
            }
        }

    private val autoHideRunnable =
        Runnable {
            if (
                autoHideOperatorUi &&
                recording != null
            ) {
                setReportOperatorControlsHidden(
                    true
                )
            }
        }

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageCapture: ImageCapture? = null
    private var recording: Recording? = null
    private var overlayEffect: OverlayEffect? = null
    private var automaticSocialTransformer: Transformer? = null
    private var automaticSocialExportActive = false
    private var additiveDeliveryTransformer: Transformer? = null
    private var additiveDeliveryExportActive = false
    private val additiveDeliveryQueue = ArrayDeque<AdditiveDeliveryRequest>()
    private var statusSegmentRestartPending = false
    private var crashSafeTake: DevelopUgandaCrashSafeTake? = null
    private var crashSafeTakeRoot = ""
    private var crashSafeRestartPending = false
    private var crashSafeSegmentContinuation = false
    private var crashSafeSegmentStopRequested = false
    private var crashSafeThermalStopRequested = false
    private var f12ProtectionStopRequested = false
    private var interviewAnalysisEnabled = true
    private var interviewBurnInEnabled = false
    private var interviewTwoShotEnabled = false
    private var selectedCameraDeviceId: String? = null
    private var directorEnabled = true
    private var lastV233ColorMonitorKey = ""
    private var v229ColorOverlayLabel = "AUTO"
    private var useFront = false
    private var torchOn = false

    private data class AdditiveDeliveryRequest(
        val inputUri: Uri,
        val sourceName: String,
        val profile: DevelopUgandaModeProfile
    )

    private val sceneModes = listOf(
        "REPORTER",
        "NEWS",
        "CINEMA",
        "MOVIE",
        "OUTDOOR",
        "INDOOR",
        "NIGHT",
        "INTERVIEW",
        "DOCUMENTARY"
    )
    private val lookModes = listOf(
        "CLEAN",
        "NATURAL",
        "WARM",
        "COOL",
        "TEAL",
        "GOLD",
        "SOFT",
        "SUNSET",
        "BLUE HOUR",
        "NIGHT",
        "MONO"
    )
    // V203 capability-driven recording profiles.
    // SOCIAL FHD remains the safest upload master for TikTok/Instagram.
    private val qualityModes = listOf(
        "SOCIAL FHD",
        "SOCIAL 60",
        "MASTER UHD",
        "UHD 60",
        "MASTER HDR",
        "SOCIAL HDR",
        "ACTION STAB",
        "ACTION 60",
        "LOW LIGHT",
        "FAST HD"
    )
    private val captureModes = listOf(
        "VIDEO",
        "PHOTO"
    )
    private var sceneIndex = 2
    private var lookIndex = 0
    private var qualityIndex = 2
    private var captureModeIndex = 0
    private var sceneExposureTarget = 0

    private var v244ShutterAngle = 0
    private var v244Iso = 0
    private var v244WhiteBalanceIndex = 0
    private var v246ImageEngineEnabled = true
    private var v246HighlightProtectEnabled = true
    private var v246LastImageEngineStatus = "AUTO ISP"
    private var v247AdaptiveDetailEnabled = true
    private var v247MotionGuideEnabled = true
    private var v247LastDetailStatus = "ADAPTIVE"
    private val v244WhiteBalanceLabels = arrayOf("AUTO", "DAYLIGHT", "CLOUDY", "TUNGSTEN", "FLUORESCENT")

    private var activeVideoFpsLabel = "AUTO FPS"
    private var activeVideoStabilizationLabel = "STAB AUTO"
    private var activeVideoDynamicRangeLabel = "SDR"
    private var activeVideoAspectLabel = "9:16 SOCIAL SAFE"
    // FIX4 core delivery state. These are the capabilities actually selected
    // for the current CameraX session, not promises made by the UI labels.
    private var coreDeliveredProfileLabel = "DEVICE AUTO"
    private var coreDeliveredBitrateBps = 0
    private var coreFocusGeneration = 0L

    private val weather = WeatherRepository()
    private lateinit var telemetryRecorder: TelemetryRecorder
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private var rotationVectorSensor: Sensor? = null
    private var ambientLightSensor: Sensor? = null
    private lateinit var powerManager: PowerManager
    @Volatile private var thermalStatus =
        PowerManager.THERMAL_STATUS_NONE
    private var thermalListenerRegistered = false

    private val thermalStatusListener =
        PowerManager.OnThermalStatusChangedListener {
                status ->
            thermalStatus =
                status

            runOnUiThread {
                updateThermalGuard()
                refreshHud()
            }
        }

    private lateinit var locationManager: LocationManager

    @Volatile private var lat: Double? = null
    @Volatile private var lon: Double? = null
    @Volatile private var alt: Double? = null
    @Volatile private var accuracy: Float? = null
    @Volatile private var speedKmh: Float? = null
    @Volatile private var heading: Float? = null
    @Volatile private var placeName = "Locating…"
    @Volatile private var estimatedUploadKbps: Int? = null
    @Volatile private var lastGpsUpdateMs = 0L
    @Volatile private var distanceTravelledM = 0f
    @Volatile private var previousTrackLat: Double? = null
    @Volatile private var previousTrackLon: Double? = null
    @Volatile private var compassAzimuthDeg: Float? = null
    @Volatile private var phonePitchDeg: Float? = null
    @Volatile private var phoneRollDeg: Float? = null
    @Volatile private var cameraShakeScore = 0f
    @Volatile private var lastMotionSampleMs = 0L
    @Volatile private var ambientLux: Float? = null
    @Volatile private var gnssSatellitesVisible = -1
    @Volatile private var gnssSatellitesUsed = -1
    @Volatile private var audioAmplitude = 0.0
    @Volatile private var audioPeakAmplitude = 0.0
    @Volatile private var audioStateLabel = "MIC READY"
    private var v273ZoomRampGeneration = -1L
    private var v273ExposureRampGeneration = -1L

    private var gnssCallbackHolder: Any? = null
    private var reporterName = "CITIZEN"
    private var storyId = ""
    private var reportId = ""
    private var cameraExperienceId =
        "V210_ALL_PRO"

    protected open fun defaultCameraExperienceId(): String {
        return "V210_ALL_PRO"
    }

    /**
     * The additive pages are intentionally isolated from all legacy experience
     * defaults.  A null profile means the pre-existing camera continues down
     * its original path without any changed values.
     */
    private fun additiveModeProfile(): DevelopUgandaModeProfile? =
        DevelopUgandaModeProfiles.forExperience(cameraExperienceId)

    private fun currentCameraPage(): DevelopUgandaCameraPage =
        additiveModeProfile()?.page ?: DevelopUgandaCameraPage.MAIN

    private fun isAdditiveCameraPage(): Boolean =
        additiveModeProfile() != null

    private fun isInterviewCamera(): Boolean =
        additiveModeProfile()?.page == DevelopUgandaCameraPage.INTERVIEW

    /** Release only while idle, before the shared navigator opens another mode. */
    private fun releaseCameraForModeSwitch() {
        if (recording != null) return
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }
        try {
            overlayEffect?.close()
        } catch (_: Exception) {
        }
        // ProcessCameraProvider is process-wide. Clear this Activity's handle
        // so its later onDestroy cannot unbind the destination's new session.
        provider = null
        overlayEffect = null
        camera = null
        videoCapture = null
        imageCapture = null
    }

    private var reportDisplayMode = "FIELD REPORT"
    private var clipSequence = 0
    private var recordStartUtc = "--"


    private var recStarted = 0L
    private var lastWeatherAt = 0L
    private var lastPlaceAt = 0L
    private var baseName = ""

    private val uiHandler = Handler(Looper.getMainLooper())

    private val shotAssistRunnable =
        object : Runnable {
            override fun run() {
                if (
                    shotAssistModeIndex !=
                        DevelopUgandaShotAssistView.MODE_OFF &&
                    ::previewView.isInitialized &&
                    ::shotAssistView.isInitialized
                ) {
                    val bitmap =
                        try {
                            previewView.bitmap
                        } catch (_: Exception) {
                            null
                        }

                    if (bitmap != null) {
                        shotAssistView.submitFrame(
                            bitmap
                        )
                    }
                }

                uiHandler.postDelayed(
                    this,
                    700L
                )
            }
        }


    private val autoViewRunnable =
        object : Runnable {
            override fun run() {
                analyzeAutoViewFrame()

                uiHandler.postDelayed(
                    this,
                    3500L
                )
            }
        }
    private val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val tick = object : Runnable {
        override fun run() {
            refreshHud()
            if (recording != null) writeTelemetry()
            uiHandler.postDelayed(this, 1000L)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val l = result.lastLocation ?: return

            lat = l.latitude
            lon = l.longitude
            alt = if (l.hasAltitude()) l.altitude else null
            accuracy = if (l.hasAccuracy()) l.accuracy else null
            speedKmh = if (l.hasSpeed()) l.speed * 3.6f else null
            heading = if (l.hasBearing()) l.bearing else null

            val now = System.currentTimeMillis()
            lastGpsUpdateMs = now

            if (recording != null) {
                val pLat = previousTrackLat
                val pLon = previousTrackLon

                if (pLat != null && pLon != null) {
                    val result = FloatArray(1)
                    android.location.Location.distanceBetween(
                        pLat,
                        pLon,
                        l.latitude,
                        l.longitude,
                        result
                    )

                    val segmentM = result[0]
                    val goodEnough =
                        (accuracy ?: 999f) <= 60f

                    if (
                        goodEnough &&
                        segmentM >= 0.7f &&
                        segmentM <= 250f
                    ) {
                        distanceTravelledM += segmentM
                    }
                }

                previousTrackLat = l.latitude
                previousTrackLon = l.longitude
            }

            if (now - lastPlaceAt > 15_000L) {
                lastPlaceAt = now
                Thread {
                    try {
                        val a: Address? = Geocoder(
                            this@DevelopUgandaCameraActivity,
                            Locale.getDefault()
                        ).getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()

                        if (a != null) {
                            val p = listOfNotNull(
                                a.subLocality,
                                a.locality,
                                a.subAdminArea,
                                a.adminArea,
                                a.countryName
                            ).distinct().joinToString(", ")

                            if (p.isNotBlank()) placeName = p
                        }
                    } catch (_: Exception) {
                    }
                }.start()
            }

            if (now - lastWeatherAt > 10 * 60_000L) {
                lastWeatherAt = now
                weather.refresh(l.latitude, l.longitude) { }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        telemetryRecorder = TelemetryRecorder(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        sensorManager =
            getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationVectorSensor =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_ROTATION_VECTOR
            )

        ambientLightSensor =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_LIGHT
            )

        powerManager =
            getSystemService(
                Context.POWER_SERVICE
            ) as PowerManager

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {
            thermalStatus =
                powerManager.currentThermalStatus
        }

        locationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager

        cameraExperienceId =
            intent.getStringExtra(
                "develop_uganda_camera_experience"
            )
                ?.trim()
                ?.uppercase(
                    Locale.US
                )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?: defaultCameraExperienceId()

        loadReporterIdentity()
        loadReportCameraPreferences()
        loadV251ProAssistPreferences()
        loadV255ProMonitorPreferences()
        loadV256ControlPreferences()
        loadV257RecordCorePreferences()
        loadV259SmartShootPreferences()
        loadV260ProjectControlPreferences()
        loadV261DirectorMonitorPreferences()
        loadV262RemoteDirectorPreferences()
        loadV263MultiCamPreferences()
        loadV264LiveCutPreferences()
        loadV265ProxyPreferences()
        if (isInterviewCamera()) {
            loadInterviewCameraPreferences()
        }
        applyIndependentCameraDefaultsIfNeeded()
        // The V242 master lock is a legacy Main Camera policy.  Leaving it
        // untouched for every existing experience is important; the three
        // additive profiles must retain their own fixed delivery defaults.
        if (!isAdditiveCameraPage()) {
            applyV242MasterQualityOnce()
        }
        recordingWarningsSeen.clear()

        reportId = newReportId()

        reportDisplayMode =
            intent.getStringExtra("develop_uganda_mode")
                ?.trim()
                ?.uppercase(Locale.US)
                ?.takeIf { it.isNotBlank() }
                ?: cameraExperienceDisplayName()

        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enforceImmersiveCameraWindow()

        buildUi()
        if (isInterviewCamera()) {
            showInterviewConsentIfNeeded()
        }
        v262EnsureRemoteServerState()
        v257UpdateHomeMotionLoop()
        showRecordingRecoveryNoticeIfNeeded()
        startAutoViewDescription()
        startShotAssistLoop()
        startDirectorLoop()
        requestPermissionsAndStart()
        v267ConsumePendingQuickPreset()
        uiHandler.post(tick)
    }


    private fun applyV242MasterQualityOnce() {
        val migration =
            duSharedPreferences(
                "develop_uganda_v242_quality_policy",
                Context.MODE_PRIVATE
            )

        // V245 MASTER QUALITY LOCK: re-apply master every launch.

        qualityModes.indexOf("MASTER UHD")
            .takeIf { it >= 0 }
            ?.let { qualityIndex = it }

        sceneModes.indexOf("CINEMA")
            .takeIf { it >= 0 }
            ?.let { sceneIndex = it }

        reportPresetLabels.indexOf("CINEMA")
            .takeIf { it >= 0 }
            ?.let { reportPresetIndex = it }

        autoDirectorEnabled = false
        saveReportCameraPreferences()

        migration.edit()
            .putBoolean("master_initialized", true)
            .apply()
    }

    internal fun v242ZoomSnapshot(): FloatArray {
        val state =
            camera?.cameraInfo?.zoomState?.value
                ?: return floatArrayOf(1f, 1f, 1f)

        return floatArrayOf(
            state.minZoomRatio,
            state.maxZoomRatio,
            state.zoomRatio
        )
    }

    internal fun v242SetZoomNormalized(value: Float) {
        val cam = camera ?: return
        val state =
            cam.cameraInfo.zoomState.value
                ?: return

        val n = value.coerceIn(0f, 1f)
        val span =
            (state.maxZoomRatio - state.minZoomRatio)
                .coerceAtLeast(0.001f)

        val ratio =
            (state.minZoomRatio + span * n)
                .coerceIn(
                    state.minZoomRatio,
                    state.maxZoomRatio
                )

        cam.cameraControl.setZoomRatio(ratio)

        if (::zoomSeek.isInitialized) {
            zoomSeek.progress =
                (n * 100f)
                    .roundToInt()
                    .coerceIn(0, 100)
        }
    }

    private fun verifyV242GalleryMaster(uri: Uri) {
        val requested =
            qualityModes.getOrNull(qualityIndex)
                ?: "AUTO"

        Thread {
            val retriever = MediaMetadataRetriever()

            try {
                retriever.setDataSource(
                    this@DevelopUgandaCameraActivity,
                    uri
                )

                val width =
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                    )?.toIntOrNull() ?: 0

                val height =
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                    )?.toIntOrNull() ?: 0

                val bitrate =
                    retriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_BITRATE
                    )?.toLongOrNull() ?: 0L

                val longSide = maxOf(width, height)
                val shortSide = minOf(width, height)

                val resolutionLabel =
                    when {
                        longSide >= 7600 && shortSide >= 4000 -> "8K"
                        longSide >= 3800 && shortSide >= 2000 -> "4K"
                        longSide >= 1900 && shortSide >= 1000 -> "1080"
                        longSide >= 1200 -> "HD"
                        else -> "${width}×${height}"
                    }

                val requested4k =
                    requested in setOf(
                        "MASTER UHD",
                        "UHD 60",
                        "MASTER HDR"
                    )

                val fallback =
                    requested4k &&
                        resolutionLabel != "4K" &&
                        resolutionLabel != "8K"

                val mbps =
                    if (bitrate > 0L) {
                        String.format(
                            Locale.US,
                            "%.1f",
                            bitrate / 1_000_000.0
                        )
                    } else {
                        "--"
                    }

                runOnUiThread {
                    statusView.text =
                        if (fallback) {
                            "MASTER WARNING • $resolutionLabel FALLBACK"
                        } else {
                            "MASTER VERIFIED • $resolutionLabel ✓"
                        }

                    statusView.setTextColor(
                        if (fallback) {
                            DevelopUgandaFivemods8Theme.warning
                        } else {
                            DevelopUgandaFivemods8Theme.accent
                        }
                    )

                    toast(
                        "MASTER FILE • ${width}×${height} • ${mbps} Mbps" +
                            if (fallback) {
                                " • device fell back below requested 4K"
                            } else {
                                " • direct CameraX master"
                            }
                    )
                }
            } catch (_: Exception) {
                runOnUiThread {
                    toast(
                        "Gallery master saved • resolution verification unavailable"
                    )
                }
            } finally {
                try {
                    retriever.release()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    internal fun v244PreviewBitmap(): android.graphics.Bitmap? =
        try {
            previewView.bitmap
        } catch (_: Exception) {
            null
        }

    internal fun v244CinemaStatusText(): String {
        val fps = requestedVideoFps().takeIf { it > 0 } ?: 30
        val shutter =
            if (v244ShutterAngle > 0) "${v244ShutterAngle}°" else "SH AUTO"
        val iso =
            if (v244Iso > 0) "ISO $v244Iso" else "ISO AUTO"
        val wb =
            v244WhiteBalanceLabels[
                v244WhiteBalanceIndex.coerceIn(
                    0,
                    v244WhiteBalanceLabels.lastIndex
                )
            ]
        val focus =
            if (focusLockActive) "AF LOCK" else "AF"

        return "MASTER LOCK • ${qualityDeckLabel()} • ${fps}FPS • $shutter • $iso • WB $wb • $focus • ${v246ImageEngineLabel()} • ${v247DetailLabel()} • TAGS ${v249TagStateCompact()} • EV ${v249ExposureLabel()}"}

    private fun v244ManualSensorSupported(): Boolean {
        val cam = camera ?: return false

        return try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val values =
                info.getCameraCharacteristic(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES
                ) ?: intArrayOf()

            values.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun v244SupportedIsoValues(): List<Int> {
        val cam = camera ?: return emptyList()

        return try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val range =
                info.getCameraCharacteristic(
                    CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
                ) ?: return emptyList()

            listOf(50, 100, 200, 400, 800, 1600, 3200, 6400)
                .filter { it in range.lower..range.upper }
                .ifEmpty { listOf(range.lower, range.upper).distinct() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun v244ApplyCamera2Controls(): String {
        val cam = camera ?: return "CAMERA NOT READY"

        if (recording != null) {
            return "STOP RECORDING BEFORE CHANGING MANUAL CAMERA CONTROL"
        }

        return try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val builder = CaptureRequestOptions.Builder()
            val manualRequested = v244ShutterAngle > 0 || v244Iso > 0
            val requestedFps = requestedVideoFps()
            val fpsRanges =
                info.getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES
                ) ?: emptyArray()
            val selectedFpsRange =
                if (isInterviewCamera()) {
                    f9InterviewFrameRateRange(cam.cameraInfo)
                } else if (requestedFps > 0) {
                    fpsRanges
                        .filter { it.upper >= requestedFps }
                        .minByOrNull { it.upper - requestedFps }
                        ?: fpsRanges.maxByOrNull { it.upper }
                } else {
                    null
                }
            val deliveredFps =
                selectedFpsRange?.upper
                    ?: requestedFps.takeIf { it > 0 }
                    ?: 30

            if (selectedFpsRange != null) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    selectedFpsRange
                )
                activeVideoFpsLabel =
                    if (selectedFpsRange.upper == requestedFps) {
                        "${selectedFpsRange.upper} FPS DEVICE"
                    } else {
                        "${selectedFpsRange.upper} FPS DEVICE (${requestedFps} REQUEST)"
                    }
            } else if (requestedFps <= 0) {
                activeVideoFpsLabel = "AUTO LOW-LIGHT FPS"
            } else {
                activeVideoFpsLabel = "AUTO FPS"
            }

            if (manualRequested && v244ManualSensorSupported()) {
                val isoRange =
                    info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE
                    )
                val exposureRange =
                    info.getCameraCharacteristic(
                        CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE
                    )

                if (isoRange == null || exposureRange == null) {
                    return "MANUAL SENSOR RANGE NOT EXPOSED BY THIS CAMERA"
                }

                val fps = deliveredFps
                val angle = v244ShutterAngle.takeIf { it > 0 } ?: 180
                val frameNs = 1_000_000_000L / fps.coerceAtLeast(1)
                val exposureNs =
                    (frameNs.toDouble() * angle.toDouble() / 360.0)
                        .toLong()
                        .coerceIn(exposureRange.lower, exposureRange.upper)
                val iso =
                    (v244Iso.takeIf { it > 0 } ?: 200)
                        .coerceIn(isoRange.lower, isoRange.upper)

                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_EXPOSURE_TIME,
                    exposureNs
                )
                builder.setCaptureRequestOption(
                    CaptureRequest.SENSOR_SENSITIVITY,
                    iso
                )
            } else {
                if (manualRequested && !v244ManualSensorSupported()) {
                    v244ShutterAngle = 0
                    v244Iso = 0
                }
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_ON
                )
                if (isInterviewCamera()) {
                    // Release a stale metering lock only when Interview returns to AUTO.
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AE_LOCK,
                        false
                    )
                }
            }

            val requestedWb =
                when (v244WhiteBalanceIndex) {
                    1 -> CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT
                    2 -> CaptureRequest.CONTROL_AWB_MODE_CLOUDY_DAYLIGHT
                    3 -> CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT
                    4 -> CaptureRequest.CONTROL_AWB_MODE_FLUORESCENT
                    else -> CaptureRequest.CONTROL_AWB_MODE_AUTO
                }

            val availableWb =
                info.getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES
                ) ?: intArrayOf(CaptureRequest.CONTROL_AWB_MODE_AUTO)

            val finalWb =
                if (availableWb.contains(requestedWb)) {
                    requestedWb
                } else {
                    v244WhiteBalanceIndex = 0
                    CaptureRequest.CONTROL_AWB_MODE_AUTO
                }

            builder.setCaptureRequestOption(
                CaptureRequest.CONTROL_AWB_MODE,
                finalWb
            )

            // Keep focus responsive while shooting.  This is hardware-gated
            // and deliberately leaves a manual focus pull in control.
            val afModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
                ) ?: intArrayOf()

            if (!v255FocusPullOn) {
                val continuousAf =
                    when {
                        afModes.contains(
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                        ) ->
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO

                        afModes.contains(
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                        ) ->
                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE

                        else ->
                            null
                    }

                if (continuousAf != null) {
                    builder.setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE,
                        continuousAf
                    )
                }
            }

            // Uganda mains power is 50 Hz.  Indoors, use the real 50 Hz
            // anti-banding mode when exposed; elsewhere leave the phone's
            // automatic anti-banding choice intact.
            val antiBandingModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics.CONTROL_AE_AVAILABLE_ANTIBANDING_MODES
                ) ?: intArrayOf()

            val antiBanding =
                when {
                    sceneModes.getOrNull(sceneIndex) == "INDOOR" &&
                        antiBandingModes.contains(
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ
                        ) ->
                            CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ

                    antiBandingModes.contains(
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO
                    ) ->
                        CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO

                    else ->
                        null
                }

            if (antiBanding != null) {
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                    antiBanding
                )
            }

            Camera2CameraControl.from(cam.cameraControl)
                .setCaptureRequestOptions(builder.build())

            refreshHud()

            if (manualRequested && !v244ManualSensorSupported()) {
                "MANUAL SENSOR NOT SUPPORTED • AUTO KEPT"
            } else {
                v244CinemaStatusText()
            }
        } catch (_: Exception) {
            "CAMERA2 CONTROL UNAVAILABLE • AUTO KEPT"
        }
    }

    internal fun v244CycleShutterAngle(): String {
        if (recording != null) {
            return "STOP RECORDING BEFORE CHANGING SHUTTER"
        }

        if (!v244ManualSensorSupported()) {
            v244ShutterAngle = 0
            return "MANUAL SHUTTER NOT SUPPORTED • AUTO KEPT"
        }

        val values = intArrayOf(0, 45, 90, 144, 180, 270, 360)
        val current = values.indexOf(v244ShutterAngle).takeIf { it >= 0 } ?: 0
        v244ShutterAngle = values[(current + 1) % values.size]
        return v244ApplyCamera2Controls()
    }

    internal fun v244CycleIso(): String {
        if (recording != null) {
            return "STOP RECORDING BEFORE CHANGING ISO"
        }

        if (!v244ManualSensorSupported()) {
            v244Iso = 0
            return "MANUAL ISO NOT SUPPORTED • AUTO KEPT"
        }

        val supported = v244SupportedIsoValues()
        if (supported.isEmpty()) {
            v244Iso = 0
            return "ISO RANGE NOT EXPOSED • AUTO KEPT"
        }

        val values = listOf(0) + supported
        val current = values.indexOf(v244Iso).takeIf { it >= 0 } ?: 0
        v244Iso = values[(current + 1) % values.size]
        return v244ApplyCamera2Controls()
    }

    internal fun v244CycleWhiteBalance(): String {
        v271RememberSettingChange("WHITE BALANCE ${v254WhiteBalanceLabel()}")
        if (recording != null) {
            return "STOP RECORDING BEFORE CHANGING WHITE BALANCE"
        }

        v244WhiteBalanceIndex =
            (v244WhiteBalanceIndex + 1) % v244WhiteBalanceLabels.size

        return v244ApplyCamera2Controls()
    }

    internal fun v244ResetCinemaControls(): String {
        if (recording != null) {
            return "STOP RECORDING BEFORE RESETTING CAMERA CONTROL"
        }

        v244ShutterAngle = 0
        v244Iso = 0
        v244WhiteBalanceIndex = 0
        return v244ApplyCamera2Controls()
    }

    internal fun v245MasterQualityStatus(): String {
        val name = qualityModes.getOrNull(qualityIndex) ?: "MASTER UHD"

        return if (
            name == "MASTER UHD" ||
            name == "UHD 60" ||
            name == "MASTER HDR"
        ) {
            "MASTER QUALITY LOCK • $name"
        } else {
            "MASTER QUALITY WARNING • $name"
        }
    }

    internal fun v246ImageEngineLabel(): String {
        return if (v246ImageEngineEnabled) {
            if (v246HighlightProtectEnabled) {
                "IMAGE HQ+"
            } else {
                "IMAGE HQ"
            }
        } else {
            "IMAGE AUTO"
        }
    }

    internal fun v246ToggleImageEngine(): String {
        if (recording != null) {
            return "STOP RECORDING BEFORE CHANGING IMAGE ENGINE"
        }

        v246ImageEngineEnabled =
            !v246ImageEngineEnabled

        return if (v246ImageEngineEnabled) {
            v246ApplyCinemaImageEngine()
        } else {
            try {
                camera?.let { cam ->
                    Camera2CameraControl
                        .from(cam.cameraControl)
                        .clearCaptureRequestOptions()
                }

                v246LastImageEngineStatus =
                    "PHONE DEFAULT ISP"

                "CINEMA IMAGE ENGINE OFF • PHONE DEFAULT ISP"
            } catch (_: Exception) {
                "IMAGE ENGINE RESET UNAVAILABLE"
            }
        }
    }

    internal fun v246ToggleHighlightProtect(): String {
        if (recording != null) {
            return "STOP RECORDING BEFORE CHANGING HIGHLIGHT PROTECT"
        }

        v246HighlightProtectEnabled =
            !v246HighlightProtectEnabled

        return v246ApplyCinemaImageEngine()
    }

    internal fun v246TenBitHdrStatus(): String {
        val cam =
            camera
                ?: return "10-BIT / HDR • CAMERA NOT READY"

        return try {
            val capabilities =
                Recorder.getVideoCapabilities(
                    cam.cameraInfo
                )

            val ranges =
                capabilities.supportedDynamicRanges

            when {
                ranges.contains(
                    DynamicRange.HLG_10_BIT
                ) ->
                    "10-BIT HLG • SUPPORTED"

                ranges.any {
                    it.bitDepth == 10
                } ->
                    "10-BIT HDR • SUPPORTED"

                else ->
                    "10-BIT HDR • NOT EXPOSED"
            }
        } catch (_: Exception) {
            "10-BIT HDR • CAPABILITY UNKNOWN"
        }
    }

    internal fun v246ApplyCinemaImageEngine(): String {
        val cam =
            camera
                ?: return "IMAGE ENGINE • CAMERA NOT READY"

        if (recording != null) {
            return "IMAGE ENGINE LOCKED WHILE RECORDING"
        }

        if (!v246ImageEngineEnabled) {
            return "CINEMA IMAGE ENGINE OFF"
        }

        return try {
            val info =
                Camera2CameraInfo.from(
                    cam.cameraInfo
                )

            val builder =
                CaptureRequestOptions.Builder()

            val noiseModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
                )
                    ?: intArrayOf()

            val edgeModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .EDGE_AVAILABLE_EDGE_MODES
                )
                    ?: intArrayOf()

            val aberrationModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES
                )
                    ?: intArrayOf()

            val hotPixelModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES
                )
                    ?: intArrayOf()

            val shadingModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .SHADING_AVAILABLE_MODES
                )
                    ?: intArrayOf()

            val toneMapModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .TONEMAP_AVAILABLE_TONE_MAP_MODES
                )
                    ?: intArrayOf()

            val colorCorrectionModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .COLOR_CORRECTION_AVAILABLE_MODES
                )
                    ?: intArrayOf()

            var detailLabel =
                "ISP"

            if (
                noiseModes.contains(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_HIGH_QUALITY
                )
                detailLabel =
                    "NR HQ"
            } else if (
                noiseModes.contains(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_FAST
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_FAST
                )
                detailLabel =
                    "NR FAST"
            }

            if (
                edgeModes.contains(
                    CaptureRequest
                        .EDGE_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    CaptureRequest
                        .EDGE_MODE_HIGH_QUALITY
                )
                detailLabel +=
                    " • EDGE HQ"
            }

            if (
                aberrationModes.contains(
                    CaptureRequest
                        .COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest
                        .COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest
                        .COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
            }

            if (
                hotPixelModes.contains(
                    CaptureRequest
                        .HOT_PIXEL_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.HOT_PIXEL_MODE,
                    CaptureRequest
                        .HOT_PIXEL_MODE_HIGH_QUALITY
                )
            }

            if (
                shadingModes.contains(
                    CaptureRequest
                        .SHADING_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.SHADING_MODE,
                    CaptureRequest
                        .SHADING_MODE_HIGH_QUALITY
                )
            }

            if (
                toneMapModes.contains(
                    CaptureRequest
                        .TONEMAP_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.TONEMAP_MODE,
                    CaptureRequest
                        .TONEMAP_MODE_HIGH_QUALITY
                )
                detailLabel +=
                    " • TONE HQ"
            }

            if (
                colorCorrectionModes.contains(
                    CaptureRequest
                        .COLOR_CORRECTION_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.COLOR_CORRECTION_MODE,
                    CaptureRequest
                        .COLOR_CORRECTION_MODE_HIGH_QUALITY
                )
                detailLabel +=
                    " • COLOR HQ"
            }

            if (
                v246HighlightProtectEnabled &&
                !isInterviewCamera() &&
                v244ShutterAngle == 0 &&
                v244Iso == 0
            ) {
                val state =
                    cam.cameraInfo.exposureState

                if (
                    state.isExposureCompensationSupported
                ) {
                    val range =
                        state.exposureCompensationRange

                    val target =
                        (-1)
                            .coerceIn(
                                range.lower,
                                range.upper
                            )

                    cam.cameraControl
                        .setExposureCompensationIndex(
                            target
                        )
                }
            }

            Camera2CameraControl
                .from(
                    cam.cameraControl
                )
                .addCaptureRequestOptions(
                    builder.build()
                )

            v246LastImageEngineStatus =
                detailLabel

            refreshHud()

            "CINEMA IMAGE ENGINE • $detailLabel • " +
                if (
                    v246HighlightProtectEnabled
                ) {
                    "HIGHLIGHT PROTECT"
                } else {
                    "NORMAL HIGHLIGHTS"
                }
        } catch (_: Exception) {
            v246LastImageEngineStatus =
                "PHONE ISP"

            "IMAGE ENGINE • PHONE ISP FALLBACK"
        }
    }

    internal fun v247DetailLabel(): String {
        return if (v247AdaptiveDetailEnabled) {
            when {
                v247IsLowLightScene() ->
                    "DETAIL LOW-LIGHT"

                cameraShakeScore >= 0.35f ->
                    "DETAIL MOTION"

                else ->
                    "DETAIL TEXTURE"
            }
        } else {
            "DETAIL AUTO"
        }
    }

    private fun v247IsLowLightScene(): Boolean {
        val scene =
            sceneModes.getOrNull(sceneIndex)
                ?: ""

        val quality =
            qualityModes.getOrNull(qualityIndex)
                ?: ""

        return (
            scene == "NIGHT" ||
            scene == "INDOOR" ||
            quality == "LOW LIGHT"
        )
    }

    private fun v247MotionClass(): String {
        return when {
            cameraShakeScore < 0.12f ->
                "STEADY"

            cameraShakeScore < 0.35f ->
                "HANDHELD"

            else ->
                "MOTION"
        }
    }

    internal fun v247MotionShutterGuide(): String {
        val fps =
            requestedVideoFps()
                .takeIf { it > 0 }
                ?: 30

        val motion =
            v247MotionClass()

        val angle =
            if (
                motion == "MOTION"
            ) {
                90
            } else {
                180
            }

        val denominator =
            (
                fps.toFloat() *
                    360f /
                    angle.toFloat()
                )
                .roundToInt()
                .coerceAtLeast(1)

        return if (
            v244ManualSensorSupported()
        ) {
            "MOTION GUIDE • $motion • $angle° ≈ 1/$denominator • SET WITH SHUTTER ANGLE"
        } else {
            "MOTION GUIDE • $motion • AUTO SHUTTER • MANUAL SENSOR NOT EXPOSED"
        }
    }

    internal fun v247ToggleAdaptiveDetail(): String {
        if (
            recording != null
        ) {
            return "STOP RECORDING BEFORE CHANGING ADAPTIVE DETAIL"
        }

        v247AdaptiveDetailEnabled =
            !v247AdaptiveDetailEnabled

        return if (
            v247AdaptiveDetailEnabled
        ) {
            v247ApplyAdaptiveDetail()
        } else {
            "ADAPTIVE DETAIL OFF • PHONE ISP / V246 BASE"
        }
    }

    internal fun v247ApplyAdaptiveDetail(): String {
        val cam =
            camera
                ?: return "ADAPTIVE DETAIL • CAMERA NOT READY"

        if (
            recording != null
        ) {
            return "ADAPTIVE DETAIL LOCKED WHILE RECORDING"
        }

        if (
            !v247AdaptiveDetailEnabled
        ) {
            return "ADAPTIVE DETAIL OFF"
        }

        return try {
            val info =
                Camera2CameraInfo.from(
                    cam.cameraInfo
                )

            val builder =
                CaptureRequestOptions.Builder()

            val noiseModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES
                )
                    ?: intArrayOf()

            val edgeModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .EDGE_AVAILABLE_EDGE_MODES
                )
                    ?: intArrayOf()

            val lowLight =
                v247IsLowLightScene()

            val motion =
                cameraShakeScore >=
                    0.35f

            var noiseLabel =
                "NR ISP"

            if (
                lowLight &&
                !motion &&
                noiseModes.contains(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE,
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_HIGH_QUALITY
                )

                noiseLabel =
                    "NR HQ"
            } else if (
                !lowLight &&
                !motion &&
                noiseModes.contains(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_MINIMAL
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE,
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_MINIMAL
                )

                noiseLabel =
                    "NR MIN"
            } else if (
                noiseModes.contains(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_FAST
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest
                        .NOISE_REDUCTION_MODE,
                    CaptureRequest
                        .NOISE_REDUCTION_MODE_FAST
                )

                noiseLabel =
                    "NR FAST"
            }

            var edgeLabel =
                "EDGE ISP"

            if (
                !lowLight &&
                !motion &&
                edgeModes.contains(
                    CaptureRequest
                        .EDGE_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    CaptureRequest
                        .EDGE_MODE_HIGH_QUALITY
                )

                edgeLabel =
                    "EDGE HQ"
            } else if (
                edgeModes.contains(
                    CaptureRequest
                        .EDGE_MODE_FAST
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.EDGE_MODE,
                    CaptureRequest
                        .EDGE_MODE_FAST
                )

                edgeLabel =
                    "EDGE NAT"
            }

            val aberrationModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES
                )
                    ?: intArrayOf()

            if (
                aberrationModes.contains(
                    CaptureRequest
                        .COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest
                        .COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest
                        .COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY
                )
            }

            val shadingModes =
                info.getCameraCharacteristic(
                    CameraCharacteristics
                        .SHADING_AVAILABLE_MODES
                )
                    ?: intArrayOf()

            if (
                shadingModes.contains(
                    CaptureRequest
                        .SHADING_MODE_HIGH_QUALITY
                )
            ) {
                builder.setCaptureRequestOption(
                    CaptureRequest.SHADING_MODE,
                    CaptureRequest
                        .SHADING_MODE_HIGH_QUALITY
                )
            }

            if (
                v246HighlightProtectEnabled &&
                !isInterviewCamera() &&
                v244ShutterAngle == 0 &&
                v244Iso == 0
            ) {
                val exposureState =
                    cam.cameraInfo
                        .exposureState

                if (
                    exposureState
                        .isExposureCompensationSupported
                ) {
                    val step =
                        exposureState
                            .exposureCompensationStep
                            .toFloat()
                            .coerceAtLeast(
                                0.01f
                            )

                    val targetIndex =
                        (
                            -0.33f /
                                step
                            )
                            .roundToInt()
                            .coerceIn(
                                exposureState
                                    .exposureCompensationRange
                                    .lower,
                                exposureState
                                    .exposureCompensationRange
                                    .upper
                            )

                    cam.cameraControl
                        .setExposureCompensationIndex(
                            targetIndex
                        )
                }
            }

            Camera2CameraControl
                .from(
                    cam.cameraControl
                )
                .addCaptureRequestOptions(
                    builder.build()
                )

            val modeLabel =
                when {
                    lowLight ->
                        "LOW-LIGHT TEXTURE"

                    motion ->
                        "MOTION DETAIL"

                    else ->
                        "DAYLIGHT TEXTURE"
                }

            v247LastDetailStatus =
                "$modeLabel • $noiseLabel • $edgeLabel"

            refreshHud()

            "ADAPTIVE DETAIL • $v247LastDetailStatus"
        } catch (_: Exception) {
            v247LastDetailStatus =
                "PHONE ISP FALLBACK"

            "ADAPTIVE DETAIL • PHONE ISP FALLBACK"
        }
    }

    internal fun v248ShutterTag(): String =
        if (v244ShutterAngle > 0) {
            "${v244ShutterAngle}°"
        } else {
            "AUTO"
        }

    internal fun v248IsoTag(): String =
        if (v244Iso > 0) {
            "ISO $v244Iso"
        } else {
            "AUTO"
        }

    internal fun v248WhiteBalanceTag(): String =
        v244WhiteBalanceLabels[
            v244WhiteBalanceIndex.coerceIn(
                0,
                v244WhiteBalanceLabels.lastIndex
            )
        ]

    internal fun v248ImageEngineTag(): String =
        if (v246ImageEngineEnabled) "ON" else "OFF"

    internal fun v248HighlightTag(): String =
        if (v246HighlightProtectEnabled) "ON" else "OFF"

    internal fun v248AdaptiveDetailTag(): String =
        if (v247AdaptiveDetailEnabled) "ON" else "OFF"

    internal fun v248HdrTag(): String {
        val status = v246TenBitHdrStatus().uppercase()
        return when {
            status.contains("SUPPORTED") && status.contains("10-BIT") -> "10-BIT"
            status.contains("NOT EXPOSED") -> "8-BIT"
            else -> "CHECK"
        }
    }

    internal fun v248MasterTag(): String {
        val quality = qualityModes.getOrNull(qualityIndex) ?: ""
        return if (
            quality == "MASTER UHD" ||
            quality == "UHD 60" ||
            quality == "MASTER HDR"
        ) {
            "LOCK"
        } else {
            "WARN"
        }
    }

    internal fun v248ReporterTagsTag(): String {
        return try {
            val snapshot = DevelopUgandaBrandMetadataStore.snapshot(this)
            if (snapshot.enabled.isEmpty()) {
                "OFF"
            } else {
                "ON ${snapshot.enabled.size}"
            }
        } catch (_: Exception) {
            "CHECK"
        }
    }

    internal fun v248OpenReporterTagSettings(): String {
        return try {
            startActivity(
                android.content.Intent(
                    this,
                    DevelopUgandaBrandMetadataActivity::class.java
                )
            )
            "OPENING TAG / METADATA SETTINGS"
        } catch (_: Exception) {
            "TAG SETTINGS UNAVAILABLE"
        }
    }

    internal fun v249TagStateCompact(): String {
        return try {
            val snapshot =
                DevelopUgandaBrandMetadataStore
                    .snapshot(this)

            if (
                snapshot.enabled.isEmpty()
            ) {
                "OFF"
            } else {
                "ON ${snapshot.enabled.size}"
            }
        } catch (_: Exception) {
            "CHECK"
        }
    }

    internal fun v249ToggleReporterTags(): String {
        if (
            recording != null
        ) {
            return "TAGS LOCKED WHILE RECORDING"
        }

        return try {
            val current =
                DevelopUgandaBrandMetadataStore
                    .snapshot(this)

            val prefs =
                duSharedPreferences(
                    "develop_uganda_v249_live_tags",
                    Context.MODE_PRIVATE
                )

            if (
                current.enabled.isNotEmpty()
            ) {
                prefs.edit()
                    .putStringSet(
                        "previous_tags",
                        current.enabled
                            .map {
                                it.key
                            }
                            .toSet()
                    )
                    .apply()

                DevelopUgandaBrandMetadataStore
                    .saveCustom(
                        context = this,
                        displayName =
                            current.displayName,
                        organization =
                            current.organization,
                        appCredit =
                            current.appCredit,
                        outputProfile =
                            current.outputProfile,
                        tags =
                            emptySet()
                    )

                refreshHud()

                "TAGS OFF • CLEAN MASTER • REC/TC MAY REMAIN"
            } else {
                val previous =
                    prefs.getStringSet(
                        "previous_tags",
                        emptySet()
                    )
                        ?: emptySet()

                val restored =
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .values()
                        .filter {
                            it.key in
                                previous
                        }
                        .toSet()
                        .ifEmpty {
                            setOf(
                                DevelopUgandaBrandMetadataStore.Tag.BRAND,
                                DevelopUgandaBrandMetadataStore.Tag.VERSION,
                                DevelopUgandaBrandMetadataStore.Tag.STORY,
                                DevelopUgandaBrandMetadataStore.Tag.DATE_TIME,
                                DevelopUgandaBrandMetadataStore.Tag.LOCATION,
                                DevelopUgandaBrandMetadataStore.Tag.CAMERA_MODE
                            )
                        }

                DevelopUgandaBrandMetadataStore
                    .saveCustom(
                        context = this,
                        displayName =
                            current.displayName,
                        organization =
                            current.organization,
                        appCredit =
                            current.appCredit,
                        outputProfile =
                            current.outputProfile,
                        tags =
                            restored
                    )

                refreshHud()

                "TAGS ON • ${restored.size}"
            }
        } catch (_: Exception) {
            "TAG CONTROL UNAVAILABLE"
        }
    }

    internal fun v249ExposureSnapshot(): FloatArray {
        val state =
            camera
                ?.cameraInfo
                ?.exposureState
                ?: return floatArrayOf(
                    -1f,
                    1f,
                    0f
                )

        if (
            !state.isExposureCompensationSupported
        ) {
            return floatArrayOf(
                0f,
                0f,
                0f
            )
        }

        val step =
            state.exposureCompensationStep
                .toFloat()
                .coerceAtLeast(
                    0.01f
                )

        return floatArrayOf(
            state.exposureCompensationRange.lower *
                step,
            state.exposureCompensationRange.upper *
                step,
            state.exposureCompensationIndex *
                step
        )
    }

    internal fun v249ExposureLabel(): String {
        val state =
            v249ExposureSnapshot()

        return String.format(
            java.util.Locale.US,
            "%+.1f",
            state[2]
        )
    }

    internal fun v249SetExposureEv(
        requestedEv: Float
    ) {
        val cam =
            camera
                ?: return

        val state =
            cam.cameraInfo
                .exposureState

        if (
            !state.isExposureCompensationSupported
        ) {
            return
        }

        val step =
            state.exposureCompensationStep
                .toFloat()
                .coerceAtLeast(
                    0.01f
                )

        val index =
            (
                requestedEv /
                    step
                )
                .roundToInt()
                .coerceIn(
                    state.exposureCompensationRange.lower,
                    state.exposureCompensationRange.upper
                )

        cam.cameraControl
            .setExposureCompensationIndex(
                index
            )
    }

    private fun enforceImmersiveCameraWindow() {
        WindowCompat.setDecorFitsSystemWindows(
            window,
            false
        )

        WindowInsetsControllerCompat(
            window,
            window.decorView
        ).apply {
            hide(
                WindowInsetsCompat.Type.systemBars()
            )

            systemBarsBehavior =
                WindowInsetsControllerCompat
                    .BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {
        super.onWindowFocusChanged(
            hasFocus
        )

        if (hasFocus) {
            enforceImmersiveCameraWindow()
        }
    }

    private fun buildUi() {
        root = FrameLayout(this).apply {
            setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
        }

        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE

            // V217: full-frame operator camera.
            // The camera image fills the phone behind all controls.
            // Gallery output remains the authoritative CameraX recording.
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        v261ApplyAnamorphicPreview()

        previewModeToneView =
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
            previewModeToneView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        shotAssistView =
            DevelopUgandaShotAssistView(
                this
            ).apply {
                setAssistMode(
                    shotAssistModeIndex
                )

                isClickable =
                    false

                isFocusable =
                    false
            }

        root.addView(
            shotAssistView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        v255MonitorView =
            DevelopUgandaV255ProMonitorView(
                this
            ).apply {
                setWaveformEnabled(v255WaveformOn)
                setFalseColorEnabled(v255FalseColorOn)
                setFalseColorStrength(v255FalseColorStrengthState / 100f)
                setRgbParadeEnabled(v261RgbParadeOn)
                setVectorscopeEnabled(v261VectorscopeOn)
                setSkinToneReferenceEnabled(v261SkinToneReferenceOn)
                setHighlightShadowAssistEnabled(v261HighlightShadowAssistOn)
                setFrameGuidesEnabled(v261FrameGuidesOn)
                setFrameGuideAspect(v261FrameGuideAspectState)
                setDirectorHudMode(v261DirectorHudModeState)
                isClickable = false
                isFocusable = false
            }

        root.addView(
            v255MonitorView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )


        if (isInterviewCamera()) {
            interviewObservationEngine =
                DevelopUgandaInterviewObservationEngine(
                    context = this,
                    onSnapshot = { snapshot ->
                        runOnUiThread {
                            interviewHudView?.setSnapshot(snapshot)
                        }
                    },
                    onSidecarSaved = { destination ->
                        runOnUiThread {
                            toast(destination)
                        }
                    }
                ).also { engine ->
                    engine.setAnalysisEnabled(interviewAnalysisEnabled)
                    engine.setBurnInEnabled(interviewBurnInEnabled)
                    engine.setRequireExplicitSubjectSelection(interviewTwoShotEnabled)
                }
            interviewAnnotatedExporter =
                DevelopUgandaInterviewAnnotatedExporter(this) { message ->
                    runOnUiThread { toast(message) }
                }
        }

        directorOverlayView =
            DevelopUgandaDirectorOverlayView(
                this,
                interviewObservationsEnabled = isInterviewCamera()
            ).apply {
                setDirectorEnabled(
                    directorEnabled || v251FaceExposurePriority || v259SmartExposureAssistOn || v255SubjectTracking != "OFF"
                )

                setTrackingMode(
                    if (isInterviewCamera()) {
                        if (interviewTwoShotEnabled) "OFF" else "PRIMARY FACE"
                    } else {
                        v255SubjectTracking
                    }
                )

                setPrimaryFaceListener { x, y, area ->
                    v277PrimaryFaceX = x
                    v277PrimaryFaceY = y
                    v277PrimaryFaceArea = area
                    v277LastFaceSeenMs = SystemClock.elapsedRealtime()
                    if (v255SubjectTracking != "OFF") {
                        v255ApplyTrackedFaceMetering(x, y, area)
                    } else {
                        v251ApplyPrimaryFaceMetering(x, y, area)
                    }
                }

                if (isInterviewCamera()) {
                    setInterviewObservationListener { observation ->
                        interviewObservationEngine?.observe(observation)
                    }
                    isClickable = true
                    isFocusable = true
                    setOnTouchListener { view, event ->
                        if (event.actionMasked == MotionEvent.ACTION_UP) {
                            val normalizedX =
                                (event.x / view.width.toFloat()).coerceIn(0f, 1f)
                            val normalizedY =
                                (event.y / view.height.toFloat()).coerceIn(0f, 1f)
                            setTrackingMode("TAP FACE")
                            setTrackingAnchor(normalizedX, normalizedY)
                            interviewObservationEngine?.armTappedSubjectSelection()
                            toast("Interview subject selected")
                        }
                        true
                    }
                } else {
                    isClickable = false
                    isFocusable = false
                }
            }

        root.addView(
            directorOverlayView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        if (isInterviewCamera()) {
            interviewHudView =
                DevelopUgandaInterviewHudView(this).apply {
                    isClickable = false
                    isFocusable = false
                    setAnalysisEnabled(interviewAnalysisEnabled)
                    setBurnInEnabled(interviewBurnInEnabled)
                    setTwoShotEnabled(interviewTwoShotEnabled)
                }
            root.addView(
                interviewHudView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }


        focusReticleView =
            TextView(this).apply {
                text =
                    "AF"

                textSize =
                    8.5f

                setTextColor(
                    DevelopUgandaFivemods8Theme.content
                )

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.DEFAULT_BOLD

                visibility =
                    View.GONE

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(12).toFloat()

                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(38)
                        )

                        setStroke(
                            dp(2),
                            DevelopUgandaFivemods8Theme.accent
                        )
                    }
            }

        root.addView(
            focusReticleView,
            FrameLayout.LayoutParams(
                dp(76),
                dp(76)
            )
        )

        horizonGuardView =
            TextView(this).apply {
                text =
                    "━━━━━━━━  LEVEL --  ━━━━━━━━"

                textSize =
                    8.6f

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.MONOSPACE

                setTextColor(
                    DevelopUgandaFivemods8Theme.contentDim
                )


                visibility =
                    View.VISIBLE
            }

        root.addView(
            horizonGuardView,
            FrameLayout.LayoutParams(
                dp(270),
                dp(34),
                Gravity.CENTER
            )
        )

        motionGuardView =
            TextView(this).apply {
                text =
                    "STEADYSHOT • --"

                textSize =
                    8.0f

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.MONOSPACE

                setTextColor(
                    DevelopUgandaFivemods8Theme.contentDim
                )

                setPadding(
                    dp(9),
                    dp(4),
                    dp(9),
                    dp(4)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(14).toFloat()

                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(66)
                        )

                        setStroke(
                            dp(1),
                            DevelopUgandaFivemods8Theme.outlineScrim(96)
                        )
                    }
            }

        val motionParams =
            FrameLayout.LayoutParams(
                dp(170),
                dp(30),
                Gravity.CENTER
            ).apply {
                topMargin =
                    dp(54)
            }

        root.addView(
            motionGuardView,
            motionParams
        )

        lightAdvisorView =
            TextView(this).apply {
                text =
                    "LIGHT • SENSOR --"

                textSize =
                    7.8f

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.MONOSPACE

                setTextColor(
                    DevelopUgandaFivemods8Theme.contentDim
                )

                setPadding(
                    dp(9),
                    dp(4),
                    dp(9),
                    dp(4)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(14).toFloat()

                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(66)
                        )

                        setStroke(
                            dp(1),
                            DevelopUgandaFivemods8Theme.outlineScrim(96)
                        )
                    }
            }

        val lightParams =
            FrameLayout.LayoutParams(
                dp(220),
                dp(30),
                Gravity.CENTER
            ).apply {
                topMargin =
                    dp(92)
            }

        root.addView(
            lightAdvisorView,
            lightParams
        )

        audioGuardView =
            TextView(this).apply {
                text =
                    "AUDIO • MIC READY"

                textSize =
                    7.8f

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.MONOSPACE

                setTextColor(
                    DevelopUgandaFivemods8Theme.contentDim
                )

                setPadding(
                    dp(9),
                    dp(4),
                    dp(9),
                    dp(4)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(14).toFloat()

                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(66)
                        )

                        setStroke(
                            dp(1),
                            DevelopUgandaFivemods8Theme.outlineScrim(96)
                        )
                    }
            }

        val audioGuardParams =
            FrameLayout.LayoutParams(
                dp(220),
                dp(30),
                Gravity.CENTER
            ).apply {
                topMargin =
                    dp(130)
            }

        root.addView(
            audioGuardView,
            audioGuardParams
        )

        thermalGuardView =
            TextView(this).apply {
                text =
                    "THERMAL • NORMAL"

                textSize =
                    7.8f

                gravity =
                    Gravity.CENTER

                typeface =
                    Typeface.MONOSPACE

                setTextColor(
                    DevelopUgandaFivemods8Theme.contentDim
                )

                setPadding(
                    dp(9),
                    dp(4),
                    dp(9),
                    dp(4)
                )

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(14).toFloat()

                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(66)
                        )

                        setStroke(
                            dp(1),
                            DevelopUgandaFivemods8Theme.outlineScrim(96)
                        )
                    }
            }

        val thermalGuardParams =
            FrameLayout.LayoutParams(
                dp(220),
                dp(30),
                Gravity.CENTER
            ).apply {
                topMargin =
                    dp(168)
            }

        root.addView(
            thermalGuardView,
            thermalGuardParams
        )

        // V187 PREVIEW HUD:
        // CameraX output graphics are no longer used for the PREVIEW target.
        // This panel is drawn in phone-screen coordinates, so develop.uganda
        // and every narration line remain inside the visible camera screen.
        previewNarrationPanel =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    dp(7),
                    dp(12),
                    dp(7)
                )

                background =
                    ColorDrawable(
                        DevelopUgandaFivemods8Theme.transparent
                    )
            }

        val previewBrandRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                gravity =
                    Gravity.CENTER_VERTICAL
            }

        previewBrandView =
            hud(
                DevelopUgandaBrandMetadataStore.previewTitle(
                    this,
                    "V259 PRO CAM"
                ),
                13.8f,
                DevelopUgandaFivemods8Theme.accent,
                bold = true
            )

        previewTagView =
            hud(
                "FIELD REPORT",
                7.1f,
                DevelopUgandaFivemods8Theme.content,
                bold = true
            ).apply {
                setPadding(
                    dp(7),
                    0,
                    0,
                    0
                )
            }

        previewBrandRow.addView(
            previewBrandView
        )

        previewBrandRow.addView(
            previewTagView
        )

        previewNarrationPanel.addView(
            previewBrandRow
        )

        cameraExperienceBannerView =
            hud(
                cameraExperienceDisplayName(),
                7.5f,
                cameraExperienceAccentColor(),
                bold = true
            ).apply {
                maxLines = 2
                setPadding(
                    dp(7),
                    dp(2),
                    dp(7),
                    dp(2)
                )
                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE
                        cornerRadius =
                            dp(9).toFloat()
                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(82)
                        )
                        setStroke(
                            dp(1),
                            cameraExperienceAccentColor()
                        )
                    }
            }

        previewNarrationPanel.addView(
            cameraExperienceBannerView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(27)
            )
        )

        autoViewDescriptionView =
            hud(
                "AUTO VIEW • analysing scene",
                7.2f,
                DevelopUgandaFivemods8Theme.accent,
                bold = true
            ).apply {
                maxLines = 1
                isSingleLine = true
            }

        previewNarrationPanel.addView(
            autoViewDescriptionView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(24)
            )
        )

        shotQualityGuardView =
            hud(
                "SHOT GUARD • READY",
                7.0f,
                DevelopUgandaFivemods8Theme.contentDim,
                bold = true
            ).apply {
                maxLines =
                    2
            }

        previewNarrationPanel.addView(
            shotQualityGuardView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(30)
            )
        )


        previewIdentityView =
            hud(
                "",
                6.1f,
                DevelopUgandaFivemods8Theme.content,
                bold = true
            )

        previewClockView =
            hud(
                "",
                6.0f,
                DevelopUgandaFivemods8Theme.record,
                bold = true
            )

        previewModeView =
            hud(
                "",
                5.8f,
                DevelopUgandaFivemods8Theme.accent,
                bold = true
            )

        previewPlaceView =
            hud(
                "",
                5.8f,
                DevelopUgandaFivemods8Theme.content
           ,
                bold = true
            )

        previewGpsView =
            hud(
                "",
                5.6f,
                DevelopUgandaFivemods8Theme.accent
           ,
                bold = true
            )

        previewNavView =
            hud(
                "",
                5.6f,
                DevelopUgandaFivemods8Theme.accent
           ,
                bold = true
            )

        previewSystemView =
            hud(
                "",
                5.4f,
                DevelopUgandaFivemods8Theme.accent
           ,
                bold = true
            )

        previewHealthView =
            hud(
                "",
                5.7f,
                DevelopUgandaFivemods8Theme.accent,
                bold = true
            )

        listOf(
            previewIdentityView,
            previewClockView,
            previewModeView,
            previewPlaceView,
            previewGpsView,
            previewNavView,
            previewSystemView,
            previewHealthView
        ).forEach {
            it.maxLines = 1
            it.isSingleLine = true

            previewNarrationPanel.addView(
                it,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(16)
                )
            )
        }

        val previewHudParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity =
                    Gravity.TOP

                // Keep the operator HUD below Android status icons/time.
                topMargin =
                    dp(44)

                leftMargin =
                    dp(10)

                rightMargin =
                    dp(10)
            }

        // FIVEMODS 12 owns the screen status inventory. The legacy narration
        // panel duplicated MODE, FORMAT, TC, BAT, FREE and AUDIO over the
        // preview, so it is deliberately not attached. Its backing values stay
        // alive for the established BRAND overlay/metadata path.
        previewNarrationPanel.layoutParams = previewHudParams

        applyAdaptiveReportPreviewTypography()

        guidesView = GuidesView(this)

        root.addView(
            guidesView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Internal state views. The visible HUD is rendered by OverlayEffect so
        // preview and saved video share the same telemetry layout.
        brandView = hud(
            "develop.uganda",
            10f,
            DevelopUgandaFivemods8Theme.accent,
            bold = true
        )
        statusView = hud(
            "STBY",
            8f,
            DevelopUgandaFivemods8Theme.record,
            bold = true
        )
        timecodeView = hud(
            "TC 00:00:00",
            7f,
            DevelopUgandaFivemods8Theme.content
        )
        formatView = hud(
            "SOCIAL FHD • DEVICE FPS",
            7f,
            DevelopUgandaFivemods8Theme.accent
        )
        locationView = hud(
            "GPS acquiring…",
            6f,
            DevelopUgandaFivemods8Theme.accent
        )
        weatherView = hud(
            "WX --",
            6f,
            DevelopUgandaFivemods8Theme.accent
        )
        systemView = hud(
            "MIC READY • NET -- • BAT -- • FREE --",
            6f,
            DevelopUgandaFivemods8Theme.accent
        )

        // ORBIT DECK: a custom floating control system. No large black panel.
        bottomDeck = LinearLayout(this).apply {
            tag = "v237_camera_deck"
            orientation = LinearLayout.VERTICAL
            setPadding(
                dp(10),
                dp(4),
                dp(10),
                dp(10)
            )
            setBackgroundColor(DevelopUgandaFivemods8Theme.transparent)
        }

        modeRow = row().apply {
            tag = "v237_camera_mode_row"
            gravity = Gravity.CENTER
        }

        sceneButton = deckButton(
            "SCENE ▾\n${sceneModes[sceneIndex]}",
            DevelopUgandaFivemods8Theme.accent
        )
        lookButton = deckButton(
            "LOOK ▾\n${lookModes[lookIndex]}",
            DevelopUgandaFivemods8Theme.accent
        )
        qualityButton = deckButton(
            "FORMAT ▾\n${qualityDeckLabel()}",
            DevelopUgandaFivemods8Theme.content
        )
        captureModeButton = deckButton(
            "CAPTURE ▾\n${captureModes[captureModeIndex]}",
            DevelopUgandaFivemods8Theme.accent
        )
        colorButton = deckButton(
            "COLOR ▾\n${v229ColorDeckLabel()}",
            DevelopUgandaFivemods8Theme.accent
        )

        sceneButton.tag = "v237_scene_button"
        lookButton.tag = "v237_look_button"
        qualityButton.tag = "v237_quality_button"
        captureModeButton.tag = "v237_capture_button"
        colorButton.tag = "v237_color_button"

        listOf(
            sceneButton,
            lookButton,
            qualityButton,
            captureModeButton,
            colorButton
        ).forEachIndexed { index, button ->
            modeRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(40),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart =
                            dp(5)
                    }
                }
            )
            if (false) {
                modeRow.addView(
                    space(dp(1)),
                    wrap(1, 1)
                )
            }
        }
        bottomDeck.addView(modeRow)

        identityRow = row().apply {
            tag = "v237_camera_identity_row"
            gravity = Gravity.CENTER
        }
        identityButton = deckButton(
            identityButtonText(),
            DevelopUgandaFivemods8Theme.accent
        )
        identityRow.addView(
            identityButton,
            LinearLayout.LayoutParams(
                0,
                dp(38),
                1f
            )
        )
        // V256 CLEAN CAM: do not attach the legacy full-width REPORT ID / CITIZEN row.
        // Reporter identity still exists in metadata, reporter settings and compact HUD.
        identityRow.visibility = View.GONE
        // V257 PRO CAM: identity remains available to the reporting system,
        // but the large bottom REPORT ID / CITIZEN pill no longer occupies
        // shooting space. The compact HUD report identity remains visible.
        identityRow.visibility = View.GONE

        // V185: FIELD REPORT camera has its own role-specific view and
        // settings controls. These are deliberately separate from LIVE STUDIO.
        reportToolsRow = row().apply {
            tag = "v237_camera_tools_row"
            gravity = Gravity.CENTER
        }

        viewModeButton = deckButton(
            "VIEW\nFULL SCREEN",
            DevelopUgandaFivemods8Theme.accent
        )

        settingsButton = deckButton(
            "SETTINGS\nREPORT",
            DevelopUgandaFivemods8Theme.accent
        )

        guidesButton = deckButton(
            "GUIDES ▾\nON",
            DevelopUgandaFivemods8Theme.accent
        )

        resetButton = deckButton(
            "RESET\nCAM",
            DevelopUgandaFivemods8Theme.accent
        )

        listOf(
            viewModeButton,
            settingsButton,
            guidesButton,
            resetButton
        ).forEachIndexed { index, button ->
            reportToolsRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(34),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart = dp(5)
                    }
                }
            )
        }

        bottomDeck.addView(reportToolsRow)

        // V188: compact operator controls for the FIELD REPORT role.
        reportAdvancedRow =
            row().apply {
                tag = "v237_camera_advanced_row"
                gravity =
                    Gravity.CENTER
            }

        autoUiButton =
            deckButton(
                "AUTO UI ▾\nON",
                DevelopUgandaFivemods8Theme.accent
            ).apply {
                isSelected = true
            }

        lockButton =
            deckButton(
                "LOCK ▾\nOFF",
                DevelopUgandaFivemods8Theme.accent
            )

        integrityButton =
            deckButton(
                "VERIFY ▾\nSHA-256",
                DevelopUgandaFivemods8Theme.accent
            ).apply {
                isSelected = true
            }

        capabilitiesButton =
            deckButton(
                "CAMERA\nCAPS",
                DevelopUgandaFivemods8Theme.content
            )

        cleanModeButton =
            deckButton(
                "CLEAN ▾\nOFF",
                DevelopUgandaFivemods8Theme.accent
            )

        assistButton =
            deckButton(
                "ASSIST ▾\n${shotAssistModeLabels[shotAssistModeIndex]}",
                DevelopUgandaFivemods8Theme.accent
            )


        listOf(
            autoUiButton,
            lockButton,
            cleanModeButton,
            assistButton
        ).forEachIndexed { index, button ->
            reportAdvancedRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(34),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart =
                            dp(7)
                    }
                }
            )
        }

        bottomDeck.addView(
            reportAdvancedRow
        )

        reportDisplayRow =
            row().apply {
                tag = "v237_camera_display_row"
                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }

        hudSizeButton =
            deckButton(
                "HUD SIZE ▾\n${reportHudLabels[reportHudSizeIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ).apply {
                isSelected =
                    true
            }

        listOf(
            integrityButton,
            capabilitiesButton,
            hudSizeButton
        ).forEachIndexed { index, button ->
            reportDisplayRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(34),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart =
                            dp(7)
                    }
                }
            )
        }

        bottomDeck.addView(
            reportDisplayRow
        )

        reportOutputRow =
            row().apply {
                tag = "v237_camera_output_row"
                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }

        hudContrastButton =
            deckButton(
                "HUD CONTRAST ▾\n${reportHudContrastLabels[reportHudContrastIndex]}",
                DevelopUgandaFivemods8Theme.accent
            ).apply {
                isSelected =
                    true
            }

        hudBackingButton =
            deckButton(
                "HUD BACKING ▾\n${reportHudBackingLabels[reportHudBackingIndex]}",
                DevelopUgandaFivemods8Theme.contentDim
            ).apply {
                isSelected =
                    reportHudBackingIndex !=
                        0
            }

        reportPresetButton =
            deckButton(
                "PRESET ▾\n${reportPresetLabels[reportPresetIndex]}",
                DevelopUgandaFivemods8Theme.contentDim
            ).apply {
                isSelected =
                    reportPresetIndex !=
                        0
            }

        autoDirectorButton =
            deckButton(
                "AUTO DIRECTOR ▾\n" +
                    if (
                        autoDirectorEnabled
                    ) {
                        "ON"
                    } else {
                        "OFF"
                    },
                DevelopUgandaFivemods8Theme.accent
            ).apply {
                isSelected =
                    autoDirectorEnabled
            }

        listOf(
            hudContrastButton,
            hudBackingButton,
            reportPresetButton,
            autoDirectorButton
        ).forEachIndexed { index, button ->
            reportOutputRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(34),
                    1f
                ).apply {
                    if (index > 0) {
                        marginStart =
                            dp(6)
                    }
                }
            )
        }

        bottomDeck.addView(
            reportOutputRow
        )

        reportDirectorRow =
            row().apply {
                tag = "v237_camera_director_row"
                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }

        directorButton =
            deckButton(
                "DIRECTOR ▾\n" +
                    if (
                        directorEnabled
                    ) {
                        "ON"
                    } else {
                        "OFF"
                    },
                DevelopUgandaFivemods8Theme.contentDim
            ).apply {
                isSelected =
                    directorEnabled
            }

        continuityButton =
            deckButton(
                "MATCH LAST\nSHOT",
                DevelopUgandaFivemods8Theme.accent
            ).apply {
                isSelected =
                    DevelopUgandaContinuityMemory.load(
                        this@DevelopUgandaCameraActivity,
                        cameraExperienceId
                    ) != null
            }

        healthButton =
            deckButton(
                "CAMERA\nHEALTH",
                DevelopUgandaFivemods8Theme.accent
            )

        brandMetadataButton =
            deckButton(
                "BRAND\nTAGS",
                DevelopUgandaFivemods8Theme.accent
            )

        listOf(
            directorButton,
            continuityButton,
            healthButton,
            brandMetadataButton
        ).forEachIndexed {
                index,
                button ->
            reportDirectorRow.addView(
                button,
                LinearLayout.LayoutParams(
                    0,
                    dp(34),
                    1f
                ).apply {
                    if (
                        index >
                            0
                    ) {
                        marginStart =
                            dp(7)
                    }
                }
            )
        }

        bottomDeck.addView(
            reportDirectorRow
        )

        settingsSummaryView = hud(
            reportSettingsSummary(),
            5.9f,
            DevelopUgandaFivemods8Theme.accent
        ).apply {
            tag = "v237_camera_settings_summary"
            visibility = View.GONE
            setPadding(
                dp(8),
                dp(5),
                dp(8),
                dp(5)
            )
            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.RECTANGLE
                    cornerRadius =
                        dp(10).toFloat()
                    setColor(
                        DevelopUgandaFivemods8Theme.surfaceScrim(196)
                    )
                    setStroke(
                        dp(1),
                        DevelopUgandaFivemods8Theme.contentScrim(128)
                    )
                }
        }

        // V256 CLEAN CAM: keep detailed settings data available, but do not attach
        // the old full-width settings-summary bar over the shooting view.
        settingsSummaryView.visibility = View.GONE

        zoomRow = row().apply { tag = "v237_camera_zoom_row" }
        zoomRow.addView(
            hud(
                "ZOOM",
                6.6f,
                DevelopUgandaFivemods8Theme.content,
                bold = true
            ),
            wrap(48, 28)
        )
        zoomSeek = SeekBar(this).apply {
            tag = "v238_zoom_seek"
            max = 100
        }
        zoomRow.addView(
            zoomSeek,
            LinearLayout.LayoutParams(
                0,
                dp(28),
                1f
            )
        )
        bottomDeck.addView(zoomRow)

        exposureRow = row().apply { tag = "v237_camera_exposure_row" }
        exposureRow.addView(
            hud(
                "EXP",
                6.6f,
                DevelopUgandaFivemods8Theme.content,
                bold = true
            ),
            wrap(48, 28)
        )
        exposureSeek = SeekBar(this).apply {
            tag = "v238_exposure_seek"
            max = 12
            progress = 6
        }
        exposureRow.addView(
            exposureSeek,
            LinearLayout.LayoutParams(
                0,
                dp(28),
                1f
            )
        )
        bottomDeck.addView(exposureRow)

        actionRow = row().apply {
            tag = "v237_camera_action_row"
            gravity = Gravity.CENTER
        }

        lensButton = deckButton(
            "LENS ▾\n${currentLensDeckLabel()}",
            DevelopUgandaFivemods8Theme.accent
        )
        torchButton = deckButton(
            "LIGHT ▾\nOFF",
            DevelopUgandaFivemods8Theme.content
        )
        recordButton = makeRecordButton()

        reportRecordState =
            ReportRecordStateView(this)

        val reportRecordArea =
            FrameLayout(this)

        reportRecordArea.addView(
            reportRecordState,
            FrameLayout.LayoutParams(
                dp(150),
                dp(62),
                Gravity.CENTER
            )
        )

        reportRecordArea.addView(
            recordButton,
            FrameLayout.LayoutParams(
                dp(132),
                dp(50),
                Gravity.CENTER
            )
        )

        actionRow.addView(
            lensButton,
            wrap(82, 46)
        )
        actionRow.addView(
            space(dp(8)),
            wrap(8, 1)
        )
        actionRow.addView(
            reportRecordArea,
            wrap(150, 62)
        )
        actionRow.addView(
            space(dp(8)),
            wrap(8, 1)
        )
        actionRow.addView(
            torchButton,
            wrap(82, 46)
        )

        bottomDeck.addView(actionRow)

        // FIVEMODS 9 moves Interview settings out of the bottom deck.

        // Only the established Main Camera and the three new pages receive
        // this additive strip.  The legacy independent cameras keep their
        // original deck exactly as before.
        if (cameraExperienceId == "V210_ALL_PRO" || isAdditiveCameraPage()) {
            bottomDeck.addView(
                DevelopUgandaCameraModeNavigator.create(
                    activity = this,
                    current = currentCameraPage(),
                    dp = ::dp,
                    canNavigate = { recording == null && !statusSegmentRestartPending },
                    releaseCamera = ::releaseCameraForModeSwitch
                )
            )
        }

        // Avoid the 3-argument FrameLayout.LayoutParams overload that
        // Kotlin/Android API 36 is resolving ambiguously in this project.
        val bottomDeckParams =
            FrameLayout.LayoutParams(
                -1, // MATCH_PARENT
                -2  // WRAP_CONTENT
            )
        bottomDeckParams.gravity =
            Gravity.BOTTOM
        root.addView(
            bottomDeck,
            bottomDeckParams
        )
        f9ConstrainInterviewControls()

        setContentView(root)

        DevelopUgandaLiveGradePanel.attach(
            activity = this,
            root = root,
            previewView = previewView,
            scopeProvider = { v229ColorScope() },
            hintProvider = { v229ColorHint() }
        )
        DevelopUgandaUnifiedControlDeck.attach(
            activity = this,
            root = root,
            scopeProvider = { v229ColorScope() },
            hintProvider = { v229ColorHint() },
            mode = DevelopUgandaUnifiedControlDeck.Mode.REPORT
        )
        DevelopUgandaFieldIntelligencePanel.attach(
            activity = this,
            root = root,
            previewView = previewView
        )
        DevelopUgandaAdaptiveFormatUi.attach(
            activity = this,
            root = root,
            role = DevelopUgandaAdaptiveFormatUi.Role.REPORT
        )
        DevelopUgandaOperatorExperience.attach(
            activity = this,
            root = root,
            role = DevelopUgandaOperatorExperience.Role.REPORT
        )
        DevelopUgandaV242QualityMatchPro.attach(
            activity = this,
            root = root
        )
        DevelopUgandaV271LiveCoach.attach(
            activity = this,
            root = root
        )
        DevelopUgandaV272FieldSoundContinuity.attach(
            activity = this,
            root = root
        )
        DevelopUgandaV273MotionShotControl.attach(
            activity = this,
            root = root
        )
        DevelopUgandaV244CinemaControl.attach(
            activity = this,
            root = root
        )
        uiHandler.postDelayed({ v246ApplyCinemaImageEngine() }, 850L)
        uiHandler.postDelayed({ v247ApplyAdaptiveDetail() }, 1250L)
        sceneButton.setOnTouchListener(
            DeckTouchListener(ACTION_SCENE)
        )

        lookButton.setOnTouchListener(
            DeckTouchListener(ACTION_LOOK)
        )

        qualityButton.setOnTouchListener(
            DeckTouchListener(ACTION_QUALITY)
        )

        captureModeButton.setOnTouchListener(
            DeckTouchListener(ACTION_CAPTURE_MODE)
        )

        colorButton.setOnTouchListener(
            DeckTouchListener(ACTION_COLOR_ENGINE)
        )

        identityButton.setOnTouchListener(
            DeckTouchListener(ACTION_IDENTITY)
        )

        viewModeButton.setOnTouchListener(
            DeckTouchListener(ACTION_VIEW_MODE)
        )

        settingsButton.setOnTouchListener(
            DeckTouchListener(ACTION_SETTINGS)
        )

        guidesButton.setOnTouchListener(
            DeckTouchListener(ACTION_GUIDES)
        )

        resetButton.setOnTouchListener(
            DeckTouchListener(ACTION_RESET)
        )

        autoUiButton.setOnTouchListener(
            DeckTouchListener(ACTION_AUTO_UI)
        )

        lockButton.setOnTouchListener(
            DeckTouchListener(ACTION_LOCK)
        )

        integrityButton.setOnTouchListener(
            DeckTouchListener(ACTION_INTEGRITY)
        )

        capabilitiesButton.setOnTouchListener(
            DeckTouchListener(ACTION_CAPABILITIES)
        )

        cleanModeButton.setOnTouchListener(
            DeckTouchListener(ACTION_CLEAN)
        )

        hudSizeButton.setOnTouchListener(
            DeckTouchListener(ACTION_HUD_SIZE)
        )

        hudContrastButton.setOnTouchListener(
            DeckTouchListener(ACTION_HUD_CONTRAST)
        )

        hudBackingButton.setOnTouchListener(
            DeckTouchListener(ACTION_HUD_BACKING)
        )

        reportPresetButton.setOnTouchListener(
            DeckTouchListener(ACTION_REPORT_PRESET)
        )

        autoDirectorButton.setOnTouchListener(
            DeckTouchListener(ACTION_AUTO_DIRECTOR)
        )

        assistButton.setOnTouchListener(
            DeckTouchListener(ACTION_SHOT_ASSIST)
        )

        directorButton.setOnTouchListener(
            DeckTouchListener(ACTION_DIRECTOR)
        )

        continuityButton.setOnTouchListener(
            DeckTouchListener(ACTION_CONTINUITY)
        )

        healthButton.setOnTouchListener(
            DeckTouchListener(ACTION_HEALTH)
        )

        brandMetadataButton.setOnTouchListener(
            DeckTouchListener(ACTION_BRAND_METADATA)
        )

        lensButton.setOnTouchListener(
            DeckTouchListener(ACTION_LENS)
        )

        torchButton.setOnTouchListener(
            DeckTouchListener(ACTION_TORCH)
        )

        recordButton.setOnTouchListener(
            DeckTouchListener(ACTION_RECORD)
        )

        zoomSeek.setOnSeekBarChangeListener(
            simpleSeek {
                applyZoom(it)
            }
        )

        exposureSeek.setOnSeekBarChangeListener(
            simpleSeek {
                val state = camera?.cameraInfo?.exposureState
                if (state != null && state.isExposureCompensationSupported) {
                    sceneExposureTarget =
                        state.exposureCompensationRange.lower + it
                    applyExposure(sceneExposureTarget)
                }
            }
        )

        previewView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    showReportOperatorControlsTemporarily()

                    gestureDownX =
                        event.x

                    gestureDownY =
                        event.y

                    gestureStartZoom =
                        camera
                            ?.cameraInfo
                            ?.zoomState
                            ?.value
                            ?.zoomRatio
                            ?: 1f

                    gestureStartExposure =
                        camera
                            ?.cameraInfo
                            ?.exposureState
                            ?.exposureCompensationIndex
                            ?: 0

                    gestureMoved =
                        false

                    focusLongPressTriggered =
                        false

                    showFocusReticle(
                        event.x,
                        event.y,
                        false
                    )

                    uiHandler.removeCallbacks(
                        focusLockRunnable
                    )

                    uiHandler.postDelayed(
                        focusLockRunnable,
                        650L
                    )

                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (
                        !operatorLocked
                    ) {
                        val dx =
                            event.x -
                                gestureDownX

                        val dy =
                            event.y -
                                gestureDownY

                        if (
                            maxOf(
                                abs(dx),
                                abs(dy)
                            ) >
                            dp(18)
                        ) {
                            gestureMoved =
                                true

                            uiHandler.removeCallbacks(
                                focusLockRunnable
                            )

                            if (
                                ::focusReticleView.isInitialized &&
                                !focusLockActive
                            ) {
                                focusReticleView.visibility =
                                    View.GONE
                            }
                        }

                        val cam =
                            camera

                        if (
                            cam != null &&
                            gestureMoved
                        ) {
                            if (
                                abs(dy) >
                                abs(dx)
                            ) {
                                val zoomState =
                                    cam.cameraInfo
                                        .zoomState
                                        .value

                                if (
                                    zoomState != null
                                ) {
                                    val span =
                                        (
                                            zoomState.maxZoomRatio -
                                                zoomState.minZoomRatio
                                            )
                                            .coerceAtLeast(
                                                0.1f
                                            )

                                    val ratio =
                                        (
                                            gestureStartZoom -
                                                (
                                                    dy /
                                                        previewView.height
                                                    ) *
                                                    span
                                            )
                                            .coerceIn(
                                                zoomState.minZoomRatio,
                                                zoomState.maxZoomRatio
                                            )

                                    cam.cameraControl
                                        .setZoomRatio(
                                            ratio
                                        )
                                }
                            } else {
                                val state =
                                    cam.cameraInfo
                                        .exposureState

                                val range =
                                    state.exposureCompensationRange

                                val total =
                                    (
                                        range.upper -
                                            range.lower
                                        )
                                        .coerceAtLeast(
                                            1
                                        )

                                val delta =
                                    (
                                        dx /
                                            previewView.width *
                                            total
                                        )
                                        .roundToInt()

                                val target =
                                    (
                                        gestureStartExposure +
                                            delta
                                        )
                                        .coerceIn(
                                            range.lower,
                                            range.upper
                                        )

                                cam.cameraControl
                                    .setExposureCompensationIndex(
                                        target
                                    )
                            }
                        }
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    uiHandler.removeCallbacks(
                        focusLockRunnable
                    )

                    if (
                        !gestureMoved &&
                        !focusLongPressTriggered
                    ) {
                        val now =
                            SystemClock.elapsedRealtime()

                        if (
                            now -
                                lastPreviewTapMs <
                            340L &&
                            !operatorLocked
                        ) {
                            if (recording == null) {
                                selectedCameraDeviceId =
                                    null

                                useFront =
                                    !useFront

                                lensButton.text =
                                    "LENS\n${currentLensDeckLabel()}"

                                bindCamera()
                                toast(
                                    "Lens switched"
                                )
                            } else {
                                focusPunchIn = !focusPunchIn
                                previewView.pivotX = event.x
                                previewView.pivotY = event.y
                                previewView.scaleX = if (focusPunchIn) 2f else 1f
                                previewView.scaleY = if (focusPunchIn) 2f else 1f
                                statusView.text =
                                    if (focusPunchIn) "FOCUS PUNCH 2X • SCREEN ONLY" else "FOCUS PUNCH OFF"
                                statusView.setTextColor(DevelopUgandaFivemods8Theme.accent)
                                toast(if (focusPunchIn) "FOCUS PUNCH 2X • SCREEN ONLY" else "FOCUS PUNCH OFF")
                            }
                        } else {
                            releaseFocusLockForTap()

                            // V277 spot meter follows an ordinary tap-to-focus point
                            // only when Spot Meter is enabled. Focus behavior is unchanged.
                            if (DevelopUgandaV277LightingExposure.bool(this, "spot_meter", true)) {
                                val w = previewView.width.coerceAtLeast(1).toFloat()
                                val h = previewView.height.coerceAtLeast(1).toFloat()
                                DevelopUgandaV277LightingExposure.setSpot(
                                    this,
                                    event.x / w,
                                    event.y / h
                                )
                            }

                            if (v255SubjectTracking == "TAP FACE") {
                                v255ArmTapTracking(event.x, event.y)
                            }

                            tapToFocus(
                                event.x,
                                event.y
                            )
                        }

                        lastPreviewTapMs =
                            now
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    uiHandler.removeCallbacks(
                        focusLockRunnable
                    )
                    true
                }

                else ->
                    true
            }
        }

        broadcastCameraChrome = DevelopUgandaBroadcastCameraChrome.attach(
            activity = this,
            root = root,
            preview = previewView,
            profile = DevelopUgandaModeProfiles.forPage(currentCameraPage()),
            onMark = ::addCurrentTakeMark,
        )
        if (cameraExperienceId == "V210_ALL_PRO" || isAdditiveCameraPage()) {
            f12CameraShell = DevelopUgandaFivemods12CameraShell.attach(
                activity = this,
                root = root,
                preview = previewView,
                page = currentCameraPage(),
                isRecording = { recording != null },
                recordingDurationMs = { v255RecordedDurationNs / 1_000_000L },
                audioAmplitude = { if (recording != null) audioAmplitude else null },
                restartSession = ::bindCamera,
            )
        }
    }

    private fun addCurrentTakeMark() {
        val take = crashSafeTake
        if (recording == null || take == null || crashSafeTakeRoot.isBlank()) {
            toast("START RECORDING BEFORE MARK")
            return
        }
        val elapsed = take.elapsedMs()
        val mark = runCatching {
            DevelopUgandaTakeMarks.add(
                this,
                crashSafeTakeRoot,
                currentCameraPage(),
                elapsed,
            )
        }.getOrElse {
            toast("MARK SIDECAR WRITE FAILED")
            return
        }
        recordButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        toast("MARK ${DevelopUgandaTakeMarks.format(mark.elapsedMs)}")
    }

    private fun enforceFullFramePreview() {
        halfPreviewMode =
  false

        previewView.layoutParams =
  FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
  ).apply {
      gravity =
          Gravity.TOP
  }

        previewView.scaleType =
  PreviewView.ScaleType.FILL_CENTER

        guidesView.layoutParams =
  FrameLayout.LayoutParams(
      ViewGroup.LayoutParams.MATCH_PARENT,
      ViewGroup.LayoutParams.MATCH_PARENT
  ).apply {
      gravity =
          Gravity.TOP
  }

        if (::viewModeButton.isInitialized) {
  viewModeButton.text =
      "VIEW\nFULL SCREEN"
        }

        if (::previewNarrationPanel.isInitialized) {
  val hudParams =
      previewNarrationPanel.layoutParams as
          FrameLayout.LayoutParams

  hudParams.topMargin =
      dp(44)

  previewNarrationPanel.layoutParams =
      hudParams

  previewBrandView.textSize =
      13.8f
        }
    }

    private fun togglePreviewMode() {
        enforceFullFramePreview()
        enforceImmersiveCameraWindow()

        toast(
  "Full-screen camera view"
        )
    }

    private fun showReportDetailedSettings() {
        detailedSettingsVisible =
            true

        settingsSummaryView.visibility =
            if (halfPreviewMode) {
                View.VISIBLE
            } else {
                View.GONE
            }

        refreshReportSettingsSummary()

        val message =
            buildString {
                append(reportSettingsSummary())
                append("\n\nRECORD OUTPUT\n")
                append("V271 OUTPUT • ${v271OutputMaster()}")
                append("\n")
                append(
                    when (v271OutputMaster()) {
                        DevelopUgandaV271LiveCoach.MODE_REPORTER -> "REPORTER MASTER • selected metadata/tags recorded"
                        DevelopUgandaV271LiveCoach.MODE_BRANDED -> "BRANDED MASTER • small develop.uganda mark recorded"
                        else -> "CLEAN MASTER • HUD / REC / TC stay screen-only"
                    }
                )
                append("\nV251 PRO ASSIST • ${v251ProAssistStatus()}")
                append("\n\nPREVIEW\n")
                append(
                    if (halfPreviewMode) {
                        "HALF — complete camera frame above operator controls"
                    } else {
                        "FULL — camera image fills the phone behind all controls"
                    }
                )
                append("\n\nROLE\n")
                append("FIELD REPORT settings remain independent from LIVE STUDIO settings.")
            }

        val dialog = AlertDialog.Builder(this)
            .setTitle("FIELD REPORT SETTINGS")
            .setMessage(message)
            .setPositiveButton("OK", null)
        if (isInterviewCamera()) {
            dialog.setNeutralButton("INTERVIEW SETTINGS") { _, _ ->
                f9ShowInterviewSettingsSheet()
            }
        }
        dialog.show()
    }

    private fun togglePreviewGuides() {
        previewGuidesEnabled =
            !previewGuidesEnabled

        guidesView.visibility =
            if (previewGuidesEnabled) {
                View.VISIBLE
            } else {
                View.INVISIBLE
            }

        guidesButton.text =
            "GUIDES ▾\n" +
                if (previewGuidesEnabled) {
                    "ON"
                } else {
                    "OFF"
                }

        saveReportCameraPreferences()

        toast(
            if (previewGuidesEnabled) {
                "Preview guides on"
            } else {
                "Preview guides off"
            }
        )
    }

    private fun resetReportCameraSettings() {
        if (recording != null) {
            toast("Stop recording before reset")
            return
        }

        sceneIndex = 0
        lookIndex = 0
        qualityIndex = 0
        captureModeIndex = 0
        sceneExposureTarget = 0

        sceneButton.text =
            "SCENE ▾\n${sceneModes[sceneIndex]}"

        lookButton.text =
            "LOOK ▾\n${lookModes[lookIndex]}"

        qualityButton.text =
            "FORMAT ▾\n${qualityDeckLabel()}"

        captureModeButton.text =
            "CAPTURE ▾\n${captureModes[captureModeIndex]}"

        exposureSeek.progress = 6

        camera?.cameraControl
            ?.setExposureCompensationIndex(0)

        refreshReportSettingsSummary()
        bindCamera()
        toast("Field Report camera reset")
    }

    private fun refreshReportSettingsSummary() {
        if (
            !::settingsSummaryView.isInitialized
        ) {
            return
        }

        settingsSummaryView.text =
            reportSettingsSummary()
    }

    private fun reportSettingsSummary(): String {
        val lens =
            if (useFront) {
                "FRONT"
            } else {
                "BACK"
            }

        val capture =
            captureModes[
                captureModeIndex
            ]

        return buildString {
            append("REPORT SETTINGS • ")
            append("VIEW ")
            append(
                if (halfPreviewMode) {
                    "HALF"
                } else {
                    "FULL"
                }
            )
            append(" • CAPTURE ")
            append(capture)
            append("\n")

            append("SCENE ")
            append(
                sceneModes[
                    sceneIndex
                ]
            )
            append(" • LOOK ")
            append(
                lookModes[
                    lookIndex
                ]
            )
            append(" • FORMAT ")
            append(
                qualityModes[
                    qualityIndex
                ]
            )
            append(" • COLOR ")
            append(
                v229ColorResolved().statusLabel()
            )
            append("\n")

            append("LENS ")
            append(lens)
            append(" • EXP ")
            append(sceneExposureTarget)
            append(" • ZOOM LIVE • TAP AF")
            append("\n")

            append("GUIDES ")
            append(
                if (previewGuidesEnabled) {
                    "ON"
                } else {
                    "OFF"
                }
            )
            append(" • AUTO UI ")
            append(
                if (autoHideOperatorUi) {
                    "ON"
                } else {
                    "OFF"
                }
            )
            append(" • VERIFY ")
            append(
                if (integrityEnabled) {
                    "SHA-256"
                } else {
                    "OFF"
                }
            )
            append(" • CLEAN ")
            append(
                if (cleanModeEnabled) {
                    "ON"
                } else {
                    "OFF"
                }
            )
            append(" • HUD ")
            append(
                reportHudLabels[
                    reportHudSizeIndex
                ]
            )
            append(" / ")
            append(
                reportHudContrastLabels[
                    reportHudContrastIndex
                ]
            )
            append(" / BACKING ")
            append(
                reportHudBackingLabels[
                    reportHudBackingIndex
                ]
            )
            append(" • PRESET ")
            append(
                reportPresetLabels[
                    reportPresetIndex
                ]
            )
            append(" • SETTINGS MEMORY ON")
            append(" • SWIPE ZOOM/EXP • DOUBLE TAP LENS")
            append(" • SOCIAL CAMERA 1080P/HIGH BITRATE • DEVICE AE/AF/AWB • TELEMETRY BURN-IN ON • GPS/GNSS • COMPASS • WEATHER • LEVEL • MIC • NET • BAT • STORAGE")
            append("\n")

            append("REPORT ID ")
            append(
                reportId.ifBlank {
                    "--"
                }
            )
            append(" • REPORTER ")
            append(
                reporterName.ifBlank {
                    "CITIZEN"
                }
            )
        }
    }

    private fun toggleReportAutoUi() {
        autoHideOperatorUi =
            !autoHideOperatorUi

        autoUiButton.text =
            "AUTO UI ▾\n" +
                if (autoHideOperatorUi) {
                    "ON"
                } else {
                    "OFF"
                }

        autoUiButton.isSelected =
            autoHideOperatorUi

        if (!autoHideOperatorUi) {
            uiHandler.removeCallbacks(
                autoHideRunnable
            )
            setReportOperatorControlsHidden(
                false
            )
        } else if (
            recording != null
        ) {
            showReportOperatorControlsTemporarily()
        }

        saveReportCameraPreferences()
        refreshReportSettingsSummary()
    }

    private fun toggleReportOperatorLock() {
        operatorLocked =
            !operatorLocked

        lockButton.text =
            "LOCK ▾\n" +
                if (operatorLocked) {
                    "ON"
                } else {
                    "OFF"
                }

        lockButton.isSelected =
            operatorLocked

        toast(
            if (operatorLocked) {
                "Report controls locked"
            } else {
                "Report controls unlocked"
            }
        )
    }

    private fun toggleReportIntegrity() {
        if (recording != null) {
            toast(
                "Change verification before recording"
            )
            return
        }

        integrityEnabled =
            !integrityEnabled

        integrityButton.text =
            "VERIFY ▾\n" +
                if (integrityEnabled) {
                    "SHA-256"
                } else {
                    "OFF"
                }

        integrityButton.isSelected =
            integrityEnabled

        saveReportCameraPreferences()
        refreshReportSettingsSummary()
    }

    private fun setReportOperatorControlsHidden(
        hidden: Boolean
    ) {
        if (isInterviewCamera()) {
            operatorControlsHidden = false
            listOf(
                modeRow, identityRow, reportToolsRow, reportAdvancedRow,
                reportDisplayRow, reportOutputRow, reportDirectorRow,
                zoomRow, exposureRow
            ).forEach { it.visibility = View.GONE }
            settingsSummaryView.visibility = View.GONE
            actionRow.visibility = View.VISIBLE
            return
        }
        operatorControlsHidden =
            hidden

        val visibility =
            if (hidden) {
                View.INVISIBLE
            } else {
                View.VISIBLE
            }

        listOf(
            modeRow,
            reportToolsRow,
            reportAdvancedRow,
            reportDisplayRow,
            reportOutputRow,
            reportDirectorRow,
            zoomRow,
            exposureRow
        ).forEach {
            it.visibility =
                visibility
        }

        // V256: legacy full-width identity + summary bars stay removed in every
        // camera state, including auto-hide restore and format changes.
        identityRow.visibility = View.GONE
        settingsSummaryView.visibility = View.GONE

        // Lens / record / light remain visible at all times.
        actionRow.visibility =
            View.VISIBLE
    }

    private fun showReportOperatorControlsTemporarily() {
        if (
            !autoHideOperatorUi
        ) {
            setReportOperatorControlsHidden(
                false
            )
            return
        }

        setReportOperatorControlsHidden(
            false
        )

        uiHandler.removeCallbacks(
            autoHideRunnable
        )

        if (
            recording != null
        ) {
            uiHandler.postDelayed(
                autoHideRunnable,
                3000L
            )
        }
    }

    private fun showCameraCapabilities() {
        val info =
            camera?.cameraInfo

        if (info == null) {
            toast(
                "Camera capabilities not ready"
            )
            return
        }

        val zoom =
            info.zoomState.value

        val exposure =
            info.exposureState

        val range =
            exposure.exposureCompensationRange

        val message =
            buildString {
                append("DEVICE CAMERA CAPABILITIES\n\n")
                append("LENS: ")
                append(
                    if (useFront) {
                        "FRONT"
                    } else {
                        "BACK"
                    }
                )
                append("\n")

                append("FLASH/TORCH: ")
                append(
                    if (info.hasFlashUnit()) {
                        "AVAILABLE"
                    } else {
                        "UNAVAILABLE"
                    }
                )
                append("\n")

                append("ZOOM: ")
                append(
                    String.format(
                        Locale.US,
                        "%.1fx – %.1fx",
                        zoom?.minZoomRatio ?: 1f,
                        zoom?.maxZoomRatio ?: 1f
                    )
                )
                append("\n")

                append("EXPOSURE COMP: ")
                append(range.lower)
                append(" to ")
                append(range.upper)
                append("\n")

                append("CURRENT EXP: ")
                append(
                    exposure.exposureCompensationIndex
                )
                append("\n")

                append("REQUESTED FORMAT: ")
                append(
                    qualityModes[
                        qualityIndex
                    ]
                )
                append("\n")

                append("CAPTURE: ")
                append(
                    captureModes[
                        captureModeIndex
                    ]
                )
                append("\n\n")

                append(
                    "FPS, HDR, codec, stabilization and manual sensor controls remain DEVICE unless the CameraX/Camera2 layer confirms direct control."
                )
            }

        AlertDialog.Builder(this)
            .setTitle(
                "FIELD CAMERA CAPABILITIES"
            )
            .setMessage(
                message
            )
            .setPositiveButton(
                "OK",
                null
            )
            .show()
    }


    private fun isSocialMediaCamera(): Boolean {
        return cameraExperienceId ==
            "V222_SOCIAL"
    }

    private fun exportAutomaticSocialMaster(
        inputUri: Uri,
        storyPackageId: String
    ) {
        if (
            !isSocialMediaCamera() ||
            automaticSocialExportActive
        ) {
            return
        }

        automaticSocialExportActive =
            true

        runOnUiThread {
            if (
                ::statusView.isInitialized
            ) {
                statusView.text =
                    "SM OPTIMIZING"

                statusView.setTextColor(
                    DevelopUgandaFivemods8Theme.accent
                )
            }

            toast(
                "SM Camera • creating social-ready copy"
            )
        }

        val exportDir =
            File(
                cacheDir,
                "v222_social_camera_exports"
            ).apply {
                mkdirs()
            }

        val temp =
            File(
                exportDir,
                "sm_${System.currentTimeMillis()}.mp4"
            )

        if (
            temp.exists()
        ) {
            temp.delete()
        }

        val sourceItem =
            MediaItem.Builder()
                .setUri(
                    inputUri
                )
                .build()

        val socialEffects =
            Effects(
                emptyList(),
                listOf(
                    Presentation.createForWidthAndHeight(
                        1080,
                        1920,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    ),

                    // Deliberately non-zero so Media3 cannot transmux the
                    // camera bitstream unchanged. This forces a real encode.
                    Brightness(
                        0.0001f
                    )
                )
            )

        val edited =
            EditedMediaItem.Builder(
                sourceItem
            )
                .setFrameRate(
                    30
                )
                .setEffects(
                    socialEffects
                )
                .build()

        val sequence =
            EditedMediaItemSequence.withAudioAndVideoFrom(
                listOf(
                    edited
                )
            )

        val compositionBuilder =
            Composition.Builder(
                listOf(
                    sequence
                )
            )

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {
            compositionBuilder.setHdrMode(
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            )
        }

        val composition =
            compositionBuilder
                .build()

        val videoSettings =
            VideoEncoderSettings.Builder()
                .setBitrate(
                    16_000_000
                )
                .setiFrameIntervalSeconds(
                    2f
                )
                .build()

        val audioSettings =
            AudioEncoderSettings.Builder()
                .setBitrate(
                    256_000
                )
                .build()

        val encoderFactory =
            DefaultEncoderFactory.Builder(
                this
            )
                .setRequestedVideoEncoderSettings(
                    videoSettings
                )
                .setRequestedAudioEncoderSettings(
                    audioSettings
                )
                .build()

        val listener =
            object :
                Transformer.Listener {

                override fun onCompleted(
                    composition: Composition,
                    result: ExportResult
                ) {
                    automaticSocialTransformer =
                        null

                    Thread {
                        try {
                            val verification =
                                verifyAutomaticSocialMaster(
                                    temp
                                )

                            val socialUri =
                                publishAutomaticSocialMaster(
                                    temp
                                )

                            DevelopUgandaStoryPackager.attachSocialMaster(
                                this@DevelopUgandaCameraActivity,
                                storyPackageId,
                                socialUri
                            )

                            temp.delete()

                            automaticSocialExportActive =
                                false

                            runOnUiThread {
                                if (
                                    ::statusView.isInitialized
                                ) {
                                    statusView.text =
                                        "SM READY"

                                    statusView.setTextColor(
                                        DevelopUgandaFivemods8Theme.accent
                                    )
                                }

                                toast(
                                    "SM READY • develop.uganda / SM Posts • $verification"
                                )
                            }
                        } catch (
                            e: Exception
                        ) {
                            automaticSocialExportActive =
                                false

                            temp.delete()

                            runOnUiThread {
                                if (
                                    ::statusView.isInitialized
                                ) {
                                    statusView.text =
                                        "SM EXPORT ERROR"

                                    statusView.setTextColor(
                                        DevelopUgandaFivemods8Theme.warning
                                    )
                                }

                                toast(
                                    "SM optimization failed • original video is safe"
                                )
                            }
                        }
                    }.start()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    automaticSocialTransformer =
                        null

                    automaticSocialExportActive =
                        false

                    temp.delete()

                    runOnUiThread {
                        if (
                            ::statusView.isInitialized
                        ) {
                            statusView.text =
                                "SM EXPORT ERROR"

                            statusView.setTextColor(
                                DevelopUgandaFivemods8Theme.warning
                            )
                        }

                        DevelopUgandaStoryPackager.markSocialMasterFailed(
                            this@DevelopUgandaCameraActivity,
                            storyPackageId,
                            "Media3 social export failed"
                        )

                        toast(
                            "SM optimization failed • original video is safe"
                        )
                    }
                }
            }

        automaticSocialTransformer =
            Transformer.Builder(
                this
            )
                .setEncoderFactory(
                    encoderFactory
                )
                .setVideoMimeType(
                    MimeTypes.VIDEO_H264
                )
                .setAudioMimeType(
                    MimeTypes.AUDIO_AAC
                )
                .addListener(
                    listener
                )
                .build()
                .also {
                    it.start(
                        composition,
                        temp.absolutePath
                    )
                }
    }

    /**
     * Delivery exports are deliberately separate from the CameraX master. A
     * failed platform copy can never replace, truncate or modify the original
     * gallery recording.
     */
    private fun exportAdditiveDelivery(
        inputUri: Uri,
        sourceName: String
    ) {
        val profile = additiveModeProfile() ?: return
        if (
            profile.deliveryProfile !in
                setOf(
                    DevelopUgandaDeliveryProfile.TIKTOK_UPLOAD,
                    DevelopUgandaDeliveryProfile.WHATSAPP_STATUS
                )
        ) {
            return
        }

        additiveDeliveryQueue.addLast(
            AdditiveDeliveryRequest(
                inputUri = inputUri,
                sourceName = sourceName,
                profile = profile
            )
        )
        startNextAdditiveDelivery()
    }

    private fun startNextAdditiveDelivery() {
        if (additiveDeliveryExportActive) return

        val request = additiveDeliveryQueue.pollFirst() ?: return
        additiveDeliveryExportActive = true

        val isStatus =
            request.profile.deliveryProfile ==
                DevelopUgandaDeliveryProfile.WHATSAPP_STATUS
        val friendlyName = if (isStatus) "STATUS" else "TIKTOK"
        val outputFolder =
            if (isStatus) "Movies/develop.uganda/Status Ready" else "Movies/develop.uganda/TikTok Ready"
        val outputPrefix =
            if (isStatus) "DEVELOP_UGANDA_STATUS_READY" else "DEVELOP_UGANDA_TIKTOK_READY"
        val audioBitrate = if (isStatus) 128_000 else 192_000

        runOnUiThread {
            if (::statusView.isInitialized) {
                statusView.text = "$friendlyName • PREPARING COPY"
                statusView.setTextColor(request.profile.chromeAccentColor)
            }
            toast("$friendlyName Cam • creating a separate upload copy")
        }

        val exportDir =
            File(cacheDir, "v281_${request.profile.page.name.lowercase(Locale.US)}_exports")
                .apply { mkdirs() }
        val temp =
            File(
                exportDir,
                "${request.profile.page.name.lowercase(Locale.US)}_${System.currentTimeMillis()}.mp4"
            )

        if (temp.exists()) temp.delete()

        val sourceItem =
            MediaItem.Builder()
                .setUri(request.inputUri)
                .build()

        val videoEffects =
            mutableListOf<Effect>(
                Presentation.createForWidthAndHeight(
                    1080,
                    1920,
                    Presentation.LAYOUT_SCALE_TO_FIT
                ),
                // A tiny non-zero grade forces a fresh encode. It is mild
                // enough to protect natural skin tones and avoids adding
                // artificial grain before a platform re-encode.
                Brightness(if (isStatus) 0.0001f else 0.012f)
            )

        if (!isStatus) {
            // TikTok's delivery copy gets a restrained, reversible-looking
            // perceptual lift before its own re-encode.  The CameraX master
            // remains untouched, and this deliberately avoids grain/noise.
            videoEffects += Contrast(0.045f)
            videoEffects +=
                HslAdjustment.Builder()
                    .adjustSaturation(3f)
                    .build()
        }

        // FIVEMODS 12 uses the already-established Media3 delivery effect
        // chain. CLEAN is a different URI and is never opened for writing.
        videoEffects += f12DeliveryIdentityOverlay(request.profile)

        val effects = Effects(emptyList(), videoEffects)

        val deliveryFrameRate =
            requireNotNull(request.profile.frameRate) {
                "${request.profile.page} has no delivery frame-rate profile"
            }
        val deliveryBitrate =
            requireNotNull(request.profile.targetBitrateBps) {
                "${request.profile.page} has no delivery bitrate profile"
            }

        val edited =
            EditedMediaItem.Builder(sourceItem)
                .setFrameRate(deliveryFrameRate)
                .setEffects(effects)
                .build()
        val sequence =
            EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
        val compositionBuilder = Composition.Builder(listOf(sequence))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            compositionBuilder.setHdrMode(
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            )
        }

        val videoSettings =
            VideoEncoderSettings.Builder()
                .setBitrate(deliveryBitrate)
                // The delivery copies deliberately request fixed-rate output.
                // A device may decline this in its codec path; any export
                // failure leaves the CameraX master untouched.
                .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                .setiFrameIntervalSeconds(2f)
                .build()
        val audioSettings =
            AudioEncoderSettings.Builder()
                .setBitrate(audioBitrate)
                .build()
        val encoderFactory =
            DefaultEncoderFactory.Builder(this)
                .setRequestedVideoEncoderSettings(videoSettings)
                .setRequestedAudioEncoderSettings(audioSettings)
                .build()

        val listener =
            object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    result: ExportResult
                ) {
                    additiveDeliveryTransformer = null

                    Thread {
                        try {
                            val verification =
                                verifyAdditiveDelivery(
                                    temp = temp,
                                    expectedBitrateBps = deliveryBitrate
                                )
                            val deliveryUri =
                                publishAdditiveDelivery(
                                    temp = temp,
                                    folder = outputFolder,
                                    filePrefix = outputPrefix
                                )

                            temp.delete()
                            additiveDeliveryExportActive = false

                            runOnUiThread {
                                if (::statusView.isInitialized) {
                                    statusView.text = "$friendlyName READY"
                                    statusView.setTextColor(request.profile.chromeAccentColor)
                                }
                                toast("$friendlyName READY • $verification")
                                if (isStatus) {
                                    offerWhatsAppStatusShare(deliveryUri)
                                }
                                startNextAdditiveDelivery()
                            }
                        } catch (_: Exception) {
                            additiveDeliveryExportActive = false
                            temp.delete()
                            runOnUiThread {
                                if (::statusView.isInitialized) {
                                    statusView.text = "$friendlyName EXPORT ERROR"
                                    statusView.setTextColor(DevelopUgandaFivemods8Theme.warning)
                                }
                                toast("$friendlyName copy failed • original video is safe")
                                startNextAdditiveDelivery()
                            }
                        }
                    }.start()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    additiveDeliveryTransformer = null
                    additiveDeliveryExportActive = false
                    temp.delete()
                    runOnUiThread {
                        if (::statusView.isInitialized) {
                            statusView.text = "$friendlyName EXPORT ERROR"
                            statusView.setTextColor(DevelopUgandaFivemods8Theme.warning)
                        }
                        toast("$friendlyName copy failed • original video is safe")
                        startNextAdditiveDelivery()
                    }
                }
            }

        additiveDeliveryTransformer =
            Transformer.Builder(this)
                .setEncoderFactory(encoderFactory)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(listener)
                .build()
                .also { transformer ->
                    transformer.start(compositionBuilder.build(), temp.absolutePath)
                }
    }

    private fun f12DeliveryIdentityOverlay(profile: DevelopUgandaModeProfile): Media3OverlayEffect {
        val identity = DevelopUgandaFivemods12Identity.forPage(profile.page)
        val label = SpannableString(identity.burnLabel).apply {
            setSpan(ForegroundColorSpan(Color.WHITE), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(BackgroundColorSpan(Color.argb(184, 0, 0, 0)), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan("sans-serif-medium"), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(28, true), 0, length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return Media3OverlayEffect(
            listOf(
                TextOverlay.createStaticTextOverlay(
                    label,
                    StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(-0.92f, -0.88f)
                        .setOverlayFrameAnchor(-1f, -1f)
                        .setScale(0.72f, 0.72f)
                        .build(),
                ),
            ),
        )
    }

    private fun verifyAdditiveDelivery(
        temp: File,
        expectedBitrateBps: Int
    ): String {
        if (!temp.exists() || temp.length() <= 0L) {
            error("Delivery output is empty")
        }

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(temp.absolutePath)
            val width =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toIntOrNull()
                    ?: 0
            val height =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toIntOrNull()
                    ?: 0
            val bitrate =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toLongOrNull()
                    ?: 0L

            if (width != 1080 || height != 1920) {
                error("Unexpected portrait dimensions ${width}×${height}")
            }

            val floor = (expectedBitrateBps * 0.45).toLong()
            val ceiling = (expectedBitrateBps * 1.55).toLong() + 1_000_000L
            if (bitrate !in floor..ceiling) {
                error("Unexpected delivery bitrate $bitrate")
            }

            String.format(
                Locale.US,
                "1080×1920 • %.1f Mbps • H.264/AAC",
                bitrate / 1_000_000.0
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun publishAdditiveDelivery(
        temp: File,
        folder: String,
        filePrefix: String
    ): Uri {
        val stamp =
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val identityPrefix = DevelopUgandaFivemods12Identity.prefixedStem(
            currentCameraPage(),
            filePrefix,
        )
        val values =
            ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "${identityPrefix}_${stamp}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, folder)
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }

        val uri =
            contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("Could not create delivery video")

        try {
            contentResolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(temp).use { input ->
                    input.copyTo(output, 1024 * 1024)
                }
            } ?: error("Could not write delivery video")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    },
                    null,
                    null
                )
            }
            return uri
        } catch (failure: Exception) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw failure
        }
    }

    private fun offerWhatsAppStatusShare(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("STATUS COPY READY")
            .setMessage("Share the separate Status-ready copy to WhatsApp. Your clean original remains in the gallery.")
            .setNegativeButton("LATER", null)
            .setPositiveButton("SHARE") { _, _ ->
                val share =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "video/mp4"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.whatsapp")
                    }

                try {
                    startActivity(share)
                } catch (_: Exception) {
                    startActivity(
                        Intent.createChooser(
                            share.setPackage(null),
                            "Share Status-ready video"
                        )
                    )
                }
            }
            .show()
    }

    private fun verifyAutomaticSocialMaster(
        temp: File
    ): String {
        if (
            !temp.exists() ||
            temp.length() <=
                0L
        ) {
            error(
                "SM output is empty"
            )
        }

        val retriever =
            MediaMetadataRetriever()

        return try {
            retriever.setDataSource(
                temp.absolutePath
            )

            val width =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )
                    ?.toIntOrNull()
                    ?: 0

            val height =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )
                    ?.toIntOrNull()
                    ?: 0

            val totalBitrate =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_BITRATE
                )
                    ?.toLongOrNull()
                    ?: 0L

            if (
                width !=
                    1080 ||
                height !=
                    1920
            ) {
                error(
                    "Unexpected SM dimensions ${width}×${height}"
                )
            }

            if (
                totalBitrate >
                    21_600_000L
            ) {
                error(
                    "SM bitrate remained too close to original"
                )
            }

            if (
                totalBitrate <
                    4_000_000L
            ) {
                error(
                    "SM bitrate unexpectedly low"
                )
            }

            val mbps =
                String.format(
                    Locale.US,
                    "%.1f",
                    totalBitrate /
                        1_000_000.0
                )

            "1080×1920 • $mbps Mbps"
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun publishAutomaticSocialMaster(
        temp: File
    ): Uri {
        val stamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(
                Date()
            )

        val name =
            DevelopUgandaFivemods12Identity.forPage(currentCameraPage()).code +
                "_DEVELOP_UGANDA_V222_SM_POST_" +
                stamp +
                ".mp4"

        val values =
            ContentValues().apply {
                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    name
                )

                put(
                    MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4"
                )

                if (
                    Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                ) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/develop.uganda/SM Posts"
                    )

                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        1
                    )
                }
            }

        val uri =
            contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error(
                "Could not create SM Posts video"
            )

        try {
            contentResolver.openOutputStream(
                uri,
                "w"
            )?.use { output ->
                FileInputStream(
                    temp
                ).use { input ->
                    input.copyTo(
                        output,
                        1024 * 1024
                    )
                }
            } ?: error(
                "Could not write SM Posts video"
            )

            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
            ) {
                contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(
                            MediaStore.Video.Media.IS_PENDING,
                            0
                        )
                    },
                    null,
                    null
                )
            }

            return uri
        } catch (
            e: Exception
        ) {
            try {
                contentResolver.delete(
                    uri,
                    null,
                    null
                )
            } catch (_: Exception) {
            }

            throw e
        }
    }


    private fun startAutoViewDescription() {
        if (
            ::autoViewLabeler.isInitialized
        ) {
            return
        }

        autoViewLabeler =
            ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(
                        0.62f
                    )
                    .build()
            )

        uiHandler.removeCallbacks(
            autoViewRunnable
        )

        uiHandler.postDelayed(
            autoViewRunnable,
            1700L
        )
    }

    private fun analyzeAutoViewFrame() {
        if (
            autoViewBusy ||
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

        autoViewBusy =
            true

        autoViewLabeler.process(
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

                autoViewSummary =
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
                    ::autoViewDescriptionView.isInitialized
                ) {
                    autoViewDescriptionView.text =
                        autoViewSummary
                }
            }
            .addOnFailureListener {
                autoViewSummary =
                    "AUTO VIEW • analysing scene"

                if (
                    ::autoViewDescriptionView.isInitialized
                ) {
                    autoViewDescriptionView.text =
                        autoViewSummary
                }
            }
            .addOnCompleteListener {
                autoViewBusy =
                    false
            }
    }

    private fun createIntegrityRecord(
        videoUri: Uri,
        finishedUtc: String
    ) {
        if (
            !integrityEnabled
        ) {
            return
        }

        val reportIdSnapshot =
            reportId
        val reporterSnapshot =
            reporterDisplayName()
        val storySnapshot =
            storyDisplayId()
        val startSnapshot =
            recordStartUtc
        val baseSnapshot =
            baseName
        val latSnapshot =
            lat
        val lonSnapshot =
            lon
        val altSnapshot =
            alt
        val accSnapshot =
            accuracy
        val qualitySnapshot =
            qualityModes[
                qualityIndex
            ]

        val modePurposeSnapshot =
            reportModePurposeLabel()

        val autoDirectorSnapshot =
            autoDirectorStateText()

        val cameraExperienceIdSnapshot =
            cameraExperienceId

        val cameraExperienceLabelSnapshot =
            cameraExperienceDisplayName()

        val sceneSnapshot =
            sceneModes[
                sceneIndex
            ]
        val lookSnapshot =
            lookModes[
                lookIndex
            ]

        val colorProfileSnapshot =
            v229ColorResolved().statusLabel()

        val thermalSnapshot =
            thermalStateLabel()

        val cameraPageSnapshot =
            currentCameraPage()

        Thread {
            try {
                val digest =
                    MessageDigest.getInstance(
                        "SHA-256"
                    )

                contentResolver
                    .openInputStream(
                        videoUri
                    )
                    ?.use { input ->
                        val buffer =
                            ByteArray(
                                256 * 1024
                            )

                        while (true) {
                            val read =
                                input.read(
                                    buffer
                                )

                            if (read <= 0) {
                                break
                            }

                            digest.update(
                                buffer,
                                0,
                                read
                            )
                        }
                    }
                    ?: error(
                        "Unable to read final video"
                    )

                val sha256 =
                    digest.digest()
                        .joinToString(
                            ""
                        ) {
                            "%02x".format(
                                Locale.US,
                                it
                            )
                        }

                val json =
                    JSONObject().apply {
                        put(
                            "schema",
                            "develop.uganda.report.integrity.v1"
                        )
                        put(
                            "app_version",
                            "V259 PRO CAM"
                        )
                        put(
                            "camera_engine",
                            "V217 FULL FRAME HUD + V221 SHOT FINDER"
                        )
                        put(
                            "camera_modules",
                            "V204,V205,V206,V207,V208,V209,V210,V211,V212,V213,V214,V215,V216,V217"
                        )
                        put(
                            "report_id",
                            reportIdSnapshot
                        )
                        put(
                            "reporter",
                            reporterSnapshot
                        )
                        put(
                            "story_id",
                            storySnapshot
                        )
                        put(
                            "filename",
                            "$baseSnapshot.mp4"
                        )
                        put(
                            "record_start_utc",
                            startSnapshot
                        )
                        put(
                            "record_end_utc",
                            finishedUtc
                        )
                        put(
                            "sha256",
                            sha256
                        )
                        put(
                            "quality_mode",
                            qualitySnapshot
                        )
                        put(
                            "mode_purpose",
                            modePurposeSnapshot
                        )
                        put(
                            "auto_director",
                            autoDirectorSnapshot
                        )
                        put(
                            "camera_experience_id",
                            cameraExperienceIdSnapshot
                        )
                        put(
                            "camera_experience_label",
                            cameraExperienceLabelSnapshot
                        )
                        put(
                            "scene",
                            sceneSnapshot
                        )
                        put(
                            "look",
                            lookSnapshot
                        )
                        put(
                            "color_profile",
                            colorProfileSnapshot
                        )
                        put(
                            "latitude",
                            latSnapshot
                        )
                        put(
                            "longitude",
                            lonSnapshot
                        )
                        put(
                            "altitude_m",
                            altSnapshot
                        )
                        put(
                            "gps_accuracy_m",
                            accSnapshot
                        )
                        put(
                            "thermal_status",
                            thermalSnapshot
                        )
                        put(
                            "note",
                            "SHA-256 identifies this finalized file; it is not a digital signature or proof of authorship."
                        )
                    }

                saveIntegrityJson(
                    "${baseSnapshot}_INTEGRITY.json",
                    json.toString(
                        2
                    )
                )

                DevelopUgandaSealRegistry.register(
                    this,
                    videoUri,
                    baseSnapshot,
                    sha256,
                    finishedUtc,
                    cameraPageSnapshot,
                    "${baseSnapshot}_INTEGRITY.json"
                )

                runOnUiThread {
                    toast(
                        "Integrity SHA-256 saved"
                    )
                }
            } catch (e: Exception) {
                runOnUiThread {
                    toast(
                        "Integrity record failed"
                    )
                }
            }
        }.start()
    }

    private fun saveIntegrityJson(
        fileName: String,
        content: String
    ) {
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.Q
        ) {
            val values =
                ContentValues().apply {
                    put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        fileName
                    )
                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/json"
                    )
                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        "Download/develop.uganda/Integrity"
                    )
                }

            val uri =
                contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                )
                    ?: error(
                        "Could not create integrity file"
                    )

            contentResolver
                .openOutputStream(
                    uri
                )
                ?.use {
                    it.write(
                        content.toByteArray(
                            Charsets.UTF_8
                        )
                    )
                }
                ?: error(
                    "Could not write integrity file"
                )
        } else {
            val dir =
                File(
                    getExternalFilesDir(
                        Environment.DIRECTORY_DOCUMENTS
                    ),
                    "develop.uganda/Integrity"
                )

            dir.mkdirs()

            FileOutputStream(
                File(
                    dir,
                    fileName
                )
            ).use {
                it.write(
                    content.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }
        }
    }

    private fun applyAdaptiveReportPreviewTypography() {
        val widthDp =
            resources.configuration
                .screenWidthDp

        val scale =
            reportHudScales[
                reportHudSizeIndex
            ]

        val brandSize =
            when {
                widthDp <= 360 ->
                    11.5f

                widthDp <= 420 ->
                    12.8f

                else ->
                    13.8f
            } *
                scale

        val rowSize =
            when {
                widthDp <= 360 ->
                    5.2f

                widthDp <= 420 ->
                    5.7f

                else ->
                    6.1f
            } *
                scale

        previewBrandView.textSize =
            brandSize

        previewTagView.textSize =
            rowSize +
                0.8f

        listOf(
            previewIdentityView,
            previewClockView,
            previewModeView,
            previewPlaceView,
            previewGpsView,
            previewNavView,
            previewSystemView,
            previewHealthView
        ).forEach {
            it.textSize =
                rowSize
        }
    }

    private fun toggleReportCleanMode() {
        cleanModeEnabled =
            !cleanModeEnabled

        cleanModeButton.text =
            "CLEAN ▾\n" +
                if (cleanModeEnabled) {
                    "ON"
                } else {
                    "OFF"
                }

        cleanModeButton.isSelected =
            cleanModeEnabled

        if (
            cleanModeEnabled
        ) {
            listOf(
                modeRow,
                identityRow,
                reportToolsRow,
                reportAdvancedRow,
                reportDisplayRow,
                reportOutputRow,
                reportDirectorRow,
                zoomRow,
                exposureRow
            ).forEach {
                it.visibility =
                    View.INVISIBLE
            }

            settingsSummaryView.visibility =
                View.GONE

            previewIdentityView.visibility =
                View.GONE

            previewModeView.visibility =
                View.GONE

            previewPlaceView.visibility =
                View.GONE

            previewGpsView.visibility =
                View.GONE

            previewNavView.visibility =
                View.GONE

            previewSystemView.visibility =
                View.GONE

            previewClockView.visibility =
                View.VISIBLE

            previewHealthView.visibility =
                View.VISIBLE

            actionRow.visibility =
                View.VISIBLE
        } else {
            setReportOperatorControlsHidden(
                false
            )

            listOf(
                previewIdentityView,
                previewClockView,
                previewModeView,
                previewPlaceView,
                previewGpsView,
                previewNavView,
                previewSystemView,
                previewHealthView
            ).forEach {
                it.visibility =
                    View.VISIBLE
            }
        }

        toast(
            if (cleanModeEnabled) {
                "Clean operator mode"
            } else {
                "Full report controls"
            }
        )
    }

    private data class FieldPreflight(
        val critical: List<String>,
        val warnings: List<String>,
        val ready: List<String>
    )

    private fun shotQualityWarnings(): List<String> {
        val warnings =
            mutableListOf<String>()

        ambientLux?.let {
            if (
                it <
                    25f
            ) {
                warnings.add(
                    "TOO DARK"
                )
            }
        }

        if (
            v254AudioClipWarningEnabled() &&
            recording !=
                null &&
            audioPeakAmplitude >=
                0.90
        ) {
            warnings.add(
                "MIC CLIPPING"
            )
        }


        if (
            v255AudioHeadroomEnabled() &&
            recording != null &&
            v255AudioPeakDbfs() > v255AudioHeadroomTargetDb().toDouble() &&
            audioPeakAmplitude < 0.90
        ) {
            warnings.add(
                "AUDIO HEADROOM"
            )
        }

        if (
            cameraShakeScore >
                22f
        ) {
            warnings.add(
                "SHAKE HIGH"
            )
        }

        phoneRollDeg?.let {
            if (
                kotlin.math.abs(
                    it
                ) >
                    3.0f
            ) {
                warnings.add(
                    "HORIZON OFF"
                )
            }
        }

        if (
            isThermalSevereOrWorse()
        ) {
            warnings.add(
                "THERMAL RISK"
            )
        }

        freeStorageGb()?.let {
            if (
                v254StorageGuardEnabled() &&
                it <= v254StorageThresholdGb().toLong()
            ) {
                warnings.add(
                    "STORAGE LOW"
                )
            }
        }

        val gpsAge =
            if (
                lastGpsUpdateMs >
                    0L
            ) {
                System.currentTimeMillis() -
                    lastGpsUpdateMs
            } else {
                Long.MAX_VALUE
            }

        if (
            accuracy ==
                null ||
            (
                accuracy ?: 999f
                ) >
                50f ||
            gpsAge >
                10_000L
        ) {
            warnings.add(
                "GPS WEAK"
            )
        }

        if (
            focusAttempted &&
            focusSuccessful ==
                false
        ) {
            warnings.add(
                "SUBJECT NOT FOCUSED"
            )
        }

        return warnings.distinct()
    }

    private fun updateShotQualityGuard() {
        if (
            !::shotQualityGuardView.isInitialized
        ) {
            return
        }

        val warnings =
            shotQualityWarnings()

        if (recording != null) {
            recordingWarningsSeen.addAll(
                warnings
            )
        }

        shotQualityGuardView.text =
            if (
                warnings.isEmpty()
            ) {
                "SHOT GUARD • READY"
            } else {
                "SHOT GUARD • " +
                    warnings.joinToString(
                        " • "
                    )
            }

        shotQualityGuardView.setTextColor(
            if (
                warnings.isEmpty()
            ) {
                DevelopUgandaFivemods8Theme.contentDim
            } else if (
                warnings.any {
                    it ==
                        "MIC CLIPPING" ||
                    it ==
                        "THERMAL RISK"
                }
            ) {
                DevelopUgandaFivemods8Theme.accent
            } else {
                DevelopUgandaFivemods8Theme.accent
            }
        )
    }

    private fun fieldPreflight(): FieldPreflight {
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
            freeStorageGb()

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

            v260StorageReservationOn &&
            storage <= v260StorageReserveGbState.toLong() ->
                    critical.add(
                        "PROJECT STORAGE RESERVE ${v260StorageReserveGbState}GB • FREE ${storage}GB"
                    )

            v254StorageGuardEnabled() &&
            storage <= maxOf(v254StorageThresholdGb(), v260StorageReserveGbState + 2).toLong() ->
                    warnings.add(
                        "STORAGE LOW ${storage}GB • RESERVE ${v260StorageReserveGbState}GB"
                    )

            else ->
                ready.add(
                    "SPACE ${storage}GB"
                )
        }

        val battery =
            batteryPct()

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

        val v260ThermalWarnAt =
            when (v260ThermalStrategyState) {
                "ENDURANCE" -> PowerManager.THERMAL_STATUS_MODERATE
                "QUALITY FIRST" -> PowerManager.THERMAL_STATUS_CRITICAL
                else -> PowerManager.THERMAL_STATUS_SEVERE
            }

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
            thermalStatus >=
                PowerManager.THERMAL_STATUS_CRITICAL
        ) {
            critical.add(
                "THERMAL ${thermalStateLabel()}"
            )
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            thermalStatus >= v260ThermalWarnAt
        ) {
            warnings.add(
                "THERMAL ${thermalStateLabel()} • ${v260ThermalStrategyState}"
            )
        } else {
            ready.add(
                "THERMAL ${thermalStateLabel()} • ${v260ThermalStrategyState}"
            )
        }

        val micReady =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) ==
                PackageManager.PERMISSION_GRANTED

        if (
            micReady
        ) {
            ready.add(
                "MIC OK"
            )
        } else {
            warnings.add(
                "MIC OFF"
            )
        }

        val gpsAge =
            if (
                lastGpsUpdateMs >
                    0L
            ) {
                System.currentTimeMillis() -
                    lastGpsUpdateMs
            } else {
                Long.MAX_VALUE
            }

        if (
            accuracy !=
                null &&
            (
                accuracy ?: 999f
                ) <=
                50f &&
            gpsAge <=
                10_000L
        ) {
            ready.add(
                "GPS FIX"
            )
        } else {
            warnings.add(
                "GPS WEAK"
            )
        }

        if (
            isSocialMediaCamera()
        ) {
            if (
                automaticSocialExportActive
            ) {
                warnings.add(
                    "SOCIAL EXPORT BUSY"
                )
            } else {
                ready.add(
                    "SOCIAL MASTER READY"
                )
            }
        }

        shotQualityWarnings()
            .filterNot {
                it in
                    setOf(
                        "STORAGE LOW",
                        "GPS WEAK",
                        "THERMAL RISK"
                    )
            }
            .forEach {
                if (
                    it !in
                        warnings
                ) {
                    warnings.add(
                        it
                    )
                }
            }

        return FieldPreflight(
            critical =
                critical.distinct(),
            warnings =
                warnings.distinct(),
            ready =
                ready.distinct()
        )
    }

    private fun runFieldPreflightBeforeRecording(): Boolean {
        if (!v254PreflightEnabled() && !v259SmartRecordCheckOn) {
            return true
        }

        if (
            preflightApprovedOnce
        ) {
            preflightApprovedOnce =
                false
            return true
        }

        val result =
            fieldPreflight()

        val readyText =
            result.ready.joinToString(
                " • "
            )

        if (
            result.critical.isEmpty() &&
            result.warnings.isEmpty()
        ) {
            if (
                ::shotQualityGuardView.isInitialized
            ) {
                shotQualityGuardView.text =
                    "PREFLIGHT GOOD • $readyText"
                shotQualityGuardView.setTextColor(
                    DevelopUgandaFivemods8Theme.contentDim
                )
            }

            return true
        }

        if (
            result.critical.isNotEmpty()
        ) {
            AlertDialog.Builder(
                this
            )
                .setTitle(
                    "RECORDING PREFLIGHT • BLOCKED"
                )
                .setMessage(
                    buildString {
                        append(
                            "Critical condition:\n"
                        )

                        result.critical.forEach {
                            append(
                                "• $it\n"
                            )
                        }

                        if (
                            result.warnings.isNotEmpty()
                        ) {
                            append(
                                "\nWarnings:\n"
                            )

                            result.warnings.forEach {
                                append(
                                    "• $it\n"
                                )
                            }
                        }

                        append(
                            "\nReady: $readyText"
                        )
                    }
                )
                .setNegativeButton(
                    "CANCEL",
                    null
                )
                .setPositiveButton(
                    "EMERGENCY RECORD"
                ) { _, _ ->
                    preflightApprovedOnce = true
                    toggleRecording()
                }
                .show()

            return false
        }

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "FIELD RECORDING PREFLIGHT"
            )
            .setMessage(
                buildString {
                    append(
                        "Warnings:\n"
                    )

                    result.warnings.forEach {
                        append(
                            "• $it\n"
                        )
                    }

                    append(
                        "\nReady: $readyText"
                    )
                }
            )
            .setNegativeButton(
                "CANCEL",
                null
            )
            .setPositiveButton(
                "RECORD ANYWAY"
            ) { _, _ ->
                preflightApprovedOnce =
                    true
                toggleRecording()
            }
            .show()

        return false
    }

    private fun recordingJournalPrefs() =
        duSharedPreferences(
            "develop_uganda_recording_recovery",
            Context.MODE_PRIVATE
        )

    private fun markRecordingJournalStarted() {
        if (!v254RecoveryJournalEnabled()) return
        recordingJournalPrefs()
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
                "base_name",
                baseName
            )
            .putString(
                "report_id",
                reportId
            )
            .putString(
                "camera",
                cameraExperienceShortLabel()
            )
            .putString(
                "started_utc",
                recordStartUtc
            )
            .apply()
    }

    private fun markRecordingJournalFinished(
        hadError: Boolean
    ) {
        if (!v254RecoveryJournalEnabled()) return
        recordingJournalPrefs()
            .edit()
            .putBoolean(
                "active",
                false
            )
            .putBoolean(
                "incomplete",
                hadError
            )
            .putString(
                "last_result",
                if (
                    hadError
                ) {
                    "INCOMPLETE"
                } else {
                    "FINALIZED"
                }
            )
            .apply()
    }

    private fun showRecordingRecoveryNoticeIfNeeded() {
        val recoveredSegments = runCatching {
            DevelopUgandaCrashSafeTake.recoverOnLaunch(this)
        }.getOrElse { failure ->
            DevelopUgandaV276RecordingSafety.addEvent(
                this,
                "SEGMENT RECOVERY READ FAILED • ${failure.javaClass.simpleName}",
            )
            emptyList()
        }
        val recoveredAudio = runCatching {
            DevelopUgandaV272FieldSoundContinuity.recoverSafetyFragments(this)
        }.getOrElse { failure ->
            listOf("SAFETY AUDIO RECOVERY FAILED • ${failure.javaClass.simpleName}")
        }
        if (!v254RecoveryJournalEnabled() && recoveredSegments.isEmpty() && recoveredAudio.isEmpty()) return
        val prefs =
            recordingJournalPrefs()

        val active =
            prefs.getBoolean(
                "active",
                false
            )

        val incomplete =
            prefs.getBoolean(
                "incomplete",
                false
            )

        if (
            !active &&
            !incomplete &&
            recoveredSegments.isEmpty() &&
            recoveredAudio.isEmpty()
        ) {
            return
        }

        val name =
            prefs.getString(
                "base_name",
                "--"
            ) ?: "--"

        val cameraName =
            prefs.getString(
                "camera",
                "--"
            ) ?: "--"

        val started =
            prefs.getString(
                "started_utc",
                "--"
            ) ?: "--"

        val itemFound =
            name != "--" &&
            try {
                contentResolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    arrayOf(
                        MediaStore.Video.Media._ID
                    ),
                    "${MediaStore.Video.Media.DISPLAY_NAME} LIKE ?",
                    arrayOf(
                        "$name%"
                    ),
                    "${MediaStore.Video.Media.DATE_ADDED} DESC"
                )?.use {
                    it.moveToFirst()
                } ?: false
            } catch (_: Exception) {
                false
            }

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "RECOVERED / INCOMPLETE CLIP"
            )
            .setMessage(
                buildString {
                    append(
                        "The previous recording session did not reach a clean completion record.\n\n"
                    )
                    append(
                        "CAMERA • $cameraName\n"
                    )
                    append(
                        "START • $started\n"
                    )
                    append(
                        "FILE • $name.mp4\n\n"
                    )
                    append(
                        if (
                            itemFound
                        ) {
                            "A Gallery item with this name exists. Inspect/play it before relying on it. develop.uganda does not claim a damaged MP4 was repaired."
                        } else {
                            "No matching Gallery item was confirmed. The recovery journal preserves the recording identity so the loss is not silent."
                        }
                    )
                    if (recoveredSegments.isNotEmpty()) {
                        append("\n\nSEGMENT RECOVERY\n")
                        recoveredSegments.forEach { result ->
                            append("• ").append(result.message).append('\n')
                        }
                        append("Raw fragments are preserved. Recovery only creates separate copies.")
                    }
                    if (recoveredAudio.isNotEmpty()) {
                        append("\n\nSAFETY AUDIO RECOVERY\n")
                        recoveredAudio.forEach { result -> append("• ").append(result).append('\n') }
                    }
                }
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
            .show()
    }

    private fun loadV251ProAssistPreferences() {
        val prefs =
            duSharedPreferences(
                "develop_uganda_v251_pro_assist",
                Context.MODE_PRIVATE
            )

        v251CleanMasterRecord =
            DevelopUgandaV271LiveCoach.outputMode(this) ==
                DevelopUgandaV271LiveCoach.MODE_CLEAN

        v251FaceExposurePriority =
            prefs.getBoolean(
                "face_exposure",
                true
            )

        v251ProAssistEnabled =
            prefs.getBoolean(
                "pro_assist",
                false
            )

        if (v251ProAssistEnabled) {
            shotAssistModeIndex =
                DevelopUgandaShotAssistView.MODE_BOTH
        }
    }

    private fun saveV251ProAssistPreferences() {
        duSharedPreferences(
            "develop_uganda_v251_pro_assist",
            Context.MODE_PRIVATE
        )
            .edit()
            .putBoolean(
                "clean_master",
                v251CleanMasterRecord
            )
            .putBoolean(
                "face_exposure",
                v251FaceExposurePriority
            )
            .putBoolean(
                "pro_assist",
                v251ProAssistEnabled
            )
            .apply()
    }

    internal fun v251ToggleProAssist(): String {
        v251ProAssistEnabled =
            !v251ProAssistEnabled

        shotAssistModeIndex =
            if (v251ProAssistEnabled) {
                DevelopUgandaShotAssistView.MODE_BOTH
            } else {
                DevelopUgandaShotAssistView.MODE_OFF
            }

        if (::shotAssistView.isInitialized) {
            shotAssistView.setAssistMode(
                shotAssistModeIndex
            )
        }

        if (::assistButton.isInitialized) {
            assistButton.text =
                "ASSIST ▾\n${shotAssistModeLabels[shotAssistModeIndex]}"

            assistButton.isSelected =
                v251ProAssistEnabled
        }

        saveV251ProAssistPreferences()

        val message =
            if (v251ProAssistEnabled) {
                "V251 PRO ASSIST ON • PEAK + ZEBRA • SCREEN ONLY"
            } else {
                "V251 PRO ASSIST OFF"
            }

        toast(message)
        return message
    }

    internal fun v251ToggleCleanMaster(): String {
        val next =
            if (DevelopUgandaV271LiveCoach.outputMode(this) == DevelopUgandaV271LiveCoach.MODE_CLEAN) {
                DevelopUgandaV271LiveCoach.MODE_REPORTER
            } else {
                DevelopUgandaV271LiveCoach.MODE_CLEAN
            }
        return v271SetOutputMaster(next)
    }

    internal fun v251ToggleFaceExposurePriority(): String {
        v251FaceExposurePriority =
            !v251FaceExposurePriority

        saveV251ProAssistPreferences()

        val message =
            if (v251FaceExposurePriority) {
                "FACE EXPOSURE PRIORITY ON • AE/AWB FOLLOWS PRIMARY FACE"
            } else {
                "FACE EXPOSURE PRIORITY OFF"
            }

        toast(message)
        return message
    }

    internal fun v251ProAssistStatus(): String {
        return buildString {
            append(
                if (v251FaceExposurePriority) {
                    "FACE AE"
                } else {
                    "FACE OFF"
                }
            )
            append(" • ")
            append(
                if (v251CleanMasterRecord) {
                    "CLEAN MASTER"
                } else {
                    "BURN-IN"
                }
            )
            append(" • ")
            append(
                if (v251ProAssistEnabled) {
                    "PEAK+ZEBRA"
                } else {
                    "ASSIST READY"
                }
            )
        }
    }

    private fun v271RememberSettingChange(label: String) {
        if (recording != null) return
        val snapshot = JSONObject()
            .put("scene", sceneIndex)
            .put("look", lookIndex)
            .put("quality", qualityIndex)
            .put("capture", captureModeIndex)
            .put("shutter", v244ShutterAngle)
            .put("iso", v244Iso)
            .put("wb", v244WhiteBalanceIndex)
            .toString()
        v271SettingHistory.addFirst(label to snapshot)
        while (v271SettingHistory.size > 8) v271SettingHistory.removeLast()
    }

    internal fun v271UndoLastChange(): String {
        if (recording != null) return "STOP RECORDING BEFORE UNDO"
        val item = v271SettingHistory.pollFirst() ?: return "NO CAMERA CHANGE TO UNDO"
        return try {
            val json = JSONObject(item.second)
            sceneIndex = json.optInt("scene", sceneIndex).coerceIn(0, sceneModes.lastIndex)
            lookIndex = json.optInt("look", lookIndex).coerceIn(0, lookModes.lastIndex)
            qualityIndex = json.optInt("quality", qualityIndex).coerceIn(0, qualityModes.lastIndex)
            captureModeIndex = json.optInt("capture", captureModeIndex).coerceIn(0, captureModes.lastIndex)
            v244ShutterAngle = json.optInt("shutter", v244ShutterAngle)
            v244Iso = json.optInt("iso", v244Iso)
            v244WhiteBalanceIndex = json.optInt("wb", v244WhiteBalanceIndex).coerceIn(0, v244WhiteBalanceLabels.lastIndex)
            saveReportCameraPreferences()
            applyScenePreset()
            refreshHud()
            bindCamera()
            val message = "UNDO • ${item.first}"
            toast(message)
            message
        } catch (_: Exception) {
            "UNDO DATA INVALID"
        }
    }

    internal fun v271SettingsHistory(): String =
        if (v271SettingHistory.isEmpty()) "NO CHANGES YET" else v271SettingHistory.joinToString("\n") { "• ${it.first}" }

    internal fun v271SessionSummary(): String {
        val durationSec = (v255RecordedDurationNs / 1_000_000_000L).coerceAtLeast(0L)
        val mb = (v255RecordedBytes / 1_000_000L).coerceAtLeast(0L)
        return "LAST CLIP • ${durationSec}s • ${mb}MB\nSTORY CLIPS • ${DevelopUgandaV269StoryDeskStore.clipCount(this)}\n${v254FieldCapacity()}\nOUTPUT • ${v271OutputMaster()}"
    }

    internal fun v271OutputMaster(): String =
        DevelopUgandaV271LiveCoach.outputMode(this)

    internal fun v271SetOutputMaster(mode: String): String {
        val message = DevelopUgandaV271LiveCoach.setOutputMode(this, mode)
        v251CleanMasterRecord = DevelopUgandaV271LiveCoach.outputMode(this) == DevelopUgandaV271LiveCoach.MODE_CLEAN
        saveV251ProAssistPreferences()
        refreshHud()
        toast(
            when (DevelopUgandaV271LiveCoach.outputMode(this)) {
                DevelopUgandaV271LiveCoach.MODE_REPORTER -> "REPORTER MASTER • SELECTED REPORT / PROJECT / TIMECODE TAGS MAY BE BURNED IN"
                DevelopUgandaV271LiveCoach.MODE_BRANDED -> "BRANDED MASTER • SMALL develop.uganda BRAND BURN-IN"
                else -> "CLEAN MASTER • HUD STAYS SCREEN-ONLY"
            }
        )
        return message
    }

    internal fun v271ToggleCoachSetting(key: String, defaultValue: Boolean, label: String): String {
        val message = DevelopUgandaV271LiveCoach.toggle(this, key, defaultValue, label)
        toast(message)
        return message
    }

    internal fun v271CoachSetting(key: String, defaultValue: Boolean): Boolean =
        DevelopUgandaV271LiveCoach.bool(this, key, defaultValue)

    internal fun v271SetInterfaceLevel(value: String): String {
        val message = DevelopUgandaV271LiveCoach.setInterfaceLevel(this, value)
        toast(message)
        return message
    }

    internal fun v271InterfaceLevel(): String = DevelopUgandaV271LiveCoach.interfaceLevel(this)

    internal fun v271VerificationSummary(): String = DevelopUgandaV271LiveCoach.verificationSummary(this)

    internal fun v271ReadyStrip(): String {
        val battery = batteryPct() ?: -1
        val free = freeStorageGb() ?: -1L
        val thermalBad = thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        val audioOk = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        var score = 100
        if (battery in 0..10) score -= 20
        if (free in 0..4) score -= 30
        if (thermalBad) score -= 35
        if (!audioOk) score -= 15
        val wb = v254WhiteBalanceLabel()
        return "READY ${score.coerceIn(0,100)}% • AUDIO ${if (audioOk) "OK" else "CHECK"} • ${v253QualityLabel()} • WB $wb • ${estimatedRecordingTimeText()}"
    }

    internal fun v271CoachSnapshot(): DevelopUgandaV271LiveCoach.CoachSnapshot {
        val free = freeStorageGb()
        if (free != null && free <= 4L) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("STORAGE", "STORAGE LOW • ${free}GB LEFT", "OPEN HEALTH")
        }
        val battery = batteryPct()
        if (battery != null && battery <= 10) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("BATTERY", "BATTERY LOW • ${battery}%", "OPEN HEALTH")
        }
        if (thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("THERMAL", "PHONE HOT • ${thermalStateLabel()}", "OPEN HEALTH")
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("MIC_PERMISSION", "MICROPHONE PERMISSION OFF", "ENABLE MIC")
        }
        if (recording != null && DevelopUgandaV272FieldSoundContinuity.audioRouteChanged(this)) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("AUDIO_ROUTE", "MIC ROUTE CHANGED • CHECK INPUT", "AUDIO HELP")
        }
        if (recording != null && v272Setting("audio_coach", true) && audioAmplitude >= 0.90) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("AUDIO_CLIP", "MIC CLIPPING RISK • PEAK TOO HIGH", "AUDIO HELP")
        }
        if (recording != null && v272Setting("audio_coach", true) && audioAmplitude < 0.015) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("AUDIO_LOW", "MIC TOO LOW • CHECK INPUT", "AUDIO HELP")
        }
        if (v273Setting("movement_coach", true) && cameraShakeScore >= 58f) {
            val move = v273ShotMode()
            val label = when (move) {
                "PAN" -> "PAN TOO FAST"
                "TILT" -> "TILT TOO FAST"
                "WALKING", "FOLLOW" -> "MOVEMENT TOO ROUGH"
                else -> "CAMERA MOVE TOO FAST"
            }
            return DevelopUgandaV271LiveCoach.CoachSnapshot("MOTION_FAST", "$label • MOTION ${cameraShakeScore.roundToInt()}")
        }
        phoneRollDeg?.let { roll ->
            if (v273Setting("horizon_assist", true) && kotlin.math.abs(roll) > 3.0f) {
                return DevelopUgandaV271LiveCoach.CoachSnapshot("LEVEL", String.format(Locale.US, "PHONE NOT LEVEL • %+.1f°", roll))
            }
        }
        if (recording == null && v272Setting("continuity_engine", true) && DevelopUgandaV272FieldSoundContinuity.hasReference(this)) {
            val continuity = v272ContinuityStatus()
            val match = Regex("CONTINUITY (\\d+)%").find(continuity)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 100
            if (match < 75) {
                return DevelopUgandaV271LiveCoach.CoachSnapshot("CONTINUITY", continuity, "MATCH LAST")
            }
        }
        val v277Light = DevelopUgandaV277LightingExposure.coachSnapshot(this)
        if (v277Light.code.isNotBlank()) {
            return v277Light
        }
        if (recording == null && v244WhiteBalanceIndex == 0) {
            return DevelopUgandaV271LiveCoach.CoachSnapshot("WB_AUTO", "WB AUTO • LOCK WHEN LIGHT IS STABLE")
        }
        return DevelopUgandaV271LiveCoach.CoachSnapshot("", "")
    }

    internal fun v271ApplyCoachFix(code: String) {
        when (code.uppercase(Locale.US)) {
            "STORAGE", "BATTERY", "THERMAL" -> openV227CameraHealth()
            "MIC_PERMISSION" -> ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 173)
            "AUDIO_LOW", "AUDIO_CLIP", "AUDIO_ROUTE" -> DevelopUgandaV270Guidance.showFeature(this, "AUDIO / MIC", "V272 is reading the live CameraX audio amplitude and expected input route. Confirm the detected input and microphone distance. If clipping is shown, reduce source level or move the mic farther away; if low, move closer or use a stronger external input. The app does not claim a second safety track unless the pipeline exposes one.")
            "CONTINUITY" -> v272RestoreReference()
            "FACE_DARK", "BACKLIGHT" -> v277MeterPrimaryFace()
            "HIGHLIGHT_CLIP", "SPOT_BRIGHT" -> v277ProtectHighlights()
            "SHADOW_LOW", "SPOT_DARK" -> DevelopUgandaV270Guidance.showFeature(this, "V277 EXPOSURE", "The preview is reading dark. Add light where possible, meter the important subject, or deliberately raise exposure with the existing exposure ruler. V277 does not silently raise ISO or alter shutter during a take.")
            "FLICKER_RISK" -> DevelopUgandaV270Guidance.showFeature(this, "V277 FLICKER ASSIST", DevelopUgandaV277LightingExposure.shutterGuidance(this, requestedVideoFps().takeIf { it > 0 } ?: 30))
            else -> DevelopUgandaV270Guidance.showFeature(this, "LIVE COACH", "This hint needs an operator decision rather than an automatic camera change.")
        }
    }

    private fun v277MeterPrimaryFace() {
        val x = v277PrimaryFaceX
        val y = v277PrimaryFaceY
        val area = v277PrimaryFaceArea
        if (x == null || y == null || area == null) {
            DevelopUgandaV270Guidance.showFeature(this, "V277 FACE METER", "No primary face is currently available. Keep the face visible or tap the subject to meter/focus manually.")
            return
        }
        v251ApplyPrimaryFaceMetering(x, y, area)
        toast("V277 • METERING PRIMARY FACE")
    }

    private fun v277ProtectHighlights() {
        val cam = camera ?: run {
            toast("V277 • CAMERA NOT READY")
            return
        }
        if (operatorLocked) {
            toast("V277 • CONTROLS LOCKED")
            return
        }
        val state = cam.cameraInfo.exposureState
        val range = state.exposureCompensationRange
        val current = state.exposureCompensationIndex
        val target = (current - 1).coerceIn(range.lower, range.upper)
        if (target == current) {
            toast("V277 • EXPOSURE ALREADY AT LOWER LIMIT")
            return
        }
        cam.cameraControl.setExposureCompensationIndex(target)
        toast("V277 • HIGHLIGHT PROTECTION • EV STEP DOWN")
    }

    internal fun v277LightingStatus(): String =
        DevelopUgandaV277LightingExposure.lightStatus(this)

    internal fun v272AudioInputStatus(): String =
        DevelopUgandaV272FieldSoundContinuity.audioInputSummary(this)

    internal fun v272LastAudioTest(): String =
        DevelopUgandaV272FieldSoundContinuity.lastAudioTest(this)

    internal fun v272RunAudioTest(): String {
        if (recording != null) return "STOP RECORDING BEFORE MIC TEST"
        val initial = "MIC TEST • LISTENING 1.4s"
        toast(initial)
        DevelopUgandaV272FieldSoundContinuity.runAudioTest(this) { result ->
            toast(result)
            DevelopUgandaV270Guidance.showFeature(
                this,
                "V272 REAL MIC TEST",
                "$result\n\nThis test samples the Android microphone input directly for about 1.4 seconds. It does not alter or replace CameraX recording audio."
            )
        }
        return initial
    }

    internal fun v272ToggleSetting(key: String, defaultValue: Boolean, label: String): String {
        val message = DevelopUgandaV272FieldSoundContinuity.toggle(this, key, defaultValue, label)
        if (key == "ghost_overlay") DevelopUgandaV272FieldSoundContinuity.refreshGhost(this, root)
        toast(message)
        return message
    }

    internal fun v272Setting(key: String, defaultValue: Boolean): Boolean =
        DevelopUgandaV272FieldSoundContinuity.bool(this, key, defaultValue)

    internal fun v272GhostStrength(): Int = DevelopUgandaV272FieldSoundContinuity.ghostAlpha(this)

    internal fun v272SetGhostStrength(value: String): String {
        val number = value.filter { it.isDigit() }.toIntOrNull() ?: 18
        val message = DevelopUgandaV272FieldSoundContinuity.setGhostAlpha(this, number)
        DevelopUgandaV272FieldSoundContinuity.refreshGhost(this, root)
        toast(message)
        return message
    }

    internal fun v272PreRollStatus(): String {
        val seconds = DevelopUgandaV272FieldSoundContinuity.preRollSeconds(this)
        return if (seconds == 0) "OFF" else "${seconds}s REQUEST • NOT VERIFIED"
    }

    internal fun v272SetPreRoll(value: String): String {
        val seconds = value.filter { it.isDigit() }.toIntOrNull() ?: 0
        val message = DevelopUgandaV272FieldSoundContinuity.setPreRollSeconds(this, seconds)
        toast(message)
        return message
    }

    private fun v272SaveContinuityReference(captureFrame: Boolean) {
        val cam = camera
        val zoom = cam?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
        val exposure = cam?.cameraInfo?.exposureState?.exposureCompensationIndex ?: sceneExposureTarget
        val json = JSONObject()
            .put("scene", sceneIndex)
            .put("look", lookIndex)
            .put("quality", qualityIndex)
            .put("shutter", v244ShutterAngle)
            .put("iso", v244Iso)
            .put("wb", v244WhiteBalanceIndex)
            .put("zoom", zoom.toDouble())
            .put("exposure", exposure)
            .put("camera_id", selectedCameraDeviceId ?: "")
            .put("front", useFront)
            .put("saved_utc", Instant.now().toString())
        DevelopUgandaV272FieldSoundContinuity.saveState(this, json)
        if (captureFrame && ::previewView.isInitialized) {
            DevelopUgandaV272FieldSoundContinuity.saveReferenceBitmap(this, previewView.bitmap)
        }
        DevelopUgandaV272FieldSoundContinuity.refreshGhost(this, root)
    }

    internal fun v272CaptureReference(): String {
        if (recording != null) return "STOP RECORDING BEFORE SAVING REFERENCE"
        v272SaveContinuityReference(captureFrame = true)
        val message = if (DevelopUgandaV272FieldSoundContinuity.hasReference(this)) {
            "V272 REFERENCE SAVED • FRAME + CAMERA SETTINGS"
        } else {
            "V272 SETTINGS SAVED • PREVIEW FRAME NOT AVAILABLE YET"
        }
        toast(message)
        return message
    }

    internal fun v272ContinuityStatus(): String {
        val ref = DevelopUgandaV272FieldSoundContinuity.loadState(this) ?: return "CONTINUITY • NO REFERENCE"
        val diffs = mutableListOf<String>()
        if (ref.optInt("scene", sceneIndex) != sceneIndex) diffs += "SCENE"
        if (ref.optInt("look", lookIndex) != lookIndex) diffs += "LOOK"
        if (ref.optInt("quality", qualityIndex) != qualityIndex) diffs += "QUALITY"
        if (ref.optInt("wb", v244WhiteBalanceIndex) != v244WhiteBalanceIndex) diffs += "WB"
        if (ref.optInt("shutter", v244ShutterAngle) != v244ShutterAngle) diffs += "SHUTTER"
        if (ref.optInt("iso", v244Iso) != v244Iso) diffs += "ISO"
        val currentZoom = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
        val refZoom = ref.optDouble("zoom", currentZoom.toDouble()).toFloat()
        if (kotlin.math.abs(currentZoom - refZoom) > 0.08f) diffs += "ZOOM"
        val currentExp = camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: sceneExposureTarget
        if (kotlin.math.abs(currentExp - ref.optInt("exposure", currentExp)) > 0) diffs += "EXPOSURE"
        val total = 8
        val score = (((total - diffs.size).coerceAtLeast(0)) * 100 / total)
        return if (diffs.isEmpty()) "CONTINUITY 100% • MATCH" else "CONTINUITY $score% • CHECK ${diffs.joinToString(" / ")}"
    }

    internal fun v272RestoreReference(): String {
        if (recording != null) return "STOP RECORDING BEFORE MATCHING REFERENCE"
        val ref = DevelopUgandaV272FieldSoundContinuity.loadState(this) ?: return "NO V272 REFERENCE SAVED"
        v244WhiteBalanceIndex = ref.optInt("wb", v244WhiteBalanceIndex).coerceIn(0, v244WhiteBalanceLabels.lastIndex)
        v244ShutterAngle = ref.optInt("shutter", v244ShutterAngle)
        v244Iso = ref.optInt("iso", v244Iso)
        saveReportCameraPreferences()
        matchLastShotContinuity()
        val message = "V272 MATCH LAST • WB / SHUTTER / ISO + V227 ZOOM / EXPOSURE RESTORE"
        toast(message)
        return message
    }

    internal fun v272VerificationSummary(): String =
        DevelopUgandaV272FieldSoundContinuity.verificationSummary(this)

    internal fun v272FieldStatus(): String =
        "${v272AudioInputStatus()} • ${v272ContinuityStatus()} • PRE-ROLL ${v272PreRollStatus()}"


    // V273 MOTION + SHOT CONTROL ENGINE ---------------------------------------
    internal fun v273RollDeg(): Float? = phoneRollDeg
    internal fun v273PitchDeg(): Float? = phonePitchDeg
    internal fun v273MotionScore(): Float = cameraShakeScore.coerceIn(0f, 100f)
    internal fun v273ZoomRatio(): Float = camera?.cameraInfo?.zoomState?.value?.zoomRatio ?: 1f
    internal fun v273StabilizationStatus(): String = activeVideoStabilizationLabel

    internal fun v273Setting(key: String, defaultValue: Boolean): Boolean =
        DevelopUgandaV273MotionShotControl.bool(this, key, defaultValue)

    internal fun v273ToggleSetting(key: String, defaultValue: Boolean, label: String): String {
        val message = DevelopUgandaV273MotionShotControl.toggle(this, key, defaultValue, label)
        DevelopUgandaV273MotionShotControl.refresh(this, root)
        toast(message)
        return message
    }

    internal fun v273ShotMode(): String = DevelopUgandaV273MotionShotControl.shotMode(this)

    internal fun v273SetShotMode(value: String): String {
        val message = DevelopUgandaV273MotionShotControl.setShotMode(this, value)
        DevelopUgandaV273MotionShotControl.refresh(this, root)
        toast(message)
        return message
    }

    internal fun v273MotionStatus(): String {
        val roll = phoneRollDeg
        val level = if (roll == null) "LEVEL --" else String.format(Locale.US, "LEVEL %+.1f°", roll)
        val motion = when {
            cameraShakeScore >= 58f -> "FAST"
            cameraShakeScore >= 30f -> "MED"
            else -> "STABLE"
        }
        return "$level • MOTION $motion ${cameraShakeScore.roundToInt()} • ${v273ShotMode()} • ${v273StabilizationStatus()}"
    }

    internal fun v273ZoomRampStatus(): String = String.format(
        Locale.US,
        "ZOOM %.1f× → %.1f× • %.1fs",
        DevelopUgandaV273MotionShotControl.zoomStart(this),
        DevelopUgandaV273MotionShotControl.zoomEnd(this),
        DevelopUgandaV273MotionShotControl.zoomDurationMs(this) / 1000f
    )

    internal fun v273SetZoomStart(value: String): String {
        val v = value.replace("×", "").trim().toFloatOrNull() ?: 1f
        val message = DevelopUgandaV273MotionShotControl.setZoomStart(this, v)
        toast(message); return message
    }

    internal fun v273SetZoomEnd(value: String): String {
        val v = value.replace("×", "").trim().toFloatOrNull() ?: 2f
        val message = DevelopUgandaV273MotionShotControl.setZoomEnd(this, v)
        toast(message); return message
    }

    internal fun v273SetZoomDuration(value: String): String {
        val seconds = value.filter { it.isDigit() }.toIntOrNull() ?: 5
        val message = DevelopUgandaV273MotionShotControl.setZoomDuration(this, seconds * 1000)
        toast(message); return message
    }

    internal fun v273RunZoomRamp(): String {
        val cam = camera ?: return "CAMERA NOT READY"
        val z = cam.cameraInfo.zoomState.value ?: return "ZOOM STATE UNAVAILABLE"
        val requestedStart = DevelopUgandaV273MotionShotControl.zoomStart(this)
        val requestedEnd = DevelopUgandaV273MotionShotControl.zoomEnd(this)
        val start = requestedStart.coerceIn(z.minZoomRatio, z.maxZoomRatio)
        val end = requestedEnd.coerceIn(z.minZoomRatio, z.maxZoomRatio)
        val duration = DevelopUgandaV273MotionShotControl.zoomDurationMs(this)
        val steps = (duration / 50).coerceIn(16, 160)
        val generation = SystemClock.elapsedRealtime()
        v273ZoomRampGeneration = generation
        for (i in 0..steps) {
            val delay = duration.toLong() * i / steps
            uiHandler.postDelayed({
                if (v273ZoomRampGeneration != generation) return@postDelayed
                val t = i.toFloat() / steps.toFloat()
                val eased = t * t * (3f - 2f * t)
                val ratio = start + (end - start) * eased
                try { cam.cameraControl.setZoomRatio(ratio) } catch (_: Exception) {}
                if (i == steps) toast(String.format(Locale.US, "ZOOM RAMP COMPLETE • %.1f×", end))
            }, delay)
        }
        val message = String.format(Locale.US,"ZOOM RAMP • %.1f× → %.1f× • %.1fs",start,end,duration/1000f)
        toast(message); return message
    }

    internal fun v273StopZoomRamp(): String {
        v273ZoomRampGeneration = -1L
        val message = "ZOOM RAMP STOPPED"
        toast(message); return message
    }

    internal fun v273ExposureRampStatus(): String =
        "EXPOSURE ${DevelopUgandaV273MotionShotControl.exposureStart(this)} → ${DevelopUgandaV273MotionShotControl.exposureEnd(this)} • ${DevelopUgandaV273MotionShotControl.exposureDurationMs(this)/1000f}s"

    internal fun v273SetExposureStart(value: String): String {
        val v=value.trim().toIntOrNull() ?: 0
        val m=DevelopUgandaV273MotionShotControl.setExposureStart(this,v); toast(m); return m
    }
    internal fun v273SetExposureEnd(value: String): String {
        val v=value.trim().toIntOrNull() ?: 0
        val m=DevelopUgandaV273MotionShotControl.setExposureEnd(this,v); toast(m); return m
    }
    internal fun v273SetExposureDuration(value: String): String {
        val seconds=value.filter{it.isDigit()}.toIntOrNull() ?: 5
        val m=DevelopUgandaV273MotionShotControl.setExposureDuration(this,seconds*1000); toast(m); return m
    }

    internal fun v273RunExposureRamp(): String {
        val cam=camera ?: return "CAMERA NOT READY"
        val state=cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return "EXPOSURE RAMP • DEVICE LIMITED"
        val start=DevelopUgandaV273MotionShotControl.exposureStart(this).coerceIn(state.exposureCompensationRange.lower,state.exposureCompensationRange.upper)
        val end=DevelopUgandaV273MotionShotControl.exposureEnd(this).coerceIn(state.exposureCompensationRange.lower,state.exposureCompensationRange.upper)
        val duration=DevelopUgandaV273MotionShotControl.exposureDurationMs(this)
        val steps=(duration/120).coerceIn(8,80)
        val generation=SystemClock.elapsedRealtime(); v273ExposureRampGeneration=generation
        for(i in 0..steps){
            val delay=duration.toLong()*i/steps
            uiHandler.postDelayed({
                if(v273ExposureRampGeneration!=generation) return@postDelayed
                val t=i.toFloat()/steps.toFloat(); val eased=t*t*(3f-2f*t)
                val index=(start+(end-start)*eased).roundToInt().coerceIn(state.exposureCompensationRange.lower,state.exposureCompensationRange.upper)
                try{cam.cameraControl.setExposureCompensationIndex(index)}catch(_:Exception){}
                if(i==steps) toast("EXPOSURE RAMP COMPLETE • $end")
            },delay)
        }
        val m="EXPOSURE RAMP • $start → $end • ${duration/1000f}s"; toast(m); return m
    }

    internal fun v273StopExposureRamp(): String { v273ExposureRampGeneration=-1L; val m="EXPOSURE RAMP STOPPED"; toast(m); return m }

    private fun v273RehearsalJson(): JSONObject = JSONObject()
        .put("shot_mode", v273ShotMode())
        .put("roll", phoneRollDeg?.toDouble() ?: JSONObject.NULL)
        .put("pitch", phonePitchDeg?.toDouble() ?: JSONObject.NULL)
        .put("motion", cameraShakeScore.toDouble())
        .put("zoom", v273ZoomRatio().toDouble())
        .put("exposure", camera?.cameraInfo?.exposureState?.exposureCompensationIndex ?: sceneExposureTarget)
        .put("wb", v244WhiteBalanceIndex)
        .put("shutter", v244ShutterAngle)
        .put("iso", v244Iso)
        .put("tracking", v255SubjectTrackingMode())
        .put("focus_a", v255FocusA())
        .put("focus_b", v255FocusB())
        .put("focus_ms", v255FocusPullMs())
        .put("saved_utc", Instant.now().toString())

    internal fun v273SaveRehearsalStart(): String {
        val m=DevelopUgandaV273MotionShotControl.saveRehearsal(this,"START",v273RehearsalJson()); toast(m); return m
    }
    internal fun v273SaveRehearsalEnd(): String {
        val m=DevelopUgandaV273MotionShotControl.saveRehearsal(this,"END",v273RehearsalJson()); toast(m); return m
    }
    internal fun v273RehearsalStatus(): String = DevelopUgandaV273MotionShotControl.rehearsalStatus(this)

    internal fun v273RestoreRehearsalStart(): String {
        if(recording!=null) return "STOP RECORDING BEFORE RESTORING REHEARSAL"
        val j=DevelopUgandaV273MotionShotControl.rehearsal(this,"START") ?: return "NO REHEARSAL START SAVED"
        v244WhiteBalanceIndex=j.optInt("wb",v244WhiteBalanceIndex).coerceIn(0,v244WhiteBalanceLabels.lastIndex)
        v244ShutterAngle=j.optInt("shutter",v244ShutterAngle)
        v244Iso=j.optInt("iso",v244Iso)
        v255FocusAState=j.optInt("focus_a",v255FocusAState).coerceIn(0,100)
        v255FocusBState=j.optInt("focus_b",v255FocusBState).coerceIn(0,100)
        v255FocusPullMsState=j.optInt("focus_ms",v255FocusPullMsState).coerceIn(500,2500)
        val mode=j.optString("tracking",v255SubjectTrackingMode())
        if(!v255FocusPullOn) v255SetSubjectTrackingMode(mode)
        saveReportCameraPreferences()
        val cam=camera
        if(cam!=null){
            val z=cam.cameraInfo.zoomState.value
            if(z!=null){ val ratio=j.optDouble("zoom",z.zoomRatio.toDouble()).toFloat().coerceIn(z.minZoomRatio,z.maxZoomRatio); try{cam.cameraControl.setZoomRatio(ratio)}catch(_:Exception){} }
            val e=cam.cameraInfo.exposureState
            if(e.isExposureCompensationSupported){ val index=j.optInt("exposure",e.exposureCompensationIndex).coerceIn(e.exposureCompensationRange.lower,e.exposureCompensationRange.upper); try{cam.cameraControl.setExposureCompensationIndex(index)}catch(_:Exception){} }
        }
        val m="REHEARSAL START RESTORED • WB / SHUTTER / ISO / ZOOM / EXPOSURE / TRACK STATE"; toast(m); return m
    }

    internal fun v273VerificationSummary(): String = DevelopUgandaV273MotionShotControl.verificationSummary(this)

    private fun v251ApplyPrimaryFaceMetering(
        normalizedX: Float,
        normalizedY: Float,
        areaRatio: Float
    ) {
        if (
            !(v251FaceExposurePriority || v259SmartExposureAssistOn) ||
            focusLockActive ||
            operatorLocked ||
            areaRatio < 0.012f ||
            !::previewView.isInitialized
        ) {
            return
        }

        val now =
            SystemClock.elapsedRealtime()

        if (
            now - v251LastFaceMeterMs <
                1800L
        ) {
            return
        }

        val cam =
            camera
                ?: return

        if (
            previewView.width <= 0 ||
            previewView.height <= 0
        ) {
            return
        }

        val x =
            normalizedX
                .coerceIn(
                    0.08f,
                    0.92f
                ) *
                previewView.width.toFloat()

        val y =
            normalizedY
                .coerceIn(
                    0.08f,
                    0.92f
                ) *
                previewView.height.toFloat()

        try {
            val point =
                previewView
                    .meteringPointFactory
                    .createPoint(
                        x,
                        y,
                        0.22f
                    )

            val action =
                FocusMeteringAction.Builder(
                    point,
                    FocusMeteringAction.FLAG_AE or
                        FocusMeteringAction.FLAG_AWB
                )
                    .setAutoCancelDuration(
                        2,
                        TimeUnit.SECONDS
                    )
                    .build()

            cam.cameraControl
                .startFocusAndMetering(
                    action
                )

            v251LastFaceMeterMs =
                now
        } catch (_: Exception) {
        }
    }

    private fun startShotAssistLoop() {
        uiHandler.removeCallbacks(
            shotAssistRunnable
        )

        uiHandler.postDelayed(
            shotAssistRunnable,
            900L
        )
    }

    private fun cycleShotAssist() {
        shotAssistModeIndex =
            (
                shotAssistModeIndex +
                    1
                ) %
                shotAssistModeLabels.size

        if (
            ::shotAssistView.isInitialized
        ) {
            shotAssistView.setAssistMode(
                shotAssistModeIndex
            )
        }

        if (
            ::assistButton.isInitialized
        ) {
            assistButton.text =
                "ASSIST ▾\n${shotAssistModeLabels[shotAssistModeIndex]}"

            assistButton.isSelected =
                shotAssistModeIndex !=
                    DevelopUgandaShotAssistView.MODE_OFF
        }

        toast(
            when (
                shotAssistModeIndex
            ) {
                DevelopUgandaShotAssistView.MODE_PEAK ->
                    "Edge peaking ON • screen only"

                DevelopUgandaShotAssistView.MODE_ZEBRA ->
                    "Exposure zebra ON • screen only"

                DevelopUgandaShotAssistView.MODE_BOTH ->
                    "Peak + zebra ON • screen only"

                else ->
                    "Shot assist OFF"
            }
        )
    }

    private fun isDirectorPeopleMode(): Boolean {
        if (v255SubjectTracking != "OFF" || v251FaceExposurePriority || v259SmartExposureAssistOn) {
            return true
        }

        return cameraExperienceId in
            setOf(
                "V205_FOCUS",
                "V211_AUDIO"
            ) ||
            sceneModes[
                sceneIndex
            ] ==
                "INTERVIEW"
    }

    private fun startDirectorLoop() {
        uiHandler.removeCallbacks(
            directorRunnable
        )

        uiHandler.postDelayed(
            directorRunnable,
            1100L
        )
    }

    private fun toggleDirectorGuidance() {
        directorEnabled =
            !directorEnabled

        if (
            ::directorOverlayView.isInitialized
        ) {
            directorOverlayView.setDirectorEnabled(
                directorEnabled || v251FaceExposurePriority || v259SmartExposureAssistOn || v255SubjectTracking != "OFF"
            )

            directorOverlayView.visibility =
                if (
                    directorEnabled
                ) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
        }

        if (
            ::directorButton.isInitialized
        ) {
            directorButton.text =
                "DIRECTOR ▾\n" +
                    if (
                        directorEnabled
                    ) {
                        "ON"
                    } else {
                        "OFF"
                    }

            directorButton.isSelected =
                directorEnabled
        }

        saveReportCameraPreferences()

        toast(
            if (
                directorEnabled
            ) {
                "Director + preview histogram ON"
            } else {
                "Director guidance OFF"
            }
        )
    }

    private fun estimatedRecordingTimeText(): String {
        return try {
            val freeBytes =
                StatFs(
                    Environment.getExternalStorageDirectory().path
                ).availableBytes

            val bitsPerSecond =
                (
                    targetVideoBitrate()
                        .toLong() +
                        320_000L
                    )
                    .coerceAtLeast(
                        1L
                    )

            val seconds =
                (
                    freeBytes *
                        8L
                    ) /
                    bitsPerSecond

            when {
                seconds <=
                    0L ->
                        "EST REC --"

                seconds >=
                    3600L ->
                        String.format(
                            Locale.US,
                            "EST REC %dh %02dm",
                            seconds /
                                3600L,
                            (
                                seconds /
                                    60L
                                ) %
                                60L
                        )

                else ->
                    String.format(
                        Locale.US,
                        "EST REC %dm",
                        seconds /
                            60L
                    )
            }
        } catch (_: Exception) {
            "EST REC --"
        }
    }

    private fun currentLensDeckLabel(): String {
        val selected =
            selectedCameraDeviceId

        return if (
            !selected.isNullOrBlank()
        ) {
            "ID " +
                selected.takeLast(
                    7
                )
        } else if (
            useFront
        ) {
            "FRONT"
        } else {
            "BACK"
        }
    }

    private fun showRealCameraDevicePicker() {
        val p =
            provider

        if (
            p ==
                null
        ) {
            toast(
                "Camera map is still loading"
            )

            return
        }

        val devices =
            DevelopUgandaLensIntelligence.devices(
                p
            )

        if (
            devices.isEmpty()
        ) {
            toast(
                "No additional CameraX device IDs were exposed"
            )

            return
        }

        AlertDialog.Builder(
            this
        )
            .setTitle(
                "REAL CAMERAS EXPOSED BY ANDROID"
            )
            .setMessage(
                "These are actual CameraX / Camera2 devices exposed by this phone. develop.uganda does not invent 0.5× / 1× / 3× lens buttons."
            )
            .setItems(
                devices
                    .map {
                        it.label()
                    }
                    .toTypedArray()
            ) {
                    _,
                    which ->
                val picked =
                    devices[
                        which
                    ]

                selectedCameraDeviceId =
                    picked.cameraId

                useFront =
                    picked.facing ==
                        "FRONT"

                saveReportCameraPreferences()

                bindCamera()

                toast(
                    "Selected ${picked.shortLabel()}"
                )
            }
            .setNegativeButton(
                "CANCEL",
                null
            )
            .show()
    }

    private fun saveV227ContinuitySnapshot() {
        val cam =
            camera

        val zoom =
            cam
                ?.cameraInfo
                ?.zoomState
                ?.value
                ?.zoomRatio
                ?: 1f

        val exposure =
            cam
                ?.cameraInfo
                ?.exposureState
                ?.exposureCompensationIndex
                ?: sceneExposureTarget

        DevelopUgandaContinuityMemory.save(
            this,
            cameraExperienceId,
            DevelopUgandaContinuityMemory.Snapshot(
                sceneIndex =
                    sceneIndex,
                lookIndex =
                    lookIndex,
                qualityIndex =
                    qualityIndex,
                presetIndex =
                    reportPresetIndex,
                useFront =
                    useFront,
                cameraDeviceId =
                    selectedCameraDeviceId,
                zoomRatio =
                    zoom,
                exposureCompensation =
                    exposure,
                savedUtc =
                    Instant.now().toString()
            )
        )

        if (
            ::continuityButton.isInitialized
        ) {
            continuityButton.text =
                "MATCH LAST\nREADY"

            continuityButton.isSelected =
                true
        }

        // V272 extends the existing continuity memory with professional exposure/WB state
        // and a real screen-only preview reference frame after a successful take.
        if (v272Setting("continuity_engine", true)) {
            v272SaveContinuityReference(captureFrame = true)
        }
    }

    private fun matchLastShotContinuity() {
        if (
            recording !=
                null
        ) {
            toast(
                "Stop recording before matching the last shot"
            )

            return
        }

        val snapshot =
            DevelopUgandaContinuityMemory.load(
                this,
                cameraExperienceId
            )

        if (
            snapshot ==
                null
        ) {
            toast(
                "No previous shot is stored for this camera"
            )

            return
        }

        sceneIndex =
            snapshot.sceneIndex
                .coerceIn(
                    0,
                    sceneModes.lastIndex
                )

        lookIndex =
            snapshot.lookIndex
                .coerceIn(
                    0,
                    lookModes.lastIndex
                )

        qualityIndex =
            snapshot.qualityIndex
                .coerceIn(
                    0,
                    qualityModes.lastIndex
                )

        reportPresetIndex =
            snapshot.presetIndex
                .coerceIn(
                    0,
                    reportPresetLabels.lastIndex
                )

        useFront =
            snapshot.useFront

        selectedCameraDeviceId =
            snapshot.cameraDeviceId
                ?.takeIf {
                    DevelopUgandaLensIntelligence.hasCamera(
                        provider,
                        it
                    )
                }

        sceneExposureTarget =
            snapshot.exposureCompensation

        saveReportCameraPreferences()

        bindCamera()

        uiHandler.postDelayed(
            {
                val cam =
                    camera

                val zoomState =
                    cam
                        ?.cameraInfo
                        ?.zoomState
                        ?.value

                if (
                    cam !=
                        null &&
                    zoomState !=
                        null
                ) {
                    val ratio =
                        snapshot.zoomRatio
                            .coerceIn(
                                zoomState.minZoomRatio,
                                zoomState.maxZoomRatio
                            )

                    cam.cameraControl
                        .setZoomRatio(
                            ratio
                        )

                    val span =
                        (
                            zoomState.maxZoomRatio -
                                zoomState.minZoomRatio
                            )
                            .coerceAtLeast(
                                0.01f
                            )

                    zoomSeek.progress =
                        (
                            (
                                ratio -
                                    zoomState.minZoomRatio
                                ) /
                                span *
                                100f
                            )
                            .roundToInt()
                            .coerceIn(
                                0,
                                100
                            )
                }

                val exposure =
                    cam
                        ?.cameraInfo
                        ?.exposureState

                if (
                    cam !=
                        null &&
                    exposure !=
                        null &&
                    exposure.isExposureCompensationSupported
                ) {
                    val value =
                        snapshot.exposureCompensation
                            .coerceIn(
                                exposure.exposureCompensationRange.lower,
                                exposure.exposureCompensationRange.upper
                            )

                    cam.cameraControl
                        .setExposureCompensationIndex(
                            value
                        )

                    sceneExposureTarget =
                        value

                    exposureSeek.progress =
                        (
                            value +
                                6
                            )
                            .coerceIn(
                                0,
                                12
                            )
                }

                refreshHud()

                toast(
                    "MATCH LAST SHOT • restored controllable settings"
                )
            },
            500L
        )
    }

    private fun v229ColorHint(): String {
        return buildString {
            append(cameraExperienceId)
            append(" • ")
            append(cameraExperienceDisplayName())
            append(" • ")
            append(sceneModes[sceneIndex])
            append(" • ")
            append(lookModes[lookIndex])
            append(" • ")
            append(qualityModes[qualityIndex])
            append(" • ")
            append(reportDisplayMode)
        }
    }

    private fun v229ColorScope(): String =
        cameraExperienceId.ifBlank {
            "REPORT"
        }

    private fun v229ColorResolved(): DevelopUgandaColorEngine.ResolvedSelection =
        DevelopUgandaColorEngine.resolve(
            this,
            v229ColorScope(),
            v229ColorHint()
        )

    private fun v229ColorDeckLabel(): String {
        val value = v229ColorResolved()
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

    private fun refreshV233ColorMonitor() {
        if (
            !::previewView.isInitialized ||
            !::colorButton.isInitialized
        ) {
            return
        }

        val value = v229ColorResolved()
        v229ColorOverlayLabel =
            if (value.enabled) {
                value.label
            } else {
                "ORIGINAL"
            }

        val key =
            "${value.requestedId}:${value.label}:${value.strength}:${DevelopUgandaColorEngine.monitorEnabled(this)}"

        colorButton.text =
            "COLOR ▾\n${v229ColorDeckLabel()}"

        colorButton.isSelected =
            value.enabled

        if (
            key !=
                lastV233ColorMonitorKey
        ) {
            lastV233ColorMonitorKey =
                key

            DevelopUgandaColorEngine.applyPreviewMonitor(
                previewView,
                value,
                v229ColorScope()
            )
        }
    }

    private fun showV233ColorDropdown(
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

        val base =
            DevelopUgandaColorEngine.menuLabels()
                .toMutableList()

        base.add(
            "COLOR STUDIO • STRENGTH / MONITOR"
        )

        val selected =
            DevelopUgandaColorEngine.selectedMenuIndex(
                this,
                v229ColorScope()
            )

        showReportPillDropdown(
            anchor,
            "V233 PROFESSIONAL COLOR",
            base.toTypedArray(),
            selected
        ) {
                picked ->
            if (
                picked >=
                    base.lastIndex
            ) {
                openV233ColorStudio()
                return@showReportPillDropdown
            }

            DevelopUgandaColorEngine.setSelectedMenuIndex(
                this,
                v229ColorScope(),
                picked
            )

            lastV233ColorMonitorKey =
                ""

            refreshV233ColorMonitor()
            refreshReportSettingsSummary()

            toast(
                "V233 COLOR • ${v229ColorResolved().statusLabel()}"
            )
        }
    }

    private fun openV233ColorStudio() {
        startActivity(
            android.content.Intent(
                this,
                DevelopUgandaColorStudioActivity::class.java
            ).apply {
                putExtra(
                    DevelopUgandaColorStudioActivity.EXTRA_SCOPE,
                    v229ColorScope()
                )
                putExtra(
                    DevelopUgandaColorStudioActivity.EXTRA_HINT,
                    v229ColorHint()
                )
            }
        )
    }

    private fun scheduleV233ColorMaster(
        sourceUri: Uri,
        packageId: String
    ) {
        val selection =
            v229ColorResolved()

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
            v229ColorScope()
        val hintSnapshot =
            v229ColorHint()

        fun waitForSafeStart(
            attempt: Int
        ) {
            val packageEntry =
                DevelopUgandaStoryPackager.listRegistry(
                    applicationContext
                )
                    .firstOrNull {
                        it.packageId ==
                            packageId
                    }

            val packageBusy =
                packageEntry
                    ?.state
                    ?.contains(
                        "BUILDING",
                        ignoreCase = true
                    )
                    ?: false

            val socialBusy =
                isSocialMediaCamera() &&
                    automaticSocialExportActive

            if (
                (packageBusy || socialBusy) &&
                attempt <
                    180
            ) {
                uiHandler.postDelayed(
                    {
                        waitForSafeStart(
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
                            "V233 COLOR MASTER READY • ${outcome.profileLabel}"
                        )
                    }
                } else {
                    DevelopUgandaStoryPackager.markColorMasterFailed(
                        applicationContext,
                        packageId,
                        outcome.message
                    )

                    runOnUiThread {
                        toast(
                            "V233 color master not created • original video is safe"
                        )
                    }
                }
            }
        }

        uiHandler.postDelayed(
            {
                waitForSafeStart(
                    0
                )
            },
            if (
                isSocialMediaCamera()
            ) {
                4500L
            } else {
                1000L
            }
        )
    }

    private fun refreshV228BrandUi() {
        if (
            ::previewBrandView.isInitialized
        ) {
            previewBrandView.text =
                DevelopUgandaBrandMetadataStore.previewTitle(
                    this,
                    "V259 PRO CAM"
                )
        }

        if (
            ::brandMetadataButton.isInitialized
        ) {
            val config =
                DevelopUgandaBrandMetadataStore.snapshot(
                    this
                )

            brandMetadataButton.text =
                "BRAND\n" +
                    config.preset
                        .take(
                            9
                        )
        }
    }

    private fun openV228BrandMetadataStudio() {
        startActivity(
            android.content.Intent(
                this,
                DevelopUgandaBrandMetadataActivity::class.java
            )
        )
    }

    private fun openV227CameraHealth() {
        startActivity(
            android.content.Intent(
                this,
                DevelopUgandaCameraHealthActivity::class.java
            )
        )
    }

    private fun recordingHealthText(): String {
        val battery =
            batteryPct()
                ?: -1

        val storage =
            freeStorageGb()
                ?: -1L

        val gps =
            accuracy

        val micReady =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) ==
                PackageManager.PERMISSION_GRANTED

        val state =
            when {
                battery in 0..9 ->
                    "CRITICAL"

                storage in 0..1L ->
                    "CRITICAL"

                !micReady ->
                    "CHECK MIC"

                gps != null &&
                    gps >
                    50f ->
                    "GPS WEAK"

                battery in 10..19 ->
                    "BAT LOW"

                storage in 2..4L ->
                    "SPACE LOW"

                else ->
                    "GOOD"
            }

        return buildString {
            append("REC HEALTH ")
            append(state)

            append(" • BAT ")
            append(
                if (battery >= 0) {
                    "$battery%"
                } else {
                    "--"
                }
            )

            append(" • FREE ")
            append(
                if (storage >= 0L) {
                    "${storage}GB"
                } else {
                    "--"
                }
            )

            append(" • GPS ")
            append(
                gps?.let {
                    String.format(
                        Locale.US,
                        "±%.0fm",
                        it
                    )
                } ?: "--"
            )

            append(" • MIC ")
            append(
                if (micReady) {
                    "READY"
                } else {
                    "OFF"
                }
            )
        }
    }

    private fun recordingHealthColor(): Int {
        val text =
            recordingHealthText()

        return when {
            text.contains(
                "CRITICAL"
            ) ->
                DevelopUgandaFivemods8Theme.record

            text.contains(
                "LOW"
            ) ||
                text.contains(
                    "WEAK"
                ) ||
                text.contains(
                    "CHECK"
                ) ->
                DevelopUgandaFivemods8Theme.accent

            else ->
                DevelopUgandaFivemods8Theme.accent
        }
    }

    private fun loadReportCameraPreferences() {
        val prefs =
            duSharedPreferences(
                reportCameraPrefsName(),
                Context.MODE_PRIVATE
            )

        sceneIndex =
            prefs.getInt(
                "scene_index",
                sceneIndex
            )
                .coerceIn(
                    0,
                    sceneModes.lastIndex
                )

        lookIndex =
            prefs.getInt(
                "look_index",
                lookIndex
            )
                .coerceIn(
                    0,
                    lookModes.lastIndex
                )

        qualityIndex =
            prefs.getInt(
                "quality_index",
                qualityIndex
            )
                .coerceIn(
                    0,
                    qualityModes.lastIndex
                )

        captureModeIndex =
            prefs.getInt(
                "capture_index",
                captureModeIndex
            )
                .coerceIn(
                    0,
                    captureModes.lastIndex
                )

        reportHudSizeIndex =
            prefs.getInt(
                "hud_size",
                reportHudSizeIndex
            )
                .coerceIn(
                    0,
                    reportHudLabels.lastIndex
                )

        reportHudContrastIndex =
            prefs.getInt(
                "hud_contrast",
                reportHudContrastIndex
            )
                .coerceIn(
                    0,
                    reportHudContrastLabels.lastIndex
                )

        reportHudBackingIndex =
            prefs.getInt(
                "hud_backing",
                reportHudBackingIndex
            )
                .coerceIn(
                    0,
                    reportHudBackingLabels.lastIndex
                )

        reportPresetIndex =
            prefs.getInt(
                "preset_index",
                reportPresetIndex
            )
                .coerceIn(
                    0,
                    reportPresetLabels.lastIndex
                )

        autoDirectorEnabled =
            prefs.getBoolean(
                "auto_director",
                autoDirectorEnabled
            )

        previewGuidesEnabled =
            prefs.getBoolean(
                "guides",
                previewGuidesEnabled
            )

        autoHideOperatorUi =
            prefs.getBoolean(
                "auto_ui",
                autoHideOperatorUi
            )

        integrityEnabled =
            prefs.getBoolean(
                "integrity",
                integrityEnabled
            )

        selectedCameraDeviceId =
            prefs.getString(
                "real_camera_id",
                selectedCameraDeviceId
            )

        directorEnabled =
            prefs.getBoolean(
                "director_enabled",
                directorEnabled
            )
    }

    private fun saveReportCameraPreferences() {
        duSharedPreferences(
            reportCameraPrefsName(),
            Context.MODE_PRIVATE
        )
            .edit()
            .putInt(
                "scene_index",
                sceneIndex
            )
            .putInt(
                "look_index",
                lookIndex
            )
            .putInt(
                "quality_index",
                qualityIndex
            )
            .putInt(
                "capture_index",
                captureModeIndex
            )
            .putInt(
                "hud_size",
                reportHudSizeIndex
            )
            .putInt(
                "hud_contrast",
                reportHudContrastIndex
            )
            .putInt(
                "hud_backing",
                reportHudBackingIndex
            )
            .putInt(
                "preset_index",
                reportPresetIndex
            )
            .putBoolean(
                "auto_director",
                autoDirectorEnabled
            )
            .putBoolean(
                "guides",
                previewGuidesEnabled
            )
            .putBoolean(
                "auto_ui",
                autoHideOperatorUi
            )
            .putBoolean(
                "integrity",
                integrityEnabled
            )
            .putString(
                "real_camera_id",
                selectedCameraDeviceId
            )
            .putBoolean(
                "director_enabled",
                directorEnabled
            )
            .apply()
    }

    private fun reportHudBackingAlpha(): Int {
        return when (
            reportHudBackingIndex
        ) {
            0 ->
                0

            2 ->
                58

            else ->
                32
        }
    }

    private fun drawReportTextBackplate(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        val alpha =
            reportHudBackingAlpha()

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

        val left =
            x -
                padX

        val top =
            y +
                metrics.ascent -
                padY

        val right =
            x +
                paint.measureText(
                    value
                ) +
                padX

        val bottom =
            y +
                metrics.descent +
                padY

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
            left,
            top,
            right,
            bottom,
            paint.textSize *
                0.22f,
            paint.textSize *
                0.22f,
            background
        )
    }

    private fun reportHudOutlineScale(): Float {
        return when (
            reportHudContrastIndex
        ) {
            0 ->
                0.014f

            2 ->
                0.032f

            else ->
                0.022f
        }
    }

    private fun reportHudOutlineColor(): Int {
        return when (
            reportHudContrastIndex
        ) {
            0 ->
                0x26000000

            2 ->
                0x52000000

            else ->
                0x38000000
        }
    }

    private fun reportHudShadowRadius(
        u: Float
    ): Float {
        return when (
            reportHudContrastIndex
        ) {
            0 ->
                0.39f * u

            2 ->
                1.0f * u

            else ->
                0.65f * u
        }
    }

    private fun requestPermissionsAndStart() {
        val needed = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(
                this,
                it
            ) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                173
            )
        } else {
            startEverything()
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
        if (requestCode == 262) {
            v262EnsureRemoteServerState()
            return
        }
        startEverything()
    }

    private fun showFocusReticle(
        x: Float,
        y: Float,
        locked: Boolean
    ) {
        if (
            !::focusReticleView.isInitialized
        ) {
            return
        }

        val size =
            dp(76).toFloat()

        val maxX =
            (
                previewView.width.toFloat() -
                    size
                ).coerceAtLeast(
                    0f
                )

        val maxY =
            (
                previewView.height.toFloat() -
                    size
                ).coerceAtLeast(
                    0f
                )

        focusReticleView.x =
            (
                x -
                    size /
                    2f
                ).coerceIn(
                    0f,
                    maxX
                )

        focusReticleView.y =
            (
                y -
                    size /
                    2f
                ).coerceIn(
                    0f,
                    maxY
                )

        focusReticleView.text =
            if (locked) {
                "AF + METER\nLOCK"
            } else {
                "AF"
            }

        focusReticleView.setTextColor(
            if (locked) {
                DevelopUgandaFivemods8Theme.accent
            } else {
                DevelopUgandaFivemods8Theme.content
            }
        )

        focusReticleView.background =
            GradientDrawable().apply {
                shape =
                    GradientDrawable.RECTANGLE

                cornerRadius =
                    dp(12).toFloat()

                setColor(
                    if (locked) {
                        DevelopUgandaFivemods8Theme.surfaceScrim(58)
                    } else {
                        DevelopUgandaFivemods8Theme.surfaceScrim(38)
                    }
                )

                setStroke(
                    dp(
                        if (locked) {
                            3
                        } else {
                            2
                        }
                    ),
                    if (locked) {
                        DevelopUgandaFivemods8Theme.accent
                    } else {
                        DevelopUgandaFivemods8Theme.accent
                    }
                )
            }

        focusReticleView.visibility =
            View.VISIBLE

        uiHandler.removeCallbacks(
            hideFocusReticleRunnable
        )

        if (!locked) {
            uiHandler.postDelayed(
                hideFocusReticleRunnable,
                950L
            )
        }
    }

    /**
     * Capability-safe focus/metering. It only asks a lens for AF or AE modes
     * that CameraX reports as supported, and discards callbacks from a prior
     * lens, rebind, or tap.
     */
    private fun coreRequestFocus(
        x: Float,
        y: Float,
        hold: Boolean
    ) {
        val cam = camera ?: run {
            toast("Camera is not ready")
            return
        }
        if (previewView.width <= 0 || previewView.height <= 0) {
            toast("Preview is not ready")
            return
        }

        val px = x.coerceIn(0f, previewView.width.toFloat())
        val py = y.coerceIn(0f, previewView.height.toFloat())
        val token = coreFocusGeneration + 1L
        coreFocusGeneration = token

        try {
            val point = previewView.meteringPointFactory.createPoint(px, py, 0.15f)
            val afProbe = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF
            ).build()
            val aeProbe = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AE
            ).build()
            val supportsAf = cam.cameraInfo.isFocusMeteringSupported(afProbe)
            val supportsAe = cam.cameraInfo.isFocusMeteringSupported(aeProbe)
            val flags =
                (if (supportsAf) FocusMeteringAction.FLAG_AF else 0) or
                    (if (supportsAe) FocusMeteringAction.FLAG_AE else 0)

            if (flags == 0) {
                focusAttempted = false
                focusSuccessful = null
                focusLockActive = false
                toast("Focus and metering are unavailable on this lens")
                return
            }

            val action = FocusMeteringAction.Builder(point, flags)
            if (hold) {
                action.disableAutoCancel()
            } else {
                action.setAutoCancelDuration(3L, TimeUnit.SECONDS)
            }

            focusAttempted = supportsAf
            focusSuccessful = null
            focusLockActive = hold
            showFocusReticle(px, py, hold)
            if (::focusReticleView.isInitialized) {
                focusReticleView.text = if (supportsAf) "FOCUSING…" else "METERING…"
            }

            val future = cam.cameraControl.startFocusAndMetering(action.build())
            future.addListener(focusResult@{
                if (
                    coreFocusGeneration != token ||
                    camera !== cam ||
                    isFinishing
                ) {
                    return@focusResult
                }

                val result = runCatching { future.get() }
                focusSuccessful =
                    if (supportsAf) {
                        result.getOrNull()?.isFocusSuccessful
                    } else {
                        null
                    }
                val confirmed =
                    result.isSuccess &&
                        (!supportsAf || focusSuccessful == true)
                focusLockActive = hold && confirmed

                if (::focusReticleView.isInitialized) {
                    focusReticleView.text =
                        when {
                            !confirmed -> "TAP TO RETRY"
                            !supportsAf && hold -> "METER LOCK"
                            !supportsAf -> "METER SET"
                            hold -> "AF LOCK"
                            else -> "FOCUS OK"
                        }
                    focusReticleView.setTextColor(
                        if (confirmed) DevelopUgandaFivemods8Theme.accent else DevelopUgandaFivemods8Theme.accent
                    )
                    if (!focusLockActive) {
                        uiHandler.postDelayed(hideFocusReticleRunnable, 1200L)
                    }
                }

                if (!confirmed) {
                    runCatching { cam.cameraControl.cancelFocusAndMetering() }
                    toast("Subject focus not confirmed")
                } else if (hold) {
                    toast(if (supportsAf) "AF + METER LOCK" else "METER LOCK")
                }
                updateShotQualityGuard()
                refreshHud()
            }, ContextCompat.getMainExecutor(this))
        } catch (_: Exception) {
            focusLockActive = false
            focusAttempted = false
            focusSuccessful = null
            toast("Focus unavailable on this lens")
        }
    }

    private fun togglePersistentFocusLock(
        x: Float,
        y: Float
    ) {
        if (focusLockActive) {
            releaseFocusLockForTap()
            if (::focusReticleView.isInitialized) {
                focusReticleView.text = "AF AUTO"
                focusReticleView.visibility = View.VISIBLE
                uiHandler.removeCallbacks(hideFocusReticleRunnable)
                uiHandler.postDelayed(hideFocusReticleRunnable, 700L)
            }
            toast("AF / METER AUTO")
            refreshHud()
        } else {
            coreRequestFocus(x, y, true)
        }
    }

    private fun releaseFocusLockForTap() {
        coreFocusGeneration += 1L
        if (focusLockActive) {
            runCatching { camera?.cameraControl?.cancelFocusAndMetering() }
        }
        focusLockActive = false
        focusAttempted = false
        focusSuccessful = null
    }

    private fun focusAssistLabel(): String {
        return if (focusLockActive) {
            "AF+METER LOCK"
        } else {
            "AF AUTO"
        }
    }

    private fun audioGuardLabel(): String {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "MIC OFF"
        }

        if (
            audioStateLabel ==
                "MIC ERROR"
        ) {
            return "MIC ERROR"
        }

        if (
            recording ==
                null
        ) {
            return "MIC READY"
        }

        val level =
            audioAmplitude.coerceIn(
                0.0,
                1.0
            )

        return when {
            level <
                0.015 ->
                    "LOW"

            v255AudioHeadroomEnabled() &&
                v255AudioDbfs() > v255AudioHeadroomTargetDb().toDouble() &&
                level < 0.90 ->
                    "HEADROOM"

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

    private fun updateAudioGuard() {
        if (
            !::audioGuardView.isInitialized
        ) {
            return
        }

        if (!v254AudioMeterEnabled()) {
            audioGuardView.visibility = View.GONE
            return
        }

        if (
            operatorControlsHidden ||
            cleanModeEnabled
        ) {
            audioGuardView.visibility =
                View.GONE

            return
        }

        audioGuardView.visibility =
            View.VISIBLE

        val label =
            audioGuardLabel()

        val percent =
            (
                audioAmplitude
                    .coerceIn(
                        0.0,
                        1.0
                    ) *
                    100.0
                ).roundToInt()

        val peakPercent =
            (
                audioPeakAmplitude
                    .coerceIn(
                        0.0,
                        1.0
                    ) *
                    100.0
                ).roundToInt()

        audioGuardView.text =
            when (label) {
                "MIC READY",
                "MIC OFF",
                "MIC ERROR" ->
                    "AUDIO • $label"

                else ->
                    "AUDIO • $label • ${percent}% • PEAK ${peakPercent}% • ${v255AudioDbfsText()}"
            }

        audioGuardView.setTextColor(
            when (label) {
                "GOOD" ->
                    DevelopUgandaFivemods8Theme.contentDim

                "HOT" ->
                    DevelopUgandaFivemods8Theme.accent

                "HEADROOM" ->
                    DevelopUgandaFivemods8Theme.accent

                "CLIP RISK",
                "MIC ERROR" ->
                    DevelopUgandaFivemods8Theme.accent

                "LOW" ->
                    DevelopUgandaFivemods8Theme.accent

                else ->
                    DevelopUgandaFivemods8Theme.contentDim
            }
        )
    }

    private fun ambientLightLabel(): String {
        val lux =
            ambientLux
                ?: return "LIGHT --"

        return when {
            lux <
                25f ->
                    "DARK"

            lux <
                100f ->
                    "DIM"

            lux <
                500f ->
                    "NORMAL"

            else ->
                "BRIGHT"
        }
    }

    private fun ambientLightRecommendation(): String {
        val lux =
            ambientLux
                ?: return "SENSOR --"

        val selected =
            qualityModes[
                qualityIndex
            ]

        return when {
            lux <
                25f ->
                    if (
                        selected ==
                            "LOW LIGHT"
                    ) {
                        "LOW LIGHT ACTIVE"
                    } else {
                        "USE LOW LIGHT"
                    }

            lux <
                100f &&
                (
                    selected ==
                        "SOCIAL 60" ||
                    selected ==
                        "UHD 60" ||
                    selected ==
                        "ACTION 60"
                    ) ->
                        "30 FPS ADVISED"

            lux >
                500f &&
                (
                    selected ==
                        "SOCIAL FHD" ||
                    selected ==
                        "ACTION STAB"
                    ) ->
                        "60 FPS AVAILABLE"

            else ->
                "EXPOSURE OK"
        }
    }

    private fun updateLightAdvisor() {
        if (
            !::lightAdvisorView.isInitialized
        ) {
            return
        }

        if (
            operatorControlsHidden ||
            cleanModeEnabled
        ) {
            lightAdvisorView.visibility =
                View.GONE

            return
        }

        lightAdvisorView.visibility =
            View.VISIBLE

        val lux =
            ambientLux

        if (
            lux ==
                null
        ) {
            lightAdvisorView.text =
                "LIGHT • SENSOR --"

            lightAdvisorView.setTextColor(
                DevelopUgandaFivemods8Theme.contentDim
            )

            return
        }

        val label =
            ambientLightLabel()

        lightAdvisorView.text =
            String.format(
                Locale.US,
                "LIGHT • %s • %.0f LUX • %s",
                label,
                lux,
                ambientLightRecommendation()
            )

        lightAdvisorView.setTextColor(
            when (label) {
                "BRIGHT" ->
                    DevelopUgandaFivemods8Theme.accent

                "NORMAL" ->
                    DevelopUgandaFivemods8Theme.contentDim

                "DIM" ->
                    DevelopUgandaFivemods8Theme.accent

                else ->
                    DevelopUgandaFivemods8Theme.accent
            }
        )
    }

    private fun motionGuardLabel(): String {
        return when {
            cameraShakeScore <=
                8f ->
                    "STEADY"

            cameraShakeScore <=
                22f ->
                    "MOVING"

            else ->
                "SHAKE"
        }
    }

    private fun updateMotionGuard() {
        if (
            !::motionGuardView.isInitialized
        ) {
            return
        }

        if (
            operatorControlsHidden ||
            cleanModeEnabled ||
            !v259StabilityAssistOn
        ) {
            motionGuardView.visibility =
                View.GONE

            return
        }

        motionGuardView.visibility =
            View.VISIBLE

        val label =
            motionGuardLabel()

        motionGuardView.text =
            String.format(
                Locale.US,
                "STEADYSHOT • %s • %.0f",
                label,
                cameraShakeScore
            )

        motionGuardView.setTextColor(
            when (label) {
                "STEADY" ->
                    DevelopUgandaFivemods8Theme.contentDim

                "MOVING" ->
                    DevelopUgandaFivemods8Theme.accent

                else ->
                    DevelopUgandaFivemods8Theme.accent
            }
        )
    }

    private fun updateHorizonGuard() {
        if (
            !::horizonGuardView.isInitialized
        ) {
            return
        }

        if (
            operatorControlsHidden ||
            cleanModeEnabled ||
            !v259HorizonAssistOn
        ) {
            horizonGuardView.visibility =
                View.GONE

            return
        }

        horizonGuardView.visibility =
            View.VISIBLE

        val roll =
            phoneRollDeg

        if (roll == null) {
            horizonGuardView.rotation =
                0f

            horizonGuardView.text =
                "━━━━━━━━  HORIZON --  ━━━━━━━━"

            horizonGuardView.setTextColor(
                DevelopUgandaFivemods8Theme.contentDim
            )

            return
        }

        val absRoll =
            kotlin.math.abs(
                roll
            )

        val stateText =
            when {
                absRoll <=
                    1.0f ->
                        "LEVEL LOCK"

                absRoll <=
                    3.0f ->
                        "LEVEL NEAR"

                else ->
                    "ADJUST"
            }

        horizonGuardView.rotation =
            (
                -roll
            ).coerceIn(
                -12f,
                12f
            )

        horizonGuardView.text =
            String.format(
                Locale.US,
                "━━━━━━━━  %s  %+.1f°  ━━━━━━━━",
                stateText,
                roll
            )

        horizonGuardView.setTextColor(
            when {
                absRoll <=
                    1.0f ->
                        DevelopUgandaFivemods8Theme.contentDim

                absRoll <=
                    3.0f ->
                        DevelopUgandaFivemods8Theme.accent

                else ->
                    DevelopUgandaFivemods8Theme.accent
            }
        )
    }

    private fun startEverything() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        }

        startLocation()
        startGnssMonitor()
    }

    private fun startLocation() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val req = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            1000L
        )
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(1000L)
            .build()

        fused.requestLocationUpdates(
            req,
            locationCallback,
            Looper.getMainLooper()
        )
    }


    // Honour the existing Interview profile in BOTH CameraX negotiation and
    // Camera2 AE. Never choose a faster range merely because it is listed first.
    private fun f9InterviewFrameRateRange(info: androidx.camera.core.CameraInfo?): android.util.Range<Int>? {
        if (!isInterviewCamera() || info == null) return null
        val target = requestedVideoFps()
        return info.supportedFrameRateRanges
            .filter { it.upper == target }
            .minByOrNull { it.lower }
    }

    private fun f9AttachCaptureTelemetry(previewBuilder: Preview.Builder) {
        try {
            androidx.camera.camera2.interop.Camera2Interop.Extender(previewBuilder)
                .setSessionCaptureCallback(f9CaptureCallback)
        } catch (_: Exception) {
            // No Camera2 callback means no invented ISO/shutter/WB HUD.
        }
    }

    private fun f9AeReadout(state: Int?, locked: Boolean?): String =
        when {
            locked == true || v244ShutterAngle > 0 || v244Iso > 0 -> "LOCKED"
            state == android.hardware.camera2.CaptureResult.CONTROL_AE_STATE_CONVERGED -> "STABLE"
            state == android.hardware.camera2.CaptureResult.CONTROL_AE_STATE_SEARCHING ||
                state == android.hardware.camera2.CaptureResult.CONTROL_AE_STATE_PRECAPTURE -> "DRIFTING"
            else -> ""
        }

    private fun f9AwbReadout(state: Int?, locked: Boolean?): String =
        when {
            locked == true -> "LOCKED"
            state == android.hardware.camera2.CaptureResult.CONTROL_AWB_STATE_CONVERGED -> "STABLE"
            state == android.hardware.camera2.CaptureResult.CONTROL_AWB_STATE_SEARCHING -> "DRIFTING"
            else -> ""
        }

    // FIVEMODS 10: the phone's ambient-light sensor is not the camera
    // meter. It can be shaded by a hand or face while the lens sees a bright
    // scene, so it must never drive capture exposure. Keep CameraX AE neutral
    // unless the operator deliberately moved the existing EV control.
    private fun f9ApplyInterviewExposure() {
        if (
            !isInterviewCamera() ||
            operatorLocked ||
            focusLockActive ||
            v244ShutterAngle > 0 ||
            v244Iso > 0
        ) return
        val cam = camera ?: return
        val state = cam.cameraInfo.exposureState
        if (!state.isExposureCompensationSupported) return
        val requestedIndex =
            sceneExposureTarget.coerceIn(
                state.exposureCompensationRange.lower,
                state.exposureCompensationRange.upper
            )
        if (state.exposureCompensationIndex != requestedIndex) {
            cam.cameraControl.setExposureCompensationIndex(requestedIndex)
        }
    }

    private fun f9UpdateAudioTelemetry(amplitude: Double) {
        audioAmplitude = amplitude.coerceIn(0.0, 1.0)
        // Peak hold is the greatest actual CameraX sample seen in this take.
        // It is deliberately not decayed or smoothed.
        audioPeakAmplitude = maxOf(audioPeakAmplitude, audioAmplitude)
        val levelDbfs =
            if (audioAmplitude > 0.000001) 20.0 * log10(audioAmplitude) else -120.0
        f9AudioPeakDbfs =
            if (audioPeakAmplitude > 0.000001) 20.0 * log10(audioPeakAmplitude) else -120.0
        if (levelDbfs <= -45.0) {
            if (f9SilenceStartedAtMs == 0L) f9SilenceStartedAtMs = SystemClock.elapsedRealtime()
        } else {
            f9SilenceStartedAtMs = 0L
        }
        f9ActiveMic = f9ActiveRecordingMic()
    }

    private fun f9ActiveRecordingMic(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return null
        return try {
            val audio = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            audio.activeRecordingConfigurations
                .firstOrNull()
                ?.audioDevice
                ?.productName
                ?.toString()
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun f9ExposureEv(): Float? {
        val state = camera?.cameraInfo?.exposureState ?: return null
        return state.takeIf { it.isExposureCompensationSupported }
            ?.let { it.exposureCompensationIndex * it.exposureCompensationStep.toFloat() }
    }

    private fun f9ActualRemainingMinutes(): Long? {
        if (v255RecordedDurationNs < 1_000_000_000L || v255RecordedBytes <= 0L) return null
        return try {
            val bps = (v255RecordedBytes * 8_000_000_000L / v255RecordedDurationNs)
                .coerceAtLeast(1L)
            val free = StatFs(Environment.getExternalStorageDirectory().path).availableBytes
            ((free * 8L / bps) / 60L).coerceAtLeast(0L)
        } catch (_: Exception) {
            null
        }
    }

    private fun f9InterviewTelemetry(): DevelopUgandaInterviewHudView.Telemetry {
        val level = audioAmplitude.coerceIn(0.000001, 1.0)
        val dbfs = if (recording != null) 20.0 * log10(level) else null
        val pause = f9SilenceStartedAtMs.takeIf { it > 0L }?.let {
            (SystemClock.elapsedRealtime() - it).coerceAtLeast(0L)
        }
        return DevelopUgandaInterviewHudView.Telemetry(
            iso = f9ActualIso,
            shutterNs = f9ActualShutterNs,
            // Camera2 has no universal actual Kelvin/CCT key. Do not estimate it.
            whiteBalanceKelvin = null,
            exposureEv = f9ExposureEv(),
            aeState = f9AeReadout.takeIf { it.isNotBlank() },
            awbState = f9AwbReadout.takeIf { it.isNotBlank() },
            audioDbfs = dbfs,
            peakDbfs = f9AudioPeakDbfs,
            headroomDb = dbfs?.let { -it },
            activeMic = f9ActiveMic,
            timecode = if (recording != null) tc() else null,
            takeNumber = if (recording != null) v260ActiveTakeNumber else null,
            remainingMinutes = f9ActualRemainingMinutes(),
            batteryPercent = batteryPct(),
            thermalState = thermalStateLabel(),
            currentSilenceMs = pause
        )
    }

    private val f9InterviewTelemetryRunnable = object : Runnable {
        override fun run() {
            if (isInterviewCamera() && interviewHudView != null) {
                interviewHudView?.setTelemetry(f9InterviewTelemetry())
                uiHandler.postDelayed(this, 150L)
            }
        }
    }

    private fun f9StartInterviewTelemetry() {
        uiHandler.removeCallbacks(f9InterviewTelemetryRunnable)
        if (isInterviewCamera()) uiHandler.post(f9InterviewTelemetryRunnable)
    }

    private fun f9NewTakeId(mode: String): String {
        val page = runCatching { DevelopUgandaCameraPage.valueOf(mode.uppercase(Locale.US)) }
            .getOrDefault(currentCameraPage())
        val raw = "${page.name}_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())}"
        return DevelopUgandaFivemods12Identity.prefixedStem(page, raw)
    }

    private fun f9WeatherAtCapture(): String? {
        val wx = weather.latest
        if (wx.updatedAtMs <= 0L || wx.condition == "Weather unavailable") return null
        return buildString {
            append(wx.condition)
            wx.temperatureC?.let { append(String.format(Locale.US, " %.1fC", it)) }
            wx.humidityPct?.let { append(" RH ${it}%") }
            wx.windKmh?.let { append(String.format(Locale.US, " WIND %.1fkm/h", it)) }
        }
    }

    private fun f9CreateTakeMetadata(takeId: String) =
        DevelopUgandaFivemods9DualOutputExporter.TakeMetadata(
            takeId = takeId,
            mode = currentCameraPage().name,
            capturedAtMs = System.currentTimeMillis(),
            operator = reporterName,
            locationName = placeName.takeIf { it.isNotBlank() && it != "Locating…" },
            latitude = lat,
            longitude = lon,
            altitudeM = alt,
            weather = f9WeatherAtCapture(),
            shutterNs = f9ActualShutterNs,
            iso = f9ActualIso,
            interviewDisclaimer = isInterviewCamera(),
            statusSegment = isStatusCamera()
        )

    private fun f9FinishTakeMetadata(
        initial: DevelopUgandaFivemods9DualOutputExporter.TakeMetadata,
        interview: DevelopUgandaInterviewObservationEngine.InterviewRecordingSummary?
    ): DevelopUgandaFivemods9DualOutputExporter.TakeMetadata {
        val trend =
            if (initial.interviewDisclaimer && interviewBurnInEnabled) {
                interview?.samples
                    ?.mapNotNull { it.channels["COMPOSURE INDEX"] }
                    ?.takeLast(24)
                    ?: emptyList()
            } else {
                emptyList()
            }
        return initial.copy(
            shutterNs = f9ActualShutterNs ?: initial.shutterNs,
            iso = f9ActualIso ?: initial.iso,
            composureTrend = trend
        )
    }

    private fun f9HandleDualOutput(outcome: DevelopUgandaFivemods9DualOutputExporter.Outcome) {
        statusView.text = outcome.detail
        statusView.setTextColor(
            if (outcome.complete) DevelopUgandaFivemods8Theme.accent
            else DevelopUgandaFivemods8Theme.warning
        )
        if (!outcome.complete) {
            toast("${outcome.take.takeId}: BRAND MISSING • CLEAN is in Gallery")
            return
        }
        toast("${outcome.take.takeId}: CLEAN + BRAND saved to Gallery")
        when (outcome.take.mode) {
            "STATUS" -> outcome.brandUri?.let {
                exportAdditiveDelivery(it, "${outcome.take.takeId}_BRAND")
            }
        }
    }

    private fun f9ShowInterviewSettingsSheet() {
        val items = arrayOf(
            "CAMERA CONFIGURATION",
            "VIEW / GUIDES",
            "OPERATOR SAFETY",
            "DIRECTOR / METADATA",
            "INTERVIEW READ-OUT OPTIONS",
            "RESET CAMERA"
        )
        AlertDialog.Builder(this)
            .setTitle("INTERVIEW SETTINGS")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> f9ShowInterviewCameraSettings()
                    1 -> f9ShowInterviewViewSettings()
                    2 -> f9ShowInterviewSafetySettings()
                    3 -> f9ShowInterviewDirectorSettings()
                    4 -> f9ShowInterviewReadoutOptions()
                    5 -> resetReportCameraSettings()
                }
            }
            .setPositiveButton("DONE", null)
            .show()
    }

    private fun f9ShowInterviewCameraSettings() {
        val items = arrayOf(
            "SCENE • ${sceneModes[sceneIndex]}",
            "LOOK • ${lookModes[lookIndex]}",
            "FORMAT • ${qualityDeckLabel()}",
            "CAPTURE • ${captureModes[captureModeIndex]}",
            "COLOR • ${v229ColorDeckLabel()}"
        )
        AlertDialog.Builder(this)
            .setTitle("INTERVIEW • CAMERA")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showReportSceneDropdown(settingsButton)
                    1 -> showReportLookDropdown(settingsButton)
                    2 -> showReportQualityDropdown(settingsButton)
                    3 -> showReportCaptureDropdown(settingsButton)
                    4 -> showV233ColorDropdown(settingsButton)
                }
            }
            .setPositiveButton("DONE", null)
            .show()
    }

    private fun f9ShowInterviewViewSettings() {
        AlertDialog.Builder(this)
            .setTitle("INTERVIEW • VIEW")
            .setItems(arrayOf("VIEW", "GUIDES", "AUTO UI")) { _, which ->
                when (which) {
                    0 -> showReportViewDropdown(settingsButton)
                    1 -> showReportGuidesDropdown(settingsButton)
                    2 -> showReportAutoUiDropdown(settingsButton)
                }
            }
            .setPositiveButton("DONE", null)
            .show()
    }

    private fun f9ShowInterviewSafetySettings() {
        AlertDialog.Builder(this)
            .setTitle("INTERVIEW • OPERATOR SAFETY")
            .setItems(arrayOf("OPERATOR LOCK", "RECORDING VERIFY")) { _, which ->
                when (which) {
                    0 -> showReportLockDropdown(settingsButton)
                    1 -> showReportIntegrityDropdown(settingsButton)
                }
            }
            .setPositiveButton("DONE", null)
            .show()
    }

    private fun f9ShowInterviewDirectorSettings() {
        val items = arrayOf(
            "AUTO DIRECTOR",
            "SHOT ASSIST",
            "DIRECTOR GUIDANCE",
            "MATCH LAST SHOT",
            "CAMERA HEALTH",
            "BRAND METADATA"
        )
        AlertDialog.Builder(this)
            .setTitle("INTERVIEW • DIRECTOR / METADATA")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleAutoDirector()
                    1 -> cycleShotAssist()
                    2 -> toggleDirectorGuidance()
                    3 -> matchLastShotContinuity()
                    4 -> openV227CameraHealth()
                    5 -> openV228BrandMetadataStudio()
                }
            }
            .setPositiveButton("DONE", null)
            .show()
    }

    private fun f9ShowInterviewReadoutOptions() {
        var analysis = interviewAnalysisEnabled
        var twoShot = interviewTwoShotEnabled
        var brandTrend = interviewBurnInEnabled
        val labels = arrayOf(
            "Observed-signal read-outs",
            "Require tap-selected subject",
            "Burn composure trend in BRAND copy"
        )
        AlertDialog.Builder(this)
            .setTitle("INTERVIEW READ-OUT OPTIONS")
            .setMultiChoiceItems(labels, booleanArrayOf(analysis, twoShot, brandTrend)) { _, which, checked ->
                when (which) {
                    0 -> analysis = checked
                    1 -> twoShot = checked
                    2 -> brandTrend = checked
                }
            }
            .setPositiveButton("SAVE") { _, _ ->
                interviewAnalysisEnabled = analysis
                interviewTwoShotEnabled = twoShot
                // BRAND itself is mandatory. This governs only optional trend text.
                interviewBurnInEnabled = brandTrend
                interviewObservationEngine?.setAnalysisEnabled(analysis)
                interviewObservationEngine?.setRequireExplicitSubjectSelection(twoShot)
                interviewObservationEngine?.setBurnInEnabled(brandTrend)
                interviewHudView?.setAnalysisEnabled(analysis)
                directorOverlayView.setTrackingMode(if (twoShot) "OFF" else "PRIMARY FACE")
                saveInterviewCameraPreferences()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    private fun f9ConstrainInterviewControls() {
        if (!isInterviewCamera()) return
        listOf(
            modeRow, identityRow, reportToolsRow, reportAdvancedRow,
            reportDisplayRow, reportOutputRow, reportDirectorRow,
            zoomRow, exposureRow
        ).forEach { it.visibility = View.GONE }
        if (::previewNarrationPanel.isInitialized) previewNarrationPanel.visibility = View.GONE
        if (::audioGuardView.isInitialized) audioGuardView.visibility = View.GONE
        if (::thermalGuardView.isInitialized) thermalGuardView.visibility = View.GONE
        (settingsButton.parent as? ViewGroup)?.removeView(settingsButton)
        settingsButton.text = "SETTINGS\nINTERVIEW"
        val rail = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(settingsButton, LinearLayout.LayoutParams(dp(78), dp(46)))
        }
        root.addView(
            rail,
            FrameLayout.LayoutParams(
                dp(82),
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.END or Gravity.CENTER_VERTICAL
            ).apply { marginEnd = dp(6) }
        )
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)

        future.addListener({
            try {
                provider = future.get()
                bindCamera()
            } catch (e: Exception) {
                toast("Camera could not start")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val p = provider ?: return

        // Never tear down an active CameraX recording because a UI preference,
        // telemetry refresh, or lens event arrives while the user is shooting.
        if (recording != null) return
        coreFocusGeneration += 1L

        applyThermalSafeProfileIfNeeded()

        p.unbindAll()

        try {
            overlayEffect?.close()
        } catch (_: Exception) {
        }

        overlayEffect = null

        if (
            selectedCameraDeviceId != null &&
            !DevelopUgandaLensIntelligence.hasCamera(
                p,
                selectedCameraDeviceId
            )
        ) {
            selectedCameraDeviceId =
                null
        }

        val selector =
            DevelopUgandaLensIntelligence.selectorFor(
                p,
                selectedCameraDeviceId,
                useFront
            )

        val selectedCameraInfo =
            try {
                p.getCameraInfo(selector)
            } catch (_: Exception) {
                null
            }

        // Resolve the UI's FORMAT / HDR / stabilization choices against the
        // camera's actual CameraX capabilities before opening an encoder.
        // A recovered interrupted session starts safely in HD rather than
        // attempting the previous high-demand profile again.
        val recoverySafeProfile =
            runCatching {
                DevelopUgandaV276RecordingSafety.needsRecoveryReview(this)
            }.getOrDefault(false)
        val preferredQualities =
            if (recoverySafeProfile) {
                listOf(Quality.HD, Quality.FHD, Quality.UHD)
            } else {
                buildQualityOrder()
            }
        var selectedQuality = Quality.HD
        var selectedDynamicRange = DynamicRange.SDR
        var enableVideoStabilization = false

        if (selectedCameraInfo != null) {
            try {
                val capabilities =
                    Recorder.getVideoCapabilities(selectedCameraInfo)
                val sdrQualities =
                    capabilities.getSupportedQualities(DynamicRange.SDR)

                fun selectFrom(available: List<Quality>): Quality {
                    return preferredQualities.firstOrNull { it in available }
                        ?: listOf(Quality.FHD, Quality.HD, Quality.UHD)
                            .firstOrNull { it in available }
                        ?: available.firstOrNull()
                        ?: Quality.HD
                }

                selectedQuality = selectFrom(sdrQualities)

                if (
                    !recoverySafeProfile &&
                    wantsVideoHdr() &&
                    capabilities.supportedDynamicRanges.contains(DynamicRange.HLG_10_BIT)
                ) {
                    val hdrQualities =
                        capabilities.getSupportedQualities(DynamicRange.HLG_10_BIT)
                    if (hdrQualities.isNotEmpty()) {
                        selectedQuality = selectFrom(hdrQualities)
                        selectedDynamicRange = DynamicRange.HLG_10_BIT
                    }
                }

                enableVideoStabilization =
                    !recoverySafeProfile &&
                        wantsVideoStabilization() &&
                        capabilities.isStabilizationSupported
            } catch (_: Exception) {
                // Keep the known-compatible SDR/HD profile below.
                selectedQuality = Quality.HD
                selectedDynamicRange = DynamicRange.SDR
                enableVideoStabilization = false
            }
        }

        coreDeliveredBitrateBps = coreBitrateFor(selectedQuality)
        val deliveredQualityLabel = coreQualityLabel(selectedQuality)
        coreDeliveredProfileLabel = when {
            recoverySafeProfile -> "RECOVERY SAFE • $deliveredQualityLabel"
            preferredQualities.firstOrNull() == selectedQuality ->
                "$deliveredQualityLabel DEVICE"
            else -> "${qualityDeckLabel()} → $deliveredQualityLabel"
        }
        activeVideoFpsLabel =
            if (qualityModes[qualityIndex] == "LOW LIGHT") {
                "AUTO LOW-LIGHT FPS"
            } else {
                "AUTO FPS"
            }
        activeVideoStabilizationLabel =
            when {
                enableVideoStabilization -> "STAB ON"
                wantsVideoStabilization() -> "STAB UNSUPPORTED"
                else -> "STAB OFF"
            }
        activeVideoDynamicRangeLabel =
            when {
                selectedDynamicRange == DynamicRange.HLG_10_BIT -> "HLG10 HDR"
                wantsVideoHdr() -> "SDR HDR-FALLBACK"
                else -> "SDR"
            }
        activeVideoAspectLabel =
            DevelopUgandaFieldIntelligencePanel.activeFormatLabel(this)

        val previewBuilder = Preview.Builder()
        f9AttachCaptureTelemetry(previewBuilder)
        val preview = previewBuilder
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val photoMode =
            captureModes[captureModeIndex] == "PHOTO"

        videoCapture = null
        imageCapture = null

        if (photoMode) {
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(
                    ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                )
                .setJpegQuality(100)
                .build()
        } else {
            fun buildCoreVideoCapture(
                quality: Quality,
                dynamicRange: DynamicRange,
                stabilization: Boolean,
                bitrate: Int
            ): VideoCapture<Recorder> {
                val recorderBuilder =
                    Recorder.Builder()
                        .setQualitySelector(
                            QualitySelector.from(quality)
                        )
                        .setTargetVideoEncodingBitRate(bitrate)

                if (additiveModeProfile()?.portraitOutput == true) {
                    // CameraX uses the same 16:9 sensor crop in portrait,
                    // yielding a 1080×1920 rotated MP4 on a portrait device.
                    recorderBuilder.setAspectRatio(AspectRatio.RATIO_16_9)
                }

                val recorder = recorderBuilder.build()

                val videoBuilder =
                    VideoCapture.Builder(recorder)

                if (isInterviewCamera()) {
                    val frameRange = f9InterviewFrameRateRange(selectedCameraInfo)
                    if (frameRange != null) {
                        videoBuilder.setTargetFrameRate(frameRange)
                    }
                    android.util.Log.i("DU_INTERVIEW_REPAIR", "CameraX FPS request: $frameRange; profile: ${requestedVideoFps()}")
                }

                if (dynamicRange != DynamicRange.SDR) {
                    videoBuilder.setDynamicRange(dynamicRange)
                }
                if (stabilization) {
                    videoBuilder.setVideoStabilizationEnabled(true)
                }
                return videoBuilder.build()
            }

            videoCapture = try {
                buildCoreVideoCapture(
                    selectedQuality,
                    selectedDynamicRange,
                    enableVideoStabilization,
                    coreDeliveredBitrateBps
                )
            } catch (_: Exception) {
                // An encoder/profile negotiation failure gets one isolated
                // fallback to SDR HD. It does not alter the UI or any project
                // setting; it only protects the next REC attempt.
                coreDeliveredBitrateBps = coreBitrateFor(Quality.HD)
                coreDeliveredProfileLabel = "SAFE FALLBACK • 720"
                activeVideoFpsLabel = "AUTO FPS"
                activeVideoStabilizationLabel = "STAB OFF"
                activeVideoDynamicRangeLabel = "SDR SAFE"
                buildCoreVideoCapture(
                    Quality.HD,
                    DynamicRange.SDR,
                    false,
                    coreDeliveredBitrateBps
                )
            }
        }

        // V280/5 VIDEO PIPELINE SAFE MODE:
        // Keep the screen-space camera UI, but do not attach OverlayEffect to VIDEO_CAPTURE.
        // The recorded-frame effect becomes active only when REC starts and can destabilize
        // the encoder/surface pipeline on some devices. Photo overlay remains available.
        var session =
            if (photoMode) {
                overlayEffect = OverlayEffect(
                    CameraEffect.IMAGE_CAPTURE,
                    0,
                    Handler(Looper.getMainLooper())
                ) { throwable ->
                    toast(
                        "Overlay warning: ${throwable.message ?: "unknown"}"
                    )
                }.also { effect ->
                    effect.setOnDrawListener { frame ->
                        runCatching { drawReporterOverlay(frame) }
                        true
                    }
                }

                SessionConfig.Builder(
                    preview,
                    imageCapture!!
                )
                    .addEffect(
                        overlayEffect!!
                    )
                    .build()
            } else {
                // Do not burn graphics into VIDEO_CAPTURE in the stability build.
                overlayEffect = null
                SessionConfig.Builder(
                    preview,
                    videoCapture!!
                )
                    .build()
            }

        try {
            camera =
                if (photoMode) {
                    p.bindToLifecycle(
                        this,
                        selector,
                        session
                    )
                } else {
                    p.bindToLifecycle(
                        this,
                        selector,
                        preview,
                        videoCapture!!
                    )
                }

            torchOn = false
            torchButton.text = "LIGHT\nOFF"

            focusLockActive =
                false

            syncCameraRanges()
            applyScenePreset()
            // Apply the current manual/auto, white-balance and supported FPS
            // request only after CameraX has bound a real camera session.
            if (!photoMode) {
                v244ApplyCamera2Controls()
                v246ApplyCinemaImageEngine()
                v247ApplyAdaptiveDetail()
                f9ApplyInterviewExposure()
                v255RestoreAutoFocus()
            }

            statusView.text =
                if (photoMode) "PHOTO READY" else "STBY • $coreDeliveredProfileLabel"
            statusView.setTextColor(DevelopUgandaFivemods8Theme.record)
            recordButton.text =
                if (photoMode) "● PHOTO" else "● RECORD"

            if (
                ::reportRecordState.isInitialized
            ) {
                reportRecordState.setRecordingState(
                    false
                )
            }

            refreshHud()
        } catch (e: Exception) {
            if (
                selectedCameraDeviceId !=
                    null
            ) {
                val failedId =
                    selectedCameraDeviceId

                selectedCameraDeviceId =
                    null

                saveReportCameraPreferences()

                toast(
                    "Camera ID $failedId cannot use this capture profile • returning to ${if (useFront) "front" else "back"} camera"
                )

                uiHandler.post {
                    bindCamera()
                }
            } else {
                toast(
                    "Selected camera is unavailable"
                )
            }
        }
    }

        private fun drawReporterOverlay(
        frame: Frame
    ) {
        val c =
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

        c.drawColor(
            Color.TRANSPARENT,
            android.graphics.PorterDuff.Mode.CLEAR
        )

        // V271 output policy: CLEAN is screen-only; REPORTER uses the existing
        // selected report/metadata overlay; BRANDED burns only a small brand mark.
        val v271OutputMode = DevelopUgandaV271LiveCoach.outputMode(this)
        if (v271OutputMode == DevelopUgandaV271LiveCoach.MODE_CLEAN) {
            return
        }

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
                crop.height()
                    .toFloat()
            } else {
                crop.width()
                    .toFloat()
            }

        val finalHeight =
            if (
                rotation ==
                    90 ||
                rotation ==
                    270
            ) {
                crop.width()
                    .toFloat()
            } else {
                crop.height()
                    .toFloat()
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

        val finalToBuffer =
            Matrix()

        if (
            !finalToBuffer.setPolyToPoly(
                source,
                0,
                destination,
                0,
                4
            )
        ) {
            return
        }

        c.save()
        c.concat(
            finalToBuffer
        )

        if (v271OutputMode == DevelopUgandaV271LiveCoach.MODE_BRANDED) {
            val uBrand = minOf(finalWidth, finalHeight) / 1000f
            val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 25f * uBrand
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val x = finalWidth * 0.045f
            val yBrand = finalHeight * 0.955f
            val back = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66031829 }
            val pad = 10f * uBrand
            val width = brandPaint.measureText("develop.uganda")
            c.drawRoundRect(x - pad, yBrand - brandPaint.textSize - pad, x + width + pad, yBrand + pad, 12f * uBrand, 12f * uBrand, back)
            c.drawText("develop.uganda", x, yBrand, brandPaint)
            c.restore()
            return
        }

        drawCreativeLook(
            c,
            finalWidth,
            finalHeight
        )

        val u =
            minOf(
                finalWidth,
                finalHeight
            ) /
                1000f *
                reportHudScales[
                    reportHudSizeIndex
                ]

        val brandConfig =
            DevelopUgandaBrandMetadataStore
                .snapshot(
                    this
                )

        val safeLeft =
            finalWidth *
                0.050f

        val safeTop =
            finalHeight *
                0.100f

        val maxWidth =
            finalWidth *
                0.56f

        var y =
            safeTop

        val railStartY =
            y -
                (
                    14f *
                        u
                    )

        val telemetryPanel =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    0x30000000

                style =
                    Paint.Style.FILL
            }

        c.drawRoundRect(
            safeLeft -
                (
                    14f *
                        u
                    ),
            safeTop -
                (
                    26f *
                        u
                    ),
            safeLeft +
                maxWidth +
                (
                    14f *
                        u
                    ),
            safeTop +
                (
                    brandConfig
                        .reportPanelHeightUnits() *
                        u
                    ),
            16f *
                u,
            16f *
                u,
            telemetryPanel
        )

        val text =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                typeface =
                    Typeface.create(
                        Typeface.MONOSPACE,
                        Typeface.BOLD
                    )

            }

        val rail =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                color =
                    if (
                        recording !=
                            null
                    ) {
                        0xFFFF4138.toInt()
                    } else {
                        0xFFD8B85B.toInt()
                    }

                strokeWidth =
                    2.3f *
                        u
            }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .BRAND
            )
        ) {
            text.color =
                0xFFD8B85B.toInt()

            text.textSize =
                34f *
                    u

            drawStrongRecordedText(
                c,
                brandConfig.displayName,
                safeLeft,
                y,
                text
            )

            if (
                brandConfig.organization
                    .isNotBlank()
            ) {
                y +=
                    18f *
                        u

                text.color =
                    Color.WHITE

                text.textSize =
                    13.0f *
                        u

                drawFitText(
                    c,
                    brandConfig.organization,
                    safeLeft,
                    y,
                    maxWidth,
                    text,
                    10.5f *
                        u
                )
            }

            y +=
                22f *
                    u
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .VERSION
            )
        ) {
            text.color =
                if (
                    reportDisplayMode ==
                        "LIVE EFFECT"
                ) {
                    0xFFFF5A52.toInt()
                } else {
                    Color.WHITE
                }

            text.textSize =
                15.8f *
                    u

            drawFitText(
                c,
                "${sceneTag()} • V259 PRO CAM",
                safeLeft,
                y,
                maxWidth,
                text,
                12.4f *
                    u
            )

            y +=
                18f *
                    u
        }

        // The V227 instruments remain, but V228 lets the user decide
        // which of them is permanently burned into new saved media.
        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .COMPASS
            )
        ) {
            drawCompassInstrument(
                c,
                finalWidth *
                    0.80f,
                finalHeight *
                    0.875f,
                43f *
                    u,
                u
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .AUDIO
            )
        ) {
            drawAudioMeterInstrument(
                c,
                finalWidth *
                    0.705f,
                finalHeight *
                    0.815f,
                finalWidth *
                    0.15f,
                10f *
                    u,
                u
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .HORIZON
            )
        ) {
            drawLevelInstrument(
                c,
                finalWidth *
                    0.80f,
                finalHeight *
                    0.935f,
                92f *
                    u,
                28f *
                    u,
                u
            )
        }

        text.color =
            if (
                recording !=
                    null
            ) {
                0xFFFF4138.toInt()
            } else {
                Color.WHITE
            }

        text.textSize =
            18.0f *
                u

        val recState =
            when {
                recording !=
                    null ->
                        "● REC"

                captureModes[
                    captureModeIndex
                ] ==
                    "PHOTO" ->
                        "● PHOTO"

                else ->
                    "STBY"
            }

        val stateParts =
            mutableListOf(
                recState,
                "TC ${tc()}"
            )

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .VERSION
            )
        ) {
            stateParts.add(
                "V259 PRO CAM"
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .THERMAL
            )
        ) {
            stateParts.add(
                "THERM ${thermalStateLabel()}"
            )
        }

        drawFitText(
            c,
            stateParts.joinToString(
                "   •   "
            ),
            safeLeft,
            y,
            maxWidth,
            text,
            14.2f *
                u
        )

        y +=
            19f *
                u

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .CAMERA_MODE
            )
        ) {
            text.color =
                cameraExperienceAccentColor()

            text.textSize =
                15.3f *
                    u

            drawFitText(
                c,
                "CAMERA • ${cameraExperienceShortLabel()}",
                safeLeft,
                y,
                maxWidth,
                text,
                13.6f *
                    u
            )

            y +=
                18f *
                    u

            text.color =
                reportModeAccentColor()

            text.textSize =
                15.2f *
                    u

            drawFitText(
                c,
                "MODE • ${qualityModes[qualityIndex]}   •   SCENE ${sceneModes[sceneIndex]}   •   LOOK ${lookModes[lookIndex]}   •   COLOR $v229ColorOverlayLabel",
                safeLeft,
                y,
                maxWidth,
                text,
                12.8f *
                    u
            )

            y +=
                18f *
                    u

            text.color =
                0xFFAEBDEB.toInt()

            text.textSize =
                14.6f *
                    u

            drawFitText(
                c,
                autoDirectorStateText(),
                safeLeft,
                y,
                maxWidth,
                text,
                12.6f *
                    u
            )

            y +=
                20f *
                    u
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .REPORTER
            ) ||
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .STORY
            )
        ) {
            val identityParts =
                mutableListOf<String>()

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .REPORTER
                )
            ) {
                identityParts.add(
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
                identityParts.add(
                    "STORY • $storyId"
                )
            }

            text.color =
                Color.WHITE

            text.textSize =
                14.4f *
                    u

            drawFitText(
                c,
                identityParts.joinToString(
                    "   |   "
                ),
                safeLeft,
                y,
                maxWidth,
                text,
                11.8f *
                    u
            )

            y +=
                20f *
                    u
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .DATE_TIME
            )
        ) {
            text.color =
                Color.WHITE

            text.textSize =
                16.0f *
                    u

            drawFitText(
                c,
                "LOCAL ${clock.format(Date())}   |   UTC ${utcClockText()}",
                safeLeft,
                y,
                maxWidth,
                text,
                13.6f *
                    u
            )

            y +=
                20f *
                    u
        }

        fun section(
            heading: String,
            value: String,
            accent: Int =
                0xFF9FD9FF.toInt(),
            valueColor: Int =
                0xFF83C7D4.toInt()
        ) {
            text.color =
                accent

            text.textSize =
                16.8f *
                    u

            drawFitText(
                c,
                heading,
                safeLeft,
                y,
                maxWidth,
                text,
                15.0f *
                    u
            )

            y +=
                16f *
                    u

            text.color =
                valueColor

            text.textSize =
                15.2f *
                    u

            drawFitText(
                c,
                value,
                safeLeft,
                y,
                maxWidth,
                text,
                12.9f *
                    u
            )

            y +=
                20f *
                    u
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .LOCATION
            )
        ) {
            section(
                "LOCATION",
                placeName
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .GPS_COORDS
            ) ||
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .ALTITUDE
            )
        ) {
            val positionParts =
                mutableListOf<String>()

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .GPS_COORDS
                )
            ) {
                positionParts.add(
                    "LAT " +
                        (
                            lat?.let {
                                String.format(
                                    Locale.US,
                                    "%.5f",
                                    it
                                )
                            } ?: "--"
                        )
                )

                positionParts.add(
                    "LON " +
                        (
                            lon?.let {
                                String.format(
                                    Locale.US,
                                    "%.5f",
                                    it
                                )
                            } ?: "--"
                        )
                )
            }

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .ALTITUDE
                )
            ) {
                positionParts.add(
                    "ALT " +
                        (
                            alt?.let {
                                String.format(
                                    Locale.US,
                                    "%.0fm",
                                    it
                                )
                            } ?: "--"
                        )
                )
            }

            section(
                "POSITION",
                positionParts.joinToString(
                    " • "
                )
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .GPS_ACCURACY
            )
        ) {
            val fixAge =
                if (
                    lastGpsUpdateMs >
                        0L
                ) {
                    (
                        System.currentTimeMillis() -
                            lastGpsUpdateMs
                        ) /
                        1000f
                } else {
                    null
                }

            section(
                "GPS STATUS",
                buildString {
                    append(
                        accuracy?.let {
                            String.format(
                                Locale.US,
                                "ACC ±%.0fm",
                                it
                            )
                        } ?: "ACC --"
                    )

                    append(
                        " • SAT ${gnssSatellitesUsed}/${gnssSatellitesVisible}"
                    )

                    append(
                        " • FIX " +
                            (
                                fixAge?.let {
                                    String.format(
                                        Locale.US,
                                        "%.1fs",
                                        it
                                    )
                                } ?: "--"
                            )
                    )
                }
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .COMPASS
            ) ||
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .SPEED_MOTION
            )
        ) {
            val navParts =
                mutableListOf<String>()

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .COMPASS
                )
            ) {
                navParts.add(
                    "COMP " +
                        (
                            compassAzimuthDeg?.let {
                                String.format(
                                    Locale.US,
                                    "%.0f°",
                                    it
                                )
                            } ?: "--"
                        )
                )

                navParts.add(
                    "GPS HDG " +
                        (
                            heading?.let {
                                String.format(
                                    Locale.US,
                                    "%.0f°",
                                    it
                                )
                            } ?: "--"
                        )
                )
            }

            if (
                brandConfig.show(
                    DevelopUgandaBrandMetadataStore
                        .Tag
                        .SPEED_MOTION
                )
            ) {
                navParts.add(
                    "SPD " +
                        (
                            speedKmh?.let {
                                String.format(
                                    Locale.US,
                                    "%.1fkm/h",
                                    it
                                )
                            } ?: "--"
                        )
                )

                navParts.add(
                    motionGuardLabel()
                )

                navParts.add(
                    String.format(
                        Locale.US,
                        "DIST %.0fm",
                        distanceTravelledM
                    )
                )
            }

            section(
                "NAVIGATION",
                navParts.joinToString(
                    " • "
                )
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .HORIZON
            )
        ) {
            section(
                "LEVEL",
                orientationOverlay(),
                Color.WHITE,
                Color.WHITE
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .WEATHER
            )
        ) {
            section(
                "WEATHER",
                weatherOverlay()
                    .removePrefix(
                        "WX "
                    )
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .AUDIO
            )
        ) {
            section(
                "AUDIO",
                audioLevelOverlay(),
                0xFF83B995.toInt(),
                0xFF83B995.toInt()
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .BATTERY_STORAGE
            )
        ) {
            val battery =
                batteryPct()

            val free =
                freeStorageGb()

            section(
                "DEVICE",
                "BAT " +
                    (
                        battery?.let {
                            "$it%"
                        } ?: "--"
                    ) +
                    " • FREE " +
                    (
                        free?.let {
                            "${it}GB"
                        } ?: "--"
                    ),
                0xFF83B995.toInt(),
                0xFF83B995.toInt()
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .NETWORK
            )
        ) {
            section(
                "NETWORK",
                networkType() +
                    " • " +
                    (
                        estimatedUploadKbps?.let {
                            "UP~${it}kbps"
                        } ?: "UP~--"
                    ),
                0xFF83B995.toInt(),
                0xFF83B995.toInt()
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .SHOT_GUARD
            )
        ) {
            val warnings =
                shotQualityWarnings()

            section(
                "SHOT GUARD",
                if (
                    warnings.isEmpty()
                ) {
                    "READY"
                } else {
                    warnings.joinToString(
                        " • "
                    )
                },
                if (
                    warnings.isEmpty()
                ) {
                    0xFF83B995.toInt()
                } else {
                    0xFFD8B85B.toInt()
                },
                if (
                    warnings.isEmpty()
                ) {
                    0xFF83B995.toInt()
                } else {
                    0xFFD8B85B.toInt()
                }
            )
        }

        if (
            brandConfig.show(
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .INTEGRITY
            )
        ) {
            text.color =
                0xFFAEBDEB.toInt()

            text.textSize =
                13.0f *
                    u

            drawFitText(
                c,
                "INTEGRITY • " +
                    if (
                        integrityEnabled
                    ) {
                        "SHA-256 STORY PACKAGE"
                    } else {
                        "OFF"
                    },
                safeLeft,
                y,
                maxWidth,
                text,
                10.5f *
                    u
            )

            y +=
                18f *
                    u
        }

        val credit =
            brandConfig.creditLine()

        if (
            credit.isNotBlank()
        ) {
            text.color =
                0xFFAEB7C7.toInt()

            text.textSize =
                10.0f *
                    u

            drawFitText(
                c,
                credit,
                safeLeft,
                y,
                maxWidth,
                text,
                8.4f *
                    u
            )

            y +=
                14f *
                    u
        }

        val railEndY =
            y +
                (
                    6f *
                        u
                    )

        c.drawLine(
            safeLeft -
                (
                    8f *
                        u
                    ),
            railStartY,
            safeLeft -
                (
                    8f *
                        u
                    ),
            railEndY,
            rail
        )

        c.drawLine(
            safeLeft -
                (
                    8f *
                        u
                    ),
            railEndY,
            safeLeft +
                (
                    7f *
                        u
                    ),
            railEndY,
            rail
        )

        c.restore()
    }



    private fun drawStrongRecordedText(
        canvas: Canvas,
        value: String,
        x: Float,
        y: Float,
        paint: Paint
    ) {
        drawReportTextBackplate(
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
                reportHudOutlineScale()

        paint.color =
            reportHudOutlineColor()

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

    private fun instrumentStateColor(): Int {
        val acc = accuracy

        return when {
            acc == null ->
                DevelopUgandaFivemods8Theme.record

            acc <= 8f ->
                DevelopUgandaFivemods8Theme.accent

            acc <= 25f ->
                DevelopUgandaFivemods8Theme.accent

            else ->
                DevelopUgandaFivemods8Theme.record
        }
    }

    private fun drawCompassInstrument(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        u: Float
    ) {
        val stateColor =
            instrumentStateColor()

        val ring =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.outline
                style =
                    Paint.Style.STROKE
                strokeWidth =
                    2.0f * u
            }

        val accentPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stateColor
                style =
                    Paint.Style.STROKE
                strokeWidth =
                    2.0f * u
            }

        val tickPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.content
                strokeWidth =
                    1.4f * u
            }

        val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DevelopUgandaFivemods8Theme.content
                typeface = Typeface.create(
                    Typeface.MONOSPACE,
                    Typeface.BOLD
                )
                textAlign =
                    Paint.Align.CENTER
                textSize =
                    11.0f * u
            }

        val valuePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.accent
                typeface = Typeface.create(
                    Typeface.MONOSPACE,
                    Typeface.BOLD
                )
                textAlign =
                    Paint.Align.CENTER
                textSize =
                    9.5f * u
            }

        canvas.drawCircle(
            centerX,
            centerY,
            radius,
            ring
        )
        canvas.drawCircle(
            centerX,
            centerY,
            radius - (4f * u),
            accentPaint
        )

        val headingValue =
            compassAzimuthDeg
        val headingDeg =
            headingValue ?: 0f

        for (index in 0 until 24) {
            val absoluteDeg =
                index * 15f
            val relativeDeg =
                absoluteDeg - headingDeg

            val rad =
                Math.toRadians(
                    (relativeDeg - 90f)
                        .toDouble()
                )

            val outer =
                radius - (3f * u)

            val inner =
                when {
                    index % 6 == 0 ->
                        radius - (12f * u)

                    index % 3 == 0 ->
                        radius - (9f * u)

                    else ->
                        radius - (6f * u)
                }

            val x1 =
                centerX +
                    (cos(rad) * inner)
                        .toFloat()
            val y1 =
                centerY +
                    (sin(rad) * inner)
                        .toFloat()
            val x2 =
                centerX +
                    (cos(rad) * outer)
                        .toFloat()
            val y2 =
                centerY +
                    (sin(rad) * outer)
                        .toFloat()

            canvas.drawLine(
                x1,
                y1,
                x2,
                y2,
                tickPaint
            )
        }

        listOf(
            "N" to 0f,
            "E" to 90f,
            "S" to 180f,
            "W" to 270f
        ).forEach { item ->
            val relativeDeg =
                item.second - headingDeg

            val rad =
                Math.toRadians(
                    (relativeDeg - 90f)
                        .toDouble()
                )

            val labelRadius =
                radius - (20f * u)

            val x =
                centerX +
                    (cos(rad) * labelRadius)
                        .toFloat()
            val y =
                centerY +
                    (sin(rad) * labelRadius)
                        .toFloat() +
                    (3f * u)

            canvas.drawText(
                item.first,
                x,
                y,
                labelPaint
            )
        }

        val pointer =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.accent
                style =
                    Paint.Style.FILL
            }

        val triangle =
            android.graphics.Path().apply {
                moveTo(
                    centerX,
                    centerY - radius -
                        (4f * u)
                )
                lineTo(
                    centerX - (5f * u),
                    centerY - radius +
                        (5f * u)
                )
                lineTo(
                    centerX + (5f * u),
                    centerY - radius +
                        (5f * u)
                )
                close()
            }

        canvas.drawPath(
            triangle,
            pointer
        )

        val headingText =
            headingValue?.let {
                String.format(
                    Locale.US,
                    "%.0f° %s",
                    it,
                    cardinalDirection(it)
                )
            } ?: "--° --"

        canvas.drawText(
            headingText,
            centerX,
            centerY + (3f * u),
            valuePaint
        )

        valuePaint.textSize =
            9.4f * u
        valuePaint.color =
            stateColor

        canvas.drawText(
            "COMPASS",
            centerX,
            centerY + (16f * u),
            valuePaint
        )
    }

    private fun drawAudioMeterInstrument(
        canvas: Canvas,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        u: Float
    ) {
        val bg =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.surfaceScrim(53)
                style =
                    Paint.Style.FILL
            }

        val outline =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.contentScrim(191)
                style =
                    Paint.Style.STROKE
                strokeWidth =
                    1f * u
            }

        canvas.drawRoundRect(
            left,
            top,
            left + width,
            top + height,
            height / 2f,
            height / 2f,
            bg
        )
        canvas.drawRoundRect(
            left,
            top,
            left + width,
            top + height,
            height / 2f,
            height / 2f,
            outline
        )

        val level =
            if (recording != null) {
                audioAmplitude
                    .coerceIn(
                        0.0,
                        1.0
                    )
                    .toFloat()
            } else {
                0f
            }

        val fillColor =
            when {
                level >= 0.92f ->
                    DevelopUgandaFivemods8Theme.record

                level >= 0.72f ->
                    DevelopUgandaFivemods8Theme.accent

                else ->
                    DevelopUgandaFivemods8Theme.accent
            }

        val fill =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = fillColor
                style =
                    Paint.Style.FILL
            }

        val inner =
            2f * u
        val usable =
            (width - (inner * 2f))
                .coerceAtLeast(0f)

        canvas.drawRoundRect(
            left + inner,
            top + inner,
            left + inner +
                (usable * level),
            top + height - inner,
            (height - (inner * 2f)) / 2f,
            (height - (inner * 2f)) / 2f,
            fill
        )

        val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = DevelopUgandaFivemods8Theme.content
                typeface = Typeface.create(
                    Typeface.MONOSPACE,
                    Typeface.BOLD
                )
                textSize =
                    7.2f * u
            }

        val pct =
            (level * 100f)
                .roundToInt()

        canvas.drawText(
            "MIC $pct%",
            left,
            top - (4f * u),
            label
        )
    }

    private fun drawLevelInstrument(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        height: Float,
        u: Float
    ) {
        val roll =
            phoneRollDeg
        val pitch =
            phonePitchDeg

        val stateColor =
            when {
                roll == null ->
                    DevelopUgandaFivemods8Theme.record

                kotlin.math.abs(roll) <= 1f ->
                    DevelopUgandaFivemods8Theme.accent

                kotlin.math.abs(roll) <= 3f ->
                    DevelopUgandaFivemods8Theme.accent

                else ->
                    DevelopUgandaFivemods8Theme.record
            }

        val framePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.contentScrim(191)
                style =
                    Paint.Style.STROKE
                strokeWidth =
                    1f * u
            }

        val levelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stateColor
                strokeWidth =
                    2f * u
            }

        val halfW =
            width / 2f
        val halfH =
            height / 2f

        canvas.drawRoundRect(
            centerX - halfW,
            centerY - halfH,
            centerX + halfW,
            centerY + halfH,
            5f * u,
            5f * u,
            framePaint
        )

        val rollValue =
            (roll ?: 0f)
                .coerceIn(
                    -30f,
                    30f
                )

        val rad =
            Math.toRadians(
                rollValue.toDouble()
            )

        val lineHalf =
            halfW - (10f * u)

        val dx =
            (cos(rad) * lineHalf)
                .toFloat()
        val dy =
            (sin(rad) * lineHalf)
                .toFloat()

        canvas.drawLine(
            centerX - dx,
            centerY - dy,
            centerX + dx,
            centerY + dy,
            levelPaint
        )

        canvas.drawLine(
            centerX - (6f * u),
            centerY,
            centerX + (6f * u),
            centerY,
            framePaint
        )

        val label =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stateColor
                typeface = Typeface.create(
                    Typeface.MONOSPACE,
                    Typeface.BOLD
                )
                textAlign =
                    Paint.Align.CENTER
                textSize =
                    7.0f * u
            }

        val value =
            String.format(
                Locale.US,
                "ROLL %s • TILT %s",
                roll?.let {
                    String.format(
                        Locale.US,
                        "%.1f°",
                        it
                    )
                } ?: "--",
                pitch?.let {
                    String.format(
                        Locale.US,
                        "%.1f°",
                        it
                    )
                } ?: "--"
            )

        canvas.drawText(
            value,
            centerX,
            centerY + halfH +
                (10f * u),
            label
        )
    }

    private fun nextClipSequence(): Int {
        val prefs =
            duSharedPreferences(
                "develop_uganda_reporter",
                Context.MODE_PRIVATE
            )

        val next =
            prefs.getInt(
                "clip_sequence",
                0
            ) + 1

        prefs.edit()
            .putInt(
                "clip_sequence",
                next
            )
            .apply()

        return next
    }

    private fun clipSequenceText(): String {
        return String.format(
            Locale.US,
            "%04d",
            clipSequence
        )
    }

    private fun utcClockText(): String {
        val iso = Instant.now().toString()
        return if (iso.length >= 19) {
            iso.substring(11, 19) + "Z"
        } else {
            iso
        }
    }

    private fun coordinatePrimaryOverlay(): String {
        return if (lat != null && lon != null) {
            String.format(
                Locale.US,
                "GPS LIVE • LAT %.5f • LON %.5f",
                lat,
                lon
            )
        } else {
            "GPS ACQUIRING • LAT -- • LON --"
        }
    }

    private fun movementOverlay(): String {
        val altText =
            alt?.let {
                String.format(
                    Locale.US,
                    "ALT %.0fm",
                    it
                )
            } ?: "ALT --"

        val accText =
            accuracy?.let {
                String.format(
                    Locale.US,
                    "ACC ±%.0fm",
                    it
                )
            } ?: "ACC --"

        val headingText =
            heading?.let {
                String.format(
                    Locale.US,
                    "HDG %.0f° %s",
                    it,
                    cardinalDirection(it)
                )
            } ?: "HDG --"

        val speedText =
            speedKmh?.let {
                String.format(
                    Locale.US,
                    "SPD %.1fkm/h",
                    it
                )
            } ?: "SPD --"

        val distanceText =
            if (recording != null) {
                if (distanceTravelledM >= 1000f) {
                    String.format(
                        Locale.US,
                        "DIST %.2fkm",
                        distanceTravelledM / 1000f
                    )
                } else {
                    String.format(
                        Locale.US,
                        "DIST %.0fm",
                        distanceTravelledM
                    )
                }
            } else {
                "DIST STBY"
            }

        return "$altText • $accText • $headingText • $speedText • ${motionLabel()} • ${gpsQualityLabel()} • FIX ${gpsFixAgeText()} • $distanceText"
    }

    private fun gpsFixAgeText(): String {
        if (lastGpsUpdateMs <= 0L) {
            return "--"
        }

        val ageMs =
            (
                System.currentTimeMillis() -
                    lastGpsUpdateMs
                ).coerceAtLeast(0L)

        return if (ageMs < 10_000L) {
            String.format(
                Locale.US,
                "%.1fs",
                ageMs / 1000f
            )
        } else {
            "${ageMs / 1000L}s"
        }
    }

    private fun gpsQualityLabel(): String {
        val a = accuracy
            ?: return "FIX WAIT"

        return when {
            a <= 8f -> "FIX EXCELLENT"
            a <= 20f -> "FIX GOOD"
            a <= 50f -> "FIX FAIR"
            else -> "FIX LOW"
        }
    }

    private fun motionLabel(): String {
        val s = speedKmh
            ?: return "MOTION --"

        return when {
            s < 1.2f -> "STILL"
            s < 8f -> "WALK"
            s < 25f -> "MOVE"
            else -> "VEHICLE"
        }
    }

    private fun cardinalDirection(
        degrees: Float
    ): String {
        val dirs = arrayOf(
            "N",
            "NE",
            "E",
            "SE",
            "S",
            "SW",
            "W",
            "NW"
        )

        val normalized =
            (
                (degrees % 360f) +
                    360f
                ) % 360f

        val index =
            (
                (normalized + 22.5f) /
                    45f
                ).toInt() % 8

        return dirs[index]
    }

    override fun onResume() {
        super.onResume()
        broadcastCameraChrome?.onResume()
        f12CameraShell?.onResume()
        f9StartInterviewTelemetry()

        refreshV228BrandUi()
        lastV233ColorMonitorKey = ""
        refreshV233ColorMonitor()

        rotationVectorSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        }

        ambientLightSensor?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
            !thermalListenerRegistered
        ) {
            try {
                powerManager.addThermalStatusListener(
                    thermalStatusListener
                )

                thermalStatus =
                    powerManager.currentThermalStatus

                thermalListenerRegistered =
                    true
            } catch (_: Exception) {
                thermalListenerRegistered =
                    false
            }
        }
    }

    override fun onPause() {
        if (recording != null) {
            cancelCrashSafeContinuation()
            cancelStatusSegmentation()
            runCatching {
                DevelopUgandaV276RecordingSafety.addEvent(this, "INTERRUPTION • APP BACKGROUND/LOCK • FINALISE CLEAN")
                statusView.text = "INTERRUPTED • FINALISING CLEAN"
                recordButton.text = "SAVING…"
                recordButton.isEnabled = false
                recording?.stop()
            }
        }
        f12CameraShell?.onPause()
        broadcastCameraChrome?.onPause()
        uiHandler.removeCallbacks(f9InterviewTelemetryRunnable)
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
            thermalListenerRegistered
        ) {
            try {
                powerManager.removeThermalStatusListener(
                    thermalStatusListener
                )
            } catch (_: Exception) {
            }

            thermalListenerRegistered =
                false
        }

        super.onPause()
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
    }

    override fun onSensorChanged(
        event: SensorEvent?
    ) {
        if (
            event ==
                null
        ) {
            return
        }

        if (
            event.sensor.type ==
                Sensor.TYPE_LIGHT
        ) {
            val firstLightReading = ambientLux == null
            ambientLux =
                event.values
                    .firstOrNull()
                    ?.coerceAtLeast(
                        0f
                    )

            updateLightAdvisor()
            // The first reading often arrives AFTER bindCamera. Complete the
            // existing F9 low-light setup then, without changing a running take.
            if (firstLightReading && recording == null) {
                f9ApplyInterviewExposure()
            }
            return
        }

        if (
            event.sensor.type !=
                Sensor.TYPE_ROTATION_VECTOR
        ) {
            return
        }

        try {
            val rotationMatrix = FloatArray(9)
            val orientation = FloatArray(3)

            SensorManager.getRotationMatrixFromVector(
                rotationMatrix,
                event.values
            )
            SensorManager.getOrientation(
                rotationMatrix,
                orientation
            )

            val azimuth =
                Math.toDegrees(
                    orientation[0].toDouble()
                ).toFloat()
            val pitch =
                Math.toDegrees(
                    orientation[1].toDouble()
                ).toFloat()
            val roll =
                Math.toDegrees(
                    orientation[2].toDouble()
                ).toFloat()

            val previousAzimuth =
                compassAzimuthDeg
            val previousPitch =
                phonePitchDeg
            val previousRoll =
                phoneRollDeg

            val normalizedAzimuth =
                (
                    (azimuth % 360f) +
                        360f
                    ) % 360f

            compassAzimuthDeg =
                normalizedAzimuth
            phonePitchDeg =
                pitch
            phoneRollDeg =
                roll

            val now =
                SystemClock.elapsedRealtime()

            if (
                previousAzimuth !=
                    null &&
                previousPitch !=
                    null &&
                previousRoll !=
                    null &&
                lastMotionSampleMs >
                    0L
            ) {
                val dt =
                    (
                        now -
                            lastMotionSampleMs
                        ).coerceAtLeast(
                            1L
                        )

                val azRaw =
                    kotlin.math.abs(
                        normalizedAzimuth -
                            previousAzimuth
                    )

                val azDelta =
                    minOf(
                        azRaw,
                        360f -
                            azRaw
                    )

                val rollDelta =
                    kotlin.math.abs(
                        roll -
                            previousRoll
                    )

                val pitchDelta =
                    kotlin.math.abs(
                        pitch -
                            previousPitch
                    )

                val scale =
                    (
                        16.67f /
                            dt.toFloat()
                        ).coerceIn(
                            0.35f,
                            2.5f
                        )

                val rawScore =
                    (
                        (
                            rollDelta +
                                pitchDelta +
                                (
                                    azDelta *
                                        0.35f
                                    )
                            ) *
                            4.2f *
                            scale
                        ).coerceIn(
                            0f,
                            100f
                        )

                cameraShakeScore =
                    (
                        cameraShakeScore *
                            0.72f
                        ) +
                        (
                            rawScore *
                                0.28f
                            )
            }

            lastMotionSampleMs =
                now
        } catch (_: Exception) {
        }
    }

    private fun startGnssMonitor() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.N
        ) {
            return
        }

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (gnssCallbackHolder != null) {
            return
        }

        try {
            val callback =
                object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(
                        status: GnssStatus
                    ) {
                        gnssSatellitesVisible =
                            status.satelliteCount

                        var used = 0
                        for (
                            i in
                            0 until status.satelliteCount
                        ) {
                            if (status.usedInFix(i)) {
                                used++
                            }
                        }
                        gnssSatellitesUsed = used
                    }

                    override fun onStopped() {
                        gnssSatellitesVisible = -1
                        gnssSatellitesUsed = -1
                    }
                }

            gnssCallbackHolder = callback
            locationManager.registerGnssStatusCallback(
                callback,
                Handler(Looper.getMainLooper())
            )
        } catch (_: Exception) {
            gnssCallbackHolder = null
        }
    }

    private fun stopGnssMonitor() {
        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.N
        ) {
            return
        }

        val callback =
            gnssCallbackHolder as?
                GnssStatus.Callback
                ?: return

        try {
            locationManager.unregisterGnssStatusCallback(
                callback
            )
        } catch (_: Exception) {
        }

        gnssCallbackHolder = null
    }

    private fun gnssOverlay(): String {
        val altText =
            alt?.let {
                String.format(
                    Locale.US,
                    "ALT %.0fm",
                    it
                )
            } ?: "ALT --"

        val accText =
            accuracy?.let {
                String.format(
                    Locale.US,
                    "ACC ±%.0fm",
                    it
                )
            } ?: "ACC --"

        val satText =
            if (
                gnssSatellitesVisible >= 0 &&
                gnssSatellitesUsed >= 0
            ) {
                "SAT ${gnssSatellitesUsed}/${gnssSatellitesVisible}"
            } else {
                "SAT --/--"
            }

        return "$altText • $accText • $satText • ${gpsQualityLabel()} • FIX ${gpsFixAgeText()}"
    }

    private fun navigationOverlay(): String {
        val compassText =
            compassAzimuthDeg?.let {
                String.format(
                    Locale.US,
                    "COMP %.0f° %s",
                    it,
                    cardinalDirection(it)
                )
            } ?: "COMP --"

        val gpsHeadingText =
            heading?.let {
                String.format(
                    Locale.US,
                    "GPS HDG %.0f° %s",
                    it,
                    cardinalDirection(it)
                )
            } ?: "GPS HDG --"

        val speedText =
            speedKmh?.let {
                String.format(
                    Locale.US,
                    "SPD %.1fkm/h",
                    it
                )
            } ?: "SPD --"

        val distanceText =
            if (recording != null) {
                if (distanceTravelledM >= 1000f) {
                    String.format(
                        Locale.US,
                        "DIST %.2fkm",
                        distanceTravelledM / 1000f
                    )
                } else {
                    String.format(
                        Locale.US,
                        "DIST %.0fm",
                        distanceTravelledM
                    )
                }
            } else {
                "DIST STBY"
            }

        return "$compassText • $gpsHeadingText • $speedText • ${motionLabel()} • $distanceText"
    }

    private fun orientationOverlay(): String {
        val pitch =
            phonePitchDeg?.let {
                String.format(
                    Locale.US,
                    "TILT %.1f°",
                    it
                )
            } ?: "TILT --"

        val roll =
            phoneRollDeg?.let {
                String.format(
                    Locale.US,
                    "HORIZON %.1f°",
                    it
                )
            } ?: "HORIZON --"

        val level =
            phoneRollDeg?.let {
                when {
                    kotlin.math.abs(it) <= 1.0f ->
                        "LEVEL LOCK"
                    kotlin.math.abs(it) <= 3.0f ->
                        "LEVEL NEAR"
                    else ->
                        "LEVEL ADJUST"
                }
            } ?: "LEVEL --"

        return "$pitch • $roll • $level"
    }

    private fun audioLevelOverlay(): String {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return "MIC OFF"
        }

        if (recording == null) {
            return "MIC READY"
        }

        val amplitude =
            audioAmplitude.coerceIn(
                0.0,
                1.0
            )

        val percent =
            (amplitude * 100.0)
                .roundToInt()

        val dbfs =
            if (amplitude > 0.0001) {
                (
                    20.0 *
                        log10(amplitude)
                    ).coerceAtLeast(-80.0)
            } else {
                -80.0
            }

        return String.format(
            Locale.US,
            "%s • LVL %d%% • %.1fdBFS",
            audioStateLabel,
            percent,
            dbfs
        )
    }

    private fun interviewCameraPrefs() =
        duSharedPreferences(
            // New Interview-only keys live in the same per-experience store
            // used by the shared camera engine. Existing modes never read
            // this V281 experience id or any of these keys.
            reportCameraPrefsName(),
            Context.MODE_PRIVATE
        )

    private fun loadInterviewCameraPreferences() {
        val prefs = interviewCameraPrefs()
        interviewAnalysisEnabled = prefs.getBoolean("analysis_enabled", true)
        // Burn-in is deliberately opt-in for every new installation.
        interviewBurnInEnabled = prefs.getBoolean("burn_in_enabled", false)
        interviewTwoShotEnabled = prefs.getBoolean("two_shot_enabled", false)
    }

    private fun saveInterviewCameraPreferences() {
        if (!isInterviewCamera()) return
        interviewCameraPrefs()
            .edit()
            .putBoolean("analysis_enabled", interviewAnalysisEnabled)
            .putBoolean("burn_in_enabled", interviewBurnInEnabled)
            .putBoolean("two_shot_enabled", interviewTwoShotEnabled)
            .apply()
    }

    private fun showInterviewConsentIfNeeded() {
        val prefs = interviewCameraPrefs()
        if (prefs.getBoolean("consent_notice_seen", false)) return

        AlertDialog.Builder(this)
            .setTitle("INTERVIEW CAM • CONSENT")
            .setMessage(
                "Tell the subject that they are being recorded and that this phone can show on-device physical-signal observations for the operator. " +
                    "No biometric data is sent off this phone. These observations are not a measure of truthfulness."
            )
            .setPositiveButton("CONTINUE") { _, _ ->
                prefs.edit().putBoolean("consent_notice_seen", true).apply()
            }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun loadReporterIdentity() {
        val prefs =
            duSharedPreferences(
                "develop_uganda_reporter",
                Context.MODE_PRIVATE
            )

        reporterName =
            prefs.getString(
                "reporter_name",
                "CITIZEN"
            )
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "CITIZEN"

        storyId =
            prefs.getString(
                "story_id",
                ""
            )
                ?.trim()
                ?: ""
    }

    private fun reporterDisplayName(): String {
        return reporterName
            .ifBlank { "CITIZEN" }
            .take(28)
    }

    private fun storyDisplayId(): String {
        return storyId
            .ifBlank { "--" }
            .take(24)
    }

    private fun newReportId(): String {
        clipSequence =
            nextClipSequence()

        return "DU-" +
            SimpleDateFormat(
                "yyMMdd-HHmmss",
                Locale.US
            ).format(Date()) +
            "-" +
            clipSequenceText()
    }

    private fun identityButtonText(): String {
        return "REPORT ID\n$reportId • ${reporterDisplayName()} • ${storyDisplayId()}"
    }

    private fun showIdentityDialog() {
        val container =
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

        val reporterInput =
            EditText(this).apply {
                hint = "Reporter / citizen name"
                setText(
                    if (
                        reporterName ==
                        "CITIZEN"
                    ) {
                        ""
                    } else {
                        reporterName
                    }
                )
                isSingleLine = true
            }

        val storyInput =
            EditText(this).apply {
                hint = "Story ID / assignment (optional)"
                setText(storyId)
                isSingleLine = true
            }

        container.addView(reporterInput)
        container.addView(storyInput)

        AlertDialog.Builder(this)
            .setTitle(
                "develop.uganda Report Identity"
            )
            .setMessage(
                "These fields are burned into the recorded report."
            )
            .setView(container)
            .setPositiveButton(
                "SAVE"
            ) { _, _ ->
                reporterName =
                    reporterInput.text
                        .toString()
                        .trim()
                        .ifBlank {
                            "CITIZEN"
                        }

                storyId =
                    storyInput.text
                        .toString()
                        .trim()

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

                refreshHud()
            }
            .setNegativeButton(
                "CANCEL",
                null
            )
            .show()
    }

    private fun coordinateOverlay(): String {
        val b = StringBuilder("GPS")

        if (lat != null && lon != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • LAT %.5f LON %.5f",
                    lat,
                    lon
                )
            )
        } else {
            b.append(" --")
        }

        if (alt != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • ALT %.0fm",
                    alt
                )
            )
        }

        if (accuracy != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • ±%.0fm",
                    accuracy
                )
            )
        }

        if (heading != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • HDG %.0f°",
                    heading
                )
            )
        }

        if (speedKmh != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • SPD %.1fkm/h",
                    speedKmh
                )
            )
        }

        return b.toString()
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
        var size = paint.textSize

        while (
            paint.measureText(value) > maxWidth &&
            size > minSize
        ) {
            size *= 0.94f
            paint.textSize = size
        }

        val textToDraw = if (
            paint.measureText(value) <= maxWidth
        ) {
            value
        } else {
            var end = value.length
            val ellipsis = "…"

            while (
                end > 1 &&
                paint.measureText(
                    value.substring(0, end) + ellipsis
                ) > maxWidth
            ) {
                end--
            }

            value.substring(0, end) + ellipsis
        }

        drawReportTextBackplate(
            canvas,
            textToDraw,
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

        // V192: high-contrast recorded text. A solid black outline sits
        // behind the chosen text colour so the burn-in stays readable on
        // white walls, sky, sunlight and other bright video backgrounds.
        paint.style =
            Paint.Style.STROKE

        paint.strokeWidth =
            paint.textSize *
                reportHudOutlineScale()

        paint.color =
            reportHudOutlineColor()

        canvas.drawText(
            textToDraw,
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
            textToDraw,
            x,
            y,
            paint
        )

        paint.style =
            savedStyle
    }

    /**
     * FIX3 recorder containment retained by FIX4. A codec/camera callback
     * must not bring down the whole application: keep the recording owner
     * until CameraX finalizes, request a controlled stop, and show the actual
     * failure class on the shooting screen.
     */
    private fun recorderCoreFailure(stage: String, failure: Throwable) {
        val kind = failure.javaClass.simpleName.ifBlank { "UNKNOWN" }
        runCatching {
            DevelopUgandaV276RecordingSafety.addEvent(
                this,
                "REC $stage • $kind"
            )
        }
        runCatching {
            statusView.text = "REC PROTECTED • $kind"
            statusView.setTextColor(DevelopUgandaFivemods8Theme.accent)
            recordButton.text = "SAVING…"
            recordButton.isEnabled = false
            toast("Recorder protected • $kind")
        }
        // Do not clear `recording` here. CameraX needs that owner alive until
        // its Finalize event has either saved or reported the clip.
        runCatching { recording?.stop() }
    }

    private fun isStatusCamera(): Boolean =
        additiveModeProfile()?.deliveryProfile ==
            DevelopUgandaDeliveryProfile.WHATSAPP_STATUS

    private val statusSegmentStopRunnable =
        Runnable {
            if (!isStatusCamera() || recording == null) return@Runnable

            // This is a controlled stop: Finalize retains ownership until the
            // source master is safely written, then starts the next segment.
            statusSegmentRestartPending = true
            crashSafeRestartPending = true
            crashSafeSegmentContinuation = true
            statusView.text = "STATUS SEGMENT • SAVING"
            recordButton.text = "SAVING…"
            recordButton.isEnabled = false
            toast("Status limit reached • saving next segment")
            runCatching { recording?.stop() }
        }

    private fun scheduleStatusSegmentIfNeeded() {
        uiHandler.removeCallbacks(statusSegmentStopRunnable)
        if (!isStatusCamera()) return

        val durationMs = DevelopUgandaStatusPolicy.segmentDurationMs(this) ?: return
        uiHandler.postDelayed(statusSegmentStopRunnable, durationMs)
    }

    private fun cancelStatusSegmentation() {
        statusSegmentRestartPending = false
        uiHandler.removeCallbacks(statusSegmentStopRunnable)
    }

    private fun cancelCrashSafeContinuation() {
        crashSafeRestartPending = false
        crashSafeSegmentContinuation = false
        crashSafeSegmentStopRequested = false
    }

    private fun toggleRecording() {
        if (captureModes[captureModeIndex] == "PHOTO") {
            takePhoto()
            return
        }

        if (recording == null && isStatusCamera()) {
            if (DevelopUgandaStatusPolicy.segmentDurationMs(this) == null) {
                DevelopUgandaStatusPolicy.requireConfirmedLimit(this) {
                    toggleRecording()
                }
                return
            }
        }

        val vc = videoCapture ?: run {
            toast("Camera is still starting")
            return
        }

        if (recording != null) {
            cancelCrashSafeContinuation()
            cancelStatusSegmentation()
            statusView.text = "SAVING…"
            recordButton.text = "SAVING…"
            recordButton.isEnabled = false
            runCatching { recording?.stop() }
            return
        }

        val cameraPage = currentCameraPage()
        val continuingProtectedTake = crashSafeSegmentContinuation && crashSafeTake != null
        if (!continuingProtectedTake) {
            val fieldPreflight = DevelopUgandaFivemods12Preflight.snapshot(this, cameraPage)
            if (!fieldPreflight.mayRecord) {
                val fieldDialog = AlertDialog.Builder(this)
                    .setTitle("FIVEMODS 12 PREFLIGHT • UNAVAILABLE")
                    .setMessage(fieldPreflight.items.joinToString("\n") { "${it.label} • ${it.stateLabel} • ${it.reason}" })
                    .setNegativeButton("CLOSE", null)
                    .setPositiveButton("OPEN FIELD CONSOLE") { _, _ ->
                        startActivity(Intent(this, DevelopUgandaFivemods12FieldConsoleActivity::class.java).putExtra(DevelopUgandaFivemods12FieldConsoleActivity.EXTRA_MODE, cameraPage.name))
                    }
                    .create()
                DevelopUgandaDialogStyler.show(fieldDialog, cameraPage)
                return
            }
            val safetyPreflight = DevelopUgandaV276RecordingSafety.recordingPreflight(this, cameraPage)
            if (!safetyPreflight.mayStart) {
                f12SafetyOverrideApprovedOnce = false
                val refusedDialog = AlertDialog.Builder(this)
                    .setTitle("RECORDING PREFLIGHT • REFUSED")
                    .setMessage(
                        safetyPreflight.blocked.joinToString("\n") { "• $it" } +
                            if (safetyPreflight.warnings.isEmpty()) "" else {
                                "\n\nUNKNOWN / WARNING\n" +
                                    safetyPreflight.warnings.joinToString("\n") { "• $it" }
                            }
                    )
                    .setPositiveButton("ACKNOWLEDGE", null)
                    .create()
                DevelopUgandaDialogStyler.show(refusedDialog, cameraPage)
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
                DevelopUgandaDialogStyler.show(overrideDialog, cameraPage)
                return
            }

            val proceededWarnings = buildList {
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
            if (proceededWarnings.isNotEmpty()) {
                DevelopUgandaV276RecordingSafety.addEvent(
                    this,
                    "PREFLIGHT WARNING PROCEEDED • ${proceededWarnings.joinToString(" • ")}",
                )
            }

            crashSafeTakeRoot = f9NewTakeId(cameraPage.name)
            crashSafeTake = runCatching {
                DevelopUgandaCrashSafeTake.begin(
                    this,
                    crashSafeTakeRoot,
                    cameraPage,
                    proceededWarnings,
                )
            }.getOrElse { failure ->
                DevelopUgandaV276RecordingSafety.addEvent(
                    this,
                    "REC PREFLIGHT • MANIFEST ${failure.javaClass.simpleName}",
                )
                null
            }
        }
        crashSafeSegmentContinuation = false
        crashSafeSegmentStopRequested = false
        val protectedTake = crashSafeTake ?: run {
            toast("Recording refused • safety manifest unavailable")
            return
        }
        val f9TakeId = protectedTake.nextSegmentName()
        val diagnosticName = "${f9TakeId}_CLEAN"

        try {
            val hasMicPermission =
                ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.RECORD_AUDIO
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

            if (!hasMicPermission) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.RECORD_AUDIO),
                    7107
                )
                toast("Allow microphone, then press REC again")
                return
            }

            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "$diagnosticName.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/develop.uganda")
                }
            }
            val outputOptions =
                MediaStoreOutputOptions.Builder(
                    contentResolver,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                )
                    .setContentValues(values)
                    .build()

            // CameraX keeps ownership of the embedded CLEAN audio. The V272
            // worker may open a separate companion safety WAV; it never
            // replaces or rewrites CameraX's track.
            val pending =
                vc.output
                    .prepareRecording(this, outputOptions)
                    .withAudioEnabled()

            var v28019LastRouteCheckMs = 0L
            var v28019AudioRouteWarned = false

            recording = pending.start(
                ContextCompat.getMainExecutor(this)
            ) recordingEvents@{ event ->
                try {
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            crashSafeThermalStopRequested = false
                            f12ProtectionStopRequested = false
                            crashSafeSegmentStopRequested = false
                            v255RecordedDurationNs = 0L
                            v255RecordedBytes = 0L
                            audioAmplitude = 0.0
                            audioPeakAmplitude = 0.0
                            f9AudioPeakDbfs = null
                            f9SilenceStartedAtMs = 0L
                            recordStartUtc = Instant.now().toString()
                            recStarted = System.currentTimeMillis()
                            f9ActiveTake = f9CreateTakeMetadata(f9TakeId)
                            crashSafeTake?.onSegmentStarting(diagnosticName)
                            f9ApplyInterviewExposure()
                            if (isInterviewCamera()) {
                                interviewObservationEngine?.beginRecording(diagnosticName)
                            }

                            val continuityStart =
                                runCatching {
                                    DevelopUgandaV272FieldSoundContinuity.armAudioLock(
                                        this@DevelopUgandaCameraActivity
                                    )
                                }
                            val safetyAudioStart =
                                runCatching {
                                    DevelopUgandaV272FieldSoundContinuity.startSafetyTrack(
                                        this@DevelopUgandaCameraActivity,
                                        diagnosticName,
                                    )
                                }.getOrNull()
                            val safetyStart =
                                runCatching {
                                    DevelopUgandaV276RecordingSafety.onRecordingStarted(
                                        this@DevelopUgandaCameraActivity,
                                        diagnosticName,
                                        "CORE+AUDIO • FIX4 $coreDeliveredProfileLabel",
                                        v260ProjectName(),
                                        v260CameraName(),
                                        v260ActiveSceneNumber,
                                        v260ActiveTakeNumber
                                    )
                                }

                            statusView.text =
                                when {
                                    !safetyStart.isSuccess -> "● REC • SAFETY WARN"
                                    !continuityStart.isSuccess -> "● REC • AUDIO LOCK WARN"
                                    safetyAudioStart?.active != true -> "● REC • SAFETY AUDIO UNAVAILABLE"
                                    else ->
                                        "● REC • $coreDeliveredProfileLabel • ${safetyAudioStart.source} • PRIMARY + -12dB"
                                }
                            statusView.setTextColor(DevelopUgandaFivemods8Theme.record)
                            recordButton.text = "■ STOP"
                            recordButton.isEnabled = true
                            if (isStatusCamera()) {
                                scheduleStatusSegmentIfNeeded()
                            }
                        }

                        is VideoRecordEvent.Status -> {
                            v255RecordedDurationNs =
                                event.recordingStats.recordedDurationNanos
                            v255RecordedBytes =
                                event.recordingStats.numBytesRecorded
                            f9UpdateAudioTelemetry(event.recordingStats.audioStats.audioAmplitude)

                            runCatching {
                                DevelopUgandaV276RecordingSafety.onRecordingStatus(
                                    this@DevelopUgandaCameraActivity,
                                    v255RecordedDurationNs,
                                    v255RecordedBytes,
                                    cameraPage,
                                )
                            }
                            crashSafeTake?.onSegmentProgress(v255RecordedDurationNs, v255RecordedBytes)

                            val fieldProtection = DevelopUgandaFivemods12RecordingProtection.state(
                                this@DevelopUgandaCameraActivity,
                                cameraPage,
                            )
                            if (!f12ProtectionStopRequested && fieldProtection.stopReason != null) {
                                f12ProtectionStopRequested = true
                                cancelCrashSafeContinuation()
                                cancelStatusSegmentation()
                                statusView.text = "${fieldProtection.stopReason} • FINALISING CLEAN"
                                statusView.setTextColor(DevelopUgandaFivemods8Theme.warning)
                                DevelopUgandaV276RecordingSafety.addEvent(
                                    this@DevelopUgandaCameraActivity,
                                    "PROTECTION STOP • ${fieldProtection.stopReason} • CLEAN FINALISE REQUESTED",
                                )
                                runCatching { recording?.stop() }
                            }

                            val safetyAudio = DevelopUgandaV272FieldSoundContinuity.safetyAudioState()
                            if (safetyAudio.active) {
                                audioStateLabel = safetyAudio.hudLine()
                                f9ActiveMic = safetyAudio.source
                            }

                            if (
                                !f12ProtectionStopRequested &&
                                !crashSafeThermalStopRequested &&
                                DevelopUgandaV276RecordingSafety.requiresGracefulThermalStop(
                                    this@DevelopUgandaCameraActivity
                                )
                            ) {
                                crashSafeThermalStopRequested = true
                                cancelCrashSafeContinuation()
                                cancelStatusSegmentation()
                                statusView.text = "THERMAL CRITICAL • FINALISING CLEAN"
                                statusView.setTextColor(DevelopUgandaFivemods8Theme.warning)
                                DevelopUgandaV276RecordingSafety.addEvent(
                                    this@DevelopUgandaCameraActivity,
                                    "THERMAL STOP • CLEAN finalise requested before any profile change",
                                )
                                runCatching { recording?.stop() }
                            } else if (
                                !f12ProtectionStopRequested &&
                                !crashSafeSegmentStopRequested &&
                                v255RecordedDurationNs >= DevelopUgandaCrashSafeTake.SEGMENT_DURATION_MS * 1_000_000L
                            ) {
                                // The Recorder Status callback already drives this check; no
                                // additional timer or capture-thread work is introduced.
                                crashSafeSegmentStopRequested = true
                                crashSafeRestartPending = true
                                crashSafeSegmentContinuation = true
                                statusSegmentRestartPending = isStatusCamera()
                                statusView.text = "SEGMENT SAFEPOINT • FINALISING CLEAN"
                                recordButton.text = "SAVING…"
                                recordButton.isEnabled = false
                                runCatching { recording?.stop() }
                            }

                            val routeNowMs = System.currentTimeMillis()
                            if (routeNowMs - v28019LastRouteCheckMs >= 1500L) {
                                v28019LastRouteCheckMs = routeNowMs
                                val routeChanged =
                                    runCatching {
                                        DevelopUgandaV272FieldSoundContinuity.audioRouteChanged(
                                            this@DevelopUgandaCameraActivity
                                        )
                                    }.getOrDefault(false)

                                if (routeChanged && !v28019AudioRouteWarned) {
                                    v28019AudioRouteWarned = true
                                    f12ProtectionStopRequested = true
                                    cancelCrashSafeContinuation()
                                    cancelStatusSegmentation()
                                    statusView.text = "MIC DISCONNECTED / ROUTE CHANGED • FINALISING CLEAN"
                                    statusView.setTextColor(DevelopUgandaFivemods8Theme.warning)
                                    DevelopUgandaV276RecordingSafety.addEvent(
                                        this@DevelopUgandaCameraActivity,
                                        "INTERRUPTION • MICROPHONE ROUTE CHANGED • CLEAN FINALISE REQUESTED",
                                    )
                                    toast("Microphone changed • finalising cleanly")
                                    runCatching { recording?.stop() }
                                }
                            }
                        }

                        is VideoRecordEvent.Finalize -> {
                            if (isStatusCamera()) {
                                uiHandler.removeCallbacks(statusSegmentStopRunnable)
                            }
                            val hadError = event.hasError()
                            val durationMs =
                                (event.recordingStats.recordedDurationNanos / 1_000_000L)
                                    .coerceAtLeast(0L)
                            val bytes =
                                event.recordingStats.numBytesRecorded.coerceAtLeast(0L)
                            v255RecordedDurationNs = event.recordingStats.recordedDurationNanos
                            v255RecordedBytes = bytes
                            val cleanUri = event.outputResults.outputUri
                            crashSafeTake?.onSegmentFinalized(
                                cleanUri.takeIf { it != Uri.EMPTY },
                                durationMs,
                                bytes,
                                hadError,
                            )
                            val safetyAudioFinal = runCatching {
                                DevelopUgandaV272FieldSoundContinuity.stopSafetyTrack()
                            }.getOrNull()

                            val safetyResult =
                                runCatching {
                                    DevelopUgandaV276RecordingSafety.onRecordingFinalized(
                                        this@DevelopUgandaCameraActivity,
                                        event.outputResults.outputUri.toString(),
                                        durationMs,
                                        bytes,
                                        hadError,
                                        emptyList<String>()
                                    )
                                }.getOrNull()

                            if (!hadError && cleanUri != Uri.EMPTY) {
                                DevelopUgandaFivemods12Identity.writeCleanSidecar(
                                    this@DevelopUgandaCameraActivity,
                                    cameraPage,
                                    "$diagnosticName.mp4",
                                    safetyResult?.ok == true,
                                    durationMs,
                                    bytes,
                                )
                            }

                            recording = null
                            recordButton.text = "● RECORD"
                            recordButton.isEnabled = true

                            val interviewRecordingSummary =
                                if (isInterviewCamera()) {
                                    interviewObservationEngine?.finishRecording()
                                } else {
                                    null
                                }

                            if (hadError) {
                                crashSafeTake?.finish(interrupted = true)
                                crashSafeTake = null
                                crashSafeTakeRoot = ""
                                cancelCrashSafeContinuation()
                                f9ActiveTake = null
                                cancelStatusSegmentation()
                                statusView.text = "CLEAN MISSING • REC ERROR ${event.error}"
                                statusView.setTextColor(DevelopUgandaFivemods8Theme.warning)
                                toast("CameraX error ${event.error}")
                            } else {
                                statusView.text =
                                    safetyResult?.label
                                        ?: "SAVED • HD + AUDIO + SAFETY"
                                statusView.setTextColor(
                                    if (safetyResult == null || safetyResult.ok) {
                                        DevelopUgandaFivemods8Theme.accent
                                    } else {
                                        DevelopUgandaFivemods8Theme.warning
                                    }
                                )
                                toast(
                                    if (safetyAudioFinal?.outputPath != null) {
                                        "CLEAN + limited primary / -12dB safety audio saved"
                                    } else {
                                        "CLEAN saved • safety audio unavailable"
                                    }
                                )
                                // The original CameraX master is already safe
                                // at this point.  A selected V233 color profile
                                // now renders a separate rich Pro Color Master
                                // in the background; a failure never replaces
                                // or damages the original clip.
                                runCatching {
                                    scheduleV233ColorMaster(
                                        event.outputResults.outputUri,
                                        diagnosticName
                                    )
                                }
                                val f9Take = f9ActiveTake?.let {
                                    f9FinishTakeMetadata(it, interviewRecordingSummary)
                                }
                                f9ActiveTake = null
                                if (f9Take == null) {
                                    statusView.text = "CLEAN SAVED • BRAND MISSING • metadata hand-off failed"
                                    statusView.setTextColor(DevelopUgandaFivemods8Theme.warning)
                                    toast("CLEAN saved • BRAND MISSING")
                                } else {
                                    statusView.text = "CLEAN SAVED • BRAND SAVING"
                                    f9DualOutputExporter.enqueue(event.outputResults.outputUri, f9Take)
                                    // Keep TikTok's pre-existing delivery job independent of
                                    // BRAND success. STATUS is intentionally different: it
                                    // encodes only after its marked BRAND master is ready.
                                    if (f9Take.mode == "TIKTOK") {
                                        runCatching {
                                            exportAdditiveDelivery(
                                                event.outputResults.outputUri,
                                                "${f9Take.takeId}_CLEAN"
                                            )
                                        }
                                    }
                                }

                                if (statusSegmentRestartPending && isStatusCamera()) {
                                    uiHandler.postDelayed({
                                        if (recording == null && statusSegmentRestartPending) {
                                            statusSegmentRestartPending = false
                                            crashSafeRestartPending = false
                                            toggleRecording()
                                        }
                                    }, 750L)
                                } else if (crashSafeRestartPending) {
                                    uiHandler.post {
                                        if (recording == null && crashSafeRestartPending) {
                                            crashSafeRestartPending = false
                                            toggleRecording()
                                        }
                                    }
                                } else {
                                    crashSafeTake?.finish(interrupted = false)
                                    crashSafeTake = null
                                    crashSafeTakeRoot = ""
                                    crashSafeSegmentStopRequested = false
                                }
                            }
                        }

                        else -> Unit
                    }
                } catch (failure: Throwable) {
                    recorderCoreFailure("CALLBACK", failure)
                }
            }
        } catch (failure: Throwable) {
            runCatching { DevelopUgandaV272FieldSoundContinuity.stopSafetyTrack() }
            crashSafeTake?.finish(interrupted = true)
            crashSafeTake = null
            crashSafeTakeRoot = ""
            cancelCrashSafeContinuation()
            recording = null
            recorderCoreFailure("START", failure)
            recordButton.text = "● RECORD"
            recordButton.isEnabled = true
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: run {
            toast("Photo camera is still starting")
            return
        }

        reportId = newReportId()
        recordStartUtc =
            Instant.now().toString()

        val photoName =
            DevelopUgandaFivemods12Identity.forPage(currentCameraPage()).code +
                "_DEVELOP_UGANDA_${reportId}_${sceneModes[sceneIndex]}_${lookModes[lookIndex]}_" +
                SimpleDateFormat(
                    "yyyyMMdd_HHmmss",
                    Locale.US
                ).format(Date())

        val values = ContentValues().apply {
            put(
                MediaStore.Images.Media.DISPLAY_NAME,
                photoName
            )
            put(
                MediaStore.Images.Media.MIME_TYPE,
                "image/jpeg"
            )

            if (
                android.os.Build.VERSION.SDK_INT >=
                29
            ) {
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/develop.uganda"
                )
            }
        }

        val output =
            ImageCapture.OutputFileOptions.Builder(
                contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
            )
                .build()

        statusView.text = "CAPTURING"

        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(this),
            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result:
                    ImageCapture.OutputFileResults
                ) {
                    statusView.text = "PHOTO SAVED"
                    statusView.setTextColor(
                        DevelopUgandaFivemods8Theme.accent
                    )
                    toast(
                        "Photo saved • develop.uganda"
                    )

                    uiHandler.postDelayed({
                        statusView.text =
                            "PHOTO READY"
                        statusView.setTextColor(
                            DevelopUgandaFivemods8Theme.record
                        )
                    }, 1600L)
                }

                override fun onError(
                    exception:
                    ImageCaptureException
                ) {
                    statusView.text = "PHOTO ERROR"
                    statusView.setTextColor(
                        DevelopUgandaFivemods8Theme.warning
                    )
                    toast(
                        "Photo capture failed"
                    )
                }
            }
        )
    }

    private fun refreshReportPillStates() {
        val values =
            listOf(
                sceneButton to
                    DevelopUgandaFivemods8Theme.accent,
                lookButton to
                    DevelopUgandaFivemods8Theme.accent,
                qualityButton to
                    DevelopUgandaFivemods8Theme.content,
                captureModeButton to
                    DevelopUgandaFivemods8Theme.accent,
                viewModeButton to
                    DevelopUgandaFivemods8Theme.accent,
                settingsButton to
                    DevelopUgandaFivemods8Theme.accent,
                guidesButton to
                    DevelopUgandaFivemods8Theme.accent,
                resetButton to
                    DevelopUgandaFivemods8Theme.accent
            )

        values.forEach {
            pair ->
            pair.first.background =
                ColorDrawable(
                    DevelopUgandaFivemods8Theme.transparent
                )

            pair.first.invalidate()
        }
    }

    private fun refreshHud() {
        refreshV233ColorMonitor()
        applyAutoDirectorIfNeeded()
        updateReportModePreviewTuning()
        applyIndependentCameraExperienceUi()
        updateHorizonGuard()
        updateMotionGuard()
        updateLightAdvisor()
        updateAudioGuard()
        updateThermalGuard()
        updateShotQualityGuard()

        timecodeView.text =
            "TC ${tc()}"

        formatView.text =
            "${verifiedCameraStateText()} • SCENE ${sceneModes[sceneIndex]} • LOOK ${lookModes[lookIndex]}"

        locationView.text =
            locationOverlay()
        weatherView.text =
            weatherOverlay()
        systemView.text =
            systemOverlay()

        val chromeProfile = DevelopUgandaModeProfiles.forPage(currentCameraPage())
        val f12Status = DevelopUgandaBroadcastStatus(
                format = chromeProfile.takeIf { it.hasProfileData }?.let { profile ->
                    listOfNotNull(
                        profile.qualityName,
                        profile.frameRate?.let { "${it}FPS" },
                    ).joinToString(" • ")
                },
                // CameraX does not expose the negotiated encoder MIME here.
                // Unknown stays visible instead of echoing a requested codec.
                codec = null,
                timecode = tc(),
                battery = batteryPct(),
                freeStorageGb = freeStorageGb(),
                audio = audioLevelOverlay(),
                recording = recording != null,
            )
        broadcastCameraChrome?.update(f12Status)
        f12CameraShell?.updateBroadcastStatus(f12Status)

        if (
            ::previewNarrationPanel.isInitialized
        ) {
            previewTagView.text =
                "${sceneTag()} • ${reportModePurposeLabel()} • ${lookModes[lookIndex]} • ${autoDirectorStateText()} • V259 PRO CAM"

            previewIdentityView.text =
                "REPORT ID $reportId • REPORTER ${reporterDisplayName()} • STORY ${storyDisplayId()}"

            previewClockView.text =
                "${
                    when {
                        recording != null -> "● REC"
                        captureModes[captureModeIndex] == "PHOTO" -> "● PHOTO"
                        else -> "STBY"
                    }
                } • TC ${tc()} • ${clock.format(Date())}"

            previewModeView.text =
                "SCENE ${sceneModes[sceneIndex]} • LOOK ${lookModes[lookIndex]} • ${qualityModes[qualityIndex]} • ${captureModes[captureModeIndex]}"

            previewPlaceView.text =
                placeName

            previewGpsView.text =
                "${coordinatePrimaryOverlay()} • ${gnssOverlay()}"

            previewNavView.text =
                "${navigationOverlay()} • ${orientationOverlay()}"

            previewSystemView.text =
                "${weatherOverlay()} • ${audioLevelOverlay()} • ${systemOverlay()}"

            previewHealthView.text =
                "${recordingHealthText()} • ${motionGuardLabel()} • ${estimatedRecordingTimeText()}"

            previewHealthView.setTextColor(
                recordingHealthColor()
            )
        }

        if (::sceneButton.isInitialized) {
            sceneButton.text =
                "SCENE ▾\n${sceneModes[sceneIndex]}"
        }

        if (::lookButton.isInitialized) {
            lookButton.text =
                "LOOK ▾\n${lookModes[lookIndex]}"
        }

        if (::qualityButton.isInitialized) {
            qualityButton.text =
                "FORMAT ▾\n${qualityDeckLabel()}"
        }

        if (::captureModeButton.isInitialized) {
            captureModeButton.text =
                "CAPTURE ▾\n${captureModes[captureModeIndex]}"
        }

        if (::identityButton.isInitialized) {
            identityButton.text =
                identityButtonText()
        }

        if (
            ::recordButton.isInitialized &&
            recording == null
        ) {
            recordButton.text =
                if (
                    captureModes[
                        captureModeIndex
                    ] == "PHOTO"
                ) {
                    "● PHOTO"
                } else {
                    "● RECORD"
                }
        }

        if (::lensButton.isInitialized) {
            lensButton.text =
                "LENS ▾\n${currentLensDeckLabel()}"
        }
    }

    internal fun v253QualityLabel(): String = qualityDeckLabel()

    internal fun v253SetQuality(label: String): String {
        v271RememberSettingChange("QUALITY ${v253QualityLabel()}")
        if (recording != null) {
            toast("Stop recording before changing format")
            return qualityDeckLabel()
        }
        val index = qualityModes.indexOf(label)
        if (index >= 0) {
            qualityIndex = index
            refreshHud()
            bindCamera()
        }
        return qualityDeckLabel()
    }

    internal fun v253SceneLabel(): String = sceneModes.getOrNull(sceneIndex) ?: "CINEMA"

    internal fun v253SetScene(label: String): String {
        if (recording != null) {
            toast("Stop recording before changing scene")
            return v253SceneLabel()
        }
        val index = sceneModes.indexOf(label)
        if (index >= 0) {
            sceneIndex = index
            applyScenePreset()
            refreshHud()
        }
        return v253SceneLabel()
    }

    internal fun v253LookLabel(): String = lookModes.getOrNull(lookIndex) ?: "CLEAN"

    internal fun v253SetLook(label: String): String {
        val index = lookModes.indexOf(label)
        if (index >= 0) {
            lookIndex = index
            refreshHud()
        }
        return v253LookLabel()
    }

    internal fun v253GuidesEnabled(): Boolean = previewGuidesEnabled

    internal fun v253ToggleGuides(): Boolean {
        togglePreviewGuides()
        return previewGuidesEnabled
    }

    internal fun v253LiveHealth(): String {
        val bat = batteryPct()?.let { "$it%" } ?: "--"
        val free = freeStorageGb()?.let { "${it}GB" } ?: "--"
        return "BAT $bat • FREE $free • THERM ${thermalStateLabel()} • $audioStateLabel • ${estimatedRecordingTimeText()}"
    }

    private fun v256Prefs() =
        duSharedPreferences(
            "develop_uganda_v256_tactile_clean_cam",
            Context.MODE_PRIVATE
        )

    private fun loadV256ControlPreferences() {
        val p = v256Prefs()
        v256SwitchHapticsOn = p.getBoolean("switch_haptics", true)
        v256SwitchMotionOn = p.getBoolean("switch_motion", true)
        v256TimecodeModeState = when (p.getString("timecode_mode", "REC RUN")?.uppercase(Locale.US)) {
            "FREE RUN" -> "FREE RUN"
            "TIME OF DAY" -> "TIME OF DAY"
            else -> "REC RUN"
        }
        v256FreeRunAnchorRealtime = SystemClock.elapsedRealtime()
    }

    internal fun v256SwitchHapticsEnabled(): Boolean = v256SwitchHapticsOn

    internal fun v256ToggleSwitchHaptics(): String {
        v256SwitchHapticsOn = !v256SwitchHapticsOn
        v256Prefs().edit().putBoolean("switch_haptics", v256SwitchHapticsOn).apply()
        val message = if (v256SwitchHapticsOn) "TACTILE SWITCH HAPTICS ON" else "TACTILE SWITCH HAPTICS OFF"
        toast(message)
        return message
    }

    internal fun v256SwitchMotionEnabled(): Boolean = v256SwitchMotionOn

    internal fun v256ToggleSwitchMotion(): String {
        v256SwitchMotionOn = !v256SwitchMotionOn
        v256Prefs().edit().putBoolean("switch_motion", v256SwitchMotionOn).apply()
        val message = if (v256SwitchMotionOn) "TACTILE SWITCH MOTION ON" else "TACTILE SWITCH MOTION OFF"
        toast(message)
        return message
    }

    internal fun v256TimecodeMode(): String = v256TimecodeModeState

    internal fun v256SetTimecodeMode(value: String): String {
        v256TimecodeModeState = when (value.trim().uppercase(Locale.US)) {
            "FREE RUN" -> "FREE RUN"
            "TIME OF DAY" -> "TIME OF DAY"
            else -> "REC RUN"
        }
        if (v256TimecodeModeState == "FREE RUN") {
            v256FreeRunAnchorRealtime = SystemClock.elapsedRealtime()
        }
        v256Prefs().edit().putString("timecode_mode", v256TimecodeModeState).apply()
        val message = "TIMECODE • $v256TimecodeModeState"
        toast(message)
        refreshHud()
        return v256TimecodeModeState
    }

    internal fun v256ResetFreeRun(): String {
        v256FreeRunAnchorRealtime = SystemClock.elapsedRealtime()
        val message = "FREE RUN TIMECODE RESET • 00:00:00"
        toast(message)
        refreshHud()
        return message
    }

    internal fun v256ControlStatus(): String =
        "SWITCH ${if (v256SwitchMotionOn) "MOTION" else "SNAP"} • HAPTIC ${if (v256SwitchHapticsOn) "ON" else "OFF"} • TC $v256TimecodeModeState • CLEAN DECK"

    private fun v257Prefs() =
        duSharedPreferences(
            "develop_uganda_v257_record_core_motion",
            Context.MODE_PRIVATE
        )

    private fun loadV257RecordCorePreferences() {
        val p = v257Prefs()
        v257BitrateModeState = when (p.getString("bitrate_mode", "STANDARD")?.uppercase(Locale.US)) {
            "SAFE" -> "SAFE"
            "HIGH" -> "HIGH"
            "MAX" -> "MAX"
            else -> "STANDARD"
        }
        v257RecordingWatchdogOn = p.getBoolean("record_watchdog", true)
        v257ClipSafeFeedbackOn = p.getBoolean("clip_safe_feedback", true)
        v257HomeMotionOn = p.getBoolean("home_motion", true)
        v257HomeMotionStyleState = when (p.getString("home_motion_style", "SUBTLE")?.uppercase(Locale.US)) {
            "NORMAL" -> "NORMAL"
            "REDUCED" -> "REDUCED"
            else -> "SUBTLE"
        }
        v257ReadyPulseOn = p.getBoolean("record_ready_pulse", true)
    }

    internal fun v257BitrateMode(): String = v257BitrateModeState

    internal fun v257BitrateTargetMbps(): Int = coreDisplayedBitrate() / 1_000_000

    internal fun v257SetBitrateMode(value: String): String {
        if (recording != null) {
            val message = "STOP RECORDING BEFORE CHANGING BITRATE"
            toast(message)
            return v257BitrateModeState
        }
        v257BitrateModeState = when (value.trim().uppercase(Locale.US)) {
            "SAFE" -> "SAFE"
            "HIGH" -> "HIGH"
            "MAX" -> "MAX"
            else -> "STANDARD"
        }
        v257Prefs().edit().putString("bitrate_mode", v257BitrateModeState).apply()
        bindCamera()
        refreshHud()
        val message = "RECORD BITRATE • $v257BitrateModeState • ${coreDisplayedBitrate() / 1_000_000}Mbps DEVICE TARGET"
        toast(message)
        return v257BitrateModeState
    }

    private fun v257ApplyBitrate(base: Int): Int {
        val multiplier = when (v257BitrateModeState) {
            "SAFE" -> 0.75
            "HIGH" -> 1.15
            "MAX" -> 1.30
            else -> 1.0
        }
        return (base * multiplier).roundToInt().coerceAtLeast(8_000_000)
    }

    internal fun v257RecordingWatchdogEnabled(): Boolean = v257RecordingWatchdogOn

    internal fun v257ToggleRecordingWatchdog(): String {
        v257RecordingWatchdogOn = !v257RecordingWatchdogOn
        v257Prefs().edit().putBoolean("record_watchdog", v257RecordingWatchdogOn).apply()
        uiHandler.removeCallbacks(v257RecordingWatchdogRunnable)
        if (v257RecordingWatchdogOn && recording != null) {
            v257LastWatchdogNotice = ""
            uiHandler.post(v257RecordingWatchdogRunnable)
        }
        val message = if (v257RecordingWatchdogOn) "RECORD WATCHDOG ON" else "RECORD WATCHDOG OFF"
        toast(message)
        return message
    }

    internal fun v257ClipSafeFeedbackEnabled(): Boolean = v257ClipSafeFeedbackOn

    internal fun v257ToggleClipSafeFeedback(): String {
        v257ClipSafeFeedbackOn = !v257ClipSafeFeedbackOn
        v257Prefs().edit().putBoolean("clip_safe_feedback", v257ClipSafeFeedbackOn).apply()
        val message = if (v257ClipSafeFeedbackOn) "CLIP SAFE FEEDBACK ON" else "CLIP SAFE FEEDBACK OFF"
        toast(message)
        return message
    }

    internal fun v257HomeMotionEnabled(): Boolean = v257HomeMotionOn

    internal fun v257ToggleHomeMotion(): String {
        v257HomeMotionOn = !v257HomeMotionOn
        v257Prefs().edit().putBoolean("home_motion", v257HomeMotionOn).apply()
        v257UpdateHomeMotionLoop()
        val message = if (v257HomeMotionOn) "HOME MOTION UI ON" else "HOME MOTION UI OFF"
        toast(message)
        return message
    }

    internal fun v257HomeMotionStyle(): String = v257HomeMotionStyleState

    internal fun v257SetHomeMotionStyle(value: String): String {
        v257HomeMotionStyleState = when (value.trim().uppercase(Locale.US)) {
            "NORMAL" -> "NORMAL"
            "REDUCED" -> "REDUCED"
            else -> "SUBTLE"
        }
        v257Prefs().edit().putString("home_motion_style", v257HomeMotionStyleState).apply()
        v257UpdateHomeMotionLoop()
        toast("HOME MOTION • $v257HomeMotionStyleState")
        return v257HomeMotionStyleState
    }

    internal fun v257ReadyPulseEnabled(): Boolean = v257ReadyPulseOn

    internal fun v257ToggleReadyPulse(): String {
        v257ReadyPulseOn = !v257ReadyPulseOn
        v257Prefs().edit().putBoolean("record_ready_pulse", v257ReadyPulseOn).apply()
        v257UpdateHomeMotionLoop()
        val message = if (v257ReadyPulseOn) "RECORD READY PULSE ON" else "RECORD READY PULSE OFF"
        toast(message)
        return message
    }

    private fun v257UpdateHomeMotionLoop() {
        uiHandler.removeCallbacks(v257HomeMotionRunnable)
        if (::recordButton.isInitialized) {
            recordButton.animate().cancel()
            recordButton.scaleX = 1f
            recordButton.scaleY = 1f
            recordButton.alpha = 1f
        }
        if (v257HomeMotionOn && v257ReadyPulseOn && recording == null && ::recordButton.isInitialized) {
            v257ReadyPulsePhase = false
            uiHandler.postDelayed(v257HomeMotionRunnable, 240L)
        }
    }

    private fun v257AnimateDeckPress(view: View?, down: Boolean) {
        val target = view ?: return
        if (!v257HomeMotionOn) return
        val pressedScale = when (v257HomeMotionStyleState) {
            "NORMAL" -> 0.955f
            "REDUCED" -> 0.985f
            else -> 0.972f
        }
        target.animate().cancel()
        target.animate()
            .scaleX(if (down) pressedScale else 1f)
            .scaleY(if (down) pressedScale else 1f)
            .setDuration(if (down) 85L else 135L)
            .start()
    }

    private fun v257AnimateState(view: View?) {
        val target = view ?: return
        if (!v257HomeMotionOn) return
        target.animate().cancel()
        target.scaleX = 0.94f
        target.scaleY = 0.94f
        target.alpha = 0.72f
        target.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180L)
            .start()
    }

    internal fun v257RecordingCoreStatus(): String {
        val rec = if (recording != null) "REC" else "READY"
        val free = freeStorageGb()?.let { "${it}GB" } ?: "--"
        return "$rec • $coreDeliveredProfileLabel • ${coreDisplayedBitrate() / 1_000_000}Mbps $v257BitrateModeState • $activeVideoFpsLabel • $activeVideoDynamicRangeLabel • FREE $free • TC $v256TimecodeModeState${if (v259ConfidenceMeterOn) " • SMART ${v259RecordingConfidenceScore()}%" else ""}"
    }

    internal fun v257DeviceRecordingCaps(): String {
        val cam = camera ?: return "DEVICE CAPS • CAMERA NOT READY"
        return try {
            val caps = Recorder.getVideoCapabilities(cam.cameraInfo)
            val sdr = caps.getSupportedQualities(DynamicRange.SDR)
            val qualities = mutableListOf<String>()
            if (sdr.contains(Quality.UHD)) qualities.add("4K")
            if (sdr.contains(Quality.FHD)) qualities.add("1080")
            if (sdr.contains(Quality.HD)) qualities.add("720")
            val hdr = if (caps.supportedDynamicRanges.contains(DynamicRange.HLG_10_BIT)) "HLG10" else "SDR"
            "VIDEO ${qualities.joinToString("/").ifBlank { "DEVICE" }} • HDR $hdr • ACTIVE $coreDeliveredProfileLabel • CODEC DEVICE AUTO • ${coreDisplayedBitrate() / 1_000_000}Mbps"
        } catch (_: Exception) {
            "DEVICE CAPS • QUERY UNAVAILABLE"
        }
    }

    internal fun v257OpenMediaManager(): String {
        return try {
            startActivity(Intent(this, DevelopUgandaStoryPackagesActivity::class.java))
            "MEDIA MANAGER OPEN"
        } catch (_: Exception) {
            val message = "MEDIA MANAGER UNAVAILABLE"
            toast(message)
            message
        }
    }

    internal fun v257MotionStatus(): String =
        "HOME ${if (v257HomeMotionOn) v257HomeMotionStyleState else "OFF"} • READY PULSE ${if (v257ReadyPulseOn) "ON" else "OFF"} • SWITCH ${if (v256SwitchMotionOn) "MOTION" else "SNAP"}"

    private fun v261Prefs() =
        duSharedPreferences(
            "develop_uganda_v261_director_monitor",
            Context.MODE_PRIVATE
        )

    private fun loadV261DirectorMonitorPreferences() {
        val p = v261Prefs()
        v261RgbParadeOn = p.getBoolean("rgb_parade", false)
        v261VectorscopeOn = p.getBoolean("vectorscope", false)
        v261SkinToneReferenceOn = p.getBoolean("skin_tone_reference", true)
        v261HighlightShadowAssistOn = p.getBoolean("highlight_shadow_assist", true)
        v261FrameGuidesOn = p.getBoolean("frame_guides", true)
        v261FrameGuideAspectState = when (p.getString("frame_guide_aspect", "2.39:1")) {
            "1.85:1" -> "1.85:1"
            "16:9" -> "16:9"
            "9:16" -> "9:16"
            "4:5" -> "4:5"
            else -> "2.39:1"
        }
        v261DirectorHudModeState = when (p.getString("director_hud", "STANDARD")?.uppercase(Locale.US)) {
            "MINIMAL" -> "MINIMAL"
            "FULL" -> "FULL"
            else -> "STANDARD"
        }
        v261AnamorphicRatioState = when (p.getString("anamorphic", "OFF")?.uppercase(Locale.US)) {
            "1.33X" -> "1.33X"
            "1.55X" -> "1.55X"
            "1.8X" -> "1.8X"
            "2.0X" -> "2.0X"
            else -> "OFF"
        }
    }

    private fun v261SyncDirectorMonitor() {
        v255SyncMonitorViews()
        v261ApplyAnamorphicPreview()
    }

    internal fun v261RgbParadeEnabled(): Boolean = v261RgbParadeOn

    internal fun v261ToggleRgbParade(): String {
        v261RgbParadeOn = !v261RgbParadeOn
        v261Prefs().edit().putBoolean("rgb_parade", v261RgbParadeOn).apply()
        v261SyncDirectorMonitor()
        val message = if (v261RgbParadeOn) "RGB PARADE ON • SCREEN ONLY" else "RGB PARADE OFF"
        toast(message)
        return message
    }

    internal fun v261VectorscopeEnabled(): Boolean = v261VectorscopeOn

    internal fun v261ToggleVectorscope(): String {
        v261VectorscopeOn = !v261VectorscopeOn
        v261Prefs().edit().putBoolean("vectorscope", v261VectorscopeOn).apply()
        v261SyncDirectorMonitor()
        val message = if (v261VectorscopeOn) "VECTORSCOPE ON • SCREEN ONLY" else "VECTORSCOPE OFF"
        toast(message)
        return message
    }

    internal fun v261SkinToneReferenceEnabled(): Boolean = v261SkinToneReferenceOn

    internal fun v261ToggleSkinToneReference(): String {
        v261SkinToneReferenceOn = !v261SkinToneReferenceOn
        v261Prefs().edit().putBoolean("skin_tone_reference", v261SkinToneReferenceOn).apply()
        v261SyncDirectorMonitor()
        val message = if (v261SkinToneReferenceOn) "SKIN TONE REFERENCE ON" else "SKIN TONE REFERENCE OFF"
        toast(message)
        return message
    }

    internal fun v261HighlightShadowAssistEnabled(): Boolean = v261HighlightShadowAssistOn

    internal fun v261ToggleHighlightShadowAssist(): String {
        v261HighlightShadowAssistOn = !v261HighlightShadowAssistOn
        v261Prefs().edit().putBoolean("highlight_shadow_assist", v261HighlightShadowAssistOn).apply()
        v261SyncDirectorMonitor()
        val message = if (v261HighlightShadowAssistOn) "HIGHLIGHT + SHADOW ASSIST ON" else "HIGHLIGHT + SHADOW ASSIST OFF"
        toast(message)
        return message
    }

    internal fun v261FrameGuidesEnabled(): Boolean = v261FrameGuidesOn

    internal fun v261ToggleFrameGuides(): String {
        v261FrameGuidesOn = !v261FrameGuidesOn
        v261Prefs().edit().putBoolean("frame_guides", v261FrameGuidesOn).apply()
        v261SyncDirectorMonitor()
        val message = if (v261FrameGuidesOn) "DIRECTOR FRAME GUIDES ON • ${v261FrameGuideAspectState}" else "DIRECTOR FRAME GUIDES OFF"
        toast(message)
        return message
    }

    internal fun v261FrameGuideAspect(): String = v261FrameGuideAspectState

    internal fun v261SetFrameGuideAspect(value: String): String {
        v261FrameGuideAspectState = when (value.trim().uppercase(Locale.US)) {
            "1.85:1" -> "1.85:1"
            "16:9" -> "16:9"
            "9:16" -> "9:16"
            "4:5" -> "4:5"
            else -> "2.39:1"
        }
        v261Prefs().edit().putString("frame_guide_aspect", v261FrameGuideAspectState).apply()
        v261FrameGuidesOn = true
        v261Prefs().edit().putBoolean("frame_guides", true).apply()
        v261SyncDirectorMonitor()
        toast("FRAME GUIDE • $v261FrameGuideAspectState")
        return v261FrameGuideAspectState
    }

    internal fun v261DirectorHudMode(): String = v261DirectorHudModeState

    internal fun v261SetDirectorHudMode(value: String): String {
        v261DirectorHudModeState = when (value.trim().uppercase(Locale.US)) {
            "MINIMAL" -> "MINIMAL"
            "FULL" -> "FULL"
            else -> "STANDARD"
        }
        v261Prefs().edit().putString("director_hud", v261DirectorHudModeState).apply()
        v261SyncDirectorMonitor()
        toast("DIRECTOR HUD • $v261DirectorHudModeState")
        return v261DirectorHudModeState
    }

    internal fun v261AnamorphicRatio(): String = v261AnamorphicRatioState

    internal fun v261SetAnamorphicRatio(value: String): String {
        v261AnamorphicRatioState = when (value.trim().uppercase(Locale.US).replace("×", "X")) {
            "1.33X" -> "1.33X"
            "1.55X" -> "1.55X"
            "1.8X" -> "1.8X"
            "2.0X" -> "2.0X"
            else -> "OFF"
        }
        v261Prefs().edit().putString("anamorphic", v261AnamorphicRatioState).apply()
        v261ApplyAnamorphicPreview()
        val message = if (v261AnamorphicRatioState == "OFF") {
            "ANAMORPHIC DESQUEEZE OFF"
        } else {
            "ANAMORPHIC ${v261AnamorphicRatioState} • PREVIEW ONLY"
        }
        toast(message)
        return v261AnamorphicRatioState
    }

    private fun v261ApplyAnamorphicPreview() {
        if (!::previewView.isInitialized) return
        val scale = when (v261AnamorphicRatioState) {
            "1.33X" -> 1.33f
            "1.55X" -> 1.55f
            "1.8X" -> 1.80f
            "2.0X" -> 2.00f
            else -> 1.00f
        }
        val applyTransform = {
            previewView.animate().cancel()
            previewView.pivotX = previewView.width.toFloat() / 2f
            previewView.pivotY = previewView.height.toFloat() / 2f
            previewView.scaleX = scale
            previewView.scaleY = 1f
        }
        if (previewView.width > 0 && previewView.height > 0) {
            applyTransform()
        } else {
            previewView.post { applyTransform() }
        }
    }

    internal fun v261SetShutterAngle(value: String): String =
        v254SetShutter(value)

    internal fun v261DirectorMonitorStatus(): String = buildString {
        append("HUD ").append(v261DirectorHudModeState)
        append(" • RGB ").append(if (v261RgbParadeOn) "ON" else "OFF")
        append(" • VECTOR ").append(if (v261VectorscopeOn) "ON" else "OFF")
        append(" • SKIN ").append(if (v261SkinToneReferenceOn) "LINE" else "OFF")
        append(" • EXP ").append(if (v261HighlightShadowAssistOn) "ASSIST" else "OFF")
        append("\nGUIDE ").append(if (v261FrameGuidesOn) v261FrameGuideAspectState else "OFF")
        append(" • ANAMORPHIC ").append(v261AnamorphicRatioState)
        append(" • SHUTTER ").append(v254ShutterLabel())
        append(" • OUTPUT ").append(v271OutputMaster())
    }

    internal fun v261ResetDirectorMonitor(): String {
        v261Prefs().edit().clear().apply()
        loadV261DirectorMonitorPreferences()
        v261SyncDirectorMonitor()
        val message = "V261 DIRECTOR MONITOR RESET • SAFE DEFAULTS"
        toast(message)
        return message
    }

    private fun v262Prefs() =
        duSharedPreferences(
            "develop_uganda_v262_remote",
            Context.MODE_PRIVATE
        )

    private fun loadV262RemoteDirectorPreferences() {
        val p = v262Prefs()
        v262RemoteHostOn = p.getBoolean("remote_host", false)
        v262RemotePreviewOn = p.getBoolean("remote_preview", true)
        v262RemoteControlOn = p.getBoolean("remote_control", true)
        v262RecordPriorityOn = p.getBoolean("record_priority", true)
        v262AutoReconnectOn = p.getBoolean("auto_reconnect", true)
        v262DirectorMarkersOn = p.getBoolean("director_markers", true)
        v262PreviewQualityState = when (p.getString("preview_quality", "BALANCED")?.uppercase(Locale.US)) {
            "LOW" -> "LOW"
            "HIGH" -> "HIGH"
            else -> "BALANCED"
        }
        val savedPin = p.getString("pairing_pin", "2626")?.filter { it.isDigit() }?.take(4).orEmpty()
        v262PairingPinState = if (savedPin.length == 4) savedPin else "2626"
    }

    private fun v262EnsureRemoteServerState() {
        if (!v262RemoteHostOn) {
            v262RemoteServer?.stop()
            v262RemoteServer = null
            return
        }
        val current = v262RemoteServer
        if (current != null && current.isRunning()) return
        current?.stop()
        v262RemoteServer = DevelopUgandaV262RemoteServer(this).also { it.start() }
    }

    internal fun v262RemoteHostEnabled(): Boolean = v262RemoteHostOn
    internal fun v262RemotePreviewEnabled(): Boolean = v262RemotePreviewOn
    internal fun v262RemoteControlEnabled(): Boolean = v262RemoteControlOn
    internal fun v262RecordPriorityEnabled(): Boolean = v262RecordPriorityOn
    internal fun v262AutoReconnectEnabled(): Boolean = v262AutoReconnectOn
    internal fun v262DirectorMarkersEnabled(): Boolean = v262DirectorMarkersOn
    internal fun v262RemotePreviewQuality(): String = v262PreviewQualityState
    internal fun v262RemotePin(): String = v262PairingPinState

    private fun v262RequestNearbyPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED) return
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 262)
    }

    internal fun v262ToggleRemoteHost(): String {
        v262RemoteHostOn = !v262RemoteHostOn
        v262Prefs().edit().putBoolean("remote_host", v262RemoteHostOn).apply()
        if (v262RemoteHostOn) v262RequestNearbyPermissionIfNeeded()
        v262EnsureRemoteServerState()
        val message = if (v262RemoteHostOn) "REMOTE CAMERA HOST ON • ${DevelopUgandaV262Network.localIpv4()}:${DevelopUgandaV262Network.PORT}" else "REMOTE CAMERA HOST OFF"
        toast(message)
        return message
    }

    internal fun v262ToggleRemotePreview(): String {
        v262RemotePreviewOn = !v262RemotePreviewOn
        v262Prefs().edit().putBoolean("remote_preview", v262RemotePreviewOn).apply()
        val message = if (v262RemotePreviewOn) "REMOTE PREVIEW ON" else "REMOTE PREVIEW OFF • CONTROL CAN REMAIN ACTIVE"
        toast(message)
        return message
    }

    internal fun v262ToggleRemoteControl(): String {
        v262RemoteControlOn = !v262RemoteControlOn
        v262Prefs().edit().putBoolean("remote_control", v262RemoteControlOn).apply()
        val message = if (v262RemoteControlOn) "REMOTE CONTROL ON" else "REMOTE CONTROL OFF • VIEW ONLY"
        toast(message)
        return message
    }

    internal fun v262ToggleRecordPriority(): String {
        v262RecordPriorityOn = !v262RecordPriorityOn
        v262Prefs().edit().putBoolean("record_priority", v262RecordPriorityOn).apply()
        val message = if (v262RecordPriorityOn) "RECORDING PRIORITY ON • REMOTE PREVIEW THROTTLES WHILE REC" else "RECORDING PRIORITY OFF"
        toast(message)
        return message
    }

    internal fun v262ToggleAutoReconnect(): String {
        v262AutoReconnectOn = !v262AutoReconnectOn
        v262Prefs().edit().putBoolean("auto_reconnect", v262AutoReconnectOn).apply()
        val message = if (v262AutoReconnectOn) "REMOTE AUTO RECONNECT ON" else "REMOTE AUTO RECONNECT OFF"
        toast(message)
        return message
    }

    internal fun v262ToggleDirectorMarkers(): String {
        v262DirectorMarkersOn = !v262DirectorMarkersOn
        v262Prefs().edit().putBoolean("director_markers", v262DirectorMarkersOn).apply()
        val message = if (v262DirectorMarkersOn) "DIRECTOR MARKERS ON" else "DIRECTOR MARKERS OFF"
        toast(message)
        return message
    }

    internal fun v262SetRemotePreviewQuality(value: String): String {
        v262PreviewQualityState = when (value.trim().uppercase(Locale.US)) {
            "LOW" -> "LOW"
            "HIGH" -> "HIGH"
            else -> "BALANCED"
        }
        v262Prefs().edit().putString("preview_quality", v262PreviewQualityState).apply()
        toast("REMOTE PREVIEW • $v262PreviewQualityState")
        return v262PreviewQualityState
    }

    internal fun v262RegeneratePairingPin(): String {
        val pin = (1000 + java.util.Random().nextInt(9000)).toString()
        v262PairingPinState = pin
        v262Prefs().edit().putString("pairing_pin", pin).apply()
        toast("V262 PAIRING PIN • $pin")
        return pin
    }

    internal fun v262OpenRemoteDirector(): String {
        return try {
            startActivity(Intent(this, DevelopUgandaV262RemoteDirectorActivity::class.java))
            "REMOTE DIRECTOR OPEN"
        } catch (_: Exception) {
            "REMOTE DIRECTOR UNAVAILABLE"
        }
    }

    internal fun v262RemoteStatus(): String = buildString {
        append(v262RemoteServer?.statusLine() ?: if (v262RemoteHostOn) "HOST STARTING" else "HOST OFF")
        append("\nPIN ").append(v262PairingPinState)
        append(" • PREVIEW ").append(if (v262RemotePreviewOn) v262PreviewQualityState else "OFF")
        append(" • CONTROL ").append(if (v262RemoteControlOn) "ON" else "VIEW ONLY")
        append(" • REC PRIORITY ").append(if (v262RecordPriorityOn) "ON" else "OFF")
        append("\nPAIR SECOND PHONE TO ").append(DevelopUgandaV262Network.localIpv4()).append(":").append(DevelopUgandaV262Network.PORT)
    }

    internal fun v262RemoteStatusSnapshot(): String = buildString {
        append("V263 REMOTE CAMERA")
        append("\nRECORDING • ").append(if (recording != null) "ON" else "STBY")
        append(" • TC ").append(tc())
        append("\n").append(v260ProjectStatus())
        val zoom = v242ZoomSnapshot()
        append("\nZOOM ").append(String.format(Locale.US, "%.2fx", zoom.getOrNull(2) ?: 1f))
        append(" • BAT ").append(batteryPct()?.let { "$it%" } ?: "--")
        append(" • FREE ").append(freeStorageGb()?.let { "${it}GB" } ?: "--")
        append(" • THERMAL ").append(thermalStateLabel())
        append("\nMIC ").append(audioStateLabel)
        append(" • LEVEL ").append((audioAmplitude * 100.0).roundToInt()).append("%")
        append(" • SMART ").append(v259RecordingConfidenceScore()).append("%")
        append(" • REMOTE MARKS ").append(v262RemoteMarkers.size)
        append("\nPROXY ").append(DevelopUgandaV265ProxyManager.state(this@DevelopUgandaCameraActivity))
    }

    internal fun v262RemotePreviewFrame(): ByteArray? {
        if (!v262RemotePreviewOn || !::previewView.isInitialized) return null
        val result = java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
        val latch = java.util.concurrent.CountDownLatch(1)
        runOnUiThread {
            try {
                val source = previewView.bitmap
                if (source != null && source.width > 0 && source.height > 0) {
                    val requestedSide = when (v262PreviewQualityState) {
                        "LOW" -> 480
                        "HIGH" -> 960
                        else -> 720
                    }
                    val side = if (v262RecordPriorityOn && recording != null) minOf(requestedSide, 480) else requestedSide
                    val scale = minOf(1f, side.toFloat() / maxOf(source.width, source.height).toFloat())
                    val width = (source.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (source.height * scale).roundToInt().coerceAtLeast(1)
                    val scaled = if (width == source.width && height == source.height) source else android.graphics.Bitmap.createScaledBitmap(source, width, height, true)
                    val quality = when {
                        v262RecordPriorityOn && recording != null -> 42
                        v262PreviewQualityState == "LOW" -> 42
                        v262PreviewQualityState == "HIGH" -> 72
                        else -> 58
                    }
                    val stream = java.io.ByteArrayOutputStream()
                    scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, stream)
                    result.set(stream.toByteArray())
                    if (scaled !== source) scaled.recycle()
                }
            } catch (_: Exception) {
                result.set(null)
            } finally {
                latch.countDown()
            }
        }
        return try {
            latch.await(1800L, TimeUnit.MILLISECONDS)
            result.get()
        } catch (_: Exception) {
            null
        }
    }

    internal fun v262RemoteCommandSync(command: String): String {
        val result = java.util.concurrent.atomic.AtomicReference("REMOTE COMMAND TIMEOUT")
        val latch = java.util.concurrent.CountDownLatch(1)
        val run = {
            try {
                result.set(v262HandleRemoteCommand(command))
            } finally {
                latch.countDown()
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else runOnUiThread(run)
        return try {
            latch.await(2200L, TimeUnit.MILLISECONDS)
            result.get()
        } catch (_: Exception) {
            "REMOTE COMMAND ERROR"
        }
    }

    private fun v262HandleRemoteCommand(command: String): String {
        val parts = command.trim().split(Regex("\\s+"))
        if (parts.isEmpty()) return "EMPTY COMMAND"
        return when (parts[0].uppercase(Locale.US)) {
            "REC_START" -> {
                if (captureModes.getOrNull(captureModeIndex) == "PHOTO") {
                    "REMOTE RECORD REQUIRES VIDEO MODE"
                } else if (recording != null) {
                    "ALREADY RECORDING"
                } else {
                    toggleRecording()
                    "RECORD START REQUESTED"
                }
            }
            "REC_STOP" -> {
                if (recording == null) "ALREADY STOPPED" else {
                    recording?.stop()
                    "SAFE STOP REQUESTED"
                }
            }
            "FOCUS" -> {
                val nx = parts.getOrNull(1)?.toFloatOrNull()
                val ny = parts.getOrNull(2)?.toFloatOrNull()
                if (nx == null || ny == null) "FOCUS COMMAND INVALID" else v262RemoteFocus(nx, ny)
            }
            "ZOOM" -> {
                val value = parts.getOrNull(1)?.toFloatOrNull()
                if (value == null) "ZOOM COMMAND INVALID" else {
                    v242SetZoomNormalized(value.coerceIn(0f, 1f))
                    "ZOOM • ${String.format(Locale.US, "%.0f%%", value.coerceIn(0f, 1f) * 100f)}"
                }
            }
            "SET_WB" -> {
                val value = parts.drop(1).joinToString(" ").ifBlank { "AUTO" }
                v254SetWhiteBalance(value)
            }
            "SET_SHUTTER" -> {
                val value = parts.drop(1).joinToString(" ").ifBlank { "AUTO" }
                v254SetShutter(value)
            }
            "SET_ISO" -> {
                val value = parts.drop(1).joinToString(" ").ifBlank { "AUTO" }
                v254SetIso(value)
            }
            "NEXT_TAKE" -> v260NextTake()
            "NEXT_SCENE" -> v260NextScene()
            "SET_TAKE" -> {
                val value = parts.getOrNull(1)?.toIntOrNull()
                if (value == null) "TAKE COMMAND INVALID" else v260SetTakeNumber(value)
            }
            "SET_SCENE" -> {
                val value = parts.getOrNull(1)?.toIntOrNull()
                if (value == null) "SCENE COMMAND INVALID" else v260SetSceneNumber(value)
            }
            "MARK" -> v262AddRemoteMarker(parts.drop(1).joinToString(" ").ifBlank { "MARK" })
            "SYNC" -> "CLOCK ${System.currentTimeMillis()} • TC ${tc()} • ${v260CameraName()}"
            else -> "UNKNOWN COMMAND"
        }
    }

    private fun v262RemoteFocus(nx: Float, ny: Float): String {
        val cam = camera ?: return "CAMERA NOT READY"
        if (!::previewView.isInitialized || previewView.width <= 0 || previewView.height <= 0) return "PREVIEW NOT READY"
        return try {
            releaseFocusLockForTap()
            val x = nx.coerceIn(0f, 1f) * previewView.width.toFloat()
            val y = ny.coerceIn(0f, 1f) * previewView.height.toFloat()
            val point = previewView.meteringPointFactory.createPoint(x, y, 0.20f)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            ).setAutoCancelDuration(2L, TimeUnit.SECONDS).build()
            cam.cameraControl.startFocusAndMetering(action)
            showFocusReticle(x, y, false)
            "REMOTE AF + AE + AWB"
        } catch (_: Exception) {
            "REMOTE FOCUS UNAVAILABLE"
        }
    }

    private fun v262AddRemoteMarker(labelValue: String): String {
        if (!v262DirectorMarkersOn) return "DIRECTOR MARKERS OFF"
        val label = labelValue.trim().uppercase(Locale.US).take(24).ifBlank { "MARK" }
        val marker = "$label @ ${tc()}"
        if (v262RemoteMarkers.size >= 80) v262RemoteMarkers.removeAt(0)
        v262RemoteMarkers.add(marker)
        toast("DIRECTOR MARK • $label")
        return marker
    }

    private fun v262WriteRemoteMarkerJournal(name: String) {
        if (v262RemoteMarkers.isEmpty()) return
        try {
            val base = getExternalFilesDir(null) ?: filesDir
            val dir = File(base, "v262-director-markers").apply { mkdirs() }
            File(dir, "${name.take(90)}-markers.txt").writeText(
                buildString {
                    append("develop.uganda V262 DIRECTOR MARKERS\n")
                    append(v260ProjectStatus()).append("\n")
                    v262RemoteMarkers.forEach { append(it).append("\n") }
                },
                Charsets.UTF_8
            )
        } catch (_: Exception) {
        }
    }

    private fun v263Prefs() =
        duSharedPreferences(
            "develop_uganda_v263_multicam",
            Context.MODE_PRIVATE
        )

    private fun loadV263MultiCamPreferences() {
        val p = v263Prefs()
        v263SyncRecordOn = p.getBoolean("sync_record", true)
        v263TallyOn = p.getBoolean("tally", true)
        v263AutoPreviewQualityOn = p.getBoolean("auto_preview_quality", true)
        v263SyncSlateOn = p.getBoolean("sync_slate", true)
        v263GridMotionOn = p.getBoolean("grid_motion", true)
        v263MaxCamerasState = p.getInt("max_cameras", 4).coerceIn(2, 4)
        v263DefaultGroupState = when (p.getString("default_group", "ALL")?.uppercase(Locale.US)) {
            "A+B" -> "A+B"
            "C+D" -> "C+D"
            "SELECTED" -> "SELECTED"
            else -> "ALL"
        }
    }

    internal fun v263SyncRecordEnabled(): Boolean = v263SyncRecordOn
    internal fun v263TallyEnabled(): Boolean = v263TallyOn
    internal fun v263AutoPreviewQualityEnabled(): Boolean = v263AutoPreviewQualityOn
    internal fun v263SyncSlateEnabled(): Boolean = v263SyncSlateOn
    internal fun v263GridMotionEnabled(): Boolean = v263GridMotionOn
    internal fun v263MaxCameras(): Int = v263MaxCamerasState
    internal fun v263DefaultGroup(): String = v263DefaultGroupState

    internal fun v263ToggleSyncRecord(): String {
        v263SyncRecordOn = !v263SyncRecordOn
        v263Prefs().edit().putBoolean("sync_record", v263SyncRecordOn).apply()
        val message = if (v263SyncRecordOn) "MULTI-CAM SYNC RECORD ON" else "MULTI-CAM SYNC RECORD OFF"
        toast(message)
        return message
    }

    internal fun v263ToggleTally(): String {
        v263TallyOn = !v263TallyOn
        v263Prefs().edit().putBoolean("tally", v263TallyOn).apply()
        val message = if (v263TallyOn) "DIRECTOR TALLY ON" else "DIRECTOR TALLY OFF"
        toast(message)
        return message
    }

    internal fun v263ToggleAutoPreviewQuality(): String {
        v263AutoPreviewQualityOn = !v263AutoPreviewQualityOn
        v263Prefs().edit().putBoolean("auto_preview_quality", v263AutoPreviewQualityOn).apply()
        val message = if (v263AutoPreviewQualityOn) "AUTO PREVIEW QUALITY ON" else "AUTO PREVIEW QUALITY OFF"
        toast(message)
        return message
    }

    internal fun v263ToggleSyncSlate(): String {
        v263SyncSlateOn = !v263SyncSlateOn
        v263Prefs().edit().putBoolean("sync_slate", v263SyncSlateOn).apply()
        val message = if (v263SyncSlateOn) "MASTER SLATE SYNC ON" else "MASTER SLATE SYNC OFF"
        toast(message)
        return message
    }

    internal fun v263ToggleGridMotion(): String {
        v263GridMotionOn = !v263GridMotionOn
        v263Prefs().edit().putBoolean("grid_motion", v263GridMotionOn).apply()
        val message = if (v263GridMotionOn) "MULTI-CAM GRID MOTION ON" else "MULTI-CAM GRID MOTION OFF"
        toast(message)
        return message
    }

    internal fun v263SetMaxCameras(value: Int): String {
        v263MaxCamerasState = value.coerceIn(2, 4)
        v263Prefs().edit().putInt("max_cameras", v263MaxCamerasState).apply()
        toast("DIRECTOR GRID • ${v263MaxCamerasState} CAMERAS MAX")
        return v263MaxCamerasState.toString()
    }

    internal fun v263SetDefaultGroup(value: String): String {
        v263DefaultGroupState = when (value.trim().uppercase(Locale.US)) {
            "A+B" -> "A+B"
            "C+D" -> "C+D"
            "SELECTED" -> "SELECTED"
            else -> "ALL"
        }
        v263Prefs().edit().putString("default_group", v263DefaultGroupState).apply()
        toast("MULTI-CAM GROUP • $v263DefaultGroupState")
        return v263DefaultGroupState
    }

    internal fun v263OpenMultiCamDirector(): String {
        return try {
            startActivity(Intent(this, DevelopUgandaV263MultiCamDirectorActivity::class.java))
            "MULTI-CAM DIRECTOR OPEN"
        } catch (_: Exception) {
            "MULTI-CAM DIRECTOR UNAVAILABLE"
        }
    }

    internal fun v263MultiCamStatus(): String = buildString {
        append("V263 MULTI-CAM DIRECTOR")
        append(" • MAX ").append(v263MaxCamerasState)
        append(" • GROUP ").append(v263DefaultGroupState)
        append(" • SYNC REC ").append(if (v263SyncRecordOn) "ON" else "OFF")
        append(" • SLATE ").append(if (v263SyncSlateOn) "SYNC" else "LOCAL")
        append("\nTALLY ").append(if (v263TallyOn) "ON" else "OFF")
        append(" • AUTO PREVIEW ").append(if (v263AutoPreviewQualityOn) "ON" else "OFF")
        append(" • GRID MOTION ").append(if (v263GridMotionOn) "ON" else "OFF")
        append(" • HOST ").append(if (v262RemoteHostOn) "READY" else "OFF")
    }

    private fun v264Prefs() =
        duSharedPreferences("develop_uganda_v264_live_cut", Context.MODE_PRIVATE)

    private fun loadV264LiveCutPreferences() {
        val p = v264Prefs()
        v264CutRecordingOn = p.getBoolean("cut_recording", true)
        v264ProgramTallyOn = p.getBoolean("program_tally", true)
        v264AutoClockSyncOn = p.getBoolean("auto_clock_sync", true)
        v264PushCameraSettingsOn = p.getBoolean("push_camera_settings", true)
        v264HandoffSuggestionOn = p.getBoolean("handoff_suggestion", true)
        v264NetworkDiagnosticsOn = p.getBoolean("network_diagnostics", true)
        v264DirectorMotionOn = p.getBoolean("director_motion", true)
        v264ExportFormatState = when (p.getString("export_format", "JSON")?.uppercase(Locale.US)) { "CSV" -> "CSV"; else -> "JSON" }
        v264TransportModeState = when (p.getString("transport_mode", "AUTO")?.uppercase(Locale.US)) { "WIFI" -> "WIFI"; "HOTSPOT" -> "HOTSPOT"; else -> "AUTO" }
    }

    internal fun v264CutRecordingEnabled(): Boolean = v264CutRecordingOn
    internal fun v264ProgramTallyEnabled(): Boolean = v264ProgramTallyOn
    internal fun v264AutoClockSyncEnabled(): Boolean = v264AutoClockSyncOn
    internal fun v264PushSettingsEnabled(): Boolean = v264PushCameraSettingsOn
    internal fun v264HandoffSuggestionEnabled(): Boolean = v264HandoffSuggestionOn
    internal fun v264NetworkDiagnosticsEnabled(): Boolean = v264NetworkDiagnosticsOn
    internal fun v264DirectorMotionEnabled(): Boolean = v264DirectorMotionOn
    internal fun v264ExportFormat(): String = v264ExportFormatState
    internal fun v264TransportMode(): String = v264TransportModeState

    private fun v264Toggle(key: String, current: Boolean, label: String): Pair<Boolean, String> {
        val next = !current
        v264Prefs().edit().putBoolean(key, next).apply()
        val message = "$label ${if (next) "ON" else "OFF"}"
        toast(message)
        return next to message
    }

    internal fun v264ToggleCutRecording(): String { val r = v264Toggle("cut_recording", v264CutRecordingOn, "DIRECTOR CUT RECORDING"); v264CutRecordingOn = r.first; return r.second }
    internal fun v264ToggleProgramTally(): String { val r = v264Toggle("program_tally", v264ProgramTallyOn, "PROGRAM TALLY"); v264ProgramTallyOn = r.first; return r.second }
    internal fun v264ToggleAutoClockSync(): String { val r = v264Toggle("auto_clock_sync", v264AutoClockSyncOn, "AUTO CLOCK SYNC"); v264AutoClockSyncOn = r.first; return r.second }
    internal fun v264TogglePushSettings(): String { val r = v264Toggle("push_camera_settings", v264PushCameraSettingsOn, "PUSH CAMERA SETTINGS"); v264PushCameraSettingsOn = r.first; return r.second }
    internal fun v264ToggleHandoffSuggestion(): String { val r = v264Toggle("handoff_suggestion", v264HandoffSuggestionOn, "HANDOFF SUGGESTION"); v264HandoffSuggestionOn = r.first; return r.second }
    internal fun v264ToggleNetworkDiagnostics(): String { val r = v264Toggle("network_diagnostics", v264NetworkDiagnosticsOn, "NETWORK DIAGNOSTICS"); v264NetworkDiagnosticsOn = r.first; return r.second }
    internal fun v264ToggleDirectorMotion(): String { val r = v264Toggle("director_motion", v264DirectorMotionOn, "DIRECTOR MOTION"); v264DirectorMotionOn = r.first; return r.second }

    internal fun v264SetExportFormat(value: String): String {
        v264ExportFormatState = if (value.trim().uppercase(Locale.US) == "CSV") "CSV" else "JSON"
        v264Prefs().edit().putString("export_format", v264ExportFormatState).apply()
        toast("CUT LIST EXPORT • $v264ExportFormatState")
        return v264ExportFormatState
    }

    internal fun v264SetTransportMode(value: String): String {
        v264TransportModeState = when (value.trim().uppercase(Locale.US)) { "WIFI" -> "WIFI"; "HOTSPOT" -> "HOTSPOT"; else -> "AUTO" }
        v264Prefs().edit().putString("transport_mode", v264TransportModeState).apply()
        toast("DIRECTOR TRANSPORT • $v264TransportModeState")
        return v264TransportModeState
    }

    internal fun v264OpenLiveCutDirector(): String = try {
        startActivity(Intent(this, DevelopUgandaV264LiveCutDirectorActivity::class.java))
        "V264 LIVE CUT DIRECTOR OPEN"
    } catch (_: Exception) {
        "V264 LIVE CUT DIRECTOR UNAVAILABLE"
    }

    internal fun v264LiveCutStatus(): String = buildString {
        append("V264 LIVE CUT + AUTO-SYNC")
        append(" • CUTS ").append(if (v264CutRecordingOn) "REC" else "OFF")
        append(" • PGM TALLY ").append(if (v264ProgramTallyOn) "ON" else "OFF")
        append(" • CLOCK ").append(if (v264AutoClockSyncOn) "AUTO" else "MANUAL")
        append(" • PUSH ").append(if (v264PushCameraSettingsOn) "ON" else "OFF")
        append("\nHANDOFF ").append(if (v264HandoffSuggestionOn) "SUGGEST" else "OFF")
        append(" • NET DIAG ").append(if (v264NetworkDiagnosticsOn) "ON" else "OFF")
        append(" • EXPORT ").append(v264ExportFormatState)
        append(" • TRANSPORT ").append(v264TransportModeState)
    }


    private fun v265Prefs() =
        duSharedPreferences("develop_uganda_v265_proxy", Context.MODE_PRIVATE)

    private fun loadV265ProxyPreferences() {
        val p = v265Prefs()
        v265ProxyEnabledState = p.getBoolean("proxy_enabled", true)
        v265AutoTransferState = p.getBoolean("auto_transfer", true)
        v265ProxyQualityState = when (p.getString("proxy_quality", "BALANCED")?.uppercase(Locale.US)) {
            "LOW" -> "LOW"
            "HIGH" -> "HIGH"
            else -> "BALANCED"
        }
        v265ProxyIntegrityState = p.getBoolean("proxy_integrity", true)
        v265ReviewMotionState = p.getBoolean("review_motion", true)
    }

    internal fun v265ProxyEnabled(): Boolean = v265ProxyEnabledState
    internal fun v265AutoTransferEnabled(): Boolean = v265AutoTransferState
    internal fun v265ProxyQuality(): String = v265ProxyQualityState
    internal fun v265ProxyIntegrityEnabled(): Boolean = v265ProxyIntegrityState
    internal fun v265ReviewMotionEnabled(): Boolean = v265ReviewMotionState

    private fun v265Toggle(key: String, current: Boolean, label: String): Pair<Boolean, String> {
        val next = !current
        v265Prefs().edit().putBoolean(key, next).apply()
        val text = "$label ${if (next) "ON" else "OFF"}"
        toast(text)
        return next to text
    }

    internal fun v265ToggleProxy(): String {
        val r = v265Toggle("proxy_enabled", v265ProxyEnabledState, "REVIEW PROXY")
        v265ProxyEnabledState = r.first
        return r.second
    }

    internal fun v265ToggleAutoTransfer(): String {
        val r = v265Toggle("auto_transfer", v265AutoTransferState, "AUTO PROXY PULL")
        v265AutoTransferState = r.first
        return r.second
    }

    internal fun v265ToggleProxyIntegrity(): String {
        val r = v265Toggle("proxy_integrity", v265ProxyIntegrityState, "PROXY INTEGRITY CHECK")
        v265ProxyIntegrityState = r.first
        return r.second
    }

    internal fun v265ToggleReviewMotion(): String {
        val r = v265Toggle("review_motion", v265ReviewMotionState, "REVIEW MOTION")
        v265ReviewMotionState = r.first
        return r.second
    }

    internal fun v265SetProxyQuality(value: String): String {
        v265ProxyQualityState = when (value.trim().uppercase(Locale.US)) {
            "LOW" -> "LOW"
            "HIGH" -> "HIGH"
            else -> "BALANCED"
        }
        v265Prefs().edit().putString("proxy_quality", v265ProxyQualityState).apply()
        toast("REVIEW PROXY • $v265ProxyQualityState")
        return v265ProxyQualityState
    }

    internal fun v265OpenProxySyncReview(): String = try {
        startActivity(Intent(this, DevelopUgandaV265ProxySyncReviewActivity::class.java))
        "V265 PROXY SYNC + REVIEW OPEN"
    } catch (_: Exception) {
        "V265 REVIEW UNAVAILABLE"
    }

    internal fun v265ProxyStatus(): String = buildString {
        append("V265 PROXY SYNC + REVIEW")
        append(" • PROXY ").append(if (v265ProxyEnabledState) "ON" else "OFF")
        append(" • QUALITY ").append(v265ProxyQualityState)
        append(" • AUTO PULL ").append(if (v265AutoTransferState) "ON" else "OFF")
        append("\nSTATE ").append(DevelopUgandaV265ProxyManager.state(this@DevelopUgandaCameraActivity))
        append(" • INTEGRITY ").append(if (v265ProxyIntegrityState) "ON" else "OFF")
        append(" • REVIEW MOTION ").append(if (v265ReviewMotionState) "ON" else "OFF")
    }

    private fun v260Prefs() =
        duSharedPreferences(
            "develop_uganda_v260_project_control_failsafe",
            Context.MODE_PRIVATE
        )

    private fun loadV260ProjectControlPreferences() {
        val p = v260Prefs()
        v260ProjectModeOn = p.getBoolean("project_mode", true)
        v260ProjectNameState = p.getString("project_name", "FIELD PROJECT")?.trim()?.take(40)?.ifBlank { "FIELD PROJECT" } ?: "FIELD PROJECT"
        v260CameraNameState = p.getString("camera_name", "CAM A")?.trim()?.take(24)?.ifBlank { "CAM A" } ?: "CAM A"
        v260SceneNumberState = p.getInt("scene_number", 1).coerceIn(1, 999)
        v260TakeNumberState = p.getInt("take_number", 1).coerceIn(1, 999)
        v260AutoTakeCounterOn = p.getBoolean("auto_take_counter", true)
        v260StorageReservationOn = p.getBoolean("storage_reservation", true)
        v260StorageReserveGbState = p.getInt("storage_reserve_gb", 2).let { if (it in setOf(2, 5, 10)) it else 2 }
        v260ThermalStrategyState = when (p.getString("thermal_strategy", "BALANCED")?.uppercase(Locale.US)) {
            "QUALITY FIRST" -> "QUALITY FIRST"
            "ENDURANCE" -> "ENDURANCE"
            else -> "BALANCED"
        }
    }

    internal fun v260ProjectModeEnabled(): Boolean = v260ProjectModeOn

    internal fun v260ToggleProjectMode(): String {
        v260ProjectModeOn = !v260ProjectModeOn
        v260Prefs().edit().putBoolean("project_mode", v260ProjectModeOn).apply()
        val message = if (v260ProjectModeOn) "PROJECT MODE ON" else "PROJECT MODE OFF • NORMAL CLIP NAMING"
        toast(message)
        return message
    }

    internal fun v260ProjectName(): String = v260ProjectNameState

    internal fun v260SetProjectName(value: String): String {
        v260ProjectNameState = value.trim().replace(Regex("\\s+"), " ").take(40).ifBlank { "FIELD PROJECT" }
        v260Prefs().edit().putString("project_name", v260ProjectNameState).apply()
        toast("PROJECT • $v260ProjectNameState")
        return v260ProjectNameState
    }

    internal fun v260CameraName(): String = v260CameraNameState

    internal fun v260SetCameraName(value: String): String {
        v260CameraNameState = value.trim().replace(Regex("\\s+"), " ").take(24).ifBlank { "CAM A" }
        v260Prefs().edit().putString("camera_name", v260CameraNameState).apply()
        toast("CAMERA NAME • $v260CameraNameState")
        return v260CameraNameState
    }

    internal fun v260SceneNumber(): Int = v260SceneNumberState
    internal fun v260TakeNumber(): Int = v260TakeNumberState

    internal fun v260SetSceneNumber(value: Int): String {
        v260SceneNumberState = value.coerceIn(1, 999)
        v260Prefs().edit().putInt("scene_number", v260SceneNumberState).apply()
        toast("SCENE • %03d".format(Locale.US, v260SceneNumberState))
        return v260SlateLabel()
    }

    internal fun v260SetTakeNumber(value: Int): String {
        v260TakeNumberState = value.coerceIn(1, 999)
        v260Prefs().edit().putInt("take_number", v260TakeNumberState).apply()
        toast("TAKE • %03d".format(Locale.US, v260TakeNumberState))
        return v260SlateLabel()
    }

    internal fun v260NextScene(): String {
        v260SceneNumberState = (v260SceneNumberState + 1).coerceAtMost(999)
        v260TakeNumberState = 1
        v260Prefs().edit().putInt("scene_number", v260SceneNumberState).putInt("take_number", 1).apply()
        return v260SlateLabel()
    }

    internal fun v260PreviousScene(): String {
        v260SceneNumberState = (v260SceneNumberState - 1).coerceAtLeast(1)
        v260TakeNumberState = 1
        v260Prefs().edit().putInt("scene_number", v260SceneNumberState).putInt("take_number", 1).apply()
        return v260SlateLabel()
    }

    internal fun v260NextTake(): String = v260SetTakeNumber(v260TakeNumberState + 1)
    internal fun v260PreviousTake(): String = v260SetTakeNumber(v260TakeNumberState - 1)

    internal fun v260AutoTakeEnabled(): Boolean = v260AutoTakeCounterOn

    internal fun v260ToggleAutoTake(): String {
        v260AutoTakeCounterOn = !v260AutoTakeCounterOn
        v260Prefs().edit().putBoolean("auto_take_counter", v260AutoTakeCounterOn).apply()
        val message = if (v260AutoTakeCounterOn) "AUTO TAKE COUNTER ON" else "AUTO TAKE COUNTER OFF"
        toast(message)
        return message
    }

    internal fun v260StorageReservationEnabled(): Boolean = v260StorageReservationOn

    internal fun v260ToggleStorageReservation(): String {
        v260StorageReservationOn = !v260StorageReservationOn
        v260Prefs().edit().putBoolean("storage_reservation", v260StorageReservationOn).apply()
        val message = if (v260StorageReservationOn) "STORAGE RESERVATION ON • ${v260StorageReserveGbState}GB" else "STORAGE RESERVATION OFF • 1GB EMERGENCY BLOCK REMAINS"
        toast(message)
        return message
    }

    internal fun v260StorageReserveGb(): Int = v260StorageReserveGbState

    internal fun v260SetStorageReserveGb(value: Int): String {
        v260StorageReserveGbState = if (value in setOf(2, 5, 10)) value else 2
        v260Prefs().edit().putInt("storage_reserve_gb", v260StorageReserveGbState).apply()
        toast("PROJECT STORAGE RESERVE • ${v260StorageReserveGbState}GB")
        return "${v260StorageReserveGbState}GB"
    }

    internal fun v260ThermalStrategy(): String = v260ThermalStrategyState

    internal fun v260SetThermalStrategy(value: String): String {
        v260ThermalStrategyState = when (value.trim().uppercase(Locale.US)) {
            "QUALITY FIRST" -> "QUALITY FIRST"
            "ENDURANCE" -> "ENDURANCE"
            else -> "BALANCED"
        }
        v260Prefs().edit().putString("thermal_strategy", v260ThermalStrategyState).apply()
        toast("THERMAL STRATEGY • $v260ThermalStrategyState")
        return v260ThermalStrategyState
    }

    internal fun v260SlateLabel(): String =
        if (!v260ProjectModeOn) "PROJECT OFF"
        else "%s • %s • S%03d T%03d".format(
            Locale.US,
            v260ProjectNameState,
            v260CameraNameState,
            v260SceneNumberState,
            v260TakeNumberState
        )

    private fun v260SnapshotSlateForRecording() {
        v260ActiveSceneNumber = v260SceneNumberState
        v260ActiveTakeNumber = v260TakeNumberState
    }

    private fun v260FilePart(value: String, fallback: String): String =
        value.trim().uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_-]+"), "_")
            .trim('_')
            .take(24)
            .ifBlank { fallback }

    private fun v260ClipBaseName(activeReportId: String, fallback: String): String {
        if (!v260ProjectModeOn) return fallback
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val project = v260FilePart(v260ProjectNameState, "PROJECT")
        val cameraName = v260FilePart(v260CameraNameState, "CAM_A")
        // V280/12 FIX3: keep the formatter pattern constant.
        return String.format(
            Locale.US,
            "DU_%s_%s_S%03d_T%03d_%s_%s",
            project,
            cameraName,
            v260ActiveSceneNumber,
            v260ActiveTakeNumber,
            activeReportId.take(18),
            stamp
        )
    }

    private fun v260CommitSuccessfulTake() {
        if (!v260ProjectModeOn || !v260AutoTakeCounterOn) return
        if (v260SceneNumberState == v260ActiveSceneNumber && v260TakeNumberState == v260ActiveTakeNumber) {
            v260TakeNumberState = (v260TakeNumberState + 1).coerceAtMost(999)
            v260Prefs().edit().putInt("take_number", v260TakeNumberState).apply()
        }
    }

    internal fun v260SaveCameraPreset(slot: String): String {
        val key = if (slot.trim().uppercase(Locale.US) == "B") "preset_b" else "preset_a"
        val json = JSONObject()
            .put("scene", sceneIndex)
            .put("look", lookIndex)
            .put("quality", qualityIndex)
            .put("capture", captureModeIndex)
            .put("shutter", v244ShutterAngle)
            .put("iso", v244Iso)
            .put("wb", v244WhiteBalanceIndex)
            .put("bitrate", v257BitrateModeState)
            .put("timecode", v256TimecodeModeState)
            .put("tracking", v255SubjectTracking)
            .put("stabilization", v254StabilizationPolicy())
            .put("saved_utc", Instant.now().toString())
        v260Prefs().edit().putString(key, json.toString()).apply()
        val label = if (key == "preset_b") "B" else "A"
        toast("CAMERA PRESET $label SAVED")
        return "PRESET $label SAVED"
    }

    internal fun v260ApplyCameraPreset(slot: String): String {
        if (recording != null) return "STOP RECORDING BEFORE APPLYING CAMERA PRESET"
        val key = if (slot.trim().uppercase(Locale.US) == "B") "preset_b" else "preset_a"
        val raw = v260Prefs().getString(key, null) ?: return "PRESET ${if (key == "preset_b") "B" else "A"} EMPTY"
        return try {
            val json = JSONObject(raw)
            sceneIndex = json.optInt("scene", sceneIndex).coerceIn(0, sceneModes.lastIndex)
            lookIndex = json.optInt("look", lookIndex).coerceIn(0, lookModes.lastIndex)
            qualityIndex = json.optInt("quality", qualityIndex).coerceIn(0, qualityModes.lastIndex)
            captureModeIndex = json.optInt("capture", captureModeIndex).coerceIn(0, captureModes.lastIndex)
            v244ShutterAngle = json.optInt("shutter", v244ShutterAngle)
            v244Iso = json.optInt("iso", v244Iso)
            v244WhiteBalanceIndex = json.optInt("wb", v244WhiteBalanceIndex).coerceIn(0, v244WhiteBalanceLabels.lastIndex)
            v257BitrateModeState = json.optString("bitrate", v257BitrateModeState)
            v256TimecodeModeState = json.optString("timecode", v256TimecodeModeState)
            v255SubjectTracking = json.optString("tracking", v255SubjectTracking)
            v257Prefs().edit().putString("bitrate_mode", v257BitrateModeState).apply()
            v256Prefs().edit().putString("timecode_mode", v256TimecodeModeState).apply()
            v255Prefs().edit().putString("subject_tracking", v255SubjectTracking).apply()
            v254PutText("stabilization_policy", json.optString("stabilization", v254StabilizationPolicy()))
            refreshReportPillStates()
            bindCamera()
            v244ApplyCamera2Controls()
            val label = if (key == "preset_b") "B" else "A"
            toast("CAMERA PRESET $label APPLIED")
            "PRESET $label APPLIED"
        } catch (_: Exception) {
            "PRESET DATA INVALID"
        }
    }

    internal fun v260ProjectStatus(): String = buildString {
        append(v260SlateLabel())
        append(" • AUTO TAKE ")
        append(if (v260AutoTakeCounterOn) "ON" else "OFF")
        append(" • RESERVE ")
        append(if (v260StorageReservationOn) "${v260StorageReserveGbState}GB" else "OFF")
        append(" • THERMAL ")
        append(v260ThermalStrategyState)
    }

    private fun v259Prefs() =
        duSharedPreferences(
            "develop_uganda_v259_smart_shoot_media",
            Context.MODE_PRIVATE
        )

    private fun loadV259SmartShootPreferences() {
        val p = v259Prefs()
        v259SmartExposureAssistOn = p.getBoolean("smart_exposure", true)
        v259SmartRecordCheckOn = p.getBoolean("smart_record_check", true)
        v259ConfidenceMeterOn = p.getBoolean("confidence_meter", true)
        v259ConfidenceProfileState = when (p.getString("confidence_profile", "NORMAL")?.uppercase(Locale.US)) {
            "STRICT" -> "STRICT"
            "RELAXED" -> "RELAXED"
            else -> "NORMAL"
        }
        v259SceneSuggestionsOn = p.getBoolean("scene_suggestions", true)
        v259HorizonAssistOn = p.getBoolean("horizon_assist", true)
        v259StabilityAssistOn = p.getBoolean("stability_assist", true)
        v259TakeFlagsOn = p.getBoolean("take_flags", true)
    }

    internal fun v259SmartExposureEnabled(): Boolean = v259SmartExposureAssistOn

    internal fun v259ToggleSmartExposure(): String {
        v259SmartExposureAssistOn = !v259SmartExposureAssistOn
        v259Prefs().edit().putBoolean("smart_exposure", v259SmartExposureAssistOn).apply()
        if (::directorOverlayView.isInitialized) {
            directorOverlayView.setDirectorEnabled(
                directorEnabled || v251FaceExposurePriority || v259SmartExposureAssistOn || v255SubjectTracking != "OFF"
            )
        }
        val message = if (v259SmartExposureAssistOn) {
            "SMART EXPOSURE ASSIST ON • PRIMARY FACE AE/AWB AVAILABLE"
        } else {
            "SMART EXPOSURE ASSIST OFF"
        }
        toast(message)
        return message
    }

    internal fun v259SmartRecordCheckEnabled(): Boolean = v259SmartRecordCheckOn

    internal fun v259ToggleSmartRecordCheck(): String {
        v259SmartRecordCheckOn = !v259SmartRecordCheckOn
        v259Prefs().edit().putBoolean("smart_record_check", v259SmartRecordCheckOn).apply()
        val message = if (v259SmartRecordCheckOn) "SMART RECORD CHECK ON" else "SMART RECORD CHECK OFF"
        toast(message)
        return message
    }

    internal fun v259ConfidenceMeterEnabled(): Boolean = v259ConfidenceMeterOn

    internal fun v259ToggleConfidenceMeter(): String {
        v259ConfidenceMeterOn = !v259ConfidenceMeterOn
        v259Prefs().edit().putBoolean("confidence_meter", v259ConfidenceMeterOn).apply()
        val message = if (v259ConfidenceMeterOn) "RECORDING CONFIDENCE ON" else "RECORDING CONFIDENCE OFF"
        toast(message)
        return message
    }

    internal fun v259ConfidenceProfile(): String = v259ConfidenceProfileState

    internal fun v259SetConfidenceProfile(value: String): String {
        v259ConfidenceProfileState = when (value.trim().uppercase(Locale.US)) {
            "STRICT" -> "STRICT"
            "RELAXED" -> "RELAXED"
            else -> "NORMAL"
        }
        v259Prefs().edit().putString("confidence_profile", v259ConfidenceProfileState).apply()
        toast("CONFIDENCE PROFILE • $v259ConfidenceProfileState")
        return v259ConfidenceProfileState
    }

    internal fun v259SceneSuggestionsEnabled(): Boolean = v259SceneSuggestionsOn

    internal fun v259ToggleSceneSuggestions(): String {
        v259SceneSuggestionsOn = !v259SceneSuggestionsOn
        v259Prefs().edit().putBoolean("scene_suggestions", v259SceneSuggestionsOn).apply()
        val message = if (v259SceneSuggestionsOn) "SCENE SUGGESTIONS ON" else "SCENE SUGGESTIONS OFF"
        toast(message)
        return message
    }

    internal fun v259HorizonAssistEnabled(): Boolean = v259HorizonAssistOn

    internal fun v259ToggleHorizonAssist(): String {
        v259HorizonAssistOn = !v259HorizonAssistOn
        v259Prefs().edit().putBoolean("horizon_assist", v259HorizonAssistOn).apply()
        updateHorizonGuard()
        val message = if (v259HorizonAssistOn) "HORIZON ASSIST ON" else "HORIZON ASSIST OFF"
        toast(message)
        return message
    }

    internal fun v259StabilityAssistEnabled(): Boolean = v259StabilityAssistOn

    internal fun v259ToggleStabilityAssist(): String {
        v259StabilityAssistOn = !v259StabilityAssistOn
        v259Prefs().edit().putBoolean("stability_assist", v259StabilityAssistOn).apply()
        updateMotionGuard()
        val message = if (v259StabilityAssistOn) "SHOT STABILITY ASSIST ON" else "SHOT STABILITY ASSIST OFF"
        toast(message)
        return message
    }

    internal fun v259TakeFlagsEnabled(): Boolean = v259TakeFlagsOn

    internal fun v259ToggleTakeFlags(): String {
        v259TakeFlagsOn = !v259TakeFlagsOn
        v259Prefs().edit().putBoolean("take_flags", v259TakeFlagsOn).apply()
        val message = if (v259TakeFlagsOn) "MEDIA TAKE FLAGS ON" else "MEDIA TAKE FLAGS OFF"
        toast(message)
        return message
    }

    internal fun v259RecordingConfidenceScore(): Int {
        var score = 100
        val battery = batteryPct()
        val free = freeStorageGb()

        if (camera == null) score -= 18
        when {
            battery == null -> score -= 3
            battery <= 5 -> score -= 28
            battery <= 15 -> score -= 14
            battery <= 30 -> score -= 5
        }
        when {
            free == null -> score -= 3
            free <= 1L -> score -= 36
            free <= 4L -> score -= 20
            free <= 8L -> score -= 9
        }
        score -= when {
            thermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> 42
            thermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 28
            thermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 10
            else -> 0
        }
        val warningPenalty = when (v259ConfidenceProfileState) {
            "STRICT" -> 7
            "RELAXED" -> 3
            else -> 5
        }
        score -= shotQualityWarnings().distinct().size.coerceAtMost(5) * warningPenalty
        return score.coerceIn(0, 100)
    }

    internal fun v259RecordingConfidenceLabel(): String {
        if (!v259ConfidenceMeterOn) return "CONFIDENCE OFF"
        val score = v259RecordingConfidenceScore()
        val state = when {
            score >= 90 -> "READY"
            score >= 75 -> "GOOD"
            score >= 55 -> "CHECK"
            else -> "RISK"
        }
        return "$state $score% • $v259ConfidenceProfileState"
    }

    internal fun v259SmartSceneSummary(): String =
        if (v259SceneSuggestionsOn) autoViewSummary else "SCENE SUGGESTIONS OFF"

    internal fun v259SmartShootStatus(): String = buildString {
        append(v259RecordingConfidenceLabel())
        append(" • EXP ")
        append(if (v259SmartExposureAssistOn) "SMART" else "MANUAL")
        append(" • LEVEL ")
        append(if (v259HorizonAssistOn) "ON" else "OFF")
        append(" • STEADY ")
        append(if (v259StabilityAssistOn) motionGuardLabel() else "OFF")
        if (v259SceneSuggestionsOn) {
            append(" • ")
            append(autoViewSummary.removePrefix("AUTO VIEW • "))
        }
    }

    internal fun v259ResetSmartShoot(): String {
        v259SmartExposureAssistOn = true
        v259SmartRecordCheckOn = true
        v259ConfidenceMeterOn = true
        v259ConfidenceProfileState = "NORMAL"
        v259SceneSuggestionsOn = true
        v259HorizonAssistOn = true
        v259StabilityAssistOn = true
        v259TakeFlagsOn = true
        v259Prefs().edit().clear().apply()
        updateHorizonGuard()
        updateMotionGuard()
        val message = "V259 SMART SHOOT RESET • SAFE DEFAULTS"
        toast(message)
        return message
    }

    private fun v255Prefs() =
        duSharedPreferences(
            "develop_uganda_v255_pro_monitor_tracking",
            Context.MODE_PRIVATE
        )

    private fun loadV255ProMonitorPreferences() {
        val p = v255Prefs()
        v255SubjectTracking = p.getString("subject_tracking", "OFF") ?: "OFF"
        v255TrackingResponseState = p.getString("tracking_response", "NORMAL") ?: "NORMAL"
        v255WaveformOn = p.getBoolean("waveform", false)
        v255FalseColorOn = p.getBoolean("false_color", false)
        v255FalseColorStrengthState = p.getInt("false_color_strength", 40).coerceIn(25, 60)
        v255AudioHeadroomOn = p.getBoolean("audio_headroom", true)
        v255AudioHeadroomTargetState = p.getInt("audio_headroom_target", -12).coerceIn(-15, -6)
        v255PerformanceMonitorOn = p.getBoolean("performance_monitor", true)
        v255ThermalPolicyState = p.getString("thermal_policy", "AUTO SAFE") ?: "AUTO SAFE"
        v255FocusPullOn = p.getBoolean("focus_pull", false)
        v255FocusAState = p.getInt("focus_a", 20).coerceIn(0, 100)
        v255FocusBState = p.getInt("focus_b", 75).coerceIn(0, 100)
        v255FocusPullMsState = p.getInt("focus_pull_ms", 1200).coerceIn(500, 2500)
    }

    private fun v255SyncMonitorViews() {
        if (::v255MonitorView.isInitialized) {
            v255MonitorView.setWaveformEnabled(v255WaveformOn)
            v255MonitorView.setFalseColorEnabled(v255FalseColorOn)
            v255MonitorView.setFalseColorStrength(v255FalseColorStrengthState / 100f)
            v255MonitorView.setRgbParadeEnabled(v261RgbParadeOn)
            v255MonitorView.setVectorscopeEnabled(v261VectorscopeOn)
            v255MonitorView.setSkinToneReferenceEnabled(v261SkinToneReferenceOn)
            v255MonitorView.setHighlightShadowAssistEnabled(v261HighlightShadowAssistOn)
            v255MonitorView.setFrameGuidesEnabled(v261FrameGuidesOn)
            v255MonitorView.setFrameGuideAspect(v261FrameGuideAspectState)
            v255MonitorView.setDirectorHudMode(v261DirectorHudModeState)
            v255MonitorView.visibility =
                if (v261MonitorVisible() && !cleanModeEnabled) View.VISIBLE else View.GONE
        }

        if (::directorOverlayView.isInitialized) {
            directorOverlayView.setTrackingMode(v255SubjectTracking)
            directorOverlayView.setDirectorEnabled(
                directorEnabled || v251FaceExposurePriority || v259SmartExposureAssistOn || v255SubjectTracking != "OFF"
            )
        }
    }

    private fun v255MonitorNeedsFrames(): Boolean =
        v255WaveformOn ||
            v255FalseColorOn ||
            v261RgbParadeOn ||
            v261VectorscopeOn ||
            v261HighlightShadowAssistOn

    private fun v261MonitorVisible(): Boolean =
        v255MonitorNeedsFrames() || v261FrameGuidesOn

    internal fun v255SubjectTrackingMode(): String = v255SubjectTracking

    internal fun v255SetSubjectTrackingMode(value: String): String {
        val mode = when (value.trim().uppercase(Locale.US)) {
            "PRIMARY FACE" -> "PRIMARY FACE"
            "TAP FACE" -> "TAP FACE"
            else -> "OFF"
        }
        if (mode != "OFF" && v255FocusPullOn) {
            val message = "TURN OFF FOCUS PULL BEFORE SUBJECT TRACKING"
            toast(message)
            return message
        }
        v255SubjectTracking = mode
        v255Prefs().edit().putString("subject_tracking", mode).apply()
        if (mode == "OFF") v255LastTrackMeterMs = 0L
        v255SyncMonitorViews()
        val message = if (mode == "TAP FACE") {
            "TAP FACE TRACKING ON • TAP A FACE TO ARM AF/AE/AWB FOLLOW"
        } else {
            "SUBJECT TRACKING • $mode"
        }
        toast(message)
        return message
    }

    internal fun v255TrackingResponse(): String = v255TrackingResponseState

    internal fun v255SetTrackingResponse(value: String): String {
        val response = when (value.trim().uppercase(Locale.US)) {
            "SMOOTH" -> "SMOOTH"
            "FAST" -> "FAST"
            else -> "NORMAL"
        }
        v255TrackingResponseState = response
        v255Prefs().edit().putString("tracking_response", response).apply()
        toast("TRACK RESPONSE • $response")
        return response
    }

    private fun v255TrackingIntervalMs(): Long =
        when (v255TrackingResponseState) {
            "FAST" -> 360L
            "SMOOTH" -> 1250L
            else -> 700L
        }

    private fun v255ArmTapTracking(x: Float, y: Float) {
        if (v255SubjectTracking != "TAP FACE" || !::previewView.isInitialized) return
        if (previewView.width <= 0 || previewView.height <= 0) return
        val nx = (x / previewView.width.toFloat()).coerceIn(0f, 1f)
        val ny = (y / previewView.height.toFloat()).coerceIn(0f, 1f)
        if (::directorOverlayView.isInitialized) {
            directorOverlayView.setTrackingAnchor(nx, ny)
            directorOverlayView.setTrackingMode("TAP FACE")
        }
        toast("TRACK TARGET ARMED • LOOKING FOR NEAREST FACE")
    }

    private fun v255ApplyTrackedFaceMetering(
        normalizedX: Float,
        normalizedY: Float,
        areaRatio: Float
    ) {
        if (
            v255SubjectTracking == "OFF" ||
            v255FocusPullOn ||
            focusLockActive ||
            operatorLocked ||
            areaRatio < 0.010f ||
            !::previewView.isInitialized
        ) return

        val now = SystemClock.elapsedRealtime()
        if (now - v255LastTrackMeterMs < v255TrackingIntervalMs()) return
        val cam = camera ?: return
        if (previewView.width <= 0 || previewView.height <= 0) return

        val x = normalizedX.coerceIn(0.05f, 0.95f) * previewView.width.toFloat()
        val y = normalizedY.coerceIn(0.05f, 0.95f) * previewView.height.toFloat()

        try {
            val point = previewView.meteringPointFactory.createPoint(x, y, 0.20f)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB
            )
                .setAutoCancelDuration(2, TimeUnit.SECONDS)
                .build()
            cam.cameraControl.startFocusAndMetering(action)
            v255LastTrackMeterMs = now
        } catch (_: Exception) {
        }
    }

    internal fun v255WaveformEnabled(): Boolean = v255WaveformOn

    internal fun v255ToggleWaveform(): String {
        v255WaveformOn = !v255WaveformOn
        v255Prefs().edit().putBoolean("waveform", v255WaveformOn).apply()
        v255SyncMonitorViews()
        val message = if (v255WaveformOn) "LUMA WAVEFORM ON • SCREEN ONLY" else "LUMA WAVEFORM OFF"
        toast(message)
        return message
    }

    internal fun v255FalseColorEnabled(): Boolean = v255FalseColorOn

    internal fun v255ToggleFalseColor(): String {
        v255FalseColorOn = !v255FalseColorOn
        v255Prefs().edit().putBoolean("false_color", v255FalseColorOn).apply()
        v255SyncMonitorViews()
        val message = if (v255FalseColorOn) "FALSE COLOR ON • SCREEN ONLY • APP PALETTE" else "FALSE COLOR OFF"
        toast(message)
        return message
    }

    internal fun v255FalseColorStrength(): Int = v255FalseColorStrengthState

    internal fun v255SetFalseColorStrength(value: String): String {
        val strength = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(25, 60) ?: 40
        v255FalseColorStrengthState = strength
        v255Prefs().edit().putInt("false_color_strength", strength).apply()
        v255SyncMonitorViews()
        toast("FALSE COLOR STRENGTH • $strength%")
        return strength.toString()
    }

    internal fun v255AudioHeadroomEnabled(): Boolean = v255AudioHeadroomOn

    internal fun v255ToggleAudioHeadroom(): String {
        v255AudioHeadroomOn = !v255AudioHeadroomOn
        v255Prefs().edit().putBoolean("audio_headroom", v255AudioHeadroomOn).apply()
        val message = if (v255AudioHeadroomOn) {
            "AUDIO HEADROOM GUARD ON • TARGET ${v255AudioHeadroomTargetState}dB"
        } else {
            "AUDIO HEADROOM GUARD OFF"
        }
        toast(message)
        return message
    }

    internal fun v255AudioHeadroomTargetDb(): Int = v255AudioHeadroomTargetState

    internal fun v255SetAudioHeadroomTarget(value: String): String {
        val target = value.replace("DB", "", true).trim().toIntOrNull()?.coerceIn(-15, -6) ?: -12
        v255AudioHeadroomTargetState = target
        v255Prefs().edit().putInt("audio_headroom_target", target).apply()
        toast("AUDIO HEADROOM TARGET • ${target}dBFS APPROX")
        return target.toString()
    }

    private fun v255AudioDbfs(): Double {
        val level = audioAmplitude.coerceIn(0.000001, 1.0)
        return 20.0 * log10(level)
    }

    private fun v255AudioPeakDbfs(): Double {
        val level = audioPeakAmplitude.coerceIn(0.000001, 1.0)
        return 20.0 * log10(level)
    }

    internal fun v255AudioDbfsText(): String =
        if (recording == null) {
            "dBFS --"
        } else {
            String.format(Locale.US, "%.1fdBFS", v255AudioDbfs())
        }

    internal fun v255PerformanceMonitorEnabled(): Boolean = v255PerformanceMonitorOn

    internal fun v255TogglePerformanceMonitor(): String {
        v255PerformanceMonitorOn = !v255PerformanceMonitorOn
        v255Prefs().edit().putBoolean("performance_monitor", v255PerformanceMonitorOn).apply()
        val message = if (v255PerformanceMonitorOn) "PERFORMANCE MONITOR ON" else "PERFORMANCE MONITOR OFF"
        toast(message)
        return message
    }

    internal fun v255PerformanceStatus(): String {
        if (!v255PerformanceMonitorOn) return "PERFORMANCE MONITOR • OFF"
        val fps = requestedVideoFps().takeIf { it > 0 } ?: 30
        val durationSec = v255RecordedDurationNs / 1_000_000_000.0
        val mbps = if (durationSec > 0.25) {
            (v255RecordedBytes.toDouble() * 8.0 / durationSec / 1_000_000.0)
        } else {
            0.0
        }
        val rec = if (recording != null) "REC" else "READY"
        val rate = if (mbps > 0.1) String.format(Locale.US, "%.1fMbps", mbps) else "--Mbps"
        return "$rec • TARGET ${fps}FPS • ENC $rate • THERM ${thermalStateLabel()} • POLICY ${v255ThermalPolicyState}"
    }

    internal fun v255ThermalPolicy(): String = v255ThermalPolicyState

    internal fun v255SetThermalPolicy(value: String): String {
        val policy = when (value.trim().uppercase(Locale.US)) {
            "OFF" -> "OFF"
            "WARN" -> "WARN"
            else -> "AUTO SAFE"
        }
        v255ThermalPolicyState = policy
        v255Prefs().edit().putString("thermal_policy", policy).apply()
        val message = when (policy) {
            "OFF" -> "THERMAL AUTO-DOWNGRADE OFF • ANDROID SAFETY STILL APPLIES"
            "WARN" -> "THERMAL WARN ONLY • NO APP AUTO-DOWNGRADE"
            else -> "THERMAL AUTO SAFE • HIGH-DEMAND MODE FALLBACK ENABLED"
        }
        toast(message)
        return policy
    }

    internal fun v255FocusPullEnabled(): Boolean = v255FocusPullOn

    internal fun v255ToggleFocusPull(): String {
        if (v255FocusPullOn && recording != null) {
            val message = "STOP RECORDING BEFORE DISABLING MANUAL FOCUS PULL"
            toast(message)
            return message
        }
        if (!v255FocusPullOn && v255SubjectTracking != "OFF") {
            val message = "TURN OFF SUBJECT TRACKING BEFORE MANUAL FOCUS PULL"
            toast(message)
            return message
        }
        v255FocusPullOn = !v255FocusPullOn
        v255Prefs().edit().putBoolean("focus_pull", v255FocusPullOn).apply()
        if (!v255FocusPullOn && recording == null) {
            v255FocusPullGeneration += 1
            v255FocusPullRunning = false
            v255RestoreAutoFocus()
        }
        val message = if (v255FocusPullOn) "FOCUS PULL A/B ARMED" else "FOCUS PULL OFF • CONTINUOUS AF RESTORED"
        toast(message)
        return message
    }

    internal fun v255FocusA(): Int = v255FocusAState
    internal fun v255FocusB(): Int = v255FocusBState
    internal fun v255FocusPullMs(): Int = v255FocusPullMsState

    internal fun v255SetFocusA(value: String): String {
        val p = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: 20
        v255FocusAState = p
        v255Prefs().edit().putInt("focus_a", p).apply()
        return p.toString()
    }

    internal fun v255SetFocusB(value: String): String {
        val p = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(0, 100) ?: 75
        v255FocusBState = p
        v255Prefs().edit().putInt("focus_b", p).apply()
        return p.toString()
    }

    internal fun v255SetFocusPullMs(value: String): String {
        val ms = value.filter { it.isDigit() }.toIntOrNull()?.coerceIn(500, 2500) ?: 1200
        v255FocusPullMsState = ms
        v255Prefs().edit().putInt("focus_pull_ms", ms).apply()
        return ms.toString()
    }

    private fun v255RestoreAutoFocus() {
        val cam = camera ?: return
        try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val afModes = info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
            ) ?: intArrayOf()
            val mode = when {
                afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO) ->
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO
                afModes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) ->
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                else -> return
            }
            val builder = CaptureRequestOptions.Builder()
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, mode)
            val options = builder.build()
            Camera2CameraControl.from(cam.cameraControl).addCaptureRequestOptions(options)
            cam.cameraControl.cancelFocusAndMetering()
        } catch (_: Exception) {
        }
    }

    internal fun v255FocusPullStatus(): String {
        val cam = camera ?: return "FOCUS A/B • CAMERA NOT READY"
        return try {
            val min = Camera2CameraInfo.from(cam.cameraInfo)
                .getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)
                ?: 0f
            if (min <= 0f) {
                "FOCUS A/B • FIXED-FOCUS / UNSUPPORTED LENS"
            } else {
                val run = if (v255FocusPullRunning) "PULLING" else if (v255FocusPullOn) "ARMED" else "OFF"
                "$run • A ${v255FocusAState}% • B ${v255FocusBState}% • ${v255FocusPullMsState}ms"
            }
        } catch (_: Exception) {
            "FOCUS A/B • CAPABILITY UNKNOWN"
        }
    }

    private fun v255ApplyFocusPercent(percent: Int): Boolean {
        val cam = camera ?: return false
        return try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val minFocus = info.getCameraCharacteristic(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE
            ) ?: return false
            val afModes = info.getCameraCharacteristic(
                CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES
            ) ?: intArrayOf()
            if (minFocus <= 0f || !afModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF)) return false

            val p = percent.coerceIn(0, 100) / 100f
            val distance = minFocus * p
            val builder = CaptureRequestOptions.Builder()
            builder.setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            builder.setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, distance)
            val options = builder.build()
            Camera2CameraControl.from(cam.cameraControl).addCaptureRequestOptions(options)
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun v255RunFocusPull(direction: String): String {
        if (!v255FocusPullOn) {
            val message = "ENABLE FOCUS PULL FIRST"
            toast(message)
            return message
        }
        val cam = camera
        if (cam == null) {
            val message = "CAMERA NOT READY"
            toast(message)
            return message
        }
        val supported = try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            val minFocus = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
            val afModes = info.getCameraCharacteristic(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            minFocus > 0f && afModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF)
        } catch (_: Exception) {
            false
        }
        if (!supported) {
            val message = "MANUAL FOCUS DISTANCE NOT SUPPORTED ON THIS LENS"
            toast(message)
            return message
        }

        val forward = direction.trim().uppercase(Locale.US) != "B>A"
        val start = if (forward) v255FocusAState else v255FocusBState
        val end = if (forward) v255FocusBState else v255FocusAState
        val duration = v255FocusPullMsState.coerceIn(500, 2500)
        val steps = (duration / 50).coerceIn(10, 50)
        val token = ++v255FocusPullGeneration
        v255FocusPullRunning = true

        for (i in 0..steps) {
            val delay = duration.toLong() * i.toLong() / steps.toLong()
            uiHandler.postDelayed({
                if (token != v255FocusPullGeneration || !v255FocusPullOn) return@postDelayed
                val t = i.toFloat() / steps.toFloat()
                val eased = t * t * (3f - 2f * t)
                val p = (start + (end - start) * eased).roundToInt()
                val ok = v255ApplyFocusPercent(p)
                if (!ok) {
                    v255FocusPullGeneration += 1
                    v255FocusPullRunning = false
                    toast("FOCUS PULL INTERRUPTED • LENS CONTROL UNAVAILABLE")
                    return@postDelayed
                }
                if (i == steps) {
                    v255FocusPullRunning = false
                    toast("FOCUS PULL COMPLETE • ${if (forward) "A→B" else "B→A"}")
                }
            }, delay)
        }

        val message = "FOCUS PULL ${if (forward) "A→B" else "B→A"} • ${duration}ms"
        toast(message)
        return message
    }

    internal fun v255LiveStatus(): String =
        buildString {
            append("TRACK ").append(v255SubjectTracking)
            append(" • RESP ").append(v255TrackingResponseState)
            append(" • WAVE ").append(if (v255WaveformOn) "ON" else "OFF")
            append(" • FALSE ").append(if (v255FalseColorOn) "${v255FalseColorStrengthState}%" else "OFF")
            append(" • AUDIO ").append(if (v255AudioHeadroomOn) "${v255AudioHeadroomTargetState}dB" else "GUARD OFF")
            append("\n").append(v255PerformanceStatus())
            append("\n").append(v255FocusPullStatus())
        }

    internal fun v255ResetProMonitor(): String {
        if (recording != null && v255FocusPullOn) {
            val message = "STOP RECORDING BEFORE RESETTING V255 FOCUS CONTROL"
            toast(message)
            return message
        }
        v255FocusPullGeneration += 1
        v255Prefs().edit().clear().apply()
        loadV255ProMonitorPreferences()
        v255SyncMonitorViews()
        if (recording == null) {
            v255RestoreAutoFocus()
        }
        val message = "V255 PRO MONITOR + TRACKING RESET • SAFE DEFAULTS"
        toast(message)
        return message
    }

    private fun v267ConsumePendingQuickPreset() {
        val prefs = duSharedPreferences(
            "develop_uganda_v267_live_spec_command_center",
            Context.MODE_PRIVATE
        )
        val preset = prefs.getString("pending_quick_preset", null)
            ?.trim()
            ?.uppercase(Locale.US)
            ?.takeIf { it in setOf("NEWS", "INTERVIEW", "CINEMA", "NIGHT", "SOCIAL") }
            ?: return
        prefs.edit().remove("pending_quick_preset").putString("last_quick_preset", preset).apply()
        uiHandler.postDelayed({
            if (!isFinishing && !isDestroyed) v254ApplyShootPreset(preset)
        }, 420L)
    }

    private fun v254Prefs() =
        duSharedPreferences(
            "develop_uganda_v254_field_controls",
            Context.MODE_PRIVATE
        )

    private fun v254Bool(key: String, defaultValue: Boolean): Boolean =
        v254Prefs().getBoolean(key, defaultValue)

    private fun v254Text(key: String, defaultValue: String): String =
        v254Prefs().getString(key, defaultValue) ?: defaultValue

    private fun v254PutBool(key: String, value: Boolean) {
        v254Prefs().edit().putBoolean(key, value).apply()
    }

    private fun v254PutText(key: String, value: String) {
        v254Prefs().edit().putString(key, value).apply()
    }

    internal fun v254AudioMeterEnabled(): Boolean =
        v254Bool("audio_meter", true)

    internal fun v254ToggleAudioMeter(): String {
        val enabled = !v254AudioMeterEnabled()
        v254PutBool("audio_meter", enabled)
        updateAudioGuard()
        val message = if (enabled) "LIVE AUDIO METER ON" else "LIVE AUDIO METER OFF"
        toast(message)
        return message
    }

    internal fun v254AudioClipWarningEnabled(): Boolean =
        v254Bool("audio_clip_warning", true)

    internal fun v254ToggleAudioClipWarning(): String {
        val enabled = !v254AudioClipWarningEnabled()
        v254PutBool("audio_clip_warning", enabled)
        val message = if (enabled) "AUDIO CLIP WARNING ON" else "AUDIO CLIP WARNING OFF"
        toast(message)
        return message
    }

    internal fun v254PreflightEnabled(): Boolean =
        v254Bool("record_preflight", true)

    internal fun v254TogglePreflight(): String {
        val enabled = !v254PreflightEnabled()
        v254PutBool("record_preflight", enabled)
        val message = if (enabled) "RECORD PREFLIGHT ON" else "RECORD PREFLIGHT OFF"
        toast(message)
        return message
    }

    internal fun v254StorageGuardEnabled(): Boolean =
        v254Bool("storage_guard", true)

    internal fun v254ToggleStorageGuard(): String {
        val enabled = !v254StorageGuardEnabled()
        v254PutBool("storage_guard", enabled)
        val message = if (enabled) "STORAGE GUARD ON" else "STORAGE EARLY WARNING OFF • 1GB EMERGENCY BLOCK KEPT"
        toast(message)
        return message
    }

    internal fun v254StorageThresholdGb(): Int =
        v254Text("storage_threshold_gb", "4")
            .toIntOrNull()
            ?.coerceIn(2, 16)
            ?: 4

    internal fun v254SetStorageThreshold(value: String): String {
        val gb = value.toIntOrNull()?.coerceIn(2, 16) ?: 4
        v254PutText("storage_threshold_gb", gb.toString())
        val message = "STORAGE WARNING • ${gb}GB"
        toast(message)
        return message
    }

    internal fun v254RecoveryJournalEnabled(): Boolean =
        v254Bool("recovery_journal", true)

    internal fun v254ToggleRecoveryJournal(): String {
        val enabled = !v254RecoveryJournalEnabled()
        v254PutBool("recovery_journal", enabled)
        val message = if (enabled) "CLIP RECOVERY JOURNAL ON" else "CLIP RECOVERY JOURNAL OFF"
        toast(message)
        return message
    }

    internal fun v254QuickReviewEnabled(): Boolean =
        v254Bool("quick_review", true)

    internal fun v254ToggleQuickReview(): String {
        val enabled = !v254QuickReviewEnabled()
        v254PutBool("quick_review", enabled)
        val message = if (enabled) "AUTO QUICK REVIEW ON" else "AUTO QUICK REVIEW OFF"
        toast(message)
        return message
    }

    internal fun v254MetadataSidecarEnabled(): Boolean =
        v254Bool("metadata_sidecar", true)

    internal fun v254ToggleMetadataSidecar(): String {
        val enabled = !v254MetadataSidecarEnabled()
        v254PutBool("metadata_sidecar", enabled)
        val message = if (enabled) "TELEMETRY SIDECAR ON" else "TELEMETRY SIDECAR OFF"
        toast(message)
        return message
    }

    internal fun v254StabilizationPolicy(): String =
        v254Text("stabilization_policy", "DEVICE")
            .uppercase(Locale.US)
            .let { if (it in setOf("DEVICE", "ON", "OFF")) it else "DEVICE" }

    internal fun v254SetStabilizationPolicy(value: String): String {
        if (recording != null) return "STOP RECORDING BEFORE CHANGING STABILIZATION"
        val policy = value.uppercase(Locale.US).let {
            if (it in setOf("DEVICE", "ON", "OFF")) it else "DEVICE"
        }
        v254PutText("stabilization_policy", policy)
        bindCamera()
        val message = "STABILIZATION • $policy"
        toast(message)
        return message
    }

    internal fun v254WhiteBalanceLabel(): String =
        v244WhiteBalanceLabels[
            v244WhiteBalanceIndex.coerceIn(0, v244WhiteBalanceLabels.lastIndex)
        ]

    internal fun v254SetWhiteBalance(label: String): String {
        v271RememberSettingChange("WHITE BALANCE ${v254WhiteBalanceLabel()}")
        if (recording != null) return "STOP RECORDING BEFORE CHANGING WHITE BALANCE"
        val normalized = label.uppercase(Locale.US)
        val index = v244WhiteBalanceLabels.indexOf(normalized)
        v244WhiteBalanceIndex = if (index >= 0) index else 0
        return v244ApplyCamera2Controls()
    }

    internal fun v254ShutterLabel(): String =
        if (v244ShutterAngle > 0) "${v244ShutterAngle}°" else "AUTO"

    internal fun v254SetShutter(label: String): String {
        v271RememberSettingChange("SHUTTER ${v254ShutterLabel()}")
        if (recording != null) return "STOP RECORDING BEFORE CHANGING SHUTTER"
        val requested = label.filter { it.isDigit() }.toIntOrNull() ?: 0
        v244ShutterAngle = when (requested) {
            45, 90, 144, 180, 270, 360 -> requested
            else -> 0
        }
        return v244ApplyCamera2Controls()
    }

    internal fun v254IsoLabel(): String =
        if (v244Iso > 0) "ISO $v244Iso" else "AUTO"

    internal fun v254SetIso(label: String): String {
        v271RememberSettingChange("ISO ${v254IsoLabel()}")
        if (recording != null) return "STOP RECORDING BEFORE CHANGING ISO"
        if (label.equals("AUTO", true)) {
            v244Iso = 0
            return v244ApplyCamera2Controls()
        }
        if (!v244ManualSensorSupported()) {
            v244Iso = 0
            return "MANUAL ISO NOT SUPPORTED • AUTO KEPT"
        }
        val requested = label.filter { it.isDigit() }.toIntOrNull() ?: 0
        val supported = v244SupportedIsoValues()
        if (supported.isEmpty()) {
            v244Iso = 0
            return "ISO RANGE NOT EXPOSED • AUTO KEPT"
        }
        v244Iso = supported.minByOrNull { kotlin.math.abs(it - requested) } ?: 0
        return v244ApplyCamera2Controls()
    }

    internal fun v254ClipNamingMode(): String =
        v254Text("clip_naming", "REPORT")
            .uppercase(Locale.US)
            .let { if (it in setOf("REPORT", "STORY", "SIMPLE")) it else "REPORT" }

    internal fun v254SetClipNamingMode(value: String): String {
        val mode = value.uppercase(Locale.US).let {
            if (it in setOf("REPORT", "STORY", "SIMPLE")) it else "REPORT"
        }
        v254PutText("clip_naming", mode)
        val message = "CLIP NAMING • $mode"
        toast(message)
        return message
    }

    private fun v254FilePart(value: String, fallback: String): String =
        value
            .trim()
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_-]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { fallback }

    private fun v254ClipBaseName(activeReportId: String): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return when (v254ClipNamingMode()) {
            "STORY" -> {
                val storyPart = v254FilePart(storyId, "FIELD")
                "DU_${storyPart}_${activeReportId}_$stamp"
            }
            "SIMPLE" -> "DU_${activeReportId}_$stamp"
            else ->
                "DEVELOP_UGANDA_V254_${cameraExperienceId}_${activeReportId}_${sceneModes[sceneIndex]}_${lookModes[lookIndex]}_$stamp"
        }
    }

    internal fun v254ShootPreset(): String =
        v254Text("shoot_preset", "CUSTOM").uppercase(Locale.US)

    internal fun v254ApplyShootPreset(label: String): String {
        if (recording != null) return "STOP RECORDING BEFORE CHANGING SHOOT PRESET"
        val preset = label.uppercase(Locale.US)
        fun setIndex(list: List<String>, value: String, current: Int): Int {
            val i = list.indexOf(value)
            return if (i >= 0) i else current
        }
        when (preset) {
            "NEWS" -> {
                sceneIndex = setIndex(sceneModes, "NEWS", sceneIndex)
                lookIndex = setIndex(lookModes, "CLEAN", lookIndex)
                qualityIndex = setIndex(qualityModes, "MASTER UHD", qualityIndex)
                v254PutText("stabilization_policy", "ON")
            }
            "INTERVIEW" -> {
                sceneIndex = setIndex(sceneModes, "INTERVIEW", sceneIndex)
                lookIndex = setIndex(lookModes, "NATURAL", lookIndex)
                qualityIndex = setIndex(qualityModes, "MASTER UHD", qualityIndex)
                v254PutText("stabilization_policy", "OFF")
            }
            "CINEMA" -> {
                sceneIndex = setIndex(sceneModes, "CINEMA", sceneIndex)
                lookIndex = setIndex(lookModes, "CLEAN", lookIndex)
                qualityIndex = setIndex(qualityModes, "MASTER UHD", qualityIndex)
                v254PutText("stabilization_policy", "DEVICE")
            }
            "NIGHT" -> {
                sceneIndex = setIndex(sceneModes, "NIGHT", sceneIndex)
                lookIndex = setIndex(lookModes, "NIGHT", lookIndex)
                qualityIndex = setIndex(qualityModes, "LOW LIGHT", qualityIndex)
                v254PutText("stabilization_policy", "DEVICE")
            }
            "SOCIAL" -> {
                sceneIndex = setIndex(sceneModes, "REPORTER", sceneIndex)
                lookIndex = setIndex(lookModes, "NATURAL", lookIndex)
                qualityIndex = setIndex(qualityModes, "SOCIAL FHD", qualityIndex)
                v254PutText("stabilization_policy", "ON")
            }
            else -> {
                v254PutText("shoot_preset", "CUSTOM")
                return "SHOOT PRESET • CUSTOM"
            }
        }
        v244ShutterAngle = 0
        v244Iso = 0
        v244WhiteBalanceIndex = 0
        v254PutText("shoot_preset", preset)
        applyScenePreset()
        refreshHud()
        bindCamera()
        val message = "SHOOT PRESET • $preset"
        toast(message)
        return message
    }

    internal fun v254AudioStatus(): String {
        val level = (audioAmplitude.coerceIn(0.0, 1.0) * 100.0).roundToInt()
        val peak = (audioPeakAmplitude.coerceIn(0.0, 1.0) * 100.0).roundToInt()
        return "AUDIO • ${audioGuardLabel()} • LEVEL ${level}% • PEAK ${peak}%"
    }

    internal fun v254FieldCapacity(): String {
        val bat = batteryPct()?.let { "$it%" } ?: "--"
        val free = freeStorageGb()?.let { "${it}GB" } ?: "--"
        return "BAT $bat • FREE $free • THERM ${thermalStateLabel()} • ${v254AudioStatus()} • ${estimatedRecordingTimeText()}"
    }

    internal fun v254ResetFieldControls(): String {
        if (recording != null) return "STOP RECORDING BEFORE RESETTING V254 SETTINGS"
        v254Prefs().edit().clear().apply()
        v244ShutterAngle = 0
        v244Iso = 0
        v244WhiteBalanceIndex = 0
        updateAudioGuard()
        applyScenePreset()
        refreshHud()
        bindCamera()
        val message = "V254 FIELD SETTINGS RESET • SAFE DEFAULTS"
        toast(message)
        return message
    }

    private fun cycleScene() {
        v271RememberSettingChange("SCENE ${sceneModes[sceneIndex]}")
        if (recording != null) {
            toast(
                "Stop recording before changing scene"
            )
            return
        }

        sceneIndex =
            (sceneIndex + 1) %
                sceneModes.size

        applyScenePreset()
        refreshHud()
    }

    private fun cycleLook() {
        v271RememberSettingChange("LOOK ${lookModes[lookIndex]}")
        lookIndex =
            (lookIndex + 1) %
                lookModes.size

        refreshHud()
    }

    private fun cycleQuality() {
        if (recording != null) {
            toast(
                "Stop recording before changing format"
            )
            return
        }

        val v245MasterModes = listOf("MASTER UHD", "UHD 60", "MASTER HDR")
        val v245CurrentName = qualityModes.getOrNull(qualityIndex) ?: "MASTER UHD"
        val v245Current = v245MasterModes.indexOf(v245CurrentName).takeIf { it >= 0 } ?: 0
        val v245Next = v245MasterModes[(v245Current + 1) % v245MasterModes.size]
        qualityIndex = qualityModes.indexOf(v245Next).takeIf { it >= 0 } ?: qualityIndex

        refreshHud()
        bindCamera()
    }

    private fun cycleCaptureMode() {
        if (recording != null) {
            toast(
                "Stop recording before changing capture mode"
            )
            return
        }

        captureModeIndex =
            (captureModeIndex + 1) %
                captureModes.size

        refreshHud()
        bindCamera()
    }

    private fun qualityDeckLabel(): String {
        return when (
            qualityModes[
                qualityIndex
            ]
        ) {
            "SOCIAL FHD" -> "1080 SOCIAL"
            "SOCIAL 60" -> "1080 60"
            "MASTER UHD" -> "4K MASTER"
            "UHD 60" -> "4K 60"
            "MASTER HDR" -> "4K HDR"
            "SOCIAL HDR" -> "1080 HDR"
            "ACTION STAB" -> "ACTION"
            "ACTION 60" -> "ACTION 60"
            "LOW LIGHT" -> "NIGHT VIDEO"
            "FAST HD" -> "HD FAST"
            else -> "AUTO"
        }
    }

    private fun targetVideoBitrate(): Int {
        additiveModeProfile()?.let { profile ->
            return requireNotNull(profile.targetBitrateBps) {
                "${profile.page} has no capture bitrate profile"
            }
        }

        val base = when (
            qualityModes[
                qualityIndex
            ]
        ) {
            "SOCIAL FHD" -> 24_000_000
            "SOCIAL 60" -> 42_000_000
            "MASTER UHD" -> 64_000_000
            "UHD 60" -> 90_000_000
            "MASTER HDR" -> 72_000_000
            "SOCIAL HDR" -> 34_000_000
            "ACTION STAB" -> 30_000_000
            "ACTION 60" -> 48_000_000
            "LOW LIGHT" -> 28_000_000
            "FAST HD" -> 12_000_000
            else -> 24_000_000
        }
        return v257ApplyBitrate(base)
    }

    private fun requestedVideoFps(): Int {
        additiveModeProfile()?.let { profile ->
            return if (
                profile.page == DevelopUgandaCameraPage.INTERVIEW &&
                ambientLux?.let { it < 100f } == true
            ) 30 else requireNotNull(profile.frameRate) {
                "${profile.page} has no capture frame-rate profile"
            }
        }

        return when (
            qualityModes[
                qualityIndex
            ]
        ) {
            "SOCIAL 60",
            "UHD 60",
            "ACTION 60" -> 60
            "LOW LIGHT" -> 0
            else -> 30
        }
    }

    private fun wantsVideoHdr(): Boolean {
        return qualityModes[
            qualityIndex
        ] in
            setOf(
                "MASTER HDR",
                "SOCIAL HDR"
            )
    }

    private fun wantsVideoStabilization(): Boolean {
        // New portrait pages request the device's stabilization path when it
        // is exposed. Existing experiences keep their exact V254 policy.
        if (isAdditiveCameraPage()) {
            return true
        }

        return when (v254StabilizationPolicy()) {
            "ON" -> true
            "OFF" -> false
            else -> when (
                qualityModes[
                    qualityIndex
                ]
            ) {
                "SOCIAL FHD",
                "ACTION STAB",
                "ACTION 60",
                "LOW LIGHT" -> true
                else -> false
            }
        }
    }

    private fun reportCameraPrefsName(): String {
        return "develop_uganda_report_camera_" +
            cameraExperienceId.lowercase(
                Locale.US
            )
    }

    private fun cameraExperienceDisplayName(): String {
        additiveModeProfile()?.let { profile ->
            return profile.displayName
        }

        return when (cameraExperienceId) {
            "V205_FOCUS" ->
                "V205 • PEOPLE FOCUS"
            "V206_METER" ->
                "V206 • SUBJECT METERING"
            "V207_HORIZON" ->
                "V207 • BUILDINGS & LEVEL"
            "V208_STEADY" ->
                "V208 • WALK & ACTION STEADY"
            "V209_NIGHT" ->
                "V209 • NIGHT & LOW LIGHT"
            "V210_ALL_PRO" ->
                "V210 • EVERYDAY PRO"
            "V211_AUDIO" ->
                "V211 • INTERVIEW AUDIO"
            "V212_VERIFIED" ->
                "V212 • VERIFIED REPORT"
            "V213_THERMAL" ->
                "V213 • LONG RECORD SAFE"
            "V214_SIGNATURE" ->
                "V214 • CINEMATIC LOOKS"
            "V215_AUTO" ->
                "V215 • SMART AUTO"
            "V222_SOCIAL" ->
                "V222 • SOCIAL MEDIA CAM"
            else ->
                "V210 • EVERYDAY PRO"
        }
    }

    private fun cameraExperienceShortLabel(): String {
        return cameraExperienceDisplayName()
            .removeSuffix(
                " CAMERA"
            )
    }

    private fun cameraExperienceInstruction(): String {
        additiveModeProfile()?.let { profile ->
            return profile.instruction
        }

        return when (cameraExperienceId) {
            "V205_FOCUS" ->
                "PEOPLE • PORTRAITS • INTERVIEWS • TAP AF • HOLD AF LOCK"
            "V206_METER" ->
                "BACKLIGHT • WINDOWS • FACES • HOLD AF+AE+AWB METERING"
            "V207_HORIZON" ->
                "BUILDINGS • ROOMS • HORIZONS • KEEP LEVEL GUIDE GREEN"
            "V208_STEADY" ->
                "WALKING • VEHICLES • ACTION • STABILIZATION WHEN SUPPORTED"
            "V209_NIGHT" ->
                "NIGHT • DARK INDOOR • LUX-DRIVEN LOW-LIGHT PROFILE"
            "V210_ALL_PRO" ->
                "EVERYDAY • NEWS • TRAVEL • ALL PRO TOOLS TOGETHER"
            "V211_AUDIO" ->
                "INTERVIEW • SPEECH • EVENTS • WATCH MIC GOOD/HOT/CLIP RISK"
            "V212_VERIFIED" ->
                "EVIDENCE • SITE REPORTS • INCIDENTS • TELEMETRY + INTEGRITY"
            "V213_THERMAL" ->
                "LONG RECORDINGS • HOT CONDITIONS • THERMAL SAFE FALLBACK"
            "V214_SIGNATURE" ->
                "CINEMATIC • PEOPLE • TRAVEL • HDR/WARM WITH DEVICE FALLBACK"
            "V215_AUTO" ->
                "QUICK SHOOTING • AUTO CHOOSES LOW-LIGHT/ACTION/60/BALANCED"
            "V222_SOCIAL" ->
                "RECORD → AUTO OPTIMIZE → SM POSTS • 9:16 • SOCIAL FHD • ORIGINAL + SOCIAL COPY"
            else ->
                "ALL PRO CAMERA TOOLS"
        }
    }


    private fun cameraExperienceBestFor(): String {
        additiveModeProfile()?.let { profile ->
            return profile.bestFor
        }

        return when (cameraExperienceId) {
            "V205_FOCUS" ->
                "PEOPLE / PORTRAITS / INTERVIEWS"
            "V206_METER" ->
                "BACKLIT FACES / WINDOWS / MIXED LIGHT"
            "V207_HORIZON" ->
                "BUILDINGS / ROOMS / LANDSCAPES"
            "V208_STEADY" ->
                "WALKING / VEHICLES / ACTION"
            "V209_NIGHT" ->
                "NIGHT / DARK INDOOR / STREETS"
            "V210_ALL_PRO" ->
                "EVERYDAY / NEWS / TRAVEL"
            "V211_AUDIO" ->
                "INTERVIEWS / SPEECH / EVENTS"
            "V212_VERIFIED" ->
                "SITE REPORTS / INCIDENTS / EVIDENCE"
            "V213_THERMAL" ->
                "LONG RECORDINGS / HOT CONDITIONS"
            "V214_SIGNATURE" ->
                "CINEMATIC / PEOPLE / TRAVEL"
            "V215_AUTO" ->
                "QUICK SHOOTING / WHEN UNSURE"
            "V222_SOCIAL" ->
                "TIKTOK / REELS / SHORTS / SOCIAL POSTS"
            else ->
                "EVERYDAY PROFESSIONAL CAPTURE"
        }
    }

    private fun cameraExperienceAccentColor(): Int {
        additiveModeProfile()?.let { profile ->
            return profile.chromeAccentColor
        }

        return when (cameraExperienceId) {
            "V205_FOCUS" -> DevelopUgandaFivemods8Theme.accent
            "V206_METER" -> DevelopUgandaFivemods8Theme.accent
            "V207_HORIZON" -> DevelopUgandaFivemods8Theme.contentDim
            "V208_STEADY" -> DevelopUgandaFivemods8Theme.accent
            "V209_NIGHT" -> DevelopUgandaFivemods8Theme.accent
            "V210_ALL_PRO" -> DevelopUgandaFivemods8Theme.accent
            "V211_AUDIO" -> DevelopUgandaFivemods8Theme.accent
            "V212_VERIFIED" -> DevelopUgandaFivemods8Theme.accent
            "V213_THERMAL" -> DevelopUgandaFivemods8Theme.accent
            "V214_SIGNATURE" -> DevelopUgandaFivemods8Theme.accent
            "V215_AUTO" -> DevelopUgandaFivemods8Theme.accent
            "V222_SOCIAL" -> DevelopUgandaFivemods8Theme.accent
            else -> DevelopUgandaFivemods8Theme.accent
        }
    }

    private fun applyIndependentCameraDefaultsIfNeeded() {
        val prefs =
            duSharedPreferences(
                reportCameraPrefsName(),
                Context.MODE_PRIVATE
            )

        if (
            prefs.getBoolean(
                "experience_initialized",
                false
            )
        ) {
            return
        }

        fun quality(value: String) {
            val index = qualityModes.indexOf(value)
            if (index >= 0) qualityIndex = index
        }

        fun look(value: String) {
            val index = lookModes.indexOf(value)
            if (index >= 0) lookIndex = index
        }

        fun scene(value: String) {
            val index = sceneModes.indexOf(value)
            if (index >= 0) sceneIndex = index
        }

        val additiveProfile = additiveModeProfile()

        if (additiveProfile != null) {
            quality(additiveProfile.qualityName)
            scene(additiveProfile.sceneName)
            look(additiveProfile.lookName)
            reportHudSizeIndex = 0
            reportHudContrastIndex = 1
            reportHudBackingIndex = 1
            autoDirectorEnabled = false
        } else when (cameraExperienceId) {
            "V205_FOCUS" -> {
                quality("SOCIAL FHD")
                scene("INTERVIEW")
                look("CLEAN")
            }
            "V206_METER" -> {
                quality("SOCIAL FHD")
                scene("REPORTER")
                look("NATURAL")
            }
            "V207_HORIZON" -> {
                quality("SOCIAL FHD")
                scene("OUTDOOR")
                look("NATURAL")
            }
            "V208_STEADY" -> {
                quality("ACTION STAB")
                scene("DOCUMENTARY")
                look("CLEAN")
            }
            "V209_NIGHT" -> {
                quality("LOW LIGHT")
                scene("NIGHT")
                look("NIGHT")
            }
            "V210_ALL_PRO" -> {
                quality("SOCIAL FHD")
                scene("REPORTER")
                look("CLEAN")
            }
            "V211_AUDIO" -> {
                quality("SOCIAL FHD")
                scene("INTERVIEW")
                look("CLEAN")
            }
            "V212_VERIFIED" -> {
                quality("SOCIAL FHD")
                scene("NEWS")
                look("NATURAL")
            }
            "V213_THERMAL" -> {
                quality("SOCIAL FHD")
                scene("REPORTER")
                look("CLEAN")
            }
            "V214_SIGNATURE" -> {
                quality("SOCIAL HDR")
                scene("CINEMA")
                look("WARM")
            }
            "V215_AUTO" -> {
                quality("SOCIAL FHD")
                scene("REPORTER")
                look("CLEAN")
                autoDirectorEnabled = true
            }
            "V222_SOCIAL" -> {
                quality("SOCIAL FHD")
                scene("REPORTER")
                look("CLEAN")
                reportHudSizeIndex = 0
                reportHudContrastIndex = 1
                reportHudBackingIndex = 1
                reportDisplayMode = "SOCIAL POST"
                autoDirectorEnabled = false
            }
        }

        prefs.edit()
            .putBoolean(
                "experience_initialized",
                true
            )
            .apply()

        saveReportCameraPreferences()
    }

    private fun applyIndependentCameraExperienceUi() {
        if (::cameraExperienceBannerView.isInitialized) {
            cameraExperienceBannerView.text =
                "${cameraExperienceDisplayName()}\nBEST FOR • ${cameraExperienceBestFor()}\n${cameraExperienceInstruction()}"
            cameraExperienceBannerView.setTextColor(
                cameraExperienceAccentColor()
            )
        }

        val mutedAlpha = 0.36f

        if (::horizonGuardView.isInitialized) {
            horizonGuardView.alpha =
                if (
                    cameraExperienceId in setOf(
                        "V207_HORIZON",
                        "V210_ALL_PRO",
                        "V212_VERIFIED",
                        "V215_AUTO"
                    )
                ) 1f else mutedAlpha
        }

        if (::motionGuardView.isInitialized) {
            motionGuardView.alpha =
                if (
                    cameraExperienceId in setOf(
                        "V208_STEADY",
                        "V210_ALL_PRO",
                        "V212_VERIFIED",
                        "V215_AUTO"
                    )
                ) 1f else mutedAlpha
        }

        if (::lightAdvisorView.isInitialized) {
            lightAdvisorView.alpha =
                if (
                    cameraExperienceId in setOf(
                        "V209_NIGHT",
                        "V210_ALL_PRO",
                        "V212_VERIFIED",
                        "V215_AUTO"
                    )
                ) 1f else mutedAlpha
        }

        if (::audioGuardView.isInitialized) {
            audioGuardView.alpha =
                if (
                    cameraExperienceId in setOf(
                        "V211_AUDIO",
                        "V210_ALL_PRO",
                        "V212_VERIFIED"
                    )
                ) 1f else mutedAlpha
        }

        if (::thermalGuardView.isInitialized) {
            thermalGuardView.alpha =
                if (
                    cameraExperienceId in setOf(
                        "V213_THERMAL",
                        "V210_ALL_PRO",
                        "V212_VERIFIED",
                        "V215_AUTO"
                    )
                ) 1f else mutedAlpha
        }

        if (::autoDirectorButton.isInitialized) {
            autoDirectorButton.alpha =
                if (
                    cameraExperienceId ==
                        "V215_AUTO"
                ) 1f else 0.64f
        }
    }

    private fun autoDirectorSuggestedMode(): Pair<String, String> {
        if (
            isThermalSevereOrWorse()
        ) {
            return Pair(
                "SOCIAL FHD",
                "THERMAL SAFE"
            )
        }

        val lux =
            ambientLux

        if (
            lux != null &&
            lux < 25f
        ) {
            return Pair(
                "LOW LIGHT",
                "DARK SCENE"
            )
        }

        if (
            cameraShakeScore > 22f &&
            (
                lux == null ||
                lux >= 100f
            )
        ) {
            return Pair(
                "ACTION STAB",
                "HANDHELD MOTION"
            )
        }

        if (
            lux != null &&
            lux >= 500f &&
            cameraShakeScore <= 8f
        ) {
            return Pair(
                "SOCIAL 60",
                "BRIGHT + STEADY"
            )
        }

        return Pair(
            "SOCIAL FHD",
            "BALANCED"
        )
    }

    private fun autoDirectorStateText(): String {
        if (!autoDirectorEnabled) {
            return "AUTO MANUAL"
        }

        val suggestion =
            autoDirectorSuggestedMode()

        return "AUTO ${suggestion.first} • ${suggestion.second}"
    }

    private fun toggleAutoDirector() {
        if (
            recording != null
        ) {
            toast(
                "Stop recording before changing Auto Director"
            )
            return
        }

        autoDirectorEnabled =
            !autoDirectorEnabled

        autoDirectorButton.text =
            "AUTO DIRECTOR ▾\n" +
                if (
                    autoDirectorEnabled
                ) {
                    "ON"
                } else {
                    "OFF"
                }

        autoDirectorButton.isSelected =
            autoDirectorEnabled

        autoDirectorReason =
            if (
                autoDirectorEnabled
            ) {
                autoDirectorSuggestedMode().second
            } else {
                "MANUAL"
            }

        saveReportCameraPreferences()

        toast(
            if (
                autoDirectorEnabled
            ) {
                "AUTO DIRECTOR ON • ${autoDirectorStateText()}"
            } else {
                "AUTO DIRECTOR OFF • manual camera mode"
            }
        )

        if (
            autoDirectorEnabled
        ) {
            applyAutoDirectorIfNeeded(
                force = true
            )
        }
    }

    private fun applyAutoDirectorIfNeeded(
        force: Boolean = false
    ) {
        if (
            !autoDirectorEnabled ||
            recording != null
        ) {
            return
        }

        val (targetMode, reason) =
            autoDirectorSuggestedMode()

        autoDirectorReason =
            reason

        val now =
            SystemClock.elapsedRealtime()

        if (
            !force &&
            now - autoDirectorLastSwitchMs < 3500L
        ) {
            return
        }

        val targetIndex =
            qualityModes.indexOf(
                targetMode
            )

        if (
            targetIndex < 0 ||
            targetIndex == qualityIndex
        ) {
            return
        }

        qualityIndex =
            targetIndex

        autoDirectorLastSwitchMs =
            now

        if (
            ::qualityButton.isInitialized
        ) {
            qualityButton.text =
                "FORMAT ▾\n${qualityDeckLabel()}"
        }

        saveReportCameraPreferences()

        toast(
            "AUTO DIRECTOR • $targetMode • $reason"
        )

        bindCamera()
    }

    private fun previewLookTintColor(): Int {
        // Very low alpha on purpose: this is an operator preview cue.
        // The recorded creative look is still produced by drawCreativeLook().
        return when (
            lookModes[
                lookIndex
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

            "SUNSET" ->
                0x10E06A46.toInt()

            "BLUE HOUR" ->
                0x102F5FA8.toInt()

            "NIGHT" ->
                0x12173363.toInt()

            "MONO" ->
                0x0D707070.toInt()

            "NATURAL" ->
                0x0600A070.toInt()

            else ->
                Color.TRANSPARENT
        }
    }

    private fun reportModeAccentColor(): Int {
        return when (
            qualityModes[
                qualityIndex
            ]
        ) {
            "SOCIAL FHD" ->
                DevelopUgandaFivemods8Theme.accent

            "SOCIAL 60" ->
                DevelopUgandaFivemods8Theme.accent

            "SOCIAL HDR" ->
                DevelopUgandaFivemods8Theme.accent

            "MASTER UHD" ->
                DevelopUgandaFivemods8Theme.accent

            "UHD 60" ->
                DevelopUgandaFivemods8Theme.accent

            "MASTER HDR" ->
                DevelopUgandaFivemods8Theme.accent

            "ACTION STAB" ->
                DevelopUgandaFivemods8Theme.contentDim

            "ACTION 60" ->
                DevelopUgandaFivemods8Theme.accent

            "LOW LIGHT" ->
                DevelopUgandaFivemods8Theme.accent

            "FAST HD" ->
                DevelopUgandaFivemods8Theme.contentDim

            else ->
                DevelopUgandaFivemods8Theme.accent
        }
    }

    private fun reportModePurposeLabel(): String {
        return when (
            qualityModes[
                qualityIndex
            ]
        ) {
            "SOCIAL FHD" ->
                "SOCIAL MASTER"

            "SOCIAL 60" ->
                "SMOOTH SOCIAL"

            "SOCIAL HDR" ->
                "SOCIAL HDR"

            "MASTER UHD" ->
                "4K MASTER"

            "UHD 60" ->
                "4K MOTION"

            "MASTER HDR" ->
                "HDR MASTER"

            "ACTION STAB" ->
                "STABLE ACTION"

            "ACTION 60" ->
                "FAST ACTION"

            "LOW LIGHT" ->
                "LOW LIGHT"

            "FAST HD" ->
                "FAST DELIVERY"

            else ->
                "CAMERA MODE"
        }
    }

    private fun updateReportModePreviewTuning() {
        if (
            ::previewModeToneView.isInitialized
        ) {
            previewModeToneView.setBackgroundColor(
                previewLookTintColor()
            )
        }

        if (
            ::previewTagView.isInitialized
        ) {
            previewTagView.setTextColor(
                reportModeAccentColor()
            )

            previewTagView.text =
                "${sceneTag()} • ${reportModePurposeLabel()} • ${lookModes[lookIndex]} • V221"
        }
    }

    private fun thermalStateLabel(): String {
        if (
            Build.VERSION.SDK_INT <
                Build.VERSION_CODES.Q
        ) {
            return "UNAVAILABLE"
        }

        return when (
            thermalStatus
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

    private fun isThermalSevereOrWorse(): Boolean {
        return (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q &&
            thermalStatus >=
                PowerManager.THERMAL_STATUS_SEVERE
            )
    }

    private fun updateThermalGuard() {
        if (
            !::thermalGuardView.isInitialized
        ) {
            return
        }

        if (
            operatorControlsHidden ||
            cleanModeEnabled
        ) {
            thermalGuardView.visibility =
                View.GONE

            return
        }

        thermalGuardView.visibility =
            View.VISIBLE

        val label =
            thermalStateLabel()

        thermalGuardView.text =
            "THERMAL • $label"

        thermalGuardView.setTextColor(
            when (label) {
                "NORMAL",
                "LIGHT" ->
                    DevelopUgandaFivemods8Theme.contentDim

                "MODERATE" ->
                    DevelopUgandaFivemods8Theme.accent

                "SEVERE",
                "CRITICAL",
                "EMERGENCY",
                "SHUTDOWN" ->
                    DevelopUgandaFivemods8Theme.accent

                else ->
                    DevelopUgandaFivemods8Theme.contentDim
            }
        )
    }

    private fun applyThermalSafeProfileIfNeeded() {
        if (v255ThermalPolicy() != "AUTO SAFE") {
            return
        }

        if (
            !isThermalSevereOrWorse() ||
            recording !=
                null
        ) {
            return
        }

        val current =
            qualityModes[
                qualityIndex
            ]

        val highDemand =
            current in
                setOf(
                    "MASTER UHD",
                    "UHD 60",
                    "MASTER HDR",
                    "SOCIAL HDR",
                    "ACTION 60"
                )

        if (!highDemand) {
            return
        }

        val safeIndex =
            qualityModes.indexOf(
                "SOCIAL FHD"
            )

        if (
            safeIndex >=
                0 &&
            safeIndex !=
                qualityIndex
        ) {
            qualityIndex =
                safeIndex

            if (
                ::qualityButton.isInitialized
            ) {
                qualityButton.text =
                    "FORMAT ▾\n${qualityDeckLabel()}"
            }

            toast(
                "THERMAL ${thermalStateLabel()} • switched to SOCIAL FHD"
            )
        }
    }

    private fun verifiedCameraStateText(): String {
        val rollText =
            phoneRollDeg?.let {
                String.format(
                    Locale.US,
                    "%+.1f°",
                    it
                )
            } ?: "--"

        val luxText =
            ambientLux?.let {
                String.format(
                    Locale.US,
                    "%.0fLUX",
                    it
                )
            } ?: "--"

        return buildString {
            append("V216 VERIFIED")
            append(" • ")
            append(qualityDeckLabel())
            append(" • ")
            append(
                reportModePurposeLabel()
            )
            append(" • LOOK ")
            append(
                lookModes[
                    lookIndex
                ]
            )
            append(" • ")
            append(activeVideoFpsLabel)
            append(" • ")
            append(activeVideoStabilizationLabel)
            append(" • ")
            append(activeVideoDynamicRangeLabel)
            append(" • ")
            append(focusAssistLabel())
            append(" • H ")
            append(rollText)
            append(" • ")
            append(motionGuardLabel())
            append(" • ")
            append(ambientLightLabel())
            append(" ")
            append(luxText)
            append(" • AUDIO ")
            append(audioGuardLabel())
            append(" • THERMAL ")
            append(
                thermalStateLabel()
            )
            append(" • ")
            append(
                autoDirectorStateText()
            )
            append(" • CAMERA ")
            append(
                cameraExperienceShortLabel()
            )
        }
    }

    private fun socialCameraStatus(): String {
        return buildString {
            append("CREATOR ENGINE • ")
            append(coreDeliveredProfileLabel)
            append(" • ")
            append(activeVideoFpsLabel)
            append(" • ")
            append(activeVideoStabilizationLabel)
            append(" • ")
            append(activeVideoDynamicRangeLabel)
            append(" • ")
            append(activeVideoAspectLabel)
            append(" • ")
            append(coreDisplayedBitrate() / 1_000_000)
            append("Mbps DEVICE TARGET")
            append(" • TAP AF")
            append(" • HOLD AF+AE+AWB METER")
            append(" • HORIZON GUARD")
            append(" • STEADYSHOT ")
            append(
                motionGuardLabel()
            )
            append(" • ")
            append(
                ambientLightRecommendation()
            )
            append(" • AUDIO ")
            append(
                audioGuardLabel()
            )
        }
    }

    private fun sceneTag(): String {
        if (reportDisplayMode == "LIVE EFFECT") {
            return "LIVE EFFECT"
        }

        return when (
            sceneModes[
                sceneIndex
            ]
        ) {
            "NEWS" -> "NEWS DESK"
            "CINEMA" -> "CINEMA UNIT"
            "MOVIE" -> "MOVIE UNIT"
            "OUTDOOR" -> "OUTDOOR UNIT"
            "INDOOR" -> "INTERIOR UNIT"
            "NIGHT" -> "NIGHT DESK"
            "INTERVIEW" -> "INTERVIEW UNIT"
            "DOCUMENTARY" -> "DOCUMENTARY UNIT"
            else -> "FIELD REPORT"
        }
    }

    private fun buildQualityOrder(): List<Quality> {
        additiveModeProfile()?.let {
            // The three delivery pages request a portrait 1080 master first.
            // Fallback order is kept local to them; no existing experience
            // changes its quality negotiation.
            return listOf(
                Quality.FHD,
                Quality.HD,
                Quality.UHD
            )
        }

        return when (
                qualityModes[
                    qualityIndex
                ]
            ) {
                // FHD Pro Clear Video: the tested clip was 720p/12 Mbps.
                // Prefer a clean, broadly compatible FHD master.  4K remains
                // a real fallback when the selected camera exposes it.
                "MASTER UHD",
                "UHD 60",
                "MASTER HDR" ->
                    listOf(
                        Quality.FHD,
                        Quality.UHD,
                        Quality.HD
                    )

                "SOCIAL HDR" ->
                    listOf(
                        Quality.FHD,
                        Quality.UHD,
                        Quality.HD
                    )

                "FAST HD" ->
                    listOf(
                        Quality.HD,
                        Quality.FHD,
                        Quality.UHD
                    )

                else ->
                    listOf(
                        Quality.FHD,
                        Quality.UHD,
                        Quality.HD
                    )
            }
    }

    private fun buildQualitySelector(): QualitySelector {
        return QualitySelector
            .fromOrderedList(
                buildQualityOrder(),
                FallbackStrategy
                    .lowerQualityOrHigherThan(
                        Quality.HD
                    )
            )
    }

    private fun coreQualityLabel(quality: Quality): String = when {
        quality == Quality.UHD -> "4K"
        quality == Quality.FHD -> "1080"
        quality == Quality.HD -> "720"
        else -> "DEVICE"
    }

    /**
     * Convert the existing SAFE / STANDARD / HIGH / MAX UI policy into a real
     * CameraX encoder target that remains sensible for the delivered profile.
     * CameraX/codec may still choose a device-specific throughput.
     */
    private fun coreBitrateFor(quality: Quality): Int {
        val requested = targetVideoBitrate()
        additiveModeProfile()?.let {
            return when {
                quality == Quality.FHD -> requested
                quality == Quality.HD -> requested.coerceIn(5_000_000, 14_000_000)
                quality == Quality.UHD -> requested.coerceIn(18_000_000, 45_000_000)
                else -> requested.coerceIn(4_000_000, 24_000_000)
            }
        }

        return when {
            quality == Quality.UHD -> requested.coerceIn(18_000_000, 45_000_000)
            quality == Quality.FHD -> requested.coerceIn(16_000_000, 24_000_000)
            quality == Quality.HD -> requested.coerceIn(5_000_000, 14_000_000)
            else -> requested.coerceIn(4_000_000, 24_000_000)
        }
    }

    private fun coreDisplayedBitrate(): Int =
        coreDeliveredBitrateBps.takeIf { it > 0 } ?: targetVideoBitrate()

    private fun applyScenePreset() {
        sceneExposureTarget =
            when (
                sceneModes[
                    sceneIndex
                ]
            ) {
                "CINEMA" -> -1
                "MOVIE" -> -1
                "OUTDOOR" -> -1
                "INDOOR" -> 1
                "NIGHT" -> 2
                "INTERVIEW" -> 0
                "DOCUMENTARY" -> 0
                else -> 0
            }

        applyExposure(
            sceneExposureTarget
        )

        if (
            ::exposureSeek.isInitialized
        ) {
            val range =
                camera?.cameraInfo?.exposureState
                    ?.takeIf { it.isExposureCompensationSupported }
                    ?.exposureCompensationRange
            if (range != null) {
                exposureSeek.max =
                    (range.upper - range.lower).coerceAtLeast(1)
                exposureSeek.progress =
                    (sceneExposureTarget - range.lower)
                        .coerceIn(0, exposureSeek.max)
            }
        }
    }

    private fun drawCreativeLook(
        canvas: Canvas,
        width: Float,
        height: Float
    ) {
        val color: Int =
            when (
                lookModes[
                    lookIndex
                ]
            ) {
                "NATURAL" ->
                    0x06FFF4E8

                "WARM" ->
                    0x0DFF8A45

                "COOL" ->
                    0x0C2F72FF

                "TEAL" ->
                    0x0D00A7A1

                "GOLD" ->
                    0x0EF2B43C

                "SOFT" ->
                    0x08FFFFFF

                "SUNSET" ->
                    0x10FF7040

                "BLUE HOUR" ->
                    0x102D62C7

                "NIGHT" ->
                    0x14092346

                "MONO" ->
                    0x12000000

                else ->
                    Color.TRANSPARENT
            }

        if (
            color ==
            Color.TRANSPARENT
        ) {
            return
        }

        val grade = Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            this.color = color
        }

        canvas.drawRect(
            0f,
            0f,
            width,
            height,
            grade
        )
    }

    private fun writeTelemetry() {
        val now = System.currentTimeMillis()
        val elapsed = if (recStarted == 0L) {
            0L
        } else {
            now - recStarted
        }

        val wx = weather.latest

        telemetryRecorder.add(
            TelemetrySample(
                elapsedMs = elapsed,
                timestampIso = Instant.ofEpochMilli(now).toString(),
                timezone = ZoneId.systemDefault().id,
                latitude = lat,
                longitude = lon,
                altitudeM = alt,
                accuracyM = accuracy,
                headingDeg = heading,
                speedKmh = speedKmh,
                placeName = placeName,
                temperatureC = wx.temperatureC,
                condition = wx.condition,
                humidityPct = wx.humidityPct,
                windKmh = wx.windKmh,
                windDirectionDeg = wx.windDirectionDeg,
                networkType = networkType(),
                estimatedUploadKbps = estimatedUploadKbps,
                droppedFrames = 0L,
                batteryPct = batteryPct(),
                freeStorageGb = freeStorageGb()
            )
        )
    }

    private fun locationOverlay(): String {
        val b = StringBuilder(placeName)

        if (lat != null && lon != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • LAT %.5f LON %.5f",
                    lat,
                    lon
                )
            )
        }

        if (alt != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • ALT %.0fm",
                    alt
                )
            )
        }

        if (accuracy != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • GPS ±%.0fm",
                    accuracy
                )
            )
        }

        if (heading != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • HDG %.0f°",
                    heading
                )
            )
        }

        if (speedKmh != null) {
            b.append(
                String.format(
                    Locale.US,
                    " • SPD %.1fkm/h",
                    speedKmh
                )
            )
        }

        return b.toString()
    }

    private fun weatherOverlay(): String {
        val wx = weather.latest

        return buildString {
            append("WX ")

            if (wx.temperatureC != null) {
                append(
                    String.format(
                        Locale.US,
                        "%.1f°C",
                        wx.temperatureC
                    )
                )
            } else {
                append("--")
            }

            append(" • ${wx.condition}")

            if (wx.humidityPct != null) {
                append(" • RH ${wx.humidityPct}%")
            }

            if (wx.windKmh != null) {
                append(
                    String.format(
                        Locale.US,
                        " • WIND %.1fkm/h",
                        wx.windKmh
                    )
                )
            }

            if (wx.windDirectionDeg != null) {
                append(
                    String.format(
                        Locale.US,
                        " %.0f°",
                        wx.windDirectionDeg
                    )
                )
            }
        }
    }

    private fun systemOverlay(): String {
        val net = networkType()
        val uplink =
            estimatedUploadKbps?.let {
                "UP~${it}kbps"
            } ?: "UP~--"

        return "NET $net • $uplink" +
            " • BAT ${batteryPct() ?: "--"}%" +
            " • FREE ${freeStorageGb() ?: "--"}GB"
    }

    private fun networkType(): String {
        val cm = getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager

        val n = cm.activeNetwork ?: return "OFFLINE"
        val caps = cm.getNetworkCapabilities(n)
            ?: return "OFFLINE"

        estimatedUploadKbps =
            caps.linkUpstreamBandwidthKbps
                .takeIf { it > 0 }

        return when {
            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            ) -> "WiFi"

            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR
            ) -> "CELL"

            caps.hasTransport(
                NetworkCapabilities.TRANSPORT_ETHERNET
            ) -> "ETH"

            else -> "NET"
        }
    }

    private fun batteryPct(): Int? {
        val bm = getSystemService(
            BATTERY_SERVICE
        ) as BatteryManager

        return bm.getIntProperty(
            BatteryManager.BATTERY_PROPERTY_CAPACITY
        ).takeIf { it >= 0 }
    }

    private fun freeStorageGb(): Long? {
        return try {
            StatFs(
                Environment.getExternalStorageDirectory().path
            ).availableBytes / (
                1024L * 1024L * 1024L
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun tc(): String {
        if (v256TimecodeModeState == "TIME OF DAY") {
            return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        }

        val ms = when (v256TimecodeModeState) {
            "FREE RUN" -> (SystemClock.elapsedRealtime() - v256FreeRunAnchorRealtime).coerceAtLeast(0L)
            else -> if (recStarted == 0L) 0L else System.currentTimeMillis() - recStarted
        }

        val seconds = ms / 1000L

        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            seconds / 3600L,
            (seconds % 3600L) / 60L,
            seconds % 60L
        )
    }

    private fun toggleTorch() {
        val cam = camera ?: return

        if (
            !cam.cameraInfo.hasFlashUnit() ||
            useFront
        ) {
            toast("Torch is unavailable on this lens")
            return
        }

        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        torchButton.text = if (torchOn) {
            "LIGHT\nON"
        } else {
            "LIGHT\nOFF"
        }
    }

    private fun syncCameraRanges() {
        val cam = camera ?: return

        val z: ZoomState? =
            cam.cameraInfo.zoomState.value

        if (z != null) {
            val progress =
                if (
                    z.maxZoomRatio >
                    z.minZoomRatio
                ) {
                    (
                        (
                            z.zoomRatio -
                                z.minZoomRatio
                            ) /
                            (
                                z.maxZoomRatio -
                                    z.minZoomRatio
                                ) *
                            100f
                        ).roundToInt()
                } else {
                    0
                }

            zoomSeek.progress =
                progress.coerceIn(0, 100)
        }

        val e: ExposureState =
            cam.cameraInfo.exposureState

        exposureSeek.isEnabled =
            e.isExposureCompensationSupported

        if (
            e.isExposureCompensationSupported
        ) {
            val range = e.exposureCompensationRange
            exposureSeek.max =
                (range.upper - range.lower).coerceAtLeast(1)
            exposureSeek.progress =
                (e.exposureCompensationIndex - range.lower)
                    .coerceIn(0, exposureSeek.max)
        }
    }

    private fun applyZoom(progress: Int) {
        val cam = camera ?: return
        val z =
            cam.cameraInfo.zoomState.value
                ?: return

        val ratio =
            z.minZoomRatio +
                (
                    z.maxZoomRatio -
                        z.minZoomRatio
                    ) *
                (progress / 100f)

        cam.cameraControl.setZoomRatio(
            ratio
        )
    }

    private fun applyExposure(value: Int) {
        val cam = camera ?: return
        val e = cam.cameraInfo.exposureState

        if (
            !e.isExposureCompensationSupported
        ) {
            return
        }

        cam.cameraControl
            .setExposureCompensationIndex(
                value.coerceIn(
                    e.exposureCompensationRange.lower,
                    e.exposureCompensationRange.upper
                )
            )
    }

    private fun tapToFocus(
        x: Float,
        y: Float
    ) {
        coreRequestFocus(x, y, false)
    }

    private inner class DeckTouchListener(
        private val actionCode: Int
    ) : View.OnTouchListener {

        override fun onTouch(
            v: View?,
            event: MotionEvent
        ): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (
                        operatorLocked &&
                        actionCode != ACTION_RECORD &&
                        actionCode != ACTION_LOCK
                    ) {
                        toast(
                            "Operator controls locked"
                        )
                        return true
                    }

                    v?.isPressed = true
                    v?.alpha = 0.94f
                    v257AnimateDeckPress(v, true)
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    v?.isPressed = false
                    v?.alpha = 1f
                    v257AnimateDeckPress(v, false)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    v?.isPressed = false
                    v?.alpha = 1f
                    v257AnimateDeckPress(v, false)
                    v?.performClick()

                    when (actionCode) {
                        ACTION_SCENE ->
                            showReportSceneDropdown(
                                v ?: sceneButton
                            )

                        ACTION_LOOK ->
                            showReportLookDropdown(
                                v ?: lookButton
                            )

                        ACTION_QUALITY ->
                            showReportQualityDropdown(
                                v ?: qualityButton
                            )

                        ACTION_CAPTURE_MODE ->
                            showReportCaptureDropdown(
                                v ?: captureModeButton
                            )
                        ACTION_IDENTITY -> showIdentityDialog()
                        ACTION_VIEW_MODE ->
                            showReportViewDropdown(
                                v ?: viewModeButton
                            )

                        ACTION_SETTINGS ->
                            showReportDetailedSettings()

                        ACTION_GUIDES ->
                            showReportGuidesDropdown(
                                v ?: guidesButton
                            )

                        ACTION_RESET ->
                            resetReportCameraSettings()

                        ACTION_AUTO_UI ->
                            showReportAutoUiDropdown(
                                v ?: autoUiButton
                            )

                        ACTION_LOCK ->
                            showReportLockDropdown(
                                v ?: lockButton
                            )

                        ACTION_INTEGRITY ->
                            showReportIntegrityDropdown(
                                v ?: integrityButton
                            )

                        ACTION_CAPABILITIES ->
                            openV227CameraHealth()

                        ACTION_CLEAN ->
                            showReportCleanDropdown(
                                v ?: cleanModeButton
                            )

                        ACTION_HUD_SIZE ->
                            showReportHudSizeDropdown(
                                v ?: hudSizeButton
                            )

                        ACTION_HUD_CONTRAST ->
                            showReportHudContrastDropdown(
                                v ?: hudContrastButton
                            )

                        ACTION_HUD_BACKING ->
                            showReportHudBackingDropdown(
                                v ?: hudBackingButton
                            )

                        ACTION_REPORT_PRESET ->
                            showReportPresetDropdown(
                                v ?: reportPresetButton
                            )

                        ACTION_AUTO_DIRECTOR ->
                            toggleAutoDirector()

                        ACTION_SHOT_ASSIST ->
                            cycleShotAssist()

                        ACTION_DIRECTOR ->
                            toggleDirectorGuidance()

                        ACTION_CONTINUITY ->
                            matchLastShotContinuity()

                        ACTION_HEALTH ->
                            openV227CameraHealth()

                        ACTION_BRAND_METADATA ->
                            openV228BrandMetadataStudio()

                        ACTION_COLOR_ENGINE ->
                            showV233ColorDropdown(
                                colorButton
                            )

                        ACTION_LENS ->
                            showReportLensDropdown(
                                v ?: lensButton
                            )

                        ACTION_TORCH ->
                            showReportLightDropdown(
                                v ?: torchButton
                            )
                        ACTION_RECORD -> toggleRecording()
                    }

                    return true
                }
            }

            return true
        }
    }

    private fun simpleSeek(
        onChange: (Int) -> Unit
    ) = object :
        SeekBar.OnSeekBarChangeListener {

        override fun onProgressChanged(
            seekBar: SeekBar?,
            progress: Int,
            fromUser: Boolean
        ) {
            if (fromUser) {
                onChange(progress)
            }
        }

        override fun onStartTrackingTouch(
            seekBar: SeekBar?
        ) {
        }

        override fun onStopTrackingTouch(
            seekBar: SeekBar?
        ) {
        }
    }

    private fun row(): LinearLayout {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER_VERTICAL
        }
    }

    private fun hud(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            gravity =
                Gravity.CENTER_VERTICAL

            typeface = Typeface.create(
                Typeface.MONOSPACE,
                if (bold) {
                    Typeface.BOLD
                } else {
                    Typeface.NORMAL
                }
            )


            setPadding(
                dp(3),
                dp(1),
                dp(3),
                dp(1)
            )
        }
    }

    private fun deckButton(
        value: String,
        accentColor: Int
    ): Button {
        return OutlineSettingButton(
            this,
            accentColor
        ).apply {
            text = value
            textSize = 5.7f
            isAllCaps = false
            setTextColor(DevelopUgandaFivemods8Theme.content)
            gravity = Gravity.CENTER
            includeFontPadding = false
            minHeight = dp(34)

            setPadding(
                dp(5),
                0,
                dp(5),
                0
            )

            // V191: the custom button itself draws the thick bright ring.
            // It becomes fully filled only when isSelected == true.
            background =
                ColorDrawable(
                    DevelopUgandaFivemods8Theme.transparent
                )

            stateListAnimator =
                null
        }
    }

    private fun pillFillColor(
        accent: Int,
        selected: Boolean = false
    ): Int {
        return if (selected) {
            DevelopUgandaFivemods8Theme.surfaceRaised
        } else {
            DevelopUgandaFivemods8Theme.surface
        }
    }

    private fun solidPillBackground(
        accent: Int,
        selected: Boolean = false
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape =
                GradientDrawable.RECTANGLE

            cornerRadius =
                dp(20).toFloat()

            setColor(
                pillFillColor(
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

    private fun reportOptionAccent(
        index: Int
    ): Int = DevelopUgandaFivemods8Theme.accent

    private fun showReportPillDropdown(
        anchor: View,
        title: String,
        options: List<String>,
        selectedIndex: Int,
        onPick: (Int) -> Unit
    ) {
        showReportPillDropdown(
            anchor,
            title,
            options.toTypedArray(),
            selectedIndex,
            onPick
        )
    }

    private fun showReportPillDropdown(
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
                            DevelopUgandaFivemods8Theme.accentScrim(80)
                        )
                    }
            }

        panel.addView(
            hud(
                title,
                7.4f,
                DevelopUgandaFivemods8Theme.content,
                bold = true
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(27)
            )
        )

        val popup =
            PopupWindow(
                panel,
                dp(188),
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
                reportOptionAccent(
                    index
                )

            val pill =
                OutlineSettingButton(
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
                        7.2f

                    isAllCaps =
                        false

                    setTextColor(
                        DevelopUgandaFivemods8Theme.content
                    )

                    gravity =
                        Gravity.CENTER

                    setPadding(
                        dp(9),
                        0,
                        dp(9),
                        0
                    )

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
                    dp(36)
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

    private fun showReportSceneDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Stop recording before changing scene"
            )
            return
        }

        showReportPillDropdown(
            anchor,
            "SCENE",
            sceneModes,
            sceneIndex
        ) { picked ->
            sceneIndex =
                picked

            markReportPresetCustom()
            applyScenePreset()
            saveReportCameraPreferences()
            refreshHud()
        }
    }

    private fun showReportLookDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "LOOK",
            lookModes,
            lookIndex
        ) { picked ->
            lookIndex =
                picked
            markReportPresetCustom()
            saveReportCameraPreferences()
            refreshHud()
        }
    }

    private fun showReportQualityDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Stop recording before changing format"
            )
            return
        }

        showReportPillDropdown(
            anchor,
            "FORMAT",
            qualityModes,
            qualityIndex
        ) { picked ->
            qualityIndex =
                picked
            markReportPresetCustom()
            saveReportCameraPreferences()
            refreshHud()
            bindCamera()
        }
    }

    private fun showReportCaptureDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Stop recording before changing capture mode"
            )
            return
        }

        showReportPillDropdown(
            anchor,
            "CAPTURE",
            captureModes,
            captureModeIndex
        ) { picked ->
            captureModeIndex =
                picked
            markReportPresetCustom()
            saveReportCameraPreferences()
            refreshHud()
            bindCamera()
        }
    }

    private fun showReportViewDropdown(
        anchor: View
    ) {
        val selected =
            if (halfPreviewMode) {
                1
            } else {
                0
            }

        showReportPillDropdown(
            anchor,
            "VIEW",
            arrayOf(
                "FULL SCREEN",
                "HALF SCREEN"
            ),
            selected
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

    private fun showReportGuidesDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "GUIDES",
            arrayOf(
                "ON",
                "OFF"
            ),
            if (previewGuidesEnabled) 0 else 1
        ) { picked ->
            val want =
                picked ==
                    0

            if (
                want !=
                previewGuidesEnabled
            ) {
                togglePreviewGuides()
            }
        }
    }

    private fun showReportAutoUiDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "AUTO UI",
            arrayOf(
                "ON",
                "OFF"
            ),
            if (autoHideOperatorUi) 0 else 1
        ) { picked ->
            val want =
                picked ==
                    0

            if (
                want !=
                autoHideOperatorUi
            ) {
                toggleReportAutoUi()
            }
        }
    }

    private fun showReportLockDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "OPERATOR LOCK",
            arrayOf(
                "OFF",
                "ON"
            ),
            if (operatorLocked) 1 else 0
        ) { picked ->
            val want =
                picked ==
                    1

            if (
                want !=
                operatorLocked
            ) {
                toggleReportOperatorLock()
            }
        }
    }

    private fun showReportIntegrityDropdown(
        anchor: View
    ) {
        if (recording != null) {
            toast(
                "Change verification before recording"
            )
            return
        }

        showReportPillDropdown(
            anchor,
            "VERIFY",
            arrayOf(
                "SHA-256 ON",
                "OFF"
            ),
            if (integrityEnabled) 0 else 1
        ) { picked ->
            val want =
                picked ==
                    0

            if (
                want !=
                integrityEnabled
            ) {
                toggleReportIntegrity()
            }
        }
    }

    private fun showReportCleanDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "CLEAN MODE",
            arrayOf(
                "OFF",
                "ON"
            ),
            if (cleanModeEnabled) 1 else 0
        ) { picked ->
            val want =
                picked ==
                    1

            if (
                want !=
                cleanModeEnabled
            ) {
                toggleReportCleanMode()
            }
        }
    }

    private fun showReportHudSizeDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "RECORDED HUD SIZE",
            reportHudLabels,
            reportHudSizeIndex
        ) { picked ->
            reportHudSizeIndex =
                picked

            markReportPresetCustom()

            hudSizeButton.text =
                "HUD SIZE ▾\n${reportHudLabels[reportHudSizeIndex]}"

            hudSizeButton.isSelected =
                true

            applyAdaptiveReportPreviewTypography()
            saveReportCameraPreferences()
            refreshHud()

            toast(
                "HUD ${reportHudLabels[reportHudSizeIndex]}"
            )
        }
    }

    private fun showReportHudBackingDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "RECORDED HUD BACKING",
            reportHudBackingLabels,
            reportHudBackingIndex
        ) { picked ->
            reportHudBackingIndex =
                picked

            markReportPresetCustom()

            hudBackingButton.text =
                "HUD BACKING ▾\n${reportHudBackingLabels[reportHudBackingIndex]}"

            hudBackingButton.isSelected =
                reportHudBackingIndex !=
                    0

            saveReportCameraPreferences()
            refreshHud()

            toast(
                "HUD backing ${reportHudBackingLabels[reportHudBackingIndex]}"
            )
        }
    }

    private fun markReportPresetCustom() {
        if (
            reportPresetIndex !=
            0
        ) {
            reportPresetIndex =
                0

            if (
                ::reportPresetButton.isInitialized
            ) {
                reportPresetButton.text =
                    "PRESET ▾\nCUSTOM"

                reportPresetButton.isSelected =
                    false
            }
        }
    }

    private fun reportIndexOf(
        values: List<String>,
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

    private fun applyReportPreset(
        picked: Int
    ) {
        reportPresetIndex =
            picked.coerceIn(
                0,
                reportPresetLabels.lastIndex
            )

        when (
            reportPresetLabels[
                reportPresetIndex
            ]
        ) {
            "FIELD" -> {
                sceneIndex =
                    reportIndexOf(
                        sceneModes,
                        "REPORTER"
                    )

                lookIndex =
                    reportIndexOf(
                        lookModes,
                        "CLEAN"
                    )

                qualityIndex =
                    reportIndexOf(
                        qualityModes,
                        "SOCIAL FHD"
                    )

                captureModeIndex =
                    reportIndexOf(
                        captureModes,
                        "VIDEO"
                    )

                reportHudSizeIndex =
                    1

                reportHudContrastIndex =
                    1

                previewGuidesEnabled =
                    true

                integrityEnabled =
                    true

                reportHudBackingIndex =
                    1
            }

            "OUTDOOR" -> {
                sceneIndex =
                    reportIndexOf(
                        sceneModes,
                        "OUTDOOR"
                    )

                lookIndex =
                    reportIndexOf(
                        lookModes,
                        "NATURAL"
                    )

                qualityIndex =
                    reportIndexOf(
                        qualityModes,
                        "SOCIAL FHD"
                    )

                captureModeIndex =
                    reportIndexOf(
                        captureModes,
                        "VIDEO"
                    )

                reportHudSizeIndex =
                    1

                reportHudContrastIndex =
                    2

                previewGuidesEnabled =
                    true

                integrityEnabled =
                    true

                reportHudBackingIndex =
                    1
            }

            "NIGHT" -> {
                sceneIndex =
                    reportIndexOf(
                        sceneModes,
                        "NIGHT"
                    )

                lookIndex =
                    reportIndexOf(
                        lookModes,
                        "NIGHT"
                    )

                qualityIndex =
                    reportIndexOf(
                        qualityModes,
                        "SOCIAL FHD"
                    )

                captureModeIndex =
                    reportIndexOf(
                        captureModes,
                        "VIDEO"
                    )

                reportHudSizeIndex =
                    1

                reportHudContrastIndex =
                    2

                previewGuidesEnabled =
                    true

                integrityEnabled =
                    true

                reportHudBackingIndex =
                    1
            }

            "INTERVIEW" -> {
                sceneIndex =
                    reportIndexOf(
                        sceneModes,
                        "INTERVIEW"
                    )

                lookIndex =
                    reportIndexOf(
                        lookModes,
                        "NATURAL"
                    )

                qualityIndex =
                    reportIndexOf(
                        qualityModes,
                        "SOCIAL FHD"
                    )

                captureModeIndex =
                    reportIndexOf(
                        captureModes,
                        "VIDEO"
                    )

                reportHudSizeIndex =
                    0

                reportHudContrastIndex =
                    1

                previewGuidesEnabled =
                    true

                integrityEnabled =
                    true

                reportHudBackingIndex =
                    1
            }

            "CINEMA" -> {
                sceneIndex =
                    reportIndexOf(
                        sceneModes,
                        "CINEMA"
                    )

                lookIndex =
                    reportIndexOf(
                        lookModes,
                        "TEAL"
                    )

                qualityIndex =
                    reportIndexOf(
                        qualityModes,
                        "MASTER UHD"
                    )

                captureModeIndex =
                    reportIndexOf(
                        captureModes,
                        "VIDEO"
                    )

                reportHudSizeIndex =
                    0

                reportHudContrastIndex =
                    0

                previewGuidesEnabled =
                    true

                integrityEnabled =
                    false
            }

            else -> {
                // CUSTOM keeps the current manual values.

                reportHudBackingIndex =
                    1
            }
        }

        reportPresetButton.text =
            "PRESET ▾\n${reportPresetLabels[reportPresetIndex]}"

        reportPresetButton.isSelected =
            reportPresetIndex !=
                0

        hudSizeButton.text =
            "HUD SIZE ▾\n${reportHudLabels[reportHudSizeIndex]}"

        hudContrastButton.text =
            "HUD CONTRAST ▾\n${reportHudContrastLabels[reportHudContrastIndex]}"

        hudBackingButton.text =
            "HUD BACKING ▾\n${reportHudBackingLabels[reportHudBackingIndex]}"

        hudBackingButton.isSelected =
            reportHudBackingIndex !=
                0

        guidesButton.text =
            "GUIDES ▾\n" +
                if (
                    previewGuidesEnabled
                ) {
                    "ON"
                } else {
                    "OFF"
                }

        integrityButton.text =
            "VERIFY ▾\n" +
                if (
                    integrityEnabled
                ) {
                    "SHA-256"
                } else {
                    "OFF"
                }

        sceneButton.text =
            "SCENE ▾\n${sceneModes[sceneIndex]}"

        lookButton.text =
            "LOOK ▾\n${lookModes[lookIndex]}"

        qualityButton.text =
            "FORMAT ▾\n${qualityDeckLabel()}"

        captureModeButton.text =
            "CAPTURE ▾\n${captureModes[captureModeIndex]}"

        applyScenePreset()
        applyAdaptiveReportPreviewTypography()
        saveReportCameraPreferences()
        refreshHud()

        if (
            recording ==
            null
        ) {
            bindCamera()
        }

        toast(
            "Preset ${reportPresetLabels[reportPresetIndex]}"
        )
    }

    private fun showReportPresetDropdown(
        anchor: View
    ) {
        if (
            recording !=
            null
        ) {
            toast(
                "Stop recording before changing preset"
            )
            return
        }

        showReportPillDropdown(
            anchor,
            "REPORT PRESET",
            reportPresetLabels,
            reportPresetIndex
        ) { picked ->
            applyReportPreset(
                picked
            )
        }
    }

    private fun showReportHudContrastDropdown(
        anchor: View
    ) {
        showReportPillDropdown(
            anchor,
            "RECORDED HUD CONTRAST",
            reportHudContrastLabels,
            reportHudContrastIndex
        ) { picked ->
            reportHudContrastIndex =
                picked

            markReportPresetCustom()

            hudContrastButton.text =
                "HUD CONTRAST ▾\n${reportHudContrastLabels[reportHudContrastIndex]}"

            hudContrastButton.isSelected =
                true

            saveReportCameraPreferences()
            refreshHud()

            toast(
                "HUD contrast ${reportHudContrastLabels[reportHudContrastIndex]}"
            )
        }
    }

    private fun showReportLensDropdown(
        anchor: View
    ) {
        if (
            recording !=
                null
        ) {
            toast(
                "Stop recording before changing lens"
            )
            return
        }

        val selected =
            when {
                selectedCameraDeviceId !=
                    null ->
                        2

                useFront ->
                    1

                else ->
                    0
            }

        showReportPillDropdown(
            anchor,
            "REAL LENS INTELLIGENCE",
            arrayOf(
                "AUTO BACK",
                "AUTO FRONT",
                "REAL CAMERAS"
            ),
            selected
        ) {
                picked ->
            when (
                picked
            ) {
                0 -> {
                    selectedCameraDeviceId =
                        null

                    useFront =
                        false

                    saveReportCameraPreferences()
                    bindCamera()
                }

                1 -> {
                    selectedCameraDeviceId =
                        null

                    useFront =
                        true

                    saveReportCameraPreferences()
                    bindCamera()
                }

                else ->
                    showRealCameraDevicePicker()
            }
        }
    }

    private fun showReportLightDropdown(
        anchor: View
    ) {
        val current =
            camera
                ?.cameraInfo
                ?.torchState
                ?.value ==
                TorchState.ON

        showReportPillDropdown(
            anchor,
            "LIGHT",
            arrayOf(
                "OFF",
                "ON"
            ),
            if (current) 1 else 0
        ) { picked ->
            val wantOn =
                picked ==
                    1

            camera
                ?.cameraControl
                ?.enableTorch(
                    wantOn
                )

            torchButton.text =
                "LIGHT ▾\n" +
                    if (wantOn) {
                        "ON"
                    } else {
                        "OFF"
                    }
        }
    }

    private fun actionButton(
        value: String
    ): Button {
        return deckButton(
            value,
            DevelopUgandaFivemods8Theme.contentScrim(153)
        )
    }

    private fun makeRecordButton(): Button {
        return Button(this).apply {
            text = "● RECORD"
            textSize = 9.5f
            isAllCaps = false
            setTextColor(DevelopUgandaFivemods8Theme.content)
            minHeight = dp(48)

            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.RECTANGLE
                    cornerRadius =
                        dp(26).toFloat()

                    setColor(
                        DevelopUgandaFivemods8Theme.recordScrim(217)
                    )

                    setStroke(
                        dp(1),
                        DevelopUgandaFivemods8Theme.record
                    )
                }
        }
    }

    private fun space(width: Int): View {
        return View(this).apply {
            minimumWidth = width
        }
    }

    private fun weight():
        LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    private fun wrap(
        widthDp: Int,
        heightDp: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            dp(widthDp),
            dp(heightDp)
        )
    }

    private fun dp(value: Int): Int {
        return (
            value *
                resources.displayMetrics.density
            ).roundToInt()
    }

    private fun toast(value: String) {
        Toast.makeText(
            this,
            value,
            Toast.LENGTH_SHORT
        ).show()
    }

    private class OutlineSettingButton(
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

        private val idleFillPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    DevelopUgandaFivemods8Theme.surfaceScrim(82)
            }

        private val pressedFillPaint =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.FILL

                color =
                    DevelopUgandaFivemods8Theme.accentScrim(79)
            }

        private val selectedFillPaint =
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

            // Dark/transparent centre until a real selection is active.
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
                        selectedFillPaint

                    isPressed ->
                        pressedFillPaint

                    else ->
                        idleFillPaint
                }
            )

            // The shared control standard reserves accent for an active state.
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
                ringPaint
            )

            super.onDraw(
                canvas
            )
        }
    }

    private class ReportRecordStateView(
        context: Context
    ) : View(context) {

        private val green =
            DevelopUgandaFivemods8Theme.record

        private val ring =
            Paint(
                Paint.ANTI_ALIAS_FLAG
            ).apply {
                style =
                    Paint.Style.STROKE
                strokeWidth =
                    3f
                color =
                    green
                alpha =
                    140
            }

        private var recording =
            false

        fun setRecordingState(
            active: Boolean
        ) {
            recording =
                active

            ring.alpha = if (active) 255 else 140

            invalidate()
        }

        override fun onDraw(
            canvas: Canvas
        ) {
            super.onDraw(
                canvas
            )

            val inset =
                8f

            val radius =
                height *
                    0.42f

            canvas.drawRoundRect(
                inset,
                inset,
                width - inset,
                height - inset,
                radius,
                radius,
                ring
            )

        }
    }

    private inner class GuidesView(
        context: Context
    ) : View(context) {

        private val gridPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.contentScrim(64)
                strokeWidth = dp(1).toFloat()
            }

        private val levelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.accentScrim(204)
                strokeWidth = dp(1).toFloat()
            }

        private val focusPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color =
                    DevelopUgandaFivemods8Theme.accentScrim(204)
                style = Paint.Style.STROKE
                strokeWidth = dp(1).toFloat()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val w = width.toFloat()
            val h = height.toFloat()

            if (w <= 0f || h <= 0f) return

            // V181: guides belong to the actual 9:16 capture frame.
            // PreviewView uses FIT_START, so the capture frame begins at the
            // top of the view and any unused screen area sits underneath it.
            val frameH =
                minOf(
                    h,
                    w * (16f / 9f)
                )

            canvas.drawLine(
                w / 3f,
                0f,
                w / 3f,
                frameH,
                gridPaint
            )

            canvas.drawLine(
                w * 2f / 3f,
                0f,
                w * 2f / 3f,
                frameH,
                gridPaint
            )

            canvas.drawLine(
                0f,
                frameH / 3f,
                w,
                frameH / 3f,
                gridPaint
            )

            canvas.drawLine(
                0f,
                frameH * 2f / 3f,
                w,
                frameH * 2f / 3f,
                gridPaint
            )

            // Thin capture boundary: everything above this line is the exact
            // 9:16 frame that will be exported.
            canvas.drawLine(
                0f,
                frameH - dp(1),
                w,
                frameH - dp(1),
                levelPaint
            )

            val cy = frameH / 2f
            val cx = w / 2f

            canvas.drawLine(
                cx - dp(42),
                cy,
                cx - dp(8),
                cy,
                levelPaint
            )

            canvas.drawLine(
                cx + dp(8),
                cy,
                cx + dp(42),
                cy,
                levelPaint
            )

            canvas.drawLine(
                cx,
                cy - dp(5),
                cx,
                cy + dp(5),
                levelPaint
            )

            val r = dp(20).toFloat()

            canvas.drawRect(
                cx - r,
                cy - r,
                cx + r,
                cy + r,
                focusPaint
            )

            // Signature ORBIT marks around the focus zone.
            val orbitPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color =
                        DevelopUgandaFivemods8Theme.accentScrim(136)
                    style =
                        Paint.Style.STROKE
                    strokeWidth =
                        dp(1).toFloat()
                }

            val orbitRadius =
                dp(42).toFloat()

            canvas.drawArc(
                cx - orbitRadius,
                cy - orbitRadius,
                cx + orbitRadius,
                cy + orbitRadius,
                205f,
                70f,
                false,
                orbitPaint
            )

            canvas.drawArc(
                cx - orbitRadius,
                cy - orbitRadius,
                cx + orbitRadius,
                cy + orbitRadius,
                25f,
                70f,
                false,
                orbitPaint
            )

            // Social title-safe corner marks.
            val insetX =
                w * 0.08f
            val insetY =
                h * 0.08f
            val arm =
                dp(18).toFloat()

            listOf(
                floatArrayOf(
                    insetX,
                    insetY,
                    insetX + arm,
                    insetY,
                    insetX,
                    insetY + arm
                ),
                floatArrayOf(
                    w - insetX,
                    insetY,
                    w - insetX - arm,
                    insetY,
                    w - insetX,
                    insetY + arm
                ),
                floatArrayOf(
                    insetX,
                    h - insetY,
                    insetX + arm,
                    h - insetY,
                    insetX,
                    h - insetY - arm
                ),
                floatArrayOf(
                    w - insetX,
                    h - insetY,
                    w - insetX - arm,
                    h - insetY,
                    w - insetX,
                    h - insetY - arm
                )
            ).forEach { p ->
                canvas.drawLine(
                    p[0], p[1], p[2], p[3],
                    focusPaint
                )
                canvas.drawLine(
                    p[0], p[1], p[4], p[5],
                    focusPaint
                )
            }

            val innerOrbit =
                dp(28).toFloat()
            canvas.drawCircle(
                cx,
                cy,
                innerOrbit,
                focusPaint
            )
        }
    }

    private fun v276SetRecordingControlLock(recordingActive: Boolean) {
        val shouldLock = recordingActive && DevelopUgandaV276RecordingSafety.controlLockEnabled(this)
        fun enable(button: Button, enabled: Boolean) {
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.48f
        }
        if (::lensButton.isInitialized) enable(lensButton, !shouldLock)
        if (::sceneButton.isInitialized) enable(sceneButton, !shouldLock)
        if (::lookButton.isInitialized) enable(lookButton, !shouldLock)
        if (::qualityButton.isInitialized) enable(qualityButton, !shouldLock)
        if (::captureModeButton.isInitialized) enable(captureModeButton, !shouldLock)
        if (::colorButton.isInitialized) enable(colorButton, !shouldLock)
        if (::identityButton.isInitialized) enable(identityButton, !shouldLock)
        if (::viewModeButton.isInitialized) enable(viewModeButton, !shouldLock)
        if (::settingsButton.isInitialized) enable(settingsButton, !shouldLock)
        if (::guidesButton.isInitialized) enable(guidesButton, !shouldLock)
        if (::resetButton.isInitialized) enable(resetButton, !shouldLock)
        if (::cleanModeButton.isInitialized) enable(cleanModeButton, !shouldLock)
        if (::reportPresetButton.isInitialized) enable(reportPresetButton, !shouldLock)
        // recordButton is intentionally not part of the lock: STOP must remain available.
        if (shouldLock) {
            DevelopUgandaV276RecordingSafety.addEvent(this, "CONTROL LOCK • major capture settings protected while REC")
        }
    }

    override fun onDestroy() {
        // Invalidate any queued focus callback before views/camera are torn down.
        coreFocusGeneration += 1L
        uiHandler.removeCallbacksAndMessages(
            null
        )

        if (
            ::autoViewLabeler.isInitialized
        ) {
            try {
                autoViewLabeler.close()
            } catch (_: Exception) {
            }
        }

        try {
            fused.removeLocationUpdates(
                locationCallback
            )
        } catch (_: Exception) {
        }

        stopGnssMonitor()

        try {
            recording?.stop()
        } catch (_: Exception) {
        }

        try {
            overlayEffect?.close()
        } catch (_: Exception) {
        }

        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }

        try {
            v262RemoteServer?.stop()
        } catch (_: Exception) {
        }
        v262RemoteServer = null

        super.onDestroy()
    }
}
