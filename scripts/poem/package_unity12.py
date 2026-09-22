"""Package the leg retarget correction while verifying Unity11 gameplay and V6 preservation."""
from pathlib import Path
from datetime import datetime
from collections import Counter
import hashlib
import json
import shutil
import sys
import zipfile
from PIL import Image, ImageDraw, ImageFont

ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/epicfight'))
from package_unity09 import VERSIONS
from fix_unity_leg_stance import LEGS

OUT=ROOT/'output/Herobrine_Scythe_UnityModes_12_LegFix'
PREVIOUS=ROOT/'output/Herobrine_Scythe_UnityModes_11_Standalone'
REPORTS=OUT/'reports'
WORK=ROOT/'build/poem_legfix'
REPORTS.mkdir(parents=True,exist_ok=True)

def digest(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def load(path):return json.loads(Path(path).read_text('utf-8'))

audit=load(WORK/'reports/leg_stance_regression.json')
assert audit['status']=='passed' and audit['reversed_knee_frames_after']==0
assert audit['resources_per_version']==76 and audit['both_versions_equal']
shutil.copy2(WORK/'reports/leg_stance_regression.json',REPORTS/'leg_stance_regression.json')
shutil.copy2(WORK/'reports/published.json',REPORTS/'changed_animation_resources.json')
versions=[]
for v in VERSIONS:
    root=v['repo'];key='neo' if v['version']=='1.21.1' else 'forge'
    suffix='121' if key=='neo' else '120'
    log=root/'build/poem_legfix'/('final'+suffix+'.log')
    text=log.read_text('utf-8',errors='replace')
    for marker in ['BUILD SUCCESSFUL','POEM_LEG_STANCE_OK:','STANDALONE_POEM_OK:','POEM_INPUT_OK: 72 checks',
                   'POEM_CONTROL_ENGINE_OK: 13 checks','UNITY_MODES_RUNTIME_OK: 72 clips','UNITY_EXTRAS_RUNTIME_OK: 12 clips']:
        assert marker in text,(v['key'],marker)
    if key=='forge':assert 'WOM_LAPSE_OWNERSHIP_PASSED 11 checks, 3 matching writes' in text
    validation=load(root/'build/poem_standalone/validation.json')
    leg_validation=load(root/'build/poem_standalone/leg_stance_validation.json')
    assert validation['leg_stance_regression_passed'] and leg_validation['maximum_entry_sole_opening_degrees']<30
    assert not validation['epic_fight_on_runtime_classpath'] and not validation['geckolib_on_runtime_classpath']
    source=root/'build/libs'/v['source']
    target=OUT/v['delivered'].replace('Unity09','Unity12')
    shutil.copy2(source,target)
    changed={'assets/herobrine_companion/'+row['resource'] for row in load(WORK/'reports/published.json') if row['project']==key}
    previous=PREVIOUS/v['delivered'].replace('Unity09','Unity11')
    with zipfile.ZipFile(target) as jar,zipfile.ZipFile(previous) as old:
        assert jar.testzip() is None
        assert not [n for n,c in Counter(jar.namelist()).items() if c>1]
        for name in changed:
            current=jar.read(name)
            assert current==(root/'src/main/resources'/name).read_bytes(),('Packaged stale animation',name)
            a,b=json.loads(old.read(name)),json.loads(current)
            assert len(a['animation'])==len(b['animation'])
            for before,after in zip(a['animation'],b['animation']):
                assert before['name']==after['name'] and before['time']==after['time']
                if before['name'] not in LEGS:assert before==after,('Changed non-leg channel',name,before['name'])
        preserved_combat=0
        for name in old.namelist():
            if name in changed:continue
            if ('/poem_unity09/' in name or name.startswith('assets/herobrine_companion/epicfight/') or
                name=='data/herobrine_companion/capabilities/weapons/poem_of_the_end.json' or
                name.startswith('assets/herobrine_companion/animations/poem_standalone/')):
                assert jar.read(name)==old.read(name),('Changed combat/rig data',name)
                preserved_combat+=1
        kept_classes=[]
        for name in old.namelist():
            if name.endswith('.class') and name.startswith(v['package']+'/') and any(p in name for p in [
                '/combat/','/client/animation/','/client/render/StandalonePoem','/client/event/StandalonePoem',
                '/client/event/PoemScytheInputEvents','/compat/epicfight/','/network/PoemAnimation','/network/PoemGesture',
                '/mixin/epicfight/','/mixin/client/Poem']):
                assert jar.read(name)==old.read(name),('Changed combat/animation code',name)
                kept_classes.append(name)
        v6=load(root/'build/epicfight-mediapipe/v6_baseline_sha256.json')
        for name,wanted in v6.items():
            file=root/name
            if not file.is_file():file=root/'src/main/resources'/name
            resource=file.relative_to(root/'src/main/resources').as_posix()
            assert digest(file)==wanted==hashlib.sha256(jar.read(resource)).hexdigest(),('V6 changed',resource)
    for name in ['validation.json','leg_stance_validation.json']:
        shutil.copy2(root/'build/poem_standalone'/name,REPORTS/(v['key']+'_'+name))
    shutil.copy2(log,REPORTS/(v['key']+'_build_and_tests.log'))
    versions.append(dict(version=v['key'],jar=target.name,bytes=target.stat().st_size,sha256=digest(target),
                         changed_leg_resources=len(changed),unchanged_combat_resources=preserved_combat,
                         unchanged_combat_and_animation_classes=len(kept_classes),preserved_v6_resources=len(v6),
                         standalone_validation=validation,leg_validation=leg_validation,live_gameplay_tested=False))
    print('UNITY12_JAR_VERIFIED',v['key'],len(changed),'leg-only resources',len(kept_classes),'unchanged classes',flush=True)

media=load(REPORTS/'media_validation.json');assert media['status']=='passed'
mcp=load(REPORTS/'mcp_validation.json');assert mcp['saved_as_normal_project'] and mcp['all_body_parts_checked']
poses=load(REPORTS/'preview_motion_validation.json')
assert len(poses)==356 and all(r['body_error']<.0001 and r['weapon_error']<.00001 for r in poses)
for name in ['entry','normal_contact','thunder_turn','void_turn']:
    assert Image.open(REPORTS/(name+'_before_after.png')).size==(1000,700)

font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',30)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',21)
image=Image.new('RGB',(1000,855),'#141b24');draw=ImageDraw.Draw(image)
draw.text((24,12),'Unity12 腿部修正 · 相同起手帧',font=font,fill='white')
draw.text((24,56),'左：Unity11，膝盖内扣、脚尖外翻     右：修正后，朝前屈伸',font=small,fill='#bdccdf')
image.paste(Image.open(REPORTS/'entry_before_after.png').convert('RGB'),(0,95))
draw.text((24,810),'实际 Java 玩家模型顶点 → Blender MCP 渲染；非游戏截图。',font=small,fill='#bdccdf')
image.save(OUT/'Unity12_腿部修正_前后对照.jpg',quality=95)

readme='''# Unity12：终末之诗腿部方向修正

1.21.1 NeoForge 和 1.20.1 Forge 两个版本均已更新。选对应 JAR 替换 mods 中的旧 Herobrine Companion 本体，同一个实例只保留一个本体 JAR。联机各端一起换成对应的新包。

## 修正内容

原转换程序在接近直腿时，直接把源模型很小的膝盖偏移当作方块人的弯腿方向；改变髋宽、腿长之后，这个方向会落到内侧或后侧。用上一帧来猜膝关节轴的正反，还会使部分帧的大腿和小腿翻转。

现在从原生动作的腿部弯曲平面取方向，在接近伸直时用大腿自身的朝向稳定它，明确膝盖向前屈伸。四套动作共用的起手姿势，实际原版模型的两脚朝向夹角由约 104° 降到约 18°。跨步、抬腿、下蹲及转体动作仍然保留。

修正覆盖四模式的 16 段普攻、准备姿势和完整动作，以及原有疾跑、空中攻击、速斩、挑空、重击的腿部数据。每个版本修改 76 个玩家/Herobrine 动作资源。每个文件只有大腿、小腿和膝盖辅助骨骼的变换发生变化；根位移、上半身、镰刀轨迹、关键帧时间和攻击判定参数均与 Unity11 核对一致。V6 资源保留。

## 功能沿用 Unity11

- 不安装史诗战斗，也可播放四种模式的 16 段普通攻击；皮肤和标准护甲随玩家实际配置。命中、伤害、冷却和实际移动仍走原有原版规则。
- 安装史诗战斗时，继续使用现有四模式战斗、空中/疾跑攻击、三种鼠标组合及长按左键 0.35 秒触发的重击。重击后 0.12 秒衔接、普攻收尾后 0.10 秒衔接下一套的设置保持原值。
- 网络协议仍为 2；本次未改输入、伤害或渲染实现。Forge 的 WOM 崩溃修复保持原样。

## 验证与预览

两版本均完成构建，并在不加载史诗战斗和 GeckoLib 的测试 JVM 中检查了真实原版玩家、细手臂和护甲模型。新增腿部回归覆盖每版 1,586 个普攻姿势、实际脚底顶点及左手镜像；另有 62 个直腿、微小抖动、身体转向和退化方向用例。全部发布动作都检查了膝盖屈伸方向。史诗战斗 72 个模式资源、12 个附加资源、输入和 WOM 回归也通过。

- `Unity12_腿部修正_前后对照.jpg`：左旧右新，同一个起手帧。
- `Unity12_四模式普通攻击_05x.mp4`：四模式各四段普通攻击的 0.5 倍速预览，逐帧标明刀数和动作内时间。回旋抛镰的镜头会随刀刃范围缩放。
- `Unity12_独立玩家动画_顶点验证.blend`：四个动作场景及四个腿部对比场景。通过 Blender MCP 构建、渲染，并核对 356 帧的全部玩家部位和镰刀与 Java 导出的顶点一致。

这些都是离线模型预览，并非游戏实录。本次未启动、重启或操作 Minecraft，尚未做实机游玩测试。Unity10、Unity11 交付目录未覆盖。
'''
(OUT/'使用说明.md').write_text(readme,'utf-8')
result=dict(status='passed',created=datetime.now().isoformat(timespec='seconds'),release='Unity12',
            scope='Correct anatomical knee poles and leg orientation; retain existing combat and upper-body motion',
            versions=versions,media=media,blender_frames_verified=len(poses),live_gameplay_tested=False)
(REPORTS/'unity12_delivery_validation.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n','utf-8')
files=sorted([p for p in OUT.iterdir() if p.is_file() and p.suffix.lower() in {'.jar','.md','.mp4','.jpg','.blend'}]
             +[p for p in REPORTS.rglob('*') if p.is_file()])
hashes=OUT/'SHA256SUMS.txt'
hashes.write_text(''.join(digest(p)+'  '+p.relative_to(OUT).as_posix()+'\n' for p in files),'utf-8')
archive=OUT/'Herobrine_Scythe_Unity12_双版本腿部修正版.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED,compresslevel=6,allowZip64=True) as z:
    for file in files+[hashes]:z.write(file,file.relative_to(OUT).as_posix())
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    for line in z.read('SHA256SUMS.txt').decode('utf-8').splitlines():
        wanted,name=line.split('  ',1);assert hashlib.sha256(z.read(name)).hexdigest()==wanted,name
archive.with_suffix('.zip.sha256').write_text(digest(archive)+'  '+archive.name+'\n','utf-8')
print('UNITY12_PACKAGE_COMPLETE',archive,archive.stat().st_size,flush=True)
