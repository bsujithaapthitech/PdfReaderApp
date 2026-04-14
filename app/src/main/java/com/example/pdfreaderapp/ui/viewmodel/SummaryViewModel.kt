package com.example.pdfreaderapp.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdfreaderapp.domain.usecase.AskPdfUseCase
import com.example.pdfreaderapp.domain.usecase.SummarizePdfUseCase
import com.example.pdfreaderapp.domain.model.QaState
import kotlinx.coroutines.launch

class SummaryViewModel(
    private val summarizeUseCase: SummarizePdfUseCase,
    private val askPdfUseCase: AskPdfUseCase
) : ViewModel() {

    sealed class UiState {
        object Idle : UiState()
        data class Loading(val message: String) : UiState()
        data class PartialSuccess(val partialSummary: String, val message: String) : UiState()
        data class Success(val summary: String) : UiState()
        data class Error(val message: String) : UiState()
    }

    private var currentChunks: List<String>? = null

    private val _uiState = MutableLiveData<UiState>(UiState.Idle)
    val uiState: LiveData<UiState> = _uiState

    private val _qaState = MutableLiveData<QaState>(QaState.Idle)
    val qaState: LiveData<QaState> = _qaState

    fun summarize(uri: Uri) {
        viewModelScope.launch {
            summarizeUseCase.execute(uri) { progress ->
                when (progress) {
                    is SummarizePdfUseCase.Progress.Scanning ->
                        _uiState.postValue(UiState.Loading("Initializing document reader..."))

                    is SummarizePdfUseCase.Progress.StatusUpdate ->
                        _uiState.postValue(UiState.Loading(progress.message))

                    is SummarizePdfUseCase.Progress.ChunksExtracted -> {
                        currentChunks = progress.chunks
                        _uiState.postValue(UiState.Loading("📄 Reading document structure..."))
                    }

                    is SummarizePdfUseCase.Progress.ChunkDone -> {
                        if (_uiState.value !is UiState.PartialSuccess) {
                            _uiState.postValue(UiState.Loading("Processing segment ${progress.done}/${progress.total}..."))
                        }
                    }

                    is SummarizePdfUseCase.Progress.PartialSummary -> {
                        _uiState.postValue(UiState.PartialSuccess(progress.partialText, "Formulating partial insights..."))
                    }

                    is SummarizePdfUseCase.Progress.FinalSynthesis ->
                        _uiState.postValue(UiState.Loading("✨ Crafting final summary..."))

                    is SummarizePdfUseCase.Progress.Complete -> {
                        _uiState.postValue(UiState.Success(progress.summary))
                    }

                    is SummarizePdfUseCase.Progress.Error -> {
                        _uiState.postValue(UiState.Error(progress.message))
                    }
                }
            }
        }
    }

    fun askQuestion(uri: Uri, question: String) {
        if (question.isBlank()) return
        
        viewModelScope.launch {
            _qaState.postValue(QaState.Thinking)
            val result = askPdfUseCase.execute(uri, question, currentChunks)
            result.fold(
                onSuccess = { answer ->
                    _qaState.postValue(QaState.Answer(question, answer))
                },
                onFailure = { error ->
                    _qaState.postValue(QaState.Error(error.message ?: "Unknown error"))
                }
            )
        }
    }
    
    fun resetState() {
        _uiState.value = UiState.Idle
        _qaState.value = QaState.Idle
        currentChunks = null
    }
}

class SummaryViewModelFactory(
    private val summarizeUseCase: SummarizePdfUseCase,
    private val askPdfUseCase: AskPdfUseCase
) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SummaryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SummaryViewModel(summarizeUseCase, askPdfUseCase) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
