package com.example.unischedule.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "instructors",
    foreignKeys = [
        ForeignKey(
            entity = Department::class,
            parentColumns = ["id"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Instructor(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val departmentId: Long,
    val name: String,
    val email: String,
    val title: String,
    val passwordHash: String
)
