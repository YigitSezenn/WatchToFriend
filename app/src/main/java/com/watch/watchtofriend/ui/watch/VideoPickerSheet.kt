package com.watch.watchtofriend.ui.watch

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.model.QueueItem
import com.watch.watchtofriend.data.model.YtSearchResult

private enum class PickerTab { SEARCH, LINK }

private data class PlatformInfo(val name: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private fun detectPlatform(context: Context, url: String): PlatformInfo? = when {
    url.contains("youtube.com") || url.contains("youtu.be") ->
        PlatformInfo("YouTube", Color(0xFFFF0000), Icons.Default.PlayArrow)
    url.contains("twitch.tv") ->
        PlatformInfo("Twitch", Color(0xFF9146FF), Icons.Default.LiveTv)
    url.contains("vimeo.com") ->
        PlatformInfo("Vimeo", Color(0xFF1AB7EA), Icons.Default.VideoLibrary)
    url.contains("dailymotion.com") ->
        PlatformInfo("Dailymotion", Color(0xFF0066DC), Icons.Default.OndemandVideo)
    url.endsWith(".mp4") || url.endsWith(".m3u8") || url.endsWith(".mkv") || url.contains(".mp4?") ->
        PlatformInfo(context.getString(R.string.video_platform_direct), Color(0xFF4CAF50), Icons.Default.OndemandVideo)
    url.startsWith("http") ->
        PlatformInfo(context.getString(R.string.video_platform_web), Color(0xFF607D8B), Icons.Default.Language)
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPickerSheet(
    sheetState: SheetState,
    ytResults: List<YtSearchResult>,
    isSearching: Boolean,
    canAddToQueue: Boolean,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onClearResults: () -> Unit,
    onPlay: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onPlayResult: (YtSearchResult) -> Unit,
    onQueueResult: (YtSearchResult) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current

    var tab by remember { mutableStateOf(PickerTab.SEARCH) }
    var searchQuery by remember { mutableStateOf("") }
    var linkUrl by remember { mutableStateOf("") }
    val searchFocus = remember { FocusRequester() }

    val platform = remember(linkUrl) { if (linkUrl.length > 8) detectPlatform(context, linkUrl) else null }

    ModalBottomSheet(
        onDismissRequest = {
            onClearResults()
            onDismiss()
        },
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Text(
                stringResource(R.string.video_add_content),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                SegmentedButton(
                    selected = tab == PickerTab.SEARCH,
                    onClick = { tab = PickerTab.SEARCH; onClearResults() },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { SegmentedButtonDefaults.ActiveIcon() },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Search, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.video_search_yt))
                        }
                    }
                )
                SegmentedButton(
                    selected = tab == PickerTab.LINK,
                    onClick = { tab = PickerTab.LINK; onClearResults() },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { SegmentedButtonDefaults.ActiveIcon() },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Link, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.video_enter_link))
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()

            AnimatedContent(targetState = tab, label = "tab_anim") { currentTab ->
                when (currentTab) {
                    PickerTab.SEARCH -> SearchTab(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        isSearching = isSearching,
                        results = ytResults,
                        canAddToQueue = canAddToQueue,
                        focusRequester = searchFocus,
                        onSearch = {
                            keyboard?.hide()
                            if (searchQuery.isNotBlank()) onSearch(searchQuery)
                        },
                        onPlay = { r ->
                            onPlayResult(r)
                            onClearResults()
                            onDismiss()
                        },
                        onQueue = { r ->
                            onQueueResult(r)
                        }
                    )
                    PickerTab.LINK -> LinkTab(
                        url = linkUrl,
                        onUrlChange = { linkUrl = it },
                        platform = platform,
                        canAddToQueue = canAddToQueue,
                        onPaste = {
                            linkUrl = clipboard.getText()?.text.orEmpty()
                        },
                        onPlay = {
                            if (linkUrl.isNotBlank()) {
                                onPlay(linkUrl)
                                onDismiss()
                            }
                        },
                        onQueue = {
                            if (linkUrl.isNotBlank()) {
                                onAddToQueue(linkUrl)
                                linkUrl = ""
                            }
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(tab) {
        if (tab == PickerTab.SEARCH) {
            runCatching { searchFocus.requestFocus() }
        }
    }
}

@Composable
private fun SearchTab(
    query: String,
    onQueryChange: (String) -> Unit,
    isSearching: Boolean,
    results: List<YtSearchResult>,
    canAddToQueue: Boolean,
    focusRequester: FocusRequester,
    onSearch: () -> Unit,
    onPlay: (YtSearchResult) -> Unit,
    onQueue: (YtSearchResult) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.video_search_ph)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (query.isNotBlank()) {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, stringResource(R.string.common_search), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .focusRequester(focusRequester)
        )

        if (results.isEmpty() && !isSearching) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Search,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(56.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.video_search_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(results, key = { it.videoId }) { result ->
                    SearchResultCard(
                        result = result,
                        canAddToQueue = canAddToQueue,
                        onPlay = { onPlay(result) },
                        onQueue = { onQueue(result) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: YtSearchResult,
    canAddToQueue: Boolean,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (result.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(result.thumbnail).build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(width = 100.dp, height = 66.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    result.channel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = onPlay,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.video_play_now), style = MaterialTheme.typography.labelSmall)
                    }
                    if (canAddToQueue) {
                        OutlinedButton(
                            onClick = onQueue,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.video_queue), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkTab(
    url: String,
    onUrlChange: (String) -> Unit,
    platform: PlatformInfo?,
    canAddToQueue: Boolean,
    onPaste: () -> Unit,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 24.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            placeholder = { Text(stringResource(R.string.video_url_ph)) },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            trailingIcon = {
                if (url.isNotBlank()) {
                    IconButton(onClick = { onUrlChange("") }) {
                        Icon(Icons.Default.Clear, stringResource(R.string.common_clear))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onPaste,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ContentPaste, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.video_paste_clipboard))
        }

        AnimatedVisibility(visible = platform != null) {
            platform?.let { p ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = p.color.copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(p.icon, null, tint = p.color, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            p.name,
                            style = MaterialTheme.typography.labelLarge,
                            color = p.color
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        val enabled = url.length > 8
        Button(
            onClick = onPlay,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.video_play_immediate))
        }
        if (canAddToQueue) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onQueue,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.video_add_queue))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    sheetState: SheetState,
    queue: List<QueueItem>,
    ytResults: List<YtSearchResult>,
    isSearching: Boolean,
    isHostUser: Boolean,
    myUid: String,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onClearResults: () -> Unit,
    onAddUrl: (String) -> Unit,
    onAddResult: (YtSearchResult) -> Unit,
    onPlayResult: (YtSearchResult) -> Unit,
    onPlayItem: (QueueItem) -> Unit,
    onRemoveItem: (QueueItem) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { onClearResults(); onDismiss() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.PlaylistPlay,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    if (queue.isNotEmpty()) stringResource(R.string.video_queue_title_count, queue.size)
                    else stringResource(R.string.video_queue_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { showPicker = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.video_queue_add))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))

            if (queue.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.PlaylistPlay,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.video_queue_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.video_queue_empty_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 480.dp)
                ) {
                    itemsIndexed(queue, key = { _, it -> it.id }) { idx, item ->
                        QueueItemCard(
                            index = idx + 1,
                            item = item,
                            canPlay = isHostUser,
                            canRemove = isHostUser || item.addedBy == myUid,
                            onPlay = { onPlayItem(item) },
                            onRemove = { onRemoveItem(item) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }

    if (showPicker) {
        VideoPickerSheet(
            sheetState = pickerState,
            ytResults = ytResults,
            isSearching = isSearching,
            canAddToQueue = true,
            onDismiss = { showPicker = false; onClearResults() },
            onSearch = onSearch,
            onClearResults = onClearResults,
            onPlay = { url -> onAddUrl(url); showPicker = false },
            onAddToQueue = { url -> onAddUrl(url); showPicker = false },
            onPlayResult = { r -> onPlayResult(r); showPicker = false },
            onQueueResult = { r -> onAddResult(r) }
        )
    }
}

@Composable
private fun QueueItemCard(
    index: Int,
    item: QueueItem,
    canPlay: Boolean,
    canRemove: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$index",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title.ifBlank { item.url }.take(55),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    item.addedByName.ifBlank { "?" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (canPlay) {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Default.PlayArrow, stringResource(R.string.common_play), tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (canRemove) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, stringResource(R.string.common_delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
