"""Audit the exact built JARs against the saved baseline and package both versions."""
from collections import Counter
from datetime import datetime
from io import BytesIO
from pathlib import Path
import hashlib
import json
import shutil
import zipfile
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Poem_ClearTrail_3DGlow'
VERSIONS = [
    ('1.21.1-NeoForge', ROOT, 'com/whitecloud233/herobrine_companion', 65,
     'Herobrine Companion-0.38-1.21.1-neoforge.jar'),
    ('1.20.1-Forge', Path('E:/java/herobrine companion'), 'com/whitecloud233/modid/herobrine_companion', 61,
     'Herobrine Companion-1.20.1-forge-0.38.jar'),
]
TEXTURE = 'assets/herobrine_companion/textures/trail/poem_standalone_trail.png'
NATIVE_RESOURCES = {
    'assets/herobrine_companion/poem_standalone/item_model.json',
    'assets/herobrine_companion/textures/item/poem_of_the_end_native.png',
    'assets/herobrine_companion/textures/item/poem_of_the_end_native_glow.png',
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding='utf-8'))


def metadata_values(data):
    # Older Forge builds used a different default charset for Chinese TOML comments.
    return b'\n'.join(line for line in data.splitlines() if line.strip() and not line.lstrip().startswith(b'#'))


def main():
    reports = OUT / 'reports'
    reports.mkdir(parents=True, exist_ok=True)
    audited = []
    shader_validation = read(ROOT / 'build/poem_trail_step/emissive_shader_validation.json')
    assert shader_validation['passed'] and len(shader_validation['versions']) == 2
    assert all(v['passed'] and v['emissive_fragments_unchanged_in_darkness']
               and v['minecraft_shader_has_no_normal_or_lightmap_lighting'] for v in shader_validation['versions'])
    visibility = read(ROOT / 'build/poem_trail_step/trail_visibility_validation.json')
    assert visibility['passed'] and visibility['checked_background_poses'] == 32
    for version, root, package, major, filename in VERSIONS:
        work = root / 'build/poem_trail_step'
        baseline = read(work / 'baseline.json')
        backup = work / 'baseline/build/libs' / filename
        assert sha(backup.read_bytes()) == baseline['build/libs/' + filename]
        validation = read(work / 'validation.json')
        standalone = read(root / 'build/poem_standalone/validation.json')
        native = read(work / 'native_item_validation.json')
        model_source = read(work / 'native_model_source_validation.json')
        emissive = read(work / 'emissive_material_validation.json')
        assert validation['passed'] and validation['stages_with_one_impulse'] == 16
        assert validation['actual_trail_renderer_and_left_hand_checked']
        assert .21 < validation['trail_lifetime_seconds'] < .23 and validation['maximum_ribbon_edges'] <= 29
        assert standalone['status'] == 'passed' and not standalone['epic_fight_on_runtime_classpath']
        assert not standalone['geckolib_on_runtime_classpath']
        assert native['passed'] and native['epic_fight_without_geckolib_selects_3d'] and native['vertices'] == 6096
        assert native['maximum_tool_space_error'] < .00002 and not native['geckolib_on_runtime_classpath']
        assert model_source['passed'] and model_source['maximum_model_space_error'] < .00002
        assert model_source['glowing_pixels'] == 231 and model_source['glow_sections'] == 59
        assert emissive['passed'] and emissive['unlit_shader_selected'] and emissive['both_native_renderers_bind_glow']
        assert emissive['dark_base_and_full_bright_glow_checked'] and emissive['matching_vertex_pairs'] == 18288
        log = (work / 'build_and_tests.log').read_text(encoding='utf-8', errors='replace')
        assert all(marker in log for marker in ('BUILD SUCCESSFUL', 'STANDALONE_POEM_OK:', 'POEM_TRAIL_STEP_OK:',
                                               'POEM_NATIVE_ITEM_OK:', 'POEM_EMISSIVE_MATERIAL_OK:'))
        source = root / 'build/libs' / filename
        with zipfile.ZipFile(source) as jar, zipfile.ZipFile(backup) as old:
            assert jar.testzip() is None
            assert not [name for name, count in Counter(jar.namelist()).items() if count > 1]
            before = {name for name in old.namelist() if not name.endswith('/')}
            after = {name for name in jar.namelist() if not name.endswith('/')}
            added, removed = sorted(after - before), sorted(before - after)
            changed = sorted(name for name in before & after if old.read(name) != jar.read(name))
            expected_added = {TEXTURE} | NATIVE_RESOURCES | {package + '/' + name + '.class' for name in (
                'combat/poem/PoemBladeTrail', 'combat/poem/PoemBladeTrail$Edge', 'combat/poem/PoemBladeTrail$Ribbon',
                'combat/poem/PoemAttackStep', 'client/animation/PoemTrailMesh', 'client/render/StandalonePoemTrailRenderer',
                'client/animation/PoemItemMesh', 'client/animation/PoemItemMesh$Display', 'client/animation/PoemItemMesh$Face',
                'client/animation/PoemItemMesh$Mesh', 'client/animation/PoemItemMesh$Setup',
                'client/render/PoemOfTheEndMeshRenderer', 'client/render/PoemRenderBackend', 'client/render/PoemWeaponRenderTypes')}
            assert set(added) == expected_added and not removed, (version, added, removed)
            allowed_changed = tuple(package + '/' + name for name in (
                'combat/poem/PoemMotionLibrary', 'combat/poem/StandalonePoemController',
                'client/animation/StandalonePoemAnimation', 'client/render/StandalonePoemRenderer',
                'client/render/PoemOfTheEndGeoCompat', 'client/render/PoemOfTheEndModelSwapper'))
            for name in changed:
                if name == 'META-INF/mods.toml':
                    assert metadata_values(old.read(name)) == metadata_values(jar.read(name)), 'Mod dependencies changed'
                else:
                    assert name.endswith('.class') and name.startswith(allowed_changed), name
            for name in added + changed:
                if name.endswith('.class'):
                    data = jar.read(name)
                    assert int.from_bytes(data[6:8], 'big') == major, (version, name, 'Java target mismatch')
                    assert b'yesman/' not in data and b'software/bernie/' not in data, name
            texture_data = jar.read(TEXTURE)
            assert texture_data == (root / 'src/main/resources' / TEXTURE).read_bytes()
            image = Image.open(BytesIO(texture_data)); image.load()
            assert image.mode == 'RGBA' and image.size == (256, 64)
            for name in NATIVE_RESOURCES:
                assert jar.read(name) == (root / 'src/main/resources' / name).read_bytes(), name
                if name.endswith('.png'):
                    image = Image.open(BytesIO(jar.read(name))); image.load()
                    assert image.mode == 'RGBA' and image.size == (128, 128)
            assert model_source['source_texture_sha256'] == sha(jar.read('assets/herobrine_companion/textures/item/poem_of_the_end_geo.png'))
            assert model_source['source_geometry_sha256'] == sha(jar.read('assets/herobrine_companion/geo/item/poem_of_the_end.geo.json'))
            unchanged_assets = sum(name.startswith('assets/') for name in before & after)
            assert all(old.read(name) == jar.read(name) for name in before & after if name.startswith('assets/'))
        target = OUT / f'Herobrine_Companion-0.38-ClearTrail-3DGlow-{version}.jar'
        shutil.copy2(source, target)
        assert sha(source.read_bytes()) == sha(target.read_bytes())
        for src, suffix in [(work / 'build_and_tests.log', 'build_and_tests.log'),
                            (work / 'validation.json', 'trail_step_validation.json'),
                            (work / 'native_item_validation.json', 'native_item_validation.json'),
                            (work / 'native_model_source_validation.json', 'native_model_source_validation.json'),
                            (work / 'emissive_material_validation.json', 'emissive_material_validation.json'),
                            (root / 'build/poem_standalone/validation.json', 'standalone_validation.json'),
                            (root / 'build/poem_standalone/leg_stance_validation.json', 'leg_stance_validation.json')]:
            shutil.copy2(src, reports / f'{version}_{suffix}')
        audited.append(dict(version=version, jar=target.name, sha256=sha(target.read_bytes()), bytes=target.stat().st_size,
                            checks=standalone['checks'], added=added, changed=changed, removed=removed,
                            unchanged_existing_assets=unchanged_assets, epic_fight_code_and_resources_unchanged=True,
                            native_item_vertices=native['vertices'], original_glow_pixels=model_source['glowing_pixels'],
                            emissive_material_verified=True,
                            live_gameplay_tested=False))
        print('POEM_CLEAR_TRAIL_3D_GLOW_JAR_OK', version, standalone['checks'], 'checks')
    shutil.copy2(ROOT / 'build/poem_trail_step/trail_geometry_preview.png', reports / 'trail_geometry_preview.png')
    shutil.copy2(ROOT / 'build/poem_trail_step/native_item_preview.png', reports / 'native_item_preview.png')
    shutil.copy2(ROOT / 'build/poem_trail_step/emissive_shader_validation.json', reports / 'emissive_shader_validation.json')
    shutil.copy2(ROOT / 'build/poem_trail_step/trail_visibility_preview.png', reports / 'trail_visibility_preview.png')
    shutil.copy2(ROOT / 'build/poem_trail_step/trail_visibility_validation.json', reports / 'trail_visibility_validation.json')
    (reports / 'jar_audit.json').write_text(json.dumps(dict(passed=True, created=datetime.now().isoformat(timespec='seconds'), versions=audited), ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    (OUT / '使用说明.md').write_text('''终末之诗：增强刀光、攻击小位移、原生 3D 与自发光修复

适用：Java 版 Minecraft 1.20.1 Forge、1.21.1 NeoForge。

退出游戏后，选择与你的游戏版本对应的 JAR，替换 mods 目录里的旧 Herobrine Companion 本体。同一实例只保留一个本体 JAR。多人游戏请同步更新服务端和客户端。

未安装 Epic Fight 时，主手持有终末之诗并使用普通攻击，即可触发现有四种模式的连招、青白色发光刀光和攻击小位移。刀光跟随实际刀刃，支持第一人称、第三人称和左手持握；按各段挥砍窗口出现。本版加强了青色主体、白色光芯及外围辉光，提高亮背景下的辨识度；拖尾从 0.14 秒延长至最长约 0.22 秒，并放缓淡出。

每个连招段仅在开始挥砍时给予一次小幅水平前冲，由服务端选择时机并通过原版速度包同步。位移遵循原版碰撞；潜行、离地、飞行、游泳、受击或切换武器等情况下不触发。原有攻击伤害和四套动作资源保留。

安装 Epic Fight、未安装 GeckoLib 时，终末之诗使用完整原生 3D 镰刀模型；第一人称、第三人称、掉落物和展示框等场景均可渲染。网格沿用原模型，握持位置、尺寸与朝向已经校准。物品栏继续显示原有 2D 图标。安装 GeckoLib 时沿用原 GeckoLib 渲染器。

独立攻击动作和 Epic Fight 无 GeckoLib 回退模型都已接入独立自发光材质。刀刃、宝石等发光区域按原贴图的 59 组标记生成，发光材质不受环境光和表面朝向影响；其余部位使用普通光照。两个版本各自的原始贴图颜色均保留。发光层与基础模型使用完全一致的顶点和姿态变换。

两个版本均已完成构建、无 Epic Fight / GeckoLib 依赖的动作检查、2098 个刀光采样姿态检查、左右手实际顶点输出检查、16 段单次位移检查、6096 顶点与 7 种显示场景的 3D 模型检查、18288 对基础层/发光层顶点对齐检查、原版着色器光照路径检查和 JAR 内容核对。增强刀光另检查了 32 组亮/暗背景下的顶点、贴图和混合结果。原有动作及 Epic Fight 兼容代码和资源保持不变。

尚未启动 Minecraft 进行实机游玩验证。reports/trail_geometry_preview.png、reports/trail_visibility_preview.png 和 reports/native_item_preview.png 是由实际 Java 顶点数据生成的几何、贴图、亮暗背景检查图，并非游戏截图。完整构建日志和验证报告位于 reports；修改前源码及 JAR 备份位于两个项目各自的 build/poem_trail_step/baseline。
''', encoding='utf-8')
    files = sorted(list(OUT.glob('*.jar')) + [OUT / '使用说明.md'] + list(reports.iterdir()))
    sums = OUT / 'SHA256SUMS.txt'
    sums.write_text(''.join(sha(p.read_bytes()) + '  ' + p.relative_to(OUT).as_posix() + '\n' for p in files), encoding='utf-8')
    archive = OUT / 'Herobrine_Poem_ClearTrail_3DGlow_双版本.zip'
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as jar:
        for path in files + [sums]:
            jar.write(path, path.relative_to(OUT).as_posix())
    with zipfile.ZipFile(archive) as jar:
        assert jar.testzip() is None
    print('POEM_CLEAR_TRAIL_3D_GLOW_PACKAGE_OK', archive)


if __name__ == '__main__':
    main()
