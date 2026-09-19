package com.sentongoharuna.pulse

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.concurrent.Executors

object DevelopUgandaStoryPackager {

    /** Explicit bridge into the Media Vault delivery queue; packaging never auto-uploads. */
    fun queuePackagedMedia(
        context: Context,
        uri: Uri,
        displayName: String,
        mode: DevelopUgandaCameraPage?,
        cleanMaster: Boolean,
        cleanUploadConsent: Boolean,
    ): DevelopUgandaQueuedDelivery = DevelopUgandaDeliveryQueue.enqueue(
        context = context,
        mediaUri = uri,
        displayName = displayName,
        mode = mode,
        destination = DevelopUgandaDeliveryQueue.configuredDestination(context),
        networkPolicy = DevelopUgandaDeliveryNetworkPolicy.WIFI_ONLY,
        cleanMaster = cleanMaster,
        cleanUploadConsent = cleanUploadConsent,
    )

    data class StoryMetadata(
        val packageId: String,
        val camera: String,
        val reporter: String,
        val storyId: String,
        val title: String,
        val place: String?,
        val latitude: Double?,
        val longitude: Double?,
        val gpsAccuracyM: Float?,
        val startedUtc: String,
        val finishedUtc: String,
        val scene: String,
        val look: String,
        val quality: String,
        val autoView: String,
        val warnings: List<String>,
        val sourceKind: String,
        val autoTranscribe: Boolean,
        val expectSocialMaster: Boolean,
        val projectName: String = "",
        val cameraName: String = "",
        val slateScene: Int = 0,
        val slateTake: Int = 0,
        val slateEnabled: Boolean = false
    )

    data class RegistryEntry(
        val packageId: String,
        val path: String,
        val title: String,
        val camera: String,
        val createdUtc: String,
        val state: String
    )

    data class PlayableVideo(
        val uri: Uri,
        val label: String
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private const val PREFS = "develop_uganda_story_packages"
    private const val REGISTRY = "registry_json"

    fun createVideoPackage(
        context: Context,
        sourceUri: Uri,
        metadata: StoryMetadata
    ) {
        val appContext = context.applicationContext

        // Snapshot before the background task starts, so the Story Package
        // records the V228 brand/tag profile that belonged to this clip.
        val brandSnapshot =
            DevelopUgandaBrandMetadataStore.snapshot(
                context
            )

        executor.execute {
            val packageId = safeSegment(
                metadata.packageId.ifBlank {
                    "DU_${System.currentTimeMillis()}"
                }
            )
            val relative = packageRelativePath(packageId)

            registerPackage(
                appContext,
                RegistryEntry(
                    packageId,
                    relative,
                    metadata.title,
                    metadata.camera,
                    metadata.finishedUtc,
                    "BUILDING"
                )
            )

            try {
                writeText(
                    appContext,
                    relative,
                    "PACKAGE_STATUS.txt",
                    "BUILDING • ORIGINAL COPY • THUMBNAIL • MANIFEST • INTEGRITY"
                )
                writeText(
                    appContext,
                    relative,
                    "README.txt",
                    buildReadme(packageId, metadata)
                )

                val sourceName = displayName(appContext, sourceUri)
                    ?: "develop_uganda_source.mp4"
                val sourceSize = sourceSizeBytes(appContext, sourceUri)

                val originalCopyUri = try {
                    copyUriToPackage(
                        appContext,
                        sourceUri,
                        relative,
                        "ORIGINAL_VIDEO.mp4",
                        "video/mp4"
                    )
                } catch (e: Exception) {
                    writeText(
                        appContext,
                        relative,
                        "ORIGINAL_COPY_STATUS.txt",
                        "COPY FAILED • SOURCE URI RETAINED IN MANIFEST • ${e.javaClass.simpleName}"
                    )
                    null
                }

                val durationMs = readDurationMs(appContext, sourceUri)
                val thumbnailUri = createThumbnail(
                    appContext,
                    sourceUri,
                    relative,
                    durationMs
                )

                val sourceHash = sha256OfUri(appContext, sourceUri)
                val copyHash = originalCopyUri?.let {
                    sha256OfUri(appContext, it)
                }
                val integrityMatch = when {
                    sourceHash == null -> "SOURCE HASH UNAVAILABLE"
                    originalCopyUri == null -> "COPY NOT AVAILABLE"
                    copyHash == null -> "COPY HASH UNAVAILABLE"
                    sourceHash == copyHash -> "MATCH"
                    else -> "MISMATCH"
                }

                writeJson(
                    appContext,
                    relative,
                    "INTEGRITY.json",
                    JSONObject()
                        .put("algorithm", "SHA-256")
                        .put("source_sha256", sourceHash ?: JSONObject.NULL)
                        .put("package_copy_sha256", copyHash ?: JSONObject.NULL)
                        .put("copy_match", integrityMatch)
                        .put(
                            "note",
                            "SHA-256 detects later file changes. It is not a digital signature or proof of authorship."
                        )
                        .put("generated_utc", Instant.now().toString())
                )

                writeJson(
                    appContext,
                    relative,
                    "BRAND_METADATA.json",
                    brandSnapshot.toJson()
                        .put(
                            "captured_for_package",
                            packageId
                        )
                        .put(
                            "captured_utc",
                            Instant.now().toString()
                        )
                )

                writeJson(
                    appContext,
                    relative,
                    "METADATA.json",
                    JSONObject()
                        .put("app_version", "V236")
                        .put("brand_display_name", brandSnapshot.displayName)
                        .put("brand_organization", brandSnapshot.organization)
                        .put("brand_overlay_preset", brandSnapshot.preset)
                        .put("brand_output_profile", brandSnapshot.outputProfile)
                        .put("package_id", packageId)
                        .put("source_kind", metadata.sourceKind)
                        .put(
                            "capture_format",
                            DevelopUgandaFieldIntelligencePanel.activeFormatLabel(appContext)
                        )
                        .put(
                            "field_intelligence",
                            DevelopUgandaFieldIntelligencePanel.snapshotJson(appContext)
                        )
                        .put("camera", metadata.camera)
                        .put("reporter", metadata.reporter)
                        .put("story_id", metadata.storyId)
                        .put("title", metadata.title)
                        .put("place", metadata.place ?: JSONObject.NULL)
                        .put("latitude", metadata.latitude ?: JSONObject.NULL)
                        .put("longitude", metadata.longitude ?: JSONObject.NULL)
                        .put("gps_accuracy_m", metadata.gpsAccuracyM ?: JSONObject.NULL)
                        .put("recording_started_utc", metadata.startedUtc)
                        .put("recording_finished_utc", metadata.finishedUtc)
                        .put("scene", metadata.scene)
                        .put("look", metadata.look)
                        .put("quality", metadata.quality)
                        .put("project_name", if (metadata.projectName.isBlank()) JSONObject.NULL else metadata.projectName)
                        .put("camera_name", if (metadata.cameraName.isBlank()) JSONObject.NULL else metadata.cameraName)
                        .put("slate_enabled", metadata.slateEnabled)
                        .put("slate_scene", if (metadata.slateEnabled) metadata.slateScene else JSONObject.NULL)
                        .put("slate_take", if (metadata.slateEnabled) metadata.slateTake else JSONObject.NULL)
                        .put("auto_view", metadata.autoView)
                        .put("shot_guard_warnings", JSONArray(metadata.warnings))
                        .put("duration_ms", durationMs ?: JSONObject.NULL)
                )

                writeText(
                    appContext,
                    relative,
                    "CAPTION_DRAFT.txt",
                    suggestedCaption(
                        metadata,
                        brandSnapshot
                    )
                )

                writeText(
                    appContext,
                    relative,
                    "SOCIAL_MASTER_STATUS.txt",
                    if (metadata.expectSocialMaster) {
                        "WAITING • V222 SM Camera is still preparing the social master"
                    } else {
                        "NOT REQUESTED • use V222 Social Media Camera or Editor when a separate social master is needed"
                    }
                )

                writeText(
                    appContext,
                    relative,
                    "COLOR_MASTER_STATUS.txt",
                    "WAITING • V235 color engine will resolve the selected profile after the original Story Package is ready"
                )

                writeText(
                    appContext,
                    relative,
                    "TRANSCRIPT_STATUS.txt",
                    when {
                        !metadata.autoTranscribe ->
                            "NOT AUTO-REQUESTED • automatic transcript is enabled by default for INTERVIEW / V211 AUDIO • it can be requested manually from Story Packages"
                        Build.VERSION.SDK_INT < 33 ->
                            "UNAVAILABLE • prerecorded-audio SpeechRecognizer injection requires Android 13 / API 33+"
                        else ->
                            "QUEUED • on-device recognizer will be used only if the phone provides one"
                    }
                )

                writeJson(
                    appContext,
                    relative,
                    "MANIFEST.json",
                    JSONObject()
                        .put("package_version", 1)
                        .put("app_version", "V236")
                        .put("package_id", packageId)
                        .put("created_utc", Instant.now().toString())
                        .put("source_uri", sourceUri.toString())
                        .put("source_display_name", sourceName)
                        .put("source_size_bytes", sourceSize ?: JSONObject.NULL)
                        .put("original_copy_uri", originalCopyUri?.toString() ?: JSONObject.NULL)
                        .put("thumbnail_uri", thumbnailUri?.toString() ?: JSONObject.NULL)
                        .put("integrity_copy_match", integrityMatch)
                        .put("social_master_expected", metadata.expectSocialMaster)
                        .put("transcript_auto_requested", metadata.autoTranscribe)
                        .put(
                            "files",
                            JSONArray(
                                listOf(
                                    "README.txt",
                                    "PACKAGE_STATUS.txt",
                                    "ORIGINAL_VIDEO.mp4",
                                    "THUMBNAIL.jpg",
                                    "METADATA.json",
                                    "BRAND_METADATA.json",
                                    "INTEGRITY.json",
                                    "CAPTION_DRAFT.txt",
                                    "SOCIAL_MASTER_STATUS.txt",
                                    "COLOR_MASTER.mp4",
                                    "COLOR_MASTER_STATUS.txt",
                                    "COLOR_PROFILE.json",
                                    "TRANSCRIPT_STATUS.txt"
                                )
                            )
                        )
                )

                val state = if (
                    metadata.autoTranscribe && Build.VERSION.SDK_INT >= 33
                ) {
                    "READY • TRANSCRIPT CHECKING"
                } else {
                    "READY"
                }

                writeText(appContext, relative, "PACKAGE_STATUS.txt", state)
                registerPackage(
                    appContext,
                    RegistryEntry(
                        packageId,
                        relative,
                        metadata.title,
                        metadata.camera,
                        metadata.finishedUtc,
                        state
                    )
                )
                showToast(appContext, "STORY PACKAGE READY • $packageId")

                if (metadata.autoTranscribe) {
                    requestTranscriptInternal(
                        appContext,
                        packageId,
                        relative,
                        originalCopyUri ?: sourceUri
                    )
                }
            } catch (e: Exception) {
                try {
                    writeText(
                        appContext,
                        relative,
                        "PACKAGE_STATUS.txt",
                        "PACKAGE ERROR • ${e.javaClass.simpleName}"
                    )
                } catch (_: Exception) {
                }

                registerPackage(
                    appContext,
                    RegistryEntry(
                        packageId,
                        relative,
                        metadata.title,
                        metadata.camera,
                        metadata.finishedUtc,
                        "ERROR"
                    )
                )
                showToast(
                    appContext,
                    "Story Package error • original recording remains safe"
                )
            }
        }
    }

    fun markColorMasterBuilding(
        context: Context,
        packageId: String,
        profileLabel: String,
        strength: Int
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val safeId = safeSegment(packageId)
            val relative = packageRelativePath(safeId)
            try {
                writeText(
                    appContext,
                    relative,
                    "COLOR_MASTER_STATUS.txt",
                    "BUILDING • $profileLabel • STRENGTH ${strength}% • ORIGINAL VIDEO REMAINS SAFE"
                )
            } catch (_: Exception) {
            }
        }
    }

    fun attachColorMaster(
        context: Context,
        packageId: String,
        colorUri: Uri,
        profileLabel: String,
        strength: Int,
        width: Int?,
        height: Int?,
        durationMs: Long?,
        bitrate: Long?
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val safeId = safeSegment(packageId)
            val relative = packageRelativePath(safeId)

            try {
                val copy = copyUriToPackage(
                    appContext,
                    colorUri,
                    relative,
                    "COLOR_MASTER.mp4",
                    "video/mp4"
                )

                val hash = sha256OfUri(
                    appContext,
                    copy
                )

                writeJson(
                    appContext,
                    relative,
                    "COLOR_PROFILE.json",
                    JSONObject()
                        .put("engine", "develop.uganda V233 Live Grade Monitor")
                        .put("profile_label", profileLabel)
                        // This is the operator-selected LUT blend strength. It
                        // is derived configuration, not measured confidence.
                        .put("strength_percent", strength)
                        .put("strength_origin", "DERIVED • OPERATOR PROFILE SETTING")
                        .put("master_file", "COLOR_MASTER.mp4")
                        .put("gallery_uri", colorUri.toString())
                        .put("package_copy_uri", copy.toString())
                        .put("width", width ?: JSONObject.NULL)
                        .put("height", height ?: JSONObject.NULL)
                        .put("duration_ms", durationMs ?: JSONObject.NULL)
                        .put("bitrate", bitrate ?: JSONObject.NULL)
                        .put("sha256", hash ?: JSONObject.NULL)
                        .put("lut_pipeline", "Media3 SingleColorLut 17^3")
                        .put("output_codec", "H.264/AAC")
                        .put(
                            "note",
                            "The original CameraX recording is preserved unchanged. The Color Master is a separate rendered delivery copy."
                        )
                        .put("generated_utc", Instant.now().toString())
                )

                writeText(
                    appContext,
                    relative,
                    "COLOR_MASTER_STATUS.txt",
                    "READY • COLOR_MASTER.mp4 • $profileLabel • PROFILE STRENGTH ${strength}% • DERIVED / OPERATOR SET • SHA-256 ${hash ?: "unavailable"}"
                )

                updateRegistryState(
                    appContext,
                    safeId,
                    "READY • COLOR MASTER"
                )

                showToast(
                    appContext,
                    "Story Package updated • V233 color master attached"
                )
            } catch (e: Exception) {
                markColorMasterFailed(
                    appContext,
                    safeId,
                    e.javaClass.simpleName
                )
            }
        }
    }

    fun markColorMasterSkipped(
        context: Context,
        packageId: String,
        reason: String
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val safeId = safeSegment(packageId)
            val relative = packageRelativePath(safeId)
            try {
                writeText(
                    appContext,
                    relative,
                    "COLOR_MASTER_STATUS.txt",
                    "NOT REQUESTED • $reason"
                )
            } catch (_: Exception) {
            }
        }
    }

    fun markColorMasterFailed(
        context: Context,
        packageId: String,
        reason: String
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val safeId = safeSegment(packageId)
            val relative = packageRelativePath(safeId)
            try {
                writeText(
                    appContext,
                    relative,
                    "COLOR_MASTER_STATUS.txt",
                    "FAILED • $reason • ORIGINAL VIDEO REMAINS SAFE"
                )
                updateRegistryState(
                    appContext,
                    safeId,
                    "READY • COLOR MASTER FAILED"
                )
            } catch (_: Exception) {
            }
        }
    }

    fun attachSocialMaster(
        context: Context,
        packageId: String,
        socialUri: Uri
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val safeId = safeSegment(packageId)
            val relative = packageRelativePath(safeId)
            try {
                val copy = copyUriToPackage(
                    appContext,
                    socialUri,
                    relative,
                    "SOCIAL_MASTER.mp4",
                    "video/mp4"
                )
                val hash = sha256OfUri(appContext, copy)
                writeText(
                    appContext,
                    relative,
                    "SOCIAL_MASTER_STATUS.txt",
                    "READY • SOCIAL_MASTER.mp4 • SHA-256 ${hash ?: "unavailable"}"
                )
                updateRegistryState(appContext, safeId, "READY • SOCIAL MASTER")
                showToast(appContext, "Story Package updated • social master attached")
            } catch (e: Exception) {
                markSocialMasterFailed(appContext, safeId, e.javaClass.simpleName)
            }
        }
    }

    fun markSocialMasterFailed(
        context: Context,
        packageId: String,
        detail: String
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                writeText(
                    appContext,
                    packageRelativePath(safeSegment(packageId)),
                    "SOCIAL_MASTER_STATUS.txt",
                    "FAILED • original video remains safe • $detail"
                )
            } catch (_: Exception) {
            }
        }
    }

    fun requestTranscript(
        context: Context,
        packageId: String
    ) {
        val appContext = context.applicationContext
        executor.execute {
            val safeId = safeSegment(packageId)
            val relative = packageRelativePath(safeId)
            val original = findPackageFile(
                appContext,
                safeId,
                "ORIGINAL_VIDEO.mp4"
            ) ?: sourceUriFromManifest(appContext, safeId)

            if (original == null) {
                writeText(
                    appContext,
                    relative,
                    "TRANSCRIPT_STATUS.txt",
                    "FAILED • no readable original video was found"
                )
                return@execute
            }

            requestTranscriptInternal(
                appContext,
                safeId,
                relative,
                original
            )
        }
    }

    private fun requestTranscriptInternal(
        context: Context,
        packageId: String,
        relative: String,
        videoUri: Uri
    ) {
        if (Build.VERSION.SDK_INT < 33) {
            writeText(
                context,
                relative,
                "TRANSCRIPT_STATUS.txt",
                "UNAVAILABLE • Android 13 / API 33+ required for prerecorded audio injection"
            )
            return
        }

        writeText(
            context,
            relative,
            "TRANSCRIPT_STATUS.txt",
            "TRANSCRIBING • on-device recognizer only • original media is unchanged"
        )

        DevelopUgandaTranscriptEngine.transcribe(
            context = context,
            videoUri = videoUri
        ) { result ->
            executor.execute {
                if (result.transcript.isNotBlank()) {
                    writeText(
                        context,
                        relative,
                        "TRANSCRIPT_DRAFT.txt",
                        "DRAFT TRANSCRIPT • REVIEW BEFORE PUBLICATION\n\n" + result.transcript
                    )
                }
                if (result.srtDraft != null) {
                    writeText(
                        context,
                        relative,
                        "SUBTITLES_DRAFT.srt",
                        result.srtDraft
                    )
                }
                writeText(
                    context,
                    relative,
                    "TRANSCRIPT_STATUS.txt",
                    result.status + "\n" + result.detail
                )
                updateRegistryState(
                    context,
                    packageId,
                    if (result.transcript.isNotBlank()) {
                        "READY • TRANSCRIPT DRAFT"
                    } else {
                        "READY • NO TRANSCRIPT"
                    }
                )
                showToast(
                    context,
                    if (result.transcript.isNotBlank()) {
                        "Transcript draft added • review before use"
                    } else {
                        "Transcript unavailable on this device/session"
                    }
                )
            }
        }
    }

    fun listRegistry(context: Context): List<RegistryEntry> {
        val raw = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(REGISTRY, "[]") ?: "[]"

        return try {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(
                        RegistryEntry(
                            item.optString("package_id"),
                            item.optString("path"),
                            item.optString("title"),
                            item.optString("camera"),
                            item.optString("created_utc"),
                            item.optString("state")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun findPackageFile(
        context: Context,
        packageId: String,
        displayName: String
    ): Uri? {
        val safeId = safeSegment(packageId)
        val relative = packageRelativePath(safeId)

        if (Build.VERSION.SDK_INT >= 29) {
            return findDownloadUri(context, relative, displayName)
        }

        val file = File(legacyPackageDir(context, safeId), displayName)
        return if (file.exists()) Uri.fromFile(file) else null
    }

    fun readText(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    fun resolvePlayableVideo(
        context: Context,
        packageId: String
    ): PlayableVideo? {
        val safeId = safeSegment(packageId)

        val source = sourceUriFromManifest(
            context,
            safeId
        )

        if (
            source != null &&
            uriIsReadable(
                context,
                source
            )
        ) {
            return PlayableVideo(
                uri = source,
                label = "ORIGINAL GALLERY VIDEO"
            )
        }

        val packageCopy = findPackageFile(
            context,
            safeId,
            "ORIGINAL_VIDEO.mp4"
        )

        if (
            packageCopy != null &&
            uriIsReadable(
                context,
                packageCopy
            )
        ) {
            return PlayableVideo(
                uri = packageCopy,
                label = "STORY PACKAGE COPY"
            )
        }

        return null
    }

    private fun uriIsReadable(
        context: Context,
        uri: Uri
    ): Boolean {
        return try {
            if (
                uri.scheme == "file"
            ) {
                val path = uri.path ?: return false
                val file = File(path)
                file.exists() && file.length() > 0L
            } else {
                context.contentResolver
                    .openFileDescriptor(
                        uri,
                        "r"
                    )
                    ?.use {
                        true
                    }
                    ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun sourceUriFromManifest(
        context: Context,
        packageId: String
    ): Uri? {
        val uri = findPackageFile(context, packageId, "MANIFEST.json") ?: return null
        val raw = readText(context, uri) ?: return null
        return try {
            JSONObject(raw).optString("source_uri")
                .takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildReadme(
        packageId: String,
        metadata: StoryMetadata
    ): String = buildString {
        append("develop.uganda AUTO STORY PACKAGE • V233\n\n")
        append("PACKAGE ID • $packageId\n")
        append("CAMERA • ${metadata.camera}\n")
        append("REPORTER • ${metadata.reporter}\n")
        append("STORY • ${metadata.storyId}\n")
        append("TITLE • ${metadata.title}\n")
        if (metadata.slateEnabled) {
            append("PROJECT • ${metadata.projectName}\n")
            append("CAMERA NAME • ${metadata.cameraName}\n")
            append("SLATE • S%03d T%03d\n".format(Locale.US, metadata.slateScene, metadata.slateTake))
        }
        append("\n")
        append("This folder is generated after a successful recording. ORIGINAL_VIDEO.mp4 is a package copy; the Gallery source remains untouched.\n\n")
        append("CAPTION_DRAFT and TRANSCRIPT_DRAFT are drafts and must be reviewed before publication. AUTO VIEW labels can be wrong.\n\n")
        append("INTEGRITY.json uses SHA-256 only to detect later file changes. It is not a digital signature and does not prove authorship.\n")
    }

    private fun suggestedCaption(
        metadata: StoryMetadata,
        brand: DevelopUgandaBrandMetadataStore.Snapshot
    ): String {
        val subject =
            metadata.autoView
                .substringAfter(
                    "likely",
                    ""
                )
                .replace(
                    "•",
                    ","
                )
                .trim()

        val title =
            metadata.title
                .trim()
                .takeIf {
                    it.isNotBlank() &&
                        it !=
                            "--"
                }

        val place =
            metadata.place
                ?.trim()
                ?.takeIf {
                    it.isNotBlank() &&
                        !it.startsWith(
                            "Locating",
                            ignoreCase =
                                true
                        )
                }

        val sentence =
            listOfNotNull(
                title,
                subject.takeIf {
                    it.isNotBlank()
                },
                place
            )
                .joinToString(
                    " — "
                )
                .ifBlank {
                    "Field report"
                }

        val byline =
            when {
                brand.organization.isNotBlank() ->
                    "${brand.displayName} • ${brand.organization}"

                brand.displayName.isNotBlank() ->
                    brand.displayName

                else ->
                    metadata.reporter
            }

        return "DRAFT CAPTION • REVIEW BEFORE POSTING\n\n" +
            sentence +
            "\n\nField report • $byline\n" +
            "Camera • ${metadata.camera}\n" +
            "Recorded with develop.uganda • V228\n"
    }


    private fun createThumbnail(
        context: Context,
        sourceUri: Uri,
        relative: String,
        durationMs: Long?
    ): Uri? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, sourceUri)
            val targetUs = if (durationMs != null && durationMs < 2_000L) {
                0L
            } else {
                1_000_000L
            }
            val frame = retriever.getFrameAtTime(
                targetUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null
            val bytes = java.io.ByteArrayOutputStream().use { buffer ->
                frame.compress(Bitmap.CompressFormat.JPEG, 90, buffer)
                buffer.toByteArray()
            }
            frame.recycle()
            writeBytes(context, relative, "THUMBNAIL.jpg", "image/jpeg", bytes)
        } catch (_: Exception) {
            try {
                writeText(context, relative, "THUMBNAIL_STATUS.txt", "THUMBNAIL UNAVAILABLE")
            } catch (_: Exception) {
            }
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun readDurationMs(context: Context, sourceUri: Uri): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, sourceUri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sourceSizeBytes(context: Context, uri: Uri): Long? {
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.SIZE),
                null,
                null,
                null
            )?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun copyUriToPackage(
        context: Context,
        sourceUri: Uri,
        relative: String,
        displayName: String,
        mime: String
    ): Uri {
        val target = createOrReplaceTarget(context, relative, displayName, mime)
        try {
            context.contentResolver.openInputStream(sourceUri)?.use { source ->
                openOutput(context, target).use { output ->
                    source.copyTo(output, 1024 * 1024)
                }
            } ?: error("source unreadable")
            return target
        } catch (e: Exception) {
            deleteTarget(context, target)
            throw e
        }
    }

    private fun writeJson(
        context: Context,
        relative: String,
        displayName: String,
        value: JSONObject
    ): Uri = writeText(
        context,
        relative,
        displayName,
        value.toString(2) + "\n"
    )

    private fun writeText(
        context: Context,
        relative: String,
        displayName: String,
        value: String
    ): Uri = writeBytes(
        context,
        relative,
        displayName,
        if (displayName.endsWith(".json", ignoreCase = true)) {
            "application/json"
        } else {
            "text/plain"
        },
        value.toByteArray(Charsets.UTF_8)
    )

    private fun writeBytes(
        context: Context,
        relative: String,
        displayName: String,
        mime: String,
        bytes: ByteArray
    ): Uri {
        val target = createOrReplaceTarget(context, relative, displayName, mime)
        try {
            openOutput(context, target).use { it.write(bytes) }
            return target
        } catch (e: Exception) {
            deleteTarget(context, target)
            throw e
        }
    }

    private fun createOrReplaceTarget(
        context: Context,
        relative: String,
        displayName: String,
        mime: String
    ): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            findDownloadUri(context, relative, displayName)?.let { return it }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, displayName)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, relative)
            }
            return context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: error("Could not create $displayName")
        }

        val packageId = relative.trimEnd('/').substringAfterLast('/')
        val dir = legacyPackageDir(context, packageId).apply { mkdirs() }
        return Uri.fromFile(File(dir, displayName))
    }

    private fun openOutput(context: Context, target: Uri): java.io.OutputStream {
        return if (target.scheme == "file") {
            FileOutputStream(File(target.path ?: error("file path missing")), false)
        } else {
            context.contentResolver.openOutputStream(target, "rwt")
                ?: error("target not writable")
        }
    }

    private fun deleteTarget(context: Context, target: Uri) {
        try {
            if (target.scheme == "file") {
                File(target.path ?: return).delete()
            } else {
                context.contentResolver.delete(target, null, null)
            }
        } catch (_: Exception) {
        }
    }

    private fun findDownloadUri(
        context: Context,
        relative: String,
        displayName: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        return try {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID),
                "${MediaStore.Downloads.RELATIVE_PATH} = ? AND ${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf(relative, displayName),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC"
            )?.use {
                if (it.moveToFirst()) {
                    ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        it.getLong(0)
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256OfUri(context: Context, uri: Uri): String? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val input = if (uri.scheme == "file") {
                File(uri.path ?: return null).inputStream()
            } else {
                context.contentResolver.openInputStream(uri) ?: return null
            }
            input.use {
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val read = it.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") {
                String.format(Locale.US, "%02x", it)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun registerPackage(context: Context, entry: RegistryEntry) {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = try {
            JSONArray(prefs.getString(REGISTRY, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }
        val fresh = JSONArray()
        fresh.put(registryJson(entry))
        var kept = 1
        for (i in 0 until old.length()) {
            if (kept >= 30) break
            val item = old.optJSONObject(i) ?: continue
            if (item.optString("package_id") == entry.packageId) continue
            fresh.put(item)
            kept += 1
        }
        prefs.edit().putString(REGISTRY, fresh.toString()).apply()
    }

    private fun updateRegistryState(
        context: Context,
        packageId: String,
        state: String
    ) {
        val existing = listRegistry(context)
            .firstOrNull { it.packageId == packageId }
            ?: return
        registerPackage(context, existing.copy(state = state))
    }

    private fun registryJson(entry: RegistryEntry): JSONObject =
        JSONObject()
            .put("package_id", entry.packageId)
            .put("path", entry.path)
            .put("title", entry.title)
            .put("camera", entry.camera)
            .put("created_utc", entry.createdUtc)
            .put("state", entry.state)

    private fun packageRelativePath(packageId: String): String =
        "Download/develop.uganda/Story Packages/${safeSegment(packageId)}/"

    private fun legacyPackageDir(context: Context, packageId: String): File =
        File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "develop.uganda/Story Packages/${safeSegment(packageId)}"
        )

    private fun safeSegment(value: String): String =
        value.trim()
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .take(80)
            .ifBlank { "DU_${System.currentTimeMillis()}" }

    private fun showToast(context: Context, value: String) {
        main.post {
            Toast.makeText(context, value, Toast.LENGTH_LONG).show()
        }
    }
}
