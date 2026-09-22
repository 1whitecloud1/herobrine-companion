"""User-directed combat mechanics over the completed MediaPipe retarget.

The capture remains the source for sequence and timing. Grounded attacks use
explicit support feet, weight transfer and forward strike arcs to accommodate
the wide shoulders and short arms of the block character.
"""
from pathlib import Path
import argparse
import json
import math
import numpy as np
from scipy.interpolate import PchipInterpolator
from scipy.spatial.transform import Rotation, Slerp
from scipy.spatial import ConvexHull
from scipy.optimize import minimize

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_mocap'
PROFILE = Path('E:/MCStudioDownload/work/m13525918851@163.com/Cpp/AddOn/3055269d404f439eba5592a9d4b52113/_artifacts/Herobrine_镰刀战斗改进_V6/target_profile.json')


def unit(v, fallback=(1., 0., 0.)):
    v = np.asarray(v, dtype=float)
    d = np.linalg.norm(v)
    return v / d if d > 1e-8 else np.asarray(fallback, dtype=float)


def affine(r, p):
    m = np.eye(4)
    m[:3, :3] = r
    m[:3, 3] = p
    return m


def basis(direction, hinge):
    y = unit(direction, (0., 0., -1.))
    x = unit(hinge - y * np.dot(hinge, y))
    z = unit(np.cross(x, y))
    return np.column_stack((np.cross(y, z), y, z))


def rotation(x=0., y=0., z=0.):
    return Rotation.from_euler('xyz', [x, y, z], degrees=True).as_matrix()


def two_bone(origin, target, hint, a, b, hinge_hint):
    vector = target - origin
    distance = np.clip(np.linalg.norm(vector), abs(a-b)+.03, a+b-.004)
    axis = unit(vector, (0., -1., 0.))
    bend = unit(hint-origin-axis*np.dot(hint-origin, axis), (0., -1., 0.))
    along = (a*a-b*b+distance*distance)/(2*distance)
    joint = origin+axis*along+bend*math.sqrt(max(0., a*a-along*along))
    end = origin+axis*distance
    u, v = unit(joint-origin), unit(end-joint)
    hinge = unit(np.cross(u, v), hinge_hint)
    if np.dot(hinge, hinge_hint) < 0:
        hinge = -hinge
    return affine(basis(u, hinge), origin), affine(basis(v, hinge), joint), end


class CombatRig:
    def __init__(self, profile, motion):
        self.rest = {n: np.asarray(v['rest']) for n, v in profile['bones'].items()}
        self.inverse = {n: np.linalg.inv(m) for n, m in self.rest.items()}
        self.ground = motion['root_ground_local']
        self.scale = motion['weapon_scale']
        self.weapon = np.asarray(profile['weapon_vertices'])
        self.hull = self.weapon[ConvexHull(self.weapon).vertices]
        _, sample_ix = np.unique(np.floor(self.weapon/.08).astype(int), axis=0,
                                  return_index=True)
        rod = np.c_[np.zeros(110), np.zeros(110), np.linspace(-3.34, 1.69, 110)]
        self.weapon_samples = np.vstack([self.weapon[sample_ix], self.weapon[self.weapon[:, 2] < 1.4], rod])
        rw = np.asarray(profile['rig_world'])
        cr = np.linalg.inv(rw) @ np.asarray(profile['character_world'])
        vv = np.asarray([v['co'] for v in profile['character_vertices']])
        self.vertices = (np.c_[vv, np.ones(len(vv))] @ cr.T)[:, :3]
        groups = {}
        for i, v in enumerate(profile['character_vertices']):
            for n, w in v['weights'].items():
                if n in self.rest and w > 0:
                    groups.setdefault(n, []).append((i, w))
        self.groups = {n: (np.array([i for i, w in rows]), np.array([w for i, w in rows]))
                       for n, rows in groups.items()}
        self.floor_adaptations = []
        self.grasp_records = []
        self.previous_grasp = None
        self.previous_elbows = {}
        self.current_frame = -1

    def fit_weapon(self, pose, rr, palm, shoulder, left_shoulder, grip, left_grip, frame):
        """Keep the authored arc while fitting a real long handle in front."""
        boxes = [('Body', [0., .3, 0.], [.4, .3, .2]),
                 ('Chest', [0., .3, 0.], [.4, .3, .2]),
                 ('Head', [0., .4, 0.], [.4, .4, .4])]
        rotations = np.array([pose[n][:3, :3] for n, c, h in boxes])
        centers = np.array([pose[n][:3, 3]+pose[n][:3, :3]@np.array(c) for n, c, h in boxes])
        extents = np.array([h for n, c, h in boxes])
        points = (self.weapon_samples-np.array([0., 0., grip])) @ rr.T*self.scale
        floor_offset = float(((self.hull-np.array([0., 0., grip])) @ rr.T)[:, 2].min()*self.scale)
        minimum_z = self.ground+.024-floor_offset
        separation = rr[:, 2]*((left_grip-grip)*self.scale)
        if np.linalg.norm(left_shoulder-separation-shoulder) > 1.748001:
            raise RuntimeError(f'Grip span exceeds both arm lengths at {frame}')
        if minimum_z > min(shoulder[2]+.874, left_shoulder[2]-separation[2]+.874):
            raise RuntimeError(f'Blade clearance exceeds hand reach at {frame}')
        previous = self.previous_grasp
        expected = None
        if previous is not None and previous['frame'] == frame-1:
            expected = previous['palm']+shoulder-previous['shoulder']

        def objective(hand):
            loss = float(np.sum((hand-palm)**2))
            if expected is not None:
                loss += getattr(self, 'grasp_smoothing', 5.)*float(np.sum((hand-expected)**2))
            return loss

        def objective_jac(hand):
            value = 2.*(hand-palm)
            if expected is not None:
                value += 2.*getattr(self, 'grasp_smoothing', 5.)*(hand-expected)
            return value

        constraint_cache = {}

        def inequalities(hand):
            if 'hand' in constraint_cache and np.array_equal(hand, constraint_cache['hand']):
                return constraint_cache['values']
            vertices = points+hand
            local = np.einsum('pbi,bij->pbj', vertices[:, None, :]-centers[None, :, :], rotations)
            q = np.abs(local)-extents
            sdf = np.linalg.norm(np.maximum(q, 0.), axis=2)+np.minimum(q.max(axis=2), 0.)
            values = np.r_[.874**2-np.sum((hand-shoulder)**2),
                         .874**2-np.sum((hand+separation-left_shoulder)**2), hand[2]-minimum_z,
                         sdf.min(axis=0)-.022,
                         np.sum((hand-shoulder)**2)-.25**2,
                         np.sum((hand+separation-left_shoulder)**2)-.25**2]
            nearest = sdf.argmin(axis=0)
            loc = local[nearest, np.arange(len(boxes))]
            active_q = q[nearest, np.arange(len(boxes))]
            outside = np.maximum(active_q, 0.)
            lengths = np.linalg.norm(outside, axis=1)
            gradient = outside/np.maximum(lengths[:, None], 1e-12)
            inside = lengths < 1e-10
            gradient[inside] = 0.
            gradient[np.flatnonzero(inside), active_q[inside].argmax(axis=1)] = 1.
            gradient *= np.sign(loc)
            body_grad = np.einsum('bij,bj->bi', rotations, gradient)
            jacobian = np.vstack([-2.*(hand-shoulder), -2.*(hand+separation-left_shoulder),
                                  [0.,0.,1.], body_grad, 2.*(hand-shoulder),
                                  2.*(hand+separation-left_shoulder)])
            constraint_cache.update(hand=hand.copy(), values=values, jacobian=jacobian)
            return values

        def inequalities_jac(hand):
            inequalities(hand)
            return constraint_cache['jacobian']

        if expected is None and inequalities(palm).min() >= -1e-7:
            hand = palm.copy()
        else:
            other = left_shoulder-separation
            middle = (shoulder+other)*.5
            axis = unit(other-shoulder)
            radius = math.sqrt(max(0., .874**2-np.sum((other-shoulder)**2)*.25))
            u = unit(np.cross(axis, [0., 0., 1.]), (0., -1., 0.))
            v = np.cross(axis, u)
            seeds = [palm, middle]
            for a in np.linspace(0., 2*math.pi, 24, endpoint=False):
                seeds.append(middle+(u*math.cos(a)+v*math.sin(a))*radius*.985)
            seeds.sort(key=lambda x: max(0., -inequalities(x).min())*200.+objective(x))
            if expected is not None:
                seeds.insert(0, expected)
            fits = []
            for seed in seeds[:getattr(self, 'max_grip_seeds', 7)]:
                result = minimize(objective, seed, jac=objective_jac,
                                  method='SLSQP',
                                  constraints=[{'type': 'ineq', 'fun': inequalities, 'jac': inequalities_jac}],
                                  bounds=[(shoulder[i]-.874, shoulder[i]+.874) for i in range(3)],
                                  options={'maxiter': getattr(self, 'grip_iterations', 120), 'ftol': 1e-9})
                if inequalities(result.x).min() >= -2e-5:
                    fits.append(result.x)
                    break
            if not fits:
                raise RuntimeError(f'Combat grip cannot fit at source frame {frame}: {inequalities(result.x)}')
            hand = min(fits, key=objective)
        self.previous_grasp = {'frame': frame, 'palm': hand.copy(), 'shoulder': shoulder.copy()}
        self.grasp_records.append({'source_frame': frame, 'grip_z': grip,
                                   'left_grip_z': left_grip,
                                   'hand_adjustment': float(np.linalg.norm(hand-palm)),
                                   'body_clearance': float(inequalities(hand)[3:6].min()+.022)})
        return hand

    def skin(self, pose):
        vertices = np.zeros_like(self.vertices)
        for n, (ix, weights) in self.groups.items():
            vertices[ix] += ((np.c_[self.vertices[ix], np.ones(len(ix))]
                             @ (pose[n] @ self.inverse[n]).T)[:, :3])*weights[:, None]
        return vertices

    def arm(self, pose, side, target, chest_r, captured_hint=None):
        sign = 1 if side == 'Left' else -1
        shoulder = pose['Chest'][:3, 3]+chest_r @ np.array([sign*.50, -.30, .4])
        hint = shoulder+chest_r @ np.array([sign*.42, -.10, -.46]) if captured_hint is None else np.asarray(captured_hint)
        axis = unit(target-shoulder)
        wanted_bend = unit(hint-shoulder-axis*np.dot(hint-shoulder, axis))
        previous = self.previous_elbows.get(side)
        if previous is not None and previous[0] == self.current_frame-1:
            old_bend = unit(previous[1]-axis*np.dot(previous[1], axis), wanted_bend)
            angle = math.atan2(np.dot(axis, np.cross(old_bend, wanted_bend)), np.dot(old_bend, wanted_bend))
            wanted_bend = Rotation.from_rotvec(axis*np.clip(angle, -.30, .30)).apply(old_bend)
        hint = shoulder+wanted_bend
        upper, lower, palm = two_bone(shoulder, target, hint, .4, .48, chest_r[:, 0])
        pose[f'Arm:{side}:Upper'] = upper
        pose[f'Arm:{side}:Lower'] = lower
        self.previous_elbows[side] = (self.current_frame, lower[:3, 3]-shoulder)
        return palm

    def leg(self, pose, side, ankle_xy, pelvis_r, lift=0.):
        sign = 1 if side == 'Left' else -1
        hip = pose['Bone.011'][:3, 3]+pelvis_r @ np.array([sign*.2, 0., 0.])
        target = np.r_[ankle_xy, self.ground+.12+lift]
        hint = hip+np.array([sign*.08, -.50, -.40])
        for _ in range(9):
            upper, lower, end = two_bone(hip, target, hint, .6, .6, pelvis_r[:, 0])
            height = .2*(abs(lower[2, 0])+abs(lower[2, 2]))
            target[2] = self.ground+.009+height+lift
        pose[f'Leg:{side}:Upper'] = upper
        pose[f'Leg:{side}:Lower'] = lower

    def synthesize(self, c, frame):
        self.current_frame = frame
        # +X is the character's left; -Y is the opponent direction.
        yaw, lean = c['yaw'], c['lean']
        pelvis_r = rotation(z=c.get('pelvis_yaw', yaw*.10))
        body_r = rotation(x=lean*.40, z=yaw*.42)
        chest_r = rotation(x=lean, z=yaw)
        origin = np.array([c.get('x', 0.), c['advance'], self.ground+c['height']+.08])
        pose = {'Bone.011': affine(pelvis_r, origin),
                'Body': affine(body_r @ self.rest['Body'][:3, :3], origin)}
        chest = origin+body_r @ np.array([0., 0., .6])
        pose['Chest'] = affine(chest_r @ self.rest['Chest'][:3, :3], chest)
        head = chest+chest_r @ np.array([0., 0., .6])
        pose['Head'] = affine(rotation(x=4.+lean*.14, z=yaw*.16) @ self.rest['Head'][:3, :3], head)
        self.leg(pose, 'Left', (c.get('foot_lx', .42)+c.get('foot_shift_x', 0.),
                                c.get('foot_ly', -.48)+c.get('foot_shift_y', 0.)), pelvis_r,
                 c.get('foot_l_lift', 0.))
        self.leg(pose, 'Right', (c.get('foot_rx', -.43)+c.get('foot_shift_x', 0.),
                                 c.get('foot_ry', .36)+c.get('foot_shift_y', 0.)), pelvis_r,
                 c.get('foot_r_lift', 0.))

        shoulder = chest+chest_r @ np.array([-.50, -.30, .4])
        left_shoulder = chest+chest_r @ np.array([.50, -.30, .4])

        # A strike goes THROUGH the front half-space. It is an arc between a
        # right/up vector and the forward vector, never an orbit around Z.
        tilt = math.radians(c['inclination'])
        theta = math.radians(c['phase'])
        diagonal = np.array([-math.cos(tilt), 0., math.sin(tilt)])
        forward = np.array([0., -1., 0.])
        axis = diagonal*math.cos(theta)+forward*math.sin(theta)
        normal = unit(np.cross(diagonal, forward))
        rr = np.column_stack((normal, np.cross(axis, normal), axis))
        rr = rr @ rotation(z=c.get('roll', 0.))
        grip = c.get('grip', -3.00)
        left_grip = c.get('left_grip', -1.25)
        # Left hand guides the middle of the handle; the right hand drives the
        # tail. The coupled solve enforces both contacts on one rigid shaft.
        forward = c.get('hand_forward', .24+.16*float(np.clip((-c['hand'][1]-.50)/.33, 0., 1.)))
        center_x = .16*math.sin(theta)-.10*math.cos(theta)
        center_z = .05+c['hand'][2]*.40
        center = (shoulder+left_shoulder)*.5+np.array([center_x, -forward, center_z])
        separation = rr[:, 2]*((left_grip-grip)*self.scale)
        wanted = center-separation*.5
        goal = self.fit_weapon(pose, rr, wanted, shoulder, left_shoulder, grip, left_grip, frame)
        palm = self.arm(pose, 'Right', goal, chest_r)
        self.arm(pose, 'Left', palm+separation, chest_r)
        translation = palm-rr[:, 2]*grip*self.scale
        pose['Weapon:Scythe'] = affine(rr*self.scale, translation)
        floor = float((self.hull @ pose['Weapon:Scythe'][:3, :3].T+translation)[:, 2].min())
        lift = max(0., self.ground+.007-self.skin(pose)[:, 2].min(), self.ground+.009-floor)
        if lift > .000001:
            for m in pose.values():
                m[2, 3] += lift
        self.floor_adaptations.append((frame, float(lift)))
        return pose, float(grip)


def controls(keys):
    fields = ['phase', 'inclination', 'yaw', 'lean', 'height', 'advance',
              'hand', 'roll', 'grip']
    times = [row[0] for row in keys]
    curves = {name: PchipInterpolator(times, [row[i+1] for row in keys], axis=0)
              for i, name in enumerate(fields)}
    return lambda f: {name: curve(f).tolist() for name, curve in curves.items()}


# Source attack beats: first down-cut, returning up-cut, then two cross-cuts.
#             theta  tilt yaw lean hipZ  rootY       right palm offset     roll grip
OPENING = [
    (0,       42,    35, -12,  5, 1.07,  .02, [-.07, -.50, -.10],   0, -1.60),
    (6,       45,    45, -20,  6, 1.07,  .07, [-.08, -.68,  .24],   0, -1.60),
    (10,      50,    45, -17,  9, 1.04,  .01, [-.06, -.72,  .20],   0, -1.60),
    (13,      68,    45,  -2, 17, 1.02, -.12, [-.06, -.80,  .26],   0, -1.60),
    (16,     115,    45,  18, 22, 1.00, -.21, [ .17, -.76,  .30],   0, -1.60),
    (21,     147,    35,  26, 21, 1.01, -.22, [ .38, -.64,  .12],   0, -1.60),
    (25,     150,    35,  23, 17, 1.04, -.14, [ .34, -.61,  .12],  70, -1.60),
    (28,     147,    35,  18, 13, 1.04, -.08, [ .30, -.61,  .12], 180, -1.60),
    (32,     115,    40,   9, 17, 1.02, -.17, [ .25, -.76,  .12], 180, -1.60),
    (36,      58,    45, -14, 17, 1.03, -.22, [-.07, -.81,  .12], 180, -1.60),
    (40,      22,    45, -24, 10, 1.06, -.08, [-.08, -.74,  .24], 180, -1.60),
    (44,      18,    38, -26,  8, 1.07,  .02, [-.08, -.69,  .25],  90, -1.60),
    (48,      20,    10, -28, 10, 1.04, -.02, [-.10, -.68, -.01],   0, -1.60),
    (52,      65,    10,  -7, 17,  .99, -.19, [-.08, -.81, -.04],   0, -1.60),
    (56,     125,    10,  18, 20, 1.00, -.24, [ .17, -.79, -.10],   0, -1.60),
    (60,     158,    10,  26, 13, 1.04, -.14, [ .38, -.66, -.08],   0, -1.60),
    (64,     155,    15,  23, 10, 1.05, -.05, [ .30, -.65, -.08], 180, -1.60),
    (68,     100,    15,   4, 19, 1.01, -.21, [ .06, -.83, -.08], 180, -1.60),
    (73,      25,    15, -23, 17, 1.03, -.19, [-.07, -.78, -.04], 180, -1.60),
    (78,      20,    35, -20, 10, 1.06, -.01, [-.04, -.66, -.03], 180, -1.60),
    (82,      24,    35, -18, 10, 1.04, -.06, [-.04, -.66, -.03], 180, -1.60),
]

DASH_COMBO = [
    (86,      32,    30, -22, 22,  .99, -.28, [-.06, -.73, .13], 120, -2.75),
    (90,      45,    20, -18, 28,  .97, -.48, [-.08, -.80, .16],   0, -2.75),
    (94,      88,    12,   0, 32,  .93, -.64, [-.07, -.82, .17],   0, -2.75),
    (100,    147,    12,  24, 29,  .89, -.78, [ .34, -.68, .13],   0, -2.75),
    (105,    150,    20,  23, 22,  .93, -.72, [ .32, -.64, .12],  70, -2.75),
    (110,    150,    35,  18, 18,  .97, -.76, [ .31, -.67, .20], 180, -2.75),
    (115,    100,    35,   4, 18, 1.00, -.91, [-.03, -.82, .24], 180, -2.75),
    (121,     32,    42, -22, 14, 1.02, -.92, [-.09, -.78, .23], 180, -2.75),
    (126,     20,    35, -24, 10, 1.05, -.75, [-.08, -.70, .20],  90, -2.75),
    (129,     24,    22, -22, 16, 1.01, -.82, [-.06, -.76, .08],   0, -2.75),
    (135,    100,    22,  12, 24,  .98, -.95, [ .06, -.83, .20],   0, -2.75),
    (141,    152,    22,  27, 18, 1.00, -.88, [ .35, -.68, .12],   0, -2.75),
    (146,    152,    12,  23, 16,  .96, -.72, [ .32, -.64, .05], 180, -2.75),
    (151,    105,    10,   3, 24,  .91, -.90, [ .00, -.82,-.02], 180, -2.75),
    (158,     40,     6, -20, 28,  .85, -.99, [-.08, -.77,-.09], 180, -2.75),
    (164,     20,     6, -22, 24,  .83, -.97, [-.07, -.79,-.10], 180, -2.75),
    (168,     26,    35, -15, 10, 1.00, -.79, [-.07, -.79, .14], 180, -2.75),
    (173,     44,    50,  -8,  6, 1.05, -.72, [-.03, -.77, .28], 180, -2.75),
    (182,     70,    50,   0,  8, 1.05, -.72, [-.06, -.73, .26], 180, -2.75),
]

MIDDLE = [
    (196,     20,    35, -20, 15, 1.04, -.74, [-.08, -.73, .19],   0, -2.75),
    (205,     30,    40, -20, 18, 1.00, -.79, [-.07, -.78, .22],   0, -2.75),
    (211,    108,    35,  12, 26,  .93, -.95, [ .10, -.78, .32],   0, -2.75),
    (217,    149,    25,  28, 22,  .92,-1.00, [ .36, -.67, .21],   0, -2.75),
    (223,    146,    28,  23, 18,  .95, -.87, [ .32, -.64, .14], 180, -2.75),
    (228,    103,    35,   4, 25,  .94, -.95, [-.02, -.81, .26], 180, -2.75),
    (235,     30,    43, -22, 15, 1.00, -.89, [-.08, -.78, .25], 180, -2.75),
    (240,     25,    12, -25, 25,  .85, -.87, [-.08, -.72, .00],  70, -2.75),
    (246,     75,     6,  -7, 34,  .76,-1.05, [-.06, -.76,-.10],   0, -2.75),
    (251,    138,     5,  23, 38,  .72,-1.04, [ .33, -.67,-.04],   0, -2.75),
    (257,    151,    12,  20, 30,  .80, -.89, [ .31, -.63, .10],  50, -2.75),
    (264,    155,    20,  18, 25,  .85, -.80, [ .28, -.66, .12], 150, -2.75),
]

FINISH = [
    (382,     20,    25, -18, 12, 1.03, -.76, [-.07, -.68, .14], 180, -2.75),
    (389,     22,    30, -21, 16,  .98, -.90, [-.07, -.67, .12], 180, -2.75),
    (395,     18,    40, -30, 28,  .89,-1.00, [-.08, -.64, .16], 180, -2.75),
    (400,     16,    42, -28, 30,  .89,-1.02, [-.09, -.62, .23], 120, -2.75),
    (414,     18,    43, -26, 28,  .90,-1.00, [-.09, -.65, .26],   0, -2.75),
    (424,     22,    50, -31, 21,  .96, -.95, [-.09, -.66, .32],   0, -2.75),
    (430,     28,    50, -29, 18, 1.00, -.97, [-.10, -.72, .32],   0, -2.75),
    (436,     62,    42,  -7, 30,  .95,-1.15, [-.09, -.81, .28],   0, -2.75),
    (442,    114,    32,  20, 34,  .90,-1.23, [ .20, -.75, .33],   0, -2.75),
    (448,    151,    28,  29, 27,  .92,-1.24, [ .36, -.67, .21],   0, -2.75),
    (455,    154,    28,  27, 22,  .98,-1.15, [ .34, -.65, .16],   0, -2.75),
    (463,    143,    25,  18, 14, 1.02,-1.12, [ .20, -.64, .09],   0, -2.75),
    (474,    135,    12,   4,  8, 1.05,-1.10, [ .02, -.58,-.18],   0, -2.75),
    (487,     80,    -8,  -4,  7, 1.05,-1.06, [-.08, -.63,-.20],   0, -2.75),
    (499,     25,   -22,  -7,  5, 1.06,-1.04, [-.10, -.56,-.24],   0, -2.75),
    (511,     16,   -22,  -8,  4, 1.06,-1.04, [-.08, -.48,-.26],   0, -2.75),
    (518,     16,   -22,  -8,  4, 1.06,-1.04, [-.08, -.48,-.26],   0, -2.75),
]


def smoothstep(a):
    a = np.clip(a, 0., 1.)
    return float(a*a*(3.-2.*a))


def footwork(c, f):
    left_step = smoothstep((f-82)/12)
    right_step = smoothstep((f-91)/12)
    c['foot_ly'] = -.48-.72*left_step
    c['foot_ry'] = .36-.72*right_step
    c['foot_l_lift'] = .12*math.sin(math.pi*(f-82)/12) if 82 < f < 94 else 0.
    c['foot_r_lift'] = .10*math.sin(math.pi*(f-91)/12) if 91 < f < 103 else 0.
    if f >= 382:
        c['foot_ly'] -= .30*smoothstep((f-427)/12)
        c['foot_ry'] -= .22*smoothstep((f-438)/13)
        c['foot_l_lift'] = .09*math.sin(math.pi*(f-427)/12) if 427 < f < 439 else 0.
        c['foot_r_lift'] = .07*math.sin(math.pi*(f-438)/13) if 438 < f < 451 else 0.


def blend_pose(rig, a, b, ga, gb, weight, rotation_cache):
    if weight >= 1.-1e-8:
        return {n: m.copy() for n, m in b.items()}, gb
    if weight <= 1e-8:
        return {n: m.copy() for n, m in a.items()}, ga
    pose = {}
    for n in a:
        scale = rig.scale if n == 'Weapon:Scythe' else 1.
        qa, qb = Rotation.from_matrix([a[n][:3, :3]/scale, b[n][:3, :3]/scale]).as_quat()
        if n in rotation_cache:
            old_a, old_b = rotation_cache[n]
            if np.dot(qa, old_a) < 0: qa = -qa
            if np.dot(qb, old_b) < 0: qb = -qb
        elif np.dot(qa, qb) < 0:
            qb = -qb
        rotation_cache[n] = (qa.copy(), qb.copy())
        dot = float(np.clip(np.dot(qa, qb), -.9999999, .9999999))
        angle = math.acos(dot)
        if dot > .9995:
            q = qa*(1-weight)+qb*weight
        else:
            q = (math.sin((1-weight)*angle)*qa+math.sin(weight*angle)*qb)/math.sin(angle)
        rr = Rotation.from_quat(q/np.linalg.norm(q)).as_matrix()
        pos = a[n][:3, 3]*(1-weight)+b[n][:3, 3]*weight
        pose[n] = affine(rr*scale, pos)
    pose['Body'][:3, 3] = pose['Bone.011'][:3, 3]
    pose['Chest'][:3, 3] = pose['Body'][:3, 3]+pose['Body'][:3, 1]*.6
    pose['Head'][:3, 3] = pose['Chest'][:3, 3]+pose['Chest'][:3, 1]*.6
    for side, sign in [('Left', 1), ('Right', -1)]:
        au = pose[f'Arm:{side}:Upper']
        offset_a = a['Chest'][:3, :3].T@(a[f'Arm:{side}:Upper'][:3, 3]-a['Chest'][:3, 3])
        offset_b = b['Chest'][:3, :3].T@(b[f'Arm:{side}:Upper'][:3, 3]-b['Chest'][:3, 3])
        au[:3, 3] = pose['Chest'][:3, 3]+pose['Chest'][:3, :3] @ (offset_a*(1-weight)+offset_b*weight)
        lu = pose[f'Leg:{side}:Upper']
        lu[:3, 3] = pose['Bone.011'][:3, 3]+pose['Bone.011'][:3, :3] @ np.array([sign*.2, 0., 0.])
        pose[f'Arm:{side}:Lower'][:3, 3] = au[:3, 3]+au[:3, 1]*.4
        pose[f'Leg:{side}:Lower'][:3, 3] = lu[:3, 3]+lu[:3, 1]*.6
        for limb, first, last in [('Arm', .4, .48), ('Leg', .6, .6)]:
            un, ln = f'{limb}:{side}:Upper', f'{limb}:{side}:Lower'
            origin = pose[un][:3, 3]
            end_a = a[ln][:3, 3]+a[ln][:3, 1]*last-a[un][:3, 3]
            end_b = b[ln][:3, 3]+b[ln][:3, 1]*last-b[un][:3, 3]
            bend_a = a[ln][:3, 3]-a[un][:3, 3]
            bend_b = b[ln][:3, 3]-b[un][:3, 3]
            goal = origin+end_a*(1-weight)+end_b*weight
            hint = origin+bend_a*(1-weight)+bend_b*weight
            upper, lower, end = two_bone(origin, goal, hint, first, last, pose[un][:3, 0])
            pose[un], pose[ln] = upper, lower
    grip = ga*(1-weight)+gb*weight
    palm = pose['Arm:Right:Lower'][:3, 3]+pose['Arm:Right:Lower'][:3, 1]*.48
    weapon = pose['Weapon:Scythe']
    weapon[:3, 3] = palm-weapon[:3, 2]*grip
    floor = float((rig.hull@weapon[:3, :3].T+weapon[:3, 3])[:, 2].min())
    if floor < rig.ground+.016 and abs(weapon[2, 2]) > .02:
        grip = float(np.clip(grip+(floor-rig.ground-.016)/weapon[2, 2], -2.85, 1.62))
        weapon[:3, 3] = palm-weapon[:3, 2]*grip
    floor = float((rig.hull@weapon[:3, :3].T+weapon[:3, 3])[:, 2].min())
    lift = max(0., rig.ground+.007-rig.skin(pose)[:, 2].min(), rig.ground+.009-floor)
    for m in pose.values():
        m[2, 3] += lift
    return pose, grip


def stabilize_limb_roll(poses):
    repairs = 0
    for side in ['Left', 'Right']:
        for limb in ['Arm', 'Leg']:
            old = None
            for pose in poses:
                upper = pose[f'{limb}:{side}:Upper']
                lower = pose[f'{limb}:{side}:Lower']
                hinge = upper[:3, 0].copy()
                if old is not None and np.dot(hinge, old) < 0:
                    hinge = -hinge
                    repairs += 1
                upper[:3, :3] = basis(upper[:3, 1], hinge)
                lower[:3, :3] = basis(lower[:3, 1], hinge)
                old = hinge
    return repairs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--opening', action='store_true')
    args = parser.parse_args()
    baseline = WORK / 'target_motion_before_combat.json'
    if not baseline.exists():
        baseline.write_bytes((WORK / 'target_motion.json').read_bytes())
    motion = json.loads(baseline.read_text(encoding='utf-8'))
    profile = json.loads(PROFILE.read_text(encoding='utf-8'))
    rig = CombatRig(profile, motion)
    poses = [{n: np.asarray(m) for n, m in row.items()} for row in motion['poses']]
    if args.opening:
        intervals = [(OPENING, 0, 82, lambda f: 1.)]
    else:
        # Carry the captured aerial passages forward by the distance covered
        # in the authored dash, retaining their relative captured motion.
        for f in range(166, len(poses)):
            for m in poses[f].values():
                m[1, 3] -= .72
        intervals = [
            (OPENING+DASH_COMBO, 0, 182, lambda f: 1.-smoothstep((f-166)/16)),
            (MIDDLE, 196, 264, lambda f: smoothstep((f-196)/12)*(1.-smoothstep((f-252)/12))),
            (FINISH, 382, 518, lambda f: smoothstep((f-382)/12)),
        ]
    weights = np.zeros(len(poses))
    left_grips = np.full(len(poses), -1.25)
    shoulder_turn = PchipInterpolator([0, 35, 45, 70, 100, 115, 147, 180],
                                     [-72, -66, -60, -48, -30, -18, 15, 27])
    for keys, start, end, envelope in intervals:
        curve = controls(keys)
        rotation_cache = {}
        for f in range(start, end+1):
            c = curve(f)
            c['phase'] = float(np.clip(c['phase'], 42., 150.))
            c['grip'] = -3.00
            c['left_grip'] = -1.25
            c['yaw'] = float(shoulder_turn(c['phase']))-10.-5.*float(np.clip(-c['inclination']/8., 0., 1.))
            footwork(c, f)
            authored, grip = rig.synthesize(c, f)
            weight = envelope(f)
            poses[f], grip = blend_pose(rig, poses[f], authored, motion['grip_local_z'][f], grip, weight, rotation_cache)
            motion['grip_local_z'][f] = grip
            if weight > .001:
                motion['support_hand_weight'][f] = 1. if weight > .999999 else 0.
            weights[f] = weight
    twist_repairs = stabilize_limb_roll(poses)
    for f, pose in enumerate(poses):
        lift = max(0., rig.ground+.004-rig.skin(pose)[:, 2].min())
        for n, m in pose.items():
            if n != 'Weapon:Scythe' or not 349 <= f <= 381:
                m[2, 3] += lift
    motion['combat_cleanup'] = {
        'version': 'two_hand_forward_combat_03',
        'source_frame_intervals': [[a, b] for k, a, b, w in intervals],
        'authored_weights': weights.tolist(),
        'notes': 'User-directed reconstruction: left hand grips the middle section of the shaft at -1.25, right hand drives the tail at -3.00. Both contacts are solved together, with a planted staggered stance and a forward arc from own upper right to lower left. Shoulder protraction and torso wind-up adapt the wide block torso while arm bone lengths remain fixed.',
        'opponent_direction_rig': [0., -1., 0.],
        'capture_timing_retained': True,
    }
    motion['method'] += ' User-directed combat cleanup adds explicit forward extension, support feet and grounded weight transfer to the attack beats.'
    motion['limitations'] = 'Single-view depth, occluded acrobatics and the released weapon are estimated. Grounded attacks use user-directed two-hand reconstruction; forward root travel and shoulder protraction are authored, not measured.'
    motion['camera_floor_compensation'] += ' Combat cleanup adds an authored forward dash; the later captured aerial passage is offset by -0.72 rig units in Y to connect it.'
    motion['left_grip_local_z'] = [float((np.linalg.inv(pose['Weapon:Scythe'])@np.r_[pose['Arm:Left:Lower'][:3, 3]+pose['Arm:Left:Lower'][:3, 1]*.48, 1.])[2]) for pose in poses]
    motion.pop('opening_direction_local_keyframes_degrees', None)
    motion['poses'] = [{n: m.tolist() for n, m in pose.items()} for pose in poses]
    (WORK / 'target_motion.json').write_text(json.dumps(motion, ensure_ascii=False, separators=(',', ':')), encoding='utf-8')
    (WORK / 'combat_cleanup_report.json').write_text(json.dumps({
        'version': motion['combat_cleanup']['version'],
        'corrected_source_frame_intervals': motion['combat_cleanup']['source_frame_intervals'],
        'maximum_floor_lift': max(v for f, v in rig.floor_adaptations),
        'maximum_grip_adaptation': max(v['hand_adjustment'] for v in rig.grasp_records),
        'limb_roll_sign_repairs': twist_repairs,
        'grasp_records': rig.grasp_records,
        'opening_controls': OPENING,
    }, ensure_ascii=False, indent=2), encoding='utf-8')
    print('COMBAT_CLEANUP', {k: v for k, v in motion['combat_cleanup'].items() if k != 'authored_weights'}, flush=True)


if __name__ == '__main__':
    main()
