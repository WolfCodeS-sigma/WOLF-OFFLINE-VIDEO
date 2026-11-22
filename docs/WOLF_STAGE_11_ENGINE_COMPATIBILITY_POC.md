# WOLF Stage 11 - Individual Engine Compatibility POCs

**Stage:** 11  
**Branch:** feature/stage-11-engine-compatibility-poc  
**Base:** Stage 10 validated commit 1ef1b39b8aa2d9d5c8bd2d59fc01c0548b7b6b11  
**Status:** CI-verified isolated POCs for Godot (bundled sample-scene render), Filament, libGDX, KorGE, and Lottie; SceneView artifact resolution verified; Cocos remains blocked pending a Creator-generated Android native project  
**Production integration:** none  
**Asset intake:** two real Poly Haven CC0 environment HDRIs acquired as CANDIDATE assets; none is production-approved or bundled in the APK

## 1. Acceptance boundary

Each candidate is evaluated independently. The POC projects live under engine-pocs/ and are not included by the production settings.gradle.kts; no candidate is added to app production dependencies. No renderer, camera authority, Scene Contract, Motion Hub, MP4 export, audio, project data, checkpoints, user-image processing, or production registry behavior is changed.

A POC result is limited to the exact test level stated below. Library resolution, class linkage, native runtime initialization, one rendered frame, Android packaging, full scene rendering, visual quality, and physical-device performance are different levels of evidence and are not interchangeable.

## 2. Individual candidate matrix

| Candidate | Pinned POC version | Isolated proof target | Result classification |
|---|---:|---|---|
| Godot Engine | 4.7.2 stable | Android AAR/host API + bundled local sample project + expected rendered-pixel assertion on Android 12 | Embedded sample-scene frame proof passed; no production integration or Realme 7 claim |
| Filament | 1.75.0 | Native Android engine + renderer/scene/view/camera resource lifecycle | Native runtime/resource-graph proof; no final surface-quality claim |
| Cocos Creator / native engine | 3.8.7 | Creator-generated Android native project, then NDK build and emulator rendered-frame test | **BLOCKED - NOT PASSED** |
| libGDX | 1.14.2 | Launch Android backend Activity and render at least one GLES frame | Android runtime frame proof |
| KorGE | 6.0.0 stable | Build/package the isolated Android target | Android packaging proof; no physical-device performance claim |
| Lottie Android | 6.7.1 | Parse local JSON and rasterize a vector animation without network access | Local animation rendering proof |
| SceneView | 4.70.0 | Separate artifact-resolution check only | Filament-based UI facade; not an independent engine |

## 3. Candidate-specific constraints

### Godot

The pinned Android AAR exposes embedding APIs such as GodotHost, GodotFragment, and GodotActivity. The isolated POC packages a local Godot sample project under the flavor APK assets, launches it through GodotPocActivity, and captures the Android 12 emulator screen until the expected crimson proof region is detected. The test also verifies the host API classes. The complete Godot instrumentation task passed in Stage 11 CI.

This proves the tested Godot sample project can be hosted and rendered inside the isolated Android APK. It does not prove that WOLF's production scenes, assets, camera contract, or renderer have been ported, nor does it prove Realme 7 performance. Only one Godot engine instance is supported per Android process; orientation/resizing must be handled according to the embedding documentation. Godot remains a candidate for the Stage 12 role decision, not an automatically approved production dependency.

### Filament and SceneView

Filament 1.75.0 is pinned for the isolated POC because its Android module remains on compileSdk 34. The newer checked releases 1.76.0 through 1.81.0 declare compileSdk 37; the isolated app stays on compileSdk 36 and AGP 8.13.2 rather than adopting an Android preview/API 37 toolchain solely for this experiment.

Filament's test initializes the Android native runtime and creates/destroys the core resource graph. A later surface-render proof must use a real render target and check output; this POC alone is not a PBR quality or target-device performance verdict.

SceneView is resolved as an optional Android viewer/UI artifact built on Filament. It is not counted as a seventh rendering engine and does not create a second camera authority.

### Cocos

The POC cannot be marked compatible until a Creator-generated Android native project is built and launched. Cocos Creator's native Android path needs the Creator build/export stage; the pinned v3.8.7 native backend documents NDK 21-22, CMake 3.8+, and C++17. The current isolated CI has no Creator editor/project export, so the result is explicitly blocked rather than inferred from Android support claims. No Cocos engine package was added to WOLF production.

### libGDX, KorGE, and Lottie

libGDX is tested by launching its Android backend and observing a GLES frame. The Android POC explicitly unpacks the official 1.14.2 gdx-platform ABI native classifier jars into the isolated libGDX flavor's generated jniLibs tree; without these native libraries the backend fails at startup with `libgdx.so` missing. KorGE is tested with its separate stable Android target packaging task, without changing WOLF's root Gradle settings. KorGE 6.0.0's settings plugin requires a JDK 21 runtime; only the KorGE POC packaging job uses JDK 21, while the WOLF production project and the other Android POC variants remain on JDK 17. Lottie is tested using a locally bundled synthetic JSON fixture; the test neither downloads an animation nor promotes the fixture into the cinematic asset library.

## 4. Registry, licensing, and asset boundaries

- No synthetic POC fixture is a cinematic asset.
- Two genuine Poly Haven 2K Radiance HDR files were acquired in the separate WOLF-ASSET-BATCH-001: Debris Basement Corridor and Abandoned Hall 01. Both are CANDIDATE only; their visual/runtime review, resource measurements, and approved scopes remain pending.
- The acquired files are delivered as a CI artifact with provenance and hashes; they have not been copied into the production manifest or APK.
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

The following runs all completed on the exact PR head `f3c61eb1e125713d0012014f8952d604b303c9e8`:

- [Stage 11 isolated engine POC workflow](https://github.com/WolfCodeS-sigma/WOLF-OFFLINE-VIDEO/actions/runs/44974469590): **success**. The Android 12 tasks for Godot, Filament, libGDX, and Lottie all completed successfully; Godot's test includes both host API linkage and the local sample-scene rendered-pixel assertion. SceneView 4.70.0 dependency resolution and isolated Android POC builds passed. The separate KorGE 6.0.0 Android APK/AAB packaging job also succeeded.
- [Stage 01–08 regression audit](https://github.com/WolfCodeS-sigma/WOLF-OFFLINE-VIDEO/actions/runs/44974469628), attempt 2: **success**. Debug APK, Android lint, and JVM unit tests passed. The Android 12 emulator completed all 13 instrumentation tests with zero skipped and zero failed. Attempt 1 failed before test execution while Gradle could not resolve multiple external Maven classpath artifacts; retrying the same job on the same commit passed.
- [WOLF cinematic asset intake](https://github.com/WolfCodeS-sigma/WOLF-OFFLINE-VIDEO/actions/runs/44974469575): **success**. Two files were acquired (12,906,007 bytes total), provider MD5 and byte sizes were verified, WOLF SHA-256 values were calculated, and all 11 intake/acquisition unit tests passed. The resulting batch artifact contains the two HDR files and source provenance.

The emulator logs include ADB/console, EGL, ColorBuffer, and graphics warnings. They did not prevent the successful test tasks; this does not mean the emulator logs were warning-free.

These results prove only the stated isolated POC levels and Stage 01–08 regression coverage. They do not prove production renderer integration, final visual quality, or physical-device performance. Cocos remains **BLOCKED - NOT PASSED** because a Creator-generated Android native project and rendered-frame test are still absent.

The acquired HDRIs remain candidates, not production assets. The production registry remains empty and unchanged. All sample scenes and Lottie fixtures remain synthetic POC inputs, not cinematic library content.


## 7. Parallel asset-intake remediation and current evidence

The original Stage 08 agreement to collect and ingest real assets in batches during Stages 09–20 had no executable acquisition queue or cross-file consistency check. The first remediation introduced the acquisition tool and CI artifact, but the source-lead register still said that an already acquired source had not been acquired. That bookkeeping inconsistency is corrected in this revision.

The active intake track now consists of:
- `asset-intake/acquisition-catalog.json`: exact source IDs, URLs, WOLF IDs, categories, and acquisition states.
- `asset-intake/leads/source-leads.json`: both acquired candidates with first-acquisition timestamp, exact file path/format, size, provider MD5, and SHA-256.
- `tools/asset-intake/acquire_polyhaven_batch.py`: developer/CI-only acquisition of the exact 2K Radiance HDR variants, with live license-page check, source metadata, provider MD5, byte-size verification, and WOLF SHA-256.
- `tools/asset-intake/validate_acquisition_links.py`: fail-closed cross-check of the acquired batch manifest against the lead register and catalog, so a lead cannot remain DISCOVERED_NOT_ACQUIRED after appearing in a batch, nor claim ACQUIRED_CANDIDATE without matching files/hashes.
- `tools/asset-intake/wolf_asset_intake.py`: generic file/provenance/licensing/review/budget gate.
- `.github/workflows/wolf-asset-intake.yml`: unit tests, lead/catalog consistency checks, acquired-file validation, and the candidate-batch artifact upload.

**Actual collection status:** WOLF-ASSET-BATCH-001 contains two acquired CC0 environment HDRI candidates. Their binaries and source provenance are available in the GitHub Actions artifact with 90-day retention; they are not committed as production source files and are not inside the APK. Batch 000 remains the intentionally empty baseline. The two acquired candidates still need WOLF renderer-import tests, measured resource budgets, explicit use scopes, and human approval. This is the start of real collection, not completion of the cinematic library or the 100% coverage requirement.


## 8. Cocos and production integration boundary

Cocos Creator 3.8.7 remains **BLOCKED - NOT PASSED**. The earlier all-green Stage 11 POC run did not include a Cocos build/test job; it covered only the candidates explicitly listed in the run output. The Cocos documentation now records why a Creator editor plus GUI-capable native export environment is required and why a source-only test would be misleading.

Keeping engine POCs under `engine-pocs/` and out of production Gradle settings is an intentional safety boundary, not a compatibility defect. Stage 11 establishes engine evidence; Stage 12 assigns approved roles; Stage 15 performs controlled production integration. Promoting an unproven Cocos package into production now would bypass those gates and risk the previously working build. No production Gradle file, production manifest, user-image flow, media pipeline, or project data was changed by this remediation.


## 9. Real-asset acquisition and Cocos fallback proof update

A developer/CI-only acquisition tool now retrieves the curated Poly Haven 2K Radiance HDR source files for `debris_basement_corridor` and `abandoned_hall_01`. It checks the current CC0 license page, obtains the exact source metadata, verifies provider MD5 and byte size, computes WOLF SHA-256, and writes a provenance-backed batch manifest. The intake validator then rechecks exact local file size/hash and emits the acquired batch as a CI artifact. Nothing is downloaded by the installed WOLF application.

Both acquired records deliberately remain `CANDIDATE`: visual review, actual WOLF renderer import, measured resource budgets, and approved use scopes still need separate checks. The acquisition workflow does not modify the production Asset Registry.

The Godot 4.7.2 POC has passed the sample-scene rendered-pixel assertion on Android 12, in addition to host API linkage. It is therefore the preferred evidence-backed Cocos fallback candidate for the Stage 12 role assessment. This does not select or integrate it into production; that requires the separate Stage 12 decision and later Stage 15 integration gate.
