# Phase 9 — WOLF Cinematic Motion Engine

## Scope
Adds a deterministic, offline character-and-prop animation pass for uploaded-image shots. The pass runs after the existing scene backend and before horror typography and the watermark.

## Motion channels
- A lightweight articulated figure/ghost rig with independent head/body and arm motion.
- Door-panel hinge rotation, curtain sway, and wind-driven branch movement.
- A restrained moving ground shadow.
- Caption cues select only the relevant overlays; ordinary captions do not receive invented props.

## Safety and continuity
- All motion is derived from normalized frame progress plus scene/shot identity; no wall clock, network, model weights, or added dependencies.
- Motion values are bounded and clamped. Canvas save/restore is paired with finally blocks.
- The pass is additive and isolated. It runs only while the selected scene backend remains UPLOADED_IMAGE. The existing procedural renderer remains responsible for procedural scenes and all established fallback behavior.
- A motion-pass failure returns false without throwing into the encoder; the already-rendered base frame remains available.

## Verification
JVM tests cover deterministic poses, bounded channels, progress clamping, cue selection, and no-op captions. CI runs Android lint and JVM unit tests only. No APK, emulator, or physical-device visual test is part of this phase.

## Known limits
Figures and props are stylized procedural overlays, not AI-generated actors or semantic object segmentation. Their screen placement is heuristic and cannot infer exact hidden geometry or occlusion from arbitrary photos.
