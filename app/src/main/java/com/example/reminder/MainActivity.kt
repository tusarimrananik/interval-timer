package com.example.reminder

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.*
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    ReminderScreen()
                }
            }
        }
    }
}

@Composable
private fun ReminderScreen() {
    val context = LocalContext.current

    val savedSchedule = remember { AlarmScheduler.loadSchedule(context) }
    var start by remember { mutableStateOf(savedSchedule?.start?.toString() ?: "05:00") }
    var end by remember { mutableStateOf(savedSchedule?.end?.toString() ?: "00:00") }
    var interval by remember { mutableStateOf(savedSchedule?.intervalMinutes ?: 45L) }

    // Keep ZonedDateTime in state
    var nextAlarmTime by remember {
        mutableStateOf(AlarmScheduler.nextScheduledDateTime(context))
    }

    // Format it for UI when needed
    val nextAlarmFormatted = nextAlarmTime?.let { zdt ->
        val millis = zdt.toInstant().toEpochMilli()
        android.text.format.DateFormat.format("hh:mm a", millis).toString()
    } ?: "—"

    val soundOptions = listOf("sound1", "sound2", "sound3")
    var selectedSound by remember { mutableStateOf(AlarmScheduler.loadSound(context)) }
    var expanded by remember { mutableStateOf(false) }

    // Broadcast receiver to update next alarm dynamically
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == "com.example.reminder.UPDATE_NEXT_ALARM") {
                    nextAlarmTime = AlarmScheduler.nextScheduledDateTime(context)
                }
            }
        }

        val filter = IntentFilter("com.example.reminder.UPDATE_NEXT_ALARM")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION", "UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose { context.unregisterReceiver(receiver) }
    }

    fun showTimePicker(currentValue: String, onTimeSelected: (String) -> Unit) {
        val parts = currentValue.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                val hh = selectedHour.toString().padStart(2, '0')
                val mm = selectedMinute.toString().padStart(2, '0')
                onTimeSelected("$hh:$mm")
            },
            hour,
            minute,
            false
        ).show()
    }

    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text("Interval Reminder", style = MaterialTheme.typography.headlineSmall)

        // Start Time Picker
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Start: ")
            Button(onClick = { showTimePicker(start) { start = it } }) {
                Text(formatToAmPm(start))
            }
        }

        // End Time Picker
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("End: ")
            Button(onClick = { showTimePicker(end) { end = it } }) {
                Text(formatToAmPm(end))
            }
        }

        // Interval Stepper
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { if (interval > 1) interval-- }) { Text("−") }
            Text("$interval min", style = MaterialTheme.typography.bodyLarge)
            Button(onClick = { interval++ }) { Text("+") }
        }


        // Inside your Column, before the "Save & Schedule" button:
        Box(modifier = Modifier.fillMaxWidth().wrapContentSize(Alignment.TopStart)) {
            OutlinedButton(onClick = { expanded = true }) {
                Text("Sound: $selectedSound")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                soundOptions.forEach { sound ->
                    DropdownMenuItem(
                        text = { Text(sound) },
                        onClick = {
                            selectedSound = sound
                            AlarmScheduler.saveSound(context, sound)
                            expanded = false
                        }
                    )
                }
            }
        }



        // Save & Cancel
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                try {
                    val schedule = AlarmScheduler.Schedule.fromStrings(start, end, interval)

                    AlarmScheduler.saveSchedule(context, schedule)

                    // Check for exact alarms permission
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                        if (!am.canScheduleExactAlarms()) {
                            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                            Toast.makeText(
                                context,
                                "Please allow exact alarms, then tap Schedule again.",
                                Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                    }

                    AlarmScheduler.scheduleAlarms(context)
                    nextAlarmTime = AlarmScheduler.nextScheduledDateTime(context)

                    Toast.makeText(
                        context,
                        "Scheduled. Next: $nextAlarmFormatted",
                        Toast.LENGTH_SHORT
                    ).show()

                } catch (e: Exception) {
                    Toast.makeText(context, "Invalid time format", Toast.LENGTH_SHORT).show()
                }
            }) {
                Text("Save & Schedule")
            }

            Button(onClick = {
                AlarmScheduler.cancelAlarms(context)
                Toast.makeText(context, "Cancelled all alarms", Toast.LENGTH_SHORT).show()
                nextAlarmTime = null // ✅ reset state properly
            }) {
                Text("Cancel Alarms")
            }
        }

        Text("Next scheduled: $nextAlarmFormatted")
    }
}

// Helper: Convert HH:mm to AM/PM
fun formatToAmPm(hhmm: String): String {
    val parts = hhmm.split(":")
    if (parts.size != 2) return hhmm
    val hour24 = parts[0].toIntOrNull() ?: return hhmm
    val minute = parts[1].toIntOrNull() ?: return hhmm
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, hour24)
    cal.set(Calendar.MINUTE, minute)
    return android.text.format.DateFormat.format("hh:mm a", cal).toString()
}
