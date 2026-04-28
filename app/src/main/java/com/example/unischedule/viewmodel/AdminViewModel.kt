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

    private val _facultiesState = MutableStateFlow<UiState<List<Faculty>>>(UiState.Loading)
    val facultiesState: StateFlow<UiState<List<Faculty>>> = _facultiesState.asStateFlow()

    private val _departmentsState = MutableStateFlow<UiState<List<Department>>>(UiState.Loading)
    val departmentsState: StateFlow<UiState<List<Department>>> = _departmentsState.asStateFlow()

    private val _coursesState = MutableStateFlow<UiState<List<Course>>>(UiState.Loading)
    val coursesState: StateFlow<UiState<List<Course>>> = _coursesState.asStateFlow()

    private val _instructorsState = MutableStateFlow<UiState<List<Instructor>>>(UiState.Loading)
    val instructorsState: StateFlow<UiState<List<Instructor>>> = _instructorsState.asStateFlow()

    private val _classroomsState = MutableStateFlow<UiState<List<Classroom>>>(UiState.Loading)
    val classroomsState: StateFlow<UiState<List<Classroom>>> = _classroomsState.asStateFlow()

    init {
        loadAllData()
    }

    private fun loadAllData() {
        observeData(_facultiesState) { repository.getAllFacultiesFlow() }
        observeData(_departmentsState) { repository.getAllDepartmentsFlow() }
        observeData(_coursesState) { repository.getAllCoursesFlow() }
        observeData(_instructorsState) { repository.getAllInstructorsFlow() }
        observeData(_classroomsState) { repository.getAllClassroomsFlow() }
    }

    private fun <T> observeData(state: MutableStateFlow<UiState<List<T>>>, flowProvider: () -> Flow<List<T>>) {
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
            }
        }
    }
}
