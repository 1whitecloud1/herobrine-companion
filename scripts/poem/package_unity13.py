"""Package and audit the GeckoLib resource discovery fix against exact old JARs."""
from collections import Counter
from datetime import datetime
import hashlib
import json
from pathlib import Path
import shutil
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/epicfight'))
from package_unity09 import VERSIONS

OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_13_GeckoFix'
REPORTS = OUT / 'reports'
OLD = 'assets/herobrine_companion/animations/poem_standalone/'
NEW = 'assets/herobrine_companion/poem_standalone/'


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8'))


def sha(data):
    return hashlib.sha256(data).hexdigest()


def main():
    REPORTS.mkdir(parents=True, exist_ok=True)
    baseline = read(OUT / 'baseline.json')
    versions = []
    for version in VERSIONS:
        project = version['repo']
        previous = next(row for row in baseline['jars'] if row['key'] == version['key'])
        source = project / 'build/libs' / version['source']
        log = project / 'build/poem_gecko_resource_layout/build_and_tests.log'
        log_text = log.read_text(encoding='utf-8', errors='replace')
        for marker in ('BUILD SUCCESSFUL', 'STANDALONE_POEM_OK:', 'GECKO_RESOURCE_LAYOUT_OK:'):
            assert marker in log_text, (version['key'], marker)
        if version['version'] == '1.20.1':
            # This checkout's pre-existing gradlew.bat falls through to exit /b 1
            # even after success. Confirm check with GradleWrapperMain directly.
            direct_log = project / 'build/poem_gecko_resource_layout/direct_check.log'
            assert 'BUILD SUCCESSFUL' in direct_log.read_text(encoding='utf-8', errors='replace')
            shutil.copy2(direct_log, REPORTS / 'forge_1.20.1_direct_check.log')
        layout = read(project / 'build/poem_gecko_resource_layout/validation.json')
        standalone = read(project / 'build/poem_standalone/validation.json')
        assert layout['passed'] and not layout['invalid']
        assert standalone['status'] == 'passed' and standalone['leg_stance_regression_passed']
        assert sha(Path(previous['backup']).read_bytes()) == previous['sha256']
        with zipfile.ZipFile(source) as jar, zipfile.ZipFile(previous['backup']) as old:
            assert jar.testzip() is None
            assert not [name for name, count in Counter(jar.namelist()).items() if count > 1]
            old_names = {i.filename for i in old.infolist() if not i.is_dir()}
            new_names = {i.filename for i in jar.infolist() if not i.is_dir()}
            assert old_names - new_names == {OLD + name for name in ('rig.json', 'weapon.json')}
            assert new_names - old_names == {NEW + name for name in ('rig.json', 'weapon.json')}
            for name in ('rig.json', 'weapon.json'):
                assert jar.read(NEW + name) == old.read(OLD + name)
                assert jar.read(NEW + name) == (project / 'src/main/resources' / (NEW + name)).read_bytes()
            scanned = []
            for name in new_names:
                parts = name.split('/')
                if len(parts) > 3 and parts[0] == 'assets' and parts[2] == 'animations' and name.endswith('.json'):
                    assert isinstance(json.loads(jar.read(name)).get('animations'), dict), name
                    scanned.append(name)
                if name.endswith('.class'):
                    assert b'animations/poem_standalone/' not in jar.read(name), name
            assert len(scanned) == layout['animation_files'] > 0
            changed = sorted(name for name in old_names & new_names if old.read(name) != jar.read(name))
            allowed = (version['package'] + '/combat/poem/PoemMotionLibrary',
                       version['package'] + '/client/render/StandalonePoemRenderer')
            assert all(name.endswith('.class') and name.startswith(allowed) for name in changed), changed
            # Every other resource/class must match the user's pre-fix build.
            v6 = read(project / 'build/epicfight-mediapipe/v6_baseline_sha256.json')
            for name, wanted in v6.items():
                file = project / name
                if not file.is_file():
                    file = project / 'src/main/resources' / name
                resource = file.relative_to(project / 'src/main/resources').as_posix()
                assert sha(file.read_bytes()) == wanted == sha(jar.read(resource)), resource
        target = OUT / version['delivered'].replace('Unity09', 'Unity13-GeckoFix')
        shutil.copy2(source, target)
        assert sha(target.read_bytes()) == sha(source.read_bytes())
        for report, suffix in ((log, 'build_and_tests.log'),
                (project / 'build/poem_gecko_resource_layout/validation.json', 'gecko_resource_layout.json'),
                (project / 'build/poem_standalone/validation.json', 'standalone_validation.json'),
                (project / 'build/poem_standalone/leg_stance_validation.json', 'leg_stance_validation.json')):
            shutil.copy2(report, REPORTS / (version['key'] + '_' + suffix))
        versions.append({
            'version': version['key'], 'jar': target.name, 'sha256': sha(target.read_bytes()),
            'bytes': target.stat().st_size, 'gecko_scanned_animations': sorted(scanned),
            'relocated_resources': [NEW + name for name in ('rig.json', 'weapon.json')],
            'relocated_resource_bytes_unchanged': True, 'changed_classes': changed,
            'all_other_jar_entries_unchanged': True, 'preserved_v6_resources': len(v6),
            'standalone_checks': standalone['checks'], 'live_gameplay_tested': False,
        })
        print('UNITY13_JAR_OK', version['key'], standalone['checks'], 'checks;', len(changed), 'class files', flush=True)
    before_log = Path(baseline['jars'][0]['root']) / 'build/poem_gecko_resource_layout/before_fix.log'
    text = before_log.read_text(encoding='utf-8', errors='replace')
    assert 'BUILD FAILED' in text and OLD + 'rig.json' in text and OLD + 'weapon.json' in text
    shutil.copy2(before_log, REPORTS / 'regression_before_fix.log')
    report = {'passed': True, 'created': datetime.now().isoformat(timespec='seconds'),
              'cause': 'GeckoLib parsed custom rig and mesh JSON placed under its animations discovery root',
              'regression_failed_before_fix': True, 'versions': versions, 'live_gameplay_tested': False}
    (REPORTS / 'unity13_validation.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    readme = '''终末之诗 Unity13：修复 GeckoLib 加载资源时崩溃

适用版本：1.20.1 Forge、1.21.1 NeoForge。选择与你的游戏版本匹配的 JAR，替换 mods 中旧的 Herobrine Companion 本体，同一实例只保留一个本体 JAR。

2026-09-14 08:34:35 的 1.20.1 崩溃发生在启动资源加载阶段。GeckoLib 4.8.4 自动扫描 assets/herobrine_companion/animations 下的 JSON，将终末之诗的 rig.json 骨架数据当作 GeckoLib 动画解析，由于缺少 animations 对象而报错。同目录的 weapon.json 网格数据存在相同问题。

现在把这两个文件移到 assets/herobrine_companion/poem_standalone，并更新动作库及武器渲染器的读取路径。两个文件的内容逐字节保留。四模式动作、腿部修正、疾跑/空中攻击、长按左键重击、连招衔接和 V6 资源均保持原样。

两个版本均完成构建、独立玩家动作读取和腿部检查。新增的 GeckoLib 扫描目录检查已接入 check/build：修复前能同时检出 rig.json 和 weapon.json，修复后通过。最终 JAR 再次核对，旧路径不存在，新路径可读取；除读取路径相关类及对应调试行号外，其他 JAR 文件内容与修复前完全一致。

本次没有启动 Minecraft，尚未做游戏启动及实机游玩验证。当前游戏 mods 里的旧 JAR 尚未自动替换。修复包和完整构建/验证报告位于本目录；修复前的两个 JAR 与修改前源码保存在 backups，baseline.json 记录备份路径及哈希。既有 Unity12 和网易交付保留。
'''
    (OUT / '使用说明.md').write_text(readme, encoding='utf-8')
    files = sorted(list(OUT.glob('*.jar')) + [OUT / '使用说明.md'] + [p for p in REPORTS.iterdir() if p.is_file()])
    sums = OUT / 'SHA256SUMS.txt'
    sums.write_text(''.join(sha(p.read_bytes()) + '  ' + p.relative_to(OUT).as_posix() + '\n' for p in files), encoding='utf-8')
    archive = OUT / 'Herobrine_Unity13_双版本GeckoLib崩溃修复.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for path in files + [sums]:
            z.write(path, path.relative_to(OUT).as_posix())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None
    archive.with_suffix('.zip.sha256').write_text(sha(archive.read_bytes()) + '  ' + archive.name + '\n', encoding='utf-8')
    print('UNITY13_PACKAGE_OK', archive, archive.stat().st_size, flush=True)


if __name__ == '__main__':
    main()
