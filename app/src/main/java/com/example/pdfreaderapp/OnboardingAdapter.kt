package com.example.pdfreaderapp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class OnboardingAdapter(private val slides: List<OnboardingSlide>) :
    RecyclerView.Adapter<OnboardingAdapter.OnboardingViewHolder>() {

    data class OnboardingSlide(
        val title: String,
        val subtitle: String,
        val iconRes: Int // Kept for compatibility but unused
    )

    class OnboardingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fragment_onboarding, parent, false)
        return OnboardingViewHolder(view)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        val slide = slides[position]
        holder.tvTitle.text = slide.title
        holder.tvSubtitle.text = slide.subtitle
    }

    override fun getItemCount(): Int = slides.size
}
