package com.somnalooper.app.data

import androidx.annotation.StringRes
import com.somnalooper.app.R

data class BuiltInPreset(val id: String, @StringRes val nameRes: Int, val mix: Map<String, Float>)

object BuiltInPresets {
    val all = listOf(
        BuiltInPreset("rainy_night", R.string.preset_rainy_night, linkedMapOf("rain_roof" to 0.7f, "thunder" to 0.35f, "fan" to 0.3f, "delta" to 0.3f)),
        BuiltInPreset("deep_focus", R.string.preset_deep_focus, linkedMapOf("brown_noise" to 0.6f, "beta" to 0.35f, "rain_forest" to 0.25f)),
        BuiltInPreset("ocean_dream", R.string.preset_ocean_dream, linkedMapOf("ocean" to 0.75f, "wind" to 0.25f, "delta" to 0.25f)),
        BuiltInPreset("campfire_night", R.string.preset_campfire_night, linkedMapOf("campfire" to 0.65f, "crickets" to 0.45f, "leaves" to 0.3f)),
        BuiltInPreset("baby_sleep", R.string.preset_baby_sleep, linkedMapOf("white_noise" to 0.45f, "heartbeat" to 0.55f)),
        BuiltInPreset("cozy_cafe", R.string.preset_cozy_cafe, linkedMapOf("cafe" to 0.6f, "rain_roof" to 0.4f)),
        BuiltInPreset("meditation", R.string.preset_meditation, linkedMapOf("singing_bowl" to 0.55f, "stream" to 0.4f, "theta" to 0.3f)),
        BuiltInPreset("night_train", R.string.preset_night_train, linkedMapOf("train" to 0.7f, "rain_roof" to 0.35f)),
        BuiltInPreset("forest_morning", R.string.preset_forest_morning, linkedMapOf("birds" to 0.6f, "stream" to 0.45f, "leaves" to 0.3f)),
    )
}
