package com.example.unischedule.data.firestore.flat

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class Course(
    @get:PropertyName("courseId")
    @set:PropertyName("courseId")
    var courseId: Long = 0,

    @get:PropertyName("name")
    @set:PropertyName("name")
    var name: String = "",

    @get:PropertyName("credits")
    @set:PropertyName("credits")
    var credits: Int = 0,

    @get:PropertyName("isMandatory")
    @set:PropertyName("isMandatory")
    var isMandatory: Boolean = false,

    @get:PropertyName("deptId")
    @set:PropertyName("deptId")
    var deptId: Long = 0
)
