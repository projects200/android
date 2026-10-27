import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest


SPEC = importlib.util.spec_from_file_location("maestro_timer_driver", Path(__file__).with_name("timer.py"))
timer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(timer)


class FakeCore:
    class RunnerFailure(Exception):
        pass

    class RunInterrupted(Exception):
        pass

    @staticmethod
    def run_maestro(device, flow, output, redactor, timeout, variables=None):
        device.events.append(("flow", flow, dict(variables)))


class FakeDevice:
    def __init__(self):
        self.events = []

    def clear(self):
        self.events.append(("clear",))

    def bootstrap(self, operation, credentials, feature=None, flow=None, run_id=None, parameters=None):
        self.events.append((operation, flow, dict(parameters or {})))
        if operation == "feature-observe" and parameters["stage"] in ("simple-created", "simple-synced"):
            return {"TIMER_LOCAL_ID": "local-id"}
        return {}


class TimerDriverTest(unittest.TestCase):
    def test_simple_crud_observes_without_resetting_room_state(self):
        device = FakeDevice()
        args = SimpleNamespace(flow_timeout=10)
        credentials = {"memberId": "member"}

        result, safe = timer._run_simple(
            FakeCore(),
            args,
            device,
            "timer-simple-preset-crud",
            Path("build/maestro-timer-test"),
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
        self.assertEqual(device.events[1][2], {})
        self.assertEqual(device.events[3][2], {"stage": "simple-baseline"})
        self.assertEqual(device.events[5][2], {"stage": "simple-created"})
        self.assertEqual(device.events[7][2], {"stage": "simple-synced"})
        self.assertEqual(device.events[9][2], {"stage": "simple-deleted"})
        self.assertEqual(device.events[6][2], {"E2E_RUN_ID": "abcdef0123456789", "TIMER_LOCAL_ID": "local-id"})


if __name__ == "__main__":
    unittest.main()
