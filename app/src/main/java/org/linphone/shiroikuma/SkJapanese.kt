package org.linphone.shiroikuma

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * shiroikuma-rindenwa fork — Japanese readings for the call log.
 *
 * Ported from the sister dialer (shiroikuma-denwa, `extensions/Long.kt`) so both apps render a
 * call history the same way: Sino-Japanese clock readings, kanji durations in full-width
 * parentheses and imperial-era (和暦) day headlines.
 *
 * Every entry point takes epoch **milliseconds**; the Linphone SDK hands out seconds, so
 * [SkCallLog] does that conversion once at the boundary.
 */
object SkJapanese {

    private val DIGITS = arrayOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    /**
     * A time of day as a Sino-Japanese clock reading: 14:53 → 午後二時五十三分, 9:30 → 午前九時半.
     * A whole hour drops the minute part, :30 becomes 半, and noon / midnight get the special
     * words 正午 / 正子.
     */
    fun clock(millis: Long): String {
        val time = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        val hour = time.hour
        val minute = time.minute
        when {
            hour == 12 && minute == 0 -> return "正午"
            hour == 12 && minute == 30 -> return "正午半"
            hour == 0 && minute == 0 -> return "正子"
            hour == 0 && minute == 30 -> return "正子半"
        }

        val period = if (hour < 12) "午前" else "午後"
        val hour12 = when {
            hour == 0 -> 12
            hour <= 12 -> hour
            else -> hour - 12
        }
        val minutePart = when (minute) {
            0 -> ""
            30 -> "半"
            else -> "${kanjiNumeral(minute)}分"
        }
        return "$period${kanjiNumeral(hour12)}時$minutePart"
    }

    /**
     * A duration in seconds as kanji wrapped in full-width parentheses: 6 → （六秒）,
     * 210 → （三分半）, 403 → （六分四十三秒）. A trailing 30s / 30m becomes 半 of the unit above it.
     */
    fun duration(seconds: Int): String {
        val core = if (seconds <= 0) {
            "零秒"
        } else {
            val hours = seconds / 3600
            val minutes = seconds % 3600 / 60
            val secs = seconds % 60
            buildString {
                if (hours > 0) append("${kanjiNumeral(hours)}時間")
                if (hours > 0 && minutes == 30 && secs == 0) {
                    append("半")
                } else {
                    if (minutes > 0) append("${kanjiNumeral(minutes)}分")
                    if (minutes > 0 && secs == 30) {
                        append("半")
                    } else if (secs > 0) {
                        append("${kanjiNumeral(secs)}秒")
                    }
                }
            }
        }
        return "（$core）"
    }

    /**
     * A day as an imperial-era (和暦) date: 令和八年七月三十日（木曜日）. The era is resolved from
     * fixed boundaries rather than through `java.time.chrono.JapaneseEra`, so the output is fully
     * ours and cannot shift with a platform's era table.
     */
    fun imperialDate(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        val (era, baseYear) = when {
            !date.isBefore(LocalDate.of(2019, 5, 1)) -> "令和" to 2018
            !date.isBefore(LocalDate.of(1989, 1, 8)) -> "平成" to 1988
            !date.isBefore(LocalDate.of(1926, 12, 25)) -> "昭和" to 1925
            !date.isBefore(LocalDate.of(1912, 7, 30)) -> "大正" to 1911
            else -> "明治" to 1867
        }
        val eraYear = date.year - baseYear
        // The first year of an era is written 元年, never 一年.
        val year = if (eraYear == 1) "元" else kanjiNumeral(eraYear)
        return "$era${year}年${kanjiNumeral(date.monthValue)}月${kanjiNumeral(date.dayOfMonth)}日" +
            "（${weekday(date)}曜日）"
    }

    /**
     * The same day in the Common Era, still written in Japanese: 二〇二六年七月三十日（木曜日）.
     * The year is spelled digit by digit, which is how a four-digit year actually reads.
     */
    fun commonEraDate(millis: Long): String {
        val date = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        val year = date.year.toString().map { digit ->
            if (digit == '0') "〇" else DIGITS[digit - '0']
        }.joinToString("")
        return "${year}年${kanjiNumeral(date.monthValue)}月${kanjiNumeral(date.dayOfMonth)}日" +
            "（${weekday(date)}曜日）"
    }

    private fun weekday(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "月"
        DayOfWeek.TUESDAY -> "火"
        DayOfWeek.WEDNESDAY -> "水"
        DayOfWeek.THURSDAY -> "木"
        DayOfWeek.FRIDAY -> "金"
        DayOfWeek.SATURDAY -> "土"
        DayOfWeek.SUNDAY -> "日"
    }

    /**
     * 1..99 as everyday kanji numerals (29 → 二十九). That covers every era year (≤ 64), month,
     * day, minute and second a call log can show; larger values fall back to plain digits.
     */
    fun kanjiNumeral(value: Int): String {
        if (value <= 0) return "〇"
        if (value >= 100) return value.toString()

        val tens = value / 10
        val ones = value % 10
        return buildString {
            when (tens) {
                0 -> {}
                1 -> append("十")
                else -> append(DIGITS[tens]).append("十")
            }
            if (ones != 0) append(DIGITS[ones])
        }
    }
}
