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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.R
import com.example.unischedule.data.entity.Course as RoomCourse
import com.example.unischedule.data.firestore.Course as FirestoreCourse
import com.example.unischedule.data.firestore.Department
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.DialogEditCourseBinding
import com.example.unischedule.databinding.FragmentCourseManagementBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.util.UiState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
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
    private var allDepartments: List<Department> = emptyList()
    private var allLecturers: List<Lecturer> = emptyList()
    private var allSchedules: List<ScheduleEntry> = emptyList()

    // Filter state — only fields that exist on Course documents
    private var selectedDepartmentId: Long? = null
    private var selectedDepartmentName: String? = null
    private var selectedYear: Int? = null
    private var selectedSemester: Int? = null
    private var mandatoryFilter: String = "All"  // "All", "Mandatory", "Elective"

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

        // Course item tap → Edit/Delete dialog
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

        // Export button
        binding.btnExportExcel.setOnClickListener {
            runWithStoragePermission { exportCourses() }
        }

        // Download sample
        binding.btnDownloadSample.setOnClickListener {
            runWithStoragePermission { downloadSample() }
        }

        // Add Course button
        binding.fabAddCourse.setOnClickListener {
            showAddCourseBottomSheet()
        }

        observeData()
        buildFilterChips()
    }

    private fun showAddCourseBottomSheet() {
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.bottom_sheet_add_course, null)
        bottomSheetDialog.setContentView(dialogView)

        val deptSpinner = dialogView.findViewById<Spinner>(R.id.departmentSpinner)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etCourseName)
        val etCode = dialogView.findViewById<TextInputEditText>(R.id.etCourseCode)
        val yearSpinner = dialogView.findViewById<Spinner>(R.id.yearSpinner)
        val semesterSpinner = dialogView.findViewById<Spinner>(R.id.semesterSpinner)
        val switchMandatory = dialogView.findViewById<SwitchMaterial>(R.id.switchMandatory)
        val btnSave = dialogView.findViewById<View>(R.id.btnSaveCourse)

        // Populate departments
        data class DeptItem(val id: Long, val name: String) { override fun toString() = name }
        val deptItems = allDepartments.map { DeptItem(it.id, it.name) }
        if (deptItems.isEmpty()) {
            Toast.makeText(context, "No departments available", Toast.LENGTH_SHORT).show()
            return
        }
        deptSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, deptItems)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Populate Year
        val years = listOf("1", "2", "3", "4")
        yearSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // Populate Semester
        val semesters = listOf("1", "2")
        semesterSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, semesters)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val code = etCode.text.toString().trim()
            val dept = deptSpinner.selectedItem as? DeptItem

            if (name.isEmpty() || code.isEmpty() || dept == null) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Check for duplicate course code
            val exists = allCourses.any { it.code.equals(code, ignoreCase = true) }
            if (exists) {
                Toast.makeText(context, "Course code already exists", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val year = years[yearSpinner.selectedItemPosition].toInt()
            val semester = semesters[semesterSpinner.selectedItemPosition].toInt()
            val isMandatory = switchMandatory.isChecked

            val newCourse = FirestoreCourse(
                id = System.currentTimeMillis(),
                code = code,
                name = name,
                departmentId = dept.id,
                year = year,
                semester = semester,
                isMandatory = isMandatory
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    firestoreRepository.addCourse(newCourse)
                    bottomSheetDialog.dismiss()
                    Snackbar.make(binding.root, "Course added successfully", Snackbar.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        bottomSheetDialog.show()
    }

    // ─── Edit/Delete selection ───────────────────────────────────────────
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

    // ─── Filter chips (based on actual Course fields) ───────────────────
    private fun buildFilterChips() {
        val chipGroup = binding.filterChipGroup
        chipGroup.removeAllViews()

        // 1. Department filter
        if (selectedDepartmentName != null) {
            chipGroup.addView(createActiveChip("Dept: $selectedDepartmentName") {
                selectedDepartmentId = null; selectedDepartmentName = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Department ▾") { showDepartmentPicker() })
        }

        // 2. Year filter
        if (selectedYear != null) {
            chipGroup.addView(createActiveChip("Year: $selectedYear") {
                selectedYear = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Year ▾") { showYearPicker() })
        }

        // 3. Semester filter
        if (selectedSemester != null) {
            chipGroup.addView(createActiveChip("Sem: $selectedSemester") {
                selectedSemester = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Semester ▾") { showSemesterPicker() })
        }

        // 4. Mandatory toggle
        if (mandatoryFilter != "All") {
            chipGroup.addView(createActiveChip(mandatoryFilter) {
                mandatoryFilter = "All"
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Type ▾") { showMandatoryPicker() })
        }

        // Clear all
        if (selectedDepartmentId != null || selectedYear != null || selectedSemester != null || mandatoryFilter != "All") {
            chipGroup.addView(createChip("✕ Clear") {
                selectedDepartmentId = null; selectedDepartmentName = null
                selectedYear = null; selectedSemester = null
                mandatoryFilter = "All"
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

    private fun showDepartmentPicker() {
        if (allDepartments.isEmpty()) { Toast.makeText(context, "No departments loaded", Toast.LENGTH_SHORT).show(); return }
        val names = allDepartments.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Department")
            .setItems(names) { _, which ->
                val d = allDepartments[which]
                selectedDepartmentId = d.id; selectedDepartmentName = d.name
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showYearPicker() {
        val years = arrayOf("1", "2", "3", "4")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Year")
            .setItems(years) { _, which ->
                selectedYear = which + 1
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showSemesterPicker() {
        val semesters = arrayOf("Semester 1", "Semester 2")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Semester")
            .setItems(semesters) { _, which ->
                selectedSemester = which + 1
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showMandatoryPicker() {
        val options = arrayOf("Mandatory", "Elective")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Type")
            .setItems(options) { _, which ->
                mandatoryFilter = options[which]
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

        // Filter by departmentId (direct field on Course)
        selectedDepartmentId?.let { deptId ->
            filtered = filtered.filter { it.departmentId == deptId }
        }

        // Filter by year
        selectedYear?.let { year ->
            filtered = filtered.filter { it.year == year }
        }

        // Filter by semester
        selectedSemester?.let { sem ->
            filtered = filtered.filter { it.semester == sem }
        }

        // Filter by mandatory/elective
        when (mandatoryFilter) {
            "Mandatory" -> filtered = filtered.filter { it.isMandatory }
            "Elective" -> filtered = filtered.filter { !it.isMandatory }
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
        val deptMap = allDepartments.associate { it.id to it.name }
        val uiItems = courses.map {
            val deptName = deptMap[it.departmentId] ?: ""
            val deptLabel = if (deptName.isNotBlank()) "$deptName | " else ""
            ResourceItem(
                title = "${it.code}: ${it.name}",
                subtitle = "${deptLabel}Year: ${it.year}, Sem: ${it.semester} | ${if (it.isMandatory) "Mandatory" else "Elective"}",
                id = it.id,
                originalObject = it
            )
        }
        courseAdapter.updateItems(uiItems)
    }

    // ─── Export ──────────────────────────────────────────────────────────
    private fun exportCourses() {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Build export rows from currently filtered courses
                var filtered = allCourses
                selectedDepartmentId?.let { deptId ->
                    filtered = filtered.filter { it.departmentId == deptId }
                }
                selectedYear?.let { y -> filtered = filtered.filter { it.year == y } }
                selectedSemester?.let { s -> filtered = filtered.filter { it.semester == s } }
                when (mandatoryFilter) {
                    "Mandatory" -> filtered = filtered.filter { it.isMandatory }
                    "Elective" -> filtered = filtered.filter { !it.isMandatory }
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

    // ─── Download sample ────────────────────────────────────────────────
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
                // Observe departments (for filter dropdown + display)
                launch {
                    firestoreRepository.observeDepartments().collect { state ->
                        if (state is UiState.Success) allDepartments = state.data
                    }
                }
                // Observe lecturers (for export)
                launch {
                    firestoreRepository.observeLecturers().collect { state ->
                        if (state is UiState.Success) allLecturers = state.data
                    }
                }
                // Observe schedules (for export — instructor/time/classroom resolution)
                launch {
                    firestoreRepository.observeSchedules().collect { state ->
                        if (state is UiState.Success) allSchedules = state.data
                    }
                }
            }
        }
    }

    // ─── Import with department resolution ──────────────────────────────
    private fun importDataFromExcel(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val importedCourses = ExcelHelper.importCoursesWithDepartment(
                        inputStream, firestoreRepository
                    )
                    importedCourses.forEach { firestoreCourse ->
                        firestoreRepository.addCourse(firestoreCourse)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imported ${importedCourses.size} courses", Toast.LENGTH_SHORT).show()
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
