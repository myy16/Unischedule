package com.example.unischedule.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unischedule.data.entity.ScheduleWithDetails
import com.example.unischedule.databinding.ItemResourceBinding

class ScheduleAdapter(
    private var items: List<ScheduleWithDetails> = emptyList(),
    private val onItemClick: (ScheduleWithDetails) -> Unit
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemResourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        
        // Show actual names instead of IDs
        holder.binding.titleText.text = "${item.course.code}: ${item.course.name}"
        
        val subtitle = buildString {
            append("${item.instructor.title} ${item.instructor.name}")
            append("\n")
            append("${getDayName(item.schedule.dayOfWeek)} | ${item.schedule.startTime} - ${item.schedule.endTime}")
            append("\n")
            append("Room: ${item.classroom.name}")
        }
        holder.binding.subtitleText.text = subtitle

        holder.binding.root.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ScheduleWithDetails>) {
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
