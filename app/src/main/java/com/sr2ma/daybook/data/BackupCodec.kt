package com.sr2ma.daybook.data

import com.sr2ma.daybook.domain.Dates
import com.sr2ma.daybook.domain.model.LogEntry
import com.sr2ma.daybook.domain.model.LogKind
import com.sr2ma.daybook.domain.model.Meeting
import com.sr2ma.daybook.domain.model.Pass
import com.sr2ma.daybook.domain.model.PassCategory
import com.sr2ma.daybook.domain.model.Priority
import com.sr2ma.daybook.domain.model.Task
import com.sr2ma.daybook.domain.model.TaskStatus
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/** Thrown when a file is not a Daybook backup, or is newer than this app understands. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Turns the whole dataset into one JSON document and back.
 *
 * Written with `org.json`, which ships with Android, so the backup format costs
 * the app no extra dependency and no code generation. The output is indented and
 * uses ISO dates, so a backup can be read, diffed or hand-edited in any text
 * editor — that matters when the file is the user's only copy of their data.
 *
 * Decoding is deliberately forgiving about individual rows: a row missing the one
 * field it cannot do without is skipped rather than failing the whole import, and
 * unknown fields are ignored so a file from a later version still mostly loads.
 * It is strict about only one thing, the [FORMAT] marker, which is what stops an
 * unrelated JSON file from being read in as an empty dataset.
 */
class BackupCodec {

    // ---- Writing ---------------------------------------------------------

    fun encode(snapshot: Snapshot, exportedAt: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("exportedAt", exportedAt)
        // The same instant in text, purely so a person opening the file can see
        // when it was made. Never read back.
        root.put("exportedAtUtc", Instant.ofEpochMilli(exportedAt).toString())
        // Meetings first: tasks refer to them, and this way the file reads in the
        // same order it is imported.
        root.put("meetings", snapshot.meetings.toArray(::encodeMeeting))
        root.put("tasks", snapshot.tasks.toArray(::encodeTask))
        root.put("logEntries", snapshot.logEntries.toArray(::encodeLogEntry))
        root.put("passes", snapshot.passes.toArray(::encodePass))
        return root.toString(2)
    }

    /** Writes the document as UTF-8. Closing [out] is the caller's job. */
    fun write(
        snapshot: Snapshot,
        out: OutputStream,
        exportedAt: Long = System.currentTimeMillis(),
    ) {
        out.write(encode(snapshot, exportedAt).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    private fun <T> List<T>.toArray(encode: (T) -> JSONObject): JSONArray {
        val array = JSONArray()
        forEach { array.put(encode(it)) }
        return array
    }

    private fun encodeTask(task: Task): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("title", task.title)
        putIfNotBlank("notes", task.notes)
        // Enum names rather than the stored integer: the file is meant to be read.
        put("priority", task.priority.name)
        put("status", task.status.storedValue)
        putIfNotNull("dueDate", Dates.store(task.dueDate))
        putIfNotNull("project", task.project)
        putIfNotNull("meetingId", task.meetingId)
        put("createdAt", task.createdAt)
        put("updatedAt", task.updatedAt)
        putIfNotNull("completedAt", task.completedAt)
    }

    private fun encodeLogEntry(entry: LogEntry): JSONObject = JSONObject().apply {
        put("id", entry.id)
        put("day", Dates.store(entry.day))
        put("body", entry.body)
        put("kind", entry.kind.storedValue)
        put("createdAt", entry.createdAt)
        put("updatedAt", entry.updatedAt)
    }

    private fun encodeMeeting(meeting: Meeting): JSONObject = JSONObject().apply {
        put("id", meeting.id)
        put("title", meeting.title)
        putIfNotBlank("attendees", meeting.attendees)
        put("day", Dates.store(meeting.day))
        putIfNotNull("startTime", Dates.store(meeting.startTime))
        putIfNotBlank("location", meeting.location)
        putIfNotBlank("notes", meeting.notes)
        putIfNotNull("nextTouch", Dates.store(meeting.nextTouch))
        put("followUpDone", meeting.followUpDone)
        put("createdAt", meeting.createdAt)
        put("updatedAt", meeting.updatedAt)
    }

    private fun encodePass(pass: Pass): JSONObject = JSONObject().apply {
        put("id", pass.id)
        put("title", pass.title)
        put("category", pass.category.storedValue)
        putIfNotBlank("barcodeValue", pass.barcodeValue)
        putIfNotBlank("barcodeFormat", pass.barcodeFormat)
        putIfNotBlank("ocrText", pass.ocrText)
        putIfNotBlank("notes", pass.notes)
        putIfNotNull("expiryDate", Dates.store(pass.expiryDate))
        putIfNotNull("balance", pass.balance)
        putIfNotNull("imagePath", pass.imagePath)
        put("createdAt", pass.createdAt)
        put("updatedAt", pass.updatedAt)
    }

    // Empty and null are the same thing here, and leaving the key out entirely
    // keeps the file short and readable rather than full of "" and null.
    private fun JSONObject.putIfNotBlank(key: String, value: String) {
        if (value.isNotBlank()) put(key, value)
    }

    private fun JSONObject.putIfNotNull(key: String, value: Any?) {
        if (value != null) put(key, value)
    }

    // ---- Reading ---------------------------------------------------------

    /** Reads the whole document as UTF-8. Closing [input] is the caller's job. */
    fun read(input: InputStream): Snapshot = decode(input.readBytes().toString(Charsets.UTF_8))

    fun decode(text: String): Snapshot {
        val root = try {
            // A backup is meant to be hand-editable, and Notepad on Windows writes
            // UTF-8 with a byte order mark. Left in place that single invisible
            // character makes the whole file "not JSON", so drop it before parsing.
            JSONObject(text.removePrefix(BOM).trim())
        } catch (_: JSONException) {
            throw BackupFormatException("That file is not JSON.")
        }

        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("That file is not a Daybook backup.")
        }
        // Falls back to the first format rather than to the current one. A file with
        // no version key is old, not new, and assuming "current" would quietly let a
        // future reader treat a v1 file as though it had every later field.
        val version = root.optInt("version", FIRST_VERSION)
        if (version > VERSION) {
            throw BackupFormatException(
                "That backup was written by a newer version of Daybook (format $version).",
            )
        }

        return Snapshot(
            tasks = root.rows("tasks", ::decodeTask),
            logEntries = root.rows("logEntries", ::decodeLogEntry),
            meetings = root.rows("meetings", ::decodeMeeting),
            passes = root.rows("passes", ::decodePass),
        )
    }

    /** Decodes one array, dropping any row [decode] rejects. */
    private fun <T> JSONObject.rows(key: String, decode: (JSONObject) -> T?): List<T> {
        val array = optJSONArray(key) ?: return emptyList()
        val rows = ArrayList<T>(array.length())
        for (index in 0 until array.length()) {
            val row = array.optJSONObject(index) ?: continue
            decode(row)?.let { rows += it }
        }
        return rows
    }

    /** Null when there is no title, since a task without one cannot be shown or edited. */
    private fun decodeTask(json: JSONObject): Task? {
        val title = json.text("title")
        if (title.isBlank()) return null
        return Task(
            id = json.optLong("id", 0L),
            title = title,
            notes = json.text("notes"),
            priority = priorityOf(json.stringOrNull("priority")),
            status = TaskStatus.fromStored(json.stringOrNull("status")?.uppercase()),
            dueDate = Dates.parseDate(json.stringOrNull("dueDate")),
            project = json.stringOrNull("project"),
            meetingId = json.longOrNull("meetingId"),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
            completedAt = json.longOrNull("completedAt"),
        )
    }

    private fun decodeLogEntry(json: JSONObject): LogEntry? {
        val day = Dates.parseDate(json.stringOrNull("day")) ?: return null
        val body = json.text("body")
        if (body.isBlank()) return null
        return LogEntry(
            id = json.optLong("id", 0L),
            day = day,
            body = body,
            kind = LogKind.fromStored(json.stringOrNull("kind")?.uppercase()),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }

    private fun decodeMeeting(json: JSONObject): Meeting? {
        val day = Dates.parseDate(json.stringOrNull("day")) ?: return null
        val title = json.text("title")
        if (title.isBlank()) return null
        return Meeting(
            id = json.optLong("id", 0L),
            title = title,
            attendees = json.text("attendees"),
            day = day,
            startTime = Dates.parseTime(json.stringOrNull("startTime")),
            location = json.text("location"),
            notes = json.text("notes"),
            nextTouch = Dates.parseDate(json.stringOrNull("nextTouch")),
            followUpDone = json.optBoolean("followUpDone", false),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }

    private fun decodePass(json: JSONObject): Pass? {
        val title = json.text("title")
        if (title.isBlank()) return null
        return Pass(
            id = json.optLong("id", 0L),
            title = title,
            category = PassCategory.fromStored(json.stringOrNull("category")),
            barcodeValue = json.text("barcodeValue"),
            barcodeFormat = json.text("barcodeFormat"),
            ocrText = json.text("ocrText"),
            notes = json.text("notes"),
            expiryDate = Dates.parseDate(json.stringOrNull("expiryDate")),
            balance = json.stringOrNull("balance"),
            imagePath = json.stringOrNull("imagePath"),
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }

    /** Accepts the enum name, and an old-style stored integer, and falls back to medium. */
    private fun priorityOf(value: String?): Priority =
        Priority.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: value?.toIntOrNull()?.let { Priority.fromStored(it) }
            ?: Priority.MEDIUM

    // isNull() covers both "key absent" and "key present but JSON null", which is
    // what makes a hand-edited or partial file safe to read.
    private fun JSONObject.text(key: String): String = if (isNull(key)) "" else optString(key)

    private fun JSONObject.stringOrNull(key: String): String? = text(key).takeIf { it.isNotBlank() }

    private fun JSONObject.longOrNull(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    companion object {
        /** Identifies the file as ours. Absent or different means "not a Daybook backup". */
        const val FORMAT: String = "daybook-backup"

        /** Bumped only for a change a v1 reader could not handle. */
        const val VERSION: Int = 1

        /** The oldest format this reader understands, used when a file states none. */
        const val FIRST_VERSION: Int = 1

        const val MIME_TYPE: String = "application/json"

        /** UTF-8 byte order mark, written as an escape so it is visible in source. */
        private const val BOM: String = "\uFEFF"

        /** e.g. "daybook-backup-2026-09-03.json", offered when picking where to save. */
        fun suggestedFileName(day: LocalDate): String = "daybook-backup-${Dates.store(day)}.json"
    }
}
