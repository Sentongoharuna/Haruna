package com.sentongoharuna.pulse

import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.effect.Brightness
import androidx.media3.effect.Presentation
import androidx.media3.transformer.AudioEncoderSettings
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
class DevelopUgandaEditorActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var statusView: TextView
    private lateinit var sourceView: TextView
    private lateinit var startSeek: SeekBar
    private lateinit var endSeek: SeekBar
    private lateinit var startLabel: TextView
    private lateinit var endLabel: TextView
    private lateinit var previewCutButton: Button
    private lateinit var assemblyView: TextView

    private var player: ExoPlayer? = null
    private var transformer: Transformer? = null
    private var assemblyExporter: DevelopUgandaFivemods9DualOutputExporter? = null

    private var sourceUri: Uri? = null
    private var exportedUri: Uri? = null
    private var socialMasterUri: Uri? = null
    private var socialMasterLabel: String = ""
    private var durationMs = 0L
    private var previewingCut = false
    private var firstFrameRendered = false
    private val assemblyClips = mutableListOf<AssemblySelection>()
    private var assemblySelectedIndex = -1

    private data class AssemblySelection(
        val uri: Uri,
        val displayName: String,
        val mode: String,
        val durationMs: Long,
        val trimStartMs: Long,
        val trimEndMs: Long,
        val markCount: Int,
    )

    private val galleryPicker =
        registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                loadVideo(uri, "GALLERY")
            }
        }

    private val filePicker =
        registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                loadVideo(uri, "FILES")
            }
        }

    private val assemblyPicker =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            uris.forEach { uri ->
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                addAssemblyClip(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = DevelopUgandaFivemods8Theme.surface
        window.navigationBarColor = DevelopUgandaFivemods8Theme.surface

        buildUi()
        buildPlayer()

        val directUri =
            intent.getStringExtra("develop_uganda_edit_uri")
                ?.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }

        if (directUri != null) {
            loadVideo(directUri, "RECENT CLIP")
        } else {
            statusView.text =
                "Choose a clip • edit it or create a TikTok / Reels master • V221"
        }
    }

    override fun onDestroy() {
        transformer?.cancel()
        transformer = null
        assemblyExporter?.cancel()
        assemblyExporter = null

        playerView.player = null
        player?.release()
        player = null

        super.onDestroy()
    }

    private fun buildUi() {
        val root =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
            }

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars =
                insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                )

            view.setPadding(
                dp(12),
                bars.top + dp(8),
                dp(12),
                bars.bottom + dp(8)
            )

            insets
        }

        root.addView(
            label(
                "develop.uganda  EDITOR V221.1",
                21f,
                DevelopUgandaFivemods8Theme.accent,
                true
            )
        )

        root.addView(
            label(
                "OPTIONAL EDITOR SOCIAL MASTER • V222 SM CAMERA IS PRIMARY",
                9.5f,
                DevelopUgandaFivemods8Theme.contentDim,
                true
            ).apply {
                setPadding(0, dp(2), 0, dp(7))
            }
        )

        val pickerRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

        pickerRow.addView(
            action("GALLERY", DevelopUgandaFivemods8Theme.accent) {
                galleryPicker.launch("video/*")
            },
            weight()
        )

        pickerRow.addView(
            action("FILES", DevelopUgandaFivemods8Theme.accent) {
                filePicker.launch(arrayOf("video/*"))
            },
            weight()
        )

        pickerRow.addView(
            action("RECENT", DevelopUgandaFivemods8Theme.contentDim) {
                showRecentDevelopUgandaClips()
            },
            weight()
        )

        root.addView(
            pickerRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        val secondPickerRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }

        secondPickerRow.addView(
            action("LAST CLIP", DevelopUgandaFivemods8Theme.contentDim) {
                loadLatestDevelopUgandaClip()
            },
            weight()
        )

        secondPickerRow.addView(
            action("PLAY / PAUSE", DevelopUgandaFivemods8Theme.accent) {
                toggleSourcePlayback()
            },
            weight()
        )

        secondPickerRow.addView(
            action("RELOAD", DevelopUgandaFivemods8Theme.contentDim) {
                sourceUri?.let {
                    loadVideo(it, "RELOAD")
                } ?: toast("Choose a video first")
            },
            weight()
        )

        root.addView(
            secondPickerRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        )

        val assemblyControlRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        assemblyControlRow.addView(
            action("ADD CLIPS", DevelopUgandaFivemods8Theme.accent) {
                assemblyPicker.launch(arrayOf("video/*"))
            },
            weight(),
        )
        assemblyControlRow.addView(
            action("MOVE UP", DevelopUgandaFivemods8Theme.contentDim) { moveAssemblyClip(-1) },
            weight(),
        )
        assemblyControlRow.addView(
            action("MOVE DOWN", DevelopUgandaFivemods8Theme.contentDim) { moveAssemblyClip(1) },
            weight(),
        )
        assemblyControlRow.addView(
            action("REMOVE", DevelopUgandaFivemods8Theme.contentDim) { removeAssemblyClip() },
            weight(),
        )
        root.addView(
            assemblyControlRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)),
        )

        val assemblyActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        assemblyActionRow.addView(
            action("SELECT NEXT", DevelopUgandaFivemods8Theme.contentDim) { selectNextAssemblyClip() },
            weight(),
        )
        assemblyActionRow.addView(
            action("PREVIEW CLIP", DevelopUgandaFivemods8Theme.contentDim) { previewAssemblyClip() },
            weight(),
        )
        assemblyActionRow.addView(
            action("ASSEMBLE BRAND", DevelopUgandaFivemods8Theme.accent) { assembleBrand() },
            weight(),
        )
        root.addView(
            assemblyActionRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)),
        )

        assemblyView = label(
            "ASSEMBLY • ADD TWO OR MORE CLEAN CLIPS",
            11f,
            DevelopUgandaFivemods8Theme.contentDim,
            false,
        ).apply {
            typeface = Typeface.MONOSPACE
            maxLines = 4
            setPadding(0, dp(2), 0, dp(3))
        }
        root.addView(assemblyView)

        sourceView =
            label(
                "NO CLIP LOADED",
                9.5f,
                DevelopUgandaFivemods8Theme.contentDim,
                true
            ).apply {
                maxLines = 2
                setPadding(0, dp(5), 0, dp(5))
            }

        root.addView(sourceView)

        playerView =
            PlayerView(this).apply {
                setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
                useController = true
                controllerAutoShow = true
                controllerShowTimeoutMs = 0
                resizeMode =
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
            }

        root.addView(
            playerView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = dp(4)
                bottomMargin = dp(4)
            }
        )

        statusView =
            label(
                "Editor ready",
                9.5f,
                DevelopUgandaFivemods8Theme.accent,
                false
            ).apply {
                maxLines = 3
                setPadding(0, dp(5), 0, dp(5))
            }

        root.addView(statusView)

        startLabel =
            label(
                "IN 00:00",
                10f,
                DevelopUgandaFivemods8Theme.accent,
                true
            )
        root.addView(startLabel)

        startSeek =
            SeekBar(this).apply {
                max = 1000
                progress = 0
            }
        root.addView(startSeek)

        endLabel =
            label(
                "OUT 00:00",
                10f,
                DevelopUgandaFivemods8Theme.contentDim,
                true
            )
        root.addView(endLabel)

        endSeek =
            SeekBar(this).apply {
                max = 1000
                progress = 1000
            }
        root.addView(endSeek)

        startSeek.setOnSeekBarChangeListener(
            seekListener { progress ->
                if (progress >= endSeek.progress) {
                    startSeek.progress =
                        (endSeek.progress - 1).coerceAtLeast(0)
                }
                updateTrimLabels()
                seekPreview(currentStartMs())
            }
        )

        endSeek.setOnSeekBarChangeListener(
            seekListener { progress ->
                if (progress <= startSeek.progress) {
                    endSeek.progress =
                        (startSeek.progress + 1).coerceAtMost(1000)
                }
                updateTrimLabels()
                seekPreview(currentEndMs())
            }
        )

        val markRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

        markRow.addView(
            action("SET IN", DevelopUgandaFivemods8Theme.accent) {
                markIn()
            },
            weight()
        )

        markRow.addView(
            action("SET OUT", DevelopUgandaFivemods8Theme.contentDim) {
                markOut()
            },
            weight()
        )

        markRow.addView(
            action("RESET CUT", DevelopUgandaFivemods8Theme.contentDim) {
                resetCut()
            },
            weight()
        )

        root.addView(
            markRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        )

        val editRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

        previewCutButton =
            action(
                "PREVIEW CUT",
                DevelopUgandaFivemods8Theme.accent
            ) {
                previewCut()
            }

        editRow.addView(previewCutButton, weight())

        editRow.addView(
            action("SAVE CUT", DevelopUgandaFivemods8Theme.contentDim) {
                exportWithMedia3(includeAudio = true)
            },
            weight()
        )

        editRow.addView(
            action("MUTE + SAVE", DevelopUgandaFivemods8Theme.accent) {
                exportWithMedia3(includeAudio = false)
            },
            weight()
        )

        root.addView(
            editRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        )

        val outputRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }

        outputRow.addView(
            action("PLAY SAVED", DevelopUgandaFivemods8Theme.accent) {
                playSaved()
            },
            weight()
        )

        outputRow.addView(
            action("SHARE", DevelopUgandaFivemods8Theme.accent) {
                shareBestAvailable()
            },
            weight()
        )

        outputRow.addView(
            action("LOAD SAVED", DevelopUgandaFivemods8Theme.accent) {
                val uri = exportedUri
                if (uri == null) {
                    toast("Save a cut first")
                } else {
                    loadVideo(
                        uri,
                        "SAVED EDIT",
                        keepExport = true
                    )
                }
            },
            weight()
        )

        root.addView(
            outputRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
            )
        )

        root.addView(
            label(
                "SOCIAL MASTER • FORCED RE-ENCODE • 1080×1920 • H.264 • AAC • 30 FPS MAX • 2s KEYFRAMES",
                8.5f,
                DevelopUgandaFivemods8Theme.contentDim,
                true
            ).apply {
                setPadding(
                    0,
                    dp(7),
                    0,
                    dp(3)
                )
            }
        )

        val socialRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        socialRow.addView(
            action(
                "TIKTOK MASTER",
                DevelopUgandaFivemods8Theme.accent
            ) {
                exportSocialMaster(
                    platform =
                        "TIKTOK",
                    bitrate =
                        16_000_000
                )
            },
            weight()
        )

        socialRow.addView(
            action(
                "REELS MASTER",
                DevelopUgandaFivemods8Theme.accent
            ) {
                exportSocialMaster(
                    platform =
                        "REELS",
                    bitrate =
                        14_000_000
                )
            },
            weight()
        )

        socialRow.addView(
            action(
                "SHARE SOCIAL",
                DevelopUgandaFivemods8Theme.accent
            ) {
                shareSocialMaster()
            },
            weight()
        )

        root.addView(
            socialRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )

        DevelopUgandaBroadcastPresentation.decorate(root)
        setContentView(root)
    }

    private fun addAssemblyClip(uri: Uri) {
        if (assemblyClips.any { it.uri == uri }) {
            toast("Clip is already in the assembly")
            return
        }
        val name = displayName(uri)
        val duration = runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?: 0L
            } finally {
                retriever.release()
            }
        }.getOrDefault(0L)
        if (duration <= 0L) {
            toast("Clip duration could not be measured")
            return
        }
        val mode = DevelopUgandaV274MediaVaultStore.modeForMedia(this, uri.toString(), name)
            ?: DevelopUgandaFivemods12Identity.forModeText(name)?.page
        val marks = DevelopUgandaTakeMarks.forClip(this, name.substringBeforeLast('.'))
            .map { it.elapsedMs.coerceIn(0L, duration) }
            .distinct()
            .sorted()
        val start = marks.firstOrNull()?.coerceAtMost((duration - 1L).coerceAtLeast(0L)) ?: 0L
        val markedEnd = marks.drop(1).lastOrNull()
        val end = (markedEnd ?: duration).coerceIn(start + 1L, duration)
        assemblyClips += AssemblySelection(
            uri = uri,
            displayName = name,
            mode = mode?.name ?: "UNKNOWN",
            durationMs = duration,
            trimStartMs = start,
            trimEndMs = end,
            markCount = marks.size,
        )
        assemblySelectedIndex = assemblyClips.lastIndex
        refreshAssemblyView()
        if (assemblyClips.size == 1) loadVideo(uri, "ASSEMBLY CLIP")
    }

    private fun moveAssemblyClip(delta: Int) {
        if (assemblyClips.isEmpty()) {
            toast("Add clips first")
            return
        }
        if (assemblySelectedIndex !in assemblyClips.indices) assemblySelectedIndex = 0
        val target = (assemblySelectedIndex + delta).coerceIn(0, assemblyClips.lastIndex)
        if (target == assemblySelectedIndex) return
        val selected = assemblyClips.removeAt(assemblySelectedIndex)
        assemblyClips.add(target, selected)
        assemblySelectedIndex = target
        refreshAssemblyView()
    }

    private fun removeAssemblyClip() {
        if (assemblySelectedIndex !in assemblyClips.indices) {
            toast("Select a clip first")
            return
        }
        assemblyClips.removeAt(assemblySelectedIndex)
        assemblySelectedIndex = assemblySelectedIndex.coerceAtMost(assemblyClips.lastIndex)
        refreshAssemblyView()
    }

    private fun selectNextAssemblyClip() {
        if (assemblyClips.isEmpty()) {
            toast("Add clips first")
            return
        }
        assemblySelectedIndex = (assemblySelectedIndex + 1).mod(assemblyClips.size)
        refreshAssemblyView()
    }

    private fun previewAssemblyClip() {
        val clip = assemblyClips.getOrNull(assemblySelectedIndex) ?: run {
            toast("Select an assembly clip")
            return
        }
        loadVideo(clip.uri, "ASSEMBLY ${assemblySelectedIndex + 1}")
        playerView.post {
            durationMs = clip.durationMs
            startSeek.progress = positionToProgress(clip.trimStartMs)
            endSeek.progress = positionToProgress(clip.trimEndMs)
            updateTrimLabels()
            seekPreview(clip.trimStartMs)
        }
    }

    private fun refreshAssemblyView() {
        if (assemblyClips.isEmpty()) {
            assemblyView.text = "ASSEMBLY • ADD TWO OR MORE CLEAN CLIPS"
            return
        }
        assemblyView.text = assemblyClips.mapIndexed { index, clip ->
            val selected = if (index == assemblySelectedIndex) "▶" else " "
            val cuts = if (clip.markCount == 0) {
                "FULL • NO TAKE MARKS"
            } else {
                "MARK CUT ${formatTime(clip.trimStartMs)}–${formatTime(clip.trimEndMs)}"
            }
            "$selected${index + 1} ${clip.mode} • ${clip.displayName} • $cuts"
        }.joinToString("\n")
    }

    private fun assembleBrand() {
        if (assemblyClips.size < 2) {
            toast("Add at least two clean clips")
            return
        }
        val firstIdentity = DevelopUgandaFivemods12Identity.forModeText(assemblyClips.first().mode)
        if (firstIdentity == null || assemblyClips.any {
                DevelopUgandaFivemods12Identity.forModeText(it.mode) == null
            }) {
            statusView.text = "ASSEMBLY REFUSED • ONE OR MORE CLIP IDENTITIES ARE UNKNOWN"
            return
        }
        val assemblyId = DevelopUgandaFivemods12Identity.prefixedStem(
            firstIdentity.page,
            "ASSEMBLY_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}",
        )
        val exporter = DevelopUgandaFivemods9DualOutputExporter(
            context = this,
            recordingActive = { false },
            onFinished = {},
        )
        assemblyExporter?.cancel()
        assemblyExporter = exporter
        statusView.text = "ASSEMBLY • VERIFYING CLEAN SOURCES AND REVIEWED CAPTIONS"
        exporter.assembleBrand(
            assemblyClips.map { clip ->
                DevelopUgandaFivemods9DualOutputExporter.AssemblyClip(
                    cleanUri = clip.uri,
                    displayName = clip.displayName,
                    mode = clip.mode,
                    trimStartMs = clip.trimStartMs,
                    trimEndMs = clip.trimEndMs,
                )
            },
            assemblyId,
        ) { outcome ->
            assemblyExporter = null
            exportedUri = outcome.brandUri
            statusView.text = outcome.detail
            toast(if (outcome.complete) "Assembly BRAND saved" else "Assembly failed; clean clips preserved")
        }
    }

    private fun buildPlayer() {
        player =
            ExoPlayer.Builder(this)
                .build()
                .also { exo ->
                    playerView.player = exo

                    exo.addListener(
                        object : Player.Listener {
                            override fun onPlaybackStateChanged(
                                playbackState: Int
                            ) {
                                if (
                                    playbackState ==
                                    Player.STATE_READY
                                ) {
                                    val d = exo.duration

                                    durationMs =
                                        if (
                                            d != C.TIME_UNSET &&
                                            d > 0L
                                        ) {
                                            d
                                        } else {
                                            0L
                                        }

                                    updateTrimLabels()

                                    statusView.text =
                                        "READY • ${formatTime(durationMs)} • tap PLAY or set IN/OUT"
                                }
                            }

                            override fun onRenderedFirstFrame() {
                                firstFrameRendered = true

                                statusView.text =
                                    "VIDEO VISIBLE • READY • ${formatTime(durationMs)} • set IN/OUT"
                            }

                            override fun onPlayerError(
                                error: PlaybackException
                            ) {
                                statusView.text =
                                    "PLAYER ERROR • ${error.errorCodeName} • try FILES or another clip"

                                toast("Could not play this video")
                            }
                        }
                    )
                }
    }

    private fun loadVideo(
        uri: Uri,
        source: String,
        keepExport: Boolean = false
    ) {
        stopCutPreview()

        sourceUri = uri

        if (!keepExport) {
            exportedUri = null
        }

        durationMs = 0L
        firstFrameRendered = false
        startSeek.progress = 0
        endSeek.progress = 1000

        sourceView.text =
            "LOADING • $source • ${displayName(uri)}"

        statusView.text =
            "Opening with Media3…"

        val exo = player ?: return

        exo.stop()
        exo.clearMediaItems()
        exo.setMediaItem(
            MediaItem.fromUri(uri)
        )
        exo.prepare()
        exo.playWhenReady = false

        sourceView.text =
            "READY • $source • ${displayName(uri)}"

        playerView.showController()

        playerView.postDelayed(
            {
                if (
                    !firstFrameRendered &&
                    sourceUri == uri
                ) {
                    statusView.text =
                        "READY • tap PLAY • if picture stays black use FILES or RECENT"
                }
            },
            1200L
        )
    }

    private fun toggleSourcePlayback() {
        val exo = player

        if (
            sourceUri == null ||
            exo == null
        ) {
            toast("Choose a video first")
            return
        }

        if (exo.isPlaying) {
            exo.pause()
        } else {
            exo.play()
        }

        playerView.showController()
    }

    private fun showRecentDevelopUgandaClips() {
        val collection =
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        val names =
            mutableListOf<String>()

        val uris =
            mutableListOf<Uri>()

        try {
            contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DATE_ADDED
                ),
                DevelopUgandaFivemods12Identity.mediaStoreNameWhere(MediaStore.Video.Media.DISPLAY_NAME),
                DevelopUgandaFivemods12Identity.mediaStoreNameArgs(),
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media._ID
                    )

                val nameIndex =
                    cursor.getColumnIndexOrThrow(
                        MediaStore.Video.Media.DISPLAY_NAME
                    )

                while (
                    cursor.moveToNext() &&
                    names.size < 12
                ) {
                    names.add(
                        cursor.getString(nameIndex)
                    )

                    uris.add(
                        ContentUris.withAppendedId(
                            collection,
                            cursor.getLong(idIndex)
                        )
                    )
                }
            }
        } catch (_: Exception) {
        }

        if (names.isEmpty()) {
            toast(
                "No accessible develop.uganda clips • use GALLERY"
            )
            return
        }

        AlertDialog.Builder(this)
            .setTitle(
                "RECENT develop.uganda VIDEOS"
            )
            .setItems(
                names.toTypedArray()
            ) { _, which ->
                loadVideo(
                    uris[which],
                    "RECENT"
                )
            }
            .setNegativeButton(
                "CANCEL",
                null
            )
            .show()
    }

    private fun loadLatestDevelopUgandaClip() {
        val collection =
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI

        try {
            contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.Video.Media._ID
                ),
                DevelopUgandaFivemods12Identity.mediaStoreNameWhere(MediaStore.Video.Media.DISPLAY_NAME),
                DevelopUgandaFivemods12Identity.mediaStoreNameArgs(),
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    loadVideo(
                        ContentUris.withAppendedId(
                            collection,
                            cursor.getLong(0)
                        ),
                        "LAST CLIP"
                    )
                    return
                }
            }

            toast(
                "No accessible develop.uganda clip • use GALLERY"
            )
        } catch (_: Exception) {
            toast(
                "Use GALLERY or FILES to choose the video"
            )
        }
    }

    private fun markIn() {
        val exo = player ?: return

        if (!readyForEdit()) {
            return
        }

        startSeek.progress =
            positionToProgress(
                exo.currentPosition
            )
                .coerceAtMost(
                    endSeek.progress - 1
                )
                .coerceAtLeast(0)

        updateTrimLabels()
    }

    private fun markOut() {
        val exo = player ?: return

        if (!readyForEdit()) {
            return
        }

        endSeek.progress =
            positionToProgress(
                exo.currentPosition
            )
                .coerceAtLeast(
                    startSeek.progress + 1
                )
                .coerceAtMost(1000)

        updateTrimLabels()
    }

    private fun resetCut() {
        stopCutPreview()
        startSeek.progress = 0
        endSeek.progress = 1000
        updateTrimLabels()
        seekPreview(0L)
        statusView.text =
            "CUT RESET • full clip selected"
    }

    private fun previewCut() {
        val exo = player ?: return

        if (!readyForEdit()) {
            return
        }

        if (previewingCut) {
            stopCutPreview()
            exo.pause()
            statusView.text =
                "Cut preview stopped"
            return
        }

        val startMs = currentStartMs()
        val endMs = currentEndMs()

        if (endMs <= startMs) {
            toast("Choose a valid cut")
            return
        }

        previewingCut = true
        previewCutButton.text =
            "STOP PREVIEW"

        exo.seekTo(startMs)
        exo.play()

        statusView.text =
            "PREVIEW CUT • ${formatTime(startMs)} → ${formatTime(endMs)}"

        val guard =
            object : Runnable {
                override fun run() {
                    if (!previewingCut) {
                        return
                    }

                    if (
                        !exo.isPlaying ||
                        exo.currentPosition >= endMs
                    ) {
                        exo.pause()
                        exo.seekTo(startMs)
                        stopCutPreview()

                        statusView.text =
                            "CUT PREVIEW FINISHED • ready to save"
                        return
                    }

                    playerView.postDelayed(
                        this,
                        60L
                    )
                }
            }

        playerView.postDelayed(
            guard,
            60L
        )
    }

    private fun stopCutPreview() {
        previewingCut = false

        if (::previewCutButton.isInitialized) {
            previewCutButton.text =
                "PREVIEW CUT"
        }
    }

    private fun seekPreview(
        positionMs: Long
    ) {
        val exo = player ?: return

        if (durationMs <= 0L) {
            return
        }

        exo.seekTo(
            positionMs.coerceIn(
                0L,
                durationMs
            )
        )
    }

    private fun exportWithMedia3(
        includeAudio: Boolean
    ) {
        val input =
            sourceUri ?: run {
                toast("Choose a video first")
                return
            }

        if (!readyForEdit()) {
            return
        }

        val startMs = currentStartMs()
        val endMs = currentEndMs()

        if (endMs <= startMs) {
            toast("Choose a longer cut")
            return
        }

        transformer?.cancel()

        val exportDir =
            File(
                cacheDir,
                "v220_media3_exports"
            ).apply {
                mkdirs()
            }

        val temp =
            File(
                exportDir,
                "export_${System.currentTimeMillis()}.mp4"
            )

        if (temp.exists()) {
            temp.delete()
        }

        val clipped =
            MediaItem.Builder()
                .setUri(input)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(startMs)
                        .setEndPositionMs(endMs)
                        .build()
                )
                .build()

        val edited =
            EditedMediaItem.Builder(clipped)
                .setRemoveAudio(!includeAudio)
                .build()

        statusView.text =
            if (includeAudio) {
                "MEDIA3 EXPORT • cutting video…"
            } else {
                "MEDIA3 EXPORT • cutting + removing audio…"
            }

        val listener =
            object : Transformer.Listener {
                override fun onCompleted(
                    composition: Composition,
                    result: ExportResult
                ) {
                    transformer = null

                    Thread {
                        try {
                            val uri =
                                publishTempVideo(
                                    temp,
                                    muted = !includeAudio
                                )

                            temp.delete()
                            exportedUri = uri

                            runOnUiThread {
                                statusView.text =
                                    "SAVED • Media3 MP4 • PLAY SAVED / LOAD SAVED / SHARE"

                                toast(
                                    "Edited video saved"
                                )
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                statusView.text =
                                    "PUBLISH FAILED • ${e.message ?: "unknown error"}"

                                toast(
                                    "Could not publish edited video"
                                )
                            }
                        }
                    }.start()
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    transformer = null
                    temp.delete()

                    statusView.text =
                        "MEDIA3 EXPORT FAILED • ${exception.errorCodeName}"

                    toast("Edit failed")
                }
            }

        transformer =
            Transformer.Builder(this)
                .addListener(listener)
                .build()
                .also {
                    it.start(
                        edited,
                        temp.absolutePath
                    )
                }
    }


    private fun exportSocialMaster(
        platform: String,
        bitrate: Int
    ) {
        val input =
            sourceUri ?: run {
                toast(
                    "Choose a video first"
                )
                return
            }

        if (
            !readyForEdit()
        ) {
            return
        }

        val startMs =
            currentStartMs()

        val endMs =
            currentEndMs()

        if (
            endMs <=
                startMs
        ) {
            toast(
                "Choose a longer cut"
            )
            return
        }

        transformer?.cancel()

        val exportDir =
            File(
                cacheDir,
                "v221_social_exports"
            ).apply {
                mkdirs()
            }

        val temp =
            File(
                exportDir,
                "social_${platform.lowercase(Locale.US)}_${System.currentTimeMillis()}.mp4"
            )

        if (
            temp.exists()
        ) {
            temp.delete()
        }

        val clipped =
            MediaItem.Builder()
                .setUri(
                    input
                )
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(
                            startMs
                        )
                        .setEndPositionMs(
                            endMs
                        )
                        .build()
                )
                .build()

        val socialEffects =
            Effects(
                emptyList(),
                listOf(
                    Presentation.createForWidthAndHeight(
                        1080,
                        1920,
                        Presentation.LAYOUT_SCALE_TO_FIT
                    ),

                    // Media3 may transmux when input/output already match.
                    // A tiny non-zero RGB effect is visually negligible but
                    // requires decode -> process -> encode, so the requested
                    // social bitrate and H.264 encoder settings are applied.
                    Brightness(
                        0.0001f
                    )
                )
            )

        val edited =
            EditedMediaItem.Builder(
                clipped
            )
                .setFrameRate(
                    30
                )
                .setEffects(
                    socialEffects
                )
                .build()

        val sequence =
            EditedMediaItemSequence.withAudioAndVideoFrom(
                listOf(
                    edited
                )
            )

        val compositionBuilder =
            Composition.Builder(
                listOf(
                    sequence
                )
            )

        if (
            Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
        ) {
            compositionBuilder.setHdrMode(
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            )
        }

        val composition =
            compositionBuilder
                .build()

        val videoSettings =
            VideoEncoderSettings.Builder()
                .setBitrate(
                    bitrate
                )
                .setiFrameIntervalSeconds(
                    2f
                )
                .build()

        val audioSettings =
            AudioEncoderSettings.Builder()
                .setBitrate(
                    256_000
                )
                .build()

        val encoderFactory =
            DefaultEncoderFactory.Builder(
                this
            )
                .setRequestedVideoEncoderSettings(
                    videoSettings
                )
                .setRequestedAudioEncoderSettings(
                    audioSettings
                )
                .build()

        statusView.text =
            "$platform MASTER • FORCED RE-ENCODE • 1080×1920 H.264/AAC • ${bitrate / 1_000_000} Mbps target"

        val listener =
            object :
                Transformer.Listener {

                override fun onCompleted(
                    completedComposition: Composition,
                    result: ExportResult
                ) {
                    transformer =
                        null

                    Thread {
                        try {
                            val verification =
                                verifySocialEncode(
                                    temp =
                                        temp,
                                    requestedBitrate =
                                        bitrate
                                )

                            val uri =
                                publishSocialMaster(
                                    temp =
                                        temp,
                                    platform =
                                        platform
                                )

                            temp.delete()

                            socialMasterUri =
                                uri

                            socialMasterLabel =
                                platform

                            runOnUiThread {
                                statusView.text =
                                    "$platform MASTER SAVED • FORCED RE-ENCODE VERIFIED • $verification • original preserved"

                                toast(
                                    "$platform social master saved"
                                )
                            }
                        } catch (
                            e: Exception
                        ) {
                            runOnUiThread {
                                statusView.text =
                                    "$platform PUBLISH FAILED • ${e.message ?: "unknown error"}"

                                toast(
                                    "Could not publish social master"
                                )
                            }
                        }
                    }.start()
                }

                override fun onError(
                    failedComposition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    transformer =
                        null

                    temp.delete()

                    statusView.text =
                        "$platform MASTER FAILED • ${exception.errorCodeName}"

                    toast(
                        "Social master export failed"
                    )
                }
            }

        transformer =
            Transformer.Builder(
                this
            )
                .setEncoderFactory(
                    encoderFactory
                )
                .setVideoMimeType(
                    MimeTypes.VIDEO_H264
                )
                .setAudioMimeType(
                    MimeTypes.AUDIO_AAC
                )
                .addListener(
                    listener
                )
                .build()
                .also {
                    it.start(
                        composition,
                        temp.absolutePath
                    )
                }
    }


    private fun verifySocialEncode(
        temp: File,
        requestedBitrate: Int
    ): String {
        if (
            !temp.exists() ||
            temp.length() <=
                0L
        ) {
            error(
                "Social master output is empty"
            )
        }

        val retriever =
            MediaMetadataRetriever()

        return try {
            retriever.setDataSource(
                temp.absolutePath
            )

            val width =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )
                    ?.toIntOrNull()
                    ?: 0

            val height =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )
                    ?.toIntOrNull()
                    ?: 0

            val totalBitrate =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_BITRATE
                )
                    ?.toLongOrNull()
                    ?: 0L

            if (
                width !=
                    1080 ||
                height !=
                    1920
            ) {
                error(
                    "Social master dimensions are ${width}×${height}, expected 1080×1920"
                )
            }

            val maxAllowed =
                (
                    requestedBitrate.toLong() *
                        135L
                    ) /
                    100L

            if (
                totalBitrate >
                    maxAllowed
            ) {
                error(
                    "Social master remained too close to original bitrate (${totalBitrate / 1_000_000L} Mbps). Forced re-encode verification failed."
                )
            }

            if (
                totalBitrate <
                    4_000_000L
            ) {
                error(
                    "Social master bitrate is unexpectedly low (${totalBitrate / 1_000_000L} Mbps)"
                )
            }

            val mbps =
                String.format(
                    Locale.US,
                    "%.1f",
                    totalBitrate /
                        1_000_000.0
                )

            "1080×1920 • $mbps Mbps"
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun publishSocialMaster(
        temp: File,
        platform: String
    ): Uri {
        if (
            !temp.exists() ||
            temp.length() <=
                0L
        ) {
            error(
                "Social master output is empty"
            )
        }

        val stamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(
                Date()
            )

        val name =
            "DEVELOP_UGANDA_V221_FIX1_${platform}_MASTER_" +
                stamp +
                ".mp4"

        val values =
            ContentValues().apply {
                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    name
                )

                put(
                    MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4"
                )

                if (
                    Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.Q
                ) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/develop.uganda/Social"
                    )

                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        1
                    )
                }
            }

        val uri =
            contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error(
                "Could not create social master in Gallery"
            )

        try {
            contentResolver.openOutputStream(
                uri,
                "w"
            )?.use { output ->
                FileInputStream(
                    temp
                ).use { input ->
                    input.copyTo(
                        output,
                        1024 * 1024
                    )
                }
            } ?: error(
                "Could not open social master output stream"
            )

            if (
                Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
            ) {
                contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(
                            MediaStore.Video.Media.IS_PENDING,
                            0
                        )
                    },
                    null,
                    null
                )
            }

            return uri
        } catch (
            e: Exception
        ) {
            try {
                contentResolver.delete(
                    uri,
                    null,
                    null
                )
            } catch (_: Exception) {
            }

            throw e
        }
    }

    private fun shareSocialMaster() {
        val uri =
            socialMasterUri ?: run {
                toast(
                    "Create a TikTok or Reels master first"
                )
                return
            }

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

                    putExtra(
                        Intent.EXTRA_TEXT,
                        "develop.uganda ${socialMasterLabel} master"
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                },
                "Share ${socialMasterLabel} master"
            )
        )
    }

    private fun publishTempVideo(
        temp: File,
        muted: Boolean
    ): Uri {
        if (
            !temp.exists() ||
            temp.length() <= 0L
        ) {
            error(
                "Transformer output is empty"
            )
        }

        val stamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(Date())

        val name =
            "DEVELOP_UGANDA_EDIT_V220_" +
                (if (muted) "MUTED_" else "") +
                stamp +
                ".mp4"

        val values =
            ContentValues().apply {
                put(
                    MediaStore.Video.Media.DISPLAY_NAME,
                    name
                )

                put(
                    MediaStore.Video.Media.MIME_TYPE,
                    "video/mp4"
                )

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.Q
                ) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "Movies/develop.uganda/Edited"
                    )

                    put(
                        MediaStore.Video.Media.IS_PENDING,
                        1
                    )
                }
            }

        val uri =
            contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                values
            ) ?: error(
                "Could not create Gallery output"
            )

        try {
            contentResolver.openOutputStream(
                uri,
                "w"
            )?.use { output ->
                FileInputStream(temp).use { input ->
                    input.copyTo(
                        output,
                        1024 * 1024
                    )
                }
            } ?: error(
                "Could not open Gallery output stream"
            )

            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.Q
            ) {
                contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(
                            MediaStore.Video.Media.IS_PENDING,
                            0
                        )
                    },
                    null,
                    null
                )
            }

            return uri
        } catch (e: Exception) {
            try {
                contentResolver.delete(
                    uri,
                    null,
                    null
                )
            } catch (_: Exception) {
            }

            throw e
        }
    }

    private fun playSaved() {
        val uri =
            exportedUri ?: run {
                toast("Save a cut first")
                return
            }

        loadVideo(
            uri,
            "SAVED EDIT",
            keepExport = true
        )

        player?.play()
    }

    private fun shareBestAvailable() {
        val uri =
            exportedUri ?: sourceUri ?: run {
                toast(
                    "Choose or save a video first"
                )
                return
            }

        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "video/mp4"

                    putExtra(
                        Intent.EXTRA_STREAM,
                        uri
                    )

                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                },
                "Share develop.uganda video"
            )
        )
    }

    private fun readyForEdit(): Boolean {
        if (sourceUri == null) {
            toast("Choose a video first")
            return false
        }

        if (durationMs <= 0L) {
            toast(
                "Wait for video to finish loading"
            )
            return false
        }

        return true
    }

    private fun currentStartMs(): Long =
        durationMs *
            startSeek.progress /
            1000L

    private fun currentEndMs(): Long =
        durationMs *
            endSeek.progress /
            1000L

    private fun positionToProgress(
        positionMs: Long
    ): Int {
        if (durationMs <= 0L) {
            return 0
        }

        return (
            positionMs *
                1000L /
                durationMs
            )
            .toInt()
            .coerceIn(0, 1000)
    }

    private fun updateTrimLabels() {
        startLabel.text =
            "IN ${formatTime(currentStartMs())}"

        endLabel.text =
            "OUT ${formatTime(currentEndMs())}"
    }

    private fun displayName(
        uri: Uri
    ): String =
        try {
            contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else {
                    null
                }
            } ?: (
                uri.lastPathSegment
                    ?: "video"
                )
        } catch (_: Exception) {
            uri.lastPathSegment
                ?: "video"
        }

    private fun seekListener(
        change: (Int) -> Unit
    ): SeekBar.OnSeekBarChangeListener =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar?,
                progress: Int,
                fromUser: Boolean
            ) {
                if (fromUser) {
                    stopCutPreview()
                    change(progress)
                }
            }

            override fun onStartTrackingTouch(
                seekBar: SeekBar?
            ) {
            }

            override fun onStopTrackingTouch(
                seekBar: SeekBar?
            ) {
            }
        }

    private fun action(
        value: String,
        accent: Int,
        click: () -> Unit
    ): Button =
        Button(this).apply {
            text = value
            textSize = 9f
            isAllCaps = false
            setTextColor(DevelopUgandaFivemods8Theme.content)

            background =
                GradientDrawable().apply {
                    shape =
                        GradientDrawable.RECTANGLE

                    cornerRadius =
                        dp(15).toFloat()

                    setColor(
                        DevelopUgandaFivemods8Theme.surfaceRaised
                    )

                    setStroke(
                        dp(1),
                        accent
                    )
                }

            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "EDITOR BUTTON") { click.invoke() }
            }
        }

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

            typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    if (bold) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
        }

    private fun weight():
        LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply {
            marginStart = dp(3)
            marginEnd = dp(3)
        }

    private fun formatTime(
        ms: Long
    ): String {
        val total =
            (ms / 1000L)
                .coerceAtLeast(0L)

        val hours =
            total / 3600L

        val minutes =
            (total % 3600L) / 60L

        val seconds =
            total % 60L

        return if (hours > 0L) {
            String.format(
                Locale.US,
                "%02d:%02d:%02d",
                hours,
                minutes,
                seconds
            )
        } else {
            String.format(
                Locale.US,
                "%02d:%02d",
                minutes,
                seconds
            )
        }
    }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources.displayMetrics.density
            )
            .roundToInt()

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
