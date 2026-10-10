# saxtuner

An Android tuner for tenor and alto saxophone. It listens through the microphone, detects the pitch with a self-written YIN detector, and shows the note together with its deviation on a needle gauge. By default it shows the written (transposed) note for tenor sax, because saxophonists read and finger written pitch.

## Features

- Note name with octave, large and readable, plus the measured frequency in Hz.
- Deviation as a signed percentage of a semitone (1 % = 1 cent), shown as a number and on a needle gauge from −50 % to +50 %, with green/yellow/red zones at ±5 % and ±15 %.
- Three display modes: **Tenor sax (B♭)** (default, +14 semitones), **Alto sax (E♭)** (+9 semitones), and **Concert (C)**.
- Adjustable reference pitch A4 from 400 to 480 Hz in 1 Hz steps (default 440 Hz).
- Input level meter, plus an adjustable sensitivity gate from −60 to −30 dBFS (default −40 dBFS), so quiet instruments can be detected and noisy rooms ignored.
- UI in English or German, switchable in the app. Note names follow the language: German uses B and H, English uses B♭ and B.
- The last note stays on screen, greyed out, for 0.8 s after the tone ends.
- The screen stays on while tuning, and the microphone is released when the app goes to the background.
- Reference pitch, display mode, gate and language are saved and survive restarts.

## Status

Version 1 is feature-complete and has been checked on the emulator. The level meter and adjustable gate are the most recent addition.

| Area | State |
| --- | --- |
| Note mapping, note names | Done, unit tested |
| YIN pitch detector, RMS, median smoothing with octave-jump guard | Done, unit tested |
| Pitch pipeline, checked against real alto sax recordings | Done, unit tested |
| Audio input (`AudioRecord`) and tuner engine | Done |
| Settings (DataStore) | Done, unit tested |
| ViewModel and Compose UI (tuner screen, gauge, level meter, settings sheet, permission screen) | Done |
| Emulator check (host audio routed through a virtual PipeWire microphone) | Done |
| Testing on a real device | **Not done yet** |

`./gradlew test` runs 31 unit tests in 8 test classes, and all of them pass.

Known gaps:

- The app has not been tested on real hardware. Latency, stability with a real saxophone and `UNPROCESSED` audio source behaviour are therefore unverified.
- `applicationId` is still the placeholder `de.example.saxtuner`.
- Release builds are not minified and no signing config is set up.

Out of scope for version 1: playing a reference tone, other instruments (soprano or baritone could be added as new `Transposition` values), strobe display, pitch history, and landscape layout.

## How it works

```
AudioRecord ──► TunerEngine ──► PitchPipeline ──────────────────► TunerViewModel ──► Compose UI
 (mono float,    (read loop on   window 4096 / hop 1024             NoteMapper:         TunerScreen
  44.1 kHz)       Dispatchers.    → level gate (gateDb)               freq + ref + mode   NeedleGauge
                  Default)        → YIN (90–1100 Hz)                  → note, percent     LevelMeter
                                  → confidence ≥ 0.80                 hold logic,         SettingsSheet
                                  → median-of-5 + octave guard        sampled every 40 ms
```

- **Audio source:** `UNPROCESSED` if the device supports it, otherwise `VOICE_RECOGNITION`. It never uses `MIC`, because that often turns on noise suppression and automatic gain control.
- **Pitch detection:** YIN in plain Kotlin with no Android dependencies ([YinPitchDetector.kt](app/src/main/java/de/example/saxtuner/dsp/YinPitchDetector.kt)). It finds the fundamental even when the second harmonic is louder, which is common in the saxophone's low register. `detect()` allocates nothing.
- **Smoothing:** a median over 5 values. A jump of more than 0.8 semitones is ignored, unless three consecutive values agree on it, in which case it counts as a real note change ([PitchSmoother.kt](app/src/main/java/de/example/saxtuner/dsp/PitchSmoother.kt)).
- **Note math:** `m = 69 + 12·log2(f / ref)`. The nearest sounding note is `n = round(m)` and the deviation is `(m − n) · 100` %. The transposition is added after rounding, and the reference pitch always means concert A4 ([NoteMapper.kt](app/src/main/java/de/example/saxtuner/music/NoteMapper.kt)). All calculation uses the pitch class index (0–11). The name comes from [NoteNames.kt](app/src/main/java/de/example/saxtuner/music/NoteNames.kt) only at display time, because "B" means different notes in German and English.
- **Tuning constants** are all in [TunerConfig.kt](app/src/main/java/de/example/saxtuner/engine/TunerConfig.kt).

### Project layout

```
app/src/main/java/de/example/saxtuner/
  MainActivity.kt      AppCompatActivity, permission switch
  audio/               AudioInput (AudioRecord wrapper)
  engine/              TunerEngine, TunerConfig
  dsp/                 YinPitchDetector, PitchSmoother, PitchPipeline, SignalUtils (Android-free)
  music/               NoteMapper, NoteNames, Transposition (Android-free)
  data/                SettingsRepository (DataStore), TunerSettings
  ui/                  TunerViewModel, TunerScreen, NeedleGauge, LevelMeter, SettingsSheet, PermissionScreen
app/src/main/res/      strings in values/ (English) and values-de/ (German)
app/src/test/          unit tests, AiffReader test helper
```

## Building

Requirements: JDK 17 or newer (the Gradle daemon toolchain is set to 21) and an Android SDK with API 37. Point `local.properties` at the SDK (`sdk.dir=...`), or open the project in Android Studio.

| | |
| --- | --- |
| Build tooling | Gradle 9.4.1, AGP 9.2.1, Kotlin 2.2.10 |
| SDK | `minSdk 26`, `targetSdk 36`, `compileSdk 37` |
| Dependencies | AndroidX (Compose Material 3, Activity, AppCompat, Lifecycle, DataStore), Kotlin Coroutines, JUnit 4 |

```sh
./gradlew assembleDebug        # build the APK
./gradlew installDebug         # install on a connected device or emulator
./gradlew test                 # run unit tests (no device needed)
```

The app needs the `RECORD_AUDIO` permission. On first launch it explains why and asks for it. If permission was permanently denied, it links to the system app settings.

## Tests

The unit tests run on the JVM and need no device:

- `NoteMapperTest`, `NoteNamesTest`: frequency to note and percent in all modes, and German/English names.
- `YinPitchDetectorTest`: pure sines at 104/440/1000 Hz within 1 cent, a sax-like harmonic tone that must not be detected an octave high, noise, silence, and a runtime budget.
- `PitchSmootherTest`, `SignalUtilsTest`, `PitchPipelineTest`: smoothing, octave guard, RMS and dB conversion, and gate behaviour.
- `SettingsRepositoryTest`: defaults, clamping, and fallback for unknown stored values.
- `SampleValidationTest`: runs four alto saxophone recordings from the [University of Iowa Musical Instrument Samples](https://theremin.music.uiowa.edu/MIS-Pitches-2012/MISEbAltoSaxophone2012.html) through the full pipeline in concert and alto mode. It checks the share of detected blocks, correct note and octave, no octave errors, median deviation, stability, vibrato swing, and onset time. It prints a measurement table for each file. The D3 recording really does jump up an octave briefly (confirmed by ear), so the test allows that one short stretch.

The recordings are not part of the repository. Before the tests run, the `downloadSamples` task in [app/build.gradle.kts](app/build.gradle.kts) downloads them, checks their SHA-256 hashes, and stores them in `~/.gradle/caches/saxtuner-samples/alto-saxophone/`. Once they are cached, no network access is needed. The directory is passed to the tests through the `samples.dir` system property. If the download fails, the build fails rather than skipping the test.

## Testing on the emulator

The emulator can be fed audio from the host through a virtual PipeWire microphone, which leaves the host's default input untouched:

1. Create a virtual microphone with `pw-loopback`: a sink `saxsink` and a source `saxmic`.
2. Start the emulator with a visible window and `-allow-host-audio`, with `PULSE_SOURCE=saxmic` and `PULSE_SERVER=unix:/run/user/1000/pulse/native` set. A headless emulator (`-no-window`) cannot open an audio driver and only receives silence.
3. Run `adb emu avd hostmicoff`, then `hostmicon`, then play a file with `pw-cat -p --target saxsink <file>`. The host microphone bridge only passes about 4–5 s of audio after each toggle and then delivers silence, even if playback continues. So toggle before every note and check the screen within about 2 s.

The emulator uses the `VOICE_RECOGNITION` source (no `UNPROCESSED`), and its latency says nothing about real devices.

## Constraints

The project follows these rules:

- No GPL or LGPL libraries (in particular no TarsosDSP). Only Apache-2.0 AndroidX, Compose, Coroutines, DataStore and JUnit.
- No NDK, C++ or Oboe. DSP code is plain Kotlin and unit-testable.
- Code and comments are in English. All visible text lives in string resources and is kept up to date in both languages.
