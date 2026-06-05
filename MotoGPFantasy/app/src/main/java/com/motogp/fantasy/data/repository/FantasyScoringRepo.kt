package com.motogp.fantasy.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton

private const val SCORING_TAG = "FantasyScoringRepo"
private const val FIRST_PLACE_POINTS = 7
private const val SECOND_PLACE_POINTS = 4
private const val THIRD_PLACE_POINTS = 2

@Singleton
class FantasyScoringRepo @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val raceRepo: RaceRepo,
    private val sportsDbResultsRepo: SportsDbResultsRepo
) {
    suspend fun updateCurrentUserScore(): Int {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return 0

        return try {
            val riderIds = loadTeamRiderIds(uid)
            if (riderIds.isEmpty()) {
                updateLeagueScores(uid, 0)
                return 0
            }

            val selectedRiderNames = loadSelectedRiderNames(riderIds)
            if (selectedRiderNames.isEmpty()) {
                updateLeagueScores(uid, 0)
                return 0
            }

            raceRepo.refresh()
            val finishedRaces = raceRepo.races.first()
                .filter { it.status == "finished" }
                .sortedBy { it.round }

            val totalPoints = finishedRaces.sumOf { race ->
                val podium = sportsDbResultsRepo.resultForRace(race)
                    ?.rows
                    .orEmpty()
                    .filter { it.position in 1..3 }

                podium.sumOf { row ->
                    if (normalize(row.rider) !in selectedRiderNames) {
                        0
                    } else {
                        when (row.position) {
                            1 -> FIRST_PLACE_POINTS
                            2 -> SECOND_PLACE_POINTS
                            3 -> THIRD_PLACE_POINTS
                            else -> 0
                        }
                    }
                }
            }

            updateLeagueScores(uid, totalPoints)
            totalPoints
        } catch (e: Exception) {
            Log.e(SCORING_TAG, "Score update failed: ${e.message}", e)
            0
        }
    }

    private suspend fun loadTeamRiderIds(uid: String): List<String> {
        val currentSeasonTeam = db.collection("teams").document("${uid}_2026").get().await()
        val team = if (currentSeasonTeam.exists()) {
            currentSeasonTeam
        } else {
            db.collection("teams").document("${uid}_2025").get().await()
        }

        return (team.get("riderIds") as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
    }

    private suspend fun loadSelectedRiderNames(riderIds: List<String>): Set<String> {
        val riders = db.collection("riders").get().await()
        return riders.documents
            .filter { it.id in riderIds }
            .mapNotNull { it.getString("name") }
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private suspend fun updateLeagueScores(uid: String, totalPoints: Int) {
        val leaguesSnap = db.collection("leagues")
            .whereArrayContains("memberIds", uid)
            .get()
            .await()

        for (leagueDoc in leaguesSnap.documents) {
            val scoreRef = db.collection("leagues")
                .document(leagueDoc.id)
                .collection("scores")
                .document(uid)

            val currentScore = scoreRef.get().await()
            scoreRef.set(
                mapOf(
                    "totalPoints" to totalPoints,
                    "displayName" to (currentScore.getString("displayName")?.takeIf { it.isNotBlank() }
                        ?: auth.currentUser?.displayName
                        ?: auth.currentUser?.email?.substringBefore("@")
                        ?: "Racer"),
                    "avatarUrl" to (currentScore.getString("avatarUrl") ?: auth.currentUser?.photoUrl?.toString().orEmpty()),
                    "userId" to uid
                ),
                SetOptions.merge()
            ).await()
        }
    }

    private fun normalize(value: String): String {
        val withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")

        return withoutAccents
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
    }
}
