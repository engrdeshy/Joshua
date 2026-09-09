package me.xizzhu.android.joshua.reminders

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ReminderSlotsTest {
    @Test
    fun `three reminders preserve legacy time and schedule independently`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("reading_reminder", Context.MODE_PRIVATE).edit()
            .clear().putBoolean("enabled", true).putInt("hour", 21).putInt("minute", 15).commit()
        val night = ReadingReminderManager(context)
        val morning = ReadingReminderManager(context, 1)
        val afternoon = ReadingReminderManager(context, 2)
        assertTrue(night.isEnabled)
        assertEquals(21, night.hour)
        assertEquals(15, night.minute)
        assertFalse(morning.isEnabled)
        assertFalse(afternoon.isEnabled)
        morning.setTime(6, 30)
        afternoon.setTime(14, 45)
        night.rescheduleIfEnabled()
        morning.setEnabled(true)
        afternoon.setEnabled(true)
        val alarms = shadowOf(context.getSystemService(Context.ALARM_SERVICE) as AlarmManager)
        assertEquals(3, alarms.scheduledAlarms.size)
        morning.setTime(8, 0)
        assertEquals(3, alarms.scheduledAlarms.size)
        morning.setEnabled(false)
        assertEquals(2, alarms.scheduledAlarms.size)
        assertTrue(night.isEnabled)
        assertTrue(afternoon.isEnabled)
        assertEquals(14, ReadingReminderManager(context, 2).hour)
        assertEquals(21, night.hour)
    }
}
