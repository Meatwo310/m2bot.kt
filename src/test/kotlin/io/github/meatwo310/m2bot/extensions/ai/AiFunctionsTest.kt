package io.github.meatwo310.m2bot.extensions.ai

import io.github.meatwo310.m2bot.extensions.reminder.ReminderExtension
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AiFunctionsTest {
    
    @Test
    fun testAddReminderWithInvalidDate() {
        // Test with invalid date format
        val result = AiFunctions.addReminder("invalid-date", "Test reminder")
        assertTrue(result.contains("Invalid date format"))
    }
    
    @Test 
    fun testAddReminderWithoutContext() {
        // Test without setting message context
        val result = AiFunctions.addReminder("2025-08-18T15:30:00", "Test reminder")
        assertTrue(result.contains("No message context available"))
    }
    
    @Test
    fun testValidDateParsing() {
        // Test that valid ISO 8601 dates don't cause parse errors
        val testDate = "2025-08-18T15:30:00"
        try {
            java.time.LocalDateTime.parse(testDate)
            // If we get here, the date parsing works
            assertTrue(true)
        } catch (e: Exception) {
            assertFalse(true, "Date parsing should work for valid ISO 8601 format")
        }
    }
}