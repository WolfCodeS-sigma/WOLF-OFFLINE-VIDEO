import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from acquire_polyhaven_batch import AcquisitionError, candidate_record, select_2k_hdr_file

class PolyHavenAcquisitionTests(unittest.TestCase):
    def test_selects_exact_2k_radiance_hdr(self):
        selected = select_2k_hdr_file({"hdri": {
            "1k": {"hdr": {"url": "https://example.org/1k.hdr", "md5": "a"*32, "size": 1000}},
            "2k": {"hdr": {"url": "https://example.org/2k.hdr", "md5": "b"*32, "size": 4096}},
            "8k": {"hdr": {"url": "https://example.org/8k.hdr", "md5": "c"*32, "size": 100000000}}
        }})
        self.assertEqual(selected["url"], "https://example.org/2k.hdr")
        self.assertEqual(selected["size"], 4096)

    def test_rejects_missing_2k_hdr(self):
        with self.assertRaises(AcquisitionError):
            select_2k_hdr_file({"hdri": {"4k": {"hdr": {"url": "https://example.org/4k.hdr", "md5": "a"*32, "size": 4096}}}})

    def test_acquired_asset_remains_candidate_until_wolf_review(self):
        record = candidate_record(asset_id="debris_basement_corridor",
            wolf_id="wolf-env-debris-basement-corridor-hdri", name="Debris Basement Corridor",
            creator="Elvis Posa", page_url="https://polyhaven.com/a/debris_basement_corridor",
            acquired_at="2026-11-22T00:00:00+00:00", relative_path="files/corridor.hdr",
            byte_size=4096, sha256="a"*64, provider_md5="b"*32)
        self.assertEqual(record["lifecycle"], "CANDIDATE")
        self.assertEqual(record["acquisitionState"], "ACQUIRED_NOT_APPROVED")
        self.assertEqual(record["budgetStatus"], "PENDING")
        self.assertEqual(record["targets"], [])
        self.assertEqual(record["approvedScopes"], [])

if __name__ == "__main__":
    unittest.main()
