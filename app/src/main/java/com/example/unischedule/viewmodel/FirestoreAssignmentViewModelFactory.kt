package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.unischedule.data.repository.FirestoreRepository

class FirestoreAssignmentViewModelFactory(private val repository: FirestoreRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirestoreAssignmentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FirestoreAssignmentViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
