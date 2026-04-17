package com.example.pdfreaderapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Adapter for displaying list of PDF items in RecyclerView
// Handles binding data and click interactions for each item
class PdfAdapter(
    private val list: List<PdfItem>,              // Data source for RecyclerView
    private val onClick: (PdfItem) -> Unit        // Click listener callback
) : RecyclerView.Adapter<PdfAdapter.PdfViewHolder>() {

    // ViewHolder class holds references to item views for reuse
    // Improves performance by avoiding repeated findViewById calls
    inner class PdfViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.tvTitle)  // Displays PDF name
    }

    // Called when RecyclerView needs a new ViewHolder
    // Inflates the item layout
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PdfViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pdf, parent, false)
        return PdfViewHolder(view)
    }

    // Binds data to the ViewHolder at a given position
    // Sets text and click listener for each item
    override fun onBindViewHolder(holder: PdfViewHolder, position: Int) {
        val item = list[position]

        // Set PDF title
        holder.title.text = item.name

        // Handle item click and pass selected PDF back to Activity
        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }

    // Returns total number of items in the list
    override fun getItemCount() = list.size
}