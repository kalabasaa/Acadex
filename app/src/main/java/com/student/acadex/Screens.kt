package com.student.acadex

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CalendarView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.PopupMenu
import android.widget.Spinner
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val WEEK = arrayOf("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private var rotateRunnable: Runnable? = null
    private var bannerHost: View? = null
    private var dotViews: List<View> = emptyList()
    private var bannerCount = 0

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val now = Calendar.getInstance()
        val today = WEEK[now.get(Calendar.DAY_OF_WEEK) - 1]
        val vpBanner = view.findViewById<androidx.viewpager2.widget.ViewPager2>(R.id.vpBanner)
        val dots = view.findViewById<LinearLayout>(R.id.bannerDots)
        bannerHost = view

        // Registered once; setupBanners() below may run more than once (e.g. after the
        // welcome-name dialog) and just refreshes bannerCount/dotViews/the adapter.
        val runnable = Runnable {
            if (bannerCount > 1) vpBanner.setCurrentItem((vpBanner.currentItem + 1) % bannerCount, true)
        }
        rotateRunnable = runnable
        vpBanner.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                dotViews.forEachIndexed { i, dot -> dot.alpha = if (i == position) 1f else 0.3f }
            }
            override fun onPageScrollStateChanged(state: Int) {
                view.removeCallbacks(runnable)
                if (state == androidx.viewpager2.widget.ViewPager2.SCROLL_STATE_IDLE) {
                    view.postDelayed(runnable, 5000)
                }
            }
        })

        var lastAnnouncements: List<Announcement> = emptyList()

        fun showAnnouncementModal(b: HomeBanner) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(b.title)
                .setMessage(b.message)
                .setPositiveButton("Close", null)
                .show()
        }

        fun showScheduleModal() {
            val classes = repo.classesOn(today)
            val scroll = android.widget.ScrollView(requireContext())
            val list = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
            scroll.addView(list)
            if (classes.isEmpty()) {
                list.addView(TextView(requireContext()).apply {
                    text = "No classes today! It's a perfect day to relax or do something productive."
                    val p = (16 * resources.displayMetrics.density).toInt()
                    setPadding(p, p, p, p)
                })
            } else {
                list.fill(classes, R.layout.item_row) { v, c ->
                    v.bindRow("${c.start.timeLabel() ?: c.start}\n${c.end.timeLabel() ?: c.end}", c.subjectName, c.where()) {}
                }
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Today's Schedule")
                .setView(scroll)
                .setPositiveButton("Close", null)
                .show()
        }

        fun setupBanners() {
            val name = Prefs(requireContext()).userName
            val banners = buildHomeBanners(repo, name, today, now, lastAnnouncements)
            bannerCount = banners.size
            vpBanner.adapter = BannerAdapter(banners) { b ->
                when (b.kind) {
                    BannerKind.ANNOUNCEMENT -> showAnnouncementModal(b)
                    BannerKind.SCHEDULE -> showScheduleModal()
                    else -> {}
                }
            }

            dots.removeAllViews()
            val dotSize = (8 * resources.displayMetrics.density).toInt()
            val dotGap = (4 * resources.displayMetrics.density).toInt()
            dotViews = banners.indices.map {
                View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                        marginStart = dotGap
                        marginEnd = dotGap
                    }
                    setBackgroundResource(R.drawable.bg_circle_selected)
                    alpha = if (it == 0) 1f else 0.3f
                }
            }
            dotViews.forEach { dots.addView(it) }

            view.removeCallbacks(runnable)
            view.postDelayed(runnable, 5000)
        }
        setupBanners()

        view.findViewById<View>(R.id.cardNotes).setOnClickListener { go(R.id.notesFragment) }
        view.findViewById<View>(R.id.cardFocusTimer).setOnClickListener { go(R.id.focusTimerFragment) }
        view.findViewById<View>(R.id.cardGpa).setOnClickListener { go(R.id.gpaFragment) }
        view.findViewById<View>(R.id.cardQr).setOnClickListener { go(R.id.shareQrFragment) }

        val prefs = Prefs(requireContext())
        if (prefs.userName.isBlank()) {
            val input = EditText(requireContext()).apply {
                hint = "Enter your name"
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            val container = FrameLayout(requireContext()).apply {
                val p = (16 * resources.displayMetrics.density).toInt()
                setPadding(p, p / 2, p, p / 2)
                addView(input)
            }
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Welcome to Acadex!")
                .setMessage("What is your name?")
                .setView(container)
                .setCancelable(false)
                .setPositiveButton("Get Started") { _, _ ->
                    val entered = input.text.toString().trim()
                    if (entered.isNotEmpty()) {
                        prefs.userName = entered
                        setupBanners()
                    }
                }
                .show()
        }

        view.findViewById<View>(R.id.ivNotification).setOnClickListener {
            go(R.id.remindersFragment)
        }
        // Announcements now live in the banner carousel (one banner per announcement) instead
        // of a separate static list; rebuild the banners whenever the cached/refreshed set arrives.
        loadAnnouncements { items ->
            lastAnnouncements = items
            setupBanners()
        }

        view.findViewById<LinearLayout>(R.id.subjectList).fill(repo.subjects(), R.layout.item_row) { v, s ->
            val info = listOfNotNull(s.code, s.professor?.takeIf { it.isNotBlank() }).joinToString(" · ")
            v.bindRow(null, s.name, info) { go(R.id.subjectDetailFragment, id = s.id) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rotateRunnable?.let { bannerHost?.removeCallbacks(it) }
        rotateRunnable = null
        bannerHost = null
    }
}

class SubjectDetailFragment : Fragment(R.layout.fragment_subject_detail) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        view.findViewById<View>(R.id.btnBack).setOnClickListener {
            go(R.id.dashboardFragment)
        }
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
        val taskList = view.findViewById<LinearLayout>(R.id.calendarList)
        val classList = view.findViewById<LinearLayout>(R.id.classList)
        val classTitle = view.findViewById<TextView>(R.id.tvClassTitle)
        val title = view.findViewById<TextView>(R.id.tvListTitle)

        val gridDays = view.findViewById<GridLayout>(R.id.gridDays)
        val tvMonthTitle = view.findViewById<TextView>(R.id.tvMonthTitle)
        val btnPrevMonth = view.findViewById<View>(R.id.btnPrevMonth)
        val btnNextMonth = view.findViewById<View>(R.id.btnNextMonth)

        val currentMonth = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
        val now = Calendar.getInstance()
        var selectedDate: String? = String.format(Locale.US, "%04d-%02d-%02d", now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH))
        var selectedDay: String? = WEEK[now.get(Calendar.DAY_OF_WEEK) - 1]

        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        lateinit var refresh: () -> Unit

        fun renderGrid() {
            gridDays.removeAllViews()
            tvMonthTitle.text = SimpleDateFormat("MMMM yyyy", Locale.US).format(currentMonth.time)

            // Java Calendar: SUN=1, MON=2, TUE=3, WED=4, THU=5, FRI=6, SAT=7
            // We align to: MON=0, TUE=1, WED=2, THU=3, FRI=4, SAT=5, SUN=6
            val firstDayOfWeek = currentMonth.get(Calendar.DAY_OF_WEEK)
            val pad = if (firstDayOfWeek == Calendar.SUNDAY) 6 else firstDayOfWeek - Calendar.MONDAY

            repeat(pad) {
                val empty = View(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f),
                        GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    ).apply { width = 0; height = dp(42) }
                }
                gridDays.addView(empty)
            }

            val year = currentMonth.get(Calendar.YEAR)
            val month = currentMonth.get(Calendar.MONTH)
            val maxDays = currentMonth.getActualMaximum(Calendar.DAY_OF_MONTH)

            // Tasks due dates map for this month
            val tasksInMonth = repo.activities()

            for (d in 1..maxDays) {
                val dateStr = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, d)
                val dayCal = Calendar.getInstance().apply { set(year, month, d) }
                val dayOfWeekStr = WEEK[dayCal.get(Calendar.DAY_OF_WEEK) - 1]
                val isSelected = (selectedDate == dateStr)
                val hasTaskDeadline = tasksInMonth.any { it.dueDate?.take(10) == dateStr }

                val cell = FrameLayout(requireContext()).apply {
                    layoutParams = GridLayout.LayoutParams(
                        GridLayout.spec(GridLayout.UNDEFINED, 1f),
                        GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    ).apply { width = 0; height = dp(42) }

                    val tv = TextView(requireContext()).apply {
                        text = d.toString()
                        textSize = 14f
                        gravity = Gravity.CENTER
                        if (isSelected) {
                            setBackgroundResource(R.drawable.bg_circle_selected)
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                        } else {
                            setTextColor(ContextCompat.getColor(requireContext(), R.color.dark))
                        }
                        layoutParams = FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER)
                    }
                    addView(tv)

                    // Blue underline indicator for task deadline
                    if (hasTaskDeadline) {
                        val underline = View(requireContext()).apply {
                            setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary))
                            val params = FrameLayout.LayoutParams(dp(18), dp(2), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
                            params.bottomMargin = dp(4)
                            layoutParams = params
                        }
                        addView(underline)
                    }

                    setOnClickListener {
                        selectedDate = dateStr
                        selectedDay = dayOfWeekStr
                        refresh()
                    }
                }
                gridDays.addView(cell)
            }
        }

        refresh = {
            renderGrid()
            val date = selectedDate
            val day = selectedDay

            // Classes that meet on the picked weekday
            val showClasses = day != null
            classTitle.visibility = if (showClasses) View.VISIBLE else View.GONE
            classList.visibility = if (showClasses) View.VISIBLE else View.GONE
            if (day != null) {
                classTitle.text = "Classes · $day"
                classList.fill(repo.classesOn(day), R.layout.item_row) { v, c ->
                    v.bindRow("${c.start}\n${c.end}", c.subjectName, c.where()) { go(R.id.addScheduleFragment, id = c.id) }
                }
            }

            // Tasks due that date (or all tasks)
            title.text = if (date == null) "All tasks" else "Tasks due ${date.shortDate()}"
            taskList.fill(repo.activities(date = date), R.layout.item_task) { v, a ->
                v.bindTask(a) { go(R.id.taskDetailFragment, id = a.id) }
            }
        }

        btnPrevMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, -1)
            refresh()
        }
        btnNextMonth.setOnClickListener {
            currentMonth.add(Calendar.MONTH, 1)
            refresh()
        }
        refresh()
    }
}

class TaskDetailFragment : Fragment(R.layout.fragment_task_detail) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        view.findViewById<View>(R.id.btnBack).setOnClickListener { back() }
        view.findViewById<View>(R.id.btnEdit).setOnClickListener { go(R.id.addActivityFragment, id = argId) }

        fun bind() {
            val a = repo.activity(argId)
            if (a == null) {
                view.post { back() }
                return
            }
            view.findViewById<TextView>(R.id.tvTaskTitle).text = a.title
            view.findViewById<TextView>(R.id.tvTaskType).text = a.type
            view.findViewById<TextView>(R.id.tvTaskSubject).text =
                if (a.subjectName == null) "No subject"
                else listOfNotNull(a.subjectName, a.subjectCode?.takeIf { it.isNotBlank() }).joinToString(" · ")

            val day = a.dueDate.dateOnly()?.let {
                try {
                    SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                        .format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)!!)
                } catch (e: Exception) {
                    null
                }
            }
            view.findViewById<TextView>(R.id.tvTaskDeadline).text =
                if (day == null) "No deadline" else day + (a.dueDate.timeOnly().timeLabel()?.let { " · $it" } ?: "")

            val desc = a.description?.trim().orEmpty()
            view.findViewById<TextView>(R.id.tvTaskDesc).text = desc.ifEmpty { "No description" }

            view.findViewById<TextView>(R.id.tvTaskStatus).apply {
                text = a.status.statusLabel()
                setTextColor(context.statusColor(a.status))
            }
            view.findViewById<View>(R.id.btnChangeStatus).setOnClickListener { v ->
                val popup = PopupMenu(v.context, v)
                val options = resources.getStringArray(R.array.statuses)
                options.forEachIndexed { i, o -> popup.menu.add(0, i, i, o.statusLabel()) }
                popup.setOnMenuItemClickListener { item ->
                    repo.setActivityStatus(a.id, options[item.itemId])
                    bind()
                    true
                }
                popup.show()
            }
        }
        bind()
    }
}

class RemindersFragment : Fragment(R.layout.fragment_reminders) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val repo = Repo(requireContext())
        val name = Prefs(requireContext()).userName
        val nameText = if (name.isBlank()) "Student" else name
        view.findViewById<TextView>(R.id.tvGreeting).text = "Hello, welcome to Acadex, $nameText!"

        // Section 1: Tomorrow's Schedule
        val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowDay = WEEK[tomorrowCal.get(Calendar.DAY_OF_WEEK) - 1]
        val tomorrowClasses = repo.classesOn(tomorrowDay)

        view.findViewById<LinearLayout>(R.id.tomorrowList).fill(tomorrowClasses, R.layout.item_text_reminder) { v, c ->
            v.findViewById<TextView>(R.id.tvTitle).text = "${c.subjectName} (${c.start} - ${c.end})"
            v.findViewById<TextView>(R.id.tvSub).text = c.where()
            v.setOnClickListener { go(R.id.addScheduleFragment, id = c.id) }
        }

        // Section 2: Tasks to Be Finished (text reminder format)
        val unfinishedTasks = repo.activities().filter { it.status != "Completed" && it.status != "Finished" }
        view.findViewById<LinearLayout>(R.id.taskList).fill(unfinishedTasks, R.layout.item_text_reminder) { v, a ->
            v.findViewById<TextView>(R.id.tvTitle).text = a.title
            v.findViewById<TextView>(R.id.tvSub).text = "Due: ${a.dueDate.shortDate()} · Type: ${a.type} · Status: ${a.status}"
            v.setOnClickListener { go(R.id.addActivityFragment, id = a.id) }
        }

        // Section 3: Online Announcements (from Supabase; empty state when there are none)
        val annList = view.findViewById<LinearLayout>(R.id.announcementList)
        loadAnnouncements { items ->
            annList.fill(items, R.layout.item_text_reminder) { v, a ->
                v.findViewById<TextView>(R.id.tvTitle).text = a.title
                v.findViewById<TextView>(R.id.tvSub).apply {
                    text = if (a.date.isEmpty()) a.body else "${a.date} · ${a.body}"
                    maxLines = 2
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
                v.setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle(a.title)
                        .setMessage(a.body)
                        .setPositiveButton("Close", null)
                        .show()
                }
            }
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

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { back() }

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

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { back() }

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
        view.findViewById<View>(R.id.btnCancel).setOnClickListener { back() }

        val spSubject = view.findViewById<Spinner>(R.id.spSubject)
        val spType = view.findViewById<Spinner>(R.id.spType)
        val spStatus = view.findViewById<Spinner>(R.id.spStatus)
        val title = view.findViewById<EditText>(R.id.etTitle)
        val due = view.findViewById<EditText>(R.id.etDue)
        val dueTime = view.findViewById<EditText>(R.id.etDueTime)
        val desc = view.findViewById<EditText>(R.id.etDesc)
        val delete = view.findViewById<View>(R.id.btnDelete)
        val types = resources.getStringArray(R.array.activity_types)
        val statuses = resources.getStringArray(R.array.statuses)

        spSubject.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item,
            listOf("No subject") + subjects.map { it.name }
        )
        due.pickDate()
        dueTime.pickTime()

        val existing = repo.activity(argId)
        val preset = existing?.subjectId ?: argSubjectId
        spSubject.setSelection(subjects.indexOfFirst { it.id == preset } + 1)

        if (existing != null) {
            view.findViewById<TextView>(R.id.tvFormTitle).text = "Edit activity"
            title.setText(existing.title)
            spType.setSelection(types.indexOf(existing.type).coerceAtLeast(0))
            spStatus.setSelection(statuses.indexOf(if (existing.status == "Finished") "Completed" else existing.status).coerceAtLeast(0))
            due.setText(existing.dueDate.dateOnly())
            dueTime.setText(existing.dueDate.timeOnly())
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
            val dueDay = due.text.toString()
            val dueClock = dueTime.text.toString()
            if (dueClock.isNotEmpty() && dueDay.isEmpty()) {
                toast("Pick a due date for that time")
                return@setOnClickListener
            }
            val pos = spSubject.selectedItemPosition
            val item = ActivityItem(
                id = existing?.id ?: 0,
                subjectId = if (pos == 0) null else subjects[pos - 1].id,
                title = t,
                type = spType.selectedItem.toString(),
                dueDate = when {
                    dueDay.isEmpty() -> null
                    dueClock.isEmpty() -> dueDay
                    else -> "$dueDay $dueClock"
                },
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

        view.findViewById<View>(R.id.btnCancel).setOnClickListener { back() }

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
