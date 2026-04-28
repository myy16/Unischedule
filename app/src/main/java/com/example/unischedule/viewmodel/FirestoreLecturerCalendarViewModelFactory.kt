package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.unischedule.data.repository.FirestoreRepository

/**
 * Factory for FirestoreLecturerCalendarViewModel.
 * Passes lecturerId from UserSession to the ViewModel for schedule filtering.
 */
class FirestoreLecturerCalendarViewModelFactory(
    private val repository: FirestoreRepository,
    private val lecturerId: Long
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return FirestoreLecturerCalendarViewModel(repository, lecturerId) as T
    }
}
