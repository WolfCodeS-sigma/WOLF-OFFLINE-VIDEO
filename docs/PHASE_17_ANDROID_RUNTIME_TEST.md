# Phase 17 — Android Runtime Validation

## Scope and preservation

- Repository: `WolfCodeS-sigma/WOLF-OFFLINE-VIDEO`.
- Test branch: `test/phase-17-android-runtime`.
- Base: approved Phase 16 branch `feature/phase-16-offline-runtime-closure`, commit `2094f6c7a3a64d6becc3f80602c4b702ba69153f`.
- Phase 17 remains isolated to its dedicated runtime workflow, its Bash smoke-test helper, and this test record.
- Shared CI maintenance updates only the GitHub Actions dependencies in `.github/workflows/offline-quality.yml`; workflow triggers and the Phase 16 quality-gate commands are unchanged.
- No application production source, Phase 16 runtime guard, lint/test definitions, or project data formats are changed.
- The Gradle action uses the basic GitHub Actions cache provider; no external commercial cache service is enabled.
- No merge into `main` is performed.

## Automated Android emulator validation

The dedicated GitHub Actions workflow `.github/workflows/phase-17-android-runtime.yml` performs the following:

1. Uses JDK 17, Android SDK 35, and Gradle 8.13.
2. Pins the JavaScript-based GitHub Actions to verified Node.js 24-compatible release commits.
3. Uses the basic GitHub Actions cache provider for Gradle.
4. Runs the Phase 16 offline runtime guard, Android lint, and JVM unit tests again.
5. Builds the debug APK with `:app:assembleDebug`.
6. Starts an Android 35 Google APIs x86_64 emulator.
7. Invokes `.github/scripts/phase-17-android-runtime-smoke.sh` as one shell command. The helper owns its shell state and multi-line control flow; it is intentionally not expanded into multiple lines in the emulator action input.
8. Installs the APK.
9. Enables airplane mode through Android's ConnectivityService shell command (`cmd connectivity airplane-mode enable`), then explicitly disables Wi-Fi. It disables mobile data when the emulator exposes a telephony service; emulator images without a phone service have no mobile-data radio to disable. It verifies airplane mode and Wi-Fi are off. It does not attempt to send Android's protected airplane-mode broadcast directly.
10. Launches the application through `com.wolfcodes.offline/.MainActivity`.
11. Confirms the app process remains alive and the UI hierarchy contains `WOLF OFFLINE VIDEO`.
12. Captures a screenshot and uploads the APK plus successful emulator evidence as 30-day workflow artifacts.

The emulator job is an automated launch smoke test. It is not a physical-device test and does not by itself prove successful film rendering or all application workflows.

## Physical-device acceptance — pending

The target physical device from the previously agreed test plan is the user's Realme 7. This stage cannot be declared fully accepted until the APK is installed and exercised on that physical phone. The following checks remain to be performed and recorded:

- Install and launch the generated APK on the Realme 7.
- Enable airplane mode and verify Wi-Fi/mobile data remain off while using the application.
- Create and save a project from manually entered story text.
- Open the project again after force-stopping the app and after restarting the phone.
- Select local images through the Android document picker.
- Record, stop, preview, approve, and recover narration segments.
- Render a short multi-scene film and confirm the MP4 is playable in the device media collection.
- Exercise chapter checkpoint recovery after an interrupted render.
- Run a longer multi-scene render, recording elapsed time, battery change, device temperature/thermal warnings, memory pressure, and any crashes.
- Verify that the app remains usable after returning from background and reopening saved projects.

A full 30-minute film export, if attempted, must be separately identified in the results; a short render is not evidence of 30-minute stability.

## Reporting rules

Report emulator build, install, offline launch, process survival, and visible-screen checks separately from physical-device checks. Do not describe the phase as fully passed while any required physical-device acceptance item remains untested. Do not infer temperature, battery, memory, or long-render performance from emulator results.
