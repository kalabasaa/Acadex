package com.student.acadex

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.content.ContextCompat

/**
 * Spinner that looks and animates like the Settings accordions: the shared ic_chevron_down arrow,
 * rotated to 180° over 200ms while the list is open and back to 0° when it closes.
 * It is still a regular Spinner, so adapters, selection and listeners work unchanged.
 */
class DropdownSpinner @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = androidx.appcompat.R.attr.spinnerStyle
) : AppCompatSpinner(context, attrs, defStyleAttr) {

    private val chevron = ContextCompat.getDrawable(context, R.drawable.ic_chevron_down)!!.mutate()
    private var angle = 0f
    private var animator: ValueAnimator? = null
    private val d = resources.displayMetrics.density

    init {
        background = ContextCompat.getDrawable(context, R.drawable.bg_dropdown)
        val pad = (16 * d).toInt()
        setPadding(pad, 0, (48 * d).toInt(), 0)
        setWillNotDraw(false)
    }

    private fun rotateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(angle, target).apply {
            duration = 200
            addUpdateListener { angle = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun performClick(): Boolean {
        val handled = super.performClick()
        if (handled) rotateTo(180f)
        return handled
    }

    // the list popup is its own window: when it closes, the host window regains focus
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && angle != 0f) rotateTo(0f)
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = (24 * d).toInt()
        val right = width - (12 * d).toInt()
        val left = right - size
        val top = (height - size) / 2
        chevron.setBounds(left, top, right, top + size)
        canvas.save()
        canvas.rotate(angle, left + size / 2f, top + size / 2f)
        chevron.draw(canvas)
        canvas.restore()
    }
}
