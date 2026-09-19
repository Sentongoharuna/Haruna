package com.sentongoharuna.pulse

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import androidx.camera.view.PreviewView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
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
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * V235 Uganda LUT Mixer Pro.
 *
 * ORIGINAL is always preserved. The operator monitor is an optional matrix
 * approximation. COLOR_MASTER.mp4 is the real 17^3 Media3 SingleColorLut
 * render with an actual H.264/AAC re-encode.
 *
 * These are develop.uganda looks, not copies of proprietary ARRI / Sony / RED
 * LUTs. The design goals are professional highlight, contrast, saturation and
 * shadow/highlight separation while remaining safe for phone-originated video.
 */
@OptIn(UnstableApi::class)
object DevelopUgandaColorEngine {

    data class Profile(
        val id: String,
        val label: String,
        val family: String,
        val purpose: String,
        val palette: String,
        val contrast: Float,
        val saturation: Float,
        val gamma: Float,
        val blackLift: Float,
        val redGain: Float,
        val greenGain: Float,
        val blueGain: Float,
        val shadowRed: Float,
        val shadowGreen: Float,
        val shadowBlue: Float,
        val midtoneRed: Float,
        val midtoneGreen: Float,
        val midtoneBlue: Float,
        val highlightRed: Float,
        val highlightGreen: Float,
        val highlightBlue: Float,
        val cyanPush: Float,
        val orangePush: Float,
        val greenPush: Float,
        val shoulder: Float,
        val defaultStrength: Int
    )

    data class ResolvedSelection(
        val requestedId: String,
        val profile: Profile?,
        val strength: Int,
        val autoResolved: Boolean
    ) {
        val enabled: Boolean
            get() = profile != null

        val label: String
            get() = profile?.let { DevelopUgandaColorTuner.displayName(it.id, it.label) } ?: "ORIGINAL"

        fun statusLabel(): String =
            if (autoResolved && profile != null) {
                "AUTO → $label"
            } else {
                label
            }
    }

    data class ExportOutcome(
        val success: Boolean,
        val uri: Uri?,
        val profileLabel: String,
        val strength: Int,
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
        val bitrate: Long?,
        val message: String
    )

    private data class VideoInfo(
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
        val bitrate: Long?
    )

    const val ID_AUTO = "AUTO"
    const val ID_ORIGINAL = "ORIGINAL"

    private const val PREFS = "develop_uganda_v230_color_engine"
    private const val KEY_MONITOR = "monitor_enabled"

    private val main = Handler(Looper.getMainLooper())
    private val activeTransformers = mutableSetOf<Transformer>()

    val profiles = listOf(
        Profile(
            "CINEMA_NATURAL",
            "DU CINEMA NATURAL",
            "CORE CINEMA",
            "Neutral cinema master • real materials • gentle highlight roll-off",
            "NATURAL • STEEL BLUE • WARM SKIN",
            1.06f, 0.96f, 1.02f, 0.008f,
            1.010f, 1.000f, 0.990f,
            -0.008f, 0.004f, 0.012f,
            0.004f, 0.001f, -0.004f,
            0.015f, 0.007f, -0.010f,
            0.008f, 0.006f, 0.000f,
            2.10f, 90
        ),
        Profile(
            "PRO_RICH_NATURAL",
            "DU PRO RICH NATURAL",
            "PRO IMAGE",
            "Richer natural colour • protected highlights • clean skin and concrete",
            "TRUE COLOR • WARM MIDTONE • NEUTRAL WHITE • CONTROLLED GREEN",
            1.08f, 1.09f, 1.01f, 0.006f,
            1.018f, 1.004f, 0.992f,
            -0.003f, 0.003f, 0.010f,
            0.007f, 0.002f, -0.003f,
            0.016f, 0.008f, -0.008f,
            0.006f, 0.018f, 0.006f,
            2.15f, 92
        ),
        Profile(
            "COOL_CINEMA",
            "DU COOL CINEMA",
            "CORE CINEMA",
            "Clearly cooler shadows • cyan/steel separation • clean highlights",
            "COOL STEEL • CYAN SHADOW • NEUTRAL WHITE",
            1.08f, 0.94f, 1.01f, 0.006f,
            0.985f, 1.005f, 1.035f,
            -0.025f, 0.006f, 0.035f,
            -0.010f, 0.002f, 0.014f,
            0.008f, 0.002f, -0.006f,
            0.025f, 0.003f, 0.000f,
            2.00f, 88
        ),
        Profile(
            "FILM_BIAS",
            "DU FILM BIAS",
            "CORE CINEMA",
            "Visible warm highlights • cool shadows • denser film-style contrast",
            "COOL SHADOW • AMBER HIGHLIGHT • DENSE BLACK",
            1.11f, 0.95f, 1.03f, 0.008f,
            1.020f, 0.995f, 0.980f,
            -0.018f, 0.000f, 0.025f,
            0.006f, 0.000f, -0.006f,
            0.035f, 0.012f, -0.022f,
            0.012f, 0.022f, 0.000f,
            2.40f, 86
        ),
        Profile(
            "URBAN_TEAL",
            "DU URBAN TEAL",
            "SIGNATURE CINEMA",
            "Teal/cyan shadows • warm orange highlights • strong city-film separation",
            "#0F354E • #1E7B93 • #D94C2E • #9B243A",
            1.15f, 1.02f, 0.99f, 0.004f,
            0.960f, 1.010f, 1.055f,
            -0.050f, 0.018f, 0.075f,
            -0.018f, 0.006f, 0.025f,
            0.040f, 0.015f, -0.030f,
            0.055f, 0.045f, 0.000f,
            2.50f, 86
        ),
        Profile(
            "GOLDEN_CITY",
            "DU GOLDEN CITY",
            "SIGNATURE CINEMA",
            "Golden sunset/highlights • steel-blue shadows • rich skyline separation",
            "#F39E4C • #C14615 • #6E90A0 • #586D75 • #25292A",
            1.12f, 1.05f, 0.99f, 0.004f,
            1.045f, 1.006f, 0.960f,
            -0.010f, 0.000f, 0.018f,
            0.012f, 0.004f, -0.012f,
            0.060f, 0.025f, -0.045f,
            0.012f, 0.065f, 0.000f,
            2.60f, 84
        ),
        Profile(
            "STEEL_FIRE",
            "DU STEEL + FIRE",
            "SIGNATURE CINEMA",
            "Deep steel shadows • hot orange/fire highlights • machinery and welding look",
            "#181A19 • #283842 • #314C56 • #CD371C • #F29F2F",
            1.18f, 1.05f, 0.98f, 0.002f,
            0.970f, 1.000f, 1.035f,
            -0.040f, 0.010f, 0.050f,
            -0.015f, 0.000f, 0.015f,
            0.075f, 0.025f, -0.060f,
            0.040f, 0.080f, 0.000f,
            2.80f, 80
        ),
        Profile(
            "RETRO_AMBER",
            "DU RETRO AMBER",
            "SIGNATURE CINEMA",
            "Deep teal/green blacks • amber lamps • vintage music/garage atmosphere",
            "DEEP TEAL • BURNT ORANGE • AMBER • WARM METAL",
            1.13f, 0.92f, 1.02f, 0.006f,
            1.040f, 1.000f, 0.960f,
            -0.025f, 0.012f, 0.012f,
            0.010f, 0.000f, -0.020f,
            0.065f, 0.030f, -0.055f,
            0.020f, 0.080f, 0.010f,
            2.80f, 82
        ),
        Profile(
            "BURGUNDY_CINEMA",
            "DU BURGUNDY CINEMA",
            "SIGNATURE CINEMA",
            "Burgundy/plum shadows • bronze highlights • dramatic interiors and night",
            "#2D0E1E • #4A052D • #651A29 • #9B6230",
            1.14f, 0.97f, 1.02f, 0.004f,
            1.040f, 0.970f, 0.985f,
            0.015f, -0.025f, 0.015f,
            0.025f, -0.015f, 0.005f,
            0.045f, 0.005f, -0.025f,
            0.000f, 0.030f, 0.000f,
            2.60f, 82
        ),
        Profile(
            "OLIVE_DOCUMENTARY",
            "DU OLIVE DOCUMENTARY",
            "SIGNATURE CINEMA",
            "Muted olive earth tones • pale blue air • documentary film character",
            "#5B5540 • #AE9D7F • #4A4D1F • #A49B47 • #94B4C0",
            1.07f, 0.82f, 1.03f, 0.016f,
            1.015f, 1.008f, 0.980f,
            0.005f, 0.010f, -0.006f,
            0.015f, 0.012f, -0.020f,
            0.020f, 0.015f, -0.008f,
            0.008f, 0.010f, 0.025f,
            2.10f, 88
        ),
        Profile(
            "DEEP_SPACE",
            "DU DEEP SPACE",
            "SIGNATURE CINEMA",
            "Navy/plum depth • controlled gold • premium low-light cinematic character",
            "#45223B • #16173A • #2A4E63 • #697763 • #B07D3A",
            1.16f, 0.96f, 1.02f, 0.002f,
            0.970f, 0.990f, 1.050f,
            0.015f, -0.030f, 0.045f,
            -0.010f, -0.010f, 0.020f,
            0.050f, 0.015f, -0.030f,
            0.025f, 0.035f, 0.000f,
            3.00f, 80
        ),
        Profile(
            "CLEAN_SOCIAL",
            "DU CLEAN SOCIAL",
            "DELIVERY / SUBJECT",
            "Bright clean TikTok/Reels delivery • moderate contrast • controlled color",
            "CLEAN WHITE • CLEAR BLUE • NATURAL WARMTH",
            1.05f, 0.98f, 0.99f, 0.012f,
            1.010f, 1.010f, 1.005f,
            -0.002f, 0.002f, 0.008f,
            0.002f, 0.002f, 0.002f,
            0.012f, 0.008f, -0.004f,
            0.006f, 0.008f, 0.000f,
            1.90f, 94
        ),
        Profile(
            "CONSTRUCTION",
            "DU CONSTRUCTION",
            "DELIVERY / SUBJECT",
            "Concrete/steel clarity • cooler structural shadows • controlled yellow/orange",
            "CONCRETE • STEEL • SAFETY ORANGE • CLEAN WHITE",
            1.12f, 0.92f, 0.99f, 0.004f,
            0.995f, 1.005f, 1.015f,
            -0.015f, 0.005f, 0.025f,
            -0.004f, 0.002f, 0.006f,
            0.020f, 0.006f, -0.012f,
            0.012f, 0.012f, -0.004f,
            2.30f, 90
        ),
        Profile(
            "PEOPLE",
            "DU PEOPLE",
            "DELIVERY / SUBJECT",
            "Skin-first rendering • restrained reds • soft highlight roll-off",
            "NATURAL SKIN • SOFT WHITE • CONTROLLED RED",
            1.03f, 0.93f, 1.02f, 0.014f,
            1.018f, 1.004f, 0.990f,
            0.002f, 0.003f, 0.004f,
            0.004f, 0.003f, 0.000f,
            0.018f, 0.008f, -0.006f,
            0.000f, 0.006f, 0.000f,
            2.20f, 90
        ),
        Profile(
            "NIGHT_CINEMA",
            "DU NIGHT CINEMA",
            "DELIVERY / SUBJECT",
            "Lifted dark detail • cool shadow depth • restrained low-light chroma",
            "NAVY SHADOW • COOL BLACK • CONTROLLED WARM LIGHT",
            0.99f, 0.80f, 1.06f, 0.028f,
            0.980f, 0.995f, 1.040f,
            -0.030f, 0.008f, 0.050f,
            -0.005f, 0.000f, 0.010f,
            0.010f, 0.000f, -0.005f,
            0.030f, 0.005f, 0.000f,
            1.80f, 94
        ),
        Profile(
            "SOFT_FILM",
            "DU SOFT FILM",
            "DELIVERY / SUBJECT",
            "Low-contrast documentary • muted chroma • soft highlight shoulder",
            "MUTED NEUTRAL • OPEN SHADOW • SOFT WHITE",
            0.96f, 0.82f, 1.025f, 0.025f,
            1.004f, 1.000f, 0.996f,
            -0.002f, 0.002f, 0.008f,
            0.000f, 0.002f, 0.002f,
            0.008f, 0.004f, -0.005f,
            0.004f, 0.003f, 0.000f,
            1.60f, 90
        ),
        Profile(
            "MONO_CINEMA",
            "DU MONO CINEMA",
            "DELIVERY / SUBJECT",
            "Luminance monochrome • lifted blacks • cinema contrast",
            "BLACK • SILVER • WHITE",
            1.10f, 0.00f, 1.00f, 0.010f,
            1.000f, 1.000f, 1.000f,
            0.000f, 0.000f, 0.000f,
            0.000f, 0.000f, 0.000f,
            0.000f, 0.000f, 0.000f,
            0.000f, 0.000f, 0.000f,
            2.30f, 92
        )
    )

    fun menuLabels(): Array<String> =
        buildList {
            add("AUTO • CAMERA / SCENE")
            add("ORIGINAL • NO COLOR MASTER")
            profiles.forEach { add(DevelopUgandaColorTuner.displayName(it.id, it.label)) }
        }.toTypedArray()

    fun selectedMenuIndex(
        context: Context,
        scope: String
    ): Int {
        val id = selectedId(context, scope)

        return when (id) {
            ID_AUTO -> 0
            ID_ORIGINAL -> 1
            else -> profiles.indexOfFirst { it.id == id }
                .takeIf { it >= 0 }
                ?.plus(2)
                ?: 0
        }
    }

    fun setSelectedMenuIndex(
        context: Context,
        scope: String,
        index: Int
    ) {
        val id = when (index) {
            0 -> ID_AUTO
            1 -> ID_ORIGINAL
            else -> profiles.getOrNull(index - 2)?.id ?: ID_AUTO
        }

        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(profileKey(scope), id)
            .apply()
    }

    fun selectedStrength(
        context: Context,
        scope: String,
        resolvedProfile: Profile? = null
    ): Int {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = strengthKey(scope)

        if (prefs.contains(key)) {
            return prefs.getInt(key, 85).coerceIn(0, 100)
        }

        return resolvedProfile?.defaultStrength ?: 85
    }

    fun setStrength(
        context: Context,
        scope: String,
        strength: Int
    ) {
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(strengthKey(scope), strength.coerceIn(0, 100))
            .apply()
    }

    fun monitorEnabled(context: Context): Boolean =
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_MONITOR, true)

    fun setMonitorEnabled(context: Context, enabled: Boolean) {
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_MONITOR, enabled)
            .apply()
    }

    fun resolve(
        context: Context,
        scope: String,
        hint: String
    ): ResolvedSelection {
        val requested = selectedId(context, scope)

        if (requested == ID_ORIGINAL) {
            return ResolvedSelection(requested, null, 0, false)
        }

        val profile = if (requested == ID_AUTO) {
            autoProfile(hint)
        } else {
            profiles.firstOrNull { it.id == requested } ?: autoProfile(hint)
        }

        return ResolvedSelection(
            requestedId = requested,
            profile = profile,
            strength = selectedStrength(context, scope, profile),
            autoResolved = requested == ID_AUTO
        )
    }

    /**
     * Optional operator monitor approximation only. It is deliberately OFF by
     * default so V235 keeps the live operator preview graded while the original camera master stays untouched an already proven CameraX preview.
     */
    fun applyPreviewMonitor(
        previewView: PreviewView,
        selection: ResolvedSelection
    ) {
        applyPreviewMonitor(
            previewView,
            selection,
            "GLOBAL"
        )
    }

    /**
     * V235 live grade monitor. The monitor remains an operator preview and is
     * never burned into the original CameraX master. The selected scene LUT,
     * master strength and the five scene palette controls plus six everyday colors are applied to
     * the camera preview immediately. COLOR_MASTER.mp4 still uses the real
     * tuned 17^3 Media3 LUT render.
     */
    fun applyPreviewMonitor(
        previewView: PreviewView,
        selection: ResolvedSelection,
        scope: String
    ) {
        if (!monitorEnabled(previewView.context) || selection.profile == null) {
            clearPreviewMonitor(previewView)
            return
        }

        val tuning =
            DevelopUgandaColorTuner.load(
                previewView.context,
                scope,
                selection.profile.id
            )

        val everyday =
            DevelopUgandaEverydayColorMixer.load(
                previewView.context,
                scope,
                selection.profile.id
            )

        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(
                previewMatrix(
                    selection.profile,
                    selection.strength / 100f,
                    tuning,
                    everyday
                )
            )
        }

        previewView.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }

    fun clearPreviewMonitor(previewView: PreviewView) {
        previewView.setLayerType(View.LAYER_TYPE_NONE, null)
    }

    fun createLutEffect(selection: ResolvedSelection): SingleColorLut? {
        val profile = selection.profile ?: return null
        return SingleColorLut.createFromCube(
            buildCube(
                profile,
                selection.strength,
                17,
                DevelopUgandaColorTuner.neutral(profile.id),
                DevelopUgandaEverydayColorMixer.neutral()
            )
        )
    }

    private fun createLutEffect(
        context: Context,
        scope: String,
        selection: ResolvedSelection
    ): SingleColorLut? {
        val profile = selection.profile ?: return null
        val tuning = DevelopUgandaColorTuner.load(context, scope, profile.id)
        val everyday =
            DevelopUgandaEverydayColorMixer.load(
                context,
                scope,
                profile.id
            )
        return SingleColorLut.createFromCube(
            buildCube(
                profile,
                selection.strength,
                17,
                tuning,
                everyday
            )
        )
    }

    fun exportVideoMaster(
        context: Context,
        inputUri: Uri,
        packageId: String,
        scope: String,
        hint: String,
        callback: (ExportOutcome) -> Unit
    ) {
        val selection = resolve(context, scope, hint)
        val profile = selection.profile

        if (profile == null) {
            callback(
                ExportOutcome(
                    false,
                    null,
                    "ORIGINAL",
                    0,
                    null,
                    null,
                    null,
                    null,
                    "Original-only color mode selected"
                )
            )
            return
        }

        val inputInfo = readVideoInfo(context, inputUri)
        val tempDir = File(context.cacheDir, "v235_color_exports").apply { mkdirs() }
        val temp = File(tempDir, "color_${System.currentTimeMillis()}.mp4")
        if (temp.exists()) temp.delete()

        val lut = createLutEffect(context, scope, selection)
        if (lut == null) {
            callback(
                ExportOutcome(
                    false,
                    null,
                    selection.label,
                    selection.strength,
                    inputInfo.width,
                    inputInfo.height,
                    inputInfo.durationMs,
                    inputInfo.bitrate,
                    "Could not create 3D LUT"
                )
            )
            return
        }

        val source = MediaItem.Builder().setUri(inputUri).build()
        val effects = Effects(emptyList(), listOf(lut))
        val edited = EditedMediaItem.Builder(source)
            .setEffects(effects)
            .build()
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(listOf(edited))
        val compositionBuilder = Composition.Builder(listOf(sequence))

        // COLOR_MASTER is a delivery copy. The original HDR source is never
        // replaced. For API 29+ Media3's OpenGL tone mapper makes an SDR master.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            compositionBuilder.setHdrMode(
                Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL
            )
        }

        val composition = compositionBuilder.build()
        val encoderFactory = DefaultEncoderFactory.Builder(context)
            .setRequestedVideoEncoderSettings(
                VideoEncoderSettings.Builder()
                    .setBitrate(targetVideoBitrate(inputInfo))
                    .setiFrameIntervalSeconds(2f)
                    .build()
            )
            .setRequestedAudioEncoderSettings(
                AudioEncoderSettings.Builder()
                    .setBitrate(256_000)
                    .build()
            )
            .build()

        var transformer: Transformer? = null

        val listener = object : Transformer.Listener {
            override fun onCompleted(
                composition: Composition,
                result: ExportResult
            ) {
                try { lut.release() } catch (_: Exception) { }

                transformer?.let {
                    synchronized(activeTransformers) {
                        activeTransformers.remove(it)
                    }
                }

                Thread {
                    try {
                        val outputInfo = readVideoInfo(temp.absolutePath)
                        val published = publishColorMaster(
                            context,
                            temp,
                            packageId,
                            selection.label
                        )
                        temp.delete()

                        main.post {
                            callback(
                                ExportOutcome(
                                    true,
                                    published,
                                    selection.label,
                                    selection.strength,
                                    outputInfo.width,
                                    outputInfo.height,
                                    outputInfo.durationMs,
                                    outputInfo.bitrate,
                                    "V235 Uganda LUT Mixer Pro • 17³ 3D LUT + palette tuning master ready"
                                )
                            )
                        }
                    } catch (e: Exception) {
                        temp.delete()
                        main.post {
                            callback(
                                ExportOutcome(
                                    false,
                                    null,
                                    selection.label,
                                    selection.strength,
                                    inputInfo.width,
                                    inputInfo.height,
                                    inputInfo.durationMs,
                                    inputInfo.bitrate,
                                    "Color export failed • original is safe • ${e.javaClass.simpleName}"
                                )
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
                try { lut.release() } catch (_: Exception) { }

                transformer?.let {
                    synchronized(activeTransformers) {
                        activeTransformers.remove(it)
                    }
                }

                temp.delete()
                callback(
                    ExportOutcome(
                        false,
                        null,
                        selection.label,
                        selection.strength,
                        inputInfo.width,
                        inputInfo.height,
                        inputInfo.durationMs,
                        inputInfo.bitrate,
                        "Media3 LUT export failed • original is safe"
                    )
                )
            }
        }

        val builtTransformer = Transformer.Builder(context)
            .setEncoderFactory(encoderFactory)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(listener)
            .build()

        transformer = builtTransformer

        synchronized(activeTransformers) {
            activeTransformers.add(builtTransformer)
        }

        builtTransformer.start(composition, temp.absolutePath)
    }

    fun profileJson(
        context: Context,
        scope: String,
        hint: String
    ): org.json.JSONObject {
        val value = resolve(context, scope, hint)

        return org.json.JSONObject()
            .put("requested_profile", value.requestedId)
            .put("resolved_profile", value.profile?.id ?: ID_ORIGINAL)
            .put("resolved_label", value.label)
            .put("profile_family", value.profile?.family ?: "ORIGINAL")
            .put("profile_palette", value.profile?.palette ?: "ORIGINAL")
            .put(
                "uganda_scene_name",
                value.profile?.let {
                    DevelopUgandaColorTuner.displayName(it.id, it.label)
                } ?: "ORIGINAL"
            )
            .put(
                "palette_controls",
                value.profile?.let {
                    DevelopUgandaColorTuner.tuningJson(context, scope, it.id)
                } ?: org.json.JSONArray()
            )
            .put(
                "everyday_color_controls",
                value.profile?.let {
                    DevelopUgandaEverydayColorMixer.tuningJson(
                        context,
                        scope,
                        it.id
                    )
                } ?: org.json.JSONArray()
            )
            .put("strength_percent", value.strength)
            .put("auto_resolved", value.autoResolved)
            .put("monitor_enabled", monitorEnabled(context))
            .put(
                "master_pipeline",
                if (value.enabled) {
                    "Media3 SingleColorLut 17^3 + Uganda scene palette + V235 everyday color mixer + H.264/AAC re-encode"
                } else {
                    "Original only"
                }
            )
            .put(
                "preview_note",
                "Optional monitor is a matrix approximation; COLOR_MASTER.mp4 uses the real 3D LUT."
            )
            .put("generated_utc", Instant.now().toString())
    }

    private fun readVideoInfo(context: Context, uri: Uri): VideoInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            VideoInfo(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            )
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun readVideoInfo(path: String): VideoInfo {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path)
            VideoInfo(
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()
            )
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun targetVideoBitrate(info: VideoInfo): Int {
        val pixels = (info.width ?: 0) * (info.height ?: 0)
        val baseline = when {
            pixels >= 8_000_000 -> 48_000_000
            pixels >= 2_000_000 -> 20_000_000
            pixels >= 900_000 -> 12_000_000
            else -> 8_000_000
        }

        val source = info.bitrate
            ?.coerceAtMost(72_000_000L)
            ?.toInt()
            ?: baseline

        return maxOf(baseline, source)
    }

    private fun publishColorMaster(
        context: Context,
        temp: File,
        packageId: String,
        profileLabel: String
    ): Uri {
        val safeProfile = profileLabel
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9]+"), "_")
            .trim('_')
            .take(28)
            .ifBlank { "COLOR" }

        val safePackage = packageId
            .uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_-]+"), "_")
            .take(42)

        val name = "DEVELOP_UGANDA_V235_${safeProfile}_${safePackage}.mp4"

        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    "Movies/develop.uganda/Color Masters"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val uri = context.contentResolver.insert(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            values
        ) ?: error("Could not create Color Master")

        try {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                FileInputStream(temp).use { input ->
                    input.copyTo(output, 1024 * 1024)
                }
            } ?: error("Could not write Color Master")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(
                    uri,
                    ContentValues().apply {
                        put(MediaStore.Video.Media.IS_PENDING, 0)
                    },
                    null,
                    null
                )
            }

            return uri
        } catch (e: Exception) {
            try { context.contentResolver.delete(uri, null, null) } catch (_: Exception) { }
            throw e
        }
    }

    private fun selectedId(context: Context, scope: String): String {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scoped = prefs.getString(profileKey(scope), null)

        if (!scoped.isNullOrBlank()) return scoped

        return prefs.getString("profile_GLOBAL", ID_AUTO) ?: ID_AUTO
    }

    private fun autoProfile(hint: String): Profile {
        val value = hint.uppercase(Locale.US)
        val id = when {
            "WELD" in value || "FIRE" in value || "SPARK" in value -> "STEEL_FIRE"
            "SUNSET" in value || "GOLDEN" in value || "SUNRISE" in value -> "GOLDEN_CITY"
            "NIGHT" in value || "LOW LIGHT" in value || "DARK" in value -> "NIGHT_CINEMA"
            "OUTDOOR" in value || "INDOOR" in value || "REPORTER" in value || "FIELD REPORT" in value -> "PRO_RICH_NATURAL"
            "CITY" in value || "STREET" in value || "URBAN" in value -> "URBAN_TEAL"
            "SOCIAL" in value || "TIKTOK" in value || "REELS" in value -> "CLEAN_SOCIAL"
            "INTERVIEW" in value || "PEOPLE" in value || "PORTRAIT" in value || "V205" in value || "V211" in value -> "PEOPLE"
            "CONSTRUCTION" in value || "BUILDING" in value || "SITE" in value -> "CONSTRUCTION"
            "DOCUMENTARY" in value || "NATURE" in value || "LANDSCAPE" in value -> "OLIVE_DOCUMENTARY"
            "MUSIC" in value || "RETRO" in value || "GARAGE" in value -> "RETRO_AMBER"
            "DRAMA" in value || "INTERIOR" in value -> "BURGUNDY_CINEMA"
            "NEWS" in value || "REPORTER" in value || "BREAKING" in value -> "CINEMA_NATURAL"
            "CINEMA" in value || "MOVIE" in value -> "PRO_RICH_NATURAL"
            else -> "PRO_RICH_NATURAL"
        }

        return profiles.first { it.id == id }
    }

    private fun previewMatrix(
        profile: Profile,
        strength: Float,
        tuning: DevelopUgandaColorTuner.Tuning,
        everyday: DevelopUgandaEverydayColorMixer.Tuning
    ): ColorMatrix {
        val sat = 1f + (profile.saturation - 1f) * strength
        val contrast = 1f + (profile.contrast - 1f) * strength
        val paletteBias =
            DevelopUgandaColorTuner.previewBias(
                profile.id,
                tuning
            )
        val everydayBias =
            DevelopUgandaEverydayColorMixer.previewBias(
                everyday
            )

        val creativeRed =
            profile.midtoneRed * 0.55f +
                profile.highlightRed * 0.25f +
                profile.shadowRed * 0.20f +
                profile.orangePush * 0.20f -
                profile.cyanPush * 0.16f +
                paletteBias[0] +
                everydayBias[0]

        val creativeGreen =
            profile.midtoneGreen * 0.55f +
                profile.highlightGreen * 0.25f +
                profile.shadowGreen * 0.20f +
                profile.cyanPush * 0.06f +
                profile.greenPush * 0.12f +
                paletteBias[1] +
                everydayBias[1]

        val creativeBlue =
            profile.midtoneBlue * 0.55f +
                profile.highlightBlue * 0.25f +
                profile.shadowBlue * 0.20f +
                profile.cyanPush * 0.16f -
                profile.orangePush * 0.12f +
                paletteBias[2] +
                everydayBias[2]

        val redGain =
            (1f + (profile.redGain - 1f) * strength) *
                (1f + creativeRed * strength)
        val greenGain =
            (1f + (profile.greenGain - 1f) * strength) *
                (1f + creativeGreen * strength)
        val blueGain =
            (1f + (profile.blueGain - 1f) * strength) *
                (1f + creativeBlue * strength)

        val inv = 1f - sat
        val rw = 0.2126f
        val gw = 0.7152f
        val bw = 0.0722f
        val offset =
            (profile.blackLift * 255f * strength) -
                ((contrast - 1f) * 127.5f)

        return ColorMatrix(
            floatArrayOf(
                (inv * rw + sat) * contrast * redGain,
                inv * gw * contrast * redGain,
                inv * bw * contrast * redGain,
                0f,
                offset,

                inv * rw * contrast * greenGain,
                (inv * gw + sat) * contrast * greenGain,
                inv * bw * contrast * greenGain,
                0f,
                offset,

                inv * rw * contrast * blueGain,
                inv * gw * contrast * blueGain,
                (inv * bw + sat) * contrast * blueGain,
                0f,
                offset,

                0f, 0f, 0f, 1f, 0f
            )
        )
    }

    private fun buildCube(
        profile: Profile,
        strengthPercent: Int,
        size: Int,
        tuning: DevelopUgandaColorTuner.Tuning,
        everyday: DevelopUgandaEverydayColorMixer.Tuning
    ): Array<Array<IntArray>> {
        val strength = strengthPercent.coerceIn(0, 100) / 100f

        return Array(size) { rIndex ->
            Array(size) { gIndex ->
                IntArray(size) { bIndex ->
                    val r = rIndex.toFloat() / (size - 1).toFloat()
                    val g = gIndex.toFloat() / (size - 1).toFloat()
                    val b = bIndex.toFloat() / (size - 1).toFloat()
                    val transformed =
                        transform(
                            profile,
                            r,
                            g,
                            b,
                            strength,
                            tuning,
                            everyday
                        )

                    Color.argb(
                        255,
                        (transformed[0] * 255f).roundToInt().coerceIn(0, 255),
                        (transformed[1] * 255f).roundToInt().coerceIn(0, 255),
                        (transformed[2] * 255f).roundToInt().coerceIn(0, 255)
                    )
                }
            }
        }
    }

    private fun transform(
        profile: Profile,
        inputR: Float,
        inputG: Float,
        inputB: Float,
        strength: Float,
        tuning: DevelopUgandaColorTuner.Tuning,
        everyday: DevelopUgandaEverydayColorMixer.Tuning
    ): FloatArray {
        val original = floatArrayOf(inputR, inputG, inputB)

        var r = tone(inputR, profile)
        var g = tone(inputG, profile)
        var b = tone(inputB, profile)

        var luma =
            (r * 0.2126f + g * 0.7152f + b * 0.0722f)
                .coerceIn(0f, 1f)

        r = luma + (r - luma) * profile.saturation
        g = luma + (g - luma) * profile.saturation
        b = luma + (b - luma) * profile.saturation

        r *= profile.redGain
        g *= profile.greenGain
        b *= profile.blueGain

        luma =
            (r * 0.2126f + g * 0.7152f + b * 0.0722f)
                .coerceIn(0f, 1f)

        val shadowWeight =
            (1f - luma).coerceIn(0f, 1f).pow(2.20f)
        val highlightWeight =
            luma.coerceIn(0f, 1f).pow(2.05f)
        val midtoneWeight =
            (1f - kotlin.math.abs(luma * 2f - 1f))
                .coerceIn(0f, 1f)
                .pow(1.35f)

        r +=
            profile.shadowRed * shadowWeight +
                profile.midtoneRed * midtoneWeight +
                profile.highlightRed * highlightWeight
        g +=
            profile.shadowGreen * shadowWeight +
                profile.midtoneGreen * midtoneWeight +
                profile.highlightGreen * highlightWeight
        b +=
            profile.shadowBlue * shadowWeight +
                profile.midtoneBlue * midtoneWeight +
                profile.highlightBlue * highlightWeight

        val maxChannel = maxOf(r, g, b)
        val minChannel = minOf(r, g, b)
        val chroma = (maxChannel - minChannel).coerceIn(0f, 1f)

        val cyanMask =
            ((((g + b) * 0.5f) - r) * 2.1f)
                .coerceIn(0f, 1f) *
                (0.35f + chroma * 0.65f)
        val warmMask =
            ((r - b) * 1.8f)
                .coerceIn(0f, 1f) *
                (0.30f + chroma * 0.70f)
        val greenMask =
            ((g - maxOf(r, b)) * 2.2f)
                .coerceIn(0f, 1f) *
                (0.35f + chroma * 0.65f)

        val cyanWeight =
            cyanMask * (0.38f + (1f - luma) * 0.62f)
        val orangeWeight =
            warmMask * (0.30f + luma * 0.70f)
        val greenWeight =
            greenMask * (0.45f + midtoneWeight * 0.55f)

        r -= profile.cyanPush * cyanWeight
        g += profile.cyanPush * 0.35f * cyanWeight
        b += profile.cyanPush * 0.70f * cyanWeight

        r += profile.orangePush * orangeWeight
        g += profile.orangePush * 0.35f * orangeWeight
        b -= profile.orangePush * 0.55f * orangeWeight

        r += profile.greenPush * 0.16f * greenWeight
        g += profile.greenPush * greenWeight
        b -= profile.greenPush * 0.24f * greenWeight

        val baseline = floatArrayOf(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )

        // V235 keeps the authored V230 LUT at 100% per swatch, then lets the
        // operator independently subtract/add each palette family from 0–200%.
        val tuned = DevelopUgandaColorTuner.applyPalette(
            profile.id,
            baseline[0],
            baseline[1],
            baseline[2],
            tuning
        )

        val everydayTuned =
            DevelopUgandaEverydayColorMixer.apply(
                tuned[0],
                tuned[1],
                tuned[2],
                everyday
            )

        return floatArrayOf(
            mix(original[0], everydayTuned[0], strength),
            mix(original[1], everydayTuned[1], strength),
            mix(original[2], everydayTuned[2], strength)
        )
    }

    private fun tone(value: Float, profile: Profile): Float {
        var x = value.coerceIn(0f, 1f)
        x = x.pow(1f / profile.gamma.coerceAtLeast(0.2f))
        x = 0.5f + (x - 0.5f) * profile.contrast
        x = profile.blackLift + x * (1f - profile.blackLift)

        if (x > 0.68f) {
            val over = x - 0.68f
            x =
                0.68f +
                    over /
                    (1f + over * profile.shoulder.coerceAtLeast(0.6f))
        }

        return x.coerceIn(0f, 1f)
    }

    private fun mix(a: Float, b: Float, t: Float): Float =
        (a + (b - a) * t).coerceIn(0f, 1f)

    private fun profileKey(scope: String): String = "profile_${safeScope(scope)}"
    private fun strengthKey(scope: String): String = "strength_${safeScope(scope)}"

    private fun safeScope(value: String): String =
        value.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_]+"), "_")
            .take(48)
            .ifBlank { "GLOBAL" }
}
