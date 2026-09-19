package com.sentongoharuna.pulse

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Screen-only operator assist.
 * PEAK is a preview contrast-edge heuristic, not sensor-native focus peaking.
 * ZEBRA marks very bright preview regions.
 * Neither layer is part of CameraX OverlayEffect, so neither is saved.
 */
class DevelopUgandaShotAssistView(
    context: Context
) : View(context) {

    companion object {
        const val MODE_OFF = 0
        const val MODE_PEAK = 1
        const val MODE_ZEBRA = 2
        const val MODE_BOTH = 3
    }

    private val worker =
        Executors.newSingleThreadExecutor()

    private val busy =
        AtomicBoolean(false)

    @Volatile
    private var assistMode =
        MODE_OFF

    @Volatile
    private var edgePoints =
        FloatArray(0)

    @Volatile
    private var zebraRects =
        FloatArray(0)

    private val peakPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DevelopUgandaFivemods8Theme.accent
            strokeWidth = 2.2f
            style = Paint.Style.STROKE
        }

    private val zebraPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DevelopUgandaFivemods8Theme.contentScrim(217)
            strokeWidth = 2.0f
            style = Paint.Style.STROKE
        }

    private val labelPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DevelopUgandaFivemods8Theme.content
            textSize = 22f
        }

    fun setAssistMode(
        mode: Int
    ) {
        assistMode =
            mode.coerceIn(
                MODE_OFF,
                MODE_BOTH
            )

        if (
            assistMode ==
                MODE_OFF
        ) {
            edgePoints =
                FloatArray(0)

            zebraRects =
                FloatArray(0)
        }

        postInvalidateOnAnimation()
    }

    fun submitFrame(
        source: Bitmap
    ) {
        if (
            assistMode ==
                MODE_OFF ||
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

        val modeSnapshot =
            assistMode

        worker.execute {
            try {
                val targetWidth =
                    96

                val targetHeight =
                    max(
                        72,
                        (
                            source.height *
                                targetWidth.toFloat() /
                                source.width.toFloat()
                            ).roundToInt()
                    )

                val bitmap =
                    Bitmap.createScaledBitmap(
                        source,
                        targetWidth,
                        targetHeight,
                        false
                    )

                val edge =
                    ArrayList<Float>(
                        900
                    )

                val zebra =
                    ArrayList<Float>(
                        500
                    )

                val sx =
                    width.toFloat()
                        .coerceAtLeast(
                            1f
                        ) /
                        targetWidth.toFloat()

                val sy =
                    height.toFloat()
                        .coerceAtLeast(
                            1f
                        ) /
                        targetHeight.toFloat()

                fun luma(
                    pixel: Int
                ): Int {
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

                    return (
                        r * 54 +
                            g * 183 +
                            b * 19
                        ) /
                        256
                }

                for (
                    y in
                    1 until targetHeight - 1
                ) {
                    for (
                        x in
                        1 until targetWidth - 1
                    ) {
                        val here =
                            luma(
                                bitmap.getPixel(
                                    x,
                                    y
                                )
                            )

                        if (
                            modeSnapshot ==
                                MODE_PEAK ||
                            modeSnapshot ==
                                MODE_BOTH
                        ) {
                            val right =
                                luma(
                                    bitmap.getPixel(
                                        x + 1,
                                        y
                                    )
                                )

                            val down =
                                luma(
                                    bitmap.getPixel(
                                        x,
                                        y + 1
                                    )
                                )

                            val contrast =
                                abs(
                                    here -
                                        right
                                ) +
                                    abs(
                                        here -
                                            down
                                    )

                            if (
                                contrast >=
                                    76 &&
                                edge.size <
                                    1600
                            ) {
                                edge.add(
                                    (
                                        x +
                                            0.5f
                                        ) *
                                        sx
                                )

                                edge.add(
                                    (
                                        y +
                                            0.5f
                                        ) *
                                        sy
                                )
                            }
                        }

                        if (
                            (
                                modeSnapshot ==
                                    MODE_ZEBRA ||
                                modeSnapshot ==
                                    MODE_BOTH
                                ) &&
                            here >=
                                236 &&
                            x %
                                4 ==
                                0 &&
                            y %
                                4 ==
                                0 &&
                            zebra.size <
                                1200
                        ) {
                            zebra.add(
                                x *
                                    sx
                            )

                            zebra.add(
                                y *
                                    sy
                            )

                            zebra.add(
                                (
                                    x +
                                        4
                                    ) *
                                    sx
                            )

                            zebra.add(
                                (
                                    y +
                                        4
                                    ) *
                                    sy
                            )
                        }
                    }
                }

                bitmap.recycle()

                edgePoints =
                    edge.toFloatArray()

                zebraRects =
                    zebra.toFloatArray()

                postInvalidateOnAnimation()
            } catch (_: Exception) {
            } finally {
                busy.set(
                    false
                )
            }
        }
    }

    override fun onDraw(
        canvas: Canvas
    ) {
        super.onDraw(
            canvas
        )

        val mode =
            assistMode

        if (
            mode ==
                MODE_OFF
        ) {
            return
        }

        if (
            mode ==
                MODE_PEAK ||
            mode ==
                MODE_BOTH
        ) {
            val points =
                edgePoints

            var i =
                0

            while (
                i +
                    1 <
                points.size
            ) {
                val x =
                    points[i]

                val y =
                    points[
                        i + 1
                    ]

                canvas.drawLine(
                    x -
                        3f,
                    y,
                    x +
                        3f,
                    y,
                    peakPaint
                )

                i +=
                    2
            }
        }

        if (
            mode ==
                MODE_ZEBRA ||
            mode ==
                MODE_BOTH
        ) {
            val rects =
                zebraRects

            var i =
                0

            while (
                i +
                    3 <
                rects.size
            ) {
                val left =
                    rects[i]

                val top =
                    rects[
                        i + 1
                    ]

                val right =
                    rects[
                        i + 2
                    ]

                val bottom =
                    rects[
                        i + 3
                    ]

                canvas.drawLine(
                    left,
                    bottom,
                    right,
                    top,
                    zebraPaint
                )

                i +=
                    4
            }
        }

        canvas.drawText(
            when (mode) {
                MODE_PEAK ->
                    "ASSIST • EDGE PEAK"

                MODE_ZEBRA ->
                    "ASSIST • ZEBRA"

                else ->
                    "ASSIST • PEAK + ZEBRA"
            },
            22f,
            height -
                28f,
            labelPaint
        )
    }

    override fun onDetachedFromWindow() {
        try {
            worker.shutdownNow()
        } catch (_: Exception) {
        }

        super.onDetachedFromWindow()
    }
}
