package com.example.unischedule.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.entity.ScheduleWithDetails
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.FragmentSchedulerBinding
import com.example.unischedule.util.ExcelHelper
import com.example.unischedule.viewmodel.ScheduleViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SchedulerFragment : Fragment() {

    private var _binding: FragmentSchedulerBinding? = null
    private val binding get() = _binding!!
    private val TAG = "SchedulerLifecycle"

    private val viewModel: ScheduleViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }

    private lateinit var scheduleAdapter: ScheduleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSchedulerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        scheduleAdapter = ScheduleAdapter { scheduleWithDetails ->
            showScheduleOptions(scheduleWithDetails)
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

        lifecycleScope.launch {
            viewModel.allSchedulesWithDetails.collectLatest { schedules ->
                scheduleAdapter.updateItems(schedules)
            }
        }
    }

    private fun showScheduleOptions(item: ScheduleWithDetails) {
        val options = arrayOf("Edit (Not Implemented)", "Delete")
        AlertDialog.Builder(requireContext())
            .setTitle("${item.course.code}: ${item.course.name}")
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> Toast.makeText(context, "Edit feature coming soon", Toast.LENGTH_SHORT).show()
                    1 -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun confirmDelete(item: ScheduleWithDetails) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete this class entry?")
            .setPositiveButton("Delete") { _, _ ->
                viewModel.deleteSchedule(item.schedule)
                Toast.makeText(context, "Schedule deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportAndShare() {
        lifecycleScope.launch {
            val schedules = viewModel.allSchedules.first()
            if (schedules.isEmpty()) {
                Toast.makeText(context, "No schedules to export", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val uri = ExcelHelper.exportScheduleToExcel(requireContext(), schedules)
            if (uri != null) {
                ExcelHelper.shareExcelFile(requireContext(), uri)
            } else {
                Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
