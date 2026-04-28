package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Course(
    var id: Long = 0,
    var departmentId: Long = 0,
    var code: String = "",
    var name: String = "",
    var year: Int = 0,
    var semester: Int = 0,
    var isMandatory: Boolean = false
)