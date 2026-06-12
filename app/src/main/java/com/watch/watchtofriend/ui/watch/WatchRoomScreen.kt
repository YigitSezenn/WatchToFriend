package com.watch.watchtofriend.ui.watch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.media.projection.MediaProjectionManager
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.watch.watchtofriend.ui.theme.AvatarColors
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.model.Message
import com.watch.watchtofriend.data.model.Room
import com.watch.watchtofriend.data.model.User
import com.watch.watchtofriend.notifications.NotificationHelper
import com.watch.watchtofriend.ui.components.InitialAvatar
import com.watch.watchtofriend.invite.InviteLink
import com.watch.watchtofriend.ui.components.copyToClipboard
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern
import android.os.Message as OsMessage

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchRoomScreen(
    roomId: String,
    onBack: () -> Unit,
    vm: WatchViewModel = viewModel(factory = WatchViewModelFactory(roomId, androidx.compose.ui.platform.LocalContext.current.applicationContext))
) {
    val room by vm.room.collectAsState()
    val messages by vm.messages.collectAsState()
    val friends by vm.friends.collectAsState()
    val senderPhotos by vm.senderPhotos.collectAsState()
    val blocked by vm.blocked.collectAsState()
    val ytResults by vm.ytResults.collectAsState()
    val ytSearching by vm.ytSearching.collectAsState()
    val roomDeleted by vm.roomDeleted.collectAsState()
    val sentFriendRequests by vm.sentFriendRequests.collectAsState()
    var chatInput by remember { mutableStateOf("") }
    var showChatSearch by remember { mutableStateOf(false) }
    var chatQuery by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var exoController by remember { mutableStateOf<ExoController?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    // Ekran paylaşımı yöneticisi — composable ömrüne bağlı, dispose'da temizlenir
    val screenShareManager = remember(roomId) {
        ScreenShareManager(context.applicationContext, roomId, vm.uid)
    }
    DisposableEffect(screenShareManager) {
        onDispose { screenShareManager.dispose() }
    }
    DisposableEffect(roomId) {
        NotificationHelper.activeRoomId = roomId
        onDispose { NotificationHelper.activeRoomId = null }
    }

    // Sesli sohbet yöneticisi
    val voiceManager = remember(roomId, vm.uid) {
        VoiceManager(
            context.applicationContext,
            roomId,
            vm.uid,
            vm.myDisplayName.ifBlank { context.getString(R.string.common_user) },
            vm.myPhoto,
        )
    }
    LaunchedEffect(vm.myDisplayName, vm.myPhoto) {
        voiceManager.updateMyProfile(vm.myDisplayName, vm.myPhoto)
    }
    DisposableEffect(voiceManager) {
        onDispose { voiceManager.destroy() }
    }
    val isInVoice       by voiceManager.inVoice.collectAsState()
    val isVoiceJoining  by voiceManager.isJoining.collectAsState()

    // Sesli sohbet açılınca/kapanınca ekran sesi yönlendirmesini güncelle (kesik ses / düşük ses fix)
    var voiceAudioTipShown by remember { mutableStateOf(false) }
    LaunchedEffect(isInVoice) {
        RoomAudioRouter.setVoiceActive(context.applicationContext, isInVoice)
        if (isInVoice && !voiceAudioTipShown) {
            voiceAudioTipShown = true
            android.widget.Toast.makeText(
                context,
                context.getString(R.string.watch_headphone_hint),
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }
    val isVoiceMuted    by voiceManager.muted.collectAsState()
    val isDeafened      by voiceManager.deafened.collectAsState()
    val pushToTalk      by voiceManager.pushToTalk.collectAsState()
    val pttActive       by voiceManager.pttActive.collectAsState()
    val speakingUids    by voiceManager.speakingUids.collectAsState()
    val localMicLevel   by voiceManager.localMicLevel.collectAsState()
    val voicePeersList  by voiceManager.voicePeersList.collectAsState()
    val voiceEvents     by voiceManager.voiceEvents.collectAsState()
    val peerLocalMuted  by voiceManager.peerLocalMuted.collectAsState()
    val peerVolumes     by voiceManager.peerVolumes.collectAsState()
    val micGain         by voiceManager.micGain.collectAsState()
    val speakThreshold  by voiceManager.speakThreshold.collectAsState()
    val speakRmsUi = VoiceManager.thresholdToUi(speakThreshold)
    val voiceJoinError by voiceManager.joinError.collectAsState()

    LaunchedEffect(voiceJoinError) {
        val err = voiceJoinError ?: return@LaunchedEffect
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.watch_voice_join_failed, err),
            android.widget.Toast.LENGTH_LONG
        ).show()
        voiceManager.clearJoinError()
    }

    val isSharing by screenShareManager.isSharing.collectAsState()
    val remoteBitmap by screenShareManager.remoteBitmap.collectAsState()
    val remoteSharingUid by screenShareManager.sharerUid.collectAsState()
    val isScreenConnecting by screenShareManager.isConnecting.collectAsState()
    val localVideoTrack by screenShareManager.localVideoTrack.collectAsState()
    val remoteVideoTrack by screenShareManager.remoteVideoTrack.collectAsState()

    // Ses kanalı giriş/çıkış bildirimi
    LaunchedEffect(voiceEvents) {
        val last = voiceEvents.lastOrNull() ?: return@LaunchedEffect
        val msg = if (last.type == "joined")
            context.getString(R.string.watch_voice_joined, last.displayName)
        else
            context.getString(R.string.watch_voice_left, last.displayName)
        android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    // Ekran paylaşımı event'lerini dinle
    LaunchedEffect(screenShareManager) {
        screenShareManager.event.collect { event ->
            when (event) {
                is ScreenShareManager.ShareEvent.IceStatus ->
                    // Diagnostik — sadece logcat'e yaz, kullanıcıya gösterme
                    android.util.Log.d("ScreenShare", "ICE: ${event.status}")
                is ScreenShareManager.ShareEvent.Error ->
                    android.widget.Toast.makeText(context, event.msg, android.widget.Toast.LENGTH_LONG).show()
                is ScreenShareManager.ShareEvent.ShareBlocked ->
                    android.widget.Toast.makeText(context, context.getString(R.string.watch_screen_someone_sharing), android.widget.Toast.LENGTH_SHORT).show()
                is ScreenShareManager.ShareEvent.ZombieCleared ->
                    android.widget.Toast.makeText(context, context.getString(R.string.watch_screen_stale_cleared), android.widget.Toast.LENGTH_SHORT).show()
                else -> {}
            }
        }
    }

    // MediaProjection izni için launcher
    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val serviceIntent = android.content.Intent(context, ScreenShareService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            screenShareManager.startSharing(result.resultCode, result.data!!)
        }
    }

    // RECORD_AUDIO runtime izin launcher — izin verilirse tam mikrofon, verilmezse dinleme modunda katıl
    val micPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (voiceManager.inVoice.value) {
            if (granted) {
                if (voiceManager.enableMicrophone()) {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.watch_mic_enabled),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            voiceManager.join(hasMicPermission = granted)
            if (!granted) {
                android.widget.Toast.makeText(
                    context,
                    context.getString(R.string.watch_mic_listen_only),
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun requestMicAndJoinVoice() {
        if (isVoiceJoining || isInVoice) return
        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasPermission) voiceManager.join(hasMicPermission = true)
        else micPermLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
    }

    // Zamanlama geri sayımı (saniye cinsinden, null = zamanlama yok)
    val scheduledAt = room?.scheduledAt ?: 0L
    var scheduleCountdownSec by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(scheduledAt) {
        if (scheduledAt <= 0L) { scheduleCountdownSec = null; return@LaunchedEffect }
        while (true) {
            val diff = scheduledAt - System.currentTimeMillis()
            scheduleCountdownSec = if (diff > 0) diff / 1000 else 0
            if (diff <= 0) break
            kotlinx.coroutines.delay(1000)
        }
    }

    var showMenu by remember { mutableStateOf(false) }
    var showScreenSharePermDialog by remember { mutableStateOf(false) }
    var showInviteDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var showChangeVideo by remember { mutableStateOf(false) }
    var showParticipants by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    val videoPickerSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queueSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showRoomSettings by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var expandVideo by remember { mutableStateOf(false) }
    var videoPanelHidden by remember { mutableStateOf(false) }
    var ytPlaybackError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(room?.videoVersion, room?.videoUrl) { ytPlaybackError = null }
    // derivedStateOf: yalnızca room.hostUid / room.moderators değişince yeniden hesaplanır.
    // playerPosSec her saniye değiştiğinden doğrudan atama, gereksiz recomposition üretir.
    val isHost by remember { derivedStateOf { room?.hostUid == vm.uid } }
    val canControl by remember { derivedStateOf { isHost || (room?.moderators?.contains(vm.uid) == true) } }
    var hostAutoStarted by remember { mutableStateOf(false) }
    LaunchedEffect(room?.videoVersion) { hostAutoStarted = false }
    // onlineCount: yalnızca room.presence değişince yeniden hesaplanır.
    // playerPosSec her saniye değiştiğinde tetiklenen recomposition'larda atlanan bir hesap.
    val onlineCount by remember {
        derivedStateOf {
            val now = System.currentTimeMillis()
            room?.presence?.values?.count { now - it < 45000 } ?: 0
        }
    }
    val onlineUids by remember {
        derivedStateOf {
            val now = System.currentTimeMillis()
            room?.presence
                ?.filter { (_, ts) -> now - ts < 45000 }
                ?.keys
                ?.toList()
                ?: emptyList()
        }
    }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Picture-in-Picture: aktifken yalnızca video gösterilir, üst çubuk gizlenir.
    val pip = com.watch.watchtofriend.ui.components.LocalPip.current
    val isInPip = pip?.isInPip == true
    // "Tam ekran" = kullanıcı genişletti ya da PiP (sohbet gizlenir). Yatayda sohbet sağda kalır.
    val videoFull = expandVideo || isInPip

    // Immersive: yatayda ya da tam ekranda sistem çubuklarını gizle (daha geniş video).
    val view = androidx.compose.ui.platform.LocalView.current
    val immersive = isLandscape || expandVideo

    // Tam ekranda TopAppBar: giriş anında 2sn görünür, dokunmayla 3sn tekrar görünür
    var topBarVisible by remember { mutableStateOf(true) }
    // Her artışta LaunchedEffect yeniden tetiklenerek timer sıfırlanır
    var topBarShowTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(expandVideo, topBarShowTrigger) {
        if (!expandVideo) { topBarVisible = true; return@LaunchedEffect }
        topBarVisible = true
        val delay = if (topBarShowTrigger == 0) 2000L else 3000L
        kotlinx.coroutines.delay(delay)
        topBarVisible = false
    }
    fun showTopBarBriefly() {
        if (!expandVideo) return
        topBarShowTrigger++
    }

    BackHandler(enabled = expandVideo) {
        expandVideo = false
        topBarVisible = true
    }

    DisposableEffect(immersive) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowInsetsControllerCompat(it, view) }
        if (controller != null) {
            if (immersive) {
                controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                controller.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            // Ekrandan çıkınca çubukları geri getir
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
    var lastTypingAt by remember { mutableStateOf(0L) }
    var reactionDisplay by remember { mutableStateOf<String?>(null) }
    var lastReactionAt by remember { mutableStateOf(0L) }
    var reactionInitialized by remember { mutableStateOf(false) }
    // Kontrol isteği banner'ı: (isteyen ad, eylem) — geçici gösterilir
    var ctrlReqDisplay by remember { mutableStateOf<Pair<String, String>?>(null) }
    var lastCtrlReqAt by remember { mutableStateOf(0L) }
    var ctrlReqInitialized by remember { mutableStateOf(false) }

    var lastSyncedState by remember { mutableStateOf<Room?>(null) }
    // Oynatıcı (YouTube/video) hazır mı? Hazır olmadan seek/oynat uygulamak işe yaramaz.
    var playerReady by remember { mutableStateOf(false) }
    // Oynatıcının anlık konumu/süresi (ilerleme çubuğu + sapma göstergesi için)
    var playerPosSec by remember { mutableStateOf(0.0) }
    var playerDurSec by remember { mutableStateOf(0.0) }
    // WebView callback'lerinin her zaman GÜNCEL oda durumunu okuyabilmesi için
    val latestRoom = rememberUpdatedState(room)
    // Tıklama-yansıtma: en son uygulanan uzak tıklama zamanı
    var lastAppliedClickAt by remember { mutableStateOf(0L) }

    // Birleşik uzak komut: ExoPlayer varsa ona, yoksa WebView'a (YouTube/site) uygula.
    val applyRemoteUnified: (Boolean, Double, Boolean, Boolean) -> Unit = { isPlaying, sec, doSeek, force ->
        val exo = exoController
        if (exo != null) {
            exo.applyRemote(isPlaying, sec, doSeek, force)
        } else {
            webViewRef?.evaluateJavascript("applyRemote($isPlaying, $sec, $doSeek, $force)", null)
        }
    }

    // Eski odalarda isPlaying=false kalmışsa host otomatik başlatır (desktop parity)
    LaunchedEffect(canControl, room?.videoUrl, room?.isPlaying, room?.videoVersion, playerReady) {
        val r = room ?: return@LaunchedEffect
        if (!canControl || hostAutoStarted || r.isPlaying) return@LaunchedEffect
        if (extractYouTubeId(r.videoUrl) == null) return@LaunchedEffect
        hostAutoStarted = true
        vm.updateVideoState(true, r.currentPositionMs)
        if (playerReady) {
            applyRemoteUnified(true, r.currentPositionMs / 1000.0, true, true)
        }
    }
    // Oynatıcı hazır mı (WebView ya da Exo)
    val anyPlayerAttached = { webViewRef != null || exoController != null }

    // PiP uygunluğu: izleme ekranında video varken aç; ekrandan çıkınca kapat.
    LaunchedEffect(room?.videoUrl) {
        pip?.setEligible(!room?.videoUrl.isNullOrBlank())
        // Video eklenince ekran paylaşımı otomatik durur
        if (!room?.videoUrl.isNullOrBlank() && isSharing) {
            screenShareManager.stopSharing()
        }
    }
    DisposableEffect(Unit) {
        onDispose { pip?.setEligible(false) }
    }

    // Tek seferlik geri dönüş: hem roomDeleted hem de room==null tetiklenince
    // onBack() iki kez çağrılıp geri yığınını fazladan patlatmasın (uygulamadan çıkma bug'ı).
    var exited by remember { mutableStateOf(false) }
    val exitOnce = {
        if (!exited) {
            exited = true
            onBack()
        }
    }

    // Oda silindi/terk edildi → geri dön
    LaunchedEffect(roomDeleted) {
        if (roomDeleted) exitOnce()
    }

    // Host odayı silerse misafirde oda dokümanı null olur → ekranda asılı kalmasın, çık
    var roomWasLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(room) {
        if (room != null) roomWasLoaded = true
        else if (roomWasLoaded) exitOnce()
    }

    // Presence: odada olduğumuzu periyodik bildir; ekrandan çıkınca temizle
    LaunchedEffect(Unit) {
        while (isActive) {
            vm.heartbeat()
            kotlinx.coroutines.delay(15000)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            vm.clearMyPresence()
            com.watch.watchtofriend.ui.components.RatingPrefs.recordRoomSession(context)
        }
    }

    LaunchedEffect(vm) {
        vm.toast.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Periyodik yeniden-senkron: host oynatırken her ~8 sn kendi konumunu yayar,
    // böylece kayan üyeler manuel durdur/başlat gerekmeden otomatik hizalanır.
    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(5000)
            val r = latestRoom.value
            if (r != null && vm.canControl() && r.isPlaying && playerReady) {
                val exo = exoController
                if (exo != null) {
                    // ExoPlayer: kendi konumunu doğrudan yay
                    vm.updateVideoState(exo.player.isPlaying, exo.player.currentPosition)
                } else {
                    webViewRef?.evaluateJavascript("window.reportPosition && reportPosition()", null)
                }
            }
        }
    }

    // İlerleme/süre bildirimi (tüm istemciler, ~1sn) — çubuk + sapma göstergesi için.
    // Bu YAYINLAMAZ; yalnızca yerel oynatıcı durumunu okur.
    LaunchedEffect(playerReady) {
        while (isActive && playerReady) {
            webViewRef?.evaluateJavascript("window.reportProgress && reportProgress()", null)
            kotlinx.coroutines.delay(1000)
        }
    }

    // Uzak tıklamayı uygula (başkası ana-sayfada tıkladıysa aynı öğeye tıkla)
    var clickInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(room?.clickAt) {
        val r = room ?: return@LaunchedEffect
        // Odaya girerken mevcut (eski) tıklamayı uygulama; sadece bundan sonrakileri
        if (!clickInitialized) {
            clickInitialized = true
            lastAppliedClickAt = r.clickAt
            return@LaunchedEffect
        }
        if (r.clickAt > lastAppliedClickAt) {
            lastAppliedClickAt = r.clickAt
            if (r.clickBy != vm.uid && r.clickSel.isNotEmpty()) {
                val enc = java.net.URLEncoder.encode(r.clickSel, "UTF-8").replace("+", "%20")
                webViewRef?.evaluateJavascript(
                    "window.__applyRemoteClick && __applyRemoteClick(decodeURIComponent('$enc'))",
                    null
                )
            }
        }
    }

    // Hem oda durumu değişince hem de oynatıcı hazır olunca senkronu uygula.
    // Oynatıcı hazır değilken hiçbir şey uygulanmaz ve lastSyncedState set EDİLMEZ;
    // böylece hazır olunca ilk emisyon "last == null" sayılıp mevcut konuma zorla atlar.
    LaunchedEffect(room, playerReady) {
        val current = room ?: return@LaunchedEffect
        if (!anyPlayerAttached()) return@LaunchedEffect
        if (!playerReady) return@LaunchedEffect

        val last = lastSyncedState
        val isInitial = last == null
        // Sadece presence (canlı kullanıcı) değiştiyse — oynatmayı yeniden uygulama.
        // (heartbeat her ~15s oda dokümanını günceller; video alanları aynıysa atla.)
        if (last != null &&
            current.videoUrl == last.videoUrl &&
            current.videoVersion == last.videoVersion &&
            current.isPlaying == last.isPlaying &&
            current.currentPositionMs == last.currentPositionMs &&
            current.lastUpdatedBy == last.lastUpdatedBy
        ) {
            return@LaunchedEffect
        }
        val isYouTube = extractYouTubeId(current.videoUrl) != null
        // Sunucu-saati ile geçen süre (saat kayması telafisi)
        val elapsed = if (current.isPlaying) (vm.serverNow() - current.updatedAt).coerceAtLeast(0L) else 0L
        val effectiveMs = current.currentPositionMs + elapsed
        val positionSeconds = effectiveMs / 1000.0

        if (isInitial) {
            when {
                !isYouTube -> applyRemoteUnified(current.isPlaying, positionSeconds, true, false)
                current.isPlaying -> applyRemoteUnified(true, positionSeconds, true, false)
                canControl -> { /* host auto-start Firestore günceller */ }
                else -> applyRemoteUnified(false, positionSeconds, true, false)
            }
        } else if (current.lastUpdatedBy != vm.uid) {
            // Her uzak güncellemede oynat/durdur uygulanır; seek yalnızca gerçek kayma varsa.
            applyRemoteUnified(current.isPlaying, positionSeconds, true, false)
        }
        lastSyncedState = current
    }

    // Senkron self-heal: misafir (kontrol yetkisi olmayan) her saniye kendi oynatıcı
    // konumunu beklenen oda konumuyla kıyaslar; sapma 2sn'yi geçerse host yazmasını
    // beklemeden kendini hizalar. (playerPosSec ~1sn'de bir güncellenir.)
    // YouTube spinner'da takılırsa kademeli play yeniden dene (desktop parity)
    LaunchedEffect(playerReady, room?.videoVersion, room?.isPlaying) {
        val videoUrl = room?.videoUrl ?: return@LaunchedEffect
        if (!playerReady || extractYouTubeId(videoUrl) == null || room?.isPlaying != true) return@LaunchedEffect
        for (gapMs in listOf(400L, 800L, 1300L, 2500L, 4000L)) {
            kotlinx.coroutines.delay(gapMs)
            if (!playerReady) return@LaunchedEffect
            val r = latestRoom.value ?: return@LaunchedEffect
            if (!r.isPlaying || extractYouTubeId(r.videoUrl) == null) return@LaunchedEffect
            val elapsed = (vm.serverNow() - r.updatedAt).coerceAtLeast(0L)
            val expected = (r.currentPositionMs + elapsed) / 1000.0
            if (playerPosSec < 0.8 && playerDurSec > 1.0) {
                applyRemoteUnified(true, expected, true, true)
            }
        }
    }

    LaunchedEffect(playerPosSec) {
        val r = room ?: return@LaunchedEffect
        if (!playerReady) return@LaunchedEffect
        val elapsed = if (r.isPlaying) (vm.serverNow() - r.updatedAt).coerceAtLeast(0L) else 0L
        val expected = (r.currentPositionMs + elapsed) / 1000.0
        if (!vm.canControl()) {
            if (kotlin.math.abs(playerPosSec - expected) > 2.0) {
                applyRemoteUnified(r.isPlaying, expected, true, true)
            }
            return@LaunchedEffect
        }
        // Host: oda oynatılıyor ama yerel oynatıcı takıldıysa düzelt
        if (r.isPlaying && extractYouTubeId(r.videoUrl) != null
            && playerPosSec < 0.8 && playerDurSec > 1.0
            && kotlin.math.abs(playerPosSec - expected) > 1.0) {
            applyRemoteUnified(true, expected, true, true)
        }
    }

    // Video değişince ilerleme/süre değerlerini sıfırla (bayat çubuk görünmesin)
    LaunchedEffect(room?.videoVersion) {
        playerPosSec = 0.0
        playerDurSec = 0.0
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    // Emoji tepkisi gelince video üstünde kısa süre göster
    LaunchedEffect(room?.reactionAt) {
        val r = room ?: return@LaunchedEffect
        if (!reactionInitialized) {
            reactionInitialized = true
            lastReactionAt = r.reactionAt
            return@LaunchedEffect
        }
        if (r.reactionAt > lastReactionAt && r.reaction.isNotBlank()) {
            lastReactionAt = r.reactionAt
            reactionDisplay = r.reaction
            kotlinx.coroutines.delay(1800)
            reactionDisplay = null
        }
    }

    // Kontrol isteği gelince banner göster (isteğin sahibi kendi banner'ını görmez)
    LaunchedEffect(room?.ctrlReqAt) {
        val r = room ?: return@LaunchedEffect
        if (!ctrlReqInitialized) {
            ctrlReqInitialized = true
            lastCtrlReqAt = r.ctrlReqAt
            return@LaunchedEffect
        }
        if (r.ctrlReqAt > lastCtrlReqAt && r.ctrlReqBy != vm.uid && r.ctrlReqName.isNotBlank()) {
            lastCtrlReqAt = r.ctrlReqAt
            ctrlReqDisplay = r.ctrlReqName to r.ctrlReqAction
            kotlinx.coroutines.delay(7000)
            ctrlReqDisplay = null
        }
    }

    // Kontrol uygula (host/yetkili banner'daki düğmeye basınca): yerelde uygula + yay
    val applyControl: (Boolean) -> Unit = applyControl@{ play ->
        val r = room ?: return@applyControl
        val elapsed = if (r.isPlaying) (vm.serverNow() - r.updatedAt).coerceAtLeast(0L) else 0L
        val sec = (r.currentPositionMs + elapsed) / 1000.0
        applyRemoteUnified(play, sec, false, false)
        vm.updateVideoState(play, (sec * 1000).toLong())
    }

    val syncDrift: Double? by remember {
        derivedStateOf {
            val r = room
            if (!playerReady || r == null || playerDurSec <= 1.0) null
            else {
                val el = if (r.isPlaying) (vm.serverNow() - r.updatedAt).coerceAtLeast(0L) else 0L
                kotlin.math.abs(playerPosSec - (r.currentPositionMs + el) / 1000.0)
            }
        }
    }
    val syncDriftValue = syncDrift
    val syncColor = when {
        !playerReady -> androidx.compose.ui.graphics.Color(0xFFF5A623)
        syncDriftValue == null || syncDriftValue < 1.5 -> com.watch.watchtofriend.ui.theme.SuccessGreen
        syncDriftValue < 3.0 -> androidx.compose.ui.graphics.Color(0xFFF5A623)
        else -> MaterialTheme.colorScheme.error
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
          if (!isInPip && (!expandVideo || topBarVisible)) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.zIndex(10f).statusBarsPadding()
            ) {
                Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 2.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (isInVoice) voiceManager.leave()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showParticipants = true }
                            .padding(horizontal = 2.dp)
                    ) {
                        Text(
                            room?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.watch_room_default, roomId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(com.watch.watchtofriend.ui.theme.SuccessGreen)
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                stringResource(R.string.watch_online_count, onlineCount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                    if (onlineUids.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            onlineUids.take(3).forEach { uid ->
                                val name = room?.presenceNames?.get(uid) ?: "?"
                                val photo = if (uid == vm.uid) vm.myPhoto.ifBlank { null }
                                else friends.find { it.uid == uid }?.photoBase64?.ifBlank { null }
                                VoiceSpeakingAvatar(
                                    initial = name.take(1).uppercase(),
                                    name = name,
                                    photoBase64 = photo,
                                    isSpeaking = speakingUids.contains(uid),
                                    voiceConnected = isInVoice && uid == vm.uid,
                                    size = 28.dp
                                )
                            }
                            if (onlineUids.size > 3) {
                                Text(
                                    "+${onlineUids.size - 3}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    val iAmSpeaking = speakingUids.contains(vm.uid) || localMicLevel > 0.07f
                    IconButton(
                        onClick = { if (isInVoice) voiceManager.toggleMute() else requestMicAndJoinVoice() },
                        enabled = !isVoiceJoining,
                        modifier = Modifier.then(
                            when {
                                iAmSpeaking -> Modifier.border(
                                    2.5.dp,
                                    com.watch.watchtofriend.ui.theme.SuccessGreen,
                                    CircleShape
                                )
                                isInVoice && !isVoiceMuted -> Modifier.border(
                                    2.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                                    CircleShape
                                )
                                else -> Modifier
                            }
                        )
                    ) {
                        Icon(
                            if (isVoiceJoining) Icons.Default.Mic
                            else if (isVoiceMuted) Icons.Default.MicOff
                            else if (isInVoice) Icons.Default.Mic
                            else Icons.Default.Mic,
                            contentDescription = if (isVoiceJoining) stringResource(R.string.common_connecting)
                            else if (isInVoice) stringResource(R.string.watch_mic) else stringResource(R.string.watch_voice_chat),
                            tint = when {
                                isVoiceJoining  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                !isInVoice      -> MaterialTheme.colorScheme.onSurfaceVariant
                                isVoiceMuted    -> MaterialTheme.colorScheme.error
                                iAmSpeaking     -> androidx.compose.ui.graphics.Color(0xFF22c55e)
                                else            -> MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                    IconButton(onClick = { expandVideo = !expandVideo }) {
                        Icon(
                            if (expandVideo) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = if (expandVideo) stringResource(R.string.watch_collapse) else stringResource(R.string.watch_expand)
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_menu))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Sesli sohbet — katıl / ayrıl
                            DropdownMenuItem(
                                text = { Text(if (isInVoice) stringResource(R.string.watch_leave_voice) else stringResource(R.string.watch_join_voice)) },
                                leadingIcon = {
                                    Icon(
                                        if (isInVoice) Icons.Default.MicOff else Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = if (isInVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    if (isInVoice) voiceManager.leave() else requestMicAndJoinVoice()
                                }
                            )
                            // Ses sustur / aç (sadece sohbetteyken göster)
                            if (isInVoice) {
                                DropdownMenuItem(
                                    text = { Text(if (isVoiceMuted) stringResource(R.string.watch_unmute) else stringResource(R.string.watch_mute)) },
                                    leadingIcon = {
                                        Icon(
                                            if (isVoiceMuted) Icons.Default.Mic else Icons.Default.MicOff,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        voiceManager.toggleMute()
                                    }
                                )
                            }
                            val queueCount = room?.queue?.size ?: 0
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (queueCount > 0) stringResource(R.string.watch_queue_count, queueCount)
                                        else stringResource(R.string.watch_queue)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showQueue = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.watch_resync)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Sync, contentDescription = null, tint = syncColor)
                                },
                                onClick = {
                                    showMenu = false
                                    val r = room
                                    if (r != null) {
                                        val elapsed = if (r.isPlaying) (vm.serverNow() - r.updatedAt).coerceAtLeast(0L) else 0L
                                        val sec = (r.currentPositionMs + elapsed) / 1000.0
                                        applyRemoteUnified(r.isPlaying, sec, true, true)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.watch_copy_invite)) },
                                leadingIcon = {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    context.copyToClipboard(InviteLink.buildShareMessage(room?.title, roomId))
                                    android.widget.Toast.makeText(context, context.getString(R.string.watch_invite_copied), android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.watch_share_invite)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Share, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    InviteLink.share(context, room?.title, roomId)
                                }
                            )
                            // Ekran paylaşımı — kendi paylaşımını başlat/durdur
                            if (remoteSharingUid == null || remoteSharingUid == vm.uid) {
                                DropdownMenuItem(
                                    text = { Text(if (isSharing) stringResource(R.string.watch_screen_stop) else stringResource(R.string.watch_screen_share)) },
                                    leadingIcon = {
                                        Icon(
                                            if (isSharing) Icons.AutoMirrored.Filled.StopScreenShare
                                            else Icons.AutoMirrored.Filled.ScreenShare,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        if (isSharing) {
                                            screenShareManager.stopSharing()
                                        } else {
                                            showScreenSharePermDialog = true
                                        }
                                    }
                                )
                            }
                            // Senaryo 5: Host başkasının paylaşımını zorla durdurabilir
                            if (isHost && remoteSharingUid != null && remoteSharingUid != vm.uid) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_screen_force_stop), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.StopScreenShare,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        screenShareManager.forceStop()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.watch_invite_friends)) },
                                onClick = {
                                    showMenu = false
                                    showInviteDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showChatSearch) stringResource(R.string.watch_chat_search_close) else stringResource(R.string.watch_chat_search_open)) },
                                onClick = {
                                    showMenu = false
                                    showChatSearch = !showChatSearch
                                    if (!showChatSearch) chatQuery = ""
                                }
                            )
                            if (!room?.videoUrl.isNullOrBlank() && remoteSharingUid == null && !isSharing) {
                                DropdownMenuItem(
                                    text = { Text(if (videoPanelHidden) stringResource(R.string.watch_video_show) else stringResource(R.string.watch_video_hide)) },
                                    leadingIcon = {
                                        Icon(
                                            if (videoPanelHidden) Icons.Default.Fullscreen else Icons.Default.FullscreenExit,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        videoPanelHidden = !videoPanelHidden
                                    }
                                )
                            }
                            if (!room?.videoUrl.isNullOrBlank()) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_pip)) },
                                    onClick = {
                                        showMenu = false
                                        pip?.enterPip()
                                    }
                                )
                            }
                            // Video Değiştir: host + yetkililer
                            if (canControl) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_change_video)) },
                                    onClick = {
                                        showMenu = false
                                        showChangeVideo = true
                                    }
                                )
                            }
                            if (isHost) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_start_poll)) },
                                    onClick = {
                                        showMenu = false
                                        showPollDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_room_settings)) },
                                    onClick = {
                                        showMenu = false
                                        showRoomSettings = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_delete_room), color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            } else {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.watch_leave_room), color = Color.Red)},
                                    onClick = {
                                        showMenu = false
                                        showLeaveConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                )
                }
            }
          }
        }
    ) { padding ->
        // Video ve sohbet bölümleri movableContent: dikey↔yatay düzen değişince
        // WebView yeniden KURULMAZ (taşınır), video kesintisiz devam eder.
        val videoSection = remember {
            movableContentOf {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(
                            if (expandVideo) Modifier.clickable(
                                indication = null,
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            ) { showTopBarBriefly() } else Modifier
                        )
                ) {
                // Ekran paylaşımı aktifse video yerine ekranı göster.
                // Firebase RTDB frame relay: WebRTC/TURN yok, her ağda çalışır.
                // İzleyici WebRTC video track'i — paylaşan için gösterilmez
                // (kendi ekranını WebRTC üzerinden önizlemek döngüsel siyah ekrana neden olur)
                val viewerVideoTrack = if (!isSharing) remoteVideoTrack else null
                val activeBitmap = if (!isSharing) remoteBitmap else null
                val screenShareActive = isSharing || remoteSharingUid != null

                if (screenShareActive) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        // Paylaşan WebRTC modunda: önizleme yok — döngüsel ekran yakalama engellenir
                        // Paylaşan RTDB modunda: bitmap preview göster
                        // İzleyici WebRTC modunda: SurfaceViewRenderer
                        when {
                            viewerVideoTrack != null -> WebRtcVideoRenderer(
                                videoTrack = viewerVideoTrack,
                                eglBaseContext = screenShareManager.getEglContext(),
                                modifier = Modifier.fillMaxSize()
                            )
                            activeBitmap != null -> Image(
                                bitmap = activeBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.watch_screen_share_label),
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Henüz görüntü yok → yükleme/bilgi göstergesi
                        val isWebRtcSharing = isSharing && localVideoTrack != null
                        val showPlaceholder = viewerVideoTrack == null && activeBitmap == null
                        if (showPlaceholder) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (isWebRtcSharing) {
                                    // WebRTC paylaşım aktif — yerel önizleme yok (beklenen davranış)
                                    Icon(
                                        Icons.AutoMirrored.Filled.ScreenShare,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        stringResource(R.string.watch_screen_sharing),
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        stringResource(R.string.watch_screen_viewers_see),
                                        color = Color.White.copy(alpha = 0.7f),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                } else {
                                    CircularProgressIndicator(color = Color.White)
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        when {
                                            isSharing -> stringResource(R.string.watch_screen_capturing)
                                            isScreenConnecting -> stringResource(R.string.watch_screen_connecting)
                                            else -> stringResource(R.string.watch_screen_waiting)
                                        },
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (isScreenConnecting && !isSharing) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            stringResource(R.string.watch_screen_webrtc),
                                            color = Color.White.copy(alpha = 0.55f),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                        // Üst sol etiket
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                            modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ScreenShare,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (isSharing) stringResource(R.string.watch_screen_you_share) else stringResource(R.string.watch_screen_share_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                        // Bağlanıyor… göstergesi (sağ üst)
                        if (isScreenConnecting && !isSharing) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(10.dp),
                                        strokeWidth = 1.5.dp
                                    )
                                    Text(stringResource(R.string.watch_screen_connecting), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                }
                            }
                        }
                        // Paylaşan için "Durdur" butonu
                        if (isSharing) {
                            FilledTonalButton(
                                onClick = { screenShareManager.stopSharing() },
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.StopScreenShare,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.watch_screen_stop))
                            }
                        }
                    }
                } else {

                val videoUrl = room?.videoUrl
                if (room != null && videoUrl.isNullOrBlank()) {
                    // Oda var ama video yok → host ekleyebilir
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.watch_no_video),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        if (room?.let { it.hostUid == vm.uid || it.moderators.contains(vm.uid) } == true) {
                            Button(onClick = { showChangeVideo = true }, shape = MaterialTheme.shapes.medium) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.watch_add_video))
                            }
                        } else {
                            Text(
                                stringResource(R.string.watch_waiting_host_video),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (videoUrl != null) {
                    // WebView oluşturulurken videonun başlayacağı konum (sonradan katılan için)
                    val startMs = room?.let {
                        it.currentPositionMs + if (it.isPlaying) (vm.serverNow() - it.updatedAt).coerceAtLeast(0L) else 0L
                    } ?: 0L
                    val startPlaying = room?.isPlaying ?: true
                    // Sadece video kimliği / videoVersion değişince WebView yeniden kurulur.
                    val ytId = remember(videoUrl) { extractYouTubeId(videoUrl) }
                    key(room?.videoVersion ?: 0L, ytId) {
                      if (isDirectVideoUrl(videoUrl)) {
                        // Doğrudan video linki (.mp4/.m3u8/HLS) → ExoPlayer (Full HD + net senkron)
                        DirectVideoPlayer(
                            videoUrl = videoUrl,
                            startPositionMs = startMs,
                            startPlaying = startPlaying,
                            canControl = { vm.canControl() },
                            handleAudioFocus = !isInVoice,
                            onReady = {
                                lastSyncedState = null
                                playerReady = true
                            },
                            onPlayStateChanged = { isPlaying, posMs ->
                                if (vm.canControl()) vm.updateVideoState(isPlaying, posMs)
                            },
                            onEnded = { vm.onVideoEnded() },
                            onProgress = { cur, dur -> playerPosSec = cur; playerDurSec = dur },
                            onController = { c ->
                                exoController = c
                                if (c != null) playerReady = false
                            }
                        )
                      } else {
                        VideoWebView(
                            videoUrl = videoUrl,
                            startPositionMs = startMs,
                            startPlaying = startPlaying,
                            onWebViewReady = { wv ->
                                webViewRef = wv
                                // Yeni WebView → eski oynatıcı hazır işaretini sıfırla
                                playerReady = false
                            },
                            onPlayerReady = {
                                // Yeni oynatıcı hazır → senkronu baştan uygula
                                lastSyncedState = null
                                playerReady = true
                            },
                            onPlayStateChanged = { isPlaying, posMs ->
                                // Senkron otoritesi: yalnızca host + yetkililer oynat/durdur
                                // durumunu yayar. Misafirlerin yerel oynat/durdur olayları
                                // herkesi etkilemez (kaos/zincirleme durma önlenir); misafirler
                                // yalnızca gelen durumu takip eder. canControl VM'den CANLI
                                // okunur (yetki sonradan değişse de doğru çalışır).
                                if (vm.canControl()) vm.updateVideoState(isPlaying, posMs)
                            },
                            onUserNavigate = { newUrl ->
                                // Tarayıcı-gibi gezinme: sadece HOST yayar; aynı sitede kalan,
                                // mevcut sayfadan farklı, gerçek bir gezinme ise odaya bildir.
                                val r = latestRoom.value
                                if (r != null && r.hostUid == vm.uid &&
                                    newUrl != r.videoUrl &&
                                    sameDomain(newUrl, r.videoUrl)
                                ) {
                                    vm.navigateTo(newUrl)
                                }
                            },
                            onUserClick = { sel ->
                                // Ana-sayfa tıklamasını odaya yay (film "oynat"ı birlikte basmak için)
                                vm.sendClick(sel)
                            },
                            onVideoEnded = { vm.onVideoEnded() },
                            onProgress = { cur, dur -> playerPosSec = cur; playerDurSec = dur },
                            onYtError = { code -> ytPlaybackError = youtubeErrorMessage(context, code) }
                        )
                      }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                } // ekran paylaşımı else bloğunun sonu
                // Uçan emoji tepkisi
                reactionDisplay?.let { emoji ->
                    Text(
                        emoji,
                        fontSize = 64.sp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )
                }
                ytPlaybackError?.let { errMsg ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                stringResource(R.string.watch_video_error),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                errMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { ytPlaybackError = null }) {
                                Text(stringResource(R.string.common_close))
                            }
                        }
                    }
                }
                // Kontrol isteği banner'ı (video üstünde)
                ctrlReqDisplay?.let { (name, action) ->
                    val actionText = if (action == "play") stringResource(R.string.watch_control_play_verb)
                    else stringResource(R.string.watch_control_pause_verb)
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.82f),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.watch_control_request, name, actionText),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            // Host/yetkili → tek dokunuşla uygula (room'dan CANLI hesap)
                            if (room?.let { it.hostUid == vm.uid || it.moderators.contains(vm.uid) } == true) {
                                Spacer(Modifier.width(10.dp))
                                FilledTonalButton(
                                    onClick = {
                                        applyControl(action == "play")
                                        ctrlReqDisplay = null
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text(if (action == "play") stringResource(R.string.common_play) else stringResource(R.string.common_pause))
                                }
                            }
                        }
                    }
                }
                // Video ilerleme çubuğu + süre + senkron sapması (alt şerit)
                if (playerDurSec > 1.0) {
                    val r = room
                    val expectedSec = if (r != null && r.isPlaying)
                        (r.currentPositionMs + (vm.serverNow() - r.updatedAt).coerceAtLeast(0L)) / 1000.0
                    else (r?.currentPositionMs ?: 0L) / 1000.0
                    val drift = kotlin.math.abs(playerPosSec - expectedSec)
                    val frac = (playerPosSec / playerDurSec).coerceIn(0.0, 1.0).toFloat()
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { frac },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "${formatClock(playerPosSec)} / ${formatClock(playerDurSec)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            val driftColor = when {
                                drift < 1.5 -> com.watch.watchtofriend.ui.theme.SuccessGreen
                                drift < 3.0 -> androidx.compose.ui.graphics.Color(0xFFF5A623)
                                else -> MaterialTheme.colorScheme.error
                            }
                            Text(
                                stringResource(R.string.watch_sync_drift, drift),
                                style = MaterialTheme.typography.labelSmall,
                                color = driftColor
                            )
                        }
                    }
                }
                if (expandVideo) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(8.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
                            .zIndex(40f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            expandVideo = false
                            topBarVisible = true
                        }) {
                            Icon(
                                Icons.Default.FullscreenExit,
                                contentDescription = stringResource(R.string.watch_collapse),
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = {
                            if (isInVoice) voiceManager.leave()
                            onBack()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
                            )
                        }
                    }
                }
                }
            }
        }

        val stageExtrasSection = remember {
            movableContentOf {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val pinned = room?.pinnedMessage.orEmpty()
                    if (pinned.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PushPin,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        room?.pinnedMessageSenderName.orEmpty(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        pinned,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2
                                    )
                                }
                                if (canControl) {
                                    IconButton(onClick = { vm.unpinMessage() }, modifier = Modifier.size(28.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_unpin), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                    if (scheduleCountdownSec != null) {
                        val remaining = scheduleCountdownSec!!
                        if (remaining > 0L) {
                            val h = remaining / 3600
                            val m = (remaining % 3600) / 60
                            val s = remaining % 60
                            val fmt = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
                            val startFmt = scheduledAt.let { ts ->
                                java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            stringResource(R.string.watch_scheduled, startFmt),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            stringResource(R.string.watch_scheduled_wait),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        fmt,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        } else if (scheduledAt > 0L) {
                            Surface(
                                color = com.watch.watchtofriend.ui.theme.SuccessGreen.copy(alpha = 0.15f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    stringResource(R.string.watch_started),
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = com.watch.watchtofriend.ui.theme.SuccessGreen,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf("❤️", "😂", "👍", "🔥", "😮", "😢").forEach { emo ->
                            Text(
                                emo,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        vm.sendReaction(emo)
                                    }
                                    .padding(4.dp)
                            )
                        }
                    }
                }
            }
        }

        val chatSection = remember {
            movableContentOf {
                Column(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider()

            // Sohbette arama alanı
            if (showChatSearch) {
                OutlinedTextField(
                    value = chatQuery,
                    onValueChange = { chatQuery = it },
                    placeholder = { Text(stringResource(R.string.watch_chat_search_ph)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showChatSearch = false; chatQuery = "" }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.common_close), modifier = Modifier.rotate(45f))
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Chat mesajları
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                state = listState,
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                val visible = messages.filter {
                    it.senderUid !in blocked &&
                        (chatQuery.isBlank() || it.text.contains(chatQuery, ignoreCase = true))
                }
                if (visible.isEmpty()) {
                    item {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 32.dp),
                            contentAlignment = androidx.compose.ui.Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = stringResource(R.string.watch_chat_empty),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                itemsIndexed(visible, key = { idx, m -> m.id.ifEmpty { "${idx}_${m.timestamp}" } }) { idx, msg ->
                    val prev = visible.getOrNull(idx - 1)
                    // Gün değişince tarih ayıracı göster
                    if (prev == null || !isSameDay(prev.timestamp, msg.timestamp)) {
                        DateSeparator(msg.timestamp)
                    }
                    MessageBubble(
                        msg = msg,
                        isMe = msg.senderUid == vm.uid,
                        photoBase64 = senderPhotos[msg.senderUid],
                        canPin = canControl,
                        myUid = vm.uid,
                        onPin = { vm.pinMessage(msg.text, msg.senderName) },
                        onReact = { emoji -> vm.toggleMessageReaction(msg.id, emoji) },
                        onDelete = { vm.deleteMessage(msg.id) }
                    )
                }
            }

            // Aktif oylama banner'ı
            val pollQuestion = room?.pollQuestion.orEmpty()
            if (pollQuestion.isNotBlank()) {
                val pollOptions = room?.pollOptions ?: emptyList()
                val pollVotes = room?.pollVotes ?: emptyMap()
                val myVote = room?.pollVoterChoice?.get(vm.uid)
                val totalVotes = pollVotes.values.sum().coerceAtLeast(1)
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.watch_poll),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.weight(1f))
                            if (isHost) {
                                IconButton(onClick = { vm.clearPoll() }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.watch_poll_end), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        Text(pollQuestion, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.height(4.dp))
                        pollOptions.forEachIndexed { idx, opt ->
                            val votes = pollVotes["$idx"] ?: 0
                            val pct = (votes * 100f / totalVotes).toInt()
                            val selected = myVote == idx
                            OutlinedButton(
                                onClick = { vm.votePoll(idx) },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (selected) MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(opt, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Text("$votes ($pct%)", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        }
                    }
                }
            }

            // Yetkisi olmayan üye: sohbete yazmadan durdur/oynat rica et (room'dan CANLI)
            if (room?.let { it.hostUid == vm.uid || it.moderators.contains(vm.uid) } != true) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    AssistChip(
                        onClick = { vm.requestControl("pause") },
                        label = { Text(stringResource(R.string.watch_request_pause)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    AssistChip(
                        onClick = { vm.requestControl("play") },
                        label = { Text(stringResource(R.string.watch_request_play)) }
                    )
                }
            }

            // Yazıyor… göstergesi (başkaları)
            val nowT = vm.serverNow()
            val typingNames = (room?.typing ?: emptyMap())
                .filter { it.key != vm.uid && nowT - it.value < 4000 }
                .keys.mapNotNull { room?.presenceNames?.get(it) }
            if (typingNames.isNotEmpty()) {
                Text(
                    if (typingNames.size == 1) stringResource(R.string.watch_typing_one, typingNames.first())
                    else stringResource(R.string.watch_typing_many),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, bottom = 2.dp)
                )
            }

            // Mesaj gönderme alanı
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = {
                            chatInput = it
                            // Yazıyor bilgisini en fazla 2 sn'de bir yay
                            val now = System.currentTimeMillis()
                            if (it.isNotBlank() && now - lastTypingAt > 2000) {
                                lastTypingAt = now
                                vm.setTyping()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.watch_chat_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            val draft = chatInput
                            vm.sendMessage(draft) { sent -> if (sent) chatInput = "" }
                        },
                        enabled = chatInput.isNotBlank()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.common_send))
                    }
                }
            }
                }
            }
        }

        val screenShareActive = isSharing || remoteSharingUid != null
        val showVideoPanel = !videoPanelHidden || screenShareActive
        // Responsive yerleşim: yatayda video solda + sohbet sağda; dikeyde üst/alt.
        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                Column(modifier = Modifier.fillMaxHeight().weight(if (videoFull) 1f else 0.62f)) {
                    if (!videoFull) {
                        stageExtrasSection()
                        if (voicePeersList.isNotEmpty()) {
                            VoiceChannelPanel(
                                voicePeersList = voicePeersList,
                                isInVoice = isInVoice,
                                myUid = vm.uid,
                                speakingUids = speakingUids,
                                localMicLevel = localMicLevel,
                                micGain = micGain,
                                peerVolumes = peerVolumes,
                                peerLocalMuted = peerLocalMuted,
                                speakRmsUi = speakRmsUi,
                                voiceManager = voiceManager,
                                presenceNames = room?.presenceNames ?: emptyMap(),
                                photoForUid = { uid, peerPhoto ->
                                    peerPhoto.ifBlank { null }
                                        ?: if (uid == vm.uid) vm.myPhoto.ifBlank { null }
                                        else friends.find { it.uid == uid }?.photoBase64?.ifBlank { null }
                                },
                            )
                        }
                    }
                    if (showVideoPanel) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            videoSection()
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.watch_video_hidden_audio), style = MaterialTheme.typography.labelMedium)
                                TextButton(onClick = { videoPanelHidden = false }) { Text(stringResource(R.string.common_show)) }
                            }
                        }
                    }
                }
                if (!videoFull) {
                    Box(modifier = Modifier.fillMaxHeight().weight(0.38f).imePadding()) {
                        chatSection()
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                if (!videoFull) {
                    stageExtrasSection()
                    if (voicePeersList.isNotEmpty()) {
                        VoiceChannelPanel(
                            voicePeersList = voicePeersList,
                            isInVoice = isInVoice,
                            myUid = vm.uid,
                            speakingUids = speakingUids,
                            localMicLevel = localMicLevel,
                            micGain = micGain,
                            peerVolumes = peerVolumes,
                            peerLocalMuted = peerLocalMuted,
                            speakRmsUi = speakRmsUi,
                            voiceManager = voiceManager,
                            presenceNames = room?.presenceNames ?: emptyMap(),
                            photoForUid = { uid, peerPhoto ->
                                peerPhoto.ifBlank { null }
                                    ?: if (uid == vm.uid) vm.myPhoto.ifBlank { null }
                                    else friends.find { it.uid == uid }?.photoBase64?.ifBlank { null }
                            },
                        )
                    }
                }
                if (showVideoPanel) {
                    Box(modifier = Modifier.fillMaxWidth().weight(if (videoFull) 1f else 0.5f)) {
                        videoSection()
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(stringResource(R.string.watch_video_hidden_audio), style = MaterialTheme.typography.labelMedium)
                            TextButton(onClick = { videoPanelHidden = false }) { Text(stringResource(R.string.common_show)) }
                        }
                    }
                }
                if (!videoFull) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(
                            if (showVideoPanel) 0.5f else 0.92f
                        )
                    ) {
                        chatSection()
                    }
                }
            }
        }
    }

    if (isVoiceJoining) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .zIndex(100f),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.watch_voice_connecting),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
    }

    // Arkadaş davet dialog'u
    if (showInviteDialog) {
        InviteFriendDialog(
            friends = friends,
            currentMembers = room?.memberUids ?: emptyList(),
            onInvite = { vm.inviteFriend(it) },
            onDismiss = { showInviteDialog = false }
        )
    }

    // Video değiştir — modern bottom sheet
    if (showChangeVideo) {
        VideoPickerSheet(
            sheetState = videoPickerSheetState,
            ytResults = ytResults,
            isSearching = ytSearching,
            canAddToQueue = canControl,
            onDismiss = { showChangeVideo = false; vm.clearYtResults() },
            onSearch = { vm.searchYouTube(it) },
            onClearResults = { vm.clearYtResults() },
            onPlay = { url -> vm.changeVideo(url); showChangeVideo = false },
            onAddToQueue = { url -> vm.addToQueue(url) },
            onPlayResult = { r -> vm.changeVideo("https://www.youtube.com/watch?v=${r.videoId}"); showChangeVideo = false },
            onQueueResult = { r -> vm.addToQueueResult(r) }
        )
    }

    // Oda ayarları (host): ad + herkese açık
    if (showRoomSettings) {
        var newTitle by remember(room?.title) { mutableStateOf(room?.title ?: "") }
        var newDiscoverable by remember(room?.discoverable) { mutableStateOf(room?.discoverable ?: false) }
        AlertDialog(
            onDismissRequest = { showRoomSettings = false },
            title = { Text(stringResource(R.string.watch_room_settings)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(stringResource(R.string.watch_settings_name)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.watch_settings_public), style = MaterialTheme.typography.bodyMedium)
                            Text(stringResource(R.string.watch_settings_public_hint), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = newDiscoverable, onCheckedChange = { newDiscoverable = it })
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateRoomSettings(newTitle, newDiscoverable)
                    showRoomSettings = false
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = { TextButton(onClick = { showRoomSettings = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // Oylama oluşturma (sadece host)
    if (showPollDialog) {
        var pollQuestion by remember { mutableStateOf("") }
        var optionA by remember { mutableStateOf("") }
        var optionB by remember { mutableStateOf("") }
        var optionC by remember { mutableStateOf("") }
        var optionD by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text(stringResource(R.string.watch_poll_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = pollQuestion,
                        onValueChange = { pollQuestion = it },
                        label = { Text(stringResource(R.string.watch_poll_question)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    listOf(
                        stringResource(R.string.watch_poll_opt_a) to optionA,
                        stringResource(R.string.watch_poll_opt_b) to optionB,
                        stringResource(R.string.watch_poll_opt_c) to optionC,
                        stringResource(R.string.watch_poll_opt_d) to optionD
                    ).forEachIndexed { i, (label, value) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { v ->
                                when (i) { 0 -> optionA = v; 1 -> optionB = v; 2 -> optionC = v; else -> optionD = v }
                            },
                            label = { Text(label) },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val opts = listOfNotNull(
                            optionA.trim().ifBlank { null },
                            optionB.trim().ifBlank { null },
                            optionC.trim().ifBlank { null },
                            optionD.trim().ifBlank { null }
                        )
                        if (pollQuestion.isNotBlank() && opts.size >= 2) {
                            vm.createPoll(pollQuestion.trim(), opts)
                            showPollDialog = false
                        }
                    },
                    enabled = pollQuestion.isNotBlank() && optionA.isNotBlank() && optionB.isNotBlank()
                ) { Text(stringResource(R.string.common_start)) }
            },
            dismissButton = { TextButton(onClick = { showPollDialog = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    // Sıra (playlist) — modern bottom sheet
    if (showQueue) {
        val queue = room?.queue ?: emptyList()
        val isHostUser = room?.hostUid == vm.uid || room?.moderators?.contains(vm.uid) == true
        QueueBottomSheet(
            sheetState = queueSheetState,
            queue = queue,
            ytResults = ytResults,
            isSearching = ytSearching,
            isHostUser = isHostUser,
            myUid = vm.uid,
            onDismiss = { showQueue = false; vm.clearYtResults() },
            onSearch = { vm.searchYouTube(it) },
            onClearResults = { vm.clearYtResults() },
            onAddUrl = { vm.addToQueue(it) },
            onAddResult = { vm.addToQueueResult(it) },
            onPlayResult = { r ->
                vm.changeVideo("https://www.youtube.com/watch?v=${r.videoId}")
                vm.clearYtResults()
                showQueue = false
            },
            onPlayItem = { vm.playQueueItem(it) },
            onRemoveItem = { vm.removeQueueItem(it) }
        )
    }

    // Ekran paylaşımı izin açıklaması
    if (showScreenSharePermDialog) {
        AlertDialog(
            onDismissRequest = { showScreenSharePermDialog = false },
            title = { Text(stringResource(R.string.watch_screen_perm_title)) },
            text = { Text(stringResource(R.string.watch_screen_perm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showScreenSharePermDialog = false
                    val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projectionLauncher.launch(mgr.createScreenCaptureIntent())
                }) { Text(stringResource(R.string.watch_grant_permission)) }
            },
            dismissButton = {
                TextButton(onClick = { showScreenSharePermDialog = false }) { Text(stringResource(R.string.watch_dismiss)) }
            }
        )
    }

    // Oda silme onayı
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.watch_delete_room)) },
            text = { Text(stringResource(R.string.watch_delete_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.deleteRoom()
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    // Katılımcı (presence) listesi
    if (showParticipants) {
        val now = vm.serverNow()
        val requestedUids = remember { mutableStateListOf<String>() }
        var menuForUid by remember { mutableStateOf<String?>(null) }
        data class P(val uid: String, val name: String, val lastSeen: Long)
        val pres = room?.presence ?: emptyMap()
        val names = room?.presenceNames ?: emptyMap()
        val sorted = pres.map { (uid, ls) -> P(uid, names[uid] ?: context.getString(R.string.common_user), ls) }
            .filter { it.uid !in blocked }  // engellenenleri listede gösterme
            .sortedByDescending { it.uid == room?.hostUid }
        // Fotoğraf: kendiminki + arkadaş listesinden eşle (yoksa baş harf)
        val photoFor: (String) -> String? = { u ->
            if (u == vm.uid) vm.myPhoto.ifBlank { null }
            else friends.find { it.uid == u }?.photoBase64?.ifBlank { null }
        }
        AlertDialog(
            onDismissRequest = { showParticipants = false },
            icon = { Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.watch_participants, sorted.size)) },
            text = {
                if (sorted.isEmpty() && voicePeersList.isEmpty()) {
                    Text(stringResource(R.string.watch_no_participants))
                } else {
                    LazyColumn {
                        if (voicePeersList.isNotEmpty()) {
                            item(key = "voice_header") {
                                Text(
                                    stringResource(R.string.watch_voice_channel, voicePeersList.size),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = com.watch.watchtofriend.ui.theme.SuccessGreen,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }
                            items(voicePeersList, key = { "voice_${it.uid}" }) { vp ->
                                val speaking = speakingUids.contains(vp.uid) ||
                                    (vp.uid == vm.uid && localMicLevel > 0.07f)
                                val peerName = vp.displayName.ifBlank {
                                    names[vp.uid] ?: context.getString(R.string.common_user)
                                }
                                val peerPhoto = photoFor(vp.uid)
                                    ?: vp.photoBase64.ifBlank { null }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    VoiceSpeakingAvatar(
                                        initial = peerName.take(1).uppercase(),
                                        name = peerName,
                                        photoBase64 = peerPhoto,
                                        isSpeaking = speaking,
                                        voiceConnected = vp.uid == vm.uid && isInVoice,
                                        size = 32.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (vp.uid == vm.uid) "${peerName}${stringResource(R.string.common_you_suffix)}" else peerName,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            when {
                                                speaking -> stringResource(R.string.watch_speaking)
                                                vp.listenOnly -> stringResource(R.string.watch_listen_only)
                                                vp.muted -> stringResource(R.string.watch_muted)
                                                else -> stringResource(R.string.watch_connected)
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (speaking) com.watch.watchtofriend.ui.theme.SuccessGreen
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            item(key = "voice_divider") {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                Text(
                                    stringResource(R.string.common_online),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }
                        items(sorted, key = { it.uid }) { p ->
                            val online = now - p.lastSeen < 45000
                            val isHostUser = p.uid == room?.hostUid
                            val isMod = room?.moderators?.contains(p.uid) == true
                            val isMe = p.uid == vm.uid
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box {
                                    InitialAvatar(p.name.ifBlank { "?" }, size = 40, photoBase64 = photoFor(p.uid))
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .size(12.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(2.dp)
                                            .clip(androidx.compose.foundation.shape.CircleShape)
                                            .background(
                                                if (online) com.watch.watchtofriend.ui.theme.SuccessGreen
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.name.ifBlank { stringResource(R.string.common_user) } + if (isMe) stringResource(R.string.common_you_suffix) else "",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        if (online) stringResource(R.string.common_online) else stringResource(R.string.watch_was_here),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isHostUser) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            stringResource(R.string.common_host),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                } else if (isMod) {
                                    Surface(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                                    ) {
                                        Text(
                                            stringResource(R.string.common_moderator),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                if (!isMe) {
                                    val alreadyFriend = vm.isFriend(p.uid)
                                    // Canlı (Firestore) gönderilen istekler + bu oturumdaki anlık dokunuş
                                    val requested = p.uid in sentFriendRequests || p.uid in requestedUids
                                    Box {
                                        IconButton(onClick = { menuForUid = p.uid }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_options))
                                        }
                                        DropdownMenu(
                                            expanded = menuForUid == p.uid,
                                            onDismissRequest = { menuForUid = null }
                                        ) {
                                            // Yalnızca mevcut host yetki verir/devreder
                                            if (room?.hostUid == vm.uid) {
                                                DropdownMenuItem(
                                                    text = { Text(if (isMod) stringResource(R.string.watch_remove_mod) else stringResource(R.string.watch_make_mod)) },
                                                    onClick = {
                                                        vm.setModerator(p.uid, !isMod)
                                                        menuForUid = null
                                                    }
                                                )
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.watch_transfer_host)) },
                                                    onClick = {
                                                        vm.transferHost(p.uid)
                                                        menuForUid = null
                                                    }
                                                )
                                            }
                                            if (!alreadyFriend) {
                                                DropdownMenuItem(
                                                    text = { Text(if (requested) stringResource(R.string.watch_request_sent) else stringResource(R.string.watch_add_friend)) },
                                                    enabled = !requested,
                                                    onClick = {
                                                        vm.sendFriendRequest(p.uid)
                                                        requestedUids.add(p.uid)
                                                        menuForUid = null
                                                    }
                                                )
                                            }
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.watch_report)) },
                                                onClick = {
                                                    vm.reportUser(p.uid, context.getString(R.string.watch_user_reported))
                                                    menuForUid = null
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text(stringResource(R.string.watch_block), color = MaterialTheme.colorScheme.error) },
                                                onClick = {
                                                    vm.blockUser(p.uid)
                                                    menuForUid = null
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showParticipants = false }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    // Odadan ayrılma onayı
    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            title = { Text(stringResource(R.string.watch_leave_confirm_title)) },
            text = { Text(stringResource(R.string.watch_leave_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showLeaveConfirm = false
                    vm.leaveRoom()
                }) { Text(stringResource(R.string.watch_leave), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showLeaveConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun InviteFriendDialog(
    friends: List<User>,
    currentMembers: List<String>,
    onInvite: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val invitedIds = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.watch_invite_friends)) },
        text = {
            if (friends.isEmpty()) {
                Text(stringResource(R.string.watch_invite_empty))
            } else {
                LazyColumn {
                    items(friends, key = { it.uid }) { friend ->
                        val alreadyIn = currentMembers.contains(friend.uid)
                        val justInvited = invitedIds.contains(friend.uid)
                        ListItem(
                            headlineContent = { Text(friend.displayName) },
                            supportingContent = { Text(friend.email) },
                            trailingContent = {
                                when {
                                    alreadyIn -> Text(
                                        stringResource(R.string.watch_already_member),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    justInvited -> Text(
                                        stringResource(R.string.watch_invited),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    else -> TextButton(onClick = {
                                        onInvite(friend.uid)
                                        invitedIds.add(friend.uid)
                                    }) { Text(stringResource(R.string.common_invite)) }
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
        }
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: Message,
    isMe: Boolean,
    photoBase64: String? = null,
    canPin: Boolean = false,
    myUid: String = "",
    onPin: () -> Unit = {},
    onReact: (String) -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var showActionMenu by remember { mutableStateOf(false) }
    val reactionEmojis = listOf("❤️", "😂", "👍", "🔥", "😮", "😢")

    // Sistem mesajı: ortalı, balonsuz; ayrılma kırmızı, katılma yeşil
    if (msg.system) {
        val lower = msg.text.lowercase()
        val isLeave = lower.contains("ayrıldı") || lower.contains("left the room")
        val isJoin = lower.contains("katıldı") || lower.contains("joined the room")
        val accent = when {
            isLeave -> MaterialTheme.colorScheme.error
            isJoin -> com.watch.watchtofriend.ui.theme.SuccessGreen
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f)
            ) {
                Text(
                    msg.text,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }
        return
    }

    // Tepki özeti: emoji -> kaç kişi
    val reactionSummary = msg.reactions.values
        .groupBy { it }
        .mapValues { it.value.size }
        .filter { it.value > 0 }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp, horizontal = 4.dp),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMe) {
                InitialAvatar(msg.senderName.ifBlank { "?" }, size = 30, photoBase64 = photoBase64)
                Spacer(Modifier.width(8.dp))
            }
            Box {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isMe) 18.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 18.dp
                    ),
                    color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                showActionMenu = true
                            }
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        if (!isMe) {
                            Text(
                                msg.senderName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            formatTime(msg.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMe) Color.White.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
                // Uzun basış menüsü: Kopyala / Tepki ver / Sabitle
                DropdownMenu(expanded = showActionMenu, onDismissRequest = { showActionMenu = false }) {
                    // Tepki emoji satırı
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        reactionEmojis.forEach { emoji ->
                            val iMine = msg.reactions[myUid] == emoji
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (iMine) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                modifier = Modifier
                                    .clickable {
                                        onReact(emoji)
                                        showActionMenu = false
                                    }
                                    .padding(4.dp)
                            ) {
                                Text(emoji, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(4.dp))
                            }
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_copy)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            context.copyToClipboard(msg.text)
                            android.widget.Toast.makeText(context, context.getString(R.string.watch_msg_copied), android.widget.Toast.LENGTH_SHORT).show()
                            showActionMenu = false
                        }
                    )
                    if (canPin) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_pin)) },
                            leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            onClick = {
                                onPin()
                                showActionMenu = false
                            }
                        )
                    }
                    if (isMe) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error) },
                            onClick = {
                                onDelete()
                                showActionMenu = false
                            }
                        )
                    }
                }
            }
        }
        // Tepki sayaç çipleri (varsa)
        if (reactionSummary.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(horizontal = if (isMe) 0.dp else 46.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                reactionSummary.entries.sortedByDescending { it.value }.take(5).forEach { (emoji, count) ->
                    val iMine = msg.reactions[myUid] == emoji
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (iMine) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.clickable { onReact(emoji) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(emoji, style = MaterialTheme.typography.labelSmall)
                            if (count > 1) {
                                Spacer(Modifier.width(2.dp))
                                Text(
                                    "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sohbette gün ayıracı (Bugün / Dün / tarih)
@Composable
private fun DateSeparator(timestamp: Long) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                formatDateLabel(timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
    }
}

private fun isSameDay(a: Long, b: Long): Boolean {
    if (a == 0L || b == 0L) return a == b
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}

private fun formatTime(ts: Long): String {
    if (ts == 0L) return ""
    return java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ts))
}

// Saniyeyi mm:ss / h:mm:ss biçimine çevir (video süresi)
private fun formatClock(sec: Double): String {
    if (sec.isNaN() || sec < 0) return "0:00"
    val total = sec.toInt()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
private fun formatDateLabel(ts: Long): String {
    if (ts == 0L) return ""
    val now = System.currentTimeMillis()
    return when {
        isSameDay(ts, now) -> stringResource(R.string.common_today)
        isSameDay(ts, now - 86_400_000L) -> stringResource(R.string.common_yesterday)
        else -> java.text.SimpleDateFormat("d MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(ts))
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun VideoWebView(
    videoUrl: String,
    startPositionMs: Long,
    startPlaying: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onPlayerReady: () -> Unit,
    onPlayStateChanged: (Boolean, Long) -> Unit,
    onUserNavigate: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onVideoEnded: () -> Unit,
    onProgress: (Double, Double) -> Unit,
    onYtError: (Int) -> Unit = {}
) {
    val youtubeId = remember(videoUrl) { extractYouTubeId(videoUrl) }
    // Bu WebView'in en son yüklediği URL (tarayıcı-gibi gezinme senkronu için)
    val lastLoadedUrl = remember { mutableStateOf("") }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    userAgentString = "Mozilla/5.0 (Linux; Android 12; Pixel 6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    setSupportZoom(false)
                    builtInZoomControls = false
                    // Reklam pop-up pencerelerini engelle
                    setSupportMultipleWindows(false)
                    javaScriptCanOpenWindowsAutomatically = false
                }

                // Reklam pop-up'larını engelle (window.open vb.)
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?, isDialog: Boolean,
                        isUserGesture: Boolean, resultMsg: OsMessage?
                    ): Boolean = false
                }

                // Gezinmeyi WebView içinde tut + enjeksiyon zamanlamasını sayfa yüklenince yap
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?, request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        // Sadece http(s) linkleri WebView içinde aç; harici şemaları engelle
                        return if (url.startsWith("http://") || url.startsWith("https://")) {
                            view?.loadUrl(url)
                            true
                        } else {
                            true // intent://, market://, tel: vb. → engelle
                        }
                    }

                    override fun onPageStarted(
                        view: WebView?, url: String?, favicon: android.graphics.Bitmap?
                    ) {
                        super.onPageStarted(view, url, favicon)
                        if (url != null) {
                            lastLoadedUrl.value = url
                            // YouTube dışı sitelerde ana çerçeve gezinmesini odaya bildir.
                            // (Host olup olmama / aynı domain / mevcut URL filtreleri lambda'da.)
                            if (youtubeId == null) onUserNavigate(url)
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // YouTube dışı siteler için <video> kontrol scriptini sayfa yüklenince enjekte et
                        if (youtubeId == null) {
                            view?.evaluateJavascript(buildVideoInjectionScript(), null)
                        }
                    }
                }

                // Köprü herkese kurulur — her üye oynat/durdur durumunu yayabilir
                addJavascriptInterface(
                    object {
                        // @JavascriptInterface metodları JS thread'inde çağrılır.
                        // Tüm callback'ler Main thread'e post edilir; positionSeconds için
                        // sınır kontrolü yapılarak Firestore'a aşırı/negatif değer yazılması engellenir.
                        private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                        // Geçerli pozisyon aralığı: 0 – 86400 saniye (24 saat üstü video olmaz)
                        private fun clampPosition(sec: Double): Long =
                            if (sec.isNaN() || sec < 0.0 || sec > 86_400.0) 0L
                            else (sec * 1000).toLong()

                        @JavascriptInterface
                        fun onPlay(positionSeconds: Double) {
                            val posMs = clampPosition(positionSeconds)
                            mainHandler.post { onPlayStateChanged(true, posMs) }
                        }
                        @JavascriptInterface
                        fun onPause(positionSeconds: Double) {
                            val posMs = clampPosition(positionSeconds)
                            mainHandler.post { onPlayStateChanged(false, posMs) }
                        }
                        @JavascriptInterface
                        fun onReady() {
                            mainHandler.post { onPlayerReady() }
                        }
                        @JavascriptInterface
                        fun onSyncClick(selector: String) {
                            // selector CSS yolu — 500 karakter üstü saldırı göstergesi, at.
                            if (selector.length > 500) return
                            mainHandler.post { onUserClick(selector) }
                        }
                        @JavascriptInterface
                        fun onEnded() {
                            mainHandler.post { onVideoEnded() }
                        }
                        @JavascriptInterface
                        fun onProgress(cur: Double, dur: Double) {
                            val c = if (cur.isNaN() || cur < 0.0) 0.0 else cur
                            val d = if (dur.isNaN() || dur < 0.0) 0.0 else dur
                            mainHandler.post { onProgress(c, d) }
                        }
                        @JavascriptInterface
                        fun onError(code: Int) {
                            mainHandler.post { onYtError(code) }
                        }
                    }, "WatchBridge"
                )

                if (youtubeId != null) {
                    // YouTube IFrame API'yi origin'i doğru olacak şekilde HTML olarak yükle
                    loadDataWithBaseURL(
                        "https://watchtofriend.app",
                        buildYouTubeHtml(youtubeId, (startPositionMs / 1000).toInt(), startPlaying),
                        "text/html", "utf-8", null
                    )
                } else {
                    lastLoadedUrl.value = videoUrl
                    loadUrl(videoUrl)
                }
                onWebViewReady(this)
            }
        },
        update = { webView ->
            // Tarayıcı-gibi gezinme senkronu: oda URL'si değişti ve bu WebView'de
            // yüklü olandan farklıysa, aynı sayfaya git (YouTube dışı).
            if (youtubeId == null && videoUrl.isNotEmpty() && videoUrl != lastLoadedUrl.value) {
                lastLoadedUrl.value = videoUrl
                webView.loadUrl(videoUrl)
            }
        },
        // Odadan çıkınca / video değişince WebView'i tamamen kapat:
        // aksi halde ses arka planda çalmaya devam eder + bellek sızar.
        onRelease = { webView ->
            try {
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.removeJavascriptInterface("WatchBridge")
                webView.destroy()
            } catch (_: Exception) {
            }
        }
    )
}

private fun youtubeErrorMessage(context: Context, code: Int): String = when (code) {
    2 -> context.getString(R.string.watch_yt_err_invalid)
    5 -> context.getString(R.string.watch_yt_err_html5)
    100 -> context.getString(R.string.watch_yt_err_not_found)
    101, 150 -> context.getString(R.string.watch_yt_err_embed)
    else -> context.getString(R.string.watch_yt_err_generic, code)
}

@Composable
private fun VoiceChannelPanel(
    voicePeersList: List<VoiceManager.VoicePeer>,
    isInVoice: Boolean,
    myUid: String,
    speakingUids: Set<String>,
    localMicLevel: Float,
    micGain: Float,
    peerVolumes: Map<String, Float>,
    peerLocalMuted: Set<String>,
    speakRmsUi: Float,
    voiceManager: VoiceManager,
    presenceNames: Map<String, String>,
    photoForUid: (String, String) -> String?,
) {
    val isMuted by voiceManager.muted.collectAsState()
    val isDeafened by voiceManager.deafened.collectAsState()
    val pushToTalkMode by voiceManager.pushToTalk.collectAsState()
    val pttActive by voiceManager.pttActive.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Text(
                stringResource(R.string.watch_voice_channel_title, voicePeersList.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(6.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            voicePeersList.forEach { peer ->
                val isSelf = peer.uid == myUid
                val peerName = peer.displayName.ifBlank {
                    presenceNames[peer.uid] ?: "?"
                }
                val peerPhoto = photoForUid(peer.uid, peer.photoBase64)
                val isSpeaking = speakingUids.contains(peer.uid) ||
                    (isSelf && localMicLevel > 0.07f)
                key(peer.uid) {
                    VoicePeerRow(
                        peerUid = peer.uid,
                        displayName = if (isSelf) {
                            "${peerName}${stringResource(R.string.common_you_suffix)}"
                        } else {
                            peerName
                        },
                        fullDisplayName = peerName,
                        initial = peerName.take(1).uppercase(),
                        photoBase64 = peerPhoto,
                        isSpeaking = isSpeaking,
                        isSelf = isSelf,
                        voiceConnected = isSelf && isInVoice,
                        muted = peer.muted,
                        listenOnly = peer.listenOnly,
                        connecting = peer.connectionState == "connecting" ||
                            peer.connectionState == "reconnecting",
                        reconnecting = peer.connectionState == "reconnecting",
                        micGain = micGain,
                        peerVolume = peerVolumes[peer.uid] ?: 1f,
                        locallyMuted = !isSelf && peer.uid in peerLocalMuted,
                        onToggleMute = { voiceManager.togglePeerLocalMute(peer.uid) },
                        onVolumeChange = { voiceManager.setPeerVolume(peer.uid, it) }
                    )
                }
            }
        }
        if (isInVoice) {
            Spacer(Modifier.height(6.dp))
            DiscordVoiceControls(
                hasMic = voiceManager.hasLocalMic,
                isMuted = isMuted,
                isDeafened = isDeafened,
                pushToTalk = pushToTalkMode,
                pttActive = pttActive,
                onToggleMute = { voiceManager.toggleMute() },
                onToggleDeafen = { voiceManager.toggleDeafen() },
                onTogglePtt = { voiceManager.setPushToTalk(!pushToTalkMode) },
                onPttPress = { voiceManager.setPttActive(it) },
                onLeave = { voiceManager.leave() }
            )
        }
        if (isInVoice && voiceManager.hasLocalMic) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (localMicLevel > 0.07f) com.watch.watchtofriend.ui.theme.SuccessGreen
                    else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.watch_mic_level),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(4.dp))
                    MicLevelBar(level = localMicLevel)
                }
            }
        }
        if (isInVoice) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.watch_speak_threshold),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        stringResource(R.string.watch_mic_gain_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.65f)
                    )
                }
                Text(
                    when {
                        speakingUids.contains(myUid) -> stringResource(R.string.watch_speaking_you)
                        voiceManager.hasLocalMic -> stringResource(R.string.watch_try_speak)
                        else -> stringResource(R.string.watch_listen_only)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        speakingUids.contains(myUid) -> com.watch.watchtofriend.ui.theme.SuccessGreen
                        voiceManager.hasLocalMic -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                    }
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.watch_sensitive),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    stringResource(R.string.watch_less_sensitive),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            SpeakThresholdSlider(
                value = speakRmsUi,
                onValueChange = { voiceManager.setSpeakRmsThreshold(it) }
            )
        }
    }
}

@Composable
private fun DiscordVoiceControls(
    hasMic: Boolean,
    isMuted: Boolean,
    isDeafened: Boolean,
    pushToTalk: Boolean,
    pttActive: Boolean,
    onToggleMute: () -> Unit,
    onToggleDeafen: () -> Unit,
    onTogglePtt: () -> Unit,
    onPttPress: (Boolean) -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = onToggleMute,
            enabled = hasMic && !isDeafened,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isMuted) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Icon(
                if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = stringResource(R.string.watch_mic),
                modifier = Modifier.size(20.dp)
            )
        }
        FilledTonalIconButton(
            onClick = onToggleDeafen,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = if (isDeafened) MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Icon(
                Icons.Default.VolumeOff,
                contentDescription = stringResource(R.string.watch_deafen),
                modifier = Modifier.size(20.dp)
            )
        }
        FilterChip(
            selected = pushToTalk,
            onClick = onTogglePtt,
            label = { Text(stringResource(R.string.watch_ptt), style = MaterialTheme.typography.labelSmall) },
            leadingIcon = if (pushToTalk) {
                { Icon(Icons.Default.Mic, null, Modifier.size(14.dp)) }
            } else null,
            modifier = Modifier.height(32.dp)
        )
        if (pushToTalk && hasMic) {
            val pttBg = if (pttActive) com.watch.watchtofriend.ui.theme.SuccessGreen.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surfaceVariant
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(pttBg)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPttPress(true)
                                tryAwaitRelease()
                                onPttPress(false)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (pttActive) stringResource(R.string.watch_ptt_speaking) else stringResource(R.string.watch_ptt_hold),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pttActive) com.watch.watchtofriend.ui.theme.SuccessGreen
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Spacer(Modifier.weight(1f))
        }
        FilledTonalIconButton(
            onClick = onLeave,
            modifier = Modifier.size(40.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
            )
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.watch_leave_voice_channel),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun MicLevelBar(level: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(80),
        label = "micBar"
    )
    val trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)
    val fillColor = when {
        animated > 0.4f -> com.watch.watchtofriend.ui.theme.SuccessGreen
        animated > 0.1f -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.35f)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.coerceAtLeast(0.02f))
                .clip(RoundedCornerShape(4.dp))
                .background(fillColor)
        )
    }
}

@Composable
private fun VoiceSpeakingAvatar(
    initial: String,
    name: String = initial,
    photoBase64: String? = null,
    isSpeaking: Boolean,
    voiceConnected: Boolean = false,
    size: androidx.compose.ui.unit.Dp = 36.dp,
) {
    val ringAlpha by animateFloatAsState(
        targetValue = if (isSpeaking) 1f else if (voiceConnected) 0.55f else 0f,
        animationSpec = tween(120),
        label = "speakRing"
    )
    val ringColor = when {
        isSpeaking -> com.watch.watchtofriend.ui.theme.SuccessGreen
        voiceConnected -> MaterialTheme.colorScheme.primary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val inner = size - 8.dp
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (ringAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = if (isSpeaking) 3.dp else 2.dp,
                        color = ringColor.copy(alpha = ringAlpha),
                        shape = CircleShape
                    )
            )
        }
        Box(
            modifier = Modifier
                .size(inner)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            InitialAvatar(
                name = name.ifBlank { initial },
                size = inner.value.toInt().coerceAtLeast(20),
                photoBase64 = photoBase64,
            )
        }
    }
}

@Composable
private fun VoicePeerRow(
    peerUid: String,
    displayName: String,
    fullDisplayName: String,
    initial: String,
    photoBase64: String?,
    isSpeaking: Boolean,
    isSelf: Boolean,
    voiceConnected: Boolean,
    muted: Boolean,
    listenOnly: Boolean,
    connecting: Boolean,
    reconnecting: Boolean,
    micGain: Float,
    peerVolume: Float,
    locallyMuted: Boolean,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            VoiceSpeakingAvatar(
                initial = initial,
                name = fullDisplayName,
                photoBase64 = photoBase64,
                isSpeaking = isSpeaking,
                voiceConnected = voiceConnected && !muted && !listenOnly,
                size = 36.dp
            )
            if (muted || listenOnly) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .align(Alignment.BottomEnd)
                        .clip(CircleShape)
                        .background(
                            if (listenOnly) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (listenOnly) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            if (connecting) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(
                            if (reconnecting) com.watch.watchtofriend.ui.theme.WarningAmber
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
        }
        Column(modifier = Modifier.widthIn(min = 52.dp, max = 120.dp)) {
            Text(
                displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                when {
                    isSpeaking -> stringResource(R.string.watch_speaking)
                    isSelf && voiceConnected && !muted && !listenOnly -> stringResource(R.string.watch_connected)
                    muted || listenOnly -> stringResource(R.string.watch_muted)
                    else -> stringResource(R.string.watch_listening)
                },
                style = MaterialTheme.typography.labelSmall,
                color = when {
                    isSpeaking -> com.watch.watchtofriend.ui.theme.SuccessGreen
                    muted || listenOnly -> MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                    else -> MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.55f)
                }
            )
        }
        VoicePeerVolumeControl(
            peerUid = peerUid,
            isSelf = isSelf,
            micGain = micGain,
            peerVolume = peerVolume,
            locallyMuted = locallyMuted,
            onToggleMute = onToggleMute,
            onVolumeChange = onVolumeChange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VoicePeerVolumeControl(
    peerUid: String,
    isSelf: Boolean,
    micGain: Float,
    peerVolume: Float,
    locallyMuted: Boolean,
    onToggleMute: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val storedVol = if (isSelf) micGain / VoicePrefs.MIC_GAIN_MAX else peerVolume
    val muted = (isSelf && micGain < 0.01f) || locallyMuted
    var localVol by remember(peerUid) { mutableFloatStateOf(storedVol) }
    var dragging by remember(peerUid) { mutableStateOf(false) }

    LaunchedEffect(storedVol, muted) {
        if (!dragging) localVol = if (muted) 0f else storedVol
    }

    val sliderVol = if (muted) 0f else localVol.coerceIn(0f, 1f)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        IconButton(onClick = onToggleMute, modifier = Modifier.size(32.dp)) {
            Icon(
                when {
                    isSelf && micGain < 0.01f -> Icons.Default.VolumeOff
                    isSelf -> Icons.Default.Mic
                    locallyMuted -> Icons.Default.VolumeOff
                    else -> Icons.Default.VolumeDown
                },
                contentDescription = if (isSelf) stringResource(R.string.watch_mic_level)
                else if (locallyMuted) stringResource(R.string.watch_unmute_audio)
                else stringResource(R.string.watch_lower_audio),
                modifier = Modifier.size(18.dp),
                tint = when {
                    (isSelf && micGain < 0.01f) || locallyMuted -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                }
            )
        }
        Slider(
            value = sliderVol,
            onValueChange = { v ->
                dragging = true
                localVol = v
                onVolumeChange(if (isSelf) v * VoicePrefs.MIC_GAIN_MAX else v)
            },
            onValueChangeFinished = { dragging = false },
            enabled = isSelf || !locallyMuted,
            modifier = Modifier
                .weight(1f)
                .height(36.dp)
        )
    }
}

@Composable
private fun SpeakThresholdSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    var localUi by remember { mutableFloatStateOf(value) }
    var dragging by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (!dragging) localUi = value
    }

    val normalized = ((localUi - 2f) / 23f).coerceIn(0f, 1f)

    Slider(
        value = normalized,
        onValueChange = { n ->
            dragging = true
            localUi = 2f + n * 23f
            onValueChange(localUi)
        },
        onValueChangeFinished = { dragging = false },
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
    )
}

private fun buildYouTubeHtml(videoId: String, startSeconds: Int, autoPlay: Boolean): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<style>
  html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden;}
  #player{position:absolute;top:0;left:0;width:100%;height:100%;}
</style>
</head>
<body>
<div id="player"></div>
<script>
  var player;
  // Uzaktan gelen komut uygulanırken kendi olayımızı köprüye yansıtmamak için pencere
  var __suppressUntil = 0;
  // Video sonu olayı bu yükleme için yalnızca BİR kez köprüye iletilsin (YouTube
  // bazen ENDED'i 2 kez tetikleyip sırada video atlatabiliyordu).
  var __endedFired = false;
  var tag = document.createElement('script');
  tag.src = "https://www.youtube.com/iframe_api";
  document.body.appendChild(tag);

  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      videoId: '$videoId',
      playerVars: { 'playsinline': 1, 'autoplay': ${if (autoPlay) 1 else 0}, 'start': $startSeconds, 'rel': 0, 'modestbranding': 1, 'enablejsapi': 1, 'origin': 'https://watchtofriend.app' },
      events: { 'onStateChange': onPlayerStateChange, 'onReady': onPlayerReady, 'onError': onPlayerError }
    });
  }
  function onPlayerError(e) {
    if (window.WatchBridge && WatchBridge.onError) WatchBridge.onError(e.data);
  }
  function onPlayerReady(e) {
    if (window.WatchBridge && WatchBridge.onReady) WatchBridge.onReady();
    // Autoplay politikası engellerse açıkça oynat (host + misafir)
    if (${if (autoPlay) "true" else "false"} && player && player.playVideo) {
      try { player.playVideo(); } catch (err) {}
    }
  }
  function onPlayerStateChange(e) {
    if (!window.WatchBridge) return;
    // Not: Oynatıcının video_id değişimini izlemiyoruz — YouTube reklam/otomatik-sonraki
    // oynatmada yanlış tetikleyip oda videosunu kaçırıyordu. Sıradaki videolar "queue"
    // ile, video bitince geçiş onEnded ile yapılıyor.
    if (e.data === 0) { if (!__endedFired && WatchBridge.onEnded) { __endedFired = true; WatchBridge.onEnded(); } return; }
    if (Date.now() < __suppressUntil) return;
    var t = player.getCurrentTime();
    if (e.data === 1) { WatchBridge.onPlay(t); }
    else if (e.data === 2) { WatchBridge.onPause(t); }
  }
  // Periyodik konum bildirimi (host kayma düzeltmesi için çağırır)
  function reportPosition() {
    if (!player || !window.WatchBridge) return;
    var t = (player.getCurrentTime && player.getCurrentTime()) || 0;
    var state = (player.getPlayerState && player.getPlayerState()) || -1;
    if (state === 1) WatchBridge.onPlay(t);
    else if (state === 2) WatchBridge.onPause(t);
  }
  // Yerel ilerleme/süre bildirimi (yayınlamaz; çubuk + sapma için)
  function reportProgress() {
    if (!player || !window.WatchBridge || !WatchBridge.onProgress) return;
    try {
      var c = player.getCurrentTime ? player.getCurrentTime() : 0;
      var d = player.getDuration ? player.getDuration() : 0;
      WatchBridge.onProgress(c || 0, d || 0);
    } catch (e) {}
  }
  // Atomik uzak komut: önce ara, sonra oynat/durdur. seekTo(sec,true) oynatmayı tetiklediği
  // için durdurma durumunda kısa gecikmeyle yeniden durdururuz.
  function applyRemote(isPlaying, sec, doSeek, force) {
    __suppressUntil = Date.now() + 2500;
    if (!player) return;
    if (doSeek && player.seekTo) {
      // Bayat oda durumunda geçen-süre konumu video sonunu aşabilir → süreye sınırla
      var dur = (player.getDuration && player.getDuration()) || 0;
      if (dur > 1 && sec > dur - 1) sec = Math.max(0, dur - 1);
      // Sadece GERÇEK kayma (>1.5sn) varsa seek et; force ise her zaman
      var cur = (player.getCurrentTime && player.getCurrentTime()) || 0;
      if (force || Math.abs(cur - sec) > 1.5) player.seekTo(sec, true);
    }
    if (isPlaying) {
      if (player.playVideo) player.playVideo();
    } else {
      if (player.pauseVideo) player.pauseVideo();
      setTimeout(function() {
        __suppressUntil = Date.now() + 1200;
        if (player && player.pauseVideo) player.pauseVideo();
      }, 600);
    }
  }
</script>
</body>
</html>
""".trimIndent()

private fun buildVideoInjectionScript(): String = """
(function() {
  if (window.__wtfInjected) return;
  window.__wtfInjected = true;
  window.__suppressUntil = 0;
  window.__readyFired = false;

  // Ana belge + AYNI-ORIGIN iframe'ler içinde <video> arar.
  // (Cross-origin iframe'ler tarayıcı güvenliği nedeniyle erişilemez.)
  function findVideo() {
    var v = document.querySelector('video');
    if (v) return v;
    var frames = document.querySelectorAll('iframe');
    for (var i = 0; i < frames.length; i++) {
      try {
        var doc = frames[i].contentDocument;
        if (doc) {
          var fv = doc.querySelector('video');
          if (fv) return fv;
        }
      } catch (e) { /* cross-origin → atla */ }
    }
    return null;
  }

  window.tryPlay = function() {
    window.__suppressUntil = Date.now() + 1200;
    var v = findVideo(); if (v) v.play();
  };
  window.tryPause = function() {
    window.__suppressUntil = Date.now() + 1200;
    var v = findVideo(); if (v) v.pause();
  };
  window.trySeek = function(sec) {
    window.__suppressUntil = Date.now() + 1200;
    var v = findVideo(); if (v) { v.currentTime = sec; }
  };
  window.reportPosition = function() {
    var v = findVideo();
    if (!v || !window.WatchBridge) return;
    var t = v.currentTime || 0;
    if (!v.paused) WatchBridge.onPlay(t);
    else WatchBridge.onPause(t);
  };
  window.reportProgress = function() {
    var v = findVideo();
    if (v && window.WatchBridge && WatchBridge.onProgress) WatchBridge.onProgress(v.currentTime || 0, v.duration || 0);
  };
  window.applyRemote = function(isPlaying, sec, doSeek, force) {
    window.__suppressUntil = Date.now() + 2500;
    var v = findVideo();
    if (!v) return;
    if (doSeek) {
      var dur = v.duration || 0;
      if (dur > 1 && sec > dur - 1) sec = Math.max(0, dur - 1);
      // Sadece gerçek kayma (>1.5sn) varsa; force ise her zaman
      if (force || Math.abs((v.currentTime || 0) - sec) > 1.5) v.currentTime = sec;
    }
    if (isPlaying) { v.play(); }
    else {
      v.pause();
      setTimeout(function() {
        window.__suppressUntil = Date.now() + 1200;
        var v2 = findVideo();
        if (v2) v2.pause();
      }, 600);
    }
  };

  // Bir <video>'ya oynat/durdur dinleyicilerini (bir kez) bağla
  function attach(v) {
    if (!v || v.__wtfBound) return;
    v.__wtfBound = true;
    v.addEventListener('play', function() {
      if (Date.now() < window.__suppressUntil) return;
      if (window.WatchBridge) WatchBridge.onPlay(v.currentTime);
    });
    v.addEventListener('pause', function() {
      if (Date.now() < window.__suppressUntil) return;
      if (window.WatchBridge) WatchBridge.onPause(v.currentTime);
    });
    v.addEventListener('seeked', function() {
      if (Date.now() < window.__suppressUntil) return;
      if (!window.WatchBridge) return;
      var t = v.currentTime || 0;
      if (!v.paused) WatchBridge.onPlay(t);
      else WatchBridge.onPause(t);
    });
    if (!window.__readyFired && window.WatchBridge && WatchBridge.onReady) {
      window.__readyFired = true;
      WatchBridge.onReady();
    }
  }

  // Dinamik yüklenen oynatıcıları yakalamak için periyodik tarama (cross-origin'i çözmez).
  function scan() {
    var v = findVideo();
    if (v) attach(v);
  }
  scan();
  setInterval(scan, 1000);
  try {
    var mo = new MutationObserver(scan);
    mo.observe(document.documentElement, { childList: true, subtree: true });
  } catch (e) {}

  // ---- Tıklama-yansıtma (film sitesi "oynat"ı birlikte basmak için) ----
  window.__clickSuppressUntil = 0;
  function cssPath(el) {
    if (!el || el.nodeType !== 1) return '';
    if (el.id) { try { return '#' + CSS.escape(el.id); } catch (e) { return '#' + el.id; } }
    var parts = [];
    var node = el;
    while (node && node.nodeType === 1 && parts.length < 8) {
      var sel = node.tagName.toLowerCase();
      var parent = node.parentNode;
      if (parent && parent.children) {
        var same = [];
        for (var i = 0; i < parent.children.length; i++) {
          if (parent.children[i].tagName === node.tagName) same.push(parent.children[i]);
        }
        if (same.length > 1) sel += ':nth-of-type(' + (same.indexOf(node) + 1) + ')';
      }
      parts.unshift(sel);
      if (node.id) { try { parts[0] = '#' + CSS.escape(node.id); } catch (e) { parts[0] = '#' + node.id; } break; }
      node = parent;
    }
    return parts.join(' > ');
  }
  document.addEventListener('click', function(e) {
    if (Date.now() < window.__clickSuppressUntil) return; // uzak tıklamayı yankılama
    var p = cssPath(e.target);
    if (p && window.WatchBridge && WatchBridge.onSyncClick) WatchBridge.onSyncClick(p);
  }, true);

  window.__applyRemoteClick = function(sel) {
    try {
      window.__clickSuppressUntil = Date.now() + 1500;
      var el = document.querySelector(sel);
      if (el) el.click();
    } catch (e) {}
  };
})();
""".trimIndent()

// Reklam/yönlendirme başka domain'lere kaçmasın diye gezinmeyi aynı sitede tutar.
private fun hostOf(url: String): String {
    return try {
        val h = java.net.URI(url).host ?: return ""
        h.removePrefix("www.").lowercase()
    } catch (_: Exception) {
        ""
    }
}

private fun sameDomain(a: String, b: String): Boolean {
    val ha = hostOf(a)
    val hb = hostOf(b)
    // Biri çözülemezse navigasyon yayılmasın (ilk yükleme / geçersiz URL vb.)
    if (ha.isEmpty() || hb.isEmpty()) return false
    return ha == hb
}

// Tek bir Pattern instance — her çağrıda yeniden derlenmesini önler.
private val YOUTUBE_ID_PATTERN =
    Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/|youtube\\.com/embed/|youtube\\.com/shorts/)([\\w-]+)")

private fun extractYouTubeId(url: String): String? {
    val matcher = YOUTUBE_ID_PATTERN.matcher(url)
    return if (matcher.find()) matcher.group(1) else null
}
