package com.mahavtaar.csvkeyboard.ui.setup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.csv.CsvParser
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences
import com.mahavtaar.csvkeyboard.data.prefs.ColumnConfigStore
import com.mahavtaar.csvkeyboard.databinding.ActivityMainBinding
import com.mahavtaar.csvkeyboard.ui.floating.FloatingBallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            loadCsvData(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        binding.btnSelectKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        binding.btnLoadCsv.setOnClickListener {
            csvPicker.launch("text/*") // Android's generic text picker works best for CSV sometimes
        }

        binding.btnEnableOverlay.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:\$packageName"))
            startActivity(intent)
        }

        binding.switchFloatingBall.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                FloatingBallManager.start(this)
            } else {
                FloatingBallManager.stop(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUiState()
    }

    private fun updateUiState() {
        val canDrawOverlays = Settings.canDrawOverlays(this)
        binding.btnEnableOverlay.visibility = if (canDrawOverlays) android.view.View.GONE else android.view.View.VISIBLE
        binding.switchFloatingBall.isEnabled = canDrawOverlays
        binding.switchFloatingBall.isChecked = AppPreferences.getBoolean(this, AppPreferences.KEY_BALL_ENABLED)

        val session = CsvRepository.loadSessionFromPrefs(this)
        if (session != null) {
            binding.tvCsvStats.text = getString(R.string.csv_stats, session.totalRows, session.headers.size)
        }
    }

    private fun loadCsvData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = CsvParser().parse(uri, this@MainActivity)
            withContext(Dispatchers.Main) {
                result.onSuccess { parseResult ->
                    CsvRepository.saveSessionData(this@MainActivity, parseResult.headers, parseResult.rows, uri.toString())
                    // Init session so it's ready right away
                    CsvRepository.loadSessionFromPrefs(this@MainActivity)?.let { CsvRepository.initSession(it) }

                    val currentConfigs = ColumnConfigStore.load(this@MainActivity)
                    if (currentConfigs == null || currentConfigs.size != parseResult.headers.size) {
                         ColumnConfigStore.save(this@MainActivity, ColumnConfigStore.generateDefaults(parseResult.headers))
                    }

                    Toast.makeText(this@MainActivity, "CSV Loaded Successfully", Toast.LENGTH_SHORT).show()
                    updateUiState()
                }.onFailure {
                    Toast.makeText(this@MainActivity, "Error parsing CSV: \${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
