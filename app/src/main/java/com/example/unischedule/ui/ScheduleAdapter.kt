package com.example.unischedule.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unischedule.data.firestore.Classroom
import com.example.unischedule.data.firestore.Course
import com.example.unischedule.data.firestore.Lecturer
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.databinding.ItemResourceBinding

class ScheduleAdapter(
    private var items: List<ScheduleEntry> = emptyList(),
    private val onItemClick: (ScheduleEntry) -> Unit = {}
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    // Lookup maps — set from Fragment once data is loaded
    private var courseMap: Map<Long, Course> = emptyMap()
    private var lecturerMap: Map<Long, Lecturer> = emptyMap()
    private var classroomMap: Map<Long, Classroom> = emptyMap()

    class ViewHolder(val binding: ItemResourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // Title: Course name (resolved)
        val course = courseMap[item.courseId]
        holder.binding.titleText.text = if (course != null) {
            "${course.code}: ${course.name}"
        } else {
            "Course #${item.courseId}"
        }

        // Subtitle: Lecturer name, Day/Time, Room
        val lecturer = lecturerMap[item.lecturerId]
        val lecturerName = formatLecturerName(lecturer)
        
        val classroom = classroomMap[item.classroomId]
        val roomLabel = classroom?.name ?: "Room #${item.classroomId}"

        val subtitle = buildString {
            append("Lecturer: $lecturerName")
            append("\n")
            append("${getDayName(item.dayOfWeek)} | ${item.startTime} - ${item.endTime}")
            append("\n")
            append("Room: $roomLabel")
        }
        holder.binding.subtitleText.text = subtitle

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ScheduleEntry>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Update all lookup maps at once. Call this when data loads. */
    fun updateLookups(
        courses: Map<Long, Course>,
        lecturers: Map<Long, Lecturer>,
        classrooms: Map<Long, Classroom>
    ) {
        courseMap = courses
        lecturerMap = lecturers
        classroomMap = classrooms
        // Re-bind visible items with new names
        notifyDataSetChanged()
    }

    /**
     * Format a lecturer's display name:
     * - Use fullName if available (clean underscores/dots → spaces)
     * - Fall back to username (clean underscores/dots → spaces)
     */
    private fun formatLecturerName(lecturer: Lecturer?): String {
        if (lecturer == null) return "Unknown"
        val raw = lecturer.fullName.ifBlank { lecturer.username }
        if (raw.isBlank()) return "Unknown"
        // Replace underscores and dots with spaces, then title-case
        return raw.replace('_', ' ').replace('.', ' ')
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            .trim()
    }

    private fun getDayName(day: Int): String = when (day) {
        1 -> "Monday"
        2 -> "Tuesday"
        3 -> "Wednesday"
        4 -> "Thursday"
        5 -> "Friday"
        6 -> "Saturday"
        7 -> "Sunday"
        else -> "Unknown"
    }
}
