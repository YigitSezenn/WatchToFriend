package com.watch.watchtofriend.data.repository

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.model.User
import kotlinx.coroutines.tasks.await

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    val currentUser: FirebaseUser? get() = auth.currentUser

    // Discord tarzı benzersiz kısa kimlik (karışık harfler hariç).
    private fun newFriendCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..6).map { chars.random() }.joinToString("")
    }

    fun getGoogleSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.google_web_client_id))
            .requestEmail()
            .build()
        val client: GoogleSignInClient = GoogleSignIn.getClient(context, gso)
        client.signOut() // önceki oturumu temizle, hesap seçiciyi hep göster
        return client.signInIntent
    }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = runCatching {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        val user = result.user ?: error("Firebase auth result is empty")
        // İlk kez giriş yapıyorsa Firestore'a kaydet
        val doc = db.collection("users").document(user.uid).get().await()
        if (!doc.exists()) {
            val userData = User(
                uid = user.uid,
                displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Kullanıcı",
                email = user.email ?: "",
                friendCode = newFriendCode()
            )
            db.collection("users").document(user.uid).set(userData).await()
        }
        user
    }

    suspend fun login(email: String, password: String): Result<FirebaseUser> = runCatching {
        val user = auth.signInWithEmailAndPassword(email, password).await().user
            ?: error("Firebase auth result is empty")
        ensureUserDocument(user)
        user
    }

    /** Oturum açıkken Firestore kullanıcı kaydı yoksa oluşturur. */
    suspend fun ensureCurrentUserDocument() {
        auth.currentUser?.let { ensureUserDocument(it) }
    }

    /** E-posta ile giriş yapan ama Firestore kaydı silinmiş/eski hesaplar için. */
    private suspend fun ensureUserDocument(user: FirebaseUser) {
        val ref = db.collection("users").document(user.uid)
        if (!ref.get().await().exists()) {
            ref.set(
                User(
                    uid = user.uid,
                    displayName = user.displayName ?: user.email?.substringBefore("@") ?: "Kullanıcı",
                    email = user.email ?: "",
                    friendCode = newFriendCode()
                )
            ).await()
        }
    }

    suspend fun register(email: String, password: String, displayName: String): Result<FirebaseUser> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val user = result.user ?: error("Firebase auth result is empty")
        val userData = User(uid = user.uid, displayName = displayName, email = email, friendCode = newFriendCode())
        db.collection("users").document(user.uid).set(userData).await()
        user
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
    }

    fun logout() = auth.signOut()
}
