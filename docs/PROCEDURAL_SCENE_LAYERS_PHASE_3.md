# Phase 3 — Historical procedural object-layer prototype

> **Status in the unified core:** archived and excluded from the production render path.

The earlier Phase 3 prototype described procedural chairs, doors, curtains, hanging lights, shadows, and independently animated object layers. Those geometric layers were removed from the unified production tree because they could create stylized placeholder objects or apparent characters that were not backed by approved visual assets.

The production renderer does not call the old `WolfSceneLayerSystem`. The source and its test were removed from the integration branch.

## Current production rule

- No user image: use the environment-only procedural renderer and environmental effects.
- User image without semantic depth validation: preserve the selected image as a still.
- Do not infer object identity, hidden geometry, or independent object motion from color-distance segmentation.
- Do not draw procedural people, ghosts, creatures, doors, furniture, or props over a user image.

Any future object-layer renderer requires a separately reviewed asset-backed implementation, explicit user-visible quality expectations, semantic validation, bounded resource use, and regression tests. This historical document is retained to explain why the former prototype is not part of production.
