"""Build offline Russian regional polygons from Natural Earth 10m Admin 1 (public domain).
Download the source from SOURCE_URL to SOURCE first; no timetable API is used.
"""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'data/map/ne_10m_admin_1_states_provinces.geojson'
SOURCE_URL = 'https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_1_states_provinces.geojson'


def simplify(points, tolerance=0.006):
    if len(points) < 3:
        return points
    ax, ay = points[0]
    bx, by = points[-1]
    dx, dy = bx - ax, by - ay
    length = dx * dx + dy * dy
    maximum, index = 0, 0
    for i, (x, y) in enumerate(points[1:-1], 1):
        t = max(0, min(1, ((x-ax)*dx + (y-ay)*dy) / length)) if length else 0
        distance = (x-ax-t*dx)**2 + (y-ay-t*dy)**2
        if distance > maximum:
            maximum, index = distance, i
    if maximum > tolerance*tolerance:
        return simplify(points[:index+1], tolerance)[:-1] + simplify(points[index:], tolerance)
    return [points[0], points[-1]]


def main():
    regions = []
    for feature in json.loads(SOURCE.read_text())['features']:
        p = feature['properties']
        if p['adm0_a3'] != 'RUS':
            continue
        geometry = feature['geometry']
        polygons = geometry['coordinates'] if geometry['type'] == 'MultiPolygon' else [geometry['coordinates']]
        rings = []
        for polygon in polygons:
            for ring in polygon:
                points = []
                previous = ring[0][0]
                for lon, lat in ring:
                    while lon - previous > 180: lon -= 360
                    while lon - previous < -180: lon += 360
                    points.append([lon, lat])
                    previous = lon
                shift = 360 if sum(x for x,y in points)/len(points) < -80 else 0
                points = [[round(x+shift, 4), round(y, 4)] for x,y in simplify(points)]
                if len(points) >= 4:
                    rings.append(points)
        lon = p['longitude']
        regions.append({'name': p.get('name_ru') or p['name'] or 'Регион',
                        'longitude': lon+360 if lon < -80 else lon, 'latitude': p['latitude'], 'rings': rings})
    output = ROOT/'app/src/main/assets/map_regions.json'
    output.write_text(json.dumps(regions, ensure_ascii=False, separators=(',', ':')))
    metadata = {'name': 'Natural Earth 10m Admin 1 States and Provinces', 'source': SOURCE_URL,
                'license': 'Public domain', 'licenseUrl': 'https://www.naturalearthdata.com/about/terms-of-use/',
                'sourceSha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
                'outputSha256': hashlib.sha256(output.read_bytes()).hexdigest(), 'regions': len(regions),
                'processing': 'RUS features as supplied, all rings, longitude unwrap, RDP tolerance 0.006 degrees, 4 decimals',
                'downloadDate': '2026-09-16', 'limitations': 'Source administrative boundaries, not street or transport geometry'}
    (ROOT/'data/map/regions-provenance.json').write_text(json.dumps(metadata, ensure_ascii=False, indent=2)+'\n')
    print(len(regions), output.stat().st_size)

if __name__ == '__main__': main()
