package com.sentongoharuna.pulse

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
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
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.log10

/**
 * Observes the existing Camera2 capture callback and PreviewView stream state.
 * It adds no use case or surface.  Recovery invokes the Activity's existing
 * session binder and stops after two consecutive failed restarts.
 */
class DevelopUgandaFivemods12PreviewMonitor(
    private val activity: Activity,
    private val page: DevelopUgandaCameraPage,
    private val isRecording: () -> Boolean,
    private val restartSession: () -> Unit,
    private val reportState: (String, Boolean) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var lastFrameElapsedMs = SystemClock.elapsedRealtime()
    @Volatile private var streamReportedIdle = false
    private var running = false
    private var retryCount = 0
    private var readyReported = false
    private var stoppedAfterFailures = false

    private val check = object : Runnable {
        override fun run() {
            if (!running) return
            val ageMs = SystemClock.elapsedRealtime() - lastFrameElapsedMs
            val stalled = streamReportedIdle || ageMs >= FRAME_TIMEOUT_MS
            if (stalled && !isRecording()) {
                if (retryCount < MAX_RESTARTS) {
                    retryCount += 1
                    lastFrameElapsedMs = SystemClock.elapsedRealtime()
                    streamReportedIdle = false
                    val reason = if (ageMs >= FRAME_TIMEOUT_MS) "NO NEW FRAMES" else "BLACK / IDLE PREVIEW"
                    reportState("$reason • RESTART $retryCount/$MAX_RESTARTS", true)
                    DevelopUgandaFiveModesDiagnostics.markPreviewRetry(activity, page, retryCount, MAX_RESTARTS)
                    DevelopUgandaFivemods12SessionLog.recordPreview(activity, page, reason, retryCount)
                    runCatching { restartSession() }.onFailure {
                        DevelopUgandaFivemods12SessionLog.recordError(activity, page, "PREVIEW RESTART", it)
                    }
                } else if (!stoppedAfterFailures) {
                    stoppedAfterFailures = true
                    reportState("PREVIEW UNAVAILABLE • STOPPED AFTER 2 RESTARTS", true)
                    DevelopUgandaFiveModesDiagnostics.markPreviewUnavailable(activity, page)
                    DevelopUgandaFivemods12SessionLog.recordPreview(activity, page, "STOPPED_AFTER_TWO_FAILURES", retryCount)
                }
            }
            main.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    fun onFrame() {
        lastFrameElapsedMs = SystemClock.elapsedRealtime()
        streamReportedIdle = false
        if (!readyReported || retryCount > 0 || stoppedAfterFailures) {
            main.post {
                readyReported = true
                retryCount = 0
                stoppedAfterFailures = false
                reportState("PREVIEW READY", false)
                DevelopUgandaFiveModesDiagnostics.markPreviewReady(activity, page)
                DevelopUgandaFivemods12SessionLog.recordPreview(activity, page, "READY", 0)
            }
        }
    }

    fun onStreamState(state: PreviewView.StreamState) {
        streamReportedIdle = state != PreviewView.StreamState.STREAMING
        if (state == PreviewView.StreamState.STREAMING) onFrame()
    }

    fun start() {
        running = true
        lastFrameElapsedMs = SystemClock.elapsedRealtime()
        main.removeCallbacks(check)
        main.postDelayed(check, FRAME_TIMEOUT_MS)
    }

    fun stop() {
        running = false
        main.removeCallbacks(check)
    }

    companion object {
        const val MAX_RESTARTS = 2
        const val FRAME_TIMEOUT_MS = 4_500L
        private const val CHECK_INTERVAL_MS = 1_500L
    }
}

/**
 * The single camera-status owner for all five modes. The strip is screen-only:
 * outline and text over the preview, never a capture Effect or filled scrim.
 */
object DevelopUgandaFivemods12CameraShell {
    class Controller internal constructor(
        private val activity: AppCompatActivity,
        private val page: DevelopUgandaCameraPage,
        preview: PreviewView,
        private val isRecording: () -> Boolean,
        private val recordingDurationMs: () -> Long,
        private val audioAmplitude: () -> Double?,
        restartSession: () -> Unit,
        private val identity: DevelopUgandaFivemods12ModeIdentity,
        private val stateView: TextView,
        private val modeView: TextView,
        private val formatView: TextView,
        private val codecView: TextView,
        private val timecodeView: DevelopUgandaLiveMetricTextView,
        private val storageView: DevelopUgandaLiveMetricTextView,
        private val batteryView: DevelopUgandaLiveMetricTextView,
        private val audioView: DevelopUgandaLiveMetricTextView,
        private val deviceView: TextView,
    ) {
        private val main = Handler(Looper.getMainLooper())
        private var started = false
        private var broadcastStatus = DevelopUgandaBroadcastStatus(
            format = null,
            codec = null,
            timecode = null,
            battery = null,
            freeStorageGb = null,
            audio = null,
            recording = false,
        )
        private val monitor = DevelopUgandaFivemods12PreviewMonitor(
            activity,
            page,
            isRecording,
            restartSession,
        ) { text, warning ->
            stateView.text = text
            stateView.setTextColor(if (warning) DevelopUgandaFivemods8Theme.warning else DevelopUgandaFivemods8Theme.contentDim)
            DevelopUgandaStatusMotion.setBreathing(activity, stateView, warning)
        }
        private val refresh = object : Runnable {
            override fun run() {
                if (!started) return
                update()
                main.postDelayed(this, 1_000L)
            }
        }

        init {
            preview.previewStreamState.observe(activity) { monitor.onStreamState(it) }
        }

        fun onFrame() = monitor.onFrame()

        fun updateBroadcastStatus(value: DevelopUgandaBroadcastStatus) {
            broadcastStatus = value
            update()
        }

        fun onResume() {
            if (started) return
            started = true
            monitor.start()
            main.post(refresh)
        }

        fun onPause() {
            started = false
            monitor.stop()
            main.removeCallbacks(refresh)
            DevelopUgandaStatusMotion.setBreathing(activity, stateView, false)
        }

        /** Exactly one call site per owned value; ci enforces this inventory. */
        private fun update() {
            val animate = DevelopUgandaBroadcastPresentation.motionLevel(activity) != DevelopUgandaMotionLevel.OFF
            statusText(modeView, "MODE", identity.code, identity.accent)
            statusText(
                formatView,
                "FORMAT",
                broadcastStatus.format ?: DevelopUgandaModeProfiles.forPage(page)
                    .takeIf { it.hasProfileData }
                    ?.let { profile ->
                        listOfNotNull(profile.qualityName, profile.frameRate?.let { "${it}FPS" })
                            .joinToString("/")
                    },
            )
            statusText(codecView, "CODEC", broadcastStatus.codec)

            if (isRecording()) {
                val seconds = recordingDurationMs().coerceAtLeast(0L) / 1_000.0
                bind(timecodeView, "f12_tc_${page.name}", "TIMECODE", seconds, { value ->
                    val whole = value.toLong().coerceAtLeast(0L)
                    String.format(
                        Locale.US,
                        "TC %02d:%02d:%02d",
                        whole / 3_600L,
                        (whole / 60L) % 60L,
                        whole % 60L,
                    )
                }, animate)
            } else {
                statusText(timecodeView, "TC", broadcastStatus.timecode)
            }

            val protection = DevelopUgandaFivemods12RecordingProtection.state(activity, page)
            val measuredFreeStorageGb = broadcastStatus.freeStorageGb
            when {
                protection.storageMinutes != null -> bind(
                    storageView,
                    "f12_space_${page.name}",
                    "STORAGE",
                    protection.storageMinutes.toDouble(),
                    { "TIME ${it.toInt()}m" },
                    animate,
                )
                measuredFreeStorageGb != null -> bind(
                    storageView,
                    "f12_free_${page.name}",
                    "STORAGE",
                    measuredFreeStorageGb.toDouble(),
                    { "FREE ${it.toLong()}GB" },
                    animate,
                )
                else -> statusText(storageView, "FREE", null)
            }
            protection.batteryPercent?.let { value ->
                bind(batteryView, "f12_battery", "BATTERY", value.toDouble(), { "BAT ${it.toInt()}%" }, animate)
            } ?: statusText(batteryView, "BAT", broadcastStatus.battery?.let { "$it%" })
            audioAmplitude()?.takeIf { it.isFinite() && isRecording() }?.let { amplitude ->
                val db = if (amplitude <= 0.000001) -120.0 else 20.0 * log10(amplitude.coerceAtMost(1.0))
                bind(audioView, "f12_audio_${page.name}", "AUDIO", db, { String.format(Locale.US, "AUDIO %.0fdB", it) }, animate)
            } ?: statusText(audioView, "AUDIO", broadcastStatus.audio)

            val warnings = protection.warnings
            deviceView.text = buildString {
                append("THERM ").append(protection.thermalStatus)
                append(" • GPS ").append(DevelopUgandaFivemods12Preflight.shortGpsLabel(activity))
                append(" • NET ").append(DevelopUgandaFivemods12Preflight.shortNetworkLabel(activity))
                if (warnings.isNotEmpty()) append(" • ").append(warnings.joinToString(" • "))
            }
            val warning = warnings.isNotEmpty()
            deviceView.setTextColor(if (warning) DevelopUgandaFivemods8Theme.warning else DevelopUgandaFivemods8Theme.contentDim)
            DevelopUgandaStatusMotion.setBreathing(activity, deviceView, warning)
        }

        private fun bind(
            view: DevelopUgandaLiveMetricTextView,
            id: String,
            label: String,
            value: Double,
            format: (Double) -> String,
            animate: Boolean,
        ) {
            DevelopUgandaMetricReadouts.bind(
                activity,
                view,
                DevelopUgandaMetricReadout(
                    id,
                    label,
                    value,
                    format,
                    "A real device or recording value.",
                    "Open Field Console for the related control.",
                    DevelopUgandaMetricOrigin.MEASURED,
                    "Android platform / current recorder status",
                ),
                animate,
            )
        }

        private fun statusText(
            view: TextView,
            label: String,
            value: String?,
            color: Int = DevelopUgandaFivemods8Theme.content,
        ) {
            view.animate().cancel()
            view.visibility = View.VISIBLE
            val present = value?.takeIf { it.isNotBlank() }
            view.text = "$label ${present ?: "UNKNOWN"}"
            view.setTextColor(if (present == null) DevelopUgandaFivemods8Theme.contentDim else color)
            view.contentDescription = view.text
        }
    }

    fun attach(
        activity: AppCompatActivity,
        root: FrameLayout,
        preview: PreviewView,
        page: DevelopUgandaCameraPage,
        isRecording: () -> Boolean,
        recordingDurationMs: () -> Long,
        audioAmplitude: () -> Double?,
        restartSession: () -> Unit,
    ): Controller {
        val identity = DevelopUgandaFivemods12Identity.forPage(page)
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            background = rounded(DevelopUgandaFivemods8Theme.transparent, identity.accent, dp(2), dp(1))
            elevation = 0f
            tag = "f12_status_strip"
        }
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val badge = TextView(activity).apply {
            text = identity.badge.uppercase(Locale.US)
            setTextColor(identity.accent)
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 11f)
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.06f
            maxLines = 1
            isSingleLine = true
            isClickable = true
            isFocusable = true
            tag = "f12_identity_badge"
            contentDescription = "${identity.name} identity. Open Field Console."
            setOnClickListener {
                activity.startActivity(Intent(activity, DevelopUgandaFivemods12FieldConsoleActivity::class.java).putExtra(DevelopUgandaFivemods12FieldConsoleActivity.EXTRA_MODE, page.name))
            }
        }
        val state = TextView(activity).apply {
            text = "PREVIEW CHECKING"
            setTextColor(DevelopUgandaFivemods8Theme.contentDim)
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 11f)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END
            maxLines = 1
            isSingleLine = true
        }
        header.addView(badge, LinearLayout.LayoutParams(0, dp(22), 1f))
        header.addView(state, LinearLayout.LayoutParams(0, dp(22), 1f))
        panel.addView(header)

        fun metric(): DevelopUgandaLiveMetricTextView = DevelopUgandaLiveMetricTextView(activity).apply {
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 11f)
            setTextColor(DevelopUgandaFivemods8Theme.content)
            typeface = Typeface.MONOSPACE
            gravity = Gravity.START
            setPadding(dp(2), 0, dp(2), 0)
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(6, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        val mode = metric()
        val format = metric()
        val codec = metric()
        val timecode = metric().apply { tag = "record_tally" }
        val storage = metric()
        val battery = metric()
        val audio = metric()
        fun row(vararg views: View): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEach { addView(it, LinearLayout.LayoutParams(0, dp(22), 1f)) }
        }
        if (activity.resources.configuration.fontScale >= 1.3f) {
            panel.addView(row(mode, format))
            panel.addView(row(codec, timecode))
            panel.addView(row(storage, battery))
            panel.addView(row(audio))
        } else {
            panel.addView(row(mode, format, codec, timecode))
            panel.addView(row(storage, battery, audio))
        }
        val device = TextView(activity).apply {
            text = "THERM UNKNOWN • GPS UNKNOWN • NET UNKNOWN"
            setTextColor(DevelopUgandaFivemods8Theme.contentDim)
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 11f)
            typeface = Typeface.MONOSPACE
            maxLines = 1
            isSingleLine = true
            setAutoSizeTextTypeUniformWithConfiguration(6, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }
        panel.addView(device, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(22)))
        root.addView(
            panel,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
                leftMargin = dp(6)
                rightMargin = dp(6)
                topMargin = DevelopUgandaFivemods8Theme.spacingUnitPx * 3
            },
        )
        panel.setOnApplyWindowInsetsListener { view, insets ->
            (view.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                params.topMargin = maxOf(dp(6), insets.systemWindowInsetTop + dp(4))
                view.layoutParams = params
            }
            insets
        }
        return Controller(
            activity,
            page,
            preview,
            isRecording,
            recordingDurationMs,
            audioAmplitude,
            restartSession,
            identity,
            state,
            mode,
            format,
            codec,
            timecode,
            storage,
            battery,
            audio,
            device,
        ).also { it.onResume() }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(fill)
        setStroke(strokeWidth.coerceAtLeast(1), stroke)
    }
}

/** Floating first-page status. It wraps but never resizes or reorders the existing home. */
object DevelopUgandaFivemods12HomeShell {
    fun wrap(activity: AppCompatActivity, existing: View): View {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density + 0.5f).toInt()
        val host = FrameLayout(activity).apply { setBackgroundColor(DevelopUgandaFivemods8Theme.surface) }
        host.addView(existing, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val card = TextView(activity).apply {
            setPadding(dp(8), dp(6), dp(8), dp(6))
            setTextColor(DevelopUgandaFivemods8Theme.content)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.START
            isClickable = true
            isFocusable = true
            elevation = 0f
            contentDescription = "FIVEMODS 12 field status. Open Field Console."
            setOnClickListener { activity.startActivity(Intent(activity, DevelopUgandaFivemods12FieldConsoleActivity::class.java)) }
        }
        val identity = DevelopUgandaFivemods12Identity.forPage(DevelopUgandaModeProfiles.selected(activity).page)
        card.background = rounded(DevelopUgandaFivemods8Theme.surface, identity.accent, DevelopUgandaFivemods8Theme.radiusPx, dp(1))
        host.addView(card, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            leftMargin = dp(12)
            rightMargin = dp(12)
            bottomMargin = dp(12)
        })
        val main = Handler(Looper.getMainLooper())
        val refresh = object : Runnable {
            override fun run() {
                if (!card.isAttachedToWindow) return
                val profile = DevelopUgandaModeProfiles.selected(activity)
                val id = DevelopUgandaFivemods12Identity.forPage(profile.page)
                val preflight = DevelopUgandaFivemods12Preflight.snapshot(activity, profile.page, log = false)
                val protection = DevelopUgandaFivemods12RecordingProtection.state(activity, profile.page)
                val recent = DevelopUgandaV274MediaVaultStore.lastClipSummary(activity)
                card.text = buildString {
                    append(id.badge).append(" • ").append(preflight.summary)
                    append("\nSPACE ").append(protection.storageMinutes?.let { "$it MIN" } ?: "UNKNOWN")
                    append(" • BAT ").append(protection.batteryPercent?.let { "$it%" } ?: "UNKNOWN")
                    append(" • THERM ").append(protection.thermalStatus)
                    append("\nGPS ").append(DevelopUgandaFivemods12Preflight.shortGpsLabel(activity))
                    append(" • NET ").append(DevelopUgandaFivemods12Preflight.shortNetworkLabel(activity))
                    append("\n").append(recent)
                }
                main.postDelayed(this, 4_000L)
            }
        }
        card.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                main.removeCallbacks(refresh)
                main.post(refresh)
            }
            override fun onViewDetachedFromWindow(v: View) {
                main.removeCallbacks(refresh)
            }
        })
        return host
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(fill)
        setStroke(strokeWidth.coerceAtLeast(1), stroke)
    }
}

class DevelopUgandaFivemods12FieldConsoleActivity : AppCompatActivity() {
    companion object { const val EXTRA_MODE = "develop_uganda_fivemods12_mode" }

    private val ink = DevelopUgandaFivemods8Theme.surface
    private val cardColor = DevelopUgandaFivemods8Theme.surfaceRaised
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val line = DevelopUgandaFivemods8Theme.outline
    private lateinit var page: LinearLayout
    private lateinit var batteryMetric: DevelopUgandaLiveMetricTextView
    private lateinit var storageMetric: DevelopUgandaLiveMetricTextView
    private lateinit var deviceMetric: TextView
    private val main = Handler(Looper.getMainLooper())
    private var firstResume = true
    private var selected = DevelopUgandaCameraPage.MAIN
    private val liveRefresh = object : Runnable {
        override fun run() {
            if (!::batteryMetric.isInitialized || !batteryMetric.isAttachedToWindow) return
            refreshLiveValues()
            main.postDelayed(this, 2_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected = intent.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { DevelopUgandaCameraPage.valueOf(it) }.getOrNull() }
            ?: DevelopUgandaModeProfiles.selected(this).page
        setContentView(buildPage())
    }

    override fun onResume() {
        super.onResume()
        if (!firstResume) rebuildPreflight()
        firstResume = false
        main.removeCallbacks(liveRefresh)
        main.post(liveRefresh)
    }

    override fun onPause() {
        main.removeCallbacks(liveRefresh)
        super.onPause()
    }

    private fun buildPage(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ink)
            isFillViewport = true
            clipToPadding = false
        }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(10), dp(10), dp(20))
        }
        scroll.addView(page, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        page.addView(text("FIVEMODS 12 • FIELD CONSOLE", 13f, white, true).apply { tag = "du_page_title" })
        page.addView(text("Reliability, recovery, identity and readable live state. Values are measured, LIMITED, UNAVAILABLE or UNKNOWN.", 11f, muted, false).apply { setPadding(0, dp(3), 0, dp(6)) })
        addModeStrip()
        addLiveSignals()
        rebuildPreflight()
        addRecovery()
        addMediaState()
        addSettingsState()
        addDiagnostics()
        addIdentityOutputReport()
        return scroll
    }

    private fun addModeStrip() {
        section("CAMERA IDENTITY")
        val scroller = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        DevelopUgandaFivemods12Identity.all().forEachIndexed { index, identity ->
            val active = identity.page == selected
            val button = Button(this).apply {
                text = "${identity.iconLabel}\n${identity.code}"
                textSize = 11f
                isAllCaps = false
                setTextColor(if (active) DevelopUgandaFivemods8Theme.surface else white)
                background = rounded(if (active) identity.accent else cardColor, identity.accent, 2, 1)
                setOnClickListener {
                    selected = identity.page
                    DevelopUgandaModeProfiles.rememberSelected(this@DevelopUgandaFivemods12FieldConsoleActivity, selected)
                    recreate()
                }
            }
            row.addView(button, LinearLayout.LayoutParams(dp(92), dp(58)).apply { if (index > 0) leftMargin = dp(6) })
        }
        scroller.addView(row)
        page.addView(scroller)
        val identity = DevelopUgandaFivemods12Identity.forPage(selected)
        page.addView(card(identity.badge, "Fixed code, existing mode name, distinct accent and icon. Tap another identity to inspect its preflight.", identity.accent))
    }

    private fun addLiveSignals() {
        section("LIVE DEVICE SIGNALS")
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        batteryMetric = liveMetric("BATTERY UNKNOWN")
        storageMetric = liveMetric("STORAGE TIME UNKNOWN")
        row.addView(batteryMetric, LinearLayout.LayoutParams(0, dp(58), 1f))
        row.addView(storageMetric, LinearLayout.LayoutParams(0, dp(58), 1f).apply { leftMargin = dp(6) })
        page.addView(row)
        deviceMetric = text("TEMPERATURE UNKNOWN\nGPS UNKNOWN\nNETWORK UNKNOWN", 11f, muted, false)
        page.addView(card("LIVE STATE", "", line).apply { addView(deviceMetric) })
    }

    private fun rebuildPreflight() {
        if (!::page.isInitialized) return
        val old = page.findViewWithTag<ViewGroup>("f12_preflight_block")
        val index = old?.let { page.indexOfChild(it) } ?: -1
        if (old != null) page.removeView(old)
        val block = LinearLayout(this).apply {
            tag = "f12_preflight_block"
            orientation = LinearLayout.VERTICAL
        }
        block.addView(text("FIVE-MODE PREFLIGHT", 11f, DevelopUgandaFivemods12Identity.forPage(selected).accent, true).apply { tag = "du_section_label"; setPadding(dp(2), dp(8), 0, dp(4)) })
        val snapshot = DevelopUgandaFivemods12Preflight.snapshot(this, selected)
        block.addView(card("${DevelopUgandaFivemods12Identity.forPage(selected).badge} • ${snapshot.summary}", if (snapshot.mayRecord) "No critical local-recording block." else "Resolve every UNAVAILABLE critical item before REC.", DevelopUgandaFivemods12Identity.forPage(selected).accent))
        snapshot.items.forEach { item -> block.addView(preflightCard(item)) }
        if (index >= 0) page.addView(block, index) else page.addView(block)
    }

    private fun preflightCard(item: DevelopUgandaFivemods12PreflightItem): View {
        val accent = when (item.state) {
            DevelopUgandaFivemods12Availability.READY -> DevelopUgandaFivemods8Theme.accent
            DevelopUgandaFivemods12Availability.LIMITED -> DevelopUgandaFivemods8Theme.warning
            DevelopUgandaFivemods12Availability.UNAVAILABLE -> DevelopUgandaFivemods8Theme.record
            DevelopUgandaFivemods12Availability.UNKNOWN -> muted
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = rounded(cardColor, accent, 2, 1)
            addView(text("${item.label} • ${item.stateLabel}", 13f, white, true))
            addView(text(item.reason, 11f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
            if (item.fix != DevelopUgandaFivemods12Fix.NONE) {
                addView(button(fixLabel(item.fix), accent) { runFix(item.fix) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(4) })
            }
        }.also { pageStyleMargin(it) }
    }

    private fun addRecovery() {
        section("RECOVERY")
        val results = DevelopUgandaCrashSafeTake.recoveryResults(this)
        if (results.isEmpty()) {
            page.addView(card("FILES FOUND • 0", "No interrupted take is awaiting review. Originals are never deleted by recovery.", line))
        } else {
            results.forEach { result ->
                val state = DevelopUgandaFivemods12Recovery.state(result)
                val identity = DevelopUgandaFivemods12Identity.forModeText(result.takeId)
                DevelopUgandaFivemods12SessionLog.recordRecovery(this, state, result.playableSegments, result.unfinishedSegments)
                page.addView(card(
                    "${identity?.badge ?: "MODE UNKNOWN"} • ${state.label}",
                    "RECOVERY 100% • PLAYABLE ${result.playableSegments} • UNFINISHED ${result.unfinishedSegments} • RAW PRESERVED ${result.rawFragmentsPreserved}\nOriginals remain untouched until any recovery copy is independently verified.",
                    if (state == DevelopUgandaFivemods12RecoveryState.RECOVERED) DevelopUgandaFivemods8Theme.accent else DevelopUgandaFivemods8Theme.warning,
                ))
            }
        }
        page.addView(button("OPEN RECOVERY CENTER", DevelopUgandaFivemods8Theme.accent) {
            startActivity(Intent(this, DevelopUgandaV276RecoveryCenterActivity::class.java))
        })
    }

    private fun addMediaState() {
        section("STORY + MEDIA / VAULT")
        val clips = DevelopUgandaV274MediaVaultStore.clips(this)
        val duration = clips.sumOf { it.durationMs.coerceAtLeast(0L) }
        val bytes = clips.sumOf { it.bytes.coerceAtLeast(0L) }
        val queue = DevelopUgandaDeliveryQueue.items(this)
        val delivered = queue.count { it.state == DevelopUgandaDeliveryState.DELIVERED }
        val sentBytes = queue.sumOf { it.bytesSent.coerceAtLeast(0L) }
        val totalBytes = queue.sumOf { it.bytesTotal.coerceAtLeast(0L) }
        val uploadProgress = if (totalBytes > 0L) {
            val percent = ((sentBytes.toDouble() / totalBytes.toDouble()) * 100.0).coerceIn(0.0, 100.0)
            String.format(Locale.US, "%.0f%% • %s / %s", percent, formatBytes(sentBytes), formatBytes(totalBytes))
        } else {
            "UNKNOWN • no measurable queued bytes"
        }
        val identityCounts = DevelopUgandaCameraPage.values().joinToString(" • ") { mode ->
            "${DevelopUgandaFivemods12Identity.forPage(mode).code} ${clips.count { it.mode == mode }}"
        }
        page.addView(card(
            "CLIPS ${clips.size} • ${formatDuration(duration)}",
            "STORAGE ${formatBytes(bytes)} • DELIVERY $delivered/${queue.size}\nUPLOAD $uploadProgress\n$identityCounts",
            DevelopUgandaFivemods12Identity.forPage(selected).accent,
        ))
        page.addView(button("OPEN MEDIA VAULT", DevelopUgandaFivemods8Theme.accent) {
            startActivity(Intent(this, DevelopUgandaV274MediaVaultActivity::class.java))
        })
    }

    private fun addSettingsState() {
        section("SETTINGS STATE")
        val motion = DevelopUgandaBroadcastPresentation.motionLevel(this)
        page.addView(settingCard("MOTION LEVEL", motion.name, if (motion == DevelopUgandaMotionLevel.OFF) "OFF because the app setting or phone Remove animations setting is off." else "Real changing digits roll; UNKNOWN and UNAVAILABLE stay still."))
        page.addView(settingCard("HEALTH MONITOR", onOff(DevelopUgandaV276RecordingSafety.bool(this, "health_monitor", true)), "Battery, measured storage and Android thermal status."))
        page.addView(settingCard("SESSION JOURNAL", onOff(DevelopUgandaV276RecordingSafety.bool(this, "session_journal", true)), "Interrupted-session state for recovery; CLEAN media is never rewritten."))
        page.addView(settingCard("FINALIZE VERIFICATION", onOff(DevelopUgandaV276RecordingSafety.bool(this, "finalize_verify", true)), "Checks URI, non-empty bytes and duration before CLIP SAFE."))
        page.addView(settingCard("PREVIEW RECOVERY", "ON • 2 RETRIES MAX", "Restarts only this mode's camera session after real capture frames stop; never while recording."))
    }

    private fun addDiagnostics() {
        section("DIAGNOSTICS")
        page.addView(card("SESSION LOG • ${DevelopUgandaFivemods12SessionLog.events(this).length()} EVENTS", "Preflight states, recoveries, preview restarts and error classes only. No names, coordinates, media URIs, endpoints, stream keys or exception messages.", line))
        page.addView(button("EXPORT PRIVACY-SAFE BUG REPORT", DevelopUgandaFivemods8Theme.accent) {
            val uri = DevelopUgandaFivemods12SessionLog.export(this)
            Toast.makeText(this, if (uri == null) "DIAGNOSTICS EXPORT FAILED" else "DIAGNOSTICS SAVED TO DOWNLOADS", Toast.LENGTH_LONG).show()
        })
    }

    private fun addIdentityOutputReport() {
        section("OUTPUT IDENTITY")
        page.addView(card(
            "CLEAN • BYTES UNTOUCHED",
            "Identity is carried by the mode-code filename, crash-safe manifest and verified JSON sidecar only.",
            DevelopUgandaFivemods8Theme.accent,
        ))
        page.addView(card(
            "BRAND • EXISTING OVERLAY PATH",
            "The one-pass reviewed BRAND exporter burns CODE + EXISTING MODE NAME. Captions remain reviewed-only. The RTMPS network encoder has no approved identity overlay hook; its local output remains CLEAN and is identified by filename/sidecar rather than changing that encoder.",
            DevelopUgandaFivemods8Theme.warning,
        ))
    }

    private fun refreshLiveValues() {
        val animate = DevelopUgandaBroadcastPresentation.motionLevel(this) != DevelopUgandaMotionLevel.OFF
        val protection = DevelopUgandaFivemods12RecordingProtection.state(this, selected)
        protection.batteryPercent?.let { value ->
            bindMetric(batteryMetric, "field_battery", value.toDouble(), { "BATTERY ${it.toInt()}%" }, animate)
        } ?: still(batteryMetric, "BATTERY UNKNOWN")
        protection.storageMinutes?.let { value ->
            bindMetric(storageMetric, "field_storage_${selected.name}", value.toDouble(), { "STORAGE ${it.toInt()} MIN" }, animate)
        } ?: still(storageMetric, "STORAGE TIME UNKNOWN")
        deviceMetric.text = "TEMPERATURE ${protection.thermalStatus}\nGPS ${DevelopUgandaFivemods12Preflight.shortGpsLabel(this)}\nNETWORK ${DevelopUgandaFivemods12Preflight.shortNetworkLabel(this)}"
    }

    private fun bindMetric(view: DevelopUgandaLiveMetricTextView, id: String, value: Double, format: (Double) -> String, animate: Boolean) {
        DevelopUgandaMetricReadouts.bind(
            this,
            view,
            DevelopUgandaMetricReadout(id, id, value, format, "Live measured value.", "Use the related preflight control if limited.", DevelopUgandaMetricOrigin.MEASURED, "Android platform"),
            animate,
        )
    }

    private fun still(view: TextView, value: String) {
        view.animate().cancel()
        view.text = value
        view.setTextColor(muted)
    }

    private fun runFix(fix: DevelopUgandaFivemods12Fix) {
        if (fix == DevelopUgandaFivemods12Fix.CONNECT_POWER || fix == DevelopUgandaFivemods12Fix.COOL_DEVICE) {
            val instruction = if (fix == DevelopUgandaFivemods12Fix.CONNECT_POWER) {
                "Connect a charger, then tap the mode again to recheck the measured battery."
            } else {
                "Stop recording, remove the case if safe, move out of heat, then tap the mode again to recheck Android thermal status."
            }
            Toast.makeText(this, instruction, Toast.LENGTH_LONG).show()
            rebuildPreflight()
            return
        }
        val request: Array<String> = when (fix) {
            DevelopUgandaFivemods12Fix.REQUEST_CAMERA -> arrayOf(Manifest.permission.CAMERA)
            DevelopUgandaFivemods12Fix.REQUEST_MICROPHONE -> arrayOf(Manifest.permission.RECORD_AUDIO)
            DevelopUgandaFivemods12Fix.REQUEST_LOCATION -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            else -> emptyArray()
        }
        if (request.isNotEmpty()) ActivityCompat.requestPermissions(this, request, 1212)
        else DevelopUgandaFivemods12Preflight.openFix(this, fix)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1212) rebuildPreflight()
    }

    private fun section(value: String) {
        page.addView(text(value, 11f, DevelopUgandaFivemods12Identity.forPage(selected).accent, true).apply { tag = "du_section_label"; setPadding(dp(2), dp(8), 0, dp(4)) })
    }

    private fun card(title: String, detail: String, accent: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(6), dp(8), dp(6))
        background = rounded(cardColor, accent, 2, 1)
        addView(text(title, 13f, white, true))
        if (detail.isNotBlank()) addView(text(detail, 11f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        pageStyleMargin(this)
    }

    private fun settingCard(title: String, state: String, detail: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(8), dp(6), dp(6), dp(6))
        background = rounded(cardColor, DevelopUgandaFivemods8Theme.accent, 2, 1)
        val copy = LinearLayout(this@DevelopUgandaFivemods12FieldConsoleActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(title, 13f, white, true))
            addView(text(detail, 11f, muted, false).apply { setPadding(0, dp(2), 0, 0) })
        }
        addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(text(state, 11f, if (state.startsWith("ON") || state == "STANDARD" || state == "FULL") DevelopUgandaFivemods8Theme.surface else white, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(5), dp(8), dp(5))
            background = rounded(if (state.startsWith("ON") || state == "STANDARD" || state == "FULL") DevelopUgandaFivemods8Theme.accent else ink, DevelopUgandaFivemods8Theme.accent, 2, 1)
        })
        pageStyleMargin(this)
    }

    private fun button(label: String, accent: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 11f
        isAllCaps = false
        setTextColor(DevelopUgandaFivemods8Theme.surface)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(accent, accent, 2, 1)
        setOnClickListener { action() }
    }

    private fun liveMetric(value: String) = DevelopUgandaLiveMetricTextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(white)
        typeface = Typeface.MONOSPACE
        gravity = Gravity.CENTER
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = rounded(cardColor, line, 2, 1)
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun pageStyleMargin(view: View) {
        view.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int, strokeDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = DevelopUgandaFivemods8Theme.radiusPx.toFloat()
        setColor(fill)
        setStroke(dp(strokeDp).coerceAtLeast(1), stroke)
    }

    private fun fixLabel(fix: DevelopUgandaFivemods12Fix): String = when (fix) {
        DevelopUgandaFivemods12Fix.REQUEST_CAMERA -> "ALLOW CAMERA"
        DevelopUgandaFivemods12Fix.REQUEST_MICROPHONE -> "ALLOW MICROPHONE"
        DevelopUgandaFivemods12Fix.REQUEST_LOCATION -> "ALLOW LOCATION"
        DevelopUgandaFivemods12Fix.OPEN_APP_PERMISSIONS -> "OPEN APP PERMISSIONS"
        DevelopUgandaFivemods12Fix.OPEN_LOCATION_SETTINGS -> "OPEN LOCATION SETTINGS"
        DevelopUgandaFivemods12Fix.OPEN_NETWORK_SETTINGS -> "OPEN NETWORK SETTINGS"
        DevelopUgandaFivemods12Fix.FREE_STORAGE -> "OPEN STORAGE SETTINGS"
        DevelopUgandaFivemods12Fix.CONNECT_POWER -> "CONNECT POWER, THEN RECHECK"
        DevelopUgandaFivemods12Fix.COOL_DEVICE -> "COOL PHONE, THEN RECHECK"
        DevelopUgandaFivemods12Fix.NONE -> "NO ACTION"
    }

    private fun onOff(value: Boolean) = if (value) "ON" else "OFF"

    private fun formatDuration(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1_000L
        return String.format(Locale.US, "%02d:%02d:%02d", total / 3_600L, (total / 60L) % 60L, total % 60L)
    }

    private fun formatBytes(bytes: Long): String = String.format(Locale.US, "%.1fGB", bytes.coerceAtLeast(0L) / 1_073_741_824.0)

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
