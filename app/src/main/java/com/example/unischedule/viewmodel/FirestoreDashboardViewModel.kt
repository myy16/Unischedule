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

class FirestoreDashboardViewModel(private val repository: FirestoreRepository) : ViewModel() {

    private val _unassignedLecturersState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val unassignedLecturersState: StateFlow<UiState<Int>> = _unassignedLecturersState.asStateFlow()

    private val _unassignedCoursesState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val unassignedCoursesState: StateFlow<UiState<Int>> = _unassignedCoursesState.asStateFlow()

    private val _availableClassroomsState = MutableStateFlow<UiState<Int>>(UiState.Loading)
    val availableClassroomsState: StateFlow<UiState<Int>> = _availableClassroomsState.asStateFlow()

    init {
        observeCount(repository.observeUnassignedLecturersCorrect(), _unassignedLecturersState)
        observeCount(repository.observeUnassignedCoursesCorrect(), _unassignedCoursesState)
        observeCount(repository.observeAvailableClassrooms(), _availableClassroomsState)
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
}