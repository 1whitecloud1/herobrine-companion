"""Encode the two-hand opening from two fixed review views at half speed."""
from pathlib import Path
import json
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_mocap'
OUT = ROOT / 'output/Herobrine_Scythe_MediaPipe'
for view in ['quarter', 'side']:
    assert all((WORK/f'combat_{view}'/f'frame_{f:04d}.png').exists() for f in range(1, 83))
overlay = Image.new('RGBA', (1920, 768), (0, 0, 0, 0))
draw = ImageDraw.Draw(overlay)
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 25)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 21)
draw.rectangle((0, 0, 1920, 59), fill='#17222e')
draw.rectangle((0, 735, 1920, 768), fill='#17222e')
draw.text((25, 14), '双手握持 · 前挥连斩  /  斜前视角', font=font, fill='#e9f4f8')
draw.text((985, 14), '侧面复查  /  前送、斩落、回收', font=font, fill='#8be3e6')
draw.text((25, 739), '左手：镰柄中段    |    右手：柄尾发力    |    0.5× 慢放', font=small, fill='#d0dce7')
overlay.save(WORK/'two_hand_opening_overlay.png')
video = OUT/'Opening_TwoHand_0.5x.mp4'
graph = ('[0:v]scale=960:675,setsar=1[a];[1:v]scale=960:675,setsar=1[b];'
         '[a][b]hstack=inputs=2,pad=1920:768:0:60:color=0x17222e[base];'
         '[base][2:v]overlay=0:0:shortest=1,fps=30,tpad=stop_mode=clone:stop_duration=0.1[out]')
command = ['ffmpeg', '-y', '-hide_banner', '-loglevel', 'error',
           '-framerate', '15', '-start_number', '1', '-i', str(WORK/'combat_quarter/frame_%04d.png'),
           '-framerate', '15', '-start_number', '1', '-i', str(WORK/'combat_side/frame_%04d.png'),
           '-loop', '1', '-i', str(WORK/'two_hand_opening_overlay.png'),
           '-filter_complex', graph, '-map', '[out]', '-frames:v', '164', '-an',
           '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p',
           '-movflags', '+faststart', str(video)]
subprocess.run(command, check=True)
result = subprocess.run(['ffprobe', '-v', 'error', '-count_frames', '-select_streams', 'v:0',
                         '-show_entries', 'stream=width,height,r_frame_rate,nb_read_frames,duration',
                         '-of', 'json', str(video)], capture_output=True, text=True, check=True)
info = json.loads(result.stdout)['streams'][0]
assert int(info['nb_read_frames']) == 164, info
print('TWO_HAND_OPENING', json.dumps(info), flush=True)
