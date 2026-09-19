package com.sentongoharuna.pulse

import android.app.AlertDialog
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val DEVELOP_UGANDA_DELIVERY_MAX_AUTOMATIC_ATTEMPTS = 5

enum class DevelopUgandaDeliveryNetworkPolicy {
    WIFI_ONLY,
    ANY_CONNECTION,
    SEND_NOW,
}

enum class DevelopUgandaDeliveryProtocol {
    RESUMABLE_RANGE,
    CHUNK_MANIFEST,
    ANDROID_HANDOFF,
}

enum class DevelopUgandaDeliveryState {
    QUEUED,
    SENDING,
    RETRYING,
    FAILED,
    VERIFYING,
    DELIVERED,
    BLOCKED,
}

data class DevelopUgandaDeliveryDestination(
    val id: String,
    val label: String,
    val endpoint: String,
    val protocol: DevelopUgandaDeliveryProtocol,
)

data class DevelopUgandaQueuedDelivery(
    val id: String,
    val mediaUri: String,
    val displayName: String,
    val mode: DevelopUgandaCameraPage?,
    val destination: DevelopUgandaDeliveryDestination,
    val priority: Int,
    val attempts: Int,
    val lastError: String?,
    val bytesSent: Long,
    val bytesTotal: Long,
    val checksumSha256: String?,
    val networkPolicy: DevelopUgandaDeliveryNetworkPolicy,
    val state: DevelopUgandaDeliveryState,
    val createdMs: Long,
    val updatedMs: Long,
    val deliveredMs: Long?,
    val uploadId: String,
    val cleanMaster: Boolean,
    val cleanUploadConsent: Boolean,
)

/**
 * Persistent field-delivery queue owned by the existing Media Vault.
 *
 * RESUMABLE_RANGE destinations must acknowledge Content-Range with an exact
 * Upload-Offset/Range and must return the full SHA-256 on completion.
 * CHUNK_MANIFEST destinations receive independently hashed 1 MiB parts and a
 * final manifest, then must return the reassembled file hash. Android handoff
 * destinations remain BLOCKED until the operator completes the foreground
 * share; they are never labelled delivered by assumption.
 */
object DevelopUgandaDeliveryQueue {
    const val ACTION_CHANGED = "com.sentongoharuna.pulse.DELIVERY_QUEUE_CHANGED"
    private const val PREFS = "develop_uganda_delivery_queue"
    private const val ITEMS = "items_v1"
    private const val ENDPOINT = "default_endpoint"
    private const val PROTOCOL = "default_protocol"
    private const val JOB_ID = 0x445551
    private const val MAX_ITEMS = 250
    private const val CHUNK_BYTES = 1024 * 1024

    private fun prefs(context: Context) =
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun configuredDestination(context: Context): DevelopUgandaDeliveryDestination? {
        val endpoint = prefs(context).getString(ENDPOINT, "").orEmpty().trim()
        if (!isHttpsEndpoint(endpoint)) return null
        val protocol = runCatching {
            DevelopUgandaDeliveryProtocol.valueOf(
                prefs(context).getString(PROTOCOL, DevelopUgandaDeliveryProtocol.RESUMABLE_RANGE.name)
                    ?: DevelopUgandaDeliveryProtocol.RESUMABLE_RANGE.name,
            )
        }.getOrDefault(DevelopUgandaDeliveryProtocol.RESUMABLE_RANGE)
        return DevelopUgandaDeliveryDestination("field-desk", "FIELD DESK", endpoint, protocol)
    }

    fun configureDestination(
        context: Context,
        endpoint: String,
        protocol: DevelopUgandaDeliveryProtocol,
    ): Boolean {
        val safe = endpoint.trim()
        if (!isHttpsEndpoint(safe)) return false
        prefs(context).edit()
            .putString(ENDPOINT, safe)
            .putString(PROTOCOL, protocol.name)
            .apply()
        notifyChanged(context)
        return true
    }

    private fun isHttpsEndpoint(value: String): Boolean = runCatching {
        val url = URL(value)
        url.protocol.equals("https", true) && url.host.isNotBlank() && url.userInfo == null
    }.getOrDefault(false)

    @Synchronized
    fun items(context: Context): List<DevelopUgandaQueuedDelivery> {
        val raw = prefs(context).getString(ITEMS, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(fromJson(item))
                }
            }.sortedWith(compareByDescending<DevelopUgandaQueuedDelivery> { it.priority }.thenBy { it.createdMs })
        }.getOrElse { error ->
            DevelopUgandaV28012RuntimeGuard.recordUiFault(context, "DELIVERY QUEUE READ", error)
            emptyList()
        }
    }

    @Synchronized
    fun enqueue(
        context: Context,
        mediaUri: Uri,
        displayName: String,
        mode: DevelopUgandaCameraPage?,
        destination: DevelopUgandaDeliveryDestination?,
        networkPolicy: DevelopUgandaDeliveryNetworkPolicy? = null,
        cleanMaster: Boolean,
        cleanUploadConsent: Boolean,
    ): DevelopUgandaQueuedDelivery {
        val total = sourceLength(context, mediaUri) ?: -1L
        val selectedDestination = destination ?: DevelopUgandaDeliveryDestination(
            "not-configured",
            "DESTINATION NOT CONFIGURED",
            "",
            DevelopUgandaDeliveryProtocol.RESUMABLE_RANGE,
        )
        val blockedReason = when {
            cleanMaster && !cleanUploadConsent -> "CLEAN MASTER NEEDS EXPLICIT UPLOAD CONSENT"
            destination == null -> "DESTINATION NOT CONFIGURED"
            total < 0L -> "SOURCE SIZE UNAVAILABLE"
            else -> null
        }
        // BRAND masters and large payloads default to Wi-Fi. CLEAN is even
        // stricter because it additionally requires explicit upload consent.
        val policy = networkPolicy ?: DevelopUgandaDeliveryNetworkPolicy.WIFI_ONLY
        val now = System.currentTimeMillis()
        val current = items(context).toMutableList()
        val item = DevelopUgandaQueuedDelivery(
            id = UUID.randomUUID().toString(),
            mediaUri = mediaUri.toString(),
            displayName = displayName.ifBlank { "UNNAMED TAKE" },
            mode = mode,
            destination = selectedDestination,
            priority = (current.maxOfOrNull { it.priority } ?: 0) + 1,
            attempts = 0,
            lastError = blockedReason,
            bytesSent = 0L,
            bytesTotal = total,
            checksumSha256 = null,
            networkPolicy = policy,
            state = if (blockedReason == null) DevelopUgandaDeliveryState.QUEUED else DevelopUgandaDeliveryState.BLOCKED,
            createdMs = now,
            updatedMs = now,
            deliveredMs = null,
            uploadId = UUID.randomUUID().toString(),
            cleanMaster = cleanMaster,
            cleanUploadConsent = cleanUploadConsent,
        )
        current += item
        save(context, current)
        if (item.state == DevelopUgandaDeliveryState.QUEUED) schedule(context)
        return item
    }

    fun enqueueVaultClip(
        context: Context,
        clip: DevelopUgandaV274MediaVaultStore.Clip,
        destination: DevelopUgandaDeliveryDestination?,
        networkPolicy: DevelopUgandaDeliveryNetworkPolicy?,
        cleanUploadConsent: Boolean,
    ): DevelopUgandaQueuedDelivery = enqueue(
        context = context,
        mediaUri = Uri.parse(clip.uri),
        displayName = clip.name,
        mode = clip.mode,
        destination = destination,
        networkPolicy = networkPolicy,
        cleanMaster = clip.outputMode.equals(DevelopUgandaV271LiveCoach.MODE_CLEAN, true),
        cleanUploadConsent = cleanUploadConsent,
    )

    @Synchronized
    fun moveToFront(context: Context, id: String) {
        val list = items(context)
        val next = (list.maxOfOrNull { it.priority } ?: 0) + 1
        save(context, list.map { if (it.id == id) it.copy(priority = next, updatedMs = System.currentTimeMillis()) else it })
        schedule(context)
    }

    @Synchronized
    fun retry(context: Context, id: String) {
        save(context, items(context).map {
            if (it.id == id && it.state != DevelopUgandaDeliveryState.DELIVERED) {
                it.copy(state = DevelopUgandaDeliveryState.QUEUED, lastError = null, updatedMs = System.currentTimeMillis())
            } else it
        })
        schedule(context)
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        save(context, items(context).filterNot { it.id == id })
    }

    @Synchronized
    internal fun replace(context: Context, replacement: DevelopUgandaQueuedDelivery) {
        save(context, items(context).map { if (it.id == replacement.id) replacement else it })
    }

    @Synchronized
    private fun save(context: Context, source: List<DevelopUgandaQueuedDelivery>) {
        val array = JSONArray()
        source.sortedByDescending { it.updatedMs }.take(MAX_ITEMS).forEach { array.put(toJson(it)) }
        prefs(context).edit().putString(ITEMS, array.toString()).commit()
        notifyChanged(context)
    }

    fun schedule(context: Context) {
        if (DevelopUgandaV276RecordingSafety.isCaptureActive(context)) return
        val pending = items(context).filter { it.state in setOf(
            DevelopUgandaDeliveryState.QUEUED,
            DevelopUgandaDeliveryState.RETRYING,
        ) }
        if (pending.isEmpty()) return
        val wifiOnly = pending.all { it.networkPolicy == DevelopUgandaDeliveryNetworkPolicy.WIFI_ONLY }
        val info = JobInfo.Builder(JOB_ID, ComponentName(context, DevelopUgandaDeliveryJobService::class.java))
            .setRequiredNetworkType(if (wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).schedule(info)
    }

    fun onCaptureStarted(context: Context) {
        (context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(JOB_ID)
    }

    fun onCaptureEnded(context: Context) = schedule(context)

    fun networkPermits(context: Context, policy: DevelopUgandaDeliveryNetworkPolicy): Pair<Boolean, String> {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false to "NO CONNECTION"
        val caps = manager.getNetworkCapabilities(network) ?: return false to "CONNECTION STATE UNKNOWN"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false to "NO INTERNET CAPABILITY"
        if (policy == DevelopUgandaDeliveryNetworkPolicy.WIFI_ONLY &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return false to "WAITING FOR WI-FI"
        }
        return true to "NETWORK READY"
    }

    fun openSource(context: Context, item: DevelopUgandaQueuedDelivery): InputStream? {
        val uri = Uri.parse(item.mediaUri)
        return when (uri.scheme?.lowercase(Locale.US)) {
            "content", "android.resource" -> context.contentResolver.openInputStream(uri)
            "file" -> FileInputStream(File(requireNotNull(uri.path)))
            null, "" -> FileInputStream(File(item.mediaUri))
            else -> null
        }
    }

    fun sourceLength(context: Context, uri: Uri): Long? {
        if (uri.scheme == "file") return uri.path?.let(::File)?.length()?.takeIf { it >= 0L }
        if (uri.scheme.isNullOrBlank()) return File(uri.toString()).length().takeIf { it >= 0L }
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
                if (descriptor.length >= 0L) return descriptor.length
            }
        }
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
            }
            null
        }.getOrNull()
    }

    fun sha256(context: Context, item: DevelopUgandaQueuedDelivery): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        openSource(context, item)?.use { input ->
            val buffer = ByteArray(256 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(Locale.US, it) }
    }.getOrNull()

    private fun notifyChanged(context: Context) {
        context.sendBroadcast(Intent(ACTION_CHANGED).setPackage(context.packageName))
    }

    private fun toJson(item: DevelopUgandaQueuedDelivery) = JSONObject()
        .put("id", item.id)
        .put("media_uri", item.mediaUri)
        .put("display_name", item.displayName)
        .put("mode", item.mode?.name ?: JSONObject.NULL)
        .put("destination", JSONObject()
            .put("id", item.destination.id)
            .put("label", item.destination.label)
            .put("endpoint", item.destination.endpoint)
            .put("protocol", item.destination.protocol.name))
        .put("priority", item.priority)
        .put("attempts", item.attempts)
        .put("last_error", item.lastError ?: JSONObject.NULL)
        .put("bytes_sent", item.bytesSent)
        .put("bytes_total", item.bytesTotal)
        .put("checksum_sha256", item.checksumSha256 ?: JSONObject.NULL)
        .put("network_policy", item.networkPolicy.name)
        .put("state", item.state.name)
        .put("created_ms", item.createdMs)
        .put("updated_ms", item.updatedMs)
        .put("delivered_ms", item.deliveredMs ?: JSONObject.NULL)
        .put("upload_id", item.uploadId)
        .put("clean_master", item.cleanMaster)
        .put("clean_upload_consent", item.cleanUploadConsent)

    private fun fromJson(json: JSONObject): DevelopUgandaQueuedDelivery {
        val destination = json.optJSONObject("destination") ?: JSONObject()
        return DevelopUgandaQueuedDelivery(
            id = json.optString("id"),
            mediaUri = json.optString("media_uri"),
            displayName = json.optString("display_name", "UNNAMED TAKE"),
            mode = json.optString("mode", "").takeIf { it.isNotBlank() }
                ?.let { runCatching { DevelopUgandaCameraPage.valueOf(it) }.getOrNull() },
            destination = DevelopUgandaDeliveryDestination(
                destination.optString("id", "unknown"),
                destination.optString("label", "DESTINATION UNKNOWN"),
                destination.optString("endpoint", ""),
                runCatching { DevelopUgandaDeliveryProtocol.valueOf(destination.optString("protocol")) }
                    .getOrDefault(DevelopUgandaDeliveryProtocol.RESUMABLE_RANGE),
            ),
            priority = json.optInt("priority", 0),
            attempts = json.optInt("attempts", 0),
            lastError = json.optString("last_error", "").takeIf { it.isNotBlank() },
            bytesSent = json.optLong("bytes_sent", 0L),
            bytesTotal = json.optLong("bytes_total", -1L),
            checksumSha256 = json.optString("checksum_sha256", "").takeIf { it.isNotBlank() },
            networkPolicy = runCatching { DevelopUgandaDeliveryNetworkPolicy.valueOf(json.optString("network_policy")) }
                .getOrDefault(DevelopUgandaDeliveryNetworkPolicy.WIFI_ONLY),
            state = runCatching { DevelopUgandaDeliveryState.valueOf(json.optString("state")) }
                .getOrDefault(DevelopUgandaDeliveryState.FAILED),
            createdMs = json.optLong("created_ms", 0L),
            updatedMs = json.optLong("updated_ms", 0L),
            deliveredMs = if (json.isNull("delivered_ms")) null else json.optLong("delivered_ms"),
            uploadId = json.optString("upload_id", UUID.randomUUID().toString()),
            cleanMaster = json.optBoolean("clean_master", false),
            cleanUploadConsent = json.optBoolean("clean_upload_consent", false),
        )
    }
}

class DevelopUgandaDeliveryJobService : JobService() {
    private val executor = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)

    override fun onStartJob(params: JobParameters): Boolean {
        stopped.set(false)
        executor.execute {
            val reschedule = runQueue()
            jobFinished(params, reschedule)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        stopped.set(true)
        return true
    }

    private fun runQueue(): Boolean {
        while (!stopped.get()) {
            if (DevelopUgandaV276RecordingSafety.isCaptureActive(this)) return true
            val item = DevelopUgandaDeliveryQueue.items(this).firstOrNull {
                it.state in setOf(
                    DevelopUgandaDeliveryState.QUEUED,
                    DevelopUgandaDeliveryState.RETRYING,
                )
            } ?: return false
            val network = DevelopUgandaDeliveryQueue.networkPermits(this, item.networkPolicy)
            if (!network.first) {
                update(item.copy(
                    state = DevelopUgandaDeliveryState.RETRYING,
                    lastError = network.second,
                    updatedMs = System.currentTimeMillis(),
                ))
                return true
            }
            val outcome = runCatching { send(item) }
            if (outcome.isFailure) {
                val error = outcome.exceptionOrNull()
                val attempts = item.attempts + 1
                update(item.copy(
                    state = if (attempts >= DEVELOP_UGANDA_DELIVERY_MAX_AUTOMATIC_ATTEMPTS) {
                        DevelopUgandaDeliveryState.FAILED
                    } else {
                        DevelopUgandaDeliveryState.RETRYING
                    },
                    attempts = attempts,
                    lastError = "${error?.javaClass?.simpleName ?: "UPLOAD ERROR"} • ${error?.message ?: "no message"}",
                    updatedMs = System.currentTimeMillis(),
                ))
                return true
            }
        }
        return true
    }

    private fun send(original: DevelopUgandaQueuedDelivery) {
        require(!original.cleanMaster || original.cleanUploadConsent) {
            "CLEAN MASTER NEEDS EXPLICIT UPLOAD CONSENT"
        }
        require(original.bytesTotal >= 0L) { "SOURCE SIZE UNAVAILABLE" }
        val checksum = original.checksumSha256 ?: DevelopUgandaDeliveryQueue.sha256(this, original)
            ?: error("SOURCE HASH UNAVAILABLE")
        var item = original.copy(
            state = DevelopUgandaDeliveryState.SENDING,
            attempts = original.attempts + 1,
            checksumSha256 = checksum,
            lastError = null,
            updatedMs = System.currentTimeMillis(),
        )
        update(item)
        when (item.destination.protocol) {
            DevelopUgandaDeliveryProtocol.RESUMABLE_RANGE -> item = sendRange(item)
            DevelopUgandaDeliveryProtocol.CHUNK_MANIFEST -> item = sendChunks(item)
            DevelopUgandaDeliveryProtocol.ANDROID_HANDOFF -> error("FOREGROUND HANDOFF REQUIRED")
        }
        val remote = verifyRemote(item)
        if (!remote.equals(checksum, true)) {
            update(item.copy(
                state = DevelopUgandaDeliveryState.FAILED,
                lastError = if (remote == null) "DESTINATION DID NOT PROVE SHA-256" else "ARRIVAL HASH MISMATCH",
                updatedMs = System.currentTimeMillis(),
            ))
            return
        }
        val delivered = System.currentTimeMillis()
        update(item.copy(
            state = DevelopUgandaDeliveryState.DELIVERED,
            bytesSent = item.bytesTotal,
            lastError = null,
            deliveredMs = delivered,
            updatedMs = delivered,
        ))
    }

    private fun sendRange(start: DevelopUgandaQueuedDelivery): DevelopUgandaQueuedDelivery {
        var item = start
        while (item.bytesSent < item.bytesTotal) {
            ensureCanContinue()
            val startByte = item.bytesSent
            val count = minOf(1024L * 1024L, item.bytesTotal - startByte).toInt()
            val payload = readRange(item, startByte, count)
            val endByte = startByte + payload.size - 1L
            val connection = open(item.destination.endpoint, "PUT")
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("Content-Range", "bytes $startByte-$endByte/${item.bytesTotal}")
            connection.setRequestProperty("Upload-Id", item.uploadId)
            connection.setRequestProperty("X-Content-SHA256", item.checksumSha256)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in setOf(200, 201, 202, 204, 308)) {
                throw IllegalStateException("DESTINATION ${safeEndpoint(item.destination.endpoint)} RETURNED HTTP $code")
            }
            val acknowledged = acknowledgedOffset(connection, endByte + 1L, item.bytesTotal)
            if (acknowledged < endByte + 1L || acknowledged > item.bytesTotal) {
                throw IllegalStateException("DESTINATION DID NOT ACKNOWLEDGE BYTE $endByte")
            }
            item = item.copy(bytesSent = acknowledged, updatedMs = System.currentTimeMillis())
            update(item)
            connection.disconnect()
        }
        return item.copy(state = DevelopUgandaDeliveryState.VERIFYING).also(::update)
    }

    private fun sendChunks(start: DevelopUgandaQueuedDelivery): DevelopUgandaQueuedDelivery {
        var item = start
        val parts = ((item.bytesTotal + 1024L * 1024L - 1L) / (1024L * 1024L)).toInt()
        while (item.bytesSent < item.bytesTotal) {
            ensureCanContinue()
            val startByte = item.bytesSent
            val part = (startByte / (1024L * 1024L)).toInt()
            val count = minOf(1024L * 1024L, item.bytesTotal - startByte).toInt()
            val payload = readRange(item, startByte, count)
            val partHash = MessageDigest.getInstance("SHA-256").digest(payload)
                .joinToString("") { "%02x".format(Locale.US, it) }
            val endpoint = appendQuery(item.destination.endpoint,
                "du_upload_id=${item.uploadId}&part=$part&parts=$parts")
            val connection = open(endpoint, "PUT")
            connection.setRequestProperty("Content-Type", "application/octet-stream")
            connection.setRequestProperty("X-Part-SHA256", partHash)
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            val confirmedPart = connection.getHeaderField("X-Part-SHA256")
            if (code !in 200..299 || !confirmedPart.equals(partHash, true)) {
                throw IllegalStateException("DESTINATION DID NOT VERIFY PART $part")
            }
            item = item.copy(bytesSent = startByte + payload.size, updatedMs = System.currentTimeMillis())
            update(item)
            connection.disconnect()
        }
        val endpoint = appendQuery(item.destination.endpoint, "du_upload_id=${item.uploadId}&complete=1")
        val manifest = JSONObject()
            .put("upload_id", item.uploadId)
            .put("parts", parts)
            .put("bytes", item.bytesTotal)
            .put("sha256", item.checksumSha256)
            .toString().toByteArray(Charsets.UTF_8)
        val complete = open(endpoint, "POST")
        complete.setRequestProperty("Content-Type", "application/json")
        complete.setFixedLengthStreamingMode(manifest.size)
        complete.outputStream.use { it.write(manifest) }
        val code = complete.responseCode
        if (code !in 200..299) throw IllegalStateException("DESTINATION REASSEMBLY FAILED • HTTP $code")
        complete.disconnect()
        return item.copy(state = DevelopUgandaDeliveryState.VERIFYING).also(::update)
    }

    private fun verifyRemote(item: DevelopUgandaQueuedDelivery): String? {
        val connection = open(
            appendQuery(item.destination.endpoint, "du_upload_id=${item.uploadId}&verify=sha256"),
            "HEAD",
        )
        val code = connection.responseCode
        if (code !in 200..299) {
            connection.disconnect()
            return null
        }
        // X-Content-SHA256 is deliberately the only accepted proof. RFC
        // Digest normally carries Base64; treating it as hexadecimal would
        // create an ambiguous or false verification result.
        val value = connection.getHeaderField("X-Content-SHA256")
        connection.disconnect()
        return value
    }

    private fun readRange(item: DevelopUgandaQueuedDelivery, offset: Long, count: Int): ByteArray {
        val input = DevelopUgandaDeliveryQueue.openSource(this, item)
            ?: error("SOURCE URI IS NOT READABLE")
        BufferedInputStream(input).use { stream ->
            skipFully(stream, offset)
            val data = ByteArray(count)
            var cursor = 0
            while (cursor < count) {
                val read = stream.read(data, cursor, count - cursor)
                if (read < 0) break
                cursor += read
            }
            if (cursor != count) error("SOURCE ENDED AT ${offset + cursor} OF ${item.bytesTotal}")
            return data
        }
    }

    private fun skipFully(input: InputStream, offset: Long) {
        var remaining = offset
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) remaining -= skipped
            else if (input.read() >= 0) remaining--
            else error("SOURCE ENDED BEFORE RESUME OFFSET")
        }
    }

    private fun acknowledgedOffset(connection: HttpURLConnection, fallback: Long, total: Long): Long {
        connection.getHeaderField("Upload-Offset")?.toLongOrNull()?.let { return it }
        val range = connection.getHeaderField("Range")
        val last = range?.substringAfterLast('-')?.trim()?.toLongOrNull()
        if (last != null) return last + 1L
        // A final success can be accepted provisionally because the following
        // HEAD must prove the whole-file SHA-256. Intermediate chunks require
        // an explicit offset; otherwise resume support would be an assumption.
        return if (fallback == total && connection.responseCode in 200..299) fallback else -1L
    }

    private fun open(endpoint: String, method: String): HttpURLConnection =
        (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            doInput = true
            doOutput = method == "PUT" || method == "POST"
            useCaches = false
            instanceFollowRedirects = false
        }

    private fun ensureCanContinue() {
        if (stopped.get() || DevelopUgandaV276RecordingSafety.isCaptureActive(this)) {
            throw InterruptedException("UPLOAD YIELDED TO ACTIVE CAPTURE")
        }
    }

    private fun update(item: DevelopUgandaQueuedDelivery) =
        DevelopUgandaDeliveryQueue.replace(this, item)

    private fun appendQuery(endpoint: String, value: String): String =
        "$endpoint${if ('?' in endpoint) '&' else '?'}$value"

    private fun safeEndpoint(endpoint: String): String = runCatching {
        val url = URL(endpoint)
        "${url.protocol}://${url.host}${url.path}"
    }.getOrDefault("DESTINATION")
}

class DevelopUgandaDeliveryBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED || intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            DevelopUgandaDeliveryQueue.schedule(context)
        }
    }
}

class DevelopUgandaDeliveryQueueActivity : AppCompatActivity() {
    private lateinit var content: LinearLayout
    private val changed = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = render()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "DELIVERY QUEUE"
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(unit() * 2, unit() * 2, unit() * 2, unit() * 4)
        }
        setContentView(ScrollView(this).apply { addView(content) })
        render()
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changed, IntentFilter(DevelopUgandaDeliveryQueue.ACTION_CHANGED), RECEIVER_NOT_EXPORTED)
        else @Suppress("DEPRECATION") registerReceiver(changed, IntentFilter(DevelopUgandaDeliveryQueue.ACTION_CHANGED))
    }

    override fun onStop() {
        runCatching { unregisterReceiver(changed) }
        super.onStop()
    }

    private fun render() {
        if (!::content.isInitialized) return
        content.removeAllViews()
        content.addView(label("DELIVERY QUEUE", 22f, DevelopUgandaFivemods8Theme.content, true))
        content.addView(label(
            "REAL OFFSETS • SHA-256 REQUIRED ON ARRIVAL • UPLOADS YIELD TO CAPTURE",
            11f,
            DevelopUgandaFivemods8Theme.contentDim,
            false,
        ))
        content.addView(action("SET DESTINATION") { editDestination() })
        content.addView(action("REFRESH STATE") { render() })
        val items = DevelopUgandaDeliveryQueue.items(this)
        if (items.isEmpty()) {
            content.addView(panel("QUEUE EMPTY", "Queue a BRAND copy from Media Vault. CLEAN needs explicit consent."))
            return
        }
        items.forEach { item -> content.addView(itemView(item)) }
    }

    private fun itemView(item: DevelopUgandaQueuedDelivery): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(unit() * 2, unit() * 2, unit() * 2, unit() * 2)
        background = surfaceDrawable(DevelopUgandaFivemods8Theme.surfaceRaised)
        val stateColor = when (item.state) {
            DevelopUgandaDeliveryState.DELIVERED -> DevelopUgandaFivemods8Theme.accent
            DevelopUgandaDeliveryState.FAILED, DevelopUgandaDeliveryState.BLOCKED -> DevelopUgandaFivemods8Theme.record
            DevelopUgandaDeliveryState.RETRYING -> DevelopUgandaFivemods8Theme.warning
            else -> DevelopUgandaFivemods8Theme.content
        }
        addView(label("${item.state.name} • ${item.mode?.name ?: "MODE UNKNOWN"}", 13f, stateColor, true))
        addView(label(item.displayName, 16f, DevelopUgandaFivemods8Theme.content, true).apply {
            typeface = Typeface.MONOSPACE
        })
        val total = item.bytesTotal.takeIf { it >= 0L }
        val progress = if (total != null) "${item.bytesSent} / $total BYTES" else "BYTES UNKNOWN"
        addView(label(progress, 13f, DevelopUgandaFivemods8Theme.contentDim, false).apply {
            typeface = Typeface.MONOSPACE
            contentDescription = "Actual upload progress $progress"
        })
        addView(label(
            "${item.destination.label} • ${item.destination.protocol.name} • ${item.networkPolicy.name}",
            11f,
            DevelopUgandaFivemods8Theme.contentDim,
            false,
        ))
        item.lastError?.let { addView(label("⚠ $it", 11f, stateColor, true)) }
        if (item.deliveredMs != null) {
            addView(label("DELIVERED ${Instant.ofEpochMilli(item.deliveredMs)} • HASH VERIFIED", 11f, stateColor, true))
        }
        val row = LinearLayout(this@DevelopUgandaDeliveryQueueActivity).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(action("SEND FIRST") { DevelopUgandaDeliveryQueue.moveToFront(this@DevelopUgandaDeliveryQueueActivity, item.id) },
            LinearLayout.LayoutParams(0, unit() * 6, 1f))
        row.addView(action("RETRY") { DevelopUgandaDeliveryQueue.retry(this@DevelopUgandaDeliveryQueueActivity, item.id) },
            LinearLayout.LayoutParams(0, unit() * 6, 1f).apply { marginStart = unit() })
        addView(row)
    }.apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = unit() }
    }

    private fun editDestination() {
        val current = DevelopUgandaDeliveryQueue.configuredDestination(this)
        val input = EditText(this).apply {
            setText(current?.endpoint.orEmpty())
            hint = "https://field-desk.example/upload"
            setTextColor(DevelopUgandaFivemods8Theme.content)
            setHintTextColor(DevelopUgandaFivemods8Theme.contentDim)
            contentDescription = "HTTPS resumable delivery endpoint"
        }
        AlertDialog.Builder(this)
            .setTitle("SET HTTPS DESTINATION")
            .setMessage("RANGE expects Content-Range acknowledgement. CHUNKS expects independently verified parts and a reassembly manifest.")
            .setSingleChoiceItems(arrayOf("RESUMABLE RANGE", "CHUNK MANIFEST"),
                if (current?.protocol == DevelopUgandaDeliveryProtocol.CHUNK_MANIFEST) 1 else 0, null)
            .setView(input)
            .setNegativeButton("CANCEL", null)
            .setPositiveButton("SAVE", null)
            .create().also { dialog ->
                dialog.setOnShowListener {
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val list = dialog.listView
                        val protocol = if (list?.checkedItemPosition == 1) DevelopUgandaDeliveryProtocol.CHUNK_MANIFEST
                        else DevelopUgandaDeliveryProtocol.RESUMABLE_RANGE
                        if (DevelopUgandaDeliveryQueue.configureDestination(this, input.text.toString(), protocol)) {
                            dialog.dismiss()
                            render()
                        } else Toast.makeText(this, "HTTPS DESTINATION REQUIRED", Toast.LENGTH_LONG).show()
                    }
                }
                dialog.show()
            }
    }

    private fun panel(title: String, detail: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(unit() * 2, unit() * 2, unit() * 2, unit() * 2)
        background = surfaceDrawable(DevelopUgandaFivemods8Theme.surface)
        addView(label(title, 16f, DevelopUgandaFivemods8Theme.content, true))
        addView(label(detail, 13f, DevelopUgandaFivemods8Theme.contentDim, false))
    }

    private fun action(text: String, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        gravity = Gravity.CENTER
        setTextColor(DevelopUgandaFivemods8Theme.content)
        contentDescription = text
        isClickable = true
        isFocusable = true
        minimumHeight = unit() * 6
        setPadding(unit() * 2, unit(), unit() * 2, unit())
        DevelopUgandaFivemods8Theme.applyTypeScale(this, 13f)
        DevelopUgandaBroadcastPresentation.styleActionSurface(
            this,
            DevelopUgandaButtonWeight.SECONDARY,
            DevelopUgandaFivemods8Theme.accent,
        )
        setOnClickListener { onClick() }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        DevelopUgandaFivemods8Theme.applyTypeScale(this, size)
        contentDescription = value
    }

    private fun surfaceDrawable(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = DevelopUgandaFivemods8Theme.radiusPx.toFloat()
        setColor(fill)
        setStroke(maxOf(1, (resources.displayMetrics.density + 0.5f).toInt()), DevelopUgandaFivemods8Theme.outline)
    }

    private fun unit() = DevelopUgandaFivemods8Theme.spacingUnitPx
}
