package com.sentongoharuna.pulse

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.roundToInt
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

open class DevelopUgandaPhotoSuiteActivity :
    AppCompatActivity(),
    SensorEventListener {

    protected open fun defaultPhotoModeId(): String =
        "PHOTO_PRO"

    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var assistView: DevelopUgandaShotAssistView
    private lateinit var directorView: DevelopUgandaDirectorOverlayView
    private lateinit var titleView: TextView
    private lateinit var capabilityView: TextView
    private lateinit var statusView: TextView
    private lateinit var levelView: TextView
    private lateinit var formatButton: Button
    private lateinit var lensButton: Button
    private lateinit var assistButton: Button
    private lateinit var captureButton: Button

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null

    private var useFront = false
    private var supportedFormats =
        listOf(
            ImageCapture.OUTPUT_FORMAT_JPEG
        )
    private var selectedFormat =
        ImageCapture.OUTPUT_FORMAT_JPEG

    private var assistMode =
        DevelopUgandaShotAssistView.MODE_OFF

    private val uiHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val fused by lazy {
        LocationServices.getFusedLocationProviderClient(
            this
        )
    }

    private var lastLat: Double? = null
    private var lastLon: Double? = null
    private var lastAccuracy: Float? = null

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null
    private var rollDeg: Float? = null

    private val assistRunnable =
        object : Runnable {
            override fun run() {
                if (
                    assistMode !=
                        DevelopUgandaShotAssistView.MODE_OFF &&
                    ::previewView.isInitialized &&
                    ::assistView.isInitialized
                ) {
                    val bitmap =
                        try {
                            previewView.bitmap
                        } catch (_: Exception) {
                            null
                        }

                    if (
                        bitmap !=
                            null
                    ) {
                        assistView.submitFrame(
                            bitmap
                        )
                    }
                }

                uiHandler.postDelayed(
                    this,
                    700L
                )
            }
        }

    private val directorRunnable =
        object : Runnable {
            override fun run() {
                if (
                    ::previewView.isInitialized &&
                    ::directorView.isInitialized &&
                    previewView.width > 0 &&
                    previewView.height > 0
                ) {
                    val bitmap =
                        try {
                            previewView.bitmap
                        } catch (_: Exception) {
                            null
                        }

                    if (
                        bitmap !=
                            null
                    ) {
                        directorView.submitFrame(
                            bitmap,
                            modeId ==
                                "PEOPLE_PHOTO"
                        )
                    }
                }

                uiHandler.postDelayed(
                    this,
                    1200L
                )
            }
        }

    private val modeId by lazy {
        defaultPhotoModeId()
    }

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(
            savedInstanceState
        )

        sensorManager =
            getSystemService(
                Context.SENSOR_SERVICE
            ) as SensorManager

        rotationSensor =
            sensorManager.getDefaultSensor(
                Sensor.TYPE_ROTATION_VECTOR
            )

        buildUi()
        requestPermissionsAndStart()

        uiHandler.postDelayed(
            assistRunnable,
            900L
        )

        uiHandler.postDelayed(
            directorRunnable,
            1200L
        )
    }

    override fun onResume() {
        super.onResume()

        rotationSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SensorManager.SENSOR_DELAY_GAME
            )
        }
    }

    override fun onPause() {
        try {
            sensorManager.unregisterListener(
                this
            )
        } catch (_: Exception) {
        }

        super.onPause()
    }

    override fun onDestroy() {
        uiHandler.removeCallbacksAndMessages(
            null
        )

        super.onDestroy()
    }

    private fun buildUi() {
        root =
            FrameLayout(
                this
            ).apply {
                setBackgroundColor(
                    DevelopUgandaFivemods8Theme.surface
                )
            }

        previewView =
            PreviewView(
                this
            ).apply {
                scaleType =
                    PreviewView.ScaleType.FILL_CENTER
            }

        root.addView(
            previewView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        assistView =
            DevelopUgandaShotAssistView(
                this
            ).apply {
                setAssistMode(
                    assistMode
                )
            }

        root.addView(
            assistView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        directorView =
            DevelopUgandaDirectorOverlayView(
                this
            )

        root.addView(
            directorView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val top =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(14),
                    dp(28),
                    dp(14),
                    dp(10)
                )

                background =
                    GradientDrawable().apply {
                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(201)
                        )

                        cornerRadius =
                            dp(18).toFloat()
                    }
            }

        titleView =
            label(
                "${modeDisplayName()} • V228",
                17f,
                modeAccent(),
                true
            )

        top.addView(
            titleView
        )

        top.addView(
            label(
                "BEST FOR • ${modeBestFor()}",
                8.5f,
                DevelopUgandaFivemods8Theme.content,
                true
            )
        )

        capabilityView =
            label(
                "CAMERA CAPABILITIES • checking…",
                8f,
                DevelopUgandaFivemods8Theme.contentDim,
                false
            ).apply {
                setPadding(
                    0,
                    dp(4),
                    0,
                    0
                )
            }

        top.addView(
            capabilityView
        )

        val topParams =
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin =
                    dp(10)

                rightMargin =
                    dp(10)

                topMargin =
                    dp(8)
            }

        root.addView(
            top,
            topParams
        )

        levelView =
            label(
                "LEVEL • --",
                9f,
                DevelopUgandaFivemods8Theme.contentDim,
                true
            ).apply {
                gravity =
                    Gravity.CENTER

                background =
                    GradientDrawable().apply {
                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(169)
                        )

                        setStroke(
                            dp(1),
                            DevelopUgandaFivemods8Theme.contentDim
                        )

                        cornerRadius =
                            dp(12).toFloat()
                    }
            }

        root.addView(
            levelView,
            FrameLayout.LayoutParams(
                dp(220),
                dp(34)
            ).apply {
                gravity =
                    Gravity.CENTER_HORIZONTAL or
                        Gravity.TOP

                topMargin =
                    dp(150)
            }
        )

        val bottom =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    dp(10),
                    dp(8),
                    dp(10),
                    dp(12)
                )

                background =
                    GradientDrawable().apply {
                        setColor(
                            DevelopUgandaFivemods8Theme.surfaceScrim(218)
                        )

                        cornerRadius =
                            dp(18).toFloat()
                    }
            }

        statusView =
            label(
                "PHOTO CAMERA STARTING",
                9f,
                DevelopUgandaFivemods8Theme.contentDim,
                true
            ).apply {
                gravity =
                    Gravity.CENTER

                setPadding(
                    0,
                    0,
                    0,
                    dp(6)
                )
            }

        bottom.addView(
            statusView
        )

        val row =
            LinearLayout(
                this
            ).apply {
                orientation =
                    LinearLayout.HORIZONTAL
            }

        formatButton =
            actionButton(
                "FORMAT\nJPEG",
                DevelopUgandaFivemods8Theme.accent
            ) {
                cycleFormat()
            }

        lensButton =
            actionButton(
                "LENS\nBACK",
                DevelopUgandaFivemods8Theme.accent
            ) {
                useFront =
                    !useFront

                bindCamera()
            }

        assistButton =
            actionButton(
                "ASSIST\nOFF",
                DevelopUgandaFivemods8Theme.accent
            ) {
                cycleAssist()
            }

        listOf(
            formatButton,
            lensButton,
            assistButton
        ).forEach {
            row.addView(
                it,
                LinearLayout.LayoutParams(
                    0,
                    dp(52),
                    1f
                ).apply {
                    marginStart =
                        dp(3)

                    marginEnd =
                        dp(3)
                }
            )
        }

        bottom.addView(
            row
        )

        captureButton =
            Button(
                this
            ).apply {
                text =
                    "● CAPTURE"

                textSize =
                    14f

                isAllCaps =
                    false

                setTextColor(
                    DevelopUgandaFivemods8Theme.content
                )

                typeface =
                    Typeface.DEFAULT_BOLD

                background =
                    GradientDrawable().apply {
                        shape =
                            GradientDrawable.RECTANGLE

                        cornerRadius =
                            dp(24).toFloat()

                        setColor(
                            modeAccent()
                        )

                        setStroke(
                            dp(3),
                            DevelopUgandaFivemods8Theme.content
                        )
                    }

                setOnClickListener {
                    capturePhoto()
                }
            }

        bottom.addView(
            captureButton,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(66)
            ).apply {
                topMargin =
                    dp(8)
            }
        )

        root.addView(
            bottom,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity =
                    Gravity.BOTTOM

                leftMargin =
                    dp(10)

                rightMargin =
                    dp(10)

                bottomMargin =
                    dp(10)
            }
        )

        previewView.setOnTouchListener {
                _,
                event ->
            if (
                event.action ==
                    MotionEvent.ACTION_UP
            ) {
                tapFocus(
                    event.x,
                    event.y
                )
            }

            true
        }

        setContentView(
            root
        )
    }

    private fun requestPermissionsAndStart() {
        val missing =
            listOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).filter {
                ContextCompat.checkSelfPermission(
                    this,
                    it
                ) !=
                    PackageManager.PERMISSION_GRANTED
            }

        if (
            missing.isEmpty()
        ) {
            startCamera()
            updateLocation()
        } else {
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                2250
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (
            requestCode ==
                2250
        ) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) ==
                    PackageManager.PERMISSION_GRANTED
            ) {
                startCamera()
            }

            updateLocation()
        }
    }

    private fun updateLocation() {
        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        fused.lastLocation
            .addOnSuccessListener {
                    location ->
                if (
                    location !=
                        null
                ) {
                    lastLat =
                        location.latitude

                    lastLon =
                        location.longitude

                    lastAccuracy =
                        location.accuracy
                }
            }
    }

    private fun startCamera() {
        val future =
            ProcessCameraProvider.getInstance(
                this
            )

        future.addListener(
            {
                try {
                    provider =
                        future.get()

                    bindCamera()
                } catch (
                    e: Exception
                ) {
                    statusView.text =
                        "PHOTO CAMERA ERROR"

                    toast(
                        "Photo camera could not start"
                    )
                }
            },
            ContextCompat.getMainExecutor(
                this
            )
        )
    }

    private fun bindCamera() {
        val p =
            provider ?: return

        p.unbindAll()

        val selector =
            if (
                useFront
            ) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

        try {
            val info =
                p.getCameraInfo(
                    selector
                )

            supportedFormats =
                ImageCapture
                    .getImageCaptureCapabilities(
                        info
                    )
                    .supportedOutputFormats
                    .toList()
                    .filter {
                        it in
                            listOf(
                                ImageCapture.OUTPUT_FORMAT_JPEG,
                                ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR,
                                ImageCapture.OUTPUT_FORMAT_RAW,
                                ImageCapture.OUTPUT_FORMAT_RAW_JPEG
                            )
                    }
                    .ifEmpty {
                        listOf(
                            ImageCapture.OUTPUT_FORMAT_JPEG
                        )
                    }

            selectedFormat =
                preferredFormatForMode(
                    supportedFormats
                )

            val preview =
                Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(
                            previewView.surfaceProvider
                        )
                    }

            val builder =
                ImageCapture.Builder()
                    .setCaptureMode(
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    )
                    .setJpegQuality(
                        100
                    )
                    .setOutputFormat(
                        selectedFormat
                    )

            imageCapture =
                builder.build()

            camera =
                p.bindToLifecycle(
                    this,
                    selector,
                    preview,
                    imageCapture
                )

            if (
                modeId ==
                    "NIGHT_PHOTO"
            ) {
                imageCapture?.flashMode =
                    ImageCapture.FLASH_MODE_OFF
            } else {
                imageCapture?.flashMode =
                    ImageCapture.FLASH_MODE_AUTO
            }

            formatButton.text =
                "FORMAT\n${formatLabel(selectedFormat)}"

            lensButton.text =
                "LENS\n${if (useFront) "FRONT" else "BACK"}"

            capabilityView.text =
                "SUPPORTED • " +
                    supportedFormats.joinToString(
                        " • "
                    ) {
                        formatLabel(
                            it
                        )
                    } +
                    "\nACTIVE • " +
                    formatLabel(
                        selectedFormat
                    ) +
                    " • MAX QUALITY"

            statusView.text =
                modeReadyText()
        } catch (
            e: Exception
        ) {
            statusView.text =
                "FORMAT / LENS UNSUPPORTED"

            toast(
                "This lens cannot use the selected photo format"
            )
        }
    }

    private fun preferredFormatForMode(
        supported: List<Int>
    ): Int {
        val preferred =
            when (
                modeId
            ) {
                "BUILDING_PHOTO" ->
                    ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR

                else ->
                    ImageCapture.OUTPUT_FORMAT_JPEG
            }

        return if (
            supported.contains(
                preferred
            )
        ) {
            preferred
        } else {
            ImageCapture.OUTPUT_FORMAT_JPEG
        }
    }

    private fun cycleFormat() {
        if (
            supportedFormats.isEmpty()
        ) {
            return
        }

        val current =
            supportedFormats.indexOf(
                selectedFormat
            )

        selectedFormat =
            supportedFormats[
                (
                    current +
                        1
                    ) %
                    supportedFormats.size
            ]

        bindCamera()
    }

    private fun cycleAssist() {
        assistMode =
            (
                assistMode +
                    1
                ) %
                4

        assistView.setAssistMode(
            assistMode
        )

        assistButton.text =
            "ASSIST\n" +
                when (
                    assistMode
                ) {
                    DevelopUgandaShotAssistView.MODE_PEAK ->
                        "PEAK"

                    DevelopUgandaShotAssistView.MODE_ZEBRA ->
                        "ZEBRA"

                    DevelopUgandaShotAssistView.MODE_BOTH ->
                        "BOTH"

                    else ->
                        "OFF"
                }
    }

    private fun tapFocus(
        x: Float,
        y: Float
    ) {
        val cam =
            camera ?: return

        try {
            val point =
                previewView
                    .meteringPointFactory
                    .createPoint(
                        x,
                        y
                    )

            val action =
                FocusMeteringAction.Builder(
                    point
                )
                    .setAutoCancelDuration(
                        3,
                        TimeUnit.SECONDS
                    )
                    .build()

            val future =
                cam.cameraControl
                    .startFocusAndMetering(
                        action
                    )

            future.addListener(
                {
                    val success =
                        try {
                            future.get()
                                .isFocusSuccessful
                        } catch (_: Exception) {
                            false
                        }

                    runOnUiThread {
                        statusView.text =
                            if (
                                success
                            ) {
                                "FOCUS CONFIRMED • READY"
                            } else {
                                "FOCUS NOT CONFIRMED • TRY AGAIN"
                            }
                    }
                },
                ContextCompat.getMainExecutor(
                    this
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun capturePhoto() {
        val capture =
            imageCapture ?: run {
                toast(
                    "Photo camera is still starting"
                )
                return
            }

        val stamp =
            SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.US
            ).format(
                Date()
            )

        val baseName =
            "DEVELOP_UGANDA_V225_${modeId}_$stamp"

        statusView.text =
            "CAPTURING • ${formatLabel(selectedFormat)}"

        if (
            selectedFormat ==
                ImageCapture.OUTPUT_FORMAT_RAW_JPEG
        ) {
            captureRawJpeg(
                capture,
                baseName
            )
            return
        }

        val output =
            outputOptionsForFormat(
                baseName,
                selectedFormat
            )

        capture.takePicture(
            output,
            ContextCompat.getMainExecutor(
                this
            ),
            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    val uri =
                        result.savedUri

                    statusView.text =
                        "PHOTO SAVED • ${formatLabel(selectedFormat)}"

                    savePhotoMetadata(
                        baseName =
                            baseName,
                        format =
                            formatLabel(
                                selectedFormat
                            ),
                        uris =
                            listOfNotNull(
                                uri
                            )
                    )

                    if (
                        modeId ==
                            "VERIFIED_PHOTO" &&
                        uri !=
                            null
                    ) {
                        saveVerifiedPhotoIntegrity(
                            uri,
                            baseName
                        )
                    }

                    toast(
                        "Photo saved • ${modeDisplayName()}"
                    )
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    statusView.text =
                        "PHOTO ERROR"

                    toast(
                        "Photo capture failed"
                    )
                }
            }
        )
    }

    private fun captureRawJpeg(
        capture: ImageCapture,
        baseName: String
    ) {
        val raw =
            outputOptionsForFormat(
                baseName +
                    "_RAW",
                ImageCapture.OUTPUT_FORMAT_RAW
            )

        val jpeg =
            outputOptionsForFormat(
                baseName +
                    "_JPEG",
                ImageCapture.OUTPUT_FORMAT_JPEG
            )

        val count =
            AtomicInteger(
                0
            )

        val uris =
            mutableListOf<Uri>()

        capture.takePicture(
            raw,
            jpeg,
            ContextCompat.getMainExecutor(
                this
            ),
            object :
                ImageCapture.OnImageSavedCallback {

                override fun onImageSaved(
                    result: ImageCapture.OutputFileResults
                ) {
                    synchronized(
                        uris
                    ) {
                        result.savedUri?.let {
                            uris.add(
                                it
                            )
                        }
                    }

                    val saved =
                        count.incrementAndGet()

                    statusView.text =
                        "RAW+JPEG • $saved/2 SAVED"

                    if (
                        saved >=
                            2
                    ) {
                        savePhotoMetadata(
                            baseName =
                                baseName,
                            format =
                                "RAW+JPEG",
                            uris =
                                synchronized(
                                    uris
                                ) {
                                    uris.toList()
                                }
                        )

                        statusView.text =
                            "RAW+JPEG SAVED"

                        toast(
                            "RAW + JPEG pair saved"
                        )
                    }
                }

                override fun onError(
                    exception: ImageCaptureException
                ) {
                    statusView.text =
                        "RAW+JPEG ERROR"

                    toast(
                        "RAW + JPEG capture failed"
                    )
                }
            }
        )
    }

    private fun outputOptionsForFormat(
        baseName: String,
        format: Int
    ): ImageCapture.OutputFileOptions {
        val raw =
            format ==
                ImageCapture.OUTPUT_FORMAT_RAW

        val extension =
            if (
                raw
            ) {
                ".dng"
            } else {
                ".jpg"
            }

        val values =
            ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    baseName +
                        extension
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    if (
                        raw
                    ) {
                        "image/x-adobe-dng"
                    } else {
                        "image/jpeg"
                    }
                )

                if (
                    android.os.Build.VERSION.SDK_INT >=
                        29
                ) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        "Pictures/develop.uganda/Photo Pro/${modeFolder()}"
                    )
                }
            }

        val collection =
            if (
                raw
            ) {
                MediaStore.Files.getContentUri(
                    "external"
                )
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

        return ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            collection,
            values
        )
            .build()
    }

    private fun savePhotoMetadata(
        baseName: String,
        format: String,
        uris: List<Uri>
    ) {
        Thread {
            try {
                val body =
                    buildString {
                        append(
                            "{\n"
                        )
                        append(
                            "  \"app_version\": \"V226\",\n"
                        )
                        append(
                            "  \"camera\": \"${modeDisplayName()}\",\n"
                        )
                        append(
                            "  \"mode_id\": \"$modeId\",\n"
                        )
                        append(
                            "  \"format\": \"$format\",\n"
                        )
                        append(
                            "  \"captured_utc\": \"${Instant.now()}\",\n"
                        )
                        append(
                            "  \"latitude\": ${lastLat?.toString() ?: "null"},\n"
                        )
                        append(
                            "  \"longitude\": ${lastLon?.toString() ?: "null"},\n"
                        )
                        append(
                            "  \"gps_accuracy_m\": ${lastAccuracy?.toString() ?: "null"},\n"
                        )
                        append(
                            "  \"files\": ["
                        )
                        append(
                            uris.joinToString(
                                ","
                            ) {
                                "\"${it}\""
                            }
                        )
                        append(
                            "]\n"
                        )
                        append(
                            "}\n"
                        )
                    }

                saveTextToDownloads(
                    "$baseName.json",
                    "Download/develop.uganda/Photo Metadata/${modeFolder()}",
                    body
                )
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun saveVerifiedPhotoIntegrity(
        uri: Uri,
        baseName: String
    ) {
        Thread {
            try {
                val digest =
                    MessageDigest.getInstance(
                        "SHA-256"
                    )

                contentResolver.openInputStream(
                    uri
                )?.use {
                    input ->
                    val buffer =
                        ByteArray(
                            1024 *
                                1024
                        )

                    while (
                        true
                    ) {
                        val read =
                            input.read(
                                buffer
                            )

                        if (
                            read <=
                                0
                        ) {
                            break
                        }

                        digest.update(
                            buffer,
                            0,
                            read
                        )
                    }
                } ?: return@Thread

                val hash =
                    digest.digest()
                        .joinToString(
                            ""
                        ) {
                            "%02x".format(
                                Locale.US,
                                it
                            )
                        }

                val body =
                    "{\n" +
                        "  \"camera\": \"${modeDisplayName()}\",\n" +
                        "  \"captured_utc\": \"${Instant.now()}\",\n" +
                        "  \"sha256\": \"$hash\",\n" +
                        "  \"note\": \"SHA-256 detects later file changes. It is not a digital signature or proof of authorship.\"\n" +
                        "}\n"

                saveTextToDownloads(
                    "${baseName}_INTEGRITY.json",
                    "Download/develop.uganda/Photo Integrity",
                    body
                )
            } catch (_: Exception) {
            }
        }.start()
    }

    private fun saveTextToDownloads(
        name: String,
        path: String,
        value: String
    ) {
        if (
            android.os.Build.VERSION.SDK_INT >=
                29
        ) {
            val values =
                ContentValues().apply {
                    put(
                        MediaStore.Downloads.DISPLAY_NAME,
                        name
                    )

                    put(
                        MediaStore.Downloads.MIME_TYPE,
                        "application/json"
                    )

                    put(
                        MediaStore.Downloads.RELATIVE_PATH,
                        path
                    )
                }

            val uri =
                contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values
                ) ?: return

            contentResolver.openOutputStream(
                uri
            )?.use {
                it.write(
                    value.toByteArray(
                        Charsets.UTF_8
                    )
                )
            }

            return
        }

        val dir =
            File(
                getExternalFilesDir(
                    null
                ),
                "develop.uganda/Photo Metadata/${modeFolder()}"
            )

        dir.mkdirs()

        FileOutputStream(
            File(
                dir,
                name
            )
        ).use {
            it.write(
                value.toByteArray(
                    Charsets.UTF_8
                )
            )
        }
    }

    private fun modeDisplayName(): String =
        when (
            modeId
        ) {
            "BUILDING_PHOTO" ->
                "V225 • BUILDING PHOTO"

            "PEOPLE_PHOTO" ->
                "V225 • PEOPLE PHOTO"

            "NIGHT_PHOTO" ->
                "V225 • NIGHT PHOTO"

            "VERIFIED_PHOTO" ->
                "V225 • VERIFIED PHOTO"

            else ->
                "V225 • PHOTO PRO"
        }

    private fun modeBestFor(): String =
        when (
            modeId
        ) {
            "BUILDING_PHOTO" ->
                "ARCHITECTURE / ROOMS / PROPERTY / STRAIGHT LINES"

            "PEOPLE_PHOTO" ->
                "PEOPLE / PORTRAITS / INTERVIEWS"

            "NIGHT_PHOTO" ->
                "NIGHT / DARK INTERIORS / MAX QUALITY"

            "VERIFIED_PHOTO" ->
                "SITE RECORDS / INSPECTIONS / EVIDENCE"

            else ->
                "GENERAL PRO PHOTOGRAPHY / RAW / HDR"
        }

    private fun modeFolder(): String =
        when (
            modeId
        ) {
            "BUILDING_PHOTO" ->
                "Building"

            "PEOPLE_PHOTO" ->
                "People"

            "NIGHT_PHOTO" ->
                "Night"

            "VERIFIED_PHOTO" ->
                "Verified"

            else ->
                "Photo Pro"
        }

    private fun modeAccent(): Int =
        when (
            modeId
        ) {
            "BUILDING_PHOTO" ->
                DevelopUgandaFivemods8Theme.contentDim

            "PEOPLE_PHOTO" ->
                DevelopUgandaFivemods8Theme.accent

            "NIGHT_PHOTO" ->
                DevelopUgandaFivemods8Theme.accent

            "VERIFIED_PHOTO" ->
                DevelopUgandaFivemods8Theme.accent

            else ->
                DevelopUgandaFivemods8Theme.accent
        }

    private fun modeReadyText(): String =
        when (
            modeId
        ) {
            "BUILDING_PHOTO" ->
                "BUILDING • LEVEL GUIDE • HDR WHEN SUPPORTED"

            "PEOPLE_PHOTO" ->
                "PEOPLE • TAP FOCUS • MAX QUALITY"

            "NIGHT_PHOTO" ->
                "NIGHT • MAX QUALITY • FLASH OFF"

            "VERIFIED_PHOTO" ->
                "VERIFIED • JPEG + SHA-256 SIDECAR"

            else ->
                "PHOTO PRO • CHOOSE SUPPORTED FORMAT"
        }

    private fun formatLabel(
        format: Int
    ): String =
        when (
            format
        ) {
            ImageCapture.OUTPUT_FORMAT_JPEG_ULTRA_HDR ->
                "ULTRA HDR"

            ImageCapture.OUTPUT_FORMAT_RAW ->
                "RAW DNG"

            ImageCapture.OUTPUT_FORMAT_RAW_JPEG ->
                "RAW+JPEG"

            else ->
                "JPEG"
        }

    private fun actionButton(
        textValue: String,
        accent: Int,
        action: () -> Unit
    ): Button =
        Button(
            this
        ).apply {
            text =
                textValue

            textSize =
                8.5f

            isAllCaps =
                false

            setTextColor(
                DevelopUgandaFivemods8Theme.content
            )

            background =
                GradientDrawable().apply {
                    cornerRadius =
                        dp(16).toFloat()

                    setColor(
                        DevelopUgandaFivemods8Theme.raisedScrim(218)
                    )

                    setStroke(
                        dp(1),
                        accent
                    )
                }

            setOnClickListener { view ->
                DevelopUgandaV28012SafeActions.run(view.context, "PHOTO SUITE BUTTON") { action.invoke() }
            }
        }

    private fun label(
        value: String,
        sp: Float,
        color: Int,
        bold: Boolean
    ): TextView =
        TextView(
            this
        ).apply {
            text =
                value

            textSize =
                sp

            setTextColor(
                color
            )

            typeface =
                Typeface.create(
                    Typeface.DEFAULT,
                    if (
                        bold
                    ) {
                        Typeface.BOLD
                    } else {
                        Typeface.NORMAL
                    }
                )
        }

    private fun dp(
        value: Int
    ): Int =
        (
            value *
                resources.displayMetrics.density
            ).roundToInt()

    private fun toast(
        value: String
    ) {
        Toast.makeText(
            this,
            value,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int
    ) {
    }

    override fun onSensorChanged(
        event: SensorEvent?
    ) {
        if (
            event ==
                null ||
            event.sensor.type !=
                Sensor.TYPE_ROTATION_VECTOR
        ) {
            return
        }

        try {
            val matrix =
                FloatArray(
                    9
                )

            val orientation =
                FloatArray(
                    3
                )

            SensorManager.getRotationMatrixFromVector(
                matrix,
                event.values
            )

            SensorManager.getOrientation(
                matrix,
                orientation
            )

            rollDeg =
                Math.toDegrees(
                    orientation[2].toDouble()
                ).toFloat()

            val roll =
                rollDeg ?: 0f

            val absRoll =
                abs(
                    roll
                )

            levelView.text =
                String.format(
                    Locale.US,
                    "LEVEL • %+.1f° • %s",
                    roll,
                    when {
                        absRoll <=
                            1f ->
                                "LOCK"

                        absRoll <=
                            3f ->
                                "NEAR"

                        else ->
                            "ADJUST"
                    }
                )

            levelView.setTextColor(
                when {
                    absRoll <=
                        1f ->
                            DevelopUgandaFivemods8Theme.contentDim

                    absRoll <=
                        3f ->
                            DevelopUgandaFivemods8Theme.accent

                    else ->
                        DevelopUgandaFivemods8Theme.accent
                }
            )
        } catch (_: Exception) {
        }
    }
}

class DevelopUgandaPhotoProCameraActivity :
    DevelopUgandaPhotoSuiteActivity() {
    override fun defaultPhotoModeId(): String =
        "PHOTO_PRO"
}

class DevelopUgandaBuildingPhotoCameraActivity :
    DevelopUgandaPhotoSuiteActivity() {
    override fun defaultPhotoModeId(): String =
        "BUILDING_PHOTO"
}

class DevelopUgandaPeoplePhotoCameraActivity :
    DevelopUgandaPhotoSuiteActivity() {
    override fun defaultPhotoModeId(): String =
        "PEOPLE_PHOTO"
}

class DevelopUgandaNightPhotoCameraActivity :
    DevelopUgandaPhotoSuiteActivity() {
    override fun defaultPhotoModeId(): String =
        "NIGHT_PHOTO"
}

class DevelopUgandaVerifiedPhotoCameraActivity :
    DevelopUgandaPhotoSuiteActivity() {
    override fun defaultPhotoModeId(): String =
        "VERIFIED_PHOTO"
}
