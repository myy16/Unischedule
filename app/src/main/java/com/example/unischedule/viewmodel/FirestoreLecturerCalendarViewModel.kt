package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.InstructorAvailability
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Task 5: ViewModel for lecturer's calendar view.
 * Provides real-time schedule updates filtered by logged-in lecturer ID.
 * Follows sustainable coding: lifecycle-aware collection via repeatOnLifecycle(STARTED) in Fragment.
 */
class FirestoreLecturerCalendarViewModel(
    private val repository: FirestoreRepository,
    private val lecturerId: Long
) : ViewModel() {

    private val _lecturerScheduleState = MutableStateFlow<UiState<List<ScheduleEntry>>>(UiState.Loading)
    val lecturerScheduleState: StateFlow<UiState<List<ScheduleEntry>>> = _lecturerScheduleState.asStateFlow()

    private val _availabilityState = MutableStateFlow<UiState<List<InstructorAvailability>>>(UiState.Loading)
    val availabilityState: StateFlow<UiState<List<InstructorAvailability>>> = _availabilityState.asStateFlow()

    init {
        observeSchedule()
        observeAvailability()
    }

    private fun observeSchedule() {
        viewModelScope.launch {
            repository.observeLecturerSchedule(lecturerId).collect { state ->
                _lecturerScheduleState.value = state
            }
        }
    }

    private fun observeAvailability() {
        viewModelScope.launch {
            repository.observeInstructorAvailability(lecturerId).collect { state ->
                _availabilityState.value = state
            }
        }
    }
}
