package com.example.pdfreaderapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.pdfreaderapp.databinding.ActivityPdfReaderBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfReaderBinding

    // Stores selected PDF URI
    private var selectedPdfUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔹 Initialize PDFBox (needed for text extraction later)
        PDFBoxResourceLoader.init(applicationContext)

        enableEdgeToEdge()

        // 🔹 ViewBinding setup
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        loadPdfFromIntent()
    }

    // 🔹 Setup toolbar + button actions

    private fun setupUI() {

        // Back button
        binding.topBar.setNavigationOnClickListener {
            finish()
        }

        // Summarize button
        binding.btnSummarize.setOnClickListener {

            if (selectedPdfUri == null) {
                Toast.makeText(this, "No PDF loaded", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(this, SummaryActivity::class.java)

            // ✅ Correct place to log
            android.util.Log.d("READER_DEBUG", "Sending URI: $selectedPdfUri")

            intent.putExtra("pdfUri", selectedPdfUri?.toString())
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            startActivity(intent)
        }
    }
    // 🔹 Get URI from previous screen
    private fun loadPdfFromIntent() {
        val uriString = intent.getStringExtra("pdfUri")

        android.util.Log.d("READER_DEBUG", "Received URI in Reader: $uriString")

        if (uriString.isNullOrEmpty() || uriString == "null") {
            Toast.makeText(this, "Invalid PDF", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uri = Uri.parse(uriString)
        selectedPdfUri = uri

        binding.topBar.title = getFileName(uri)

        loadPdf(uri)
    }
    // 🔹 Load PDF into viewer
    private fun loadPdf(uri: Uri) {

        // Show loader
        binding.progressPdf.visibility = View.VISIBLE

        // Disable summarize until loaded
        binding.btnSummarize.isEnabled = false

        binding.pdfView.fromUri(uri)
            .enableSwipe(true)          // Scroll pages
            .swipeHorizontal(false)    // Vertical scroll
            .enableDoubletap(true)     // Zoom support
            .spacing(8)                // Page spacing

            // 🔹 Called when PDF fully loaded
            .onLoad { totalPages ->
                binding.progressPdf.visibility = View.GONE
                binding.txtPage.text = "Page 1/$totalPages"

                binding.btnSummarize.isEnabled = true
            }

            // 🔹 Page change listener
            .onPageChange { page, pageCount ->
                binding.txtPage.text = "Page ${page + 1}/$pageCount"
            }

            // 🔹 Error handling
            .onError {
                binding.progressPdf.visibility = View.GONE
                binding.btnSummarize.isEnabled = true

                Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show()
            }

            .load()
    }

    // 🔹 Utility: Get clean file name
    private fun getFileName(uri: Uri): String {
        var name = "PDF"

        val cursor = contentResolver.query(uri, null, null, null, null)

        cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (it.moveToFirst() && index != -1) {
                name = it.getString(index)
            }
        }

        return name
    }
}