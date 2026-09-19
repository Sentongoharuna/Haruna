#!/usr/bin/env python3
"""Source-contract gate carried forward through the verified FIVEMODS 12 chain."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = ROOT / "app/src/main/java/com/sentongoharuna/pulse"
EVIDENCE = ROOT / "build/verification"


def need(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"SOURCE CONTRACT FAILED: {message}")


def read(name: str) -> str:
    path = PACKAGE / name
    need(path.is_file(), f"required source missing: {name}")
    return path.read_text(encoding="utf-8")


def selected(text: str, start: str, end: str, label: str) -> str:
    need(text.count(start) == 1, f"{label} start anchor changed")
    need(text.count(end) == 1, f"{label} end anchor changed")
    begin = text.index(start)
    return text[begin : text.index(end, begin)]


camera = read("DevelopUgandaCameraActivity.kt")

# The original protected construction block contains 1/4/1. Two later,
# separately protected state updates bring the complete-file inventory to
# 2/4/2. Checking both catches a moved, added, or removed preview scrim.
camera_ui = selected(
    camera,
    "    private fun buildUi() {",
    "\n    private fun requestPermissionsAndStart()",
    "camera UI",
)
for alpha, expected in {38: 1, 66: 4, 82: 1}.items():
    found = camera_ui.count(f"DevelopUgandaFivemods8Theme.surfaceScrim({alpha})")
    need(found == expected, f"camera UI surfaceScrim({alpha}) {found} != {expected}")
for alpha, expected in {38: 2, 66: 4, 82: 2}.items():
    found = camera.count(f"DevelopUgandaFivemods8Theme.surfaceScrim({alpha})")
    need(found == expected, f"whole-file surfaceScrim({alpha}) {found} != {expected}")
need(
    "du_viewfinder_indicator_backing" not in camera,
    "opaque viewfinder backing token reached CameraActivity",
)

exposure = selected(
    camera,
    "    // FIVEMODS 10: the phone's ambient-light sensor",
    "\n    private fun f9UpdateAudioTelemetry(",
    "FIVEMODS 10 exposure",
)
need("ambientLux" not in exposure and "requestedEv" not in exposure,
     "ambient-light exposure override returned")
need("sceneExposureTarget.coerceIn(" in exposure,
     "operator/neutral AE policy changed")
need(exposure.count("setExposureCompensationIndex(requestedIndex)") == 1,
     "exposure write inventory changed")

controls = selected(
    camera,
    "    private fun v244ApplyCamera2Controls(): String {",
    "\n    internal fun v244CycleShutterAngle(): String {",
    "Camera2 controls",
)
need(controls.count("CaptureRequest.CONTROL_AE_MODE_OFF") == 1,
     "manual exposure branch changed")
need(controls.count("CaptureRequest.CONTROL_AE_MODE_ON") == 1,
     "automatic exposure branch changed")
need(controls.count("CaptureRequest.CONTROL_AE_LOCK,\n                        false") == 1,
     "Interview AE unlock changed")
need(camera.count("f9InterviewFrameRateRange") == 3,
     "Interview FPS helper inventory changed")

navigator = read("DevelopUgandaCameraModeNavigator.kt")
for anchor in (
    "CAMERA_HANDOFF_SETTLE_MS = 850L",
    "releaseCamera()",
    "outer.postDelayed(",
    "DevelopUgandaCameraModeRoutes.launch(activity, destination)",
):
    need(navigator.count(anchor) == 1, f"release handoff changed: {anchor}")

profiles = read("DevelopUgandaModeProfiles.kt")
need(
    re.search(
        r"navigatorOrder\s*=\s*listOf\(\s*main\s*,\s*live\s*,\s*tikTok\s*,\s*status\s*,\s*interview\s*\)",
        profiles,
    ) is not None,
    "five-mode navigator order changed",
)
need(profiles.count("stages = listOf(") == 5, "per-mode stage lists missing")
need(profiles.count("profileData = DevelopUgandaModeDataAvailability.ABSENT") == 2,
     "MAIN/LIVE ABSENT contract changed")
need(profiles.count("DevelopUgandaStageAvailability.ABSENT") == 10,
     "MAIN/LIVE stage shells must remain explicitly ABSENT")
for required in ("STREAM_KEY", "CONNECTION", "SEGMENT", "SHARE", "SUBJECT_LOCK", "MARKERS"):
    need(f'"{required}"' in profiles, f"per-mode stage missing: {required}")

presentation = read("DevelopUgandaBroadcastPresentation.kt")
need("enum class DevelopUgandaButtonWeight" in presentation,
     "shared button system missing")
for state in ("PRIMARY", "SECONDARY", "TERTIARY", "DESTRUCTIVE"):
    need(state in presentation, f"button weight missing: {state}")
need("useDevelopUgandaMotion" in presentation and "systemReduceMotion" in presentation,
     "shared motion/reduce-motion routing missing")

readout = read("DevelopUgandaLiveReadout.kt")
for anchor in (
    "DevelopUgandaRollingMetricBuffer",
    "60_000L",
    "DevelopUgandaMetricTrendView",
    "DevelopUgandaMotionLevel",
    "OFF",
    "STANDARD",
    "FULL",
):
    need(anchor in readout, f"metric/motion component missing: {anchor}")
need("TRANSITION_ANIMATION_SCALE" in readout,
     "system reduce-motion setting is not respected")

chrome = read("DevelopUgandaBroadcastCameraChrome.kt")
for anchor in (
    "ACTION SAFE", "TITLE SAFE", "CLEAN", "HOLD UNLOCK",
    "left_handed", "dark_adapt", "screen_dim", "OrientationEventListener",
):
    need(anchor in chrome, f"camera chrome capability missing: {anchor}")
need("Handler(" not in chrome and "postDelayed(" not in chrome,
     "camera chrome introduced a timer")
need(re.search(r"0x[0-9A-Fa-f]{6,8}|#[0-9A-Fa-f]{6,8}", chrome) is None,
     "camera chrome introduced a colour literal")
need("DevelopUgandaBroadcastCameraChrome.attach(" in camera,
     "shared chrome is detached from CameraActivity")
need("DevelopUgandaBroadcastCameraChrome.attach(" in read("DevelopUgandaLiveActivity.kt"),
     "shared chrome is detached from LiveActivity")
for duplicate in ("statusStrip", 'field("MODE"', 'field("FORMAT"', 'field("CODEC"',
                  'field("TC"', 'field("BAT"', 'field("FREE"', 'field("AUDIO"'):
    need(duplicate not in chrome, f"legacy duplicate camera readout returned: {duplicate}")

motion = read("DevelopUgandaV273MotionShotControl.kt")
need("color = DevelopUgandaFivemods8Theme.outline" in motion,
     "motion guide lines no longer use outline")
need("color = DevelopUgandaFivemods8Theme.accent" in motion,
     "motion guide centre no longer uses accent")

captions = read("DevelopUgandaReviewedCaptions.kt")
engine = read("DevelopUgandaTranscriptEngine.kt")
for anchor in (
    "DRAFT_REVIEW_REQUIRED", "CONFIRMED_UNTIMED", "caption_burn_allowed",
    "DevelopUgandaReviewedCaptionOverlay", "WORD CONFIDENCE UNAVAILABLE",
    "PACE", "PAUSE", "seal_scope",
):
    need(anchor.lower() in captions.lower(), f"reviewed caption contract missing: {anchor}")
need("No cloud recognizer was substituted" in engine,
     "on-device-only recognizer honesty contract missing")

exporter = read("DevelopUgandaFivemods9DualOutputExporter.kt")
need("overlays += DevelopUgandaReviewedCaptionOverlay(cues)" in exporter,
     "reviewed captions left the BRAND overlay chain")
need("prepareReviewedCaptions" in exporter and "DevelopUgandaCaptionReview.prepare" in exporter,
     "mandatory review gate missing")
for contract in (
    "clean.videoSamples != brand.videoSamples",
    "Timeline origin differs",
    "Frame timestamp sequence differs",
    "CLEAN SAVED • BRAND MISSING",
):
    need(contract in exporter, f"CLEAN/BRAND contract changed: {contract}")

# FIVEMODS 12 additions are additive around the protected capture/encoding
# chain. They must remain measurable, bounded and explicit about UNKNOWN.
identity12 = read("DevelopUgandaFivemods12Identity.kt")
for code in ("MCAM", "LIVE", "TIK", "STAT", "INTV"):
    need(f'"{code}"' in identity12, f"fixed mode identity missing: {code}")
need(identity12.count("DevelopUgandaFivemods12ModeIdentity(") == 6,
     "five identity records changed")  # data-class declaration + five records
for anchor in (
    "cleanBytesModified\", false",
    "writeCleanSidecar",
    "prefixedStem",
    "modeCode",
    "modeName",
    "mediaStoreNameWhere",
    "isAppMediaName",
):
    need(anchor in identity12, f"CLEAN identity sidecar contract missing: {anchor}")

reliability12 = read("DevelopUgandaFivemods12Reliability.kt")
for check in ("camera(context)", "microphone(context)", "storage(context, mode)",
              "permissions(context)", "gps(context)", "network(context)",
              "battery(context)", "temperature(context)"):
    need(check in reliability12, f"eight-item preflight missing: {check}")
for state in ("READY", "LIMITED", "UNAVAILABLE", "UNKNOWN"):
    need(state in reliability12, f"preflight state missing: {state}")
for result in ("RECOVERED", "PARTLY RECOVERED", "COULD NOT RECOVER"):
    need(result in reliability12, f"recovery result missing: {result}")
for privacy in ("REDACTED_URL", "REDACTED_COORDINATE", "stream keys", "exception messages"):
    need(privacy in reliability12, f"privacy-safe diagnostics contract missing: {privacy}")

field_ui12 = read("DevelopUgandaFivemods12FieldUi.kt")
for anchor in (
    "MAX_RESTARTS = 2",
    "FRAME_TIMEOUT_MS = 4_500L",
    "PREVIEW UNAVAILABLE • STOPPED AFTER 2 RESTARTS",
    "DevelopUgandaFivemods12RecordingProtection.state",
    "DevelopUgandaBroadcastPresentation.motionLevel",
    "RTMPS network encoder has no approved identity overlay hook",
    "RECOVERY 100%",
    "UPLOAD $uploadProgress",
):
    need(anchor in field_ui12, f"field reliability UI contract missing: {anchor}")
need("ImageAnalysis" not in field_ui12 and "Recorder.Builder" not in field_ui12,
     "preview recovery introduced a new capture/encoding path")
need('tag = "f12_status_strip"' in field_ui12,
     "single FIVEMODS 12 camera-status owner missing")
for field in ("mode", "format", "codec", "timecode", "storage", "battery", "audio"):
    need(field_ui12.count(f"val {field} = metric()") == 1,
         f"{field} status is not created from exactly one call site")
need("root.addView(\n            previewNarrationPanel" not in camera,
     "legacy CameraActivity status/narration panel is visible")
live_source = read("DevelopUgandaLiveActivity.kt")
need("root.addView(\n            topPanel" not in live_source,
     "legacy LIVE status panel is visible")
need("identity?.burnLabel" in exporter,
     "mode code + name left the existing BRAND overlay path")
need("videoEffects += f12DeliveryIdentityOverlay(request.profile)" in camera,
     "social/status delivery copies lost their existing Media3 identity overlay")

theme12 = read("DevelopUgandaFivemods8Theme.kt")
for anchor in (
    "DevelopUgandaFivemods12LiveDigits",
    "previous != current",
    "UNKNOWN\" !in upper",
    "DevelopUgandaMotionLevel.OFF",
    "isImmersiveCamera(activity)",
    "WindowInsetsCompat.Type.statusBars()",
):
    need(anchor in theme12, f"global live-digit/readability contract missing: {anchor}")

# Typed-preference crash containment. Native preference access is allowed only
# inside the guarded proxy, and every typed getter is implemented there.
safe_preferences = read("DevelopUgandaSafePreferences.kt")
for source_path in PACKAGE.rglob("*.kt"):
    if source_path.name == "DevelopUgandaSafePreferences.kt":
        continue
    source_text = source_path.read_text(encoding="utf-8")
    need("getSharedPreferences(" not in source_text,
         f"raw SharedPreferences factory outside guard: {source_path.name}")
for getter in ("getBoolean", "getInt", "getLong", "getFloat", "getString", "getStringSet"):
    need(f"override fun {getter}" in safe_preferences,
         f"guarded preference getter missing: {getter}")
need('develop_uganda_live_camera/audio' in safe_preferences and '"ON"' in safe_preferences,
     "LIVE audio String-to-Boolean migration missing")
need("DevelopUgandaPreferenceMigration.run(this)" in theme12,
     "typed-preference launch migration is not installed")

# Storage must be measured on a directory this app can actually stat; unknown
# bitrate may hide take capacity, never the measured free-space value.
recording_safety = read("DevelopUgandaV276RecordingSafety.kt")
for anchor in ("StorageManager", "StatFs", "getExternalFilesDir", "availableBytes", "StorageMeasurement"):
    need(anchor in recording_safety, f"measured storage path missing: {anchor}")
need("NO MEASURED ${mode.name} BITRATE" in reliability12,
     "unknown bitrate is no longer distinguished from storage measurement")
need("THERMAL_STATUS_SEVERE" in recording_safety and "thermalBlocksTake" in recording_safety,
     "measured critical thermal threshold changed")
need("UNKNOWN is never a refusal" in reliability12,
     "unknown preflight policy is no longer explicit")
for anchor in (
    "needsOperatorOverride",
    "BATTERY MEASURED $battery%",
    "STORAGE MEASURED FOR ${availableMinutes}MIN",
):
    need(anchor in recording_safety, f"operator warning override policy missing: {anchor}")
for activity_name in (
    "DevelopUgandaCameraActivity.kt",
    "DevelopUgandaLiveActivity.kt",
    "DevelopUgandaRtmpsLiveActivity.kt",
):
    activity_source = read(activity_name)
    need("OPERATOR START ANYWAY" in activity_source,
         f"recorded warning override missing: {activity_name}")

dialog_styler = read("DevelopUgandaDialogStyler.kt")
need("chromeAccent" not in dialog_styler and "forPage(it).accent" in dialog_styler,
     "dialog actions are not routed through mode identity accent")
styles = (ROOT / "app/src/main/res/values/styles.xml").read_text(encoding="utf-8")
need("DUAlertDialogTheme" in styles and "DUDialogAction" in styles,
     "shared dialog accent/type route missing")
for dialog_size in ("13sp", "11sp"):
    need(dialog_size in styles, f"compact dialog type missing: {dialog_size}")

# Multi-clip assembly must stay inside the existing one-pass Media3 BRAND
# exporter. Only CONFIRMED timed captions may enter and CLEAN inputs are never
# opened for writing.
brand_identity = read("DevelopUgandaBrandIdentity.kt")
for anchor in (
    "lowerThirdStyles",
    "DevelopUgandaTimedBrandTextOverlay",
    "CONTACT • develop.uganda",
    "includeEndCard",
    "identity?.burnLabel",
):
    need(anchor in brand_identity, f"shared five-mode BRAND identity missing: {anchor}")
for anchor in (
    "fun assembleBrand(",
    "EditedMediaItemSequence.withAudioAndVideoFrom(editedItems)",
    "DevelopUgandaBrandIdentity.overlays(",
    "DevelopUgandaTranscriptArchive.confirmedForMedia(",
    "DevelopUgandaTranscriptArchive.retimeForAssembly(",
    "expected.videoSamples != brand.videoSamples",
    "Timeline origin differs",
    "Frame timestamp sequence differs",
    "CLEAN SOURCES PRESERVED",
):
    need(anchor in exporter, f"assembly/CLEAN contract missing: {anchor}")
editor = read("DevelopUgandaEditorActivity.kt")
for anchor in (
    "OpenMultipleDocuments",
    "ASSEMBLE BRAND",
    "DevelopUgandaTakeMarks.forClip",
    "moveAssemblyClip",
    "trimStartMs",
    "trimEndMs",
):
    need(anchor in editor, f"ordered mark-aware editor assembly missing: {anchor}")
for anchor in ("confirmedForMedia", "retimeForAssembly", "DRAFT_REVIEW_REQUIRED", "CONFIRMED_UNTIMED"):
    need(anchor in captions, f"joined caption review contract missing: {anchor}")

# Compact instrument typography and flat chrome. The 40sp token is selected
# only by the explicit record-tally tag or the tapped-number sheet request.
for anchor in (
    'view.tag == "du_record_tally" -> 40f',
    'view.tag == "du_hero_value" -> 22f',
    'view is Button -> 11f',
    'view.tag == "du_page_title" -> 13f',
    'view.tag == "du_section_label" -> 11f',
    'view.tag == "du_telemetry_value"',
    'TypefaceSpan("monospace")',
    "view.elevation = 0f",
    "setShadowLayer(0f",
    "fun flattenChrome(root: View)",
    "view.translationZ = 0f",
):
    need(anchor in presentation, f"instrument presentation mapping missing: {anchor}")
theme_values = (ROOT / "app/src/main/res/values/develop_uganda_theme.xml").read_text(encoding="utf-8")
need('<dimen name="du_radius">2dp</dimen>' in theme_values,
     "single small instrument radius changed")
need('develop.uganda • V285 FIELD MASTER' in read("DevelopUgandaGeneralHubActivity.kt"),
     "single-line compact hub title missing")

# Flat instrument chrome is enforced both at runtime and in source. Solid
# GradientDrawable rectangles are allowed; shader gradients, depth shadows,
# positive elevation and animated glow rings are not.
instrument_source = "\n".join(
    path.read_text(encoding="utf-8") for path in PACKAGE.rglob("*.kt")
)
need(instrument_source.count("setShadowLayer(") == 2 and
     instrument_source.count("setShadowLayer(0f") == 2,
     "a non-zero drop shadow returned")
for forbidden in ("LinearGradient", "RadialGradient", "SweepGradient"):
    need(forbidden not in instrument_source, f"shader gradient returned: {forbidden}")
need("glow" not in instrument_source.lower(), "animated glow chrome returned")
need(re.search(r"\belevation\s*=\s*(?:dp\(|if\s*\(|[1-9]\d*(?:\.\d+)?f)", instrument_source) is None,
     "positive view elevation returned")
need("<gradient" not in "\n".join(
    path.read_text(encoding="utf-8") for path in (ROOT / "app/src/main/res").rglob("*.xml")
).lower(), "XML gradient returned")
need("DevelopUgandaBroadcastPresentation.flattenChrome(it)" in theme12,
     "flat chrome is not applied to camera activities")

# The named delivery workflow contains no embedded patch/overlay and the real
# device suite is a hard gate after explicit KVM enablement.
workflow_path = ROOT / ".github/workflows/00-UPLOAD-THIS-FIVEMODS-12-FAST-APK.yml"
need(workflow_path.is_file(), "named FIVEMODS 12.1 workflow missing")
workflow = workflow_path.read_text(encoding="utf-8")
for anchor in (
    "actions/checkout@v6",
    "actions/setup-java@v5",
    "gradle/actions/setup-gradle@v5",
    "reactivecircus/android-emulator-runner@v2",
    "sudo udevadm trigger --name-match=kvm",
    ":app:connectedDebugAndroidTest",
    "timeout-minutes: 90",
    "00-1-FIVEMODS12-RUN4-SOURCE.zip",
    "00-DOWNLOAD-NEW-FIVEMODS-12-1-RUN4-COMPILE-FIXED-APK-",
):
    need(anchor in workflow, f"gated ordinary-source workflow missing: {anchor}")
need("continue-on-error" not in workflow,
     "instrumented device suite is allowed to fail without failing the build")
need("base64" not in workflow.lower() and "F12_OVERLAY" not in workflow,
     "embedded source overlay returned to the named workflow")
need("SOURCE_ZIP_SHA256_REPLACED_DURING_PACKAGING" not in workflow,
     "source_hash was not updated for the packaged ordinary source")

# SAFE HOME regression gate: every printf-style call has an explicit fixed
# locale. There are no translated string resources containing raw percent
# format text in this project.
all_kotlin = "\n".join(path.read_text(encoding="utf-8") for path in PACKAGE.glob("*.kt"))
literal_format = re.compile(r'"(?:\\.|[^"\\])*"\.format\(\s*([^,\n)]+)', re.MULTILINE)
string_format = re.compile(r'String\.format\(\s*([^,\n)]+)', re.MULTILINE)
for match in list(literal_format.finditer(all_kotlin)) + list(string_format.finditer(all_kotlin)):
    need("Locale." in match.group(1),
         f"printf-style formatting lacks fixed Locale near: {match.group(0)[:80]}")
for values_file in (ROOT / "app/src/main/res/values").glob("*.xml"):
    xml = values_file.read_text(encoding="utf-8")
    for value in re.findall(r'<string\b[^>]*>(.*?)</string>', xml, flags=re.DOTALL):
        need(re.search(r'%(?!%|(?:\d+\$)?[-#+ 0,(<]*\d*(?:\.\d+)?[a-zA-Z])', value) is None,
             f"raw percent in translated text: {values_file.name}")

# These are the 17 restored feature modules whose loadability is also checked
# by the instrumented suite. The source gate protects their ordinary files.
feature_files = (
    "DevelopUgandaV255ProMonitorView.kt",
    "DevelopUgandaV262RemoteDirector.kt",
    "DevelopUgandaV263MultiCamDirector.kt",
    "DevelopUgandaV264LiveCutDirector.kt",
    "DevelopUgandaV265ProxySyncReview.kt",
    "DevelopUgandaV269SmartStoryDesk.kt",
    "DevelopUgandaV270Guidance.kt",
    "DevelopUgandaV271LiveCoach.kt",
    "DevelopUgandaV272FieldSoundContinuity.kt",
    "DevelopUgandaV273MotionShotControl.kt",
    "DevelopUgandaV274MediaVault.kt",
    "DevelopUgandaV275ControlSurface.kt",
    "DevelopUgandaV276RecordingSafety.kt",
    "DevelopUgandaV277LightingExposure.kt",
    "DevelopUgandaV278LiveWorkflow.kt",
    "DevelopUgandaV279ActiveShooting.kt",
    "DevelopUgandaV280SmartDirector.kt",
)
for name in feature_files:
    read(name)

runtime_guard = read("DevelopUgandaV28012RuntimeGuard.kt")
need("MAX_UI_FAULTS = 20" in runtime_guard, "fault ring capacity changed")
need("takeLast(MAX_UI_FAULTS - 1) + fault" in runtime_guard,
     "fault collection is no longer capped")

hub = read("DevelopUgandaGeneralHubActivity.kt")
need("STATUS • UNKNOWN • READ FAILED" in hub,
     "failed route read can no longer be distinguished")
flow = selected(
    hub,
    "    private fun buildFlowTrack(): View {",
    "\n    private fun panelBlock(): LinearLayout",
    "mode stage track",
)
need("DevelopUgandaModeProfiles.selected(this)" in flow and ".stages.forEachIndexed" in flow,
     "flow track is no longer driven by the selected mode stages")
need("postDelayed(" not in flow and "LinearInterpolator" not in flow,
     "flow track regained a decorative timer")
console = read("DevelopUgandaV28012ProWorkflowConsole.kt")
need("SnapshotUnavailableException" in console,
     "failed snapshot no longer reports explicit unavailability")
need("readiness = 70" not in console, "synthetic readiness returned")

newsroom = read("DevelopUgandaNewsroomActivity.kt")
need("UNKNOWN MODE" in newsroom or "UNKNOWN" in newsroom,
     "unknown clip state missing")
need("else -> \"REPORT\"" not in newsroom, "clip classifier guesses REPORT")

delivery = read("DevelopUgandaDeliveryQueue.kt")
for anchor in (
    "setPersisted(true)",
    "Content-Range",
    "X-Content-SHA256",
    "CLEAN MASTER NEEDS EXPLICIT UPLOAD CONSENT",
    "DevelopUgandaV276RecordingSafety.isCaptureActive",
    "WIFI_ONLY",
):
    need(anchor in delivery, f"delivery queue contract missing: {anchor}")

rtmps = read("DevelopUgandaRtmpsLiveActivity.kt")
for anchor in (
    "AndroidKeyStore",
    "migrateLegacyPlaintextKey",
    ".remove(\"key\")",
    "setLogs(false)",
    "QueueAwareBitrateAdapter",
    "LOCAL CLEAN BACKUP NOT READY • LIVE REFUSED",
    "startLocalSegment()",
    "startStream(target)",
    "DevelopUgandaCrashSafeTake.SEGMENT_DURATION_MS",
):
    need(anchor in rtmps, f"RTMPS safety contract missing: {anchor}")
need("rtmp://" not in rtmps, "unencrypted RTMP endpoint accepted")

seal = read("DevelopUgandaSealVerification.kt")
for anchor in (
    "MISSING_SIDECAR",
    "NOT_YET_CHECKED",
    "MessageDigest.getInstance(\"SHA-256\")",
    "exportManifest",
):
    need(anchor in seal, f"seal verification contract missing: {anchor}")
need("?: DevelopUgandaSealState.MISSING_SIDECAR" in seal,
     "unknown seal state can default to verified")

crash_safe = read("DevelopUgandaCrashSafeTake.kt")
for anchor in (
    "BOUNDED_SEGMENTED_CAPTURE",
    "PRESERVE_NEVER_OVERWRITE_OR_DELETE",
    "SEGMENT_DURATION_MS = 15_000L",
    "DevelopUgandaFivemods7Mp4FastStart.ensure",
):
    need(anchor in crash_safe, f"crash-safe capture contract missing: {anchor}")

sound = read("DevelopUgandaV272FieldSoundContinuity.kt")
for anchor in (
    "SAFETY_ATTENUATION = 0.25118864f",
    "tanh(normalized * 1.8)",
    "createWiredMonitor",
    "WIND/HANDLING HIGH",
):
    need(anchor in sound, f"field sound safety contract missing: {anchor}")

instrumentation = ROOT / "app/src/androidTest/java/com/sentongoharuna/pulse/DevelopUgandaFiveModeInstrumentation.kt"
need(instrumentation.is_file(), "five-mode device gate missing")
instrumentation_text = instrumentation.read_text(encoding="utf-8")
need('putString("audio", "ON")' in instrumentation_text,
     "wrong-typed LIVE audio regression fixture missing")
for anchor in (
    "modeProfilesAreExactlyFiveInNavigatorOrder",
    "modeDestinationsResolveAndLaunch",
    "mainAndLiveProfileDataStayAbsent",
    "sealStateNeverDefaultsToVerified",
    "draftAndUntimedCaptionsCannotBurn",
    "fivemods12IdentitySetsAreFixedAndDistinct",
    "fivemods12FilenamePrefixesAreStable",
    "fivemods12PreflightAlwaysReportsEightHonestItems",
    "wrongTypedLivePreferenceMigratesAndLiveOpens",
    "measuredStorageIsPositive",
    "moderateThermalAndUnknownStorageAllowRecording",
    "measuredCriticalPreflightRefuses",
    "warningThresholdRequiresRecordedOverride",
    "cameraStatusHasOneOwnerAtLargestFontScale",
):
    need(anchor in instrumentation_text, f"device test missing: {anchor}")

EVIDENCE.mkdir(parents=True, exist_ok=True)
report = {
    "result": "PASS",
    "surface_scrim_protected_block": {"38": 1, "66": 4, "82": 1},
    "surface_scrim_complete_file": {"38": 2, "66": 4, "82": 2},
    "feature_module_count": len(feature_files),
    "clean_master_captioned": False,
    "mode_count": 5,
    "main_live_profile_data": "ABSENT",
    "crash_safe_strategy": "BOUNDED_SEGMENTED_CAPTURE",
    "rtmps_key_storage": "ANDROID_KEYSTORE_AES_GCM",
    "delivery_arrival_proof": "SHA-256",
    "device_gate": instrumentation.name,
    "fivemods12_preflight_checks": 8,
    "fivemods12_preview_restart_limit": 2,
    "printf_locale_audit": "PASS",
}
(EVIDENCE / "source-contracts.json").write_text(
    json.dumps(report, indent=2) + "\n", encoding="utf-8"
)
print("PASS: committed-source contracts verified")
