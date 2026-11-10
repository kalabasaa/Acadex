package com.student.acadex

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

/** Small settings store: user name, theme, Campus Map visibility and the announcements cache. */
class Prefs(context: Context) {
    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("acadex_prefs", Context.MODE_PRIVATE)

    var userName: String
        get() {
            var name = sp.getString("user_name", null)
            if (name == null) {
                name = getUserFromDb().userName
                sp.edit().putString("user_name", name).apply()
            }
            return name
        }
        set(value) {
            sp.edit().putString("user_name", value).apply()
            updateDbUser(userName = value)
        }

    /** Last announcements response, so they still show when offline. */
    var announcementsCache: String
        get() = sp.getString("announcements_cache", "") ?: ""
        set(value) {
            sp.edit().putString("announcements_cache", value).apply()
        }

    /** Dark Mode switch. Off (Light Mode) until the user turns it on. */
    var darkMode: Boolean
        get() {
            if (!sp.contains("dark_mode")) {
                val dbMode = getUserFromDb().darkMode
                sp.edit().putBoolean("dark_mode", dbMode).apply()
                return dbMode
            }
            return sp.getBoolean("dark_mode", false)
        }
        set(value) {
            sp.edit().putBoolean("dark_mode", value).apply()
            updateDbUser(darkMode = value)
        }

    /** Whether the Campus Map tab is shown. On by default. */
    var showCampusMap: Boolean
        get() {
            if (!sp.contains("show_campus_map")) {
                val dbMap = getUserFromDb().showCampusMap
                sp.edit().putBoolean("show_campus_map", dbMap).apply()
                return dbMap
            }
            return sp.getBoolean("show_campus_map", true)
        }
        set(value) {
            sp.edit().putBoolean("show_campus_map", value).apply()
            updateDbUser(showCampusMap = value)
        }

    private data class DbUser(val userName: String, val darkMode: Boolean, val showCampusMap: Boolean)

    private fun getUserFromDb(): DbUser {
        return try {
            val helper = DatabaseHelper(appContext)
            helper.readableDatabase.rawQuery("SELECT user_name, dark_mode, show_campus_map FROM users WHERE id = 1", null).use { cursor ->
                if (cursor.moveToFirst()) {
                    DbUser(
                        userName = cursor.getString(0) ?: "",
                        darkMode = cursor.getInt(1) == 1,
                        showCampusMap = cursor.getInt(2) == 1
                    )
                } else DbUser("", false, true)
            }
        } catch (e: Exception) {
            DbUser("", false, true)
        }
    }

    private fun updateDbUser(userName: String? = null, darkMode: Boolean? = null, showCampusMap: Boolean? = null) {
        try {
            val current = getUserFromDb()
            val helper = DatabaseHelper(appContext)
            val cv = ContentValues().apply {
                put("id", 1)
                put("user_name", userName ?: current.userName)
                put("dark_mode", if (darkMode ?: current.darkMode) 1 else 0)
                put("show_campus_map", if (showCampusMap ?: current.showCampusMap) 1 else 0)
            }
            helper.writableDatabase.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        } catch (e: Exception) {
            // fallback if DB not ready
        }
    }
}
