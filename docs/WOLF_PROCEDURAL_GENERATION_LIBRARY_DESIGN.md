# WOLF Procedural Generation Library — Stage 09 Design

**Design ID:** WOLF-PROCGEN-DESIGN-001  
**Version:** 1.0  
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

## 2. What the library generates

### 2.1 Initial supported families

| Family ID | Scope | Examples | Restrictions |
|---|---|---|---|
| `ATMOSPHERE` | Environmental atmosphere | fog density fields, haze layers, dust motes, subtle airborne particles | Must preserve scene readability and avoid decorative/cartoon appearance |
| `WEATHER` | Weather overlays | rain streaks, wind direction fields, restrained mist movement | Must be driven by explicit scene parameters; no arbitrary weather inference |
| `ENVIRONMENT_MOTION` | Motion of already-approved environmental elements | foliage sway, cloth-like environmental motion, environmental drift | May animate an existing approved asset; may not invent a realistic tree/prop/character |
| `CAMERA_MOTION` | Cinematic camera transforms | dolly, pan, tilt, restrained handheld, reveal timing | Must remain inside declared safe framing bounds and preserve subject visibility |
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
2. **Policy Resolver** — obtains eligible generator records exclusively through `WolfAssetRegistry.approvedAssets(request)`. It rejects missing, pending, rejected, retired, unregistered, version-mismatched, or out-of-scope generators.
3. **Request Validator** — validates the scene contract, parameter schema, seed, timeline, dimensions, coordinate conventions, and resource envelope before execution.
4. **Deterministic Execution Core** — executes a pure generation function from canonical input. It owns seed derivation, stable ordering, parameter normalization, cancellation boundaries, and output identity.
5. **Renderer Adapters** — translate renderer-neutral effect descriptors into a specific backend. An adapter must declare the exact runtime/renderer version it was tested against. Stage 09 does not certify Godot, Filament, Cocos Creator, libGDX, KorGE, Lottie, or SceneView.
6. **Output Audit and Handoff** — records generator/version, seed, canonical parameters, runtime version, output type, warnings, resource measurements, and provenance. Reusable packaged outputs are submitted as registry candidates; they are not auto-approved.

The library must remain independent of the current Canvas implementation. The existing `WolfProceduralMotionEngine` is a legacy renderer implementation and a source of current behavior to preserve, not the architectural API for every future engine.

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
- `resourceEnvelope`: optional measured limits supplied by the caller; absence means unknown/pending, not unlimited.

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

The canonical parameter digest uses UTF-8 canonical JSON: object keys sorted lexicographically, arrays kept in declared order, strings encoded as UTF-8, booleans/null represented as JSON literals, integers in base-10 without leading zeros, and decimal values normalized to a non-exponent decimal representation with trailing fractional zeros removed. NaN and infinity are invalid. The canonicalization version is recorded with every result.

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
- expected CPU, memory, GPU, and output-size measurement fields;
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

Budgets remain `PENDING` until measured on the target integration and later device stage. Stage 10 can compare generator behavior and reject pathological/unbounded workloads, but it cannot claim final Realme 7 approval. Physical-device testing remains deferred to Stage 21.

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
| `OfflineMotionEngine` | Remains authoritative for film pacing, frame allocation, and camera-motion planning until a separately tested contract is approved. |
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

## 13. Stage 09 acceptance criteria

Stage 09 is complete only when:

- The library mission, scope, exclusions, and initial generator families are explicit.
- Architecture layers and boundaries are documented independently of any single renderer.
- Request/result, deterministic seed, frame timeline, descriptor, and provenance contracts are defined.
- Generator source and reusable outputs are governed by the Stage 08 registry; no approval bypass exists.
- Realistic human/creature/prop/environment fabrication is explicitly prohibited.
- Failure fallback, offline/privacy/security constraints, and resource measurement requirements are defined.
- Existing components and protected behaviors are mapped with no production integration changes.
- Stage 10 tests and handoff criteria are concrete and traceable.

This stage defines the design baseline only. It does not claim that new generator modules, engine adapters, generated assets, or runtime integrations have been implemented or certified.

---

**Handoff:** Stage 10 may implement generator modules and run cross-generator cooperation tests against this contract. It must not silently widen the approved scope or modify the protected Stage 01–08 production paths.
