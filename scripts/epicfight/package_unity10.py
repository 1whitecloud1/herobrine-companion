"""Package the validated tap/hold and short-recovery release for both loaders."""
from collections import Counter
from datetime import datetime
import json
from pathlib import Path
import shutil
import struct
import zipfile

from package_unity09 import digest, sha, VERSIONS

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
REPORTS = OUT / 'reports'
BLEND = OUT / 'Herobrine_终末之诗_Unity10_四模式与重击.blend'


def main():
    assets = sorted(p for p in (OUT / 'resources').rglob('*') if p.is_file())
    assert len(assets) == 170, len(assets)
    build_reports = []
    for version in VERSIONS:
        repo, key = version['repo'], version['key']
        suffix = '121' if version['version'] == '1.21.1' else '120'
        work = repo / 'build/scythe_unity_pack_09'
        full_log = work / f'unity10_build_{suffix}.log'
        final_log = work / f'unity10_input_final_{suffix}.log'
        full_text, final_text = [p.read_text('utf-8', errors='replace') for p in (full_log, final_log)]
        assert all(s in full_text for s in ['BUILD SUCCESSFUL', 'UNITY_MODES_RUNTIME_OK', 'UNITY_EXTRAS_RUNTIME_OK', 'POEM_INPUT_OK: 72 checks'])
        assert all(s in final_text for s in ['BUILD SUCCESSFUL', 'POEM_INPUT_OK: 72 checks', 'POEM_CONTROL_ENGINE_OK: 13 checks'])
        if suffix == '120':
            assert 'WOM_LAPSE_OWNERSHIP_PASSED 11 checks, 3 matching writes' in full_text
        source = repo / 'build/libs' / version['source']
        destination = OUT / version['delivered'].replace('Unity09', 'Unity10')
        shutil.copy2(source, destination)
        assert digest(source) == digest(destination)
        checked = {}
        baseline = json.loads((repo / 'build/epicfight-mediapipe/v6_baseline_sha256.json').read_text('utf-8'))
        with zipfile.ZipFile(destination) as jar:
            assert not [n for n, count in Counter(jar.namelist()).items() if count > 1]
            for file in assets:
                resource = file.relative_to(OUT / 'resources').as_posix()
                wanted = digest(file)
                assert digest(repo / 'src/main/resources' / resource) == wanted, (key, resource, 'source')
                assert sha(jar.read(resource)) == wanted, (key, resource, 'jar')
                checked[resource] = wanted
            weapon = 'data/herobrine_companion/capabilities/weapons/poem_of_the_end.json'
            assert sha(jar.read(weapon)) == digest(repo / 'src/main/resources' / weapon)
            clips = [n for n in checked if '/animmodels/animations/' in n and '/data/' not in n]
            metadata = [n for n in checked if '/animmodels/animations/' in n and '/data/' in n]
            assert len(clips) == 84 and len(metadata) == 84
            for name in ['client/event/PoemScytheInputEvents', 'client/event/PoemScytheInputEvents$EpicFightKeys',
                         'combat/PoemAttackInput', 'combat/PoemGestureQueue', 'network/PoemGesturePacket',
                         'mixin/epicfight/PoemAttackControlMixin', 'compat/epicfight/PoemGestureController',
                         'compat/epicfight/UnityScytheAnimations', 'compat/epicfight/UnityScytheExtraAnimations',
                         'compat/epicfight/UnityScythePlungeAnimation']:
                data = jar.read(version['package'] + '/' + name + '.class')
                assert data[:4] == b'\xca\xfe\xba\xbe' and struct.unpack('>H', data[6:8])[0] == version['major'], name
            config = json.loads(jar.read('herobrine_companion.mixins.json'))
            assert 'epicfight.PoemAttackControlMixin' in config['client']
            assert 'epicfight.PoemAttackControlMixin' not in config['mixins']
            for language in ['zh_cn', 'en_us']:
                resource = f'assets/herobrine_companion/lang/{language}.json'
                actual = json.loads(jar.read(resource))
                expected = json.loads((repo / 'src/main/resources' / resource).read_text('utf-8'))
                for name in ['poem_flurry_modifier', 'poem_uppercut_modifier', 'poem_heavy_modifier']:
                    field = 'key.herobrine_companion.' + name
                    assert actual[field] == expected[field] and actual[field]
                hint = 'item.herobrine_companion.poem_of_the_end.epicfight.input'
                assert actual[hint] == expected[hint] and '0.35' in actual[hint]
                assert 'key.herobrine_companion.poem_heavy_attack' not in actual
            for name, wanted in baseline.items():
                file = repo / name
                if not file.is_file(): file = repo / 'src/main/resources' / name
                assert digest(file) == wanted, ('V6 source', file)
                resource = file.relative_to(repo / 'src/main/resources').as_posix()
                assert sha(jar.read(resource)) == wanted, ('V6 jar', resource)
            if suffix == '120':
                assert 'epicfight.WomAntitheusLapseMixin' in config['mixins']
                for name in ['mixin/epicfight/WomAntitheusLapseMixin', 'compat/epicfight/WomAntitheusLapseCompat']:
                    assert struct.unpack('>H', jar.read(version['package']+'/'+name+'.class')[6:8])[0] == 61
        for name in ['unity_modes_runtime_validation.json', 'unity_extras_runtime_validation.json']:
            shutil.copy2(work / name, REPORTS / (key + '_' + name))
        shutil.copy2(full_log, REPORTS / (key + '_animations_build.log'))
        shutil.copy2(final_log, REPORTS / (key + '_final_input_build.log'))
        if suffix == '120':
            shutil.copy2(work / 'wom_lapse_ownership_validation.json', REPORTS / 'forge_1.20.1_wom_lapse_ownership_validation.json')
        build_reports.append(dict(version=key, jar=destination.name, bytes=destination.stat().st_size,
            sha256=digest(destination), checked_resource_files=len(checked), animation_clips=len(clips),
            animation_display_configs=len(metadata), preserved_v6_files=len(baseline), java_class_major=version['major'],
            input_checks=72, installed_control_engine_checks=13, resource_hashes=checked))
        print('UNITY10_JAR_VERIFIED', key, destination.name, flush=True)

    blend = json.loads((REPORTS / 'blender_project_validation.json').read_text('utf-8'))
    assert blend['actions'] == 42 and blend['bones'] == 20 and digest(BLEND) == blend['sha256']
    mcp = json.loads((REPORTS / 'mcp_final_scene.json').read_text('utf-8'))
    assert mcp['actions'] == 42 and mcp['bones'] == 20 and len(mcp['packed_images']) >= 2
    assert mcp['heavy_source'] == 'Combo_Attack_05_01' and (REPORTS / mcp['visual_preview']).is_file()
    assert json.loads((REPORTS / 'media_validation.json').read_text('utf-8'))['status'] == 'passed'
    report = dict(status='passed', created=datetime.now().isoformat(timespec='seconds'), versions=build_reports,
                  blender_actions=42, blender_bones=20, live_gameplay_tested=False,
                  heavy_hold_milliseconds=350, heavy_after_contact_recovery_seconds=.12,
                  final_combo_after_contact_recovery_seconds=.10, raw_preview_sequences_excluded=True)
    (REPORTS / 'unity10_delivery_validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
    files = [p for p in OUT.iterdir() if p.is_file() and p.suffix.lower() in {'.jar', '.md', '.mp4', '.jpg'}]
    files.append(BLEND)
    for folder in [OUT / 'resources', REPORTS]:
        files.extend(p for p in folder.rglob('*') if p.is_file())
    files = sorted(set(files))
    hashes = OUT / 'SHA256SUMS.txt'
    hashes.write_text(''.join(digest(p) + '  ' + p.relative_to(OUT).as_posix() + '\n' for p in files), encoding='utf-8')
    files.append(hashes)
    archive = OUT / 'Herobrine_Scythe_Unity10_双版本交付包.zip'
    print('PACKAGING_UNITY10', len(files), 'files', flush=True)
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as bundle:
        for file in files:
            bundle.write(file, file.relative_to(OUT).as_posix())
    with zipfile.ZipFile(archive) as bundle:
        assert bundle.testzip() is None
        assert not any(n.startswith('preview/') for n in bundle.namelist())
    archive.with_suffix('.zip.sha256').write_text(digest(archive) + '  ' + archive.name + '\n', encoding='utf-8')
    print('UNITY10_PACKAGE_COMPLETE', archive, archive.stat().st_size, flush=True)


if __name__ == '__main__':
    main()
