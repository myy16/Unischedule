package com.example.unischedule.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = Department::class,
            parentColumns = ["id"],
            childColumns = ["departmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val departmentId: Long,
    val code: String,
    val name: String,
    val year: Int,
    val semester: Int,
    val isMandatory: Boolean
)
