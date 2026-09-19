package com.sentongoharuna.pulse

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class DevelopUgandaColorStudioActivity : AppCompatActivity() {

    companion object {
        // Keep the established extras so REPORT/LIVE integration does not need rewiring.
        const val EXTRA_SCOPE = "v230_color_scope"
        const val EXTRA_HINT = "v230_color_hint"
    }

    private val ink = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val green = DevelopUgandaFivemods8Theme.accent
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val violet = DevelopUgandaFivemods8Theme.contentDim
    private val amber = DevelopUgandaFivemods8Theme.accent

    private lateinit var activeView: TextView
    private lateinit var strengthView: TextView
    private lateinit var monitorCheck: CheckBox
    private lateinit var strengthSeek: SeekBar
    private lateinit var profileHost: LinearLayout
    private lateinit var paletteCardHost: LinearLayout
    private var loading = false

    private val scope: String by lazy {
        intent.getStringExtra(EXTRA_SCOPE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "GLOBAL"
    }

    private val hint: String by lazy {
        intent.getStringExtra(EXTRA_HINT)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "GENERAL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
            setPadding(dp(12), dp(18), dp(12), dp(12))
        }

        root.addView(label("develop.uganda • V235", 20f, violet, true))
        root.addView(
            label(
                "UGANDA SCENE COLOR LAB + LIVE GRADE • 5-COLOR LUT TUNER • REAL 17³ MASTER",
                8f,
                green,
                true
            ).apply { setPadding(0, dp(3), 0, dp(8)) }
        )

        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(24))
        }

        page.addView(card().apply {
            addView(label("ACTIVE COLOR", 11f, white, true))
            addView(label("SCOPE • $scope", 8f, cyan, true))
            addView(label("AUTO HINT • $hint", 8f, muted, false))
            activeView = label("", 11f, violet, true).apply {
                setPadding(0, dp(8), 0, 0)
            }
            addView(activeView)
        })

        page.addView(card().apply {
            addView(label("OUTPUT ARCHITECTURE", 11f, white, true))
            addView(
                label(
                    "ORIGINAL MP4 • NEVER REPLACED\nCOLOR_MASTER.mp4 • V233 tuned 17³ LUT + H.264/AAC re-encode\nEACH LUT • five independent palette amounts saved per camera scope\n100% • authored V230 color • 0% reduces it • 200% pushes it harder",
                    8f,
                    muted,
                    false
                ).apply { setPadding(0, dp(5), 0, 0) }
            )
        })

        page.addView(section("MASTER STRENGTH"))
        strengthView = label("", 10f, white, true)
        page.addView(strengthView)

        strengthSeek = SeekBar(this).apply {
            max = 100
            setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        strengthView.text = "LUT MASTER STRENGTH • $progress%"
                        if (fromUser) {
                            DevelopUgandaColorEngine.setStrength(
                                this@DevelopUgandaColorStudioActivity,
                                scope,
                                progress
                            )
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        refreshActiveText()
                    }
                }
            )
        }
        page.addView(strengthSeek)

        page.addView(section("INDIVIDUAL LUT COLORS"))
        paletteCardHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(paletteCardHost)

        page.addView(section("UGANDA SCENE LUTS"))
        profileHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        page.addView(profileHost)

        monitorCheck = CheckBox(this).apply {
            text = "LIVE CAMERA GRADE MONITOR"
            textSize = 9f
            setTextColor(white)
            buttonTintList = android.content.res.ColorStateList.valueOf(green)
            setOnCheckedChangeListener { _, checked ->
                if (loading) return@setOnCheckedChangeListener
                DevelopUgandaColorEngine.setMonitorEnabled(
                    this@DevelopUgandaColorStudioActivity,
                    checked
                )
                toast(
                    if (checked) {
                        "Live grade monitor ON • camera screen follows your tuning • exported Color Master uses the full tuned 3D LUT"
                    } else {
                        "Live grade monitor OFF • original camera preview shown"
                    }
                )
            }
        }
        page.addView(monitorCheck)

        page.addView(
            label(
                "V233 names the looks after familiar Ugandan scenes. The numbers identify different color recipes, so KAMPALA NIGHT 01 and 02 can stay recognizably related while rendering differently.",
                8f,
                amber,
                true
            ).apply { setPadding(0, dp(9), 0, 0) }
        )

        scroll.addView(page)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        setContentView(root)
    }

    private fun refresh() {
        loading = true
        val resolved = DevelopUgandaColorEngine.resolve(this, scope, hint)

        strengthSeek.progress = resolved.strength.coerceIn(0, 100)
        strengthView.text = "LUT MASTER STRENGTH • ${resolved.strength}%"
        monitorCheck.isChecked = DevelopUgandaColorEngine.monitorEnabled(this)
        refreshActiveText()
        rebuildPaletteTuner(resolved.profile?.id)
        rebuildProfiles()
        loading = false
    }

    private fun refreshActiveText() {
        val resolved = DevelopUgandaColorEngine.resolve(this, scope, hint)
        val tuned = resolved.profile?.let {
            val tuning = DevelopUgandaColorTuner.load(this, scope, it.id)
            if (tuning.isDefault()) "BASE PALETTE" else "CUSTOM PALETTE"
        } ?: "ORIGINAL"
        activeView.text =
            "ACTIVE • ${resolved.statusLabel()} • ${resolved.strength}% • $tuned"
    }

    private fun rebuildPaletteTuner(profileId: String?) {
        paletteCardHost.removeAllViews()
        if (profileId == null) {
            paletteCardHost.addView(card().apply {
                addView(label("ORIGINAL • NO LUT COLORS", 10f, white, true))
                addView(label("Choose AUTO or a Uganda Scene LUT to tune its individual colors.", 8f, muted, false))
            })
            return
        }

        val style = DevelopUgandaColorTuner.style(profileId) ?: return
        val tuning = DevelopUgandaColorTuner.load(this, scope, profileId)

        val box = card()
        box.addView(label(style.displayName, 13f, white, true))
        box.addView(label(style.bestFor, 8f, muted, false).apply { setPadding(0, dp(3), 0, dp(7)) })
        box.addView(label("COLOR ROW", 8f, amber, true))

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(5), 0, dp(7))
        }

        style.slots.forEachIndexed { index, slot ->
            val swatch = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(2), 0, dp(2), 0)
            }
            swatch.addView(TextView(this).apply {
                text = ""
                background = rounded(Color.parseColor(slot.hex), white, 8)
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)))
            swatch.addView(label("${index + 1}", 7f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(swatch, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        box.addView(row)

        style.slots.forEachIndexed { index, slot ->
            val title = label("", 8f, white, true)
            fun setTitle(value: Int) {
                title.text = "${index + 1}. ${slot.name} • ${slot.hex} • $value%"
            }
            setTitle(tuning.value(index))
            box.addView(title)

            val seek = SeekBar(this).apply {
                max = 200
                progress = tuning.value(index)
                progressTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(slot.hex))
                thumbTintList = android.content.res.ColorStateList.valueOf(Color.parseColor(slot.hex))
                setOnSeekBarChangeListener(
                    object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(
                            seekBar: SeekBar?,
                            progress: Int,
                            fromUser: Boolean
                        ) {
                            setTitle(progress)
                            if (fromUser) {
                                DevelopUgandaColorTuner.set(
                                    this@DevelopUgandaColorStudioActivity,
                                    scope,
                                    profileId,
                                    index,
                                    progress
                                )
                                refreshActiveText()
                            }
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) { }
                        override fun onStopTrackingTouch(seekBar: SeekBar?) { }
                    }
                )
            }
            box.addView(seek)
        }

        box.addView(Button(this).apply {
            text = "RESET ${style.displayName} • ALL COLORS 100%"
            textSize = 8f
            isAllCaps = false
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(DevelopUgandaFivemods8Theme.surfaceRaised, amber)
            setOnClickListener {
                DevelopUgandaColorTuner.reset(
                    this@DevelopUgandaColorStudioActivity,
                    scope,
                    profileId
                )
                rebuildPaletteTuner(profileId)
                refreshActiveText()
                toast("${style.displayName} palette reset")
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply {
            topMargin = dp(7)
        })

        paletteCardHost.addView(box)
    }

    private fun rebuildProfiles() {
        profileHost.removeAllViews()
        addProfileButton(
            "AUTO • CAMERA / SCENE",
            "The scene hint chooses the most suitable Uganda Scene LUT automatically.",
            0,
            null
        )
        addProfileButton(
            "ORIGINAL • NO COLOR MASTER",
            "No grade is exported. The original CameraX recording remains the only master.",
            1,
            null
        )

        var lastFamily = ""
        DevelopUgandaColorEngine.profiles.forEachIndexed { index, profile ->
            val style = DevelopUgandaColorTuner.style(profile.id)
            val family = style?.family ?: profile.family
            if (family != lastFamily) {
                lastFamily = family
                profileHost.addView(
                    label(family, 9f, amber, true).apply {
                        setPadding(dp(2), dp(12), dp(2), dp(2))
                    }
                )
            }
            addProfileButton(
                style?.displayName ?: profile.label,
                style?.bestFor ?: profile.purpose,
                index + 2,
                style
            )
        }
    }

    private fun addProfileButton(
        name: String,
        purpose: String,
        menuIndex: Int,
        style: DevelopUgandaColorTuner.SceneStyle?
    ) {
        val selected = DevelopUgandaColorEngine.selectedMenuIndex(this, scope) == menuIndex
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = rounded(panel, if (selected) green else DevelopUgandaFivemods8Theme.outline)
        }

        val button = Button(this).apply {
            text = name
            textSize = 9f
            isAllCaps = false
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(DevelopUgandaFivemods8Theme.surfaceRaised, if (selected) green else cyan)
            setOnClickListener {
                DevelopUgandaColorEngine.setSelectedMenuIndex(
                    this@DevelopUgandaColorStudioActivity,
                    scope,
                    menuIndex
                )
                refresh()
                toast("V233 COLOR • $name")
            }
        }

        box.addView(button, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(43)))
        box.addView(label(purpose, 7.5f, muted, false).apply { setPadding(dp(2), dp(5), dp(2), 0) })

        if (style != null) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(1), dp(5), dp(1), 0)
            }
            style.slots.forEach { slot ->
                row.addView(TextView(this).apply {
                    background = rounded(Color.parseColor(slot.hex), DevelopUgandaFivemods8Theme.outline, 6)
                }, LinearLayout.LayoutParams(0, dp(12), 1f).apply {
                    marginStart = dp(1)
                    marginEnd = dp(1)
                })
            }
            box.addView(row)
        }

        profileHost.addView(
            box,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(11), dp(10), dp(11), dp(10))
        background = rounded(panel, DevelopUgandaFivemods8Theme.outline)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(7) }
    }

    private fun section(value: String): TextView =
        label(value, 11f, violet, true).apply {
            setPadding(0, dp(13), 0, dp(5))
        }

    private fun label(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean
    ): TextView = TextView(this).apply {
        text = value
        textSize = sp
        setTextColor(color)
        typeface = Typeface.create(
            Typeface.DEFAULT,
            if (bold) Typeface.BOLD else Typeface.NORMAL
        )
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int = 15): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(radiusDp).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun toast(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    }
}
