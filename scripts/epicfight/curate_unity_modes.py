"""Publish four native motion sets to both Minecraft projects, preserving V6."""
import hashlib
import shutil

import numpy as np
from scipy.ndimage import gaussian_filter1d

from unity_scythe_common import OUT, ROOT, WORK, GameMesh, NativeReference, load, write

FINAL = ROOT / 'output/Herobrine_Scythe_UnityModes_10_EpicFight'
PROJECTS = [ROOT, ROOT.parent / 'herobrine companion']
MODES = [
    (0, 'normal', '普通', 'Combo_Attack_01_All', [0, 30, 60, 105, 166],
     [13.5, 47.5, 70.5, 111.5, 142], 'Run_Attack_01', [22.5, 32.5], 'Combo_Attack_Air_01', [17]),
    (1, 'realm_breaker', '破境', 'Combo_Attack_02_All', [0, 25, 65, 102, 166],
     [12, 52, 78.5, 114, 145.5], 'Skill_01', [44, 74], 'Combo_Attack_Air_02', [12.5, 26, 32.5]),
    (2, 'thunder', '鸣雷', 'Combo_Attack_03_All', [0, 25, 50, 162, 233],
     [21.5, 46.5, 70.5, 103, 120.5, 153.5, 193, 211.5], 'Dash_Air_Attack', [2.5, 20], 'Combo_Attack_Air_03', [6, 32.5, 39.5, 54.5]),
    (3, 'void_shatter', '碎空', 'Combo_Attack_04_All', [0, 35, 95, 150, 220],
     [26.5, 61, 89.5, 111.5, 148.5, 175, 198], 'Run_Attack_02', [27.5, 39, 75, 92.5], 'Combo_Attack_Air_04', [16, 22, 28]),
]


def source_speed(name):
    native = NativeReference()
    vertices = np.array(native.data['meshes']['Scythe']['vertices'])
    local = (np.c_[vertices, np.ones(len(vertices))] @ np.linalg.inv(native.rest['Scythe_Weapon_R']).T)[:, :3]
    local = local[np.unique(np.r_[local.argmin(axis=0), local.argmax(axis=0)])]
    data = np.load(WORK / 'native_samples' / (name + '.npz'))
    matrices = data['world'][:, list(data['names']).index('Scythe_Weapon_R')]
    positions = np.einsum('nij,pj->npi', matrices[:, :3, :3], local) + matrices[:, None, :3, 3]
    return gaussian_filter1d(np.linalg.norm(np.gradient(positions, data['times'], axis=0), axis=-1).max(axis=1), 1.5)


def contact_windows(name, peaks, lo, hi):
    speed = source_speed(name)
    windows = []
    for peak in peaks:
        if not lo <= peak < hi: continue
        index = round(peak * 2)
        left, right = index, index
        threshold = speed[index] * .42
        while left > lo * 2 and index - left < 22 and speed[left - 1] >= threshold: left -= 1
        while right < (hi - 1) * 2 and right - index < 22 and speed[right + 1] >= threshold: right += 1
        first = max(lo + .25, min(left / 2, peak - 2.5))
        last = min(hi - .75, max(right / 2, peak + 2.5))
        windows.append([first, last])
    merged = []
    for first, last in windows:
        if merged and first <= merged[-1][1] + 1.5:
            merged[-1][1] = max(merged[-1][1], last)
        else:
            merged.append([first, last])
    assert merged, (name, lo, hi)
    return [dict(start=round((first - lo) / 60, 7), end=round((last - lo) / 60, 7),
                 source_frames=[first, last]) for first, last in merged]


def clip(name, lo, hi, standalone=False):
    data = np.load(WORK / 'retargeted' / (name + '.npz'))
    hz = round(1 / np.diff(data['times']).mean())
    first, last = round(lo * hz / 60), round(hi * hz / 60)
    matrices = data['local'][first:last + 1].copy(); names = list(data['names'])
    root = names.index('Root')
    offset = matrices[0, root, :2, 3].copy()
    matrices[:, root, :2, 3] -= offset
    initial_lift = max(0., matrices[0, root, 2, 3] - GameMesh().rest['Root'][2, 3]) if standalone else 0.
    matrices[:, root, 2, 3] -= initial_lift
    times = np.arange(len(matrices)) / hz
    value = {'animation': [dict(name=n, time=np.round(times, 7).tolist(),
             transform=np.round(matrices[:, j].reshape(-1, 16), 8).tolist()) for j, n in enumerate(names)]}
    travel = (matrices[-1, root, :3, 3] - matrices[0, root, :3, 3]).tolist()
    return value, travel, initial_lift


def publish(key, name, value, sidecar=None):
    for actor in ('player', 'hero'):
        rel = 'assets/herobrine_companion/animmodels/animations/' + actor + '/poem_unity09/' + key
        target = FINAL / 'resources' / rel
        write(target / (name + '.json'), value)
        if sidecar: write(target / 'data' / (name + '.json'), sidecar)
        for project in PROJECTS:
            folder = project / 'src/main/resources' / rel
            folder.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(target / (name + '.json'), folder / (name + '.json'))
            if sidecar:
                (folder / 'data').mkdir(exist_ok=True)
                shutil.copyfile(target / 'data' / (name + '.json'), folder / 'data' / (name + '.json'))


def timing(output_name, source, lo, hi, peaks, special=False, chain_delay=None):
    value, travel, lift = clip(source, lo, hi, special)
    contacts = contact_windows(source, peaks, lo, hi)
    duration = (hi - lo) / 60
    recovery = min(duration - .005, max(duration - .09, contacts[-1]['end'] + .006))
    if chain_delay is not None:
        recovery = min(duration - .005, contacts[-1]['end'] + chain_delay)
    result = dict(name=output_name, source_clip=source, source_first_frame=lo, source_last_frame=hi,
                  start=round(lo / 60, 7) if not special else 0., duration=round(duration, 7),
                  recovery=round(recovery, 7), contacts=contacts, travel_blocks=travel,
                  standalone_initial_height_removed=lift, playback_speed=1.)
    return result, value


def main():
    mode_rows = []
    manifest = []
    attack_layer = dict(layer='BASE_LAYER', priority='HIGHEST')
    for index, key, label, source, cuts, peaks, dash, dash_peaks, air, air_peaks in MODES:
        segments = []
        for i, (lo, hi) in enumerate(zip(cuts[:-1], cuts[1:])):
            name = 'combo_%02d' % (i + 1)
            item, value = timing(name, source, lo, hi, peaks, chain_delay=.10 if i == 3 else None)
            segments.append(item); publish(key, name, value, attack_layer)
        specials = []
        for name, native_name, native_peaks in [('dash', dash, dash_peaks), ('air', air, air_peaks)]:
            data = np.load(WORK / 'retargeted' / (native_name + '.npz'))
            end = round(data['times'][-1] * 60)
            item, value = timing(name, native_name, 0, end, native_peaks, True)
            specials.append(item); publish(key, name, value, attack_layer)
        full, _, _ = clip(source, cuts[0], cuts[-1])
        publish(key, 'full', full, attack_layer)
        ready = {'animation': [dict(name=row['name'], time=[0., 1.],
                  transform=[row['transform'][0], row['transform'][0]]) for row in full['animation']]}
        publish(key, 'ready', ready, dict(layer='BASE_LAYER', priority='HIGH'))
        hold = {'animation': [row for row in ready['animation'] if row['name'] != 'Root'
                               and not row['name'].startswith(('Thigh_', 'Leg_', 'Knee_'))]}
        publish(key, 'hold', hold, dict(layer='COMPOSITE_LAYER', priority='MIDDLE', masks=[dict(livingmotion='ALL', type='arms')]))
        mode_rows.append(dict(mode=index, key=key, label=label, ground_source=source, source_boundaries=cuts,
                              segments=segments, specials=specials))
        manifest.append(dict(mode=index, key=key, label=label, combo_source=source, dash_source=dash,
                             air_source=air, combo_count=4, dash_count=1, air_count=1))
    timeline = dict(source_package='Scythe Animation Pack1.0.unitypackage', source_fps=60, export_hz=240,
                    playback_speed=1., combo_restart_after_last_contact_seconds=.10,
                    contact_method='Reviewed native blade-speed peaks; separate per-strike hit phases', modes=mode_rows)
    rel = 'assets/herobrine_companion/epicfight/poem_unity09_timing.json'
    write(FINAL / 'resources' / rel, timeline, True)
    for project in PROJECTS: shutil.copyfile(FINAL / 'resources' / rel, project / 'src/main/resources' / rel)
    write(FINAL / 'reports/mode_manifest.json', manifest, True)
    print('PUBLISHED_FOUR_MODES', FINAL, flush=True)
    for mode in mode_rows:
        print(mode['label'], [(s['name'], s['duration'], [(h['start'], h['end']) for h in s['contacts']])
                               for s in mode['segments'] + mode['specials']], flush=True)


if __name__ == '__main__': main()
