"""Exercise offline driver boundaries without devices, accounts, or network calls."""

import importlib.util
from pathlib import Path
import signal
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("offline_test_core", Path(__file__).parents[1] / "run-tests.py")
core = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(core)
SPEC = importlib.util.spec_from_file_location("offline_test_driver", Path(__file__).with_name("offline.py"))
offline = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(offline)
CREDENTIALS = {"authState": {"accessToken": "synthetic-private-token"}, "memberId": "dedicated-member",
               "fixtureStartDate": "2026-01-01"}


class Clock:
    def __init__(self):
        self.now = 0

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.now += seconds


class RadioDevice:
    def __init__(self):
        self.values = {"wifi_on": b"0", "mobile_data": b"1", "airplane_mode_on": b"0"}
        self.calls = []
        self.external_network = False
        self.failed_radio = None
        self.report = None

    def require(self, *args):
        self.calls.append(args)
        if args[:4] == ("shell", "settings", "get", "global"):
            return self.values[args[4]] + b"\n"
        if args == ("shell", "dumpsys", "connectivity"):
            if self.report is not None:
                return self.report
            connected = self.external_network or any(self.values[key] == b"1" for key in ("wifi_on", "mobile_data"))
            return b"Active default network: " + (b"100" if connected else b"none") + b"\nHistory: VALIDATED network 99\n"
        if args[:2] == ("shell", "svc"):
            if args[2] == self.failed_radio:
                raise core.RunnerFailure("adb-operation-failed")
            key = {"wifi": "wifi_on", "data": "mobile_data"}[args[2]]
            self.values[key] = b"1" if args[3] == "enable" else b"0"
            return b""
        raise AssertionError("Unexpected radio command")


class OfflineNetworkTests(unittest.TestCase):
    def setUp(self):
        self.device = RadioDevice()
        self.clock = Clock()
        patch = mock.patch.object(offline, "time", self.clock)
        patch.start()
        self.addCleanup(patch.stop)

    def test_disconnect_checks_current_default_and_restores_original_radios(self):
        original = dict(self.device.values)
        network = offline.NetworkState(core, self.device)
        network.disconnect()
        self.assertFalse(network.connected())
        network.restore()
        self.assertEqual(self.device.values, original)
        self.assertTrue(network.connected())
        self.assertNotIn(("shell", "svc", "wifi", "enable"), self.device.calls)

    def test_ethernet_or_other_default_prevents_false_offline_success(self):
        network = offline.NetworkState(core, self.device)
        self.device.external_network = True
        with self.assertRaisesRegex(core.RunnerFailure, "^device-network-transition-not-observed$"):
            network.disconnect()
        self.assertEqual(self.clock.now, 30)
        network.restore()
        self.assertEqual(self.device.values["mobile_data"], b"1")

    def test_restore_attempts_both_radios_after_partial_failure(self):
        network = offline.NetworkState(core, self.device)
        network.disconnect()
        self.device.failed_radio = "wifi"
        with self.assertRaisesRegex(core.RunnerFailure, "^device-network-restore-failed$"):
            network.restore()
        self.assertEqual(self.device.values["mobile_data"], b"1")
        self.assertIn(("shell", "svc", "data", "enable"), self.device.calls)

    def test_unknown_settings_and_reports_fail_before_radio_changes(self):
        for report in (b"", b"History: Active default network: 100\n",
                       b"Active default network: none\nActive default network: 100\n"):
            with self.subTest(report=report):
                self.device.report = report
                with self.assertRaisesRegex(core.RunnerFailure, "^unsupported-device-connectivity-report$"):
                    offline.NetworkState(core, self.device)
        self.device.values["mobile_data"] = b"null"
        with self.assertRaisesRegex(core.RunnerFailure, "^unsupported-device-network-settings$"):
            offline.NetworkState(core, self.device)
        self.assertFalse(any(args[:2] == ("shell", "svc") for args in self.device.calls))

    def test_initially_offline_device_is_not_silently_reconfigured(self):
        self.device.values["mobile_data"] = b"0"
        with self.assertRaisesRegex(core.RunnerFailure, "^offline-suite-requires-initial-online-network$"):
            offline.NetworkState(core, self.device)
        self.assertFalse(any(args[:2] == ("shell", "svc") for args in self.device.calls))


class OfflineDriverTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="maestro-offline-driver-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name)
        self.events = []
        self.failed_phase = None
        self.interrupted = False
        self.fail_restore = False
        self.fail_cleanup = False
        self.fail_prepare = False
        self.fail_upload = False
        self.failed_observation = None
        self.cleanup_failure = core.RunnerFailure("bootstrap-cleanup-failed")
        self.connected = True
        self.prepared_variables = {"TIMER_STEP_NAME": "fixture-step", "TIMER_INITIAL_TIME": "00:53"}
        self.device = mock.Mock()
        self.device.clear.side_effect = lambda: self.events.append("clear")
        self.device.bootstrap.side_effect = self.bootstrap
        self.device.exercise_variables.side_effect = self.variables
        self.index = 0
        self.network = mock.Mock()
        self.network.disconnect.side_effect = self.disconnect
        self.network.restore.side_effect = self.restore
        for patch in (
            mock.patch.object(core, "Device", return_value=self.device),
            mock.patch.object(core, "read_credentials", return_value=CREDENTIALS),
            mock.patch.object(core, "run_maestro", side_effect=self.phase),
            mock.patch.object(offline, "NetworkState", return_value=self.network),
            mock.patch.object(offline, "signal", SimpleNamespace(
                SIGINT=signal.SIGINT, SIGTERM=signal.SIGTERM, SIG_IGN=signal.SIG_IGN,
                signal=mock.Mock(return_value=signal.SIG_DFL),
            )),
        ):
            patch.start()
            self.addCleanup(patch.stop)

    def variables(self):
        self.index += 1
        title = "maestro-e2e-" + ("%032x" % self.index)
        return {"EXERCISE_TITLE": title, "EDITED_EXERCISE_TITLE": title + "-edited",
                "EXERCISE_START_DATE": "2026.10.15", "EXERCISE_END_DATE": "2026.10.15",
                "EXERCISE_START_HOUR": "09", "EXERCISE_END_HOUR": "10"}

    def bootstrap(self, operation, request, **metadata):
        stage = request.get("stage") or metadata.get("parameters", {}).get("stage")
        self.events.append(operation + (":" + stage if stage else ""))
        if stage is not None and stage == self.failed_observation:
            if stage == "exercise-server-absent":
                raise core.RunnerFailure("offline-suite-exercise-server-absence-mismatch")
            raise core.RunnerFailure("offline-suite-exercise-local-mismatch")
        if operation == "offline-observe":
            self.assertNotIn("authState", request)
            self.assertEqual(metadata, {})
            self.assertEqual(request["memberId"], CREDENTIALS["memberId"])
            if stage == "timers-synced" and self.fail_upload:
                raise core.RunnerFailure("offline-suite-requires-real-server-upload")
        elif operation == "feature-observe":
            self.assertEqual(request, {"memberId": CREDENTIALS["memberId"]})
            self.assertEqual(metadata["feature"], "offline")
            if stage in ("exercise-local-created", "exercise-local-created-relaunch", "exercise-local-deleted"):
                self.assertFalse(self.connected)
                return {}
            if stage == "timers-local-online":
                return {"TIMER_ORIGINAL_LOCAL_ID": "original-id", "TIMER_DELETE_LOCAL_ID": "deleted-id"}
            if stage == "timers-local-created":
                return {"TIMER_CREATE_LOCAL_ID": "created-id"}
            raise AssertionError("Unexpected local observation")
        else:
            self.assertEqual(request, CREDENTIALS)
            self.assertEqual(metadata["feature"], "offline")
            self.assertIn(metadata["flow"], tuple(scenario[0] for scenario in offline.SCENARIOS))
            self.assertRegex(metadata["run_id"], r"\A[a-f0-9]{32}\Z")
            self.assertEqual(set(metadata["parameters"]), {"startedAt", "endedAt", "initialSeconds", "updatedSeconds", "stepName"})
            if operation == "prepare" and self.fail_prepare:
                raise core.RunnerFailure("offline-suite-requires-enabled-cache-flag")
            if operation == "cleanup" and self.fail_cleanup:
                raise self.cleanup_failure
            if operation == "prepare":
                return self.prepared_variables
        return {}

    def disconnect(self):
        self.events.append("disconnect")
        self.connected = False

    def restore(self):
        self.events.append("restore")
        if self.fail_restore:
            raise core.RunnerFailure("device-network-restore-failed")
        self.connected = True

    def phase(self, _device, name, _output, _redactor, _timeout, **kwargs):
        self.events.append(name)
        self.assertNotIn("authState", kwargs["variables"])
        self.assertNotIn("memberId", kwargs["variables"])
        self.assertEqual(kwargs["variables"]["TIMER_STEP_NAME"], "fixture-step")
        if name in offline.SCENARIOS[0]:
            self.assertFalse(self.connected)
        if name in ("offline-timers-mutate", "offline-timers-mutate-finish", "offline-timers-relaunch",
                    "offline-timers-after-reconnect", "offline-timers-reconnect"):
            self.assertEqual(kwargs["variables"]["TIMER_ORIGINAL_LOCAL_ID"], "original-id")
        if name in ("offline-timers-mutate-finish", "offline-timers-relaunch",
                    "offline-timers-after-reconnect", "offline-timers-reconnect"):
            self.assertEqual(kwargs["variables"]["TIMER_CREATE_LOCAL_ID"], "created-id")
        if name == self.failed_phase:
            failure = core.RunInterrupted if self.interrupted else core.RunnerFailure
            raise failure("run-interrupted" if self.interrupted else "maestro-flow-failed")

    def run_driver(self, **inputs):
        args = SimpleNamespace(output=self.output, device="synthetic", configure_emulator=False,
                               apk=None, flow_timeout=42, **inputs)
        result = offline.run_suite(args, core)
        self.assertEqual([flow for flow, _, _ in result], list(offline.FLOWS))
        self.assertEqual(core.validate_driver_results(offline.FLOWS, result), result)
        self.assertNotIn("synthetic-private-token", str(result))
        return {flow: errors for flow, _, errors in result}

    def test_every_declared_phase_executes_with_cache_miss_isolated_and_cleanup_ordered(self):
        result = self.run_driver()
        self.assertTrue(all(not errors for errors in result.values()))
        for ordered in (
            ("offline-exercise-create", "feature-observe:exercise-local-created",
             "offline-exercise-cached", "feature-observe:exercise-local-created-relaunch",
             "offline-exercise-delete", "feature-observe:exercise-local-deleted",
             "offline-observe:exercise-server-absent"),
            ("offline-timers-online", "offline-observe:timers-online", "feature-observe:timers-local-online",
             "offline-timers-mutate", "feature-observe:timers-local-created", "offline-timers-mutate-finish",
             "offline-timers-relaunch", "offline-timers-after-reconnect",
             "offline-observe:timers-synced", "offline-timers-reconnect"),
        ):
            for earlier, later in zip(ordered, ordered[1:]):
                self.assertLess(self.events.index(earlier), self.events.index(later), (earlier, later))
        local_deleted = self.events.index("feature-observe:exercise-local-deleted")
        restored = self.events.index("restore", local_deleted)
        server_absent = self.events.index("offline-observe:exercise-server-absent")
        exercise_cleanup = self.events.index("cleanup")
        self.assertLess(local_deleted, restored)
        self.assertLess(restored, server_absent)
        self.assertLess(server_absent, exercise_cleanup)
        cache_miss = self.events.index("offline-exercise-missing")
        self.assertLess(exercise_cleanup, cache_miss)
        self.assertIn("clear", self.events[exercise_cleanup + 1:cache_miss])
        timers_local = self.events.index("feature-observe:timers-local-online")
        self.assertLess(self.events.index("disconnect", timers_local), self.events.index("offline-timers-mutate"))
        timers_relaunch = self.events.index("offline-timers-relaunch")
        self.assertLess(self.events.index("restore", timers_relaunch), self.events.index("offline-timers-after-reconnect"))
        timers_online = self.events.index("offline-timers-online")
        self.assertLess(self.events.index("offline-timers-reconnect"), self.events.index("cleanup", timers_online))
        calls = self.device.bootstrap.call_args_list
        for prepare in (call for call in calls if call.args[0] == "prepare"):
            cleanup = next(call for call in calls if call.args[0] == "cleanup" and call.kwargs == prepare.kwargs)
            self.assertEqual(prepare.args[1], cleanup.args[1])
        self.assertEqual(len({call.kwargs["run_id"] for call in calls if call.args[0] == "prepare"}), 3)

    def test_nondefault_inputs_remain_identical_for_prepare_local_observation_server_and_cleanup(self):
        result = self.run_driver(offline_initial_seconds=53, offline_updated_seconds=97, offline_step_name="set-639")
        self.assertTrue(all(not errors for errors in result.values()))
        for call in self.device.bootstrap.call_args_list:
            if call.args[0] == "offline-observe":
                parameters = call.args[1]
            else:
                parameters = call.kwargs["parameters"]
            self.assertEqual(parameters["initialSeconds"], "53")
            self.assertEqual(parameters["updatedSeconds"], "97")
            self.assertEqual(parameters["stepName"], "set-639")

    def test_interruption_restores_before_cleanup_and_reports_unexecuted_phases(self):
        self.failed_phase = "offline-exercise-cached"
        self.interrupted = True
        result = self.run_driver()
        self.assertIn("run-interrupted", result[self.failed_phase])
        self.assertEqual(self.events[-3:], ["restore", "cleanup", "clear"])
        self.assertNotIn("offline-exercise-missing", self.events)
        self.assertIn("offline-phase-not-executed", result["offline-timers-online"])

    def test_missing_upload_fails_the_observation_owner_and_does_not_claim_final_form_execution(self):
        self.fail_upload = True
        result = self.run_driver()
        self.assertIn("offline-suite-requires-real-server-upload", result["offline-timers-after-reconnect"])
        self.assertIn("offline-phase-not-executed", result["offline-timers-reconnect"])
        self.assertIn("offline-phase-not-executed", result["offline-timers-after-reconnect"])
        self.assertNotIn("offline-timers-reconnect", self.events)
        self.assertEqual(self.events[-3:], ["restore", "cleanup", "clear"])

    def test_prepare_failure_still_cleans_the_same_ownership_without_ui(self):
        self.fail_prepare = True
        result = self.run_driver()
        self.assertIn("offline-suite-requires-enabled-cache-flag", result["offline-exercise-create"])
        self.assertEqual(self.events, ["clear", "prepare", "restore", "cleanup", "clear"] * 3)

    def test_failed_restoration_preserves_private_state_and_stops_other_scenarios(self):
        self.failed_phase = "offline-exercise-cached"
        self.fail_restore = True
        result = self.run_driver()
        self.assertEqual(self.events[-1], "restore")
        self.assertNotIn("cleanup", self.events)
        self.assertEqual(self.device.clear.call_count, 1)
        self.assertIn("private-state-preserved-for-recovery", result["offline-exercise-create"])
        self.assertIn("device-network-restore-failed", result[self.failed_phase])
        self.assertNotIn("offline-exercise-missing", self.events)

    def test_failed_fixture_cleanup_does_not_clear_private_recovery_or_run_later_tests(self):
        self.fail_cleanup = True
        result = self.run_driver()
        self.assertEqual(self.events[-2:], ["restore", "cleanup"])
        self.assertEqual(self.device.clear.call_count, 1)
        self.assertIn("fixture-cleanup-failed", result["offline-exercise-create"])
        self.assertIn("private-state-preserved-for-recovery", result["offline-exercise-cached"])
        self.assertNotIn("offline-exercise-missing", self.events)

    def test_pending_recovery_clear_refusal_never_prepares_or_runs_a_later_scenario(self):
        self.device.clear.side_effect = core.RunnerFailure("pending-feature-restoration-refuses-app-data-clear")
        result = self.run_driver()
        self.assertEqual(self.device.clear.call_count, 1)
        self.device.bootstrap.assert_not_called()
        self.assertIn("pending-feature-restoration-refuses-app-data-clear", result["offline-exercise-create"])
        self.assertTrue(all(errors for errors in result.values()))

    def test_ui_failure_cleans_and_cannot_be_hidden_by_later_success(self):
        self.failed_phase = "offline-exercise-cached"
        result = self.run_driver()
        self.assertIn("maestro-flow-failed", result[self.failed_phase])
        self.assertEqual(result["offline-timers-reconnect"], [])
        index = self.events.index("offline-exercise-cached")
        self.assertEqual(self.events[index + 1:index + 4], ["restore", "cleanup", "clear"])

    def test_partial_disconnect_failure_still_restores_before_cleanup(self):
        def fail_disconnect():
            self.events.append("partial-disconnect")
            raise core.RunnerFailure("adb-operation-failed")

        self.network.disconnect.side_effect = fail_disconnect
        result = self.run_driver()
        self.assertIn("adb-operation-failed", result["offline-exercise-create"])
        index = self.events.index("partial-disconnect")
        self.assertEqual(self.events[index + 1:index + 4], ["restore", "cleanup", "clear"])
        self.assertNotIn("offline-exercise-cached", self.events)

    def test_final_clear_failure_stops_later_scenarios_and_fails_completed_phases(self):
        calls = 0

        def clear():
            nonlocal calls
            calls += 1
            self.events.append("clear")
            if calls == 2:
                raise core.RunnerFailure("app-data-clear-failed")

        self.device.clear.side_effect = clear
        result = self.run_driver()
        self.assertIn("final-app-data-clear-failed", result["offline-exercise-create"])
        self.assertIn("final-app-data-clear-failed", result["offline-exercise-cached"])
        self.assertNotIn("offline-exercise-missing", self.events)

    def test_server_absence_failure_belongs_to_delete_phase_and_never_completes_it(self):
        self.failed_observation = "exercise-server-absent"
        result = self.run_driver()
        self.assertIn("offline-suite-exercise-server-absence-mismatch", result["offline-exercise-delete"])
        self.assertIn("offline-phase-not-executed", result["offline-exercise-delete"])
        self.assertEqual(result["offline-exercise-create"], [])
        self.assertEqual(result["offline-exercise-cached"], [])
        index = self.events.index("offline-observe:exercise-server-absent")
        self.assertEqual(self.events[index + 1:index + 4], ["restore", "cleanup", "clear"])

    def test_local_observation_failure_does_not_complete_its_ui_phase(self):
        self.failed_observation = "exercise-local-created-relaunch"
        self.fail_cleanup = True
        self.cleanup_failure = core.RunnerFailure("bootstrap-cleanup-failed-offline-exercise-cleanup-unsafe")
        result = self.run_driver()
        self.assertIn("offline-suite-exercise-local-mismatch", result["offline-exercise-cached"])
        self.assertIn("offline-phase-not-executed", result["offline-exercise-cached"])
        self.assertNotIn("offline-phase-not-executed", result["offline-exercise-create"])
        self.assertNotIn("offline-exercise-delete", self.events)
        self.assertNotIn("offline-exercise-missing", self.events)
        self.assertEqual(self.events[-2:], ["restore", "cleanup"])
        self.assertEqual(self.device.clear.call_count, 1)

    def test_failed_ui_cancellation_keeps_original_error_and_owned_pending_recovery(self):
        self.failed_phase = "offline-exercise-delete"
        self.fail_cleanup = True
        self.cleanup_failure = core.RunnerFailure("bootstrap-cleanup-failed-offline-exercise-cleanup-unsafe")
        result = self.run_driver()
        self.assertIn("maestro-flow-failed", result["offline-exercise-delete"])
        self.assertIn("offline-phase-not-executed", result["offline-exercise-delete"])
        self.assertIn("fixture-cleanup-failed", result["offline-exercise-delete"])
        self.assertIn("bootstrap-cleanup-failed-offline-exercise-cleanup-unsafe", result["offline-exercise-delete"])
        self.assertNotIn("offline-observe:exercise-server-absent", self.events)
        self.assertEqual(self.events[-2:], ["restore", "cleanup"])
        self.assertEqual(self.device.clear.call_count, 1)
        self.assertNotIn("offline-exercise-missing", self.events)

    def test_cleanup_exports_only_reviewed_detail_to_xml_and_never_exception_text(self):
        cases = (
            (core.RunnerFailure("bootstrap-cleanup-failed-fixture-delete"), "bootstrap-cleanup-failed-fixture-delete"),
            (core.RunnerFailure("bootstrap-cleanup-failed-registration-http-502"), "bootstrap-cleanup-failed-registration-http-502"),
            (core.RunnerFailure("bootstrap-cleanup-failed-fixture-delete\n" + CREDENTIALS["authState"]["accessToken"]), None),
            (core.RunnerFailure("bootstrap-cleanup-failed-registration-http-502-extra"), None),
            (core.RunnerFailure("bootstrap-cleanup-failed-registration-http-999"), None),
            (core.RunnerFailure("bootstrap-cleanup-failed-" + CREDENTIALS["authState"]["accessToken"]), None),
            (ValueError("bootstrap-cleanup-failed-fixture-delete"), None),
        )
        for failure, detail in cases:
            with self.subTest(failure=type(failure).__name__, detail=detail):
                self.fail_cleanup = True
                self.cleanup_failure = failure
                result = self.run_driver()
                errors = result["offline-exercise-delete"]
                self.assertIn("fixture-cleanup-failed", errors)
                if detail is not None:
                    self.assertIn(detail, errors)
                else:
                    self.assertFalse(any(error.startswith("bootstrap-cleanup-failed-") for error in errors))
                core.write_report(self.output, [("offline-exercise-delete", 0, errors)])
                report = (self.output / "report.xml").read_text(encoding="utf-8")
                self.assertIn("fixture-cleanup-failed", report)
                if detail is not None:
                    self.assertIn(detail, report)
                else:
                    self.assertNotIn("bootstrap-cleanup-failed-", report)
                self.assertNotIn(CREDENTIALS["authState"]["accessToken"], report)

    def test_missing_or_symlinked_helper_fails_before_device_setup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for folder, names in (("flows", (*offline.FLOWS, "offline-timers-mutate-finish")), ("offline", offline.HELPERS)):
                parent = root / ".maestro" / folder
                parent.mkdir(parents=True)
                for name in names:
                    (parent / (name + ".yaml")).write_text("synthetic test fixture")
            helper = root / ".maestro" / "offline" / "create-timer.yaml"
            helper.unlink()
            with mock.patch.object(core, "ROOT", root):
                result = self.run_driver()
                self.assertTrue(all("selected-offline-flow-or-helper-missing" in errors for errors in result.values()))
                helper.symlink_to(root / ".maestro" / "flows" / (offline.FLOWS[0] + ".yaml"))
                result = self.run_driver()
                self.assertTrue(all("selected-offline-flow-or-helper-missing" in errors for errors in result.values()))
            self.device.verify_setup.assert_not_called()
            self.device.bootstrap.assert_not_called()


if __name__ == "__main__":
    unittest.main()
