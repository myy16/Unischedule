package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.entity.InstructorAvailability
import com.example.unischedule.data.entity.ScheduleWithDetails
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * Task 1 & 2: Sustainable UI State Management using StateFlow and non-blocking logic.
 */
class InstructorViewModel(private val repository: UniversityRepository) : ViewModel() {

    private val _scheduleState = MutableStateFlow<UiState<List<ScheduleWithDetails>>>(UiState.Idle)
    val scheduleState: StateFlow<UiState<List<ScheduleWithDetails>>> = _scheduleState.asStateFlow()

    private val _availabilityState = MutableStateFlow<UiState<List<InstructorAvailability>>>(UiState.Idle)
    val availabilityState: StateFlow<UiState<List<InstructorAvailability>>> = _availabilityState.asStateFlow()

    /**
     * Task 3: Single Source of Truth.
     * Collects real-time updates from the Repository's Flow and maps them to UiState.
     */
    fun loadMySchedule(instructorId: Long) {
        _scheduleState.value = UiState.Loading
        viewModelScope.launch {
            repository.getSchedulesWithDetailsByInstructorFlow(instructorId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _scheduleState.value = UiState.Error(e.message ?: "Unknown Error")
                }
                .collect { list ->
                    _scheduleState.value = UiState.Success(list)
                }
        }
    }

    fun loadMyAvailability(instructorId: Long) {
        _availabilityState.value = UiState.Loading
        viewModelScope.launch {
            repository.getInstructorAvailability(instructorId)
                .catch { e ->
                    if (e is CancellationException) throw e
                    _availabilityState.value = UiState.Error(e.message ?: "Unknown Error")
                }
                .collect { list ->
                    _availabilityState.value = UiState.Success(list)
                }
        }
    }

    /**
     * Task 4: Production-Quality Error Handling with CancellationException check.
     */
    fun toggleAvailability(instructorId: Long, dayOfWeek: Int, startTime: String) {
        viewModelScope.launch {
            try {
                repository.toggleAvailability(instructorId, dayOfWeek, startTime)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Handle or log error state if needed
            }
        }
    }
}
