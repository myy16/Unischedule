package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.FragmentBaseResourceListBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.AdminViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class BaseResourceListFragment : Fragment() {

    private var _binding: FragmentBaseResourceListBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: AdminViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }
    
    private var type: Int = 0
    private lateinit var resourceAdapter: ResourceAdapter

    companion object {
        fun newInstance(type: Int) = BaseResourceListFragment().apply {
            arguments = Bundle().apply { putInt("type", type) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        type = arguments?.getInt("type") ?: 0
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentBaseResourceListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        resourceAdapter = ResourceAdapter()
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = resourceAdapter
        }

        binding.fabAdd.setOnClickListener {
            when (type) {
                0 -> AddFacultyBottomSheet().show(childFragmentManager, "AddFaculty")
                1 -> AddDepartmentBottomSheet().show(childFragmentManager, "AddDepartment")
                2 -> AddInstructorBottomSheet().show(childFragmentManager, "AddInstructor")
                3 -> { /* Classroom dialog can be added if needed */ }
            }
        }

        observeData()
    }

    private fun observeData() {
        val stateFlow = when (type) {
            0 -> viewModel.facultiesState
            1 -> viewModel.departmentsState
            2 -> viewModel.instructorsState
            3 -> viewModel.classroomsState
            else -> null
        }

        stateFlow?.let { flow ->
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    flow.collect { state ->
                        handleUiState(state)
                    }
                }
            }
        }
    }

    private fun <T> handleUiState(state: UiState<List<T>>) {
        when (state) {
            is UiState.Loading -> {
                // Show loading indicator
            }
            is UiState.Success -> {
                val uiItems = state.data.map { item ->
                    when (item) {
                        is com.example.unischedule.data.entity.Faculty -> ResourceItem(item.name, "ID: ${item.id}")
                        is com.example.unischedule.data.entity.Department -> ResourceItem(item.name, "Faculty ID: ${item.facultyId}")
                        is com.example.unischedule.data.entity.Instructor -> ResourceItem(item.name, "${item.title} - ${item.email}")
                        is com.example.unischedule.data.entity.Classroom -> {
                            val typeStr = if (item.isLab) "LAB" else "THEORY"
                            ResourceItem(item.name, "Capacity: ${item.capacity} | Type: $typeStr")
                        }
                        else -> ResourceItem("Unknown", "")
                    }
                }
                resourceAdapter.updateItems(uiItems)
            }
            is UiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            }
            else -> Unit
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
