package com.example.unischedule.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import android.graphics.Typeface
import android.widget.TableRow
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unischedule.R
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.data.session.UserSession
import com.example.unischedule.databinding.FragmentCalendarBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreLecturerCalendarViewModel
import com.example.unischedule.viewmodel.FirestoreLecturerCalendarViewModelFactory
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

enum class CellState { AVAILABLE, MY_COURSE, BUSY }

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

    private val timeSlots = listOf(
        "08:00-10:00", "10:00-12:00", "13:00-15:00", "15:00-17:00"
    )

    private var courseMap: Map<Long, Course> = emptyMap()
    private var classroomMap: Map<Long, Classroom> = emptyMap()
    private var allSchedules: List<ScheduleEntry> = emptyList()

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

        buildCalendarGrid()

        observeScheduleUpdates()
        observeLookupData()
    }

    private fun buildCalendarGrid() {
        val table = binding.calendarTable
        table.removeAllViews()

        // 1. Create header row (empty cell, Mon, Tue, Wed, Thu, Fri)
        val headerRow = TableRow(requireContext())
        
        val emptyCell = TextView(requireContext()).apply {
            layoutParams = TableRow.LayoutParams(dpToPx(72), dpToPx(40))
            setBackgroundColor(Color.LTGRAY)
        }
        headerRow.addView(emptyCell)

        for ((_, dayLabel) in daysOfWeek) {
            val dayHeader = TextView(requireContext()).apply {
                layoutParams = TableRow.LayoutParams(dpToPx(64), dpToPx(40))
                text = dayLabel
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setBackgroundColor(Color.LTGRAY)
                // Add a border by using a padding or drawable if needed, but LTGRAY is fine
            }
            headerRow.addView(dayHeader)
        }
        table.addView(headerRow)

        // 2. Create rows for each time slot
        for (timeSlot in timeSlots) {
            val row = TableRow(requireContext())
            
            // Time label cell
            val timeCell = TextView(requireContext()).apply {
                layoutParams = TableRow.LayoutParams(dpToPx(72), dpToPx(56))
                text = timeSlot.replace("-", "\n")
                textSize = 12f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.grid_border)
            }
            row.addView(timeCell)

            // 5 Day cells
            for ((dayNum, _) in daysOfWeek) {
                val cell = LinearLayout(requireContext()).apply {
                    layoutParams = TableRow.LayoutParams(dpToPx(64), dpToPx(56))
                    orientation = LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.grid_border)
                    tag = "cell_${dayNum}_${timeSlot}"
                }
                row.addView(cell)
            }
            table.addView(row)
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
                            binding.calendarScrollView.visibility = View.VISIBLE
                            binding.noClassesText.visibility = View.GONE
                            
                            allSchedules = state.data
                            populateCalendar()
                        }
                        is UiState.Error -> {
                            binding.loadingProgress.visibility = View.GONE
                            binding.calendarScrollView.visibility = View.GONE
                            binding.noClassesText.visibility = View.GONE
                            binding.errorText.visibility = View.VISIBLE
                            binding.errorText.text = "Error: ${state.message}"
                            Snackbar.make(binding.root, "Failed to load course data. Please try again.", Snackbar.LENGTH_LONG).show()
                        }
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
                        populateCalendar()
                    }
                }
                launch {
                    viewModel.classroomMap.collect { map ->
                        classroomMap = map
                        populateCalendar()
                    }
                }
            }
        }
    }

    private fun populateCalendar() {
        if (allSchedules.isEmpty() && courseMap.isEmpty() && classroomMap.isEmpty()) return
        
        val currentLecturerId = UserSession.userId ?: return

        for ((dayNum, _) in daysOfWeek) {
            for (timeSlot in timeSlots) {
                val cellTag = "cell_${dayNum}_${timeSlot}"
                val cell = binding.calendarTable.findViewWithTag<LinearLayout>(cellTag) ?: continue

                cell.removeAllViews()

                // Find entry matching this exact day and time slot
                val entry = allSchedules.find { 
                    it.dayOfWeek == dayNum && 
                    "${it.startTime}-${it.endTime}".replace(" - ", "-") == timeSlot 
                }

                val state = when {
                    entry == null -> CellState.AVAILABLE
                    entry.lecturerId == currentLecturerId -> CellState.MY_COURSE
                    else -> CellState.BUSY
                }

                when (state) {
                    CellState.AVAILABLE -> {
                        cell.setBackgroundColor(Color.parseColor("#C8E6C9")) // Soft green
                        val tv = TextView(requireContext()).apply {
                            text = "Free"
                            textSize = 10f
                            setTextColor(Color.LTGRAY)
                            gravity = android.view.Gravity.CENTER
                        }
                        cell.addView(tv)
                    }
                    CellState.BUSY -> {
                        cell.setBackgroundColor(Color.parseColor("#FFCDD2")) // Soft red
                        val tv = TextView(requireContext()).apply {
                            text = "•"
                            textSize = 14f
                            setTextColor(Color.WHITE)
                            gravity = android.view.Gravity.CENTER
                        }
                        cell.addView(tv)
                    }
                    CellState.MY_COURSE -> {
                        cell.setBackgroundColor(Color.parseColor("#BBDEFB")) // Soft blue
                        if (entry != null) {
                            val course = courseMap[entry.courseId]
                            val classroom = classroomMap[entry.classroomId]
                            
                            val tv = TextView(requireContext()).apply {
                                text = buildString {
                                    if (course != null) {
                                        append(course.name)
                                        append("\n")
                                        append(course.code)
                                    } else {
                                        append("Course ${entry.courseId}")
                                    }
                                    append("\n")
                                    append(classroom?.name ?: "Unknown Room")
                                }
                                textSize = 10f
                                setTypeface(null, Typeface.BOLD)
                                setTextColor(Color.DKGRAY)
                                gravity = android.view.Gravity.CENTER
                            }
                            cell.addView(tv)
                        }
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
