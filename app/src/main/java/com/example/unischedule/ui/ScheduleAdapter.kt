package com.example.unischedule.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unischedule.data.firestore.ScheduleEntry
import com.example.unischedule.databinding.ItemResourceBinding

class ScheduleAdapter(
    private var items: List<ScheduleEntry> = emptyList(),
    private val onItemClick: (ScheduleEntry) -> Unit = {}
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemResourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        holder.binding.titleText.text = "Schedule #${item.id}"
        
        val subtitle = buildString {
            append("Lecturer: ${item.lecturerId}")
            append("\n")
            append("${getDayName(item.dayOfWeek)} | ${item.startTime} - ${item.endTime}")
            append("\n")
            append("Room: ${item.classroomId}")
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
