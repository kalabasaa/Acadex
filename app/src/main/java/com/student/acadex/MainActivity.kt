package com.student.acadex

import android.animation.ObjectAnimator
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        val fab = findViewById<FloatingActionButton>(R.id.fabQuick)

        // Android 15+ draws apps edge-to-edge: keep content clear of the status bar,
        // the gesture bar and the keyboard (hide the nav bar while typing).
        if (Build.VERSION.SDK_INT >= 35) {
            ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                val typing = insets.isVisible(WindowInsetsCompat.Type.ime())
                v.updatePadding(left = bars.left, top = bars.top, right = bars.right, bottom = if (typing) ime.bottom else 0)
                bottomNav.updatePadding(bottom = if (typing) 0 else bars.bottom)
                bottomNav.visibility = if (typing) View.GONE else View.VISIBLE
                WindowInsetsCompat.CONSUMED
            }
        }

        val nav = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        bottomNav.setupWithNavController(nav)

        // pages need a solid background or they show through each other mid-transition
        val navHost = supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment
        navHost.childFragmentManager.registerFragmentLifecycleCallbacks(object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                v.setBackgroundColor(ContextCompat.getColor(this@MainActivity, R.color.bg))
            }
        }, false)

        // Tapping a tab always lands on that tab's main page (even from a sub-page),
        // instead of restoring whatever sub-page you left it on.
        val tabOptions = NavOptions.Builder()
            .setLaunchSingleTop(true)
            .setPopUpTo(nav.graph.startDestinationId, false)
            .setEnterAnim(R.anim.fade_in)
            .setExitAnim(R.anim.fade_out)
            .setPopEnterAnim(R.anim.fade_in)
            .setPopExitAnim(R.anim.fade_out)
            .build()
        bottomNav.setOnItemSelectedListener { item ->
            val id = item.itemId
            if (nav.currentDestination?.id != id && !nav.popBackStack(id, false)) {
                nav.navigate(id, null, tabOptions)
            }
            true
        }
        bottomNav.setOnItemReselectedListener { item ->
            if (nav.currentDestination?.id != item.itemId) nav.popBackStack(item.itemId, false)
        }

        val speedDial = findViewById<LinearLayout>(R.id.fabSpeedDial)
        val btnSchedule = findViewById<View>(R.id.btnFabAddSchedule)
        val btnReminders = findViewById<View>(R.id.btnFabAddReminders)
        val btnSubject = findViewById<View>(R.id.btnFabAddSubject)
        val btnTask = findViewById<View>(R.id.btnFabAddTask)

        var isExpanded = false
        val dp = resources.displayMetrics.density
        // same order as on screen, bottom to top: Task, Subject, Schedule, Reminders
        val labels = listOf(btnTask, btnSubject, btnSchedule, btnReminders)

        val easeOut = PathInterpolator(0.2f, 0f, 0f, 1f)      // smooth landing
        val easeIn = PathInterpolator(0.3f, 0f, 0.8f, 0.15f)  // smooth takeoff

        // own animator for the rotation so the fab's fade (below) can't cancel it
        var rotation: ObjectAnimator? = null
        fun rotateFab(to: Float) {
            rotation?.cancel()
            rotation = ObjectAnimator.ofFloat(fab, View.ROTATION, to).apply {
                duration = 260
                interpolator = easeOut
                start()
            }
        }

        // closing: top one leaves first, the Task one (closest to the +) leaves last
        fun collapseSpeedDial() {
            if (!isExpanded) return
            isExpanded = false
            rotateFab(0f)
            labels.reversed().forEachIndexed { i, view ->
                view.animate().cancel()
                val anim = view.animate()
                    .translationY(20 * dp)
                    .alpha(0f)
                    .setStartDelay(i * 50L)
                    .setDuration(160)
                    .setInterpolator(easeIn)
                    .withLayer()
                if (i == labels.lastIndex) {
                    // only hide it if nobody re-opened the menu in the meantime
                    anim.withEndAction { if (!isExpanded) speedDial.visibility = View.GONE }
                } else {
                    anim.withEndAction {}
                }
                anim.start()
            }
        }

        // opening: Task pops up first, then Subject, Schedule, Reminders, each a bit later
        fun expandSpeedDial() {
            if (isExpanded) return
            isExpanded = true
            rotateFab(45f)
            speedDial.visibility = View.VISIBLE
            labels.forEachIndexed { i, view ->
                view.animate().cancel()
                view.translationY = 20 * dp
                view.alpha = 0f
                view.animate()
                    .translationY(0f)
                    .alpha(1f)
                    .setStartDelay(i * 70L)
                    .setDuration(260)
                    .setInterpolator(easeOut)
                    .withLayer()
                    .withEndAction {}
                    .start()
            }
        }

        fab.setOnClickListener {
            if (isExpanded) collapseSpeedDial() else expandSpeedDial()
        }

        // the destination listener below closes the menu as the new page slides in
        btnSchedule.setOnClickListener { nav.navigate(R.id.addScheduleFragment, null, pageNavOptions()) }
        btnReminders.setOnClickListener { nav.navigate(R.id.addReminderFragment, null, pageNavOptions()) }
        btnSubject.setOnClickListener { nav.navigate(R.id.addSubjectFragment, null, pageNavOptions()) }
        btnTask.setOnClickListener { nav.navigate(R.id.addActivityFragment, null, pageNavOptions()) }

        val topLevel = setOf(
            R.id.dashboardFragment,
            R.id.calendarFragment,
            R.id.directoryFragment,
            R.id.remindersFragment,
            R.id.settingsFragment
        )
        nav.addOnDestinationChangedListener { _, dest, _ ->
            collapseSpeedDial()
            fab.animate().cancel()
            if (dest.id in topLevel) {
                if (fab.visibility != View.VISIBLE) {
                    fab.alpha = 0f
                    fab.visibility = View.VISIBLE
                }
                fab.animate().alpha(1f).setDuration(200).withEndAction {}.start()
            } else if (fab.visibility == View.VISIBLE) {
                fab.animate().alpha(0f).setDuration(150).withEndAction { fab.visibility = View.GONE }.start()
            }
        }
    }
}
