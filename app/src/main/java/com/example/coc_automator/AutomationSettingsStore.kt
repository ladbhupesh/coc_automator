package com.example.coc_automator

import android.content.Context
import android.content.SharedPreferences

/** Timing and other automation prefs (same file as coordinate JSON for one backup scope). */
object AutomationSettingsStore {

    private const val PREFS_NAME = "attack_coordinates"
    private const val KEY_POST_DEPLOY_WAIT_MS = "post_deploy_wait_ms"
    private const val KEY_INTER_TAP_DELAY_MS = "inter_tap_delay_ms"
    private const val KEY_PLACEMENT_DEVIATION_MAX = "placement_deviation_max"
    private const val KEY_BAR_SELECT_DEVIATION_MAX = "bar_select_deviation_max"
    private const val KEY_GOBLIN_DEVIATION = "goblin_deviation"
    private const val KEY_UI_WIDE_DEVIATION = "ui_wide_deviation"

    private const val DEFAULT_POST_DEPLOY_MS = 30_000L
    private const val MAX_POST_DEPLOY_MS = 600_000L

    /** Pause after each root tap so the game can register input before the next tap. */
    private const val DEFAULT_INTER_TAP_MS = 180L
    private const val MAX_INTER_TAP_MS = 3_000L

    /** Random tap offset on the board: [CoCAttackAutomation] uses Random.nextInt(2, max+1). */
    private const val DEFAULT_PLACEMENT_DEV_MAX = 3
    private const val MIN_PLACEMENT_DEV_MAX = 2
    private const val MAX_PLACEMENT_DEV_MAX = 25

    /** Horizontal jitter when selecting a card on the bar: Random.nextInt(5, max+1). */
    private const val DEFAULT_BAR_SELECT_DEV_MAX = 6
    private const val MIN_BAR_SELECT_DEV_MAX = 5
    private const val MAX_BAR_SELECT_DEV_MAX = 40

    private const val DEFAULT_GOBLIN_DEVIATION = 2
    private const val MIN_GOBLIN_DEVIATION = 1
    private const val MAX_GOBLIN_DEVIATION = 25

    /** Next / End battle / Confirm — large hit area in base px. */
    private const val DEFAULT_UI_WIDE_DEVIATION = 50
    private const val MIN_UI_WIDE_DEVIATION = 10
    private const val MAX_UI_WIDE_DEVIATION = 300

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

    fun placementDeviationMax(context: Context): Int {
        val v = prefs(context).getInt(KEY_PLACEMENT_DEVIATION_MAX, DEFAULT_PLACEMENT_DEV_MAX)
        return v.coerceIn(MIN_PLACEMENT_DEV_MAX, MAX_PLACEMENT_DEV_MAX)
    }

    fun setPlacementDeviationMax(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_PLACEMENT_DEVIATION_MAX, value.coerceIn(MIN_PLACEMENT_DEV_MAX, MAX_PLACEMENT_DEV_MAX))
            .apply()
    }

    fun barSelectDeviationMax(context: Context): Int {
        val v = prefs(context).getInt(KEY_BAR_SELECT_DEVIATION_MAX, DEFAULT_BAR_SELECT_DEV_MAX)
        return v.coerceIn(MIN_BAR_SELECT_DEV_MAX, MAX_BAR_SELECT_DEV_MAX)
    }

    fun setBarSelectDeviationMax(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_BAR_SELECT_DEVIATION_MAX, value.coerceIn(MIN_BAR_SELECT_DEV_MAX, MAX_BAR_SELECT_DEV_MAX))
            .apply()
    }

    fun goblinDeviation(context: Context): Int {
        val v = prefs(context).getInt(KEY_GOBLIN_DEVIATION, DEFAULT_GOBLIN_DEVIATION)
        return v.coerceIn(MIN_GOBLIN_DEVIATION, MAX_GOBLIN_DEVIATION)
    }

    fun setGoblinDeviation(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_GOBLIN_DEVIATION, value.coerceIn(MIN_GOBLIN_DEVIATION, MAX_GOBLIN_DEVIATION))
            .apply()
    }

    fun uiWideDeviation(context: Context): Int {
        val v = prefs(context).getInt(KEY_UI_WIDE_DEVIATION, DEFAULT_UI_WIDE_DEVIATION)
        return v.coerceIn(MIN_UI_WIDE_DEVIATION, MAX_UI_WIDE_DEVIATION)
    }

    fun setUiWideDeviation(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_UI_WIDE_DEVIATION, value.coerceIn(MIN_UI_WIDE_DEVIATION, MAX_UI_WIDE_DEVIATION))
            .apply()
    }
}
