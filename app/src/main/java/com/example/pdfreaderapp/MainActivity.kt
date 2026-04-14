package com.example.pdfreaderapp

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.pdfreaderapp.data.api.NetworkModule
import com.example.pdfreaderapp.data.pdf.PdfRendererHelper
import com.example.pdfreaderapp.data.pdf.PdfTextExtractor
import com.example.pdfreaderapp.data.repository.SummaryRepository
import com.example.pdfreaderapp.databinding.ActivityMainBinding
import com.example.pdfreaderapp.domain.model.QaState
import com.example.pdfreaderapp.domain.usecase.AskPdfUseCase
import com.example.pdfreaderapp.domain.usecase.SummarizePdfUseCase
import com.example.pdfreaderapp.domain.util.TextChunker
import com.example.pdfreaderapp.ui.viewmodel.SummaryViewModel
import com.example.pdfreaderapp.ui.viewmodel.SummaryViewModelFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    
    private val viewModel: SummaryViewModel by viewModels {
        val extractor = PdfTextExtractor(this)
        val repository = SummaryRepository(NetworkModule.openRouterApi)
        val summarizeUseCase = SummarizePdfUseCase(extractor, TextChunker, repository)
        val askPdfUseCase = AskPdfUseCase(extractor, repository)
        SummaryViewModelFactory(summarizeUseCase, askPdfUseCase)
    }

    private var selectedPdfUri: Uri? = null
    private var currentPageIndex = 0
    private lateinit var pdfRendererHelper: PdfRendererHelper

    private val pdfPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedPdfUri = it
            viewModel.resetState()
            loadPdf(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        
        PDFBoxResourceLoader.init(applicationContext)
        pdfRendererHelper = PdfRendererHelper(this)

        setupClickListeners()
        observeViewModel()
    }

    override fun onDestroy() {
        pdfRendererHelper.close()
        super.onDestroy()
    }

    private fun setupClickListeners() {
        binding.btnOpen.setOnClickListener {
            openPdfFromStorage()
        }

        binding.btnSummarize.setOnClickListener {
            selectedPdfUri?.let {
                viewModel.summarize(it)
            } ?: Toast.makeText(this, "Please select a PDF first", Toast.LENGTH_SHORT).show()
        }

        binding.btnAsk.setOnClickListener {
            val question = binding.etQuestion.text.toString()
            selectedPdfUri?.let { uri ->
                viewModel.askQuestion(uri, question)
            } ?: Toast.makeText(this, "Please select a PDF first", Toast.LENGTH_SHORT).show()
        }

        binding.btnNext.setOnClickListener {
            if (currentPageIndex < pdfRendererHelper.getPageCount() - 1) {
                currentPageIndex++
                renderCurrentPage()
            }
        }

        binding.btnPrev.setOnClickListener {
            if (currentPageIndex > 0) {
                currentPageIndex--
                renderCurrentPage()
            }
        }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(this) { state ->
            updateUiState(state)
        }
        viewModel.qaState.observe(this) { state ->
            handleQaState(state)
        }
    }

    private fun updateUiState(state: SummaryViewModel.UiState) {
        when (state) {
            is SummaryViewModel.UiState.Idle -> {
                binding.layoutSummaryLoading.visibility = View.GONE
            }
            is SummaryViewModel.UiState.Loading -> {
                binding.layoutSummaryLoading.visibility = View.VISIBLE
                binding.txtSummaryStatus.text = state.message
                binding.txtSummary.alpha = 0.5f // Dim previous text
            }
            is SummaryViewModel.UiState.PartialSuccess -> {
                binding.layoutSummaryLoading.visibility = View.VISIBLE
                binding.txtSummaryStatus.text = state.message
                binding.txtSummary.text = state.partialSummary
                binding.txtSummary.alpha = 0.8f // Keep slightly dimmed to indicate it's partial
                binding.txtSummary.setTextColor(getColor(R.color.slate_900))
            }
            is SummaryViewModel.UiState.Success -> {
                binding.layoutSummaryLoading.visibility = View.GONE
                binding.txtSummary.text = state.summary
                binding.txtSummary.alpha = 1.0f
                binding.txtSummary.setTextColor(getColor(R.color.slate_900))
                System.gc()
            }
            is SummaryViewModel.UiState.Error -> {
                binding.layoutSummaryLoading.visibility = View.GONE
                binding.txtSummary.text = "Error: ${state.message}"
                binding.txtSummary.alpha = 1.0f
                binding.txtSummary.setTextColor(getColor(R.color.accent))
            }
        }
    }

    private fun handleQaState(state: QaState) {
        when (state) {
            is QaState.Idle -> {
                binding.qaProgress.visibility = View.GONE
                binding.btnAsk.isEnabled = true
            }
            is QaState.Thinking -> {
                binding.qaProgress.visibility = View.VISIBLE
                binding.btnAsk.isEnabled = false
            }
            is QaState.Answer -> {
                binding.qaProgress.visibility = View.GONE
                binding.btnAsk.isEnabled = true
                binding.etQuestion.text.clear()
                
                val formattedAnswer = "Q: ${state.question}\n\nA: ${state.answer}"
                binding.txtSummary.text = formattedAnswer
                binding.txtSummary.alpha = 1.0f
                binding.txtSummary.setTextColor(getColor(R.color.primary))
                System.gc()
            }
            is QaState.Error -> {
                binding.qaProgress.visibility = View.GONE
                binding.btnAsk.isEnabled = true
                Toast.makeText(this, "Q&A Error: ${state.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openPdfFromStorage() {
        pdfPickerLauncher.launch("application/pdf")
    }

    private fun loadPdf(uri: Uri) {
        val pageCount = pdfRendererHelper.open(uri)
        if (pageCount > 0) {
            currentPageIndex = 0
            binding.layoutNav.visibility = View.VISIBLE
            binding.txtPageIndicator.text = "Page 1 of $pageCount"
            renderCurrentPage()
        } else {
            binding.layoutNav.visibility = View.GONE
            Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderCurrentPage() {
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                pdfRendererHelper.renderPage(currentPageIndex)
            }
            bitmap?.let {
                binding.imgPdfPage.setImageBitmap(it)
                val totalPages = pdfRendererHelper.getPageCount()
                binding.txtPageIndicator.text = "Page ${currentPageIndex + 1} of $totalPages"
                binding.btnPrev.isEnabled = currentPageIndex > 0
                binding.btnNext.isEnabled = currentPageIndex < (totalPages - 1)
            }
        }
    }
}