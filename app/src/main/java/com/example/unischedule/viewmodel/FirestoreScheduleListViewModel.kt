package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

class FirestoreScheduleListViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _scheduleState = MutableStateFlow<UiState<List<ScheduleEntry>>>(UiState.Loading)
    val scheduleState: StateFlow<UiState<List<ScheduleEntry>>> = _scheduleState.asStateFlow()

    init {
        loadAllSchedules()
    }

    fun loadAllSchedules() {
        _scheduleState.value = UiState.Loading
        viewModelScope.launch {
            repository.observeSchedules()
                .catch { e ->
                    if (e is CancellationException) throw e
                    _scheduleState.value = UiState.Error(e.message ?: "Unknown Error")
                }
                .collect { state ->
                    _scheduleState.value = state
                }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            try {
                // Firestore delete operation would go here
                // For now, we'll reload the schedule list
                loadAllSchedules()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }
}
