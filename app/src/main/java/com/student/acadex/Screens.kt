package com.student.acadex

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CalendarView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.materialswitch.MaterialSwitch
import java.util.Calendar
import java.util.Locale

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val today = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
        view.findViewById<TextView>(R.id.tvToday).text = "Today's schedule · $today"

        view.findViewById<LinearLayout>(R.id.todayList).fill(repo.classesOn(today), R.layout.item_row) { v, c ->
            v.bindRow("${c.start}\n${c.end}", c.subjectName, c.where()) { go(R.id.addScheduleFragment, id = c.id) }
        }

        view.findViewById<LinearLayout>(R.id.subjectList).fill(repo.subjects(), R.layout.item_row) { v, s ->
            val info = listOfNotNull(s.code, s.professor?.takeIf { it.isNotBlank() }).joinToString(" · ")
            v.bindRow(null, s.name, info) { go(R.id.subjectDetailFragment, id = s.id) }
        }
    }
}

class SubjectDetailFragment : Fragment(R.layout.fragment_subject_detail) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val s = repo.subject(argId) ?: run {
            back()
            return
        }

        view.findViewById<TextView>(R.id.tvName).text = s.name
        view.findViewById<TextView>(R.id.tvInfo).text =
            listOfNotNull(s.code, s.professor?.takeIf { it.isNotBlank() }).joinToString(" · ")

        view.findViewById<View>(R.id.btnEditSubject).setOnClickListener { go(R.id.addSubjectFragment, id = s.id) }
        view.findViewById<View>(R.id.btnDeleteSubject).setOnClickListener {
            confirm("Delete ${s.name}? Its schedule will be removed too.") {
                repo.deleteSubject(s.id)
                back()
            }
        }
        view.findViewById<View>(R.id.btnAddSchedule).setOnClickListener { go(R.id.addScheduleFragment, subjectId = s.id) }
        view.findViewById<View>(R.id.btnAddActivity).setOnClickListener { go(R.id.addActivityFragment, subjectId = s.id) }

        view.findViewById<LinearLayout>(R.id.scheduleList).fill(repo.classesFor(s.id), R.layout.item_row) { v, c ->
            v.bindRow(c.day, "${c.start} – ${c.end}", c.where()) { go(R.id.addScheduleFragment, id = c.id) }
        }

        view.findViewById<LinearLayout>(R.id.activityList).fill(repo.activities(subjectId = s.id), R.layout.item_row) { v, a ->
            v.bindRow(a.dueDate.shortDate(), a.title, a.type, a.status) { go(R.id.addActivityFragment, id = a.id) }
        }
    }
}

class CalendarFragment : Fragment(R.layout.fragment_calendar) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val list = view.findViewById<LinearLayout>(R.id.calendarList)
        val title = view.findViewById<TextView>(R.id.tvListTitle)

        fun show(date: String?) {
            title.text = if (date == null) "All activities" else "Due $date"
            list.fill(repo.activities(date = date), R.layout.item_row) { v, a ->
                val info = listOfNotNull(a.subjectName, a.type).joinToString(" · ")
                v.bindRow(a.dueDate.shortDate(), a.title, info, a.status) { go(R.id.addActivityFragment, id = a.id) }
            }
        }

        view.findViewById<CalendarView>(R.id.calendar).setOnDateChangeListener { _, y, m, d ->
            show(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d))
        }
        view.findViewById<View>(R.id.btnShowAll).setOnClickListener { show(null) }
        show(null)
    }
}

class RemindersFragment : Fragment(R.layout.fragment_reminders) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        view.findViewById<View>(R.id.btnAddReminder).setOnClickListener { go(R.id.addReminderFragment) }

        view.findViewById<LinearLayout>(R.id.reminderList).fill(repo.reminders(), R.layout.item_reminder) { v, r ->
            v.findViewById<TextView>(R.id.tvTitle).text = r.title
            v.findViewById<TextView>(R.id.tvSub).text = r.time.fmt()
            v.findViewById<MaterialSwitch>(R.id.swEnabled).apply {
                isChecked = r.enabled
                setOnCheckedChangeListener { _, on -> repo.setReminderEnabled(r.id, on) }
            }
            v.setOnClickListener { go(R.id.addReminderFragment, id = r.id) }
        }
    }
}

class AddSubjectFragment : Fragment(R.layout.fragment_add_subject) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val name = view.findViewById<EditText>(R.id.etSubjectName)
        val code = view.findViewById<EditText>(R.id.etSubjectCode)
        val prof = view.findViewById<EditText>(R.id.etProfessor)
        val delete = view.findViewById<View>(R.id.btnDelete)
        val existing = repo.subject(argId)

        if (existing != null) {
            view.findViewById<TextView>(R.id.tvFormTitle).text = "Edit subject"
            name.setText(existing.name)
            code.setText(existing.code)
            prof.setText(existing.professor)
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                confirm("Delete ${existing.name}? Its schedule will be removed too.") {
                    repo.deleteSubject(existing.id)
                    back()
                }
            }
        }

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val n = name.text.toString().trim()
            val c = code.text.toString().trim()
            if (n.isEmpty() || c.isEmpty()) {
                toast("Name and code are required")
                return@setOnClickListener
            }
            val saved = repo.saveSubject(Subject(existing?.id ?: 0, n, c, prof.text.toString().trim().ifEmpty { null }))
            if (saved) back() else toast("Subject code already exists")
        }
    }
}

class AddScheduleFragment : Fragment(R.layout.fragment_add_schedule) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val subjects = repo.subjects()
        if (subjects.isEmpty()) {
            toast("Add a subject first")
            back()
            return
        }

        val spSubject = view.findViewById<Spinner>(R.id.spSubject)
        val spDay = view.findViewById<Spinner>(R.id.spDay)
        val start = view.findViewById<EditText>(R.id.etStart)
        val end = view.findViewById<EditText>(R.id.etEnd)
        val room = view.findViewById<EditText>(R.id.etRoom)
        val online = view.findViewById<EditText>(R.id.etOnline)
        val tilRoom = view.findViewById<View>(R.id.tilRoom)
        val tilOnline = view.findViewById<View>(R.id.tilOnline)
        val rgType = view.findViewById<RadioGroup>(R.id.rgClassType)
        val rbOnline = view.findViewById<RadioButton>(R.id.rbOnline)
        val rbOnsite = view.findViewById<RadioButton>(R.id.rbOnsite)
        val delete = view.findViewById<View>(R.id.btnDelete)
        val days = resources.getStringArray(R.array.days)

        spSubject.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, subjects.map { it.name })
        start.pickTime()
        end.pickTime()

        fun sync() {
            val on = rbOnline.isChecked
            tilOnline.visibility = if (on) View.VISIBLE else View.GONE
            tilRoom.visibility = if (on) View.GONE else View.VISIBLE
        }
        rgType.setOnCheckedChangeListener { _, _ -> sync() }

        val existing = repo.classItem(argId)
        val preset = existing?.subjectId ?: argSubjectId
        spSubject.setSelection(subjects.indexOfFirst { it.id == preset }.coerceAtLeast(0))

        if (existing != null) {
            view.findViewById<TextView>(R.id.tvFormTitle).text = "Edit schedule"
            spDay.setSelection(days.indexOf(existing.day).coerceAtLeast(0))
            start.setText(existing.start)
            end.setText(existing.end)
            if (existing.type == "Online") rbOnline.isChecked = true else rbOnsite.isChecked = true
            room.setText(existing.room)
            online.setText(existing.online)
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                confirm("Delete this schedule?") {
                    repo.deleteClass(existing.id)
                    back()
                }
            }
        }
        sync()

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val isOnline = rbOnline.isChecked
            val s = start.text.toString()
            val e = end.text.toString()
            val r = room.text.toString().trim()
            val o = online.text.toString().trim()
            when {
                s.isEmpty() || e.isEmpty() -> toast("Pick a start and end time")
                s >= e -> toast("End time must be after start time")
                isOnline && o.isEmpty() -> toast("Add the online details")
                !isOnline && r.isEmpty() -> toast("Add a room")
                else -> {
                    val item = ClassItem(
                        id = existing?.id ?: 0,
                        subjectId = subjects[spSubject.selectedItemPosition].id,
                        day = spDay.selectedItem.toString(),
                        start = s,
                        end = e,
                        type = if (isOnline) "Online" else "Onsite",
                        room = if (isOnline) null else r,
                        online = if (isOnline) o else null
                    )
                    if (repo.saveClass(item)) back() else toast("Could not save")
                }
            }
        }
    }
}

class AddActivityFragment : Fragment(R.layout.fragment_add_activity) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val subjects = repo.subjects()
        val spSubject = view.findViewById<Spinner>(R.id.spSubject)
        val spType = view.findViewById<Spinner>(R.id.spType)
        val spStatus = view.findViewById<Spinner>(R.id.spStatus)
        val title = view.findViewById<EditText>(R.id.etTitle)
        val due = view.findViewById<EditText>(R.id.etDue)
        val desc = view.findViewById<EditText>(R.id.etDesc)
        val delete = view.findViewById<View>(R.id.btnDelete)
        val types = resources.getStringArray(R.array.activity_types)
        val statuses = resources.getStringArray(R.array.statuses)

        spSubject.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf("No subject") + subjects.map { it.name }
        )
        due.pickDate()

        val existing = repo.activity(argId)
        val preset = existing?.subjectId ?: argSubjectId
        spSubject.setSelection(subjects.indexOfFirst { it.id == preset } + 1)

        if (existing != null) {
            view.findViewById<TextView>(R.id.tvFormTitle).text = "Edit activity"
            title.setText(existing.title)
            spType.setSelection(types.indexOf(existing.type).coerceAtLeast(0))
            spStatus.setSelection(statuses.indexOf(existing.status).coerceAtLeast(0))
            due.setText(existing.dueDate)
            desc.setText(existing.description)
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                confirm("Delete ${existing.title}?") {
                    repo.deleteActivity(existing.id)
                    back()
                }
            }
        }

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val t = title.text.toString().trim()
            if (t.isEmpty()) {
                toast("Title is required")
                return@setOnClickListener
            }
            val pos = spSubject.selectedItemPosition
            val item = ActivityItem(
                id = existing?.id ?: 0,
                subjectId = if (pos == 0) null else subjects[pos - 1].id,
                title = t,
                type = spType.selectedItem.toString(),
                dueDate = due.text.toString().ifEmpty { null },
                description = desc.text.toString().trim().ifEmpty { null },
                status = spStatus.selectedItem.toString()
            )
            if (repo.saveActivity(item)) back() else toast("Could not save")
        }
    }
}

private class Target(val label: String, val classId: Long?, val activityId: Long?)

class AddReminderFragment : Fragment(R.layout.fragment_add_reminder) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val targets = repo.classesFor().map { Target("Class · ${it.subjectName} ${it.day} ${it.start}", it.id, null) } +
            repo.activities().map { Target("Activity · ${it.title}", null, it.id) }
        if (targets.isEmpty()) {
            toast("Add a class or activity first")
            back()
            return
        }

        val spTarget = view.findViewById<Spinner>(R.id.spTarget)
        val title = view.findViewById<EditText>(R.id.etTitle)
        val date = view.findViewById<EditText>(R.id.etDate)
        val time = view.findViewById<EditText>(R.id.etTime)
        val delete = view.findViewById<View>(R.id.btnDelete)

        spTarget.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, targets.map { it.label })
        date.pickDate()
        time.pickTime()

        val existing = repo.reminder(argId)
        if (existing != null) {
            view.findViewById<TextView>(R.id.tvFormTitle).text = "Edit reminder"
            spTarget.setSelection(targets.indexOfFirst { it.classId == existing.classId && it.activityId == existing.activityId }.coerceAtLeast(0))
            title.setText(existing.title)
            val c = Calendar.getInstance().apply { timeInMillis = existing.time }
            date.setText(String.format(Locale.US, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH)))
            time.setText(String.format(Locale.US, "%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
            delete.visibility = View.VISIBLE
            delete.setOnClickListener {
                confirm("Delete this reminder?") {
                    repo.deleteReminder(existing.id)
                    back()
                }
            }
        }

        view.findViewById<View>(R.id.btnSave).setOnClickListener {
            val t = title.text.toString().trim()
            val d = date.text.toString().split("-")
            val h = time.text.toString().split(":")
            if (t.isEmpty() || d.size != 3 || h.size != 2) {
                toast("Title, date and time are required")
                return@setOnClickListener
            }
            val c = Calendar.getInstance().apply {
                set(d[0].toInt(), d[1].toInt() - 1, d[2].toInt(), h[0].toInt(), h[1].toInt(), 0)
                set(Calendar.MILLISECOND, 0)
            }
            val target = targets[spTarget.selectedItemPosition]
            val item = Reminder(existing?.id ?: 0, target.classId, target.activityId, t, c.timeInMillis, existing?.enabled ?: true)
            if (repo.saveReminder(item)) back() else toast("Could not save")
        }
    }
}
