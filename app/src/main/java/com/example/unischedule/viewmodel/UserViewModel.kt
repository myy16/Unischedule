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

class UserViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _profileState = MutableStateFlow<UiState<User>>(UiState.Loading)
    val profileState: StateFlow<UiState<User>> = _profileState.asStateFlow()

    private val _roleState = MutableStateFlow(UserRole.UNKNOWN)
    val roleState: StateFlow<UserRole> = _roleState.asStateFlow()

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
                    UserSession.userName = profile.full_name
                }
            } catch (e: Exception) {
                _profileState.value = UiState.Error(e.message ?: "Failed to load authenticated profile")
                _roleState.value = UserRole.UNKNOWN
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
