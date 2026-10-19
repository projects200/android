"""Matching hook boundary tests; authenticated UI behavior is checked by Maestro."""

import importlib.util
import json
from pathlib import Path
import re
import stat
import tempfile
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


class MatchingDiagnosticsTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="matching-map-test-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name)
        self.core = SimpleNamespace(RunnerFailure=Failure, APP_ID="com.project200.undabang")
        self.device = Mock()
        self.run_id = "0123456789abcdef"
        self.secret = "https://private.invalid/request?token=synthetic-secret"

    def collect(self, events=None, status="ok", raw=None, run_id=None):
        self.device.require.side_effect = None
        self.device.require.return_value = (
            json.dumps({"runId": run_id or self.run_id, "events": events or []}).encode()
            if raw is None else raw
        )
        arguments = (
            self.core, self.device, "matching-member-to-chat", {"PRIVATE": self.secret},
            {"authState": self.secret}, self.run_id, self.output,
        )
        if status == "ok":
            MATCHING.collect_diagnostics(*arguments)
        else:
            with self.assertRaisesRegex(Failure, r"^matching-map-diagnostics-unavailable$"):
                MATCHING.collect_diagnostics(*arguments)
        path = self.output / MATCHING.DIAGNOSTICS_FILE
        content = path.read_text()
        self.assertNotIn(self.secret, content)
        self.assertEqual(stat.S_IMODE(path.stat().st_mode), 0o600)
        result = json.loads(content)
        self.assertEqual(result["status"], status)
        self.assertEqual(set(result), {"schemaVersion", "status", "finalState", "events", "labelClicks", "placeLoads"})
        self.assertEqual(result["schemaVersion"], 1)
        return result

    def loading(self, sequence=1):
        return {"type": "map", "sequence": sequence, "attempt": sequence, "state": "loading"}

    def failed(self, code, category, sequence=2, attempt=1):
        return {
            "type": "map", "sequence": sequence, "attempt": attempt, "state": "failed",
            "category": category, "sdkErrorCode": code,
        }

    def place_load(self, sequence=1, request=None, state="started", elapsed_ms=0, category=None):
        event = {
            "type": "place_load", "sequence": sequence, "request": sequence if request is None else request,
            "state": state, "elapsedMs": elapsed_ms,
        }
        if category is not None:
            event["category"] = category
        return event

    def test_only_safe_map_fields_survive_private_fixture_evidence(self):
        result = self.collect([
            {"type": "places", "places": [{"id": self.secret, "address": self.secret}]},
            self.loading(2),
            self.failed(1001, "initialization", sequence=3, attempt=2),
            {"type": "chat", "peerId": self.secret, "roomId": self.secret},
            {"type": "denied", "locationId": self.secret},
        ])
        self.assertEqual(result["finalState"], "failed")
        self.assertEqual(result["events"], [
            {"sequence": 2, "attempt": 2, "state": "loading"},
            {"sequence": 3, "attempt": 2, "state": "failed",
             "category": "initialization", "sdkErrorCode": 1001},
        ])
        self.assertEqual(len(list(self.output.iterdir())), 1)

    def test_missing_callback_or_loading_does_not_claim_authentication(self):
        self.assertEqual(self.collect()["finalState"], "not_observed")
        result = self.collect([self.loading()])
        self.assertEqual(result["finalState"], "loading")
        self.assertNotIn("category", result["events"][0])
        self.assertNotIn("sdkErrorCode", result["events"][0])

    def test_public_codes_and_unknown_integer_remain_distinct(self):
        cases = {
            -1: "unknown", -2: "network", -3: "network", -4: "network",
            400: "request", 401: "auth", 403: "auth", 429: "quota", 499: "network",
            1001: "initialization", 1002: "rendering", 1003: "auth", 7654321: "unknown",
        }
        for code, category in cases.items():
            with self.subTest(code=code):
                event = self.collect([self.loading(), self.failed(code, category)])["events"][-1]
                self.assertEqual(event["sdkErrorCode"], code)
                self.assertEqual(event["category"], category)
        for category in ("network", "unknown"):
            result = self.collect([self.loading(), self.failed(None, category)])
            self.assertIsNone(result["events"][-1]["sdkErrorCode"])

    def test_reentry_orders_new_map_attempt_without_reusing_failed_state(self):
        result = self.collect([
            self.loading(), self.failed(401, "auth"), self.loading(3),
            {"type": "map", "sequence": 4, "attempt": 3, "state": "ready"},
        ])
        self.assertEqual(result["finalState"], "ready")
        self.assertEqual([event["attempt"] for event in result["events"]], [1, 1, 3, 3])

    def test_label_clicks_export_only_actual_callback_kind_and_attempt(self):
        result = self.collect([
            self.loading(),
            {"type": "map", "sequence": 2, "attempt": 1, "state": "ready"},
            {"type": "places", "places": [{"address": self.secret}]},
            {"type": "map_label_click", "sequence": 4, "attempt": 1, "kind": "place"},
            {"type": "map_label_click", "sequence": 5, "attempt": 1, "kind": "cluster"},
            {"type": "map_label_click", "sequence": 6, "attempt": 1, "kind": "unknown"},
        ])
        self.assertEqual(result["finalState"], "ready")
        self.assertEqual(result["labelClicks"], [
            {"sequence": 4, "attempt": 1, "kind": "place"},
            {"sequence": 5, "attempt": 1, "kind": "cluster"},
            {"sequence": 6, "attempt": 1, "kind": "unknown"},
        ])
        self.assertEqual(len(result["events"]), 2)

    def test_label_click_fields_types_and_map_attempt_fail_closed(self):
        valid = {"type": "map_label_click", "sequence": 3, "attempt": 1, "kind": "cluster"}
        for event in (
            {**valid, "kind": self.secret}, {**valid, "kind": []},
            {**valid, "sequence": True}, {**valid, "sequence": 4},
            {**valid, "attempt": True}, {**valid, "attempt": 2},
            {**valid, "peerId": self.secret}, {**valid, "latitude": 37.5},
        ):
            with self.subTest(event=event):
                result = self.collect([
                    self.loading(), {"type": "map", "sequence": 2, "attempt": 1, "state": "ready"}, event,
                ], status="invalid_evidence")
                self.assertEqual(result["events"], [])
                self.assertEqual(result["labelClicks"], [])
        self.collect([{**valid, "sequence": 1}], status="invalid_evidence")
        self.collect([self.loading(), {**valid, "sequence": 2}], status="invalid_evidence")
        self.collect([self.loading(), self.failed(1001, "initialization"), valid], status="invalid_evidence")

    def test_unknown_fields_strings_types_or_invalid_transitions_fail_closed(self):
        invalid = [
            {**self.failed(401, "auth"), "message": self.secret},
            {**self.failed(401, "auth"), "category": self.secret},
            self.failed(1001, "auth"),
            self.failed(None, "auth"),
            self.failed(True, "unknown"),
            self.failed(401.0, "auth"),
            self.failed("401", "auth"),
            self.failed(2147483648, "unknown"),
            {**self.failed(401, "auth"), "attempt": True},
            {**self.failed(401, "auth"), "sequence": True},
            {**self.failed(401, "auth"), "sequence": 3},
            {**self.failed(401, "auth"), "state": self.secret},
            {**self.failed(401, "auth"), "state": []},
            {"type": self.secret},
        ]
        for event in invalid:
            with self.subTest(event=event):
                result = self.collect([self.loading(), event], status="invalid_evidence")
                self.assertEqual(result["events"], [])
                self.assertEqual(result["finalState"], "unknown")
        self.collect([self.failed(401, "auth", sequence=1)], status="invalid_evidence")
        self.collect([
            self.loading(), self.failed(1002, "rendering"),
            {"type": "map", "sequence": 3, "attempt": 1, "state": "ready"},
        ], status="invalid_evidence")

    def test_stale_broken_or_oversized_evidence_exports_only_fixed_status(self):
        self.collect(run_id=self.secret, status="stale_evidence")
        for raw in (
            b"{", b"\xff", b"[]", b"null",
            json.dumps({"runId": self.run_id, "events": [], "token": self.secret}).encode(),
            json.dumps({"runId": self.run_id, "events": True}).encode(),
        ):
            with self.subTest(raw=raw):
                self.collect(raw=raw, status="invalid_evidence")
        self.collect(raw=b"x" * (MATCHING.MAX_EVIDENCE_BYTES + 1), status="size_limit")
        events = [self.loading(index) for index in range(1, 1001)]
        self.assertEqual(len(self.collect(events)["events"]), 1000)
        self.collect(events + [self.loading(1001)], status="invalid_evidence")

    def test_place_load_start_without_terminal_remains_unfinished(self):
        self.assertEqual(self.collect()["placeLoads"], [])
        result = self.collect([self.place_load()])
        self.assertEqual(result["finalState"], "not_observed")
        self.assertEqual(result["events"], [])
        self.assertEqual(result["placeLoads"], [
            {"sequence": 1, "request": 1, "state": "started", "elapsedMs": 0},
        ])

    def test_place_load_terminals_export_only_fixed_categories_and_elapsed_time(self):
        for state, categories in (
            ("success", (None,)), ("cancelled", (None,)),
            ("error", ("network", "mapping", "auth", "server", "unknown")),
            ("exception", ("network", "mapping", "auth", "server", "unknown")),
        ):
            for category in categories:
                with self.subTest(state=state, category=category):
                    result = self.collect([
                        self.place_load(),
                        self.place_load(2, 1, state, 30123, category),
                    ])
                    expected = {"sequence": 2, "request": 1, "state": state, "elapsedMs": 30123}
                    if category is not None:
                        expected["category"] = category
                    self.assertEqual(result["placeLoads"][-1], expected)
                    self.assertEqual(result["finalState"], "not_observed")

    def test_interleaved_place_requests_preserve_map_labels_and_global_sequence(self):
        result = self.collect([
            self.loading(),
            self.place_load(2),
            {"type": "map", "sequence": 3, "attempt": 1, "state": "ready"},
            {"type": "places", "places": [{"id": self.secret, "latitude": self.secret}]},
            self.place_load(5),
            self.place_load(6, 5, "error", 12, "unknown"),
            {"type": "map_label_click", "sequence": 7, "attempt": 1, "kind": "cluster"},
            {"type": "chat", "peerId": self.secret, "roomId": self.secret},
            self.place_load(9, 2, "success", 50),
            {"type": "map_label_click", "sequence": 10, "attempt": 1, "kind": "place"},
            self.place_load(11),
            {"type": "denied", "locationId": self.secret},
        ])
        self.assertEqual(result["finalState"], "ready")
        self.assertEqual(result["events"], [
            {"sequence": 1, "attempt": 1, "state": "loading"},
            {"sequence": 3, "attempt": 1, "state": "ready"},
        ])
        self.assertEqual(result["labelClicks"], [
            {"sequence": 7, "attempt": 1, "kind": "cluster"},
            {"sequence": 10, "attempt": 1, "kind": "place"},
        ])
        self.assertEqual(result["placeLoads"], [
            {"sequence": 2, "request": 2, "state": "started", "elapsedMs": 0},
            {"sequence": 5, "request": 5, "state": "started", "elapsedMs": 0},
            {"sequence": 6, "request": 5, "state": "error", "elapsedMs": 12, "category": "unknown"},
            {"sequence": 9, "request": 2, "state": "success", "elapsedMs": 50},
            {"sequence": 11, "request": 11, "state": "started", "elapsedMs": 0},
        ])

    def test_place_load_fields_types_and_categories_fail_closed(self):
        valid = self.place_load(2, 1, "error", 30, "unknown")
        invalid = [
            {**valid, "message": self.secret}, {**valid, "errorCode": self.secret},
            {**valid, "cause": self.secret}, {**valid, "class": self.secret},
            {**valid, "url": self.secret}, {**valid, "placeId": self.secret},
            {**valid, "count": 2}, {**valid, "latitude": 37.5},
            {**valid, "category": self.secret}, {**valid, "category": []},
            {**valid, "category": None}, {**valid, "state": self.secret},
            {**valid, "state": []}, {**valid, "sequence": True},
            {**valid, "sequence": 2.0}, {**valid, "sequence": 3},
            {**valid, "request": True}, {**valid, "request": 1.0},
            {**valid, "request": 0}, {**valid, "request": 1001},
            {**valid, "elapsedMs": True}, {**valid, "elapsedMs": 1.0},
            {**valid, "elapsedMs": "30"}, {**valid, "elapsedMs": -1},
            {**valid, "elapsedMs": 9223372036854775808},
            self.place_load(2, 1, "error", 30),
            self.place_load(2, 1, "exception", 30),
            self.place_load(2, 1, "success", 30, "unknown"),
            self.place_load(2, 1, "cancelled", 30, "unknown"),
        ]
        invalid.extend({key: value for key, value in valid.items() if key != missing} for missing in valid)
        for event in invalid:
            with self.subTest(event=event):
                result = self.collect([self.place_load(), event], status="invalid_evidence")
                self.assertEqual(result["events"], [])
                self.assertEqual(result["labelClicks"], [])
                self.assertEqual(result["placeLoads"], [])
                self.assertEqual(result["finalState"], "unknown")

    def test_place_load_requires_one_start_and_at_most_one_terminal_per_request(self):
        cases = [
            [self.place_load(1, 1, "success", 0)],
            [self.place_load(elapsed_ms=1)],
            [self.place_load(category="unknown")],
            [self.place_load(request=2)],
            [self.place_load(), self.place_load(2, 1)],
            [self.place_load(), self.place_load(2, 2, "success", 1)],
            [self.place_load(), self.place_load(2, 1, "success", 1), self.place_load(3, 1, "cancelled", 2)],
            [self.place_load(), self.place_load(2, 1, "error", 1, "network"), self.place_load(3, 1, "success", 2)],
            [self.loading(), self.place_load(2, 1, "exception", 1, "unknown")],
        ]
        for events in cases:
            with self.subTest(events=events):
                self.assertEqual(self.collect(events, status="invalid_evidence")["placeLoads"], [])

    def test_stale_malformed_and_limited_place_evidence_never_exports_partial_progress(self):
        self.assertEqual(self.collect([self.place_load()], run_id=self.secret, status="stale_evidence")["placeLoads"], [])
        malformed = json.dumps({"runId": self.run_id, "events": [self.place_load()]}).encode()[:-1]
        self.assertEqual(self.collect(raw=malformed, status="invalid_evidence")["placeLoads"], [])
        events = [self.place_load(index) for index in range(1, MATCHING.MAX_EVENTS + 1)]
        self.assertEqual(len(self.collect(events)["placeLoads"]), MATCHING.MAX_EVENTS)
        result = self.collect(events + [self.place_load(MATCHING.MAX_EVENTS + 1)], status="invalid_evidence")
        self.assertEqual(result["placeLoads"], [])

    def test_private_read_failure_is_fixed_and_does_not_export_exception(self):
        self.device.require.side_effect = RuntimeError(self.secret)
        with self.assertRaisesRegex(Failure, r"^matching-map-diagnostics-unavailable$"):
            MATCHING.collect_diagnostics(
                self.core, self.device, "matching-place-crud", {}, {}, self.run_id, self.output,
            )
        path = self.output / MATCHING.DIAGNOSTICS_FILE
        self.assertEqual(json.loads(path.read_text()), {
            "schemaVersion": 1, "status": "read_failed", "finalState": "unknown",
            "events": [], "labelClicks": [], "placeLoads": [],
        })
        self.assertNotIn(self.secret, path.read_text())


if __name__ == "__main__":
    unittest.main()
