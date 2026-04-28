package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirestoreAssignmentViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _coursesState = MutableStateFlow<UiState<List<Course>>>(UiState.Loading)
    val coursesState: StateFlow<UiState<List<Course>>> = _coursesState.asStateFlow()

    private val _lecturersState = MutableStateFlow<UiState<List<Lecturer>>>(UiState.Loading)
    val lecturersState: StateFlow<UiState<List<Lecturer>>> = _lecturersState.asStateFlow()

    private val _classroomsState = MutableStateFlow<UiState<List<Classroom>>>(UiState.Loading)
    val classroomsState: StateFlow<UiState<List<Classroom>>> = _classroomsState.asStateFlow()

    private val _assignmentState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val assignmentState: StateFlow<UiState<Unit>> = _assignmentState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeCourses().collect { _coursesState.value = it }
        }
        viewModelScope.launch {
            repository.observeLecturers().collect { _lecturersState.value = it }
        }
        viewModelScope.launch {
            repository.observeAvailableClassrooms().collect { _classroomsState.value = it }
        }
    }

    fun assign(
        courseId: Long,
        lecturerId: Long,
        classroomId: Long,
        dayOfWeek: Int,
        startTime: String,
        endTime: String
    ) {
        _assignmentState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.assignScheduleAtomic(
                    courseId = courseId,
                    lecturerId = lecturerId,
                    classroomId = classroomId,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime
                )
                _assignmentState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _assignmentState.value = UiState.Error(e.message ?: "Assignment failed")
            }
        }
    }

    fun resetAssignmentState() {
        _assignmentState.value = UiState.Loading
    }
}
