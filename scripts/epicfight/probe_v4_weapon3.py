"""Locate the rig's scythe geometry and measure its orientation in rest space
against the convention export_v4.py assumed for the weapon socket.

Read-only. Run:
blender --background --factory-startup --disable-autoexec <V4.blend> --python <this>
"""
import json
import sys
from pathlib import Path

import bpy
import numpy as np

REPO = Path(r'E:\java\herobrine_companion')
sys.path.insert(0, str(REPO / 'scripts' / 'epicfight'))
from export_v4 import load, geo_points  # noqa: E402

SWAP = np.array([[1., 0, 0], [0, 0, 1], [0, 1, 0]])
PIVOT = np.array([-6., 34., 0.])


def bbox(p):
    return [p.min(0).round(5).tolist(), p.max(0).round(5).tolist()]


def spans(p):
    lo, hi = p.min(0), p.max(0)
    return np.round(hi - lo, 5).tolist()


def fit_similarity(p, q):
    pc, qc = p.mean(0), q.mean(0)
    x, y = p - pc, q - qc
    u, s, vt = np.linalg.svd(x.T @ y)
    d = np.sign(np.linalg.det(vt.T @ u.T))
    diag = np.array([1., 1., d])
    rot = vt.T @ np.diag(diag) @ u.T
    return float((s * diag).sum() / (x ** 2).sum()), rot, qc - float((s * diag).sum() / (x ** 2).sum()) * rot @ pc


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
    span_b, span_t = np.linalg.norm(src - bc, axis=1).mean(), np.linalg.norm(dst - tc, axis=1).mean()
    best = None
    for rot0 in axis_rotations():
        s0 = span_t / span_b
        scale, rot, t = s0, rot0, tc - s0 * rot0 @ bc
        p = scale * src @ rot.T + t
        for _ in range(15):
            ix, _d = nearest(p, dst)
            scale, rot, t = fit_similarity(src, dst[ix])
            p = scale * src @ rot.T + t
        ix, dist = nearest(p, dst)
        score = float(np.percentile(dist, 90))
        if best is None or score < best[0]:
            best = (score, scale, rot, t, float(dist.mean()), float(dist.max()))
    return best


def describe(rot):
    ang = float(np.degrees(np.arccos(np.clip((np.trace(rot) - 1) / 2, -1, 1))))
    w, v = np.linalg.eig(rot)
    ax = np.real(v[:, np.argmin(np.abs(w - 1))])
    return round(ang, 3), np.round(ax / np.linalg.norm(ax), 5).tolist()


def main():
    rig = bpy.data.objects['Bones:Character']
    arm_world = np.array(rig.matrix_world)
    rest_weapon = np.array(rig.data.bones['Weapon:Scythe'].matrix_local)
    hand = np.array(rig.data.bones['Arm:Right:Lower'].matrix_local)
    print('PROBE_HAND_BONE', json.dumps({'matrix_local': np.round(hand, 6).tolist(),
                                         'head': np.round(hand[:3, 3], 6).tolist()}))

    blend_dir = Path(bpy.data.filepath).parent
    geo = geo_points(load(blend_dir.parents[1] / 'resource_pack_hc_core_main/models/entity/hero_scythe.geo.json')['minecraft:geometry'][0])
    keys = sorted(geo.keys())
    b_pts = np.array([SWAP @ (geo[k] - PIVOT) / 16 for k in keys])
    print('PROBE_B', json.dumps({'count': len(b_pts), 'bbox': bbox(b_pts), 'spans': spans(b_pts)}))

    for name in ('Character', '2nd Layer'):
        ob = bpy.data.objects[name]
        obj_world = np.array(ob.matrix_world)
        arm_from_obj = np.linalg.inv(arm_world) @ obj_world
        co = np.array([v.co[:] for v in ob.data.vertices])
        arm_pts = (np.c_[co, np.ones(len(co))] @ arm_from_obj.T)[:, :3]
        print('PROBE_MESH', json.dumps({'object': name, 'verts': len(co),
                                        'arm_bbox': bbox(arm_pts), 'arm_spans': spans(arm_pts)}))
        for g in ob.vertex_groups:
            idx = [v.index for v in ob.data.vertices if any(gg.group == g.index and gg.weight > 0.5 for gg in v.groups)]
            if not idx:
                continue
            sub = arm_pts[idx]
            print('PROBE_GROUP', json.dumps({'object': name, 'group': g.name, 'count': len(idx),
                                             'bbox': bbox(sub), 'spans': spans(sub)}))
            if max(spans(sub)) > 1.6:
                res = icp(b_pts, sub[np.unique(np.round(sub, 4), axis=0, return_index=True)[1]])
                score, scale, rot, t, mean_d, max_d = res
                m = np.block([[scale * rot, t[:, None]], [np.zeros(3), 1.]])
                delta = np.linalg.inv(rest_weapon) @ m
                ang, ax = describe(delta[:3, :3])
                print('PROBE_FIT', json.dumps({
                    'object': name, 'group': g.name, 'p90_nn_dist': round(score, 6),
                    'mean': round(mean_d, 6), 'max': round(max_d, 6), 'scale': round(scale, 6),
                    'R_bsrc_to_group': np.round(rot, 5).tolist(),
                    'delta_vs_exporter_assumption': {'angle_deg': ang, 'axis': ax,
                                                     'translation': np.round(delta[:3, 3], 5).tolist(),
                                                     'is_identity': ang < 5}}))
    print('PROBE_DONE')


if __name__ == '__main__':
    main()
