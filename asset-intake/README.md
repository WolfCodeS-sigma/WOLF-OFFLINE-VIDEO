# WOLF Cinematic Asset Intake — Executable Batch Track

This operational intake track runs in parallel with Stages 09–20. It does not replace those stages and does not silently change the production APK.

## States must not be conflated

- Source lead: discovered source page, with no acquired local binary.
- Acquired candidate: exact source file acquired into a batch, with provenance, byte size, provider checksum, and WOLF SHA-256 recorded.
- Reviewed candidate: rights, cinematic realism, technical profile, exact runtime/version, and measured resource limits reviewed.
- Production-approved asset: every mandatory Stage 06/08 gate has passed, and exact files are admitted through a separate reviewed change into the production manifest and APK.

The current lead register contains two ACQUIRED_CANDIDATE environment HDRIs from Poly Haven in WOLF-ASSET-BATCH-001. Both remain candidates: visual/runtime review, import into the intended WOLF renderer, measured resource budgets, and explicit use scopes are pending. Batch 000-baseline remains intentionally empty and is not the acquired batch.

## Current acquired batch

- debris_basement_corridor — 2K Radiance HDR, 6,402,928 bytes, SHA-256 4636e5b2f308c44c550a236752ad171be7af16161789bb2ba07838c39cece78c.
- abandoned_hall_01 — 2K Radiance HDR, 6,503,079 bytes, SHA-256 3b3525a4caea1acb18200565acea70d3fd774bcc73b887b4eade0f65a54cd4e6.

The exact files, API provenance, and acquisition manifest are emitted by the developer/CI-only workflow as the wolf-cinematic-asset-batch-001 GitHub Actions artifact (90-day retention). They are not committed as production assets and are not bundled in the WOLF APK. The WOLF application performs no network download. The source files can be reacquired reproducibly from Poly Haven through the pinned batch script.

## Batch layout

The acquisition job creates:

    asset-intake/batches/WOLF-ASSET-BATCH-001/
      manifest.json
      acquisition-report.json
      files/
        debris_basement_corridor_2k.hdr
        abandoned_hall_01_2k.hdr
      provenance/
        <asset-id>.info.json
        <asset-id>.files.json

Every files[] record points to an actual local file and records the exact format, byte size, provider MD5, and SHA-256. The lead register and acquisition catalog mirror these facts; validate_acquisition_links.py fails closed if their source URL, creator, batch ID, timestamp, file path, size, resolution, or hashes drift from the acquired manifest.

Do not place user-selected project images here without a separate, explicit rights-compliant library-ingestion decision.

## Validate locally

The generic validator is standard-library-only. For the stable empty baseline:

    python3 tools/asset-intake/wolf_asset_intake.py \
      --manifest asset-intake/batches/000-baseline/manifest.json \
      --repo-root .

For an acquired batch after its files and manifest are available locally:

    python3 tools/asset-intake/validate_acquisition_links.py \
      --manifest asset-intake/batches/WOLF-ASSET-BATCH-001/manifest.json \
      --lead-register asset-intake/leads/source-leads.json \
      --catalog asset-intake/acquisition-catalog.json
    python3 tools/asset-intake/wolf_asset_intake.py \
      --manifest asset-intake/batches/WOLF-ASSET-BATCH-001/manifest.json \
      --repo-root .

The generic validator rejects path traversal, symlinks, missing files, duplicate IDs/paths, incorrect sizes/hashes, incomplete provenance, and invalid approved records. The acquisition-link validator keeps the source register, catalog, and acquired manifest synchronized. Neither tool downloads media, infers license rights, performs visual review, alters the production registry, or approves anything.

## Human approval and production hand-off

A record can only claim APPROVED after source/version/license evidence, commercial use, modification, redistribution and application-bundling rights, attribution decision, visual realism score (at least 4/5), reviewer record, exact tested runtime/version, test result, explicit use scopes, measured resource values, and budgetStatus=PASS are present. The scripts check assertions but do not replace human/legal review or actual visual/runtime testing.

Once a batch is mechanically valid, production admission is a separate code-reviewed change that copies exact bytes into app/src/main/assets/cinematic/, records their hashes in app/src/main/assets/wolf_asset_registry.json, and includes required notices. A candidate batch never writes into the APK automatically. This fail-closed separation protects existing project/user media.

## Parallel checkpoints

parallel-intake-plan.json defines evidence checkpoints for every stage from 09 through 20. At each checkpoint, new leads, acquired candidates, reviewed candidates, and production-approved assets must be reported separately, with missing categories retained as explicit gaps. The library is not complete until it satisfies the 100% coverage requirement and passes all three reference stories.
