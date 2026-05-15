package com.mahavtaar.csvkeyboard.ui.config

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mahavtaar.csvkeyboard.R
import com.mahavtaar.csvkeyboard.data.model.ColumnConfig
import com.mahavtaar.csvkeyboard.data.model.ColumnMode
import com.mahavtaar.csvkeyboard.databinding.ItemColumnConfigBinding
import java.util.Collections

class ColumnConfigAdapter(
    private val configs: MutableList<ColumnConfig>,
    private val onColorPickClick: (Int, ColumnConfig) -> Unit,
    private val onConfigChanged: () -> Unit
) : RecyclerView.Adapter<ColumnConfigAdapter.ConfigViewHolder>() {

    inner class ConfigViewHolder(val binding: ItemColumnConfigBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(config: ColumnConfig, position: Int) {
            binding.tvColumnName.text = config.columnName

            // Temporary remove listener to avoid triggering on bind
            binding.etDisplayLabel.setText(config.displayLabel)

            when (config.mode) {
                ColumnMode.TYPE -> binding.toggleGroupMode.check(R.id.btnType)
                ColumnMode.INFO -> binding.toggleGroupMode.check(R.id.btnInfo)
                ColumnMode.HIDDEN -> binding.toggleGroupMode.check(R.id.btnHidden)
            }

            updateColorVisibility(config.mode)
            try {
                binding.viewColorPreview.setBackgroundColor(Color.parseColor(config.colorHex))
            } catch (e: Exception) {
                binding.viewColorPreview.setBackgroundColor(Color.parseColor("#0F3460"))
            }

            binding.toggleGroupMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
                if (isChecked) {
                    val pos = adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return@addOnButtonCheckedListener

                    val newMode = when (checkedId) {
                        R.id.btnType -> ColumnMode.TYPE
                        R.id.btnInfo -> ColumnMode.INFO
                        else -> ColumnMode.HIDDEN
                    }
                    if (configs[pos].mode != newMode) {
                        configs[pos] = configs[pos].copy(mode = newMode)
                        updateColorVisibility(newMode)
                        onConfigChanged()
                    }
                }
            }

            binding.etDisplayLabel.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val pos = adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return

                    val newLabel = s?.toString() ?: ""
                    if (configs[pos].displayLabel != newLabel) {
                        configs[pos] = configs[pos].copy(displayLabel = newLabel)
                        onConfigChanged()
                    }
                }
            })

            binding.viewColorPreview.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onColorPickClick(pos, configs[pos])
                }
            }
        }

        private fun updateColorVisibility(mode: ColumnMode) {
            binding.llColorPicker.visibility = if (mode == ColumnMode.TYPE) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConfigViewHolder {
        val binding = ItemColumnConfigBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ConfigViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ConfigViewHolder, position: Int) {
        holder.bind(configs[position], position)
    }

    override fun getItemCount() = configs.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        Collections.swap(configs, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)

        // Update order field in configs
        configs.forEachIndexed { index, config ->
            configs[index] = config.copy(order = index)
        }
        onConfigChanged()
    }

    fun updateColor(position: Int, colorHex: String) {
        configs[position] = configs[position].copy(colorHex = colorHex)
        notifyItemChanged(position)
        onConfigChanged()
    }

    fun getConfigs(): List<ColumnConfig> = configs.toList()
}
