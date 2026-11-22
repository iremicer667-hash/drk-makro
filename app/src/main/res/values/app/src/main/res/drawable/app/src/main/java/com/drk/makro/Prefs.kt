package com.drk.makro

import android.content.Context

/** Tıklama aralığını (ms) kalıcı olarak saklar. */
object Prefs {
    const val MIN_INTERVAL_MS = 10L
    const val MAX_INTERVAL_MS = 5000L

    private fun prefs(context: Context) =
        context.getSharedPreferences("drk_makro", Context.MODE_PRIVATE)

    fun intervalMs(context: Context): Long =
        prefs(context).getLong("interval_ms", 100L)

    fun setIntervalMs(context: Context, value: Long) {
        prefs(context).edit()
            .putLong("interval_ms", value.coerceIn(MIN_INTERVAL_MS, MAX_INTERVAL_MS))
            .apply()
    }
}
