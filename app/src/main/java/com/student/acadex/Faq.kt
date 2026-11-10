package com.student.acadex

/** Questions and answers shown in Settings > Help & Support. Answers must match what the app really does. */
object Faq {
    const val WEBSITE = "https://acadex-download.vercel.app"
    const val GITHUB = "https://github.com/kalabasaa"

    val items: List<Pair<String, String>> = listOf(
        "What is Acadex?" to
            "Acadex is an academic companion that helps students organize their schedules, manage academic tasks, track grades, and stay focused throughout their academic journey.",
        "How do I enable notifications?" to
            "Go to Settings > Permissions and turn on Allow Notifications. Android will ask for your permission. If you denied it before, Android will not ask again, so open Android App Settings and allow notifications there. Push notifications are still in testing, so they may not always arrive yet.",
        "Why does Acadex need camera access?" to
            "Camera access is used exclusively for scanning schedule QR codes. It is requested only when you start a scan, it is not used in the background, and no photos or videos are saved.",
        "How do I scan another user's schedule QR code?" to
            "Go to Settings > Scan Schedule QR and tap Scan QR code. Allow camera access if asked, then point the camera at the code inside the frame. You will see a preview of the subjects and classes, and nothing is imported until you tap Import. Classes you already have are skipped, and ones that overlap your schedule are left unchecked.",
        "How do I share my subjects and schedules?" to
            "On the Home page open Share QR under Mini Features, pick the subjects you want to include, and tap Generate QR. You can show the code to a classmate or share it as an image. Only share what you intend to disclose. QR schedule sharing is in early access testing, so it may still change.",
        "How does Dark Mode work?" to
            "Go to Settings > Preferences and turn Dark Mode on or off. Your choice is saved and applied the next time you open Acadex.",
        "Can I hide the Campus Map?" to
            "Yes. The Campus Map is made for University of Eastern Pangasinan students. If that is not you, go to Settings > Preferences and turn off Show Campus Map. The Map tab is hidden and nothing is deleted. You can turn it back on at any time.",
        "How do I archive a semester?" to
            "Go to Settings > Manage Data > Archive Semester, choose the academic year and semester, and confirm. Your subjects, class schedules, tasks and their reminders move out of your active pages but are not deleted. To restore them, open Manage Data > Archived Semesters. Notes and saved GPA calculations are not archived and stay where they are.",
        "Where can I find more information about Acadex?" to
            "For additional information, guides, updates, and announcements, visit the official Acadex website.\n" + WEBSITE
    )
}
