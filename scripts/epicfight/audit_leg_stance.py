"""Regression for the straight-knee singularity and the published leg-only edit."""
import json
import numpy as np
from scipy.spatial.transform import Rotation

from fix_unity_leg_stance import FIX, LEGS, catalog
from export_unity_scythe import Retarget
from unity_scythe_common import C, ROOT, WORK, GameMesh, NativeReference, load, unit, write

g, native = GameMesh(), NativeReference()
retarget = Retarget(g, native)
synthetic = 0
for side in ('R', 'L'):
    s = side.lower()
    for degrees in (0, 89, 179, 181, 270, 359):
        rotation = Rotation.from_euler('z', degrees, degrees=True).as_matrix()
        rs = {n: rotation @ r for n, r in native.rot.items()}
        expected = C @ rotation @ np.array([0., -1., 0.])
        for jitter in (-.001, -.0001, 0, .0001, .001):
            p = {'thigh_' + s: rotation @ np.array([0., 0., .9]),
                 'calf_' + s: rotation @ np.array([jitter, jitter, .45]),
                 'foot_' + s: np.zeros(3)}
            pole = retarget.knee_pole(p, rs, side)
            assert np.dot(pole, expected) > .99999, (side, degrees, jitter, pole)
            synthetic += 1
    retarget.previous = {}
    world = {n: m.copy() for n, m in g.rest.items()}
    a = world['Thigh_' + side][:3, 3].copy()
    retarget.limb(world, side, a + [0, 0, -.72], a + [0, 0, -.36], leg=True, pole=np.array([0., 0., -1.]))
    assert world['Leg_' + side][1, 3] > a[1], 'Degenerate leg pole must bend forward'
    synthetic += 1

sources = []
for name in dict.fromkeys(row[2] for row in catalog()):
    old = np.load(WORK / 'retargeted' / (name + '.npz'))
    new = np.load(FIX / 'retargeted' / (name + '.npz'))
    indices = {n: i for i, n in enumerate(new['names'])}
    nonlegs = [i for n, i in indices.items() if n not in LEGS]
    assert np.array_equal(old['world'][:, nonlegs], new['world'][:, nonlegs]), name
    bad_old = bad_new = 0
    maximum_foot_xy_change = 0
    for s in ('R', 'L'):
        upper, lower = indices['Thigh_' + s], indices['Leg_' + s]
        for array, key in ((old['world'], 'old'), (new['world'], 'new')):
            u = array[:, upper, :3, 1]; l = array[:, lower, :3, 1]; h = array[:, upper, :3, 0]
            flex = -np.einsum('ij,ij->i', np.cross(u, l), h)
            bad = int(np.count_nonzero(flex < -.001))
            if key == 'old': bad_old += bad
            else: bad_new += bad
        before = np.einsum('nij,j->ni', old['world'][:, lower, :3, :3], g.foot_centers[s]) + old['world'][:, lower, :3, 3]
        after = np.einsum('nij,j->ni', new['world'][:, lower, :3, :3], g.foot_centers[s]) + new['world'][:, lower, :3, 3]
        maximum_foot_xy_change = max(maximum_foot_xy_change, float(np.linalg.norm(after[:, :2] - before[:, :2], axis=1).max()))
    assert bad_new == 0, name
    sources.append(dict(source=name, frames=len(new['times']), reversed_knee_frames_before=bad_old,
                        reversed_knee_frames_after=bad_new, nonleg_world_exact=True, max_foot_xy_change=maximum_foot_xy_change))

published = load(FIX / 'reports/published.json')
for item in published:
    rel = item['resource']
    project = ROOT if item['project'] == 'neo' else ROOT.parent / 'herobrine companion'
    old = load(FIX / 'before' / item['project'] / rel)
    new = load(project / 'src/main/resources/assets/herobrine_companion' / rel)
    for before, after in zip(old['animation'], new['animation']):
        assert before['name'] == after['name'] and before['time'] == after['time'], rel
        if before['name'] not in LEGS:
            assert before == after, (rel, before['name'])
    peer = ROOT.parent / 'herobrine companion' / 'src/main/resources/assets/herobrine_companion' / rel
    assert new == load(peer), ('Two versions diverged', rel)

result = dict(status='passed', synthetic_straight_leg_checks=synthetic, resources_per_version=len(published)//2,
              sources=sources, nonleg_transforms_exact=True, all_key_times_exact=True, both_versions_equal=True,
              reversed_knee_frames_before=sum(row['reversed_knee_frames_before'] for row in sources),
              reversed_knee_frames_after=sum(row['reversed_knee_frames_after'] for row in sources))
write(FIX / 'reports/leg_stance_regression.json', result, True)
print('UNITY12_LEG_AUDIT_OK', result['synthetic_straight_leg_checks'], 'synthetic checks;', result['resources_per_version'],
      'resources/version; reversed knee frames', result['reversed_knee_frames_before'], '->', result['reversed_knee_frames_after'])
