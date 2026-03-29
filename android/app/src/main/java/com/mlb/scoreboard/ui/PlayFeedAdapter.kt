package com.mlb.scoreboard.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mlb.scoreboard.R

data class PlayItem(
    val id: String,
    val event: String,
    val description: String,
    val isScoring: Boolean = false,
    val isHomeRun: Boolean = false
)

class PlayFeedAdapter : ListAdapter<PlayItem, PlayFeedAdapter.ViewHolder>(DIFF) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val event: TextView = view.findViewById(R.id.playEvent)
        val description: TextView = view.findViewById(R.id.playDescription)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_play, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.event.text = item.event.uppercase()
        holder.description.text = item.description

        // Highlight scoring plays
        when {
            item.isHomeRun -> {
                holder.event.setTextColor(Color.rgb(255, 87, 34))
                holder.itemView.setBackgroundColor(Color.argb(25, 255, 87, 34))
            }
            item.isScoring -> {
                holder.event.setTextColor(Color.rgb(255, 202, 40))
                holder.itemView.setBackgroundColor(Color.argb(20, 255, 202, 40))
            }
            else -> {
                holder.event.setTextColor(Color.rgb(5, 122, 255))
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<PlayItem>() {
            override fun areItemsTheSame(a: PlayItem, b: PlayItem) = a.id == b.id
            override fun areContentsTheSame(a: PlayItem, b: PlayItem) = a == b
        }
    }
}
