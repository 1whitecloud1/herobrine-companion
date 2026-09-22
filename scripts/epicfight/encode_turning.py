"""Render captions from the final motion, with explicit source/game clocks."""
import json
import subprocess
from pathlib import Path

import cv2
import numpy as np
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output/Herobrine_Scythe_Turn_07'
PREVIEW=OUT/'preview'
motion=json.loads((OUT/'capture/target_motion.json').read_text(encoding='utf-8'))
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',25)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',19)
title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',29)
duration=motion['duration_seconds']


def ffmpeg(*args):
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y',*map(str,args)],check=True)


def segment_at(frame):
    return next((s for s in motion['attack_segments'] if s['first_frame']<=frame<s['last_frame']),motion['attack_segments'][-1])


def captioned(frame,index,view):
    im=Image.open(PREVIEW/f'sequence_{view}/{index:04d}.png').convert('RGB')
    draw=ImageDraw.Draw(im)
    s=segment_at(frame)
    draw.rectangle((0,0,960,47),fill='#17222e')
    draw.text((18,8),f"{int(s['name'][-2:]):02d}  {s['label']}",font=font,fill='white')
    draw.rectangle((0,681,960,720),fill='#17222e')
    p=np.array(motion['poses'][frame-1]['Bone.011'])[:3,3]
    first=np.array(motion['poses'][0]['Bone.011'])[:3,3]
    text=f"F{frame:03d}  ·  游戏动作 {(frame-1)/60:.2f}s  ·  前进 {-(p-first)[1]*.625:.2f} 格"
    if view=='side':text+='  ·  固定镜头'
    draw.text((18,690),text,font=small,fill='#c9dce9')
    return im


for view in ('front','side'):
    folder=PREVIEW/f'captioned_{view}'
    folder.mkdir(exist_ok=True)
    for index,frame in enumerate(range(1,len(motion['poses'])+1,2)):
        captioned(frame,index,view).save(folder/f'{index:04d}.png')
    ffmpeg('-framerate',30,'-i',folder/'%04d.png','-t',f'{duration:.6f}',
           '-c:v','libx264','-crf',17,'-pix_fmt','yuv420p','-movflags','+faststart',PREVIEW/f'Turn07_{view}.mp4')
ffmpeg('-i',PREVIEW/'Turn07_front.mp4','-vf','setpts=2*PTS','-an','-r',30,
       '-c:v','libx264','-crf',18,'-pix_fmt','yuv420p','-movflags','+faststart',PREVIEW/'Turn07_front_05x.mp4')

comparison=PREVIEW/'sequence_comparison'
comparison.mkdir(exist_ok=True)
cap=cv2.VideoCapture(str(ROOT/'build/scythe_mocap/reference_hd.mp4'))
points=np.load(ROOT/'build/scythe_turn_07/capture_selected.npz')['xy']
for index,frame in enumerate(range(1,len(motion['poses'])+1,2)):
    fw=motion['footwork'][frame-1]
    ref=fw['reference_frame']-1
    cap.set(cv2.CAP_PROP_POS_FRAMES,round(ref))
    ok,im=cap.read();assert ok
    im=cv2.resize(im,(854,426))
    hip=points[min(len(points)-1,round(ref*2)),[23,24],:].mean(axis=0)
    im=cv2.copyMakeBorder(im,100,100,200,200,cv2.BORDER_CONSTANT,value=(140,140,140))
    im=cv2.getRectSubPix(im,(568,426),(float(hip[0]+200),313.))
    left=Image.fromarray(cv2.cvtColor(im,cv2.COLOR_BGR2RGB)).resize((960,720))
    canvas=Image.new('RGB',(1920,810),'#17222e')
    canvas.paste(left,(0,46))
    canvas.paste(captioned(frame,index,'front'),(960,46))
    draw=ImageDraw.Draw(canvas)
    draw.text((20,8),'原视频 · Pirate_Gn《镰刀连击》',font=font,fill='white')
    draw.text((980,8),'Herobrine · 游戏资源还原',font=font,fill='white')
    draw.rectangle((0,46,960,92),fill='#17222e')
    draw.text((20,55),f'原视频 F{round(ref):03d} / {ref/30:.3f}s',font=font,fill='#cfdeeb')
    draw.rectangle((0,727,960,766),fill='#17222e')
    draw.text((20,737),'Google MediaPipe 捕捉 + 逐段转体、刃向与步幅编辑',font=small,fill='#cfdeeb')
    draw.text((20,779),'按源帧同步对照（动作经过重定时） · 右侧 0.5× · 地砖边长 0.5 格',font=small,fill='#b9d0df')
    canvas.save(comparison/f'{index:04d}.png')
cap.release()
ffmpeg('-framerate',15,'-i',comparison/'%04d.png','-t',f'{duration*2:.6f}',
       '-c:v','libx264','-crf',18,'-pix_fmt','yuv420p','-movflags','+faststart',PREVIEW/'Turn07_comparison_05x.mp4')

sheet=Image.new('RGB',(1920,8*382+60),'#17222e')
draw=ImageDraw.Draw(sheet)
draw.text((20,10),'Turn07 · 实际游戏骨架 · 每刀四个阶段',font=title,fill='white')
for i,s in enumerate(motion['attack_segments']):
    for j,u in enumerate((.13,.36,.59,.83)):
        frame=int(round((s['first_frame']-1+(s['last_frame']-s['first_frame'])*u)/2))*2+1
        pic=captioned(frame,(frame-1)//2,'front').resize((480,360))
        sheet.paste(pic,(j*480,60+i*382))
sheet.save(PREVIEW/'Game_Mesh_Turn07.jpg',quality=94)

# A separate large strip makes the reported third-second turn easy to inspect.
focus=Image.new('RGB',(1920,790),'#17222e')
draw=ImageDraw.Draw(focus)
draw.text((20,9),'第 3 刀连续转体 · 第 75 帧起已翻正刀刃',font=title,fill='white')
for i,frame in enumerate((81,89,97,107)):
    pic=captioned(frame,(frame-1)//2,'front').resize((480,360))
    focus.paste(pic,(i*480,57))
    pic=captioned(frame,(frame-1)//2,'side').resize((480,360))
    focus.paste(pic,(i*480,425))
focus.save(PREVIEW/'Turn07_third_attack.jpg',quality=95)
print('TURN07_PREVIEWS_COMPLETE',flush=True)
