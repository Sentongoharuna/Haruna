package com.sentongoharuna.pulse

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * V274 PRO CAM • MEDIA VAULT + DELIVERY ENGINE.
 *
 * This layer is intentionally cumulative. It registers newly finalized camera
 * masters, keeps ratings/protection metadata separate from the original media,
 * provides frame-grab/share/review actions, and keeps delivery presets honest:
 * ORIGINAL MASTER can be shared immediately; aspect-changing/social delivery
 * presets are marked RENDER REQUIRED until the editor/export renderer produces
 * a new file. The original CameraX master is never overwritten here.
 */
object DevelopUgandaV274MediaVaultStore {
    private const val PREF = "develop_uganda_v274_media_vault"
    private const val KEY_CLIPS = "clips_json"
    private const val KEY_QUEUE = "delivery_queue_json"

    const val PRESET_ORIGINAL = "ORIGINAL MASTER"
    const val PRESET_TIKTOK = "TIKTOK 9:16"
    const val PRESET_REELS = "REELS 9:16"
    const val PRESET_YOUTUBE = "YOUTUBE 16:9"
    const val PRESET_NEWS = "NEWS DESK"
    const val PRESET_WHATSAPP = "WHATSAPP QUICK"

    data class Clip(
        val id: String,
        val name: String,
        val uri: String,
        val durationMs: Long,
        val bytes: Long,
        val createdMs: Long,
        val outputMode: String,
        val project: String,
        val camera: String,
        val scene: Int,
        val take: Int,
        val health: String,
        val warnings: List<String>,
        val rating: String,
        val isProtected: Boolean,
        val frameUri: String,
        val deliveryPreset: String,
        val mode: DevelopUgandaCameraPage? = null,
    )

    data class DeliveryItem(
        val clipId: String,
        val clipName: String,
        val preset: String,
        val outputMode: String,
        val state: String,
        val createdMs: Long
    )

    private fun prefs(context: Context) =
        context.duSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun autoRegister(context: Context): Boolean =
        prefs(context).getBoolean("auto_register", true)

    fun setAutoRegister(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("auto_register", enabled).apply()
    }

    fun protectMasters(context: Context): Boolean =
        prefs(context).getBoolean("protect_masters", true)

    fun setProtectMasters(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("protect_masters", enabled).apply()
    }

    fun healthCheck(context: Context): Boolean =
        prefs(context).getBoolean("health_check", true)

    fun setHealthCheck(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("health_check", enabled).apply()
    }

    fun selectedPreset(context: Context): String =
        prefs(context).getString("delivery_preset", PRESET_ORIGINAL) ?: PRESET_ORIGINAL

    fun setSelectedPreset(context: Context, preset: String) {
        prefs(context).edit().putString("delivery_preset", preset).apply()
    }

    private fun clipFromJson(o: JSONObject): Clip {
        val warnings = mutableListOf<String>()
        val a = o.optJSONArray("warnings") ?: JSONArray()
        for (i in 0 until a.length()) warnings += a.optString(i)
        return Clip(
            id = o.optString("id"),
            name = o.optString("name", "CLIP"),
            uri = o.optString("uri"),
            durationMs = o.optLong("durationMs", 0L),
            bytes = o.optLong("bytes", 0L),
            createdMs = o.optLong("createdMs", 0L),
            outputMode = o.optString("outputMode", DevelopUgandaV271LiveCoach.MODE_CLEAN),
            project = o.optString("project", "FIELD PROJECT"),
            camera = o.optString("camera", "CAM A"),
            scene = o.optInt("scene", 1),
            take = o.optInt("take", 1),
            health = o.optString("health", "RECORDING CLEAN"),
            warnings = warnings,
            rating = o.optString("rating", "UNRATED"),
            isProtected = o.optBoolean("protected", false),
            frameUri = o.optString("frameUri"),
            deliveryPreset = o.optString("deliveryPreset", PRESET_ORIGINAL),
            mode = o.optString("mode", "").takeIf { it.isNotBlank() }?.let {
                runCatching { DevelopUgandaCameraPage.valueOf(it) }.getOrNull()
            },
        )
    }

    private fun clipToJson(c: Clip): JSONObject = JSONObject().apply {
        put("id", c.id)
        put("name", c.name)
        put("uri", c.uri)
        put("durationMs", c.durationMs)
        put("bytes", c.bytes)
        put("createdMs", c.createdMs)
        put("outputMode", c.outputMode)
        put("project", c.project)
        put("camera", c.camera)
        put("scene", c.scene)
        put("take", c.take)
        put("health", c.health)
        put("warnings", JSONArray(c.warnings))
        put("rating", c.rating)
        put("protected", c.isProtected)
        put("frameUri", c.frameUri)
        put("deliveryPreset", c.deliveryPreset)
        put("mode", c.mode?.name ?: JSONObject.NULL)
    }

    fun clips(context: Context): List<Clip> {
        val raw = prefs(context).getString(KEY_CLIPS, "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            val out = ArrayList<Clip>(a.length())
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                out += clipFromJson(o)
            }
            out.sortedByDescending { it.createdMs }
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V274 CLIP STORE READ", error)
            throw IllegalStateException("Media Vault clip store could not be read", error)
        }
    }

    private fun saveClips(context: Context, items: List<Clip>) {
        val a = JSONArray()
        items.sortedByDescending { it.createdMs }.take(250).forEach { a.put(clipToJson(it)) }
        prefs(context).edit().putString(KEY_CLIPS, a.toString()).apply()
    }

    fun onClipFinalized(
        context: Context,
        name: String,
        uri: String,
        durationMs: Long,
        bytes: Long,
        outputMode: String,
        project: String,
        camera: String,
        scene: Int,
        take: Int,
        warnings: List<String>,
        mode: DevelopUgandaCameraPage? = null,
    ) {
        if (!autoRegister(context) || uri.isBlank()) return
        val existing = clips(context).toMutableList()
        if (existing.any { it.uri == uri }) return
        val health = when {
            !healthCheck(context) -> "HEALTH CHECK OFF"
            warnings.isEmpty() -> "RECORDING CLEAN"
            warnings.any { it.contains("AUDIO", true) || it.contains("MIC", true) } -> "AUDIO CHECK"
            warnings.any { it.contains("THERM", true) } -> "THERMAL WARNING"
            warnings.any { it.contains("TRACK", true) || it.contains("SUBJECT", true) } -> "SUBJECT CHECK"
            else -> "CHECK WARNINGS"
        }
        existing.add(
            0,
            Clip(
                id = "V274_${System.currentTimeMillis()}",
                name = name.ifBlank { "DU_${System.currentTimeMillis()}" },
                uri = uri,
                durationMs = durationMs.coerceAtLeast(0L),
                bytes = bytes.coerceAtLeast(0L),
                createdMs = System.currentTimeMillis(),
                outputMode = outputMode,
                project = project.ifBlank { "FIELD PROJECT" },
                camera = camera.ifBlank { "CAM A" },
                scene = scene.coerceAtLeast(1),
                take = take.coerceAtLeast(1),
                health = health,
                warnings = warnings.take(12),
                rating = "UNRATED",
                isProtected = false,
                frameUri = "",
                deliveryPreset = selectedPreset(context),
                mode = mode,
            )
        )
        saveClips(context, existing)
    }

    private fun updateClip(context: Context, id: String, transform: (Clip) -> Clip) {
        val updated = clips(context).map { if (it.id == id) transform(it) else it }
        saveClips(context, updated)
    }

    fun setRating(context: Context, id: String, rating: String) =
        updateClip(context, id) { it.copy(rating = rating.uppercase(Locale.US)) }

    fun toggleProtected(context: Context, id: String): Boolean {
        var value = false
        updateClip(context, id) {
            value = !it.isProtected
            it.copy(isProtected = value)
        }
        return value
    }

    fun setFrameUri(context: Context, id: String, frameUri: String) =
        updateClip(context, id) { it.copy(frameUri = frameUri) }

    fun setClipDeliveryPreset(context: Context, id: String, preset: String) =
        updateClip(context, id) { it.copy(deliveryPreset = preset) }

    fun removeEntry(context: Context, id: String) {
        saveClips(context, clips(context).filterNot { it.id == id })
    }

    fun modeForMedia(context: Context, uri: String, name: String): DevelopUgandaCameraPage? =
        clips(context).firstOrNull { clip ->
            (uri.isNotBlank() && clip.uri == uri) || (name.isNotBlank() && clip.name == name)
        }?.mode

    fun lastClipForMode(context: Context, page: DevelopUgandaCameraPage): Clip? =
        clips(context).firstOrNull { it.mode == page }

    fun lastClipSummary(context: Context): String {
        val c = clips(context).firstOrNull() ?: return "NO V274 CLIPS YET • RECORD A NEW TAKE"
        val rating = if (c.rating == "UNRATED") "UNRATED" else c.rating
        val lock = if (c.isProtected) "PROTECTED" else "MASTER SAFE"
        val identity = c.mode?.let { DevelopUgandaFivemods12Identity.forPage(it).code } ?: "MODE UNKNOWN"
        return "LAST $identity • ${shortName(c.name)} • ${formatDuration(c.durationMs)} • $rating • ${c.outputMode} • $lock"
    }

    fun hubSummary(context: Context): String {
        val list = clips(context)
        val best = list.count { it.rating == "BEST" }
        val keep = list.count { it.rating == "KEEP" }
        val protectedCount = list.count { it.isProtected }
        return "CLIPS ${list.size} • BEST $best • KEEP $keep • LOCKED $protectedCount • ${selectedPreset(context)} • QUEUE ${DevelopUgandaDeliveryQueue.items(context).size}"
    }

    fun verificationSummary(context: Context): String {
        return "WORKING • clip registration / ratings / protect flag / frame grab / original share  •  RENDER REQUIRED • social aspect conversion / trim render  •  ORIGINAL NEVER OVERWRITTEN"
    }

    fun queue(context: Context): List<DeliveryItem> {
        val raw = prefs(context).getString(KEY_QUEUE, "[]") ?: "[]"
        return try {
            val a = JSONArray(raw)
            val out = mutableListOf<DeliveryItem>()
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                out += DeliveryItem(
                    clipId = o.optString("clipId"),
                    clipName = o.optString("clipName"),
                    preset = o.optString("preset", PRESET_ORIGINAL),
                    outputMode = o.optString("outputMode", DevelopUgandaV271LiveCoach.MODE_CLEAN),
                    state = o.optString("state", "READY"),
                    createdMs = o.optLong("createdMs", 0L)
                )
            }
            out.sortedByDescending { it.createdMs }
        } catch (error: Exception) {
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "V274 DELIVERY QUEUE READ", error)
            throw IllegalStateException("Media Vault delivery queue could not be read", error)
        }
    }

    fun enqueue(context: Context, clip: Clip, preset: String): DeliveryItem {
        val state = if (preset == PRESET_ORIGINAL) "READY TO SHARE" else "RENDER REQUIRED"
        val item = DeliveryItem(clip.id, clip.name, preset, clip.outputMode, state, System.currentTimeMillis())
        val items = queue(context).toMutableList().apply { add(0, item) }.take(100)
        val a = JSONArray()
        items.forEach {
            a.put(JSONObject().apply {
                put("clipId", it.clipId)
                put("clipName", it.clipName)
                put("preset", it.preset)
                put("outputMode", it.outputMode)
                put("state", it.state)
                put("createdMs", it.createdMs)
            })
        }
        prefs(context).edit().putString(KEY_QUEUE, a.toString()).apply()
        setClipDeliveryPreset(context, clip.id, preset)
        return item
    }

    fun clearQueue(context: Context) {
        prefs(context).edit().putString(KEY_QUEUE, "[]").apply()
    }

    fun shortName(value: String): String =
        if (value.length <= 22) value else value.take(19) + "…"

    fun formatDuration(ms: Long): String {
        val s = (ms / 1000L).coerceAtLeast(0L)
        return "%02d:%02d".format(Locale.US, s / 60L, s % 60L)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "--MB"
        return String.format(Locale.US, "%.0fMB", bytes.toDouble() / 1_048_576.0)
    }
}

class DevelopUgandaV274MediaVaultActivity : AppCompatActivity() {
    private val ink = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val card = DevelopUgandaFivemods8Theme.surfaceRaised
    private val line = DevelopUgandaFivemods8Theme.outline
    private val gold = DevelopUgandaFivemods8Theme.accent
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val green = DevelopUgandaFivemods8Theme.accent
    private val violet = DevelopUgandaFivemods8Theme.contentDim
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val red = DevelopUgandaFivemods8Theme.record

    private lateinit var root: LinearLayout
    private lateinit var clipList: LinearLayout
    private lateinit var liveSummary: TextView
    private lateinit var searchBox: EditText
    private var filter = "ALL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildPage())
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        window.decorView.post {
            DevelopUgandaFivemods8Theme.enforceTouchTargets(window.decorView)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildPage(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(ink)
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(30))
            setBackgroundColor(ink)
        }

        val hero = block(panel, gold).apply {
            addView(text("develop.uganda • V274 PRO CAM", 20f, white, true))
            addView(text("MEDIA VAULT + DELIVERY ENGINE", 11f, gold, true))
            addView(text("SHOOT → PROTECT → REVIEW → ORGANIZE → PREPARE → DELIVER", 8.5f, muted, true).apply { setPadding(0, dp(7), 0, 0) })
            liveSummary = text("LOADING VAULT…", 9.4f, cyan, true).apply { setPadding(0, dp(9), 0, 0) }
            addView(liveSummary)
            addView(text("Original CameraX masters are never overwritten here. Social aspect-changing presets clearly say RENDER REQUIRED until a renderer creates a new file.", 8.1f, muted, false).apply { setPadding(0, dp(6), 0, 0) })
        }
        root.addView(hero)

        root.addView(settingsPanel())
        root.addView(deliveryPanel())

        val searchPanel = block(panel, cyan).apply {
            addView(text("FIND + FILTER MEDIA", 10f, white, true))
            searchBox = EditText(this@DevelopUgandaV274MediaVaultActivity).apply {
                hint = "Search project, clip, camera, health…"
                setHintTextColor(muted)
                setTextColor(white)
                setSingleLine(true)
                background = rounded(card, line, 14, 1)
                setPadding(dp(10), 0, dp(10), 0)
            }
            addView(searchBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(7) })
            val searchAction = button("SEARCH", cyan) { refresh() }
            addView(searchAction, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(6) })
            val row = LinearLayout(this@DevelopUgandaV274MediaVaultActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("ALL", "BEST", "KEEP", "PROTECTED").forEachIndexed { index, value ->
                row.addView(button(value, if (value == "ALL") gold else green) {
                    filter = value
                    refresh()
                }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (index > 0) leftMargin = dp(4) })
            }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(6) })
        }
        root.addView(searchPanel)

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(5))
        }
        head.addView(text("MEDIA VAULT", 12f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(button("REFRESH", cyan) { refresh() }, LinearLayout.LayoutParams(dp(100), dp(38)))
        root.addView(head)

        clipList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(clipList)

        root.addView(
            block(panel, green).apply {
                addView(text("V274 VERIFICATION CENTER", 10f, white, true))
                addView(text(DevelopUgandaV274MediaVaultStore.verificationSummary(this@DevelopUgandaV274MediaVaultActivity), 8.2f, muted, false).apply { setPadding(0, dp(5), 0, 0) })
                addView(button("VERIFY FOOTAGE SEALS", cyan) {
                    startActivity(Intent(this@DevelopUgandaV274MediaVaultActivity, DevelopUgandaSealVerificationActivity::class.java))
                }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })
            }
        )

        scroll.addView(root)
        return scroll
    }

    private fun settingsPanel(): View = block(panel, green).apply {
        addView(text("VAULT SETTINGS", 10f, white, true))
        addView(toggleButton("AUTO REGISTER NEW CLIPS") {
            val next = !DevelopUgandaV274MediaVaultStore.autoRegister(this@DevelopUgandaV274MediaVaultActivity)
            DevelopUgandaV274MediaVaultStore.setAutoRegister(this@DevelopUgandaV274MediaVaultActivity, next)
            toast("Auto register ${if (next) "ON" else "OFF"}")
            refresh()
        })
        addView(toggleButton("PROTECTED MASTER GUARD") {
            val next = !DevelopUgandaV274MediaVaultStore.protectMasters(this@DevelopUgandaV274MediaVaultActivity)
            DevelopUgandaV274MediaVaultStore.setProtectMasters(this@DevelopUgandaV274MediaVaultActivity, next)
            toast("Protected master guard ${if (next) "ON" else "OFF"}")
            refresh()
        })
        addView(toggleButton("POST-RECORD HEALTH LABELS") {
            val next = !DevelopUgandaV274MediaVaultStore.healthCheck(this@DevelopUgandaV274MediaVaultActivity)
            DevelopUgandaV274MediaVaultStore.setHealthCheck(this@DevelopUgandaV274MediaVaultActivity, next)
            toast("Health labels ${if (next) "ON" else "OFF"}")
            refresh()
        })
    }

    private fun toggleButton(label: String, action: () -> Unit): View {
        val status = when (label) {
            "AUTO REGISTER NEW CLIPS" -> DevelopUgandaV274MediaVaultStore.autoRegister(this)
            "PROTECTED MASTER GUARD" -> DevelopUgandaV274MediaVaultStore.protectMasters(this)
            else -> DevelopUgandaV274MediaVaultStore.healthCheck(this)
        }
        return button("$label  •  ${if (status) "ON" else "OFF"}", if (status) green else muted, action)
    }

    private fun deliveryPanel(): View = block(panel, violet).apply {
        addView(text("DELIVERY PRESET", 10f, white, true))
        addView(text("ORIGINAL can share immediately. TikTok/Reels/YouTube/News/WhatsApp presets are organization targets until the editor/export renderer creates a new file.", 8f, muted, false).apply { setPadding(0, dp(4), 0, 0) })
        val presets = listOf(
            DevelopUgandaV274MediaVaultStore.PRESET_ORIGINAL,
            DevelopUgandaV274MediaVaultStore.PRESET_TIKTOK,
            DevelopUgandaV274MediaVaultStore.PRESET_REELS,
            DevelopUgandaV274MediaVaultStore.PRESET_YOUTUBE,
            DevelopUgandaV274MediaVaultStore.PRESET_NEWS,
            DevelopUgandaV274MediaVaultStore.PRESET_WHATSAPP
        )
        presets.chunked(2).forEach { pair ->
            val row = LinearLayout(this@DevelopUgandaV274MediaVaultActivity).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { index, preset ->
                row.addView(button(preset, if (DevelopUgandaV274MediaVaultStore.selectedPreset(this@DevelopUgandaV274MediaVaultActivity) == preset) gold else cyan) {
                    DevelopUgandaV274MediaVaultStore.setSelectedPreset(this@DevelopUgandaV274MediaVaultActivity, preset)
                    toast("Delivery preset • $preset")
                    recreate()
                }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { if (index > 0) leftMargin = dp(4) })
            }
            addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(5) })
        }
        addView(text("DELIVERY QUEUE • ${DevelopUgandaDeliveryQueue.items(this@DevelopUgandaV274MediaVaultActivity).size} ITEMS", 8.4f, gold, true).apply { setPadding(0, dp(8), 0, 0) })
        addView(button("OPEN DELIVERY QUEUE", cyan) {
            startActivity(Intent(this@DevelopUgandaV274MediaVaultActivity, DevelopUgandaDeliveryQueueActivity::class.java))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(8) })
        addView(button("CLEAR DELIVERY QUEUE", red) {
            DevelopUgandaV274MediaVaultStore.clearQueue(this@DevelopUgandaV274MediaVaultActivity)
            toast("LEGACY DELIVERY LIST CLEARED • MASTERS UNTOUCHED")
            recreate()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(42)).apply { topMargin = dp(5) })
    }

    private fun refresh() {
        if (!::clipList.isInitialized) return
        liveSummary.text = DevelopUgandaV274MediaVaultStore.hubSummary(this)
        clipList.removeAllViews()
        val q = if (::searchBox.isInitialized) searchBox.text.toString().trim().lowercase(Locale.US) else ""
        val items = DevelopUgandaV274MediaVaultStore.clips(this).filter { c ->
            val filterOk = when (filter) {
                "BEST" -> c.rating == "BEST"
                "KEEP" -> c.rating == "KEEP"
                "PROTECTED" -> c.isProtected
                else -> true
            }
            val searchOk = q.isBlank() || listOf(c.name, c.project, c.camera, c.health, c.outputMode, c.rating).any { it.lowercase(Locale.US).contains(q) }
            filterOk && searchOk
        }
        if (items.isEmpty()) {
            clipList.addView(block(card, line).apply {
                addView(text("NO MATCHING V274 CLIPS", 10f, white, true))
                addView(text("New successful recordings will register here automatically when Auto Register is ON.", 8.2f, muted, false).apply { setPadding(0, dp(5), 0, 0) })
            })
            return
        }
        items.take(80).forEach { clipList.addView(clipCard(it)) }
    }

    private fun clipCard(c: DevelopUgandaV274MediaVaultStore.Clip): View = block(card, if (c.isProtected) gold else line).apply {
        val identity = c.mode?.let(DevelopUgandaFivemods12Identity::forPage)
        val titleRow = LinearLayout(this@DevelopUgandaV274MediaVaultActivity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        titleRow.addView(text(DevelopUgandaV274MediaVaultStore.shortName(c.name), 10.5f, white, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        titleRow.addView(text(if (c.isProtected) "🔒" else c.rating, 8.5f, if (c.isProtected) gold else green, true).apply { gravity = Gravity.END }, LinearLayout.LayoutParams(dp(96), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(titleRow)
        addView(text(identity?.badge ?: "MODE UNKNOWN • IDENTITY UNAVAILABLE", 8.6f, identity?.accent ?: muted, true).apply { setPadding(0, dp(3), 0, 0) })
        val sceneCode = c.scene.coerceIn(0, 999).toString().padStart(3, '0')
val takeCode = c.take.coerceIn(0, 999).toString().padStart(3, '0')
addView(text("${c.project} • ${c.camera} • MODE ${c.mode?.name ?: "UNKNOWN"} • S$sceneCode T$takeCode", 8.3f, cyan, true).apply { setPadding(0, dp(3), 0, 0) })
        addView(text("MODE ${c.mode?.name ?: "UNKNOWN"} • ${DevelopUgandaV274MediaVaultStore.formatDuration(c.durationMs)} • ${DevelopUgandaV274MediaVaultStore.formatBytes(c.bytes)} • ${c.outputMode} • ${c.health}", 8.1f, muted, false).apply { setPadding(0, dp(3), 0, 0) })
        if (c.warnings.isNotEmpty()) addView(text("WARN • ${c.warnings.joinToString(" • ").take(160)}", 7.7f, red, false).apply { setPadding(0, dp(3), 0, 0) })

        val ratingRow = LinearLayout(this@DevelopUgandaV274MediaVaultActivity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("KEEP" to green, "BEST" to gold, "RETAKE" to red).forEachIndexed { index, (value, tint) ->
            ratingRow.addView(button(value, tint) {
                DevelopUgandaV274MediaVaultStore.setRating(this@DevelopUgandaV274MediaVaultActivity, c.id, value)
                haptic()
                refresh()
            }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { if (index > 0) leftMargin = dp(4) })
        }
        addView(ratingRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(7) })

        val actionRow = LinearLayout(this@DevelopUgandaV274MediaVaultActivity).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(button("REVIEW", cyan) { openClip(c) }, LinearLayout.LayoutParams(0, dp(40), 1f))
        actionRow.addView(button("FRAME GRAB", violet) { frameGrab(c) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(4) })
        actionRow.addView(button("SHARE", green) { shareClip(c) }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(4) })
        addView(actionRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(5) })

        val safeRow = LinearLayout(this@DevelopUgandaV274MediaVaultActivity).apply { orientation = LinearLayout.HORIZONTAL }
        safeRow.addView(button(if (c.isProtected) "UNLOCK MASTER" else "PROTECT MASTER", gold) {
            val now = DevelopUgandaV274MediaVaultStore.toggleProtected(this@DevelopUgandaV274MediaVaultActivity, c.id)
            toast(if (now) "Master protected" else "Master protection removed")
            haptic()
            refresh()
        }, LinearLayout.LayoutParams(0, dp(40), 1f))
        safeRow.addView(button("QUEUE ${DevelopUgandaV274MediaVaultStore.selectedPreset(this@DevelopUgandaV274MediaVaultActivity)}", cyan) {
            queueForDelivery(c)
        }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { leftMargin = dp(4) })
        addView(safeRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)).apply { topMargin = dp(5) })

        addView(button("DELETE MASTER…", red) { confirmDelete(c) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { topMargin = dp(5) })
    }

    private fun queueForDelivery(c: DevelopUgandaV274MediaVaultStore.Clip) {
        val isClean = c.outputMode.equals(DevelopUgandaV271LiveCoach.MODE_CLEAN, true)
        val enqueue = { cleanConsent: Boolean ->
            val item = DevelopUgandaDeliveryQueue.enqueueVaultClip(
                this,
                c,
                DevelopUgandaDeliveryQueue.configuredDestination(this),
                DevelopUgandaDeliveryNetworkPolicy.WIFI_ONLY,
                cleanConsent,
            )
            toast("${item.state.name} • ${item.lastError ?: "WI-FI ONLY"}")
            startActivity(Intent(this, DevelopUgandaDeliveryQueueActivity::class.java))
        }
        if (!isClean) {
            enqueue(false)
            return
        }
        AlertDialog.Builder(this)
            .setTitle("QUEUE CLEAN MASTER?")
            .setMessage("CLEAN is protected and is never uploaded by default. This action grants upload consent for this one queue item; the local master is never deleted.")
            .setNegativeButton("KEEP LOCAL", null)
            .setPositiveButton("UPLOAD CLEAN") { _, _ -> enqueue(true) }
            .show()
    }

    private fun openClip(c: DevelopUgandaV274MediaVaultStore.Clip) {
        startActivity(Intent(this, DevelopUgandaStoryPlayerActivity::class.java).apply {
            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_URI, c.uri)
            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_DIRECT_LABEL, c.name)
            putExtra(DevelopUgandaStoryPlayerActivity.EXTRA_TAKE_ID, c.name)
        })
    }

    private fun shareClip(c: DevelopUgandaV274MediaVaultStore.Clip) {
        val uri = Uri.parse(c.uri)
        try {
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share develop.uganda master"))
        } catch (_: Exception) { toast("Share is unavailable") }
    }

    private fun frameGrab(c: DevelopUgandaV274MediaVaultStore.Clip) {
        toast("Creating frame grab…")
        Thread {
            var retriever: MediaMetadataRetriever? = null
            try {
                retriever = MediaMetadataRetriever()
                retriever.setDataSource(this, Uri.parse(c.uri))
                val atUs = ((c.durationMs.coerceAtLeast(1000L) / 2L) * 1000L)
                val bitmap = retriever.getFrameAtTime(atUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: throw IllegalStateException("No frame")
                val name = "${c.name}_FRAME_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/develop.uganda/Frames")
                }
                val outUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Cannot create frame")
                contentResolver.openOutputStream(outUri)?.use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 94, out) }
                    ?: throw IllegalStateException("Cannot write frame")
                DevelopUgandaV274MediaVaultStore.setFrameUri(this, c.id, outUri.toString())
                runOnUiThread { toast("Frame saved • Pictures/develop.uganda/Frames"); refresh() }
            } catch (_: Exception) {
                runOnUiThread { toast("Frame grab could not be created on this clip") }
            } finally {
                try { retriever?.release() } catch (_: Exception) { }
            }
        }.start()
    }

    private fun confirmDelete(c: DevelopUgandaV274MediaVaultStore.Clip) {
        if (c.isProtected && DevelopUgandaV274MediaVaultStore.protectMasters(this)) {
            toast("Protected master • unlock it first")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Delete recorded master?")
            .setMessage("This deletes the selected media item. V274 never deletes a protected master silently.")
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("DELETE") { _, _ ->
                try {
                    val deleted = contentResolver.delete(Uri.parse(c.uri), null, null)
                    if (deleted > 0) {
                        DevelopUgandaV274MediaVaultStore.removeEntry(this, c.id)
                        toast("Master deleted")
                    } else {
                        toast("Android did not permit deletion")
                    }
                } catch (_: SecurityException) {
                    toast("Android confirmation/permission required for this media")
                } catch (_: Exception) {
                    toast("Delete failed")
                }
                refresh()
            }
            .show()
    }

    private fun button(label: String, tint: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        DevelopUgandaFivemods8Theme.applyTypeScale(this, 8.5f)
        setTextColor(white)
        isAllCaps = false
        gravity = Gravity.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        background = rounded(DevelopUgandaFivemods8Theme.surfaceRaised, tint, 12, 1)
        setPadding(dp(5), 0, dp(5), 0)
        setOnClickListener { view ->
            DevelopUgandaV28012SafeActions.run(view.context, "MEDIA VAULT BUTTON") { action() }
        }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        DevelopUgandaFivemods8Theme.applyTypeScale(this, size)
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun block(fill: Int, stroke: Int): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(11), dp(10), dp(11), dp(10))
        background = rounded(fill, stroke, 18, 1)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = DevelopUgandaFivemods8Theme.radiusPx.toFloat()
        setStroke(dp(strokeDp), stroke)
    }

    private fun haptic() {
        window.decorView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
}
