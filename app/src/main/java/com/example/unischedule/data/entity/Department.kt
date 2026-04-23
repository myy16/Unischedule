package com.example.unischedule.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "departments",
    foreignKeys = [
        ForeignKey(
            entity = Faculty::class,
            parentColumns = ["id"],
            childColumns = ["facultyId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Department(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val facultyId: Long,
    val name: String
)
