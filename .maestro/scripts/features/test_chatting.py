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
                return {
                    "CHAT_ROOM_ID": "200",
                    "PEER_MEMBER_ID": request["parameters"]["MAESTRO_PEER_MEMBER_ID"],
                    "PEER_NICKNAME": "peer.*[]",
                }

        def run_maestro(device, flow, output, redactor, timeout, fixture_start_date, variables=None):
            name = output.parent.name
            self.events.append(("flow", device.serial, flow, name, variables))
            if name == self.failure_stage:
                raise RunnerFailure("maestro-flow-failed")

        self.core = SimpleNamespace(
            RunnerFailure=RunnerFailure, RunInterrupted=RunInterrupted,
            Device=Device, Redactor=lambda values: values,
            read_credentials=lambda prefix="MAESTRO_": self.credentials[prefix],
            run_maestro=run_maestro,
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
        self.assertTrue(all(event[0] == "flow" for event in self.events[first_flow:last_flow + 1]))
        for actor, member, peer in (("device-a", "a", "b"), ("device-b", "b", "a")):
            prepared = next(event for event in self.events if event[:2] == ("prepare", actor))
            cleaned = next(event for event in self.events if event[:2] == ("cleanup", actor))
            self.assertEqual(prepared[2], member)
            self.assertEqual(prepared[3]["parameters"]["MAESTRO_PEER_MEMBER_ID"], peer)
            self.assertEqual(prepared[3], cleaned[3])
            self.assertIn(("locale", actor), self.events)
            self.assertTrue(any(event[:2] == ("install", actor) for event in self.events))
        self.assertEqual(len([event for event in self.events if event[0] == "prepare"]), 2)

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

    def test_selectors_quote_regex_names_and_literal_quote_terminators(self):
        self.assertEqual(chatting.selector("peer.*[]"), "\\Qpeer.*[]\\E")
        self.assertEqual(chatting.selector("a\\Eb"), "\\Qa\\E\\\\E\\Qb\\E")


if __name__ == "__main__":
    unittest.main()
