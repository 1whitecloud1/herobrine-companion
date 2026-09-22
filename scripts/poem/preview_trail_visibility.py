"""Check the actual exported trail vertices, UVs, opacity and blend equations."""
from pathlib import Path
import json
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/poem_trail_step'
SIZE = 540


def normalized(p):
    p = np.array(p, dtype=float)
    return p / np.linalg.norm(p)


def render(frame, texture, background):
    forward = normalized([5, -6, 3])
    right = normalized(np.cross([0, 0, 1], forward))
    up = np.cross(forward, right)
    basis = np.stack([right, up, forward], axis=1)
    layers = [np.array(frame[key]) for key in ('body_vertices', 'glow_vertices')]
    for layer in layers:
        p = layer[:, :3].copy()
        layer[:, :3] = np.column_stack([-p[:, 0], -p[:, 2], 1.5 - p[:, 1]]) @ basis
    points = np.vstack(layers)
    center = (points[:, :2].min(0) + points[:, :2].max(0)) / 2
    scale = min((SIZE - 50) / np.ptp(points[:, 0]), (SIZE - 90) / np.ptp(points[:, 1]))
    pixels = np.full((SIZE, SIZE, 3), background, dtype=float) / 255
    for number, layer in enumerate(layers):
        layer[:, 0] = (layer[:, 0] - center[0]) * scale + SIZE / 2
        layer[:, 1] = SIZE / 2 - (layer[:, 1] - center[1]) * scale
        quads = layer.reshape(-1, 4, 8)
        if number == 0: quads = quads[np.argsort(quads[:, :, 2].mean(1))]
        for quad in quads:
            covered = np.zeros((SIZE, SIZE), dtype=bool)
            for indices in ((0, 1, 2), (0, 2, 3)):
                p = quad[list(indices)]
                lo = np.maximum(np.floor(p[:, :2].min(0)).astype(int), 0)
                hi = np.minimum(np.ceil(p[:, :2].max(0)).astype(int), SIZE - 1)
                x, y = np.meshgrid(np.arange(lo[0], hi[0] + 1) + .5, np.arange(lo[1], hi[1] + 1) + .5)
                denominator = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
                if abs(denominator) < 1e-8: continue
                a = ((p[1, 1] - p[2, 1]) * (x - p[2, 0]) + (p[2, 0] - p[1, 0]) * (y - p[2, 1])) / denominator
                b = ((p[2, 1] - p[0, 1]) * (x - p[2, 0]) + (p[0, 0] - p[2, 0]) * (y - p[2, 1])) / denominator
                weights = np.stack([a, b, 1 - a - b], axis=-1)
                uv = weights @ p[:, 3:5]
                u = np.clip((uv[:, :, 0] * texture.shape[1]).astype(int), 0, texture.shape[1] - 1)
                v = np.clip((uv[:, :, 1] * texture.shape[0]).astype(int), 0, texture.shape[0] - 1)
                texel = texture[v, u] / 255
                alpha = (weights @ p[:, 5]) / 255 * texel[:, :, 3]
                region = np.s_[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
                visible = (weights.min(-1) >= -1e-7) & ~covered[region]
                destination = pixels[region]
                source = texel[:, :, :3] * alpha[:, :, None]
                composite = source + destination * (1 - alpha[:, :, None]) if number == 0 else source + destination
                destination[visible] = np.clip(composite[visible], 0, 1)
                covered[region][visible] = True
    image = (pixels * 255).astype(np.uint8)
    difference = np.linalg.norm(image.astype(float) - background, axis=-1)
    visible_pixels = int((difference > 35).sum())
    assert visible_pixels > 1000, (frame['mode'], frame['step'], 'Trail lacks visible background contrast', visible_pixels)
    return Image.fromarray(image), visible_pixels


def main():
    frames = json.loads((OUT / 'trail_preview.json').read_text(encoding='utf-8'))
    texture = np.array(Image.open(ROOT / 'src/main/resources/assets/herobrine_companion/textures/trail/poem_standalone_trail.png').convert('RGBA'))
    canvas = Image.new('RGB', (2240, 1300), '#111a25')
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 23)
    heading = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 34)
    draw.text((28, 17), 'Standalone blade trail | stronger cyan body and luminous edge', font=heading, fill='#ebf8ff')
    draw.text((28, 66), 'Actual Java trail vertices and texture, composited over dark and bright backgrounds.', font=font, fill='#b4cadb')
    labels = ['Normal', 'Realm breaker', 'Thunder', 'Void shatter']
    reports = []
    for row, background in enumerate(([17, 26, 37], [223, 232, 238])):
        for frame in frames:
            image, visible = render(frame, texture, background)
            reports.append(dict(mode=frame['mode'], step=frame['step'], background='dark' if row == 0 else 'bright', visible_pixels=visible))
            if frame['step'] != 0: continue
            x, y = 20 + frame['mode'] * 555, 126 + row * 566
            canvas.paste(image, (x, y))
            draw.text((x + 10, y + 8), labels[frame['mode']] + (' / dark' if row == 0 else ' / bright'), font=font,
                      fill='#d9effa' if row == 0 else '#1f3347')
    draw.text((28, 1260), 'Texture/blending inspection only; this is not an in-game screenshot.', font=font, fill='#9ab1c3')
    canvas.save(OUT / 'trail_visibility_preview.png')
    (OUT / 'trail_visibility_validation.json').write_text(json.dumps(dict(passed=True, checked_background_poses=len(reports),
        gpu_tested=False, results=reports), indent=2) + '\n', encoding='utf-8')
    print('POEM_TRAIL_VISIBILITY_OK', len(reports), 'background poses;', OUT / 'trail_visibility_preview.png')


if __name__ == '__main__':
    main()
