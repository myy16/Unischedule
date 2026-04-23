package com.example.unischedule.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.unischedule.R
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.FragmentDashboardBinding
import com.example.unischedule.viewmodel.AdminViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val TAG = "DashboardLifecycle"

    private val viewModel: AdminViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupStatCards()
        observeStats()
    }

    private fun setupStatCards() {
        binding.cardFaculties.setOnClickListener {
            val bundle = Bundle().apply { putInt("selectedTab", 0) }
            findNavController().navigate(R.id.nav_resources, bundle)
        }
        binding.cardDepartments.setOnClickListener {
            val bundle = Bundle().apply { putInt("selectedTab", 1) }
            findNavController().navigate(R.id.nav_resources, bundle)
        }
        binding.cardInstructors.setOnClickListener {
            val bundle = Bundle().apply { putInt("selectedTab", 2) }
            findNavController().navigate(R.id.nav_resources, bundle)
        }
        binding.cardCourses.setOnClickListener {
            findNavController().navigate(R.id.nav_courses)
        }
    }

    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.faculties.collectLatest {
                binding.facultyCountText.text = it.size.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAllDepartments().collectLatest {
                binding.deptCountText.text = it.size.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAllCourses().collectLatest {
                binding.courseCountText.text = it.size.toString()
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getAllInstructors().collectLatest {
                binding.instructorCountText.text = it.size.toString()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
