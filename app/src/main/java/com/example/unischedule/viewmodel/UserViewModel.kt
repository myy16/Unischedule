package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.flat.User
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job

class UserViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _profileState = MutableStateFlow<UiState<User>>(UiState.Loading)
    val profileState: StateFlow<UiState<User>> = _profileState.asStateFlow()

    private val _roleState = MutableStateFlow(UserRole.UNKNOWN)
    val roleState: StateFlow<UserRole> = _roleState.asStateFlow()

    private val _statusState = MutableStateFlow<UiState<String>>(UiState.Success("Available"))
    val statusState: StateFlow<UiState<String>> = _statusState.asStateFlow()

    private var statusJob: Job? = null

    fun loadCurrentUserProfile(userUid: String?) {
        if (userUid.isNullOrBlank()) {
            _profileState.value = UiState.Error("Missing user id")
            _roleState.value = UserRole.UNKNOWN
            return
        }

        viewModelScope.launch {
            try {
                val profile = repository.fetchUserProfile(userUid)
                if (profile == null) {
                    _profileState.value = UiState.Error("User profile not found")
                    _roleState.value = UserRole.UNKNOWN
                } else {
                    _profileState.value = UiState.Success(profile)
                    _roleState.value = profile.role.toUserRole()
                    _statusState.value = UiState.Success(profile.status.ifBlank { "Available" })

                    // Keep lightweight session fields in sync for existing screens.
                    UserSession.userName = profile.full_name
                    UserSession.userRole = when (_roleState.value) {
                        UserRole.ADMIN -> UserSession.Role.ADMIN
                        UserRole.INSTRUCTOR -> UserSession.Role.LECTURER
                        else -> UserSession.userRole
                    }
                }
            } catch (e: Exception) {
                _profileState.value = UiState.Error(e.message ?: "Failed to load profile")
                _roleState.value = UserRole.UNKNOWN
            }
        }
    }

    fun loadProfileFromFirebaseAuth() {
        viewModelScope.launch {
            try {
                val profile = repository.fetchCurrentUserProfileAfterAuth()
                if (profile == null) {
                    _profileState.value = UiState.Error("Authenticated user profile not found")
                    _roleState.value = UserRole.UNKNOWN
                } else {
                    _profileState.value = UiState.Success(profile)
                    _roleState.value = profile.role.toUserRole()
                    _statusState.value = UiState.Success(profile.status.ifBlank { "Available" })
                    UserSession.userName = profile.full_name
                    UserSession.userRole = when (_roleState.value) {
                        UserRole.ADMIN -> UserSession.Role.ADMIN
                        UserRole.INSTRUCTOR -> UserSession.Role.LECTURER
                        UserRole.STUDENT -> UserSession.Role.STUDENT
                        else -> UserSession.userRole
                    }
                }
            } catch (e: Exception) {
                _profileState.value = UiState.Error(e.message ?: "Failed to load authenticated profile")
                _roleState.value = UserRole.UNKNOWN
            }
        }
    }

    fun observeCurrentUserStatus(userUid: String?) {
        if (userUid.isNullOrBlank()) {
            _statusState.value = UiState.Success("Available")
            return
        }

        statusJob?.cancel()
        viewModelScope.launch {
            statusJob = launch {
                repository.observeUserStatus(userUid)
                .collect { state ->
                    _statusState.value = when (state) {
                        is UiState.Success -> UiState.Success(state.data.ifBlank { "Available" })
                        is UiState.Error -> UiState.Error(state.message)
                        is UiState.Loading -> UiState.Loading
                    }
                }
            }
        }
    }

    private fun String?.toUserRole(): UserRole {
        return when (this?.lowercase()) {
            "admin" -> UserRole.ADMIN
            "instructor", "lecturer" -> UserRole.INSTRUCTOR
            "student" -> UserRole.STUDENT
            else -> UserRole.UNKNOWN
        }
    }
}
