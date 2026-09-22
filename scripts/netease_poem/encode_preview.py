"""Label the rendered frames with their actual source clip and chain index."""
import subprocess
import shutil
from PIL import Image, ImageDraw, ImageFont, ImageEnhance
from common import *

def main():
    data=read(WORK/'preview.json'); manifest=read(WORK/'library.json')
    clips={c['id']:c for c in manifest['clips']}
    frames=WORK/'frames'
    missing=[i+1 for i in range(len(data['schedule'])) if not (frames/('frame_%04d.png'%(i+1))).exists()]
    assert not missing,missing[:10]
    output=OUT/'NetEase_终末之诗_长连招与空中攻击_05x.mp4'
    ffmpeg=shutil.which('ffmpeg')
    assert ffmpeg, 'ffmpeg is required to encode the preview'
    command=[ffmpeg,'-hide_banner','-loglevel','warning','-y','-f','rawvideo','-pix_fmt','rgb24','-s','960x720',
             '-r','30','-i','-','-an','-c:v','libx264','-preset','medium','-crf','19','-pix_fmt','yuv420p','-movflags','+faststart',str(output)]
    title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',26)
    label=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',22)
    small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',16)
    contact=Image.new('RGB',(1440,4*306),(15,22,31))
    desired=[1,3,4,5,6,10,11,15,17,20,22,26]
    selections={}
    for step in desired:
        choices=[i for i,k in enumerate(data['schedule']) if k['step']==step]
        selections[choices[int(len(choices)*.55)]]=desired.index(step)
    process=subprocess.Popen(command,stdin=subprocess.PIPE)
    for index,key in enumerate(data['schedule']):
        im=Image.open(frames/('frame_%04d.png'%(index+1))).convert('RGB')
        im=ImageEnhance.Brightness(im).enhance(1.16)
        if index in selections:
            tile=im.resize((480,270),Image.Resampling.LANCZOS)
            which=selections[index];x=(which%3)*480;y=(which//3)*306
            contact.paste(tile,(x,y))
            ImageDraw.Draw(contact).text((x+12,y+276),str(key['step'])+'  '+key['label'],font=small,fill=(216,237,242))
        draw=ImageDraw.Draw(im)
        draw.rectangle((0,0,960,86),fill=(17,24,33))
        draw.rectangle((0,637,960,720),fill=(17,24,33))
        heading='终末之诗 · 网易版 22 段地面长连招' if key['step']<=22 else '终末之诗 · 网易版空中攻击轮换'
        draw.text((24,13),heading,font=title,fill=(223,243,245))
        draw.text((25,52),'0.5 倍速 · 读取转换后的网易资源生成，非游戏录屏',font=small,fill=(165,185,197))
        number=('地面连招 %02d / 22'%key['step']) if key['step']<=22 else ('空中攻击 %02d / 08'%(key['step']-22))
        draw.text((24,648),number+'   '+key['label'],font=label,fill=(220,241,245))
        draw.text((25,683),'动作来源：'+clips[key['clip']]['source_clip'],font=small,fill=(162,184,198))
        progress=(index+1)/len(data['schedule'])
        draw.rectangle((0,716,round(960*progress),720),fill=(74,202,204))
        process.stdin.write(im.tobytes())
    process.stdin.close()
    assert process.wait()==0
    contact.save(OUT/'NetEase_长连招关键姿势.jpg',quality=94)
    # Decode the produced stream to catch a truncated/corrupt encode.
    verify=subprocess.run([ffmpeg,'-hide_banner','-v','error','-i',str(output),'-f','null','-'],capture_output=True,text=True)
    assert verify.returncode==0,verify.stderr
    write(WORK/'media_validation.json',{'passed':True,'video':str(output),'frames':len(data['schedule']),
            'duration_seconds':len(data['schedule'])/30.,'fps':30,'playback_speed':.5,
            'complete_decode_passed':True,'live_gameplay_tested':False},True)
    print('VIDEO_READY',output,output.stat().st_size,'bytes')


if __name__=='__main__':main()
