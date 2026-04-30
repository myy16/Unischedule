package com.example.unischedule.ui

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.R
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Department
import com.example.unischedule.data.firestore.Faculty
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.databinding.FragmentSchedulerBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreScheduleListViewModel
import com.example.unischedule.viewmodel.FirestoreScheduleListViewModelFactory
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class SchedulerFragment : Fragment() {

    private var _binding: FragmentSchedulerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreScheduleListViewModel by viewModels {
        FirestoreScheduleListViewModelFactory.create(FirebaseFirestore.getInstance())
    }

    private lateinit var scheduleAdapter: ScheduleAdapter

    // Raw data
    private var allSchedules: List<ScheduleEntry> = emptyList()
    private var allCourses: List<Course> = emptyList()
    private var allLecturers: List<Lecturer> = emptyList()
    private var allClassrooms: List<Classroom> = emptyList()
    private var allDepartments: List<Department> = emptyList()

    // Filter state
    private var filterDepartmentId: Long? = null
    private var filterDepartmentName: String? = null
    private var filterLecturerId: Long? = null
    private var filterLecturerName: String? = null
    private var filterDay: Int? = null
    private var filterDayName: String? = null
    private var filterTimeSlot: String? = null

    private var pendingExportAction: (() -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingExportAction?.invoke()
        else Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
    }

    // Lookup maps
    private val courseMap get() = allCourses.associate { it.id to it }
    private val lecturerMap get() = allLecturers.associate { it.id to it }
    private val classroomMap get() = allClassrooms.associate { it.id to it }
    private val deptMap get() = allDepartments.associate { it.id to it.name }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSchedulerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleAdapter = ScheduleAdapter { scheduleEntry ->
            showScheduleOptions(scheduleEntry)
        }

        binding.schedulerRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scheduleAdapter
        }

        binding.fabAddSchedule.setOnClickListener {
            AddScheduleBottomSheet().show(childFragmentManager, "AddSchedule")
        }

        // 2D: Export
        binding.btnExportShare.setOnClickListener {
            runWithStoragePermission { exportSchedule() }
        }

        // 2E: Download sample
        binding.btnDownloadSample.setOnClickListener {
            runWithStoragePermission { downloadSample() }
        }

        observeData()
        buildFilterChips()
    }

    // ─── 2B: Edit/Delete ────────────────────────────────────────────────
    private fun showScheduleOptions(item: ScheduleEntry) {
        val courseName = courseMap[item.courseId]?.let { "${it.code}: ${it.name}" } ?: "Schedule #${item.id}"
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle(courseName)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditScheduleDialog(item)
                    1 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun confirmDelete(item: ScheduleEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete this schedule entry? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSchedule(item.id.toString())
                Snackbar.make(binding.root, "Schedule deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditScheduleDialog(entry: ScheduleEntry) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(
            com.example.unischedule.R.layout.bottom_sheet_add_schedule, null
        )

        // Spinners
        val courseSpinner = dialogView.findViewById<Spinner>(com.example.unischedule.R.id.courseSpinner)
        val instructorSpinner = dialogView.findViewById<Spinner>(com.example.unischedule.R.id.instructorSpinner)
        val classroomSpinner = dialogView.findViewById<Spinner>(com.example.unischedule.R.id.classroomSpinner)
        val daySpinner = dialogView.findViewById<Spinner>(com.example.unischedule.R.id.daySpinner)
        val btnStartTime = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.example.unischedule.R.id.btnStartTime)
        val btnEndTime = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.example.unischedule.R.id.btnEndTime)
        // Hide the assign button from the bottom sheet layout since dialog has its own buttons
        dialogView.findViewById<View>(com.example.unischedule.R.id.assignButton)?.visibility = View.GONE

        var editStartTime = entry.startTime
        var editEndTime = entry.endTime

        // Course spinner
        data class SpinnerItem(val id: Long, val label: String) { override fun toString() = label }
        val courseItems = allCourses.map { SpinnerItem(it.id, "${it.code} - ${it.name}") }
        courseSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, courseItems).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        courseItems.indexOfFirst { it.id == entry.courseId }.let { if (it >= 0) courseSpinner.setSelection(it) }

        // Lecturer spinner
        val lecturerItems = allLecturers.map { SpinnerItem(it.id, it.fullName.ifBlank { it.username }) }
        instructorSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lecturerItems).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        lecturerItems.indexOfFirst { it.id == entry.lecturerId }.let { if (it >= 0) instructorSpinner.setSelection(it) }

        // Classroom spinner
        val classroomItems = allClassrooms.map { SpinnerItem(it.id, it.name) }
        classroomSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, classroomItems).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        classroomItems.indexOfFirst { it.id == entry.classroomId }.let { if (it >= 0) classroomSpinner.setSelection(it) }

        // Day spinner
        data class DayItem(val id: Int, val name: String) { override fun toString() = name }
        val days = listOf(DayItem(1,"Monday"),DayItem(2,"Tuesday"),DayItem(3,"Wednesday"),DayItem(4,"Thursday"),DayItem(5,"Friday"),DayItem(6,"Saturday"),DayItem(7,"Sunday"))
        daySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, days).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        days.indexOfFirst { it.id == entry.dayOfWeek }.let { if (it >= 0) daySpinner.setSelection(it) }

        // Time buttons
        btnStartTime.text = "Start: ${entry.startTime}"
        btnEndTime.text = "End: ${entry.endTime}"
        btnStartTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h, m ->
                editStartTime = String.format("%02d:%02d", h, m)
                btnStartTime.text = "Start: $editStartTime"
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        btnEndTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h, m ->
                editEndTime = String.format("%02d:%02d", h, m)
                btnEndTime.text = "End: $editEndTime"
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Schedule")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selectedCourse = (courseSpinner.selectedItem as? SpinnerItem)?.id ?: entry.courseId
                val selectedLecturer = (instructorSpinner.selectedItem as? SpinnerItem)?.id ?: entry.lecturerId
                val selectedClassroom = (classroomSpinner.selectedItem as? SpinnerItem)?.id ?: entry.classroomId
                val selectedDay = (daySpinner.selectedItem as? DayItem)?.id ?: entry.dayOfWeek

                val course = courseMap[selectedCourse]
                val updatedEntry = entry.copy(
                    courseId = selectedCourse,
                    lecturerId = selectedLecturer,
                    classroomId = selectedClassroom,
                    dayOfWeek = selectedDay,
                    startTime = editStartTime,
                    endTime = editEndTime,
                    courseDepartmentId = course?.departmentId ?: entry.courseDepartmentId,
                    courseYear = course?.year ?: entry.courseYear,
                    courseIsMandatory = course?.isMandatory ?: entry.courseIsMandatory
                )

                // 2A: Conflict check (exclude current entry)
                val conflict = viewModel.checkConflicts(updatedEntry, allSchedules, excludeId = entry.id)
                if (conflict != null) {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Conflict Detected")
                        .setMessage(conflict)
                        .setPositiveButton("OK", null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show()
                } else {
                    viewModel.updateSchedule(updatedEntry)
                    Snackbar.make(binding.root, "Schedule updated", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── 2C: Filter chips ───────────────────────────────────────────────
    private fun buildFilterChips() {
        val chipGroup = binding.filterChipGroup
        chipGroup.removeAllViews()

        // Department
        if (filterDepartmentName != null) {
            chipGroup.addView(createActiveChip("Dept: $filterDepartmentName") {
                filterDepartmentId = null; filterDepartmentName = null
                filterLecturerId = null; filterLecturerName = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Department ▾") { showDeptPicker() })
        }

        // Instructor (only if department selected)
        if (filterDepartmentId != null) {
            if (filterLecturerName != null) {
                chipGroup.addView(createActiveChip("Instructor: $filterLecturerName") {
                    filterLecturerId = null; filterLecturerName = null
                    rebuildAndApply()
                })
            } else {
                chipGroup.addView(createChip("Instructor ▾") { showInstructorPicker() })
            }
        }

        // Day
        if (filterDayName != null) {
            chipGroup.addView(createActiveChip("Day: $filterDayName") {
                filterDay = null; filterDayName = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Day ▾") { showDayPicker() })
        }

        // Time slot
        if (filterTimeSlot != null) {
            chipGroup.addView(createActiveChip("Time: $filterTimeSlot") {
                filterTimeSlot = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Time ▾") { showTimePicker() })
        }

        // Clear
        if (filterDepartmentId != null || filterLecturerId != null || filterDay != null || filterTimeSlot != null) {
            chipGroup.addView(createChip("✕ Clear") {
                filterDepartmentId = null; filterDepartmentName = null
                filterLecturerId = null; filterLecturerName = null
                filterDay = null; filterDayName = null
                filterTimeSlot = null
                rebuildAndApply()
            })
        }
    }

    private fun createChip(label: String, onClick: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            text = label; isCheckable = false; isClickable = true
            setChipBackgroundColorResource(R.color.white)
            setTextColor(resources.getColor(R.color.navy_blue, null))
            chipStrokeWidth = 2f; setChipStrokeColorResource(R.color.navy_blue)
            setOnClickListener { onClick() }
        }
    }

    private fun createActiveChip(label: String, onClick: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            text = label; isCheckable = false; isClickable = true
            setChipBackgroundColorResource(R.color.navy_blue)
            setTextColor(resources.getColor(R.color.white, null))
            isCloseIconVisible = true; setCloseIconTintResource(R.color.white)
            setOnCloseIconClickListener { onClick() }
            setOnClickListener { onClick() }
        }
    }

    private fun showDeptPicker() {
        if (allDepartments.isEmpty()) { Toast.makeText(context, "No departments loaded", Toast.LENGTH_SHORT).show(); return }
        val names = allDepartments.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Department")
            .setItems(names) { _, which ->
                val d = allDepartments[which]
                filterDepartmentId = d.id; filterDepartmentName = d.name
                filterLecturerId = null; filterLecturerName = null
                rebuildAndApply()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showInstructorPicker() {
        val filtered = allLecturers.filter { it.departmentId == filterDepartmentId }
        if (filtered.isEmpty()) { Toast.makeText(context, "No lecturers in this department", Toast.LENGTH_SHORT).show(); return }
        val names = filtered.map { it.fullName.ifBlank { it.username } }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Instructor")
            .setItems(names) { _, which ->
                val l = filtered[which]
                filterLecturerId = l.id; filterLecturerName = l.fullName.ifBlank { l.username }
                rebuildAndApply()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showDayPicker() {
        val days = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Day")
            .setItems(days) { _, which ->
                filterDay = which + 1; filterDayName = days[which]
                rebuildAndApply()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun showTimePicker() {
        val slots = arrayOf("08:00-10:00", "10:00-12:00", "13:00-15:00", "15:00-17:00")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Time Slot")
            .setItems(slots) { _, which ->
                filterTimeSlot = slots[which]
                rebuildAndApply()
            }.setNegativeButton("Cancel", null).show()
    }

    private fun rebuildAndApply() {
        buildFilterChips()
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = allSchedules

        // Department: filter by course's departmentId
        filterDepartmentId?.let { deptId ->
            val courseIdsInDept = allCourses.filter { it.departmentId == deptId }.map { it.id }.toSet()
            filtered = filtered.filter { it.courseId in courseIdsInDept || it.courseDepartmentId == deptId }
        }

        // Instructor
        filterLecturerId?.let { lecId ->
            filtered = filtered.filter { it.lecturerId == lecId }
        }

        // Day
        filterDay?.let { day ->
            filtered = filtered.filter { it.dayOfWeek == day }
        }

        // Time slot
        filterTimeSlot?.let { slot ->
            val parts = slot.split("-")
            if (parts.size == 2) {
                val slotStart = parts[0].trim()
                val slotEnd = parts[1].trim()
                filtered = filtered.filter { entry ->
                    entry.startTime < slotEnd && slotStart < entry.endTime
                }
            }
        }

        if (_binding != null) {
            binding.resultCountText.text = if (filtered.size == allSchedules.size)
                "Showing all ${allSchedules.size} schedules"
            else "Showing ${filtered.size} of ${allSchedules.size} schedules"
        }

        scheduleAdapter.updateItems(filtered)
    }

    // ─── 2D: Export ─────────────────────────────────────────────────────
    private fun exportSchedule() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use currently filtered list
                var filtered = allSchedules
                filterDepartmentId?.let { dId ->
                    filtered = filtered.filter { it.courseDepartmentId == dId || courseMap[it.courseId]?.departmentId == dId }
                }
                filterLecturerId?.let { lId -> filtered = filtered.filter { it.lecturerId == lId } }
                filterDay?.let { d -> filtered = filtered.filter { it.dayOfWeek == d } }
                filterTimeSlot?.let { slot ->
                    val p = slot.split("-"); if (p.size == 2) {
                        filtered = filtered.filter { it.startTime < p[1].trim() && p[0].trim() < it.endTime }
                    }
                }

                val dayNames = mapOf(1 to "Monday", 2 to "Tuesday", 3 to "Wednesday", 4 to "Thursday", 5 to "Friday", 6 to "Saturday", 7 to "Sunday")

                val rows = filtered.map { entry ->
                    ExcelHelper.ScheduleExportRow(
                        department = deptMap[courseMap[entry.courseId]?.departmentId] ?: "—",
                        instructor = lecturerMap[entry.lecturerId]?.fullName ?: "—",
                        courseName = courseMap[entry.courseId]?.name ?: "—",
                        courseCode = courseMap[entry.courseId]?.code ?: "—",
                        classroom = classroomMap[entry.classroomId]?.name ?: "Room #${entry.classroomId}",
                        day = dayNames[entry.dayOfWeek] ?: "—",
                        timeSlot = "${entry.startTime}-${entry.endTime}"
                    )
                }

                val success = ExcelHelper.exportScheduleToDownloads(requireContext(), rows)
                withContext(Dispatchers.Main) {
                    if (success) Snackbar.make(binding.root, "Exported to Downloads/schedule_export.xlsx", Snackbar.LENGTH_LONG).show()
                    else Snackbar.make(binding.root, "Export failed", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Export error: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── 2E: Download sample ────────────────────────────────────────────
    private fun downloadSample() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = ExcelHelper.generateScheduleImportSample(requireContext())
                withContext(Dispatchers.Main) {
                    if (success) Snackbar.make(binding.root, "Sample saved to Downloads/sample_schedule_import.xlsx", Snackbar.LENGTH_LONG).show()
                    else Snackbar.make(binding.root, "Failed to save sample", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── Permission helper ──────────────────────────────────────────────
    private fun runWithStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
        } else {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                action()
            } else {
                pendingExportAction = action
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    // ─── Data observation ───────────────────────────────────────────────
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.scheduleState.collect { state ->
                        when (state) {
                            is UiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                            is UiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                allSchedules = state.data
                                applyFilters()
                            }
                            is UiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                launch {
                    viewModel.coursesState.collect { state ->
                        if (state is UiState.Success) { allCourses = state.data; applyFilters() }
                    }
                }
                launch {
                    viewModel.lecturersState.collect { state ->
                        if (state is UiState.Success) { allLecturers = state.data; applyFilters() }
                    }
                }
                launch {
                    viewModel.classroomsState.collect { state ->
                        if (state is UiState.Success) { allClassrooms = state.data; applyFilters() }
                    }
                }
                launch {
                    viewModel.departmentsState.collect { state ->
                        if (state is UiState.Success) allDepartments = state.data
                    }
                }
                launch {
                    viewModel.operationState.collect { state ->
                        if (state is UiState.Error) {
                            Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            viewModel.resetOperationState()
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
