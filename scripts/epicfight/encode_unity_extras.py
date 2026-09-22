"""Frame-accurate native/Herobrine previews for the three mouse gestures."""
import json

from PIL import Image, ImageDraw, ImageFont

from encode_unity_modes import OUT, WORK, concatenate, process

timeline = json.loads((OUT / 'resources/assets/herobrine_companion/epicfight/poem_unity09_extras.json').read_text('utf-8'))
font = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 26)
small = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 20)
title = ImageFont.truetype('C:/Windows/Fonts/msyh.ttc', 30)
colors = ['#69e0cf', '#b8a7ef', '#f2b184']
inputs = ['侧键 4＋左键', '侧键 5＋左键', '长按左键 0.35 秒 / 右键＋左键']


def caption(image, move, kind, frame, rate):
    timing = move[kind]; t = frame / 60
    contact = any(c['start'] <= t <= c['end'] for c in timing['contacts'])
    canvas = Image.new('RGB', (1600, 980), '#121e2b'); canvas.paste(image, (0, 78))
    d = ImageDraw.Draw(canvas); color = colors[move['gesture']]
    d.rectangle((0, 0, 1600, 5), fill=color)
    d.text((26, 18), 'Unity 原动作', font=title, fill='white')
    d.text((828, 18), 'Herobrine · ' + move['label'] + (' · 地面' if kind == 'ground' else ' · 空中'), font=title, fill=color)
    d.text((1400, 24), '0.5× 慢放' if rate == .5 else '1× 原速', font=font, fill='white')
    d.text((26, 888), inputs[move['gesture']] + '  ·  四种模式通用', font=font, fill='white')
    d.text((778, 888), f'素材 F{frame:03d} / {t:.3f}s', font=font, fill='#d4e2ef')
    state = '命中时段' if contact else '可接下一击' if t >= timing['recovery'] else '起手 / 收势'
    d.text((1332, 888), state, font=font, fill='#ffce82' if contact else '#a6bbce')
    note = '  |  游戏中随地形下降；此处展示素材时间轴' if move['key'] == 'heavy' and kind == 'air' else '  |  地砖边长 0.5 方块'
    d.text((26, 932), timing['source_clip'] + ' · Blender 素材预览' + note, font=small, fill='#a6bbce')
    d.line((26, 972, 1574, 972), fill='#354457', width=4)
    d.line((26, 972, 26 + int(1548 * min(1, t / timing['duration'])), 972), fill=color, width=4)
    return canvas


def main():
    normal, slow = [], []
    sheet = Image.new('RGB', (1920, 960), '#121e2b'); d = ImageDraw.Draw(sheet)
    d.text((22, 15), '终末之诗 · 三种组合攻击与长按左键重击', font=title, fill='white')
    for col, move in enumerate(timeline['moves']):
        for row, kind in enumerate(('ground', 'air')):
            timing = move[kind]; count = round(timing['duration'] * 60)
            folder = OUT / 'preview/sequence_60/extra' / timing['name']
            assert all((folder / f'{i:04d}.png').exists() for i in range(count)), timing['name']
            base = WORK / 'encoded10' / ('extra_' + timing['name'] + '.mp4')
            half = WORK / 'encoded10' / ('extra_' + timing['name'] + '_05x.mp4')
            one, two = process(base, 60), process(half, 30)
            try:
                for frame in range(count):
                    image = Image.open(folder / f'{frame:04d}.png').convert('RGB')
                    one.stdin.write(caption(image, move, kind, frame, 1).tobytes())
                    two.stdin.write(caption(image, move, kind, frame, .5).tobytes())
            finally:
                one.stdin.close(); two.stdin.close()
            assert one.wait() == 0 and two.wait() == 0
            normal.append(base); slow.append(half)
            strike = round(sum(timing['contacts'][0][k] for k in ('start', 'end')) * 30)
            image = Image.open(folder / f'{strike:04d}.png').convert('RGB').crop((790, 75, 1600, 725)).resize((640, 360))
            x, y = col * 640, 75 + row * 435
            sheet.paste(image, (x, y + 30))
            d.text((x + 14, y), move['label'] + (' · 地面' if kind == 'ground' else ' · 空中') + f' / F{strike}', font=font, fill=colors[col])
            d.text((x + 14, y + 390), inputs[col], font=small, fill='#c8d6e4')
            print('ENCODED_EXTRA', timing['name'], count, flush=True)
    concatenate(normal, OUT / 'Unity10_extra_attacks.mp4')
    concatenate(slow, OUT / 'Unity10_extra_attacks_05x.mp4')
    sheet.save(OUT / 'Unity10_extra_attacks_overview.jpg', quality=95)
    print('UNITY_EXTRA_PREVIEWS_COMPLETE', OUT, flush=True)


if __name__ == '__main__': main()
