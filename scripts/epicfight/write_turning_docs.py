"""Document only the measured Turn07 result and preserve the V6 modes."""
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
load=lambda p:json.loads(p.read_text(encoding='utf-8'))
validation=load(ROOT/'build/epicfight-mediapipe/validation_report.json')
timing=load(ROOT/'src/main/resources/assets/herobrine_companion/epicfight/poem_mediapipe_timing.json')
assert timing['combat_edit']=='mediapipe_turning_07'
assert validation['full_body_turns_verified'] and validation['ordinary_attacks_always_grounded']
rows=[]
for s,m in zip(timing['segments'],validation['attack_metrics']):
    rows.append(f"| {s['name'][-2:]} | {s['label']} | {s['first_frame']}–{s['last_frame']} | {s['duration']:.3f} 秒 | {s['travel_blocks']:.3f} 格 |")
text=f'''# 终末之诗 Turn07：转体、跨步与刃向修正

包含 Minecraft 1.21.1 NeoForge 和 1.20.1 Forge 两版。第三刀开始有完整的身体转向，髋部、胸口和头部经过背身再转回；后续分别采用斜抡、跨步下沉、绕镰上挑、提膝回环和低身扫镰。脚步增加前跨、侧跨和支撑脚周围的重心移动。

根据用户在 Blender 中的逐帧检查，第 **75–365 帧**的刀刃相对被检查版本翻转 180°，在第 61–75 帧完成平滑过渡。右手仍握柄尾，左手握中段。完整八刀实际向前移动 {sum(s['travel_blocks'] for s in timing['segments']):.3f} 格，并包含侧向位移。

| 刀次 | 动作 | Blender 帧 | 时长 | 向前位移 |
| --- | --- | --- | --- | --- |
{chr(10).join(rows)}

## 模式与安装

玩家普通（0）和鸣雷（2）使用 Turn07；**破境（1）和碎空（3）继续使用原 V6**，包括连招、冲刺、空中动作、时序和各版本原有速度。Herobrine 持终末之诗使用八段新动作。

退出相应游戏实例后，用对应版本的 Turn07 JAR 替换旧 Herobrine Companion JAR。进入 Epic Fight 战斗模式持终末之诗；Shift + 右键切换模式。每个实例只保留一个本模组 JAR。

## 查看动作

`Turn07_front.mp4`：正常游戏速度；`Turn07_front_05x.mp4`：0.5 倍速。

`Turn07_side.mp4`：固定世界镜头，可检查实际前进、侧移和脚步落点；地砖边长为 0.5 格。

`Turn07_comparison_05x.mp4`：参考视频与游戏骨架按源帧同步，右侧以 0.5 倍速播放。动画经过重定时，因此左侧速度随片段对应关系变化。画面分别标出原视频时间和 Blender 帧／游戏动作时间；字幕来自最终动作分段。

`Turn07_third_attack.jpg`：第三刀的连续关键姿态；`Game_Mesh_Turn07.jpg`：八刀逐段检查图。

## 来源与验证

沿用 Google MediaPipe 0.10.21 Pose Heavy / GHUM 对 0.5 倍速视频的捕捉结果。通过逐帧观察补全遮挡时丢失的完整转身，并编辑武器刃向、三维挥砍平面、跨步落点和节奏，再通过 Blender MCP 烘焙至 Herobrine 方块骨架。这是经编辑的单目动捕重定向，不是原作者的三维工程。

完成两版构建与 Epic Fight 本身的资源加载、旋转插值、Root 位移和模式分发检查。导出网格检查覆盖完整身体转向、去除根旋转后的动作差异、双手握柄、骨长、脚底和刀刃离地。两个版本各 26 个 V6 资源的 SHA-256 保持不变。尚未在实际游戏世界中进行实机攻击测试。

双手最大握点误差 {max(validation['right_grip_max_error_blocks'],validation['left_grip_max_error_blocks']):.5f} 格；支撑脚最大滑移 {validation['maximum_planted_foot_drift_blocks']:.5f} 格。新的腿部求解保持脚底接触，导出不需要整体抬高角色。

复现：`retarget_turning.py` → 通过 Blender MCP 执行 `bake_turning.py` → `export_mediapipe.py` → `verify_mediapipe.py` → `sync_turning_1201.py` → 两版 `verifyMediaPipe build` → `encode_turning.py` → `package_turning.py`。用户检查过的原刃向保存在 `build/scythe_turn_07/reviewed_blade_before.json`。

参考：[Pirate_Gn《镰刀连击》](https://www.bilibili.com/video/BV1JH4y1r7po/)。原视频归原作者，未包含在模组 JAR 中；动作不标为 CC0。完整校验信息在 `reports/`。
'''
for root in (ROOT,ROOT.parent/'herobrine companion'):
    for name in ('epicfight-poem-turn07.md','epicfight-poem-mediapipe.md'):
        (root/'docs'/name).write_text(text,encoding='utf-8')
license_text='''Herobrine / Poem of the End - MediaPipe scythe animation, Turn07

Visual reference: Pirate_Gn, 镰刀连击
https://www.bilibili.com/video/BV1JH4y1r7po/

Motion source: Google MediaPipe 0.10.21 Pose Heavy / GHUM on 0.5x footage.
This edit reuses the existing reviewed capture; it is not a new capture run.
Explicit source-frame correspondence is retained. Single-view occlusion loses
complete turns: the pelvis, chest and head headings are unwrapped through
visual review. Each cut has its own blade plane, body compression, hand height
and pivot/stepping pattern. Wide forward and lateral footfalls are authored
for the block skeleton. Blade direction from Blender frame 75 through 365 is
flipped 180 degrees against the user-reviewed version, with a smooth lead-in.
Both hands stay attached to the same shaft. The body, weapon and all footfalls
are baked through Blender MCP and exported to the Epic Fight biped.

These edited depth, heading, blade and movement curves are combat adaptations,
not exact recovery of the author's original 3D animation. Ordinary attacks do
not include the later pole-vault and aerial acrobatic sequence.

Resources: player/poem_mediapipe/* and hero/poem_mediapipe/*
Authoring: scripts/mocap/retarget_turning.py
Conversion: scripts/epicfight/export_mediapipe.py

The reference video belongs to its original author. This notice does not grant
rights to that video or describe the derived motion as CC0. The reference video
is not distributed in this mod. Existing character, weapon, texture and Epic
Fight assets retain their respective licenses.

The player/poem_v6/* resources remain unchanged for Realm Breaker and Void
Shatter, including their original per-version animation speeds.
'''
(ROOT/'src/main/resources/META-INF/licenses/herobrine_scythe_mediapipe.txt').write_text(license_text,encoding='utf-8')
print('TURN07_DOCS_COMPLETE')
