package com.somnalooper.app.data

import java.util.Locale

/** Encodes a mix (layer key -> volume) as "key=0.70;key2=0.30". */
object MixCodec {
    fun encode(mix: Map<String, Float>): String =
        mix.entries.joinToString(";") { (k, v) -> "$k=" + String.format(Locale.US, "%.3f", v) }

    fun decode(s: String?): Map<String, Float> {
        if (s.isNullOrBlank()) return emptyMap()
        val out = LinkedHashMap<String, Float>()
        s.split(';').forEach { part ->
            val i = part.lastIndexOf('=')
            if (i > 0) part.substring(i + 1).toFloatOrNull()?.let { out[part.substring(0, i)] = it.coerceIn(0f, 1f) }
        }
        return out
    }
}
