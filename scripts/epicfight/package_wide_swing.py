"""Audit and package both compiled mods with the validated animation artifacts."""
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'output/Herobrine_Scythe_Wide_06_EpicFight'
SOURCE = ROOT/'output/Herobrine_Scythe_Wide_06'
WORK = Path('build/epicfight-mediapipe')
RES = Path('src/main/resources')
load = lambda p: json.loads(p.read_text(encoding='utf-8'))
sha = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()

OUT.mkdir(parents=True, exist_ok=True)
geometry = load(ROOT/WORK/'validation_report.json')
assert geometry['v6_resources_unchanged'] and geometry['ordinary_attacks_always_grounded']
assert geometry['minimum_mesh_height_blocks'] >= 0 and geometry['minimum_weapon_height_blocks'] >= 0
assert max(geometry['right_grip_max_error_blocks'], geometry['left_grip_max_error_blocks']) < .012
assert geometry['maximum_planted_foot_drift_blocks'] < .005
assert all(m['shaft_direction_span_degrees'] > 155 and 185 < m['sweep_path_degrees'] < 330
           and m['contact_sweep_degrees'] > 135 for m in geometry['attack_metrics'])
report = []
for root, version, loader, package, major, filename in [
    (ROOT, '1.21.1', 'NeoForge', 'com/whitecloud233/herobrine_companion', 65,
     'Herobrine Companion-0.38-1.21.1-neoforge.jar'),
    (ROOT.parent/'herobrine companion', '1.20.1', 'Forge', 'com/whitecloud233/modid/herobrine_companion', 61,
     'Herobrine Companion-1.20.1-forge-0.38.jar'),
]:
    registry = load(root/WORK/'registry_validation.json')
    assert registry['player_modes'] == {'0': 'MediaPipe', '1': 'V6', '2': 'MediaPipe', '3': 'V6'}
    assert registry['full_body_attack_policy_checked'] and registry['production_hit_phases_checked']
    assert len(load(root/WORK/'engine_validation.json')) == 24
    assert len(load(root/WORK/'movement_validation.json')) == 20
    jar = root/'build/libs'/filename
    baseline = load(root/WORK/'v6_baseline_sha256.json')
    paths = []
    for actor in ('hero', 'player'):
        folder = Path(f'assets/herobrine_companion/animmodels/animations/{actor}/poem_mediapipe')
        paths.extend(p.relative_to(root/RES) for p in (root/RES/folder).rglob('*') if p.is_file())
    paths.extend([Path('assets/herobrine_companion/epicfight/poem_mediapipe_timing.json'),
                  Path('META-INF/licenses/herobrine_scythe_mediapipe.txt')])
    with zipfile.ZipFile(jar) as packed:
        assert packed.testzip() is None
        for rel in paths:
            assert packed.read(rel.as_posix()) == (root/RES/rel).read_bytes() == (ROOT/RES/rel).read_bytes()
        for rel, digest in baseline.items():
            path = root/rel if rel.replace('\\', '/').startswith('src/') else root/RES/rel
            assert sha(path) == digest
            assert hashlib.sha256(packed.read(path.relative_to(root/RES).as_posix())).hexdigest() == digest
        for name in ('PoemScythePlayerAnimations', 'MediaPipeScytheAnimations', 'MediaPipeScytheAttackAnimation'):
            data = packed.read(package+'/compat/epicfight/'+name+'.class')
            assert data[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(data[6:8], 'big') == major
    target = OUT/f'Herobrine_Companion-0.38-{version}-{loader}-Wide06.jar'
    shutil.copy2(jar, target)
    entry = {'version': version, 'loader': loader, 'jar': target.name, 'sha256': sha(target),
             'bytes': target.stat().st_size, 'preserved_v6_files': len(baseline),
             'player_modes': registry['player_modes'], 'live_gameplay_tested': False}
    report.append(entry)
    reports = OUT/'reports'/version
    reports.mkdir(parents=True, exist_ok=True)
    for name in ('engine_validation.json', 'movement_validation.json', 'registry_validation.json', 'v6_baseline_sha256.json'):
        shutil.copy2(root/WORK/name, reports/name)
    for name in ('engine_validation.json', 'reach_validation.json'):
        shutil.copy2(root/'build/epicfight-v6'/name, reports/('v6_'+name))
for path in (SOURCE/'capture').glob('*.json'):
    shutil.copy2(path, OUT/'reports'/path.name)
for name in ('validation_report.json', 'conversion_report.json'):
    shutil.copy2(ROOT/WORK/name, OUT/'reports'/name)
for name in ('capture_report.json', 'selection_report.json'):
    shutil.copy2(ROOT/'build/scythe_wide_06'/name, OUT/'reports'/name)
for path in SOURCE.glob('*.blend'):
    shutil.copy2(path, OUT/path.name)
for path in (SOURCE/'preview').glob('*'):
    if path.name.startswith(('Wide06_', 'Game_Mesh_')) and path.suffix.lower() in ('.mp4', '.jpg'):
        shutil.copy2(path, OUT/path.name)
shutil.copy2(ROOT/'docs/epicfight-poem-wide06.md', OUT/'README_中文.md')
(OUT/'reports/jar_validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
manifest = {p.relative_to(OUT).as_posix(): {'bytes': p.stat().st_size, 'sha256': sha(p)}
            for p in OUT.rglob('*') if p.is_file() and p.name != 'manifest.json'}
(OUT/'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print('WIDE06_PACKAGE_COMPLETE', json.dumps(report, ensure_ascii=False))
