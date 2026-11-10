package com.student.acadex

/**
 * Privacy Policy text for Acadex v2.3.0. It describes what the code actually does:
 * the only network call is the announcements request to Supabase (see Announcements.kt).
 * Lines that start with a number and a dot ("1. ...") are shown as section headings.
 */
object PrivacyPolicy {
    const val TEXT = """
Acadex v2.3.0 · Last updated: November 8, 2026

1. Our Commitment

Your academic data belongs to you. Acadex is designed to help you manage your academic information without claiming ownership of your personal content.

Acadex does not sell your personal academic information.

2. Information Stored on Your Device

The following is created by you and stored locally on your device (in a private SQLite database and app preferences). It is not uploaded to any Acadex server:

- Subjects and schedules: subject names, codes, professors, class days, times and rooms.
- Notes: titles, text and the subject a note belongs to.
- Grades and GPA calculations: the calculations you save.
- Tasks and activities: assignments, projects, quizzes, exams, due dates and their reminders.
- Archived semesters: copies of subjects, schedules, tasks and reminders that you archived. They stay on your device until you restore or delete them.
- User preferences: your name, Dark Mode, Campus Map visibility, timer settings, and a saved copy of the latest announcements so they can show offline.

Android may include app data in device backups depending on your device settings.

3. Information Handled by External Services

Acadex contacts one external service: Supabase, which hosts the online announcements.

- What is sent: a request to read the latest announcements. It includes Acadex's public access key. It does not include your name, subjects, schedules, notes, grades, tasks or any other academic information.
- Why: to show announcements on the Home page.
- Who receives it: Supabase. As with any internet request, the service can see technical connection details such as your IP address, and may keep ordinary server logs.
- What comes back: the announcement text, which is saved on your device for offline viewing.

The app code does not include advertising, analytics or usage-tracking tools, and it does not upload your academic data. Opening the Acadex website or GitHub links from Settings opens your browser, which is then covered by those sites' own policies.

4. Camera Privacy

Camera access is used exclusively for scanning schedule QR codes. It is requested only when you choose to scan, and the camera is active only on the scanner screen. Acadex does not use the camera for surveillance, does not record or save photos or videos, and is not designed to run the camera silently in the background. The camera is released when you leave the scanner.

5. Notification Privacy

Notifications are controlled by Android permissions. You can allow or block them in Settings > Permissions or in Android App Settings. If you do not allow them, Acadex will not show notifications. Push notifications are currently in testing.

6. QR Code Privacy

A QR code made by Acadex contains the subjects and class schedules you selected to share (names, codes, professors, days, times and rooms). Anyone who can see or scan the code can read this information, so review your selection before sharing. Acadex does not automatically share all of your academic information. When you scan a code, the content is read on your device, checked, and shown as a preview before anything is imported.

7. Your Control

You can:
- Edit or delete your subjects, schedules, tasks and notes.
- Archive and restore semesters, or delete archived semesters.
- Delete all schedules or all tasks from Settings > Manage Data.
- Allow or block notifications, and allow or block camera access, in Android settings.
- Choose which subjects you share by QR code.

Uninstalling Acadex or clearing its app data removes the information stored on your device.

8. Updates to This Policy

This Privacy Policy was last updated on November 8, 2026. It may change when new functionality is introduced, and the latest version will always be available in Settings > Privacy Policy.

9. Contact

Privacy questions can be sent to the developer, kalabasaa, through github.com/kalabasaa or acadex-download.vercel.app.
"""
}
