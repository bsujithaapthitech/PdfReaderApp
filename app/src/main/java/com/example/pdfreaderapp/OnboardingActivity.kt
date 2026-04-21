package com.example.pdfreaderapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.pdfreaderapp.databinding.ActivityOnboardingBinding
import com.google.android.material.tabs.TabLayoutMediator

class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val slides = listOf(
            OnboardingAdapter.OnboardingSlide(
                "Decipher the\nComplex",
                "Transform dense PDFs into clear,\nactionable insights in seconds.",
                R.drawable.ob_slide_1
            ),
            OnboardingAdapter.OnboardingSlide(
                "Intelligent\nConversations",
                "Ask your documents anything. Our AI understands context, nuance, and detail.",
                R.drawable.ob_slide_2
            ),
            OnboardingAdapter.OnboardingSlide(
                "Total Clarity",
                "Ready to experience the future of document intelligence? Let's get started.",
                R.drawable.ob_slide_3
            )
        )

        val adapter = OnboardingAdapter(slides)
        binding.viewPager.adapter = adapter

        // Sync TabLayout dots with ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { _, _ -> }.attach()

        binding.btnGetStarted.setOnClickListener {
            val currentItem = binding.viewPager.currentItem
            if (currentItem < slides.size - 1) {
                binding.viewPager.currentItem = currentItem + 1
            } else {
                finishOnboarding()
            }
        }

        binding.btnSkip.setOnClickListener {
            finishOnboarding()
        }

        // Update button text on last slide
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                if (position == slides.size - 1) {
                    binding.btnGetStarted.text = "Get Started"
                } else {
                    binding.btnGetStarted.text = "Next"
                }
            }
        })
    }

    private fun finishOnboarding() {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putBoolean("is_first_time", false)
            apply()
        }
        startActivity(Intent(this, HomeScreenActivity::class.java))
        finish()
    }
}
