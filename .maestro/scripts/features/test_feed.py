"""Unit tests for feed media ownership checks. These tests do not invoke adb."""

import importlib.util
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest import mock


SPEC = importlib.util.spec_from_file_location("maestro_feed", Path(__file__).with_name("feed.py"))
feed = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(feed)
RUN_ID = "0123456789abcdef"


class Failure(Exception):
    pass


class FeedMediaTest(unittest.TestCase):
    def setUp(self):
        self.core = SimpleNamespace(RunnerFailure=Failure)
        self.variables = {}
        for label, digest, taken in (("A", "a" * 64, "1704153600000"), ("B", "b" * 64, "1704067200000")):
            self.variables.update({
                "FEED_IMAGE_" + label + "_NAME": "maestro-feed-" + RUN_ID + "-" + label + ".png",
                "FEED_IMAGE_" + label + "_SHA256": digest,
                "FEED_IMAGE_" + label + "_TAKEN": taken,
                "FEED_IMAGE_" + label + "_CAPTURE_TEXT": "2024-01-" + ("02" if label == "A" else "01") + " 오전 9:00:00",
            })
        self.rows = [
            "Row: 0 _id=11, _display_name=maestro-feed-" + RUN_ID + "-A.png, datetaken=1704153600000, relative_path=Pictures/maestro-feed/" + RUN_ID + "/",
            "Row: 1 _id=10, _display_name=maestro-feed-" + RUN_ID + "-B.png, datetaken=1704067200000, relative_path=Pictures/maestro-feed/" + RUN_ID + "/",
        ]

    def device(self, rows=None, emulator=b"1", api=b"33"):
        device = mock.Mock()
        device.require.side_effect = [emulator, api, "\n".join(self.rows if rows is None else rows).encode()]
        return device

    def before(self, device, flow="feed-photo"):
        return feed.before_flow(self.core, device, flow, self.variables, {}, RUN_ID)

    def test_selection_uses_capture_metadata_regardless_of_provider_order(self):
        expected = {
            "FEED_IMAGE_A_SELECTOR": r"(?s).*\Q2024-01-02 오전 9:00:00\E.*",
            "FEED_IMAGE_B_SELECTOR": r"(?s).*\Q2024-01-01 오전 9:00:00\E.*",
        }
        self.assertEqual(self.before(self.device()), expected)
        self.assertEqual(self.before(self.device(list(reversed(self.rows)))), expected)

    def test_unrelated_distinct_image_is_preserved_and_does_not_change_selection(self):
        device = self.device(self.rows + ["Row: 2 _id=9, _display_name=personal.png, datetaken=1, relative_path=Pictures/"])
        self.assertEqual(self.before(device), self.before(self.device()))
        self.assertTrue(all("delete" not in call.args for call in device.require.call_args_list))

    def test_foreign_image_with_same_native_second_is_ambiguous(self):
        device = self.device(self.rows + [
            "Row: 2 _id=9, _display_name=personal.png, datetaken=1704153600999, relative_path=Pictures/",
        ])
        with self.assertRaisesRegex(Failure, "^feed-photo-ambiguous-media-metadata$"):
            self.before(device)
        self.assertTrue(all("delete" not in call.args for call in device.require.call_args_list))

    def test_duplicate_owned_media_cannot_choose_an_arbitrary_row(self):
        with self.assertRaisesRegex(Failure, "^feed-photo-owned-media-mismatch$"):
            self.before(self.device(self.rows + [self.rows[0].replace("_id=11", "_id=12")]))

    def test_native_metadata_is_required_and_java_regex_is_literal(self):
        self.variables["FEED_IMAGE_A_CAPTURE_TEXT"] = r"Jan [2]. (9:00)\E"
        self.assertEqual(
            self.before(self.device())["FEED_IMAGE_A_SELECTOR"],
            r"(?s).*\QJan [2]. (9:00)\E\\E\Q\E.*",
        )
        self.variables["FEED_IMAGE_A_CAPTURE_TEXT"] = ""
        with self.assertRaisesRegex(Failure, "^feed-photo-invalid-media-metadata$"):
            self.before(self.device())

    def test_duplicate_native_capture_text_is_not_a_unique_selector(self):
        self.variables["FEED_IMAGE_B_CAPTURE_TEXT"] = self.variables["FEED_IMAGE_A_CAPTURE_TEXT"]
        with self.assertRaisesRegex(Failure, "^feed-photo-ambiguous-media-metadata$"):
            self.before(self.device())

    def test_null_capture_date_cannot_fall_back_to_insert_or_file_time(self):
        rows = [self.rows[0].replace("datetaken=1704153600000", "datetaken=NULL"), self.rows[1]]
        with self.assertRaisesRegex(Failure, "^feed-photo-unexpected-media-provider-result$"):
            self.before(self.device(rows))

    def test_rescan_changed_capture_date_cannot_select_a_different_asset(self):
        rows = [self.rows[0].replace("datetaken=1704153600000", "datetaken=1704186000000"), self.rows[1]]
        with self.assertRaisesRegex(Failure, "^feed-photo-owned-media-mismatch$"):
            self.before(self.device(rows))

    def test_equal_capture_dates_cannot_rely_on_insertion_ids(self):
        rows = [self.rows[0], self.rows[1].replace("datetaken=1704067200000", "datetaken=1704153600000")]
        with self.assertRaisesRegex(Failure, "^feed-photo-owned-media-mismatch$"):
            self.before(self.device(rows))

    def test_missing_or_foreign_media_is_not_a_fallback(self):
        with self.assertRaisesRegex(Failure, "^feed-photo-owned-media-mismatch$"):
            self.before(self.device(self.rows[:1]))
        with self.assertRaisesRegex(Failure, "^feed-photo-unexpected-media-provider-result$"):
            self.before(self.device(["No result found."]))

    def test_equal_asset_checksums_fail_before_provider_query(self):
        self.variables["FEED_IMAGE_B_SHA256"] = self.variables["FEED_IMAGE_A_SHA256"]
        device = self.device()
        with self.assertRaisesRegex(Failure, "^feed-photo-images-must-differ$"):
            self.before(device)
        self.assertEqual(device.require.call_count, 2)

    def test_physical_device_and_old_api_fail_before_gallery_access(self):
        with self.assertRaisesRegex(Failure, "^feed-photo-requires-disposable-emulator$"):
            self.before(self.device(emulator=b"0"))
        with self.assertRaisesRegex(Failure, "^feed-photo-requires-api-33$"):
            self.before(self.device(api=b"30"))

    def test_other_flows_do_not_read_or_change_the_gallery(self):
        device = mock.Mock()
        self.assertEqual(self.before(device, "feed-comment"), {})
        device.require.assert_not_called()


if __name__ == "__main__":
    unittest.main()
