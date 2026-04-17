package com.example.pdfreaderapp.util

import com.example.pdfreaderapp.BuildConfig

object Constants {
    val OPENROUTER_API_KEY = BuildConfig.OPENROUTER_API_KEY
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
    const val DEFAULT_MODEL = "nvidia/nemotron-3-super-120b-a12b:free"
    const val FALLBACK_MODEL = "google/gemma-4-26b-a4b:free"
    
    const val ERROR_RATE_LIMIT = "RATE_LIMIT_REACHED"
    
    // Chunking Constraints
    const val TARGET_CHUNK_SIZE = 12000         // Increased from 8000 to better handle massive docs
    const val CHUNK_OVERLAP = 200               // Maintains cross-chunk context
    const val MAX_CHUNKS = 150                  // Hard cap to prevent runaway calls
    const val STABILITY_THRESHOLD_CHARS = 100   // Fallback indicator

    // Reliability Optimized Constants
    const val API_TIMEOUT_MS = 60_000L          // 60s per individual API call
    const val QA_CONTEXT_LIMIT = 5000

    // Concurrency & Rate limits
    const val MAX_CONCURRENT_API_CALLS = 2      // Reduced from 3 to prevent bursts
    const val BATCH_DELAY_MS = 2000L            // Increased from 1s to 2s
    const val RATE_LIMIT_BACKOFF_MS = 240_000L

    // Hard global timeout — any document, any size, guaranteed ≤15 minutes total
    const val HARD_TIMEOUT_MS = 900_000L        // 15 minutes = 900 seconds
}
