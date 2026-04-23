package com.example.unischedule.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.unischedule.databinding.ItemResourceBinding

data class ResourceItem(
    val title: String,
    val subtitle: String,
    val id: Long? = null,
    val originalObject: Any? = null
)

class ResourceAdapter(
    private var items: List<ResourceItem> = emptyList(),
    private val onItemClick: ((ResourceItem) -> Unit)? = null
) : RecyclerView.Adapter<ResourceAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemResourceBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemResourceBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.titleText.text = item.title
        holder.binding.subtitleText.text = item.subtitle
        
        holder.itemView.setOnClickListener {
            onItemClick?.invoke(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateItems(newItems: List<ResourceItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
