package com.sentongoharuna.pulse

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * V262 local-LAN Remote Director foundation.
 *
 * No cloud service is used. The camera phone hosts a small authenticated TCP server on port 8262.
 * The director phone connects over the same Wi-Fi / hotspot. Preview frames are low-resolution
 * JPEG snapshots from PreviewView only; CameraX continues writing the full local master normally.
 */
object DevelopUgandaV262Network {
    const val PORT = 8262
    const val MAGIC = "DU262"

    fun localIpv4(): String {
        return try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            val preferred = mutableListOf<InetAddress>()
            val fallback = mutableListOf<InetAddress>()
            for (network in interfaces) {
                if (!network.isUp || network.isLoopback) continue
                for (address in Collections.list(network.inetAddresses)) {
                    if (address is Inet4Address && !address.isLoopbackAddress) {
                        if (address.isSiteLocalAddress) preferred.add(address) else fallback.add(address)
                    }
                }
            }
            (preferred.firstOrNull() ?: fallback.firstOrNull())?.hostAddress ?: "NO LAN IP"
        } catch (_: Exception) {
            "NO LAN IP"
        }
    }
}

class DevelopUgandaV262RemoteServer(
    private val activity: DevelopUgandaCameraActivity
) {
    private val running = AtomicBoolean(false)
    private val acceptExecutor = Executors.newSingleThreadExecutor()
    private val clientExecutor = Executors.newCachedThreadPool()
    @Volatile private var serverSocket: ServerSocket? = null
    @Volatile var lastClientIp: String = "--"
        private set
    @Volatile var lastClientAt: Long = 0L
        private set
    @Volatile var requests: Long = 0L
        private set
    @Volatile var lastError: String = ""
        private set

    fun isRunning(): Boolean = running.get()

    fun start() {
        if (!running.compareAndSet(false, true)) return
        acceptExecutor.execute {
            try {
                val socket = ServerSocket(DevelopUgandaV262Network.PORT).apply {
                    reuseAddress = true
                }
                serverSocket = socket
                while (running.get()) {
                    val client = try {
                        socket.accept()
                    } catch (e: Exception) {
                        if (running.get()) throw e
                        break
                    }
                    clientExecutor.execute { handle(client) }
                }
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                running.set(false)
            } finally {
                try { serverSocket?.close() } catch (_: Exception) {}
                serverSocket = null
            }
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
    }

    private fun handle(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 3500
                val input = DataInputStream(BufferedInputStream(client.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(client.getOutputStream()))
                val magic = input.readUTF()
                val pin = input.readUTF()
                val action = input.readUTF().uppercase(Locale.US)

                if (magic != DevelopUgandaV262Network.MAGIC) {
                    writeText(output, "DENIED • PROTOCOL")
                    return
                }
                if (pin != activity.v262RemotePin()) {
                    writeText(output, "DENIED • PIN")
                    return
                }

                lastClientIp = client.inetAddress?.hostAddress ?: "--"
                lastClientAt = System.currentTimeMillis()
                requests += 1L

                when (action) {
                    "STATUS" -> writeText(output, activity.v262RemoteStatusSnapshot())
                    "FRAME" -> {
                        if (!activity.v262RemotePreviewEnabled()) {
                            writeText(output, "PREVIEW OFF")
                        } else {
                            val bytes = activity.v262RemotePreviewFrame()
                            if (bytes == null || bytes.isEmpty()) {
                                writeText(output, "FRAME NOT READY")
                            } else {
                                output.writeUTF("JPEG")
                                output.writeInt(bytes.size)
                                output.write(bytes)
                                output.flush()
                            }
                        }
                    }
                    "PROXY_META" -> {
                        if (!DevelopUgandaV265ProxyManager.enabled(activity)) {
                            writeText(output, "PROXY OFF")
                        } else {
                            val meta = DevelopUgandaV265ProxyManager.latestMeta(activity)
                            if (meta.isBlank()) writeText(output, DevelopUgandaV265ProxyManager.state(activity))
                            else writeText(output, meta)
                        }
                    }
                    "PROXY" -> {
                        if (!DevelopUgandaV265ProxyManager.enabled(activity)) {
                            writeText(output, "PROXY OFF")
                        } else if (activity.v262RemoteStatusSnapshot().contains("RECORDING • ON")) {
                            writeText(output, "WAIT • CAMERA RECORDING • MASTER PRIORITY")
                        } else {
                            val file = DevelopUgandaV265ProxyManager.latestFile(activity)
                            if (file == null || !file.exists() || file.length() <= 0L) {
                                writeText(output, "NO PROXY READY")
                            } else {
                                output.writeUTF("FILE")
                                output.writeLong(file.length())
                                file.inputStream().buffered(64 * 1024).use { inputFile ->
                                    inputFile.copyTo(output, 64 * 1024)
                                }
                                output.flush()
                            }
                        }
                    }
                    "COMMAND" -> {
                        val command = input.readUTF()
                        if (!activity.v262RemoteControlEnabled()) {
                            writeText(output, "REMOTE CONTROL OFF")
                        } else {
                            writeText(output, activity.v262RemoteCommandSync(command))
                        }
                    }
                    else -> writeText(output, "UNKNOWN REQUEST")
                }
            } catch (_: Exception) {
                // A dropped remote connection must never interrupt local recording.
            }
        }
    }

    private fun writeText(output: DataOutputStream, text: String) {
        output.writeUTF("TEXT")
        output.writeUTF(text)
        output.flush()
    }

    fun statusLine(): String {
        val state = if (isRunning()) "HOST LIVE" else "HOST OFF"
        val peer = if (lastClientAt > 0L) " • LAST $lastClientIp" else " • NO DIRECTOR"
        val error = if (lastError.isNotBlank()) " • $lastError" else ""
        return "$state • ${DevelopUgandaV262Network.localIpv4()}:${DevelopUgandaV262Network.PORT}$peer$error"
    }
}

class DevelopUgandaV262RemoteDirectorActivity : AppCompatActivity() {
    private val navy = DevelopUgandaFivemods8Theme.surface
    private val panel = DevelopUgandaFivemods8Theme.surface
    private val line = DevelopUgandaFivemods8Theme.outline
    private val cyan = DevelopUgandaFivemods8Theme.content
    private val gold = DevelopUgandaFivemods8Theme.accent
    private val green = DevelopUgandaFivemods8Theme.accent
    private val white = DevelopUgandaFivemods8Theme.content
    private val muted = DevelopUgandaFivemods8Theme.contentDim
    private val red = DevelopUgandaFivemods8Theme.record

    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var connection: TextView
    private lateinit var hostInput: EditText
    private lateinit var pinInput: EditText
    private lateinit var startButton: Button
    private lateinit var stopButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)
    @Volatile private var alive = false
    @Volatile private var lastLatencyMs = 0L

    private val prefs by lazy {
        duSharedPreferences("develop_uganda_v262_remote", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES), 262)
        }
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        alive = true
        scheduleStatus(80L)
        schedulePreview(180L)
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
            setPadding(dp(14), dp(12), dp(14), dp(18))
        }

        root.addView(label("V262 • REMOTE DIRECTOR", 22f, white, true))
        root.addView(label("LOCAL Wi-Fi / HOTSPOT • MASTER RECORDING STAYS ON CAMERA PHONE", 9.4f, gold, true).apply {
            setPadding(0, dp(3), 0, dp(10))
        })

        val pair = card().apply {
            addView(label("PAIR CAMERA", 11f, cyan, true))
            addView(label("On the camera phone enable Remote Camera Host in Pro Settings. Enter its LAN IP and 4-digit PIN here.", 9f, muted, false).apply {
                setPadding(0, dp(4), 0, dp(8))
            })

            hostInput = edit("CAMERA IP", prefs.getString("last_host", "") ?: "")
            pinInput = edit("PIN", prefs.getString("last_pin", "") ?: "")
            addView(hostInput)
            addView(pinInput)

            val row = LinearLayout(this@DevelopUgandaV262RemoteDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("CONNECT / REFRESH", cyan) {
                    savePairing()
                    requestStatus()
                }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
                addView(action("CLOCK / TC CHECK", gold) {
                    sendCommand("SYNC") { result -> toastLine(result) }
                }, LinearLayout.LayoutParams(0, dp(46), 1f))
            }
            addView(row)
            connection = label("DISCONNECTED", 9.5f, muted, true).apply { setPadding(0, dp(8), 0, 0) }
            addView(connection)
        }
        root.addView(pair)

        val previewCard = card().apply {
            setPadding(dp(8), dp(8), dp(8), dp(8))
            preview = ImageView(this@DevelopUgandaV262RemoteDirectorActivity).apply {
                setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Remote camera preview. Tap to meter focus and exposure."
                setOnTouchListener { v, event ->
                    if (event.action == MotionEvent.ACTION_UP && v.width > 0 && v.height > 0) {
                        val nx = (event.x / v.width.toFloat()).coerceIn(0f, 1f)
                        val ny = (event.y / v.height.toFloat()).coerceIn(0f, 1f)
                        sendCommand(String.format(Locale.US, "FOCUS %.4f %.4f", nx, ny)) { result -> toastLine(result) }
                    }
                    true
                }
            }
            addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(300)))
            addView(label("TAP PREVIEW • REMOTE AF + AE + AWB", 8.5f, muted, true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(6), 0, 0)
            })
        }
        root.addView(previewCard)

        val controls = card().apply {
            addView(label("REMOTE CONTROL", 11f, cyan, true))
            val recordRow = LinearLayout(this@DevelopUgandaV262RemoteDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                startButton = action("● START RECORD", red) { sendCommand("REC_START") { requestStatus() } }
                stopButton = action("■ STOP / SAFE", gold) { sendCommand("REC_STOP") { requestStatus() } }
                addView(startButton, LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(6) })
                addView(stopButton, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            addView(recordRow)

            addView(label("REMOTE ZOOM POSITION", 8.5f, muted, true).apply { setPadding(0, dp(9), 0, dp(4)) })
            val zoomRow = LinearLayout(this@DevelopUgandaV262RemoteDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("WIDE" to 0f, "1×" to 0.25f, "TELE" to 0.60f, "MAX" to 1f).forEachIndexed { index, pair ->
                    addView(action(pair.first, line) {
                        sendCommand(String.format(Locale.US, "ZOOM %.3f", pair.second)) { result -> toastLine(result) }
                    }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { if (index < 3) rightMargin = dp(4) })
                }
            }
            addView(zoomRow)

            addView(label("PROJECT / SLATE", 8.5f, muted, true).apply { setPadding(0, dp(9), 0, dp(4)) })
            val slateRow = LinearLayout(this@DevelopUgandaV262RemoteDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("NEXT TAKE", cyan) { sendCommand("NEXT_TAKE") { requestStatus() } }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { rightMargin = dp(4) })
                addView(action("NEXT SCENE", gold) { sendCommand("NEXT_SCENE") { requestStatus() } }, LinearLayout.LayoutParams(0, dp(42), 1f))
            }
            addView(slateRow)

            addView(label("DIRECTOR MARKERS", 8.5f, muted, true).apply { setPadding(0, dp(9), 0, dp(4)) })
            val markerRow = LinearLayout(this@DevelopUgandaV262RemoteDirectorActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                listOf("BEST", "RETAKE", "B-ROLL", "QUOTE").forEachIndexed { index, mark ->
                    addView(action(mark, if (mark == "BEST") green else line) {
                        sendCommand("MARK $mark") { result -> toastLine(result) }
                    }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { if (index < 3) rightMargin = dp(4) })
                }
            }
            addView(markerRow)
        }
        root.addView(controls)

        val live = card().apply {
            addView(label("CAMERA LIVE STATUS", 11f, cyan, true))
            status = label("WAITING FOR CAMERA…", 9.5f, white, false).apply {
                typeface = Typeface.MONOSPACE
                setPadding(0, dp(6), 0, 0)
            }
            addView(status)
        }
        root.addView(live)

        val scroll = ScrollView(this).apply { isFillViewport = false }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return scroll
    }

    private fun scheduleStatus(delay: Long) {
        handler.postDelayed({
            if (!alive) return@postDelayed
            requestStatus()
            if (prefs.getBoolean("auto_reconnect", true)) scheduleStatus(850L)
        }, delay)
    }

    private fun schedulePreview(delay: Long) {
        handler.postDelayed({
            if (!alive) return@postDelayed
            requestFrame()
            schedulePreview(420L)
        }, delay)
    }

    private fun requestStatus() {
        val host = currentHost() ?: return
        val pin = currentPin() ?: return
        val started = System.currentTimeMillis()
        io.execute {
            val result = requestText(host, pin, "STATUS", null)
            lastLatencyMs = (System.currentTimeMillis() - started).coerceAtLeast(0L)
            runOnUiThread {
                if (!alive) return@runOnUiThread
                if (result != null) {
                    status.text = result
                    connection.text = "${qualityLabel(lastLatencyMs)} • ${lastLatencyMs}ms • $host:${DevelopUgandaV262Network.PORT}"
                    connection.setTextColor(if (lastLatencyMs < 550L) green else gold)
                    val recOn = result.contains("RECORDING • ON")
                    startButton.alpha = if (recOn) 0.55f else 1f
                    stopButton.alpha = if (recOn) 1f else 0.7f
                } else {
                    connection.text = "RECONNECTING • $host:${DevelopUgandaV262Network.PORT}"
                    connection.setTextColor(red)
                }
            }
        }
    }

    private fun requestFrame() {
        val host = currentHost() ?: return
        val pin = currentPin() ?: return
        io.execute {
            val bytes = requestJpeg(host, pin)
            if (bytes != null && bytes.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                runOnUiThread {
                    if (alive && bitmap != null) preview.setImageBitmap(bitmap)
                }
            }
        }
    }

    private fun sendCommand(command: String, done: (String) -> Unit = {}) {
        val host = currentHost() ?: run { toastLine("ENTER CAMERA IP"); return }
        val pin = currentPin() ?: run { toastLine("ENTER PIN"); return }
        savePairing()
        io.execute {
            val result = requestText(host, pin, "COMMAND", command) ?: "NO RESPONSE"
            runOnUiThread { if (alive) done(result) }
        }
    }

    private fun requestText(host: String, pin: String, action: String, command: String?): String? {
        return try {
            Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, DevelopUgandaV262Network.PORT), 1800)
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
                socket.connect(java.net.InetSocketAddress(host, DevelopUgandaV262Network.PORT), 1800)
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

    private fun currentHost(): String? = hostInput.text?.toString()?.trim()?.takeIf { it.isNotBlank() }
    private fun currentPin(): String? = pinInput.text?.toString()?.trim()?.takeIf { it.length >= 4 }

    private fun savePairing() {
        prefs.edit()
            .putString("last_host", hostInput.text?.toString()?.trim().orEmpty())
            .putString("last_pin", pinInput.text?.toString()?.trim().orEmpty())
            .apply()
    }

    private fun qualityLabel(ms: Long): String = when {
        ms < 150L -> "EXCELLENT"
        ms < 350L -> "GOOD"
        ms < 700L -> "WEAK"
        else -> "VERY WEAK"
    }

    private fun toastLine(text: String) {
        connection.text = text
        connection.setTextColor(gold)
    }

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
        textSize = 11f
        setSingleLine(true)
        setText(value)
        setPadding(dp(10), 0, dp(10), 0)
        background = rounded(DevelopUgandaFivemods8Theme.surface, line, 12, 1)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(6) }
    }

    private fun action(textValue: String, accent: Int, click: () -> Unit): Button = Button(this).apply {
        text = textValue
        textSize = 8.8f
        setTextColor(white)
        typeface = Typeface.DEFAULT_BOLD
        isAllCaps = false
        background = rounded(DevelopUgandaFivemods8Theme.surface, accent, 13, 1)
        setOnClickListener {
            animate().scaleX(0.96f).scaleY(0.96f).setDuration(70L).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(110L).start()
            }.start()
            DevelopUgandaV28012SafeActions.run(context, "REMOTE DIRECTOR BUTTON") { click() }
        }
    }

    private fun rounded(fill: Int, stroke: Int, radius: Int, strokeWidth: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(fill)
        if (strokeWidth > 0) setStroke(dp(strokeWidth), stroke)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
