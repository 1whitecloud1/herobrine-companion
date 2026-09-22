"""Reuse the calibrated mesh in the existing GeckoLib model space, without a runtime dependency."""
from pathlib import Path
import hashlib
import json
import numpy as np
from scipy.spatial import cKDTree
from scipy.spatial.transform import Rotation
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSET_PATH = Path('src/main/resources/assets/herobrine_companion')


def generate(root):
    assets = root / ASSET_PATH
    rig = json.loads((assets / 'poem_standalone/rig.json').read_text(encoding='utf-8'))
    # Forge applies these same display values in its renderer; only NeoForge
    # stores them in the builtin/entity JSON. Both share the calibrated mesh.
    model = json.loads((ROOT / ASSET_PATH / 'models/item/poem_of_the_end.json').read_text(encoding='utf-8'))
    # Inverse of Epic Fight's Tool_R correction, the existing display translation,
    # and GeoObjectRenderer's 0.01-block vertical model-origin adjustment.
    correction = np.eye(4)
    angle = np.float32(-np.pi / 2)
    c, s = float(np.cos(angle)), float(np.sin(angle))
    correction[:3, :3] = [[1, 0, 0], [0, c, -s], [0, s, c]]
    correction[2, 3] = -.13
    display = np.eye(4)
    display[:3, 3] = np.array(model['display']['thirdperson_righthand']['translation']) / 16
    origin = np.eye(4); origin[1, 3] = .01
    mesh_to_model = np.linalg.inv(correction @ display @ origin) @ np.array(rig['weapon_to_tool'])
    setup = dict(format=1, mesh_to_model=mesh_to_model.reshape(-1).tolist(), display=model['display'],
                 source_mesh_sha256=hashlib.sha256((assets / 'poem_standalone/weapon.json').read_bytes()).hexdigest())
    texture_path = assets / 'textures/item/poem_of_the_end_geo.png'
    texture = np.array(Image.open(texture_path).convert('RGBA'))
    metadata = json.loads(texture_path.with_suffix('.png.mcmeta').read_text(encoding='utf-8'))
    glowing = np.zeros(texture.shape[:2], dtype=bool)
    for section in metadata['glowsections']['sections']:
        assert section['alpha'] == 255, 'Native glow sections must be opaque'
        glowing[section['y1']:section['y2'] + 1, section['x1']:section['x2'] + 1] = True
    assert np.array_equal(glowing, (texture[:, :, 3] > 0) & (texture[:, :, 3] < 255)), 'Glow metadata and atlas marking disagree'
    assert glowing.any(), 'Expected the existing blade edging and gem glow pixels'
    base = texture.copy(); base[glowing, 3] = 0
    glow = np.zeros_like(texture); glow[glowing] = texture[glowing]; glow[glowing, 3] = 255
    # Independently reconstruct every original Bedrock cube corner to check the
    # reused canonical mesh against the actual model, including all bone pivots.
    geo_path = assets / 'geo/item/poem_of_the_end.geo.json'
    geo = json.loads(geo_path.read_text(encoding='utf-8'))['minecraft:geometry'][0]
    bones = {bone['name']: bone for bone in geo['bones']}; worlds = {}
    def transform(bone):
        pivot = np.array(bone.get('pivot', [0, 0, 0]))
        rotation = Rotation.from_euler('xyz', np.array(bone.get('rotation', [0, 0, 0])) * [-1, 1, -1], degrees=True).as_matrix()
        result = np.eye(4); result[:3, :3] = rotation; result[:3, 3] = pivot - rotation @ pivot
        return result
    def world(name):
        if name not in worlds:
            bone = bones[name]
            worlds[name] = (world(bone['parent']) if 'parent' in bone else np.eye(4)) @ transform(bone)
        return worlds[name]
    expected = []
    for bone in geo['bones']:
        for cube in bone.get('cubes', []):
            matrix = world(bone['name']) @ transform(cube)
            # Bedrock inflate is applied in cube space, before either pivot rotation.
            # Seventy decorative cubes use it (including negative values).
            inflate = cube.get('inflate', bone.get('inflate', 0))
            lower = np.array(cube['origin']) - inflate
            size = np.array(cube['size']) + 2 * inflate
            for corner in np.ndindex(2, 2, 2):
                point = (matrix @ np.r_[lower + np.array(corner) * size, 1])[:3]
                expected.append(point * [-1, 1, 1] / 16)
    mesh = json.loads((assets / 'poem_standalone/weapon.json').read_text(encoding='utf-8'))
    actual = np.array(mesh['vertices']) @ mesh_to_model[:3, :3].T + mesh_to_model[:3, 3]
    error = max(float(cKDTree(expected).query(actual)[0].max()), float(cKDTree(actual).query(expected)[0].max()))
    assert error < .00002, ('Native mesh differs from the original GeckoLib model', error)
    report = dict(passed=True, original_cube_corners=len(expected), native_vertices=len(actual), maximum_model_space_error=error,
                  inflated_cubes=sum('inflate' in c for b in geo['bones'] for c in b.get('cubes', [])),
                  glowing_pixels=int(glowing.sum()), glow_sections=len(metadata['glowsections']['sections']),
                  source_texture_sha256=hashlib.sha256(texture_path.read_bytes()).hexdigest(),
                  source_geometry_sha256=hashlib.sha256(geo_path.read_bytes()).hexdigest())
    (assets / 'poem_standalone/item_model.json').write_text(json.dumps(setup, indent=2) + '\n', encoding='utf-8')
    Image.fromarray(base).save(assets / 'textures/item/poem_of_the_end_native.png', optimize=True)
    Image.fromarray(glow).save(assets / 'textures/item/poem_of_the_end_native_glow.png', optimize=True)
    report_path = root / 'build/poem_trail_step/native_model_source_validation.json'
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(root.name, 'native item setup and glow masks generated:', int(glowing.sum()), 'glowing pixels')
    print('Original 3D model match:', error, 'blocks maximum error')


if __name__ == '__main__':
    generate(ROOT)
