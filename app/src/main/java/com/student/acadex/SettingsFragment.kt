package com.student.acadex

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.provider.Settings
import android.text.util.Linkify
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.content.DialogInterface
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputLayout
import java.util.Calendar

class SettingsFragment : Fragment(R.layout.fragment_settings) {
    private var importer: QrImportPreview? = null
    private var refreshPermissions: () -> Unit = {}

    // Notification prompt (Android 13+). Only used after the user flips the switch, and only once.
    private val notifLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshPermissions()
        if (!granted) showEnableInSettings(notifications = true, turnOn = true)
    }

    // The camera permission is requested by the scanner screen itself, when a scan starts.
    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        refreshPermissions()
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            result.data?.getStringExtra(QrScanActivity.EXTRA_TEXT)?.let { importer?.handleScan(it) }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions() // e.g. back from Android App Settings
    }

    override fun onDestroyView() {
        super.onDestroyView()
        importer = null
        refreshPermissions = {}
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED

    private fun openLink(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast("No app found to open the link")
        }
    }

    private fun openAppSettings(notifications: Boolean) {
        val pkg = requireContext().packageName
        val intent = if (notifications && Build.VERSION.SDK_INT >= 26) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Could not open Android settings")
        }
    }

    private fun showEnableInSettings(notifications: Boolean, turnOn: Boolean) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (turnOn) "Notifications are off" else "Turn off notifications")
            .setMessage(
                if (turnOn) "Android is blocking notifications for Acadex. To allow them, open Android App Settings and turn on notifications for Acadex."
                else "Android controls this permission. To turn notifications off, open Android App Settings and switch them off for Acadex."
            )
            .setNegativeButton("Not now", null)
            .setPositiveButton("Open App Settings") { _, _ -> openAppSettings(notifications) }
            .show()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val prefs = Prefs(requireContext())

        // Header Profile Name & Stats
        val tvProfileName = view.findViewById<TextView>(R.id.tvProfileName)
        val tvProfileStats = view.findViewById<TextView>(R.id.tvProfileStats)

        fun updateProfileHeader() {
            tvProfileName.text = prefs.userName.ifBlank { "Student" }
            val subjectsCount = repo.subjects().size
            val tasks = repo.activities()
            val pendingCount = tasks.count { it.status == "Pending" || it.status == "In Progress" }
            val finishedCount = tasks.count { it.status == "Completed" || it.status == "Finished" }
            val missedCount = tasks.count { it.status == "Missed" }
            tvProfileStats.text = "$subjectsCount total subjects · $pendingCount pending task · $finishedCount finished · $missedCount missed"
        }
        updateProfileHeader()

        // Accordion helper with ImageView arrow rotation
        fun toggleAccordion(header: View, container: View, arrow: ImageView) {
            header.setOnClickListener {
                val isOpen = container.visibility == View.VISIBLE
                TransitionManager.beginDelayedTransition(view as ViewGroup)
                container.visibility = if (isOpen) View.GONE else View.VISIBLE
                arrow.animate().rotation(if (isOpen) 0f else 180f).setDuration(200).start()
            }
        }

        toggleAccordion(
            view.findViewById(R.id.headerProfile),
            view.findViewById(R.id.containerProfile),
            view.findViewById(R.id.arrowProfile)
        )
        toggleAccordion(
            view.findViewById(R.id.headerPrefs),
            view.findViewById(R.id.containerPrefs),
            view.findViewById(R.id.arrowPrefs)
        )
        toggleAccordion(
            view.findViewById(R.id.headerImport),
            view.findViewById(R.id.containerImport),
            view.findViewById(R.id.arrowImport)
        )
        toggleAccordion(
            view.findViewById(R.id.headerData),
            view.findViewById(R.id.containerData),
            view.findViewById(R.id.arrowData)
        )
        toggleAccordion(
            view.findViewById(R.id.headerTerms),
            view.findViewById(R.id.containerTerms),
            view.findViewById(R.id.arrowTerms)
        )
        toggleAccordion(
            view.findViewById(R.id.headerHelp),
            view.findViewById(R.id.containerHelp),
            view.findViewById(R.id.arrowHelp)
        )
        toggleAccordion(
            view.findViewById(R.id.headerAbout),
            view.findViewById(R.id.containerAbout),
            view.findViewById(R.id.arrowAbout)
        )

        // coming back from the archive pages: land on Manage Data again
        if (reopenManageData) {
            reopenManageData = false
            view.findViewById<View>(R.id.containerData).visibility = View.VISIBLE
            view.findViewById<ImageView>(R.id.arrowData).rotation = 180f
        }

        view.findViewById<TextView>(R.id.tvTermsText).text = renderLegal(requireContext(), TermsAndConditions.TEXT)
        view.findViewById<TextView>(R.id.tvPrivacyText).text = renderLegal(requireContext(), PrivacyPolicy.TEXT)

        toggleAccordion(view.findViewById(R.id.headerPermissions), view.findViewById(R.id.containerPermissions), view.findViewById(R.id.arrowPermissions))
        toggleAccordion(view.findViewById(R.id.headerScanQr), view.findViewById(R.id.containerScanQr), view.findViewById(R.id.arrowScanQr))
        toggleAccordion(view.findViewById(R.id.headerPrivacy), view.findViewById(R.id.containerPrivacy), view.findViewById(R.id.arrowPrivacy))

        // ---- Permissions: always show the real Android state ----
        val switchNotif = view.findViewById<MaterialSwitch>(R.id.switchNotifications)
        val tvNotifStatus = view.findViewById<TextView>(R.id.tvNotifStatus)
        val tvCameraStatus = view.findViewById<TextView>(R.id.tvCameraStatus)
        var updatingSwitch = false

        fun refreshPermissionStatus() {
            val notifOn = NotificationManagerCompat.from(requireContext()).areNotificationsEnabled()
            updatingSwitch = true
            switchNotif.isChecked = notifOn
            updatingSwitch = false
            tvNotifStatus.text = if (notifOn) "Status: Allowed" else "Status: Not allowed"
            tvCameraStatus.text = if (granted(Manifest.permission.CAMERA)) {
                "Status: Allowed. Used only when you scan a QR code."
            } else {
                "Status: Not allowed. Acadex asks only when you start a scan. If you chose Don't allow, enable it in Android App Settings > Permissions > Camera."
            }
        }
        refreshPermissions = ::refreshPermissionStatus
        refreshPermissionStatus()

        switchNotif.setOnCheckedChangeListener { _, wantOn ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            val canAsk = wantOn && Build.VERSION.SDK_INT >= 33 &&
                !granted(Manifest.permission.POST_NOTIFICATIONS) && !prefs.notificationPromptShown
            if (canAsk) {
                prefs.notificationPromptShown = true // never ask a second time
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                showEnableInSettings(notifications = true, turnOn = wantOn)
            }
            refreshPermissionStatus() // the switch only moves when Android really changes
        }
        view.findViewById<View>(R.id.btnCameraSettings).setOnClickListener { openAppSettings(notifications = false) }

        // ---- Scan Schedule QR (reuses the Share QR scanner, validation, preview and import) ----
        importer = QrImportPreview(this, view) { updateProfileHeader() }
        view.findViewById<View>(R.id.btnStartScan).setOnClickListener {
            view.findViewById<View>(R.id.tvImportError).visibility = View.GONE
            importer?.clear()
            scanLauncher.launch(Intent(requireContext(), QrScanActivity::class.java))
        }

        // ---- FAQ ----
        val faqList = view.findViewById<LinearLayout>(R.id.faqList)
        val dp = resources.displayMetrics.density
        Faq.items.forEachIndexed { i, (question, answer) ->
            faqList.addView(TextView(requireContext(), null, 0, R.style.Body).apply {
                text = question
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = if (i == 0) 0 else (12 * dp).toInt() }
            })
            faqList.addView(TextView(requireContext(), null, 0, R.style.Hint).apply {
                text = answer
                if (answer.contains("http")) {
                    Linkify.addLinks(this, Linkify.WEB_URLS) // clickable website address
                    setLinkTextColor(ContextCompat.getColor(requireContext(), R.color.primary))
                }
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    .apply { topMargin = (2 * dp).toInt() }
            })
        }

        // ---- About ----
        val versionName = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        } catch (e: Exception) {
            null
        } ?: "2.3.0"
        view.findViewById<TextView>(R.id.tvAppVersion).text = "Version: $versionName"
        view.findViewById<View>(R.id.tvAboutWebsite).setOnClickListener { openLink(Faq.WEBSITE) }
        view.findViewById<View>(R.id.tvAboutGitHub).setOnClickListener { openLink(Faq.GITHUB) }

        // ---- Preferences ----
        // The listeners are attached after the saved state is shown, so setting it does not trigger them.
        val switchDark = view.findViewById<MaterialSwitch>(R.id.switchDarkMode)
        switchDark.isChecked = prefs.darkMode
        switchDark.setOnCheckedChangeListener { _, on ->
            prefs.darkMode = on          // saved, so it is still applied after a restart
            AcadexApp.applyTheme(on)     // recreates the screen in the new theme
        }

        val switchMap = view.findViewById<MaterialSwitch>(R.id.switchCampusMap)
        switchMap.isChecked = prefs.showCampusMap
        switchMap.setOnCheckedChangeListener { _, on ->
            prefs.showCampusMap = on
            (requireActivity() as? MainActivity)?.updateNavItems()
        }

        // ---- Website ----
        view.findViewById<TextView>(R.id.tvWebsite).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://acadex-download.vercel.app")))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "No app found to open the link", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- Semester archive ----
        view.findViewById<View>(R.id.btnArchiveSemester).setOnClickListener {
            showArchiveDialog(repo, ::updateProfileHeader)
        }
        view.findViewById<View>(R.id.btnArchivedSemesters).setOnClickListener {
            reopenManageData = true
            go(R.id.archivedSemestersFragment)
        }

        // ---- Contact developer ----
        view.findViewById<TextView>(R.id.tvGitHub).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kalabasaa")))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), "No app found to open the link", Toast.LENGTH_SHORT).show()
            }
        }

        // ---- Profile ----
        val etName = view.findViewById<EditText>(R.id.etUserName)
        etName.setText(prefs.userName)
        view.findViewById<View>(R.id.btnSaveName).setOnClickListener {
            val newName = etName.text.toString().trim()
            prefs.userName = newName
            etName.clearFocus()
            updateProfileHeader()
            toast("Name saved")
        }

        // ---- AI schedule import ----
        val etSql = view.findViewById<EditText>(R.id.etSql)
        val tvResult = view.findViewById<TextView>(R.id.tvImportResult)
        val btnCopyError = view.findViewById<View>(R.id.btnCopyError)
        var lastError = ""

        fun show(ok: Boolean, text: String) {
            tvResult.visibility = View.VISIBLE
            tvResult.text = text
            tvResult.setTextColor(ContextCompat.getColor(requireContext(), if (ok) R.color.success else R.color.danger))
            btnCopyError.visibility = if (ok) View.GONE else View.VISIBLE
            lastError = if (ok) "" else text
        }

        // commit = false -> only test, nothing is saved
        fun runImport(commit: Boolean) {
            val parsed = SqlGuard.parse(etSql.text.toString())
            if (parsed.error != null) {
                show(false, parsed.error)
                return
            }
            val out = repo.applyImport(parsed.statements, commit)
            if (!out.ok) {
                show(false, out.message)
                return
            }
            val summary = "${out.subjects} subject(s), ${out.classes} class(es), ${out.activities} task(s)"
            if (commit) {
                show(true, "Imported: $summary.")
                etSql.setText("")
                toast("Import complete")
            } else {
                show(true, "Looks good. Tapping Import will add $summary.")
            }
        }

        view.findViewById<View>(R.id.btnCopyPrompt).setOnClickListener {
            copyToClipboard("Acadex AI prompt", AiImport.PROMPT)
            toast("Prompt copied. Paste it into your AI app with a photo of your schedule.")
        }
        view.findViewById<View>(R.id.btnPaste).setOnClickListener {
            val text = clipboardText()
            if (text.isNullOrBlank()) toast("Clipboard is empty") else etSql.setText(text)
        }
        view.findViewById<View>(R.id.btnCheck).setOnClickListener { runImport(false) }
        view.findViewById<View>(R.id.btnImport).setOnClickListener { runImport(true) }
        btnCopyError.setOnClickListener {
            copyToClipboard(
                "Acadex import error",
                "The SQL you gave me failed the check in my app.\n\nError:\n$lastError\n\n" +
                        "Please fix it and reply with the full corrected SQL only (INSERT statements, same rules as before)."
            )
            toast("Copied. Send it to the AI.")
        }

        // ---- Danger zone ----
        view.findViewById<View>(R.id.btnDeleteSchedules).setOnClickListener {
            confirm(
                "This permanently deletes ALL subjects, ALL class schedules and ALL tasks, " +
                        "plus the reminders attached to them.\n\nThis cannot be undone.",
                yes = "Delete everything",
                title = "Delete all schedules?"
            ) {
                repo.deleteAllSchedules()
                toast("All schedules, subjects and tasks deleted")
            }
        }
        view.findViewById<View>(R.id.btnDeleteTasks).setOnClickListener {
            confirm(
                "This permanently deletes ALL tasks (assignments, projects, quizzes and exams) " +
                        "and the reminders attached to them.\n\nYour subjects and class schedules are NOT deleted. " +
                        "This cannot be undone.",
                yes = "Delete tasks",
                title = "Delete all tasks?"
            ) {
                repo.deleteAllTasks()
                toast("All tasks deleted")
            }
        }
    }

    /** Step 1: pick the academic year and semester. Step 2: confirm. Step 3: archive. */
    private fun showArchiveDialog(repo: Repo, onDone: () -> Unit) {
        val ctx = requireContext()
        val content = layoutInflater.inflate(R.layout.dialog_archive_semester, null)
        val tilYear = content.findViewById<TextInputLayout>(R.id.tilYear)
        val tilSemester = content.findViewById<TextInputLayout>(R.id.tilSemester)
        val actYear = content.findViewById<AutoCompleteTextView>(R.id.actYear)
        val actSemester = content.findViewById<AutoCompleteTextView>(R.id.actSemester)
        val tvError = content.findViewById<TextView>(R.id.tvArchiveError)

        // nothing is pre-selected: the user has to say which semester this is
        actYear.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, academicYears()))
        actSemester.setAdapter(ArrayAdapter(ctx, android.R.layout.simple_list_item_1, Semesters.all))
        actYear.setOnItemClickListener { _, _, _, _ -> tilYear.error = null; tvError.visibility = View.GONE }
        actSemester.setOnItemClickListener { _, _, _, _ -> tilSemester.error = null; tvError.visibility = View.GONE }

        val dialog = MaterialAlertDialogBuilder(ctx)
            .setTitle("Archive semester")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Continue", null) // set below, so the dialog stays open on errors
            .create()

        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val year = actYear.text.toString().trim()
                val semester = actSemester.text.toString().trim()
                tilYear.error = if (year.isEmpty()) "Select an academic year" else null
                tilSemester.error = if (semester.isEmpty()) "Select a semester" else null
                if (year.isEmpty() || semester.isEmpty()) return@setOnClickListener

                if (repo.archiveExists(year, semester)) {
                    tvError.text = "$year, $semester is already archived. Restore it first, or choose another semester."
                    tvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                val (subjects, classes, tasks) = repo.activeCounts()
                if (subjects == 0 && classes == 0 && tasks == 0) {
                    tvError.text = "There is nothing to archive. Add subjects, schedules or tasks first."
                    tvError.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                dialog.dismiss()
                confirm(
                    "Academic year: $year\nSemester: $semester\n\n" +
                        "All associated schedules, subjects and tasks will be archived: " +
                        "$subjects subject(s), $classes class schedule(s) and $tasks task(s), plus their reminders.\n\n" +
                        "They disappear from your active pages but are NOT deleted. " +
                        "You can restore them any time from Manage Data > Archived semesters.",
                    yes = "Archive",
                    title = "Archive this semester?"
                ) {
                    val out = repo.archiveSemester(year, semester)
                    if (out.ok) {
                        toast("Archived $year, $semester")
                        onDone()
                    } else {
                        MaterialAlertDialogBuilder(ctx)
                            .setTitle("Could not archive")
                            .setMessage(out.message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }
        }
        dialog.show()
    }

    /** "2027–2028", "2026–2027", ... The user picks one; none is chosen for them. */
    private fun academicYears(): List<String> {
        val thisYear = Calendar.getInstance().get(Calendar.YEAR)
        return (thisYear + 1 downTo thisYear - 6).map { "$it\u2013${it + 1}" }
    }

    private companion object {
        /** Set when the user opens the archive pages, so Back lands on Manage Data again. */
        var reopenManageData = false
    }
}
