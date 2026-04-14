package com.example.pdfreaderapp.data.pdf

import android.content.Context
import android.net.Uri
import android.util.Log
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

class PdfTextExtractor(private val context: Context) {

    /**
     * Highly-optimized sequential extraction.
     * Parallel loading of PDDocument was creating massive memory overhead and redundant 
     * structural parsing, crippling latency for mid-to-large PDFs.
     */
    suspend fun extractAllTextParallel(uri: Uri): String = withContext(Dispatchers.IO) {
        extractText(uri)
    }

    /**
     * Sequential fallback (Low memory, High reliability)
     */
    fun extractTextSequential(uri: Uri): String {
        return extractText(uri)
    }

    private fun extractText(uri: Uri): String {
        val sb = StringBuilder()
        var inputStream: InputStream? = null
        var document: PDDocument? = null
        return try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return ""
            document = PDDocument.load(inputStream)
            
            if (document.numberOfPages == 0) return ""
            
            // Extract all text sequentially - this is drastically faster than allocating
            // multiple PDDocument instances and avoids large memory spikes.
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            
            sb.append(text ?: "")
            sb.toString()
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Extraction failed: ${e.message}")
            ""
        } finally {
            try { document?.close() } catch (e: Exception) {}
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Memory-efficient streaming fallback for huge documents (300-500+ pages).
     */
    suspend fun extractTextStreaming(uri: Uri, onPageExtracted: (String) -> Unit) = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var document: PDDocument? = null
        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext
            document = PDDocument.load(inputStream)
            
            val stripper = PDFTextStripper()
            val totalPages = document.numberOfPages
            
            for (i in 1..totalPages) {
                stripper.startPage = i
                stripper.endPage = i
                val pageText = stripper.getText(document)
                if (pageText != null) {
                    onPageExtracted(pageText)
                }
            }
        } catch (e: Exception) {
            Log.e("PdfTextExtractor", "Extraction failed: ${e.message}")
        } finally {
            try { document?.close() } catch (e: Exception) {}
            try { inputStream?.close() } catch (e: Exception) {}
        }
    }
}
