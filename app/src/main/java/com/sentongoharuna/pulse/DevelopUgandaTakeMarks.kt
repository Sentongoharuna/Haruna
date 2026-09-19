package com.sentongoharuna.pulse

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.util.Locale

data class DevelopUgandaTakeMark(
    val takeId: String,
    val mode: String,
    val elapsedMs: Long,
    val createdUtc: String,
    val label: String,
)

/** App-private review sidecars. They never modify the video or its SHA-256. */
object DevelopUgandaTakeMarks {
    private const val DIRECTORY = "develop_uganda_take_marks"

    @Synchronized
    fun add(
        context: Context,
        takeId: String,
        mode: DevelopUgandaCameraPage,
        elapsedMs: Long,
        label: String = "MARK",
    ): DevelopUgandaTakeMark {
        require(takeId.isNotBlank()) { "Active take id unavailable" }
        val mark = DevelopUgandaTakeMark(
            takeId = takeId,
            mode = mode.name,
            elapsedMs = elapsedMs.coerceAtLeast(0L),
            createdUtc = Instant.now().toString(),
            label = label.trim().take(80).ifBlank { "MARK" },
        )
        val existing = forTake(context, takeId).toMutableList()
        existing += mark
        write(context, takeId, existing)
        return mark
    }

    fun forTake(context: Context, takeId: String): List<DevelopUgandaTakeMark> {
        val file = file(context, takeId)
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONObject(file.readText(Charsets.UTF_8)).optJSONArray("marks") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        DevelopUgandaTakeMark(
                            takeId = item.optString("take_id", takeId),
                            mode = item.optString("mode", "UNKNOWN"),
                            elapsedMs = item.optLong("elapsed_ms", 0L).coerceAtLeast(0L),
                            createdUtc = item.optString("created_utc", "UNKNOWN"),
                            label = item.optString("label", "MARK"),
                        ),
                    )
                }
            }
        }.getOrElse { failure ->
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "TAKE MARK SIDECAR READ", failure)
            emptyList()
        }
    }

    fun forClip(context: Context, clipName: String): List<DevelopUgandaTakeMark> =
        all(context).filter { mark ->
            clipName == mark.takeId ||
                clipName.startsWith("${mark.takeId}_") ||
                mark.takeId.startsWith("${clipName}_")
        }.sortedBy { it.elapsedMs }

    fun all(context: Context): List<DevelopUgandaTakeMark> =
        directory(context).listFiles { file -> file.extension == "json" }
            .orEmpty()
            .flatMap { file -> forTake(context, file.nameWithoutExtension) }
            .sortedByDescending { it.createdUtc }

    fun format(elapsedMs: Long): String {
        val total = elapsedMs.coerceAtLeast(0L) / 1_000L
        return String.format(
            Locale.US,
            "%02d:%02d:%02d",
            total / 3_600L,
            (total / 60L) % 60L,
            total % 60L,
        )
    }

    private fun write(context: Context, takeId: String, marks: List<DevelopUgandaTakeMark>) {
        val target = file(context, takeId)
        val pending = File(target.parentFile, "${target.name}.pending")
        val root = JSONObject()
            .put("schema", "develop.uganda.take.marks.v1")
            .put("take_id", takeId)
            .put("seal_scope", "VIDEO FILE ONLY; MARK SIDECAR IS SEPARATE")
            .put(
                "marks",
                JSONArray().apply {
                    marks.forEach { mark ->
                        put(
                            JSONObject()
                                .put("take_id", mark.takeId)
                                .put("mode", mark.mode)
                                .put("elapsed_ms", mark.elapsedMs)
                                .put("created_utc", mark.createdUtc)
                                .put("label", mark.label),
                        )
                    }
                },
            )
        pending.writeText(root.toString(2), Charsets.UTF_8)
        if (target.exists() && !target.delete()) error("Could not replace mark sidecar")
        if (!pending.renameTo(target)) error("Could not commit mark sidecar")
    }

    private fun directory(context: Context): File =
        File(context.filesDir, DIRECTORY).apply { mkdirs() }

    private fun file(context: Context, takeId: String): File {
        val safe = takeId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(directory(context), "$safe.json")
    }
}
