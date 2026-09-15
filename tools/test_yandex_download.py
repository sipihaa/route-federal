"""Offline checks: no real API key or network requests are used."""

import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import urllib.error
import yandex_api
import download_yandex_network as downloader


class DownloadTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        (self.root / ".env.local").write_text("YANDEX_RASP_API_KEY=test-key-never-real\n")
        self.patch_api = patch.object(yandex_api, "ROOT", self.root)
        self.patch_downloader = patch.object(downloader, "ROOT", self.root)
        self.patch_api.start()
        self.patch_downloader.start()

    def tearDown(self):
        self.patch_downloader.stop()
        self.patch_api.stop()
        self.temporary.cleanup()

    def test_cache_works_with_zero_remaining_requests_and_key_is_not_in_url(self):
        api = yandex_api.YandexApi(max_requests=1)
        api.opener = Mock()
        api.opener.open.return_value = io.BytesIO(b'{"copyright": {}}')
        api.get("copyright")
        request = api.opener.open.call_args.args[0]
        self.assertNotIn("test-key", request.full_url)
        self.assertEqual(request.get_header("Authorization"), "test-key-never-real")
        cached = yandex_api.YandexApi(max_requests=0)
        cached.opener = Mock(side_effect=AssertionError("Network must not be used"))
        self.assertEqual(cached.get("copyright"), {"copyright": {}})
        self.assertEqual(cached.requests, 0)
        self.assertEqual(cached.cache_hits, 1)
        self.assertEqual(len(json.loads((self.root / ".yandex-cache/requests.json").read_text())), 1)

    def test_daily_budget_survives_restart(self):
        first = yandex_api.YandexApi(max_requests=10, daily_limit=2)
        first.reserve_request()
        second = yandex_api.YandexApi(max_requests=10, daily_limit=2)
        second.reserve_request()
        third = yandex_api.YandexApi(max_requests=10, daily_limit=2)
        with self.assertRaisesRegex(yandex_api.ApiError, "24-hour budget"):
            third.reserve_request()
        self.assertEqual(third.requests, 0)

    def test_failed_request_is_counted_and_response_does_not_expose_key(self):
        api = yandex_api.YandexApi(max_requests=1)
        api.opener = Mock()
        api.opener.open.side_effect = urllib.error.HTTPError(
            "https://api.rasp.yandex-net.ru/", 403, "test-key-never-real", {},
            io.BytesIO(b'test-key-never-real'))
        with self.assertRaises(yandex_api.ApiError) as error:
            api.get("copyright")
        self.assertNotIn("test-key", str(error.exception))
        self.assertEqual(api.requests, 1)
        self.assertEqual(api.opener.open.call_count, 1)
        with self.assertRaisesRegex(yandex_api.ApiError, "run's request budget"):
            api.get("copyright")
        self.assertEqual(api.opener.open.call_count, 1)

    def test_partial_pagination_never_replaces_previous_bundle(self):
        cache = self.root / ".yandex-cache"
        cache.mkdir()
        bundle = cache / "network-bundle.json"
        bundle.write_text("previous complete bundle")
        plan = {"dateFrom": "2026-09-21", "dateUntilInclusive": "2026-09-21",
                "connections": [{"from": "s1", "to": "s2", "transport": "train"}],
                "budget": {"maximumNewRequestsPerFullDownload": 10,
                           "projectDataLimitIncludingProbe": 250}}
        api = Mock()
        def get(resource, **params):
            if params["offset"] > 0:
                raise yandex_api.ApiError("Simulated network failure")
            result = {"pagination": {"total": 2, "offset": 0}, "segments": [{}]}
            downloader.cached_path(params).write_text(json.dumps(result))
            return result
        api.get.side_effect = get
        with patch.object(downloader, "YandexApi", return_value=api):
            with self.assertRaisesRegex(yandex_api.ApiError, "Simulated"):
                downloader.download(plan)
        self.assertEqual(bundle.read_text(), "previous complete bundle")
        self.assertEqual(api.get.call_count, 2)


if __name__ == "__main__":
    unittest.main()
