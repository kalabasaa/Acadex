package com.student.acadex

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.util.Locale

enum class TimerPhase { IDLE, STUDY, BREAK }

/**
 * Timer session state, kept in its own SharedPreferences file rather than in-memory, so it
 * survives the fragment being destroyed/recreated or the app being backgrounded. Running time
 * is tracked against SystemClock.elapsedRealtime() (a monotonic clock, unaffected by the user
 * changing the wall-clock time) rather than counted down purely on the UI thread, so the
 * remaining time is always recomputed from a real timestamp instead of drifting.
 */
class TimerState(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("acadex_timer", Context.MODE_PRIVATE)

    var phase: TimerPhase
        get() = try {
            TimerPhase.valueOf(sp.getString("phase", TimerPhase.IDLE.name) ?: TimerPhase.IDLE.name)
        } catch (e: IllegalArgumentException) {
            TimerPhase.IDLE
        }
        set(v) = sp.edit().putString("phase", v.name).apply()

    var studyMinutes: Int
        get() = sp.getInt("study_min", 25)
        set(v) = sp.edit().putInt("study_min", v).apply()

    var breakMinutes: Int
        get() = sp.getInt("break_min", 5)
        set(v) = sp.edit().putInt("break_min", v).apply()

    var paused: Boolean
        get() = sp.getBoolean("paused", false)
        set(v) = sp.edit().putBoolean("paused", v).apply()

    /** elapsedRealtime() (ms) at which the current phase ends; only meaningful while running. */
    var endAt: Long
        get() = sp.getLong("end_at", 0L)
        set(v) = sp.edit().putLong("end_at", v).apply()

    /** Remaining ms, frozen while paused. */
    var remainingMillis: Long
        get() = sp.getLong("remaining_ms", 0L)
        set(v) = sp.edit().putLong("remaining_ms", v).apply()

    /** Total ms the current phase started with, so the ring can show remaining/total. */
    var totalMillis: Long
        get() = sp.getLong("total_ms", 0L)
        set(v) = sp.edit().putLong("total_ms", v).apply()

    fun reset() {
        phase = TimerPhase.IDLE
        paused = false
        endAt = 0
        remainingMillis = 0
        totalMillis = 0
    }
}

class FocusTimerFragment : Fragment(R.layout.fragment_focus_timer) {
    private var tickRunnable: Runnable? = null
    private var hostView: View? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        hostView = view
        val state = TimerState(requireContext())

        fun safeBack() {
            if (!isAdded) return
            val nav = findNavController()
            if (!nav.popBackStack()) {
                nav.navigate(R.id.dashboardFragment)
            }
        }

        view.findViewById<View>(R.id.btnBack)?.setOnClickListener { safeBack() }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            safeBack()
        }

        val tvPhase = view.findViewById<TextView>(R.id.tvPhase)
        val tvTime = view.findViewById<TextView>(R.id.tvTime)
        val ring = view.findViewById<CircularProgressIndicator>(R.id.ringTimer)
        val configSection = view.findViewById<View>(R.id.configSection)
        val tvStudyMin = view.findViewById<TextView>(R.id.tvStudyMin)
        val tvBreakMin = view.findViewById<TextView>(R.id.tvBreakMin)
        val btnStart = view.findViewById<View>(R.id.btnStart)
        val btnPauseResume = view.findViewById<Button>(R.id.btnPauseResume)
        val btnStop = view.findViewById<View>(R.id.btnStop)
        val btnPlus1 = view.findViewById<View>(R.id.btnPlus1)
        val btnPlus3 = view.findViewById<View>(R.id.btnPlus3)

        ring.max = 10000

        fun fmt(ms: Long): String {
            val totalSec = (ms / 1000).coerceAtLeast(0)
            return String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
        }

        // Cascades through any phases that fully elapsed while the app was away (e.g. study
        // AND break both finished while backgrounded) instead of only advancing one step.
        fun advancePhaseIfDue() {
            var guard = 0
            while (state.phase != TimerPhase.IDLE && !state.paused && guard < 4) {
                guard++
                val remaining = state.endAt - SystemClock.elapsedRealtime()
                if (remaining > 0) return
                when (state.phase) {
                    TimerPhase.STUDY -> {
                        state.phase = TimerPhase.BREAK
                        state.totalMillis = state.breakMinutes * 60_000L
                        state.endAt = SystemClock.elapsedRealtime() + state.totalMillis
                    }
                    TimerPhase.BREAK -> state.reset()
                    TimerPhase.IDLE -> return
                }
            }
        }

        fun renderIdle() {
            val ctx = context ?: return
            tvPhase.text = "Ready"
            tvPhase.setTextColor(ContextCompat.getColor(ctx, R.color.muted))
            configSection.visibility = View.VISIBLE
            tvStudyMin.text = "${state.studyMinutes} min"
            tvBreakMin.text = "${state.breakMinutes} min"
            tvTime.text = fmt(state.studyMinutes * 60_000L)
            ring.setProgressCompat(10000, false)
            btnStart.visibility = View.VISIBLE
            btnPauseResume.visibility = View.GONE
            btnStop.visibility = View.GONE
            btnPlus1.isEnabled = false
            btnPlus3.isEnabled = false
        }

        fun renderActive() {
            val ctx = context ?: return
            configSection.visibility = View.GONE
            btnStart.visibility = View.GONE
            btnPauseResume.visibility = View.VISIBLE
            btnStop.visibility = View.VISIBLE
            btnPlus1.isEnabled = true
            btnPlus3.isEnabled = true

            val isStudy = state.phase == TimerPhase.STUDY
            tvPhase.text = if (isStudy) "STUDY" else "BREAK"
            tvPhase.setTextColor(ContextCompat.getColor(ctx, if (isStudy) R.color.primary else R.color.success))
            btnPauseResume.text = if (state.paused) "Resume" else "Pause"

            val remaining = if (state.paused) state.remainingMillis
                else (state.endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
            tvTime.text = fmt(remaining)
            val total = state.totalMillis.coerceAtLeast(1)
            val frac = (remaining.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
            ring.setProgressCompat((frac * 10000).toInt(), true)
        }

        fun render() {
            if (!isAdded || context == null) return
            advancePhaseIfDue()
            if (state.phase == TimerPhase.IDLE) renderIdle() else renderActive()
        }

        val runnable = object : Runnable {
            override fun run() {
                if (!isAdded || context == null || hostView == null) return
                render()
                hostView?.postDelayed(this, 500)
            }
        }
        tickRunnable = runnable

        btnStart.setOnClickListener {
            state.phase = TimerPhase.STUDY
            state.paused = false
            state.totalMillis = state.studyMinutes * 60_000L
            state.endAt = SystemClock.elapsedRealtime() + state.totalMillis
            render()
        }
        btnPauseResume.setOnClickListener {
            if (state.paused) {
                state.endAt = SystemClock.elapsedRealtime() + state.remainingMillis
                state.paused = false
            } else {
                state.remainingMillis = (state.endAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
                state.paused = true
            }
            render()
        }
        btnStop.setOnClickListener {
            state.reset()
            render()
        }
        fun addMinutes(minutes: Int) {
            val extra = minutes * 60_000L
            if (state.paused) state.remainingMillis += extra else state.endAt += extra
            render()
        }
        btnPlus1.setOnClickListener { addMinutes(1) }
        btnPlus3.setOnClickListener { addMinutes(3) }

        view.findViewById<View>(R.id.btnStudyMinus).setOnClickListener {
            state.studyMinutes = (state.studyMinutes - 1).coerceAtLeast(1)
            render()
        }
        view.findViewById<View>(R.id.btnStudyPlus).setOnClickListener {
            state.studyMinutes = (state.studyMinutes + 1).coerceAtMost(180)
            render()
        }
        view.findViewById<View>(R.id.btnBreakMinus).setOnClickListener {
            state.breakMinutes = (state.breakMinutes - 1).coerceAtLeast(1)
            render()
        }
        view.findViewById<View>(R.id.btnBreakPlus).setOnClickListener {
            state.breakMinutes = (state.breakMinutes + 1).coerceAtMost(60)
            render()
        }

        render()
        hostView?.postDelayed(runnable, 500)
    }

    override fun onDestroyView() {
        tickRunnable?.let { r -> hostView?.removeCallbacks(r) }
        tickRunnable = null
        hostView = null
        super.onDestroyView()
    }
}
