package com.example.coc_automator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

/**
 * Pause / resume / restart-current-attack / stop, matching automated_attack.py semantics.
 */
class AutomationController {
    val running = AtomicBoolean(false)
    val paused = AtomicBoolean(false)
    private val restartRequested = AtomicBoolean(false)

    fun requestRestart() {
        restartRequested.set(true)
        paused.set(false)
        DebugLog.d("control: restart requested (attack will restart from top)")
    }

    fun consumeRestart(): Boolean = restartRequested.getAndSet(false)

    suspend fun waitUnpaused() {
        while (coroutineContext.isActive && running.get()) {
            if (consumeRestart()) throw RestartAttackException()
            if (!paused.get()) return
            delay(50)
        }
        if (!running.get()) throw CancellationException()
    }

    suspend fun interruptibleSleep(totalMs: Long) {
        var remaining = totalMs
        while (remaining > 0 && coroutineContext.isActive && running.get()) {
            if (consumeRestart()) throw RestartAttackException()
            waitUnpaused()
            val step = remaining.coerceAtMost(50)
            delay(step)
            remaining -= step
        }
        if (!running.get()) throw CancellationException()
    }

    suspend fun attackTick() {
        if (!coroutineContext.isActive || !running.get()) throw CancellationException()
        if (consumeRestart()) throw RestartAttackException()
        waitUnpaused()
    }
}

class RestartAttackException : Exception()
