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
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
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
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // Time slots matching the actual scheduling slots
    private val timeSlots = listOf(
        "08:00-10:00", "10:00-12:00", "13:00-15:00", "15:00-17:00"
    )

    private var currentAvailability: List<InstructorAvailability> = emptyList()
    private var courseMap: Map<Long, Course> = emptyMap()
    private var classroomMap: Map<Long, Classroom> = emptyMap()

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
        setupLegend()

        // Collect schedule data and populate grid
        observeScheduleUpdates()
        observeAvailabilityUpdates()
        observeLookupData()
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
     * Builds the left-side time slot column.
     */
    private fun buildTimeSlotColumn() {
        val timeSlotsList = binding.timeSlotsList
        timeSlotsList.removeAllViews()

        for (timeSlot in timeSlots) {
            val timeView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(100) // Increased height for 2-hour blocks
                ).apply {
                    setMargins(0, 0, 0, dpToPx(1))
                }
                text = timeSlot.replace("-", "\n")
                textSize = 12f
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
                    dpToPx(120), // Wide enough for text
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(dpToPx(1), 0, dpToPx(1), 0)
                }
                orientation = LinearLayout.VERTICAL
                tag = "day_$dayNum"
            }

            // Day header (matches the XML 'Time' header's 40dp height)
            val dayHeader = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dpToPx(40)
                )
                text = dayLabel
                textSize = 14f
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
                        dpToPx(100) // Matches time slot height
                    ).apply {
                        setMargins(0, 0, 0, dpToPx(1))
                    }
                    orientation = LinearLayout.VERTICAL
                    setBackgroundResource(R.drawable.grid_border)
                    tag = "cell_${dayNum}_${timeSlot}"
                    setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                }
                dayColumn.addView(cellView)
            }

            dayColumnsContainer.addView(dayColumn)
        }
    }

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
                            refreshCalendarColors()
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

    private fun observeLookupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.courseMap.collect { map ->
                        courseMap = map
                    }
                }
                launch {
                    viewModel.classroomMap.collect { map ->
                        classroomMap = map
                    }
                }
            }
        }
    }

    private fun populateCalendar(schedules: List<ScheduleEntry>) {
        // Clear previous course displays from all cells
        clearCoursesFromCells()

        // Place each schedule entry in its grid cell
        for (schedule in schedules) {
            val dayNum = schedule.dayOfWeek
            val slotString = "${schedule.startTime}-${schedule.endTime}"
            val cellTag = "cell_${dayNum}_${slotString}"
            val cell = binding.dayColumnsContainer.findViewWithTag<LinearLayout>(cellTag)

            if (cell != null) {
                cell.removeAllViews()
                val courseCard = createCourseCard(schedule)
                cell.addView(courseCard)
            }
        }
        
        refreshCalendarColors()
    }

    private fun refreshCalendarColors() {
        for ((dayNum, _) in daysOfWeek) {
            for (timeSlot in timeSlots) {
                val cellTag = "cell_${dayNum}_${timeSlot}"
                val cell = binding.dayColumnsContainer.findViewWithTag<LinearLayout>(cellTag) ?: continue
                
                val parts = timeSlot.split("-")
                val startTime = if (parts.size == 2) parts[0] else timeSlot
                val hasAvailability = currentAvailability.any { it.dayOfWeek == dayNum && it.startTime == startTime }
                
                val hasCourse = cell.childCount > 0

                // ISSUE 2: Color coding
                // Blue (#E3F2FD) for available (no course)
                // Pink/Red (#FFEBEE) for busy (has course, or not available)
                val bgColor = if (hasCourse) {
                    Color.parseColor("#FFEBEE") // Busy
                } else if (hasAvailability) {
                    Color.parseColor("#E3F2FD") // Available
                } else {
                    Color.parseColor("#F5F5F5") // Not available, no course (Gray)
                }
                
                cell.setBackgroundColor(bgColor)
            }
        }
    }

    private fun clearCoursesFromCells() {
        for ((dayNum, _) in daysOfWeek) {
            for (timeSlot in timeSlots) {
                val cellTag = "cell_${dayNum}_${timeSlot}"
                val cell = binding.dayColumnsContainer.findViewWithTag<LinearLayout>(cellTag) ?: continue
                cell.removeAllViews()
                cell.setBackgroundColor(Color.WHITE)
            }
        }
    }

    private fun setupLegend() {
        binding.calendarLegend.removeAllViews()

        val legendItems = listOf(
            LegendItem("Available 🔵", "#E3F2FD"),
            LegendItem("Busy 🔴", "#FFEBEE")
        )

        for (item in legendItems) {
            val legendRow = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, dpToPx(16), 0)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val swatch = View(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(dpToPx(16), dpToPx(16)).apply {
                    setMargins(0, 0, dpToPx(8), 0)
                }
                setBackgroundColor(Color.parseColor(item.colorHex))
            }

            val label = TextView(requireContext()).apply {
                text = item.text
                textSize = 14f
                setTextColor(Color.DKGRAY)
            }

            legendRow.addView(swatch)
            legendRow.addView(label)
            binding.calendarLegend.addView(legendRow)
        }
    }

    private data class LegendItem(val text: String, val colorHex: String)

    private fun createCourseCard(schedule: ScheduleEntry): View {
        val cardView = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            // We use transparent background because the cell background dictates the color
            setBackgroundColor(Color.TRANSPARENT) 
            setPadding(0, dpToPx(4), 0, 0)
        }

        val courseCodeView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val course = courseMap[schedule.courseId]
            text = if (course != null) "${course.name}\n${course.code}" else "Course ${schedule.courseId}"
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.parseColor("#C62828")) // Dark red text for contrast
        }
        cardView.addView(courseCodeView)

        val classroomView = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, dpToPx(4), 0, 0)
            }
            val classroom = classroomMap[schedule.classroomId]
            text = classroom?.name ?: "Unknown Room"
            textSize = 10f
            gravity = android.view.Gravity.CENTER
            setTextColor(Color.DKGRAY)
        }
        cardView.addView(classroomView)

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
