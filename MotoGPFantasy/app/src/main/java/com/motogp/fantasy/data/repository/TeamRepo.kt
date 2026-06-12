package com.motogp.fantasy.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.motogp.fantasy.data.CURRENT_SEASON
import com.motogp.fantasy.data.PREVIOUS_TEAM_SEASON
import com.motogp.fantasy.data.teamDocumentId
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

data class FantasyTeamFull(
    val userId: String = "",
    val riderIds: List<String> = emptyList(),
    val constructorId: String? = null,
    val budgetUsed: Double = 0.0,
    val season: Int = CURRENT_SEASON,
    val isLocked: Boolean = false
)

@Singleton
class TeamRepo @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    private val uid get() = auth.currentUser?.uid

    fun myTeam(): Flow<FantasyTeamFull?> {
        val u = uid ?: return flowOf(null)
        return callbackFlow {
            var currentTeam: FantasyTeamFull? = null
            var previousTeam: FantasyTeamFull? = null
            var migrationStarted = false

            fun publish() {
                val team = currentTeam ?: previousTeam
                trySend(team)

                val teamToMigrate = previousTeam
                if (currentTeam == null && teamToMigrate != null && !migrationStarted) {
                    migrationStarted = true
                    launch { runCatching { migratePreviousTeam(u, teamToMigrate) } }
                }
            }

            val currentSub = db.collection("teams").document(teamDocumentId(u))
                .addSnapshotListener { snap, _ ->
                    currentTeam = snap.toFantasyTeamFull()
                    publish()
                }

            val previousSub = db.collection("teams").document(teamDocumentId(u, PREVIOUS_TEAM_SEASON))
                .addSnapshotListener { snap, _ ->
                    previousTeam = snap.toFantasyTeamFull()
                    publish()
                }

            awaitClose {
                currentSub.remove()
                previousSub.remove()
            }
        }
    }

    suspend fun saveTeam(riderIds: List<String>, budget: Double, constructorId: String? = null) {
        val u = uid ?: return
        val data = hashMapOf(
            "userId" to u,
            "riderIds" to riderIds,
            "constructorId" to constructorId,
            "budgetUsed" to budget,
            "season" to CURRENT_SEASON,
            "isLocked" to false,
            "lastUpdated" to System.currentTimeMillis()
        )
        db.collection("teams").document(teamDocumentId(u)).set(data).await()
    }

    private suspend fun migratePreviousTeam(userId: String, team: FantasyTeamFull) {
        val currentRef = db.collection("teams").document(teamDocumentId(userId))
        if (currentRef.get().await().exists()) return

        currentRef.set(
            mapOf(
                "userId" to userId,
                "riderIds" to team.riderIds,
                "constructorId" to team.constructorId,
                "budgetUsed" to team.budgetUsed,
                "season" to CURRENT_SEASON,
                "isLocked" to team.isLocked,
                "lastUpdated" to System.currentTimeMillis()
            )
        ).await()
    }

    private fun DocumentSnapshot?.toFantasyTeamFull(): FantasyTeamFull? {
        if (this == null || !exists()) return null
        return FantasyTeamFull(
            userId = getString("userId") ?: "",
            riderIds = (get("riderIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            constructorId = getString("constructorId"),
            budgetUsed = getDouble("budgetUsed") ?: 0.0,
            season = (getLong("season") ?: CURRENT_SEASON.toLong()).toInt(),
            isLocked = getBoolean("isLocked") ?: false
        )
    }
}
