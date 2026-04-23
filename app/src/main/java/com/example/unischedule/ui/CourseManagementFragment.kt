package com.example.unischedule.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.entity.Course
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.DialogEditCourseBinding
import com.example.unischedule.databinding.FragmentCourseManagementBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.AdminViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CourseManagementFragment : Fragment() {
    private var _binding: FragmentCourseManagementBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }

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
            val course = resourceItem.originalObject as? Course
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
                viewModel.coursesState.collect { state ->
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

    private fun showEditCourseDialog(course: Course) {
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
                viewModel.updateCourse(updatedCourse)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun importDataFromExcel(uri: android.net.Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val importedCourses = ExcelHelper.importCoursesFromExcel(inputStream)
                    importedCourses.forEach { course ->
                        viewModel.addCourse(course)
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Imported ${importedCourses.size} courses", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                // Task 4: Note - Excel import is a utility but still follows pattern
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
