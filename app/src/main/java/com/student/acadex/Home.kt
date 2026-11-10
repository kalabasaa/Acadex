package com.student.acadex

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.Calendar

enum class BannerKind { GREETING, SCHEDULE, ANNOUNCEMENT, UPCOMING }

data class HomeBanner(val kind: BannerKind, val title: String, val message: String? = null)

/** A short, varied set of everyday academic/motivational lines; one is picked per day. */
private val DAILY_QUOTES = listOf(
    "Small progress still moves you forward.",
    "Make today count, one task at a time.",
    "Stay focused on what matters.",
    "Another day to learn something new.",
    "Your progress starts with today's effort.",
    "One step today beats zero steps tomorrow.",
    "Consistency beats intensity.",
    "Showing up is half the battle.",
    "Today's effort is tomorrow's result.",
    "Keep going — you're closer than you think."
)

fun dailyQuote(now: Calendar = Calendar.getInstance()): String =
    DAILY_QUOTES[now.get(Calendar.DAY_OF_YEAR) % DAILY_QUOTES.size]

class BannerAdapter(
    private val banners: List<HomeBanner>,
    private val onClick: (HomeBanner) -> Unit = {}
) : RecyclerView.Adapter<BannerAdapter.VH>() {
    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvBannerTitle)
        val message: TextView = view.findViewById(R.id.tvBannerMessage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_banner, parent, false))

    override fun getItemCount() = banners.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val b = banners[position]
        holder.title.text = b.title
        holder.message.apply {
            text = b.message
            visibility = if (b.message.isNullOrEmpty()) View.GONE else View.VISIBLE
        }
        val opensModal = b.kind == BannerKind.ANNOUNCEMENT || b.kind == BannerKind.SCHEDULE
        holder.itemView.isClickable = opensModal
        holder.itemView.isFocusable = opensModal
        holder.itemView.setOnClickListener(if (opensModal) View.OnClickListener { onClick(b) } else null)
    }
}

private fun minutesOf(hhmm: String): Int {
    val p = hhmm.split(":")
    return p[0].toInt() * 60 + p[1].toInt()
}

/**
 * Builds the rotating Home banners: a greeting (with a daily quote underneath), one banner per
 * announcement, a one-line summary of today's classes, and a schedule-aware "what's next"
 * banner (an upcoming class within two hours, an evening wind-down message, or a "take a rest"
 * fallback). Tapping an announcement or the schedule banner opens the full content in a modal.
 */
fun buildHomeBanners(repo: Repo, userName: String, today: String, now: Calendar, announcements: List<Announcement>): List<HomeBanner> {
    val hour = now.get(Calendar.HOUR_OF_DAY)
    val nowMinutes = hour * 60 + now.get(Calendar.MINUTE)

    val hello = when (hour) {
        in 0..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        else -> "Good evening"
    }
    val greeting = HomeBanner(
        BannerKind.GREETING,
        if (userName.isBlank()) hello else "$hello, $userName!",
        dailyQuote(now)
    )

    val announcementBanners = announcements.map { HomeBanner(BannerKind.ANNOUNCEMENT, it.title, it.body) }

    val todaysClasses = repo.classesOn(today)
    val scheduleBanner = if (todaysClasses.isEmpty()) {
        HomeBanner(BannerKind.SCHEDULE, "Today's Schedule", "No classes today! It's a perfect day to relax or do something productive.")
    } else {
        val summary = todaysClasses.joinToString(", ") { "${it.subjectName} at ${it.start.timeLabel() ?: it.start}" }
        HomeBanner(BannerKind.SCHEDULE, "Today's Schedule", summary)
    }

    val upcoming = todaysClasses
        .filter { minutesOf(it.end) > nowMinutes } // hasn't ended yet
        .filter { minutesOf(it.start) - nowMinutes in 0..120 } // starts within the next 2 hours
        .minByOrNull { minutesOf(it.start) }

    val upcomingBanner = when {
        upcoming != null -> HomeBanner(
            BannerKind.UPCOMING,
            "Upcoming class",
            "${upcoming.subjectName} at ${upcoming.start.timeLabel() ?: upcoming.start} · ${upcoming.where()}"
        )
        hour >= 18 -> HomeBanner(BannerKind.UPCOMING, "Upcoming class", "It's evening! Time to relax and recharge.")
        else -> HomeBanner(BannerKind.UPCOMING, "Upcoming class", "No upcoming classes detected yet. Take a rest!")
    }

    return listOf(greeting) + announcementBanners + listOf(scheduleBanner, upcomingBanner)
}
