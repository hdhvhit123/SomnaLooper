package com.somnalooper.app.audio

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlin.math.abs

/**
 * "Sleep Motion Auto-Stop": with the phone on the mattress / next to the pillow, the
 * accelerometer picks up tossing and turning. When no movement is seen for [stillMinutes],
 * the user is most likely asleep and [onStill] is invoked once.
 *
 * Sensor events are batched by the sensor hub (maxReportLatency) so the CPU is woken rarely.
 */
class MotionDetector(context: Context, private val onStill: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val handler = Handler(Looper.getMainLooper())

    val available get() = accel != null

    private var running = false
    private var stillMs = 20 * 60_000L
    private var threshold = 0.25f
    private var lastMotion = 0L
    private var fired = false
    private val last = FloatArray(3)
    private var hasLast = false

    /** @param sensitivity 0 (low) .. 2 (high) */
    fun start(stillMinutes: Int, sensitivity: Int) {
        stillMs = stillMinutes * 60_000L
        threshold = when (sensitivity) { 0 -> 0.5f; 2 -> 0.12f; else -> 0.25f }
        lastMotion = SystemClock.elapsedRealtime()
        fired = false
        if (running || accel == null) return
        running = true
        hasLast = false
        sensorManager.registerListener(this, accel, 200_000, 10_000_000, handler)
        handler.postDelayed(check, 30_000)
    }

    fun stop() {
        if (!running) return
        running = false
        sensorManager.unregisterListener(this)
        handler.removeCallbacks(check)
    }

    private val check = object : Runnable {
        override fun run() {
            if (!running) return
            evaluate()
            handler.postDelayed(this, 30_000)
        }
    }

    private fun evaluate() {
        if (!fired && SystemClock.elapsedRealtime() - lastMotion >= stillMs) {
            fired = true
            onStill()
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        if (hasLast) {
            val delta = abs(v[0] - last[0]) + abs(v[1] - last[1]) + abs(v[2] - last[2])
            if (delta > threshold) {
                lastMotion = SystemClock.elapsedRealtime()
                fired = false
            }
        }
        last[0] = v[0]; last[1] = v[1]; last[2] = v[2]
        hasLast = true
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
