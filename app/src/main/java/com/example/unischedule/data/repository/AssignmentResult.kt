package com.example.unischedule.data.repository

sealed class AssignmentResult {
    data object Success : AssignmentResult()
    data class InstructorBusy(val details: String) : AssignmentResult()
    data class RoomOccupied(val details: String) : AssignmentResult()
    data class StudentGroupConflict(val details: String) : AssignmentResult()
    data object InstructorPreferenceConflict : AssignmentResult()
    data class Error(val message: String) : AssignmentResult()
}
