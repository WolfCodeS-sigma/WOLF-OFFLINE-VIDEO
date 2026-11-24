# WOLF Asset Quality, Realism, and Licensing Policy

**Policy ID:** WOLF-ASSET-POLICY-001  
**Version:** 1.0  
**Applies to:** WOLF Cinematic Asset Library, WOLF Procedural Generation Library, and every asset proposed for WOLF OFFLINE VIDEO  
**Status:** Stage 06 policy baseline  
**Runtime model:** Offline-first. Approved assets are packaged with the application or its explicitly versioned, application-integrated asset store. No runtime asset downloads.

---

## 1. Purpose and governing principles

This policy defines the minimum evidence and quality requirements an asset must satisfy before it can be used in a WOLF CODE S film.

The product target is serious, credible, cinematic horror. Assets must support that target. An asset is not acceptable merely because it renders, has a compatible file extension, or is free to download.

The following principles are mandatory:

1. **Rights before inclusion.** No asset may enter an approved collection until its origin, license, permitted uses, restrictions, and required notices are recorded and reviewed.
2. **Realism before quantity.** A smaller library of credible assets is preferable to a large library containing cartoonish, comic, low-quality, or stylistically inconsistent assets.
3. **Fail closed.** Missing, ambiguous, contradictory, or unverifiable evidence means the asset is not approved.
4. **No silent substitution.** A failed or unavailable asset must not be replaced with an unrelated comic, primitive, or visually misleading asset.
5. **Offline delivery.** Runtime rendering must not depend on downloading assets, contacting a licensing server, or calling a cloud generation service.
6. **Traceability.** Every approved asset must have a stable ID, source record, version, content hash, license record, technical profile, and review history.
7. **Preserve user content.** User-selected images and other project media are not library assets and must not be silently modified, replaced, uploaded, or added to the shared asset library.

## 2. Scope and asset classes

This policy applies to:

- Human characters, creatures, animals, silhouettes, skeletons, and other character assets.
- Environments, architecture, interiors, exteriors, terrain, vegetation, and atmospheric scene assets.
- Props, furniture, vehicles, tools, set dressing, and scene objects.
- Textures, materials, shaders, decals, surface scans, and PBR maps.
- Rigged meshes, skeletons, animation clips, facial animation, and motion data.
- Sound effects, ambience, music beds, and other audio assets proposed for the cinematic asset collection.
- Fonts, icons, logos, and other visual identity assets used in rendered films.
- Procedural generators, source code, templates, and generated outputs from the WOLF Procedural Generation Library.

The policy covers both source assets and derivatives, including converted formats, retopologized meshes, modified textures, baked maps, animation retargets, and packaged runtime variants.

## 3. Licensing and rights requirements

### 3.1 Required rights record

Each candidate must have a rights record containing, at minimum:

- Asset ID and exact asset/version covered by the record.
- Creator, author, publisher, or legal rights holder, as applicable.
- Original source name and source location (URL or documented offline origin).
- Acquisition date and acquisition method.
- Exact license name and version, plus a preserved copy or stable reference to the license terms.
- Whether commercial use is permitted.
- Whether modification, conversion, and creation of derivatives are permitted.
- Whether redistribution and bundling inside a commercial Android application are permitted.
- Attribution, notice, share-alike, source-disclosure, or other obligations.
- Restrictions on use, redistribution, platform, territory, or audience, if any.
- Evidence of any required model/property/performer releases or other permissions.
- Reviewer, review date, decision, and rationale.

A generic label such as "free", "public", "royalty-free", "open source", or "downloadable" is not a sufficient license record.

### 3.2 Allowed license outcomes

A candidate may be approved only when the recorded terms clearly permit the intended WOLF use, including the intended modifications and packaging/distribution model.

The review must distinguish between:
- Permission to view or use an asset personally.
- Permission to use it in a rendered film.
- Permission to modify it.
- Permission to redistribute the asset itself, including inside an application package.
- Permission to use it commercially.

Permission for one activity does not imply permission for another.

### 3.3 Mandatory rejection or hold conditions

Reject or quarantine a candidate when:

- The license is missing, ambiguous, contradictory, or cannot be verified.
- The source does not establish who supplied or controls the asset.
- Commercial use or application bundling is prohibited or unclear for the intended release.
- Required attribution or notices cannot be satisfied.
- The asset is marked non-commercial, no-derivatives, or otherwise restricted in a way incompatible with the intended use.
- The asset appears copied, ripped, leaked, pirated, or taken from a game, film, paid pack, or other protected work without documented permission.
- A recognizable person, performer, property, brand, or protected design is included and required permissions are absent or unclear.
- The provenance or rights record cannot be tied to the exact asset version being packaged.

Do not infer permission from search-result snippets, repository visibility, a creator's silence, or the fact that a file can be downloaded.

### 3.4 Attribution and notices

Required attribution and license notices must be preserved with the asset and included in the appropriate in-app notices or distributed third-party notices. Share-alike and source-disclosure obligations must be escalated for review before acceptance; they must not be ignored or silently waived.

### 3.5 Procedural generation and code

Procedural generators are software assets and require their own source and dependency license review. Their generated outputs are not automatically free of third-party rights. Review must cover:

- Generator source code and dependencies.
- Embedded templates, sample data, textures, fonts, and reference material.
- Any externally sourced model, checkpoint, or training/reference asset used by the generator.
- Output restrictions stated by the generator or upstream licenses.
- Whether generated output contains recognizable protected material or other restricted content.

A procedural output may be approved only when both the generator's rights and the output's provenance/use conditions are sufficiently documented.

### 3.6 Legal review boundary

This policy is an engineering and asset-governance control, not legal advice. Unclear, conflicting, high-value, or commercially material rights questions must be escalated for qualified legal review. Until resolved, the asset remains unapproved.

## 4. Cinematic quality and realism gate

Every visual candidate must be reviewed in context, not only as a thumbnail or isolated mesh.

### 4.1 Style requirements

Accepted visual assets must support serious, credible, cinematic horror. They must not introduce:

- Cartoon, toy-like, comic, chibi, mascot, or intentionally humorous styling.
- Exaggerated proportions or facial expressions that undermine the intended realism.
- Bright playful palettes or decorative effects inconsistent with the scene's approved direction.
- Primitive placeholder geometry presented as a finished human, creature, or realistic environment.
- Obvious low-quality stock, mismatched art styles, or visibly inconsistent asset packs.

Stylization is not automatically prohibited when it is explicitly required by a specific scene, but it must be reviewed and approved for that defined use. The default library standard remains realistic cinematic horror.

### 4.2 Human and creature assets

Review at least:

- Anatomical proportions, silhouette, facial structure, skin/material response, and visible deformation.
- Mesh topology and deformation around joints and facial features.
- Rig completeness, bone naming/mapping, skin weights, and retargeting behavior.
- Animation quality, foot contact, body weight, timing, transitions, and absence of mechanical sliding or broken poses.
- Compatibility of the character's appearance and motion with the intended scene and camera distance.

Primitive procedural shapes may be used for abstract environmental effects or non-character blocking where explicitly labeled. They must not be represented as approved realistic humans or creatures.

### 4.3 Environment and prop assets

Review:

- Scale, proportion, construction logic, and scene coherence.
- Surface detail and material plausibility.
- Texture resolution, UV quality, seams, stretching, and visible repetition.
- Lighting response and consistency with the intended environment.
- Geometry defects, missing faces, broken normals, z-fighting, and clipping.
- Whether the asset remains credible at the intended output resolution and camera distance.

### 4.4 PBR and material requirements

Where PBR materials are supplied, record and validate available maps and conventions, including base color/albedo, normal, roughness, metallic, ambient occlusion, and any required emissive or opacity data.

Do not assume every asset requires every map. Missing maps must be explicitly recorded, and the renderer's fallback behavior must be visually acceptable. Color-space, channel packing, normal-map convention, texture dimensions, and material parameters must be known before approval.

### 4.5 Animation and motion quality

Animation clips must be evaluated for:

- Correct rig binding and expected skeleton.
- Stable frame rate, duration, loop behavior, and root-motion conventions.
- Plausible movement and transitions.
- No broken limbs, severe foot sliding, sudden pose snaps, or unintended penetration.
- Safe blending with the motion system and other clips.

An animation that technically plays but visibly damages realism must fail.

## 5. Technical compatibility and resource gate

Every candidate must declare and pass applicable checks for:

- File format and version.
- Coordinate system, axis orientation, units, scale, and origin/pivot.
- Mesh topology, vertex/index counts, material count, and texture dependencies.
- Skeleton, bone count, hierarchy, skin weights, and animation bindings, when applicable.
- Shader/material features and renderer requirements.
- Target engine or runtime compatibility, with a tested version and import settings.
- Memory footprint, decoded texture footprint, and expected runtime allocation.
- CPU/GPU cost and draw-call impact where measurable.
- Compression/transcoding settings and packaged size.
- Dependency closure: all required files must be present and version-pinned.

Compatibility must be demonstrated against the actual target integration. A format being theoretically supported by an engine is not proof that a specific asset works correctly.

No fixed universal file-size or polygon budget is declared by this policy. Budgets must be established from measured device capability and renderer role in the later engine-integration and Realme 7 optimization stages. Until those budgets exist, record measurements and mark the relevant performance gate as pending rather than inventing a pass.

## 6. Asset states and approval decisions

Every candidate must have exactly one lifecycle state:

| State | Meaning | Runtime eligibility |
|---|---|---|
| CANDIDATE | Submitted but not yet reviewed | No |
| UNDER_REVIEW | Quality, rights, or compatibility checks are in progress | No |
| APPROVED | All mandatory gates passed for a defined use and target | Yes, within approved scope |
| REJECTED | A mandatory gate failed or rights are incompatible | No |
| QUARANTINED | Evidence is missing, disputed, expired, or under investigation | No |
| RETIRED | Previously approved but withdrawn, superseded, or no longer supported | No for new projects |

Only APPROVED assets may be offered by the production pipeline as library assets. A state change must be recorded with reviewer, timestamp, reason, and affected asset version.

Approval is scoped. An asset approved for a still image or background use is not automatically approved for close-up character animation, redistribution in another product, or use in a different engine.

## 7. Quality Gate decision sequence

The Asset Registry and Quality Gate planned for Stage 08 must enforce the following logical sequence:

1. Validate identity and required metadata.
2. Verify provenance and exact-version rights evidence.
3. Check license compatibility with modification, commercial use, and application bundling.
4. Review visual realism and intended cinematic use.
5. Validate geometry, materials, textures, rigs, and animation as applicable.
6. Validate target engine/runtime compatibility.
7. Record resource measurements and applicable budget status.
8. Verify dependency closure, hashes, and packaged files.
9. Record reviewer decision and permitted use scope.
10. Allow runtime indexing only when the final state is APPROVED.

A failed mandatory gate must block approval. A warning may be accepted only when it is documented, does not conceal a failed mandatory requirement, and has an explicit reviewer decision.

## 8. Registry metadata contract

The future WOLF Asset Registry must support at least these fields:

- asset_id: stable unique identifier.
- asset_name: human-readable name.
- asset_category: character, creature, environment, prop, texture, material, animation, audio, font, shader, or generator.
- tags: controlled descriptive tags.
- source_name and source_location.
- creator_or_rights_holder.
- acquired_at and asset_version.
- license_id, license_name, license_version, and license_evidence_ref.
- commercial_use, modification_allowed, redistribution_allowed, and attribution_required.
- attribution_text and license_restrictions.
- provenance_record_ref and review_record_ref.
- file_manifest, including relative path, file format, byte size, and cryptographic hash for every packaged file.
- dependencies and dependency versions.
- geometry_profile, material_profile, texture_profile, and animation_profile, as applicable.
- coordinate_system, units, and import settings.
- compatible_engines, tested engine versions, and renderer requirements.
- resource_measurements and budget_profile.
- visual_quality_result, technical_quality_result, and rights_quality_result.
- approval_scope, lifecycle_state, reviewer, reviewed_at, and decision_reason.

Unknown values must be represented as unknown or pending. They must never be silently filled with guessed values.

## 9. Packaging, updates, and offline operation

- Approved assets must be packaged with the application or its explicitly versioned, application-integrated asset store.
- The production runtime must not download missing assets or query remote license servers.
- Every packaged asset must match its approved manifest and hash.
- Asset updates require a new version, fresh hash, dependency review, and renewed quality/rights checks when the files or license terms change.
- Removing or replacing an asset must not silently corrupt existing projects. Compatibility or migration behavior must be defined before a breaking asset update.
- Third-party notices and attribution must ship with the relevant application release where required.
- No asset may be copied into the library merely because a user selected it for a project. User content remains project-scoped unless the user explicitly authorizes a separate library-ingestion workflow and all applicable rights checks pass.

## 10. Review evidence and audit trail

A review record must preserve:

- Candidate ID and exact version/hash.
- Review date and reviewer.
- Source and rights evidence checked.
- Visual review notes and relevant preview/test references.
- Technical validation results and target versions.
- Known limitations and approved usage scope.
- Final state and reason.
- Any follow-up action or review expiry/trigger.

Changes to source, license terms, dependencies, geometry, textures, rig, animation, or packaging must trigger re-evaluation of the affected gates.

## 11. Explicit non-goals of Stage 06

This policy does not itself:

- Download, purchase, import, or bundle third-party assets.
- Claim that any existing asset pack has been legally or technically approved.
- Create the full Asset Registry implementation or automated Quality Gate (Stage 08).
- Build the WOLF Cinematic Asset Library inventory (Stage 07).
- Build the WOLF Procedural Generation Library (Stage 09).
- Certify Godot, Filament, Cocos Creator, libGDX, KorGE, Lottie, or SceneView compatibility.
- Set final memory, polygon, texture, or APK-size budgets.
- Replace legal review where rights are uncertain.

Those tasks require their own stages, evidence, and approvals.

## 12. Acceptance criteria for this policy stage

Stage 06 documentation is complete when:

- The policy defines a clear realistic-horror quality bar and rejects comic/cartoon assets by default.
- The policy defines the minimum license and provenance evidence for every asset class.
- The policy distinguishes use, modification, commercial use, and redistribution/bundling rights.
- The policy defines fail-closed states and ensures unapproved assets cannot enter runtime selection.
- The policy defines technical, material, rig, animation, compatibility, and resource evidence.
- The policy defines registry metadata needed for later automation.
- The policy preserves offline operation and prohibits runtime downloads.
- The policy explicitly separates this stage from asset inventory, registry implementation, and engine integration.

---

**Governance note:** This policy is the acceptance baseline for later WOLF asset-library stages. Any exception must be explicit, scoped to a particular asset and use, documented with evidence, and approved before that asset can be used.
