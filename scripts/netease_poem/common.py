"""Offline Bedrock geometry / animation math. Never shipped into ModSDK."""
import json
import math
from pathlib import Path

import numpy as np
from scipy.spatial.transform import Rotation, Slerp

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/herobrine_companion'
WORK = ROOT / 'build/netease_poem_longcombo_v1'
OUT = ROOT / 'output/Herobrine_Poem_NetEase_LongCombo_01'
ADDONS = Path('E:/MCStudioDownload/work/m13525918851@163.com/Cpp/AddOn')
STORY = ADDONS / 'adfe8b2fa8444c3aaffbeca53d5ef7d8'
CORE = ADDONS / '3055269d404f439eba5592a9d4b52113'
REQUIRED = ADDONS / 'a6466d08608f4264848e2737b365074d'
RP = WORK / 'resource_pack'
BP = WORK / 'behavior_pack'
# DCC: right, forward, up. Bedrock JSON: left, up, backward, in pixels.
AXES = np.array([[-1., 0., 0.], [0., 0., 1.], [0., -1., 0.]])
B = np.eye(4)
B[:3, :3] = 16 * AXES
BI = np.linalg.inv(B)
SIGNS = np.array([-1., 1., -1.])
PREFIX = 'hc_poem_v1_'
QUERY = 'query.mod.hc_poem_v1_'


def read(path):
    return json.loads(Path(path).read_text(encoding='utf-8-sig'))


def write(path, data, pretty=False):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, allow_nan=False,
                              indent=2 if pretty else None,
                              separators=None if pretty else (',', ':')) + '\n', encoding='utf-8')


def translation(p):
    m = np.eye(4)
    m[:3, 3] = p
    return m


def bedrock_rotation(degrees):
    # Bedrock rotations in unreflected JSON coordinates, matching the existing
    # geometry calibration (export_v4.geo_points) and Blockbench XYZ convention.
    return Rotation.from_euler('xyz', np.asarray(degrees) * SIGNS, degrees=True).as_matrix()


def bedrock_euler(matrix):
    return Rotation.from_matrix(matrix).as_euler('xyz', degrees=True) * SIGNS


def continuous_eulers(matrices):
    """Choose equivalent XYZ solutions, avoiding the +/-180 and gimbal jumps."""
    angles = Rotation.from_matrix(matrices).as_euler('xyz', degrees=True)
    out = [angles[0]]
    for value in angles[1:]:
        other = np.array([value[0] + 180., 180. - value[1], value[2] + 180.])
        options = [x + 360. * np.round((out[-1] - x) / 360.) for x in (value, other)]
        out.append(min(options, key=lambda x: np.linalg.norm(x - out[-1])))
    return np.array(out) * SIGNS


def cube_matrix(obj):
    pivot = np.array(obj.get('pivot', [0, 0, 0]), dtype=float)
    m = np.eye(4)
    m[:3, :3] = bedrock_rotation(obj.get('rotation', [0, 0, 0]))
    m[:3, 3] = pivot - m[:3, :3] @ pivot
    return m


def geometry_points(geometry):
    """Raw Bedrock cube corners, including authored bone and cube rotations."""
    world, result = {}, []
    for bone in geometry['bones']:
        world[bone['name']] = world.get(bone.get('parent'), np.eye(4)) @ cube_matrix(bone)
        for cube in bone.get('cubes', []):
            m = world[bone['name']] @ cube_matrix(cube)
            points = np.array(cube['origin']) + np.array(list(np.ndindex(2, 2, 2))) * cube['size']
            result.extend((np.c_[points, np.ones(8)] @ m.T)[:, :3])
    return np.array(result)


def decompose_series(matrices):
    m = np.asarray(matrices)
    scales = np.linalg.norm(m[:, :3, :3], axis=1)
    rough = m[:, :3, :3] / scales[:, None, :]
    u, _, vt = np.linalg.svd(rough)
    r = u @ vt
    assert np.min(np.linalg.det(r)) > .999
    residual = np.max(np.abs(m[:, :3, :3] - r * scales[:, None, :]))
    return m[:, :3, 3], continuous_eulers(r), scales, float(residual)


def interpolate_matrices(times, matrices, sample_times):
    out = np.tile(np.eye(4), (len(sample_times), 1, 1))
    scale = np.linalg.norm(matrices[:, :3, :3], axis=1)
    r = matrices[:, :3, :3] / scale[:, None, :]
    u, _, vt = np.linalg.svd(r)
    rotations = Slerp(times, Rotation.from_matrix(u @ vt))(sample_times).as_matrix()
    for axis in range(3):
        out[:, axis, 3] = np.interp(sample_times, times, matrices[:, axis, 3])
        out[:, :3, axis] = rotations[:, :3, axis] * np.interp(sample_times, times, scale[:, axis])[:, None]
    return out


def source_rig():
    rig = read(ASSETS / 'poem_standalone/rig.json')
    names = [j['name'] for j in rig['joints']]
    rest = {}
    for j in rig['joints']:
        local = np.array(j['bind']).reshape(4, 4)
        rest[j['name']] = (rest[names[j['parent']]] if j['parent'] >= 0 else np.eye(4)) @ local
    return rig, names, rest


def source_clip(relative, duration=None, hz=60):
    rig, names, rest = source_rig()
    data = read(ASSETS / 'animmodels/animations/player/poem_unity09' / (relative + '.json'))
    tracks = {t['name']: t for t in data['animation']}
    end = min(duration or 1e9, tracks['Root']['time'][-1])
    times = np.unique(np.r_[np.arange(0, end, 1. / hz), end])
    native_world = {}
    for j in rig['joints']:
        track = tracks[j['name']]
        local = np.array(track['transform']).reshape(-1, 4, 4)
        native_world[j['name']] = (native_world[names[j['parent']]] @ local if j['parent'] >= 0 else local)
    world = {n: interpolate_matrices(np.array(tracks[n]['time']), w, times) for n, w in native_world.items()}
    return times, world


def curve(times, values, digits=5):
    values = np.round(values, digits)
    if np.max(np.abs(values - values[0])) < 10 ** (-digits):
        return values[0].tolist()
    # Keep every sample: fast spinning blades need angle winding and 60 Hz keys.
    return {format(float(t), '.6f').rstrip('0').rstrip('.') or '0': v.tolist()
            for t, v in zip(times, values)}


def sample_curve(value, time):
    if not isinstance(value, dict):
        return np.asarray(value if isinstance(value, list) else [value] * 3, dtype=float)
    keys = sorted((float(t), v) for t, v in value.items())
    ix = np.searchsorted([t for t, _ in keys], time, side='right')
    if ix == 0:
        return np.array(keys[0][1])
    if ix == len(keys):
        return np.array(keys[-1][1])
    t0, v0 = keys[ix - 1]
    t1, v1 = keys[ix]
    return np.array(v0) + (np.array(v1) - v0) * ((time - t0) / (t1 - t0))


def sample_geometry(geometry, animation, time):
    """Independently read the delivered JSON, as the preview and verifier do."""
    world = {}
    for bone in geometry['bones']:
        name = bone['name']
        track = animation.get('bones', {}).get(name.lower(), animation.get('bones', {}).get(name, {}))
        p = np.array(bone.get('pivot', [0, 0, 0]), dtype=float)
        position = sample_curve(track.get('position', [0, 0, 0]), time)
        rot = np.array(bone.get('rotation', [0, 0, 0])) + sample_curve(track.get('rotation', [0, 0, 0]), time)
        scale = sample_curve(track.get('scale', [1, 1, 1]), time)
        m = np.eye(4)
        m[:3, :3] = bedrock_rotation(rot) @ np.diag(scale)
        m[:3, 3] = p + position - m[:3, :3] @ p
        world[name] = world.get(bone.get('parent'), np.eye(4)) @ m
    return world
