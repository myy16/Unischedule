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
import androidx.fragment.app.viewModels
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

    private val viewModel: com.example.unischedule.viewmodel.FirestoreAdminViewModel by viewModels {
        com.example.unischedule.viewmodel.FirestoreAdminViewModelFactory(firestoreRepository)
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

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val lecturer = Lecturer(
                        id = 0L,  // Let ViewModel generate sequential ID
                        username = email.substringBefore("@"),  // Extract username from email
                        passwordHash = com.example.unischedule.util.PasswordHasher.sha256("123456"),  // Default secure password
                        fullName = if (title.isBlank()) name else "$title $name",
                        departmentId = selectedDept.id,
                        mustChangePassword = true // Require password change on first login
                    )
                    viewModel.addLecturer(lecturer)
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
