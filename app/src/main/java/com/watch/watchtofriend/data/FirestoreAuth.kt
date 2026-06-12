package com.watch.watchtofriend.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

/** Firestore dinleyicilerinden önce Auth token'ının geçerli olduğundan emin olur. */
object FirestoreAuth {

    private const val TAG = "FirestoreAuth"

    suspend fun ensureReady(): Boolean {
        val user = FirebaseAuth.getInstance().currentUser ?: return false
        return try {
            user.getIdToken(true).await()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Auth token yenilenemedi, oturum kapatılıyor: ${e.message}")
            FirebaseAuth.getInstance().signOut()
            false
        }
    }

    /** PERMISSION_DENIED → silinmiş/geçersiz oturum; kullanıcıyı çıkışa zorla. */
    fun handleListenerError(err: FirebaseFirestoreException?): Boolean {
        if (err?.code != FirebaseFirestoreException.Code.PERMISSION_DENIED) return false
        Log.w(TAG, "Firestore PERMISSION_DENIED — oturum kapatılıyor")
        FirebaseAuth.getInstance().signOut()
        return true
    }
}
