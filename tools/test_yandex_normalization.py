"""Offline regression cases for provider dates, pagination and trip merging."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch
import prepare_yandex_timetable as prepare


class NormalizationTest(unittest.TestCase):
    def setUp(self):
        self.folder = tempfile.TemporaryDirectory()
        self.root = Path(self.folder.name)
        (self.root / '.yandex-cache').mkdir()
        self.catalog = {s: {'title': s, 'region': 'test', 'latitude': 55, 'longitude': 40, 'cityCode': 'c' + s} for s in ('s1', 's2', 's3')}

    def tearDown(self):
        self.folder.cleanup()

    def row(self, origin, destination, dep, arr):
        return {'from': {'code': origin}, 'to': {'code': destination}, 'departure': dep, 'arrival': arr,
                'duration': (datetime.fromisoformat(arr) - datetime.fromisoformat(dep)).total_seconds(),
                'start_date': '2026-09-21', 'has_transfers': False,
                'thread': {'transport_type': 'train', 'uid': 'same-vehicle', 'number': '123', 'title': 'A — C'}}

    def bundle(self, rows):
        connections = []
        pages = []
        for i, row in enumerate(rows):
            origin, destination = row['from']['code'], row['to']['code']
            connections.append({'from': origin, 'to': destination, 'transport': 'train'})
            for reverse in (False, True):
                params = {'from': destination if reverse else origin, 'to': origin if reverse else destination,
                          'transport_types': 'train', 'transfers': 'false', 'date': '2026-09-21', 'limit': 100, 'offset': 0}
                body = json.dumps({'pagination': {'offset': 0, 'total': 0 if reverse else 1}, 'segments': [] if reverse else [row]}).encode()
                name = f'{i}-{reverse}.json'
                (self.root / '.yandex-cache' / name).write_bytes(body)
                pages.append({'parameters': params, 'cacheFile': name, 'sha256': hashlib.sha256(body).hexdigest()})
        return {'queryCoverageComplete': True, 'expiresAtEpoch': time.time() + 1000,
                'network': {'dateFrom': '2026-09-21', 'dateUntilInclusive': '2026-09-21', 'connections': connections}, 'pages': pages}

    def normalize(self, bundle):
        with patch.object(prepare, 'ROOT', self.root):
            return prepare.normalize(bundle, self.catalog)

    def test_merges_known_stops_without_inventing_arrival(self):
        b = self.bundle([self.row('s1', 's3', '2026-09-21T08:00:00+03:00', '2026-09-21T10:00:00+03:00'),
                         self.row('s2', 's3', '2026-09-21T09:00:00+03:00', '2026-09-21T10:00:00+03:00')])
        _, trips, _ = self.normalize(b)
        self.assertEqual(len(trips), 1)
        self.assertEqual([c['stationId'] for c in trips[0]['calls']], ['s1', 's2', 's3'])
        self.assertIsNone(trips[0]['calls'][1]['arrivalMinutes'])
        self.assertEqual(trips[0]['calls'][1]['departureMinutes'], 540)

    def test_rejects_conflicting_times_for_same_vehicle(self):
        b = self.bundle([self.row('s1', 's3', '2026-09-21T08:00:00+03:00', '2026-09-21T10:00:00+03:00'),
                         self.row('s2', 's3', '2026-09-21T09:00:00+03:00', '2026-09-21T10:10:00+03:00')])
        with self.assertRaisesRegex(ValueError, 'Conflicting arrival'):
            self.normalize(b)

    def test_rejects_missing_query_even_when_bundle_claims_complete(self):
        b = self.bundle([self.row('s1', 's3', '2026-09-21T08:00:00+03:00', '2026-09-21T10:00:00+03:00')])
        b['pages'].pop()
        with self.assertRaisesRegex(ValueError, 'Missing planned'):
            self.normalize(b)

    def test_keeps_actual_night_date_from_previous_query(self):
        b = self.bundle([self.row('s1', 's3', '2026-09-21T01:00:00+03:00', '2026-09-21T03:00:00+03:00')])
        # The service returned by a previous-day query retains its actual date.
        old_pages = []
        for page in b['pages']:
            old_pages.append({**page, 'parameters': {**page['parameters'], 'date': '2026-09-20'}})
        b['pages'] += old_pages
        b['network']['boundaryLookbackDays'] = 1
        _, trips, report = self.normalize(b)
        self.assertEqual(trips[0]['includedDates'], ['2026-09-21'])
        self.assertEqual(trips[0]['calls'][0]['departureMinutes'], 60)
        self.assertEqual(report['adjacentDateRows'], 1)
        self.assertEqual(report['duplicateRows'], 1)


if __name__ == '__main__':
    unittest.main()
