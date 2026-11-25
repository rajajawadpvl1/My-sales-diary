package com.example.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/**
 * Provides the current device-local calendar date and epoch timestamp.
 * Injectable so tests can advance dates deterministically without mocking system time.
 */
interface ClockProvider {
    fun todayIso(): String
    fun nowMillis(): Long
}

object SystemClockProvider : ClockProvider {
    override fun todayIso(): String = LocalDateHelper.todayIso()
    override fun nowMillis(): Long = System.currentTimeMillis()
}

/**
 * Timezone-safe calendar date utilities operating strictly in the phone's local timezone
 * and storing dates canonically as ISO "yyyy-MM-dd" strings so UTC offsets never shift days.
 */
object LocalDateHelper {

    private val isoRegex = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val timeRegex = Regex("""^\d{2}:\d{2}$""")

    fun todayIso(timeZone: TimeZone = TimeZone.getDefault()): String {
        val cal = Calendar.getInstance(timeZone)
        return formatIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun formatIso(year: Int, month1Based: Int, dayOfMonth: Int): String {
        return String.format(Locale.US, "%04d-%02d-%02d", year, month1Based, dayOfMonth)
    }

    fun isValidIsoDate(isoDate: String?): Boolean {
        if (isoDate == null || !isoRegex.matches(isoDate)) return false
        val parts = isoDate.split("-")
        val y = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        val d = parts[2].toIntOrNull() ?: return false
        if (m !in 1..12 || d !in 1..31) return false
        val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
            isLenient = false
            clear()
            set(y, m - 1, d, 12, 0, 0)
        }
        return try {
            cal.timeInMillis
            cal.get(Calendar.YEAR) == y &&
                cal.get(Calendar.MONTH) == m - 1 &&
                cal.get(Calendar.DAY_OF_MONTH) == d
        } catch (_: Exception) {
            false
        }
    }

    fun isValidTime(hhmm: String?): Boolean {
        if (hhmm == null || !timeRegex.matches(hhmm)) return false
        val parts = hhmm.split(":")
        val h = parts[0].toIntOrNull() ?: return false
        val m = parts[1].toIntOrNull() ?: return false
        return h in 0..23 && m in 0..59
    }

    /**
     * Adds exact calendar days (respecting leap years, month ends, and DST) to an ISO date.
     */
    fun addDays(isoDate: String, days: Int): String {
        val cal = parseToNoonCalendar(isoDate) ?: Calendar.getInstance(TimeZone.getDefault())
        cal.add(Calendar.DAY_OF_YEAR, days)
        return formatIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    /**
     * Returns calendar days from [fromIso] to [toIso]. Positive if [toIso] is after [fromIso].
     */
    fun daysBetween(fromIso: String, toIso: String): Int {
        val c1 = parseToUtcNoonCalendar(fromIso) ?: return 0
        val c2 = parseToUtcNoonCalendar(toIso) ?: return 0
        val diffMillis = c2.timeInMillis - c1.timeInMillis
        return (diffMillis.toDouble() / 86_400_000.0).roundToInt()
    }

    /**
     * Material 3 DatePicker returns UTC midnight millis for the picked calendar date.
     * Extract year/month/day in UTC so local negative/positive timezone offsets never shift the day.
     */
    fun pickerUtcMillisToIsoDate(utcMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = utcMillis
        }
        return formatIso(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
        )
    }

    fun isoDateToPickerUtcMillis(isoDate: String): Long? {
        if (!isValidIsoDate(isoDate)) return null
        val parts = isoDate.split("-")
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        }
        return cal.timeInMillis
    }

    fun formatDisplayDate(isoDate: String): String {
        val cal = parseToNoonCalendar(isoDate) ?: return isoDate
        val formatter = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        formatter.timeZone = cal.timeZone
        return formatter.format(cal.time)
    }

    fun formatShortDate(isoDate: String): String {
        val cal = parseToNoonCalendar(isoDate) ?: return isoDate
        val formatter = SimpleDateFormat("MMM d", Locale.getDefault())
        formatter.timeZone = cal.timeZone
        return formatter.format(cal.time)
    }

    fun formatRelativeDate(isoDate: String, todayIso: String): String {
        val delta = daysBetween(todayIso, isoDate)
        return when {
            delta == 0 -> "Today"
            delta == 1 -> "Tomorrow"
            delta == -1 -> "1 day overdue"
            delta < -1 -> "${-delta} days overdue"
            delta in 2..6 -> "In $delta days (${formatShortDate(isoDate)})"
            else -> formatDisplayDate(isoDate)
        }
    }

    fun formatTime(hour24: Int, minute: Int): String {
        return String.format(Locale.US, "%02d:%02d", hour24.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    fun parseTime(hhmm: String?): Pair<Int, Int> {
        if (!isValidTime(hhmm)) return 10 to 0
        val parts = hhmm!!.split(":")
        return parts[0].toInt() to parts[1].toInt()
    }

    fun formatTimeDisplay(hhmm: String?): String? {
        if (!isValidTime(hhmm)) return null
        val (hour24, minute) = parseTime(hhmm)
        val suffix = if (hour24 >= 12) "PM" else "AM"
        val hour12 = when {
            hour24 == 0 -> 12
            hour24 > 12 -> hour24 - 12
            else -> hour24
        }
        return String.format(Locale.US, "%d:%02d %s", hour12, minute, suffix)
    }

    fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        val hasLeadingPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasLeadingPlus && digits.isNotEmpty()) "+$digits" else digits
    }

    private fun parseToNoonCalendar(isoDate: String): Calendar? {
        if (!isValidIsoDate(isoDate)) return null
        val parts = isoDate.split("-")
        return Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 12, 0, 0)
        }
    }

    private fun parseToUtcNoonCalendar(isoDate: String): Calendar? {
        if (!isValidIsoDate(isoDate)) return null
        val parts = isoDate.split("-")
        return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 12, 0, 0)
        }
    }
}
