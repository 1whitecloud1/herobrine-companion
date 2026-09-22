"""Remove IK elbow cusps with at most 6% uniform limb proportion adaptation.

Both palms, the body turn, weapon rotation and root travel remain exact. Small
uniform bone scales avoid the shears that nonuniform correction would introduce
when Epic Fight decomposes local matrices into translation/rotation/scale.
"""
import hashlib
import shutil
import sys

import numpy as np
from scipy.ndimage import gaussian_filter1d, maximum_filter1d
from scipy.spatial.transform import Rotation

from unity_scythe_common import OUT, WORK, GameMesh, basis, load, rotations, write


def stabilize(name):
    g = GameMesh(); path = WORK / 'retargeted' / (name + '.npz')
    backup = WORK / 'before_limb_stabilization' / path.name
    backup.parent.mkdir(exist_ok=True)
    if not backup.exists(): shutil.copyfile(path, backup)
    data = np.load(backup); world = data['world'].copy(); names = list(data['names'])
    ns = {n: i for i, n in enumerate(names)}; times = data['times']; hz = 1 / np.diff(times).mean()
    root = world[:, ns['Root']]; root_r = root[:, :3, :3]; root_p = root[:, :3, 3]
    scales = []
    for s in ('R', 'L'):
        upper = ns['Arm_' + s]; lower = ns['Hand_' + s]
        a = world[:, upper, :3, 3].copy(); b = world[:, lower, :3, 3].copy()
        local_end = g.local['Tool_' + s][:3, 3]
        end = np.einsum('nij,j->ni', world[:, lower, :3, :3], local_end) + b
        local_b = np.einsum('nji,nj->ni', root_r, b - root_p)
        smooth_b = np.einsum('nij,nj->ni', root_r, gaussian_filter1d(local_b, hz * .025, axis=0, mode='nearest')) + root_p
        l1 = np.linalg.norm(g.local['Hand_' + s][:3, 3]); l2 = np.linalg.norm(local_end)
        blend = np.ones(len(times))
        for _ in range(12):
            candidate = b + (smooth_b - b) * blend[:, None]
            length1 = np.linalg.norm(candidate - a, axis=1) / l1
            length2 = np.linalg.norm(end - candidate, axis=1) / l2
            bad = (np.abs(length1 - 1) > .06) | (np.abs(length2 - 1) > .06)
            blend[bad] *= .72
        b = b + (smooth_b - b) * blend[:, None]
        for bone, p0, p1, local_direction, length in [
                (upper, a, b, g.local['Hand_' + s][:3, 3], l1),
                (lower, b, end, local_end, l2)]:
            relative = np.swapaxes(root_r, -1, -2) @ rotations(world[:, bone])
            q = Rotation.from_matrix(relative).as_quat()
            for i in range(1, len(q)):
                if np.dot(q[i - 1], q[i]) < 0: q[i] *= -1
            q = gaussian_filter1d(q, hz * .018, axis=0, mode='nearest')
            q /= np.linalg.norm(q, axis=1)[:, None]
            filtered = root_r @ Rotation.from_quat(q).as_matrix()
            scale = np.linalg.norm(p1 - p0, axis=1) / length
            scales.extend(scale.tolist())
            for i in range(len(times)):
                world[i, bone, :3, :3] = basis(p1[i] - p0[i], filtered[i, :, 0]) @ basis(local_direction, [1, 0, 0]).T * scale[i]
            world[:, bone, :3, 3] = p0
    g.helper_angles = {}
    metrics_path = WORK / 'retargeted' / (name + '_metrics.json')
    metrics = load(metrics_path)
    for i in range(len(times)):
        w = dict(zip(names, world[i]))
        w['Tool_L'] = w['Hand_L'] @ g.local['Tool_L']
        g.helpers(w)
        world[i] = np.array([w[n] for n in names])
        metrics[i]['body_min'] = float(g.skin(w)[:, 2].min())
    needed = np.array([max(0., .012 - m['body_min']) for m in metrics])
    spine_lift = np.maximum(needed, gaussian_filter1d(maximum_filter1d(needed, size=17), hz * .02))
    upper_body = [j for j, n in enumerate(names) if n != 'Root' and not n.startswith(('Thigh_', 'Leg_', 'Knee_'))]
    for i in range(len(times)):
        world[i, upper_body, 2, 3] += spine_lift[i]
        metrics[i]['body_min'] = float(g.skin(dict(zip(names, world[i])))[:, 2].min())
        metrics[i]['blade_min'] += float(spine_lift[i])
    local = np.empty_like(world)
    for j, n in enumerate(names):
        parent = g.parents[n]
        local[:, j] = np.linalg.inv(world[:, ns[parent]]) @ world[:, j] if parent else world[:, j]
    entries = [dict(name=n, time=np.round(times, 7).tolist(), transform=np.round(local[:, j].reshape(-1, 16), 8).tolist()) for j, n in enumerate(names)]
    output = OUT / 'epicfight/assets/herobrine_companion/animmodels/animations/player/poem_unity09' / (name.lower() + '.json')
    write(output, {'animation': entries})
    np.savez_compressed(path, names=data['names'], times=times, world=world, local=local, grips=data['grips'], contacts=data['contacts'])
    write(metrics_path, metrics)
    report_path = OUT / 'reports' / (name + '.json'); report = load(report_path)
    report.update(sha256=hashlib.sha256(output.read_bytes()).hexdigest(), maximum_limb_proportion_adaptation=float(np.max(np.abs(np.array(scales) - 1))),
                  maximum_spine_floor_adaptation=float(spine_lift.max()),
                  minimum_body_height=min(m['body_min'] for m in metrics))
    write(report_path, report, True)
    print('STABILIZED', name, 'max_limb_adaptation', round(report['maximum_limb_proportion_adaptation'], 4),
          'floor', round(report['minimum_body_height'], 4), flush=True)


if __name__ == '__main__':
    for name in sys.argv[1:]: stabilize(name)
