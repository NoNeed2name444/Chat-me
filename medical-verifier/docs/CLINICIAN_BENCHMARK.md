# Clinician benchmark and calibration milestone

The verifier must be evaluated against cases whose ground truth is established
by qualified clinical reviewers before confidence can be treated as calibrated.

## Dataset shape

Each benchmark case should contain:
- stable case ID
- exact claim text
- ground-truth verdict
- clinician-reviewed flag
- risk class
- source IDs/evidence IDs
- population
- source era
- optional expected support probability

## Splits

Do not randomly split near-duplicates across train/calibration/test.

Use:
- development set
- calibration set
- locked holdout test set
- adversarial holdout
- temporal holdout
- population/subgroup holdouts

## Required measurements

At minimum:
- sensitivity
- specificity
- PPV
- NPV
- abstention rate
- Brier score
- expected calibration error
- critical-error rate

## Calibration

Use the calibration split only to learn a mapping from raw verifier scores to
probabilities. Never calibrate on the locked test set.

The included IsotonicCalibrator is a pure-Python implementation of the
pool-adjacent-violators algorithm.

## Release gate

A new verifier version should fail release if:
- critical-error rate worsens;
- citation correctness worsens;
- subgroup performance crosses a pre-defined floor;
- calibration error materially worsens;
- abstention drops while unsafe false-positive support rises.

Thresholds must be set by the clinical governance team rather than by the
model developer alone.

## Important

A benchmark can demonstrate measured performance for the tested populations and
claim types. It cannot prove "near-100% accuracy" in medicine.
