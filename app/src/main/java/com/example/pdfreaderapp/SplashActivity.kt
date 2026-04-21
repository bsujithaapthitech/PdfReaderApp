package com.example.pdfreaderapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Handle the splash screen transition.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Keep the splash screen on-screen until the condition is met.
        // In this case, we use a simple delay for a premium feel.
        lifecycleScope.launch {
            delay(2000) // 2 second delay
            navigateToNextScreen()
        }
    }

    private fun navigateToNextScreen() {
        val sharedPref = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val isFirstTime = sharedPref.getBoolean("is_first_time", true)

        if (isFirstTime) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        } else {
            startActivity(Intent(this, HomeScreenActivity::class.java))
        }
        finish()
    }
}
