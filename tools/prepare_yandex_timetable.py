"""Validate the complete cached network and produce a temporary Android JSON.

No network calls. Inconsistent records abort conversion instead of guessing times.
"""
from collections import Counter, defaultdict
from datetime import datetime, date, time as day_time, timedelta, timezone
import hashlib
import json
from pathlib import Path
import time
from download_yandex_network import ROOT, planned_queries


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_cache_catalog():
    for meta_path in (ROOT / '.yandex-cache').glob('*.meta.json'):
        meta = json.loads(meta_path.read_text())
        if meta['resource'] != 'stations_list':
            continue
        path = meta_path.with_name(meta_path.name.replace('.meta.json', '.json'))
        body = path.read_bytes()
        require(hashlib.sha256(body).hexdigest() == meta['sha256'], 'Station catalog checksum mismatch')
        result = {}
        for country in json.loads(body)['countries']:
            if country.get('codes', {}).get('yandex_code') != 'l225':
                continue
            for region in country['regions']:
                for city in region['settlements']:
                    for station in city['stations']:
                        code = station.get('codes', {}).get('yandex_code')
                        result[code] = {**station, 'region': region['title'],
                                        'cityName': city['title'], 'cityCode': city.get('codes', {}).get('yandex_code')}
        return result, meta['fetchedAtEpoch'] + 7 * 86400
    raise ValueError('Cached station catalog is missing')


def moment(value):
    result = datetime.fromisoformat(value)
    require(result.utcoffset() == timedelta(hours=3), 'Unexpected time zone in the selected four regions')
    require(result.second == 0 and result.microsecond == 0, 'Cannot discard seconds')
    return result


def normalize(bundle, catalog):
    plan = bundle['network']
    require(bundle.get('queryCoverageComplete') is True, 'Source bundle is incomplete')
    require(bundle['expiresAtEpoch'] > time.time(), 'Source cache has expired')
    expected = {json.dumps(p, sort_keys=True) for p in planned_queries(plan)}
    seen_first = set()
    pages_by_query = defaultdict(list)
    services = {}
    stations = {}
    rows_by_mode = Counter()
    coverage = []
    raw_bytes = 0
    rows = 0
    duplicates = 0
    adjacent_date_rows = 0
    outside_period_rows = 0
    for page in bundle['pages']:
        params = page['parameters']
        path = ROOT / '.yandex-cache' / page['cacheFile']
        require(path.parent == ROOT / '.yandex-cache', 'Invalid cache path')
        body = path.read_bytes()
        require(hashlib.sha256(body).hexdigest() == page['sha256'], 'Response checksum mismatch')
        raw_bytes += len(body)
        data = json.loads(body)
        base = {**params, 'offset': 0}
        query_key = json.dumps(base, sort_keys=True)
        require(query_key in expected, 'Unexpected query in the bundle')
        if params['offset'] == 0:
            require(query_key not in seen_first, 'Duplicate first page')
            seen_first.add(query_key)
        require(not data.get('interval_segments'), 'Interval services have no exact departure; conversion stopped')
        segments = data.get('segments', [])
        pagination = data['pagination']
        require(pagination['offset'] == params['offset'], 'Page offset mismatch')
        pages_by_query[query_key].append((params['offset'], len(segments), pagination['total']))
        coverage.append({**params, 'rows': len(segments), 'total': pagination['total']})
        for row in segments:
            rows += 1
            require(row.get('has_transfers') is False, 'Provider transfer route is not a direct service')
            thread = row['thread']
            mode = thread['transport_type']
            require(mode == params['transport_types'] and mode in {'bus', 'train', 'suburban'}, 'Unexpected transport')
            departure, arrival = moment(row['departure']), moment(row['arrival'])
            require(arrival > departure, 'Nonpositive journey duration')
            require((arrival - departure).total_seconds() == row['duration'], 'Duration mismatch')
            query_date = date.fromisoformat(params['date'])
            require(query_date <= departure.date() <= query_date + timedelta(days=1),
                    'Unexpected departure day relative to API query')
            if departure.date() != query_date:
                adjacent_date_rows += 1
            if not plan['dateFrom'] <= departure.date().isoformat() <= plan['dateUntilInclusive']:
                outside_period_rows += 1
                continue
            original_date = date.fromisoformat(row['start_date'])
            require(original_date <= departure.date(), 'Trip origin date is after boarding')
            start, end = row['from']['code'], row['to']['code']
            require(start != end, 'Same origin and destination station')
            for code, endpoint in [(start, 'from'), (end, 'to')]:
                require(code in catalog, f'Station {code} is missing from the catalog')
                item = catalog[code]
                query_point = params[endpoint]
                require(code == query_point if query_point.startswith('s') else item['cityCode'] == query_point,
                        f'Station {code} does not match query point {query_point}')
                latitude, longitude = float(item['latitude']), float(item['longitude'])
                require(50 < latitude < 60 and 30 < longitude < 50, f'Station coordinates outside selected area: {code}')
                stations[code] = {'id': code, 'name': item['title'], 'regionCode': item['region'],
                                  'cityId': item['cityCode'], 'cityName': item.get('cityName', item['title']),
                                  'latitude': latitude, 'longitude': longitude, 'timeZone': 'Europe/Moscow',
                                  'minTransferMinutes': None}
            uid = thread['uid']
            require(bool(uid), 'Missing trip UID')
            key = (mode, uid, row['start_date'])
            carrier = thread.get('carrier') or {}
            route_key = '|'.join([mode, str(carrier.get('code', '')), thread.get('number') or '', thread['title']])
            service = services.setdefault(key, {'events': {}, 'edges': set(), 'route': route_key,
                                                 'name': ' '.join(x for x in [thread.get('number'), thread['title']] if x)})
            require(service['route'] == route_key, 'Conflicting identity of the same trip')
            edge = (start, end, departure, arrival)
            if edge in service['edges']:
                duplicates += 1
            service['edges'].add(edge)
            for code, field, value in [(start, 'departure', departure), (end, 'arrival', arrival)]:
                event = service['events'].setdefault(code, {})
                require(field not in event or event[field] == value,
                        f'Conflicting {field}: {uid}, {row["start_date"]}, {code}')
                event[field] = value
            rows_by_mode[mode] += 1
    require(seen_first == expected, 'Missing planned dates or directions')
    for pages in pages_by_query.values():
        offset, total = 0, pages[0][2]
        for actual, size, page_total in sorted(pages):
            require(actual == offset and page_total == total, 'Gap or duplicate in pagination')
            offset += size
        require(offset == total, 'Incomplete query result')
    trips = []
    for (mode, uid, original_date), service in sorted(services.items()):
        ordered = sorted(service['events'].items(), key=lambda item: min(item[1].values()))
        require('departure' in ordered[0][1] and 'arrival' in ordered[-1][1], 'Incomplete outer endpoints')
        first = ordered[0][1]['departure']
        service_date = first.date()
        midnight = datetime.combine(service_date, day_time(), tzinfo=first.tzinfo)
        calls = []
        previous = None
        for code, event in ordered:
            for field in ('arrival', 'departure'):
                value = event.get(field)
                if value is not None:
                    require(previous is None or value >= previous, f'Conflicting stop order: {uid}, {original_date}')
                    previous = value
            calls.append({'stationId': code,
                          'arrivalMinutes': int((event['arrival'] - midnight).total_seconds() / 60) if 'arrival' in event else None,
                          'departureMinutes': int((event['departure'] - midnight).total_seconds() / 60) if 'departure' in event else None})
        # Every consecutive known stop must belong to an actually observed section.
        for left, right in zip(ordered, ordered[1:]):
            left_time, right_time = max(left[1].values()), min(right[1].values())
            require(any(dep <= left_time <= right_time <= arr for _, _, dep, arr in service['edges']),
                    f'Disconnected sections of trip: {uid}, {original_date}')
        trips.append({'id': f'yandex:{mode}:{uid}:{original_date}',
                      'routeId': hashlib.sha256(service['route'].encode()).hexdigest()[:24],
                      'routeName': service['name'], 'transport': mode.upper(), 'daysOfWeek': [],
                      'seasonStart': '01-01', 'seasonEnd': '12-31', 'timeZone': 'Europe/Moscow',
                      'includedDates': [service_date.isoformat()], 'excludedDates': [], 'calls': calls,
                      'sourceTripUid': uid, 'sourceStartDate': original_date})
    require(trips, 'No usable dated trips')
    report = {'date': date.today().isoformat(), 'pages': len(bundle['pages']), 'plannedQueries': len(expected),
              'sourceRows': rows, 'sourceRowsByMode': dict(rows_by_mode), 'duplicateRows': duplicates,
              'adjacentDateRows': adjacent_date_rows, 'outsidePeriodRows': outside_period_rows,
              'trips': len(trips), 'tripsByMode': dict(Counter(t['transport'] for t in trips)),
              'stations': len(stations), 'sourceBytes': raw_bytes,
              'tripsWithMultipleSections': sum(len(t['calls']) > 2 for t in trips),
              'checks': ['SHA-256', 'all query pages', 'all dates and directions', 'ground direct services',
                         'station IDs and coordinates', 'exact dates and UTC offsets', 'duration',
                         'trip identity', 'consistent stop times', 'connected observed sections'],
              'coverage': coverage, 'emptyQueries': [q for q in coverage if q['total'] == 0]}
    return stations, trips, report


def main():
    cache = ROOT / '.yandex-cache'
    bundle = json.loads((cache / 'network-bundle.json').read_text())
    catalog, catalog_expiry = read_cache_catalog()
    stations, trips, report = normalize(bundle, catalog)
    expiry = min(catalog_expiry, bundle['expiresAtEpoch'])
    require(expiry > time.time(), 'Station catalog has expired')
    source = {'name': 'Яндекс Расписания', 'url': 'https://rasp.yandex.ru/',
              'snapshotDate': datetime.fromtimestamp(bundle['createdAtEpoch'], timezone.utc).date().isoformat(), 'validFrom': bundle['network']['dateFrom'],
              'validUntil': bundle['network']['dateUntilInclusive'],
              'expiresAt': datetime.fromtimestamp(expiry, timezone.utc).isoformat(),
              'coverageNote': 'Выбранные связи Москвы, Московской, Владимирской и Нижегородской областей. '
                              'Только загруженные станции и рейсы; полное покрытие регионов не заявляется. '
                              'Наличие билетов не проверяется.'}
    output = {'schemaVersion': 1, 'source': source, 'stops': sorted(stations.values(), key=lambda s: s['name']), 'trips': trips}
    body = json.dumps(output, ensure_ascii=False, separators=(',', ':')).encode()
    report.update(normalizedBytes=len(body), normalizedSha256=hashlib.sha256(body).hexdigest(),
                  expiresAt=source['expiresAt'], lastDownloadNewRequests=bundle['newRequests'], lastDownloadCacheHits=bundle['cacheHits'])
    target = cache / 'timetable.json'
    temp = target.with_suffix('.new')
    temp.write_bytes(body)
    temp.replace(target)
    audit = ROOT / 'docs/validation/yandex-network-audit-2026-09-15.json'
    audit.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps({key: value for key, value in report.items() if key not in {'coverage', 'emptyQueries'}}, ensure_ascii=False, indent=2))
    print('Empty queries:', len(report['emptyQueries']))


if __name__ == '__main__':
    main()
