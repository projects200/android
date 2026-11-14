"""Behavior checks for native-media identity, private control, and failure boundaries."""

import contextlib
import copy
import importlib.util
import io
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


def app_state_responses():
    # Synthetic data shaped like public Android dumps, never a device capture.
    return (
        (0, b"NAME\ncom.project200.undabang\nprivate-process\n", b"private-process-error"),
        (0, b"  topResumedActivity=ActivityRecord{abc u0 com.project200.undabang/.main.MainActivity} t12}\n",
         b"private-activity-error"),
        (0, b'private-ui-tool-preamble\r\n<?xml version="1.0"?>\r\n<hierarchy rotation="0">\r\n'
         b'<node package="com.project200.undabang" resource-id="exercise_detail_menu" '
         b'text="private-ui-text" content-desc="private-description"/>\r\n'
         b'<node package="private.foreign" resource-id="private-id" text="private-account-token"/>\r\n'
         b'</hierarchy>\r\nUI hierchary dumped to: /dev/tty\r\n', b"private-ui-error"),
    )


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
    def test_crlf_capture_preserves_owned_values_and_geometry_without_exporting_foreign_text(self):
        device = mock.Mock()
        raw = (
            b'private-tool-preamble\r\n<?xml version="1.0"?>\r\n<hierarchy>\r\n'
            b'<node resource-id="com.project200.undabang:id/exercise_description_input" '
            b'text="owned-unsaved" bounds="[20,200][400,360]"/>\r\n'
            b'<node text="foreign private content" bounds="[0,0][1,1]"/>\r\n'
            b'</hierarchy>\r\nUI hierchary dumped to: /dev/tty\r\n'
        )

        device.call.return_value = (0, raw, b"private-stderr-token")

        stdout, stderr = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            result = cases.visible_snapshot(core, device, {"exercise_description_input": "owned-unsaved"})
            self.assertEqual(result, {"exercise_description_input": ("owned-unsaved", "[20,200][400,360]")})
            self.assertNotIn("foreign", str(result))
            self.assertNotIn("private", str(result))
            with self.assertRaisesRegex(core.RunnerFailure, "^offline-ui-preservation-mismatch$"):
                cases.visible_snapshot(core, device, {"exercise_description_input": "replaced-by-server"})
        self.assertEqual(stdout.getvalue() + stderr.getvalue(), "")

    def test_ambiguous_missing_or_zero_area_fields_are_not_preservation_evidence(self):
        device = mock.Mock()
        for nodes in ('<node resource-id="field" text="owned" bounds="[0,0][0,0]"/>',
                      '<node resource-id="field" text="owned" bounds="[10,0][0,10]"/>',
                      '<node resource-id="field" text="owned" bounds="[0,10][10,0]"/>',
                      '<node resource-id="other" text="owned" bounds="[0,0][10,10]"/>',
                      '<node resource-id="field" text="owned" bounds="[0,0][10,10]"/>' * 2):
            device.call.return_value = (
                0, ('<?xml version="1.0"?><hierarchy>' + nodes + '</hierarchy>').encode(), b"",
            )
            with self.subTest(nodes=nodes), self.assertRaisesRegex(
                    core.RunnerFailure, "^offline-ui-preservation-mismatch$"):
                cases.visible_snapshot(core, device, {"field": "owned"})

    def test_capture_and_parser_failures_publish_only_their_distinct_fixed_codes(self):
        valid = (b'<?xml version="1.0"?><hierarchy><node resource-id="field" '
                 b'text="owned" bounds="[0,0][10,10]"/></hierarchy>')
        device = mock.Mock()
        redactor = cases.ArtifactRedactor(core, PRIMARY, PEER)
        raw_stderr = b"private-stderr-token https://private.invalid/account"
        for code, raw, failure in (
            (1, b"private-stdout-token\r\n" + valid, "capture-failed"),
            (0, b"private-stdout-token", "hierarchy-missing"),
            (0, b'<?xml version="1.0"?><hierarchy>private-truncated', "hierarchy-missing"),
            (0, b'<hierarchy>private-missing-declaration</hierarchy>', "hierarchy-missing"),
            (0, b"private-oversized " + b"x" * (1024 * 1024), "hierarchy-too-large"),
            (0, b'<?xml version="1.0"?><hierarchy><node text="private-xml-token"></hierarchy>', "xml-invalid"),
            (0, valid.replace(b"[0,0][10,10]", b"private-bounds-token"), "bounds-invalid"),
            (0, valid.replace(b' bounds="[0,0][10,10]"', b""), "bounds-invalid"),
            (0, valid.replace(b"[0,0][10,10]", b"[-1,0][10,10]"), "bounds-invalid"),
        ):
            expected = "offline-ui-preservation-" + failure
            device.call.return_value = code, raw, raw_stderr
            stdout, stderr = io.StringIO(), io.StringIO()
            with self.subTest(failure=failure), contextlib.redirect_stdout(stdout), \
                    contextlib.redirect_stderr(stderr):
                with self.assertRaisesRegex(core.RunnerFailure, "^" + expected + "$") as caught:
                    cases.visible_snapshot(core, device, {"field": "owned"})
                public = cases.public_failure_codes(core, caught.exception)
                clean, sensitive = redactor.clean(
                    str(caught.exception) + "\n" + raw.decode("utf-8") + "\n" + raw_stderr.decode("utf-8"),
                )
            self.assertEqual(public, [expected])
            self.assertEqual(clean, expected)
            self.assertTrue(sensitive)
            self.assertNotIn("private", json.dumps(public) + clean)
            self.assertEqual(stdout.getvalue() + stderr.getvalue(), "")

    def test_one_mib_capture_is_allowed_but_one_more_byte_is_rejected(self):
        raw = (b'<?xml version="1.0"?><hierarchy><node resource-id="field" '
               b'text="owned" bounds="[0,0][10,10]"/></hierarchy>')
        raw = b" " * (1024 * 1024 - len(raw)) + raw
        device = mock.Mock()
        device.call.return_value = 0, raw, b""
        self.assertEqual(cases.visible_snapshot(core, device, {"field": "owned"}), {
            "field": ("owned", "[0,0][10,10]"),
        })
        device.call.return_value = 0, raw + b"\n", b""
        with self.assertRaisesRegex(core.RunnerFailure, "^offline-ui-preservation-hierarchy-too-large$"):
            cases.visible_snapshot(core, device, {"field": "owned"})

    def test_capture_tool_errors_and_interruptions_propagate_without_reclassification(self):
        device = mock.Mock()
        for failure in (core.RunnerFailure("tool-timeout"), core.RunnerFailure("tool-unavailable"),
                        core.RunInterrupted("run-interrupted"), KeyboardInterrupt()):
            device.call.side_effect = failure
            with self.subTest(kind=type(failure).__name__), self.assertRaises(type(failure)) as caught:
                cases.visible_snapshot(core, device, {"field": "owned"})
            self.assertIs(caught.exception, failure)

    def test_worker_completion_cannot_move_the_same_visible_body(self):
        case = object.__new__(cases.Case)
        case.core = core
        case.device = mock.Mock()
        case.variables = {"RECORD_DESCRIPTION": "owned"}
        initial = ('<?xml version="1.0"?><hierarchy><node resource-id="exercise_detail_description" '
                   'text="owned" bounds="[20,200][400,360]"/></hierarchy>')
        case.device.call.return_value = 0, initial.encode(), b""
        before = case.snapshot()
        case.device.call.return_value = 0, initial.replace("[20,200][400,360]", "[20,240][400,400]").encode(), b""
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
        case.device.call.return_value = 0, initial.encode(), b""
        before = case.snapshot(listing=True)
        case.device.call.return_value = 0, initial.replace("2026-10-15", "2026-10-16").encode(), b""
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
        redactor = cases.ArtifactRedactor(core, PRIMARY, PEER)
        for message in (
            "https://private.invalid/private-primary-token",
            'private-stdout <hierarchy text="private-account"/> private-stderr',
            "offline-ui-preservation-capture-failed private-token",
            "offline-ui-preservation-unavailable",
        ):
            failure = core.RunnerFailure(message)
            self.assertEqual(cases.public_failure_codes(core, failure), ["offline-case-operation-failed"])
            clean, sensitive = redactor.clean(message)
            self.assertEqual(clean, "[offline private UI details omitted]")
            self.assertTrue(sensitive)


class CommandSummaryTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="offline-command-tests-")
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.raw = self.root / "raw"
        self.raw.mkdir()
        self.destination = self.root / "public"
        self.redactor = cases.ArtifactRedactor(core, PRIMARY, PEER)
        self.redactor.set_command_context("offline-photo-create", "create", 1)

    def record(self, command_type, sequence, depth, status="COMPLETED", source=None):
        return {
            "command": {command_type: {
                "sourceDescription": source, "selector": {"textRegex": "private-selector"},
                "text": "private-input", "env": {"TOKEN": "private-environment"},
                "primary": PRIMARY, "peer": PEER, "runId": RUN,
                "url": "https://private.invalid/photo", "uri": "content://private/media/1",
                "file": "file:///private/photo.png", "hierarchy": "private-hierarchy",
            }},
            "metadata": {
                "sequenceNumber": sequence, "depth": depth, "status": status,
                "timestamp": "private-timestamp", "duration": 123,
                "error": "private-error-stack", "evaluatedCommand": {
                    "command": "private-evaluated", "status": "FAILED",
                },
            },
        }

    def records(self):
        records = [
            self.record("runFlowCommand", 0, 0, "FAILED", "../offline/dispatch.yaml"),
            self.record("applyConfigurationCommand", 1, 1),
            self.record("runFlowCommand", 2, 1, "FAILED", "create.yaml"),
            self.record("defineVariablesCommand", 3, 1),
            self.record("applyConfigurationCommand", 4, 2),
            self.record("runFlowCommand", 5, 2, source="../subflows/launch-logged-in.yaml"),
            self.record("launchAppCommand", 6, 3),
            self.record("runFlowCommand", 7, 2, source="../subflows/set-exercise-time.yaml"),
            self.record("tapOnElement", 8, 3),
            self.record("runFlowCommand", 9, 2, "FAILED", "pick-photo.yaml"),
            self.record("defineVariablesCommand", 10, 2),
            self.record("tapOnElement", 11, 3),
            self.record("assertConditionCommand", 12, 3, "FAILED"),
            self.record("runFlowCommand", 13, 2, "SKIPPED", "assert-detail.yaml"),
            self.record("inputTextCommand", 14, 2, "PENDING"),
        ]
        records[2]["command"]["runFlowCommand"]["commands"] = [
            self.record("assertConditionCommand", 999, 3, "FAILED"),
        ]
        return records

    def capture(self, records=None):
        if records is not None:
            (self.raw / "commands.json").write_text(json.dumps(records), encoding="utf-8")
        core.save_debug(self.raw, self.destination, self.redactor, b"private-console")
        return json.loads((self.destination / "command-summary.json").read_text())

    def test_runtime_leaf_and_parent_failures_survive_real_scrub_without_private_artifacts(self):
        (self.raw / "private-photo.png").write_bytes(b"\x89PNG\r\n\x1a\nprivate-image")
        summary = self.capture(self.records())
        self.assertEqual({key: summary[key] for key in ("flow", "action", "invocation", "status")}, {
            "flow": "offline-photo-create", "action": "create", "invocation": 1, "status": "captured",
        })
        self.assertEqual([(item["sequenceNumber"], item["failure_role"]) for item in summary["failures"]],
                         [(0, "propagated"), (2, "propagated"), (9, "propagated"), (12, "leaf")])
        leaf = summary["failures"][-1]
        self.assertEqual({key: leaf[key] for key in ("command_type", "sequenceNumber", "depth", "status")},
                         {"command_type": "assertConditionCommand", "sequenceNumber": 12, "depth": 3, "status": "FAILED"})
        self.assertEqual([(item["sequenceNumber"], item["helper"]) for item in leaf["ancestors"]], [
            (0, "offline/dispatch.yaml"), (2, "offline/create.yaml"), (9, "offline/pick-photo.yaml"),
        ])
        self.assertEqual(summary["last_executed"]["sequenceNumber"], 12)
        self.assertEqual(summary["last_completed"]["sequenceNumber"], 11)
        self.assertEqual(summary["last_completed"]["command_type"], "tapOnElement")
        public = "\n".join(path.read_text() for path in self.destination.iterdir())
        for secret in (
            "private-", "selector", "evaluatedCommand", "timestamp", "999", "assert-detail.yaml",
            RUN, "https://", "content://", "file://", "hierarchy",
        ):
            self.assertNotIn(secret, public)
        self.assertFalse(list(self.destination.glob("*.png")))
        self.assertEqual((self.destination / "debug-001.json").read_text(), "[offline private UI details omitted]")
        self.assertTrue(self.redactor.clean("arbitrary")[1])

    def test_unrelated_json_is_not_opened_as_commands_and_all_artifacts_stay_private(self):
        unrelated = {
            "manifest.json": json.dumps({
                "$schema": "https://private.invalid/schema",
                "entries": [{"path": "private-manifest-path"}],
            }),
            "ai-(private-flow).json": json.dumps({"analysis": "private-ai-response"}),
            "home/.maestro/config.json": json.dumps({"token": "private-home-token"}),
            "malformed.json": '{"private-malformed":',
            "commands-copy.json": '{"private-copy":',
            "commands-(private-flow).json.bak": '{"private-backup":',
            "commands.JSON": '{"private-uppercase":',
            "commands-().json": '{"private-empty-flow":',
        }
        for name, content in unrelated.items():
            path = self.raw / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content, encoding="utf-8")
        (self.raw / "private-photo.png").write_bytes(b"\x89PNG\r\n\x1a\nprivate-image")
        opened = []
        original_open = Path.open

        def track_report_reads(path, *args, **kwargs):
            if args == ("rb",):
                opened.append(path.relative_to(self.raw).as_posix())
            return original_open(path, *args, **kwargs)

        with mock.patch.object(Path, "open", new=track_report_reads), \
                mock.patch.object(cases, "COMMAND_MAX_REPORTS", 1):
            summary = self.capture(self.records())
        self.assertEqual(summary["status"], "captured")
        self.assertEqual(summary["failures"][-1]["sequenceNumber"], 12)
        self.assertEqual(opened, ["commands.json"])
        artifacts = list(self.destination.iterdir())
        scrubbed = [path for path in artifacts if path.name != "command-summary.json"]
        self.assertTrue(scrubbed)
        self.assertTrue(all(path.read_text() == "[offline private UI details omitted]" for path in scrubbed))
        public = "\n".join(path.read_text() for path in artifacts)
        for secret in ("private-", RUN, "https://", "content://", "file://"):
            self.assertNotIn(secret, public)
        self.assertFalse(list(self.destination.glob("*.png")))

    def test_malformed_recognized_report_is_not_ignored_beside_valid_commands(self):
        (self.raw / "commands-(private-flow).json").write_text('{"private-malformed":', encoding="utf-8")
        summary = self.capture(self.records())
        self.assertEqual(summary["status"], "unavailable")
        self.assertNotIn("private", json.dumps(summary))

    def test_known_helpers_are_exact_matches_and_private_sources_become_unknown(self):
        for source, expected in (
            ("assert-detail.yaml", "offline/assert-detail.yaml"),
            ("../subflows/set-exercise-time.yaml", "subflows/set-exercise-time.yaml"),
            ("../subflows/launch-logged-in.yaml", "subflows/launch-logged-in.yaml"),
            ("/private/account/pick-photo.yaml", "unknown"),
            ("https://private.invalid/create.yaml", "unknown"),
            ("content://private/create.yaml", "unknown"),
            ("file:///private/create.yaml", "unknown"),
            ("private-regex-safe.yaml", "unknown"), (None, "unknown"),
            ({"private-source": "create.yaml"}, "unknown"),
        ):
            with self.subTest(source=source):
                summary = self.capture([self.record("runFlowCommand", 0, 0, "FAILED", source)])
                self.assertEqual(summary["failures"][0]["helper"], expected)
                self.assertEqual(summary["failures"][0]["failure_role"], "leaf")
                self.assertNotIn("private", json.dumps(summary))

    def test_skipped_and_future_records_are_not_execution_or_failure_evidence(self):
        summary = self.capture([
            self.record("tapOnElement", 0, 0),
            self.record("assertConditionCommand", 1, 0, "SKIPPED"),
            self.record("inputTextCommand", 2, 0, "PENDING"),
        ])
        self.assertEqual(summary["failures"], [])
        self.assertEqual(summary["last_executed"]["sequenceNumber"], 0)
        self.assertEqual(summary["last_completed"]["sequenceNumber"], 0)
        summary = self.capture([self.record("tapOnElement", 0, 0, "RUNNING")])
        self.assertEqual(summary["last_executed"]["status"], "RUNNING")
        self.assertIsNone(summary["last_completed"])
        self.assertEqual(self.capture([self.record("tapOnElement", 0, 0, "PENDING")])["status"], "unavailable")

    def test_warned_commands_are_executed_but_not_failed_or_completed(self):
        summary = self.capture([
            self.record("runFlowCommand", 0, 0, "WARNED", "create.yaml"),
            self.record("tapOnElement", 1, 1),
            self.record("assertConditionCommand", 2, 1, "WARNED"),
            self.record("assertConditionCommand", 3, 1, "SKIPPED"),
            self.record("inputTextCommand", 4, 1, "PENDING"),
        ])
        self.assertEqual(summary["status"], "captured")
        self.assertEqual(summary["failures"], [])
        self.assertEqual(summary["last_executed"]["sequenceNumber"], 2)
        self.assertEqual(summary["last_executed"]["status"], "WARNED")
        self.assertEqual(summary["last_executed"]["ancestors"], [{
            "command_type": "runFlowCommand", "sequenceNumber": 0,
            "depth": 0, "status": "WARNED", "helper": "offline/create.yaml",
        }])
        self.assertEqual(summary["last_completed"]["sequenceNumber"], 1)
        self.assertEqual(summary["last_completed"]["status"], "COMPLETED")
        summary = self.capture([self.record("assertConditionCommand", 0, 0, "WARNED")])
        self.assertEqual(summary["status"], "captured")
        self.assertEqual(summary["failures"], [])
        self.assertEqual(summary["last_executed"]["status"], "WARNED")
        self.assertIsNone(summary["last_completed"])

    def test_missing_malformed_and_ambiguous_records_are_unavailable(self):
        valid = self.record("assertConditionCommand", 0, 0, "FAILED")
        invalid = [None, {}, {"nested": [valid]}, [None], [{"command": valid["command"]}]]
        for field, values in (
            ("sequenceNumber", (None, True, -1, 1.5, "0", 100001)),
            ("depth", (None, False, -1, 1.5, "0", 33)),
            ("status", (None, {}, "private-status")),
        ):
            for value in values:
                record = copy.deepcopy(valid)
                record["metadata"][field] = value
                invalid.append([record])
        invalid.extend((
            [self.record("privateCommand", 0, 0, "FAILED")],
            [{"command": {"tapOnElement": {}, "inputTextCommand": {}}, "metadata": valid["metadata"]}],
            [self.record("tapOnElement", 0, 1, "FAILED")],
            [self.record("runFlowCommand", 0, 0, "SKIPPED"), self.record("tapOnElement", 1, 1, "FAILED")],
        ))
        for records in invalid:
            with self.subTest(records=records):
                summary = self.capture(records)
                self.assertEqual(summary["status"], "unavailable")
                self.assertNotIn("failures", summary)
                self.assertNotIn("private", json.dumps(summary))

    def test_duplicate_copies_are_deterministic_but_conflicting_identities_are_unavailable(self):
        records = self.records()
        expected = self.capture(records)
        duplicate = self.raw / "commands-(my_flow).json"
        duplicate.write_text(json.dumps(records), encoding="utf-8")
        self.assertEqual(self.capture(records + [records[-1]]), expected)
        duplicate.write_text(json.dumps(list(reversed(records))), encoding="utf-8")
        self.assertEqual(self.capture(records), expected)
        for mutate in ("status", "selector", "numeric-type", "subset"):
            with self.subTest(mutate=mutate):
                conflict = copy.deepcopy(records)
                if mutate == "status":
                    conflict[12]["metadata"]["status"] = "COMPLETED"
                elif mutate == "selector":
                    conflict[12]["command"]["assertConditionCommand"]["text"] = "other-private-input"
                elif mutate == "numeric-type":
                    conflict[12]["metadata"]["duration"] = 123.0
                else:
                    conflict.pop()
                duplicate.write_text(json.dumps(conflict), encoding="utf-8")
                self.assertEqual(self.capture(records)["status"], "unavailable")
        duplicate.unlink()
        conflict = copy.deepcopy(records[12])
        conflict["metadata"]["depth"] = 2
        self.assertEqual(self.capture(records + [conflict])["status"], "unavailable")

    def test_invalid_json_size_count_and_nesting_are_unavailable(self):
        nested = "private-nested"
        for _ in range(65):
            nested = [nested]
        deeply_nested = self.record("assertConditionCommand", 0, 0, "FAILED")
        deeply_nested["command"]["assertConditionCommand"]["selector"] = nested
        valid_json = json.dumps(self.records())
        for payload in (
            "{", "null",
            valid_json.replace('"status": "FAILED"', '"status": "FAILED", "status": "COMPLETED"', 1),
            valid_json.replace('"duration": 123', '"duration": NaN', 1),
            json.dumps([deeply_nested]),
        ):
            (self.raw / "commands.json").write_text(payload, encoding="utf-8")
            self.assertEqual(self.capture()["status"], "unavailable")
        for constant, limit in (("COMMAND_MAX_BYTES", 16), ("COMMAND_MAX_RECORDS", 2),
                                ("COMMAND_MAX_PATHS", 0), ("COMMAND_MAX_REPORTS", 0),
                                ("COMMAND_MAX_TOTAL_BYTES", 16), ("COMMAND_MAX_FAILURES", 1)):
            with self.subTest(constant=constant), mock.patch.object(cases, constant, limit):
                self.assertEqual(self.capture(self.records())["status"], "unavailable")

    def test_large_nested_dispatch_definitions_and_copies_remain_bounded_and_private(self):
        records = self.records()
        records[0]["command"]["runFlowCommand"]["commands"] = [{
            "assertConditionCommand": {"selector": {"textRegex": "private-planned-" + "x" * (4 * 1024 * 1024)}},
        }]
        content = json.dumps(records)
        (self.raw / "commands-(dispatch).json").write_text(content, encoding="utf-8")
        with mock.patch.object(cases.time, "monotonic", return_value=100.0):
            summary = self.capture(records)
        self.assertEqual(summary["status"], "captured")
        self.assertEqual(summary["failures"][-1]["sequenceNumber"], 12)
        self.assertNotIn("private", json.dumps(summary))
        self.assertEqual((self.destination / "debug-001.json").read_text(), "[offline private UI details omitted]")
        self.assertEqual((self.destination / "debug-002.json").read_text(), "[offline private UI details omitted]")

    def test_missing_or_unreadable_reports_are_unavailable_without_changing_scrub(self):
        self.assertEqual(self.capture()["status"], "unavailable")
        (self.raw / "commands.json").write_text(json.dumps(self.records()), encoding="utf-8")
        original_open = Path.open

        def unreadable(path, *args, **kwargs):
            if path.parent == self.raw and args == ("rb",):
                raise OSError("private-read-error")
            return original_open(path, *args, **kwargs)

        with mock.patch.object(Path, "open", new=unreadable):
            self.assertEqual(self.capture()["status"], "unavailable")
        self.assertEqual((self.destination / "console.log").read_text(), "[offline private UI details omitted]")

    def test_symlink_reports_and_expired_collection_do_not_claim_execution(self):
        outside = self.root / "outside.json"
        outside.write_text(json.dumps(self.records()), encoding="utf-8")
        linked = self.raw / "commands.json"
        linked.symlink_to(outside)
        self.assertEqual(self.capture()["status"], "unavailable")
        linked.unlink()
        linked.write_text(json.dumps(self.records()), encoding="utf-8")
        with mock.patch.object(cases.time, "monotonic", side_effect=[100.0, 103.0]):
            self.assertEqual(self.capture()["status"], "unavailable")

    def test_context_rejects_nonfixed_names_and_noninteger_invocations(self):
        for flow, action, invocation in (
            ("private-flow", "create", 1), ("offline-photo-create", "private-action", 1),
            ("offline-photo-create", "create", True), ("offline-photo-create", "create", "1"),
            ("offline-photo-create", "create", 0), ("offline-photo-create", "create", 100001),
        ):
            with self.subTest(flow=flow, action=action, invocation=invocation):
                self.redactor.set_command_context(flow, action, invocation)
                self.assertIsNone(self.redactor.command_summary(self.raw))

    def test_ordinary_redactors_and_offline_without_ui_context_keep_existing_artifacts(self):
        (self.raw / "safe.log").write_text("safe text", encoding="utf-8")
        (self.raw / "safe.png").write_bytes(b"\x89PNG\r\n\x1a\nsynthetic")
        core.save_debug(self.raw, self.destination, core.Redactor(PRIMARY), b"safe console")
        self.assertFalse((self.destination / "command-summary.json").exists())
        self.assertEqual((self.destination / "debug-001.log").read_text(), "safe text")
        self.assertEqual((self.destination / "screenshot-001.png").read_bytes(), b"\x89PNG\r\n\x1a\nsynthetic")
        private = self.root / "offline-without-context"
        core.save_debug(self.raw, private, cases.ArtifactRedactor(core, PRIMARY, PEER), b"safe console")
        self.assertFalse((private / "command-summary.json").exists())
        self.assertFalse(list(private.glob("*.png")))

    def test_diagnostic_interruptions_are_not_converted_to_unavailable(self):
        (self.raw / "commands.json").write_text(json.dumps(self.records()), encoding="utf-8")
        original_open = Path.open
        original_write = Path.write_text
        for stage in ("read", "parse", "write"):
            for interruption in (core.RunInterrupted("run-interrupted"), KeyboardInterrupt()):
                with self.subTest(stage=stage, kind=type(interruption).__name__), contextlib.ExitStack() as patches:
                    def interrupted_open(path, *args, **kwargs):
                        if path.parent == self.raw and args == ("rb",):
                            raise interruption
                        return original_open(path, *args, **kwargs)

                    def interrupted_write(path, *args, **kwargs):
                        if path.name == "command-summary.json":
                            raise interruption
                        return original_write(path, *args, **kwargs)

                    if stage == "read":
                        patches.enter_context(mock.patch.object(Path, "open", new=interrupted_open))
                    elif stage == "parse":
                        patches.enter_context(mock.patch.object(cases, "parse_command_records", side_effect=interruption))
                    else:
                        patches.enter_context(mock.patch.object(Path, "write_text", new=interrupted_write))
                    with self.assertRaises(type(interruption)) as caught:
                        self.capture()
                    self.assertIs(caught.exception, interruption)


class AppStateParserTests(unittest.TestCase):
    def test_exact_main_process_is_distinct_from_prefix_subprocess_and_driver(self):
        for names, expected in (
            ("init\ncom.project200.undabang\n[kworker/0:0]", True),
            ("init\ncom.project200.undabang:worker\ncom.project200.undabang.other\n"
             "dev.mobile.maestro\ndev.mobile.maestro.test", False),
        ):
            self.assertEqual(cases.parse_app_processes(("NAME\n" + names + "\n").encode()), {
                "status": "captured", "main_process_present": expected,
            })

    def test_missing_invalid_or_oversized_process_data_is_not_absence(self):
        for content in (
            b"", b"NAME\n", b"PID NAME\n1 private-process\n", b"NAME\n\n",
            b"NAME\nerror: private-adb-error\n", b"NAME\nprivate\x00process\n", b"NAME\n\xff\n",
            b"x" * (cases.APP_STATE_MAX_BYTES + 1),
            b"NAME\n" + b"init\n" * (cases.APP_STATE_MAX_PROCESSES + 1),
        ):
            self.assertEqual(cases.parse_app_processes(content), {"status": "unrecognized"})

    def test_public_current_record_shapes_and_exact_components_collapse_to_categories(self):
        for component, expected in (
            ("com.project200.undabang/.main.MainActivity", "main"),
            ("com.project200.undabang/com.project200.undabang.main.MainActivity", "main"),
            ("com.project200.undabang/.auth.login.LoginActivity", "login"),
            ("com.project200.undabang/com.project200.undabang.auth.login.LoginActivity", "login"),
            ("com.project200.undabang/.private.UnknownActivity", "app_other"),
            ("private.foreign/.PrivateActivity", "other_app"),
            ("com.project200.undabang.other/.main.MainActivity", "other_app"),
        ):
            for marker in ("topResumedActivity=", "mResumedActivity: ", "mResumedActivity=", "ResumedActivity: "):
                for tail in ("}", "} t12}", " t12}", "} t12 f} isExiting}"):
                    with self.subTest(component=component, marker=marker, tail=tail):
                        content = ("  " + marker + "ActivityRecord{abc u0 " + component + tail + "\n").encode()
                        self.assertEqual(cases.parse_resumed_activity(content), {
                            "status": "captured", "category": expected,
                        })

    def test_only_explicit_current_null_is_none_and_history_is_not_current(self):
        for content in (
            b"", b"ACTIVITY MANAGER ACTIVITIES\n",
            b"  Hist #0: ActivityRecord{abc u0 com.project200.undabang/.main.MainActivity t12}\n",
            b"  mLastResumedActivity: ActivityRecord{abc u0 private.foreign/.PrivateActivity t12}\n",
        ):
            self.assertEqual(cases.parse_resumed_activity(content), {"status": "unrecognized"})
        self.assertEqual(cases.parse_resumed_activity(b"  topResumedActivity=null\n"), {
            "status": "captured", "category": "none",
        })

    def test_top_current_marker_has_priority_but_conflicts_and_malformed_records_reject(self):
        top = b"topResumedActivity=ActivityRecord{abc u0 com.project200.undabang/.main.MainActivity t12}\n"
        legacy = b"mResumedActivity: ActivityRecord{def u0 private.foreign/.PrivateActivity t13}\n"
        self.assertEqual(cases.parse_resumed_activity(legacy + top), {"status": "captured", "category": "main"})
        for content in (
            top + top.replace(b".main.MainActivity", b".auth.login.LoginActivity"),
            top + top.replace(b"abc", b"def"),  # Same category is not the same resumed record.
            legacy + legacy.replace(b"private.foreign", b"another.foreign"),
            top + b"topResumedActivity=null\n", top + b"mResumedActivity: private-malformed\n",
            b"topResumedActivity\n", b"topResumedActivity=ActivityRecord{private-unknown}\n",
            top.replace(b" t12}", b" t12} private-trailing-data"),
            b"\xff", b"x" * (cases.APP_STATE_MAX_BYTES + 1),
            b"\n" * (cases.APP_STATE_MAX_ACTIVITY_LINES + 1),
        ):
            self.assertEqual(cases.parse_resumed_activity(content), {"status": "unrecognized"})

    def test_ui_markers_use_only_exact_owned_package_and_resource_ids(self):
        for selected in (("list",), ("detail_menu", "detail_content"), ("form",)):
            nodes = "".join(
                '<node package="com.project200.undabang" resource-id="%s"/>' % resource
                for name in selected for resource in (
                    cases.APP_STATE_MARKERS[name],
                    "com.project200.undabang:id/" + cases.APP_STATE_MARKERS[name],
                )
            )
            result = cases.parse_app_markers((
                '<?xml version="1.0"?><hierarchy rotation="0">' + nodes + '</hierarchy>\n'
                'UI hierchary dumped to: /dev/tty'
            ).encode())
            self.assertEqual(result, {
                "status": "captured", "markers": {name: name in selected for name in cases.APP_STATE_MARKERS},
            })
        foreign = (
            '<hierarchy><node package="private.foreign" resource-id="exercise_list_rv"/>'
            '<node resource-id="exercise_detail_menu"/>'
            '<node package="com.project200.undabang" resource-id="private.foreign:id/exercise_title_input" '
            'text="exercise_list_rv" content-desc="exercise_detail_scroll"/>'
            '<node package="com.project200.undabang.other" resource-id="exercise_detail_menu"/>'
            '<node package="com.project200.undabang" resource-id="exercise_detail_scroll_suffix"/></hierarchy>'
        )
        self.assertEqual(cases.parse_app_markers(foreign.encode()), {
            "status": "captured", "markers": dict.fromkeys(cases.APP_STATE_MARKERS, False),
        })
        self.assertEqual(cases.parse_app_markers(b"<hierarchy/>")["status"], "captured")

    def test_invalid_xml_entities_and_resource_limits_cannot_fabricate_markers(self):
        for content in (
            b"", b"ERROR: private-display-unavailable", b"<hierarchy><node></hierarchy>",
            b"<other><node/></other>", b"<hierarchy/><hierarchy/>", b"<hierarchy>\xff</hierarchy>",
            b'<!DOCTYPE hierarchy [<!ENTITY secret "private-token">]><hierarchy>&secret;</hierarchy>',
            b'<!ENTITY secret SYSTEM "file:///private/account"><hierarchy/>',
            b"x" * (cases.APP_STATE_MAX_BYTES + 1),
            ("<hierarchy>" + "<node/>" * cases.APP_STATE_MAX_NODES + "</hierarchy>").encode(),
            ("<hierarchy>" + "<node>" * cases.APP_STATE_MAX_DEPTH
             + "</node>" * cases.APP_STATE_MAX_DEPTH + "</hierarchy>").encode(),
        ):
            self.assertEqual(cases.parse_app_markers(content), {"status": "unrecognized"})


class AppStateCollectionTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="offline-app-state-tests-")
        self.addCleanup(temporary.cleanup)
        self.destination = Path(temporary.name) / "public"
        self.redactor = cases.ArtifactRedactor(core, PRIMARY, PEER)
        self.redactor.set_command_context("offline-photo-create", "create", 1)
        self.device = mock.Mock()
        self.device.call.side_effect = app_state_responses()

    def capture(self):
        self.redactor.capture_failure_state(self.device, self.destination)
        return json.loads((self.destination / "app-state-summary.json").read_text())

    def test_schema_queries_and_public_only_serialization(self):
        stdout, stderr = io.StringIO(), io.StringIO()
        self.redactor.command_context["private-extra"] = PRIMARY
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            result = self.capture()
        self.assertEqual(result, {
            "flow": "offline-photo-create", "action": "create", "invocation": 1,
            "phase": "after-maestro-failure-before-fixture-cleanup",
            "process": {"status": "captured", "main_process_present": True},
            "resumed_activity": {"status": "captured", "category": "main"},
            "ui_markers": {"status": "captured", "markers": {
                "list": False, "detail_menu": True, "detail_content": False, "form": False,
            }},
        })
        self.assertTrue(all(0 < call.kwargs["timeout"] <= 5 for call in self.device.call.call_args_list))
        public = json.dumps(result) + stdout.getvalue() + stderr.getvalue()
        for secret in ("private-", "com.project200", "ActivityRecord", " t12", "<?xml", "<node", "resource-id", RUN):
            self.assertNotIn(secret, public)
        self.assertEqual({path.name for path in self.destination.iterdir()}, {"app-state-summary.json"})
        self.assertEqual(stdout.getvalue() + stderr.getvalue(), "")

    def test_missing_or_invalid_context_prevents_queries_and_writes(self):
        for context in (None, {}, {"flow": "private-flow", "action": "create", "invocation": 1},
                        {"flow": "offline-photo-create", "action": "create", "invocation": True},
                        {"flow": "offline-photo-create", "action": "create", "invocation": 0}):
            self.redactor.command_context = context
            self.redactor.capture_failure_state(self.device, self.destination)
        self.device.call.assert_not_called()
        self.assertFalse(self.destination.exists())

    def test_shared_budget_skips_later_queries_and_each_timeout_is_remaining_positive_time(self):
        with mock.patch.object(cases.time, "monotonic", side_effect=[100, 108, 109, 110]):
            result = self.capture()
        self.assertEqual([call.kwargs["timeout"] for call in self.device.call.call_args_list], [2, 1])
        self.assertEqual(result["ui_markers"], {"status": "unavailable"})
        self.device.call.reset_mock()
        with mock.patch.object(cases.time, "monotonic", side_effect=[100, 110, 111, 112]):
            result = self.capture()
        self.device.call.assert_not_called()
        self.assertTrue(all(result[name] == {"status": "unavailable"}
                            for name in ("process", "resumed_activity", "ui_markers")))

    def test_failures_invalid_bytes_and_unknown_names_do_not_leak_or_claim_absence(self):
        for responses, statuses in (
            ((OSError("private-read-error"), (1, b"private-output", b"private-error"),
              (0, b"ERROR: private-display-unavailable", b"")),
             ("unavailable", "unavailable", "unrecognized")),
            (((0, b"\xff", b""), (0, b"private-unsupported-format", b""),
              (0, b"x" * (cases.APP_STATE_MAX_BYTES + 1), b"")),
             ("unrecognized", "unrecognized", "unrecognized")),
            (((0, b"NAME\nprivate-process\n", b""),
              (0, b"ResumedActivity: ActivityRecord{abc u0 private.package/.PrivateActivity t12}\n", b""),
              (0, b'<hierarchy><node package="private.package" text="private-account"/></hierarchy>', b"")),
             ("captured", "captured", "captured")),
        ):
            self.device.call.side_effect = responses
            stdout, stderr = io.StringIO(), io.StringIO()
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                result = self.capture()
            self.assertEqual(tuple(result[name]["status"] for name in (
                "process", "resumed_activity", "ui_markers",
            )), statuses)
            self.assertNotIn("private", json.dumps(result) + stdout.getvalue() + stderr.getvalue())
            self.assertEqual(stdout.getvalue() + stderr.getvalue(), "")
            if statuses[0] != "captured":
                self.assertNotIn("main_process_present", result["process"])
                self.assertNotIn("category", result["resumed_activity"])
                self.assertNotIn("markers", result["ui_markers"])

    def test_cancellation_propagates_at_read_parse_and_write_boundaries(self):
        original_write = Path.write_text
        for stage in ("read", "parse", "write"):
            for interruption in (core.RunInterrupted("run-interrupted"), KeyboardInterrupt()):
                self.device.call.side_effect = app_state_responses()
                with self.subTest(stage=stage, kind=type(interruption).__name__), contextlib.ExitStack() as patches:
                    def interrupted_write(path, *args, **kwargs):
                        if path.name == "app-state-summary.json":
                            raise interruption
                        return original_write(path, *args, **kwargs)

                    if stage == "read":
                        self.device.call.side_effect = interruption
                    elif stage == "parse":
                        patches.enter_context(mock.patch.object(cases, "parse_app_processes", side_effect=interruption))
                    else:
                        patches.enter_context(mock.patch.object(Path, "write_text", new=interrupted_write))
                    with self.assertRaises(type(interruption)) as caught:
                        self.capture()
                    self.assertIs(caught.exception, interruption)


class MaestroAppStateBoundaryTests(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="offline-maestro-state-tests-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name)
        self.device = mock.Mock(serial="private-device-serial")
        self.device.exercise_variables.return_value = {}
        self.device.call.side_effect = app_state_responses()
        self.redactor = cases.ArtifactRedactor(core, PRIMARY, PEER)
        self.redactor.set_command_context("offline-photo-create", "create", 1)
        self.mode = "failure"
        self.events = []
        for name in ("save_failure_logcat", "save_failure_anr", "save_failure_anr_events",
                     "save_driver_failure", "save_driver_connectivity"):
            patch = mock.patch.object(core, name, side_effect=lambda *_, name=name: self.events.append(name))
            patch.start()
            self.addCleanup(patch.stop)
        for patch in (mock.patch.object(core.shutil, "which", return_value="synthetic-maestro"),
                      mock.patch.object(core, "command", side_effect=self.command)):
            patch.start()
            self.addCleanup(patch.stop)

    def command(self, argv, _env, timeout=60):
        if argv[1:] == ["--version"]:
            return (0, b"private-version", b"") if self.mode == "version" else (0, core.MAESTRO_VERSION.encode(), b"")
        debug = Path(argv[argv.index("--debug-output") + 1])
        debug.mkdir()
        (debug / "private.png").write_bytes(b"\x89PNG\r\n\x1a\nprivate-image")
        if self.mode == "timeout":
            raise core.RunnerFailure("tool-timeout")
        if self.mode == "interrupted":
            raise core.RunInterrupted("run-interrupted")
        if self.mode == "keyboard":
            raise KeyboardInterrupt()
        if self.mode != "invalid-junit":
            report = Path(argv[argv.index("--output") + 1])
            report.write_text('<testsuite><testcase>' + (
                '<failure message="private-failure"/>' if self.mode == "failed-junit" else ""
            ) + '</testcase></testsuite>', encoding="utf-8")
        return (1 if self.mode == "failure" else 0), b"private-console", b"private-error"

    def run_maestro(self):
        core.run_maestro(self.device, "offline-photo-create", self.output, self.redactor, 42)

    def test_actual_failure_collects_once_before_existing_diagnostics_and_scrub(self):
        capture = self.redactor.capture_failure_state

        def collect(*args):
            self.events.append("capture")
            return capture(*args)

        with mock.patch.object(self.redactor, "capture_failure_state", side_effect=collect) as collector:
            with self.assertRaisesRegex(core.RunnerFailure, "^maestro-flow-failed$"):
                self.run_maestro()
        collector.assert_called_once_with(self.device, self.output / "offline-photo-create")
        self.assertEqual(self.events, [
            "capture", "save_failure_logcat", "save_failure_anr", "save_failure_anr_events",
            "save_driver_failure", "save_driver_connectivity",
        ])
        destination = self.output / "offline-photo-create"
        self.assertTrue((destination / "app-state-summary.json").exists())
        self.assertEqual((destination / "console.log").read_text(), "[offline private UI details omitted]")
        self.assertFalse(list(destination.glob("*.png")))
        for path in destination.iterdir():
            self.assertNotIn("private-", path.read_text())

    def test_success_interruption_and_version_failure_do_not_collect(self):
        for mode, failure in (("success", None), ("interrupted", core.RunInterrupted),
                              ("keyboard", KeyboardInterrupt), ("version", core.RunnerFailure)):
            self.mode = mode
            with self.subTest(mode=mode), mock.patch.object(self.redactor, "capture_failure_state") as collector:
                if failure is None:
                    self.run_maestro()
                else:
                    with self.assertRaises(failure):
                        self.run_maestro()
                collector.assert_not_called()
            self.assertFalse((self.output / "offline-photo-create/app-state-summary.json").exists())
        self.device.call.assert_not_called()

    def test_ordinary_redactor_does_not_add_device_queries(self):
        self.redactor = core.Redactor(PRIMARY)
        with self.assertRaisesRegex(core.RunnerFailure, "^maestro-flow-failed$"):
            self.run_maestro()
        self.device.call.assert_not_called()
        self.assertFalse((self.output / "offline-photo-create/app-state-summary.json").exists())

    def test_diagnostic_read_parse_and_write_errors_preserve_original_public_failure_and_scrub(self):
        original_write = Path.write_text
        for stage in ("read", "parse", "write", "hook"):
            self.device.call.side_effect = app_state_responses()
            with self.subTest(stage=stage), contextlib.ExitStack() as patches:
                def unwritable(path, *args, **kwargs):
                    if path.name == "app-state-summary.json":
                        raise OSError("private-write-error")
                    return original_write(path, *args, **kwargs)

                if stage == "read":
                    self.device.call.side_effect = OSError("private-adb-error")
                elif stage == "parse":
                    patches.enter_context(mock.patch.object(cases, "parse_app_processes",
                                                            side_effect=RuntimeError("private-parser-error")))
                elif stage == "write":
                    patches.enter_context(mock.patch.object(Path, "write_text", new=unwritable))
                else:
                    patches.enter_context(mock.patch.object(self.redactor, "capture_failure_state",
                                                            side_effect=RuntimeError("private-hook-error")))
                stdout, stderr = io.StringIO(), io.StringIO()
                with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                    with self.assertRaisesRegex(core.RunnerFailure, "^maestro-flow-failed$"):
                        self.run_maestro()
                destination = self.output / "offline-photo-create"
                self.assertEqual((destination / "console.log").read_text(), "[offline private UI details omitted]")
                self.assertFalse(list(destination.glob("*.png")))
                public = "".join(path.read_text() for path in destination.iterdir())
                self.assertNotIn("private-", public + stdout.getvalue() + stderr.getvalue())
                self.assertEqual(stdout.getvalue() + stderr.getvalue(), "")


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
        self.real_run_maestro = core.run_maestro
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
        self.assertEqual(redactor.command_context, {
            "flow": _flow, "action": action, "invocation": int(_output.name.split("-", 1)[0]),
        })
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

    def test_command_diagnostic_errors_preserve_original_failure_and_unsafe_cleanup(self):
        self.fail_cleanup = True
        self.device.exercise_variables.return_value = {}
        original_open = Path.open
        original_write = Path.write_text

        def command(argv, _env, timeout=60):
            if argv[1:] == ["--version"]:
                return 0, core.MAESTRO_VERSION.encode(), b""
            raw = Path(argv[argv.index("--debug-output") + 1])
            raw.mkdir()
            (raw / "commands.json").write_text(json.dumps([{
                "command": {"assertConditionCommand": {"selector": "private-selector"}},
                "metadata": {"status": "FAILED", "sequenceNumber": 0, "depth": 0},
            }]), encoding="utf-8")
            (raw / "private.png").write_bytes(b"\x89PNG\r\n\x1a\nprivate-image")
            return 1, b"private-console", b"private-error"

        def unreadable(path, *args, **kwargs):
            if path.name == "commands.json" and args == ("rb",):
                raise OSError("private-read-error")
            return original_open(path, *args, **kwargs)

        def unwritable(path, *args, **kwargs):
            if path.name == "command-summary.json":
                raise OSError("private-write-error")
            return original_write(path, *args, **kwargs)

        for stage in ("read", "parse", "write"):
            with self.subTest(stage=stage), contextlib.ExitStack() as patches:
                self.events.clear()
                self.args.output = self.output / stage
                patches.enter_context(mock.patch.object(core, "run_maestro", side_effect=core._run_maestro))
                patches.enter_context(mock.patch.object(core, "command", side_effect=command))
                patches.enter_context(mock.patch.object(core.shutil, "which", return_value="synthetic-maestro"))
                for name in ("save_failure_logcat", "save_failure_anr", "save_failure_anr_events", "save_driver_failure"):
                    patches.enter_context(mock.patch.object(core, name))
                if stage == "read":
                    patches.enter_context(mock.patch.object(Path, "open", new=unreadable))
                elif stage == "parse":
                    patches.enter_context(mock.patch.object(cases, "parse_command_records", side_effect=RuntimeError("private-parser-error")))
                else:
                    patches.enter_context(mock.patch.object(Path, "write_text", new=unwritable))
                result = self.run_driver("offline-photo-create", "offline-body-retry")
                self.assertIn("maestro-flow-failed", result[0][2])
                self.assertIn("bootstrap-cleanup-failed-offline-exercise-cleanup-unsafe", result[0][2])
                self.assertIn("private-state-preserved-for-recovery", result[0][2])
                self.assertIn("offline-phase-not-executed", result[1][2])
                self.assertEqual(self.events.count("clear"), 1)
                self.assertLess(self.index("online"), self.index("cleanup:"))
                output = self.args.output / "offline/offline-photo-create"
                result_json = json.loads((output / "result.json").read_text())
                self.assertEqual(result_json["status"], "cleanup-failed")
                self.assertEqual(result_json["failureStage"], "function")
                diagnostics = output / "01-create/offline-photo-create"
                self.assertFalse(list(diagnostics.glob("*.png")))
                for path in diagnostics.iterdir():
                    self.assertNotIn("private-", path.read_text())
                if stage != "write":
                    self.assertEqual(json.loads((diagnostics / "command-summary.json").read_text())["status"], "unavailable")

    def test_real_cli_failure_boundaries_capture_once_before_fixture_cleanup(self):
        capture = cases.ArtifactRedactor.capture_failure_state
        save_debug = core.save_debug
        self.device.serial = "private-device-serial"
        self.device.exercise_variables.return_value = {}
        self.fail_cleanup = True
        for mode, expected in (
            ("failure", "maestro-flow-failed"), ("timeout", "tool-timeout"),
            ("failed-junit", "maestro-assertion-failed"), ("invalid-junit", "missing-or-invalid-maestro-junit"),
        ):
            with self.subTest(mode=mode), contextlib.ExitStack() as patches:
                self.events.clear()
                self.args.output = self.output / mode
                responses = iter(app_state_responses())

                def query(*args, timeout):
                    self.events.append("state-query")
                    return next(responses)

                def collect(redactor, device, destination):
                    self.events.append("capture")
                    capture(redactor, device, destination)
                    self.events.append("captured")

                def scrub(*args):
                    self.events.append("scrub")
                    return save_debug(*args)

                def command(argv, _env, timeout=60):
                    if argv[1:] == ["--version"]:
                        return 0, core.MAESTRO_VERSION.encode(), b""
                    raw = Path(argv[argv.index("--debug-output") + 1])
                    raw.mkdir()
                    (raw / "private.png").write_bytes(b"\x89PNG\r\n\x1a\nprivate-image")
                    if mode == "timeout":
                        raise core.RunnerFailure("tool-timeout")
                    report = Path(argv[argv.index("--output") + 1])
                    report.write_text(
                        "<private-invalid" if mode == "invalid-junit"
                        else "<testsuite><testcase><failure/></testcase></testsuite>", encoding="utf-8",
                    )
                    return (1 if mode == "failure" else 0), b"private-console", b"private-error"

                self.device.call.side_effect = query
                patches.enter_context(mock.patch.object(core, "run_maestro", side_effect=self.real_run_maestro))
                patches.enter_context(mock.patch.object(core, "command", side_effect=command))
                patches.enter_context(mock.patch.object(core.shutil, "which", return_value="synthetic-maestro"))
                collector = patches.enter_context(mock.patch.object(
                    cases.ArtifactRedactor, "capture_failure_state", autospec=True, side_effect=collect,
                ))
                patches.enter_context(mock.patch.object(core, "save_debug", side_effect=scrub))
                for name in ("save_failure_logcat", "save_failure_anr", "save_failure_anr_events",
                             "save_driver_failure", "save_driver_connectivity"):
                    patches.enter_context(mock.patch.object(
                        core, name, side_effect=lambda *_, name=name: self.events.append(name),
                    ))
                result = self.run_driver("offline-photo-create", "offline-body-retry")
                self.assertIn(expected, result[0][2])
                self.assertIn("private-state-preserved-for-recovery", result[0][2])
                self.assertIn("offline-phase-not-executed", result[1][2])
                collector.assert_called_once()
                self.assertEqual(self.events.count("state-query"), 3)
                self.assertLess(self.index("captured"), self.index("save_driver_failure"))
                self.assertLess(self.index("captured"), self.index("scrub"))
                self.assertLess(self.index("scrub"), self.index("online"))
                self.assertLess(self.index("online"), self.index("cleanup:"))
                if mode == "failure":
                    self.assertLess(self.index("captured"), self.index("save_failure_logcat"))
                self.assertEqual(self.events.count("clear"), 1)
                destination = self.args.output / "offline/offline-photo-create/01-create/offline-photo-create"
                summary = json.loads((destination / "app-state-summary.json").read_text())
                self.assertEqual(summary["process"], {"status": "captured", "main_process_present": True})
                self.assertEqual(summary["resumed_activity"], {"status": "captured", "category": "main"})
                self.assertEqual(summary["ui_markers"]["markers"]["detail_menu"], True)
                self.assertFalse(list(destination.glob("*.png")))
                for path in destination.iterdir():
                    self.assertNotIn("private-", path.read_text())


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
