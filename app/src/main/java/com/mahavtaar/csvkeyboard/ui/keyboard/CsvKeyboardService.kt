package com.mahavtaar.csvkeyboard.ui.keyboard

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.model.ColumnMode
import com.mahavtaar.csvkeyboard.data.model.CsvRow
import com.mahavtaar.csvkeyboard.data.prefs.ColumnConfigStore
import com.mahavtaar.csvkeyboard.databinding.KeyboardViewBinding
import com.mahavtaar.csvkeyboard.ui.setup.MainActivity

class CsvKeyboardService : InputMethodService(), LifecycleOwner, ViewModelStoreOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private lateinit var viewModel: KeyboardViewModel
    private lateinit var binding: KeyboardViewBinding

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        viewModel = ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        )[KeyboardViewModel::class.java]
    }

    override fun onCreateInputView(): View {
        binding = KeyboardViewBinding.inflate(layoutInflater)

        setupListeners()
        observeState()

        return binding.root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        viewModel.refreshSession()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }

    private fun setupListeners() {
        binding.btnNext.setOnClickListener { viewModel.goNext() }
        binding.btnPrevious.setOnClickListener { viewModel.goPrevious() }
        binding.btnSettings.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            requestHideSelf(0)
        }
        binding.btnReload.setOnClickListener { viewModel.refreshSession() }
    }

    private fun observeState() {
        viewModel.currentRow.observe(this) { row ->
            if (row != null) renderRow(row)
        }
        viewModel.sessionStats.observe(this) { stats ->
            binding.tvRowCounter.text = getString(R.string.row_counter, stats.first + 1, stats.second)
        }
    }

    private fun renderRow(row: CsvRow) {
        val configs = ColumnConfigStore.load(this) ?: return

        binding.infoChipsContainer.removeAllViews()
        binding.typeButtonsContainer.removeAllViews()

        configs.sortedBy { it.order }.forEach { config ->
            val value = row.data[config.columnName] ?: ""
            when (config.mode) {
                ColumnMode.INFO -> {
                    val chip = LayoutInflater.from(this).inflate(R.layout.view_info_chip, binding.infoChipsContainer, false)
                    chip.findViewById<TextView>(R.id.tvLabel).text = config.displayLabel
                    chip.findViewById<TextView>(R.id.tvValue).text = value
                    chip.setOnClickListener { copyToClipboard(value, config.displayLabel) }
                    binding.infoChipsContainer.addView(chip)
                }
                ColumnMode.TYPE -> {
                    val btn = LayoutInflater.from(this).inflate(R.layout.view_type_button, binding.typeButtonsContainer, false)
                    btn.findViewById<TextView>(R.id.tvLabel).text = config.displayLabel
                    btn.findViewById<TextView>(R.id.tvValue).text = value
                    btn.setOnClickListener { currentInputConnection?.commitText(value, 1) }
                    binding.typeButtonsContainer.addView(btn)
                }
                ColumnMode.HIDDEN -> {}
            }
        }
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_toast, text), Toast.LENGTH_SHORT).show()
    }
}
