"""Evaluates and compares detector runs written by EvaluationExport, block by block. See RESEARCH_LOG.md.

Usage:
  evaluate.py summary RUN.csv [--by-instrument]
  evaluate.py compare BASE.csv NEW.csv [--top N]
  evaluate.py offsets RUN.csv          median reading relative to the note in the file name, per instrument

Every block where the level gate lets the detector run is put into one phase, with the same rules as
SampleValidationTest: attack (first 300 ms after the first such block), release (more than 12 dB below the note's
peak, 20 dB for plucked notes) and settled (the rest). Each reading is then classified against the recording's
reference pitch: ok (within ±50 cents), oct+ / oct- (an octave off, ±100 cents), wrong (anything else), none (no
reading). Players are not always in tune (some recordings sit 50-80 cents off), so the reference is the note in the
file name shifted by the median of the settled readings that lie within ±100 cents of it.
"raw" is the detector's own output after the confidence filter (≥ 0.80), "shown" is what the tuner displays after
smoothing.
"""
import csv
import math
import re
import statistics
import sys
from collections import Counter, defaultdict
from pathlib import Path

MANIFEST = Path(__file__).resolve().parents[2] / "app/src/test/resources/samples.tsv"
NOTE = re.compile(r"\.([A-G])(b?)(\d)(\.stereo)?\.aiff?$")
ATTACK_BLOCKS = 12  # 300 ms in 1024-sample blocks, as in SampleValidationTest
MIN_CONFIDENCE = 0.80
CLASSES = ["ok", "none", "oct+", "oct-", "wrong"]
RANK = {"ok": 2, "none": 1, "oct+": 0, "oct-": 0, "wrong": 0}  # a wrong note is worse than no note


def manifest():
    info = {}
    for line in MANIFEST.read_text().splitlines():
        if not line.strip() or line.startswith("#"):
            continue
        cols = line.split("\t")
        name = cols[0].rsplit("/", 1)[-1]
        letter, flat, octave = NOTE.search(name).groups()[:3]
        midi = (int(octave) + 1) * 12 + "C D EF G A B".index(letter) - (1 if flat else 0)
        offset = 0
        for flag in cols[4].split(","):
            if flag.startswith("sounds"):
                offset = int(flag[len("sounds"):])  # e.g. sounds+24: the file name gives the written note
        info[name] = dict(midi=midi + offset, kind=cols[3], flags=cols[4], instrument=name.split(".")[0].lower())
    return info


def load(path):
    rows = defaultdict(list)
    with open(path) as f:
        for r in csv.DictReader(f):
            rows[r["file"]].append(r)
    return rows


def cents_from(hz, midi):
    return 1200 * math.log2(hz / (440 * 2 ** ((midi - 69) / 12)))


def classify(hz, midi, ref=0.0):
    if hz is None:
        return "none", None
    cents = cents_from(hz, midi) - ref
    if abs(cents) <= 50:
        return "ok", cents
    if abs(cents - 1200) <= 100:
        return "oct+", cents
    if abs(cents + 1200) <= 100:
        return "oct-", cents
    return "wrong", cents


def blocks(rows, info):
    """Yields (file, block, phase, raw class, raw cents, shown class, shown cents) for every gated block."""
    for name, rs in rows.items():
        if name not in info:
            continue  # removed from the set since the run
        meta = info[name]
        loud = [r for r in rs if r["loud"] == "1"]
        if not loud:
            continue
        first = int(loud[0]["block"])
        peak = max(float(r["level_db"]) for r in loud)
        release_db = 20 if meta["kind"] == "PLUCKED" else 12

        def phase_of(r):
            b = int(r["block"])
            level = float(r["level_db"])
            return "attack" if b < first + ATTACK_BLOCKS else "release" if level < peak - release_db else "settled"

        near = [cents_from(float(r["raw_hz"]), meta["midi"]) for r in loud
                if phase_of(r) == "settled" and r["raw_hz"] and float(r["raw_conf"]) >= MIN_CONFIDENCE]
        near = [c for c in near if abs(c) <= 100]
        ref = statistics.median(near) if len(near) >= 5 else 0.0
        for r in loud:
            b = int(r["block"])
            phase = phase_of(r)
            raw = float(r["raw_hz"]) if r["raw_hz"] and float(r["raw_conf"]) >= MIN_CONFIDENCE else None
            shown = float(r["hz"]) if r["hz"] else None
            rc, rcents = classify(raw, meta["midi"], ref)
            sc, scents = classify(shown, meta["midi"], ref)
            yield name, b, phase, rc, rcents, sc, scents


def jitter(entries):
    """Median step in cents between consecutive ok raw readings of the settled part, averaged over files."""
    per_file = defaultdict(list)
    for name, b, phase, rc, rcents, *_ in entries:
        if phase == "settled" and rc == "ok":
            per_file[name].append((b, rcents))
    values = []
    for v in per_file.values():
        steps = [abs(c2 - c1) for (b1, c1), (b2, c2) in zip(v, v[1:]) if b2 == b1 + 1]
        if steps:
            values.append(statistics.median(steps))
    return statistics.mean(values) if values else float("nan")


def table(entries, title):
    print(title)
    print(f"  {'phase':8} {'out':5} {'blocks':>7} " + " ".join(f"{c:>7}" for c in CLASSES))
    for phase in ["settled", "release", "attack"]:
        for out, idx in [("raw", 3), ("shown", 5)]:
            sel = [e[idx] for e in entries if e[2] == phase]
            n = Counter(sel)
            print(f"  {phase:8} {out:5} {len(sel):7d} " + " ".join(f"{n[c]:7d}" for c in CLASSES))
    print(f"  settled raw jitter (mean of per-file median steps): {jitter(entries):.3f} cents")


def summary(path, by_instrument):
    info = manifest()
    entries = list(blocks(load(path), info))
    table(entries, f"{path}: {len({e[0] for e in entries})} recordings")
    if by_instrument:
        groups = defaultdict(list)
        for e in entries:
            groups[info[e[0]]["instrument"]].append(e)
        print("\n  settled, raw: instrument  blocks  ok  none  oct+  oct-  wrong")
        for inst, es in sorted(groups.items()):
            n = Counter(e[3] for e in es if e[2] == "settled")
            print(f"  {inst:14} {sum(n.values()):6d} " + " ".join(f"{n[c]:5d}" for c in CLASSES))


def compare(base_path, new_path, top):
    info = manifest()
    base = {(e[0], e[1]): e for e in blocks(load(base_path), info)}
    new = {(e[0], e[1]): e for e in blocks(load(new_path), info)}
    keys = sorted(set(base) & set(new))
    table(list(base.values()), f"BASE {base_path}")
    table(list(new.values()), f"NEW  {new_path}")
    for out, idx in [("raw", 3), ("shown", 5)]:
        print(f"\n{out}: blocks whose class changed (base -> new), by phase")
        for phase in ["settled", "release", "attack"]:
            moves = Counter((base[k][idx], new[k][idx]) for k in keys if base[k][2] == phase and base[k][idx] != new[k][idx])
            better = sum(v for (a, b), v in moves.items() if RANK[b] > RANK[a])
            worse = sum(v for (a, b), v in moves.items() if RANK[b] < RANK[a])
            detail = ", ".join(f"{a}->{b} {v}" for (a, b), v in moves.most_common())
            print(f"  {phase:8} better {better:5d}  worse {worse:5d}  {detail}")
        per_file = Counter()
        for k in keys:
            per_file[k[0]] += RANK[new[k][idx]] - RANK[base[k][idx]]
        changed = [(v, f) for f, v in per_file.items() if v]
        if changed:
            changed.sort()
            print(f"  worst files: " + ", ".join(f"{f} {v:+d}" for v, f in changed[:top]))
            print(f"  best files:  " + ", ".join(f"{f} {v:+d}" for v, f in reversed(changed[-top:])))
    shifts = [new[k][4] - base[k][4] for k in keys if base[k][3] == "ok" and new[k][3] == "ok" and base[k][2] == "settled"]
    if shifts:
        shifts.sort()
        print(f"\nsettled raw ok in both: shift median {statistics.median(shifts):+.3f}, "
              f"min {shifts[0]:+.3f}, max {shifts[-1]:+.3f} cents, |shift| > 1 cent: {sum(abs(s) > 1 for s in shifts)}")


def offsets(path):
    info = manifest()
    groups = defaultdict(list)
    for name, rs in load(path).items():
        if name not in info:
            continue
        for r in rs:
            if r["raw_hz"] and float(r["raw_conf"]) >= MIN_CONFIDENCE:
                midi = 69 + 12 * math.log2(float(r["raw_hz"]) / 440)
                groups[(info[name]["instrument"], name)].append(midi - info[name]["midi"])
    for (inst, name), v in sorted(groups.items()):
        print(f"  {inst:14} {name:48} median {statistics.median(v):+7.2f} semitones")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "summary":
        summary(sys.argv[2], "--by-instrument" in sys.argv)
    elif cmd == "compare":
        top = int(sys.argv[sys.argv.index("--top") + 1]) if "--top" in sys.argv else 5
        compare(sys.argv[2], sys.argv[3], top)
    elif cmd == "offsets":
        offsets(sys.argv[2])
    else:
        sys.exit(__doc__)
