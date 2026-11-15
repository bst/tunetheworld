# Figures for PITCH_DETECTION.md

The figures in [docs/images](../images) are drawn from the app's own detector code, not from a separate re-implementation. Two steps produce them.

1. **Export the data.** [PlotDataExport](../../app/src/test/java/de/example/tunetheworld/docs/PlotDataExport.kt) is a JUnit test. It is skipped unless `PLOT_DATA_DIR` is set. It runs the test signals and recordings through [YinTrace](../../app/src/test/java/de/example/tunetheworld/docs/YinTrace.kt) and writes CSV files. `YinTrace` repeats the steps of `YinPitchDetector` and keeps every intermediate value. It checks that its result still matches the real detector exactly, and the export fails if it does not. The export also fails if the chosen example blocks no longer show the effect their figure is about.

   ```sh
   PLOT_DATA_DIR=$PWD/build/plot-data ./gradlew :app:testDebugUnitTest --tests '*PlotDataExport' --rerun
   ```

   The step 3 examples use the test recordings, which the Gradle build downloads on first use (see the README).

2. **Draw the figures.** [plot_yin.py](plot_yin.py) reads the CSV files and writes SVGs. It needs Python 3.10 or later with matplotlib and numpy. With [uv](https://docs.astral.sh/uv/), the dependencies declared in the script are installed automatically:

   ```sh
   uv run docs/plots/plot_yin.py build/plot-data docs/images
   ```

   Without uv, install `matplotlib` and `numpy` into a virtual environment and run the script with `python`.

| Figure | Data | Shows |
|---|---|---|
| `yin-functions.svg` | `functions*.csv` | Steps 1 and 2 on the sax-like test tone from `YinPitchDetectorTest` |
| `yin-dip-choice.svg` | `clarinet.*`, `altosax.*` | Step 3: near-deepest rule (clarinet B♭6) and octave check (alto sax D3) |
| `yin-lag-grid.svg` | none, computed from 44100 / τ | Step 4: frequencies that whole-sample lags can express |
| `yin-parabola-fit.svg` | `sine440.*`, `interpolation.*`, `*-continuous.csv` | Step 4: true d(τ) between samples against the parabola through three samples |
| `yin-interpolation.svg` | `sweep.csv` | Step 4: the error across the range (whole samples, parabola on raw d, parabola on d′) |

Rerun both steps after changing the detector, and commit the updated SVGs. Unchanged figures give byte-identical files.
