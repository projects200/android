#!/usr/bin/env python3
"""Check suite validation and restoration failure boundaries without devices."""

import importlib.util
import json
from pathlib import Path
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location(
    "maestro_feature_runner", Path(__file__).with_name("run-tests.py"),
)
runner = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = runner
SPEC.loader.exec_module(runner)

MATCHING_SPEC = importlib.util.spec_from_file_location(
    "maestro_matching_diagnostics", Path(__file__).with_name("features") / "matching.py",
)
matching = importlib.util.module_from_spec(MATCHING_SPEC)
MATCHING_SPEC.loader.exec_module(matching)


class FeatureSuiteTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="maestro-feature-test-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / ".maestro" / "suites").mkdir(parents=True)
        (self.root / ".maestro" / "flows").mkdir()
        for name in ("smoke-tabs", "login-screen", "exercise-form-entry", "exercise-crud", "profile-edit"):
            (self.root / ".maestro" / "flows" / (name + ".yaml")).write_text("appId: test\n---\n")
        patch = mock.patch.object(runner, "ROOT", self.root)
        patch.start()
        self.addCleanup(patch.stop)
        self.descriptor = {
            "flows": ["profile-edit"], "apiLevel": 33, "fixture": True,
            "requiredEnvironment": [], "driver": None,
        }

    def publish(self, descriptor=None):
        path = self.root / ".maestro" / "suites" / "profile.json"
        path.write_text(json.dumps(self.descriptor if descriptor is None else descriptor))

    def publish_matching_and_chatting(self):
        self.publish()
        descriptors = {
            "matching": {
                "flows": ["matching-member-to-chat"], "apiLevel": 30, "fixture": True,
                "requiredEnvironment": ["MAESTRO_PEER_MEMBER_ID"], "driver": None,
            },
            "chatting": {
                "flows": ["chatting-send", "chatting-receive", "chatting-list-refresh", "chatting-history"],
                "apiLevel": 35, "fixture": True,
                "requiredEnvironment": [
                    "MAESTRO_PEER_AUTH_STATE_JSON", "MAESTRO_PEER_MEMBER_ID",
                    "MAESTRO_PEER_FIXTURE_START_DATE", "MAESTRO_CHAT_ROOM_ID",
                ],
                "driver": "chatting",
            },
        }
        features = self.root / ".maestro" / "scripts" / "features"
        features.mkdir(parents=True)
        (features / "chatting.py").write_text("# 선택 검증용 자료이며 기기 코드는 실행하지 않습니다.\n")
        for name, descriptor in descriptors.items():
            (self.root / ".maestro" / "suites" / (name + ".json")).write_text(json.dumps(descriptor))
            for flow in descriptor["flows"]:
                (self.root / ".maestro" / "flows" / (flow + ".yaml")).write_text("appId: test\n---\n")

    def test_empty_installation_keeps_smoke_and_exercise_contracts(self):
        self.assertEqual(runner.selected_flows(runner.select_suites("ci")), ["smoke-tabs", "login-screen"])
        self.assertEqual(runner.selected_flows(runner.select_suites("regression")),
                         ["exercise-form-entry", "exercise-crud"])

    def test_ci_discovers_feature_and_selects_api_with_login_last(self):
        self.publish()
        suites = runner.select_suites("ci")
        self.assertEqual(runner.selected_flows(suites), ["smoke-tabs", "profile-edit", "login-screen"])
        self.assertEqual(runner.required_api(suites), 33)

    def test_only_ci_excludes_chatting_and_keeps_matching_with_login_last(self):
        self.publish_matching_and_chatting()
        chatting_flows = ["chatting-send", "chatting-receive", "chatting-list-refresh", "chatting-history"]
        cases = (
            ("ci", ["smoke-tabs", "matching-member-to-chat", "profile-edit", "login-screen"], 33),
            ("all", [
                "smoke-tabs", "exercise-form-entry", "exercise-crud", *chatting_flows,
                "matching-member-to-chat", "profile-edit", "login-screen",
            ], 35),
            ("chatting", chatting_flows, 35),
        )
        for name, flows, api_level in cases:
            with self.subTest(suite=name):
                suites = runner.select_suites(name)
                self.assertEqual(runner.selected_flows(suites), flows)
                self.assertEqual(runner.required_api(suites), api_level)

    def test_ci_keeps_matching_peer_without_chatting_inputs(self):
        self.publish_matching_and_chatting()
        environment = {"MAESTRO_PEER_MEMBER_ID": "test-peer"}
        with mock.patch.dict(runner.os.environ, environment, clear=True):
            self.assertEqual(runner.read_required_environment(runner.select_suites("ci")), environment)

    def test_all_and_chatting_still_require_chatting_inputs(self):
        self.publish_matching_and_chatting()
        with mock.patch.dict(runner.os.environ, {"MAESTRO_PEER_MEMBER_ID": "test-peer"}, clear=True):
            for name in ("all", "chatting"):
                with self.subTest(suite=name):
                    with self.assertRaisesRegex(runner.RunnerFailure, "missing-required-environment"):
                        runner.read_required_environment(runner.select_suites(name))

    def test_ci_still_requires_matching_peer(self):
        self.publish_matching_and_chatting()
        with mock.patch.dict(runner.os.environ, {}, clear=True):
            with self.assertRaisesRegex(
                runner.RunnerFailure, "missing-required-environment: MAESTRO_PEER_MEMBER_ID",
            ):
                runner.read_required_environment(runner.select_suites("ci"))

    def test_invalid_descriptor_fails_before_device_access(self):
        for change in ({"flows": []}, {"flows": ["../private"]}, {"apiLevel": True},
                       {"driver": "other"}, {"requiredEnvironment": ["PATH"]}):
            with self.subTest(change=change):
                self.publish({**self.descriptor, **change})
                with self.assertRaises(runner.RunnerFailure):
                    runner.select_suites("ci")

    def test_missing_required_peer_does_not_fall_back_to_smoke(self):
        self.publish({**self.descriptor, "requiredEnvironment": ["MAESTRO_PEER_MEMBER_ID"]})
        with mock.patch.dict(runner.os.environ, {}, clear=True):
            with self.assertRaisesRegex(runner.RunnerFailure, "MAESTRO_PEER_MEMBER_ID"):
                runner.read_required_environment(runner.select_suites("ci"))

    def test_partial_bootstrap_metadata_does_not_stop_the_app(self):
        device = object.__new__(runner.Device)
        device.require = mock.Mock()
        with self.assertRaisesRegex(runner.RunnerFailure, "invalid-feature-metadata"):
            device.bootstrap("prepare", {}, feature="profile")
        device.require.assert_not_called()

    def test_failed_restore_preserves_private_snapshot_and_stops_next_clear(self):
        device = mock.Mock()
        device.bootstrap.side_effect = [{}, runner.RunnerFailure("bootstrap-cleanup-failed")]
        args = SimpleNamespace(flow_timeout=10)
        output = self.root / "artifacts"
        credentials = {"fixtureStartDate": "2026-01-01"}
        parameters = {"MAESTRO_PROFILE_VALUE": "private-parameter"}
        with mock.patch.object(runner, "run_maestro"):
            result, safe_to_continue = runner.run_ordinary_flow(
                args, device, {"name": "profile", **self.descriptor}, "profile-edit",
                output, runner.Redactor(credentials), credentials, parameters, None,
            )
        self.assertFalse(safe_to_continue)
        self.assertEqual(device.clear.call_count, 1)
        self.assertIn("fixture-cleanup-failed", result[2])
        self.assertIn("private-state-preserved-for-recovery", result[2])
        prepare, cleanup = device.bootstrap.call_args_list
        self.assertEqual(prepare.args, ("prepare", credentials))
        self.assertEqual(cleanup.args, ("cleanup", credentials))
        self.assertEqual(prepare.kwargs, cleanup.kwargs)
        self.assertEqual(cleanup.kwargs["feature"], "profile")
        self.assertEqual(cleanup.kwargs["flow"], "profile-edit")
        self.assertEqual(cleanup.kwargs["parameters"], parameters)
        run_id = cleanup.kwargs["run_id"]
        self.assertRegex(run_id, r"\A[a-f0-9]{16}\Z")
        self.assertEqual(
            (output / "profile-edit" / "ownership.log").read_text(),
            "E2E_RUN_ID=" + run_id + "\n",
        )

    def test_hook_failure_still_restores_backend_and_clears_after_success(self):
        device = mock.Mock()
        device.bootstrap.return_value = {}
        hooks = SimpleNamespace(before_flow=mock.Mock(side_effect=ValueError("private")))
        result, safe_to_continue = runner.run_ordinary_flow(
            SimpleNamespace(flow_timeout=10), device, {"name": "profile", **self.descriptor},
            "profile-edit", self.root, runner.Redactor({}), {"fixtureStartDate": "2026-01-01"}, {}, hooks,
        )
        self.assertTrue(safe_to_continue)
        self.assertEqual([call.args[0] for call in device.bootstrap.call_args_list], ["prepare", "cleanup"])
        self.assertEqual(device.clear.call_count, 2)
        self.assertIn("feature-hook-failed", result[2])

    def test_each_maestro_failure_collects_private_map_evidence_before_cleanup(self):
        failures = (
            (runner.RunnerFailure("maestro-flow-failed"), "maestro-flow-failed"),
            (runner.RunnerFailure("maestro-assertion-failed"), "maestro-assertion-failed"),
            (runner.RunnerFailure("missing-or-invalid-maestro-junit"), "missing-or-invalid-maestro-junit"),
            (runner.RunnerFailure("tool-timeout"), "tool-timeout"),
            (runner.RunnerFailure("maestro-unavailable"), "maestro-unavailable"),
            (runner.RunInterrupted("run-interrupted"), "run-interrupted"),
            (ValueError("https://private.invalid/?token=synthetic-secret"), "runner-operation-failed"),
        )
        for index, (failure, expected) in enumerate(failures):
            with self.subTest(expected=expected):
                operations = []
                private = {}
                device = mock.Mock()
                output = self.root / str(index)
                flow = "matching-member-to-chat"
                artifact = output / flow / matching.DIAGNOSTICS_FILE

                def bootstrap(operation, credentials, **metadata):
                    operations.append(operation)
                    if operation == "prepare":
                        private["evidence"] = json.dumps({
                            "runId": metadata["run_id"],
                            "events": [
                                {"type": "map", "sequence": 1, "attempt": 1, "state": "loading"},
                                {"type": "map", "sequence": 2, "attempt": 1, "state": "failed",
                                 "category": "initialization", "sdkErrorCode": 1001},
                                {"type": "chat", "roomId": "synthetic-secret"},
                            ],
                        }).encode()
                    else:
                        self.assertEqual(json.loads(artifact.read_text())["finalState"], "failed")
                        private.clear()
                    return {}

                def read(*args):
                    operations.append("diagnostics-read")
                    return private["evidence"]

                device.bootstrap.side_effect = bootstrap
                device.require.side_effect = read
                device.clear.side_effect = lambda: operations.append("clear")
                hooks = SimpleNamespace(collect_diagnostics=matching.collect_diagnostics, after_flow=mock.Mock())
                with mock.patch.object(runner, "run_maestro", side_effect=failure):
                    result, safe = runner.run_ordinary_flow(
                        SimpleNamespace(flow_timeout=10), device, {"name": "matching", **self.descriptor},
                        flow, output, runner.Redactor({}), {"fixtureStartDate": "2026-01-01"}, {}, hooks,
                    )
                self.assertEqual(result[2], [expected])
                self.assertEqual(safe, expected != "run-interrupted")
                self.assertEqual(operations, ["clear", "prepare", "diagnostics-read", "cleanup", "clear"])
                self.assertEqual(private, {})
                self.assertNotIn("synthetic-secret", artifact.read_text())
                hooks.after_flow.assert_not_called()

    def test_diagnostics_and_cleanup_failures_preserve_the_original_ui_failure(self):
        secret = "https://private.invalid/?token=synthetic-secret"
        device = mock.Mock()
        operations = []
        flow = "matching-member-to-chat"
        artifact = self.root / flow / matching.DIAGNOSTICS_FILE

        def bootstrap(operation, credentials, **metadata):
            operations.append(operation)
            if operation == "cleanup":
                self.assertEqual(json.loads(artifact.read_text())["status"], "read_failed")
                raise RuntimeError(secret)
            return {}

        device.bootstrap.side_effect = bootstrap
        device.require.side_effect = RuntimeError(secret)
        hooks = SimpleNamespace(collect_diagnostics=matching.collect_diagnostics)
        with mock.patch.object(runner, "run_maestro", side_effect=runner.RunnerFailure("maestro-flow-failed")):
            result, safe = runner.run_ordinary_flow(
                SimpleNamespace(flow_timeout=10), device, {"name": "matching", **self.descriptor},
                flow, self.root, runner.Redactor({}), {"fixtureStartDate": "2026-01-01"}, {}, hooks,
            )
        self.assertEqual(result[2], [
            "maestro-flow-failed", "feature-diagnostics-failed", "fixture-cleanup-failed",
            "private-state-preserved-for-recovery",
        ])
        self.assertFalse(safe)
        self.assertEqual(operations, ["prepare", "cleanup"])
        self.assertEqual(device.clear.call_count, 1)
        self.assertNotIn(secret, artifact.read_text())
        self.assertNotIn(secret, str(result))

    def test_unexpected_collector_failure_does_not_prevent_successful_fixture_cleanup(self):
        secret = "https://private.invalid/?token=synthetic-secret"
        operations = []
        device = mock.Mock()
        device.bootstrap.side_effect = lambda operation, *args, **kwargs: operations.append(operation) or {}
        device.clear.side_effect = lambda: operations.append("clear")

        def collect(*args):
            operations.append("diagnostics")
            raise OSError(secret)

        hooks = SimpleNamespace(collect_diagnostics=collect)
        with mock.patch.object(runner, "run_maestro", side_effect=runner.RunnerFailure("maestro-flow-failed")):
            result, safe = runner.run_ordinary_flow(
                SimpleNamespace(flow_timeout=10), device, {"name": "profile", **self.descriptor},
                "profile-edit", self.root, runner.Redactor({}), {"fixtureStartDate": "2026-01-01"}, {}, hooks,
            )
        self.assertEqual(result[2], ["maestro-flow-failed", "feature-diagnostics-failed"])
        self.assertTrue(safe)
        self.assertEqual(operations, ["clear", "prepare", "diagnostics", "cleanup", "clear"])
        self.assertNotIn(secret, str(result))

    def test_driver_missing_result_is_a_failure_for_the_actual_flow(self):
        results = runner.validate_driver_results(["one", "two"], [("one", 1.0, [])])
        self.assertEqual(results[0], ("one", 1.0, []))
        self.assertEqual(results[1], ("two", 0, ["driver-flow-not-reported"]))

    def test_pending_recovery_refuses_even_the_first_destructive_command(self):
        device = object.__new__(runner.Device)
        device.call = mock.Mock(return_value=(4, b"", b""))
        device.require = mock.Mock()
        with self.assertRaisesRegex(runner.RunnerFailure, "pending-feature-restoration"):
            device.clear()
        device.require.assert_not_called()

    def test_invalid_driver_error_cannot_export_private_exception_text(self):
        results = runner.validate_driver_results(["one"], [("one", 1.0, ["private value"])])
        self.assertEqual(results, [("one", 0, ["driver-flow-not-reported"])])

    def test_redaction_includes_short_peer_room_identifiers(self):
        redactor = runner.Redactor({"peer": {"memberId": "xy"}, "room": "42"})
        cleaned, changed = redactor.clean("member xy room 42")
        self.assertTrue(changed)
        self.assertNotIn("xy", cleaned)
        self.assertNotIn("42", cleaned)


if __name__ == "__main__":
    unittest.main()
