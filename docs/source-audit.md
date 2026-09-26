# Phase 1 — source audit and licensing gate

**Decision date:** 2026-09-26  
**First region:** **thorax (thoracic cage, lungs, heart, mediastinum)**. It proves hierarchy, layer peeling, bilateral organs, opaque/transparent materials, and a high-value relationship review while avoiding the known weak first-pass areas of peripheral nerves and small vessels.

This is a licensing decision record, not a claim that all public anatomy meshes are medically accurate. Each downloaded object must retain its source URL, version/hash, license text, anatomy ID, and processing log in its manifest before it can ship.

## Coverage matrix

| Source | Skeletal | Muscle | Solid organs | Thoracic viscera | Arteries / veins | Peripheral nerves | Primary format / use | App Store status |
|---|---:|---:|---:|---:|---:|---:|---|---|
| BodyParts3D | Strong | Good | Good | Good | Moderate | Weak | OBJ; broad visible anatomy baseline | **Conditional clear** — CC BY-SA 2.1 Japan attribution/share-alike obligations must travel with derivatives. |
| Z-Anatomy | Strong | Strong | Good | Good | Moderate | Moderate | Blender/OBJ-style editable assets; useful detail supplement | **Conditional clear** — CC BY-SA 4.0 requires attribution, license notice, change notice, and share-alike for adapted assets. |
| TotalSegmentator | Strong | Limited | Strong | Good | Strong (large vessels) | Absent | NIfTI segmentations; build mesh with marching cubes | **Not a shipping source until per-release dataset/data license is verified.** Its model/code terms do not automatically clear every imaging datum for redistribution. |
| Open Anatomy / SPL atlases | Good | Variable | Moderate | Variable | Weak | MR/CT label maps, segmentations and scene data | **Conditional, item-by-item.** SPL is a platform; each atlas has distinct provenance/license. Use only assets explicitly cleared for redistribution. |
| Visible Human Project | Strong | Moderate | Good | Good | Variable | Weak | High-resolution image volumes/derived segmentation | **Do not ship or redistribute** without a written redistribution grant from NLM. Keep out of the automated release path. |
| Terminologia Anatomica / FMA | Naming only | Naming only | Naming only | Naming only | Naming only | Naming only | Terminology/ontology, not meshes | **Reference-only.** Store stable IDs and names; reproduce only material permitted by the terminology’s terms. |

## Clearance and attribution policy

1. **Approved starting geometry:** use only a specifically pinned BodyParts3D or Z-Anatomy object whose downloadable license file is captured in `Sources/`. Every in-app pack must show source, creator, license, URL, and modifications.
2. **Copyleft consequence:** CC BY-SA meshes and their adapted exports (including decimated USDZ) are treated as share-alike derivatives. The Sources screen exposes the applicable license and an export/link to the matching source package. This is compatible with paid App Store distribution in principle, but legal review must confirm the chosen attribution presentation before release.
3. **Excluded:** proprietary Complete Anatomy and Visible Body; Visible Human data without redistribution permission; any NC, research-only, no-redistribution, or unknown provenance asset. TotalSegmentator remains an R&D segmentation input only until a manifest records a redistributable scan/license.
4. **Terminology:** use FMA IDs where available; map TA names as controlled display terms. The app stores identifiers, not copied atlas labels/descriptions.

## Known gaps / honest display policy

- Peripheral nerves, autonomic plexuses, small coronary/bronchial vessels, lymphatics, fascial planes, and thin membranes are not validated in the broad sources.
- CT/MR slice thickness makes small structures unreliable; a missing or coarse mesh is never silently replaced by a realistic-looking tube.
- Fine structures enter only a dedicated **Detail / schematic** pack with an on-screen schematic badge and an explicit limitation.
- No asset is clinical guidance; anatomy review is required before a pack moves from `review` to `approved`.

## Source links to preserve during intake

- BodyParts3D: https://lifesciencedb.jp/bp3d/
- Z-Anatomy: https://z-anatomy.com/
- TotalSegmentator: https://github.com/wasserth/TotalSegmentator
- Open Anatomy: https://www.openanatomy.org/
- NLM Visible Human Project: https://www.nlm.nih.gov/research/visible/
- FMA: https://bioportal.bioontology.org/ontologies/FMA

Before public release, have counsel verify the exact downloaded license text and attribution string. This gate is deliberately conservative because licenses and dataset versions can change.
