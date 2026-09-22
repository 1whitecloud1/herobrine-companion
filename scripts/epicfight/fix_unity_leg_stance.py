"""Repair only the leg channels of published Unity motions, keeping all timing/weapon data.

The staged output and previous resources are kept outside the delivered Unity11
folder. Run without --publish to inspect and audit before updating either repo.
"""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

import numpy as np
from scipy.spatial.transform import Rotation

from export_unity_scythe import Retarget
from unity_scythe_common import ASSETS, C, ROOT, WORK, GameMesh, NativeReference, load, rotations, unit, write

FIX = ROOT / 'build/poem_legfix'
LEGS = {p + s for p in ('Thigh_', 'Leg_', 'Knee_') for s in ('R', 'L')}


def catalog():
    rows = []
    modes = load(ASSETS / 'epicfight/poem_unity09_timing.json')
    for mode in modes['modes']:
        for item in mode['segments'] + mode['specials']:
            rows.append((mode['key'], item['name'], item['source_clip'], item['source_first_frame'], item['source_last_frame']))
        rows.append((mode['key'], 'full', mode['ground_source'], mode['source_boundaries'][0], mode['source_boundaries'][-1]))
        rows.append((mode['key'], 'ready', mode['ground_source'], 0, 0))
    for move in load(ASSETS / 'epicfight/poem_unity09_extras.json')['moves']:
        for kind in ('ground', 'air'):
            item = move[kind]
            rows.append(('extra', item['name'], item['source_clip'], item['source_first_frame'], item['source_last_frame']))
    return rows


def knee_helper(g, world, side, previous):
    upper, lower, knee = ('Thigh_' + side, 'Leg_' + side, 'Knee_' + side)
    ur, lr = rotations(world[upper]), rotations(world[lower])
    relative = ur.T @ lr
    angle = np.arctan2(relative[2, 1] - relative[1, 2], relative[1, 1] + relative[2, 2])
    if side in previous:
        old = previous[side]
        angle = old + np.arctan2(np.sin(angle - old), np.cos(angle - old))
    previous[side] = angle
    world[knee][:3, :3] = ur @ Rotation.from_rotvec([angle * .5, 0., 0.]).as_matrix() @ g.neutral[upper].T @ g.neutral[knee]
    world[knee][:3, 3] = world[lower][:3, 3]


def solve(name):
    g, native = GameMesh(), NativeReference()
    retarget = Retarget(g, native)
    retarget.previous = {}
    original = np.load(WORK / 'retargeted' / (name + '.npz'))
    names, times = list(original['names']), original['times']
    world = original['world'].copy()
    source = np.load(WORK / 'native_samples' / (name + '.npz'))
    snames = list(source['names'])
    sw = source['world']
    count = len(times)
    assert count == len(sw) * 2 - 1, (name, count, len(sw))
    sp = np.empty((count, len(snames), 3))
    sr = np.empty((count, len(snames), 3, 3))
    sp[::2] = sw[:, :, :3, 3]
    sp[1::2] = (sp[::2][:-1] + sp[::2][1:]) * .5
    sr[::2] = rotations(sw)
    sr[1::2] = rotations((sr[::2][:-1] + sr[::2][1:]) * .5)
    origin = sp[0, snames.index('pelvis')].copy()
    origin[2] = 0
    helper_angles = {}
    foot_error, foot_height_error, bend_alignment, minimum_flex = [], [], [], []
    for i in range(count):
        w = dict(zip(names, world[i]))
        p, r = dict(zip(snames, sp[i])), dict(zip(snames, sr[i]))
        metrics = retarget.legs(w, p, r, origin)
        for s in ('R', 'L'):
            knee_helper(g, w, s, helper_angles)
            a, k = w['Thigh_' + s][:3, 3], w['Leg_' + s][:3, 3]
            e = (w['Leg_' + s] @ np.r_[g.foot_centers[s], 1])[:3]
            axis = unit(e - a)
            bend = unit(k - a - axis * np.dot(k - a, axis))
            pole = retarget.knee_pole(p, r, s)
            pole = unit(pole - axis * np.dot(pole, axis))
            bend_alignment.append(float(np.dot(bend, pole)))
            minimum_flex.append(float(-np.dot(np.cross(unit(k - a), unit(e - k)), w['Thigh_' + s][:3, 0])))
            foot_error.append(metrics[s]['xy_error'])
            foot_height_error.append(abs(metrics[s]['minimum'] - (.012 + metrics[s]['source_lift'] * retarget.root_scale)))
    nonlegs = [j for j, n in enumerate(names) if n not in LEGS]
    assert np.array_equal(world[:, nonlegs], original['world'][:, nonlegs]), 'Changed a non-leg world matrix'
    local = original['local'].copy()
    indices = {n: j for j, n in enumerate(names)}
    for j, n in enumerate(names):
        if n in LEGS:
            local[:, j] = np.linalg.inv(world[:, indices[g.parents[n]]]) @ world[:, j]
    FIX.joinpath('retargeted').mkdir(parents=True, exist_ok=True)
    np.savez_compressed(FIX / 'retargeted' / (name + '.npz'), names=original['names'], times=times, world=world, local=local)
    headings = {}
    for s in ('R', 'L'):
        j = indices['Leg_' + s]
        old = original['world'][0, j, :3, :3] @ g.neutral['Leg_' + s].T @ [0., 1., 0.]
        new = world[0, j, :3, :3] @ g.neutral['Leg_' + s].T @ [0., 1., 0.]
        headings[s] = dict(before=float(np.degrees(np.arctan2(old[0], old[1]))), after=float(np.degrees(np.arctan2(new[0], new[1]))))
    angular = []
    for n in LEGS:
        rs = rotations(world[:, indices[n]])
        angular.extend(np.degrees(Rotation.from_matrix(np.swapaxes(rs[:-1], 1, 2) @ rs[1:]).magnitude()).tolist())
    result = dict(source=name, frames=count, nonleg_world_matrices_exact=True, first_frame_shin_heading_degrees=headings,
                  max_foot_xy_error=float(max(foot_error)), max_sole_height_error=float(max(foot_height_error)),
                  minimum_anatomical_knee_flex=float(min(minimum_flex)), minimum_source_pole_alignment=float(min(bend_alignment)),
                  max_leg_rotation_per_240hz_frame_degrees=float(max(angular)))
    write(FIX / 'reports' / (name + '.json'), result, True)
    print('LEGS_SOLVED', json.dumps(result, ensure_ascii=False), flush=True)
    return result


def stage(rows):
    count = 0
    for mode, move, source, first, last in rows:
        file = FIX / 'retargeted' / (source + '.npz')
        if not file.exists():
            continue
        corrected = np.load(file)
        names = list(corrected['names'])
        for actor in ('player', 'hero'):
            rel = Path('animmodels/animations') / actor / 'poem_unity09' / mode / (move + '.json')
            original = load(ASSETS / rel)
            for channel in original['animation']:
                if channel['name'] not in LEGS:
                    continue
                indices = np.full(len(channel['time']), round(first * 4), dtype=int) if move == 'ready' else np.rint(np.asarray(channel['time']) * 240 + first * 4).astype(int)
                assert indices[0] == first * 4 and (move == 'ready' or indices[-1] == last * 4), rel
                channel['transform'] = np.round(corrected['local'][indices, names.index(channel['name'])].reshape(-1, 16), 8).tolist()
            write(FIX / 'resources' / rel, original)
            count += 1
    print('STAGED_LEG_ONLY_RESOURCES', count, flush=True)


def publish():
    changed = []
    for project, key in [(ROOT, 'neo'), (ROOT.parent / 'herobrine companion', 'forge')]:
        base = project / 'src/main/resources/assets/herobrine_companion'
        for source in (FIX / 'resources').rglob('*.json'):
            rel = source.relative_to(FIX / 'resources')
            dest = base / rel
            assert dest.is_file(), dest
            before, after = load(dest), load(source)
            b, a = {r['name']: r for r in before['animation']}, {r['name']: r for r in after['animation']}
            assert b.keys() == a.keys()
            for n in b:
                assert b[n]['time'] == a[n]['time'], (rel, n, 'timing')
                if n not in LEGS:
                    assert b[n] == a[n], (rel, n, 'non-leg')
            backup = FIX / 'before' / key / rel
            backup.parent.mkdir(parents=True, exist_ok=True)
            if not backup.exists():
                shutil.copyfile(dest, backup)
            shutil.copyfile(source, dest)
            changed.append(dict(project=key, resource=str(rel).replace('\\', '/'), sha256=hashlib.sha256(dest.read_bytes()).hexdigest()))
    write(FIX / 'reports/published.json', changed, True)
    print('PUBLISHED_LEG_ONLY_RESOURCES', len(changed), flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--sources', nargs='*')
    parser.add_argument('--publish', action='store_true')
    parser.add_argument('--stage-only', action='store_true')
    parser.add_argument('--reuse-solved', action='store_true')
    args = parser.parse_args()
    rows = catalog()
    if not args.stage_only:
        for name in dict.fromkeys(row[2] for row in rows):
            if args.sources is None or name in args.sources:
                if args.reuse_solved and (FIX / 'retargeted' / (name + '.npz')).exists():
                    continue
                solve(name)
    stage(rows)
    if args.publish:
        publish()
