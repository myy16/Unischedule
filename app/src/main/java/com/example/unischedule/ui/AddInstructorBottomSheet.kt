package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.entity.Department
import com.example.unischedule.data.entity.Instructor
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.BottomSheetAddInstructorBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.AdminViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AddInstructorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddInstructorBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AdminViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }

    private data class DepartmentSpinnerItem(
        val id: Long,
        val label: String
    ) {
        override fun toString(): String = label
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddInstructorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDepartmentSpinner()

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString()
            val email = binding.emailEditText.text.toString()
            val title = binding.titleEditText.text.toString()
            val selectedDept = binding.departmentSpinner.selectedItem as? DepartmentSpinnerItem

            if (name.isBlank() || email.isBlank() || title.isBlank() || selectedDept == null) {
                Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addInstructor(
                Instructor(
                    departmentId = selectedDept.id,
                    name = name,
                    email = email,
                    title = title,
                    passwordHash = "123456" // Default password
                )
            )
            dismiss()
        }
    }

    private fun setupDepartmentSpinner() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.departmentsState.collect { state ->
                    if (state is UiState.Success) {
                        val items = state.data.map { department ->
                            department.toSpinnerItem()
                        }

                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_item,
                            items
                        ).apply {
                            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        }

                        binding.departmentSpinner.adapter = adapter
                    }
                }
            }
        }
    }

    private fun Department.toSpinnerItem(): DepartmentSpinnerItem {
        return DepartmentSpinnerItem(id = id, label = name)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
