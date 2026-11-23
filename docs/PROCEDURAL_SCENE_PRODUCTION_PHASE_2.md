# WOLF Procedural Scene Production — Phase 2 baseline

## Current role

When no user image is selected, the application uses a deterministic, fully local environment-only renderer. StoryDirector still owns scene segmentation, shot timing, shot frame budgets, camera/composition intent, and lighting intent.

The procedural planner classifies the scene caption into a coarse environment label (forest, corridor, window, door, cemetery, room, or haunted house). The current artwork implementation is intentionally atmospheric rather than a full object renderer.

## Active frame path

1. StoryDirector creates the scene and three-shot plan.
2. `WolfProceduralSceneProductionEngine` validates the caption and scene index, classifies the environment, and derives environmental feature cues.
3. `WolfSceneArtEngine` paints a dark atmospheric background, drifting haze, and vignette.
4. `WolfProceduralMotionEngine` adds environmental effects such as fog, particles, wind-driven branches, rain, environmental pulses, and restrained signal distortion.
5. The shared production pipeline adds typography/watermark, encodes the video, mixes audio, and performs output inspection.

## Explicit visual limits

- The environment label is a planning cue; it does not mean a photorealistic room, corridor, house, door, or cemetery model was generated.
- The current base artwork is atmospheric and does not draw people, ghosts, creatures, furniture, buildings, doors, or props as geometric placeholders.
- Environmental branches and weather are not evidence of subject-level character/object generation.
- The three shot compositions provide deterministic framing changes for image-free environments; they are not three independent photorealistic source images.
- If an important subject cannot be represented by the active renderer, the application must not claim that it was generated.

## User-image path

A selected user image remains the source for its shot. Because the current border/color segmenter is not semantic AI, depth layers are disabled until semantic validation exists. The original image is rendered as a still. An image decode or actual rendering failure stops export rather than silently switching to an unrelated procedural scene.

## Verification boundary

JVM tests cover planning, environment cue classification, shot composition bounds, backend dispatch, and deterministic motion helpers. CI lint/unit-test success is not a physical-device visual test.
