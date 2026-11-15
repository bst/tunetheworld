# Tune the World

A free tuner for Android. No ads, no tracking, no account. The app has no internet permission, so nothing you play ever leaves your phone.

It listens through the microphone, detects the pitch with a self-written YIN detector, and shows the note together with its deviation on a needle gauge. It works with any instrument or voice from about 30 Hz to 4200 Hz, from the low B of a five-string bass to the top of the piccolo. By default it shows the sounding (concert) pitch. For transposing instruments you can set the transposition in semitones, and the tuner then shows the written note.

## Features

- Note name with octave, large and readable, plus the measured frequency in Hz.
- Deviation as a signed percentage of a semitone (1 % = 1 cent), shown as a number and on a needle gauge from −50 % to +50 %, with green/yellow/red zones at ±5 % and ±15 %.
- Any transposition from −24 to +26 semitones (default 0, concert pitch). That covers everything from glockenspiel (−24) through trumpet and clarinet in B♭ (+2), horn in F (+7), alto sax (+9), guitar and double bass (+12) and tenor sax (+14) to bass saxophone and contrabass clarinet (+26). The settings show which note a written C4 sounds as, for example B♭2 for tenor sax.
- Adjustable reference pitch A4 from 400 to 480 Hz in 1 Hz steps (default 440 Hz).
- Input level meter, plus an adjustable sensitivity gate from −60 to −30 dBFS (default −40 dBFS), so quiet instruments can be detected and noisy rooms ignored.
- UI in English or German, switchable in the app. Note names follow the language: German uses B and H, English uses B♭ and B.
- The last note stays on screen, greyed out, for 0.8 s after the tone ends.
- The screen stays on while tuning, and the microphone is released when the app goes to the background.
- Reference pitch, transposition, gate and language are saved and survive restarts. A tenor or alto mode chosen in version 1 carries over as +14 or +9.

## Status

Version 1 is feature-complete and has been checked on the emulator and on a real device. The level meter and adjustable gate are the most recent addition.

| Area | State |
| --- | --- |
| Note mapping, note names | Done, unit tested |
| YIN pitch detector, RMS, median smoothing with octave-jump guard | Done, unit tested |
| Pitch pipeline, checked against 175 real recordings of 25 instruments | Done, unit tested |
| Audio input (`AudioRecord`) and tuner engine | Done |
| Settings (DataStore) | Done, unit tested |
| ViewModel and Compose UI (tuner screen, gauge, level meter, settings sheet, permission screen) | Done |
| Emulator check (host audio routed through a virtual PipeWire microphone) | Done |
| Testing on a real device | Done, works as expected |

`./gradlew test` runs 48 unit tests in 10 test classes, and all of them pass. Two more classes only export data and are skipped unless asked for: `PlotDataExport` writes the data behind the figures in [PITCH_DETECTION.md](PITCH_DETECTION.md) (see [docs/plots/README.md](docs/plots/README.md)), and `EvaluationExport` writes every reading for the block-by-block evaluation (see the [research log](docs/research/RESEARCH_LOG.md)).

Known gaps:

- `applicationId` is `de.example.tunetheworld`, which still uses the placeholder `de.example` prefix.
- Release builds are not minified and no signing config is set up.

Out of scope for now: playing a reference tone, strobe display, pitch history, and landscape layout.

## How it works

```mermaid
flowchart LR
    A["<b>AudioRecord</b><br/>mono float, 44.1 kHz"]
    B["<b>TunerEngine</b><br/>read loop on<br/>Dispatchers.Default"]
    subgraph P["PitchPipeline (window 4096 / hop 1024)"]
        direction TB
        P1["level gate (gateDb)"] --> P2["YIN (30–4200 Hz)<br/>+ octave check<br/>+ memory of last block"]
        P2 --> P3["confidence ≥ 0.80"]
        P3 --> P4["median-of-5 + octave guard"]
    end
    C["<b>TunerViewModel</b><br/>NoteMapper: freq + ref + transposition<br/>→ note, percent<br/>hold logic, sampled every 40 ms"]
    subgraph UI["Compose UI"]
        direction TB
        U1[TunerScreen]
        U2[NeedleGauge]
        U3[LevelMeter]
        U4[SettingsSheet]
    end
    A --> B --> P --> C --> UI
```

- **Audio source:** `UNPROCESSED` if the device supports it, otherwise `VOICE_RECOGNITION`. It never uses `MIC`, because that often turns on noise suppression and automatic gain control.
- **Pitch detection:** YIN in plain Kotlin with no Android dependencies ([YinPitchDetector.kt](app/src/main/java/de/example/tunetheworld/dsp/YinPitchDetector.kt)). It searches 30–4200 Hz. Three additions to plain YIN keep it on the right octave:
  - A dip is only accepted if it is within 0.1 of the deepest dip. Otherwise a shallow dip at half the period can win on high notes (clarinet B♭6).
  - An octave check looks at twice the lag (for lags of 100 samples or more, below about 440 Hz) and three times the lag (for lags of 20 or more). If the dip in the raw difference there is less than a fifth as deep, that multiple is taken as the period. This catches notes whose second or third harmonic is much louder than the fundamental: low strings, the saxophone's low register, the oboe's B♭4.
  - The detector remembers the previous block. If its dip was cleaner and the raw difference is much smaller near its period, the current block takes that period. This is the YIN paper's step 6, adapted; it repairs octave errors of low strings as their fundamental fades.

  The [research log](docs/research/RESEARCH_LOG.md) records how these rules and their constants were chosen.

  The difference function is computed with an FFT ([DifferenceFunction.kt](app/src/main/java/de/example/tunetheworld/dsp/DifferenceFunction.kt)): expanding the square leaves two energy terms (running sums) and a cross-correlation, which one forward and one inverse FFT of size 4096 give for every lag at once. Everything is in double precision, because near a dip the result is a small difference of large terms. This is about 12× faster than the plain loop it replaced (about 240 µs instead of 2.8 ms per analysis on a desktop JVM). Across the 42 test recordings of the time it gave the same readings within 0.002 cents. `detect()` allocates nothing. The level gate checks both ends of the analysis window, so a note's first blocks are not analysed while the window still holds the silence before it.
- **Smoothing:** a median over 5 values. A jump of more than 0.8 semitones is ignored, unless three consecutive values agree on it, in which case it counts as a real note change ([PitchSmoother.kt](app/src/main/java/de/example/tunetheworld/dsp/PitchSmoother.kt)).
- **Note math:** `m = 69 + 12·log2(f / ref)`. The nearest sounding note is `n = round(m)` and the deviation is `(m − n) · 100` %. The transposition is added after rounding, and the reference pitch always means concert A4 ([NoteMapper.kt](app/src/main/java/de/example/tunetheworld/music/NoteMapper.kt)). All calculation uses the pitch class index (0–11). The name comes from [NoteNames.kt](app/src/main/java/de/example/tunetheworld/music/NoteNames.kt) only at display time, because "B" means different notes in German and English.
- **More detail:** [PITCH_DETECTION.md](PITCH_DETECTION.md) describes every step, why it is there, and the papers it builds on.
- **Tuning constants** are all in [TunerConfig.kt](app/src/main/java/de/example/tunetheworld/engine/TunerConfig.kt).

### Project layout

```
app/src/main/java/de/example/tunetheworld/
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
./gradlew lintDebug            # run Android lint
./gradlew ktlintCheck          # check Kotlin code style
./gradlew ktlintFormat         # fix Kotlin code style where possible
```

Git hooks in `.githooks/` run these checks. Enable them once per clone with `git config core.hooksPath .githooks`.

- **pre-commit** runs ktlint and Android lint. Lint treats warnings as errors. Warnings that already existed are listed in `app/lint-baseline.xml` and ignored.
- **pre-push** runs the unit tests.

ktlint uses the Android Studio code style (see `.editorconfig`), with Composable functions allowed to start with a capital letter.

The app needs the `RECORD_AUDIO` permission. On first launch it explains why and asks for it. If permission was permanently denied, it links to the system app settings.

## Tests

The unit tests run on the JVM and need no device:

- `NoteMapperTest`, `NoteNamesTest`, `TranspositionTest`: frequency to note and percent with and without transposition, German/English names, the key and the sounding note of a written C.
- `YinPitchDetectorTest`: pure sines from 31 Hz to 4186 Hz (all within 1 cent), sax-like and low-string tones with a weak fundamental that must not be detected an octave high, a tone with a dominant third harmonic, the memory of the previous block (repairs a fading fundamental, does not hold back a real note change, forgets on reset), noise, silence, and a runtime budget.
- `DifferenceFunctionTest`: the FFT difference function matches the plain definition (computed in double precision) on noise and harmonic tones, keeps exact zeros at the period of a perfectly periodic tone, and gives zero for silence.
- `PitchSmootherTest`, `SignalUtilsTest`, `PitchPipelineTest`: smoothing, octave guard, RMS and dB conversion, and gate behaviour.
- `SettingsRepositoryTest`: defaults, clamping, migration of the version 1 modes, and fallback for unknown stored values.
- `SampleValidationTest`: runs 175 recordings from the [University of Iowa Musical Instrument Samples](https://theremin.music.uiowa.edu/MIS.html) through the full pipeline. They cover all 25 instruments of the collection with their lowest, highest and two middle notes at ff, plus the lowest and highest at pp. The instruments range from double bass C1 (32.7 Hz) to glockenspiel and crotales near C8: saxophones, clarinets, flutes, oboe, bassoon, brass, bowed and plucked strings, piano, and mallet percussion. Each recording is checked in concert pitch and in its instrument's usual transposition, against its player's reference pitch (the named note shifted by the median of the settled readings within ±100 cents of it). The test splits every note into three phases:
  - **Attack:** the first 300 ms. Only the onset time is checked, because the sound itself is still settling: overblown flute notes speak in the octave below for a moment, and low brass slides into the pitch.
  - **Settled part:** judged strictly. It must show the right note and octave in at least 95 % of blocks, have no octave errors, and change by at most 3 cents per block in the median. Struck and plucked notes that die away before they settle are only checked for wrong readings, and notes shorter than about 230 ms of full windows are only reported.
  - **Release:** the note has fallen more than 12 dB below its peak (20 dB for plucked notes). Low strings lose their fundamental as they die away, so readings an octave up are tolerated there and listed in the report.

  The test prints a measurement table for each file. The alto sax D3 recording really does jump up an octave briefly (confirmed by ear), so the test allows that one short stretch. Eight recordings that the detector still gets wrong are marked `known-failure`: they are run and reported, but do not fail the test (see [PITCH_DETECTION.md](PITCH_DETECTION.md#remaining-weak-spots)).
- `EvaluationExport` (skipped unless `EVAL_OUT` is set) writes the reading of every analysis block of every recording, for comparing detector versions block by block with [docs/research/evaluate.py](docs/research/evaluate.py).

The recordings are not part of the repository. Their site says they "may be downloaded and used for any projects, without restrictions". They are listed with their SHA-256 hashes in [app/src/test/resources/samples.tsv](app/src/test/resources/samples.tsv), which both the test and the `downloadSamples` task in [app/build.gradle.kts](app/build.gradle.kts) read. To add a recording, add a line there. Before the tests run, the task downloads any missing files (about 170 MB in total), checks their hashes, and stores them in `~/.gradle/caches/tunetheworld-samples/iowa-mis/`. Once they are cached, no network access is needed. The directory is passed to the tests through the `samples.dir` system property. If the download fails, the build fails rather than skipping the test.

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
