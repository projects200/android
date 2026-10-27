import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
import tempfile


SPEC = importlib.util.spec_from_file_location("timer_test_core", Path(__file__).parents[1] / "run-tests.py")
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


SPEC = importlib.util.spec_from_file_location("maestro_timer_driver", Path(__file__).with_name("timer.py"))
timer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(timer)


class FakeCore:
    class RunnerFailure(Exception):
        pass

    class RunInterrupted(Exception):
        pass
    safe_variables = staticmethod(runner.safe_variables)

    @staticmethod
    def run_maestro(device, flow, output, redactor, timeout, variables=None):
        device.events.append(("flow", flow, dict(variables)))


class FakeDevice:
    def __init__(self):
        self.events = []
        self.prepared_variables = {"TIMER_INITIAL_TIME": "00:41", "TIMER_UPDATED_TIME": "00:42"}
        self.fail_stage = None

    def clear(self):
        self.events.append(("clear",))

    def bootstrap(self, operation, credentials, feature=None, flow=None, run_id=None, parameters=None):
        self.events.append((operation, flow, dict(parameters or {})))
        if operation == "prepare":
            return self.prepared_variables
        if operation == "feature-observe" and parameters["stage"] == self.fail_stage:
            raise FakeCore.RunnerFailure("identity-not-observed")
        if operation == "feature-observe" and parameters["stage"] == "custom-created":
            return {"TIMER_CUSTOM_LOCAL_ID": "owned-custom-id"}
        if operation == "feature-observe" and parameters["stage"] in ("simple-created", "simple-synced"):
            return {"TIMER_LOCAL_ID": "local-id"}
        return {}


class TimerDriverTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory(prefix="maestro-timer-driver-")
        self.addCleanup(temporary.cleanup)
        self.output = Path(temporary.name)

    def test_simple_run_keeps_both_expected_durations_equal_to_initial(self):
        self.assertEqual(timer.scenario_input(SimpleNamespace(), "timer-simple-run"), timer.SimpleTimerInput(45, 45))
        args = SimpleNamespace(timer_initial_seconds=53, timer_updated_seconds=97)
        self.assertEqual(timer.scenario_input(args, "timer-simple-run"), timer.SimpleTimerInput(53, 53))

    def test_simple_crud_observes_without_resetting_room_state(self):
        device = FakeDevice()
        args = SimpleNamespace(flow_timeout=10)
        credentials = {"memberId": "member"}

        result, safe = timer._run_timer(
            FakeCore(),
            args,
            device,
            "timer-simple-preset-crud",
            self.output,
            object(),
            credentials,
            "abcdef0123456789",
        )

        self.assertTrue(safe)
        self.assertEqual(result[2], [])
        self.assertEqual(
            [event[0] for event in device.events],
            [
                "clear", "prepare", "flow", "feature-observe", "flow", "feature-observe",
                "flow", "feature-observe", "flow", "feature-observe", "cleanup", "clear",
            ],
        )
        parameters = {"initialSeconds": "41", "updatedSeconds": "42"}
        self.assertEqual(device.events[1][2], parameters)
        self.assertEqual(device.events[3][2], {**parameters, "stage": "simple-baseline"})
        self.assertEqual(device.events[5][2], {**parameters, "stage": "simple-created"})
        self.assertEqual(device.events[7][2], {**parameters, "stage": "simple-synced"})
        self.assertEqual(device.events[9][2], {**parameters, "stage": "simple-deleted"})
        self.assertEqual(device.events[6][2], {
            "E2E_RUN_ID": "abcdef0123456789", "TIMER_LOCAL_ID": "local-id", **device.prepared_variables,
        })

    def test_custom_identity_is_observed_between_real_creation_and_reentry(self):
        device = FakeDevice()
        device.prepared_variables = {"TIMER_CUSTOM_TITLE": "run-owned-title"}
        result, safe = timer._run_timer(
            FakeCore(), SimpleNamespace(flow_timeout=10), device, "timer-custom-crud",
            self.output, object(), {"memberId": "member"}, "abcdef0123456789",
        )
        self.assertTrue(safe)
        self.assertEqual(result[2], [])
        self.assertEqual([event[0] for event in device.events],
                         ["clear", "prepare", "flow", "feature-observe", "flow", "cleanup", "clear"])
        self.assertEqual(device.events[2][1], "timer-custom-crud-create")
        self.assertNotIn("TIMER_CUSTOM_LOCAL_ID", device.events[2][2])
        self.assertEqual(device.events[4][2]["TIMER_CUSTOM_LOCAL_ID"], "owned-custom-id")
        self.assertEqual(device.events[4][2]["TIMER_CUSTOM_TITLE"], "run-owned-title")

    def test_missing_identity_stops_ui_continuation_but_cleans_original_inputs(self):
        device = FakeDevice()
        device.fail_stage = "simple-created"
        result, safe = timer._run_timer(
            FakeCore(), SimpleNamespace(flow_timeout=10, timer_initial_seconds=53, timer_updated_seconds=97),
            device, "timer-simple-preset-crud", self.output, object(),
            {"memberId": "member"}, "abcdef0123456789",
        )
        self.assertTrue(safe)
        self.assertEqual(result[2], ["identity-not-observed"])
        self.assertNotIn("timer-simple-preset-crud-continue", [event[1] for event in device.events if event[0] == "flow"])
        self.assertEqual(device.events[1][2], {"initialSeconds": "53", "updatedSeconds": "97"})
        self.assertEqual(device.events[-2], ("cleanup", "timer-simple-preset-crud", device.events[1][2]))


if __name__ == "__main__":
    unittest.main()
