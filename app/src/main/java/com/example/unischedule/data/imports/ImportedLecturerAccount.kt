package com.example.unischedule.data.imports

import com.example.unischedule.data.firestore.Lecturer

data class ImportedLecturerAccount(
    val lecturer: Lecturer,
    val initialPassword: String
)