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
    val subjectName: String? = null
)

data class Reminder(
    val id: Long = 0,
    val classId: Long?,
    val activityId: Long?,
    val title: String,
    val time: Long,
    val enabled: Boolean
)
