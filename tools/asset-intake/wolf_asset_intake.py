#!/usr/bin/env python3
"""Fail-closed validator for WOLF's offline cinematic-asset intake batches."""
from __future__ import annotations
import argparse, hashlib, json, re, sys
from collections import Counter
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlparse

SCHEMA_VERSION = 1
LIFECYCLES = {"CANDIDATE", "UNDER_REVIEW", "APPROVED", "REJECTED", "QUARANTINED", "RETIRED"}
CATEGORIES = {"CHARACTER", "CREATURE", "ENVIRONMENT", "PROP", "TEXTURE", "MATERIAL", "ANIMATION", "AUDIO", "FONT", "SHADER", "GENERATOR"}
BATCH_ID = re.compile(r"^WOLF-ASSET-BATCH-[0-9]{3,}$")
ASSET_ID = re.compile(r"^wolf-[a-z0-9]+(?:-[a-z0-9]+)*$")
SHA256 = re.compile(r"^[a-fA-F0-9]{64}$")

class IntakeError(Exception):
    pass

def _text(obj: Any, key: str) -> str:
    value = obj.get(key) if isinstance(obj, dict) else None
    return value.strip() if isinstance(value, str) else ""

def _bool(obj: Any, key: str) -> bool:
    return isinstance(obj, dict) and obj.get(key) is True

def _safe_file(root: Path, relative: str) -> Path:
    if not isinstance(relative, str) or not relative or "\\" in relative:
        raise IntakeError(f"unsafe file path: {relative!r}")
    posix = PurePosixPath(relative)
    if posix.is_absolute() or any(part in {"", ".", ".."} for part in posix.parts):
        raise IntakeError(f"unsafe file path: {relative!r}")
    current = root
    for part in posix.parts:
        current = current / part
        if current.is_symlink():
            raise IntakeError(f"symlink is not allowed in intake path: {relative}")
    resolved_root, resolved_file = root.resolve(strict=True), current.resolve(strict=True)
    if not resolved_file.is_relative_to(resolved_root) or not resolved_file.is_file():
        raise IntakeError(f"file is missing or outside batch root: {relative}")
    return resolved_file

def _hash_file(path: Path) -> tuple[int, str]:
    digest, total = hashlib.sha256(), 0
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            total += len(chunk)
            digest.update(chunk)
    return total, digest.hexdigest()

def validate_batch(manifest_path: Path, repo_root: Path) -> dict[str, Any]:
    manifest_path, repo_root = manifest_path.resolve(strict=True), repo_root.resolve(strict=True)
    try:
        manifest_path.relative_to(repo_root)
    except ValueError as exc:
        raise IntakeError("manifest must be inside the repository root") from exc
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise IntakeError(f"manifest is not valid UTF-8 JSON: {exc}") from exc
    if not isinstance(manifest, dict):
        raise IntakeError("manifest root must be a JSON object")

    errors: list[str] = []
    if manifest.get("schemaVersion") != SCHEMA_VERSION:
        errors.append(f"schemaVersion must equal {SCHEMA_VERSION}")
    batch_id = _text(manifest, "batchId")
    if not BATCH_ID.fullmatch(batch_id):
        errors.append("batchId has invalid WOLF-ASSET-BATCH-NNN format")
    if _text(manifest, "batchStatus") not in {"OPEN", "CLOSED"}:
        errors.append("batchStatus must be OPEN or CLOSED")
    if _text(manifest, "policyId") != "WOLF-ASSET-POLICY-001":
        errors.append("policyId must reference WOLF-ASSET-POLICY-001")
    assets = manifest.get("assets")
    if not isinstance(assets, list):
        errors.append("assets must be an array")
        assets = []

    seen_ids: set[str] = set()
    counts: Counter[str] = Counter()
    eligible_ids: list[str] = []
    file_count = 0
    batch_root = manifest_path.parent

    for index, asset in enumerate(assets):
        prefix = f"assets[{index}]"
        if not isinstance(asset, dict):
            errors.append(f"{prefix} must be an object")
            continue
        asset_id, lifecycle = _text(asset, "assetId"), _text(asset, "lifecycle")
        if not ASSET_ID.fullmatch(asset_id):
            errors.append(f"{prefix}.assetId is invalid")
        if asset_id in seen_ids:
            errors.append(f"duplicate assetId: {asset_id}")
        seen_ids.add(asset_id)
        if lifecycle not in LIFECYCLES:
            errors.append(f"{prefix}.lifecycle is invalid")
        counts[lifecycle or "INVALID"] += 1
        if _text(asset, "category") not in CATEGORIES:
            errors.append(f"{prefix}.category is invalid")
        if not _text(asset, "name"):
            errors.append(f"{prefix}.name is required")

        source = asset.get("source") if isinstance(asset.get("source"), dict) else {}
        source_url = urlparse(_text(source, "url"))
        if source_url.scheme != "https" or not source_url.netloc:
            errors.append(f"{prefix}.source.url must be an HTTPS source URL")
        if not _text(source, "name") or not _text(source, "creator"):
            errors.append(f"{prefix}.source.name and creator are required")
        if not _text(source, "acquiredAt") or not _text(source, "acquisitionMethod"):
            errors.append(f"{prefix}.source.acquiredAt and acquisitionMethod are required")

        license_data = asset.get("license") if isinstance(asset.get("license"), dict) else {}
        if not _text(license_data, "name") or not _text(license_data, "version") or not _text(license_data, "evidenceUrl"):
            errors.append(f"{prefix}.license needs exact name, version, and evidenceUrl")
        evidence = urlparse(_text(license_data, "evidenceUrl"))
        if evidence.scheme != "https" or not evidence.netloc:
            errors.append(f"{prefix}.license.evidenceUrl must be HTTPS")

        files = asset.get("files")
        if not isinstance(files, list) or not files:
            errors.append(f"{prefix}.files must contain at least one acquired file")
            files = []
        seen_paths: set[str] = set()
        for file_index, record in enumerate(files):
            file_prefix = f"{prefix}.files[{file_index}]"
            if not isinstance(record, dict):
                errors.append(f"{file_prefix} must be an object")
                continue
            relative = record.get("relativePath")
            if not isinstance(relative, str) or relative in seen_paths:
                errors.append(f"{file_prefix}.relativePath must be unique")
            if isinstance(relative, str):
                seen_paths.add(relative)
            if not _text(record, "format"):
                errors.append(f"{file_prefix}.format is required")
            size, expected_hash = record.get("byteSize"), _text(record, "sha256")
            if not isinstance(size, int) or isinstance(size, bool) or size <= 0:
                errors.append(f"{file_prefix}.byteSize must be a positive integer")
            if not SHA256.fullmatch(expected_hash):
                errors.append(f"{file_prefix}.sha256 must be 64 hex characters")
            if isinstance(relative, str) and isinstance(size, int) and SHA256.fullmatch(expected_hash):
                try:
                    absolute = _safe_file(batch_root, relative)
                    actual_size, actual_hash = _hash_file(absolute)
                    file_count += 1
                    if actual_size != size:
                        errors.append(f"{file_prefix} byteSize mismatch: expected {size}, got {actual_size}")
                    if actual_hash.lower() != expected_hash.lower():
                        errors.append(f"{file_prefix} SHA-256 mismatch")
                except (IntakeError, OSError) as exc:
                    errors.append(f"{file_prefix}: {exc}")

        if lifecycle == "APPROVED":
            for key in ("verified", "commercialUse", "modificationAllowed", "redistributionAllowed", "appBundlingAllowed"):
                if not _bool(license_data, key):
                    errors.append(f"{prefix}.license.{key} must be true for APPROVED assets")
            if not _text(license_data, "rightsHolder"):
                errors.append(f"{prefix}.license.rightsHolder is required for APPROVED assets")
            if not _text(license_data, "attributionText") and license_data.get("attributionRequired") is not False:
                errors.append(f"{prefix}.license attribution decision/text is incomplete")
            review = asset.get("review") if isinstance(asset.get("review"), dict) else {}
            score = review.get("realismScore")
            if _text(review, "visualStatus") != "PASS" or not isinstance(score, (int, float)) or score < 4:
                errors.append(f"{prefix}.review requires visualStatus PASS and realismScore >= 4")
            if (_text(review, "technicalStatus") != "PASS" or not _text(review, "reviewer")
                    or not _text(review, "reviewedAt") or not _text(review, "decisionReason")):
                errors.append(f"{prefix}.review requires technical PASS and reviewer/date/decisionReason")
            targets = asset.get("targets")
            if not isinstance(targets, list) or not targets:
                errors.append(f"{prefix}.targets must identify an exact tested runtime/version")
            else:
                for target_index, target in enumerate(targets):
                    if (not isinstance(target, dict) or not _text(target, "runtime")
                            or not _text(target, "version") or _text(target, "testResult") != "PASS"):
                        errors.append(f"{prefix}.targets[{target_index}] needs exact runtime/version and testResult PASS")
            if _text(asset, "budgetStatus") != "PASS":
                errors.append(f"{prefix}.budgetStatus must be PASS before APPROVED packaging")
            measurements = asset.get("resourceMeasurements")
            if (not isinstance(measurements, dict) or not measurements or
                    not all(isinstance(k, str) and k.strip() and isinstance(v, (int, float)) and v >= 0
                            for k, v in (measurements.items() if isinstance(measurements, dict) else []))):
                errors.append(f"{prefix}.resourceMeasurements must contain measured non-negative numeric values")
            scopes, dependencies = asset.get("approvedScopes"), asset.get("dependencies")
            if not isinstance(scopes, list) or not scopes or not all(isinstance(x, str) and x.strip() for x in scopes):
                errors.append(f"{prefix}.approvedScopes must be a non-empty list of scopes")
            if not isinstance(dependencies, list):
                errors.append(f"{prefix}.dependencies must be an explicit list (empty when none)")
            if not errors:
                eligible_ids.append(asset_id)

    if errors:
        raise IntakeError("\n".join(errors))
    return {
        "schemaVersion": 1, "batchId": batch_id, "batchStatus": _text(manifest, "batchStatus"),
        "assetCount": len(assets), "fileCount": file_count,
        "lifecycleCounts": dict(sorted(counts.items())),
        "mechanicallyEligibleForHumanFinalReview": eligible_ids,
        "productionRegistryModified": False, "runtimeDownloadsPerformed": False,
        "result": "EMPTY_BATCH" if not assets else "VALIDATED_CANDIDATES",
    }

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--repo-root", default=Path.cwd(), type=Path)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args(argv)
    try:
        report = validate_batch(args.manifest, args.repo_root)
    except (IntakeError, OSError) as exc:
        print(f"ASSET INTAKE FAILED CLOSED\n{exc}", file=sys.stderr)
        return 2
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    print(rendered, end="")
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(rendered, encoding="utf-8")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
