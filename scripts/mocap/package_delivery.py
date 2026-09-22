"""Encode review videos and assemble a documented, self-contained asset package."""
from pathlib import Path
import json
import hashlib
import shutil
import subprocess
import zipfile
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
OUT=ROOT/'output/Herobrine_Scythe_MediaPipe'
blend=OUT/'Herobrine_镰刀动捕_MediaPipe.blend'
render=json.loads((WORK/'render_manifest.json').read_text(encoding='utf-8'))
assert render['completed'] and render['frames']==519
assert render['blend_sha256']==hashlib.sha256(blend.read_bytes()).hexdigest(),'Render belongs to a different saved Blender file'
assert len(list((WORK/'render_full').glob('frame_*.png')))==519
capture=json.loads((WORK/'capture_report.json').read_text(encoding='utf-8'))
qa=json.loads((WORK/'motion_qa.json').read_text(encoding='utf-8'))
combat=json.loads((WORK/'combat_qa.json').read_text(encoding='utf-8'))
motion=json.loads((WORK/'target_motion.json').read_text(encoding='utf-8'))
fbx=json.loads((WORK/'fbx_validation.json').read_text(encoding='utf-8'))
assert fbx['passed'] and not qa['weapon_penetrating_frames'] and not qa['body_penetrating_frames']
assert qa['opening_character_relative_direction']['passed']
assert combat['left_middle_grip_max_error']<1e-4 and combat['right_tail_grip_max_error']<1e-4
assert not combat['weapon_torso_or_head_contacts_in_ground_attacks']
assert (OUT/'Opening_TwoHand_0.5x.mp4').exists()
shutil.copy2(OUT/'Opening_TwoHand_0.5x.mp4',OUT/'Opening_0.5x.mp4')

def run(args):
    result=subprocess.run(args,stdout=subprocess.PIPE,stderr=subprocess.PIPE,encoding='utf-8',errors='replace')
    if result.returncode:raise RuntimeError(result.stderr[-5000:])
    return result.stdout

common=['ffmpeg','-y','-hide_banner','-loglevel','error']
codec=['-c:v','libx264','-preset','medium','-crf','18','-pix_fmt','yuv420p','-movflags','+faststart']
for rate,name in [('30','Preview_1x.mp4'),('15','Preview_0.5x.mp4')]:
    run(common+['-framerate',rate,'-start_number','1','-i',str(WORK/'render_full/frame_%04d.png'),
                '-r','30']+codec+[str(OUT/name)])
    print('ENCODED',name,flush=True)

font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',27)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',23)
overlay=Image.new('RGBA',(1920,640),(0,0,0,0));draw=ImageDraw.Draw(overlay)
draw.rectangle((0,0,1920,59),fill='#141c26');draw.rectangle((0,600,1920,640),fill='#141c26')
draw.text((28,14),'参考视频 · Pirate_Gn《镰刀连击》',font=font,fill='#e8edf5')
draw.text((990,14),'Herobrine · 双手前挥攻击修正版',font=font,fill='#86d9ec')
draw.text((28,607),'0.5× 慢放  |  MediaPipe 动捕基础  |  左手握中段，右手握柄尾；地面攻击含人工重构',font=small,fill='#bec9d8')
overlay.save(WORK/'comparison_overlay.png')
filtergraph=('[0:v]scale=960:480,pad=960:540:0:30:color=0x898989,setsar=1[ref];'
             '[1:v]scale=960:540,setsar=1[target];[ref][target]hstack=inputs=2[pair];'
             '[pair]pad=1920:640:0:60:color=0x141c26[canvas];'
             '[canvas][2:v]overlay=0:0:shortest=1,setpts=2*PTS,tpad=stop_mode=clone:stop_duration=0.1,fps=30,trim=duration=34.6[out]')
run(common+['-i',str(WORK/'reference_hd.mp4'),'-i',str(OUT/'Preview_1x.mp4'),
            '-loop','1','-i',str(WORK/'comparison_overlay.png'),'-filter_complex',filtergraph,
            '-map','[out]','-t','34.6','-an']+codec+[str(OUT/'Comparison_0.5x.mp4')])
print('ENCODED Comparison_0.5x.mp4',flush=True)
run(common+['-i',str(WORK/'capture_overlay.avi'),'-an']+codec+[str(OUT/'MediaPipe_Overlay.mp4')])

frames=[1,16,45,81,120,149,181,193,224,267,271,297,312,327,341,356,371,401,445,519]
sheet=Image.new('RGB',(1600,1240),'#141c26');draw=ImageDraw.Draw(sheet)
label=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',16)
for i,frame in enumerate(frames):
    x=i%4*400;y=i//4*248
    im=Image.open(WORK/'render_full'/f'frame_{frame:04d}.png').resize((400,225),Image.Resampling.LANCZOS)
    sheet.paste(im,(x,y+23));draw.text((x+9,y+2),f'Frame {frame:03d}   {(frame-1)/30:.2f} s',font=label,fill='#dce6ef')
sheet.save(OUT/'Contact_Sheet.jpg',quality=94)

reports=OUT/'reports';reports.mkdir(exist_ok=True)
for name in ['capture_report.json','combat_cleanup_report.json','combat_qa.json','motion_qa.json',
             'blender_bake_report.json','export_report.json','fbx_validation.json','render_manifest.json']:
    shutil.copy2(WORK/name,reports/name)
data_dir=OUT/'capture';data_dir.mkdir(exist_ok=True)
for name in ['capture_solved.npz','target_motion.json','floor_tracking.json']:
    shutil.copy2(WORK/name,data_dir/name)
shutil.copy2(WORK/'reference_hd.mp4',data_dir/'reference_BV1JH4y1r7po.mp4')
(OUT/'Source.url').write_text('[InternetShortcut]\nURL=https://www.bilibili.com/video/BV1JH4y1r7po/\n',encoding='utf-8')

readme=f'''# Herobrine 镰刀攻击 · MediaPipe 双手战斗修正版

以 Pirate_Gn《镰刀连击》的 Google MediaPipe Pose Heavy 捕捉为基础，经用户指定的双手握持和前挥攻击修正，再通过 Blender MCP 烘焙到现有 Herobrine 方块人及原版镰刀。保留主要动作顺序与总时长：30 fps，1–519 帧，约 17.3 秒。

这是单目捕捉与动作重构结合的可编辑结果。地面攻击按反馈重新编排了双手握持、支撑脚、肩部转动与向前发力。高速转身、遮挡、倒立和空中动作存在识别歧义；本文件不是原作者的原始三维动画，也不是逐帧复刻。

## 使用

- `Herobrine_镰刀动捕_MediaPipe.blend`：主工程，模型贴图已打包。打开后按空格播放，Action 为 `HB_Scythe_MediaPipe_BV1JH4y1r7po`。时间线标记包括连斩、突进、倒立、撑杆、脱手、回镰和收势。
- `Herobrine_Scythe_MediaPipe.fbx`：模型、骨架及整段烘焙动画，贴图同时嵌入并保存在 `textures/`。在 Blender 中复查时设 30 fps，FBX 导入 Animation Offset 设为 **0**，保持与主工程一致的帧号。
- `Preview_1x.mp4`：原速，17.3 秒。
- `Preview_0.5x.mp4`：半速，34.6 秒。每个源帧显示两次，未生成新的中间动作。
- `Opening_TwoHand_0.5x.mp4`：起手连斩的斜前、侧面双视角慢放，可检查左手中段与右手柄尾的握持关系。`Opening_0.5x.mp4` 为同一最新预览的兼容副本。
- `Comparison_0.5x.mp4`：原片与 Herobrine 并排慢放。
- `MediaPipe_Overlay.mp4`、`Contact_Sheet.jpg`：检测骨架叠加和动作缩略图。

## 捕捉与重定向

使用 Google MediaPipe 0.10.21 / BlazePose GHUM Heavy，通过高清显式人物 ROI 和多个图像旋转候选改善倒立识别，再进行时序选择、左右身份校正及短缺口插值。519 帧中有 517 帧取得可用候选；源帧 282、296 为插值，{len(capture['low_visibility_frames'])} 帧的平均关键点可见度低于 0.65。中位可见度约 {capture['median_landmark_visibility']:.2f}，这是模型置信度，不是动作准确率。

初始重定向使用图像关节点、镰柄直线及刀头装饰位置。最终地面攻击改为固定的双手握持：左手在柄中段，右手在柄尾，两个握点共同约束同一把刚性镰刀。手臂骨长保持不变，肩关节向前收拢以适配方块人的宽胸廓；肘部连续性、握点、刀刃姿态及地面约束均经过修正。原模型网格及权重保留，Blender 与 FBX 均使用线性蒙皮。

按用户确认，起手方向以**角色自身**为准：右上蓄势，先向前方挥出，再向左下斩落。左手引导柄中段，右手从柄尾发力；脚下使用前后支撑与重心转移，躯干保持朝向攻击区域。第 7 帧检查右上方向，第 22 帧检查左下方向；自动检测叠加视频保留捕捉记录，最终握持与动作以 Blender 动画为准。

单目视频无法给出真实的世界纵深位移。地面使用阴影位置校正，源帧 349–375 的腾空高度为重建弧线。新增的突进和蓄力斩使用人工设定的前进量，后续捕捉的腾空段整体向前平移以衔接位置。倒立时增加头部收拢以适应大方块头和短手臂；脱手至回镰保持单独烘焙。地面双手攻击与捕捉的腾空段之间使用过渡。

## 验证

- 全部 519 帧检查实际人物 {qa['body_vertices_checked']} 个顶点和镰刀 {qa['weapon_vertices_checked']} 个顶点，未检出低于地面的顶点。
- 骨段连接最大误差 {qa['bone_connection_max_error']:.3g}，主手握点最大误差 {qa['primary_grip_max_error']:.3g}（骨架局部单位）。双手完全握持帧中，辅助手距镰柄中心线最大约 {qa['support_hand_max_distance_to_shaft']*qa['rig_world_scale']*1000:.1f} mm。
- 重构的 {combat['authored_ground_frames']} 个地面攻击帧中，左手中段握点最大误差 {combat['left_middle_grip_max_error']:.3g}，右手柄尾握点最大误差 {combat['right_tail_grip_max_error']:.3g}；躯干、头部包围盒与镰柄/刀刃采样检查未检出穿插。
- FBX 在独立空场景重导入后抽查 {fbx['frames_checked']} 帧，几何包围盒最大世界坐标差 {fbx['maximum_world_bounds_error']:.3g}。
- 地面、握持及包围盒检查不等同于完整的自碰撞检测，也不证明三维姿态与原片完全一致。捕捉的倒立、撑镰和空中动作仍包含估计。

`capture/` 保存检测结果、最终骨骼矩阵、地面跟踪及参考视频；`reports/` 保存处理和验证记录。源帧编号从 0 开始，Blender 时间线从 1 开始。本机处理脚本保留在 `E:/java/herobrine_companion/scripts/mocap/`。

## 来源与连接

参考：[Pirate_Gn《镰刀连击》](https://www.bilibili.com/video/BV1JH4y1r7po/)。视频版权归原作者；本包未将该参考动作标记为 CC0，也未附加原作者未声明的授权。

本次使用独立的 Blender MCP 会话 `127.0.0.1:9877`，主工程已在该会话打开。原模型工程保留在原位置。
'''
(OUT/'README_中文.md').write_text(readme,encoding='utf-8')

video_checks={}
for name,expected_frames,expected_duration in [('Preview_1x.mp4',519,17.3),('Preview_0.5x.mp4',1038,34.6),('Comparison_0.5x.mp4',1038,34.6)]:
    info=json.loads(run(['ffprobe','-v','error','-count_frames','-select_streams','v:0',
                         '-show_entries','stream=width,height,r_frame_rate,nb_read_frames,duration','-of','json',str(OUT/name)]))['streams'][0]
    assert int(info['nb_read_frames'])==expected_frames,(name,info)
    assert abs(float(info['duration'])-expected_duration)<.05,(name,info)
    video_checks[name]=info
(reports/'video_validation.json').write_text(json.dumps(video_checks,indent=2),encoding='utf-8')

# Blender creates these task-owned backups during repeated saves. Keep only
# the final artifact in the delivery folder; other directories are untouched.
for backup in OUT.glob('Herobrine_镰刀动捕_MediaPipe.blend[0-9]*'):
    assert backup.resolve().parent==OUT.resolve()
    backup.unlink()
manifest={str(p.relative_to(OUT)).replace('\\','/'):{'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
          for p in sorted(OUT.rglob('*')) if p.is_file() and p.name!='manifest.json'}
(OUT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
archive=OUT.parent/'Herobrine_Scythe_MediaPipe.zip'
with zipfile.ZipFile(archive,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=5) as z:
    for p in sorted(OUT.rglob('*')):
        if p.is_file():z.write(p,Path(OUT.name)/p.relative_to(OUT))
print('PACKAGE_COMPLETE',json.dumps({'folder':str(OUT),'archive':str(archive),'files':len(manifest)+1,
                                    'archive_bytes':archive.stat().st_size,'videos':video_checks},ensure_ascii=True),flush=True)
