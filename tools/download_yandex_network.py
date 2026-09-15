"""Preview the selected network, or explicitly download its raw API responses.

The output is a temporary source bundle, not yet the Android timetable format.
"""

import argparse
from datetime import date, timedelta
import hashlib
import json
import time
import urllib.parse
from yandex_api import ApiError, CACHE_SECONDS, ROOT, YandexApi


def planned_queries(plan):
    first = date.fromisoformat(plan["dateFrom"]) - timedelta(days=plan.get("boundaryLookbackDays", 0))
    last = date.fromisoformat(plan["dateUntilInclusive"])
    for connection in plan["connections"]:
        for reverse in (False, True):
            origin, destination = connection["from"], connection["to"]
            if reverse:
                origin, destination = destination, origin
            for day in range((last - first).days + 1):
                yield {"from": origin, "to": destination,
                       "transport_types": connection["transport"], "transfers": "false",
                       "date": (first + timedelta(days=day)).isoformat(), "limit": 100, "offset": 0}


def cached_path(params):
    query = urllib.parse.urlencode(sorted({"format": "json", "lang": "ru_RU", **params}.items()))
    name = hashlib.sha256(("search/?" + query).encode()).hexdigest()
    return ROOT / ".yandex-cache" / (name + ".json")


def preview(plan):
    queries = list(planned_queries(plan))
    cached = sum(path.exists() and time.time() - path.stat().st_mtime < CACHE_SECONDS
                 for path in map(cached_path, queries))
    return {"dates": [plan["dateFrom"], plan["dateUntilInclusive"]],
            "connections": len(plan["connections"]), "baseRequests": len(queries),
            "cachedFirstPages": cached, "newFirstPages": len(queries) - cached,
            "dailyDataLimit": plan["budget"]["projectDataLimitIncludingProbe"],
            "note": "Extra pages may be needed. This preview performs no network requests."}


def download(plan):
    budget = plan["budget"]
    api = YandexApi(max_requests=budget["maximumNewRequestsPerFullDownload"],
                    daily_limit=budget["projectDataLimitIncludingProbe"])
    api.remove_expired_responses()
    pages = []
    earliest_expiry = time.time() + CACHE_SECONDS
    for base in planned_queries(plan):
        params = dict(base)
        total = None
        while True:
            data = api.get("search", **params)
            pagination = data["pagination"]
            actual_total = int(pagination["total"])
            if actual_total < 0 or int(pagination["offset"]) != params["offset"]:
                raise ApiError("Invalid API pagination; previous bundle is preserved.")
            if total is not None and actual_total != total:
                raise ApiError("Pagination changed during download; previous bundle is preserved.")
            total = actual_total
            count = len(data.get("segments", [])) + len(data.get("interval_segments", []))
            if count == 0 and params["offset"] < total:
                raise ApiError("Incomplete API page; previous bundle is preserved.")
            if count > 100 or params["offset"] + count > total:
                raise ApiError("Unexpected API page size; previous bundle is preserved.")
            path = cached_path(params)
            earliest_expiry = min(earliest_expiry, path.stat().st_mtime + CACHE_SECONDS)
            pages.append({"parameters": dict(params), "cacheFile": path.name,
                          "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
            if params["offset"] + count >= total:
                break
            params["offset"] += count
    result = {"network": plan, "queryCoverageComplete": True, "pages": pages,
              "createdAtEpoch": time.time(), "expiresAtEpoch": earliest_expiry,
              "newRequests": api.requests, "cacheHits": api.cache_hits,
              "note": "Responses cover selected queries only, not every service in the regions."}
    path = ROOT / ".yandex-cache" / "network-bundle.json"
    temporary = path.with_suffix(".new")
    temporary.write_text(json.dumps(result, ensure_ascii=False, indent=2))
    temporary.replace(path)
    return {"pages": len(pages), "newRequests": api.requests,
            "cacheHits": api.cache_hits, "bundle": str(path)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--download", action="store_true", help="Perform the bounded network download")
    args = parser.parse_args()
    plan = json.loads((ROOT / "data/yandex/network-plan.json").read_text())
    try:
        print(json.dumps(download(plan) if args.download else preview(plan), ensure_ascii=False, indent=2))
    except ApiError as error:
        print(str(error))
        raise SystemExit(1)
