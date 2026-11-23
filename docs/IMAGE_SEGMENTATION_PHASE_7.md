# Phase 7 — Lightweight Offline Image Segmentation Improvements

## Scope
Phase 7 improves the existing uploaded-image depth estimator without adding a neural model, network call, paid service, or dependency. The current segmentation remains a deterministic heuristic; it is not semantic object recognition.

## Changes
- Replace the single median-border distance threshold with a robust border model using bounded border sampling, per-channel median color, and upper-percentile border variation to adapt the foreground threshold.
- Bound working analysis to at most 768 pixels on the longest side and 262,144 pixels total, whichever is smaller.
- Treat transparent/near-transparent pixels as non-foreground so transparent holes do not become invented subjects.
- Use 8-connected border flood fill and 8-connected component grouping to reduce diagonal pinholes and fragmented masks.
- Keep the largest coherent foreground component in the primary midground plane; retain sufficiently large disconnected components in the secondary foreground plane; discard sub-threshold isolated components entirely, and reject frames without a sufficiently large coherent subject.
- Preserve conservative confidence gating. Ambiguous images remain unusable for depth layers and continue through the existing Phase 6 routing/fallback behavior.
- Keep source bitmap ownership with the caller. Temporary analysis bitmaps are recycled in a finally block, and partially allocated output layers are recycled if construction fails.

## Performance and compatibility
- No new dependencies, model weights, network access, or persistent background work.
- Bounded border sampling caps statistical work; analysis resolution is capped by both dimensions and total pixels.
- The existing `Layers` and `MaskResult` contracts and `segment(source)` entry point remain unchanged.
- The Phase 6 `WolfSceneRenderPipeline` routing contract is not modified. It still chooses the procedural backend when segmentation is unavailable or below the accepted confidence threshold.
- This remains a color/border heuristic. Busy borders, similar foreground/background colors, overlapping subjects, hair/fine detail, and complex transparency can still be ambiguous. It cannot identify arbitrary semantic objects or reconstruct hidden pixels.

## Tests added
- Mild background gradient plus sparse border clutter while retaining a high-contrast subject.
- Transparent pixels enclosed inside a foreground region are not marked as foreground.
- Tiny disconnected noise does not become an independent foreground plane.
- Frames where foreground would consume almost the whole image are rejected conservatively.

Existing tests for high-contrast separation, disconnected depth planes, uniform-image fallback, determinism, and failure signaling remain in place.

## Verification boundary
The CI workflow is limited to Android lint and JVM unit tests. Those tests exercise the pure mask-analysis logic, not Android bitmap rasterization on a physical device, full MP4 export, or memory/latency on target phones. No APK, emulator, instrumentation test, or physical-device test is performed in this phase.
