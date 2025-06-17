package io.github.meatwo310.m2bot.extensions.reminder

import dev.kord.common.entity.AllowedMentionType
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.scheduling.Scheduler
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.Clock
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
                if (!event.message.mentionedUserIds.contains(kord.selfId)) return@action
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
            seconds = 10,
            startNow = true,
            name = "check-reminder",
            repeat = true,
        ) {
            val now = Clock.System.now()
            val reminders = reminderStorage.getDueReminders(now)

            if (reminders.isEmpty()) {
                return@schedule
            }

            logger.info { "Found ${reminders.size} reminders due." }

            reminders.forEach { reminder ->
                logger.info { "Processing reminder for user ${reminder.userId} in channel ${reminder.channelId}." }

                val channel = kord.getChannel(reminder.channelId)

                channel?.asChannelOfOrNull<GuildMessageChannel>()?.let {
                    it.createMessage {
                        content = "<@${reminder.userId}> リマインドです！\n-# <t:${reminder.scheduledAt.epochSeconds}:R> に登録されたリマインダー"
                        allowedMentions {
                            add(AllowedMentionType.UserMentions)
                        }
                    }
                } ?: run {
                    logger.error { "Channel ${reminder.channelId} is not a text-based channel." }
                    return@schedule
                }

                reminderStorage.removeReminder(reminder)
            }
        }
    }
}
