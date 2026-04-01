package com.example.coc_automator

import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One interactive `su` process for the whole automation run.
 * Taps are sent as shell lines; a daemon thread drains stdout/stderr so pipes never fill
 * (unlike one-shot `su -c` per tap, which is heavier and still needed draining for each process).
 *
 * Note: Android has no `sudo`; Magisk/supersu expose `su` only.
 */
class RootTapSession {

    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var drainThread: Thread? = null
    private val stopped = AtomicBoolean(false)

    fun isAlive(): Boolean = process?.isAlive == true && stdin != null

    /**
     * Spawns `su` (no `-c`) so one root shell stays open. Returns false if the process dies immediately.
     */
    fun open(): Boolean {
        close()
        stopped.set(false)
        return try {
            val p = ProcessBuilder("su")
                .redirectErrorStream(true)
                .start()
            drainThread = Thread(
                {
                    try {
                        val buf = ByteArray(8192)
                        val ins = p.inputStream
                        while (!stopped.get()) {
                            val n = ins.read(buf)
                            if (n < 0) break
                        }
                    } catch (_: Exception) {
                    }
                },
                "CoC-su-drain",
            ).apply {
                isDaemon = true
                start()
            }
            stdin = BufferedWriter(OutputStreamWriter(p.outputStream, StandardCharsets.UTF_8))
            process = p
            Thread.sleep(150)
            if (!p.isAlive) {
                DebugLog.w("RootTapSession: su exited immediately")
                close()
                return false
            }
            DebugLog.d("RootTapSession: interactive su ready")
            true
        } catch (e: Exception) {
            DebugLog.e("RootTapSession.open failed", e)
            close()
            false
        }
    }

    fun tap(x: Int, y: Int) {
        val w = stdin
        val p = process
        if (w == null || p == null || !p.isAlive) {
            DebugLog.w("RootTapSession.tap: no shell screen=($x,$y)")
            return
        }
        try {
            synchronized(this) {
                w.write("/system/bin/input tap $x $y\n")
                w.flush()
            }
            DebugLog.d("root shell tap $x $y")
        } catch (e: Exception) {
            DebugLog.e("root shell tap failed screen=($x,$y)", e)
        }
    }

    fun close() {
        stopped.set(true)
        try {
            stdin?.close()
        } catch (_: Exception) {
        }
        stdin = null
        try {
            process?.destroyForcibly()
        } catch (_: Exception) {
        }
        process = null
        drainThread = null
        DebugLog.d("RootTapSession: closed")
    }
}
