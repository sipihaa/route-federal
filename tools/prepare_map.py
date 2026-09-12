"""Prepare a local geographic background from unmodified Natural Earth GeoJSON."""
import hashlib
import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = root / 'data/map/ne_50m_admin_0_countries.geojson'
features = json.loads(source.read_text())['features']
rings = []
for feature in features:
    geometry = feature['geometry']
    polygons = geometry['coordinates'] if geometry['type'] == 'MultiPolygon' else [geometry['coordinates']]
    for polygon in polygons:
        # Exterior only; this is an overview, not a navigational map.
        ring = polygon[0]
        points = []
        previous = ring[0][0]
        for lon, lat in ring:
            while lon - previous > 180:
                lon -= 360
            while lon - previous < -180:
                lon += 360
            points.append([lon, lat])
            previous = lon
        mean = sum(p[0] for p in points) / len(points)
        shift = 360 if mean < -80 else 0
        points = [[round(lon + shift, 4), round(lat, 4)] for lon, lat in points]
        if max(p[0] for p in points) < 15 or min(p[0] for p in points) > 195:
            continue
        if max(p[1] for p in points) < 35:
            continue
        rings.append(points)
output = root / 'app/src/main/assets/map_land.json'
output.write_text(json.dumps(rings, separators=(',', ':')))
metadata = {
    'name': 'Natural Earth 1:50m Admin 0 Countries',
    'source': 'https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_50m_admin_0_countries.geojson',
    'license': 'Public domain',
    'licenseUrl': 'https://www.naturalearthdata.com/about/terms-of-use/',
    'sourceSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
    'outputSha256': hashlib.sha256(output.read_bytes()).hexdigest(),
    'processing': 'Exterior geographic rings, longitude unwrap, overview area filtering, 4 decimal places',
    'limitation': 'Overview geography only; no street routing or exact transport geometry',
}
(root/'data/map/provenance.json').write_text(json.dumps(metadata,indent=2))
print(f'{len(rings)} polygons; {output.stat().st_size} bytes')
