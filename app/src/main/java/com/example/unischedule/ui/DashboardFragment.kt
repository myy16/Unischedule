package com.example.unischedule.ui

import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.unischedule.R
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Lecturer
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

    private var isLecturersExpanded = false
    private var isCoursesExpanded = false
    private var isClassroomsExpanded = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expandable panel toggles
        binding.cardWarningLecturers.setOnClickListener {
            isLecturersExpanded = !isLecturersExpanded
            binding.listWarningLecturers.visibility = if (isLecturersExpanded) View.VISIBLE else View.GONE
            binding.arrowWarningLecturers.animate().rotation(if (isLecturersExpanded) 180f else 0f).setDuration(200).start()
        }

        binding.cardWarningCourses.setOnClickListener {
            isCoursesExpanded = !isCoursesExpanded
            binding.listWarningCourses.visibility = if (isCoursesExpanded) View.VISIBLE else View.GONE
            binding.arrowWarningCourses.animate().rotation(if (isCoursesExpanded) 180f else 0f).setDuration(200).start()
        }

        binding.cardWarningClassrooms.setOnClickListener {
            isClassroomsExpanded = !isClassroomsExpanded
            binding.listWarningClassrooms.visibility = if (isClassroomsExpanded) View.VISIBLE else View.GONE
            binding.arrowWarningClassrooms.animate().rotation(if (isClassroomsExpanded) 180f else 0f).setDuration(200).start()
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
                                val count = state.data.size
                                if (count > 0) {
                                    binding.cardWarningLecturers.visibility = View.VISIBLE
                                    binding.warningLecturersText.text =
                                        "$count lecturer${if (count != 1) "s" else ""} ha${if (count != 1) "ve" else "s"} no assigned courses"
                                    
                                    // Collapse on update and populate
                                    isLecturersExpanded = false
                                    binding.listWarningLecturers.visibility = View.GONE
                                    binding.arrowWarningLecturers.rotation = 0f
                                    populateLecturersList(state.data)
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
                                val count = state.data.size
                                if (count > 0) {
                                    binding.cardWarningCourses.visibility = View.VISIBLE
                                    binding.warningCoursesText.text =
                                        "$count course${if (count != 1) "s" else ""} ha${if (count != 1) "ve" else "s"} no assigned classroom"

                                    isCoursesExpanded = false
                                    binding.listWarningCourses.visibility = View.GONE
                                    binding.arrowWarningCourses.rotation = 0f
                                    populateCoursesList(state.data)
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
                                val count = state.data.size
                                if (count > 0) {
                                    binding.cardWarningClassrooms.visibility = View.VISIBLE
                                    binding.warningClassroomsText.text =
                                        "$count classroom${if (count != 1) "s" else ""} fully booked this week"

                                    isClassroomsExpanded = false
                                    binding.listWarningClassrooms.visibility = View.GONE
                                    binding.arrowWarningClassrooms.rotation = 0f
                                    populateClassroomsList(state.data)
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

    private fun populateLecturersList(lecturers: List<Lecturer>) {
        val container = binding.listWarningLecturers
        container.removeAllViews()
        for (lecturer in lecturers) {
            val departmentName = viewModel.departmentMap[lecturer.departmentId] ?: "Department #${lecturer.departmentId}"
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val titleView = TextView(requireContext()).apply {
                text = "• ${lecturer.fullName}"
                textSize = 14f
                setTextColor(resources.getColor(R.color.dash_on_surface, null))
            }
            val deptView = TextView(requireContext()).apply {
                text = departmentName
                textSize = 12f
                setTextColor(resources.getColor(R.color.dash_subtitle, null))
                setPadding(dp(10), 0, 0, 0)
            }
            row.addView(titleView)
            row.addView(deptView)
            container.addView(row)
        }
    }

    private fun populateCoursesList(courses: List<Course>) {
        val container = binding.listWarningCourses
        container.removeAllViews()
        for (course in courses) {
            val departmentName = viewModel.departmentMap[course.departmentId] ?: "Department #${course.departmentId}"
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val titleView = TextView(requireContext()).apply {
                text = Html.fromHtml("&#8226; <b>${course.code}</b> — ${course.name}", Html.FROM_HTML_MODE_COMPACT)
                textSize = 14f
                setTextColor(resources.getColor(R.color.dash_on_surface, null))
            }
            val deptView = TextView(requireContext()).apply {
                text = departmentName
                textSize = 12f
                setTextColor(resources.getColor(R.color.dash_subtitle, null))
                setPadding(dp(10), 0, 0, 0)
            }
            row.addView(titleView)
            row.addView(deptView)
            container.addView(row)
        }
    }

    private fun populateClassroomsList(classrooms: List<FirestoreDashboardViewModel.FullyBookedClassroom>) {
        val container = binding.listWarningClassrooms
        container.removeAllViews()
        for (item in classrooms) {
            val classroom = viewModel.classroomMap[item.classroomId]
            val roomName = classroom?.name ?: "Room #${item.classroomId}"
            val roomType = if (classroom?.isLab == true) "Laboratory" else "Classroom"
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val titleView = TextView(requireContext()).apply {
                text = "• $roomName"
                textSize = 14f
                setTextColor(resources.getColor(R.color.dash_on_surface, null))
            }
            val detailsView = TextView(requireContext()).apply {
                text = "$roomType — ${item.slotsFilled}/20 slots filled"
                textSize = 12f
                setTextColor(resources.getColor(R.color.dash_subtitle, null))
                setPadding(dp(10), 0, 0, 0)
            }
            row.addView(titleView)
            row.addView(detailsView)
            container.addView(row)
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
