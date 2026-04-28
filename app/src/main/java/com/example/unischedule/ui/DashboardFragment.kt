package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.FragmentDashboardBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreDashboardViewModel
import com.example.unischedule.viewmodel.FirestoreDashboardViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreDashboardViewModel by viewModels {
        FirestoreDashboardViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        observeStats()
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.unassignedLecturersState.collect { state ->
                        when (state) {
                            is UiState.Success -> binding.unassignedLecturersCountText.text = state.data.toString()
                            is UiState.Error -> binding.unassignedLecturersCountText.text = "!"
                            is UiState.Loading -> binding.unassignedLecturersCountText.text = "--"
                        }
                    }
                }
                launch {
                    viewModel.unassignedCoursesState.collect { state ->
                        when (state) {
                            is UiState.Success -> binding.unassignedCoursesCountText.text = state.data.toString()
                            is UiState.Error -> binding.unassignedCoursesCountText.text = "!"
                            is UiState.Loading -> binding.unassignedCoursesCountText.text = "--"
                        }
                    }
                }
                launch {
                    viewModel.availableClassroomsState.collect { state ->
                        when (state) {
                            is UiState.Success -> binding.availableClassroomsCountText.text = state.data.toString()
                            is UiState.Error -> binding.availableClassroomsCountText.text = "!"
                            is UiState.Loading -> binding.availableClassroomsCountText.text = "--"
                        }
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
