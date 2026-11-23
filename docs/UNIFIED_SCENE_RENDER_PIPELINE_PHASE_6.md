# Phase 6 — Unified Scene Render Pipeline

## Production ownership

`OfflineProductionEngine` uses `WolfSceneRenderPipeline` as the single backend dispatch boundary for each shot. StoryDirector remains the owner of shot timing and visual intent. The pipeline does not rewrite the story plan.

## Backend selection

- No user image selected: choose the environment-only procedural backend.
- Valid user image selected: keep the uploaded-image backend even when depth analysis is unavailable.
- The current heuristic segmenter is not semantic AI. It is not allowed to enable parallax; `semanticValidationPassed` defaults to false and `safeForParallax` remains false.
- A selected image that cannot be decoded is an explicit export error, not permission to substitute another scene.

## Runtime failure policy

- A decoded user image without validated depth layers is drawn as the original aspect-matched still, with no camera zoom, pan, parallax, or procedural subject overlay.
- A safe-frame result or actual failure to draw the selected image aborts export with a clear error. The shot does not switch to a generated scene.
- The no-image procedural route draws only the environment atmosphere and environmental motion. It does not generate people, ghosts, creatures, buildings, doors, furniture, or props from primitive geometry.
- Fallback diagnostics are best-effort and must not change the selected visual source.
- The caller retains ownership of the decoded source bitmap; prepared segmentation layers are released by the shot lifecycle.

## Regression coverage

JVM tests cover no-image procedural dispatch, retention of a decoded user image when depth analysis is unavailable, and export failure instead of procedural substitution after a selected-image render failure. Image depth tests assert that the heuristic segmenter does not claim semantic readiness.

## Verification boundary

The workflow checks the offline contract, Android lint, and JVM unit tests. It does not prove successful export or visual quality on a physical Android device.
