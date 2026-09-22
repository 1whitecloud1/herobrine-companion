"""Convert the reviewed two-hand MediaPipe bake to Epic Fight's biped clips.

Blender --background --factory-startup --python-exit-code 1 --python this.py
    -- FINAL_BLEND SOURCE_PROFILE
The Blender file, capture and existing V6 resources are read only.
"""
import hashlib
import json
import math
import sys
import zipfile
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix, Vector

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export_v4 import ASSETS, C, MAPPING, REPO, SCALE, affine, load, rotation, weapon_calibration, write

OUT = REPO / 'build/epicfight-mediapipe'
DEST = ASSETS / 'animmodels/animations/player/poem_mediapipe'
HERO_DEST = ASSETS / 'animmodels/animations/hero/poem_mediapipe'
# The block arms and item pivot need subframe keys during the fastest cuts.
# Continuous IK below removes pole flips before this export sampling is applied.
HZ = 960


def main():
    global OUT
    args = sys.argv[sys.argv.index('--') + 1:]
    blend, profile_path = map(Path, args)
    motion_path = blend.parent / 'capture/target_motion.json'
    motion, profile = load(motion_path), load(profile_path)
    aerial = motion['combat_cleanup']['version'] == 'mediapipe_aerial_dash_08'
    assert aerial or motion['combat_cleanup']['version'] in ('mediapipe_recapture_05', 'mediapipe_wide_swing_06', 'mediapipe_turning_07')
    if aerial:
        OUT = REPO/'build/epicfight-aerial-dash'
    assert not motion['release_interval_source_frames']
    assert motion['frames'] == list(range(1, len(motion['poses']) + 1))
    fps = motion['fps'] / motion['fps_base']
    assert HZ % fps == 0
    substeps = int(HZ / fps)
    attacks = motion['attack_segments']
    assert len(attacks) == (1 if aerial else 8) and min(motion['support_hand_weight']) == 1
    OUT.mkdir(parents=True, exist_ok=True)
    bpy.ops.wm.open_mainfile(filepath=str(blend))
    baked_action = bpy.data.objects['Bones:Character'].animation_data.action
    assert baked_action.get('motion_sha256') == hashlib.sha256(motion_path.read_bytes()).hexdigest(), 'Rebake the edited source before export'
    weapon_mesh = bpy.data.objects['Poem of the End | 原版镰刀'].data
    weapon_vertices = np.array([v.co[:] for v in weapon_mesh.vertices]) * SCALE
    source_weapon = {
        'source_weapon_vertices': weapon_vertices.tolist(),
        'source_weapon_faces': [list(p.vertices) for p in weapon_mesh.polygons],
        'source_weapon_uv': [uv.uv[:] for uv in weapon_mesh.uv_layers.active.data],
    }
    jar = next((REPO / '.gradle-home/caches/modules-2/files-2.1/curse.maven/epic-fight-mod-405076/8080214').rglob('*.jar'))
    with zipfile.ZipFile(jar) as z:
        (OUT / 'biped.json').write_bytes(z.read('assets/epicfight/animmodels/entity/biped.json'))
    mesh = load(OUT / 'biped.json')
    local, rest, parents = {}, {}, {}

    def walk(nodes, parent=None):
        for node in nodes:
            n = node['name']
            local[n] = np.array(node['transform']).reshape(4, 4)
            parents[n] = parent
            rest[n] = (rest[parent] if parent else np.eye(4)) @ local[n]
            walk(node.get('children', []), n)
    walk(mesh['armature']['hierarchy'])
    names = list(rest)
    assert len(names) == 20
    # The Hero armature adds weapon bones but has identical biped bind matrices.
    hero_nodes = {}
    def collect(nodes):
        for node in nodes:
            hero_nodes[node['name']] = np.array(node['transform']).reshape(4, 4)
            collect(node.get('children', []))
    collect(load(ASSETS / 'animmodels/entity/hero_biped_nightfall.json')['armature']['hierarchy'])
    assert all(np.allclose(local[n], hero_nodes[n], atol=1e-7) for n in names)
    neutral = {n: rotation(m) for n, m in rest.items()}
    for side in ('R', 'L'):
        neutral['Hand_' + side] = neutral['Arm_' + side]
    source_rest = {n: np.array(info['rest']) for n, info in profile['bones'].items()}
    source_rot = {n: rotation(source_rest[s]) for n, s in MAPPING.items()}
    source = [{n: np.array(v) for n, v in row.items()} for row in motion['poses']]
    quaternions = {n: [Matrix(rotation(row[n])).to_quaternion() for row in source] for n in source[0]}
    raw_grips = {}
    for side in ('Right', 'Left'):
        raw_grips[side] = np.array([
            np.linalg.solve(row['Weapon:Scythe'], row['Arm:' + side + ':Lower'] @ [0, .48, 0, 1])[:3]
            for row in source
        ])
    # Root advances along source -Y. The first pelvis rotation is the captured
    # wind-up, not a change of attack heading; keep that body turn in the pose
    # without rotating the whole movement track away from game forward.
    axes = C.copy()
    correction, weapon_report = weapon_calibration(profile_path.parent)
    v = mesh['vertices']
    points = np.array(v['positions']['array']).reshape(-1, 3)
    influences, cursor = {n: [] for n in names}, 0
    for i, count in enumerate(v['vcounts']['array']):
        for _ in range(count):
            j, wi = v['vindices']['array'][cursor:cursor + 2]
            cursor += 2
            influences[mesh['armature']['joints'][j]].append((i, v['weights']['array'][wi]))
    groups = {n: (np.array([i for i, w in g]), np.array([w for i, w in g])) for n, g in influences.items() if g}
    inv_rest = {n: np.linalg.inv(m) for n, m in rest.items()}
    foot_points, foot_centers = {}, {}
    for side in ('R', 'L'):
        joint = 'Leg_' + side
        ix, weights = groups[joint]
        bottom = points[ix[(weights > .99) & (points[ix, 2] < .04)]]
        assert len(bottom) >= 4
        foot_points[side] = (np.c_[bottom, np.ones(len(bottom))] @ inv_rest[joint].T)[:, :3]
        foot_centers[side] = foot_points[side].mean(axis=0)

    def skin(world):
        result = np.zeros_like(points)
        for n, (ix, weights) in groups.items():
            result[ix] += (np.c_[points[ix], np.ones(len(ix))] @ (world[n] @ inv_rest[n]).T)[:, :3] * weights[:, None]
        return result

    def palm(world, side):
        return (world['Hand_' + side] @ np.r_[local['Tool_' + side][:3, 3], 1])[:3]

    previous_bends, previous_hinges = {}, {}
    previous_leg_bends, previous_leg_hinges = {}, {}

    def arm_basis(direction, hinge):
        y = direction / np.linalg.norm(direction)
        x = hinge - y * np.dot(hinge, y)
        x /= np.linalg.norm(x)
        z = np.cross(x, y)
        return np.column_stack((np.cross(y, z), y, z))

    def solve_arm(world, side, goal, hint):
        upper, lower = 'Arm_' + side, 'Hand_' + side
        a, b, c = world[upper][:3, 3].copy(), world[lower][:3, 3].copy(), palm(world, side)
        l1, l2 = np.linalg.norm(b - a), np.linalg.norm(c - b)
        delta = goal - a
        reach = np.linalg.norm(delta)
        assert abs(l1 - l2) + 1e-6 < reach < l1 + l2, (side, reach, l1, l2)
        axis = delta / reach
        bend = hint - a - axis * np.dot(hint - a, axis)
        if np.linalg.norm(bend) < 1e-7:
            bend = b - a - axis * np.dot(b - a, axis)
        if np.linalg.norm(bend) < 1e-7:
            bend = np.cross(axis, [0., 0., 1.])
        bend /= np.linalg.norm(bend)
        if side in previous_bends:
            previous, previous_axis = previous_bends[side]
            transport = np.array(Vector(previous_axis).rotation_difference(Vector(axis)).to_matrix())
            previous = transport @ previous
            previous -= axis * np.dot(previous, axis)
            previous /= np.linalg.norm(previous)
            if np.dot(bend, previous) < 0:
                bend = -bend
            # An almost straight captured arm has an unreliable elbow pole.
            # Transport the last pole with the reach direction, then turn it
            # toward the captured hint with a short relaxation and a bounded
            # roll speed. This does not filter the blade or either hand target.
            angle = math.atan2(np.dot(axis, np.cross(previous, bend)), np.dot(previous, bend))
            angle *= 1 - math.exp(-1 / (HZ * .018))
            angle = np.clip(angle, -math.radians(900) / HZ, math.radians(900) / HZ)
            bend = previous * math.cos(angle) + np.cross(axis, previous) * math.sin(angle)
        previous_bends[side] = (bend.copy(), axis.copy())
        along = (l1 * l1 - l2 * l2 + reach * reach) / (2 * reach)
        elbow = a + axis * along + bend * math.sqrt(max(0., l1 * l1 - along * along))
        # Both EF arm segments point along local +Y. Build their orientations
        # from the same continuous elbow hinge. Applying shortest-arc rotations
        # separately to the captured segments introduces roll singularities
        # when a source direction is opposite the solved direction; local TRS
        # interpolation then pulls the long item pivot away from both hands.
        hinge = np.cross(bend, axis)
        reference = previous_hinges.get(side, world[upper][:3, 0])
        if np.dot(hinge, reference) < 0:
            hinge = -hinge
        previous_hinges[side] = hinge.copy()
        for bone, direction, child in ((upper, elbow - a, lower),
                                       (lower, goal - elbow, 'Tool_' + side)):
            bind_basis = arm_basis(local[child][:3, 3], np.array([1., 0., 0.]))
            world[bone][:3, :3] = arm_basis(direction, hinge) @ bind_basis.T
        world[lower][:3, 3] = elbow

    def solve_leg(world, side, goal, hint, lift):
        """Keep the actual block sole planted after the biped retarget."""
        upper, lower = 'Thigh_' + side, 'Leg_' + side
        a, b = world[upper][:3, 3].copy(), world[lower][:3, 3].copy()
        c = (world[lower] @ np.r_[foot_centers[side], 1])[:3]
        ru0, rl0 = world[upper][:3, :3].copy(), world[lower][:3, :3].copy()
        l1, l2 = np.linalg.norm(b-a), np.linalg.norm(c-b)
        if aerial or motion['combat_cleanup']['version'] == 'mediapipe_turning_07':
            # Shortest-arc rotations of each leg separately flip at a deeply
            # folded knee. Solve both segments in one continuous bend plane,
            # and keep the knee cube clear without raising the planted sole.
            old = previous_leg_bends.get(side)
            previous_hinge = previous_leg_hinges.get(side, world[upper][:3, 0])
            for _ in range(48):
                delta=goal-a
                reach=np.linalg.norm(delta)
                assert abs(l1-l2)+1e-6 < reach < l1+l2, (side,sample,reach,l1+l2)
                axis=delta/reach
                bend=hint-a-axis*np.dot(hint-a,axis)
                if np.linalg.norm(bend)<1e-7:
                    bend=np.cross(axis,[1.,0.,0.])
                bend/=np.linalg.norm(bend)
                if old is not None:
                    prior,prior_axis=old
                    transport=np.array(Vector(prior_axis).rotation_difference(Vector(axis)).to_matrix())
                    prior=transport @ prior
                    prior-=axis*np.dot(prior,axis)
                    prior/=np.linalg.norm(prior)
                    angle=math.atan2(np.dot(axis,np.cross(prior,bend)),np.dot(prior,bend))
                    angle*=1-math.exp(-1/(HZ*.012))
                    angle=np.clip(angle,-math.radians(1440)/HZ,math.radians(1440)/HZ)
                    bend=prior*math.cos(angle)+np.cross(axis,prior)*math.sin(angle)
                along=(l1*l1-l2*l2+reach*reach)/(2*reach)
                radius=math.sqrt(max(0.,l1*l1-along*along))
                up=np.array([0.,0.,1.])-axis*axis[2]
                if np.linalg.norm(up)>1e-7 and radius>1e-7:
                    up/=np.linalg.norm(up)
                    required=((.23 if aerial else .195)-a[2]-axis[2]*along)/(radius*up[2])
                    if required>-1:
                        limit=math.acos(np.clip(required,-1,1))
                        angle=math.atan2(np.dot(axis,np.cross(up,bend)),np.dot(up,bend))
                        angle=np.clip(angle,-limit,limit)
                        bend=up*math.cos(angle)+np.cross(axis,up)*math.sin(angle)
                knee=a+axis*along+bend*radius
                hinge=np.cross(bend,axis)
                if np.dot(hinge,previous_hinge)<0:
                    hinge=-hinge
                world[upper][:3,:3]=arm_basis(knee-a,hinge) @ arm_basis(local[lower][:3,3],np.array([1.,0.,0.])).T
                world[lower][:3,:3]=arm_basis(goal-knee,hinge) @ arm_basis(foot_centers[side],np.array([1.,0.,0.])).T
                world[lower][:3,3]=knee
                offsets=(foot_points[side]-foot_centers[side]) @ world[lower][:3,:3].T
                next_z=.012+lift-float(offsets[:,2].min())
                if abs(next_z-goal[2])<1e-8:
                    break
                goal[2]+=.7*(next_z-goal[2])
            previous_leg_bends[side]=(bend.copy(),axis.copy())
            previous_leg_hinges[side]=hinge.copy()
            return
        for _ in range(24):
            delta = goal-a
            reach = np.linalg.norm(delta)
            assert abs(l1-l2)+1e-6 < reach < l1+l2, (side, sample, reach, l1+l2)
            axis = delta/reach
            bend = hint-a-axis*np.dot(hint-a, axis)
            bend /= np.linalg.norm(bend)
            along = (l1*l1-l2*l2+reach*reach)/(2*reach)
            knee = a+axis*along+bend*math.sqrt(max(0., l1*l1-along*along))
            ru = np.array(Vector(b-a).rotation_difference(Vector(knee-a)).to_matrix())
            rl = np.array(Vector(c-b).rotation_difference(Vector(goal-knee)).to_matrix())
            world[upper][:3, :3] = ru @ ru0
            world[lower][:3, :3] = rl @ rl0
            world[lower][:3, 3] = knee
            offsets = (foot_points[side]-foot_centers[side]) @ world[lower][:3, :3].T
            next_z = .012+lift-float(offsets[:, 2].min())
            if abs(next_z-goal[2]) < 1e-7:
                break
            goal[2] = next_z

    def clear_airborne_sole(world, side):
        """Match the larger EF sole during takeoff/landing without lifting the torso."""
        upper,lower='Thigh_'+side,'Leg_'+side
        a=world[upper][:3,3].copy()
        b=world[lower][:3,3].copy()
        c=(world[lower] @ np.r_[foot_centers[side],1])[:3]
        goal=c.copy()
        l1=np.linalg.norm(local[lower][:3,3]);l2=np.linalg.norm(foot_centers[side])
        hinge_hint=world[upper][:3,0].copy()
        for _ in range(40):
            minimum=float((np.c_[foot_points[side],np.ones(len(foot_points[side]))] @ world[lower].T)[:,2].min())
            needed=.014-minimum
            if needed<1e-8:
                return
            goal[2]+=needed*1.01
            delta=goal-a;reach=np.linalg.norm(delta);axis=delta/reach
            reach=np.clip(reach,abs(l1-l2)+.0001,l1+l2-.0001)
            goal=a+axis*reach
            bend=b-a-axis*np.dot(b-a,axis)
            if np.linalg.norm(bend)<1e-8:
                bend=np.cross(axis,hinge_hint)
            bend/=np.linalg.norm(bend)
            along=(l1*l1-l2*l2+reach*reach)/(2*reach)
            knee=a+axis*along+bend*math.sqrt(max(0.,l1*l1-along*along))
            hinge=np.cross(bend,axis)
            if np.dot(hinge,hinge_hint)<0:hinge=-hinge
            world[upper][:3,:3]=arm_basis(knee-a,hinge) @ arm_basis(local[lower][:3,3],np.array([1.,0.,0.])).T
            world[lower][:3,:3]=arm_basis(goal-knee,hinge) @ arm_basis(foot_centers[side],np.array([1.,0.,0.])).T
            world[lower][:3,3]=knee

    worlds, lifts, grip_rows, left_grip_rows, support_weights, errors, adjustments, foot_rows = [], [], [], [], [], [], [], []
    times = np.arange((len(source) - 1) * substeps + 1) / HZ
    root_start = source[0]['Bone.011'][:3, 3].copy()
    root_start[2] = 0
    for sample, time in enumerate(times):
        f = sample / substeps
        i, j, u = int(f), min(int(f) + 1, len(source) - 1), f % 1
        row = {}
        for n in source[0]:
            r = np.array(quaternions[n][i].slerp(quaternions[n][j], u).to_matrix())
            if n == 'Weapon:Scythe':
                r *= motion['weapon_scale']
            row[n] = affine(r, source[i][n][:3, 3] * (1 - u) + source[j][n][:3, 3] * u)
        grips = {side: raw_grips[side][i] * (1 - u) + raw_grips[side][j] * u for side in raw_grips}
        # Interpolate the driving grip, not the distant mesh origin. Otherwise
        # a rapid shaft rotation makes the right palm orbit the item pivot in
        # between two valid captures, even crossing the shoulder's IK center.
        anchor_i = (source[i]['Weapon:Scythe'] @ np.r_[raw_grips['Right'][i], 1])[:3]
        anchor_j = (source[j]['Weapon:Scythe'] @ np.r_[raw_grips['Right'][j], 1])[:3]
        row['Weapon:Scythe'][:3, 3] = (anchor_i * (1 - u) + anchor_j * u
                                     - row['Weapon:Scythe'][:3, :3] @ grips['Right'])
        source_palms = {side: (row['Weapon:Scythe'] @ np.r_[g, 1])[:3] for side, g in grips.items()}
        world = {}
        for n in names:
            pw = world[parents[n]] if parents[n] else np.eye(4)
            m = pw @ local[n]
            if n in MAPPING:
                m[:3, :3] = axes @ rotation(row[MAPPING[n]]) @ source_rot[n].T @ C.T @ neutral[n]
                if n == 'Root':
                    m[:3, 3] = rest[n][:3, 3] + axes @ (row['Bone.011'][:3, 3] - root_start) * SCALE
            elif n.startswith('Shoulder_'):
                side = 'Right' if n.endswith('R') else 'Left'
                sn = 'Arm:' + side + ':Upper'
                anchor = world['Chest'][:3, 3] + axes @ (row[sn][:3, 3] - row['Chest'][:3, 3]) * SCALE
                # Preserve the delivered shoulder protraction/spacing. Keeping
                # the wider EF bind anchors would make the rigid two-hand grip
                # unreachable even though the two arm lengths are sufficient.
                m[:3, 3] = anchor - m[:3, :3] @ local['Arm_' + n[-1]][:3, 3]
            world[n] = m
        feet = {}
        for s, side, short in [('R', 'Right', 'r'), ('L', 'Left', 'l')]:
            def foot_value(key):
                return motion['footwork'][i][key]*(1-u)+motion['footwork'][j][key]*u
            point = np.array([foot_value('foot_'+short+'x'), foot_value('foot_'+short+'y'), 0.])
            goal = rest['Root'][:3, 3] + axes @ (point-root_start)*SCALE
            lift = foot_value('foot_'+short+'_lift')*SCALE
            goal[2] = .07+lift
            hint = world['Root'][:3, 3] + axes @ (row['Leg:'+side+':Lower'][:3, 3]-row['Bone.011'][:3, 3])*SCALE
            flying = aerial and foot_value('aerial_weight') > 1e-8
            if not flying:
                solve_leg(world, s, goal.copy(), hint, lift)
            else:
                clear_airborne_sole(world,s)
            feet[s] = {'xy':goal[:2].tolist(), 'lift':lift, 'planted':not flying and lift < 1e-9}
        foot_rows.append(feet)
        wanted = {s: world['Chest'][:3, 3] + axes @ (source_palms[side] - row['Chest'][:3, 3]) * SCALE
                  for s, side in [('R', 'Right'), ('L', 'Left')]}
        # A shared translation fits both grips to the game arms' reach spheres.
        # Independent IK clamping would detach a hand from the rigid shaft.
        shift = np.zeros(3)
        centers = {s: world['Arm_' + s][:3, 3] - wanted[s] for s in ('R', 'L')}
        radii = {s: np.linalg.norm(local['Hand_' + s][:3, 3]) + np.linalg.norm(local['Tool_' + s][:3, 3]) - .0002
                 for s in ('R', 'L')}
        if any(np.linalg.norm(centers[s]) > radii[s] for s in centers):
            candidates = [centers[s] * (1 - radii[s] / np.linalg.norm(centers[s])) for s in centers]
            valid = [x for x in candidates if all(np.linalg.norm(x - centers[s]) <= radii[s] + 1e-9 for s in centers)]
            if valid:
                shift = min(valid, key=np.linalg.norm)
            else:
                delta = centers['L'] - centers['R']
                distance = np.linalg.norm(delta)
                assert abs(radii['R'] - radii['L']) < distance < radii['R'] + radii['L'], (sample, distance, radii)
                axis = delta / distance
                along = (radii['R'] ** 2 - radii['L'] ** 2 + distance ** 2) / (2 * distance)
                center = centers['R'] + along * axis
                radius = math.sqrt(max(0., radii['R'] ** 2 - along ** 2))
                toward_origin = -center + axis * np.dot(center, axis)
                if np.linalg.norm(toward_origin) < 1e-8:
                    toward_origin = np.cross(axis, [0., 0., 1.])
                shift = center + toward_origin / np.linalg.norm(toward_origin) * radius
        # Preserve sole plants: resolve blade clearance in the shared two-hand
        # solve instead of lifting the entire character away from the floor.
        base_socket = affine(axes @ row['Weapon:Scythe'][:3, :3],
                             wanted['R'] + axes @ (row['Weapon:Scythe'][:3, 3]-source_palms['Right'])*SCALE)
        floor_shift = .015-float((np.c_[weapon_vertices, np.ones(len(weapon_vertices))] @ base_socket.T)[:, 2].min())
        # Also keep a small clearance from the arm's folded singularity.
        for _ in range(300):
            correction_size = 0.
            needed = max(0., floor_shift-shift[2])
            shift[2] += needed
            correction_size = max(correction_size, needed)
            for s in ('R', 'L'):
                a = world['Arm_' + s][:3, 3]
                distance = np.linalg.norm(wanted[s] + shift - a)
                l1, l2 = np.linalg.norm(local['Hand_' + s][:3, 3]), np.linalg.norm(local['Tool_' + s][:3, 3])
                limit = np.clip(distance, abs(l1 - l2) + .0001, l1 + l2 - .0001)
                delta = (a - wanted[s] - shift) * (1 - limit / max(distance, 1e-9))
                shift += delta
                correction_size = max(correction_size, float(np.linalg.norm(delta)))
            if correction_size < 1e-8:
                break
        adjustments.append(float(np.linalg.norm(shift)))
        for s, side in [('R', 'Right'), ('L', 'Left')]:
            hint = world['Chest'][:3, 3] + axes @ (row['Arm:' + side + ':Lower'][:3, 3] - row['Chest'][:3, 3]) * SCALE + shift
            solve_arm(world, s, wanted[s] + shift, hint)
        for n in names:
            if n.startswith(('Elbow_', 'Knee_')):
                side = n[-1]
                upper = ('Arm_' if n.startswith('Elbow') else 'Thigh_') + side
                lower = ('Hand_' if n.startswith('Elbow') else 'Leg_') + side
                q1, q2 = [Matrix(world[b][:3, :3]).to_quaternion() for b in (upper, lower)]
                world[n][:3, :3] = np.array(q1.slerp(q2, .5).to_matrix()) @ neutral[upper].T @ neutral[n]
                world[n][:3, 3] = world[lower][:3, 3]
        rp = source_palms['Right']
        socket = affine(axes @ row['Weapon:Scythe'][:3, :3], palm(world, 'R') + axes @ (row['Weapon:Scythe'][:3, 3] - rp) * SCALE)
        world['Tool_R'] = socket @ correction
        world['Tool_L'] = world['Hand_L'] @ local['Tool_L']
        errors.append(float(np.linalg.norm(palm(world, 'L') - (socket @ np.r_[grips['Left'] * SCALE, 1])[:3])))
        wp = (np.c_[weapon_vertices, np.ones(len(weapon_vertices))] @ socket.T)[:, :3]
        lifts.append(max(0., .008 - min(float(skin(world)[:, 2].min()), float(wp[:, 2].min()))))
        worlds.append(world)
        grip_rows.append((grips['Right'] * SCALE).tolist())
        left_grip_rows.append((grips['Left'] * SCALE).tolist())
        support_weights.append(motion['support_hand_weight'][i] * (1 - u) + motion['support_hand_weight'][j] * u)
    lifts = np.array(lifts)
    radius, smooth = round(HZ * .05), round(HZ * .025)
    envelope = np.array([max(lifts[max(0, i - radius):i + radius + 1]) for i in range(len(lifts))])
    envelope = np.maximum(lifts, np.convolve(np.pad(envelope, (smooth, smooth), mode='edge'),
                                          np.ones(2 * smooth + 1) / (2 * smooth + 1), mode='valid'))
    sampled = []
    for i, world in enumerate(worlds):
        for m in world.values():
            m[2, 3] += envelope[i]
        sampled.append({n: np.linalg.inv(world[parents[n]] if parents[n] else np.eye(4)) @ world[n] for n in names})

    # EF interpolates local translation/quaternion/scale. Test the intervals too:
    # a rotating blade can dip below both endpoint bounds during a low sweep.
    components = [{n: Matrix(np.linalg.inv(local[n]) @ row[n]).decompose() for n in names} for row in sampled]
    maximum_arm_step = max(math.degrees(2 * math.acos(min(1., abs(components[i][n][1].dot(components[i + 1][n][1])))))
                           for i in range(len(components) - 1)
                           for n in ('Arm_R', 'Hand_R', 'Arm_L', 'Hand_L'))
    assert maximum_arm_step < 20., ('Discontinuous arm IK', maximum_arm_step)
    # Keep a 120 Hz grid, retaining the finer samples around rapid arm/tool turns.
    # The long inverse item-pivot offset otherwise arcs away from the gripping
    # hand under local TRS interpolation even when both key poses are exact.
    stride = HZ // 120
    selected = set(range(0, len(sampled), stride))
    threshold = math.cos(math.radians(10) / 2)
    for a in range(0, len(sampled) - 1, stride):
        b = min(a + stride, len(sampled) - 1)
        if any(abs(components[a][n][1].dot(components[j][n][1])) < threshold
               for n in ('Root', 'Chest', 'Arm_R', 'Hand_R', 'Arm_L', 'Hand_L', 'Tool_R',
                         'Thigh_R', 'Leg_R', 'Thigh_L', 'Leg_L')
               for j in range(a + 1, b + 1)):
            selected.update(range(a, b + 1))
    selected = sorted(selected)
    extra_lift = np.zeros(len(sampled))
    weapon_h = np.c_[weapon_vertices, np.ones(len(weapon_vertices))]
    for i, j in zip(selected, selected[1:]):
        world = {}
        for n in names:
            t, q, s = components[i][n]
            t2, q2, s2 = components[j][n]
            m = local[n] @ np.array(Matrix.LocRotScale(t.lerp(t2, .5), q.slerp(q2, .5), s.lerp(s2, .5)))
            world[n] = (world[parents[n]] if parents[n] else np.eye(4)) @ m
        socket = world['Tool_R'] @ np.linalg.inv(correction)
        needed = max(0., .008 - min(float(skin(world)[:, 2].min()), float((weapon_h @ socket.T)[:, 2].min())))
        extra_lift[i:j + 1] = np.maximum(extra_lift[i:j + 1], needed)
    extra_lift = np.array([max(extra_lift[max(0, i - smooth):i + smooth + 1]) for i in range(len(extra_lift))])
    for i, row in enumerate(sampled):
        row['Root'][2, 3] += extra_lift[i]
    envelope += extra_lift
    index = {original: compact for compact, original in enumerate(selected)}
    times, envelope = times[selected], envelope[selected]
    sampled = [sampled[i] for i in selected]
    grip_rows, left_grip_rows = [[rows[i] for i in selected] for rows in (grip_rows, left_grip_rows)]
    support_weights = [support_weights[i] for i in selected]
    foot_rows = [foot_rows[i] for i in selected]

    def clip(first, last, static=False, upper_only=False, zero_travel=False):
        a, b = index[(first - 1) * substeps], index[(last - 1) * substeps]
        entries = []
        for n in names:
            if upper_only and (n == 'Root' or n.startswith(('Thigh_', 'Leg_', 'Knee_'))):
                continue
            matrices = np.array([x[n] for x in sampled[a:b + 1]])
            t = times[a:b + 1] - times[a]
            if n == 'Root':
                matrices[:, :2, 3] -= matrices[0, :2, 3] - rest[n][:2, 3]
                if zero_travel:
                    matrices[:, :2, 3] = rest[n][:2, 3]
            if static:
                matrices, t = np.stack([matrices[0], matrices[0]]), np.array([0., 1.])
            entries.append({'name': n, 'time': np.round(t, 6).tolist(), 'transform': np.round(matrices.reshape(-1, 16), 7).tolist()})
        return {'animation': entries}

    def timing(name, label, first, last, windows, recovery):
        return {'name': name, 'label': label, 'first_frame': first, 'last_frame': last,
                'start': (first - 1) / fps, 'duration': (last - first) / fps,
                'recovery': (recovery - first) / fps,
                'contacts': [{'start': (a - first) / fps, 'end': (b - first) / fps} for a, b in windows]}

    if aerial:
        attack = attacks[0]
        s = timing('dash',attack['label'],1,len(source),attack['contact_frames'],attack['recovery_frame'])
        s.update(aerial=True,full_body_turn=True,root_turn_degrees=attack['root_turn_degrees'],
                 travel_blocks=attack['travel_blocks'],lateral_blocks=0.,reference_frames=attack['reference_frames'],
                 vertical_coord_policy='positive_root_height',reference_playback_rate=.5)
        data = clip(1,len(source))
        write(DEST/'dash.json',data)
        timing_path = ASSETS/'epicfight/poem_mediapipe_timing.json'
        runtime = load(timing_path)
        assert runtime['combat_edit']=='mediapipe_turning_07' and len(runtime['segments'])==8
        runtime['specials'][0] = s
        runtime['dash_edit'] = motion['combat_cleanup']['version']
        write(timing_path,runtime,True)
        report = dict(action=motion['action'],source_sha256=hashlib.sha256(motion_path.read_bytes()).hexdigest(),
                      blend_sha256=hashlib.sha256(blend.read_bytes()).hexdigest(),samples=len(times),sample_hz=HZ,
                      duration_seconds=float(times[-1]),source_fps=fps,source_frames=len(source),
                      combat_edit=motion['combat_cleanup']['version'],joint_count=len(names),
                      axis_source_to_epicfight=axes.tolist(),weapon=weapon_report,weapon_scale=motion['weapon_scale'],
                      segments=[s],max_support_hand_error_blocks=max(errors),
                      max_shared_grip_translation_blocks=max(adjustments),
                      max_arm_rotation_per_substep_degrees=maximum_arm_step,
                      max_floor_correction_blocks=float(envelope.max()),floor_correction_blocks=envelope.tolist(),
                      clip_sha256=hashlib.sha256((DEST/'dash.json').read_bytes()).hexdigest())
        write(OUT/'conversion_report.json',report,True)
        write(OUT/'reference_samples.json',dict(source_weapon,source_grips_scaled=grip_rows,
              source_left_grips_scaled=left_grip_rows,support_weights=support_weights,foot_plants=foot_rows,
              foot_local_points={s:p.tolist() for s,p in foot_points.items()},
              foot_local_centers={s:p.tolist() for s,p in foot_centers.items()}))
        assert max(errors)<.0001,max(errors)
        print('AERIAL_DASH_EXPORT',json.dumps({k:report[k] for k in ('samples','duration_seconds',
              'max_support_hand_error_blocks','max_floor_correction_blocks')}),flush=True)
        return

    segments = []
    for attack in attacks:
        first, last = attack['first_frame'], attack['last_frame']
        s = timing(attack['name'], attack['label'], first, last, attack['contact_frames'], attack['recovery_frame'])
        s.update(travel_blocks=attack['travel_blocks'], lateral_blocks=attack.get('lateral_blocks',0.),
                 reference_frames=attack['reference_frames'])
        if 'full_body_turn' in attack:
            s.update(full_body_turn=attack['full_body_turn'], root_turn_degrees=attack['root_turn_degrees'])
        segments.append(s)
        data = clip(first, last)
        write(DEST / (s['name'] + '.json'), data)
        write(HERO_DEST / (s['name'] + '.json'), data)
    specials = []
    for name, attack in [('dash', attacks[4]), ('air', attacks[0])]:
        s = timing(name, attack['label'], attack['first_frame'], attack['last_frame'], attack['contact_frames'], attack['recovery_frame'])
        s['travel_blocks'] = attack['travel_blocks'] if name == 'dash' else 0.
        s['lateral_blocks'] = attack.get('lateral_blocks',0.) if name == 'dash' else 0.
        specials.append(s)
    for s in specials:
        write(DEST / (s['name'] + '.json'), clip(s['first_frame'], s['last_frame'], zero_travel=s['name'] == 'air'))
    for folder in (DEST, HERO_DEST):
        write(folder / 'full.json', clip(1, len(source)))
        write(folder / 'ready.json', clip(1, 1, static=True))
        write(folder / 'hold.json', clip(1, 1, static=True, upper_only=True))
        for name, layer in [('ready', 'BASE_LAYER'), ('hold', 'COMPOSITE_LAYER')]:
            data = {'layer': layer, 'priority': 'HIGH' if name == 'ready' else 'MIDDLE'}
            if name == 'hold':
                data['masks'] = [{'livingmotion': 'ALL', 'type': 'arms'}]
            write(folder / 'data' / (name + '.json'), data)
        # Remove only stale generated segments from the previous full capture.
        active = {s['name'] + '.json' for s in segments}
        for obsolete in folder.glob('combo_??.json'):
            if obsolete.name not in active:
                obsolete.unlink()
    runtime = {'source': motion['source_url'], 'duration': float(times[-1]), 'playback_speed': 1.,
               'combat_edit': motion['combat_cleanup']['version'], 'segments': segments, 'specials': specials}
    write(ASSETS / 'epicfight/poem_mediapipe_timing.json', runtime, True)
    report = {'action': motion['action'], 'source_sha256': hashlib.sha256(motion_path.read_bytes()).hexdigest(),
              'blend_sha256': hashlib.sha256(blend.read_bytes()).hexdigest(), 'samples': len(times), 'sample_hz': HZ,
              'duration_seconds': float(times[-1]), 'source_fps': fps, 'source_frames':len(source),
              'combat_edit': motion['combat_cleanup']['version'], 'joint_count': len(names), 'axis_source_to_epicfight': axes.tolist(),
              'weapon': weapon_report, 'weapon_scale': motion['weapon_scale'], 'segments': segments, 'specials': specials,
              'release_interval_source_frames': motion['release_interval_source_frames'],
              'max_support_hand_error_blocks': max(errors), 'max_shared_grip_translation_blocks': max(adjustments),
              'max_arm_rotation_per_substep_degrees': maximum_arm_step,
              'elbow_pole_speed_limit_degrees_per_second': 900., 'weapon_interpolation_anchor': 'right_hand_grip',
              'max_floor_correction_blocks': float(envelope.max()), 'floor_correction_blocks': envelope.tolist(),
              'player_prefix': 'player/poem_mediapipe/', 'hero_prefix': 'hero/poem_mediapipe/'}
    write(OUT / 'conversion_report.json', report, True)
    write(OUT / 'reference_samples.json', dict(source_weapon, source_grips_scaled=grip_rows,
          source_left_grips_scaled=left_grip_rows, support_weights=support_weights,
          foot_plants=foot_rows, foot_local_points={s:p.tolist() for s,p in foot_points.items()},
          foot_local_centers={s:p.tolist() for s,p in foot_centers.items()}))
    assert max(errors) < .0001, max(errors)
    print('MEDIAPIPE_EXPORT', json.dumps({k: report[k] for k in ('samples', 'duration_seconds', 'joint_count',
          'max_support_hand_error_blocks', 'max_shared_grip_translation_blocks', 'max_floor_correction_blocks')}), flush=True)


if __name__ == '__main__':
    main()
