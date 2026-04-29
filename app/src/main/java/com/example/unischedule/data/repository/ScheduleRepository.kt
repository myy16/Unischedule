package com.example.unischedule.data.repository

import com.example.unischedule.data.firestore.flat.ScheduleEntry
import com.example.unischedule.util.UiState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ScheduleRepository(private val db: FirebaseFirestore) {

    class ScheduleConflictException(message: String) : IllegalStateException(message)

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

    fun observeSchedules(): Flow<UiState<List<ScheduleEntry>>> =
        observeQuery(db.collection("schedules"))

    suspend fun createSchedule(entry: ScheduleEntry): Result<ScheduleEntry> = withContext(Dispatchers.IO) {
        try {
            val scheduleId = if (entry.scheduleId.isBlank()) {
                db.collection("schedules").document().id
            } else {
                entry.scheduleId
            }

            db.runTransaction { transaction ->
                // Transaction-safe lock documents ensure atomic conflict checks per slot.
                val slotKey = "${entry.day}_${entry.timeSlot}"
                val lecturerLockRef = db.collection("schedule_locks_lecturer")
                    .document("${slotKey}_${entry.lecturerId}")
                val roomLockRef = db.collection("schedule_locks_room")
                    .document("${slotKey}_${entry.classId}")
                val groupLockRef = db.collection("schedule_locks_group_mandatory")
                    .document("${slotKey}_${entry.groupCode}")

                if (transaction.get(lecturerLockRef).exists()) {
                    throw ScheduleConflictException("Lecturer conflict: this lecturer already has a class at the selected day and time slot.")
                }

                if (transaction.get(roomLockRef).exists()) {
                    throw ScheduleConflictException("Room conflict: this classroom is already booked at the selected day and time slot.")
                }

                if (entry.isMandatory && transaction.get(groupLockRef).exists()) {
                    throw ScheduleConflictException("Group conflict: this mandatory group already has another mandatory course at the selected day and time slot.")
                }

                val document = db.collection("schedules").document(scheduleId)
                val finalEntry = entry.copy(scheduleId = scheduleId)
                transaction.set(document, finalEntry)

                transaction.set(
                    lecturerLockRef,
                    mapOf(
                        "scheduleId" to scheduleId,
                        "day" to entry.day,
                        "timeSlot" to entry.timeSlot,
                        "lecturerId" to entry.lecturerId
                    )
                )

                transaction.set(
                    roomLockRef,
                    mapOf(
                        "scheduleId" to scheduleId,
                        "day" to entry.day,
                        "timeSlot" to entry.timeSlot,
                        "classId" to entry.classId
                    )
                )

                if (entry.isMandatory) {
                    transaction.set(
                        groupLockRef,
                        mapOf(
                            "scheduleId" to scheduleId,
                            "day" to entry.day,
                            "timeSlot" to entry.timeSlot,
                            "groupCode" to entry.groupCode,
                            "isMandatory" to true
                        )
                    )
                }
                null
            }.await()

            Result.success(entry.copy(scheduleId = scheduleId))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(scheduleId: String) = withContext(Dispatchers.IO) {
        try {
            val scheduleRef = db.collection("schedules").document(scheduleId)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(scheduleRef)
                val schedule = snapshot.toObject(ScheduleEntry::class.java)

                transaction.delete(scheduleRef)

                if (schedule != null) {
                    val slotKey = "${schedule.day}_${schedule.timeSlot}"
                    val lecturerLockRef = db.collection("schedule_locks_lecturer")
                        .document("${slotKey}_${schedule.lecturerId}")
                    val roomLockRef = db.collection("schedule_locks_room")
                        .document("${slotKey}_${schedule.classId}")
                    val groupLockRef = db.collection("schedule_locks_group_mandatory")
                        .document("${slotKey}_${schedule.groupCode}")

                    transaction.delete(lecturerLockRef)
                    transaction.delete(roomLockRef)

                    if (schedule.isMandatory) {
                        transaction.delete(groupLockRef)
                    }
                }

                null
            }.await()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            throw e
        }
    }
}
