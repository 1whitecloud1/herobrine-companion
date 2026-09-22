"""Diagnose the V4 weapon convention: how is the rig's skinned scythe geometry
oriented relative to the 'Weapon:Scythe' bone, versus the convention the exporter
assumed (b = swap @ (geo_pt - [-6,34,0]) / 16, placed by the bone pose matrix)?

Read-only. Print PROBE_* JSON lines.
Run: blender --background --factory-startup --disable-autoexec <V4.blend> --python <this>
"""
import json
import sys
from pathlib import Path

import bpy
import numpy as np

REPO = Path(r'E:\java\herobrine_companion')
sys.path.insert(0, str(REPO / 'scripts' / 'epicfight'))
from export_v4 import ASSETS, load, geo_points  # noqa: E402

SWAP = np.array([[1., 0, 0], [0, 0, 1], [0, 1, 0]])
PIVOT = np.array([-6., 34., 0.])


def m16(m):
    return [round(float(v), 8) for v in np.array(m).reshape(-1)]


def r3(m):
    return np.array(m)[:3, :3]


def fit_similarity(p, q):
    """Return (s, R, t) minimising ||s R p + t - q||, p/q row clouds."""
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
    out = []
    for perm in ((0, 1, 2), (1, 2, 0), (2, 0, 1), (0, 2, 1), (2, 1, 0), (1, 0, 2)):
        for sx in (1, -1):
            for sy in (1, -1):
                for sz in (1, -1):
                    m = np.zeros((3, 3))
                    for i, j in enumerate(perm):
                        m[i, j] = (sx, sy, sz)[i]
                    if np.linalg.det(m) > 0.5:
                        out.append(m)
    uniq, seen = [], set()
    for m in out:
        k = tuple(np.round(m.reshape(-1), 6))
        if k not in seen:
            seen.add(k)
            uniq.append(m)
    return uniq


def main():
    blend_dir = Path(bpy.data.filepath).parent
    source_geo_path = blend_dir.parents[1] / 'resource_pack_hc_core_main/models/entity/hero_scythe.geo.json'
    print('PROBE_SOURCE_GEO', str(source_geo_path), source_geo_path.exists())
    if not source_geo_path.exists():
        print('PROBE_NO_GEO')
        return
    source_geo = load(source_geo_path)['minecraft:geometry'][0]
    geo = geo_points(source_geo)
    keys = sorted(geo.keys())
    b_pts = np.array([SWAP @ (geo[k] - PIVOT) / 16 for k in keys])
    print('PROBE_B_POINTS', json.dumps({'count': len(b_pts),
                                        'bbox': [b_pts.min(0).round(6).tolist(), b_pts.max(0).round(6).tolist()],
                                        'scale_note': 'blocks = bedrock_px/16'}))
    print('PROBE_GEO_CUBES', json.dumps(sorted({k[0] for k in keys})))

    rig = bpy.data.objects['Bones:Character']
    arm_world = np.array(rig.matrix_world)
    rest_weapon = np.array(rig.data.bones['Weapon:Scythe'].matrix_local)

    # find the skinned mesh that carries the scythe
    for name in ('Character', '2nd Layer'):
        ob = bpy.data.objects.get(name)
        if ob is None:
            continue
        groups = {g.name: g.index for g in ob.vertex_groups}
        print('PROBE_GROUPS', name, json.dumps(sorted(groups)))
        if 'Weapon:Scythe' not in groups:
            continue
        gi = groups['Weapon:Scythe']
        idx = [v.index for v in ob.data.vertices if any(g.group == gi and g.weight > 0.5 for g in v.groups)]
        if not idx:
            continue
        co = np.array([ob.data.vertices[i].co[:] for i in idx])
        obj_world = np.array(ob.matrix_world)
        arm_from_obj = np.linalg.inv(arm_world) @ obj_world
        verts_arm = (np.c_[co, np.ones(len(co))] @ arm_from_obj.T)[:, :3]
        print('PROBE_WEAPON_MESH', json.dumps({
            'object': name, 'vert_count': len(idx),
            'obj_space_bbox': [co.min(0).round(6).tolist(), co.max(0).round(6).tolist()],
            'armature_space_bbox': [verts_arm.min(0).round(6).tolist(), verts_arm.max(0).round(6).tolist()],
            'arm_from_obj_scale': round(float(np.cbrt(abs(np.linalg.det(arm_from_obj[:3, :3])))), 8),
        }))
        # ICP: b-space geo corners -> armature-space weapon vertices
        target = verts_arm[np.unique(np.round(verts_arm, 4), axis=0, return_index=True)[1]]
        best = None
        bc, tc = b_pts.mean(0), target.mean(0)
        span_b = np.linalg.norm(b_pts - bc, axis=1).mean()
        span_t = np.linalg.norm(target - tc, axis=1).mean()
        for rot0 in axis_rotations():
            s0 = span_t / span_b
            p = s0 * (b_pts - bc) @ rot0.T + tc
            scale, rot, t = s0, rot0, tc - s0 * rot0 @ bc
            for _ in range(12):
                ix, _d = nearest(p, target)
                scale, rot, t = fit_similarity(b_pts, target[ix])
                p = scale * b_pts @ rot.T + t
            ix, dist = nearest(p, target)
            score = float(np.percentile(dist, 95))
            if best is None or score < best[0]:
                best = (score, scale, rot, t, float(dist.mean()), float(dist.max()))
        score, scale, rot, t, mean_d, max_d = best
        print('PROBE_FIT', json.dumps({
            'object': name, 'p95_nn_dist_blocks': round(score, 6),
            'mean_nn_dist': round(mean_d, 6), 'max_nn_dist': round(max_d, 6),
            'scale_b_to_armature': round(scale, 8),
            'R_b_to_armature': np.round(rot, 6).tolist(),
        }))
        delta = np.linalg.inv(rest_weapon) @ np.block([[scale * rot, t[:, None]], [np.zeros(3), 1.]])
        dd = r3(delta)
        ang = float(np.degrees(np.arccos(np.clip((np.trace(dd) - 1) / 2, -1, 1))))
        w, v = np.linalg.eig(dd)
        ax = np.real(v[:, np.argmin(np.abs(w - 1))])
        ax = ax / np.linalg.norm(ax)
        print('PROBE_DELTA', json.dumps({
            'note': 'rest_weapon^-1 @ M ; exporter assumed identity rotation here',
            'rotation_angle_deg': round(ang, 4),
            'rotation_axis': np.round(ax, 6).tolist(),
            'rotation_matrix': np.round(dd, 6).tolist(),
            'translation': np.round(delta[:3, 3], 6).tolist(),
            'fits_identity': bool(ang < 5),
        }))
        # verify: place the poem-model blade via calibration under both conventions
        break

    poem = bpy.data.objects.get('Poem of the End | 原版镰刀')
    if poem is not None:
        co = np.array([v.co[:] for v in poem.data.vertices])
        print('PROBE_POEM_OBJECT', json.dumps({
            'matrix_world': m16(poem.matrix_world),
            'parent': poem.parent.name if poem.parent else None,
            'bbox': [co.min(0).round(6).tolist(), co.max(0).round(6).tolist()]}))
    print('PROBE_DONE')


if __name__ == '__main__':
    main()
