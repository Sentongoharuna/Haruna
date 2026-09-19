package com.sentongoharuna.pulse

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * Dependency-free device gate for the five restored camera modes.
 *
 * API 36 no longer carries android.test.InstrumentationTestCase. This runner
 * speaks the standard instrumentation status protocol directly so the tests
 * still gate connectedDebugAndroidTest without adding a parallel test stack.
 */
class DevelopUgandaFiveModeInstrumentation : Instrumentation() {
    private val app: Context get() = targetContext
    private var current = 0

    override fun onStart() {
        super.onStart()
        Thread({ runSuite() }, "du-five-mode-tests").start()
    }

    private fun runSuite() {
        val tests = listOf<Pair<String, () -> Unit>>(
            "wrongTypedLivePreferenceMigratesAndLiveOpens" to ::wrongTypedLivePreferenceMigratesAndLiveOpens,
            "measuredStorageIsPositive" to ::measuredStorageIsPositive,
            "moderateThermalAndUnknownStorageAllowRecording" to ::moderateThermalAndUnknownStorageAllowRecording,
            "measuredCriticalPreflightRefuses" to ::measuredCriticalPreflightRefuses,
            "warningThresholdRequiresRecordedOverride" to ::warningThresholdRequiresRecordedOverride,
            "modeProfilesAreExactlyFiveInNavigatorOrder" to ::modeProfilesAreExactlyFiveInNavigatorOrder,
            "modeDestinationsResolveAndLaunch" to ::modeDestinationsResolveAndLaunch,
            "mainAndLiveProfileDataStayAbsent" to ::mainAndLiveProfileDataStayAbsent,
            "restoredFeatureModulesLoadAtRuntime" to ::restoredFeatureModulesLoadAtRuntime,
            "faultRingIsCappedAndVisible" to ::faultRingIsCappedAndVisible,
            "recordedModeComesOnlyFromStoredMetadata" to ::recordedModeComesOnlyFromStoredMetadata,
            "draftAndUntimedCaptionsCannotBurn" to ::draftAndUntimedCaptionsCannotBurn,
            "sealStateNeverDefaultsToVerified" to ::sealStateNeverDefaultsToVerified,
            "fivemods12IdentitySetsAreFixedAndDistinct" to ::fivemods12IdentitySetsAreFixedAndDistinct,
            "fivemods12FilenamePrefixesAreStable" to ::fivemods12FilenamePrefixesAreStable,
            "fivemods12PreflightAlwaysReportsEightHonestItems" to ::fivemods12PreflightAlwaysReportsEightHonestItems,
            "cameraStatusHasOneOwnerAtLargestFontScale" to ::cameraStatusHasOneOwnerAtLargestFontScale,
        )
        grantRuntimePermissions()
        for ((name, body) in tests) {
            current += 1
            sendTestStatus(STATUS_START, name, tests.size, null)
            try {
                body()
                sendTestStatus(STATUS_OK, name, tests.size, null)
            } catch (failure: Throwable) {
                sendTestStatus(STATUS_FAILURE, name, tests.size, failure)
                finish(
                    Activity.RESULT_CANCELED,
                    Bundle().apply {
                        putString("stream", "\nFAIL: $name\n${failure.stackTraceToString()}\n")
                        putString("shortMsg", failure.message ?: failure.javaClass.simpleName)
                    },
                )
                return
            }
        }
        finish(
            Activity.RESULT_OK,
            Bundle().apply { putString("stream", "\nOK (${tests.size} device tests)\n") },
        )
    }

    private fun sendTestStatus(code: Int, name: String, total: Int, failure: Throwable?) {
        sendStatus(code, Bundle().apply {
            putString("class", javaClass.name)
            putString("test", name)
            putInt("current", current)
            putInt("numtests", total)
            putString("stream", if (failure == null) "\n$name" else "\n${failure.stackTraceToString()}")
            failure?.let {
                putString("stack", it.stackTraceToString())
                putString("shortMsg", it.message ?: it.javaClass.simpleName)
            }
        })
    }

    private fun grantRuntimePermissions() {
        for (permission in arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )) {
            uiAutomation.executeShellCommand("pm grant ${app.packageName} $permission").close()
        }
    }

    private fun modeProfilesAreExactlyFiveInNavigatorOrder() {
        val expected = listOf(
            DevelopUgandaCameraPage.MAIN,
            DevelopUgandaCameraPage.LIVE,
            DevelopUgandaCameraPage.TIKTOK,
            DevelopUgandaCameraPage.STATUS,
            DevelopUgandaCameraPage.INTERVIEW,
        )
        val profiles = DevelopUgandaModeProfiles.all()
        require(expected == profiles.map { it.page }) { "Navigator order changed" }
        profiles.forEach { profile ->
            require(profile == DevelopUgandaModeProfiles.forExperience(profile.experienceId))
            require(profile == DevelopUgandaModeProfiles.forExperience(profile.page.name))
        }
    }

    private fun wrongTypedLivePreferenceMigratesAndLiveOpens() {
        app.getSharedPreferences("develop_uganda_live_camera", Context.MODE_PRIVATE)
            .edit().putString("audio", "ON").commit()
        app.getSharedPreferences("develop_uganda_preference_migrations", Context.MODE_PRIVATE)
            .edit().clear().commit()
        DevelopUgandaPreferenceMigration.run(app)
        val repaired = app.getSharedPreferences(
            "develop_uganda_live_camera",
            Context.MODE_PRIVATE,
        ).all["audio"]
        require(repaired is Boolean && repaired) {
            "LIVE audio was not migrated from String to Boolean"
        }
        val activity = startActivitySync(
            Intent(app, DevelopUgandaLiveActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        waitForIdleSync()
        require(activity is DevelopUgandaLiveActivity)
        runOnMainSync { activity.finish() }
        waitForIdleSync()
    }

    private fun measuredStorageIsPositive() {
        val measurement = DevelopUgandaV276RecordingSafety.storageMeasurement(app)
        require((measurement.freeBytes ?: 0L) > 0L) {
            "Device has writable space but measurement was ${measurement.reason}"
        }
    }

    private fun moderateThermalAndUnknownStorageAllowRecording() {
        require(!DevelopUgandaV276RecordingSafety.thermalBlocksTake(PowerManager.THERMAL_STATUS_MODERATE))
        val items = honestReadyItems().map {
            if (it.id == "storage") it.copy(
                state = DevelopUgandaFivemods12Availability.UNKNOWN,
                reason = "Free space query unavailable in this test state.",
            ) else it
        }
        require(DevelopUgandaFivemods12PreflightSnapshot(DevelopUgandaCameraPage.INTERVIEW, items).mayRecord) {
            "UNKNOWN storage blocked a take"
        }
    }

    private fun measuredCriticalPreflightRefuses() {
        val items = honestReadyItems().map {
            if (it.id == "temperature") it.copy(
                state = DevelopUgandaFivemods12Availability.UNAVAILABLE,
                reason = "SEVERE measured; SEVERE start-block threshold crossed.",
            ) else it
        }
        require(!DevelopUgandaFivemods12PreflightSnapshot(DevelopUgandaCameraPage.INTERVIEW, items).mayRecord) {
            "Measured critical thermal state did not refuse"
        }
    }

    private fun warningThresholdRequiresRecordedOverride() {
        val warning = DevelopUgandaV276RecordingSafety.RecordingPreflight(
            blocked = emptyList(),
            warnings = listOf("STORAGE UNKNOWN • RECORDING ALLOWED"),
            ready = emptyList(),
            overridable = listOf(
                "BATTERY MEASURED 9% • BELOW 10% OPERATOR WARNING THRESHOLD",
            ),
        )
        require(warning.mayStart) { "Warning threshold was treated as critical" }
        require(warning.needsOperatorOverride) { "Measured warning lost its manual override gate" }
        val recordedReason = warning.overridable.single() + " • OPERATOR START ANYWAY"
        require(recordedReason.contains("MEASURED 9%") && recordedReason.endsWith("OPERATOR START ANYWAY")) {
            "Override reason is not suitable for the take sidecar"
        }

        val critical = DevelopUgandaV276RecordingSafety.RecordingPreflight(
            blocked = listOf("THERMAL MEASURED SEVERE • SEVERE START-BLOCK THRESHOLD CROSSED"),
            warnings = emptyList(),
            ready = emptyList(),
        )
        require(!critical.mayStart) { "Measured critical state became overridable" }
        require(!critical.needsOperatorOverride) { "Critical state incorrectly offered an override" }
    }

    private fun honestReadyItems(): List<DevelopUgandaFivemods12PreflightItem> =
        listOf("camera", "microphone", "storage", "permissions", "gps", "network", "battery", "temperature")
            .map { id ->
                DevelopUgandaFivemods12PreflightItem(
                    id = id,
                    label = id.uppercase(),
                    state = DevelopUgandaFivemods12Availability.READY,
                    reason = "Measured ready.",
                    fix = DevelopUgandaFivemods12Fix.NONE,
                )
            }

    private fun modeDestinationsResolveAndLaunch() {
        val expected = linkedMapOf<Class<out Activity>, DevelopUgandaCameraPage>(
            DevelopUgandaAllProCameraActivity::class.java to DevelopUgandaCameraPage.MAIN,
            DevelopUgandaLiveActivity::class.java to DevelopUgandaCameraPage.LIVE,
            DevelopUgandaTikTokCameraActivity::class.java to DevelopUgandaCameraPage.TIKTOK,
            DevelopUgandaStatusCameraActivity::class.java to DevelopUgandaCameraPage.STATUS,
            DevelopUgandaInterviewCameraActivity::class.java to DevelopUgandaCameraPage.INTERVIEW,
        )
        DevelopUgandaModeProfiles.all().forEach { profile ->
            require(profile.page == expected[profile.destination]) { "Wrong destination for ${profile.page}" }
            val intent = Intent(app, profile.destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            require(app.packageManager.resolveActivity(intent, 0) != null) { "Unresolvable ${profile.destination}" }
            val activity = startActivitySync(intent)
            require(profile.destination == activity.javaClass) { "Launch returned ${activity.javaClass}" }
            waitForIdleSync()
            runOnMainSync { activity.finish() }
            waitForIdleSync()
        }
    }

    private fun mainAndLiveProfileDataStayAbsent() {
        for (page in listOf(DevelopUgandaCameraPage.MAIN, DevelopUgandaCameraPage.LIVE)) {
            val profile = DevelopUgandaModeProfiles.forPage(page)
            require(profile.profileData == DevelopUgandaModeDataAvailability.ABSENT)
            require(!profile.hasProfileData)
            require(profile.frameRate == null && profile.targetBitrateBps == null)
            require(profile.qualityName == "ABSENT")
            require(profile.stages.isNotEmpty())
            require(profile.stages.all { it.availability == DevelopUgandaStageAvailability.ABSENT }) {
                "${page.name} manufactured stage readiness"
            }
        }
    }

    private fun restoredFeatureModulesLoadAtRuntime() {
        val names = listOf(
            "DevelopUgandaV255ProMonitorView", "DevelopUgandaV262RemoteDirectorActivity",
            "DevelopUgandaV263MultiCamDirectorActivity", "DevelopUgandaV264LiveCutDirectorActivity",
            "DevelopUgandaV265ProxyManager", "DevelopUgandaV269StoryDeskStore",
            "DevelopUgandaV270Guidance", "DevelopUgandaV271LiveCoach",
            "DevelopUgandaV272FieldSoundContinuity", "DevelopUgandaV273MotionShotControl",
            "DevelopUgandaV274MediaVaultStore", "DevelopUgandaV275ControlSurface",
            "DevelopUgandaV276RecordingSafety", "DevelopUgandaV277LightingExposure",
            "DevelopUgandaV278LiveWorkflow", "DevelopUgandaV279ActiveShooting",
            "DevelopUgandaV280SmartDirector",
        )
        require(names.size == 17)
        names.forEach { name ->
            requireNotNull(Class.forName("com.sentongoharuna.pulse.$name", true, app.classLoader))
        }
    }

    private fun faultRingIsCappedAndVisible() {
        app.getSharedPreferences("develop_uganda_v28012_runtime_guard", Context.MODE_PRIVATE)
            .edit().clear().commit()
        repeat(25) { index ->
            DevelopUgandaV28012RuntimeGuard.recordUiFault(
                app,
                "TEST PANEL $index",
                IllegalStateException("fault-$index"),
            )
        }
        val faults = DevelopUgandaV28012RuntimeGuard.uiFaults(app)
        require(faults.size == 20 && faults.first().area == "TEST PANEL 5" && faults.last().area == "TEST PANEL 24")

        val activity = startActivitySync(
            Intent(app, DevelopUgandaGeneralHubActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        ) as DevelopUgandaGeneralHubActivity
        val visibleFault = DevelopUgandaV28012RuntimeGuard.recordUiFault(
            activity,
            "V278 WORKFLOW",
            IllegalArgumentException("forced test panel failure"),
        )
        runOnMainSync {
            val method = DevelopUgandaGeneralHubActivity::class.java.getDeclaredMethod(
                "showUiFault",
                DevelopUgandaV28012RuntimeGuard.UiFault::class.java,
            )
            method.isAccessible = true
            method.invoke(activity, visibleFault)
        }
        waitForIdleSync()
        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        require(findText(root) { it.contains("V278 WORKFLOW") && it.contains("IllegalArgumentException") })
        runOnMainSync { activity.finish() }
    }

    private fun recordedModeComesOnlyFromStoredMetadata() {
        app.getSharedPreferences("develop_uganda_v274_media_vault", Context.MODE_PRIVATE)
            .edit().clear().commit()
        DevelopUgandaV274MediaVaultStore.onClipFinalized(
            app, "KNOWN.mp4", "content://test/known", 1_000L, 10L,
            DevelopUgandaV271LiveCoach.MODE_CLEAN, "TEST", "legacy camera", 1, 1,
            emptyList(), DevelopUgandaCameraPage.TIKTOK,
        )
        DevelopUgandaV274MediaVaultStore.onClipFinalized(
            app, "OLD_WITHOUT_ENUM.mp4", "content://test/old", 1_000L, 10L,
            DevelopUgandaV271LiveCoach.MODE_CLEAN, "TEST", "REPORT", 1, 2,
            emptyList(), null,
        )
        require(DevelopUgandaV274MediaVaultStore.modeForMedia(app, "content://test/known", "KNOWN.mp4") == DevelopUgandaCameraPage.TIKTOK)
        require(DevelopUgandaV274MediaVaultStore.modeForMedia(app, "content://test/old", "OLD_WITHOUT_ENUM.mp4") == null)
    }

    private fun draftAndUntimedCaptionsCannotBurn() {
        val timed = listOf(DevelopUgandaCaptionCue(1, 0L, 1_500L, "confirmed words"))
        require(!DevelopUgandaCaptionReview.burnAllowed("DRAFT_REVIEW_REQUIRED", timed))
        require(!DevelopUgandaCaptionReview.burnAllowed("CONFIRMED_UNTIMED", timed))
        require(!DevelopUgandaCaptionReview.burnAllowed("CONFIRMED", emptyList()))
        require(DevelopUgandaCaptionReview.burnAllowed("CONFIRMED", timed))
    }

    private fun sealStateNeverDefaultsToVerified() {
        app.getSharedPreferences("develop_uganda_seal_registry_v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
        require(
            DevelopUgandaSealRegistry.stateFor(app, "content://test/no-sidecar") ==
                DevelopUgandaSealState.MISSING_SIDECAR,
        )
    }

    private fun fivemods12IdentitySetsAreFixedAndDistinct() {
        val identities = DevelopUgandaFivemods12Identity.all()
        require(identities.size == 5)
        require(identities.map { it.page } == DevelopUgandaCameraPage.values().toList())
        require(identities.map { it.code } == listOf("MCAM", "LIVE", "TIK", "STAT", "INTV"))
        require(identities.map { it.code }.distinct().size == 5)
        require(identities.map { it.iconLabel }.distinct().size == 5)
        require(identities.map { it.accent }.distinct().size == 5)
        identities.forEach { identity ->
            require(identity.name == DevelopUgandaModeProfiles.forPage(identity.page).displayName)
        }
    }

    private fun fivemods12FilenamePrefixesAreStable() {
        DevelopUgandaFivemods12Identity.all().forEach { identity ->
            val stem = DevelopUgandaFivemods12Identity.prefixedStem(identity.page, "DU_TEST_TAKE")
            require(stem.startsWith("${identity.code}_")) { "Missing prefix for ${identity.page}" }
            require(DevelopUgandaFivemods12Identity.prefixedStem(identity.page, stem) == stem) {
                "Prefix was duplicated for ${identity.page}"
            }
        }
    }

    private fun fivemods12PreflightAlwaysReportsEightHonestItems() {
        DevelopUgandaCameraPage.values().forEach { mode ->
            val snapshot = DevelopUgandaFivemods12Preflight.snapshot(app, mode, log = false)
            require(snapshot.items.map { it.id } == listOf(
                "camera", "microphone", "storage", "permissions",
                "gps", "network", "battery", "temperature",
            ))
            require(snapshot.items.all { it.reason.isNotBlank() })
        }
    }

    private fun cameraStatusHasOneOwnerAtLargestFontScale() {
        val previous = ParcelFileDescriptor.AutoCloseInputStream(
            uiAutomation.executeShellCommand("settings get system font_scale"),
        ).bufferedReader().use { it.readText().trim().ifBlank { "1.0" } }
        try {
            uiAutomation.executeShellCommand("settings put system font_scale 2.0").close()
            for (destination in listOf(
                DevelopUgandaLiveActivity::class.java,
                DevelopUgandaInterviewCameraActivity::class.java,
            )) {
                val activity = startActivitySync(
                    Intent(app, destination).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                waitForIdleSync()
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                val strips = findTagged(root, "f12_status_strip")
                require(strips.size == 1) { "${destination.simpleName} rendered ${strips.size} status owners" }
                val strip = strips.single() as ViewGroup
                require(findTagged(strip, "record_tally").size == 1) { "Record tally missing or duplicated" }
                require(strip.bottom <= root.height / 2) { "Status strip collides with lower truth/transport region" }
                visibleText(strip).forEach { textView ->
                    require(textView.lineCount <= 1) { "Status text wrapped: ${textView.text}" }
                    require(textView.width == 0 || textView.paint.measureText(textView.text.toString()) <=
                        textView.width - textView.paddingLeft - textView.paddingRight + 3f) {
                        "Status text truncated: ${textView.text}"
                    }
                }
                runOnMainSync { activity.finish() }
                waitForIdleSync()
            }
        } finally {
            uiAutomation.executeShellCommand("settings put system font_scale $previous").close()
        }
    }

    private fun findTagged(view: View, tag: String): List<View> {
        val found = mutableListOf<View>()
        if (view.tag == tag) found += view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) found += findTagged(view.getChildAt(index), tag)
        }
        return found
    }

    private fun visibleText(view: View): List<TextView> {
        val found = mutableListOf<TextView>()
        if (view is TextView && view.visibility == View.VISIBLE) found += view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) found += visibleText(view.getChildAt(index))
        }
        return found
    }

    private fun findText(view: View, predicate: (String) -> Boolean): Boolean {
        if (view is TextView && view.visibility == View.VISIBLE && predicate(view.text.toString())) return true
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) if (findText(view.getChildAt(index), predicate)) return true
        }
        return false
    }

    companion object {
        private const val STATUS_START = 1
        private const val STATUS_OK = 0
        private const val STATUS_FAILURE = -2
    }
}
