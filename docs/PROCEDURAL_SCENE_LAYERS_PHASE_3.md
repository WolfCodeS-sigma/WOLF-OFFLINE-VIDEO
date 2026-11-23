# Phase 3 — Procedural scene layers, depth and motion

## Scope

This phase extends the local procedural scene renderer. It does not replace the Phase 2 scene planner, artwork renderer, export pipeline, or the adopted baseline.

## Object layer model

`WolfSceneLayerSystem` creates deterministic scene-local element descriptors with:
- stable element IDs;
- explicit background, midground, or foreground depth;
- normalized scene anchors and bounded size;
- z-order within a depth plane;
- independent motion phase and amplitude.

The current procedural element set is deliberately conservative: chair, door, curtains, branches, hanging light, and environmental shadow. Environment selection comes from the Phase 2 scene plan. Story presence/reveal/chase cues may add a separate moving shadow. The renderer does not claim semantic recognition of arbitrary objects.

## Motion and composition

Each element receives its own deterministic pose (translation, rotation, scale, alpha). Motion is driven only by normalized frame progress, so retries and chapter re-renders remain reproducible. Depth planes also apply different scale and parallax strengths. The layer renderer restores Canvas state in a `finally` block per object.

The existing three-plane depth compositor now restores Canvas state on failure for both procedural planes and supplied-image planes. The existing Phase 2 pipeline remains the caller and retains typography/watermark ordering after scene motion.

## Verification

JVM tests cover environment-specific object plans, depth/z ordering, story-cue shadows, deterministic bounded poses, independent object phases, and invalid scene indices. CI runs Android lint and local JVM unit tests only.

## Explicit limitations

- These are authored procedural shapes, not photorealistic assets or AI instance segmentation.
- Objects are independent within the procedural layer set; arbitrary props mentioned in story text are not automatically discovered or reconstructed.
- A single generated scene does not contain hidden image data to inpaint.
- No APK, emulator, instrumentation, or physical-device test is performed in this phase.
