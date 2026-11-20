# Phase 5 — Isolated Uploaded Image Engine

## Architecture boundary
- Added `WolfUploadedImageEngine` as a dedicated offline component for user-supplied still images.
- It does not call the procedural scene planner, procedural artwork renderer, or story/environment classifier.
- The procedural no-image branch remains the existing `WolfProceduralSceneProductionEngine` path.
- Uploaded-image analysis and composition are best-effort and are contained behind the image engine API.

## Processing
1. The caller decodes the selected image and retains ownership of the original bitmap.
2. The image engine attempts the existing deterministic border-background/depth-plane analysis.
3. Depth layers are accepted only when separation succeeds and confidence is at least 0.30.
4. A weak or failed analysis discards only the generated layer bitmaps and keeps the original image intact.
5. The image engine renders the three image depth planes with independent depth parallax when available; otherwise it renders the original image with a local virtual-camera move.
6. Composition exceptions trigger a best-effort original-image redraw, then a safe black frame if even that drawing fails.
7. Prepared layer bitmaps are released after their shot; the caller-owned source bitmap is released by the existing caller lifecycle.

## Isolation and safety
- No network, external model, image download, or new dependency.
- No image-processing failure is allowed to propagate from `prepare` or `render` into the procedural renderer.
- The engine has no reference to procedural engine classes.
- The existing procedural engine remains the fallback for scenes with no decoded user image; it is not used to process uploaded images.
- Existing atmosphere/typography/watermark and export stages remain outside this isolated image component.

## Verification boundary and limitations
- Added JVM coverage for the strict layer acceptance threshold.
- Existing segmenter tests cover deterministic masks, border-background separation, disconnected components, uniform-image fallback, and failure signaling.
- This remains heuristic depth-plane segmentation, not semantic AI instance segmentation. It cannot reliably identify every object in arbitrary photos, separate overlapping objects, or reconstruct hidden pixels.
- CI lint and JVM tests are required. No APK, emulator, screenshot, or physical-device test is performed in this phase.
