# Phase 8 — WOLF 2.5D CINEMATIC IMAGE ENGINE

## Scope
Adds a dedicated, fully offline compositor for the three depth planes already produced by Phase 7. The new engine applies deterministic camera choreography independently at background, midground, and foreground depth, with bounded parallax, subtle breathing, restrained rotation, atmospheric color grading, threat-accent glow, and vignette.

## Integration
The uploaded-image renderer delegates its analyzed-layer composition to `Wolf25DCinematicImageEngine`. Existing image analysis, confidence gates, source bitmap ownership, procedural fallback state machine, and diagnostics remain in place. If layer rendering returns a safe frame or throws, Phase 6 routing still switches the shot to the procedural renderer.

## Tests
- Deterministic camera poses and distinct depth parallax.
- Bounded rotation and breathing.
- Clamped progress and finite camera values for every camera move.
- Shot identity changes handheld choreography.

## Explicit limits
This is 2.5D compositing of three heuristic depth planes, not true 3D, semantic object segmentation, independently rigged characters, or hidden-area reconstruction. Parallax may expose edge gaps or repeated clean-plate estimates in difficult images. Color grading and atmospheric accents are stylized overlays, not physical lighting. No APK, emulator, device visual, thermal, or MP4 export test is performed in this phase.

## Compatibility
No prior phase files are deleted. The Phase 6 procedural fallback remains the recovery route. No network, model weights, new dependencies, paid service, or cloud component is introduced.
