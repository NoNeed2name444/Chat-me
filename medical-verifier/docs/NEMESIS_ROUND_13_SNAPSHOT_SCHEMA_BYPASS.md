# Nemesis Round 13 — Snapshot/schema bypass

## Attack A: unsorted manifest IDs
A payload presents the same case set in a different case order while keeping a sorted manifest.

Control: snapshot import rejects unsorted manifest IDs and normalizes case ordering only after schema checks.

## Attack B: missing digest
A payload omits the manifest snapshot hash entirely.

Control: import rejects missing or malformed manifest hashes instead of treating the hash as optional.

## Attack C: type confusion
A snapshot replaces the cases array or manifest case-ID array with an object or scalar.

Control: import rejects non-array structures deterministically.

## Attack D: custom alias bypass
A caller supplies an explicit empty alias map and attempts to inherit the global aliases accidentally.

Control: explicit alias configuration is authoritative; an empty map means no aliases.

## Attack E: substring temporal injection
A source contains words that merely contain temporal markers as substrings.

Control: temporal marker detection uses token boundaries rather than unrestricted substring matching.

## Result

The benchmark import boundary and normalization helpers now fail closed for these structural
bypass attempts. These checks remain engineering controls and do not establish clinical
validity, clinical accuracy, calibration, or regulatory compliance.
