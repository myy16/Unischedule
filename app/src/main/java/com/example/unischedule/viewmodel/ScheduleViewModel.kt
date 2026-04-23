package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.entity.*
import com.example.unischedule.data.repository.AssignmentResult
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScheduleViewModel(private val repository: UniversityRepository) : ViewModel() {

    private val _scheduleDetailsState = MutableStateFlow<UiState<List<ScheduleWithDetails>>>(UiState.Idle)
    val scheduleDetailsState: StateFlow<UiState<List<ScheduleWithDetails>>> = _scheduleDetailsState.asStateFlow()

    private val _assignmentState = MutableStateFlow<UiState<AssignmentResult?>>(UiState.Idle)
    val assignmentState: StateFlow<UiState<AssignmentResult?>> = _assignmentState.asStateFlow()

    private val _coursesState = MutableStateFlow<UiState<List<Course>>>(UiState.Idle)
    val coursesState: StateFlow<UiState<List<Course>>> = _coursesState.asStateFlow()

    init {
        loadSchedules()
        loadCourses()
    }

    private fun loadSchedules() {
        _scheduleDetailsState.value = UiState.Loading
        viewModelScope.launch {
            repository.getAllSchedulesWithDetailsFlow()
                .catch { e ->
                    if (e is CancellationException) throw e
                    _scheduleDetailsState.value = UiState.Error(e.message ?: "Failed to load schedules")
                }
                .collect { _scheduleDetailsState.value = UiState.Success(it) }
        }
    }

    private fun loadCourses() {
        _coursesState.value = UiState.Loading
        viewModelScope.launch {
            repository.getAllCourses()
                .catch { e ->
                    if (e is CancellationException) throw e
                    _coursesState.value = UiState.Error(e.message ?: "Failed to load courses")
                }
                .collect { _coursesState.value = UiState.Success(it) }
        }
    }

    fun tryAddSchedule(schedule: Schedule, departmentId: Long, semester: Int, force: Boolean = false) {
        _assignmentState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val result = repository.addScheduleAtomically(schedule, departmentId, semester, force)
                _assignmentState.value = UiState.Success(result)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _assignmentState.value = UiState.Error(e.message ?: "Assignment failed")
            }
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            try {
                repository.deleteSchedule(schedule)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
        }
    }

    fun resetAssignmentState() {
        _assignmentState.value = UiState.Idle
    }
}
