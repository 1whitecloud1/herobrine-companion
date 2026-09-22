"""Regularize retarget fitting in time, keeping native body and blade rotations.

The block head and short arms give the per-frame fitting problem disconnected
solutions. Smooth the fitted handle translation and hand slide, then solve the
connected rigid limbs again. Never smooth away the native weapon/body turns.
"""
import hashlib
import shutil
import sys

import numpy as np
from scipy.ndimage import gaussian_filter1d, maximum_filter1d

from export_unity_scythe import HZ, WEAPON_SCALE, Retarget
from unity_scythe_common import C, OUT, WORK, GameMesh, NativeReference, affine, load, rotations, unit, write


def clearance(retarget, world, wr, wp):
    points = retarget.weapon_samples @ wr.T + wp
    values = []
    for n, c, half in [('Head', [0, .25, 0], [.25, .25, .25]),
                       ('Chest', [0, .2, 0], [.25, .20, .125]),
                       ('Torso', [0, .125, 0], [.25, .175, .125])]:
        center = world[n][:3, 3] + world[n][:3, :3] @ c
        local = (points - center) @ world[n][:3, :3]
        q = np.abs(local) - half
        sdf = np.linalg.norm(np.maximum(q, 0), axis=1) + np.minimum(q.max(axis=1), 0)
        values.append(sdf.min())
    return float(min(values))


def refine(name, sigma=.035):
    g = GameMesh(); native = NativeReference(); retarget = Retarget(g, native)
    source_path = WORK / 'retargeted' / (name + '.npz')
    backup = WORK / 'unrefined' / (name + '.npz')
    backup.parent.mkdir(exist_ok=True)
    metric_path = WORK / 'retargeted' / (name + '_metrics.json')
    metric_backup = WORK / 'unrefined' / (name + '_metrics.json')
    if not backup.exists():
        shutil.copyfile(source_path, backup)
        shutil.copyfile(metric_path, metric_backup)
    data = np.load(backup)
    names = list(data['names']); times = data['times']; world = data['world'].copy()
    assert abs(np.diff(times).mean() - 1 / HZ) < 1e-7
    count = len(times)
    source = np.load(WORK / 'native_samples' / (name + '.npz'))
    snames = list(source['names']); stimes = source['times']; sw = source['world']
    # Position interpolation is exact at the native 120 Hz samples. Feet only
    # use foot rotations; orthonormalize their midpoint interpolation.
    sp = np.empty((count, len(snames), 3)); sr = np.empty((count, len(snames), 3, 3))
    sp[::2] = sw[:, :, :3, 3]; sp[1::2] = (sw[:-1, :, :3, 3] + sw[1:, :, :3, 3]) * .5
    sr[::2] = rotations(sw); sr[1::2] = rotations((sr[::2][:-1] + sr[::2][1:]) * .5)
    si = {n: j for j, n in enumerate(snames)}
    ni = {n: j for j, n in enumerate(names)}
    root = world[:, ni['Root']]
    # A low human lunge needs enough hip clearance for solid block thighs.
    lift = np.maximum(.48 - root[:, 2, 3], 0)
    lift = np.maximum(lift, gaussian_filter1d(maximum_filter1d(lift, size=21), 8))
    world[:, :, 2, 3] += lift[:, None]
    rr = root[:, :3, :3]
    weapon = world[:, ni['Tool_R']] @ g.correction_inverse
    local_wp = np.einsum('nji,nj->ni', rr, weapon[:, :3, 3] - root[:, :3, 3])
    local_wp = gaussian_filter1d(local_wp, sigma * HZ, axis=0, mode='nearest')
    wp_all = np.einsum('nij,nj->ni', rr, local_wp) + root[:, :3, 3]
    grips = gaussian_filter1d(data['grips'], sigma * HZ, axis=0, mode='nearest')
    contacts = gaussian_filter1d(data['contacts'], .025 * HZ, axis=0, mode='nearest')
    contacts[contacts > .995] = 1.
    contacts[contacts < .005] = 0.
    free = {}
    hints = {}
    for s in ('R', 'L'):
        suffix = s.lower()
        palm = (sp[:, si['middle_02_' + suffix]] + sp[:, si['thumb_03_' + suffix]]) * .5
        source_shoulder = sp[:, si['upperarm_' + suffix]]
        delta = (palm - source_shoulder) @ C.T * retarget.arm_scale
        free[s] = np.einsum('nij,nj->ni', rr,
                    gaussian_filter1d(np.einsum('nji,nj->ni', rr, delta), sigma * HZ, axis=0, mode='nearest'))
        hints[s] = (sp[:, si['lowerarm_' + suffix]] - source_shoulder) @ C.T * retarget.arm_scale
    origin = sp[0, si['pelvis']].copy(); origin[2] = 0
    previous_axis = {}
    retarget.previous = {}; g.helper_angles = {}
    metrics = load(metric_backup)
    max_adjustment = 0.
    for i in range(count):
        w = dict(zip(names, world[i]))
        wr = weapon[i, :3, :3]; wp = wp_all[i].copy()
        desired = {}
        for j, s in enumerate(('R', 'L')):
            shoulder = w['Arm_' + s][:3, 3]
            delta = free[s][i]
            radius = np.linalg.norm(g.local['Hand_' + s][:3, 3]) + np.linalg.norm(g.local['Tool_' + s][:3, 3]) - .027
            desired[s] = shoulder + unit(delta) * np.clip(np.linalg.norm(delta), .22, radius)
            desired[s][2] = max(.20, desired[s][2])
        # Project one common rigid translation into the two reachable balls.
        # This convex step cannot switch to a different collision-avoidance
        # branch; all held palms continue to meet the original handle axis.
        for _ in range(100):
            previous_wp = wp.copy()
            for j, s in enumerate(('R', 'L')):
                weight = contacts[i, j]
                if weight < .001:
                    continue
                radius = np.linalg.norm(g.local['Hand_' + s][:3, 3]) + np.linalg.norm(g.local['Tool_' + s][:3, 3]) - .027
                center = w['Arm_' + s][:3, 3] - wr @ grips[i, j]
                delta = wp - center
                limit = radius + (1 - weight) * 2 / max(weight, .001)
                distance = np.linalg.norm(delta)
                if distance > limit:
                    wp = center + unit(delta) * limit
            minimum = float((g.weapon_vertices @ wr.T + wp)[:, 2].min())
            wp[2] += max(0., .012 - minimum)
            if np.linalg.norm(wp - previous_wp) < 1e-9:
                break
        grip_error = {}
        for j, s in enumerate(('R', 'L')):
            attached = wp + wr @ grips[i, j]
            goal = desired[s] * (1 - contacts[i, j]) + attached * contacts[i, j]
            end = retarget.limb(w, s, goal, w['Arm_' + s][:3, 3] + hints[s][i])
            previous_axis[s] = unit(end - w['Arm_' + s][:3, 3])
            grip_error[s] = float(np.linalg.norm(end - attached)) if contacts[i, j] > .999 else 0.
        w['Tool_R'] = affine(wr, wp) @ g.correction
        w['Tool_L'] = w['Hand_L'] @ g.local['Tool_L']
        p = dict(zip(snames, sp[i])); r = dict(zip(snames, sr[i]))
        feet = retarget.legs(w, p, r, origin)
        g.helpers(w)
        body_min = float(g.skin(w)[:, 2].min())
        # Keep the pose's own small clearance correction; do not move its feet
        # sideways or change the native full-body spin.
        metrics[i].update(feet=feet, grip_error=grip_error, body_min=body_min,
                          contacts=dict(zip(('R', 'L'), map(float, contacts[i]))),
                          blade_min=float((g.weapon_vertices @ wr.T + wp)[:, 2].min()),
                          weapon_body_clearance=clearance(retarget, w, wr, wp),
                          temporal_fit_adjustment=float(np.linalg.norm(wp - weapon[i, :3, 3])),
                          root_adaptation=metrics[i]['root_adaptation'] + float(lift[i]))
        world[i] = np.array([w[n] for n in names])
    # Resolve any floor penetration created by smoothing the elbows without
    # introducing a one-frame body lift.
    required = np.array([max(0., .012 - m['body_min']) for m in metrics])
    extra = np.maximum(required, gaussian_filter1d(maximum_filter1d(required, size=21), 8))
    retarget.previous = {}; g.helper_angles = {}
    for i in range(count):
        world[i, :, 2, 3] += extra[i]
        w = dict(zip(names, world[i]))
        metrics[i]['feet'] = retarget.legs(w, dict(zip(snames, sp[i])), dict(zip(snames, sr[i])), origin)
        g.helpers(w)
        metrics[i]['body_min'] = float(g.skin(w)[:, 2].min())
        metrics[i]['blade_min'] += float(extra[i])
        metrics[i]['root_adaptation'] += float(extra[i])
        world[i] = np.array([w[n] for n in names])
    local = np.empty_like(world)
    for j, n in enumerate(names):
        parent = g.parents[n]
        local[:, j] = np.linalg.inv(world[:, ni[parent]]) @ world[:, j] if parent else world[:, j]
    entries = [dict(name=n, time=np.round(times, 7).tolist(), transform=np.round(local[:, j].reshape(-1, 16), 8).tolist()) for j, n in enumerate(names)]
    path = OUT / 'epicfight/assets/herobrine_companion/animmodels/animations/player/poem_unity09' / (name.lower() + '.json')
    write(path, {'animation': entries})
    np.savez_compressed(source_path, names=data['names'], times=times, world=world, local=local, grips=grips, contacts=contacts)
    write(metric_path, metrics)
    report_path = OUT / 'reports' / (name + '.json')
    report = load(report_path)
    report.update(export_hz=HZ, temporal_fit_sigma_seconds=sigma, sha256=hashlib.sha256(path.read_bytes()).hexdigest(),
                  max_grip_error=max(max(m['grip_error'].values()) for m in metrics),
                  max_sole_xy_error=max(m['feet'][s]['xy_error'] for m in metrics for s in ('R','L')),
                  minimum_body_height=min(m['body_min'] for m in metrics),
                  minimum_blade_height=min(m['blade_min'] for m in metrics),
                  minimum_held_weapon_body_clearance=min(m['weapon_body_clearance'] for m in metrics if max(m['contacts'].values()) > .99))
    write(report_path, report, True)
    print('REFINED', name, 'grip', round(report['max_grip_error'], 5), 'floor', round(report['minimum_body_height'], 5),
          'clearance', round(report['minimum_held_weapon_body_clearance'], 4), flush=True)


if __name__ == '__main__':
    for name in sys.argv[1:]:
        refine(name)
