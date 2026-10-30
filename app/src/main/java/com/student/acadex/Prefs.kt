package com.student.acadex

import android.content.Context

/** Small settings store (currently just the user's name for the dashboard greeting). */
class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("acadex_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() = sp.getString("user_name", "") ?: ""
        set(value) {
            sp.edit().putString("user_name", value).apply()
        }

    /** Last announcements response, so they still show when offline. */
    var announcementsCache: String
        get() = sp.getString("announcements_cache", "") ?: ""
        set(value) {
            sp.edit().putString("announcements_cache", value).apply()
        }
}
