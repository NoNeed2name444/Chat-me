# Nemesis Round 12 — Provenance Bypass

## Attack surface

This round targeted the v1.8 additions for benchmark and evidence provenance.

### Attack A: stale or forged manifest digest
Input: a valid snapshot whose manifest hash was replaced with an arbitrary 64-character SHA-256 value.

Expected control: reject the snapshot with a deterministic manifest-hash mismatch.

### Attack B: duplicate case-ID collision
Input: two distinct records sharing the same benchmark case ID.

Expected control: flag the duplicate case ID before evaluation.

### Attack C: train/test provenance overlap
Input: a test case sharing source family, study family, and canonical source ID with training data.

Expected control: surface all explicit overlap findings; optional enforcement must reject the split.

### Attack D: inline split bypass
Input: train/test membership encoded only inside case records rather than separate arrays.

Expected control: optional split enforcement must still inspect those inline split labels.

### Attack E: provenance-bound evidence bypass
Input: verification/evaluation evidence omits source family, canonical source ID, source snapshot hash, or passage hash.

Expected control: provenance-bound mode must not treat the evidence as verification-ready.

### Attack F: temporal date substitution
Input: a claim dated 2026-09-28 against evidence dated 2026-09-27.

Expected control: return uncertainty for the date mismatch rather than treating the statements as temporally equivalent.

### Attack G: entity alias overreach
Input: explicit acetaminophen/paracetamol alias versus a near-spelling mutation.

Expected control: the configured alias pair may match; fuzzy or near-spelling similarity must not.

## Result

The intended v1.8 architecture is fail-closed for these explicit deterministic attack classes.

This is an engineering adversarial assessment, not evidence of clinical validity,
clinical accuracy, calibration, or regulatory compliance.
