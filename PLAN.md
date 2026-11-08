# Plan

What comes next for Tune the World. Version 1 is feature-complete and tested on a real device (see [README.md](README.md#status)). It has since been generalized from a saxophone tuner to any instrument: 30–4200 Hz, any transposition from −24 to +26 semitones, checked against 42 recordings of 12 instruments. The next work makes the project ready to publish as open source and keeps it working once other people send changes.

## Next steps

In rough order:

1. **Check the wider range on a real device.** The detector now searches 30–4200 Hz instead of 90–1100 Hz, which makes each analysis about 3× more expensive (3.5 ms on a desktop JVM, about 43 analyses per second). Measure the CPU load and battery use on the phone. If it is too high, compute the YIN difference function with an FFT (pure Kotlin, in double precision), which cuts the cost by roughly 10×. The `runtime` test in `YinPitchDetectorTest` is now close to its 5 s limit and could become flaky on slow CI runners; recheck its limit then.
2. **Prepare for open source.**
   - Choose a license and add a `LICENSE` file. It must be compatible with the "no GPL/LGPL libraries" rule; Apache-2.0 matches the dependencies.
   - Replace the placeholder `de.example` prefix in `applicationId` and the package name before the first public release. Changing it later breaks updates for installed apps.
   - Add a short `CONTRIBUTING.md`: how to build, enable the git hooks, run the tests, and the project constraints from the README.
3. **Milestone: CI for pull requests.** See [below](#milestone-ci-for-pull-requests).
4. **Release builds.** Turn on minification, set up a signing config with the key kept outside the repository, and decide on a distribution channel (GitHub Releases, F-Droid, Play Store).

Still out of scope for now: reference tone, more transpositions, strobe display, pitch history, landscape layout.

## Milestone: CI for pull requests

**Goal:** every pull request is automatically checked to build, pass all checks, and start on Android before it can be merged. It must cost nothing for a public repository and work for pull requests from forks.

**Platform:** GitHub Actions on `ubuntu-latest`. Public repositories get unlimited free minutes on standard runners, and those runners support KVM, so the Android emulator runs at usable speed.

### Job 1: checks (every pull request and push to `main`, ~3–5 min)

Runs the same checks as the git hooks, plus an APK build:

```sh
./gradlew :app:ktlintCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

- [ ] Add `.github/workflows/ci.yml` with `actions/checkout`, `actions/setup-java` (Temurin 21, matching `gradle-daemon-jvm.properties`) and `gradle/actions/setup-gradle` (Gradle caching and wrapper validation).
- [ ] Cache `~/.gradle/caches/tunetheworld-samples` with `actions/cache`, keyed on the hash of `app/src/test/resources/samples.tsv`. Otherwise every run downloads the recordings (about 32 MB) from the University of Iowa server and fails when it is down.
- [ ] Upload `app/build/reports` as a build artifact when the job fails, so lint and test reports can be read.
- [ ] If the runner image lacks API 37 and AGP does not download it, add an `sdkmanager "platforms;android-37"` step.

### Job 2: instrumented smoke tests on an emulator (~6–10 min per API level)

All current tests run on the JVM, so nothing yet checks that the app starts on a phone. The emulator's microphone is silent or synthetic, so these tests cover startup, the permission flow and UI, not pitch accuracy. Pitch accuracy is already covered by `SampleValidationTest`.

- [ ] Add `app/src/androidTest` and the dependencies (`androidx.test` runner and rules, `compose-ui-test-junit4`, `ui-test-manifest` for debug), plus `testInstrumentationRunner` in `defaultConfig`.
- [ ] Smoke test: launch `MainActivity` with `RECORD_AUDIO` granted (`GrantPermissionRule`) and check that the tuner screen appears and the engine starts without crashing.
- [ ] Smoke test: open the settings sheet, change the display mode and the reference pitch, and check that the screen reflects the change.
- [ ] Smoke test: launch without the permission and check that the permission screen is shown.
- [ ] Run `connectedDebugAndroidTest` with `reactivecircus/android-emulator-runner` in a matrix over API 26 (`minSdk`) and API 35 or 36 (near `targetSdk`). Use the `aosp_atd` target and cache the AVD snapshot to keep runs fast and stable.

### Repository settings

- [ ] Protect `main`: require both jobs to pass before merging.
- [ ] Enable Dependabot for Gradle dependencies and GitHub Actions versions.
- [ ] Add a CI badge and a short "Continuous integration" section to the README.

### Optional later

- Firebase Test Lab on real devices (free daily quota on the Spark plan). It needs a secret, which GitHub does not give to pull requests from forks, so run it only on pushes to `main`, never as a required check.
- If emulator minutes become a problem, run Job 2 only on pushes to `main` or only when `app/src/main` changes.

### Done when

- A pull request from a fork runs both jobs without any secrets and shows their status on the pull request.
- A deliberately broken change (a lint error, a failing unit test, a crash on startup) is blocked from merging.
- Both jobs pass on `main`, and a full pull request run takes under 15 minutes.
