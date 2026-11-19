package com.somnalooper.app

import com.somnalooper.app.audio.SoundCatalog
import com.somnalooper.app.data.MixCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class AudioDspTest {
    @Test
    fun everyGeneratorIsFiniteAndAudible() {
        val sr = 44100
        val n = 1024
        val l = FloatArray(n)
        val r = FloatArray(n)
        for (def in SoundCatalog.all) {
            val gen = def.factory(sr)
            var sum = 0.0
            var count = 0
            repeat(30 * sr / n) { // 30 seconds
                l.fill(0f); r.fill(0f)
                gen.render(l, r, n)
                for (i in 0 until n) {
                    assertFalse("${def.id} produced NaN", l[i].isNaN() || r[i].isNaN())
                    assertTrue("${def.id} exploded", abs(l[i]) < 20f && abs(r[i]) < 20f)
                    val a = l[i] * def.trim
                    sum += a * a; count++
                }
            }
            val rms = sqrt(sum / count)
            assertTrue("${def.id} is silent (rms=$rms)", rms > 0.001)
        }
    }

    @Test
    fun mixCodecRoundTrip() {
        val mix = linkedMapOf("rain_roof" to 0.7f, "user:12" to 0.25f)
        val decoded = MixCodec.decode(MixCodec.encode(mix))
        assertEquals(mix.keys.toList(), decoded.keys.toList())
        assertEquals(0.7f, decoded["rain_roof"]!!, 0.001f)
        assertEquals(0.25f, decoded["user:12"]!!, 0.001f)
        assertTrue(MixCodec.decode("garbage;;=").isEmpty())
    }

    @Test
    fun freeTierHasFifteenSounds() {
        assertEquals(15, SoundCatalog.all.count { !it.pro })
    }
}
