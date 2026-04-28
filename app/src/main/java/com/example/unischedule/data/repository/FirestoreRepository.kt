package com.example.unischedule.data.repository

import com.example.unischedule.data.entity.Classroom as RoomClassroom
import com.example.unischedule.data.entity.Course as RoomCourse
import com.example.unischedule.data.entity.Instructor as RoomInstructor
import com.example.unischedule.data.firestore.AdminAccount
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Faculty
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.util.PasswordHasher
import com.example.unischedule.util.UiState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreRepository(private val db: FirebaseFirestore) {

    private inline fun <reified T> observeQuery(query: Query): Flow<UiState<List<T>>> = callbackFlow {
        trySend(UiState.Loading)
        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(UiState.Error(error.message ?: "Firestore Listener Error"))
                return@addSnapshotListener
            }

            val items = snapshot?.toObjects(T::class.java) ?: emptyList()
            trySend(UiState.Success(items))
        }

        awaitClose { registration.remove() }
    }

    private fun timeRangesOverlap(existingStart: String, existingEnd: String, newStart: String, newEnd: String): Boolean {
        return existingStart < newEnd && newStart < existingEnd
    }

    /**
     * Phase 2: CallbackFlow for real-time updates from Cloud Firestore.
     * Follows sustainable coding practices: non-blocking and lifecycle-safe when collected properly.
     */
    fun observeSchedules(): Flow<UiState<List<ScheduleEntry>>> = observeQuery(db.collection("schedules"))

    fun observeCourses(): Flow<UiState<List<Course>>> = observeQuery(db.collection("courses"))

    fun observeLecturers(): Flow<UiState<List<Lecturer>>> = observeQuery(db.collection("lecturers"))

    fun observeClassrooms(): Flow<UiState<List<Classroom>>> = observeQuery(db.collection("classrooms"))

    fun observeFaculties(): Flow<UiState<List<Faculty>>> = observeQuery(db.collection("faculties"))

    fun observeDepartments(): Flow<UiState<List<com.example.unischedule.data.firestore.Department>>> = 
        observeQuery(db.collection("departments"))

    fun observeUnassignedLecturers(): Flow<UiState<List<Lecturer>>> =
        observeQuery(
            db.collection("lecturers")
                .whereEqualTo("departmentId", 0L)
        )

    fun observeUnassignedCourses(): Flow<UiState<List<Course>>> =
        observeQuery(
            db.collection("courses")
                .whereEqualTo("departmentId", 0L)
        )

    fun observeAvailableClassrooms(): Flow<UiState<List<Classroom>>> =
        observeQuery(
            db.collection("classrooms")
                .whereEqualTo("isAvailable", true)
        )

    /**
     * Task 5: Real-time listener for lecturer's schedule.
     * Filters ScheduleEntry collection where lecturerId matches the logged-in lecturer.
     * Used by CalendarFragment to display weekly grid.
     */
    fun observeLecturerSchedule(lecturerId: Long): Flow<UiState<List<ScheduleEntry>>> =
        observeQuery(
            db.collection("schedules")
                .whereEqualTo("lecturerId", lecturerId)
        )

    suspend fun authenticateUser(username: String, passwordHash: String): AuthenticatedUser? = withContext(Dispatchers.IO) {
        authenticateAdmin(username, passwordHash) ?: authenticateLecturer(username, passwordHash)
    }

    private suspend fun authenticateAdmin(username: String, passwordHash: String): AuthenticatedUser? {
        val snapshot = db.collection("admins")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()

        val admin = snapshot.toObjects(AdminAccount::class.java).firstOrNull() ?: return null
        if (admin.passwordHash != passwordHash) return null

        return AuthenticatedUser(
            id = admin.id,
            username = admin.username,
            role = UserSession.Role.ADMIN,
            mustChangePassword = admin.mustChangePassword
        )
    }

    private suspend fun authenticateLecturer(username: String, passwordHash: String): AuthenticatedUser? {
        val snapshot = db.collection("lecturers")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()

        val lecturer = snapshot.toObjects(Lecturer::class.java).firstOrNull() ?: return null
        if (lecturer.passwordHash != passwordHash) return null

        return AuthenticatedUser(
            id = lecturer.id,
            username = lecturer.username,
            role = UserSession.Role.LECTURER,
            mustChangePassword = lecturer.mustChangePassword
        )
    }

    suspend fun seedBaharKulogluLecturerIfMissing() = withContext(Dispatchers.IO) {
        val username = "bahar_kuloglu"
        val snapshot = db.collection("lecturers")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()

        if (!snapshot.isEmpty) return@withContext

        db.collection("lecturers")
            .document("1001")
            .set(
                Lecturer(
                    id = 1001,
                    fullName = "Bahar Kuloğlu",
                    departmentId = 0,
                    username = username,
                    passwordHash = PasswordHasher.sha256("Bahar123!"),
                    role = "Lecturer",
                    mustChangePassword = false
                )
            )
            .await()
    }

    class AssignmentConflict(message: String) : IllegalStateException(message)

    suspend fun assignScheduleAtomic(
        courseId: Long,
        lecturerId: Long,
        classroomId: Long,
        dayOfWeek: Int,
        startTime: String,
        endTime: String
    ) = withContext(Dispatchers.IO) {
        try {
            // Fetch existing schedules before transaction for conflict checking
            val existingSchedulesSnapshot = db.collection("schedules").get().await()
            val existingSchedules = existingSchedulesSnapshot.toObjects(ScheduleEntry::class.java)

            db.runTransaction { transaction ->
                val course = readRequiredCourse(transaction, courseId)
                readRequiredLecturer(transaction, lecturerId)
                readRequiredClassroom(transaction, classroomId)

                existingSchedules.forEach { existing ->
                    if (existing.dayOfWeek != dayOfWeek) return@forEach
                    if (!timeRangesOverlap(existing.startTime, existing.endTime, startTime, endTime)) return@forEach

                    if (existing.lecturerId == lecturerId) {
                        throw AssignmentConflict("Lecturer is already assigned to another course in this slot.")
                    }

                    if (existing.classroomId == classroomId) {
                        throw AssignmentConflict("Classroom is already booked for this slot.")
                    }

                    val sameMandatoryCourseGroup =
                        course.isMandatory &&
                        existing.courseIsMandatory &&
                        existing.courseDepartmentId == course.departmentId &&
                        existing.courseYear == course.year

                    if (sameMandatoryCourseGroup) {
                        throw AssignmentConflict("Mandatory courses for the same year and department cannot overlap.")
                    }
                }

                val scheduleRef = db.collection("schedules").document()
                transaction.set(
                    scheduleRef,
                    ScheduleEntry(
                        id = 0,
                        courseId = courseId,
                        courseDepartmentId = course.departmentId,
                        courseYear = course.year,
                        courseIsMandatory = course.isMandatory,
                        lecturerId = lecturerId,
                        classroomId = classroomId,
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime
                    )
                )
                null
            }.await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw if (e is AssignmentConflict) e else AssignmentConflict(e.message ?: "Critical conflict detected during transaction.")
        }
    }

    private fun readRequiredCourse(transaction: com.google.firebase.firestore.Transaction, courseId: Long): Course {
        val snapshot = transaction.get(db.collection("courses").document(courseId.toString()))
        return snapshot.toObject(Course::class.java) ?: throw AssignmentConflict("Selected course could not be found.")
    }

    private fun readRequiredLecturer(transaction: com.google.firebase.firestore.Transaction, lecturerId: Long): Lecturer {
        val snapshot = transaction.get(db.collection("lecturers").document(lecturerId.toString()))
        return snapshot.toObject(Lecturer::class.java) ?: throw AssignmentConflict("Selected lecturer could not be found.")
    }

    private fun readRequiredClassroom(transaction: com.google.firebase.firestore.Transaction, classroomId: Long): Classroom {
        val snapshot = transaction.get(db.collection("classrooms").document(classroomId.toString()))
        return snapshot.toObject(Classroom::class.java) ?: throw AssignmentConflict("Selected classroom could not be found.")
    }

    suspend fun addLecturer(lecturer: Lecturer) = withContext(Dispatchers.IO) {
        try {
            db.collection("lecturers").document(lecturer.id.toString()).set(lecturer).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun addFaculty(faculty: Faculty) = withContext(Dispatchers.IO) {
        try {
            db.collection("faculties").document(faculty.id.toString()).set(faculty).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun addInstructor(instructor: RoomInstructor) = withContext(Dispatchers.IO) {
        try {
            db.collection("instructors").document(instructor.id.toString()).set(instructor).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // Department CRUD
    suspend fun addDepartment(department: com.example.unischedule.data.firestore.Department) = withContext(Dispatchers.IO) {
        try {
            db.collection("departments").document(department.id.toString()).set(department).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun updateDepartment(department: com.example.unischedule.data.firestore.Department) = withContext(Dispatchers.IO) {
        try {
            db.collection("departments").document(department.id.toString()).set(department).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteDepartment(departmentId: Long) = withContext(Dispatchers.IO) {
        try {
            db.collection("departments").document(departmentId.toString()).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // Course CRUD
    suspend fun addCourse(course: Course) = withContext(Dispatchers.IO) {
        try {
            db.collection("courses").document(course.id.toString()).set(course).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun updateCourse(course: Course) = withContext(Dispatchers.IO) {
        try {
            db.collection("courses").document(course.id.toString()).set(course).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteCourse(courseId: Long) = withContext(Dispatchers.IO) {
        try {
            db.collection("courses").document(courseId.toString()).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // Classroom CRUD
    suspend fun addClassroom(classroom: Classroom) = withContext(Dispatchers.IO) {
        try {
            db.collection("classrooms").document(classroom.id.toString()).set(classroom).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun updateClassroom(classroom: Classroom) = withContext(Dispatchers.IO) {
        try {
            db.collection("classrooms").document(classroom.id.toString()).set(classroom).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteClassroom(classroomId: Long) = withContext(Dispatchers.IO) {
        try {
            db.collection("classrooms").document(classroomId.toString()).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // Lecture CRUD (additional)
    suspend fun updateLecturer(lecturer: Lecturer) = withContext(Dispatchers.IO) {
        try {
            db.collection("lecturers").document(lecturer.id.toString()).set(lecturer).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteLecturer(lecturerId: Long) = withContext(Dispatchers.IO) {
        try {
            db.collection("lecturers").document(lecturerId.toString()).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun updateFaculty(faculty: Faculty) = withContext(Dispatchers.IO) {
        try {
            db.collection("faculties").document(faculty.id.toString()).set(faculty).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteFaculty(facultyId: Long) = withContext(Dispatchers.IO) {
        try {
            db.collection("faculties").document(facultyId.toString()).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    /**
     * Check if a Firestore collection is empty.
     * Returns true if collection doesn't exist or has no documents.
     */
    suspend fun isCollectionEmpty(collectionName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = db.collection(collectionName).limit(1).get().await()
            result.isEmpty
        } catch (e: Exception) {
            true  // Consider collection empty if we can't check (likely doesn't exist)
        }
    }
}
