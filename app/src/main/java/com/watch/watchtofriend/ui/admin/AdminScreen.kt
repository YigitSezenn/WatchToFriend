package com.watch.watchtofriend.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.database
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.*

internal const val ADMIN_EMAIL = "ysezenn@outlook.com"

data class RoomInfo(
    val id: String,
    val title: String,
    val hostUid: String,
    val activeUsers: Int,
    val memberCount: Int,
    val voiceCount: Int,
    val hasVideo: Boolean,
    val isSharingScreen: Boolean,
    val createdAt: Long
)

data class UserInfo(
    val uid: String,
    val displayName: String,
    val email: String,
    val friendCount: Int,
    val lastActive: Long,
    val online: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(onBack: () -> Unit) {
    // Güvenlik: sadece admin e-postasıyla giriş yapmış kullanıcı görebilir.
    // Navigasyon katmanındaki kontrol bypass edilse bile burada ikinci engel olur.
    val currentEmail = FirebaseAuth.getInstance().currentUser?.email
    if (currentEmail?.lowercase() != ADMIN_EMAIL.lowercase()) {
        // Yetkisiz erişim — geri yönlendir
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val db = remember { Firebase.firestore }
    val rtdb = remember { Firebase.database("https://watchtofriend-default-rtdb.firebaseio.com/").reference }
    val scope = rememberCoroutineScope()

    var rooms by remember { mutableStateOf<List<RoomInfo>>(emptyList()) }
    var users by remember { mutableStateOf<List<UserInfo>>(emptyList()) }
    var totalUsers by remember { mutableStateOf(0) }
    var activeShareCount by remember { mutableStateOf(0) }
    var activeRoomCount by remember { mutableStateOf(0) }
    var dmCount by remember { mutableStateOf(0) }
    var reportCount by remember { mutableStateOf(-1) }
    var rtdbFrameCount by remember { mutableStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var lastRefresh by remember { mutableStateOf("") }

    fun refresh() {
        isLoading = true
        scope.launch {
            try {
                // Firestore: odaları çek
                val roomSnap = db.collection("rooms").get().await()
                val now = System.currentTimeMillis()
                val roomList = roomSnap.documents.mapNotNull { doc ->
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val hostUid = doc.getString("hostUid") ?: ""
                    val presence = doc.get("presence") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val activeUsers = presence.values.count { (it as? Long)?.let { ts -> now - ts < 60_000 } == true }
                    val members = doc.get("memberUids") as? List<*> ?: emptyList<Any>()
                    val memberCount = members.size
                    var voiceCount = 0
                    if (activeUsers > 0) {
                        try {
                            val voiceSnap = db.collection("rooms").document(doc.id)
                                .collection("voicePeers").get().await()
                            voiceCount = voiceSnap.size()
                        } catch (_: Exception) { }
                    }
                    val hasVideo = !doc.getString("videoUrl").isNullOrBlank()
                    val isSharingScreen = !doc.getString("screenShareUid").isNullOrBlank()
                    val createdAt = doc.getLong("createdAt") ?: 0L
                    RoomInfo(doc.id, title, hostUid, activeUsers, memberCount, voiceCount, hasVideo, isSharingScreen, createdAt)
                }.sortedByDescending { it.activeUsers }
                rooms = roomList
                activeShareCount = roomList.count { it.isSharingScreen }
                activeRoomCount = roomList.count { it.activeUsers > 0 }

                val userSnap = db.collection("users").get().await()
                totalUsers = userSnap.size()
                users = userSnap.documents.map { doc ->
                    val lastActive = doc.getLong("lastActive") ?: 0L
                    val friends = doc.get("friendIds") as? List<*> ?: emptyList<Any>()
                    UserInfo(
                        uid = doc.id,
                        displayName = doc.getString("displayName").orEmpty().ifBlank { "—" },
                        email = doc.getString("email").orEmpty().ifBlank { "—" },
                        friendCount = friends.size,
                        lastActive = lastActive,
                        online = lastActive > 0 && now - lastActive < 5 * 60_000
                    )
                }.sortedByDescending { it.lastActive }

                dmCount = db.collection("dms").get().await().size()
                try {
                    reportCount = db.collection("reports").get().await().size()
                } catch (_: Exception) {
                    reportCount = -1
                }

                // RTDB: aktif ekran paylaşım node sayısı
                val rtdbSnap = rtdb.child("screenShare").get().await()
                rtdbFrameCount = rtdbSnap.childrenCount.toInt()

                lastRefresh = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            } catch (e: Exception) {
                lastRefresh = "Hata: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // RTDB online listener — DisposableEffect kullanılıyor; composable kapanınca listener kaldırılır
    DisposableEffect(Unit) {
        refresh()
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                rtdbFrameCount = snapshot.childrenCount.toInt()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        val ref = rtdb.child("screenShare")
        ref.addValueEventListener(listener)
        onDispose {
            ref.removeEventListener(listener)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Admin Paneli") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (!isLoading) {
                        IconButton(onClick = { refresh() }) {
                            Icon(Icons.Default.Refresh, "Yenile")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Özet kartları ──────────────────────────────────────────────
            item {
                Text(
                    "Genel Bakış",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MeetingRoom,
                        label = "Toplam Oda",
                        value = rooms.size.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.People,
                        label = "Kayıtlı Kullanıcı",
                        value = totalUsers.toString()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Wifi,
                        label = "Aktif Kullanıcı",
                        value = rooms.sumOf { it.activeUsers }.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.MeetingRoom,
                        label = "Aktif Oda",
                        value = activeRoomCount.toString()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.ScreenShare,
                        label = "Ekran Paylaşımı",
                        value = "$activeShareCount aktif"
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.AutoMirrored.Filled.Message,
                        label = "DM Sohbet",
                        value = dmCount.toString()
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Report,
                        label = "Şikayet",
                        value = if (reportCount < 0) "—" else reportCount.toString()
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Default.Storage,
                        label = "RTDB Paylaşım",
                        value = "$rtdbFrameCount node"
                    )
                }
                Spacer(Modifier.height(8.dp))
                StatCard(
                    modifier = Modifier.fillMaxWidth(),
                    icon = Icons.Default.AccessTime,
                    label = "Son Güncelleme",
                    value = lastRefresh
                )
            }

            // ── Kullanıcı listesi ─────────────────────────────────────────
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kullanıcılar ($totalUsers)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(users.take(50), key = { it.uid }) { user ->
                UserCard(user)
            }

            // ── Firebase Kota Notu ─────────────────────────────────────────
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Firebase Ücretsiz Kota (Spark)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        QuotaRow("Firestore Okuma", "50.000/gün", Icons.Default.MenuBook)
                        QuotaRow("Firestore Yazma", "20.000/gün", Icons.Default.Edit)
                        QuotaRow("Firestore Silme", "20.000/gün", Icons.Default.Delete)
                        QuotaRow("Firestore Depolama", "1 GB", Icons.Default.Cloud)
                        QuotaRow("RTDB İndirme", "10 GB/ay", Icons.Default.Download)
                        QuotaRow("RTDB Depolama", "1 GB", Icons.Default.Storage)
                        HorizontalDivider()
                        Text(
                            "Not: Ekran paylaşımı ~30KB/frame × 2sn = ~72MB/saat/izleyici",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // ── Oda listesi ───────────────────────────────────────────────
            item {
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text(
                    "Odalar (${rooms.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(rooms, key = { it.id }) { room ->
                RoomCard(room)
            }

            if (rooms.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { Text("Henüz oda yok") }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Card(modifier = modifier) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun QuotaRow(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UserCard(user: UserInfo) {
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${user.friendCount} arkadaş · ${fmt.format(Date(user.lastActive))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = if (user.online) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    if (user.online) "Çevrimiçi" else "Kapalı",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun RoomCard(room: RoomInfo) {
    val fmt = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (room.isSharingScreen) {
                    Icon(
                        Icons.Default.ScreenShare,
                        "Ekran paylaşımı",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                if (room.hasVideo) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.PlayCircle,
                        "Video",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.People, null, modifier = Modifier.size(14.dp))
                    Text("${room.activeUsers} aktif / ${room.memberCount} üye", style = MaterialTheme.typography.bodySmall)
                }
                if (room.voiceCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Default.Mic, null, modifier = Modifier.size(14.dp))
                        Text("${room.voiceCount} ses", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text(
                    fmt.format(Date(room.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                "ID: ${room.id}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
