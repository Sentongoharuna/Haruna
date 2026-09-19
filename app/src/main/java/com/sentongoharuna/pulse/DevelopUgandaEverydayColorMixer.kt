package com.sentongoharuna.pulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * V235 Everyday Color Mixer.
 *
 * These six plain-language controls sit after the authored Uganda Scene LUT.
 * 100% preserves the LUT, 0% gently removes that color family, and 200%
 * increases it. Values are saved independently per LUT and camera scope.
 */
object DevelopUgandaEverydayColorMixer {

    data class Family(
        val name: String,
        val hex: String,
        val example: String
    )

    data class Tuning(
        val values: IntArray
    ) {
        fun value(index: Int): Int =
            values.getOrNull(index)?.coerceIn(0, 200) ?: 100

        fun isDefault(): Boolean =
            values.all { it == 100 }
    }

    private const val PREFS = "develop_uganda_v235_everyday_color_mixer"
    private const val DEFAULT = 100

    val families = listOf(
        Family("GREEN", "#3EA65A", "salad • leaves • vegetation"),
        Family("RED", "#D93D35", "meat • signs • clothes"),
        Family("YELLOW", "#F2C84B", "banana • sun • warm lights"),
        Family("BLUE", "#3F83D5", "sky • water • cool glass"),
        Family("ORANGE", "#E97D32", "food • brick • warm light"),
        Family("BROWN", "#7B5736", "earth • wood • coffee")
    )

    fun load(
        context: Context,
        scope: String,
        profileId: String
    ): Tuning {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Tuning(
            IntArray(families.size) { index ->
                prefs.getInt(key(scope, profileId, index), DEFAULT)
                    .coerceIn(0, 200)
            }
        )
    }

    fun neutral(): Tuning =
        Tuning(IntArray(families.size) { DEFAULT })

    fun set(
        context: Context,
        scope: String,
        profileId: String,
        index: Int,
        value: Int
    ) {
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(
                key(scope, profileId, index),
                value.coerceIn(0, 200)
            )
            .apply()
    }

    fun reset(
        context: Context,
        scope: String,
        profileId: String
    ) {
        val editor =
            context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()

        families.indices.forEach { index ->
            editor.remove(key(scope, profileId, index))
        }

        editor.apply()
    }

    fun tuningJson(
        context: Context,
        scope: String,
        profileId: String
    ): JSONArray {
        val tuning = load(context, scope, profileId)
        return JSONArray().apply {
            families.forEachIndexed { index, family ->
                put(
                    JSONObject()
                        .put("name", family.name)
                        .put("hex", family.hex)
                        .put("example", family.example)
                        .put("amount_percent", tuning.value(index))
                )
            }
        }
    }

    /**
     * Small matrix approximation used only by the live operator monitor.
     * The final COLOR_MASTER uses apply() inside the real 17^3 LUT.
     */
    fun previewBias(tuning: Tuning): FloatArray {
        var rBias = 0f
        var gBias = 0f
        var bBias = 0f

        families.forEachIndexed { index, family ->
            val delta = (tuning.value(index) - DEFAULT) / 100f
            if (abs(delta) < 0.001f) return@forEachIndexed

            val rgb = rgb(family.hex)
            val neutral =
                rgb[0] * 0.2126f +
                    rgb[1] * 0.7152f +
                    rgb[2] * 0.0722f

            rBias += (rgb[0] - neutral) * delta * 0.055f
            gBias += (rgb[1] - neutral) * delta * 0.055f
            bBias += (rgb[2] - neutral) * delta * 0.055f
        }

        return floatArrayOf(
            rBias.coerceIn(-0.10f, 0.10f),
            gBias.coerceIn(-0.10f, 0.10f),
            bBias.coerceIn(-0.10f, 0.10f)
        )
    }

    /**
     * Hue-family adjustment for the final 17^3 LUT.
     *
     * This deliberately uses broad, overlapping masks rather than razor-sharp
     * hue keys, which helps phone footage avoid banding and unnatural edges.
     */
    fun apply(
        inputR: Float,
        inputG: Float,
        inputB: Float,
        tuning: Tuning
    ): FloatArray {
        var r = inputR.coerceIn(0f, 1f)
        var g = inputG.coerceIn(0f, 1f)
        var b = inputB.coerceIn(0f, 1f)

        val maxC = max(r, max(g, b))
        val minC = minOf(r, g, b)
        val chroma = (maxC - minC).coerceIn(0f, 1f)
        val luma =
            (r * 0.2126f + g * 0.7152f + b * 0.0722f)
                .coerceIn(0f, 1f)
        val mid =
            (1f - abs(luma * 2f - 1f))
                .coerceIn(0f, 1f)

        val greenMask =
            ((g - max(r, b)) * 2.7f)
                .coerceIn(0f, 1f) *
                (0.35f + chroma * 0.65f)

        val redMask =
            ((r - max(g, b)) * 2.5f)
                .coerceIn(0f, 1f) *
                (0.35f + chroma * 0.65f)

        val yellowMask =
            ((minOf(r, g) - b) * 2.3f)
                .coerceIn(0f, 1f) *
                (0.30f + chroma * 0.70f) *
                (0.55f + luma * 0.45f)

        val blueMask =
            ((b - max(r, g)) * 2.5f)
                .coerceIn(0f, 1f) *
                (0.35f + chroma * 0.65f)

        val warmMask =
            ((r - b) * 1.85f)
                .coerceIn(0f, 1f)

        val orangeMask =
            warmMask *
                ((g - b) * 2.0f).coerceIn(0f, 1f) *
                (0.38f + chroma * 0.62f) *
                (0.45f + mid * 0.55f)

        val brownMask =
            warmMask *
                (1f - luma).coerceIn(0f, 1f) *
                (0.35f + mid * 0.65f) *
                (0.30f + chroma * 0.70f)

        val masks = floatArrayOf(
            greenMask,
            redMask,
            yellowMask,
            blueMask,
            orangeMask,
            brownMask
        )

        families.forEachIndexed { index, family ->
            val delta = (tuning.value(index) - DEFAULT) / 100f
            val mask = masks[index].coerceIn(0f, 1f)
            if (abs(delta) < 0.001f || mask < 0.001f) {
                return@forEachIndexed
            }

            val target = rgb(family.hex)

            if (delta > 0f) {
                val weight = (mask * delta * 0.24f).coerceIn(0f, 0.28f)
                r = mix(r, target[0], weight)
                g = mix(g, target[1], weight)
                b = mix(b, target[2], weight)
            } else {
                val neutral =
                    (r * 0.2126f + g * 0.7152f + b * 0.0722f)
                        .coerceIn(0f, 1f)
                val weight =
                    (mask * -delta * 0.58f)
                        .coerceIn(0f, 0.62f)

                r = mix(r, neutral, weight)
                g = mix(g, neutral, weight)
                b = mix(b, neutral, weight)
            }
        }

        return floatArrayOf(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )
    }

    private fun rgb(hex: String): FloatArray {
        val clean = hex.removePrefix("#")
        val value = clean.toLong(16)
        return floatArrayOf(
            ((value shr 16) and 0xFF) / 255f,
            ((value shr 8) and 0xFF) / 255f,
            (value and 0xFF) / 255f
        )
    }

    private fun mix(a: Float, b: Float, t: Float): Float =
        (a + (b - a) * t).coerceIn(0f, 1f)

    private fun key(
        scope: String,
        profileId: String,
        index: Int
    ): String =
        "${safe(scope)}_${safe(profileId)}_$index"

    private fun safe(value: String): String =
        value.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_]+"), "_")
            .take(48)
            .ifBlank { "GLOBAL" }
}
