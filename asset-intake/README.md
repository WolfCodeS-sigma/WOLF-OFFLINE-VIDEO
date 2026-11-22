# WOLF Cinematic Asset Intake — Executable Batch Track

This is the operational intake track promised after Stage 08. It runs in parallel with Stages 09–20; it is not a replacement for those stages and does not silently change the production APK.

## States must not be conflated

- **Source lead:** a discovered source page; no local file has been acquired.
- **Acquired candidate:** the exact source file is present under a batch folder, with byte size and SHA-256 recorded.
- **Reviewed candidate:** rights, cinematic realism, technical profile, exact runtime/version, and resource measurements have been reviewed.
- **Production-approved asset:** all mandatory Stage 06/08 gates passed and the exact files are separately admitted to the production manifest and APK.

A URL, thumbnail, filename, or a claim that a license is "free" does not make an asset acquired or approved. \`asset-intake/leads/source-leads.json\` currently records one discovery lead only. Its binary has **not** been acquired. Batch 000 is deliberately empty and establishes the machine-validated baseline; neither file claims the library is populated.

## Batch layout

Each actual batch uses this shape:

\`\`\`text
asset-intake/batches/WOLF-ASSET-BATCH-NNN/
  manifest.json
  files/<asset-id>/<source files...>
\`\`\`

Every \`files[]\` record points to an actual local file relative to the batch directory and records the exact format, byte size, and SHA-256. Do not place user-selected project images here without a separate, explicit rights-compliant library-ingestion decision.

## Validate locally

From the repository root:

\`\`\`bash
python3 tools/asset-intake/wolf_asset_intake.py --manifest asset-intake/batches/000-baseline/manifest.json --repo-root . --report /tmp/wolf-asset-intake-report.json
\`\`\`

The validator is standard-library-only. It rejects path traversal, symlinks, missing files, duplicate IDs/paths, incorrect sizes/hashes, incomplete provenance, and invalid approved records. It does not download media, infer license rights, perform visual review, alter the production registry, or approve anything. It reports an empty batch honestly as \`EMPTY_BATCH\`.

## Human approval and production hand-off

A record can only claim \`APPROVED\` after the source/version/license evidence, commercial use, modification, redistribution and application bundling rights, attribution decision, visual realism score (at least 4/5), reviewer record, exact tested runtime/version, test result, explicit use scopes, measured resource values, and \`budgetStatus=PASS\` are present. This script checks those assertions but does not replace human/legal review or actual visual/runtime testing.

Once a batch is mechanically valid, its production admission is a separate code-reviewed change that copies exact bytes into \`app/src/main/assets/cinematic/\`, records their hashes in \`app/src/main/assets/wolf_asset_registry.json\`, and includes required notices. A candidate batch never writes into the APK automatically. This keeps unreviewed content fail-closed and protects existing project/user media.

## Parallel checkpoints

\`parallel-intake-plan.json\` defines evidence checkpoints for every stage from 09 through 20. At each checkpoint, new leads and acquired batches must be reported separately, with missing categories retained as explicit gaps. The library is not complete until it satisfies the 100% coverage requirement and passes all three reference stories.
