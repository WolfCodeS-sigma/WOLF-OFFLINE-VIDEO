#!/usr/bin/env python3
"""Fail-closed cross-check between acquired batch records, source leads, and catalog."""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


class LinkValidationError(ValueError):
    pass


def _load_object(path: Path, label: str) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise LinkValidationError(f"{label} is not readable UTF-8 JSON: {exc}") from exc
    if not isinstance(value, dict):
        raise LinkValidationError(f"{label} root must be an object")
    return value


def _parse_timestamp(value: Any, label: str) -> datetime:
    if not isinstance(value, str) or not value.strip():
        raise LinkValidationError(f"{label} must be an ISO-8601 timestamp")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        raise LinkValidationError(f"{label} is not a valid ISO-8601 timestamp") from exc
    if parsed.tzinfo is None:
        raise LinkValidationError(f"{label} must include a timezone")
    return parsed.astimezone(timezone.utc)


def validate_acquisition_links(
    manifest: dict[str, Any],
    lead_register: dict[str, Any],
    catalog: dict[str, Any],
) -> dict[str, Any]:
    batch_id = manifest.get("batchId")
    assets = manifest.get("assets")
    leads = lead_register.get("leads")
    catalog_assets = catalog.get("assets")
    if not isinstance(batch_id, str) or not batch_id.strip():
        raise LinkValidationError("batch manifest must contain batchId")
    if not isinstance(assets, list) or not assets:
        raise LinkValidationError("acquired batch must contain a non-empty assets array")
    if not isinstance(leads, list):
        raise LinkValidationError("source-lead register must contain a leads array")
    if not isinstance(catalog_assets, list):
        raise LinkValidationError("acquisition catalog must contain an assets array")
    if catalog.get("batchId") != batch_id:
        raise LinkValidationError("acquisition catalog batchId does not match the acquired manifest")

    def unique_index(items: list[Any], key: str, label: str) -> dict[str, dict[str, Any]]:
        indexed: dict[str, dict[str, Any]] = {}
        for index, item in enumerate(items):
            if not isinstance(item, dict):
                raise LinkValidationError(f"{label}[{index}] must be an object")
            value = item.get(key)
            if not isinstance(value, str) or not value.strip():
                raise LinkValidationError(f"{label}[{index}].{key} is required")
            if value in indexed:
                raise LinkValidationError(f"duplicate {key} in {label}: {value}")
            indexed[value] = item
        return indexed

    lead_by_url = unique_index(leads, "sourceUrl", "leads")
    catalog_by_url = unique_index(catalog_assets, "sourceUrl", "catalog.assets")
    asset_by_url: dict[str, dict[str, Any]] = {}

    for index, asset in enumerate(assets):
        if not isinstance(asset, dict):
            raise LinkValidationError(f"manifest.assets[{index}] must be an object")
        source = asset.get("source")
        if not isinstance(source, dict):
            raise LinkValidationError(f"manifest.assets[{index}].source must be an object")
        source_url = source.get("url")
        if not isinstance(source_url, str) or not source_url.strip():
            raise LinkValidationError(f"manifest.assets[{index}].source.url is required")
        if source_url in asset_by_url:
            raise LinkValidationError(f"duplicate source URL in acquired manifest: {source_url}")
        asset_by_url[source_url] = asset
        lead = lead_by_url.get(source_url)
        catalog_item = catalog_by_url.get(source_url)
        if lead is None:
            raise LinkValidationError(f"acquired source has no matching source-lead record: {source_url}")
        if catalog_item is None:
            raise LinkValidationError(f"acquired source has no matching catalog record: {source_url}")
        if lead.get("status") != "ACQUIRED_CANDIDATE":
            raise LinkValidationError(f"source lead still has a stale/non-acquired status: {source_url}")
        if catalog_item.get("status") != "ACQUIRED_CANDIDATE":
            raise LinkValidationError(f"catalog status is not ACQUIRED_CANDIDATE: {source_url}")
        if lead.get("sourceAssetId") != catalog_item.get("assetId"):
            raise LinkValidationError(f"sourceAssetId mismatch between lead and catalog: {source_url}")
        if lead.get("creator") != source.get("creator"):
            raise LinkValidationError(f"creator mismatch between lead and acquired manifest: {source_url}")
        if catalog_item.get("wolfAssetId") != asset.get("assetId"):
            raise LinkValidationError(f"WOLF asset ID mismatch between catalog and manifest: {source_url}")
        if catalog_item.get("category") != asset.get("category"):
            raise LinkValidationError(f"category mismatch between catalog and manifest: {source_url}")

        acquisition = lead.get("acquisition")
        if not isinstance(acquisition, dict):
            raise LinkValidationError(f"acquired lead lacks acquisition evidence: {source_url}")
        if acquisition.get("batchId") != batch_id:
            raise LinkValidationError(f"lead batchId mismatch: {source_url}")
        first_acquired_at = _parse_timestamp(
            acquisition.get("firstAcquiredAt"), f"lead acquisition.firstAcquiredAt for {source_url}"
        )
        current_batch_acquired_at = _parse_timestamp(
            source.get("acquiredAt"), f"manifest source.acquiredAt for {source_url}"
        )
        if first_acquired_at > current_batch_acquired_at:
            raise LinkValidationError(
                f"lead first-acquisition timestamp is later than the current batch acquisition: {source_url}"
            )
        if acquisition.get("resolution") != "2k":
            raise LinkValidationError(f"lead resolution must be the acquired 2k variant: {source_url}")
        records = asset.get("files")
        if not isinstance(records, list) or len(records) != 1 or not isinstance(records[0], dict):
            raise LinkValidationError(f"expected exactly one acquired HDR file record for: {source_url}")
        file_record = records[0]
        expected = {
            "relativePath": file_record.get("relativePath"),
            "format": file_record.get("format"),
            "byteSize": file_record.get("byteSize"),
            "sha256": file_record.get("sha256"),
            "providerMd5": file_record.get("providerMd5"),
            "resolution": file_record.get("resolution"),
        }
        for key, value in expected.items():
            if acquisition.get(key) != value:
                raise LinkValidationError(f"lead acquisition.{key} does not match manifest for: {source_url}")
        if file_record.get("resolution") != "2k" or file_record.get("format") != "image/vnd.radiance":
            raise LinkValidationError(f"acquired record is not the expected 2K Radiance HDR variant: {source_url}")

    for source_url, lead in lead_by_url.items():
        status = lead.get("status")
        if status == "ACQUIRED_CANDIDATE":
            if source_url not in asset_by_url:
                raise LinkValidationError(f"lead claims ACQUIRED_CANDIDATE but is absent from this batch: {source_url}")
            if source_url not in catalog_by_url:
                raise LinkValidationError(f"acquired lead is absent from catalog: {source_url}")
        elif status == "DISCOVERED_NOT_ACQUIRED":
            if source_url in asset_by_url:
                raise LinkValidationError(f"lead is still marked unacquired but exists in acquired manifest: {source_url}")
        else:
            raise LinkValidationError(f"unsupported source-lead status {status!r}: {source_url}")

    if len(catalog_by_url) != len(asset_by_url):
        raise LinkValidationError("catalog contains entries that are not represented in the acquired manifest")
    for source_url, catalog_item in catalog_by_url.items():
        if source_url not in asset_by_url:
            raise LinkValidationError(f"catalog has no acquired manifest entry: {source_url}")
        if catalog_item.get("status") != "ACQUIRED_CANDIDATE":
            raise LinkValidationError(f"catalog item has inconsistent status: {source_url}")

    return {
        "result": "ACQUISITION_LINKS_VALIDATED",
        "batchId": batch_id,
        "assetCount": len(assets),
        "acquiredLeadCount": sum(1 for lead in leads if lead.get("status") == "ACQUIRED_CANDIDATE"),
        "catalogCount": len(catalog_assets),
        "productionRegistryModified": False,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--lead-register", required=True, type=Path)
    parser.add_argument("--catalog", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        report = validate_acquisition_links(
            _load_object(args.manifest, "manifest"),
            _load_object(args.lead_register, "lead-register"),
            _load_object(args.catalog, "catalog"),
        )
    except LinkValidationError as exc:
        print(f"ACQUISITION LINK VALIDATION FAILED CLOSED\n{exc}", file=sys.stderr)
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
