"""Combat edit of the half-speed-reviewed MediaPipe scythe capture.

Keep the first eight grounded cuts; author compact timing and actual advancing
support steps. The complete capture and its original Blender bake stay intact.
Distances below are in source-rig units (10 pixels); Epic Fight uses 16/block.
"""
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from scipy.interpolate import PchipInterpolator

from combat_cleanup import CombatRig, PROFILE, smoothstep, stabilize_limb_roll

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT/'output/Herobrine_Scythe_Grounded_Combat'
FPS = 60
SCALE = 10/16
# label, reviewed source frames (1-based), duration in 60 Hz frames, travel,
# swing inclination, lowest hip height. No vault/release material is selected.
CUTS = [
    ('踏进右上斜斩', 1, 29, 30, .36, 42., .97),
    ('跟步反手回斩', 29, 47, 24, .32, 38., .96),
    ('进步前送横斩', 47, 65, 27, .42, 10., .95),
    ('跟步反向横斩', 65, 83, 24, .36, 12., .95),
    ('跨步突进斩', 83, 111, 33, .66, 18., .84),
    ('跟进上挑斩', 111, 129, 24, .34, 38., .96),
    ('追步斜劈', 129, 147, 27, .40, 32., .94),
    ('压身收势低扫', 147, 166, 30, .42, 6., .82),
]


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, allow_nan=False, separators=(',', ':'))+'\n', encoding='utf-8')


def main():
    source_path = ROOT/'output/Herobrine_Scythe_MediaPipe/capture/target_motion.json'
    source = json.loads(source_path.read_text(encoding='utf-8'))
    profile = json.loads(PROFILE.read_text(encoding='utf-8'))
    rig = CombatRig(profile, source)
    rig.grasp_smoothing = .65
    shoulder_turn = PchipInterpolator([0, 35, 45, 70, 100, 115, 147, 180],
                                     [-72, -66, -60, -48, -30, -18, 15, 27])
    poses, authored, segments = [], [], []
    start, traveled = 1, 0.
    for index, (label, source_a, source_b, count, blocks, tilt, low) in enumerate(CUTS):
        forward_cut = index % 2 == 0
        phase_a, phase_b = (26., 156.) if forward_cut else (156., 26.)
        roll_a, roll_b = (0., 180.) if forward_cut else (180., 0.)
        next_tilt = CUTS[(index+1) % len(CUTS)][5]
        phase = PchipInterpolator([0, .16, .26, .43, .61, .76, 1],
                                  [phase_a, phase_a, phase_a+(phase_b-phase_a)*.10,
                                   phase_a+(phase_b-phase_a)*.55, phase_b, phase_b, phase_b])
        height = PchipInterpolator([0, .12, .34, .53, .73, 1], [1.03, 1.015, low, low+.025, 1.0, 1.03])
        lean = PchipInterpolator([0, .16, .36, .55, .74, 1], [11, 13, 30 if index in (4, 7) else 23, 24, 16, 11])
        # Both feet travel each cut. Lead foot lands before the power stroke;
        # rear foot follows after that landing. At least one foot always supports.
        distance = blocks/SCALE
        contacts = [(start+round(count*.24), start+round(count*.64))]
        recovery = start+count-3
        segments.append({'name': f'combo_{index+1:02d}', 'label': label,
                         'first_frame': start, 'last_frame': start+count,
                         'contact_frames': contacts, 'recovery_frame': recovery,
                         'reference_frames': [source_a, source_b], 'travel_blocks': blocks})
        for tick in range(count+1):
            if index and tick == 0:
                continue
            u = tick/count
            lead = smoothstep((u-.02)/.30)
            follow = smoothstep((u-.40)/.48)
            root_fraction = .58*lead+.42*smoothstep((u-.40)/.48)
            theta = float(phase(u))
            lead_lift = (.20 if index == 4 else .15)*math.sin(math.pi*(u-.02)/.30)**2 if .02 < u < .32 else 0.
            rear_lift = (.15 if index == 4 else .12)*math.sin(math.pi*(u-.40)/.48)**2 if .40 < u < .88 else 0.
            hand_z, next_hand_z = (.14 if index != 7 else -.12), (-.12 if index == 6 else .14)
            c = {'phase': theta, 'inclination': tilt+(next_tilt-tilt)*smoothstep((u-.76)/.24),
                 'yaw': float(shoulder_turn(theta))-10., 'pelvis_yaw': 0.,
                 'lean': float(lean(u)), 'height': float(height(u)),
                 'advance': -traveled-distance*root_fraction,
                 'hand': [0., -.82, hand_z+(next_hand_z-hand_z)*smoothstep((u-.76)/.24)],
                 'hand_forward': .36+.16*math.sin(math.pi*smoothstep((u-.14)/.54)),
                 'roll': roll_a+(roll_b-roll_a)*smoothstep((u-.72)/.28),
                 'grip': -3., 'left_grip': -1.25,
                 'foot_lx': .32, 'foot_rx': -.32,
                 'foot_ly': -.34-traveled-distance*lead,
                 'foot_ry': .28-traveled-distance*follow,
                 'foot_l_lift': lead_lift, 'foot_r_lift': rear_lift}
            frame = len(poses)
            pose, _ = rig.synthesize(c, frame)
            poses.append(pose)
            authored.append(dict(c, segment=index+1,
                                 foot_left_planted=lead_lift < 1e-9,
                                 foot_right_planted=rear_lift < 1e-9,
                                 reference_frame=source_a+(source_b-source_a)*u))
        traveled += distance
        start += count
        print('GROUNDED_CUT', label, 'frames', count, 'travel', blocks, flush=True)
    repairs = stabilize_limb_roll(poses)
    max_ankle_error, max_grip_error = 0., 0.
    minimum_body, minimum_weapon = float('inf'), float('inf')
    for pose, c in zip(poses, authored):
        for side, short, grip in [('Left', 'l', -1.25), ('Right', 'r', -3.)]:
            ankle = pose[f'Leg:{side}:Lower'] @ [0, .6, 0, 1]
            target = np.array([c[f'foot_{short}x'], c[f'foot_{short}y']])
            max_ankle_error = max(max_ankle_error, float(np.linalg.norm(ankle[:2]-target)))
            palm = pose[f'Arm:{side}:Lower'] @ [0, .48, 0, 1]
            socket = pose['Weapon:Scythe'] @ [0, 0, grip, 1]
            max_grip_error = max(max_grip_error, float(np.linalg.norm(palm-socket)))
        minimum_body = min(minimum_body, float(rig.skin(pose)[:, 2].min()-rig.ground))
        minimum_weapon = min(minimum_weapon, float((rig.hull @ pose['Weapon:Scythe'][:3, :3].T+pose['Weapon:Scythe'][:3, 3])[:, 2].min()-rig.ground))
        assert c['foot_left_planted'] or c['foot_right_planted']
    metadata = {k:source[k] for k in ['source_url', 'source_author', 'weapon_scale', 'root_ground_local']}
    metadata.update(action='HB_Scythe_MediaPipe_Grounded_Combat_04', fps=FPS, fps_base=1,
                    frames=list(range(1, len(poses)+1)), duration_seconds=(len(poses)-1)/FPS,
                    grip_local_z=[-3.]*len(poses), left_grip_local_z=[-1.25]*len(poses),
                    support_hand_weight=[1.]*len(poses), release_interval_source_frames=[],
                    method='Google MediaPipe capture, half-speed visual review, grounded combat edit and coupled two-hand IK.',
                    limitations='Attack timing, root travel and foot plants are authored from the reviewed cuts; monocular capture does not measure their world-space depth.',
                    combat_cleanup={'version':'grounded_combat_04', 'source_sha256':hashlib.sha256(source_path.read_bytes()).hexdigest(),
                                    'notes':'Eight advancing grounded cuts. Left hand on shaft middle, right hand drives tail. Fast wide forward arcs; no release, pole vault or orbiting dance.',
                                    'capture_timing_retained':False, 'reference_review_playback_speed':.5,
                                    'opponent_direction_rig':[0., -1., 0.]},
                    markers=[{'frame':s['first_frame'], 'label':s['label']} for s in segments],
                    attack_segments=segments,
                    footwork=authored,
                    poses=[{n:m.tolist() for n,m in p.items()} for p in poses])
    report = {'version':'grounded_combat_04', 'frames':len(poses), 'fps':FPS,
              'duration_seconds':metadata['duration_seconds'], 'total_travel_blocks':traveled*SCALE,
              'maximum_ankle_xy_error_blocks':max_ankle_error*SCALE,
              'maximum_grip_error_blocks':max_grip_error*SCALE,
              'minimum_body_clearance_blocks':minimum_body*SCALE,
              'minimum_weapon_clearance_blocks':minimum_weapon*SCALE,
              'max_source_floor_lift_blocks':max(v for f,v in rig.floor_adaptations)*SCALE,
              'limb_roll_sign_repairs':repairs, 'segments':segments}
    dump(OUT/'capture/target_motion.json', metadata)
    dump(OUT/'capture/combat_edit_report.json', report)
    print('GROUNDED_COMBAT', json.dumps(report, ensure_ascii=True), flush=True)
    assert max_ankle_error < .025, max_ankle_error
    assert max_grip_error < 1e-4 and minimum_body >= 0 and minimum_weapon >= 0


if __name__ == '__main__':
    main()
