package com.example.unischedule.data.firestore.flat

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

@IgnoreExtraProperties
data class User(
    @get:PropertyName("uid")
    @set:PropertyName("uid")
    var uid: String = "",

    @get:PropertyName("email")
    @set:PropertyName("email")
    var email: String = "",

    @get:PropertyName("full_name")
    @set:PropertyName("full_name")
    var full_name: String = "",

    @get:PropertyName("role")
    @set:PropertyName("role")
    var role: String = "student",

    @get:PropertyName("status")
    @set:PropertyName("status")
    var status: String = "Available",

    @get:PropertyName("deptId")
    @set:PropertyName("deptId")
    var deptId: Long = 0
)
