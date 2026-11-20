# WOLF Procedural Scene Production — Phase 2

## Goal

Make the local procedural renderer the first-party visual production path whenever a project has no supplied reference images. The scene must remain producible without image downloads, cloud rendering, external models, or network access.

## Integrated frame pipeline

For every image-free shot, `WolfProceduralSceneProductionEngine` now:

1. Validates and plans the scene from its story caption and stable scene index.
2. Classifies a local horror environment (forest, corridor, window, door, cemetery, room, or haunted house) using deterministic Arabic/English text cues.
3. Creates one of three distinct bounded shot compositions (establishing, medium, close).
4. Draws the local layered scene artwork through `WolfSceneArtEngine`.
5. Adds the existing depth-aware deterministic motion layers through `WolfProceduralMotionEngine`.
6. Returns to the shared production pipeline for horror typography, optional WOLF CODE S watermark, video encoding, optional local sound effects, audio muxing, timestamp normalization, output saving, and film QA.

User-supplied images remain optional. When a usable image exists, the existing image/depth path remains active and receives the same procedural motion overlays. This change does not remove or rewrite that path.

## Independence and boundaries

- The image-free visual path requires no external image, model, API, account, or network connection.
- All procedural plans and shot compositions are deterministic for the same inputs, supporting chapter retry and reproducible output.
- The artwork is locally drawn procedural illustration, not photorealistic AI image generation.
- Text classification is heuristic, not semantic AI understanding. Unrecognized scenes use the haunted-house environment.
- The current three-shot system creates distinct compositions from one procedural scene; it does not claim to create three independent photorealistic source images.
- The existing optional narration and local sound-effects stages remain separate from visual scene creation.

## Verification scope

JVM tests cover bilingual environment classification, deterministic scene plans, three distinct bounded shot compositions, invalid shot clamping, and invalid plan inputs. Android lint and JVM unit tests are the intended CI checks. No APK, emulator, instrumentation, or device test is included in this phase.
