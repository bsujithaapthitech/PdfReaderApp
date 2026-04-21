package com.example.pdfreaderapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pdfreaderapp.databinding.ActivityHomeScreenBinding

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeScreenBinding
    private val pdfList = mutableListOf<PdfItem>()
    private lateinit var adapter: PdfAdapter

    private val pdfPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            binding.btnUpload.isEnabled = true
            uri?.let {
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                val pdf = PdfItem(
                    name = getFileName(it),
                    uri = it.toString()
                )

                if (pdfList.none { item -> item.uri == pdf.uri }) {
                    pdfList.add(0, pdf)
                    adapter.notifyItemInserted(0)
                    binding.rvRecent.scrollToPosition(0)
                    savePdfList()
                    updateEmptyState()
                }
                openReaderScreen(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
        loadPdfList()
        updateEmptyState()
        setUpClickListeners()
    }

    private fun setupRecycler() {
        adapter = PdfAdapter(pdfList) { pdf ->
            val intent = Intent(this, PdfReaderActivity::class.java)
            intent.putExtra("pdfUri", pdf.uri)
            startActivity(intent)
        }

        binding.rvRecent.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecent.adapter = adapter
    }

    private fun setUpClickListeners() {
        binding.btnUpload.setOnClickListener {
            openPdfPicker()
        }

        binding.cardSummarize.setOnClickListener {
            if (pdfList.isEmpty()) {
                openPdfPicker()
            } else {
                Toast.makeText(this, "Select a PDF to summarize", Toast.LENGTH_SHORT).show()
            }
        }

        binding.cardAsk.setOnClickListener {
            Toast.makeText(this, "Select a PDF to ask questions", Toast.LENGTH_SHORT).show()
        }

        binding.cardInsights.setOnClickListener {
            Toast.makeText(this, "Analytics coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPdfPicker() {
        binding.btnUpload.isEnabled = false
        pdfPickerLauncher.launch(arrayOf("application/pdf"))
    }

    private fun openReaderScreen(uri: Uri) {
        val intent = Intent(this, PdfReaderActivity::class.java).apply {
            putExtra("pdfUri", uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        startActivity(intent)
    }

    private fun savePdfList() {
        val prefs = getSharedPreferences("pdf_prefs", MODE_PRIVATE)
        val json = com.google.gson.Gson().toJson(pdfList)
        prefs.edit().putString("pdf_list", json).apply()
    }

    private fun loadPdfList() {
        val prefs = getSharedPreferences("pdf_prefs", MODE_PRIVATE)
        val json = prefs.getString("pdf_list", null)
        if (!json.isNullOrEmpty()) {
            val type = object : com.google.gson.reflect.TypeToken<MutableList<PdfItem>>() {}.type
            val savedList: MutableList<PdfItem> = com.google.gson.Gson().fromJson(json, type)
            pdfList.clear()
            pdfList.addAll(savedList)
            adapter.notifyDataSetChanged()
        }
    }

    private fun updateEmptyState() {
        if (pdfList.isEmpty()) {
            binding.rvRecent.visibility = View.GONE
            binding.tvEmptyRecent.visibility = View.VISIBLE
        } else {
            binding.rvRecent.visibility = View.VISIBLE
            binding.tvEmptyRecent.visibility = View.GONE
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = "PDF"
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}