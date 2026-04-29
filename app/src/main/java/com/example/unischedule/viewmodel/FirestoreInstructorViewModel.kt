package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.InstructorAvailability
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class FirestoreInstructorViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _scheduleState = MutableStateFlow<UiState<List<ScheduleEntry>>>(UiState.Loading)
    val scheduleState: StateFlow<UiState<List<ScheduleEntry>>> = _scheduleState.asStateFlow()

    private val _availabilityState = MutableStateFlow<UiState<List<InstructorAvailability>>>(UiState.Loading)
    val availabilityState: StateFlow<UiState<List<InstructorAvailability>>> = _availabilityState.asStateFlow()

    fun loadMySchedule(instructorId: Long) {
        _scheduleState.value = UiState.Loading
        viewModelScope.launch {
            repository.observeLecturerSchedule(instructorId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _scheduleState.value = UiState.Error(e.message ?: "Unknown Error")
                }
                .collect { state ->
                    _scheduleState.value = state
                }
        }
    }

    fun loadMyAvailability(instructorId: Long) {
        _availabilityState.value = UiState.Loading
        viewModelScope.launch {
            repository.observeInstructorAvailability(instructorId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _availabilityState.value = UiState.Error(e.message ?: "Unknown Error")
                }
                .collect { state ->
                    _availabilityState.value = state
                }
        }
    }

    fun toggleAvailability(instructorId: Long, dayOfWeek: Int, startTime: String, endTime: String = "") {
        viewModelScope.launch {
            try {
                repository.toggleInstructorAvailability(instructorId, dayOfWeek, startTime, endTime)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
