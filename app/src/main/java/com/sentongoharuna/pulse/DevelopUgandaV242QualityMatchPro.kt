package com.sentongoharuna.pulse

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

/**
 * V242 Cinema Pro operator layer.
 *
 * It never replaces or re-parents the CameraX preview.
 * It uses the existing V238 buttons and the existing real zoom SeekBar.
 */
object DevelopUgandaV242QualityMatchPro {

    private const val TAG = "develop_uganda_v242_quality_match_pro"
    private const val LUT_TAG = "develop_uganda_v235_live_grade_panel"
    private const val ASPECT_TAG = "develop_uganda_v236_field_intelligence"
    private const val TOOLS_TAG = "develop_uganda_v233_tools_chip"
    private const val ZOOM_TAG = "v238_zoom_seek"

    fun attach(
        activity: DevelopUgandaCameraActivity,
        root: FrameLayout
    ) {
        if (root.findViewWithTag<View>(TAG) != null) return
        Controller(activity, root).attach()
    }

    private class Controller(
        private val activity: DevelopUgandaCameraActivity,
        private val root: FrameLayout
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private lateinit var marker: View
        private lateinit var settingsTab: Button
        private lateinit var drawer: LinearLayout
        private lateinit var scrim: View
        private lateinit var zoomRuler: ZoomRulerView
        private lateinit var exposureRuler: ExposureRulerView
        private lateinit var v250LeftEdgeZone: View
        private lateinit var v250RightEdgeZone: View
        private val v250EdgeHandler = Handler(Looper.getMainLooper())
        private val v250HideRulers = Runnable {
            v250Cam2DismissAll(animated = true)
        }

        private val tick = object : Runnable {
            override fun run() {
                if (activity.isFinishing || activity.isDestroyed) return

                root.findViewWithTag<View>(TOOLS_TAG)?.visibility = View.GONE
                root.findViewWithTag<View>("v237_camera_zoom_row")?.visibility = View.GONE
                root.findViewWithTag<View>("v237_camera_identity_row")?.visibility = View.GONE
                root.findViewWithTag<View>("v237_camera_settings_summary")?.visibility = View.GONE
                root.findViewWithTag<View>(LUT_TAG)?.visibility = View.VISIBLE
                root.findViewWithTag<View>(ASPECT_TAG)?.visibility = View.VISIBLE

                if (zoomRuler.visibility == View.VISIBLE) {
                    zoomRuler.syncFromSeek()
                }
                if (exposureRuler.visibility == View.VISIBLE) {
                    exposureRuler.syncFromCamera()
                }
                (root.findViewWithTag<View>("v238_exposure_seek")?.parent as? View)?.visibility = View.GONE
                handler.postDelayed(this, 250L)
            }
        }

        fun attach() {
            marker = View(activity).apply {
                tag = TAG
                visibility = View.GONE
            }
            root.addView(marker, FrameLayout.LayoutParams(1, 1))

            buildScrim()
            buildDrawer()
            buildSettingsTab()
            buildZoomRuler()
            buildExposureRuler()
            buildV250EdgeRevealZones()
            handleV275DeepLink()

            marker.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) {
                        handler.removeCallbacksAndMessages(null)
                    }
                }
            )

            handler.post(tick)
        }

        private fun handleV275DeepLink() {
            val intent = activity.intent ?: return
            val openSettings = intent.getBooleanExtra("v275_open_pro_settings", false)
            val control = intent.getStringExtra("v275_open_control")?.trim()?.uppercase() ?: ""
            if (!openSettings && control.isBlank()) return
            intent.removeExtra("v275_open_pro_settings")
            intent.removeExtra("v275_open_control")
            handler.postDelayed({
                when (control) {
                    "LUTS" -> root.findViewWithTag<View>(LUT_TAG)?.performClick()
                    "ASPECT" -> root.findViewWithTag<View>(ASPECT_TAG)?.performClick()
                    else -> setDrawer(true)
                }
            }, 260L)
        }

        private fun buildScrim() {
            scrim = View(activity).apply {
                setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceScrim(85))
                visibility = View.GONE
                setOnClickListener { setDrawer(false) }
            }

            root.addView(
                scrim,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
        }

        private fun buildSettingsTab() {
            settingsTab = proButton("PRO\nSETTINGS ▸").apply {
                tag = "v242_settings_tab"
                setOnClickListener { setDrawer(true) }
            }

            root.addView(
                settingsTab,
                FrameLayout.LayoutParams(
                    dp(92),
                    dp(52)
                ).apply {
                    gravity = Gravity.END or Gravity.TOP
                    topMargin = dp(78)
                    rightMargin = dp(7)
                }
            )
        }

        private fun v258WorkflowPrefs() =
            activity.duSharedPreferences(
                "develop_uganda_v258_live_workflow_home",
                android.content.Context.MODE_PRIVATE
            )

        private fun v258WorkflowMotionEnabled(): Boolean =
            DevelopUgandaMotionPreferences.level(activity) != DevelopUgandaMotionLevel.OFF

        private fun v258ToggleWorkflowMotion() {
            val next = if (v258WorkflowMotionEnabled()) DevelopUgandaMotionLevel.OFF else DevelopUgandaMotionLevel.STANDARD
            DevelopUgandaMotionPreferences.setLevel(activity, next)
            Toast.makeText(activity, "WORKFLOW MOTION • ${next.name}", Toast.LENGTH_SHORT).show()
        }

        private fun v258WorkflowMotionStyle(): String =
            DevelopUgandaMotionPreferences.level(activity).name

        private fun v258SetWorkflowMotionStyle(value: String) {
            val next = when (value.trim().uppercase()) {
                "NORMAL", "FULL" -> DevelopUgandaMotionLevel.FULL
                "REDUCED", "SUBTLE", "STANDARD" -> DevelopUgandaMotionLevel.STANDARD
                "OFF" -> DevelopUgandaMotionLevel.OFF
                else -> DevelopUgandaMotionLevel.STANDARD
            }
            DevelopUgandaMotionPreferences.setLevel(activity, next)
            Toast.makeText(activity, "WORKFLOW MOTION • ${next.name}", Toast.LENGTH_SHORT).show()
        }

        private fun v258LiveCardDataEnabled(): Boolean =
            v258WorkflowPrefs().getBoolean("live_card_data", true)

        private fun v258ToggleLiveCardData() {
            val next = !v258LiveCardDataEnabled()
            v258WorkflowPrefs().edit().putBoolean("live_card_data", next).apply()
            Toast.makeText(activity, if (next) "LIVE WORKFLOW DATA ON" else "LIVE WORKFLOW DATA OFF", Toast.LENGTH_SHORT).show()
        }

        private fun v258FlowPathMotionEnabled(): Boolean =
            v258WorkflowPrefs().getBoolean("flow_path_motion", true)

        private fun v258ToggleFlowPathMotion() {
            val next = !v258FlowPathMotionEnabled()
            v258WorkflowPrefs().edit().putBoolean("flow_path_motion", next).apply()
            Toast.makeText(activity, if (next) "FLOW PATH MOTION ON" else "FLOW PATH MOTION OFF", Toast.LENGTH_SHORT).show()
        }

        private fun v258WorkflowHomeStatus(): String =
            "WORKFLOW ${DevelopUgandaMotionPreferences.level(activity).name} • LIVE DATA ${if (v258LiveCardDataEnabled()) "ON" else "OFF"} • FLOW ${if (v258FlowPathMotionEnabled()) "ON" else "OFF"}"

        private fun v267SpecPrefs() =
            activity.duSharedPreferences(
                "develop_uganda_v267_live_spec_command_center",
                android.content.Context.MODE_PRIVATE
            )

        private fun v267SpecBool(key: String, defaultValue: Boolean): Boolean =
            v267SpecPrefs().getBoolean(key, defaultValue)

        private fun v267ToggleSpecBool(key: String, defaultValue: Boolean, onLabel: String, offLabel: String) {
            val next = !v267SpecBool(key, defaultValue)
            v267SpecPrefs().edit().putBoolean(key, next).apply()
            Toast.makeText(activity, if (next) onLabel else offLabel, Toast.LENGTH_SHORT).show()
        }

        private fun v267SpecStatus(): String =
            "LIVE ${if (v267SpecBool("live_spec_data", true)) "ON" else "OFF"} • AUTO ${if (v267SpecBool("auto_refresh", true)) "ON" else "OFF"} • QUICK ${if (v267SpecBool("quick_controls", true)) "ON" else "OFF"} • MOTION ${if (v267SpecBool("command_motion", true)) "ON" else "OFF"} • ADV ${if (v267SpecBool("advanced_specs", true)) "ON" else "OFF"}"


        private fun v269StoryPrefs() =
            activity.duSharedPreferences(
                DevelopUgandaV269StoryDeskStore.PREFS_NAME,
                android.content.Context.MODE_PRIVATE
            )

        private fun v269StoryBool(key: String, defaultValue: Boolean): Boolean =
            v269StoryPrefs().getBoolean(key, defaultValue)

        private fun v269ToggleStoryBool(key: String, defaultValue: Boolean, onLabel: String, offLabel: String) {
            val next = !v269StoryBool(key, defaultValue)
            v269StoryPrefs().edit().putBoolean(key, next).apply()
            Toast.makeText(activity, if (next) onLabel else offLabel, Toast.LENGTH_SHORT).show()
        }

        private fun v269SetSocialPreset(value: String) {
            val next = when (value.uppercase()) {
                "16:9", "1:1", "4:5" -> value.uppercase()
                else -> "9:16"
            }
            v269StoryPrefs().edit().putString("social_export_preset", next).apply()
            Toast.makeText(activity, "SOCIAL EXPORT • $next", Toast.LENGTH_SHORT).show()
        }

        private fun v269SetBrandMode(value: String) {
            val next = when (value.uppercase()) {
                "DEVELOP.UGANDA", "CUSTOM BRAND" -> value.uppercase()
                else -> "CLEAN MASTER"
            }
            v269StoryPrefs().edit().putString("export_brand_mode", next).apply()
            Toast.makeText(activity, "EXPORT BRAND • $next", Toast.LENGTH_SHORT).show()
        }

        private fun buildDrawer() {
            drawer = LinearLayout(activity).apply {
                tag = "v242_settings_drawer"
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(13), dp(12), dp(10))
                background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(250), DevelopUgandaFivemods8Theme.accent, 18)
                visibility = View.GONE
                elevation = 0f
            }

            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(2), 0, 0, dp(6))
            }
            header.addView(
                TextView(activity).apply {
                    text = "V280 • SMART DIRECTOR + ACTIVE SHOOTING + SHOT MEMORY"
                    textSize = 11.4f
                    setTextColor(DevelopUgandaFivemods8Theme.content)
                    typeface = Typeface.DEFAULT_BOLD
                },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            )
            header.addView(
                proButton("CLOSE ×").apply { setOnClickListener { setDrawer(false) } },
                LinearLayout.LayoutParams(dp(82), dp(44))
            )
            drawer.addView(header)
            drawer.addView(TextView(activity).apply {
                text = "Same develop.uganda colours + clean shooting interface • V280 preserves the complete V279/V278/V277/V276/V275/V274/V273/V272/V271 stack. Smart Director now adds planned shot sequences, coverage and Next Shot guidance while Active Shooting Intelligence, Shot Memory, lighting/exposure, recording safety, recovery and Pro Settings remain intact."
                textSize = 7.3f
                setTextColor(DevelopUgandaFivemods8Theme.accent)
                setPadding(dp(2), 0, dp(2), dp(7))
            })

            val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

            val v260Search = android.widget.EditText(activity).apply {
                hint = "Search settings • audio, LUT, tracking, project, thermal…"
                textSize = 9f
                setTextColor(DevelopUgandaFivemods8Theme.content)
                setHintTextColor(DevelopUgandaFivemods8Theme.contentDim)
                setSingleLine(true)
                setPadding(dp(12), dp(8), dp(12), dp(8))
                background = rounded(DevelopUgandaFivemods8Theme.surface, DevelopUgandaFivemods8Theme.outline, 14)
            }
            drawer.addView(v260Search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply {
                bottomMargin = dp(6)
            })
            v260Search.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    v260FilterSettings(body, s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })

            drawer.addView(TextView(activity).apply {
                text = "GUIDED ORDER • 1 PREPARE → 2 SOUND → 3 CONTINUITY → 4 FRAME → 5 REHEARSE → 6 FOCUS/TRACK → 7 RECORD → 8 REVIEW → 9 ORGANIZE/EDIT → 10 DELIVER\nTap a row to use it • touch ⓘ for WHAT / HOW / RESULT."
                textSize = 7.1f
                setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                setPadding(dp(5), dp(4), dp(5), dp(7))
            })

            v253Section(body, "V274 MEDIA VAULT + DELIVERY")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Auto Register New Clips",
                    "Registers each successful new CameraX video master in the V274 Media Vault after Finalize. This adds organization metadata only; it does not rewrite the video.",
                    { DevelopUgandaV274MediaVaultStore.autoRegister(activity) }
                ) {
                    val next = !DevelopUgandaV274MediaVaultStore.autoRegister(activity)
                    DevelopUgandaV274MediaVaultStore.setAutoRegister(activity, next)
                    "AUTO REGISTER ${if (next) "ON" else "OFF"}"
                },
                v253ToggleRow(
                    "Protected Master Guard",
                    "When ON, a clip marked PROTECTED in Media Vault must be unlocked before V274 will attempt deletion.",
                    { DevelopUgandaV274MediaVaultStore.protectMasters(activity) }
                ) {
                    val next = !DevelopUgandaV274MediaVaultStore.protectMasters(activity)
                    DevelopUgandaV274MediaVaultStore.setProtectMasters(activity, next)
                    "MASTER GUARD ${if (next) "ON" else "OFF"}"
                },
                v253ToggleRow(
                    "Post-record Health Labels",
                    "Uses warning history collected during the take to label the vault card RECORDING CLEAN / AUDIO CHECK / THERMAL WARNING / SUBJECT CHECK. It does not invent analysis that was not observed.",
                    { DevelopUgandaV274MediaVaultStore.healthCheck(activity) }
                ) {
                    val next = !DevelopUgandaV274MediaVaultStore.healthCheck(activity)
                    DevelopUgandaV274MediaVaultStore.setHealthCheck(activity, next)
                    "HEALTH LABELS ${if (next) "ON" else "OFF"}"
                },
                v253ExpandableRow(
                    "Delivery Preset",
                    { DevelopUgandaV274MediaVaultStore.selectedPreset(activity) },
                    "ORIGINAL MASTER can share immediately. Aspect-changing social/news presets are stored as delivery intent and say RENDER REQUIRED until the editor/export renderer creates a new file."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf(
                            "ORIGINAL" to DevelopUgandaV274MediaVaultStore.PRESET_ORIGINAL,
                            "TIKTOK" to DevelopUgandaV274MediaVaultStore.PRESET_TIKTOK,
                            "REELS" to DevelopUgandaV274MediaVaultStore.PRESET_REELS,
                            "YOUTUBE" to DevelopUgandaV274MediaVaultStore.PRESET_YOUTUBE,
                            "NEWS" to DevelopUgandaV274MediaVaultStore.PRESET_NEWS,
                            "WHATSAPP" to DevelopUgandaV274MediaVaultStore.PRESET_WHATSAPP
                        ),
                        { DevelopUgandaV274MediaVaultStore.selectedPreset(activity) }
                    ) { value ->
                        DevelopUgandaV274MediaVaultStore.setSelectedPreset(activity, value)
                        refresh()
                    }
                    box.addView(v253MiniAction("OPEN MEDIA VAULT") {
                        activity.startActivity(Intent(activity, DevelopUgandaV274MediaVaultActivity::class.java))
                    })
                },
                v253ExpandableRow(
                    "V274 Verification Center",
                    { "VIEW" },
                    "Separates actions that work now from delivery formats that still require a renderer. Original master safety is explicit."
                ) { box, _ -> box.addView(v253LiveValue(DevelopUgandaV274MediaVaultStore.verificationSummary(activity))) }
            ))

            v253Section(body, "V273 MOTION + SHOT CONTROL")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Movement Coach",
                    "Uses the phone's real rotation-vector motion score to show temporary PAN/TILT/MOVE TOO FAST guidance. Screen-only and auto-hides when stable.",
                    { activity.v273Setting("movement_coach", true) }
                ) { activity.v273ToggleSetting("movement_coach", true, "MOVEMENT COACH") },
                v253ToggleRow(
                    "Horizon Intelligence",
                    "Draws a thin live horizon guide from the real phone roll angle and disappears when the shot becomes level/stable.",
                    { activity.v273Setting("horizon_assist", true) }
                ) { activity.v273ToggleSetting("horizon_assist", true, "HORIZON ASSIST") },
                v253ToggleRow(
                    "Auto-hide Motion UI",
                    "Keeps the shooting frame clean by hiding motion/horizon graphics once level and stable.",
                    { activity.v273Setting("auto_hide_motion_ui", true) }
                ) { activity.v273ToggleSetting("auto_hide_motion_ui", true, "MOTION UI AUTO-HIDE") },
                v253ExpandableRow(
                    "Shot Movement Mode",
                    { activity.v273ShotMode() },
                    "Tell the coach what move you are attempting so warnings can be contextual. The app guides the operator; it does not claim to physically move the phone."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("HANDHELD" to "HANDHELD","STATIC" to "STATIC","PAN" to "PAN","TILT" to "TILT","FOLLOW" to "FOLLOW","WALKING" to "WALKING","PUSH-IN" to "PUSH-IN","PULL-OUT" to "PULL-OUT","ORBIT" to "ORBIT","TRIPOD" to "TRIPOD","GIMBAL" to "GIMBAL"), { activity.v273ShotMode() }) { value -> activity.v273SetShotMode(value); refresh() }
                    box.addView(v253LiveValue(activity.v273MotionStatus()))
                },
                v253ExpandableRow(
                    "Subject Tracking Lock",
                    { activity.v255SubjectTrackingMode() },
                    "Uses the existing ML Kit face detector and real CameraX AF/AE/AWB metering. PRIMARY FACE follows the primary detected face; TAP FACE lets the operator arm a face target."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("OFF" to "OFF","PRIMARY FACE" to "PRIMARY FACE","TAP FACE" to "TAP FACE"), { activity.v255SubjectTrackingMode() }) { value -> activity.v255SetSubjectTrackingMode(value); refresh() }
                    v253ChoiceGrid(box, listOf("SMOOTH" to "SMOOTH","NORMAL" to "NORMAL","FAST" to "FAST"), { activity.v255TrackingResponse() }) { value -> activity.v255SetTrackingResponse(value); refresh() }
                },
                v253ExpandableRow(
                    "Real Focus A/B Pull",
                    { activity.v255FocusPullStatus() },
                    "Uses Camera2 manual lens focus distance only when the active lens reports manual-focus support. Unsupported/fixed-focus lenses are clearly reported."
                ) { box, refresh ->
                    box.addView(v253MiniAction(if (activity.v255FocusPullEnabled()) "TURN FOCUS A/B OFF" else "ARM FOCUS A/B") { activity.v255ToggleFocusPull(); refresh() })
                    v253ChoiceGrid(box, listOf("A 10%" to "10","A 25%" to "25","A 50%" to "50","A 75%" to "75","A 90%" to "90"), { activity.v255FocusA().toString() }) { value -> activity.v255SetFocusA(value); refresh() }
                    v253ChoiceGrid(box, listOf("B 10%" to "10","B 25%" to "25","B 50%" to "50","B 75%" to "75","B 90%" to "90"), { activity.v255FocusB().toString() }) { value -> activity.v255SetFocusB(value); refresh() }
                    v253ChoiceGrid(box, listOf("0.8s" to "800","1.2s" to "1200","1.8s" to "1800","2.5s" to "2500"), { activity.v255FocusPullMs().toString() }) { value -> activity.v255SetFocusPullMs(value); refresh() }
                    box.addView(v253MiniAction("RUN FOCUS A→B") { activity.v255RunFocusPull("A>B"); refresh() })
                    box.addView(v253MiniAction("RUN FOCUS B→A") { activity.v255RunFocusPull("B>A"); refresh() })
                },
                v253ExpandableRow(
                    "Smooth Zoom Ramp",
                    { activity.v273ZoomRampStatus() },
                    "Runs a real timed CameraX zoomRatio ramp, clamped to the current lens's reported min/max zoom range."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("0.5×" to "0.5","1.0×" to "1.0","2.0×" to "2.0","3.0×" to "3.0","5.0×" to "5.0"), { String.format(java.util.Locale.US,"%.1f",DevelopUgandaV273MotionShotControl.zoomStart(activity)) }) { value -> activity.v273SetZoomStart(value); refresh() }
                    v253ChoiceGrid(box, listOf("0.5×" to "0.5","1.0×" to "1.0","2.0×" to "2.0","3.0×" to "3.0","5.0×" to "5.0"), { String.format(java.util.Locale.US,"%.1f",DevelopUgandaV273MotionShotControl.zoomEnd(activity)) }) { value -> activity.v273SetZoomEnd(value); refresh() }
                    v253ChoiceGrid(box, listOf("3s" to "3","5s" to "5","8s" to "8","10s" to "10"), { (DevelopUgandaV273MotionShotControl.zoomDurationMs(activity)/1000).toString() }) { value -> activity.v273SetZoomDuration(value); refresh() }
                    box.addView(v253MiniAction("RUN ZOOM RAMP") { activity.v273RunZoomRamp(); refresh() })
                    box.addView(v253MiniAction("STOP ZOOM RAMP") { activity.v273StopZoomRamp(); refresh() })
                },
                v253ExpandableRow(
                    "Exposure Ramp",
                    { activity.v273ExposureRampStatus() },
                    "Runs a timed CameraX exposure-compensation transition only when the active camera reports exposure compensation support."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("-2" to "-2","-1" to "-1","0" to "0","+1" to "1","+2" to "2"), { DevelopUgandaV273MotionShotControl.exposureStart(activity).toString() }) { value -> activity.v273SetExposureStart(value); refresh() }
                    v253ChoiceGrid(box, listOf("-2" to "-2","-1" to "-1","0" to "0","+1" to "1","+2" to "2"), { DevelopUgandaV273MotionShotControl.exposureEnd(activity).toString() }) { value -> activity.v273SetExposureEnd(value); refresh() }
                    v253ChoiceGrid(box, listOf("3s" to "3","5s" to "5","8s" to "8","10s" to "10"), { (DevelopUgandaV273MotionShotControl.exposureDurationMs(activity)/1000).toString() }) { value -> activity.v273SetExposureDuration(value); refresh() }
                    box.addView(v253MiniAction("RUN EXPOSURE RAMP") { activity.v273RunExposureRamp(); refresh() })
                    box.addView(v253MiniAction("STOP EXPOSURE RAMP") { activity.v273StopExposureRamp(); refresh() })
                },
                v253ExpandableRow(
                    "Shot Rehearsal",
                    { activity.v273RehearsalStatus() },
                    "Save START and END bookends containing shot mode, roll/pitch/motion, zoom, exposure, WB, shutter, ISO, tracking and focus A/B setup. Restore START before another take."
                ) { box, refresh ->
                    box.addView(v253MiniAction("SAVE REHEARSAL START") { activity.v273SaveRehearsalStart(); refresh() })
                    box.addView(v253MiniAction("SAVE REHEARSAL END") { activity.v273SaveRehearsalEnd(); refresh() })
                    box.addView(v253MiniAction("RESTORE REHEARSAL START") { activity.v273RestoreRehearsalStart(); refresh() })
                },
                v253ExpandableRow(
                    "V273 Verification Center",
                    { "VIEW" },
                    "Shows which V273 functions are real, which depend on active lens/device support, and which physical camera movements are not claimed."
                ) { box, _ -> box.addView(v253LiveValue(activity.v273VerificationSummary())) }
            ))

            v253Section(body, "V272 FIELD SOUND + CONTINUITY")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "Professional Audio Status",
                    { activity.v272AudioInputStatus() },
                    "Shows the Android input devices currently detected. CameraX still owns the actual recording route; V272 does not pretend it can force every USB/Bluetooth route on every phone."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v272AudioInputStatus()))
                    box.addView(v253LiveValue(activity.v254AudioStatus()))
                    box.addView(v253LiveValue(activity.v272LastAudioTest()))
                },
                v253ToggleRow(
                    "Audio Coach",
                    "Adds temporary MIC TOO LOW / CLIPPING RISK guidance using the live CameraX amplitude. Screen-only and never burned into the master.",
                    { activity.v272Setting("audio_coach", true) }
                ) { activity.v272ToggleSetting("audio_coach", true, "AUDIO COACH") },
                v253ToggleRow(
                    "Audio Lock",
                    "Stores the expected Android input route at REC start and warns if the detected route changes during the take. It cannot force the OS to keep disconnected hardware attached.",
                    { activity.v272Setting("audio_lock", true) }
                ) { activity.v272ToggleSetting("audio_lock", true, "AUDIO LOCK") },
                v253ExpandableRow(
                    "Real Microphone Test",
                    { activity.v272LastAudioTest() },
                    "Runs a real ~1.4 second AudioRecord test outside recording and reports RMS/peak dBFS. Requires microphone permission."
                ) { box, refresh ->
                    box.addView(v253MiniAction("RUN 1.4s MIC TEST") { activity.v272RunAudioTest(); refresh() })
                },
                v253ToggleRow(
                    "Continuity Engine",
                    "Keeps extended shot-matching state for Scene, Look, Quality, WB, shutter, ISO, zoom and exposure.",
                    { activity.v272Setting("continuity_engine", true) }
                ) { activity.v272ToggleSetting("continuity_engine", true, "CONTINUITY ENGINE") },
                v253ToggleRow(
                    "Ghost Reference Overlay",
                    "Shows the saved previous/reference frame faintly over the live preview. Screen-only; it is never written into CameraX output.",
                    { activity.v272Setting("ghost_overlay", false) }
                ) { activity.v272ToggleSetting("ghost_overlay", false, "GHOST OVERLAY") },
                v253ExpandableRow(
                    "Ghost Strength",
                    { "${activity.v272GhostStrength()}%" },
                    "Choose how faintly the previous reference frame sits over the live camera."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("12%" to "12", "18%" to "18", "25%" to "25", "35%" to "35"), { activity.v272GhostStrength().toString() }) { value -> activity.v272SetGhostStrength(value); refresh() }
                },
                v253ExpandableRow(
                    "Reference Frame + Match",
                    { activity.v272ContinuityStatus() },
                    "Save the current live preview and controllable camera state, then compare or restore compatible settings later."
                ) { box, refresh ->
                    box.addView(v253LiveValue(activity.v272ContinuityStatus()))
                    box.addView(v253MiniAction("SAVE CURRENT REFERENCE") { activity.v272CaptureReference(); refresh() })
                    box.addView(v253MiniAction("MATCH LAST REFERENCE") { activity.v272RestoreReference(); refresh() })
                },
                v253ExpandableRow(
                    "Pre-Roll Request",
                    { activity.v272PreRollStatus() },
                    "OFF / 3s / 5s / 10s preference is stored, but V272 does not claim encoded pre-roll until the CameraX/encoder pipeline has a verified rolling buffer. Verification Center shows the truth."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("OFF" to "0", "3s" to "3", "5s" to "5", "10s" to "10"), { DevelopUgandaV272FieldSoundContinuity.preRollSeconds(activity).toString() }) { value -> activity.v272SetPreRoll(value); refresh() }
                },
                v253ExpandableRow(
                    "V272 Verification Center",
                    { "VIEW" },
                    "Separates real working audio/continuity functions from device-limited safety-track and unverified encoded pre-roll behavior."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v272VerificationSummary()))
                }
            ))

            v253Section(body, "V271 LIVE COACH + OUTPUT MASTER")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "Recording Output Master",
                    { activity.v271OutputMaster() },
                    "Choose exactly what is written into saved video: CLEAN keeps all HUD screen-only; REPORTER uses selected report/project/timecode tags; BRANDED burns only a small develop.uganda mark."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf(
                            "CLEAN MASTER" to "CLEAN MASTER",
                            "REPORTER MASTER" to "REPORTER MASTER",
                            "BRANDED MASTER" to "BRANDED MASTER"
                        ),
                        { activity.v271OutputMaster() }
                    ) { value -> activity.v271SetOutputMaster(value); refresh() }
                },
                v253ToggleRow(
                    "Live Coach",
                    "Temporary screen-only hints for storage, battery, heat, microphone, level and white-balance awareness. Hints fade and never burn into the master.",
                    { activity.v271CoachSetting("live_coach", true) }
                ) { activity.v271ToggleCoachSetting("live_coach", true, "LIVE COACH") },
                v253ToggleRow(
                    "Tap-to-fix Suggestions",
                    "Lets a Live Coach hint open the real related health/permission/help action when an automatic safe change exists.",
                    { activity.v271CoachSetting("tap_fix", true) }
                ) { activity.v271ToggleCoachSetting("tap_fix", true, "TAP-TO-FIX") },
                v253ToggleRow(
                    "Hold-for-help",
                    "Long-press advanced setting labels to open WHAT IT DOES / HOW TO USE / RESULT even when the Pro interface hides visible help icons.",
                    { activity.v271CoachSetting("hold_help", true) }
                ) { activity.v271ToggleCoachSetting("hold_help", true, "HOLD-FOR-HELP") },
                v253ToggleRow(
                    "Haptic Confirmation Language",
                    "Uses subtle tactile feedback for record start/stop and guided actions.",
                    { activity.v271CoachSetting("haptic_language", true) }
                ) { activity.v271ToggleCoachSetting("haptic_language", true, "HAPTIC LANGUAGE") },
                v253ToggleRow(
                    "Voice Confirmations",
                    "Optional short spoken record confirmations. OFF by default so field audio is not disturbed unless you deliberately enable it.",
                    { activity.v271CoachSetting("voice_confirmations", false) }
                ) { activity.v271ToggleCoachSetting("voice_confirmations", false, "VOICE CONFIRMATIONS") },
                v253ExpandableRow(
                    "Beginner / Pro Interface",
                    { activity.v271InterfaceLevel() },
                    "BEGINNER keeps teaching language prominent. PRO keeps the same functions but reduces teaching emphasis."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("BEGINNER" to "BEGINNER", "PRO" to "PRO"), { activity.v271InterfaceLevel() }) { value -> activity.v271SetInterfaceLevel(value); refresh() }
                },
                v253ExpandableRow(
                    "V271 Verification Center",
                    { "VIEW" },
                    "Shows WORKING / DEVICE LIMITED / NEEDS CONNECTION instead of pretending all hardware functions are available on every phone."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v271VerificationSummary()))
                },
                v253ExpandableRow(
                    "Undo + Settings History",
                    { "UNDO / HISTORY" },
                    "Keeps a short in-session history of key camera changes so an accidental Scene, Look, Quality, shutter, ISO or WB change can be restored before recording."
                ) { box, refresh ->
                    box.addView(v253LiveValue(activity.v271SettingsHistory()))
                    box.addView(v253MiniAction("UNDO LAST CAMERA CHANGE") { activity.v271UndoLastChange(); refresh() })
                },
                v253ExpandableRow(
                    "Shot Setup Memory",
                    { "PRESET A / B" },
                    "Save complete camera setups for repeat jobs such as INTERVIEW 1, NIGHT STREET, NEWS STANDUP or SOCIAL PORTRAIT. Two real camera-memory slots are retained in V271."
                ) { box, refresh ->
                    val a = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    a.addView(v253MiniAction("SAVE A") { activity.v260SaveCameraPreset("A"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(3) })
                    a.addView(v253MiniAction("APPLY A") { activity.v260ApplyCameraPreset("A"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(3) })
                    box.addView(a)
                    val b = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    b.addView(v253MiniAction("SAVE B") { activity.v260SaveCameraPreset("B"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { rightMargin = dp(3); topMargin = dp(5) })
                    b.addView(v253MiniAction("APPLY B") { activity.v260ApplyCameraPreset("B"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(3); topMargin = dp(5) })
                    box.addView(b)
                },
                v253ExpandableRow(
                    "Gesture Tutorial",
                    { "SHOW" },
                    "Explains the temporary left exposure ruler, right zoom ruler, tap focus, long-press help and operator lock gestures."
                ) { box, _ ->
                    box.addView(v253MiniAction("SHOW CAMERA GESTURES") {
                        DevelopUgandaV270Guidance.showFeature(activity, "CAMERA GESTURES", "LEFT EDGE • touch/slide for exposure ruler.\nRIGHT EDGE • touch/slide for zoom ruler.\nTAP SUBJECT • focus + metering.\nLONG PRESS HELP • explain advanced controls.\nLOCK • protect the current operator setup.")
                    })
                },
                v253ExpandableRow(
                    "Session Summary",
                    { "VIEW" },
                    "Shows the last clip duration/size, current story clip count, field capacity and selected V271 output master."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v271SessionSummary()))
                }
            ))

            v253Section(body, "V271 GUIDANCE + ACTION AUDIT")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Guided Help",
                    "Shows the small ⓘ touch-help beside advanced settings and workflow cards.",
                    { DevelopUgandaV270Guidance.guidedHelpEnabled(activity) }
                ) { DevelopUgandaV270Guidance.toggleGuidedHelp(activity) },
                v253ToggleRow(
                    "First-use Coach",
                    "Shows the four-step plain-language workflow once on a fresh V271 install.",
                    { DevelopUgandaV270Guidance.firstUseCoachEnabled(activity) }
                ) { DevelopUgandaV270Guidance.toggleFirstUseCoach(activity) },
                v253ToggleRow(
                    "Action Guard",
                    "Checks that a destination exists in the installed build before a navigation button opens it, preventing dead-route crashes.",
                    { DevelopUgandaV270Guidance.actionGuardEnabled(activity) }
                ) { DevelopUgandaV270Guidance.toggleActionGuard(activity) },
                v253ExpandableRow(
                    "Function Audit",
                    { DevelopUgandaV270Guidance.auditRoutes(activity).shortLabel() },
                    "Checks the installed build for every main launcher workflow destination. Hardware-dependent behavior still depends on the real phone."
                ) { box, refresh ->
                    val result = DevelopUgandaV270Guidance.auditRoutes(activity)
                    box.addView(v253LiveValue(result.shortLabel()))
                    box.addView(v253MiniAction("RUN FULL UI ACTION CHECK") {
                        DevelopUgandaV270Guidance.showAudit(activity)
                        refresh()
                    })
                    box.addView(v253MiniAction("SHOW GUIDED WORKFLOW") {
                        DevelopUgandaV270Guidance.showWorkflow(activity)
                    })
                }
            ))

            v253Section(body, "RECORD")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Quick Clean / Reporter Toggle",
                    "Fast switch between CLEAN MASTER and REPORTER MASTER. Use Recording Output Master above for the BRANDED option.",
                    { activity.v271OutputMaster() == DevelopUgandaV271LiveCoach.MODE_CLEAN }
                ) { activity.v251ToggleCleanMaster() },
                v253ExpandableRow(
                    "Timecode Mode",
                    { activity.v256TimecodeMode() },
                    "REC RUN follows each clip. FREE RUN counts continuously for this camera session. TIME OF DAY follows the phone clock."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("REC RUN" to "REC RUN", "FREE RUN" to "FREE RUN", "TIME OF DAY" to "TIME OF DAY"),
                        { activity.v256TimecodeMode() }
                    ) { value -> activity.v256SetTimecodeMode(value); refresh() }
                    box.addView(v253MiniAction("RESET FREE RUN TO 00:00:00") {
                        activity.v256ResetFreeRun(); refresh()
                    })
                },
                v253ExpandableRow(
                    "Master Output",
                    { activity.v253QualityLabel() },
                    "Choose the real CameraX recording profile here."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf(
                            "4K MASTER" to "MASTER UHD",
                            "4K 60" to "UHD 60",
                            "4K HDR" to "MASTER HDR",
                            "1080 SOCIAL" to "SOCIAL FHD"
                        ),
                        { activity.v253QualityLabel() }
                    ) { value -> activity.v253SetQuality(value); refresh() }
                },
                v253ToggleRow(
                    "Record Preflight",
                    "Checks storage, battery, thermal, microphone, GPS and shot warnings before REC.",
                    { activity.v254PreflightEnabled() }
                ) { activity.v254TogglePreflight() },
                v253ExpandableRow(
                    "Clip Naming",
                    { activity.v254ClipNamingMode() },
                    "Choose how saved video filenames are generated."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf(
                            "REPORT" to "REPORT",
                            "STORY" to "STORY",
                            "SIMPLE" to "SIMPLE"
                        ),
                        { activity.v254ClipNamingMode() }
                    ) { value -> activity.v254SetClipNamingMode(value); refresh() }
                },
                v253ToggleRow(
                    "Auto Quick Review",
                    "Show the QC review card automatically after a successful clip finalizes.",
                    { activity.v254QuickReviewEnabled() }
                ) { activity.v254ToggleQuickReview() },
                v253ExpandableRow(
                    "Record Health",
                    { "LIVE" },
                    "Battery, storage, thermal, microphone and estimated recording time update live."
                ) { box, refresh ->
                    val live = v253LiveValue(activity.v254FieldCapacity())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v254FieldCapacity()
                            handler.postDelayed(this, 1000L)
                        }
                    }
                    handler.post(runner)
                    box.addView(v253MiniAction("RUN FULL HEALTH CHECK") { triggerText("HEALTH"); refresh() })
                }
            ))

            v253Section(body, "V260 PROJECT + FAILSAFE")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Project Mode",
                    "Organize clips with project, camera name, scene and take metadata. Recorded pixels remain unchanged.",
                    { activity.v260ProjectModeEnabled() }
                ) { activity.v260ToggleProjectMode() },
                v253ExpandableRow(
                    "Project + Digital Slate",
                    { activity.v260SlateLabel() },
                    "Set project, camera name, scene and take. These values are written into V260 filenames and Story Package metadata."
                ) { box, refresh ->
                    fun input(label: String, value: String, save: (String) -> Unit): android.widget.EditText {
                        return android.widget.EditText(activity).apply {
                            hint = label
                            setText(value)
                            setSingleLine(true)
                            textSize = 9f
                            setTextColor(DevelopUgandaFivemods8Theme.content)
                            setHintTextColor(DevelopUgandaFivemods8Theme.contentDim)
                            setPadding(dp(10), dp(7), dp(10), dp(7))
                            background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(170), DevelopUgandaFivemods8Theme.outlineScrim(85), 11)
                            setOnEditorActionListener { _, _, _ -> save(text.toString()); refresh(); clearFocus(); true }
                            setOnFocusChangeListener { _, hasFocus -> if (!hasFocus) { save(text.toString()); refresh() } }
                        }
                    }
                    box.addView(input("PROJECT NAME", activity.v260ProjectName()) { activity.v260SetProjectName(it) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { bottomMargin = dp(6) })
                    box.addView(input("CAMERA NAME", activity.v260CameraName()) { activity.v260SetCameraName(it) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { bottomMargin = dp(7) })
                    box.addView(v253LiveValue(activity.v260SlateLabel()))
                    val sceneRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    sceneRow.addView(v253MiniAction("SCENE −") { activity.v260PreviousScene(); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f))
                    sceneRow.addView(v253MiniAction("SCENE +") { activity.v260NextScene(); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
                    box.addView(sceneRow)
                    val takeRow = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    takeRow.addView(v253MiniAction("TAKE −") { activity.v260PreviousTake(); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f))
                    takeRow.addView(v253MiniAction("TAKE +") { activity.v260NextTake(); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
                    box.addView(takeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(6) })
                },
                v253ToggleRow(
                    "Auto Take Counter",
                    "After a successful finalized clip, increment TAKE automatically. Failed clips do not advance the counter.",
                    { activity.v260AutoTakeEnabled() }
                ) { activity.v260ToggleAutoTake() },
                v253ExpandableRow(
                    "Camera Presets A / B",
                    { "SAVE / APPLY" },
                    "Store and recall scene, look, quality, capture mode, shutter, ISO, WB, bitrate, timecode, tracking and stabilization."
                ) { box, refresh ->
                    val a = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    a.addView(v253MiniAction("SAVE A") { activity.v260SaveCameraPreset("A"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f))
                    a.addView(v253MiniAction("APPLY A") { activity.v260ApplyCameraPreset("A"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
                    box.addView(a)
                    val b = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                    b.addView(v253MiniAction("SAVE B") { activity.v260SaveCameraPreset("B"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f))
                    b.addView(v253MiniAction("APPLY B") { activity.v260ApplyCameraPreset("B"); refresh() }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(6) })
                    box.addView(b, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(6) })
                },
                v253ToggleRow(
                    "Storage Reservation",
                    "Block a new recording before it consumes the reserved project space. The existing 1GB emergency block remains even when this is off.",
                    { activity.v260StorageReservationEnabled() }
                ) { activity.v260ToggleStorageReservation() },
                v253ExpandableRow(
                    "Reserved Storage",
                    { "${activity.v260StorageReserveGb()}GB" },
                    "Choose how much free space V260 must preserve before allowing a new recording."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("2 GB" to "2", "5 GB" to "5", "10 GB" to "10"),
                        { activity.v260StorageReserveGb().toString() }
                    ) { value -> activity.v260SetStorageReserveGb(value.toIntOrNull() ?: 2); refresh() }
                },
                v253ExpandableRow(
                    "Thermal Strategy",
                    { activity.v260ThermalStrategy() },
                    "Changes when preflight warns about phone heat. CRITICAL thermal state always blocks recording."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("QUALITY FIRST" to "QUALITY FIRST", "BALANCED" to "BALANCED", "ENDURANCE" to "ENDURANCE"),
                        { activity.v260ThermalStrategy() }
                    ) { value -> activity.v260SetThermalStrategy(value); refresh() }
                },
                v253ExpandableRow(
                    "V260 Project Status",
                    { if (activity.v260ProjectModeEnabled()) "LIVE" else "OFF" },
                    "Live project, slate, auto-take, reserve and thermal policy."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v260ProjectStatus())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v260ProjectStatus()
                            handler.postDelayed(this, 1000L)
                        }
                    }
                    handler.post(runner)
                }
            ))

            v253Section(body, "V257 RECORD CORE")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "Recording Bitrate",
                    { "${activity.v257BitrateMode()} • ${activity.v257BitrateTargetMbps()} Mbps" },
                    "Real CameraX target bitrate policy. STANDARD preserves the V256 target; SAFE reduces heat/storage demand; HIGH and MAX request progressively higher encoder bitrate."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("SAFE" to "SAFE", "STANDARD" to "STANDARD", "HIGH" to "HIGH", "MAX" to "MAX"),
                        { activity.v257BitrateMode() }
                    ) { value -> activity.v257SetBitrateMode(value); refresh() }
                    box.addView(v253LiveValue(activity.v257RecordingCoreStatus()))
                },
                v253ToggleRow(
                    "Recording Watchdog",
                    "While recording, watches storage, battery and severe thermal state. At critical storage/battery it requests a graceful stop so the current clip can finalize.",
                    { activity.v257RecordingWatchdogEnabled() }
                ) { activity.v257ToggleRecordingWatchdog() },
                v253ToggleRow(
                    "Clip Safe Feedback",
                    "After CameraX finalizes a successful clip, show CLIP SAFE with the recorded size while QC begins.",
                    { activity.v257ClipSafeFeedbackEnabled() }
                ) { activity.v257ToggleClipSafeFeedback() },
                v253ExpandableRow(
                    "Recording Core Status",
                    { "LIVE" },
                    "Live output, bitrate, requested FPS, dynamic range, free storage and timecode state."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v257RecordingCoreStatus())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v257RecordingCoreStatus()
                            handler.postDelayed(this, 700L)
                        }
                    }
                    handler.post(runner)
                },
                v253ExpandableRow(
                    "Device Recording Caps",
                    { "CAPS" },
                    "Reads the real CameraX quality/dynamic-range capabilities exposed by this camera. Codec remains DEVICE AUTO because CameraX Recorder does not expose a reliable cross-device codec selector here."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v257DeviceRecordingCaps()))
                    box.addView(v253MiniAction("OPEN FULL CAMERA HEALTH") { triggerText("HEALTH") })
                },
                v253ExpandableRow(
                    "Media Manager",
                    { "OPEN" },
                    "Open the existing develop.uganda Story Packages / media inbox for recorded clips without adding another permanent home-screen button."
                ) { box, refresh ->
                    box.addView(v253MiniAction("OPEN MEDIA MANAGER") { activity.v257OpenMediaManager(); refresh() })
                }
            ))

            v253Section(body, "CAMERA")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Face Exposure Priority",
                    "Primary-face AE/AWB follows the subject gently.",
                    { activity.v251ProAssistStatus().contains("FACE AE") }
                ) { activity.v251ToggleFaceExposurePriority() },
                v253ToggleRow(
                    "Focus Peaking + Zebras",
                    "Screen-only focus + highlight assistance; never burned into Clean Master.",
                    { activity.v251ProAssistStatus().contains("PEAK+ZEBRA") }
                ) { activity.v251ToggleProAssist() },
                v253ExpandableRow(
                    "Video Stabilization",
                    { activity.v254StabilizationPolicy() },
                    "DEVICE follows the recording profile. ON requests stabilization when hardware supports it; OFF keeps it disabled."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("DEVICE" to "DEVICE", "ON" to "ON", "OFF" to "OFF"),
                        { activity.v254StabilizationPolicy() }
                    ) { value -> activity.v254SetStabilizationPolicy(value); refresh() }
                },
                v253ExpandableRow(
                    "Manual Camera Controls",
                    { "${activity.v254ShutterLabel()} • ${activity.v254IsoLabel()} • WB ${activity.v254WhiteBalanceLabel()}" },
                    "Direct shutter, ISO and white-balance choices update without leaving this page. Unsupported manual values safely fall back."
                ) { box, refresh ->
                    box.addView(v253SubLabel("SHUTTER ANGLE"))
                    v253ChoiceGrid(box,
                        listOf("AUTO" to "AUTO", "180°" to "180", "90°" to "90", "360°" to "360"),
                        { activity.v254ShutterLabel() }
                    ) { value -> activity.v254SetShutter(value); refresh() }
                    box.addView(v253SubLabel("ISO"))
                    v253ChoiceGrid(box,
                        listOf("AUTO" to "AUTO", "100" to "100", "200" to "200", "400" to "400", "800" to "800", "1600" to "1600"),
                        { activity.v254IsoLabel() }
                    ) { value -> activity.v254SetIso(value); refresh() }
                    box.addView(v253SubLabel("WHITE BALANCE"))
                    v253ChoiceGrid(box,
                        listOf(
                            "AUTO" to "AUTO",
                            "DAYLIGHT" to "DAYLIGHT",
                            "CLOUDY" to "CLOUDY",
                            "TUNGSTEN" to "TUNGSTEN",
                            "FLUORESCENT" to "FLUORESCENT"
                        ),
                        { activity.v254WhiteBalanceLabel() }
                    ) { value -> activity.v254SetWhiteBalance(value); refresh() }
                    box.addView(v253MiniAction("RESET MANUAL CAMERA") { activity.v244ResetCinemaControls(); refresh() })
                }
            ))

            v253Section(body, "TRACK + MONITOR")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "Subject Tracking",
                    { activity.v255SubjectTrackingMode() },
                    "OFF leaves normal tap focus unchanged. PRIMARY FACE follows the largest face. TAP FACE lets you tap a face and follows the nearest detected face with real CameraX AF/AE/AWB metering."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf(
                            "OFF" to "OFF",
                            "PRIMARY FACE" to "PRIMARY FACE",
                            "TAP FACE" to "TAP FACE"
                        ),
                        { activity.v255SubjectTrackingMode() }
                    ) { value -> activity.v255SetSubjectTrackingMode(value); refresh() }
                },
                v253ExpandableRow(
                    "Tracking Response",
                    { activity.v255TrackingResponse() },
                    "Controls how often the tracking engine refreshes AF/AE/AWB: SMOOTH for interviews, NORMAL general use, FAST for movement."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("SMOOTH" to "SMOOTH", "NORMAL" to "NORMAL", "FAST" to "FAST"),
                        { activity.v255TrackingResponse() }
                    ) { value -> activity.v255SetTrackingResponse(value); refresh() }
                },
                v253ToggleRow(
                    "Waveform Monitor",
                    "Real preview-luma waveform computed from the live camera preview. Screen only; never burned into Clean Master.",
                    { activity.v255WaveformEnabled() }
                ) { activity.v255ToggleWaveform() },
                v253ToggleRow(
                    "False Color Exposure",
                    "Screen-only exposure false colour using the same develop.uganda navy / blue / green / gold / warning palette.",
                    { activity.v255FalseColorEnabled() }
                ) { activity.v255ToggleFalseColor() },
                v253ExpandableRow(
                    "False Color Strength",
                    { "${activity.v255FalseColorStrength()}%" },
                    "Choose overlay strength without changing the app colour language."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("25%" to "25", "40%" to "40", "60%" to "60"),
                        { activity.v255FalseColorStrength().toString() }
                    ) { value -> activity.v255SetFalseColorStrength(value); refresh() }
                },
                v253ExpandableRow(
                    "Focus Pull A / B",
                    { activity.v255FocusPullStatus() },
                    "Camera2 manual lens-focus pull where supported. 0% is far / infinity side; 100% is the near-focus side. Tracking and focus pull are intentionally mutually exclusive."
                ) { box, refresh ->
                    box.addView(v253InlineToggle("Enable Manual Focus Pull", { activity.v255FocusPullEnabled() }) {
                        activity.v255ToggleFocusPull(); refresh()
                    })
                    box.addView(v253SubLabel("FOCUS A"))
                    v253ChoiceGrid(box,
                        listOf("FAR" to "0", "20" to "20", "50" to "50", "75" to "75", "NEAR" to "100"),
                        { activity.v255FocusA().toString() }
                    ) { value -> activity.v255SetFocusA(value); refresh() }
                    box.addView(v253SubLabel("FOCUS B"))
                    v253ChoiceGrid(box,
                        listOf("FAR" to "0", "20" to "20", "50" to "50", "75" to "75", "NEAR" to "100"),
                        { activity.v255FocusB().toString() }
                    ) { value -> activity.v255SetFocusB(value); refresh() }
                    box.addView(v253SubLabel("PULL SPEED"))
                    v253ChoiceGrid(box,
                        listOf("0.5s" to "500", "1.2s" to "1200", "2.5s" to "2500"),
                        { activity.v255FocusPullMs().toString() }
                    ) { value -> activity.v255SetFocusPullMs(value); refresh() }
                    box.addView(v253MiniAction("PULL A → B") { activity.v255RunFocusPull("A>B"); refresh() })
                    box.addView(v253MiniAction("PULL B → A") { activity.v255RunFocusPull("B>A"); refresh() })
                },
                v253ToggleRow(
                    "Performance Monitor",
                    "Live recorder throughput, requested FPS target and Android thermal status. It does not pretend to count encoded dropped frames when the device API does not expose them.",
                    { activity.v255PerformanceMonitorEnabled() }
                ) { activity.v255TogglePerformanceMonitor() },
                v253ExpandableRow(
                    "Live Performance",
                    { "LIVE" },
                    "Shows recorder bitrate throughput, target FPS and thermal state while recording."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v255PerformanceStatus())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v255PerformanceStatus()
                            handler.postDelayed(this, 500L)
                        }
                    }
                    handler.post(runner)
                },
                v253ExpandableRow(
                    "Thermal Protection",
                    { activity.v255ThermalPolicy() },
                    "AUTO SAFE keeps the existing severe-heat fallback. WARN keeps warnings but stops app auto-downgrade. OFF disables the app policy only; Android/device thermal safety still applies."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("AUTO SAFE" to "AUTO SAFE", "WARN" to "WARN", "OFF" to "OFF"),
                        { activity.v255ThermalPolicy() }
                    ) { value -> activity.v255SetThermalPolicy(value); refresh() }
                }
            ))

            v253Section(body, "AUDIO")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Live Audio Meter",
                    "Show the existing live microphone level / peak guard on the camera screen.",
                    { activity.v254AudioMeterEnabled() }
                ) { activity.v254ToggleAudioMeter() },
                v253ToggleRow(
                    "Clip Risk Warning",
                    "Add MIC CLIPPING to Shot Guard when recorded audio approaches clipping.",
                    { activity.v254AudioClipWarningEnabled() }
                ) { activity.v254ToggleAudioClipWarning() },
                v253ToggleRow(
                    "Audio Headroom Guard",
                    "Uses the live CameraX microphone amplitude as an approximate dBFS guard and warns before clipping. It does not alter the recorded master or claim a second safety track.",
                    { activity.v255AudioHeadroomEnabled() }
                ) { activity.v255ToggleAudioHeadroom() },
                v253ExpandableRow(
                    "Headroom Target",
                    { "${activity.v255AudioHeadroomTargetDb()}dB" },
                    "Choose the approximate peak-warning target. -12dB is the safe field default."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("-6dB" to "-6", "-9dB" to "-9", "-12dB" to "-12", "-15dB" to "-15"),
                        { activity.v255AudioHeadroomTargetDb().toString() }
                    ) { value -> activity.v255SetAudioHeadroomTarget(value); refresh() }
                },
                v253ExpandableRow(
                    "Audio Live Status",
                    { "LIVE" },
                    "Read the current microphone state, level and held peak here."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v254AudioStatus())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v254AudioStatus()
                            handler.postDelayed(this, 500L)
                        }
                    }
                    handler.post(runner)
                }
            ))

            v253Section(body, "FIELD SAFETY")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Storage Guard",
                    "Early storage warning. OFF removes the early warning but the 1GB emergency preflight block remains.",
                    { activity.v254StorageGuardEnabled() }
                ) { activity.v254ToggleStorageGuard() },
                v253ExpandableRow(
                    "Storage Warning Level",
                    { "${activity.v254StorageThresholdGb()}GB" },
                    "Choose when the early STORAGE LOW warning appears."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf("2GB" to "2", "4GB" to "4", "8GB" to "8", "16GB" to "16"),
                        { activity.v254StorageThresholdGb().toString() }
                    ) { value -> activity.v254SetStorageThreshold(value); refresh() }
                },
                v253ToggleRow(
                    "Clip Recovery Journal",
                    "Record an in-progress journal and warn next launch if a clip did not finalize cleanly.",
                    { activity.v254RecoveryJournalEnabled() }
                ) { activity.v254ToggleRecoveryJournal() },
                v253ToggleRow(
                    "Telemetry Sidecar",
                    "Export the existing telemetry sidecar after a successful recording without burning it into the clean image.",
                    { activity.v254MetadataSidecarEnabled() }
                ) { activity.v254ToggleMetadataSidecar() },
                v253ExpandableRow(
                    "Field Capacity",
                    { "LIVE" },
                    "Live battery, free space, thermal, audio and estimated recording capacity."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v254FieldCapacity())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v254FieldCapacity()
                            handler.postDelayed(this, 1000L)
                        }
                    }
                    handler.post(runner)
                }
            ))

            v253Section(body, "PROJECT PRESETS")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "Shoot Preset",
                    { activity.v254ShootPreset() },
                    "Apply a coordinated scene, look, recording profile and stabilization starting point. Every underlying setting remains adjustable afterwards."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf(
                            "NEWS" to "NEWS",
                            "INTERVIEW" to "INTERVIEW",
                            "CINEMA" to "CINEMA",
                            "NIGHT" to "NIGHT",
                            "SOCIAL" to "SOCIAL",
                            "CUSTOM" to "CUSTOM"
                        ),
                        { activity.v254ShootPreset() }
                    ) { value -> activity.v254ApplyShootPreset(value); refresh() }
                }
            ))

            v253Section(body, "IMAGE + COLOR")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "LUTS / Color Grade",
                    { "KAMPALA FILM" },
                    "Protected LUT system stays unchanged; deeper colour tuning remains available here."
                ) { box, refresh ->
                    box.addView(v253LiveValue("KAMPALA FILM • LUT STRENGTH / SCENE COLOUR • SKIN-SAFE WORKFLOW"))
                    box.addView(v253MiniAction("OPEN LUT MIXER") { triggerTag(LUT_TAG); refresh() })
                },
                v253ExpandableRow(
                    "Aspect / Format",
                    { DevelopUgandaFieldIntelligencePanel.activeFormatLabel(activity).substringBefore(" •") },
                    "Pick the delivery frame here; orientation and safe-frame engine update from the same selection."
                ) { box, refresh ->
                    v253ChoiceGrid(box,
                        listOf(
                            "9:16" to "VERTICAL_9_16",
                            "16:9" to "YOUTUBE_16_9",
                            "4:5" to "INSTAGRAM_4_5",
                            "1:1" to "SQUARE_1_1",
                            "2.39:1" to "CINEMA_239",
                            "DUAL SAFE" to "DUAL_SAFE"
                        ),
                        { DevelopUgandaFieldIntelligencePanel.activeFormatId(activity) }
                    ) { id -> DevelopUgandaFieldIntelligencePanel.setFormat(activity, id); refresh() }
                },
                v253ExpandableRow(
                    "Scene / Look",
                    { "${activity.v253SceneLabel()} • ${activity.v253LookLabel()}" },
                    "Scene and image-look choices update instantly and stay visible on the row."
                ) { box, refresh ->
                    box.addView(v253SubLabel("SCENE"))
                    v253ChoiceGrid(box,
                        listOf(
                            "NEWS" to "NEWS",
                            "CINEMA" to "CINEMA",
                            "INTERVIEW" to "INTERVIEW",
                            "DOCUMENTARY" to "DOCUMENTARY",
                            "NIGHT" to "NIGHT",
                            "OUTDOOR" to "OUTDOOR"
                        ),
                        { activity.v253SceneLabel() }
                    ) { value -> activity.v253SetScene(value); refresh() }
                    box.addView(v253SubLabel("LOOK"))
                    v253ChoiceGrid(box,
                        listOf(
                            "CLEAN" to "CLEAN",
                            "NATURAL" to "NATURAL",
                            "WARM" to "WARM",
                            "COOL" to "COOL",
                            "GOLD" to "GOLD",
                            "SOFT" to "SOFT"
                        ),
                        { activity.v253LookLabel() }
                    ) { value -> activity.v253SetLook(value); refresh() }
                },
                v253ExpandableRow(
                    "Guides",
                    { if (activity.v253GuidesEnabled()) "ON" else "OFF" },
                    "Composition guides can be switched directly here."
                ) { box, refresh ->
                    box.addView(v253InlineToggle("Composition + Horizon Guides", { activity.v253GuidesEnabled() }) {
                        activity.v253ToggleGuides(); refresh()
                    })
                }
            ))

            v253Section(body, "V261 DIRECTOR MONITOR")
            body.addView(v253Panel(
                v253ToggleRow(
                    "RGB Parade",
                    "Live preview RGB channel distribution. Screen-only scope; it never changes or burns into the recorded master.",
                    { activity.v261RgbParadeEnabled() }
                ) { activity.v261ToggleRgbParade() },
                v253ToggleRow(
                    "Vectorscope",
                    "Live chroma vectorscope generated from preview frames. Screen-only.",
                    { activity.v261VectorscopeEnabled() }
                ) { activity.v261ToggleVectorscope() },
                v253ToggleRow(
                    "Skin Tone Reference",
                    "Shows the conventional skin-tone reference direction inside the V261 vectorscope using the app-gold guide colour.",
                    { activity.v261SkinToneReferenceEnabled() }
                ) { activity.v261ToggleSkinToneReference() },
                v253ToggleRow(
                    "Highlight + Shadow Assist",
                    "Analyses preview luma and reports near-clipped highlights and crushed-shadow percentage without altering exposure automatically.",
                    { activity.v261HighlightShadowAssistEnabled() }
                ) { activity.v261ToggleHighlightShadowAssist() },
                v253ExpandableRow(
                    "Director HUD Mode",
                    { activity.v261DirectorHudMode() },
                    "MINIMAL shows warnings only; STANDARD keeps practical monitoring; FULL adds the complete Director Monitor technical label set."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("MINIMAL" to "MINIMAL", "STANDARD" to "STANDARD", "FULL" to "FULL"),
                        { activity.v261DirectorHudMode() }
                    ) { value -> activity.v261SetDirectorHudMode(value); refresh() }
                },
                v253ToggleRow(
                    "Director Frame Guides",
                    "Preview-only safe-frame + rule-of-thirds guide. The recorded master remains untouched.",
                    { activity.v261FrameGuidesEnabled() }
                ) { activity.v261ToggleFrameGuides() },
                v253ExpandableRow(
                    "Frame Guide Format",
                    { activity.v261FrameGuideAspect() },
                    "Choose a director framing overlay independently from the actual recording format."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf(
                            "2.39:1" to "2.39:1",
                            "1.85:1" to "1.85:1",
                            "16:9" to "16:9",
                            "9:16" to "9:16",
                            "4:5" to "4:5"
                        ),
                        { activity.v261FrameGuideAspect() }
                    ) { value -> activity.v261SetFrameGuideAspect(value); refresh() }
                },
                v253ExpandableRow(
                    "Shutter Angle",
                    { activity.v254ShutterLabel() },
                    "AUTO or real Camera2 manual shutter angles where the active lens exposes manual sensor control. Exposure time follows the selected recording FPS."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf(
                            "AUTO" to "AUTO",
                            "45°" to "45",
                            "90°" to "90",
                            "144°" to "144",
                            "180°" to "180",
                            "270°" to "270",
                            "360°" to "360"
                        ),
                        { activity.v254ShutterLabel() }
                    ) { value -> activity.v261SetShutterAngle(value); refresh() }
                },
                v253ExpandableRow(
                    "Anamorphic Desqueeze",
                    { activity.v261AnamorphicRatio() },
                    "Preview-only horizontal desqueeze. OFF leaves the normal camera preview unchanged; saved CameraX media is never stretched."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf(
                            "OFF" to "OFF",
                            "1.33×" to "1.33X",
                            "1.55×" to "1.55X",
                            "1.8×" to "1.8X",
                            "2.0×" to "2.0X"
                        ),
                        { activity.v261AnamorphicRatio() }
                    ) { value -> activity.v261SetAnamorphicRatio(value); refresh() }
                },
                v253ExpandableRow(
                    "V261 Monitor Status",
                    { "LIVE" },
                    "Live Director Monitor configuration including scopes, guide, desqueeze, shutter angle and clean-master state."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v261DirectorMonitorStatus())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v261DirectorMonitorStatus()
                            handler.postDelayed(this, 1000L)
                        }
                    }
                    handler.post(runner)
                }
            ))

            v253Section(body, "V262 REMOTE DIRECTOR")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Remote Camera Host",
                    "Hosts a PIN-authenticated local Wi-Fi / hotspot service on port 8262 while this camera is open. No cloud service is used.",
                    { activity.v262RemoteHostEnabled() }
                ) { activity.v262ToggleRemoteHost() },
                v253ToggleRow(
                    "Remote Preview",
                    "Sends low-resolution JPEG preview frames from PreviewView only. It never replaces or downscales the local CameraX master recording.",
                    { activity.v262RemotePreviewEnabled() }
                ) { activity.v262ToggleRemotePreview() },
                v253ExpandableRow(
                    "Remote Preview Quality",
                    { activity.v262RemotePreviewQuality() },
                    "LOW, BALANCED or HIGH controls only the director-phone preview. Recording Priority can automatically throttle preview load while recording."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("LOW" to "LOW", "BALANCED" to "BALANCED", "HIGH" to "HIGH"),
                        { activity.v262RemotePreviewQuality() }
                    ) { value -> activity.v262SetRemotePreviewQuality(value); refresh() }
                },
                v253ToggleRow(
                    "Remote Control",
                    "Allows the paired director phone to start/stop video, tap AF/AE/AWB, change zoom position and advance scene/take. Turn OFF for view-only monitoring.",
                    { activity.v262RemoteControlEnabled() }
                ) { activity.v262ToggleRemoteControl() },
                v253ToggleRow(
                    "Recording Priority",
                    "Protects the local master by reducing remote-preview size/quality while recording. A weak director connection must never stop local capture.",
                    { activity.v262RecordPriorityEnabled() }
                ) { activity.v262ToggleRecordPriority() },
                v253ToggleRow(
                    "Remote Auto Reconnect",
                    "The director phone keeps retrying the last camera address after a temporary Wi-Fi drop.",
                    { activity.v262AutoReconnectEnabled() }
                ) { activity.v262ToggleAutoReconnect() },
                v253ToggleRow(
                    "Director Markers",
                    "Allows BEST / RETAKE / B-ROLL / QUOTE markers from the director phone. Markers are written to a small local marker journal after a successful clip.",
                    { activity.v262DirectorMarkersEnabled() }
                ) { activity.v262ToggleDirectorMarkers() },
                v253ExpandableRow(
                    "Pairing + Remote Director",
                    { "PIN ${activity.v262RemotePin()}" },
                    "Use the displayed LAN IP and 4-digit PIN on a second phone running V262. Regenerate the PIN whenever you want to revoke an old pairing."
                ) { box, refresh ->
                    box.addView(v253LiveValue(activity.v262RemoteStatus()))
                    box.addView(v253MiniAction("NEW 4-DIGIT PIN") { activity.v262RegeneratePairingPin(); refresh() })
                    box.addView(v253MiniAction("OPEN REMOTE DIRECTOR") { activity.v262OpenRemoteDirector() })
                },
                v253ExpandableRow(
                    "V262 Remote Status",
                    { if (activity.v262RemoteHostEnabled()) "HOST" else "OFF" },
                    "Live host address, pairing state, preview policy and current camera status."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v262RemoteStatus() + "\n" + activity.v262RemoteStatusSnapshot())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v262RemoteStatus() + "\n" + activity.v262RemoteStatusSnapshot()
                            handler.postDelayed(this, 1000L)
                        }
                    }
                    handler.post(runner)
                }
            ))

            v253Section(body, "V263 MULTI-CAM DIRECTOR")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Sync Record Commands",
                    "Allows SYNC START / SYNC SAFE STOP to be sent to the selected camera group. The measured command span is shown honestly; this is not hardware genlock.",
                    { activity.v263SyncRecordEnabled() }
                ) { activity.v263ToggleSyncRecord() },
                v253ToggleRow(
                    "Director Tally",
                    "Shows a live recording/standby border on each connected camera tile in the V263 director grid.",
                    { activity.v263TallyEnabled() }
                ) { activity.v263ToggleTally() },
                v253ToggleRow(
                    "Auto Preview Quality",
                    "Reduces remote preview polling when a camera link becomes slow. It never lowers that camera's local recording quality.",
                    { activity.v263AutoPreviewQualityEnabled() }
                ) { activity.v263ToggleAutoPreviewQuality() },
                v253ToggleRow(
                    "Master Slate Sync",
                    "Scene and take changes from the director are sent to the active camera group using the existing Project Control metadata system.",
                    { activity.v263SyncSlateEnabled() }
                ) { activity.v263ToggleSyncSlate() },
                v253ToggleRow(
                    "Multi-Cam Grid Motion",
                    "Keeps the same develop.uganda palette but adds restrained tile selection and control motion to the director grid.",
                    { activity.v263GridMotionEnabled() }
                ) { activity.v263ToggleGridMotion() },
                v253ExpandableRow(
                    "Maximum Connected Cameras",
                    { "${activity.v263MaxCameras()} CAMS" },
                    "Choose a 2, 3 or 4 camera director grid. Empty slots remain inactive until an IP and PIN are entered."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("2 CAMS" to "2", "3 CAMS" to "3", "4 CAMS" to "4"),
                        { activity.v263MaxCameras().toString() }
                    ) { value -> activity.v263SetMaxCameras(value.toIntOrNull() ?: 4); refresh() }
                },
                v253ExpandableRow(
                    "Default Camera Group",
                    { activity.v263DefaultGroup() },
                    "ALL, A+B, C+D or SELECTED defines which cameras receive group record, slate and marker commands by default."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("ALL" to "ALL", "A+B" to "A+B", "C+D" to "C+D", "SELECTED" to "SELECTED"),
                        { activity.v263DefaultGroup() }
                    ) { value -> activity.v263SetDefaultGroup(value); refresh() }
                },
                v253ExpandableRow(
                    "V263 Multi-Cam Director",
                    { "OPEN" },
                    "Open the real local-LAN 2–4 camera director grid with per-camera preview/status, selected-camera AF/AE/AWB, group record, slate, markers and clock-offset checks."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v263MultiCamStatus()))
                    box.addView(v253MiniAction("OPEN V263 MULTI-CAM DIRECTOR") { activity.v263OpenMultiCamDirector() })
                }
            ))

            v253Section(body, "V264 LIVE CUT + AUTO-SYNC")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Director Cut Recording",
                    "Records Preview→Program CUT decisions as edit metadata only; local masters are never switched or altered.",
                    { activity.v264CutRecordingEnabled() }
                ) { activity.v264ToggleCutRecording() },
                v253ToggleRow(
                    "Program Tally",
                    "Shows the chosen Program camera with the existing recording/tally colour language inside the director grid.",
                    { activity.v264ProgramTallyEnabled() }
                ) { activity.v264ToggleProgramTally() },
                v253ToggleRow(
                    "Auto Clock Sync",
                    "Periodically measures LAN clock offset/RTT for connected cameras. This is an edit sync map, not hardware genlock.",
                    { activity.v264AutoClockSyncEnabled() }
                ) { activity.v264ToggleAutoClockSync() },
                v253ToggleRow(
                    "Push Camera Settings",
                    "Allows the director to push supported WB, shutter and ISO values to the active camera group. Receiving cameras still enforce their real hardware limits.",
                    { activity.v264PushSettingsEnabled() }
                ) { activity.v264TogglePushSettings() },
                v253ToggleRow(
                    "Handoff Suggestion",
                    "If the current Program camera disconnects, recommends the healthiest connected camera; it never forces a cut without the director.",
                    { activity.v264HandoffSuggestionEnabled() }
                ) { activity.v264ToggleHandoffSuggestion() },
                v253ToggleRow(
                    "Network Diagnostics",
                    "Shows real director-link latency and measured clock offsets. These are preview/control network figures, not dropped master-recording frames.",
                    { activity.v264NetworkDiagnosticsEnabled() }
                ) { activity.v264ToggleNetworkDiagnostics() },
                v253ToggleRow(
                    "Director Motion",
                    "Adds restrained Preview/Program tile motion while preserving the established develop.uganda palette and layout.",
                    { activity.v264DirectorMotionEnabled() }
                ) { activity.v264ToggleDirectorMotion() },
                v253ExpandableRow(
                    "Cut List Export",
                    { activity.v264ExportFormat() },
                    "Choose JSON for structured project metadata or CSV for a simple edit-decision spreadsheet-style list."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("JSON" to "JSON", "CSV" to "CSV"), { activity.v264ExportFormat() }) { value ->
                        activity.v264SetExportFormat(value); refresh()
                    }
                },
                v253ExpandableRow(
                    "Director Transport",
                    { activity.v264TransportMode() },
                    "AUTO, WIFI or HOTSPOT labels the intended local-LAN transport. Local master recording stays independent from the director link."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("AUTO" to "AUTO", "WIFI" to "WIFI", "HOTSPOT" to "HOTSPOT"), { activity.v264TransportMode() }) { value ->
                        activity.v264SetTransportMode(value); refresh()
                    }
                },
                v253ExpandableRow(
                    "V264 Live Cut Director",
                    { "OPEN" },
                    "Open Preview→Program control, camera matching, cut-list export, auto clock maps, diagnostics and handoff suggestions."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v264LiveCutStatus()))
                    box.addView(v253MiniAction("OPEN V264 LIVE CUT DIRECTOR") { activity.v264OpenLiveCutDirector() })
                }
            ))

            v253Section(body, "V265 PROXY SYNC + REVIEW")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Review Proxy Creation",
                    "After a successful master finalize, create a separate H.264/AAC low-bitrate review proxy. The CameraX master is never replaced or altered.",
                    { activity.v265ProxyEnabled() }
                ) { activity.v265ToggleProxy() },
                v253ExpandableRow(
                    "Proxy Quality",
                    { activity.v265ProxyQuality() },
                    "LOW prioritizes transfer speed, BALANCED is the default field-review profile, HIGH gives a sharper 720p review copy."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("LOW" to "LOW", "BALANCED" to "BALANCED", "HIGH" to "HIGH"), { activity.v265ProxyQuality() }) { value ->
                        activity.v265SetProxyQuality(value); refresh()
                    }
                },
                v253ToggleRow(
                    "Auto Proxy Pull",
                    "When the V265 Review page is open, ready proxies can enter the transfer queue automatically. Pulling pauses whenever a camera reports RECORDING.",
                    { activity.v265AutoTransferEnabled() }
                ) { activity.v265ToggleAutoTransfer() },
                v253ToggleRow(
                    "Proxy Integrity Check",
                    "Verify transferred review files have readable video duration and non-empty media before marking them LOCAL READY.",
                    { activity.v265ProxyIntegrityEnabled() }
                ) { activity.v265ToggleProxyIntegrity() },
                v253ToggleRow(
                    "Review Motion",
                    "Keeps angle switching and review feedback restrained and consistent with the established develop.uganda motion language.",
                    { activity.v265ReviewMotionEnabled() }
                ) { activity.v265ToggleReviewMotion() },
                v253ExpandableRow(
                    "V265 Proxy Sync + Review",
                    { "OPEN" },
                    "Open the real LAN proxy queue, integrity status, shared-timeline angle review, ±20ms manual sync nudges, take ratings and director notes."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v265ProxyStatus()))
                    box.addView(v253MiniAction("OPEN V265 PROXY SYNC + REVIEW") { activity.v265OpenProxySyncReview() })
                }
            ))

            v253Section(body, "V267 LIVE SPEC COMMAND CENTER")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Live Spec Data",
                    "Show truthful camera capability, battery, storage, thermal, project, audio-route and current setup data on the first page.",
                    { v267SpecBool("live_spec_data", true) }
                ) { v267ToggleSpecBool("live_spec_data", true, "LIVE SPEC DATA ON", "LIVE SPEC DATA OFF") },
                v253ToggleRow(
                    "Command Center Auto Refresh",
                    "Refresh changing first-page figures automatically. Turn off if you want the command center to update only when you return or run a readiness check.",
                    { v267SpecBool("auto_refresh", true) }
                ) { v267ToggleSpecBool("auto_refresh", true, "SPEC AUTO REFRESH ON", "SPEC AUTO REFRESH OFF") },
                v253ToggleRow(
                    "Quick Controls",
                    "Show Field Camera, Camera Health, Director and Review shortcuts plus quick shoot presets on the first page.",
                    { v267SpecBool("quick_controls", true) }
                ) { v267ToggleSpecBool("quick_controls", true, "SPEC QUICK CONTROLS ON", "SPEC QUICK CONTROLS OFF") },
                v253ToggleRow(
                    "Command Center Motion",
                    "Use restrained press, value-change and readiness motion on the first page. Recorded media is never affected.",
                    { v267SpecBool("command_motion", true) }
                ) { v267ToggleSpecBool("command_motion", true, "SPEC MOTION ON", "SPEC MOTION OFF") },
                v253ToggleRow(
                    "Advanced Device Specs",
                    "Include extra hardware facts such as OIS availability and Android API level in the first-page capability card.",
                    { v267SpecBool("advanced_specs", true) }
                ) { v267ToggleSpecBool("advanced_specs", true, "ADVANCED SPECS ON", "ADVANCED SPECS OFF") },
                v253ExpandableRow(
                    "V267 Command Center Status",
                    { if (v267SpecBool("live_spec_data", true)) "LIVE" else "MANUAL" },
                    "Current V267 first-page control state. Changes take effect the next time the launcher page is shown; live values refresh automatically when enabled."
                ) { box, _ ->
                    box.addView(v253LiveValue(v267SpecStatus()))
                }
            ))

            v253Section(body, "SMART STORY + FIELD DESK")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Story Desk",
                    "Enable the active story workspace on the first Command Center page and after successful recordings.",
                    { v269StoryBool("story_desk_enabled", true) }
                ) { v269ToggleStoryBool("story_desk_enabled", true, "STORY DESK ON", "STORY DESK OFF") },
                v253ToggleRow(
                    "Shot List Assist",
                    "Track OPENING, WIDE, MEDIUM, CLOSE-UP, INTERVIEW, B-ROLL and CLOSING shots for the active story.",
                    { v269StoryBool("shot_list_enabled", true) }
                ) { v269ToggleStoryBool("shot_list_enabled", true, "SHOT LIST ASSIST ON", "SHOT LIST ASSIST OFF") },
                v253ToggleRow(
                    "Auto Complete Selected Shot",
                    "When a master clip finalizes successfully, mark the selected NEXT SHOT complete and advance to the next missing shot.",
                    { v269StoryBool("auto_complete_shot", true) }
                ) { v269ToggleStoryBool("auto_complete_shot", true, "AUTO COMPLETE SHOT ON", "AUTO COMPLETE SHOT OFF") },
                v253ToggleRow(
                    "Quick Take Panel",
                    "After a safe finalized recording, show BEST / KEEP / RETAKE / REJECT controls plus a quick note path. It never changes the master video.",
                    { v269StoryBool("quick_take_enabled", true) }
                ) { v269ToggleStoryBool("quick_take_enabled", true, "QUICK TAKE PANEL ON", "QUICK TAKE PANEL OFF") },
                v253ToggleRow(
                    "Recent Story Activity",
                    "Show the latest story / shot / rating action on the first Command Center page.",
                    { v269StoryBool("recent_activity_enabled", true) }
                ) { v269ToggleStoryBool("recent_activity_enabled", true, "RECENT STORY ACTIVITY ON", "RECENT STORY ACTIVITY OFF") },
                v253ExpandableRow(
                    "Social Export Intent",
                    { DevelopUgandaV269StoryDeskStore.socialPreset(activity) },
                    "Select the delivery shape remembered by the Story Desk. This is an export intention and does not crop the clean camera master."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("9:16" to "9:16", "16:9" to "16:9", "1:1" to "1:1", "4:5" to "4:5"), { DevelopUgandaV269StoryDeskStore.socialPreset(activity) }) { value -> v269SetSocialPreset(value); refresh() }
                },
                v253ExpandableRow(
                    "Export Brand Mode",
                    { DevelopUgandaV269StoryDeskStore.brandMode(activity) },
                    "Remember whether story delivery should be CLEAN MASTER, DEVELOP.UGANDA or CUSTOM BRAND. The clean recording itself remains untouched."
                ) { box, refresh ->
                    v253ChoiceGrid(box, listOf("CLEAN" to "CLEAN MASTER", "DEVELOP" to "DEVELOP.UGANDA", "CUSTOM" to "CUSTOM BRAND"), { DevelopUgandaV269StoryDeskStore.brandMode(activity) }) { value -> v269SetBrandMode(value); refresh() }
                },
                v253ExpandableRow(
                    "Story Desk Status",
                    { "OPEN" },
                    "Live story title, next shot, progress, clip count and take rating."
                ) { box, _ ->
                    box.addView(v253LiveValue(DevelopUgandaV269StoryDeskStore.statusSummary(activity)))
                    box.addView(v253MiniAction("OPEN SMART STORY / FIELD DESK") { DevelopUgandaV270Guidance.safeOpen(activity, DevelopUgandaV269SmartStoryDeskActivity::class.java) })
                }
            ))

            v253Section(body, "PRO ASSIST")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "V255 Live Status",
                    { "VIEW" },
                    "Live Pro Assist + field-control + V255 tracking/monitor state."
                ) { box, _ ->
                    val live = v253LiveValue(activity.v251ProAssistStatus() + "\n" + activity.v254FieldCapacity() + "\n" + activity.v255LiveStatus())
                    box.addView(live)
                    val runner = object : Runnable {
                        override fun run() {
                            if (activity.isFinishing || activity.isDestroyed) return
                            if (live.isShown) live.text = activity.v251ProAssistStatus() + "\n" + activity.v254FieldCapacity() + "\n" + activity.v255LiveStatus()
                            handler.postDelayed(this, 1000L)
                        }
                    }
                    handler.post(runner)
                },
                v253ExpandableRow(
                    "8K Max / Device Check",
                    { "CAPS" },
                    "Capability-first: unsupported hardware is never labelled as 8K."
                ) { box, _ ->
                    box.addView(v253LiveValue("REAL DEVICE CAPS ONLY • UHD / HDR / FPS / ENCODER CHECK"))
                    box.addView(v253MiniAction("CHECK DEVICE CAPS") { triggerText("CAMERA", "CAPS") })
                }
            ))


            v253Section(body, "SMART SHOOT")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Smart Exposure Assist",
                    "Uses the existing real primary-face AE/AWB metering path as an additional smart assist. It does not fake exposure values or burn anything into media.",
                    { activity.v259SmartExposureEnabled() }
                ) { activity.v259ToggleSmartExposure() },
                v253ToggleRow(
                    "Smart Record Start Check",
                    "Runs the real field preflight before recording even if the older V254 preflight toggle is off: storage, battery, thermal, mic/GPS and shot warnings remain truthful.",
                    { activity.v259SmartRecordCheckEnabled() }
                ) { activity.v259ToggleSmartRecordCheck() },
                v253ToggleRow(
                    "Recording Confidence",
                    "Shows a live 0–100 readiness score derived from real battery, free storage, thermal state, camera readiness and current shot warnings.",
                    { activity.v259ConfidenceMeterEnabled() }
                ) { activity.v259ToggleConfidenceMeter() },
                v253ExpandableRow(
                    "Confidence Profile",
                    { activity.v259ConfidenceProfile() },
                    "STRICT penalizes warnings more heavily; NORMAL is balanced; RELAXED reduces warning penalties. Critical device conditions remain visible in every mode."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("STRICT" to "STRICT", "NORMAL" to "NORMAL", "RELAXED" to "RELAXED"),
                        { activity.v259ConfidenceProfile() }
                    ) { value -> activity.v259SetConfidenceProfile(value); refresh() }
                },
                v253ToggleRow(
                    "Scene Suggestions",
                    "Uses the existing on-device ML image labeler to show likely scene content as guidance only; it never silently changes your LUT or master recording settings.",
                    { activity.v259SceneSuggestionsEnabled() }
                ) { activity.v259ToggleSceneSuggestions() },
                v253ToggleRow(
                    "Horizon Assist",
                    "Show the existing live roll/level guide when operator UI is visible.",
                    { activity.v259HorizonAssistEnabled() }
                ) { activity.v259ToggleHorizonAssist() },
                v253ToggleRow(
                    "Shot Stability Assist",
                    "Show the existing real motion/shake indicator when operator UI is visible.",
                    { activity.v259StabilityAssistEnabled() }
                ) { activity.v259ToggleStabilityAssist() },
                v253ExpandableRow(
                    "Smart Shoot Status",
                    { activity.v259RecordingConfidenceLabel() },
                    "Live V259 confidence, exposure-assist, level, stability and ML scene status."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v259SmartShootStatus()))
                    box.addView(v253LiveValue(activity.v259SmartSceneSummary()))
                }
            ))

            v253Section(body, "MEDIA INTELLIGENCE")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Best Take Flags",
                    "Adds BEST / KEEP / REVIEW take labels inside Story Packages / Media Manager. Labels are local metadata and never alter the original clip.",
                    { activity.v259TakeFlagsEnabled() }
                ) { activity.v259ToggleTakeFlags() },
                v253ExpandableRow(
                    "Open Media Manager",
                    { if (activity.v259TakeFlagsEnabled()) "TAKE FLAGS ON" else "TAKE FLAGS OFF" },
                    "Open Story Packages / Review to mark and organize recorded takes."
                ) { box, _ ->
                    box.addView(v253MiniAction("OPEN STORY PACKAGES / MEDIA") { activity.v257OpenMediaManager() })
                }
            ))

            v253Section(body, "HOME MOTION")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Home Motion UI",
                    "Adds restrained physical movement to the camera-home controls when touched or when important state changes. Recorded media is unaffected.",
                    { activity.v257HomeMotionEnabled() }
                ) { activity.v257ToggleHomeMotion() },
                v253ExpandableRow(
                    "Motion Style",
                    { activity.v257HomeMotionStyle() },
                    "SUBTLE is the V257 default. NORMAL is more visible. REDUCED keeps only very small movement."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("SUBTLE" to "SUBTLE", "NORMAL" to "NORMAL", "REDUCED" to "REDUCED"),
                        { activity.v257HomeMotionStyle() }
                    ) { value -> activity.v257SetHomeMotionStyle(value); refresh() }
                },
                v253ToggleRow(
                    "Record Ready Pulse",
                    "A slow restrained breathing motion on the RECORD control while the camera is ready; it stops automatically during recording.",
                    { activity.v257ReadyPulseEnabled() }
                ) { activity.v257ToggleReadyPulse() },
                v253ExpandableRow(
                    "Motion Status",
                    { activity.v257HomeMotionStyle() },
                    "Shows home motion, ready pulse and tactile-switch motion state."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v257MotionStatus()))
                }
            ))

            v253Section(body, "LIVE WORKFLOW HOME")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "Workflow Motion Level",
                    { v258WorkflowMotionStyle() },
                    "OFF is instant. STANDARD rolls/eases/crossfades. FULL adds warning/record emphasis and press flourishes. It never affects recording."
                ) { box, refresh ->
                    v253ChoiceGrid(
                        box,
                        listOf("OFF" to "OFF", "STANDARD" to "STANDARD", "FULL" to "FULL"),
                        { v258WorkflowMotionStyle() }
                    ) { value -> v258SetWorkflowMotionStyle(value); refresh() }
                },
                v253ToggleRow(
                    "Live Card Data",
                    "Shows truthful changing values such as battery, free storage, thermal state, bitrate policy and timecode directly on the workflow cards.",
                    { v258LiveCardDataEnabled() }
                ) { v258ToggleLiveCardData() },
                v253ToggleRow(
                    "Flow Path Animation",
                    "Moves a restrained indicator along CAPTURE → COLOR → REVIEW → EDIT → STORY → SHARE so the workflow header feels active rather than static.",
                    { v258FlowPathMotionEnabled() }
                ) { v258ToggleFlowPathMotion() },
                v253ExpandableRow(
                    "Workflow Home Status",
                    { if (v258WorkflowMotionEnabled()) "LIVE" else "STATIC" },
                    "Current V258 workflow-home motion state."
                ) { box, _ ->
                    box.addView(v253LiveValue(v258WorkflowHomeStatus()))
                }
            ))

            v253Section(body, "CONTROL FEEL")
            body.addView(v253Panel(
                v253ToggleRow(
                    "Switch Haptics",
                    "Small tactile tick when an ON/OFF control changes.",
                    { activity.v256SwitchHapticsEnabled() }
                ) { activity.v256ToggleSwitchHaptics() },
                v253ToggleRow(
                    "Switch Motion",
                    "Physical-style thumb slides between states. Turn off for instant snap changes.",
                    { activity.v256SwitchMotionEnabled() }
                ) { activity.v256ToggleSwitchMotion() },
                v253ExpandableRow(
                    "V256 Control Status",
                    { activity.v256TimecodeMode() },
                    "Live control-feel and clean-deck status."
                ) { box, _ ->
                    box.addView(v253LiveValue(activity.v256ControlStatus()))
                }
            ))

            v253Section(body, "SAFETY + RESET")
            body.addView(v253Panel(
                v253ExpandableRow(
                    "Reset V255 Pro Monitor",
                    { "RESET" },
                    "Return V255 tracking/monitor additions to safe defaults without changing the visual theme or V254 field controls."
                ) { box, refresh ->
                    box.addView(v253MiniAction("RESET V261 DIRECTOR MONITOR") {
                        activity.v261ResetDirectorMonitor(); refresh()
                    })
                    box.addView(v253MiniAction("RESET V259 SMART SHOOT") {
                        activity.v259ResetSmartShoot(); refresh()
                    })
                    box.addView(v253MiniAction("RESET V255 PRO MONITOR") {
                        activity.v255ResetProMonitor(); refresh()
                    })
                    box.addView(v253MiniAction("RESET V254 FIELD SETTINGS") {
                        activity.v254ResetFieldControls(); refresh()
                    })
                    box.addView(v253MiniAction("RESET FULL CAMERA TO SAFE DEFAULTS") {
                        triggerText("RESET"); refresh()
                    })
                }
            ))

            val scroll = ScrollView(activity).apply {
                isFillViewport = false
                addView(body, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            drawer.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            drawer.addView(TextView(activity).apply {
                text = "V273 is cumulative: it preserves V272 Field Sound + Continuity, V271 Output Master + Live Coach, V269 Story Desk, V262→V264 director work, V265 proxy review, the established palette and clean deck. Motion/rehearsal controls are added without removing earlier builds; old bottom bars remain removed."
                textSize = 7.1f
                setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(2), dp(7), dp(2), 0)
            })

            val width = (activity.resources.displayMetrics.widthPixels * 0.88f).toInt().coerceAtMost(dp(430))
            root.addView(drawer, FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                gravity = Gravity.END
                topMargin = dp(4)
                bottomMargin = dp(4)
                rightMargin = dp(4)
            })
        }

        private var v253OpenBox: LinearLayout? = null
        private var v253OpenArrow: TextView? = null

        private fun v260FilterSettings(body: LinearLayout, query: String) {
            val q = query.trim().lowercase()
            var pendingSection: View? = null
            for (i in 0 until body.childCount) {
                val child = body.getChildAt(i)
                val tag = child.tag?.toString().orEmpty()
                if (tag.startsWith("v260_settings_section|")) {
                    pendingSection = child
                    if (q.isBlank()) child.visibility = View.VISIBLE
                    continue
                }
                if (tag == "v260_settings_panel" && child is ViewGroup) {
                    var any = q.isBlank()
                    for (j in 0 until child.childCount) {
                        val row = child.getChildAt(j)
                        val rowTag = row.tag?.toString().orEmpty()
                        if (rowTag.startsWith("v260_setting_row|")) {
                            val show = q.isBlank() || rowTag.contains(q)
                            row.visibility = if (show) View.VISIBLE else View.GONE
                            any = any || show
                        } else if (q.isNotBlank() && row.layoutParams?.height != dp(1)) {
                            // Keep separators harmless; panel visibility is determined by matching rows.
                        }
                    }
                    child.visibility = if (any) View.VISIBLE else View.GONE
                    pendingSection?.visibility = if (any) View.VISIBLE else View.GONE
                    pendingSection = null
                }
            }
        }

        private fun v253Section(parent: LinearLayout, title: String) {
            parent.addView(TextView(activity).apply {
                tag = "v260_settings_section|${title.lowercase()}"
                text = title
                textSize = 7.5f
                setTextColor(DevelopUgandaFivemods8Theme.accent)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(3), dp(10), 0, dp(4))
            })
        }

        private fun v253Panel(vararg rows: View): LinearLayout {
            return LinearLayout(activity).apply {
                tag = "v260_settings_panel"
                orientation = LinearLayout.VERTICAL
                background = rounded(DevelopUgandaFivemods8Theme.raisedScrim(238), DevelopUgandaFivemods8Theme.outline, 15)
                setPadding(0, dp(2), 0, dp(2))
                rows.forEachIndexed { index, row ->
                    addView(row)
                    if (index != rows.lastIndex) addView(View(activity).apply { setBackgroundColor(DevelopUgandaFivemods8Theme.outlineScrim(85)) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        leftMargin = dp(12); rightMargin = dp(12)
                    })
                }
            }.also {
                it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(3) }
            }
        }

        private fun v253ToggleRow(
            title: String,
            detail: String,
            state: () -> Boolean,
            action: () -> Unit
        ): LinearLayout {
            val row = LinearLayout(activity).apply {
                tag = "v260_setting_row|${title.lowercase()} ${detail.lowercase()}"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(10), dp(9))
                isClickable = true
                isFocusable = true
            }
            val copy = v253Copy(title, detail)
            row.addView(copy, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val switch = v253MovingSwitch(state)
            row.addView(switch, LinearLayout.LayoutParams(dp(76), dp(36)))
            row.post { v275SyncToggleRow(row, state()) }
            fun toggle() {
                if (activity.v256SwitchHapticsEnabled()) {
                    row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                action()
                v253SyncSwitch(switch, state(), activity.v256SwitchMotionEnabled())
                v275SyncToggleRow(row, state())
            }
            row.setOnClickListener { toggle() }
            switch.setOnClickListener { toggle() }
            return row
        }

        private fun v253InlineToggle(title: String, state: () -> Boolean, action: () -> Unit): LinearLayout {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(8), dp(7), dp(7), dp(7))
                background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(136), DevelopUgandaFivemods8Theme.outlineScrim(85), 11)
            }
            row.addView(TextView(activity).apply {
                text = title
                textSize = 7.8f
                setTextColor(DevelopUgandaFivemods8Theme.content)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val switch = v253MovingSwitch(state)
            row.addView(switch, LinearLayout.LayoutParams(dp(70), dp(32)))
            row.post { v275SyncToggleRow(row, state()) }
            fun toggle() {
                if (activity.v256SwitchHapticsEnabled()) {
                    row.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                action()
                v253SyncSwitch(switch, state(), activity.v256SwitchMotionEnabled())
                v275SyncToggleRow(row, state())
            }
            row.setOnClickListener { toggle() }
            switch.setOnClickListener { toggle() }
            return row
        }

        private fun v275SyncToggleRow(row: LinearLayout, enabled: Boolean) {
            row.background = rounded(
                if (enabled) DevelopUgandaFivemods8Theme.surfaceScrim(119) else DevelopUgandaFivemods8Theme.surfaceScrim(34),
                if (enabled) DevelopUgandaFivemods8Theme.accent else DevelopUgandaFivemods8Theme.outlineScrim(85),
                12
            )
            row.elevation = 0f
        }

        private fun v253MovingSwitch(state: () -> Boolean): FrameLayout {
            val track = FrameLayout(activity).apply {
                tag = "v253_moving_switch"
                isClickable = true
                isFocusable = true
                setPadding(dp(3), dp(3), dp(3), dp(3))
                elevation = 0f
            }

            val offLabel = TextView(activity).apply {
                tag = "v256_switch_off_label"
                text = "OFF"
                textSize = 6.4f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.accent)
            }
            val onLabel = TextView(activity).apply {
                tag = "v256_switch_on_label"
                text = "ON"
                textSize = 6.4f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.accent)
            }
            track.addView(offLabel, FrameLayout.LayoutParams(dp(33), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START or Gravity.CENTER_VERTICAL))
            track.addView(onLabel, FrameLayout.LayoutParams(dp(33), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END or Gravity.CENTER_VERTICAL))

            val thumb = LinearLayout(activity).apply {
                tag = "v253_switch_thumb"
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                background = rounded(DevelopUgandaFivemods8Theme.surface, DevelopUgandaFivemods8Theme.outline, 50)
                elevation = 0f
                repeat(6) { index ->
                    addView(View(activity).apply {
                        background = rounded(DevelopUgandaFivemods8Theme.outline, DevelopUgandaFivemods8Theme.transparent, 50)
                    }, LinearLayout.LayoutParams(dp(2), dp(2)).apply {
                        if (index > 0) marginStart = dp(2)
                    })
                }
            }
            track.addView(thumb, FrameLayout.LayoutParams(dp(38), dp(30)).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.START
                leftMargin = dp(3)
            })
            track.post { v253SyncSwitch(track, state(), false) }
            return track
        }

        private fun v253SyncSwitch(track: FrameLayout, enabled: Boolean, animated: Boolean) {
            track.background = rounded(
                if (enabled) DevelopUgandaFivemods8Theme.outline else DevelopUgandaFivemods8Theme.surface,
                if (enabled) DevelopUgandaFivemods8Theme.content else DevelopUgandaFivemods8Theme.outline,
                50
            )
            track.elevation = 0f
            val off = track.findViewWithTag<TextView>("v256_switch_off_label")
            val on = track.findViewWithTag<TextView>("v256_switch_on_label")
            off?.alpha = if (enabled) 0.28f else 1f
            on?.alpha = if (enabled) 1f else 0.28f

            val thumb = track.findViewWithTag<View>("v253_switch_thumb") ?: return
            thumb.background = rounded(
                if (enabled) DevelopUgandaFivemods8Theme.accent else DevelopUgandaFivemods8Theme.surface,
                if (enabled) DevelopUgandaFivemods8Theme.content else DevelopUgandaFivemods8Theme.outline,
                50
            )
            val travel = (track.width - dp(44)).coerceAtLeast(dp(24)).toFloat()
            val target = if (enabled) 0f else travel
            thumb.animate().cancel()
            if (animated) {
                thumb.animate()
                    .translationX(target)
                    .scaleX(0.96f)
                    .scaleY(0.96f)
                    .setDuration(115L)
                    .withEndAction {
                        thumb.animate().scaleX(1f).scaleY(1f).setDuration(75L).start()
                    }
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
            } else {
                thumb.translationX = target
            }
        }

        private fun v253ExpandableRow(
            title: String,
            status: () -> String,
            detail: String,
            builder: (LinearLayout, () -> Unit) -> Unit
        ): LinearLayout {
            val shell = LinearLayout(activity).apply {
                tag = "v260_setting_row|${title.lowercase()} ${detail.lowercase()}"
                orientation = LinearLayout.VERTICAL
            }
            val header = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(9), dp(10), dp(9))
                isClickable = true
                isFocusable = true
            }
            header.addView(v253Copy(title, detail), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val statusView = TextView(activity).apply {
                text = status()
                textSize = 7.1f
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                setTextColor(DevelopUgandaFivemods8Theme.accent)
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
            }
            header.addView(statusView, LinearLayout.LayoutParams(dp(92), ViewGroup.LayoutParams.WRAP_CONTENT))
            val arrow = TextView(activity).apply {
                text = "⌄"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(DevelopUgandaFivemods8Theme.accent)
            }
            header.addView(arrow, LinearLayout.LayoutParams(dp(20), dp(30)))
            shell.addView(header)

            val box = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(1), dp(11), dp(10))
                visibility = View.GONE
                alpha = 0f
            }
            fun refresh() { statusView.text = status() }
            builder(box, ::refresh)
            shell.addView(box)

            header.setOnClickListener {
                header.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                val opening = box.visibility != View.VISIBLE
                if (opening) {
                    v253OpenBox?.takeIf { it !== box }?.let { old ->
                        old.animate().cancel(); old.visibility = View.GONE; old.alpha = 0f
                    }
                    v253OpenArrow?.takeIf { it !== arrow }?.text = "⌄"
                    v253OpenBox = box
                    v253OpenArrow = arrow
                    box.visibility = View.VISIBLE
                    box.alpha = 0f
                    box.translationY = -dp(6).toFloat()
                    box.animate().alpha(1f).translationY(0f).setDuration(180L)
                        .setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                    arrow.text = "⌃"
                    refresh()
                } else {
                    box.animate().alpha(0f).translationY(-dp(4).toFloat()).setDuration(120L).withEndAction {
                        box.visibility = View.GONE
                        box.translationY = 0f
                    }.start()
                    arrow.text = "⌄"
                    if (v253OpenBox === box) { v253OpenBox = null; v253OpenArrow = null }
                }
            }
            return shell
        }

        private fun v253Copy(title: String, detail: String): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val top = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(TextView(activity).apply {
                text = title
                textSize = 8.6f
                setTextColor(DevelopUgandaFivemods8Theme.content)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (DevelopUgandaV270Guidance.guidedHelpEnabled(activity) && DevelopUgandaV271LiveCoach.interfaceLevel(activity) == "BEGINNER") {
                top.addView(TextView(activity).apply {
                    text = "ⓘ"
                    textSize = 10.5f
                    gravity = Gravity.CENTER
                    setTextColor(DevelopUgandaFivemods8Theme.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    isClickable = true
                    isFocusable = true
                    contentDescription = "Help for $title"
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        DevelopUgandaV270Guidance.showFeature(
                            activity,
                            title,
                            detail,
                            "Tap this row to use it. A moving switch changes ON/OFF; an expandable row opens its choices below."
                        )
                    }
                }, LinearLayout.LayoutParams(dp(30), dp(30)))
            }
            top.isLongClickable = true
            top.setOnLongClickListener {
                if (DevelopUgandaV271LiveCoach.bool(activity, "hold_help", true)) {
                    top.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    DevelopUgandaV270Guidance.showFeature(
                        activity,
                        title,
                        detail,
                        "Long-press help is active in V271. Tap the row to use it; long-press the label area whenever you need WHAT / HOW / RESULT."
                    )
                    true
                } else false
            }
            addView(top)
            addView(TextView(activity).apply {
                text = detail
                textSize = 6.8f
                setTextColor(DevelopUgandaFivemods8Theme.accent)
                setPadding(0, dp(1), dp(4), 0)
            })
        }

        private fun v253SubLabel(textValue: String): TextView = TextView(activity).apply {
            text = textValue
            textSize = 6.8f
            setTextColor(DevelopUgandaFivemods8Theme.accent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(7), 0, dp(3))
        }

        private fun v253LiveValue(textValue: String): TextView = TextView(activity).apply {
            text = textValue
            textSize = 7.1f
            setTextColor(DevelopUgandaFivemods8Theme.contentDim)
            typeface = Typeface.MONOSPACE
            setPadding(dp(8), dp(7), dp(8), dp(7))
            background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(136), DevelopUgandaFivemods8Theme.outlineScrim(85), 10)
        }

        private fun v253MiniAction(label: String, action: () -> Unit): TextView = TextView(activity).apply {
            text = label
            textSize = 7.2f
            gravity = Gravity.CENTER
            setTextColor(DevelopUgandaFivemods8Theme.accent)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(170), DevelopUgandaFivemods8Theme.outline, 10)
            setOnClickListener { view ->
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                DevelopUgandaV28012SafeActions.run(view.context, "PRO SETTINGS ACTION • $label") { action() }
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) }
        }

        private fun v253ButtonRow(vararg buttons: Pair<String, () -> Unit>): LinearLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, 0)
            buttons.forEachIndexed { index, item ->
                addView(v253MiniAction(item.first, item.second), LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                    if (index > 0) leftMargin = dp(5)
                })
            }
        }

        private fun v253ChoiceGrid(
            parent: LinearLayout,
            options: List<Pair<String, String>>,
            selected: () -> String,
            onSelect: (String) -> Unit
        ) {
            val views = mutableListOf<Pair<TextView, String>>()
            options.chunked(2).forEach { pair ->
                val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEachIndexed { index, option ->
                    val chip = TextView(activity).apply {
                        textSize = 7.2f
                        gravity = Gravity.CENTER
                        typeface = Typeface.DEFAULT_BOLD
                        setPadding(dp(5), dp(7), dp(5), dp(7))
                    }
                    views += chip to option.second
                    line.addView(chip, LinearLayout.LayoutParams(0, dp(36), 1f).apply { if (index > 0) leftMargin = dp(5) })
                }
                parent.addView(line, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(5) })
            }
            fun sync() {
                val active = selected()
                views.forEachIndexed { i, pair ->
                    val chip = pair.first
                    val value = pair.second
                    val option = options[i]
                    val isActive = active == value || active.contains(option.first, ignoreCase = true)
                    chip.text = (if (isActive) "✓ " else "") + option.first
                    chip.setTextColor(if (isActive) DevelopUgandaFivemods8Theme.content else DevelopUgandaFivemods8Theme.accent)
                    chip.background = rounded(
                        if (isActive) DevelopUgandaFivemods8Theme.surface else DevelopUgandaFivemods8Theme.surfaceScrim(170),
                        if (isActive) DevelopUgandaFivemods8Theme.accent else DevelopUgandaFivemods8Theme.outline,
                        10
                    )
                    chip.setOnClickListener {
                        chip.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        onSelect(value)
                        sync()
                    }
                }
            }
            sync()
        }

        private fun buildExposureRuler() {
            exposureRuler =
                ExposureRulerView(
                    activity,
                    activity
                ).apply {
                    tag =
                        "v249_exposure_ruler"
                    visibility = View.GONE
                }

            root.addView(
                exposureRuler,
                FrameLayout.LayoutParams(
                    dp(30),
                    dp(300)
                ).apply {
                    gravity =
                        Gravity.START or
                            Gravity.BOTTOM

                    leftMargin =
                        dp(4)

                    bottomMargin =
                        dp(196)
                }
            )
        }

        private fun buildZoomRuler() {
            zoomRuler = ZoomRulerView(activity, activity).apply {
                tag = "v242_zoom_ruler"
                visibility = View.GONE
            }

            root.addView(
                zoomRuler,
                FrameLayout.LayoutParams(
                    dp(24),
                    dp(360)
                ).apply {
                    gravity = Gravity.END or Gravity.BOTTOM
                    rightMargin = 0
                    bottomMargin = dp(190)
                }
            )
        }

        private fun setDrawer(open: Boolean) {
            if (::exposureRuler.isInitialized) {
                exposureRuler.visibility = View.GONE
            }
            if (::zoomRuler.isInitialized) {
                zoomRuler.visibility = View.GONE
            }
            drawer.animate().cancel()
            scrim.animate().cancel()
            settingsTab.animate().cancel()
            zoomRuler.animate().cancel()

            if (open) {
                scrim.alpha = 0f
                scrim.visibility = View.VISIBLE
                drawer.alpha = 0f
                drawer.translationX = dp(24).toFloat()
                drawer.visibility = View.VISIBLE
                settingsTab.visibility = View.GONE

                zoomRuler.animate()
                    .alpha(0f)
                    .setDuration(90L)
                    .withEndAction {
                        zoomRuler.visibility = View.GONE
                        zoomRuler.alpha = 1f
                    }
                    .start()

                scrim.animate().alpha(1f).setDuration(120L).start()
                drawer.animate()
                    .alpha(1f)
                    .translationX(0f)
                    .setDuration(175L)
                    .start()

                drawer.bringToFront()
            } else {
                drawer.animate()
                    .alpha(0f)
                    .translationX(dp(20).toFloat())
                    .setDuration(145L)
                    .withEndAction {
                        drawer.visibility = View.GONE
                        drawer.alpha = 1f
                        drawer.translationX = 0f
                    }
                    .start()

                scrim.animate()
                    .alpha(0f)
                    .setDuration(110L)
                    .withEndAction {
                        scrim.visibility = View.GONE
                        scrim.alpha = 1f
                    }
                    .start()

                settingsTab.alpha = 0f
                settingsTab.visibility = View.VISIBLE
                settingsTab.animate().alpha(1f).setDuration(130L).start()

                zoomRuler.animate().cancel()
                zoomRuler.alpha = 1f
                zoomRuler.visibility = View.GONE
            }
        }

        private fun v250Cam2Show(
            active: View,
            inactive: View
        ) {
            v250EdgeHandler.removeCallbacks(v250HideRulers)

            inactive.animate().cancel()
            inactive.alpha = 1f
            inactive.visibility = View.GONE

            active.animate().cancel()
            if (active.visibility != View.VISIBLE) {
                active.alpha = 0f
                active.visibility = View.VISIBLE
            }
            active.bringToFront()
            active.animate()
                .alpha(1f)
                .setDuration(115L)
                .start()
        }

        private fun v250Cam2DismissAll(
            animated: Boolean = true
        ) {
            v250EdgeHandler.removeCallbacks(v250HideRulers)

            fun hide(view: View) {
                view.animate().cancel()

                if (!animated || view.visibility != View.VISIBLE) {
                    view.alpha = 1f
                    view.visibility = View.GONE
                    return
                }

                view.animate()
                    .alpha(0f)
                    .setDuration(145L)
                    .withEndAction {
                        view.visibility = View.GONE
                        view.alpha = 1f
                    }
                    .start()
            }

            if (::exposureRuler.isInitialized) hide(exposureRuler)
            if (::zoomRuler.isInitialized) hide(zoomRuler)
        }

        private fun buildV250EdgeRevealZones() {
            val edgeWidth = dp(18)

            fun scheduleHide() {
                v250EdgeHandler.removeCallbacks(
                    v250HideRulers
                )

                v250EdgeHandler.postDelayed(
                    v250HideRulers,
                    900L
                )
            }

            v250LeftEdgeZone =
                View(activity).apply {
                    tag = "v250_left_edge_zone"
                    setBackgroundColor(
                        DevelopUgandaFivemods8Theme.transparent
                    )
                    isClickable = true
                    isFocusable = false

                    setOnTouchListener {
                        _,
                        event ->

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE -> {
                                if (exposureRuler.visibility != View.VISIBLE) {
                                    v250Cam2Show(
                                        exposureRuler,
                                        zoomRuler
                                    )
                                }

                                val normalizedY =
                                    (
                                        (event.rawY -
                                            exposureRuler.top) /
                                            exposureRuler.height
                                                .coerceAtLeast(1)
                                                .toFloat()
                                        )
                                        .coerceIn(
                                            0f,
                                            1f
                                        )

                                val forwarded =
                                    MotionEvent.obtain(
                                        event.downTime,
                                        event.eventTime,
                                        event.action,
                                        exposureRuler.width /
                                            2f,
                                        normalizedY *
                                            exposureRuler.height,
                                        event.metaState
                                    )

                                exposureRuler.dispatchTouchEvent(
                                    forwarded
                                )
                                forwarded.recycle()

                                scheduleHide()
                                true
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                scheduleHide()
                                true
                            }

                            else -> true
                        }
                    }
                }

            root.addView(
                v250LeftEdgeZone,
                FrameLayout.LayoutParams(
                    edgeWidth,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity =
                        Gravity.START

                    topMargin =
                        dp(120)

                    bottomMargin =
                        dp(185)
                }
            )

            v250RightEdgeZone =
                View(activity).apply {
                    tag =
                        "v250_right_edge_zone"

                    setBackgroundColor(
                        DevelopUgandaFivemods8Theme.transparent
                    )

                    isClickable = true
                    isFocusable = false

                    setOnTouchListener {
                        _,
                        event ->

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE -> {
                                if (zoomRuler.visibility != View.VISIBLE) {
                                    v250Cam2Show(
                                        zoomRuler,
                                        exposureRuler
                                    )
                                }

                                val normalizedY =
                                    (
                                        (event.rawY -
                                            zoomRuler.top) /
                                            zoomRuler.height
                                                .coerceAtLeast(1)
                                                .toFloat()
                                        )
                                        .coerceIn(
                                            0f,
                                            1f
                                        )

                                val forwarded =
                                    MotionEvent.obtain(
                                        event.downTime,
                                        event.eventTime,
                                        event.action,
                                        zoomRuler.width /
                                            2f,
                                        normalizedY *
                                            zoomRuler.height,
                                        event.metaState
                                    )

                                zoomRuler.dispatchTouchEvent(
                                    forwarded
                                )
                                forwarded.recycle()

                                scheduleHide()
                                true
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                scheduleHide()
                                true
                            }

                            else -> true
                        }
                    }
                }

            root.addView(
                v250RightEdgeZone,
                FrameLayout.LayoutParams(
                    edgeWidth,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    gravity =
                        Gravity.END

                    topMargin =
                        dp(120)

                    bottomMargin =
                        dp(185)
                }
            )

            v250LeftEdgeZone.bringToFront()
            v250RightEdgeZone.bringToFront()
        }

        private fun addSection(
            host: LinearLayout,
            title: String
        ) {
            host.addView(
                TextView(activity).apply {
                    text = title
                    textSize = 7.8f
                    setTextColor(DevelopUgandaFivemods8Theme.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(9), 0, dp(4))
                }
            )
        }

        private fun settingButton(
            title: String,
            detail: String,
            action: () -> Unit
        ): LinearLayout {
            val row = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                background = rounded(
                    DevelopUgandaFivemods8Theme.raisedScrim(238),
                    DevelopUgandaFivemods8Theme.outline,
                    14
                )
                isClickable = true
                isFocusable = true
                setOnClickListener { view ->
                    DevelopUgandaV28012SafeActions.run(view.context, "PRO SETTINGS ROUTE • $title") { action() }
                    setDrawer(false)
                }
            }

            row.addView(
                TextView(activity).apply {
                    text = title
                    textSize = 8.5f
                    setTextColor(DevelopUgandaFivemods8Theme.content)
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
            row.addView(
                TextView(activity).apply {
                    text = detail
                    textSize = 7f
                    setTextColor(DevelopUgandaFivemods8Theme.accent)
                    setPadding(0, dp(2), 0, 0)
                }
            )

            row.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(5)
                }

            return row
        }

        private fun triggerTag(tag: String): Boolean {
            val view = root.findViewWithTag<View>(tag) ?: return false
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            return view.performClick()
        }

        private fun triggerText(vararg words: String): Boolean {
            val wanted = words.map { it.uppercase() }
            var found: Button? = null

            fun walk(view: View) {
                if (found != null) return

                if (view is Button) {
                    val value = view.text?.toString()?.uppercase() ?: ""
                    if (wanted.all { value.contains(it) }) {
                        found = view
                        return
                    }
                }

                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        walk(view.getChildAt(i))
                        if (found != null) return
                    }
                }
            }

            walk(root)
            val button = found ?: return false
            button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            return button.performClick()
        }

        private fun proButton(value: String): Button =
            Button(activity).apply {
                text = value
                textSize = 7.8f
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
                setPadding(dp(5), 0, dp(5), 0)
                background = rounded(
                    DevelopUgandaFivemods8Theme.surfaceScrim(240),
                    DevelopUgandaFivemods8Theme.accent,
                    16
                )
            }

        private fun rounded(
            fill: Int,
            stroke: Int,
            radius: Int
        ): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fill)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }

        private fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density).toInt()
    }

    private class ExposureRulerView(
        context: android.content.Context,
        private val cameraActivity: DevelopUgandaCameraActivity
    ) : View(context) {

        private val finePaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val majorPaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private val labelPaint =
            Paint(Paint.ANTI_ALIAS_FLAG)

        private var minimum = -1f
        private var maximum = 1f
        private var current = 0f
        private var target = 0f
        private var animating = false
        private var lastSnap = Float.NaN
        private var cam2DragStartY = 0f
        private var cam2DragStartEv = 0f

        init {
            finePaint.color =
                DevelopUgandaFivemods8Theme.accentScrim(153)
            finePaint.strokeWidth =
                dp(1).toFloat()

            majorPaint.color =
                DevelopUgandaFivemods8Theme.content
            majorPaint.strokeWidth =
                dp(2).toFloat()

            labelPaint.color =
                DevelopUgandaFivemods8Theme.content
            labelPaint.textSize =
                dp(7).toFloat()
            labelPaint.typeface =
                Typeface.DEFAULT_BOLD
            labelPaint.textAlign =
                Paint.Align.CENTER

            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.RECTANGLE
                    setColor(
                        DevelopUgandaFivemods8Theme.surfaceScrim(148)
                    )
                    cornerRadius =
                        dp(13).toFloat()
                    setStroke(
                        dp(1),
                        DevelopUgandaFivemods8Theme.accentScrim(184)
                    )
                }

            isClickable = true
            isFocusable = true
        }

        fun syncFromCamera() {
            val state =
                cameraActivity
                    .v249ExposureSnapshot()

            minimum = state[0]
            maximum = state[1]

            if (!animating) {
                current = state[2]
                target = state[2]
            }

            invalidate()
        }

        private fun snapEv(raw: Float): Float {
            val candidates =
                floatArrayOf(
                    -2f,
                    -1f,
                    -0.7f,
                    -0.3f,
                    0f,
                    0.3f,
                    0.7f,
                    1f,
                    2f
                )

            var selected = raw
            var distance =
                Float.MAX_VALUE

            for (ev in candidates) {
                if (
                    ev < minimum ||
                    ev > maximum
                ) {
                    continue
                }

                val d =
                    kotlin.math.abs(
                        raw - ev
                    )

                if (d < distance) {
                    selected = ev
                    distance = d
                }
            }

            if (distance <= 0.09f) {
                if (
                    lastSnap.isNaN() ||
                    kotlin.math.abs(
                        lastSnap - selected
                    ) > 0.01f
                ) {
                    performHapticFeedback(
                        HapticFeedbackConstants.CLOCK_TICK
                    )
                    lastSnap = selected
                }
                return selected
            }

            lastSnap = Float.NaN
            return raw
        }

        private fun animateExposure() {
            if (animating) return

            animating = true

            postOnAnimation(
                object : Runnable {
                    override fun run() {
                        val delta =
                            target - current

                        if (
                            kotlin.math.abs(delta) <
                            0.01f
                        ) {
                            current = target
                            cameraActivity
                                .v249SetExposureEv(
                                    current
                                )
                            animating = false
                            syncFromCamera()
                            return
                        }

                        current +=
                            delta * 0.38f

                        cameraActivity
                            .v249SetExposureEv(
                                current
                            )

                        invalidate()
                        postOnAnimation(this)
                    }
                }
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width * 0.48f
            val centerY = height / 2f
            val spacing = dp(17).toFloat()
            val windowTop = centerY - spacing * 5f
            val windowBottom = centerY + spacing * 5f

            finePaint.alpha = 120
            canvas.drawLine(
                cx,
                windowTop,
                cx,
                windowBottom,
                finePaint
            )

            for (offset in -5..5) {
                val distance = kotlin.math.abs(offset)
                val y = centerY + offset * spacing
                val isCenter = offset == 0
                val majorTick = isCenter || offset % 2 == 0
                val paint = if (majorTick) majorPaint else finePaint

                paint.alpha = when {
                    isCenter -> 255
                    distance <= 2 -> 205
                    distance <= 4 -> 125
                    else -> 55
                }

                val len = when {
                    isCenter -> dp(12)
                    majorTick -> dp(8)
                    else -> dp(4)
                }

                canvas.drawLine(
                    cx - len,
                    y,
                    cx + dp(2),
                    y,
                    paint
                )
            }

            finePaint.alpha = 255
            majorPaint.alpha = 255

            canvas.drawLine(
                dp(3).toFloat(),
                centerY,
                width - dp(3).toFloat(),
                centerY,
                majorPaint
            )

            canvas.drawText(
                String.format(
                    java.util.Locale.US,
                    "%+.1f",
                    current
                ),
                cx,
                centerY - dp(8),
                labelPaint
            )
        }

        override fun onTouchEvent(
            event: MotionEvent
        ): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cam2DragStartY = event.y
                    cam2DragStartEv = current
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (maximum <= minimum) {
                        return true
                    }

                    val usable =
                        (height - dp(40))
                            .coerceAtLeast(1)
                            .toFloat()

                    val raw =
                        (cam2DragStartEv +
                            ((cam2DragStartY - event.y) /
                                usable) *
                                (maximum - minimum))
                            .coerceIn(minimum, maximum)

                    target = snapEv(raw)
                    animateExposure()
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    performClick()
                    return true
                }
            }

            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun dp(value: Int): Int =
            (value *
                resources
                    .displayMetrics
                    .density)
                .toInt()
    }

    private class ZoomRulerView(
        context: android.content.Context,
        private val cameraActivity: DevelopUgandaCameraActivity
    ) : View(context) {

        private val finePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        private var minimum = 1f
        private var maximum = 1f
        private var current = 1f
        private var normalized = 0f
        private var targetNormalized = 0f
        private var zoomAnimationRunning = false
        private var lastSnapRatio = Float.NaN
        private var cam2DragStartY = 0f
        private var cam2DragStartNormalized = 0f

        init {
            finePaint.color = DevelopUgandaFivemods8Theme.accentScrim(153)
            finePaint.strokeWidth = dp(1).toFloat()

            majorPaint.color = DevelopUgandaFivemods8Theme.content
            majorPaint.strokeWidth = dp(2).toFloat()

            labelPaint.color = DevelopUgandaFivemods8Theme.content
            labelPaint.textSize = dp(6).toFloat()
            labelPaint.typeface = Typeface.DEFAULT_BOLD
            labelPaint.textAlign = Paint.Align.CENTER

            background = null

            isClickable = true
            isFocusable = true
        }

        fun syncFromSeek() {
            val state = cameraActivity.v242ZoomSnapshot()
            minimum = state[0]
            maximum = state[1]
            current = state[2]

            val span = (maximum - minimum).coerceAtLeast(0.001f)
            val actual = ((current - minimum) / span).coerceIn(0f, 1f)

            if (!zoomAnimationRunning) {
                normalized = actual
                targetNormalized = actual
            }

            invalidate()
        }

        private fun snapZoomTarget(raw: Float): Float {
            val span = (maximum - minimum).coerceAtLeast(0.001f)
            val rawRatio = minimum + span * raw.coerceIn(0f, 1f)
            val candidates = floatArrayOf(0.5f, 1f, 2f, 3f, 5f, 10f)

            var selected = rawRatio
            var distance = Float.MAX_VALUE

            for (ratio in candidates) {
                if (ratio < minimum || ratio > maximum) continue
                val d = kotlin.math.abs(rawRatio - ratio)
                if (d < distance) {
                    selected = ratio
                    distance = d
                }
            }

            val threshold = kotlin.math.max(0.07f, rawRatio * 0.028f)
            val snapped = distance <= threshold

            if (snapped) {
                if (
                    lastSnapRatio.isNaN() ||
                    kotlin.math.abs(lastSnapRatio - selected) > 0.01f
                ) {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    lastSnapRatio = selected
                }
            } else {
                lastSnapRatio = Float.NaN
            }

            val finalRatio = if (snapped) selected else rawRatio
            return ((finalRatio - minimum) / span).coerceIn(0f, 1f)
        }

        private fun smoothZoomToTarget() {
            if (zoomAnimationRunning) return
            zoomAnimationRunning = true

            postOnAnimation(
                object : Runnable {
                    override fun run() {
                        val delta = targetNormalized - normalized

                        if (kotlin.math.abs(delta) < 0.0015f) {
                            normalized = targetNormalized
                            cameraActivity.v242SetZoomNormalized(normalized)
                            zoomAnimationRunning = false
                            syncFromSeek()
                            return
                        }

                        normalized += delta * 0.34f
                        cameraActivity.v242SetZoomNormalized(normalized)
                        invalidate()
                        postOnAnimation(this)
                    }
                }
            )
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val cx = width * 0.68f
            val centerY = height / 2f
            val spacing = dp(17).toFloat()
            val windowTop = centerY - spacing * 5f
            val windowBottom = centerY + spacing * 5f

            finePaint.alpha = 120
            canvas.drawLine(
                cx,
                windowTop,
                cx,
                windowBottom,
                finePaint
            )

            for (offset in -5..5) {
                val distance = kotlin.math.abs(offset)
                val y = centerY + offset * spacing
                val isCenter = offset == 0
                val majorTick = isCenter || offset % 2 == 0
                val paint = if (majorTick) majorPaint else finePaint

                paint.alpha = when {
                    isCenter -> 255
                    distance <= 2 -> 205
                    distance <= 4 -> 125
                    else -> 55
                }

                val len = when {
                    isCenter -> dp(12)
                    majorTick -> dp(8)
                    else -> dp(4)
                }

                canvas.drawLine(
                    cx - len,
                    y,
                    cx + dp(2),
                    y,
                    paint
                )
            }

            finePaint.alpha = 255
            majorPaint.alpha = 255

            canvas.drawLine(
                dp(3).toFloat(),
                centerY,
                width - dp(3).toFloat(),
                centerY,
                majorPaint
            )

            canvas.drawText(
                String.format(
                    java.util.Locale.US,
                    "%.1f×",
                    current
                ),
                cx,
                centerY - dp(8),
                labelPaint
            )
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cam2DragStartY = event.y
                    cam2DragStartNormalized = targetNormalized
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val usable =
                        (height - dp(40))
                            .coerceAtLeast(1)
                            .toFloat()

                    val raw =
                        (cam2DragStartNormalized +
                            (cam2DragStartY - event.y) /
                                usable)
                            .coerceIn(0f, 1f)

                    targetNormalized = snapZoomTarget(raw)
                    smoothZoomToTarget()
                    return true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    performClick()
                    return true
                }
            }

            return super.onTouchEvent(event)
        }

        override fun performClick(): Boolean {
            super.performClick()
            return true
        }

        private fun dp(value: Int): Int =
            (value * resources.displayMetrics.density).toInt()
    }
}
