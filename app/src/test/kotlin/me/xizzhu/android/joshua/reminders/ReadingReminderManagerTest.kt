/*
 * Copyright (C) 2026 Bible Flourish contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package me.xizzhu.android.joshua.reminders

import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingReminderManagerTest {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    @Test
    fun `next trigger uses today when reminder time is still ahead`() {
        val now = time(day = 10, hour = 10)

        assertEquals(time(day = 10, hour = 19), ReadingReminderManager.nextTriggerMillis(now, 19, 0, utc))
    }

    @Test
    fun `next trigger uses tomorrow when reminder time has passed`() {
        val now = time(day = 10, hour = 20)

        assertEquals(time(day = 11, hour = 19), ReadingReminderManager.nextTriggerMillis(now, 19, 0, utc))
    }

    private fun time(day: Int, hour: Int): Long = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.JANUARY, day, hour, 0, 0)
    }.timeInMillis
}
