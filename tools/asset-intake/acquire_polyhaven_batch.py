#!/usr/bin/env python3
"""Acquire exact Poly Haven CC0 HDRI files for WOLF's offline review batch."""
from __future__ import annotations
import argparse, hashlib, json, re, sys, time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

USER_AGENT = "WOLF-OFFLINE-VIDEO-Asset-Intake/1.0 (https://github.com/WolfCodeS-sigma/WOLF-OFFLINE-VIDEO)"
LICENSE_URL = "https://polyhaven.com/license"
API_ROOT = "https://api.polyhaven.com"
BATCH_ID = "WOLF-ASSET-BATCH-001"
ASSETS = (
    {"asset_id": "debris_basement_corridor", "wolf_id": "wolf-env-debris-basement-corridor-hdri", "page_url": "https://polyhaven.com/a/debris_basement_corridor"},
    {"asset_id": "abandoned_hall_01", "wolf_id": "wolf-env-abandoned-hall-01-hdri", "page_url": "https://polyhaven.com/a/abandoned_hall_01"},
)

class AcquisitionError(RuntimeError):
    pass

def fetch_bytes(url: str, retries: int = 4) -> bytes:
    parsed = urlparse(url)
    if parsed.scheme != "https" or not parsed.netloc:
        raise AcquisitionError(f"Refusing non-HTTPS source URL: {url}")
    last_error = None
    for attempt in range(retries):
        try:
            request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "*/*"})
            with urlopen(request, timeout=90) as response:
                if response.status < 200 or response.status >= 300:
                    raise AcquisitionError(f"Unexpected HTTP status {response.status} for {url}")
                return response.read()
        except (HTTPError, URLError, TimeoutError, OSError) as exc:
            last_error = exc
            if attempt + 1 < retries:
                time.sleep(2 ** attempt)
    raise AcquisitionError(f"Failed to fetch {url}: {last_error}")

def fetch_json(url: str) -> dict[str, Any]:
    try:
        value = json.loads(fetch_bytes(url).decode("utf-8"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AcquisitionError(f"Invalid JSON from {url}: {exc}") from exc
    if not isinstance(value, dict):
        raise AcquisitionError(f"Expected JSON object from {url}")
    return value

def select_2k_hdr_file(files_data: dict[str, Any]) -> dict[str, Any]:
    try:
        selected = files_data["hdri"]["2k"]["hdr"]
    except (KeyError, TypeError) as exc:
        raise AcquisitionError("Poly Haven API did not expose the expected 2K HDR file") from exc
    if not isinstance(selected, dict):
        raise AcquisitionError("2K HDR metadata is not an object")
    url, md5, size = selected.get("url"), selected.get("md5"), selected.get("size")
    if not isinstance(url, str) or not isinstance(md5, str) or not re.fullmatch(r"[0-9a-fA-F]{32}", md5):
        raise AcquisitionError("2K HDR metadata is missing a valid URL or MD5")
    if not isinstance(size, int) or isinstance(size, bool) or not (1024 <= size <= 32 * 1024 * 1024):
        raise AcquisitionError(f"2K HDR size is outside the safe intake cap: {size!r}")
    return selected

def candidate_record(*, asset_id: str, wolf_id: str, name: str, creator: str,
        page_url: str, acquired_at: str, relative_path: str, byte_size: int,
        sha256: str, provider_md5: str) -> dict[str, Any]:
    return {
        "assetId": wolf_id, "name": name, "category": "ENVIRONMENT",
        "subtype": "HDRI_ENVIRONMENT_LIGHTING", "lifecycle": "CANDIDATE",
        "acquisitionState": "ACQUIRED_NOT_APPROVED",
        "source": {
            "name": "Poly Haven", "url": page_url,
            "apiInfoUrl": f"{API_ROOT}/info/{asset_id}",
            "apiFilesUrl": f"{API_ROOT}/files/{asset_id}",
            "creator": creator, "acquiredAt": acquired_at,
            "acquisitionMethod": "Poly Haven public API 2K Radiance HDR direct download; provider MD5 and exact byte size verified"
        },
        "license": {
            "rightsHolder": creator, "name": "CC0 1.0 Universal", "version": "1.0",
            "evidenceUrl": LICENSE_URL, "verified": True, "commercialUse": True,
            "modificationAllowed": True, "redistributionAllowed": True,
            "appBundlingAllowed": True, "attributionRequired": False,
            "attributionText": "",
            "restrictions": "CC0; no copyright attribution required. Retain source/creator provenance internally."
        },
        "review": {
            "visualStatus": "PENDING", "realismScore": None, "technicalStatus": "PENDING",
            "reviewer": "", "reviewedAt": "",
            "decisionReason": "Acquired candidate only; WOLF visual review, runtime import/conversion, and resource measurement remain pending."
        },
        "targets": [], "budgetStatus": "PENDING", "resourceMeasurements": {},
        "approvedScopes": [], "dependencies": [],
        "files": [{
            "relativePath": relative_path, "format": "image/vnd.radiance",
            "byteSize": byte_size, "sha256": sha256,
            "providerMd5": provider_md5, "resolution": "2k"
        }]
    }

def acquire_batch(output_root: Path) -> dict[str, Any]:
    output_root.mkdir(parents=True, exist_ok=True)
    files_root, provenance_root = output_root / "files", output_root / "provenance"
    files_root.mkdir(parents=True, exist_ok=True)
    provenance_root.mkdir(parents=True, exist_ok=True)
    license_text = fetch_bytes(LICENSE_URL).decode("utf-8", errors="replace").lower()
    if "cc0" not in license_text or "commercial" not in license_text:
        raise AcquisitionError("Poly Haven license page no longer confirms CC0 commercial use")
    acquired_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    records = []
    for item in ASSETS:
        asset_id = item["asset_id"]
        info_url, files_url = f"{API_ROOT}/info/{asset_id}", f"{API_ROOT}/files/{asset_id}"
        info, file_manifest = fetch_json(info_url), fetch_json(files_url)
        meta = select_2k_hdr_file(file_manifest)
        name, authors = info.get("name"), info.get("authors")
        creator = next(iter(authors.keys()), "") if isinstance(authors, dict) else ""
        if not isinstance(name, str) or not name.strip() or not isinstance(creator, str) or not creator.strip():
            raise AcquisitionError(f"Missing exact asset name or creator metadata for {asset_id}")
        payload = fetch_bytes(str(meta["url"]))
        actual_size, actual_md5 = len(payload), hashlib.md5(payload).hexdigest()
        if actual_size != meta["size"]:
            raise AcquisitionError(f"Byte-size mismatch for {asset_id}: API={meta['size']} downloaded={actual_size}")
        if actual_md5.lower() != meta["md5"].lower():
            raise AcquisitionError(f"Poly Haven MD5 mismatch for {asset_id}; refusing the file")
        sha256 = hashlib.sha256(payload).hexdigest()
        file_name, relative_path = f"{asset_id}_2k.hdr", f"files/{asset_id}_2k.hdr"
        destination = files_root / file_name
        temporary = destination.with_suffix(destination.suffix + ".part")
        temporary.write_bytes(payload)
        temporary.replace(destination)
        (provenance_root / f"{asset_id}.info.json").write_text(
            json.dumps(info, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        (provenance_root / f"{asset_id}.files.json").write_text(
            json.dumps(file_manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        records.append(candidate_record(
            asset_id=asset_id, wolf_id=item["wolf_id"], name=name, creator=creator,
            page_url=item["page_url"], acquired_at=acquired_at, relative_path=relative_path,
            byte_size=actual_size, sha256=sha256, provider_md5=actual_md5))
    manifest = {
        "schemaVersion": 1, "batchId": BATCH_ID, "batchStatus": "OPEN",
        "createdAt": acquired_at, "policyId": "WOLF-ASSET-POLICY-001",
        "registryId": "WOLF-ASSET-REGISTRY-001",
        "batchPurpose": "First real, licensed environment-HDRI acquisition batch; all entries remain CANDIDATE.",
        "sourceCredit": "Assets sourced from Poly Haven. The live API is used only during developer/CI intake; WOLF does not call the API at runtime.",
        "licenseEvidence": {"name": "CC0 1.0 Universal", "url": LICENSE_URL,
            "verifiedFromSourcePage": True, "commercialUse": True, "applicationBundling": True},
        "assets": records
    }
    (output_root / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    report = {
        "result": "ACQUIRED_CANDIDATES", "batchId": BATCH_ID,
        "assetCount": len(records), "fileCount": sum(len(x["files"]) for x in records),
        "totalBytes": sum(x["files"][0]["byteSize"] for x in records),
        "lifecycle": "CANDIDATE", "productionRegistryModified": False,
        "runtimeDownloadsPerformed": False
    }
    (output_root / "acquisition-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return report

def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-root", required=True, type=Path)
    args = parser.parse_args(argv)
    try:
        report = acquire_batch(args.output_root)
    except (AcquisitionError, OSError) as exc:
        print(f"POLY HAVEN ACQUISITION FAILED CLOSED\n{exc}", file=sys.stderr)
        return 2
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
