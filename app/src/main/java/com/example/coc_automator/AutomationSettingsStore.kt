package com.example.coc_automator

import android.content.Context
import android.content.SharedPreferences

/** Timing and other automation prefs (same file as coordinate JSON for one backup scope). */
object AutomationSettingsStore {

    private const val PREFS_NAME = "attack_coordinates"
    private const val KEY_POST_DEPLOY_WAIT_MS = "post_deploy_wait_ms"

    private const val DEFAULT_POST_DEPLOY_MS = 30_000L
    private const val MAX_POST_DEPLOY_MS = 600_000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun postDeployWaitMs(context: Context): Long {
        val v = prefs(context).getLong(KEY_POST_DEPLOY_WAIT_MS, DEFAULT_POST_DEPLOY_MS)
        return v.coerceIn(0L, MAX_POST_DEPLOY_MS)
    }

    fun setPostDeployWaitMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_POST_DEPLOY_WAIT_MS, ms.coerceIn(0L, MAX_POST_DEPLOY_MS)).apply()
    }
}
