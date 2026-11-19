package com.somnalooper.app.audio.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/**
 * A procedural, endless sound source. Implementations ADD their output into [l] and [r]
 * for [n] frames. Because sounds are synthesized in real time they never repeat and have no
 * loop seam at all, which is the ideal "seamless loop".
 *
 * Pure Kotlin (no Android imports) so it can be unit tested on the JVM.
 */
abstract class Generator(val sr: Int) {
    protected val rng = Rng()
    abstract fun render(l: FloatArray, r: FloatArray, n: Int)

    protected fun seconds(s: Float): Int = (s * sr).toInt().coerceAtLeast(1)
}

// ---------------------------------------------------------------- Colored noise

class WhiteNoiseGen(sr: Int) : Generator(sr) {
    private val rngR = Rng(rng.nextInt())
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            l[i] += rng.bipolar() * 0.3f
            r[i] += rngR.bipolar() * 0.3f
        }
    }
}

class PinkNoiseGen(sr: Int) : Generator(sr) {
    private val pl = PinkNoise(rng)
    private val pr = PinkNoise(Rng(rng.nextInt()))
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            l[i] += pl.next() * 1.6f
            r[i] += pr.next() * 1.6f
        }
    }
}

class BrownNoiseGen(sr: Int) : Generator(sr) {
    private val bl = BrownNoise(rng)
    private val br = BrownNoise(Rng(rng.nextInt()))
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            l[i] += bl.next() * 0.9f
            r[i] += br.next() * 0.9f
        }
    }
}

/** "Green noise": pink noise focused around 500 Hz, popular for sleep. */
class GreenNoiseGen(sr: Int) : Generator(sr) {
    private val pl = PinkNoise(rng)
    private val pr = PinkNoise(Rng(rng.nextInt()))
    private val fl = Biquad(sr).set(Biquad.Type.BAND_PASS, 500f, 0.6f)
    private val fr = Biquad(sr).set(Biquad.Type.BAND_PASS, 500f, 0.6f)
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            l[i] += fl.process(pl.next()) * 3.2f
            r[i] += fr.process(pr.next()) * 3.2f
        }
    }
}

// ---------------------------------------------------------------- Binaural beats

/**
 * Binaural beat: slightly different pure tones in each ear. The brain perceives the difference
 * ([beatHz]) as a pulsation. Headphones are required for the effect.
 */
class BinauralGen(sr: Int, private val carrierHz: Float, private val beatHz: Float) : Generator(sr) {
    private var pl = 0f
    private var pr = 0f
    private val il = (carrierHz - beatHz / 2f) / sr
    private val ir = (carrierHz + beatHz / 2f) / sr
    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            l[i] += SineTable.sin(pl) * 0.22f
            r[i] += SineTable.sin(pr) * 0.22f
            pl += il; if (pl >= 1f) pl -= 1f
            pr += ir; if (pr >= 1f) pr -= 1f
        }
    }
}

// ---------------------------------------------------------------- Nature

/** Rain on a tin roof: soft hiss plus bright metallic drop impacts. */
class RainRoofGen(sr: Int) : Generator(sr) {
    private val pinkL = PinkNoise(rng)
    private val pinkR = PinkNoise(Rng(rng.nextInt()))
    private val hpL = Biquad(sr).set(Biquad.Type.HIGH_PASS, 500f, 0.7f)
    private val hpR = Biquad(sr).set(Biquad.Type.HIGH_PASS, 500f, 0.7f)
    private val lpL = Biquad(sr).set(Biquad.Type.LOW_PASS, 7000f, 0.7f)
    private val lpR = Biquad(sr).set(Biquad.Type.LOW_PASS, 7000f, 0.7f)
    private val drops = PingPool(48)
    private val intensity = Drift(rng, sr, 6f, 20f, 0.6f, 1f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val inten = intensity.advance(n)
        val dropProb = 90f * inten / sr
        for (i in 0 until n) {
            if (rng.chance(dropProb)) {
                val heavy = rng.chance(0.15f)
                drops.obtain().trigger(
                    sr,
                    freq = if (heavy) rng.range(900f, 1800f) else rng.range(2200f, 5200f),
                    amplitude = if (heavy) rng.range(0.08f, 0.18f) else rng.range(0.02f, 0.08f),
                    decaySeconds = if (heavy) rng.range(0.05f, 0.12f) else rng.range(0.012f, 0.04f),
                    pan = rng.bipolar(),
                    glide = 0.92f,
                )
            }
            l[i] += lpL.process(hpL.process(pinkL.next())) * 0.9f * inten
            r[i] += lpR.process(hpR.process(pinkR.next())) * 0.9f * inten
            drops.mixInto(l, r, i)
        }
    }
}

/** Dense tropical rain with occasional distant birds. */
class RainForestGen(sr: Int) : Generator(sr) {
    private val pinkL = PinkNoise(rng)
    private val pinkR = PinkNoise(Rng(rng.nextInt()))
    private val lpL = Biquad(sr).set(Biquad.Type.LOW_PASS, 4500f, 0.7f)
    private val lpR = Biquad(sr).set(Biquad.Type.LOW_PASS, 4500f, 0.7f)
    private val drops = PingPool(64)
    private val swell = Drift(rng, sr, 4f, 12f, 0.7f, 1.1f)
    private val birds = BirdVoice(sr, rng)
    private var nextBird = seconds(rng.range(4f, 12f))

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val s = swell.advance(n)
        val p = 260f / sr
        for (i in 0 until n) {
            if (rng.chance(p)) {
                drops.obtain().trigger(sr, rng.range(2500f, 7000f), rng.range(0.01f, 0.04f), rng.range(0.004f, 0.012f), rng.bipolar())
            }
            if (--nextBird <= 0) {
                birds.call(rng.bipolar() * 0.8f, 0.25f)
                nextBird = seconds(rng.range(6f, 20f))
            }
            l[i] += lpL.process(pinkL.next()) * 1.2f * s
            r[i] += lpR.process(pinkR.next()) * 1.2f * s
            drops.mixInto(l, r, i)
            birds.mixInto(l, r, i)
        }
    }
}

/** Distant rolling thunder: deep rumbles separated by quiet gaps. */
class ThunderGen(sr: Int) : Generator(sr) {
    private val brownL = BrownNoise(rng)
    private val brownR = BrownNoise(Rng(rng.nextInt()))
    private val lpL = Biquad(sr).set(Biquad.Type.LOW_PASS, 220f, 0.8f)
    private val lpR = Biquad(sr).set(Biquad.Type.LOW_PASS, 220f, 0.8f)
    private val rumbleMod = OnePole(sr, 3f)
    private var envPos = 0
    private var attack = 1
    private var length = 1
    private var peak = 0f
    private var wait = seconds(rng.range(3f, 10f))

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            var env = 0.06f
            if (wait > 0) {
                wait--
                if (wait == 0) {
                    envPos = 0
                    attack = seconds(rng.range(0.3f, 1.5f))
                    length = attack + seconds(rng.range(4f, 10f))
                    peak = rng.range(0.5f, 1f)
                }
            } else {
                envPos++
                val e = if (envPos < attack) envPos.toFloat() / attack
                else exp(-4f * (envPos - attack).toFloat() / (length - attack))
                env += e * peak
                if (envPos >= length) wait = seconds(rng.range(12f, 40f))
            }
            val mod = 0.55f + abs(rumbleMod.process(rng.bipolar() * 12f))
            l[i] += lpL.process(brownL.next()) * 4.5f * env * mod
            r[i] += lpR.process(brownR.next()) * 4.5f * env * mod
        }
    }
}

/** Night ocean waves: slow swells with a filter that opens on each crash. */
class OceanGen(sr: Int) : Generator(sr) {
    private val pinkL = PinkNoise(rng)
    private val pinkR = PinkNoise(Rng(rng.nextInt()))
    private val lpL = Biquad(sr)
    private val lpR = Biquad(sr)
    private val waveL = WaveEnvelope(sr, rng)
    private val waveR = WaveEnvelope(sr, rng).also { it.offset(0.35f) }
    private var counter = 0

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val el = waveL.next()
            val er = waveR.next()
            if (counter++ % 64 == 0) {
                lpL.set(Biquad.Type.LOW_PASS, 300f + 3200f * el, 0.7f)
                lpR.set(Biquad.Type.LOW_PASS, 300f + 3200f * er, 0.7f)
            }
            l[i] += lpL.process(pinkL.next()) * (0.25f + 1.6f * el)
            r[i] += lpR.process(pinkR.next()) * (0.25f + 1.6f * er)
        }
    }

    private class WaveEnvelope(private val sr: Int, private val rng: Rng) {
        private var pos = 0
        private var len = newLen()
        private fun newLen() = (rng.range(7f, 12f) * sr).toInt()
        fun offset(fraction: Float) { pos = (len * fraction).toInt() }
        fun next(): Float {
            pos++
            if (pos >= len) { pos = 0; len = newLen() }
            val t = pos.toFloat() / len
            // rise for 40%, then crash and a long washing decay
            return if (t < 0.4f) {
                val x = t / 0.4f
                x * x
            } else {
                exp(-3.5f * (t - 0.4f) / 0.6f)
            }
        }
    }
}

/** Babbling brook: band-passed rushing water plus resonant bubbles. */
class StreamGen(sr: Int) : Generator(sr) {
    private val nl = PinkNoise(rng)
    private val nr = PinkNoise(Rng(rng.nextInt()))
    private val bpL = Biquad(sr).set(Biquad.Type.BAND_PASS, 1100f, 0.6f)
    private val bpR = Biquad(sr).set(Biquad.Type.BAND_PASS, 1300f, 0.6f)
    private val modL = OnePole(sr, 9f)
    private val modR = OnePole(sr, 9f)
    private val bubbles = PingPool(40)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val p = 55f / sr
        for (i in 0 until n) {
            if (rng.chance(p)) {
                bubbles.obtain().trigger(sr, rng.range(450f, 1700f), rng.range(0.03f, 0.09f), rng.range(0.02f, 0.06f), rng.bipolar() * 0.8f, glide = 1.7f)
            }
            val ml = 0.6f + 6f * abs(modL.process(rng.bipolar()))
            val mr = 0.6f + 6f * abs(modR.process(rng.bipolar()))
            l[i] += bpL.process(nl.next()) * 2.6f * ml
            r[i] += bpR.process(nr.next()) * 2.6f * mr
            bubbles.mixInto(l, r, i)
        }
    }
}

/** Wind howling through a canyon: resonant filtered noise with drifting pitch and gusts. */
class WindGen(sr: Int) : Generator(sr) {
    private val wl = Rng(rng.nextInt())
    private val wr = Rng(rng.nextInt())
    private val bpL = Biquad(sr)
    private val bpR = Biquad(sr)
    private val body = BrownNoise(rng)
    private val gust = Drift(rng, sr, 2.5f, 8f, 0.25f, 1f)
    private val pitchL = Drift(rng, sr, 3f, 9f, 250f, 800f)
    private val pitchR = Drift(rng, sr, 3f, 9f, 250f, 800f)
    private var counter = 0

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val g = gust.advance(n)
        val fl = pitchL.advance(n) * (0.7f + 0.5f * g)
        val fr = pitchR.advance(n) * (0.7f + 0.5f * g)
        if (counter++ % 2 == 0) {
            bpL.set(Biquad.Type.BAND_PASS, fl, 5f)
            bpR.set(Biquad.Type.BAND_PASS, fr, 5f)
        }
        for (i in 0 until n) {
            val b = body.next() * 0.35f * g
            l[i] += bpL.process(wl.bipolar()) * 0.9f * g + b
            r[i] += bpR.process(wr.bipolar()) * 0.9f * g + b
        }
    }
}

/** Rustling leaves: bright noise grains whose density follows the breeze. */
class LeavesGen(sr: Int) : Generator(sr) {
    private val hpL = Biquad(sr).set(Biquad.Type.HIGH_PASS, 1800f, 0.7f)
    private val hpR = Biquad(sr).set(Biquad.Type.HIGH_PASS, 1800f, 0.7f)
    private val breeze = Drift(rng, sr, 2f, 6f, 0.2f, 1f)
    private val grainsL = FloatArray(6)
    private val grainsR = FloatArray(6)
    private val decay = exp(-1f / (0.05f * sr))

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val b = breeze.advance(n)
        val p = 70f * b / sr
        for (i in 0 until n) {
            if (rng.chance(p)) {
                val k = (rng.unit() * grainsL.size).toInt()
                val pan = rng.unit()
                val a = rng.range(0.3f, 1f)
                grainsL[k] = a * (1 - pan); grainsR[k] = a * pan
            }
            var el = 0.05f * b
            var er = 0.05f * b
            for (k in grainsL.indices) {
                el += grainsL[k]; er += grainsR[k]
                grainsL[k] *= decay; grainsR[k] *= decay
            }
            l[i] += hpL.process(rng.bipolar()) * el * 0.35f
            r[i] += hpR.process(rng.bipolar()) * er * 0.35f
        }
    }
}

/** Reusable synthesized bird call voice. */
class BirdVoice(private val sr: Int, private val rng: Rng) {
    private var notesLeft = 0
    private var noteLen = 0
    private var notePos = 0
    private var gap = 0
    private var phase = 0f
    private var f0 = 0f
    private var f1 = 0f
    private var amp = 0f
    private var panL = 0.7f
    private var panR = 0.7f
    private var species = 0

    val busy get() = notesLeft > 0

    fun call(pan: Float, amplitude: Float) {
        species = (rng.unit() * 3).toInt()
        notesLeft = when (species) { 0 -> (2 + rng.unit() * 4).toInt(); 1 -> (8 + rng.unit() * 10).toInt(); else -> 2 }
        amp = amplitude
        panL = kotlin.math.sqrt((1 - pan) / 2); panR = kotlin.math.sqrt((1 + pan) / 2)
        startNote()
    }

    private fun startNote() {
        notePos = 0
        when (species) {
            0 -> { noteLen = (0.07f * sr).toInt(); f0 = rng.range(2400f, 3200f); f1 = f0 * rng.range(1.3f, 1.7f); gap = (0.09f * sr).toInt() }
            1 -> { noteLen = (0.03f * sr).toInt(); f0 = rng.range(3800f, 4600f); f1 = f0 * 0.85f; gap = (0.025f * sr).toInt() }
            else -> { noteLen = (0.35f * sr).toInt(); f0 = rng.range(1800f, 2600f); f1 = f0 * rng.range(0.75f, 1.25f); gap = (0.2f * sr).toInt() }
        }
    }

    fun mixInto(l: FloatArray, r: FloatArray, i: Int) {
        if (notesLeft <= 0) return
        if (notePos < noteLen) {
            val t = notePos.toFloat() / noteLen
            val f = f0 + (f1 - f0) * t
            val env = sin(PI.toFloat() * t)
            phase += f / sr
            if (phase >= 1f) phase -= 1f
            val v = SineTable.sin(phase) * env * env * amp
            l[i] += v * panL; r[i] += v * panR
        } else if (notePos >= noteLen + gap) {
            notesLeft--
            if (notesLeft > 0) startNote()
            return
        }
        notePos++
    }
}

/** Morning birdsong over a soft breeze. */
class BirdsGen(sr: Int) : Generator(sr) {
    private val voices = Array(3) { BirdVoice(sr, rng) }
    private val air = PinkNoise(rng)
    private val airLp = Biquad(sr).set(Biquad.Type.LOW_PASS, 1200f, 0.7f)
    private var wait = seconds(0.5f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (--wait <= 0) {
                voices.firstOrNull { !it.busy }?.call(rng.bipolar(), rng.range(0.12f, 0.3f))
                wait = seconds(rng.range(0.4f, 3.5f))
            }
            val a = airLp.process(air.next()) * 0.18f
            l[i] += a; r[i] += a
            for (v in voices) v.mixInto(l, r, i)
        }
    }
}

/** Summer-night crickets: several pulsed high tones with independent rhythms. */
class CricketsGen(sr: Int) : Generator(sr) {
    private class Cricket(val freq: Float, val period: Int, val pulses: Int, val pulseLen: Int, val panL: Float, val panR: Float, var pos: Int)

    private val crickets = Array(4) {
        val pan = rng.bipolar() * 0.9f
        Cricket(
            freq = rng.range(4000f, 5200f),
            period = seconds(rng.range(0.45f, 0.9f)),
            pulses = 3 + (rng.unit() * 3).toInt(),
            pulseLen = seconds(0.018f),
            panL = kotlin.math.sqrt((1 - pan) / 2), panR = kotlin.math.sqrt((1 + pan) / 2),
            pos = (rng.unit() * sr).toInt(),
        )
    }
    private val phases = FloatArray(crickets.size)
    private val night = PinkNoise(rng)
    private val nightLp = Biquad(sr).set(Biquad.Type.LOW_PASS, 600f, 0.7f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val bg = nightLp.process(night.next()) * 0.12f
            var sl = bg
            var sr2 = bg
            for (c in crickets.indices) {
                val cr = crickets[c]
                cr.pos = (cr.pos + 1) % cr.period
                val pulseSlot = cr.pos / (cr.pulseLen * 2)
                val inPulse = pulseSlot < cr.pulses && (cr.pos % (cr.pulseLen * 2)) < cr.pulseLen
                phases[c] += cr.freq / sr
                if (phases[c] >= 1f) phases[c] -= 1f
                if (inPulse) {
                    val t = (cr.pos % (cr.pulseLen * 2)).toFloat() / cr.pulseLen
                    val v = SineTable.sin(phases[c]) * sin(PI.toFloat() * t) * 0.05f
                    sl += v * cr.panL; sr2 += v * cr.panR
                }
            }
            l[i] += sl; r[i] += sr2
        }
    }
}

// ---------------------------------------------------------------- Ambient / environment

/** Crackling campfire: low roar plus random crackles and pops. */
class CampfireGen(sr: Int) : Generator(sr) {
    private val roarL = BrownNoise(rng)
    private val roarR = BrownNoise(Rng(rng.nextInt()))
    private val roarLp = OnePole(sr, 350f)
    private val roarLp2 = OnePole(sr, 350f)
    private val flicker = Drift(rng, sr, 0.3f, 1.5f, 0.6f, 1f)
    private val crackHp = Biquad(sr).set(Biquad.Type.HIGH_PASS, 1500f, 0.7f)
    private var crackEnv = 0f
    private var crackPan = 0f
    private val crackDecay = exp(-1f / (0.004f * sr))
    private var burst = 0
    private val pops = PingPool(8)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val fl = flicker.advance(n)
        val crackP = 9f / sr
        val popP = 0.35f / sr
        for (i in 0 until n) {
            if (burst > 0 && rng.chance(0.002f)) { crackEnv = rng.range(0.2f, 0.6f); burst-- }
            if (rng.chance(crackP)) {
                crackEnv = rng.range(0.3f, 1f); crackPan = rng.bipolar(); burst = (rng.unit() * 4).toInt()
            }
            if (rng.chance(popP)) {
                pops.obtain().trigger(sr, rng.range(180f, 600f), rng.range(0.2f, 0.4f), rng.range(0.03f, 0.08f), rng.bipolar())
            }
            val c = crackHp.process(rng.bipolar()) * crackEnv
            crackEnv *= crackDecay
            l[i] += roarLp.process(roarL.next()) * 1.6f * fl + c * (1 - crackPan) * 0.5f
            r[i] += roarLp2.process(roarR.next()) * 1.6f * fl + c * (1 + crackPan) * 0.5f
            pops.mixInto(l, r, i)
        }
    }
}

/**
 * Parisian café approximation: formant-filtered "babble" voices, room tone and cup clinks.
 * Synthesized; a real recording sounds more natural (see README: replace with assets/sounds/cafe.ogg).
 */
class CafeGen(sr: Int) : Generator(sr) {
    private class Voice(sr: Int, rng: Rng) {
        val f1 = Biquad(sr)
        val f2 = Biquad(sr)
        var pitch = rng.range(95f, 230f)
        var phase = 0f
        var syl = 0
        var env = 0f
        var target = 0f
        var talking = rng.chance(0.5f)
        var phrase = 0
        val pan = rng.bipolar() * 0.8f
    }

    private val voices = Array(5) { Voice(sr, rng) }
    private val room = PinkNoise(rng)
    private val roomLp = Biquad(sr).set(Biquad.Type.LOW_PASS, 900f, 0.7f)
    private val combL = Comb((0.031f * sr).toInt(), 0.55f)
    private val combR = Comb((0.037f * sr).toInt(), 0.55f)
    private val outLpL = Biquad(sr).set(Biquad.Type.LOW_PASS, 2800f, 0.7f)
    private val outLpR = Biquad(sr).set(Biquad.Type.LOW_PASS, 2800f, 0.7f)
    private val clinks = PingPool(12)
    private var clinkWait = seconds(3f)
    private val vowelsF1 = floatArrayOf(300f, 400f, 530f, 660f, 730f, 570f)
    private val vowelsF2 = floatArrayOf(870f, 2300f, 1840f, 1720f, 1090f, 840f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            var bl = 0f
            var br = 0f
            for (v in voices) {
                if (--v.syl <= 0) {
                    v.syl = seconds(rng.range(0.08f, 0.22f))
                    if (--v.phrase <= 0) { v.talking = !v.talking; v.phrase = (rng.range(6f, 25f)).toInt() }
                    v.target = if (v.talking && rng.chance(0.85f)) rng.range(0.4f, 1f) else 0f
                    val k = (rng.unit() * vowelsF1.size).toInt()
                    v.f1.set(Biquad.Type.BAND_PASS, vowelsF1[k] * rng.range(0.9f, 1.1f), 5f)
                    v.f2.set(Biquad.Type.BAND_PASS, vowelsF2[k] * rng.range(0.9f, 1.1f), 6f)
                    v.pitch *= rng.range(0.96f, 1.04f)
                    v.pitch = v.pitch.coerceIn(85f, 260f)
                }
                v.env += (v.target - v.env) * 0.002f
                v.phase += v.pitch / sr
                if (v.phase >= 1f) v.phase -= 1f
                val src = (v.phase * 2f - 1f) + rng.bipolar() * 0.3f
                val s = (v.f1.process(src) + v.f2.process(src) * 0.6f) * v.env
                bl += s * (1 - v.pan); br += s * (1 + v.pan)
            }
            if (--clinkWait <= 0) {
                val base = rng.range(2200f, 4200f)
                val pan = rng.bipolar() * 0.9f
                val a = rng.range(0.02f, 0.06f)
                clinks.obtain().trigger(sr, base, a, rng.range(0.3f, 0.7f), pan)
                clinks.obtain().trigger(sr, base * 2.76f, a * 0.5f, rng.range(0.2f, 0.4f), pan)
                clinkWait = seconds(rng.range(2f, 9f))
            }
            val rt = roomLp.process(room.next()) * 0.5f
            val wl = outLpL.process(bl * 0.25f)
            val wr = outLpR.process(br * 0.25f)
            l[i] += wl + combL.process(wl) * 0.5f + rt
            r[i] += wr + combR.process(wr) * 0.5f + rt
            clinks.mixInto(l, r, i)
        }
    }
}

/** Mechanical clock: tick / tock once per second. */
class ClockGen(sr: Int) : Generator(sr) {
    private val pings = PingPool(6)
    private val clickBp = Biquad(sr).set(Biquad.Type.BAND_PASS, 3500f, 4f)
    private var clickEnv = 0f
    private val clickDecay = exp(-1f / (0.0015f * sr))
    private var pos = 0
    private var tick = true

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (pos == 0) {
                val f = if (tick) 2300f else 1900f
                pings.obtain().trigger(sr, f, 0.12f, 0.025f, 0f)
                pings.obtain().trigger(sr, f * 2.3f, 0.05f, 0.015f, 0f)
                clickEnv = 0.6f
                tick = !tick
            }
            pos = (pos + 1) % sr
            val c = clickBp.process(rng.bipolar()) * clickEnv
            clickEnv *= clickDecay
            l[i] += c; r[i] += c
            pings.mixInto(l, r, i)
        }
    }
}

/** Ceiling fan: soft airy whoosh with gentle blade modulation and motor hum. */
class FanGen(sr: Int) : Generator(sr) {
    private val pl = PinkNoise(rng)
    private val pr = PinkNoise(Rng(rng.nextInt()))
    private val lpL = Biquad(sr).set(Biquad.Type.LOW_PASS, 1100f, 0.7f)
    private val lpR = Biquad(sr).set(Biquad.Type.LOW_PASS, 1100f, 0.7f)
    private var bladePhase = 0f
    private var humPhase = 0f
    private val bladeRate = 3.6f / sr

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            bladePhase += bladeRate; if (bladePhase >= 1f) bladePhase -= 1f
            humPhase += 100f / sr; if (humPhase >= 1f) humPhase -= 1f
            val am = 0.85f + 0.15f * SineTable.sin(bladePhase)
            val hum = SineTable.sin(humPhase) * 0.012f
            l[i] += lpL.process(pl.next()) * 1.7f * am + hum
            r[i] += lpR.process(pr.next()) * 1.7f * (1.7f - am) * 0.6f + hum
        }
    }
}

/** Night train: low rumble, rhythmic wheel clacks over rail joints and a rare distant horn. */
class TrainGen(sr: Int) : Generator(sr) {
    private val rumbleL = BrownNoise(rng)
    private val rumbleR = BrownNoise(Rng(rng.nextInt()))
    private val rumbleLp = OnePole(sr, 180f)
    private val rumbleLp2 = OnePole(sr, 180f)
    private val rattle = PinkNoise(rng)
    private val rattleBp = Biquad(sr).set(Biquad.Type.BAND_PASS, 1400f, 0.8f)
    private val thumps = PingPool(8)
    private val clickLp = OnePole(sr, 2000f)
    private var clickEnv = 0f
    private val clickDecay = exp(-1f / (0.01f * sr))
    private val bar = seconds(1.15f)
    private var pos = 0
    private var swayPhase = 0f
    private var hornWait = seconds(rng.range(40f, 90f))
    private var hornPos = -1
    private val hornLen = seconds(2.4f)
    private var h1 = 0f
    private var h2 = 0f
    private val hornLp = OnePole(sr, 900f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            // "da-dum ...... da-dum" pattern: two pairs per bar
            if (pos == 0 || pos == seconds(0.12f) || pos == seconds(0.55f) || pos == seconds(0.67f)) {
                thumps.obtain().trigger(sr, rng.range(55f, 85f), 0.35f, 0.12f, rng.bipolar() * 0.3f)
                clickEnv = 0.5f
            }
            pos = (pos + 1) % bar
            swayPhase += 0.3f / sr; if (swayPhase >= 1f) swayPhase -= 1f
            val sway = 0.85f + 0.15f * SineTable.sin(swayPhase)
            val click = clickLp.process(rng.bipolar()) * clickEnv
            clickEnv *= clickDecay
            var horn = 0f
            if (hornPos < 0 && --hornWait <= 0) { hornPos = 0 }
            if (hornPos >= 0) {
                val t = hornPos.toFloat() / hornLen
                val env = if (t < 0.1f) t / 0.1f else if (t > 0.8f) (1 - t) / 0.2f else 1f
                h1 += 220f / sr; if (h1 >= 1f) h1 -= 1f
                h2 += 277f / sr; if (h2 >= 1f) h2 -= 1f
                horn = hornLp.process((h1 * 2 - 1) + (h2 * 2 - 1)) * env * 0.05f
                if (++hornPos >= hornLen) { hornPos = -1; hornWait = seconds(rng.range(60f, 150f)) }
            }
            val rat = rattleBp.process(rattle.next()) * 0.5f * sway
            l[i] += rumbleLp.process(rumbleL.next()) * 2.2f * sway + rat + click + horn
            r[i] += rumbleLp2.process(rumbleR.next()) * 2.2f * sway + rat + click * 0.8f + horn
            thumps.mixInto(l, r, i)
        }
    }
}

/** Cat purring: low pulse train with a breathing cycle. */
class CatPurrGen(sr: Int) : Generator(sr) {
    private val noise = BrownNoise(rng)
    private val lp = Biquad(sr).set(Biquad.Type.LOW_PASS, 500f, 0.9f)
    private var pulsePhase = 0f
    private var breathPos = 0
    private var inhale = seconds(1.3f)
    private var exhale = seconds(1.7f)
    private val gap = seconds(0.18f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            val total = inhale + gap + exhale + gap
            breathPos++
            if (breathPos >= total) {
                breathPos = 0
                inhale = seconds(rng.range(1.1f, 1.5f)); exhale = seconds(rng.range(1.5f, 2.0f))
            }
            val rate: Float
            val amp: Float
            when {
                breathPos < inhale -> { rate = 23f; amp = 0.55f * sin(PI.toFloat() * breathPos / inhale) }
                breathPos < inhale + gap -> { rate = 25f; amp = 0f }
                breathPos < inhale + gap + exhale -> { rate = 27f; amp = sin(PI.toFloat() * (breathPos - inhale - gap) / exhale) }
                else -> { rate = 25f; amp = 0f }
            }
            pulsePhase += rate / sr
            if (pulsePhase >= 1f) pulsePhase -= 1f
            val pulse = if (pulsePhase < 0.35f) sin(PI.toFloat() * pulsePhase / 0.35f) else 0f
            val v = lp.process(noise.next() * 6f) * pulse * amp * 0.9f
            l[i] += v; r[i] += v
        }
    }
}

/** Heartbeat in the womb: lub-dub pulses under a muffled whoosh. Soothing for babies. */
class HeartbeatGen(sr: Int) : Generator(sr) {
    private val beats = PingPool(6)
    private val whoosh = PinkNoise(rng)
    private val whooshLp = Biquad(sr).set(Biquad.Type.LOW_PASS, 380f, 0.7f)
    private val period = seconds(60f / 66f)
    private var pos = 0
    private val dub = seconds(0.28f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (pos == 0) beats.obtain().trigger(sr, 48f, 0.7f, 0.16f, 0f, glide = 0.8f)
            if (pos == dub) beats.obtain().trigger(sr, 62f, 0.45f, 0.13f, 0f, glide = 0.8f)
            pos = (pos + 1) % period
            val t = pos.toFloat() / period
            val w = whooshLp.process(whoosh.next()) * (0.8f + 0.6f * exp(-6f * t))
            l[i] += w; r[i] += w
            beats.mixInto(l, r, i)
        }
    }
}

/** Airplane cabin: steady deep broadband engine noise. */
class AirplaneGen(sr: Int) : Generator(sr) {
    private val bl = BrownNoise(rng)
    private val br = BrownNoise(Rng(rng.nextInt()))
    private val pl = PinkNoise(rng)
    private val lp = Biquad(sr).set(Biquad.Type.LOW_PASS, 650f, 0.7f)
    private var hum = 0f

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            hum += 118f / sr; if (hum >= 1f) hum -= 1f
            val common = lp.process(pl.next()) * 1.1f + SineTable.sin(hum) * 0.01f
            l[i] += bl.next() * 0.8f + common
            r[i] += br.next() * 0.8f + common
        }
    }
}

/** Tibetan singing bowl: periodic strikes with long, beating inharmonic partials. */
class SingingBowlGen(sr: Int) : Generator(sr) {
    private val partials = PingPool(16)
    private var wait = seconds(0.2f)
    private val roots = floatArrayOf(196f, 220f, 261.6f, 293.7f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (--wait <= 0) {
                strike(roots[(rng.unit() * roots.size).toInt()], 0.25f)
                wait = seconds(rng.range(14f, 26f))
            }
            partials.mixInto(l, r, i)
        }
    }

    fun strike(f: Float, a: Float) {
        val ratios = floatArrayOf(1f, 2.71f, 5.15f)
        val amps = floatArrayOf(1f, 0.45f, 0.2f)
        val decays = floatArrayOf(14f, 9f, 5f)
        for (k in ratios.indices) {
            // two slightly detuned components per partial produce the characteristic beating
            partials.obtain().trigger(sr, f * ratios[k], a * amps[k] * 0.5f, decays[k], -0.3f)
            partials.obtain().trigger(sr, f * ratios[k] * 1.004f, a * amps[k] * 0.5f, decays[k], 0.3f)
        }
    }
}

/** Wind chimes: pentatonic notes triggered by a gentle breeze. */
class WindChimesGen(sr: Int) : Generator(sr) {
    private val notes = PingPool(24)
    private val breeze = Drift(rng, sr, 3f, 10f, 0.1f, 1f)
    private val scale = floatArrayOf(1046.5f, 1174.7f, 1318.5f, 1568f, 1760f, 2093f)
    private val air = PinkNoise(rng)
    private val airLp = Biquad(sr).set(Biquad.Type.LOW_PASS, 800f, 0.7f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val b = breeze.advance(n)
        val p = 2.2f * b * b / sr
        for (i in 0 until n) {
            if (rng.chance(p)) {
                val f = scale[(rng.unit() * scale.size).toInt()]
                val pan = rng.bipolar() * 0.7f
                val a = rng.range(0.03f, 0.08f)
                notes.obtain().trigger(sr, f, a, rng.range(2f, 4f), pan)
                notes.obtain().trigger(sr, f * 2.76f, a * 0.3f, 1.2f, pan)
            }
            val w = airLp.process(air.next()) * 0.15f * b
            l[i] += w; r[i] += w
            notes.mixInto(l, r, i)
        }
    }
}

/** Underwater: muffled deep rumble with slow rising bubbles. */
class UnderwaterGen(sr: Int) : Generator(sr) {
    private val bl = BrownNoise(rng)
    private val br = BrownNoise(Rng(rng.nextInt()))
    private val lpL = Biquad(sr).set(Biquad.Type.LOW_PASS, 300f, 0.9f)
    private val lpR = Biquad(sr).set(Biquad.Type.LOW_PASS, 300f, 0.9f)
    private val bubbles = PingPool(16)
    private val swell = Drift(rng, sr, 4f, 9f, 0.6f, 1f)

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        val s = swell.advance(n)
        val p = 4f / sr
        for (i in 0 until n) {
            if (rng.chance(p)) bubbles.obtain().trigger(sr, rng.range(250f, 700f), rng.range(0.03f, 0.08f), rng.range(0.05f, 0.12f), rng.bipolar() * 0.7f, glide = 1.9f)
            l[i] += lpL.process(bl.next()) * 2.6f * s
            r[i] += lpR.process(br.next()) * 2.6f * s
            bubbles.mixInto(l, r, i)
        }
    }
}

/** Soft one-shot chime used for Pomodoro phase changes. */
class ChimeGen(sr: Int) : Generator(sr) {
    private val pool = PingPool(8)
    private var fired = false
    var finished = false; private set

    override fun render(l: FloatArray, r: FloatArray, n: Int) {
        if (!fired) {
            fired = true
            pool.obtain().trigger(sr, 659.3f, 0.18f, 2.5f, -0.2f)
            pool.obtain().trigger(sr, 987.8f, 0.12f, 2.0f, 0.2f)
            pool.obtain().trigger(sr, 1318.5f, 0.06f, 1.5f, 0f)
        }
        var any = false
        for (i in 0 until n) pool.mixInto(l, r, i)
        for (p in pool.pings) if (p.active) any = true
        if (!any) finished = true
    }
}

