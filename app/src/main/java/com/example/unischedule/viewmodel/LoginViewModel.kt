package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.repository.AuthenticatedUser
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.PasswordHasher
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _loginState = MutableStateFlow<UiState<AuthenticatedUser>>(UiState.Loading)
    val loginState: StateFlow<UiState<AuthenticatedUser>> = _loginState.asStateFlow()

    fun login(username: String, rawPassword: String) {
        _loginState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val authenticated = repository.authenticateUser(username, rawPassword)
                if (authenticated == null) {
                    _loginState.value = UiState.Error("Invalid credentials")
                } else {
                    val profile = repository.fetchUserProfile(authenticated.id.toString())
                    val enrichedUser = authenticated.copy(
                        username = profile?.full_name ?: authenticated.username
                    )
                    _loginState.value = UiState.Success(enrichedUser)
                }
            } catch (e: Exception) {
                _loginState.value = UiState.Error(e.message ?: "Login failed")
            }
        }
    }

    fun resetState() {
        _loginState.value = UiState.Loading
    }
}