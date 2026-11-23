# WOLF OFFLINE VIDEO

Standalone Android application for local horror-film production. This repository contains the offline production path only; it does not contain the WOLF online engines, WebView workspace, API hub, or cloud services.

## Product direction

WOLF OFFLINE VIDEO builds a film locally from story text, optional user-selected images, deterministic scene planning, atmospheric procedural rendering, horror typography, local sound effects, and an optional approved human voice recording. It does not require a large video-generation model or a cloud video service.

## Production architecture

- `MainActivity.kt`: local project, story, voice-recording, approval, and production interface.
- `WolfOfflineStoryAiEngine.kt` and `WolfOfflineStoryAiActivity.kt`: on-device LiteRT-LM story planning and resumable generation. A compatible model must be imported by the user.
- `StoryDirectorEngine.kt`: deterministic scene and three-shot planning, exact frame allocation, shot-boundary chapter slicing, motion intent, composition, and lighting intent.
- `WolfProceduralSceneProductionEngine.kt`: chooses the environment category for shots without user images. It does not invent people, ghosts, creatures, buildings, doors, furniture, or props from primitive geometry.
- `WolfSceneArtEngine.kt` and `WolfProceduralMotionEngine.kt`: atmospheric backgrounds and environmental effects only, including haze, particles, wind-driven branches, rain, environmental pulses, and restrained signal distortion.
- `WolfSceneRenderPipeline.kt`: one render dispatch boundary. No selected image means the environment-only procedural path. A selected image remains the source even if segmentation is unavailable; it is rendered as a still until semantic depth validation is genuinely available. An actual failure to render the selected image aborts export rather than silently substituting another scene.
- `WolfImageDepthSegmenter.kt`, `WolfUploadedImageEngine.kt`, `Wolf25DCinematicImageEngine.kt`, and `WolfSceneDepthComposer.kt`: bounded local image analysis and depth composition. The current color/border heuristic is not semantic segmentation and is explicitly not authorized to drive parallax.
- `WolfSceneAssetStore.kt`: project-scoped user image slots and depth-analysis versioning.
- `OfflineProductionEngine.kt`: local frame rendering, H.264 MP4 encoding, audio integration, shot-aligned chapter rendering, resumable checkpoints, and export inspection.
- `WolfLongFilmEngine.kt` and `WolfFilmProductionCoordinator.kt`: bounded chapters, shot-aligned recovery, deterministic render fingerprints, and ordered assembly.
- `WolfStoryVoiceEngine.kt` and `WolfVoiceSegmentStore.kt`: human narration recording, explicit approval, segmented recording recovery, and ordered audio assembly.
- `WolfCinematicAudioTimeline.kt`, `WolfAudioMixer.kt`, and `WolfAudioTranscoder.kt`: shared intro/narration/video timing, narration cleanup, effects ducking, full-duration PCM output, and AAC integration.
- `WolfMotionCodeLibrary.kt` and `WolfSceneContract.kt`: engine-neutral motion primitives and a versioned renderer contract. These are core contracts, not proof that a separate engine has been integrated.
- `WolfHorrorKnowledgeLibrary.kt`: private on-device horror reference library.
- `WolfDiagnostics.kt`, `WolfResourceGuard.kt`, `WolfMemoryManager.kt`, and `WolfFilmQualityInspector.kt`: diagnostics, resource protection, memory safeguards, and output inspection.

## Visual fallback rules

1. **No user image:** render the planned environment and atmospheric effects. Do not claim that a character or object was generated when no approved asset-backed subject renderer exists.
2. **User image with no semantic depth validation:** preserve the original selected image as a still. No camera zoom, pan, parallax, or procedural scene substitution.
3. **User image cannot be decoded:** stop that export path with an explicit error. Do not replace the selected image silently.
4. **Selected image fails during actual rendering:** stop export instead of switching to an unrelated generated scene.
5. **Depth/parallax:** remains disabled until a real semantic validation implementation passes its own tests. Confidence from color-distance or border heuristics is not sufficient.

## Build requirements

- Android SDK 35
- JDK 17
- Gradle 8.13 or a compatible Gradle 8.x release
- Android device API 26 or later for the application runtime

Open the repository as an Android Gradle project in a compatible IDE, or use a Gradle installation to run the verification tasks.

## Verification policy

The core CI workflow runs the offline runtime contract check, Android lint, and local JVM unit tests. It does not by itself prove a successful physical-device export, a complete emulator regression, or correct playback on a specific phone. Device/runtime verification remains a separate gate and must be reported only when actually performed.

## Privacy and connectivity

The application runtime is designed to operate without internet access. The Android manifest does not request Internet or network-state permissions. The `:app:verifyOfflineRuntime` Gradle task guards source, manifest, and direct dependencies against common network APIs and unapproved runtime dependencies.

Project records, narration recordings, the horror knowledge library, rendering, and exported media are stored locally. The offline production path does not require an account, API key, paid service, remote controller, or cloud backup. User-selected image URIs are retained as local document references.

The story-writing AI uses the on-device LiteRT-LM runtime. A compatible `.litertlm` model file must be selected and imported by the user from local storage before AI story generation can run. The application does not download model weights. Without an imported model, the user can still write or paste a story manually and use the local film-production pipeline.

## Output

Rendered videos are exported to the device's Movies/WOLF CODE S media collection when the platform media provider is available.
