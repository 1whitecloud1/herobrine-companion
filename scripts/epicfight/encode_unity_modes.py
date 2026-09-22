"""Caption native-frame comparisons and encode honest 1x / 0.5x previews."""
import argparse
import json
import subprocess
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
WORK = ROOT / 'build/scythe_unity_pack_09'
timeline = json.loads((OUT / 'resources/assets/herobrine_companion/epicfight/poem_unity09_timing.json').read_text(encoding='utf-8'))
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 27)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 21)
title = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 31)
tiny = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 17)
colors = ['#69e0cf', '#f0a37e', '#f4d66b', '#bda4ef']


def info(mode, motion):
    if motion == 'full':
        return mode['ground_source'], mode['source_boundaries'][-1] / 60, '地面四段连招'
    timing = next(t for t in mode['specials'] if t['name'] == motion)
    return timing['source_clip'], timing['duration'], '疾跑攻击' if motion == 'dash' else '空中攻击'


def caption(im, mode, motion, frame, rate):
    source, duration, label = info(mode, motion)
    time = frame / 60
    contact = False; segment = None
    if motion == 'full':
        segment = next((i for i, t in enumerate(mode['segments']) if t['start'] <= time < t['start'] + t['duration']), 3)
        item = mode['segments'][segment]; local_time = time - item['start']
        contact = any(h['start'] <= local_time <= h['end'] for h in item['contacts'])
    else:
        item = next(t for t in mode['specials'] if t['name'] == motion)
        local_time = time
        contact = any(h['start'] <= time <= h['end'] for h in item['contacts'])
    canvas = Image.new('RGB', (1600, 980), '#121e2b'); canvas.paste(im, (0, 78)); d = ImageDraw.Draw(canvas)
    d.rectangle((0, 0, 1600, 5), fill=colors[mode['mode']])
    d.text((26, 18), 'Unity 原动作', font=title, fill='white')
    d.text((828, 18), 'Herobrine · ' + mode['label'] + '模式', font=title, fill=colors[mode['mode']])
    d.text((1400, 24), '0.5× 慢放' if rate == .5 else '1× 原速', font=font, fill='white')
    display = label + (f' · 第 {segment + 1} 段' if segment is not None else '')
    d.text((26, 888), display, font=font, fill='white')
    d.text((690, 888), f'素材 F{frame:03d} / {time:.3f}s', font=font, fill='#d4e2ef')
    state = '命中时段' if contact else '可接下一击' if local_time >= item['recovery'] else '起手 / 收势'
    d.text((1332, 888), state, font=font, fill='#ffce82' if contact else '#a6bbce')
    d.text((26, 932), source + '   |   Blender 素材预览 · 地砖边长 0.5 方块', font=small, fill='#a6bbce')
    d.line((26, 972, 1574, 972), fill='#354457', width=4)
    d.line((26, 972, 26 + int(1548 * min(1, time / duration)), 972), fill=colors[mode['mode']], width=4)
    return canvas


def overview():
    sheet = Image.new('RGB', (1920, 1140), '#121e2b'); d = ImageDraw.Draw(sheet)
    d.text((22, 12), '终末之诗 · 四组原生镰刀动作', font=title, fill='white')
    for col, mode in enumerate(timeline['modes']):
        for row, motion in enumerate(('full', 'dash', 'air')):
            timing = mode['segments'][0] if motion == 'full' else next(t for t in mode['specials'] if t['name'] == motion)
            time = sum(timing['contacts'][0][k] for k in ('start', 'end')) * .5
            # These frames were validated against the newly shipped matrices.
            index = round(time * 60)
            path = OUT / 'preview/sequence_60' / mode['key'] / motion / f'{index:04d}.png'
            im = Image.open(path).convert('RGB').crop((790, 75, 1600, 725)).resize((480, 310))
            x = col * 480; y = 68 + row * 354
            sheet.paste(im, (x, y + 32))
            d.text((x + 14, y + 3), mode['label'] + ' · ' + info(mode, motion)[2], font=small, fill=colors[mode['mode']])
            d.text((x + 14, y + 320), info(mode, motion)[0] + f' / F{index}', font=tiny, fill='#c8d6e4')
    sheet.save(OUT / 'UnityModes10_overview.jpg', quality=95)


def process(path, fps):
    path.parent.mkdir(parents=True, exist_ok=True)
    return subprocess.Popen(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
                             '-s', '1600x980', '-r', str(fps), '-i', '-', '-an', '-c:v', 'libx264', '-preset', 'fast',
                             '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(path)], stdin=subprocess.PIPE)


def concatenate(paths, target):
    listing = WORK / (target.stem + '_concat.txt')
    listing.write_text(''.join("file '" + p.as_posix().replace("'", "'\\''") + "'\n" for p in paths), encoding='utf-8')
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(listing),
                    '-c', 'copy', '-movflags', '+faststart', str(target)], check=True)


def encode():
    all_normal = []; all_slow = []
    for mode in timeline['modes']:
        mode_slow = []
        for motion in ('full', 'dash', 'air'):
            source, duration, _ = info(mode, motion); count = round(duration * 60)
            folder = OUT / 'preview/sequence_60' / mode['key'] / motion
            assert all((folder / f'{i:04d}.png').exists() for i in range(count)), (mode['key'], motion)
            base = WORK / 'encoded10' / (mode['key'] + '_' + motion + '.mp4')
            slow = WORK / 'encoded10' / (mode['key'] + '_' + motion + '_05x.mp4')
            normal_process = process(base, 60); slow_process = process(slow, 30)
            try:
                for frame in range(count):
                    im = Image.open(folder / f'{frame:04d}.png').convert('RGB')
                    normal_process.stdin.write(caption(im, mode, motion, frame, 1).tobytes())
                    slow_process.stdin.write(caption(im, mode, motion, frame, .5).tobytes())
            finally:
                normal_process.stdin.close(); slow_process.stdin.close()
            assert normal_process.wait() == 0 and slow_process.wait() == 0
            all_normal.append(base); all_slow.append(slow); mode_slow.append(slow)
            print('ENCODED', mode['key'], motion, count, flush=True)
        concatenate(mode_slow, OUT / f'UnityModes10_{mode["mode"]+1:02d}_{mode["key"]}_05x.mp4')
    concatenate(all_normal, OUT / 'UnityModes10_comparison.mp4')
    concatenate(all_slow, OUT / 'UnityModes10_comparison_05x.mp4')
    print('UNITY_MODE_PREVIEWS_COMPLETE', OUT, flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(); parser.add_argument('--stills', action='store_true'); args = parser.parse_args()
    overview()
    if not args.stills: encode()
