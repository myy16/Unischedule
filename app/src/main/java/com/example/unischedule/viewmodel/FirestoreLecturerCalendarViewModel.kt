package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.InstructorAvailability
import com.example.unischedule.data.firestore.Lecturer
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
 * Phase 2: Also resolves course names and classroom names from Firestore.
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

    // Phase 2: Course, Classroom, and Lecturer lookup maps for resolving IDs to names
    private val _courseMap = MutableStateFlow<Map<Long, Course>>(emptyMap())
    val courseMap: StateFlow<Map<Long, Course>> = _courseMap.asStateFlow()

    private val _classroomMap = MutableStateFlow<Map<Long, Classroom>>(emptyMap())
    val classroomMap: StateFlow<Map<Long, Classroom>> = _classroomMap.asStateFlow()

    private val _lecturerMap = MutableStateFlow<Map<Long, Lecturer>>(emptyMap())
    val lecturerMap: StateFlow<Map<Long, Lecturer>> = _lecturerMap.asStateFlow()

    init {
        observeSchedule()
        observeAvailability()
        loadLookupData()
    }

    private fun observeSchedule() {
        viewModelScope.launch {
            repository.observeSchedules().collect { state ->
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

    /**
     * Phase 2: Load all courses, classrooms, and lecturers for ID-to-name resolution in calendar cells.
     */
    private fun loadLookupData() {
        viewModelScope.launch {
            repository.observeCourses().collect { state ->
                if (state is UiState.Success) {
                    _courseMap.value = state.data.associateBy { it.id }
                }
            }
        }
        viewModelScope.launch {
            repository.observeClassrooms().collect { state ->
                if (state is UiState.Success) {
                    _classroomMap.value = state.data.associateBy { it.id }
                }
            }
        }
        viewModelScope.launch {
            repository.observeLecturers().collect { state ->
                if (state is UiState.Success) {
                    _lecturerMap.value = state.data.associateBy { it.id }
                }
            }
        }
    }
}
