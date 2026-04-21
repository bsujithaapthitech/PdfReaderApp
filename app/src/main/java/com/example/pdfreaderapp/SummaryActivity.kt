package com.example.pdfreaderapp

import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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

class SummaryActivity : AppCompatActivity() {

    private var lastQuestion: String = ""
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<ChatItem>()
    private lateinit var db: AppDatabase
    private lateinit var dao: PdfDao
    private lateinit var binding: ActivitySummaryBinding
    private var selectedPdfUri: Uri? = null

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

        chatAdapter = ChatAdapter(chatList)
        binding.recyclerChat.adapter = chatAdapter
        binding.recyclerChat.layoutManager = LinearLayoutManager(this)

        db = AppDatabase.getDatabase(this)
        dao = db.pdfDao()

        val uriString = intent.getStringExtra("pdfUri")
        selectedPdfUri = uriString?.let { Uri.parse(it) }

        binding.topBar.setNavigationOnClickListener { finish() }

        setupKeyboardHandling()
        setupObservers()
        loadInitialData()
        setupAsk()
    }

    private fun setupKeyboardHandling() {
        // Apply insets so the input bar shifts above the keyboard
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            // Use IME bottom if keyboard is visible, otherwise use system bar bottom
            val bottomPadding = if (imeInsets.bottom > 0) imeInsets.bottom else systemBarInsets.bottom
            view.updatePadding(bottom = bottomPadding)
            insets
        }
    }

    private fun loadInitialData() {
        val uri = selectedPdfUri ?: return

        lifecycleScope.launch {
            // Load saved Q&A
            val qaList = dao.getQaList(uri.toString())

            // Check for cached summary
            val cachedSummary = dao.getSummary(uri.toString())

            if (cachedSummary != null) {
                binding.tvCacheLabel.visibility = View.VISIBLE
                chatList.add(ChatItem(cachedSummary.summary, false))
            }

            qaList.forEach {
                chatList.add(ChatItem(it.question, true))
                chatList.add(ChatItem(it.answer, false))
            }

            chatAdapter.notifyDataSetChanged()
            scrollToBottom()

            // If no cache, start fresh summary
            if (cachedSummary == null) {
                viewModel.summarize(uri)
            }
        }
    }

    private fun showLoading(message: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvLoadingStatus.visibility = View.VISIBLE
        binding.tvLoadingStatus.text = message
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.GONE
        binding.tvLoadingStatus.visibility = View.GONE
    }

    private fun setupObservers() {
        viewModel.uiState.observe(this) { state ->
            when (state) {
                is SummaryViewModel.UiState.Loading -> {
                    showLoading(state.message)
                }
                is SummaryViewModel.UiState.PartialSuccess -> {
                    showLoading(state.message)
                }
                is SummaryViewModel.UiState.Success -> {
                    hideLoading()
                    val summaryText = state.summary
                    addAiMessage(summaryText)
                    saveSummaryLocally(summaryText)
                }
                is SummaryViewModel.UiState.Error -> {
                    hideLoading()
                    addAiMessage("Sorry, I couldn't summarize this PDF. Please check your connection.")
                }
                else -> {}
            }
        }

        viewModel.qaState.observe(this) { state ->
            when (state) {
                is QaState.Thinking -> {
                    showLoading("🧠 Thinking...")
                }
                is QaState.Answer -> {
                    hideLoading()
                    addAiMessage(state.answer)
                    saveQaLocally(lastQuestion, state.answer)
                    binding.etQuestion.text?.clear()
                }
                is QaState.Error -> {
                    hideLoading()
                    addAiMessage("Error: ${state.message}")
                }
                else -> {}
            }
        }
    }

    private fun addAiMessage(text: String) {
        chatList.add(ChatItem(text, false))
        chatAdapter.notifyItemInserted(chatList.size - 1)
        scrollToBottom()
    }

    private fun saveSummaryLocally(summaryText: String) {
        lifecycleScope.launch {
            dao.insertSummary(SummaryEntity(selectedPdfUri.toString(), summaryText))
        }
    }

    private fun saveQaLocally(question: String, answer: String) {
        lifecycleScope.launch {
            dao.insertQa(
                QaEntity(
                    pdfUri = selectedPdfUri.toString(),
                    question = question,
                    answer = answer
                )
            )
        }
    }

    private fun setupAsk() {
        binding.btnAsk.setOnClickListener {
            val question = binding.etQuestion.text.toString()
            if (question.isBlank()) return@setOnClickListener

            lastQuestion = question
            chatList.add(ChatItem(question, true))
            chatAdapter.notifyItemInserted(chatList.size - 1)
            scrollToBottom()

            selectedPdfUri?.let {
                viewModel.askQuestion(it, question)
            }
        }
    }

    private fun scrollToBottom() {
        if (chatList.isNotEmpty()) {
            binding.recyclerChat.post {
                binding.recyclerChat.smoothScrollToPosition(chatList.size - 1)
            }
        }
    }
}
