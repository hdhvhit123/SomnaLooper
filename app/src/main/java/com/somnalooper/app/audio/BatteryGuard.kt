package com.somnalooper.app.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat

/**
 * "Battery Saver Safeguard": stops playback as soon as the battery drops to the threshold
 * while not charging, so the phone still has power for the morning alarm.
 */
class BatteryGuard(private val context: Context, private val onLow: (level: Int) -> Unit) {
    private var registered = false
    private var threshold = 15
    private var fired = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) = evaluate(intent)
    }

    fun start(thresholdPercent: Int) {
        threshold = thresholdPercent
        fired = false
        if (!registered) {
            registered = true
            // ACTION_BATTERY_CHANGED is a protected system broadcast; the sticky intent is returned immediately.
            val sticky = ContextCompat.registerReceiver(
                context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            sticky?.let { evaluate(it) }
        }
    }

    fun stop() {
        if (!registered) return
        registered = false
        runCatching { context.unregisterReceiver(receiver) }
    }

    private fun evaluate(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level < 0 || scale <= 0) return
        val pct = level * 100 / scale
        if (!plugged && pct <= threshold && !fired) {
            fired = true
            onLow(pct)
        } else if (plugged || pct > threshold) {
            fired = false
        }
    }
}
