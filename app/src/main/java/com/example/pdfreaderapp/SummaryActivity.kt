package com.example.pdfreaderapp

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.pdfreaderapp.data.api.NetworkModule
import com.example.pdfreaderapp.data.pdf.PdfTextExtractor
import com.example.pdfreaderapp.data.repository.SummaryRepository
import com.example.pdfreaderapp.databinding.ActivitySummaryBinding
import com.example.pdfreaderapp.domain.model.QaState
import com.example.pdfreaderapp.domain.usecase.AskPdfUseCase
import com.example.pdfreaderapp.domain.usecase.SummarizePdfUseCase
import com.example.pdfreaderapp.domain.util.TextChunker
import com.example.pdfreaderapp.ui.viewmodel.SummaryViewModel
import com.example.pdfreaderapp.ui.viewmodel.SummaryViewModelFactory

class SummaryActivity : AppCompatActivity() {

    // ViewBinding instance for accessing UI components
    private lateinit var binding: ActivitySummaryBinding

    // Holds the selected PDF URI received from previous screen
    private var selectedPdfUri: Uri? = null

    // Single handler used for all UI animations and delayed tasks
    // Centralizing this avoids multiple handler leaks and race conditions
    private val handler = Handler(Looper.getMainLooper())

    // Flag to control loading state and animation lifecycle
    private var isLoading = false

    // ViewModel initialization using factory pattern
    // Injects dependencies required for summary and Q&A features
    private val viewModel: SummaryViewModel by viewModels {
        val extractor = PdfTextExtractor(this)
        val repository = SummaryRepository(NetworkModule.openRouterApi)
        val summarizeUseCase = SummarizePdfUseCase(extractor, TextChunker, repository)
        val askPdfUseCase = AskPdfUseCase(extractor, repository)
        SummaryViewModelFactory(summarizeUseCase, askPdfUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve URI passed from PdfReaderActivity
        val uriString = intent.getStringExtra("pdfUri")

        android.util.Log.d("SUMMARY_DEBUG", "Received URI: $uriString")

        // Parse and store URI
        selectedPdfUri = uriString?.let { Uri.parse(it) }

        // Handle toolbar back navigation
        binding.topBar.setNavigationOnClickListener { finish() }

        // Setup observers for summary and Q&A responses
        setupObservers()

        // Trigger summary generation immediately
        startSummary()

        // Setup Ask (Q&A) functionality
        setupAsk()
    }

    // Timeout runnable to prevent infinite loading
    // Acts as fallback when API does not respond
    private val timeoutRunnable = Runnable {
        if (isLoading) {
            stopLoading()
            binding.tvSummary.text = "Taking too long...\nCheck internet or API"
        }
    }

    // Starts summary generation process
    private fun startSummary() {

        // Validate URI before proceeding
        if (selectedPdfUri == null || selectedPdfUri.toString() == "null") {
            binding.tvSummary.text = "No PDF received"
            return
        }

        val uri = selectedPdfUri!!
        isLoading = true

        // Reset UI state
        binding.tvSummary.text = ""
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.progress = 0

        // Start loading animations
        startAnalyzingAnimation()
        simulateProgress()
        startDotsAnimation()

        // Start timeout safeguard (10 seconds)
        handler.postDelayed(timeoutRunnable, 10000)

        // Trigger ViewModel summary logic
        viewModel.summarize(uri)
    }

    // Observes ViewModel state changes for summary and Q&A
    private fun setupObservers() {

        viewModel.uiState.observe(this) { state ->

            when (state) {

                // Summary success case
                is SummaryViewModel.UiState.Success -> {
                    stopLoading()
                    binding.tvSummary.text = ""
                    typeText(state.summary)
                }

                // Summary error case
                is SummaryViewModel.UiState.Error -> {
                    stopLoading()

                    binding.tvSummary.text = "Failed to generate summary\n(Check API or try again)"

                    // Allow retry on click
                    binding.tvSummary.setOnClickListener {
                        startSummary()
                    }
                }

                // Loading or idle state
                else -> {
                    // No action, loading animations continue
                }
            }
        }

        // Observe Q&A responses
        viewModel.qaState.observe(this) { state ->
            when (state) {
                is QaState.Answer -> {
                    stopThinking()
                    typeTextAnswer(state.answer)
                }

                is QaState.Error -> {
                    binding.tvAnswer.text = "Error: ${state.message}"
                }

                else -> {}
            }
        }
    }

    // Handles Ask button interaction for Q&A feature
    private fun setupAsk() {
        binding.btnAsk.setOnClickListener {
            val question = binding.etQuestion.text.toString()

            if (question.isBlank()) return@setOnClickListener

            // Start "thinking" animation
            startThinkingAnimation()

            selectedPdfUri?.let {
                viewModel.askQuestion(it, question)
            }
        }
    }

    // Stops loading animations and resets UI state
    private fun stopLoading() {
        isLoading = false

        // Remove all pending callbacks and animations
        handler.removeCallbacksAndMessages(null)

        binding.progressBar.progress = 100
        binding.progressBar.visibility = View.GONE
        binding.btnAsk.isEnabled = true
    }

    // Stops Q&A thinking animation
    private fun stopThinking() {
        handler.removeCallbacksAndMessages(null)
    }

    // Typing animation for summary text
    private fun typeText(fullText: String) {
        var index = 0

        handler.post(object : Runnable {
            override fun run() {
                if (index <= fullText.length) {
                    binding.tvSummary.text = fullText.substring(0, index)
                    index++
                    handler.postDelayed(this, 10)
                }
            }
        })
    }

    // Typing animation for answer text
    private fun typeTextAnswer(fullText: String) {
        var index = 0

        handler.post(object : Runnable {
            override fun run() {
                if (index <= fullText.length) {
                    binding.tvAnswer.text = fullText.substring(0, index)
                    index++
                    handler.postDelayed(this, 10)
                }
            }
        })
    }

    // Simulates progress bar movement during loading
    private fun simulateProgress() {
        var progress = 0

        handler.post(object : Runnable {
            override fun run() {
                if (isLoading && progress < 95) {

                    progress += (1..4).random()

                    binding.progressBar.progress = progress

                    handler.postDelayed(this, (150..300).random().toLong())
                }
            }
        })
    }

    // Displays rotating AI-style status messages
    private fun startAnalyzingAnimation() {

        val messages = listOf(
            "Analyzing document...",
            "Extracting key insights...",
            "Understanding context...",
            "Scanning important sections...",
            "Finding key points...",
            "Generating summary...",
            "Almost done..."
        )

        var index = 0

        handler.post(object : Runnable {
            override fun run() {
                if (isLoading) {
                    binding.tvSummary.text = messages[index % messages.size]
                    index++
                    handler.postDelayed(this, (800..1400).random().toLong())
                }
            }
        })
    }

    // Adds animated dots to simulate ongoing processing
    private fun startDotsAnimation() {

        var dots = ""

        handler.post(object : Runnable {
            override fun run() {
                if (isLoading) {

                    dots = if (dots.length >= 3) "" else dots + "."

                    val baseText = binding.tvSummary.text.toString()
                        .replace(Regex("\\.*$"), "")

                    binding.tvSummary.text = baseText + dots

                    handler.postDelayed(this, 400)
                }
            }
        })
    }

    // Displays "thinking" animation for Q&A responses
    private fun startThinkingAnimation() {
        val texts = listOf("Thinking.", "Thinking..", "Thinking...")
        var index = 0

        handler.post(object : Runnable {
            override fun run() {
                binding.tvAnswer.text = texts[index % texts.size]
                index++
                handler.postDelayed(this, 500)
            }
        })
    }

    // Cleanup handler callbacks to avoid memory leaks
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}