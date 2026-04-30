package com.example.unischedule.ui

import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unischedule.R
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.FragmentDashboardBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreDashboardViewModel
import com.example.unischedule.viewmodel.FirestoreDashboardViewModelFactory
import com.example.unischedule.viewmodel.RecentActivityItem
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class DashboardFragment : Fragment() {
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreDashboardViewModel by viewModels {
        FirestoreDashboardViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Warning panel taps
        binding.cardWarningLecturers.setOnClickListener {
            // Navigate to Resource Management → Instructors tab (index 2)
            findNavController().navigate(R.id.nav_resources, bundleOf("selectedTab" to 2))
        }
        binding.cardWarningCourses.setOnClickListener {
            findNavController().navigate(R.id.assignmentFragment)
        }

        observeStats()
        observeWarnings()
        observeRecentActivity()
    }

    // ── SECTION 1: Stats Cards ───────────────────────────────────────────
    private fun observeStats() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.totalLecturersState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                binding.statLecturersCount.text = state.data.toString()
                                binding.progressLecturers.visibility = View.GONE
                            }
                            is UiState.Error -> {
                                binding.statLecturersCount.text = "!"
                                binding.progressLecturers.visibility = View.GONE
                            }
                            is UiState.Loading -> {
                                binding.statLecturersCount.text = ""
                                binding.progressLecturers.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                launch {
                    viewModel.totalCoursesState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                binding.statCoursesCount.text = state.data.toString()
                                binding.progressCourses.visibility = View.GONE
                            }
                            is UiState.Error -> {
                                binding.statCoursesCount.text = "!"
                                binding.progressCourses.visibility = View.GONE
                            }
                            is UiState.Loading -> {
                                binding.statCoursesCount.text = ""
                                binding.progressCourses.visibility = View.VISIBLE
                            }
                        }
                    }
                }
                launch {
                    viewModel.totalClassroomsState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                binding.statClassroomsCount.text = state.data.toString()
                                binding.progressClassrooms.visibility = View.GONE
                            }
                            is UiState.Error -> {
                                binding.statClassroomsCount.text = "!"
                                binding.progressClassrooms.visibility = View.GONE
                            }
                            is UiState.Loading -> {
                                binding.statClassroomsCount.text = ""
                                binding.progressClassrooms.visibility = View.VISIBLE
                            }
                        }
                    }
                }
            }
        }
    }

    // ── SECTION 2: Warning Panels ────────────────────────────────────────
    private fun observeWarnings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.unassignedLecturersState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val count = state.data
                                if (count > 0) {
                                    binding.cardWarningLecturers.visibility = View.VISIBLE
                                    binding.warningLecturersText.text =
                                        "$count lecturer${if (count != 1) "s" else ""} ha${if (count != 1) "ve" else "s"} no assigned courses"
                                } else {
                                    binding.cardWarningLecturers.visibility = View.GONE
                                }
                            }
                            else -> binding.cardWarningLecturers.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.unassignedCoursesState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val count = state.data
                                if (count > 0) {
                                    binding.cardWarningCourses.visibility = View.VISIBLE
                                    binding.warningCoursesText.text =
                                        "$count course${if (count != 1) "s" else ""} ha${if (count != 1) "ve" else "s"} no assigned classroom"
                                } else {
                                    binding.cardWarningCourses.visibility = View.GONE
                                }
                            }
                            else -> binding.cardWarningCourses.visibility = View.GONE
                        }
                    }
                }
                launch {
                    viewModel.fullyBookedClassroomsState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val count = state.data
                                if (count > 0) {
                                    binding.cardWarningClassrooms.visibility = View.VISIBLE
                                    binding.warningClassroomsText.text =
                                        "$count classroom${if (count != 1) "s" else ""} fully booked this week"
                                } else {
                                    binding.cardWarningClassrooms.visibility = View.GONE
                                }
                            }
                            else -> binding.cardWarningClassrooms.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    // ── SECTION 3: Recent Activity ───────────────────────────────────────
    private fun observeRecentActivity() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.recentActivityState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            binding.progressActivity.visibility = View.GONE
                            val items = state.data
                            if (items.isEmpty()) {
                                binding.emptyActivityText.visibility = View.VISIBLE
                                binding.recentActivityList.visibility = View.GONE
                            } else {
                                binding.emptyActivityText.visibility = View.GONE
                                binding.recentActivityList.visibility = View.VISIBLE
                                populateRecentActivity(items)
                            }
                        }
                        is UiState.Error -> {
                            binding.progressActivity.visibility = View.GONE
                            binding.emptyActivityText.visibility = View.VISIBLE
                            binding.emptyActivityText.text = "Failed to load activity"
                        }
                        is UiState.Loading -> {
                            binding.progressActivity.visibility = View.VISIBLE
                            binding.emptyActivityText.visibility = View.GONE
                            binding.recentActivityList.visibility = View.GONE
                        }
                    }
                }
            }
        }
    }

    private fun populateRecentActivity(items: List<RecentActivityItem>) {
        val container = binding.recentActivityList
        container.removeAllViews()

        items.forEachIndexed { index, item ->
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(10), 0, dp(10))
            }

            // Left dot indicator
            val dot = TextView(requireContext()).apply {
                text = "●"
                textSize = 10f
                setTextColor(resources.getColor(R.color.dash_activity_icon, null))
                setPadding(0, 0, dp(10), 0)
            }
            row.addView(dot)

            // Activity text
            val text = TextView(requireContext()).apply {
                val line = "${item.courseCode} → ${item.lecturerName} → ${item.classroomName} — ${item.dayLabel} ${item.startTime}"
                this.text = line
                textSize = 13f
                setTextColor(resources.getColor(R.color.dash_on_surface, null))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            row.addView(text)

            container.addView(row)

            // Divider (not after last)
            if (index < items.size - 1) {
                val divider = View(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    ).apply {
                        setMargins(dp(20), 0, 0, 0)
                    }
                    setBackgroundColor(0xFFE5E7EB.toInt())
                }
                container.addView(divider)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
