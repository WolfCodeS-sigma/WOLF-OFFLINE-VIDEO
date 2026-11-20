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
- The procedural renderer clears and redraws the frame, so a partially drawn image frame is not left under the fallback scene.
- Fallback reporting is best-effort: exceptions from the optional diagnostics callback are contained and cannot interrupt image-to-procedural routing.
- Runtime fallback events are recorded through `WolfDiagnostics` when diagnostics storage is available.
- The caller retains ownership of the decoded source bitmap. The prepared shot releases only its generated segmentation layers; the encoder releases the source bitmap in its existing `finally` lifecycle.

## No-image verification
- JVM test `missingUploadedImageSelectsProceduralBackendAndBuildsACompletePlan` invokes the production pipeline preparation API with a null image and asserts that the procedural backend is selected, a complete scene plan is produced for the requested scene, and the fallback reason is recorded.
- JVM test `missingImageStillSelectsProceduralBackendWhenDiagnosticsCallbackFails` verifies that an unavailable diagnostics callback cannot interrupt the no-image route.
- Additional tests verify that failed/unusable analysis selects the procedural route and that usable partial depth layers retain the uploaded-image route.
- CI runs Android lint and JVM unit tests. No APK, emulator, or physical-device test is performed.

## Continuity boundary
- The no-image and failed-analysis route no longer depends on the uploaded-image renderer.
- If the procedural renderer itself or the underlying video encoder fails, this routing layer cannot guarantee uninterrupted export; those are separate failure domains handled by the existing export recovery path.
- This phase adds no network access, external models, paid services, or dependencies.
