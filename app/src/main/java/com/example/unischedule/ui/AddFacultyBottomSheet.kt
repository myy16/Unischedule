package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.BottomSheetAddFacultyBinding
import com.example.unischedule.viewmodel.FirestoreAdminViewModel
import com.example.unischedule.viewmodel.FirestoreAdminViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AddFacultyBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddFacultyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreAdminViewModel by viewModels {
        FirestoreAdminViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddFacultyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.saveButton.setOnClickListener {
            val name = binding.nameEditText.text.toString().trim()

            if (name.isEmpty()) {
                binding.nameLayout.error = "Faculty name cannot be empty"
                return@setOnClickListener
            }

            try {
                viewModel.addFaculty(name)
                Toast.makeText(requireContext(), "Faculty added successfully", Toast.LENGTH_SHORT).show()
                dismiss()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Error adding faculty: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
