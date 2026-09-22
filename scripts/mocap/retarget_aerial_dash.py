"""Adapt the reference's F276-294 airborne scythe spin to a sprint attack.

The full Google MediaPipe capture supplies torso lean, knee accents and source
correspondence. Reviewed unwrapped spin keys recover the rotations obscured by
the coat/weapon. The sprint approach and two-foot landing are game adaptations.
"""
import copy
import hashlib
import json
import math
from pathlib import Path

import numpy as np
from scipy.interpolate import PchipInterpolator
from scipy.spatial.transform import Rotation, Slerp

from combat_cleanup import CombatRig, PROFILE, affine, rotation, unit, two_bone, stabilize_limb_roll
from retarget_turning import solve_grounded_leg, smooth, sample, write, load

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT/'build/scythe_aerial_dash_08'
OUT = ROOT/'output/Herobrine_Scythe_AerialDash_08'
CAPTURE = ROOT/'build/scythe_recapture_05/capture_selected.npz'
VERSION = 'mediapipe_aerial_dash_08'
FPS, COUNT = 60, 91
# seconds, unwrapped axial spin, forward body tilt, pelvis height above floor,
# forward displacement in blocks, reference frame (zero based, 30 fps).
KEYS = np.array([
    (0.00, -45, 0, 1.03, 0.00, 273),
    (0.10, -58, 8, .86, .10, 275),
    (0.18, -38, 26, 1.38, .32, 276),
    (0.28, 15, 58, 2.48, .72, 279),
    (0.40, 145, 73, 3.10, 1.23, 282),
    (0.55, 315, 80, 3.38, 1.84, 285),
    (0.72, 515, 78, 3.32, 2.46, 288),
    (0.87, 665, 70, 2.90, 2.96, 291),
    (1.00, 675, 45, 2.08, 3.32, 294),
    (1.12, 685, 18, 1.27, 3.54, 294),
    (1.20, 680, 0, .88, 3.65, 294),
    (1.35, 675, 0, .98, 3.70, 294),
    (1.50, 675, 0, 1.03, 3.70, 294),
])


def main():
    WORK.mkdir(parents=True, exist_ok=True)
    (OUT/'capture').mkdir(parents=True, exist_ok=True)
    data = load(ROOT/'output/Herobrine_Scythe_Turn_07/capture/target_motion.json')
    profile = load(PROFILE)
    rig = CombatRig(profile, data)
    rig.grasp_smoothing = .025
    rig.max_grip_seeds = 8
    rig.grip_iterations = 140
    capture = np.load(CAPTURE)
    curve = PchipInterpolator(KEYS[:, 0], KEYS[:, 1:], axis=0)
    start_pose = {n:np.array(v) for n,v in data['poses'][0].items()}
    # The corrected third-cut blade is the edge reference, never the reversed
    # Turn07 review baseline. Orientations below carry that mesh with the body.
    reviewed = {n:np.array(v) for n,v in data['poses'][94].items()}
    chest_delta = lambda p:p['Chest'][:3,:3] @ rig.rest['Chest'][:3,:3].T
    ready_weapon = chest_delta(start_pose).T @ start_pose['Weapon:Scythe'][:3,:3]/rig.scale
    spin_weapon = chest_delta(reviewed).T @ reviewed['Weapon:Scythe'][:3,:3]/rig.scale
    # The mesh's hooked edge extends along local -Y, not local X. Its previous
    # roll put that hook almost parallel to the body's down axis. Project the
    # outward (in front of the chest) direction onto the plane across the shaft.
    # The hook now widens the spin's radius instead of pointing along the legs.
    shaft=unit(spin_weapon[:,2])
    hook=unit(np.array([0.,-1.,0.])-shaft*np.dot([0.,-1.,0.],shaft))
    spin_weapon=np.column_stack((unit(np.cross(-hook,shaft)),-hook,shaft))
    weapon_curve = Slerp([0., 1.], Rotation.from_matrix([ready_weapon, spin_weapon]))
    start_feet = {s:np.array([data['footwork'][0][f'foot_{s}x'], data['footwork'][0][f'foot_{s}y']])
                  for s in ('l','r')}
    poses, controls, footwork, records = [], [], [], []
    for i in range(COUNT):
        t = i/FPS
        yaw, tilt, height, advance, ref = map(float, curve(t))
        p = sample(capture['points'], ref*2)
        torso = p[[11,12]].mean(axis=0)-p[[23,24]].mean(axis=0)
        captured_tilt = math.degrees(math.atan2(np.linalg.norm(torso[:2]), torso[2]))
        aerial = float(np.clip(smooth((t-.13)/.14)*(1-smooth((t-1.01)/.18)),0.,1.))
        spin_weight = float(np.clip(smooth((t-.13)/.17)*(1-smooth((t-1.02)/.22)),0.,1.))
        tilt += .14*spin_weight*(np.clip(captured_tilt, 20., 88.)-tilt)
        # Spin around a tilted body axis, so the torso leads and the folded legs
        # orbit behind it. Rz @ tilt would merely circle an upright body heading.
        heading = rotation(x=tilt, y=-12.*spin_weight) @ rotation(z=yaw)
        origin = np.array([0., -advance/(10/16), rig.ground+height])
        pose = {'Bone.011':affine(heading, origin)}
        feet = {'reference_frame':ref+1, 'segment':1, 'aerial_weight':aerial}
        for side, short, sign in (('Left','l',1),('Right','r',-1)):
            hip = origin+heading @ [sign*.2,0,0]
            if t <= .13:
                plant = start_feet[short]
            elif t >= 1.01:
                plant = start_feet[short]+[0.,-3.70/(10/16)]
            else:
                plant = start_feet[short]+origin[:2]
            feet[f'foot_{short}x'],feet[f'foot_{short}y'] = map(float,plant)
            feet[f'foot_{short}_lift'] = 0.
            if aerial < 1e-8:
                solve_grounded_leg(rig, pose, side, feet, hip+heading @ [sign*.05,-.65,-.2])
            else:
                knee = p[25 if short=='l' else 26]-p[23 if short=='l' else 24]
                accent = float(np.clip(knee[2],-.4,.15))
                # Asymmetric scissor/tuck, driven in part by the captured knees.
                local_foot = [sign*(.33+.10*math.sin(t*7+sign)),
                              -.15+sign*.30*math.cos(t*5), -.66+accent*.30]
                air_goal = origin+heading @ local_foot
                goal = np.r_[plant,rig.ground+.10]*(1-aerial)+air_goal*aerial
                hint = hip+heading @ [sign*.18,-.78,-.10]
                leg_names=[f'Leg:{side}:{part}' for part in ('Upper','Lower')]
                ids=np.unique(np.concatenate([rig.groups[n][0] for n in leg_names]))
                for _ in range(24):
                    upper,lower,end = two_bone(hip, goal, hint,.6,.6,heading[:,0])
                    verts=np.zeros_like(rig.vertices)
                    for n,m in zip(leg_names,(upper,lower)):
                        ix,weights=rig.groups[n]
                        verts[ix]+=((np.c_[rig.vertices[ix],np.ones(len(ix))] @ (m @ rig.inverse[n]).T)[:,:3])*weights[:,None]
                    needed=rig.ground+.012-float(verts[ids,2].min())
                    if needed<1e-7:
                        break
                    goal[2]+=needed*1.02
                pose[f'Leg:{side}:Upper'],pose[f'Leg:{side}:Lower'] = upper,lower
                feet[f'foot_{short}_lift'] = max(0.,float(end[2]-rig.ground-.10))
        twist = -35.*(1-spin_weight)+10.*spin_weight*math.sin(t*9)
        body_r = heading @ rotation(x=8.*(1-spin_weight), z=twist*.48)
        chest_r = heading @ rotation(x=12.*(1-spin_weight), z=twist)
        chest = origin+body_r @ [0,0,.6]
        pose['Body'] = affine(body_r @ rig.rest['Body'][:3,:3],origin)
        pose['Chest'] = affine(chest_r @ rig.rest['Chest'][:3,:3],chest)
        pose['Head'] = affine(heading @ rotation(x=2.*(1-spin_weight),z=twist*.85)
                              @ rig.rest['Head'][:3,:3],chest+chest_r @ [0,0,.6])
        weapon_weight=float(np.clip(smooth((t-.08)/.14)*(1-smooth((t-1.03)/.20)),0.,1.))
        rr = chest_r @ weapon_curve([weapon_weight]).as_matrix()[0]
        rsh,lsh = [chest+chest_r @ [sign*.5,-.3,.4] for sign in (-1,1)]
        separation = rr[:,2]*1.75*rig.scale
        wanted = chest+chest_r @ [0,-.63,.24]-separation*.5
        hand = rig.fit_weapon(pose,rr,wanted,rsh,lsh,-3.,-1.25,i)
        rig.current_frame=i
        for side,sign,target in (('Right',-1,hand),('Left',1,hand+separation)):
            hint = chest+chest_r @ [sign*.88,-.32,.02]
            palm = rig.arm(pose,side,target,chest_r,hint)
            assert np.linalg.norm(palm-target)<1e-5
        pose['Weapon:Scythe'] = affine(rr*rig.scale,hand+rr[:,2]*3*rig.scale)
        poses.append(pose)
        controls.append(dict(frame=i+1,time=t,reference_frame=ref,spin_degrees=yaw,
                             body_tilt_degrees=tilt,mediapipe_tilt_degrees=captured_tilt,
                             advance_blocks=advance,height_source=height,aerial_weight=aerial,
                             phase='蹬地起跳' if t<.24 else '倾身收腿 · 人镰空转' if t<1.01 else '落地缓冲（游戏适配）'))
        footwork.append(feet)
        if i%15==0:
            print('AERIAL_RETARGET',i+1,'/',COUNT,'spin',round(yaw,1),flush=True)
    stabilize_limb_roll(poses)
    for i,pose in enumerate(poses):
        body_min=float(rig.skin(pose)[:,2].min()-rig.ground)
        blade_min=float(((np.c_[rig.weapon,np.ones(len(rig.weapon))] @ pose['Weapon:Scythe'].T)[:,2]).min()-rig.ground)
        records.append(dict(frame=i+1,body_clearance_source=body_min,blade_clearance_source=blade_min))
    assert min(r['body_clearance_source'] for r in records)>-.01, min(records,key=lambda r:r['body_clearance_source'])
    assert min(r['blade_clearance_source'] for r in records)>.005
    notes = ('Google MediaPipe GHUM Heavy capture, reviewed at 0.5x; source F276-294 (9.2-9.8s). '
             'Reviewed continuous keys restore a 720-degree tilted axial spin under occlusion. '
             'Captured torso/knee accents are retargeted to the block rig with two-hand IK. '
             'Sprint approach, 3.70-block travel and grounded landing are game adaptations; '
             'monocular depth/weapon roll are edited, not claimed as exact capture.')
    segment=dict(name='dash',label='疾跑 · 腾空旋镰',first_frame=1,last_frame=COUNT,
                 contact_frames=[[17,66]],recovery_frame=82,travel_blocks=3.70,lateral_blocks=0.,
                 reference_frames=[276,294],full_body_turn=True,root_turn_degrees=720.,aerial=True)
    data.update(action='HB_Scythe_MediaPipe_AerialDash_08',frames=list(range(1,COUNT+1)),
                poses=[{n:m.tolist() for n,m in row.items()} for row in poses],
                duration_seconds=(COUNT-1)/FPS,attack_segments=[segment],footwork=footwork,
                grip_local_z=[-3.]*COUNT,left_grip_local_z=[-1.25]*COUNT,support_hand_weight=[1.]*COUNT,
                release_interval_source_frames=[],method=notes,markers=[dict(frame=1,name=segment['label'])])
    data['combat_cleanup']=dict(version=VERSION,notes=notes,reference_playback_rate=.5,
                               capture_origin=str(CAPTURE),capture_sha256=hashlib.sha256(CAPTURE.read_bytes()).hexdigest(),
                               ordinary_combo_preserved='mediapipe_turning_07',v6_modes_preserved=[1,3],
                               reference_source_frames=[276,294],airborne_spin=True,
                               blade_direction='hook extends outward across the spin plane, away from the body axis')
    write(OUT/'capture/target_motion.json',data,True)
    write(OUT/'capture/aerial_retarget_report.json',dict(version=VERSION,controls=controls,clearance=records,
          source_capture_sha256=data['combat_cleanup']['capture_sha256'],grip_records=rig.grasp_records,notes=notes))
    print('AERIAL_RETARGET_COMPLETE',COUNT,flush=True)


if __name__=='__main__':
    main()
