package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.FragmentBaseResourceListBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreAdminViewModel
import com.example.unischedule.viewmodel.FirestoreAdminViewModelFactory
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class BaseResourceListFragment : Fragment() {

    private var _binding: FragmentBaseResourceListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: FirestoreAdminViewModel by viewModels {
        FirestoreAdminViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }
    
    private var type: Int = 0
    private lateinit var resourceAdapter: ResourceAdapter

    // --- Filter/Sort state ---
    // Instructors (type=2)
    private var selectedDepartmentFilter: Long? = null
    private var selectedDepartmentName: String? = null
    private var selectedTitleFilter: String? = null
    private var instructorSortMode: String = "none"

    // Classrooms (type=3)
    private var selectedTypeFilter: String? = null   // "Lab" or "Classroom"
    private var selectedCapacityRange: String? = null // "<30", "30-60", "60+"
    private var classroomSortMode: String = "none"

    // Raw data caches
    private var rawLecturers: List<Lecturer> = emptyList()
    private var rawClassrooms: List<Classroom> = emptyList()
    private var rawFaculties: List<Faculty> = emptyList()
    private var rawDepartments: List<Department> = emptyList()

    // Department lookup for filter dropdown
    private var departmentList: List<Department> = emptyList()

    companion object {
        fun newInstance(type: Int) = BaseResourceListFragment().apply {
            arguments = Bundle().apply { putInt("type", type) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = arguments?.getInt("type") ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBaseResourceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        resourceAdapter = ResourceAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = resourceAdapter
        }

        binding.fabAdd.setOnClickListener {
            when (type) {
                0 -> AddFacultyBottomSheet().show(childFragmentManager, "AddFaculty")
                1 -> AddDepartmentBottomSheet().show(childFragmentManager, "AddDepartment")
                2 -> AddInstructorBottomSheet().show(childFragmentManager, "AddInstructor")
                3 -> AddClassroomBottomSheet().show(childFragmentManager, "AddClassroom")
            }
        }

        // Only show filter bar for Instructors (2) and Classrooms (3)
        binding.filterBarContainer.visibility =
            if (type == 2 || type == 3) View.VISIBLE else View.GONE

        observeData()
        buildFilterChips()
    }

    // ─── Filter Chips ────────────────────────────────────────────────────
    private fun buildFilterChips() {
        val chipGroup = binding.filterChipGroup
        chipGroup.removeAllViews()

        when (type) {
            2 -> buildInstructorChips()
            3 -> buildClassroomChips()
        }
    }

    private fun createChip(label: String, onClick: () -> Unit): Chip {
        val chip = Chip(requireContext())
        chip.text = label
        chip.isCheckable = false
        chip.isClickable = true
        chip.setChipBackgroundColorResource(R.color.white)
        chip.setTextColor(resources.getColor(R.color.navy_blue, null))
        chip.chipStrokeWidth = 2f
        chip.setChipStrokeColorResource(R.color.navy_blue)
        chip.setOnClickListener { onClick() }
        return chip
    }

    private fun createActiveChip(label: String, onClick: () -> Unit): Chip {
        val chip = Chip(requireContext())
        chip.text = label
        chip.isCheckable = false
        chip.isClickable = true
        chip.setChipBackgroundColorResource(R.color.navy_blue)
        chip.setTextColor(resources.getColor(R.color.white, null))
        chip.isCloseIconVisible = true
        chip.setCloseIconTintResource(R.color.white)
        chip.setOnCloseIconClickListener { onClick() }
        chip.setOnClickListener { onClick() }
        return chip
    }

    // ─── INSTRUCTOR filter chips ─────────────────────────────────────────
    private fun buildInstructorChips() {
        val chipGroup = binding.filterChipGroup
        chipGroup.removeAllViews()

        // Department filter chip
        if (selectedDepartmentName != null) {
            chipGroup.addView(createActiveChip("Dept: $selectedDepartmentName") {
                selectedDepartmentFilter = null
                selectedDepartmentName = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Department ▾") {
                showDepartmentPicker()
            })
        }

        // Title filter chip
        if (selectedTitleFilter != null) {
            chipGroup.addView(createActiveChip("Title: $selectedTitleFilter") {
                selectedTitleFilter = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Title ▾") {
                showTitlePicker()
            })
        }

        // Sort chip
        val sortLabel = when (instructorSortMode) {
            "name_asc" -> "Sort: A→Z"
            "name_desc" -> "Sort: Z→A"
            "department" -> "Sort: Dept"
            else -> "Sort ▾"
        }
        if (instructorSortMode != "none") {
            chipGroup.addView(createActiveChip(sortLabel) {
                instructorSortMode = "none"
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip(sortLabel) {
                showInstructorSortPicker()
            })
        }

        // Clear all
        if (selectedDepartmentFilter != null || selectedTitleFilter != null || instructorSortMode != "none") {
            chipGroup.addView(createChip("✕ Clear") {
                selectedDepartmentFilter = null
                selectedDepartmentName = null
                selectedTitleFilter = null
                instructorSortMode = "none"
                rebuildAndApply()
            })
        }
    }

    // ─── CLASSROOM filter chips ──────────────────────────────────────────
    private fun buildClassroomChips() {
        val chipGroup = binding.filterChipGroup
        chipGroup.removeAllViews()

        // Type filter
        if (selectedTypeFilter != null) {
            chipGroup.addView(createActiveChip("Type: $selectedTypeFilter") {
                selectedTypeFilter = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Type ▾") {
                showClassroomTypePicker()
            })
        }

        // Capacity filter
        if (selectedCapacityRange != null) {
            chipGroup.addView(createActiveChip("Capacity: $selectedCapacityRange") {
                selectedCapacityRange = null
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip("Capacity ▾") {
                showCapacityPicker()
            })
        }

        // Sort
        val sortLabel = when (classroomSortMode) {
            "code_asc" -> "Sort: Code A→Z"
            "cap_low" -> "Sort: Cap ↑"
            "cap_high" -> "Sort: Cap ↓"
            else -> "Sort ▾"
        }
        if (classroomSortMode != "none") {
            chipGroup.addView(createActiveChip(sortLabel) {
                classroomSortMode = "none"
                rebuildAndApply()
            })
        } else {
            chipGroup.addView(createChip(sortLabel) {
                showClassroomSortPicker()
            })
        }

        // Clear all
        if (selectedTypeFilter != null || selectedCapacityRange != null || classroomSortMode != "none") {
            chipGroup.addView(createChip("✕ Clear") {
                selectedTypeFilter = null
                selectedCapacityRange = null
                classroomSortMode = "none"
                rebuildAndApply()
            })
        }
    }

    // ─── Picker Dialogs ──────────────────────────────────────────────────
    private fun showDepartmentPicker() {
        if (departmentList.isEmpty()) {
            Toast.makeText(context, "No departments loaded yet", Toast.LENGTH_SHORT).show()
            return
        }
        val names = departmentList.map { it.name }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Department")
            .setItems(names) { _, which ->
                val dept = departmentList[which]
                selectedDepartmentFilter = dept.id
                selectedDepartmentName = dept.name
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showTitlePicker() {
        val titles = arrayOf("Prof.", "Assoc. Prof.", "Assist. Prof.", "Dr.")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Academic Title")
            .setItems(titles) { _, which ->
                selectedTitleFilter = titles[which]
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showInstructorSortPicker() {
        val options = arrayOf("Name A→Z", "Name Z→A", "Department")
        AlertDialog.Builder(requireContext())
            .setTitle("Sort Instructors")
            .setItems(options) { _, which ->
                instructorSortMode = when (which) {
                    0 -> "name_asc"
                    1 -> "name_desc"
                    2 -> "department"
                    else -> "none"
                }
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClassroomTypePicker() {
        val options = arrayOf("Lab", "Classroom")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Type")
            .setItems(options) { _, which ->
                selectedTypeFilter = options[which]
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showCapacityPicker() {
        val options = arrayOf("<30", "30–60", "60+")
        AlertDialog.Builder(requireContext())
            .setTitle("Filter by Capacity")
            .setItems(options) { _, which ->
                selectedCapacityRange = options[which]
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showClassroomSortPicker() {
        val options = arrayOf("Room code A→Z", "Capacity low→high", "Capacity high→low")
        AlertDialog.Builder(requireContext())
            .setTitle("Sort Classrooms")
            .setItems(options) { _, which ->
                classroomSortMode = when (which) {
                    0 -> "code_asc"
                    1 -> "cap_low"
                    2 -> "cap_high"
                    else -> "none"
                }
                rebuildAndApply()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Apply filters + sort to cached data ─────────────────────────────
    private fun rebuildAndApply() {
        buildFilterChips()
        when (type) {
            2 -> applyInstructorFilters()
            3 -> applyClassroomFilters()
        }
    }

    private fun applyInstructorFilters() {
        var filtered = rawLecturers

        // Filter by department
        selectedDepartmentFilter?.let { deptId ->
            filtered = filtered.filter { it.departmentId == deptId }
        }

        // Filter by academic title prefix
        selectedTitleFilter?.let { title ->
            filtered = filtered.filter { it.fullName.startsWith(title, ignoreCase = true) }
        }

        // Sort
        filtered = when (instructorSortMode) {
            "name_asc" -> filtered.sortedBy { it.fullName.lowercase() }
            "name_desc" -> filtered.sortedByDescending { it.fullName.lowercase() }
            "department" -> filtered.sortedBy { it.departmentId }
            else -> filtered
        }

        updateResultCount(filtered.size, rawLecturers.size, "lecturers")
        displayLecturers(filtered)
    }

    private fun applyClassroomFilters() {
        var filtered = rawClassrooms

        // Filter by type
        selectedTypeFilter?.let { typeStr ->
            filtered = when (typeStr) {
                "Lab" -> filtered.filter { it.isLab }
                "Classroom" -> filtered.filter { !it.isLab }
                else -> filtered
            }
        }

        // Filter by capacity range
        selectedCapacityRange?.let { range ->
            filtered = when (range) {
                "<30" -> filtered.filter { it.capacity < 30 }
                "30–60" -> filtered.filter { it.capacity in 30..60 }
                "60+" -> filtered.filter { it.capacity > 60 }
                else -> filtered
            }
        }

        // Sort
        filtered = when (classroomSortMode) {
            "code_asc" -> filtered.sortedBy { it.name.lowercase() }
            "cap_low" -> filtered.sortedBy { it.capacity }
            "cap_high" -> filtered.sortedByDescending { it.capacity }
            else -> filtered
        }

        updateResultCount(filtered.size, rawClassrooms.size, "classrooms")
        displayClassrooms(filtered)
    }

    private fun updateResultCount(shown: Int, total: Int, label: String) {
        if (_binding == null) return
        if (shown == total) {
            binding.resultCountText.text = "Showing all $total $label"
        } else {
            binding.resultCountText.text = "Showing $shown of $total $label"
        }
    }

    private fun displayLecturers(list: List<Lecturer>) {
        val uiItems = list.map { item ->
            ResourceItem(
                item.fullName.ifBlank { item.username },
                "Role: ${item.role} | Department ID: ${item.departmentId}"
            )
        }
        resourceAdapter.updateItems(uiItems)
    }

    private fun displayClassrooms(list: List<Classroom>) {
        val uiItems = list.map { item ->
            val typeStr = if (item.isLab) "LAB" else "THEORY"
            ResourceItem(item.name, "Capacity: ${item.capacity} | Type: $typeStr")
        }
        resourceAdapter.updateItems(uiItems)
    }

    // ─── Data observation ────────────────────────────────────────────────
    private fun observeData() {
        // Always observe departments for the filter dropdown
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.departmentsState.collect { state ->
                    if (state is UiState.Success) {
                        departmentList = state.data
                    }
                }
            }
        }

        val stateFlow = when (type) {
            0 -> viewModel.facultiesState
            1 -> viewModel.departmentsState
            2 -> viewModel.lecturersState
            3 -> viewModel.classroomsState
            else -> null
        }

        stateFlow?.let { flow ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    flow.collect { state ->
                        handleUiState(state)
                    }
                }
            }
        }
    }

    private fun <T> handleUiState(state: UiState<List<T>>) {
        when (state) {
            is UiState.Loading -> {
                if (_binding != null) binding.resultCountText.text = "Loading…"
            }
            is UiState.Success -> {
                when (type) {
                    0 -> {
                        // Faculties — no filtering
                        val uiItems = state.data.map { item ->
                            val faculty = item as Faculty
                            ResourceItem(faculty.name, "ID: ${faculty.id}")
                        }
                        resourceAdapter.updateItems(uiItems)
                        if (_binding != null) binding.resultCountText.text = "${uiItems.size} faculties"
                    }
                    1 -> {
                        // Departments — no filtering
                        val uiItems = state.data.map { item ->
                            val dept = item as Department
                            ResourceItem(dept.name, "Faculty ID: ${dept.facultyId}")
                        }
                        resourceAdapter.updateItems(uiItems)
                        if (_binding != null) binding.resultCountText.text = "${uiItems.size} departments"
                    }
                    2 -> {
                        // Lecturers — apply filters
                        @Suppress("UNCHECKED_CAST")
                        rawLecturers = state.data as List<Lecturer>
                        applyInstructorFilters()
                    }
                    3 -> {
                        // Classrooms — apply filters
                        @Suppress("UNCHECKED_CAST")
                        rawClassrooms = state.data as List<Classroom>
                        applyClassroomFilters()
                    }
                    else -> {
                        val uiItems = state.data.map { ResourceItem("Unknown", "") }
                        resourceAdapter.updateItems(uiItems)
                    }
                }
            }
            is UiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
