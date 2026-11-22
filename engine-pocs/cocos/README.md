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
