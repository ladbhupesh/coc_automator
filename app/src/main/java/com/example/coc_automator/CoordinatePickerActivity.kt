package com.example.coc_automator

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class CoordinatePickerActivity : AppCompatActivity() {

    private lateinit var slot: CoordinateSlot
    private lateinit var imageView: ImageView
    private lateinit var overlay: CoordinateMarkerOverlayView
    private lateinit var summary: TextView

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@registerForActivityResult
        contentResolver.openInputStream(uri)?.use { ins ->
            val bmp = BitmapFactory.decodeStream(ins)
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
                overlay.invalidate()
            } else {
                Toast.makeText(this, R.string.coord_image_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coordinate_picker)

        val name = intent.getStringExtra(EXTRA_SLOT) ?: run {
            finish()
            return
        }
        slot = runCatching { CoordinateSlot.valueOf(name) }.getOrElse {
            finish()
            return
        }

        title = getString(slot.titleRes())

        val hint = findViewById<TextView>(R.id.text_hint)
        hint.text = when {
            slot.showOcrRectangle -> getString(R.string.coord_hint_ocr_rect)
            slot.isYOnly -> getString(R.string.coord_hint_y_only)
            slot.isCardXOnly -> getString(R.string.coord_hint_card_bar)
            slot.count > 1 -> getString(R.string.coord_hint_tap_multi, slot.count)
            else -> getString(R.string.coord_hint_tap_single)
        }

        imageView = findViewById(R.id.image_reference)
        overlay = findViewById(R.id.marker_overlay)
        summary = findViewById(R.id.text_selection_summary)

        overlay.targetImageView = imageView
        overlay.maxCount = slot.count
        overlay.yOnly = slot.isYOnly
        overlay.cardXOnly = slot.isCardXOnly
        overlay.showOcrRectanglePreview = slot.showOcrRectangle

        val cfg = AttackCoordinateStore.load(this)
        overlay.pointsBase.addAll(cfg.slotPoints(slot))
        overlay.onPointsChanged = { refreshSummary() }
        refreshSummary()

        findViewById<Button>(R.id.btn_pick_image).setOnClickListener {
            pickImage.launch("image/*")
        }
        findViewById<Button>(R.id.btn_undo).setOnClickListener { overlay.undoLast() }
        findViewById<Button>(R.id.btn_clear).setOnClickListener { overlay.clearPoints() }
        findViewById<Button>(R.id.btn_reset_defaults).setOnClickListener {
            overlay.clearPoints()
            overlay.pointsBase.addAll(AttackCoordinateConfig.default().slotPoints(slot))
            overlay.invalidate()
            refreshSummary()
        }
        findViewById<Button>(R.id.btn_confirm).setOnClickListener { confirm() }
    }

    private fun refreshSummary() {
        val lines = overlay.pointsBase.mapIndexed { i, (x, y) -> "#${i + 1}: ($x, $y)" }
        val base = if (lines.isEmpty()) {
            getString(R.string.coord_no_points_yet, overlay.maxCount)
        } else {
            lines.joinToString("\n") + "\n— ${overlay.pointsBase.size}/${overlay.maxCount}"
        }
        val extra = if (slot.showOcrRectangle && overlay.pointsBase.size == 2) {
            val r = OcrCropRect.fromDiagonalCorners(overlay.pointsBase[0], overlay.pointsBase[1])
            "\n" + getString(
                R.string.coord_ocr_rect_preview,
                r.left,
                r.top,
                r.right,
                r.bottom,
            )
        } else {
            ""
        }
        summary.text = base + extra
    }

    private fun confirm() {
        if (overlay.pointsBase.size != slot.count) {
            Toast.makeText(
                this,
                getString(R.string.coord_need_n_points, slot.count),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        if (slot.showOcrRectangle) {
            val r = OcrCropRect.fromDiagonalCorners(overlay.pointsBase[0], overlay.pointsBase[1])
            AlertDialog.Builder(this)
                .setTitle(R.string.coord_confirm_ocr_title)
                .setMessage(
                    getString(
                        R.string.coord_confirm_ocr_message,
                        r.left,
                        r.top,
                        r.right,
                        r.bottom,
                    ),
                )
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok) { _, _ -> deliverResultAndFinish() }
                .show()
            return
        }
        deliverResultAndFinish()
    }

    private fun deliverResultAndFinish() {
        val flat = IntArray(slot.count * 2)
        overlay.pointsBase.forEachIndexed { i, p ->
            flat[i * 2] = p.first
            flat[i * 2 + 1] = p.second
        }
        setResult(RESULT_OK, Intent().putExtra(RESULT_FLAT_COORDS, flat))
        finish()
    }

    companion object {
        const val EXTRA_SLOT = "slot"
        const val RESULT_FLAT_COORDS = "flat_coords"

        fun flatToPairs(flat: IntArray): List<Pair<Int, Int>> {
            require(flat.size % 2 == 0)
            return buildList {
                for (i in flat.indices step 2) {
                    add(flat[i] to flat[i + 1])
                }
            }
        }
    }
}
