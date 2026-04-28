package com.example.unischedule.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "instructor_availability",
    indices = [Index(value = ["instructorId"])],
    foreignKeys = [
        ForeignKey(
            entity = Instructor::class,
            parentColumns = ["id"],
            childColumns = ["instructorId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class InstructorAvailability(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val instructorId: Long,
    val dayOfWeek: Int, // 1: Monday, 7: Sunday
    val startTime: String, // "09:00"
    val endTime: String    // "11:00"
)
