package io.github.meatwo310.m2bot.interfaces

import kotlinx.datetime.*

interface IMessageDateTimeParser {
    companion object {
        private val patternYear = """(\d\d\d\d)年""".toRegex()
        private val patternMonth = """(\d|1[0-2])月""".toRegex()
        private val patternDay = """([0-2]?[0-9]|3[0-1])日""".toRegex()
        private val patternHour = """([0-2]?[0-9])時""".toRegex()
        private val patternMinute = """([0-5]?[0-9])分""".toRegex()
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
        patternYear.find(this)?.let {
            year = it.groupValues[1].toInt()
        }

        // 月の処理
        patternMonth.find(this)?.let {
            month = it.groupValues[1].toInt()
        }

        // 日の処理
        when {
            this.contains(Regex("""明日|あした|tomorrow""")) -> day += 1
            this.contains("明後日") -> day += 2
            this.contains("一昨日") -> day -= 2
            this.contains("昨日") -> day -= 1
            else -> patternDay.find(this)?.let {
                day = it.groupValues[1].toInt()
            }
        }

        // 時の処理
        patternHour.find(this)?.let {
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
        patternMinute.find(this)?.let {
            minute = it.groupValues[1].toInt()
        }

        // フルタイムフォーマットの処理
        val fullTimeYearPattern = """(\d\d\d\d)/(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{1,2})""".toRegex()
        val fullTimeMonthPattern = """(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{1,2})""".toRegex()
        val fullTimeHourPattern = """(\d{1,2}):(\d{1,2})""".toRegex()

        fullTimeYearPattern.find(this)?.let {
            return LocalDateTime(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                it.groupValues[4].toInt(),
                it.groupValues[5].toInt(),
                0, 0
            )
        }

        fullTimeMonthPattern.find(this)?.let {
            return LocalDateTime(
                year,
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                it.groupValues[4].toInt(),
                0, 0
            )
        }

        fullTimeHourPattern.find(this)?.let {
            return LocalDateTime(
                year,
                month,
                day,
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                0, 0
            )
        }

        val currentDateTime = LocalDateTime(currentYear, currentMonth, currentDay, currentHour, currentMinute, 0, 0)
        val resultDateTime = LocalDateTime(year, month, day, hour, minute, 0, 0)

        return if (currentDateTime == resultDateTime) null
        else resultDateTime
    }

}
