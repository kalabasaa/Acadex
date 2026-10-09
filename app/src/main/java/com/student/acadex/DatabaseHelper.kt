
package com.student.acadex

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "acadex.db", null, 1) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {

        // Subjects and professor information
        db.execSQL(
            """
            CREATE TABLE subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_name TEXT NOT NULL,
                subject_code TEXT NOT NULL UNIQUE,
                professor_name TEXT
            )
            """.trimIndent()
        )

        // Weekly class schedules
        // Each row represents one weekly meeting.
        db.execSQL(
            """
            CREATE TABLE classes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER NOT NULL,

                day TEXT NOT NULL CHECK (
                    day IN (
                        'MON', 'TUE', 'WED', 'THU',
                        'FRI', 'SAT', 'SUN'
                    )
                ),

                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,

                class_type TEXT NOT NULL CHECK (
                    class_type IN ('Online', 'Onsite')
                ),

                room TEXT,
                online_details TEXT,

                FOREIGN KEY (subject_id)
                    REFERENCES subjects(id)
                    ON DELETE CASCADE,

                CHECK (start_time < end_time),

                CHECK (
                    (
                        class_type = 'Online'
                        AND online_details IS NOT NULL
                        AND length(trim(online_details)) > 0
                        AND room IS NULL
                    )
                    OR
                    (
                        class_type = 'Onsite'
                        AND room IS NOT NULL
                        AND length(trim(room)) > 0
                        AND online_details IS NULL
                    )
                )
            )
            """.trimIndent()
        )

        // Assignments, projects, quizzes, and exams
        db.execSQL(
            """
            CREATE TABLE activities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                due_date TEXT,
                description TEXT,
                status TEXT NOT NULL DEFAULT 'Pending'
                    CHECK (
                        status IN (
                            'Pending',
                            'In Progress',
                            'Completed',
                            'Missed'
                        )
                    ),

                FOREIGN KEY (subject_id)
                    REFERENCES subjects(id)
                    ON DELETE SET NULL
            )
            """.trimIndent()
        )

        // Reminders for classes and activities
        db.execSQL(
            """
            CREATE TABLE reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                class_id INTEGER,
                activity_id INTEGER,
                title TEXT NOT NULL,
                reminder_time INTEGER NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1
                    CHECK (is_enabled IN (0, 1)),

                FOREIGN KEY (class_id)
                    REFERENCES classes(id)
                    ON DELETE CASCADE,

                FOREIGN KEY (activity_id)
                    REFERENCES activities(id)
                    ON DELETE CASCADE,

                CHECK (
                    class_id IS NOT NULL
                    OR activity_id IS NOT NULL
                )
            )
            """.trimIndent()
        )

        // Indexes for faster lookups
        db.execSQL(
            "CREATE INDEX idx_classes_day ON classes(day)"
        )

        db.execSQL(
            "CREATE INDEX idx_activities_due_date ON activities(due_date)"
        )

        db.execSQL(
            "CREATE INDEX idx_reminders_time ON reminders(reminder_time)"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Database migrations will be added when the schema changes.
    }
}
