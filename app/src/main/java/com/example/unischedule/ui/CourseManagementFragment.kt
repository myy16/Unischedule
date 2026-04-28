package com.example.unischedule.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.data.entity.Course as RoomCourse
import com.example.unischedule.data.firestore.Course as FirestoreCourse
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.DialogEditCourseBinding
import com.example.unischedule.databinding.FragmentCourseManagementBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.util.UiState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CourseManagementFragment : Fragment() {
    private var _binding: FragmentCourseManagementBinding? = null
    private val binding get() = _binding!!

    private val firestoreRepository by lazy { FirestoreRepository(FirebaseFirestore.getInstance()) }
    private lateinit var courseAdapter: ResourceAdapter

    private val importLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importDataFromExcel(uri)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCourseManagementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        courseAdapter = ResourceAdapter { resourceItem ->
            val course = resourceItem.originalObject as? FirestoreCourse
            if (course != null) {
                showEditCourseDialog(course)
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

        // Sustainable UI Collection Strategy
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                firestoreRepository.observeCourses().collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            // Show progress bar if available
                        }
                        is UiState.Success -> {
                            val uiItems = state.data.map { 
                                ResourceItem(
                                    title = "${it.code}: ${it.name}", 
                                    subtitle = "Year: ${it.year}, Sem: ${it.semester} | ${if(it.isMandatory) "Mandatory" else "Elective"}",
                                    id = it.id,
                                    originalObject = it
                                )
                            }
                            courseAdapter.updateItems(uiItems)
                        }
                        is UiState.Error -> {
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                        }
                        else -> Unit
                    }
                }
            }
        }
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
