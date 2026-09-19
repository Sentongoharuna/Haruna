package com.sentongoharuna.pulse

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Address
import android.location.Geocoder
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExposureState
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.ZoomState
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var status: TextView
    private lateinit var timecode: TextView
    private lateinit var locationLine: TextView
    private lateinit var telemetryLine: TextView
    private lateinit var audioLine: TextView
    private lateinit var recordButton: Button
    private lateinit var lensButton: Button
    private lateinit var torchButton: Button
    private lateinit var zoomSeek: SeekBar
    private lateinit var exposureSeek: SeekBar

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var overlayEffect: OverlayEffect? = null
    private var useFront = false
    private var torch = false

    private val overlay = ReporterOverlayState()
    private val weather = WeatherRepository()
    private lateinit var telemetryRecorder: TelemetryRecorder
    private lateinit var fused: FusedLocationProviderClient

    @Volatile private var lat: Double? = null
    @Volatile private var lon: Double? = null
    @Volatile private var alt: Double? = null
    @Volatile private var accuracy: Float? = null
    @Volatile private var speedKmh: Float? = null
    @Volatile private var heading: Float? = null
    @Volatile private var placeName = "Locating…"
    @Volatile private var droppedFrames = 0L
    @Volatile private var streamHealth = 100
    @Volatile private var estimatedUploadKbps: Int? = null

    private var recStarted = 0L
    private var lastWeatherAt = 0L
    private var lastPlaceAt = 0L
    private var baseName = ""

    private val uiHandler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    private val tick = object : Runnable {
        override fun run() {
            refreshHud()
            if (recording != null) writeTelemetry()
            uiHandler.postDelayed(this, 1000)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val l = result.lastLocation ?: return
            lat = l.latitude
            lon = l.longitude
            alt = if (l.hasAltitude()) l.altitude else null
            accuracy = if (l.hasAccuracy()) l.accuracy else null
            speedKmh = if (l.hasSpeed()) l.speed * 3.6f else null
            heading = if (l.hasBearing()) l.bearing else null

            val now = System.currentTimeMillis()
            if (now - lastPlaceAt > 15_000) {
                lastPlaceAt = now
                Thread {
                    try {
                        val a: Address? = Geocoder(this@MainActivity, Locale.getDefault())
                            .getFromLocation(l.latitude, l.longitude, 1)?.firstOrNull()
                        if (a != null) {
                            val p = listOfNotNull(
                                a.subLocality,
                                a.locality,
                                a.subAdminArea,
                                a.adminArea,
                                a.countryName
                            ).distinct().joinToString(", ")
                            if (p.isNotBlank()) placeName = p
                        }
                    } catch (_: Exception) {}
                }.start()
            }

            if (now - lastWeatherAt > 10 * 60_000L) {
                lastWeatherAt = now
                weather.refresh(l.latitude, l.longitude) { }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        telemetryRecorder = TelemetryRecorder(this)
        fused = LocationServices.getFusedLocationProviderClient(this)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildUi()
        requestPermissionsAndStart()
        uiHandler.post(tick)
    }

    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(DevelopUgandaFivemods8Theme.surface) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        // TOP BROADCAST HUD
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceScrim(204))
        }
        val titleRow = row()
        titleRow.addView(hud("PULSE  •  PROFESSIONAL NEWS CAMERA", 14f, DevelopUgandaFivemods8Theme.content), weight())
        titleRow.addView(hud("● PULSE LIVE", 11f, DevelopUgandaFivemods8Theme.record), wrap(120, 40))
        top.addView(titleRow)

        val statusRow = row()
        status = hud("REC STBY", 11f, DevelopUgandaFivemods8Theme.content)
        timecode = hud("TC 00:00:00", 10f, DevelopUgandaFivemods8Theme.content)
        val fmt = hud("4K • 30FPS • HEVC • HDR", 9f, DevelopUgandaFivemods8Theme.accent)
        statusRow.addView(status, weight())
        statusRow.addView(timecode, weight())
        statusRow.addView(fmt, weight())
        top.addView(statusRow)

        top.addView(hud("${overlay.reporter} • ${overlay.station}", 9f, DevelopUgandaFivemods8Theme.accent))
        top.addView(hud(overlay.headline, 13f, DevelopUgandaFivemods8Theme.content))
        locationLine = hud("GPS acquiring…", 8.5f, DevelopUgandaFivemods8Theme.accent)
        telemetryLine = hud("WX -- • NET -- • BAT -- • FREE --", 7.7f, DevelopUgandaFivemods8Theme.accent)
        audioLine = hud("AUDIO -∞ dB • MIC AUTO", 7.7f, DevelopUgandaFivemods8Theme.accent)
        top.addView(locationLine)
        top.addView(telemetryLine)
        top.addView(audioLine)
        root.addView(top, frameTop())

        // MANUAL BAR
        val manual = row().apply {
            setPadding(dp(5), dp(2), dp(5), dp(2))
            setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceScrim(181))
        }
        listOf("ISO\n400","SHUTTER\n180°","WB\n5600K","TINT\n0","FOCUS\nAUTO","LOCK\nAE")
            .forEach { manual.addView(mini(it), weight()) }
        root.addView(manual, frameTop(dp(188)))

        // MONITORING TOOLS
        val monitor = row().apply {
            setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceScrim(153))
            setPadding(dp(3), dp(2), dp(3), dp(2))
        }
        listOf("PEAK","ZEBRA","FALSE","WAVE","HIST","LOG/LUT","GRID","LEVEL")
            .forEach { monitor.addView(hud(it, 6.6f, DevelopUgandaFivemods8Theme.accent).apply { gravity = Gravity.CENTER }, weight()) }
        root.addView(monitor, frameTop(dp(242)))

        // BOTTOM CONTROLS
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(10))
            setBackgroundColor(DevelopUgandaFivemods8Theme.surfaceScrim(224))
        }
        val readouts = row()
        readouts.addView(hud("LENS WIDE",8f,DevelopUgandaFivemods8Theme.accent), weight())
        readouts.addView(hud("STAB ON",8f,DevelopUgandaFivemods8Theme.content), weight())
        readouts.addView(hud("GAIN 0dB",8f,DevelopUgandaFivemods8Theme.content), weight())
        readouts.addView(hud("ND SIM OFF",8f,DevelopUgandaFivemods8Theme.content), weight())
        bottom.addView(readouts)

        val zrow = row()
        zrow.addView(hud("ZOOM",9f,DevelopUgandaFivemods8Theme.content), wrap(58,38))
        zoomSeek = SeekBar(this).apply { max = 100 }
        zrow.addView(zoomSeek, LinearLayout.LayoutParams(0, dp(38), 1f))
        bottom.addView(zrow)

        val erow = row()
        erow.addView(hud("EXPOSURE",9f,DevelopUgandaFivemods8Theme.content), wrap(75,38))
        exposureSeek = SeekBar(this).apply { max = 12; progress = 6 }
        erow.addView(exposureSeek, LinearLayout.LayoutParams(0, dp(38), 1f))
        bottom.addView(erow)

        val action = row()
        lensButton = actionButton("LENS")
        torchButton = actionButton("TORCH")
        val liveButton = actionButton("LIVE")
        val editButton = actionButton("EDIT")
        recordButton = actionButton("● REC").apply { setTextColor(DevelopUgandaFivemods8Theme.record) }
        action.addView(lensButton, weight())
        action.addView(torchButton, weight())
        action.addView(liveButton, weight())
        action.addView(editButton, weight())
        action.addView(recordButton, weight())
        bottom.addView(action)
        root.addView(bottom, frameBottom())

        setContentView(root)

        lensButton.setOnClickListener { if (recording == null) { useFront = !useFront; bindCamera() } }
        torchButton.setOnClickListener { toggleTorch() }
        recordButton.setOnClickListener { toggleRecording() }
        liveButton.setOnClickListener {
            DevelopUgandaV270Guidance.safeOpen(this, DevelopUgandaLiveActivity::class.java)
        }
        editButton.setOnClickListener {
            DevelopUgandaV270Guidance.safeOpen(this, DevelopUgandaEditorActivity::class.java)
        }

        zoomSeek.setOnSeekBarChangeListener(simpleSeek { applyZoom(it) })
        exposureSeek.setOnSeekBarChangeListener(simpleSeek { applyExposure(it - 6) })
    }

    private fun requestPermissionsAndStart() {
        val needed = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val missing = needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 172)
        } else startEverything()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startEverything()
    }

    private fun startEverything() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        }
        startLocation()
    }

    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setMaxUpdateDelayMillis(1000L)
            .build()
        fused.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            provider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val p = provider ?: return
        p.unbindAll()
        overlayEffect?.close()
        overlayEffect = null

        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.UHD, Quality.FHD, Quality.HD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.HD)
                )
            ).build()
        videoCapture = VideoCapture.withOutput(recorder)

        overlayEffect = OverlayEffect(
            CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE,
            0,
            Handler(Looper.getMainLooper())
        ) { throwable ->
            Toast.makeText(this, "Overlay effect warning: ${throwable.message}", Toast.LENGTH_SHORT).show()
        }.also { effect ->
            effect.setOnDrawListener { frame ->
                drawReporterOverlay(frame.overlayCanvas, frame.cropRect.width().toFloat(), frame.cropRect.height().toFloat())
                true
            }
        }

        val session = SessionConfig.Builder(preview, videoCapture!!)
            .addEffect(overlayEffect!!)
            .build()

        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        camera = p.bindToLifecycle(this, selector, session)
        syncCameraRanges()
        status.text = "REC STBY"
    }

    private fun drawReporterOverlay(c: Canvas, w: Float, h: Float) {
        if (w <= 0 || h <= 0) return
        c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        val u = minOf(w, h) / 1000f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD) }

        fill.color = 0xE6FF2D20.toInt()
        c.drawRoundRect(28*u, 28*u, 240*u, 92*u, 14*u,14*u,fill)
        text.color = Color.WHITE; text.textSize = 31*u
        c.drawText("● PULSE LIVE", 45*u, 68*u, text)

        fill.color = 0xD8000000.toInt()
        c.drawRoundRect(252*u,28*u,470*u,92*u,14*u,14*u,fill)
        text.color = 0xFFFFD45A.toInt(); text.textSize = 28*u
        c.drawText("BREAKING",270*u,67*u,text)

        text.textAlign = Paint.Align.RIGHT; text.color = Color.WHITE; text.textSize = 23*u
        c.drawText("${clock.format(Date())}  TC ${tc()}", w-28*u, 66*u, text)
        text.textAlign = Paint.Align.LEFT

        val top = h * overlay.lowerStripPercent
        fill.color = ((overlay.opacity*255).roundToInt() shl 24) or 0x000000
        c.drawRoundRect(28*u, top, w-28*u, h-30*u, 18*u,18*u,fill)

        text.color = 0xFF7CEBFF.toInt(); text.textSize = 25*u
        c.drawText("${overlay.reporter} • ${overlay.station}", 52*u, top+40*u, text)

        text.color = Color.WHITE; text.textSize = 38*u
        c.drawText(overlay.headline.take(52),52*u,top+88*u,text)

        text.color = 0xFFD6E9ED.toInt(); text.textSize = 22*u
        c.drawText(locationOverlay().take(95),52*u,top+128*u,text)

        text.color = 0xFFA4BBC0.toInt(); text.textSize = 21*u
        c.drawText(telemetryOverlay().take(105),52*u,top+162*u,text)

        // Burn the professional camera configuration/status line into the saved video.
        text.color = 0xFF8FA8AE.toInt(); text.textSize = 18*u
        c.drawText(
            "CAM 4K 30FPS HEVC HDR • SET ISO 400 SH 180° WB 5600K TINT 0 FOCUS AUTO • LENS WIDE STAB ON • MIC ON",
            52*u,
            top+190*u,
            text
        )

        if (recording != null) {
            // Red tally border is part of the exported recording, not only the screen UI.
            val tally = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFF2D20.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 8*u
            }
            c.drawRect(5*u,5*u,w-5*u,h-5*u,tally)

            fill.color = 0xEEFF2D20.toInt()
            c.drawCircle(w-52*u,h-58*u,14*u,fill)
            text.textAlign = Paint.Align.RIGHT; text.color = Color.WHITE; text.textSize = 23*u
            c.drawText("REC",w-78*u,h-50*u,text)
            text.textAlign = Paint.Align.LEFT
        }
    }

    private fun toggleRecording() {
        val vc = videoCapture ?: return
        if (recording != null) {
            recording?.stop()
            return
        }

        baseName = "PULSE_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, baseName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (android.os.Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/PULSE")
        }
        val out = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = vc.output.prepareRecording(this, out)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }

        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    recStarted = System.currentTimeMillis()
                    status.text = "● REC LIVE"
                    status.setTextColor(DevelopUgandaFivemods8Theme.record)
                    recordButton.text = "■ STOP"
                }
                is VideoRecordEvent.Status -> {
                    // CameraX recording statistics are available here if needed.
                }
                is VideoRecordEvent.Finalize -> {
                    val hadError = event.hasError()
                    recording = null
                    status.setTextColor(DevelopUgandaFivemods8Theme.content)
                    recordButton.text = "● REC"
                    if (hadError) {
                        status.text = "RECORDING ERROR"
                    } else {
                        status.text = "SAVED TO GALLERY • PULSE"
                        telemetryRecorder.exportSidecar(baseName)
                        Toast.makeText(this, "Saved to Gallery with PULSE LIVE graphics + telemetry sidecar", Toast.LENGTH_LONG).show()
                    }
                    recStarted = 0L
                }
            }
        }
    }

    private fun refreshHud() {
        timecode.text = "TC ${tc()}"
        locationLine.text = locationOverlay()
        telemetryLine.text = telemetryOverlay()
        audioLine.text = "AUDIO LIVE • MIC ${if (recording != null) "RECORDING" else "STBY"} • GAIN 0dB"
    }

    private fun writeTelemetry() {
        val now = System.currentTimeMillis()
        val elapsed = if (recStarted == 0L) 0L else now - recStarted
        val wx = weather.latest
        telemetryRecorder.add(
            TelemetrySample(
                elapsedMs = elapsed,
                timestampIso = Instant.ofEpochMilli(now).toString(),
                timezone = ZoneId.systemDefault().id,
                latitude = lat,
                longitude = lon,
                altitudeM = alt,
                accuracyM = accuracy,
                headingDeg = heading,
                speedKmh = speedKmh,
                placeName = placeName,
                temperatureC = wx.temperatureC,
                condition = wx.condition,
                humidityPct = wx.humidityPct,
                windKmh = wx.windKmh,
                windDirectionDeg = wx.windDirectionDeg,
                networkType = networkType(),
                estimatedUploadKbps = estimatedUploadKbps,
                droppedFrames = droppedFrames,
                batteryPct = batteryPct(),
                freeStorageGb = freeStorageGb()
            )
        )
    }

    private fun locationOverlay(): String {
        val b = StringBuilder(placeName)
        if (lat != null && lon != null) b.append(String.format(Locale.US," • LAT %.5f LON %.5f",lat,lon))
        if (alt != null) b.append(String.format(Locale.US," • ALT %.0fm",alt))
        if (accuracy != null) b.append(String.format(Locale.US," • ±%.0fm",accuracy))
        if (heading != null) b.append(String.format(Locale.US," • HDG %.0f°",heading))
        if (speedKmh != null) b.append(String.format(Locale.US," • SPD %.1fkm/h",speedKmh))
        return b.toString()
    }

    private fun telemetryOverlay(): String {
        val wx = weather.latest
        val w = buildString {
            if (wx.temperatureC != null) append(String.format(Locale.US,"%.1f°C ",wx.temperatureC))
            append(wx.condition)
            if (wx.humidityPct != null) append(" RH ${wx.humidityPct}%")
            if (wx.windKmh != null) append(String.format(Locale.US," WIND %.1fkm/h",wx.windKmh))
            if (wx.windDirectionDeg != null) append(String.format(Locale.US," %.0f°",wx.windDirectionDeg))
        }
        return "WX $w • NET ${networkType()} • BAT ${batteryPct() ?: "--"}% • FREE ${freeStorageGb() ?: "--"}GB"
    }

    private fun networkType(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return "OFFLINE"
        val caps = cm.getNetworkCapabilities(n) ?: return "OFFLINE"
        estimatedUploadKbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 }
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELL"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETH"
            else -> "NET"
        }
    }

    private fun batteryPct(): Int? {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it >= 0 }
    }

    private fun freeStorageGb(): Long? = try {
        StatFs(Environment.getExternalStorageDirectory().path).availableBytes / (1024L*1024L*1024L)
    } catch (_: Exception) { null }

    private fun tc(): String {
        val ms = if (recStarted == 0L) 0 else System.currentTimeMillis()-recStarted
        val s = ms/1000
        return String.format(Locale.US,"%02d:%02d:%02d",s/3600,(s%3600)/60,s%60)
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit() || useFront) return
        torch = !torch
        cam.cameraControl.enableTorch(torch)
        torchButton.text = if (torch) "TORCH ON" else "TORCH"
    }

    private fun syncCameraRanges() {
        val cam = camera ?: return
        val z: ZoomState? = cam.cameraInfo.zoomState.value
        if (z != null) {
            val p = if (z.maxZoomRatio > z.minZoomRatio)
                (((z.zoomRatio-z.minZoomRatio)/(z.maxZoomRatio-z.minZoomRatio))*100).roundToInt()
            else 0
            zoomSeek.progress = p.coerceIn(0,100)
        }
        val e: ExposureState = cam.cameraInfo.exposureState
        exposureSeek.isEnabled = e.isExposureCompensationSupported
        if (e.isExposureCompensationSupported) exposureSeek.progress = (e.exposureCompensationIndex+6).coerceIn(0,12)
    }

    private fun applyZoom(progress: Int) {
        val cam = camera ?: return
        val z = cam.cameraInfo.zoomState.value ?: return
        val ratio = z.minZoomRatio + (z.maxZoomRatio-z.minZoomRatio)*(progress/100f)
        cam.cameraControl.setZoomRatio(ratio)
    }

    private fun applyExposure(v: Int) {
        val cam = camera ?: return
        val e = cam.cameraInfo.exposureState
        if (!e.isExposureCompensationSupported) return
        cam.cameraControl.setExposureCompensationIndex(v.coerceIn(e.exposureCompensationRange.lower,e.exposureCompensationRange.upper))
    }

    private fun simpleSeek(onChange: (Int)->Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) onChange(p) }
        override fun onStartTrackingTouch(s: SeekBar?) {}
        override fun onStopTrackingTouch(s: SeekBar?) {}
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun hud(t:String,sp:Float,c:Int)=TextView(this).apply{ text=t;textSize=sp;setTextColor(c);gravity=Gravity.CENTER_VERTICAL;setPadding(dp(4),dp(2),dp(4),dp(2)) }
    private fun mini(t:String)=TextView(this).apply{ text=t;textSize=7f;setTextColor(DevelopUgandaFivemods8Theme.accent);setBackgroundColor(DevelopUgandaFivemods8Theme.transparent);setPadding(0,0,0,0);gravity=Gravity.CENTER }
    private fun actionButton(t:String)=Button(this).apply{ text=t;textSize=9f;setTextColor(DevelopUgandaFivemods8Theme.content);setBackgroundColor(DevelopUgandaFivemods8Theme.transparent);minHeight=dp(44) }
    private fun weight()=LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f)
    private fun wrap(w:Int,h:Int)=LinearLayout.LayoutParams(dp(w),dp(h))
    private fun frameTop(m:Int=0)=FrameLayout.LayoutParams(-1,-2,Gravity.TOP).apply{topMargin=m}
    private fun frameBottom()=FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM)
    private fun dp(v:Int)=Math.round(v*resources.displayMetrics.density)

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(null)
        try { fused.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        try { recording?.stop() } catch (_: Exception) {}
        try { overlayEffect?.close() } catch (_: Exception) {}
        try { provider?.unbindAll() } catch (_: Exception) {}
        super.onDestroy()
    }
}
