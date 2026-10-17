"""Run real offline UI phases with independent server observation and safe recovery."""

from pathlib import Path
import re
import signal
import time


SCENARIOS = (
    ("offline-exercise-create", "offline-exercise-cached"),
    ("offline-exercise-missing",),
    ("offline-timers-online", "offline-timers-mutate", "offline-timers-relaunch",
     "offline-timers-after-reconnect", "offline-timers-reconnect"),
)
FLOWS = tuple(flow for scenario in SCENARIOS for flow in scenario)
HELPERS = (
    "assert-exercise-record", "open-timer-list", "create-timer", "edit-timer",
    "set-timer-time", "assert-timer-list", "prepare-timer-draft", "assert-timer-draft",
)


def validate_paths(core):
    base = core.ROOT / ".maestro"
    if base.is_symlink() or not base.is_dir():
        raise core.RunnerFailure("invalid-offline-flow-directory")
    for folder, names in (("flows", FLOWS), ("offline", HELPERS)):
        directory = base / folder
        if directory.is_symlink() or not directory.is_dir():
            raise core.RunnerFailure("invalid-offline-flow-directory")
        for name in names:
            path = directory / (name + ".yaml")
            if path.is_symlink() or not path.is_file():
                raise core.RunnerFailure("selected-offline-flow-or-helper-missing")


class NetworkState:
    """Restore the original radios, not an assumed Wi-Fi configuration."""

    KEYS = ("wifi_on", "mobile_data", "airplane_mode_on")

    def __init__(self, core, device):
        self.core = core
        self.device = device
        self.original = self.settings()
        self.original_connected = self.connected()
        if not self.original_connected:
            raise core.RunnerFailure("offline-suite-requires-initial-online-network")

    def settings(self):
        values = {}
        for key in self.KEYS:
            value = self.device.require("shell", "settings", "get", "global", key).strip()
            if value not in (b"0", b"1"):
                raise self.core.RunnerFailure("unsupported-device-network-settings")
            values[key] = value
        return values

    def connected(self):
        output = self.device.require("shell", "dumpsys", "connectivity").decode("utf-8", "replace")
        # Read the current default, never a historical VALIDATED entry.
        values = re.findall(r"^\s*Active default network:\s*(none|[0-9]+)\s*$", output, re.MULTILINE)
        if len(values) != 1:
            raise self.core.RunnerFailure("unsupported-device-connectivity-report")
        return values[0] != "none"

    def wait(self, expected, connected):
        deadline = time.monotonic() + 30
        while time.monotonic() < deadline:
            if self.settings() == expected and self.connected() == connected:
                return
            time.sleep(0.5)
        raise self.core.RunnerFailure("device-network-transition-not-observed")

    def disconnect(self):
        self.device.require("shell", "svc", "wifi", "disable")
        self.device.require("shell", "svc", "data", "disable")
        self.wait({**self.original, "wifi_on": b"0", "mobile_data": b"0"}, False)

    def restore(self):
        failed = False
        for service, key in (("wifi", "wifi_on"), ("data", "mobile_data")):
            try:
                self.device.require("shell", "svc", service, "enable" if self.original[key] == b"1" else "disable")
            except Exception:
                failed = True
        if failed:
            raise self.core.RunnerFailure("device-network-restore-failed")
        self.wait(self.original, self.original_connected)


def offline_variables(device):
    variables = device.exercise_variables()
    run_id = variables["EXERCISE_TITLE"].removeprefix("maestro-e2e-")
    prefix = "maestro-offline-" + run_id
    return {**variables, "E2E_RUN_ID": run_id,
            "TIMER_CREATE_TITLE": prefix + "-create", "TIMER_ORIGINAL_TITLE": prefix + "-update",
            "TIMER_UPDATED_TITLE": prefix + "-updated", "TIMER_DELETE_TITLE": prefix + "-delete",
            "TIMER_DRAFT_TITLE": prefix + "-draft"}


def run_suite(args, core):
    errors = {flow: [] for flow in FLOWS}
    durations = {flow: 0.0 for flow in FLOWS}
    completed = set()
    try:
        validate_paths(core)
        credentials = core.read_credentials()
        redactor = core.Redactor(credentials)
        device = core.Device(args.device)
        if args.configure_emulator:
            device.configure_emulator()
        device.verify_locale()
        if args.apk:
            device.install(Path(args.apk).resolve())
        device.verify_setup()
    except core.RunInterrupted:
        return [(flow, 0, ["run-interrupted"]) for flow in FLOWS]
    except core.RunnerFailure as failure:
        return [(flow, 0, [str(failure)]) for flow in FLOWS]
    except Exception:
        return [(flow, 0, ["offline-driver-setup-failed"]) for flow in FLOWS]

    for scenario in SCENARIOS:
        network = None
        prepared = False
        safe_to_continue = True
        owner = scenario[0]
        try:
            network = NetworkState(core, device)
            variables = core.safe_variables(offline_variables(device))
            run_id = variables["E2E_RUN_ID"]
            parameters = {
                "startedAt": variables["EXERCISE_START_DATE"].replace(".", "-") + "T" + variables["EXERCISE_START_HOUR"] + ":00:00",
                "endedAt": variables["EXERCISE_END_DATE"].replace(".", "-") + "T" + variables["EXERCISE_END_HOUR"] + ":00:00",
            }
            metadata = {"feature": "offline", "flow": owner, "run_id": run_id, "parameters": parameters}
            output = args.output / "offline" / owner
            output.mkdir(parents=True, exist_ok=True)
            (output / "ownership.log").write_text("E2E_RUN_ID=" + run_id + "\n", encoding="utf-8")
            safe_to_continue = False
            device.clear()
            safe_to_continue = True
            prepared = True
            device.bootstrap("prepare", credentials, **metadata)

            def phase(flow):
                nonlocal owner
                owner = flow
                started = time.monotonic()
                try:
                    core.run_maestro(device, flow, output, redactor, args.flow_timeout, variables=variables)
                    completed.add(flow)
                finally:
                    durations[flow] += time.monotonic() - started

            def observe(stage):
                device.bootstrap("offline-observe", {
                    "memberId": credentials["memberId"], "runId": run_id, "flow": scenario[0],
                    **parameters, "stage": stage,
                })

            if scenario == SCENARIOS[0]:
                phase("offline-exercise-create")
                observe("exercise-online")
                network.disconnect()
                phase("offline-exercise-cached")
            elif scenario == SCENARIOS[1]:
                network.disconnect()
                phase("offline-exercise-missing")
            else:
                phase("offline-timers-online")
                observe("timers-online")
                network.disconnect()
                phase("offline-timers-mutate")
                phase("offline-timers-relaunch")
                network.restore()
                phase("offline-timers-after-reconnect")
                observe("timers-synced")
                phase("offline-timers-reconnect")
        except core.RunInterrupted:
            errors[owner].append("run-interrupted")
            safe_to_continue = False
        except core.RunnerFailure as failure:
            errors[owner].append(str(failure))
        except Exception:
            errors[owner].append("offline-driver-operation-failed")
        finally:
            handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
            try:
                restored = True
                if network is not None:
                    try:
                        network.restore()
                    except Exception:
                        restored = False
                        safe_to_continue = False
                        for flow in scenario:
                            errors[flow].append("device-network-restore-failed")
                cleanup_failed = False
                if prepared and restored:
                    try:
                        device.bootstrap("cleanup", credentials, **metadata)
                    except Exception:
                        cleanup_failed = True
                        safe_to_continue = False
                        for flow in scenario:
                            errors[flow].append("fixture-cleanup-failed")
                if prepared and (not restored or cleanup_failed):
                    for flow in scenario:
                        errors[flow].append("private-state-preserved-for-recovery")
                elif prepared:
                    try:
                        device.clear()
                    except Exception:
                        safe_to_continue = False
                        for flow in scenario:
                            errors[flow].append("final-app-data-clear-failed")
            finally:
                for sig, handler in handlers.items():
                    signal.signal(sig, handler)
        if not safe_to_continue:
            break

    for flow in FLOWS:
        if flow not in completed:
            errors[flow].append("offline-phase-not-executed")
    return [(flow, durations[flow], errors[flow]) for flow in FLOWS]
