package com.example.pdfreaderapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summary_table")
data class SummaryEntity(
    @PrimaryKey val pdfUri :String,
    val summary : String
)