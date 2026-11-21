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

## Verification policy

The continuous-integration workflow builds the Debug APK, runs Android lint and JVM unit tests, then runs the Android 12 emulator regression suite for media export, audio, checkpoints, and the bundled asset registry. Testing on the physical Realme 7 remains a separate final stage and must only be performed after the offline product has been completed and explicitly approved for that stage.

## Privacy and connectivity

Project records, narration recordings, rendering, and exported media are stored locally. The offline production path does not require an account, API key, paid service, remote controller, or cloud backup. User-selected image URIs are retained as local document references.

## Output

Rendered videos are exported to the device's Movies/WOLF CODE S media collection when the platform media provider is available.
