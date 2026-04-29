package com.example.unischedule.data.firestore.flat

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class Classroom(
    @get:PropertyName("classId")
    @set:PropertyName("classId")
    var classId: Long = 0,

    @get:PropertyName("roomName")
    @set:PropertyName("roomName")
    var roomName: String = "",

    @get:PropertyName("capacity")
    @set:PropertyName("capacity")
    var capacity: Int = 0
)
