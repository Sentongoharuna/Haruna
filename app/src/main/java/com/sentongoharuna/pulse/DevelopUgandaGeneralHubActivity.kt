package com.sentongoharuna.pulse

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import kotlin.math.roundToInt

/**
 * V280/12 PRO WORKFLOW CONSOLE + FUNCTION DEEPENING.
 *
 * The first page is now deliberately chronological and operational:
 * PREPARE → SHOOT → DIRECT/MONITOR → REVIEW/COLOR → EDIT/DELIVER.
 * V235→V280/11 systems remain preserved; V280/12 connects their real status and routes
 * into one professional production console without overwriting Clean Master media.
 */
class DevelopUgandaGeneralHubActivity : AppCompatActivity() {

    private val ink = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val card = DevelopUgandaFivemods8Theme.surfaceRaised
    private val line = DevelopUgandaFivemods8Theme.outline
    private val gold = DevelopUgandaFivemods8Theme.accent
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val green = DevelopUgandaFivemods8Theme.accent
    private val violet = DevelopUgandaFivemods8Theme.contentDim
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val red = DevelopUgandaFivemods8Theme.record

    private val handler = Handler(Looper.getMainLooper())
    private val cards = mutableListOf<CardRefs>()
    private val sections = mutableListOf<View>()
    private var flowTrack: LinearLayout? = null
    private var lastOpenedCard: View? = null
    private var hasResumedOnce = false
    private val uiFaultMarkers = mutableMapOf<String, TextView>()

    private var v267ReadyView: TextView? = null
    private var v267DeviceView: TextView? = null
    private var v267SetupView: TextView? = null
    private var v267HealthView: TextView? = null
    private var v267ProjectView: TextView? = null
    private var v267AudioView: TextView? = null
    private var v267RecommendationView: TextView? = null
    private var v267CommandShell: View? = null

    private var v269StoryView: TextView? = null
    private var v269AfterShootView: TextView? = null
    private var v269RecentView: TextView? = null
    private var v271ReadyStripView: TextView? = null
    private var v271OutputStripView: TextView? = null
    private var v272SoundStripView: TextView? = null
    private var v273MotionStripView: TextView? = null
    private var v274VaultStripView: TextView? = null
    private var v276SafetyStripView: TextView? = null
    private var v277LightStripView: TextView? = null
    private var v278LiveStatusView: TextView? = null
    private var v278NextBestView: TextView? = null
    private var v278MemoryView: TextView? = null
    private val v278StageChips = mutableMapOf<String, TextView>()
    private var v279HealthView: TextView? = null
    private var v279PriorityView: TextView? = null
    private var v279ContinuityView: TextView? = null
    private var v279SceneView: TextView? = null
    private var v279AssistView: TextView? = null
    private var v279TakeView: TextView? = null
    private val v279StageChips = mutableMapOf<String, TextView>()
    private var v280CoverageView: TextView? = null
    private var v280NextShotView: TextView? = null
    private var v280SessionView: TextView? = null
    private var v280DirectorView: TextView? = null
    private val v280ShotChips = mutableMapOf<Int, TextView>()
    private var v28012RibbonView: TextView? = null
    private var v28012ReadyView: TextView? = null
    private var v28012NextView: TextView? = null
    private var v28012DetailView: TextView? = null
    private var v28013CueView: TextView? = null
    private var v28013ProgressView: TextView? = null
    private var v28018SessionView: TextView? = null
    private var v28018AlertView: TextView? = null
    private var v28019QaView: TextView? = null
    private var v28012PrimaryAction: Button? = null
    private val v28012StageChips = mutableMapOf<String, TextView>()
    private var v28012SelectedStageId: String? = null
    private val v28012ContextButtons = mutableListOf<Button>()
    private var v275ReadyView: TextView? = null
    private var v275JobView: TextView? = null
    private var v275ContextView: TextView? = null
    private var v275NextView: TextView? = null
    private var v275RecentView: TextView? = null
    private val v275StageChips = mutableMapOf<String, TextView>()
    private var batteryMetric: MetricCardRefs? = null
    private var storageMetric: MetricCardRefs? = null
    private var thermalMetric: MetricCardRefs? = null
    private var readinessMetric: MetricCardRefs? = null
    private var takeMetric: MetricCardRefs? = null
    private var clipMetric: MetricCardRefs? = null
    private var coverageMetric: MetricCardRefs? = null
    private var storyMetric: MetricCardRefs? = null

    private data class CardRefs(
        val title: String,
        val shell: LinearLayout,
        val arrow: TextView,
        val accent: View,
        val live: TextView?,
        val liveProvider: (() -> String)?,
        val liveColor: Int,
    )

    private data class MetricCardRefs(
        val shell: LinearLayout,
        val value: DevelopUgandaLiveMetricTextView,
        val bar: DevelopUgandaEasedMetricBar?,
    )

    private val liveTick = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return
            v28012SafeUi("LIVE CARDS TICK") { refreshLiveCards(animated = true) }
            if (v267AutoRefreshEnabled()) v28012SafeUi("V267 COMMAND TICK") { refreshV267CommandCenter(animated = true) }
            v28012SafeUi("V269 STORY TICK") { refreshV269StoryCommand(animated = true) }
            v28012SafeUi("V271 COACH TICK") { refreshV271LiveStrip(animated = true) }
            v28012SafeUi("V272 SOUND TICK") { refreshV272SoundStrip(animated = true) }
            v28012SafeUi("V273 MOTION TICK") { refreshV273MotionStrip(animated = true) }
            v28012SafeUi("V274 VAULT TICK") { refreshV274VaultStrip(animated = true) }
            v28012SafeUi("V276 SAFETY TICK") { refreshV276SafetyStrip(animated = true) }
            v28012SafeUi("V277 LIGHT TICK") { refreshV277LightingStrip(animated = true) }
            v28012SafeUi("V278 WORKFLOW TICK") { refreshV278WorkflowHome(animated = true) }
            v28012SafeUi("V279 SHOOTING TICK") { refreshV279ActiveShooting(animated = true) }
            v28012SafeUi("V280 DIRECTOR TICK") { refreshV280SmartDirector(animated = true) }
            v28012SafeUi("V28012 CONSOLE TICK") { refreshV28012ProWorkflowConsole(animated = true) }
            v28012SafeUi("V275 CONTROL TICK") { refreshV275ControlSurface(animated = true) }
            handler.postDelayed(this, if (v267AutoRefreshEnabled()) 1600L else 3200L)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
    return try {
        super.dispatchTouchEvent(ev)
    } catch (t: Throwable) {
        DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "TOUCH DISPATCH", t)
        Toast.makeText(this, "ACTION BLOCKED • ${t.javaClass.simpleName}", Toast.LENGTH_SHORT).show()
        true
    }
}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DevelopUgandaV28012RuntimeGuard.install(this)
        runCatching { DevelopUgandaV28019FieldReliability.beginSession(this) }
            .onFailure { DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V28019 SESSION START", it) }
        val recoveryNeedsReview = runCatching {
            DevelopUgandaV276RecordingSafety.reconcileInterruptedSession(this)
        }.onFailure {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V276 STARTUP RECONCILE", it)
        }.getOrDefault(false)
        try {
            setContentView(present(buildHome()))
        } catch (t: Throwable) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "GENERAL HUB BUILD", t)
            setContentView(present(buildV28012SafeRecoveryHome(t)))
        }
        handler.postDelayed({
            runCatching { DevelopUgandaV270Guidance.maybeShowFirstUse(this) }
                .onFailure { DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "FIRST USE GUIDE", it) }
        }, 450L)
        if (recoveryNeedsReview) {
            handler.post {
                if (isFinishing || isDestroyed) return@post
                val detail = DevelopUgandaV276RecordingSafety.recoveryDetail(this)
                    ?: "INTERRUPTED TAKE • RECOVERY DETAIL UNKNOWN • OPEN RECOVERY CENTER"
                val recoveryDialog = AlertDialog.Builder(this)
                    .setTitle("RECOVERY CHECK")
                    .setMessage("$detail\n\nRAW FRAGMENTS ARE PRESERVED. NOTHING WAS OVERWRITTEN OR DELETED.")
                    .setNegativeButton("KEEP NOTICE", null)
                    .setPositiveButton("OPEN RECOVERY CENTER") { _, _ ->
                        startActivity(Intent(this, DevelopUgandaV276RecoveryCenterActivity::class.java))
                    }
                    .create()
                DevelopUgandaDialogStyler.show(recoveryDialog)
            }
        }
    }

    private fun present(view: View): View {
        DevelopUgandaBroadcastPresentation.decorate(view, DevelopUgandaModeProfiles.selected(this))
        return DevelopUgandaFivemods12HomeShell.wrap(this, view)
    }

    override fun onResume() {
        super.onResume()
        v28012SafeUi("LIVE CARDS") { refreshLiveCards(animated = false) }
        v28012SafeUi("V267 COMMAND") { refreshV267CommandCenter(animated = false) }
        v28012SafeUi("V269 STORY") { refreshV269StoryCommand(animated = false) }
        v28012SafeUi("V271 COACH") { refreshV271LiveStrip(animated = false) }
        v28012SafeUi("V272 SOUND") { refreshV272SoundStrip(animated = false) }
        v28012SafeUi("V273 MOTION") { refreshV273MotionStrip(animated = false) }
        v28012SafeUi("V274 VAULT") { refreshV274VaultStrip(animated = false) }
        v28012SafeUi("V276 SAFETY") { refreshV276SafetyStrip(animated = false) }
        v28012SafeUi("V277 LIGHT") { refreshV277LightingStrip(animated = false) }
        v28012SafeUi("V278 WORKFLOW") { refreshV278WorkflowHome(animated = false) }
        v28012SafeUi("V279 SHOOTING") { refreshV279ActiveShooting(animated = false) }
        v28012SafeUi("V280 DIRECTOR") { refreshV280SmartDirector(animated = false) }
        v28012SafeUi("V28012 CONSOLE") { refreshV28012ProWorkflowConsole(animated = false) }
        v28012SafeUi("V275 CONTROL") { refreshV275ControlSurface(animated = false) }
        runCatching { DevelopUgandaV28019FieldReliability.markHubResume(this) }
            .onFailure { DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V28019 HUB RESUME", it) }
        DevelopUgandaV28012RuntimeGuard.markReturnedToHub(this)
        handler.removeCallbacks(liveTick)
        if (liveCardDataEnabled()) handler.postDelayed(liveTick, 500L)
        restartFlowMotion()

        if (hasResumedOnce) {
            lastOpenedCard?.let { card ->
                if (workflowMotionEnabled()) {
                    card.animate().cancel()
                    card.scaleX = 1.015f
                    card.scaleY = 1.015f
                    card.alpha = 0.86f
                    card.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity)
                        .start()
                }
            }
        }
        hasResumedOnce = true
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(liveTick)
        DevelopUgandaStatusMotion.stopAll(this)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun v258Prefs() =
        duSharedPreferences("develop_uganda_v258_live_workflow_home", MODE_PRIVATE)

    private fun v257MotionPrefs() =
        duSharedPreferences("develop_uganda_v257_record_core_motion", MODE_PRIVATE)

    private fun v256Prefs() =
        duSharedPreferences("develop_uganda_v256_tactile_clean_cam", MODE_PRIVATE)

    private fun v259Prefs() =
        duSharedPreferences("develop_uganda_v259_smart_shoot_media", MODE_PRIVATE)

    private fun v260Prefs() =
        duSharedPreferences("develop_uganda_v260_project_control_failsafe", MODE_PRIVATE)

    private fun v267Prefs() =
        duSharedPreferences("develop_uganda_v267_live_spec_command_center", MODE_PRIVATE)

    private fun v267LiveSpecEnabled(): Boolean =
        v267Prefs().getBoolean("live_spec_data", true)

    private fun v267AutoRefreshEnabled(): Boolean =
        v267Prefs().getBoolean("auto_refresh", true)

    private fun v267QuickControlsEnabled(): Boolean =
        v267Prefs().getBoolean("quick_controls", true)

    private fun v267CommandMotionEnabled(): Boolean =
        DevelopUgandaMotionPreferences.animationAllowed(this) &&
            v267Prefs().getBoolean("command_motion", true)

    private fun v267AdvancedSpecsEnabled(): Boolean =
        v267Prefs().getBoolean("advanced_specs", true)

    private fun v260ProjectStatus(): String {
        val p = v260Prefs()
        if (!p.getBoolean("project_mode", true)) return "PROJECT OFF"
        val project = p.getString("project_name", "FIELD PROJECT") ?: "FIELD PROJECT"
        val camera = p.getString("camera_name", "CAM A") ?: "CAM A"
        val scene = p.getInt("scene_number", 1).coerceIn(1, 999)
        val take = p.getInt("take_number", 1).coerceIn(1, 999)
        return "%s • %s • S%03d T%03d".format(Locale.US, project, camera, scene, take)
    }

    private fun workflowMotionEnabled(): Boolean =
        DevelopUgandaMotionPreferences.animationAllowed(this)

    private fun workflowMotionStyle(): String = when (DevelopUgandaMotionPreferences.level(this)) {
        DevelopUgandaMotionLevel.FULL -> "NORMAL"
        DevelopUgandaMotionLevel.STANDARD -> "SUBTLE"
        DevelopUgandaMotionLevel.OFF -> "REDUCED"
    }

    private fun liveCardDataEnabled(): Boolean =
        v258Prefs().getBoolean("live_card_data", true)

    private fun buildHome(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ink)
            isFillViewport = true
            clipToPadding = false
        }

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(12), space(12), space(24))
            setBackgroundColor(ink)
        }

        val hero = panelBlock().apply {
            val titleRow = LinearLayout(this@DevelopUgandaGeneralHubActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            titleRow.addView(
                text("develop.uganda • V285 FIELD MASTER", 13f, white, true).apply {
                    tag = "du_page_title"
                    maxLines = 1
                    isSingleLine = true
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            val help = text("ⓘ", 18f, gold, true).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "How to use develop.uganda"
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    DevelopUgandaV270Guidance.showWorkflow(this@DevelopUgandaGeneralHubActivity)
                }
            }
            titleRow.addView(help, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(titleRow)

            val settingsRow = LinearLayout(this@DevelopUgandaGeneralHubActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, space(5), 0, space(4))
            }
            settingsRow.addView(
                v267ActionButton("CAMERA ⚙", cyan) {
                    v267Open(DevelopUgandaV275CameraSettingsActivity::class.java)
                },
                LinearLayout.LayoutParams(0, dp(32), 1f).apply { rightMargin = space(2) }
            )
            settingsRow.addView(
                v267ActionButton("WORKFLOW ⚙", gold) {
                    v267Open(DevelopUgandaV275WorkflowSettingsActivity::class.java)
                },
                LinearLayout.LayoutParams(0, dp(32), 1f).apply { leftMargin = space(2) }
            )
            addView(settingsRow)
            addView(
                text("FIELD RELIABILITY • PRODUCTION QA • LIVE OPERATOR CORE", 12f, gold, true).apply {
                    setPadding(0, space(2), 0, 0)
                }
            )
            addView(
                text(
                    "PREPARE → SHOOT → DIRECT / MONITOR → REVIEW / COLOR → EDIT / DELIVER",
                    9.2f,
                    muted,
                    true
                ).apply { setPadding(0, space(9), 0, 0) }
            )
            DevelopUgandaV28012RuntimeGuard.lastCrashSummary(this@DevelopUgandaGeneralHubActivity)?.let { crash ->
                addView(text(crash, 7.2f, red, true).apply {
                    setPadding(0, space(7), 0, space(2))
                    maxLines = 3
                })
            }
            addView(buildFlowTrack())
            addView(
                text(
                    "V280/19 keeps the working V280/18 operator stack intact and adds field-session diagnostics, route-return tracking and production QA. PREPARE → SHOOT → DIRECT / MONITOR → REVIEW / COLOR → EDIT / DELIVER remains the same protected chronological workflow.",
                    8.7f,
                    muted,
                    false
                ).apply { setPadding(0, space(7), 0, 0) }
            )
        }
        page.addView(hero)
        page.addView(buildV28012ProWorkflowConsole())

        section(page, "1 • PREPARE • PROJECT / HEALTH / AUDIO / CAMERA")
        page.addView(
            routeCard(
                "SMART STORY / FIELD DESK",
                "Define the story first • choose the next required shot • keep Scene/Take, ratings and delivery intent organized",
                gold,
                DevelopUgandaV269SmartStoryDeskActivity::class.java
            ) { DevelopUgandaV269StoryDeskStore.commandSummary(this) }
        )
        page.addView(
            routeCard(
                "CAMERA HEALTH",
                "Run before long or important takes • real CameraX / Camera2 capabilities • storage • battery • thermal checks",
                cyan,
                DevelopUgandaCameraHealthActivity::class.java
            ) { cameraHealthStatus() }
        )
        page.addView(
            routeCard(
                "BRAND + METADATA",
                "Set project-facing identity and metadata before shooting so new clips are organized consistently",
                gold,
                DevelopUgandaBrandMetadataActivity::class.java
            ) { "METADATA • TIMECODE ${timecodeMode()}" }
        )

        section(page, "2 • SHOOT • FRAME / LIGHT / FOCUS / RECORD")
        DevelopUgandaModeProfiles.all().forEach { profile ->
            page.addView(routeCard(profile) { modeRouteStatus(profile) })
        }
        page.addView(
            routeCard(
                "LIGHT / EXPOSURE INTELLIGENCE",
                "V277 preview-derived lighting analysis • face-region exposure guidance • highlight/shadow protection • tap spot meter • flicker-risk guidance. Screen guidance only; Clean Master is not burned with meters.",
                gold,
                DevelopUgandaV277LightingExposureActivity::class.java
            ) { DevelopUgandaV277LightingExposure.lightStatus(this) }
        )

        section(page, "3 • DIRECT / MONITOR • LOCAL / REMOTE / MULTI-CAM / LIVE CUT")
        page.addView(
            routeCard(
                "LOCAL DIRECTOR MONITOR",
                "Open the professional camera with preserved V261 RGB Parade, vectorscope, skin-tone reference, highlight/shadow assist, frame guides, shutter angle and anamorphic desqueeze. Monitor overlays stay screen-only.",
                cyan,
                DevelopUgandaModeProfiles.selected(this).destination
            ) { "V261 MONITOR • RGB / VECTOR / GUIDES • CLEAN MASTER SAFE" }
        )
        page.addView(
            routeCard(
                "REMOTE DIRECTOR • V262",
                "Use a second Android phone on the same Wi-Fi/hotspot for low-bandwidth preview, remote START REC / SAFE STOP, zoom and AF/AE/AWB controls. The camera phone keeps the full-quality master.",
                gold,
                DevelopUgandaV262RemoteDirectorActivity::class.java
            ) { "REMOTE • LAN / HOTSPOT • MASTER STAYS ON CAMERA" }
        )
        page.addView(
            routeCard(
                "MULTI-CAM DIRECTOR • V263",
                "Coordinate CAM A/B/C/D, reuse camera identity / Scene / Take and prepare multiple phone angles for synchronized director operation without claiming hardware genlock.",
                green,
                DevelopUgandaV263MultiCamDirectorActivity::class.java
            ) { "CAM A/B/C/D • MULTI-CAM CONTROL • PROJECT LINKED" }
        )
        page.addView(
            routeCard(
                "LIVE CUT DIRECTOR • V264",
                "Preview→Program decisions, camera matching, shared clock mapping and exportable cut metadata for the preserved multi-camera workflow.",
                violet,
                DevelopUgandaV264LiveCutDirectorActivity::class.java
            ) { "PVW→PGM • LIVE CUT • CUT LIST • LAN WORKFLOW" }
        )

        section(page, "4 • REVIEW / COLOR • RATE / PROXY / PROTECT / GRADE")
        page.addView(
            routeCard(
                "PROXY SYNC / MULTI-CAM REVIEW",
                "Create and transfer review proxies after the master is safe • shared-timeline angle switching • take rating + notes",
                green,
                DevelopUgandaV265ProxySyncReviewActivity::class.java
            ) { "PROXIES • SYNC REVIEW • MASTERS STAY LOCAL" }
        )
        page.addView(
            routeCard(
                "LUTS / COLOR STUDIO",
                "Choose and refine the finishing look after capture • 17³ color master • Uganda scene palettes • original master preserved",
                violet,
                DevelopUgandaColorStudioActivity::class.java
            ) { "17³ LUT ENGINE • ORIGINAL MASTER PRESERVED" }
        )
        page.addView(
            routeCard(
                "MEDIA VAULT / INSTANT REVIEW",
                "V274 preserved • protect masters • BEST/KEEP/RETAKE • frame grab • search/filter • health labels • delivery queue",
                gold,
                DevelopUgandaV274MediaVaultActivity::class.java
            ) { DevelopUgandaV274MediaVaultStore.hubSummary(this) }
        )
        page.addView(
            routeCard(
                "V276 RECOVERY CENTER",
                "REC journal • interrupted-session review • finalize verification • recording event log • original masters untouched",
                red,
                DevelopUgandaV276RecoveryCenterActivity::class.java
            ) { DevelopUgandaV276RecordingSafety.recoverySummary(this).lineSequence().firstOrNull() ?: "RECOVERY READY" }
        )
        page.addView(
            routeCard(
                "STORY PACKAGES / QC",
                "Review saved packages, clip health, metadata, ratings and packaged reporting work before delivery",
                green,
                DevelopUgandaStoryPackagesActivity::class.java
            ) {
                val free = freeStorageGb()
                "REVIEW / QC • FREE ${if (free >= 0L) "${free}GB" else "UNKNOWN"}"
            }
        )

        section(page, "5 • EDIT / DELIVER • ASSEMBLE / QUEUE / SHARE")
        page.addView(
            routeCard(
                "V274 DELIVERY QUEUE • PRESERVED",
                "Choose ORIGINAL / TikTok / Reels / YouTube / News Desk / WhatsApp delivery intent. Original can share now; format-changing exports are marked render-required until created by the editor/export pipeline.",
                violet,
                DevelopUgandaV274MediaVaultActivity::class.java
            ) { "${DevelopUgandaV274MediaVaultStore.selectedPreset(this)} • QUEUE ${DevelopUgandaV274MediaVaultStore.queue(this).size}" }
        )
        page.addView(
            routeCard(
                "EDIT VIDEO",
                "Open the editor after capture/review • assemble and finish the selected material",
                cyan,
                DevelopUgandaEditorActivity::class.java
            ) { "EDITOR • READY" }
        )
        page.addView(
            routeCard(
                "STORY DELIVERY / FIELD DESK",
                "Return to the story desk to confirm shot completion, take notes and delivery intent before sharing",
                gold,
                DevelopUgandaV269SmartStoryDeskActivity::class.java
            ) { "DELIVERY ${DevelopUgandaV269StoryDeskStore.socialPreset(this)} • ${DevelopUgandaV269StoryDeskStore.brandMode(this)}" }
        )

        section(page, "DEEP PRO STATUS + INTELLIGENCE • PRESERVED / CONNECTED")
        page.addView(buildV280SmartDirector())
        page.addView(buildV279ActiveShootingHome())
        page.addView(buildV275ControlSurface())
        page.addView(buildV277LightingStrip())
        page.addView(buildV276SafetyStrip())
        page.addView(text("LIVE SYSTEM STATUS • V271–V276", 8.4f, muted, true).apply { setPadding(space(3), space(4), 0, space(6)) })
        page.addView(buildV271LiveStrip())
        page.addView(buildV272SoundStrip())
        page.addView(buildV273MotionStrip())
        page.addView(buildV274VaultStrip())
        page.addView(buildV270GuidedWorkflow())
        page.addView(buildV267CommandCenter())
        page.addView(buildV269StoryCommand())

        section(page, "SPECIALIZED PRO CAMERAS • USE WHEN NEEDED")
        page.addView(routeCard("PEOPLE / PORTRAIT FOCUS", "V205 • people, portraits and interviews • AF emphasis", cyan, DevelopUgandaFocusAssistCameraActivity::class.java) { "AF EMPHASIS • PEOPLE" })
        page.addView(routeCard("SUBJECT METERING", "V206 • backlight, windows and mixed-light metering", gold, DevelopUgandaMeteringLockCameraActivity::class.java) { "AE / AWB • SUBJECT" })
        page.addView(routeCard("BUILDINGS / LEVEL", "V207 • architecture, rooms, level and horizon work", green, DevelopUgandaHorizonCameraActivity::class.java) { "LEVEL / HORIZON • READY" })
        page.addView(routeCard("WALK / ACTION STEADY", "V208 • movement, vehicles and stabilization-aware shooting", green, DevelopUgandaSteadyShotCameraActivity::class.java) { "STABILIZATION PATH • READY" })
        page.addView(routeCard("NIGHT / LOW LIGHT", "V209 • dark interiors and night streets • lux-aware guidance", violet, DevelopUgandaNightIntelligenceCameraActivity::class.java) { "LOW LIGHT • LUX AWARE" })
        page.addView(routeCard(DevelopUgandaModeProfiles.interview) { modeRouteStatus(DevelopUgandaModeProfiles.interview) })
        page.addView(routeCard("VERIFIED REPORT", "V212 • site reports / incidents / telemetry / integrity workflow", cyan, DevelopUgandaVerifiedCameraActivity::class.java) { "REPORT / TELEMETRY • READY" })
        page.addView(routeCard("LONG RECORD / THERMAL SAFE", "V213 • long takes and hot-condition protection", red, DevelopUgandaThermalSafeCameraActivity::class.java) { thermalStatusLabel() })
        page.addView(routeCard("CINEMATIC LOOKS", "V214 • cinematic people / travel / creative shooting", violet, DevelopUgandaModeSignatureCameraActivity::class.java) { "CINEMA LOOK PATH • READY" })
        page.addView(routeCard("SMART AUTO", "V215 • automatic guidance when you do not want to choose every mode manually", cyan, DevelopUgandaAutoDirectorCameraActivity::class.java) { "AUTO DIRECTOR • READY" })

        section(page, "PHOTO CAMERAS")
        page.addView(routeCard("PHOTO PRO", "Professional still-photo path", cyan, DevelopUgandaPhotoProCameraActivity::class.java) { "STILL • PRO" })
        page.addView(routeCard("BUILDING PHOTO", "Architecture and building stills", green, DevelopUgandaBuildingPhotoCameraActivity::class.java) { "STILL • ARCHITECTURE" })
        page.addView(routeCard("PEOPLE PHOTO", "Portrait and people stills", gold, DevelopUgandaPeoplePhotoCameraActivity::class.java) { "STILL • PORTRAIT" })
        page.addView(routeCard("NIGHT PHOTO", "Low-light still photography", violet, DevelopUgandaNightPhotoCameraActivity::class.java) { "STILL • LOW LIGHT" })
        page.addView(routeCard("VERIFIED PHOTO", "Evidence / site / documented still-photo path", cyan, DevelopUgandaVerifiedPhotoCameraActivity::class.java) { "STILL • VERIFIED" })

        section(page, "ADVANCED + COMPLETE DECK")
        page.addView(
            routeCard(
                "FULL DECK / ALL WORK",
                "Open the original Newsroom home with the complete history of modules. Use this after the guided core workflow when you need older/specialist tools.",
                gold,
                DevelopUgandaNewsroomActivity::class.java
            ) { DevelopUgandaV270Guidance.auditRoutes(this).shortLabel() }
        )

        section(page, "SYSTEM PRINCIPLE")
        page.addView(
            infoCard(
                "FUNCTION FIRST • NO FAKE BUTTONS",
                "V280/12 is cumulative on the V280/11 preservation master. All V235→V280 systems remain; the new Pro Workflow Console connects them into PREPARE → SHOOT → DIRECT/MONITOR → REVIEW/COLOR → EDIT/DELIVER. It reads real saved/device state, opens verified existing functions, keeps Remote/Multi-Cam/Live Cut/Proxy/Story/Safety/Audio/Lighting/Smart Director intact and never overwrites the original Clean Master.",
                green
            )
        )

        scroll.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        DevelopUgandaFivemods8Theme.enforceTouchTargets(scroll)
        scroll.post { animateEntrance(hero) }
        return scroll
    }

    private inline fun v28012SafeUi(area: String, block: () -> Unit) {
        try {
            block()
            uiFaultMarkers[faultAreaKey(area)]?.visibility = View.GONE
        } catch (t: Throwable) {
            val fault = DevelopUgandaV28012RuntimeGuard.recordUiFault(this, area, t)
            showUiFault(fault)
        }
    }

    private fun faultAreaKey(area: String): String = area.removeSuffix(" TICK")

    private fun showUiFault(fault: DevelopUgandaV28012RuntimeGuard.UiFault) {
        val key = faultAreaKey(fault.area)
        val marker = uiFaultMarkers[key] ?: faultAnchor(key)?.let { anchor ->
            val parent = anchor.parent as? ViewGroup ?: return@let null
            text("⚠ $key • DATA UNKNOWN", 8f, muted, true).apply {
                alpha = 0.72f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(space(8), space(8), space(8), space(8))
                minHeight = dp(48)
                isClickable = true
                isFocusable = true
                setOnClickListener { showFaultDialog(tag as? DevelopUgandaV28012RuntimeGuard.UiFault) }
                val markerIndex = (parent.indexOfChild(anchor) + 1).coerceIn(0, parent.childCount)
                parent.addView(this, markerIndex)
                uiFaultMarkers[key] = this
            }
        }
        if (marker != null) {
            marker.tag = fault
            marker.text = "⚠ $key • ${fault.exception.substringAfterLast('.')}"
            marker.visibility = View.VISIBLE
        } else if (fault.area.startsWith("BUTTON") || fault.area.startsWith("CONTEXT")) {
            Toast.makeText(this, "ACTION FAILED • ${fault.exception.substringAfterLast('.')}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun faultAnchor(area: String): View? = when (area) {
        "LIVE CARDS" -> cards.firstOrNull()?.shell
        "V267 COMMAND" -> v267CommandShell
        "V269 STORY" -> v269StoryView
        "V271 COACH" -> v271ReadyStripView
        "V272 SOUND" -> v272SoundStripView
        "V273 MOTION" -> v273MotionStripView
        "V274 VAULT" -> v274VaultStripView
        "V275 CONTROL" -> v275ReadyView
        "V276 SAFETY" -> v276SafetyStripView
        "V277 LIGHT" -> v277LightStripView
        "V278 WORKFLOW" -> v278LiveStatusView
        "V279 SHOOTING" -> v279HealthView
        "V280 DIRECTOR" -> v280CoverageView
        "V28012 CONSOLE" -> v28012RibbonView
        else -> null
    }

    private fun showFaultDialog(fault: DevelopUgandaV28012RuntimeGuard.UiFault?) {
        val value = fault ?: DevelopUgandaV28012RuntimeGuard.latestUiFault(this)
        val message = value?.let {
            "AREA: ${it.area}\nEXCEPTION: ${it.exception}\nMESSAGE: ${it.message}\nTIMESTAMP: ${it.timestampMs}"
        } ?: "No recorded UI fault is available."
        AlertDialog.Builder(this)
            .setTitle("Panel data unavailable")
            .setMessage(message)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun showFullCrashReport() {
        val report = text(
            DevelopUgandaV28012RuntimeGuard.fullCrashReport(this),
            11f,
            white,
            false,
        ).apply {
            typeface = Typeface.MONOSPACE
            setPadding(space(16), space(16), space(16), space(16))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ink)
            addView(report)
        }
        AlertDialog.Builder(this)
            .setTitle("Runtime report")
            .setView(scroll)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    private fun buildV28012SafeRecoveryHome(error: Throwable): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(ink) }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(18), space(28), space(18), space(28))
        }
        page.addView(text("develop.uganda • SAFE HOME", 19f, white, true))
        page.addView(text("A first-page module failed, but the app stayed open instead of closing.", 10f, gold, true).apply { setPadding(0, space(8), 0, space(6)) })
        page.addView(text("${error.javaClass.simpleName} • ${(error.message ?: "no message").take(220)}", 8f, red, false))
        val selectedMode = DevelopUgandaModeProfiles.selected(this)
        page.addView(
            v267ActionButton("OPEN LAST MODE • ${selectedMode.shortName}", selectedMode.chromeAccentColor) {
                v267OpenSelectedMode()
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, space(50)).apply { topMargin = space(14) },
        )
        val otherModesScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val otherModes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        DevelopUgandaModeProfiles.all().filterNot { it.page == selectedMode.page }.forEachIndexed { index, profile ->
            otherModes.addView(
                v267ActionButton(profile.shortName, profile.chromeAccentColor) {
                    DevelopUgandaModeProfiles.rememberSelected(this, profile.page)
                    v267Open(profile.destination)
                },
                LinearLayout.LayoutParams(dp(112), dp(48)).apply {
                    if (index > 0) leftMargin = space(8)
                },
            )
        }
        otherModesScroll.addView(otherModes)
        page.addView(otherModesScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, space(56)).apply { topMargin = space(8) })
        page.addView(v267ActionButton("RETRY FULL HOME", gold) {
    runCatching { buildHome() }
        .onSuccess { setContentView(present(it)) }
        .onFailure {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this@DevelopUgandaGeneralHubActivity, "SAFE HOME RETRY", it)
            setContentView(present(buildV28012SafeRecoveryHome(it)))
        }
}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, space(50)).apply { topMargin = space(8) })

        page.addView(v267ActionButton("SHOW LAST CRASH REPORT", violet) {
            showFullCrashReport()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, space(50)).apply { topMargin = space(8) })
        scroll.addView(page)
        DevelopUgandaFivemods8Theme.enforceTouchTargets(scroll)
        return scroll
    }

    private fun buildV28012ProWorkflowConsole(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 24, 1)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(
            text("V280/19 • FIELD RELIABILITY + PRODUCTION QA", 14.5f, white, true),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleRow.addView(text("STABLE", 7.2f, gold, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(titleRow)

        v28012RibbonView = text("PROJECT • CHECKING LIVE STATUS…", 7.9f, cyan, true).apply {
            setPadding(0, space(7), 0, space(3))
            maxLines = 2
        }
        shell.addView(v28012RibbonView)
        v28018SessionView = text("LIVE SESSION • CHECKING…", 7.6f, cyan, true).apply {
            setPadding(0, space(2), 0, space(2))
            maxLines = 2
        }
        shell.addView(v28018SessionView)
        v28018AlertView = text("OPERATOR PRIORITY • CHECKING…", 7.7f, gold, true).apply {
            setPadding(0, space(2), 0, space(3))
            maxLines = 3
        }
        shell.addView(v28018AlertView)
        v28019QaView = text("FIELD QA • CHECKING SESSION…", 7.5f, green, true).apply {
            setPadding(0, space(2), 0, space(3))
            maxLines = 2
        }
        shell.addView(v28019QaView)
        v28012ReadyView = text("READY • CHECKING…", 11.5f, green, true).apply { setPadding(0, space(3), 0, space(2)) }
        shell.addView(v28012ReadyView)
        v28012NextView = text("NEXT ACTION • CHECKING…", 10.0f, gold, true).apply { setPadding(0, space(2), 0, space(2)) }
        shell.addView(v28012NextView)
        v28012DetailView = text("AUDIO • LIGHT • SAFETY • STORY • DIRECTOR • DELIVERY", 7.8f, muted, false).apply {
            setPadding(0, space(2), 0, space(8))
            maxLines = 3
        }
        shell.addView(v28012DetailView)

        val metricScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val metricRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun addMetric(label: String, accent: Int, bounded: Boolean): MetricCardRefs {
            val refs = liveMetricCard(label, accent, bounded)
            metricRow.addView(
                refs.shell,
                LinearLayout.LayoutParams(space(136), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = space(8)
                },
            )
            return refs
        }
        batteryMetric = addMetric("BATTERY", green, true)
        storageMetric = addMetric("FREE STORAGE", cyan, false)
        thermalMetric = addMetric("THERMAL", red, true)
        readinessMetric = addMetric("READINESS", gold, true)
        takeMetric = addMetric("TAKE", violet, false)
        clipMetric = addMetric("CLIPS", green, false)
        coverageMetric = addMetric("COVERAGE", cyan, true)
        storyMetric = addMetric("STORY", gold, true)
        metricScroll.addView(metricRow)
        shell.addView(
            metricScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = space(8)
            },
        )
        v28013CueView = text("OPERATOR CUE • CHECKING…", 8.0f, cyan, true).apply {
            setPadding(0, space(3), 0, space(2))
            maxLines = 3
        }
        shell.addView(v28013CueView)
        v28013ProgressView = text("○ PREP  ○ SHOOT  ○ DIRECT  ○ REVIEW  ○ DELIVER", 7.2f, muted, true).apply {
            setPadding(0, space(2), 0, space(7))
            maxLines = 2
        }
        shell.addView(v28013ProgressView)

        val stageScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val stageRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        DevelopUgandaV28012ProWorkflowConsole.modeStages(this).forEachIndexed { index, stage ->
            val availability = if (stage.availability == DevelopUgandaStageAvailability.ABSENT) "ABSENT" else "PENDING"
            val chip = text("${index + 1} ${stage.label}\n$availability", 7.7f, white, true).apply {
                gravity = Gravity.CENTER
                setPadding(space(9), space(7), space(9), space(7))
                minWidth = dp(130)
                minHeight = dp(58)
                background = rounded(card, line, 14, 1)
                isClickable = true
                isFocusable = true
                contentDescription = "${stage.label}. $availability. ${stage.nextAction}"
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (stage.availability == DevelopUgandaStageAvailability.ABSENT) {
                        Toast.makeText(this@DevelopUgandaGeneralHubActivity, "${stage.label} • PROFILE DATA ABSENT", Toast.LENGTH_SHORT).show()
                    } else {
                        v28012SelectedStageId = stage.id
                        DevelopUgandaV28012ProWorkflowConsole.selectStage(
                            this@DevelopUgandaGeneralHubActivity,
                            DevelopUgandaV28012ProWorkflowConsole.shellStage(stage),
                        )
                        refreshV28012ProWorkflowConsole(animated = true)
                    }
                }
            }
            v28012StageChips[stage.id] = chip
            stageRow.addView(chip, LinearLayout.LayoutParams(space(138), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = space(5) })
        }
        stageScroll.addView(stageRow)
        shell.addView(stageScroll)

        v28012PrimaryAction = v267ActionButton("OPEN READY CHECK", gold) { v28012OpenSelectedStage() }.apply {
            tag = "du_primary_action"
        }
        shell.addView(v28012PrimaryAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, space(48)).apply { topMargin = space(8) })

        val contextLabel = text("CONTEXT CONTROLS • THE BUTTONS BELOW FOLLOW THE SELECTED STAGE", 7.0f, muted, true).apply {
            setPadding(0, space(8), 0, space(5))
        }
        shell.addView(contextLabel)
        val contextRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        repeat(4) { index ->
            val button = v267ActionButton("READY", if (index == 0) cyan else if (index == 1) gold else if (index == 2) green else violet) { }
            v28012ContextButtons += button
            contextRow.addView(button, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                leftMargin = if (index == 0) 0 else space(2)
                rightMargin = if (index == 3) 0 else space(2)
            })
        }
        shell.addView(contextRow)

        val autoRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        autoRow.addView(
            v267ActionButton("AUTO NEXT", green) {
                v28012SelectedStageId = null
                DevelopUgandaV28012ProWorkflowConsole.clearManualStage(this@DevelopUgandaGeneralHubActivity)
                refreshV28012ProWorkflowConsole(animated = true)
            },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = space(2) }
        )
        autoRow.addView(
            v267ActionButton("MASTER CHECK", cyan) {
                Toast.makeText(this@DevelopUgandaGeneralHubActivity, DevelopUgandaV28018LiveOperatorIntelligence.masterSummary(DevelopUgandaV28012ProWorkflowConsole.snapshot(this@DevelopUgandaGeneralHubActivity)), Toast.LENGTH_LONG).show()
            },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = space(2); rightMargin = space(2) }
        )
        autoRow.addView(
            v267ActionButton("FIELD QA", gold) {
                DevelopUgandaV28012SafeActions.run(this@DevelopUgandaGeneralHubActivity, "V28019 FIELD QA") {
                    val snap = DevelopUgandaV28012ProWorkflowConsole.snapshot(this@DevelopUgandaGeneralHubActivity)
                    Toast.makeText(
                        this@DevelopUgandaGeneralHubActivity,
                        DevelopUgandaV28019FieldReliability.fullReport(this@DevelopUgandaGeneralHubActivity, snap).take(3500),
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = space(2) }
        )
        shell.addView(autoRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = space(6) })

        shell.addView(text("V280/19 reliability is read-only supervision • Clean Master, CameraX recording, V276 safety, V272 audio continuity, LUTS/rulers, V262–V265 Director stack, V280/18 intelligence and FIX3 navigation guards remain authoritative.", 6.9f, muted, false).apply {
            setPadding(0, space(7), 0, 0)
        })
        v28012SafeUi("V28012 CONSOLE") { refreshV28012ProWorkflowConsole(animated = false) }
        return shell
    }

    private fun refreshV28012ProWorkflowConsole(animated: Boolean) {
        if (v28012RibbonView == null) return
        val snap = runCatching { DevelopUgandaV28012ProWorkflowConsole.snapshot(this) }.getOrElse {
            v267SetLiveText(v28012RibbonView, "PROJECT • SNAPSHOT FAILED • TAP FAULT MARKER", false)
            v267SetLiveText(v28012ReadyView, "READINESS UNKNOWN • LIVE STATE UNAVAILABLE", false)
            hideConsoleMetrics()
            throw it
        }
        val active = DevelopUgandaV28012ProWorkflowConsole.activeStage(this, snap)
        val auto = DevelopUgandaV28012ProWorkflowConsole.autoStage(snap)
        val lively = animated && workflowMotionEnabled()
        refreshConsoleMetrics(snap, lively)

        v267SetLiveText(v28012RibbonView, DevelopUgandaV28012ProWorkflowConsole.statusRibbon(snap), lively)
        v267SetLiveText(v28018SessionView, "LIVE SESSION • ${DevelopUgandaV28018LiveOperatorIntelligence.sessionBar(snap)}", lively)
        v267SetLiveText(v28018AlertView, "${DevelopUgandaV28018LiveOperatorIntelligence.operatorPriority(snap)} • ${DevelopUgandaV28018LiveOperatorIntelligence.alert(snap)}", lively)
        val qa = DevelopUgandaV28019FieldReliability.report(this, snap)
        v267SetLiveText(v28019QaView, "FIELD QA • ${qa.summary} • SESSION ${qa.uptime}", lively)
        v28019QaView?.setTextColor(when { qa.score >= 90 -> green; qa.score >= 70 -> gold; else -> red })
        v267SetLiveText(v28012ReadyView, "${DevelopUgandaV28013StableMasterRefinement.productionState(snap)} • ${DevelopUgandaV28012ProWorkflowConsole.readinessHeadline(snap)}", lively)
        v267SetLiveText(v28012NextView, "NEXT ACTION • ${DevelopUgandaV28012ProWorkflowConsole.nextAction(snap)}", lively)
        v267SetLiveText(v28012DetailView, DevelopUgandaV28012ProWorkflowConsole.nextActionDetail(snap), lively)
        v267SetLiveText(v28013CueView, "OPERATOR CUE • ${DevelopUgandaV28013StableMasterRefinement.operatorCue(snap)}", lively)
        v267SetLiveText(v28013ProgressView, "FLOW • ${DevelopUgandaV28013StableMasterRefinement.progressLine(this, snap)} • ${DevelopUgandaV28013StableMasterRefinement.operatorReason(snap)}", lively)
        v28012ReadyView?.setTextColor(when {
            snap.recoveryNeeded || snap.readiness < 55 -> red
            snap.readiness < 75 -> gold
            else -> green
        })

        val modeStages = DevelopUgandaV28012ProWorkflowConsole.modeStages(this)
        val activeModeStage = v28012SelectedStageId?.let { id -> modeStages.firstOrNull { it.id == id } }
            ?: DevelopUgandaModeProfiles.stageForDetailed(this, DevelopUgandaV28012ProWorkflowConsole.stageLabel(active))
        modeStages.forEachIndexed { index, stage ->
            val shellStage = DevelopUgandaV28012ProWorkflowConsole.shellStage(stage)
            val state = if (stage.availability == DevelopUgandaStageAvailability.ABSENT) {
                "ABSENT"
            } else {
                DevelopUgandaV28012ProWorkflowConsole.stageState(this, snap, shellStage)
            }
            val isActive = stage.id == activeModeStage?.id
            v28012StageChips[stage.id]?.apply {
                text = "${index + 1} ${stage.label}\n$state • ${DevelopUgandaV28012ProWorkflowConsole.stageSummary(snap, shellStage)}"
                setTextColor(if (isActive) white else muted)
                background = rounded(if (isActive) DevelopUgandaFivemods8Theme.surface else card, if (isActive) DevelopUgandaModeProfiles.selected(this@DevelopUgandaGeneralHubActivity).chromeAccentColor else line, 14, if (isActive) 2 else 1)
                alpha = if (stage.availability == DevelopUgandaStageAvailability.ABSENT) 0.52f else if (isActive) 1f else 0.88f
            }
        }

        val source = if (active == auto) "NEXT" else "SELECTED"
        v28012PrimaryAction?.text = "${activeModeStage?.nextAction ?: "OPEN ${DevelopUgandaV28012ProWorkflowConsole.stageLabel(active)}"} • $source"
        configureV28012ContextControls(active)
    }

    private fun configureV28012ContextControls(stage: Int) {
        fun set(index: Int, label: String, action: () -> Unit) {
            val button = v28012ContextButtons.getOrNull(index) ?: return
            button.text = label
            button.setOnClickListener {
                v28012SafeUi("CONTEXT BUTTON • $label") { action() }
            }
        }
        when (stage) {
            DevelopUgandaV28012ProWorkflowConsole.STAGE_PREPARE -> {
                set(0, "HEALTH") { v267Open(DevelopUgandaCameraHealthActivity::class.java) }
                set(1, "CAM SET") { v267Open(DevelopUgandaV275CameraSettingsActivity::class.java) }
                set(2, "FLOW SET") { v267Open(DevelopUgandaV275WorkflowSettingsActivity::class.java) }
                set(3, "RECOVERY") { v267Open(DevelopUgandaV276RecoveryCenterActivity::class.java) }
            }
            DevelopUgandaV28012ProWorkflowConsole.STAGE_SHOOT -> {
                set(0, "${DevelopUgandaModeProfiles.selected(this).shortName} CAM") { v267OpenSelectedMode() }
                set(1, "STORY NEXT") { v267Open(DevelopUgandaV269SmartStoryDeskActivity::class.java) }
                set(2, "LIGHT") { v267Open(DevelopUgandaV277LightingExposureActivity::class.java) }
                set(3, "AUDIO") { v267Open(DevelopUgandaAudioGuardCameraActivity::class.java) }
            }
            DevelopUgandaV28012ProWorkflowConsole.STAGE_DIRECT -> {
                set(0, "LOCAL") { v267OpenSelectedMode() }
                set(1, "REMOTE") { v267Open(DevelopUgandaV262RemoteDirectorActivity::class.java) }
                set(2, "MULTI-CAM") { v267Open(DevelopUgandaV263MultiCamDirectorActivity::class.java) }
                set(3, "LIVE CUT") { v267Open(DevelopUgandaV264LiveCutDirectorActivity::class.java) }
            }
            DevelopUgandaV28012ProWorkflowConsole.STAGE_REVIEW -> {
                set(0, "LAST TAKE") { v267Open(DevelopUgandaV274MediaVaultActivity::class.java) }
                set(1, "PROXY") { v267Open(DevelopUgandaV265ProxySyncReviewActivity::class.java) }
                set(2, "COLOR") { v267Open(DevelopUgandaColorStudioActivity::class.java) }
                set(3, "QC") { v267Open(DevelopUgandaStoryPackagesActivity::class.java) }
            }
            else -> {
                set(0, "EDIT") { v267Open(DevelopUgandaEditorActivity::class.java) }
                set(1, "STORY") { v267Open(DevelopUgandaV269SmartStoryDeskActivity::class.java) }
                set(2, "QUEUE") { v267Open(DevelopUgandaV274MediaVaultActivity::class.java) }
                set(3, "PACKAGES") { v267Open(DevelopUgandaStoryPackagesActivity::class.java) }
            }
        }
    }

    private fun v28012OpenSelectedStage() {
        val snap = DevelopUgandaV28012ProWorkflowConsole.snapshot(this)
        val modeStage = v28012SelectedStageId?.let { id ->
            DevelopUgandaV28012ProWorkflowConsole.modeStages(this).firstOrNull { it.id == id }
        } ?: DevelopUgandaModeProfiles.stageForDetailed(
            this,
            DevelopUgandaV28012ProWorkflowConsole.stageLabel(
                DevelopUgandaV28012ProWorkflowConsole.activeStage(this, snap),
            ),
        )
        if (snap.recoveryNeeded) {
            v267Open(DevelopUgandaV276RecoveryCenterActivity::class.java)
            return
        }
        if (modeStage != null && modeStage.availability == DevelopUgandaStageAvailability.PRESENT) {
            v275OpenStage(modeStage.id)
            return
        }
        when (DevelopUgandaV28012ProWorkflowConsole.activeStage(this, snap)) {
            DevelopUgandaV28012ProWorkflowConsole.STAGE_PREPARE -> {
                if (snap.recoveryNeeded) v267Open(DevelopUgandaV276RecoveryCenterActivity::class.java)
                else v267Open(DevelopUgandaCameraHealthActivity::class.java)
            }
            DevelopUgandaV28012ProWorkflowConsole.STAGE_SHOOT -> v267OpenSelectedMode()
            DevelopUgandaV28012ProWorkflowConsole.STAGE_DIRECT -> v267Open(DevelopUgandaV264LiveCutDirectorActivity::class.java)
            DevelopUgandaV28012ProWorkflowConsole.STAGE_REVIEW -> v267Open(DevelopUgandaV274MediaVaultActivity::class.java)
            else -> v267Open(DevelopUgandaEditorActivity::class.java)
        }
    }

    private fun buildV280SmartDirector(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 24, 1)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text("V280 • SMART DIRECTOR MODE", 13.8f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(text("DIRECT", 7.2f, gold, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(titleRow)

        v280DirectorView = text("DIRECTOR • CHECKING PLAN…", 8.4f, gold, true).apply { setPadding(0, space(7), 0, space(3)) }
        shell.addView(v280DirectorView)
        v280CoverageView = text("COVERAGE • CHECKING…", 9.0f, green, true).apply { setPadding(0, space(2), 0, space(3)) }
        shell.addView(v280CoverageView)
        v280NextShotView = text("NEXT SHOT • CHECKING…", 8.6f, cyan, true).apply { setPadding(0, space(2), 0, space(3)) }
        shell.addView(v280NextShotView)
        v280SessionView = text("SESSION • CHECKING…", 7.6f, muted, true).apply { setPadding(0, space(2), 0, space(6)) }
        shell.addView(v280SessionView)

        val shotScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val shotRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        DevelopUgandaV280SmartDirector.plan(this).forEachIndexed { index, shot ->
            val chip = text("${index + 1} $shot", 7.0f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(space(10), space(7), space(10), space(7))
                background = rounded(DevelopUgandaFivemods8Theme.surface, line, 13, 1)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    Toast.makeText(this@DevelopUgandaGeneralHubActivity, DevelopUgandaV280SmartDirector.setTarget(this@DevelopUgandaGeneralHubActivity, index), Toast.LENGTH_SHORT).show()
                    refreshV280SmartDirector(animated = true)
                }
            }
            v280ShotChips[index] = chip
            shotRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                if (index > 0) leftMargin = space(4)
            })
        }
        shotScroll.addView(shotRow)
        shell.addView(shotScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(v267ActionButton("MODE", gold) {
            Toast.makeText(this, DevelopUgandaV280SmartDirector.cycleDirectorPreset(this), Toast.LENGTH_SHORT).show()
            recreate()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = space(2) })
        actions.addView(v267ActionButton("SHOT DONE", green) {
            Toast.makeText(this, DevelopUgandaV280SmartDirector.markShotDone(this), Toast.LENGTH_SHORT).show()
            refreshV280SmartDirector(animated = true)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        actions.addView(v267ActionButton("BEST", violet) {
            Toast.makeText(this, DevelopUgandaV279ActiveShooting.markBestTake(this, workflowConfidenceScore()), Toast.LENGTH_SHORT).show()
            refreshV279ActiveShooting(animated = true)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        actions.addView(v267ActionButton("${DevelopUgandaModeProfiles.selected(this).shortName} CAM", cyan) {
            v267OpenSelectedMode()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2) })
        shell.addView(actions)

        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, space(5), 0, 0) }
        nav.addView(v267ActionButton("◀ PREV", muted) {
            Toast.makeText(this, DevelopUgandaV280SmartDirector.setPreviousTarget(this), Toast.LENGTH_SHORT).show()
            refreshV280SmartDirector(animated = true)
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = space(2) })
        nav.addView(v267ActionButton("NEXT ▶", muted) {
            Toast.makeText(this, DevelopUgandaV280SmartDirector.setNextTarget(this), Toast.LENGTH_SHORT).show()
            refreshV280SmartDirector(animated = true)
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        nav.addView(v267ActionButton("REVIEW", green) {
            v267Open(DevelopUgandaV274MediaVaultActivity::class.java)
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        nav.addView(v267ActionButton("RESET", red) {
            Toast.makeText(this, DevelopUgandaV280SmartDirector.resetSession(this), Toast.LENGTH_SHORT).show()
            refreshV280SmartDirector(animated = true)
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = space(2) })
        shell.addView(nav)

        shell.addView(text("MODE cycles GENERAL → NEWS → TIKTOK → PROPERTY → CONSTRUCTION → INTERVIEW → CINEMA • new Media Vault clips can advance one planned shot automatically • SHOT DONE is the manual fallback", 6.9f, muted, false).apply {
            setPadding(0, space(7), 0, 0)
        })
        v28012SafeUi("V280 DIRECTOR") { refreshV280SmartDirector(animated = false) }
        return shell
    }

    private fun refreshV280SmartDirector(animated: Boolean) {
        val autoAdvance = DevelopUgandaV280SmartDirector.reconcileNewClip(this)
        if (autoAdvance != null && animated) Toast.makeText(this, autoAdvance, Toast.LENGTH_SHORT).show()
        val lively = workflowMotionEnabled()
        val directorPreset = DevelopUgandaV280SmartDirector.directorPreset(this)
        val target = DevelopUgandaV280SmartDirector.currentShot(this)
        v267SetLiveText(v280DirectorView, "DIRECTOR PRESET • $directorPreset • TARGET $target", animated && lively)
        v267SetLiveText(v280CoverageView, DevelopUgandaV280SmartDirector.coverageStatus(this), animated && lively)
        v267SetLiveText(v280NextShotView, DevelopUgandaV280SmartDirector.nextShotGuidance(this), animated && lively)
        v267SetLiveText(v280SessionView, DevelopUgandaV280SmartDirector.sessionStatus(this), animated && lively)

        val active = DevelopUgandaV280SmartDirector.targetIndex(this)
        v280ShotChips.forEach { (index, chip) ->
            val isActive = index == active
            chip.setTextColor(if (isActive) ink else muted)
            chip.background = rounded(if (isActive) gold else DevelopUgandaFivemods8Theme.surface, if (isActive) DevelopUgandaFivemods8Theme.content else line, 13, 1)
            chip.elevation = 0f
            if (isActive && animated && lively) {
                chip.animate().cancel()
                chip.scaleX = 0.97f
                chip.scaleY = 0.97f
                chip.animate().scaleX(1f).scaleY(1f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
            }
        }
    }

    private fun buildV279ActiveShootingHome(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 24, 1)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text("V279 • ACTIVE SHOOTING INTELLIGENCE", 13.8f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(text("ACTIVE", 7.2f, green, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(titleRow)

        v279HealthView = text("RECORDING HEALTH • CHECKING…", 9.1f, green, true).apply { setPadding(0, space(7), 0, space(4)) }
        shell.addView(v279HealthView)

        val stageScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val stageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        DevelopUgandaV279ActiveShooting.stages(this).forEachIndexed { index, stage ->
            val availability = if (stage.availability == DevelopUgandaStageAvailability.ABSENT) "ABSENT" else "PENDING"
            val chip = text("${index + 1} ${stage.label}\n$availability", 7.0f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(space(10), space(7), space(10), space(7))
                background = rounded(DevelopUgandaFivemods8Theme.surface, line, 13, 1)
                isClickable = true
                isFocusable = true
                contentDescription = "${stage.label}. $availability. ${stage.nextAction}"
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (stage.availability == DevelopUgandaStageAvailability.ABSENT) {
                        Toast.makeText(this@DevelopUgandaGeneralHubActivity, "${stage.label} • PROFILE DATA ABSENT", Toast.LENGTH_SHORT).show()
                    } else {
                        v279OpenStage(stage.id)
                    }
                }
            }
            v279StageChips[stage.id] = chip
            stageRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                if (index > 0) leftMargin = space(4)
            })
        }
        stageScroll.addView(stageRow)
        shell.addView(stageScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        v279PriorityView = text("NEXT BEST ACTION • CHECKING…", 8.8f, gold, true).apply {
            setPadding(0, space(7), 0, space(4))
            isClickable = true
            isFocusable = true
            setOnClickListener { v279OpenStage(DevelopUgandaV279ActiveShooting.stageFromDetailed(v275CurrentStage())) }
        }
        shell.addView(v279PriorityView)

        v279ContinuityView = text("CONTINUITY • CHECKING…", 7.9f, violet, true).apply { setPadding(0, space(2), 0, space(2)) }
        shell.addView(v279ContinuityView)
        v279SceneView = text("SCENE WATCH • CHECKING…", 7.8f, cyan, true).apply { setPadding(0, space(2), 0, space(2)) }
        shell.addView(v279SceneView)
        v279AssistView = text("ACTIVE ASSIST • CHECKING…", 7.8f, muted, true).apply { setPadding(0, space(2), 0, space(2)) }
        shell.addView(v279AssistView)
        v279TakeView = text("TAKE INTELLIGENCE • CHECKING…", 8.2f, green, true).apply { setPadding(0, space(2), 0, space(7)) }
        shell.addView(v279TakeView)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(v267ActionButton("OPEN PRIORITY STAGE", gold) {
            v279OpenStage(DevelopUgandaV279ActiveShooting.stageFromDetailed(v275CurrentStage()))
        }.apply { tag = "du_primary_action" }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = space(2) })
        actions.addView(v267ActionButton("TARGET", cyan) {
            val enabled = DevelopUgandaV279ActiveShooting.toggleSubjectPriority(this)
            Toast.makeText(this, if (enabled) "SUBJECT PRIORITY ON" else "SUBJECT PRIORITY OFF", Toast.LENGTH_SHORT).show()
            refreshV279ActiveShooting(animated = true)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        actions.addView(v267ActionButton("BEST", violet) {
            Toast.makeText(this, DevelopUgandaV279ActiveShooting.markBestTake(this, workflowConfidenceScore()), Toast.LENGTH_SHORT).show()
            refreshV279ActiveShooting(animated = true)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        actions.addView(v267ActionButton("${DevelopUgandaModeProfiles.selected(this).shortName} CAM", green) {
            v267OpenSelectedMode()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2) })
        shell.addView(actions)

        shell.addView(text("TAKE TAGS • save operator judgement against current telemetry", 7.0f, muted, true).apply { setPadding(0, space(7), 0, space(4)) })
        val tags = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("GOOD", "RETAKE", "B-ROLL", "INTERVIEW").forEachIndexed { index, tag ->
            tags.addView(v267ActionButton(tag, if (tag == "RETAKE") red else cyan) {
                Toast.makeText(this, DevelopUgandaV279ActiveShooting.markTake(this, tag, workflowConfidenceScore()), Toast.LENGTH_SHORT).show()
                refreshV279ActiveShooting(animated = true)
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                if (index > 0) leftMargin = space(2)
                if (index < 3) rightMargin = space(2)
            })
        }
        shell.addView(tags)

        val memoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        memoryRow.addView(v267ActionButton("MEM SAVE", violet) {
            Toast.makeText(this, DevelopUgandaV278LiveWorkflow.saveShotMemory(this), Toast.LENGTH_SHORT).show()
            refreshV279ActiveShooting(animated = true)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = space(2) })
        memoryRow.addView(v267ActionButton("SILENT", muted) {
            val enabled = DevelopUgandaV279ActiveShooting.toggleSilentAlerts(this)
            Toast.makeText(this, if (enabled) "SILENT ALERTS ON" else "SILENT ALERTS OFF", Toast.LENGTH_SHORT).show()
            refreshV279ActiveShooting(animated = true)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        memoryRow.addView(v267ActionButton("REVIEW", green) {
            v267Open(DevelopUgandaV274MediaVaultActivity::class.java)
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = space(2) })
        shell.addView(memoryRow)

        shell.addView(text("TARGET is operator subject-priority intent, not fake unsupported CV tracking • scores use real workflow telemetry available to V279", 6.9f, muted, false).apply {
            setPadding(0, space(7), 0, 0)
        })
        v28012SafeUi("V279 SHOOTING") { refreshV279ActiveShooting(animated = false) }
        return shell
    }

    private fun v279OpenStage(stage: String) {
        when (stage) {
            "READY", "STREAM_KEY", "CONNECTION" -> v267Open(DevelopUgandaCameraHealthActivity::class.java)
            "LIGHT" -> v267Open(DevelopUgandaV277LightingExposureActivity::class.java)
            "SOUND", "FRAME", "SUBJECT_LOCK" -> v267Open(DevelopUgandaV275CameraSettingsActivity::class.java)
            "TRACK", "RECORD", "SEGMENT", "MARKERS" -> v267OpenSelectedMode()
            "PROTECT" -> v267Open(DevelopUgandaV276RecoveryCenterActivity::class.java)
            "RATE", "REVIEW", "TRANSCRIPT", "CAPTIONS" -> v267Open(DevelopUgandaV274MediaVaultActivity::class.java)
            "DELIVER", "DELIVERY", "SHARE" -> v267Open(DevelopUgandaNewsroomActivity::class.java)
            else -> v267OpenSelectedMode()
        }
    }

    private fun refreshV279ActiveShooting(animated: Boolean) {
        val detailed = v275CurrentStage()
        val stage = DevelopUgandaModeProfiles.stageForDetailed(this, detailed)?.id
            ?: DevelopUgandaV279ActiveShooting.stageFromDetailed(detailed)
        val readiness = workflowConfidenceScore()
        val lively = workflowMotionEnabled()
        v267SetLiveText(v279HealthView, DevelopUgandaV279ActiveShooting.healthBar(this, readiness), animated && lively)
        v267SetLiveText(v279PriorityView, DevelopUgandaV279ActiveShooting.priorityAction(this, detailed, readiness), animated && lively)
        v267SetLiveText(v279ContinuityView, DevelopUgandaV279ActiveShooting.continuityStatus(this), animated && lively)
        v267SetLiveText(v279SceneView, DevelopUgandaV279ActiveShooting.sceneChangeStatus(this), animated && lively)
        v267SetLiveText(v279AssistView, DevelopUgandaV279ActiveShooting.assistStatus(this), animated && lively)
        v267SetLiveText(v279TakeView, DevelopUgandaV279ActiveShooting.takeStatus(this, readiness), animated && lively)

        v279StageChips.forEach { (name, chip) ->
            val profileStage = DevelopUgandaModeProfiles.selected(this).stages.firstOrNull { it.id == name }
            val absent = profileStage?.availability == DevelopUgandaStageAvailability.ABSENT
            val active = name == stage && !absent
            chip.setTextColor(if (active) ink else muted)
            chip.background = rounded(if (active) gold else DevelopUgandaFivemods8Theme.surfaceRaised, if (active) DevelopUgandaFivemods8Theme.content else line, 13, 1)
            chip.elevation = 0f
            chip.alpha = if (absent) 0.52f else 1f
            if (active && animated && lively) {
                chip.animate().cancel()
                chip.scaleX = 0.97f
                chip.scaleY = 0.97f
                chip.animate().scaleX(1f).scaleY(1f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
            }
        }
    }

    private fun buildV278WorkflowHome(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, cyan, 24, 1)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val selectedMode = DevelopUgandaModeProfiles.selected(this)
        titleRow.addView(text("V278 • SELECTED MODE WORKFLOW", 13.8f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(text(selectedMode.shortName, 7.4f, selectedMode.chromeAccentColor, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(72), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(titleRow)

        v278LiveStatusView = text("● ${selectedMode.shortName} • CHECKING CAMERA WORKFLOW…", 9.0f, selectedMode.chromeAccentColor, true).apply {
            setPadding(0, space(7), 0, space(6))
        }
        shell.addView(v278LiveStatusView)

        val stageScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val stageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        DevelopUgandaV278LiveWorkflow.stages(this).forEachIndexed { index, stage ->
            val availability = if (stage.availability == DevelopUgandaStageAvailability.ABSENT) "ABSENT" else "PENDING"
            val chip = text("${index + 1} ${stage.label}\n$availability", 7.1f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(space(10), space(7), space(10), space(7))
                background = rounded(DevelopUgandaFivemods8Theme.surface, line, 13, 1)
                isClickable = true
                isFocusable = true
                contentDescription = "${stage.label}. $availability. ${stage.nextAction}"
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (stage.availability == DevelopUgandaStageAvailability.ABSENT) {
                        Toast.makeText(this@DevelopUgandaGeneralHubActivity, "${stage.label} • PROFILE DATA ABSENT", Toast.LENGTH_SHORT).show()
                    } else {
                        v278OpenSimpleStage(stage.id)
                    }
                }
            }
            v278StageChips[stage.id] = chip
            stageRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                if (index > 0) leftMargin = space(4)
            })
        }
        stageScroll.addView(stageRow)
        shell.addView(stageScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        v278NextBestView = text("NEXT BEST ACTION • CHECKING…", 8.8f, gold, true).apply {
            setPadding(0, space(8), 0, space(5))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                v275OpenStage(v275CurrentStage())
            }
        }
        shell.addView(v278NextBestView)

        v278MemoryView = text("SHOT MEMORY • CHECKING…", 8.0f, violet, true).apply {
            setPadding(0, space(3), 0, space(8))
        }
        shell.addView(v278MemoryView)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(v267ActionButton("OPEN ACTIVE STAGE", gold) {
            v275OpenStage(v275CurrentStage())
        }.apply { tag = "du_primary_action" }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = space(2) })
        val memoryButton = v267ActionButton("MEM SAVE", violet) {
            val result = DevelopUgandaV278LiveWorkflow.saveShotMemory(this)
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
            refreshV278WorkflowHome(animated = true)
        }.apply {
            setOnLongClickListener {
                DevelopUgandaV278LiveWorkflow.clearShotMemory(this@DevelopUgandaGeneralHubActivity)
                Toast.makeText(this@DevelopUgandaGeneralHubActivity, "SHOT MEMORY CLEARED", Toast.LENGTH_SHORT).show()
                refreshV278WorkflowHome(animated = true)
                true
            }
        }
        actions.addView(memoryButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        actions.addView(v267ActionButton("${DevelopUgandaModeProfiles.selected(this).shortName} CAM", cyan) {
            v267OpenSelectedMode()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        actions.addView(v267ActionButton("REVIEW", green) {
            v267Open(DevelopUgandaV274MediaVaultActivity::class.java)
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = space(2) })
        shell.addView(actions)

        shell.addView(text("Tap a stage to open its real tool • MEM SAVE stores a reference setup • long-press MEM SAVE to clear", 7.1f, muted, false).apply {
            setPadding(0, space(7), 0, 0)
        })
        v28012SafeUi("V278 WORKFLOW") { refreshV278WorkflowHome(animated = false) }
        return shell
    }

    private fun v278OpenSimpleStage(stage: String) {
        when (stage) {
            "READY", "STREAM_KEY", "CONNECTION" -> v267Open(DevelopUgandaCameraHealthActivity::class.java)
            "LIGHT" -> v267Open(DevelopUgandaV277LightingExposureActivity::class.java)
            "SOUND", "SUBJECT_LOCK" -> v267Open(DevelopUgandaV275CameraSettingsActivity::class.java)
            "FRAME", "MOTION" -> v267Open(DevelopUgandaV275CameraSettingsActivity::class.java)
            "RECORD", "SEGMENT", "MARKERS" -> v267OpenSelectedMode()
            "SAFETY" -> v267Open(DevelopUgandaV276RecoveryCenterActivity::class.java)
            "REVIEW", "TRANSCRIPT", "CAPTIONS" -> v267Open(DevelopUgandaV274MediaVaultActivity::class.java)
            "DELIVER", "DELIVERY", "SHARE" -> v267Open(DevelopUgandaNewsroomActivity::class.java)
            else -> v267OpenSelectedMode()
        }
    }

    private fun refreshV278WorkflowHome(animated: Boolean) {
        val detailed = v275CurrentStage()
        val simple = DevelopUgandaModeProfiles.stageForDetailed(this, detailed)?.id
            ?: DevelopUgandaV278LiveWorkflow.simpleStage(detailed)
        val score = workflowConfidenceScore()
        val lively = workflowMotionEnabled()
        v267SetLiveText(v278LiveStatusView, DevelopUgandaV278LiveWorkflow.liveStatus(this, detailed, score), animated && lively)
        v267SetLiveText(v278NextBestView, DevelopUgandaV278LiveWorkflow.nextBestAction(this, detailed), animated && lively)
        v267SetLiveText(v278MemoryView, DevelopUgandaV278LiveWorkflow.memoryStatus(this), animated && lively)

        v278StageChips.forEach { (name, chip) ->
            val profileStage = DevelopUgandaModeProfiles.selected(this).stages.firstOrNull { it.id == name }
            val absent = profileStage?.availability == DevelopUgandaStageAvailability.ABSENT
            val active = name == simple && !absent
            chip.setTextColor(if (active) ink else muted)
            chip.background = rounded(if (active) cyan else DevelopUgandaFivemods8Theme.surfaceRaised, if (active) DevelopUgandaFivemods8Theme.accent else line, 13, 1)
            chip.elevation = 0f
            chip.alpha = if (absent) 0.52f else 1f
            if (active && animated && lively) {
                chip.animate().cancel()
                chip.scaleX = 0.97f
                chip.scaleY = 0.97f
                chip.animate().scaleX(1f).scaleY(1f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
            }
        }
    }

    private fun buildV275ControlSurface(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 22, 1)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text("V279 DEEP CONTROL SURFACE", 13.4f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(text("CONTEXT", 7.2f, gold, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(74), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(titleRow)

        v275ReadyView = text("CHECKING READY STATE…", 9.2f, green, true).apply {
            setPadding(0, space(7), 0, space(4))
        }
        shell.addView(v275ReadyView)

        v275JobView = text("PROJECT • CHECKING…", 8.1f, white, true).apply {
            setPadding(0, space(2), 0, space(7))
        }
        shell.addView(v275JobView)

        val stageScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val stageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val stages = DevelopUgandaModeProfiles.selected(this).stages
        stages.forEachIndexed { index, stage ->
            val availability = if (stage.availability == DevelopUgandaStageAvailability.ABSENT) "ABSENT" else "PENDING"
            val chip = text("${index + 1} ${stage.label}\n$availability", 7.2f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(space(10), space(7), space(10), space(7))
                background = rounded(DevelopUgandaFivemods8Theme.surface, line, 13, 1)
                isClickable = true
                isFocusable = true
                contentDescription = "${stage.label}. $availability. ${stage.nextAction}"
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (stage.availability == DevelopUgandaStageAvailability.ABSENT) {
                        Toast.makeText(this@DevelopUgandaGeneralHubActivity, "${stage.label} • PROFILE DATA ABSENT", Toast.LENGTH_SHORT).show()
                    } else {
                        v275OpenStage(stage.id)
                    }
                }
            }
            v275StageChips[stage.id] = chip
            stageRow.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply {
                if (index > 0) leftMargin = space(4)
            })
        }
        stageScroll.addView(stageRow)
        shell.addView(stageScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)))

        v275ContextView = text("CONTEXT • CHECKING…", 8.3f, cyan, true).apply {
            setPadding(0, space(7), 0, space(3))
        }
        shell.addView(v275ContextView)
        v275NextView = text("NEXT → CHECKING…", 9.1f, gold, true).apply {
            setPadding(0, space(3), 0, space(7))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                v275OpenStage(v275CurrentStage())
            }
        }
        shell.addView(v275NextView)

        shell.addView(text("QUICK ACTIONS • direct, real routes", 7.3f, muted, true).apply { setPadding(0, space(2), 0, space(5)) })
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(v267ActionButton("LUTS", violet) { v275OpenCameraControl("LUTS") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = space(2) })
        quick.addView(v267ActionButton("ASPECT", cyan) { v275OpenCameraControl("ASPECT") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        quick.addView(v267ActionButton("OUTPUT", gold) { v267Open(DevelopUgandaV275CameraSettingsActivity::class.java) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        quick.addView(v267ActionButton("LAST CLIP", green) { v267Open(DevelopUgandaV274MediaVaultActivity::class.java) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(2); rightMargin = space(2) })
        quick.addView(v267ActionButton("DIRECTOR", violet) { v267Open(DevelopUgandaV264LiveCutDirectorActivity::class.java) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(2) })
        shell.addView(quick)

        v275RecentView = text("RECENT • CHECKING…", 7.5f, muted, true).apply {
            setPadding(0, space(8), 0, 0)
            visibility = if (DevelopUgandaV275ControlSurface.bool(this@DevelopUgandaGeneralHubActivity, "recent_activity", true)) View.VISIBLE else View.GONE
        }
        shell.addView(v275RecentView)
        v28012SafeUi("V275 CONTROL") { refreshV275ControlSurface(animated = false) }
        return shell
    }

    private fun v275OpenCameraControl(control: String) {
        try {
            val profile = DevelopUgandaModeProfiles.selected(this)
            DevelopUgandaModeProfiles.rememberSelected(this, profile.page)
            startActivity(
                Intent(this, profile.destination)
                    .putExtra("v275_open_control", control)
            )
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V275 CAMERA CONTROL", error)
            Toast.makeText(this, "$control • CAMERA ROUTE UNAVAILABLE", Toast.LENGTH_SHORT).show()
        }
    }

    private fun v275CurrentStage(): String {
        if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(this)) return "VERIFY"
        val score = workflowConfidenceScore()
        if (score < 75) return "PREPARE"
        val audio = try {
            DevelopUgandaV272FieldSoundContinuity.audioInputSummary(this)
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V275 AUDIO READ", error)
            "AUDIO UNKNOWN"
        }
        if (audio.contains("PERMISSION", true) || audio.contains("NONE", true)) return "SOUND"
        if (DevelopUgandaV277LightingExposure.bool(this, "lighting_coach", true)) {
            if (!DevelopUgandaV277LightingExposure.hasFreshReading(this) || DevelopUgandaV277LightingExposure.needsAttention(this)) return "LIGHT"
        }
        val clips = DevelopUgandaV274MediaVaultStore.clips(this)
        if (clips.isEmpty() && DevelopUgandaV272FieldSoundContinuity.bool(this, "continuity_engine", true) && !DevelopUgandaV272FieldSoundContinuity.hasReference(this)) return "CONTINUITY"
        val mode = DevelopUgandaV273MotionShotControl.shotMode(this)
        if (clips.isEmpty() && mode !in setOf("STATIC", "TRIPOD") && DevelopUgandaV273MotionShotControl.rehearsal(this, "START") == null) return "REHEARSE"
        val last = clips.firstOrNull()
        if (last == null) return "RECORD"
        if (last.rating == "UNRATED") return "REVIEW"
        if (DevelopUgandaV274MediaVaultStore.queue(this).isNotEmpty()) return "DELIVER"
        return "RECORD"
    }

    private fun v275StageDetail(stage: String): String = when (stage) {
        "PREPARE" -> "Check battery/storage/thermal and project identity before a long or important take."
        "SOUND" -> "Confirm the microphone route and run Audio Check before recording."
        "LIGHT" -> "Open the camera and confirm face exposure, highlights/shadows and flicker-risk guidance before continuity/framing."
        "CONTINUITY" -> "Save/match a reference if this scene needs repeatable framing and camera state."
        "FRAME" -> "Set aspect, LUT preview, horizon and composition before rehearsing."
        "REHEARSE" -> "Save START/END move state for repeatable motion, focus and zoom work."
        "FOCUS" -> "Confirm tracking / focus A-B / metering before the take."
        "VERIFY" -> "V276 checks the finalized URI/file/duration or shows an interrupted session that needs operator review."
        "REVIEW" -> "Rate the newest clip BEST / KEEP / RETAKE and protect important masters."
        "ORGANIZE" -> "Use Media Vault / Story Desk to name, filter and protect recorded work."
        "DELIVER" -> "Check delivery queue; render-required formats stay clearly marked."
        else -> "Stage state is unknown; use the visible fault marker or return to the workflow home."
    }

    private fun v275OpenStage(stage: String) {
        when (stage) {
            "PREPARE", "READY", "STREAM_KEY", "CONNECTION" -> v267Open(DevelopUgandaCameraHealthActivity::class.java)
            "SOUND", "SUBJECT_LOCK", "CONTINUITY", "FRAME", "REHEARSE", "FOCUS" -> v267Open(DevelopUgandaV275CameraSettingsActivity::class.java)
            "LIGHT" -> v267Open(DevelopUgandaV277LightingExposureActivity::class.java)
            "RECORD", "SEGMENT", "MARKERS" -> v267OpenSelectedMode()
            "VERIFY" -> v267Open(DevelopUgandaV276RecoveryCenterActivity::class.java)
            "REVIEW", "ORGANIZE", "TRANSCRIPT", "CAPTIONS" -> v267Open(DevelopUgandaV274MediaVaultActivity::class.java)
            "DELIVER", "DELIVERY", "SHARE" -> v267Open(DevelopUgandaNewsroomActivity::class.java)
            else -> v267OpenSelectedMode()
        }
    }

    private fun refreshV275ControlSurface(animated: Boolean) {
        val stage = v275CurrentStage()
        val modeStage = DevelopUgandaModeProfiles.stageForDetailed(this, stage)?.id
        val score = workflowConfidenceScore()
        val output = DevelopUgandaV271LiveCoach.outputMode(this)
        val audio = try {
            DevelopUgandaV272FieldSoundContinuity.audioInputSummary(this)
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V275 AUDIO READ", error)
            "AUDIO UNKNOWN"
        }
        val audioShort = when {
            audio.contains("UNKNOWN", true) -> "AUDIO UNKNOWN"
            audio.contains("PERMISSION", true) -> "MIC PERMISSION"
            audio.contains("NONE", true) -> "MIC CHECK"
            else -> "AUDIO OK"
        }
        val recSafety = if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(this)) "RECOVERY CHECK" else "REC SAFE"
        val light = when {
            !DevelopUgandaV277LightingExposure.hasFreshReading(this) -> "LIGHT CHECK"
            DevelopUgandaV277LightingExposure.needsAttention(this) -> "LIGHT ATTENTION"
            else -> "LIGHT GOOD"
        }
        val readyText = if (score >= 0) "$score%" else "UNKNOWN"
        val freeText = freeStorageGb().let { if (it >= 0L) "${it}GB" else "UNKNOWN" }
        val readiness = "READY $readyText • $recSafety • $audioShort • $light • ${DevelopUgandaV273MotionShotControl.shotMode(this)} • $freeText • $output"
        v267SetLiveText(v275ReadyView, readiness, animated && workflowMotionEnabled())
        v267SetLiveText(v275JobView, v260ProjectStatus() + " • PROFILE ${DevelopUgandaV275ControlSurface.selectedProfile(this)}", animated)
        v267SetLiveText(v275ContextView, "CONTEXT • $stage • ${v275StageDetail(stage)}", animated)
        v267SetLiveText(v275NextView, "NEXT → $stage", animated)

        val highlights = DevelopUgandaV275ControlSurface.bool(this, "context_highlights", true)
        v275StageChips.forEach { (name, chip) ->
            val profileStage = DevelopUgandaModeProfiles.selected(this).stages.firstOrNull { it.id == name }
            val absent = profileStage?.availability == DevelopUgandaStageAvailability.ABSENT
            val active = name == modeStage && !absent
            chip.setTextColor(if (active) ink else if (highlights) muted else white)
            chip.background = rounded(if (active) gold else DevelopUgandaFivemods8Theme.surfaceRaised, if (active) DevelopUgandaFivemods8Theme.content else line, 13, 1)
            chip.elevation = 0f
            chip.alpha = if (absent) 0.52f else 1f
            if (active && animated && workflowMotionEnabled()) {
                chip.animate().cancel()
                chip.scaleX = 0.97f
                chip.scaleY = 0.97f
                chip.animate().scaleX(1f).scaleY(1f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
            }
        }

        v275RecentView?.visibility = if (DevelopUgandaV275ControlSurface.bool(this, "recent_activity", true)) View.VISIBLE else View.GONE
        val clips = DevelopUgandaV274MediaVaultStore.clips(this)
        val recent = if (clips.isEmpty()) {
            "RECENT • no V274/V275/V276/V277 clip yet • record a take to start the vault timeline"
        } else {
            val c = clips.first()
            "RECENT • ${c.name.take(28)} • ${c.rating} • ${c.health} • QUEUE ${DevelopUgandaV274MediaVaultStore.queue(this).size}"
        }
        v267SetLiveText(v275RecentView, recent, animated)
    }

    private fun buildV277LightingStrip(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 20, 1)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                v267Open(DevelopUgandaV277LightingExposureActivity::class.java)
            }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text("V277 • LIGHTING + EXPOSURE", 11.8f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("OPEN ›", 7.4f, gold, true), LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(row)
        v277LightStripView = text("LIGHT • OPEN CAMERA TO ANALYSE", 8.2f, gold, true).apply { setPadding(0, space(6), 0, 0) }
        shell.addView(v277LightStripView)
        v28012SafeUi("V277 LIGHT") { refreshV277LightingStrip(animated = false) }
        return shell
    }

    private fun refreshV277LightingStrip(animated: Boolean) {
        val value = DevelopUgandaV277LightingExposure.lightStatus(this)
        v267SetLiveText(v277LightStripView, value, animated && workflowMotionEnabled())
        v277LightStripView?.setTextColor(if (DevelopUgandaV277LightingExposure.needsAttention(this)) red else if (DevelopUgandaV277LightingExposure.hasFreshReading(this)) green else gold)
    }

    private fun buildV276SafetyStrip(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, green, 20, 1)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                v267Open(DevelopUgandaV276RecoveryCenterActivity::class.java)
            }
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(text("V276 • RECORDING SAFETY + RECOVERY", 11.8f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(text("OPEN ›", 7.4f, green, true), LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(row)
        v276SafetyStripView = text("REC SAFETY • CHECKING…", 8.2f, green, true).apply { setPadding(0, space(6), 0, 0) }
        shell.addView(v276SafetyStripView)
        v28012SafeUi("V276 SAFETY") { refreshV276SafetyStrip(animated = false) }
        return shell
    }

    private fun refreshV276SafetyStrip(animated: Boolean) {
        val value = DevelopUgandaV276RecordingSafety.healthStrip(this)
        v267SetLiveText(v276SafetyStripView, value, animated && workflowMotionEnabled())
        v276SafetyStripView?.setTextColor(if (DevelopUgandaV276RecordingSafety.needsRecoveryReview(this)) red else green)
    }

    private fun buildV270GuidedWorkflow(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, cyan, 22, 1)
        }
        val audit = DevelopUgandaV270Guidance.auditRoutes(this)
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text("GUIDED WORKFLOW • NEXT ACTION", 13.2f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(text(audit.shortLabel(), 7.4f, if (audit.ok) green else red, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(132), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(titleRow)

        val clipCount = DevelopUgandaV269StoryDeskStore.clipCount(this)
        val progress = DevelopUgandaV269StoryDeskStore.progress(this)
        val score = workflowConfidenceScore()
        val next = when {
            score < 75 -> "1 • PREPARE • fix health warnings before recording"
            clipCount == 0 -> "2 • SHOOT • record ${DevelopUgandaV269StoryDeskStore.activeShot(this)}"
            DevelopUgandaV269StoryDeskStore.lastRating(this) == "UNRATED" -> "3 • REVIEW • rate the latest take"
            progress < 100 -> "2 • SHOOT • next ${DevelopUgandaV269StoryDeskStore.activeShot(this)}"
            else -> "5 • DELIVER • story shot list is complete"
        }
        shell.addView(text(next, 9f, gold, true).apply { setPadding(0, space(5), 0, space(8)) })
        shell.addView(text("Recommended order: PREPARE → SHOOT → REVIEW → COLOR/EDIT → PACKAGE/SHARE. Touch ⓘ anywhere you see it for plain-language help.", 8f, muted, false))

        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(v267ActionButton("1 PREPARE", green) { v267Open(DevelopUgandaCameraHealthActivity::class.java) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = space(3) })
        row1.addView(v267ActionButton("2 SHOOT", cyan) { v267OpenSelectedMode() }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(3); rightMargin = space(3) })
        row1.addView(v267ActionButton("3 REVIEW", gold) { v267Open(DevelopUgandaV265ProxySyncReviewActivity::class.java) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(3) })
        shell.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = space(8) })

        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(v267ActionButton("4 COLOR / EDIT", violet) { v267Open(DevelopUgandaColorStudioActivity::class.java) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = space(3) })
        row2.addView(v267ActionButton("5 DELIVER", gold) { v267Open(DevelopUgandaV269SmartStoryDeskActivity::class.java) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(3); rightMargin = space(3) })
        row2.addView(v267ActionButton("VERIFY\nACTIONS", if (audit.ok) green else red) { DevelopUgandaV270Guidance.showAudit(this) }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = space(3) })
        shell.addView(row2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = space(6) })
        return shell
    }

    private fun buildV269StoryCommand(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 22, 1)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("SMART STORY • FIELD DESK", 13.2f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(text("V272", 9.5f, gold, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(64), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(head)
        shell.addView(text("The story layer now follows the guided order: define the story → choose NEXT shot → shoot → rate the take → review/deliver. Touch ⓘ on cards/settings whenever a function is unfamiliar.", 8.4f, muted, false).apply { setPadding(0, space(4), 0, space(8)) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val storyCard = v267MetricCard("CURRENT STORY", gold)
        v269StoryView = storyCard.findViewWithTag("value")
        row.addView(storyCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = space(4) })
        val afterCard = v267MetricCard("AFTER SHOOT", green)
        v269AfterShootView = afterCard.findViewWithTag("value")
        row.addView(afterCard, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = space(4) })
        shell.addView(row)

        v269RecentView = text("RECENT • —", 7.8f, cyan, true).apply { setPadding(space(2), space(8), space(2), space(4)); maxLines = 2 }
        shell.addView(v269RecentView)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(v267ActionButton("OPEN\nSTORY DESK", gold) { v267Open(DevelopUgandaV269SmartStoryDeskActivity::class.java) }.apply { tag = "du_primary_action" }, LinearLayout.LayoutParams(0, space(52), 1f).apply { rightMargin = space(3) })
        actions.addView(v267ActionButton("OPEN\nSELECTED MODE", cyan) { v267OpenSelectedMode() }, LinearLayout.LayoutParams(0, space(52), 1f).apply { leftMargin = space(3); rightMargin = space(3) })
        actions.addView(v267ActionButton("REVIEW\nTAKES", green) { v267Open(DevelopUgandaStoryPackagesActivity::class.java) }, LinearLayout.LayoutParams(0, space(52), 1f).apply { leftMargin = space(3) })
        shell.addView(actions)
        v28012SafeUi("V269 STORY") { refreshV269StoryCommand(animated = false) }
        return shell
    }

    private fun refreshV269StoryCommand(animated: Boolean) {
        val enabled = DevelopUgandaV269StoryDeskStore.storyDeskEnabled(this)
        if (!enabled) {
            v267SetLiveText(v269StoryView, "STORY DESK OFF\nEnable in Pro Settings", animated)
            v267SetLiveText(v269AfterShootView, "CLIPS ${DevelopUgandaV269StoryDeskStore.clipCount(this)}\nDESK PAUSED", animated)
            v267SetLiveText(v269RecentView, "RECENT • STORY DESK OFF", animated)
            return
        }
        v267SetLiveText(v269StoryView, DevelopUgandaV269StoryDeskStore.commandStorySummary(this), animated)
        v267SetLiveText(v269AfterShootView, DevelopUgandaV269StoryDeskStore.afterShootSummary(this), animated)
        v267SetLiveText(v269RecentView, "RECENT • ${DevelopUgandaV269StoryDeskStore.latestActivity(this)}", animated)
    }

    private fun buildV271LiveStrip(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(10), space(12), space(10))
            background = rounded(DevelopUgandaFivemods8Theme.surface, cyan, 18, 1)
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(text("V271 LIVE STRIP", 8.5f, gold, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val output = text(DevelopUgandaV271LiveCoach.outputMode(this), 8.4f, cyan, true).apply {
            gravity = Gravity.END
            isClickable = true
            setOnClickListener { v267OpenSelectedMode() }
        }
        v271OutputStripView = output
        top.addView(output, LinearLayout.LayoutParams(dp(155), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(top)

        val ready = text("CHECKING…", 9.4f, white, true).apply {
            setPadding(0, space(6), 0, space(2))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                v267Open(DevelopUgandaCameraHealthActivity::class.java)
            }
            setOnLongClickListener {
                DevelopUgandaV270Guidance.showFeature(
                    this@DevelopUgandaGeneralHubActivity,
                    "V271 LIVE STRIP",
                    "A changing readiness line using real battery, free storage, thermal state, audio-input availability and current output-master mode. Tap it to open Camera Health."
                )
                true
            }
        }
        v271ReadyStripView = ready
        shell.addView(ready)
        shell.addView(text("Tap LIVE STRIP → Camera Health • tap output mode → Field Camera / Pro Settings", 7.2f, muted, false))
        v28012SafeUi("V271 COACH") { refreshV271LiveStrip(animated = false) }
        return shell
    }

    private fun refreshV271LiveStrip(animated: Boolean) {
        val score = workflowConfidenceScore()
        val bat = batteryPercent().let { if (it >= 0) "$it%" else "UNKNOWN" }
        val free = freeStorageGb()
        val audio = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val manager = getSystemService(AUDIO_SERVICE) as AudioManager
                if (manager.getDevices(AudioManager.GET_DEVICES_INPUTS).isNotEmpty()) "AUDIO OK" else "AUDIO CHECK"
            } else "AUDIO DEVICE"
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V271 AUDIO READ", error)
            "AUDIO UNKNOWN"
        }
        val therm = thermalStatusLabel().removePrefix("THERMAL • ")
        val readyText = if (score >= 0) "$score%" else "UNKNOWN"
        val freeText = if (free >= 0L) "${free}GB" else "UNKNOWN"
        val value = "READY $readyText  •  $audio  •  BAT $bat  •  FREE $freeText  •  THERM $therm"
        v267SetLiveText(v271ReadyStripView, value, animated)
        v267SetLiveText(v271OutputStripView, DevelopUgandaV271LiveCoach.outputMode(this), animated)
    }

    private fun buildV272SoundStrip(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(9), space(12), space(9))
            background = rounded(DevelopUgandaFivemods8Theme.surface, green, 18, 1)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("V272 SOUND + CONTINUITY", 8.5f, gold, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(text("LIVE", 8.4f, green, true), LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(head)
        val live = text("CHECKING SOUND / REFERENCE…", 9.1f, white, true).apply {
            setPadding(0, space(6), 0, space(2))
            isClickable = true
            setOnClickListener { v267OpenSelectedMode() }
            setOnLongClickListener {
                DevelopUgandaV270Guidance.showFeature(
                    this@DevelopUgandaGeneralHubActivity,
                    "V272 FIELD SOUND + CONTINUITY",
                    "Shows detected Android microphone inputs, whether a continuity reference exists, and the pre-roll request state. Tap to open the Field Camera / Pro Settings."
                )
                true
            }
        }
        v272SoundStripView = live
        shell.addView(live)
        shell.addView(text("WORKFLOW • 1 PREPARE → 2 SOUND CHECK → 3 MATCH SHOT → 4 FRAME/FOCUS → 5 RECORD → 6 REVIEW → 7 DELIVER", 7.0f, muted, false))
        v28012SafeUi("V272 SOUND") { refreshV272SoundStrip(animated = false) }
        v28012SafeUi("V273 MOTION") { refreshV273MotionStrip(animated = false) }
        return shell
    }

    private fun refreshV272SoundStrip(animated: Boolean) {
        v267SetLiveText(v272SoundStripView, DevelopUgandaV272FieldSoundContinuity.hubSummary(this), animated)
    }


    private fun buildV273MotionStrip(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(9), space(12), space(9))
            background = rounded(DevelopUgandaFivemods8Theme.surface, cyan, 18, 1)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("V273 MOTION + SHOT CONTROL", 8.5f, gold, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(text("LIVE", 8.4f, cyan, true), LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(head)
        val live = text("CHECKING LEVEL / MOTION…", 9.1f, white, true).apply {
            setPadding(0, space(6), 0, space(2))
            isClickable = true
            setOnClickListener { v267OpenSelectedMode() }
            setOnLongClickListener {
                DevelopUgandaV270Guidance.showFeature(
                    this@DevelopUgandaGeneralHubActivity,
                    "V273 MOTION + SHOT CONTROL",
                    "Live strip for shot-move mode, motion guidance, subject-tracking mode, focus A/B state and stabilization status. Tap to open Field Camera / Pro Settings."
                )
                true
            }
        }
        v273MotionStripView = live
        shell.addView(live)
        shell.addView(text("WORKFLOW • 1 PREPARE → 2 SOUND → 3 CONTINUITY → 4 FRAME → 5 REHEARSE MOVE → 6 FOCUS/TRACK → 7 RECORD → 8 REVIEW → 9 DELIVER", 7.0f, muted, false))
        v28012SafeUi("V273 MOTION") { refreshV273MotionStrip(animated = false) }
        return shell
    }

    private fun refreshV273MotionStrip(animated: Boolean) {
        v267SetLiveText(v273MotionStripView, DevelopUgandaV273MotionShotControl.hubSummary(this), animated)
    }

    internal fun v273HubMotionSummary(mode: String): String {
        // General Hub has no live camera sensor stream; it truthfully reports the saved shot mode
        // plus the active camera-control features that will be live once the camera opens.
        return "SHOT $mode  •  TRACK READY  •  FOCUS A/B  •  ZOOM RAMP  •  OPEN CAMERA FOR LIVE LEVEL"
    }

    private fun buildV274VaultStrip(): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(12), space(9), space(12), space(9))
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 18, 1)
        }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        head.addView(text("V274 MEDIA VAULT + DELIVERY", 8.5f, gold, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(text("LIVE", 8.4f, green, true), LinearLayout.LayoutParams(dp(52), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(head)
        val live = text("CHECKING LAST CLIP / DELIVERY…", 9.1f, white, true).apply {
            setPadding(0, space(6), 0, space(2))
            isClickable = true
            isFocusable = true
            setOnClickListener { v267Open(DevelopUgandaV274MediaVaultActivity::class.java) }
            setOnLongClickListener {
                DevelopUgandaV270Guidance.showFeature(
                    this@DevelopUgandaGeneralHubActivity,
                    "V274 MEDIA VAULT + DELIVERY",
                    "Shows the latest registered master, clip rating/protection state, selected delivery preset and queue. Tap to open Media Vault. Original masters are never overwritten by this layer."
                )
                true
            }
        }
        v274VaultStripView = live
        shell.addView(live)
        shell.addView(text("WORKFLOW • RECORD → REVIEW → PROTECT → ORGANIZE → PREPARE → DELIVER", 7.0f, muted, false))
        v28012SafeUi("V274 VAULT") { refreshV274VaultStrip(animated = false) }
        return shell
    }

    private fun refreshV274VaultStrip(animated: Boolean) {
        val latest = DevelopUgandaV274MediaVaultStore.lastClipSummary(this)
        val queue = DevelopUgandaV274MediaVaultStore.queue(this).size
        val preset = DevelopUgandaV274MediaVaultStore.selectedPreset(this)
        v267SetLiveText(v274VaultStripView, "$latest • $preset • QUEUE $queue", animated)
    }

    private fun buildV267CommandCenter(): View {
        val shell = panelBlock().apply {
            background = rounded(DevelopUgandaFivemods8Theme.surface, gold, 22, 1)
            v267CommandShell = this
        }

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(text("LIVE SPEC • COMMAND CENTER", 13.2f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val ready = text("CHECKING", 10f, gold, true).apply { gravity = Gravity.END }
        v267ReadyView = ready
        head.addView(ready, LinearLayout.LayoutParams(dp(112), ViewGroup.LayoutParams.WRAP_CONTENT))
        shell.addView(head)

        shell.addView(text("Real device capability + current setup + project + health. Tap controls here to act; detailed workflow remains below.", 8.4f, muted, false).apply {
            setPadding(0, space(4), 0, space(8))
        })

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        v267DeviceView = v267MetricCard("DEVICE SPECS", cyan).also { r1.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = space(4) }) }.findViewWithTag("value")
        v267SetupView = v267MetricCard("CURRENT SETUP", gold).also { r1.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = space(4) }) }.findViewWithTag("value")
        v267HealthView = v267MetricCard("CAMERA HEALTH", green).also { r2.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = space(4); topMargin = space(8) }) }.findViewWithTag("value")
        v267ProjectView = v267MetricCard("PROJECT / SLATE", violet).also { r2.addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = space(4); topMargin = space(8) }) }.findViewWithTag("value")
        grid.addView(r1)
        grid.addView(r2)
        shell.addView(grid)

        v267AudioView = text("AUDIO • CHECKING", 7.8f, cyan, true).apply { setPadding(space(2), space(9), space(2), 0) }
        shell.addView(v267AudioView)
        v267RecommendationView = text("RECOMMENDATION • CHECKING", 8.2f, gold, true).apply { setPadding(space(2), space(5), space(2), space(2)) }
        shell.addView(v267RecommendationView)

        val readyButton = v267ActionButton("✓ READY TO SHOOT CHECK", gold) {
            refreshV267CommandCenter(animated = true)
            val score = workflowConfidenceScore()
            val result = when {
                score < 0 -> "READINESS UNKNOWN • CHECK TELEMETRY"
                score >= 85 -> "READY $score% • SHOOT"
                else -> "READY $score% • CHECK WARNINGS"
            }
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show()
        }
        shell.addView(readyButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { topMargin = space(8) })

        if (v267QuickControlsEnabled()) {
            shell.addView(text("QUICK ACTIONS", 8.2f, muted, true).apply { setPadding(space(2), space(10), 0, space(5)) })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            actions.addView(v267ActionButton("FIELD\nCAM", cyan) { v267OpenSelectedMode() }, LinearLayout.LayoutParams(0, space(52), 1f).apply { rightMargin = space(3) })
            actions.addView(v267ActionButton("HEALTH", green) { v267Open(DevelopUgandaCameraHealthActivity::class.java) }, LinearLayout.LayoutParams(0, space(52), 1f).apply { leftMargin = space(3); rightMargin = space(3) })
            actions.addView(v267ActionButton("DIRECTOR", violet) { v267Open(DevelopUgandaV264LiveCutDirectorActivity::class.java) }, LinearLayout.LayoutParams(0, space(52), 1f).apply { leftMargin = space(3); rightMargin = space(3) })
            actions.addView(v267ActionButton("REVIEW", gold) { v267Open(DevelopUgandaV265ProxySyncReviewActivity::class.java) }, LinearLayout.LayoutParams(0, space(52), 1f).apply { leftMargin = space(3) })
            shell.addView(actions)

            shell.addView(text("QUICK SHOOT PRESET • applies when Field Camera opens", 7.7f, muted, true).apply { setPadding(space(2), space(9), 0, space(5)) })
            val presets = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("NEWS", "INTERVIEW", "CINEMA", "NIGHT", "SOCIAL").forEachIndexed { index, name ->
                presets.addView(v267ActionButton(name, if (index == 2) gold else cyan) { v267QueuePreset(name) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply {
                    if (index > 0) leftMargin = space(2)
                    if (index < 4) rightMargin = space(2)
                })
            }
            shell.addView(presets)
        }

        v28012SafeUi("V267 COMMAND") { refreshV267CommandCenter(animated = false) }
        return shell
    }

    private fun v267MetricCard(title: String, accent: Int): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(10), space(9), space(10), space(9))
            background = rounded(card, line, 16, 1)
            addView(text(title, 7.3f, accent, true))
            addView(text("CHECKING…", 8.2f, white, true).apply {
                tag = "value"
                setPadding(0, space(4), 0, 0)
                minLines = 2
            })
        }

    private fun liveMetricCard(title: String, accent: Int, bounded: Boolean): MetricCardRefs {
        val value = DevelopUgandaLiveMetricTextView(this).apply {
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 16f)
            setTextColor(white)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, space(4), 0, space(4))
            visibility = View.GONE
        }
        val bar = if (bounded) {
            DevelopUgandaEasedMetricBar(this).apply {
                setAccent(accent)
                visibility = View.GONE
            }
        } else {
            null
        }
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(8), space(8), space(8), space(8))
            background = rounded(card, line, 12, 1)
            visibility = View.GONE
            addView(text(title, 11f, accent, true))
            addView(
                value,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, touchMinimum()),
            )
            bar?.let {
                addView(
                    it,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, space(8)).apply {
                        topMargin = space(8)
                    },
                )
            }
        }
        return MetricCardRefs(shell, value, bar)
    }

    private fun bindConsoleMetric(
        refs: MetricCardRefs?,
        readout: DevelopUgandaMetricReadout?,
        animated: Boolean,
    ) {
        val target = refs ?: return
        if (readout == null || !readout.value.isFinite()) {
            target.shell.visibility = View.VISIBLE
            target.value.visibility = View.VISIBLE
            target.value.animate().cancel()
            target.value.text = "UNKNOWN"
            target.value.setTextColor(DevelopUgandaFivemods8Theme.contentDim)
            target.value.isClickable = false
            target.value.isLongClickable = false
            target.value.contentDescription = "Value unknown; live source unavailable"
            target.bar?.visibility = View.GONE
            return
        }
        target.shell.visibility = View.VISIBLE
        DevelopUgandaMetricReadouts.bind(this, target.value, readout, animated)
        target.bar?.setLevel(readout.value, animated)
    }

    private fun hideConsoleMetrics() {
        listOf(
            batteryMetric,
            storageMetric,
            thermalMetric,
            readinessMetric,
            takeMetric,
            clipMetric,
            coverageMetric,
            storyMetric,
        ).forEach { refs ->
            refs?.shell?.visibility = View.VISIBLE
            refs?.value?.apply {
                visibility = View.VISIBLE
                animate().cancel()
                text = "UNKNOWN"
                setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                isClickable = false
                isLongClickable = false
                contentDescription = "Value unknown; workflow snapshot failed"
            }
            refs?.bar?.visibility = View.GONE
        }
    }

    private fun refreshConsoleMetrics(
        snapshot: DevelopUgandaV28012ProWorkflowConsole.Snapshot,
        animated: Boolean,
    ) {
        bindConsoleMetric(
            batteryMetric,
            snapshot.battery.takeIf { it >= 0 }?.let { value ->
                DevelopUgandaMetricReadout(
                    id = "battery_percent",
                    label = "BATTERY",
                    value = value.toDouble(),
                    format = { "${it.roundToInt().coerceIn(0, 100)}%" },
                    meaning = "The phone's current reported battery capacity.",
                    action = if (value <= 15) "Connect power before a critical or long take." else "No action needed.",
                    origin = DevelopUgandaMetricOrigin.MEASURED,
                    source = "Android BatteryManager.BATTERY_PROPERTY_CAPACITY",
                    warning = value <= 15,
                )
            },
            animated,
        )
        bindConsoleMetric(
            storageMetric,
            snapshot.freeGb.takeIf { it >= 0L }?.let { value ->
                DevelopUgandaMetricReadout(
                    id = "free_storage_gb",
                    label = "FREE STORAGE",
                    value = value.toDouble(),
                    format = { "${it.roundToInt().coerceAtLeast(0)} GB" },
                    meaning = "Free space on the filesystem used by this app.",
                    action = if (value <= 4L) "Free storage before recording another important take." else "No action needed.",
                    origin = DevelopUgandaMetricOrigin.MEASURED,
                    source = "Android StatFs.availableBytes converted to decimal GB",
                    warning = value <= 4L,
                )
            },
            animated,
        )
        bindConsoleMetric(
            thermalMetric,
            snapshot.thermalStatus.takeIf { it >= 0 }?.let { value ->
                DevelopUgandaMetricReadout(
                    id = "thermal_status",
                    label = "THERMAL",
                    value = value.toDouble(),
                    format = { snapshot.thermal },
                    meaning = "The current thermal pressure state reported by Android.",
                    action = if (value >= PowerManager.THERMAL_STATUS_MODERATE) "Pause non-essential work and let the device cool." else "No action needed.",
                    origin = DevelopUgandaMetricOrigin.MEASURED,
                    source = "Android PowerManager.currentThermalStatus",
                    warning = value >= PowerManager.THERMAL_STATUS_MODERATE,
                )
            },
            animated,
        )
        bindConsoleMetric(
            readinessMetric,
            snapshot.readiness.takeIf { it >= 0 }?.let { value ->
                DevelopUgandaMetricReadout(
                    id = "workflow_readiness",
                    label = "READINESS",
                    value = value.toDouble(),
                    format = { "${it.roundToInt().coerceIn(0, 100)}%" },
                    meaning = "A workflow readiness score derived from current device and recovery state.",
                    action = if (value < 75) "Open the readiness detail and resolve its named warning." else "No action needed.",
                    origin = DevelopUgandaMetricOrigin.DERIVED,
                    source = "Battery, storage, thermal, recovery and lighting state",
                    warning = value < 75,
                )
            },
            animated,
        )
        bindConsoleMetric(
            takeMetric,
            DevelopUgandaMetricReadout(
                id = "take_counter",
                label = "TAKE",
                value = snapshot.take.toDouble(),
                format = { "T${it.roundToInt().coerceAtLeast(1).toString().padStart(3, '0')}" },
                meaning = "The take number currently stored in the active project slate.",
                action = "Change it only from the existing project/slate controls.",
                origin = DevelopUgandaMetricOrigin.MEASURED,
                source = "Persisted project take counter",
            ),
            animated,
        )
        bindConsoleMetric(
            clipMetric,
            DevelopUgandaMetricReadout(
                id = "registered_clips",
                label = "CLIPS",
                value = snapshot.clipCount.toDouble(),
                format = { it.roundToInt().coerceAtLeast(0).toString() },
                meaning = "The largest known registered-clip count across Story Desk and Media Vault.",
                action = "If this is lower than expected, open Media Vault and inspect its visible fault state.",
                origin = DevelopUgandaMetricOrigin.DERIVED,
                source = "max(Story Desk clip count, Media Vault registered clips)",
            ),
            animated,
        )
        bindConsoleMetric(
            coverageMetric,
            DevelopUgandaMetricReadout(
                id = "director_coverage",
                label = "COVERAGE",
                value = snapshot.coverage.toDouble(),
                format = { "${it.roundToInt().coerceIn(0, 100)}%" },
                meaning = "The share of the current Smart Director shot plan marked complete.",
                action = if (snapshot.coverage < 100) "Use the named next shot in Smart Director." else "Review and rate the completed coverage.",
                origin = DevelopUgandaMetricOrigin.DERIVED,
                source = "Completed Smart Director shots divided by planned shots",
            ),
            animated,
        )
        bindConsoleMetric(
            storyMetric,
            DevelopUgandaMetricReadout(
                id = "story_progress",
                label = "STORY",
                value = snapshot.storyProgress.toDouble(),
                format = { "${it.roundToInt().coerceIn(0, 100)}%" },
                meaning = "The completion percentage stored by the current Story Desk workflow.",
                action = if (snapshot.storyProgress < 100) "Capture the Story Desk's named next shot." else "Proceed to review and delivery.",
                origin = DevelopUgandaMetricOrigin.DERIVED,
                source = "Story Desk completed items divided by its current plan",
            ),
            animated,
        )
    }

    private fun v267ActionButton(label: String, accent: Int, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 7.8f)
            minimumWidth = 0
            minimumHeight = 0
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            gravity = Gravity.CENTER
            setPadding(space(5), 0, space(5), 0)
            DevelopUgandaBroadcastPresentation.styleButton(
                this,
                DevelopUgandaButtonWeight.SECONDARY,
                accent,
            )
            setOnClickListener {
                v28012SafeUi("BUTTON • $label") {
                    action()
                }
            }
        }

    private fun v267Open(destination: Class<out Activity>) {
        DevelopUgandaV270Guidance.safeOpen(this, destination)
    }

    private fun v267OpenSelectedMode() {
        val profile = DevelopUgandaModeProfiles.selected(this)
        DevelopUgandaModeProfiles.rememberSelected(this, profile.page)
        v267Open(profile.destination)
    }

    private fun v267QueuePreset(name: String) {
        val preset = name.uppercase(Locale.US).let { if (it in setOf("NEWS", "INTERVIEW", "CINEMA", "NIGHT", "SOCIAL")) it else "NEWS" }
        v267Prefs().edit()
            .putString("pending_quick_preset", preset)
            .putString("last_quick_preset", preset)
            .apply()
        Toast.makeText(this, "$preset PRESET QUEUED • OPENING FIELD CAMERA", Toast.LENGTH_SHORT).show()
        handler.postDelayed({ v267OpenSelectedMode() }, if (v267CommandMotionEnabled()) 120L else 0L)
    }

    private fun refreshV267CommandCenter(animated: Boolean) {
        val live = v267LiveSpecEnabled()
        val score = workflowConfidenceScore()
        val readyLabel = when {
            !live -> "LIVE DATA OFF"
            score < 0 -> "READINESS UNKNOWN"
            score >= 90 -> "READY $score%"
            score >= 75 -> "CHECK $score%"
            else -> "HOLD $score%"
        }
        v267SetLiveText(v267ReadyView, readyLabel, animated)
        if (!live) {
            v267SetLiveText(v267DeviceView, "LIVE SPEC DATA OFF\nEnable in Pro Settings", animated)
            v267SetLiveText(v267SetupView, "QUICK CONTROLS ${if (v267QuickControlsEnabled()) "ON" else "OFF"}\nADVANCED ${if (v267AdvancedSpecsEnabled()) "ON" else "OFF"}", animated)
            v267SetLiveText(v267HealthView, cameraHealthStatus(), animated)
            v267SetLiveText(v267ProjectView, v260ProjectStatus(), animated)
            v267SetLiveText(v267AudioView, "AUDIO • LIVE SPEC DATA OFF", animated)
            v267SetLiveText(v267RecommendationView, "RECOMMENDATION • RUN READY CHECK OR ENABLE LIVE SPEC DATA", animated)
            return
        }
        v267SetLiveText(v267DeviceView, v267DeviceSpecSummary(), animated)
        v267SetLiveText(v267SetupView, v267CurrentSetupSummary(), animated)
        v267SetLiveText(v267HealthView, cameraHealthStatus() + "\nREADY ${if (score >= 0) "$score%" else "UNKNOWN"}", animated)
        v267SetLiveText(v267ProjectView, v260ProjectStatus(), animated)
        v267SetLiveText(v267AudioView, v267AudioSummary(), animated)
        v267SetLiveText(v267RecommendationView, v267Recommendation(score), animated)
    }

    private fun v267SetLiveText(view: TextView?, value: String, animated: Boolean) {
        val target = view ?: return
        val status = value.uppercase(Locale.US)
        val emphasis = listOf("WARNING", "HOLD", "RECORDING", "REC ").any { status.contains(it) }
        if (target.text.toString() == value) {
            DevelopUgandaStatusMotion.setBreathing(this, target, emphasis)
            return
        }
        DevelopUgandaStatusMotion.setBreathing(this, target, false)
        if (!animated || !v267CommandMotionEnabled()) {
            target.text = value
            target.alpha = 1f
            target.scaleX = 1f
            target.scaleY = 1f
            DevelopUgandaStatusMotion.setBreathing(this, target, emphasis)
            return
        }
        target.animate().cancel()
        target.animate()
            .alpha(0f)
            .useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity)
            .withEndAction {
                target.text = value
                target.alpha = 0f
                target.scaleX = 1f
                target.scaleY = 1f
                target.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity)
                    .withEndAction {
                        DevelopUgandaStatusMotion.setBreathing(
                            this@DevelopUgandaGeneralHubActivity,
                            target,
                            emphasis,
                        )
                    }
                    .start()
            }
            .start()
    }

    private fun v267DeviceSpecSummary(): String {
        return try {
            val manager = getSystemService(CAMERA_SERVICE) as CameraManager
            var maxW = 0
            var maxH = 0
            var maxFps = 0
            var back = 0
            var front = 0
            var optical = false
            manager.cameraIdList.forEach { id ->
                val c = manager.getCameraCharacteristics(id)
                when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_BACK -> back++
                    CameraCharacteristics.LENS_FACING_FRONT -> front++
                }
                c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    ?.getOutputSizes(SurfaceTexture::class.java)
                    ?.forEach { size -> if (size.width.toLong() * size.height > maxW.toLong() * maxH) { maxW = size.width; maxH = size.height } }
                c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    ?.forEach { range -> if (range.upper > maxFps) maxFps = range.upper }
                val ois = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                if (ois?.any { it == CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON } == true) optical = true
            }
            val base = "${back}B/${front}F CAM • MAX OUT ${maxW}×${maxH} • AE ${maxFps}FPS"
            if (v267AdvancedSpecsEnabled()) "$base\nOIS ${if (optical) "YES" else "DEVICE"} • API ${Build.VERSION.SDK_INT}" else base
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V267 DEVICE CAPABILITY READ", error)
            "DEVICE CAPS UNKNOWN • READ FAILED\nAPI ${Build.VERSION.SDK_INT}"
        }
    }

    private fun v267CurrentSetupSummary(): String {
        val bitrate = (v257MotionPrefs().getString("bitrate_mode", "STANDARD") ?: "STANDARD").uppercase(Locale.US)
        val preset = (duSharedPreferences("develop_uganda_v254_field_controls", MODE_PRIVATE).getString("shoot_preset", "CUSTOM") ?: "CUSTOM").uppercase(Locale.US)
        val proxy = (duSharedPreferences("develop_uganda_v265_proxy_sync_review", MODE_PRIVATE).getString("proxy_quality", "BALANCED") ?: "BALANCED").uppercase(Locale.US)
        val last = (v267Prefs().getString("last_quick_preset", preset) ?: preset).uppercase(Locale.US)
        return "$last • BITRATE $bitrate\nTC ${timecodeMode()} • PROXY $proxy"
    }

    private fun v267AudioSummary(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return "AUDIO • INPUT ROUTE UNKNOWN"
        return try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            val inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val names = inputs.take(2).map { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB"
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH"
                    AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED"
                    AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILT-IN MIC"
                    else -> device.productName?.toString()?.take(16)?.uppercase(Locale.US) ?: "INPUT"
                }
            }.distinct()
            "AUDIO • ${if (names.isEmpty()) "INPUT ROUTE UNKNOWN" else names.joinToString(" + ")}"
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "V267 AUDIO ROUTE READ", error)
            "AUDIO • INPUT ROUTE UNKNOWN • READ FAILED"
        }
    }

    private fun v267Recommendation(score: Int): String {
        val bat = batteryPercent()
        val free = freeStorageGb()
        val thermal = thermalStatusLabel().removePrefix("THERMAL • ")
        return when {
            free < 0L || bat < 0 || score < 0 -> "RECOMMENDATION • TELEMETRY UNKNOWN • OPEN CAMERA HEALTH"
            free <= 4L -> "RECOMMENDATION • FREE STORAGE FIRST • ${free}GB LEFT"
            bat in 0..12 -> "RECOMMENDATION • CONNECT POWER • BAT ${bat}%"
            thermal in setOf("SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN") -> "RECOMMENDATION • ENDURANCE MODE • THERM $thermal"
            score >= 90 -> "RECOMMENDATION • READY TO SHOOT • ${v260ProjectStatus()}"
            else -> "RECOMMENDATION • RUN READY CHECK • SCORE $score%"
        }
    }

    private fun buildFlowTrack(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        flowTrack = row
        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(row)
        }
        scroll.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = space(7)
        }
        restartFlowMotion()
        return scroll
    }

    private fun restartFlowMotion() {
        val row = flowTrack ?: return
        row.removeAllViews()
        val profile = DevelopUgandaModeProfiles.selected(this)
        val activeId = DevelopUgandaModeProfiles.stageForDetailed(this, v275CurrentStage())?.id
        profile.stages.forEachIndexed { index, stage ->
            val absent = stage.availability == DevelopUgandaStageAvailability.ABSENT
            val active = !absent && stage.id == activeId
            val state = when {
                absent -> "ABSENT"
                active -> "ACTIVE"
                else -> "PENDING"
            }
            row.addView(
                text("${stage.label}\n$state", 7.1f, if (active) ink else muted, true).apply {
                    gravity = Gravity.CENTER
                    isClickable = true
                    isFocusable = true
                    alpha = if (absent) 0.52f else 1f
                    background = rounded(
                        if (active) profile.chromeAccentColor else DevelopUgandaFivemods8Theme.surfaceRaised,
                        line,
                        12,
                        1,
                    )
                    contentDescription = "${profile.shortName}. ${stage.label}. $state. ${stage.nextAction}"
                    setOnClickListener {
                        if (absent) {
                            Toast.makeText(this@DevelopUgandaGeneralHubActivity, "${stage.label} • PROFILE DATA ABSENT", Toast.LENGTH_SHORT).show()
                        } else {
                            v275OpenStage(stage.id)
                        }
                    }
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply {
                    if (index > 0) marginStart = space(4)
                },
            )
        }
    }

    private fun panelBlock(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(14), space(14), space(14), space(14))
            background = rounded(panel, cyan, 22, 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = space(10)
            }
        }

    private fun section(host: LinearLayout, value: String) {
        val label = text(value, 10f, gold, true).apply {
            tag = "du_section_label"
            setPadding(space(2), space(8), 0, space(4))
        }
        sections.add(label)
        host.addView(label)
        label.postDelayed({
            if (workflowMotionEnabled()) {
                label.alpha = 0f
                label.translationX = -dp(8).toFloat()
                label.animate().alpha(1f).translationX(0f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
            }
        }, (sections.size * 45L).coerceAtMost(450L))
    }

    private fun routeCard(
        profile: DevelopUgandaModeProfile,
        liveProvider: (() -> String)? = null,
    ): View {
        val view = routeCard(
            title = profile.displayName,
            detail = if (profile.hasProfileData) {
                "${profile.bestFor} • ${profile.instruction}"
            } else {
                "Established ${profile.shortName} route • additive capture profile data is absent"
            },
            accent = profile.chromeAccentColor,
            destination = profile.destination,
            beforeOpen = { DevelopUgandaModeProfiles.rememberSelected(this, profile.page) },
            liveProvider = liveProvider,
        )
        if (DevelopUgandaModeProfiles.selected(this).page == profile.page) {
            DevelopUgandaBroadcastPresentation.styleActionSurface(
                view,
                DevelopUgandaButtonWeight.PRIMARY,
                profile.chromeAccentColor,
                latched = true,
            )
        }
        return view
    }

    private fun routeCard(
        title: String,
        detail: String,
        accent: Int,
        destination: Class<out Activity>,
        beforeOpen: (() -> Unit)? = null,
        liveProvider: (() -> String)? = null
    ): View {
        val shell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(14), space(11), space(14), space(11))
            background = rounded(card, line, 18, 1)
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $detail"
        }
        DevelopUgandaBroadcastPresentation.styleActionSurface(
            shell,
            DevelopUgandaButtonWeight.SECONDARY,
            accent,
        )

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(text(title, 13f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (DevelopUgandaV270Guidance.guidedHelpEnabled(this)) {
            val info = text("ⓘ", 13f, gold, true).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                contentDescription = "Help for $title"
                setOnClickListener {
                    DevelopUgandaV270Guidance.showFeature(
                        this@DevelopUgandaGeneralHubActivity,
                        title,
                        detail,
                        "Tap the main card to open this tool. Use it at the workflow step where it is listed, then return here for the next step."
                    )
                }
            }
            titleRow.addView(info, LinearLayout.LayoutParams(dp(34), dp(34)))
        }
        val arrow = text("›", 17f, white, true).apply { gravity = Gravity.CENTER }
        titleRow.addView(arrow, LinearLayout.LayoutParams(dp(28), dp(28)))
        shell.addView(titleRow)
        shell.addView(
            text(detail, 8.8f, muted, false).apply { setPadding(0, space(4), 0, 0) }
        )

        val live = if (liveProvider != null) {
            var initialFault: DevelopUgandaV28012RuntimeGuard.UiFault? = null
            val initialLive = try { liveProvider() } catch (t: Throwable) {
                DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "CARD $title", t).also {
                    initialFault = it
                }
                "STATUS • UNKNOWN • READ FAILED"
            }
            text(initialLive, 7.4f, accent, true).apply {
                tag = "v258_live_card_data"
                setPadding(0, space(6), 0, 0)
                visibility = if (liveCardDataEnabled()) View.VISIBLE else View.GONE
                initialFault?.let { fault ->
                    tag = fault
                    setTextColor(muted)
                    alpha = 0.68f
                    isClickable = true
                    setOnClickListener {
                        showFaultDialog(tag as? DevelopUgandaV28012RuntimeGuard.UiFault)
                    }
                }
            }.also { shell.addView(it) }
        } else null

        val accentLine = View(this).apply {
            background = rounded(accent, DevelopUgandaFivemods8Theme.transparent, 50, 0)
            pivotX = 0f
        }
        shell.addView(accentLine, LinearLayout.LayoutParams(space(42), space(2)).apply { topMargin = space(8) })

        fun animatePress(down: Boolean) {
            if (!workflowMotionEnabled()) return
            val scale = when (workflowMotionStyle()) {
                "NORMAL" -> 0.972f
                "REDUCED" -> 0.993f
                else -> 0.983f
            }
            shell.animate().cancel()
            shell.animate()
                .scaleX(if (down) scale else 1f)
                .scaleY(if (down) scale else 1f)
                .alpha(if (down) 0.91f else 1f)
                .useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity)
                .start()
            arrow.animate().cancel()
            arrow.animate().translationX(if (down) dp(5).toFloat() else 0f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
            accentLine.animate().cancel()
            accentLine.animate().scaleX(if (down) 1.42f else 1f).alpha(if (down) 1f else 0.9f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
        }

        shell.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> animatePress(true)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> animatePress(false)
            }
            false
        }
        shell.setOnClickListener {
            lastOpenedCard = shell
            beforeOpen?.invoke()
            if (workflowMotionEnabled()) {
                shell.animate().cancel()
                shell.animate().scaleX(1.008f).scaleY(1.008f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).withEndAction {
                    shell.animate().scaleX(1f).scaleY(1f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
                }.start()
            }
            handler.postDelayed({
                DevelopUgandaV270Guidance.safeOpen(this@DevelopUgandaGeneralHubActivity, destination)
            }, if (workflowMotionEnabled()) 95L else 0L)
        }

        shell.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = space(7)
        }
        DevelopUgandaBroadcastPresentation.styleActionSurface(
            shell,
            DevelopUgandaButtonWeight.SECONDARY,
            accent,
        )

        val refs = CardRefs(title, shell, arrow, accentLine, live, liveProvider, accent)
        cards.add(refs)
        shell.postDelayed({ animateCardIn(refs) }, (cards.size * 38L).coerceAtMost(520L))
        return shell
    }

    private fun animateCardIn(refs: CardRefs) {
        if (!workflowMotionEnabled()) return
        val distance = when (workflowMotionStyle()) {
            "NORMAL" -> dp(15)
            "REDUCED" -> dp(4)
            else -> dp(9)
        }.toFloat()
        refs.shell.alpha = 0f
        refs.shell.translationY = distance
        refs.accent.scaleX = 0.25f
        refs.shell.animate()
            .alpha(1f)
            .translationY(0f)
            .useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity)
            .start()
        refs.accent.animate().scaleX(1f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
    }

    private fun animateEntrance(hero: View) {
        if (!workflowMotionEnabled()) return
        hero.alpha = 0.55f
        hero.translationY = -dp(6).toFloat()
        hero.animate().alpha(1f).translationY(0f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
    }

    private fun refreshLiveCards(animated: Boolean) {
        val enabled = liveCardDataEnabled()
        cards.forEach { refs ->
            val view = refs.live ?: return@forEach
            view.visibility = if (enabled) View.VISIBLE else View.GONE
            if (!enabled) return@forEach
            val provider = refs.liveProvider ?: return@forEach
            val next = try {
                provider().also {
                    view.setTextColor(refs.liveColor)
                    view.alpha = 1f
                    view.setOnClickListener(null)
                    view.isClickable = false
                }
            } catch (error: Exception) {
                val fault = DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "CARD ${refs.title}", error)
                view.setTextColor(muted)
                view.alpha = 0.68f
                view.isClickable = true
                view.tag = fault
                view.setOnClickListener { showFaultDialog(view.tag as? DevelopUgandaV28012RuntimeGuard.UiFault) }
                "STATUS • UNKNOWN • READ FAILED"
            }
            if (view.text.toString() != next) {
                view.text = next
                if (animated && workflowMotionEnabled()) {
                    view.animate().cancel()
                    view.alpha = 0.48f
                    view.translationX = dp(3).toFloat()
                    view.animate().alpha(1f).translationX(0f).useDevelopUgandaMotion(this@DevelopUgandaGeneralHubActivity).start()
                }
            }
        }
    }

    private fun infoCard(title: String, detail: String, accent: Int): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(space(14), space(11), space(14), space(11))
            background = rounded(card, line, 18, 1)
            addView(text(title, 12.5f, accent, true))
            addView(text(detail, 8.8f, muted, false).apply { setPadding(0, space(5), 0, 0) })
        }

    private fun fieldCameraStatus(): String {
        val p = v257MotionPrefs()
        val bitrate = p.getString("bitrate_mode", "STANDARD") ?: "STANDARD"
        val motion = p.getString("home_motion_style", "SUBTLE") ?: "SUBTLE"
        val score = workflowConfidenceScore()
        val smart = if (!v259Prefs().getBoolean("confidence_meter", true)) "SMART OFF"
        else if (score >= 0) "SMART $score%" else "SMART UNKNOWN"
        return "${v260ProjectStatus()} • $smart • BITRATE ${bitrate.uppercase(Locale.US)} • CAMERA MOTION ${motion.uppercase(Locale.US)}"
    }

    private fun modeRouteStatus(profile: DevelopUgandaModeProfile): String {
        val preview = DevelopUgandaFiveModesDiagnostics.snapshot(this)
            .firstOrNull { it.page == profile.page }
        val previewState = preview?.let { "PREVIEW ${it.state}" } ?: "PREVIEW UNKNOWN"
        val spec = if (profile.hasProfileData && profile.frameRate != null && profile.targetBitrateBps != null) {
            "${profile.qualityName} • ${profile.frameRate}FPS • ${profile.targetBitrateBps / 1_000_000}Mbps"
        } else {
            "CAPTURE PROFILE DATA ABSENT"
        }
        val lastTake = DevelopUgandaV274MediaVaultStore.lastClipForMode(this, profile.page)?.let {
            "LAST ${DevelopUgandaV274MediaVaultStore.shortName(it.name)}"
        } ?: "LAST TAKE UNKNOWN"
        val selected = if (DevelopUgandaModeProfiles.selected(this).page == profile.page) " • SELECTED" else ""
        return "$previewState • $spec • $lastTake$selected"
    }

    private fun workflowConfidenceScore(): Int {
        var score = 100
        val battery = batteryPercent()
        val free = freeStorageGb()
        val audio = v267AudioSummary()
        if (battery < 0 || free < 0L || audio.contains("UNKNOWN", true)) return -1
        if (battery in 0..5) score -= 28 else if (battery in 6..15) score -= 14
        if (free <= 1L) score -= 36 else if (free <= 4L) score -= 20 else if (free <= 8L) score -= 9
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return -1
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                score -= when {
                    pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_CRITICAL -> 42
                    pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE -> 28
                    pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE -> 10
                    else -> 0
                }
            } catch (error: Exception) {
                DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "WORKFLOW THERMAL READ", error)
                return -1
            }
        }
        return score.coerceIn(0, 100)
    }

    private fun timecodeMode(): String =
        (v256Prefs().getString("timecode_mode", "REC RUN") ?: "REC RUN").uppercase(Locale.US)

    private fun batteryPercent(): Int {
        return try {
            val manager = getSystemService(BATTERY_SERVICE) as BatteryManager
            val value = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (value !in 0..100) {
                DevelopUgandaV28012RuntimeGuard.recordUiFault(
                    this,
                    "WORKFLOW BATTERY READ",
                    IllegalStateException("Battery capacity unavailable: $value"),
                )
                -1
            } else value
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "WORKFLOW BATTERY READ", error)
            -1
        }
    }

    private fun freeStorageGb(): Long {
        return DevelopUgandaV276RecordingSafety.freeStorageBytesMeasured(this)
            ?.div(1_000_000_000L)
            ?: -1L
    }

    private fun thermalStatusLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "THERMAL • UNKNOWN • API UNSUPPORTED"
        return try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val state = when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "COOL"
                PowerManager.THERMAL_STATUS_LIGHT -> "LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "CRITICAL"
                PowerManager.THERMAL_STATUS_EMERGENCY -> "EMERGENCY"
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "SHUTDOWN"
                else -> "UNKNOWN"
            }
            "THERMAL • $state"
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "WORKFLOW THERMAL LABEL", error)
            "THERMAL • UNKNOWN"
        }
    }

    private fun cameraHealthStatus(): String {
        val bat = batteryPercent().let { if (it >= 0) "$it%" else "UNKNOWN" }
        val thermal = thermalStatusLabel().removePrefix("THERMAL • ")
        val free = freeStorageGb().let { if (it >= 0L) "${it}GB" else "UNKNOWN" }
        return "BAT $bat • FREE $free • THERM $thermal"
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView =
        TextView(this).apply {
            text = value
            DevelopUgandaFivemods8Theme.applyTypeScale(this, size)
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.START
        }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = if (radius >= 50) dp(radius).toFloat() else DevelopUgandaFivemods8Theme.radiusPx.toFloat()
            if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
        }

    private fun space(value: Int): Int = DevelopUgandaFivemods8Theme.spacingPx(value)

    private fun touchMinimum(): Int = DevelopUgandaFivemods8Theme.touchMinimumPx

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
