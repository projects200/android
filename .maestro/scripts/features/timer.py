"""Run timer flows with Room identity observation and owned server cleanup."""

import signal
import time
import uuid
from pathlib import Path


FLOWS = (
    "timer-simple-preset-crud",
    "timer-simple-run",
    "timer-custom-crud",
    "timer-custom-run",
    "timer-form-validation",
)
SIMPLE_FLOWS = frozenset(("timer-simple-preset-crud", "timer-simple-run"))
SUITE = {"name": "timer", "fixture": True}


def _observe(device, credentials, flow, run_id, stage):
    return device.bootstrap(
        "feature-observe",
        {"memberId": credentials["memberId"]},
        feature="timer",
        flow=flow,
        run_id=run_id,
        parameters={"stage": stage},
    )


def _run_maestro(core, args, device, flow, output, redactor, variables):
    core.run_maestro(device, flow, output, redactor, args.flow_timeout, variables=variables)


def _run_simple(core, args, device, flow, output, redactor, credentials, run_id):
    started = time.monotonic()
    errors = []
    prepared = False
    safe_to_continue = True
    variables = {"E2E_RUN_ID": run_id}
    flow_output = output / flow
    flow_output.mkdir(parents=True, exist_ok=True)
    (flow_output / "ownership.log").write_text("E2E_RUN_ID=" + run_id + "\n", encoding="utf-8")
    try:
        device.clear()
        prepared = True
        variables.update(device.bootstrap("prepare", credentials, feature="timer", flow=flow, run_id=run_id, parameters={}))
        _run_maestro(core, args, device, flow, flow_output, redactor, variables)
        _observe(device, credentials, flow, run_id, "simple-baseline")
        _run_maestro(core, args, device, flow + "-create", flow_output, redactor, variables)
        variables.update(_observe(device, credentials, flow, run_id, "simple-created"))
        if flow == "timer-simple-preset-crud":
            _run_maestro(core, args, device, flow + "-continue", flow_output, redactor, variables)
            variables.update(_observe(device, credentials, flow, run_id, "simple-synced"))
            _run_maestro(core, args, device, flow + "-finish", flow_output, redactor, variables)
        else:
            _run_maestro(core, args, device, flow + "-reenter", flow_output, redactor, variables)
            variables.update(_observe(device, credentials, flow, run_id, "simple-synced"))
            _run_maestro(core, args, device, flow + "-execute", flow_output, redactor, variables)
        _observe(device, credentials, flow, run_id, "simple-deleted")
    except core.RunInterrupted:
        errors.append("run-interrupted")
        safe_to_continue = False
    except core.RunnerFailure as failure:
        errors.append(str(failure))
    except Exception:
        errors.append("timer-driver-operation-failed")
    finally:
        handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        try:
            cleanup_failed = False
            if prepared:
                try:
                    device.bootstrap("cleanup", credentials, feature="timer", flow=flow, run_id=run_id, parameters={})
                except Exception:
                    errors.append("fixture-cleanup-failed")
                    cleanup_failed = True
                    safe_to_continue = False
            if cleanup_failed:
                errors.append("private-state-preserved-for-recovery")
            elif prepared:
                try:
                    device.clear()
                except Exception:
                    errors.append("final-app-data-clear-failed")
                    safe_to_continue = False
        finally:
            for sig, handler in handlers.items():
                signal.signal(sig, handler)
    return (flow, time.monotonic() - started, errors), safe_to_continue


def run_suite(args, core):
    results = []
    try:
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
        return [(flow, 0, ["timer-driver-setup-failed"]) for flow in FLOWS]

    continue_run = True
    for flow in FLOWS:
        if not continue_run:
            results.append((flow, 0, ["not-executed-after-unsafe-cleanup-or-interruption"]))
            continue
        if flow in SIMPLE_FLOWS:
            result, continue_run = _run_simple(
                core,
                args,
                device,
                flow,
                args.output / "timer",
                redactor,
                credentials,
                uuid.uuid4().hex[:16],
            )
        else:
            result, continue_run = core.run_ordinary_flow(
                args,
                device,
                SUITE,
                flow,
                args.output / "timer",
                redactor,
                credentials,
                {},
                None,
            )
        results.append(result)
    return results
