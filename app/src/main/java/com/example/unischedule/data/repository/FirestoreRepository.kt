package com.example.unischedule.data.repository

import com.example.unischedule.data.entity.Classroom as RoomClassroom
import com.example.unischedule.data.entity.Course as RoomCourse
import com.example.unischedule.data.entity.Instructor as RoomInstructor
import com.example.unischedule.data.entity.Schedule as RoomSchedule
import com.example.unischedule.data.firestore.AdminAccount
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.session.UserSession
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
    fun observeSchedules(): Flow<UiState<List<RoomSchedule>>> = observeQuery(db.collection("schedules"))

    fun observeCourses(): Flow<UiState<List<Course>>> = observeQuery(db.collection("courses"))

    fun observeLecturers(): Flow<UiState<List<Lecturer>>> = observeQuery(db.collection("lecturers"))

    fun observeClassrooms(): Flow<UiState<List<Classroom>>> = observeQuery(db.collection("classrooms"))

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
            db.runTransaction { transaction ->
                val course = readRequiredCourse(transaction, courseId)
                readRequiredLecturer(transaction, lecturerId)
                readRequiredClassroom(transaction, classroomId)

                val scheduleSnapshot = transaction.get(db.collection("schedules")).documents
                scheduleSnapshot.forEach { document ->
                    val existing = document.toObject(ScheduleEntry::class.java) ?: return@forEach
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

    suspend fun addLecturer(lecturer: Lecturer) = withContext(Dispatchers.IO) {
        try {
            db.collection("lecturers").document(lecturer.id.toString()).set(lecturer).await()
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
}
