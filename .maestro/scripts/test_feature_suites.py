#!/usr/bin/env python3
"""Check suite validation and restoration failure boundaries without devices."""

import contextlib
import importlib.util
import io
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

    def test_empty_installation_keeps_smoke_and_exercise_contracts(self):
        self.assertEqual(runner.selected_flows(runner.select_suites("ci")), ["smoke-tabs", "login-screen"])
        self.assertEqual(runner.selected_flows(runner.select_suites("regression")),
                         ["exercise-form-entry", "exercise-crud"])

    def test_ci_discovers_feature_and_selects_api_with_login_last(self):
        self.publish()
        suites = runner.select_suites("ci")
        self.assertEqual(runner.selected_flows(suites), ["smoke-tabs", "profile-edit", "login-screen"])
        self.assertEqual(runner.required_api(suites), 33)

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


class InstalledSuiteTests(unittest.TestCase):
    def test_all_selects_every_installed_case_and_keeps_orchestrated_helpers_out(self):
        suites = runner.select_suites("all")
        expected = [
            "smoke-tabs", "exercise-form-entry", "exercise-crud",
            "chatting-send", "chatting-receive", "chatting-list-refresh", "chatting-history",
            "feed-post-crud", "feed-like", "feed-comment", "feed-photo", "feed-form-validation",
            "matching-place-crud", "matching-place-validation", "matching-member-to-chat",
            "offline-exercise-create", "offline-exercise-cached", "offline-exercise-missing",
            "offline-timers-online", "offline-timers-mutate", "offline-timers-relaunch",
            "offline-timers-after-reconnect", "offline-timers-reconnect",
            "profile-edit", "profile-edit-validation", "profile-preferred-exercise", "profile-notification",
            "timer-simple-preset-crud", "timer-simple-run", "timer-custom-crud", "timer-custom-run",
            "timer-form-validation", "login-screen",
        ]
        self.assertEqual(runner.selected_flows(suites), expected)
        self.assertEqual(runner.required_api(suites), 34)
        installed = {path.stem for path in (runner.ROOT / ".maestro" / "flows").glob("*.yaml")}
        self.assertEqual(installed - set(expected), {"chatting-open", "chatting-ready", "chatting-away"})
        for name in ("chatting", "offline"):
            suite = next(suite for suite in suites if suite["name"] == name)
            self.assertEqual(suite["driver"], name)
            self.assertTrue(callable(runner.load_feature_module(suite).run_suite))
        self.assertEqual(
            runner.selected_flows(runner.select_suites("ci")),
            [flow for flow in expected if flow not in runner.FLOWS["regression"]],
        )
        self.assertEqual(
            runner.selected_flows(runner.select_suites("offline")),
            next(suite["flows"] for suite in suites if suite["name"] == "offline"),
        )

    def run_all(self, omit_offline_result=False):
        suites = runner.select_suites("all")
        ordinary = []
        drivers = {}
        for suite in suites:
            if suite["driver"]:
                flows = suite["flows"][:-1] if omit_offline_result and suite["name"] == "offline" else suite["flows"]
                drivers[suite["name"]] = SimpleNamespace(run_suite=mock.Mock(
                    return_value=[(flow, 1.0, []) for flow in flows],
                ))

        def run_ordinary(_args, _device, _suite, flow, *_rest):
            ordinary.append(flow)
            return (flow, 1.0, []), True

        with tempfile.TemporaryDirectory(prefix="maestro-all-dispatch-") as directory:
            root = Path(directory)
            with (
                mock.patch.object(runner, "ROOT", root),
                mock.patch.object(runner, "select_suites", return_value=suites),
                mock.patch.object(runner, "read_required_environment", return_value={}),
                mock.patch.object(runner, "read_credentials", return_value={"fixtureStartDate": "2026-01-01"}),
                mock.patch.object(runner, "Device"),
                mock.patch.object(runner, "load_feature_module", side_effect=lambda suite: drivers.get(suite["name"])),
                mock.patch.object(runner, "run_ordinary_flow", side_effect=run_ordinary),
                contextlib.redirect_stdout(io.StringIO()),
            ):
                code = runner.run_suite(SimpleNamespace(suite="all", device="primary", apk=None,
                                                       configure_emulator=False, flow_timeout=42))
            reports = list((root / "build" / "maestro").glob("*/report.xml"))
            self.assertEqual(len(reports), 1)
            cases = {case.get("name"): case for case in runner.ET.parse(reports[0]).getroot().findall("testcase")}
        return code, cases, ordinary, drivers

    def test_all_dispatches_chat_and_offline_to_drivers_not_single_device_iteration(self):
        code, cases, ordinary, drivers = self.run_all()
        self.assertEqual(code, 0)
        self.assertEqual(len(cases), 33)
        self.assertIn("exercise-crud", ordinary)
        for name, module in drivers.items():
            module.run_suite.assert_called_once()
            args, core = module.run_suite.call_args.args
            self.assertEqual(args.device, "primary")
            self.assertIs(core, runner)
            self.assertFalse(any(flow.startswith(name + "-") for flow in ordinary))

    def test_omitted_offline_phase_fails_and_blocks_later_suites_without_skip_green(self):
        code, cases, ordinary, _drivers = self.run_all(omit_offline_result=True)
        self.assertEqual(code, 1)
        self.assertEqual(len(cases), 33)
        self.assertIn("driver-flow-not-reported", cases["offline-timers-reconnect"].find("failure").get("message"))
        for flow in ("profile-edit", "timer-custom-crud", "login-screen"):
            self.assertNotIn(flow, ordinary)
            self.assertIn("not-executed", cases[flow].find("failure").get("message"))


if __name__ == "__main__":
    unittest.main()
