package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.unischedule.data.repository.FirestoreRepository
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreScheduleListViewModelFactory(private val firestoreRepository: FirestoreRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirestoreScheduleListViewModel::class.java)) {
            return FirestoreScheduleListViewModel(firestoreRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    companion object {
        fun create(db: FirebaseFirestore): FirestoreScheduleListViewModelFactory {
            val repository = FirestoreRepository(db)
            return FirestoreScheduleListViewModelFactory(repository)
        }
    }
}
