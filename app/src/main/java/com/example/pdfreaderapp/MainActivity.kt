package com.example.pdfreaderapp

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.pdfreaderapp.databinding.ActivityMainBinding
import com.github.barteksc.pdfviewer.PDFView
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
class MainActivity : AppCompatActivity() {

    // ViewBinding instance
    private lateinit var binding: ActivityMainBinding

    // PDF viewer instance
    private lateinit var pdfView: PDFView

    // Stores last opened page (basic state handling)
    private var lastPage = 0


    private var selectedPdfUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ViewBinding
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)
        PDFBoxResourceLoader.init(applicationContext)

        // Initialize PDFView from layout
        pdfView = binding.pdfView

        // Button to pick PDF from device storage
        binding.btnOpen.setOnClickListener {
            openPdfFromStorage()
        }
        binding.btnExtract.setOnClickListener {
            selectedPdfUri?.let {
                extractTextFromPdf(it)
            } ?: Toast.makeText(this, "Select PDF first", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Opens file picker to select a PDF from device storage
     */
    private fun openPdfFromStorage() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "application/pdf" // Filter only PDF files
        }
        startActivityForResult(intent, 100)
    }

    /**
     * Handles result from file picker
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {

            // Safely get selected file URI
            val uri = data?.data ?: return

            selectedPdfUri = uri
            // Update app title with file name
            title = uri.lastPathSegment

            // Load selected PDF into PDFView
            pdfView.fromUri(uri)
                .enableSwipe(true)              // Enable page swiping
                .swipeHorizontal(false)         // Vertical scrolling
                .enableDoubletap(true)          // Enable zoom on double tap
                .defaultPage(lastPage)          // Open last viewed page
                .onPageChange { page, pageCount ->
                    // Update page indicator
                    binding.txtPage.text = "Page ${page + 1} / $pageCount"

                    // Save current page
                    lastPage = page
                }
                .onLoad {
                    // Called when PDF is fully loaded
                    Toast.makeText(this, "PDF Loaded", Toast.LENGTH_SHORT).show()
                }
                .onError {
                    // Handle loading error
                    Toast.makeText(this, "Failed to open PDF", Toast.LENGTH_SHORT).show()
                }
                .load()
        }
    }

    private fun extractTextFromPdf(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val document = PDDocument.load(inputStream)

            val stripper = PDFTextStripper()
            val text = stripper.getText(document)

            binding.txtExtracted.text = text

            document.close()
            inputStream?.close()

        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error extracting text", Toast.LENGTH_SHORT).show()
        }
    }
}