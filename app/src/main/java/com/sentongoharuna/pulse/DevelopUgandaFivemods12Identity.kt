package com.sentongoharuna.pulse

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * FIVEMODS 12 identity is deliberately presentation/metadata only.
 *
 * The capture profiles, CameraX use cases, encoder settings, exposure policy and
 * CLEAN bytes are not available to this component.  BRAND obtains the same
 * identity through the established Media3 overlay pass; CLEAN obtains it only
 * through its filename, recovery manifest and this sidecar.
 */
data class DevelopUgandaFivemods12ModeIdentity(
    val page: DevelopUgandaCameraPage,
    val code: String,
    val name: String,
    val iconLabel: String,
    val accent: Int,
) {
    val badge: String get() = "$iconLabel  $code • $name"
    val burnLabel: String get() = "$code • $name"
}

object DevelopUgandaFivemods12Identity {
    private val identities by lazy {
        listOf(
            DevelopUgandaFivemods12ModeIdentity(
                DevelopUgandaCameraPage.MAIN,
                "MCAM",
                DevelopUgandaModeProfiles.main.displayName,
                "CAM",
                DevelopUgandaModeProfiles.main.chromeAccentColor,
            ),
            DevelopUgandaFivemods12ModeIdentity(
                DevelopUgandaCameraPage.LIVE,
                "LIVE",
                DevelopUgandaModeProfiles.live.displayName,
                "AIR",
                DevelopUgandaModeProfiles.live.chromeAccentColor,
            ),
            DevelopUgandaFivemods12ModeIdentity(
                DevelopUgandaCameraPage.TIKTOK,
                "TIK",
                DevelopUgandaModeProfiles.tikTok.displayName,
                "NOTE",
                DevelopUgandaModeProfiles.tikTok.chromeAccentColor,
            ),
            DevelopUgandaFivemods12ModeIdentity(
                DevelopUgandaCameraPage.STATUS,
                "STAT",
                DevelopUgandaModeProfiles.status.displayName,
                "CHAT",
                DevelopUgandaModeProfiles.status.chromeAccentColor,
            ),
            DevelopUgandaFivemods12ModeIdentity(
                DevelopUgandaCameraPage.INTERVIEW,
                "INTV",
                DevelopUgandaModeProfiles.interview.displayName,
                "MIC",
                DevelopUgandaModeProfiles.interview.chromeAccentColor,
            ),
        )
    }

    fun all(): List<DevelopUgandaFivemods12ModeIdentity> = identities

    fun forPage(page: DevelopUgandaCameraPage): DevelopUgandaFivemods12ModeIdentity =
        identities.first { it.page == page }

    fun forModeText(value: String?): DevelopUgandaFivemods12ModeIdentity? {
        val upper = value.orEmpty().uppercase(Locale.US)
        return identities.firstOrNull { identity ->
            upper == identity.page.name ||
                upper.contains(identity.code) ||
                upper.contains(identity.name) ||
                (identity.page == DevelopUgandaCameraPage.TIKTOK && upper.contains("TIKTOK")) ||
                (identity.page == DevelopUgandaCameraPage.STATUS && upper.contains("WHATSAPP")) ||
                (identity.page == DevelopUgandaCameraPage.INTERVIEW && upper.contains("INTERVIEW"))
        }
    }

    /** Prefixes a filename stem without changing any media bytes. */
    fun prefixedStem(page: DevelopUgandaCameraPage, rawStem: String): String {
        val identity = forPage(page)
        val safe = rawStem
            .trim()
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .removePrefix("DU_")
            .ifBlank { "TAKE_${System.currentTimeMillis()}" }
        return if (safe.startsWith("${identity.code}_", ignoreCase = true)) {
            safe
        } else {
            "${identity.code}_$safe"
        }
    }

    fun identityLine(page: DevelopUgandaCameraPage): String = forPage(page).burnLabel

    /** Keeps the media pickers compatible with both FIVEMODS 12 and legacy names. */
    fun mediaStoreNameWhere(column: String): String =
        mediaStoreNamePatterns().joinToString(prefix = "(", postfix = ")", separator = " OR ") {
            "$column LIKE ?"
        }

    fun mediaStoreNameArgs(): Array<String> = mediaStoreNamePatterns().toTypedArray()

    fun isAppMediaName(displayName: String): Boolean {
        val upper = displayName.uppercase(Locale.US)
        return upper.startsWith("DEVELOP_UGANDA_") || identities.any { upper.startsWith("${it.code}_") }
    }

    private fun mediaStoreNamePatterns(): List<String> =
        identities.map { "${it.code}_%" } + "DEVELOP_UGANDA_%"

    /**
     * Writes a small sidecar for CLEAN.  The original URI is intentionally not
     * copied into the sidecar and the source file is never opened for writing.
     */
    fun writeCleanSidecar(
        context: Context,
        page: DevelopUgandaCameraPage,
        displayName: String,
        verified: Boolean,
        durationMs: Long,
        bytes: Long,
    ): File? = runCatching {
        val identity = forPage(page)
        val stem = prefixedStem(page, displayName.substringBeforeLast('.'))
        val directory = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
            "develop.uganda/Identity",
        ).apply { mkdirs() }
        val target = File(directory, "${stem}_IDENTITY.json")
        val pending = File(directory, "${target.name}.pending")
        val json = JSONObject().apply {
            put("schema", "develop.uganda.fivemods12.identity.v1")
            put("modeCode", identity.code)
            put("modeName", identity.name)
            put("mode", identity.page.name)
            put("cleanMaster", true)
            put("cleanBytesModified", false)
            put("sourceDisplayName", displayName.substringAfterLast('/'))
            put("verified", verified)
            put("durationMs", durationMs.coerceAtLeast(0L))
            put("bytes", bytes.coerceAtLeast(0L))
            put("writtenUtc", Instant.now().toString())
        }
        pending.writeText(json.toString(2), Charsets.UTF_8)
        if (target.exists() && !target.delete()) error("Identity sidecar replace failed")
        if (!pending.renameTo(target)) error("Identity sidecar commit failed")
        target
    }.getOrNull()
}
