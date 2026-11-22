# WOLF isolated engine POCs

These projects are intentionally outside the production Gradle settings and Android app module. They are not included in the WOLF APK and do not alter the current renderer, exporter, audio, timing, checkpoints, user-image pipeline, or asset registry.

## Current candidate checks

- **Godot 4.7.2 stable:** Android AAR dependency resolution and runtime loading of the documented Godot host API classes. This does not launch an exported Godot game project; a project-specific Android AAR is still required for that proof.
- **Filament 1.81.0:** Android native engine initialization and creation/destruction of a renderer, scene, view, and camera resource graph. This does not establish final PBR image quality or performance on the Realme 7.
- **libGDX 1.14.2:** Android backend Activity launch and an actual OpenGL frame on the API 31 emulator.
- **KorGE 6.0.0:** isolated Android target packaging using the stable plugin line. The check proves Android packaging/build compatibility, not final WOLF scene quality or device performance.
- **Lottie Android 6.7.1:** parse a bundled local JSON animation and rasterize it into a bitmap with no network request.
- **Cocos Creator 3.8.7:** blocked until the matching Creator editor export and NDK 21/22 native project can be supplied and tested. See cocos/README.md.
- **SceneView 4.70.0:** treated as an optional Android UI/viewer façade built on Filament, not a separate independent engine. CI resolves its Android artifact separately; no duplicate rendering-engine count is added.

All local animation/scene data in this directory are synthetic test fixtures only. They are not cinematic assets and must not be entered into the production WOLF Cinematic Asset Library.
