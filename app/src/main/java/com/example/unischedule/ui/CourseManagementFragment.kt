package com.example.unischedule.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.R
import com.example.unischedule.data.entity.Course as RoomCourse
import com.example.unischedule.data.firestore.Course as FirestoreCourse
import com.example.unischedule.data.firestore.Department
import com.example.unischedule.data.firestore.Faculty
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.DialogEditCourseBinding
import com.example.unischedule.databinding.FragmentCourseManagementBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.util.UiState
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CourseManagementFragment : Fragment() {
    private var _binding: FragmentCourseManagementBinding? = null
    private val binding get() = _binding!!

    private val firestoreRepository by lazy { FirestoreRepository(FirebaseFirestore.getInstance()) }
    private lateinit var courseAdapter: ResourceAdapter

    // Raw data caches
    private var allCourses: List<FirestoreCourse> = emptyList()
    private var allFaculties: List<Faculty> = emptyList()
    private var allDepartments: List<Department> = emptyList()
    private var allLecturers: List<Lecturer> = emptyList()
    private var allSchedules: List<ScheduleEntry> = emptyList()

    // Filter state
    private var selectedFacultyId: Long? = null
    private var selectedFacultyName: String? = null
    private var selectedDepartmentId: Long? = null
    private var selectedDepartmentName: String? = null
    private var selectedLecturerId: Long? = null
    private var selectedLecturerName: String? = null

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importDataFromExcel(uri)
            }
        }
    }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pendingExportAction?.invoke()
        else Toast.makeText(context, "Storage permission required", Toast.LENGTH_SHORT).show()
    }

    private var pendingExportAction: (() -> Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (UserSession.userRole != UserSession.Role.ADMIN) {
            findNavController().navigate(R.id.nav_dashboard)
            return
        }

        // 1A: Course item tap → Edit/Delete dialog
        courseAdapter = ResourceAdapter { resourceItem ->
            val course = resourceItem.originalObject as? FirestoreCourse
            if (course != null) {
                showCourseActionDialog(course)
            }
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = courseAdapter
        }

        binding.btnImportExcel.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            importLauncher.launch(Intent.createChooser(intent, "Select Excel File"))
        }

        // 1C: Export button
        binding.btnExportExcel.setOnClickListener {
            runWithStoragePermission { exportCourses() }
        }

        // 1D: Download sample
        binding.btnDownloadSample.setOnClickListener {
            runWithStoragePermission { downloadSample() }
        }

        observeData()
        buildFilterChips()
    }

    // ─── 1A: Edit/Delete selection ──────────────────────────────────────
    private fun showCourseActionDialog(course: FirestoreCourse) {
        val options = arrayOf("Edit", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle("${course.code}: ${course.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showEditCourseDialog(course)
                    1 -> showDeleteConfirmation(course)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(course: FirestoreCourse) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete ${course.name}?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        firestoreRepository.deleteCourse(course.id)
                        Snackbar.make(binding.root, "Course deleted", Snackbar.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditCourseDialog(course: FirestoreCourse) {
        val dialogBinding = DialogEditCourseBinding.inflate(layoutInflater)
        
        dialogBinding.etCourseCode.setText(course.code)
        dialogBinding.etCourseName.setText(course.name)
        dialogBinding.etYear.setText(course.year.toString())
        dialogBinding.etSemester.setText(course.semester.toString())
        dialogBinding.switchMandatory.isChecked = course.isMandatory

        AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { _, _ ->
                val updatedCourse = course.copy(
                    code = dialogBinding.etCourseCode.text.toString(),
                    name = dialogBinding.etCourseName.text.toString(),
                    year = dialogBinding.etYear.text.toString().toIntOrNull() ?: course.year,
                    semester = dialogBinding.etSemester.text.toString().toIntOrNull() ?: course.semester,
                    isMandatory = dialogBinding.switchMandatory.isChecked
                )
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        firestoreRepository.updateCourse(updatedCourse)
                        Toast.makeText(context, "Course updated", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Log.w("CourseEdit", "Update failed", e)
                        Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── 1B: Cascading filter chips ─────────────────────────────────────
    private fun buildFilterChips() {
        val chipGroup = binding.filterChipGroup
        chipGroup.removeAllViews()

        // Faculty chip
        if (selectedFacultyName != null) {
            chipGroup.addView(createActiveChip("Faculty: $selectedFacultyName") {
                selectedFacultyId = null; selectedFacultyName = null
                selectedDepartmentId = null; selectedDepartmentName = null
                selectedLecturerId = null; selectedLecturerName = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Faculty ▾") { showFacultyPicker() })
        }

        // Department chip (only if faculty selected)
        if (selectedFacultyId != null) {
            if (selectedDepartmentName != null) {
                chipGroup.addView(createActiveChip("Dept: $selectedDepartmentName") {
                    selectedDepartmentId = null; selectedDepartmentName = null
                    selectedLecturerId = null; selectedLecturerName = null
                    rebuildAndApply()
                })
            } else {
                chipGroup.addView(createChip("Department ▾") { showDepartmentPicker() })
            }
        }

        // Instructor chip (only if department selected)
        if (selectedDepartmentId != null) {
            if (selectedLecturerName != null) {
                chipGroup.addView(createActiveChip("Instructor: $selectedLecturerName") {
                    selectedLecturerId = null; selectedLecturerName = null
                    rebuildAndApply()
                })
            } else {
                chipGroup.addView(createChip("Instructor ▾") { showLecturerPicker() })
            }
        }

        // Clear all
        if (selectedFacultyId != null || selectedDepartmentId != null || selectedLecturerId != null) {
            chipGroup.addView(createChip("✕ Clear") {
                selectedFacultyId = null; selectedFacultyName = null
                selectedDepartmentId = null; selectedDepartmentName = null
                selectedLecturerId = null; selectedLecturerName = null
                rebuildAndApply()
            })
        }
    }

    private fun createChip(label: String, onClick: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            text = label; isCheckable = false; isClickable = true
            setChipBackgroundColorResource(R.color.white)
            setTextColor(resources.getColor(R.color.navy_blue, null))
            chipStrokeWidth = 2f
            setChipStrokeColorResource(R.color.navy_blue)
            setOnClickListener { onClick() }
        }
    }

    private fun createActiveChip(label: String, onClick: () -> Unit): Chip {
        return Chip(requireContext()).apply {
            text = label; isCheckable = false; isClickable = true
            setChipBackgroundColorResource(R.color.navy_blue)
            setTextColor(resources.getColor(R.color.white, null))
            isCloseIconVisible = true
            setCloseIconTintResource(R.color.white)
            setOnCloseIconClickListener { onClick() }
            setOnClickListener { onClick() }
        }
    }

    private fun showFacultyPicker() {
        if (allFaculties.isEmpty()) { Toast.makeText(context, "No faculties loaded", Toast.LENGTH_SHORT).show(); return }
        val names = allFaculties.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Faculty")
            .setItems(names) { _, which ->
                val f = allFaculties[which]
                selectedFacultyId = f.id; selectedFacultyName = f.name
                selectedDepartmentId = null; selectedDepartmentName = null
                selectedLecturerId = null; selectedLecturerName = null
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showDepartmentPicker() {
        val filtered = allDepartments.filter { it.facultyId == selectedFacultyId }
        if (filtered.isEmpty()) { Toast.makeText(context, "No departments in this faculty", Toast.LENGTH_SHORT).show(); return }
        val names = filtered.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Department")
            .setItems(names) { _, which ->
                val d = filtered[which]
                selectedDepartmentId = d.id; selectedDepartmentName = d.name
                selectedLecturerId = null; selectedLecturerName = null
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showLecturerPicker() {
        val filtered = allLecturers.filter { it.departmentId == selectedDepartmentId }
        if (filtered.isEmpty()) { Toast.makeText(context, "No lecturers in this department", Toast.LENGTH_SHORT).show(); return }
        val names = filtered.map { it.fullName.ifBlank { it.username } }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Instructor")
            .setItems(names) { _, which ->
                val l = filtered[which]
                selectedLecturerId = l.id; selectedLecturerName = l.fullName.ifBlank { l.username }
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun rebuildAndApply() {
        buildFilterChips()
        applyFilters()
    }

    private fun applyFilters() {
        var filtered = allCourses

        // Filter by department (from faculty → department cascade)
        if (selectedDepartmentId != null) {
            filtered = filtered.filter { it.departmentId == selectedDepartmentId }
        } else if (selectedFacultyId != null) {
            // If faculty is selected but not a specific department, filter by all departments in that faculty
            val deptIdsInFaculty = allDepartments.filter { it.facultyId == selectedFacultyId }.map { it.id }.toSet()
            filtered = filtered.filter { it.departmentId in deptIdsInFaculty }
        }

        // Filter by instructor: find courses assigned to this lecturer in schedules
        if (selectedLecturerId != null) {
            val courseIdsForLecturer = allSchedules
                .filter { it.lecturerId == selectedLecturerId }
                .map { it.courseId }
                .toSet()
            filtered = filtered.filter { it.id in courseIdsForLecturer }
        }

        updateResultCount(filtered.size, allCourses.size)
        displayCourses(filtered)
    }

    private fun updateResultCount(shown: Int, total: Int) {
        if (_binding == null) return
        binding.resultCountText.text = if (shown == total) "Showing all $total courses"
            else "Showing $shown of $total courses"
    }

    private fun displayCourses(courses: List<FirestoreCourse>) {
        val uiItems = courses.map {
            ResourceItem(
                title = "${it.code}: ${it.name}",
                subtitle = "Year: ${it.year}, Sem: ${it.semester} | ${if (it.isMandatory) "Mandatory" else "Elective"}",
                id = it.id,
                originalObject = it
            )
        }
        courseAdapter.updateItems(uiItems)
    }

    // ─── 1C: Export ─────────────────────────────────────────────────────
    private fun exportCourses() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Build export rows from currently filtered courses
                var filtered = allCourses
                if (selectedDepartmentId != null) {
                    filtered = filtered.filter { it.departmentId == selectedDepartmentId }
                } else if (selectedFacultyId != null) {
                    val deptIds = allDepartments.filter { it.facultyId == selectedFacultyId }.map { it.id }.toSet()
                    filtered = filtered.filter { it.departmentId in deptIds }
                }
                if (selectedLecturerId != null) {
                    val courseIds = allSchedules.filter { it.lecturerId == selectedLecturerId }.map { it.courseId }.toSet()
                    filtered = filtered.filter { it.id in courseIds }
                }

                val deptMap = allDepartments.associate { it.id to it.name }
                val lecturerMap = allLecturers.associate { it.id to it.fullName.ifBlank { it.username } }

                val rows = filtered.map { course ->
                    val schedule = allSchedules.firstOrNull { it.courseId == course.id }
                    ExcelHelper.CourseExportRow(
                        department = deptMap[course.departmentId] ?: "Dept #${course.departmentId}",
                        courseName = course.name,
                        courseCode = course.code,
                        instructor = if (schedule != null) lecturerMap[schedule.lecturerId] ?: "—" else "—",
                        timeSlot = if (schedule != null) "${schedule.startTime}-${schedule.endTime}" else "—",
                        classroom = if (schedule != null) "Room #${schedule.classroomId}" else "—"
                    )
                }

                val success = ExcelHelper.exportCoursesToDownloads(requireContext(), rows)
                withContext(Dispatchers.Main) {
                    if (success) Snackbar.make(binding.root, "Exported to Downloads/courses_export.xlsx", Snackbar.LENGTH_LONG).show()
                    else Snackbar.make(binding.root, "Export failed", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Export error: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── 1D: Download sample ────────────────────────────────────────────
    private fun downloadSample() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val success = ExcelHelper.generateCourseImportSample(requireContext())
                withContext(Dispatchers.Main) {
                    if (success) Snackbar.make(binding.root, "Sample saved to Downloads/sample_import.xlsx", Snackbar.LENGTH_LONG).show()
                    else Snackbar.make(binding.root, "Failed to save sample", Snackbar.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "Error: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ─── Storage permission helper ──────────────────────────────────────
    private fun runWithStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // No permission needed on API 29+
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
                // Observe courses
                launch {
                    firestoreRepository.observeCourses().collect { state ->
                        when (state) {
                            is UiState.Loading -> binding.progressBar.visibility = View.VISIBLE
                            is UiState.Success -> {
                                binding.progressBar.visibility = View.GONE
                                allCourses = state.data
                                applyFilters()
                            }
                            is UiState.Error -> {
                                binding.progressBar.visibility = View.GONE
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
                // Observe faculties
                launch {
                    firestoreRepository.observeFaculties().collect { state ->
                        if (state is UiState.Success) allFaculties = state.data
                    }
                }
                // Observe departments
                launch {
                    firestoreRepository.observeDepartments().collect { state ->
                        if (state is UiState.Success) allDepartments = state.data
                    }
                }
                // Observe lecturers
                launch {
                    firestoreRepository.observeLecturers().collect { state ->
                        if (state is UiState.Success) allLecturers = state.data
                    }
                }
                // Observe schedules (for instructor→course mapping and export)
                launch {
                    firestoreRepository.observeSchedules().collect { state ->
                        if (state is UiState.Success) allSchedules = state.data
                    }
                }
            }
        }
    }

    private fun importDataFromExcel(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val importedRoomCourses = ExcelHelper.importCoursesFromExcel(inputStream)
                    importedRoomCourses.forEach { roomCourse ->
                        // Convert Room Course to Firestore Course
                        val firestoreCourse = FirestoreCourse(
                            id = (1000L..9999L).random(),
                            departmentId = roomCourse.departmentId,
                            code = roomCourse.code,
                            name = roomCourse.name,
                            year = roomCourse.year,
                            semester = roomCourse.semester,
                            isMandatory = roomCourse.isMandatory
                        )
                        firestoreRepository.addCourse(firestoreCourse)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imported ${importedRoomCourses.size} courses", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
