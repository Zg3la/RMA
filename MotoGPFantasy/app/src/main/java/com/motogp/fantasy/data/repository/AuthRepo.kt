package com.motogp.fantasy.data.repository

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.motogp.fantasy.data.model.User
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepo @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {
    val isLoggedIn get() = auth.currentUser != null
    val uid get() = auth.currentUser?.uid

    suspend fun signInWithGoogle(account: GoogleSignInAccount): Result<Unit> {
        return try {
            val cred = GoogleAuthProvider.getCredential(account.idToken, null)
            val result = auth.signInWithCredential(cred).await()
            val fu = result.user ?: throw Exception("Auth failed")
            val user = User(
                uid = fu.uid,
                displayName = fu.displayName ?: "Racer",
                email = fu.email ?: "",
                avatarUrl = fu.photoUrl?.toString() ?: ""
            )
            db.collection("users").document(fu.uid).set(user).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut() {
        auth.signOut()
    }
}
