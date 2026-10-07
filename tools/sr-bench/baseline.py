#!/usr/bin/env python3
"""Regression baseline for burst_bench: run a fixed matrix of configs, save
their machine-readable metrics, and check later runs against the snapshot.

Every metric is compared with a relative tolerance; "higher is better" keys
(correlations, MTF50, delivery/amplitude) regress on a decrease, the rest on an
increase. Exit code 1 on any regression, so a tuning change cannot land by
quietly trading another dimension away.

Usage:
    baseline.py --save baseline.json     # freeze the current build
    baseline.py --check baseline.json    # verify no regressions
"""
import argparse
import json
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
PY = sys.executable

CONFIGS = [
    ("ramp9", ["--scene", "ramp", "--frames", "9"]),
    ("diag9", ["--scene", "diag", "--frames", "9"]),
    ("diag40", ["--scene", "diag", "--frames", "40"]),
    ("grad9", ["--scene", "grad", "--frames", "9"]),
    ("tones9", ["--scene", "tones", "--frames", "9"]),
    ("tones40", ["--scene", "tones", "--frames", "40"]),
    ("photo9", ["--scene", "photo", "--frames", "9"]),
    ("photo40", ["--scene", "photo", "--frames", "40"]),
    ("tones9noise", ["--scene", "tones", "--frames", "9", "--noise", "0.01"]),
]
BASE = ["--cfa", "on", "--kmap", "real", "--gate", "off"]
HIGHER = ("corr", "mtf50", "deliver", "below.amp", "c_sr", "c_lo", "c_hi")


def flatten(d, prefix=""):
    out = {}
    if isinstance(d, dict):
        for k, v in d.items():
            out.update(flatten(v, prefix + str(k) + "."))
    elif isinstance(d, list):
        for i, v in enumerate(d):
            out.update(flatten(v, prefix + str(i) + "."))
    elif isinstance(d, (int, float)):
        out[prefix[:-1]] = float(d)
    return out


def dump_exists():
    dump = os.path.normpath(os.path.join(HERE, "..", "..", "srsamples", "srdump"))
    return os.path.exists(os.path.join(dump, "lattice.raw")) and \
        os.path.exists(os.path.join(dump, "params.json"))


def run_matrix(only=None):
    res = {}
    have_photo = dump_exists()
    for name, extra in CONFIGS:
        if only and name not in only:
            continue
        if name.startswith("photo") and not have_photo:
            print("  skip", name, "(no srsamples/srdump)")
            continue
        with tempfile.NamedTemporaryFile(suffix=".json", delete=False) as tf:
            path = tf.name
        cmd = [PY, os.path.join(HERE, "burst_bench.py")] + BASE + extra + \
            ["--metrics", path]
        p = subprocess.run(cmd, capture_output=True, text=True)
        if p.returncode != 0:
            print("FAILED", name)
            print(p.stdout[-3000:])
            print(p.stderr[-3000:])
            sys.exit(1)
        res[name] = flatten(json.load(open(path)))
        os.unlink(path)
        print("  ran", name)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--save", default="")
    ap.add_argument("--check", default="")
    ap.add_argument("--only", default="", help="comma-separated config names")
    ap.add_argument("--tol", type=float, default=0.02,
                    help="relative tolerance (default 2%%)")
    args = ap.parse_args()
    only = set(args.only.split(",")) if args.only else None
    res = run_matrix(only)
    if args.save:
        json.dump(res, open(args.save, "w"), indent=1, sort_keys=True)
        print("baseline saved:", args.save, "(%d configs)" % len(res))
        return 0
    base = json.load(open(args.check))
    fails = []
    total = 0
    for name, metrics in res.items():
        b = base.get(name)
        if b is None:
            print("  %-12s NEW (not in baseline)" % name)
            continue
        for k, v in metrics.items():
            if k not in b:
                continue
            total += 1
            bv = b[k]
            tol = args.tol * max(abs(bv), 1e-3)
            higher = any(t in k for t in HIGHER)
            if (higher and v < bv - tol) or (not higher and v > bv + tol):
                fails.append((name, k, bv, v))
    for name, k, bv, v in fails:
        print("  REGRESSION %-12s %-26s %.5f -> %.5f" % (name, k, bv, v))
    print("check: %d metrics, %d regressions (tol %.1f%%)"
          % (total, len(fails), 100.0 * args.tol))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
