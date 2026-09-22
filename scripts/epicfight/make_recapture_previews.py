"""Encode the verified game mesh at combat speed, plus a half-speed comparison."""
import json,subprocess
from pathlib import Path
import cv2
from PIL import Image,ImageDraw,ImageFont

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/epicfight-mediapipe'
PREVIEW=WORK/'preview'
SOURCE=ROOT/'build/scythe_recapture_05'
motion=json.loads((ROOT/'output/Herobrine_Scythe_Recapture_05/capture/target_motion.json').read_text(encoding='utf-8'))
duration=motion['duration_seconds']
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',26)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',20)


def ffmpeg(*args):
    subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y',*map(str,args)],check=True)


for view in ['front','side']:
    folder=PREVIEW/f'sequence_{view}'
    files=sorted(folder.glob('*.png'))
    assert len(files)==len(range(1,len(motion['poses'])+1,2)),(folder,len(files))
    ffmpeg('-framerate','30','-i',folder/'%04d.png','-t',f'{duration:.6f}',
           '-c:v','libx264','-crf','18','-preset','medium','-pix_fmt','yuv420p','-movflags','+faststart',
           PREVIEW/f'Recapture05_{view}.mp4')

folder=PREVIEW/'sequence_compare';folder.mkdir(exist_ok=True)
capture=cv2.VideoCapture(str(SOURCE/'reference_half_speed.mp4'))
points=__import__('numpy').load(SOURCE/'capture_selected.npz')['xy']
for index,f in enumerate(range(0,len(motion['poses']),2)):
    fw=motion['footwork'][f];ref=fw['reference_frame']-1;half=round(ref*2)
    capture.set(cv2.CAP_PROP_POS_FRAMES,half)
    ok,image=capture.read();assert ok
    image=cv2.resize(image,(854,426));joints=points[half].round().astype(int)
    for a,b in [(11,12),(11,23),(12,24),(23,24),(11,13),(13,15),(12,14),(14,16),(23,25),(25,27),(24,26),(26,28)]:
        cv2.line(image,tuple(joints[a]),tuple(joints[b]),(50,250,70) if a%2 else (30,170,255),2,cv2.LINE_AA)
    canvas=Image.new('RGB',(1920,720),'#17222e');draw=ImageDraw.Draw(canvas)
    left=Image.fromarray(cv2.cvtColor(image,cv2.COLOR_BGR2RGB)).resize((960,479))
    canvas.paste(left,(0,118))
    game=Image.open(PREVIEW/f'sequence_front/{index:04d}.png').convert('RGB')
    canvas.paste(game,(960,0))
    draw.rectangle((960,0,1920,70),fill='#17222e')
    draw.text((24,22),'原视频 · 本轮 Google MediaPipe 骨架',font=font,fill='white')
    draw.text((984,22),'Herobrine · 游戏网格重定向',font=font,fill='white')
    label=motion['attack_segments'][fw['segment']-1]['label']
    draw.text((24,635),f"第 {fw['segment']} 刀  {label}",font=font,fill='white')
    draw.text((24,677),f'源视频 {ref/30:.2f}s · 两侧按同一源帧对应 · 0.5 倍动作检查',font=small,fill='#b9d0df')
    canvas.save(folder/f'{index:04d}.png')
capture.release()
ffmpeg('-framerate','15','-i',folder/'%04d.png','-t',f'{duration*2:.6f}',
       '-c:v','libx264','-crf','19','-preset','medium','-pix_fmt','yuv420p','-movflags','+faststart',
       PREVIEW/'Recapture05_reference_comparison_half_speed.mp4')
print('RECAPTURE_VIDEOS_COMPLETE',duration,'seconds, front / fixed side / half-speed reference comparison')
