package io.github.meatwo310.m2bot.extensions.reminder

import dev.kord.common.entity.Snowflake
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ReminderExtensionKotlinTest {
    
    @Test
    fun testAddReminderFromJavaWithValidData() {
        val result = ReminderExtension.addReminderFromJava(
            guildId = Snowflake(123456789L),
            channelId = Snowflake(987654321L),
            messageId = Snowflake(111222333L),
            userId = Snowflake(444555666L),
            scheduledAtIsoString = "2025-12-25T10:00:00",
            message = "Test reminder from Java"
        )
        
        assertTrue(result.contains("Reminder set for"))
        assertTrue(result.contains("Test reminder from Java"))
    }
    
    @Test
    fun testAddReminderFromJavaWithInvalidDate() {
        val result = ReminderExtension.addReminderFromJava(
            guildId = Snowflake(123456789L),
            channelId = Snowflake(987654321L), 
            messageId = Snowflake(111222333L),
            userId = Snowflake(444555666L),
            scheduledAtIsoString = "invalid-date",
            message = "Test reminder"
        )
        
        assertTrue(result.contains("Error setting reminder"))
    }
    
    @Test
    fun testReminderStorageIsAccessible() {
        val storage = ReminderExtension.reminderStorage
        assertNotNull(storage)
    }
}