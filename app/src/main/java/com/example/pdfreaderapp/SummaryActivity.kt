package com.example.pdfreaderapp

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pdfreaderapp.create.ChatAdapter
import com.example.pdfreaderapp.data.api.NetworkModule
import com.example.pdfreaderapp.data.local.dao.PdfDao
import com.example.pdfreaderapp.data.local.database.AppDatabase
import com.example.pdfreaderapp.data.local.entity.QaEntity
import com.example.pdfreaderapp.data.local.entity.SummaryEntity
import com.example.pdfreaderapp.data.pdf.PdfTextExtractor
import com.example.pdfreaderapp.data.repository.SummaryRepository
import com.example.pdfreaderapp.databinding.ActivitySummaryBinding
import com.example.pdfreaderapp.domain.model.QaState
import com.example.pdfreaderapp.domain.usecase.AskPdfUseCase
import com.example.pdfreaderapp.domain.usecase.SummarizePdfUseCase
import com.example.pdfreaderapp.domain.util.TextChunker
import com.example.pdfreaderapp.model.ChatItem
import com.example.pdfreaderapp.ui.viewmodel.SummaryViewModel
import com.example.pdfreaderapp.ui.viewmodel.SummaryViewModelFactory
import kotlinx.coroutines.launch
import androidx.recyclerview.widget.LinearLayoutManager

class SummaryActivity : AppCompatActivity() {

    // Stores last asked question to ensure correct mapping with response
    private var lastQuestion: String = ""

    // Adapter and list for chat UI
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<ChatItem>()

    // Room database and DAO
    private lateinit var db: AppDatabase
    private lateinit var dao: PdfDao

    // ViewBinding for UI access
    private lateinit var binding: ActivitySummaryBinding

    // Holds selected PDF URI
    private var selectedPdfUri: Uri? = null

    // Handler for animations and delayed tasks
    private val handler = Handler(Looper.getMainLooper())

    // Loading state flag
    private var isLoading = false

    // ViewModel initialization with required dependencies
    private val viewModel: SummaryViewModel by viewModels {
        val extractor = PdfTextExtractor(this)
        val repository = SummaryRepository(NetworkModule.openRouterApi)
        val summarizeUseCase = SummarizePdfUseCase(extractor, TextChunker, repository)
        val askPdfUseCase = AskPdfUseCase(extractor, repository)
        SummaryViewModelFactory(summarizeUseCase, askPdfUseCase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize binding
        binding = ActivitySummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup RecyclerView for chat messages
        chatAdapter = ChatAdapter(chatList)
        binding.recyclerChat.adapter = chatAdapter
        binding.recyclerChat.layoutManager = LinearLayoutManager(this)

        // Initialize database and DAO
        db = AppDatabase.getDatabase(this)
        dao = db.pdfDao()

        // Retrieve PDF URI from intent
        val uriString = intent.getStringExtra("pdfUri")
        selectedPdfUri = uriString?.let { Uri.parse(it) }

        // Load previously saved Q&A from database
        lifecycleScope.launch {
            val uri = selectedPdfUri?.toString() ?: return@launch
            val qaList = dao.getQaList(uri)

            qaList.forEach {
                chatList.add(ChatItem(it.question, true))
                chatList.add(ChatItem(it.answer, false))
            }

            chatAdapter.notifyDataSetChanged()
            binding.recyclerChat.scrollToPosition(chatList.size - 1)
        }

        // Back navigation
        binding.topBar.setNavigationOnClickListener { finish() }

        // Initialize observers, summary and Q&A features
        setupObservers()
        startSummary()
        setupAsk()
    }

    // Timeout fallback if API takes too long
    private val timeoutRunnable = Runnable {
        if (isLoading) {
            stopLoading()
            binding.tvSummary.text = "Taking too long...\nCheck internet or API"
        }
    }

    // Starts summary generation or loads from local DB
    private fun startSummary() {

        if (selectedPdfUri == null) {
            binding.tvSummary.text = "No PDF received"
            return
        }

        val uri = selectedPdfUri!!

        lifecycleScope.launch {

            val localSummary = dao.getSummary(uri.toString())

            // If summary exists locally, display it without API call
            if (localSummary != null) {
                binding.tvSummary.append("\n\n(Loaded from offline)")
                stopLoading()
                binding.tvSummary.text = ""
                typeText(localSummary.summary)
                return@launch
            }

            // Start loading UI
            isLoading = true
            binding.tvSummary.text = ""
            binding.progressBar.visibility = View.VISIBLE
            binding.progressBar.progress = 0

            startAnalyzingAnimation()
            startDotsAnimation()

            // Timeout protection
            handler.postDelayed(timeoutRunnable, 10000)

            // Trigger API call
            viewModel.summarize(uri)
        }
    }

    // Observes summary and Q&A state changes from ViewModel
    private fun setupObservers() {

        viewModel.uiState.observe(this) { state ->

            when (state) {

                // Summary success
                is SummaryViewModel.UiState.Success -> {

                    val summaryText = state.summary

                    stopLoading()
                    binding.tvSummary.text = ""
                    typeText(summaryText)

                    // Save summary locally
                    lifecycleScope.launch {
                        dao.insertSummary(
                            SummaryEntity(
                                pdfUri = selectedPdfUri.toString(),
                                summary = summaryText
                            )
                        )
                    }
                }

                // Summary error
                is SummaryViewModel.UiState.Error -> {
                    stopLoading()
                    binding.tvSummary.text = "Failed to generate summary\n(Check API)"
                }

                else -> {}
            }
        }

        // Q&A observer
        viewModel.qaState.observe(this) { state ->

            when (state) {

                is QaState.Answer -> {

                    val answerText = state.answer

                    // Use stored question to avoid mismatch
                    val questionText = lastQuestion

                    // Add AI response to chat
                    chatList.add(ChatItem(answerText, false))
                    chatAdapter.notifyItemInserted(chatList.size - 1)

                    if (chatList.isNotEmpty()) {
                        binding.recyclerChat.scrollToPosition(chatList.size - 1)
                    }

                    // Save Q&A locally
                    lifecycleScope.launch {
                        dao.insertQa(
                            QaEntity(
                                pdfUri = selectedPdfUri.toString(),
                                question = questionText,
                                answer = answerText
                            )
                        )
                    }

                    // Clear input field
                    binding.etQuestion.text?.clear()
                }

                is QaState.Error -> {
                    // Error handling can be added if needed
                }

                else -> {}
            }
        }
    }

    // Handles user question input
    private fun setupAsk() {
        binding.btnAsk.setOnClickListener {
            val question = binding.etQuestion.text.toString()

            if (question.isBlank()) return@setOnClickListener

            // Store last question for mapping
            lastQuestion = question

            // Add user message to chat
            chatList.add(ChatItem(question, true))
            chatAdapter.notifyItemInserted(chatList.size - 1)
            binding.recyclerChat.smoothScrollToPosition(chatList.size - 1)

            // Trigger API call
            selectedPdfUri?.let {
                viewModel.askQuestion(it, question)
            }
        }
    }

    // Stops loading state and animations
    private fun stopLoading() {
        isLoading = false
        handler.removeCallbacksAndMessages(null)
        binding.progressBar.visibility = View.GONE
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

    // Simulates progress bar movement
    private fun simulateProgress() {
        var progress = 0

        handler.post(object : Runnable {
            override fun run() {
                if (isLoading && progress < 95) {
                    progress += (1..4).random()
                    binding.progressBar.progress = progress
                    handler.postDelayed(this, 200)
                }
            }
        })
    }

    // Displays rotating loading messages
    private fun startAnalyzingAnimation() {

        val messages = listOf(
            "Analyzing document...",
            "Extracting insights...",
            "Understanding content...",
            "Generating summary..."
        )

        var index = 0

        handler.post(object : Runnable {
            override fun run() {
                if (isLoading) {
                    binding.tvSummary.text = messages[index % messages.size]
                    index++
                    handler.postDelayed(this, 1000)
                }
            }
        })
    }

    // Adds animated dots during loading
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

    // Cleanup handler to prevent memory leaks
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}