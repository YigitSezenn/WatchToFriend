package com.watch.watchtofriend.ui.auth

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.watch.watchtofriend.R
import com.watch.watchtofriend.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private fun friendlyAuthError(ctx: Context, e: Throwable?): String = when {
    e is FirebaseAuthWeakPasswordException -> ctx.getString(R.string.auth_err_weak_password)
    e is FirebaseAuthInvalidUserException -> ctx.getString(R.string.auth_err_user_not_found)
    e is FirebaseAuthUserCollisionException -> ctx.getString(R.string.auth_err_email_in_use)
    e is FirebaseAuthInvalidCredentialsException -> {
        val m = e.message?.lowercase() ?: ""
        if (m.contains("email") || m.contains("badly formatted")) ctx.getString(R.string.auth_err_invalid_email)
        else ctx.getString(R.string.auth_err_wrong_password)
    }
    e is FirebaseNetworkException -> ctx.getString(R.string.auth_err_network)
    else -> {
        val m = e?.message?.lowercase() ?: ""
        when {
            m.contains("password is invalid") || m.contains("incorrect") || m.contains("malformed") ||
                m.contains("credential") -> ctx.getString(R.string.auth_err_wrong_password)
            m.contains("no user record") -> ctx.getString(R.string.auth_err_user_not_found)
            m.contains("already in use") -> ctx.getString(R.string.auth_err_email_in_use)
            m.contains("at least 6") || m.contains("weak") -> ctx.getString(R.string.auth_err_weak_password)
            m.contains("badly formatted") || m.contains("email") -> ctx.getString(R.string.auth_err_invalid_email)
            m.contains("network") || m.contains("timeout") || m.contains("unreachable") ->
                ctx.getString(R.string.auth_err_network)
            m.contains("blocked") || m.contains("too many") ->
                ctx.getString(R.string.auth_err_too_many)
            else -> ctx.getString(R.string.auth_err_generic)
        }
    }
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val isSuccess: Boolean = false
)

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AuthRepository()

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state

    val isLoggedIn: Boolean get() = repo.currentUser != null

    fun getGoogleSignInIntent(context: Context): Intent = repo.getGoogleSignInIntent(context)

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _state.value = AuthUiState(isLoading = true)
            val result = repo.signInWithGoogle(idToken)
            val ctx = getApplication<Application>()
            _state.value = if (result.isSuccess) {
                AuthUiState(isSuccess = true)
            } else {
                AuthUiState(error = friendlyAuthError(ctx, result.exceptionOrNull()))
            }
        }
    }

    fun login(email: String, password: String) {
        val e = email.trim()
        val ctx = getApplication<Application>()
        if (e.isBlank() || password.isBlank()) {
            _state.value = AuthUiState(error = ctx.getString(R.string.auth_err_credentials_required))
            return
        }
        viewModelScope.launch {
            _state.value = AuthUiState(isLoading = true)
            val result = repo.login(e, password)
            _state.value = if (result.isSuccess) {
                AuthUiState(isSuccess = true)
            } else {
                AuthUiState(error = friendlyAuthError(ctx, result.exceptionOrNull()))
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        val e = email.trim()
        val ctx = getApplication<Application>()
        if (displayName.isBlank()) {
            _state.value = AuthUiState(error = ctx.getString(R.string.auth_err_name_required))
            return
        }
        if (e.isBlank()) {
            _state.value = AuthUiState(error = ctx.getString(R.string.auth_err_email_required))
            return
        }
        if (password.length < 6) {
            _state.value = AuthUiState(error = ctx.getString(R.string.auth_err_password_min))
            return
        }
        viewModelScope.launch {
            _state.value = AuthUiState(isLoading = true)
            val result = repo.register(e, password, displayName.trim())
            _state.value = if (result.isSuccess) {
                AuthUiState(isSuccess = true)
            } else {
                AuthUiState(error = friendlyAuthError(ctx, result.exceptionOrNull()))
            }
        }
    }

    fun resetPassword(email: String) {
        val e = email.trim()
        val ctx = getApplication<Application>()
        if (e.isBlank() || !e.contains("@")) {
            _state.value = _state.value.copy(error = ctx.getString(R.string.auth_err_reset_email), info = null)
            return
        }
        viewModelScope.launch {
            _state.value = AuthUiState(isLoading = true)
            val result = repo.sendPasswordReset(e)
            _state.value = if (result.isSuccess) {
                AuthUiState(info = ctx.getString(R.string.auth_info_reset_sent))
            } else {
                AuthUiState(error = friendlyAuthError(ctx, result.exceptionOrNull()))
            }
        }
    }

    fun logout() {
        repo.logout()
        _state.value = AuthUiState()
    }

    fun showAuthError(message: String) {
        _state.value = AuthUiState(error = message)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null, info = null)
    }
}
