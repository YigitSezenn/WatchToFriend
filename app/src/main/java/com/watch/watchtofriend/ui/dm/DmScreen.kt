package com.watch.watchtofriend.ui.dm

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.model.Message
import com.watch.watchtofriend.notifications.NotificationHelper
import com.watch.watchtofriend.ui.components.InitialAvatar
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val EMOJI_LIST = listOf("❤️", "😂", "👍", "🔥", "😮", "😢")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmScreen(
    dmId: String,
    otherName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val resolvedDmId = remember(dmId) { dmId.trim().ifBlank { null } } ?: run {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val vm: DmViewModel = viewModel(
        key = resolvedDmId,
        factory = DmViewModelFactory(
            context.applicationContext as android.app.Application,
            resolvedDmId
        )
    )
    val messages by vm.messages.collectAsState()
    DisposableEffect(resolvedDmId) {
        NotificationHelper.activeDmId = resolvedDmId
        onDispose { NotificationHelper.activeDmId = null }
    }
    var chatInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(vm) {
        vm.toast.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(otherName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.dm_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.dm_empty_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    itemsIndexed(messages, key = { idx, m -> m.id.ifEmpty { "${idx}_${m.timestamp}" } }) { _, msg ->
                        DmMessageBubble(
                            msg = msg,
                            isMe = msg.senderUid == vm.uid,
                            myUid = vm.uid,
                            onReaction = { emoji -> vm.toggleReaction(msg.id, emoji) },
                            onDelete = { vm.deleteMessage(msg.id) }
                        )
                    }
                }
            }

            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatInput,
                        onValueChange = { chatInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.dm_input_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = {
                            val draft = chatInput
                            vm.sendMessage(draft) { sent ->
                                if (sent) chatInput = ""
                            }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DmMessageBubble(
    msg: Message,
    isMe: Boolean,
    myUid: String,
    onReaction: (String) -> Unit,
    onDelete: () -> Unit = {}
) {
    var showEmojiMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isMe) {
            InitialAvatar(msg.senderName.ifBlank { "?" }, size = 30, photoBase64 = null)
            Spacer(Modifier.width(8.dp))
        }

        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Box {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp, topEnd = 18.dp,
                        bottomStart = if (isMe) 18.dp else 4.dp,
                        bottomEnd = if (isMe) 4.dp else 18.dp
                    ),
                    color = if (isMe) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { showEmojiMenu = true }
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Text(
                            msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isMe) Color.White.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }

                DropdownMenu(
                    expanded = showEmojiMenu,
                    onDismissRequest = { showEmojiMenu = false }
                ) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        EMOJI_LIST.forEach { emoji ->
                            val selected = msg.reactions[myUid] == emoji
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                TextButton(
                                    onClick = {
                                        onReaction(emoji)
                                        showEmojiMenu = false
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(emoji, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                    if (isMe) {
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                onDelete()
                                showEmojiMenu = false
                            }
                        )
                    }
                }
            }

            if (msg.reactions.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                val grouped = msg.reactions.entries.groupBy { it.value }
                    .mapValues { it.value.size }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    grouped.forEach { (emoji, count) ->
                        val iMine = msg.reactions[myUid] == emoji
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (iMine) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                        ) {
                            TextButton(
                                onClick = { onReaction(emoji) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "$emoji $count",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (iMine) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
