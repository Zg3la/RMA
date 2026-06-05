package com.motogp.fantasy.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.motogp.fantasy.data.model.FantasyTeam
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// Extended FantasyTeam with constructorId
data class FantasyTeamFull(
    val userId: String = "",
    val riderIds: List<String> = emptyList(),
    val constructorId: String? = null,
    val budgetUsed: Double = 0.0,
    val season: Int = 2025,
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
            val sub = db.collection("teams").document("${u}_2025")
                .addSnapshotListener { snap, _ ->
                    if (snap == null || !snap.exists()) { trySend(null); return@addSnapshotListener }
                    val team = FantasyTeamFull(
                        userId = snap.getString("userId") ?: "",
                        riderIds = (snap.get("riderIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        constructorId = snap.getString("constructorId"),
                        budgetUsed = snap.getDouble("budgetUsed") ?: 0.0,
                        season = (snap.getLong("season") ?: 2025L).toInt(),
                        isLocked = snap.getBoolean("isLocked") ?: false
                    )
                    trySend(team)
                }
            awaitClose { sub.remove() }
        }
    }

    suspend fun saveTeam(riderIds: List<String>, budget: Double, constructorId: String? = null) {
        val u = uid ?: return
        val data = hashMapOf(
            "userId" to u,
            "riderIds" to riderIds,
            "constructorId" to constructorId,
            "budgetUsed" to budget,
            "season" to 2025,
            "isLocked" to false,
            "lastUpdated" to System.currentTimeMillis()
        )
        db.collection("teams").document("${u}_2025").set(data).await()
    }
}
