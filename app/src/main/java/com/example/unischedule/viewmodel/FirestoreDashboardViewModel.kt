package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Dashboard data model containing a human-readable recent activity line.
 */
data class RecentActivityItem(
    val courseCode: String,
    val lecturerName: String,
    val classroomName: String,
    val dayLabel: String,
    val startTime: String
)

class FirestoreDashboardViewModel(private val repository: FirestoreRepository) : ViewModel() {

    // ── SECTION 1: Stats counts ──────────────────────────────────────────
    private val _totalLecturersState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val totalLecturersState: StateFlow<UiState<Int>> = _totalLecturersState.asStateFlow()

    private val _totalCoursesState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val totalCoursesState: StateFlow<UiState<Int>> = _totalCoursesState.asStateFlow()

    private val _totalClassroomsState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val totalClassroomsState: StateFlow<UiState<Int>> = _totalClassroomsState.asStateFlow()

    // ── SECTION 2: Warning counts ────────────────────────────────────────
    private val _unassignedLecturersState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val unassignedLecturersState: StateFlow<UiState<Int>> = _unassignedLecturersState.asStateFlow()

    private val _unassignedCoursesState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val unassignedCoursesState: StateFlow<UiState<Int>> = _unassignedCoursesState.asStateFlow()

    private val _fullyBookedClassroomsState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val fullyBookedClassroomsState: StateFlow<UiState<Int>> = _fullyBookedClassroomsState.asStateFlow()

    // ── SECTION 3: Recent activity ───────────────────────────────────────
    private val _recentActivityState = MutableStateFlow<UiState<List<RecentActivityItem>>>(UiState.Loading)
    val recentActivityState: StateFlow<UiState<List<RecentActivityItem>>> = _recentActivityState.asStateFlow()

    // Caches for cross-referencing
    private var lecturerMap: Map<Long, String> = emptyMap()
    private var courseMap: Map<Long, String> = emptyMap()
    private var classroomMap: Map<Long, String> = emptyMap()
    private var scheduleEntries: List<ScheduleEntry> = emptyList()

    init {
        // Section 1: Total counts
        observeCount(repository.observeLecturers(), _totalLecturersState)
        observeCount(repository.observeCourses(), _totalCoursesState)
        observeCount(repository.observeClassrooms(), _totalClassroomsState)

        // Section 2: Warning counts
        observeCount(repository.observeUnassignedLecturersCorrect(), _unassignedLecturersState)
        observeCount(repository.observeUnassignedCoursesCorrect(), _unassignedCoursesState)

        // Section 2: Fully booked classrooms (classrooms used in 5+ schedule slots this week)
        observeFullyBookedClassrooms()

        // Build lookup maps and recent activity
        observeLookupData()
    }

    private fun <T> observeCount(
        source: kotlinx.coroutines.flow.Flow<UiState<List<T>>>,
        target: MutableStateFlow<UiState<Int>>
    ) {
        viewModelScope.launch {
            source.collect { state ->
                target.value = when (state) {
                    is UiState.Loading -> UiState.Loading
                    is UiState.Error -> UiState.Error(state.message)
                    is UiState.Success -> UiState.Success(state.data.size)
                }
            }
        }
    }

    private fun observeFullyBookedClassrooms() {
        viewModelScope.launch {
            repository.observeSchedules().collect { state ->
                when (state) {
                    is UiState.Success -> {
                        // Count unique slots per classroom; "fully booked" = 5+ slots (whole week filled)
                        val slotsPerClassroom = state.data.groupBy { it.classroomId }
                        val fullyBooked = slotsPerClassroom.count { (_, slots) -> slots.size >= 5 }
                        _fullyBookedClassroomsState.value = UiState.Success(fullyBooked)
                    }
                    is UiState.Error -> _fullyBookedClassroomsState.value = UiState.Error(state.message)
                    is UiState.Loading -> _fullyBookedClassroomsState.value = UiState.Loading
                }
            }
        }
    }

    private fun observeLookupData() {
        // Observe lecturers for name lookup
        viewModelScope.launch {
            repository.observeLecturers().collect { state ->
                if (state is UiState.Success) {
                    lecturerMap = state.data.associate { it.id to it.fullName.ifBlank { it.username } }
                    buildRecentActivity()
                }
            }
        }
        // Observe courses for code lookup
        viewModelScope.launch {
            repository.observeCourses().collect { state ->
                if (state is UiState.Success) {
                    courseMap = state.data.associate { it.id to it.code.ifBlank { it.name } }
                    buildRecentActivity()
                }
            }
        }
        // Observe classrooms for name lookup
        viewModelScope.launch {
            repository.observeClassrooms().collect { state ->
                if (state is UiState.Success) {
                    classroomMap = state.data.associate { it.id to it.name }
                    buildRecentActivity()
                }
            }
        }
        // Observe schedules
        viewModelScope.launch {
            repository.observeSchedules().collect { state ->
                if (state is UiState.Success) {
                    scheduleEntries = state.data
                    buildRecentActivity()
                }
            }
        }
    }

    private fun buildRecentActivity() {
        if (lecturerMap.isEmpty() && courseMap.isEmpty() && classroomMap.isEmpty()) return
        if (scheduleEntries.isEmpty()) {
            _recentActivityState.value = UiState.Success(emptyList())
            return
        }

        val dayNames = mapOf(
            1 to "Monday", 2 to "Tuesday", 3 to "Wednesday",
            4 to "Thursday", 5 to "Friday", 6 to "Saturday", 7 to "Sunday"
        )

        // Take last 5 entries (by order from Firestore — newest documents are typically last)
        val recent = scheduleEntries.takeLast(5).reversed().map { entry ->
            RecentActivityItem(
                courseCode = courseMap[entry.courseId] ?: "Course #${entry.courseId}",
                lecturerName = lecturerMap[entry.lecturerId] ?: "Lecturer #${entry.lecturerId}",
                classroomName = classroomMap[entry.classroomId] ?: "Room #${entry.classroomId}",
                dayLabel = dayNames[entry.dayOfWeek] ?: "Day ${entry.dayOfWeek}",
                startTime = entry.startTime
            )
        }

        _recentActivityState.value = UiState.Success(recent)
    }
}