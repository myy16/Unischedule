package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.util.PasswordHasher
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PasswordChangeViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _changeState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val changeState: StateFlow<UiState<Unit>> = _changeState.asStateFlow()

    fun changePassword(userId: Long, currentPassword: String, newPassword: String, role: UserSession.Role?) {
        _changeState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val currentHash = PasswordHasher.sha256(currentPassword)

                when (role) {
                    UserSession.Role.ADMIN -> {
                        val admin = repository.getAdminById(userId)
                        if (admin == null) {
                            _changeState.value = UiState.Error("Kullanıcı bulunamadı")
                            return@launch
                        }
                        if (currentHash != admin.passwordHash && currentPassword != admin.passwordHash) {
                            _changeState.value = UiState.Error("Mevcut şifre yanlış")
                            return@launch
                        }

                        val newHash = PasswordHasher.sha256(newPassword)
                        repository.updateAdminFields(userId, mapOf("passwordHash" to newHash))
                        _changeState.value = UiState.Success(Unit)
                    }
                    UserSession.Role.LECTURER, UserSession.Role.INSTRUCTOR -> {
                        val lecturer = repository.getLecturerById(userId)
                        if (lecturer == null) {
                            _changeState.value = UiState.Error("Kullanıcı bulunamadı")
                            return@launch
                        }
                        if (currentHash != lecturer.passwordHash && currentPassword != lecturer.passwordHash) {
                            _changeState.value = UiState.Error("Mevcut şifre yanlış")
                            return@launch
                        }

                        val newHash = PasswordHasher.sha256(newPassword)
                        repository.updateLecturerFields(userId, mapOf(
                            "passwordHash" to newHash,
                            "mustChangePassword" to false
                        ))
                        _changeState.value = UiState.Success(Unit)
                    }
                    else -> {
                        _changeState.value = UiState.Error("Geçersiz kullanıcı rolü")
                    }
                }
            } catch (e: Exception) {
                _changeState.value = UiState.Error(e.message ?: "Şifre değiştirilemedi")
            }
        }
    }

    fun resetState() {
        _changeState.value = UiState.Loading
    }
}
