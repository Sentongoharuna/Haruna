package com.sentongoharuna.pulse

import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.SystemClock
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.WeakHashMap
import kotlin.math.abs

/** The single motion preference used by the workflow home and its settings UIs. */
enum class DevelopUgandaMotionLevel {
    OFF,
    STANDARD,
    FULL,
}

object DevelopUgandaMotionPreferences {
    private const val KEY = "motion_level"
    private const val WORKFLOW_PREFS = "develop_uganda_v258_live_workflow_home"

    fun level(context: Context): DevelopUgandaMotionLevel {
        val reduceMotion = runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
        if (reduceMotion) return DevelopUgandaMotionLevel.OFF
        val control = context.duSharedPreferences(
            DevelopUgandaV275ControlSurface.PREFS,
            Context.MODE_PRIVATE,
        )
        control.getString(KEY, null)?.let { stored ->
            return runCatching { DevelopUgandaMotionLevel.valueOf(stored) }
                .getOrDefault(DevelopUgandaMotionLevel.STANDARD)
        }

        val workflow = context.duSharedPreferences(WORKFLOW_PREFS, Context.MODE_PRIVATE)
        val livelyWasSet = control.contains("lively_first_page")
        val workflowWasSet = workflow.contains("workflow_motion")
        val styleWasSet = workflow.contains("workflow_motion_style")
        val lively = control.getBoolean("lively_first_page", true)
        val enabled = workflow.getBoolean("workflow_motion", true)
        val style = workflow.getString("workflow_motion_style", "SUBTLE")
            ?.uppercase(Locale.US)
            ?: "SUBTLE"

        // A prior explicit OFF wins over an explicit ON. This preserves the
        // user's safest motion choice when the old keys disagree.
        val migrated = when {
            (livelyWasSet && !lively) || (workflowWasSet && !enabled) -> DevelopUgandaMotionLevel.OFF
            styleWasSet && style == "NORMAL" -> DevelopUgandaMotionLevel.FULL
            else -> DevelopUgandaMotionLevel.STANDARD
        }
        // Record only the resolved key during migration. The three legacy
        // values remain untouched until the user deliberately changes level.
        control.edit().putString(KEY, migrated.name).apply()
        return migrated
    }

    fun setLevel(context: Context, level: DevelopUgandaMotionLevel) {
        context.duSharedPreferences(DevelopUgandaV275ControlSurface.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, level.name)
            .putBoolean("lively_first_page", level != DevelopUgandaMotionLevel.OFF)
            .apply()
        context.duSharedPreferences(WORKFLOW_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("workflow_motion", level != DevelopUgandaMotionLevel.OFF)
            .putString(
                "workflow_motion_style",
                when (level) {
                    DevelopUgandaMotionLevel.OFF -> "REDUCED"
                    DevelopUgandaMotionLevel.STANDARD -> "SUBTLE"
                    DevelopUgandaMotionLevel.FULL -> "NORMAL"
                },
            )
            .apply()
    }

    fun setLevel(context: Context, value: String) {
        setLevel(
            context,
            runCatching { DevelopUgandaMotionLevel.valueOf(value.uppercase(Locale.US)) }
                .getOrDefault(DevelopUgandaMotionLevel.STANDARD),
        )
    }

    fun animationAllowed(context: Context): Boolean {
        if (level(context) == DevelopUgandaMotionLevel.OFF) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return try {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            power.currentThermalStatus < PowerManager.THERMAL_STATUS_MODERATE
        } catch (_: Exception) {
            false
        }
    }
}

data class DevelopUgandaMetricSample(
    val elapsedRealtimeMs: Long,
    val wallClockMs: Long,
    val value: Double,
)

/** Reusable honest rolling history. It accepts only finite, supplied values. */
class DevelopUgandaRollingMetricBuffer(
    private val capacity: Int = 512,
    private val windowMs: Long = 60_000L,
    private val minimumSampleIntervalMs: Long = 0L,
) {
    private val samples = ArrayDeque<DevelopUgandaMetricSample>()

    @Synchronized
    fun offer(
        value: Double,
        elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
        wallClockMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!value.isFinite()) return false
        val latest = samples.lastOrNull()
        if (latest != null && elapsedRealtimeMs - latest.elapsedRealtimeMs < minimumSampleIntervalMs) {
            return false
        }
        samples.addLast(DevelopUgandaMetricSample(elapsedRealtimeMs, wallClockMs, value))
        while (samples.size > capacity.coerceAtLeast(2)) samples.removeFirst()
        prune(elapsedRealtimeMs)
        return true
    }

    @Synchronized
    fun snapshot(nowElapsedRealtimeMs: Long = SystemClock.elapsedRealtime()): List<DevelopUgandaMetricSample> {
        prune(nowElapsedRealtimeMs)
        return samples.toList()
    }

    @Synchronized
    fun clear() {
        samples.clear()
    }

    private fun prune(nowElapsedRealtimeMs: Long) {
        while (samples.isNotEmpty() && nowElapsedRealtimeMs - samples.first().elapsedRealtimeMs > windowMs) {
            samples.removeFirst()
        }
    }
}

enum class DevelopUgandaMetricOrigin(val label: String) {
    MEASURED("MEASURED"),
    DERIVED("DERIVED"),
}

data class DevelopUgandaMetricReadout(
    val id: String,
    val label: String,
    val value: Double,
    val format: (Double) -> String,
    val meaning: String,
    val action: String,
    val origin: DevelopUgandaMetricOrigin,
    val source: String,
    val warning: Boolean = false,
)

/**
 * Binds real numeric values to their existing hub refresh. There is no timer:
 * the 60-second buffer advances only when a caller supplies a measured value.
 */
object DevelopUgandaMetricReadouts {
    private data class BoundState(
        val buffer: DevelopUgandaRollingMetricBuffer = DevelopUgandaRollingMetricBuffer(),
        var lastValue: Double? = null,
        var readout: DevelopUgandaMetricReadout? = null,
        var animator: ValueAnimator? = null,
        var detachListenerInstalled: Boolean = false,
    )

    private val states = WeakHashMap<TextView, BoundState>()

    fun bind(activity: Activity, view: TextView, readout: DevelopUgandaMetricReadout, animate: Boolean) {
        if (!readout.value.isFinite()) {
            view.visibility = View.GONE
            return
        }
        val state = states.getOrPut(view) { BoundState() }
        val previous = state.lastValue
        val changed = previous == null || abs(previous - readout.value) > 0.000_001
        if (changed) state.buffer.offer(readout.value)
        state.readout = readout
        view.visibility = View.VISIBLE
        view.alpha = 1f
        DevelopUgandaFivemods8Theme.expandTouchTarget(view)
        view.isClickable = true
        view.isFocusable = true
        view.isLongClickable = true

        state.animator?.cancel()
        if (changed && previous != null && animate && DevelopUgandaMotionPreferences.animationAllowed(activity)) {
            state.animator = ValueAnimator.ofFloat(previous.toFloat(), readout.value.toFloat()).apply {
                duration = DevelopUgandaFivemods8Theme.motionDurationMs
                interpolator = DevelopUgandaFivemods8Theme.motionInterpolator(activity)
                addUpdateListener { animation ->
                    view.text = readout.format((animation.animatedValue as Float).toDouble())
                }
                start()
            }
        } else {
            view.text = readout.format(readout.value)
        }
        state.lastValue = readout.value
        if (view is DevelopUgandaLiveMetricTextView) {
            view.setHistory(state.buffer.snapshot())
        }

        view.setOnClickListener {
            show(activity, readout, state.buffer.snapshot())
        }
        view.setOnLongClickListener {
            val measuredAt = state.buffer.snapshot().lastOrNull()?.wallClockMs
                ?: System.currentTimeMillis()
            copy(activity, readout.format(readout.value), measuredAt)
            true
        }
        view.setOnTouchListener { _, event ->
            if (DevelopUgandaMotionPreferences.level(activity) != DevelopUgandaMotionLevel.FULL ||
                !DevelopUgandaMotionPreferences.animationAllowed(activity)) {
                return@setOnTouchListener false
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    DevelopUgandaStatusMotion.setBreathing(activity, view, false)
                    view.animate().scaleX(0.98f).scaleY(0.98f)
                        .useDevelopUgandaMotion(activity).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f)
                    .useDevelopUgandaMotion(activity)
                    .withEndAction { DevelopUgandaStatusMotion.setBreathing(activity, view, readout.warning) }
                    .start()
            }
            false
        }
        if (!state.detachListenerInstalled) {
            state.detachListenerInstalled = true
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    state.animator?.cancel()
                    v.animate().cancel()
                    v.removeOnAttachStateChangeListener(this)
                    state.detachListenerInstalled = false
                }
            })
        }

        DevelopUgandaStatusMotion.setBreathing(activity, view, readout.warning)
    }

    fun show(
        activity: Activity,
        readout: DevelopUgandaMetricReadout,
        samples: List<DevelopUgandaMetricSample>,
    ) {
        val unit = DevelopUgandaFivemods8Theme.spacingUnitPx
        val body = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(unit * 2, unit * 2, unit * 2, unit * 2)
            setBackgroundColor(DevelopUgandaFivemods8Theme.surface)
        }
        body.addView(text(activity, readout.label, 16f, true))
        body.addView(text(activity, readout.format(readout.value), 40f, true).apply {
            typeface = Typeface.MONOSPACE
            setTextColor(if (readout.warning) DevelopUgandaFivemods8Theme.warning else DevelopUgandaFivemods8Theme.content)
            setPadding(0, unit, 0, unit)
        })
        body.addView(
            DevelopUgandaMetricTrendView(activity).apply { setSamples(samples) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, unit * 12),
        )
        body.addView(text(activity, readout.meaning, 13f, false).apply { setPadding(0, unit, 0, 0) })
        body.addView(text(activity, "ACTION • ${readout.action}", 13f, true).apply { setPadding(0, unit, 0, 0) })
        body.addView(
            text(
                activity,
                "${readout.origin.label} • ${readout.source} • 60 SECOND ROLLING WINDOW",
                11f,
                true,
            ).apply {
                setTextColor(DevelopUgandaFivemods8Theme.contentDim)
                setPadding(0, unit, 0, 0)
            },
        )
        AlertDialog.Builder(activity)
            .setView(body)
            .setPositiveButton("CLOSE", null)
            .show()
    }

    fun copy(context: Context, value: String, timestampMs: Long) {
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date(timestampMs))
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("develop.uganda readout", "$value • $stamp"))
        Toast.makeText(context, "VALUE + TIMESTAMP COPIED", Toast.LENGTH_SHORT).show()
    }

    private fun text(context: Context, value: String, size: Float, bold: Boolean) = TextView(context).apply {
        text = value
        DevelopUgandaFivemods8Theme.applyTypeScale(this, size)
        setTextColor(DevelopUgandaFivemods8Theme.content)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        gravity = Gravity.START
    }
}

/** FULL-only warning/record breathing, owned in one lifecycle-aware component. */
object DevelopUgandaStatusMotion {
    private data class State(
        var generation: Int = 0,
        var breathing: Boolean = false,
        var detachListenerInstalled: Boolean = false,
    )

    private val states = WeakHashMap<View, State>()

    fun setBreathing(activity: Activity, view: View, active: Boolean) {
        val state = states.getOrPut(view) { State() }
        val allowed = active &&
            DevelopUgandaMotionPreferences.level(activity) == DevelopUgandaMotionLevel.FULL &&
            DevelopUgandaMotionPreferences.animationAllowed(activity) &&
            view.isAttachedToWindow
        if (allowed && state.breathing) return

        state.generation += 1
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
        state.breathing = allowed
        if (!allowed) return

        if (!state.detachListenerInstalled) {
            state.detachListenerInstalled = true
            view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit
                override fun onViewDetachedFromWindow(v: View) {
                    stop(v)
                    v.removeOnAttachStateChangeListener(this)
                    state.detachListenerInstalled = false
                }
            })
        }
        pulse(activity, view, state, state.generation, dim = true)
    }

    fun stopAll(activity: Activity) {
        states.keys.toList().filter { it.context === activity }.forEach(::stop)
    }

    private fun stop(view: View) {
        val state = states[view] ?: return
        state.generation += 1
        state.breathing = false
        view.animate().cancel()
        view.alpha = 1f
        view.scaleX = 1f
        view.scaleY = 1f
    }

    private fun pulse(activity: Activity, view: View, state: State, generation: Int, dim: Boolean) {
        if (!state.breathing || state.generation != generation || !view.isAttachedToWindow ||
            !DevelopUgandaMotionPreferences.animationAllowed(activity)) {
            stop(view)
            return
        }
        view.animate()
            .alpha(if (dim) 0.78f else 1f)
            .scaleX(if (dim) 1.012f else 1f)
            .scaleY(if (dim) 1.012f else 1f)
            .setStartDelay(DevelopUgandaFivemods8Theme.motionDurationMs * 3L)
            .useDevelopUgandaMotion(activity)
            .withEndAction { pulse(activity, view, state, generation, !dim) }
            .start()
    }
}

/** Numeric TextView with a quiet real-history trace behind its digits. */
class DevelopUgandaLiveMetricTextView(context: Context) : TextView(context) {
    private var samples: List<DevelopUgandaMetricSample> = emptyList()
    private val historyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DevelopUgandaFivemods8Theme.accentScrim(70)
        strokeWidth = resources.displayMetrics.density * 1.5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    fun setHistory(value: List<DevelopUgandaMetricSample>) {
        samples = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val points = samples
        if (points.size >= 2) {
            val minTime = points.first().elapsedRealtimeMs
            val maxTime = points.last().elapsedRealtimeMs.coerceAtLeast(minTime + 1L)
            var minValue = points.minOf { it.value }
            var maxValue = points.maxOf { it.value }
            if (abs(maxValue - minValue) < 0.000_001) {
                minValue -= 1.0
                maxValue += 1.0
            }
            val left = paddingLeft.toFloat()
            val right = (width - paddingRight).toFloat().coerceAtLeast(left + 1f)
            val top = paddingTop.toFloat()
            val bottom = (height - paddingBottom).toFloat().coerceAtLeast(top + 1f)
            val path = Path()
            points.forEachIndexed { index, sample ->
                val x = left + (right - left) * ((sample.elapsedRealtimeMs - minTime).toFloat() / (maxTime - minTime).toFloat())
                val y = bottom - (bottom - top) * ((sample.value - minValue) / (maxValue - minValue)).toFloat()
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, historyPaint)
        }
        super.onDraw(canvas)
    }
}

/** Token-timed, timer-free bar for bounded real metrics. */
class DevelopUgandaEasedMetricBar(context: Context) : View(context) {
    private var level = 0f
    private var animator: ValueAnimator? = null
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DevelopUgandaFivemods8Theme.outlineScrim(90)
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DevelopUgandaFivemods8Theme.accent
    }

    fun setAccent(color: Int) {
        fill.color = color
        invalidate()
    }

    fun setLevel(value: Double, animate: Boolean) {
        if (!value.isFinite()) {
            visibility = GONE
            return
        }
        visibility = VISIBLE
        val next = value.toFloat().coerceIn(0f, 100f)
        if (abs(level - next) < 0.000_001f) return
        animator?.cancel()
        if (animate && isAttachedToWindow && DevelopUgandaMotionPreferences.animationAllowed(context)) {
            animator = ValueAnimator.ofFloat(level, next).apply {
                duration = DevelopUgandaFivemods8Theme.motionDurationMs
                interpolator = DevelopUgandaFivemods8Theme.motionInterpolator(context)
                addUpdateListener {
                    level = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            level = next
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val radius = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, track)
        canvas.drawRoundRect(0f, 0f, width * (level / 100f), height.toFloat(), radius, radius, fill)
    }
}

/** A reusable, timer-free plot for any supplied rolling metric buffer. */
class DevelopUgandaMetricTrendView(context: Context) : View(context) {
    private var samples: List<DevelopUgandaMetricSample> = emptyList()
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DevelopUgandaFivemods8Theme.outline
        strokeWidth = resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DevelopUgandaFivemods8Theme.accent
        strokeWidth = resources.displayMetrics.density * 2f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val caption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = DevelopUgandaFivemods8Theme.contentDim
        textSize = resources.displayMetrics.scaledDensity * 11f
    }

    fun setSamples(value: List<DevelopUgandaMetricSample>) {
        samples = value
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(),
            DevelopUgandaFivemods8Theme.radiusPx.toFloat(),
            DevelopUgandaFivemods8Theme.radiusPx.toFloat(), grid)
        val points = samples
        if (points.isEmpty()) {
            canvas.drawText("NO LIVE SAMPLES IN THE LAST 60 SECONDS", 12f, height / 2f, caption)
            return
        }
        val minTime = points.first().elapsedRealtimeMs
        val maxTime = points.last().elapsedRealtimeMs.coerceAtLeast(minTime + 1L)
        var minValue = points.minOf { it.value }
        var maxValue = points.maxOf { it.value }
        if (abs(maxValue - minValue) < 0.000_001) {
            minValue -= 1.0
            maxValue += 1.0
        }
        val left = 10f
        val right = (width - 10).toFloat().coerceAtLeast(left + 1f)
        val top = 10f
        val bottom = (height - 22).toFloat().coerceAtLeast(top + 1f)
        canvas.drawLine(left, (top + bottom) / 2f, right, (top + bottom) / 2f, grid)
        val path = Path()
        points.forEachIndexed { index, sample ->
            val x = left + (right - left) * ((sample.elapsedRealtimeMs - minTime).toFloat() / (maxTime - minTime).toFloat())
            val y = bottom - (bottom - top) * ((sample.value - minValue) / (maxValue - minValue)).toFloat()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, line)
        canvas.drawText("${points.size} REAL SAMPLE${if (points.size == 1) "" else "S"}", left, height - 5f, caption)
    }
}
