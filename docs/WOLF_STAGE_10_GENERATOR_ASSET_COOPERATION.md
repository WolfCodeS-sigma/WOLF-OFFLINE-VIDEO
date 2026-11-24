# WOLF Stage 10 — Generator and Asset Cooperation

**Stage:** 10  
**Design baseline:** WOLF Procedural Generation Library 1.3  
**Status:** Implemented cooperation core and automated verification  
**Runtime boundary:** In-app, offline, statically linked, renderer-neutral descriptors

## 1. What Stage 10 implements

Stage 10 introduces a first, deliberately narrow cooperation path between approved Stage 08 registry records and three statically compiled WPGL environmental generators:

1. **Atmosphere** — produces a bounded fog-density and drift descriptor.
2. **Weather** — consumes the atmosphere descriptor and produces a bounded rain descriptor coupled to that atmosphere.
3. **Lighting** — consumes both preceding descriptors and produces bounded exposure, fog-scattering, and wet-reflection values.

The chain is fixed in code as ATMOSPHERE → WEATHER → LIGHTING_EFFECT. The lighting stage records the digests of both upstream outputs. Identical request identity, inputs, generator versions, and master seed reproduce the same logical descriptors. A frame can be regenerated independently; prior frames are not needed.

These are renderer-neutral, transient descriptors. Stage 10 does not add a new camera system, create realistic people/creatures/props/buildings, write generated assets to disk, or change the production renderer/exporter path.

## 2. Stage 08 cooperation and fail-closed selection

The library calls WolfAssetRegistry.approvedAssets(request) and explicitly filters the returned records to WolfAssetCategory.GENERATOR. Each required generator must then match the closed in-app binding exactly:

- registry asset ID and semantic version;
- canonical generator profile and dependency profile;
- implementation binding ID, implementation version, ABI, parameter schema, output kind, and seed algorithm;
- source repository path, Git blob revision, and SHA-256 source fingerprint;
- exact target runtime version and approved use scope.

The selected generator must be APPROVED through Stage 08, have budgetStatus == PASS, carry finite, non-negative resource measurements with evidence, and declare the exact upstream generator dependency IDs and versions required by its family. Atmosphere has no generator dependency; weather pins atmosphere 1.0.0; lighting pins atmosphere 1.0.0 and weather 1.0.0. Missing, extra, or version-mismatched dependencies are rejected. The production built-in bindings currently retain zero/PENDING resource ceilings and therefore cannot run. They may only be enabled after actual target-runtime measurements are reviewed and added to the binding and Stage 08 record. Each measured high-water value is compared with its corresponding hard limit. Missing, non-numeric, UNKNOWN, PENDING, over-limit, or unsupported values block production dispatch.

The caller supplies a finite resource envelope. Production construction additionally reads a live Android snapshot through WolfResourceGuard and combines it conservatively with the request snapshot (minimum memory/storage, maximum thermal state, and logical-OR low-memory flag); failure to read live state blocks dispatch. Each generator's elapsed wall-clock execution time is checked against both request and approved hard limits after it returns. This detects overruns for the current bounded built-ins; it is not preemptive cancellation, which remains outside this stage until generators expose a cooperative cancellation contract.

The current implementation targets the environment_background scope and requires exact, approved ENVIRONMENT asset pins. A non-environment category cannot be used as an environment target.

## 3. Asset use and output ownership

The generators receive immutable references containing only the approved environment asset ID, version, category, and tags. Every generated descriptor retains those references as provenance. The pipeline never edits, overwrites, copies, or reclassifies the original asset.

Outputs are transient descriptors in memory. They are not promoted to the shared cinematic asset library. If a later stage chooses to bake an output into a reusable file, that file must become a new candidate and pass Stage 08 rights, technical, hash, scope, and realism review independently.

Every returned payload must match the output kind declared by its generator binding and pass family-specific numeric bounds. A generator exceeding its measured execution deadline is rejected. Any generator failure is isolated and returns no partial chain. The caller receives BASE_SCENE_NO_EFFECT, allowing the existing approved base-scene/fallback path to continue without changing film duration, frame count, audio, or timestamps.

## 4. Static implementation and source provenance

All three implementations are compiled into the Android application and enumerated in WolfBuiltInGeneratorCatalog. The registry contains authorization/provenance metadata only. It cannot name a class to instantiate and cannot load executable code. There is no reflection-based class-name loading, dynamic DEX/JAR/SO loading, downloaded script, or network lookup.

Each built-in binding pins both the Git blob revision and the raw-file SHA-256 of its Kotlin source. The JVM regression test recomputes both fingerprints from the checked-out source and compares them with the static binding. This validates the source fingerprint in CI; it does not claim that WolfAssetFileVerifier hashes compiled DEX bytes.

## 5. Verification coverage

The Stage 10 JVM suite covers:

- approved generator and environment resolution, fixed cooperation order, upstream digest handoff, and asset immutability;
- repeatability for the same seed/frame identity, independent frame recovery, and distinct seeded outputs;
- empty generator records, wrong category, pending budgets, altered profiles, missing/pending measurements, and measurements above hard limits;
- caller envelopes above reviewed limits, unsafe request/live Android runtime snapshots, unapproved assets, and asset-version mismatch;
- exact upstream dependency-version pins, wrong payload-kind rejection, and execution-deadline overrun fallback;
- required upstream atmosphere/weather outputs;
- closed static catalog identity and exact source Git blob/SHA-256 fingerprints.

JVM and Android fixtures inject finite TEST_ONLY ceilings separately from the production catalog. These are synthetic structural test limits, not device measurements, production budgets, or values to copy into a real asset record. An Android instrumentation test constructs synthetic registry metadata and uses the instrumentation APK's real AssetManager to verify the referenced test fixture bytes before running the same three-generator cooperation chain.

## 6. Production asset status and explicit limitations

The production app/src/main/assets/wolf_asset_registry.json remains intentionally empty at the Stage 10 starting point. In addition, the production built-in bindings keep resource ceilings at zero/PENDING until actual reviewed measurements exist. Consequently, a normal production registry loaded by WolfProceduralGenerationLibrary.loadBundled() has no approved generator records and no production-ready resource bindings; it fails closed to the base-scene/no-effect fallback.

The test registry records and byte files are synthetic, test-only fixtures. They are not cinematic assets, do not demonstrate visual realism, do not satisfy the real-asset collection target, and are not added to the production APK. No genuine cinematic environment asset has been approved or integrated by this stage.

Stage 10 proves the software contracts and cooperation order only. Visual quality, renderer adapter behavior, actual licensed cinematic asset coverage, and end-to-end rendered scene quality remain for their later stages. The existing production renderer, story/camera planners, audio, frame timing, checkpoints, and MP4 export were not changed. Stage 10 does not add camera-motion candidates; StoryDirectorEngine and OfflineMotionEngine remain the only camera authorities.
