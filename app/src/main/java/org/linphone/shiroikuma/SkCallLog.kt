package org.linphone.shiroikuma

import android.content.Context
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import org.linphone.R

/**
 * shiroikuma-rindenwa fork — how a call-history record reads.
 *
 * The stock list gives every row one grey `07/30 | 午後8:47` line. This module replaces that with
 * the sister dialer's layout: the day becomes an underlined headline above its calls, and each row
 * carries only its own time and duration, in whichever of the formats below 白い熊 has picked on
 * the UI page. Every knob is a `sk_` preference, so it travels with the Appearance export.
 *
 * All timestamps arriving here are **epoch seconds** — the unit the Linphone SDK uses for call
 * logs; [SkJapanese] is fed milliseconds after the conversion in [millisOf].
 */
object SkCallLog {

    // Preference keys. The sk_ prefix is what puts them in the Appearance export.
    private const val KEY_DAY_HEADERS = "sk_call_day_headers"
    private const val KEY_RELATIVE_DAYS = "sk_call_relative_days"
    private const val KEY_DATE_FORMAT = "sk_call_date_format"
    private const val KEY_TIME_FORMAT = "sk_call_time_format"
    private const val KEY_SHOW_DURATION = "sk_call_show_duration"
    private const val KEY_DURATION_FORMAT = "sk_call_duration_format"
    private const val KEY_SHOW_DIRECTION = "sk_call_show_direction"

    /** The separator drawn between the time and the duration, as in the sister dialer. */
    const val DURATION_SEPARATOR = "•"

    /** How the day headline (and, with headlines off, the row's own date) is written. */
    enum class DateFormatOption(val labelRes: Int) {
        IMPERIAL(R.string.sk_call_date_imperial),
        COMMON_ERA(R.string.sk_call_date_common_era),
        SYSTEM(R.string.sk_call_format_system),
    }

    /** How a call's time of day is written. */
    enum class TimeFormatOption(val labelRes: Int) {
        JAPANESE(R.string.sk_call_format_japanese),
        SYSTEM(R.string.sk_call_format_system),
        HOUR_24(R.string.sk_call_time_24h),
        HOUR_12(R.string.sk_call_time_12h),
    }

    /** How a call's duration is written. */
    enum class DurationFormatOption(val labelRes: Int) {
        JAPANESE(R.string.sk_call_format_japanese),
        DIGITAL(R.string.sk_call_duration_digital),
    }

    /** Which arrow a record carries — each kind has its own colour slot. */
    enum class Direction { INCOMING, OUTGOING, MISSED }

    // ------------------------------------------------------------------ settings

    fun dayHeadersEnabled(context: Context): Boolean =
        SkTheme.prefs(context).getBoolean(KEY_DAY_HEADERS, true)

    fun setDayHeadersEnabled(context: Context, value: Boolean) {
        SkTheme.prefs(context).edit().putBoolean(KEY_DAY_HEADERS, value).apply()
    }

    /** Whether today and yesterday are named instead of dated. */
    fun relativeDaysEnabled(context: Context): Boolean =
        SkTheme.prefs(context).getBoolean(KEY_RELATIVE_DAYS, true)

    fun setRelativeDaysEnabled(context: Context, value: Boolean) {
        SkTheme.prefs(context).edit().putBoolean(KEY_RELATIVE_DAYS, value).apply()
    }

    fun dateFormat(context: Context): DateFormatOption =
        DateFormatOption.entries.getOrElse(
            SkTheme.prefs(context).getInt(KEY_DATE_FORMAT, 0),
        ) { DateFormatOption.IMPERIAL }

    fun setDateFormat(context: Context, option: DateFormatOption) {
        SkTheme.prefs(context).edit().putInt(KEY_DATE_FORMAT, option.ordinal).apply()
    }

    fun timeFormat(context: Context): TimeFormatOption =
        TimeFormatOption.entries.getOrElse(
            SkTheme.prefs(context).getInt(KEY_TIME_FORMAT, 0),
        ) { TimeFormatOption.JAPANESE }

    fun setTimeFormat(context: Context, option: TimeFormatOption) {
        SkTheme.prefs(context).edit().putInt(KEY_TIME_FORMAT, option.ordinal).apply()
    }

    fun durationShown(context: Context): Boolean =
        SkTheme.prefs(context).getBoolean(KEY_SHOW_DURATION, true)

    fun setDurationShown(context: Context, value: Boolean) {
        SkTheme.prefs(context).edit().putBoolean(KEY_SHOW_DURATION, value).apply()
    }

    fun durationFormat(context: Context): DurationFormatOption =
        DurationFormatOption.entries.getOrElse(
            SkTheme.prefs(context).getInt(KEY_DURATION_FORMAT, 0),
        ) { DurationFormatOption.JAPANESE }

    fun setDurationFormat(context: Context, option: DurationFormatOption) {
        SkTheme.prefs(context).edit().putInt(KEY_DURATION_FORMAT, option.ordinal).apply()
    }

    fun directionShown(context: Context): Boolean =
        SkTheme.prefs(context).getBoolean(KEY_SHOW_DIRECTION, true)

    fun setDirectionShown(context: Context, value: Boolean) {
        SkTheme.prefs(context).edit().putBoolean(KEY_SHOW_DIRECTION, value).apply()
    }

    // ------------------------------------------------------------------ text

    /**
     * What a record's own time line says. With day headlines on that is only the time — the day is
     * already written above it; with them off the date is prepended, as the stock list did.
     */
    fun rowTimeText(context: Context, timestampSecs: Long): String {
        val time = timeText(context, timestampSecs)
        if (dayHeadersEnabled(context)) return time
        return "${dayText(context, timestampSecs)} $time"
    }

    fun timeText(context: Context, timestampSecs: Long): String {
        val millis = millisOf(timestampSecs)
        return when (timeFormat(context)) {
            TimeFormatOption.JAPANESE -> SkJapanese.clock(millis)
            TimeFormatOption.SYSTEM -> DateFormat.getTimeInstance(DateFormat.SHORT).format(
                Date(millis),
            )
            TimeFormatOption.HOUR_24 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(
                Date(millis),
            )
            TimeFormatOption.HOUR_12 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(
                Date(millis),
            )
        }
    }

    /**
     * The day headline: today and yesterday by name (when that is turned on), every older day in
     * the picked date format.
     */
    fun dayText(context: Context, timestampSecs: Long): String {
        if (relativeDaysEnabled(context)) {
            // Compared as calendar days, not as a 24-hour offset, so a DST change cannot make
            // yesterday's calls fall back to a dated headline.
            val day = localDate(timestampSecs)
            val today = LocalDate.now(ZoneId.systemDefault())
            when (day) {
                today -> return context.getString(R.string.today)
                today.minusDays(1) -> return context.getString(R.string.yesterday)
            }
        }

        val millis = millisOf(timestampSecs)
        return when (dateFormat(context)) {
            DateFormatOption.IMPERIAL -> SkJapanese.imperialDate(millis)
            DateFormatOption.COMMON_ERA -> SkJapanese.commonEraDate(millis)
            DateFormatOption.SYSTEM -> DateFormat.getDateInstance(DateFormat.FULL).format(
                Date(millis),
            )
        }
    }

    /** Empty when the duration is not worth showing (a call that never connected, or turned off). */
    fun durationText(context: Context, seconds: Int, connected: Boolean): String {
        if (!durationShown(context) || !connected || seconds <= 0) return ""
        return when (durationFormat(context)) {
            DurationFormatOption.JAPANESE -> SkJapanese.duration(seconds)
            DurationFormatOption.DIGITAL -> digitalDuration(seconds)
        }
    }

    private fun digitalDuration(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = seconds % 3600 / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
        }
    }

    // ------------------------------------------------------------------ grouping

    /**
     * The local calendar day a record belongs to, as a sortable `yyyy-MM-dd` string — what decides
     * where one day headline ends and the next begins.
     */
    fun dayKey(timestampSecs: Long): String = localDate(timestampSecs).toString()

    private fun localDate(timestampSecs: Long): LocalDate =
        Instant.ofEpochMilli(millisOf(timestampSecs))
            .atZone(ZoneId.systemDefault())
            .toLocalDate()

    private fun millisOf(timestampSecs: Long): Long = timestampSecs * 1000L
}
