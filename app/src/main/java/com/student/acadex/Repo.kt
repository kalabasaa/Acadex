package com.student.acadex

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteConstraintException

private var shared: DatabaseHelper? = null

private fun helperOf(context: Context): DatabaseHelper =
    shared ?: DatabaseHelper(context.applicationContext).also { shared = it }

/** Result of testing or running imported SQL. */
class ImportOutcome(
    val ok: Boolean,
    val message: String = "",
    val subjects: Int = 0,
    val classes: Int = 0,
    val activities: Int = 0
)

class Repo(context: Context) {
    private val helper = helperOf(context)
    private val db get() = helper.writableDatabase

    private val dayOrder =
        "CASE c.day WHEN 'MON' THEN 1 WHEN 'TUE' THEN 2 WHEN 'WED' THEN 3 WHEN 'THU' THEN 4 WHEN 'FRI' THEN 5 WHEN 'SAT' THEN 6 ELSE 7 END"
    private val classSql =
        "SELECT c.*, s.subject_name FROM classes c JOIN subjects s ON s.id = c.subject_id"
    private val activitySql =
        "SELECT a.*, s.subject_name, s.subject_code FROM activities a LEFT JOIN subjects s ON s.id = a.subject_id"

    private fun Cursor.lng(c: String): Long? =
        getColumnIndexOrThrow(c).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.str(c: String): String? =
        getColumnIndexOrThrow(c).let { if (isNull(it)) null else getString(it) }

    private fun <T> query(sql: String, args: List<String> = emptyList(), map: (Cursor) -> T): List<T> {
        val out = ArrayList<T>()
        db.rawQuery(sql, args.toTypedArray()).use { while (it.moveToNext()) out.add(map(it)) }
        return out
    }

    private fun write(table: String, id: Long, values: ContentValues): Boolean = try {
        if (id == 0L) db.insertOrThrow(table, null, values)
        else db.update(table, values, "id=?", arrayOf(id.toString()))
        true
    } catch (e: SQLiteConstraintException) {
        false
    }

    private fun remove(table: String, id: Long) {
        db.delete(table, "id=?", arrayOf(id.toString()))
    }

    private fun Cursor.toSubject() =
        Subject(lng("id")!!, str("subject_name")!!, str("subject_code")!!, str("professor_name"))

    private fun Cursor.toClass() = ClassItem(
        lng("id")!!, lng("subject_id")!!, str("day")!!, str("start_time")!!, str("end_time")!!,
        str("class_type")!!, str("room"), str("online_details"), str("subject_name") ?: ""
    )

    private fun Cursor.toActivity() = ActivityItem(
        lng("id")!!, lng("subject_id"), str("title")!!, str("type")!!, str("due_date"),
        str("description"), str("status")!!, str("subject_name"), str("subject_code")
    )

    private fun Cursor.toReminder() = Reminder(
        lng("id")!!, lng("class_id"), lng("activity_id"), str("title")!!,
        lng("reminder_time")!!, lng("is_enabled") == 1L
    )

    fun subjects() =
        query("SELECT * FROM subjects ORDER BY subject_name COLLATE NOCASE") { it.toSubject() }

    fun subject(id: Long) =
        query("SELECT * FROM subjects WHERE id=?", listOf(id.toString())) { it.toSubject() }.firstOrNull()

    fun saveSubject(s: Subject) = write(
        "subjects", s.id,
        ContentValues().apply {
            put("subject_name", s.name)
            put("subject_code", s.code)
            put("professor_name", s.professor)
        }
    )

    fun deleteSubject(id: Long) = remove("subjects", id)

    fun classesFor(subjectId: Long? = null) = query(
        classSql + (if (subjectId == null) "" else " WHERE c.subject_id=?") + " ORDER BY $dayOrder, c.start_time",
        listOfNotNull(subjectId?.toString())
    ) { it.toClass() }

    fun classesOn(day: String) =
        query("$classSql WHERE c.day=? ORDER BY c.start_time", listOf(day)) { it.toClass() }

    fun classItem(id: Long) =
        query("$classSql WHERE c.id=?", listOf(id.toString())) { it.toClass() }.firstOrNull()

    fun saveClass(c: ClassItem) = write(
        "classes", c.id,
        ContentValues().apply {
            put("subject_id", c.subjectId)
            put("day", c.day)
            put("start_time", c.start)
            put("end_time", c.end)
            put("class_type", c.type)
            put("room", c.room)
            put("online_details", c.online)
        }
    )

    fun deleteClass(id: Long) = remove("classes", id)

    fun activities(subjectId: Long? = null, date: String? = null): List<ActivityItem> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (subjectId != null) {
            where.add("a.subject_id=?")
            args.add(subjectId.toString())
        }
        if (date != null) {
            // due_date may be "yyyy-MM-dd" or "yyyy-MM-dd HH:mm"; compare the date part only
            where.add("substr(a.due_date,1,10)=?")
            args.add(date)
        }
        val sql = activitySql +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY a.due_date IS NULL, a.due_date, a.title"
        return query(sql, args) { it.toActivity() }
    }

    fun activity(id: Long) =
        query("$activitySql WHERE a.id=?", listOf(id.toString())) { it.toActivity() }.firstOrNull()

    fun saveActivity(a: ActivityItem) = write(
        "activities", a.id,
        ContentValues().apply {
            put("subject_id", a.subjectId)
            put("title", a.title)
            put("type", a.type)
            put("due_date", a.dueDate)
            put("description", a.description)
            put("status", a.status)
        }
    )

    fun deleteActivity(id: Long) = remove("activities", id)

    fun reminders() =
        query("SELECT * FROM reminders ORDER BY reminder_time") { it.toReminder() }

    fun reminder(id: Long) =
        query("SELECT * FROM reminders WHERE id=?", listOf(id.toString())) { it.toReminder() }.firstOrNull()

    fun saveReminder(r: Reminder) = write(
        "reminders", r.id,
        ContentValues().apply {
            put("class_id", r.classId)
            put("activity_id", r.activityId)
            put("title", r.title)
            put("reminder_time", r.time)
            put("is_enabled", if (r.enabled) 1 else 0)
        }
    )

    fun setReminderEnabled(id: Long, on: Boolean) {
        db.update(
            "reminders",
            ContentValues().apply { put("is_enabled", if (on) 1 else 0) },
            "id=?",
            arrayOf(id.toString())
        )
    }

    fun deleteReminder(id: Long) = remove("reminders", id)

    fun setActivityStatus(id: Long, status: String) {
        db.update(
            "activities",
            ContentValues().apply { put("status", status) },
            "id=?",
            arrayOf(id.toString())
        )
    }

    /** Deletes every subject, class schedule and task (reminders attached to them go too). */
    fun deleteAllSchedules() {
        db.beginTransaction()
        try {
            db.delete("activities", null, null)
            db.delete("subjects", null, null) // classes + their reminders cascade
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Deletes only tasks (activities). Subjects and class schedules are kept. */
    fun deleteAllTasks() {
        db.delete("activities", null, null)
    }

    /**
     * Runs already-validated INSERT statements inside one transaction.
     * commit = false only tests them and always rolls back, so nothing is saved.
     * Any error rolls everything back.
     */
    fun applyImport(statements: List<String>, commit: Boolean): ImportOutcome {
        val d = db
        d.beginTransaction()
        try {
            val s0 = count("subjects")
            val c0 = count("classes")
            val a0 = count("activities")

            for ((i, sql) in statements.withIndex()) {
                try {
                    d.execSQL(sql)
                } catch (e: Exception) {
                    return ImportOutcome(false, "Statement ${i + 1} failed: ${explain(e)}\n\n${sql.replace(Regex("\\s+"), " ").take(160)}")
                }
            }

            importProblem()?.let { return ImportOutcome(false, it) }

            val out = ImportOutcome(true, "", count("subjects") - s0, count("classes") - c0, count("activities") - a0)
            if (commit) d.setTransactionSuccessful()
            return out
        } finally {
            d.endTransaction()
        }
    }

    private fun count(table: String): Int = DatabaseUtils.queryNumEntries(db, table).toInt()

    private fun bad(sql: String): Boolean = DatabaseUtils.longForQuery(db, sql, null) > 0

    private fun importProblem(): String? {
        val t = "(%s NOT GLOB '[01][0-9]:[0-5][0-9]' AND %s NOT GLOB '2[0-3]:[0-5][0-9]')"
        if (bad("SELECT COUNT(*) FROM classes WHERE " + t.format("start_time", "start_time") + " OR " + t.format("end_time", "end_time"))) {
            return "Class times must be 24-hour HH:MM with a leading zero, like 08:00 or 13:30."
        }
        if (bad("SELECT COUNT(*) FROM subjects WHERE length(trim(subject_name)) = 0 OR length(trim(subject_code)) = 0")) {
            return "A subject has an empty name or code."
        }
        if (bad("SELECT COUNT(*) FROM activities WHERE length(trim(title)) = 0")) {
            return "A task has an empty title."
        }
        if (bad("SELECT COUNT(*) FROM activities WHERE type NOT IN ('Assignment','Project','Quiz','Exam')")) {
            return "Task type must be Assignment, Project, Quiz or Exam."
        }
        if (bad(
                "SELECT COUNT(*) FROM activities WHERE due_date IS NOT NULL " +
                    "AND due_date NOT GLOB '[0-9][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9]' " +
                    "AND due_date NOT GLOB '[0-9][0-9][0-9][0-9]-[01][0-9]-[0-3][0-9] [0-2][0-9]:[0-5][0-9]'"
            )
        ) {
            return "Task due_date must be YYYY-MM-DD or YYYY-MM-DD HH:MM (or NULL)."
        }
        return null
    }

    private fun explain(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return when {
            "classes.subject_id" in m && "NOT NULL" in m ->
                "the subject code was not found. Add the subject first and use exactly the same subject_code."
            "UNIQUE" in m && "subject_code" in m ->
                "that subject code already exists. Use INSERT OR IGNORE for subjects, or delete schedules first."
            "FOREIGN KEY" in m ->
                "a class or task points to a subject that does not exist."
            "CHECK constraint" in m ->
                "a value is not allowed. Check: day is MON-SUN, class_type is Online or Onsite, start is before end, " +
                    "Onsite needs a room (and no online_details), Online needs online_details (and no room), status is Pending/In Progress/Completed/Missed."
            "NOT NULL" in m ->
                "a required value is missing ($m)."
            else -> m
        }
    }
}
