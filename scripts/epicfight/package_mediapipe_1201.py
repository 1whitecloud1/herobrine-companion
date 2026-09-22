"""Verify and package the Forge 1.20.1 port after the Gradle/EF checks pass."""
import hashlib
import json
import shutil
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT.parent / 'herobrine_companion'
WORK = ROOT / 'build/epicfight-mediapipe'
OUT = ROOT / 'output/Herobrine_Scythe_Recapture_05_EpicFight_1.20.1'
RESOURCES = ROOT / 'src/main/resources'


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def main():
    registry = load(WORK / 'registry_validation.json')
    assert registry['player_modes'] == {'0': 'MediaPipe', '1': 'V6', '2': 'MediaPipe', '3': 'MediaPipe'}
    assert registry['registered_clips'] == 47 and registry['hero_combo_slots'] == 8
    assert registry['production_hit_phases_checked'] and registry['v6_speed_preserved']
    assert registry['full_body_attack_policy_checked']
    assert load(WORK/'conversion_report.json')['combat_edit'] == 'mediapipe_recapture_05'
    assert len(load(WORK / 'engine_validation.json')) == 24
    assert len(load(WORK / 'movement_validation.json')) == 20
    geometry = load(WORK/'validation_report.json')
    assert max(geometry['right_grip_max_error_blocks'], geometry['left_grip_max_error_blocks']) < .012
    assert geometry['maximum_planted_foot_drift_blocks'] < .005 and geometry['maximum_support_sole_height_blocks'] < .035
    assert geometry['ordinary_attacks_always_grounded'] and geometry['distinct_attack_trajectories']
    build_log = (WORK / 'final_build.log').read_bytes()
    assert b'BUILD SUCCESSFUL' in build_log or 'BUILD SUCCESSFUL'.encode('utf-16le') in build_log

    properties = dict(line.split('=', 1) for line in (ROOT / 'gradle.properties').read_text(encoding='utf-8').splitlines()
                      if '=' in line and not line.startswith('#'))
    assert properties['minecraft_version'] == '1.20.1'
    jar = ROOT / 'build/libs' / f"{properties['mod_name']}-1.20.1-forge-{properties['mod_version']}.jar"
    v6 = load(WORK / 'v6_baseline_sha256.json')
    assert len(v6) == 26
    paths = []
    for actor in ('player', 'hero'):
        paths += [p for p in (RESOURCES / f'assets/herobrine_companion/animmodels/animations/{actor}/poem_mediapipe').rglob('*') if p.is_file()]
    paths += [RESOURCES / 'assets/herobrine_companion/epicfight/poem_mediapipe_timing.json',
              RESOURCES / 'META-INF/licenses/herobrine_scythe_mediapipe.txt']
    assert len(paths) == 30
    synchronized = {}
    for p in paths:
        rel = p.relative_to(RESOURCES)
        assert p.read_bytes() == (SOURCE / 'src/main/resources' / rel).read_bytes(), rel
        synchronized[rel.as_posix()] = sha256(p.read_bytes())
    for rel, digest in v6.items():
        assert sha256((RESOURCES / rel).read_bytes()) == digest, rel

    # The port deliberately preserves the target's V6 recovery/phase function.
    java_prefix = 'src/main/java/com/whitecloud233/modid/herobrine_companion/compat/epicfight/'
    rel = java_prefix + 'PoemScythePlayerAnimations.java'
    before = (WORK / 'pre-sync-1201' / rel).read_text(encoding='utf-8')
    after = (ROOT / rel).read_text(encoding='utf-8')
    def phase(text):
        return text.split('    private static AttackAnimation.Phase phase(', 1)[1].split('    private static <T extends AttackAnimation>', 1)[0]
    assert phase(before) == phase(after), 'V6 phase or recovery changed'
    assert (WORK / 'biped.json').read_bytes() == (SOURCE / 'build/epicfight-mediapipe/biped.json').read_bytes()
    for rel in ('assets/herobrine_companion/animmodels/entity/hero_biped_nightfall.json',
                'assets/herobrine_companion/geo/item/poem_of_the_end.geo.json',
                'assets/herobrine_companion/item_skins/poem_of_the_end.json'):
        assert (RESOURCES / rel).read_bytes() == (SOURCE / 'src/main/resources' / rel).read_bytes(), rel

    class_prefix = 'com/whitecloud233/modid/herobrine_companion/'
    classes = [class_prefix + 'compat/epicfight/' + name + '.class' for name in (
        'MediaPipeScytheAnimations', 'MediaPipeScytheAttackAnimation', 'PoemScytheStyle', 'PoemScythePlayerAnimations',
        'HeroScytheComboBehaviors', 'HeroEpicFightBridge', 'HeroEpicFightCompat', 'HeroEpicFightPatch')]
    classes += [class_prefix + 'item/PoemOfTheEndItem.class']
    with zipfile.ZipFile(jar) as packed:
        assert packed.testzip() is None, 'JAR CRC failure'
        for actor in ('player', 'hero'):
            prefix = f'assets/herobrine_companion/animmodels/animations/{actor}/poem_mediapipe/combo_'
            assert len([n for n in packed.namelist() if n.startswith(prefix)]) == 8
        assert class_prefix + 'compat/epicfight/PoemScytheWeaponCapability.class' not in packed.namelist()
        for rel in classes:
            data = packed.read(rel)
            assert data[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(data[6:8], 'big') == 61, rel
        for rel, digest in (synchronized | v6).items():
            assert sha256(packed.read(rel)) == digest, rel
        assert packed.read('data/herobrine_companion/capabilities/weapons/poem_of_the_end.json') == (
            RESOURCES / 'data/herobrine_companion/capabilities/weapons/poem_of_the_end.json').read_bytes()
        assert b'onPoemModeChanged' in packed.read(class_prefix + 'item/PoemOfTheEndItem.class')
        metadata = packed.read('META-INF/mods.toml').decode('utf-8')
        assert 'modLoader="javafml"' in metadata and 'loaderVersion="[47,)"' in metadata
        assert 'versionRange="[1.20,1.21)"' in metadata  # Includes the pinned 1.20.1 build.

    report = {
        'jar': jar.relative_to(ROOT).as_posix(), 'bytes': jar.stat().st_size,
        'sha256': sha256(jar.read_bytes()), 'minecraft': '1.20.1', 'loader': 'Forge',
        'java_class_version': 61, 'new_resources_identical_to_1211': len(synchronized),
        'preserved_v6_resources': len(v6), 'v6_phase_and_recovery_preserved': True,
        'biped_and_hero_armatures_match': True, 'weapon_geometry_matches': True,
        'jar_crc_valid': True, 'live_gameplay_tested': False,
    }
    (WORK / 'jar_validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (WORK / 'resource_sync_sha256.json').write_text(json.dumps(synchronized, indent=2) + '\n', encoding='utf-8')
    OUT.mkdir(parents=True, exist_ok=True)
    target = OUT / (jar.stem + '-MediaPipe-Recapture05.jar')
    shutil.copy2(jar, target)
    shutil.copy2(ROOT / 'docs/epicfight-poem-1201.md', OUT / 'README_中文.md')
    reports = OUT / 'reports'
    reports.mkdir(exist_ok=True)
    for name in ('engine_validation.json', 'registry_validation.json', 'movement_validation.json', 'jar_validation.json',
                 'conversion_report.json', 'validation_report.json',
                 'resource_sync_sha256.json', 'v6_baseline_sha256.json'):
        shutil.copy2(WORK / name, reports / name)
    for name in ('engine_validation.json', 'reach_validation.json'):
        shutil.copy2(ROOT / 'build/epicfight-v6' / name, reports / ('v6_' + name))
    for name in ('capture_report.json', 'selection_report.json'):
        shutil.copy2(SOURCE/'build/scythe_recapture_05'/name, reports/name)
    for name in ('retarget_report.json', 'footstep_polish_report.json', 'blender_bake_report.json', 'source_review_report.json'):
        shutil.copy2(SOURCE/'output/Herobrine_Scythe_Recapture_05/capture'/name, reports/name)
    for video in (SOURCE/'build/epicfight-mediapipe/preview').glob('Recapture05_*.mp4'):
        shutil.copy2(video, OUT/video.name)
    sheet = SOURCE/'output/Herobrine_Scythe_Recapture_05_EpicFight/Game_Mesh_Poses.jpg'
    if sheet.exists():
        shutil.copy2(sheet, OUT/sheet.name)
    manifest = {p.relative_to(OUT).as_posix(): {'bytes': p.stat().st_size, 'sha256': sha256(p.read_bytes())}
                for p in sorted(OUT.rglob('*')) if p.is_file() and p.name != 'manifest.json'}
    (OUT / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    assert sha256(target.read_bytes()) == report['sha256']
    print('FORGE_1201_PACKAGE_OK', json.dumps({**report, 'delivery': str(target)}, ensure_ascii=False))


if __name__ == '__main__':
    main()
