package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.PasswordHasher
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PasswordChangeViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _changeState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val changeState: StateFlow<UiState<Unit>> = _changeState.asStateFlow()

    fun changePassword(lecturerId: Long, currentPassword: String, newPassword: String) {
        _changeState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val lecturer = repository.getLecturerById(lecturerId)
                if (lecturer == null) {
                    _changeState.value = UiState.Error("Kullanıcı bulunamadı")
                    return@launch
                }

                val currentHash = PasswordHasher.sha256(currentPassword)
                if (currentHash != lecturer.passwordHash && currentPassword != lecturer.passwordHash) {
                    _changeState.value = UiState.Error("Mevcut şifre yanlış")
                    return@launch
                }

                val newHash = PasswordHasher.sha256(newPassword)
                repository.updateLecturerFields(lecturerId, mapOf(
                    "passwordHash" to newHash,
                    "mustChangePassword" to false
                ))

                _changeState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _changeState.value = UiState.Error(e.message ?: "Şifre değiştirilemedi")
            }
        }
    }

    fun resetState() {
        _changeState.value = UiState.Loading
    }
}
