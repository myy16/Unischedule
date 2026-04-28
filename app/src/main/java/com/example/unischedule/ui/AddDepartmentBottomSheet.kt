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
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.BottomSheetAddDepartmentBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.AdminViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class AddDepartmentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddDepartmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AdminViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }

    private data class FacultySpinnerItem(val id: Long, val name: String) {
        override fun toString(): String = name
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddDepartmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupFacultySpinner()

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val selectedFaculty = binding.facultySpinner.selectedItem as? FacultySpinnerItem

            if (name.isEmpty()) {
                binding.nameLayout.error = "Department name cannot be empty"
                return@setOnClickListener
            }

            if (selectedFaculty == null) {
                Toast.makeText(requireContext(), "Please select a faculty", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addDepartment(selectedFaculty.id, name)
            Toast.makeText(requireContext(), "Department addition requested", Toast.LENGTH_SHORT).show()
            dismiss()
        }
    }

    private fun setupFacultySpinner() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.facultiesState.collect { state ->
                    if (state is UiState.Success) {
                        val items = state.data.map { FacultySpinnerItem(it.id, it.name) }
                        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        binding.facultySpinner.adapter = adapter
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
