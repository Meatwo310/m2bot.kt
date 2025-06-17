package io.github.meatwo310.m2bot.extensions.reminder

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.reply
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.scheduling.Scheduler
import io.github.meatwo310.m2bot.interfaces.IMessageDateTimeParser
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaLocalDateTime
import java.sql.Timestamp

class ReminderExtension : Extension(), IMessageDateTimeParser {
    override val name = "reminder"
    val logger = KotlinLogging.logger {}

    private val scheduler = Scheduler()
    private val reminderStorage = ReminderStorage()

    override suspend fun setup() {
        event<MessageCreateEvent> {
            action {
                if (event.message.author?.isBot == true) return@action
                if (!event.message.mentionedUserIds.contains(Snowflake(798821896179286036))) return@action
                if (!event.message.content.contains(Regex("""([rR]emind|リマイン[ドダ])"""))) return@action

                val localDateTime = event.message.content.parseMessageDateTime()
                if (localDateTime == null) {
                    event.message.reply {
                        content = "日時を指定してください"
                        allowedMentions {}
                    }
                    return@action
                }

                val timeStamp = Timestamp.valueOf(localDateTime.toJavaLocalDateTime())

                val reminderData = ReminderData(
                    guildId = event.guildId,
                    channelId = event.message.channelId,
                    messageId = event.message.id,
                    userId = event.message.author?.id,
                    scheduledAt = Instant.fromEpochMilliseconds(timeStamp.time),
                    message = event.message.content,
                )

                reminderStorage.addReminder(reminderData)

                val t = timeStamp.time / 1000

                event.message.reply {
                    content = "<t:${t}:R> にリマインダーを設定しました。\n-# この機能は試験稼働中です！"
                    allowedMentions {}
                }
            }
        }

        scheduler.schedule(
            seconds = 60,
            startNow = false,
            name = "check-reminder",
            repeat = true,
        ) {
            checkReminder()
        }
    }

    private fun checkReminder() {

    }
}
