package com.student.acadex

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.OverScroller
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/**
 * ImageView with smooth pinch-to-zoom, two-finger pan, drag, fling and an animated
 * double-tap zoom. Used for the campus map.
 */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val m = Matrix()
    private val vals = FloatArray(9)
    private var zoom = 1f
    private val maxZoom = 5f
    private val doubleTapZoom = 2.5f

    private val scroller = OverScroller(context)
    private var flingX = 0
    private var flingY = 0
    private var zoomAnim: ValueAnimator? = null
    private var lastFocusX = 0f
    private var lastFocusY = 0f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = (zoom * detector.scaleFactor).coerceIn(1f, maxZoom)
            val step = target / zoom
            zoom = target
            m.postScale(step, step, detector.focusX, detector.focusY)
            // moving both fingers together pans the map as well
            m.postTranslate(detector.focusX - lastFocusX, detector.focusY - lastFocusY)
            lastFocusX = detector.focusX
            lastFocusY = detector.focusY
            render()
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (zoom > 1f && !scaleDetector.isInProgress) {
                m.postTranslate(-distanceX, -distanceY)
                render()
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (zoom > 1f && !scaleDetector.isInProgress) fling(velocityX, velocityY)
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            animateZoomTo(if (zoom > 1f) 1f else doubleTapZoom, e.x, e.y)
            return true
        }
    })

    init {
        scaleType = ImageView.ScaleType.MATRIX
    }

    override fun setImageDrawable(drawable: Drawable?) {
        // mipmaps keep text and lines clean when the big map is shown small
        (drawable as? BitmapDrawable)?.apply {
            setFilterBitmap(true)
            setMipMap(true)
        }
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    override fun onDetachedFromWindow() {
        stopMotion()
        super.onDetachedFromWindow()
    }

    /** Fit the whole image inside the view. */
    fun resetZoom() {
        stopMotion()
        val d = drawable ?: return
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        val base = min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        zoom = 1f
        m.reset()
        m.postScale(base, base)
        render()
    }

    private fun stopMotion() {
        zoomAnim?.cancel()
        zoomAnim = null
        scroller.forceFinished(true)
        removeCallbacks(flingStep)
    }

    /** Smoothly zoom to [target] around the point ([fx], [fy]). */
    private fun animateZoomTo(target: Float, fx: Float, fy: Float) {
        stopMotion()
        zoomAnim = ValueAnimator.ofFloat(zoom, target).apply {
            duration = 280
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                val z = it.animatedValue as Float
                val step = z / zoom
                zoom = z
                m.postScale(step, step, fx, fy)
                render()
            }
            start()
        }
    }

    private fun fling(vx: Float, vy: Float) {
        val d = drawable ?: return
        m.getValues(vals)
        val scale = vals[Matrix.MSCALE_X]
        val w = (d.intrinsicWidth * scale).toInt()
        val h = (d.intrinsicHeight * scale).toInt()
        val x = vals[Matrix.MTRANS_X].toInt()
        val y = vals[Matrix.MTRANS_Y].toInt()
        val minX = if (w > width) width - w else x
        val maxX = if (w > width) 0 else x
        val minY = if (h > height) height - h else y
        val maxY = if (h > height) 0 else y
        flingX = x
        flingY = y
        scroller.fling(x, y, vx.toInt(), vy.toInt(), minX, maxX, minY, maxY)
        postOnAnimation(flingStep)
    }

    private val flingStep = object : Runnable {
        override fun run() {
            if (scroller.computeScrollOffset()) {
                m.postTranslate((scroller.currX - flingX).toFloat(), (scroller.currY - flingY).toFloat())
                flingX = scroller.currX
                flingY = scroller.currY
                render()
                postOnAnimation(this)
            }
        }
    }

    private fun render() {
        val d = drawable
        if (d != null && d.intrinsicWidth > 0 && d.intrinsicHeight > 0) {
            m.getValues(vals)
            val scale = vals[Matrix.MSCALE_X]
            val w = d.intrinsicWidth * scale
            val h = d.intrinsicHeight * scale
            vals[Matrix.MTRANS_X] =
                if (w <= width) (width - w) / 2f else vals[Matrix.MTRANS_X].coerceIn(width - w, 0f)
            vals[Matrix.MTRANS_Y] =
                if (h <= height) (height - h) / 2f else vals[Matrix.MTRANS_Y].coerceIn(height - h, 0f)
            m.setValues(vals)
        }
        imageMatrix = m
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // a new touch stops any fling / zoom animation that is still running
        if (event.actionMasked == MotionEvent.ACTION_DOWN) stopMotion()
        // While zoomed (or pinching) keep the page from scrolling under the map.
        val busy = zoom > 1f || event.pointerCount > 1
        parent?.requestDisallowInterceptTouchEvent(busy)
        scaleDetector.onTouchEvent(event)
        gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
