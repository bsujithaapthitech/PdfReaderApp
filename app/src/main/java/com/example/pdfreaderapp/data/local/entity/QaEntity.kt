package com.example.pdfreaderapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey


@Entity(tableName = "qa_table")
data class QaEntity(
    @PrimaryKey(autoGenerate = true) val id : Int = 0,
    val pdfUri : String,
    val question : String,
    val answer: String
)