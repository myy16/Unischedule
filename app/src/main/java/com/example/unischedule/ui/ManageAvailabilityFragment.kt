package com.example.unischedule.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
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
import com.example.unischedule.viewmodel.UserViewModel
import com.example.unischedule.viewmodel.UserViewModelFactory
import com.example.unischedule.viewmodel.UserRole
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class ManageAvailabilityFragment : Fragment() {
    private var _binding: FragmentAvailabilityBinding? = null
    protected val binding get() = _binding!!

    private val instructorViewModel: FirestoreInstructorViewModel by viewModels {
        FirestoreInstructorViewModelFactory.create(FirebaseFirestore.getInstance())
    }

    private val userViewModel: UserViewModel by viewModels {
        UserViewModelFactory(com.example.unischedule.data.repository.FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    private var retryJob: Job? = null
    private lateinit var adapter: AvailabilityAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val instructorId = UserSession.userId ?: return
        userViewModel.observeCurrentUserStatus(instructorId.toString())
        instructorViewModel.loadMyAvailability(instructorId)

        adapter = AvailabilityAdapter { day, time, endTime ->
            instructorViewModel.toggleAvailability(instructorId, day, time, endTime)
        }

        binding.availabilityRecyclerView.adapter = adapter
        binding.availabilityRecyclerView.setHasFixedSize(true)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                binding.availabilityRecyclerView.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 5)

                launch {
                    instructorViewModel.availabilityState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                adapter.updateData(state.data)
                                binding.availabilityStatusButton.isVisible = true
                            }
                            is UiState.Error -> showRetryMessage(state.message)
                            else -> Unit
                        }
                    }
                }

                launch {
                    userViewModel.statusState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val status = state.data.ifBlank { "Available" }
                                binding.availabilityStatusButton.text = status
                                applyStatusColors(status)
                            }
                            is UiState.Error -> showRetryMessage(state.message)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    protected fun applyStatusColors(status: String) {
        val button = binding.availabilityStatusButton
        val isAvailable = status.equals("Available", ignoreCase = true)
        val backgroundColor = if (isAvailable) {
            MaterialColors.getColor(button, com.google.android.material.R.attr.colorPrimary, 0xFF2E7D32.toInt())
        } else {
            MaterialColors.getColor(button, com.google.android.material.R.attr.colorError, 0xFFC62828.toInt())
        }
        val textColor = MaterialColors.getColor(button, com.google.android.material.R.attr.colorOnPrimary, 0xFFFFFFFF.toInt())
        button.backgroundTintList = ColorStateList.valueOf(backgroundColor)
        button.setTextColor(textColor)
    }

    protected fun showRetryMessage(message: String) {
        Snackbar.make(binding.root, "Connection Error: Retrying...", Snackbar.LENGTH_SHORT).show()
        retryJob?.cancel()
        retryJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(2000)
            val instructorId = UserSession.userId ?: return@launch
            instructorViewModel.loadMyAvailability(instructorId)
            userViewModel.observeCurrentUserStatus(instructorId.toString())
        }
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        retryJob?.cancel()
        _binding = null
    }
}
