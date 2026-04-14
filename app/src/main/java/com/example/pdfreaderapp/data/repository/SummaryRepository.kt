package com.example.pdfreaderapp.data.repository

import com.example.pdfreaderapp.data.api.ChatRequest
import com.example.pdfreaderapp.data.api.Message
import com.example.pdfreaderapp.data.api.OpenRouterApi
import com.example.pdfreaderapp.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import retrofit2.Response
import kotlin.random.Random

class SummaryRepository(private val apiService: OpenRouterApi) {

    // Ultra-Fast L1 Prompt
    private val L1_SYSTEM_PROMPT = """
        Surgical extraction engine. Output 3 bullets under 10 words each. 
        Raw facts only. No padding. Format: • [fact] • [fact] • [fact]
    """.trimIndent()

    // Ultra-Fast L2 Prompt
    private val L2_SYSTEM_PROMPT = """
        Direct executive synthesis. Write 2-3 flowing paragraphs. 
        Focus on core purpose and key results ONLY. Minimalist style.
    """.trimIndent()

    suspend fun summarizeSinglePass(
        text: String,
        model: String = Constants.DEFAULT_MODEL,
        retryCount: Int = 0
    ): Result<String> = try {
        withTimeout(Constants.API_TIMEOUT_MS) {
            val systemPrompt = "Detailed executive summary. Write 3 paragraphs. Focus on purpose, key facts, and conclusions. Direct prose."
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    Message(role = "system", content = systemPrompt),
                    Message(role = "user", content = text)
                )
            )
            val authHeader = "Bearer ${Constants.OPENROUTER_API_KEY}"
            val response = apiService.getCompletion(authHeader, request)
            handleApiResponse(response, text, retryCount) { summarizeSinglePass(it, model, retryCount + 1) }
        }
    } catch (e: Exception) {
        if (retryCount < 1) summarizeSinglePass(text, model, retryCount + 1)
        else Result.failure(e)
    }

    suspend fun summarizeChunk(
        text: String,
        model: String = Constants.DEFAULT_MODEL,
        retryCount: Int = 0
    ): Result<String> = try {
        withTimeout(Constants.API_TIMEOUT_MS) {
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    Message(role = "system", content = L1_SYSTEM_PROMPT),
                    Message(role = "user", content = text)
                )
            )
            val authHeader = "Bearer ${Constants.OPENROUTER_API_KEY}"
            val response = apiService.getCompletion(authHeader, request)
            handleApiResponse(response, text, retryCount) { summarizeChunk(it, model, retryCount + 1) }
        }
    } catch (e: Exception) {
        if (retryCount < 1) summarizeChunk(text, model, retryCount + 1)
        else Result.failure(e)
    }

    suspend fun synthesizeFinal(bullets: List<String>): Result<String> = try {
        withTimeout(Constants.API_TIMEOUT_MS) {
            val combined = bullets.joinToString("\n")
            val request = ChatRequest(
                model = Constants.DEFAULT_MODEL,
                messages = listOf(
                    Message(role = "system", content = L2_SYSTEM_PROMPT),
                    Message(role = "user", content = combined)
                )
            )
            val authHeader = "Bearer ${Constants.OPENROUTER_API_KEY}"
            val response = apiService.getCompletion(authHeader, request)
            if (response.isSuccessful) {
                val content = response.body()?.choices?.getOrNull(0)?.message?.content
                if (content != null) Result.success(content)
                else Result.failure(Exception("Empty synthesis"))
            } else Result.failure(Exception("Synthesis failed ${response.code()}"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun askQuestion(
        question: String,
        context: String,
        model: String = Constants.DEFAULT_MODEL,
        retryCount: Int = 0
    ): Result<String> = try {
        withTimeout(Constants.API_TIMEOUT_MS) {
            // Hard Context Trim
            val trimmedContext = if (context.length > Constants.QA_CONTEXT_LIMIT) {
                context.substring(0, Constants.QA_CONTEXT_LIMIT)
            } else context

            val systemPrompt = "Surgical Q&A. Answer strictly using context. 1 sentence max. No context = 'Not found'."
            val request = ChatRequest(
                model = model,
                messages = listOf(
                    Message(role = "system", content = systemPrompt),
                    Message(role = "user", content = "Context: $trimmedContext\n\nQuestion: $question")
                )
            )
            val authHeader = "Bearer ${Constants.OPENROUTER_API_KEY}"
            val response = apiService.getCompletion(authHeader, request)
            handleApiResponse(response, question, retryCount) { askQuestion(it, context, model, retryCount + 1) }
        }
    } catch (e: Exception) {
        if (retryCount < 1) askQuestion(question, context, model, retryCount + 1)
        else Result.failure(e)
    }

    private suspend fun handleApiResponse(
        response: Response<com.example.pdfreaderapp.data.api.ChatResponse>,
        input: String,
        retryCount: Int,
        retryBlock: suspend (String) -> Result<String>
    ): Result<String> {
        return try {
            when {
                response.isSuccessful -> {
                    val content = response.body()?.choices?.getOrNull(0)?.message?.content
                    if (content != null) Result.success(content)
                    else Result.failure(Exception("AI returned an empty response. Let's try once more."))
                }
                // Standardized rate limit error for UseCase detection
                response.code() == 429 && retryCount < 4 -> {
                    // Exponential backoff with jitter: (2^retry * 2s) + [0-1s]
                    val waitMs = (1 shl retryCount) * 2000L + Random.nextLong(1000)
                    kotlinx.coroutines.delay(waitMs)
                    retryBlock(input)
                }
                response.code() == 429 -> {
                    Result.failure(Exception(Constants.ERROR_RATE_LIMIT))
                }
                else ->
                    Result.failure(Exception("API error (${response.code()}): ${response.message()}"))
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Result.failure(Exception("AI analysis timed out. The document might be too complex for a quick response."))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
