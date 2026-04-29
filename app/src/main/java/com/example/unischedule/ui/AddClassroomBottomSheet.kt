package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.BottomSheetAddClassroomBinding
import com.example.unischedule.viewmodel.FirestoreAdminViewModel
import com.example.unischedule.viewmodel.FirestoreAdminViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddClassroomBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddClassroomBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreAdminViewModel by viewModels {
        FirestoreAdminViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddClassroomBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()
            val capacityStr = binding.capacityEditText.text.toString().trim()
            val isLab = binding.isLabSwitch.isChecked

            if (name.isEmpty() || capacityStr.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            try {
                val classroom = Classroom(
                    id = 0, // ID will be assigned by viewModel sequentially
                    name = name,
                    capacity = capacityStr.toInt(),
                    isLab = isLab,
                    isAvailable = true
                )
                viewModel.addClassroom(classroom)
                Toast.makeText(requireContext(), "Classroom added successfully", Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error adding classroom: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
