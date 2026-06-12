package com.watch.watchtofriend.ui.home

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.watch.watchtofriend.R
import com.watch.watchtofriend.ui.components.AppReview
import com.watch.watchtofriend.ui.components.HelpDialog
import com.watch.watchtofriend.ui.components.RatingPrefs
import com.watch.watchtofriend.ui.locale.LocalePrefs
import com.watch.watchtofriend.ui.components.copyToClipboard
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watch.watchtofriend.ui.components.rememberPhoto
import java.io.ByteArrayOutputStream
import com.google.firebase.auth.FirebaseAuth
import com.watch.watchtofriend.data.model.Request
import com.watch.watchtofriend.data.model.resolvedId
import com.watch.watchtofriend.ui.admin.ADMIN_EMAIL
import com.watch.watchtofriend.data.model.Room
import com.watch.watchtofriend.data.model.User
import com.watch.watchtofriend.ui.components.BrandLogo
import com.watch.watchtofriend.ui.components.EmptyState
import com.watch.watchtofriend.ui.components.FirstLaunchTourOverlay
import com.watch.watchtofriend.ui.components.InitialAvatar
import com.watch.watchtofriend.ui.components.TourPrefs
import com.watch.watchtofriend.ui.components.brandGradient
import com.watch.watchtofriend.ui.components.videoIconFor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onRoomClick: (String) -> Unit,
    onLogout: () -> Unit,
    onDmClick: (dmId: String, otherName: String) -> Unit = { _, _ -> },
    onAdminPanel: () -> Unit = {},
    initialTab: Int = 0,
    vm: HomeViewModel = viewModel()
) {
    val rooms by vm.rooms.collectAsState()
    val friends by vm.friends.collectAsState()
    val requests by vm.requests.collectAsState()
    val publicRooms by vm.publicRooms.collectAsState()
    val watchHistory by vm.watchHistory.collectAsState()
    val dmConversations by vm.dmConversations.collectAsState()
    val myUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val isAdmin = FirebaseAuth.getInstance().currentUser?.email == ADMIN_EMAIL
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { selectedTab = initialTab }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    val searchResult by vm.searchResult.collectAsState()
    val searchError by vm.searchError.collectAsState()
    val info by vm.info.collectAsState()
    var searchEmail by remember { mutableStateOf("") }
    var roomToDelete by remember { mutableStateOf<Room?>(null) }
    var showProfile by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showTour by remember { mutableStateOf(!TourPrefs.isDone(context)) }

    LaunchedEffect(Unit) {
        if (RatingPrefs.shouldAutoPrompt(context)) {
            (context as? Activity)?.let { AppReview.requestReview(it) }
        }
    }
    val myProfile by vm.myProfile.collectAsState()
    val isUpdatingProfile by vm.isUpdatingProfile.collectAsState()
    val isRemovingPhoto by vm.isRemovingPhoto.collectAsState()
    val blockedUsers by vm.blockedUsers.collectAsState()

    // Senaryo 1: Sessiz hataları Toast olarak göster
    LaunchedEffect(vm) {
        vm.toast.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val b64 = encodeImageToBase64(context, uri)
            if (b64 != null) vm.updatePhoto(b64)
            else android.widget.Toast.makeText(context, context.getString(R.string.home_photo_too_large), android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Çevrimiçi göstergesi: Home açıkken periyodik aktiflik bildir
    LaunchedEffect(Unit) {
        while (true) {
            vm.touchActive()
            kotlinx.coroutines.delay(30000)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            when (selectedTab) {
                0 -> Column(horizontalAlignment = Alignment.End) {
                    ExtendedFloatingActionButton(
                        onClick = onJoinRoom,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        icon = { Icon(Icons.Default.MeetingRoom, contentDescription = null) },
                        text = { Text(stringResource(R.string.home_fab_join)) }
                    )
                    Spacer(Modifier.height(12.dp))
                    ExtendedFloatingActionButton(
                        onClick = onCreateRoom,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = Color.White,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.home_fab_create)) }
                    )
                }
                1 -> ExtendedFloatingActionButton(
                    onClick = { showAddFriendDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                    text = { Text(stringResource(R.string.home_fab_add_friend)) }
                )
            }
        }
    ) { padding ->
        val unreadDmTotal = dmConversations.sumOf { it.unreadCount[myUid] ?: 0L }.coerceAtMost(99L).toInt()
        val sectionTitle = when (selectedTab) {
            0 -> stringResource(R.string.tab_rooms)
            1 -> stringResource(R.string.tab_friends)
            2 -> stringResource(R.string.tab_discover)
            else -> stringResource(R.string.tab_messages)
        }
        Row(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            DiscordNavRail(
                selected = selectedTab,
                onSelect = { selectedTab = it },
                requestCount = requests.size,
                unreadDmCount = unreadDmTotal,
                profile = myProfile,
                onProfileClick = { showProfile = true },
                onLogout = onLogout,
                isAdmin = isAdmin,
                onAdminPanel = onAdminPanel
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Tag,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            sectionTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                when (selectedTab) {
                0 -> RoomsList(
                    rooms = rooms,
                    myUid = myUid,
                    onRoomClick = onRoomClick,
                    onDeleteRoom = { roomToDelete = it }
                )
                1 -> FriendsTab(
                    friends = friends,
                    requests = requests,
                    onAccept = { vm.acceptRequest(it) },
                    onReject = { vm.rejectRequest(it) },
                    onRemoveFriend = { vm.removeFriend(it) },
                    onMessageClick = { friend ->
                        vm.openOrCreateDm(friend) { dmId, name -> onDmClick(dmId, name) }
                    }
                )
                2 -> DiscoverList(
                    rooms = publicRooms,
                    myUid = myUid,
                    onJoin = { roomId, password -> vm.joinPublicRoom(roomId, password) { onRoomClick(it) } }
                )
                3 -> DmConversationsList(
                    conversations = dmConversations,
                    myUid = myUid,
                    friends = friends,
                    onOpenDm = { friend ->
                        vm.openOrCreateDm(friend) { dmId, otherName -> onDmClick(dmId, otherName) }
                    },
                    onConvClick = { conv ->
                        val otherUid = conv.participantUids.firstOrNull { it != myUid } ?: return@DmConversationsList
                        val otherName = conv.participantNames[otherUid] ?: context.getString(R.string.common_user)
                        val dmId = conv.resolvedId(myUid)
                        if (dmId.isBlank()) return@DmConversationsList
                        onDmClick(dmId, otherName)
                    }
                )
                }
                }
            }
        }
    }

    if (roomToDelete != null) {
        AlertDialog(
            onDismissRequest = { roomToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.home_delete_room_title)) },
            text = { Text(stringResource(R.string.home_delete_room_body, roomToDelete?.roomId ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    roomToDelete?.roomId?.let { vm.deleteRoom(it) }
                    roomToDelete = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { roomToDelete = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }

    if (showTour) {
        FirstLaunchTourOverlay(
            onDone = {
                TourPrefs.setDone(context)
                showTour = false
            }
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }

    if (showAddFriendDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddFriendDialog = false
                vm.clearSearch()
                searchEmail = ""
            },
            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.home_add_friend_title)) },
            text = {
                Column {
                    myProfile?.friendCode?.takeIf { it.isNotBlank() }?.let { code ->
                        Text(
                            stringResource(R.string.home_your_id, code),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = searchEmail,
                        onValueChange = { searchEmail = it },
                        label = { Text(stringResource(R.string.home_friend_search_hint)) },
                        placeholder = { Text(stringResource(R.string.home_friend_search_placeholder)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                    searchResult?.let { result ->
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                InitialAvatar(result.displayName, size = 40, photoBase64 = result.photoBase64)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(result.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        result.friendCode.takeIf { it.isNotBlank() }?.let { "#$it" }
                                            ?: result.email,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { vm.sendFriendRequest(result.uid) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) { Text(stringResource(R.string.home_send_friend_request)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.searchUser(searchEmail) }) { Text(stringResource(R.string.common_search)) } },
            dismissButton = {
                TextButton(onClick = {
                    showAddFriendDialog = false
                    vm.clearSearch()
                    searchEmail = ""
                }) { Text(stringResource(R.string.common_close)) }
            }
        )
    }

    // Profil ekranı (avatar, ad, ID)
    if (showProfile) {
        var editName by remember(myProfile?.displayName) { mutableStateOf(myProfile?.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { if (!isRemovingPhoto) showProfile = false },
            title = { Text(stringResource(R.string.profile_title)) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            InitialAvatar(
                                name = myProfile?.displayName ?: "?",
                                size = 96,
                                photoBase64 = if (isRemovingPhoto) null else myProfile?.photoBase64
                            )
                            if (!isRemovingPhoto) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable(enabled = !isUpdatingProfile) {
                                            photoPicker.launch("image/*")
                                        }
                                ) {
                                    Icon(
                                        Icons.Default.PhotoCamera,
                                        contentDescription = stringResource(R.string.home_pick_photo),
                                        tint = Color.White,
                                        modifier = Modifier.padding(6.dp).size(18.dp)
                                    )
                                }
                            }
                        }
                        if (isRemovingPhoto) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.45f),
                                modifier = Modifier.size(96.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        color = Color.White,
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { photoPicker.launch("image/*") },
                        enabled = !isRemovingPhoto && !isUpdatingProfile
                    ) {
                        Text(stringResource(R.string.profile_pick_photo))
                    }
                    if (!myProfile?.photoBase64.isNullOrBlank() || isRemovingPhoto) {
                        OutlinedButton(
                            onClick = { vm.removePhoto() },
                            enabled = !isRemovingPhoto && !isUpdatingProfile,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.45f)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            if (isRemovingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.profile_removing))
                            } else {
                                Icon(
                                    Icons.Outlined.DeleteOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.profile_remove_photo))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.profile_display_name)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    // ID satırı (kopyalanabilir)
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Tag, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.profile_friend_id), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    "#${myProfile?.friendCode ?: "..."}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            IconButton(onClick = {
                                context.copyToClipboard(myProfile?.friendCode ?: "")
                                android.widget.Toast.makeText(context, context.getString(R.string.profile_id_copied), android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.common_copy))
                            }
                        }
                    }
                    val appVersion = remember {
                        runCatching {
                            val pm = context.packageManager
                            val pkg = context.packageName
                            val info = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                pm.getPackageInfo(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getPackageInfo(pkg, 0)
                            }
                            info.versionName ?: "?"
                        }.getOrDefault("?")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_version, appVersion),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.profile_theme),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    val themeMode = com.watch.watchtofriend.ui.theme.ThemePref.mode.intValue
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            stringResource(R.string.theme_system) to 0,
                            stringResource(R.string.theme_light) to 1,
                            stringResource(R.string.theme_dark) to 2
                        ).forEach { (label, value) ->
                            FilterChip(
                                selected = themeMode == value,
                                onClick = { com.watch.watchtofriend.ui.theme.ThemePref.set(context, value) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.profile_language),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    val localeTag = LocalePrefs.getTag(context)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            stringResource(R.string.lang_system) to "",
                            stringResource(R.string.lang_turkish) to "tr",
                            stringResource(R.string.lang_english) to "en"
                        ).forEach { (label, tag) ->
                            FilterChip(
                                selected = if (tag.isEmpty()) localeTag.isEmpty() else localeTag == tag,
                                onClick = {
                                    val normalized = if (tag.isEmpty()) "" else tag
                                    if (localeTag != normalized) {
                                        LocalePrefs.setTag(context, normalized)
                                        (context as? Activity)?.recreate()
                                    }
                                },
                                label = { Text(label, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            TourPrefs.reset(context)
                            showProfile = false
                            showTour = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_replay_tour)) }
                    TextButton(
                        onClick = { showHelp = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_help)) }
                    TextButton(
                        onClick = {
                            val act = context as? Activity
                            if (act != null) AppReview.requestReview(act)
                            else AppReview.openStoreListing(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.profile_rate)) }
                    // İzleme Geçmişi
                    if (watchHistory.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.profile_history),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { vm.clearAllHistory() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    stringResource(R.string.profile_clear_history),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            watchHistory.take(10).forEach { entry ->
                                val dateStr = java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(entry.watchedAt))
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                entry.title.ifBlank { entry.videoUrl }.take(50),
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1
                                            )
                                            Text(
                                                stringResource(R.string.home_history_meta, dateStr, entry.memberCount),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { vm.deleteHistory(entry.id) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = stringResource(R.string.common_delete),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Engellenenler listesi
                    LaunchedEffect(Unit) { vm.refreshBlockedUsers() }
                    if (blockedUsers.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PersonRemove,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.profile_blocked),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            blockedUsers.forEach { blockedUser ->
                                Surface(
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                                    ) {
                                        InitialAvatar(
                                            name = blockedUser.displayName.ifBlank { "?" },
                                            size = 32,
                                            photoBase64 = blockedUser.photoBase64.takeIf { it.isNotBlank() }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                blockedUser.displayName.ifBlank { stringResource(R.string.common_unknown_user) },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 1
                                            )
                                            if (blockedUser.email.isNotBlank()) {
                                                Text(
                                                    blockedUser.email,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                        TextButton(
                                            onClick = { vm.unblockUser(blockedUser.uid) },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text(
                                                stringResource(R.string.profile_unblock),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
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
                TextButton(
                    onClick = {
                        if (editName.isNotBlank() && editName != myProfile?.displayName) vm.updateName(editName)
                        showProfile = false
                    },
                    enabled = !isRemovingPhoto
                ) { Text(stringResource(R.string.profile_save)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showProfile = false },
                    enabled = !isRemovingPhoto
                ) { Text(stringResource(R.string.profile_close)) }
            }
        )
    }
}

@Composable
private fun DiscordNavRail(
    selected: Int,
    onSelect: (Int) -> Unit,
    requestCount: Int,
    unreadDmCount: Int,
    profile: User?,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    isAdmin: Boolean = false,
    onAdminPanel: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BrandLogo(size = 44)
        Spacer(Modifier.height(12.dp))
        NavRailItem(Icons.Default.MeetingRoom, stringResource(R.string.tab_rooms), selected == 0, 0) { onSelect(0) }
        NavRailItem(Icons.Default.Public, stringResource(R.string.tab_discover), selected == 2, 0) { onSelect(2) }
        NavRailItem(Icons.Default.Group, stringResource(R.string.tab_friends), selected == 1, requestCount) { onSelect(1) }
        NavRailItem(Icons.AutoMirrored.Filled.Message, stringResource(R.string.tab_messages), selected == 3, unreadDmCount) { onSelect(3) }
        Spacer(Modifier.weight(1f))
        if (isAdmin) {
            IconButton(onClick = onAdminPanel) {
                Icon(Icons.Default.Storage, contentDescription = stringResource(R.string.common_admin), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.common_logout), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .padding(bottom = 8.dp)
                .clip(CircleShape)
                .clickable(onClick = onProfileClick)
        ) {
            InitialAvatar(name = profile?.displayName ?: "?", size = 40, photoBase64 = profile?.photoBase64)
        }
    }
}

@Composable
private fun NavRailItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    badge: Int,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "railbg"
    )
    val fg by animateColorAsState(
        if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "railfg"
    )
    Box(
        modifier = Modifier.padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size(22.dp))
        }
        if (badge > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
            ) { Text("$badge", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun BrandHeader(
    profile: User?,
    onProfileClick: () -> Unit,
    onLogout: () -> Unit,
    isAdmin: Boolean = false,
    onAdminPanel: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BrandLogo(size = 40)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Watch with",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Friends",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        // Profil avatarı (tıklanınca profil ekranı)
        Box(modifier = Modifier.clip(CircleShape).clickable(onClick = onProfileClick).padding(4.dp)) {
            InitialAvatar(
                name = profile?.displayName ?: "?",
                size = 38,
                photoBase64 = profile?.photoBase64
            )
        }
        if (isAdmin) {
            IconButton(onClick = onAdminPanel) {
                Icon(
                    Icons.Default.Storage,
                    contentDescription = stringResource(R.string.common_admin),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.common_logout), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// Firestore tek alan üst sınırı ≈1 MB — ham base64 güvenli limit.
private const val MAX_PROFILE_PHOTO_BASE64_LEN = 900_000

// Galeriden seçilen resmi küçültüp base64 JPEG'e çevirir (Firestore için).
private fun encodeImageToBase64(context: android.content.Context, uri: Uri): String? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > 512 || bounds.outHeight / sample > 512) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: return null
        var maxEdge = 256
        var quality = 70
        var encoded = ""
        val out = ByteArrayOutputStream()
        repeat(10) {
            val ratio = maxEdge.toFloat() / maxOf(bmp.width, bmp.height).toFloat()
            val w = (bmp.width * ratio).toInt().coerceAtLeast(1)
            val h = (bmp.height * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(bmp, w, h, true)
            out.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
            encoded = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            if (encoded.length <= MAX_PROFILE_PHOTO_BASE64_LEN) return encoded
            if (quality > 45) quality -= 8 else maxEdge = (maxEdge * 0.8f).toInt().coerceAtLeast(96)
        }
        if (encoded.length <= MAX_PROFILE_PHOTO_BASE64_LEN) encoded else null
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun SegmentedTabs(selected: Int, onSelect: (Int) -> Unit, requestCount: Int, unreadDmCount: Int = 0) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            SegmentTab(stringResource(R.string.tab_rooms), selected == 0, Modifier.weight(1f), 0) { onSelect(0) }
            SegmentTab(stringResource(R.string.tab_discover), selected == 2, Modifier.weight(1f), 0) { onSelect(2) }
            SegmentTab(stringResource(R.string.tab_friends), selected == 1, Modifier.weight(1f), requestCount) { onSelect(1) }
            SegmentTab(stringResource(R.string.tab_messages), selected == 3, Modifier.weight(1f), unreadDmCount) { onSelect(3) }
        }
    }
}

@Composable
private fun SegmentTab(
    label: String,
    active: Boolean,
    modifier: Modifier,
    badge: Int,
    onClick: () -> Unit
) {
    val bg by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
        label = "segbg"
    )
    val fg by animateColorAsState(
        if (active) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "segfg"
    )
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
            if (badge > 0) {
                Spacer(Modifier.width(6.dp))
                Badge(containerColor = MaterialTheme.colorScheme.tertiary) { Text("$badge") }
            }
        }
    }
}

@Composable
private fun RoomsList(
    rooms: List<Room>,
    myUid: String,
    onRoomClick: (String) -> Unit,
    onDeleteRoom: (Room) -> Unit
) {
    if (rooms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.MeetingRoom,
                title = stringResource(R.string.home_empty_rooms_title),
                subtitle = stringResource(R.string.home_empty_rooms_sub)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            items(rooms, key = { it.roomId }) { room ->
                RoomItem(
                    room = room,
                    isHost = room.hostUid == myUid,
                    onClick = { onRoomClick(room.roomId) },
                    onDelete = { onDeleteRoom(room) }
                )
            }
        }
    }
}

@Composable
private fun DiscoverList(rooms: List<Room>, myUid: String, onJoin: (String, String) -> Unit) {
    var passwordRoom by remember { mutableStateOf<Room?>(null) }
    var passwordInput by remember { mutableStateOf("") }

    if (rooms.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.Default.Public,
                title = stringResource(R.string.home_empty_discover_title),
                subtitle = stringResource(R.string.home_empty_discover_sub)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
        ) {
            items(rooms, key = { it.roomId }) { room ->
                val hasPassword = room.password.isNotBlank()
                Card(
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable {
                            if (hasPassword && !room.memberUids.contains(myUid)) {
                                passwordInput = ""
                                passwordRoom = room
                            } else {
                                onJoin(room.roomId, "")
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(brandGradient),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(videoIconFor(room.videoUrl), contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(room.title.ifBlank { stringResource(R.string.home_room_default, room.roomId) }, style = MaterialTheme.typography.titleMedium, maxLines = 1, modifier = Modifier.weight(1f))
                                if (hasPassword) {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.common_locked), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.People, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.home_viewers, room.memberUids.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (room.scheduledAt > 0L) {
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Default.CalendarMonth, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                                    Spacer(Modifier.width(3.dp))
                                    Text(
                                        java.text.SimpleDateFormat("dd MMM HH:mm", java.util.Locale.getDefault())
                                            .format(java.util.Date(room.scheduledAt)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        FilledTonalButton(onClick = {
                            if (hasPassword && !room.memberUids.contains(myUid)) {
                                passwordInput = ""
                                passwordRoom = room
                            } else {
                                onJoin(room.roomId, "")
                            }
                        }) {
                            Text(if (room.memberUids.contains(myUid)) stringResource(R.string.common_enter) else stringResource(R.string.common_join))
                        }
                    }
                }
            }
        }
    }

    // Şifre dialog'u
    if (passwordRoom != null) {
        AlertDialog(
            onDismissRequest = { passwordRoom = null },
            icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.home_password_room_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.home_password_room_body, passwordRoom!!.title.ifBlank { passwordRoom!!.roomId }), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text(stringResource(R.string.common_password)) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val room = passwordRoom ?: return@TextButton
                    onJoin(room.roomId, passwordInput)
                    passwordRoom = null
                }) { Text(stringResource(R.string.common_join)) }
            },
            dismissButton = {
                TextButton(onClick = { passwordRoom = null }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}

@Composable
private fun RoomItem(
    room: Room,
    isHost: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Tag,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    room.title.ifBlank { room.roomId },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isHost) {
                    Spacer(Modifier.width(8.dp))
                    HostBadge()
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = room.videoUrl.take(48).let { if (room.videoUrl.length > 48) "$it…" else it },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.People,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "${room.memberUids.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (room.scheduledAt > 0L) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        if (isHost) {
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun HostBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    ) {
        Text(
            stringResource(R.string.common_host),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun FriendsTab(
    friends: List<User>,
    requests: List<Request>,
    onAccept: (Request) -> Unit,
    onReject: (Request) -> Unit,
    onRemoveFriend: (String) -> Unit,
    onMessageClick: (User) -> Unit = {}
) {
    var friendToRemove by remember { mutableStateOf<User?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
    ) {
        if (requests.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.home_incoming_requests)) }
            items(requests, key = { it.id }) { req ->
                RequestItem(req = req, onAccept = { onAccept(req) }, onReject = { onReject(req) })
            }
            item { SectionHeader(stringResource(R.string.home_friends_section)) }
        }
        if (friends.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.Group,
                        title = stringResource(R.string.home_empty_friends_title),
                        subtitle = stringResource(R.string.home_empty_friends_sub)
                    )
                }
            }
        } else {
            items(friends, key = { it.uid }) { friend ->
                FriendCard(
                    friend = friend,
                    onRemove = { friendToRemove = friend },
                    onMessage = { onMessageClick(friend) }
                )
            }
        }
    }

    if (friendToRemove != null) {
        val friend = friendToRemove!!
        AlertDialog(
            onDismissRequest = { friendToRemove = null },
            icon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.home_remove_friend_title)) },
            text = { Text(stringResource(R.string.home_remove_friend_body, friend.displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    onRemoveFriend(friend.uid)
                    friendToRemove = null
                }) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { friendToRemove = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun FriendCard(friend: User, onRemove: () -> Unit, onMessage: () -> Unit = {}) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val online = System.currentTimeMillis() - friend.lastActive < 90_000
            Box {
                InitialAvatar(friend.displayName, photoBase64 = friend.photoBase64)
                if (online) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(2.dp)
                            .clip(CircleShape)
                            .background(com.watch.watchtofriend.ui.theme.SuccessGreen)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(friend.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    friend.friendCode.takeIf { it.isNotBlank() }?.let { "#$it" } ?: friend.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            IconButton(onClick = onMessage) {
                Icon(Icons.AutoMirrored.Filled.Message, contentDescription = stringResource(R.string.home_send_message),
                    tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.PersonRemove, contentDescription = stringResource(R.string.home_remove_friend), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RequestItem(req: Request, onAccept: () -> Unit, onReject: () -> Unit) {
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(req.fromName.ifBlank { "?" })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(req.fromName.ifBlank { stringResource(R.string.common_some_user) }, style = MaterialTheme.typography.titleSmall)
                Text(
                    if (req.type == "room") stringResource(R.string.home_request_room_invite, req.roomId)
                    else stringResource(R.string.home_request_friend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onAccept,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) { Icon(Icons.Default.Check, contentDescription = stringResource(R.string.common_accept), tint = Color.White) }
            Spacer(Modifier.width(6.dp))
            FilledTonalIconButton(onClick = onReject) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_reject), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DmConversationsList(
    conversations: List<com.watch.watchtofriend.data.model.DmConversation>,
    myUid: String,
    friends: List<User>,
    onOpenDm: (User) -> Unit,
    onConvClick: (com.watch.watchtofriend.data.model.DmConversation) -> Unit
) {
    val pendingFriends = friends.filter { f ->
        conversations.none { c -> c.participantUids.contains(f.uid) }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 4.dp, bottom = 96.dp)
    ) {
        if (conversations.isEmpty() && friends.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.People,
                        title = stringResource(R.string.home_empty_dm_title),
                        subtitle = stringResource(R.string.home_empty_dm_sub)
                    )
                }
            }
        }
        items(conversations, key = { it.resolvedId(myUid).ifBlank { it.participantUids.joinToString() } }) { conv ->
            val otherUid = conv.participantUids.firstOrNull { it != myUid } ?: return@items
            val otherName = conv.participantNames[otherUid] ?: stringResource(R.string.common_user)
            val otherPhoto = conv.participantPhotos[otherUid]
            val unread = conv.unreadCount[myUid] ?: 0L
            ListItem(
                headlineContent = { Text(otherName, style = MaterialTheme.typography.titleSmall) },
                supportingContent = {
                    Text(
                        conv.lastMessage.take(60).ifBlank { stringResource(R.string.home_no_messages_yet) },
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unread > 0) androidx.compose.ui.text.font.FontWeight.SemiBold else null
                    )
                },
                leadingContent = { InitialAvatar(otherName, size = 44, photoBase64 = otherPhoto) },
                trailingContent = {
                    if (unread > 0) {
                        Badge(containerColor = MaterialTheme.colorScheme.primary) {
                            Text("$unread", color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                },
                modifier = Modifier.clickable { onConvClick(conv) }
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (pendingFriends.isNotEmpty()) {
            item {
                Text(
                    stringResource(R.string.home_friends_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(pendingFriends, key = { "f_${it.uid}" }) { friend ->
                ListItem(
                    headlineContent = { Text(friend.displayName, style = MaterialTheme.typography.titleSmall) },
                    supportingContent = { Text(stringResource(R.string.home_dm_start)) },
                    leadingContent = { InitialAvatar(friend.displayName, size = 44, photoBase64 = friend.photoBase64) },
                    modifier = Modifier.clickable { onOpenDm(friend) }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
