"""Render the shipped GLSL with a real offscreen OpenGL 3.2 context; never a Minecraft screenshot."""
from pathlib import Path
import hashlib
import json
import math
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/poem_mode_fx'
sys.path.insert(0, str(OUT / 'python'))
import moderngl
import numpy as np
from PIL import Image, ImageDraw, ImageFont

SHADERS = ROOT / 'src/main/resources/assets/herobrine_companion/shaders/program'
FORGE = ROOT.parent / 'herobrine companion'
checks = []


def check(ok, message):
    if not ok:
        raise AssertionError(message)
    checks.append(message)


def perspective(width, height):
    f = 1 / math.tan(math.radians(70) / 2)
    near, far = .05, 256
    return np.array([[f * height / width, 0, 0, 0], [0, f, 0, 0],
                     [0, 0, (far + near) / (near - far), 2 * far * near / (near - far)],
                     [0, 0, -1, 0]], dtype='f4')


def depth_for(distance):
    near, far = .05, 256
    return (far + near) / (2 * (far - near)) + .5 - far * near / ((far - near) * distance)


def make_scene(width, height, foreground=False):
    y, x = np.mgrid[0:height, 0:width]
    sky = y < height * .50
    rgba = np.empty((height, width, 4), dtype='u1')
    rgba[:, :, :3] = np.stack([40 + 25 * y / height, 60 + 43 * y / height, 84 + 45 * y / height], axis=-1)
    tiles = ((x // max(1, width // 24)) + (y // max(1, height // 16))) % 2
    rgba[~sky, :3] = np.where(tiles[~sky, None] > 0, [52, 88, 76], [33, 62, 56])
    grid = (x % max(1, width // 32) < 2) | (y % max(1, height // 20) < 2)
    rgba[grid, :3] = [105, 144, 153]
    rgba[:, :, 3] = 255
    depth = np.full((height, width), depth_for(18), dtype='f4')
    mask = np.zeros((height, width), dtype=bool)
    if foreground:
        mask = (x > width * .51) & (x < width * .565) & (y > height * .24) & (y < height * .86)
        rgba[mask, :3] = [211, 90, 29]
        depth[mask] = depth_for(2)
    return rgba, depth, mask


class Renderer:
    def __init__(self, ctx, width, height):
        self.ctx, self.width, self.height = ctx, width, height
        self.program = ctx.program(vertex_shader=(SHADERS / 'void_rift_lens.vsh').read_text(),
                                   fragment_shader=(SHADERS / 'void_rift_lens.fsh').read_text())
        self.color = ctx.texture((width, height), 4, dtype='f1')
        self.depth = ctx.texture((width, height), 1, dtype='f4')
        self.output = ctx.texture((width, height), 4, dtype='f4')
        self.fbo = ctx.framebuffer([self.output])
        self.copied = ctx.texture((width, height), 4, dtype='f4')
        self.copy_fbo = ctx.framebuffer([self.copied])
        self.vbo = ctx.buffer(np.array([[0, 0, 500, 1], [width, 0, 500, 1], [width, height, 500, 1],
                                        [0, 0, 500, 1], [width, height, 500, 1], [0, height, 500, 1]], dtype='f4').tobytes())
        self.vao = ctx.vertex_array(self.program, [(self.vbo, '4f', 'Position')])
        ortho = np.array([[2 / width, 0, 0, -1], [0, 2 / height, 0, -1], [0, 0, -.002, -1], [0, 0, 0, 1]], dtype='f4')
        self.program['ProjMat'].write(ortho.T.copy().tobytes())
        self.program['InverseProjection'].write(np.linalg.inv(perspective(width, height)).T.copy().tobytes())
        self.program['InSize'].value = (width, height)
        self.program['OutSize'].value = (width, height)
        self.program['DiffuseSampler'].value = 0
        self.program['DepthSampler'].value = 1
        self.color.filter = self.depth.filter = (moderngl.NEAREST, moderngl.NEAREST)
        self.output.filter = (moderngl.NEAREST, moderngl.NEAREST)
        # Include the final framebuffer copy: testing only the lens pass misses horizon alpha bugs.
        self.copy_program = ctx.program(vertex_shader=(SHADERS / 'void_rift_lens.vsh').read_text(),
                                        fragment_shader=(SHADERS / 'void_rift_copy.fsh').read_text())
        self.copy_vao = ctx.vertex_array(self.copy_program, [(self.vbo, '4f', 'Position')])
        self.copy_program['ProjMat'].write(ortho.T.copy().tobytes())
        self.copy_program['OutSize'].value = (width, height)
        self.copy_program['DiffuseSampler'].value = 0
        reference = ROOT / 'build/poem_fx_reference/neo'
        self.legacy_program = ctx.program(vertex_shader=(reference / 'blit.vsh').read_text(),
                                          fragment_shader=(reference / 'blit.fsh').read_text())
        self.legacy_vao = ctx.vertex_array(self.legacy_program, [(self.vbo, '4f', 'Position')])
        self.legacy_program['ProjMat'].write(ortho.T.copy().tobytes())
        self.legacy_program['OutSize'].value = (width, height)
        self.legacy_program['DiffuseSampler'].value = 0
        self.legacy_program['ColorModulate'].value = (1, 1, 1, 1)

    def draw(self, rgba, depth, rifts, legacy_copy=False):
        self.color.write(np.flipud(rgba).copy().tobytes())
        self.depth.write(np.flipud(depth).copy().tobytes())
        self.color.use(0)
        self.depth.use(1)
        self.program['RiftCount'].value = len(rifts)
        for i in range(8):
            shape, state = rifts[i] if i < len(rifts) else ((0, 0, 0, 0), (1, 0, 0, 0))
            self.program[f'Rift{i}'].value = shape
            self.program[f'RiftState{i}'].value = state
        self.fbo.use()
        self.ctx.viewport = (0, 0, self.width, self.height)
        self.ctx.disable(moderngl.DEPTH_TEST | moderngl.BLEND | moderngl.CULL_FACE)
        self.vao.render(mode=moderngl.TRIANGLES)
        self.copy_fbo.use()
        self.copy_fbo.clear(0, 0, 0, 0)
        self.output.use(0)
        if legacy_copy:
            self.ctx.enable(moderngl.BLEND)
            self.ctx.blend_func = (moderngl.SRC_ALPHA, moderngl.ONE_MINUS_SRC_ALPHA)
            self.legacy_vao.render(mode=moderngl.TRIANGLES)
        else:
            self.ctx.disable(moderngl.BLEND)
            self.copy_vao.render(mode=moderngl.TRIANGLES)
        output = np.frombuffer(self.copy_fbo.read(components=4, dtype='f4'), dtype='f4').reshape(self.height, self.width, 4)
        check(np.isfinite(output).all(), f'Finite output at {self.width}x{self.height}, {len(rifts)} rifts')
        return np.flipud(output).copy()

    def close(self):
        for obj in (self.vao, self.copy_vao, self.legacy_vao, self.vbo, self.fbo, self.copy_fbo,
                    self.output, self.copied, self.depth, self.color, self.program, self.copy_program, self.legacy_program):
            obj.release()


def png(array):
    return Image.fromarray(np.round(np.clip(array, 0, 1) * 255).astype('u1'), 'RGBA').convert('RGB')


def main():
    OUT.mkdir(exist_ok=True, parents=True)
    ctx = moderngl.create_standalone_context(require=320)
    hashes = {}
    for name in ('void_rift_lens.vsh', 'void_rift_lens.fsh', 'void_rift_lens.json', 'void_rift_copy.fsh', 'void_rift_copy.json'):
        payload = (SHADERS / name).read_bytes()
        check(payload == (FORGE / SHADERS.relative_to(ROOT) / name).read_bytes(), f'Identical shader resource: {name}')
        hashes[name] = hashlib.sha256(payload).hexdigest()
    samples = []
    horizon_samples = []
    horizon_metrics = []
    for version in ('neo', 'forge'):
        vanilla_blend = json.loads((ROOT / f'build/poem_fx_reference/{version}/blit.json').read_text())['blend']
        check(vanilla_blend['srcrgb'] == 'srcalpha' and vanilla_blend['dstrgb'] == '1-srcalpha',
              f'{version} vanilla blit uses alpha compositing, not an opaque scene copy')
    copy_blend = json.loads((SHADERS / 'void_rift_copy.json').read_text())['blend']
    check(copy_blend['srcrgb'] == 'one' and copy_blend['dstrgb'] == 'zero', 'Shipped final pass overwrites RGB without alpha attenuation')
    largest_change = 0
    for w, h in ((960, 540), (512, 512), (720, 405), (360, 640)):
        renderer = Renderer(ctx, w, h)
        declared = json.loads((SHADERS / 'void_rift_lens.json').read_text())
        for uniform in declared['uniforms']:
            check(uniform['name'] in renderer.program, f'Active uniform {uniform["name"]} at {w}x{h}')
        rgba, depth, _ = make_scene(w, h)
        original = rgba.astype('f4') / 255
        off = renderer.draw(rgba, depth, [])
        check(np.max(np.abs(original - off)) < 1e-6, f'No-rift pass is identical at {w}x{h}')
        shape = (.5, .5, .225, .52)
        state = (5.2, 1, 1.9, .43)
        single = renderer.draw(rgba, depth, [(shape, state)])
        y, x = np.mgrid[0:h, 0:w]
        u, v = (x + .5) / w, 1 - (y + .5) / h
        radius = np.sqrt(((u - .5) * (w / h) / .225) ** 2 + ((v - .5) / .225) ** 2)
        check(np.max(np.abs(single[radius >= 1.601] - original[radius >= 1.601])) < 1e-6,
              f'Pixels outside lens remain identical at {w}x{h}')
        check(np.array_equal(single[:, :, 3], original[:, :, 3]), f'Alpha preserved at {w}x{h}')
        change = np.abs(single[:, :, :3] - original[:, :, :3]).max(axis=2)
        # Away from the crack, a changed grid establishes actual scene refraction, not only edge glow.
        annulus = (radius > .4) & (radius < 1.1)
        significant = int(((change > .075) & annulus).sum())
        check(significant > w * h * .0007, f'Visible background refraction at {w}x{h}: {significant} pixels')
        largest_change = max(largest_change, significant)
        hidden = renderer.draw(rgba, np.full_like(depth, depth_for(2)), [(shape, state)])
        check(np.max(np.abs(hidden - original)) < 1e-6, f'Fully occluded rift is invisible at {w}x{h}')
        foreground, foreground_depth, mask = make_scene(w, h, True)
        blocked = renderer.draw(foreground, foreground_depth, [(shape, state)])
        check(np.max(np.abs(blocked[mask] - foreground.astype('f4')[mask] / 255)) < 1e-6,
              f'Foreground geometry remains unchanged at {w}x{h}')
        check(not np.any((blocked[~mask, 0] > .6) & (blocked[~mask, 1] < .45) & (blocked[~mask, 2] < .3)),
              f'Foreground color is not pulled into surrounding pixels at {w}x{h}')
        closed = renderer.draw(rgba, depth, [(shape, (5.2, 0, 1.9, 1.5))])
        check(np.max(np.abs(closed - original)) < 1e-6, f'Closed rift leaves no residue at {w}x{h}')
        fading = renderer.draw(rgba, depth, [(shape, (5.2, .25, 1.9, .43))])
        check(np.mean(np.abs(fading - original)) < np.mean(np.abs(single - original)), f'Fade reduces effect at {w}x{h}')
        many = [((.15 + i * .1, .42 + .10 * (i % 2), .12, i * .63), (9 - i * .6, 1, i * .8, .5)) for i in range(8)]
        multiple = renderer.draw(rgba, depth, many)
        check(np.mean(np.abs(multiple - original)) > .001, f'All 8 rift slots render at {w}x{h}')
        # Sky renders may leave alpha < 1 in the world framebuffer. Reproduce the user's dark horizon
        # with the real vanilla tail pass, then verify every RGB/alpha pixel through the shipped tail.
        horizon_scene = rgba.copy()
        horizon_depth = depth.copy()
        horizon_depth[y < h * .50] = 1.0
        band = (y >= h * .455) & (y < h * .505)
        horizon_scene[band, 3] = 0
        horizon_scene[(y >= h * .505) & (y < h * .53), 3] = 64
        horizon_expected = horizon_scene.astype('f4') / 255
        legacy_horizon = renderer.draw(horizon_scene, horizon_depth, [(shape, state)], legacy_copy=True)
        fixed_horizon = renderer.draw(horizon_scene, horizon_depth, [(shape, state)])
        outside = radius >= 1.601
        check(np.max(np.abs(fixed_horizon[outside] - horizon_expected[outside])) < 1e-6,
              f'Complete pipeline preserves low-alpha sky/horizon at {w}x{h}')
        check(np.max(legacy_horizon[band, :3]) < 1e-6, f'Reproduced old black horizon band at {w}x{h}')
        check(np.mean(fixed_horizon[band & (radius > .5), :3]) > .18, f'Fixed horizon remains lit at {w}x{h}')
        check(np.array_equal(fixed_horizon[:, :, 3], horizon_expected[:, :, 3]), f'Copy also preserves original alpha at {w}x{h}')
        for alpha in (0, 1, 64, 128, 254, 255):
            test = rgba.copy()
            test[:, :, 3] = alpha
            clean = renderer.draw(test, horizon_depth, [])
            check(np.max(np.abs(clean - test.astype('f4') / 255)) < 1e-6, f'Complete copy preserves RGB at alpha {alpha}, {w}x{h}')
        horizon_metrics.append({'resolution': f'{w}x{h}', 'legacy_band_rgb_max': float(legacy_horizon[band, :3].max()),
                                'fixed_unaffected_rgba_error': float(np.abs(fixed_horizon[outside] - horizon_expected[outside]).max())})
        if w == 960:
            horizon_samples = [('Reproduced: vanilla alpha-blended copy', png(legacy_horizon)),
                               ('Fixed: complete scene-color copy', png(fixed_horizon))]
        for edge_center in ((-.05, .5), (1.05, .5), (.5, -.05), (.5, 1.05)):
            renderer.draw(rgba, depth, [((*edge_center, .25, -.5), state)])
        if w == 960:
            for label, array in (('Original test scene', original), ('Rift and world distortion', single),
                                 ('Foreground depth occlusion', blocked), ('Eight simultaneous rifts', multiple)):
                samples.append((label, png(array)))
            frames = []
            for age in np.linspace(0, 31, 48):
                def smooth(t):
                    t = min(1, max(0, t))
                    return t * t * (3 - 2 * t)
                strength = smooth(age / 2) * smooth((31 - age) / 6)
                frame = png(renderer.draw(rgba, depth, [(shape, (5.2, strength, 1.9, age / 20))]))
                frame = frame.resize((640, 360))
                draw = ImageDraw.Draw(frame)
                draw.rectangle((0, 0, 640, 26), fill=(12, 15, 23))
                draw.text((9, 7), 'OFFSCREEN GLSL TEST - NOT A MINECRAFT SCREENSHOT', fill=(223, 223, 235))
                frames.append(frame)
            frames[0].save(OUT / 'rift_shader_preview.gif', save_all=True, append_images=frames[1:], duration=40, loop=0)
        renderer.close()
    canvas = Image.new('RGB', (1440, 982), (13, 17, 26))
    draw = ImageDraw.Draw(canvas)
    title_font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 27)
    label_font = ImageFont.truetype('C:/Windows/Fonts/arial.ttf', 19)
    draw.text((24, 20), 'VOID SHATTER / PROCEDURAL RIFT + SCENE LENSING', font=title_font, fill=(231, 229, 246))
    draw.text((24, 58), 'Actual OpenGL 3.2 shader output on a test grid. Not a Minecraft gameplay screenshot.',
              font=label_font, fill=(161, 174, 198))
    for i, (label, sample) in enumerate(samples):
        x, y = 24 + (i % 2) * 708, 110 + (i // 2) * 428
        draw.text((x, y), label, font=label_font, fill=(228, 226, 241))
        canvas.paste(sample.resize((684, 385)), (x, y + 29))
    canvas.save(OUT / 'rift_shader_preview.png')
    comparison = Image.new('RGB', (1440, 560), (13, 17, 26))
    draw = ImageDraw.Draw(comparison)
    draw.text((24, 20), 'HORIZON BLACK-BAND REGRESSION / FULL TWO-PASS PIPELINE', font=title_font, fill=(231, 229, 246))
    draw.text((24, 61), 'Real OpenGL output with a low-alpha horizon. Test scene, not a Minecraft screenshot.',
              font=label_font, fill=(161, 174, 198))
    for i, (label, sample) in enumerate(horizon_samples):
        x = 24 + i * 708
        draw.text((x, 106), label, font=label_font, fill=(228, 226, 241))
        comparison.paste(sample.resize((684, 385)), (x, 140))
    comparison.save(OUT / 'horizon_black_band_fix.png')
    report = {'status': 'passed', 'checks': len(checks), 'assertions': checks,
              'renderer': ctx.info['GL_RENDERER'], 'gl_version': ctx.info['GL_VERSION'],
              'glsl_version': 150, 'shader_sha256': hashes, 'resolutions': ['960x540', '512x512', '720x405', '360x640'],
              'max_refracted_grid_pixels': largest_change, 'live_gameplay_tested': False}
    report['full_two_pass_pipeline_tested'] = True
    report['horizon_regression'] = horizon_metrics
    (OUT / 'shader_validation.json').write_text(json.dumps(report, indent=2), encoding='utf8')
    print(f'SHADER_OK: {len(checks)} checks, {ctx.info["GL_RENDERER"]}; real GLSL compile/render; no live gameplay test')
    ctx.release()


if __name__ == '__main__':
    main()
