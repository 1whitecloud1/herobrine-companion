"""Check the actual NeoForge JAR against the finished combat edit and V6 baseline."""
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT/'build/epicfight-mediapipe'
RES = ROOT/'src/main/resources'
load = lambda p:json.loads(p.read_text(encoding='utf-8'))
sha = lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
timing = load(RES/'assets/herobrine_companion/epicfight/poem_mediapipe_timing.json')
registry = load(WORK/'registry_validation.json')
assert timing['combat_edit'] == 'mediapipe_recapture_05'
assert registry['registered_clips'] == 47 and registry['hero_combo_slots'] == 8
assert registry['player_modes'] == {'0':'MediaPipe', '1':'V6', '2':'MediaPipe', '3':'MediaPipe'}
assert len(load(WORK/'engine_validation.json')) == 24 and len(load(WORK/'movement_validation.json')) == 20
geometry = load(WORK/'validation_report.json')
assert geometry['ordinary_attacks_always_grounded'] and geometry['maximum_planted_foot_drift_blocks'] < .005
properties = dict(line.split('=',1) for line in (ROOT/'gradle.properties').read_text(encoding='utf-8').splitlines() if '=' in line and not line.startswith('#'))
jar = ROOT/'build/libs'/f"{properties['mod_name']}-{properties['mod_version']}-1.21.1-neoforge.jar"
paths = []
for actor in ('player', 'hero'):
    paths += [p for p in (RES/f'assets/herobrine_companion/animmodels/animations/{actor}/poem_mediapipe').rglob('*') if p.is_file()]
paths += [RES/'assets/herobrine_companion/epicfight/poem_mediapipe_timing.json', RES/'META-INF/licenses/herobrine_scythe_mediapipe.txt']
assert len(paths) == 30
v6 = load(WORK/'v6_baseline_sha256.json')
classes = ['MediaPipeScytheAnimations', 'MediaPipeScytheAttackAnimation', 'PoemScytheStyle', 'PoemScythePlayerAnimations', 'HeroScytheComboBehaviors']
with zipfile.ZipFile(jar) as packed:
    assert packed.testzip() is None
    for path in paths:
        assert packed.read(path.relative_to(RES).as_posix()) == path.read_bytes()
    for rel,digest in v6.items():
        path = ROOT/rel
        assert sha(path) == digest
        assert hashlib.sha256(packed.read(path.relative_to(RES).as_posix())).hexdigest() == digest
    for actor in ('player', 'hero'):
        names = [n for n in packed.namelist() if n.startswith(f'assets/herobrine_companion/animmodels/animations/{actor}/poem_mediapipe/combo_')]
        assert len(names) == 8, names
    for name in classes:
        data = packed.read('com/whitecloud233/herobrine_companion/compat/epicfight/'+name+'.class')
        assert data[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(data[6:8], 'big') == 65
report = {'jar':jar.relative_to(ROOT).as_posix(), 'sha256':sha(jar), 'bytes':jar.stat().st_size,
          'clip_counts':{'player/poem_mediapipe/':13, 'hero/poem_mediapipe/':11, 'player/poem_v6/':23},
          'preserved_v6_files':len(v6), 'zip_integrity':True, 'compiled_integration_classes':True,
          'combat_edit':'mediapipe_recapture_05', 'live_gameplay_tested':False}
(WORK/'jar_validation.json').write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
print('GROUNDED_JAR_OK', json.dumps(report))
