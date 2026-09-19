package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.roundToInt

/** A strictly physical, on-device face observation for Interview Cam. */
data class DevelopUgandaFaceObservation(
    val observedAtMs: Long,
    val subjectId: String,
    val centerX: Float,
    val centerY: Float,
    val areaRatio: Float,
    val yawDegrees: Float,
    val rollDegrees: Float,
    val leftEyeOpen: Float?,
    val rightEyeOpen: Float?,
    val smileProbability: Float?,
    val faceCount: Int
)

/**
 * V227 screen-only Director + Preview Histogram.
 *
 * This view never participates in CameraX OverlayEffect, so its face guides
 * and histogram are operator aids only and are never burned into saved media.
 */
class DevelopUgandaDirectorOverlayView(
    context: Context,
    private val interviewObservationsEnabled: Boolean = false
) : View(context) {

    private val worker =
        Executors.newSingleThreadExecutor()

    private val busy =
        AtomicBoolean(false)

    private val faceDetector: FaceDetector =
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(
                    FaceDetectorOptions.PERFORMANCE_MODE_FAST
                )
                .setMinFaceSize(
                    0.12f
                )
                .apply {
                    // Main and Live retain their original FAST detector.
                    // Interview Cam asks this same overlay layer for the
                    // additional classifier/tracking values it can observe.
                    if (interviewObservationsEnabled) {
                        setClassificationMode(
                            FaceDetectorOptions.CLASSIFICATION_MODE_ALL
                        )
                        enableTracking()
                    }
                }
                .build()
        )

    @Volatile
    private var enabled =
        true

    @Volatile
    private var peopleMode =
        false

    @Volatile
    private var inputWidth =
        1

    @Volatile
    private var inputHeight =
        1

    @Volatile
    private var faces =
        emptyList<Face>()

    @Volatile
    private var primaryFaceListener: ((Float, Float, Float) -> Unit)? =
        null

    @Volatile
    private var interviewObservationListener:
        ((DevelopUgandaFaceObservation) -> Unit)? = null


    @Volatile
    private var trackingMode = "OFF"

    @Volatile
    private var trackingAnchorX: Float? = null

    @Volatile
    private var trackingAnchorY: Float? = null

    @Volatile
    private var histogram =
        IntArray(
            32
        )

    @Volatile
    private var histogramMax =
        1

    @Volatile
    private var histogramMessage =
        "PREVIEW HISTOGRAM • ANALYSING"

    @Volatile
    private var compositionMessage =
        "DIRECTOR • COMPOSITION READY"

    private val guidePaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                DevelopUgandaFivemods8Theme.contentDim

            style =
                Paint.Style.STROKE

            strokeWidth =
                3f
        }

    private val warningPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                DevelopUgandaFivemods8Theme.accent

            style =
                Paint.Style.STROKE

            strokeWidth =
                3f
        }

    private val gridPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                DevelopUgandaFivemods8Theme.contentScrim(40)

            strokeWidth =
                1f
        }

    private val panelPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                DevelopUgandaFivemods8Theme.surfaceScrim(204)

            style =
                Paint.Style.FILL
        }

    private val histogramPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                DevelopUgandaFivemods8Theme.accent

            style =
                Paint.Style.FILL
        }

    private val labelPaint =
        Paint(
            Paint.ANTI_ALIAS_FLAG
        ).apply {
            color =
                DevelopUgandaFivemods8Theme.content

            textSize =
                22f

        }

    fun setPrimaryFaceListener(
        listener: ((Float, Float, Float) -> Unit)?
    ) {
        primaryFaceListener =
            listener
    }

    fun setInterviewObservationListener(
        listener: ((DevelopUgandaFaceObservation) -> Unit)?
    ) {
        interviewObservationListener = listener
    }


    fun setTrackingMode(value: String) {
        trackingMode = value.trim().uppercase()
        if (trackingMode == "OFF") {
            trackingAnchorX = null
            trackingAnchorY = null
        }
        postInvalidateOnAnimation()
    }

    fun setTrackingAnchor(normalizedX: Float, normalizedY: Float) {
        trackingAnchorX = normalizedX.coerceIn(0f, 1f)
        trackingAnchorY = normalizedY.coerceIn(0f, 1f)
        postInvalidateOnAnimation()
    }

    fun setDirectorEnabled(
        value: Boolean
    ) {
        enabled =
            value

        if (
            !value
        ) {
            faces =
                emptyList()

            histogram =
                IntArray(
                    32
                )

            compositionMessage =
                "DIRECTOR • OFF"
        }

        postInvalidateOnAnimation()
    }

    fun submitFrame(
        source: Bitmap,
        enablePeopleGuidance: Boolean
    ) {
        if (
            !enabled ||
            source.width <=
                0 ||
            source.height <=
                0 ||
            !busy.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        peopleMode =
            enablePeopleGuidance

        worker.execute {
            var scaled: Bitmap? =
                null

            try {
                val targetWidth =
                    minOf(
                        420,
                        source.width
                    )
                        .coerceAtLeast(
                            96
                        )

                val targetHeight =
                    max(
                        96,
                        (
                            source.height.toFloat() *
                                targetWidth.toFloat() /
                                source.width.toFloat()
                            ).roundToInt()
                    )

                scaled =
                    Bitmap.createScaledBitmap(
                        source,
                        targetWidth,
                        targetHeight,
                        false
                    )

                inputWidth =
                    scaled.width

                inputHeight =
                    scaled.height

                calculateHistogram(
                    scaled
                )

                if (
                    !enablePeopleGuidance
                ) {
                    faces =
                        emptyList()

                    compositionMessage =
                        "DIRECTOR • GRID / HISTOGRAM"

                    val finished =
                        scaled

                    post {
                        postInvalidateOnAnimation()
                    }

                    try {
                        finished.recycle()
                    } catch (_: Exception) {
                    }

                    busy.set(
                        false
                    )

                    return@execute
                }

                val image =
                    InputImage.fromBitmap(
                        scaled,
                        0
                    )

                val imageForClose =
                    scaled

                faceDetector
                    .process(
                        image
                    )
                    .addOnSuccessListener {
                            detected ->
                        val sorted =
                            detected
                                .sortedByDescending {
                                    it.boundingBox.width() *
                                        it.boundingBox.height()
                                }

                        val selected =
                            when (trackingMode) {
                                "TAP FACE" -> {
                                    val ax = trackingAnchorX
                                    val ay = trackingAnchorY
                                    if (ax != null && ay != null && inputWidth > 0 && inputHeight > 0) {
                                        sorted.minByOrNull { face ->
                                            val box = face.boundingBox
                                            val cx = (box.left + box.right) / 2f / inputWidth.toFloat()
                                            val cy = (box.top + box.bottom) / 2f / inputHeight.toFloat()
                                            val dx = cx - ax
                                            val dy = cy - ay
                                            dx * dx + dy * dy
                                        }
                                    } else {
                                        sorted.firstOrNull()
                                    }
                                }

                                "PRIMARY FACE" ->
                                    sorted.firstOrNull()

                                else ->
                                    sorted.firstOrNull()
                            }

                        faces =
                            if (trackingMode != "OFF" && selected != null) {
                                listOf(selected) + sorted.filterNot { it === selected }
                            } else {
                                sorted
                            }

                        val primary = faces.firstOrNull()

                        compositionMessage =
                            if (trackingMode != "OFF") {
                                if (primary != null) {
                                    "TRACK • FACE LOCK • ${trackingMode}"
                                } else {
                                    "TRACK • SEARCHING FOR FACE"
                                }
                            } else {
                                compositionFor(primary)
                            }

                        if (
                            primary != null &&
                            inputWidth > 0 &&
                            inputHeight > 0
                        ) {
                            val box = primary.boundingBox
                            val centerX = (box.left + box.right) / 2f / inputWidth.toFloat()
                            val centerY = (box.top + box.bottom) / 2f / inputHeight.toFloat()
                            val areaRatio =
                                (box.width().toFloat() * box.height().toFloat()) /
                                    (inputWidth.toFloat() * inputHeight.toFloat())

                            if (trackingMode == "TAP FACE") {
                                trackingAnchorX = centerX.coerceIn(0f, 1f)
                                trackingAnchorY = centerY.coerceIn(0f, 1f)
                            }

                            primaryFaceListener?.invoke(
                                centerX.coerceIn(0f, 1f),
                                centerY.coerceIn(0f, 1f),
                                areaRatio.coerceIn(0f, 1f)
                            )

                            if (interviewObservationsEnabled) {
                                interviewObservationListener?.invoke(
                                    DevelopUgandaFaceObservation(
                                        observedAtMs = android.os.SystemClock.elapsedRealtime(),
                                        subjectId = primary.trackingId?.toString() ?: "face-0",
                                        centerX = centerX.coerceIn(0f, 1f),
                                        centerY = centerY.coerceIn(0f, 1f),
                                        areaRatio = areaRatio.coerceIn(0f, 1f),
                                        yawDegrees = primary.headEulerAngleY,
                                        rollDegrees = primary.headEulerAngleZ,
                                        leftEyeOpen = primary.leftEyeOpenProbability,
                                        rightEyeOpen = primary.rightEyeOpenProbability,
                                        smileProbability = primary.smilingProbability,
                                        faceCount = sorted.size
                                    )
                                )
                            }
                        }

                        postInvalidateOnAnimation()
                    }
                    .addOnFailureListener {
                        faces =
                            emptyList()

                        compositionMessage =
                            "DIRECTOR • FACE CHECK UNAVAILABLE"

                        postInvalidateOnAnimation()
                    }
                    .addOnCompleteListener {
                        try {
                            imageForClose.recycle()
                        } catch (_: Exception) {
                        }

                        busy.set(
                            false
                        )
                    }
            } catch (_: Exception) {
                try {
                    scaled?.recycle()
                } catch (_: Exception) {
                }

                busy.set(
                    false
                )
            }
        }
    }

    private fun calculateHistogram(
        bitmap: Bitmap
    ) {
        val bins =
            IntArray(
                32
            )

        var count =
            0

        var sum =
            0L

        var highlights =
            0

        var shadows =
            0

        val step =
            4

        var y =
            0

        while (
            y <
                bitmap.height
        ) {
            var x =
                0

            while (
                x <
                    bitmap.width
            ) {
                val pixel =
                    bitmap.getPixel(
                        x,
                        y
                    )

                val r =
                    Color.red(
                        pixel
                    )

                val g =
                    Color.green(
                        pixel
                    )

                val b =
                    Color.blue(
                        pixel
                    )

                val luma =
                    (
                        r *
                            54 +
                            g *
                                183 +
                            b *
                                19
                        ) /
                        256

                val bin =
                    (
                        luma *
                            bins.size /
                            256
                        )
                        .coerceIn(
                            0,
                            bins.lastIndex
                        )

                bins[
                    bin
                ] +=
                    1

                sum +=
                    luma

                count +=
                    1

                if (
                    luma >=
                        235
                ) {
                    highlights +=
                        1
                }

                if (
                    luma <=
                        25
                ) {
                    shadows +=
                        1
                }

                x +=
                    step
            }

            y +=
                step
        }

        histogram =
            bins

        histogramMax =
            (
                bins.maxOrNull()
                    ?: 1
                )
                .coerceAtLeast(
                    1
                )

        val mean =
            if (
                count >
                    0
            ) {
                sum.toFloat() /
                    count.toFloat()
            } else {
                0f
            }

        val highPct =
            if (
                count >
                    0
            ) {
                highlights.toFloat() /
                    count.toFloat()
            } else {
                0f
            }

        val shadowPct =
            if (
                count >
                    0
            ) {
                shadows.toFloat() /
                    count.toFloat()
            } else {
                0f
            }

        histogramMessage =
            when {
                highPct >
                    0.12f ->
                        "PREVIEW HISTOGRAM • HIGHLIGHTS HIGH"

                shadowPct >
                    0.36f ->
                        "PREVIEW HISTOGRAM • SHADOWS HEAVY"

                mean in
                    85f..180f ->
                        "PREVIEW HISTOGRAM • EXPOSURE BALANCED"

                mean <
                    80f ->
                        "PREVIEW HISTOGRAM • PREVIEW DARK"

                else ->
                    "PREVIEW HISTOGRAM • HIGHLIGHTS SAFE"
            }
    }

    private fun compositionFor(
        face: Face?
    ): String {
        if (
            face ==
                null
        ) {
            return "DIRECTOR • NO FACE CONFIRMED"
        }

        val rect =
            face.boundingBox

        val w =
            inputWidth
                .toFloat()
                .coerceAtLeast(
                    1f
                )

        val h =
            inputHeight
                .toFloat()
                .coerceAtLeast(
                    1f
                )

        val cx =
            rect.centerX() /
                w

        val faceWidth =
            rect.width() /
                w

        val faceHeight =
            rect.height() /
                h

        val estimatedEyeY =
            (
                rect.top +
                    rect.height() *
                        0.33f
                ) /
                h

        return when {
            faceWidth >
                0.62f ||
            faceHeight >
                0.58f ->
                    "DIRECTOR • FACE TOO CLOSE"

            cx <
                0.34f ->
                    "DIRECTOR • MOVE SUBJECT RIGHT"

            cx >
                0.66f ->
                    "DIRECTOR • MOVE SUBJECT LEFT"

            estimatedEyeY <
                0.22f ->
                    "DIRECTOR • EYE LINE HIGH"

            estimatedEyeY >
                0.42f ->
                    "DIRECTOR • EYE LINE LOW"

            else ->
                "DIRECTOR • HEADROOM GOOD • EYE LINE NEAR THIRD"
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        if (
            !enabled
        ) {
            return
        }

        val w =
            width.toFloat()

        val h =
            height.toFloat()

        if (
            w <=
                0f ||
            h <=
                0f
        ) {
            return
        }

        canvas.drawLine(
            w /
                3f,
            0f,
            w /
                3f,
            h,
            gridPaint
        )

        canvas.drawLine(
            w *
                2f /
                3f,
            0f,
            w *
                2f /
                3f,
            h,
            gridPaint
        )

        canvas.drawLine(
            0f,
            h /
                3f,
            w,
            h /
                3f,
            gridPaint
        )

        canvas.drawLine(
            0f,
            h *
                2f /
                3f,
            w,
            h *
                2f /
                3f,
            gridPaint
        )

        if (
            peopleMode
        ) {
            val sx =
                w /
                    inputWidth
                        .toFloat()
                        .coerceAtLeast(
                            1f
                        )

            val sy =
                h /
                    inputHeight
                        .toFloat()
                        .coerceAtLeast(
                            1f
                        )

            faces.take(
                3
            ).forEachIndexed {
                    index,
                    face ->
                val box =
                    face.boundingBox

                val rect =
                    RectF(
                        box.left *
                            sx,
                        box.top *
                            sy,
                        box.right *
                            sx,
                        box.bottom *
                            sy
                    )

                val alert =
                    compositionMessage.contains(
                        "TOO CLOSE"
                    ) ||
                        compositionMessage.contains(
                            "MOVE SUBJECT"
                        ) ||
                        compositionMessage.contains(
                            "EYE LINE"
                        )

                canvas.drawRoundRect(
                    rect,
                    22f,
                    22f,
                    if (
                        trackingMode != "OFF" && index == 0
                    ) {
                        warningPaint
                    } else if (
                        alert
                    ) {
                        warningPaint
                    } else {
                        guidePaint
                    }
                )
            }
        }

        val panelLeft =
            18f

        val panelTop =
            (
                h *
                    0.56f
                )
                .coerceIn(
                    190f,
                    h -
                        300f
                )

        val panelBottom =
            panelTop +
                92f

        val panelRight =
            minOf(
                w -
                    18f,
                365f
            )

        canvas.drawRoundRect(
            panelLeft,
            panelTop,
            panelRight,
            panelBottom,
            16f,
            16f,
            panelPaint
        )

        val bins =
            histogram

        val usableW =
            panelRight -
                panelLeft -
                18f

        val usableH =
            48f

        val barW =
            usableW /
                bins.size
                    .toFloat()

        bins.forEachIndexed {
                index,
                value ->
            val fraction =
                value.toFloat() /
                    histogramMax
                        .toFloat()

            val left =
                panelLeft +
                    9f +
                    index *
                        barW

            val bottom =
                panelBottom -
                    13f

            val top =
                bottom -
                    usableH *
                        fraction

            canvas.drawRect(
                left,
                top,
                left +
                    max(
                        1f,
                        barW -
                            1.2f
                    ),
                bottom,
                histogramPaint
            )
        }

        labelPaint.textSize =
            18f

        canvas.drawText(
            histogramMessage,
            panelLeft +
                10f,
            panelTop +
                24f,
            labelPaint
        )

        if (
            peopleMode
        ) {
            val textY =
                (
                    panelTop -
                        18f
                    )
                    .coerceAtLeast(
                        80f
                    )

            canvas.drawRoundRect(
                18f,
                textY -
                    30f,
                w -
                    18f,
                textY +
                    12f,
                13f,
                13f,
                panelPaint
            )

            labelPaint.textSize =
                19f

            canvas.drawText(
                compositionMessage,
                30f,
                textY,
                labelPaint
            )
        }
    }

    override fun onDetachedFromWindow() {
        try {
            faceDetector.close()
        } catch (_: Exception) {
        }

        try {
            worker.shutdownNow()
        } catch (_: Exception) {
        }

        super.onDetachedFromWindow()
    }
}
