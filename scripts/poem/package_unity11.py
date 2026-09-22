"""Package the independent-player-animation release and verify the retained Unity10/V6 paths."""
from pathlib import Path
from datetime import datetime
from collections import Counter
import hashlib
import json
import shutil
import struct
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/epicfight'))
from package_unity09 import VERSIONS

OUT=ROOT/'output/Herobrine_Scythe_UnityModes_11_Standalone'
PREVIOUS=ROOT/'output/Herobrine_Scythe_UnityModes_10_EpicFight'
REPORTS=OUT/'reports'
REPORTS.mkdir(parents=True,exist_ok=True)

def sha(data):return hashlib.sha256(data).hexdigest()
def digest(path):return sha(Path(path).read_bytes())

versions=[]
for version in VERSIONS:
    root=version['repo'];suffix='121' if version['version']=='1.21.1' else '120'
    log=root/'build/poem_standalone'/f'final{suffix}.log'
    text=log.read_text('utf-8',errors='replace')
    for marker in ['BUILD SUCCESSFUL','STANDALONE_POEM_OK:','POEM_INPUT_OK: 72 checks','POEM_CONTROL_ENGINE_OK: 13 checks','UNITY_MODES_RUNTIME_OK: 72 clips','UNITY_EXTRAS_RUNTIME_OK: 12 clips']:
        assert marker in text,(version['key'],marker)
    if suffix=='120':assert 'WOM_LAPSE_OWNERSHIP_PASSED 11 checks, 3 matching writes' in text
    validation=json.loads((root/'build/poem_standalone/validation.json').read_text('utf-8'))
    assert validation['status']=='passed' and validation['ordinary_clips']==16
    assert not validation['epic_fight_on_runtime_classpath'] and not validation['geckolib_on_runtime_classpath']
    source=root/'build/libs'/version['source']
    destination=OUT/version['delivered'].replace('Unity09','Unity11')
    shutil.copy2(source,destination)
    previous=PREVIOUS/version['delivered'].replace('Unity09','Unity10')
    with zipfile.ZipFile(destination) as jar,zipfile.ZipFile(previous) as old:
        assert not [n for n,c in Counter(jar.namelist()).items() if c>1]
        assert jar.testzip() is None
        unchanged=[]
        for file in (PREVIOUS/'resources').rglob('*'):
            if not file.is_file():continue
            name=file.relative_to(PREVIOUS/'resources').as_posix()
            assert sha(jar.read(name))==digest(file)==digest(root/'src/main/resources'/name),(version['key'],name)
            unchanged.append(name)
        assert len(unchanged)==170
        baseline=json.loads((root/'build/epicfight-mediapipe/v6_baseline_sha256.json').read_text('utf-8'))
        for name,wanted in baseline.items():
            file=root/name
            if not file.is_file():file=root/'src/main/resources'/name
            resource=file.relative_to(root/'src/main/resources').as_posix()
            assert digest(file)==wanted==sha(jar.read(resource)),('V6',resource)
        kept_classes=[]
        prefixes=['client/event/PoemScytheInputEvents','combat/PoemAttackInput','combat/PoemGestureQueue',
                  'compat/epicfight/UnityScythe','compat/epicfight/PoemGestureController',
                  'compat/epicfight/PoemScythePlayerAnimations','mixin/epicfight/PoemAttackControlMixin']
        if suffix=='120':prefixes+=['compat/epicfight/WomAntitheusLapseCompat','mixin/epicfight/WomAntitheusLapseMixin']
        for name in old.namelist():
            if name.endswith('.class') and any(name.startswith(version['package']+'/'+p) for p in prefixes):
                assert jar.read(name)==old.read(name),('Retained EF implementation changed',name)
                kept_classes.append(name)
        new_classes=[]
        for name in jar.namelist():
            if name.endswith('.class') and name.startswith(version['package']+'/') and any(part in name for part in [
                '/combat/poem/','/client/animation/StandalonePoem','/client/animation/PoemSkinMesh','/client/render/StandalonePoem',
                '/client/event/StandalonePoem','/network/PoemAnimation','/mixin/client/Poem']):
                assert struct.unpack('>H',jar.read(name)[6:8])[0]==version['major'],name
                new_classes.append(name)
        assert len(new_classes)>=24
        config=json.loads(jar.read('herobrine_companion.mixins.json'))
        for name in ['PoemLivingRendererMixin','PoemHumanoidRenderMixin','PoemHeldItemMixin']:
            assert 'client.'+name in config['client'] and 'client.'+name not in config['mixins']
        assert 'epicfight.PoemAttackControlMixin' in config['client']
        if suffix=='120':
            assert 'epicfight.WomAntitheusLapseMixin' in config['mixins']
            refmap=json.loads(jar.read(config['refmap']))
            for name in ['PoemLivingRendererMixin','PoemHumanoidRenderMixin','PoemHeldItemMixin']:
                assert version['package']+'/mixin/client/'+name in refmap['mappings']
        extra={}
        for file in (root/'src/main/resources/assets/herobrine_companion/animations/poem_standalone').glob('*.json'):
            resource=file.relative_to(root/'src/main/resources').as_posix()
            assert sha(jar.read(resource))==digest(file)
            extra[resource]=digest(file)
        for lang in ['zh_cn','en_us']:
            resource='assets/herobrine_companion/lang/'+lang+'.json'
            actual=json.loads(jar.read(resource));expected=json.loads((root/'src/main/resources'/resource).read_text('utf-8'))
            for key in ['standalone.input','standalone.rules','epicfight.input']:
                name='item.herobrine_companion.poem_of_the_end.'+key
                assert actual[name]==expected[name] and actual[name]
        versions.append(dict(version=version['key'],jar=destination.name,bytes=destination.stat().st_size,sha256=digest(destination),
            unchanged_unity10_resources=len(unchanged),unchanged_epicfight_classes=len(kept_classes),preserved_v6_resources=len(baseline),
            independent_classes=len(new_classes),java_class_major=version['major'],independent_validation=validation,
            new_resource_hashes=extra,network_protocol='2',live_gameplay_tested=False))
    shutil.copy2(log,REPORTS/(version['key']+'_build_and_tests.log'))
    shutil.copy2(root/'build/poem_standalone/validation.json',REPORTS/(version['key']+'_standalone_validation.json'))
    for name in ['unity_modes_runtime_validation.json','unity_extras_runtime_validation.json']:
        shutil.copy2(root/'build/scythe_unity_pack_09'/name,REPORTS/(version['key']+'_'+name))
    if suffix=='120':shutil.copy2(root/'build/scythe_unity_pack_09/wom_lapse_ownership_validation.json',REPORTS/'forge_wom_crash_regression.json')
    print('UNITY11_JAR_VERIFIED',version['key'],destination.stat().st_size,flush=True)

media=json.loads((REPORTS/'media_validation.json').read_text('utf-8'));assert media['status']=='passed'
mcp=json.loads((REPORTS/'mcp_validation.json').read_text('utf-8'));assert mcp['saved_as_normal_project'] and len(mcp['scenes'])==4
poses=json.loads((REPORTS/'preview_motion_validation.json').read_text('utf-8'))
assert all(row['weapon_error']<.00001 and row['body_error']<.00003 for row in poses)
readme='''# Unity11：终末之诗四模式普通攻击独立动画

1.21.1 NeoForge 与 1.20.1 Forge 均支持。不需要安装史诗战斗、PlayerAnimator 或 GeckoLib 来播放这四套普通攻击动画。

## 安装与使用

- 按游戏版本选择一个 JAR，替换 mods 中旧的 Herobrine Companion 本体。同一实例只保留一个本体 JAR。
- 联机时，服务端与所有客户端一起更新到本次对应版本。同步协议已更新为 2，新旧包会在连接时检查版本。
- 主手持有终末之诗，使用现有的 Shift + 右键切换普通、破境、鸣雷、碎空。连续点按攻击键播放当前模式的四段普攻，短时间内的下一次点按可缓存衔接。
- 第三人称显示全身转动、屈肘屈膝和挥镰；第一人称显示对应手臂与镰刀动作。皮肤来自当前玩家，支持宽手臂、细手臂及左手持物设置。标准玩家护甲、皮肤外层和背部装饰跟随动作。
- 附近安装本次版本的玩家可看到动画。切武器、切模式、受伤、死亡、坐骑、游泳、鞘翅滑翔及使用物品会退出独立攻击动画。右键技能和挖方块不会被识别为普通连招。

## 本次范围

独立层移植的是四种模式各四段普通攻击，共 16 段。无史诗战斗时，命中时机、伤害、冷却和实际移动沿用原有原版战斗规则；不会按动画刀刃另加伤害，也不会强制推进或旋转玩家视角。移除水平根位移，保留源动作的上下起伏与腿部姿势；行走由玩家控制。

装有史诗战斗时自动使用既有的 Unity10 战斗实现：长按攻击 0.35 秒重击、三个组合键、疾跑和空中攻击继续有效。重击接普攻的 0.12 秒窗口、普攻收尾接下一套的 0.10 秒窗口，以及 V6 资源和 Forge 的 WOM 崩溃修复均保留。独立层不额外实现这些重击/附加招式。

## 校验与预览

- 两版本均完成构建。将史诗战斗和 GeckoLib 从测试 JVM 的运行类路径移除后，执行了实际 16 段动作的采样、原版玩家/细手臂/护甲模型顶点渲染、皮肤 UV 与隐藏部位检查、左手镜像、连招缓存、网络包编解码和渲染注入点检查。
- 既有史诗战斗的 72 段模式资源、12 段附加动作、72 项输入状态检查、13 项输入接口检查及 Forge WOM 修复回归均通过。
- 170 个 Unity10 动作/配置资源和各版本 26 个 V6 基线资源与旧交付包及新 JAR 核对一致，关键史诗战斗实现类亦逐字节核对一致。
- `Unity11_四模式普通攻击_05x.mp4`：Java 播放层导出的实际模型顶点与镰刀关节，在 Blender 中以 0.5 倍速对照。每个区域标明当前模式、刀数和动作内时间；较短动作结束后标明定格。视频是分段动作检查，不是游戏实录，也不展示原版伤害判定。
- `Unity11_独立玩家动画_顶点验证.blend`：四场景的独立渲染检查工程。材质已打包，通过 Blender MCP 打开、渲染和核查。预览使用示例 Herobrine 皮肤，游戏里使用玩家自己的皮肤。

未启动或修改正在运行的 Minecraft，也未做实机第一人称或双人联机游玩测试。其他模组自定义的非标准玩家/护甲渲染器需要在实际整合包中确认。

原 Unity10 交付目录保持原样。本次的完整检查结果与哈希见 reports 和 SHA256SUMS.txt。
'''
(OUT/'使用说明.md').write_text(readme,'utf-8')
report=dict(status='passed',created=datetime.now().isoformat(timespec='seconds'),release='Unity11',scope='Four ordinary combo animation sets without Epic Fight; vanilla combat rules retained',
            versions=versions,blender_project_sha256=digest(OUT/'Unity11_独立玩家动画_顶点验证.blend'),media=media,live_gameplay_tested=False)
(REPORTS/'unity11_delivery_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
files=sorted([p for p in OUT.iterdir() if p.is_file() and p.suffix.lower() in {'.jar','.md','.mp4','.jpg','.blend'}]+[p for p in REPORTS.rglob('*') if p.is_file()])
hashes=OUT/'SHA256SUMS.txt';hashes.write_text(''.join(digest(p)+'  '+p.relative_to(OUT).as_posix()+'\n' for p in files),'utf-8')
archive=OUT/'Herobrine_Scythe_Unity11_双版本交付包.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as z:
    for file in files+[hashes]:z.write(file,file.relative_to(OUT).as_posix())
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
archive.with_suffix('.zip.sha256').write_text(digest(archive)+'  '+archive.name+'\n','utf-8')
print('UNITY11_PACKAGE_COMPLETE',archive,archive.stat().st_size,flush=True)
