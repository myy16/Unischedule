package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class AdminAccount(
    var id: Long = 0,
    var username: String = "",
    var passwordHash: String = "",
    var role: String = "Admin",
    var mustChangePassword: Boolean = false
)