package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class DevelopUgandaBrandMetadataActivity :
    AppCompatActivity() {

    private lateinit var displayNameEdit: EditText
    private lateinit var organizationEdit: EditText
    private lateinit var appCreditCheck: CheckBox
    private lateinit var outputProfileView: TextView
    private lateinit var presetView: TextView
    private lateinit var previewTitleView: TextView
    private lateinit var previewRowsView: TextView

    private val checks =
        linkedMapOf<
            DevelopUgandaBrandMetadataStore.Tag,
            CheckBox
        >()

    private var outputProfile =
        DevelopUgandaBrandMetadataStore
            .OUTPUT_VERIFIED_MASTER

    private val ink =
        DevelopUgandaFivemods8Theme.surface

    private val panel =
        DevelopUgandaFivemods8Theme.surface

    private val white =
        DevelopUgandaFivemods8Theme.content

    private val muted =
        DevelopUgandaFivemods8Theme.contentDim

    private val cyan =
        DevelopUgandaFivemods8Theme.accent

    private val green =
        DevelopUgandaFivemods8Theme.contentDim

    private val gold =
        DevelopUgandaFivemods8Theme.accent

    private val amber =
        DevelopUgandaFivemods8Theme.accent

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        buildUi()
        loadCurrent()
    }

    private fun buildUi() {
        val root =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    ink
                )
            }

        val top =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(18),
                    dp(14),
                    dp(10)
                )

                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surface
                )
            }

        top.addView(
            label(
                "develop.uganda • V228",
                20f,
                gold,
                true
            )
        )

        top.addView(
            label(
                "BRAND & METADATA STUDIO • CONTROLS FUTURE RECORDED BURN-IN",
                8f,
                green,
                true
            )
        )

        root.addView(
            top
        )

        val scroll =
            ScrollView(
                this
            )

        val page =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    dp(8),
                    dp(12),
                    dp(24)
                )
            }

        page.addView(
            section(
                "BRAND IDENTITY"
            )
        )

        displayNameEdit =
            input(
                "Main display name"
            )

        organizationEdit =
            input(
                "Organization / newsroom (optional)"
            )

        page.addView(
            displayNameEdit
        )

        page.addView(
            organizationEdit
        )

        val reporterButton =
            actionButton(
                "USE SAVED REPORTER NAME",
                cyan
            ) {
                val reporter =
                    duSharedPreferences(
                        "develop_uganda_reporter",
                        Context.MODE_PRIVATE
                    )
                        .getString(
                            "reporter_name",
                            ""
                        )
                        ?.trim()
                        .orEmpty()

                if (
                    reporter.isBlank()
                ) {
                    toast(
                        "No saved reporter name is available"
                    )
                } else {
                    displayNameEdit.setText(
                        reporter
                    )

                    refreshPreview()
                }
            }

        page.addView(
            reporterButton,
            buttonParams()
        )

        appCreditCheck =
            CheckBox(
                this
            ).apply {
                text =
                    "Small “Recorded with develop.uganda • V228” credit when the main name is custom"

                textSize =
                    9f

                setTextColor(
                    white
                )

                buttonTintList =
                    android.content.res.ColorStateList.valueOf(
                        green
                    )

                setPadding(
                    dp(3),
                    dp(7),
                    dp(3),
                    dp(7)
                )

                setOnCheckedChangeListener {
                        _,
                        _ ->
                    refreshPreview()
                }
            }

        page.addView(
            appCreditCheck
        )

        page.addView(
            section(
                "OUTPUT PROFILE"
            )
        )

        outputProfileView =
            label(
                "",
                10f,
                white,
                true
            )

        page.addView(
            outputProfileView
        )

        val outputRow =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        outputRow.addView(
            actionButton(
                "VERIFIED MASTER",
                green
            ) {
                saveIdentityOnly()

                DevelopUgandaBrandMetadataStore
                    .applyOutputProfile(
                        this,
                        DevelopUgandaBrandMetadataStore
                            .OUTPUT_VERIFIED_MASTER
                    )

                loadCurrent()
            },
            weight()
        )

        outputRow.addView(
            actionButton(
                "PUBLIC / SOCIAL",
                cyan
            ) {
                saveIdentityOnly()

                DevelopUgandaBrandMetadataStore
                    .applyOutputProfile(
                        this,
                        DevelopUgandaBrandMetadataStore
                            .OUTPUT_PUBLIC_SOCIAL
                    )

                loadCurrent()
            },
            weight(
                dp(6)
            )
        )

        page.addView(
            outputRow
        )

        page.addView(
            label(
                "This changes the tags burned into NEW recordings. It does not pretend to remove telemetry that was already burned into an older video. Full Story Package metadata can still be preserved underneath.",
                8f,
                muted,
                false
            ).apply {
                setPadding(
                    0,
                    dp(6),
                    0,
                    dp(4)
                )
            }
        )

        page.addView(
            section(
                "OVERLAY PRESETS"
            )
        )

        presetView =
            label(
                "",
                9f,
                gold,
                true
            )

        page.addView(
            presetView
        )

        val presetHost =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL
            }

        DevelopUgandaBrandMetadataStore
            .presetLabels
            .filter {
                it !=
                    DevelopUgandaBrandMetadataStore
                        .PRESET_CUSTOM
            }
            .chunked(
                2
            )
            .forEach {
                    pair ->
                val row =
                    LinearLayout(
                        this
                    ).apply {
                        orientation =
                            LinearLayout.HORIZONTAL
                    }

                pair.forEachIndexed {
                        index,
                        preset ->
                    row.addView(
                        actionButton(
                            preset,
                            if (
                                preset ==
                                    DevelopUgandaBrandMetadataStore
                                        .PRESET_SOCIAL_CLEAN
                            ) {
                                cyan
                            } else {
                                gold
                            }
                        ) {
                            saveIdentityOnly()

                            DevelopUgandaBrandMetadataStore
                                .applyPreset(
                                    this,
                                    preset
                                )

                            loadCurrent()
                        },
                        weight(
                            if (
                                index >
                                    0
                            ) {
                                dp(6)
                            } else {
                                0
                            }
                        )
                    )
                }

                presetHost.addView(
                    row
                )
            }

        page.addView(
            presetHost
        )

        page.addView(
            section(
                "VISIBLE SAVED-VIDEO TAGS"
            )
        )

        DevelopUgandaBrandMetadataStore
            .Tag
            .entries
            .forEach {
                    tag ->
                val check =
                    CheckBox(
                        this
                    ).apply {
                        text =
                            tag.label

                        textSize =
                            9.3f

                        setTextColor(
                            white
                        )

                        buttonTintList =
                            android.content.res.ColorStateList.valueOf(
                                green
                            )

                        setPadding(
                            dp(3),
                            dp(3),
                            dp(3),
                            dp(3)
                        )

                        setOnCheckedChangeListener {
                                _,
                                _ ->
                            refreshPreview()
                        }
                    }

                checks[
                    tag
                ] =
                    check

                page.addView(
                    check
                )
            }

        val saveButton =
            actionButton(
                "SAVE CUSTOM BRAND + TAGS",
                green
            ) {
                val selected =
                    checks
                        .filterValues {
                            it.isChecked
                        }
                        .keys
                        .toSet()

                DevelopUgandaBrandMetadataStore
                    .saveCustom(
                        this,
                        displayNameEdit
                            .text
                            .toString(),
                        organizationEdit
                            .text
                            .toString(),
                        appCreditCheck.isChecked,
                        outputProfile,
                        selected
                    )

                toast(
                    "V228 brand and metadata settings saved"
                )

                loadCurrent()
            }

        page.addView(
            saveButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            ).apply {
                topMargin =
                    dp(10)
            }
        )

        page.addView(
            section(
                "RECORDED-VIDEO PREVIEW"
            )
        )

        val previewCard =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(12),
                    dp(12),
                    dp(12),
                    dp(12)
                )

                background =
                    rounded(
                        DevelopUgandaFivemods8Theme.surfaceRaised,
                        cyan
                    )
            }

        previewTitleView =
            label(
                "",
                17f,
                white,
                true
            )

        previewRowsView =
            label(
                "",
                8f,
                muted,
                false
            ).apply {
                setPadding(
                    0,
                    dp(6),
                    0,
                    0
                )
            }

        previewCard.addView(
            previewTitleView
        )

        previewCard.addView(
            previewRowsView
        )

        page.addView(
            previewCard
        )

        page.addView(
            label(
                "PRIVACY: if exact GPS is enabled, V228 warns before immediate post-record sharing. PUBLIC / SOCIAL defaults to a cleaner tag set. VERIFIED MASTER defaults to the full V227 telemetry experience.",
                8f,
                amber,
                true
            ).apply {
                setPadding(
                    0,
                    dp(8),
                    0,
                    0
                )
            }
        )

        scroll.addView(
            page
        )

        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(
            root
        )

        displayNameEdit.setOnFocusChangeListener {
                _,
                hasFocus ->
            if (
                !hasFocus
            ) {
                refreshPreview()
            }
        }

        organizationEdit.setOnFocusChangeListener {
                _,
                hasFocus ->
            if (
                !hasFocus
            ) {
                refreshPreview()
            }
        }
    }

    private fun loadCurrent() {
        val current =
            DevelopUgandaBrandMetadataStore
                .snapshot(
                    this
                )

        displayNameEdit.setText(
            current.displayName
        )

        organizationEdit.setText(
            current.organization
        )

        appCreditCheck.isChecked =
            current.appCredit

        outputProfile =
            current.outputProfile

        outputProfileView.text =
            "ACTIVE OUTPUT • ${current.outputProfile}"

        presetView.text =
            "ACTIVE PRESET • ${current.preset}"

        checks.forEach {
                tag,
                check ->
            check.isChecked =
                current.show(
                    tag
                )
        }

        refreshPreview()
    }

    private fun saveIdentityOnly() {
        val current =
            DevelopUgandaBrandMetadataStore
                .snapshot(
                    this
                )

        DevelopUgandaBrandMetadataStore
            .saveCustom(
                this,
                displayNameEdit
                    .text
                    .toString(),
                organizationEdit
                    .text
                    .toString(),
                appCreditCheck.isChecked,
                current.outputProfile,
                current.enabled
            )
    }

    private fun refreshPreview() {
        if (
            !::previewTitleView.isInitialized
        ) {
            return
        }

        val display =
            displayNameEdit
                .text
                .toString()
                .trim()
                .ifBlank {
                    "develop.uganda"
                }

        val org =
            organizationEdit
                .text
                .toString()
                .trim()

        val brandVisible =
            checks[
                DevelopUgandaBrandMetadataStore
                    .Tag
                    .BRAND
                ]
                ?.isChecked
                ?: true

        previewTitleView.text =
            if (
                brandVisible
            ) {
                if (
                    org.isBlank()
                ) {
                    display
                } else {
                    "$display\n$org"
                }
            } else {
                "BRAND HIDDEN"
            }

        val rows =
            checks
                .filterValues {
                    it.isChecked
                }
                .keys
                .map {
                    it.label
                }

        previewRowsView.text =
            buildString {
                append(
                    "V228 SAVED BURN-IN\n"
                )

                if (
                    rows.isEmpty()
                ) {
                    append(
                        "No optional metadata tags selected"
                    )
                } else {
                    append(
                        rows.joinToString(
                            " • "
                        )
                    )
                }

                if (
                    appCreditCheck.isChecked &&
                    !display.equals(
                        "develop.uganda",
                        ignoreCase =
                            true
                    )
                ) {
                    append(
                        "\nRecorded with develop.uganda • V228"
                    )
                }
            }
    }

    private fun input(
        hintValue: String
    ): EditText =
        EditText(
            this
        ).apply {
            hint =
                hintValue

            textSize =
                11f

            setTextColor(
                white
            )

            setHintTextColor(
                muted
            )

            setSingleLine(
                true
            )

            setPadding(
                dp(10),
                0,
                dp(10),
                0
            )

            background =
                rounded(
                    DevelopUgandaFivemods8Theme.surface,
                    cyan
                )
        }

    private fun section(
        value: String
    ): TextView =
        label(
            value,
            11f,
            gold,
            true
        ).apply {
            setPadding(
                0,
                dp(12),
                0,
                dp(6)
            )
        }

    private fun actionButton(
        value: String,
        accent: Int,
        action: () -> Unit
    ): Button =
        Button(
            this
        ).apply {
            text =
                value

            textSize =
                8f

            isAllCaps =
                false

            setTextColor(
                white
            )

            typeface =
                Typeface.DEFAULT_BOLD

            background =
                rounded(
                    DevelopUgandaFivemods8Theme.surfaceRaised,
                    accent
                )

            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "BRAND / METADATA BUTTON") { action.invoke() }
            }
        }

    private fun label(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean
    ): TextView =
        TextView(
            this
        ).apply {
            text =
                value

            textSize =
                sp

            setTextColor(
                color
            )

            typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    if (
                        bold
                    ) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
        }

    private fun rounded(
        fill: Int,
        stroke: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius =
                dp(14)
                    .toFloat()

            setColor(
                fill
            )

            setStroke(
                dp(1),
                stroke
            )
        }

    private fun buttonParams() =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(44)
        ).apply {
            topMargin =
                dp(6)
        }

    private fun weight(
        marginStart: Int = 0
    ) =
        LinearLayout.LayoutParams(
            0,
            dp(44),
            1f
        ).apply {
            this.marginStart =
                marginStart
        }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources.displayMetrics
                    .density
            ).roundToInt()

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
