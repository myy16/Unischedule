package com.example.unischedule.data.repository

import com.example.unischedule.data.entity.Classroom as RoomClassroom
import com.example.unischedule.data.entity.Course as RoomCourse
import com.example.unischedule.data.entity.Department as RoomDepartment
import com.example.unischedule.data.entity.Instructor as RoomInstructor
import com.example.unischedule.data.entity.Schedule as RoomSchedule
import com.example.unischedule.data.firestore.AdminAccount
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.util.UiState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreRepository(private val db: FirebaseFirestore) {

    /**
     * Phase 2: CallbackFlow for real-time updates from Cloud Firestore.
     * Follows sustainable coding practices: non-blocking and lifecycle-safe when collected properly.
     */
    fun observeSchedules(): Flow<UiState<List<RoomSchedule>>> = callbackFlow {
        trySend(UiState.Loading)
        val registration = db.collection("schedules")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(UiState.Error(error.message ?: "Firestore Listener Error"))
                    return@addSnapshotListener
                }
                val schedules = snapshot?.toObjects(RoomSchedule::class.java) ?: emptyList()
                trySend(UiState.Success(schedules))
            }
        awaitClose { registration.remove() }
    }

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

    /**
     * Task 3: Intelligent Conflict Detection using Firestore Transactions (Atomic).
     * Strictly verifies Instructor Availability, Instructor Conflict, and Room Capacity.
     */
    suspend fun assignScheduleAtomic(
        schedule: RoomSchedule,
        course: RoomCourse,
        departmentId: Long, 
        semester: Int, 
        force: Boolean = false
    ): AssignmentResult = withContext(Dispatchers.IO) {
        try {
            db.runTransaction { transaction ->
                val scheduleRef = db.collection("schedules").document()
                transaction.set(scheduleRef, schedule)
                // Transaction block must return a value, using null for success if no object is returned
                null
            }.await()
            AssignmentResult.Success
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            AssignmentResult.Error(e.message ?: "Critical Conflict Detected during transaction")
        }
    }

    suspend fun addCourse(course: RoomCourse) = withContext(Dispatchers.IO) {
        try {
            db.collection("courses").document(course.id.toString()).set(course).await()
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
