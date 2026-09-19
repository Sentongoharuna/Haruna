package com.sentongoharuna.pulse

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.roundToInt

class DevelopUgandaStoryPackagesActivity : AppCompatActivity() {

    private lateinit var host: LinearLayout

    private val ink = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val green = DevelopUgandaFivemods8Theme.accent
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val gold = DevelopUgandaFivemods8Theme.accent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        refreshPackages()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ink)
            setPadding(dp(12), dp(16), dp(12), dp(12))
        }

        root.addView(
            label(
                "develop.uganda • V259 MEDIA INTELLIGENCE",
                18f,
                gold,
                true
            )
        )

        root.addView(
            label(
                "ORIGINAL SAFE • STORY PACKAGES • BEST / KEEP / REVIEW TAKE FLAGS • MANIFEST • INTEGRITY • CAPTION • TRANSCRIPT",
                8f,
                muted,
                true
            ).apply {
                setPadding(0, dp(3), 0, dp(8))
            }
        )

        val refresh = actionButton(
            "SCAN RECORDINGS + REFRESH",
            cyan
        ) {
            refreshPackages()
        }

        root.addView(
            refresh,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
            )
        )

        val scroll = ScrollView(this)
        host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(20))
        }
        scroll.addView(host)

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

    private fun refreshPackages() {
        host.removeAllViews()

        val inbox =
            DevelopUgandaMediaInbox.findUnpackaged(
                this,
                24
            )

        if (
            inbox.isNotEmpty()
        ) {
            host.addView(
                cardText(
                    "NEW RECORDINGS INBOX • ${inbox.size}",
                    "These are develop.uganda videos found directly in Android MediaStore that do not yet have a V227 Story Package. PLAY and SHARE work immediately. BUILD PACKAGE creates the newsroom package."
                )
            )

            inbox.take(
                12
            ).forEach {
                    video ->
                val intakeCard =
                    LinearLayout(
                        this
                    ).apply {
                        orientation =
                            LinearLayout.VERTICAL

                        setPadding(
                            dp(10),
                            dp(10),
                            dp(10),
                            dp(10)
                        )

                        background =
                            rounded(
                                panel,
                                green
                            )
                    }

                intakeCard.addView(
                    label(
                        video.displayName,
                        10f,
                        white,
                        true
                    )
                )

                intakeCard.addView(
                    label(
                        "MEDIASTORE RECORDING • ${formatDuration(video.durationMs)} • ${formatBytes(video.sizeBytes)}\nNOT PACKAGED YET",
                        7.5f,
                        muted,
                        false
                    ).apply {
                        setPadding(
                            0,
                            dp(3),
                            0,
                            dp(6)
                        )
                    }
                )

                val mediaRow =
                    LinearLayout(
                        this
                    ).apply {
                        orientation =
                            LinearLayout.HORIZONTAL
                    }

                mediaRow.addView(
                    smallButton(
                        "PLAY",
                        green
                    ) {
                        playDirect(
                            video.uri,
                            video.displayName
                        )
                    },
                    weight()
                )

                mediaRow.addView(
                    smallButton(
                        "SHARE",
                        cyan
                    ) {
                        shareDirect(
                            video.uri
                        )
                    },
                    weight().apply {
                        marginStart =
                            dp(5)
                    }
                )

                intakeCard.addView(
                    mediaRow
                )

                val buildPackage =
                    actionButton(
                        "BUILD STORY PACKAGE",
                        gold
                    ) {
                        DevelopUgandaMediaInbox.packageVideo(
                            this,
                            video
                        )

                        toast(
                            "Building Story Package • original video stays safe"
                        )

                        host.postDelayed(
                            {
                                refreshPackages()
                            },
                            1800L
                        )
                    }

                intakeCard.addView(
                    buildPackage,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(44)
                    ).apply {
                        topMargin =
                            dp(5)
                    }
                )

                host.addView(
                    intakeCard,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin =
                            dp(7)
                    }
                )
            }

            host.addView(
                label(
                    "PACKAGED STORIES",
                    10f,
                    gold,
                    true
                ).apply {
                    setPadding(
                        0,
                        dp(14),
                        0,
                        dp(5)
                    )
                }
            )
        }

        val packages = DevelopUgandaStoryPackager.listRegistry(this)

        if (
            packages.isEmpty() &&
            inbox.isEmpty()
        ) {
            host.addView(
                cardText(
                    "NO DEVELOP.UGANDA VIDEOS FOUND",
                    "Record with a develop.uganda camera. New successful recordings package automatically after CameraX Finalize. This screen also scans MediaStore so older or missed develop.uganda recordings can be recovered into the newsroom."
                )
            )
            return
        }

        packages.forEach { entry ->
            val f12Identity = DevelopUgandaFivemods12Identity.forModeText("${entry.camera} ${entry.packageId}")
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(10), dp(10), dp(10))
                background = rounded(panel, cyan)
            }

            card.addView(
                label(
                    entry.title.ifBlank { entry.packageId },
                    12f,
                    white,
                    true
                )
            )

            card.addView(
                label(
                    f12Identity?.badge ?: "MODE UNKNOWN • IDENTITY UNAVAILABLE",
                    13f,
                    f12Identity?.accent ?: muted,
                    true,
                ).apply { setPadding(0, dp(3), 0, 0) }
            )

            card.addView(
                label(
                    "${entry.camera}\nPACKAGE • ${entry.packageId}\n${entry.state}\n${entry.createdUtc}",
                    8f,
                    muted,
                    false
                ).apply {
                    setPadding(0, dp(3), 0, dp(7))
                }
            )

            if (v259TakeFlagsEnabled()) {
                val flag = v259TakeFlag(entry.packageId)
                card.addView(
                    label("TAKE FLAG • $flag", 8f, if (flag == "BEST") gold else muted, true).apply {
                        setPadding(0, 0, 0, dp(5))
                    }
                )
                val flagRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                flagRow.addView(smallButton("BEST", gold) { v259SetTakeFlag(entry.packageId, "BEST"); refreshPackages() }, weight())
                flagRow.addView(smallButton("KEEP", green) { v259SetTakeFlag(entry.packageId, "KEEP"); refreshPackages() }, weight().apply { marginStart = dp(5) })
                flagRow.addView(smallButton("REVIEW", cyan) { v259SetTakeFlag(entry.packageId, "REVIEW"); refreshPackages() }, weight().apply { marginStart = dp(5) })
                card.addView(flagRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { bottomMargin = dp(5) })
            }

            val row1 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            row1.addView(
                smallButton("PLAY ORIGINAL", green) {
                    playOriginal(
                        entry.packageId
                    )
                },
                weight()
            )

            row1.addView(
                smallButton("SHARE ORIGINAL", cyan) {
                    shareOriginal(
                        entry.packageId
                    )
                },
                weight().apply { marginStart = dp(5) }
            )

            card.addView(row1)

            val row2 = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

            row2.addView(
                smallButton("CAPTION", gold) {
                    sharePackageFile(entry.packageId, "CAPTION_DRAFT.txt", "text/plain")
                },
                weight()
            )

            row2.addView(
                smallButton("MANIFEST", cyan) {
                    sharePackageFile(entry.packageId, "MANIFEST.json", "application/json")
                },
                weight().apply { marginStart = dp(5) }
            )

            card.addView(
                row2,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(42)
                ).apply {
                    topMargin = dp(5)
                }
            )

            val transcribe = actionButton(
                if (Build.VERSION.SDK_INT >= 33) {
                    "TRANSCRIBE SAVED VIDEO • ON-DEVICE IF AVAILABLE"
                } else {
                    "TRANSCRIPT REQUIRES ANDROID 13+"
                },
                green
            ) {
                if (Build.VERSION.SDK_INT < 33) {
                    toast("Android 13 / API 33+ required")
                } else {
                    DevelopUgandaStoryPackager.requestTranscript(
                        this,
                        entry.packageId
                    )
                    toast("Transcript request started • clear speech is required")
                }
            }

            transcribe.isEnabled = Build.VERSION.SDK_INT >= 33

            card.addView(
                transcribe,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(44)
                ).apply {
                    topMargin = dp(5)
                }
            )

            val transcriptStatus = DevelopUgandaStoryPackager.findPackageFile(
                this,
                entry.packageId,
                "TRANSCRIPT_STATUS.txt"
            )?.let {
                DevelopUgandaStoryPackager.readText(this, it)
            }

            if (!transcriptStatus.isNullOrBlank()) {
                card.addView(
                    label(
                        transcriptStatus,
                        7.5f,
                        muted,
                        false
                    ).apply {
                        setPadding(0, dp(6), 0, 0)
                    }
                )
            }

            host.addView(
                card,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(8)
                }
            )
        }
    }

    private fun v259Prefs() =
        duSharedPreferences("develop_uganda_v259_smart_shoot_media", MODE_PRIVATE)

    private fun v259TakeFlagsEnabled(): Boolean =
        v259Prefs().getBoolean("take_flags", true)

    private fun v259TakeFlag(packageId: String): String =
        v259Prefs().getString("take_flag_$packageId", "UNMARKED") ?: "UNMARKED"

    private fun v259SetTakeFlag(packageId: String, flag: String) {
        v259Prefs().edit().putString("take_flag_$packageId", flag).apply()
        toast("TAKE • $flag")
    }

    private fun playOriginal(
        packageId: String
    ) {
        val resolved =
            DevelopUgandaStoryPackager.resolvePlayableVideo(
                this,
                packageId
            )

        if (
            resolved == null
        ) {
            toast(
                "Original video is not readable from Gallery or the Story Package"
            )
            return
        }

        startActivity(
            Intent(
                this,
                DevelopUgandaStoryPlayerActivity::class.java
            ).apply {
                putExtra(
                    DevelopUgandaStoryPlayerActivity.EXTRA_PACKAGE_ID,
                    packageId
                )
            }
        )
    }

    private fun playDirect(
        uri: Uri,
        label: String
    ) {
        startActivity(
            Intent(
                this,
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
    }

    private fun shareOriginal(
        packageId: String
    ) {
        val resolved =
            DevelopUgandaStoryPackager.resolvePlayableVideo(
                this,
                packageId
            )

        if (
            resolved == null
        ) {
            toast(
                "Original video is not readable"
            )
            return
        }

        shareDirect(
            resolved.uri
        )
    }

    private fun shareDirect(
        uri: Uri
    ) {
        try {
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
                    "Share video"
                )
            )
        } catch (_: Exception) {
            toast(
                "Share is not available"
            )
        }
    }

    private fun formatDuration(
        durationMs: Long?
    ): String {
        if (
            durationMs == null ||
            durationMs <= 0L
        ) {
            return "DURATION --"
        }

        val totalSeconds =
            durationMs / 1000L

        val minutes =
            totalSeconds / 60L

        val seconds =
            totalSeconds % 60L

        return String.format(
            java.util.Locale.US,
            "%02d:%02d",
            minutes,
            seconds
        )
    }

    private fun formatBytes(
        bytes: Long?
    ): String {
        if (
            bytes == null ||
            bytes <= 0L
        ) {
            return "SIZE --"
        }

        val mb =
            bytes.toDouble() /
                (
                    1024.0 *
                        1024.0
                    )

        return if (
            mb >= 1024.0
        ) {
            String.format(
                java.util.Locale.US,
                "%.1f GB",
                mb / 1024.0
            )
        } else {
            String.format(
                java.util.Locale.US,
                "%.0f MB",
                mb
            )
        }
    }

    private fun openPackageFile(
        packageId: String,
        displayName: String,
        mime: String
    ) {
        val uri = DevelopUgandaStoryPackager.findPackageFile(
            this,
            packageId,
            displayName
        ) ?: run {
            toast("File not available in this package")
            return
        }

        if (uri.scheme == "file") {
            toast("Open/share from Story Packages requires Android 10+; the package still exists in the app external Downloads folder")
            return
        }

        try {
            startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mime)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            )
        } catch (_: Exception) {
            toast("No app is available to open this file")
        }
    }

    private fun sharePackageFile(
        packageId: String,
        displayName: String,
        mime: String
    ) {
        val uri = DevelopUgandaStoryPackager.findPackageFile(
            this,
            packageId,
            displayName
        ) ?: run {
            toast("File not available in this package")
            return
        }

        if (uri.scheme == "file") {
            toast("Open/share from Story Packages requires Android 10+; the package still exists in the app external Downloads folder")
            return
        }

        try {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = mime
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    },
                    "Share $displayName"
                )
            )
        } catch (_: Exception) {
            toast("Share is not available")
        }
    }

    private fun cardText(title: String, body: String): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(panel, cyan)
            addView(label(title, 12f, white, true))
            addView(
                label(body, 8.5f, muted, false).apply {
                    setPadding(0, dp(4), 0, 0)
                }
            )
        }

    private fun actionButton(
        value: String,
        accent: Int,
        action: () -> Unit
    ): Button =
        Button(this).apply {
            text = value
            textSize = 8f
            isAllCaps = false
            setTextColor(white)
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(DevelopUgandaFivemods8Theme.surfaceRaised, accent)
            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "STORY PACKAGES BUTTON") { action.invoke() }
            }
        }

    private fun smallButton(
        value: String,
        accent: Int,
        action: () -> Unit
    ): Button = actionButton(value, accent, action)

    private fun label(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean
    ): TextView =
        TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            typeface = Typeface.create(
                Typeface.DEFAULT,
                if (bold) Typeface.BOLD else Typeface.NORMAL
            )
        }

    private fun rounded(fill: Int, stroke: Int): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = dp(15).toFloat()
            setColor(fill)
            setStroke(dp(1), stroke)
        }

    private fun weight(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, dp(42), 1f)

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).roundToInt()

    private fun toast(value: String) {
        Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    }
}
