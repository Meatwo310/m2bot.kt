package io.github.meatwo310.m2bot.extensions

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.reply
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.*
import java.sql.Timestamp

class ReminderExtension : Extension() {
    override val name = "reminder"
    val logger = KotlinLogging.logger {}

    override suspend fun setup() {
        event<MessageCreateEvent> {
            action {
                if (event.message.author?.isBot == true) return@action
                if (!event.message.mentionedUserIds.contains(Snowflake(798821896179286036))) return@action
                if (!event.message.content.contains(Regex("""([rR]emind|リマイン[ドダ])"""))) return@action

                val localDateTime = getDateFromMessage(event.message.content)
                val timeStamp = Timestamp.valueOf(localDateTime.toJavaLocalDateTime())

                // TODO: リマインドを登録できる機能を実装する

                val t = timeStamp.time / 1000

                event.message.reply {
                    content = "<t:${t}:R> にリマインダーを設定しました。\n-# この機能は試験稼働中です！バグは Meatwo310 まで報告してください"
                    allowedMentions {}
                }
            }
        }
    }

    private fun getDateFromMessage(message: String): LocalDateTime {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

        var year = now.year
        var month = now.month.number
        var day = now.dayOfMonth
        var hour = now.hour
        var minute = now.minute

        val patternYear = """(\d\d\d\d)年""".toRegex()
        val patternMonth = """(\d|1[0-2])月""".toRegex()
        val patternDay = """([0-2]?[0-9]|3[0-1])日""".toRegex()
        val patternHour = """([0-2]?[0-9])時""".toRegex()
        val patternMinute = """([0-5]?[0-9])分""".toRegex()

        // 年の処理
        patternYear.find(message)?.let {
            year = it.groupValues[1].toInt()
        }

        // 月の処理
        patternMonth.find(message)?.let {
            month = it.groupValues[1].toInt()
        }

        // 日の処理
        when {
            message.contains(Regex("""明日|あした|tomorrow""")) -> day += 1
            message.contains("明後日") -> day += 2
            message.contains("一昨日") -> day -= 2
            message.contains("昨日") -> day -= 1
            else -> patternDay.find(message)?.let {
                day = it.groupValues[1].toInt()
            }
        }

        // 時の処理
        patternHour.find(message)?.let {
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
        patternMinute.find(message)?.let {
            minute = it.groupValues[1].toInt()
        }

        // フルタイムフォーマットの処理
        val fullTimeYearPattern = """(\d\d\d\d)/(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{1,2})""".toRegex()
        val fullTimeMonthPattern = """(\d{1,2})/(\d{1,2}) (\d{1,2}):(\d{1,2})""".toRegex()
        val fullTimeHourPattern = """(\d{1,2}):(\d{1,2})""".toRegex()

        fullTimeYearPattern.find(message)?.let {
            return LocalDateTime(
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                it.groupValues[4].toInt(),
                it.groupValues[5].toInt(),
                0, 0
            )
        }

        fullTimeMonthPattern.find(message)?.let {
            return LocalDateTime(
                year,
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                it.groupValues[3].toInt(),
                it.groupValues[4].toInt(),
                0, 0
            )
        }

        fullTimeHourPattern.find(message)?.let {
            return LocalDateTime(
                year,
                month,
                day,
                it.groupValues[1].toInt(),
                it.groupValues[2].toInt(),
                0, 0
            )
        }

        return LocalDateTime(year, month, day, hour, minute, 0, 0)
    }
}
