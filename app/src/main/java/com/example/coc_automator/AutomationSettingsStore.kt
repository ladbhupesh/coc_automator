package com.example.coc_automator

import android.content.Context
import android.content.SharedPreferences

/** Timing and other automation prefs (same file as coordinate JSON for one backup scope). */
object AutomationSettingsStore {

    private const val PREFS_NAME = "attack_coordinates"
    private const val KEY_POST_DEPLOY_WAIT_MS = "post_deploy_wait_ms"
    private const val KEY_INTER_TAP_DELAY_MS = "inter_tap_delay_ms"

    private const val DEFAULT_POST_DEPLOY_MS = 30_000L
    private const val MAX_POST_DEPLOY_MS = 600_000L

    /** Pause after each root tap so the game can register input before the next tap. */
    private const val DEFAULT_INTER_TAP_MS = 180L
    private const val MAX_INTER_TAP_MS = 3_000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun postDeployWaitMs(context: Context): Long {
        val v = prefs(context).getLong(KEY_POST_DEPLOY_WAIT_MS, DEFAULT_POST_DEPLOY_MS)
        return v.coerceIn(0L, MAX_POST_DEPLOY_MS)
    }

    fun setPostDeployWaitMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_POST_DEPLOY_WAIT_MS, ms.coerceIn(0L, MAX_POST_DEPLOY_MS)).apply()
    }

    fun interTapDelayMs(context: Context): Long {
        val v = prefs(context).getLong(KEY_INTER_TAP_DELAY_MS, DEFAULT_INTER_TAP_MS)
        return v.coerceIn(0L, MAX_INTER_TAP_MS)
    }

    fun setInterTapDelayMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_INTER_TAP_DELAY_MS, ms.coerceIn(0L, MAX_INTER_TAP_MS)).apply()
    }
}
