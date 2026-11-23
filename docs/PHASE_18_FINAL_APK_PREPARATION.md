# Phase 18 — Final APK Preparation (Pre-build Gate)

## Scope and stop condition

This phase prepares the existing WOLF OFFLINE VIDEO application for a final, signed APK. It does not create the final APK. The final release build, release installation test, acceptance run, and delivery remain blocked until the user explicitly directs the build to begin.

Repository: `WolfCodeS-sigma/WOLF-OFFLINE-VIDEO`  
Preparation branch: `release/phase-18-final-apk-preparation`  
Parent: accepted Phase 17 head `6151f03e50cab2b09476374cc58a764a1ac0bd64`

## Preservation boundary

- Phase 17 is treated as accepted by the user.
- This branch is based directly on the accepted Phase 17 head; Phase 17 is not merged into `main`.
- `main` remains untouched.
- No application production source, earlier phase implementation, existing project data format, or runtime behavior is changed in this preparation pass.
- No final APK build, release signing, keystore generation, release installation, or release acceptance test is performed here.

## Release configuration audit

Current values read from `app/build.gradle.kts`:

| Setting | Current value | Preparation decision |
|---|---|---|
| Namespace | `com.wolfcodes.offline` | Preserve |
| Application ID | `com.wolfcodes.offline` | Preserve |
| minSdk | 26 | Preserve |
| compileSdk | 35 | Preserve |
| targetSdk | 35 | Preserve |
| versionCode | 1 | Preserve unless a separately approved release version is supplied |
| versionName | `1.0.0` | Preserve unless a separately approved release version is supplied |
| Java/Kotlin target | 17 | Preserve |
| Direct runtime dependency | LiteRT-LM Android 0.18.0 | Preserve |

The project uses Gradle 8.13 in its Android CI. No ABI filter is declared, so no architecture restriction should be introduced during release preparation. No custom release signing configuration or release-specific R8/ProGuard rules are currently declared. The release configuration must not enable shrinking/obfuscation without dedicated compatibility verification, particularly for the on-device LiteRT-LM runtime.

## Permissions, connectivity, and local data review

- The application manifest requests `RECORD_AUDIO` only; it does not declare `INTERNET` or `ACCESS_NETWORK_STATE`.
- The application label is `WOLF OFFLINE VIDEO`; the launcher activity is `MainActivity`.
- `android:allowBackup="false"` is set.
- Image selection uses local document/content URIs; rendered output is written through Android media facilities. No broad storage permission is declared in the manifest.
- Project records, voice data, model files, and other working records are held under app-private `filesDir` locations in the inspected stores.
- App-private storage and disabled Android backup reduce exposure, but the inspected project/story/voice records are not encrypted at rest by an application-level encryption layer. This is a known security limitation, not something to silently change in a release-preparation pass because encryption would require a carefully tested migration and could affect existing user data.
- No API key or remote-service credential is required by the documented offline path. The release-signing private key is a separate critical secret and must never be committed to the repository, included in the APK, or printed in CI logs.

## Device compatibility and test profile

The target device remains Realme 7, Android 12 (API 31), RMX2163, with a 1080×2400 display. Future emulator checks must approximate the phone's actual capability profile as closely as the available emulator permits—not merely match Android version and display size. Record and align CPU architecture/performance class, available RAM, graphics acceleration/GPU constraints, storage pressure, and screen profile where supported. Clearly label emulator-only evidence; it is not physical-device evidence.

## Required gates before the final build

1. Confirm the release signing identity and secure keystore custody. No keystore or signing credentials were supplied or generated during this pass.
2. Decide whether the known lack of application-level encryption at rest is acceptable for this release or requires a separately scoped, backward-compatible protection change.
3. Confirm the intended release version values; current project values remain unchanged at 1 / 1.0.0.
4. Add a release signing configuration that reads secrets only from a protected CI secret store or another explicitly approved secure mechanism. Never place passwords or keystore bytes in source control.
5. Run the offline contract guard, lint, JVM tests, and release-variant verification before creating the signed artifact.
6. Build the signed release APK only after the explicit user instruction to proceed.
7. Verify APK signature, package ID, version, minimum SDK, manifest permissions, ABI contents, and artifact checksum.
8. Install and exercise the release APK using a close Realme 7 capability-matched emulator profile, then perform the agreed physical-device checks when available. Do not describe emulator results as physical-device results.

## Current preparation result

Pre-build repository and configuration review is recorded. No release APK has been built. The final build is not yet ready to execute because the signing identity/keystore custody and the local-data-at-rest security decision are not yet established. These are explicit gates, not values to guess.
