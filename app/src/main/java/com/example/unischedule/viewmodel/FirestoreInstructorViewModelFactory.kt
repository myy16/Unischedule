package com.example.unischedule.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.unischedule.data.repository.FirestoreRepository
import com.google.firebase.firestore.FirebaseFirestore

class FirestoreInstructorViewModelFactory(private val firestoreRepository: FirestoreRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FirestoreInstructorViewModel::class.java)) {
            return FirestoreInstructorViewModel(firestoreRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }

    companion object {
        fun create(db: FirebaseFirestore): FirestoreInstructorViewModelFactory {
            val repository = FirestoreRepository(db)
            return FirestoreInstructorViewModelFactory(repository)
        }
    }
}
