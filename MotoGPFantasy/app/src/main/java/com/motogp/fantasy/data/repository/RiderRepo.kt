package com.motogp.fantasy.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.motogp.fantasy.data.model.Rider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RiderRepo @Inject constructor(private val db: FirebaseFirestore) {

    fun riders(): Flow<List<Rider>> = callbackFlow {
        val sub = db.collection("riders")
            .addSnapshotListener { snap, error ->
                if (error != null) {
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        Rider(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            team = doc.getString("team") ?: "",
                            number = (doc.getLong("number") ?: 0L).toInt(),
                            nationality = doc.getString("nationality") ?: "",
                            imageUrl = doc.getString("imageUrl") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            points = (doc.getLong("points") ?: 0L).toInt()
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list.sortedByDescending { it.price })
            }
        awaitClose { sub.remove() }
    }

    suspend fun refresh() { /* Firestore listener handles updates in real time */ }
}
