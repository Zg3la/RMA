package com.motogp.fantasy.service

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.motogp.fantasy.data.model.LeaderboardEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LeagueOvertakeNotifier @Inject constructor(
    @ApplicationContext context: Context,
    private val auth: FirebaseAuth
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val lastScoresByLeague = mutableMapOf<String, Map<String, Int>>()

    fun onLeaderboardChanged(
        leagueId: String,
        leagueName: String,
        entries: List<LeaderboardEntry>
    ) {
        val uid = auth.currentUser?.uid ?: return
        val myEntry = entries.firstOrNull { it.userId == uid || it.isCurrentUser } ?: return
        val previousScores = lastScoresByLeague[leagueId]
        val previousMyPoints = previousScores?.get(myEntry.userId)
        val rivalsAhead = entries.filter { entry ->
            entry.userId != myEntry.userId && entry.totalPoints > myEntry.totalPoints
        }
        val rivalsAheadIds = rivalsAhead.map { it.userId }.toSet()

        clearPassedRivals(uid, leagueId, rivalsAheadIds)

        val notifiedKeys = notifiedKeys()
        val newRivalsAhead = rivalsAhead.filter { rival ->
            val key = notificationKey(uid, leagueId, rival.userId)
            val wasAhead = previousScores?.let { scores ->
                val rivalPreviousPoints = scores[rival.userId]
                rivalPreviousPoints != null &&
                    previousMyPoints != null &&
                    rivalPreviousPoints > previousMyPoints
            } ?: false

            key !in notifiedKeys && (previousScores == null || !wasAhead)
        }

        if (newRivalsAhead.isNotEmpty()) {
            val title = "Leaderboard update"
            val body = overtakeMessage(newRivalsAhead, leagueName)
            if (MotoGpNotifier.show(appContext, title, body)) {
                rememberNotified(newRivalsAhead.map { rival ->
                    notificationKey(uid, leagueId, rival.userId)
                }.toSet())
            }
        }

        lastScoresByLeague[leagueId] = entries.associate { it.userId to it.totalPoints }
    }

    fun clearRuntimeState() {
        lastScoresByLeague.clear()
    }

    private fun clearPassedRivals(uid: String, leagueId: String, rivalsAheadIds: Set<String>) {
        val prefix = "$uid|$leagueId|"
        val remaining = notifiedKeys().filterTo(mutableSetOf()) { key ->
            !key.startsWith(prefix) || key.removePrefix(prefix) in rivalsAheadIds
        }
        prefs.edit().putStringSet(KEY_NOTIFIED_OVERTAKES, remaining).apply()
    }

    private fun rememberNotified(keys: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_NOTIFIED_OVERTAKES, notifiedKeys() + keys)
            .apply()
    }

    private fun notifiedKeys(): Set<String> {
        return prefs.getStringSet(KEY_NOTIFIED_OVERTAKES, emptySet()).orEmpty().toSet()
    }

    private fun overtakeMessage(rivals: List<LeaderboardEntry>, leagueName: String): String {
        val firstRival = rivals.first().displayName
        return if (rivals.size == 1) {
            "$firstRival moved ahead of you in $leagueName."
        } else {
            "$firstRival and ${rivals.size - 1} others moved ahead of you in $leagueName."
        }
    }

    private fun notificationKey(uid: String, leagueId: String, rivalUid: String): String {
        return "$uid|$leagueId|$rivalUid"
    }

    companion object {
        private const val PREFS_NAME = "league_overtake_notifications"
        private const val KEY_NOTIFIED_OVERTAKES = "notified_overtake_keys"
    }
}
