# Thorax review round 1

The initial viewer is a pipeline shell, so the first install should show a yellow **Schematic / asset pending** card rather than a claimed anatomical mesh.

When processed assets arrive, verify against Netter’s and Gray’s:

1. In anterior, left lateral, posterior, and superior views, confirm the thoracic cage orientation and left/right labels.
2. Peel skin → muscle → organs → bone. No organ may float after a layer is hidden.
3. Isolate each lung; check lobation, hilum-facing medial surface, and cardiac notch on the left.
4. Check heart position: apex points anterior/inferior/left; base is posterior; great-vessel roots join without gaps.
5. Check trachea, main bronchi, esophagus, and descending aorta relations in the posterior mediastinum.
6. Report each finding as `anatomy`, `alignment`, `mesh`, or `cosmetic`, plus structure ID and view.
7. Check the Sources screen lists an attribution and processing note for every non-schematic object.

The processing log must record source hash, orientation transform, smoothing iterations, triangle counts at every LOD, and any known simplification.
