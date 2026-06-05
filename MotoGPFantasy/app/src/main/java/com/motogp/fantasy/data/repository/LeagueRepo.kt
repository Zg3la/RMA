package com.motogp.fantasy.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.motogp.fantasy.data.model.League
import com.motogp.fantasy.data.model.LeaderboardEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

private const val TAG = "LeagueRepo"

@Singleton
class LeagueRepo @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth
) {
    private val uid get() = auth.currentUser?.uid ?: ""

    private fun getDisplayName(): String {
        // Synchronous — use auth directly, no suspend needed for this
        val name = auth.currentUser?.displayName
        val email = auth.currentUser?.email
        Log.d(TAG, "getDisplayName: displayName=$name email=$email uid=$uid")
        return when {
            !name.isNullOrBlank() -> name
            !email.isNullOrBlank() -> email.substringBefore("@")
            else -> "Racer"
        }
    }

    private fun getAvatarUrl(): String {
        return auth.currentUser?.photoUrl?.toString() ?: ""
    }

    private suspend fun writeScoreEntry(leagueId: String, rank: Int) {
        if (uid.isEmpty()) {
            Log.e(TAG, "writeScoreEntry: uid is empty, cannot write")
            return
        }
        val displayName = getDisplayName()
        val avatarUrl = getAvatarUrl()
        val data = hashMapOf(
            "displayName" to displayName,
            "avatarUrl" to avatarUrl,
            "totalPoints" to 0,
            "currentRank" to rank,
            "previousRank" to rank,
            "userId" to uid
        )
        Log.d(TAG, "Writing score entry for $uid ($displayName) in league $leagueId")
        try {
            db.collection("leagues")
                .document(leagueId)
                .collection("scores")
                .document(uid)
                .set(data, SetOptions.merge())
                .await()
            Log.d(TAG, "Score entry written successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write score entry: ${e.message}", e)
            throw e // rethrow so caller knows it failed
        }
    }

    fun myLeagues(): Flow<List<League>> = callbackFlow {
        if (uid.isEmpty()) { trySend(emptyList()); close(); return@callbackFlow }
        val sub = db.collection("leagues")
            .whereArrayContains("memberIds", uid)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "myLeagues error: ${error.message}")
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        League(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            code = doc.getString("code") ?: "",
                            isPublic = doc.getBoolean("isPublic") ?: false,
                            memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            createdBy = doc.getString("createdBy") ?: "",
                            season = (doc.getLong("season") ?: 2025L).toInt()
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }

    fun publicLeagues(): Flow<List<League>> = callbackFlow {
        val sub = db.collection("leagues")
            .whereEqualTo("isPublic", true)
            .limit(20)
            .addSnapshotListener { snap, _ ->
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        League(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            code = doc.getString("code") ?: "",
                            isPublic = true,
                            memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                            createdBy = doc.getString("createdBy") ?: "",
                            season = (doc.getLong("season") ?: 2025L).toInt()
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { sub.remove() }
    }

    fun leaderboard(leagueId: String): Flow<List<LeaderboardEntry>> = callbackFlow {
        Log.d(TAG, "Starting leaderboard listener for league $leagueId, uid=$uid")
        val sub = db.collection("leagues").document(leagueId)
            .collection("scores")
            .orderBy("totalPoints", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    Log.e(TAG, "Leaderboard error: ${error.message}")
                    // If index error, try without orderBy as fallback
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                Log.d(TAG, "Leaderboard snapshot: ${snap?.documents?.size} docs")
                snap?.documents?.forEach { doc ->
                    Log.d(TAG, "  doc: ${doc.id} -> displayName=${doc.getString("displayName")}")
                }
                CoroutineScope(Dispatchers.IO).launch {
                    val list = snap?.documents?.mapNotNull { doc ->
                        try {
                            LeaderboardEntry(
                                userId = doc.id,
                                displayName = doc.getString("displayName")?.takeIf { it.isNotBlank() } ?: "Racer",
                                avatarUrl = doc.getString("avatarUrl") ?: "",
                                totalPoints = (doc.getLong("totalPoints") ?: 0L).toInt(),
                                currentRank = (doc.getLong("currentRank") ?: 0L).toInt(),
                                previousRank = (doc.getLong("previousRank") ?: 0L).toInt(),
                                isCurrentUser = doc.id == uid
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }?.toMutableList() ?: mutableListOf()

                    try {
                        val leagueDoc = db.collection("leagues").document(leagueId).get().await()
                        val memberIds = (leagueDoc.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                        val scoreUserIds = list.map { it.userId }.toSet()

                        val missingIds = memberIds.filter { it !in scoreUserIds }
                        for (missingId in missingIds) {
                            val userDoc = db.collection("users").document(missingId).get().await()
                            val name = userDoc.getString("displayName")?.takeIf { it.isNotBlank() } 
                                ?: userDoc.getString("email")?.substringBefore("@") 
                                ?: "Racer"
                            val avatar = userDoc.getString("avatarUrl") ?: ""

                            list.add(LeaderboardEntry(
                                userId = missingId,
                                displayName = name,
                                avatarUrl = avatar,
                                totalPoints = 0,
                                currentRank = list.size + 1,
                                previousRank = list.size + 1,
                                isCurrentUser = missingId == uid
                            ))
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching missing users: ${e.message}")
                    }

                    trySend(list)
                }
            }
        awaitClose { sub.remove() }
    }

    suspend fun createLeague(name: String, isPublic: Boolean): League {
        if (uid.isEmpty()) throw Exception("Not logged in")
        Log.d(TAG, "Creating league: $name, uid=$uid")
        val code = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[Random.nextInt(32)] }.joinToString("")
        val data = hashMapOf(
            "name" to name,
            "code" to code,
            "isPublic" to isPublic,
            "memberIds" to listOf(uid),
            "createdBy" to uid,
            "season" to 2025
        )
        val ref = db.collection("leagues").add(data).await()
        Log.d(TAG, "League created: ${ref.id}, now writing score entry")
        writeScoreEntry(ref.id, 1)
        return League(id = ref.id, name = name, code = code, isPublic = isPublic, memberIds = listOf(uid), createdBy = uid)
    }

    suspend fun joinLeague(code: String): Result<League> {
        return try {
            Log.d(TAG, "Joining league with code: $code, uid=$uid")
            val snap = db.collection("leagues")
                .whereEqualTo("code", code.uppercase())
                .limit(1)
                .get().await()
            val doc = snap.documents.firstOrNull()
                ?: return Result.failure(Exception("League not found. Check the code."))
            val memberIds = (doc.get("memberIds") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val league = League(
                id = doc.id,
                name = doc.getString("name") ?: "",
                code = doc.getString("code") ?: "",
                isPublic = doc.getBoolean("isPublic") ?: false,
                memberIds = memberIds,
                createdBy = doc.getString("createdBy") ?: ""
            )
            if (uid !in memberIds) {
                db.collection("leagues").document(doc.id)
                    .update("memberIds", FieldValue.arrayUnion(uid)).await()
            }
            Log.d(TAG, "Joined league ${doc.id}, writing score entry")
            writeScoreEntry(doc.id, memberIds.size + 1)
            Result.success(league)
        } catch (e: Exception) {
            Log.e(TAG, "joinLeague failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun fixMyScoreEntries() {
        if (uid.isEmpty()) return
        Log.d(TAG, "fixMyScoreEntries for uid=$uid")
        try {
            val leagues = db.collection("leagues")
                .whereArrayContains("memberIds", uid)
                .get().await()
            Log.d(TAG, "Found ${leagues.documents.size} leagues to fix")
            for (doc in leagues.documents) {
                try {
                    writeScoreEntry(doc.id, 1)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to fix entry for league ${doc.id}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fixMyScoreEntries failed: ${e.message}")
        }
    }

    suspend fun deleteLeague(leagueId: String) {
        if (uid.isEmpty()) throw Exception("Not logged in")
        val leagueDoc = db.collection("leagues").document(leagueId).get().await()
        if (leagueDoc.getString("createdBy") == uid) {
            val scores = db.collection("leagues").document(leagueId).collection("scores").get().await()
            for (score in scores.documents) {
                db.collection("leagues").document(leagueId).collection("scores").document(score.id).delete()
            }
            db.collection("leagues").document(leagueId).delete().await()
        } else {
            throw Exception("Only the creator can delete this league.")
        }
    }
}
