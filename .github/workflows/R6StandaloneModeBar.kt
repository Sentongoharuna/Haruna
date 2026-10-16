package com.r6.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout

/** Five transparent standalone mode icons; host must supply original R6 drawables and callbacks. */
class R6StandaloneModeBar(context: Context) : LinearLayout(context) {
    enum class Mode { MAIN, LIVE, TIKTOK, STATUS, INTERVIEW }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        setBackgroundColor(Color.TRANSPARENT)
        background = null
        elevation = 0f
        clipChildren = false
        clipToPadding = false
    }

    fun bind(icons: Map<Mode, Drawable>, onModeSelected: (Mode) -> Unit) {
        removeAllViews()
        for (mode in Mode.values()) {
            val icon = requireNotNull(icons[mode]) { "Missing original R6 icon: $mode" }
            val button = ImageButton(context).apply {
                setImageDrawable(icon)
                background = null
                setBackgroundColor(Color.TRANSPARENT)
                backgroundTintList = null
                imageTintList = null // preserve original icon appearance
                elevation = 0f
                stateListAnimator = null
                scaleType = ImageButton.ScaleType.FIT_CENTER
                setPadding(dp(9), dp(9), dp(9), dp(9))
                contentDescription = mode.name
                setOnClickListener { onModeSelected(mode) }
            }
            addView(button, LayoutParams(0, dp(58), 1f))
        }
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()
}
