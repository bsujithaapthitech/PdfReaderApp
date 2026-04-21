package com.example.pdfreaderapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.pdfreaderapp.databinding.ActivityPdfReaderBinding
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPdfReaderBinding
    private var selectedPdfUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PDFBoxResourceLoader.init(applicationContext)
        binding = ActivityPdfReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        loadPdfFromIntent()
    }

    private fun setupToolbar() {
        binding.topBar.setNavigationOnClickListener { finish() }
        
        binding.topBar.inflateMenu(R.menu.pdf_menu)
        binding.topBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_summarize -> {
                    navigateToSummary()
                    true
                }
                R.id.menu_find, R.id.menu_select -> {
                    Toast.makeText(this, "Feature coming soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
    }

    private fun navigateToSummary() {
        selectedPdfUri?.let { uri ->
            val intent = Intent(this, SummaryActivity::class.java).apply {
                putExtra("pdfUri", uri.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "No PDF loaded", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPdfFromIntent() {
        val uriString = intent.getStringExtra("pdfUri")
        if (uriString.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid PDF", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val uri = Uri.parse(uriString)
        selectedPdfUri = uri
        binding.topBar.title = getFileName(uri)
        loadPdf(uri)
    }

    private fun loadPdf(uri: Uri) {
        binding.pdfView.fromUri(uri)
            .enableSwipe(true)
            .swipeHorizontal(false)
            .enableDoubletap(true)
            .spacing(8)
            .onLoad { 
                // Any post-load logic
            }
            .onError {
                Toast.makeText(this, "Failed to load PDF", Toast.LENGTH_SHORT).show()
            }
            .load()
    }

    private fun getFileName(uri: Uri): String {
        var name = "PDF"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && index != -1) {
                name = cursor.getString(index)
            }
        }
        return name
    }
}