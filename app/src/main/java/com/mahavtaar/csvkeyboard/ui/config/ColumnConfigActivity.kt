package com.mahavtaar.csvkeyboard.ui.config

import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mahavtaar.csvkeyboard.data.csv.CsvRepository
import com.mahavtaar.csvkeyboard.data.model.ColumnConfig
import com.mahavtaar.csvkeyboard.data.prefs.ColumnConfigStore
import com.mahavtaar.csvkeyboard.databinding.ActivityColumnConfigBinding
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ColumnConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityColumnConfigBinding
    private lateinit var adapter: ColumnConfigAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityColumnConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        loadConfigs()

        binding.btnReset.setOnClickListener {
            resetToDefaults()
        }
    }

    private fun loadConfigs() {
        lifecycleScope.launch(Dispatchers.IO) {
            val configs = ColumnConfigStore.load(this@ColumnConfigActivity).sortedBy { it.order }.toMutableList()

            withContext(Dispatchers.Main) {
                setupRecyclerView(configs)
            }
        }
    }

    private fun setupRecyclerView(configs: MutableList<ColumnConfig>) {
        adapter = ColumnConfigAdapter(
            configs,
            onColorPickClick = { position, config -> showColorPicker(position, config) },
            onConfigChanged = { saveConfigs() }
        )
        binding.recyclerViewConfigs.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewConfigs.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = true
        })
        touchHelper.attachToRecyclerView(binding.recyclerViewConfigs)
    }

    private fun showColorPicker(position: Int, config: ColumnConfig) {
        ColorPickerDialog.Builder(this)
            .setTitle("Select Button Color")
            .setPreferenceName("ColorPickerDialog")
            .setPositiveButton("Confirm",
                ColorEnvelopeListener { envelope: ColorEnvelope, _ ->
                    val colorHex = "#" + envelope.hexCode
                    adapter.updateColor(position, colorHex)
                })
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .attachAlphaSlideBar(false)
            .attachBrightnessSlideBar(true)
            .setBottomSpace(12)
            .show()
    }

    private fun saveConfigs() {
        lifecycleScope.launch(Dispatchers.IO) {
            ColumnConfigStore.save(this@ColumnConfigActivity, adapter.getConfigs())
        }
    }

    private fun resetToDefaults() {
        lifecycleScope.launch(Dispatchers.IO) {
            val session = CsvRepository.loadSession(this@ColumnConfigActivity)
            if (session != null) {
                val defaults = ColumnConfigStore.generateDefaults(session.headers)
                ColumnConfigStore.save(this@ColumnConfigActivity, defaults)
                withContext(Dispatchers.Main) {
                    loadConfigs()
                }
            }
        }
    }
}
