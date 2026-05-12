package com.mahavtaar.csvkeyboard.ui.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.csv.CsvParser
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.prefs.AppPreferences
import com.mahavtaar.csvkeyboard.data.prefs.ColumnConfigStore
import com.mahavtaar.csvkeyboard.databinding.ActivityMainBinding
import com.mahavtaar.csvkeyboard.ui.floating.FloatingBallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isWaitingForOverlay = false

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            CsvRepository.saveUri(this, it)
            loadCsvData(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        checkOnboarding()
    }

    private fun setupListeners() {
        binding.btnEnableKeyboard.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }

        binding.btnSelectKeyboard.setOnClickListener {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        binding.btnLoadCsv.setOnClickListener {
            csvPicker.launch("text/*")
        }

        binding.btnEnableOverlay.setOnClickListener {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}"))
            startActivity(intent)
            isWaitingForOverlay = true
        }

        binding.switchFloatingBall.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                FloatingBallManager.start(this)
            } else {
                FloatingBallManager.stop(this)
            }
            binding.tvBallStatus.text = if (isChecked) "Floating ball is active ✓" else "Tap to show info bubble over any app"
        }
    }

    override fun onResume() {
        super.onResume()
        refreshAllPermissionStates()

        if (isWaitingForOverlay) {
            val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
            if (canDrawOverlays) {
                isWaitingForOverlay = false
                Toast.makeText(this, "Overlay permission granted!", Toast.LENGTH_SHORT).show()
                // Emphasize the floating ball
                binding.switchFloatingBall.isChecked = true
            }
        }
    }

    private fun checkOnboarding() {
        val onboardingDone = AppPreferences.getBoolean(this, "onboarding_done", false)
        if (!onboardingDone) {
            showOnboardingDialog()
        }
    }

    private fun showOnboardingDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_onboarding, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val btnAction = dialogView.findViewById<View>(R.id.btnOnboardingAction)
        val tvStep1Done = dialogView.findViewById<View>(R.id.tvStep1Done)
        val tvStep2Done = dialogView.findViewById<View>(R.id.tvStep2Done)
        val tvStep3Done = dialogView.findViewById<View>(R.id.tvStep3Done)

        fun updateDialogState() {
            val imeEnabled = isImeEnabled()
            val overlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
            val csvLoaded = CsvRepository.getSession() != null

            tvStep1Done.visibility = if (imeEnabled) View.VISIBLE else View.GONE
            tvStep2Done.visibility = if (overlayGranted) View.VISIBLE else View.GONE
            tvStep3Done.visibility = if (csvLoaded) View.VISIBLE else View.GONE

            when {
                !imeEnabled -> {
                    (btnAction as TextView).text = "Enable Keyboard"
                    btnAction.setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
                }
                !overlayGranted -> {
                    (btnAction as TextView).text = "Enable Overlay"
                    btnAction.setOnClickListener {
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${packageName}"))
                        startActivity(intent)
                    }
                }
                !csvLoaded -> {
                    (btnAction as TextView).text = "Load CSV"
                    btnAction.setOnClickListener { csvPicker.launch("text/*") }
                }
                else -> {
                    AppPreferences.save(this, "onboarding_done", true)
                    dialog.dismiss()
                }
            }
        }

        updateDialogState()

        // Auto-refresh dialog when returning to activity
        lifecycleScope.launch {
            while (dialog.isShowing) {
                delay(1000)
                updateDialogState()
            }
        }

        dialog.show()
    }

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.enabledInputMethodList.any { it.packageName == packageName }
    }

    private fun refreshAllPermissionStates() {
        val imeEnabled = isImeEnabled()
        val canDrawOverlays = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

        binding.btnEnableKeyboard.visibility = if (imeEnabled) View.GONE else View.VISIBLE
        binding.btnSelectKeyboard.visibility = if (imeEnabled) View.VISIBLE else View.GONE

        binding.btnEnableOverlay.visibility = if (canDrawOverlays) View.GONE else View.VISIBLE
        binding.cardFloatingBall.visibility = if (canDrawOverlays) View.VISIBLE else View.GONE

        binding.switchFloatingBall.setOnCheckedChangeListener(null)
        binding.switchFloatingBall.isChecked = FloatingBallManager.isRunning(this)
        binding.tvBallStatus.text = if (binding.switchFloatingBall.isChecked) "Floating ball is active ✓" else "Tap to show info bubble over any app"
        setupListeners() // reattach listener

        val session = CsvRepository.loadSessionFromPrefs(this)
        if (session != null) {
            binding.tvCsvStats.text = getString(R.string.csv_stats, session.totalRows, session.headers.size)
            binding.tvCsvStats.visibility = View.VISIBLE
        } else {
            binding.tvCsvStats.visibility = View.GONE
        }
    }

    private fun loadCsvData(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = CsvParser().parse(uri, this@MainActivity)
            withContext(Dispatchers.Main) {
                result.onSuccess { parseResult ->
                    CsvRepository.saveSessionData(this@MainActivity, parseResult.headers, parseResult.rows, uri.toString())
                    CsvRepository.loadSessionFromPrefs(this@MainActivity)?.let { CsvRepository.initSession(it) }

                    val currentConfigs = ColumnConfigStore.load(this@MainActivity)
                    if (currentConfigs == null || currentConfigs.size != parseResult.headers.size) {
                         ColumnConfigStore.save(this@MainActivity, ColumnConfigStore.generateDefaults(parseResult.headers))
                    }

                    showSuccessBottomSheet(parseResult.rows.size, parseResult.headers)
                    refreshAllPermissionStates()
                }.onFailure {
                    Toast.makeText(this@MainActivity, "Error parsing CSV: ${it.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showSuccessBottomSheet(rowCount: Int, headers: List<String>) {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_csv_success, null)
        dialog.setContentView(view)

        val tvDetails = view.findViewById<TextView>(R.id.tvCsvDetails)
        tvDetails.text = "Found $rowCount rows\nColumns detected: ${headers.joinToString(", ")}"

        view.findViewById<View>(R.id.btnConfigureColumns).setOnClickListener {
            Toast.makeText(this, "Configure Columns Coming in Phase 2", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }
}
