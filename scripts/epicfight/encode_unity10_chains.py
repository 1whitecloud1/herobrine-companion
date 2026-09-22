"""Label the recovery/blend/next attack separately in the offline preview."""
import json
import subprocess

from PIL import Image, ImageDraw, ImageFont
from encode_unity_modes import OUT, WORK, concatenate

font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 26)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 19)


def start(path, rate):
    path.parent.mkdir(parents=True, exist_ok=True)
    return subprocess.Popen(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
                             '-s', '1280x940', '-r', str(rate), '-i', '-', '-an', '-c:v', 'libx264', '-preset', 'fast',
                             '-crf', '19', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(path)], stdin=subprocess.PIPE)


def main():
    data = json.loads((OUT / 'reports/chain_preview_timeline.json').read_text('utf-8'))
    normal, slow = [], []
    for chain in data['chains']:
        key, kind = chain['mode'], chain['kind']
        folder = OUT / 'preview/chains_60' / key / kind
        path = WORK / 'encoded10' / f'chain_{key}_{kind}.mp4'
        half = path.with_stem(path.stem + '_05x')
        one, two = start(path, 60), start(half, 30)
        for row in chain['frames']:
            frame, phase = row['frame'], row['phase']
            im = Image.open(folder / f'{frame:04d}.png').convert('RGB')
            for process, rate in ((one, 1), (two, .5)):
                canvas = Image.new('RGB', (1280, 940), '#121e2b')
                canvas.paste(im, (0, 64)); d = ImageDraw.Draw(canvas)
                label = '长按重击 → 普攻' if kind == 'heavy_to_light' else '普攻末段 → 下一套第一刀'
                d.text((22, 15), chain['label'] + ' · ' + label, font=font, fill='white')
                d.text((1090, 15), '0.5× 慢放' if rate == .5 else '1× 原速', font=font, fill='#69e0cf')
                status = '前一击 · 起手与收势' if phase == 'source' else '接招窗口已打开 · 0.10 秒姿势过渡' if phase == 'blend' else '下一次普通攻击'
                d.text((22, 870), status, font=font, fill='#69e0cf' if phase != 'source' else '#d5e1ef')
                d.text((680, 876), 'Blender 衔接示意 · 非游戏实录', font=small, fill='#a6bbce')
                source = chain['next_clip'] if phase == 'next' else chain['source_clip']
                native_frame = row['source_time'] * 60 + (chain['source_first_frame'] if phase == 'source' else 0)
                note = '姿势混合中' if phase == 'blend' else f'{source} / F{native_frame:.1f}'
                d.text((22, 910), note, font=small, fill='#a6bbce')
                process.stdin.write(canvas.tobytes())
        one.stdin.close(); two.stdin.close()
        assert one.wait() == 0 and two.wait() == 0
        normal.append(path); slow.append(half)
        print('ENCODED_CHAIN', key, kind, flush=True)
    concatenate(normal, OUT / 'Unity10_chaining.mp4')
    concatenate(slow, OUT / 'Unity10_chaining_05x.mp4')


if __name__ == '__main__':
    main()
