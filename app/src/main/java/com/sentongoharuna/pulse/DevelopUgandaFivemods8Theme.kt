package com.sentongoharuna.pulse

import android.app.Application
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.graphics.Rect
import android.view.MotionEvent
import android.view.TouchDelegate
import android.view.ViewPropertyAnimator
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.view.animation.Interpolator
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.WeakHashMap

/**
 * FIVEMODS 8 XML-only resource reader.
 *
 * Values live once in res/values/develop_uganda_theme.xml. Kotlin contains no
 * ARGB literals or token values: it only reads the requested XML resource.
 */
object DevelopUgandaFivemods8Theme {
    private lateinit var applicationContext: Context

    fun install(context: Context) {
        applicationContext = context.applicationContext
    }

    @ColorInt
    fun color(@ColorRes resourceId: Int): Int =
        applicationContext.getColor(resourceId)

    @get:ColorInt val surface: Int get() = color(R.color.du_surface)
    @get:ColorInt val surfaceRaised: Int get() = color(R.color.du_surface_raised)
    @get:ColorInt val content: Int get() = color(R.color.du_content)
    @get:ColorInt val contentDim: Int get() = color(R.color.du_content_dim)
    @get:ColorInt val accent: Int get() = color(R.color.du_accent)
    @get:ColorInt val modeMain: Int get() = color(R.color.du_mode_main)
    @get:ColorInt val modeLive: Int get() = color(R.color.du_mode_live)
    @get:ColorInt val modeTikTok: Int get() = color(R.color.du_mode_tiktok)
    @get:ColorInt val modeStatus: Int get() = color(R.color.du_mode_status)
    @get:ColorInt val modeInterview: Int get() = color(R.color.du_mode_interview)
    @get:ColorInt val outline: Int get() = color(R.color.du_outline)
    @get:ColorInt val record: Int get() = color(R.color.du_record)
    @get:ColorInt val warning: Int get() = color(R.color.du_warning)
    @get:ColorInt val systemChrome: Int get() = color(R.color.du_system_chrome)
    @get:ColorInt val transparent: Int get() = color(R.color.du_transparent)

    val spacingUnitPx: Int get() = applicationContext.resources.getDimensionPixelSize(R.dimen.du_spacing_unit)
    val touchMinimumPx: Int get() = applicationContext.resources.getDimensionPixelSize(R.dimen.du_touch_minimum)
    val radiusPx: Int get() = applicationContext.resources.getDimensionPixelSize(R.dimen.du_radius)
    val motionDurationMs: Long
        get() = applicationContext.resources.getInteger(R.integer.du_motion_fast_out_slow_in_duration).toLong()

    fun motionInterpolator(context: Context): Interpolator =
        AnimationUtils.loadInterpolator(context, R.interpolator.du_motion_fast_out_slow_in)

    fun typeStyleFor(requestedSp: Float): Int = listOf(
        11f to R.style.du_type_11,
        12f to R.style.du_type_12,
        13f to R.style.du_type_13,
        14f to R.style.du_type_14,
        16f to R.style.du_type_16,
        20f to R.style.du_type_20,
        22f to R.style.du_type_22,
        40f to R.style.du_type_40,
    ).minBy { (step, _) -> kotlin.math.abs(requestedSp - step) }.second

    fun applyTypeScale(view: TextView, requestedSp: Float) {
        view.setTextAppearance(typeStyleFor(requestedSp))
    }

    fun spacingPx(requestedDp: Int): Int {
        if (requestedDp == 0) return 0
        val units = kotlin.math.max(1, kotlin.math.round(requestedDp / 8f).toInt())
        return spacingUnitPx * units
    }

    fun enforceTouchTargets(root: View) {
        if (root.isClickable || root.isLongClickable) {
            expandTouchTarget(root)
        }
        if (root is ViewGroup) {
            for (index in 0 until root.childCount) enforceTouchTargets(root.getChildAt(index))
        }
    }

    /** Keeps the drawn control unchanged while expanding only its hit region. */
    fun expandTouchTarget(view: View) {
        view.post {
            val parent = view.parent as? View ?: return@post
            val bounds = Rect()
            view.getHitRect(bounds)
            val horizontal = maxOf(0, touchMinimumPx - bounds.width())
            val vertical = maxOf(0, touchMinimumPx - bounds.height())
            if (horizontal == 0 && vertical == 0) return@post
            bounds.left -= horizontal / 2
            bounds.right += horizontal - horizontal / 2
            bounds.top -= vertical / 2
            bounds.bottom += vertical - vertical / 2
            val group = (parent.touchDelegate as? DevelopUgandaTouchDelegateGroup)
                ?: DevelopUgandaTouchDelegateGroup(parent).also { parent.touchDelegate = it }
            group.add(bounds, view)
        }
    }

    /*
     * Compatibility alpha composition only. It derives opacity from XML
     * colours and does not introduce another palette or token definition.
     */
    @ColorInt fun surfaceScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(surface, alpha)
    @ColorInt fun raisedScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(surfaceRaised, alpha)
    @ColorInt fun contentScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(content, alpha)
    @ColorInt fun contentDimScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(contentDim, alpha)
    @ColorInt fun accentScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(accent, alpha)
    @ColorInt fun outlineScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(outline, alpha)
    @ColorInt fun recordScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(record, alpha)
    @ColorInt fun warningScrim(alpha: Int): Int = ColorUtils.setAlphaComponent(warning, alpha)
}

private class DevelopUgandaTouchDelegateGroup(parent: View) : TouchDelegate(Rect(), parent) {
    private val delegates = mutableListOf<TouchDelegate>()

    fun add(bounds: Rect, target: View) {
        delegates.removeAll { (it as? TaggedTouchDelegate)?.target === target }
        delegates += TaggedTouchDelegate(Rect(bounds), target)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        for (delegate in delegates) if (delegate.onTouchEvent(event)) return true
        return false
    }

    private class TaggedTouchDelegate(bounds: Rect, val target: View) : TouchDelegate(bounds, target)
}

fun ViewPropertyAnimator.useDevelopUgandaMotion(context: Context): ViewPropertyAnimator =
    setDuration(DevelopUgandaFivemods8Theme.motionDurationMs)
        .setInterpolator(DevelopUgandaFivemods8Theme.motionInterpolator(context))

/** Installs the XML resource reader before any activity creates a view. */
class DevelopUgandaFivemods8Application : Application() {
    override fun onCreate() {
        super.onCreate()
        DevelopUgandaFivemods8Theme.install(this)
        // Runs before any Activity reads LIVE/safety settings. The migration
        // is idempotent and repairs legacy String ON/OFF values in place.
        DevelopUgandaPreferenceMigration.run(this)
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                DevelopUgandaFivemods12StatusInsets.apply(activity)
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) {
                activity.findViewById<View>(android.R.id.content)?.let {
                    DevelopUgandaBroadcastPresentation.flattenChrome(it)
                }
                if (!DevelopUgandaFivemods12StatusInsets.isImmersiveCamera(activity)) {
                    activity.findViewById<View>(android.R.id.content)?.let { content ->
                        DevelopUgandaBroadcastPresentation.applyReadability(content)
                        DevelopUgandaFivemods12LiveDigits.attach(activity, content)
                    }
                }
            }
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                DevelopUgandaFivemods12LiveDigits.detach(activity)
            }
        })
    }
}

/** Adds only the system top inset; immersive camera layouts remain untouched. */
object DevelopUgandaFivemods12StatusInsets {
    private val originalPadding = WeakHashMap<View, IntArray>()

    fun apply(activity: Activity) {
        // Editor already owns a system-bar inset listener on its content root.
        if (isImmersiveCamera(activity) || activity is DevelopUgandaEditorActivity) return
        val content = activity.findViewById<View>(android.R.id.content) ?: return
        val base = originalPadding.getOrPut(content) {
            intArrayOf(content.paddingLeft, content.paddingTop, content.paddingRight, content.paddingBottom)
        }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(base[0], base[1] + statusTop, base[2], base[3])
            insets
        }
        ViewCompat.requestApplyInsets(content)
    }

    fun isImmersiveCamera(activity: Activity): Boolean =
            activity is DevelopUgandaCameraActivity ||
            activity is DevelopUgandaLiveActivity ||
            activity is DevelopUgandaRtmpsLiveActivity ||
            activity.javaClass.simpleName.contains("CameraActivity")
}

/**
 * Adds a short roll only when an already-visible real numeric value changes.
 * UNKNOWN/UNAVAILABLE stay motionless, and camera activities are never watched.
 */
object DevelopUgandaFivemods12LiveDigits {
    private data class Binding(
        val root: View,
        val values: WeakHashMap<TextView, String>,
        val listener: ViewTreeObserver.OnGlobalLayoutListener,
    )

    private val bindings = WeakHashMap<Activity, Binding>()
    private val numeric = Regex("\\d")

    @Synchronized
    fun attach(activity: Activity, root: View) {
        if (bindings[activity]?.root === root) return
        detach(activity)
        val values = WeakHashMap<TextView, String>()
        lateinit var listener: ViewTreeObserver.OnGlobalLayoutListener
        listener = ViewTreeObserver.OnGlobalLayoutListener {
            scan(root) { view ->
                if (view !is TextView || view is DevelopUgandaLiveMetricTextView) return@scan
                val current = view.text?.toString().orEmpty()
                val previous = values.put(view, current)
                val upper = current.uppercase(java.util.Locale.US)
                val known = "UNKNOWN" !in upper && "UNAVAILABLE" !in upper
                if (previous != null && previous != current && known && numeric.containsMatchIn(current)) {
                    roll(activity, view)
                }
            }
        }
        bindings[activity] = Binding(root, values, listener)
        listener.onGlobalLayout()
        root.viewTreeObserver.addOnGlobalLayoutListener(listener)
    }

    @Synchronized
    fun detach(activity: Activity) {
        val binding = bindings.remove(activity) ?: return
        if (binding.root.viewTreeObserver.isAlive) {
            binding.root.viewTreeObserver.removeOnGlobalLayoutListener(binding.listener)
        }
    }

    private fun roll(activity: Activity, view: TextView) {
        val level = DevelopUgandaBroadcastPresentation.motionLevel(activity)
        view.animate().cancel()
        if (level == DevelopUgandaMotionLevel.OFF) {
            view.alpha = 1f
            view.translationY = 0f
            view.scaleX = 1f
            view.scaleY = 1f
            return
        }
        view.alpha = 0.72f
        view.translationY = view.resources.displayMetrics.density * 4f
        if (level == DevelopUgandaMotionLevel.FULL) {
            view.scaleX = 1.02f
            view.scaleY = 1.02f
        }
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .useDevelopUgandaMotion(activity)
            .start()
    }

    private fun scan(view: View, action: (View) -> Unit) {
        action(view)
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) scan(view.getChildAt(index), action)
        }
    }
}
