package com.student.acadex

data class Subject(
    val id: Long = 0,
    val name: String,
    val code: String,
    val professor: String?
)

data class ClassItem(
    val id: Long = 0,
    val subjectId: Long,
    val day: String,
    val start: String,
    val end: String,
    val type: String,
    val room: String?,
    val online: String?,
    val subjectName: String = ""
)

data class ActivityItem(
    val id: Long = 0,
    val subjectId: Long?,
    val title: String,
    val type: String,
    val dueDate: String?,
    val description: String?,
    val status: String,
    val subjectName: String? = null,
    val subjectCode: String? = null
)

data class Reminder(
    val id: Long = 0,
    val classId: Long?,
    val activityId: Long?,
    val title: String,
    val time: Long,
    val enabled: Boolean
)

data class Note(
    val id: Long = 0,
    val subjectId: Long?,
    val title: String,
    val content: String,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val subjectName: String? = null,
    val subjectCode: String? = null
)

data class GradeCalc(
    val id: Long = 0,
    val subjectId: Long?,
    val structure: String, // "3" (Prelim/Midterm/Finals) or "4" (+ Semi-finals)
    val prelim: Double,
    val midterm: Double,
    val semifinal: Double?, // only used when structure == "4"
    val finals: Double,
    val prelimWeight: Double,
    val midtermWeight: Double,
    val semifinalWeight: Double?,
    val finalsWeight: Double,
    val result: Double,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val subjectName: String? = null,
    val subjectCode: String? = null
)

/** The semesters a user can pick when archiving. */
object Semesters {
    val all = listOf("1st Semester", "2nd Semester", "Summer")
}

/** One archived semester (a row of archive_semesters) with how much it holds. */
data class ArchivedSemester(
    val id: Long,
    val academicYear: String,
    val semester: String,
    val archivedAt: Long,
    val subjects: Int,
    val classes: Int,
    val activities: Int
)
