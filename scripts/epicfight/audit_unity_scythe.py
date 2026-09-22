"""Quantitative continuity review of the selected native-to-Epic-Fight clips."""
import json
import sys

import numpy as np
from scipy.spatial.transform import Rotation

from unity_scythe_common import OUT, WORK, load, rotations, write


def audit(name):
    data = np.load(WORK / 'retargeted' / (name + '.npz'))
    names = list(data['names'])
    times = data['times']
    result = {'name': name, 'export_hz': round(1 / np.diff(times).mean()),
              'finite': bool(np.isfinite(data['local']).all())}
    steps = []
    for kind in ('local', 'world'):
        rr = rotations(data[kind])
        delta = np.swapaxes(rr[:-1], -1, -2) @ rr[1:]
        angles = np.rad2deg(Rotation.from_matrix(delta.reshape(-1, 3, 3)).magnitude()).reshape(len(times) - 1, -1)
        maxima = angles.argmax(axis=0)
        result[kind + '_steps'] = sorted([
            {'joint': n, 'degrees': float(angles[maxima[j], j]),
             'source_frame': float(times[maxima[j] + 1] * 60)}
            for j, n in enumerate(names)], key=lambda x: -x['degrees'])
    metrics = load(WORK / 'retargeted' / (name + '_metrics.json'))
    held = [(i, m) for i, m in enumerate(metrics) if max(m['contacts'].values()) > .99]
    failures = [(i, m) for i, m in held if m['weapon_body_clearance'] < .005]
    result['held_body_fit_failures'] = len(failures)
    result['minimum_held_blade_body_clearance'] = min((m['weapon_body_clearance'] for i, m in held), default=None)
    result['failed_source_frames'] = [float(times[i] * 60) for i, m in failures]
    result['max_grip_error'] = max(max(m['grip_error'].values()) for m in metrics)
    result['minimum_body_height'] = min(m['body_min'] for m in metrics)
    result['minimum_blade_height'] = min(m['blade_min'] for m in metrics)
    return result


if __name__ == '__main__':
    selected = sys.argv[1:] or [c['name'] for c in load(OUT / 'reports/conversion_catalog.json')]
    results = [audit(name) for name in selected]
    write(OUT / 'reports/continuity_audit.json', results, True)
    for r in results:
        print(r['name'], 'fit_failures=', r['held_body_fit_failures'], 'clearance=', round(r['minimum_held_blade_body_clearance'], 4),
              'local=', [(s['joint'], round(s['degrees'], 1), round(s['source_frame'], 2)) for s in r['local_steps'][:4]],
              'world=', [(s['joint'], round(s['degrees'], 1), round(s['source_frame'], 2)) for s in r['world_steps'][:2]])
