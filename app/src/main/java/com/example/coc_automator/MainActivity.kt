package com.example.coc_automator

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var spinnerHeroCount: Spinner
    private lateinit var checkReinforcements: CheckBox
    private lateinit var editPostDeploySeconds: EditText
    private lateinit var editInterTapDelayMs: EditText

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            DebugLog.d("MainActivity: screen capture cancelled or no data")
            Toast.makeText(this, R.string.projection_denied, Toast.LENGTH_LONG).show()
            return@registerForActivityResult
        }
        OverlayService.resultCode = result.resultCode
        OverlayService.mediaProjectionData = result.data

        val heroCount = spinnerHeroCount.selectedItemPosition + 1
        val reinforcements = checkReinforcements.isChecked
        DebugLog.d("MainActivity: starting OverlayService heroes=$heroCount reinforcements=$reinforcements")

        ContextCompat.startForegroundService(
            this,
            Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_HERO_COUNT, heroCount)
                putExtra(OverlayService.EXTRA_REINFORCEMENTS, reinforcements)
            },
        )
        Toast.makeText(this, R.string.overlay_started, Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    private fun persistPostDeployWaitSeconds() {
        val raw = editPostDeploySeconds.text.toString().trim()
        val sec = raw.toLongOrNull()?.coerceIn(0L, 600L) ?: 30L
        AutomationSettingsStore.setPostDeployWaitMs(this, sec * 1000L)
    }

    private fun persistInterTapDelayMs() {
        val raw = editInterTapDelayMs.text.toString().trim()
        val ms = raw.toLongOrNull()?.coerceIn(0L, 3000L) ?: 180L
        AutomationSettingsStore.setInterTapDelayMs(this, ms)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        spinnerHeroCount = findViewById(R.id.spinner_hero_count)
        checkReinforcements = findViewById(R.id.check_reinforcements)
        editPostDeploySeconds = findViewById(R.id.edit_post_deploy_seconds)
        editInterTapDelayMs = findViewById(R.id.edit_inter_tap_delay_ms)
        val savedDeployMs = AutomationSettingsStore.postDeployWaitMs(this)
        editPostDeploySeconds.setText((savedDeployMs / 1000L).toString())
        editInterTapDelayMs.setText(AutomationSettingsStore.interTapDelayMs(this).toString())

        spinnerHeroCount.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            listOf("1 — King", "2 — King + Queen", "3 — + Warden", "4 — All heroes"),
        )
        spinnerHeroCount.setSelection(3)

        findViewById<Button>(R.id.btn_configure_coordinates).setOnClickListener {
            startActivity(Intent(this, CoordinateConfigActivity::class.java))
        }

        findViewById<Button>(R.id.btn_ocr_gallery).setOnClickListener {
            startActivity(Intent(this, OcrCaptureGalleryActivity::class.java))
        }

        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"),
                    ),
                )
            } else {
                Toast.makeText(this, R.string.overlay_already_granted, Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<Button>(R.id.btn_start_automation).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, R.string.need_overlay_first, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            persistPostDeployWaitSeconds()
            persistInterTapDelayMs()
            val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        }
    }
}
