package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.unischedule.data.firestore.Department
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.BottomSheetAddDepartmentBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlin.random.Random

class AddDepartmentBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddDepartmentBinding? = null
    private val binding get() = _binding!!

    private val firestoreRepository by lazy { FirestoreRepository(FirebaseFirestore.getInstance()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddDepartmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()

            if (name.isEmpty()) {
                binding.nameLayout.error = "Department name cannot be empty"
                return@setOnClickListener
            }

            saveDepartmentToFirestore(name)
        }
    }

    private fun saveDepartmentToFirestore(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val departmentId = Random.nextLong(1000, 9999)
                val department = Department(
                    id = departmentId,
                    facultyId = 1L,
                    name = name
                )
                firestoreRepository.addDepartment(department)
                Toast.makeText(requireContext(), "Department added to Firestore", Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
