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

/** Result of archiving or restoring a semester. */
class ArchiveOutcome(
    val ok: Boolean,
    val message: String = "",
    val subjects: Int = 0,
    val classes: Int = 0,
    val activities: Int = 0,
    /** Restore only: archived records that already existed in the active data and were not added again. */
    val skipped: Int = 0
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
    private val noteSql =
        "SELECT n.*, s.subject_name, s.subject_code FROM notes n LEFT JOIN subjects s ON s.id = n.subject_id"
    private val gradeCalcSql =
        "SELECT g.*, s.subject_name, s.subject_code FROM grade_calcs g LEFT JOIN subjects s ON s.id = g.subject_id"

    private fun Cursor.lng(c: String): Long? =
        getColumnIndexOrThrow(c).let { if (isNull(it)) null else getLong(it) }

    private fun Cursor.str(c: String): String? =
        getColumnIndexOrThrow(c).let { if (isNull(it)) null else getString(it) }

    private fun Cursor.dbl(c: String): Double? =
        getColumnIndexOrThrow(c).let { if (isNull(it)) null else getDouble(it) }

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

    private fun Cursor.toNote() = Note(
        lng("id")!!, lng("subject_id"), str("title")!!, str("content") ?: "",
        lng("created_at")!!, lng("updated_at")!!, str("subject_name"), str("subject_code")
    )

    private fun Cursor.toGradeCalc() = GradeCalc(
        lng("id")!!, lng("subject_id"), str("structure")!!,
        dbl("prelim")!!, dbl("midterm")!!, dbl("semifinal"), dbl("finals")!!,
        dbl("prelim_weight")!!, dbl("midterm_weight")!!, dbl("semifinal_weight"), dbl("finals_weight")!!,
        dbl("result")!!, lng("created_at")!!, lng("updated_at")!!, str("subject_name"), str("subject_code")
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

    /** Any existing class on the same day whose time range overlaps [start, end), regardless of subject. */
    fun classConflict(day: String, start: String, end: String) =
        query(
            "$classSql WHERE c.day=? AND c.start_time<? AND c.end_time>? ORDER BY c.start_time LIMIT 1",
            listOf(day, end, start)
        ) { it.toClass() }.firstOrNull()

    /**
     * Imports subjects/classes shared via QR, in one transaction. A subject whose code already
     * exists is reused (never duplicated or overwritten). selected controls exactly which
     * incoming classes to insert — the caller has already resolved duplicates/conflicts with the
     * user; a class that exactly matches one already in the database is skipped regardless, so
     * nothing is ever inserted twice. Existing rows are never modified, only new ones added.
     */
    fun importShared(subjects: List<SharedSubject>, selected: Set<Pair<Int, Int>>): ImportOutcome {
        val d = db
        d.beginTransaction()
        try {
            var addedSubjects = 0
            var addedClasses = 0
            var skipped = 0
            subjects.forEachIndexed { si, s ->
                val subjectId = longOrNull("SELECT id FROM subjects WHERE subject_code=?", s.code)
                    ?: d.insertOrThrow(
                        "subjects", null,
                        ContentValues().apply {
                            put("subject_name", s.name)
                            put("subject_code", s.code)
                            put("professor_name", s.professor)
                        }
                    ).also { addedSubjects++ }

                s.classes.forEachIndexed { ci, c ->
                    if ((si to ci) !in selected) return@forEachIndexed
                    val dup = longOrNull(
                        "SELECT id FROM classes WHERE subject_id=? AND day=? AND start_time=? AND end_time=? AND class_type=? " +
                            "AND IFNULL(room,'')=? AND IFNULL(online_details,'')=?",
                        subjectId.toString(), c.day, c.start, c.end, c.type, c.room.orEmpty(), c.online.orEmpty()
                    )
                    if (dup != null) {
                        skipped++
                        return@forEachIndexed
                    }
                    d.insertOrThrow(
                        "classes", null,
                        ContentValues().apply {
                            put("subject_id", subjectId)
                            put("day", c.day)
                            put("start_time", c.start)
                            put("end_time", c.end)
                            put("class_type", c.type)
                            put("room", c.room)
                            put("online_details", c.online)
                        }
                    )
                    addedClasses++
                }
            }
            d.setTransactionSuccessful()
            val msg = "Imported $addedSubjects subject(s) and $addedClasses class(es)." +
                if (skipped > 0) " Skipped $skipped already in your schedule." else ""
            return ImportOutcome(true, msg, addedSubjects, addedClasses)
        } catch (e: Exception) {
            return ImportOutcome(false, e.message ?: "Could not import.")
        } finally {
            d.endTransaction()
        }
    }

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

    /**
     * subjectId picks one subject's notes; noSubjectOnly picks the "None" bucket (subjectId
     * is ignored when this is true); leaving both out returns every note. search matches
     * the title or content.
     */
    fun notes(subjectId: Long? = null, noSubjectOnly: Boolean = false, search: String? = null): List<Note> {
        val where = ArrayList<String>()
        val args = ArrayList<String>()
        if (noSubjectOnly) {
            where.add("n.subject_id IS NULL")
        } else if (subjectId != null) {
            where.add("n.subject_id=?")
            args.add(subjectId.toString())
        }
        if (!search.isNullOrBlank()) {
            where.add("(n.title LIKE ? OR n.content LIKE ?)")
            val like = "%${search.trim()}%"
            args.add(like)
            args.add(like)
        }
        val sql = noteSql +
            (if (where.isEmpty()) "" else " WHERE " + where.joinToString(" AND ")) +
            " ORDER BY n.updated_at DESC"
        return query(sql, args) { it.toNote() }
    }

    fun note(id: Long) =
        query("$noteSql WHERE n.id=?", listOf(id.toString())) { it.toNote() }.firstOrNull()

    fun saveNote(n: Note): Boolean {
        val now = System.currentTimeMillis()
        return write(
            "notes", n.id,
            ContentValues().apply {
                put("subject_id", n.subjectId)
                put("title", n.title)
                put("content", n.content)
                put("created_at", if (n.id == 0L) now else n.createdAt)
                put("updated_at", now)
            }
        )
    }

    fun deleteNote(id: Long) = remove("notes", id)

    /** subjectId picks one subject's saved calculations; noSubjectOnly picks the "None" bucket. */
    fun gradeCalcs(subjectId: Long? = null, noSubjectOnly: Boolean = false): List<GradeCalc> {
        val where = when {
            noSubjectOnly -> " WHERE g.subject_id IS NULL"
            subjectId != null -> " WHERE g.subject_id=?"
            else -> ""
        }
        val args = if (!noSubjectOnly && subjectId != null) listOf(subjectId.toString()) else emptyList()
        return query("$gradeCalcSql$where ORDER BY g.updated_at DESC", args) { it.toGradeCalc() }
    }

    fun gradeCalc(id: Long) =
        query("$gradeCalcSql WHERE g.id=?", listOf(id.toString())) { it.toGradeCalc() }.firstOrNull()

    fun saveGradeCalc(g: GradeCalc): Boolean {
        val now = System.currentTimeMillis()
        return write(
            "grade_calcs", g.id,
            ContentValues().apply {
                put("subject_id", g.subjectId)
                put("structure", g.structure)
                put("prelim", g.prelim)
                put("midterm", g.midterm)
                put("semifinal", g.semifinal)
                put("finals", g.finals)
                put("prelim_weight", g.prelimWeight)
                put("midterm_weight", g.midtermWeight)
                put("semifinal_weight", g.semifinalWeight)
                put("finals_weight", g.finalsWeight)
                put("result", g.result)
                put("created_at", if (g.id == 0L) now else g.createdAt)
                put("updated_at", now)
            }
        )
    }

    fun deleteGradeCalc(id: Long) = remove("grade_calcs", id)

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

    // ------------------------------------------------------------------
    // Semester archive
    // ------------------------------------------------------------------

    private fun Cursor.int(c: String): Int = getInt(getColumnIndexOrThrow(c))

    private fun longOrNull(sql: String, vararg args: String): Long? =
        query(sql, args.toList()) { it.getLong(0) }.firstOrNull()

    /** True when this academic year + semester is already in the archive. */
    fun archiveExists(year: String, semester: String): Boolean = DatabaseUtils.longForQuery(
        db,
        "SELECT COUNT(*) FROM archive_semesters WHERE academic_year=? AND semester=?",
        arrayOf(year, semester)
    ) > 0

    /** Subjects, class schedules and tasks that are active right now (what archiving would move). */
    fun activeCounts() = Triple(count("subjects"), count("classes"), count("activities"))

    /**
     * Moves every active subject, class schedule, task and reminder into the archive under the
     * given academic year + semester. Everything happens in ONE transaction: if any step fails
     * (or a count does not match) it is rolled back and nothing changes.
     * Nothing is deleted before it has been copied and verified.
     */
    fun archiveSemester(year: String, semester: String): ArchiveOutcome {
        val d = db
        d.beginTransaction()
        try {
            if (archiveExists(year, semester)) {
                return ArchiveOutcome(false, "$year, $semester is already archived. Restore it first, or pick another semester.")
            }
            val s0 = count("subjects")
            val c0 = count("classes")
            val a0 = count("activities")
            val r0 = count("reminders")
            if (s0 == 0 && c0 == 0 && a0 == 0) {
                return ArchiveOutcome(false, "There is nothing to archive. Add subjects, schedules or tasks first.")
            }

            val sem = d.insertOrThrow(
                "archive_semesters", null,
                ContentValues().apply {
                    put("academic_year", year)
                    put("semester", semester)
                    put("archived_at", System.currentTimeMillis())
                }
            )

            d.execSQL(
                "INSERT INTO archived_subjects (semester_id, source_id, subject_name, subject_code, professor_name) " +
                    "SELECT ?, id, subject_name, subject_code, professor_name FROM subjects",
                arrayOf<Any>(sem)
            )
            d.execSQL(
                "INSERT INTO archived_classes (semester_id, source_id, subject_id, day, start_time, end_time, class_type, room, online_details) " +
                    "SELECT ?, c.id, (SELECT s.id FROM archived_subjects s WHERE s.semester_id = ? AND s.source_id = c.subject_id), " +
                    "c.day, c.start_time, c.end_time, c.class_type, c.room, c.online_details FROM classes c",
                arrayOf<Any>(sem, sem)
            )
            d.execSQL(
                "INSERT INTO archived_activities (semester_id, source_id, subject_id, title, type, due_date, description, status) " +
                    "SELECT ?, a.id, (SELECT s.id FROM archived_subjects s WHERE s.semester_id = ? AND s.source_id = a.subject_id), " +
                    "a.title, a.type, a.due_date, a.description, a.status FROM activities a",
                arrayOf<Any>(sem, sem)
            )
            d.execSQL(
                "INSERT INTO archived_reminders (semester_id, class_id, activity_id, title, reminder_time, is_enabled) " +
                    "SELECT ?, (SELECT ac.id FROM archived_classes ac WHERE ac.semester_id = ? AND ac.source_id = r.class_id), " +
                    "(SELECT aa.id FROM archived_activities aa WHERE aa.semester_id = ? AND aa.source_id = r.activity_id), " +
                    "r.title, r.reminder_time, r.is_enabled FROM reminders r",
                arrayOf<Any>(sem, sem, sem)
            )

            // verify the copy is complete BEFORE the active rows are removed
            fun archived(table: String) =
                DatabaseUtils.longForQuery(d, "SELECT COUNT(*) FROM $table WHERE semester_id=?", arrayOf(sem.toString())).toInt()
            check(archived("archived_subjects") == s0) { "the archived copy of your subjects did not match" }
            check(archived("archived_classes") == c0) { "the archived copy of your classes did not match" }
            check(archived("archived_activities") == a0) { "the archived copy of your tasks did not match" }
            check(archived("archived_reminders") == r0) { "the archived copy of your reminders did not match" }

            d.delete("activities", null, null) // its reminders cascade
            d.delete("subjects", null, null)   // classes (+ their reminders) cascade
            d.delete("reminders", null, null)  // anything left (already copied above)

            d.setTransactionSuccessful()
            return ArchiveOutcome(true, subjects = s0, classes = c0, activities = a0)
        } catch (e: Exception) {
            return ArchiveOutcome(false, "Archiving failed, so nothing was changed. (${e.message ?: e.javaClass.simpleName})")
        } finally {
            d.endTransaction()
        }
    }

    fun archivedSemesters(): List<ArchivedSemester> = query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM archived_subjects WHERE semester_id = s.id) AS n_subjects, " +
            "(SELECT COUNT(*) FROM archived_classes WHERE semester_id = s.id) AS n_classes, " +
            "(SELECT COUNT(*) FROM archived_activities WHERE semester_id = s.id) AS n_activities " +
            "FROM archive_semesters s " +
            "ORDER BY s.academic_year DESC, " +
            "CASE s.semester WHEN '1st Semester' THEN 1 WHEN '2nd Semester' THEN 2 ELSE 3 END, s.archived_at DESC"
    ) { it.toArchivedSemester() }

    fun archivedSemester(id: Long): ArchivedSemester? = query(
        "SELECT s.*, " +
            "(SELECT COUNT(*) FROM archived_subjects WHERE semester_id = s.id) AS n_subjects, " +
            "(SELECT COUNT(*) FROM archived_classes WHERE semester_id = s.id) AS n_classes, " +
            "(SELECT COUNT(*) FROM archived_activities WHERE semester_id = s.id) AS n_activities " +
            "FROM archive_semesters s WHERE s.id=?",
        listOf(id.toString())
    ) { it.toArchivedSemester() }.firstOrNull()

    private fun Cursor.toArchivedSemester() = ArchivedSemester(
        lng("id")!!, str("academic_year")!!, str("semester")!!, lng("archived_at")!!,
        int("n_subjects"), int("n_classes"), int("n_activities")
    )

    fun archivedSubjects(semesterId: Long) = query(
        "SELECT * FROM archived_subjects WHERE semester_id=? ORDER BY subject_name COLLATE NOCASE",
        listOf(semesterId.toString())
    ) { it.toSubject() }

    fun archivedClasses(semesterId: Long) = query(
        "SELECT c.*, s.subject_name FROM archived_classes c JOIN archived_subjects s ON s.id = c.subject_id " +
            "WHERE c.semester_id=? ORDER BY s.subject_name COLLATE NOCASE, $dayOrder, c.start_time",
        listOf(semesterId.toString())
    ) { it.toClass() }

    fun archivedActivities(semesterId: Long) = query(
        "SELECT a.*, s.subject_name, s.subject_code FROM archived_activities a " +
            "LEFT JOIN archived_subjects s ON s.id = a.subject_id " +
            "WHERE a.semester_id=? ORDER BY a.due_date IS NULL, a.due_date, a.title",
        listOf(semesterId.toString())
    ) { it.toActivity() }

    private fun archivedReminders(semesterId: Long) = query(
        "SELECT * FROM archived_reminders WHERE semester_id=?", listOf(semesterId.toString())
    ) { it.toReminder() }

    /**
     * Moves an archived semester back into the active tables, in ONE transaction (all or nothing).
     * Relationships are rebuilt through id maps (archived id -> active id).
     * No duplicates: a subject whose code is already active is reused instead of added again,
     * and a class, task or reminder identical to an active one is skipped.
     * When everything is back, the archive entry is removed.
     */
    fun restoreSemester(semesterId: Long): ArchiveOutcome {
        val d = db
        d.beginTransaction()
        try {
            val sem = archivedSemester(semesterId)
                ?: return ArchiveOutcome(false, "This archived semester no longer exists.")

            var addedSubjects = 0
            var addedClasses = 0
            var addedActivities = 0
            var skipped = 0

            val subjectMap = HashMap<Long, Long>() // archived subject id -> active subject id
            for (s in archivedSubjects(semesterId)) {
                val existing = longOrNull("SELECT id FROM subjects WHERE subject_code=?", s.code)
                if (existing != null) {
                    subjectMap[s.id] = existing
                    skipped++
                } else {
                    subjectMap[s.id] = d.insertOrThrow(
                        "subjects", null,
                        ContentValues().apply {
                            put("subject_name", s.name)
                            put("subject_code", s.code)
                            put("professor_name", s.professor)
                        }
                    )
                    addedSubjects++
                }
            }

            val classMap = HashMap<Long, Long>() // archived class id -> active class id
            for (c in archivedClasses(semesterId)) {
                val subjectId = subjectMap[c.subjectId] ?: error("class without subject")
                val existing = longOrNull(
                    "SELECT id FROM classes WHERE subject_id=? AND day=? AND start_time=? AND end_time=? AND class_type=? " +
                        "AND IFNULL(room,'')=? AND IFNULL(online_details,'')=?",
                    subjectId.toString(), c.day, c.start, c.end, c.type, c.room.orEmpty(), c.online.orEmpty()
                )
                if (existing != null) {
                    classMap[c.id] = existing
                    skipped++
                } else {
                    classMap[c.id] = d.insertOrThrow(
                        "classes", null,
                        ContentValues().apply {
                            put("subject_id", subjectId)
                            put("day", c.day)
                            put("start_time", c.start)
                            put("end_time", c.end)
                            put("class_type", c.type)
                            put("room", c.room)
                            put("online_details", c.online)
                        }
                    )
                    addedClasses++
                }
            }

            // NOTE: IFNULL(...) is an expression, so SQLite does not convert the bound text to a number
            // for us. The CAST(? AS INTEGER) in the two duplicate checks below is what makes them match.
            val activityMap = HashMap<Long, Long>() // archived task id -> active task id
            for (a in archivedActivities(semesterId)) {
                val subjectId = a.subjectId?.let { subjectMap[it] }
                val existing = longOrNull(
                    "SELECT id FROM activities WHERE IFNULL(subject_id,0)=CAST(? AS INTEGER) AND title=? AND type=? AND IFNULL(due_date,'')=?",
                    (subjectId ?: 0L).toString(), a.title, a.type, a.dueDate.orEmpty()
                )
                if (existing != null) {
                    activityMap[a.id] = existing
                    skipped++
                } else {
                    activityMap[a.id] = d.insertOrThrow(
                        "activities", null,
                        ContentValues().apply {
                            put("subject_id", subjectId)
                            put("title", a.title)
                            put("type", a.type)
                            put("due_date", a.dueDate)
                            put("description", a.description)
                            put("status", a.status)
                        }
                    )
                    addedActivities++
                }
            }

            for (r in archivedReminders(semesterId)) {
                val classId = r.classId?.let { classMap[it] }
                val activityId = r.activityId?.let { activityMap[it] }
                if (classId == null && activityId == null) continue
                val existing = longOrNull(
                    "SELECT id FROM reminders WHERE IFNULL(class_id,0)=CAST(? AS INTEGER) AND IFNULL(activity_id,0)=CAST(? AS INTEGER) AND title=? AND reminder_time=?",
                    (classId ?: 0L).toString(), (activityId ?: 0L).toString(), r.title, r.time.toString()
                )
                if (existing == null) {
                    d.insertOrThrow(
                        "reminders", null,
                        ContentValues().apply {
                            put("class_id", classId)
                            put("activity_id", activityId)
                            put("title", r.title)
                            put("reminder_time", r.time)
                            put("is_enabled", if (r.enabled) 1 else 0)
                        }
                    )
                }
            }

            // everything is back: remove the archive entry (its archived rows cascade)
            d.delete("archive_semesters", "id=?", arrayOf(semesterId.toString()))

            d.setTransactionSuccessful()
            return ArchiveOutcome(true, sem.academicYear + ", " + sem.semester, addedSubjects, addedClasses, addedActivities, skipped)
        } catch (e: Exception) {
            return ArchiveOutcome(false, "Restoring failed, so nothing was changed. (${e.message ?: e.javaClass.simpleName})")
        } finally {
            d.endTransaction()
        }
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
