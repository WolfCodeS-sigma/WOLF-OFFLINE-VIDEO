# WOLF Stage 11 - Individual Engine Compatibility POCs

**Stage:** 11  
**Branch:** feature/stage-11-engine-compatibility-poc  
**Base:** Stage 10 validated commit 1ef1b39b8aa2d9d5c8bd2d59fc01c0548b7b6b11  
**Status:** CI-verified isolated POCs for Godot, Filament, libGDX, KorGE, and Lottie; SceneView artifact resolution verified; Cocos remains blocked pending a Creator-generated Android native project  
**Production integration:** none  
**Asset intake:** no real cinematic assets were added by this stage

## 1. Acceptance boundary

Each candidate is evaluated independently. The POC projects live under engine-pocs/ and are not included by the production settings.gradle.kts; no candidate is added to app production dependencies. No renderer, camera authority, Scene Contract, Motion Hub, MP4 export, audio, project data, checkpoints, user-image processing, or production registry behavior is changed.

A POC result is limited to the exact test level stated below. Library resolution, class linkage, native runtime initialization, one rendered frame, Android packaging, full scene rendering, visual quality, and physical-device performance are different levels of evidence and are not interchangeable.

## 2. Individual candidate matrix

| Candidate | Pinned POC version | Isolated proof target | Result classification |
|---|---:|---|---|
| Godot Engine | 4.7.2 stable | Android AAR resolution + Godot host API class loading | API-linkage only; exported game runtime not yet proven |
| Filament | 1.75.0 | Native Android engine + renderer/scene/view/camera resource lifecycle | Native runtime/resource-graph proof; no final surface-quality claim |
| Cocos Creator / native engine | 3.8.7 | Creator-generated Android native project, then NDK build and emulator rendered-frame test | **BLOCKED - NOT PASSED** |
| libGDX | 1.14.2 | Launch Android backend Activity and render at least one GLES frame | Android runtime frame proof |
| KorGE | 6.0.0 stable | Build/package the isolated Android target | Android packaging proof; no physical-device performance claim |
| Lottie Android | 6.7.1 | Parse local JSON and rasterize a vector animation without network access | Local animation rendering proof |
| SceneView | 4.70.0 | Separate artifact-resolution check only | Filament-based UI facade; not an independent engine |

## 3. Candidate-specific constraints

### Godot

The Android AAR exposes embedding APIs such as GodotHost, GodotFragment, and GodotActivity. The test confirms those host classes are packaged and loadable. It does not claim that a WOLF Godot game has been exported or executed: official embedding requires a project-specific AAR generated from a Godot project and then included with its export dependencies. Only one Godot engine instance is supported per Android process; orientation/resizing must also be handled according to the embedding documentation.

### Filament and SceneView

Filament 1.75.0 is pinned for the isolated POC because its Android module remains on compileSdk 34. The newer checked releases 1.76.0 through 1.81.0 declare compileSdk 37; the isolated app stays on compileSdk 36 and AGP 8.13.2 rather than adopting an Android preview/API 37 toolchain solely for this experiment.

Filament's test initializes the Android native runtime and creates/destroys the core resource graph. A later surface-render proof must use a real render target and check output; this POC alone is not a PBR quality or target-device performance verdict.

SceneView is resolved as an optional Android viewer/UI artifact built on Filament. It is not counted as a seventh rendering engine and does not create a second camera authority.

### Cocos

The POC cannot be marked compatible until a Creator-generated Android native project is built and launched. Cocos Creator's native Android path needs the Creator build/export stage; the pinned v3.8.7 native backend documents NDK 21-22, CMake 3.8+, and C++17. The current isolated CI has no Creator editor/project export, so the result is explicitly blocked rather than inferred from Android support claims. No Cocos engine package was added to WOLF production.

### libGDX, KorGE, and Lottie

libGDX is tested by launching its Android backend and observing a GLES frame. The Android POC explicitly unpacks the official 1.14.2 gdx-platform ABI native classifier jars into the isolated libGDX flavor's generated jniLibs tree; without these native libraries the backend fails at startup with `libgdx.so` missing. KorGE is tested with its separate stable Android target packaging task, without changing WOLF's root Gradle settings. KorGE 6.0.0's settings plugin requires a JDK 21 runtime; only the KorGE POC packaging job uses JDK 21, while the WOLF production project and the other Android POC variants remain on JDK 17. Lottie is tested using a locally bundled synthetic JSON fixture; the test neither downloads an animation nor promotes the fixture into the cinematic asset library.

## 4. Registry, licensing, and asset boundaries

- No POC fixture is a cinematic asset.
- No real asset was downloaded, added to the production manifest, or marked approved by this stage.
- No third-party engine binary is copied into the production application.
- Any future engine inclusion requires a separate version/license/dependency/ABI review and an explicit integration stage.
- Any real cinematic asset must independently pass the Stage 08 Asset Registry & Quality Gate, rights evidence, realism review, file/hash checks, target-runtime compatibility, scope, dependencies, and measured resource limits.

## 5. Verification commands

From the repository root, the Android POC project is built and run independently from WOLF production:

    gradle --no-daemon -p engine-pocs/android :app:assembleGodotDebug :app:connectedGodotDebugAndroidTest :app:assembleFilamentDebug :app:connectedFilamentDebugAndroidTest :app:assembleLibgdxDebug :app:connectedLibgdxDebugAndroidTest :app:assembleLottieDebug :app:connectedLottieDebugAndroidTest :app:dependencies --configuration sceneViewPoc

The separate KorGE 6.0.0 Android executable is packaged using the task names exposed by this exact plugin version:

    gradle --no-daemon -p engine-pocs/korge assembleDebug packageAndroidDebug

`assembleDebug` creates the debug APK; `packageAndroidDebug` is the KorGE 6.0.0 task that depends on Android's `bundleDebug` and creates the debug AAB. The older `bundleAndroid` task name in the general KorGE Android page is not registered by the pinned 6.0.0 plugin.

CI outcomes must be recorded from the run on this exact branch head. No POC is approved for production merely because its isolated build/test passes.


## 6. CI execution evidence

The Stage 11 POC implementation was first verified on commit
`9e07568b032d9ff2350d81b20301d6f19146e9bb`. The subsequent commit
`fd89bd89e9d16eb1d8713a52a51d232003163314` changed only this Stage 11 report;
it did not change any POC source, Gradle configuration, production file, or
engine test. The two workflow runs below therefore validate the same POC
implementation and its documentation-only follow-up:

- [Stage 11 isolated engine POC workflow](https://github.com/WolfCodeS-sigma/WOLF-OFFLINE-VIDEO/actions/runs/44970929424): **success**. All four Android 12 instrumentation tests completed (Godot, Filament, libGDX, and Lottie), the isolated POC variants built, SceneView 4.70.0 dependency resolution completed, and the separate KorGE 6.0.0 Android APK/AAB packaging job succeeded.
- [Stage 01–08 regression workflow](https://github.com/WolfCodeS-sigma/WOLF-OFFLINE-VIDEO/actions/runs/44970929423): **success**. The Debug APK, Android lint, and JVM unit-test tasks passed; the Android 12 emulator regression suite completed all 13 instrumentation tests with zero skipped and zero failed. The emulator job needed a retry after its first attempt could not resolve external Gradle classpath artifacts; the retry passed.
- The emulator logs contained ADB, console, EGL, and graphics warnings. They did not prevent the successful POC or regression test runs.

These results validate only the POC proof targets stated in the candidate matrix. They do not prove Godot project-specific game embedding, Cocos compatibility, production renderer integration, visual quality, or physical-device performance. Cocos remains **BLOCKED - NOT PASSED** until a Creator-generated native Android project is built and shown rendering on an emulator.

No real cinematic assets were acquired or ingested by Stage 11. The production asset manifest remains empty; all local motion/scene samples are synthetic test fixtures and must not be treated as cinematic library assets.


## 7. Parallel asset-intake remediation and current evidence

The earlier Stage 08 agreement to collect and ingest real assets in batches during Stages 09–20 had no executable queue, batch contract, validator, or CI check. That was the root cause of the workstream remaining only a documented intention.

This branch now establishes an executable intake track:
- `asset-intake/parallel-intake-plan.json` records evidence checkpoints for every stage from 09 through 20.
- `asset-intake/batches/000-baseline/manifest.json` is the first machine-validated batch baseline.
- `tools/asset-intake/wolf_asset_intake.py` and its eight unit tests check provenance fields, exact local file bytes, hashes, path safety, rights assertions, realism review, exact tested runtime/version, scopes, and measured PASS budgets.
- `.github/workflows/wolf-asset-intake.yml` runs the validator tests and asserts source leads cannot silently enter the production registry.

**Actual collection status remains deliberately honest:** Batch 000 has zero files. The source-lead register contains one Poly Haven abandoned-basement-corridor HDRI lead (CC0 source terms) but its binary has not been acquired, hashed, visually reviewed, or tested on a WOLF runtime. It is not a library asset and is not in the APK. No Stage 11 result can therefore claim real cinematic asset intake completion.

## 8. Cocos and production integration boundary

Cocos Creator 3.8.7 remains **BLOCKED - NOT PASSED**. The earlier all-green Stage 11 POC run did not include a Cocos build/test job; it covered only the candidates explicitly listed in the run output. The Cocos documentation now records why a Creator editor plus GUI-capable native export environment is required and why a source-only test would be misleading.

Keeping engine POCs under `engine-pocs/` and out of production Gradle settings is an intentional safety boundary, not a compatibility defect. Stage 11 establishes engine evidence; Stage 12 assigns approved roles; Stage 15 performs controlled production integration. Promoting an unproven Cocos package into production now would bypass those gates and risk the previously working build. No production Gradle file, production manifest, user-image flow, media pipeline, or project data was changed by this remediation.
