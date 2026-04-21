package com.example.pdfreaderapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.pdfreaderapp.data.local.entity.QaEntity
import com.example.pdfreaderapp.data.local.entity.SummaryEntity

@Dao
interface PdfDao {

    //Summary
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summaryEntity: SummaryEntity)

    @Query("SELECT * FROM summary_table WHERE pdfUri = :uri LIMIT 1 ")
    suspend fun getSummary(uri : String): SummaryEntity?

    //Q&A
    @Insert
    suspend fun insertQa(qa : QaEntity)

    @Query("SELECT * FROM qa_table WHERE pdfUri = :uri ")
    suspend fun getQaList(uri: String): List<QaEntity>

}