package com.sentongoharuna.pulse

import android.app.Activity
import android.content.Context

/** Navigator order and persisted recording identity for every camera face. */
enum class DevelopUgandaCameraPage {
    MAIN,
    LIVE,
    TIKTOK,
    STATUS,
    INTERVIEW
}

enum class DevelopUgandaDeliveryProfile {
    NONE,
    TIKTOK_UPLOAD,
    WHATSAPP_STATUS,
    INTERVIEW_DUAL
}

enum class DevelopUgandaModeDataAvailability {
    PRESENT,
    ABSENT
}

enum class DevelopUgandaModeGlyph {
    MAIN_CAMERA,
    LIVE_CAMERA,
    TIKTOK_NOTE,
    STATUS_BUBBLE,
    INTERVIEW_MIC
}

enum class DevelopUgandaStageAvailability {
    PRESENT,
    ABSENT,
}

data class DevelopUgandaModeStage(
    val mode: DevelopUgandaCameraPage,
    val id: String,
    val label: String,
    val nextAction: String,
    val availability: DevelopUgandaStageAvailability = DevelopUgandaStageAvailability.PRESENT,
)

/**
 * One source of truth for mode identity and routing.
 *
 * MAIN and LIVE pre-date additive capture profiles. Their availability is
 * explicitly ABSENT; callers must not manufacture aspect, rate or delivery
 * values for them. Their route identity remains fully represented.
 */
data class DevelopUgandaModeProfile(
    val page: DevelopUgandaCameraPage,
    val experienceId: String,
    val displayName: String,
    val shortName: String,
    val routeDescription: String,
    val destination: Class<out Activity>,
    val glyph: DevelopUgandaModeGlyph,
    val bestFor: String,
    val instruction: String,
    /** Preserved capture/burn-in semantic; presentation repair must not change it. */
    val accentColor: Int,
    /** Chrome-only identity for route cards, headers and mode strips. */
    val chromeAccentColor: Int,
    val qualityName: String,
    val sceneName: String,
    val lookName: String,
    val deliveryProfile: DevelopUgandaDeliveryProfile,
    val frameRate: Int? = null,
    val targetBitrateBps: Int? = null,
    val stages: List<DevelopUgandaModeStage>,
    val portraitOutput: Boolean = true,
    val profileData: DevelopUgandaModeDataAvailability = DevelopUgandaModeDataAvailability.PRESENT,
) {
    val hasProfileData: Boolean get() = profileData == DevelopUgandaModeDataAvailability.PRESENT
}

object DevelopUgandaModeProfiles {
    private const val PREFS = "develop_uganda_selected_camera_mode"
    private const val SELECTED_PAGE = "selected_page"

    val main = DevelopUgandaModeProfile(
        page = DevelopUgandaCameraPage.MAIN,
        experienceId = "MAIN",
        displayName = "MAIN CAM",
        shortName = "MAIN",
        routeDescription = "Main Cam",
        destination = DevelopUgandaAllProCameraActivity::class.java,
        glyph = DevelopUgandaModeGlyph.MAIN_CAMERA,
        bestFor = "ABSENT",
        instruction = "PROFILE DATA ABSENT",
        accentColor = DevelopUgandaFivemods8Theme.accent,
        chromeAccentColor = DevelopUgandaFivemods8Theme.modeMain,
        qualityName = "ABSENT",
        sceneName = "ABSENT",
        lookName = "ABSENT",
        deliveryProfile = DevelopUgandaDeliveryProfile.NONE,
        stages = listOf(
            DevelopUgandaModeStage(DevelopUgandaCameraPage.MAIN, "READY", "READY", "RUN READY CHECK", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.MAIN, "FRAME", "FRAME", "SET THE FRAME", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.MAIN, "RECORD", "RECORD", "START MAIN TAKE", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.MAIN, "REVIEW", "REVIEW", "VERIFY LAST TAKE", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.MAIN, "DELIVER", "DELIVER", "OPEN DELIVERY", DevelopUgandaStageAvailability.ABSENT),
        ),
        portraitOutput = false,
        profileData = DevelopUgandaModeDataAvailability.ABSENT,
    )

    val live = DevelopUgandaModeProfile(
        page = DevelopUgandaCameraPage.LIVE,
        experienceId = "LIVE",
        displayName = "LIVE CAM",
        shortName = "LIVE",
        routeDescription = "Live Cam",
        destination = DevelopUgandaLiveActivity::class.java,
        glyph = DevelopUgandaModeGlyph.LIVE_CAMERA,
        bestFor = "ABSENT",
        instruction = "PROFILE DATA ABSENT",
        accentColor = DevelopUgandaFivemods8Theme.accent,
        chromeAccentColor = DevelopUgandaFivemods8Theme.modeLive,
        qualityName = "ABSENT",
        sceneName = "ABSENT",
        lookName = "ABSENT",
        deliveryProfile = DevelopUgandaDeliveryProfile.NONE,
        stages = listOf(
            DevelopUgandaModeStage(DevelopUgandaCameraPage.LIVE, "STREAM_KEY", "STREAM KEY", "ENTER STREAM KEY", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.LIVE, "CONNECTION", "CONNECTION", "CHECK CONNECTION", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.LIVE, "FRAME", "FRAME", "SET LIVE FRAME", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.LIVE, "RECORD", "LOCAL RECORD", "START LOCAL BACKUP", DevelopUgandaStageAvailability.ABSENT),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.LIVE, "REVIEW", "REVIEW", "VERIFY LOCAL TAKE", DevelopUgandaStageAvailability.ABSENT),
        ),
        portraitOutput = false,
        profileData = DevelopUgandaModeDataAvailability.ABSENT,
    )

    val tikTok = DevelopUgandaModeProfile(
        page = DevelopUgandaCameraPage.TIKTOK,
        experienceId = "V281_TIKTOK",
        displayName = "TIKTOK CAM",
        shortName = "TIKTOK",
        routeDescription = "TikTok Cam",
        destination = DevelopUgandaTikTokCameraActivity::class.java,
        glyph = DevelopUgandaModeGlyph.TIKTOK_NOTE,
        bestFor = "TIKTOK / REELS / SHORTS",
        instruction = "CLEAN MASTER + TIKTOK UPLOAD COPY",
        accentColor = DevelopUgandaFivemods8Theme.accent,
        chromeAccentColor = DevelopUgandaFivemods8Theme.modeTikTok,
        qualityName = "SOCIAL FHD",
        sceneName = "REPORTER",
        lookName = "NATURAL",
        deliveryProfile = DevelopUgandaDeliveryProfile.TIKTOK_UPLOAD,
        frameRate = 60,
        targetBitrateBps = 18_000_000,
        stages = listOf(
            DevelopUgandaModeStage(DevelopUgandaCameraPage.TIKTOK, "READY", "READY", "RUN READY CHECK"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.TIKTOK, "FRAME", "VERTICAL FRAME", "SET VERTICAL FRAME"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.TIKTOK, "RECORD", "RECORD", "START TIKTOK TAKE"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.TIKTOK, "REVIEW", "REVIEW", "VERIFY LAST TAKE"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.TIKTOK, "CAPTIONS", "CAPTIONS", "REVIEW TRANSCRIPT"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.TIKTOK, "DELIVERY", "DELIVERY COPY", "VERIFY BRAND COPY"),
        ),
    )

    val status = DevelopUgandaModeProfile(
        page = DevelopUgandaCameraPage.STATUS,
        experienceId = "V281_STATUS",
        displayName = "STATUS CAM",
        shortName = "STATUS",
        routeDescription = "WhatsApp Status",
        destination = DevelopUgandaStatusCameraActivity::class.java,
        glyph = DevelopUgandaModeGlyph.STATUS_BUBBLE,
        bestFor = "WHATSAPP STATUS",
        instruction = "CLEAN MASTER + WHATSAPP STATUS COPY",
        accentColor = DevelopUgandaFivemods8Theme.accent,
        chromeAccentColor = DevelopUgandaFivemods8Theme.modeStatus,
        qualityName = "SOCIAL FHD",
        sceneName = "REPORTER",
        lookName = "NATURAL",
        deliveryProfile = DevelopUgandaDeliveryProfile.WHATSAPP_STATUS,
        frameRate = 30,
        targetBitrateBps = 7_000_000,
        stages = listOf(
            DevelopUgandaModeStage(DevelopUgandaCameraPage.STATUS, "READY", "READY", "RUN READY CHECK"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.STATUS, "SEGMENT", "SEGMENT", "SET STATUS SEGMENT"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.STATUS, "RECORD", "RECORD", "START STATUS TAKE"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.STATUS, "REVIEW", "REVIEW", "VERIFY LAST TAKE"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.STATUS, "CAPTIONS", "CAPTIONS", "REVIEW TRANSCRIPT"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.STATUS, "SHARE", "SHARE", "OPEN SHARE STEP"),
        ),
    )

    val interview = DevelopUgandaModeProfile(
        page = DevelopUgandaCameraPage.INTERVIEW,
        experienceId = "V281_INTERVIEW",
        displayName = "INTERVIEW CAM",
        shortName = "INTERVIEW",
        routeDescription = "Interview Cam",
        destination = DevelopUgandaInterviewCameraActivity::class.java,
        glyph = DevelopUgandaModeGlyph.INTERVIEW_MIC,
        bestFor = "SINGLE-SUBJECT INTERVIEWS",
        instruction = "16:9 ARCHIVE • ON-DEVICE OBSERVATION HUD • CLEAN MASTER",
        accentColor = DevelopUgandaFivemods8Theme.accent,
        chromeAccentColor = DevelopUgandaFivemods8Theme.modeInterview,
        qualityName = "MASTER UHD",
        sceneName = "INTERVIEW",
        lookName = "CLEAN",
        deliveryProfile = DevelopUgandaDeliveryProfile.INTERVIEW_DUAL,
        frameRate = 30,
        targetBitrateBps = 18_000_000,
        stages = listOf(
            DevelopUgandaModeStage(DevelopUgandaCameraPage.INTERVIEW, "SUBJECT_LOCK", "SUBJECT LOCK", "LOCK SUBJECT"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.INTERVIEW, "SOUND", "SOUND", "VERIFY MICROPHONE"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.INTERVIEW, "FRAME", "FRAME", "SET INTERVIEW FRAME"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.INTERVIEW, "RECORD", "RECORD", "START INTERVIEW TAKE"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.INTERVIEW, "MARKERS", "QUESTION MARKERS", "MARK QUESTIONS"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.INTERVIEW, "TRANSCRIPT", "TRANSCRIPT REVIEW", "REVIEW TRANSCRIPT"),
            DevelopUgandaModeStage(DevelopUgandaCameraPage.INTERVIEW, "DELIVERY", "DUAL OUTPUT", "VERIFY CLEAN + BRAND"),
        ),
        portraitOutput = false,
    )

    private val navigatorOrder = listOf(main, live, tikTok, status, interview)

    fun all(): List<DevelopUgandaModeProfile> = navigatorOrder

    fun forPage(page: DevelopUgandaCameraPage): DevelopUgandaModeProfile =
        navigatorOrder.first { it.page == page }

    fun forExperience(experienceId: String): DevelopUgandaModeProfile? =
        navigatorOrder.firstOrNull {
            it.experienceId.equals(experienceId, ignoreCase = true) ||
                it.page.name.equals(experienceId, ignoreCase = true)
        }

    fun rememberSelected(context: Context, page: DevelopUgandaCameraPage) {
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(SELECTED_PAGE, page.name).apply()
    }

    fun selected(context: Context): DevelopUgandaModeProfile {
        val saved = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(SELECTED_PAGE, null)
        val page = saved?.let { runCatching { DevelopUgandaCameraPage.valueOf(it) }.getOrNull() }
        return page?.let(::forPage) ?: main
    }

    fun stageForDetailed(context: Context, detailed: String): DevelopUgandaModeStage? {
        val profile = selected(context)
        val preferred = when (detailed.uppercase()) {
            "PREPARE" -> listOf("READY", "SUBJECT_LOCK", "STREAM_KEY")
            "LIGHT", "CONTINUITY", "FRAME", "FOCUS", "REHEARSE" -> listOf("FRAME", "SUBJECT_LOCK")
            "SOUND" -> listOf("SOUND", "READY")
            "RECORD" -> listOf("RECORD")
            "VERIFY", "REVIEW", "ORGANIZE" -> listOf("REVIEW", "TRANSCRIPT", "CAPTIONS")
            "DELIVER" -> listOf("DELIVER", "DELIVERY", "SHARE")
            else -> emptyList()
        }
        return preferred.firstNotNullOfOrNull { id -> profile.stages.firstOrNull { it.id == id } }
            ?: profile.stages.firstOrNull { it.availability == DevelopUgandaStageAvailability.PRESENT }
            ?: profile.stages.firstOrNull()
    }
}
