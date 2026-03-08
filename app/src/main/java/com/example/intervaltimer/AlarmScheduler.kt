package com.example.intervaltimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

object AlarmScheduler {

    data class Schedule(
        val start: LocalTime,
        val end: LocalTime,
        val intervalMinutes: Long
    ) {
        val crossesMidnight: Boolean
            get() = end <= start

        companion object {
            private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

            fun fromStrings(
                startHHmm: String,
                endHHmm: String,
                intervalMinutes: Long
            ): Schedule {
                val s = LocalTime.parse(startHHmm, fmt)
                val e = LocalTime.parse(endHHmm, fmt)
                require(intervalMinutes > 0) { "Interval must be > 0" }
                return Schedule(s, e, intervalMinutes)
            }
        }
    }

    data class SpecialSoundRule(
        val timeHHmm: String,
        val soundName: String
    )

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

        return try {
            Schedule(LocalTime.parse(start), LocalTime.parse(end), interval)
        } catch (_: Exception) {
            null
        }
    }

    fun saveSound(context: Context, soundName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_SOUND, soundName)
        }
    }

    fun loadSound(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SOUND, DEFAULT_SOUND) ?: DEFAULT_SOUND
    }

    fun saveSpecialRules(context: Context, rules: List<SpecialSoundRule>) {
        val arr = JSONArray()
        rules.forEach { rule ->
            val obj = JSONObject()
            obj.put("time", rule.timeHHmm)
            obj.put("sound", rule.soundName)
            arr.put(obj)
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_SPECIAL_RULES, arr.toString())
        }
    }

    fun loadSpecialRules(context: Context): List<SpecialSoundRule> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_SPECIAL_RULES, null) ?: return emptyList()

        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        SpecialSoundRule(
                            timeHHmm = obj.getString("time"),
                            soundName = obj.getString("sound")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun availableTriggerTimes(schedule: Schedule): List<String> {
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        val baseDate = LocalDate.of(2000, 1, 1)

        var current = LocalDateTime.of(baseDate, schedule.start)
        val windowEnd = if (schedule.crossesMidnight) {
            LocalDateTime.of(baseDate.plusDays(1), schedule.end)
        } else {
            LocalDateTime.of(baseDate, schedule.end)
        }

        val result = mutableListOf<String>()

        while (current < windowEnd) {
            result.add(current.toLocalTime().format(fmt))
            current = current.plusMinutes(schedule.intervalMinutes)
        }

        return result
    }

    fun scheduleAlarms(context: Context) {
        cancelAlarms(context)

        if (!canScheduleExactAlarms(context)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            return
        }

        val schedule = loadSchedule(context) ?: return
        val next = computeNextTrigger(
            nowInstant = Instant.now(),
            schedule = schedule,
            zone = ZoneId.systemDefault()
        ) ?: return

        val soundName = soundForTrigger(context, next)
        setExactAlarm(context, next.toInstant().toEpochMilli(), soundName)
    }

    fun cancelAlarms(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context))
    }

    fun nextScheduledDateTime(context: Context): ZonedDateTime? {
        val schedule = loadSchedule(context) ?: return null
        return computeNextTrigger(
            nowInstant = Instant.now(),
            schedule = schedule,
            zone = ZoneId.systemDefault()
        )
    }

    private fun soundForTrigger(context: Context, trigger: ZonedDateTime): String {
        val hhmm = trigger.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
        val rules = loadSpecialRules(context)
        return rules.firstOrNull { it.timeHHmm == hhmm }?.soundName
            ?: loadSound(context)
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

        return if (withinWindow) {
            val windowStart = if (schedule.crossesMidnight && timeNow < schedule.end) {
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

            if (!candidate.isAfter(now)) {
                candidate = candidate.plusSeconds(intervalSec)
            }

            if (!candidate.isBefore(windowEnd)) {
                nextWindowStart(windowStart)
            } else {
                candidate
            }
        } else {
            val startToday = ZonedDateTime.of(today, schedule.start, zone)

            if (!schedule.crossesMidnight) {
                if (timeNow < schedule.start) startToday else startToday.plusDays(1)
            } else {
                when {
                    timeNow < schedule.end -> startToday
                    timeNow < schedule.start -> startToday
                    else -> startToday.plusDays(1)
                }
            }
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

    private fun setExactAlarm(
        context: Context,
        triggerAtMillis: Long,
        soundName: String
    ) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, soundName)

        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    private fun pendingIntent(
        context: Context,
        soundName: String? = null
    ): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION
            if (soundName != null) {
                putExtra(EXTRA_SOUND_NAME, soundName)
            }
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, AlarmReceiver.REQ_CODE, intent, flags)
    }

    private fun canScheduleExactAlarms(context: Context): Boolean {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }
    }



    fun saveCommentTask(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_COMMENT_TASK, value)
        }
    }

    fun loadCommentTask(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_COMMENT_TASK, "") ?: ""
    }

    private const val KEY_COMMENT_TASK = "comment_task"
    const val EXTRA_SOUND_NAME = "sound_name"

    private const val PREFS = "reminder_prefs"
    private const val KEY_START = "start_hhmm"
    private const val KEY_END = "end_hhmm"
    private const val KEY_INTERVAL = "interval_minutes"
    private const val KEY_SOUND = "selected_sound"
    private const val KEY_SPECIAL_RULES = "special_sound_rules"
    private const val DEFAULT_SOUND = "short_alert"
}