package com.example.unischedule.data.repository

import androidx.room.Transaction
import com.example.unischedule.data.dao.UniversityDao
import com.example.unischedule.data.entity.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UniversityRepository(private val dao: UniversityDao) {

    // --- Single Source of Truth for Schedules ---
    fun getAllSchedulesWithDetailsFlow(): Flow<List<ScheduleWithDetails>> = 
        dao.getAllSchedulesWithDetails()

    fun getSchedulesWithDetailsByInstructorFlow(instructorId: Long): Flow<List<ScheduleWithDetails>> = 
        dao.getSchedulesWithDetailsByInstructor(instructorId)

    /**
     * Atomic Operation (Task 5): 
     * Ensures all constraint checks and the write operation happen as a single unit.
     */
    @Transaction
    suspend fun addScheduleAtomically(
        schedule: Schedule, 
        departmentId: Long, 
        semester: Int, 
        force: Boolean = false
    ): AssignmentResult = withContext(Dispatchers.IO) {
        try {
            // 1. Instructor Conflict (Hard)
            val instructorConflicts = dao.checkInstructorConflict(
                schedule.instructorId, schedule.dayOfWeek, schedule.startTime, schedule.endTime
            )
            if (instructorConflicts.isNotEmpty()) {
                return@withContext AssignmentResult.InstructorBusy("Instructor is already teaching another course at this time.")
            }

            // 2. Classroom Conflict (Hard)
            val classroomConflicts = dao.checkClassroomConflict(
                schedule.classroomId, schedule.dayOfWeek, schedule.startTime, schedule.endTime
            )
            if (classroomConflicts.isNotEmpty()) {
                return@withContext AssignmentResult.RoomOccupied("Classroom is already occupied by another class.")
            }

            // 3. Mandatory Course Conflict (Hard)
            val mandatoryConflicts = dao.checkMandatoryCourseConflict(
                departmentId, semester, schedule.dayOfWeek, schedule.startTime, schedule.endTime
            )
            if (mandatoryConflicts.isNotEmpty()) {
                return@withContext AssignmentResult.StudentGroupConflict("Students of this year/semester already have a mandatory course at this time.")
            }

            // 4. Availability Check (Soft)
            if (!force) {
                val availability = dao.getAvailability(schedule.instructorId, schedule.dayOfWeek, schedule.startTime)
                if (availability == null) {
                    return@withContext AssignmentResult.InstructorPreferenceConflict
                }
            }

            dao.insertSchedule(schedule)
            AssignmentResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw Exception("Failed to assign schedule: ${e.message}")
        }
    }

    // --- Asynchronous & Non-Blocking CRUD ---

    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) {
        try {
            dao.updateCourse(course)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw Exception("Database Update Error: ${e.message}")
        }
    }

    suspend fun insertInstructor(instructor: Instructor) = withContext(Dispatchers.IO) {
        dao.insertInstructor(instructor)
    }

    suspend fun insertCourse(course: Course) = withContext(Dispatchers.IO) {
        dao.insertCourse(course)
    }

    suspend fun toggleAvailability(instructorId: Long, dayOfWeek: Int, startTime: String) = withContext(Dispatchers.IO) {
        val existing = dao.getAvailability(instructorId, dayOfWeek, startTime)
        if (existing != null) {
            dao.deleteAvailability(existing)
        } else {
            dao.insertInstructorAvailability(InstructorAvailability(
                instructorId = instructorId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = ""
            ))
        }
    }

    // Flow-based reads for automatic UI updates
    fun getAllCourses(): Flow<List<Course>> = dao.getAllCourses()
    fun getAllInstructors(): Flow<List<Instructor>> = dao.getAllInstructors()
    fun getAllDepartments(): Flow<List<Department>> = dao.getAllDepartments()
    fun getAllFaculties(): Flow<List<Faculty>> = dao.getAllFaculties()
    fun getAllClassrooms(): Flow<List<Classroom>> = dao.getAllClassrooms()
    fun getInstructorAvailability(instructorId: Long): Flow<List<InstructorAvailability>> = 
        dao.getInstructorAvailability(instructorId)
    
    suspend fun getAdminByUsername(username: String) = dao.getAdminByUsername(username)
    suspend fun getInstructorByEmail(email: String) = dao.getInstructorByEmail(email)
}
