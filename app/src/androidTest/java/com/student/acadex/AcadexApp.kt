package com.student.acadex

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

/** Applies the saved Light / Dark Mode choice before the first screen is created. */
class AcadexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applyTheme(Prefs(this).darkMode)
    }

    companion object {
        fun applyTheme(dark: Boolean) {
            AppCompatDelegate.setDefaultNightMode(
                if (dark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
        }
    }
}
