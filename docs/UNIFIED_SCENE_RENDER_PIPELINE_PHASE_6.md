# Phase 6 — Unified Scene Render Pipeline and Procedural Fallback

## Unified entry point
- `WolfSceneRenderPipeline` is the single routing interface used by `OfflineProductionEngine` for every shot.
- The encoder no longer contains separate image/procedural rendering decisions.
- The pipeline prepares a shot once, selects a backend, and reuses that decision for every frame in the shot.

## Backend selection
- A missing image, invalid/recycled image, decode failure, segmentation exception, or unusable depth-layer result selects `PROCEDURAL`.
- A successfully analyzed image with usable depth layers selects `UPLOADED_IMAGE`.
- The procedural scene plan is created lazily for valid uploaded-image shots. It is created immediately only when the image is absent or its analysis is unusable.
- A partial but usable depth-layer result remains eligible for the uploaded-image renderer.

## Runtime fallback
- If the uploaded-image renderer returns `SAFE_FRAME` or throws while rendering, the same frame is redrawn through `WolfProceduralSceneProductionEngine`.
- The shot switches permanently to the procedural backend after that failure, so later frames do not repeatedly invoke the failing image renderer.
- The procedural renderer clears and redraws the frame, so a partially drawn image frame is not left under the fallback scene.
- Fallback reporting is best-effort: exceptions from the optional diagnostics callback are contained and cannot interrupt image-to-procedural routing.
- Runtime fallback events are recorded through `WolfDiagnostics` when diagnostics storage is available.
- The caller retains ownership of the decoded source bitmap. The prepared shot releases only its generated segmentation layers; the encoder releases the source bitmap in its existing `finally` lifecycle.

## No-image and recovery verification
- JVM test `missingUploadedImageSelectsAndRendersThroughProceduralBackend` invokes the production preparation API with a null image, then exercises the same backend-dispatch state machine used by the Canvas render entry point. It asserts the procedural callback runs once and the uploaded-image callback is never invoked.
- JVM test `safeImageFrameSwitchesToProceduralAndStaysThereForTheRestOfTheShot` verifies that a safe-frame result switches the shot to the procedural backend and that subsequent frames bypass the uploaded-image renderer.
- JVM test `missingImageStillSelectsProceduralBackendWhenDiagnosticsCallbackFails` verifies that an unavailable diagnostics callback cannot interrupt the no-image route.
- Additional tests verify failed/unusable analysis routing and acceptance of usable partial depth layers.
- CI runs Android lint and JVM unit tests. The dispatch tests do not execute Android Canvas rasterization, encode a full MP4, or replace device testing.

## Continuity boundary
- The no-image and failed-analysis route no longer depends on the uploaded-image renderer.
- If the procedural renderer itself or the underlying video encoder fails, this routing layer cannot guarantee uninterrupted export; those are separate failure domains handled by the existing export recovery path.
- This phase adds no network access, external models, paid services, or dependencies.
