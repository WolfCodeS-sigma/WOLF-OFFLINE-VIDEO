# Phase 6 — Unified Scene Render Pipeline

## Production ownership

`StoryDirectorEngine` remains the owner of story interpretation, scene boundaries, shot timing and visual intent. It supplies the `SceneRenderContext` and `ShotPlan` consumed by the render path; the render pipeline does not rewrite the approved story plan.

The actual MP4 production route is:

```text
OfflineProductionEngine.render
  -> encode / encodeChaptered
     -> encode (MediaCodec input Surface + MediaMuxer)
        -> per shot: WolfSceneRenderPipeline.prepare
           -> per frame: WolfSceneRenderPipeline.renderPreparedShot
              -> no selected image: WolfProceduralSceneProductionEngine.render
              -> selected image: existing uploaded-image frame renderer
        -> MediaCodec drain
        -> MediaMuxer MP4 samples
```

`WolfSceneRenderPipeline` is the backend dispatch boundary used by the production encoder, not merely an isolated utility. A `PreparedShot` is prepared once for each shot and retained across that shot's frames. The selected backend is fixed for the shot and cannot be silently replaced after a rendering failure.

## Backend selection

- No user image is assigned to the current shot: prepare a deterministic environment plan and dispatch its frames to `WolfProceduralSceneProductionEngine`.
- A user image is assigned and decoded: keep the uploaded-image backend. The existing aspect-match/crop and image-frame renderer remain in use, preserving current output treatment.
- A selected image that cannot be decoded is an explicit export error before rendering. A selected-image frame that returns `SAFE_FRAME` also aborts export; it never falls through to procedural artwork.
- Depth-aware image composition remains gated by `safeForParallax` and semantic validation. With the current heuristic segmenter unavailable for semantic validation, the uploaded image remains a still; this change does not enable heuristic Parallax.
- The scene context and shot plan are carried into the procedural renderer so its environment treatment and camera framing use the same StoryDirector intent that previously reached the direct `OfflineProductionEngine` drawing branch.

## Runtime and ownership policy

- A decoded user image without validated depth layers remains the aspect-matched source still. No generated scene replaces it.
- The procedural route renders environment atmosphere only. It does not synthesize people, ghosts, creatures, buildings, doors, furniture or props from primitive geometry.
- `WolfProceduralMotionEngine` remains part of the selected rendering route: it runs after the procedural background is drawn, or as the shared image-route overlay. Planned lighting, typography and optional watermark remain downstream of backend selection.
- The caller owns the decoded source bitmap. The shot lifecycle releases only prepared segmentation layers, then recycles the decoded bitmap after all frames of the shot are encoded.
- Fallback diagnostics are best-effort and do not change the selected visual source.

## Regression and encoder integration coverage

JVM tests continue to cover dispatch selection, uploaded-image retention, and failure instead of switching a failed selected image to procedural art.

`WolfScenePipelineEncoderIntegrationTest` adds device-side coverage through the actual `OfflineProductionEngine.encode` method. On an Android emulator it exercises:
1. A shot with no uploaded image, asserting procedural backend selection for encoded frames.
2. A shot with a decoded user image, asserting uploaded-image backend selection for encoded frames.

Both tests inspect the resulting MP4 using `MediaExtractor` and verify the video track dimensions, encoded sample count, and increasing presentation timestamps. The Android 12 offline runtime workflow runs `connectedDebugAndroidTest` while airplane mode and Wi-Fi-off checks are already active, then continues its existing app launch smoke check.

## Verification boundary

The pull-request CI must pass the offline runtime contract, Android lint, JVM unit tests, Android instrumentation/encoder tests, and the existing Android 12 offline launch smoke test before this item can be considered verified.

These checks do not constitute a physical-phone test, signed release build, visual acceptance of every environment, or completion of Phase 6-C through 6-G. The current environment catalogue still does not include a dedicated beach/sea production environment; that work remains in 6-C. No old branch or historical PR is merged as part of 6-B.


## Stage 06-C — image-free environment completion

The no-image procedural renderer now distinguishes the outdoor locations already described by StoryDirector rather than routing all unknown captions to the haunted-house default. Added classifications cover BEACH, SEA, DESERT, ROAD, RURAL, MEDICAL_FACILITY, and EDUCATIONAL. Beach is resolved before generic sand/desert cues, and StoryDirector and the procedural classifier use aligned Arabic/English location cues.

Beach frames now contain separate sky, sea, curved shoreline, wet-sand transition, dry-sand region, foam edge, horizon mist, and deterministic wave bands. Open-sea frames contain a visible horizon, layered water surface, ripples, and localized horizon mist without adding a beach. Desert, forest, road, and rural captions receive distinct environment planes. Existing horror atmosphere, lighting, typography, and watermark remain downstream and are not replaced.

These renderers are deterministic Canvas-based 2D environmental illustrations; they are not photorealistic 3D environments or a substitute for a licensed cinematic asset library. No person, ghost, creature, furniture, door, building, or unlicensed external asset is generated. User-image rendering, semantic-depth gating, and Parallax behavior are unchanged.

Android instrumentation checks the actual procedural pipeline raster output for separate beach sky/sea/wet-shore/sand regions, an open-sea horizon/depth, and distinct desert/forest planes. A beach/no-image instrumentation case also runs through `OfflineProductionEngine.encode` and `MediaCodec/MediaMuxer`, asserting that every encoded frame remains on the procedural backend and that the resulting MP4 has valid video samples and monotonic presentation timestamps.
