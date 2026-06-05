package com.motogp.fantasy.data.repository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.motogp.fantasy.data.model.User
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserRepo @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    fun currentUser(): Flow<User?> {
        val uid = auth.currentUser?.uid ?: return flowOf(null)
        return callbackFlow {
            val sub = db.collection("users").document(uid).addSnapshotListener { snap, _ ->
                trySend(snap?.toObject(User::class.java))
            }
            awaitClose { sub.remove() }
        }
    }

    suspend fun updatePref(field: String, value: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).update(field, value).await()
    }
}
