package com.sentongoharuna.pulse

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * V235 keeps the V234 navy-safe design language:
 * dark navy, thin accent borders, bottom-sheet placement and a scroll-safe list.
 */
object DevelopUgandaNavySheet {

    fun show(
        activity: AppCompatActivity,
        title: String,
        subtitle: String,
        labels: Array<String>,
        selectedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val dialog = Dialog(activity)
        dialog.setCancelable(true)

        val panel = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 14))
            background = rounded(
                activity,
                DevelopUgandaFivemods8Theme.surfaceScrim(247),
                DevelopUgandaFivemods8Theme.outline,
                22
            )
        }

        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleBox = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        titleBox.addView(
            TextView(activity).apply {
                text = title
                textSize = 14.5f
                setTextColor(DevelopUgandaFivemods8Theme.content)
                typeface = Typeface.DEFAULT_BOLD
            }
        )

        titleBox.addView(
            TextView(activity).apply {
                text = subtitle
                textSize = 7.7f
                setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(activity, 2), 0, dp(activity, 5))
            }
        )

        header.addView(
            titleBox,
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            Button(activity).apply {
                text = "×"
                textSize = 18f
                isAllCaps = false
                setTextColor(DevelopUgandaFivemods8Theme.content)
                background = rounded(
                    activity,
                    DevelopUgandaFivemods8Theme.surfaceRaised,
                    DevelopUgandaFivemods8Theme.accent,
                    13
                )
                setOnClickListener { dialog.dismiss() }
            },
            LinearLayout.LayoutParams(
                dp(activity, 44),
                dp(activity, 40)
            )
        )

        panel.addView(header)

        val list = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex

            list.addView(
                Button(activity).apply {
                    text =
                        if (selected) {
                            "✓  $label"
                        } else {
                            label
                        }
                    textSize = 9.1f
                    isAllCaps = false
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(DevelopUgandaFivemods8Theme.content)
                    setPadding(
                        dp(activity, 13),
                        0,
                        dp(activity, 10),
                        0
                    )
                    minHeight = dp(activity, 50)
                    background = rounded(
                        activity,
                        if (selected) {
                            DevelopUgandaFivemods8Theme.surface
                        } else {
                            DevelopUgandaFivemods8Theme.surfaceRaised
                        },
                        if (selected) {
                            DevelopUgandaFivemods8Theme.contentDim
                        } else {
                            DevelopUgandaFivemods8Theme.outline
                        },
                        14
                    )
                    setOnClickListener {
                        dialog.dismiss()
                        onSelected(index)
                    }
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(activity, 50)
                ).apply {
                    topMargin = dp(activity, 5)
                }
            )
        }

        val scroll = ScrollView(activity).apply {
            isFillViewport = false
            addView(
                list,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        panel.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        dialog.setContentView(panel)

        dialog.setOnShowListener {
            dialog.window?.let { window ->
                window.setBackgroundDrawable(ColorDrawable(DevelopUgandaFivemods8Theme.transparent))
                window.setGravity(Gravity.BOTTOM)
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (activity.resources.displayMetrics.heightPixels * 0.72f).toInt()
                )
                window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                val attributes = window.attributes
                attributes.dimAmount = 0.44f
                window.attributes = attributes
            }
        }

        dialog.show()
    }

    private fun rounded(
        activity: AppCompatActivity,
        fill: Int,
        stroke: Int,
        radius: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(activity, radius).toFloat()
            setStroke(dp(activity, 1), stroke)
        }

    private fun dp(
        activity: AppCompatActivity,
        value: Int
    ): Int =
        (value * activity.resources.displayMetrics.density).toInt()
}
