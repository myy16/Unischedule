package com.example.unischedule.data.firestore.flat

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class ScheduleEntry(
    @get:PropertyName("scheduleId")
    @set:PropertyName("scheduleId")
    var scheduleId: String = "",

    @get:PropertyName("courseId")
    @set:PropertyName("courseId")
    var courseId: Long = 0,

    @get:PropertyName("lecturerId")
    @set:PropertyName("lecturerId")
    var lecturerId: Long = 0,

    @get:PropertyName("classId")
    @set:PropertyName("classId")
    var classId: Long = 0,

    @get:PropertyName("day")
    @set:PropertyName("day")
    var day: Int = 0,

    @get:PropertyName("timeSlot")
    @set:PropertyName("timeSlot")
    var timeSlot: String = "",

    @get:PropertyName("groupCode")
    @set:PropertyName("groupCode")
    var groupCode: String = "",

    @get:PropertyName("isMandatory")
    @set:PropertyName("isMandatory")
    var isMandatory: Boolean = false
)
