from pathlib import Path
import json
import subprocess
from PIL import Image, ImageDraw, ImageFont, ImageEnhance

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'output/Herobrine_Scythe_UnityModes_11_Standalone'
report=json.loads((OUT/'reports/blender_preview.json').read_text('utf-8'))
data=json.loads((ROOT/'build/poem_standalone/vanilla_mesh_preview.json').read_text('utf-8'))
font=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',28)
small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',21)
path=OUT/'Unity11_四模式普通攻击_05x.mp4'
writer=subprocess.Popen(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','rawvideo','-pix_fmt','rgb24',
                         '-s','1280x1580','-r','15','-i','-','-an','-c:v','libx264','-preset','fast','-crf','19',
                         '-pix_fmt','yuv420p','-movflags','+faststart',str(path)],stdin=subprocess.PIPE)
frames=max(info['frames'] for info in report['scenes'])
for frame in range(frames):
    canvas=Image.new('RGB',(1280,1580),'#141b24');draw=ImageDraw.Draw(canvas)
    draw.text((24,15),'四模式普通攻击 · 独立玩家播放层',font=font,fill='white')
    draw.text((1090,18),'0.5× 慢放',font=font,fill='#79e0da')
    draw.text((24,57),'实际 Java 模型顶点 / 原动作分段对照 / 非游戏实录',font=small,fill='#bdccdf')
    for info in report['scenes']:
        mode=info['mode'];index=min(frame,info['frames']-1)
        x=(mode%2)*640;y=100+(mode//2)*720
        image=Image.open(ROOT/'build/poem_standalone/frames'/str(mode)/f'f_{index+1:04d}.png').convert('RGB')
        # The workbench studio light is darker than the camera stills; use one fixed exposure for all frames.
        image=ImageEnhance.Brightness(image).enhance(1.22)
        canvas.paste(image,(x,y+73))
        draw.rectangle((x,y,x+640,y+73),fill='#1d2938')
        key=data[0]['meshes'][0]['poses'][mode]['schedule'][index]
        move=info['label']+f"  ·  第 {key['step']+1} 刀"
        if mode==2 and key['step']==2:move+=' · 回旋抛镰'
        draw.text((x+18,y+8),move,font=font,fill='white')
        note='该套结束 · 定格对照' if frame>=info['frames'] else f"动作内时间 {key['time']:.2f} 秒"
        draw.text((x+18,y+43),note,font=small,fill='#aec5de')
    draw.text((24,1545),'无史诗战斗时：移动与命中沿用原版；穿戴和皮肤由玩家当前配置决定。',font=small,fill='#bdccdf')
    writer.stdin.write(canvas.tobytes())
writer.stdin.close()
assert writer.wait()==0
probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-select_streams','v:0','-show_entries','stream=width,height,nb_frames,r_frame_rate','-show_entries','format=duration','-of','json',str(path)],text=True))
assert int(probe['streams'][0]['nb_frames'])==frames and probe['streams'][0]['r_frame_rate']=='15/1'
subprocess.run(['ffmpeg','-v','error','-i',str(path),'-f','null','-'],check=True)
probe.update(status='passed',source='Actual Java-rendered vanilla mesh frames',live_gameplay_tested=False,playback_speed=.5)
(OUT/'reports/media_validation.json').write_text(json.dumps(probe,ensure_ascii=False,indent=2)+'\n','utf-8')
print('UNITY11_VIDEO_OK',frames,probe['format']['duration'],path)
