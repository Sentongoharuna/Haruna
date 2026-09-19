package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * V232 Uganda Scene Palette Tuner.
 *
 * Each V230 LUT keeps its authored baseline at 100%. The five palette controls
 * are independent per scene/profile and per camera scope. 0% subtracts that
 * palette bias, 100% preserves the authored V230 recipe and 200% doubles the
 * additional palette emphasis. The original recording is never modified.
 */
object DevelopUgandaColorTuner {

    enum class Role {
        SHADOW,
        AMBIENT,
        MIDTONE,
        HIGHLIGHT,
        ACCENT
    }

    data class Slot(
        val name: String,
        val hex: String,
        val role: Role
    )

    data class SceneStyle(
        val profileId: String,
        val displayName: String,
        val family: String,
        val bestFor: String,
        val slots: List<Slot>
    )

    data class Tuning(
        val values: IntArray
    ) {
        fun value(index: Int): Int = values.getOrNull(index)?.coerceIn(0, 200) ?: 100
        fun isDefault(): Boolean = values.all { it == 100 }
    }

    private const val PREFS = "develop_uganda_v231_palette_tuner"
    private const val DEFAULT = 100

    val styles = listOf(
        SceneStyle(
            "CINEMA_NATURAL",
            "KAMPALA NATURAL 01",
            "UGANDA CORE",
            "Neutral daylight • buildings • people • everyday field reporting",
            listOf(
                Slot("DEEP STEEL", "#283842", Role.SHADOW),
                Slot("CITY GREY", "#566A74", Role.AMBIENT),
                Slot("EARTH", "#697763", Role.MIDTONE),
                Slot("WARM SKIN", "#AE9D7F", Role.HIGHLIGHT),
                Slot("CLEAN WHITE", "#D1D3E0", Role.ACCENT)
            )
        ),
        SceneStyle(
            "COOL_CINEMA",
            "CITY RAIN 01",
            "UGANDA CORE",
            "Cloudy Kampala • rain • steel • glass • cool modern streets",
            listOf(
                Slot("INK BLUE", "#0C263E", Role.SHADOW),
                Slot("CITY BLUE", "#2D475A", Role.AMBIENT),
                Slot("STEEL", "#566A74", Role.MIDTONE),
                Slot("COOL SKY", "#95BCD0", Role.HIGHLIGHT),
                Slot("CLEAN WHITE", "#D1D3E0", Role.ACCENT)
            )
        ),
        SceneStyle(
            "FILM_BIAS",
            "KAMPALA FILM 01",
            "UGANDA CORE",
            "General cinema • denser blacks • warm highlights • documentary film feel",
            listOf(
                Slot("NAVY", "#16173A", Role.SHADOW),
                Slot("STEEL BLUE", "#314C56", Role.AMBIENT),
                Slot("OLIVE EARTH", "#697763", Role.MIDTONE),
                Slot("BRONZE", "#B07D3A", Role.HIGHLIGHT),
                Slot("CREAM", "#E7D6C1", Role.ACCENT)
            )
        ),
        SceneStyle(
            "URBAN_TEAL",
            "KAMPALA STREET 01",
            "KAMPALA CITY",
            "Traffic • streets • glass • shops • strong teal/orange city separation",
            listOf(
                Slot("DEEP TEAL", "#0F354E", Role.SHADOW),
                Slot("CYAN", "#1E7B93", Role.AMBIENT),
                Slot("SKY", "#95BCD0", Role.MIDTONE),
                Slot("ORANGE", "#D94C2E", Role.HIGHLIGHT),
                Slot("BURGUNDY", "#9B243A", Role.ACCENT)
            )
        ),
        SceneStyle(
            "GOLDEN_CITY",
            "NAKASERO GOLD 01",
            "KAMPALA CITY",
            "Sunset skyline • warm facades • city lights • premium golden-hour streets",
            listOf(
                Slot("CHARCOAL", "#25292A", Role.SHADOW),
                Slot("STEEL", "#586D75", Role.AMBIENT),
                Slot("CITY BLUE", "#6E90A0", Role.MIDTONE),
                Slot("BURNT ORANGE", "#C14615", Role.HIGHLIGHT),
                Slot("GOLD", "#F39E4C", Role.ACCENT)
            )
        ),
        SceneStyle(
            "STEEL_FIRE",
            "CLOCK TOWER STEEL 01",
            "CONSTRUCTION / FIELD",
            "Construction • welding • machinery • concrete • sparks • hot work",
            listOf(
                Slot("BLACK STEEL", "#181A19", Role.SHADOW),
                Slot("DARK STEEL", "#283842", Role.AMBIENT),
                Slot("BLUE STEEL", "#314C56", Role.MIDTONE),
                Slot("FIRE RED", "#CD371C", Role.HIGHLIGHT),
                Slot("SPARK GOLD", "#F29F2F", Role.ACCENT)
            )
        ),
        SceneStyle(
            "RETRO_AMBER",
            "KISENYI AMBER 01",
            "MARKET / INTERIOR",
            "Markets • workshops • warm bulbs • indoor stalls • vintage atmosphere",
            listOf(
                Slot("DEEP TEAL", "#0F354E", Role.SHADOW),
                Slot("MARKET BLUE", "#2D475A", Role.AMBIENT),
                Slot("BRONZE", "#C2852D", Role.MIDTONE),
                Slot("ORANGE", "#D94C2E", Role.HIGHLIGHT),
                Slot("RED", "#9B243A", Role.ACCENT)
            )
        ),
        SceneStyle(
            "BURGUNDY_CINEMA",
            "KAMPALA NIGHT 01",
            "NIGHT / INTERIOR",
            "Restaurants • interiors • nightlife • dramatic red/plum practical lights",
            listOf(
                Slot("PLUM", "#2D0E1E", Role.SHADOW),
                Slot("BURGUNDY", "#4A052D", Role.AMBIENT),
                Slot("WINE", "#651A29", Role.MIDTONE),
                Slot("BRONZE", "#9B6230", Role.HIGHLIGHT),
                Slot("GOLD", "#C2852D", Role.ACCENT)
            )
        ),
        SceneStyle(
            "OLIVE_DOCUMENTARY",
            "WAKISO GREEN 01",
            "LAND / DOCUMENTARY",
            "Vegetation • roadsides • farms • earth • muted documentary daylight",
            listOf(
                Slot("EARTH", "#5B5540", Role.SHADOW),
                Slot("SAND", "#AE9D7F", Role.AMBIENT),
                Slot("OLIVE", "#4A4D1F", Role.MIDTONE),
                Slot("SUN GREEN", "#A49B47", Role.HIGHLIGHT),
                Slot("PALE SKY", "#94B4C0", Role.ACCENT)
            )
        ),
        SceneStyle(
            "DEEP_SPACE",
            "KAMPALA NIGHT 02",
            "NIGHT / INTERIOR",
            "Premium night • deep blue/plum shadows • controlled gold practicals",
            listOf(
                Slot("PLUM", "#45223B", Role.SHADOW),
                Slot("PURPLE GREY", "#443449", Role.AMBIENT),
                Slot("DEEP NAVY", "#16173A", Role.MIDTONE),
                Slot("BLUE", "#2A4E63", Role.HIGHLIGHT),
                Slot("GOLD", "#B07D3A", Role.ACCENT)
            )
        ),
        SceneStyle(
            "CLEAN_SOCIAL",
            "KAMPALA SOCIAL 01",
            "SOCIAL / DELIVERY",
            "TikTok • Reels • clean bright city posts • controlled punch",
            listOf(
                Slot("DEEP BLUE", "#0F354E", Role.SHADOW),
                Slot("CYAN", "#1E7B93", Role.AMBIENT),
                Slot("SKY", "#95BCD0", Role.MIDTONE),
                Slot("WHITE", "#F1F3F8", Role.HIGHLIGHT),
                Slot("ORANGE", "#D94C2E", Role.ACCENT)
            )
        ),
        SceneStyle(
            "CONSTRUCTION",
            "KAMPALA CONCRETE 01",
            "CONSTRUCTION / FIELD",
            "Concrete • blocks • reinforcement • earthworks • architecture progress",
            listOf(
                Slot("BLACK", "#181A19", Role.SHADOW),
                Slot("STRUCTURE", "#283842", Role.AMBIENT),
                Slot("CONCRETE", "#566A74", Role.MIDTONE),
                Slot("DUST", "#9B6230", Role.HIGHLIGHT),
                Slot("SUN", "#F29F2F", Role.ACCENT)
            )
        ),
        SceneStyle(
            "PEOPLE",
            "UGANDA PEOPLE 01",
            "PEOPLE / INTERVIEW",
            "Interviews • faces • natural skin • warm practical light • social portraits",
            listOf(
                Slot("PLUM SHADOW", "#45223B", Role.SHADOW),
                Slot("DEEP RED", "#9B243A", Role.AMBIENT),
                Slot("BRONZE", "#9B6230", Role.MIDTONE),
                Slot("SKIN WARM", "#D9A07A", Role.HIGHLIGHT),
                Slot("CREAM", "#E7D6C1", Role.ACCENT)
            )
        ),
        SceneStyle(
            "NIGHT_CINEMA",
            "CITY NIGHT 01",
            "NIGHT / INTERIOR",
            "Night roads • signs • low light • city lamps • controlled shadow color",
            listOf(
                Slot("MIDNIGHT", "#030D25", Role.SHADOW),
                Slot("INK BLUE", "#0C263E", Role.AMBIENT),
                Slot("NIGHT BLUE", "#2A4E63", Role.MIDTONE),
                Slot("PLUM", "#45223B", Role.HIGHLIGHT),
                Slot("LAMP GOLD", "#C2852D", Role.ACCENT)
            )
        ),
        SceneStyle(
            "SOFT_FILM",
            "UGANDA DOCUMENTARY 01",
            "LAND / DOCUMENTARY",
            "Low contrast • interviews • observational documentary • soft daylight",
            listOf(
                Slot("PURPLE GREY", "#443449", Role.SHADOW),
                Slot("EARTH", "#5B5540", Role.AMBIENT),
                Slot("SOFT STEEL", "#697763", Role.MIDTONE),
                Slot("SAND", "#AE9D7F", Role.HIGHLIGHT),
                Slot("SOFT WHITE", "#D1D3E0", Role.ACCENT)
            )
        ),
        SceneStyle(
            "MONO_CINEMA",
            "KAMPALA MONO 01",
            "MONO / RECORD",
            "Black-and-white architecture • documentary • dramatic field record",
            listOf(
                Slot("BLACK", "#181A19", Role.SHADOW),
                Slot("DARK GREY", "#343638", Role.AMBIENT),
                Slot("MID GREY", "#697076", Role.MIDTONE),
                Slot("SILVER", "#B1B4B7", Role.HIGHLIGHT),
                Slot("WHITE", "#F1F3F8", Role.ACCENT)
            )
        )
    )

    private val styleById = styles.associateBy { it.profileId }

    fun style(profileId: String?): SceneStyle? = profileId?.let(styleById::get)

    fun displayName(profileId: String?, fallback: String): String =
        style(profileId)?.displayName ?: fallback

    fun load(context: Context, scope: String, profileId: String): Tuning {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = style(profileId)?.slots?.size ?: 5
        return Tuning(
            IntArray(count) { index ->
                prefs.getInt(key(scope, profileId, index), DEFAULT).coerceIn(0, 200)
            }
        )
    }

    fun neutral(profileId: String): Tuning =
        Tuning(IntArray(style(profileId)?.slots?.size ?: 5) { DEFAULT })

    fun set(
        context: Context,
        scope: String,
        profileId: String,
        index: Int,
        value: Int
    ) {
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(key(scope, profileId, index), value.coerceIn(0, 200))
            .apply()
    }

    fun reset(context: Context, scope: String, profileId: String) {
        val style = style(profileId) ?: return
        val editor = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        style.slots.indices.forEach { editor.remove(key(scope, profileId, it)) }
        editor.apply()
    }

    fun tuningJson(
        context: Context,
        scope: String,
        profileId: String
    ): JSONArray {
        val style = style(profileId) ?: return JSONArray()
        val tuning = load(context, scope, profileId)
        return JSONArray().apply {
            style.slots.forEachIndexed { index, slot ->
                put(
                    JSONObject()
                        .put("name", slot.name)
                        .put("hex", slot.hex)
                        .put("role", slot.role.name)
                        .put("amount_percent", tuning.value(index))
                )
            }
        }
    }

    /**
     * Applies only the user's deviation from the authored V230 baseline.
     * 100% = no change, preserving the exact V230 LUT. Values below/above 100
     * subtract/add that palette family in the region assigned to the swatch.
     */
    fun applyPalette(
        profileId: String,
        inputR: Float,
        inputG: Float,
        inputB: Float,
        tuning: Tuning
    ): FloatArray {
        val style = style(profileId) ?: return floatArrayOf(inputR, inputG, inputB)

        var r = inputR
        var g = inputG
        var b = inputB

        val luma = (r * 0.2126f + g * 0.7152f + b * 0.0722f).coerceIn(0f, 1f)
        val maxC = max(r, max(g, b))
        val minC = minOf(r, g, b)
        val chroma = (maxC - minC).coerceIn(0f, 1f)
        val mid = (1f - abs(luma * 2f - 1f)).coerceIn(0f, 1f)

        style.slots.forEachIndexed { index, slot ->
            val delta = (tuning.value(index) - DEFAULT) / 100f
            if (abs(delta) < 0.001f) return@forEachIndexed

            val roleWeight = when (slot.role) {
                Role.SHADOW -> (1f - luma).pow(2.2f)
                Role.AMBIENT -> (1f - luma).pow(1.15f) * (0.45f + mid * 0.55f)
                Role.MIDTONE -> mid.pow(1.2f)
                Role.HIGHLIGHT -> luma.pow(1.85f)
                Role.ACCENT -> (0.30f + chroma * 0.70f) * (0.40f + mid * 0.60f)
            }

            val color = Color.parseColor(slot.hex)
            val cr = Color.red(color) / 255f
            val cg = Color.green(color) / 255f
            val cb = Color.blue(color) / 255f

            // Luminance-centered vector changes hue while protecting brightness.
            val targetLuma = cr * 0.2126f + cg * 0.7152f + cb * 0.0722f
            val dr = cr - targetLuma
            val dg = cg - targetLuma
            val db = cb - targetLuma

            val scale = delta * roleWeight * 0.17f
            r += dr * scale
            g += dg * scale
            b += db * scale
        }

        return floatArrayOf(
            r.coerceIn(0f, 1f),
            g.coerceIn(0f, 1f),
            b.coerceIn(0f, 1f)
        )
    }


    /**
     * Fast screen-space approximation used by the V232 live monitor.
     * The final export still uses applyPalette() inside the 17^3 LUT cube.
     * This returns a small RGB creative bias so slider movement is visible
     * immediately on the CameraX PreviewView without re-rendering a LUT cube.
     */
    fun previewBias(
        profileId: String,
        tuning: Tuning
    ): FloatArray {
        val style = style(profileId) ?: return floatArrayOf(0f, 0f, 0f)
        var rr = 0f
        var gg = 0f
        var bb = 0f

        style.slots.forEachIndexed { index, slot ->
            val delta = (tuning.value(index) - DEFAULT) / 100f
            if (abs(delta) < 0.001f) return@forEachIndexed

            val roleWeight = when (slot.role) {
                Role.SHADOW -> 0.42f
                Role.AMBIENT -> 0.48f
                Role.MIDTONE -> 0.58f
                Role.HIGHLIGHT -> 0.48f
                Role.ACCENT -> 0.40f
            }

            val color = Color.parseColor(slot.hex)
            val cr = Color.red(color) / 255f
            val cg = Color.green(color) / 255f
            val cb = Color.blue(color) / 255f
            val y = cr * 0.2126f + cg * 0.7152f + cb * 0.0722f
            val scale = delta * roleWeight * 0.22f

            rr += (cr - y) * scale
            gg += (cg - y) * scale
            bb += (cb - y) * scale
        }

        return floatArrayOf(
            rr.coerceIn(-0.20f, 0.20f),
            gg.coerceIn(-0.20f, 0.20f),
            bb.coerceIn(-0.20f, 0.20f)
        )
    }

    private fun key(scope: String, profileId: String, index: Int): String =
        "${safe(scope)}_${safe(profileId)}_slot_$index"

    private fun safe(value: String): String =
        value.uppercase(Locale.US)
            .replace(Regex("[^A-Z0-9_]+"), "_")
            .take(48)
            .ifBlank { "GLOBAL" }
}
