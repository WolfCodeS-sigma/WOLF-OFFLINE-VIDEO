# WOLF Asset Registry & Quality Gate — Stage 08

**Registry ID:** WOLF-ASSET-REGISTRY-001  
**Schema version:** 1  
**Policy baseline:** `WOLF-ASSET-POLICY-001` (Stage 06)  
**Inventory baseline:** `WOLF-CINEMATIC-INVENTORY-001` (Stage 07)  
**Runtime model:** Android application-bundled manifest and files; offline only.

## 1. Purpose and boundaries

Stage 08 implements the in-app registry model and a fail-closed quality gate. It does not acquire assets, grant licenses, certify an engine, create procedural generators, or claim that the library is populated.

The registry is part of the Android application source and APK under `app/src/main/assets/wolf_asset_registry.json`; it is not a companion application or separately installed package. The current manifest intentionally contains an empty `assets` array because Stage 07 verified zero packaged cinematic assets in its audited repository snapshot. No fictitious asset, license, source, hash, or compatibility record is inserted.

Project-scoped user images remain managed by `WolfSceneAssetStore`. They are not automatically copied into this shared library, reviewed as library content, or changed by this stage.

## 2. Runtime contract

Runtime consumers must request assets using both:
- `targetRuntime`: exact runtime identifier.
- `intendedScope`: exact approved use, such as `environment_background` or a scene-specific character use.

Consumers may use only results from `WolfAssetRegistry.approvedAssets(request)`. A missing ID, candidate, under-review, rejected, quarantined, retired, or otherwise failing entry is not eligible. There is no fallback that substitutes an unrelated asset. An empty registry returns no cinematic library assets; the existing safe atmospheric fallback and user-image paths remain separate.

The manifest is loaded from Android `AssetManager`; file verification reads the packaged asset stream, checks exact byte size, and recalculates SHA-256. JVM tests can use a temporary directory verifier for deterministic fixtures.

## 3. Required record

Each entry in the JSON `assets` array maps to `WolfAssetRegistryEntry` and must contain:

- `assetId`: stable ID matching `wolf_[a-z0-9][a-z0-9._-]{2,63}`.
- `name`, `version`, `category`, `tags`, and `lifecycle`.
- `license`: creator/rights holder, source name/location, exact license name/version, evidence reference, verification state, commercial-use permission, modification permission, redistribution permission, app-bundling permission, attribution requirement/text, and restrictions.
- `files`: one or more relative packaged file paths, format, exact byte size, and SHA-256 for each file.
- `dependencies`: IDs of other registry entries required by this asset.
- `compatibleRuntimes`: runtime identifier to exact tested version.
- `approvedScopes`: explicitly approved use scopes.
- `budgetStatus`: `PASS`, `PENDING`, or `FAIL`.
- `review`: reviewer, review timestamp, reason, rights/visual/technical pass flags, realism score, visual style, and any scoped-stylization approvals.

Allowed categories: `CHARACTER`, `CREATURE`, `ENVIRONMENT`, `PROP`, `TEXTURE`, `MATERIAL`, `ANIMATION`, `AUDIO`, `FONT`, `SHADER`, `GENERATOR`.

Lifecycle values: `CANDIDATE`, `UNDER_REVIEW`, `APPROVED`, `REJECTED`, `QUARANTINED`, `RETIRED`.

Visual styles: `CINEMATIC_REALISTIC`, `SCOPED_STYLIZED`, `CARTOON`, `TOY_LIKE`, `COMIC`, `UNKNOWN`.

Unknown or missing values do not receive guessed defaults that can create approval. Parsing errors or unsupported schema versions stop registry loading.

## 4. Mandatory Quality Gate rules

The gate blocks approval when any of the following is true:

1. Identity, name, version, state, request runtime, or intended scope is missing/invalid.
2. Source, creator/rights holder, exact license/version, or verified license evidence is missing.
3. Commercial use, modification, redistribution, or bundling inside the app is not explicitly permitted.
4. Required attribution text is missing.
5. Visual tags indicate cartoon, chibi, toy-like, comic, mascot, humorous/comical, or primitive-human content.
6. Visual style is not cinematic realistic. Scoped stylization is allowed only for its explicitly reviewed scope.
7. Reviewer, date, decision reason, rights/visual/technical review, or realism score (minimum 4/5) is missing or failed.
8. The requested runtime version is not explicitly listed as tested, or the requested use scope is not approved.
9. The manifest is empty for an asset, a path is absolute/traversal/unsafe, a file is absent, or its byte size/SHA-256 differs from the packaged bytes.
10. A dependency is missing, not approved for the same runtime/scope, or participates in a dependency cycle.
11. Resource budget status is `FAIL`.

A `PENDING` resource budget produces a visible warning, not a false performance pass. This is intentional: Stage 06 says device budgets must be measured in later integration and device-optimization stages, not invented now.

## 5. File integrity and path safety

All file paths are relative to the packaged asset root. Absolute paths, backslashes, drive-letter paths, empty segments, `.`, and `..` traversal segments are rejected. The verifier checks the resolved file remains under the packaged root (JVM verifier), or reads the exact named packaged asset stream (Android verifier). Every file must match both its declared byte count and lowercase/uppercase-insensitive SHA-256 value.

A changed file requires a new version/hash and a fresh review. The gate never repairs, rewrites, or silently replaces an asset.

## 6. Current verified state

| Item | Current value |
|---|---:|
| Registry schema version | 1 |
| Registry entries | 0 |
| Approved cinematic assets | 0 |
| Runtime downloads | Prohibited |
| User-project media auto-ingestion | Prohibited |
| Final device performance budgets | Pending later measured stages |

The empty manifest is an honest implementation baseline, not evidence that the cinematic library is complete. Real candidates must be added only with their actual files and complete rights/quality/compatibility evidence.

## 7. Tests added

`WolfAssetRegistryTest` covers:
- A complete, realistic, licensed entry passing the gate with a pending-budget warning.
- Unverified/non-commercial rights rejection.
- Cartoon/t toy-like visual rejection.
- Packaged byte/hash mismatch rejection.
- Absolute/traversal path rejection.
- Missing dependency rejection.
- Scope and tested-runtime restrictions.
- Resource budget failure.
- Runtime filtering to approved entries only.
- Loading the empty bundled-registry schema without inventing assets.

## 8. Non-goals

This stage does not populate the library, add third-party binary assets, build the WOLF Procedural Generation Library, connect renderer engines, alter user image assignments, modify project persistence, or test the physical Realme 7. Those remain separate planned work with their own acceptance evidence.
