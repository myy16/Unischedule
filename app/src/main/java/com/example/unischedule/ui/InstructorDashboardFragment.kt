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
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.R
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentInstructorDashboardBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.InstructorViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class InstructorDashboardFragment : Fragment() {
    private var _binding: FragmentInstructorDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstructorViewModel by viewModels {
        val database = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(database.universityDao()))
    }

    private lateinit var scheduleAdapter: ScheduleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInstructorDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val instructorId = UserSession.userId ?: return
        binding.welcomeText.text = "Welcome, ${UserSession.userName ?: "Instructor"}"
        
        viewModel.loadMySchedule(instructorId)

        scheduleAdapter = ScheduleAdapter { 
            // Optional: Click on schedule item
        }

        binding.myScheduleRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.myScheduleRecyclerView.adapter = scheduleAdapter

        binding.btnManageAvailability.setOnClickListener {
            findNavController().navigate(R.id.action_instructorDashboardFragment_to_availabilityFragment)
        }

        binding.btnLogout.setOnClickListener {
            UserSession.logout()
            findNavController().navigate(R.id.loginFragment)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scheduleState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            val schedules = state.data
                            if (schedules.isEmpty()) {
                                binding.noClassesText.visibility = View.VISIBLE
                                binding.myScheduleRecyclerView.visibility = View.GONE
                            } else {
                                binding.noClassesText.visibility = View.GONE
                                binding.myScheduleRecyclerView.visibility = View.VISIBLE
                                scheduleAdapter.updateItems(schedules)
                            }
                        }
                        is UiState.Loading -> {
                            // Show loading
                        }
                        is UiState.Error -> {
                            // Show error
                        }
                        else -> Unit
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
