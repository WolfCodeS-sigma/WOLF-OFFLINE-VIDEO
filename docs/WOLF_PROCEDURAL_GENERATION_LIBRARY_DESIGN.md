# WOLF Procedural Generation Library — Stage 09 Design

**Design ID:** WOLF-PROCGEN-DESIGN-001  
**Version:** 1.1  
**Status:** Stage 09 design baseline  
**Depends on:** Stage 06 asset policy, Stage 07 inventory, Stage 08 Asset Registry & Quality Gate  
**Runtime model:** Android application-integrated, deterministic, offline-first  
**Implementation boundary:** This document defines the architecture and contracts. Runtime integration and cross-generator verification belong to Stage 10 and later stages.

---

## 1. Mission and non-negotiable rules

The WOLF Procedural Generation Library (WPGL) supplies controlled, repeatable procedural effects and motion that strengthen a serious cinematic-horror scene when suitable approved media is unavailable or when an environmental effect is needed.

The library is not a substitute for a cinematic asset library. It must not turn primitive geometry into a fake realistic person, creature, prop, vehicle, building, or other semantic subject. Realistic characters, creatures, environments, and props must come from separately sourced assets that pass the WOLF Asset Registry & Quality Gate.

Mandatory rules:

1. **Offline by construction:** no runtime network calls, downloads, remote generation APIs, license-server checks, or cloud dependencies.
2. **Deterministic by contract:** identical canonical inputs, generator version, seed, and supported runtime version must reproduce the same logical output and timeline. Cross-engine pixel-identical output is not promised until separately proven.
3. **Fail closed for eligibility:** only a generator explicitly approved and scoped through Stage 08 may be selected for production use.
4. **No quality bypass:** generated reusable files are candidates, not automatically approved library assets. They must pass the same rights, realism, technical, dependency, hash, and scope checks as other assets.
5. **No semantic fabrication:** no procedural human/creature faces, bodies, silhouettes, anatomy, realistic props, or architecture presented as finished cinematic assets.
6. **No silent substitution:** if a generator cannot satisfy a request, the scene planner must use a safe approved asset, a separately approved user image, an existing safe atmospheric fallback, or a documented neutral fallback. It must not invent an unrelated subject.
7. **Preserve prior paths:** the existing renderer, exporter, project records, audio, user-image store, frame timing, checkpoints, and fallback behavior remain independent until an explicitly tested integration stage changes them.
8. **Measured resource claims only:** unknown performance budgets remain pending; this design does not invent GPU, memory, polygon, texture, or APK-size limits.
9. **No unbounded production execution:** every production request must carry finite, reviewed resource limits and a current runtime safety snapshot. A missing envelope, an unbounded limit, or a `PENDING` production budget blocks execution; it is never interpreted as unlimited capacity.

## 2. What the library generates

### 2.1 Initial supported families

| Family ID | Scope | Examples | Restrictions |
|---|---|---|---|
| `ATMOSPHERE` | Environmental atmosphere | fog density fields, haze layers, dust motes, subtle airborne particles | Must preserve scene readability and avoid decorative/cartoon appearance |
| `WEATHER` | Weather overlays | rain streaks, wind direction fields, restrained mist movement | Must be driven by explicit scene parameters; no arbitrary weather inference |
| `ENVIRONMENT_MOTION` | Motion of already-approved environmental elements | foliage sway, cloth-like environmental motion, environmental drift | May animate an existing approved asset; may not invent a realistic tree/prop/character |
| `CAMERA_MOTION` | Candidate cinematic camera-motion profiles | bounded dolly, pan, tilt, restrained handheld, reveal timing profiles | Profiles are proposals only; `StoryDirectorEngine` owns shot-level camera/lens/composition intent and shot-frame budgets, while `OfflineMotionEngine` owns scene-frame allocation and depth-aware deterministic per-frame camera transforms |
| `LIGHTING_EFFECT` | Non-destructive lighting/post effects | vignette, exposure pulse, restrained volumetric-style overlays, signal interference | Must not obscure required story content, captions, or watermark |

These are the initial design families, not a claim that every generator already exists or is approved. New families require a documented scope, quality rules, rights review, resource profile, and tests before use.

### 2.2 Explicitly excluded from procedural fabrication

- Realistic human or creature models, faces, bodies, skeletons, skin, or character animation.
- Realistic props, weapons, vehicles, furniture, buildings, interiors, or complete environments.
- Any placeholder primitive that could be mistaken for a finished subject.
- Audio/music generation in this stage; audio remains governed by its own planned audio stage.
- Automatic generation or modification of user-selected images.
- Generators requiring online models, cloud inference, or runtime asset downloads.

A later proposal may add a category only through a separate design review and explicit acceptance criteria. It cannot silently expand these exclusions.

## 3. Architectural layers

The library is designed as six separable layers. Their eventual Kotlin package/file names are implementation choices for Stage 10 or later; this design does not prematurely bind the architecture to a renderer.

1. **Generator Catalog** — stable generator IDs, versions, family, supported capabilities, dependencies, and approval scope. Catalog entries are resolved from the app-bundled registry; they are never discovered from the network.
2. **Policy Resolver** — calls `WolfAssetRegistry.approvedAssets(request)` first, then filters the returned records to `category == WolfAssetCategory.GENERATOR` before exposing anything to the generator catalog. The Stage 08 method intentionally returns every approved asset category; it is not itself a generator-only query. The resolver must never select a `CHARACTER`, `ENVIRONMENT`, `PROP`, `SHADER`, or other non-`GENERATOR` record as executable generator code. It also verifies the `generatorProfile` and `dependencyProfile` metadata against the exact generator ID/version, family, parameter-schema ID/version, output kinds, dependencies, seed algorithm, and supported runtime declared by the catalog. Only an exact, approved, in-scope, hash-verified generator record can be selected.
3. **Request Validator** — validates the scene contract, parameter schema, seed, timeline, dimensions, coordinate conventions, and resource envelope before execution.
4. **Deterministic Execution Core** — executes a pure generation function from canonical input. It owns seed derivation, stable ordering, parameter normalization, cancellation boundaries, and output identity.
5. **Renderer Adapters** — translate renderer-neutral effect descriptors into a specific backend. An adapter must declare the exact runtime/renderer version it was tested against. Stage 09 does not certify Godot, Filament, Cocos Creator, libGDX, KorGE, Lottie, or SceneView.
6. **Output Audit and Handoff** — records generator/version, seed, canonical parameters, runtime version, output type, warnings, resource measurements, and provenance. Reusable packaged outputs are submitted as registry candidates; they are not auto-approved.

The library must remain independent of the current Canvas implementation. The existing `WolfProceduralMotionEngine` is a legacy renderer implementation and a source of current behavior to preserve, not the architectural API for every future engine.

### 3.1 Stage 08 generator-resolution contract

The production resolver follows this fail-closed sequence:

1. Build the exact `WolfAssetUseRequest` using the requested runtime ID, exact runtime version, and intended use scope.
2. Ask the bundled `WolfAssetRegistry.approvedAssets(request)` for records that pass the existing Stage 08 rights, realism, review, technical, scope, dependency, file-size, and SHA-256 checks.
3. Filter that result to `WolfAssetCategory.GENERATOR` only. Filtering is mandatory because `approvedAssets()` returns all approved categories, not just generators.
4. Match the registry record's `assetId`, version, `generatorProfile`, `dependencyProfile`, compatible runtime version, approved scope, and dependency versions against the requested catalog descriptor. Any absent, unknown, conflicting, or mismatched field rejects the record.
5. For production execution, additionally require `budgetStatus == PASS` and complete, finite reviewed resource measurements for the generator. Stage 08 may allow a reviewer-accepted `PENDING` budget with a warning; WPGL must still block that generator from production execution until the measurements pass. Such entries may appear only in isolated Stage 10 test fixtures with explicit finite test limits, never in a production resolver result.
6. Pin the selected registry asset ID, exact version, file hashes, dependency versions, runtime ID/version, and scope for the entire request. A later registry change cannot silently change an in-flight render.

A missing or empty generator set is a normal safe result: WPGL reports that no eligible generator is registered and returns control to the scene planner's approved fallback path. It must not fall back to a non-generator registry category or load executable code from an unregistered location.

### 3.2 Existing camera and frame-allocation authority

The current production path deliberately separates shot intent from per-frame camera execution:

- `StoryDirectorEngine` owns the semantic shot plan, including each shot's camera/lens description, composition, motion intent, and duration weighting. Its `allocateShotFrames` function owns the distribution of a scene's frame budget across that scene's shots.
- `OfflineMotionEngine` owns deterministic scene-level frame allocation through `allocateFrames` and computes bounded per-frame camera offsets, zoom, and rotation through `cameraFrame` (and its related helpers) only when safe depth layers are available.
- `OfflineProductionEngine` integrates those decisions into the frame render. When safe depth layers are unavailable, the existing safe-still-image path intentionally avoids camera warping and preserves the still image; it does not create an alternate procedural camera-motion path. The renderer consumes the accepted shot plan and, when applicable, the resolved depth-aware camera frame; it does not establish an independent camera-motion authority.
- A WPGL `CAMERA_MOTION` generator may return a bounded, data-only candidate profile. `StoryDirectorEngine` must accept or reject its shot-level intent against the shot plan; `OfflineMotionEngine` must validate and resolve any accepted per-frame movement. WPGL and renderer adapters may not write camera transforms directly, replace shot plans, change scene/shot frame counts, or alter timestamps. If either existing owner does not accept the candidate, it is discarded and the existing approved fallback behavior continues.

This is a handoff between existing owners, not a second camera system. Any future reassignment of either responsibility requires a separate design decision and regression proof.

### 3.2 Existing camera and frame-allocation authority

The current production path deliberately separates shot intent from per-frame camera execution:

- `StoryDirectorEngine` owns the semantic shot plan, including each shot's camera/lens description, composition, motion intent, and duration weighting. Its `allocateShotFrames` function owns the distribution of a scene's frame budget across that scene's shots.
- `OfflineMotionEngine` owns deterministic scene-level frame allocation through `allocateFrames` and computes the bounded per-frame camera offsets, zoom, and rotation through `cameraFrame` (and its related camera helpers).
- `OfflineProductionEngine` integrates those existing decisions into the frame render. The renderer consumes the accepted shot plan and resolved camera frame; it does not establish an independent camera-motion authority.
- A WPGL `CAMERA_MOTION` generator may return a bounded, data-only candidate profile. `StoryDirectorEngine` must accept or reject its shot-level intent against the shot plan; `OfflineMotionEngine` must validate and resolve any accepted per-frame movement. WPGL and renderer adapters may not write camera transforms directly, replace shot plans, change scene/shot frame counts, or alter timestamps. If either existing owner does not accept the candidate, it is discarded and the existing approved fallback behavior continues.

This is a handoff between existing owners, not a second camera system. Any future reassignment of either responsibility requires a separate design decision and regression proof.

`generatorProfile` must be a versioned, validated `WOLF-CANON-JSON-1` object containing at least the canonical generator ID, semantic version, family ID, parameter-schema ID/version, seed-algorithm ID, supported output kinds, execution contract version, and a `hardLimits` object. `hardLimits` declares finite integer maxima for `maxWorkingSetBytes`, `maxTemporaryStorageBytes`, `maxReusableOutputBytes`, `maxTransientElementsPerFrame`, `maxRetainedFrameCount`, and `maxExecutionMillis`; `maxNativeGpuBytes` is also required when that runtime exposes a reliable measurement. `resourceMeasurements` must contain the corresponding measured high-water values (`peakWorkingSetBytes`, `peakTemporaryStorageBytes`, `maxReusableOutputBytesObserved`, `maxTransientElementsPerFrameObserved`, `maxRetainedFrameCountObserved`, and `maxExecutionMillisObserved`, plus `peakNativeGpuBytes` where measurable) and a non-empty `measurementEvidenceReference`. Every required observation must be a finite non-negative base-10 integer no greater than its declared hard limit. Missing, unparsable, `UNKNOWN`, or `PENDING` values, absent evidence, or an exceeded limit block production dispatch even if the general Stage 08 gate returns an approved result. `dependencyProfile` must describe the exact dependency closure and versions, consistent with the Stage 08 `dependencies` map. These are values in the existing registry schema; they do not create a second approval database.

## 4. Core request and result contracts

A generation request must contain:

- `requestId`: stable ID for one logical request.
- `generatorId` and exact `generatorVersion`.
- `sceneId`, `shotId`, and intended use scope.
- `seed`: explicit unsigned 64-bit seed; no implicit wall-clock or device-derived seed.
- `timeline`: integer frame index, total frame count, and rational frame rate (numerator/denominator). Avoid floating-point wall-clock time as the authoritative timeline.
- `canvas`: width, height, pixel aspect ratio, and declared coordinate origin.
- `parameters`: typed, schema-versioned values with explicit units and bounded ranges.
- `runtimeTarget`: exact engine/renderer identifier and version.
- `resourceEnvelope`: required finite hard limits for peak working memory, temporary storage, reusable output bytes, transient elements per frame, retained frame/cache count, and execution/cancellation deadline. Include native/GPU memory where it can be measured. The request also carries the current runtime safety snapshot (available memory, free storage, thermal state, and applicable `WolfResourceGuard` result). Every mandatory cap must be positive, finite, reviewed, and no greater than the generator's approved descriptor limits; missing, unknown, infinite, or `PENDING` values reject execution before allocation. No default of “unlimited” is permitted.

A generation result must contain:

- `requestId`, generator ID/version, and canonical-input digest.
- Deterministic seed and derived sub-seeds.
- `outputKind`: transient effect descriptor, motion curve, renderer-neutral layer description, or reusable file.
- Output payload or a stable reference to it.
- `provenance`: generator identity/version, dependency versions, seed, canonical parameter digest, and runtime target.
- `warnings`, validation findings, measured resource observations, and execution status.
- Whether the result is transient-only or a reusable candidate requiring registry ingestion.

No result may claim approval merely because generation completed successfully.

### 4.1 Deterministic seed derivation

Sub-generators use the versioned algorithm `WOLF-SEED-1`:

1. Serialize the fields in this exact order: `masterSeed` (unsigned 64-bit big-endian), `sceneId`, `shotId`, `generatorId`, `generatorVersion`, and `subGeneratorKey`.
2. Encode each string as UTF-8 preceded by its unsigned 32-bit big-endian byte length. Do not normalize or reinterpret IDs after validation.
3. Compute SHA-256 over the concatenated byte sequence.
4. Interpret the first eight digest bytes as an unsigned 64-bit big-endian sub-seed.

The canonical parameter digest uses the versioned `WOLF-CANON-JSON-1` encoding. Its rules are exact:

- Input must be valid UTF-8 JSON with no duplicate object keys, invalid Unicode surrogate sequences, NaN, or infinity.
- Object keys are sorted by unsigned lexicographic order of their UTF-8 byte sequences. Array order is preserved.
- Unicode string values are preserved as supplied; no NFC/NFD or locale-dependent normalization is performed. UTF-8 encoding is mandatory. In JSON strings, the double-quote character is escaped by prefixing one backslash, a backslash character is escaped as two backslashes, and U+0000 through U+001F use the standard short escape where defined or a backslash followed by lowercase `u00xx`; all other valid Unicode scalar values are emitted as UTF-8.
- JSON numbers are parsed as exact base-10 values, not binary floating point. Emit plain decimal notation without an exponent, remove unnecessary leading integer zeros and trailing fractional zeros, remove a now-empty decimal point, emit a minus sign only for negative non-zero values, and canonicalize every negative zero to `0`. Equivalent numeric values therefore have one byte representation (for example, `1`, `1.0`, and `1e0` canonicalize identically).
- Parameter schemas must bound string lengths, numeric precision/scale, nesting depth, array length, and total canonical input bytes before hashing; values outside those declared bounds are rejected.
- Booleans and null use the exact JSON literals `true`, `false`, and `null`.

The digest is SHA-256 over the resulting UTF-8 byte sequence. Every result records `WOLF-CANON-JSON-1`; changing any canonicalization rule requires a new canonicalization ID and verification fixtures.

Never use Kotlin/JVM object hash codes, unordered map iteration, device time, random UUIDs, or unseeded random APIs as reproducibility inputs. A future algorithm change requires a new seed-algorithm ID and generator compatibility declaration.

The same seed and request must produce the same logical effect descriptor and keyframe values on the same supported generator/runtime version. Any intentional output change requires a generator version change and a new verification run.

### 4.2 Timeline and frame determinism

- Frame index is the authoritative time coordinate.
- A generator must not independently choose export duration or alter the film's frame allocation.
- Motion curves must clamp or explicitly define behavior outside their declared interval.
- Chapter recovery must be able to regenerate a frame from its scene/shot/frame identity without replaying all prior frames.
- Generator execution must not mutate project timing, audio duration, encoder timestamps, or checkpoint state.

## 5. Generator descriptor and lifecycle

Each generator has a stable descriptor with:

- generator ID and semantic version;
- family and concise purpose;
- parameter-schema ID/version;
- output kinds and supported dimensions/frame-rate ranges;
- supported renderer/runtime IDs and exact tested versions;
- dependencies pinned to exact generator or asset versions;
- deterministic/recovery capability declaration;
- reviewed finite hard limits for CPU execution time, peak working memory, temporary storage, reusable output bytes, transient elements per frame, and retained frame/cache count; native/GPU limits are included where measurable;
- measured CPU, memory, GPU, output-size, and frame-time observations, kept distinct from the hard limits;
- allowed use scopes and prohibited use cases;
- source/license/provenance references;
- test evidence and lifecycle state.

The generator source itself is a software asset. It must be recorded under Stage 08 using category `GENERATOR`, with its own actual source, license, version, dependency closure, file hashes, technical profile, review evidence, and approved scopes. No source license is inferred from the generated output.

Lifecycle:

`CANDIDATE → UNDER_REVIEW → APPROVED`

Failure or withdrawal may move a generator to `REJECTED`, `QUARANTINED`, or `RETIRED`. These are the existing Stage 08 lifecycle values; the design does not introduce a new `DRAFT` registry state. Only `APPROVED` generators can be resolved for production requests. The current Stage 08 gate remains authoritative; this design adds no parallel approval database.

## 6. Generated output governance

Two output classes are distinguished:

### A. Transient runtime effects

Examples: per-frame fog density, rain positions, camera keyframes, or a temporary lighting pulse. These are not automatically added to the shared cinematic asset library. They remain bound to the approved generator, scene request, and exact timeline. Their provenance and execution diagnostics must be available for audit/recovery.

### B. Reusable generated files

Examples: a baked texture, cached environment layer, or a reusable animation/data file. These are new asset candidates. Before they can be reused as library content they must receive:

- a stable asset ID and version;
- generator provenance and exact seed/parameter digest;
- all source/dependency license evidence;
- output file manifest, byte size, and SHA-256;
- category-specific technical profile;
- visual/realism review and approved scope;
- exact runtime compatibility and measured resource status;
- explicit Stage 08 registry review and approval.

Generated output does not inherit an automatic license grant from the generator. Output rights and restrictions must be documented according to the source licenses and the Stage 06 policy. If the rights outcome is unclear, the output remains unapproved.

## 7. Quality and visual guardrails

Every visual generator must define:

- intended cinematic role and permitted shot distances;
- output bounds, scale, opacity, contrast, motion speed, and density ranges;
- visual failure examples and rejection criteria;
- interaction rules with the base image, approved assets, captions, and WOLF watermark;
- safe behavior when inputs or dependencies are missing.

Hard stops include cartoon/toy/comic/mascot output, primitive human/creature/prop fabrication, unreviewed stylization, visible glitches that damage the scene, unsafe frame cropping, excessive flicker, and effects that conceal important story information.

Text captions and the watermark must remain in their established topmost composition layer. Procedural effects may not overwrite or modify user media.

## 8. Failure handling and fallback order

A generator failure must be isolated to that generator/request. It must not terminate the entire film export unless the existing production pipeline independently determines that continuing would corrupt output or endanger device resources.

Fallback order:

1. another explicitly approved generator that satisfies the same request and scope;
2. a matching Stage 08 approved asset;
3. an approved user-selected project image, unchanged and within its existing project scope;
4. the existing safe atmospheric-only fallback;
5. a neutral no-effect/base-scene result.

Never fall back to a cartoon, primitive person/creature, unrelated prop, fabricated architecture, or an unapproved generated output. A fallback must preserve the original scene duration, frame count, audio timing, and export timeline.

Cancellation, low-memory, thermal, and storage safeguards remain under the existing resource guard. The procedural library may report cost estimates and checkpoints, but it must not bypass or weaken those safeguards.

## 9. Resource and performance contract

The design requires measurement fields, not invented budgets:

- generation CPU time and total execution time;
- peak and retained heap allocation;
- native/GPU memory where measurable;
- draw calls and renderer-specific workload where applicable;
- output bytes and temporary storage;
- frame-time distribution and dropped/late-frame observations;
- cancellation and recovery latency.

Budgets remain `PENDING` until measured on the target integration and later device stage. A `PENDING` budget is not an executable production budget: WPGL may describe or test the generator with finite isolated test limits, but production dispatch is blocked until Stage 08 records `PASS` with complete reviewed measurements. Stage 10 must test both the blocked-pending path and the passing bounded path. Even after that, passing measurements on a CI emulator do not claim final Realme 7 approval. Physical-device testing remains deferred to Stage 21.

## 10. Security, bounds, and privacy

- No network access or remote code loading.
- No dynamic executable scripts, downloaded shaders, or untrusted generator plugins.
- Generator parameters must be typed, range-checked, and bounded before allocation.
- Avoid unbounded recursion, unbounded particle counts, unbounded frame caches, and unbounded output files.
- File output must remain under the app's designated local cache/output root and use safe relative paths.
- Temporary outputs must be owned, named, and cleaned without touching project files or user media.
- User-selected images and project records remain project-scoped; they are not training data or generator inputs unless the project explicitly supplies them for that scene.
- Diagnostics must not include private image contents, narration, or unrelated project data.

## 11. Relationship to existing WOLF components

| Existing component | Stage 09 design relationship |
|---|---|
| `WolfProceduralMotionEngine` | Preserve its current behavior as a legacy implementation; future adapters may wrap selected safe effects after Stage 10 verification. |
| `WolfProceduralVisualSystem` | Keep its compatibility entry point; do not replace it in this design stage. |
| `StoryDirectorEngine` | Sole authority for semantic scene/shot plans, shot-level camera/lens/composition intent, motion intent, and shot-frame allocation. It may accept or reject a WPGL camera-profile candidate as shot-plan data. |
| `OfflineMotionEngine` | Sole authority for scene-frame allocation and depth-aware deterministic per-frame camera offsets, zoom, and rotation. If safe depth layers are unavailable, the established still-image fallback remains unwarped. It validates and resolves only candidates accepted through the existing shot-plan contract. Renderers may not add independent camera transforms or change frame allocation. |
| `WolfSceneArtEngine` | Remains the current safe base-art/fallback path; the new library must not bypass it. |
| `WolfAssetRegistry` | Sole approval authority for shared generators and reusable generated assets. |
| `WolfSceneAssetStore` | Continues to own project-scoped user images; no automatic ingestion or mutation. |
| `WolfResourceGuard` | Remains authoritative for device safety and continuation checks. |
| Exporter/checkpoints/audio | No changes to timestamps, duration, frame allocation, audio, checkpoint formats, or MP4 publication are authorized by this design. |

## 12. Stage 10 verification plan

Stage 10 must implement focused tests before any production renderer integration:

1. Same request/seed/version produces the same canonical output digest.
2. Different seeds produce distinct outputs where the generator contract says randomness is meaningful.
3. Frame N can be regenerated independently after chapter recovery.
4. Invalid parameters, unknown schema versions, unsupported dimensions, and out-of-range values fail before expensive allocation.
5. Missing/unapproved generator records and dependency version mismatches fail closed through Stage 08.
6. Generated reusable files remain candidates until separately approved; no auto-promotion occurs.
7. Generator failure invokes only the defined fallback order and preserves total frames, duration, audio timing, and project media.
8. Cancellation and resource limits are honored without leaking temporary files.
9. Renderer adapters declare and enforce exact tested runtime versions.
10. Existing Stage 01–08 regression suites continue to pass unchanged.
11. A non-`GENERATOR` record that passes the general registry gate is still excluded from the WPGL executable catalog; an approved `GENERATOR` record must match exact profile, dependency, scope, runtime, version, hash, and `PASS` budget requirements.
12. `PENDING` generator budgets and missing/unknown/infinite resource limits are blocked before allocation in production; finite test-only envelopes remain isolated from production dispatch.
13. A `CAMERA_MOTION` candidate cannot directly mutate the renderer camera, shot plan, or frame timeline; `StoryDirectorEngine` must accept/reject shot-level intent and `OfflineMotionEngine` may resolve per-frame camera movement only within the existing depth-aware path and frame allocation. If depth layers are unavailable, the safe still-image fallback remains unwarped.
14. Missing, unparsable, unknown, or over-limit resource measurements (including absent measurement evidence) block production dispatch; valid finite measurements are checked against every declared hard limit before allocation.

## 13. Stage 09 acceptance criteria

Stage 09 is complete only when:

- The library mission, scope, exclusions, and initial generator families are explicit.
- Architecture layers and boundaries are documented independently of any single renderer.
- Request/result, deterministic seed, frame timeline, descriptor, and provenance contracts are defined.
- Generator source and reusable outputs are governed by the Stage 08 registry; WPGL filters executable records to `GENERATOR` only and has no approval bypass.
- Production execution requires `PASS` resource budgets and finite reviewed limits; `PENDING` is never treated as unlimited or production-safe.
- `StoryDirectorEngine` remains the authority for shot-level camera intent and shot-frame allocation; `OfflineMotionEngine` remains the authority for scene-frame allocation and depth-aware per-frame camera transforms. WPGL camera motion is candidate-only and must pass through both existing owners.
- Realistic human/creature/prop/environment fabrication is explicitly prohibited.
- Failure fallback, offline/privacy/security constraints, and resource measurement requirements are defined.
- Existing components and protected behaviors are mapped with no production integration changes.
- Stage 10 tests and handoff criteria are concrete and traceable.

This stage defines the design baseline only. It does not claim that new generator modules, engine adapters, generated assets, or runtime integrations have been implemented or certified.

---

**Handoff:** Stage 10 may implement generator modules and run cross-generator cooperation tests against this contract. It must first prove generator-only registry filtering, exact profile/version/scope matching, bounded production dispatch, deterministic canonicalization fixtures, and candidate-only camera motion passed through the existing StoryDirectorEngine and OfflineMotionEngine authority boundaries. It must not silently widen the approved scope or modify the protected Stage 01–08 production paths.
