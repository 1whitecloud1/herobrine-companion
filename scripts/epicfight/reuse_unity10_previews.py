"""Reuse rendered frames only after proving the delivered matrix prefix matches."""
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OLD = ROOT / 'output/Herobrine_Scythe_UnityModes_09_EpicFight'
NEW = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
load = lambda p: json.loads(p.read_text('utf-8'))
folder = 'resources/assets/herobrine_companion/animmodels/animations/player/poem_unity09'
report = []
for path in (NEW / folder).rglob('*.json'):
    rel = path.relative_to(NEW / folder)
    if 'data' in rel.parts or (rel.parts[0] != 'extra' and path.stem not in ('full', 'dash', 'air')):
        continue
    if rel.as_posix() == 'extra/heavy_ground.json':
        continue
    before = load(OLD / folder / rel)['animation']
    after = load(path)['animation']
    assert len(before) == len(after)
    for a, b in zip(before, after):
        assert a['name'] == b['name']
        assert b['time'] == a['time'][:len(b['time'])], (rel, b['name'], 'time')
        assert b['transform'] == a['transform'][:len(b['transform'])], (rel, b['name'], 'matrix')
    count = round(after[0]['time'][-1] * 60) + 1
    sequence = Path('preview/sequence_60') / rel.with_suffix('')
    (NEW / sequence).mkdir(parents=True, exist_ok=True)
    for frame in range(count):
        source, target = OLD / sequence / f'{frame:04d}.png', NEW / sequence / f'{frame:04d}.png'
        assert source.exists(), source
        if not target.exists():
            os.link(source, target)
    report.append(dict(clip=rel.as_posix(), matrix_prefix_identical=True, frames=count,
                       new_json_sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
(NEW / 'reports/preview_reuse_validation.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
print('VERIFIED_PREVIEW_PREFIXES', len(report), 'FRAMES', sum(r['frames'] for r in report))
