# Research log: ideas from the YIN paper

This log records what was tried to improve the detector after a detailed reading of the YIN paper (de Cheveigné and Kawahara 2002, reference [1] in [PITCH_DETECTION.md](../../PITCH_DETECTION.md)): what was measured, and whether each idea was accepted or rejected. The newest entries come last.

## Method

**Recordings.** In November 2026 the test set grew from 42 to 175 recordings of the University of Iowa Musical Instrument Samples ([samples.tsv](../../app/src/test/resources/samples.tsv)). For every instrument of the collection, and for each main playing technique (arco and pizzicato, with and without vibrato), the new set has the lowest, the highest and two middle notes at ff, plus the lowest and highest note at pp where the collection has them. Instruments are chosen by rule, not by how well they work. New instruments: soprano sax, E♭ clarinet, bass clarinet, alto and bass flute, bass trombone, viola, piano, marimba, vibraphone, xylophone, glockenspiel ("bells") and crotales. The file names give the sounding note for every instrument. Checked: the median reading of glockenspiel and xylophone lies within half a semitone of the name. One recording was removed: the double bass pizzicato "G4" sounds at 362 Hz, 138 cents below its name, according to its spectrum.

**Export.** [EvaluationExport](../../app/src/test/java/de/example/tunetheworld/sample/EvaluationExport.kt) runs every recording through the pipeline. For every 1024-sample block it writes the level, whether the level gate passes, the detector's raw reading and confidence, and the reading the tuner shows after smoothing. The variant is chosen with `EVAL_VARIANT`, which sets the experimental switches in `YinOptions`.

```sh
EVAL_OUT=$PWD/build/eval/baseline.csv ./gradlew :app:testDebugUnitTest --tests '*EvaluationExport' --rerun
EVAL_OUT=$PWD/build/eval/x.csv EVAL_VARIANT=octaveMinDip=0.03 ./gradlew :app:testDebugUnitTest --tests '*EvaluationExport' --rerun
python3 docs/research/evaluate.py compare build/eval/baseline.csv build/eval/x.csv
```

**Scoring.** [evaluate.py](evaluate.py) puts every block that passes the gate into a phase, with the rules of `SampleValidationTest`: attack (the first 300 ms), release (more than 12 dB below the note's peak, 20 dB for plucked and struck notes) and settled (the rest). It then classifies each reading against the recording's reference pitch:

- **ok:** within ±50 cents.
- **oct+ / oct−:** an octave off (±100 cents).
- **wrong:** anything else.
- **none:** no reading.

Some players are 50–80 cents out of tune (soprano sax E6, alto and bass flute), so the reference is the named note shifted by the median of the settled readings within ±100 cents of it. "raw" means the detector's output after the confidence filter; "shown" means what the tuner displays after smoothing. Two runs are compared block by block. A block counts as better or worse by the order ok > none > wrong: for a tuner, a wrong note is worse than no note.

The main figure of merit is the settled raw counts. The settled part is what a player tunes to, and raw readings show the detector's own behaviour without the smoothing hiding it. Release and attack are reported too, but they are allowed to be imperfect.

## Baseline

Detector as of commit `000cde5`: YIN with the near-deepest rule, octave check, interpolation on raw d, threshold 0.15. It takes 224 µs per analysis on a desktop JVM.

```
168 recordings with at least one gated block (7 are too short or quiet for one full 93 ms window)
  phase    out    blocks      ok    none    oct+    oct-   wrong
  settled  raw     12501   12252      34     111      37      67
  settled  shown   12501   12278      34     102      18      69
  release  raw       919     753       3     154       5       4
  release  shown     919     771       3     143       0       2
  attack   raw      1920    1677     118      16      34      75
  attack   shown    1920    1649     118      12      39     102
  settled raw jitter (mean of per-file median steps): 1.448 cents
```

98.0 % of settled blocks are right. The errors that remain are real detector errors:

| Recording | Settled blocks wrong | Cause |
|---|---|---|
| Cello pizz C2 | 66 of 66, octave up | the fundamental of the plucked low string is weak |
| Bass pizz C1, E1 | 27 + 3, octave up | same |
| Oboe B♭4 | 56 of 56, 19 semitones up | the 3rd harmonic is 21 dB stronger than the fundamental; YIN takes a third of the period |
| Crotales C6, A♭6 | 36 | inharmonic bell sound |
| Trumpet, violin A4, bass and tenor trombone, tuba, E♭ clarinet | 21, octave down | octave check misfiring (first dip 0.005–0.012) |
| Bass trombone D♭1, B♭1, tuba C1 | many blocks without a reading | low brass below 60 Hz: confidence under 0.8 or no dip below the threshold |

The release phase has 154 octave-up blocks, mostly low strings losing their fundamental as they die away.

## Experiments

The experiments ran in this order. Each one is compared with the baseline that was current at the time: the original baseline up to experiment 5, then the accepted combination `base2`. "Raw" and "shown" give the blocks that got better and worse in the settled part, then in the release, as `+better/−worse`. The code with all experiment switches is in commit `fec3db7` (`YinOptions` in YinPitchDetector.kt, `EVAL_VARIANT` in EvaluationExport), so every run can be repeated there. The next commit removed the switches and kept only the accepted behaviour; its readings are byte-identical to the snapshot's.

### 1. Lower limit of the octave check (`OCTAVE_MIN_DIP`): rejected alone, accepted at 0.02 together with experiment 5

The octave check misfires when the first dip is only just above 0.005. The hypothesis was that the real octave errors start much higher (0.045 on the original 42 recordings), so raising the limit would separate them.

| Limit | Raw settled | Raw release | Shown settled | Shown release |
|---|---|---|---|---|
| 0.01 | +15/−1 | 0/−4 | 0/0 | 0/−4 |
| 0.02 | +20/−3 | 0/−23 | +1/−2 | 0/−27 |
| 0.03 | +20/−6 | 0/−30 | +1/−4 | 0/−38 |
| 0.05 | +22/−15 | 0/−39 | +1/−14 | 0/−48 |

The hypothesis is wrong on the larger set. The bowed double bass D♭1 and C1 and the bowed cello C2 have real octave errors with first dips between 0.005 and 0.05, and raising the limit lets them through. The misfires it removes never reach the display anyway, because the smoother's jump guard holds them back. Alone, the change trades one kind of error for another, so it was rejected. Together with experiment 5, which repairs the low-string octave errors in another way, 0.02 helps (see there).

### 2. Threshold (paper: 0.1, here 0.15): 0.20 accepted at the end

| Threshold | Raw settled | Raw release | Raw attack | Shown settled | Shown attack |
|---|---|---|---|---|---|
| 0.10 | +3/−11 | 0/−7 | +19/−36 | +1/−12 | +33/−55 |
| 0.12 | 0/−5 | 0/−1 | +7/−19 | 0/−7 | +17/−25 |
| 0.20 | +8/0 | +1/0 | +21/−11 | +8/0 | +29/−20 |

A lower threshold loses readings, a higher one gains a few. With 0.20 the threshold matches the confidence filter (confidence = 1 − d′ ≥ 0.80), which drops anything above 0.20 anyway. At first the 8 settled blocks looked like the same blocks that experiment 5 gains. Once experiment 5 resets its state at a closed gate it no longer gains them, so 0.20 was tested again on top of the final combination: raw settled +7/0, release +1/0, attack +15/−9; shown settled +7/0, attack +22/−18. Accepted. In the attack it brings a few more wrong readings along with more right ones.

### 3. Interpolated dip depths when choosing (paper step 5): rejected

The paper interpolates the depth of every dip with a parabola before comparing it with the threshold, because a dip sampled at whole lags can look shallower than it is. Two variants were tried: interpolated depths for choosing only, and for the confidence as well.

Both change 3 blocks out of 15,359, none of them in the settled part. With a 4096-sample window, even a 10-sample period gives dips that are sampled well enough. Rejected: it adds code without a measurable effect.

### 4. Amplitude-change model (paper section VI A, Eq. 12): rejected

The model lets the amplitude change by a constant factor per period, which should help with notes that die away. Eq. (12) squares the cross-correlation, so an inverted copy (half the period of a sine) would also count as a match. Only positive correlation was counted.

- **Instead of d:** raw settled +68/−67, release +22/−12, attack +48/−87; shown settled +64/−55. It fixes the oboe B♭4 (+136) and the bowed cello C2 (+21), but breaks the violin E7 (−50) and adds 48 octave-down blocks. 147 correct readings move by more than a cent (up to 5.3 cents). The paper, too, saw a slight loss on speech.
- **As a fallback** when d gives no confident pitch: no block changes. The model's result is not confident in those blocks either.

Rejected. The oboe result led to experiment 8.

### 5. Best local estimate (paper step 6), adapted: accepted

The paper looks at the estimates within half the longest period around each analysis point, takes the one with the smallest d′, and searches again within ±20 % of its period. Here the analysis points are 1024 samples apart, more than half the longest period (735 samples), so the previous block serves as the neighbourhood. If its dip was cleaner (lower d′) than the current one, the current block is searched again within ±20 % of the previous period: the first dip below the threshold there, or else the deepest point.

| Variant | Raw settled | Raw release | Shown settled | Shown release |
|---|---|---|---|---|
| as in the paper | +63/−71 | +97/−11 | +60/−70 | +84/−16 |
| previous dip at least 2× cleaner | +34/−34 | +57/−6 | +31/−39 | +49/−12 |
| … 4× cleaner | +19/−22 | +37/0 | +13/−25 | +31/−1 |
| … 10× cleaner | +14/−2 | +2/0 | +11/−4 | +3/0 |
| only if the current dip is above 0.02 | +22/−14 | +11/−11 | +18/−17 | +4/−14 |
| only if raw d at the new lag < 0.5 × at the current one | +49/−20 | +51/−9 | +44/−25 | +44/−13 |
| … < 0.3 × | +23/−9 | +39/0 | +19/−11 | +36/0 |
| … < 0.2 × | +14/−6 | +18/0 | +8/−8 | +12/0 |
| < 0.3 × and `OCTAVE_MIN_DIP` 0.01 | +37/−6 | +39/0 | +19/−8 | +36/0 |
| **< 0.3 × and `OCTAVE_MIN_DIP` 0.02** | **+43/−1** | **+36/−5** | **+19/0** | **+28/−2** |
| < 0.3 × and `OCTAVE_MIN_DIP` 0.03 | +43/−1 | +36/−6 | +20/0 | +28/−5 |
| < 0.4 × and `OCTAVE_MIN_DIP` 0.02 | +48/−2 | +45/−7 | +24/−3 | +34/−2 |

As in the paper, it repairs most octave-up errors of low strings (bowed cello C2 +112, pizzicato cello C2 +56, bass D♭1 +56), but it also carries errors forward. A tone that repeats after T also repeats after 2T, so an octave-low reading from an attack, or from an octave-check misfire, fits every following block just as well, and step 6 keeps it. Requiring a much cleaner previous dip removes the harm but also the gain.

What separates the two cases is the raw difference. At the true period of a low string with a weak fundamental, d is much smaller than at half the period. A clean tone gives about the same d at T and at 2T. So step 6 may only move to the previous period if raw d there is less than 0.3 times raw d at the current choice. The remaining harm came from octave-check misfires seeding wrong periods, which `OCTAVE_MIN_DIP` = 0.02 removes. Accepted: guard 0.3 with `OCTAVE_MIN_DIP` 0.02. No correct reading moves by more than 0.93 cents, and the speed is unchanged.

In the evaluation every recording gets a fresh detector, but the live tuner runs from one note to the next. So the detector forgets the previous block whenever the level gate closes. That costs some of the gain, because a few blocks were repaired across short gate dropouts within a note. The new baseline `base2` against the original: raw settled +34/−1, release +36/−5, attack +11/−2; shown settled +9/0, release +28/−2, attack +7/−2.

### 6. Low-pass prefilter (paper section IV): rejected

The paper filters with a 1 ms moving average (first zero at 1 kHz), which would remove every note above 1 kHz here. Gentler moving averages were tried instead:

| Length | First zero | Raw settled | Raw release | Correct readings moved > 1 cent |
|---|---|---|---|---|
| 4 samples | 11 kHz | +3/0 | +1/−6 | 54 (−16 to +8 cents) |
| 8 samples | 5.5 kHz | +15/−1 | 0/−9 | 94 (up to −25 cents) |
| 16 samples | 2.8 kHz | +14/−26 | 0/−11 | 198 |

It helps the bell sounds (glockenspiel, crotales) by damping their inharmonic upper partials, but costs precision on other recordings and adds octave-down errors in releases and in the vibrato flute A5. Rejected.

### 7. DC-offset model (paper section VI C, Eq. 13): rejected

The best constant offset between the signal and its shifted copy is the mean of x[j] − x[j+τ], which gives d_dc(τ) = d(τ) − (1/w)·(Σ x[j] − x[j+τ])². It is cheap with prefix sums. No block changes on the recordings. A constant offset cancels out of d anyway; the model only matters for an offset that drifts within the window, which studio recordings do not have and phone audio rarely does. Rejected: code without a measurable benefit.

### 8. Octave check at three times the lag (not from the paper): accepted

This idea came from the evaluation, not the paper. The oboe B♭4 locks onto a third of the period, and the octave check only tries twice the lag. Three times the lag at short lags is needed (the oboe's third of the period is about 32 samples), but short lags are why the twice-the-lag check has a 100-sample minimum: a multiple can land on a whole sample by chance and look deeper. Comparing the interpolated depths of the dips, the paper's step-5 idea applied to raw d, was tried as the remedy.

| Variant | Raw settled | Shown settled |
|---|---|---|
| 3× the lag, from lag 100 | 0/0 | 0/0 |
| interpolated depths, from lag 100 | 0/0 | 0/0 |
| interpolated depths, 2× and 3× from lag 20 | +56/−24 | +56/−26 |
| 3× from lag 20, sampled depths | +56/−52 | +56/−54 |
| **3× from lag 20, interpolated depths** (2× still from lag 100) | **+56/−1** | **+56/0** |
| 3× from lag 10, interpolated depths | +56/−90 | +56/−86 |

Interpolated depths are what make the short-lag check safe: without them the clarinet B6 loses 114 blocks to a chance multiple. They do not make the twice-the-lag check safe at short lags, though: the clarinet B♭6 and the vibrato flutes still get false octaves there. Accepted: 3× from lag 20 (fundamentals up to 735 Hz) with interpolated depths, 2× unchanged. This rule was made for one recording. Across all phases it makes 5 raw blocks worse (settled 1, release 2, attack 2) and no displayed ones. A unit test with a dominant third harmonic now guards it, and so does the oboe B♭4 in SampleValidationTest.

## Ideas from the paper that were not tried

- **Variable F0 (section VI B):** splits the window to allow a pitch change within it. Vibrato changes the pitch by a few cents over the 46 ms integration window, and the paper saw no gain on speech, which changes pitch much faster.
- **Periodic noise, a second voice, filter banks (sections VI D–F):** for a concurrent second sound or noise with the same spectrum. A tuner listens to one instrument.
- **Larger integration window (section IV):** the paper finds fewer errors with larger windows. Here a larger window needs a longer buffer than 4096 samples, which adds latency, and the current one already meets the paper's rule (window ≥ longest period).
- **Stopping at the first acceptable dip (section V):** lowers latency for a direct implementation. The FFT computes all lags at once anyway, and the near-deepest rule needs all of them.
- **Symmetric window (appendix, Eq. A1):** only shifts which instant an estimate belongs to.

## Result

Accepted: step 6 adapted to the previous block, with a raw-d guard (experiment 5); `OCTAVE_MIN_DIP` 0.02 (experiment 1); the octave check at three times the lag with interpolated depths (experiment 8); threshold 0.20 (experiment 2).

```
final, 168 recordings with gated blocks
  phase    out    blocks      ok    none    oct+    oct-   wrong
  settled  raw     12501   12347      26      98      17      13
  settled  shown   12501   12350      26      93      18      14
  release  raw       919     784       3     123       4       5
  release  shown     919     798       3     117       0       1
  attack   raw      1920    1711      92      14      31      72
  attack   shown    1920    1677      92      12      43      96
  settled raw jitter (mean of per-file median steps): 1.446 cents
```

Against the original baseline, block by block: raw settled +97/−2, release +38/−7, attack +37/−12; shown settled +72/0, release +29/−2, attack +38/−19. Settled errors drop from 249 to 154 blocks (−38 %), and settled accuracy rises from 98.0 % to 98.8 %. No correct reading moves by more than 0.93 cents, and the speed is unchanged (about 225 µs per analysis).

Still open, marked `known-failure` in samples.tsv:

- **Pizzicato cello C2 and double bass C1:** octave up. Step 6 repairs only part of them.
- **Crotales C6 and A♭6:** inharmonic.
- **Bass trombone D♭1 and B♭1:** fewer than half the blocks get a reading, and the readings jitter by more than 4 cents.
- **Bass trombone G2:** one octave-down block in the displayed reading.
- **Piano B0:** no confident reading.

Low brass and low piano notes below about 60 Hz are the most promising next target.
