package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Lecturer(
    var id: Long = 0,
    var fullName: String = "",
    var departmentId: Long = 0,
    var username: String = "",
    var passwordHash: String = "",
    var role: String = "Lecturer",
    var mustChangePassword: Boolean = false
)