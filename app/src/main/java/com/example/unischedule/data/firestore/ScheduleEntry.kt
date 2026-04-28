package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class ScheduleEntry(
    var id: Long = 0,
    var courseId: Long = 0,
    var courseDepartmentId: Long = 0,
    var courseYear: Int = 0,
    var courseIsMandatory: Boolean = false,
    var lecturerId: Long = 0,
    var classroomId: Long = 0,
    var dayOfWeek: Int = 0,
    var startTime: String = "",
    var endTime: String = ""
)