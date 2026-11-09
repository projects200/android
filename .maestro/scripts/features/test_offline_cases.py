"""Behavior checks for native-media identity, private control, and failure boundaries."""

import importlib.util
import json
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("offline_cases_core", Path(__file__).parents[1] / "run-tests.py")
core = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(core)
SPEC = importlib.util.spec_from_file_location("offline_extended_test", Path(__file__).with_name("offline_cases.py"))
cases = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(cases)
RUN = "0123456789abcdef0123456789abcdef"
PRIMARY = {"memberId": "private-primary", "authState": {"accessToken": "private-primary-token"},
           "fixtureStartDate": "2026-01-01"}
PEER = {"memberId": "private-peer", "authState": {"accessToken": "private-peer-token"},
        "fixtureStartDate": "2026-01-01"}


def variables(run=RUN):
    values = {
        "E2E_RUN_ID": run, "EXERCISE_TITLE": "maestro-e2e-" + run,
        "EDITED_EXERCISE_TITLE": "maestro-e2e-" + run + "-edited",
        "EXERCISE_START_DATE": "2026.10.15", "EXERCISE_END_DATE": "2026.10.15",
        "EXERCISE_START_HOUR": "09", "EXERCISE_END_HOUR": "10",
        "EXERCISE_DESCRIPTION": "e2e-body-" + run,
        "EXERCISE_EDITED_DESCRIPTION": "e2e-local-" + run,
        "EXERCISE_REMOTE_DESCRIPTION": "e2e-remote-" + run,
    }
    for label, taken, digest in (("A", "1600000000000", "a"), ("B", "1600086400000", "b")):
        values.update({
            "PHOTO_" + label + "_NAME": "maestro-" + run + "-" + label.lower() + ".png",
            "PHOTO_" + label + "_TAKEN": taken,
            "PHOTO_" + label + "_SHA256": digest * 64,
            "PHOTO_" + label + "_RELATIVE_PATH": "Pictures/maestro-offline/" + run + "/",
            "PHOTO_" + label + "_CAPTURE_TEXT": "native captured " + label,
        })
    return values


def gallery(values):
    return "\n".join("Row: %d _id=%d, _display_name=%s, datetaken=%s, relative_path=%s" % (
        index, index + 1, values["PHOTO_" + label + "_NAME"], values["PHOTO_" + label + "_TAKEN"],
        values["PHOTO_" + label + "_RELATIVE_PATH"],
    ) for index, label in enumerate(("A", "B")))


class PickerIdentityTests(unittest.TestCase):
    def setUp(self):
        self.values = variables()
        self.rows = gallery(self.values)
        self.device = mock.Mock()
        self.device.require.side_effect = self.require

    def require(self, *args):
        if args == ("shell", "getprop", "ro.kernel.qemu"):
            return b"1"
        if args == ("shell", "getprop", "ro.build.version.sdk"):
            return b"33"
        return self.rows.encode()

    def test_native_selector_uses_validated_capture_identity_not_row_position(self):
        result = cases.picker_variables(core, self.device, self.values, RUN)
        self.assertEqual(result["PHOTO_A_SELECTOR"], r"(?s).*\Qnative captured A\E.*")
        self.assertNotEqual(result["PHOTO_A_SELECTOR"], result["PHOTO_B_SELECTOR"])
        query = self.device.require.call_args.args
        self.assertIn("_id:_display_name:datetaken:relative_path", query)

    def test_foreign_same_capture_second_is_rejected_even_when_owned_rows_exist(self):
        self.rows += "\nRow: 2 _id=3, _display_name=private.png, datetaken=1600000000123, relative_path=DCIM/"
        with self.assertRaisesRegex(core.RunnerFailure, "^offline-photo-ambiguous-media-metadata$"):
            cases.picker_variables(core, self.device, self.values, RUN)

    def test_duplicate_owned_rows_and_wrong_paths_cannot_pass_as_a_photo(self):
        original = self.rows
        for rows in (original + "\n" + original.splitlines()[0], original.replace("Pictures/maestro-offline/", "DCIM/")):
            self.rows = rows
            with self.assertRaisesRegex(core.RunnerFailure, "^offline-photo-owned-media-mismatch$"):
                cases.picker_variables(core, self.device, self.values, RUN)

    def test_original_uri_and_sensitive_metadata_are_never_returned(self):
        result = cases.picker_variables(core, self.device, self.values, RUN)
        self.assertEqual(set(result), {"PHOTO_A_SELECTOR", "PHOTO_B_SELECTOR"})
        self.assertNotIn("content://", json.dumps(result))


class ControlEnvelopeTests(unittest.TestCase):
    def setUp(self):
        self.device = object.__new__(core.Device)
        self.device.require = mock.Mock(return_value=b"")
        self.device.call = mock.Mock(return_value=(0, b'{"status":"controlled","variables":{}}', b""))
        self.metadata = {"feature": "offline", "flow": "offline-body-retry", "run_id": RUN,
                         "parameters": {"startedAt": "2026-10-15T09:00:00", "endedAt": "2026-10-15T10:00:00",
                                        "initialSeconds": "45", "updatedSeconds": "75", "stepName": "offline-step"}}

    def request(self):
        writes = [call.kwargs["payload"] for call in self.device.require.call_args_list if "payload" in call.kwargs]
        return json.loads(writes[-1])

    def test_control_preserves_activity_and_uses_root_action_without_credentials(self):
        self.device.bootstrap("offline-control", {"memberId": PRIMARY["memberId"]},
                              action="block-body", **self.metadata)
        request = self.request()
        self.assertEqual(request["action"], "block-body")
        self.assertEqual(request["operation"], "offline-control")
        self.assertNotIn("authState", request)
        self.assertNotIn("action", request["parameters"])
        self.assertFalse(any(call.args[:3] == ("shell", "am", "force-stop")
                             for call in self.device.require.call_args_list))

    def test_only_staging_accepts_both_private_authentication_fields(self):
        request = {"memberId": PRIMARY["memberId"], "authState": PEER["authState"], "targetMemberId": PEER["memberId"]}
        self.device.bootstrap("offline-control", request, action="stage-peer-login", **self.metadata)
        self.assertEqual(self.request()["authState"], PEER["authState"])
        self.assertEqual(self.request()["targetMemberId"], PEER["memberId"])
        for action, fields in (("block-body", request), ("stage-peer-login", {"memberId": PRIMARY["memberId"]}),
                               ("stage-primary-login", {"memberId": PRIMARY["memberId"], "authState": PRIMARY["authState"]})):
            self.device.require.reset_mock()
            with self.assertRaisesRegex(core.RunnerFailure, "^invalid-offline-control$"):
                self.device.bootstrap("offline-control", fields, action=action, **self.metadata)
            self.device.require.assert_not_called()

    def test_observed_status_cannot_substitute_for_controlled(self):
        self.device.call.return_value = (0, b'{"status":"observed"}', b"")
        with self.assertRaisesRegex(core.BootstrapFailure, "^bootstrap-offline-control-failed$"):
            self.device.bootstrap("offline-control", {"memberId": PRIMARY["memberId"]}, action="block-body", **self.metadata)

    def test_unknown_control_and_nested_root_fields_fail_without_device_mutation(self):
        owner = {"memberId": PRIMARY["memberId"]}
        for action in ("confirm-adoption", "force-worker", None, {}):
            with self.assertRaisesRegex(core.RunnerFailure, "^invalid-offline-control$"):
                self.device.bootstrap("offline-control", owner, action=action, **self.metadata)
        for reserved in ("action", "authState", "targetMemberId"):
            metadata = {**self.metadata, "parameters": {reserved: "private"}}
            with self.assertRaisesRegex(core.RunnerFailure, "^invalid-offline-control$"):
                self.device.bootstrap("offline-control", owner, action="block-body", **metadata)
        self.device.require.assert_not_called()

    def test_long_observation_does_not_change_default_timeout(self):
        clock = SimpleNamespace(now=0)
        clock.monotonic = lambda: clock.now
        clock.sleep = lambda seconds: setattr(clock, "now", clock.now + seconds)
        self.device.call.return_value = (3, b"", b"")
        with mock.patch.object(core, "time", clock):
            for timeout, expected in ((750, 750), (None, 300)):
                clock.now = 0
                kwargs = {} if timeout is None else {"timeout": timeout}
                with self.assertRaisesRegex(core.RunnerFailure, "^bootstrap-offline-observe-timeout$"):
                    self.device.bootstrap("offline-observe", {"memberId": PRIMARY["memberId"]}, **kwargs)
                self.assertEqual(clock.now, expected)

    def test_prerequisite_codes_remain_fixed_and_other_features_do_not_inherit_them(self):
        for reason, expected in core.OFFLINE_PREREQUISITE_FAILURES.items():
            for operation in ("prepare", "offline-control", "feature-observe"):
                failure = core.BootstrapFailure(operation, "offline", {"status": "failed", "reason": reason})
                self.assertEqual(core.failure_codes(failure), [expected])
            self.assertEqual(core.bootstrap_failure_code("prepare", "feed", reason), "bootstrap-prepare-failed")
        private = core.BootstrapFailure("offline-control", "offline", {
            "status": "failed", "reason": "offline_server_clock_untrusted\nprivate-peer-token",
        })
        self.assertEqual(str(private), "bootstrap-offline-control-failed")


class UiPreservationTests(unittest.TestCase):
    def test_field_value_and_geometry_are_compared_without_exporting_foreign_text(self):
        device = mock.Mock()
        device.require.return_value = (
            b'<?xml version="1.0"?><hierarchy><node resource-id="exercise_description_input" '
            b'text="owned-unsaved" bounds="[20,200][400,360]"/>'
            b'<node text="foreign private content" bounds="[0,0][1,1]"/></hierarchy>\nUI hierchary dumped'
        )
        result = cases.visible_snapshot(core, device, {"exercise_description_input": "owned-unsaved"})
        self.assertEqual(result, {"exercise_description_input": ("owned-unsaved", "[20,200][400,360]")})
        self.assertNotIn("foreign", str(result))
        with self.assertRaisesRegex(core.RunnerFailure, "^offline-ui-preservation-mismatch$"):
            cases.visible_snapshot(core, device, {"exercise_description_input": "replaced-by-server"})

    def test_ambiguous_missing_or_zero_area_fields_are_not_preservation_evidence(self):
        device = mock.Mock()
        for nodes in ('<node resource-id="field" text="owned" bounds="[0,0][0,0]"/>',
                      '<node resource-id="other" text="owned" bounds="[0,0][10,10]"/>',
                      '<node resource-id="field" text="owned" bounds="[0,0][10,10]"/>' * 2):
            device.require.return_value = ('<?xml version="1.0"?><hierarchy>' + nodes + '</hierarchy>').encode()
            with self.assertRaises(core.RunnerFailure):
                cases.visible_snapshot(core, device, {"field": "owned"})

    def test_worker_completion_cannot_move_the_same_visible_body(self):
        case = object.__new__(cases.Case)
        case.core = core
        case.device = mock.Mock()
        case.variables = {"RECORD_DESCRIPTION": "owned"}
        initial = ('<?xml version="1.0"?><hierarchy><node resource-id="exercise_detail_description" '
                   'text="owned" bounds="[20,200][400,360]"/></hierarchy>')
        case.device.require.return_value = initial.encode()
        before = case.snapshot()
        case.device.require.return_value = initial.replace("[20,200][400,360]", "[20,240][400,400]").encode()
        with self.assertRaisesRegex(core.RunnerFailure, "^offline-ui-preservation-mismatch$"):
            case.unchanged(before)

    def test_selected_list_date_cannot_change_even_when_the_owned_title_remains(self):
        case = object.__new__(cases.Case)
        case.core = core
        case.device = mock.Mock()
        case.variables = {"RECORD_TITLE": "owned", "EXERCISE_START_DATE": "2026.10.15"}
        initial = ('<?xml version="1.0"?><hierarchy><node resource-id="exercise_title_tv" '
                   'text="owned" bounds="[20,200][400,240]"/>'
                   '<node resource-id="exercise_list_rv" content-desc="2026-10-15 운동 기록 목록" '
                   'bounds="[0,100][450,700]"/></hierarchy>')
        case.device.require.return_value = initial.encode()
        before = case.snapshot(listing=True)
        case.device.require.return_value = initial.replace("2026-10-15", "2026-10-16").encode()
        with self.assertRaisesRegex(core.RunnerFailure, "^offline-ui-preservation-mismatch$"):
            case.unchanged(before, listing=True)


class ArtifactPrivacyTests(unittest.TestCase):
    def test_both_accounts_urls_foreign_originals_and_images_stay_private(self):
        redactor = cases.ArtifactRedactor(core, PRIMARY, PEER)
        raw = "\n".join((
            PRIMARY["memberId"], PEER["memberId"], "private-primary-token", "private-peer-token",
            "https://private.invalid/picture", "content://media/external/999",
            '{"text":"foreign original","photo":"file:///private/original.png"}',
            "offline-body-retry", "maestro-flow-failed",
        ))
        clean, sensitive = redactor.clean(raw)
        self.assertEqual(clean, "offline-body-retry\nmaestro-flow-failed")
        self.assertTrue(sensitive)  # Existing save_debug must not retain unverified screenshots.

    def test_capability_evidence_requires_observed_values_and_never_echoes_unknown_fields(self):
        self.assertEqual(cases.capability_evidence({})["server"], {"status": "unavailable"})
        unsafe = cases.capability_evidence({
            "E2E_APP_VERSION": "https://private.invalid/token", "E2E_APP_VERSION_CODE": "12",
            "E2E_SERVER_CONTRACT": "private-token",
        })
        self.assertEqual(unsafe, {"app": {"status": "unavailable"}, "server": {"status": "unavailable"}})
        observed = cases.capability_evidence({
            "E2E_APP_VERSION": "0.9.1-e2e", "E2E_APP_VERSION_CODE": "91",
            "E2E_SERVER_CONTRACT": "observed-version-edited-at",
        })
        self.assertEqual(observed["app"]["status"], "observed")
        self.assertEqual(observed["server"], {"status": "observed", "contract": "version-edited-at"})

    def test_arbitrary_runner_failure_is_not_published_as_a_fixed_code(self):
        failure = core.RunnerFailure("https://private.invalid/private-primary-token")
        self.assertEqual(cases.public_failure_codes(core, failure), ["offline-case-operation-failed"])


class DriverBoundaryTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="offline-case-tests-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name)
        self.events = []
        self.fail_stage = None
        self.fail_cleanup = False
        self.fail_prepare = False
        self.fail_restore = False
        self.device = mock.Mock()
        self.device.bootstrap.side_effect = self.bootstrap
        self.device.clear.side_effect = lambda: self.events.append("clear")
        self.network = mock.Mock()
        self.network.disconnect.side_effect = lambda: self.events.append("offline")
        self.network.restore.side_effect = self.restore
        self.device.require.side_effect = lambda *args: self.events.append(("adb", args)) or b""
        self.args = SimpleNamespace(output=self.output, device="synthetic", flow_timeout=42)
        self.patches = (
            mock.patch.object(core, "Device", return_value=self.device),
            mock.patch.object(core, "read_credentials", side_effect=lambda prefix="MAESTRO_": PEER if prefix == "MAESTRO_PEER_" else PRIMARY),
            mock.patch.object(core, "run_maestro", side_effect=self.ui),
            mock.patch.object(cases, "picker_variables", return_value={"PHOTO_A_SELECTOR": "A", "PHOTO_B_SELECTOR": "B"}),
            mock.patch.object(cases.Case, "snapshot", return_value={"owned": ("frozen", "bounds")}),
        )
        for patch in self.patches:
            patch.start()
            self.addCleanup(patch.stop)

    def restore(self):
        self.events.append("online")
        if self.fail_restore:
            raise core.RunnerFailure("device-network-restore-failed")

    def ui(self, _device, _flow, _output, redactor, _timeout, variables):
        action = variables["UI_ACTION"]
        self.events.append("ui:" + action)
        self.assertNotIn("authState", variables)
        for private in (PRIMARY["memberId"], PEER["memberId"], PRIMARY["authState"]["accessToken"], PEER["authState"]["accessToken"]):
            self.assertNotIn(private, redactor.clean(private)[0])

    def bootstrap(self, operation, request, **metadata):
        stage = request.get("stage") or metadata.get("parameters", {}).get("stage")
        event = operation + ":" + (stage or metadata.get("action", ""))
        self.events.append(event)
        if stage is not None and stage == self.fail_stage:
            raise core.RunnerFailure("offline-suite-exercise-server-mismatch")
        if operation == "prepare":
            if self.fail_prepare:
                raise core.BootstrapFailure("prepare", "offline", {"status": "failed", "reason": "refresh_failed"})
            return variables(metadata["run_id"])
        if operation == "cleanup" and self.fail_cleanup:
            raise core.BootstrapFailure("cleanup", "offline", {"status": "failed", "reason": "offline_exercise_cleanup_unsafe"})
        return {}

    def run_driver(self, *flows):
        with mock.patch.object(cases, "FLOWS", flows):
            return cases.run_suite(self.args, core, lambda *_: self.network, lambda _: variables(),
                                   lambda _: SimpleNamespace(parameters=lambda: {
                                       "initialSeconds": "45", "updatedSeconds": "75", "stepName": "offline-step",
                                   }))

    def index(self, name):
        return self.events.index(name)

    def test_retry_waits_real_terminal_observation_then_only_button_can_resume(self):
        result = self.run_driver("offline-body-retry")
        self.assertEqual(result[0][2], [])
        exhausted = self.index("offline-observe:exercise-case-retry-exhausted")
        retry = self.index("ui:retry")
        self.assertLess(exhausted, retry)
        between = self.events[exhausted:retry]
        self.assertNotIn("ui:relaunch", between)
        self.assertFalse(any(isinstance(event, tuple) for event in between))
        self.assertIn("ui:assert-no-retry", between)
        terminal = next(call for call in self.device.bootstrap.call_args_list
                        if call.args[1].get("stage") == "exercise-case-retry-exhausted")
        self.assertEqual(terminal.kwargs["timeout"], 750)
        other_observations = [call for call in self.device.bootstrap.call_args_list
                              if call.args[0] == "offline-observe" and call is not terminal]
        self.assertTrue(all(call.kwargs.get("timeout", 300) == 300 for call in other_observations))
        self.assertLess(retry, self.index("offline-observe:exercise-case-complete"))
        self.assertLess(self.index("ui:delete"), self.index("offline-observe:exercise-case-ui-deleted-synced"))

    def test_terminal_observation_failure_never_taps_retry_or_marks_success(self):
        self.fail_stage = "exercise-case-retry-exhausted"
        self.fail_cleanup = True
        result = self.run_driver("offline-body-retry", "offline-session-retain")
        self.assertNotIn("ui:retry", self.events)
        self.assertIn("offline-suite-exercise-server-mismatch", result[0][2])
        self.assertIn("private-state-preserved-for-recovery", result[0][2])
        self.assertIn("offline-phase-not-executed", result[1][2])
        self.assertEqual(self.events.count("clear"), 1)
        self.assertLess(self.index("offline-control:unblock-transport"), self.index("cleanup:"))

    def test_prepare_failure_is_not_success_and_never_runs_ui(self):
        self.fail_prepare = True
        result = self.run_driver("offline-photo-create")
        self.assertIn("bootstrap-prepare-failed-refresh", result[0][2])
        self.assertIn("offline-phase-not-executed", result[0][2])
        self.assertFalse(any(isinstance(event, str) and event.startswith("ui:") for event in self.events))
        status = json.loads((self.output / "offline/offline-photo-create/result.json").read_text())
        self.assertEqual(status["status"], "preparation-failed")
        self.assertFalse(status["uiCompleted"])

    def test_cleanup_failure_fails_completed_ui_and_stops_later_cases(self):
        self.fail_cleanup = True
        result = self.run_driver("offline-remote-delete", "offline-body-retry")
        self.assertIn("fixture-cleanup-failed", result[0][2])
        self.assertIn("private-state-preserved-for-recovery", result[0][2])
        self.assertIn("offline-phase-not-executed", result[1][2])
        self.assertEqual(self.events.count("clear"), 1)
        status = json.loads((self.output / "offline/offline-remote-delete/result.json").read_text())
        self.assertEqual(status["status"], "cleanup-failed")
        self.assertTrue(status["uiCompleted"])

    def test_remote_discard_requires_cancel_observation_before_real_approval(self):
        result = self.run_driver("offline-remote-delete")
        self.assertEqual(result[0][2], [])
        self.assertLess(self.index("ui:remote-cancel"), self.index("feature-observe:exercise-case-discard-cancelled"))
        self.assertLess(self.index("feature-observe:exercise-case-discard-cancelled"), self.index("ui:remote-confirm"))
        self.assertLess(self.index("ui:remote-confirm"), self.index("feature-observe:exercise-case-discarded"))

    def test_adoption_is_never_approved_by_a_fixture_and_cleanup_restores_original_owner(self):
        result = self.run_driver("offline-session-adoption")
        self.assertEqual(result[0][2], [])
        self.assertLess(self.index("ui:adoption-cancel"), self.index("feature-observe:session-peer-cancelled"))
        self.assertLess(self.index("ui:adoption-confirm"), self.index("feature-observe:session-peer-approved"))
        self.assertLess(self.index("feature-observe:session-peer-logged-out"), self.index("ui:open-server-record"))
        self.assertLess(self.index("offline-observe:exercise-case-ui-deleted-synced"),
                        self.index("feature-observe:session-primary-restored-clean"))
        for call in self.device.bootstrap.call_args_list:
            if call.args[0] == "offline-control":
                request, action = call.args[1], call.kwargs["action"]
                self.assertEqual(request["memberId"], PRIMARY["memberId"])
                self.assertEqual("authState" in request, action.startswith("stage-"))
                self.assertNotIn("confirm", action)
        public = (self.output / "offline/offline-session-adoption/result.json").read_text()
        for private in (PRIMARY["memberId"], PEER["memberId"], "private-primary-token", "private-peer-token"):
            self.assertNotIn(private, public)

    def test_conflict_order_is_independently_different_for_local_and_server_newer(self):
        for flow, remote_first in (("offline-conflict-local", True), ("offline-conflict-server", False)):
            self.events.clear()
            self.assertEqual(self.run_driver(flow)[0][2], [])
            self.assertEqual(self.index("offline-control:remote-update") < self.index("ui:edit"), remote_first)
            self.assertLess(self.index("ui:open-draft"), self.index("offline-observe:exercise-case-complete"))
            self.assertLess(self.index("offline-observe:exercise-case-complete"), self.index("ui:assert-draft"))

    def test_network_restoration_failure_preserves_private_state_and_never_runs_cleanup(self):
        self.fail_restore = True
        result = self.run_driver("offline-body-retry", "offline-photo-create")
        self.assertIn("device-network-restore-failed", result[0][2])
        self.assertIn("private-state-preserved-for-recovery", result[0][2])
        self.assertNotIn("cleanup:", self.events)
        self.assertEqual(self.events.count("clear"), 1)
        self.assertIn("offline-phase-not-executed", result[1][2])

    def test_partial_fault_arm_is_restored_even_when_control_fails(self):
        original = self.device.bootstrap.side_effect

        def fail_arm(operation, request, **metadata):
            result = original(operation, request, **metadata)
            if metadata.get("action") == "block-body":
                raise core.RunnerFailure("adb-operation-failed")
            return result

        self.device.bootstrap.side_effect = fail_arm
        self.fail_cleanup = True
        result = self.run_driver("offline-body-retry")
        self.assertIn("adb-operation-failed", result[0][2])
        self.assertLess(self.index("offline-control:block-body"), self.index("offline-control:unblock-transport"))
        self.assertLess(self.index("offline-control:unblock-transport"), self.index("cleanup:"))
        self.assertEqual(self.events.count("clear"), 1)

    def test_photo_interruption_relaunches_offline_and_restores_before_final_ack(self):
        self.assertEqual(self.run_driver("offline-photo-create")[0][2], [])
        pending = [index for index, event in enumerate(self.events)
                   if event == "offline-observe:exercise-case-photos-pending"]
        self.assertEqual(len(pending), 2)
        for index in pending:
            relaunch = self.events.index("ui:relaunch", index)
            self.assertIn("offline", self.events[index:relaunch])
        completed = self.index("offline-observe:exercise-case-complete")
        unblock = max(index for index, event in enumerate(self.events[:completed])
                      if event == "offline-control:unblock-transport")
        self.assertIn("online", self.events[unblock:completed])
        self.assertLess(completed, self.index("feature-observe:exercise-case-points-stable"))

    def test_server_contract_failure_after_ui_creation_is_preparation_failure_not_success(self):
        original = self.device.bootstrap.side_effect

        def no_contract(operation, request, **metadata):
            result = original(operation, request, **metadata)
            if metadata.get("action") == "remote-update":
                raise core.BootstrapFailure(operation, "offline", {
                    "status": "failed", "reason": "offline_server_clock_contract_missing",
                })
            return result

        self.device.bootstrap.side_effect = no_contract
        self.fail_cleanup = True
        result = self.run_driver("offline-conflict-local")
        self.assertIn("offline-prerequisite-server-clock-contract", result[0][2])
        status = json.loads((self.output / "offline/offline-conflict-local/result.json").read_text())
        self.assertEqual(status["failureStage"], "preparation")
        self.assertEqual(status["cleanupStatus"], "failed")
        self.assertNotIn("ui:delete", self.events)


class SuiteCompositionTests(unittest.TestCase):
    def test_previous_private_recovery_prevents_loading_or_running_extended_driver(self):
        spec = importlib.util.spec_from_file_location("offline_composition", Path(__file__).with_name("offline.py"))
        offline = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(offline)
        legacy = [(flow, 0, ["private-state-preserved-for-recovery"]) for flow in offline.LEGACY_FLOWS]
        with mock.patch.object(offline, "run_legacy_suite", return_value=legacy), mock.patch.object(
            offline.importlib.util, "spec_from_file_location",
        ) as load:
            result = offline.run_suite(SimpleNamespace(), core)
        load.assert_not_called()
        self.assertEqual(result[:len(legacy)], legacy)
        self.assertTrue(all("offline-prior-recovery-required" in errors
                            for _, _, errors in result[len(legacy):]))


if __name__ == "__main__":
    unittest.main()
