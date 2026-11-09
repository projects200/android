"""Drive run-owned photo/conflict/session cases through production UI and Worker."""

import json
from pathlib import Path
import re
import signal
import time
import xml.etree.ElementTree as ET


FLOWS = (
    "offline-photo-create", "offline-conflict-local", "offline-conflict-server",
    "offline-remote-delete", "offline-body-retry", "offline-session-retain",
    "offline-session-logout", "offline-session-adoption",
)
ACTIONS = (
    "create", "edit", "replace-photo", "relaunch", "assert-detail", "open-list",
    "open-draft", "assert-draft", "cancel-draft", "assert-retry", "retry",
    "assert-no-retry", "remote-cancel", "remote-confirm", "delete",
    "logout-cancel", "logout-confirm", "adoption-pending", "adoption-cancel",
    "adoption-confirm", "login-ready", "list-preserved", "open-server-record",
)
LOGIN_COMPONENT = "com.project200.undabang/com.project200.undabang.auth.login.LoginActivity"
PUBLIC_FAILURES = frozenset({
    "adb-operation-failed", "tool-timeout", "tool-unavailable", "maestro-flow-failed",
    "maestro-assertion-failed", "maestro-unavailable", "maestro-version-must-be-2.11.0",
    "missing-or-invalid-maestro-junit", "invalid-feature-variables", "invalid-feature-metadata",
    "invalid-bootstrap-result", "invalid-offline-control",
    "invalid-offline-flow-directory", "selected-offline-flow-or-helper-missing",
    "offline-requires-distinct-authenticated-accounts", "offline-photo-requires-disposable-emulator",
    "offline-photo-invalid-device-api", "offline-photo-requires-api-33",
    "offline-photo-invalid-media-metadata", "offline-photo-unexpected-media-provider-result",
    "offline-photo-owned-media-mismatch", "offline-photo-ambiguous-media-metadata",
    "offline-ui-preservation-unavailable", "offline-ui-preservation-mismatch",
    "offline-suite-requires-initial-online-network", "unsupported-device-network-settings",
    "unsupported-device-connectivity-report", "device-network-transition-not-observed",
    "device-network-restore-failed", "offline-suite-exercise-server-mismatch",
    "offline-suite-exercise-local-mismatch", "offline-suite-exercise-identity-changed",
    "offline-suite-requires-real-server-upload", "offline-suite-requires-enabled-cache-flag",
    "offline-suite-flag-fetch-failed", "pending-feature-restoration-refuses-app-data-clear",
    "app-data-clear-failed", "invalid-session-secrets", "invalid-fixture-start-date",
    "device-locale-must-be-ko-kr", "device-timezone-must-be-asia-seoul",
    *("bootstrap-" + operation + "-timeout" for operation in (
        "prepare", "cleanup", "feature-observe", "offline-observe", "offline-control",
    )),
})


def public_failure_codes(core, failure):
    if isinstance(failure, core.BootstrapFailure):
        return core.failure_codes(failure)
    return [str(failure)] if str(failure) in PUBLIC_FAILURES else ["offline-case-operation-failed"]


def capability_evidence(variables):
    evidence = {"app": {"status": "unavailable"}, "server": {"status": "unavailable"}}
    version, code = variables.get("E2E_APP_VERSION", ""), variables.get("E2E_APP_VERSION_CODE", "")
    if re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,79}", version) and re.fullmatch(r"[0-9]{1,18}", code):
        evidence["app"] = {"status": "observed", "version": version, "versionCode": code}
    if variables.get("E2E_SERVER_CONTRACT") == "observed-version-edited-at":
        evidence["server"] = {"status": "observed", "contract": "version-edited-at"}
    return evidence



COMMAND_TYPES = frozenset({
    "defineVariablesCommand", "applyConfigurationCommand", "runFlowCommand",
    "launchAppCommand", "stopAppCommand", "tapOnElement", "assertConditionCommand",
    "inputTextCommand", "eraseTextCommand", "hideKeyboardCommand", "backPressCommand",
    "scrollUntilVisible",
})
COMMAND_STATUSES = frozenset({"COMPLETED", "FAILED", "WARNED", "RUNNING", "SKIPPED", "PENDING"})
COMMAND_EXECUTED = frozenset({"COMPLETED", "FAILED", "WARNED", "RUNNING"})
COMMAND_HELPERS = {
    source: "offline/" + name + ".yaml"
    for name in ("dispatch", *ACTIONS, "pick-photo", "assert-exercise-record")
    for source in (name + ".yaml", "../offline/" + name + ".yaml")
}
COMMAND_HELPERS.update({
    source: "subflows/" + name + ".yaml"
    for name in (
        "launch-logged-in", "set-exercise-time", "select-exercise-date",
        "assert-exercise-detail", "assert-exercise-records-absent",
        "timer-clear-text", "timer-hide-keyboard",
    )
    for source in (name + ".yaml", "../subflows/" + name + ".yaml")
})
COMMAND_MAX_BYTES = 8388608
COMMAND_MAX_TOTAL_BYTES = 33554432
COMMAND_MAX_PATHS = 256
COMMAND_MAX_REPORTS = 16
COMMAND_MAX_RECORDS = 4096
COMMAND_MAX_DEPTH = 32
COMMAND_MAX_SEQUENCE = 100000
COMMAND_MAX_FAILURES = 32


def command_integer(value, maximum):
    return type(value) is int and 0 <= value <= maximum


def command_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError
        result[key] = value
    return result


def invalid_command_constant(_value):
    raise ValueError


def command_identity(records):
    return json.dumps(records, sort_keys=True, allow_nan=False, separators=(",", ":"))


def parse_command_records(content):
    # JSON 전체 크기와 중첩을 먼저 제한하고 실행 레코드의 root 배열만 읽습니다
    if len(content) > COMMAND_MAX_BYTES:
        raise ValueError
    nesting, quoted, escaped = 0, False, False
    for byte in content:
        if quoted:
            if escaped:
                escaped = False
            elif byte == 92:
                escaped = True
            elif byte == 34:
                quoted = False
        elif byte == 34:
            quoted = True
        elif byte in (91, 123):
            nesting += 1
            if nesting > 64:
                raise ValueError
        elif byte in (93, 125):
            nesting -= 1
    report = json.loads(content.decode("utf-8", "strict"), object_pairs_hook=command_object,
                        parse_constant=invalid_command_constant)
    if not isinstance(report, list) or not report or len(report) > COMMAND_MAX_RECORDS:
        raise ValueError
    records = {}
    for record in report:
        if not isinstance(record, dict):
            raise ValueError
        command, metadata = record.get("command"), record.get("metadata")
        if not isinstance(command, dict) or len(command) != 1 or not isinstance(metadata, dict):
            raise ValueError
        command_type = next(iter(command))
        if command_type not in COMMAND_TYPES or not isinstance(command[command_type], dict):
            raise ValueError
        sequence, depth = metadata.get("sequenceNumber"), metadata.get("depth")
        if (not command_integer(sequence, COMMAND_MAX_SEQUENCE)
                or not command_integer(depth, COMMAND_MAX_DEPTH)
                or metadata.get("status") not in COMMAND_STATUSES):
            raise ValueError
        if sequence in records and command_identity(records[sequence]) != command_identity(record):
            raise ValueError
        records[sequence] = record
    return records


def summarize_commands(records):
    active, executed, failures = [], [], []
    for sequence in sorted(records):
        record = records[sequence]
        command_type = next(iter(record["command"]))
        metadata = record["metadata"]
        depth, status = metadata["depth"], metadata["status"]
        # Maestro는 helper 환경 변수를 호출자 depth에서 기록한 뒤 자식 depth로 진입합니다
        while active and active[-1]["depth"] >= depth:
            if command_type == "defineVariablesCommand" and active[-1]["depth"] == depth:
                break
            active.pop()
        ancestors = [parent for parent in active if parent["depth"] < depth]
        if [parent["depth"] for parent in ancestors] != list(range(depth)):
            raise ValueError
        public = {
            "command_type": command_type, "sequenceNumber": sequence,
            "depth": depth, "status": status,
        }
        if command_type == "runFlowCommand":
            source = record["command"][command_type].get("sourceDescription")
            public["helper"] = COMMAND_HELPERS.get(source, "unknown") if isinstance(source, str) else "unknown"
        if status not in COMMAND_EXECUTED:
            continue
        observed = {**public, "ancestors": list(ancestors)}
        executed.append(observed)
        if status == "FAILED":
            failures.append(observed)
            if len(failures) > COMMAND_MAX_FAILURES:
                raise ValueError
        if command_type == "runFlowCommand":
            active.append(public)
    if not executed:
        raise ValueError
    propagated = {
        parent["sequenceNumber"]
        for failure in failures
        for parent in failure["ancestors"]
    }
    completed = [record for record in executed if record["status"] == "COMPLETED"]
    return {
        "status": "captured",
        "failures": [
            {**failure, "failure_role": (
                "propagated" if failure["sequenceNumber"] in propagated else "leaf"
            )}
            for failure in failures
        ],
        "last_executed": executed[-1],
        "last_completed": completed[-1] if completed else None,
    }


class ArtifactRedactor:
    """Keep fixed diagnostics, not arbitrary picker/account hierarchy or user images."""

    def __init__(self, core, primary, peer):
        self.core = core
        self.command_context = None
        self.redactor = core.Redactor({"primary": primary, "peer": peer})
        self.allowed = PUBLIC_FAILURES | frozenset(FLOWS) | frozenset(ACTIONS)

    def clean(self, text):
        clean, _ = self.redactor.clean(text)
        lines = [line.strip() for line in clean.splitlines() if line.strip() in self.allowed]
        # save_debug omits screenshots when safety cannot be established from the text.
        return "\n".join(lines) or "[offline private UI details omitted]", True

    def set_command_context(self, flow, action, invocation):
        self.command_context = (
            {"flow": flow, "action": action, "invocation": invocation}
            if flow in FLOWS and action in ACTIONS
            and command_integer(invocation, COMMAND_MAX_SEQUENCE) and invocation > 0 else None
        )

    def command_summary(self, raw):
        if self.command_context is None:
            return None
        summary = {"status": "unavailable"}
        try:
            records = None
            total, count = 0, 0
            deadline = time.monotonic() + 2
            for index, path in enumerate(raw.rglob("*")):
                if index >= COMMAND_MAX_PATHS or time.monotonic() >= deadline:
                    raise ValueError
                if (path.is_symlink()
                        or not re.fullmatch(r"commands(?:-\(.+\))?\.json", path.name)
                        or not path.is_file()):
                    continue
                count += 1
                if count > COMMAND_MAX_REPORTS:
                    raise ValueError
                with path.open("rb") as handle:
                    content = handle.read(min(COMMAND_MAX_BYTES, COMMAND_MAX_TOTAL_BYTES - total) + 1)
                total += len(content)
                if len(content) > COMMAND_MAX_BYTES or total > COMMAND_MAX_TOTAL_BYTES:
                    raise ValueError
                candidate = parse_command_records(content)
                # 복사본은 원문 신원까지 같아야 하며 서로 다른 보고서를 임의 병합하지 않습니다
                if records is not None and command_identity(records) != command_identity(candidate):
                    raise ValueError
                records = candidate
            if records is not None:
                summary = summarize_commands(records)
        except self.core.RunInterrupted:
            raise
        except Exception:
            pass
        return {**self.command_context, **summary}


def selector(value):
    return r"\Q" + value.replace(r"\E", r"\E\\E\Q") + r"\E"


def picker_variables(core, device, variables, run_id):
    """Prove native picker identity against the complete gallery, not app visibility."""
    if device.require("shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise core.RunnerFailure("offline-photo-requires-disposable-emulator")
    try:
        api = int(device.require("shell", "getprop", "ro.build.version.sdk").strip())
    except ValueError:
        raise core.RunnerFailure("offline-photo-invalid-device-api") from None
    if api < 33:
        raise core.RunnerFailure("offline-photo-requires-api-33")
    expected = []
    captures = []
    hashes = []
    for label in ("A", "B"):
        name = variables.get("PHOTO_" + label + "_NAME")
        taken = variables.get("PHOTO_" + label + "_TAKEN", "")
        digest = variables.get("PHOTO_" + label + "_SHA256", "")
        capture = variables.get("PHOTO_" + label + "_CAPTURE_TEXT", "")
        relative = variables.get("PHOTO_" + label + "_RELATIVE_PATH")
        if (name != "maestro-" + run_id + "-" + label.lower() + ".png"
                or not re.fullmatch(r"[0-9]+", taken)
                or not re.fullmatch(r"[a-f0-9]{64}", digest)
                or relative != "Pictures/maestro-offline/" + run_id + "/"
                or not capture or any(ord(char) < 32 for char in capture)):
            raise core.RunnerFailure("offline-photo-invalid-media-metadata")
        expected.append((name, taken, relative))
        captures.append(capture)
        hashes.append(digest)
    raw = device.require(
        "shell", "content", "query", "--uri", "content://media/external/images/media",
        "--projection", "_id:_display_name:datetaken:relative_path", "--sort", "'_id ASC'",
    ).decode("utf-8", "replace")
    rows = []
    for line in raw.splitlines():
        match = re.fullmatch(
            r"Row: [0-9]+ _id=[0-9]+, _display_name=([^,]+), datetaken=([0-9]+), relative_path=([^,]+)",
            line.strip(),
        )
        if not match:
            raise core.RunnerFailure("offline-photo-unexpected-media-provider-result")
        rows.append(match.groups())
    if len(rows) != len(set(rows)) or any(rows.count(row) != 1 for row in expected):
        raise core.RunnerFailure("offline-photo-owned-media-mismatch")
    seconds = {int(row[1]) // 1000 for row in expected}
    if (len(seconds) != 2 or len(set(captures)) != 2 or len(set(hashes)) != 2
            or any(row not in expected and (row[0] in {item[0] for item in expected}
                                           or int(row[1]) // 1000 in seconds) for row in rows)):
        raise core.RunnerFailure("offline-photo-ambiguous-media-metadata")
    return {"PHOTO_" + label + "_SELECTOR": "(?s).*" + selector(capture) + ".*"
            for label, capture in zip(("A", "B"), captures)}


def visible_snapshot(core, device, expected):
    """Keep only owned field values and bounds in memory; never persist hierarchy."""
    raw = device.require("shell", "uiautomator", "dump", "/dev/tty")
    start, end = raw.find(b"<?xml"), raw.rfind(b"</hierarchy>")
    if start < 0 or end < start or len(raw) > 1024 * 1024:
        raise core.RunnerFailure("offline-ui-preservation-unavailable")
    try:
        root = ET.fromstring(raw[start:end + len(b"</hierarchy>")])
    except ET.ParseError:
        raise core.RunnerFailure("offline-ui-preservation-unavailable") from None
    result = {}
    for identifier, value in expected.items():
        nodes = [node for node in root.iter("node")
                 if node.get("resource-id", "").split("/")[-1] == identifier
                 and value in (node.get("text"), node.get("content-desc"))]
        if len(nodes) != 1:
            raise core.RunnerFailure("offline-ui-preservation-mismatch")
        bounds = nodes[0].get("bounds", "")
        match = re.fullmatch(r"\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]", bounds)
        if not match:
            raise core.RunnerFailure("offline-ui-preservation-unavailable")
        left, top, right, bottom = map(int, match.groups())
        if right <= left or bottom <= top:
            raise core.RunnerFailure("offline-ui-preservation-mismatch")
        result[identifier] = (value, bounds)
    return result


class Case:
    def __init__(self, args, core, device, network, flow, credentials, peer, variables, parameters):
        self.args, self.core, self.device, self.network = args, core, device, network
        self.flow, self.credentials, self.peer = flow, credentials, peer
        self.variables, self.parameters = variables, parameters
        self.metadata = {"feature": "offline", "flow": flow, "run_id": variables["E2E_RUN_ID"],
                         "parameters": parameters}
        self.output = args.output / "offline" / flow
        self.redactor = ArtifactRedactor(core, credentials, peer)
        self.fault = False
        self.step = 0
        self.approved_erasure = False

    def ui(self, action, **values):
        if action not in ACTIONS:
            raise self.core.RunnerFailure("offline-invalid-ui-action")
        self.step += 1
        variables = self.core.safe_variables({**self.variables, "UI_ACTION": action, **values})
        self.redactor.set_command_context(self.flow, action, self.step)
        self.core.run_maestro(self.device, self.flow, self.output / ("%02d-" % self.step + action),
                              self.redactor, self.args.flow_timeout, variables=variables)

    def local(self, stage):
        result = self.device.bootstrap("feature-observe", {"memberId": self.credentials["memberId"]},
                                       **{**self.metadata, "parameters": {**self.parameters, "stage": stage}})
        self.variables.update(self.core.safe_variables(result))

    def server(self, stage, timeout=300):
        # The flat offline-observe envelope is deliberately different from feature-observe.
        result = self.device.bootstrap("offline-observe", {
            "memberId": self.credentials["memberId"], "runId": self.variables["E2E_RUN_ID"],
            "flow": self.flow, **self.parameters, "stage": stage,
        }, timeout=timeout)
        self.variables.update(self.core.safe_variables(result))

    def control(self, action, target=None):
        if action.startswith("block-"):
            self.fault = True  # A partial arm must still take the restoration path.
        request = {"memberId": self.credentials["memberId"]}
        if target is not None:
            request.update({"authState": target["authState"], "targetMemberId": target["memberId"]})
        result = self.device.bootstrap("offline-control", request, action=action, **self.metadata)
        self.variables.update(self.core.safe_variables(result))
        if action == "unblock-transport":
            self.fault = False

    def snapshot(self, form=False, listing=False):
        if listing:
            expected = {
                "exercise_title_tv": self.variables["RECORD_TITLE"],
                "exercise_list_rv": self.variables["EXERCISE_START_DATE"].replace(".", "-") + " 운동 기록 목록",
            }
        else:
            expected = {
                "exercise_description_input" if form else "exercise_detail_description":
                    self.variables["EXERCISE_EDITED_DESCRIPTION"] + "-unsaved" if form
                    else self.variables["RECORD_DESCRIPTION"],
            }
        return visible_snapshot(self.core, self.device, expected)

    def unchanged(self, before, **options):
        if self.snapshot(**options) != before:
            raise self.core.RunnerFailure("offline-ui-preservation-mismatch")

    def login(self, peer=False):
        self.control("stage-peer-login" if peer else "stage-primary-login", self.peer if peer else self.credentials)
        self.device.require("shell", "am", "start", "-W", "-n", LOGIN_COMPONENT)

    def create(self):
        self.network.disconnect()
        self.ui("create")
        self.local("exercise-case-local-created")

    def baseline(self):
        self.create()
        self.network.restore()
        self.server("exercise-case-baseline-synced")

    def edit(self, replace=False):
        self.ui("replace-photo" if replace else "edit")
        self.variables.update({"RECORD_TITLE": self.variables["EDITED_EXERCISE_TITLE"],
                               "RECORD_DESCRIPTION": self.variables["EXERCISE_EDITED_DESCRIPTION"],
                               "START_MINUTE": "05"})
        self.local("exercise-case-local-edited")

    def delete(self):
        self.ui("delete")
        self.server("exercise-case-ui-deleted-synced")

    def photo(self):
        self.create()
        self.control("block-photos")
        self.network.restore()
        self.server("exercise-case-photos-pending")
        self.network.disconnect()
        # BODY ACK must not settle points while A upload is still pending.
        self.ui("assert-detail")
        self.ui("relaunch")
        self.local("exercise-case-retained")
        self.control("unblock-transport")
        self.network.restore()
        self.server("exercise-case-baseline-synced")
        self.network.disconnect()
        self.edit(replace=True)
        self.control("media-delete-originals")
        self.ui("relaunch")
        self.local("exercise-case-retained")
        self.control("block-photos")
        self.network.restore()
        self.ui("open-draft")
        self.server("exercise-case-photos-pending")
        self.network.disconnect()
        self.ui("assert-draft")
        self.ui("relaunch")
        self.local("exercise-case-retained")
        self.ui("open-list")
        before = self.snapshot(listing=True)
        self.control("unblock-transport")
        self.network.restore()
        self.server("exercise-case-complete")
        self.ui("list-preserved")
        self.unchanged(before, listing=True)
        self.local("exercise-case-points-consumed")
        self.ui("relaunch")
        self.ui("open-list")
        self.local("exercise-case-points-stable")
        self.ui("assert-detail")
        self.delete()

    def conflict(self, server_wins):
        self.baseline()
        self.control("block-body")
        if not server_wins:
            self.control("remote-update")
        self.network.disconnect()
        self.edit()
        self.network.restore()
        if server_wins:
            self.control("remote-update")
        self.ui("open-draft")
        before = self.snapshot(form=True)
        self.control("unblock-transport")
        self.server("exercise-case-complete")
        self.ui("assert-draft")  # Neither Worker nor remote adoption replaces active input.
        self.unchanged(before, form=True)
        self.ui("cancel-draft")
        if server_wins:
            self.variables.update({"RECORD_TITLE": self.variables["EXERCISE_TITLE"],
                                   "RECORD_DESCRIPTION": self.variables["EXERCISE_REMOTE_DESCRIPTION"],
                                   "START_MINUTE": "00"})
        self.ui("relaunch")
        self.ui("assert-detail")
        self.delete()

    def retry(self):
        self.baseline()
        self.control("block-body")
        self.network.disconnect()
        self.edit()
        self.ui("assert-no-retry")
        before = self.snapshot()
        self.network.restore()
        self.server("exercise-case-retry-exhausted", timeout=750)
        self.ui("assert-retry")
        self.unchanged(before)
        self.network.disconnect()
        self.ui("assert-no-retry")
        # Do not re-enter Main or authenticate here: only the button may schedule new work.
        self.control("unblock-transport")
        self.network.restore()
        self.ui("retry")
        self.server("exercise-case-complete")
        self.ui("assert-detail")
        self.delete()

    def remote_delete(self):
        self.baseline()
        self.control("block-body")
        self.network.disconnect()
        self.edit()
        self.network.restore()
        self.control("remote-delete")
        self.control("unblock-transport")
        self.local("exercise-case-remote-deleted")
        self.ui("remote-cancel")
        self.local("exercise-case-discard-cancelled")
        self.ui("relaunch")
        self.local("exercise-case-retained")
        self.ui("remote-confirm")
        self.local("exercise-case-discarded")
        self.approved_erasure = True

    def session(self):
        self.local("session-baseline")
        self.create()
        self.control("block-photos")
        self.network.restore()
        self.server("exercise-case-photos-pending")
        self.local("session-pending")
        if self.flow == "offline-session-logout":
            self.ui("logout-cancel")
            self.local("session-logout-cancelled")
            self.ui("logout-confirm")
            self.local("session-logout-approved")
            self.approved_erasure = True
            self.login()
            self.ui("login-ready")
        else:
            self.control("force-exit")
            self.local("session-forced-retained")
            if self.flow == "offline-session-adoption":
                self.login(peer=True)
                self.ui("adoption-pending")
                self.local("session-peer-pending")
                self.ui("adoption-cancel")
                self.local("session-peer-cancelled")
                self.login()
                self.ui("login-ready")
                self.local("session-primary-restored")
                self.control("force-exit")
                self.login(peer=True)
                self.ui("adoption-pending")
                self.local("session-peer-pending")
                self.ui("adoption-confirm")
                self.local("session-peer-approved")
                self.approved_erasure = True
                self.server("session-peer-isolated")
                self.ui("logout-confirm")
                self.local("session-peer-logged-out")
                self.login()
                self.ui("login-ready")
            else:
                self.login()
                self.ui("login-ready")
                self.local("session-primary-restored")
        self.control("unblock-transport")
        if self.approved_erasure:
            # The approved device wipe does not delete the already ACKed A server body.
            self.ui("open-server-record")
            self.delete()
            self.local("session-primary-restored-clean")
        else:
            self.server("exercise-case-baseline-synced")
            self.ui("relaunch")
            self.ui("assert-detail")
            self.delete()

    def execute(self):
        if self.flow == "offline-photo-create":
            self.photo()
        elif self.flow in ("offline-conflict-local", "offline-conflict-server"):
            self.conflict(self.flow.endswith("server"))
        elif self.flow == "offline-body-retry":
            self.retry()
        elif self.flow == "offline-remote-delete":
            self.remote_delete()
        else:
            self.session()


def validate_paths(core):
    base = core.ROOT / ".maestro"
    for folder, names in (("flows", FLOWS), ("offline", ("dispatch", *ACTIONS, "pick-photo"))):
        directory = base / folder
        if directory.is_symlink() or not directory.is_dir():
            raise core.RunnerFailure("invalid-offline-flow-directory")
        for name in names:
            path = directory / (name + ".yaml")
            if path.is_symlink() or not path.is_file():
                raise core.RunnerFailure("selected-offline-flow-or-helper-missing")


def run_suite(args, core, network_type, variable_factory, input_factory):
    results = []
    try:
        validate_paths(core)
        credentials = core.read_credentials()
        peer = core.read_credentials(prefix="MAESTRO_PEER_")
        if credentials["memberId"] == peer["memberId"] or credentials["authState"] == peer["authState"]:
            raise core.RunnerFailure("offline-requires-distinct-authenticated-accounts")
        device = core.Device(args.device)
        device.verify_locale()
        device.verify_setup()
    except core.RunInterrupted:
        return [(flow, 0, ["run-interrupted", "offline-phase-not-executed"]) for flow in FLOWS]
    except core.RunnerFailure as failure:
        return [(flow, 0, public_failure_codes(core, failure) + ["offline-phase-not-executed"]) for flow in FLOWS]
    except Exception:
        return [(flow, 0, ["offline-case-preparation-failed", "offline-phase-not-executed"]) for flow in FLOWS]
    stopped = False
    for flow in FLOWS:
        if stopped:
            results.append((flow, 0, ["offline-phase-not-executed", "offline-prior-recovery-required"]))
            continue
        started = time.monotonic()
        errors, case, network = [], None, None
        prepared = False
        completed = False
        preparation_complete = False
        try:
            network = network_type(core, device)
            variables = core.safe_variables(variable_factory(device))
            parameters = {
                "startedAt": variables["EXERCISE_START_DATE"].replace(".", "-") + "T" + variables["EXERCISE_START_HOUR"] + ":00:00",
                "endedAt": variables["EXERCISE_END_DATE"].replace(".", "-") + "T" + variables["EXERCISE_END_HOUR"] + ":00:00",
                **input_factory(args).parameters(),
            }
            case = Case(args, core, device, network, flow, credentials, peer, variables, parameters)
            case.output.mkdir(parents=True, exist_ok=True)
            (case.output / "ownership.log").write_text("E2E_RUN_ID=" + variables["E2E_RUN_ID"] + "\n", encoding="utf-8")
            stopped = True
            device.clear()
            stopped = False
            prepared = True
            variables.update(core.safe_variables(device.bootstrap("prepare", credentials, **case.metadata)))
            variables.update(picker_variables(core, device, variables, variables["E2E_RUN_ID"]))
            variables.update({"RECORD_TITLE": variables["EXERCISE_TITLE"],
                              "RECORD_DESCRIPTION": variables["EXERCISE_DESCRIPTION"], "START_MINUTE": "00"})
            preparation_complete = True
            case.execute()
            completed = True
        except core.RunInterrupted:
            errors.append("run-interrupted")
            stopped = True
        except core.RunnerFailure as failure:
            details = public_failure_codes(core, failure)
            errors.extend(details)
            if any(code in core.OFFLINE_PREREQUISITE_FAILURES.values() for code in details):
                preparation_complete = False
        except Exception:
            errors.append("offline-case-operation-failed")
        finally:
            handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
            try:
                restored = True
                if network is not None:
                    try:
                        network.restore()
                    except Exception:
                        errors.append("device-network-restore-failed")
                        restored = False
                if prepared and case.fault:
                    try:
                        case.control("unblock-transport")
                    except Exception:
                        errors.append("offline-transport-restore-failed")
                        restored = False
                if prepared and restored:
                    try:
                        device.bootstrap("cleanup", credentials, **case.metadata)
                    except Exception as failure:
                        errors.append("fixture-cleanup-failed")
                        errors.extend(core.safe_cleanup_failure_codes(failure, "offline"))
                        restored = False
                if prepared and not restored:
                    errors.append("private-state-preserved-for-recovery")
                    stopped = True
                elif prepared:
                    try:
                        device.clear()
                    except Exception:
                        errors.append("final-app-data-clear-failed")
                        stopped = True
                        restored = False
            finally:
                for sig, handler in handlers.items():
                    signal.signal(sig, handler)
        if not completed:
            errors.append("offline-phase-not-executed")
        if case is not None:
            status = ("passed" if not errors else "interrupted" if "run-interrupted" in errors
                      else "cleanup-failed" if not restored
                      else "functional-failed" if preparation_complete else "preparation-failed")
            evidence = capability_evidence(case.variables)
            (case.output / "result.json").write_text(json.dumps({
                "flow": flow, "status": status, "uiCompleted": completed,
                "capabilityEvidence": evidence,
                "failureStage": ("none" if not errors else "preparation" if not preparation_complete
                                 else "function" if not completed else "cleanup"),
                "cleanupStatus": "completed" if prepared and restored else "failed" if prepared else "not-started",
                "errors": errors,
            }, ensure_ascii=False, sort_keys=True) + "\n", encoding="utf-8")
        results.append((flow, time.monotonic() - started, errors))
    return results
