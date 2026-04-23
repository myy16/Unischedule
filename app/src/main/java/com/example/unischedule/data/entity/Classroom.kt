package com.example.unischedule.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "classrooms")
data class Classroom(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val capacity: Int,
    val isLab: Boolean
)
