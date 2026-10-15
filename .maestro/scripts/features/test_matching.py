"""Matching hook boundary tests; authenticated UI behavior is checked by Maestro."""

import importlib.util
from pathlib import Path
import re
from types import SimpleNamespace
import unittest
from unittest.mock import Mock


SPEC = importlib.util.spec_from_file_location("matching_feature_hooks", Path(__file__).with_name("matching.py"))
MATCHING = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MATCHING)


class Failure(Exception):
    pass


class MatchingHooksTest(unittest.TestCase):
    def setUp(self):
        self.core = SimpleNamespace(RunnerFailure=Failure, APP_ID="com.project200.undabang")
        self.device = Mock()
        self.device.require.return_value = b"1\n"
        self.variables = {
            "MATCHING_LATITUDE": "37.5", "MATCHING_LONGITUDE": "127.0",
            "MATCHING_CATALOG_NAME": "실제 이름 (A)", "MATCHING_CATALOG_ADDRESS": "주소 1.2",
            "MATCHING_PEER_NICKNAME": "이름 [B]", "MATCHING_PEER_ID": "peer-id",
            "MATCHING_PEER_LOCATION_ID": "31",
        }
        self.run_id = "0123456789abcdef"

    def test_before_flow_uses_resolved_coordinates_and_literal_identity_patterns(self):
        patterns = MATCHING.before_flow(
            self.core, self.device, "matching-place-crud", self.variables, {}, self.run_id,
        )
        self.device.require.assert_any_call("emu", "geo", "fix", "127.0", "37.5")
        self.assertIsNotNone(re.fullmatch(patterns["MATCHING_CATALOG_NAME_PATTERN"], "실제 이름 (A)"))
        self.assertIsNone(re.fullmatch(patterns["MATCHING_CATALOG_NAME_PATTERN"], "실제 이름 A"))
        self.assertIsNotNone(re.fullmatch(patterns["MATCHING_PEER_ROW_PATTERN"], "matching_member_peer-id_31"))

    def test_missing_peer_data_fails_before_any_device_mutation(self):
        del self.variables["MATCHING_PEER_ID"]
        with self.assertRaisesRegex(Failure, "matching-fixture-variables-invalid"):
            MATCHING.before_flow(self.core, self.device, "matching-member-to-chat", self.variables, {}, self.run_id)
        self.device.require.assert_not_called()

    def test_nonfinite_coordinates_do_not_fall_back_to_a_public_location(self):
        self.variables["MATCHING_LATITUDE"] = "nan"
        with self.assertRaisesRegex(Failure, "matching-fixture-coordinates-invalid"):
            MATCHING.before_flow(self.core, self.device, "matching-place-crud", self.variables, {}, self.run_id)
        self.device.require.assert_not_called()

    def test_verification_failure_still_restores_both_permissions_and_fails(self):
        self.device.bootstrap.side_effect = Failure("fixed-bootstrap-failure")
        with self.assertRaisesRegex(Failure, "matching-server-verification-failed"):
            MATCHING.after_flow(self.core, self.device, "matching-member-to-chat", {}, {}, self.run_id)
        for permission in ("android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"):
            self.device.require.assert_any_call("shell", "pm", "grant", self.core.APP_ID, permission)


if __name__ == "__main__":
    unittest.main()
