package com.watch.watchtofriend.ui.room

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.repository.RoomRepository
import com.watch.watchtofriend.invite.InviteLink
import com.watch.watchtofriend.ui.components.GlowIconBadge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class JoinRoomViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = RoomRepository()
    private val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _joinedRoomId = MutableStateFlow<String?>(null)
    val joinedRoomId: StateFlow<String?> = _joinedRoomId

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _needsPassword = MutableStateFlow(false)
    val needsPassword: StateFlow<Boolean> = _needsPassword

    fun joinRoom(code: String, password: String = "") {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val ctx = getApplication<Application>()
            try {
                when (val res = repo.joinRoomChecked(code.uppercase(), uid, password)) {
                    is RoomRepository.JoinResult.Success -> _joinedRoomId.value = res.room.roomId
                    RoomRepository.JoinResult.NotFound -> _error.value = ctx.getString(R.string.join_err_not_found)
                    RoomRepository.JoinResult.WrongPassword -> {
                        _needsPassword.value = true
                        if (password.isNotBlank()) _error.value = ctx.getString(R.string.join_err_wrong_password)
                    }
                    RoomRepository.JoinResult.Full -> _error.value = ctx.getString(R.string.join_err_full)
                }
            } catch (_: Exception) {
                _error.value = ctx.getString(R.string.join_err_network)
            }
            _isLoading.value = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRoomScreen(
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
    initialCode: String? = null,
    vm: JoinRoomViewModel = viewModel()
) {
    val joinedRoomId by vm.joinedRoomId.collectAsState()
    val error by vm.error.collectAsState()
    val isLoading by vm.isLoading.collectAsState()
    val needsPassword by vm.needsPassword.collectAsState()
    val context = LocalContext.current
    var code by remember(initialCode) { mutableStateOf(initialCode?.uppercase() ?: "") }
    var roomPassword by remember { mutableStateOf("") }

    fun applyJoinInput(raw: String) {
        InviteLink.extractCodeFromInput(raw)?.let { code = it; return }
        if (raw.length <= 6) code = raw.uppercase()
    }

    LaunchedEffect(joinedRoomId) { joinedRoomId?.let { onJoined(it) } }
    LaunchedEffect(initialCode) {
        if (!initialCode.isNullOrBlank() && code.isBlank()) {
            code = initialCode.uppercase()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_title)) },
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
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GlowIconBadge(icon = Icons.Default.MeetingRoom, size = 84)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.join_heading), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(if (initialCode.isNullOrBlank()) R.string.join_hint_code else R.string.join_hint_invite),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(28.dp))
            OutlinedTextField(
                value = code,
                onValueChange = ::applyJoinInput,
                label = { Text(stringResource(R.string.join_code_label)) },
                placeholder = { Text("ABC123") },
                shape = MaterialTheme.shapes.medium,
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    textAlign = TextAlign.Center,
                    letterSpacing = 8.sp
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                trailingIcon = {
                    IconButton(onClick = {
                        val clip = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as? android.content.ClipboardManager
                        val text = clip?.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                        if (text.isNotBlank()) applyJoinInput(text)
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = stringResource(R.string.common_paste))
                    }
                }
            )
            if (needsPassword) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = roomPassword,
                    onValueChange = { roomPassword = it },
                    label = { Text(stringResource(R.string.join_password_label)) },
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.joinRoom(code, roomPassword) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = MaterialTheme.shapes.medium,
                enabled = code.length == 6 && !isLoading
            ) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                else {
                    Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.common_join), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
