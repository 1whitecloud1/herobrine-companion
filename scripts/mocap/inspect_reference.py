"""Extract source frames without changing the source time base."""
from pathlib import Path
import json
import cv2
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_mocap'
cap = cv2.VideoCapture(str(WORK / 'reference.mp4'))
fps = cap.get(cv2.CAP_PROP_FPS)
frames = []
while True:
    ok, frame = cap.read()
    if not ok:
        break
    frames.append(frame)
cap.release()
(WORK / 'frames').mkdir(exist_ok=True)
for i, frame in enumerate(frames):
    cv2.imwrite(str(WORK / 'frames' / f'{i:04d}.png'), frame)
tile_w, tile_h = 426, 236
sheet = Image.new('RGB', (tile_w * 4, tile_h * 9), (22, 24, 29))
draw = ImageDraw.Draw(sheet)
for k in range(36):
    i = round(k * (len(frames)-1) / 35)
    im = Image.fromarray(cv2.cvtColor(frames[i], cv2.COLOR_BGR2RGB))
    im.thumbnail((tile_w, tile_h - 23))
    x, y = (k % 4) * tile_w, (k // 4) * tile_h
    sheet.paste(im, (x, y + 23))
    draw.text((x+8, y+5), f'Frame {i:03d}   {i/fps:.3f}s', fill='white')
sheet.save(WORK / 'reference_contact_sheet.jpg', quality=94)
info = {'fps': fps, 'frame_count': len(frames), 'duration': len(frames)/fps,
        'width': frames[0].shape[1], 'height': frames[0].shape[0],
        'source_url': 'https://www.bilibili.com/video/BV1JH4y1r7po/',
        'source_title': '镰刀连击', 'source_author': 'Pirate_Gn'}
(WORK / 'reference_metadata.json').write_text(json.dumps(info, ensure_ascii=False, indent=2), encoding='utf-8')
print(json.dumps(info, ensure_ascii=True))
