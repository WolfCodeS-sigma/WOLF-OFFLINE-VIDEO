# WOLF OFFLINE VIDEO

Standalone Android application for local horror-film production. This repository contains the offline production path only; it does not contain the WOLF online engines, WebView workspace, API hub, or cloud services.

## Product direction

WOLF OFFLINE VIDEO builds a film locally from story text, optional user-selected images, procedural artwork, layered motion, horror typography, procedural sound effects, and an optional approved voice recording. It does not require a large video-generation model.

## Roadmap

The full staged plan, including the new 2.5D cinematic image engine, local TXT horror knowledge library, offline story AI, long-story word-count/structure controls, and originality review, is documented in [WOLF OFFLINE VIDEO Roadmap](docs/WOLF_OFFLINE_VIDEO_ROADMAP.md). Roadmap items are planned work, not claims of completed implementation.

## Current source layout

- `MainActivity.kt`: independent offline project and production interface.
- `WolfProjectStore.kt`: durable local project records with atomic file replacement.
- `WolfOfflineStoryAiEngine.kt` and `WolfOfflineStoryAiActivity.kt`: local LiteRT-LM story planning, chapter-by-chapter generation, resumable checkpoints, and explicit final approval.
- `WolfHorrorKnowledgeLibrary.kt` and `WolfHorrorKnowledgeLibraryActivity.kt`: private on-device TXT library, categorized/foldered/tagged entries, per-entry local search indexes, source metadata, duplicate detection, original-text preservation, and reference opt-out.
- `StoryDirectorEngine.kt` and `OfflineSupportEngines.kt`: deterministic story segmentation, shot planning, pacing, and local visual helpers.
- `WolfSceneArtEngine.kt`, `WolfSceneAssetStore.kt`, and `WolfProceduralMotionEngine.kt`: scene artwork, optional image slots, procedural character/environment motion, fog, particles, rain, spectral silhouettes, and visual effects.
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

The continuous-integration workflow is intentionally limited to Android lint and local JVM unit tests. It does not build an APK, start an emulator, or run instrumentation tests. Device/runtime verification is a separate final stage and must only be performed after the offline product has been completed and explicitly approved for that stage.

## Privacy and connectivity

Project records, narration recordings, horror knowledge TXT entries, their indexes, rendering, and exported media are stored locally. The offline production path does not require an account, API key, paid service, remote controller, or cloud backup. User-selected image URIs are retained as local document references.

## Output

Rendered videos are exported to the device's Movies/WOLF CODE S media collection when the platform media provider is available.
