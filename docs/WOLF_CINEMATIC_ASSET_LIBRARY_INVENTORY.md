# WOLF Cinematic Asset Library — Inventory Baseline

**Inventory ID:** WOLF-CINEMATIC-INVENTORY-001  
**Version:** 1.1  
**Stage:** 07 — WOLF Cinematic Asset Library inventory  
**Inventory status:** Initial repository audit completed; asset acquisition and approval are not claimed  
**Product target:** Serious, realistic, cinematic horror  
**Runtime target:** Offline; assets must be packaged with and versioned as part of the Android application  
**Policy baseline:** WOLF-ASSET-POLICY-001, version 1.0  
**Repository:** WolfCodeS-sigma/WOLF-OFFLINE-VIDEO  
**Audited reference:** `fix/stages-01-06-audit`  
**Audited commit:** `77b0186689b856be92ecc80029e97f487305a425`  
**Audit method:** Git tree and tracked-file inventory, application source review, and asset-path/resource-directory inspection

---

## 1. Executive result

The audited Stage 07 base snapshot (`fix/stages-01-06-audit`) contains **no packaged cinematic media assets**. Its recursive Git tree contains 45 tracked file blobs: 23 production Kotlin files under `app/src/main`, 13 Kotlin JVM test files, and 9 supporting files. No tracked files with common model, texture, image, audio, animation, or font asset extensions were found. The Android `app/src/main/assets` and `app/src/main/res` directories are absent in that baseline.

Therefore, the verified library inventory at this baseline is:

| Inventory measure | Verified count |
|---|---:|
| Approved cinematic library assets | 0 |
| Candidate third-party cinematic assets present in the repository | 0 |
| Verified rigged human characters | 0 |
| Verified creatures / realistic non-human characters | 0 |
| Verified 3D environments | 0 |
| Verified standalone props | 0 |
| Verified PBR materials / texture sets | 0 |
| Verified rigs / skeletons / animation clips | 0 |
| Packaged audio asset files | 0 |
| Packaged font / logo / watermark files | 0 |
| Binary cinematic media files tracked in the audited Git tree | 0 |

**Important interpretation:** zero means no qualifying packaged assets were found in the audited repository snapshot. It does not mean that the user's phone, private storage, or untracked external folders were inspected. No such private or external storage is accessible through this repository audit.

No asset has been assigned an APPROVED state. No third-party asset has been downloaded, purchased, copied, or bundled in this stage.

---

## 2. Inventory rules

This inventory distinguishes four different things that must not be mixed:

1. **Cinematic library asset:** a discrete, reusable source asset such as a rigged character, creature, environment model, prop, PBR texture set, or animation clip, with exact-version provenance and rights evidence.
2. **Procedural rendering code:** application source code that draws pixels or calculates effects at runtime. It is not a mesh, scanned environment, texture pack, character model, or animation asset.
3. **User project media:** images or recordings selected by the user for one project. They remain project-scoped and are not automatically library content.
4. **Unverified external candidate:** an asset mentioned, linked, or discovered outside the repository but not acquired and reviewed. It is not part of the local library and cannot be offered to the offline renderer.

Only the first class can become an item in the WOLF Cinematic Asset Library. Every item must pass the rights, visual, technical, dependency, and packaging gates in WOLF-ASSET-POLICY-001.

### Inventory status vocabulary

- `NOT_FOUND`: no matching asset file was present in the audited repository.
- `CODE_ONLY`: a procedural feature exists in source code, but no discrete library asset exists.
- `PROJECT_SCOPED`: user media is stored as a project reference and is not library content.
- `UNVERIFIED`: a possible external item has not been acquired or reviewed.
- `UNDER_REVIEW`: a specific acquired item is undergoing the required checks.
- `APPROVED`: every mandatory gate passed for an explicit use and target.
- `REJECTED`: a mandatory gate failed.
- `QUARANTINED`: provenance, rights, integrity, or safety evidence is missing or disputed.

No `UNVERIFIED`, `UNDER_REVIEW`, `REJECTED`, or `QUARANTINED` item may enter runtime asset selection.

---

## 3. Repository evidence and existing implementation

### 3.1 Packaged media search

The audited Git tree was inspected recursively. No tracked files were found with common cinematic media extensions, including:

- 3D models/scenes: `.glb`, `.gltf`, `.fbx`, `.obj`, `.dae`, `.blend`, `.usd`, `.usdz`.
- Images/textures: `.png`, `.jpg`, `.jpeg`, `.webp`, `.tif`, `.tiff`, `.exr`, `.hdr`.
- GPU textures: `.ktx`, `.ktx2`, `.basis`.
- Audio: `.wav`, `.mp3`, `.ogg`, `.flac`.
- Fonts: `.ttf`, `.otf`.
- Animation files: common standalone animation/clip files.

The following expected Android resource locations were checked:

| Path | Result |
|---|---|
| `app/src/main/assets/` | Not present |
| `app/src/main/res/` | Not present |
| `app/src/main/res/drawable/` | Not present |
| `app/src/main/res/raw/` | Not present |

The application source tree is currently Kotlin source plus the manifest; it does not contain an embedded cinematic media pack.

### 3.2 Existing code systems — not cinematic library assets

| Existing source file / system | What exists in code | Inventory classification | Library decision |
|---|---|---|---|
| `WolfSceneArtEngine.kt` | Canvas-drawn 2D backgrounds and simplified forest, corridor, window, door, room, cemetery, and house illustrations | `CODE_ONLY` | Not an approved realistic environment or prop library |
| `WolfProceduralMotionEngine.kt` | Procedural fog, particles, wind-driven branches, rain, environmental pulses, and signal effects | `CODE_ONLY` | Effects code only; may be retained as supporting effects after visual QA |
| `WolfProceduralVisualSystem.kt` | Procedural 2D scene composition and a simplified ghost-like silhouette | `CODE_ONLY` | Not a realistic human/creature asset; must not be presented as one |
| `WolfSceneAssetStore.kt` | Project-scoped image URI assignments, keyed by project/scene/shot | `PROJECT_SCOPED` | User-selected images are not library assets and must not be copied into the library automatically |
| `WolfImageDepthSegmenter.kt` | Color-distance / border-connectivity image heuristic | `CODE_ONLY` | Not semantic understanding and not authorized to drive depth motion; renderer preserves the original user still until trusted per-image semantic validation exists |
| `WolfSceneDepthComposer.kt` | Composition of image layers | `CODE_ONLY` | Rendering logic, not an asset |
| `WolfHorrorTypographyEngine.kt` | Programmatic horror typography treatment | `CODE_ONLY` | No packaged font file was found; system typefaces are not a bundled font library |
| `WolfSoundFxEngine.kt` | Procedural sound-effect generation | `CODE_ONLY` | Not a collection of licensed audio files |
| `WolfStoryVoiceEngine.kt` | Local voice recording / narration workflow | `PROJECT_SCOPED` / `CODE_ONLY` | User recordings are project media, not shared library assets |

The simplified 2D scenes and ghost silhouette are explicitly not substitutes for realistic rigged characters, creatures, scanned environments, or physically plausible props. Their existence does not increase the verified cinematic library asset count.

### 3.3 Existing visual limitations relevant to the inventory

- The current procedural scene art is drawn from primitive 2D shapes and gradients; it is not photorealistic source material.
- Door, window, grave, forest, and house drawings are parts of procedural illustrations, not separately reusable 3D props or environment assets.
- Fog, rain, particles, and branches are procedural effects; they do not supply realistic scene geometry or detailed PBR materials.
- The ghost-like silhouette in the older procedural visual system is a simplified graphic shape, not an approved human/creature character.
- The user image store preserves project references; it does not contain a centrally curated or licensed library.

These findings are inventory classifications, not authorization to remove or change existing features. Any replacement or retirement must be handled in its designated implementation stage and must preserve project compatibility.

---

## 4. Cinematic library category inventory

The following table is the initial category-level inventory. It records what is actually present, not what the product hopes to contain.

| Category | Required asset types | Found in audited repository | State | Evidence / action |
|---|---|---:|---|---|
| Human characters | Realistic human meshes, face/body variants, clothing variants, complete rigs | 0 | `NOT_FOUND` | No model or rig files; source or create compliant assets later |
| Creatures | Realistic supernatural/animal creature meshes, anatomy, rig, approved clips | 0 | `NOT_FOUND` | No creature model files; do not use primitive silhouettes as replacements |
| Environments | Realistic interiors/exteriors, buildings, rooms, corridors, forests, cemeteries, terrain | 0 | `NOT_FOUND` | Existing scene drawings are `CODE_ONLY`, not environment assets |
| Props | Doors, windows, furniture, tools, vehicles, set dressing, interactive objects | 0 | `NOT_FOUND` | Existing vector drawings are not reusable prop assets |
| PBR materials | Base color, normal, roughness, metallic, AO, emissive/opacity where applicable | 0 | `NOT_FOUND` | No texture or material files found |
| Rigs and skeletons | Character skeletons, skin weights, retarget profiles | 0 | `NOT_FOUND` | No rigged model files found |
| Animation clips | Idle, walk, run, turn, look, fear, attack, reveal, creature movement | 0 | `NOT_FOUND` | No standalone clips or rigged models found |
| Decals / surface detail | Blood, dirt, damage, stains, wear, scratches, wetness | 0 | `NOT_FOUND` | No decal/texture packs found |
| Atmospheric visual assets | Volumetric fog assets, smoke cards, particles, rain geometry/texture sets | 0 discrete files | `CODE_ONLY` | Some effects are procedural code; no reusable packaged source assets |
| Audio assets | Licensed ambience, impacts, footsteps, doors, creature sounds, stingers | 0 files | `CODE_ONLY` for procedural audio | Sound-generation code exists; no packaged audio collection found |
| Fonts / film identity | Licensed Arabic/Latin horror fonts, logo, watermark source assets | 0 files | `NOT_FOUND` | Typography code exists; no packaged font or logo media found |

### 4.1 Priority groups for future collection

These are collection priorities, not claims that assets have already been obtained:

**Priority A — Core realism**
- Human character base set with complete, tested rigs.
- A small number of realistic creature archetypes suitable for serious horror.
- High-quality interior environments: room, corridor, abandoned house, stairwell, basement.
- Exterior environments: forest, rural road, cemetery, abandoned building.
- Essential reusable props: doors, windows, furniture, lamps, vehicles, tools.

**Priority B — Materials and motion**
- Coherent PBR material sets for skin, fabric, wood, concrete, rust, glass, wet surfaces, soil, stone, and vegetation.
- Compatible skeleton and retarget profiles.
- Natural idle, breathing, walking, running, turning, looking, and reaction clips.
- Creature-specific movement clips with believable weight and contact.

**Priority C — Cinematic detail**
- Surface decals and damage overlays.
- Controlled atmospheric elements and environmental detail.
- Scene-specific variations that maintain the same visual realism standard.
- Optional licensed audio collection, coordinated with the later audio stage.

No priority group may bypass rights review or the visual quality gate.

---

## 5. Acceptance profile for each future asset

Every newly sourced asset must be recorded against the following minimum inventory fields before it can be considered for approval:

| Field | Required evidence |
|---|---|
| Asset identity | Unique proposed ID, display name, category, exact version |
| Source and provenance | Creator/rights holder, source page or documented origin, acquisition date |
| License | Exact license/version and preserved license evidence |
| Intended rights | Commercial use, modification, redistribution, and app bundling permissions |
| Visual review | Preview/reference, realism notes, scene/camera use, defects |
| Technical format | Exact format/version, units, coordinate system, pivot/origin |
| Geometry | Topology, mesh/vertex/index counts, material slots where applicable |
| Textures/materials | Maps present, resolution, channel packing, color space, normal convention |
| Rig/animation | Skeleton, bone mapping, skin weights, clip duration, frame rate, root motion |
| Dependencies | Complete file list and exact dependency versions |
| Integrity | SHA-256 per source/packaged file |
| Resource measurements | Source size, packaged size, decoded texture estimate, memory and runtime measurements when available |
| Compatibility | Target runtime/engine and exact tested version; untested compatibility remains pending |
| Decision | Reviewer, date, state, approval scope, reasons and limitations |

Unknown values must be marked `UNKNOWN` or `PENDING`. They must not be guessed. The full automated registry and Quality Gate remain the scope of Stage 08.

---

## 6. Visual acceptance rules for WOLF horror

A candidate is unsuitable by default if it is cartoonish, toy-like, comic, chibi, mascot-like, exaggerated, comical, or visibly inconsistent with serious cinematic horror.

### Human and creature assets
- Anatomically credible proportions and silhouette.
- Clean deformation topology and appropriate skin/material response.
- Complete rig with documented bone hierarchy and usable skin weights.
- No broken joints, severe clipping, sliding feet, mechanical motion, or unexplained pose snaps.
- Facial detail appropriate to the intended camera distance.
- Creature design must be unsettling through credible form and movement, not comic exaggeration.

### Environments and props
- Plausible construction, scale, material response, and surface wear.
- No obvious low-poly placeholder geometry presented as final content.
- Consistent visual language across assets used in the same scene.
- Suitable detail at the final render resolution and expected camera distance.

### Animation
- Motion must have weight, timing, contact, and controlled transitions.
- Clips must be tested on their exact target rig.
- Retargeting is not assumed to work merely because skeletons look similar.

### Materials
- PBR conventions must be documented and validated.
- Missing maps must be declared; renderer fallbacks must not create visibly implausible surfaces.
- Texture and shader costs must be measured on target hardware before budgets are finalized.

---

## 7. Packaging and offline constraints

The library is intended to be integrated with the Android application, not delivered as a separately installed companion app or runtime-downloaded pack.

For every future approved asset:
- The exact approved files and dependencies must be included in the application-integrated asset layout.
- The application must work with networking disabled.
- No runtime asset download, remote license check, cloud generation, or remote dependency is permitted.
- The packaged hash must match the reviewed inventory record.
- Asset updates must be versioned and must not silently break existing projects.
- User-selected project media must remain separate from shared library assets unless a distinct, explicit, rights-compliant ingestion workflow is later approved.

Stage 07 records the intended packaging constraint; it does not implement the Stage 08 registry or change the Android packaging configuration.

---

## 8. Stage 07 completion record

### Completed in this inventory pass
- Recursively inspected the tracked Git tree at the audited Stage 06 reference.
- Checked Android source/resource locations for embedded media.
- Reviewed the existing scene art, procedural motion, procedural visual, image-store, typography, and audio source systems for correct classification.
- Separated reusable media assets from procedural code and project-scoped user media.
- Recorded the zero-asset baseline without inventing asset names, source URLs, licenses, hashes, or compatibility claims.
- Defined the category-level collection priorities and per-asset evidence required for future intake.

### Explicitly not performed
- No third-party model, texture, animation, font, image, or audio pack was downloaded or imported.
- No purchase, license, redistribution permission, or commercial-use permission was assumed.
- No asset was marked `APPROVED`.
- No asset binary was added to the APK or application source tree.
- No automated Asset Registry or Quality Gate was implemented.
- No procedural generation library was built.
- No engine compatibility or Realme 7 performance claim was made.
- No existing feature or user project data was changed.

### Acceptance status

**The repository inventory baseline is documented. The actual WOLF Cinematic Asset Library is not populated yet.**

The next asset intake must provide specific candidates and evidence, then evaluate each candidate under WOLF-ASSET-POLICY-001. A category being listed as a priority does not make it available to the film renderer.

---

## 9. Audit limitations

This report describes the repository contents at the exact audited commit. It does not inspect:
- Files on the user's Android phone.
- Private folders or untracked local assets outside the Git repository.
- External asset marketplaces or accounts.
- License records not present in the repository.
- Runtime files created after installation.

The inventory must be refreshed when real candidate files are added. A future scan must calculate hashes and inspect actual media contents; filenames and extensions alone are not enough to approve an asset.
