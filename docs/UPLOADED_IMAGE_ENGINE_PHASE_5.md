# Phase 5 — Isolated Uploaded Image Engine

## Architecture boundary
- WolfUploadedImageEngine is a dedicated offline component for user-supplied still images.
- It does not call the procedural scene planner, procedural artwork renderer, or procedural motion engine.
- The no-image path remains the existing WolfProceduralSceneProductionEngine path.
- Story-aware camera choreography and image-only atmospheric composition stay inside the uploaded-image engine.

## Image preparation and partial segmentation
1. The caller decodes the selected image and retains ownership of the original bitmap.
2. The image engine attempts deterministic border-background/depth-plane analysis.
3. Usable separated planes are retained at confidence 0.12 or higher; the segmentation result must also explicitly report successful separation.
4. If no usable plane separation exists, generated layer bitmaps are discarded and the untouched original image remains available.
5. The original-image fallback still receives continuous virtual-camera movement and image-only atmosphere.
6. If only a subset of depth planes contains useful separated content, empty planes remain transparent and only available planes contribute parallax.
7. Composition exceptions trigger an original-image redraw, then a safe black frame if even that drawing fails.

## Story-aware camera and atmosphere
- Camera motion is selected from scene text and shot index: slow push, slow pull, lateral tracking, reveal, or restrained handheld drift.
- Movement is continuous across each shot and driven by normalized shot progress, so it respects shot duration.
- Story cues select independent visual atmosphere: drifting fog for forest/ghost/cemetery beats, dust for corridors/doors/mirrors, rain streaks for rain beats, and restrained motion streaks for chase beats.
- A restrained vignette and contextual red light pulse support selected reveal beats.
- No image fallback is frozen; even without segmentation, the original bitmap is animated by the virtual camera and atmosphere compositor.
- These effects are image-engine-local and do not call procedural rendering or alter the source bitmap.

## Resource and isolation safety
- No network, external model, image download, or new dependency.
- Analysis and rendering failures are contained behind the image engine API.
- Prepared layer bitmaps are released after their shot; the caller-owned source bitmap is released by the existing caller lifecycle.
- The existing procedural engine remains the fallback for scenes with no decoded user image; it is not used to process uploaded images.

## Verification boundary and limitations
- JVM tests cover segmentation acceptance, story-to-camera/atmosphere selection, determinism, and bounded motion-plan values.
- Existing segmenter tests cover deterministic masks, border-background separation, disconnected components, uniform-image fallback, and failure signaling.
- Segmentation remains a deterministic heuristic, not semantic AI instance segmentation. It cannot reliably identify every object in arbitrary photos, separate overlapping objects, or reconstruct hidden pixels.
- Atmospheric overlays are procedural approximations, not physically simulated fog/rain or object-aware lighting.
- CI lint and JVM tests are required. No APK, emulator, screenshot, or physical-device test is performed in this phase.
