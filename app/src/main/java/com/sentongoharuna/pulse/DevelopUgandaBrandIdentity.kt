package com.sentongoharuna.pulse

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import java.util.Locale

/** Shared BRAND-only identity package for every camera mode and assembly. */
@OptIn(UnstableApi::class)
object DevelopUgandaBrandIdentity {
    val lowerThirdStyles = arrayOf("BREAKING", "CLEAN", "URGENT", "MINIMAL")

    fun overlays(
        context: Context,
        identity: DevelopUgandaFivemods12ModeIdentity?,
        takeId: String,
        durationMs: Long,
        includeEndCard: Boolean,
    ): List<TextureOverlay> {
        val brand = DevelopUgandaBrandMetadataStore.snapshot(context)
        val codeAndName = identity?.burnLabel ?: "MODE IDENTITY UNKNOWN"
        val lowerEnd = minOf(durationMs.coerceAtLeast(1L), 6_000L)
        val endStart = (durationMs - 3_000L).coerceAtLeast(0L)
        return buildList {
            add(
                DevelopUgandaTimedBrandTextOverlay(
                    "DU",
                    0L,
                    durationMs.coerceAtLeast(1L),
                    anchor = StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(0.94f, -0.92f)
                        .setOverlayFrameAnchor(1f, -1f)
                        .setScale(0.62f, 0.62f)
                        .build(),
                    size = 34,
                ),
            )
            add(
                DevelopUgandaTimedBrandTextOverlay(
                    "${brand.displayName.uppercase(Locale.US)}  |  $codeAndName\nTAKE $takeId",
                    0L,
                    lowerEnd,
                    anchor = StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(-0.94f, 0.82f)
                        .setOverlayFrameAnchor(-1f, 1f)
                        .setScale(0.58f, 0.58f)
                        .build(),
                    size = 30,
                ),
            )
            if (includeEndCard) {
                add(
                    DevelopUgandaTimedBrandTextOverlay(
                        buildString {
                            append(brand.displayName)
                            brand.organization.takeIf { it.isNotBlank() }?.let { append('\n').append(it) }
                            append("\nCONTACT • develop.uganda")
                            append("\n").append(codeAndName)
                        },
                        endStart,
                        durationMs.coerceAtLeast(1L),
                        anchor = StaticOverlaySettings.Builder()
                            .setBackgroundFrameAnchor(0f, 0f)
                            .setOverlayFrameAnchor(0f, 0f)
                            .setScale(0.72f, 0.72f)
                            .build(),
                        size = 38,
                    ),
                )
            }
        }
    }
}

/** Empty outside its measured interval; no second renderer or encoder. */
@OptIn(UnstableApi::class)
class DevelopUgandaTimedBrandTextOverlay(
    private val value: String,
    private val startMs: Long,
    private val endMs: Long,
    private val anchor: OverlaySettings,
    private val size: Int,
) : TextOverlay() {
    override fun getText(presentationTimeUs: Long): SpannableString {
        val timeMs = presentationTimeUs / 1_000L
        val visible = if (timeMs in startMs until endMs) value else ""
        return SpannableString(visible).apply {
            if (isEmpty()) return@apply
            setSpan(ForegroundColorSpan(DevelopUgandaFivemods8Theme.content), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(AbsoluteSizeSpan(size), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(TypefaceSpan("monospace"), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = anchor
}
