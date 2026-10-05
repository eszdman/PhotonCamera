#!/bin/sh
# Scenario A/B matrix for the SR recovery bench: frame count x noise (ISO)
# x restoration gain cap. Prints one line per cell; every cell must PASS.
#
#   PY=/path/to/.venv/bin/python sh run_matrix.sh
set -e
PY="${PY:-./.venv/bin/python}"
for frames in 8 24 40; do
  for noise in 0.005 0.01 0.03; do
    for gain in 1.8 2.2 3.5; do
      line=$($PY sr_bench.py --frames "$frames" --noise "$noise" --gainmax "$gain" | tail -2 | tr "\n" " ")
      echo "frames=$frames noise=$noise gain=$gain -> $line"
    done
  done
done
