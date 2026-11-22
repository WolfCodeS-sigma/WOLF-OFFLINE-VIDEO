# Cocos Creator Android POC - blocked pending the native export toolchain

**Pinned candidate:** Cocos Creator 3.8.7 / Cocos native engine v3.8.7.

This is deliberately recorded as **BLOCKED - NOT COMPATIBILITY-PASSED**. The current repository and CI environment do not contain a Cocos Creator editor installation or a Creator-generated Android native project. A source-only or Maven-resolution check would not prove that the actual Creator runtime can be built and launched inside WOLF.

The official Creator 3.8 native publishing flow first generates a native project from the Creator editor/build system; Android compilation then runs on that generated project. The native engine's v3.8.7 README states that Android requires NDK 21-22 (NDK 23+ is not supported by that engine revision), plus CMake 3.8 or later and C++17. The project must therefore use a separately pinned NDK 22 toolchain rather than inheriting the production app's normal Android SDK setup.

## Required evidence before changing this status

1. The exact Creator 3.8.7 editor/build tool and its distribution terms are recorded.
2. A tiny local Creator project with one deterministic 2D/3D effect scene is exported to its native Android project.
3. That generated project is built using the documented NDK 21/22 and CMake toolchain.
4. Its APK is installed and launched on an Android emulator, and the test verifies the Cocos scene actually reaches a rendered frame.
5. The POC remains separate from the WOLF production application and its asset registry.

No Cocos binaries, project files, or assets have been added to the production APK. Do not infer support from the fact that Cocos supports Android in general.


## Why the previous green POC workflow did not prove Cocos

The Stage 11 GitHub-hosted workflow did not execute a Cocos job; its green result covered only the named Godot, Filament, libGDX, Lottie, SceneView-resolution, and KorGE packaging checks. That green result must not be interpreted as an all-candidate pass.

Creator's official command-line publishing instructions state that the Creator application still needs a GUI environment even when invoked from the command line. The current repository runner has neither a licensed Creator 3.8.7 installation nor a configured interactive/virtual display plus Creator-generated export. A plain native engine source build or dependency resolution would be a different test and cannot close this gate.

Official references:
- Creator 3.8 command-line publishing: https://docs.cocos.com/creator/3.8/manual/en/editor/publish/publish-in-command-line.html
- Creator native Android publishing: https://docs.cocos.com/creator/3.8/manual/en/editor/publish/native-options.html
- Creator native project layout: https://docs.cocos.com/creator/3.8/manual/en/advanced-topics/native-secondary-development.html

Do not add the editor binary to this repository or the WOLF APK. The editor is a separately licensed development tool. Keep the Cocos candidate blocked until a properly licensed Creator 3.8.7 build environment can export the project and the generated APK reaches a verified rendered frame. Only after that proof can Stage 12 assess its role; production integration remains a later, separate stage.


## Technical disposition and preferred alternative

The Cocos limitation is structural to the available hosted runner, not a missing Maven dependency. Creator 3.8 documents that command-line publishing still needs a GUI environment; the documented command-line examples target macOS and Windows, while the current WOLF CI runner is headless Ubuntu. Creator's native publishing workflow also uses its Build/Make steps to generate the native Android project before Android Studio/Gradle compilation. A headless native source build is not equivalent to a Creator export and cannot honestly close this compatibility gate.

Official evidence:
- Creator 3.8 command-line publishing, including the GUI requirement: https://docs.cocos.com/creator/3.8/manual/en/editor/publish/publish-in-command-line.html
- Creator native Android publishing and Build/Make workflow: https://docs.cocos.com/creator/3.8/manual/en/editor/publish/native-options.html
- Creator Android native development setup: https://docs.cocos.com/creator/3.8/manual/en/editor/publish/setup-native-development.html

**Preferred strong alternative: Godot 4.7.2 stable.** Godot officially provides an Android library/AAR and supports embedding a Godot project inside an existing Android application through `GodotActivity` or `GodotFragment`. Godot's documentation explicitly supports placing the project under the host APK's `assets` directory. This fits WOLF better than relying on an unavailable Creator editor pipeline: Android/Kotlin continues to own the product shell, while Godot is a scene-runtime surface behind a controlled host contract.

Known constraints remain: only one Godot engine instance is supported per Android process, and orientation/resize configuration events must be handled explicitly. See https://docs.godotengine.org/en/4.7/tutorials/platform/android/android_library.html.

The isolated POC now includes a local Godot project and a host Activity. Its Android 12 instrumentation test checks the expected crimson scene pixel, not just API class linkage. The Android 12 instrumentation test has now passed and verifies the expected crimson sample-scene pixel as well as host API linkage. Godot is therefore the evidence-backed fallback candidate for the Stage 12 role assessment, not an automatically selected or production-approved engine. Stage 12 must make the role decision; Stage 15 remains the separate controlled production integration stage. Cocos remains BLOCKED and is not added to the production APK.
