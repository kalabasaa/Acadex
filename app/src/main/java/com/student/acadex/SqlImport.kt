package com.student.acadex

/**
 * Safety net for the "paste SQL from an AI" feature.
 *
 * Only plain INSERT statements for subjects / classes / activities are accepted.
 * Anything else (DROP, DELETE, UPDATE, CREATE, ...) is rejected before it can touch the database.
 */
object SqlGuard {

    class Parsed(val statements: List<String>, val ignored: Int, val error: String?)

    private val allowedColumns = mapOf(
        "subjects" to setOf("subject_name", "subject_code", "professor_name"),
        "classes" to setOf("subject_id", "day", "start_time", "end_time", "class_type", "room", "online_details"),
        "activities" to setOf("subject_id", "title", "type", "due_date", "description", "status")
    )

    private val forbidden = Regex(
        "\\b(DROP|DELETE|UPDATE|ALTER|CREATE|ATTACH|DETACH|PRAGMA|VACUUM|TRIGGER|REPLACE|UNION|WITH|REINDEX|" +
            "SAVEPOINT|RELEASE|BEGIN|COMMIT|ROLLBACK|ANALYZE|EXPLAIN|INDEXED|RETURNING)\\b",
        RegexOption.IGNORE_CASE
    )

    // Harmless lines AI tools like to add; we skip them because Acadex runs its own transaction.
    private val skippable = Regex(
        "^(BEGIN(\\s+TRANSACTION)?|COMMIT|END(\\s+TRANSACTION)?|PRAGMA\\s+foreign_keys\\s*=\\s*(ON|1))$",
        RegexOption.IGNORE_CASE
    )

    private val insertShape = Regex(
        "^INSERT\\s+(OR\\s+IGNORE\\s+)?INTO\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*VALUES\\s*\\(.*$",
        RegexOption.IGNORE_CASE
    )

    private val fromTable = Regex("\\bFROM\\s+(\\w+)", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Parsed {
        val text = raw.lines().filterNot { it.trim().startsWith("```") }.joinToString("\n")
        val split = split(text)
        if (split.second != null) return Parsed(emptyList(), 0, split.second)

        val out = ArrayList<String>()
        var ignored = 0
        split.first.forEachIndexed { index, stmt ->
            val n = index + 1
            val sk = skeleton(stmt)

            if (skippable.matches(sk)) {
                ignored++
                return@forEachIndexed
            }
            if ('"' in sk || '`' in sk) {
                return Parsed(emptyList(), 0, "Statement $n: use single quotes 'like this' for text, not double quotes or backticks.\n\n${preview(stmt)}")
            }
            if (sk.any { it == '\u2018' || it == '\u2019' || it == '\u201C' || it == '\u201D' }) {
                return Parsed(emptyList(), 0, "Statement $n has curly quotes. Replace them with straight single quotes '.\n\n${preview(stmt)}")
            }
            forbidden.find(sk)?.let {
                return Parsed(
                    emptyList(), 0,
                    "Statement $n uses ${it.value.uppercase()}, which is not allowed. Acadex already has its tables, so only INSERT statements are accepted.\n\n${preview(stmt)}"
                )
            }
            val m = insertShape.matchEntire(sk)
                ?: return Parsed(
                    emptyList(), 0,
                    "Statement $n must look like: INSERT INTO table (columns) VALUES (...);\n\n${preview(stmt)}"
                )
            val table = m.groupValues[2].lowercase()
            val allowed = allowedColumns[table]
                ?: return Parsed(emptyList(), 0, "Statement $n: table '$table' is not allowed. Use subjects, classes or activities.\n\n${preview(stmt)}")
            val cols = m.groupValues[3].split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            if (cols.isEmpty()) {
                return Parsed(emptyList(), 0, "Statement $n: list the column names after the table name.\n\n${preview(stmt)}")
            }
            if ("id" in cols) {
                return Parsed(emptyList(), 0, "Statement $n: remove the id column. Acadex numbers rows itself.\n\n${preview(stmt)}")
            }
            cols.firstOrNull { it !in allowed }?.let {
                return Parsed(
                    emptyList(), 0,
                    "Statement $n: unknown column '$it' for $table. Allowed: ${allowed.joinToString(", ")}.\n\n${preview(stmt)}"
                )
            }
            fromTable.findAll(sk).firstOrNull { it.groupValues[1].lowercase() != "subjects" }?.let {
                return Parsed(emptyList(), 0, "Statement $n: a subquery may only read from subjects.\n\n${preview(stmt)}")
            }
            out.add(stmt)
        }

        if (out.isEmpty()) {
            return Parsed(emptyList(), ignored, "No INSERT statements found. Paste the SQL the AI gave you.")
        }
        if (out.size > 1000) {
            return Parsed(emptyList(), ignored, "Too many statements (${out.size}). Import in smaller parts.")
        }
        return Parsed(out, ignored, null)
    }

    private fun preview(s: String): String {
        val one = s.replace(Regex("\\s+"), " ").trim()
        return if (one.length > 140) one.take(140) + "..." else one
    }

    /** Splits on ';' outside quotes and removes -- and slash-star comments. Returns (statements, error). */
    private fun split(text: String): Pair<List<String>, String?> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val next = if (i + 1 < text.length) text[i + 1] else '\u0000'
            if (inQuote) {
                cur.append(ch)
                if (ch == '\'') {
                    if (next == '\'') {
                        cur.append(next)
                        i++
                    } else {
                        inQuote = false
                    }
                }
            } else if (ch == '\'') {
                inQuote = true
                cur.append(ch)
            } else if (ch == '-' && next == '-') {
                while (i < text.length && text[i] != '\n') i++
                cur.append(' ')
                continue
            } else if (ch == '/' && next == '*') {
                val end = text.indexOf("*/", i + 2)
                i = if (end < 0) text.length else end + 2
                cur.append(' ')
                continue
            } else if (ch == ';') {
                val s = cur.toString().trim()
                if (s.isNotEmpty()) out.add(s)
                cur.setLength(0)
            } else {
                cur.append(ch)
            }
            i++
        }
        if (inQuote) {
            return Pair(emptyList(), "A text value is missing its closing quote '. Check apostrophes: write O''Brien (two quotes) inside text.")
        }
        val last = cur.toString().trim()
        if (last.isNotEmpty()) out.add(last)
        return Pair(out, null)
    }

    /** The statement with the inside of every '...' text value removed, spaces collapsed. */
    private fun skeleton(s: String): String {
        val sb = StringBuilder()
        var inQuote = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (inQuote) {
                if (ch == '\'') {
                    if (i + 1 < s.length && s[i + 1] == '\'') {
                        i++
                    } else {
                        inQuote = false
                        sb.append(ch)
                    }
                }
            } else {
                if (ch == '\'') inQuote = true
                sb.append(ch)
            }
            i++
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}

/** The text the user copies and gives to an AI together with a picture of their schedule. */
object AiImport {
    val PROMPT = """
You are helping me fill the local SQLite database of my student app, Acadex.
I will attach a picture of my class schedule (and optionally my tasks). Read it carefully and reply with ONLY SQL INSERT statements inside one ```sql code block. No explanations.

TABLES (they already exist. Do NOT create or alter them):
subjects(subject_name TEXT NOT NULL, subject_code TEXT NOT NULL UNIQUE, professor_name TEXT)
classes(subject_id, day, start_time, end_time, class_type, room, online_details)
activities(subject_id, title, type, due_date, description, status)

RULES
1. Write only INSERT INTO statements for subjects, classes and activities. Never write the id column, CREATE, DROP, DELETE, UPDATE, PRAGMA, BEGIN or COMMIT.
2. Always list the column names.
3. Insert the subjects first with INSERT OR IGNORE. Every subject needs a short unique subject_code. Use the code shown in the image; if there is none, invent a short one such as IT101.
4. Link every class and activity to its subject with a subquery on the code, for example (SELECT id FROM subjects WHERE subject_code = 'IT101'). Never guess numeric ids.
5. day must be one of 'MON','TUE','WED','THU','FRI','SAT','SUN' (uppercase). One row per weekly meeting: a subject that meets Monday and Wednesday needs 2 rows.
6. start_time and end_time use 24-hour 'HH:MM' with a leading zero ('08:00', '13:30'). Start must be earlier than end.
7. class_type is 'Onsite' or 'Online'. Onsite: fill room and set online_details to NULL. Online: fill online_details (link or platform) and set room to NULL.
8. activities.type is one of 'Assignment','Project','Quiz','Exam'. status is one of 'Pending','In Progress','Completed','Missed' (use 'Pending' if unknown). due_date is 'YYYY-MM-DD' or 'YYYY-MM-DD HH:MM', or NULL. subject_id may be NULL.
9. Use single quotes for text. Write an apostrophe inside text as two single quotes ('O''Brien').
10. End every statement with a semicolon.

EXAMPLE
INSERT OR IGNORE INTO subjects (subject_name, subject_code, professor_name) VALUES ('Application Development', 'IT101', 'Mr. Cruz');
INSERT INTO classes (subject_id, day, start_time, end_time, class_type, room, online_details) VALUES ((SELECT id FROM subjects WHERE subject_code = 'IT101'), 'MON', '08:00', '09:30', 'Onsite', 'Room 204', NULL);
INSERT INTO activities (subject_id, title, type, due_date, description, status) VALUES ((SELECT id FROM subjects WHERE subject_code = 'IT101'), 'Final project', 'Project', '2026-12-10 23:59', 'Submit the app and the report', 'Pending');

Now read my attached image and write the SQL.
""".trimIndent()
}
