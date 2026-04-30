package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Department
import com.example.unischedule.data.firestore.Faculty
import com.example.unischedule.data.firestore.Lecturer
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

    // Lookup data for human-readable display + filtering + export
    private val _coursesState = MutableStateFlow<UiState<List<Course>>>(UiState.Loading)
    val coursesState: StateFlow<UiState<List<Course>>> = _coursesState.asStateFlow()

    private val _lecturersState = MutableStateFlow<UiState<List<Lecturer>>>(UiState.Loading)
    val lecturersState: StateFlow<UiState<List<Lecturer>>> = _lecturersState.asStateFlow()

    private val _classroomsState = MutableStateFlow<UiState<List<Classroom>>>(UiState.Loading)
    val classroomsState: StateFlow<UiState<List<Classroom>>> = _classroomsState.asStateFlow()

    private val _departmentsState = MutableStateFlow<UiState<List<Department>>>(UiState.Loading)
    val departmentsState: StateFlow<UiState<List<Department>>> = _departmentsState.asStateFlow()

    private val _facultiesState = MutableStateFlow<UiState<List<Faculty>>>(UiState.Loading)
    val facultiesState: StateFlow<UiState<List<Faculty>>> = _facultiesState.asStateFlow()

    // Operation feedback
    private val _operationState = MutableStateFlow<UiState<String>?>(null)
    val operationState: StateFlow<UiState<String>?> = _operationState.asStateFlow()

    init {
        loadAllSchedules()
        viewModelScope.launch { repository.observeCourses().collect { _coursesState.value = it } }
        viewModelScope.launch { repository.observeLecturers().collect { _lecturersState.value = it } }
        viewModelScope.launch { repository.observeClassrooms().collect { _classroomsState.value = it } }
        viewModelScope.launch { repository.observeDepartments().collect { _departmentsState.value = it } }
        viewModelScope.launch { repository.observeFaculties().collect { _facultiesState.value = it } }
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
                repository.deleteScheduleFlat(scheduleId)
                _operationState.value = UiState.Success("Schedule deleted")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _operationState.value = UiState.Error(e.message ?: "Delete failed")
            }
        }
    }

    /**
     * Check for conflicts before saving a schedule entry.
     * Returns null if no conflict, or a conflict message string.
     * excludeId: if editing, exclude this entry from conflict check.
     */
    fun checkConflicts(
        entry: ScheduleEntry,
        allSchedules: List<ScheduleEntry>,
        excludeId: Long? = null
    ): String? {
        val candidates = allSchedules.filter { existing ->
            existing.id != excludeId &&
            existing.dayOfWeek == entry.dayOfWeek &&
            timeRangesOverlap(existing.startTime, existing.endTime, entry.startTime, entry.endTime)
        }

        for (existing in candidates) {
            if (existing.courseId == entry.courseId && existing.lecturerId == entry.lecturerId) {
                return "This course is already assigned to this lecturer at this time."
            }
            if (existing.classroomId == entry.classroomId) {
                return "This classroom is already booked at this time."
            }
            if (existing.lecturerId == entry.lecturerId) {
                return "This lecturer already has a course at this time."
            }
        }
        return null
    }

    fun updateSchedule(entry: ScheduleEntry) {
        viewModelScope.launch {
            try {
                // Write ScheduleEntry to Firestore using the document ID
                val docId = entry.id.toString()
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                db.collection("schedules").document(docId).set(entry).await()
                _operationState.value = UiState.Success("Schedule updated")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _operationState.value = UiState.Error(e.message ?: "Update failed")
            }
        }
    }

    fun addScheduleEntry(entry: ScheduleEntry) {
        viewModelScope.launch {
            try {
                repository.assignScheduleAtomic(
                    courseId = entry.courseId,
                    lecturerId = entry.lecturerId,
                    classroomId = entry.classroomId,
                    dayOfWeek = entry.dayOfWeek,
                    startTime = entry.startTime,
                    endTime = entry.endTime
                )
                _operationState.value = UiState.Success("Schedule added")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _operationState.value = UiState.Error(e.message ?: "Add failed")
            }
        }
    }

    fun resetOperationState() {
        _operationState.value = null
    }

    private fun timeRangesOverlap(startA: String, endA: String, startB: String, endB: String): Boolean {
        return startA < endB && startB < endA
    }

    private suspend fun com.google.android.gms.tasks.Task<Void>.await() {
        kotlinx.coroutines.tasks.await(this)
    }
}
