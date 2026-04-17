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
     * Entry point used by ViewModel.
     * Runs extraction on IO thread to avoid blocking UI.
     */
    suspend fun extractAllTextParallel(uri: Uri): String = withContext(Dispatchers.IO) {
        extractText(uri)
    }

    /**
     * Optional fallback (same logic, no coroutine)
     */
    fun extractTextSequential(uri: Uri): String {
        return extractText(uri)
    }

    /**
     * Core extraction logic
     */
    private fun extractText(uri: Uri): String {

        var inputStream: InputStream? = null
        var document: PDDocument? = null

        return try {

            // Debug log to confirm URI being processed
            Log.d("PDF_DEBUG", "Starting extraction for URI: $uri")

            // Open file stream using content resolver
            inputStream = context.contentResolver.openInputStream(uri)

            // If stream fails → stop early (avoid crash)
            if (inputStream == null) {
                Log.e("PDF_DEBUG", "InputStream is null")
                return "Failed to read PDF"
            }

            // Load PDF document into memory
            document = PDDocument.load(inputStream)

            val pageCount = document.numberOfPages
            Log.d("PDF_DEBUG", "Page count: $pageCount")

            // Edge case: empty PDF
            if (pageCount == 0) {
                return "Empty PDF"
            }

            val stripper = PDFTextStripper()

            /**
             * IMPORTANT:
             * Limit extraction to first few pages.
             * Why:
             * - Full PDF extraction is slow (especially 100+ pages)
             * - AI only needs initial context
             * - Improves performance drastically
             */
            stripper.startPage = 1
            stripper.endPage = minOf(5, pageCount)

            // Extract text from selected pages
            val text = stripper.getText(document)

            // Log preview for debugging
            Log.d("PDF_DEBUG", "Extracted preview: ${text.take(200)}")

            // Handle case where PDF has no readable text (image PDFs etc.)
            if (text.isNullOrBlank()) {
                return "No readable text found in PDF"
            }

            // Return extracted text
            text

        } catch (e: Exception) {

            // Log full error for debugging
            Log.e("PDF_DEBUG", "Extraction failed: ${e.message}")

            // Return safe message instead of crashing
            "Error extracting PDF content"

        } finally {

            // Always close resources to avoid memory leaks
            try { document?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Streaming extraction (for very large PDFs)
     * Processes page-by-page instead of loading all text at once
     */
    suspend fun extractTextStreaming(
        uri: Uri,
        onPageExtracted: (String) -> Unit
    ) = withContext(Dispatchers.IO) {

        var inputStream: InputStream? = null
        var document: PDDocument? = null

        try {
            inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) return@withContext

            document = PDDocument.load(inputStream)

            val stripper = PDFTextStripper()
            val totalPages = document.numberOfPages

            for (i in 1..totalPages) {

                // Extract one page at a time (memory efficient)
                stripper.startPage = i
                stripper.endPage = i

                val pageText = stripper.getText(document)

                // Only send non-empty text
                if (!pageText.isNullOrBlank()) {
                    onPageExtracted(pageText)
                }
            }

        } catch (e: Exception) {
            Log.e("PDF_DEBUG", "Streaming failed: ${e.message}")
        } finally {
            try { document?.close() } catch (_: Exception) {}
            try { inputStream?.close() } catch (_: Exception) {}
        }
    }
}






















 /*
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


  */