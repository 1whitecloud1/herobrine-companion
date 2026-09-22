"""Verify and package the two tested Unity09 builds without raw preview frames."""
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import struct
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_09_EpicFight'
REPORTS = OUT / 'reports'
BLEND = OUT / 'Herobrine_终末之诗_Unity09_四模式与重击.blend'
VERSIONS = [
    dict(key='neoforge_1.21.1', repo=ROOT, version='1.21.1', major=65,
         package='com/whitecloud233/herobrine_companion',
         source='Herobrine Companion-0.38-1.21.1-neoforge.jar',
         delivered='Herobrine_Companion-0.38-Unity09-1.21.1-NeoForge.jar',
         log='final_build_121.log'),
    dict(key='forge_1.20.1', repo=Path('E:/java/herobrine companion'), version='1.20.1', major=61,
         package='com/whitecloud233/modid/herobrine_companion',
         source='Herobrine Companion-1.20.1-forge-0.38.jar',
         delivered='Herobrine_Companion-0.38-Unity09-1.20.1-Forge.jar',
         log='final_build_120.log'),
]


def sha(data):
    return hashlib.sha256(data).hexdigest()


def digest(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    REPORTS.mkdir(parents=True, exist_ok=True)
    assets = sorted(p for p in (OUT / 'resources').rglob('*') if p.is_file())
    assert len(assets) == 170, len(assets)
    build_reports = []
    for version in VERSIONS:
        repo = version['repo']
        source_jar = repo / 'build/libs' / version['source']
        destination = OUT / version['delivered']
        work = repo / 'build/scythe_unity_pack_09'
        build_log = work / version['log']
        assert 'BUILD SUCCESSFUL' in build_log.read_text('utf-8', errors='replace')
        assert 'POEM_INPUT_OK: 49 checks' in build_log.read_text('utf-8', errors='replace')
        if version['version'] == '1.20.1':
            assert 'BUILD SUCCESSFUL' in (work / 'crash_fix_confirm_120.log').read_text('utf-8', errors='replace')
        shutil.copy2(source_jar, destination)
        assert digest(source_jar) == digest(destination)
        checked = {}
        baseline = json.loads((repo / 'build/epicfight-mediapipe/v6_baseline_sha256.json').read_text('utf-8'))
        with zipfile.ZipFile(destination) as jar:
            duplicates = [n for n, count in Counter(jar.namelist()).items() if count > 1]
            assert not duplicates, duplicates
            for file in assets:
                resource = file.relative_to(OUT / 'resources').as_posix()
                wanted = digest(file)
                assert digest(repo / 'src/main/resources' / resource) == wanted, (version['key'], resource, 'source')
                assert sha(jar.read(resource)) == wanted, (version['key'], resource, 'jar')
                checked[resource] = wanted
            for resource in ['data/herobrine_companion/capabilities/weapons/poem_of_the_end.json']:
                assert sha(jar.read(resource)) == digest(repo / 'src/main/resources' / resource)
            clips = [n for n in checked if '/animmodels/animations/' in n and '/data/' not in n]
            metadata = [n for n in checked if '/animmodels/animations/' in n and '/data/' in n]
            assert len(clips) == 84 and len(metadata) == 84
            for name in [
                'client/event/PoemScytheInputEvents', 'combat/PoemAttackInput', 'combat/PoemGestureQueue',
                'network/PoemGesturePacket', 'compat/epicfight/PoemGestureController',
                'compat/epicfight/UnityScytheAnimations', 'compat/epicfight/UnityScytheExtraAnimations',
                'compat/epicfight/UnityScythePlungeAnimation',
            ]:
                data = jar.read(version['package'] + '/' + name + '.class')
                assert data[:4] == b'\xca\xfe\xba\xbe'
                assert struct.unpack('>H', data[6:8])[0] == version['major'], name
            for language in ['zh_cn', 'en_us']:
                resource = f'assets/herobrine_companion/lang/{language}.json'
                actual = json.loads(jar.read(resource))
                expected = json.loads((repo / 'src/main/resources' / resource).read_text('utf-8'))
                for key in ['poem_flurry_modifier', 'poem_uppercut_modifier', 'poem_heavy_modifier', 'poem_heavy_attack']:
                    full = 'key.herobrine_companion.' + key
                    assert actual[full] == expected[full] and actual[full], (language, key)
            for name, wanted in baseline.items():
                path = repo / name
                if not path.is_file():
                    path = repo / 'src/main/resources' / name
                assert digest(path) == wanted, ('V6 source', path)
                resource = path.relative_to(repo / 'src/main/resources').as_posix()
                assert sha(jar.read(resource)) == wanted, ('V6 jar', resource)
            if version['version'] == '1.20.1':
                mixins = json.loads(jar.read('herobrine_companion.mixins.json'))
                assert 'epicfight.WomAntitheusLapseMixin' in mixins['mixins']
                for name in ['mixin/epicfight/WomAntitheusLapseMixin', 'compat/epicfight/WomAntitheusLapseCompat']:
                    assert struct.unpack('>H', jar.read(version['package'] + '/' + name + '.class')[6:8])[0] == 61
        for name in ['unity_modes_runtime_validation.json', 'unity_extras_runtime_validation.json']:
            shutil.copy2(work / name, REPORTS / (version['key'] + '_' + name))
        shutil.copy2(build_log, REPORTS / (version['key'] + '_animations_build.log'))
        build_reports.append(dict(version=version['key'], jar=destination.name, bytes=destination.stat().st_size,
                                  sha256=digest(destination), checked_resource_files=len(checked),
                                  animation_clips=len(clips), animation_display_configs=len(metadata),
                                  preserved_v6_files=len(baseline), java_class_major=version['major'],
                                  input_checks=49, resource_hashes=checked))
        print('JAR_VERIFIED', version['key'], destination.name, flush=True)

    forge_work = VERSIONS[1]['repo'] / 'build/scythe_unity_pack_09'
    shutil.copy2(forge_work / 'wom_lapse_ownership_validation.json', REPORTS / 'forge_1.20.1_wom_lapse_ownership_validation.json')
    shutil.copy2(forge_work / 'crash_fix_confirm_120.log', REPORTS / 'forge_1.20.1_crash_fix_build.log')
    shutil.copy2(ROOT / 'build/scythe_unity_pack_09/mcp_final_viewport.jpg', REPORTS / 'mcp_final_viewport.jpg')
    blend_check = json.loads((REPORTS / 'blender_project_validation.json').read_text('utf-8'))
    assert blend_check['actions'] == 42 and blend_check['bones'] == 20
    assert digest(BLEND) == blend_check['sha256']
    mcp = json.loads((REPORTS / 'mcp_final_scene.json').read_text('utf-8'))
    assert mcp['actions'] == 42 and mcp['bones'] == 20 and len(mcp['packed_images']) >= 2
    report = dict(status='passed', created=datetime.now().isoformat(timespec='seconds'),
                  versions=build_reports, blender_actions=42, blender_bones=20,
                  live_gameplay_tested=False, raw_preview_sequences_excluded=True)
    (REPORTS / 'unity09_delivery_validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    files = [p for p in OUT.iterdir() if p.is_file() and p.suffix.lower() in {'.jar', '.md', '.mp4', '.jpg'}]
    files.append(BLEND)
    for directory in [OUT / 'resources', REPORTS]:
        files.extend(p for p in directory.rglob('*') if p.is_file())
    files = sorted(set(files))
    hashes = OUT / 'SHA256SUMS.txt'
    hashes.write_text(''.join(digest(p) + '  ' + p.relative_to(OUT).as_posix() + '\n' for p in files), encoding='utf-8')
    files.append(hashes)
    archive = OUT / 'Herobrine_Scythe_Unity09_双版本交付包.zip'
    print('PACKAGING', len(files), 'files', sum(p.stat().st_size for p in files), 'bytes before compression', flush=True)
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=6, allowZip64=True) as bundle:
        for file in files:
            bundle.write(file, file.relative_to(OUT).as_posix())
    with zipfile.ZipFile(archive) as bundle:
        assert bundle.testzip() is None
        assert not any(n.startswith('preview/') for n in bundle.namelist())
    archive.with_suffix('.zip.sha256').write_text(digest(archive) + '  ' + archive.name + '\n', encoding='utf-8')
    print('PACKAGE_COMPLETE', archive, archive.stat().st_size, flush=True)


if __name__ == '__main__':
    main()
