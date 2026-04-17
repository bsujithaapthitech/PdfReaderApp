package com.example.pdfreaderapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.pdfreaderapp.databinding.ActivityHomeScreenBinding
import java.util.Calendar

class HomeScreenActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeScreenBinding

    // List to hold recent PDFs (dynamic)
    private val pdfList = mutableListOf<PdfItem>()

    // RecyclerView adapter
    private lateinit var adapter: PdfAdapter


    // PDF picker using OpenDocument (persistent permission)
    private val pdfPickerLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->

            binding.fabAdd.isEnabled = true

            uri?.let {

                // Persist permission so we can access PDF later (important for SummaryActivity)
                contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )

                // Create PDF item with proper file name
                val pdf = PdfItem(
                    name = getFileName(it),
                    uri = it.toString()
                )

                // Prevent duplicate entries (optional but useful)
                if (pdfList.any { item -> item.uri == pdf.uri }) return@let

                // Add new PDF to top of list
                pdfList.add(0, pdf)
                adapter.notifyItemInserted(0)

                // Scroll to top for better UX
                binding.recyclerRecent.scrollToPosition(0)

                // Save list locally
                savePdfList()

                // Update empty state UI
                updateEmptyState()

                // Open PDF reader screen
                openReaderScreen(it)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityHomeScreenBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setGreeting()

        // Setup RecyclerView
        setupRecycler()

        // Load saved PDFs
        loadPdfList()

        // Update UI based on list
        updateEmptyState()

        // Setup click listeners
        setUpUI()
    }

    // Set greeting based on time
    private fun setGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val greeting = when {
            hour < 12 -> "Good morning 👋"
            hour < 18 -> "Good afternoon 👋"
            else -> "Good evening 👋"
        }

        binding.tvGreeting.text = greeting
    }

    // Setup RecyclerView
    private fun setupRecycler() {

        adapter = PdfAdapter(pdfList) { pdf ->

            // Open reader when item clicked
            val intent = Intent(this, PdfReaderActivity::class.java)
            intent.putExtra("pdfUri", pdf.uri)
            startActivity(intent)
        }

        binding.recyclerRecent.layoutManager = LinearLayoutManager(this)
        binding.recyclerRecent.adapter = adapter
    }

    // FAB click animation + open picker
    private fun setUpUI() {
        binding.fabAdd.setOnClickListener {

            it.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).duration = 100
                openPdfPicker()
            }
        }
    }

    // Launch PDF picker
    private fun openPdfPicker() {
        binding.fabAdd.isEnabled = false
        pdfPickerLauncher.launch(arrayOf("application/pdf"))
    }

    // Open PDF Reader screen
    private fun openReaderScreen(uri: Uri) {
        val intent = Intent(this, PdfReaderActivity::class.java).apply {
            putExtra("pdfUri", uri.toString())
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivity(intent)
    }

    // Save PDF list to SharedPreferences
    private fun savePdfList() {

        val prefs = getSharedPreferences("pdf_prefs", MODE_PRIVATE)

        val json = com.google.gson.Gson().toJson(pdfList)

        // Use commit to ensure immediate save
        prefs.edit().putString("pdf_list", json).commit()
    }

    // Load PDF list from SharedPreferences
    private fun loadPdfList() {

        val prefs = getSharedPreferences("pdf_prefs", MODE_PRIVATE)

        val json = prefs.getString("pdf_list", null)

        if (!json.isNullOrEmpty()) {

            val type = object :
                com.google.gson.reflect.TypeToken<MutableList<PdfItem>>() {}.type

            val savedList: MutableList<PdfItem> =
                com.google.gson.Gson().fromJson(json, type)

            pdfList.clear()
            pdfList.addAll(savedList)

            adapter.notifyDataSetChanged()
        }
    }

    // Show/hide empty state
    private fun updateEmptyState() {

        if (pdfList.isEmpty()) {
            binding.recyclerRecent.visibility = View.GONE
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.recyclerRecent.visibility = View.VISIBLE
            binding.tvEmpty.visibility = View.GONE
        }
    }

    // Get proper file name from URI
    private fun getFileName(uri: Uri): String {

        var name = "PDF"

        val cursor = contentResolver.query(uri, null, null, null, null)

        cursor?.use {

            val nameIndex =
                it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)

            if (it.moveToFirst() && nameIndex != -1) {
                name = it.getString(nameIndex)
            }
        }

        return name
    }
}