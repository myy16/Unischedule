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
        val nextId = repository.getNextIdForCollection("faculties")
        repository.addFaculty(Faculty(id = nextId, name = name))
    }

    fun addDepartment(facultyId: Long, name: String) = viewModelScope.launch {
        val nextId = repository.getNextIdForCollection("departments")
        repository.addDepartment(Department(id = nextId, facultyId = facultyId, name = name))
    }

    fun addCourse(course: Course) = viewModelScope.launch {
        val nextId = if (course.id == 0L) repository.getNextIdForCollection("courses") else course.id
        repository.addCourse(course.copy(id = nextId))
    }

    fun addClassroom(classroom: Classroom) = viewModelScope.launch {
        val nextId = if (classroom.id == 0L) repository.getNextIdForCollection("classrooms") else classroom.id
        repository.addClassroom(classroom.copy(id = nextId))
    }

    fun addLecturer(lecturer: Lecturer) = viewModelScope.launch {
        val nextId = if (lecturer.id == 0L) repository.getNextIdForCollection("lecturers") else lecturer.id
        repository.addLecturer(lecturer.copy(id = nextId))
    }
}