package com.example.coc_automator

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Process
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
import android.widget.TextView
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

    /** Touch-through status line (top center); null when not shown. */
    private var statusBarView: View? = null

    private val controller = AutomationController()
    private val rootTapSession = RootTapSession()
    private var heroCount = 4
    private var addReinforcements = false

    companion object {
        const val CHANNEL_ID = "OverlayServiceChannel"
        const val NOTIFICATION_ID = 1
        const val EXTRA_HERO_COUNT = "hero_count"
        const val EXTRA_REINFORCEMENTS = "add_reinforcements"

        var mediaProjectionData: Intent? = null
        var resultCode: Int = 0

        /**
         * The screen-capture [mediaProjectionData] is single-use: after [MediaProjection.stop]
         * (or system revoke), you must obtain a new consent from [MainActivity].
         */
        fun invalidateCaptureConsent() {
            mediaProjectionData = null
            resultCode = 0
        }
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
     * Sends tap to the long-lived [rootTapSession] (single interactive `su` for the whole run).
     * Uses full path to `input` for Magisk/sh PATH issues.
     */
    private fun tapRawPixels(x: Int, y: Int) {
        rootTapSession.tap(x, y)
    }

    /**
     * One `su -c id` before automation so we fail fast with a clear toast if root is denied.
     * All taps then use one interactive `su` via [rootTapSession] (not `su -c` per tap).
     */
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
        overlayView.findViewById<Button>(R.id.btn_stop).setOnClickListener {
            DebugLog.d("overlay: Stop")
            stopAutomationLoop()
            Toast.makeText(this, R.string.overlay_stopped, Toast.LENGTH_SHORT).show()
        }
        overlayView.findViewById<Button>(R.id.btn_kill_app).setOnClickListener {
            DebugLog.d("overlay: Kill app")
            Toast.makeText(this, R.string.overlay_kill_toast, Toast.LENGTH_SHORT).show()
            killAutomatorApp()
        }

        windowManager.addView(overlayView, layoutParams)
    }

    private fun addStatusBarOverlayIfNeeded() {
        if (statusBarView != null) return
        val v = LayoutInflater.from(this).inflate(R.layout.overlay_status_bar, null)
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (10 * resources.displayMetrics.density).toInt()
        }
        windowManager.addView(v, p)
        statusBarView = v
    }

    private fun removeStatusBarOverlay() {
        statusBarView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        statusBarView = null
    }

    private fun setAutomationStatus(line: String) {
        mainHandler.post {
            addStatusBarOverlayIfNeeded()
            statusBarView?.findViewById<TextView>(R.id.text_automation_status)?.text = line
        }
    }

    private fun killAutomatorApp() {
        stopAutomationLoop()
        removeStatusBarOverlay()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {
            }
        }
        stopForeground(Service.STOP_FOREGROUND_REMOVE)
        stopSelf()
        Process.killProcess(Process.myPid())
    }

    private fun startAutomationLoop() {
        if (automationJob?.isActive == true) {
            DebugLog.d("startAutomationLoop: already running")
            Toast.makeText(this, R.string.already_running, Toast.LENGTH_SHORT).show()
            return
        }
        // After Stop we keep MediaProjection but clear the virtual display; consent Intent may be nulled only after full teardown.
        if (mediaProjection == null && mediaProjectionData == null) {
            DebugLog.w("startAutomationLoop: no MediaProjection and no consent data")
            Toast.makeText(this, R.string.no_projection, Toast.LENGTH_LONG).show()
            return
        }
        if (!hasWorkingRoot()) {
            Toast.makeText(this, R.string.root_failed, Toast.LENGTH_LONG).show()
            DebugLog.e("startAutomationLoop: root check failed — grant su to app")
            return
        }

        controller.running.set(true)
        setAutomationStatus(getString(R.string.status_starting))

        automationJob = serviceScope.launch {
            try {
                setupMediaProjection()
                rootTapSession.close()
                if (!rootTapSession.open()) {
                    mainHandler.post {
                        Toast.makeText(this@OverlayService, R.string.root_shell_failed, Toast.LENGTH_LONG).show()
                    }
                    DebugLog.e("startAutomationLoop: interactive su failed to start")
                    return@launch
                }
                val metrics = resources.displayMetrics
                DebugLog.d("display: ${metrics.widthPixels}x${metrics.heightPixels} densityDpi=${metrics.densityDpi}")
                val geometry = ScreenGeometry(metrics.widthPixels, metrics.heightPixels)
                DebugLog.d("ScreenGeometry base ${ScreenGeometry.BASE_WIDTH}x${ScreenGeometry.BASE_HEIGHT} (ref resolution)")
                setAutomationStatus(getString(R.string.status_capture_ready))

                val ocrSaver = object : OcrCaptureSaver {
                    override suspend fun saveGoldElixirCrops(
                        gold: Bitmap,
                        elixir: Bitmap,
                        goldText: String?,
                        elixirText: String?,
                    ) {
                        OcrCaptureStore.saveCapture(
                            applicationContext,
                            gold,
                            elixir,
                            goldText,
                            elixirText,
                        )
                    }
                }

                while (isActive && controller.running.get()) {
                    val coordConfig = AttackCoordinateStore.load(applicationContext)
                    val postDeployMs = AutomationSettingsStore.postDeployWaitMs(applicationContext)
                    val attack = CoCAttackAutomation(
                        geometry = geometry,
                        tap = { x, y -> tapRawPixels(x, y) },
                        controller = controller,
                        captureFullScreen = { captureScreenshot() },
                        coords = coordConfig,
                        postDeployWaitMs = postDeployMs,
                        onStatus = { setAutomationStatus(it) },
                        ocrCaptureSaver = ocrSaver,
                    )
                    try {
                        attack.executeAttackSequence(heroCount, addReinforcements)
                        DebugLog.d("main loop: sequence finished, sleep 2s before next attack")
                    } catch (_: CancellationException) {
                        DebugLog.d("main loop: cancelled")
                        break
                    }
                    if (!controller.running.get()) break
                    setAutomationStatus(getString(R.string.status_next_attack_soon))
                    try {
                        controller.interruptibleSleep(2000)
                    } catch (_: CancellationException) {
                        break
                    }
                }
            } catch (e: Exception) {
                DebugLog.e("automation coroutine failed", e)
                setAutomationStatus(getString(R.string.status_error, e.message ?: ""))
            } finally {
                DebugLog.d("automation: finally releaseVirtualDisplayOnly + running=false")
                rootTapSession.close()
                releaseVirtualDisplayOnly()
                controller.running.set(false)
                removeStatusBarOverlay()
            }
        }
    }

    private fun stopAutomationLoop() {
        DebugLog.d("stopAutomationLoop")
        controller.running.set(false)
        automationJob?.cancel()
        automationJob = null
        rootTapSession.close()
        releaseVirtualDisplayOnly()
        removeStatusBarOverlay()
    }

    /** Tear down capture surface only so [MediaProjection] can be reused after FAB Stop → Start. */
    private fun releaseVirtualDisplayOnly() {
        DebugLog.d("releaseVirtualDisplayOnly")
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    /** Stop projection and clear consent — service destroy, system revoke, or fatal setup error. */
    private fun releaseCaptureFully() {
        DebugLog.d("releaseCaptureFully")
        releaseVirtualDisplayOnly()
        mediaProjection?.let { mp ->
            try {
                projectionCallback?.let { mp.unregisterCallback(it) }
            } catch (e: Exception) {
                DebugLog.w("unregister MediaProjection callback", e)
            }
            projectionCallback = null
            try {
                mp.stop()
            } catch (e: Exception) {
                DebugLog.w("MediaProjection.stop", e)
            }
        }
        mediaProjection = null
        invalidateCaptureConsent()
    }

    /** System invoked [MediaProjection.Callback.onStop]; projection is already ending — do not call [MediaProjection.stop]. */
    private fun handleProjectionStoppedByCallback() {
        DebugLog.d("handleProjectionStoppedByCallback")
        releaseVirtualDisplayOnly()
        mediaProjection?.let { mp ->
            try {
                projectionCallback?.let { mp.unregisterCallback(it) }
            } catch (e: Exception) {
                DebugLog.w("unregister after projection stop", e)
            }
        }
        projectionCallback = null
        mediaProjection = null
        invalidateCaptureConsent()
    }

    private fun setupMediaProjection() {
        if (virtualDisplay != null) {
            DebugLog.d("setupMediaProjection: already active, skip")
            return
        }
        val metrics = resources.displayMetrics

        val mp = mediaProjection ?: run {
            val data = mediaProjectionData
            if (data == null) {
                DebugLog.e("setupMediaProjection: no mediaProjectionData for new projection")
                return
            }
            val mpManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val newMp = mpManager.getMediaProjection(resultCode, data)
            if (newMp == null) {
                DebugLog.e("setupMediaProjection: getMediaProjection returned null")
                return
            }
            mediaProjection = newMp
            DebugLog.d("setupMediaProjection: new MediaProjection from consent")

            val cb = object : MediaProjection.Callback() {
                override fun onStop() {
                    DebugLog.w("MediaProjection.Callback.onStop — revoked or stopped")
                    mainHandler.post { handleProjectionStoppedByCallback() }
                }
            }
            projectionCallback = cb
            newMp.registerCallback(cb, mainHandler)
            newMp
        }

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
            releaseCaptureFully()
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
        removeStatusBarOverlay()
        stopAutomationLoop()
        releaseCaptureFully()
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
