package com.sentongoharuna.pulse

import android.graphics.Typeface
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

/** One action-colour and type route for every operator dialog. */
object DevelopUgandaDialogStyler {
    fun show(
        dialog: android.app.AlertDialog,
        page: DevelopUgandaCameraPage? = null,
    ): android.app.AlertDialog {
        dialog.show()
        val accent = page?.let { DevelopUgandaFivemods12Identity.forPage(it).accent }
            ?: DevelopUgandaFivemods8Theme.accent
        listOf(
            android.app.AlertDialog.BUTTON_POSITIVE,
            android.app.AlertDialog.BUTTON_NEGATIVE,
            android.app.AlertDialog.BUTTON_NEUTRAL,
        ).mapNotNull(dialog::getButton).forEach { action ->
            action.setTextColor(accent)
            DevelopUgandaFivemods8Theme.applyTypeScale(action, 11f)
            action.typeface = Typeface.DEFAULT_BOLD
        }
        dialog.findViewById<TextView>(android.R.id.message)?.let { body ->
            DevelopUgandaFivemods8Theme.applyTypeScale(body, 11f)
            body.setTextColor(DevelopUgandaFivemods8Theme.content)
        }
        val titleId = dialog.context.resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<TextView>(titleId)?.let { title ->
            DevelopUgandaFivemods8Theme.applyTypeScale(title, 13f)
            title.setTextColor(DevelopUgandaFivemods8Theme.content)
        }
        return dialog
    }

    fun show(
        dialog: AlertDialog,
        page: DevelopUgandaCameraPage? = null,
    ): AlertDialog {
        dialog.show()
        val accent = page?.let { DevelopUgandaFivemods12Identity.forPage(it).accent }
            ?: DevelopUgandaFivemods8Theme.accent
        listOf(
            AlertDialog.BUTTON_POSITIVE,
            AlertDialog.BUTTON_NEGATIVE,
            AlertDialog.BUTTON_NEUTRAL,
        ).mapNotNull(dialog::getButton).forEach { action ->
            action.setTextColor(accent)
            DevelopUgandaFivemods8Theme.applyTypeScale(action, 11f)
            action.typeface = Typeface.DEFAULT_BOLD
        }
        dialog.findViewById<TextView>(android.R.id.message)?.let { body ->
            DevelopUgandaFivemods8Theme.applyTypeScale(body, 11f)
            body.setTextColor(DevelopUgandaFivemods8Theme.content)
        }
        val titleId = dialog.context.resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<TextView>(titleId)?.let { title ->
            DevelopUgandaFivemods8Theme.applyTypeScale(title, 13f)
            title.setTextColor(DevelopUgandaFivemods8Theme.content)
        }
        return dialog
    }
}
