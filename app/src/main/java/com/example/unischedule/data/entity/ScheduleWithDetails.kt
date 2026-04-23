package com.example.unischedule.data.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ScheduleWithDetails(
    @Embedded val schedule: Schedule,
    @Relation(
        parentColumn = "courseId",
        entityColumn = "id"
    )
    val course: Course,
    @Relation(
        parentColumn = "instructorId",
        entityColumn = "id"
    )
    val instructor: Instructor,
    @Relation(
        parentColumn = "classroomId",
        entityColumn = "id"
    )
    val classroom: Classroom
)
