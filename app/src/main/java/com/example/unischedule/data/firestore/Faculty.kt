package com.example.unischedule.data.firestore

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Faculty(
    var id: Long = 0,
    var name: String = ""
)