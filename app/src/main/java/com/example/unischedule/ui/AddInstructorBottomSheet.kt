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
                    val generatedFullName = if (title.isBlank()) name else "$title $name"
                    val generatedPassword = com.example.unischedule.util.StringUtil.generateInitialPassword()
                    val lecturer = Lecturer(
                        id = 0L,  // Let ViewModel generate sequential ID
                        username = com.example.unischedule.util.StringUtil.generateUsername(generatedFullName),
                        passwordHash = com.example.unischedule.util.PasswordHasher.sha256(generatedPassword),
                        fullName = generatedFullName,
                        departmentId = selectedDept.id,
                        mustChangePassword = true // Require password change on first login
                    )
                    viewModel.addLecturer(lecturer)
                    
                    // Show the generated credentials so Admin can share them
                    android.app.AlertDialog.Builder(requireContext())
                        .setTitle("Lecturer Added")
                        .setMessage("Lecturer successfully added.\n\nUsername: ${lecturer.username}\nTemporary Password: $generatedPassword\n\nPlease securely share these credentials.")
                        .setPositiveButton("OK") { _, _ -> dismiss() }
                        .setCancelable(false)
                        .show()
                        
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
