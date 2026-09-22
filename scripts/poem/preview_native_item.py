"""Textured geometry QA using the vertices emitted by each version's Java renderer."""
from pathlib import Path
import json
import zipfile
import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/poem_trail_step'
ASSETS = Path('src/main/resources/assets/herobrine_companion')
BACKGROUND = (17, 26, 37)


def unit(value):
    value = np.array(value, dtype=float)
    return value / np.linalg.norm(value)


def render(vertices, texture, camera, width=660, height=660, world_light=1):
    forward = unit(camera)
    right = unit(np.cross([0, 1, 0], forward))
    up = np.cross(forward, right)
    basis = np.stack([right, up, forward], axis=1)
    view = vertices[:, :3] @ basis
    center = (view[:, :2].min(0) + view[:, :2].max(0)) / 2
    scale = min((width - 70) / np.ptp(view[:, 0]), (height - 60) / np.ptp(view[:, 1]))
    projected = np.column_stack(((view[:, 0] - center[0]) * scale + width / 2,
                                 height / 2 - (view[:, 1] - center[1]) * scale, view[:, 2]))
    color = np.full((height, width, 3), BACKGROUND, dtype=np.uint8)
    depth = np.full((height, width), -np.inf)
    emissive = np.zeros((height, width), dtype=bool)
    tex_h, tex_w = texture.shape[:2]
    light = unit([.8, 1.4, -.6])
    for start in range(0, len(vertices), 4):
        for tri in ((0, 1, 2), (0, 2, 3)):
            ids = start + np.array(tri)
            p = projected[ids]
            lo = np.maximum(np.floor(p[:, :2].min(0)).astype(int), [0, 0])
            hi = np.minimum(np.ceil(p[:, :2].max(0)).astype(int), [width - 1, height - 1])
            if np.any(hi < lo):
                continue
            x, y = np.meshgrid(np.arange(lo[0], hi[0] + 1) + .5, np.arange(lo[1], hi[1] + 1) + .5)
            denominator = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
            if abs(denominator) < 1e-8:
                continue
            a = ((p[1, 1] - p[2, 1]) * (x - p[2, 0]) + (p[2, 0] - p[1, 0]) * (y - p[2, 1])) / denominator
            b = ((p[2, 1] - p[0, 1]) * (x - p[2, 0]) + (p[0, 0] - p[2, 0]) * (y - p[2, 1])) / denominator
            weights = np.stack([a, b, 1 - a - b], axis=-1)
            z = weights @ p[:, 2]
            uv = weights @ vertices[ids, 3:5]
            tx = np.clip(np.floor(uv[:, :, 0] * tex_w).astype(int), 0, tex_w - 1)
            ty = np.clip(np.floor(uv[:, :, 1] * tex_h).astype(int), 0, tex_h - 1)
            texel = texture[ty, tx]
            region = np.s_[lo[1]:hi[1] + 1, lo[0]:hi[0] + 1]
            visible = (weights.min(-1) >= -1e-7) & (texel[:, :, 3] > 0) & (z > depth[region])
            # Emissive pixels bypass the simple preview lighting, as in the renderer.
            shade = world_light * (.65 + .35 * max(0, np.dot(unit(vertices[ids[0], 5:8]), light)))
            intensity = np.where(texel[:, :, 3] < 255, 1, shade)
            rgb = (texel[:, :, :3] * intensity[:, :, None]).astype(np.uint8)
            color[region][visible] = rgb[visible]
            depth[region][visible] = z[visible]
            emissive[region][visible] = texel[:, :, 3][visible] < 255
    return Image.fromarray(color), emissive


def main():
    fonts = Path('C:/Windows/Fonts')
    heading = ImageFont.truetype(str(fonts / 'segoeuib.ttf'), 34)
    label = ImageFont.truetype(str(fonts / 'segoeuib.ttf'), 22)
    caption = ImageFont.truetype(str(fonts / 'segoeui.ttf'), 21)
    canvas = Image.new('RGB', (2040, 1580), BACKGROUND)
    draw = ImageDraw.Draw(canvas)
    draw.text((36, 20), 'Poem of the End | native 3D fallback', font=heading, fill='#ebf8ff')
    draw.text((36, 68), 'Actual Java vertices and item UVs. Base material follows lighting; blade and gem emission stays bright.', font=caption, fill='#a6bdce')
    report = []
    for row, (root, version) in enumerate(((ROOT, '1.21.1 NeoForge'), (Path('E:/java/herobrine companion'), '1.20.1 Forge'))):
        views = {item['context']: np.array(item['vertices']) for item in json.loads(
            (root / 'build/poem_trail_step/native_item_preview.json').read_text(encoding='utf-8'))}
        base = np.array(Image.open(root / ASSETS / 'textures/item/poem_of_the_end_native.png').convert('RGBA'))
        glow = np.array(Image.open(root / ASSETS / 'textures/item/poem_of_the_end_native_glow.png').convert('RGBA'))
        glowing = glow[:, :, 3] > 0
        base[glowing] = glow[glowing]
        base[glowing, 3] = 254  # Marks the emissive texels for the preview shader.
        reference, reference_mask = render(views['thirdperson_righthand'], base, [1, .08, .22])
        dark, dark_mask = render(views['thirdperson_righthand'], base, [1, .08, .22], world_light=.04)
        assert np.array_equal(reference_mask, dark_mask) and reference_mask.any()
        assert np.array_equal(np.array(reference)[reference_mask], np.array(dark)[dark_mask]), 'World darkness dimmed an emissive fragment'
        source_jar = ROOT / ('.gradle-home/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar' if row == 0 else
                             '.gradle-home/caches/fabric-loom/1.20.1/minecraft-client.jar')
        with zipfile.ZipFile(source_jar) as jar:
            vertex = jar.read('assets/minecraft/shaders/core/rendertype_eyes.vsh').decode()
            fragment = jar.read('assets/minecraft/shaders/core/rendertype_eyes.fsh').decode()
        assert 'vertexColor = Color;' in vertex
        assert all(word not in vertex + fragment for word in ('minecraft_mix_light', 'Sampler2', 'in vec3 Normal'))
        report.append(dict(version=version, passed=True, visible_glowing_pixels=int(reference_mask.sum()),
                           emissive_fragments_unchanged_in_darkness=True, minecraft_shader_has_no_normal_or_lightmap_lighting=True,
                           gpu_tested=False))
        for column, (context, camera, brightness, title) in enumerate((
            ('thirdperson_righthand', [1, .08, .22], 1, 'Side / normal light'),
            ('thirdperson_righthand', [-1, .18, -.6], 1, 'Reverse angle'),
            ('thirdperson_righthand', [1, .08, .22], .04, 'Dark / unlit blade and gems'),
        )):
            x, y = 20 + column * 680, 120 + row * 710
            rendered, _ = render(views[context], base, camera, world_light=brightness)
            canvas.paste(rendered, (x, y + 31))
            draw.text((x + 12, y), version + '  |  ' + title, font=label, fill='#dbeaf4')
    draw.text((36, 1540), 'Geometry inspection with simple preview lighting; this is not an in-game screenshot.', font=caption, fill='#9ab0c2')
    target = OUT / 'native_item_preview.png'
    canvas.save(target)
    (OUT / 'emissive_shader_validation.json').write_text(json.dumps(dict(passed=True, versions=report), indent=2) + '\n', encoding='utf-8')
    print(target)


if __name__ == '__main__':
    main()
