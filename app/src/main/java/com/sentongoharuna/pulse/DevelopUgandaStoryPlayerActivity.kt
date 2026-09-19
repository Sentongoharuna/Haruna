package com.sentongoharuna.pulse

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlin.math.roundToInt

class DevelopUgandaStoryPlayerActivity :
    AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_ID =
            "story_package_id"

        const val EXTRA_DIRECT_URI =
            "direct_video_uri"

        const val EXTRA_DIRECT_LABEL =
            "direct_video_label"

        const val EXTRA_TAKE_ID =
            "direct_take_id"

        const val EXTRA_START_MS =
            "direct_start_ms"
    }

    private var player: ExoPlayer? =
        null

    private lateinit var playerView: PlayerView
    private lateinit var statusView: TextView
    private lateinit var reviewPanel: LinearLayout
    private var startPositionMs = 0L

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        val root =
            FrameLayout(
                this
            ).apply {
                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surface
                )
            }

        playerView =
            PlayerView(
                this
            ).apply {
                useController =
                    true

                resizeMode =
                    AspectRatioFrameLayout.RESIZE_MODE_FIT

                setShowBuffering(
                    PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                )

                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surface
                )
            }

        root.addView(
            playerView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        statusView =
            TextView(
                this
            ).apply {
                text =
                    "develop.uganda • STORY PLAYER"

                textSize =
                    10f

                setTextColor(
                    DevelopUgandaFivemods8Theme.content
                )

                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surfaceScrim(153)
                )

                setPadding(
                    dp(12),
                    dp(8),
                    dp(12),
                    dp(8)
                )
            }

        root.addView(
            statusView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity =
                    Gravity.TOP
            }
        )

        reviewPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceScrim(204))
            visibility = android.view.View.GONE
            contentDescription = "Take marks and reviewed transcript timeline"
        }
        root.addView(
            reviewPanel,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.BOTTOM },
        )

        setContentView(
            root
        )

        val directUri =
            intent.getStringExtra(
                EXTRA_DIRECT_URI
            )
                ?.takeIf {
                    it.isNotBlank()
                }
                ?.let {
                    Uri.parse(
                        it
                    )
                }

        if (
            directUri !=
                null
        ) {
            startPositionMs = intent.getLongExtra(EXTRA_START_MS, 0L).coerceAtLeast(0L)
            val takeId = intent.getStringExtra(EXTRA_TAKE_ID).orEmpty()
            buildReviewTimeline(takeId, directUri)
            playUri(
                directUri,
                intent.getStringExtra(
                    EXTRA_DIRECT_LABEL
                ) ?: "RECORDED VIDEO"
            )

            return
        }

        val packageId =
            intent.getStringExtra(
                EXTRA_PACKAGE_ID
            )
                ?.trim()
                .orEmpty()

        if (
            packageId.isBlank()
        ) {
            finish()
            return
        }

        val resolved =
            DevelopUgandaStoryPackager
                .resolvePlayableVideo(
                    this,
                    packageId
                )

        if (
            resolved ==
                null
        ) {
            statusView.text =
                "VIDEO NOT FOUND • package $packageId"

            return
        }

        playUri(
            resolved.uri,
            resolved.label
        )
    }

    private fun playUri(
        uri: Uri,
        label: String
    ) {
        val exo =
            ExoPlayer.Builder(
                this
            )
                .build()

        player =
            exo

        playerView.player =
            exo

        exo.addListener(
            object :
                Player.Listener {

                override fun onPlaybackStateChanged(
                    playbackState: Int
                ) {
                    statusView.text =
                        when (
                            playbackState
                        ) {
                            Player.STATE_BUFFERING ->
                                "BUFFERING • $label"

                            Player.STATE_READY ->
                                "PLAYING • $label"

                            Player.STATE_ENDED ->
                                "ENDED • $label"

                            else ->
                                "develop.uganda • $label"
                        }
                }

                override fun onPlayerError(
                    error: androidx.media3.common.PlaybackException
                ) {
                    statusView.text =
                        "PLAYBACK ERROR • ${error.errorCodeName}"
                }
            }
        )

        exo.setMediaItem(
            MediaItem.fromUri(
                uri
            )
        )

        exo.prepare()
        if (startPositionMs > 0L) exo.seekTo(startPositionMs)
        exo.playWhenReady =
            true
    }

    private fun buildReviewTimeline(takeId: String, uri: Uri) {
        val marks = if (takeId.isBlank()) emptyList() else DevelopUgandaTakeMarks.forClip(this, takeId)
        val transcript = DevelopUgandaTranscriptArchive.all(this).firstOrNull { entry ->
            entry.videoUri == uri || (takeId.isNotBlank() && (entry.takeId == takeId || takeId.startsWith(entry.takeId)))
        }
        if (marks.isEmpty() && transcript == null) return
        reviewPanel.visibility = android.view.View.VISIBLE
        reviewPanel.addView(reviewLabel("REVIEW TIMELINE • TAP TO JUMP", 11f, DevelopUgandaFivemods8Theme.contentDim))
        if (marks.isNotEmpty()) {
            reviewPanel.addView(reviewRow(marks.mapIndexed { index, mark ->
                "MARK ${index + 1} • ${DevelopUgandaTakeMarks.format(mark.elapsedMs)}" to mark.elapsedMs
            }))
        }
        transcript?.let { entry ->
            val pace = entry.paceWordsPerMinute?.let {
                String.format(java.util.Locale.US, "PACE %.1f WPM • DERIVED", it)
            } ?: "PACE • UNKNOWN"
            reviewPanel.addView(reviewLabel("$pace • PAUSES ${entry.answerPausesMs.size}", 11f, DevelopUgandaFivemods8Theme.contentDim))
            reviewPanel.addView(reviewRow(entry.cues.map { cue ->
                "${format(cue.startMs)} • ${cue.text.take(34)}" to cue.startMs
            }))
        }
    }

    private fun reviewRow(items: List<Pair<String, Long>>): HorizontalScrollView =
        HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(
                LinearLayout(this@DevelopUgandaStoryPlayerActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    items.forEach { (text, atMs) ->
                        addView(
                            reviewLabel(text, 11f, DevelopUgandaFivemods8Theme.content).apply {
                                gravity = Gravity.CENTER
                                isClickable = true
                                isFocusable = true
                                setPadding(dp(12), 0, dp(12), 0)
                                DevelopUgandaBroadcastPresentation.styleButton(
                                    this,
                                    DevelopUgandaButtonWeight.SECONDARY,
                                )
                                setOnClickListener {
                                    player?.seekTo(atMs)
                                    player?.play()
                                }
                            },
                            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, DevelopUgandaFivemods8Theme.touchMinimumPx).apply {
                                marginEnd = dp(8)
                                topMargin = dp(4)
                            },
                        )
                    }
                },
            )
        }

    private fun reviewLabel(value: String, step: Float, color: Int): TextView = TextView(this).apply {
        text = value
        setTextColor(color)
        typeface = android.graphics.Typeface.MONOSPACE
        DevelopUgandaFivemods8Theme.applyTypeScale(this, step)
        contentDescription = value
    }

    private fun format(ms: Long): String = String.format(
        java.util.Locale.US,
        "%02d:%02d.%03d",
        ms / 60_000L,
        (ms % 60_000L) / 1_000L,
        ms % 1_000L,
    )

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        try {
            playerView.player =
                null

            player?.release()
        } catch (_: Exception) {
        }

        player =
            null

        super.onDestroy()
    }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources.displayMetrics.density
            ).roundToInt()
}
