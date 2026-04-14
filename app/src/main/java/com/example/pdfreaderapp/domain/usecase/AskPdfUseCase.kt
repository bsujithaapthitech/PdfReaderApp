package com.example.pdfreaderapp.domain.usecase

import android.net.Uri
import com.example.pdfreaderapp.data.pdf.PdfTextExtractor
import com.example.pdfreaderapp.data.repository.SummaryRepository
import com.example.pdfreaderapp.domain.util.TextChunker
import com.example.pdfreaderapp.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.max
import kotlin.math.min

class AskPdfUseCase(
    private val pdfExtractor: PdfTextExtractor,
    private val repository: SummaryRepository
) {

    /**
     * Executes a contextual Q&A query against a PDF.
     * Uses term-frequency scoring to find precise dense segments of context.
     */
    suspend fun execute(
        uri: Uri, 
        question: String, 
        cachedChunks: List<String>? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. Get Chunks (Reuse cache or scan if needed)
            val chunks = if (!cachedChunks.isNullOrEmpty()) {
                cachedChunks
            } else {
                var fullText = pdfExtractor.extractAllTextParallel(uri)
                if (fullText.length < Constants.STABILITY_THRESHOLD_CHARS) {
                    fullText = pdfExtractor.extractTextSequential(uri)
                }
                
                if (fullText.isBlank()) return@withContext Result.failure(Exception("No text found in PDF"))
                TextChunker.chunkText(fullText)
            }

            // 2. High-Precision TF-IDF emulation
            val keywords = question.lowercase(Locale.ROOT)
                .replace(Regex("[^a-z0-9\\s]"), "")
                .split(" ")
                .filter { it.length > 3 } // Filter out stop words
                
            if (keywords.isEmpty()) {
                // Fallback for short questions like "Who am I" (though unlikely to have meaning)
                return@withContext repository.askQuestion(question, chunks.take(2).joinToString("\n"))
            }

            val scoredChunks = chunks.map { chunk ->
                val chunkLower = chunk.lowercase(Locale.ROOT)
                var score = 0
                val matchIndices = mutableListOf<Int>()
                
                keywords.forEach { keyword ->
                    var idx = chunkLower.indexOf(keyword)
                    while (idx >= 0) {
                        score++
                        matchIndices.add(idx)
                        idx = chunkLower.indexOf(keyword, idx + keyword.length)
                    }
                }
                
                // 3. Precise Window Extraction: focus on the dense area where keywords strike
                val denseContext = if (matchIndices.isNotEmpty()) {
                    val averageIndex = matchIndices.sum() / matchIndices.size
                    val windowSize = 1500
                    val start = max(0, averageIndex - windowSize / 2)
                    val end = min(chunk.length, averageIndex + windowSize / 2)
                    chunk.substring(start, end)
                } else chunk.take(1500)

                Triple(chunk, score, denseContext)
            }.filter { it.second > 0 }.sortedByDescending { it.second }

            // 4. Extract Top Context (Fallback to regular top 2 chunks if no matches at all)
            val context = if (scoredChunks.isNotEmpty()) {
                scoredChunks.take(3).joinToString("\n---\n") { it.third }
            } else {
                chunks.take(2).joinToString("\n---\n")
            }

            // 5. Query Repository
            repository.askQuestion(question, context)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
