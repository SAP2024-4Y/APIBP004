"""Startup-readiness regressions; write requests must never be retried on resets."""
import http.client
import importlib.util
from pathlib import Path
import unittest
from unittest.mock import MagicMock, patch

spec = importlib.util.spec_from_file_location("walkthrough", Path(__file__).resolve().parents[1] / "api-walkthrough.py")
walkthrough = importlib.util.module_from_spec(spec)
spec.loader.exec_module(walkthrough)


class ReadinessTest(unittest.TestCase):
    def test_transient_startup_disconnects_are_retried(self):
        response = MagicMock()
        response.__enter__.return_value.status = 200
        with patch.object(walkthrough.urllib.request, "urlopen", side_effect=[
            ConnectionResetError("starting"), http.client.RemoteDisconnected("starting"), response
        ]) as request, patch.object(walkthrough.time, "sleep") as sleep:
            walkthrough.Api("http://localhost:18080").wait(30)
        self.assertEqual(3, request.call_count)
        self.assertEqual(2, sleep.call_count)
        self.assertTrue(all(call.args[0].endswith("/health") for call in request.call_args_list))

    def test_resets_do_not_extend_the_startup_deadline(self):
        with patch.object(walkthrough.urllib.request, "urlopen", side_effect=ConnectionResetError()), \
             patch.object(walkthrough.time, "monotonic", side_effect=[0, 2]), \
             patch.object(walkthrough.time, "sleep") as sleep:
            with self.assertRaisesRegex(RuntimeError, "before the deadline"):
                walkthrough.Api("http://localhost:18080").wait(1)
        sleep.assert_not_called()

    def test_write_connection_failures_are_not_retried(self):
        with patch.object(walkthrough.urllib.request, "urlopen", side_effect=ConnectionResetError()) as request:
            with self.assertRaises(ConnectionResetError):
                walkthrough.Api("http://localhost:18080").call("POST", "/api/v1/jobs", {})
        request.assert_called_once()


if __name__ == "__main__":
    unittest.main()
