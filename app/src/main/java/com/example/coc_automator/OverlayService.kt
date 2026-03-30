package com.example.coc_automator

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private lateinit var fabCircle: ImageView
    private lateinit var menuLayout: LinearLayout
    private lateinit var layoutParams: WindowManager.LayoutParams

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var projectionCallback: MediaProjection.Callback? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.Default)
    private var automationJob: Job? = null

    private val controller = AutomationController()
    private var heroCount = 4
    private var addReinforcements = false

    companion object {
        const val CHANNEL_ID = "OverlayServiceChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_HERO_COUNT = "hero_count"
        const val EXTRA_REINFORCEMENTS = "add_reinforcements"

        var mediaProjectionData: Intent? = null
        var resultCode: Int = 0
    }

    override fun onCreate() {
        super.onCreate()
        DebugLog.d("OverlayService.onCreate")
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_running))
                .setSmallIcon(android.R.drawable.ic_media_play)
                .build(),
        )

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            heroCount = it.getIntExtra(EXTRA_HERO_COUNT, 4).coerceIn(1, 4)
            addReinforcements = it.getBooleanExtra(EXTRA_REINFORCEMENTS, false)
            DebugLog.d("onStartCommand heroes=$heroCount addReinforcements=$addReinforcements")
        }
        return START_STICKY
    }

    /**
     * One-shot root tap. Avoids a long-lived `su` shell: without draining stdout/stderr,
     * the pipe fills and `input` blocks — a common reason no taps reach the game.
     * Uses full path to `input` for Magisk/sh PATH issues.
     */
    private fun tapRawPixels(x: Int, y: Int) {
        try {
            val cmd = "/system/bin/input tap $x $y"
            DebugLog.d("root: exec su -c \"$cmd\"")
            val p = ProcessBuilder("su", "-c", cmd)
                .redirectErrorStream(true)
                .start()
            p.inputStream.use { ins ->
                val buf = ByteArray(512)
                while (ins.read(buf) != -1) {
                    // discard su/input chatter
                }
            }
            if (!p.waitFor(2, TimeUnit.SECONDS)) {
                p.destroyForcibly()
                DebugLog.w("root: tap TIMEOUT screen=($x,$y)")
                return
            }
            val code = p.exitValue()
            if (code != 0) {
                DebugLog.w("root: tap exitCode=$code screen=($x,$y)")
            } else {
                DebugLog.d("root: tap OK screen=($x,$y)")
            }
        } catch (e: Exception) {
            DebugLog.e("root: tap exception screen=($x,$y)", e)
        }
    }

    /** Quick root check before automation (Magisk prompt may appear here). */
    private fun hasWorkingRoot(): Boolean {
        return try {
            val p = ProcessBuilder("su", "-c", "id")
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().use { it.readText() }
            val ok = p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0
            val root = ok && out.contains("uid=0")
            DebugLog.d("root: check ok=$ok uid0=$root output=${out.trim()}")
            root
        } catch (e: Exception) {
            DebugLog.e("root: check exception", e)
            false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 120
        }

        fabCircle = overlayView.findViewById(R.id.fab_circle)
        menuLayout = overlayView.findViewById(R.id.menu_layout)

        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downRawX = 0f
        var downRawY = 0f
        var initialX = 0
        var initialY = 0
        var dragging = false

        fabCircle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!dragging) {
                        if (abs(event.rawX - downRawX) > touchSlop || abs(event.rawY - downRawY) > touchSlop) {
                            dragging = true
                        }
                    }
                    if (dragging) {
                        layoutParams.x = initialX - (event.rawX - downRawX).toInt()
                        layoutParams.y = initialY + (event.rawY - downRawY).toInt()
                        windowManager.updateViewLayout(overlayView, layoutParams)
                        true
                    } else false
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        fabCircle.performClick()
                    }
                    false
                }
                else -> false
            }
        }

        fabCircle.setOnClickListener {
            val open = menuLayout.visibility == View.GONE
            menuLayout.visibility = if (open) View.VISIBLE else View.GONE
            DebugLog.d("overlay: FAB ${if (open) "open menu" else "close menu"}")
        }

        overlayView.findViewById<Button>(R.id.btn_start).setOnClickListener {
            DebugLog.d("overlay: Start pressed")
            menuLayout.visibility = View.GONE
            startAutomationLoop()
        }
        overlayView.findViewById<Button>(R.id.btn_pause).setOnClickListener {
            DebugLog.d("overlay: Pause")
            controller.paused.set(true)
            Toast.makeText(this, R.string.overlay_paused, Toast.LENGTH_SHORT).show()
        }
        overlayView.findViewById<Button>(R.id.btn_resume).setOnClickListener {
            DebugLog.d("overlay: Resume")
            controller.paused.set(false)
            Toast.makeText(this, R.string.overlay_resumed, Toast.LENGTH_SHORT).show()
        }
        overlayView.findViewById<Button>(R.id.btn_restart).setOnClickListener {
            DebugLog.d("overlay: Restart attack")
            controller.requestRestart()
            Toast.makeText(this, R.string.overlay_restart_toast, Toast.LENGTH_SHORT).show()
        }
        overlayView.findViewById<Button>(R.id.btn_stop).setOnClickListener {
            DebugLog.d("overlay: Stop")
            stopAutomationLoop()
            Toast.makeText(this, R.string.overlay_stopped, Toast.LENGTH_SHORT).show()
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun startAutomationLoop() {
        if (automationJob?.isActive == true) {
            DebugLog.d("startAutomationLoop: already running")
            Toast.makeText(this, R.string.already_running, Toast.LENGTH_SHORT).show()
            return
        }
        if (mediaProjectionData == null) {
            DebugLog.w("startAutomationLoop: no mediaProjectionData")
            Toast.makeText(this, R.string.no_projection, Toast.LENGTH_LONG).show()
            return
        }
        if (!hasWorkingRoot()) {
            Toast.makeText(this, R.string.root_failed, Toast.LENGTH_LONG).show()
            DebugLog.e("startAutomationLoop: root check failed — grant su to app")
            return
        }

        controller.running.set(true)
        controller.paused.set(false)

        automationJob = serviceScope.launch {
            try {
                setupMediaProjection()
                val metrics = resources.displayMetrics
                DebugLog.d("display: ${metrics.widthPixels}x${metrics.heightPixels} densityDpi=${metrics.densityDpi}")
                val geometry = ScreenGeometry(metrics.widthPixels, metrics.heightPixels)
                DebugLog.d("ScreenGeometry base ${ScreenGeometry.BASE_WIDTH}x${ScreenGeometry.BASE_HEIGHT} (ref resolution)")
                val attack = CoCAttackAutomation(
                    geometry = geometry,
                    tap = { x, y -> tapRawPixels(x, y) },
                    controller = controller,
                    captureFullScreen = { captureScreenshot() },
                )

                while (isActive && controller.running.get()) {
                    try {
                        attack.executeAttackSequence(heroCount, addReinforcements)
                        DebugLog.d("main loop: sequence finished, sleep 2s before next attack")
                    } catch (_: RestartAttackException) {
                        DebugLog.d("main loop: RestartAttackException -> re-run sequence")
                        continue
                    } catch (_: CancellationException) {
                        DebugLog.d("main loop: cancelled")
                        break
                    }
                    if (!controller.running.get()) break
                    try {
                        controller.interruptibleSleep(2000)
                    } catch (_: RestartAttackException) {
                        DebugLog.d("main loop: restart during inter-attack sleep")
                        continue
                    } catch (_: CancellationException) {
                        break
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("automation coroutine failed", e)
            } finally {
                DebugLog.d("automation: finally releaseCapture + running=false")
                releaseCapture()
                controller.running.set(false)
            }
        }
    }

    private fun stopAutomationLoop() {
        DebugLog.d("stopAutomationLoop")
        controller.running.set(false)
        controller.paused.set(false)
        automationJob?.cancel()
        automationJob = null
        releaseCapture()
    }

    private fun setupMediaProjection() {
        if (virtualDisplay != null) {
            DebugLog.d("setupMediaProjection: already active, skip")
            return
        }
        val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = mpManager.getMediaProjection(resultCode, mediaProjectionData!!)
        if (mp == null) {
            DebugLog.e("setupMediaProjection: getMediaProjection returned null")
            return
        }
        mediaProjection = mp
        DebugLog.d("setupMediaProjection: got MediaProjection")

        // API 34+: must register a callback before createVirtualDisplay.
        val cb = object : MediaProjection.Callback() {
            override fun onStop() {
                DebugLog.w("MediaProjection.Callback.onStop — capture ended (revoked or stopped)")
                virtualDisplay?.release()
                virtualDisplay = null
                imageReader?.close()
                imageReader = null
            }
        }
        projectionCallback = cb
        mp.registerCallback(cb, mainHandler)

        val metrics = resources.displayMetrics
        try {
            imageReader = ImageReader.newInstance(
                metrics.widthPixels,
                metrics.heightPixels,
                PixelFormat.RGBA_8888,
                2,
            )
            virtualDisplay = mp.createVirtualDisplay(
                "CoCAutomator",
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null,
            )
            DebugLog.d("VirtualDisplay created ${metrics.widthPixels}x${metrics.heightPixels}")
        } catch (e: Exception) {
            DebugLog.e("createVirtualDisplay failed", e)
            releaseCapture()
            throw e
        }
    }

    private suspend fun captureScreenshot(): Bitmap? =
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val image = imageReader?.acquireLatestImage()
            if (image == null) {
                DebugLog.d("captureScreenshot: no Image (reader null or no frame yet)")
                return@withContext null
            }
            try {
                val planes = image.planes
                val buffer = planes[0].buffer
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val bitmap = Bitmap.createBitmap(
                    image.width + rowPadding / pixelStride,
                    image.height,
                    Bitmap.Config.ARGB_8888,
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val out = Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                DebugLog.d("captureScreenshot: ${out.width}x${out.height}")
                out
            } finally {
                image.close()
            }
        }

    private fun releaseCapture() {
        DebugLog.d("releaseCapture")
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.let { mp ->
            projectionCallback?.let { mp.unregisterCallback(it) }
            projectionCallback = null
            mp.stop()
        }
        mediaProjection = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        DebugLog.d("OverlayService.onDestroy")
        stopAutomationLoop()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {
            }
        }
        serviceJob.cancel()
        super.onDestroy()
    }
}
