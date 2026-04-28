package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Department
import com.example.unischedule.data.firestore.Faculty
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class FirestoreAdminViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _facultiesState = MutableStateFlow<UiState<List<Faculty>>>(UiState.Loading)
    val facultiesState: StateFlow<UiState<List<Faculty>>> = _facultiesState.asStateFlow()

    private val _departmentsState = MutableStateFlow<UiState<List<Department>>>(UiState.Loading)
    val departmentsState: StateFlow<UiState<List<Department>>> = _departmentsState.asStateFlow()

    private val _coursesState = MutableStateFlow<UiState<List<Course>>>(UiState.Loading)
    val coursesState: StateFlow<UiState<List<Course>>> = _coursesState.asStateFlow()

    private val _lecturersState = MutableStateFlow<UiState<List<Lecturer>>>(UiState.Loading)
    val lecturersState: StateFlow<UiState<List<Lecturer>>> = _lecturersState.asStateFlow()

    private val _classroomsState = MutableStateFlow<UiState<List<Classroom>>>(UiState.Loading)
    val classroomsState: StateFlow<UiState<List<Classroom>>> = _classroomsState.asStateFlow()

    init {
        observeData(_facultiesState) { repository.observeFaculties() }
        observeData(_departmentsState) { repository.observeDepartments() }
        observeData(_coursesState) { repository.observeCourses() }
        observeData(_lecturersState) { repository.observeLecturers() }
        observeData(_classroomsState) { repository.observeClassrooms() }
    }

    private fun <T> observeData(
        state: MutableStateFlow<UiState<List<T>>>,
        flowProvider: () -> kotlinx.coroutines.flow.Flow<UiState<List<T>>>
    ) {
        viewModelScope.launch {
            flowProvider().collect { state.value = it }
        }
    }

    fun addFaculty(name: String) = viewModelScope.launch {
        repository.addFaculty(Faculty(id = randomId(), name = name))
    }

    fun addDepartment(facultyId: Long, name: String) = viewModelScope.launch {
        repository.addDepartment(Department(id = randomId(), facultyId = facultyId, name = name))
    }

    fun addCourse(course: Course) = viewModelScope.launch {
        repository.addCourse(course.copy(id = if (course.id == 0L) randomId() else course.id))
    }

    fun addClassroom(classroom: Classroom) = viewModelScope.launch {
        repository.addClassroom(classroom.copy(id = if (classroom.id == 0L) randomId() else classroom.id))
    }

    private fun randomId(): Long = (1000L..9999L).random()
}