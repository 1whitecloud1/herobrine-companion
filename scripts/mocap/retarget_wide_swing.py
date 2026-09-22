"""Restore large scythe arcs after MediaPipe capture, without a forward-cone clamp.

The image-plane shaft direction is measured in the reference. Monocular depth
and combat exaggeration are authored here, not claimed as recovered 3D truth.
Captured steps and per-cut timing remain distinct; both hands share one shaft.
"""
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from scipy.interpolate import PchipInterpolator
from scipy.ndimage import gaussian_filter1d
from scipy.spatial.transform import Rotation, Slerp

from combat_cleanup import CombatRig, PROFILE, affine, rotation, unit, two_bone, stabilize_limb_roll
from reviewed_weapon_capture import capture_axes

ROOT = Path(__file__).resolve().parents[2]
VERSION = 'mediapipe_wide_swing_06'
# Reviewed combat edits: blade plane inclination and continuous sweep phase.
# The single view cannot distinguish shaft depth during the motion-blur frames.
# These curves clean those ambiguous reversals into the eight visible attacks.
SWING_EDITS = [
    (32., -20., 210.),   # right shoulder -> opposite low follow-through
    (32., 210., -25.),   # reverse rising cut
    (8., -25., 210.),    # broad waist-height sweep
    (-8., 210., -25.),   # reverse waist cut
    (10., -25., 225.),   # advancing heavy sweep
    (28., 225., -20.),   # rising return
    (53., -20., 205.),   # overhead diagonal, floor-limited on follow-through
    (6., 205., -20.),    # crouched low sweep
]


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, allow_nan=False, indent=2) + '\n', encoding='utf-8')


def lerp(array, f):
    a = min(int(f), len(array)-1)
    b = min(a+1, len(array)-1)
    return array[a]*(1-f%1)+array[b]*(f%1)


def span(axes):
    return float(np.degrees(np.arccos(np.clip(axes @ axes.T, -1, 1))).max())


def transport_bases(axes):
    result = []
    for i, axis in enumerate(axes):
        if not i:
            yy = unit([0., 1., 0.]-axis*axis[1], (0, 0, 1))
        else:
            cross = np.cross(axes[i-1], axis)
            angle = math.atan2(np.linalg.norm(cross), np.dot(axes[i-1], axis))
            yy = Rotation.from_rotvec(unit(cross)*angle).apply(result[-1][:, 1])
            yy = unit(yy-axis*np.dot(yy, axis))
        xx = unit(np.cross(yy, axis))
        result.append(np.column_stack([xx, np.cross(axis, xx), axis]))
    return np.array(result)


def dynamic_path(unary, angles, weight, maximum_step):
    delta = angles[:, None]-angles[None, :]
    transition = weight*delta**2+np.where(abs(delta) > maximum_step, 1e6, 0)
    cost, backs = unary[0], []
    for row in unary[1:]:
        candidates = cost[:, None]+transition
        backs.append(candidates.argmin(axis=0))
        cost = row+candidates.min(axis=0)
    choice = int(cost.argmin())
    path = [choice]
    for previous in reversed(backs):
        choice = int(previous[choice])
        path.append(choice)
    return angles[path[::-1]]


def solve_grounded_leg(rig, pose, side, foot, original_hint):
    short, sign = ('l', 1) if side == 'Left' else ('r', -1)
    origin, pelvis = pose['Bone.011'][:3, 3], pose['Bone.011'][:3, :3]
    hip = origin+pelvis @ [sign*.2, 0, 0]
    goal = np.array([foot[f'foot_{short}x'], foot[f'foot_{short}y'], rig.ground+.12+foot[f'foot_{short}_lift']])
    for _ in range(24):
        direction = unit(goal-hip)
        bend = unit(original_hint-hip-direction*np.dot(original_hint-hip, direction), (0, -1, 0))
        upper, lower, end = two_bone(hip, goal, hip+bend, .6, .6, pelvis[:, 0])
        up = unit(np.array([0, 0, 1])-direction*direction[2], bend)
        for _ in range(24):
            if lower[2, 3] >= rig.ground+.24:
                break
            bend = unit(bend*.85+up*.15)
            upper, lower, end = two_bone(hip, goal, hip+bend, .6, .6, pelvis[:, 0])
        goal[2] = rig.ground+.012+.2*(abs(lower[2, 0])+abs(lower[2, 2]))+foot[f'foot_{short}_lift']
    assert np.linalg.norm(end[:2]-goal[:2]) < 1e-5, (side, end, goal)
    pose[f'Leg:{side}:Upper'], pose[f'Leg:{side}:Lower'] = upper, lower


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--work', type=Path, default=ROOT/'build/scythe_wide_06')
    parser.add_argument('--output', type=Path, default=ROOT/'output/Herobrine_Scythe_Wide_06')
    parser.add_argument('--sweep-scale', type=float, default=1.)
    args = parser.parse_args()
    work, out = args.work.resolve(), args.output.resolve()
    base_path = ROOT/'output/Herobrine_Scythe_Recapture_05/capture/target_motion.json'
    data, profile = load(base_path), load(PROFILE)
    old = [{n: np.array(m) for n, m in row.items()} for row in data['poses']]
    original = copy.deepcopy(data)
    rig = CombatRig(profile, data)
    rig.grasp_smoothing = .15
    rig.max_grip_seeds = 3
    rig.grip_iterations = 80
    capture = np.load(work/'capture_selected.npz')
    observed_axes = capture_axes()
    # Allow a readable wind-up/contact/follow-through in normal game playback.
    counts = [36, 38, 36, 36, 42, 38, 38, 40]
    raw, feet, segments, refs, old_indices = [], [], [], [], []
    start = 1
    rotations = {n: Slerp(np.arange(len(old)), Rotation.from_matrix([row[n][:3, :3]/(rig.scale if n == 'Weapon:Scythe' else 1.) for row in old])) for n in old[0]}
    for ci, (segment, count) in enumerate(zip(original['attack_segments'], counts)):
        a, b = segment['first_frame']-1, segment['last_frame']-1
        s = copy.deepcopy(segment)
        s.update(first_frame=start, last_frame=start+count,
                 contact_frames=[(start+round(count*.25), start+round(count*.65))],
                 recovery_frame=start+count-4)
        segments.append(s)
        for k in range(count+1):
            if ci and not k:
                continue
            f = a+(b-a)*k/count
            row = {n: affine(rotations[n](f).as_matrix()*(rig.scale if n == 'Weapon:Scythe' else 1.),
                             lerp(np.array([p[n][:3, 3] for p in old]), f)) for n in old[0]}
            foot = dict(original['footwork'][min(int(f), len(old)-1)])
            for key in ['foot_lx', 'foot_ly', 'foot_rx', 'foot_ry', 'foot_l_lift', 'foot_r_lift', 'reference_frame']:
                foot[key] = float(lerp(np.array([p[key] for p in original['footwork']]), f))
            foot['segment'] = ci+1
            raw.append(row)
            feet.append(foot)
            refs.append(foot['reference_frame']-1)
            old_indices.append(f)
        start += count
    count = len(raw)
    # Retiming must preserve a common support foot across every interpolation.
    for i in range(count-2, -1, -1):
        if any(feet[i][f'foot_{s}_lift'] < 1e-9 and feet[i+1][f'foot_{s}_lift'] < 1e-9 for s in 'lr'):
            continue
        side = min('lr', key=lambda s: feet[i+1][f'foot_{s}_lift'])
        for axis in 'xy':
            feet[i][f'foot_{side}{axis}'] = feet[i+1][f'foot_{side}{axis}']
        feet[i][f'foot_{side}_lift'] = 0.

    axes, leans, captured_yaws, captured_hands = [], [], [], []
    for i, f in enumerate(refs):
        axis = unit(lerp(observed_axes, f))
        angle = math.acos(np.clip(-axis[1], -1, 1))
        radial = unit([axis[0], 0., axis[2]])
        wide = min(math.radians(148), angle*2.25)
        axis = radial*math.sin(wide)+np.array([0., -math.cos(wide), 0.])
        # A real long blade cannot point straight down while the body crouches.
        # Adjust elevation only; the wide horizontal sweep is not contracted.
        z = float(np.clip(axis[2], -.48, .93))
        axis = np.r_[unit(axis[:2])[:2]*math.sqrt(1-z*z), z]
        axes.append(axis)
        p = lerp(capture['points'], f*2)
        up = p[[11, 12]].mean(axis=0)-p[[23, 24]].mean(axis=0)
        leans.append(float(np.clip(math.atan2(np.linalg.norm(up[:2]), up[2]), .08, .62)))
        old_chest = raw[i]['Chest'][:3, :3] @ rig.rest['Chest'][:3, :3].T
        captured_yaws.append(math.atan2(old_chest[1, 0], old_chest[0, 0]))
        hand = p[[15, 16]].mean(axis=0)-p[[11, 12]].mean(axis=0)
        captured_hands.append(np.clip(hand, [-.45, -.6, -.6], [.45, .6, .6]))
    axes = np.array(axes)
    # Do not merely amplify the frame-to-frame monocular jitter: a swing needs
    # a visible continuous path. Contact traverses most of the measured arc,
    # while the captured body/feet still use their individual source intervals.
    attack_ease = PchipInterpolator([0., .18, .26, .65, .86, 1.], [0., .025, .08, .89, .985, 1.])
    for segment, (inclination, first_phase, last_phase) in zip(segments, SWING_EDITS):
        a, b = segment['first_frame']-1, segment['last_frame']-1
        diagonal = np.array([-math.cos(math.radians(inclination)), 0., math.sin(math.radians(inclination))])
        for i in range(a, b+1):
            if a and i == a:
                continue
            u = (i-a)/(b-a)
            phase = math.radians(first_phase+(last_phase-first_phase)*args.sweep_scale*float(attack_ease(u)))
            axis = diagonal*math.cos(phase)+np.array([0., -1., 0.])*math.sin(phase)
            z = max(-.45, axis[2])
            axis = np.r_[unit(axis[:2])[:2]*math.sqrt(1-z*z), z]
            if a and i-a < 7:
                # Adjacent attacks share their exact seam. Blend only the
                # wind-up's small change of strike plane, not the contact arc.
                t = (i-a)/7
                t = t*t*(3-2*t)
                axis = unit(axes[a]*(1-t)+axis*t)
            axes[i] = axis
            # Shoulder turns precede the hands and continue into the release.
            captured_yaws[i] = .12*captured_yaws[i] + .88*math.radians(-95*math.cos(phase))
    leans = gaussian_filter1d(leans, 1.1, mode='nearest')
    yaw_grid = np.radians(np.arange(-155, 156, 5))
    unary = []
    for axis, observed in zip(axes, captured_yaws):
        shoulder_deltas = np.c_[np.cos(yaw_grid), np.sin(yaw_grid), np.zeros(len(yaw_grid))]
        reach = np.linalg.norm(shoulder_deltas-axis*1.75*rig.scale, axis=1)
        front = np.c_[np.sin(yaw_grid), -np.cos(yaw_grid), np.zeros(len(yaw_grid))]
        behind = front @ axis
        unary.append(.30*(yaw_grid-observed)**2+80*np.maximum(0, reach-1.42)**2+
                     30*np.maximum(0, -.10-behind)**2+
                     2*np.maximum(0, abs(yaw_grid)-math.radians(110))**2)
    yaws = dynamic_path(np.array(unary), yaw_grid, 4., math.radians(15))
    yaws = gaussian_filter1d(yaws, 1., mode='nearest')
    bases = transport_bases(axes)
    roll_grid = np.radians(np.arange(-180, 181, 5))
    roll_rotations = Rotation.from_rotvec(np.c_[np.zeros((len(roll_grid), 2)), roll_grid]).as_matrix()
    unary = []
    for i, axis in enumerate(axes):
        height = raw[i]['Bone.011'][2, 3]+.6+.4
        rr = np.einsum('ij,kjl->kil', bases[i], roll_rotations)
        offsets = np.einsum('pj,kij->kpi', (rig.hull-[0, 0, -3])*rig.scale, rr)[:, :, 2].min(axis=1)
        minimum = rig.ground+.028-offsets
        maximum = min(height+.77, height-axis[2]*1.75*rig.scale+.77)
        unary.append(6*np.maximum(0, minimum-(height-.2-axis[2]*.5))**2+
                     1500*np.maximum(0, minimum-maximum)**2+.004*roll_grid**2)
    rolls = dynamic_path(np.array(unary), roll_grid, 18., math.radians(20))
    rolls = gaussian_filter1d(rolls, .7, mode='nearest')

    poses, adaptations = [], []
    for i, reference in enumerate(refs):
        origin = raw[i]['Bone.011'][:3, 3]
        pose = {'Bone.011': raw[i]['Bone.011'].copy()}
        for side in ('Right', 'Left'):
            solve_grounded_leg(rig, pose, side, feet[i], raw[i][f'Leg:{side}:Lower'][:3, 3])
        expected_yaw = yaws[i]
        candidates = []
        for level in [0., .07, .14, .22, .32, .43]:
            az = min(.93, axes[i, 2]+level) if axes[i, 2] < 0 else axes[i, 2]
            axis = np.r_[unit(axes[i, :2])[:2]*math.sqrt(1-az*az), az]
            diff = np.cross(axes[i], axis)
            change = math.atan2(np.linalg.norm(diff), np.dot(axes[i], axis))
            rr = Rotation.from_rotvec(unit(diff)*change).as_matrix() @ bases[i] @ rotation(z=math.degrees(rolls[i]))
            for turn in [0, -5, 5, -10, 10, -20, 20, -35, 35, -55, 55, -75, 75]:
                yaw = expected_yaw+math.radians(turn)
                if abs(yaw) > math.radians(165):
                    continue
                chest_r = rotation(x=math.degrees(leans[i]), z=math.degrees(yaw))
                separation = rr[:, 2]*1.75*rig.scale
                reach = np.linalg.norm(chest_r @ [1., 0., 0.]-separation)
                if reach > 1.744:
                    continue
                cost = (turn/40)**2+level*9
                if poses:
                    previous_yaw = adaptations[-1]['torso_yaw_degrees']
                    cost += ((math.degrees(yaw)-previous_yaw)/18)**2*.7
                candidates.append((cost, rr, chest_r, yaw, level))
        candidates.sort(key=lambda c: c[0])
        previous_grasp = rig.previous_grasp
        solved, errors = None, []
        for _, rr, chest_r, yaw, level in candidates:
            body_r = rotation(x=math.degrees(leans[i])*.42, z=math.degrees(yaw)*.43)
            pose['Body'] = affine(body_r @ rig.rest['Body'][:3, :3], origin)
            chest = origin+body_r @ [0., 0., .6]
            pose['Chest'] = affine(chest_r @ rig.rest['Chest'][:3, :3], chest)
            pose['Head'] = affine(rotation(x=6., z=math.degrees(yaw)*.2) @ rig.rest['Head'][:3, :3], chest+chest_r @ [0, 0, .6])
            rsh = chest+chest_r @ [-.5, -.3, .4]
            lsh = chest+chest_r @ [.5, -.3, .4]
            separation = rr[:, 2]*1.75*rig.scale
            hand_offset = np.array([captured_hands[i][0]*.20, -.53, .22+captured_hands[i][2]*.20])
            center = chest+chest_r @ hand_offset
            wanted = center-separation*.5
            rig.previous_grasp = previous_grasp
            try:
                hand = rig.fit_weapon(pose, rr, wanted, rsh, lsh, -3., -1.25, i)
            except RuntimeError as error:
                errors.append(str(error))
                continue
            solved = (rr, chest_r, hand, separation, yaw, level)
            break
        if solved is None:
            write(work/'wide_failure.json', {'frame': i, 'reference': reference, 'axis': axes[i].tolist(),
                  'expected_yaw': math.degrees(expected_yaw), 'previous': adaptations[-3:], 'errors': errors[-8:]})
            raise RuntimeError(('No feasible wide grip', i, reference, errors[-3:]))
        rr, chest_r, hand, separation, yaw, level = solved
        rig.current_frame = i
        for side, target in [('Right', hand), ('Left', hand+separation)]:
            # Captured elbow elevation guides the continuous bend pole.
            hint = pose['Chest'][:3, 3]+raw[i][f'Arm:{side}:Lower'][:3, 3]-raw[i]['Chest'][:3, 3]
            palm = rig.arm(pose, side, target, chest_r, hint)
            assert np.linalg.norm(palm-target) < 1e-5
        pose['Weapon:Scythe'] = affine(rr*rig.scale, hand+rr[:, 2]*3*rig.scale)
        poses.append(pose)
        adaptations.append({'frame': i+1, 'reference_frame': reference+1,
                            'torso_yaw_degrees': math.degrees(yaw), 'elevation_clearance_correction': level})
        if i % 20 == 0:
            print('WIDE_RETARGET', i, '/', count, 'torso', round(math.degrees(yaw), 1), 'elevation', level, flush=True)
    stabilize_limb_roll(poses)
    metrics = []
    for segment, before in zip(segments, original['attack_segments']):
        a, b = segment['first_frame']-1, segment['last_frame']
        old_axes = np.array([unit(p['Weapon:Scythe'][:3, 2]) for p in old[before['first_frame']-1:before['last_frame']]])
        new_axes = np.array([unit(p['Weapon:Scythe'][:3, 2]) for p in poses[a:b]])
        metrics.append({'name': segment['name'], 'before_span_degrees': span(old_axes), 'after_span_degrees': span(new_axes)})
    notes = ('Fresh Google MediaPipe Heavy on 0.5x footage; image-reviewed continuous attack planes replace ambiguous motion-blur reversals, '
             'unrestricted rear wind-up, coordinated torso rotation and two-hand IK. Left hand at shaft middle, right at tail. '
             'Retimed captured steps retain floor contacts. Shaft depth and amplitude are authored monocular adaptations.')
    data.update(action='HB_Scythe_MediaPipe_WideSwing_06', frames=list(range(1, count+1)),
                poses=[{n: m.tolist() for n, m in row.items()} for row in poses],
                attack_segments=segments, footwork=feet, duration_seconds=(count-1)/60,
                grip_local_z=[-3.]*count, left_grip_local_z=[-1.25]*count, support_hand_weight=[1.]*count,
                release_interval_source_frames=[], method=notes,
                markers=[{'frame': s['first_frame'], 'name': s['label']} for s in segments])
    data['combat_cleanup'] = dict(version=VERSION, notes=notes, sweep_scale=args.sweep_scale, swing_edits=SWING_EDITS,
                                 reference_playback_rate=.5, capture_work=str(work),
                                 capture_sha256=hashlib.sha256((work/'capture_selected.npz').read_bytes()).hexdigest(),
                                 previous_support_edit='mediapipe_recapture_05')
    path = out/'capture/target_motion.json'
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, allow_nan=False, separators=(',', ':'))+'\n', encoding='utf-8')
    write(out/'capture/wide_swing_report.json', {'version': VERSION, 'source_baseline_sha256': hashlib.sha256(base_path.read_bytes()).hexdigest(),
          'fresh_capture': load(work/'capture_report.json'), 'frames': count, 'duration_seconds': (count-1)/60,
          'sweep_scale': args.sweep_scale, 'swing_edits': SWING_EDITS, 'attack_metrics': metrics, 'adaptations': adaptations,
          'grip_records': rig.grasp_records, 'motion_sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
    print('WIDE_RETARGET_COMPLETE', json.dumps(metrics), flush=True)


if __name__ == '__main__':
    main()
