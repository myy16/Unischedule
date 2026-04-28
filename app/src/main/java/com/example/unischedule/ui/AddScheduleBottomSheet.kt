package com.example.unischedule.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.unischedule.data.database.UniversityDatabase
import com.example.unischedule.data.entity.Course
import com.example.unischedule.data.entity.Schedule
import com.example.unischedule.data.repository.AssignmentResult
import com.example.unischedule.data.repository.UniversityRepository
import com.example.unischedule.databinding.BottomSheetAddScheduleBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.ScheduleViewModel
import com.example.unischedule.viewmodel.ViewModelFactory
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.*

class AddScheduleBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAddScheduleBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ScheduleViewModel by viewModels {
        val db = UniversityDatabase.getDatabase(requireContext(), lifecycleScope)
        ViewModelFactory(UniversityRepository(db.universityDao()))
    }

    private var selectedCourse: Course? = null
    private var dataCollectionJob: Job? = null
    
    private var selectedStartTime = "09:00"
    private var selectedEndTime = "10:00"

    private data class CourseSpinnerItem(val course: Course) {
        override fun toString(): String = "${course.code} - ${course.name}"
    }

    private data class ResourceSpinnerItem(val id: Long, val label: String) {
        override fun toString(): String = label
    }

    private data class DaySpinnerItem(val id: Int, val name: String) {
        override fun toString(): String = name
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetAddScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinners()
        setupTimePickers()
        observeData()

        binding.assignButton.setOnClickListener {
            performAssignment(force = false)
        }
    }

    private fun performAssignment(force: Boolean) {
        val course = selectedCourse ?: return
        val instructor = binding.instructorSpinner.selectedItem as? ResourceSpinnerItem
        val classroom = binding.classroomSpinner.selectedItem as? ResourceSpinnerItem
        val day = (binding.daySpinner.selectedItem as? DaySpinnerItem)?.id ?: 1

        if (instructor == null || classroom == null) {
            Toast.makeText(context, "Please select all resources", Toast.LENGTH_SHORT).show()
            return
        }

        val schedule = Schedule(
            courseId = course.id,
            instructorId = instructor.id,
            classroomId = classroom.id,
            dayOfWeek = day,
            startTime = selectedStartTime,
            endTime = selectedEndTime
        )

        viewModel.tryAddSchedule(schedule, course.departmentId, course.semester, force)
    }

    private fun setupSpinners() {
        binding.courseSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selected = parent?.getItemAtPosition(position) as? CourseSpinnerItem ?: return
                selectedCourse = selected.course
                // Logic for instructor/classroom filtering can be added to ViewModel
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val days = listOf(
            DaySpinnerItem(1, "Monday"), DaySpinnerItem(2, "Tuesday"),
            DaySpinnerItem(3, "Wednesday"), DaySpinnerItem(4, "Thursday"),
            DaySpinnerItem(5, "Friday"), DaySpinnerItem(6, "Saturday"), DaySpinnerItem(7, "Sunday")
        )
        val dayAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, days)
        dayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.daySpinner.adapter = dayAdapter
    }

    private fun setupTimePickers() {
        binding.btnStartTime.setOnClickListener { showTimePicker(true) }
        binding.btnEndTime.setOnClickListener { showTimePicker(false) }
    }

    private fun showTimePicker(isStartTime: Boolean) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(requireContext(), { _, h, m ->
            val time = String.format("%02d:%02d", h, m)
            if (isStartTime) {
                selectedStartTime = time
                binding.btnStartTime.text = "Start: $time"
            } else {
                selectedEndTime = time
                binding.btnEndTime.text = "End: $time"
            }
        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.coursesState.collect { state ->
                        if (state is UiState.Success) {
                            val items = state.data.map { CourseSpinnerItem(it) }
                            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                            binding.courseSpinner.adapter = adapter
                        }
                    }
                }
                launch {
                    viewModel.assignmentState.collect { state ->
                        if (state is UiState.Success) {
                            state.data?.let { handleResult(it) }
                            viewModel.resetAssignmentState()
                        } else if (state is UiState.Error) {
                            showErrorDialog("Error", state.message)
                        }
                    }
                }
                // Instructors and Classrooms can be collected here if ViewModel exposes them as UiState
            }
        }
    }

    private fun handleResult(result: AssignmentResult) {
        when (result) {
            is AssignmentResult.Success -> {
                Toast.makeText(context, "Assignment Successful!", Toast.LENGTH_SHORT).show()
                dismiss()
            }
            is AssignmentResult.InstructorBusy -> showErrorDialog("Instructor Conflict", result.details)
            is AssignmentResult.RoomOccupied -> showErrorDialog("Classroom Conflict", result.details)
            is AssignmentResult.StudentGroupConflict -> showErrorDialog("Student Conflict", result.details)
            is AssignmentResult.InstructorPreferenceConflict -> {
                AlertDialog.Builder(requireContext())
                    .setTitle("Instructor Preference Warning")
                    .setMessage("This instructor has marked this time as 'Unavailable'. Do you want to assign anyway?")
                    .setPositiveButton("Assign Anyway") { _, _ -> performAssignment(force = true) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            is AssignmentResult.Error -> showErrorDialog("Error", result.message)
        }
    }

    private fun showErrorDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
