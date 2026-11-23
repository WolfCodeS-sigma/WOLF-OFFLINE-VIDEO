import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from validate_acquisition_links import LinkValidationError, validate_acquisition_links


def fixtures():
    entries = [
        {
            "assetId": "wolf-env-debris-basement-corridor-hdri",
            "category": "ENVIRONMENT",
            "source": {
                "url": "https://polyhaven.com/a/debris_basement_corridor",
                "creator": "Elvis Posa",
                "acquiredAt": "2026-11-22T02:05:58+00:00",
            },
            "files": [{
                "relativePath": "files/debris_basement_corridor_2k.hdr",
                "format": "image/vnd.radiance",
                "byteSize": 6402928,
                "sha256": "4636e5b2f308c44c550a236752ad171be7af16161789bb2ba07838c39cece78c",
                "providerMd5": "815fbe853a286c294027425a375de393",
                "resolution": "2k",
            }],
        },
        {
            "assetId": "wolf-env-abandoned-hall-01-hdri",
            "category": "ENVIRONMENT",
            "source": {
                "url": "https://polyhaven.com/a/abandoned_hall_01",
                "creator": "Octavian Tudose",
                "acquiredAt": "2026-11-22T02:05:58+00:00",
            },
            "files": [{
                "relativePath": "files/abandoned_hall_01_2k.hdr",
                "format": "image/vnd.radiance",
                "byteSize": 6503079,
                "sha256": "3b3525a4caea1acb18200565acea70d3fd774bcc73b887b4eade0f65a54cd4e6",
                "providerMd5": "0ae542e79a5d5b0bf63816e704efade9",
                "resolution": "2k",
            }],
        },
    ]
    leads = []
    catalog = []
    for asset, source_id in zip(entries, ("debris_basement_corridor", "abandoned_hall_01")):
        source = asset["source"]
        file_record = asset["files"][0]
        leads.append({
            "sourceUrl": source["url"],
            "sourceAssetId": source_id,
            "creator": source["creator"],
            "status": "ACQUIRED_CANDIDATE",
            "acquisition": {
                "batchId": "WOLF-ASSET-BATCH-001",
                "firstAcquiredAt": source["acquiredAt"],
                "resolution": file_record["resolution"],
                **file_record,
            },
        })
        catalog.append({
            "assetId": source_id,
            "wolfAssetId": asset["assetId"],
            "sourceUrl": source["url"],
            "category": "ENVIRONMENT",
            "status": "ACQUIRED_CANDIDATE",
        })
    return (
        {"batchId": "WOLF-ASSET-BATCH-001", "assets": entries},
        {"leads": leads},
        {"batchId": "WOLF-ASSET-BATCH-001", "assets": catalog},
    )


class AcquisitionLinkTests(unittest.TestCase):
    def test_two_acquired_candidates_link_to_exact_batch_and_catalog(self):
        manifest, leads, catalog = fixtures()
        report = validate_acquisition_links(manifest, leads, catalog)
        self.assertEqual(report["result"], "ACQUISITION_LINKS_VALIDATED")
        self.assertEqual(report["assetCount"], 2)
        self.assertEqual(report["acquiredLeadCount"], 2)

    def test_rejects_stale_discovered_status_for_acquired_asset(self):
        manifest, leads, catalog = fixtures()
        leads["leads"][0]["status"] = "DISCOVERED_NOT_ACQUIRED"
        with self.assertRaises(LinkValidationError):
            validate_acquisition_links(manifest, leads, catalog)

    def test_rejects_lead_hash_that_differs_from_acquired_manifest(self):
        manifest, leads, catalog = fixtures()
        leads["leads"][1]["acquisition"]["sha256"] = "f" * 64
        with self.assertRaises(LinkValidationError):
            validate_acquisition_links(manifest, leads, catalog)

    def test_rejects_candidate_lead_that_is_absent_from_batch(self):
        manifest, leads, catalog = fixtures()
        manifest["assets"].pop()
        with self.assertRaises(LinkValidationError):
            validate_acquisition_links(manifest, leads, catalog)

    def test_reacquisition_later_than_first_acquisition_is_valid(self):
        manifest, leads, catalog = fixtures()
        for asset in manifest["assets"]:
            asset["source"]["acquiredAt"] = "2026-11-22T21:21:35+00:00"
        report = validate_acquisition_links(manifest, leads, catalog)
        self.assertEqual(report["result"], "ACQUISITION_LINKS_VALIDATED")

    def test_rejects_first_acquisition_timestamp_after_current_batch(self):
        manifest, leads, catalog = fixtures()
        leads["leads"][0]["acquisition"]["firstAcquiredAt"] = "2026-11-23T00:00:00+00:00"
        with self.assertRaises(LinkValidationError):
            validate_acquisition_links(manifest, leads, catalog)

    def test_rejects_catalog_status_drift(self):
        manifest, leads, catalog = fixtures()
        catalog["assets"][0]["status"] = "ACQUIRE_AND_VERIFY_IN_CI"
        with self.assertRaises(LinkValidationError):
            validate_acquisition_links(manifest, leads, catalog)


if __name__ == "__main__":
    unittest.main()
