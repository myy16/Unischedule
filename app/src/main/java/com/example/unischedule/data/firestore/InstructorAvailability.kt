package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class InstructorAvailability(
    var id: String = "",
    var instructorId: Long = 0,
    var dayOfWeek: Int = 0,  // 1: Monday, 7: Sunday
    var startTime: String = "",  // "09:00"
    var endTime: String = ""     // "11:00"
)
