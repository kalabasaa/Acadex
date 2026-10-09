package com.student.acadex

import android.os.Bundle
import android.view.View
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val nav = (supportFragmentManager.findFragmentById(R.id.navHost) as NavHostFragment).navController
        findViewById<BottomNavigationView>(R.id.bottomNav).setupWithNavController(nav)

        val fab = findViewById<FloatingActionButton>(R.id.fabQuick)
        val topLevel = setOf(R.id.dashboardFragment, R.id.calendarFragment, R.id.remindersFragment)
        nav.addOnDestinationChangedListener { _, dest, _ ->
            fab.visibility = if (dest.id in topLevel) View.VISIBLE else View.GONE
        }

        fab.setOnClickListener { v ->
            PopupMenu(this, v).apply {
                menuInflater.inflate(R.menu.quick_actions, menu)
                setOnMenuItemClickListener {
                    nav.navigate(it.itemId)
                    true
                }
                show()
            }
        }
    }
}
