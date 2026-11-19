package com.somnalooper.app.audio.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/** Fast xorshift random generator. Avoids allocation and locking of java.util.Random on the audio thread. */
class Rng(seed: Int = (System.nanoTime() xor 0x5DEECE66DL).toInt()) {
    private var s: Int = if (seed == 0) 0x1234567 else seed

    fun nextInt(): Int {
        var x = s
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        s = x
        return x
    }

    /** Uniform in [-1, 1). */
    fun bipolar(): Float = nextInt() * 4.656613e-10f

    /** Uniform in [0, 1). */
    fun unit(): Float = (nextInt() ushr 8) * 5.9604645e-8f

    fun range(lo: Float, hi: Float): Float = lo + (hi - lo) * unit()

    fun chance(p: Float): Boolean = unit() < p
}

/** Paul Kellet's refined pink noise filter. */
class PinkNoise(private val rng: Rng) {
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var b3 = 0f; private var b4 = 0f; private var b5 = 0f; private var b6 = 0f

    fun next(): Float {
        val w = rng.bipolar()
        b0 = 0.99886f * b0 + w * 0.0555179f
        b1 = 0.99332f * b1 + w * 0.0750759f
        b2 = 0.96900f * b2 + w * 0.1538520f
        b3 = 0.86650f * b3 + w * 0.3104856f
        b4 = 0.55000f * b4 + w * 0.5329522f
        b5 = -0.7616f * b5 - w * 0.0168980f
        val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + w * 0.5362f
        b6 = w * 0.115926f
        return out * 0.11f
    }
}

/** Brownian (red) noise: leaky integrated white noise. */
class BrownNoise(private val rng: Rng) {
    private var last = 0f
    fun next(): Float {
        last = (last + 0.02f * rng.bipolar()) / 1.02f
        return last * 3.5f
    }
}

/** One pole low pass filter. */
class OnePole(sampleRate: Int, cutoff: Float) {
    private val sr = sampleRate.toFloat()
    private var a = 0f
    private var y = 0f

    init { setCutoff(cutoff) }

    fun setCutoff(fc: Float) {
        a = 1f - exp(-2.0 * PI * fc / sr).toFloat()
    }

    fun process(x: Float): Float {
        y += a * (x - y)
        return y
    }
}

/** RBJ biquad filter (transposed direct form II). */
class Biquad(private val sampleRate: Int) {
    enum class Type { LOW_PASS, HIGH_PASS, BAND_PASS }

    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f
    private var z1 = 0f; private var z2 = 0f

    fun set(type: Type, freq: Float, q: Float): Biquad {
        val f = freq.coerceIn(10f, sampleRate * 0.45f)
        val w0 = 2.0 * PI * f / sampleRate
        val cw = cos(w0)
        val alpha = sin(w0) / (2.0 * q)
        val a0: Double
        when (type) {
            Type.LOW_PASS -> {
                b0 = ((1 - cw) / 2).toFloat(); b1 = (1 - cw).toFloat(); b2 = b0
            }
            Type.HIGH_PASS -> {
                b0 = ((1 + cw) / 2).toFloat(); b1 = (-(1 + cw)).toFloat(); b2 = b0
            }
            Type.BAND_PASS -> {
                // constant 0 dB peak gain
                b0 = alpha.toFloat(); b1 = 0f; b2 = (-alpha).toFloat()
            }
        }
        a0 = 1 + alpha
        b0 = (b0 / a0).toFloat(); b1 = (b1 / a0).toFloat(); b2 = (b2 / a0).toFloat()
        a1 = (-2 * cw / a0).toFloat(); a2 = ((1 - alpha) / a0).toFloat()
        return this
    }

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }
}

/** Shared sine lookup table. */
object SineTable {
    private const val SIZE = 4096
    private val table = FloatArray(SIZE + 1) { sin(2.0 * PI * it / SIZE).toFloat() }

    /** phase in cycles [0,1). */
    fun sin(phase: Float): Float {
        val p = (phase - phase.toInt()) * SIZE
        val i = p.toInt()
        val frac = p - i
        return table[i] + (table[i + 1] - table[i]) * frac
    }
}

/** Exponentially decaying sine, used for drops, clinks, bubbles and pings. */
class Ping {
    var active = false
    private var phase = 0f
    private var inc = 0f
    private var incMul = 1f
    private var amp = 0f
    private var decay = 0f
    var panL = 1f; private set
    var panR = 1f; private set

    fun trigger(sampleRate: Int, freq: Float, amplitude: Float, decaySeconds: Float, pan: Float, glide: Float = 1f) {
        active = true
        phase = 0f
        inc = freq / sampleRate
        // glide is the frequency multiplier reached after decaySeconds
        incMul = if (glide == 1f) 1f else exp(kotlin.math.ln(glide) / (decaySeconds * sampleRate))
        amp = amplitude
        decay = exp(-6.9f / (decaySeconds * sampleRate)) // -60 dB after decaySeconds
        val p = pan.coerceIn(-1f, 1f)
        panL = sqrt((1f - p) / 2f)
        panR = sqrt((1f + p) / 2f)
    }

    fun next(): Float {
        if (!active) return 0f
        val v = SineTable.sin(phase) * amp
        phase += inc
        if (phase >= 1f) phase -= 1f
        inc *= incMul
        amp *= decay
        if (amp < 1e-4f) active = false
        return v
    }
}

/** Pool of pings so generators can fire many overlapping events without allocation. */
class PingPool(size: Int) {
    val pings = Array(size) { Ping() }
    private var cursor = 0

    fun obtain(): Ping {
        for (i in pings.indices) {
            val p = pings[(cursor + i) % pings.size]
            if (!p.active) {
                cursor = (cursor + i + 1) % pings.size
                return p
            }
        }
        cursor = (cursor + 1) % pings.size
        return pings[cursor]
    }

    /** Adds all active pings into l/r. */
    fun mixInto(l: FloatArray, r: FloatArray, i: Int, gain: Float = 1f) {
        var sl = 0f
        var sr = 0f
        for (p in pings) {
            if (p.active) {
                val v = p.next()
                sl += v * p.panL
                sr += v * p.panR
            }
        }
        l[i] += sl * gain
        r[i] += sr * gain
    }
}

/** Smoothly wandering random value (slow LFO with random targets). */
class Drift(private val rng: Rng, private val sampleRate: Int, private val minPeriod: Float, private val maxPeriod: Float, private val lo: Float, private val hi: Float) {
    var value = rng.range(lo, hi); private set
    private var from = value
    private var to = rng.range(lo, hi)
    private var pos = 0
    private var len = newLen()

    private fun newLen() = (rng.range(minPeriod, maxPeriod) * sampleRate).toInt().coerceAtLeast(1)

    /** Advances by n samples (call once per block). */
    fun advance(n: Int): Float {
        pos += n
        if (pos >= len) {
            pos = 0; len = newLen(); from = to; to = rng.range(lo, hi)
        }
        val t = pos.toFloat() / len
        val s = t * t * (3 - 2 * t)
        value = from + (to - from) * s
        return value
    }
}

/** Simple feedback comb used as a cheap room/reverb smear. */
class Comb(size: Int, private val feedback: Float) {
    private val buf = FloatArray(size)
    private var idx = 0
    fun process(x: Float): Float {
        val y = buf[idx]
        buf[idx] = x + y * feedback
        idx = (idx + 1) % buf.size
        return y
    }
}
