"""Measure the V4 export's weapon socket against the scene's animated scythe object.

The blend contains an object-level-animated 'Poem of the End | 原版镰刀' that the
author animated as the visible weapon. export_v4.py placed the weapon as
    socket_base(t) = [ C @ P_weapon_rot(t) | palm(t) ]
applied to the b-space geometry (b = swap @ (geo_pt - [-6,34,0]) / 16).
This probe compares that assumption against the object's real animated placement.

Read-only. Run:
blender --background --factory-startup --disable-autoexec <V4.blend> --python <this>
"""
import json
import math
import sys
from pathlib import Path

import bpy
import numpy as np

REPO = Path(r'E:\java\herobrine_companion')
sys.path.insert(0, str(REPO / 'scripts' / 'epicfight'))
from export_v4 import load, geo_points  # noqa: E402

SWAP = np.array([[1., 0, 0], [0, 0, 1], [0, 1, 0]])
PIVOT = np.array([-6., 34., 0.])
C = np.diag([-1., -1., 1.])
HZ = 120


def fit_similarity(p, q):
    pc, qc = p.mean(0), q.mean(0)
    x, y = p - pc, q - qc
    u, s, vt = np.linalg.svd(x.T @ y)
    d = np.sign(np.linalg.det(vt.T @ u.T))
    diag = np.array([1., 1., d])
    rot = vt.T @ np.diag(diag) @ u.T
    scale = float((s * diag).sum() / (x ** 2).sum())
    return scale, rot, qc - scale * rot @ pc


def nearest(a, b):
    d = ((a[:, None, :] - b[None, :, :]) ** 2).sum(-1)
    ix = d.argmin(1)
    return ix, np.sqrt(d[np.arange(len(a)), ix])


def axis_rotations():
    out, seen = [], set()
    for perm in ((0, 1, 2), (1, 2, 0), (2, 0, 1), (0, 2, 1), (2, 1, 0), (1, 0, 2)):
        for sx in (1, -1):
            for sy in (1, -1):
                for sz in (1, -1):
                    m = np.zeros((3, 3))
                    for i, j in enumerate(perm):
                        m[i, j] = (sx, sy, sz)[i]
                    if np.linalg.det(m) > 0.5:
                        k = tuple(np.round(m.reshape(-1), 6))
                        if k not in seen:
                            seen.add(k)
                            out.append(m)
    return out


def icp(src, dst):
    bc, tc = src.mean(0), dst.mean(0)
    span = np.linalg.norm(dst - tc, axis=1).mean() / np.linalg.norm(src - bc, axis=1).mean()
    best = None
    for rot0 in axis_rotations():
        scale, rot, t = span, rot0, tc - span * rot0 @ bc
        p = scale * src @ rot.T + t
        for _ in range(20):
            ix, _ = nearest(p, dst)
            scale, rot, t = fit_similarity(src, dst[ix])
            p = scale * src @ rot.T + t
        ix, dist = nearest(p, dst)
        score = float(np.percentile(dist, 90))
        if best is None or score < best[0]:
            best = (score, scale, rot, t, float(dist.mean()), float(dist.max()))
    return best


def angle_axis(rot):
    ang = float(np.degrees(np.arccos(np.clip((np.trace(rot) - 1) / 2, -1, 1))))
    if ang < 1e-6:
        return 0.0, [0.0, 0.0, 0.0]
    w, v = np.linalg.eig(rot)
    ax = np.real(v[:, np.argmin(np.abs(w - 1))])
    return round(ang, 3), np.round(ax / np.linalg.norm(ax), 5).tolist()


def main():
    blend_dir = Path(bpy.data.filepath).parent
    geo = geo_points(load(blend_dir.parents[1] / 'resource_pack_hc_core_main/models/entity/hero_scythe.geo.json')['minecraft:geometry'][0])
    b_pts = np.array([SWAP @ (geo[k] - PIVOT) / 16 for k in sorted(geo)])
    ob = bpy.data.objects['Poem of the End | 原版镰刀']
    v_local = np.array([v.co[:] for v in ob.data.vertices])
    print('PROBE_OBJ_ANIM', json.dumps({
        'has_animation_data': ob.animation_data is not None,
        'action': ob.animation_data.action.name if ob.animation_data and ob.animation_data.action else None,
        'drivers': [d.data_path for d in ob.animation_data.drivers] if ob.animation_data else [],
        'constraints': [c.type for c in ob.constraints],
        'parent': ob.parent.name if ob.parent else None,
        'n_verts': len(v_local),
    }))
    uniq = v_local[np.unique(np.round(v_local, 4), axis=0, return_index=True)[1]]
    score, scale, rotY, tY, mean_d, max_d = icp(b_pts, uniq)
    angY, axY = angle_axis(rotY)
    print('PROBE_Y_FIT', json.dumps({
        'note': 'Y: b-space geo -> poem object local space',
        'p90_nn_dist': round(score, 6), 'mean': round(mean_d, 6), 'max': round(max_d, 6),
        'scale': round(scale, 7), 'rotation_angle_deg': angY, 'rotation_axis': axY,
        'rotation': np.round(rotY, 6).tolist(), 'translation': np.round(tY, 6).tolist()}))

    rig = bpy.data.objects['Bones:Character']
    scene = bpy.context.scene
    print('PROBE_FPS', scene.render.fps / scene.render.fps_base,
          'frame_range', list(rig.animation_data.action.frame_range))
    ticks = [0, 6, 12, 18, 24, 36, 42, 48, 54, 60, 66, 72, 78, 84, 90, 96, 108, 120, 132, 144]
    rows = []
    for tick in ticks:
        frame = 1 + tick * 30 / HZ
        scene.frame_set(math.floor(frame), subframe=frame % 1)
        deps = bpy.context.evaluated_depsgraph_get()
        pose = np.array(rig.evaluated_get(deps).pose.bones['Weapon:Scythe'].matrix)
        m_true = np.array(ob.evaluated_get(deps).matrix_world)
        r_true = (m_true @ np.block([[rotY * scale, tY[:, None]], [np.zeros(3), 1.]]))[:3, :3]
        r_socket = (C @ pose[:3, :3])
        d_rot = r_true @ np.linalg.inv(r_socket)
        ang, ax = angle_axis(d_rot)
        origin_true = (m_true @ np.r_[tY, 1.])[:3]
        palm = pose[:3, 3]
        rows.append({'tick': tick, 'delta_angle_deg': ang, 'delta_axis': ax,
                     'delta_matrix': np.round(d_rot, 6).tolist(),
                     'geometry_origin_world': np.round(origin_true, 5).tolist(),
                     'weapon_bone_head_world': np.round(palm, 5).tolist(),
                     'origin_minus_palm': np.round(origin_true - palm, 5).tolist()})
    print('PROBE_ROWS', json.dumps(rows))
    angs = [r['delta_angle_deg'] for r in rows]
    print('PROBE_SUMMARY', json.dumps({
        'delta_angle_min': min(angs), 'delta_angle_max': max(angs),
        'delta_angle_mean': round(float(np.mean(angs)), 3),
        'delta_axis_first': rows[0]['delta_axis'],
        'origin_offset_max': round(float(max(np.linalg.norm(r['origin_minus_palm']) for r in rows)), 5),
        'constant_rotation': bool(max(angs) - min(angs) < 2.0)}))
    print('PROBE_DONE')


if __name__ == '__main__':
    main()
