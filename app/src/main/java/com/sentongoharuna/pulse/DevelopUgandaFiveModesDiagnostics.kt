package com.sentongoharuna.pulse

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Read-only operational notes for the five camera faces. The ledger observes
 * lifecycle milestones only; it does not open, close, or alter CameraX.
 */
object DevelopUgandaFiveModesDiagnostics {

    data class PreviewState(
        val page: DevelopUgandaCameraPage,
        val state: String,
        val detail: String,
        val updatedAtMs: Long
    )

    private const val PREFS = "develop_uganda_fivemods6_preview_diagnostics"

    fun markSwitchRequested(
        context: Context,
        from: DevelopUgandaCameraPage,
        to: DevelopUgandaCameraPage
    ) {
        write(context, from, "SWITCHING", "to ${labelFor(to)}")
        write(context, to, "STARTING", "from ${labelFor(from)}")
    }

    fun markPreviewReady(context: Context, page: DevelopUgandaCameraPage) {
        write(context, page, "READY", "preview stream attached")
    }

    fun markPreviewRetry(
        context: Context,
        page: DevelopUgandaCameraPage,
        attempt: Int,
        maximum: Int
    ) {
        write(context, page, "RETRY $attempt/$maximum", "waiting for preview stream")
    }

    fun markPreviewUnavailable(context: Context, page: DevelopUgandaCameraPage) {
        write(context, page, "CHECK", "preview did not report ready after retries")
    }

    fun snapshot(context: Context): List<PreviewState> =
        DevelopUgandaCameraPage.values().map { page ->
            val prefs = prefs(context)
            PreviewState(
                page = page,
                state = prefs.getString("${page.name}_state", "NOT TESTED").orEmpty(),
                detail = prefs.getString("${page.name}_detail", "open this mode to test").orEmpty(),
                updatedAtMs = prefs.getLong("${page.name}_time", 0L)
            )
        }

    fun summary(context: Context): String =
        snapshot(context).joinToString("\n") { state ->
            "${labelFor(state.page)} • ${state.state} • ${formatTime(state.updatedAtMs)}"
        }

    private fun write(
        context: Context,
        page: DevelopUgandaCameraPage,
        state: String,
        detail: String
    ) {
        prefs(context)
            .edit()
            .putString("${page.name}_state", state)
            .putString("${page.name}_detail", detail)
            .putLong("${page.name}_time", System.currentTimeMillis())
            .apply()
    }

    private fun prefs(context: Context) =
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun labelFor(page: DevelopUgandaCameraPage): String =
        DevelopUgandaModeProfiles.forPage(page).displayName

    private fun formatTime(value: Long): String =
        if (value <= 0L) {
            "not yet"
        } else {
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(value))
        }
}
