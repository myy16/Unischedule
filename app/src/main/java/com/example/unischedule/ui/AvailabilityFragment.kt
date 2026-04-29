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
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentAvailabilityBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreInstructorViewModel
import com.example.unischedule.viewmodel.FirestoreInstructorViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class AvailabilityFragment : Fragment() {
    private var _binding: FragmentAvailabilityBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreInstructorViewModel by viewModels {
        FirestoreInstructorViewModelFactory.create(FirebaseFirestore.getInstance())
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

        adapter = AvailabilityAdapter { day, time, endTime ->
            viewModel.toggleAvailability(instructorId, day, time, endTime)
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
