package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentAvailabilityBinding
import com.example.unischedule.viewmodel.InstructorViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
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

        adapter = AvailabilityAdapter { day, time ->
            viewModel.toggleAvailability(instructorId, day, time)
        }

        // 5 columns for Mon, Tue, Wed, Thu, Fri
        val layoutManager = GridLayoutManager(requireContext(), 5)
        binding.availabilityRecyclerView.layoutManager = layoutManager
        binding.availabilityRecyclerView.adapter = adapter
        
        // Disable nested scrolling if needed, but let's keep it simple
        binding.availabilityRecyclerView.setHasFixedSize(true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getMyAvailability(instructorId).collectLatest {
                adapter.updateData(it)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
