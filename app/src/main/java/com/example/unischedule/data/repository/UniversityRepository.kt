package com.example.unischedule.data.repository

import androidx.room.Transaction
import com.example.unischedule.data.dao.UniversityDao
import com.example.unischedule.data.entity.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class UniversityRepository(private val dao: UniversityDao) {

    // --- Flows for real-time updates ---
    fun getAllSchedulesWithDetailsFlow(): Flow<List<ScheduleWithDetails>> = 
        dao.getAllSchedulesWithDetails()

    fun getSchedulesWithDetailsByInstructorFlow(instructorId: Long): Flow<List<ScheduleWithDetails>> = 
        dao.getSchedulesWithDetailsByInstructor(instructorId)

    fun getAllCoursesFlow(): Flow<List<Course>> = dao.getAllCourses()
    fun getAllInstructorsFlow(): Flow<List<Instructor>> = dao.getAllInstructors()
    fun getAllDepartmentsFlow(): Flow<List<Department>> = dao.getAllDepartments()
    fun getAllFacultiesFlow(): Flow<List<Faculty>> = dao.getAllFaculties()
    fun getAllClassroomsFlow(): Flow<List<Classroom>> = dao.getAllClassrooms()
    fun getAllSchedulesFlow(): Flow<List<Schedule>> = dao.getAllSchedules()

    fun getDepartmentsByFacultyFlow(facultyId: Long): Flow<List<Department>> = dao.getDepartmentsByFaculty(facultyId)
    fun getInstructorsByDepartmentFlow(departmentId: Long): Flow<List<Instructor>> = dao.getInstructorsByDepartment(departmentId)
    fun getClassroomsByCapacityAndTypeFlow(minCapacity: Int, isLab: Boolean): Flow<List<Classroom>> =
        dao.getClassroomsByCapacityAndType(minCapacity, isLab)

    fun getInstructorAvailabilityFlow(instructorId: Long): Flow<List<InstructorAvailability>> = 
        dao.getInstructorAvailability(instructorId)

    /**
     * Atomic Operation: Ensures all constraint checks and the write operation happen as a single unit.
     */
    @Transaction
    suspend fun addScheduleAtomically(
        schedule: Schedule, 
        departmentId: Long, 
        semester: Int, 
        force: Boolean = false
    ): AssignmentResult = withContext(Dispatchers.IO) {
        try {
            val instructorConflicts = dao.checkInstructorConflict(
                schedule.instructorId, schedule.dayOfWeek, schedule.startTime, schedule.endTime
            )
            if (instructorConflicts.isNotEmpty()) {
                return@withContext AssignmentResult.InstructorBusy("Instructor is already teaching another course at this time.")
            }

            val classroomConflicts = dao.checkClassroomConflict(
                schedule.classroomId, schedule.dayOfWeek, schedule.startTime, schedule.endTime
            )
            if (classroomConflicts.isNotEmpty()) {
                return@withContext AssignmentResult.RoomOccupied("Classroom is already occupied by another class.")
            }

            val mandatoryConflicts = dao.checkMandatoryCourseConflict(
                departmentId, semester, schedule.dayOfWeek, schedule.startTime, schedule.endTime
            )
            if (mandatoryConflicts.isNotEmpty()) {
                return@withContext AssignmentResult.StudentGroupConflict("Students of this year/semester already have a mandatory course at this time.")
            }

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
            AssignmentResult.Error(e.message ?: "Failed to assign schedule")
        }
    }

    // --- Non-Blocking Writes ---

    suspend fun insertFaculty(faculty: Faculty) = withContext(Dispatchers.IO) { dao.insertFaculty(faculty) }
    suspend fun insertDepartment(department: Department) = withContext(Dispatchers.IO) { dao.insertDepartment(department) }
    suspend fun insertInstructor(instructor: Instructor) = withContext(Dispatchers.IO) { dao.insertInstructor(instructor) }
    suspend fun insertCourse(course: Course) = withContext(Dispatchers.IO) { dao.insertCourse(course) }
    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) { dao.updateCourse(course) }
    suspend fun insertClassroom(classroom: Classroom) = withContext(Dispatchers.IO) { dao.insertClassroom(classroom) }
    suspend fun deleteSchedule(schedule: Schedule) = withContext(Dispatchers.IO) { dao.deleteSchedule(schedule) }

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

    suspend fun getAdminByUsername(username: String) = withContext(Dispatchers.IO) { dao.getAdminByUsername(username) }
    suspend fun getInstructorByEmail(email: String) = withContext(Dispatchers.IO) { dao.getInstructorByEmail(email) }
}
