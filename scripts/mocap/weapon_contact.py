"""Geometric contact solver for a differently proportioned target scythe.

Pose observations remain the motion source. These constraints adapt the grip,
blade roll and monocular depth, which are not identifiable from the video.
"""
import math
import numpy as np
from scipy.optimize import minimize
from scipy.spatial import ConvexHull
from scipy.spatial.transform import Rotation


def unit(v, fallback=(1., 0., 0.)):
    n = np.linalg.norm(v)
    return np.asarray(v) / n if n > 1e-8 else np.array(fallback)


class WeaponContact:
    def __init__(self, vertices, scale, ground):
        self.scale = scale
        self.ground = ground
        self.vertices = np.asarray(vertices) * scale
        self.hull = self.vertices[ConvexHull(self.vertices).vertices]
        _, ix = np.unique(np.floor(self.vertices / .14).astype(int), axis=0,
                          return_index=True)
        self.samples = self.vertices[ix]
        self.previous = None
        self.reach = .872
        self.minimum_reach = .30
        self.margin = .012
        self.records = []

    @staticmethod
    def orientation(angle, depth, roll):
        axis = np.array([math.cos(angle) * math.cos(depth), math.sin(depth),
                         math.sin(angle) * math.cos(depth)])
        y = unit(np.cross(axis, [0, -1, 0]))
        x = unit(np.cross(y, axis))
        base = np.column_stack([x, np.cross(axis, x), axis])
        return Rotation.from_rotvec(axis * roll).as_matrix() @ base

    def solve(self, frame, axis, palm, shoulder, left_palm, left_shoulder,
              grip, box_rotations, box_centers, box_extents, planted=False,
              two_hands=False, locked_axis=False, blade_plane_hint=None):
        angle = math.atan2(axis[2], axis[0])
        depth = math.asin(np.clip(axis[1], -.75, .75))
        # x = primary palm XYZ, blade roll, absolute grip Z, shaft depth angle.
        reference = np.r_[palm, math.pi if planted else 0., grip, depth]
        previous = self.previous
        prev_roll = previous[3] if previous is not None else reference[3]
        preferred_roll = math.pi if planted else prev_roll
        preferred_roll += round((prev_roll - preferred_roll) / (2*math.pi)) * 2*math.pi
        bounds = [(shoulder[k]-self.reach, shoulder[k]+self.reach) for k in range(3)]
        roll_step=.62 if previous is not None else math.pi
        bounds += [(prev_roll-roll_step, prev_roll+roll_step), (-2.85, 1.62),
                   (max(-1.02, depth-.8), min(1.02, depth+.8))]
        if planted:
            bounds[5] = (max(-.35, depth-.18), min(.35, depth+.18))
        if locked_axis:
            depth=math.asin(np.clip(axis[1],-.9999,.9999))
            reference[5]=depth
            bounds[5]=(depth-.025,depth+.025)
            if blade_plane_hint is not None:
                base=self.orientation(angle,depth,0.)
                desired_x=unit(blade_plane_hint-axis*np.dot(blade_plane_hint,axis))
                desired_roll=math.atan2(np.dot(axis,np.cross(base[:,0],desired_x)),np.dot(base[:,0],desired_x))
                preferred_roll=desired_roll+round((prev_roll-desired_roll)/(2*math.pi))*2*math.pi
                reference[3]=preferred_roll
                # The screen-derived basis changes sign when the shaft faces
                # the camera. Compensate that basis change instead of flipping
                # the actual blade face during this corrected cut.
                bounds[3]=(preferred_roll-.12,preferred_roll+.12)

        def geometry(x):
            rr = self.orientation(angle, x[5], x[3])
            origin = x[:3] - rr[:, 2] * x[4] * self.scale
            floor = (self.hull @ rr[2, :]).min() + origin[2] - self.ground
            # Choose the supporting grip on the real rod, close to its observed
            # hand but with enough separation for the block palms.
            along = np.dot((left_palm*.5+left_shoulder*.5)-origin, rr[:, 2]) / self.scale
            along = np.clip(along, -2.85, 1.62)
            if abs(along-x[4]) < .38:
                options = np.clip([x[4]-.42, x[4]+.42], -2.85, 1.62)
                goals = origin + np.asarray(options)[:, None]*self.scale*rr[:, 2]
                along = options[np.argmin(np.linalg.norm(goals-left_shoulder, axis=1))]
            left_goal = origin + rr[:, 2]*along*self.scale
            return rr, origin, floor, left_goal, float(along)

        def inequalities(x):
            rr, origin, floor, lg, _ = geometry(x)
            d2=np.sum((x[:3]-shoulder)**2)
            values = [self.reach**2-d2, d2-self.minimum_reach**2, floor-self.margin]
            if two_hands:
                ld2=np.sum((lg-left_shoulder)**2)
                values.extend([self.reach**2-ld2,ld2-self.minimum_reach**2])
            return np.asarray(values)

        def objective(x):
            rr, origin, floor, lg, _ = geometry(x)
            pts = self.samples @ rr.T + origin
            local = np.einsum('pbi,bij->pbj', pts[:, None, :]-box_centers[None, :, :],
                              box_rotations)
            q = np.abs(local)-box_extents
            sdf = np.linalg.norm(np.maximum(q, 0), axis=2)+np.minimum(q.max(axis=2), 0)
            collision = np.maximum(0, .008-sdf.min(axis=0))
            loss = np.sum((x[:3]-palm)**2) * (2. if planted else 6.)
            loss += (x[4]-grip)**2*.36 + (x[5]-depth)**2*3.
            loss += (x[3]-preferred_roll)**2*(.7 if planted else .035)
            loss += np.sum(collision**2)*95
            if planted:
                loss += (floor-self.margin)**2*25
            if two_hands:
                loss += np.sum((lg-left_palm)**2)*1.5
            if previous is not None:
                loss += (x[3]-prev_roll)**2*.15+(x[4]-previous[4])**2*.32
                loss += np.sum((x[:3]-shoulder-self.previous_offset)**2)*3.0
            return float(loss)

        # Sliding the hand along the shaft often resolves the target's larger
        # blade without moving its body. Generate feasible starts explicitly.
        starts = []
        for roll in [preferred_roll, preferred_roll+math.pi, preferred_roll-math.pi]:
            roll = np.clip(roll, *bounds[3])
            x = reference.copy(); x[3] = roll
            if previous is not None:
                x[4] = (grip+previous[4])*.5
            x = np.array([np.clip(v, *b) for v, b in zip(x, bounds)])
            starts.append(self.project(x, angle, shoulder, bounds))
        if (planted or two_hands) and starts[0] is not None:
            # Put the shaft between the shoulders as a robust starting point
            # for the constrained two-hand solve.
            x = starts[0].copy()
            x[:3] = (shoulder+left_shoulder)*.5
            starts.append(self.project(x, angle, shoulder, bounds))

        candidates = []
        for i, start in enumerate(starts):
            if start is None:
                continue
            fit = minimize(objective, start, method='SLSQP', bounds=bounds,
                           constraints=[{'type':'ineq', 'fun':inequalities}],
                           options={'maxiter':55, 'ftol':2e-5})
            violation = max(0., -inequalities(fit.x).min())
            candidates.append((objective(fit.x)+violation*1e5, fit.x, violation))
            if violation < 1e-5 and i == 0 and not planted and objective(fit.x) < 1.5:
                break
        valid = [item for item in candidates if item[2] < 2e-5]
        x = min(valid or candidates, key=lambda item:item[0])[1]
        # Enforce floor/reach analytically even when a numerical solve stops
        # on a nonsmooth mesh extremum. This uses every convex-hull vertex.
        x = self.project(x, angle, shoulder, bounds)
        if x is None:
            raise RuntimeError(f'No feasible primary grip at source frame {frame}')
        rr, origin, floor, lg, lgrip = geometry(x)
        lerror = max(0., np.linalg.norm(lg-left_shoulder)-self.reach)
        self.previous = x.copy()
        self.previous_offset=x[:3]-shoulder
        self.records.append({'source_frame':frame, 'floor_clearance':float(floor),
                             'grip_z':float(x[4]), 'roll':float(x[3]),
                             'depth_angle':float(x[5]), 'hand_adaptation':float(np.linalg.norm(x[:3]-palm)),
                             'planted':bool(planted), 'two_hands_requested':bool(two_hands),
                             'support_reach_error':float(lerror)})
        return rr, origin, x[:3], float(x[4]), lg, lgrip, lerror

    def project(self, x, angle, shoulder, bounds):
        """Project a grasp into the arm's reach ball and floor half-space."""
        x = x.copy()
        rr = self.orientation(angle, x[5], x[3])
        axis = rr[:, 2]
        # The hand may slide on the actual handle, but not beyond its mount.
        floor = (self.hull @ rr[2, :]).min()+x[2]-axis[2]*x[4]*self.scale-self.ground
        if floor < self.margin and abs(axis[2]) > .02:
            x[4] = np.clip(x[4]+(floor-self.margin)/(axis[2]*self.scale), -2.85, 1.62)
        minimum = self.ground+self.margin-(self.hull @ rr[2, :]).min()+axis[2]*x[4]*self.scale
        if minimum > shoulder[2]+self.reach-1e-6:
            # Try additional blade roll/depth candidates if a very low pose
            # cannot hold the large target blade in the observed image plane.
            best = None
            for roll in [x[3], x[3]-math.pi/2, x[3]+math.pi/2, x[3]+math.pi]:
                for dep in [x[5], bounds[5][0], bounds[5][1]]:
                    cand = x.copy();cand[3]=np.clip(roll,*bounds[3]);cand[5]=dep
                    r = self.orientation(angle, dep, cand[3]); az=r[2,2]
                    cand[4] = 1.62 if az<0 else -2.85
                    h = self.ground+self.margin-(self.hull @ r[2,:]).min()+az*cand[4]*self.scale
                    if h <= shoulder[2]+self.reach-1e-6:
                        cost=(cand[3]-x[3])**2*.05+(dep-x[5])**2+(cand[4]-x[4])**2*.1
                        if best is None or cost<best[0]:best=(cost,cand,h)
            if best is None:return None
            _, x, minimum=best
        vec = x[:3]-shoulder
        if np.linalg.norm(vec)>self.reach:
            x[:3]=shoulder+unit(vec)*self.reach
        if x[2]<minimum:
            x[2]=minimum
            radius=math.sqrt(max(0,self.reach**2-(minimum-shoulder[2])**2))
            radial=x[:2]-shoulder[:2]
            if np.linalg.norm(radial)>radius:x[:2]=shoulder[:2]+unit(radial, (1.,0.))*radius
        vec=x[:3]-shoulder
        if np.linalg.norm(vec)<self.minimum_reach:
            x[:3]=shoulder+unit(vec)*self.minimum_reach
            if x[2]<minimum:
                x[2]=minimum
                radial=x[:2]-shoulder[:2]
                radius=math.sqrt(max(0,self.minimum_reach**2-(minimum-shoulder[2])**2))
                x[:2]=shoulder[:2]+unit(radial,(1.,0.))*radius
        return x
