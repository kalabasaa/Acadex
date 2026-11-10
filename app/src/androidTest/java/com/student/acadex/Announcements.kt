package com.student.acadex

import androidx.fragment.app.Fragment
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

data class Announcement(val id: Long, val title: String, val body: String, val date: String)

/** Reads the public `announcements` table from Supabase (plain REST, no extra library). */
object AnnouncementApi {
    /** Returns the raw JSON from Supabase (newest first). Call off the main thread. */
    fun fetch(): String {
        val url = "${Supabase.URL}/rest/v1/announcements?select=id,title,body,created_at&order=created_at.desc&limit=10"
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("apikey", Supabase.KEY)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun parse(raw: String): List<Announcement> = try {
        val arr = JSONArray(raw)
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Announcement(
                id = o.optLong("id"),
                title = o.optString("title"),
                body = o.optString("body"),
                date = shortDate(o.optString("created_at"))
            )
        }
    } catch (e: Exception) {
        emptyList()
    }

    // "2026-10-31T01:23:45+00:00" -> "Oct 31"
    private fun shortDate(iso: String): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.take(10))!!
        SimpleDateFormat("MMM d", Locale.US).format(d)
    } catch (e: Exception) {
        ""
    }
}

/** Shows the cached announcements right away, then refreshes them from Supabase. */
fun Fragment.loadAnnouncements(show: (List<Announcement>) -> Unit) {
    val prefs = Prefs(requireContext())
    show(AnnouncementApi.parse(prefs.announcementsCache))
    if (!Supabase.isConfigured) return
    Thread {
        val raw = try { AnnouncementApi.fetch() } catch (e: Exception) { null }
        if (raw != null) {
            prefs.announcementsCache = raw
            activity?.runOnUiThread { if (isAdded) show(AnnouncementApi.parse(raw)) }
        }
    }.start()
}
