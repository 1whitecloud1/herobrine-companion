"""Encode normal/half-speed game previews and the fresh MediaPipe comparison."""
import json
import subprocess
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'output/Herobrine_Scythe_Wide_06'
PREVIEW = OUT/'preview'
CAPTURE = ROOT/'build/scythe_wide_06'
motion = json.loads((OUT/'capture/target_motion.json').read_text(encoding='utf-8'))
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 25)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 20)
duration = motion['duration_seconds']


def ffmpeg(*args):
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', *map(str, args)], check=True)


for view in ('front', 'side'):
    folder = PREVIEW/f'sequence_{view}'
    assert len(list(folder.glob('*.png'))) == len(range(0, len(motion['poses']), 2))
    ffmpeg('-framerate', 30, '-i', folder/'%04d.png', '-t', f'{duration:.6f}',
           '-c:v', 'libx264', '-crf', 17, '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
           PREVIEW/f'Wide06_{view}.mp4')
ffmpeg('-i', PREVIEW/'Wide06_front.mp4', '-vf', 'setpts=2*PTS', '-an', '-r', 30,
       '-c:v', 'libx264', '-crf', 18, '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
       PREVIEW/'Wide06_front_05x.mp4')

comparison = PREVIEW/'sequence_comparison'
comparison.mkdir(exist_ok=True)
cap = cv2.VideoCapture(str(CAPTURE/'reference_half_speed.mp4'))
points = np.load(CAPTURE/'capture_selected.npz')['xy']
for index, f in enumerate(range(0, len(motion['poses']), 2)):
    fw = motion['footwork'][f]
    reference = fw['reference_frame']-1
    half = round(reference*2)
    cap.set(cv2.CAP_PROP_POS_FRAMES, half)
    ok, frame = cap.read()
    assert ok
    frame = cv2.resize(frame, (854, 426))
    joints = points[half].round().astype(int)
    for a, b in ((11, 12), (11, 23), (12, 24), (23, 24), (11, 13), (13, 15), (12, 14), (14, 16), (23, 25), (25, 27), (24, 26), (26, 28)):
        cv2.line(frame, tuple(joints[a]), tuple(joints[b]), (50, 250, 70) if a % 2 else (30, 170, 255), 2, cv2.LINE_AA)
    canvas = Image.new('RGB', (1920, 720), '#17222e')
    canvas.paste(Image.fromarray(cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)).resize((960, 479)), (0, 118))
    canvas.paste(Image.open(PREVIEW/f'sequence_front/{index:04d}.png').convert('RGB'), (960, 0))
    draw = ImageDraw.Draw(canvas)
    draw.rectangle((960, 0, 1920, 62), fill='#17222e')
    draw.text((24, 19), '参考视频 · Google MediaPipe 人体捕捉', font=font, fill='white')
    draw.text((984, 19), 'Herobrine · 大幅镰斩', font=font, fill='white')
    segment = motion['attack_segments'][fw['segment']-1]
    draw.text((24, 623), segment['label'], font=font, fill='white')
    draw.text((24, 664), '半速对照 · 人体按源帧对应，武器弧线经过战斗编辑', font=small, fill='#b9d0df')
    canvas.save(comparison/f'{index:04d}.png')
cap.release()
ffmpeg('-framerate', 15, '-i', comparison/'%04d.png', '-t', f'{duration*2:.6f}',
       '-c:v', 'libx264', '-crf', 18, '-pix_fmt', 'yuv420p', '-movflags', '+faststart',
       PREVIEW/'Wide06_comparison_05x.mp4')

sheet = Image.new('RGB', (1920, 8*396+52), '#17222e')
draw = ImageDraw.Draw(sheet)
draw.text((18, 10), 'Wide06 · 游戏骨架与终末之诗网格 · 蓄力 / 入刃 / 挥砍 / 收势', font=font, fill='white')
for i, segment in enumerate(motion['attack_segments']):
    a, b = segment['first_frame']-1, segment['last_frame']-1
    for j, u in enumerate((.12, .30, .51, .80)):
        index = round((a+(b-a)*u)/2)
        image = Image.open(PREVIEW/f'sequence_front/{index:04d}.png').convert('RGB').resize((480, 360))
        x, y = j*480, 52+i*396
        sheet.paste(image, (x, y))
        draw.text((x+12, y+363), f"{i+1:02d} {segment['label']}", font=small, fill='white')
sheet.save(PREVIEW/'Game_Mesh_Wide06.jpg', quality=93)
print('WIDE06_PREVIEWS_COMPLETE', duration)
