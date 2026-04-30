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
    private var allFaculties: List<Faculty> = emptyList()

    // Filter state — cascading Faculty→Department→Instructor + Day + Time
    private var filterFacultyId: Long? = null
    private var filterFacultyName: String? = null
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

        scheduleAdapter = ScheduleAdapter { entry -> showScheduleOptions(entry) }

        binding.schedulerRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scheduleAdapter
        }

        binding.fabAddSchedule.setOnClickListener {
            AddScheduleBottomSheet().show(childFragmentManager, "AddSchedule")
        }
        binding.btnExportShare.setOnClickListener { runWithStoragePermission { exportSchedule() } }
        binding.btnDownloadSample.setOnClickListener { runWithStoragePermission { downloadSample() } }

        observeData()
        buildFilterChips()
    }

    // ─── Edit/Delete ────────────────────────────────────────────────────
    private fun showScheduleOptions(item: ScheduleEntry) {
        val courseName = courseMap[item.courseId]?.let { "${it.code}: ${it.name}" } ?: "Schedule #${item.id}"
        AlertDialog.Builder(requireContext())
            .setTitle(courseName)
            .setItems(arrayOf("Edit", "Delete")) { _, which ->
                when (which) {
                    0 -> showEditScheduleDialog(item)
                    1 -> confirmDelete(item)
                }
            }.show()
    }

    private fun confirmDelete(item: ScheduleEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Schedule")
            .setMessage("Are you sure? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSchedule(item.id.toString())
                Snackbar.make(binding.root, "Schedule deleted", Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null).show()
    }

    // 2C: Edit updates existing record (same document ID)
    private fun showEditScheduleDialog(entry: ScheduleEntry) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_add_schedule, null)
        val courseSpinner = dialogView.findViewById<Spinner>(R.id.courseSpinner)
        val instructorSpinner = dialogView.findViewById<Spinner>(R.id.instructorSpinner)
        val classroomSpinner = dialogView.findViewById<Spinner>(R.id.classroomSpinner)
        val daySpinner = dialogView.findViewById<Spinner>(R.id.daySpinner)
        val btnStart = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnStartTime)
        val btnEnd = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnEndTime)
        dialogView.findViewById<View>(R.id.assignButton)?.visibility = View.GONE

        var editStart = entry.startTime
        var editEnd = entry.endTime

        data class SI(val id: Long, val label: String) { override fun toString() = label }
        data class DI(val id: Int, val name: String) { override fun toString() = name }

        // Populate spinners
        val cItems = allCourses.map { SI(it.id, "${it.code} - ${it.name}") }
        courseSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cItems)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        cItems.indexOfFirst { it.id == entry.courseId }.let { if (it >= 0) courseSpinner.setSelection(it) }

        val lItems = allLecturers.map { SI(it.id, it.fullName.ifBlank { it.username }) }
        instructorSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lItems)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        lItems.indexOfFirst { it.id == entry.lecturerId }.let { if (it >= 0) instructorSpinner.setSelection(it) }

        val rItems = allClassrooms.map { SI(it.id, it.name) }
        classroomSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rItems)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        rItems.indexOfFirst { it.id == entry.classroomId }.let { if (it >= 0) classroomSpinner.setSelection(it) }

        val days = listOf(DI(1,"Monday"),DI(2,"Tuesday"),DI(3,"Wednesday"),DI(4,"Thursday"),DI(5,"Friday"),DI(6,"Saturday"),DI(7,"Sunday"))
        daySpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, days)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        days.indexOfFirst { it.id == entry.dayOfWeek }.let { if (it >= 0) daySpinner.setSelection(it) }

        btnStart.text = "Start: ${entry.startTime}"
        btnEnd.text = "End: ${entry.endTime}"
        btnStart.setOnClickListener {
            val c = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h, m ->
                editStart = String.format("%02d:%02d", h, m); btnStart.text = "Start: $editStart"
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }
        btnEnd.setOnClickListener {
            val c = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h, m ->
                editEnd = String.format("%02d:%02d", h, m); btnEnd.text = "End: $editEnd"
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
        }

        AlertDialog.Builder(requireContext()).setTitle("Edit Schedule").setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val selCourse = (courseSpinner.selectedItem as? SI)?.id ?: entry.courseId
                val selLec = (instructorSpinner.selectedItem as? SI)?.id ?: entry.lecturerId
                val selRoom = (classroomSpinner.selectedItem as? SI)?.id ?: entry.classroomId
                val selDay = (daySpinner.selectedItem as? DI)?.id ?: entry.dayOfWeek
                val course = courseMap[selCourse]

                // Keep the same ID → updateSchedule writes to the same document
                val updated = entry.copy(
                    courseId = selCourse, lecturerId = selLec, classroomId = selRoom,
                    dayOfWeek = selDay, startTime = editStart, endTime = editEnd,
                    courseDepartmentId = course?.departmentId ?: entry.courseDepartmentId,
                    courseYear = course?.year ?: entry.courseYear,
                    courseIsMandatory = course?.isMandatory ?: entry.courseIsMandatory
                )

                val conflict = viewModel.checkConflicts(updated, allSchedules, excludeId = entry.id)
                if (conflict != null) {
                    AlertDialog.Builder(requireContext()).setTitle("Conflict").setMessage(conflict)
                        .setPositiveButton("OK", null).setIcon(android.R.drawable.ic_dialog_alert).show()
                } else {
                    viewModel.updateSchedule(updated)
                    Snackbar.make(binding.root, "Schedule updated", Snackbar.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null).show()
    }

    // ─── Cascading filter: Faculty → Department → Instructor + Day + Time ─
    private fun buildFilterChips() {
        val cg = binding.filterChipGroup
        cg.removeAllViews()

        // Faculty
        if (filterFacultyName != null) {
            cg.addView(activeChip("Faculty: $filterFacultyName") {
                filterFacultyId = null; filterFacultyName = null
                filterDepartmentId = null; filterDepartmentName = null
                filterLecturerId = null; filterLecturerName = null; rebuildAndApply()
            })
        } else { cg.addView(chip("Faculty ▾") { pickFaculty() }) }

        // Department (only when faculty selected)
        if (filterFacultyId != null) {
            if (filterDepartmentName != null) {
                cg.addView(activeChip("Dept: $filterDepartmentName") {
                    filterDepartmentId = null; filterDepartmentName = null
                    filterLecturerId = null; filterLecturerName = null; rebuildAndApply()
                })
            } else { cg.addView(chip("Department ▾") { pickDepartment() }) }
        }

        // Instructor (only when department selected)
        if (filterDepartmentId != null) {
            if (filterLecturerName != null) {
                cg.addView(activeChip("Instructor: $filterLecturerName") {
                    filterLecturerId = null; filterLecturerName = null; rebuildAndApply()
                })
            } else { cg.addView(chip("Instructor ▾") { pickInstructor() }) }
        }

        // Day
        if (filterDayName != null) {
            cg.addView(activeChip("Day: $filterDayName") { filterDay = null; filterDayName = null; rebuildAndApply() })
        } else { cg.addView(chip("Day ▾") { pickDay() }) }

        // Time
        if (filterTimeSlot != null) {
            cg.addView(activeChip("Time: $filterTimeSlot") { filterTimeSlot = null; rebuildAndApply() })
        } else { cg.addView(chip("Time ▾") { pickTime() }) }

        // Clear
        val anyActive = filterFacultyId != null || filterDepartmentId != null || filterLecturerId != null || filterDay != null || filterTimeSlot != null
        if (anyActive) {
            cg.addView(chip("✕ Clear") {
                filterFacultyId = null; filterFacultyName = null
                filterDepartmentId = null; filterDepartmentName = null
                filterLecturerId = null; filterLecturerName = null
                filterDay = null; filterDayName = null; filterTimeSlot = null
                rebuildAndApply()
            })
        }
    }

    private fun chip(label: String, onClick: () -> Unit) = Chip(requireContext()).apply {
        text = label; isCheckable = false; isClickable = true
        setChipBackgroundColorResource(R.color.white)
        setTextColor(resources.getColor(R.color.navy_blue, null))
        chipStrokeWidth = 2f; setChipStrokeColorResource(R.color.navy_blue)
        setOnClickListener { onClick() }
    }

    private fun activeChip(label: String, onClick: () -> Unit) = Chip(requireContext()).apply {
        text = label; isCheckable = false; isClickable = true
        setChipBackgroundColorResource(R.color.navy_blue)
        setTextColor(resources.getColor(R.color.white, null))
        isCloseIconVisible = true; setCloseIconTintResource(R.color.white)
        setOnCloseIconClickListener { onClick() }; setOnClickListener { onClick() }
    }

    private fun pickFaculty() {
        if (allFaculties.isEmpty()) { Toast.makeText(context, "No faculties loaded", Toast.LENGTH_SHORT).show(); return }
        val n = allFaculties.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("Select Faculty").setItems(n) { _, w ->
            val f = allFaculties[w]
            filterFacultyId = f.id; filterFacultyName = f.name
            filterDepartmentId = null; filterDepartmentName = null
            filterLecturerId = null; filterLecturerName = null; rebuildAndApply()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun pickDepartment() {
        val filtered = allDepartments.filter { it.facultyId == filterFacultyId }
        if (filtered.isEmpty()) { Toast.makeText(context, "No departments in this faculty", Toast.LENGTH_SHORT).show(); return }
        val n = filtered.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("Select Department").setItems(n) { _, w ->
            val d = filtered[w]
            filterDepartmentId = d.id; filterDepartmentName = d.name
            filterLecturerId = null; filterLecturerName = null; rebuildAndApply()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun pickInstructor() {
        val filtered = allLecturers.filter { it.departmentId == filterDepartmentId }
        if (filtered.isEmpty()) { Toast.makeText(context, "No lecturers in this department", Toast.LENGTH_SHORT).show(); return }
        val n = filtered.map { it.fullName.ifBlank { it.username } }.toTypedArray()
        AlertDialog.Builder(requireContext()).setTitle("Select Instructor").setItems(n) { _, w ->
            val l = filtered[w]
            filterLecturerId = l.id; filterLecturerName = l.fullName.ifBlank { l.username }; rebuildAndApply()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun pickDay() {
        val d = arrayOf("Monday","Tuesday","Wednesday","Thursday","Friday")
        AlertDialog.Builder(requireContext()).setTitle("Filter by Day").setItems(d) { _, w ->
            filterDay = w + 1; filterDayName = d[w]; rebuildAndApply()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun pickTime() {
        val s = arrayOf("08:00-10:00","10:00-12:00","13:00-15:00","15:00-17:00")
        AlertDialog.Builder(requireContext()).setTitle("Filter by Time").setItems(s) { _, w ->
            filterTimeSlot = s[w]; rebuildAndApply()
        }.setNegativeButton("Cancel", null).show()
    }

    private fun rebuildAndApply() { buildFilterChips(); applyFilters() }

    private fun applyFilters() {
        var filtered = allSchedules

        // Faculty → get all departments in that faculty → filter by course departmentId
        if (filterDepartmentId != null) {
            val cIds = allCourses.filter { it.departmentId == filterDepartmentId }.map { it.id }.toSet()
            filtered = filtered.filter { it.courseId in cIds || it.courseDepartmentId == filterDepartmentId }
        } else if (filterFacultyId != null) {
            val dIds = allDepartments.filter { it.facultyId == filterFacultyId }.map { it.id }.toSet()
            val cIds = allCourses.filter { it.departmentId in dIds }.map { it.id }.toSet()
            filtered = filtered.filter { it.courseId in cIds || it.courseDepartmentId in dIds }
        }

        filterLecturerId?.let { id -> filtered = filtered.filter { it.lecturerId == id } }
        filterDay?.let { d -> filtered = filtered.filter { it.dayOfWeek == d } }
        filterTimeSlot?.let { slot ->
            val p = slot.split("-"); if (p.size == 2) {
                filtered = filtered.filter { it.startTime < p[1].trim() && p[0].trim() < it.endTime }
            }
        }

        if (_binding != null) {
            binding.resultCountText.text = if (filtered.size == allSchedules.size)
                "Showing all ${allSchedules.size} schedules"
            else "Showing ${filtered.size} of ${allSchedules.size} schedules"
        }
        scheduleAdapter.updateItems(filtered)
    }

    // ─── Export / Sample ────────────────────────────────────────────────
    private fun exportSchedule() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dayNames = mapOf(1 to "Monday",2 to "Tuesday",3 to "Wednesday",4 to "Thursday",5 to "Friday",6 to "Saturday",7 to "Sunday")
                val rows = allSchedules.map { e ->
                    ExcelHelper.ScheduleExportRow(
                        department = deptMap[courseMap[e.courseId]?.departmentId] ?: "—",
                        instructor = lecturerMap[e.lecturerId]?.fullName ?: "—",
                        courseName = courseMap[e.courseId]?.name ?: "—",
                        courseCode = courseMap[e.courseId]?.code ?: "—",
                        classroom = classroomMap[e.classroomId]?.name ?: "Room #${e.classroomId}",
                        day = dayNames[e.dayOfWeek] ?: "—",
                        timeSlot = "${e.startTime}-${e.endTime}"
                    )
                }
                val ok = ExcelHelper.exportScheduleToDownloads(requireContext(), rows)
                withContext(Dispatchers.Main) {
                    if (ok) Snackbar.make(binding.root, "Exported to Downloads/schedule_export.xlsx", Snackbar.LENGTH_LONG).show()
                    else Snackbar.make(binding.root, "Export failed", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private fun downloadSample() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val ok = ExcelHelper.generateScheduleImportSample(requireContext())
                withContext(Dispatchers.Main) {
                    if (ok) Snackbar.make(binding.root, "Sample saved to Downloads", Snackbar.LENGTH_LONG).show()
                    else Snackbar.make(binding.root, "Failed", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show() }
            }
        }
    }

    private fun runWithStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) action()
        else if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) action()
        else { pendingExportAction = action; permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
    }

    // ─── Data observation ───────────────────────────────────────────────
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.scheduleState.collect { s ->
                    when (s) {
                        is UiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                        is UiState.Success -> { binding.progressBar.visibility = View.GONE; allSchedules = s.data; applyFilters() }
                        is UiState.Error -> { binding.progressBar.visibility = View.GONE; Toast.makeText(context, s.message, Toast.LENGTH_LONG).show() }
                    }
                }}
                launch { viewModel.coursesState.collect { if (it is UiState.Success) { allCourses = it.data; refreshLookups(); applyFilters() } } }
                launch { viewModel.lecturersState.collect { if (it is UiState.Success) { allLecturers = it.data; refreshLookups(); applyFilters() } } }
                launch { viewModel.classroomsState.collect { if (it is UiState.Success) { allClassrooms = it.data; refreshLookups(); applyFilters() } } }
                launch { viewModel.departmentsState.collect { if (it is UiState.Success) allDepartments = it.data } }
                launch { viewModel.facultiesState.collect { if (it is UiState.Success) allFaculties = it.data } }
                launch { viewModel.operationState.collect { if (it is UiState.Error) { Snackbar.make(binding.root, it.message, Snackbar.LENGTH_LONG).show(); viewModel.resetOperationState() } } }
            }
        }
    }

    /** Push latest lookup maps into the adapter so cards show human-readable names */
    private fun refreshLookups() {
        scheduleAdapter.updateLookups(courseMap, lecturerMap, classroomMap)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
