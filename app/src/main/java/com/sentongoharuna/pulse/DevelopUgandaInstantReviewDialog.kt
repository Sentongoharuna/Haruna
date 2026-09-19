package com.sentongoharuna.pulse

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

object DevelopUgandaInstantReviewDialog {

    fun show(
        context: Context,
        result: DevelopUgandaClipQc.Result,
        packageId: String,
        socialMasterExpected: Boolean,
        onMatchLastShot: (() -> Unit)? = null
    ) {
        val panel =
            LinearLayout(
                context
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(
                        context,
                        14
                    ),
                    dp(
                        context,
                        10
                    ),
                    dp(
                        context,
                        14
                    ),
                    dp(
                        context,
                        8
                    )
                )
            }

        panel.addView(
            title(
                context,
                "INSTANT CLIP QC"
            )
        )

        result.lines()
            .forEach {
                panel.addView(
                    line(
                        context,
                        it
                    )
                )
            }

        val row1 =
            row(
                context
            )

        row1.addView(
            button(
                context,
                "PLAY"
            ) {
                play(
                    context,
                    result.sourceUri,
                    "INSTANT REVIEW"
                )
            },
            weight()
        )

        row1.addView(
            button(
                context,
                "SHARE"
            ) {
                share(
                    context,
                    result.sourceUri
                )
            },
            weight(
                dp(
                    context,
                    6
                )
            )
        )

        panel.addView(
            row1
        )

        val row2 =
            row(
                context
            )

        row2.addView(
            button(
                context,
                "PACKAGE"
            ) {
                context.startActivity(
                    Intent(
                        context,
                        DevelopUgandaStoryPackagesActivity::class.java
                    )
                )
            },
            weight()
        )

        row2.addView(
            button(
                context,
                "SOCIAL MASTER"
            ) {
                val social =
                    DevelopUgandaStoryPackager
                        .findPackageFile(
                            context,
                            packageId,
                            "SOCIAL_MASTER.mp4"
                        )

                if (
                    social !=
                        null
                ) {
                    play(
                        context,
                        social,
                        "SOCIAL MASTER"
                    )
                } else {
                    toast(
                        context,
                        if (
                            socialMasterExpected
                        ) {
                            "Social master is still preparing or unavailable"
                        } else {
                            "This recording did not request a separate social master"
                        }
                    )
                }
            },
            weight(
                dp(
                    context,
                    6
                )
            )
        )

        panel.addView(
            row2
        )

        val colorRow =
            row(
                context
            )

        colorRow.addView(
            button(
                context,
                "COLOR MASTER"
            ) {
                val color =
                    DevelopUgandaStoryPackager
                        .findPackageFile(
                            context,
                            packageId,
                            "COLOR_MASTER.mp4"
                        )

                if (
                    color !=
                        null
                ) {
                    play(
                        context,
                        color,
                        "V233 COLOR MASTER"
                    )
                } else {
                    toast(
                        context,
                        "V233 Color Master is still preparing, ORIGINAL was selected, or export was unavailable"
                    )
                }
            },
            weight()
        )

        colorRow.addView(
            button(
                context,
                "COLOR STUDIO"
            ) {
                context.startActivity(
                    Intent(
                        context,
                        DevelopUgandaColorStudioActivity::class.java
                    )
                )
            },
            weight(
                dp(
                    context,
                    6
                )
            )
        )

        panel.addView(
            colorRow
        )

        if (
            onMatchLastShot !=
                null
        ) {
            panel.addView(
                button(
                    context,
                    "MATCH LAST SHOT"
                ) {
                    onMatchLastShot.invoke()
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(
                        context,
                        44
                    )
                ).apply {
                    topMargin =
                        dp(
                            context,
                            6
                        )
                }
            )
        }

        val deleteButton =
            button(
                context,
                "DELETE GALLERY SOURCE"
            ) {
                val packageEntry =
                    DevelopUgandaStoryPackager
                        .listRegistry(
                            context
                        )
                        .firstOrNull {
                            it.packageId ==
                                packageId
                        }

                if (
                    packageEntry !=
                        null &&
                    packageEntry.state.contains(
                        "BUILDING",
                        ignoreCase =
                            true
                    )
                ) {
                    toast(
                        context,
                        "Wait for Story Package to finish before deleting the Gallery source"
                    )

                    return@button
                }

                AlertDialog.Builder(
                    context
                )
                    .setTitle(
                        "DELETE ORIGINAL GALLERY VIDEO?"
                    )
                    .setMessage(
                        "This permanently deletes the Gallery source selected for this review. A completed Story Package copy, if already created, is separate. This action cannot be undone."
                    )
                    .setNegativeButton(
                        "CANCEL",
                        null
                    )
                    .setPositiveButton(
                        "DELETE"
                    ) {
                            _,
                            _ ->
                        try {
                            val deleted =
                                context.contentResolver.delete(
                                    result.sourceUri,
                                    null,
                                    null
                                )

                            toast(
                                context,
                                if (
                                    deleted >
                                        0
                                ) {
                                    "Gallery source deleted"
                                } else {
                                    "Video was not deleted"
                                }
                            )
                        } catch (_: Exception) {
                            toast(
                                context,
                                "Android did not allow this video to be deleted"
                            )
                        }
                    }
                    .show()
            }

        deleteButton.setTextColor(
            DevelopUgandaFivemods8Theme.accent
        )

        panel.addView(
            deleteButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(
                    context,
                    42
                )
            ).apply {
                topMargin =
                    dp(
                        context,
                        8
                    )
            }
        )

        val scroll =
            ScrollView(
                context
            ).apply {
                addView(
                    panel
                )
            }

        AlertDialog.Builder(
            context
        )
            .setTitle(
                "develop.uganda • V233 REVIEW"
            )
            .setView(
                scroll
            )
            .setNegativeButton(
                "RECORD NEXT",
                null
            )
            .show()
    }

    private fun play(
        context: Context,
        uri: Uri,
        label: String
    ) {
        try {
            context.startActivity(
                Intent(
                    context,
                    DevelopUgandaStoryPlayerActivity::class.java
                ).apply {
                    putExtra(
                        DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_URI,
                        uri.toString()
                    )

                    putExtra(
                        DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_LABEL,
                        label
                    )
                }
            )
        } catch (_: Exception) {
            toast(
                context,
                "Internal player could not open this video"
            )
        }
    }

    private fun share(
        context: Context,
        uri: Uri
    ) {
        if (
            DevelopUgandaBrandMetadataStore
                .exactGpsVisible(
                    context
                )
        ) {
            AlertDialog.Builder(
                context
            )
                .setTitle(
                    "PRIVACY CHECK • EXACT GPS VISIBLE"
                )
                .setMessage(
                    "Your current V233 burn-in profile includes exact latitude/longitude. If this is the clip you just recorded, those coordinates may be permanently visible in the video. Share anyway?"
                )
                .setNegativeButton(
                    "CANCEL",
                    null
                )
                .setPositiveButton(
                    "SHARE ANYWAY"
                ) {
                        _,
                        _ ->
                    shareNow(
                        context,
                        uri
                    )
                }
                .show()

            return
        }

        shareNow(
            context,
            uri
        )
    }

    private fun shareNow(
        context: Context,
        uri: Uri
    ) {
        try {
            context.startActivity(
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
                    "Share video"
                )
            )
        } catch (_: Exception) {
            toast(
                context,
                "Share is unavailable"
            )
        }
    }


    private fun title(
        context: Context,
        value: String
    ): TextView =
        TextView(
            context
        ).apply {
            text =
                value

            textSize =
                15f

            setTextColor(
                DevelopUgandaFivemods8Theme.content
            )

            typeface =
                Typeface.DEFAULT_BOLD

            setPadding(
                0,
                0,
                0,
                dp(
                    context,
                    7
                )
            )
        }

    private fun line(
        context: Context,
        value: String
    ): TextView =
        TextView(
            context
        ).apply {
            text =
                value

            textSize =
                10f

            setTextColor(
                if (
                    value.contains(
                        "CHECK"
                    )
                ) {
                    DevelopUgandaFivemods8Theme.accent
                } else {
                    DevelopUgandaFivemods8Theme.contentDim
                }
            )

            typeface =
                Typeface.MONOSPACE

            setPadding(
                dp(
                    context,
                    4
                ),
                dp(
                    context,
                    3
                ),
                dp(
                    context,
                    4
                ),
                dp(
                    context,
                    3
                )
            )
        }

    private fun row(
        context: Context
    ): LinearLayout =
        LinearLayout(
            context
        ).apply {
            orientation =
                LinearLayout.HORIZONTAL

            setPadding(
                0,
                dp(
                    context,
                    7
                ),
                0,
                0
            )
        }

    private fun button(
        context: Context,
        value: String,
        action: () -> Unit
    ): Button =
        Button(
            context
        ).apply {
            text =
                value

            textSize =
                8f

            isAllCaps =
                false

            setTextColor(
                DevelopUgandaFivemods8Theme.content
            )

            typeface =
                Typeface.DEFAULT_BOLD

            background =
                GradientDrawable().apply {
                    cornerRadius =
                        dp(
                            context,
                            16
                        ).toFloat()

                    setColor(
                        DevelopUgandaFivemods8Theme.surfaceRaised
                    )

                    setStroke(
                        dp(
                            context,
                            1
                        ),
                        DevelopUgandaFivemods8Theme.accent
                    )
                }

            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "INSTANT REVIEW BUTTON") { action.invoke() }
            }
        }

    private fun weight(
        marginStart: Int = 0
    ): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            this.marginStart =
                marginStart
        }

    private fun dp(
        context: Context,
        value: Int
    ): Int =
        (
            value *
                context.resources
                    .displayMetrics
                    .density
            ).roundToInt()

    private fun toast(
        context: Context,
        value: String
    ) {
        Toast.makeText(
            context,
            value,
            Toast.LENGTH_SHORT
        ).show()
    }
}
