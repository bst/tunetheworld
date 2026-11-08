# /// script
# requires-python = ">=3.10"
# dependencies = ["matplotlib>=3.8", "numpy>=1.26"]
# ///
"""Draws the figures in PITCH_DETECTION.md from the data written by PlotDataExport. See README.md next to this file.

Usage: plot_yin.py <plot data dir> <output dir>
"""
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import numpy as np  # noqa: E402

SR = 44100
BLUE, ORANGE, GREY, RED, PURPLE, GREEN = "#2563eb", "#ea580c", "#6b7280", "#dc2626", "#7c3aed", "#15803d"

plt.rcParams.update({
    "svg.fonttype": "none",  # keep text as text: smaller files, searchable
    "svg.hashsalt": "tune-the-world",  # stable element ids, so unchanged figures give unchanged files
    "font.family": "sans-serif",
    "font.size": 10,
    "axes.spines.top": False,
    "axes.spines.right": False,
    "axes.titlesize": 10.5,
    "axes.titlelocation": "left",
})


def load(data: Path, name: str):
    trace = np.genfromtxt(data / f"{name}.csv", delimiter=",", names=True)
    meta = {}
    for line in (data / f"{name}.meta").read_text().splitlines():
        key, value = line.split("=", 1)
        meta[key] = value
    return trace, meta


def num(meta, key):
    return float(meta[key])


def cents(f, ref):
    return 1200 * np.log2(f / ref)


def label_lines(ax, xs, names, y, color):
    for x, name in zip(xs, names):
        ax.axvline(x, color=color, lw=0.8, ls="--" if color == ORANGE else ":")
        ax.text(x, y, name, ha="center", va="center", color=color, fontsize=9, bbox=dict(fc="white", ec="none", pad=1))


def save(fig, out: Path):
    fig.savefig(out, bbox_inches="tight", facecolor="white", metadata={"Date": None})
    plt.close(fig)
    print("wrote", out)


def functions(data: Path, out: Path):
    """Steps 1 and 2: signal, d(τ) and d′(τ) of the sax-like test tone."""
    t, meta = load(data, "functions")
    x = np.loadtxt(data / "functions-signal.csv")
    tau, d, c = t["tau"], t["d"], t["cmnd"]
    f0 = num(meta, "expected_hz")
    period, w = SR / f0, 2048
    half = int(round(period / 2))

    fig, ax = plt.subplots(3, 1, figsize=(8, 8.4), facecolor="white",
                           gridspec_kw={"height_ratios": [1, 1.2, 1.2], "hspace": 0.55})

    a = ax[0]
    a.axvspan(0, w, color=BLUE, alpha=0.07, lw=0)
    a.plot(np.arange(len(x)), x, color="black", lw=0.7)
    a.axvspan(period * 2, period * 3, color=ORANGE, alpha=0.18, lw=0)
    a.text(period * 2.5, 2.75, f"one period\nT ≈ {period:.1f} samples", ha="center", va="bottom", fontsize=9,
           color=ORANGE)
    a.text(w / 2, -2.9, f"x[j], j < w = {w} (compared part)", ha="center", va="top", fontsize=9, color=BLUE)
    a.text(w + (len(x) - w) / 2, -2.9, "x[j+τ] reaches up to here", ha="center", va="top", fontsize=9, color=GREY)
    a.set_xlim(0, len(x))
    a.set_ylim(-4.4, 4.6)
    a.set_title(f"Test signal: {f0} Hz (low sax B♭2), partials 0.4, 1.0, 0.7, 0.5, 0.3")
    a.set_xlabel("sample j")

    a = ax[1]
    a.plot(tau, d, color=BLUE, lw=1.1)
    label_lines(a, [period, 2 * period, 3 * period], ["T", "2T", "3T"], d.max() * 1.3, ORANGE)
    label_lines(a, [period / 2], ["T/2"], d.max() * 1.3, GREY)
    a.annotate("d(0) = 0: every signal matches itself", xy=(0, 0), xytext=(230, d.max() * 1.1), va="center",
               fontsize=9, arrowprops=dict(arrowstyle="->", color=GREY, lw=0.8, relpos=(0, 0.5)))
    a.set_xlim(0, tau.max())
    a.set_ylim(0, d.max() * 1.4)
    a.set_title("Step 1: difference function d(τ) = Σ (x[j] − x[j+τ])²")
    a.set_xlabel("lag τ (samples)")

    a = ax[2]
    a.plot(tau, c, color=BLUE, lw=1.1)
    a.axhline(num(meta, "threshold"), color=RED, lw=0.9)
    a.text(tau.max() - 5, num(meta, "threshold") + 0.02, f"threshold {num(meta, 'threshold'):.2f}", ha="right",
           va="bottom", color=RED, fontsize=9)
    a.axhline(1.0, color=GREY, lw=0.6, ls=":")
    for k in (1, 2, 3):
        a.axvline(k * period, color=ORANGE, lw=0.8, ls="--")
    a.axvline(period / 2, color=GREY, lw=0.8, ls=":")
    a.annotate(f"first dip below the threshold:\nτ = {num(meta, 'refined_lag'):.1f} → 44100 / τ = "
               f"{num(meta, 'frequency_hz'):.2f} Hz",
               xy=(period, 0.02), xytext=(period + 110, 2.6), fontsize=9,
               arrowprops=dict(arrowstyle="->", color="black", lw=0.8))
    a.annotate(f"T/2: only even partials line up,\nd′ ≈ {c[half]:.2f}, well above {num(meta, 'threshold'):.2f}",
               xy=(period / 2, c[half] + 0.03), xytext=(20, 3.05), fontsize=9, color=GREY,
               arrowprops=dict(arrowstyle="->", color=GREY, lw=0.8))
    a.set_xlim(0, tau.max())
    a.set_ylim(0, 3.6)
    a.set_title("Step 2: cumulative mean normalized difference d′(τ)")
    a.set_xlabel("lag τ (samples)")

    save(fig, out / "yin-functions.svg")


def dip_choice(data: Path, out: Path):
    """Step 3: the near-deepest rule (clarinet) and the octave check (alto sax)."""
    fig, ax = plt.subplots(3, 1, figsize=(8, 9.6), facecolor="white", gridspec_kw={"hspace": 0.62})

    # Near-deepest rule
    t, meta = load(data, "clarinet")
    tau, c = t["tau"], t["cmnd"]
    f0 = num(meta, "expected_hz")
    period = SR / f0
    plain, chosen = int(num(meta, "plain_lag")), int(num(meta, "final_lag"))
    tau_min = int(np.floor(SR / 4200))
    a = ax[0]
    a.axvspan(0, tau_min, color=GREY, alpha=0.12, lw=0)
    a.text(tau_min / 2, 0.75, "not\nsearched", ha="center", va="center", fontsize=8.5, color=GREY,
           bbox=dict(fc="white", ec="none", alpha=0.8, pad=1))
    a.plot(tau, c, color=BLUE, lw=1.0, marker="o", ms=2.5)
    a.axhline(num(meta, "threshold"), color=RED, lw=0.9, label=f"threshold {num(meta, 'threshold'):.2f}")
    a.axhline(num(meta, "accept"), color=PURPLE, lw=0.9, ls="--",
              label=f"deepest dip + 0.1 = {num(meta, 'accept'):.3f}")
    a.legend(loc="upper right", frameon=False, fontsize=9)
    a.plot([plain], [c[plain]], "x", color=RED, ms=8, mew=1.8)
    a.annotate(f"plain YIN: first dip below {num(meta, 'threshold'):.2f} (d′ = {c[plain]:.2f})\nτ = {plain} → {SR / plain:.0f} Hz, "
               "an octave too high",
               xy=(plain, c[plain]), xytext=(13, 3.75), va="top", fontsize=9, color=RED,
               arrowprops=dict(arrowstyle="->", color=RED, lw=0.8))
    a.plot([chosen], [c[chosen]], "o", color=GREEN, ms=7, mfc="none", mew=1.8)
    a.annotate(f"first dip that is also near the deepest (d′ = {c[chosen]:.3f})\n"
               f"τ = {num(meta, 'refined_lag'):.2f} → {num(meta, 'frequency_hz'):.0f} Hz",
               xy=(chosen, c[chosen]), xytext=(25, 2.85), va="top", fontsize=9, color=GREEN,
               arrowprops=dict(arrowstyle="->", color=GREEN, lw=0.8))
    a.set_xlim(0, 80)
    a.set_ylim(0, 3.9)
    a.set_title(f"Step 3, near-deepest rule: clarinet B♭6 ({f0:.0f} Hz, period {period:.1f} samples), d′(τ)")
    a.set_xlabel("lag τ (samples)")

    # Octave check: d′ finds the half period first
    t, meta = load(data, "altosax")
    tau, d, c = t["tau"], t["d"], t["cmnd"]
    f0 = num(meta, "expected_hz")
    period = SR / f0
    first, double = int(num(meta, "near_deepest_lag")), int(num(meta, "octave_candidate"))
    a = ax[1]
    a.plot(tau, c, color=BLUE, lw=1.0)
    a.axhline(num(meta, "threshold"), color=RED, lw=0.9)
    a.text(695, num(meta, "threshold") + 0.02, f"threshold {num(meta, 'threshold'):.2f}", ha="right", va="bottom", color=RED, fontsize=9)
    a.plot([first], [c[first]], "x", color=RED, ms=8, mew=1.8)
    a.annotate(f"first dip below {num(meta, 'threshold'):.2f}: τ = {first} (d′ = {c[first]:.3f})\nalone this gives about "
               f"{SR / first:.0f} Hz, an octave too high",
               xy=(first, c[first]), xytext=(165, 3.15), va="top", fontsize=9, color=RED,
               arrowprops=dict(arrowstyle="->", color=RED, lw=0.8))
    a.annotate("d′ is deeper here too,\nbut YIN stops at the first dip", xy=(double, c[double] + 0.03),
               xytext=(double + 25, 2.1), fontsize=9, color=GREY, va="center",
               arrowprops=dict(arrowstyle="->", color=GREY, lw=0.8))
    a.set_xlim(0, 700)
    a.set_ylim(0, 3.25)
    a.set_title(f"Step 3b, octave check: alto sax D3 ({f0:.2f} Hz, period {period:.1f} samples), d′(τ)")
    a.set_xlabel("lag τ (samples)")

    # Octave check: the raw difference at twice the lag decides
    a = ax[2]
    a.semilogy(tau[1:], d[1:], color=BLUE, lw=1.0)
    ratio = d[double] / d[first]
    a.plot([first], [d[first]], "x", color=RED, ms=8, mew=1.8)
    a.plot([double], [d[double]], "o", color=GREEN, ms=7, mfc="none", mew=1.8)
    a.plot([first, double], [d[first] * 0.2] * 2, color=PURPLE, lw=0.9, ls="--")
    a.text(double + 12, d[first] * 0.2, "20 % of d(151)", va="center", color=PURPLE, fontsize=9)
    a.annotate(f"d({first}) = {d[first]:.2f}", xy=(first, d[first]), xytext=(first - 120, d[first] * 0.12),
               fontsize=9, color=RED, arrowprops=dict(arrowstyle="->", color=RED, lw=0.8))
    a.annotate(f"d({double}) = {d[double]:.3f}, only {ratio * 100:.1f} % of d({first})\n"
               f"→ the period is twice the lag:\nτ = {num(meta, 'refined_lag'):.1f} → "
               f"{num(meta, 'frequency_hz'):.2f} Hz",
               xy=(double, d[double]), xytext=(double + 45, d[double] * 0.2), fontsize=9, color=GREEN, va="center",
               bbox=dict(fc="white", ec="none", alpha=0.85, pad=1),
               arrowprops=dict(arrowstyle="->", color=GREEN, lw=0.8))
    a.set_xlim(0, 700)
    a.set_ylim(d[double] * 0.04, d.max() * 2)
    a.set_title("Step 3b: the raw difference d(τ), log scale, decides")
    a.set_xlabel("lag τ (samples)")

    save(fig, out / "yin-dip-choice.svg")


def interpolation(data: Path, out: Path):
    """Step 4: the error with and without interpolation, for pure sines across the range."""
    s = np.genfromtxt(data / "sweep.csv", delimiter=",", names=True)
    fig, a = plt.subplots(figsize=(8, 4.2), facecolor="white")
    floor = 1e-3
    a.loglog(s["f"], np.maximum(np.abs(cents(s["integer_lag"], s["f"])), floor), color=ORANGE, lw=0.9,
             label="whole-sample lag only")
    a.loglog(s["f"], np.maximum(np.abs(cents(s["cmnd_parabola"], s["f"])), floor), color=GREY, lw=0.9,
             label="parabola on d′(τ) (earlier version, biased)")
    a.loglog(s["f"], np.maximum(np.abs(cents(s["detected"], s["f"])), floor), color=BLUE, lw=1.2,
             label="parabola on raw d(τ) (what the detector does, as in the paper)")
    a.axhline(1, color=GREY, lw=0.7, ls=":")
    a.text(32, 1.12, "1 cent", color=GREY, fontsize=9)
    a.set_xlim(s["f"].min(), s["f"].max())
    a.set_ylim(floor, 200)
    a.set_xticks([30, 50, 100, 200, 500, 1000, 2000, 4000])
    a.set_xticklabels(["30", "50", "100", "200", "500", "1k", "2k", "4k"])
    a.legend(loc="upper left", frameon=False, fontsize=9)
    a.set_title("Step 4: error for pure sines across the search range (below 0.001 cents drawn at 0.001)")
    a.set_xlabel("frequency (Hz)")
    a.set_ylabel("error (cents, log scale)")
    save(fig, out / "yin-interpolation.svg")


def lag_grid(out: Path):
    """Step 4, why: the frequencies whole-sample lags can express, on a tuner's cents scale."""
    notes = [("E1, double bass", 41.2034), ("A4", 440.0), ("E7, violin", 2637.02), ("C8, piccolo", 4186.01)]
    fig, axes = plt.subplots(len(notes), 1, figsize=(8, 6.4), facecolor="white", gridspec_kw={"hspace": 1.5})
    for a, (name, f0) in zip(axes, notes):
        period = SR / f0
        lags = np.arange(int(period) - 400, int(period) + 401)
        lags = lags[lags > 1]
        c = cents(SR / lags, f0)
        visible = np.abs(c) <= 100
        a.axvspan(-5, 5, color=GREEN, alpha=0.15, lw=0)
        a.axvline(0, color=GREEN, lw=1.0)
        a.vlines(c[visible], 0, 1, color=BLUE, lw=1.0 if visible.sum() < 40 else 0.6)
        if visible.sum() < 40:
            for lag, x in zip(lags[visible], c[visible]):
                a.text(x, 1.12, f"τ={lag}", ha="center", va="bottom", fontsize=8, color=BLUE)
        step = 1200 * np.log2((period + 1) / period)
        a.set_title(f"{name}: {f0:.2f} Hz, period {period:.1f} samples, one sample = {step:.1f} cents", pad=20)
        a.set_xlim(-100, 100)
        a.set_ylim(0, 1)
        a.set_yticks([])
        a.spines["left"].set_visible(False)
        a.set_xticks([-100, -50, -5, 0, 5, 50, 100])
        a.set_xticklabels(["−100", "−50", "", "0", "", "+50", "+100"])
    axes[-1].set_xlabel("cents from the played note (green: ±5 cents)")
    save(fig, out / "yin-lag-grid.svg")


def parabola_fit(data: Path, out: Path):
    """Step 4, why a parabola: the true curve between samples against the parabola through three samples."""
    fig, axes = plt.subplots(1, 2, figsize=(8, 3.9), facecolor="white", gridspec_kw={"wspace": 0.12})
    for a, name in zip(axes, ["sine440", "interpolation"]):
        t, meta = load(data, name)
        cont = np.genfromtxt(data / f"{name}-continuous.csv", delimiter=",", names=True)
        f0 = num(meta, "expected_hz")
        period = SR / f0
        scale = cont["d"].max()
        a.plot(cont["tau"] - period, cont["d"] / scale, color=GREY, lw=1.2, label="true d(τ) between samples")

        lag = int(round(period))
        lag = lag if t["d"][lag] <= min(t["d"][lag - 1], t["d"][lag + 1]) else int(np.floor(period))
        near = (t["tau"] >= period - 8) & (t["tau"] <= period + 8)
        a.plot(t["tau"][near] - period, t["d"][near] / scale, "o", color=BLUE, ms=4, label="whole-sample values")
        s0, s1, s2 = (t["d"][lag + k] / scale for k in (-1, 0, 1))
        pa, pb = (s0 - 2 * s1 + s2) / 2, (s2 - s0) / 2
        fit = np.linspace(-8, 8, 400) + period
        a.plot(fit - period, pa * (fit - lag) ** 2 + pb * (fit - lag) + s1, color=PURPLE, lw=1.2,
               label="parabola through three samples")
        a.plot([lag - 1 - period, lag - period, lag + 1 - period], [s0, s1, s2], "o", color=PURPLE, ms=8, mfc="none",
               mew=1.5)
        vertex = lag - pb / (2 * pa)
        err = cents(SR / vertex, f0)
        a.axvline(0, color=ORANGE, lw=0.8, ls="--")
        a.set_xlim(-8, 8)
        a.set_ylim(-0.03, 1.15)
        a.set_title(f"{f0:g} Hz: {period:.1f} samples per period")
        a.text(0, 1.1, f"vertex {vertex - period:+.3f} samples from the true period ({err:+.2f} cents)",
               ha="center", va="top", fontsize=8.5, color=PURPLE, bbox=dict(fc="white", ec="none", pad=1))
        a.set_xlabel("lag minus true period (samples)")
        a.set_yticks([])
    axes[0].set_ylabel("d(τ), scaled")
    axes[0].legend(loc="center left", bbox_to_anchor=(0, 0.6), frameon=False, fontsize=8.5)
    save(fig, out / "yin-parabola-fit.svg")


def main():
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    data, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    functions(data, out)
    dip_choice(data, out)
    lag_grid(out)
    parabola_fit(data, out)
    interpolation(data, out)


if __name__ == "__main__":
    main()
