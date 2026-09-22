"""Write the release notes and review sheet from old/new delivered timelines."""
import json
from pathlib import Path
import shutil

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OLD = ROOT / 'output/Herobrine_Scythe_UnityModes_09_EpicFight'
OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
load = lambda p: json.loads(p.read_text('utf-8'))
rel = 'resources/assets/herobrine_companion/epicfight'
old_modes, modes = [load(p / rel / 'poem_unity09_timing.json')['modes'] for p in (OLD, OUT)]
old_extras, extras = [load(p / rel / 'poem_unity09_extras.json')['moves'] for p in (OLD, OUT)]
changes = []
for old, new in zip(old_modes, modes):
    assert old['segments'][:3] == new['segments'][:3] and old['specials'] == new['specials']
    a, b = old['segments'][-1], new['segments'][-1]
    assert a['contacts'] == b['contacts']
    changes.append(dict(attack=new['label'] + '普攻末段', old_source=a['source_clip'], new_source=b['source_clip'],
                        old_recovery=a['recovery'], new_recovery=b['recovery'],
                        old_wait=a['recovery']-a['contacts'][-1]['end'], new_wait=b['recovery']-b['contacts'][-1]['end'],
                        old_duration=a['duration'], new_duration=b['duration']))
assert old_extras[:2] == extras[:2]
for kind, label in [('ground', '地面重击'), ('air', '空中重击')]:
    a, b = old_extras[2][kind], extras[2][kind]
    changes.append(dict(attack=label, old_source=a['source_clip'], new_source=b['source_clip'],
                        old_recovery=a['recovery'], new_recovery=b['recovery'],
                        old_wait=a['recovery']-a['contacts'][-1]['end'], new_wait=b['recovery']-b['contacts'][-1]['end'],
                        old_duration=a['duration'], new_duration=b['duration']))
(OUT / 'reports/recovery_changes.json').write_text(json.dumps(dict(changes=changes, gameplay_tested=False,
    ground_combo_contact_windows_preserved=True, dash_air_and_flurry_uppercut_preserved=True,
    heavy_hold_milliseconds=350, input_checks_per_version=72, control_engine_checks_per_version=13), ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
table = '\n'.join(f'| {r["attack"]} | {r["old_wait"]:.3f} 秒 | {r["new_wait"]:.3f} 秒 |' for r in changes)
doc = '''终末之诗 Unity10：长按重击与连招衔接

地面重击已从 Skill_04 更换为 Combo_Attack_05_01 的踏步挥斩，保留前 52 个素材帧。动作维持原速，带约 2 格的整体位移。四套普攻只裁去末段最后命中后的长收势，前面三段、转身、脚步和全部命中时段保留。

**默认操作**

主手持终末之诗，进入 Epic Fight 战斗模式。按键跟随 Epic Fight 的攻击键设置，默认左键。

| 输入 | 结果 |
| --- | --- |
| 短按左键并松开 | 一次普通攻击，使用 Epic Fight 原有的连招和预输入机制 |
| 长按左键 0.35 秒 | 自动触发一次重击；持续按住不重复，松开不会补打一刀 |
| 鼠标侧键 4＋左键 | 速斩 |
| 鼠标侧键 5＋左键 | 挑空 |
| 右键＋左键 | 重击，与长按左键使用相同动作 |
| Shift＋右键 | 原有的四种模式切换 |

三种组合同时支持。先按住组合键，再按攻击键；组合键可在设置中修改。右键单独使用时，在松开后执行原有交互；参与组合后不会再补发右键技能。右键作为组合键时会暂缓同键格挡，避免抢走重击。旧的“独立重击键”已移除。

短按在松开时判断为普攻，长按达到阈值时判断为重击。重击前不会先插入一次普通攻击。切槽、换模式、打开界面、失去窗口焦点、暂停、死亡或受击会取消待处理的按住输入。

**接招等待的变化**

下表是“末次命中结束 → 允许下一次普通攻击”的等待时间。

| 动作 | Unity09 | Unity10 |
| --- | --- | --- |
''' + table + '''

接上的下一刀仍有自己的起手和约 0.10 秒的姿势过渡。游戏以 tick 更新，实际响应也受按键时机和网络影响。表中时间已经通过两个版本的真实 Epic Fight 状态查询验证，包括命中期间不能取消、接招窗口之后可以普攻。

重击伤害系数 1.5、冲击系数 1.7。地面重击使用新的踏步挥斩；空中仍使用下劈落地动作，收势已缩短。空中重击按真实地形下降，命中前等待接近地面，上限为 60 tick，预览中的素材时长不包括这段可变等待。

**安装两个版本**

分别用下面对应的 JAR 替换各实例里的旧 Herobrine Companion JAR。每个实例只保留一个版本。

- `Herobrine_Companion-0.38-Unity10-1.21.1-NeoForge.jar`：Minecraft 1.21.1 / NeoForge / Java 21；检查使用 Epic Fight 21.17.2、WOM 2.0.176。
- `Herobrine_Companion-0.38-Unity10-1.20.1-Forge.jar`：Minecraft 1.20.1 / Forge / Java 17；检查使用 Epic Fight 20.14.17、WOM 2.0.171。

两个开发工程源码也已更新。Unity09 交付文件继续保留。各工程的 26 个 V6 基线文件保持原 SHA-256；当前四种模式沿用上一版的四套 Unity 来源。

| 模式 | 地面连击 | 疾跑攻击 | 空中攻击 |
| --- | --- | --- | --- |
| 普通 | Combo_Attack_01_All | Run_Attack_01 | Combo_Attack_Air_01 |
| 破境 | Combo_Attack_02_All | Skill_01 | Combo_Attack_Air_02 |
| 鸣雷 | Combo_Attack_03_All | Dash_Air_Attack | Combo_Attack_Air_03 |
| 碎空 | Combo_Attack_04_All | Run_Attack_02 | Combo_Attack_Air_04 |

**预览与工程**

- `Unity10_chaining_05x.mp4`：四种模式的重击接普攻、末段接下一套。采用交付姿势和 0.10 秒混合的 Blender 衔接示意，非游戏实录。
- `UnityModes10_comparison_05x.mp4`：四种模式与 Unity 原素材的慢放对照。
- `Unity10_extra_attacks_05x.mp4`：速斩、挑空、长按重击的地面和空中素材对照。
- 同名不带 `_05x` 的视频为原速。字幕区分命中、起手与收势、可接下一击，不把整个非命中段称为可衔接。
- `Herobrine_终末之诗_Unity10_四模式与重击.blend`：42 个可编辑 Action、20 根骨骼、打包贴图。默认打开新重击，并标出命中与接普攻时刻。
- `resources`：玩家和 Herobrine 共 84 个动作、84 个显示配置、2 个时间轴。资源 ID 保留 poem_unity09 以兼容已有注册。
- `reports`：两版构建、输入、接招、矩阵、Blender MCP 和资源哈希报告。

**验证范围**

两个版本均构建成功，各通过 72 项输入检查及 13 项实际 Epic Fight 输入接口检查；对 72 个四模式资源和 12 个额外资源完成实际引擎加载与 480 Hz 姿势、整体位移检查。重击和普攻收势时刻通过引擎状态验证。Blender 工程的 42 个 Action 与资源矩阵一致。

上一版的挑空时间校验修复和 Forge WOM Antitheus 回调修复均保留；WOM 的 11 项回归检查通过，详见 `崩溃日志分析与修复.md`。

尚未进入 Minecraft 实际对战测试。短暂交叉换握仍可能有方块肢体穿插；Blender 衔接视频用于审阅姿势和接招窗口，不能代替实机手感与网络验证。
'''
(OUT / '使用说明.md').write_text(doc, encoding='utf-8')
crashes = (OLD / '崩溃日志分析与修复.md').read_text('utf-8').replace('49 项检查', '72 项检查')
crashes = 'Unity10 保留以下两项修复，并重新通过双版本动画检查及 Forge WOM 的 11 项回归检查。\n\n' + crashes
(OUT / '崩溃日志分析与修复.md').write_text(crashes, encoding='utf-8')

chains = load(OUT / 'reports/chain_preview_timeline.json')['chains']
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 23)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 18)
sheet = Image.new('RGB', (1920, 1070), '#121e2b')
d = ImageDraw.Draw(sheet)
d.text((18, 12), 'Unity10 · 四模式衔接姿势检查（Blender 示意）', font=font, fill='white')
labels = ['重击收势', '接普攻过渡', '接入第一刀', '普攻末段收势', '下一套过渡', '下一套第一刀']
for col, label in enumerate(labels):
    d.text((col*320+14, 50), label, font=small, fill='#69e0cf')
for row, mode in enumerate(modes):
    y = 80 + row*245
    for group, kind in enumerate(('heavy_to_light', 'combo_restart')):
        chain = next(c for c in chains if c['mode'] == mode['key'] and c['kind'] == kind)
        contact = mode['segments'][0]['contacts'][0]
        times = [chain['switch_seconds'], chain['switch_seconds']+.05,
                 chain['switch_seconds']+.10+(contact['start']+contact['end'])/2]
        for stage, time in enumerate(times):
            frame = min(len(chain['frames'])-1, round(time*60))
            path = OUT / 'preview/chains_60' / mode['key'] / kind / f'{frame:04d}.png'
            im = Image.open(path).convert('RGB').resize((320, 200))
            x = (group*3+stage)*320
            sheet.paste(im, (x, y))
            d.text((x+12, y+208), mode['label'] + f'  /  {time:.2f}s', font=small, fill='#c7d8e8')
sheet.save(OUT / 'Unity10_chaining_overview.jpg', quality=95)
print('UNITY10_NOTES_AND_REVIEW_READY')
for row in changes:
    print(row['attack'], round(row['old_wait'], 4), '->', round(row['new_wait'], 4))
