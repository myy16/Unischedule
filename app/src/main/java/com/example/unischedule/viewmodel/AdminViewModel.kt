package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.unischedule.data.entity.*
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.util.UiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AdminViewModel(private val repository: UniversityRepository) : ViewModel() {

    private val _facultiesState = MutableStateFlow<UiState<List<Faculty>>>(UiState.Idle)
    val facultiesState: StateFlow<UiState<List<Faculty>>> = _facultiesState.asStateFlow()

    private val _departmentsState = MutableStateFlow<UiState<List<Department>>>(UiState.Idle)
    val departmentsState: StateFlow<UiState<List<Department>>> = _departmentsState.asStateFlow()

    private val _coursesState = MutableStateFlow<UiState<List<Course>>>(UiState.Idle)
    val coursesState: StateFlow<UiState<List<Course>>> = _coursesState.asStateFlow()

    private val _instructorsState = MutableStateFlow<UiState<List<Instructor>>>(UiState.Idle)
    val instructorsState: StateFlow<UiState<List<Instructor>>> = _instructorsState.asStateFlow()

    private val _classroomsState = MutableStateFlow<UiState<List<Classroom>>>(UiState.Idle)
    val classroomsState: StateFlow<UiState<List<Classroom>>> = _classroomsState.asStateFlow()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        observeData(_facultiesState) { repository.getAllFaculties() }
        observeData(_departmentsState) { repository.getAllDepartments() }
        observeData(_coursesState) { repository.getAllCourses() }
        observeData(_instructorsState) { repository.getAllInstructors() }
        observeData(_classroomsState) { repository.getAllClassrooms() }
    }

    private fun <T> observeData(state: MutableStateFlow<UiState<T>>, flowProvider: () -> Flow<T>) {
        state.value = UiState.Loading
        viewModelScope.launch {
            flowProvider()
                .catch { e ->
                    if (e is CancellationException) throw e
                    state.value = UiState.Error(e.message ?: "Unknown Error")
                }
                .collect { list ->
                    state.value = UiState.Success(list)
                }
        }
    }

    fun addFaculty(name: String) = safeLaunch { repository.insertFaculty(Faculty(name = name)) }
    fun addDepartment(facultyId: Long, name: String) = safeLaunch { repository.insertDepartment(Department(facultyId = facultyId, name = name)) }
    fun addInstructor(instructor: Instructor) = safeLaunch { repository.insertInstructor(instructor) }
    fun addCourse(course: Course) = safeLaunch { repository.insertCourse(course) }
    fun updateCourse(course: Course) = safeLaunch { repository.updateCourse(course) }
    fun addClassroom(classroom: Classroom) = safeLaunch { repository.insertClassroom(classroom) }

    private fun safeLaunch(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                // Global error channel could be implemented here
            }
        }
    }
}
