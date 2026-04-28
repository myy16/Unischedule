package com.example.unischedule.ui

import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.repository.FirestoreRepository
import com.example.unischedule.databinding.FragmentAssignmentBinding
import com.example.unischedule.util.UiState
import com.example.unischedule.viewmodel.FirestoreAssignmentViewModel
import com.example.unischedule.viewmodel.FirestoreAssignmentViewModelFactory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.util.Calendar

class AssignmentFragment : Fragment() {

    private var _binding: FragmentAssignmentBinding? = null
    private val binding get() = _binding!!

    private val viewModel: FirestoreAssignmentViewModel by viewModels {
        FirestoreAssignmentViewModelFactory(FirestoreRepository(FirebaseFirestore.getInstance()))
    }

    private var selectedCourse: Course? = null
    private var selectedLecturer: Lecturer? = null
    private var selectedClassroom: Classroom? = null
    private var selectedStartTime = "09:00"
    private var selectedEndTime = "10:00"

    private data class CourseItem(val course: Course) {
        override fun toString(): String = "${course.code} - ${course.name}"
    }

    private data class LecturerItem(val lecturer: Lecturer) {
        override fun toString(): String = lecturer.fullName.ifBlank { lecturer.username }
    }

    private data class ClassroomItem(val classroom: Classroom) {
        override fun toString(): String = classroom.name
    }

    private data class DayItem(val value: Int, val label: String) {
        override fun toString(): String = label
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAssignmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupDaySpinner()
        setupTimePickers()
        observeData()

        binding.btnAssign.setOnClickListener {
            val course = selectedCourse
            val lecturer = selectedLecturer
            val classroom = selectedClassroom
            val day = (binding.daySpinner.selectedItem as? DayItem)?.value ?: 1

            if (course == null || lecturer == null || classroom == null) {
                Toast.makeText(context, "Please choose course, lecturer, and classroom", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.assign(
                courseId = course.id,
                lecturerId = lecturer.id,
                classroomId = classroom.id,
                dayOfWeek = day,
                startTime = selectedStartTime,
                endTime = selectedEndTime
            )
        }

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.assignmentState.collect { state ->
                    when (state) {
                        is UiState.Loading -> Unit
                        is UiState.Success -> {
                            Toast.makeText(context, "Assignment saved", Toast.LENGTH_SHORT).show()
                            viewModel.resetAssignmentState()
                            findNavController().navigateUp()
                        }
                        is UiState.Error -> {
                            Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            viewModel.resetAssignmentState()
                        }
                    }
                }
            }
        }
    }

    private fun setupDaySpinner() {
        val days = listOf(
            DayItem(1, "Monday"),
            DayItem(2, "Tuesday"),
            DayItem(3, "Wednesday"),
            DayItem(4, "Thursday"),
            DayItem(5, "Friday"),
            DayItem(6, "Saturday"),
            DayItem(7, "Sunday")
        )
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, days)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.daySpinner.adapter = adapter
    }

    private fun setupTimePickers() {
        binding.btnStartTime.setOnClickListener { showTimePicker(true) }
        binding.btnEndTime.setOnClickListener { showTimePicker(false) }
    }

    private fun showTimePicker(isStart: Boolean) {
        val calendar = Calendar.getInstance()
        TimePickerDialog(
            requireContext(),
            { _, hour, minute ->
                val time = String.format("%02d:%02d", hour, minute)
                if (isStart) {
                    selectedStartTime = time
                    binding.btnStartTime.text = "Start: $time"
                } else {
                    selectedEndTime = time
                    binding.btnEndTime.text = "End: $time"
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.coursesState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val items = state.data.map { CourseItem(it) }
                                binding.courseSpinner.adapter = ArrayAdapter(
                                    requireContext(),
                                    android.R.layout.simple_spinner_item,
                                    items
                                ).apply {
                                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                }
                                binding.courseSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                        selectedCourse = items.getOrNull(position)?.course
                                    }

                                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                                }
                            }
                            is UiState.Error -> Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            is UiState.Loading -> Unit
                        }
                    }
                }
                launch {
                    viewModel.lecturersState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val items = state.data.map { LecturerItem(it) }
                                binding.lecturerSpinner.adapter = ArrayAdapter(
                                    requireContext(),
                                    android.R.layout.simple_spinner_item,
                                    items
                                ).apply {
                                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                }
                                binding.lecturerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                        selectedLecturer = items.getOrNull(position)?.lecturer
                                    }

                                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                                }
                            }
                            is UiState.Error -> Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            is UiState.Loading -> Unit
                        }
                    }
                }
                launch {
                    viewModel.classroomsState.collect { state ->
                        when (state) {
                            is UiState.Success -> {
                                val items = state.data.map { ClassroomItem(it) }
                                binding.classroomSpinner.adapter = ArrayAdapter(
                                    requireContext(),
                                    android.R.layout.simple_spinner_item,
                                    items
                                ).apply {
                                    setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                                }
                                binding.classroomSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                                    override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                                        selectedClassroom = items.getOrNull(position)?.classroom
                                    }

                                    override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                                }
                            }
                            is UiState.Error -> Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                            is UiState.Loading -> Unit
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
