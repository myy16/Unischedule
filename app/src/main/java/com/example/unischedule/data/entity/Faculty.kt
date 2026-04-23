package com.example.unischedule.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "faculties")
data class Faculty(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String
)
