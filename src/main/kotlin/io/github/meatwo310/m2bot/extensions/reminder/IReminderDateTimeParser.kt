package io.github.meatwo310.m2bot.extensions.reminder

import kotlinx.datetime.*

interface IMessageDateTimeParser {
    companion object {
        val yearPattern = """(\d\d\d\d)年""".toRegex()
        val monthPattern = """(\d|1[0-2])月""".toRegex()
        val dayPattern = """([0-2]?[0-9]|3[0-1])日""".toRegex()
        val inDaysPattern = """([1-9][0-9]*)日後""".toRegex()
        val hourPattern = """([0-2]?[0-9])時""".toRegex()
        val minutePattern = """([0-5]?[0-9])分""".toRegex()

        val tomorrowPattern = """明日|あした|tomorrow""".toRegex()
        val twoDaysPattern = """明後日|あさって""".toRegex()

        val fullTimeYearPattern = """(\d\d\d\d)/(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{1,2})""".toRegex()
        val fullTimeMonthPattern = """(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{1,2})""".toRegex()
        val fullTimeHourPattern = """(\d{1,2}):(\d{1,2})""".toRegex()
    }

    fun String.parseMessageDateTime(): LocalDateTime? {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        val currentYear = now.year
        val currentMonth = now.month.number
        val currentDay = now.dayOfMonth
        val currentHour = now.hour
        val currentMinute = now.minute

        var year = now.year
        var month = now.month.number
        var day = now.dayOfMonth
        var hour = now.hour
        var minute = now.minute

        // 年の処理
        yearPattern.find(this)?.let {
            year = it.groupValues[1].toInt()
        }

        // 月の処理
        monthPattern.find(this)?.let {
            month = it.groupValues[1].toInt()
        }

        // 日の処理
        when {
            tomorrowPattern.containsMatchIn(this) -> day += 1
            twoDaysPattern.containsMatchIn(this) -> day += 2
            else -> {
                inDaysPattern.find(this)?.let {
                    day += it.groupValues[1].toInt()
                } ?: dayPattern.find(this)?.let {
                    day = it.groupValues[1].toInt()
                }
            }
        }

        // 時の処理
        hourPattern.find(this)?.let {
            hour = it.groupValues[1].toInt()
            minute = 0
            if (now.dayOfMonth == day && now.hour >= 12 && hour < 12) {
                hour += 12
            }
            while (hour > 23) {
                hour -= 24
                day += 1
            }
        }

        // 分の処理
        minutePattern.find(this)?.let {
            minute = it.groupValues[1].toInt()
        }

        // フルタイムフォーマットの処理
        fullTimeYearPattern.find(this)?.let {
            return LocalDateTime(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                it.groupValues[4].toInt(),
                it.groupValues[5].toInt(),
                0,
                0
            )
        }

        fullTimeMonthPattern.find(this)?.let {
            return LocalDateTime(
                year,
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                it.groupValues[4].toInt(),
                0,
                0
            )
        }

        fullTimeHourPattern.find(this)?.let {
            return LocalDateTime(
                year,
                month,
                day,
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                0,
                0
            )
        }

        val currentDateTime = LocalDateTime(currentYear, currentMonth, currentDay, currentHour, currentMinute, 0, 0)
        val resultDateTime = LocalDateTime(year, month, day, hour, minute, 0, 0)

        return if (currentDateTime == resultDateTime) null
        else resultDateTime
    }
}
