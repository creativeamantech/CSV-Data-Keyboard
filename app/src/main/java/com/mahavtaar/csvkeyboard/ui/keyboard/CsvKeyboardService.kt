package com.mahavtaar.csvkeyboard.ui.keyboard

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.Insets
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.model.ColumnMode
import com.mahavtaar.csvkeyboard.data.model.CsvRow
import com.mahavtaar.csvkeyboard.data.prefs.ColumnConfigStore
import com.mahavtaar.csvkeyboard.databinding.KeyboardViewBinding
import com.mahavtaar.csvkeyboard.ui.setup.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

class CsvKeyboardService : InputMethodService() {

    private var viewModel: KeyboardViewModel? = null
    private var binding: KeyboardViewBinding? = null
    private var serviceScope: CoroutineScope? = null
    private var inputViewScope: CoroutineScope? = null
    private lateinit var gestureDetector: GestureDetector

    override fun onCreate() {
        super.onCreate()
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        viewModel = KeyboardViewModel(applicationContext, serviceScope!!)

        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            private val SWIPE_THRESHOLD = 100
            private val SWIPE_VELOCITY_THRESHOLD = 100

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Swipe Right -> Previous (RTL safe navigation)
                            viewModel?.goPrevious()
                        } else {
                            // Swipe Left -> Next
                            viewModel?.goNext()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateInputView(): View {
        inputViewScope?.cancel()
        inputViewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        val themedContext = ContextThemeWrapper(this, R.style.Theme_CsvKeyboard)
        val inflater = LayoutInflater.from(themedContext)

        val session = CsvRepository.getSession()
        if (session == null || session.rows.isEmpty()) {
            return inflater.inflate(R.layout.keyboard_empty_state, null)
        }

        binding = KeyboardViewBinding.inflate(inflater)

        setupListeners()
        observeState()

        // Setup Swipe Listener
        binding?.keyboardRoot?.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true // Consume event so swipes register anywhere
        }

        // Slide up animation
        binding?.root?.translationY = 500f
        binding?.root?.animate()?.translationY(0f)?.setDuration(200)?.setInterpolator(DecelerateInterpolator())?.start()

        return binding!!.root
    }

    override fun onStartInputView(info: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        viewModel?.refreshSession()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        inputViewScope?.cancel()
        inputViewScope = null
        binding = null
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        inputViewScope?.cancel()
        inputViewScope = null
        binding = null
    }

    override fun onDestroy() {
        viewModel?.clear()
        serviceScope?.cancel()
        inputViewScope?.cancel()
        super.onDestroy()
    }

    override fun onComputeInsets(outInsets: Insets) {
        super.onComputeInsets(outInsets)
        outInsets.contentTopInsets = outInsets.visibleTopInsets
    }

    private fun safeCommitText(value: String) {
        val ic = currentInputConnection ?: return
        try {
            ic.beginBatchEdit()
            ic.commitText(value, 1)
            ic.endBatchEdit()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupListeners() {
        binding?.btnNext?.setOnClickListener { viewModel?.goNext() }
        binding?.btnPrevious?.setOnClickListener { viewModel?.goPrevious() }
        binding?.btnSettings?.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            requestHideSelf(0)
        }
        binding?.btnReload?.setOnClickListener { viewModel?.refreshSession() }

        binding?.btnSearchToggle?.setOnClickListener {
            val searchBar = binding?.searchBarLayout
            if (searchBar?.visibility == View.VISIBLE) {
                searchBar.visibility = View.GONE
                viewModel?.setSearchQuery("")
                binding?.etSearch?.setText("")
            } else {
                searchBar?.visibility = View.VISIBLE
            }
        }

        binding?.btnCloseSearch?.setOnClickListener {
            binding?.searchBarLayout?.visibility = View.GONE
            viewModel?.setSearchQuery("")
            binding?.etSearch?.setText("")
        }

        binding?.etSearch?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel?.setSearchQuery(s.toString())
            }
        })

        binding?.btnMarkDone?.setOnClickListener {
            viewModel?.markRowDone()
        }
    }

    private fun observeState() {
        inputViewScope?.launch {
            viewModel?.currentRow?.collect { row ->
                if (row != null) {
                    renderRow(row)
                }
            }
        }

        inputViewScope?.launch {
            viewModel?.sessionStats?.collect { stats ->
                val session = CsvRepository.getSession()
                val counterText = if (session == null || session.totalRows == 0 || stats.second == 0)
                    "No data"
                else
                    "Row ${stats.first + 1} / ${stats.second}"
                binding?.tvRowCounter?.text = counterText
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private suspend fun renderRow(row: CsvRow) {
        val configs = ColumnConfigStore.load(this)

        // Cross-fade animation setup
        binding?.infoChipsContainer?.animate()?.alpha(0f)?.setDuration(75)?.withEndAction {
            binding?.infoChipsContainer?.removeAllViews()

            val themedContext = ContextThemeWrapper(this, R.style.Theme_CsvKeyboard)
            val inflater = LayoutInflater.from(themedContext)

            configs.sortedBy { it.order }.forEach { config ->
                val rawValue = row.data[config.columnName] ?: ""
                val value = rawValue.ifBlank { "—" }

                if (config.mode == ColumnMode.INFO) {
                    val chip = inflater.inflate(R.layout.view_info_chip, binding?.infoChipsContainer, false)
                    chip.findViewById<TextView>(R.id.tvLabel).text = config.displayLabel
                    val tvValue = chip.findViewById<TextView>(R.id.tvValue)
                    tvValue.text = value

                    if (row.isDone) {
                        tvValue.setTextColor(Color.parseColor("#888888"))
                        chip.alpha = 0.5f
                    }

                    chip.contentDescription = "${config.displayLabel}: $value, info only"
                    chip.setOnClickListener { copyToClipboard(value, config.displayLabel) }
                    binding?.infoChipsContainer?.addView(chip)
                }
            }
            binding?.infoChipsContainer?.animate()?.alpha(1f)?.setDuration(75)?.start()
        }?.start()

        binding?.typeButtonsContainer?.animate()?.alpha(0f)?.setDuration(75)?.withEndAction {
            binding?.typeButtonsContainer?.removeAllViews()

            val themedContext = ContextThemeWrapper(this, R.style.Theme_CsvKeyboard)
            val inflater = LayoutInflater.from(themedContext)

            configs.sortedBy { it.order }.forEach { config ->
                val rawValue = row.data[config.columnName] ?: ""
                val value = rawValue.ifBlank { "—" }

                if (config.mode == ColumnMode.TYPE) {
                    val btn = inflater.inflate(R.layout.view_type_button, binding?.typeButtonsContainer, false)
                    val tvLabel = btn.findViewById<TextView>(R.id.tvLabel)
                    val tvValue = btn.findViewById<TextView>(R.id.tvValue)
                    tvLabel.text = config.displayLabel
                    tvValue.text = value
                    btn.contentDescription = "Type ${config.displayLabel} value: $value"

                    try {
                        val card = btn as com.google.android.material.card.MaterialCardView
                        card.setCardBackgroundColor(Color.parseColor(config.colorHex))
                        if (row.isDone) {
                            tvLabel.setTextColor(Color.parseColor("#888888"))
                            tvValue.setTextColor(Color.parseColor("#888888"))
                            card.alpha = 0.5f
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    btn.setOnTouchListener { v, event ->
                        when(event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                val scaleDown = ObjectAnimator.ofPropertyValuesHolder(
                                    v,
                                    PropertyValuesHolder.ofFloat("scaleX", 0.95f),
                                    PropertyValuesHolder.ofFloat("scaleY", 0.95f)
                                )
                                scaleDown.duration = 80
                                scaleDown.interpolator = AccelerateDecelerateInterpolator()
                                scaleDown.start()
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                val scaleUp = ObjectAnimator.ofPropertyValuesHolder(
                                    v,
                                    PropertyValuesHolder.ofFloat("scaleX", 1f),
                                    PropertyValuesHolder.ofFloat("scaleY", 1f)
                                )
                                scaleUp.duration = 80
                                scaleUp.interpolator = AccelerateDecelerateInterpolator()
                                scaleUp.start()
                            }
                        }
                        false // Let click listener handle the actual click
                    }

                    btn.setOnClickListener { safeCommitText(value) }

                    btn.setOnLongClickListener {
                        val popup = PopupMenu(this, btn)
                        popup.menu.add(0, 1, 0, getString(R.string.copy))
                        popup.menu.add(0, 2, 1, getString(R.string.type_space))
                        popup.menu.add(0, 3, 2, getString(R.string.type_newline))

                        popup.setOnMenuItemClickListener { item: MenuItem ->
                            when (item.itemId) {
                                1 -> copyToClipboard(value, config.displayLabel)
                                2 -> safeCommitText("$value ")
                                3 -> safeCommitText("$value\n")
                            }
                            true
                        }
                        popup.show()
                        true
                    }

                    binding?.typeButtonsContainer?.addView(btn)
                }
            }
            binding?.typeButtonsContainer?.animate()?.alpha(1f)?.setDuration(75)?.start()
        }?.start()
    }

    private fun copyToClipboard(text: String, label: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, getString(R.string.copied_toast, text), Toast.LENGTH_SHORT).show()
    }
}
