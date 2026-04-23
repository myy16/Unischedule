package com.example.unischedule.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(entity = Course::class, parentColumns = ["id"], childColumns = ["courseId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Instructor::class, parentColumns = ["id"], childColumns = ["instructorId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Classroom::class, parentColumns = ["id"], childColumns = ["classroomId"], onDelete = ForeignKey.CASCADE)
    ]
)
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val instructorId: Long,
    val classroomId: Long,
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String
)
