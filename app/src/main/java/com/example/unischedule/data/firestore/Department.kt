package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Department(
    var id: Long = 0,
    var facultyId: Long = 0,
    var name: String = ""
)