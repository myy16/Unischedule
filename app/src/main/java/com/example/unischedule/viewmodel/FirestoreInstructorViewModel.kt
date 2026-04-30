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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class FirestoreInstructorViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _scheduleState = MutableStateFlow<UiState<List<ScheduleEntry>>>(UiState.Loading)
    val scheduleState: StateFlow<UiState<List<ScheduleEntry>>> = _scheduleState.asStateFlow()

    private val _availabilityState = MutableStateFlow<UiState<List<InstructorAvailability>>>(UiState.Loading)
    val availabilityState: StateFlow<UiState<List<InstructorAvailability>>> = _availabilityState.asStateFlow()

    private val _courseMap = MutableStateFlow<Map<Long, Course>>(emptyMap())
    val courseMap: StateFlow<Map<Long, Course>> = _courseMap.asStateFlow()

    private val _classroomMap = MutableStateFlow<Map<Long, Classroom>>(emptyMap())
    val classroomMap: StateFlow<Map<Long, Classroom>> = _classroomMap.asStateFlow()

    private val _lecturerMap = MutableStateFlow<Map<Long, Lecturer>>(emptyMap())
    val lecturerMap: StateFlow<Map<Long, Lecturer>> = _lecturerMap.asStateFlow()

    private var scheduleJob: Job? = null
    private var availabilityJob: Job? = null

    init {
        loadLookupMaps()
    }

    private fun loadLookupMaps() {
        viewModelScope.launch {
            repository.observeCourses().collect { state ->
                if (state is UiState.Success) _courseMap.value = state.data.associateBy { it.id }
            }
        }
        viewModelScope.launch {
            repository.observeClassrooms().collect { state ->
                if (state is UiState.Success) _classroomMap.value = state.data.associateBy { it.id }
            }
        }
        viewModelScope.launch {
            repository.observeLecturers().collect { state ->
                if (state is UiState.Success) _lecturerMap.value = state.data.associateBy { it.id }
            }
        }
    }

    fun loadMySchedule(instructorId: Long) {
        if (scheduleJob != null) return // Already observing
        _scheduleState.value = UiState.Loading
        scheduleJob = viewModelScope.launch {
            repository.observeLecturerSchedule(instructorId)
                .catch { e ->
                    if (e !is CancellationException) _scheduleState.value = UiState.Error(e.message ?: "Unknown Error")
                }
                .collect { state ->
                    _scheduleState.value = state
                }
        }
    }

    fun loadMyAvailability(instructorId: Long) {
        if (availabilityJob != null) return // Already observing
        _availabilityState.value = UiState.Loading
        availabilityJob = viewModelScope.launch {
            repository.observeInstructorAvailability(instructorId)
                .catch { e ->
                    if (e !is CancellationException) _availabilityState.value = UiState.Error(e.message ?: "Unknown Error")
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
