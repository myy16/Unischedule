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
import androidx.recyclerview.widget.GridLayoutManager
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentAvailabilityBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.InstructorViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import kotlinx.coroutines.launch

class AvailabilityFragment : Fragment() {
    private var _binding: FragmentAvailabilityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: InstructorViewModel by viewModels {
        val database = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(database.universityDao()))
    }

    private lateinit var adapter: AvailabilityAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val instructorId = UserSession.userId ?: return
        viewModel.loadMyAvailability(instructorId)

        adapter = AvailabilityAdapter { day, time ->
            viewModel.toggleAvailability(instructorId, day, time)
        }

        binding.availabilityRecyclerView.layoutManager = GridLayoutManager(requireContext(), 5)
        binding.availabilityRecyclerView.adapter = adapter
        binding.availabilityRecyclerView.setHasFixedSize(true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.availabilityState.collect { state ->
                    if (state is UiState.Success) {
                        adapter.updateData(state.data)
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
