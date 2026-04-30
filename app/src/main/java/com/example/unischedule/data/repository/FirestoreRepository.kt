package com.example.unischedule.data.repository

import com.example.unischedule.data.entity.Classroom as RoomClassroom
import com.example.unischedule.data.entity.Course as RoomCourse
import com.example.unischedule.data.entity.Instructor as RoomInstructor
import com.example.unischedule.data.firestore.AdminAccount
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Faculty
import com.example.unischedule.data.firestore.InstructorAvailability as FirestoreInstructorAvailability
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.firestore.flat.Classroom as FlatClassroom
import com.example.unischedule.data.firestore.flat.Course as FlatCourse
import com.example.unischedule.data.firestore.flat.ScheduleEntry as FlatScheduleEntry
import com.example.unischedule.data.firestore.flat.User as FlatUser
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.util.PasswordHasher
import com.example.unischedule.util.UiState
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
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

    suspend fun authenticateUser(username: String, rawPassword: String): AuthenticatedUser? = withContext(Dispatchers.IO) {
        authenticateAdmin(username, rawPassword) ?: authenticateLecturer(username, rawPassword)
    }

    private suspend fun authenticateAdmin(username: String, rawPassword: String): AuthenticatedUser? {
        val snapshot = db.collection("admins")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()

        val admin = snapshot.toObjects(AdminAccount::class.java).firstOrNull() ?: return null
        
        val hashed = PasswordHasher.sha256(rawPassword)
        if (admin.passwordHash != hashed && admin.passwordHash != rawPassword) return null
        
        // Migration: If password was plaintext, update it to hash
        if (admin.passwordHash == rawPassword) {
            snapshot.documents.firstOrNull()?.reference?.update("passwordHash", hashed)
        }

        return AuthenticatedUser(
            id = admin.id,
            username = admin.username,
            role = UserSession.Role.ADMIN,
            mustChangePassword = admin.mustChangePassword
        )
    }

    private suspend fun authenticateLecturer(username: String, rawPassword: String): AuthenticatedUser? {
        val snapshot = db.collection("lecturers")
            .whereEqualTo("username", username)
            .limit(1)
            .get()
            .await()

        val lecturer = snapshot.toObjects(Lecturer::class.java).firstOrNull() ?: return null
        
        val hashed = PasswordHasher.sha256(rawPassword)
        if (lecturer.passwordHash != hashed && lecturer.passwordHash != rawPassword) return null
        
        // Migration: If password was plaintext, update it to hash
        if (lecturer.passwordHash == rawPassword) {
            snapshot.documents.firstOrNull()?.reference?.update("passwordHash", hashed)
        }

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

    suspend fun getNextIdForCollection(collectionName: String): Long = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection(collectionName).get().await()
            if (snapshot.isEmpty) return@withContext 1L
            val maxId = snapshot.documents.mapNotNull { it.id.toLongOrNull() }.maxOrNull()
            if (maxId != null) maxId + 1L else 1L
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Fallback to random ID if fetch fails to avoid overwriting
            (1000L..9999L).random()
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
    suspend fun findDepartmentByName(name: String): com.example.unischedule.data.firestore.Department? = withContext(Dispatchers.IO) {
        try {
            val snapshot = db.collection("departments")
                .whereEqualTo("name", name)
                .limit(1)
                .get().await()
            if (snapshot.isEmpty) null
            else snapshot.documents.first().toObject(com.example.unischedule.data.firestore.Department::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

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

    // --- Phase 2: Single document lookups ---

    suspend fun getLecturerById(lecturerId: Long): Lecturer? = withContext(Dispatchers.IO) {
        try {
            db.collection("lecturers").document(lecturerId.toString())
                .get().await()
                .toObject(Lecturer::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun getAdminById(adminId: Long): AdminAccount? = withContext(Dispatchers.IO) {
        try {
            db.collection("admins").document(adminId.toString())
                .get().await()
                .toObject(AdminAccount::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun updateAdminFields(adminId: Long, fields: Map<String, Any>) = withContext(Dispatchers.IO) {
        try {
            db.collection("admins").document(adminId.toString()).update(fields).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun getDepartmentById(departmentId: Long): com.example.unischedule.data.firestore.Department? = withContext(Dispatchers.IO) {
        try {
            db.collection("departments").document(departmentId.toString())
                .get().await()
                .toObject(com.example.unischedule.data.firestore.Department::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun getCourseById(courseId: Long): Course? = withContext(Dispatchers.IO) {
        try {
            db.collection("courses").document(courseId.toString())
                .get().await()
                .toObject(Course::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun getClassroomById(classroomId: Long): Classroom? = withContext(Dispatchers.IO) {
        try {
            db.collection("classrooms").document(classroomId.toString())
                .get().await()
                .toObject(Classroom::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    /**
     * Phase 2: Partial field update for a lecturer document.
     * Used by password change flow to update passwordHash and mustChangePassword.
     */
    suspend fun updateLecturerFields(lecturerId: Long, fields: Map<String, Any>) = withContext(Dispatchers.IO) {
        try {
            db.collection("lecturers").document(lecturerId.toString()).update(fields).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // --- Phase 2: Correct dashboard queries ---

    /**
     * Returns lecturers who have NO entries in schedules collection.
     * This is the correct semantic for "unassigned lecturers".
     */
    fun observeUnassignedLecturersCorrect(): Flow<UiState<List<Lecturer>>> = callbackFlow {
        trySend(UiState.Loading)

        val lecturerReg = db.collection("lecturers").addSnapshotListener { lecturerSnap, lecturerErr ->
            if (lecturerErr != null) {
                trySend(UiState.Error(lecturerErr.message ?: "Firestore error"))
                return@addSnapshotListener
            }

            db.collection("schedules").get()
                .addOnSuccessListener { scheduleSnap ->
                    val assignedLecturerIds = scheduleSnap.documents
                        .mapNotNull { it.getLong("lecturerId") }
                        .toSet()

                    val unassigned = lecturerSnap?.toObjects(Lecturer::class.java)
                        ?.filter { it.id !in assignedLecturerIds }
                        ?: emptyList()

                    trySend(UiState.Success(unassigned))
                }
                .addOnFailureListener { e ->
                    trySend(UiState.Error(e.message ?: "Failed to check schedules"))
                }
        }

        awaitClose { lecturerReg.remove() }
    }

    /**
     * Returns courses that have NO entries in schedules collection.
     * This is the correct semantic for "unassigned courses".
     */
    fun observeUnassignedCoursesCorrect(): Flow<UiState<List<Course>>> = callbackFlow {
        trySend(UiState.Loading)

        val courseReg = db.collection("courses").addSnapshotListener { courseSnap, courseErr ->
            if (courseErr != null) {
                trySend(UiState.Error(courseErr.message ?: "Firestore error"))
                return@addSnapshotListener
            }

            db.collection("schedules").get()
                .addOnSuccessListener { scheduleSnap ->
                    val assignedCourseIds = scheduleSnap.documents
                        .mapNotNull { it.getLong("courseId") }
                        .toSet()

                    val unassigned = courseSnap?.toObjects(Course::class.java)
                        ?.filter { it.id !in assignedCourseIds }
                        ?: emptyList()

                    trySend(UiState.Success(unassigned))
                }
                .addOnFailureListener { e ->
                    trySend(UiState.Error(e.message ?: "Failed to check schedules"))
                }
        }

        awaitClose { courseReg.remove() }
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

    // Instructor Availability Firestore methods
    fun observeInstructorAvailability(instructorId: Long): Flow<UiState<List<FirestoreInstructorAvailability>>> =
        observeQuery<FirestoreInstructorAvailability>(
            db.collection("instructor_availability")
                .whereEqualTo("instructorId", instructorId)
        ).map { state ->
            when (state) {
                is UiState.Success<List<FirestoreInstructorAvailability>> -> UiState.Success(
                    state.data.sortedWith(compareBy({ it.dayOfWeek }, { it.startTime }))
                )
                is UiState.Error -> state
                is UiState.Loading -> state
            }
        }

    suspend fun toggleInstructorAvailability(
        instructorId: Long,
        dayOfWeek: Int,
        startTime: String,
        endTime: String
    ) = withContext(Dispatchers.IO) {
        try {
            val availabilityCollection = db.collection("instructor_availability")
            val snapshot = availabilityCollection
                .whereEqualTo("instructorId", instructorId)
                .get().await()

            val targetDoc = snapshot.documents.firstOrNull {
                it.getLong("dayOfWeek")?.toInt() == dayOfWeek &&
                it.getString("startTime") == startTime
            }

            if (targetDoc == null) {
                // Add new availability slot
                val docId = "${instructorId}_${dayOfWeek}_${startTime}"
                availabilityCollection.document(docId).set(
                    FirestoreInstructorAvailability(
                        id = docId,
                        instructorId = instructorId,
                        dayOfWeek = dayOfWeek,
                        startTime = startTime,
                        endTime = endTime
                    )
                ).await()
            } else {
                // Remove existing availability slot
                availabilityCollection.document(targetDoc.id).delete().await()
            }

            val remainingSlots = availabilityCollection
                .whereEqualTo("instructorId", instructorId)
                .get()
                .await()

            db.collection("availability_status")
                .document(instructorId.toString())
                .set(
                    mapOf(
                        "instructorId" to instructorId,
                        "isAvailable" to !remainingSlots.isEmpty,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                )
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun addInstructorAvailability(availability: FirestoreInstructorAvailability) = withContext(Dispatchers.IO) {
        try {
            val docId = "${availability.instructorId}_${availability.dayOfWeek}_${availability.startTime}"
            db.collection("instructor_availability").document(docId).set(availability).await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteInstructorAvailability(instructorId: Long, dayOfWeek: Int, startTime: String) = withContext(Dispatchers.IO) {
        try {
            val docId = "${instructorId}_${dayOfWeek}_${startTime}"
            db.collection("instructor_availability").document(docId).delete().await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // Option A (Flat) - Users/Profile
    suspend fun fetchUserProfile(userUid: String): FlatUser? = withContext(Dispatchers.IO) {
        try {
            db.collection("users")
                .document(userUid)
                .get()
                .await()
                .toObject(FlatUser::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    suspend fun fetchCurrentUserProfileAfterAuth(): FlatUser? = withContext(Dispatchers.IO) {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext null
            db.collection("users")
                .document(uid)
                .get()
                .await()
                .toObject(FlatUser::class.java)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    // Option A (Flat) - Courses CRUD
    suspend fun addCourseFlat(course: FlatCourse) = withContext(Dispatchers.IO) {
        try {
            db.collection("courses")
                .document(course.courseId.toString())
                .set(course)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun updateCourseFlat(course: FlatCourse) = withContext(Dispatchers.IO) {
        try {
            db.collection("courses")
                .document(course.courseId.toString())
                .set(course)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteCourseFlat(courseId: Long) = withContext(Dispatchers.IO) {
        try {
            db.collection("courses")
                .document(courseId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // Option A (Flat) - Classrooms CRUD
    suspend fun addClassroomFlat(classroom: FlatClassroom) = withContext(Dispatchers.IO) {
        try {
            db.collection("classrooms")
                .document(classroom.classId.toString())
                .set(classroom)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun updateClassroomFlat(classroom: FlatClassroom) = withContext(Dispatchers.IO) {
        try {
            db.collection("classrooms")
                .document(classroom.classId.toString())
                .set(classroom)
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteClassroomFlat(classId: Long) = withContext(Dispatchers.IO) {
        try {
            db.collection("classrooms")
                .document(classId.toString())
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    // Option A (Flat) - Schedules CRUD + realtime callbackFlow
    fun observeSchedulesFlat(): Flow<UiState<List<FlatScheduleEntry>>> =
        observeQuery(db.collection("schedules"))

    fun observeUserStatus(userUid: String): Flow<UiState<String>> = callbackFlow {
        trySend(UiState.Loading)
        val registration = db.collection("users")
            .document(userUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error(error.message ?: "Firestore Listener Error"))
                    return@addSnapshotListener
                }

                val status = snapshot?.getString("status")?.takeIf { it.isNotBlank() } ?: "Available"
                trySend(UiState.Success(status))
            }

        awaitClose { registration.remove() }
    }

    suspend fun addScheduleFlat(schedule: FlatScheduleEntry) = withContext(Dispatchers.IO) {
        try {
            val documentId = if (schedule.scheduleId.isBlank()) db.collection("schedules").document().id else schedule.scheduleId
            db.collection("schedules")
                .document(documentId)
                .set(schedule.copy(scheduleId = documentId))
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun updateScheduleFlat(schedule: FlatScheduleEntry) = withContext(Dispatchers.IO) {
        try {
            val documentId = if (schedule.scheduleId.isBlank()) db.collection("schedules").document().id else schedule.scheduleId
            db.collection("schedules")
                .document(documentId)
                .set(schedule.copy(scheduleId = documentId))
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }

    suspend fun deleteScheduleFlat(scheduleId: String) = withContext(Dispatchers.IO) {
        try {
            db.collection("schedules")
                .document(scheduleId)
                .delete()
                .await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }
}
