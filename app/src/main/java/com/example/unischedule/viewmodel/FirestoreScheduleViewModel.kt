package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.repository.AssignmentResult
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirestoreScheduleViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _coursesState = MutableStateFlow<UiState<List<Course>>>(UiState.Loading)
    val coursesState: StateFlow<UiState<List<Course>>> = _coursesState.asStateFlow()

    private val _lecturersState = MutableStateFlow<UiState<List<Lecturer>>>(UiState.Loading)
    val lecturersState: StateFlow<UiState<List<Lecturer>>> = _lecturersState.asStateFlow()

    private val _classroomsState = MutableStateFlow<UiState<List<Classroom>>>(UiState.Loading)
    val classroomsState: StateFlow<UiState<List<Classroom>>> = _classroomsState.asStateFlow()

    private val _assignmentState = MutableStateFlow<UiState<AssignmentResult?>>(UiState.Loading)
    val assignmentState: StateFlow<UiState<AssignmentResult?>> = _assignmentState.asStateFlow()

    init {
        viewModelScope.launch { repository.observeCourses().collect { _coursesState.value = it } }
        viewModelScope.launch { repository.observeLecturers().collect { _lecturersState.value = it } }
        viewModelScope.launch { repository.observeAvailableClassrooms().collect { _classroomsState.value = it } }
    }

    fun tryAddSchedule(
        course: Course,
        lecturerId: Long,
        classroomId: Long,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        force: Boolean = false
    ) {
        _assignmentState.value = UiState.Loading
        viewModelScope.launch {
            try {
                repository.assignScheduleAtomic(
                    courseId = course.id,
                    lecturerId = lecturerId,
                    classroomId = classroomId,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime
                )
                _assignmentState.value = UiState.Success(AssignmentResult.Success)
            } catch (e: Exception) {
                _assignmentState.value = UiState.Success(mapErrorToResult(e.message ?: "Assignment failed", force))
            }
        }
    }

    fun resetAssignmentState() {
        _assignmentState.value = UiState.Loading
    }

    private fun mapErrorToResult(message: String, force: Boolean): AssignmentResult {
        return when {
            message.contains("Lecturer conflict", ignoreCase = true) -> AssignmentResult.InstructorBusy(message)
            message.contains("Room conflict", ignoreCase = true) -> AssignmentResult.RoomOccupied(message)
            message.contains("Group conflict", ignoreCase = true) -> AssignmentResult.StudentGroupConflict(message)
            message.contains("Lecturer is already assigned", ignoreCase = true) -> AssignmentResult.InstructorBusy(message)
            message.contains("Classroom is already booked", ignoreCase = true) -> AssignmentResult.RoomOccupied(message)
            message.contains("Mandatory courses", ignoreCase = true) -> AssignmentResult.StudentGroupConflict(message)
            message.contains("Unavailable", ignoreCase = true) && !force -> AssignmentResult.InstructorPreferenceConflict
            else -> AssignmentResult.Error(message)
        }
    }
}