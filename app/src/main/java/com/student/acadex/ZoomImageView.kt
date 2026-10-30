package com.student.acadex

import android.content.Context
import android.graphics.Matrix
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.min

/** ImageView with pinch-to-zoom, drag and double-tap reset. Used for the campus map. */
class ZoomImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val m = Matrix()
    private val vals = FloatArray(9)
    private var zoom = 1f

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val target = (zoom * detector.scaleFactor).coerceIn(1f, 6f)
            val step = target / zoom
            zoom = target
            m.postScale(step, step, detector.focusX, detector.focusY)
            render()
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (zoom > 1f) {
                m.postTranslate(-distanceX, -distanceY)
                render()
            }
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (zoom > 1f) {
                resetZoom()
            } else {
                zoom = 2.5f
                m.postScale(2.5f, 2.5f, e.x, e.y)
                render()
            }
            return true
        }
    })

    init {
        scaleType = ImageView.ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetZoom()
    }

    /** Fit the whole image inside the view. */
    fun resetZoom() {
        val d = drawable ?: return
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0 || d.intrinsicHeight <= 0) return
        val base = min(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        zoom = 1f
        m.reset()
        m.postScale(base, base)
        render()
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
