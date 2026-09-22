"""Export the delivered V4 .blend to Epic Fight MATRIX clips (Blender 5 Python).

Run blender --background --factory-startup --disable-autoexec <V4.blend>
    --python scripts/epicfight/export_v4.py
The source .blend is read only. Outputs are relative to this repository.
"""
import hashlib
import json
import math
from pathlib import Path

import bpy
import numpy as np
from mathutils import Euler, Matrix, Vector

REPO = Path(__file__).resolve().parents[2]
OUT = REPO / 'build/epicfight-v4'
ASSETS = REPO / 'src/main/resources/assets/herobrine_companion'
DEST = ASSETS / 'animmodels/animations/hero'
HZ = 120
DURATION = 4.2
ACTION = 'HB_Scythe_V4_Quaternius_Heavy_Combo'
SCALE = 10 / 16  # BSS rig: 10 pixels/unit; Epic Fight: 16 pixels/block.
ROOT_CLEARANCE = .023  # EF biped feet differ slightly from the source block feet.
C = np.diag([-1., -1., 1.])  # source -Y forward/+X left -> EF +Y forward/+X right
# The source action is a *sword* combo, so it says nothing about which side of the
# pole a scythe's hook faces; the V6 pipeline had to solve that roll from video
# (scripts/mocap/weapon_contact.py: "blade roll ... not identifiable from the video",
# preferred_roll = pi). Without it the scythe renders with the blade rolled to the
# wrong side ("刀刃朝向反了"). Rolling the mesh 180 degrees about its own pole axis -
# the blend's bone-space +Z, which passes through the mesh origin - fixes the hook
# side while leaving the pole direction, the grip and every body joint untouched.
# scripts/epicfight/fix_v4_weapon_roll.py applies this same roll to already delivered
# clips; keep the two in sync.
WEAPON_POLE_ROLL_DEGREES = 180.
_POLE_ROLL = np.array(Matrix.Rotation(math.radians(WEAPON_POLE_ROLL_DEGREES), 3, 'Z'))
MAPPING = {
    'Root': 'Bone.011', 'Torso': 'Body', 'Chest': 'Chest', 'Head': 'Head',
    'Arm_R': 'Arm:Right:Upper', 'Hand_R': 'Arm:Right:Lower',
    'Arm_L': 'Arm:Left:Upper', 'Hand_L': 'Arm:Left:Lower',
    'Thigh_R': 'Leg:Right:Upper', 'Leg_R': 'Leg:Right:Lower',
    'Thigh_L': 'Leg:Left:Upper', 'Leg_L': 'Leg:Left:Lower',
}
# Exact tick boundaries; edited only after examining the baked motion's speed peaks.
SEGMENTS = [(0., 1.1), (1.1, 2.05), (2.05, 3.05), (3.05, 4.2)]
HIT_WINDOWS = [(.45, .83), (1.55, 1.9), (2.6, 2.9), (3.65, 4.)]


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, value, pretty=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, allow_nan=False,
                               indent=2 if pretty else None,
                               separators=None if pretty else (',', ':')) + '\n', encoding='utf-8')


def affine(r=np.eye(3), p=(0, 0, 0)):
    m = np.eye(4)
    m[:3, :3], m[:3, 3] = r, p
    return m


def rotation(m):
    u, _, vt = np.linalg.svd(m[:3, :3])
    r = u @ vt
    assert np.linalg.det(r) > 0.99
    return r


def rx(degrees):
    return np.array(Matrix.Rotation(math.radians(degrees), 4, 'X'))


def geo_points(geo):
    """Bedrock vertices before handedness conversion, matched by bone/cube/corner."""
    bones = {b['name']: b for b in geo['bones']}
    worlds = {}
    def transform(b):
        p = np.array(b.get('pivot', [0, 0, 0]), dtype=float)
        e = [math.radians(v*s) for v, s in zip(b.get('rotation', [0, 0, 0]), (-1, 1, -1))]
        r = np.array(Euler(e, 'XYZ').to_matrix())
        return affine(r, p-r@p)
    def world(name):
        if name not in worlds:
            b = bones[name]
            worlds[name] = (world(b['parent']) if 'parent' in b else np.eye(4)) @ transform(b)
        return worlds[name]
    points = {}
    for b in geo['bones']:
        for i, cube in enumerate(b.get('cubes', [])):
            m = world(b['name']) @ transform(cube)
            origin, size = np.array(cube['origin']), np.array(cube['size'])
            for k, xyz in enumerate(np.ndindex(2, 2, 2)):
                p = origin + np.array(xyz)*size
                points[b['name'], i, k] = (m @ np.r_[p, 1])[:3]
    return points


def weapon_calibration(source_dir):
    source_geo = load(source_dir.parents[1] / 'resource_pack_hc_core_main/models/entity/hero_scythe.geo.json')['minecraft:geometry'][0]
    target_geo = load(ASSETS/'geo/item/poem_of_the_end.geo.json')['minecraft:geometry'][0]
    source, target = geo_points(source_geo), geo_points(target_geo)
    keys = sorted(source.keys() & target.keys())
    swap = np.array([[1.,0,0],[0,0,1],[0,1,0]])
    reflect = np.diag([-1.,1.,1.])  # GeckoLib converts the Bedrock X coordinate.
    a = np.array([reflect @ target[k] / 16 for k in keys])
    b = np.array([swap @ (source[k]-[-6,34,0]) / 16 for k in keys])
    ac, bc = a.mean(axis=0), b.mean(axis=0)
    u, _, vt = np.linalg.svd((a-ac).T @ (b-bc))
    r = vt.T @ u.T
    assert np.linalg.det(r) > 0.99, 'Weapon geometry requires reflection, check axes'
    offset = bc-r@ac
    errors = np.linalg.norm(a@r.T+offset-b, axis=1)
    assert errors.max() < 0.0001, ('Weapon geometries differ', errors.max())
    # RenderItemBase correction * vanilla display * (-.5) item origin +
    # GeoObjectRenderer (.5,.51,.5). All in game model coordinates.
    display = load(ASSETS/'models/item/poem_of_the_end.json')['display']['thirdperson_righthand']
    assert set(display) == {'translation'}, 'Update calibration for changed item display'
    render = affine(p=[0,0,-.13]) @ rx(-90) @ affine(p=np.array(display['translation'])/16) @ affine(p=[0,.01,0])
    calibration = affine(r, offset)
    blade = np.array([p for k,p in zip(keys,a) if k[0] in ('daoren','yuan')])
    tool_blade = (np.c_[blade,np.ones(len(blade))] @ render.T)[:,:3]
    lo, hi = tool_blade.min(axis=0), tool_blade.max(axis=0)
    return calibration @ np.linalg.inv(render), {
        'matched_weapon_vertices': len(keys), 'max_weapon_fit_error_blocks': float(errors.max()),
        'model_to_socket': calibration.tolist(), 'item_render_transform': render.tolist(),
        'collider_center': ((lo+hi)/2).tolist(),
        'collider_half_extents': ((hi-lo)/2+0.12).tolist(),
    }


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rig = bpy.data.objects['Bones:Character']
    scene = bpy.data.scenes['Scene']
    bpy.context.window.scene = scene
    assert rig.animation_data.action.name == ACTION
    assert list(rig.animation_data.action.frame_range) == [1.,127.]
    assert scene.render.fps/scene.render.fps_base == 30
    arm_path = ASSETS/'animmodels/entity/hero_biped_nightfall.json'
    arm = load(arm_path)['armature']
    local, rest, parents = {}, {}, {}
    def walk(nodes, parent=None):
        for node in nodes:
            n = node['name']
            local[n] = np.array(node['transform']).reshape(4,4)
            parents[n] = parent
            rest[n] = (rest[parent] if parent else np.eye(4)) @ local[n]
            walk(node.get('children', []), n)
    walk(arm['hierarchy'])
    source_rest = {j: rotation(np.array(rig.data.bones[s].matrix_local)) for j,s in MAPPING.items()}
    neutral = {n:rotation(m) for n,m in rest.items()}
    # The EF forearm bind pose includes 6.31 degrees of bend. Remove that bias
    # while transferring the source's absolute limb directions.
    for side in ('R','L'):
        neutral['Hand_'+side] = neutral['Arm_'+side]
    socket_correction, weapon_report = weapon_calibration(Path(bpy.data.filepath).parent)
    names = [n for n in rest if n in MAPPING or n.startswith(('Shoulder_','Elbow_','Knee_')) or n in ('Tool_R','Tool_L')]
    sampled, source_samples = [], []
    for tick in range(round(DURATION*HZ)+1):
        frame = 1 + tick*30/HZ
        scene.frame_set(math.floor(frame), subframe=frame%1)
        deps = bpy.context.evaluated_depsgraph_get()
        evaluated = rig.evaluated_get(deps)
        source = {s:np.array(evaluated.pose.bones[s].matrix) for s in set(MAPPING.values()) | {'Weapon:Scythe'}}
        source_samples.append(source)
        world, frame_local = {}, {}
        for name in names:
            parent = parents[name]
            pw = world[parent] if parent else np.eye(4)
            m = pw @ local[name]
            if name in MAPPING:
                sr = rotation(source[MAPPING[name]])
                m[:3,:3] = C @ sr @ source_rest[name].T @ C.T @ neutral[name]
                if name == 'Root':
                    p = source['Bone.011'][:3,3].copy()
                    if tick == 0:
                        start = p.copy()
                    p[:2] -= start[:2]
                    m[:3,3] = rest['Root'][:3,3] + C @ p * SCALE
                    m[2,3] += ROOT_CLEARANCE
            elif name.startswith(('Elbow_','Knee_')):
                side = name[-1]
                upper = ('Arm_' if name.startswith('Elbow') else 'Thigh_') + side
                lower = ('Hand_' if name.startswith('Elbow') else 'Leg_') + side
                q1 = Matrix(world[upper][:3,:3].tolist()).to_quaternion()
                q2 = Matrix(world[lower][:3,:3].tolist()).to_quaternion()
                halfway = np.array(q1.slerp(q2, .5).to_matrix())
                m[:3,:3] = halfway @ neutral[upper].T @ neutral[name]
                m[:3,3] = world[lower][:3,3]
            elif name == 'Tool_R':
                # The animated tool origin includes the inverse game item transform;
                # the *rendered grip*, not the raw Tool_R origin, stays at the palm.
                grip = (pw @ np.r_[local[name][:3,3],1])[:3]
                socket = affine(C @ rotation(source['Weapon:Scythe']) @ _POLE_ROLL, grip)
                m = socket @ socket_correction
            world[name] = m
            frame_local[name] = np.linalg.inv(pw) @ m
        sampled.append(frame_local)
    times = np.arange(len(sampled))/HZ
    def clip(start, end):
        first, last = round(start*HZ), round(end*HZ)
        entries = []
        for name in names:
            matrices = np.array([x[name] for x in sampled[first:last+1]])
            if name == 'Root':
                # Preserve pelvis height; only horizontal travel is relative to each segment.
                matrices[:,0,3] -= matrices[0,0,3]-rest[name][0,3]
                matrices[:,1,3] -= matrices[0,1,3]-rest[name][1,3]
            entries.append({'name':name, 'time':np.round(times[first:last+1]-start,6).tolist(),
                            'transform':np.round(matrices.reshape(-1,16),7).tolist()})
        return {'animation':entries}
    write(DEST/'hero_scythe_combo_v4.json', clip(0,DURATION))
    for i,(start,end) in enumerate(SEGMENTS,1):
        write(DEST/f'hero_scythe_combo_v4_{i}.json',clip(start,end))
    # Export samples for independent loader/FK verification and preview generation.
    write(OUT/'reference_samples.json', {
        'source':[{n:m.tolist() for n,m in row.items()} for row in source_samples],
        'local':[{n:m.tolist() for n,m in row.items()} for row in sampled],
        'source_weapon_vertices': [[float(v) * SCALE for v in vertex.co] for vertex in bpy.data.objects['Poem of the End | 原版镰刀'].data.vertices],
        'source_weapon_faces': [list(p.vertices) for p in bpy.data.objects['Poem of the End | 原版镰刀'].data.polygons],
        'source_weapon_uv': [list(uv.uv) for uv in bpy.data.objects['Poem of the End | 原版镰刀'].data.uv_layers.active.data],
    })
    speed = []
    for i in range(1,len(sampled)-1):
        q1 = Matrix(source_samples[i-1]['Weapon:Scythe'][:3,:3].tolist()).to_quaternion()
        q2 = Matrix(source_samples[i+1]['Weapon:Scythe'][:3,:3].tolist()).to_quaternion()
        angle = 2*math.acos(min(1,abs(q1.dot(q2))))
        speed.append((i/HZ, math.degrees(angle)*HZ/2))
    peaks = sorted([row for i,row in enumerate(speed[1:-1],1) if row[1]>speed[i-1][1] and row[1]>speed[i+1][1]], key=lambda row:-row[1])
    report = {'action':ACTION, 'duration_seconds':DURATION, 'sample_hz':HZ, 'samples':len(sampled),
              'joint_count':len(names), 'mapping':MAPPING, 'axis_source_to_epicfight':C.tolist(),
              'source_sha256':hashlib.sha256(Path(bpy.data.filepath).read_bytes()).hexdigest(),
              'armature_sha256':hashlib.sha256(arm_path.read_bytes()).hexdigest(),
              'weapon':weapon_report, 'segments':SEGMENTS, 'hit_windows':HIT_WINDOWS,
              'root_clearance_blocks':ROOT_CLEARANCE, 'angular_speed_peaks_deg_s':peaks[:20],
              'root_displacement_blocks':(C@(source_samples[-1]['Bone.011'][:3,3]-source_samples[0]['Bone.011'][:3,3])*SCALE).tolist()}
    write(OUT/'conversion_report.json',report,True)
    print('V4_EXPORT',json.dumps(report,ensure_ascii=False))


if __name__ == '__main__':
    main()
