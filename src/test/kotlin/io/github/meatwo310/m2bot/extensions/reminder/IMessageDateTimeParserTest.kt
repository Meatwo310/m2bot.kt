package io.github.meatwo310.m2bot.extensions.reminder

import kotlinx.datetime.number
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IMessageDateTimeParserTest : IMessageDateTimeParser {
    @Test
    fun testParseFullDateTime() {
        val input = "2025/06/17 12:34"
        val result = input.parseMessageDateTime()
        assertNotNull(result)
        assertEquals(2025, result.year)
        assertEquals(6, result.month.number)
        assertEquals(17, result.day)
        assertEquals(12, result.hour)
        assertEquals(34, result.minute)
    }

    @Test
    fun testParseMonthDayTime() {
        val input = "6/18 08:00"
        val result = input.parseMessageDateTime()
        assertNotNull(result)
        assertEquals(6, result.month.number)
        assertEquals(18, result.day)
        assertEquals(8, result.hour)
        assertEquals(0, result.minute)
    }

    @Test
    fun testParseHourMinute() {
        val input = "15:45"
        val result = input.parseMessageDateTime()
        assertNotNull(result)
        assertEquals(result.hour, 15)
        assertEquals(result.minute, 45)
    }

    @Test
    fun testParseTomorrow() {
        val input = "明日 10:00"
        val result = input.parseMessageDateTime()
        assertNotNull(result)
        // 現在日付の翌日になっていることを確認
        val now = java.time.LocalDate.now()
        val expectedDate = now.plusDays(1)
        assertEquals(expectedDate.year, result.year)
        assertEquals(expectedDate.monthValue, result.month.number)
        assertEquals(expectedDate.dayOfMonth, result.day)
        assertEquals(10, result.hour)
        assertEquals(0, result.minute)
    }

    @Test
    fun testParseInvalid() {
        val input = "invalid string"
        val result = input.parseMessageDateTime()
        assertNull(result)
    }

    @Test
    fun testParseInvalidDate() {
        val input = "2025/13/32 25:61"
        try {
            input.parseMessageDateTime()
        } catch (_: IllegalArgumentException) {
            return
        }
    }

    @Test
    fun testParseInvalidNumber() {
        val input = "2147483647"
        val result = input.parseMessageDateTime()
        assertNull(result)
    }
}

