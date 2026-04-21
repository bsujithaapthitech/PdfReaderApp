package com.example.pdfreaderapp.create

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.pdfreaderapp.R
import com.example.pdfreaderapp.model.ChatItem

// Adapter class for displaying chat messages in RecyclerView
class ChatAdapter(private val list: MutableList<ChatItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // Determines view type based on whether message is from user or AI
    override fun getItemViewType(position: Int): Int {
        return if (list[position].isUser) 1 else 0
    }

    // Creates ViewHolder with appropriate layout (user or AI message)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {

        val view = LayoutInflater.from(parent.context).inflate(
            // Select layout based on message type
            if (viewType == 1) R.layout.user_message else R.layout.ai_message,
            parent,
            false
        )

        // Using anonymous ViewHolder since no complex binding is required
        return object : RecyclerView.ViewHolder(view) {}
    }

    // Binds message data to the TextView inside each item
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val tv = holder.itemView.findViewById<TextView>(R.id.tvMessage)
        tv.text = list[position].message
    }

    // Returns total number of chat items
    override fun getItemCount() = list.size
}