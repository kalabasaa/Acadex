package com.student.acadex

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "acadex.db", null, 5) {

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
                            'Finished',
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

        createArchiveTables(db)
        createNotesTable(db)
        createGradeCalcsTable(db)
        createUsersTable(db)
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // v1 -> v2: semester archive. Only adds new tables, existing data is untouched.
        // (SQLiteOpenHelper already runs onUpgrade inside one transaction.)
        if (oldVersion < 2) {
            createArchiveTables(db)
        }
        // v2 -> v3: personal notes, optionally tied to a subject. Only adds a new table.
        if (oldVersion < 3) {
            createNotesTable(db)
        }
        // v3 -> v4: saved GPA calculator results. Only adds a new table.
        if (oldVersion < 4) {
            createGradeCalcsTable(db)
        }
        // v4 -> v5: user settings table. Stores user_name, dark_mode, and show_campus_map.
        if (oldVersion < 5) {
            createUsersTable(db)
        }
    }

    /** User settings table storing user name, dark mode, and campus map preference. */
    private fun createUsersTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                user_name TEXT NOT NULL DEFAULT '',
                dark_mode INTEGER NOT NULL DEFAULT 0 CHECK (dark_mode IN (0, 1)),
                show_campus_map INTEGER NOT NULL DEFAULT 1 CHECK (show_campus_map IN (0, 1))
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO users (id, user_name, dark_mode, show_campus_map)
            VALUES (1, '', 0, 1)
            """.trimIndent()
        )
    }

    /** Personal notes. Independent of the archive: notes are never archived or restored. */
    private fun createNotesTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS notes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER,
                title TEXT NOT NULL,
                content TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,

                FOREIGN KEY (subject_id)
                    REFERENCES subjects(id)
                    ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_subject ON notes(subject_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_notes_updated ON notes(updated_at)")
    }

    /**
     * Semester archive. Archived data lives in its own tables, so it can never show up in the
     * active subjects / schedules / tasks screens or clash with active data (for example the
     * UNIQUE subject_code). Rows keep their relationships through archive-local ids, and
     * source_id remembers the id the row had while it was active.
     * One row per archived semester; deleting it removes its archived rows (ON DELETE CASCADE),
     * which is what Restore does after moving the records back.
     */
    private fun createArchiveTables(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS archive_semesters (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                academic_year TEXT NOT NULL,
                semester TEXT NOT NULL CHECK (
                    semester IN ('1st Semester', '2nd Semester', 'Summer')
                ),
                archived_at INTEGER NOT NULL,
                UNIQUE (academic_year, semester)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS archived_subjects (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                semester_id INTEGER NOT NULL,
                source_id INTEGER NOT NULL,
                subject_name TEXT NOT NULL,
                subject_code TEXT NOT NULL,
                professor_name TEXT,

                FOREIGN KEY (semester_id)
                    REFERENCES archive_semesters(id)
                    ON DELETE CASCADE,

                UNIQUE (semester_id, source_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS archived_classes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                semester_id INTEGER NOT NULL,
                source_id INTEGER NOT NULL,
                subject_id INTEGER NOT NULL,
                day TEXT NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                class_type TEXT NOT NULL,
                room TEXT,
                online_details TEXT,

                FOREIGN KEY (semester_id)
                    REFERENCES archive_semesters(id)
                    ON DELETE CASCADE,

                FOREIGN KEY (subject_id)
                    REFERENCES archived_subjects(id)
                    ON DELETE CASCADE,

                UNIQUE (semester_id, source_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS archived_activities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                semester_id INTEGER NOT NULL,
                source_id INTEGER NOT NULL,
                subject_id INTEGER,
                title TEXT NOT NULL,
                type TEXT NOT NULL,
                due_date TEXT,
                description TEXT,
                status TEXT NOT NULL,

                FOREIGN KEY (semester_id)
                    REFERENCES archive_semesters(id)
                    ON DELETE CASCADE,

                FOREIGN KEY (subject_id)
                    REFERENCES archived_subjects(id)
                    ON DELETE SET NULL,

                UNIQUE (semester_id, source_id)
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS archived_reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                semester_id INTEGER NOT NULL,
                class_id INTEGER,
                activity_id INTEGER,
                title TEXT NOT NULL,
                reminder_time INTEGER NOT NULL,
                is_enabled INTEGER NOT NULL DEFAULT 1,

                FOREIGN KEY (semester_id)
                    REFERENCES archive_semesters(id)
                    ON DELETE CASCADE,

                FOREIGN KEY (class_id)
                    REFERENCES archived_classes(id)
                    ON DELETE CASCADE,

                FOREIGN KEY (activity_id)
                    REFERENCES archived_activities(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_archived_subjects_sem ON archived_subjects(semester_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_archived_classes_sem ON archived_classes(semester_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_archived_activities_sem ON archived_activities(semester_id)"
        )
    }

    /**
     * Saved GPA calculator results. structure is '3' (Prelim/Midterm/Finals) or '4'
     * (Prelim/Midterm/Semi-finals/Finals); semifinal/semifinal_weight are only used by '4'.
     * Weights are entered by the user (percentages that must add up to 100) rather than a
     * fixed institutional formula, since Acadex has no built-in grading scheme to assume.
     */
    private fun createGradeCalcsTable(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS grade_calcs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                subject_id INTEGER,
                structure TEXT NOT NULL CHECK (structure IN ('3', '4')),

                prelim REAL NOT NULL,
                midterm REAL NOT NULL,
                semifinal REAL,
                finals REAL NOT NULL,

                prelim_weight REAL NOT NULL,
                midterm_weight REAL NOT NULL,
                semifinal_weight REAL,
                finals_weight REAL NOT NULL,

                result REAL NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,

                FOREIGN KEY (subject_id)
                    REFERENCES subjects(id)
                    ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_grade_calcs_subject ON grade_calcs(subject_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_grade_calcs_updated ON grade_calcs(updated_at)")
    }
}
