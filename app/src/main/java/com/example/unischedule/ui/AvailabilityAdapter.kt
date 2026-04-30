package com.example.unischedule.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.unischedule.R
import com.example.unischedule.data.firestore.InstructorAvailability
import com.example.unischedule.databinding.ItemAvailabilitySlotBinding

class AvailabilityAdapter(
    private val onSlotClick: (Int, String, String) -> Unit
) : RecyclerView.Adapter<AvailabilityAdapter.ViewHolder>() {

    private var availabilities: List<InstructorAvailability> = emptyList()
    private val slots = listOf(
        "08:30", "09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", 
        "12:30", "13:00", "13:30", "14:00", "14:30", "15:00", "15:30", "16:00",
        "16:30", "17:00", "17:30", "18:00"
    )

    fun updateData(newData: List<InstructorAvailability>) {
        availabilities = newData
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemAvailabilitySlotBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAvailabilitySlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val dayIndex = position % 5
        val slotIndex = position / 5
        val day = dayIndex + 1
        val time = slots[slotIndex]

        holder.binding.tvTime.text = time
        
        val availability = availabilities.find { it.dayOfWeek == day && it.startTime == time }
        
        val isCellBusy = if (availability != null) {
            if (availability.status != null) isBusy(availability.status) else false
        } else {
            true
        }
        
        if (!isCellBusy) {
            holder.binding.cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#4CAF50"))
            holder.binding.tvStatus.text = "AVAIL"
            holder.binding.tvStatus.setTextColor(android.graphics.Color.WHITE)
            holder.binding.tvTime.setTextColor(android.graphics.Color.WHITE)
        } else {
            holder.binding.cardView.setCardBackgroundColor(android.graphics.Color.parseColor("#EF5350"))
            holder.binding.tvStatus.text = "BUSY"
            holder.binding.tvStatus.setTextColor(android.graphics.Color.WHITE)
            holder.binding.tvTime.setTextColor(android.graphics.Color.WHITE)
        }

        holder.itemView.setOnClickListener {
            val endTime = availability?.endTime ?: calculateEndTime(time)
            onSlotClick(day, time, endTime)
        }
    }

    private fun isBusy(status: Any?): Boolean = when (status) {
        is Boolean -> status == false
        is String -> status.toString().lowercase() in listOf("busy", "false", "0")
        is Long -> status == 0L
        is Double -> status == 0.0
        else -> false
    }

    override fun getItemCount(): Int = 5 * slots.size

    private fun calculateEndTime(startTime: String): String {
        val parts = startTime.split(":")
        val hour = parts[0].toIntOrNull() ?: 8
        val minute = parts[1].toIntOrNull() ?: 30
        
        val totalMinutes = hour * 60 + minute + 30
        val endHour = totalMinutes / 60
        val endMinute = totalMinutes % 60
        
        return String.format("%02d:%02d", endHour, endMinute)
    }
}
