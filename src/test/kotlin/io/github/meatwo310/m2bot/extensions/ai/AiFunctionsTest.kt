package io.github.meatwo310.m2bot.extensions.ai

import io.github.meatwo310.m2bot.extensions.reminder.ReminderExtension
import dev.kord.common.entity.Snowflake
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
        assertTrue(result.contains("No request context available"))
    }
    
    @Test
    fun testAddReminderWithContext() {
        // Test with proper context set
        val context = AiFunctions.MessageContext(
            Snowflake(123L),
            Snowflake(456L), 
            Snowflake(789L),
            Snowflake(101112L)
        )
        val requestId = AiFunctions.setMessageContext(context)
        
        try {
            val result = AiFunctions.addReminder("2025-12-25T10:00:00", "Test reminder")
            // Should not contain error messages about missing context
            assertFalse(result.contains("No request context available"))
            assertFalse(result.contains("Message context not found"))
        } finally {
            AiFunctions.clearMessageContext(requestId)
        }
    }
    
    @Test
    fun testConcurrentContexts() {
        // Test that multiple contexts don't interfere with each other
        val context1 = AiFunctions.MessageContext(Snowflake(1L), Snowflake(2L), Snowflake(3L), Snowflake(4L))
        val context2 = AiFunctions.MessageContext(Snowflake(5L), Snowflake(6L), Snowflake(7L), Snowflake(8L))
        
        val requestId1 = AiFunctions.setMessageContext(context1)
        val requestId2 = AiFunctions.setMessageContext(context2)
        
        try {
            // Both contexts should work independently
            val result1 = AiFunctions.addReminder("2025-12-25T10:00:00", "Test 1")
            val result2 = AiFunctions.addReminder("2025-12-25T11:00:00", "Test 2")
            
            assertFalse(result1.contains("No request context available"))
            assertFalse(result2.contains("No request context available"))
        } finally {
            AiFunctions.clearMessageContext(requestId1)
            AiFunctions.clearMessageContext(requestId2)
        }
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