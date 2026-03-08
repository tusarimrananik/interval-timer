package com.example.intervaltimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "reminder:alarm_wakelock"
        )

        wl.setReferenceCounted(false)
        wl.acquire(15_000L)

        val mp = MediaPlayer()

        mp.setOnCompletionListener {
            try {
                it.release()
            } catch (_: Exception) {
            }
            if (wl.isHeld) wl.release()
        }

        mp.setOnErrorListener { player, _, _ ->
            try {
                player.release()
            } catch (_: Exception) {
            }
            if (wl.isHeld) wl.release()
            true
        }

        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )

            val soundName = intent?.getStringExtra(AlarmScheduler.EXTRA_SOUND_NAME)
                ?: AlarmScheduler.loadSound(context)

            val resId = context.resources.getIdentifier(
                soundName,
                "raw",
                context.packageName
            )

            if (resId != 0) {
                val afd = context.resources.openRawResourceFd(resId)
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()

                mp.prepare()
                mp.start()
            } else {
                if (wl.isHeld) wl.release()
                try {
                    mp.release()
                } catch (_: Exception) {
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                mp.release()
            } catch (_: Exception) {
            }
            if (wl.isHeld) wl.release()
        }

        Toast.makeText(context, "Reminder!", Toast.LENGTH_SHORT).show()

        AlarmScheduler.scheduleAlarms(context)

        val updateIntent = Intent("com.example.reminder.UPDATE_NEXT_ALARM").apply {
            setPackage(context.packageName)
        }
        context.sendBroadcast(updateIntent)
    }

    companion object {
        const val ACTION = "com.example.reminder.ACTION_PLAY_ALARM"
        const val REQ_CODE = 1001
    }
}