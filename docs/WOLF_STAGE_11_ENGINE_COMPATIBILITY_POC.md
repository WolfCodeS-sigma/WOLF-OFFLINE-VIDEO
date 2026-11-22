# WOLF Stage 11 - Individual Engine Compatibility POCs

**Stage:** 11  
**Branch:** feature/stage-11-engine-compatibility-poc  
**Base:** Stage 10 validated commit 1ef1b39b8aa2d9d5c8bd2d59fc01c0548b7b6b11  
**Status:** Isolated POC suite prepared; automated outcomes pending CI  
**Production integration:** none  
**Asset intake:** no real cinematic assets were added by this stage

## 1. Acceptance boundary

Each candidate is evaluated independently. The POC projects live under engine-pocs/ and are not included by the production settings.gradle.kts; no candidate is added to app production dependencies. No renderer, camera authority, Scene Contract, Motion Hub, MP4 export, audio, project data, checkpoints, user-image processing, or production registry behavior is changed.

A POC result is limited to the exact test level stated below. Library resolution, class linkage, native runtime initialization, one rendered frame, Android packaging, full scene rendering, visual quality, and physical-device performance are different levels of evidence and are not interchangeable.

## 2. Individual candidate matrix

| Candidate | Pinned POC version | Isolated proof target | Result classification |
|---|---:|---|---|
| Godot Engine | 4.7.2 stable | Android AAR resolution + Godot host API class loading | API-linkage only; exported game runtime not yet proven |
| Filament | 1.81.0 | Native Android engine + renderer/scene/view/camera resource lifecycle | Native runtime/resource-graph proof; no final surface-quality claim |
| Cocos Creator / native engine | 3.8.7 | Creator-generated Android native project, then NDK build and emulator rendered-frame test | **BLOCKED - NOT PASSED** |
| libGDX | 1.14.2 | Launch Android backend Activity and render at least one GLES frame | Android runtime frame proof |
| KorGE | 6.0.0 stable | Build/package the isolated Android target | Android packaging proof; no physical-device performance claim |
| Lottie Android | 6.7.1 | Parse local JSON and rasterize a vector animation without network access | Local animation rendering proof |
| SceneView | 4.70.0 | Separate artifact-resolution check only | Filament-based UI façade; not an independent engine |

## 3. Candidate-specific constraints

### Godot

The Android AAR exposes embedding APIs such as GodotHost, GodotFragment, and GodotActivity. The test confirms those host classes are packaged and loadable. It does not claim that a WOLF Godot game has been exported or executed: official embedding requires a project-specific AAR generated from a Godot project and then included with its export dependencies. Only one Godot engine instance is supported per Android process; orientation/resizing must also be handled according to the embedding documentation.

### Filament and SceneView

Filament's test initializes the Android native runtime and creates/destroys the core resource graph. A later surface-render proof must use a real render target and check output; this POC alone is not a PBR quality or target-device performance verdict.

SceneView is resolved as an optional Android viewer/UI artifact built on Filament. It is not counted as a seventh rendering engine and does not create a second camera authority.

### Cocos

The POC cannot be marked compatible until a Creator-generated Android native project is built and launched. Cocos Creator's native Android path needs the Creator build/export stage; the pinned v3.8.7 native backend documents NDK 21-22, CMake 3.8+, and C++17. The current isolated CI has no Creator editor/project export, so the result is explicitly blocked rather than inferred from Android support claims. No Cocos engine package was added to WOLF production.

### libGDX, KorGE, and Lottie

libGDX is tested by launching its Android backend and observing a GLES frame. KorGE is tested with its separate stable Android target packaging task, without changing WOLF's root Gradle settings. Lottie is tested using a locally bundled synthetic JSON fixture; the test neither downloads an animation nor promotes the fixture into the cinematic asset library.

## 4. Registry, licensing, and asset boundaries

- No POC fixture is a cinematic asset.
- No real asset was downloaded, added to the production manifest, or marked approved by this stage.
- No third-party engine binary is copied into the production application.
- Any future engine inclusion requires a separate version/license/dependency/ABI review and an explicit integration stage.
- Any real cinematic asset must independently pass the Stage 08 Asset Registry & Quality Gate, rights evidence, realism review, file/hash checks, target-runtime compatibility, scope, dependencies, and measured resource limits.

## 5. Verification commands

From the repository root, the Android POC project is built and run independently from WOLF production:

    gradle --no-daemon -p engine-pocs/android :app:assembleGodotDebug :app:connectedGodotDebugAndroidTest :app:assembleFilamentDebug :app:connectedFilamentDebugAndroidTest :app:assembleLibgdxDebug :app:connectedLibgdxDebugAndroidTest :app:assembleLottieDebug :app:connectedLottieDebugAndroidTest :app:dependencies --configuration sceneViewPoc

The separate KorGE target is packaged with:

    gradle --no-daemon -p engine-pocs/korge bundleAndroid

CI outcomes must be recorded from the run on this exact branch head. No POC is approved for production merely because its isolated build/test passes.
