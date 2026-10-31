package com.student.acadex

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Bundle
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

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

        view.findViewById<TextView>(R.id.tvTermsText).text = TermsAndConditions.TEXT.trimIndent()

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
}