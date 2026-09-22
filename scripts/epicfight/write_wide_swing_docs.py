"""Write usage and measured results only after the exported mesh checks pass."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
validation = json.loads((ROOT/'build/epicfight-mediapipe/validation_report.json').read_text(encoding='utf-8'))
timing = json.loads((ROOT/'src/main/resources/assets/herobrine_companion/epicfight/poem_mediapipe_timing.json').read_text(encoding='utf-8'))
assert timing['combat_edit'] == 'mediapipe_wide_swing_06'
old = json.loads((ROOT/'output/Herobrine_Scythe_Wide_06/capture/wide_swing_report.json').read_text(encoding='utf-8'))
rows = []
for segment, metric, before in zip(timing['segments'], validation['attack_metrics'], old['attack_metrics']):
    rows.append(f"| {segment['label']} | {before['before_span_degrees']:.0f}° | {metric['shaft_direction_span_degrees']:.0f}° | {metric['sweep_path_degrees']:.0f}° | {segment['duration']:.3f} 秒 |")
text = f'''# 终末之诗 Wide06：大幅镰斩，碎空保留 v6

提供 Minecraft 1.21.1 NeoForge 和 1.20.1 Forge 两个 JAR。两版新动作资源一致，分别保留各自原来的 v6 动作、时序、判定和播放速度。

| 使用者／模式 | 动作 |
| --- | --- |
| Herobrine 持终末之诗 | 新八刀大幅镰斩 |
| 玩家普通（0） | 新八刀大幅镰斩 |
| 玩家破境（1） | 原 v6 |
| 玩家鸣雷（2） | 新八刀大幅镰斩 |
| 玩家碎空（3） | 原 v6 |

## 修改内容

旧版将刀柄限制在角色前方，握持求解还会进一步压缩摆角。Wide06 增加身后蓄力、跨过身体的挥砍和完整收势，腰胸随武器转动。双手仍握在同一根镰柄上：右手握柄尾，左手握中段；脚步继续推进，支撑脚保持落点。

本轮用 Google MediaPipe Heavy 重新处理 0.5 倍速参考视频的前 346 帧。人体捕捉、之前核对的脚底接触和八个独立源区间用于重定向；武器不属于 MediaPipe 人体关键点，镰柄轨迹结合 OpenCV 与逐帧观察处理。遮挡、残影中的方向跳变经过动画编辑，整理成可见的连续攻击弧线，单目视频不能恢复作者原始三维动作。

## 实际导出幅度

下面来自导出的 Epic Fight 骨架与动画文件。方向跨度是任意两帧刀柄方向的最大夹角，最高为 180°；连续挥砍路程统计整个弧线，可超过 180°。

| 动作 | 旧方向跨度 | 新方向跨度 | 连续挥砍路程 | 时长 |
| --- | --- | --- | --- | --- |
{chr(10).join(rows)}

连续八刀共 {timing['duration']:.3f} 秒。攻击判定和拖尾覆盖主要挥砍阶段，保留预备与收势时间。

## 使用

退出游戏，在对应实例的 mods 文件夹中用同版本 Wide06 JAR 替换旧 Herobrine Companion JAR，然后重启。每个实例只保留一个 Herobrine Companion JAR。进入 Epic Fight 战斗模式，主手持终末之诗，Shift + 右键切换模式。碎空（3）的普通连招、冲刺／空中攻击和持镰姿势都恢复到原 v6。

`Wide06_front.mp4` 为正常游戏速度；`Wide06_comparison_05x.mp4` 为源视频骨架与游戏网格的半速比较。参考画面按人体捕捉的源帧对应，武器大幅弧线是本次战斗编辑。

## 验证范围

完成两版本编译、Epic Fight 资源加载与插值、模式分发、Root 位移、双手握点、骨长、脚底与武器离地检查。两个版本的 v6 资源逐文件 SHA-256 不变。游戏网格离线预览用于检查挥砍轮廓与节奏；尚未在实际游戏世界里完成实机攻击测试。

Blender 源工程通过 Blender MCP 在本地会话中烘焙。完整报告在 `reports/`。复现顺序：`recapture_half_speed.py --work build/scythe_wide_06 --source-seconds 5.75` → `select_recapture.py --work build/scythe_wide_06` → `retarget_wide_swing.py` → 通过 Blender MCP 运行 `bake_wide_swing.py` → `export_mediapipe.py` → `verify_mediapipe.py` → 两版 Gradle `verifyMediaPipe` 与构建。

参考：Pirate_Gn《镰刀连击》，https://www.bilibili.com/video/BV1JH4y1r7po/ 。原视频版权归作者；源视频不包含在模组 JAR 中，动作不标记为 CC0。
'''
for root in (ROOT, ROOT.parent/'herobrine companion'):
    for name in ('epicfight-poem-wide06.md', 'epicfight-poem-mediapipe.md'):
        (root/'docs'/name).write_text(text, encoding='utf-8')
print('WIDE06_DOCS_COMPLETE')
