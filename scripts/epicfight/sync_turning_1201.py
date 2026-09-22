"""Copy only the final turning resources to the existing Forge port."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT.parent/'herobrine companion'
RES = Path('src/main/resources')
WORK = Path('build/epicfight-mediapipe')
load = lambda p: json.loads(p.read_text(encoding='utf-8'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

timing = load(ROOT/RES/'assets/herobrine_companion/epicfight/poem_mediapipe_timing.json')
assert timing['combat_edit'] == 'mediapipe_turning_07'
assert len(timing['segments']) == 8
for root in (ROOT, TARGET):
    for rel, digest in load(root/WORK/'v6_baseline_sha256.json').items():
        path = root/rel if rel.replace('\\', '/').startswith('src/') else root/RES/rel
        assert sha(path) == digest, ('V6 changed', path)
paths = []
for actor in ('hero', 'player'):
    folder = Path(f'assets/herobrine_companion/animmodels/animations/{actor}/poem_mediapipe')
    paths.extend(p.relative_to(ROOT/RES) for p in (ROOT/RES/folder).rglob('*') if p.is_file())
paths.extend([Path('assets/herobrine_companion/epicfight/poem_mediapipe_timing.json'),
              Path('META-INF/licenses/herobrine_scythe_mediapipe.txt')])
assert len(paths) == 30
for rel in paths:
    target = TARGET/RES/rel
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT/RES/rel, target)
    assert sha(ROOT/RES/rel) == sha(target)
for name in ('conversion_report.json', 'validation_report.json'):
    shutil.copy2(ROOT/WORK/name, TARGET/WORK/name)
print('TURN07_FORGE_SYNC: 30 identical resources; both V6 baselines retained')
