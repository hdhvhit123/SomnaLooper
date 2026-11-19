package com.somnalooper.app.audio

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.FlutterDash
import androidx.compose.material.icons.outlined.Forest
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ScubaDiving
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Water
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WindPower
import androidx.compose.ui.graphics.vector.ImageVector
import com.somnalooper.app.R
import com.somnalooper.app.audio.dsp.AirplaneGen
import com.somnalooper.app.audio.dsp.BinauralGen
import com.somnalooper.app.audio.dsp.BirdsGen
import com.somnalooper.app.audio.dsp.BrownNoiseGen
import com.somnalooper.app.audio.dsp.CafeGen
import com.somnalooper.app.audio.dsp.CampfireGen
import com.somnalooper.app.audio.dsp.CatPurrGen
import com.somnalooper.app.audio.dsp.ClockGen
import com.somnalooper.app.audio.dsp.CricketsGen
import com.somnalooper.app.audio.dsp.FanGen
import com.somnalooper.app.audio.dsp.Generator
import com.somnalooper.app.audio.dsp.GreenNoiseGen
import com.somnalooper.app.audio.dsp.HeartbeatGen
import com.somnalooper.app.audio.dsp.LeavesGen
import com.somnalooper.app.audio.dsp.OceanGen
import com.somnalooper.app.audio.dsp.PinkNoiseGen
import com.somnalooper.app.audio.dsp.RainForestGen
import com.somnalooper.app.audio.dsp.RainRoofGen
import com.somnalooper.app.audio.dsp.SingingBowlGen
import com.somnalooper.app.audio.dsp.StreamGen
import com.somnalooper.app.audio.dsp.ThunderGen
import com.somnalooper.app.audio.dsp.TrainGen
import com.somnalooper.app.audio.dsp.UnderwaterGen
import com.somnalooper.app.audio.dsp.WhiteNoiseGen
import com.somnalooper.app.audio.dsp.WindChimesGen
import com.somnalooper.app.audio.dsp.WindGen

enum class SoundCategory(@StringRes val label: Int) {
    NATURE(R.string.cat_nature),
    AMBIENT(R.string.cat_ambient),
    NOISE(R.string.cat_noise),
    BRAINWAVE(R.string.cat_brainwave),
    MINE(R.string.cat_mine),
}

/**
 * A built-in sound.
 *
 * Every built-in sound is synthesized on device (zero download, never repeats, no loop seam).
 * If a recorded file named `assets/sounds/<id>.ogg` is bundled, it automatically replaces the
 * synthesized version and is looped with the crossfade looper.
 *
 * @param trim loudness normalization so all sounds sit around the same perceived level.
 */
data class SoundDef(
    val id: String,
    val category: SoundCategory,
    @StringRes val nameRes: Int,
    val icon: ImageVector,
    val pro: Boolean,
    val trim: Float,
    @StringRes val descRes: Int? = null,
    val factory: (Int) -> Generator,
)

object SoundCatalog {
    const val USER_PREFIX = "user:"

    val all: List<SoundDef> = listOf(
        // Nature
        SoundDef("rain_roof", SoundCategory.NATURE, R.string.snd_rain_roof, Icons.Outlined.WaterDrop, false, 1.5f) { RainRoofGen(it) },
        SoundDef("rain_forest", SoundCategory.NATURE, R.string.snd_rain_forest, Icons.Outlined.Forest, false, 0.6f) { RainForestGen(it) },
        SoundDef("thunder", SoundCategory.NATURE, R.string.snd_thunder, Icons.Outlined.Thunderstorm, false, 1.3f) { ThunderGen(it) },
        SoundDef("ocean", SoundCategory.NATURE, R.string.snd_ocean, Icons.Outlined.Waves, false, 0.77f) { OceanGen(it) },
        SoundDef("stream", SoundCategory.NATURE, R.string.snd_stream, Icons.Outlined.Water, false, 0.75f) { StreamGen(it) },
        SoundDef("wind", SoundCategory.NATURE, R.string.snd_wind, Icons.Outlined.Air, false, 1.9f) { WindGen(it) },
        SoundDef("leaves", SoundCategory.NATURE, R.string.snd_leaves, Icons.Outlined.Eco, false, 1.0f) { LeavesGen(it) },
        SoundDef("birds", SoundCategory.NATURE, R.string.snd_birds, Icons.Outlined.FlutterDash, false, 1.6f) { BirdsGen(it) },
        SoundDef("crickets", SoundCategory.NATURE, R.string.snd_crickets, Icons.Outlined.NightsStay, true, 2.5f) { CricketsGen(it) },
        SoundDef("underwater", SoundCategory.NATURE, R.string.snd_underwater, Icons.Outlined.ScubaDiving, true, 0.27f) { UnderwaterGen(it) },
        // Ambient
        SoundDef("campfire", SoundCategory.AMBIENT, R.string.snd_campfire, Icons.Outlined.LocalFireDepartment, false, 0.48f) { CampfireGen(it) },
        SoundDef("cafe", SoundCategory.AMBIENT, R.string.snd_cafe, Icons.Outlined.LocalCafe, true, 1.2f) { CafeGen(it) },
        SoundDef("clock", SoundCategory.AMBIENT, R.string.snd_clock, Icons.Outlined.Schedule, false, 1.5f) { ClockGen(it) },
        SoundDef("fan", SoundCategory.AMBIENT, R.string.snd_fan, Icons.Outlined.WindPower, false, 0.58f) { FanGen(it) },
        SoundDef("train", SoundCategory.AMBIENT, R.string.snd_train, Icons.Outlined.Train, false, 0.38f) { TrainGen(it) },
        SoundDef("cat_purr", SoundCategory.AMBIENT, R.string.snd_cat_purr, Icons.Outlined.Pets, true, 0.42f) { CatPurrGen(it) },
        SoundDef("heartbeat", SoundCategory.AMBIENT, R.string.snd_heartbeat, Icons.Outlined.MonitorHeart, true, 0.75f) { HeartbeatGen(it) },
        SoundDef("airplane", SoundCategory.AMBIENT, R.string.snd_airplane, Icons.Outlined.Flight, true, 0.47f) { AirplaneGen(it) },
        SoundDef("singing_bowl", SoundCategory.AMBIENT, R.string.snd_singing_bowl, Icons.Outlined.SelfImprovement, true, 1.6f) { SingingBowlGen(it) },
        SoundDef("wind_chimes", SoundCategory.AMBIENT, R.string.snd_wind_chimes, Icons.Outlined.MusicNote, true, 2.0f) { WindChimesGen(it) },
        // Noise colors
        SoundDef("white_noise", SoundCategory.NOISE, R.string.snd_white, Icons.Outlined.GraphicEq, false, 0.64f, R.string.desc_white) { WhiteNoiseGen(it) },
        SoundDef("pink_noise", SoundCategory.NOISE, R.string.snd_pink, Icons.Outlined.BlurOn, false, 0.36f, R.string.desc_pink) { PinkNoiseGen(it) },
        SoundDef("brown_noise", SoundCategory.NOISE, R.string.snd_brown, Icons.Outlined.Grain, false, 0.61f, R.string.desc_brown) { BrownNoiseGen(it) },
        SoundDef("green_noise", SoundCategory.NOISE, R.string.snd_green, Icons.Outlined.Spa, true, 0.41f, R.string.desc_green) { GreenNoiseGen(it) },
        // Binaural beats (carrier, beat)
        SoundDef("delta", SoundCategory.BRAINWAVE, R.string.snd_delta, Icons.Outlined.Bedtime, true, 0.5f, R.string.desc_delta) { BinauralGen(it, 150f, 2f) },
        SoundDef("theta", SoundCategory.BRAINWAVE, R.string.snd_theta, Icons.Outlined.SelfImprovement, true, 0.5f, R.string.desc_theta) { BinauralGen(it, 180f, 6f) },
        SoundDef("alpha", SoundCategory.BRAINWAVE, R.string.snd_alpha, Icons.Outlined.WbSunny, true, 0.5f, R.string.desc_alpha) { BinauralGen(it, 200f, 10f) },
        SoundDef("beta", SoundCategory.BRAINWAVE, R.string.snd_beta, Icons.Outlined.Psychology, true, 0.5f, R.string.desc_beta) { BinauralGen(it, 220f, 18f) },
        SoundDef("gamma", SoundCategory.BRAINWAVE, R.string.snd_gamma, Icons.Outlined.Headphones, true, 0.45f, R.string.desc_gamma) { BinauralGen(it, 240f, 40f) },
    )

    private val byId = all.associateBy { it.id }

    fun get(id: String): SoundDef? = byId[id]

    fun isUser(key: String) = key.startsWith(USER_PREFIX)
    fun userKey(trackId: Long) = "$USER_PREFIX$trackId"
    fun userId(key: String): Long? = key.removePrefix(USER_PREFIX).toLongOrNull()

    /** True if the layer is locked behind Pro for a free user. */
    fun isPro(key: String): Boolean = get(key)?.pro ?: false
}
