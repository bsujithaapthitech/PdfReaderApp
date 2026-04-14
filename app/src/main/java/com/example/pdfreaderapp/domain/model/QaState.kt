package com.example.pdfreaderapp.domain.model

sealed class QaState {
    object Idle : QaState()
    object Thinking : QaState()
    data class Answer(val question: String, val answer: String) : QaState()
    data class Error(val message: String) : QaState()
}
