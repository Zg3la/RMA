package com.motogp.fantasy.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.motogp.fantasy.data.CURRENT_SEASON
import com.motogp.fantasy.data.model.FirestoreResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResultsRepo @Inject constructor(
    private val db: FirebaseFirestore
) {
    fun results(season: Int = CURRENT_SEASON): Flow<List<FirestoreResult>> = callbackFlow {
        val sub = db.collection("results")
            .whereEqualTo("season", season)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        FirestoreResult(
                            raceName = doc.getString("raceName") ?: "",
                            round = (doc.getLong("round") ?: 0L).toInt(),
                            sprintWinner = doc.getString("sprintWinner") ?: "",
                            sprintSecond = doc.getString("sprintSecond") ?: "",
                            sprintThird = doc.getString("sprintThird") ?: "",
                            raceWinner = doc.getString("raceWinner") ?: "",
                            raceSecond = doc.getString("raceSecond") ?: "",
                            raceThird = doc.getString("raceThird") ?: "",
                            season = (doc.getLong("season") ?: season.toLong()).toInt(),
                            processed = doc.getBoolean("processed") ?: false
                        )
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()
                trySend(list.sortedByDescending { it.round })
            }
        awaitClose { sub.remove() }
    }

    fun resultForRace(raceName: String, season: Int = CURRENT_SEASON): Flow<FirestoreResult?> = callbackFlow {
        val sub = db.collection("results")
            .whereEqualTo("raceName", raceName)
            .whereEqualTo("season", season)
            .limit(1)
            .addSnapshotListener { snap, _ ->
                val result = snap?.documents?.firstOrNull()?.let { doc ->
                    FirestoreResult(
                        raceName = doc.getString("raceName") ?: "",
                        round = (doc.getLong("round") ?: 0L).toInt(),
                        sprintWinner = doc.getString("sprintWinner") ?: "",
                        sprintSecond = doc.getString("sprintSecond") ?: "",
                        sprintThird = doc.getString("sprintThird") ?: "",
                        raceWinner = doc.getString("raceWinner") ?: "",
                        raceSecond = doc.getString("raceSecond") ?: "",
                        raceThird = doc.getString("raceThird") ?: "",
                        season = (doc.getLong("season") ?: season.toLong()).toInt()
                    )
                }
                trySend(result)
            }
        awaitClose { sub.remove() }
    }
}
