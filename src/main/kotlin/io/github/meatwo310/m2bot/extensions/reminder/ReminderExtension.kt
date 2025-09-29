package io.github.meatwo310.m2bot.extensions.reminder

import dev.kord.common.entity.AllowedMentionType
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.asChannelOfOrNull
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.channel.GuildMessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.allowedMentions
import dev.kordex.core.checks.isNotBot
import dev.kordex.core.checks.userFor
import dev.kordex.core.extensions.Extension
import dev.kordex.core.extensions.event
import dev.kordex.core.utils.scheduling.Scheduler
import io.github.meatwo310.m2bot.extensions.preferences.PreferencesExtension
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import java.sql.Timestamp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class ReminderExtension : Extension(), IMessageDateTimeParser {
    override val name = "reminder"
    val logger = KotlinLogging.logger {}

    private val scheduler = Scheduler()
    private val reminderStorage = ReminderStorage()

    companion object {
        val reminderStorage = ReminderStorage()
        private var instance: ReminderExtension? = null
        
        fun getInstance(): ReminderExtension? = instance
        
        internal fun setInstance(ext: ReminderExtension) {
            instance = ext
        }
    }

    suspend fun addReminder(reminderData: ReminderData) {
        Companion.reminderStorage.addReminder(reminderData)
    }

    suspend fun addReminder(
        guildId: Snowflake?,
        channelId: Snowflake,
        messageId: Snowflake,
        userId: Snowflake?,
        scheduledAtIsoString: String,
        message: String,
        createdAt: Instant = Clock.System.now()
    ): String {
        return try {
            val scheduledAt = kotlinx.datetime.LocalDateTime.parse(scheduledAtIsoString)
                .toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())
                
            val reminderData = ReminderData(
                guildId = guildId,
                channelId = channelId,
                messageId = messageId,
                userId = userId,
                scheduledAt = scheduledAt,
                message = message,
                createdAt = createdAt
            )
            Companion.reminderStorage.addReminder(reminderData)
            "Reminder set for ${scheduledAtIsoString}: ${message}"
        } catch (e: Exception) {
            "Error setting reminder: ${e.message}"
        }
    }

    override suspend fun setup() {
        // Set this instance in companion object for Java access
        Companion.setInstance(this)
        
        event<MessageCreateEvent> {
            check {
                isNotBot()
                failIfNot(event.message.mentionedUserIds.contains(kord.selfId))
                failIfNot(event.message.content.contains(Regex("""([rR]emind|リマイン[ドダ])""")))
                userFor(event)?.id?.let {
                    failIf(PreferencesExtension.preferencesStorage.getOrDefault(it).enableAI)
                } ?: fail()
            }

            action {
                val localDateTime = event.message.content.parseMessageDateTime()
                if (localDateTime == null) {
                    event.message.reply {
                        content = "日時を指定してください"
                        allowedMentions {}
                    }
                    return@action
                }

                val timeStamp = Timestamp.valueOf(localDateTime.toJavaLocalDateTime())
                if (timeStamp.time < System.currentTimeMillis()) {
                    event.message.reply {
                        content = "過去の日時 <t:${timeStamp.time / 1000}:R> は指定できません"
                        allowedMentions {}
                    }
                    return@action
                }

                addReminder(
                    guildId = event.guildId,
                    channelId = event.message.channelId,
                    messageId = event.message.id,
                    userId = event.message.author?.id,
                    scheduledAt = Instant.fromEpochMilliseconds(timeStamp.time),
                    message = event.message.content,
                    createdAt = event.message.timestamp,
                )

                val t = timeStamp.time / 1000

                event.message.reply {
                    content = "<t:${t}:R> にリマインダーを設定しました。"
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
            val reminders = Companion.reminderStorage.getDueReminders(now)

            if (reminders.isEmpty()) {
                return@schedule
            }

            logger.info { "Found ${reminders.size} reminders due." }

            reminders.forEach { reminder ->
                logger.info { "Processing reminder for user ${reminder.userId} in channel ${reminder.channelId}." }

                val channel = kord.getChannel(reminder.channelId)

                channel?.asChannelOfOrNull<GuildMessageChannel>()?.let {
                    it.createMessage {
                        content = "<@${reminder.userId}> リマインドです！\n-# <t:${reminder.createdAt.epochSeconds}:R> に登録されました"
                        allowedMentions {
                            add(AllowedMentionType.UserMentions)
                        }
                    }
                } ?: run {
                    logger.error { "Channel ${reminder.channelId} is not a text-based channel." }
                    return@schedule
                }

                Companion.reminderStorage.removeReminder(reminder)
            }
        }
    }
}
