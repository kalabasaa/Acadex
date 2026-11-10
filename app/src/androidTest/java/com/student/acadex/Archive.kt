package com.student.acadex

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Long.archiveDate(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(this))

private fun plural(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"

/** Settings > Manage Data > Archived semesters: every archived semester, grouped by academic year. */
class ArchivedSemestersFragment : Fragment(R.layout.fragment_archived_semesters) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btnBack).setOnClickListener { back() }

        val list = view.findViewById<LinearLayout>(R.id.archiveList)
        val empty = view.findViewById<View>(R.id.emptyState)
        val semesters = Repo(requireContext()).archivedSemesters()

        list.removeAllViews()
        empty.visibility = if (semesters.isEmpty()) View.VISIBLE else View.GONE
        if (semesters.isEmpty()) return

        val inflater = LayoutInflater.from(requireContext())
        val tint = ContextCompat.getColor(requireContext(), R.color.primary_soft)

        // already sorted: newest academic year first, then 1st, 2nd, Summer
        semesters.groupBy { it.academicYear }.forEach { (year, items) ->
            val header = inflater.inflate(R.layout.item_section, list, false)
            header.findViewById<TextView>(R.id.tvSection).text = "Academic year $year"
            list.addView(header)

            items.forEach { sem ->
                val row = inflater.inflate(R.layout.item_row, list, false)
                row.bindRow(
                    left = sem.semester.replace(" Semester", " Sem"),
                    title = sem.semester,
                    sub = "Archived ${sem.archivedAt.archiveDate()} · " +
                        "${plural(sem.subjects, "subject")} · ${plural(sem.classes, "class", "classes")} · ${plural(sem.activities, "task")}",
                    right = "Archived"
                ) { go(R.id.archivedSemesterDetailFragment, id = sem.id) }
                // soft blue card = archived, so it never looks like active data
                (row as MaterialCardView).setCardBackgroundColor(tint)
                list.addView(row)
            }
        }
    }
}

/** One archived semester: what is inside it (read-only) and the Restore Semester action. */
class ArchivedSemesterDetailFragment : Fragment(R.layout.fragment_archived_semester_detail) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        view.findViewById<View>(R.id.btnBack).setOnClickListener { back() }

        val sem = repo.archivedSemester(argId) ?: run {
            back()
            return
        }

        view.findViewById<TextView>(R.id.tvName).text = sem.semester
        view.findViewById<TextView>(R.id.tvInfo).text =
            "${sem.academicYear} · Archived ${sem.archivedAt.archiveDate()}"
        (view.findViewById<View>(R.id.cardBanner) as MaterialCardView)
            .setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_soft))

        val subjects = repo.archivedSubjects(sem.id)
        val classes = repo.archivedClasses(sem.id)
        val tasks = repo.archivedActivities(sem.id)

        view.findViewById<TextView>(R.id.tvSubjectsTitle).text = "Subjects (${subjects.size})"
        view.findViewById<TextView>(R.id.tvSchedulesTitle).text = "Class schedules (${classes.size})"
        view.findViewById<TextView>(R.id.tvTasksTitle).text = "Tasks and activities (${tasks.size})"

        // read-only: tapping does nothing, because archived records are not edited in place
        view.findViewById<LinearLayout>(R.id.subjectList).fill(subjects, R.layout.item_row) { v, s ->
            v.bindRow(s.code, s.name, s.professor?.takeIf { it.isNotBlank() }) {}
        }
        view.findViewById<LinearLayout>(R.id.scheduleList).fill(classes, R.layout.item_row) { v, c ->
            v.bindRow(c.day, c.subjectName, "${c.start} – ${c.end} · ${c.where()}") {}
        }
        view.findViewById<LinearLayout>(R.id.taskList).fill(tasks, R.layout.item_task) { v, a ->
            v.bindTask(a) {}
        }

        view.findViewById<View>(R.id.btnRestore).setOnClickListener {
            confirm(
                "Its ${plural(sem.subjects, "subject")}, ${plural(sem.classes, "class schedule")} and " +
                    "${plural(sem.activities, "task")} will return to your active Home, Calendar and Reminders pages.\n\n" +
                    "Nothing is duplicated: a subject with the same code, or a class or task that is already active, " +
                    "is reused instead of being added again.\n\n" +
                    "This semester is then removed from the archive list.",
                yes = "Restore",
                title = "Restore ${sem.academicYear}, ${sem.semester}?"
            ) {
                val out = repo.restoreSemester(sem.id)
                if (out.ok) {
                    val skipped = if (out.skipped > 0) " ${plural(out.skipped, "record")} already active, not duplicated." else ""
                    toast("Restored ${out.message}.$skipped")
                    back()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Could not restore")
                        .setMessage(out.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }
}
