"""Compare how the V4 (hero) and V6 (player) clips hold the scythe in the right hand.

For a fixed grip the weapon's axes expressed in the Hand_R frame should be roughly
constant and equal across animations of the same weapon. V6 is verified correct in
game by the player; V4 is the set reported as having the blade reversed.

hook = b-space -Y (blade hook side, from geo cube centroids: daoren/yuan sit at -Y)
pole = b-space +Z (handle end -Z, blade end +Z)

world_geometry_rot = Tool_R_world_rot @ inv(correction)_rot   (socket rotation)
Run: blender --background --factory-startup --python <this>
"""
import json
import sys
from pathlib import Path

import numpy as np

REPO121 = Path(r'E:\java\herobrine_companion')
REPO120 = Path(r'E:\java\herobrine companion')
sys.path.insert(0, str(REPO121 / 'scripts' / 'epicfight'))


def load(p):
    return json.loads(Path(p).read_text(encoding='utf-8'))


def rotation(m):
    u, _, vt = np.linalg.svd(np.array(m)[:3, :3])
    r = u @ vt
    if np.linalg.det(r) < 0:
        u[:, -1] *= -1
        r = u @ vt
    return r


def armature(path):
    arm = load(path)['armature']
    local, parent = {}, {}

    def walk(nodes, p=None):
        for n in nodes:
            local[n['name']] = np.array(n['transform']).reshape(4, 4)
            parent[n['name']] = p
            walk(n.get('children', []), n['name'])
    walk(arm['hierarchy'])
    return local, parent


def clip_world(path, local, parent):
    data = load(path)['animation']
    mats = {d['name']: np.array(d['transform'], dtype=float).reshape(-1, 4, 4) for d in data}
    n = len(next(iter(mats.values())))
    out = []
    for t in range(n):
        world = {}
        for name in local:
            m = mats[name][t] if name in mats else local[name]
            world[name] = (world[parent[name]] if parent[name] else np.eye(4)) @ m
        out.append(world)
    return out


def correction_of(report_path):
    rep = load(report_path)
    cal = np.array(rep['weapon']['model_to_socket'])
    ren = np.array(rep['weapon']['item_render_transform'])
    return cal @ np.linalg.inv(ren)


def summarize(label, worlds, correction, contact_indices):
    inv_rot = np.linalg.inv(rotation(correction))
    hooks, poles, hooks_c = [], [], []
    for i, w in enumerate(worlds):
        rh = rotation(w['Hand_R'])
        socket = rotation(w['Tool_R']) @ inv_rot
        rel = rh.T @ socket
        hooks.append(rel @ np.array([0., -1., 0.]))
        poles.append(rel @ np.array([0., 0., 1.]))
        if i in contact_indices:
            hooks_c.append(rel @ np.array([0., -1., 0.]))
    hooks, poles = np.array(hooks), np.array(poles)
    print('CLIP', label, 'ticks', len(worlds))
    print('   hook in hand  mean', np.round(hooks.mean(0), 4).tolist(),
          ' spread_deg', round(float(np.degrees(np.arccos(np.clip(hooks @ hooks.mean(0), -1, 1))).max()), 2))
    print('   pole in hand  mean', np.round(poles.mean(0), 4).tolist(),
          ' spread_deg', round(float(np.degrees(np.arccos(np.clip(poles @ poles.mean(0), -1, 1))).max()), 2))
    if hooks_c:
        print('   hook@contact  mean', np.round(np.array(hooks_c).mean(0), 4).tolist())
    return hooks.mean(0), poles.mean(0)


def main():
    biped_local, biped_parent = armature(REPO121 / 'build/epicfight-v6/biped.json')
    hero_local, hero_parent = armature(REPO120 / 'src/main/resources/assets/herobrine_companion/animmodels/entity/hero_biped_nightfall.json')
    corr_v6 = correction_of(REPO121 / 'build/epicfight-v6/conversion_report.json')
    corr_v4 = correction_of(REPO121 / 'build/epicfight-v4/conversion_report.json')
    print('CORRECTION_DELTA_ROT', round(float(np.degrees(np.arccos(np.clip(
        (np.trace(rotation(corr_v6).T @ rotation(corr_v4)) - 1) / 2, -1, 1)))), 4),
        'deg  translation delta', np.round(corr_v6[:3, 3] - corr_v4[:3, 3], 6).tolist())

    timing = load(REPO121 / 'src/main/resources/assets/herobrine_companion/epicfight/poem_v6_timing.json')
    v6_hooks, v6_poles = [], []
    segs = timing['segments']
    sample = segs[:1] + segs[len(segs) // 2:len(segs) // 2 + 1] + segs[-1:]
    for seg in sample:
        name = seg.get('name')
        path = REPO121 / f'src/main/resources/assets/herobrine_companion/animmodels/animations/player/poem_v6/{name}.json'
        if not path.exists():
            print('MISSING', path)
            continue
        worlds = clip_world(path, biped_local, biped_parent)
        contact = {round(float(seg['contact']) * 120)}
        h, p = summarize('V6 ' + str(name), worlds, corr_v6, contact)
        v6_hooks.append(h)
        v6_poles.append(p)
    for name in ('hold', 'ready'):
        path = REPO121 / f'src/main/resources/assets/herobrine_companion/animmodels/animations/player/poem_v6/{name}.json'
        if path.exists():
            worlds = clip_world(path, biped_local, biped_parent)
            h, p = summarize('V6 ' + name, worlds, corr_v6, {0})
            v6_hooks.append(h)
            v6_poles.append(p)

    v4_hooks, v4_poles = [], []
    for i in range(1, 5):
        path = REPO120 / f'src/main/resources/assets/herobrine_companion/animmodels/animations/hero/hero_scythe_combo_v4_{i}.json'
        if not path.exists():
            print('MISSING', path)
            continue
        worlds = clip_world(path, hero_local, hero_parent)
        h, p = summarize(f'V4 segment {i}', worlds, corr_v4, {0, len(worlds) // 2, len(worlds) - 1})
        v4_hooks.append(h)
        v4_poles.append(p)

    if v6_hooks and v4_hooks:
        v6h, v6p = np.array(v6_hooks).mean(0), np.array(v6_poles).mean(0)
        v4h, v4p = np.array(v4_hooks).mean(0), np.array(v4_poles).mean(0)
        ang_h = float(np.degrees(np.arccos(np.clip(np.dot(v6h, v4h), -1, 1))))
        ang_p = float(np.degrees(np.arccos(np.clip(np.dot(v6p, v4p), -1, 1))))
        print('COMPARE hook angle V6-vs-V4 %.2f deg' % ang_h)
        print('COMPARE pole angle V6-vs-V4 %.2f deg' % ang_p)
        print('COMPARE hooks', np.round(v6h, 4).tolist(), np.round(v4h, 4).tolist())
        print('COMPARE poles', np.round(v6p, 4).tolist(), np.round(v4p, 4).tolist())


if __name__ == '__main__':
    main()
