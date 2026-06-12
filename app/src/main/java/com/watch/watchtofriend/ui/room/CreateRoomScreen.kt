package com.watch.watchtofriend.ui.room

import android.app.Application
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.repository.RoomRepository
import com.watch.watchtofriend.ui.components.GlowIconBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CreateRoomViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RoomRepository()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _roomCode = MutableStateFlow<String?>(null)
    val roomCode: StateFlow<String?> = _roomCode

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun createRoom(
        videoUrl: String, title: String = "", discoverable: Boolean = false,
        password: String = "", maxMembers: Int = 0, scheduledAt: Long = 0L
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _roomCode.value = repo.createRoom(uid, videoUrl, title, discoverable, password, maxMembers, scheduledAt)
            } catch (_: Exception) {
                _error.value = getApplication<Application>().getString(R.string.room_err_create_failed)
            }
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    onBack: () -> Unit,
    onRoomCreated: (String) -> Unit,
    vm: CreateRoomViewModel = viewModel()
) {
    val roomCode by vm.roomCode.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val createError by vm.error.collectAsState()
    var videoUrl by remember { mutableStateOf("") }
    var roomTitle by remember { mutableStateOf("") }
    var discoverable by remember { mutableStateOf(false) }
    var roomPassword by remember { mutableStateOf("") }
    var maxMembersText by remember { mutableStateOf("") }
    var scheduledAt by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    val templates = stringArrayResource(R.array.room_templates)

    LaunchedEffect(roomCode) { roomCode?.let { onRoomCreated(it) } }

    val urlError = when {
        videoUrl.isNotBlank() && !videoUrl.startsWith("http://") && !videoUrl.startsWith("https://") ->
            stringResource(R.string.room_err_url)
        else -> null
    }
    val pastDateError = scheduledAt > 0L && scheduledAt < System.currentTimeMillis()
    val canCreate = !isLoading && urlError == null && !pastDateError

    val scheduledLabel = if (scheduledAt > 0L) {
        SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(scheduledAt))
    } else null

    fun pickDateTime() {
        val now = Calendar.getInstance()
        DatePickerDialog(context, { _, y, m, d ->
            TimePickerDialog(context, { _, h, min ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d, h, min, 0)
                scheduledAt = cal.timeInMillis
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.room_create_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlowIconBadge(icon = Icons.Default.MovieCreation, size = 84)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.room_create_heading), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.room_create_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))
            Text(
                stringResource(R.string.room_templates),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                templates.forEach { template ->
                    val selected = roomTitle == template
                    FilterChip(
                        selected = selected,
                        onClick = { roomTitle = if (selected) "" else template },
                        label = { Text(template, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = roomTitle,
                onValueChange = { roomTitle = it },
                label = { Text(stringResource(R.string.room_name_hint)) },
                placeholder = { Text(stringResource(R.string.room_name_placeholder)) },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = videoUrl,
                onValueChange = { videoUrl = it },
                label = { Text(stringResource(R.string.room_video_hint)) },
                placeholder = { Text(stringResource(R.string.room_video_placeholder)) },
                leadingIcon = { Icon(Icons.Default.AddLink, contentDescription = null) },
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.room_schedule), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when {
                            pastDateError -> stringResource(R.string.room_schedule_past)
                            scheduledLabel != null -> scheduledLabel
                            else -> stringResource(R.string.room_schedule_now)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            pastDateError -> MaterialTheme.colorScheme.error
                            scheduledAt > 0L -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                Row {
                    if (scheduledAt > 0L) {
                        TextButton(onClick = { scheduledAt = 0L }) { Text(stringResource(R.string.common_clear)) }
                    }
                    IconButton(onClick = { pickDateTime() }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.room_pick_date),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.room_public), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.room_public_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = discoverable, onCheckedChange = { discoverable = it })
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = roomPassword,
                    onValueChange = { roomPassword = it },
                    label = { Text(stringResource(R.string.room_password_hint)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = maxMembersText,
                    onValueChange = { v -> maxMembersText = v.filter { it.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.room_max_members)) },
                    placeholder = { Text("∞") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.width(130.dp)
                )
            }
            Spacer(Modifier.height(24.dp))
            if (createError != null) {
                Text(
                    createError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            Button(
                onClick = {
                    vm.createRoom(
                        videoUrl.trim(), roomTitle.trim(), discoverable,
                        roomPassword.trim(), maxMembersText.toIntOrNull() ?: 0,
                        scheduledAt
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = canCreate
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else Text(
                    stringResource(if (scheduledAt > 0L) R.string.room_schedule_btn else R.string.room_create_btn),
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
