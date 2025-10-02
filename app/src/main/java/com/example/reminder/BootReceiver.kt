package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            // If a schedule exists, restore next alarm
            val sched = AlarmScheduler.loadSchedule(context)
            if (sched != null) {
                AlarmScheduler.scheduleAlarms(context)
                // Notify UI to refresh
                val updateIntent = Intent("com.example.reminder.UPDATE_NEXT_ALARM").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }
}

