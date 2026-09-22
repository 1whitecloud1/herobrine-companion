"""Audit scoped changes against the saved JARs, then package both mode-FX/horizon fixes."""
from collections import Counter
from datetime import datetime
from difflib import unified_diff
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import json
import re
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Poem_ModeFX_HorizonFix'
VERSIONS = [
    ('1.21.1-NeoForge', ROOT, 'com/whitecloud233/herobrine_companion', 65,
     'Herobrine Companion-0.38-1.21.1-neoforge.jar'),
    ('1.20.1-Forge', ROOT.parent / 'herobrine companion', 'com/whitecloud233/modid/herobrine_companion', 61,
     'Herobrine Companion-1.20.1-forge-0.38.jar'),
]
SHADERS = 'assets/herobrine_companion/shaders/'
NEW_RESOURCES = {SHADERS + name for name in ('post/void_rift_lens.json', 'program/void_rift_lens.json',
                 'program/void_rift_lens.vsh', 'program/void_rift_lens.fsh',
                 'program/void_rift_copy.json', 'program/void_rift_copy.fsh')}
NEW_CLASSES = ('VoidRiftPostEffect', 'VoidRiftPostEffect$LensChain', 'VoidRiftPostEffect$State',
               'VoidRiftPostEffect$Resources', 'VoidRiftProjection', 'VoidRiftProjection$Projected',
               'VoidRiftRenderer$FallbackMaterial')


def sha(payload):
    return hashlib.sha256(payload).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf8'))


def source_guards(root, package):
    source = Path('src/main/java') / package
    baseline = root / 'build/poem_mode_fx/baseline'
    rel = source / 'item/PoemOfTheEndItem.java'
    old = (baseline / rel).read_text(encoding='utf8')
    new = (root / rel).read_text(encoding='utf8')
    imported = 'import ' + package.replace('/', '.')
    marker = imported + '.entity.projectile.RealmBreakerLightningEntity;\n'
    expected = old.replace(marker, marker + imported + '.network.PacketHandler;\n' + imported + '.network.PaleLightningPacket;\n')
    vanilla = (r'            LightningBolt lightning = EntityType\.LIGHTNING_BOLT\.create\(level\);\n'
               r'            if \(lightning != null\) \{\n'
               r'                lightning.moveTo\(target.position\(\)\);\n'
               r'                lightning.setVisualOnly\(true\);[^\n]*\n'
               r'                level.addFreshEntity\(lightning\);\n'
               r'            \}')
    packet = ('            // Use the challenge mode\'s same branched white/cyan pillar and warning ring.\n'
              '            PacketHandler.sendToTracking(new PaleLightningPacket(target.getX(), target.getY(), target.getZ(), 6.0F), target);')
    expected, replacements = re.subn(vanilla, packet, expected)
    assert replacements == 1 and expected == new, 'Unexpected weapon gameplay/source change'
    rel = source / 'entity/projectile/VoidRiftEntity.java'
    old = (baseline / rel).read_text(encoding='utf8')
    new = (root / rel).read_text(encoding='utf8')
    marker = '    public float getRotation() {\n        return this.entityData.get(ROTATION);\n    }\n'
    getter = '\n    public int getVisualLifetime() {\n        return MAX_LIFE_TIME;\n    }\n'
    assert old.replace(marker, marker + getter) == new, 'Rift gameplay was changed beyond adding the visual lifetime getter'


def main():
    reports = OUT / 'reports'
    reports.mkdir(parents=True, exist_ok=True)
    gpu = read(ROOT / 'build/poem_mode_fx/shader_validation.json')
    assert gpu['status'] == 'passed' and gpu['full_two_pass_pipeline_tested']
    assert len(gpu['horizon_regression']) == 4
    assert all(r['legacy_band_rgb_max'] < 1e-6 and r['fixed_unaffected_rgba_error'] < 1e-6 for r in gpu['horizon_regression'])
    audited = []
    for version, root, package, major, filename in VERSIONS:
        work = root / 'build/poem_mode_fx'
        manifest = read(work / 'baseline.json')
        for relative, expected_hash in manifest.items():
            assert sha((work / 'baseline' / relative).read_bytes()) == expected_hash, 'Baseline backup was modified: ' + relative
        source_guards(root, package)
        standalone = read(root / 'build/poem_standalone/validation.json')
        mode = read(work / 'java_validation.json')
        assert standalone['status'] == mode['status'] == 'passed'
        assert not standalone['epic_fight_on_runtime_classpath'] and not standalone['geckolib_on_runtime_classpath']
        assert not mode['epic_fight_on_runtime_classpath'] and not mode['geckolib_on_runtime_classpath']
        assert mode['same_challenge_lightning_packet'] and mode['visual_only_rifts_use_new_shader']
        log_bytes = (work / 'build_and_tests.log').read_bytes()
        log = log_bytes.decode('utf16' if log_bytes.startswith(b'\xff\xfe') else 'utf8', errors='replace')
        for marker in ('BUILD SUCCESSFUL', 'POEM_MODE_FX_OK:', 'STANDALONE_POEM_OK:', 'POEM_TRAIL_STEP_OK:', 'POEM_NATIVE_ITEM_OK:', 'POEM_EMISSIVE_MATERIAL_OK:'):
            assert marker in log, f'{version}: {marker} missing'
        source = root / 'build/libs' / filename
        backup = work / 'baseline/build/libs' / filename
        with ZipFile(source) as jar, ZipFile(backup) as old:
            assert jar.testzip() is None
            assert all(count == 1 for count in Counter(jar.namelist()).values()), 'Duplicate JAR entries'
            before = {n for n in old.namelist() if not n.endswith('/')}
            after = {n for n in jar.namelist() if not n.endswith('/')}
            added, removed = sorted(after - before), sorted(before - after)
            changed = sorted(n for n in before & after if jar.read(n) != old.read(n))
            expected_added = NEW_RESOURCES | {package + '/client/render/' + n + '.class' for n in NEW_CLASSES}
            expected_removed = set()
            expected_changed = {package + '/' + n + '.class' for n in
                                ('client/render/VoidRiftRenderer', 'entity/projectile/VoidRiftEntity', 'item/PoemOfTheEndItem')}
            if major == 61:
                expected_removed |= {package + '/client/render/' + n + '.class' for n in
                                     ('VoidRiftDistortionHandler', 'VoidRiftDistortionHandler$ModBusEvents')}
                expected_removed |= {SHADERS + 'core/void_rift_distortion' + ext for ext in ('.json', '.vsh', '.fsh')}
                expected_changed.add(package + '/item/PoemOfTheEndItem$1.class')
            assert set(added) == expected_added, (version, 'Unexpected additions', added)
            assert set(removed) == expected_removed, (version, 'Unexpected removals', removed)
            assert set(changed) == expected_changed, (version, 'Unexpected changes', changed)
            for name in added + changed:
                payload = jar.read(name)
                if name.endswith('.class'):
                    assert int.from_bytes(payload[6:8], 'big') == major, 'Wrong Java target: ' + name
                    if name in added:
                        assert b'yesman/' not in payload and b'software/bernie/' not in payload, 'New optional animation dependency'
            for name in NEW_RESOURCES:
                payload = jar.read(name)
                assert payload == (root / 'src/main/resources' / name).read_bytes(), 'Outdated packed shader: ' + name
                if Path(name).name in gpu['shader_sha256']:
                    assert sha(payload) == gpu['shader_sha256'][Path(name).name], 'GPU tested a different shader'
            assert all(old.read(n) == jar.read(n) for n in before & after if n.startswith('assets/'))
            preserved = len(before & after) - len(changed)
        target = OUT / f'Herobrine_Companion-0.38-ModeFX-HorizonFix-{version}.jar'
        shutil.copy2(source, target)
        assert sha(target.read_bytes()) == sha(source.read_bytes())
        for file, suffix in [(work / 'build_and_tests.log', 'build_and_tests.log'),
                             (work / 'java_validation.json', 'mode_fx_validation.json'),
                             (root / 'build/poem_standalone/validation.json', 'standalone_validation.json'),
                             (root / 'build/poem_trail_step/validation.json', 'trail_step_validation.json'),
                             (root / 'build/poem_trail_step/native_item_validation.json', 'native_item_validation.json'),
                             (root / 'build/poem_trail_step/emissive_material_validation.json', 'emissive_material_validation.json')]:
            shutil.copy2(file, reports / f'{version}_{suffix}')
        patch = []
        source_paths = set(n for n in manifest if not n.startswith('build/'))
        source_paths |= {'src/main/resources/' + name for name in NEW_RESOURCES}
        source_paths |= {'src/main/java/' + package + '/client/render/' + name + '.java'
                         for name in ('VoidRiftProjection', 'VoidRiftPostEffect')}
        for relative in sorted(source_paths):
            before_path, after_path = work / 'baseline' / relative, root / relative
            before_text = before_path.read_text(encoding='utf8') if before_path.exists() else ''
            after_text = after_path.read_text(encoding='utf8') if after_path.exists() else ''
            patch.extend(unified_diff(before_text.splitlines(keepends=True), after_text.splitlines(keepends=True),
                                      fromfile='before/' + relative, tofile='after/' + relative))
        (reports / f'{version}_source_changes.patch').write_text(''.join(patch), encoding='utf8')
        audited.append({'version': version, 'jar': target.name, 'sha256': sha(target.read_bytes()),
                        'bytes': target.stat().st_size, 'added': added, 'changed': changed, 'removed': removed,
                        'unchanged_existing_entries': preserved, 'standalone_checks': standalone['checks'],
                        'mode_fx_checks': mode['checks'], 'weapon_damage_targeting_cooldowns_unchanged': True,
                        'rift_gameplay_unchanged': True, 'previous_trail_step_3d_glow_preserved': True,
                        'complete_gpu_pipeline_tested': True, 'horizon_alpha_regression_passed': True,
                        'live_gameplay_tested': False})
        print('MODE_FX_HORIZON_JAR_OK', version, 'preserved entries:', preserved)
    for filename in ('shader_validation.json', 'rift_shader_preview.png', 'rift_shader_preview.gif', 'horizon_black_band_fix.png'):
        shutil.copy2(ROOT / 'build/poem_mode_fx' / filename, reports / filename)
    (reports / 'jar_audit.json').write_text(json.dumps({'passed': True, 'created': datetime.now().isoformat(timespec='seconds'),
                                                     'versions': audited}, ensure_ascii=False, indent=2) + '\n', encoding='utf8')
    (OUT / '使用说明.md').write_text('''终末之诗：鸣雷、碎空着色器与地平线黑带修复

适用 Java 版 Minecraft 1.20.1 Forge、1.21.1 NeoForge。退出游戏后，选择对应版本的 JAR 替换 mods 目录中的旧 Herobrine Companion 本体，同一实例保留一个本体 JAR。多人游戏请同步更新客户端与服务端。

地平线黑带来自特效画面的最后回写：原版 blit 会按透明度混合，而天空、地平线的画面缓冲像素可能具有低透明度，导致已经画好的 RGB 与清屏黑色混合。本版使用独立的完整颜色拷贝着色器，不再按天空透明度衰减画面，同时保留原始 alpha 和世界深度。

鸣雷复用 Herobrine 挑战模式的预警环与白青色分叉雷柱，沿用挑战模式的演出时序。武器原有伤害判定、附魔加成、目标范围与冷却保持原样。

碎空裂隙改为程序化黑色裂口、紫白发光边缘和周围空间的引力扭曲。扭曲直接采样世界画面，并检查前景深度；第一人称手部与 HUD 在特效之后绘制。裂隙会平滑开启、消退，同屏最多处理最近的 8 个。Forge 的受击事件生成的仅视觉裂隙也接入相同效果；旧贴图扭曲渲染器已移除，实体是否造成伤害的规则保留。资源重载会重新加载着色器；着色器加载失败时提供简单的无贴图裂口回退。

前版的独立攻击刀光、攻击小位移、无 GeckoLib 的原生 3D 模型及刀刃/宝石发光修复全部保留。

两个版本均已完整构建。每版通过 4870 项特效与投影检查（含 429 组相机/视场角/分辨率组合）、原有约 23.9 万项动作与模型回归检查。实际 OpenGL 3.2 执行完整的两段后处理流程，通过 296 项检查，覆盖四种分辨率、遮挡、背景折射、多裂隙、消退，以及透明度为 0、1、64、128、254、255 的画面回写。地平线测试使用原版透明混合复现黑带，修复后的流程保持裂隙范围外的 RGBA 不变。

尚未进行 Minecraft 游戏内实测。reports 中的预览和黑带前后对比是实际 GLSL 的离屏测试图，不是游戏截图。构建日志、校验报告、相对修改前备份的源码补丁及 JAR 审核报告均位于 reports。修改前备份位于两个工程各自的 build/poem_mode_fx/baseline。
''', encoding='utf8')
    files = sorted(list(OUT.glob('*.jar')) + [OUT / '使用说明.md'] + list(reports.iterdir()))
    sums = OUT / 'SHA256SUMS.txt'
    sums.write_text(''.join(sha(p.read_bytes()) + '  ' + p.relative_to(OUT).as_posix() + '\n' for p in files), encoding='utf8')
    archive = OUT / 'Herobrine_Poem_ModeFX_HorizonFix_双版本.zip'
    with ZipFile(archive, 'w', ZIP_DEFLATED, compresslevel=6) as z:
        for path in files + [sums]:
            z.write(path, path.relative_to(OUT).as_posix())
    with ZipFile(archive) as z:
        assert z.testzip() is None
    print('MODE_FX_HORIZON_PACKAGE_OK', archive)


if __name__ == '__main__':
    main()
