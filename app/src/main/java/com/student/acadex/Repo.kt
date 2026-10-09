package com.student.acadex

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException

private var shared: DatabaseHelper? = null

private fun helperOf(context: Context): DatabaseHelper =
    shared ?: DatabaseHelper(context.applicationContext).also { shared = it }

class Repo(context: Context) {
    private val helper = helperOf(context)
    private val db get() = helper.writableDatabase

    private val dayOrder =
        "CASE c.day WHEN 'MON' THEN 1 WHEN 'TUE' THEN 2 WHEN 'WED' THEN 3 WHEN 'THU' THEN 4 WHEN 'FRI' THEN 5 WHEN 'SAT' THEN 6 ELSE 7 END"
    private val classSql =
        "SELECT c.*, s.subject_name FROM classes c JOIN subjects s ON s.id = c.subject_id"
    private val activitySql =
        "SELECT a.*, s.subject_name FROM activities a LEFT JOIN subjects s ON s.id = a.subject_id"

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
        str("description"), str("status")!!, str("subject_name")
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
            where.add("a.due_date=?")
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
}
