package com.example.intervaltimer

import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private enum class PickerType { TIME, SOUND }

private data class RulePicker(
    val index: Int,
    val type: PickerType
)

private val AppDarkColors = darkColorScheme(
    primary = Color(0xFF8B5CF6),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF051319),
    background = Color(0xFF090B10),
    onBackground = Color(0xFFE9EDF5),
    surface = Color(0xFF11141B),
    onSurface = Color(0xFFE9EDF5),
    surfaceVariant = Color(0xFF1A1F2B),
    onSurfaceVariant = Color(0xFF97A2B2),
    outline = Color(0xFF2B3240),
    error = Color(0xFFFF6B6B)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = AppDarkColors
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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

    var start by rememberSaveable { mutableStateOf(savedSchedule?.start?.toString() ?: "05:00") }
    var end by rememberSaveable { mutableStateOf(savedSchedule?.end?.toString() ?: "00:00") }
    var interval by rememberSaveable { mutableStateOf(savedSchedule?.intervalMinutes ?: 45L) }

    var commentAction by rememberSaveable {
        mutableStateOf(AlarmScheduler.loadCommentTask(context).ifEmpty { "1" })
    }

    var nextAlarmTime by remember {
        mutableStateOf(AlarmScheduler.nextScheduledDateTime(context))
    }

    val nextAlarmFormatted = nextAlarmTime?.let { zdt ->
        val millis = zdt.toInstant().toEpochMilli()
        DateFormat.format("hh:mm a", millis).toString()
    } ?: "—"

    val soundOptions = remember {
        listOf(
            "short_alert",
            "alert_alarm",
            "classic_alarm",
            "game_notification"
        )
    }

    var defaultSound by rememberSaveable {
        mutableStateOf(AlarmScheduler.loadSound(context))
    }
    var defaultSoundExpanded by remember { mutableStateOf(false) }

    var specialRules by remember {
        mutableStateOf(AlarmScheduler.loadSpecialRules(context))
    }

    var activePicker by remember { mutableStateOf<RulePicker?>(null) }

    val currentSchedule = remember(start, end, interval) {
        runCatching {
            AlarmScheduler.Schedule.fromStrings(start, end, interval)
        }.getOrNull()
    }

    val validSpecialTimes by remember(currentSchedule) {
        derivedStateOf {
            currentSchedule?.let { AlarmScheduler.availableTriggerTimes(it) } ?: emptyList()
        }
    }

    val hasUnusedValidTime by remember(validSpecialTimes, specialRules) {
        derivedStateOf {
            validSpecialTimes.any { candidate ->
                specialRules.none { it.timeHHmm == candidate }
            }
        }
    }

    LaunchedEffect(currentSchedule, specialRules) {
        val allowedTimes = validSpecialTimes.toSet()
        val cleaned = specialRules
            .filter { it.timeHHmm in allowedTimes }
            .distinctBy { it.timeHHmm }

        if (cleaned != specialRules) {
            specialRules = cleaned
        }
    }

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

    fun saveScheduleAndAlarms() {
        try {
            val schedule = AlarmScheduler.Schedule.fromStrings(start, end, interval)

            val cleanedRules = specialRules
                .filter { it.timeHHmm in validSpecialTimes }
                .distinctBy { it.timeHHmm }

            AlarmScheduler.saveSchedule(context, schedule)
            AlarmScheduler.saveSound(context, defaultSound)
            AlarmScheduler.saveSpecialRules(context, cleanedRules)
            AlarmScheduler.saveCommentTask(context, commentAction)

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
                    return
                }
            }

            AlarmScheduler.scheduleAlarms(context)
            val updatedNext = AlarmScheduler.nextScheduledDateTime(context)
            nextAlarmTime = updatedNext

            val updatedNextFormatted = updatedNext?.let { zdt ->
                val millis = zdt.toInstant().toEpochMilli()
                DateFormat.format("hh:mm a", millis).toString()
            } ?: "—"

            Toast.makeText(
                context,
                "Scheduled. Next: $updatedNextFormatted",
                Toast.LENGTH_SHORT
            ).show()
        } catch (_: Exception) {
            Toast.makeText(context, "Invalid time format", Toast.LENGTH_SHORT).show()
        }
    }

    fun cancelAllAlarms() {
        AlarmScheduler.cancelAlarms(context)
        nextAlarmTime = null
        Toast.makeText(context, "Cancelled all alarms", Toast.LENGTH_SHORT).show()
    }

    activePicker?.let { picker ->
        if (picker.index in specialRules.indices) {
            val rule = specialRules[picker.index]

            if (picker.type == PickerType.TIME) {
                val usedTimesByOtherRules = specialRules
                    .mapIndexedNotNull { i, item ->
                        if (i != picker.index) item.timeHHmm else null
                    }
                    .toSet()

                val selectableTimes = validSpecialTimes.filter { time ->
                    time == rule.timeHHmm || time !in usedTimesByOtherRules
                }

                SelectionDialog(
                    title = "Choose Time",
                    items = selectableTimes,
                    labelForItem = { formatToAmPm(it) },
                    onDismiss = { activePicker = null },
                    onItemSelected = { hhmm ->
                        specialRules = specialRules.toMutableList().apply {
                            this[picker.index] = this[picker.index].copy(timeHHmm = hhmm)
                        }
                        activePicker = null
                    }
                )
            } else {
                SelectionDialog(
                    title = "Choose Sound",
                    items = soundOptions,
                    labelForItem = { it },
                    onDismiss = { activePicker = null },
                    onItemSelected = { sound ->
                        specialRules = specialRules.toMutableList().apply {
                            this[picker.index] = this[picker.index].copy(soundName = sound)
                        }
                        activePicker = null
                    }
                )
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            ActionBar(
                interval = interval,
                commentAction = commentAction,
                onSave = { saveScheduleAndAlarms() },
                onCancel = { cancelAllAlarms() }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                HeroCard(nextAlarmFormatted = nextAlarmFormatted)
            }

            item {
                ModernCard(title = "Schedule") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TimeButton(
                            modifier = Modifier.weight(1f),
                            label = "Start",
                            value = formatToAmPm(start),
                            onClick = { showTimePicker(start) { start = it } }
                        )
                        TimeButton(
                            modifier = Modifier.weight(1f),
                            label = "End",
                            value = formatToAmPm(end),
                            onClick = { showTimePicker(end) { end = it } }
                        )
                    }

                    Spacer(Modifier.size(16.dp))

                    Text(
                        text = "Interval",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        GlassIconButton(
                            text = "-",
                            onClick = { if (interval > 1) interval-- }
                        )

                        Text(
                            text = "$interval min",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        GlassIconButton(
                            text = "+",
                            onClick = { interval++ }
                        )
                    }
                }
            }

            item {
                ModernCard(title = "Action Number") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currentAction = commentAction.toIntOrNull() ?: 1

                        GlassIconButton(
                            text = "-",
                            onClick = {
                                if (currentAction > 1) {
                                    val newVal = (currentAction - 1).toString()
                                    commentAction = newVal
                                    AlarmScheduler.saveCommentTask(context, newVal)
                                }
                            }
                        )

                        Text(
                            text = "$currentAction",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        GlassIconButton(
                            text = "+",
                            onClick = {
                                val newVal = (currentAction + 1).toString()
                                commentAction = newVal
                                AlarmScheduler.saveCommentTask(context, newVal)
                            }
                        )
                    }
                }
            }

            item {
                ModernCard(title = "Default Sound") {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { defaultSoundExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                        ) {
                            Text(
                                text = defaultSound,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            Icon(
                                Icons.Filled.ArrowDropDown,
                                contentDescription = "Select Sound",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = defaultSoundExpanded,
                            onDismissRequest = { defaultSoundExpanded = false }
                        ) {
                            soundOptions.forEach { sound ->
                                DropdownMenuItem(
                                    text = { Text(sound) },
                                    onClick = {
                                        defaultSound = sound
                                        defaultSoundExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            item {
                ModernCard(title = "Special Sounds") {
                    when {
                        currentSchedule == null -> {
                            Text(
                                text = "Enter a valid schedule to add special sounds.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        validSpecialTimes.isEmpty() -> {
                            Text(
                                text = "No valid trigger times for current schedule.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        else -> {
                            specialRules.forEachIndexed { index, rule ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                                    ),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = { activePicker = RulePicker(index, PickerType.TIME) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                                )
                                            ) {
                                                Text(
                                                    text = "Time: ${formatToAmPm(rule.timeHHmm)}",
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(Modifier.weight(1f))
                                                Icon(
                                                    Icons.Filled.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            OutlinedButton(
                                                onClick = { activePicker = RulePicker(index, PickerType.SOUND) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                border = BorderStroke(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                                                )
                                            ) {
                                                Text(
                                                    text = rule.soundName,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Spacer(Modifier.weight(1f))
                                                Icon(
                                                    Icons.Filled.ArrowDropDown,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                specialRules = specialRules.toMutableList().apply {
                                                    removeAt(index)
                                                }
                                            }
                                        ) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Remove rule",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }

                                Spacer(Modifier.size(12.dp))
                            }

                            Button(
                                onClick = {
                                    val firstUnusedTime = validSpecialTimes.firstOrNull { candidate ->
                                        specialRules.none { it.timeHHmm == candidate }
                                    }

                                    if (firstUnusedTime != null) {
                                        specialRules = specialRules + AlarmScheduler.SpecialSoundRule(
                                            timeHHmm = firstUnusedTime,
                                            soundName = defaultSound
                                        )
                                    }
                                },
                                enabled = hasUnusedValidTime,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text("Add Special Time")
                            }
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}

@Composable
private fun SelectionDialog(
    title: String,
    items: List<String>,
    labelForItem: (String) -> String,
    onDismiss: () -> Unit,
    onItemSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items) { item ->
                    TextButton(
                        onClick = { onItemSelected(item) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = labelForItem(item),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun HeroCard(nextAlarmFormatted: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Next Reminder",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f)
            )
            Text(
                text = nextAlarmFormatted,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun ModernCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.size(16.dp))
            content()
        }
    }
}

@Composable
private fun TimeButton(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
            )
        ) {
            Text(
                text = value,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun GlassIconButton(
    text: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
        )
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp)
        ) {
            Text(
                text = text,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ActionBar(
    interval: Long,
    commentAction: String,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val actionText = commentAction.ifBlank { "1" }

    Surface(
        shadowElevation = 18.dp,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "DO #$actionText comments in every $interval mins",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Cancel All")
                }

                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Save")
                }
            }
        }
    }
}

fun formatToAmPm(hhmm: String): String {
    val parts = hhmm.split(":")
    if (parts.size != 2) return hhmm

    val hour24 = parts[0].toIntOrNull() ?: return hhmm
    val minute = parts[1].toIntOrNull() ?: return hhmm

    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, hour24)
    cal.set(Calendar.MINUTE, minute)

    return DateFormat.format("hh:mm a", cal).toString()
}