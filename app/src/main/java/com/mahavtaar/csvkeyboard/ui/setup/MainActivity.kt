package com.mahavtaar.csvkeyboard.ui.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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

    companion object {
        const val REQUEST_CODE_NOTIFICATION = 1002
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_CODE_NOTIFICATION)
            }
        }
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
            OverlayPermissionHelper.requestOverlayPermission(this)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OverlayPermissionHelper.REQUEST_CODE_OVERLAY) {
            Handler(Looper.getMainLooper()).postDelayed({
                refreshAllPermissionStates()
                if (OverlayPermissionHelper.hasOverlayPermission(this)) {
                    Toast.makeText(this, "✅ Overlay permission granted!", Toast.LENGTH_SHORT).show()
                    binding.switchFloatingBall.isEnabled = true
                } else {
                    showMiuiOverlayGuideDialog()
                }
            }, 500)
        }
    }

    override fun onResume() {
        super.onResume()
        Handler(Looper.getMainLooper()).postDelayed({
            refreshAllPermissionStates()
        }, 300)
    }

    private fun checkOnboarding() {
        val onboardingDone = AppPreferences.getBoolean(this, "onboarding_done", false)
        if (!onboardingDone) {
            showOnboardingDialog()
        }
    }

    private fun showMiuiOverlayGuideDialog() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ One More Step Required")
            .setMessage(
                "Your device (MIUI / HyperOS) needs an extra permission:\n\n" +
                "1. Go to Settings\n" +
                "2. Search 'Display pop-up windows'\n" +
                "   OR go to Apps → CSV Keyboard → Other Permissions\n" +
                "3. Enable 'Display pop-up windows while running in background'\n" +
                "4. Also enable 'Display pop-up window'\n\n" +
                "Then come back and start the Floating Ball."
            )
            .setPositiveButton("Open App Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("I'll do it manually", null)
            .show()
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
            val overlayGranted = OverlayPermissionHelper.hasOverlayPermission(this)
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
                        OverlayPermissionHelper.requestOverlayPermission(this@MainActivity)
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
        if (isDestroyed || isFinishing) return
        val imeEnabled = isImeEnabled()
        val canDrawOverlays = OverlayPermissionHelper.hasOverlayPermission(this)

        binding.btnEnableKeyboard.visibility = if (imeEnabled) View.GONE else View.VISIBLE
        binding.btnSelectKeyboard.visibility = if (imeEnabled) View.VISIBLE else View.GONE

        binding.btnEnableOverlay.visibility = if (canDrawOverlays) View.GONE else View.VISIBLE
        binding.cardFloatingBall.visibility = if (canDrawOverlays) View.VISIBLE else View.GONE

        binding.switchFloatingBall.setOnCheckedChangeListener(null)
        binding.switchFloatingBall.isChecked = FloatingBallManager.isRunning(this)
        binding.tvBallStatus.text = if (binding.switchFloatingBall.isChecked) "Floating ball is active ✓" else "Tap to show info bubble over any app"
        setupListeners()

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
