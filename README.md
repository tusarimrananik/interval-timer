# Interval Timer

A sleek dark-themed Android interval reminder app built with **Kotlin** and **Jetpack Compose**.  
It lets you schedule repeating reminders between a start and end time, choose a default sound, and assign **special sounds** to specific trigger times.

## Features

- Dark modern UI with Material 3 styling
- Start time and end time selection
- Adjustable interval in minutes
- Displays the next scheduled reminder
- Choose a default reminder sound
- Add special sound rules for specific trigger times
- Keeps special times unique and valid for the current schedule
- Saves schedule and sound preferences locally
- Reschedules alarms after device reboot
- Uses exact alarms for reliable reminder timing

## Tech Stack

- **Kotlin**
- **Jetpack Compose**
- **Material 3**
- **AlarmManager**
- **BroadcastReceiver**
- **SharedPreferences**

## How It Works

The app stores the user’s selected:

- start time
- end time
- interval
- default sound
- special sound rules
- action number

When the schedule is saved:

1. The app calculates the **next valid reminder time**
2. It schedules **one exact alarm**
3. When that alarm fires, the receiver:
   - plays the selected sound
   - shows a toast
   - schedules the next alarm automatically

This keeps the app efficient while preserving the same reminder behavior.

## Project Structure

```text
com.example.intervaltimer
├── MainActivity.kt       # Main Compose UI
├── AlarmScheduler.kt     # Schedule calculation, storage, exact alarm setup
├── AlarmReceiver.kt      # Plays sound and schedules next reminder
└── BootReceiver.kt       # Restores alarms after reboot
```

## Requirements

- Android Studio Hedgehog or newer recommended
- Minimum SDK supported by your project setup
- Android device/emulator with alarm permission support

## Permissions / Important Notes

For Android 12+ (`SDK 31+`), exact alarms may require user approval.

The app may prompt for:

- `SCHEDULE_EXACT_ALARM`

To restore alarms after reboot, make sure the app declares:

- `RECEIVE_BOOT_COMPLETED`

## Example Manifest Entries

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
```

Receivers:

```xml
<receiver
    android:name=".AlarmReceiver"
    android:exported="false" />

<receiver
    android:name=".BootReceiver"
    android:enabled="true"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.BOOT_COMPLETED" />
    </intent-filter>
</receiver>
```

## Setup

1. Clone or open the project in Android Studio
2. Make sure your package name matches:
   - `com.example.intervaltimer`
3. Add your alarm sound files into:

```text
app/src/main/res/raw/
```

Example sound files:

```text
short_alert.mp3
alert_alarm.mp3
classic_alarm.mp3
game_notification.mp3
```

4. Build and run the app on a device or emulator

## Using the App

1. Choose a **Start** time
2. Choose an **End** time
3. Set the **Interval**
4. Select the **Default Sound**
5. Optionally add **Special Sounds** for specific trigger times
6. Tap **Save**

The app will then show the next scheduled reminder time.

## Special Sounds

Special sounds let you override the default sound for certain reminder times.

Example:

- Default sound: `short_alert`
- 08:00 → `classic_alarm`
- 09:30 → `game_notification`

Only valid trigger times for the current schedule can be assigned.

## Performance Improvements Included

This version keeps the same core functionality while improving performance:

- Uses a **shared picker dialog** for special sound editing instead of heavy per-row dropdown menus
- Uses `LazyColumn` for smoother rendering
- Uses derived state to reduce unnecessary recomposition
- Schedules only the **next** exact alarm instead of creating many alarms at once

## Limitations

- Sound names must match actual files inside `res/raw`
- Uses `Toast` for reminder feedback; no notification UI yet
- Very short interval settings may still feel frequent depending on device battery optimization

## Future Improvements

- Foreground notification support
- Better audio handling with `SoundPool` for short sounds
- Edit and preview sound playback in the UI
- Dynamic color support
- Persistent notification showing next reminder
- Export/import schedule settings

## License

This project is for personal/educational use unless you add your own license.

## Author

Built as an Android interval reminder app using Jetpack Compose and AlarmManager.
