package com.example.pdfreaderapp.domain.util

import com.example.pdfreaderapp.util.Constants

object TextChunker {

    fun chunkText(text: String): List<String> {
        val chunks = mutableListOf<String>()
        var currentIndex = 0
        val textLength = text.length

        while (currentIndex < textLength && chunks.size < Constants.MAX_CHUNKS) {
            val endLimit = minOf(currentIndex + Constants.TARGET_CHUNK_SIZE, textLength)
            
            // If we are at the end, just add the rest and finish
            if (endLimit == textLength) {
                chunks.add(text.substring(currentIndex, endLimit).trim())
                break
            }

            // High-performance micro-splitting logic
            // Look backward from the target boundary for a natural break
            val window = text.substring(currentIndex, endLimit)
            val lastLineBreak = window.lastIndexOf('\n')
            val lastPeriod = window.lastIndexOf('.')
            
            // Priority: Newline > Period > Hard cut
            val cutOffset = when {
                lastLineBreak > Constants.TARGET_CHUNK_SIZE * 0.5 -> lastLineBreak + 1
                lastPeriod > Constants.TARGET_CHUNK_SIZE * 0.5 -> lastPeriod + 1
                else -> Constants.TARGET_CHUNK_SIZE
            }

            val chunkStr = text.substring(currentIndex, currentIndex + cutOffset).trim()
            if (chunkStr.isNotEmpty()) {
                chunks.add(chunkStr)
            }
            
            // Advance index but backtrack slightly for overlap to maintain contextual flow
            currentIndex += cutOffset
            if (currentIndex < textLength) {
                currentIndex = maxOf(0, currentIndex - Constants.CHUNK_OVERLAP)
            }
        }

        return chunks
    }

    fun chunkStreamedPages(pages: List<String>): List<String> {
        val chunks = mutableListOf<String>()
        var currentChunk = StringBuilder()
        
        for (page in pages) {
            if (currentChunk.length + page.length > Constants.TARGET_CHUNK_SIZE) {
                chunks.add(currentChunk.toString().trim())
                currentChunk.clear()
                if (chunks.size >= Constants.MAX_CHUNKS) break
            }
            currentChunk.append(page).append("\n")
        }
        
        if (currentChunk.isNotEmpty() && chunks.size < Constants.MAX_CHUNKS) {
            chunks.add(currentChunk.toString().trim())
        }
        return chunks
    }
}
