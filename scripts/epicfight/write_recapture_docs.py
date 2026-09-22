"""Write version-specific usage notes from the final capture/conversion reports."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
load=lambda p:json.loads(p.read_text(encoding='utf-8'))
motion=load(ROOT/'output/Herobrine_Scythe_Recapture_05/capture/target_motion.json')
conversion=load(ROOT/'build/epicfight-mediapipe/conversion_report.json')
assert conversion['combat_edit']=='mediapipe_recapture_05'
rows=[]
for i,s in enumerate(conversion['segments'],1):
    ref=s['reference_frames']
    rows.append(f"| {i} | {s['label']} | {(ref[0]-1)/30:.2f}–{(ref[1]-1)/30:.2f} 秒 | {s['duration']:.3f} 秒 | {s['travel_blocks']:.2f} 格 |")
table='\n'.join(rows)
for version,project,filename,loader,java,special in [
    ('1.21.1',ROOT,'epicfight-poem-mediapipe.md','NeoForge 21.1.219 / Epic Fight 文件 8080214','21','2'),
    ('1.20.1',ROOT.parent/'herobrine companion','epicfight-poem-1201.md','Forge 47.4.16 / Epic Fight 20.14.17（文件 8049910）','17','4/3'),
]:
    text=f'''# 终末之诗：半速视频重新捕捉的八式进步镰斩

适用 Minecraft {version}，{loader}，Java {java}。
本次版本为 `mediapipe_recapture_05`。Google MediaPipe 0.10.21 GHUM Heavy 对 0.5 倍速视频重新处理了 1037 帧；八刀分别取自不同的捕捉区间，保留各段的转胯、沉身、屈膝和换步。镰柄由 OpenCV 线段跟踪并逐段核对刀头，避免将柄尾金色装饰认作刀头。

| 使用者 / 模式 | 动作 |
| --- | --- |
| Herobrine 主手持终末之诗 | 新捕捉的八刀连招 |
| 玩家普通（0） | 新捕捉动作 |
| 玩家破境（1） | 原 V6 |
| 玩家鸣雷（2） | 新捕捉动作 |
| 玩家碎空（3） | 新捕捉动作 |

左手握镰柄中段、右手握柄尾，第一刀从角色自身右上向前斩至左下。普通连招排除开头绕镰准备和后面的脱手、撑镰倒立、绕杆腾转及踢腿段。连续八刀共 {motion['duration_seconds']:.3f} 秒，累计前进 {sum(s['travel_blocks'] for s in conversion['segments']):.2f} 格；实际攻击判定窗口为约 0.15–0.20 秒。玩家冲刺攻击采用突进段，空中攻击采用第一段，空中片段不额外写入地面推进。

| 刀序 | 动作与步法 | 原视频区间 | 游戏时长 | 前进量 |
| --- | --- | --- | --- | --- |
{table}

## 游戏使用

以本版本 JAR 替换旧版 Herobrine Companion，然后重启游戏。玩家进入 Epic Fight 战斗模式，主手持终末之诗；Shift + 右键切换普通、破境、鸣雷、碎空。模式切换会清零连段编号并刷新持镰姿势。Herobrine 在战斗模式下主手装备终末之诗后使用新连招。

本工程破境保留 V6 的 18 段普通连招及持镰姿势，普通连招速度 4 倍，冲刺与空中攻击速度 {special} 倍。原 V6 的阶段、判定及 26 个资源文件按 SHA-256 基线核对。

## 脚步与位移

普通攻击使用 `MediaPipeScytheAttackAnimation`。Epic Fight 普通连招原有的上身遮罩会去掉腿部骨骼，且移动连招设置能关闭动作位移；本类让这套攻击保留全身动画、攻击状态和 Root 推进。Root 通过 `RAW_COORD` 与固定动作距离移动实体，仍使用游戏自身的移动、碰撞和同步机制。支撑脚保持落点，迈步脚抬起换位，重定向到实际 BIPED 后再次求解脚底。

玩家和 Herobrine 各用自己的骨架与相同捕捉资源。两版本的 30 个新资源文件逐文件一致，1.20.1 保留自己的 V6 特殊攻击速度和渲染器适配。

## 预览与验证

交付包中的 `Recapture05_front.mp4` 是正常速度近景；`Recapture05_side.mp4` 使用固定世界机位展示前进距离；`Recapture05_reference_comparison_half_speed.mp4` 按同一源帧并排展示本轮 MediaPipe 骨架和游戏网格，以半速检查。预览由实际 Epic Fight BIPED、终末之诗网格和导出的动画资源重建。

独立验证覆盖双手握点、骨长、脚底落点与滑动、地面间隙、动作接缝，24 个新动画的 Epic Fight 加载及 960 Hz 插值，20 条 Root 轨迹的 20 Hz 推进量，47 个注册槽位和四模式分发。原有 23 个 V6 动画及 528 项判定检查一并运行。报告保存在 `build/epicfight-mediapipe/`、`build/epicfight-v6/` 和交付包 `reports/`。

这些是离线网格、引擎检查与构建验证，尚未在实际游戏世界中测试。音效、拖尾和完整武器能力工厂仍依赖游戏生命周期。

## 动作源工程

新 Blender 工程位于 1.21.1 工程的 `output/Herobrine_Scythe_Recapture_05/Herobrine_半速重捕_八式进步镰斩.blend`，经独立 Blender MCP 会话烘焙，原工程保留。复现顺序为 `scripts/mocap/recapture_half_speed.py` → `select_recapture.py` → `retarget_distinct_capture.py` → `polish_recapture.py` → `ensure_recapture_support.py` → 经 Blender MCP 运行 `bake_recapture.py` → `scripts/epicfight/export_mediapipe.py` → `verify_mediapipe.py`。游戏骨架另做双手与脚底 IK，肘部弯曲方向连续传递，武器过渡围绕右手握点计算；导出采样保留快速挥砍的帧间过渡。

参考 [Pirate_Gn《镰刀连击》](https://www.bilibili.com/video/BV1JH4y1r7po/)。单目画面存在深度与遮挡歧义，朝向、握持和真实世界推进量经过战斗用途适配；半速播放也不会增加原视频的图像细节。本动作不作为作者原始三维动画或 CC0 资源分发。来源说明随模组保存在 `META-INF/licenses/herobrine_scythe_mediapipe.txt`。
'''
    (project/'docs'/filename).write_text(text,encoding='utf-8')
print('RECAPTURE_DOCS_WRITTEN',motion['duration_seconds'],len(motion['poses']))
