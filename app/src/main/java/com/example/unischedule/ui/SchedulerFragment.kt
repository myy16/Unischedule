package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.databinding.FragmentSchedulerBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreScheduleListViewModel
import com.example.unischedule.viewmodel.FirestoreScheduleListViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class SchedulerFragment : Fragment() {

    private var _binding: FragmentSchedulerBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreScheduleListViewModel by viewModels {
        FirestoreScheduleListViewModelFactory.create(FirebaseFirestore.getInstance())
    }

    private lateinit var scheduleAdapter: ScheduleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSchedulerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleAdapter = ScheduleAdapter { scheduleEntry ->
            showScheduleOptions(scheduleEntry)
        }
        
        binding.schedulerRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scheduleAdapter
        }
        
        binding.fabAddSchedule.setOnClickListener {
            AddScheduleBottomSheet().show(childFragmentManager, "AddSchedule")
        }

        binding.btnExportShare.setOnClickListener {
            exportAndShare()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.scheduleState.collect { state ->
                    if (state is UiState.Success) {
                        scheduleAdapter.updateItems(state.data)
                    }
                }
            }
        }
    }

    private fun showScheduleOptions(item: ScheduleEntry) {
        val options = arrayOf("Edit (Not Implemented)", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle("Schedule #${item.id}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(context, "Edit feature coming soon", Toast.LENGTH_SHORT).show()
                    1 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun confirmDelete(item: ScheduleEntry) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete this class entry?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSchedule(item.id.toString())
                Toast.makeText(context, "Schedule deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportAndShare() {
        // Logic for export can be updated to use UiState or specific flow
        Toast.makeText(context, "Exporting...", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
