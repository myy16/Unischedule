package com.example.unischedule.data.dao

import androidx.room.*
import com.example.unischedule.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UniversityDao {

    // --- Conflict Checks ---

    @Query("""
        SELECT * FROM schedules 
        WHERE instructorId = :instructorId 
        AND dayOfWeek = :dayOfWeek 
        AND ((startTime < :endTime AND endTime > :startTime))
    """)
    suspend fun checkInstructorConflict(
        instructorId: Long, 
        dayOfWeek: Int, 
        startTime: String, 
        endTime: String
    ): List<Schedule>

    @Query("""
        SELECT * FROM schedules 
        WHERE classroomId = :classroomId 
        AND dayOfWeek = :dayOfWeek 
        AND ((startTime < :endTime AND endTime > :startTime))
    """)
    suspend fun checkClassroomConflict(
        classroomId: Long, 
        dayOfWeek: Int, 
        startTime: String, 
        endTime: String
    ): List<Schedule>

    @Query("""
        SELECT s.* FROM schedules s
        INNER JOIN courses c ON s.courseId = c.id
        WHERE c.departmentId = :departmentId 
        AND c.semester = :semester 
        AND c.isMandatory = 1
        AND s.dayOfWeek = :dayOfWeek 
        AND ((s.startTime < :endTime AND s.endTime > :startTime))
    """)
    suspend fun checkMandatoryCourseConflict(
        departmentId: Long, 
        semester: Int, 
        dayOfWeek: Int, 
        startTime: String, 
        endTime: String
    ): List<Schedule>

    /**
     * Finds any schedule conflict for a given instructor, classroom, or department's mandatory courses.
     */
    @Query("""
        SELECT s.* FROM schedules s
        LEFT JOIN courses c ON s.courseId = c.id
        WHERE s.dayOfWeek = :dayOfWeek 
        AND ((s.startTime < :endTime AND s.endTime > :startTime))
        AND (
            s.instructorId = :instructorId 
            OR s.classroomId = :classroomId 
            OR (c.departmentId = :departmentId AND c.semester = :semester AND c.isMandatory = 1)
        )
    """)
    suspend fun findConflicts(
        instructorId: Long,
        classroomId: Long,
        departmentId: Long,
        semester: Int,
        dayOfWeek: Int,
        startTime: String,
        endTime: String
    ): List<Schedule>

    @Query("SELECT * FROM instructor_availability WHERE instructorId = :instructorId")
    fun getInstructorAvailability(instructorId: Long): Flow<List<InstructorAvailability>>

    @Query("SELECT * FROM instructor_availability WHERE instructorId = :instructorId AND dayOfWeek = :dayOfWeek AND startTime = :startTime")
    suspend fun getAvailability(instructorId: Long, dayOfWeek: Int, startTime: String): InstructorAvailability?

    @Delete
    suspend fun deleteAvailability(availability: InstructorAvailability)

    // --- CRUD Operations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaculty(faculty: Faculty): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDepartment(department: Department): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstructor(instructor: Instructor): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourse(course: Course): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClassroom(classroom: Classroom): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: Schedule): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAdmin(admin: Admin): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstructorAvailability(availability: InstructorAvailability): Long

    @Update
    suspend fun updateFaculty(faculty: Faculty)
    @Update
    suspend fun updateDepartment(department: Department)
    @Update
    suspend fun updateInstructor(instructor: Instructor)
    @Update
    suspend fun updateCourse(course: Course)
    @Update
    suspend fun updateClassroom(classroom: Classroom)
    @Update
    suspend fun updateSchedule(schedule: Schedule)

    @Delete
    suspend fun deleteFaculty(faculty: Faculty)
    @Delete
    suspend fun deleteDepartment(department: Department)
    @Delete
    suspend fun deleteInstructor(instructor: Instructor)
    @Delete
    suspend fun deleteCourse(course: Course)
    @Delete
    suspend fun deleteClassroom(classroom: Classroom)
    @Delete
    suspend fun deleteSchedule(schedule: Schedule)

    @Query("SELECT * FROM admins WHERE username = :username LIMIT 1")
    suspend fun getAdminByUsername(username: String): Admin?

    @Query("SELECT * FROM instructors WHERE email = :email LIMIT 1")
    suspend fun getInstructorByEmail(email: String): Instructor?

    @Query("SELECT * FROM faculties")
    fun getAllFaculties(): Flow<List<Faculty>>

    @Query("SELECT * FROM departments")
    fun getAllDepartments(): Flow<List<Department>>

    @Query("SELECT * FROM departments WHERE facultyId = :facultyId")
    fun getDepartmentsByFaculty(facultyId: Long): Flow<List<Department>>
    
    @Query("SELECT * FROM instructors")
    fun getAllInstructors(): Flow<List<Instructor>>

    @Query("SELECT * FROM instructors WHERE departmentId = :departmentId")
    fun getInstructorsByDepartment(departmentId: Long): Flow<List<Instructor>>

    @Query("SELECT * FROM courses")
    fun getAllCourses(): Flow<List<Course>>

    @Query("SELECT * FROM classrooms")
    fun getAllClassrooms(): Flow<List<Classroom>>

    @Query("SELECT * FROM classrooms WHERE capacity >= :minCapacity AND isLab = :isLab")
    fun getClassroomsByCapacityAndType(minCapacity: Int, isLab: Boolean): Flow<List<Classroom>>

    @Transaction
    @Query("SELECT * FROM schedules")
    fun getAllSchedulesWithDetails(): Flow<List<ScheduleWithDetails>>

    @Transaction
    @Query("SELECT * FROM schedules WHERE instructorId = :instructorId")
    fun getSchedulesWithDetailsByInstructor(instructorId: Long): Flow<List<ScheduleWithDetails>>

    @Query("SELECT * FROM schedules")
    fun getAllSchedules(): Flow<List<Schedule>>

    @Query("SELECT * FROM schedules WHERE instructorId = :instructorId")
    fun getSchedulesByInstructor(instructorId: Long): Flow<List<Schedule>>
}
