package com.sentongoharuna.pulse

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

enum class DevelopUgandaSealState(val label: String) {
    VERIFIED("VERIFIED"),
    ALTERED("ALTERED"),
    MISSING_SIDECAR("MISSING SIDECAR"),
    NOT_YET_CHECKED("NOT YET CHECKED"),
    READ_FAILED("READ FAILED"),
}

data class DevelopUgandaSealRecord(
    val uri: String,
    val slug: String,
    val expectedSha256: String,
    val sealedUtc: String,
    val mode: String,
    val appBuild: String,
    val sidecarName: String,
    val checkedSha256: String?,
    val checkedUtc: String?,
    val readFailed: Boolean,
) {
    val state: DevelopUgandaSealState
        get() = when {
            readFailed -> DevelopUgandaSealState.READ_FAILED
            checkedSha256 == null -> DevelopUgandaSealState.NOT_YET_CHECKED
            checkedSha256.equals(expectedSha256, true) -> DevelopUgandaSealState.VERIFIED
            else -> DevelopUgandaSealState.ALTERED
        }
}

/**
 * Expected hashes are written only after an integrity sidecar succeeds. A
 * take is never considered VERIFIED until a fresh read of the video matches.
 */
object DevelopUgandaSealRegistry {
    private const val PREFS = "develop_uganda_seal_registry_v1"
    private const val KEY = "records"
    private const val MAX_RECORDS = 500

    @Synchronized
    fun register(
        context: Context,
        uri: Uri,
        slug: String,
        expectedSha256: String,
        sealedUtc: String,
        mode: DevelopUgandaCameraPage?,
        sidecarName: String,
    ) {
        if (expectedSha256.length != 64 || uri.toString().isBlank()) return
        val existing = records(context).filterNot { it.uri == uri.toString() }.toMutableList()
        existing.add(
            0,
            DevelopUgandaSealRecord(
                uri = uri.toString(),
                slug = slug,
                expectedSha256 = expectedSha256.lowercase(Locale.US),
                sealedUtc = sealedUtc,
                mode = mode?.name ?: "UNKNOWN",
                appBuild = appBuild(context),
                sidecarName = sidecarName,
                checkedSha256 = null,
                checkedUtc = null,
                readFailed = false,
            ),
        )
        save(context, existing.take(MAX_RECORDS))
    }

    @Synchronized
    fun records(context: Context): List<DevelopUgandaSealRecord> = runCatching {
        val raw = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val expected = item.optString("expected_sha256")
                val uri = item.optString("uri")
                if (expected.length != 64 || uri.isBlank()) continue
                add(
                    DevelopUgandaSealRecord(
                        uri = uri,
                        slug = item.optString("slug", "TAKE"),
                        expectedSha256 = expected,
                        sealedUtc = item.optString("sealed_utc", "UNKNOWN"),
                        mode = item.optString("mode", "UNKNOWN"),
                        appBuild = item.optString("app_build", "UNKNOWN"),
                        sidecarName = item.optString("sidecar_name", "UNKNOWN"),
                        checkedSha256 = item.optString("checked_sha256", "").takeIf { it.isNotBlank() },
                        checkedUtc = item.optString("checked_utc", "").takeIf { it.isNotBlank() },
                        readFailed = item.optBoolean("read_failed", false),
                    ),
                )
            }
        }
    }.getOrElse { failure ->
        DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "SEAL REGISTRY READ", failure)
        emptyList()
    }

    fun recordFor(context: Context, uri: String): DevelopUgandaSealRecord? =
        records(context).firstOrNull { it.uri == uri }

    fun stateFor(context: Context, uri: String): DevelopUgandaSealState =
        recordFor(context, uri)?.state ?: DevelopUgandaSealState.MISSING_SIDECAR

    fun verifyAsync(context: Context, uri: Uri, callback: (DevelopUgandaSealState) -> Unit) {
        val app = context.applicationContext
        Thread {
            val actual = hash(app, uri)
            val record = recordFor(app, uri.toString())
            val nextState = when {
                record == null -> DevelopUgandaSealState.MISSING_SIDECAR
                actual == null -> {
                    updateCheck(app, record.uri, null, readFailed = true)
                    DevelopUgandaSealState.READ_FAILED
                }
                else -> {
                    updateCheck(app, record.uri, actual, readFailed = false)
                    if (actual.equals(record.expectedSha256, true)) {
                        DevelopUgandaSealState.VERIFIED
                    } else {
                        DevelopUgandaSealState.ALTERED
                    }
                }
            }
            (context as? Activity)?.runOnUiThread { callback(nextState) }
                ?: android.os.Handler(android.os.Looper.getMainLooper()).post { callback(nextState) }
        }.start()
    }

    /** Creates the LIVE backup seal off the encoder thread. */
    fun sealAsync(
        context: Context,
        uri: Uri,
        slug: String,
        mode: DevelopUgandaCameraPage,
        sealedUtc: String = Instant.now().toString(),
    ) {
        val app = context.applicationContext
        Thread {
            val sha = hash(app, uri) ?: return@Thread
            val sidecarName = "${slug}_INTEGRITY.json"
            val payload = JSONObject()
                .put("schema", "develop.uganda.report.integrity.v1")
                .put("filename", "$slug.mp4")
                .put("record_end_utc", sealedUtc)
                .put("mode", mode.name)
                .put("app_version", appBuild(app))
                .put("sha256", sha)
                .put("note", "SHA-256 detects later file changes; verification requires a fresh re-hash.")
                .toString(2)
            if (writeSidecar(app, sidecarName, payload)) {
                register(app, uri, slug, sha, sealedUtc, mode, sidecarName)
            }
        }.start()
    }

    fun exportManifest(context: Context): Uri? {
        val payload = JSONObject()
            .put("schema", "develop.uganda.seal.manifest.v1")
            .put("generated_utc", Instant.now().toString())
            .put("note", "State is the last explicit check; NOT YET CHECKED is never promoted automatically.")
            .put(
                "takes",
                JSONArray().apply {
                    records(context).forEach { record ->
                        put(
                            JSONObject()
                                .put("file", record.slug)
                                .put("uri", record.uri)
                                .put("hash", record.expectedSha256)
                                .put("timestamp", record.sealedUtc)
                                .put("mode", record.mode)
                                .put("app_build", record.appBuild)
                                .put("seal_state", record.state.label)
                                .put("last_checked_utc", record.checkedUtc ?: JSONObject.NULL),
                        )
                    }
                },
            )
            .toString(2)
        val name = "DEVELOP_UGANDA_SEAL_MANIFEST_${System.currentTimeMillis()}.json"
        return writeExport(context, name, payload)
    }

    private fun hash(context: Context, uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return@runCatching null
        digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }.getOrNull()

    @Synchronized
    private fun updateCheck(context: Context, uri: String, actual: String?, readFailed: Boolean) {
        save(
            context,
            records(context).map { record ->
                if (record.uri != uri) record else record.copy(
                    checkedSha256 = actual,
                    checkedUtc = Instant.now().toString(),
                    readFailed = readFailed,
                )
            },
        )
    }

    private fun save(context: Context, records: List<DevelopUgandaSealRecord>) {
        val array = JSONArray()
        records.take(MAX_RECORDS).forEach { record ->
            array.put(
                JSONObject()
                    .put("uri", record.uri)
                    .put("slug", record.slug)
                    .put("expected_sha256", record.expectedSha256)
                    .put("sealed_utc", record.sealedUtc)
                    .put("mode", record.mode)
                    .put("app_build", record.appBuild)
                    .put("sidecar_name", record.sidecarName)
                    .put("checked_sha256", record.checkedSha256 ?: JSONObject.NULL)
                    .put("checked_utc", record.checkedUtc ?: JSONObject.NULL)
                    .put("read_failed", record.readFailed),
            )
        }
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString())
            .apply()
    }

    private fun writeSidecar(context: Context, name: String, payload: String): Boolean =
        writeDocument(context, name, "application/json", payload) != null

    @Suppress("DEPRECATION")
    private fun appBuild(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "UNKNOWN"
    }.getOrDefault("UNKNOWN")

    private fun writeExport(context: Context, name: String, payload: String): Uri? =
        writeDocument(context, name, "application/json", payload)

    private fun writeDocument(context: Context, name: String, mime: String, payload: String): Uri? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/develop.uganda/Integrity")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null
            context.contentResolver.openOutputStream(uri, "w")?.use {
                it.write(payload.toByteArray(Charsets.UTF_8))
            } ?: return@runCatching null
            uri
        } else {
            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),
                "develop.uganda/Integrity",
            ).apply { mkdirs() }
            val file = File(directory, name)
            FileOutputStream(file).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            Uri.fromFile(file)
        }
    }.getOrNull()
}

class DevelopUgandaSealVerificationActivity : AppCompatActivity() {
    private lateinit var list: LinearLayout
    private lateinit var summary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(unit(), unit(), unit(), unit() * 3)
        }
        body.addView(label("VERIFY FOOTAGE SEALS", 22f, DevelopUgandaFivemods8Theme.content, true))
        body.addView(label("A stored hash is not a PASS until the video has been read again and matched.", 13f, DevelopUgandaFivemods8Theme.contentDim, false))
        summary = label("LOADING", 16f, DevelopUgandaFivemods8Theme.content, true).apply {
            setPadding(0, unit(), 0, unit())
        }
        body.addView(summary)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(action("VERIFY ALL") { verifyAll() }, LinearLayout.LayoutParams(0, unit() * 6, 1f))
        actions.addView(action("EXPORT MANIFEST") { exportManifest() }, LinearLayout.LayoutParams(0, unit() * 6, 1f).apply { marginStart = unit() })
        body.addView(actions)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(list)
        scroll.addView(body)
        setContentView(scroll)
        render()
    }

    private fun render() {
        val clips = runCatching { DevelopUgandaV274MediaVaultStore.clips(this) }.getOrDefault(emptyList())
        list.removeAllViews()
        val counts = clips.groupingBy { DevelopUgandaSealRegistry.stateFor(this, it.uri) }.eachCount()
        summary.text = buildString {
            append("TAKES ${clips.size}")
            DevelopUgandaSealState.entries.forEach { state ->
                val count = counts[state] ?: 0
                if (count > 0) append(" • ${state.label} $count")
            }
        }
        if (clips.isEmpty()) {
            list.addView(card("NO TAKES", "Record a take or add one to Media Vault. No file has been assumed verified.", DevelopUgandaFivemods8Theme.contentDim, null))
            return
        }
        clips.forEach { clip ->
            val state = DevelopUgandaSealRegistry.stateFor(this, clip.uri)
            val color = when (state) {
                DevelopUgandaSealState.VERIFIED -> DevelopUgandaFivemods8Theme.accent
                DevelopUgandaSealState.ALTERED, DevelopUgandaSealState.READ_FAILED -> DevelopUgandaFivemods8Theme.record
                DevelopUgandaSealState.MISSING_SIDECAR -> DevelopUgandaFivemods8Theme.warning
                DevelopUgandaSealState.NOT_YET_CHECKED -> DevelopUgandaFivemods8Theme.contentDim
            }
            list.addView(
                card(
                    clip.name,
                    "${state.label} • MODE ${clip.mode?.name ?: "UNKNOWN"} • ${DevelopUgandaV274MediaVaultStore.formatDuration(clip.durationMs)}",
                    color,
                    if (state == DevelopUgandaSealState.MISSING_SIDECAR) null else {
                        { verify(clip.uri) }
                    },
                ),
            )
        }
    }

    private fun verify(uri: String) {
        Toast.makeText(this, "VERIFYING FILE", Toast.LENGTH_SHORT).show()
        DevelopUgandaSealRegistry.verifyAsync(this, Uri.parse(uri)) { state ->
            Toast.makeText(this, state.label, Toast.LENGTH_SHORT).show()
            render()
        }
    }

    private fun verifyAll() {
        val records = runCatching { DevelopUgandaV274MediaVaultStore.clips(this) }
            .getOrDefault(emptyList())
            .filter { DevelopUgandaSealRegistry.recordFor(this, it.uri) != null }
        if (records.isEmpty()) {
            Toast.makeText(this, "NO CHECKABLE SIDECARS", Toast.LENGTH_SHORT).show()
            return
        }
        fun next(index: Int) {
            if (index >= records.size) {
                render()
                Toast.makeText(this, "VERIFICATION COMPLETE", Toast.LENGTH_SHORT).show()
                return
            }
            DevelopUgandaSealRegistry.verifyAsync(this, Uri.parse(records[index].uri)) { next(index + 1) }
        }
        next(0)
    }

    private fun exportManifest() {
        val uri = DevelopUgandaSealRegistry.exportManifest(this)
        Toast.makeText(
            this,
            if (uri == null) "MANIFEST EXPORT FAILED" else "MANIFEST SAVED • DOWNLOADS/develop.uganda/Integrity",
            Toast.LENGTH_LONG,
        ).show()
    }

    private fun card(title: String, detail: String, color: Int, verify: (() -> Unit)?): View =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(unit(), unit(), unit(), unit())
            background = GradientDrawable().apply {
                setColor(DevelopUgandaFivemods8Theme.surfaceRaised)
                cornerRadius = DevelopUgandaFivemods8Theme.radiusPx.toFloat()
                setStroke(maxOf(1, unit() / 8), DevelopUgandaFivemods8Theme.outline)
            }
            addView(label(title, 16f, DevelopUgandaFivemods8Theme.content, true))
            addView(label(detail, 13f, color, true))
            verify?.let { addView(action("VERIFY NOW", it), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, unit() * 6).apply { topMargin = unit() }) }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = unit() }
        }

    private fun action(value: String, onClick: () -> Unit): TextView = label(value, 13f, DevelopUgandaFivemods8Theme.content, true).apply {
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        contentDescription = value
        DevelopUgandaBroadcastPresentation.styleButton(this, DevelopUgandaButtonWeight.SECONDARY)
        setOnClickListener { onClick() }
    }

    private fun label(value: String, step: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        setTextColor(color)
        typeface = Typeface.create(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        DevelopUgandaFivemods8Theme.applyTypeScale(this, step)
        contentDescription = value
    }

    private fun unit(): Int = DevelopUgandaFivemods8Theme.spacingUnitPx
}
