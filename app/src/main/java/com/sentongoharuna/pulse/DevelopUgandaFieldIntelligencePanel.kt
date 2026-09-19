package com.sentongoharuna.pulse

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * V236 Multi-Format + Satellite Field Intelligence.
 *
 * This layer does not replace the V235 LUT engine or CameraX recording path.
 * It adds platform orientation locks, safe-frame guides, GNSS satellite status,
 * horizon information and an offline sun-position field aid.
 */
object DevelopUgandaFieldIntelligencePanel {

    private const val TAG = "develop_uganda_v236_field_intelligence"
    private const val PREFS = "develop_uganda_v236_field_intelligence"
    private const val KEY_FORMAT = "format_index"

    data class FormatProfile(
        val id: String,
        val label: String,
        val detail: String,
        val landscape: Boolean,
        val guideRatio: Float,
        val dualSafe: Boolean = false
    )

    private val formats = listOf(
        FormatProfile(
            "VERTICAL_9_16",
            "VERTICAL 9:16 • SHORTS / TIKTOK / REELS",
            "Portrait master • 2160×3840 or 1080×1920 when the device profile allows it",
            false,
            9f / 16f
        ),
        FormatProfile(
            "YOUTUBE_16_9",
            "YOUTUBE 16:9 • LANDSCAPE",
            "Landscape master • 3840×2160 or 1920×1080 when the device profile allows it",
            true,
            16f / 9f
        ),
        FormatProfile(
            "INSTAGRAM_4_5",
            "INSTAGRAM FEED 4:5 • SAFE CROP",
            "Portrait clean master with a centered 4:5 delivery guide",
            false,
            4f / 5f
        ),
        FormatProfile(
            "SQUARE_1_1",
            "SQUARE 1:1 • SAFE CROP",
            "Portrait clean master with a centered 1:1 delivery guide",
            false,
            1f
        ),
        FormatProfile(
            "CINEMA_239",
            "CINEMA 2.39:1 • LANDSCAPE SAFE",
            "Landscape clean master with a 2.39:1 cinema composition guide",
            true,
            2.39f
        ),
        FormatProfile(
            "DUAL_SAFE",
            "DUAL SAFE • 16:9 MASTER + 9:16 SOCIAL",
            "Landscape master with a centered vertical social-safe window",
            true,
            16f / 9f,
            true
        )
    )

    fun activeFormatLabel(context: Context): String =
        selected(context).label

    fun activeFormatId(context: Context): String =
        selected(context).id

    fun snapshotJson(context: Context): JSONObject {
        val prefs = context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("capture_format", activeFormatLabel(context))
            .put("satellites_visible", prefs.getInt("sat_visible", 0))
            .put("satellites_used", prefs.getInt("sat_used", 0))
            .put("satellite_avg_cn0_dbhz", prefs.getFloat("sat_cn0", 0f).toDouble())
            .put("constellations", prefs.getString("constellations", "--"))
            .put("latitude", nullableDouble(prefs, "lat"))
            .put("longitude", nullableDouble(prefs, "lon"))
            .put("altitude_m", nullableDouble(prefs, "alt"))
            .put("accuracy_m", nullableDouble(prefs, "accuracy"))
            .put("horizon_roll_deg", prefs.getFloat("roll", 0f).toDouble())
            .put("pitch_deg", prefs.getFloat("pitch", 0f).toDouble())
            .put("sun_azimuth_deg", nullableDouble(prefs, "sun_az"))
            .put("sun_elevation_deg", nullableDouble(prefs, "sun_el"))
            .put("sun_light", prefs.getString("sun_light", "UNKNOWN"))
    }

    fun attach(
        activity: AppCompatActivity,
        root: FrameLayout,
        previewView: View
    ) {
        if (root.findViewWithTag<View>(TAG) != null) return
        Controller(activity, root, previewView).attach()
    }

    fun setFormat(context: Context, id: String): String {
        val index = formats.indexOfFirst { it.id == id }
        if (index < 0) return activeFormatLabel(context)
        context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_FORMAT, index).apply()
        val format = formats[index]
        (context as? android.app.Activity)?.let { activity ->
            activity.requestedOrientation =
                if (format.landscape) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }
            activity.window.decorView.postInvalidate()
        }
        return format.label
    }

    private fun selected(context: Context): FormatProfile {
        val index =
            context.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(KEY_FORMAT, 0)
                .coerceIn(0, formats.lastIndex)
        return formats[index]
    }

    private fun nullableDouble(
        prefs: android.content.SharedPreferences,
        key: String
    ): Any =
        if (prefs.contains(key)) prefs.getFloat(key, 0f).toDouble() else JSONObject.NULL

    private class Controller(
        private val activity: AppCompatActivity,
        private val root: FrameLayout,
        private val previewView: View
    ) : SensorEventListener {

        private val handler = Handler(Looper.getMainLooper())
        private val prefs = activity.duSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private val locationManager =
            activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        private val sensorManager =
            activity.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        private lateinit var chip: Button
        private lateinit var guide: SafeFrameView
        private var infoText: TextView? = null
        private var sunText: TextView? = null
        private var horizonText: TextView? = null

        private var satVisible = prefs.getInt("sat_visible", 0)
        private var satUsed = prefs.getInt("sat_used", 0)
        private var satCn0 = prefs.getFloat("sat_cn0", 0f)
        private var constellationText = prefs.getString("constellations", "--") ?: "--"
        private var latitude: Double? = null
        private var longitude: Double? = null
        private var altitude: Double? = null
        private var accuracy: Float? = null
        private var roll = prefs.getFloat("roll", 0f)
        private var pitch = prefs.getFloat("pitch", 0f)
        private var sunAzimuth: Double? = null
        private var sunElevation: Double? = null
        private var sunLight = prefs.getString("sun_light", "UNKNOWN") ?: "UNKNOWN"
        private var registeredGnss = false
        private var registeredLocation = false
        private var registeredSensor = false

        private val tick = object : Runnable {
            override fun run() {
                updateSun()
                refreshText()
                handler.postDelayed(this, 1000L)
            }
        }

        private val gnssCallback =
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    satVisible = status.satelliteCount
                    var used = 0
                    var signalTotal = 0f
                    var signalCount = 0
                    val constellations = linkedSetOf<String>()

                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) used += 1
                        val cn0 = status.getCn0DbHz(i)
                        if (cn0 > 0f) {
                            signalTotal += cn0
                            signalCount += 1
                        }
                        constellations.add(constellationName(status.getConstellationType(i)))
                    }

                    satUsed = used
                    satCn0 = if (signalCount > 0) signalTotal / signalCount else 0f
                    constellationText = constellations.filter { it != "OTHER" }.joinToString(" • ").ifBlank { "--" }
                    persistSnapshot()
                    refreshText()
                }
            }

        private val locationListener =
            object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    latitude = location.latitude
                    longitude = location.longitude
                    altitude = if (location.hasAltitude()) location.altitude else null
                    accuracy = if (location.hasAccuracy()) location.accuracy else null
                    updateSun()
                    persistSnapshot()
                    refreshText()
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
                override fun onProviderEnabled(provider: String) { }
                override fun onProviderDisabled(provider: String) { }
            }

        fun attach() {
            guide = SafeFrameView(activity).apply {
                formatProvider = { selected(activity) }
                rollProvider = { roll }
            }

            val previewIndex = root.indexOfChild(previewView).coerceAtLeast(0)
            root.addView(
                guide,
                (previewIndex + 1).coerceAtMost(root.childCount),
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )

            chip = Button(activity).apply {
                tag = TAG
                textSize = 8f
                isAllCaps = false
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(DevelopUgandaFivemods8Theme.content)
                setPadding(dp(7), 0, dp(7), 0)
                background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(230), DevelopUgandaFivemods8Theme.accent, 15)
                setOnClickListener { showSheet() }
            }

            root.addView(
                chip,
                FrameLayout.LayoutParams(dp(112), dp(54)).apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    leftMargin = dp(8)
                }
            )

            chip.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) { }
                    override fun onViewDetachedFromWindow(v: View) {
                        stopFieldSensors()
                        handler.removeCallbacksAndMessages(null)
                    }
                }
            )

            applySelectedOrientation(initial = true)
            startFieldSensors()
            refreshText()
            handler.post(tick)
        }

        private fun showSheet() {
            val dialog = Dialog(activity)
            dialog.setCancelable(true)

            val panel = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(14))
                background = rounded(DevelopUgandaFivemods8Theme.surfaceScrim(247), DevelopUgandaFivemods8Theme.outline, 22)
            }

            panel.addView(
                TextView(activity).apply {
                    text = "V236 • FORMAT + SATELLITE FIELD INTELLIGENCE"
                    textSize = 14f
                    setTextColor(DevelopUgandaFivemods8Theme.content)
                    typeface = Typeface.DEFAULT_BOLD
                }
            )
            panel.addView(
                TextView(activity).apply {
                    text = "Select a delivery frame. The format stays locked while you shoot; V235 LUT processing remains unchanged."
                    textSize = 8f
                    setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                    setPadding(0, dp(3), 0, dp(8))
                }
            )

            infoText = statusLabel()
            sunText = statusLabel()
            horizonText = statusLabel()
            panel.addView(infoText)
            panel.addView(sunText)
            panel.addView(horizonText)

            panel.addView(
                TextView(activity).apply {
                    text = "PLATFORM FORMAT"
                    textSize = 8f
                    setTextColor(DevelopUgandaFivemods8Theme.accent)
                    typeface = Typeface.DEFAULT_BOLD
                    setPadding(0, dp(9), 0, dp(3))
                }
            )

            val list = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
            }
            val selectedIndex = prefs.getInt(KEY_FORMAT, 0).coerceIn(0, formats.lastIndex)
            formats.forEachIndexed { index, format ->
                list.addView(
                    Button(activity).apply {
                        text = (if (index == selectedIndex) "✓  " else "") + format.label + "\n" + format.detail
                        textSize = 8.1f
                        isAllCaps = false
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(DevelopUgandaFivemods8Theme.content)
                        setPadding(dp(12), 0, dp(10), 0)
                        background = rounded(
                            if (index == selectedIndex) DevelopUgandaFivemods8Theme.surface else DevelopUgandaFivemods8Theme.surfaceRaised,
                            if (index == selectedIndex) DevelopUgandaFivemods8Theme.contentDim else DevelopUgandaFivemods8Theme.outline,
                            14
                        )
                        setOnClickListener {
                            prefs.edit().putInt(KEY_FORMAT, index).apply()
                            guide.invalidate()
                            dialog.dismiss()
                            applySelectedOrientation(initial = false)
                            refreshText()
                        }
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(64)
                    ).apply { topMargin = dp(5) }
                )
            }

            val scroll = ScrollView(activity).apply {
                addView(list)
            }
            panel.addView(
                scroll,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )

            panel.addView(
                TextView(activity).apply {
                    text = "SATELLITE COUNTS COME FROM THE PHONE GNSS RECEIVER • POSITION/ACCURACY REQUIRES LOCATION PERMISSION • SUN POSITION IS CALCULATED OFFLINE FROM TIME + LOCATION"
                    textSize = 7.1f
                    setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                    setPadding(0, dp(8), 0, 0)
                }
            )

            dialog.setContentView(panel)
            dialog.setOnShowListener {
                dialog.window?.let { window ->
                    window.setBackgroundDrawable(ColorDrawable(DevelopUgandaFivemods8Theme.transparent))
                    window.setGravity(Gravity.BOTTOM)
                    window.setLayout(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (activity.resources.displayMetrics.heightPixels * 0.76f).toInt()
                    )
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    val attrs = window.attributes
                    attrs.dimAmount = 0.44f
                    window.attributes = attrs
                }
                refreshText()
            }
            dialog.setOnDismissListener {
                infoText = null
                sunText = null
                horizonText = null
            }
            dialog.show()
        }

        private fun applySelectedOrientation(initial: Boolean) {
            val format = selected(activity)
            val requested =
                if (format.landscape) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                }

            if (activity.requestedOrientation != requested) {
                activity.requestedOrientation = requested
                if (!initial) {
                    Toast.makeText(
                        activity,
                        "FORMAT LOCK • ${format.label}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        private fun startFieldSensors() {
            val rotation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            if (rotation != null) {
                registeredSensor =
                    sensorManager.registerListener(
                        this,
                        rotation,
                        SensorManager.SENSOR_DELAY_UI
                    )
            }

            if (
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            try {
                val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                listOfNotNull(gps, network)
                    .maxByOrNull { it.time }
                    ?.let { locationListener.onLocationChanged(it) }
            } catch (_: Exception) { }

            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1200L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
                registeredLocation = true
            } catch (_: Exception) { }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    registeredGnss =
                        locationManager.registerGnssStatusCallback(
                            gnssCallback,
                            handler
                        )
                } catch (_: Exception) {
                    registeredGnss = false
                }
            }
        }

        private fun stopFieldSensors() {
            if (registeredSensor) {
                try { sensorManager.unregisterListener(this) } catch (_: Exception) { }
                registeredSensor = false
            }
            if (registeredLocation) {
                try { locationManager.removeUpdates(locationListener) } catch (_: Exception) { }
                registeredLocation = false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && registeredGnss) {
                try { locationManager.unregisterGnssStatusCallback(gnssCallback) } catch (_: Exception) { }
                registeredGnss = false
            }
        }

        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
            val matrix = FloatArray(9)
            val orientation = FloatArray(3)
            SensorManager.getRotationMatrixFromVector(matrix, event.values)
            SensorManager.getOrientation(matrix, orientation)
            pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
            roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            prefs.edit().putFloat("roll", roll).putFloat("pitch", pitch).apply()
            guide.rollDeg = roll
            guide.invalidate()
            refreshText()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

        private fun updateSun() {
            val lat = latitude ?: return
            val lon = longitude ?: return
            val sun = sunPosition(System.currentTimeMillis(), lat, lon)
            sunAzimuth = sun.first
            sunElevation = sun.second
            sunLight = when {
                sun.second < -6.0 -> "NIGHT"
                sun.second < 0.0 -> "BLUE HOUR / TWILIGHT"
                sun.second < 8.0 -> "GOLDEN HOUR"
                sun.second < 28.0 -> "LOW SUN"
                else -> "DAYLIGHT"
            }
            persistSnapshot()
        }

        private fun persistSnapshot() {
            val editor = prefs.edit()
                .putInt("sat_visible", satVisible)
                .putInt("sat_used", satUsed)
                .putFloat("sat_cn0", satCn0)
                .putString("constellations", constellationText)
                .putFloat("roll", roll)
                .putFloat("pitch", pitch)
                .putString("sun_light", sunLight)

            latitude?.let { editor.putFloat("lat", it.toFloat()) }
            longitude?.let { editor.putFloat("lon", it.toFloat()) }
            altitude?.let { editor.putFloat("alt", it.toFloat()) }
            accuracy?.let { editor.putFloat("accuracy", it) }
            sunAzimuth?.let { editor.putFloat("sun_az", it.toFloat()) }
            sunElevation?.let { editor.putFloat("sun_el", it.toFloat()) }
            editor.apply()
        }

        private fun refreshText() {
            val format = selected(activity)
            val accuracyText = accuracy?.let { "±${String.format(Locale.US, "%.1f", it)}m" } ?: "--"
            val cn0 = if (satCn0 > 0f) String.format(Locale.US, "%.1f", satCn0) else "--"
            val level = if (abs(roll) <= 1.5f) "LEVEL" else String.format(Locale.US, "%+.1f°", roll)

            chip.text =
                "ASPECT • ${if (format.landscape) "16:9" else format.id.substringAfter('_').replace('_', ':')} • ${if (format.dualSafe) "DUAL" else "LOCK"}\nSAT $satUsed/$satVisible • $level"

            infoText?.text =
                "GNSS • $satUsed/$satVisible USED/VISIBLE • CN0 $cn0 dB-Hz • $accuracyText\n$constellationText"

            val az = sunAzimuth?.let { String.format(Locale.US, "%.0f°", it) } ?: "--"
            val el = sunElevation?.let { String.format(Locale.US, "%+.1f°", it) } ?: "--"
            sunText?.text = "SUN • AZ $az • ELEV $el • $sunLight"
            horizonText?.text =
                "HORIZON • ROLL $level • PITCH ${String.format(Locale.US, "%+.1f°", pitch)} • ${format.label}"
        }

        private fun statusLabel(): TextView =
            TextView(activity).apply {
                textSize = 8.2f
                setTextColor(DevelopUgandaFivemods8Theme.content)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(10), dp(7), dp(10), dp(7))
                background = rounded(DevelopUgandaFivemods8Theme.surfaceRaised, DevelopUgandaFivemods8Theme.outline, 12)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            }

        private fun constellationName(type: Int): String =
            when (type) {
                GnssStatus.CONSTELLATION_GPS -> "GPS"
                GnssStatus.CONSTELLATION_GLONASS -> "GLONASS"
                GnssStatus.CONSTELLATION_GALILEO -> "GALILEO"
                GnssStatus.CONSTELLATION_BEIDOU -> "BEIDOU"
                GnssStatus.CONSTELLATION_QZSS -> "QZSS"
                GnssStatus.CONSTELLATION_SBAS -> "SBAS"
                7 -> "NAVIC"
                else -> "OTHER"
            }

        private fun sunPosition(
            epochMs: Long,
            latitudeDeg: Double,
            longitudeDeg: Double
        ): Pair<Double, Double> {
            val jd = epochMs / 86400000.0 + 2440587.5
            val n = jd - 2451545.0
            val meanLongitude = normalizeDeg(280.460 + 0.9856474 * n)
            val meanAnomaly = normalizeDeg(357.528 + 0.9856003 * n)
            val lambda =
                normalizeDeg(
                    meanLongitude +
                        1.915 * sin(Math.toRadians(meanAnomaly)) +
                        0.020 * sin(Math.toRadians(2.0 * meanAnomaly))
                )
            val obliquity = 23.439 - 0.0000004 * n
            val lambdaRad = Math.toRadians(lambda)
            val obRad = Math.toRadians(obliquity)
            val rightAscension =
                Math.toDegrees(
                    atan2(
                        cos(obRad) * sin(lambdaRad),
                        cos(lambdaRad)
                    )
                )
            val declination =
                Math.toDegrees(
                    asin(sin(obRad) * sin(lambdaRad))
                )
            val gmst = normalizeDeg(280.46061837 + 360.98564736629 * (jd - 2451545.0))
            val hourAngle = normalizeSigned(gmst + longitudeDeg - rightAscension)
            val latRad = Math.toRadians(latitudeDeg)
            val decRad = Math.toRadians(declination)
            val haRad = Math.toRadians(hourAngle)
            val elevation =
                Math.toDegrees(
                    asin(
                        sin(latRad) * sin(decRad) +
                            cos(latRad) * cos(decRad) * cos(haRad)
                    )
                )
            val azimuth =
                normalizeDeg(
                    Math.toDegrees(
                        atan2(
                            sin(haRad),
                            cos(haRad) * sin(latRad) -
                                kotlin.math.tan(decRad) * cos(latRad)
                        )
                    ) + 180.0
                )
            return Pair(azimuth, elevation)
        }

        private fun normalizeDeg(value: Double): Double {
            var v = value % 360.0
            if (v < 0.0) v += 360.0
            return v
        }

        private fun normalizeSigned(value: Double): Double {
            var v = normalizeDeg(value)
            if (v > 180.0) v -= 360.0
            return v
        }

        private fun rounded(fill: Int, stroke: Int, radius: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(fill)
                cornerRadius = dp(radius).toFloat()
                setStroke(dp(1), stroke)
            }

        private fun dp(value: Int): Int =
            (value * activity.resources.displayMetrics.density).toInt()
    }

    private class SafeFrameView(context: Context) : View(context) {
        var formatProvider: (() -> FormatProfile)? = null
        var rollProvider: (() -> Float)? = null
        var rollDeg: Float = 0f

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 1.2f
        }
        private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 1.4f
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = resources.displayMetrics.scaledDensity * 10f
            typeface = Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val format = formatProvider?.invoke() ?: return
            val margin = minOf(width, height) * 0.055f
            val available = RectF(margin, margin, width - margin, height - margin)
            val main = fitRatio(available, format.guideRatio)

            paint.color = DevelopUgandaFivemods8Theme.accentScrim(204)
            canvas.drawRect(main, paint)

            val thirdX1 = main.left + main.width() / 3f
            val thirdX2 = main.left + main.width() * 2f / 3f
            val thirdY1 = main.top + main.height() / 3f
            val thirdY2 = main.top + main.height() * 2f / 3f
            paint.color = DevelopUgandaFivemods8Theme.accentScrim(85)
            canvas.drawLine(thirdX1, main.top, thirdX1, main.bottom, paint)
            canvas.drawLine(thirdX2, main.top, thirdX2, main.bottom, paint)
            canvas.drawLine(main.left, thirdY1, main.right, thirdY1, paint)
            canvas.drawLine(main.left, thirdY2, main.right, thirdY2, paint)

            if (format.dualSafe) {
                val vertical = fitRatio(main, 9f / 16f)
                paint.color = DevelopUgandaFivemods8Theme.accentScrim(221)
                canvas.drawRect(vertical, paint)
                textPaint.color = DevelopUgandaFivemods8Theme.accent
                canvas.drawText("9:16 SOCIAL SAFE", vertical.left + 8f, vertical.top + 18f, textPaint)
            }

            val roll = rollProvider?.invoke() ?: rollDeg
            val cx = main.centerX()
            val cy = main.centerY()
            horizonPaint.color = if (abs(roll) <= 1.5f) DevelopUgandaFivemods8Theme.contentDimScrim(221) else DevelopUgandaFivemods8Theme.accentScrim(221)
            canvas.save()
            canvas.rotate(-roll, cx, cy)
            canvas.drawLine(cx - main.width() * 0.18f, cy, cx + main.width() * 0.18f, cy, horizonPaint)
            canvas.restore()

            textPaint.color = DevelopUgandaFivemods8Theme.content
            canvas.drawText(format.label, main.left + 8f, main.bottom - 10f, textPaint)
        }

        private fun fitRatio(container: RectF, ratio: Float): RectF {
            val containerRatio = container.width() / container.height()
            return if (containerRatio > ratio) {
                val w = container.height() * ratio
                val left = container.centerX() - w / 2f
                RectF(left, container.top, left + w, container.bottom)
            } else {
                val h = container.width() / ratio
                val top = container.centerY() - h / 2f
                RectF(container.left, top, container.right, top + h)
            }
        }
    }
}
