package com.example.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.PowerManager
import android.widget.Toast

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        // Play MP3 with a short partial wakelock to ensure CPU stays on during playback
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "reminder:alarm_wakelock")
        wl.setReferenceCounted(false)
        wl.acquire(15_000L) // hold up to 15s

        val mp = MediaPlayer()
        mp.setOnCompletionListener {
            try { it.release() } catch (_: Exception) {}
            if (wl.isHeld) wl.release()
        }
        mp.setOnErrorListener { player, _, _ ->
            try { player.release() } catch (_: Exception) {}
            if (wl.isHeld) wl.release()
            true
        }

        try {
            val soundName = AlarmScheduler.loadSound(context)

            // Dynamically find the resource ID by name
            val resId = context.resources.getIdentifier(soundName, "raw", context.packageName)

            if (resId == 0) {
                // Fallback if sound not found
                if (wl.isHeld) wl.release()
                return
            }

            val afd = context.resources.openRawResourceFd(resId)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            mp.prepare()
            mp.start()
        } catch (_: Exception) {
            try { mp.release() } catch (_: Exception) {}
            if (wl.isHeld) wl.release()
        }

        // Optional tiny UX cue (no persistent notifications as requested)
        Toast.makeText(context, "Reminder!", Toast.LENGTH_SHORT).show()

        // Schedule the next alarm based on current time and stored schedule
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
