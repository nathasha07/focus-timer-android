package com.example.focustimer


import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

// Local settings storage
val Context.focusDataStore by preferencesDataStore(
    name = "focus_timer_settings"
)

val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
val SHORT_BREAK_KEY = intPreferencesKey("short_break_minutes")
val LONG_BREAK_KEY = intPreferencesKey("long_break_minutes")
val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
val COMPLETED_SESSIONS_KEY = intPreferencesKey("completed_sessions")
val SESSION_DATE_KEY = stringPreferencesKey("session_date")
val DAILY_GOAL_KEY = intPreferencesKey("daily_goal")
private fun todayKey(): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        .format(Date())
}

private data class TimerSettings(
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val isDark: Boolean,
    val completedSessions: Int,
    val sessionDate: String,
    val dailyGoal: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createTimerNotificationChannel(this)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionLauncher =
                registerForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    if (isGranted) {
                        // Notifications are enabled
                    }
                }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(
                    Manifest.permission.POST_NOTIFICATIONS
                )
            }
        }
        setContent {
            MaterialTheme {
                FocusTimer()
            }
        }
    }
}
private const val TIMER_CHANNEL_ID = "focus_timer_channel"

private fun createTimerNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            TIMER_CHANNEL_ID,
            "Focus Timer",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when a focus session or break finishes"
            enableVibration(true)
        }

        val manager =
            context.getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        manager.createNotificationChannel(channel)
    }
}

private fun showTimerNotification(
    context: Context,
    title: String,
    message: String
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }

    val notification = NotificationCompat.Builder(
        context,
        TIMER_CHANNEL_ID
    )
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setContentTitle(title)
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setDefaults(NotificationCompat.DEFAULT_ALL)
        .build()

    NotificationManagerCompat.from(context).notify(1001, notification)
}
enum class TimerMode(val label: String) {
    FOCUS("Focus"),
    SHORT_BREAK("Short Break"),
    LONG_BREAK("Long Break")
}

private fun TimerMode.emoji(): String = when (this) {
    TimerMode.FOCUS -> "🎀"
    TimerMode.SHORT_BREAK -> "☁️"
    TimerMode.LONG_BREAK -> "🌙"
}

private fun TimerMode.description(): String = when (this) {
    TimerMode.FOCUS -> "Concentrate on what matters"
    TimerMode.SHORT_BREAK -> "Take a little breather"
    TimerMode.LONG_BREAK -> "Rest and recharge"
}

private fun startBackgroundTimer(
    context: Context,
    seconds: Int,
    mode: TimerMode
) {
    val endTime = System.currentTimeMillis() + seconds * 1000L

    val intent = android.content.Intent(
        context,
        TimerForegroundService::class.java
    ).apply {
        action = TimerForegroundService.ACTION_START
        putExtra(TimerForegroundService.EXTRA_END_TIME, endTime)
        putExtra(TimerForegroundService.EXTRA_MODE, mode.name)
    }

    androidx.core.content.ContextCompat.startForegroundService(
        context,
        intent
    )
}

private fun pauseBackgroundTimer(context: Context) {
    val intent = android.content.Intent(
        context,
        TimerForegroundService::class.java
    ).apply {
        action = TimerForegroundService.ACTION_PAUSE
    }

    context.startService(intent)
}


private fun resetBackgroundTimer(
    context: Context,
    seconds: Int,
    mode: TimerMode
) {
    val intent = android.content.Intent(
        context,
        TimerForegroundService::class.java
    ).apply {
        action = TimerForegroundService.ACTION_RESET
        putExtra(
            TimerForegroundService.EXTRA_REMAINING,
            seconds
        )
        putExtra(
            TimerForegroundService.EXTRA_MODE,
            mode.name
        )
    }

    context.startService(intent)
}



@Composable
fun FocusTimer() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val savedSettingsFlow = remember(context) {
        context.focusDataStore.data.map<Preferences, TimerSettings?> { prefs ->
            TimerSettings(
                focusMinutes = (prefs[FOCUS_MINUTES_KEY] ?: 25).coerceIn(1, 180),
                shortBreakMinutes = (prefs[SHORT_BREAK_KEY] ?: 5).coerceIn(1, 180),
                longBreakMinutes = (prefs[LONG_BREAK_KEY] ?: 15).coerceIn(1, 180),
                isDark = prefs[DARK_THEME_KEY] ?: true,
                completedSessions = (prefs[COMPLETED_SESSIONS_KEY] ?: 0)
                    .coerceAtLeast(0),
                sessionDate = prefs[SESSION_DATE_KEY] ?: "",
                dailyGoal = (prefs[DAILY_GOAL_KEY] ?: 4).coerceIn(1, 20)
            )
        }
    }

    val savedSettings by savedSettingsFlow.collectAsState(initial = null)

    var settingsLoaded by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(TimerMode.FOCUS) }

    var selectedTab by remember { mutableStateOf("Timer") }

    var focusMinutes by remember { mutableStateOf(25) }
    var shortBreakMinutes by remember { mutableStateOf(5) }
    var longBreakMinutes by remember { mutableStateOf(15) }

    var focusInput by remember { mutableStateOf("25") }
    var shortInput by remember { mutableStateOf("5") }
    var longInput by remember { mutableStateOf("15") }
    var settingsMessage by remember { mutableStateOf("") }

    var seconds by remember { mutableStateOf(25 * 60) }
    var running by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var completedSessions by remember { mutableStateOf(0) }
    // Prevent the polling loop from counting the same completed focus timer twice.
    var completionHandled by remember { mutableStateOf(false) }
    var isDark by remember { mutableStateOf(true) }

    var dailyGoal by remember { mutableStateOf(4) }
    var dailyGoalInput by remember { mutableStateOf("4") }
    // Load saved settings once when DataStore first returns its values.
    LaunchedEffect(savedSettings) {
        val saved = savedSettings
        if (saved != null && !settingsLoaded) {
            focusMinutes = saved.focusMinutes
            shortBreakMinutes = saved.shortBreakMinutes
            longBreakMinutes = saved.longBreakMinutes

            focusInput = saved.focusMinutes.toString()
            shortInput = saved.shortBreakMinutes.toString()
            longInput = saved.longBreakMinutes.toString()

            isDark = saved.isDark

            if (saved.sessionDate == todayKey()) {
                completedSessions = saved.completedSessions
            } else {
                // Start a fresh daily goal when the calendar date changes.
                completedSessions = 0
                context.focusDataStore.edit { prefs ->
                    prefs[COMPLETED_SESSIONS_KEY] = 0
                    prefs[SESSION_DATE_KEY] = todayKey()
                }
            }

            seconds = saved.focusMinutes * 60
            settingsLoaded = true
            dailyGoal = saved.dailyGoal
            dailyGoalInput = saved.dailyGoal.toString()
        }
    }

    LaunchedEffect(
        savedSettings?.completedSessions,
        savedSettings?.sessionDate,
        settingsLoaded
    ) {
        if (!settingsLoaded) return@LaunchedEffect

        val saved = savedSettings ?: return@LaunchedEffect

        completedSessions = if (saved.sessionDate == todayKey()) {
            saved.completedSessions
        } else {
            0
        }
    }


    fun durationFor(selectedMode: TimerMode): Int {
        val minutes = when (selectedMode) {
            TimerMode.FOCUS -> focusMinutes
            TimerMode.SHORT_BREAK -> shortBreakMinutes
            TimerMode.LONG_BREAK -> longBreakMinutes
        }
        return minutes * 60
    }

    val backgroundColor =
        if (isDark) Color(0xFF10111B) else Color(0xFFF6F3FC)
    val cardColor =
        if (isDark) Color(0xFF1B1D2B) else Color.White
    val textColor =
        if (isDark) Color(0xFFF5F2FF) else Color(0xFF25243A)
    val mutedColor =
        if (isDark) Color(0xFFAAA9C1) else Color(0xFF77748D)
    val accentColor = Color(0xFFB9A2FF)
    val trackColor =
        if (isDark) Color(0xFF343449) else Color(0xFFE8E1F4)

    val currentDuration = durationFor(mode)
    val progress = (
            1f - seconds.toFloat() / currentDuration.coerceAtLeast(1)
            ).coerceIn(0f, 1f)

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "timerProgress"
    )

    val timerScale by animateFloatAsState(
        targetValue = if (completed) 1.06f else 1f,
        animationSpec = spring(),
        label = "timerCompletionScale"
    )

    // Countdown and session completion.

    // Countdown and session completion.

    LaunchedEffect(settingsLoaded) {
        if (!settingsLoaded) return@LaunchedEffect

        val runtimePrefs = context.getSharedPreferences(
            TimerForegroundService.PREFS_NAME,
            Context.MODE_PRIVATE
        )

        while (true) {
            val isRunning = runtimePrefs.getBoolean(
                TimerForegroundService.KEY_RUNNING,
                false
            )
            val endTime = runtimePrefs.getLong(
                TimerForegroundService.KEY_END_TIME,
                0L
            )
            val savedMode = runtimePrefs.getString(
                TimerForegroundService.KEY_MODE,
                "FOCUS"
            ) ?: "FOCUS"

            if (isRunning && endTime > 0L) {
                val remaining = (
                        (endTime - System.currentTimeMillis() + 999L) / 1000L
                        ).toInt().coerceAtLeast(0)

                seconds = remaining
                running = remaining > 0

                if (remaining == 0) {
                    completed = true
                }

                val restoredMode = runCatching {
                    TimerMode.valueOf(savedMode)
                }.getOrNull()

                if (restoredMode != null && mode != restoredMode) {
                    mode = restoredMode
                }
            } else {
                val remaining = runtimePrefs.getInt(
                    TimerForegroundService.KEY_REMAINING,
                    -1
                )

                if (remaining >= 0) {
                    seconds = remaining
                }

                running = false

                if (runtimePrefs.getBoolean(
                        TimerForegroundService.KEY_COMPLETED,
                        false
                    )
                ) {
                    completed = true
                    val completedEndTime = runtimePrefs.getLong(
                        TimerForegroundService.KEY_END_TIME,
                        0L
                    )
                    val completionPrefs = context.getSharedPreferences(
                        "focus_timer_completion_tracking",
                        Context.MODE_PRIVATE
                    )
                    val lastCountedEndTime = completionPrefs.getLong(
                        "last_counted_end_time",
                        -1L
                    )

                    // Only completed Focus sessions count toward the daily goal.
                    if (savedMode == TimerMode.FOCUS.name &&
                        completedEndTime > 0L &&
                        completedEndTime != lastCountedEndTime &&
                        !completionHandled
                    ) {
                        completionHandled = true
                        val today = todayKey()
                        // Persist atomically with the current date so yesterday's count never carries over.
                        scope.launch {
                            var newCount = 1
                            context.focusDataStore.edit { prefs ->
                                val existing = if (prefs[SESSION_DATE_KEY] == today) {
                                    prefs[COMPLETED_SESSIONS_KEY] ?: 0
                                } else 0
                                newCount = existing + 1
                                prefs[COMPLETED_SESSIONS_KEY] = newCount
                                prefs[SESSION_DATE_KEY] = today
                            }
                            completedSessions = newCount
                            completionPrefs.edit()
                                .putLong("last_counted_end_time", completedEndTime)
                                .apply()
                        }
                    }
                } else {
                    completionHandled = false
                }
            }

            delay(500L)
        }
    }


    val goalProgress =
        (completedSessions.toFloat() / dailyGoal).coerceIn(0f, 1f)

    val minutesText = "%02d:%02d".format(
        seconds / 60,
        seconds % 60
    )

    Scaffold(
        containerColor = backgroundColor,
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                cardColor = cardColor,
                accentColor = accentColor,
                mutedColor = mutedColor
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(backgroundColor)
        ) {
            Column(

                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                // Header and theme toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "little by little ✦",
                            fontSize = 13.sp,
                            color = accentColor
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Focus Space",
                            fontSize = 27.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = cardColor,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                val newTheme = !isDark
                                isDark = newTheme
                                scope.launch {
                                    context.focusDataStore.edit { prefs ->
                                        prefs[DARK_THEME_KEY] = newTheme
                                    }
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                if (isDark) "☀" else "☾",
                                fontSize = 23.sp,
                                color = accentColor
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    "A little progress is still progress. ♡",
                    fontSize = 14.sp,
                    color = mutedColor,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))
                if (selectedTab == "Modes") {
                    Text(
                        "Choose your mood ✨",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Switch your timer to match what you need right now.",
                        modifier = Modifier.fillMaxWidth(),
                        fontSize = 13.sp,
                        color = mutedColor
                    )
                    Spacer(Modifier.height(18.dp))

                    TimerMode.values().forEach { timerMode ->
                        val selected = mode == timerMode
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    resetBackgroundTimer(context, durationFor(timerMode), timerMode)
                                    mode = timerMode
                                    seconds = durationFor(timerMode)
                                    running = false
                                    completed = false
                                    completionHandled = false
                                },
                            shape = RoundedCornerShape(24.dp),
                            color = if (selected) accentColor.copy(alpha = if (isDark) 0.22f else 0.28f) else cardColor,
                            border = androidx.compose.foundation.BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) accentColor else mutedColor.copy(alpha = 0.18f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = accentColor.copy(alpha = 0.18f),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(timerMode.emoji(), fontSize = 25.sp)
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        timerMode.label,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        timerMode.description(),
                                        fontSize = 12.sp,
                                        color = mutedColor
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "${durationFor(timerMode) / 60} minutes",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accentColor
                                    )
                                }
                                if (selected) {
                                    Text("✓", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = accentColor)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { selectedTab = "Timer" },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Back to timer", color = textColor)
                    }
                    Spacer(Modifier.height(16.dp))
                }
                if (selectedTab == "Timer") {
                    // Main timer card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(30.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardColor
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 26.dp, horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Always show the active mode on the timer screen, not just in Modes.
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = accentColor.copy(alpha = if (isDark) 0.20f else 0.24f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(mode.emoji(), fontSize = 16.sp)
                                    Text(
                                        mode.label.uppercase(Locale.getDefault()),
                                        fontSize = 12.sp,
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                }
                            }

                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = when {
                                    completed && mode == TimerMode.FOCUS -> "Focus session complete! ✨"
                                    completed -> "Your ${mode.label.lowercase(Locale.getDefault())} is complete! ✨"
                                    running && mode == TimerMode.FOCUS -> "You're in your focus zone"
                                    running && mode == TimerMode.SHORT_BREAK -> "Slow down and breathe"
                                    running && mode == TimerMode.LONG_BREAK -> "Give yourself room to recharge"
                                    mode == TimerMode.FOCUS -> mode.description() + " 🌷"
                                    else -> mode.description() + " ♡"
                                },
                                fontSize = 14.sp,
                                color = mutedColor,
                                textAlign = TextAlign.Center
                            )

                            Spacer(Modifier.height(20.dp))

                            Box(
                                modifier = Modifier
                                    .size(244.dp)
                                    .scale(timerScale),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val stroke = 12.dp.toPx()
                                    val arcSize = Size(
                                        size.width - stroke,
                                        size.height - stroke
                                    )
                                    val topLeft = Offset(stroke / 2, stroke / 2)

                                    drawArc(
                                        color = trackColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        topLeft = topLeft,
                                        size = arcSize,
                                        style = Stroke(
                                            width = stroke,
                                            cap = StrokeCap.Round
                                        )
                                    )

                                    if (animatedProgress > 0f) {
                                        drawArc(
                                            color = accentColor,
                                            startAngle = -90f,
                                            sweepAngle = 360f * animatedProgress,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(
                                                width = stroke,
                                                cap = StrokeCap.Round
                                            )
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(mode.emoji(), fontSize = 24.sp, color = accentColor)
                                    Text(
                                        minutesText,
                                        fontSize = 43.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Text(
                                        mode.label.uppercase(Locale.getDefault()),
                                        fontSize = 10.sp,
                                        letterSpacing = 2.sp,
                                        color = mutedColor
                                    )
                                }
                            }

                            Spacer(Modifier.height(24.dp))

                            Button(

                                onClick = {
                                    if (seconds == 0) {
                                        seconds = currentDuration
                                        completed = false
                                        completionHandled = false
                                        startBackgroundTimer(context, seconds, mode)
                                        running = true
                                    } else if (running) {
                                        pauseBackgroundTimer(context)
                                        running = false
                                        completed = false
                                    } else {
                                        completed = false
                                        completionHandled = false
                                        startBackgroundTimer(context, seconds, mode)
                                        running = true
                                    }
                                },

                                enabled = settingsLoaded,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = Color(0xFF201833)
                                )
                            ) {
                                Text(
                                    when {
                                        running -> "Ⅱ   Pause gently"
                                        seconds == 0 -> "↻   Start again"
                                        else -> "▶   Let's begin"
                                    },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            OutlinedButton(

                                onClick = {
                                    resetBackgroundTimer(context, currentDuration, mode)
                                    running = false
                                    seconds = currentDuration
                                    completed = false
                                    completionHandled = false
                                },

                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("Reset timer", color = textColor)
                            }

                            AnimatedVisibility(
                                visible = completed,
                                enter = fadeIn() + scaleIn()
                            ) {
                                Text(
                                    text = if (mode == TimerMode.FOCUS) {
                                        "You showed up for yourself. Proud of you! 🌷"
                                    } else {
                                        "Rest is productive, too. ♡"
                                    },
                                    modifier = Modifier.padding(top = 20.dp),
                                    fontSize = 14.sp,
                                    color = accentColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Daily goal card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardColor
                        )
                    ) {
                        Column(Modifier.padding(20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "Your daily blooms 🌷",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textColor
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Every session is a little win",
                                        fontSize = 12.sp,
                                        color = mutedColor
                                    )
                                }

                                Text(
                                    "$completedSessions/$dailyGoal",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(trackColor, CircleShape)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(goalProgress)
                                        .height(10.dp)
                                        .background(accentColor, CircleShape)
                                )
                            }

                            Spacer(Modifier.height(10.dp))

                            Text(
                                text = if (completedSessions >= dailyGoal) {
                                    "You reached your goal! You did amazing ✨"
                                } else {
                                    "${dailyGoal - completedSessions} more focus " +
                                            if (dailyGoal - completedSessions == 1) {
                                                "session to reach your goal ♡"
                                            } else {
                                                "sessions to reach your goal ♡"
                                            }
                                },
                                fontSize = 13.sp,
                                color = mutedColor
                            )

                            Spacer(Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = {
                                    completedSessions = 0
                                    scope.launch {
                                        context.focusDataStore.edit { prefs ->
                                            prefs[COMPLETED_SESSIONS_KEY] = 0
                                            prefs[SESSION_DATE_KEY] = todayKey()
                                        }
                                    }
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("Reset daily progress")
                            }
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                }
                if (selectedTab == "Settings") {
                    // Custom timer settings
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = cardColor
                        )
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text(
                                "Customize your timers ⚙️",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )

                            Spacer(Modifier.height(5.dp))

                            Text(
                                "Choose a duration from 1 to 180 minutes.",
                                fontSize = 12.sp,
                                color = mutedColor
                            )

                            Spacer(Modifier.height(16.dp))

                            DurationField(
                                label = "Focus",
                                value = focusInput,
                                onValueChange = { focusInput = it },
                                textColor = textColor,
                                accentColor = accentColor,
                                cardColor = backgroundColor
                            )

                            Spacer(Modifier.height(10.dp))

                            DurationField(
                                label = "Short Break",
                                value = shortInput,
                                onValueChange = { shortInput = it },
                                textColor = textColor,
                                accentColor = accentColor,
                                cardColor = backgroundColor
                            )

                            Spacer(Modifier.height(10.dp))

                            DurationField(
                                label = "Long Break",
                                value = longInput,
                                onValueChange = { longInput = it },
                                textColor = textColor,
                                accentColor = accentColor,
                                cardColor = backgroundColor
                            )

                            Spacer(Modifier.height(20.dp))

                            Text(
                                "Daily focus goal 🌷",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "How many Focus sessions would you like to finish each day?",
                                fontSize = 12.sp,
                                color = mutedColor
                            )
                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(2, 4, 6, 8).forEach { goal ->
                                    val selected = dailyGoalInput == goal.toString()
                                    OutlinedButton(
                                        onClick = { dailyGoalInput = goal.toString() },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (selected) accentColor.copy(alpha = 0.22f) else Color.Transparent,
                                            contentColor = textColor
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (selected) accentColor else mutedColor.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Text(
                                            goal.toString(),
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = dailyGoalInput,
                                onValueChange = { value ->
                                    if (value.all { it.isDigit() } && value.length <= 2) {
                                        dailyGoalInput = value
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Sessions per day (1–20)") },
                                supportingText = { Text("Choose a preset above or enter any number from 1 to 20.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(18.dp),
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = accentColor,
                                    focusedLabelColor = accentColor,
                                    unfocusedLabelColor = mutedColor,
                                    focusedTextColor = textColor,
                                    unfocusedTextColor = textColor,
                                    cursorColor = accentColor,
                                    unfocusedContainerColor = backgroundColor,
                                    focusedContainerColor = backgroundColor
                                )
                            )

                            Spacer(Modifier.height(14.dp))

                            Button(
                                onClick = {
                                    val f = focusInput.toIntOrNull()
                                    val s = shortInput.toIntOrNull()
                                    val l = longInput.toIntOrNull()
                                    val goal = dailyGoalInput.toIntOrNull()

                                    if (
                                        f != null && f in 1..180 &&
                                        s != null && s in 1..180 &&
                                        l != null && l in 1..180 &&
                                        goal != null && goal in 1..20
                                    ) {
                                        focusMinutes = f
                                        shortBreakMinutes = s
                                        longBreakMinutes = l
                                        dailyGoal = goal

                                        running = false
                                        seconds = when (mode) {
                                            TimerMode.FOCUS -> f * 60
                                            TimerMode.SHORT_BREAK -> s * 60
                                            TimerMode.LONG_BREAK -> l * 60
                                        }
                                        resetBackgroundTimer(context, seconds, mode)
                                        completed = false
                                        completionHandled = false
                                        settingsMessage = "Timer settings saved! ♡"

                                        scope.launch {
                                            context.focusDataStore.edit { prefs ->
                                                prefs[FOCUS_MINUTES_KEY] = f
                                                prefs[SHORT_BREAK_KEY] = s
                                                prefs[LONG_BREAK_KEY] = l
                                                prefs[DAILY_GOAL_KEY] = goal
                                            }
                                        }
                                        settingsMessage = "Your settings and daily goal are saved! ♡"
                                    } else {
                                        settingsMessage =
                                            "Timer durations must be 1–180 and daily goal 1–20."
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = Color(0xFF201833)
                                )
                            ) {
                                Text(
                                    "Apply Settings",
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (settingsMessage.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    settingsMessage,
                                    fontSize = 12.sp,
                                    color = accentColor
                                )
                            }

                            Spacer(Modifier.height(6.dp))
                        }
                        Text(
                            "Current: Focus ${focusMinutes}m · " +
                                    "Short ${shortBreakMinutes}m · " +
                                    "Long ${longBreakMinutes}m",
                            fontSize = 11.sp,
                            color = mutedColor
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                if (selectedTab == "Timer") {
                    Text(
                        text = if (completedSessions >= dailyGoal) {
                            "You bloomed today. Take a little bow. 🌙"
                        } else {
                            "You don't have to do it all at once. ♡"
                        },
                        fontSize = 13.sp,
                        color = mutedColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    cardColor: Color,
    accentColor: Color,
    mutedColor: Color
) {
    val tabs = listOf(
        "Timer" to Icons.Default.Timer,
        "Modes" to Icons.Default.Layers,
        "Settings" to Icons.Default.Settings
    )

    NavigationBar(
        containerColor = cardColor
    ) {
        tabs.forEach { (title, icon) ->
            NavigationBarItem(
                selected = selectedTab == title,
                onClick = { onTabSelected(title) },
                icon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = title
                    )
                },
                label = { Text(title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = accentColor,
                    selectedTextColor = accentColor,
                    unselectedIconColor = mutedColor,
                    unselectedTextColor = mutedColor,
                    indicatorColor = accentColor.copy(alpha = 0.18f)
                )
            )
        }
    }
}

@Composable
fun DurationField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    textColor: Color,
    accentColor: Color,
    cardColor: Color
) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (newValue.all { it.isDigit() } && newValue.length <= 3) {
                onValueChange(newValue)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("$label duration (minutes)") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number
        ),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            focusedLabelColor = accentColor,
            unfocusedLabelColor = textColor.copy(alpha = 0.65f),
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            cursorColor = accentColor,
            unfocusedContainerColor = cardColor,
            focusedContainerColor = cardColor
        )
    )
}
