# WOLF OFFLINE VIDEO

Standalone Android application for local horror-film production. This repository contains the offline production path only; it does not contain the WOLF online engines, WebView workspace, API hub, or cloud services.

## Product direction

WOLF OFFLINE VIDEO builds a film locally from story text, optional user-selected images, procedural artwork, layered motion, horror typography, procedural sound effects, and an optional approved voice recording. It does not require a large video-generation model.

## Current source layout

- `MainActivity.kt`: independent offline project and production interface.
- `WolfProjectStore.kt`: durable local project records with atomic file replacement.
- `StoryDirectorEngine.kt` and `OfflineSupportEngines.kt`: deterministic story segmentation, shot planning, pacing, and local visual helpers.
- `WolfSceneArtEngine.kt`, `WolfSceneAssetStore.kt`, and `WolfProceduralMotionEngine.kt`: scene artwork, project-scoped user image slots, procedural motion, and atmospheric effects.
- `docs/WOLF_PROCEDURAL_GENERATION_LIBRARY_DESIGN.md`: Stage 09 architecture, generator-only Stage 08 registry resolution, deterministic canonical input/seed contracts, bounded resource execution, statically linked in-app generator bindings, candidate-only camera-motion profiles gated by the existing shot and motion planners, with safe still-image fallback preserved, governance, safety boundaries, and Stage 10 verification handoff. This is a design baseline, not a claim that new generator modules are already implemented.
- WolfProceduralGenerationLibrary.kt and the statically linked atmosphere, weather, and lighting generators: Stage 10 approved-asset cooperation, deterministic renderer-neutral effect descriptors, strict registry/resource gating, exact upstream dependency pins, output-kind validation, execution-overrun checks, live WolfResourceGuard preflight, and no-effect fallback. Production resource ceilings remain PENDING until actual measurements are reviewed; finite ceilings in automated tests are TEST_ONLY. See docs/WOLF_STAGE_10_GENERATOR_ASSET_COOPERATION.md for scope and test evidence.
- `WolfAssetRegistry.kt` and `app/src/main/assets/wolf_asset_registry.json`: application-bundled cinematic asset registry, SHA-256/file integrity checks, rights/realism/scope/runtime validation, and fail-closed Quality Gate. The initial registry is intentionally empty until real assets pass review.
- `OfflineProductionEngine.kt`: local frame rendering, H.264 MP4 encoding, audio integration, long-film chapter handling, checkpoint support, recovery paths, and export quality inspection.
- `WolfStoryVoiceEngine.kt`, `WolfAudioMixer.kt`, `WolfAudioTranscoder.kt`, and `WolfSoundFxEngine.kt`: local narration recording, approval state, audio processing, and procedural sound effects.
- `WolfLongFilmEngine.kt`: chapter planning and resumable chapter checkpoints.
- `WolfDiagnostics.kt`, `WolfResourceGuard.kt`, `WolfMemoryManager.kt`, and `WolfFilmQualityInspector.kt`: diagnostics, resource protection, memory safeguards, and output inspection.
- `app/src/test`: offline unit tests.

## Build requirements

- Android SDK 35
- JDK 17
- Gradle 8.13 or a compatible Gradle 8.x release
- Android device API 26 or later for the application runtime

Open the repository as an Android Gradle project in a compatible IDE, or use a Gradle installation to run the verification tasks.

- `docs/WOLF_STAGE_11_ENGINE_COMPATIBILITY_POC.md` and `engine-pocs/`: Stage 11 compatibility evidence. The projects under `engine-pocs/` remain isolated. Godot is the preferred Cocos fallback candidate after its Android sample-scene render proof; Cocos remains blocked pending a licensed Creator-generated Android export and rendered-frame proof.
- `docs/WOLF_STAGE_12_FINAL_ENGINE_ROLES_AR.md`: final role allocation for WOLF Motion Hub, Godot, Filament, libGDX, KorGE, Lottie, Cocos, SceneView, and Blender, including proof levels and production-integration gates.
- `HorrorVideoExporter.kt`: separate Filament prototype path, launched only from the Debug-only UI control and not yet connected to the ordinary `OfflineProductionEngine` film pipeline. Its Filament dependencies are currently declared in the main app Gradle configuration; see the Stage 12 document for the Release packaging caveat.

## Verification policy

The continuous-integration workflow builds the Debug APK, runs Android lint and JVM unit tests, then runs the Android 12 emulator regression suite for media export, audio, checkpoints, and the bundled asset registry. Testing on the physical Realme 7 remains a separate final stage and must only be performed after the offline product has been completed and explicitly approved for that stage.

## Privacy and connectivity

Project records, narration recordings, rendering, and exported media are stored locally. The offline production path does not require an account, API key, paid service, remote controller, or cloud backup. User-selected image URIs are retained as local document references.

## Output

Rendered videos are exported to the device's Movies/WOLF CODE S media collection when the platform media provider is available.

- `asset-intake/` and `tools/asset-intake/`: executable, fail-closed batch intake. Two CC0 environment HDRIs are acquired as candidates in WOLF-ASSET-BATCH-001; neither is reviewed/approved for production or included in the APK. Batch 000-baseline remains empty. See `asset-intake/README.md`.
