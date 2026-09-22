"""Compare the V4 (blend/bone) weapon geometry convention with the V6 (mocap) one.

V4 convention: b = SWAP @ (hero_scythe_geo_pt - [-6,34,0]) / 16  [verified identical
to the blend's 'Fixed grip' scythe object, up to uniform scale 1.6].
V6 convention: reference_samples['source_weapon_vertices'] and weapon_mesh.json.

If the two conventions disagree by a rotation, that is exactly the flip the user sees
(V6 renders correctly in game, V4 does not).

Run: blender --background --factory-startup --python <this>
"""
import json
import sys
from pathlib import Path

import numpy as np

REPO = Path(r'E:\java\herobrine_companion')
sys.path.insert(0, str(REPO / 'scripts' / 'epicfight'))
from export_v4 import load, geo_points  # noqa: E402

SWAP = np.array([[1., 0, 0], [0, 0, 1], [0, 1, 0]])
PIVOT = np.array([-6., 34., 0.])
SRC_GEO = Path(r'E:\MCStudioDownload\work\m13525918851@163.com\Cpp\AddOn\3055269d404f439eba5592a9d4b52113\resource_pack_hc_core_main\models\entity\hero_scythe.geo.json')


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


def voxel(points, cells=12):
    """Downsample by keeping one point per voxel cell of the bounding box."""
    lo, hi = points.min(0), points.max(0)
    span = np.where(hi - lo < 1e-9, 1.0, hi - lo)
    key = np.floor((points - lo) / span * cells).astype(np.int64)
    _, ix = np.unique(key, axis=0, return_index=True)
    return points[ix]


def icp(src_full, dst_full):
    src, dst = voxel(src_full), voxel(dst_full)
    bc, tc = src.mean(0), dst.mean(0)
    span = np.linalg.norm(dst - tc, axis=1).mean() / np.linalg.norm(src - bc, axis=1).mean()
    best = None
    for rot0 in axis_rotations():
        scale, rot, t = span, rot0, tc - span * rot0 @ bc
        p = scale * src @ rot.T + t
        for _ in range(25):
            ix, _ = nearest(p, dst)
            scale, rot, t = fit_similarity(src, dst[ix])
            p = scale * src @ rot.T + t
        ix, dist = nearest(p, dst)
        score = float(np.percentile(dist, 90))
        if best is None or score < best[0]:
            best = (score, scale, rot, t, float(dist.mean()), float(dist.max()))
    # refine on the full clouds with the winning init
    score, scale, rot, t, mean_d, max_d = best
    p = scale * src_full @ rot.T + t
    for _ in range(6):
        ix, dist = nearest(p, dst_full)
        scale, rot, t = fit_similarity(src_full, dst_full[ix])
        p = scale * src_full @ rot.T + t
    ix, dist = nearest(p, dst_full)
    return float(np.percentile(dist, 90)), scale, rot, t, float(dist.mean()), float(dist.max())


def angle_axis(rot):
    ang = float(np.degrees(np.arccos(np.clip((np.trace(rot) - 1) / 2, -1, 1))))
    if ang < 1e-6:
        return 0.0, None
    w, v = np.linalg.eig(rot)
    ax = np.real(v[:, np.argmin(np.abs(w - 1))])
    return round(ang, 4), np.round(ax / np.linalg.norm(ax), 5).tolist()


def bbox(p):
    return [np.round(p.min(0), 5).tolist(), np.round(p.max(0), 5).tolist()]


def main():
    geo = geo_points(load(SRC_GEO)['minecraft:geometry'][0])
    keys = sorted(geo.keys())
    b_pts = np.array([SWAP @ (geo[k] - PIVOT) / 16 for k in keys])
    blade_ix = [i for i, k in enumerate(keys) if k[0] in ('daoren', 'yuan')]
    blade_pts = b_pts[blade_ix]
    print('PROBE_V4_SPACE', json.dumps({'bbox': bbox(b_pts), 'n': len(b_pts),
                                        'blade_bbox': bbox(blade_pts),
                                        'blade_centroid': np.round(blade_pts.mean(0), 5).tolist(),
                                        'all_centroid': np.round(b_pts.mean(0), 5).tolist()}))
    print('PROBE_V4_CUBES', json.dumps({c: np.round(b_pts[[i for i, k in enumerate(keys) if k[0] == c]].mean(0), 5).tolist()
                                        for c in sorted({k[0] for k in keys})}))

    v6 = REPO / 'build/epicfight-v6'
    for label, path, key in (('v6_reference_source_weapon', v6 / 'reference_samples.json', 'source_weapon_vertices'),
                             ('v6_weapon_mesh', v6 / 'weapon_mesh.json', 'vertices')):
        if not path.exists():
            print('PROBE_MISSING', str(path))
            continue
        data = load(path)
        if key not in data:
            print('PROBE_KEYS', label, json.dumps(sorted(data)))
            continue
        pts = np.array(data[key], dtype=float).reshape(-1, 3)
        uniq = pts[np.unique(np.round(pts, 4), axis=0, return_index=True)[1]]
        print('PROBE_V6_SPACE', json.dumps({'label': label, 'n': len(pts), 'bbox': bbox(pts),
                                            'centroid': np.round(pts.mean(0), 5).tolist()}))
        score, scale, rot, t, mean_d, max_d = icp(b_pts, uniq)
        ang, ax = angle_axis(rot)
        print('PROBE_V6_FIT', json.dumps({
            'label': label, 'p90_nn_dist': round(score, 6), 'mean': round(mean_d, 6), 'max': round(max_d, 6),
            'scale': round(scale, 7), 'rotation_angle_deg': ang, 'rotation_axis': ax,
            'rotation': np.round(rot, 5).tolist(), 'translation': np.round(t, 5).tolist(),
            'conventions_match': bool(ang < 5)}))
        placed = scale * b_pts @ rot.T + t
        print('PROBE_V6_BLADE_THERE', json.dumps({
            'label': label,
            'mapped_blade_centroid': np.round(placed[blade_ix].mean(0), 5).tolist(),
            'mapped_all_centroid': np.round(placed.mean(0), 5).tolist()}))
    print('PROBE_DONE')


if __name__ == '__main__':
    main()
