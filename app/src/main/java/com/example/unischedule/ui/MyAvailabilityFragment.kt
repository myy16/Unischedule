package com.example.unischedule.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentAvailabilityBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreInstructorViewModel
import com.example.unischedule.viewmodel.FirestoreInstructorViewModelFactory
import com.google.android.material.color.MaterialColors
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

open class MyAvailabilityFragment : Fragment() {
    private var _binding: FragmentAvailabilityBinding? = null
    protected val binding get() = _binding!!

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

        binding.availabilityRecyclerView.adapter = adapter
        binding.availabilityRecyclerView.setHasFixedSize(true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    binding.availabilityRecyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 5)
                }

                launch {
                    viewModel.availabilityState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                adapter.updateData(state.data)
                                binding.availabilityStatusButton.isVisible = true
                            }
                            is UiState.Error -> {
                                binding.availabilityStatusButton.isVisible = true
                                binding.availabilityStatusButton.text = "Busy"
                                applyStatusColors(false)
                            }
                            else -> Unit
                        }
                    }
                }

                launch {
                    observeAvailabilityStatus(instructorId).collect { available ->
                        binding.availabilityStatusButton.text = if (available) "Available" else "Busy"
                        applyStatusColors(available)
                    }
                }
            }
        }
    }

    private fun observeAvailabilityStatus(instructorId: Long): Flow<Boolean> = callbackFlow {
        val registration = FirebaseFirestore.getInstance()
            .collection("availability_status")
            .document(instructorId.toString())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val isAvailable = snapshot?.getBoolean("isAvailable") ?: false
                trySend(isAvailable)
            }

        awaitClose { registration.remove() }
    }

    private fun applyStatusColors(isAvailable: Boolean) {
        val button = binding.availabilityStatusButton
        val color = if (isAvailable) {
            ColorStateList.valueOf(MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimary))
        } else {
            ColorStateList.valueOf(MaterialColors.getColor(button, com.google.android.material.R.attr.colorError))
        }
        button.backgroundTintList = color
        button.setTextColor(MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimary))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
