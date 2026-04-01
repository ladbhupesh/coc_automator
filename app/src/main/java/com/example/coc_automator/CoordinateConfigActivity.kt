package com.example.coc_automator

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class CoordinateConfigActivity : AppCompatActivity() {

    private val slots = CoordinateSlot.entries.toList()

    private val openPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val flat = data.getIntArrayExtra(CoordinatePickerActivity.RESULT_FLAT_COORDS) ?: return@registerForActivityResult
        val pending = pendingSlot ?: return@registerForActivityResult
        val pairs = CoordinatePickerActivity.flatToPairs(flat)
        val cfg = AttackCoordinateStore.load(this)
        val updated = cfg.withSlot(pending, pairs)
        AttackCoordinateStore.save(this, updated)
        pendingSlot = null
    }

    private var pendingSlot: CoordinateSlot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coordinate_config)
        title = getString(R.string.coord_config_title)

        findViewById<Button>(R.id.btn_reset_all_defaults).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.coord_reset_all_title)
                .setMessage(R.string.coord_reset_all_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.coord_reset_all_confirm) { _, _ ->
                    AttackCoordinateStore.resetToDefaults(this)
                }
                .show()
        }

        val list = findViewById<ListView>(R.id.list_slots)
        list.adapter = SlotAdapter(this, slots) { slot ->
            pendingSlot = slot
            val i = Intent(this, CoordinatePickerActivity::class.java)
                .putExtra(CoordinatePickerActivity.EXTRA_SLOT, slot.name)
            openPicker.launch(i)
        }
    }

    private class SlotAdapter(
        private val context: Context,
        private val items: List<CoordinateSlot>,
        private val onPick: (CoordinateSlot) -> Unit,
    ) : BaseAdapter() {

        override fun getCount(): Int = items.size
        override fun getItem(position: Int): CoordinateSlot = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.list_item_coordinate_slot, parent, false)
            val slot = items[position]
            v.findViewById<TextView>(R.id.text_title).text = context.getString(slot.titleRes())
            v.findViewById<TextView>(R.id.text_subtitle).text = when {
                slot.isYOnly -> context.getString(R.string.slot_subtitle_y_only)
                slot.isCardXOnly -> context.getString(R.string.slot_subtitle_card_bar, slot.count)
                slot.showOcrRectangle -> context.getString(R.string.slot_subtitle_ocr_rect)
                slot.count > 1 -> context.getString(R.string.slot_subtitle_multi, slot.count)
                else -> context.getString(R.string.slot_subtitle_single)
            }
            v.setOnClickListener { onPick(slot) }
            return v
        }
    }
}
