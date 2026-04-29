package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unischedule.R
import com.example.unischedule.data.firestore.InstructorAvailability
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentCalendarBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreLecturerCalendarViewModel
import com.example.unischedule.viewmodel.FirestoreLecturerCalendarViewModelFactory
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

/**
 * Task 5: CalendarFragment for Lecturers.
 * Displays weekly grid (Mon-Fri) showing courses assigned to the logged-in lecturer.
 * Uses repeatOnLifecycle(STARTED) to collect real-time schedule updates.
 * Sustainable coding: lifecycle-safe, non-blocking, clean resource management.
 */
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreLecturerCalendarViewModel by viewModels {
        val lecturerId = UserSession.userId ?: 0L
        FirestoreLecturerCalendarViewModelFactory(
            FirestoreRepository(FirebaseFirestore.getInstance()),
            lecturerId
        )
    }

    // Time slots: 9:00 AM to 5:00 PM (9 slots of 1 hour each)
    private val timeSlots = listOf(
        "09:00", "10:00", "11:00", "12:00", "13:00", "14:00", "15:00", "16:00", "17:00"
    )

    private var currentAvailability: List<InstructorAvailability> = emptyList()

    private val daysOfWeek = listOf(
        Pair(1, "Mon"),
        Pair(2, "Tue"),
        Pair(3, "Wed"),
        Pair(4, "Thu"),
        Pair(5, "Fri")
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Build static calendar grid (time slots and day headers)
        buildCalendarGrid()

        // Collect schedule data and populate grid
        observeScheduleUpdates()
        observeAvailabilityUpdates()
    }

    /**
     * Builds the static calendar structure: time slot column and day columns.
     */
    private fun buildCalendarGrid() {
        // Build time slot column
        buildTimeSlotColumn()

        // Build day columns
        buildDayColumns()
    }

    /**
     * Builds the left-side time slot column (9:00 to 17:00).
     */
    private fun buildTimeSlotColumn() {
        val timeSlotsList = binding.timeSlotsList
        timeSlotsList.removeAllViews()

        for (timeSlot in timeSlots) {
            val timeView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    80
                ).apply {
                    setMargins(0, 0, 0, 1)
                }
                text = timeSlot
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.grid_border)
            }
            timeSlotsList.addView(timeView)
        }
    }

    /**
     * Builds day columns (Mon-Fri) with empty cells for each time slot.
     */
    private fun buildDayColumns() {
        val dayColumnsContainer = binding.dayColumnsContainer
        dayColumnsContainer.removeAllViews()

        for ((dayNum, dayLabel) in daysOfWeek) {
            // Create column for each day
            val dayColumn = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    120,
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(1, 0, 1, 0)
                }
                orientation = LinearLayout.VERTICAL
                tag = "day_$dayNum"
            }

            // Day header
            val dayHeader = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    40
                )
                text = dayLabel
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(android.graphics.Color.LTGRAY)
            }
            dayColumn.addView(dayHeader)

            // Add empty cells for each time slot
            for (timeSlot in timeSlots) {
                val cellView = LinearLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        80
                    ).apply {
                        setMargins(0, 0, 0, 1)
                    }
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.grid_border)
                    tag = "cell_${dayNum}_${timeSlot}"
                    setPadding(4, 4, 4, 4)
                }
                dayColumn.addView(cellView)
            }

            dayColumnsContainer.addView(dayColumn)
        }
    }

    /**
     * Task 5: Observes real-time schedule updates using repeatOnLifecycle(STARTED).
     * Sustainable coding: lifecycle-safe collection ensures no leaks when fragment is paused/destroyed.
     */
    private fun observeScheduleUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.lecturerScheduleState.collect { state ->
                    when (state) {
                        is UiState.Loading -> {
                            binding.loadingProgress.visibility = View.VISIBLE
                            binding.errorText.visibility = View.GONE
                            binding.calendarScrollView.visibility = View.GONE
                            binding.noClassesText.visibility = View.GONE
                        }
                        is UiState.Success -> {
                            binding.loadingProgress.visibility = View.GONE
                            binding.errorText.visibility = View.GONE

                            if (state.data.isEmpty()) {
                                binding.calendarScrollView.visibility = View.GONE
                                binding.noClassesText.visibility = View.VISIBLE
                            } else {
                                binding.calendarScrollView.visibility = View.VISIBLE
                                binding.noClassesText.visibility = View.GONE
                                populateCalendar(state.data)
                            }
                        }
                        is UiState.Error -> {
                            binding.loadingProgress.visibility = View.GONE
                            binding.calendarScrollView.visibility = View.GONE
                            binding.noClassesText.visibility = View.GONE
                            binding.errorText.visibility = View.VISIBLE
                            binding.errorText.text = "Error: ${state.message}"
                            showRetrySnack()
                        }
                    }
                }
            }
        }
    }

    private fun observeAvailabilityUpdates() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.availabilityState.collect { state ->
                    when (state) {
                        is UiState.Success -> {
                            currentAvailability = state.data
                            refreshCalendarAvailability()
                        }
                        is UiState.Error -> {
                            showRetrySnack()
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    /**
     * Populates calendar cells with course information from schedule entries.
     * Maps each ScheduleEntry to the correct grid cell based on day of week and time slot.
     */
    private fun populateCalendar(schedules: List<ScheduleEntry>) {
        // Clear previous course displays from all cells
        clearCoursesFromCells()

        // Place each schedule entry in its grid cell
        for (schedule in schedules) {
            val dayNum = schedule.dayOfWeek
            val startTime = schedule.startTime

            // Find the cell for this schedule
            val cellTag = "cell_${dayNum}_${startTime}"
            val cell = binding.dayColumnsContainer.findViewWithTag<LinearLayout>(cellTag) ?: continue

            // Clear cell and add course card
            cell.removeAllViews()

            val courseCard = createCourseCard(schedule)
            cell.addView(courseCard)

            // Mark schedule cell as busy/occupied when availability does not include this slot.
            val isAvailable = currentAvailability.any { it.dayOfWeek == dayNum && it.startTime == startTime }
            cell.setBackgroundColor(
                MaterialColors.getColor(
                    cell,
                    if (isAvailable) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorErrorContainer,
                    if (isAvailable) Color.parseColor("#D9EAD3") else Color.parseColor("#F4CCCC")
                )
            )
        }
    }

    private fun refreshCalendarAvailability() {
        for ((dayNum, _) in daysOfWeek) {
            for (timeSlot in timeSlots) {
                val cellTag = "cell_${dayNum}_${timeSlot}"
                val cell = binding.dayColumnsContainer.findViewWithTag<LinearLayout>(cellTag) ?: continue
                val hasAvailability = currentAvailability.any { it.dayOfWeek == dayNum && it.startTime == timeSlot }
                if (cell.childCount == 0) {
                    cell.setBackgroundColor(
                        MaterialColors.getColor(
                            cell,
                            if (hasAvailability) com.google.android.material.R.attr.colorPrimaryContainer else com.google.android.material.R.attr.colorErrorContainer,
                            if (hasAvailability) Color.parseColor("#D9EAD3") else Color.parseColor("#F4CCCC")
                        )
                    )
                }
            }
        }
    }

    /**
     * Clears all course information from calendar cells (resets to empty state).
     */
    private fun clearCoursesFromCells() {
        for ((dayNum, _) in daysOfWeek) {
            for (timeSlot in timeSlots) {
                val cellTag = "cell_${dayNum}_${timeSlot}"
                val cell = binding.dayColumnsContainer.findViewWithTag<LinearLayout>(cellTag) ?: continue
                cell.removeAllViews()
            }
        }
    }

    /**
     * Creates a course card view to display in a calendar cell.
     * Shows course code, classroom, and time range.
     */
    private fun createCourseCard(schedule: ScheduleEntry): View {
        val cardView = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#E3F2FD"))
            setPadding(4, 4, 4, 4)
        }

        // Course code
        val courseCodeView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Course ${schedule.courseId}"
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.DKGRAY)
        }
        cardView.addView(courseCodeView)

        // Classroom
        val classroomView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Room ${schedule.classroomId}"
            textSize = 9f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
        }
        cardView.addView(classroomView)

        // Time range
        val timeView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "${schedule.startTime}-${schedule.endTime}"
            textSize = 8f
            gravity = android.view.Gravity.CENTER
            setTextColor(android.graphics.Color.GRAY)
        }
        cardView.addView(timeView)

        return cardView
    }

    private fun showRetrySnack() {
        Snackbar.make(binding.root, "Connection Error: Retrying...", Snackbar.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
