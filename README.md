# SomnaLooper – Sleep, Focus & Smart Audio Companion

> Ngủ ngon & tập trung sâu — Không quảng cáo, không tốn mạng, trọn vẹn riêng tư.

Android app (Kotlin 100%, Jetpack Compose + Material 3 dark, Media3, Room, DataStore) that
mixes ambient sounds, noise colors and binaural beats **entirely on-device**. No backend, no
INTERNET permission, no analytics.

## Features

| Area | What is implemented |
| --- | --- |
| Multi-layer mixer | 29 built-in sounds in 4 categories (Nature, Ambient, Noise colors, Brainwaves) + "My sounds". Up to 8 layers (free: 3) with independent volume via a glowing circular ring you drag with one finger, or sliders in the "Active sounds" sheet. |
| Sound engine | Every built-in sound is **synthesized in real time** (`audio/dsp/Generators.kt`): rain on tin roof, rainforest, distant thunder, ocean waves, brook, canyon wind, leaves, birds, crickets, underwater, campfire, Paris café, clock, ceiling fan, night train, purring cat, womb heartbeat, airplane cabin, singing bowl, wind chimes, white/pink/brown/green noise, Delta/Theta/Alpha/Beta/Gamma binaural beats. Synthesis never repeats, so there is no loop seam at all, and the APK stays tiny. All synth layers are mixed on one audio thread into a single float `AudioTrack` (power-saving mode) with per-layer ramps and soft clipping. |
| Presets | Save the current mix as a named favourite (Room). 9 curated soundscapes. Rename / delete. |
| Import & seamless looper | Import MP3/M4A/FLAC/WAV/OGG through the system picker (persisted URI permission, no storage permission). Looped by `CrossfadeLooper`: two Media3 ExoPlayers with an equal-power crossfade of 0.5–2 s (configurable) at the loop point. |
| Background playback | `MediaSessionService` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. A `SimpleBasePlayer` bridges the mixer to Media3, so you get the media notification, lock-screen and headset controls; Android 14/15/16 compliant. |
| Sleep timer | Mechanical rotary dial with haptic ticks (1 turn = 60 min, up to 10 h), quick chips 15/30/45/60/120, +15 min. Smooth exponential (linear-in-dB, −60 dB) fade-out over the last 1–15 min. |
| Sleep motion auto-stop (Pro) | Accelerometer (batched, low rate) detects no movement for 5–60 min (default 20), then fades out. 3 sensitivity levels. |
| Battery safeguard (Pro) | Stops playback at 5–30 % (default 15 %) when not charging and posts a notification. |
| Pomodoro focus | 25/5 or 50/10 (long break every 4). Work = brown noise (+ Beta waves for Pro), break = brook + birds, soft chime between phases. |
| Zen ambient display | OLED black, dim retro flip clock or misty hourglass, slow rain or water-ripple animation, screen dimmed and kept on, double-tap to leave. |
| History | Listening sessions (Room) with weekly total and stop reason. |
| Pro | One-time in-app product `somnalooper_pro` via Play Billing 7. Free tier: 15 sounds, 3 layers, standard timer. Debug builds have a "Developer: unlock Pro" switch. |
| Languages | Follows the phone language. Shipped: English (default), Tiếng Việt, Español, Français, Deutsch, Português (BR), Русский, 日本語, 한국어, 简体中文, Bahasa Indonesia, ไทย. Per-app language picker in Settings (Android 13+ system setting is also supported via `locales_config`). |

## Build

Requirements: Android Studio Ladybug or newer (JDK 17+), Android SDK 35.

```bash
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest     # DSP + codec unit tests
./gradlew assembleRelease       # minified; signed with the debug key for testing
```

Before publishing, add your own `signingConfig` in `app/build.gradle.kts` and create the
`somnalooper_pro` product in Play Console.

## Adding / editing translations

Strings live in `tools/i18n/<lang>.txt` (`key|text`, plurals as `@key|one=…|other=…`).
Run `python3 tools/i18n/gen.py` to validate placeholders and regenerate every
`res/values*/strings.xml`. Add the new locale to `res/xml/locales_config.xml` and to
`LANGUAGES` in `ui/settings/SettingsScreen.kt`.

## Using real recordings (optional)

The spec mentions a ~25 MB OGG sound pack. Licensed recordings were not available, so all
sounds are procedural. Synthesized noises, binaural beats, fan, clock, heartbeat and airplane
are indistinguishable from recordings; nature textures (rain, ocean, wind, fire) are convincing;
**café, birds and cat purr are approximations** that benefit most from real recordings.

Drop `assets/sounds/<id>.ogg` (e.g. `cafe.ogg`) and it automatically replaces the synthesized
version, looped with the crossfade looper. See `app/src/main/assets/sounds/README.txt` for ids.

## Not implemented on purpose

* **Ads banner**: the spec's free tier mentions a small static banner. An ad SDK would require
  the INTERNET permission and third-party data collection, contradicting the "100 % private,
  0 backend" promise, so it was left out. Revenue comes from the one-time Pro unlock.
* **$0.99/month subscription**: only the one-time purchase is wired; a subscription can be added
  in `billing/BillingManager.kt`.

## Architecture

```
SomnaApp ─ AppContainer (manual DI, process scope)
  ├─ SoundEngine ── Renderer thread → AudioTrack (all synth layers)
  │              └─ CrossfadeLooper ×N (Media3 ExoPlayer pairs for files)
  ├─ MotionDetector, BatteryGuard (active only while playing + Pro)
  ├─ FocusController (Pomodoro)
  ├─ BillingManager (Play Billing)
  ├─ AppDatabase (Room: presets, tracks, sessions) · SettingsRepository (DataStore)
PlaybackService (MediaSessionService) ─ MixerSessionPlayer (SimpleBasePlayer)
MainActivity (AppCompat, per-app locales) ─ Compose UI: Mixer · Presets · Timer · Focus · Zen · Settings · History
```
