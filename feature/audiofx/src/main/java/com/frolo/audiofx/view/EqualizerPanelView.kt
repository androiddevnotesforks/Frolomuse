package com.frolo.audiofx.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import com.frolo.audiofx.ui.R
import com.frolo.audiofx2.AudioEffect2
import com.frolo.audiofx2.Equalizer
import com.frolo.audiofx2.EqualizerPreset
import com.frolo.equalizerview.impl.SeekBarEqualizerView
import com.frolo.rx.KeyedDisposableContainer
import com.frolo.ui.Screen
import com.google.android.material.switchmaterial.SwitchMaterial
import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.item_equalizer_preset.view.preset_name
import kotlinx.android.synthetic.main.item_equalizer_preset_drop_down.view.*


class EqualizerPanelView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
): ConstraintLayout(context, attrs, defStyleAttr) {
    private val equalizerView: SeekBarEqualizerView
    private val captionTextView: TextView
    private val enableStatusSwatch: SwitchMaterial
    private val switchListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
        this.equalizer?.isEnabled = isChecked
    }
    private val presetSpinner: Spinner
    private val spinnerListener = object : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long
        ) {
            val adapter = parent?.adapter
            if (adapter is PresetAdapter) {
                val preset = adapter.getItem(position)
                usePresetAsync(preset)
            }
        }
        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    init {
        View.inflate(context, R.layout.merge_equalizer_panel, this)
        equalizerView = findViewById(R.id.equalizer_view)
        captionTextView = findViewById(R.id.caption)
        enableStatusSwatch = findViewById(R.id.enable_status_switch)
        presetSpinner = findViewById(R.id.preset_spinner)
        // Set up listeners
        enableStatusSwatch.setOnCheckedChangeListener(switchListener)
        presetSpinner.onItemSelectedListener = spinnerListener
    }

    private var equalizer: Equalizer? = null
    private val onEnableStatusChangeListener =
        AudioEffect2.OnEnableStatusChangeListener { effect, enabled ->
            equalizerView.isEqualizerUiEnabled = enabled
            setChecked(checked = enabled)
        }

    // Async operations
    private val keyedDisposableContainer = KeyedDisposableContainer<String>()

    fun setup(equalizer: Equalizer?) {
        if (this.equalizer == equalizer) {
            // No changes
            return
        }
        this.equalizer?.apply {
            removeOnEnableStatusChangeListener(onEnableStatusChangeListener)
        }
        keyedDisposableContainer.clear()
        this.equalizer = equalizer
        equalizer?.apply {
            addOnEnableStatusChangeListener(onEnableStatusChangeListener)
        }
        equalizerView.isEqualizerUiEnabled = equalizer?.isEnabled == true
        equalizerView.setup(
            equalizer = equalizer?.let(::AudioFx2EqualizerToEqualizerAdapter),
            animate = isLaidOut
        )
        captionTextView.text = equalizer?.descriptor?.name
        setChecked(checked = equalizer?.isEnabled == true)
        loadPresetsAsync(equalizer)
    }

    private fun loadPresetsAsync(equalizer: Equalizer?) {
        if (equalizer == null) {
            setPresets(emptyList())
            return
        }
        Single.fromCallable { equalizer.getAllPresets() }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(::setPresets)
            .also { disposable ->
                keyedDisposableContainer.add("load_presets_async", disposable)
            }
    }

    private fun setPresets(presets: List<EqualizerPreset>) {
        val listener = presetSpinner.onItemSelectedListener
        presetSpinner.onItemSelectedListener = null
        presetSpinner.adapter = PresetAdapter(
            presets = presets,
            onRemoveItem = ::removePresetAsync
        )
        presetSpinner.onItemSelectedListener = listener
    }

    private fun usePresetAsync(preset: EqualizerPreset) {
        val equalizer = this.equalizer ?: return
        Completable.fromAction { equalizer.usePreset(preset) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
            .also { disposable ->
                keyedDisposableContainer.add("use_preset_async", disposable)
            }
    }

    private fun removePresetAsync(preset: EqualizerPreset) {
        val equalizer = this.equalizer ?: return
        Completable.fromAction { equalizer.deletePreset(preset) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe()
            .also { disposable ->
                keyedDisposableContainer.add("remove_preset_async", disposable)
            }
    }

    private fun setChecked(checked: Boolean) {
        enableStatusSwatch.apply {
            setOnCheckedChangeListener(null)
            isChecked = checked
            setOnCheckedChangeListener(switchListener)
        }
    }
}

private class PresetAdapter constructor(
    private val presets: List<EqualizerPreset>,
    private val onRemoveItem: ((item: EqualizerPreset) -> Unit)? = null
) : BaseAdapter() {

    override fun getCount(): Int = presets.count()

    override fun getItem(position: Int): EqualizerPreset = presets[position]

    override fun getItemId(position: Int): Long = presets[position].name.hashCode().toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView: View = if (convertView == null) {
            val inflater = LayoutInflater.from(parent.context)
            inflater.inflate(R.layout.item_equalizer_preset, parent, false)
        } else {
            convertView
        }
        val preset = getItem(position)
        bindView(itemView, preset, isDropDownItem = false)
        return itemView
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val itemView: View = if (convertView == null) {
            val inflater = LayoutInflater.from(parent.context)
            inflater.inflate(R.layout.item_equalizer_preset_drop_down, parent, false)
        } else {
            convertView
        }
        val preset = getItem(position)
        bindView(itemView, preset, isDropDownItem = true)
        return itemView
    }

    private fun bindView(
        itemView: View,
        preset: EqualizerPreset,
        isDropDownItem: Boolean
    ) = itemView.apply {
        preset_name.text = preset.name
        remove_icon?.apply {
            isVisible = preset.isDeletable
            setOnClickListener { onRemoveItem?.invoke(preset) }
        }
        if (isDropDownItem) {
            val context = itemView.context
            preset_name.updatePadding(
                left = Screen.dp(context, 8f),
                right = if (remove_icon?.isVisible == true) {
                    0
                } else {
                    Screen.dp(context, 16f)
                }
            )
        }
    }
}