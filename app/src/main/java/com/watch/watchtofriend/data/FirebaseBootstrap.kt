package com.watch.watchtofriend.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firestore'u ilk erişimden önce yapılandırır.
 *
 * Desktop (Firebase JS) HTTP/WebChannel kullanır; Android gRPC/HTTP2 kullanır.
 * Long polling yalnızca JS SDK'da vardır. Burada asıl fark: FcmService ve diğer
 * erken çağrılar ayarlar kilitlenmeden Firestore'u açıyordu; DNS/Wi‑Fi geçişinde
 * kanal UNAVAILABLE kalıyordu. Erken persistence + enableNetwork bunu giderir.
 */
object FirebaseBootstrap {

    private const val TAG = "FirebaseBootstrap"

    @Volatile
    private var configured = false

    /** İlk Firestore erişiminden ÖNCE çağrılmalı (ContentProvider veya Application.onCreate başı). */
    fun configureFirestore() {
        if (configured) return
        synchronized(this) {
            if (configured) return
            try {
                val settings = FirebaseFirestoreSettings.Builder()
                    .setPersistenceEnabled(true)
                    .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                    .build()
                FirebaseFirestore.getInstance().firestoreSettings = settings
                configured = true
                Log.i(TAG, "Firestore yapılandırıldı (persistence, erken init)")
            } catch (e: Exception) {
                Log.w(TAG, "Firestore ayarları uygulanamadı: ${e.message}")
            }
        }
    }

    /** Wi‑Fi geri gelince Firestore yazma/okuma kanalını yeniden aç. */
    fun onNetworkAvailable() {
        if (!configured) configureFirestore()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                FirebaseFirestore.getInstance().enableNetwork().await()
                Log.d(TAG, "Firestore ağı yeniden etkin")
            } catch (e: Exception) {
                Log.w(TAG, "enableNetwork: ${e.message}")
            }
        }
    }
}
