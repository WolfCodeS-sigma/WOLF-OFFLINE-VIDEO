# WOLF isolated engine POCs

These projects are intentionally outside the production Gradle settings and Android app module. They are not included in the WOLF APK and do not alter the current renderer, exporter, audio, timing, checkpoints, user-image pipeline, or asset registry.

## Current candidate checks

- **Godot 4.7.2 stable:** Android AAR resolution, host API linkage, and an isolated `GodotActivity` hosting a local Godot sample project in the flavor APK assets. CI checks its expected rendered frame on Android 12. This is an embedded-view POC, not production integration or a Realme 7 performance claim.
- **Filament 1.75.0:** Android native engine initialization and creation/destruction of a renderer, scene, view, and camera resource graph. The POC uses the API-34-compatible stable release line; newer checked tags require compileSdk 37, beyond the selected AGP 8.13.2/compileSdk 36 toolchain. This does not establish final PBR image quality or performance on the Realme 7.
- **libGDX 1.14.2:** Android backend Activity launch and an actual OpenGL frame on the API 31 emulator.
- **KorGE 6.0.0:** isolated Android target packaging using the stable plugin line. Its settings plugin requires JDK 21, so only the isolated KorGE CI job uses JDK 21; WOLF production and the other engine POC app remain on JDK 17.
- **Lottie Android 6.7.1:** parse a bundled local JSON animation and rasterize it into a bitmap with no network request.
- **Cocos Creator 3.8.7:** blocked until the matching Creator editor export and NDK 21/22 native project can be supplied and tested. See cocos/README.md.
- **SceneView 4.70.0:** treated as an optional Android UI/viewer facade built on Filament, not a separate independent engine. CI resolves its Android artifact separately; no duplicate rendering-engine count is added.

All local animation/scene data in this directory are synthetic test fixtures only. They are not cinematic assets and must not be entered into the production WOLF Cinematic Asset Library.
