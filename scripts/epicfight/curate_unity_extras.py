"""Publish three gestures with ground/air variants and visible blade windows."""
import shutil

import numpy as np

from curate_unity_modes import FINAL, PROJECTS, clip, publish, source_speed
from unity_scythe_common import WORK, write

EXTRAS = [
    (0, 'flurry', '速斩', .70, 1., 'Speed_Attack_All', [21, 27.5, 34.5, 41, 48.5, 62],
     'Speed_Attack_Air_All', [21, 27.5, 34.5, 41, 48.5, 62]),
    (1, 'uppercut', '挑空', 1., 1.2, 'Attack_Up_Floor_to_Air_02', [12.5, 19.5, 49],
     'Attack_Up_Air_to_Air_03', [12.5, 19.5, 49]),
    (2, 'heavy', '重击', 1.5, 1.7, 'Combo_Attack_05_01', [25],
     'Attack_Air_to_Floor_01_All', [43]),
]


def windows(source, peaks, end):
    speed = source_speed(source)
    result = []
    for i, peak in enumerate(peaks):
        # Consecutive cuts must be separate phases: a victim can be struck once
        # by each sweep, never repeatedly by one continuous contact interval.
        left_bound = (peaks[i-1] + peak) / 2 + .5 if i else .25
        right_bound = (peak + peaks[i+1]) / 2 - .5 if i + 1 < len(peaks) else end - .75
        first = last = round(peak * 2)
        threshold = speed[first] * .5
        while first / 2 > left_bound and peak - first / 2 < 6 and speed[first-1] >= threshold: first -= 1
        while last / 2 < right_bound and last / 2 - peak < 6 and speed[last+1] >= threshold: last += 1
        lo = max(left_bound, min(peak - 1.25, first / 2))
        hi = min(right_bound, max(peak + 1.25, last / 2))
        assert lo < peak < hi
        result.append(dict(start=round(lo / 60, 7), end=round(hi / 60, 7), source_frames=[lo, hi]))
    return result


def main():
    moves = []
    for gesture, key, label, damage, impact, ground, ground_peaks, air, air_peaks in EXTRAS:
        row = dict(gesture=gesture, key=key, label=label, damage=damage, impact=impact)
        for kind, source, peaks in [('ground', ground, ground_peaks), ('air', air, air_peaks)]:
            data = np.load(WORK / 'retargeted' / (source + '.npz'))
            end = round(data['times'][-1] * 60)
            if key == 'heavy':
                end = 52 if kind == 'ground' else 66
            # The plunge retains the floor-relative authored pose: its subclass
            # adds collision-respecting descent and a bounded airborne hold.
            value, travel, lift = clip(source, 0, end, standalone=not (key == 'heavy' and kind == 'air'))
            contacts = windows(source, peaks, end)
            recovery = min(end / 60 - .005, max(end / 60 - .09, contacts[-1]['end'] + .006))
            if key == 'heavy':
                recovery = min(end / 60 - .005, contacts[-1]['end'] + .12)
            timing = dict(name=key + '_' + kind, source_clip=source, source_first_frame=0,
                          source_last_frame=end, start=0., duration=round(end / 60, 7),
                          recovery=round(recovery, 7), contacts=contacts,
                          travel_blocks=travel, standalone_initial_height_removed=lift, playback_speed=1.)
            if key == 'heavy' and kind == 'air':
                timing['runtime_landing'] = dict(hold_source_frames=[27, 35], maximum_hold_ticks=60,
                                                descent_blocks_per_tick=.35, resume_floor_distance=1.0)
            row[kind] = timing
            publish('extra', timing['name'], value, dict(layer='BASE_LAYER', priority='HIGHEST'))
        moves.append(row)
    timeline = dict(source_package='Scythe Animation Pack1.0.unitypackage', source_fps=60, export_hz=240,
                    playback_speed=1., moves=moves,
                    inputs=['Mouse 4 + Attack', 'Mouse 5 + Attack', 'Use / Mouse Right + Attack'],
                    primary_attack='Release a short attack tap for a native combo; hold attack for 350 ms for one heavy',
                    heavy_hold_milliseconds=350, heavy_repeat_while_held=False,
                    heavy_chain_after_last_contact_seconds=.12,
                    shared_use_behavior='A use tap without an attack chord is replayed through vanilla on release',
                    buffer_ticks=10, live_gameplay_tested=False)
    rel = 'assets/herobrine_companion/epicfight/poem_unity09_extras.json'
    write(FINAL / 'resources' / rel, timeline, True)
    for project in PROJECTS: shutil.copyfile(FINAL / 'resources' / rel, project / 'src/main/resources' / rel)
    write(FINAL / 'reports/extra_manifest.json', timeline, True)
    for row in moves:
        print('PUBLISHED_EXTRA', row['key'], [(k, row[k]['duration'], len(row[k]['contacts'])) for k in ('ground', 'air')], flush=True)


if __name__ == '__main__': main()
