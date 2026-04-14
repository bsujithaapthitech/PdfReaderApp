package com.example.pdfreaderapp.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.IOException

class PdfRendererHelper(private val context: Context) {

    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null

    fun open(uri: Uri): Int {
        close() // Close old renderer if any
        return try {
            parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
            parcelFileDescriptor?.let {
                val renderer = PdfRenderer(it)
                pdfRenderer = renderer
                renderer.pageCount
            } ?: 0
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    fun renderPage(index: Int): Bitmap? {
        val renderer = pdfRenderer ?: return null
        if (index < 0 || index >= renderer.pageCount) return null

        return try {
            val page = renderer.openPage(index)
            // Create a bitmap with the page dimensions
            // We can scale this if needed, but for now use natural size or max screen width
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            
            // Clear the bitmap with white (PdfRenderer doesn't always clear)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getPageCount(): Int = pdfRenderer?.pageCount ?: 0

    fun close() {
        try {
            pdfRenderer?.close()
            parcelFileDescriptor?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        } finally {
            pdfRenderer = null
            parcelFileDescriptor = null
        }
    }
}
