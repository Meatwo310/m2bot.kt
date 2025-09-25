package io.github.meatwo310.m2bot.extensions.reminder

import dev.kord.common.entity.Snowflake
import io.github.meatwo310.m2bot.extensions.common.JsonStorage
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.InstantComponentSerializer
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
@Serializable
data class ReminderData(
    val guildId: Snowflake?,
    val channelId: Snowflake,
    val messageId: Snowflake,
    val userId: Snowflake?,
    @Serializable(with = InstantComponentSerializer::class)
    val scheduledAt: Instant,
    val message: String,
    @Serializable(with = InstantComponentSerializer::class)
    val createdAt: Instant,
)

@OptIn(ExperimentalTime::class)
class ReminderStorage : JsonStorage<ReminderData>() {
    override val storageFile get() = File("local/reminders.json")
    override val errorMessageLoad = "リマインダーデータの読み込みに失敗しました"
    override val errorMessageSave = "リマインダーデータの保存に失敗しました"

    override fun decodeFromString(content: String): List<ReminderData> {
        return Json.decodeFromString(content)
    }

    override fun encodeToString(data: List<ReminderData>): String {
        val json = Json { prettyPrint = true }
        return json.encodeToString(data)
    }

    suspend fun addReminder(reminder: ReminderData): ReminderData {
        withDataLock { data ->
            data.add(reminder)
        }
        return reminder
    }

    suspend fun getDueReminders(now: Instant = Clock.System.now()): List<ReminderData> {
        return readDataLock { data ->
            data.filter { it.scheduledAt <= now }
        }
    }

    suspend fun removeReminder(reminder: ReminderData) {
        withDataLock { data ->
            data.remove(reminder)
        }
    }
}
