package com.motogp.fantasy.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.motogp.fantasy.data.model.Race
import com.motogp.fantasy.data.model.RaceSession
import com.motogp.fantasy.data.model.User
import com.motogp.fantasy.data.repository.RaceRepo
import com.motogp.fantasy.data.repository.RaceWeekendSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RaceNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val raceRepo: RaceRepo
) {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val scheduledAlarms = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun schedule(races: List<Race>, user: User?) {
        cancelScheduledNotifications()
        if (user == null) return

        races
            .filter { it.status == "upcoming" || it.status == "live" }
            .forEach { race ->
                val detail = raceRepo.getRoundDetail(race)
                val practiceOne = detail.sessions.firstOrNull { it.type == "Free Practice 1" }
                val mainRace = detail.sessions.firstOrNull { it.type == "Race" }

                if (user.notifyDeadline && practiceOne != null) {
                    scheduleTeamLockNotification(race, practiceOne)
                }

                if (user.notifyRaceStart && mainRace != null) {
                    scheduleRaceStartNotification(race, mainRace)
                }
            }
    }

    private fun scheduleTeamLockNotification(race: Race, session: RaceSession) {
        val trackStart = RaceWeekendSchedule.sessionStartAtTrack(race, session) ?: return
        val triggerAt = trackStart.minusMinutes(TEAM_LOCK_NOTICE_MINUTES_BEFORE)
        enqueue(
            uniqueName = "team-lock-${race.round}",
            triggerAt = triggerAt,
            title = "Team lock deadline",
            body = "${race.name}: team locks in $TEAM_LOCK_NOTICE_MINUTES_BEFORE minutes. Free Practice 1 starts at ${session.time} local track time (${trackStart.zone.id})."
        )
    }

    private fun scheduleRaceStartNotification(race: Race, session: RaceSession) {
        val trackStart = RaceWeekendSchedule.sessionStartAtTrack(race, session) ?: return
        val triggerAt = trackStart.minusMinutes(RACE_NOTICE_MINUTES_BEFORE)
        enqueue(
            uniqueName = "race-start-${race.round}",
            triggerAt = triggerAt,
            title = "Race starting soon",
            body = "${race.name}: race starts in $RACE_NOTICE_MINUTES_BEFORE minutes (${session.time} local track time, ${trackStart.zone.id})."
        )
    }

    private fun enqueue(
        uniqueName: String,
        triggerAt: ZonedDateTime,
        title: String,
        body: String,
        remember: Boolean = true
    ): Boolean {
        val delay = Duration.between(Instant.now(), triggerAt.toInstant())
        if (delay.isNegative || delay.isZero) return false

        val pendingIntent = pendingIntent(
            uniqueName = uniqueName,
            title = title,
            body = body,
            flags = PendingIntent.FLAG_UPDATE_CURRENT
        ) ?: return false
        val triggerAtMillis = triggerAt.toInstant().toEpochMilli()

        if (canScheduleExactAlarms()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }

        if (remember) rememberScheduled(uniqueName)
        return true
    }

    private fun canScheduleExactAlarms(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
    }

    private fun cancelScheduledNotifications() {
        scheduledKeys().forEach { uniqueName ->
            pendingIntent(
                uniqueName = uniqueName,
                title = "",
                body = "",
                flags = PendingIntent.FLAG_NO_CREATE
            )?.let { pendingIntent ->
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
        scheduledAlarms.edit().remove(KEY_SCHEDULED_ALARMS).apply()
    }

    private fun rememberScheduled(uniqueName: String) {
        scheduledAlarms.edit()
            .putStringSet(KEY_SCHEDULED_ALARMS, scheduledKeys() + uniqueName)
            .apply()
    }

    private fun scheduledKeys(): Set<String> {
        return scheduledAlarms.getStringSet(KEY_SCHEDULED_ALARMS, emptySet()).orEmpty()
    }

    private fun pendingIntent(
        uniqueName: String,
        title: String,
        body: String,
        flags: Int
    ): PendingIntent? {
        val intent = Intent(appContext, RaceNotificationAlarmReceiver::class.java).apply {
            action = RaceNotificationAlarmReceiver.ACTION_SHOW_NOTIFICATION
            data = Uri.parse("motogp-fantasy://notification/$uniqueName")
            putExtra(RaceNotificationAlarmReceiver.EXTRA_TITLE, title)
            putExtra(RaceNotificationAlarmReceiver.EXTRA_BODY, body)
        }
        return PendingIntent.getBroadcast(
            appContext,
            uniqueName.hashCode(),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val PREFS_NAME = "race_notification_alarms"
        private const val KEY_SCHEDULED_ALARMS = "scheduled_alarm_keys"
        private const val TEAM_LOCK_NOTICE_MINUTES_BEFORE = 30L
        private const val RACE_NOTICE_MINUTES_BEFORE = 15L
    }
}
