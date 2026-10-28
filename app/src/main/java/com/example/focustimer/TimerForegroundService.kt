
package com.example.focustimer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TimerForegroundService : Service() {

    private val serviceScope =
        CoroutineScope(Dispatchers.IO + Job())

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()

        notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_START -> {
                val endTime = intent.getLongExtra(
                    EXTRA_END_TIME,
                    0L
                )

                val mode = intent.getStringExtra(
                    EXTRA_MODE
                ) ?: "FOCUS"

                if (endTime <= System.currentTimeMillis()) {
                    stopSelf()
                    return START_NOT_STICKY
                }

                saveTimerState(
                    endTime,
                    true,
                    mode,
                    false
                )

                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(
                        "Focus Timer",
                        "Your timer is running."
                    )
                )

                startCountdown(endTime, mode)
            }

            ACTION_PAUSE -> {
                val endTime = getSavedEndTime()

                val remaining =
                    if (endTime > System.currentTimeMillis()) {
                        (
                                (endTime -
                                        System.currentTimeMillis() +
                                        999L) / 1000L
                                ).toInt()
                    } else {
                        0
                    }

                saveTimerState(
                    0L,
                    false,
                    getSavedMode(),
                    false
                )

                saveRemainingSeconds(remaining)

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }


            ACTION_RESET -> {
                val remaining = intent.getIntExtra(
                    EXTRA_REMAINING,
                    0
                )

                val mode = intent.getStringExtra(EXTRA_MODE)
                    ?: getSavedMode()

                saveTimerState(
                    0L,
                    false,
                    mode,
                    false
                )

                saveRemainingSeconds(remaining)

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

        }

        return START_NOT_STICKY
    }

    private fun startCountdown(
        endTime: Long,
        mode: String
    ) {
        serviceScope.launch {

            while (System.currentTimeMillis() < endTime) {

                val remaining = (
                        (
                                endTime -
                                        System.currentTimeMillis() +
                                        999L
                                ) / 1000L
                        ).toInt().coerceAtLeast(0)

                saveRemainingSeconds(remaining)

                notificationManager.notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        "Focus Timer",
                        "$remaining seconds remaining"
                    )
                )

                delay(1000L)
            }

            saveTimerState(
                0L,
                false,
                mode,
                true
            )

            saveRemainingSeconds(0)

            if (mode == "FOCUS") {
                incrementCompletedSessions()
                android.util.Log.d(
                    "FocusTimer",
                    "Focus session count increment requested"
                )
            }

            showCompletionNotification(mode)

            showCompletionNotification(mode)

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun incrementCompletedSessions() {
        val today = SimpleDateFormat(
            "yyyy-MM-dd",
            Locale.getDefault()
        ).format(Date())

        focusDataStore.edit { prefs ->

            val previousCount =
                if (prefs[SESSION_DATE_KEY] == today) {
                    prefs[COMPLETED_SESSIONS_KEY] ?: 0
                } else {
                    0
                }

            prefs[COMPLETED_SESSIONS_KEY] =
                previousCount + 1

            prefs[SESSION_DATE_KEY] = today
        }
    }

    private fun saveTimerState(
        endTime: Long,
        running: Boolean,
        mode: String,
        completed: Boolean
    ) {
        getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putLong(KEY_END_TIME, endTime)
            .putBoolean(KEY_RUNNING, running)
            .putString(KEY_MODE, mode)
            .putBoolean(KEY_COMPLETED, completed)
            .apply()
    }

    private fun saveRemainingSeconds(seconds: Int) {
        getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
            .edit()
            .putInt(KEY_REMAINING, seconds)
            .apply()
    }

    private fun getSavedEndTime(): Long {
        return getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getLong(KEY_END_TIME, 0L)
    }

    private fun getSavedMode(): String {
        return getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        ).getString(KEY_MODE, "FOCUS") ?: "FOCUS"
    }

    private fun buildNotification(
        title: String,
        message: String
    ): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_lock_idle_alarm
            )
            .setContentTitle(title)
            .setContentText(message)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showCompletionNotification(mode: String) {

        val message = when (mode) {
            "FOCUS" ->
                "Great work! Time for a break."

            "SHORT_BREAK" ->
                "Ready to focus again?"

            "LONG_BREAK" ->
                "Refreshed? Let's get back to work."

            else ->
                "Your timer has finished."
        }

        val notification = NotificationCompat.Builder(
            this,
            COMPLETION_CHANNEL_ID
        )
            .setSmallIcon(
                android.R.drawable.ic_lock_idle_alarm
            )
            .setContentTitle("Timer complete!")
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .build()

        notificationManager.notify(
            COMPLETION_NOTIFICATION_ID,
            notification
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            notificationManager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Running Focus Timer",
                    NotificationManager.IMPORTANCE_LOW
                )
            )

            notificationManager.createNotificationChannel(
                NotificationChannel(
                    COMPLETION_CHANNEL_ID,
                    "Timer Completion",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    enableVibration(true)
                }
            )
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {

        const val ACTION_START =
            "com.example.focustimer.START"

        const val ACTION_PAUSE =
            "com.example.focustimer.PAUSE"

        const val ACTION_RESET =
            "com.example.focustimer.RESET"

        const val EXTRA_END_TIME = "end_time"
        const val EXTRA_MODE = "timer_mode"
        const val EXTRA_REMAINING = "remaining_seconds_extra"

        const val PREFS_NAME = "timer_runtime"

        const val KEY_END_TIME = "end_time"
        const val KEY_RUNNING = "running"
        const val KEY_MODE = "mode"
        const val KEY_REMAINING = "remaining_seconds"
        const val KEY_COMPLETED = "completed"

        private const val CHANNEL_ID =
            "running_timer_channel"

        private const val COMPLETION_CHANNEL_ID =
            "timer_finished_channel"

        private const val NOTIFICATION_ID = 1002

        private const val COMPLETION_NOTIFICATION_ID = 1003
    }
}
