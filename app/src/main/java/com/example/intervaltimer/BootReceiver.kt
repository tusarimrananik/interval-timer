package com.example.intervaltimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val schedule = AlarmScheduler.loadSchedule(context)
            if (schedule != null) {
                AlarmScheduler.scheduleAlarms(context)

                val updateIntent = Intent("com.example.reminder.UPDATE_NEXT_ALARM").apply {
                    setPackage(context.packageName)
                }
                context.sendBroadcast(updateIntent)
            }
        }
    }
}