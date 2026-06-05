package com.motogp.fantasy.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.motogp.fantasy.data.model.Constructor
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConstructorRepo @Inject constructor(private val db: FirebaseFirestore) {

    fun constructors(): Flow<List<Constructor>> = callbackFlow {
        val sub = db.collection("constructors")
            .addSnapshotListener { snap, error ->
                if (error != null) { trySend(emptyList()); return@addSnapshotListener }
                val list = snap?.documents?.mapNotNull { doc ->
                    try {
                        Constructor(
                            id = doc.id,
                            name = doc.getString("name") ?: "",
                            price = doc.getDouble("price") ?: 0.0,
                            imageUrl = doc.getString("imageUrl") ?: ""
                        )
                    } catch (e: Exception) { null }
                } ?: emptyList()
                trySend(list.sortedByDescending { it.price })
            }
        awaitClose { sub.remove() }
    }
}
