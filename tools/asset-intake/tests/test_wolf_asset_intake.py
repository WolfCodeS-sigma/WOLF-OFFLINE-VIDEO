import hashlib, json, sys, tempfile, unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from wolf_asset_intake import IntakeError, validate_batch

class AssetIntakeTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="wolf-intake-test-")
        self.root = Path(self.temp.name)
        self.batch = self.root / "asset-intake" / "batches" / "WOLF-ASSET-BATCH-001"
        (self.batch / "files").mkdir(parents=True)
        self.file = self.batch / "files" / "scene.bin"
        self.file.write_bytes(b"local-candidate-bytes")
        self.manifest_path = self.batch / "manifest.json"
        self.manifest = {"schemaVersion": 1, "batchId": "WOLF-ASSET-BATCH-001",
                         "batchStatus": "OPEN", "policyId": "WOLF-ASSET-POLICY-001", "assets": []}

    def tearDown(self):
        self.temp.cleanup()

    def write_manifest(self):
        self.manifest_path.write_text(json.dumps(self.manifest), encoding="utf-8")

    def approved_asset(self):
        raw = self.file.read_bytes()
        return {
            "assetId": "wolf-env-test-corridor", "name": "Test Corridor Environment",
            "category": "ENVIRONMENT", "lifecycle": "APPROVED",
            "source": {"name": "Test Source", "url": "https://example.org/assets/corridor",
                       "creator": "Test Creator", "acquiredAt": "2026-11-22",
                       "acquisitionMethod": "direct-download"},
            "license": {"rightsHolder": "Test Creator", "name": "CC0", "version": "1.0",
                        "evidenceUrl": "https://example.org/license/cc0", "verified": True,
                        "commercialUse": True, "modificationAllowed": True,
                        "redistributionAllowed": True, "appBundlingAllowed": True,
                        "attributionRequired": False, "attributionText": ""},
            "review": {"visualStatus": "PASS", "realismScore": 4.5, "technicalStatus": "PASS",
                       "reviewer": "Test Reviewer", "reviewedAt": "2026-11-22",
                       "decisionReason": "Test-only record for validator coverage"},
            "targets": [{"runtime": "filament", "version": "1.75.0", "testResult": "PASS"}],
            "budgetStatus": "PASS", "resourceMeasurements": {"packagedBytes": len(raw), "decodedBytes": 21},
            "approvedScopes": ["environment_background"], "dependencies": [],
            "files": [{"relativePath": "files/scene.bin", "format": "application/octet-stream",
                       "byteSize": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}],
        }

    def test_empty_batch_is_honestly_reported_and_does_not_promote(self):
        self.write_manifest()
        report = validate_batch(self.manifest_path, self.root)
        self.assertEqual(report["result"], "EMPTY_BATCH")
        self.assertEqual(report["assetCount"], 0)
        self.assertEqual(report["mechanicallyEligibleForHumanFinalReview"], [])
        self.assertFalse(report["productionRegistryModified"])

    def test_valid_candidate_is_not_auto_approved(self):
        asset = self.approved_asset()
        asset["lifecycle"] = "CANDIDATE"
        self.manifest["assets"] = [asset]
        self.write_manifest()
        report = validate_batch(self.manifest_path, self.root)
        self.assertEqual(report["lifecycleCounts"], {"CANDIDATE": 1})
        self.assertEqual(report["mechanicallyEligibleForHumanFinalReview"], [])

    def test_approved_asset_requires_exact_file_hash(self):
        asset = self.approved_asset()
        asset["files"][0]["sha256"] = "0" * 64
        self.manifest["assets"] = [asset]
        self.write_manifest()
        with self.assertRaisesRegex(IntakeError, "SHA-256 mismatch"):
            validate_batch(self.manifest_path, self.root)

    def test_approved_asset_requires_passed_runtime_and_budget(self):
        asset = self.approved_asset()
        asset["targets"][0]["testResult"] = "PENDING"
        asset["budgetStatus"] = "PENDING"
        self.manifest["assets"] = [asset]
        self.write_manifest()
        with self.assertRaises(IntakeError):
            validate_batch(self.manifest_path, self.root)

    def test_complete_record_is_mechanically_eligible_not_auto_promoted(self):
        self.manifest["assets"] = [self.approved_asset()]
        self.write_manifest()
        report = validate_batch(self.manifest_path, self.root)
        self.assertEqual(report["mechanicallyEligibleForHumanFinalReview"], ["wolf-env-test-corridor"])
        self.assertFalse(report["productionRegistryModified"])

    def test_path_traversal_is_rejected(self):
        asset = self.approved_asset()
        asset["files"][0]["relativePath"] = "../../outside.bin"
        self.manifest["assets"] = [asset]
        self.write_manifest()
        with self.assertRaises(IntakeError):
            validate_batch(self.manifest_path, self.root)

    def test_duplicate_asset_ids_are_rejected(self):
        asset = self.approved_asset()
        self.manifest["assets"] = [asset, dict(asset)]
        self.write_manifest()
        with self.assertRaisesRegex(IntakeError, "duplicate assetId"):
            validate_batch(self.manifest_path, self.root)

    def test_unverified_rights_cannot_be_approved(self):
        asset = self.approved_asset()
        asset["license"]["verified"] = False
        self.manifest["assets"] = [asset]
        self.write_manifest()
        with self.assertRaisesRegex(IntakeError, "license.verified must be true"):
            validate_batch(self.manifest_path, self.root)

if __name__ == "__main__":
    unittest.main()
