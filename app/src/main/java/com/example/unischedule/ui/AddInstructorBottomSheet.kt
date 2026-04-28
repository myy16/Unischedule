package com.example.unischedule.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.BottomSheetAddInstructorBinding
import com.example.unischedule.util.UiState
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class AddInstructorBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddInstructorBinding? = null
    private val binding get() = _binding!!
    
    private val firestoreRepository by lazy { FirestoreRepository(FirebaseFirestore.getInstance()) }

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

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val lecturer = Lecturer(
                        id = (1000L..9999L).random(),  // Generate random ID like departments
                        username = email.substringBefore("@"),  // Extract username from email
                        passwordHash = "123456",  // Default password
                        fullName = if (title.isBlank()) name else "$title $name",
                        departmentId = selectedDept.id
                    )
                    firestoreRepository.addLecturer(lecturer)
                    Toast.makeText(context, "Lecturer added successfully", Toast.LENGTH_SHORT).show()
                    dismiss()
                } catch (e: Exception) {
                    Log.w("AddInstructor", "Failed to add lecturer", e)
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupDepartmentSpinner() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                firestoreRepository.observeDepartments().collect { state ->
                    if (state is UiState.Success) {
                        val items = state.data.map { department ->
                            DepartmentSpinnerItem(id = department.id, label = department.name)
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
