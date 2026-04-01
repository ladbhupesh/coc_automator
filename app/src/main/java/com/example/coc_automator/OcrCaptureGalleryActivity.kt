package com.example.coc_automator

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OcrCaptureGalleryActivity : AppCompatActivity() {

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: CapturesAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ocr_gallery)
        title = getString(R.string.ocr_gallery_title)

        recycler = findViewById(R.id.recycler_captures)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = CapturesAdapter(timeFmt)
        recycler.adapter = adapter

        findViewById<Button>(R.id.btn_clear_captures).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.ocr_gallery_clear_title)
                .setMessage(R.string.ocr_gallery_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ocr_gallery_clear) { _, _ ->
                    OcrCaptureStore.clearAll(this)
                    refreshList()
                }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        adapter.submit(OcrCaptureStore.listCaptures(this))
    }

    private class CapturesAdapter(
        private val timeFmt: SimpleDateFormat,
    ) : RecyclerView.Adapter<CapturesAdapter.VH>() {

        private var items: List<OcrCaptureEntry> = emptyList()

        fun submit(list: List<OcrCaptureEntry>) {
            items = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ocr_capture, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(items[position], timeFmt)
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val time: TextView = itemView.findViewById(R.id.text_capture_time)
            private val goldIv: ImageView = itemView.findViewById(R.id.image_gold)
            private val elixirIv: ImageView = itemView.findViewById(R.id.image_elixir)
            private val numbers: TextView = itemView.findViewById(R.id.text_ocr_numbers)

            fun bind(entry: OcrCaptureEntry, fmt: SimpleDateFormat) {
                time.text = fmt.format(Date(entry.timeMs))
                val gBmp = OcrCaptureStore.decodeBitmap(entry.goldFile)
                val eBmp = OcrCaptureStore.decodeBitmap(entry.elixirFile)
                goldIv.setImageBitmap(gBmp)
                elixirIv.setImageBitmap(eBmp)
                val gt = entry.goldText ?: "—"
                val et = entry.elixirText ?: "—"
                numbers.text = itemView.context.getString(R.string.ocr_gallery_numbers_line, gt, et)
            }
        }
    }
}
