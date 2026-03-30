package com.example.coc_automator

import android.util.Log

/**
 * Logcat helper: verbose [d] only when this is a **debug** build (`BuildConfig.DEBUG`).
 * [w] / [e] are always emitted so tap/projection failures still show in release.
 *
 * Filter: `adb logcat -s CoCAutomator:D` (debug) or `CoCAutomator:*` for everything from this tag.
 */
object DebugLog {
    const val TAG = "CoCAutomator"

    fun d(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    fun w(msg: String, throwable: Throwable? = null) {
        if (throwable != null) Log.w(TAG, msg, throwable) else Log.w(TAG, msg)
    }

    fun e(msg: String, throwable: Throwable? = null) {
        if (throwable != null) Log.e(TAG, msg, throwable) else Log.e(TAG, msg)
    }
}
