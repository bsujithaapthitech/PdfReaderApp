package com.example.pdfreaderapp.domain.usecase

import android.net.Uri
import com.example.pdfreaderapp.data.pdf.PdfTextExtractor
import com.example.pdfreaderapp.data.repository.SummaryRepository
import com.example.pdfreaderapp.domain.util.TextChunker
import com.example.pdfreaderapp.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicInteger

class SummarizePdfUseCase(
    private val extractor: PdfTextExtractor,
    private val chunker: TextChunker,
    private val repository: SummaryRepository
) {
    sealed class Progress {
        object Scanning : Progress()
        data class StatusUpdate(val message: String) : Progress()  // Live stage messages
        data class ChunksExtracted(val chunks: List<String>) : Progress()
        data class ChunkDone(val done: Int, val total: Int) : Progress()
        data class PartialSummary(val partialText: String) : Progress()
        object FinalSynthesis : Progress()
        data class Complete(val summary: String) : Progress()
        data class Error(val message: String) : Progress()
    }

    // Staged scanning messages — total ~30s (leaves ~6 min for API calls within 7 min hard cap)
    // Staged scanning messages to keep the user engaged
    private val scanStages = listOf(
        "🤖 Waking up our AI assistant..." to 4_000L,
        "📄 Working on it — Reading PDF structure..." to 5_000L,
        "🔍 Extracting text and context layers..." to 6_000L,
        "🧠 Analyzing content for key insights..." to 6_000L,
        "🧐 Deep scanning for facts and data..." to 5_000L,
        "📊 Organizing information into sections..." to 5_000L,
        "✨ Finalizing your AI summary..." to 4_000L,
    )

    /** Waits [waitSeconds] emitting a countdown via [onProgress], then returns. */
    private suspend fun rateLimitCountdown(
        waitSeconds: Int,
        onProgress: (Progress) -> Unit
    ) {
        var remaining = minOf(waitSeconds,30)
        while (remaining > 0) {
            onProgress(Progress.StatusUpdate("⏳ Rate limited — auto-retrying in ${remaining}s..."))
            delay(1000L)
            remaining -= 1
        }
    }

    /** Calls [block], and if it gets a 429, tries fallback then countdown. */
    private suspend fun withRateLimitRetry(
        onProgress: (Progress) -> Unit,
        block: suspend (String) -> Result<String>
    ): Result<String> {
        // 1. Try Primary
        val first = block(Constants.DEFAULT_MODEL)
        if (first.isFailure && first.exceptionOrNull()?.message == Constants.ERROR_RATE_LIMIT) {
            
            // 2. Try Fallback immediately
            onProgress(Progress.StatusUpdate("🔄 Primary model busy, trying fallback..."))
            val second = block(Constants.FALLBACK_MODEL)
            
            if (second.isFailure && second.exceptionOrNull()?.message == Constants.ERROR_RATE_LIMIT) {
                // 3. Both failed - do long wait
                rateLimitCountdown(Constants.RATE_LIMIT_BACKOFF_MS.toInt() / 1000, onProgress)
                return block(Constants.DEFAULT_MODEL) 
            }
            return second
        }
        return first
    }

    suspend fun execute(
        uri: Uri,
        onProgress: (Progress) -> Unit
    ) = coroutineScope {

        // Shared partial results — accessible even if hard timeout fires
        val partialBullets = mutableListOf<String>()

        try {
          withTimeout(Constants.HARD_TIMEOUT_MS) {

            // ── Step 1-2: Extraction & Chunking (Streaming) ───────────────
            onProgress(Progress.Scanning)

            val pages = mutableListOf<String>()
            extractor.extractTextStreaming(uri) { pageText ->
                if (pageText.isNotBlank()) pages.add(pageText)
            }

            if (pages.isEmpty()) {
                onProgress(Progress.Error("No readable text found. The PDF might be secure, empty, or an image scan without OCR."))
                return@withTimeout
            }
            
            // Re-combine and chunk respecting the strict 8000 constraint
            val chunks = chunker.chunkStreamedPages(pages)
            onProgress(Progress.ChunksExtracted(chunks))
            
            // To emulate fast-track size logic based on character roughly:
            val totalExtractedSize = pages.sumOf { it.length }

            // ── Step 3: Scan animation (~30s) ─────────────────────────────
            val scanningJob = launch {
                for ((message, delayMs) in scanStages) {
                    onProgress(Progress.StatusUpdate(message))
                    delay(delayMs)
                }
            }

            // ── Step 4: Short doc fast-track ──────────────────────────────
            if (totalExtractedSize < 5000) {
                scanningJob.join()
                onProgress(Progress.StatusUpdate("✨ Generating executive summary..."))
                delay(3_000L)
                onProgress(Progress.FinalSynthesis)

                val fullTextConcat = pages.joinToString("\n").take(Constants.TARGET_CHUNK_SIZE)
                val result = withRateLimitRetry(onProgress) { model -> 
                    repository.summarizeSinglePass(fullTextConcat, model = model) 
                }
                result.fold(
                    onSuccess = { onProgress(Progress.Complete(it)) },
                    onFailure = { onProgress(Progress.Error(it.message ?: "Analysis failed")) }
                )
                return@withTimeout
            }

            // ── Step 5: Batched parallel multi-chunk summarization ──────────
            scanningJob.join()
            onProgress(Progress.StatusUpdate("🚀 Sending batched content to AI engine..."))
            delay(3_000L)

            val semaphore = Semaphore(Constants.MAX_CONCURRENT_API_CALLS)
            // Hard cap to prevent runaway memory
            val apiChunks = if (chunks.size > Constants.MAX_CHUNKS) chunks.take(Constants.MAX_CHUNKS) else chunks

            val totalBatches = kotlin.math.ceil(apiChunks.size.toDouble() / Constants.MAX_CONCURRENT_API_CALLS).toInt()
            val completedCount = AtomicInteger(0)
            val totalChunks = apiChunks.size

            val batchChunks = apiChunks.chunked(Constants.MAX_CONCURRENT_API_CALLS)
            val level1Summaries = mutableListOf<String>()
            val batchBuffer = mutableListOf<Deferred<String?>>()

            batchChunks.forEachIndexed { batchIndex, batch ->
                onProgress(Progress.StatusUpdate("Summarizing batch ${batchIndex + 1} of $totalBatches"))

                coroutineScope {
                    batch.forEach { chunk ->
                        batchBuffer.add(async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val result = withRateLimitRetry(onProgress) { model -> 
                                    repository.summarizeChunk(chunk, model = model) 
                                }
                                val out = result.getOrNull() ?: ""

                                val done = completedCount.incrementAndGet()
                                onProgress(Progress.ChunkDone(done, totalChunks))

                                if (result.isFailure) {
                                    onProgress(Progress.StatusUpdate("⚠️ Chunk failed: ${result.exceptionOrNull()?.message}"))
                                }
                                out
                            }
                        })
                    }
                }

                // Await all AI responses for the current batch
                val batchResults = batchBuffer.awaitAll()
                batchResults.filterNotNull().filter { it.isNotBlank() }.forEach {
                    level1Summaries.add(it)
                    partialBullets.add(it)
                }

                if (partialBullets.isNotEmpty()) {
                    onProgress(Progress.PartialSummary(partialBullets.joinToString("\n\n")))
                }

                // CLEAR BUFFER memory explicitly
                batchBuffer.clear()

                if (batchIndex < totalBatches - 1) delay(Constants.BATCH_DELAY_MS)
            }

            // ── Step 6: Hierarchical Two-Level Final Synthesis ─────────────
            onProgress(Progress.StatusUpdate("Generating final synthesized summary..."))
            onProgress(Progress.FinalSynthesis)
            val allBullets = level1Summaries

            if (allBullets.isEmpty()) {
                onProgress(Progress.Error("Summarization failed to produce content."))
                return@withTimeout
            }

            val finalResult = repository.synthesizeFinal(allBullets)
            finalResult.fold(
                onSuccess = { onProgress(Progress.Complete(it)) },
                onFailure = { onProgress(Progress.Complete("Summary:\n" + allBullets.joinToString("\n"))) }
            )

          } // end withTimeout
        } catch (e: TimeoutCancellationException) {
            // Hard 7-min cap hit — show whatever partial results we collected
            if (partialBullets.isNotEmpty()) {
                onProgress(Progress.Complete("⏱️ Summary (time limit reached):\n" + partialBullets.joinToString("\n")))
            } else {
                onProgress(Progress.Error("Analysis took too long. Please try again with a smaller document."))
            }
        }

        System.gc()
    }
}
