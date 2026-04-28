package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Classroom(
    var id: Long = 0,
    var name: String = "",
    var capacity: Int = 0,
    var isLab: Boolean = false,
    var isAvailable: Boolean = true
)