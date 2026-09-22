"""Create frame-labelled sprint previews with truthful reference correspondence."""
import json
import subprocess
from pathlib import Path

import cv2
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output/Herobrine_Scythe_AerialDash_08'
PREVIEW=OUT/'preview'
data=json.loads((OUT/'capture/aerial_retarget_report.json').read_text(encoding='utf-8'))
controls=data['controls']
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',25)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',20)
title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',30)
def ffmpeg(*args):
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y',*map(str,args)],check=True)
def captioned(frame,index,side=False):
    c=controls[frame-1]
    im=Image.open(PREVIEW/f'sequence_{"side" if side else "front"}/{index:04d}.png').convert('RGB')
    d=ImageDraw.Draw(im)
    d.rectangle((0,0,960,54),fill='#17222e')
    d.text((18,12),f'疾跑 · 腾空旋镰    F{frame:03d} / {c["time"]:.2f}s',font=font,fill='white')
    d.rectangle((0,641,960,720),fill='#17222e')
    d.text((18,649),c['phase'],font=font,fill='white')
    d.text((18,686),'刀刃朝外 · 地砖边长 0.5 格'+(' · 固定侧面机位' if side else ''),font=small,fill='#bfd7e8')
    return im
for side in (False,True):
    view='side' if side else 'front'
    assert len(list((PREVIEW/f'sequence_{view}').glob('*.png')))==46
    folder=PREVIEW/f'captioned_{view}';folder.mkdir(exist_ok=True)
    for index,frame in enumerate(range(1,92,2)):
        captioned(frame,index,side).save(folder/f'{index:04d}.png')
    ffmpeg('-framerate',30,'-i',folder/'%04d.png','-t','1.5','-c:v','libx264','-crf',17,
           '-pix_fmt','yuv420p','-movflags','+faststart',PREVIEW/f'AerialDash08_{view}.mp4')
    ffmpeg('-i',PREVIEW/f'AerialDash08_{view}.mp4','-vf','setpts=2*PTS','-r',30,'-an',
           '-c:v','libx264','-crf',18,'-pix_fmt','yuv420p','-movflags','+faststart',PREVIEW/f'AerialDash08_{view}_05x.mp4')
folder=PREVIEW/'sequence_comparison';folder.mkdir(exist_ok=True)
cap=cv2.VideoCapture(str(ROOT/'build/scythe_mocap/reference_hd.mp4'))
for index,frame in enumerate(range(1,92,2)):
    c=controls[frame-1];ref=round(c['reference_frame'])
    cap.set(cv2.CAP_PROP_POS_FRAMES,ref);ok,bgr=cap.read();assert ok
    raw=Image.fromarray(cv2.cvtColor(bgr,cv2.COLOR_BGR2RGB));raw.thumbnail((960,620))
    canvas=Image.new('RGB',(1920,820),'#17222e')
    canvas.paste(raw,((960-raw.width)//2,98+(560-raw.height)//2))
    canvas.paste(captioned(frame,index),(960,62))
    d=ImageDraw.Draw(canvas)
    d.text((20,15),'原视频 · Pirate_Gn《镰刀连击》',font=title,fill='white')
    d.text((981,15),'Herobrine · 疾跑攻击 · 刀刃朝外',font=title,fill='white')
    d.text((20,76),f'原片 F{ref:03d} / {ref/30:.3f}s',font=font,fill='#cddfed')
    d.text((20,668),'参考：后半段人镰一同腾空旋转',font=font,fill='white')
    if c['time']>=1.0:
        d.text((20,714),'空转参考段结束；右侧为游戏落地衔接',font=font,fill='#ffe5a0')
    else:
        d.text((20,714),'按源帧对应；动作已重定时',font=font,fill='#bfd7e8')
    d.text((20,782),'半速 0.5× · Google MediaPipe 捕捉 + 方块人体型、转体和刃向编辑',font=small,fill='#bfd7e8')
    canvas.save(folder/f'{index:04d}.png')
cap.release()
ffmpeg('-framerate',15,'-i',folder/'%04d.png','-t','3','-c:v','libx264','-crf',18,
       '-pix_fmt','yuv420p','-movflags','+faststart',PREVIEW/'AerialDash08_comparison_05x.mp4')
sheet=Image.new('RGB',(1920,800),'#17222e');d=ImageDraw.Draw(sheet)
d.text((20,10),'疾跑 · 腾空旋镰｜两周空转 · 刀刃朝外',font=title,fill='white')
for row,side in enumerate((False,True)):
    for col,frame in enumerate((21,29,37,45)):
        sheet.paste(captioned(frame,(frame-1)//2,side).resize((480,360)),(col*480,66+row*365))
sheet.save(PREVIEW/'AerialDash08_outward_blade.jpg',quality=95)
print('AERIAL_DASH_PREVIEWS_ENCODED',PREVIEW)
