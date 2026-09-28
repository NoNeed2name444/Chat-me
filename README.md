# Anatomy Atlas

Native SwiftUI + RealityKit anatomy-study app, beginning with a **thorax** region pack. The initial build is a reviewable pipeline and viewer shell: it intentionally does not present procedural geometry as anatomical fact.

## Open on a Mac build worker

1. Install XcodeGen (`brew install xcodegen`) and run `xcodegen generate`.
2. Open `AnatomyAtlas.xcodeproj`, select an iPhone simulator/device, and build.
3. Install the thorax pack from the Packs tab. Until approved scan-derived USDZ assets are downloaded and processed, the viewer displays an explicit schematic placeholder.

## Repository layout

- `docs/source-audit.md` — coverage and App Store licensing decision record.
- `docs/thorax-review-checklist.md` — reviewer’s first build checklist.
- `pipeline/` — repeatable, headless asset processing entrypoints.
- `AnatomyAtlas/` — iOS application source and the shipped manifest.

Large approved source archives, GLB/OBJ intermediates, USDZ files and preview renders belong in Git LFS; see `.gitattributes`.

## Version 1 visual render

![Thorax explorer version 1 render](docs/screenshots/thorax-explorer-v1.png)

The render documents the intended iPhone Explore screen for this schematic-review build. It is a generated UI layout preview, not a device capture; the native RealityKit scene still needs cloud-macOS compilation and on-device review.
