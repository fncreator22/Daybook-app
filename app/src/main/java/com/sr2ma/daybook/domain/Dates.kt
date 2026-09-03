package com.sr2ma.daybook.domain

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Date and time plumbing. minSdk is 26, so java.time is available on every
 * supported device and no core-library desugaring is needed.
 *
 * Everything is stored as a text ISO date ("2026-09-03") or "HH:mm" time, which
 * keeps backups human-readable and sorts correctly as a plain string in SQL.
 */
object Dates {

    // Stored values are machine values: they go into SQLite and into backup JSON, so
    // they are pinned to Locale.ROOT and never follow the device language.
    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE.withLocale(Locale.ROOT)
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    /** Reading side only: accepts what a person types ("9:30") as well as stored "09:30". */
    private val TIME_LENIENT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm", Locale.ROOT)

    /**
     * Display formatters are language-sensitive ("Sep" vs "sept."), so they have to
     * follow the *current* system language. Building them once at object init froze
     * whichever locale happened to be live then, which mixed languages inside one
     * string and kept stale month names after a language change. Resolved per call
     * instead, with a one-entry cache so a long list does not re-parse the patterns
     * for every row.
     */
    private class Display(val locale: Locale) {
        val dayMonth: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", locale)
        val dayMonthYear: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
        val weekdayLong: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", locale)
    }

    @Volatile
    private var displayCache: Display = Display(Locale.getDefault())

    private fun display(): Display {
        val locale = Locale.getDefault()
        val cached = displayCache
        return if (cached.locale == locale) cached else Display(locale).also { displayCache = it }
    }

    /** The device's current local date. Injected into pure logic rather than read inside it. */
    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)

    fun store(date: LocalDate?): String? = date?.format(DATE)

    fun store(time: LocalTime?): String? = time?.format(TIME)

    fun parseDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDate.parse(value, DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * Lenient on the way in, because this backs a typed field: surrounding spaces are
     * ignored and both "9:30" and "09:30" are accepted. Null, blank and genuinely
     * invalid text all come back as null, so callers can use it as validation.
     */
    fun parseTime(value: String?): LocalTime? {
        val text = value?.trim()
        if (text.isNullOrEmpty()) return null
        return try {
            LocalTime.parse(text, TIME_LENIENT)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /** "3 Sep", or "3 Sep 2025" once the year differs from [reference]. */
    fun shortLabel(date: LocalDate, reference: LocalDate): String {
        val formatters = display()
        return if (date.year == reference.year) {
            date.format(formatters.dayMonth)
        } else {
            date.format(formatters.dayMonthYear)
        }
    }

    fun weekdayLong(date: LocalDate): String = date.format(display().weekdayLong)

    /** Always the stored 24-hour form, which is also what the typed time field expects. */
    fun timeLabel(time: LocalTime): String = time.format(TIME)

    /** Whole days from [from] to [to]; negative when [to] is in the past. */
    fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

    /** Epoch millis of midnight on [date], for the Material date picker. */
    fun toUtcMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()

    /** Inverse of [toUtcMillis]. The picker hands back UTC midnight. */
    fun fromUtcMillis(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()

    fun localDateOf(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
}
