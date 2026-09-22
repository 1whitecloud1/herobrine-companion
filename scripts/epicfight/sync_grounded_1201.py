"""Sync only the reviewed combat resources and shared verifier to Forge 1.20.1."""
import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT.parent/'herobrine companion'
RES = Path('src/main/resources')
WORK = Path('build/epicfight-mediapipe')
timing = json.loads((ROOT/RES/'assets/herobrine_companion/epicfight/poem_mediapipe_timing.json').read_text(encoding='utf-8'))
assert timing['combat_edit'] == 'mediapipe_recapture_05' and len(timing['segments']) == 8
active = {s['name']+'.json' for s in timing['segments']}
paths = []
for actor in ('player', 'hero'):
    folder = Path(f'assets/herobrine_companion/animmodels/animations/{actor}/poem_mediapipe')
    paths.extend(p.relative_to(ROOT/RES) for p in (ROOT/RES/folder).rglob('*') if p.is_file())
    for base in (TARGET/RES/folder, ROOT/'build/resources/main'/folder, TARGET/'build/resources/main'/folder):
        for obsolete in base.glob('combo_??.json'):
            if obsolete.name not in active:
                obsolete.unlink()
paths.extend([Path('assets/herobrine_companion/epicfight/poem_mediapipe_timing.json'), Path('META-INF/licenses/herobrine_scythe_mediapipe.txt')])
for rel in paths:
    dest = TARGET/RES/rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(ROOT/RES/rel, dest)
for name in ('MediaPipeClipCheck.java',):
    shutil.copy2(ROOT/'scripts/epicfight'/name, TARGET/'scripts/epicfight'/name)
for name in ('biped.json', 'conversion_report.json', 'validation_report.json'):
    shutil.copy2(ROOT/WORK/name, TARGET/WORK/name)
baseline = json.loads((TARGET/WORK/'v6_baseline_sha256.json').read_text(encoding='utf-8'))
assert all(hashlib.sha256((TARGET/RES/rel).read_bytes()).hexdigest() == digest for rel,digest in baseline.items())
assert len(paths) == 30
print('GROUNDED_SYNC_1201', len(paths), 'identical new resources;', len(baseline), 'V6 files preserved')
