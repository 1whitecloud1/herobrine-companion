"""Check both hand contacts and frame the actual geometry for combat review."""
import json
from pathlib import Path
import numpy as np
from scipy.ndimage import gaussian_filter1d, maximum_filter1d
from scipy.spatial.transform import Rotation
from combat_cleanup import CombatRig, PROFILE, unit

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_mocap'
data = json.loads((WORK/'target_motion.json').read_text(encoding='utf-8'))
profile = json.loads(PROFILE.read_text(encoding='utf-8'))
rig = CombatRig(profile, data)
rw = np.asarray(profile['rig_world'])
poses = [{n: np.asarray(m) for n, m in row.items()} for row in data['poses']]
weights = np.asarray(data['combat_cleanup']['authored_weights'])
active = weights > .999999
right_errors, left_errors, contacts, reaches = [], [], [], []
boxes = [('Body', [0., .3, 0.], [.4, .3, .2]),
         ('Chest', [0., .3, 0.], [.4, .3, .2]),
         ('Head', [0., .4, 0.], [.4, .4, .4])]
z_axis = unit([8., -12., 3.2])
x_axis = unit(np.cross([0., 0., 1.], z_axis))
y_axis = unit(np.cross(z_axis, x_axis))
camera_basis = np.column_stack([x_axis, y_axis, z_axis])
bounds = []
for f, pose in enumerate(poses):
    w = pose['Weapon:Scythe']
    points = rig.weapon @ w[:3, :3].T+w[:3, 3]
    char = rig.skin(pose)
    world = np.c_[np.vstack([char, points]), np.ones(len(char)+len(points))] @ rw.T
    projected = world[:, :3] @ camera_basis
    bounds.append([projected[:, :2].min(axis=0), projected[:, :2].max(axis=0)])
    if not active[f]:
        continue
    for side, grip, values in [('Right', data['grip_local_z'][f], right_errors),
                               ('Left', data['left_grip_local_z'][f], left_errors)]:
        lower = pose[f'Arm:{side}:Lower']
        palm = lower[:3, 3]+lower[:3, 1]*.48
        contact = (w @ np.array([0., 0., grip, 1.]))[:3]
        values.append(float(np.linalg.norm(contact-palm)))
    rod = np.c_[np.zeros(110), np.zeros(110), np.linspace(-3.34, 1.69, 110)]
    samples = np.vstack([points, rod@w[:3, :3].T+w[:3, 3]])
    for name, center, extent in boxes:
        local = (samples-pose[name][:3, 3]) @ pose[name][:3, :3]-center
        q = np.abs(local)-extent
        sdf = np.linalg.norm(np.maximum(q, 0.), axis=1)+np.minimum(q.max(axis=1), 0.)
        if sdf.min() < -.003:
            contacts.append({'frame': f+1, 'bone': name, 'depth': float(-sdf.min())})
    if f in [6, 13, 16, 21, 94, 135, 211, 246, 442]:
        head_mount = (w@np.array([0., 0., 1.69, 1.]))[:3]
        reaches.append({'frame': f+1,
                        'blade_forward_from_chest_rig_units': float(pose['Chest'][1, 3]-head_mount[1]),
                        'left_grip': data['left_grip_local_z'][f], 'right_grip': data['grip_local_z'][f]})
continuity = {}
for n in poses[0]:
    if n == 'Weapon:Scythe':
        continue
    rotations = Rotation.from_matrix(np.array([pose[n][:3, :3] for pose in poses]))
    delta = np.degrees((rotations[1:]*rotations[:-1].inv()).magnitude())
    valid = active[1:] & active[:-1]
    continuity[n] = {'ground_attack_maximum_degrees_per_frame': float(delta[valid].max()),
                     'ground_attack_p95': float(np.quantile(delta[valid], .95))}
report = {'version': data['combat_cleanup']['version'], 'authored_ground_frames': int(active.sum()),
          'right_tail_grip_max_error': max(right_errors), 'left_middle_grip_max_error': max(left_errors),
          'weapon_torso_or_head_contacts_in_ground_attacks': contacts,
          'reach_samples': reaches, 'ground_continuity': continuity,
          'note': 'Contacts use torso/head boxes, actual blade vertices and sampled shaft. This is not a complete deforming-mesh self-collision test.'}
assert report['right_tail_grip_max_error'] < 1e-4, report
assert report['left_middle_grip_max_error'] < 1e-4, report
assert not contacts, contacts
(WORK/'combat_qa.json').write_text(json.dumps(report, indent=2), encoding='utf-8')
bb = np.asarray(bounds)
half = (bb[:, 1]-bb[:, 0])*.5
required = np.maximum(half[:, 0]*2., half[:, 1]*2.*16/9)*1.16
width = np.maximum(5.3, required)
width = np.maximum(width, gaussian_filter1d(maximum_filter1d(width, size=19), 5))
center = gaussian_filter1d(bb.mean(axis=1), 10, axis=0, mode='nearest')
visible = np.c_[width/2., width/2.*9/16]*.95
center = np.minimum(np.maximum(center, bb[:, 1]-visible), bb[:, 0]+visible)
targets = center[:, 0, None]*x_axis+center[:, 1, None]*y_axis
framing = {'direction': z_axis.tolist(), 'camera_target': targets.tolist(),
           'width_per_frame': width.tolist(), 'basis': camera_basis.tolist()}
(WORK/'combat_camera_framing.json').write_text(json.dumps(framing), encoding='utf-8')
print('COMBAT_QA', json.dumps({k: v for k, v in report.items() if k not in ['ground_continuity', 'reach_samples']}))
print('COMBAT_CAMERA_WIDTH', float(width.min()), float(width.max()))
