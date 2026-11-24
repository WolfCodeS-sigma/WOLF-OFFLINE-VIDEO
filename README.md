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
- `WolfStoryLengthStructureEngine.kt`: offline Arabic/English word counting, deterministic per-chapter word budgets, eight narrative beats, prompt guidance, and quantitative length/reading-time reports.
- `WolfStoryOriginalityReviewEngine.kt`: offline phrase-overlap review against permitted local references, source-version ledger, internal repeated-phrase checks, and a review gate before final story approval.
- `WolfHorrorKnowledgeLibrary.kt` and `WolfHorrorKnowledgeLibraryActivity.kt`: private on-device TXT library, categorized/foldered/tagged entries, per-entry local search indexes, source metadata, duplicate detection, original-text preservation, and reference opt-out.
- `StoryDirectorEngine.kt` and `OfflineSupportEngines.kt`: deterministic story segmentation, shot planning, pacing, and local visual helpers.
- `WolfSceneArtEngine.kt`, `WolfSceneAssetStore.kt`, and `WolfProceduralMotionEngine.kt`: scene artwork, optional image slots, procedural character/environment motion, fog, particles, rain, spectral silhouettes, and visual effects.
- `OfflineProductionEngine.kt`: local frame rendering, H.264 MP4 encoding, audio integration, long-film chapter handling, checkpoint support, recovery paths, and export quality inspection.
- `WolfStoryVoiceEngine.kt` and `WolfVoiceSegmentStore.kt`: approved-script-bound narration, independently saved recording parts, recovery and ordered AAC/M4A assembly.
- `WolfAudioMixer.kt`, `WolfAudioTranscoder.kt`, and `WolfSoundFxEngine.kt`: local narration cleanup, voice-aware effects ducking, AAC mixing, and deterministic procedural sound effects.
- `WolfCinematicAudioTimeline.kt` and `WolfCinematicIdentity.kt`: shared intro/narration/video timing and the approved WOLF CODE S cinematic identity.
- `WolfLongFilmEngine.kt`: chapter planning and resumable chapter checkpoints.
- `WolfFilmProductionCoordinator.kt`: Phase 14 orchestration for 12-scene/36-shot plans, shot-aligned five-minute chapters, resumable motion offsets, and ordered film assembly.
- `WolfDiagnostics.kt`, `WolfResourceGuard.kt`, `WolfMemoryManager.kt`, and `WolfFilmQualityInspector.kt`: diagnostics, resource protection, memory safeguards, and output inspection.
- `app/src/test`: offline unit tests.
- `docs/WOLF_AUDIO_CINEMATIC_IDENTITY_PHASE_15.md`: Phase 15 implementation notes and verification boundaries.
- `docs/PHASE_16_OFFLINE_RUNTIME_CLOSURE.md`: offline boundary, audit findings, and verification limits.

## Build requirements

- Android SDK 35
- JDK 17
- Gradle 8.13 or a compatible Gradle 8.x release
- Android device API 26 or later for the application runtime

Open the repository as an Android Gradle project in a compatible IDE, or use a Gradle installation to run the verification tasks.

## Verification policy

The continuous-integration workflow runs the offline runtime contract check, Android lint, and local JVM unit tests. It does not build an APK, start an emulator, or run instrumentation tests. Device/runtime verification is a separate final stage and must only be performed after the offline product has been completed and explicitly approved for that stage.

## Privacy and connectivity

The application runtime is designed to operate without internet access. The Android manifest does not request Internet or network-state permissions. The production source is guarded against common network APIs, embedded HTTP(S) endpoints, WebView, and unapproved direct runtime dependencies by the `:app:verifyOfflineRuntime` Gradle task.

Project records, narration recordings, horror knowledge TXT entries, their indexes, rendering, and exported media are stored locally. The offline production path does not require an account, API key, paid service, remote controller, or cloud backup. User-selected image URIs are retained as local document references.

The story-writing AI uses the on-device LiteRT-LM runtime. A compatible `.litertlm` model file must be selected and imported by the user from local storage before AI story generation can run. The application does not download model weights. Without an imported model, the user can still write or paste a story manually and use the local film-production pipeline.

The offline contract task is a source/manifest/direct-dependency guard. It is not a physical-device network-capture test and cannot prove the behavior of Android, the operating system, or transitive native code at runtime. CI verification must therefore not be described as a completed device test.

## Output

Rendered videos are exported to the device's Movies/WOLF CODE S media collection when the platform media provider is available.
