package com.chy.muscletome.data.timer

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.chy.muscletome.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-surviving rest timer: an ongoing countdown notification plus an
 * [AlarmManager]-scheduled beep at the end. The on-screen ring stays an
 * in-app coroutine; this manager makes the *user-facing cue* immune to
 * screen-off, leaving the workout screen, and process death.
 *
 * The countdown state persists in SharedPreferences so the timer can be
 * restored when the app reopens mid-rest.
 */
@Singleton
class RestTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Starts (or restarts) a rest countdown of [totalSeconds]. */
    fun startRest(totalSeconds: Int, exerciseName: String) {
        if (totalSeconds <= 0) {
            cancel()
            return
        }
        val endWallMs = System.currentTimeMillis() + (totalSeconds * 1000L)
        persist(endWallMs, exerciseName)
        scheduleAlarm(totalSeconds)
        showNotification(exerciseName, endWallMs)
    }

    /** Adds [seconds] to the active countdown; no-op when none is active. */
    fun addSeconds(seconds: Int) {
        val end = prefs().getLong(KEY_END_WALL_MS, 0L)
        if (end <= System.currentTimeMillis()) return
        val exerciseName = prefs().getString(KEY_EXERCISE, "").orEmpty()
        val newEnd = end + (seconds * 1000L)
        persist(newEnd, exerciseName)
        scheduleAlarm(((newEnd - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(1))
        showNotification(exerciseName, newEnd)
    }

    /** Seconds remaining, or 0 when no countdown is active. */
    fun remainingSeconds(): Int {
        val end = prefs().getLong(KEY_END_WALL_MS, 0L)
        if (end <= 0L) return 0
        return ((end - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
    }

    /** Cancels the countdown, its notification, and the pending beep. */
    fun cancel() {
        prefs().edit().clear().apply()
        alarmManager.cancel(alarmPendingIntent())
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun persist(endWallMs: Long, exerciseName: String) {
        prefs().edit()
            .putLong(KEY_END_WALL_MS, endWallMs)
            .putString(KEY_EXERCISE, exerciseName)
            .apply()
    }

    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleAlarm(seconds: Int) {
        val triggerAt = SystemClock.elapsedRealtime() + (seconds * 1000L)
        val pi = alarmPendingIntent()
        val canBeExact = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
        if (canBeExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pi,
            )
        } else {
            // SCHEDULE_EXACT_ALARM is denied by default on Android 13+.
            // The notification chronometer stays exact (SystemUI renders it
            // from the `when` timestamp); only the beep may be delayed a few
            // minutes in deep doze.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pi,
            )
        }
    }

    private fun showNotification(exerciseName: String, endWallMs: Long) {
        val manager = NotificationManagerCompat.from(context)
        // Explicit permission check (required by lint on API 33+):
        // areNotificationsEnabled() alone is the pre-33 equivalent.
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!manager.areNotificationsEnabled()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Rest timer",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val launch =
            context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        val contentPi = PendingIntent.getActivity(
            context,
            CONTENT_REQUEST_CODE,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val endTime = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(endWallMs))
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rest · ${exerciseName.ifBlank { "workout" }}")
            .setContentText("Ends $endTime")
            .setWhen(endWallMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setSilent(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPi)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun prefs() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        ALARM_REQUEST_CODE,
        Intent(context, RestAlarmReceiver::class.java)
            .setAction(RestAlarmReceiver.ACTION_REST_FINISHED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val PREFS_NAME = "rest_timer"
        private const val KEY_END_WALL_MS = "end_wall_ms"
        private const val KEY_EXERCISE = "exercise_name"
        private const val CHANNEL_ID = "rest_timer"
        private const val NOTIFICATION_ID = 1001
        private const val ALARM_REQUEST_CODE = 1002
        private const val CONTENT_REQUEST_CODE = 1003

        /**
         * End-of-rest side effects: clear the persisted countdown, drop the
         * summary notification, and play the completion beep.
         *
         * Device-setting limitations, for the user:
         * - The tone plays on the **notification** stream, so turn the
         *   notification volume up (not just media) to hear it.
         * - Silent/vibrate mode and Do-Not-Disturb silence the tone, as
         *   they silence any other notification sound; a normal heads-up
         *   notification is still posted for DND bypassed users only.
         * - The chronometer notification ("Ends 3:42 PM") updates
         *   regardless — the beep is the part that can be silenced.
         */
        fun handleRestFinished(context: Context, onDone: () -> Unit = {}) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().clear().apply()
            try {
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
            playCompletionCue(onDone)
        }

        /**
         * Plays the short completion tone and reports completion through
         * [onDone] so the caller can keep its broadcast alive while the
         * sound plays ([RestAlarmReceiver] waits ~900 ms).
         */
        fun playCompletionCue(onDone: () -> Unit = {}) {
            val tone = try {
                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
            } catch (_: RuntimeException) {
                onDone()
                return // no audio resource available right now
            }
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
            Handler(Looper.getMainLooper()).postDelayed({
                tone.release()
                onDone()
            }, 800)
        }
    }
}
