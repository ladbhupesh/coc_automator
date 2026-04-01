package com.example.coc_automator

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

/** Running / stop only; sleeps honour [running] and cancellation. */
class AutomationController {
    val running = AtomicBoolean(false)

    suspend fun waitUnpaused() {
        if (!coroutineContext.isActive) throw CancellationException()
        if (!running.get()) throw CancellationException()
    }

    suspend fun interruptibleSleep(totalMs: Long) {
        var remaining = totalMs
        while (remaining > 0 && coroutineContext.isActive && running.get()) {
            val step = remaining.coerceAtMost(50)
            delay(step)
            remaining -= step
        }
        if (!running.get()) throw CancellationException()
    }

    suspend fun attackTick() {
        if (!coroutineContext.isActive || !running.get()) throw CancellationException()
    }
}
