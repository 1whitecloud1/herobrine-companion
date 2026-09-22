import hashlib
import json
from pathlib import Path

V4_TARGET = Path(r'E:\java\herobrine companion\src\main\resources\assets\herobrine_companion\animmodels\animations\hero')
V4_PIPE = Path(r'E:\java\herobrine_companion\src\main\resources\assets\herobrine_companion\animmodels\animations\hero')
V4_BUILD = Path(r'E:\java\herobrine_companion\build\epicfight-v4')

for name in ['hero_scythe_combo_v4'] + [f'hero_scythe_combo_v4_{i}' for i in range(1, 5)]:
    t = V4_TARGET / f'{name}.json'
    p = V4_PIPE / f'{name}.json'
    def h(x):
        return hashlib.sha256(x.read_bytes()).hexdigest()[:12] if x.exists() else 'MISSING'
    print(f'{name:28s} target {t.stat().st_size if t.exists() else -1:>9} {h(t)}  pipe {p.stat().st_size if p.exists() else -1:>9} {h(p)}')

seg = V4_TARGET / 'hero_scythe_combo_v4_1.json'
data = json.loads(seg.read_text(encoding='utf-8'))
anim = data['animation']
print('\nsegment_1 joints:', len(anim), [d['name'] for d in anim])
for d in anim:
    print(f"   {d['name']:12s} keys {len(d['transform'])}  first16 {[round(v, 3) for v in d['transform'][0]]}")

print('\nbuild dir V4 clips:')
for f in sorted(V4_BUILD.glob('*.json')):
    print(f'   {f.name:34s} {f.stat().st_size}')
