/*
 * Copyright (C) 2026 Bible Flourish contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package me.xizzhu.android.joshua.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import java.util.TimeZone
import me.xizzhu.android.joshua.R
import me.xizzhu.android.joshua.reading.ReadingActivity

class ReadingReminderManager(private val context: Context, val slot: Int = 0) {
    init { require(slot in 0..2) }
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private fun key(name: String): String = if (slot == 0) name else "${name}_$slot"

    val isEnabled: Boolean
        get() = preferences.getBoolean(key(KEY_ENABLED), false)

    val hour: Int
        get() = preferences.getInt(key(KEY_HOUR), listOf(DEFAULT_HOUR, 7, 13)[slot])

    val minute: Int
        get() = preferences.getInt(key(KEY_MINUTE), DEFAULT_MINUTE)

    fun formattedTime(): String = android.text.format.DateFormat.getTimeFormat(context).format(
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
        }.time
    )

    fun setEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(key(KEY_ENABLED), enabled).apply()
        if (enabled) schedule() else cancel()
    }

    fun setTime(hour: Int, minute: Int) {
        require(hour in 0..23 && minute in 0..59)
        preferences.edit().putInt(key(KEY_HOUR), hour).putInt(key(KEY_MINUTE), minute).apply()
        if (isEnabled) schedule()
    }

    fun rememberChapter(chapter: String) {
        preferences.edit().putString(KEY_CHAPTER, chapter).apply()
    }

    fun rescheduleIfEnabled() {
        if (isEnabled) schedule()
    }

    private fun schedule() {
        val firstTrigger = nextTriggerMillis(System.currentTimeMillis(), hour, minute)

        alarmManager().setWindow(
            AlarmManager.RTC_WAKEUP,
            firstTrigger,
            REMINDER_WINDOW_MILLIS,
            reminderPendingIntent()
        )
    }

    private fun cancel() {
        alarmManager().cancel(reminderPendingIntent())
    }

    private fun alarmManager(): AlarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun reminderPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REMINDER_REQUEST_CODE + slot,
        Intent(context, ReadingReminderReceiver::class.java).setAction(ACTION_SHOW_REMINDER).putExtra(EXTRA_SLOT, slot),
        PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
    )

    companion object {
        private const val PREFERENCES_NAME = "reading_reminder"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_HOUR = "hour"
        private const val KEY_MINUTE = "minute"
        private const val KEY_CHAPTER = "chapter"
        private const val DEFAULT_HOUR = 19
        private const val DEFAULT_MINUTE = 0
        private const val REMINDER_REQUEST_CODE = 2200
        private const val NOTIFICATION_ID = 2200
        private const val REMINDER_WINDOW_MILLIS = 30 * 60 * 1000L
        private const val CHANNEL_ID = "reading_reminders"
        internal const val ACTION_SHOW_REMINDER = "me.xizzhu.android.joshua.SHOW_READING_REMINDER"
        internal const val EXTRA_SLOT = "reminder_slot"

        internal fun nextTriggerMillis(
            nowMillis: Long,
            hour: Int,
            minute: Int,
            timeZone: TimeZone = TimeZone.getDefault()
        ): Long = Calendar.getInstance(timeZone).apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis

        private fun immutableFlag(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

        internal fun showNotification(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = context.getString(R.string.reminder_channel_description) }
                (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
            }

            val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val chapter = preferences.getString(KEY_CHAPTER, null)
            val message = if (chapter.isNullOrBlank()) {
                context.getString(R.string.reminder_message_current_chapter)
            } else {
                context.getString(R.string.reminder_message_chapter, chapter)
            }
            val contentIntent = PendingIntent.getActivity(
                context,
                REMINDER_REQUEST_CODE,
                Intent(context, ReadingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or immutableFlag()
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_bookmark)
                .setContentTitle(context.getString(R.string.reminder_title))
                .setContentText(message)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}

class ReadingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReadingReminderManager.ACTION_SHOW_REMINDER) {
            val slot = intent.getIntExtra(ReadingReminderManager.EXTRA_SLOT, 0)
            if (slot !in 0..2) return
            val manager = ReadingReminderManager(context.applicationContext, slot)
            if (manager.isEnabled) ReadingReminderManager.showNotification(context)
            manager.rescheduleIfEnabled()
        } else {
            (0..2).forEach { ReadingReminderManager(context.applicationContext, it).rescheduleIfEnabled() }
        }
    }
}
