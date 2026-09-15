"""Local Yandex Rasp requests. Credentials and temporary responses stay outside Git."""

import fcntl
import hashlib
import json
import os
from pathlib import Path
import ssl
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BASE_URL = "https://api.rasp.yandex-net.ru/v3.0/"
CACHE_SECONDS = 7 * 24 * 60 * 60


class ApiError(Exception):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ApiError("Unexpected API redirect; credentials were not forwarded.")


class YandexApi:
    def __init__(self, max_requests=30, daily_limit=250):
        if not 0 <= max_requests <= 500 or not 1 <= daily_limit <= 500:
            raise ValueError("Request budgets must fit the confirmed 500-request daily limit.")
        self.key = ""
        for line in (ROOT / ".env.local").read_text().splitlines():
            name, separator, value = line.partition("=")
            if separator and name.strip() == "YANDEX_RASP_API_KEY":
                self.key = value.strip().strip("\"'")
        if not self.key or self.key in {"ваш_ключ", "ВАШ_КЛЮЧ"}:
            raise ApiError("Fill YANDEX_RASP_API_KEY in .env.local first.")
        self.cache = ROOT / ".yandex-cache"
        self.cache.mkdir(mode=0o700, exist_ok=True)
        self.max_requests = max_requests
        self.daily_limit = daily_limit
        self.requests = 0
        self.cache_hits = 0
        system_certificates = Path("/etc/ssl/cert.pem")
        context = ssl.create_default_context(cafile=str(system_certificates) if system_certificates.exists() else None)
        self.opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=context))
        self.last_request = 0.0

    def reserve_request(self):
        if self.requests >= self.max_requests:
            raise ApiError("This run's request budget is exhausted.")
        with (self.cache / "requests.lock").open("a") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            path = self.cache / "requests.json"
            history = json.loads(path.read_text()) if path.exists() else []
            recent = [stamp for stamp in history if stamp > time.time() - 86400]
            # Conservative rolling 24 hours; the data task normally uses at most 250.
            # Requests made elsewhere with the same key are not visible here.
            if len(recent) >= self.daily_limit:
                raise ApiError(f"Local 24-hour budget is exhausted ({self.daily_limit} of 500).")
            history.append(time.time())
            temporary = path.with_suffix(".new")
            temporary.write_text(json.dumps(history))
            temporary.replace(path)
        self.requests += 1

    def get(self, resource, **params):
        if resource not in {"copyright", "stations_list", "schedule", "thread", "search"}:
            raise ApiError("Unsupported API resource.")
        if any("key" in name.lower() or name.lower() == "authorization" for name in params):
            raise ApiError("Credentials must only be sent in the Authorization header.")
        params = {"format": "json", "lang": "ru_RU", **params}
        query = urllib.parse.urlencode(sorted(params.items()))
        request_name = resource + "/?" + query
        digest = hashlib.sha256(request_name.encode()).hexdigest()
        path = self.cache / (digest + ".json")
        if path.exists() and time.time() - path.stat().st_mtime < CACHE_SECONDS:
            self.cache_hits += 1
            return json.loads(path.read_text())
        self.reserve_request()
        time.sleep(max(0, 1.0 - (time.monotonic() - self.last_request)))
        self.last_request = time.monotonic()
        request = urllib.request.Request(
            BASE_URL + request_name,
            headers={"Authorization": self.key, "User-Agent": "RouteFederal-Course/0.2"},
        )
        started = time.monotonic()
        try:
            with self.opener.open(request, timeout=45) as response:
                body = response.read(100 * 1024 * 1024 + 1)
                if len(body) > 100 * 1024 * 1024:
                    raise ApiError("API response exceeds the 100 MiB download limit.")
        except urllib.error.HTTPError as error:
            # Never print bodies or request headers: they may echo credentials.
            raise ApiError(f"API HTTP {error.code}; no automatic retry.") from None
        except (urllib.error.URLError, TimeoutError, OSError):
            raise ApiError("Network connection to Yandex failed; no automatic retry.") from None
        try:
            result = json.loads(body)
        except (ValueError, UnicodeError):
            raise ApiError("API did not return valid JSON.") from None
        if isinstance(result, dict) and "error" in result:
            raise ApiError("API returned an error object; response was not cached.")
        temporary = path.with_suffix(".new")
        temporary.write_bytes(body)
        temporary.replace(path)
        metadata = {
            "resource": resource, "parameters": params, "fetchedAtEpoch": time.time(),
            "bytes": len(body), "seconds": round(time.monotonic() - started, 3),
            "sha256": hashlib.sha256(body).hexdigest(),
        }
        path.with_suffix(".meta.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2))
        return result

    def remove_expired_responses(self):
        for path in self.cache.glob("*.meta.json"):
            if time.time() - path.stat().st_mtime >= CACHE_SECONDS:
                path.with_name(path.name.replace(".meta.json", ".json")).unlink(missing_ok=True)
                path.unlink()


if __name__ == "__main__":
    try:
        api = YandexApi(max_requests=1)
        api.remove_expired_responses()
        data = api.get("copyright")
        print(json.dumps({"access": "ok", "requests": api.requests, "responseFields": list(data)}, ensure_ascii=False))
    except ApiError as error:
        print(str(error))
        raise SystemExit(1)
