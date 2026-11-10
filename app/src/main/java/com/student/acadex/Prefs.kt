package com.student.acadex

import android.content.Context

/** Small settings store: user name, theme, Campus Map visibility and the announcements cache. */
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

    /** Dark Mode switch. Off (Light Mode) until the user turns it on. */
    var darkMode: Boolean
        get() = sp.getBoolean("dark_mode", false)
        set(value) {
            sp.edit().putBoolean("dark_mode", value).apply()
        }

    /** Whether the Campus Map tab is shown. On by default. */
    var showCampusMap: Boolean
        get() = sp.getBoolean("show_campus_map", true)
        set(value) {
            sp.edit().putBoolean("show_campus_map", value).apply()
        }

    /** True once Acadex has shown the system notification prompt, so it is never shown again. */
    var notificationPromptShown: Boolean
        get() = sp.getBoolean("notification_prompt_shown", false)
        set(value) {
            sp.edit().putBoolean("notification_prompt_shown", value).apply()
        }
}
