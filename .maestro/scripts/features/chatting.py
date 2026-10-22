"""Coordinate real UI chat exchanges without restarting a live receiver."""

import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import time
import uuid


FLOWS = ("chatting-send", "chatting-receive", "chatting-list-refresh", "chatting-history")
REQUIRED = (
    "MAESTRO_PEER_AUTH_STATE_JSON", "MAESTRO_PEER_MEMBER_ID",
    "MAESTRO_PEER_FIXTURE_START_DATE", "MAESTRO_CHAT_ROOM_ID",
)


def selector(value):
    # Maestro uses Java regular expressions, including for accessibility labels.
    return "\\Q" + value.replace("\\E", "\\E\\\\E\\Q") + "\\E"


class PeerEmulator:
    def __init__(self, core, primary):
        self.core = core
        self.process = None
        self.serial = None
        avd_output = primary.require("emu", "avd", "name").decode("utf-8", "replace")
        names = [line.strip() for line in avd_output.splitlines() if line.strip() and line.strip() != "OK"]
        if len(names) != 1 or not re.fullmatch(r"[A-Za-z0-9_.-]+", names[0]):
            raise core.RunnerFailure("chat-primary-must-have-supported-avd")
        emulator = shutil.which("emulator")
        if not emulator:
            sdk = os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME")
            candidate = Path(sdk) / "emulator" / "emulator" if sdk else None
            if candidate and candidate.is_file():
                emulator = str(candidate)
        if not emulator:
            raise core.RunnerFailure("chat-peer-emulator-tool-unavailable")
        devices = primary.require("devices").decode("utf-8", "replace")
        port = None
        for candidate in range(5554, 5585, 2):
            if "emulator-" + str(candidate) in devices or primary.serial == "emulator-" + str(candidate):
                continue
            ports = []
            try:
                for number in (candidate, candidate + 1):
                    connection = socket.socket()
                    ports.append(connection)
                    connection.bind(("127.0.0.1", number))
                port = candidate
                break
            except OSError:
                continue
            finally:
                for connection in ports:
                    connection.close()
        if port is None:
            raise core.RunnerFailure("chat-peer-emulator-port-unavailable")
        self.serial = "emulator-" + str(port)
        env = core.tool_environment()
        for key in ("ANDROID_AVD_HOME", "ANDROID_USER_HOME", "ANDROID_SDK_HOME"):
            if key in os.environ:
                env[key] = os.environ[key]
        try:
            self.process = subprocess.Popen(
                [emulator, "-avd", names[0], "-read-only", "-port", str(port),
                 "-no-window", "-no-audio", "-no-boot-anim", "-no-snapshot",
                 "-gpu", "swiftshader", "-timezone", "Asia/Seoul"],
                cwd=core.ROOT, env=env, stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True,
            )
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline:
                if self.process.poll() is not None:
                    raise core.RunnerFailure("chat-peer-emulator-exited")
                code, output, _ = core.command(
                    [primary.adb[0], "-s", self.serial, "shell", "getprop", "sys.boot_completed"],
                    env, timeout=15,
                )
                if code == 0 and output.strip() == b"1":
                    return
                # Poll an actual boot-complete property. This is not a chat phase delay.
                time.sleep(0.5)
            raise core.RunnerFailure("chat-peer-emulator-boot-timeout")
        except BaseException:
            self.stop()
            raise

    def stop(self):
        if self.process is None:
            return
        if self.process.poll() is None:
            os.killpg(self.process.pid, signal.SIGTERM)
            try:
                self.process.wait(timeout=20)
            except subprocess.TimeoutExpired:
                os.killpg(self.process.pid, signal.SIGKILL)
                self.process.wait(timeout=20)


def run_suite(args, core):
    errors = {flow: [] for flow in FLOWS}
    durations = {flow: 0.0 for flow in FLOWS}
    completed = set()
    actors = {}
    cleanup_requests = []
    emulator = None
    primary = None
    sampler = None
    keep_peer = False
    run_id = uuid.uuid4().hex[:16]
    room_id = None
    redactor = None
    stage_started = False

    def checkpoint(phase):
        if primary is None:
            return
        try:
            core.capture_anr_checkpoint(
                primary, args.output / "chatting" / "diagnostics" / phase, phase,
            )
        except core.RunInterrupted:
            raise
        except Exception:
            # 진단 부재는 UI 성공 증거가 아니며 원래 흐름과 복구를 바꾸지 않습니다.
            pass

    def stop_sampler():
        if sampler is None:
            return
        try:
            sampler.stop()
        except core.RunInterrupted:
            raise
        except Exception:
            pass

    def stage(name, owner, actor, flow, message, **extra):
        nonlocal stage_started
        stage_started = True
        started = time.monotonic()
        variables = dict(actors[actor]["variables"])
        variables.update({"MESSAGE": message, "MESSAGE_SELECTOR": selector(message), **extra})
        try:
            core.run_maestro(
                actors[actor]["device"], flow,
                args.output / "chatting" / name / actor, redactor, args.flow_timeout,
                actors[actor]["credentials"]["fixtureStartDate"], variables=variables,
            )
        except core.RunInterrupted:
            errors[owner].append("chat-" + name + "-interrupted")
            raise
        except core.RunnerFailure:
            errors[owner].append("chat-" + name + "-failed")
            raise
        except Exception:
            errors[owner].append("chat-" + name + "-operation-failed")
            raise
        finally:
            durations[owner] += time.monotonic() - started

    try:
        missing = [key for key in REQUIRED if not os.environ.get(key, "").strip()]
        if missing:
            # Names are fixed public identifiers. Values never enter diagnostics.
            for key in missing:
                for flow in FLOWS:
                    errors[flow].append("missing-" + key.lower().replace("_", "-"))
            raise core.RunnerFailure("chat-missing-required-environment")
        credentials = core.read_credentials()
        peer_credentials = core.read_credentials(prefix="MAESTRO_PEER_")
        if (credentials["memberId"] == peer_credentials["memberId"] or
                credentials["authState"] == peer_credentials["authState"]):
            raise core.RunnerFailure("chat-requires-distinct-authenticated-accounts")
        room_id = os.environ["MAESTRO_CHAT_ROOM_ID"].strip()
        if not re.fullmatch(r"[1-9][0-9]{0,18}", room_id) or int(room_id) > 9223372036854775807:
            raise core.RunnerFailure("chat-invalid-dedicated-room-id")
        if not args.apk:
            raise core.RunnerFailure("chat-requires-e2e-apk")
        redactor = core.Redactor({"primary": credentials, "peer": peer_credentials, "room": room_id})
        primary = core.Device(args.device)
        try:
            sampler = core.host_resource_sampler(
                args.output / "chatting" / "diagnostics" / "host-resources.log",
            )
            sampler.start()
        except core.RunInterrupted:
            raise
        except Exception:
            pass
        if args.peer_device:
            if args.peer_device == primary.serial:
                raise core.RunnerFailure("chat-requires-two-distinct-devices")
            peer = core.Device(args.peer_device)
        else:
            emulator = PeerEmulator(core, primary)
            peer = core.Device(emulator.serial)
        actors = {
            "a": {"device": primary, "credentials": credentials},
            "b": {"device": peer, "credentials": peer_credentials},
        }
        checkpoint("peer-ready-before-primary-prepare")
        for actor, opposite in (("a", "b"), ("b", "a")):
            device = actors[actor]["device"]
            if args.configure_emulator or (actor == "b" and emulator is not None):
                device.configure_emulator()
            device.verify_locale()
            device.install(Path(args.apk).resolve())
            device.verify_setup()
            device.clear()
            request = {
                "feature": "chatting", "flow": "chatting-send", "run_id": run_id,
                "parameters": {
                    "MAESTRO_CHAT_ROOM_ID": room_id,
                    "MAESTRO_PEER_MEMBER_ID": actors[opposite]["credentials"]["memberId"],
                },
            }
            # Register cleanup before prepare, which may fail after its ownership marker is written.
            cleanup_requests.append((actor, request))
            variables = device.bootstrap("prepare", actors[actor]["credentials"], **request)
            expected_peer = actors[opposite]["credentials"]["memberId"]
            if (variables.get("CHAT_ROOM_ID") != room_id or variables.get("PEER_MEMBER_ID") != expected_peer or
                    not variables.get("PEER_NICKNAME")):
                raise core.RunnerFailure("chat-fixture-identity-mismatch")
            variables.update({
                "E2E_RUN_ID": run_id,
                "ROOM_SELECTOR": selector("chat-room:" + room_id + ":peer:" + expected_peer),
                "READY_SELECTOR": selector("chat-ready:" + room_id + ":peer:" + expected_peer),
                "DISCONNECTED_SELECTOR": selector("chat-disconnected:" + room_id),
                "PEER_LABEL_SELECTOR": selector(variables["PEER_NICKNAME"] + "님과의 채팅"),
            })
            actors[actor]["variables"] = variables
            if actor == "a":
                checkpoint("primary-fixture-ready")

        message_id = str(int(run_id[:8], 16))
        first = "1-" + message_id
        reply = "2-" + message_id
        away = "3-" + message_id

        # B is live and M1 is absent before A sends. No B launch/bootstrap occurs until receipt.
        stage("a-to-b-open-receiver", "chatting-receive", "b", "chatting-open", first)
        stage("a-to-b-receiver-barrier", "chatting-receive", "b", "chatting-ready", first)
        checkpoint("receiver-ready-before-primary-launch")
        try:
            stage("a-to-b-open-sender", "chatting-send", "a", "chatting-open", first)
        finally:
            stop_sampler()
        stage("a-to-b-send", "chatting-send", "a", "chatting-send", first)
        stage("a-to-b-live-receive", "chatting-receive", "b", "chatting-receive", first)
        # 두 소켓이 준비된 동안 B가 같은 화면에서 바로 답장하고 A가 실시간으로 받습니다.
        stage("b-to-a-receiver-barrier", "chatting-receive", "a", "chatting-ready", reply)
        stage("b-to-a-send", "chatting-send", "b", "chatting-send", reply)
        stage("b-to-a-live-receive", "chatting-receive", "a", "chatting-receive", reply)
        completed.update(("chatting-send", "chatting-receive"))
        stage("a-leave-before-list", "chatting-list-refresh", "a", "chatting-away", reply)
        stage("a-received-list", "chatting-list-refresh", "a", "chatting-list-refresh", reply)
        completed.add("chatting-list-refresh")

        # B must retain M1 before leaving. Only observed socket closure releases A's M3 send.
        stage("history-existing-before-leave", "chatting-history", "b", "chatting-receive", first)
        stage("history-receiver-away", "chatting-history", "b", "chatting-away", away)
        stage("history-open-sender", "chatting-history", "a", "chatting-open", away)
        stage("history-away-send", "chatting-history", "a", "chatting-send", away)
        stage("history-away-list", "chatting-history", "b", "chatting-list-refresh", away)
        stage("history-reenter", "chatting-history", "b", "chatting-open", away)
        stage(
            "history-retained-and-away", "chatting-history", "b", "chatting-history", away,
            EXISTING_MESSAGE_SELECTOR=selector(first), AWAY_MESSAGE_SELECTOR=selector(away),
        )
        completed.add("chatting-history")
    except core.RunInterrupted:
        for flow in FLOWS:
            if flow not in completed:
                errors[flow].append("chat-run-interrupted")
    except core.RunnerFailure as failure:
        if not stage_started:
            # Only core/driver fixed errors reach this branch, never backend response text.
            reason = str(failure)
            if not re.fullmatch(r"[a-z][a-z0-9-]*", reason):
                reason = "chat-preflight-failed"
            for flow in FLOWS:
                errors[flow].append(reason)
    except Exception:
        for flow in FLOWS:
            if flow not in completed:
                errors[flow].append("chat-driver-operation-failed")
    finally:
        handlers = {sig: signal.signal(sig, signal.SIG_IGN) for sig in (signal.SIGINT, signal.SIGTERM)}
        try:
            try:
                if primary is not None and len(completed) != len(FLOWS):
                    checkpoint("failure")
            except (core.RunInterrupted, KeyboardInterrupt):
                for flow in FLOWS:
                    if flow not in completed:
                        errors[flow].append("chat-run-interrupted")
            finally:
                # 이전 단계에서 실패해도 fixture 정리보다 먼저 수집 스레드를 중단합니다.
                try:
                    stop_sampler()
                except (core.RunInterrupted, KeyboardInterrupt):
                    for flow in FLOWS:
                        if flow not in completed:
                            errors[flow].append("chat-run-interrupted")
            for actor, request in reversed(cleanup_requests):
                device = actors[actor]["device"]
                try:
                    device.bootstrap("cleanup", actors[actor]["credentials"], **request)
                except Exception:
                    for flow in FLOWS:
                        errors[flow].append("chat-" + actor + "-fixture-cleanup-failed")
                    if actor == "b":
                        keep_peer = True
                    # Keep private recovery state. Never clear after failed cleanup.
                    continue
                try:
                    device.clear()
                except Exception:
                    for flow in FLOWS:
                        errors[flow].append("chat-" + actor + "-final-clear-failed")
                    if actor == "b":
                        keep_peer = True
            if emulator is not None and not keep_peer:
                try:
                    emulator.stop()
                except Exception:
                    for flow in FLOWS:
                        errors[flow].append("chat-peer-emulator-stop-failed")
            elif emulator is not None:
                for flow in FLOWS:
                    errors[flow].append("chat-peer-emulator-kept-for-recovery")
        finally:
            for sig, handler in handlers.items():
                signal.signal(sig, handler)
    for flow in FLOWS:
        if flow not in completed:
            errors[flow].append("chat-scenario-not-completed")
    return [(flow, durations[flow], errors[flow]) for flow in FLOWS]
