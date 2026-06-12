package com.watch.watchtofriend

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.watch.watchtofriend.data.FirebaseBootstrap
import com.watch.watchtofriend.data.TurnConfig
import com.watch.watchtofriend.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WatchApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        instance = this
        super.onCreate()

        // ContentProvider da ayarlar; yedek olarak burada tekrar (FcmService'den önce)
        FirebaseBootstrap.configureFirestore()

        try { com.watch.watchtofriend.ui.theme.ThemePref.load(this) } catch (_: Exception) {}

        NotificationHelper.createChannels(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                NotificationHelper.isAppInForeground = true
            }
            override fun onStop(owner: LifecycleOwner) {
                NotificationHelper.isAppInForeground = false
                NotificationHelper.activeDmId = null
                NotificationHelper.activeRoomId = null
            }
        })

        appScope.launch {
            try { TurnConfig.init() } catch (_: Exception) {}
        }

        try { FcmService.refreshToken() } catch (_: Exception) {}
        try { NotificationWorker.schedule(this) } catch (_: Exception) {}

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("WatchApplication", "Yakalanmayan hata (${thread.name})", throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        lateinit var instance: WatchApplication
            private set
    }
}
