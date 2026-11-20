# Phase 4 — Visual and Motion Quality Review

## Scope
This review targets the procedural scene production path introduced in Phase 2 and extended by Phase 3. It does not claim photorealistic output or device-level visual certification.

## Findings and fixes
- **Virtual-camera alignment:** The base artwork used each shot's zoom and focus transform, but depth-aware effects and independently moving object layers were drawn after that transform had been restored. This could make curtains, doors, shadows, particles and atmosphere drift relative to the base composition in close shots. The depth-aware pass now renders inside the same saved camera transform as the base artwork.
- **Per-frame planning overhead:** Procedural object-layer plans were being rebuilt for every rendered frame. Scene planning now creates and retains the immutable object-layer list once per scene and passes it to the frame renderer.
- **Per-frame Paint allocation:** The object-layer renderer now reuses its Paint instance across frames, guarded by synchronization because the instance is shared.

## Automated verification
- Added a JVM assertion that scene plans retain the same deterministic object-layer plan produced by the layer planner.
- CI runs Android lint and JVM unit tests.
- No APK, emulator, instrumentation test, screenshot comparison, frame-time benchmark, or physical-device playback test is part of this phase.

## Remaining visual verification
A human visual pass on the target Android device is still required to judge composition, actual frame pacing, thermal behavior, and whether any particular scene has unwanted overlaps. CI success is not evidence of those properties.
