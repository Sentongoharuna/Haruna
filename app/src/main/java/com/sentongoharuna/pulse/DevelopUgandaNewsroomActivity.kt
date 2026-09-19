package com.sentongoharuna.pulse

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

class DevelopUgandaNewsroomActivity : AppCompatActivity() {

    private lateinit var contentHost: FrameLayout

    private val gold = DevelopUgandaFivemods8Theme.accent
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val green = DevelopUgandaFivemods8Theme.accent
    private val red = DevelopUgandaFivemods8Theme.record
    private val white = DevelopUgandaFivemods8Theme.content
    private val ink = DevelopUgandaFivemods8Theme.surface
    private val card = DevelopUgandaFivemods8Theme.surfaceRaised
    private val muted = DevelopUgandaFivemods8Theme.contentDim

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )
        buildShell()
        showHome()
    }

    private fun buildShell() {
        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL
                setBackgroundColor(
                    ink
                )
            }

        val top =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    space(18),
                    space(11),
                    space(18),
                    space(8)
                )
                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surface
                )
            }

        top.addView(
            label(
                "develop.uganda",
                22f,
                gold,
                true
            ),
            LinearLayout.LayoutParams(
                0,
                dp(46),
                1f
            )
        )

        top.addView(
            label(
                "UNIFIED LIVE CONTROL DECK • V235\nCOLOR • DIRECTOR • BRAND • QC • ALL RETAINED",
                9f,
                white,
                true
            ).apply {
                gravity =
                    Gravity.CENTER_VERTICAL or
                        Gravity.END
            },
            LinearLayout.LayoutParams(
                dp(165),
                dp(46)
            )
        )

        root.addView(
            top,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66)
            )
        )

        contentHost =
            FrameLayout(this).apply {
                setBackgroundColor(
                    ink
                )
            }

        root.addView(
            contentHost,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        val nav = LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER
                setPadding(
                    space(8),
                    space(5),
                    space(8),
                    space(9)
                )
                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surface
                )
            }

        nav.addView(navButton("HOME", white) { showHome() }, LinearLayout.LayoutParams(dp(88), dp(56)))

        DevelopUgandaModeProfiles.all().forEach { profile ->
            nav.addView(
                navButton(profile.shortName, profile.chromeAccentColor) { openMode(profile) },
                LinearLayout.LayoutParams(space(88), space(56)).apply { marginStart = space(8) },
            )
        }

        nav.addView(
            navButton(
                "EDIT\nVIDEO",
                cyan
            ) {
                openEditor()
            },
            LinearLayout.LayoutParams(space(88), space(56)).apply { marginStart = space(8) }
        )

        nav.addView(
            navButton(
                "DESK\nSTORY",
                green
            ) {
                showNewsroom()
            },
            LinearLayout.LayoutParams(space(88), space(56)).apply { marginStart = space(8) }
        )

        val navScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(nav)
        }
        root.addView(
            navScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(68)
            )
        )

        setContentView(
            root
        )
        DevelopUgandaFivemods8Theme.enforceTouchTargets(root)
    }

    private fun showHome() {
        val scroll =
            ScrollView(this)

        val page =
            pageColumn()

        page.addView(
            hero(
                "develop.uganda NEWSROOM",
                DevelopUgandaModeProfiles.all().joinToString(" • ") { it.shortName } + " • EDIT • NEWSROOM"
            )
        )

        page.addView(
            compactStatus(
                "READY TO REPORT",
                "FIVE CAMERA ROUTES • EDIT • STORY DESK",
                green
            )
        )

        page.addView(
            compactStatus(
                "CAMERA MEMORY",
                "Each camera mode keeps its own route identity; profile data is shown only where it exists",
                gold
            )
        )

        page.addView(
            compactStatus(
                "QUICK PRESETS",
                "MAIN / LIVE retain their established controls • TIKTOK / STATUS / INTERVIEW use their additive mode profiles",
                cyan
            )
        )

        page.addView(
            compactStatus(
                "SAVED VIDEO VISIBILITY",
                "SHORT TELEMETRY RAIL • WIDER READABLE COLUMN • LIVE BUILD TAG AND BLINKING REC BADGE USE SEPARATE SAFE LANES",
                green
            )
        )


        page.addView(
            compactStatus(
                "AUTO VIEW • ON-DEVICE SCENE DESCRIPTION",
                "WHERE THE EXISTING MODE PROVIDES IT, PREVIEW SHOWS A BRIEF ON-DEVICE DESCRIPTION • SCREEN-ONLY SO A WRONG AI LABEL IS NOT PERMANENTLY BURNED INTO EVIDENCE FOOTAGE",
                DevelopUgandaFivemods8Theme.accent
            )
        )

        page.addView(
            compactStatus(
                "SHOT QUALITY GUARD • REAL SIGNALS",
                "TOO DARK • MIC CLIPPING • SHAKE HIGH • HORIZON OFF • THERMAL RISK • STORAGE LOW • GPS WEAK • FOCUS NOT CONFIRMED • FIELD PREFLIGHT • RECOVERY JOURNAL • SCREEN-ONLY PEAK/ZEBRA",
                DevelopUgandaFivemods8Theme.accent
            )
        )

        page.addView(
            compactStatus(
                "CREATOR CAMERA ENGINE",
                "V205→V222 INDEPENDENT CAMERAS RETAINED • V217 ADDS FULL-SCREEN CAMERA PREVIEW + POLISHED SAVED-VIDEO HUD • NOTHING DROPPED",
                cyan
            )
        )

        page.addView(
            compactStatus(
                "V227 • DIRECTOR & QC PRO",
                "SCREEN-ONLY REAL FACE COMPOSITION FOR PEOPLE/INTERVIEW • PREVIEW LUMA HISTOGRAM • ESTIMATED RECORD TIME • REAL CAMERA DEVICE MAP • SHOT CONTINUITY • INSTANT MP4 QC + REVIEW • V226 FIX2 NEWSROOM INTAKE RETAINED",
                DevelopUgandaFivemods8Theme.contentDim
            )
        )

        page.addView(
            compactStatus(
                "V228 • BRAND & METADATA STUDIO",
                "CUSTOM TOP NAME / ORGANIZATION • EVERY SAVED-VIDEO METADATA CATEGORY CAN BE ON/OFF • FULL FORENSIC / NEWS / SOCIAL CLEAN / CONSTRUCTION / INTERVIEW / PRIVATE MASTER / CUSTOM • PUBLIC PROFILE HIDES EXACT GPS BY DEFAULT • STORY PACKAGE PRESERVES BRAND/TAG SNAPSHOT",
                DevelopUgandaFivemods8Theme.accent
            )
        )

        page.addView(
            launchCard(
                "V228 • BRAND & METADATA",
                "Make the camera yours without losing the professional system",
                "Change the main saved-video name • optional organization • choose exactly which telemetry is visibly burned into NEW recordings • privacy-aware PUBLIC/SOCIAL profile • VERIFIED MASTER defaults to the full V227 experience",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN BRAND & TAG STUDIO"
            ) {
                openIndependentCamera(
                    DevelopUgandaBrandMetadataActivity::class.java
                )
            }
        )

        page.addView(
            compactStatus(
                "V233 • UNIFIED LIVE CONTROL DECK",
                "ORIGINAL VIDEO ALWAYS PRESERVED • REAL MEDIA3 SingleColorLut 17³ COLOR MASTER • AUTO BY CAMERA/SCENE • 13 ORIGINAL DU COLOR PROFILES • OPTIONAL MONITOR APPROXIMATION • STORY PACKAGE COLOR_PROFILE.json",
                DevelopUgandaFivemods8Theme.accent
            )
        )

        page.addView(
            launchCard(
                "V233 • COLOR + LIVE CONTROL",
                "Professional color without pretending a phone sensor is an ARRI / RED / Sony cinema sensor",
                "DU CINEMA NATURAL • COOL CINEMA • FILM BIAS • EXTENDED VIDEO • SOFT FILM • WARM 709 • NIGHT CINEMA • BLEACH DRAMA • GOLDEN HOUR • CLEAN SOCIAL • CONSTRUCTION • PEOPLE • MONO CINEMA • real separate COLOR_MASTER.mp4",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN COLOR STUDIO"
            ) {
                openIndependentCamera(
                    DevelopUgandaColorStudioActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V227 • CAMERA HEALTH",
                "See what this phone actually exposes",
                "REAL CameraX/Camera2 device IDs + focal lengths • UHD • HLG HDR • stabilization • hardware FPS ranges • JPEG / Ultra HDR / RAW / RAW+JPEG • on-device transcription • thermal/location/storage • H.264/AAC encoder presence",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN CAMERA HEALTH"
            ) {
                openIndependentCamera(
                    DevelopUgandaCameraHealthActivity::class.java
                )
            }
        )

        page.addView(
            sectionTitle(
                "INDEPENDENT PRO CAMERAS"
            )
        )

        page.addView(
            compactStatus(
                "SHOT FINDER • ALL CAMERAS ARE INDEPENDENT • PICK BY WHAT YOU ARE FILMING",
                "PEOPLE → V205   •   BACKLIGHT → V206   •   BUILDINGS → V207   •   WALK/ACTION → V208   •   NIGHT → V209   •   EVERYDAY → V210   •   INTERVIEW AUDIO → V211   •   VERIFIED → V212   •   LONG RECORD → V213   •   CINEMATIC → V214   •   UNSURE → V215   •   SOCIAL POST → V222",
                cyan
            )
        )

        page.addView(
            compactStatus(
                "V217 FULL FRAME CAMERA",
                "EVERY CAMERA STILL OPENS DIRECTLY • CAMERA PREVIEW NOW FILLS THE SCREEN BEHIND CONTROLS • RECORDING FORCES FULL FRAME • SAVED HUD IS WIDER AND CLEANER",
                green
            )
        )

        page.addView(
            launchCard(
                "V205 • PEOPLE / PORTRAIT FOCUS",
                "People, portraits and interviews",
                "Tap subject for AF • long-press persistent AF lock • INTERVIEW + SOCIAL FHD default • focus reticle emphasized • all shared recording/telemetry tools remain",
                gold,
                "OPEN V205 FOCUS CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaFocusAssistCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V206 • SUBJECT METERING",
                "Backlit faces, windows and mixed light",
                "Long-press metering region • visible reticle • NATURAL default • independent saved settings • exact camera identity burned into V216 output",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN V206 METER CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaMeteringLockCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V207 • BUILDINGS / LEVEL",
                "Architecture, rooms and straight horizons",
                "Rotation-vector horizon guide emphasized • LEVEL LOCK / LEVEL NEAR / ADJUST • OUTDOOR default • other modules retained but visually secondary",
                green,
                "OPEN V207 HORIZON CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaHorizonCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V208 • WALK / ACTION STEADY",
                "Walking, vehicles and moving subjects",
                "ACTION STAB default • real STEADY / MOVING / SHAKE guidance emphasized • DOCUMENTARY default • device stabilization remains real CameraX capability",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN V208 STEADYSHOT CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaSteadyShotCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V209 • NIGHT / LOW LIGHT",
                "Night streets and dark interiors",
                "LOW LIGHT + NIGHT defaults • real Android lux sensor emphasized • dark/dim/normal/bright guidance • 30fps advice preserved",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN V209 NIGHT CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaNightIntelligenceCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V210 • EVERYDAY PRO",
                "Best general-purpose professional camera",
                "All V204→V215 tools visible together • Social Master capture engine • focus/meter/horizon/motion/lux/audio/thermal/verified-state controls • REPORTER default",
                cyan,
                "OPEN V210 ALL-PRO CAMERA"
            ) {
                openMode(DevelopUgandaModeProfiles.main)
            }
        )

        page.addView(
            launchCard(
                "V211 • INTERVIEW / AUDIO",
                "Speech, interviews and events",
                "CameraX microphone amplitude + peak emphasized • LOW / GOOD / HOT / CLIP RISK • INTERVIEW default • audio track is still recorded normally",
                green,
                "OPEN V211 AUDIO CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaAudioGuardCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V212 • VERIFIED REPORT",
                "Site reports, incidents and evidence capture",
                "Live CameraX + GPS + sensor + audio state emphasized • NEWS default • V216 filename and SHA-256 integrity metadata identify this exact camera",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN V212 VERIFIED CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaVerifiedCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V213 • LONG RECORD / HEAT SAFE",
                "Long takes and hot conditions",
                "Android PowerManager thermal state emphasized • severe+ safe fallback retained • SOCIAL FHD default • thermal state recorded in output",
                red,
                "OPEN V213 THERMAL CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaThermalSafeCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V214 • CINEMATIC LOOKS",
                "Cinematic people, travel and creative shots",
                "SOCIAL HDR + WARM first-run defaults • subtle look-matched preview • mode accent/purpose • exact quality/scene/look recorded",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN V214 SIGNATURE CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaModeSignatureCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V215 • SMART AUTO",
                "Fast shooting when you do not want to choose settings",
                "AUTO DIRECTOR enabled on first launch • real lux + shake + thermal choose actual Social FHD / Social 60 / Action Stab / Low Light • never changes mid-recording",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN V215 AUTO CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaAutoDirectorCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V222 • SOCIAL MEDIA CAMERA",
                "TikTok • Instagram Reels • YouTube Shorts • social posts",
                "Direct 9:16 SOCIAL FHD camera • compact social HUD • records the normal high-quality original first • then automatically forces a separate H.264/AAC social re-encode • 1080×1920 • 30fps max • 16 Mbps target • 2s keyframes • saves separately in Movies/develop.uganda/SM Posts",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN V222 SM CAMERA"
            ) {
                openIndependentCamera(
                    DevelopUgandaSocialMediaCameraActivity::class.java
                )
            }
        )

        page.addView(
            compactStatus(
                "SM CAMERA WORKFLOW",
                "TAP V222 → RECORD → STOP → ORIGINAL SAVES NORMALLY → SM OPTIMIZING → SM READY → PICK THE SEPARATE SM POSTS VIDEO IN TIKTOK / REELS",
                DevelopUgandaFivemods8Theme.accent
            )
        )

        page.addView(
            compactStatus(
                "NOTHING DROPPED",
                "V205 • V206 • V207 • V208 • V209 • V210 • V211 • V212 • V213 • V214 • V215 • V222 SOCIAL MEDIA CAMERA ALL REMAIN DIRECT-LAUNCH OPTIONS • V223 ADDS AUTO VIEW TO MAIN REPORT/LIVE",
                gold
            )
        )

        val quickLaunch = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, space(8), 0, space(8))
        }
        DevelopUgandaModeProfiles.all().forEachIndexed { index, profile ->
            quickLaunch.addView(
                smallClipButton(profile.shortName, profile.chromeAccentColor) { openMode(profile) },
                LinearLayout.LayoutParams(dp(104), dp(48)).apply {
                    if (index > 0) marginStart = space(8)
                },
            )
        }
        val quickLaunchScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(quickLaunch)
        }
        page.addView(quickLaunchScroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))

        page.addView(
            sectionTitle(
                "AUTO STORY PACKAGE • V227"
            )
        )

        page.addView(
            compactStatus(
                "AFTER A SUCCESSFUL REPORT / LIVE RECORDING",
                "ORIGINAL PACKAGE COPY • THUMBNAIL • MANIFEST • METADATA • SHA-256 COPY CHECK • CAPTION DRAFT • SOCIAL MASTER WHEN AVAILABLE • OPTIONAL ON-DEVICE TRANSCRIPT / SRT DRAFT",
                DevelopUgandaFivemods8Theme.accent
            )
        )

        page.addView(
            launchCard(
                "V227 • STORY PACKAGES",
                "Open, share and transcribe completed report packages",
                "Each package is stored under Download/develop.uganda/Story Packages/<Package ID> • original Gallery video stays untouched • Interview/V211 can request on-device transcript automatically • any package can request transcript manually on Android 13+ when an on-device recognizer exists",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN STORY PACKAGES"
            ) {
                openIndependentCamera(
                    DevelopUgandaStoryPackagesActivity::class.java
                )
            }
        )

        page.addView(
            sectionTitle(
                "PROFESSIONAL PHOTO CAMERAS • V225"
            )
        )

        page.addView(
            compactStatus(
                "CAPABILITY-AWARE STILL PHOTOGRAPHY",
                "ONLY FORMATS THE SELECTED LENS REPORTS AS SUPPORTED ARE SHOWN • JPEG • ULTRA HDR JPEG_R • RAW DNG • RAW+JPEG • EDGE PEAK / ZEBRA REMAIN SCREEN-ONLY",
                DevelopUgandaFivemods8Theme.accent
            )
        )

        page.addView(
            launchCard(
                "V225 • PHOTO PRO",
                "General professional still photography",
                "Maximum-quality CameraX ImageCapture • tap focus • capability-aware JPEG / Ultra HDR / RAW DNG / RAW+JPEG selector • level guide • edge peak / zebra operator assist",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN PHOTO PRO"
            ) {
                openIndependentCamera(
                    DevelopUgandaPhotoProCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V225 • BUILDING PHOTO",
                "Architecture • rooms • property • straight lines",
                "Level guide emphasized • Ultra HDR preferred only when this lens reports support • otherwise JPEG fallback • maximum-quality capture • RAW options remain selectable when supported",
                green,
                "OPEN BUILDING PHOTO"
            ) {
                openIndependentCamera(
                    DevelopUgandaBuildingPhotoCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V225 • PEOPLE PHOTO",
                "People • portraits • interview stills",
                "Tap-to-focus with CameraX focus confirmation • maximum-quality JPEG default • RAW/HDR choices appear only if the device supports them • peaking optional",
                gold,
                "OPEN PEOPLE PHOTO"
            ) {
                openIndependentCamera(
                    DevelopUgandaPeoplePhotoCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V225 • NIGHT PHOTO",
                "Night • dark rooms • low-light stills",
                "CameraX MAXIMIZE_QUALITY • flash OFF by default • no fake Nightography claim • device-supported RAW/HDR formats remain selectable • zebra/peaking available",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN NIGHT PHOTO"
            ) {
                openIndependentCamera(
                    DevelopUgandaNightPhotoCameraActivity::class.java
                )
            }
        )

        page.addView(
            launchCard(
                "V225 • VERIFIED PHOTO",
                "Inspections • site records • evidence-style stills",
                "JPEG maximum quality • filename identifies VERIFIED PHOTO • metadata JSON includes capture time/GPS/camera • SHA-256 integrity sidecar detects later file changes without claiming authorship",
                DevelopUgandaFivemods8Theme.accent,
                "OPEN VERIFIED PHOTO"
            ) {
                openIndependentCamera(
                    DevelopUgandaVerifiedPhotoCameraActivity::class.java
                )
            }
        )

        page.addView(
            sectionTitle(
                "CAPTURE MODES"
            )
        )

        DevelopUgandaModeProfiles.all().forEach { profile ->
            val capability = if (profile.hasProfileData && profile.frameRate != null && profile.targetBitrateBps != null) {
                "${profile.qualityName} • ${profile.frameRate}fps • ${profile.targetBitrateBps / 1_000_000}Mbps"
            } else {
                "ADDITIVE PROFILE DATA ABSENT • EXISTING MODE SETTINGS RETAINED"
            }
            page.addView(
                launchCard(
                    profile.displayName,
                    profile.routeDescription.uppercase() + " • " + capability,
                    if (profile.hasProfileData) profile.instruction else "No additive aspect, rate or delivery value is claimed for this mode.",
                    profile.chromeAccentColor,
                    "OPEN ${profile.shortName}"
                ) { openMode(profile) }
            )
        }

        page.addView(
            sectionTitle(
                "POST PRODUCTION"
            )
        )

        page.addView(
            launchCard(
                "EDIT + SOCIAL MASTER • OPTIONAL",
                "Edit normally, then create TikTok or Reels upload masters without touching the original",
                "GALLERY • FILES • RECENT • LAST CLIP • Media3 preview/edit • TIKTOK MASTER 16 Mbps • REELS MASTER 14 Mbps • original preserved",
                cyan,
                "OPEN EDIT DESK"
            ) {
                openEditor()
            }
        )

        page.addView(
            launchCard(
                "NEWSROOM DESK",
                "Prepare the story before capture",
                "Reporter • Story ID • headline • description • assignment",
                green,
                "OPEN NEWSROOM"
            ) {
                showNewsroom()
            }
        )

        page.addView(
            compactStatus(
                "REPORTING WORKFLOW",
                "ASSIGN → CAPTURE → VERIFY → EDIT → SHARE",
                cyan
            )
        )

        page.addView(
            sectionTitle(
                "RECENT CLIPS"
            )
        )

        addRecentClips(
            page
        )

        page.addView(
            sectionTitle(
                "LIVE STATUS"
            )
        )

        val previewStates = DevelopUgandaFiveModesDiagnostics.snapshot(this).associateBy { it.page }
        DevelopUgandaModeProfiles.all().forEach { profile ->
            val state = previewStates[profile.page]
            page.addView(
                compactStatus(
                    profile.displayName,
                    state?.let { "${it.state} • ${it.detail}" } ?: "STATE UNKNOWN",
                    profile.chromeAccentColor
                )
            )
        }
        page.addView(
            compactStatus(
                "EDITOR",
                "V218 • GALLERY / FILES / LAST CLIP • PREVIEW CUT • KEYFRAME-SAFE MP4 REMUX • MUTE • SHARE",
                cyan
            )
        )

        scroll.addView(
            page
        )

        setPage(
            scroll
        )
    }

    private fun addRecentClips(
        page: LinearLayout
    ) {
        val projection =
            arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED
            )

        val uri =
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        var shown =
            0
        var queryFailure: Exception? = null

        try {
            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            ) ?: throw IllegalStateException("MediaStore returned no cursor")
            cursor.use {
                val idCol =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media._ID
                    )

                val nameCol =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DISPLAY_NAME
                    )

                val durationCol =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DURATION
                    )

                while (
                    cursor.moveToNext() &&
                    shown <
                    4
                ) {
                    val name =
                        cursor.getString(
                            nameCol
                        ) ?: continue

                    if (!DevelopUgandaFivemods12Identity.isAppMediaName(name)) {
                        continue
                    }

                    val id =
                        cursor.getLong(
                            idCol
                        )

                    val duration =
                        cursor.getLong(
                            durationCol
                        )

                    val clipUri =
                        ContentUris.withAppendedId(
                            uri,
                            id
                        )

                    page.addView(
                        recentClipCard(
                            name,
                            duration,
                            clipUri,
                            modeForRecordedClip(clipUri, name),
                        )
                    )

                    shown +=
                        1
                }
            }
        } catch (
            error: Exception
        ) {
            queryFailure = error
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "NEWSROOM MEDIASTORE READ", error)
        }

        val failure = queryFailure
        if (failure != null) {
            page.addView(
                infoCard(
                    "MEDIA LIBRARY UNAVAILABLE",
                    "Android MediaStore could not be read • ${failure.javaClass.simpleName} • ${failure.message ?: "no message"}"
                )
            )
            return
        }

        if (
            shown ==
            0
        ) {
            page.addView(
                infoCard(
                    "NO RECENT CLIPS",
                    "No develop.uganda clip is present in Android MediaStore. Record with any of the five camera modes, then return here."
                )
            )
        }
    }

    private fun recentClipCard(
        name: String,
        durationMs: Long,
        uri: Uri,
        mode: DevelopUgandaCameraPage?,
    ): View {
        val cardView =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    space(12),
                    space(10),
                    space(12),
                    space(10)
                )

                background =
                    rounded(
                        card,
                        DevelopUgandaFivemods8Theme.surface,
                        15
                    )
            }

        val profile = mode?.let { DevelopUgandaModeProfiles.forPage(it) }
        val badge = profile?.shortName ?: "MODE UNKNOWN"

        cardView.addView(
            label(
                "$badge • ${formatDuration(durationMs)}",
                10f,
                profile?.chromeAccentColor ?: muted,
                true
            )
        )

        cardView.addView(
            label(
                name.removeSuffix(
                    ".mp4"
                ),
                11f,
                white,
                true
            ).apply {
                maxLines =
                    1
            }
        )

        val transcriptState = DevelopUgandaTranscriptArchive.stateForMedia(this, uri, name)
        cardView.addView(
            label(
                transcriptState,
                11f,
                if (transcriptState == "NO TRANSCRIPT") muted else DevelopUgandaFivemods8Theme.accent,
                true,
            ).apply { setPadding(0, space(3), 0, 0) }
        )

        val actions =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL

                setPadding(
                    0,
                    space(7),
                    0,
                    0
                )
            }

        actions.addView(
            smallClipButton(
                "PLAY",
                cyan
            ) {
                try {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW
                        ).apply {
                            setDataAndType(
                                uri,
                                "video/mp4"
                            )

                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                    )
                } catch (
                    _: Exception
                ) {
                    toast(
                        "No video player available"
                    )
                }
            },
            LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
            )
        )

        actions.addView(
            smallClipButton(
                "EDIT",
                green
            ) {
                startActivity(
                    Intent(
                        this,
                        DevelopUgandaEditorActivity::class.java
                    ).apply {
                        putExtra(
                            "develop_uganda_edit_uri",
                            uri.toString()
                        )
                    }
                )
            },
            LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
            ).apply {
                marginStart =
                    space(7)
            }
        )

        actions.addView(
            smallClipButton(
                "SHARE",
                gold
            ) {
                startActivity(
                    Intent.createChooser(
                        Intent(
                            Intent.ACTION_SEND
                        ).apply {
                            type =
                                "video/mp4"

                            putExtra(
                                Intent.EXTRA_STREAM,
                                uri
                            )

                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        },
                        "Share develop.uganda clip"
                    )
                )
            },
            LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
            ).apply {
                marginStart =
                    space(7)
            }
        )

        cardView.addView(
            actions
        )

        cardView.layoutParams =
            full(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                0,
                7
            )

        return cardView
    }

    private fun modeForRecordedClip(uri: Uri, name: String): DevelopUgandaCameraPage? {
        val stored = try {
            DevelopUgandaV274MediaVaultStore.modeForMedia(this, uri.toString(), name)
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(this, "NEWSROOM CLIP MODE READ", error)
            null
        }
        return stored ?: modeFromRecordedBrandMetadata(uri.toString())
    }

    /**
     * Resolves CLEAN or BRAND MediaStore URIs through the capture-time overlay
     * and render records. A missing record stays unknown; filenames are never
     * used as a camera-mode guess.
     */
    private fun modeFromRecordedBrandMetadata(mediaUri: String): DevelopUgandaCameraPage? {
        val directory = File(filesDir, "fivemods8/brand-overlays")
        if (!directory.isDirectory) return null
        val renderFiles = directory.listFiles { file -> file.name.endsWith(".render.json") }
            ?: return null
        for (renderFile in renderFiles) {
            try {
                val render = JSONObject(renderFile.readText(Charsets.UTF_8))
                val clean = render.optString("cleanUri", "")
                val brand = render.optString("brandUri", "")
                if (mediaUri != clean && mediaUri != brand) continue
                val takeId = render.optString("takeId", "")
                if (takeId.isBlank()) return null
                val safeTakeId = takeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
                val overlayFile = File(directory, "$safeTakeId.json")
                if (!overlayFile.isFile) return null
                val mode = JSONObject(overlayFile.readText(Charsets.UTF_8))
                    .optString("mode", "")
                    .uppercase(java.util.Locale.US)
                return runCatching { DevelopUgandaCameraPage.valueOf(mode) }.getOrNull()
            } catch (error: Exception) {
                DevelopUgandaV28012RuntimeGuard.recordUiFault(
                    this,
                    "NEWSROOM RECORDED MODE METADATA",
                    error,
                )
            }
        }
        return null
    }

    private fun smallClipButton(
        value: String,
        accent: Int,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text =
                value
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 8f)
            minimumWidth = touchMinimum()
            minimumHeight = touchMinimum()

            isAllCaps =
                false

            setTextColor(
                white
            )

            DevelopUgandaBroadcastPresentation.styleButton(
                this,
                DevelopUgandaButtonWeight.SECONDARY,
                accent,
            )

            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "NEWSROOM BUTTON") { action.invoke() }
            }
        }
    }

    private fun formatDuration(
        durationMs: Long
    ): String {
        val seconds =
            (
                durationMs /
                    1000L
                )
                .coerceAtLeast(
                    0L
                )

        return String.format(
            java.util.Locale.US,
            "%02d:%02d",
            seconds /
                60L,
            seconds %
                60L
        )
    }

    private fun showLivePage() {
        val scroll =
            ScrollView(this)
        val page =
            pageColumn()

        page.addView(
            hero(
                "LIVE CONTROL ROOM",
                "A separate broadcast deck for breaking news, interviews, events and community live-style coverage."
            )
        )

        page.addView(
            bigAction(
                "● ENTER LIVE STUDIO",
                red
            ) {
                openMode(DevelopUgandaModeProfiles.live)
            }
        )

        page.addView(
            sectionTitle(
                "LIVE-ONLY CONTROLS"
            )
        )

        page.addView(
            infoCard(
                "QUALITY",
                "Switch FHD / HD for the LIVE camera."
            )
        )
        page.addView(
            infoCard(
                "AUDIO",
                "Enable or disable recorded microphone audio."
            )
        )
        page.addView(
            infoCard(
                "GRAPHICS",
                "Show or hide the LIVE camera's broadcast reticle and live-feed graphics."
            )
        )
        page.addView(
            infoCard(
                "LENS / LIGHT / OUTPUT",
                "Live-specific lens, torch and output status controls."
            )
        )

        page.addView(
            sectionTitle(
                "SIGNAL SYSTEM"
            )
        )

        page.addView(
            infoCard(
                "GREEN SIGNALS",
                "NET, GPS, MIC, CAM and battery lamps report readiness. REC turns red during capture."
            )
        )

        page.addView(
            infoCard(
                "LIVE INDICATOR",
                "The LIVE REC logo and circular record control use a steady red state outline while recording."
            )
        )

        page.addView(
            infoCard(
                "RTMPS LIVE",
                "LIVE mode now provides secure RTMPS output with measured connection state, uplink bitrate, dropped-frame count, adaptive resolution and an always-on segmented CLEAN backup."
            )
        )

        scroll.addView(
            page
        )
        setPage(
            scroll
        )
    }

    private fun showNewsroom() {
        val scroll =
            ScrollView(this)
        val page =
            pageColumn()

        page.addView(
            hero(
                "NEWSROOM DESK",
                "Prepare identity, headline and assignment before recording."
            )
        )
        addTranscriptSearch(page)

        val prefs =
            duSharedPreferences(
                "develop_uganda_reporter",
                Context.MODE_PRIVATE
            )

        val newsroomPrefs =
            duSharedPreferences(
                "develop_uganda_newsroom",
                Context.MODE_PRIVATE
            )

        val reporter =
            editorField(
                "Reporter / citizen name",
                prefs.getString(
                    "reporter_name",
                    ""
                ) ?: ""
            )

        val story =
            editorField(
                "Story ID / assignment",
                prefs.getString(
                    "story_id",
                    ""
                ) ?: ""
            )

        val headline =
            editorField(
                "Headline",
                newsroomPrefs.getString(
                    "headline",
                    ""
                ) ?: ""
            )

        val description =
            EditText(this).apply {
                hint =
                    "Story summary / caption"
                setHintTextColor(
                    DevelopUgandaFivemods8Theme.contentDim
                )
                setTextColor(
                    white
                )
                DevelopUgandaFivemods8Theme.applyTypeScale(this, 15f)
                gravity =
                    Gravity.TOP
                setPadding(
                    space(14),
                    space(12),
                    space(14),
                    space(12)
                )
                minLines =
                    4
                setText(
                    newsroomPrefs.getString(
                        "description",
                        ""
                    ) ?: ""
                )
                background =
                    rounded(
                        card,
                        DevelopUgandaFivemods8Theme.surface,
                        14
                    )
            }

        page.addView(
            sectionTitle(
                "ASSIGNMENT"
            )
        )

        page.addView(
            reporter,
            full(
                dp(54),
                0,
                6
            )
        )
        page.addView(
            story,
            full(
                dp(54),
                0,
                6
            )
        )
        page.addView(
            headline,
            full(
                dp(54),
                0,
                6
            )
        )
        page.addView(
            description,
            full(
                dp(110),
                0,
                12
            )
        )

        page.addView(
            bigAction(
                "SAVE ASSIGNMENT",
                green
            ) {
                saveAssignment(
                    reporter,
                    story,
                    headline,
                    description
                )
            }
        )

        DevelopUgandaModeProfiles.all().forEach { profile ->
            page.addView(
                bigAction("SAVE + OPEN ${profile.displayName}", profile.chromeAccentColor) {
                    saveAssignment(reporter, story, headline, description)
                    openMode(profile)
                }
            )
        }

        page.addView(
            bigAction(
                "SHARE STORY TEXT",
                cyan
            ) {
                saveAssignment(
                    reporter,
                    story,
                    headline,
                    description
                )
                shareStoryText(
                    story,
                    headline,
                    description
                )
            }
        )

        scroll.addView(
            page
        )

        setPage(
            scroll
        )
    }

    private fun addTranscriptSearch(page: LinearLayout) {
        page.addView(sectionTitle("CONFIRMED TRANSCRIPT ARCHIVE"))
        val query = editorField("Search what was said", "").apply {
            contentDescription = "Search confirmed transcripts"
            isSingleLine = true
        }
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        fun showMessage(title: String, detail: String) {
            results.removeAllViews()
            results.addView(infoCard(title, detail))
        }

        fun search() {
            val needle = query.text.toString().trim()
            if (needle.isBlank()) {
                val count = DevelopUgandaTranscriptArchive.all(this).size
                showMessage(
                    if (count == 0) "NO CONFIRMED TRANSCRIPTS" else "$count CONFIRMED TRANSCRIPTS",
                    if (count == 0) {
                        "No reviewed transcript sidecar exists. Takes without one remain labelled NO TRANSCRIPT."
                    } else {
                        "Enter words from the recording to find the matching line, timestamp, take and mode."
                    },
                )
                return
            }
            val hits = DevelopUgandaTranscriptArchive.search(this, needle)
            val markHits = DevelopUgandaTakeMarks.all(this).filter { mark ->
                mark.label.contains(needle, true) ||
                    "MARK".contains(needle, true) ||
                    DevelopUgandaTakeMarks.format(mark.elapsedMs).contains(needle, true) ||
                    mark.takeId.contains(needle, true)
            }
            results.removeAllViews()
            if (hits.isEmpty() && markHits.isEmpty()) {
                results.addView(infoCard(
                    "NO ARCHIVE MATCH",
                    "No confirmed transcript or take mark contains “$needle”. Unreviewed and absent transcripts are not treated as empty speech.",
                ))
                return
            }
            hits.forEach { hit ->
                val cardView = infoCard(
                    "${hit.entry.mode} • ${formatTranscriptTime(hit.cue.startMs)} • ${hit.entry.takeId}",
                    hit.cue.text,
                ).apply {
                    isClickable = true
                    isFocusable = true
                    minimumHeight = touchMinimum()
                    contentDescription = "Play ${hit.entry.mode} take ${hit.entry.takeId} at ${formatTranscriptTime(hit.cue.startMs)}. ${hit.cue.text}"
                    setOnClickListener { DevelopUgandaCaptionReview.showPlayback(this@DevelopUgandaNewsroomActivity, hit) }
                }
                DevelopUgandaBroadcastPresentation.styleActionSurface(
                    cardView,
                    DevelopUgandaButtonWeight.SECONDARY,
                    DevelopUgandaModeProfiles.forExperience(hit.entry.mode)?.chromeAccentColor ?: gold,
                )
                results.addView(cardView)
            }
            val clips = runCatching { DevelopUgandaV274MediaVaultStore.clips(this) }.getOrDefault(emptyList())
            markHits.forEachIndexed { index, mark ->
                val clip = clips.firstOrNull { candidate ->
                    candidate.name == mark.takeId || candidate.name.startsWith("${mark.takeId}_")
                }
                val cardView = infoCard(
                    "${mark.mode} • ${DevelopUgandaTakeMarks.format(mark.elapsedMs)} • ${mark.takeId}",
                    "${mark.label} ${index + 1} • REVIEW SIDECAR",
                ).apply {
                    isClickable = clip != null
                    isFocusable = clip != null
                    minimumHeight = touchMinimum()
                    contentDescription = if (clip == null) {
                        "Take mark found; matching Media Vault video unavailable"
                    } else {
                        "Play ${mark.mode} take mark at ${DevelopUgandaTakeMarks.format(mark.elapsedMs)}"
                    }
                    if (clip != null) setOnClickListener {
                        startActivity(Intent(this@DevelopUgandaNewsroomActivity, DevelopUgandaStoryPlayerActivity::class.java).apply {
                            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_URI, clip.uri)
                            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_LABEL, clip.name)
                            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_TAKE_ID, mark.takeId)
                            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_START_MS, mark.elapsedMs)
                        })
                    }
                }
                DevelopUgandaBroadcastPresentation.styleActionSurface(
                    cardView,
                    DevelopUgandaButtonWeight.SECONDARY,
                    DevelopUgandaModeProfiles.forExperience(mark.mode)?.chromeAccentColor ?: gold,
                )
                results.addView(cardView)
            }
        }

        val searchButton = Button(this).apply {
            text = "SEARCH TRANSCRIPTS"
            contentDescription = "Search confirmed transcript archive"
            setOnClickListener { search() }
        }
        DevelopUgandaBroadcastPresentation.styleButton(
            searchButton,
            DevelopUgandaButtonWeight.SECONDARY,
            gold,
        )
        query.setOnEditorActionListener { _, _, _ -> search(); true }
        page.addView(query, full(dp(54), 0, 6))
        page.addView(searchButton, full(touchMinimum(), 0, 6))
        page.addView(results)
        search()
    }

    private fun formatTranscriptTime(ms: Long): String = String.format(
        java.util.Locale.US,
        "%02d:%02d.%03d",
        ms / 60_000L,
        (ms % 60_000L) / 1_000L,
        ms % 1_000L,
    )

    private fun saveAssignment(
        reporter: EditText,
        story: EditText,
        headline: EditText,
        description: EditText
    ) {
        val reporterValue =
            reporter.text
                .toString()
                .trim()
                .ifBlank {
                    "CITIZEN"
                }

        val prefs =
            duSharedPreferences(
                "develop_uganda_reporter",
                Context.MODE_PRIVATE
            )

        val newsroomPrefs =
            duSharedPreferences(
                "develop_uganda_newsroom",
                Context.MODE_PRIVATE
            )

        prefs.edit()
            .putString(
                "reporter_name",
                reporterValue
            )
            .putString(
                "story_id",
                story.text
                    .toString()
                    .trim()
            )
            .apply()

        newsroomPrefs.edit()
            .putString(
                "headline",
                headline.text
                    .toString()
                    .trim()
            )
            .putString(
                "description",
                description.text
                    .toString()
                    .trim()
            )
            .apply()

        toast(
            "Assignment saved"
        )
    }

    private fun shareStoryText(
        story: EditText,
        headline: EditText,
        description: EditText
    ) {
        val text =
            buildString {
                val h =
                    headline.text
                        .toString()
                        .trim()

                if (
                    h.isNotBlank()
                ) {
                    append(h)
                    append("\n\n")
                }

                append(
                    description.text
                        .toString()
                        .trim()
                )

                val id =
                    story.text
                        .toString()
                        .trim()

                if (
                    id.isNotBlank()
                ) {
                    append(
                        "\n\nStory ID: "
                    )
                    append(id)
                }

                append(
                    "\n\n#developUganda"
                )
            }

        val send =
            Intent(
                Intent.ACTION_SEND
            ).apply {
                type =
                    "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    text
                )
            }

        startActivity(
            Intent.createChooser(
                send,
                "Publish / share story"
            )
        )
    }

    private fun openIndependentCamera(
        cameraClass: Class<*>
    ) {
        startActivity(
            Intent(
                this,
                cameraClass
            )
        )
    }

    private fun openMode(profile: DevelopUgandaModeProfile) {
        DevelopUgandaModeProfiles.rememberSelected(this, profile.page)
        startActivity(Intent(this, profile.destination))
    }

    private fun openEditor() {
        startActivity(
            Intent(
                this,
                DevelopUgandaEditorActivity::class.java
            )
        )
    }

    private fun setPage(
        view: View
    ) {
        contentHost.removeAllViews()

        contentHost.addView(
            view,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        DevelopUgandaBroadcastPresentation.decorate(view, DevelopUgandaModeProfiles.selected(this))
    }

    private fun pageColumn():
        LinearLayout {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                space(14),
                space(12),
                space(14),
                space(26)
            )
        }
    }

    private fun hero(
        title: String,
        subtitle: String
    ): View {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                space(18),
                space(18),
                space(18),
                space(18)
            )
            background =
                rounded(
                    DevelopUgandaFivemods8Theme.surface,
                    gold,
                    22
                )

            addView(
                label(
                    title,
                    23f,
                    gold,
                    true
                )
            )
            addView(
                label(
                    subtitle,
                    13f,
                    DevelopUgandaFivemods8Theme.content,
                    false
                ).apply {
                    setPadding(
                        0,
                        space(6),
                        0,
                        0
                    )
                }
            )
        }.apply {
            layoutParams =
                full(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0,
                    13
                )
        }
    }

    private fun launchCard(
        title: String,
        kicker: String,
        body: String,
        accent: Int,
        actionText: String,
        action: () -> Unit
    ): View {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                space(15),
                space(14),
                space(15),
                space(14)
            )
            background =
                rounded(
                    card,
                    accent,
                    18
                )

            addView(
                label(
                    title,
                    17f,
                    accent,
                    true
                )
            )

            addView(
                label(
                    kicker,
                    12f,
                    white,
                    true
                ).apply {
                    setPadding(
                        0,
                        space(5),
                        0,
                        0
                    )
                }
            )

            addView(
                label(
                    body,
                    12f,
                    muted,
                    false
                ).apply {
                    setPadding(
                        0,
                        space(5),
                        0,
                        space(10)
                    )
                }
            )

            addView(
                bigAction(
                    actionText,
                    accent,
                    action
                )
            )
        }.apply {
            layoutParams =
                full(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0,
                    9
                )
        }
    }

    private fun compactStatus(
        title: String,
        body: String,
        accent: Int
    ): View {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.HORIZONTAL
            gravity =
                Gravity.CENTER_VERTICAL
            setPadding(
                space(12),
                space(10),
                space(12),
                space(10)
            )
            background =
                rounded(
                    DevelopUgandaFivemods8Theme.surface,
                    DevelopUgandaFivemods8Theme.surface,
                    14
                )

            addView(
                label(
                    "●",
                    16f,
                    accent,
                    true
                ),
                LinearLayout.LayoutParams(
                    dp(24),
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            addView(
                LinearLayout(this@DevelopUgandaNewsroomActivity).apply {
                    orientation =
                        LinearLayout.VERTICAL

                    addView(
                        label(
                            title,
                            11f,
                            white,
                            true
                        )
                    )

                    addView(
                        label(
                            body,
                            10f,
                            muted,
                            false
                        )
                    )
                },
                LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
            )
        }.apply {
            layoutParams =
                full(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0,
                    6
                )
        }
    }

    private fun infoCard(
        title: String,
        body: String
    ): View {
        return LinearLayout(this).apply {
            orientation =
                LinearLayout.VERTICAL
            setPadding(
                space(14),
                space(12),
                space(14),
                space(12)
            )
            background =
                rounded(
                    card,
                    DevelopUgandaFivemods8Theme.surface,
                    16
                )

            addView(
                label(
                    title,
                    13f,
                    white,
                    true
                )
            )

            addView(
                label(
                    body,
                    12f,
                    muted,
                    false
                ).apply {
                    setPadding(
                        0,
                        space(5),
                        0,
                        0
                    )
                }
            )
        }.apply {
            layoutParams =
                full(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    0,
                    7
                )
        }
    }

    private fun sectionTitle(
        value: String
    ): TextView {
        return label(
            value,
            10f,
            DevelopUgandaFivemods8Theme.contentDim,
            true
        ).apply {
            setPadding(
                space(2),
                space(14),
                space(2),
                space(7)
            )
        }
    }

    private fun bigAction(
        title: String,
        accent: Int,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text =
                title
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 11f)
            minimumWidth = touchMinimum()
            minimumHeight = touchMinimum()
            isAllCaps =
                false
            setTextColor(
                white
            )
            DevelopUgandaBroadcastPresentation.styleButton(
                this,
                DevelopUgandaButtonWeight.SECONDARY,
                accent,
            )
            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "NEWSROOM BUTTON") { action.invoke() }
            }
            layoutParams =
                full(
                    dp(54),
                    0,
                    7
                )
        }
    }

    private fun navButton(
        title: String,
        accent: Int,
        action: () -> Unit
    ): Button {
        return Button(this).apply {
            text =
                title
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 9f)
            minimumWidth = touchMinimum()
            minimumHeight = touchMinimum()
            isAllCaps =
                false
            setTextColor(
                accent
            )
            DevelopUgandaBroadcastPresentation.styleButton(
                this,
                DevelopUgandaButtonWeight.TERTIARY,
                accent,
            )
            setTextColor(accent)
            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "NEWSROOM BUTTON") { action.invoke() }
            }
            setPadding(
                space(1),
                0,
                space(1),
                0
            )
        }
    }

    private fun editorField(
        hintValue: String,
        initial: String
    ): EditText {
        return EditText(this).apply {
            hint =
                hintValue
            setHintTextColor(
                DevelopUgandaFivemods8Theme.contentDim
            )
            setTextColor(
                white
            )
            DevelopUgandaFivemods8Theme.applyTypeScale(this, 15f)
            setText(
                initial
            )
            setPadding(
                space(14),
                0,
                space(14),
                0
            )
            isSingleLine =
                true
            background =
                rounded(
                    card,
                    DevelopUgandaFivemods8Theme.surface,
                    14
                )
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
            DevelopUgandaFivemods8Theme.applyTypeScale(this, size)
            setTextColor(
                color
            )
            typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    if (bold) {
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
                if (radius >= 50) dp(radius).toFloat() else DevelopUgandaFivemods8Theme.radiusPx.toFloat()
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

    private fun full(
        height: Int,
        top: Int,
        bottom: Int
    ): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            height
        ).apply {
            topMargin =
                space(top)
            bottomMargin =
                space(bottom)
        }
    }

    private fun navWeight():
        LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            dp(52),
            1f
        )
    }

    private fun dp(
        value: Int
    ): Int {
        return (
            value *
                resources.displayMetrics.density
            ).roundToInt()
    }

    private fun space(value: Int): Int = DevelopUgandaFivemods8Theme.spacingPx(value)

    private fun touchMinimum(): Int = DevelopUgandaFivemods8Theme.touchMinimumPx

    private fun toast(
        value: String
    ) {
        Toast.makeText(
            this,
            value,
            Toast.LENGTH_SHORT
        ).show()
    }
}
