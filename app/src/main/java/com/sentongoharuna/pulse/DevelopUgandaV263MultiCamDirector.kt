package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * V263 multi-camera director.
 *
 * Each camera continues to record its own full-quality local master. The director only receives
 * low-bandwidth preview JPEGs and sends authenticated control commands over the same local LAN.
 * SYNC RECORD measures command completion spread; it is intentionally not labelled genlock.
 */
class DevelopUgandaV263MultiCamDirectorActivity : AppCompatActivity() {
    private val navy = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val line = DevelopUgandaFivemods8Theme.outline
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val gold = DevelopUgandaFivemods8Theme.accent
    private val green = DevelopUgandaFivemods8Theme.accent
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val red = DevelopUgandaFivemods8Theme.record

    private data class SlotUi(
        val index: Int,
        val name: String,
        val card: LinearLayout,
        val header: TextView,
        val host: EditText,
        val pin: EditText,
        val preview: ImageView,
        val connection: TextView,
        val status: TextView,
        val priority: Button,
        var lastStatus: String = "",
        var latencyMs: Long = Long.MAX_VALUE,
        var clockOffsetMs: Long? = null,
        var connected: Boolean = false,
        var recording: Boolean = false,
        var frameTick: Int = 0
    )

    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()
    private val slots = mutableListOf<SlotUi>()
    private lateinit var productionStatus: TextView
    private lateinit var groupButton: Button
    private lateinit var sceneTake: TextView
    private var alive = false
    private var selectedSlot = 0
    private var groupState = "ALL"
    private var lastSyncSpanMs: Long? = null
    private var sceneNumber = 1
    private var takeNumber = 1

    private val prefs by lazy {
        duSharedPreferences("develop_uganda_v263_multicam", Context.MODE_PRIVATE)
    }

    private val maxCameras: Int
        get() = prefs.getInt("max_cameras", 4).coerceIn(2, 4)
    private val syncRecordEnabled: Boolean
        get() = prefs.getBoolean("sync_record", true)
    private val tallyEnabled: Boolean
        get() = prefs.getBoolean("tally", true)
    private val autoQualityEnabled: Boolean
        get() = prefs.getBoolean("auto_preview_quality", true)
    private val syncSlateEnabled: Boolean
        get() = prefs.getBoolean("sync_slate", true)
    private val gridMotionEnabled: Boolean
        get() = prefs.getBoolean("grid_motion", true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        selectedSlot = prefs.getInt("selected_slot", 0).coerceIn(0, 3)
        groupState = prefs.getString("default_group", "ALL")?.uppercase(Locale.US) ?: "ALL"
        sceneNumber = prefs.getInt("director_scene", 1).coerceIn(1, 999)
        takeNumber = prefs.getInt("director_take", 1).coerceIn(1, 999)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        alive = true
        schedulePoll(80L)
        scheduleFrames(180L)
    }

    override fun onPause() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    override fun onDestroy() {
        alive = false
        handler.removeCallbacksAndMessages(null)
        io.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            setPadding(dp(14), dp(12), dp(14), dp(22))
        }
        root.addView(label("V263 • MULTI-CAM DIRECTOR", 22f, white, true))
        root.addView(label("2–4 LOCAL CAMERAS • LOCAL MASTERS STAY ON EACH CAMERA PHONE", 9.2f, gold, true).apply {
            setPadding(0, dp(3), 0, dp(10))
        })

        val overview = card().apply {
            productionStatus = label("0 CAMS • STANDBY", 11f, white, true).apply { typeface = Typeface.MONOSPACE }
            addView(productionStatus)
            addView(label("SYNC timing below is LAN command spread, not hardware genlock.", 8.4f, muted, false).apply {
                setPadding(0, dp(4), 0, dp(8))
            })
            val row = LinearLayout(this@DevelopUgandaV263MultiCamDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                groupButton = action("GROUP • $groupState", cyan) { cycleGroup() }
                addView(groupButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(5) })
                addView(action("CLOCK OFFSETS", gold) { checkClockOffsets() }, LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            addView(row)
        }
        root.addView(overview)

        val gridTitle = label("DIRECTOR GRID", 10f, gold, true).apply { setPadding(dp(2), dp(6), 0, dp(6)) }
        root.addView(gridTitle)
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (rowIndex in 0 until 2) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (col in 0 until 2) {
                val index = rowIndex * 2 + col
                val slot = createSlot(index)
                slots.add(slot)
                row.addView(slot.card, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (col == 0) rightMargin = dp(5)
                })
            }
            grid.addView(row)
        }
        root.addView(grid)
        updateSlotVisibility()
        updateSelectedSlot(false)

        val selectedControls = card().apply {
            addView(label("SELECTED CAMERA CONTROL", 11f, cyan, true))
            val recRow = LinearLayout(this@DevelopUgandaV263MultiCamDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("● RECORD", red) { sendSelected("REC_START") }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(5) })
                addView(action("■ SAFE STOP", gold) { sendSelected("REC_STOP") }, LinearLayout.LayoutParams(0, dp(44), 1f))
            }
            addView(recRow)
            addView(label("LENS / ZOOM", 8.5f, muted, true).apply { setPadding(0, dp(8), 0, dp(4)) })
            val zoomRow = LinearLayout(this@DevelopUgandaV263MultiCamDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("WIDE" to 0f, "1×" to 0.25f, "TELE" to 0.60f, "MAX" to 1f).forEachIndexed { idx, item ->
                    addView(action(item.first, line) { sendSelected(String.format(Locale.US, "ZOOM %.3f", item.second)) },
                        LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (idx < 3) rightMargin = dp(3) })
                }
            }
            addView(zoomRow)
        }
        root.addView(selectedControls)

        val sync = card().apply {
            addView(label("SYNC RECORD + MASTER SLATE", 11f, cyan, true))
            val recRow = LinearLayout(this@DevelopUgandaV263MultiCamDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("SYNC START", red) { sendGroup("REC_START", true) }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(5) })
                addView(action("SYNC SAFE STOP", gold) { sendGroup("REC_STOP", true) }, LinearLayout.LayoutParams(0, dp(46), 1f))
            }
            addView(recRow)
            sceneTake = label(slateLabel(), 11f, white, true).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(9), 0, dp(5))
            }
            addView(sceneTake)
            val slateRow = LinearLayout(this@DevelopUgandaV263MultiCamDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("NEXT TAKE", cyan) { nextTake() }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(4) })
                addView(action("NEXT SCENE", gold) { nextScene() }, LinearLayout.LayoutParams(0, dp(42), 1f))
            }
            addView(slateRow)
            addView(label("GROUP MARKERS", 8.5f, muted, true).apply { setPadding(0, dp(8), 0, dp(4)) })
            val markRow = LinearLayout(this@DevelopUgandaV263MultiCamDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("BEST", "RETAKE", "B-ROLL", "QUOTE").forEachIndexed { idx, mark ->
                    addView(action(mark, if (mark == "BEST") green else line) { sendGroup("MARK $mark", false) },
                        LinearLayout.LayoutParams(0, dp(39), 1f).apply { if (idx < 3) rightMargin = dp(3) })
                }
            }
            addView(markRow)
        }
        root.addView(sync)

        val note = card().apply {
            addView(label("V263 CONNECTION POLICY", 10f, gold, true))
            addView(label("Lost cameras are retried automatically. Auto Preview Quality reduces frame polling when a camera becomes slow; it never changes that camera's local recording quality.", 8.7f, muted, false).apply {
                setPadding(0, dp(4), 0, 0)
            })
        }
        root.addView(note)

        val scroll = ScrollView(this).apply { isFillViewport = false }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun createSlot(index: Int): SlotUi {
        val name = "CAM ${('A'.code + index).toChar()}"
        val slotCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = rounded(panel, line, 15, 1)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(5)
            }
        }
        val header = label(name, 10.5f, white, true)
        slotCard.addView(header)
        val host = edit("IP", prefs.getString("slot_${index}_host", "") ?: "")
        val pin = edit("PIN", prefs.getString("slot_${index}_pin", "") ?: "")
        host.textSize = 9f
        pin.textSize = 9f
        slotCard.addView(host, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { bottomMargin = dp(3) })
        slotCard.addView(pin, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38)).apply { bottomMargin = dp(4) })
        val preview = ImageView(this).apply {
            setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "$name preview. Tap to select and meter focus/exposure."
        }
        slotCard.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(130)))
        val connection = label("OFFLINE", 7.9f, muted, true).apply { setPadding(0, dp(4), 0, 0) }
        val status = label("STBY", 7.7f, muted, false).apply {
            typeface = Typeface.MONOSPACE
            maxLines = 3
        }
        slotCard.addView(connection)
        slotCard.addView(status)
        val priority = action(priorityFor(index), line) { cyclePriority(index) }
        slotCard.addView(priority, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(34)).apply { topMargin = dp(4) })

        val slot = SlotUi(index, name, slotCard, header, host, pin, preview, connection, status, priority)
        slotCard.setOnClickListener { selectSlot(index) }
        preview.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_UP && v.width > 0 && v.height > 0) {
                selectSlot(index)
                val nx = (event.x / v.width.toFloat()).coerceIn(0f, 1f)
                val ny = (event.y / v.height.toFloat()).coerceIn(0f, 1f)
                sendTo(slot, String.format(Locale.US, "FOCUS %.4f %.4f", nx, ny)) { result ->
                    runOnUiThread { slot.connection.text = result }
                }
            }
            true
        }
        return slot
    }

    private fun updateSlotVisibility() {
        slots.forEach { it.card.visibility = if (it.index < maxCameras) View.VISIBLE else View.GONE }
        if (selectedSlot >= maxCameras) selectedSlot = 0
    }

    private fun selectSlot(index: Int) {
        if (index !in slots.indices || index >= maxCameras) return
        selectedSlot = index
        prefs.edit().putInt("selected_slot", index).apply()
        updateSelectedSlot(gridMotionEnabled)
    }

    private fun updateSelectedSlot(animate: Boolean) {
        slots.forEach { slot ->
            val selected = slot.index == selectedSlot
            slot.header.text = if (selected) "${slot.name} • SELECTED" else slot.name
            slot.header.setTextColor(if (selected) gold else white)
            updateSlotBorder(slot)
            if (animate && selected) {
                slot.card.animate().scaleX(1.025f).scaleY(1.025f).setDuration(100L).withEndAction {
                    slot.card.animate().scaleX(1f).scaleY(1f).setDuration(150L).start()
                }.start()
            }
        }
        updateProductionStatus()
    }

    private fun updateSlotBorder(slot: SlotUi) {
        val selected = slot.index == selectedSlot
        val stroke = when {
            tallyEnabled && slot.recording -> red
            selected -> gold
            slot.connected -> cyan
            else -> line
        }
        slot.card.background = rounded(panel, stroke, 15, if (selected || slot.recording) 2 else 1)
    }

    private fun cycleGroup() {
        val next = when (groupState) {
            "ALL" -> "A+B"
            "A+B" -> "C+D"
            "C+D" -> "SELECTED"
            else -> "ALL"
        }
        groupState = next
        prefs.edit().putString("default_group", next).apply()
        groupButton.text = "GROUP • $groupState"
        updateProductionStatus()
    }

    private fun activeGroupSlots(): List<SlotUi> {
        val visible = slots.filter { it.index < maxCameras && it.host.text?.toString()?.trim()?.isNotBlank() == true }
        return when (groupState) {
            "A+B" -> visible.filter { it.index <= 1 }
            "C+D" -> visible.filter { it.index >= 2 }
            "SELECTED" -> visible.filter { it.index == selectedSlot }
            else -> visible
        }
    }

    private fun schedulePoll(delay: Long) {
        handler.postDelayed({
            if (!alive) return@postDelayed
            pollAllStatus()
            schedulePoll(950L)
        }, delay)
    }

    private fun scheduleFrames(delay: Long) {
        handler.postDelayed({
            if (!alive) return@postDelayed
            requestFrames()
            scheduleFrames(520L)
        }, delay)
    }

    private fun pollAllStatus() {
        slots.filter { it.index < maxCameras }.forEach { slot ->
            saveSlot(slot)
            val host = slot.host.text?.toString()?.trim().orEmpty()
            val pin = slot.pin.text?.toString()?.trim().orEmpty()
            if (host.isBlank() || pin.length < 4) {
                slot.connected = false
                slot.recording = false
                runOnUiThread {
                    slot.connection.text = "ENTER IP + PIN"
                    slot.connection.setTextColor(muted)
                    updateSlotBorder(slot)
                    updateProductionStatus()
                }
                return@forEach
            }
            val started = System.currentTimeMillis()
            io.execute {
                val result = requestText(host, pin, "STATUS", null)
                val latency = (System.currentTimeMillis() - started).coerceAtLeast(0L)
                runOnUiThread {
                    if (!alive) return@runOnUiThread
                    slot.latencyMs = latency
                    slot.connected = result != null
                    slot.lastStatus = result.orEmpty()
                    slot.recording = result?.contains("RECORDING • ON") == true
                    if (result != null) {
                        slot.connection.text = "${qualityLabel(latency)} • ${latency}ms${slot.clockOffsetMs?.let { " • Δ${signed(it)}ms" } ?: ""}"
                        slot.connection.setTextColor(if (latency < 600L) green else gold)
                        slot.status.text = compactStatus(result)
                    } else {
                        slot.connection.text = "RECONNECTING…"
                        slot.connection.setTextColor(red)
                        slot.status.text = "LAST ${slot.lastStatus.take(70).ifBlank { "--" }}"
                    }
                    updateSlotBorder(slot)
                    updateProductionStatus()
                }
            }
        }
    }

    private fun requestFrames() {
        slots.filter { it.index < maxCameras }.forEach { slot ->
            val host = slot.host.text?.toString()?.trim().orEmpty()
            val pin = slot.pin.text?.toString()?.trim().orEmpty()
            if (host.isBlank() || pin.length < 4 || !slot.connected) return@forEach
            slot.frameTick += 1
            val slow = autoQualityEnabled && slot.latencyMs > 650L
            val verySlow = autoQualityEnabled && slot.latencyMs > 1100L
            val selected = slot.index == selectedSlot
            val shouldPull = when {
                selected -> !verySlow || slot.frameTick % 2 == 0
                verySlow -> slot.frameTick % 5 == 0
                slow -> slot.frameTick % 3 == 0
                else -> slot.frameTick % 2 == 0
            }
            if (!shouldPull) return@forEach
            io.execute {
                val bytes = requestJpeg(host, pin)
                if (bytes != null && bytes.isNotEmpty()) {
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    runOnUiThread {
                        if (alive && bitmap != null) slot.preview.setImageBitmap(bitmap)
                    }
                }
            }
        }
    }

    private fun sendSelected(command: String) {
        val slot = slots.getOrNull(selectedSlot) ?: return
        sendTo(slot, command) { result ->
            runOnUiThread {
                slot.connection.text = result
                handler.postDelayed({ pollAllStatus() }, 180L)
            }
        }
    }

    private fun sendGroup(command: String, recordCommand: Boolean) {
        val targets = activeGroupSlots()
        if (targets.isEmpty()) {
            productionStatus.text = "NO CAMERAS IN $groupState"
            return
        }
        if (recordCommand && !syncRecordEnabled) {
            productionStatus.text = "SYNC RECORD OFF IN PRO SETTINGS"
            return
        }
        val endpoints = targets.map { slot -> Triple(slot, slot.host.text.toString().trim(), slot.pin.text.toString().trim()) }
        io.execute {
            val latch = CountDownLatch(endpoints.size)
            val completion = java.util.Collections.synchronizedList(mutableListOf<Long>())
            endpoints.forEach { endpoint ->
                val slot = endpoint.first
                val host = endpoint.second
                val pin = endpoint.third
                io.execute {
                    val started = System.currentTimeMillis()
                    val result = requestText(host, pin, "COMMAND", command)
                    completion.add(System.currentTimeMillis())
                    val elapsed = System.currentTimeMillis() - started
                    runOnUiThread {
                        slot.connection.text = result ?: "NO RESPONSE"
                        slot.connection.setTextColor(if (result != null) gold else red)
                        slot.latencyMs = elapsed
                    }
                    latch.countDown()
                }
            }
            latch.await(4L, TimeUnit.SECONDS)
            val spread = if (completion.size >= 2) (completion.maxOrNull()!! - completion.minOrNull()!!) else 0L
            if (recordCommand) lastSyncSpanMs = spread
            runOnUiThread {
                productionStatus.text = if (recordCommand) {
                    "${targets.size} CAMS • ${if (command == "REC_START") "START" else "STOP"} COMMAND SPAN ${spread}ms"
                } else {
                    "${targets.size} CAMS • COMMAND SENT"
                }
                handler.postDelayed({ pollAllStatus() }, 220L)
            }
        }
    }

    private fun nextTake() {
        takeNumber = (takeNumber + 1).coerceAtMost(999)
        prefs.edit().putInt("director_take", takeNumber).apply()
        sceneTake.text = slateLabel()
        if (syncSlateEnabled) sendGroup("SET_TAKE $takeNumber", false)
    }

    private fun nextScene() {
        sceneNumber = (sceneNumber + 1).coerceAtMost(999)
        takeNumber = 1
        prefs.edit().putInt("director_scene", sceneNumber).putInt("director_take", takeNumber).apply()
        sceneTake.text = slateLabel()
        if (syncSlateEnabled) {
            sendGroup("SET_SCENE $sceneNumber", false)
            handler.postDelayed({ sendGroup("SET_TAKE $takeNumber", false) }, 160L)
        }
    }

    private fun checkClockOffsets() {
        val targets = activeGroupSlots()
        if (targets.isEmpty()) {
            productionStatus.text = "NO CAMERAS FOR CLOCK CHECK"
            return
        }
        val endpoints = targets.map { slot -> Triple(slot, slot.host.text.toString().trim(), slot.pin.text.toString().trim()) }
        endpoints.forEach { endpoint ->
            val slot = endpoint.first
            val host = endpoint.second
            val pin = endpoint.third
            io.execute {
                val t0 = System.currentTimeMillis()
                val response = requestText(host, pin, "COMMAND", "SYNC")
                val t1 = System.currentTimeMillis()
                val remote = response?.let { parseRemoteClock(it) }
                val midpoint = t0 + ((t1 - t0) / 2L)
                slot.clockOffsetMs = remote?.minus(midpoint)
                runOnUiThread {
                    slot.connection.text = if (slot.clockOffsetMs != null) {
                        "CLOCK Δ${signed(slot.clockOffsetMs!!)}ms • RTT ${t1 - t0}ms"
                    } else {
                        "CLOCK CHECK FAILED"
                    }
                    updateProductionStatus()
                }
            }
        }
    }

    private fun sendTo(slot: SlotUi, command: String, done: (String) -> Unit = {}) {
        saveSlot(slot)
        val host = slot.host.text?.toString()?.trim().orEmpty()
        val pin = slot.pin.text?.toString()?.trim().orEmpty()
        if (host.isBlank() || pin.length < 4) {
            done("ENTER IP + PIN")
            return
        }
        io.execute {
            val result = requestText(host, pin, "COMMAND", command) ?: "NO RESPONSE"
            done(result)
        }
    }

    private fun requestText(host: String, pin: String, action: String, command: String?): String? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, DevelopUgandaV262Network.PORT), 1700)
                socket.soTimeout = 3000
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                out.writeUTF(DevelopUgandaV262Network.MAGIC)
                out.writeUTF(pin)
                out.writeUTF(action)
                if (command != null) out.writeUTF(command)
                out.flush()
                when (input.readUTF()) {
                    "TEXT" -> input.readUTF()
                    "JPEG" -> {
                        val length = input.readInt().coerceIn(0, 4_000_000)
                        val bytes = ByteArray(length)
                        input.readFully(bytes)
                        "JPEG ${bytes.size}"
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun requestJpeg(host: String, pin: String): ByteArray? {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, DevelopUgandaV262Network.PORT), 1700)
                socket.soTimeout = 3000
                val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                out.writeUTF(DevelopUgandaV262Network.MAGIC)
                out.writeUTF(pin)
                out.writeUTF("FRAME")
                out.flush()
                when (input.readUTF()) {
                    "JPEG" -> {
                        val length = input.readInt()
                        if (length <= 0 || length > 4_000_000) return null
                        ByteArray(length).also { input.readFully(it) }
                    }
                    "TEXT" -> {
                        input.readUTF()
                        null
                    }
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateProductionStatus() {
        if (!::productionStatus.isInitialized) return
        val visible = slots.filter { it.index < maxCameras }
        val connected = visible.count { it.connected }
        val recording = visible.count { it.recording }
        val selected = slots.getOrNull(selectedSlot)?.name ?: "CAM A"
        val sync = lastSyncSpanMs?.let { " • SPAN ${it}ms" } ?: ""
        productionStatus.text = "$connected CAMS • $recording REC • $selected • $groupState • ${slateLabel()}$sync"
    }

    private fun compactStatus(value: String): String {
        val recording = if (value.contains("RECORDING • ON")) "REC" else "STBY"
        val cam = Regex("CAMERA [^•\\n]+", RegexOption.IGNORE_CASE).find(value)?.value ?: ""
        val bat = Regex("BAT [^•\\n]+", RegexOption.IGNORE_CASE).find(value)?.value ?: ""
        val free = Regex("FREE [^•\\n]+", RegexOption.IGNORE_CASE).find(value)?.value ?: ""
        val thermal = Regex("THERMAL [^•\\n]+", RegexOption.IGNORE_CASE).find(value)?.value ?: ""
        return listOf(recording, cam, bat, free, thermal).filter { it.isNotBlank() }.joinToString(" • ").take(150)
    }

    private fun parseRemoteClock(value: String): Long? = Regex("CLOCK\\s+(\\d{10,})").find(value)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun saveSlot(slot: SlotUi) {
        prefs.edit()
            .putString("slot_${slot.index}_host", slot.host.text?.toString()?.trim().orEmpty())
            .putString("slot_${slot.index}_pin", slot.pin.text?.toString()?.trim().orEmpty())
            .apply()
    }

    private fun cyclePriority(index: Int) {
        val current = priorityFor(index)
        val next = when (current) {
            "MASTER" -> "SECONDARY"
            "SECONDARY" -> "B-ROLL"
            else -> "MASTER"
        }
        prefs.edit().putString("slot_${index}_priority", next).apply()
        slots.getOrNull(index)?.priority?.text = next
    }

    private fun priorityFor(index: Int): String = prefs.getString("slot_${index}_priority", if (index == 0) "MASTER" else "SECONDARY") ?: "SECONDARY"

    private fun slateLabel(): String = "S%03d / T%03d".format(Locale.US, sceneNumber, takeNumber)

    private fun qualityLabel(ms: Long): String = when {
        ms < 170L -> "EXCELLENT"
        ms < 380L -> "GOOD"
        ms < 720L -> "WEAK"
        else -> "VERY WEAK"
    }

    private fun signed(value: Long): String = if (value >= 0L) "+$value" else value.toString()

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(11), dp(12), dp(11))
        background = rounded(panel, line, 18, 1)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(9)
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean): TextView = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun edit(hintText: String, value: String): EditText = EditText(this).apply {
        hint = hintText
        setHintTextColor(DevelopUgandaFivemods8Theme.contentDim)
        setTextColor(white)
        textSize = 10f
        setSingleLine(true)
        setText(value)
        setPadding(dp(8), 0, dp(8), 0)
        background = rounded(DevelopUgandaFivemods8Theme.surface, line, 10, 1)
    }

    private fun action(textValue: String, accent: Int, click: () -> Unit): Button = Button(this).apply {
        text = textValue
        textSize = 8.3f
        setTextColor(white)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = rounded(DevelopUgandaFivemods8Theme.surface, accent, 12, 1)
        setOnClickListener {
            if (gridMotionEnabled) {
                animate().scaleX(0.96f).scaleY(0.96f).setDuration(70L).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
                }.start()
            }
            DevelopUgandaV28012SafeActions.run(context, "MULTI-CAM BUTTON") { click() }
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(strokeWidth), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt().coerceAtLeast(1)
}
