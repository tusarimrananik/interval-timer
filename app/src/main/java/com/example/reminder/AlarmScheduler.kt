package com.example.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.time.*
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import androidx.core.content.edit

object AlarmScheduler {

    // ---- Public API ----

    data class Schedule(
        val start: LocalTime,
        val end: LocalTime,
        val intervalMinutes: Long
    ) {
        val crossesMidnight: Boolean get() = end <= start

        companion object {
            private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            fun fromStrings(startHHmm: String, endHHmm: String, intervalMinutes: Long): Schedule {
                val s = LocalTime.parse(startHHmm, fmt)
                val e = LocalTime.parse(endHHmm, fmt)
                require(intervalMinutes > 0) { "Interval must be > 0" }
                return Schedule(s, e, intervalMinutes)
            }
        }
    }

    fun saveSchedule(context: Context, schedule: Schedule) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_START, schedule.start.toString())
            putString(KEY_END, schedule.end.toString())
            putLong(KEY_INTERVAL, schedule.intervalMinutes)
        }
    }

    fun loadSchedule(context: Context): Schedule? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val start = prefs.getString(KEY_START, null) ?: return null
        val end = prefs.getString(KEY_END, null) ?: return null
        val interval = prefs.getLong(KEY_INTERVAL, -1L)
        if (interval <= 0) return null
        return Schedule(LocalTime.parse(start), LocalTime.parse(end), interval)
    }

    /**
     * Schedule the next exact alarm based on the current time and saved schedule.
     * Will reschedule after each firing.
     */
    fun scheduleAlarms(context: Context) {
        cancelAlarms(context) // ensure no duplicates
        if (!canScheduleExactAlarms(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
            return
        }

        val schedule = loadSchedule(context) ?: return
        val next = computeNextTrigger(Instant.now(), schedule, ZoneId.systemDefault()) ?: return
        setExactAlarm(context, next.toInstant().toEpochMilli())
    }

    /** Cancel any pending alarms. */
    fun cancelAlarms(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    /** For showing in the UI. */
    fun nextScheduledDateTime(context: Context): ZonedDateTime? {
        val schedule = loadSchedule(context) ?: return null
        return computeNextTrigger(Instant.now(), schedule, ZoneId.systemDefault())
    }


    private fun computeNextTrigger(
        nowInstant: Instant,
        schedule: Schedule,
        zone: ZoneId
    ): ZonedDateTime? {
        val now = nowInstant.atZone(zone)
        val today = now.toLocalDate()
        val timeNow = now.toLocalTime()

        val withinWindow = isWithinWindow(timeNow, schedule)

        if (withinWindow) {
            val windowStart = if (schedule.crossesMidnight && timeNow < schedule.end) {
                // Started yesterday
                ZonedDateTime.of(today.minusDays(1), schedule.start, zone)
            } else {
                ZonedDateTime.of(today, schedule.start, zone)
            }
            val windowEnd = if (schedule.crossesMidnight) {
                ZonedDateTime.of(windowStart.toLocalDate().plusDays(1), schedule.end, zone)
            } else {
                ZonedDateTime.of(windowStart.toLocalDate(), schedule.end, zone)
            }

            val firstTick = windowStart
            val intervalSec = schedule.intervalMinutes * 60L
            val elapsedSec = Duration.between(firstTick, now).seconds.coerceAtLeast(0)
            val steps = ceil(elapsedSec / intervalSec.toDouble()).toLong()
            var candidate = firstTick.plusSeconds(steps * intervalSec)

            // 🔑 Fix: ensure candidate is strictly after 'now'
            if (!candidate.isAfter(now)) {
                candidate = candidate.plusSeconds(intervalSec)
            }

            return if (!candidate.isBefore(windowEnd)) {
                nextWindowStart(windowStart)
            } else {
                candidate
            }
        } else {
            val startToday = ZonedDateTime.of(today, schedule.start, zone)
            val nextStart = if (!schedule.crossesMidnight) {
                if (timeNow < schedule.start) startToday else startToday.plusDays(1)
            } else {
                when {
                    timeNow < schedule.end -> startToday
                    timeNow < schedule.start -> startToday
                    else -> startToday.plusDays(1)
                }
            }
            return nextStart
        }
    }


    private fun isWithinWindow(now: LocalTime, schedule: Schedule): Boolean {
        return if (!schedule.crossesMidnight) {
            now >= schedule.start && now < schedule.end
        } else {
            now >= schedule.start || now < schedule.end
        }
    }

    private fun nextWindowStart(currentWindowStart: ZonedDateTime): ZonedDateTime {
        return currentWindowStart.plusDays(1)
    }

    // ---- AlarmManager plumbing ----

    private fun setExactAlarm(context: Context, triggerAtMillis: Long) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context)

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                context.startActivity(intent)
            }
        }
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, AlarmReceiver.REQ_CODE, intent, flags)
    }

    // ---- Permission check ----

    private fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }

    // ---- SharedPreferences keys ----

    private const val PREFS = "reminder_prefs"

    @Suppress("SpellCheckingInspection")
    private const val KEY_START = "start_hhmm"

    @Suppress("SpellCheckingInspection")
    private const val KEY_END = "end_hhmm"

    private const val KEY_INTERVAL = "interval_minutes"
}
