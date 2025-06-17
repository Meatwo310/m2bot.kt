package io.github.meatwo310.m2bot.extensions.reminder

import dev.kord.common.entity.Snowflake
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException

@Serializable
data class ReminderData(
    val guildId: Snowflake?,
    val channelId: Snowflake,
    val messageId: Snowflake,
    val userId: Snowflake?,
    val scheduledAt: Instant,
    val message: String,
)

class ReminderStorageException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class ReminderStorage {
    private val reminders = mutableListOf<ReminderData>()
    private val mutex = Mutex()
    private val storageFile = File("local/reminders.json")

    init {
        loadReminders()
    }

    private fun loadReminders() {
        try {
            if (!storageFile.exists()) {
                storageFile.parentFile.mkdirs()
                storageFile.createNewFile()
                saveReminders()
                return
            }
            val content = storageFile.readText()
            if (content.isNotEmpty()) {
                reminders.addAll(Json.decodeFromString(content))
            }
        } catch (e: IOException) {
            throw ReminderStorageException("リマインダーデータの読み込みに失敗しました", e)
        }
    }

    private fun saveReminders() {
        try {
            storageFile.parentFile.mkdirs()
            val json = Json { prettyPrint = true }
            storageFile.writeText(json.encodeToString(reminders))
        } catch (e: IOException) {
            throw ReminderStorageException("リマインダーデータの保存に失敗しました", e)
        }
    }

    suspend fun addReminder(reminder: ReminderData): ReminderData {
        mutex.withLock {
            reminders.add(reminder)
            saveReminders()
        }

        return reminder
    }

    suspend fun getDueReminders(now: Instant = Clock.System.now()): List<ReminderData> {
        return mutex.withLock {
            reminders.filter { it.scheduledAt <= now }
        }
    }

    suspend fun removeReminder(reminder: ReminderData) {
        mutex.withLock {
            reminders.remove(reminder)
            saveReminders()
        }
    }
}
