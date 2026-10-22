"""Unit checks for orchestration boundaries, not real chat delivery evidence."""

import importlib.util
import os
import re
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("maestro_chat_driver", Path(__file__).with_name("chatting.py"))
chatting = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(chatting)


class RunnerFailure(Exception):
    pass


class RunInterrupted(RunnerFailure):
    pass


class ChatDriverTests(unittest.TestCase):
    def setUp(self):
        self.events = []
        self.failure_stage = None
        self.cleanup_failure = None
        self.prepare_failure = None
        self.interruption_stage = None
        self.diagnostics_unavailable = False
        self.sampler_failure = None
        self.sampler_active = False
        self.credentials = {
            "MAESTRO_": {"authState": {"refresh": "unit-a"}, "memberId": "a", "fixtureStartDate": "2026-01-01"},
            "MAESTRO_PEER_": {"authState": {"refresh": "unit-b"}, "memberId": "b", "fixtureStartDate": "2026-01-01"},
        }
        owner = self

        class Device:
            def __init__(self, serial):
                self.serial = serial
                owner.events.append(("device", serial))

            def configure_emulator(self):
                owner.events.append(("configure", self.serial))

            def verify_locale(self):
                owner.events.append(("locale", self.serial))

            def install(self, apk):
                owner.events.append(("install", self.serial, apk))

            def verify_setup(self):
                owner.events.append(("setup", self.serial))

            def clear(self):
                owner.events.append(("clear", self.serial))

            def bootstrap(self, operation, credentials, **request):
                owner.events.append((operation, self.serial, credentials["memberId"], request))
                if operation == "cleanup" and owner.cleanup_failure == self.serial:
                    raise RunnerFailure("unit-cleanup-failed")
                if operation == "prepare" and owner.prepare_failure == self.serial:
                    raise RunnerFailure("bootstrap-prepare-failed-setup")
                return {
                    "CHAT_ROOM_ID": "200",
                    "PEER_MEMBER_ID": request["parameters"]["MAESTRO_PEER_MEMBER_ID"],
                    "PEER_NICKNAME": "peer.*[]",
                }

        def run_maestro(device, flow, output, redactor, timeout, fixture_start_date, variables=None):
            name = output.parent.name
            self.events.append(("flow", device.serial, flow, name, variables))
            if name == self.interruption_stage:
                raise RunInterrupted("run-interrupted")
            if name == self.failure_stage:
                raise RunnerFailure("maestro-flow-failed")

        def capture_anr_checkpoint(device, destination, phase):
            self.events.append(("checkpoint", device.serial, phase))
            if self.diagnostics_unavailable:
                raise OSError("synthetic-private-diagnostic-error")

        class HostResourceSampler:
            def __init__(self, destination):
                self.destination = destination

            def start(self):
                owner.events.append(("sampling-start",))
                if owner.sampler_failure == "start":
                    raise RuntimeError("synthetic-private-thread-error")
                owner.sampler_active = True

            def stop(self):
                owner.events.append(("sampling-stop",))
                owner.sampler_active = False
                if owner.sampler_failure == "stop":
                    raise OSError("synthetic-private-write-error")

        self.core = SimpleNamespace(
            RunnerFailure=RunnerFailure, RunInterrupted=RunInterrupted,
            Device=Device, Redactor=lambda values: values,
            read_credentials=lambda prefix="MAESTRO_": self.credentials[prefix],
            run_maestro=run_maestro,
            capture_anr_checkpoint=capture_anr_checkpoint,
            host_resource_sampler=HostResourceSampler,
        )
        self.args = SimpleNamespace(
            device="device-a", peer_device="device-b", apk="unit-e2e.apk",
            configure_emulator=False, output=Path("unit-output"), flow_timeout=90,
        )
        environment = {key: "unit-required-value" for key in chatting.REQUIRED}
        environment["MAESTRO_CHAT_ROOM_ID"] = "200"
        patcher = mock.patch.dict(os.environ, environment, clear=True)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_missing_peer_inputs_fail_all_four_without_opening_a_device(self):
        for key in chatting.REQUIRED:
            os.environ.pop(key)
        results = chatting.run_suite(self.args, self.core)
        self.assertEqual([name for name, _, _ in results], list(chatting.FLOWS))
        self.assertTrue(all(errors for _, _, errors in results))
        self.assertEqual(self.events, [])
        for _, _, errors in results:
            self.assertIn("missing-maestro-peer-auth-state-json", errors)
            self.assertIn("missing-maestro-chat-room-id", errors)

    def test_primary_credentials_cannot_supply_the_peer(self):
        self.credentials["MAESTRO_PEER_"] = self.credentials["MAESTRO_"]
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all("chat-requires-distinct-authenticated-accounts" in errors for _, _, errors in results))
        self.assertEqual(self.events, [])

    def test_each_actor_keeps_its_identity_and_receiver_stays_live(self):
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all(not errors for _, _, errors in results))
        flow_events = [event for event in self.events if event[0] == "flow"]
        stages = [event[3] for event in flow_events]
        messages = {event[4]["MESSAGE"] for event in flow_events}
        self.assertEqual(len(messages), 3)
        self.assertTrue(all(re.fullmatch(r"[123]-[0-9]{1,10}", message) for message in messages))
        for barrier, sender, receiver in (
            ("a-to-b-receiver-barrier", "a-to-b-send", "a-to-b-live-receive"),
            ("b-to-a-receiver-barrier", "b-to-a-send", "b-to-a-live-receive"),
            ("history-receiver-away", "history-away-send", "history-away-list"),
        ):
            self.assertLess(stages.index(barrier), stages.index(sender))
            self.assertLess(stages.index(sender), stages.index(receiver))
        first_flow = next(index for index, event in enumerate(self.events) if event[0] == "flow")
        last_flow = max(index for index, event in enumerate(self.events) if event[0] == "flow")
        self.assertFalse(any(
            event[0] in ("configure", "install", "prepare", "cleanup", "clear")
            for event in self.events[first_flow:last_flow + 1]
        ))
        for actor, member, peer in (("device-a", "a", "b"), ("device-b", "b", "a")):
            prepared = next(event for event in self.events if event[:2] == ("prepare", actor))
            cleaned = next(event for event in self.events if event[:2] == ("cleanup", actor))
            self.assertEqual(prepared[2], member)
            self.assertEqual(prepared[3]["parameters"]["MAESTRO_PEER_MEMBER_ID"], peer)
            self.assertEqual(prepared[3], cleaned[3])
            self.assertIn(("locale", actor), self.events)
            self.assertTrue(any(event[:2] == ("install", actor) for event in self.events))
        self.assertEqual(len([event for event in self.events if event[0] == "prepare"]), 2)

    def test_live_reply_then_list_and_history_use_the_exact_messages_and_actors(self):
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all(not errors for _, _, errors in results))
        flows = [event for event in self.events if event[0] == "flow"]
        first = flows[0][4]["MESSAGE"]
        message_id = first.removeprefix("1-")
        reply, away = "2-" + message_id, "3-" + message_id
        self.assertEqual(
            [(event[1], event[2], event[3], event[4]["MESSAGE"]) for event in flows],
            [
                ("device-b", "chatting-open", "a-to-b-open-receiver", first),
                ("device-b", "chatting-ready", "a-to-b-receiver-barrier", first),
                ("device-a", "chatting-open", "a-to-b-open-sender", first),
                ("device-a", "chatting-send", "a-to-b-send", first),
                ("device-b", "chatting-receive", "a-to-b-live-receive", first),
                ("device-a", "chatting-ready", "b-to-a-receiver-barrier", reply),
                ("device-b", "chatting-send", "b-to-a-send", reply),
                ("device-a", "chatting-receive", "b-to-a-live-receive", reply),
                ("device-a", "chatting-away", "a-leave-before-list", reply),
                ("device-a", "chatting-list-refresh", "a-received-list", reply),
                ("device-b", "chatting-receive", "history-existing-before-leave", first),
                ("device-b", "chatting-away", "history-receiver-away", away),
                ("device-a", "chatting-open", "history-open-sender", away),
                ("device-a", "chatting-send", "history-away-send", away),
                ("device-b", "chatting-list-refresh", "history-away-list", away),
                ("device-b", "chatting-open", "history-reenter", away),
                ("device-b", "chatting-history", "history-retained-and-away", away),
            ],
        )
        for _, actor, _, _, variables in flows:
            peer = "b" if actor == "device-a" else "a"
            self.assertEqual(variables["MESSAGE_SELECTOR"], chatting.selector(variables["MESSAGE"]))
            self.assertEqual(variables["READY_SELECTOR"], chatting.selector("chat-ready:200:peer:" + peer))
            self.assertEqual(variables["ROOM_SELECTOR"], chatting.selector("chat-room:200:peer:" + peer))
        history = flows[-1][4]
        self.assertEqual(history["EXISTING_MESSAGE_SELECTOR"], chatting.selector(first))
        self.assertEqual(history["AWAY_MESSAGE_SELECTOR"], chatting.selector(away))

    def test_each_acceptance_failure_stops_the_protocol_without_passing_that_scenario(self):
        for stage, owner, passed in (
            ("a-to-b-send", "chatting-send", set()),
            ("b-to-a-send", "chatting-send", set()),
            ("b-to-a-live-receive", "chatting-receive", set()),
            ("a-received-list", "chatting-list-refresh", {"chatting-send", "chatting-receive"}),
            ("history-away-list", "chatting-history", set(chatting.FLOWS) - {"chatting-history"}),
            ("history-retained-and-away", "chatting-history", set(chatting.FLOWS) - {"chatting-history"}),
        ):
            with self.subTest(stage=stage):
                self.events.clear()
                self.failure_stage = stage
                results = chatting.run_suite(self.args, self.core)
                errors = {name: reasons for name, _, reasons in results}
                self.assertIn("chat-" + stage + "-failed", errors[owner])
                self.assertIn("chat-scenario-not-completed", errors[owner])
                self.assertEqual({name for name, reasons in errors.items() if not reasons}, passed)
                self.assertEqual([event for event in self.events if event[0] == "flow"][-1][3], stage)
                self.assertEqual(len([event for event in self.events if event[0] == "cleanup"]), 2)

    def test_failed_live_receipt_cannot_pass_dependent_scenarios(self):
        self.failure_stage = "a-to-b-live-receive"
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all(errors for _, _, errors in results))
        receive_errors = next(errors for name, _, errors in results if name == "chatting-receive")
        self.assertIn("chat-a-to-b-live-receive-failed", receive_errors)
        self.assertEqual(len([event for event in self.events if event[0] == "cleanup"]), 2)
        self.assertFalse(any(event[0] == "flow" and event[3] == "history-away-send" for event in self.events))

    def test_failed_cleanup_preserves_that_actors_private_recovery_state(self):
        self.cleanup_failure = "device-b"
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all("chat-b-fixture-cleanup-failed" in errors for _, _, errors in results))
        self.assertEqual(self.events.count(("clear", "device-b")), 1)
        self.assertEqual(self.events.count(("clear", "device-a")), 2)

    def test_checkpoints_bound_preparation_and_launch_without_extending_sampler_lifetime(self):
        self.args.configure_emulator = True
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all(not errors for _, _, errors in results))
        peer_ready = self.events.index(("checkpoint", "device-a", "peer-ready-before-primary-prepare"))
        fixture_ready = self.events.index(("checkpoint", "device-a", "primary-fixture-ready"))
        launch_ready = self.events.index(("checkpoint", "device-a", "receiver-ready-before-primary-launch"))
        self.assertLess(self.events.index(("sampling-start",)), self.events.index(("device", "device-b")))
        self.assertLess(self.events.index(("device", "device-b")), peer_ready)
        self.assertLess(peer_ready, self.events.index(("configure", "device-a")))
        prepare_a = next(index for index, event in enumerate(self.events) if event[:2] == ("prepare", "device-a"))
        self.assertLess(prepare_a, fixture_ready)
        self.assertLess(fixture_ready, self.events.index(("configure", "device-b")))
        barrier = next(index for index, event in enumerate(self.events)
                       if event[0] == "flow" and event[3] == "a-to-b-receiver-barrier")
        opened = next(index for index, event in enumerate(self.events)
                      if event[0] == "flow" and event[3] == "a-to-b-open-sender")
        sent = next(index for index, event in enumerate(self.events)
                    if event[0] == "flow" and event[3] == "a-to-b-send")
        self.assertLess(barrier, launch_ready)
        self.assertLess(launch_ready, opened)
        self.assertLess(opened, self.events.index(("sampling-stop",)))
        self.assertLess(self.events.index(("sampling-stop",)), sent)
        self.assertFalse(self.sampler_active)
        self.assertNotIn(("checkpoint", "device-a", "failure"), self.events)

    def test_primary_prepare_failure_captures_before_recovery_and_stops_sampling(self):
        self.prepare_failure = "device-a"
        self.diagnostics_unavailable = True
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all("bootstrap-prepare-failed-setup" in errors for _, _, errors in results))
        failure = self.events.index(("checkpoint", "device-a", "failure"))
        cleanup = next(index for index, event in enumerate(self.events) if event[0] == "cleanup")
        self.assertLess(failure, cleanup)
        self.assertLess(self.events.index(("sampling-stop",)), cleanup)
        self.assertFalse(self.sampler_active)
        self.assertNotIn(("checkpoint", "device-a", "primary-fixture-ready"), self.events)
        self.assertFalse(any(event[0] == "flow" for event in self.events))
        self.assertEqual(self.events.count(("clear", "device-a")), 2)
        self.assertFalse(any(event[:2] == ("prepare", "device-b") for event in self.events))

    def test_identity_mismatch_does_not_publish_fixture_ready_or_prepare_peer(self):
        with mock.patch.object(self.core.Device, "bootstrap", return_value={"CHAT_ROOM_ID": "wrong"}):
            results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all("chat-fixture-identity-mismatch" in errors for _, _, errors in results))
        self.assertNotIn(("checkpoint", "device-a", "primary-fixture-ready"), self.events)
        self.assertIn(("checkpoint", "device-a", "failure"), self.events)
        self.assertFalse(any(event[0] == "flow" for event in self.events))
        self.assertFalse(self.sampler_active)
        self.assertEqual(self.events.count(("clear", "device-a")), 2)
        self.assertNotIn(("locale", "device-b"), self.events)

    def test_automatic_peer_boot_is_observed_before_primary_preparation(self):
        self.args.peer_device = None

        def boot(core, primary):
            self.assertTrue(self.sampler_active)
            self.assertFalse(any(event[0] == "prepare" for event in self.events))
            return SimpleNamespace(serial="device-b", stop=lambda: self.events.append(("peer-stop",)))

        with mock.patch.object(chatting, "PeerEmulator", side_effect=boot):
            results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all(not errors for _, _, errors in results))
        self.assertFalse(self.sampler_active)
        self.assertIn(("peer-stop",), self.events)

    def test_failed_primary_open_keeps_flow_error_when_diagnostics_and_cleanup_fail(self):
        self.failure_stage = "a-to-b-open-sender"
        self.diagnostics_unavailable = True
        self.cleanup_failure = "device-b"
        self.sampler_failure = "stop"
        results = chatting.run_suite(self.args, self.core)
        errors = {name: reasons for name, _, reasons in results}
        self.assertIn("chat-a-to-b-open-sender-failed", errors["chatting-send"])
        self.assertTrue(all("chat-b-fixture-cleanup-failed" in reasons for reasons in errors.values()))
        self.assertTrue(all("chat-scenario-not-completed" in reasons for reasons in errors.values()))
        failure = self.events.index(("checkpoint", "device-a", "failure"))
        first_cleanup = next(index for index, event in enumerate(self.events) if event[0] == "cleanup")
        self.assertLess(failure, first_cleanup)
        self.assertLess(self.events.index(("sampling-stop",)), first_cleanup)
        self.assertFalse(self.sampler_active)
        self.assertEqual(self.events.count(("clear", "device-b")), 1)
        self.assertEqual(self.events.count(("clear", "device-a")), 2)
        self.assertNotIn("synthetic-private", str(results))
        self.assertFalse(any(event[0] == "flow" and event[3] == "a-to-b-send" for event in self.events))

    def test_interruption_stops_sampling_and_keeps_fixture_recovery_when_diagnostics_unavailable(self):
        for stage in ("a-to-b-receiver-barrier", "a-to-b-open-sender"):
            with self.subTest(stage=stage):
                self.events.clear()
                self.interruption_stage = stage
                self.diagnostics_unavailable = True
                results = chatting.run_suite(self.args, self.core)
                self.assertTrue(all("chat-run-interrupted" in errors for _, _, errors in results))
                owner = "chatting-send" if stage == "a-to-b-open-sender" else "chatting-receive"
                errors = next(errors for flow, _, errors in results if flow == owner)
                self.assertIn("chat-" + stage + "-interrupted", errors)
                cleanup = next(index for index, event in enumerate(self.events) if event[0] == "cleanup")
                self.assertLess(self.events.index(("checkpoint", "device-a", "failure")), cleanup)
                self.assertLess(self.events.index(("sampling-stop",)), cleanup)
                self.assertFalse(self.sampler_active)
                self.assertEqual(len([event for event in self.events if event[0] == "cleanup"]), 2)

    def test_peer_boot_failure_stops_sampling_and_captures_primary_before_return(self):
        self.args.peer_device = None
        with mock.patch.object(chatting, "PeerEmulator", side_effect=RunnerFailure("chat-peer-emulator-exited")):
            results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all("chat-peer-emulator-exited" in errors for _, _, errors in results))
        self.assertIn(("checkpoint", "device-a", "failure"), self.events)
        self.assertIn(("sampling-start",), self.events)
        self.assertIn(("sampling-stop",), self.events)
        self.assertFalse(self.sampler_active)
        self.assertFalse(any(event[0] in ("prepare", "cleanup", "flow") for event in self.events))

    def test_sampler_start_failure_does_not_replace_success_or_prevent_cleanup(self):
        self.sampler_failure = "start"
        results = chatting.run_suite(self.args, self.core)
        self.assertTrue(all(not errors for _, _, errors in results))
        self.assertFalse(self.sampler_active)
        self.assertEqual(len([event for event in self.events if event[0] == "cleanup"]), 2)

    def test_keyboard_interrupt_propagates_after_failure_checkpoint_and_cleanup(self):
        interruption = KeyboardInterrupt()
        with mock.patch.object(self.core, "run_maestro", side_effect=interruption):
            with self.assertRaises(KeyboardInterrupt) as caught:
                chatting.run_suite(self.args, self.core)
        self.assertIs(caught.exception, interruption)
        cleanup = next(index for index, event in enumerate(self.events) if event[0] == "cleanup")
        self.assertLess(self.events.index(("checkpoint", "device-a", "failure")), cleanup)
        self.assertLess(self.events.index(("sampling-stop",)), cleanup)
        self.assertFalse(self.sampler_active)
        self.assertEqual(len([event for event in self.events if event[0] == "cleanup"]), 2)

    def test_selectors_quote_regex_names_and_literal_quote_terminators(self):
        self.assertEqual(chatting.selector("peer.*[]"), "\\Qpeer.*[]\\E")
        self.assertEqual(chatting.selector("a\\Eb"), "\\Qa\\E\\\\E\\Qb\\E")


if __name__ == "__main__":
    unittest.main()
