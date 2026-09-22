"""Review-guided MediaPipe retarget with unwrapped body turns and pivot steps.

The single reference view does not recover reliable yaw through occlusion.
Reviewed, continuous heading keys restore those turns; the selected Google
MediaPipe capture still supplies the source correspondence and body accents.
Every cut has its own body, blade elevation, stance and hand-height curves.
"""
import copy
import hashlib
import json
import math
import shutil
from pathlib import Path

import numpy as np
from scipy.interpolate import PchipInterpolator
from scipy.ndimage import gaussian_filter1d
from scipy.spatial.transform import Rotation

from combat_cleanup import CombatRig, PROFILE, affine, rotation, unit, two_bone, stabilize_limb_roll
from retarget_wide_swing import dynamic_path, transport_bases

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT/'build/scythe_turn_07'
OUT = ROOT/'output/Herobrine_Scythe_Turn_07'
CAPTURE = ROOT/'build/scythe_wide_06'
VERSION = 'mediapipe_turning_07'
FIELDS = ('yaw', 'twist', 'shaft', 'elevation', 'lean', 'side', 'height', 'reach', 'hand_z')
# u, unwrapped pelvis heading, chest twist, shaft azimuth relative to chest,
# shaft elevation, forward lean, side lean, pelvis height, hand reach, hand height.
# The third cut begins at 2.47 s in the half-speed comparison, as in Wide06.
CUTS = [
    dict(label='跨步大幅斜斩', count=36, source=(15, 28), advance=.80, x=-.18,
         contact=(.22,.74), step=[('r',.06,.86,.17)], keys=[
        (0,-45,-35,-50,38,12,0,1.08,.60,.22),
        (.16,-52,-38,-55,44,9,-7,1.08,.54,.32),
        (.40,-5,-10,-8,28,22,-12,1.01,.74,.28),
        (.65,43,23,47,-24,32,10,.96,.77,.08),
        (1,55,25,60,-22,23,5,1.01,.60,.10)]),
    dict(label='反向上挑举镰', count=38, source=(28, 49), advance=.72, x=.15,
         contact=(.22,.75), step=[('l',.05,.86,.16)], keys=[
        (.15,53,29,62,-25,27,9,.96,.64,.05),
        (.38,24,0,18,-15,23,4,.99,.77,.13),
        (.61,-12,-27,-47,40,11,-14,1.05,.62,.45),
        (.80,-20,-40,-55,73,2,-5,1.10,.46,.51),
        (1,-20,-40,-55,73,3,0,1.10,.48,.48)]),
    dict(label='跨步转体横扫', count=44, source=(49, 65), advance=.55, x=-.12,
         contact=(.16,.87), step=[('r',.04,.96,.40)], spin=True, keys=[
        (.12,-10,-20,-44,40,12,-8,1.03,.58,.32),
        (.30,90,5,-12,8,13,-10,1.02,.72,.21),
        (.52,230,20,15,5,14,5,1.03,.74,.16),
        (.77,325,-12,-22,10,10,10,1.05,.67,.24),
        (1,340,-20,-35,12,13,0,1.04,.60,.25)]),
    dict(label='侧跨换脚转体斜抡', count=44, source=(65, 82), advance=.60, x=.20,
         contact=(.16,.84), step=[('l',.04,.96,.25)], spin=True, keys=[
        (.17,369,-30,-54,40,7,-16,1.06,.57,.43),
        (.41,523,18,8,45,10,17,1.01,.66,.40),
        (.64,655,35,40,-14,29,16,.92,.76,.05),
        (.84,696,24,-15,-22,34,-8,.89,.72,.10),
        (1,700,20,-35,-20,30,-10,.89,.62,.20)]),
    dict(label='跨步转身沉身扫镰', count=52, source=(82, 110), advance=1.45, x=-.28,
         contact=(.23,.82), step=[('r',.03,.45,.22),('l',.50,.91,.18)], spin=True, keys=[
        (.18,746,-18,-62,43,35,-12,.88,.51,.39),
        (.37,890,5,-15,36,25,5,.90,.68,.44),
        (.57,1020,32,32,4,37,12,.72,.78,.03),
        (.72,1060,15,60,22,37,-8,.63,.65,.05),
        (1,1060,15,60,22,34,-8,.66,.63,.05)]),
    dict(label='弓步起身绕镰上挑', count=46, source=(110, 128), advance=.78, x=.18,
         contact=(.16,.84), step=[('r',.04,.90,.18)], keys=[
        (.22,1100,35,43,68,10,16,.99,.43,.46),
        (.45,1150,26,38,-10,29,10,.91,.77,.09),
        (.64,1110,-15,-20,22,23,-16,.99,.74,.27),
        (.84,1040,-35,-64,58,8,-6,1.07,.53,.47),
        (1,1030,-30,-65,55,10,0,1.07,.53,.43)]),
    dict(label='跨步提膝转体大回环', count=50, source=(128, 148), advance=.62, x=-.14,
         contact=(.14,.86), step=[('l',.03,.96,.49)], spin=True, keys=[
        (.19,1060,-20,-54,-18,25,14,1.00,.73,.04),
        (.39,1205,24,-3,55,8,-17,1.04,.57,.48),
        (.61,1325,33,46,42,3,17,1.08,.59,.49),
        (.81,1380,22,38,-18,22,10,1.02,.72,.11),
        (1,1390,20,30,-20,19,0,1.03,.65,.13)]),
    dict(label='侧跨低身转体扫镰', count=54, source=(148, 170), advance=.60, x=0.,
         contact=(.22,.84), step=[('r',.03,.96,.11)], spin=True, keys=[
        (.17,1410,25,22,-4,38,-10,.68,.69,-.03),
        (.34,1495,18,-12,4,43,-12,.58,.78,-.05),
        (.55,1660,20,15,3,40,12,.61,.77,-.03),
        (.76,1740,4,4,14,28,10,.77,.73,.05),
        (1,1755,-35,-50,38,12,0,1.08,.60,.22)]),
]


def load(path):
    return json.loads(path.read_text(encoding='utf-8'))


def write(path, data, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, allow_nan=False,
                   indent=None if compact else 2, separators=(',', ':') if compact else None)+'\n', encoding='utf-8')


def smooth(u):
    u = float(np.clip(u, 0., 1.))
    return u*u*u*(10.+u*(-15.+6.*u))


def sample(array, f):
    a, b = int(f), min(int(f)+1, len(array)-1)
    return array[a]*(1-f%1)+array[b]*(f%1)


def solve_grounded_leg(rig, pose, side, foot, hint):
    """Plant the weighted source mesh, including its blended knee vertices."""
    short,sign=('l',1) if side=='Left' else ('r',-1)
    pelvis=pose['Bone.011'][:3,:3]
    hip=pose['Bone.011'][:3,3]+pelvis @ [sign*.2,0,0]
    lift=foot[f'foot_{short}_lift']
    goal=np.array([foot[f'foot_{short}x'],foot[f'foot_{short}y'],rig.ground+.13+lift])
    names=[f'Leg:{side}:{part}' for part in ('Upper','Lower')]
    ids=np.unique(np.concatenate([rig.groups[n][0] for n in names]))
    direction=unit(goal-hip)
    bend=unit(hint-hip-direction*np.dot(hint-hip,direction))
    up=unit(np.array([0,0,1])-direction*direction[2],bend)
    for _ in range(42):
        upper,lower,end=two_bone(hip,goal,hip+bend,.6,.6,pelvis[:,0])
        if lower[2,3] < rig.ground+.28:
            bend=unit(bend*.8+up*.2)
            continue
        verts=np.zeros_like(rig.vertices)
        for n,m in zip(names,(upper,lower)):
            ix,weights=rig.groups[n]
            verts[ix]+=((np.c_[rig.vertices[ix],np.ones(len(ix))] @ (m @ rig.inverse[n]).T)[:,:3])*weights[:,None]
        minimum=float(verts[ids,2].min())
        delta=rig.ground+.012+lift-minimum
        if abs(delta)<2e-6:
            break
        goal[2]+=delta*.75
    assert np.linalg.norm(end[:2]-goal[:2])<1e-5,(side,hip,goal)
    pose[names[0]],pose[names[1]]=upper,lower


def build_controls(capture):
    controls, segments, feet = [], [], []
    last, start = None, 1
    root_start = np.array([0., 0.])
    foot_start = {s: (rotation(z=-45) @ [sign*.52, -.20 if s=='r' else .20, 0])[:2]
                  for s, sign in (('l',1),('r',-1))}
    for ci, cut in enumerate(CUTS):
        keys = cut['keys'] if last is None else [(0., *last)]+cut['keys']
        curve = PchipInterpolator([r[0] for r in keys], [r[1:] for r in keys], axis=0)
        last = keys[-1][1:]
        end = root_start+np.array([cut['x']-root_start[0], -cut['advance']])
        root_at = lambda u: root_start+(end-root_start)*smooth(u)
        targets, events = {}, {}
        for side, a, b, height in cut['step']:
            future = np.clip(b+.035,0,1)
            yaw = float(curve(future)[0])
            sign = 1 if side=='l' else -1
            stance_width = .70 if ci in (4,5,7) else .55
            # A landing is ahead of its hip. In the low finish the free leg
            # reaches sideways; its trajectory therefore differs from a pivot.
            local = [sign*stance_width, -.28 if ci!=7 else .14, ]
            targets[side] = root_at(future)+(rotation(z=yaw) @ [*local,0])[:2]
            # The stepping foot travels toward the opponent even while the
            # pelvis is facing backward halfway through a turning advance.
            targets[side][1]=min(targets[side][1],root_at(future)[1]-.24)
            events[side] = (a,b,height)
        first = start
        seg = dict(name=f'combo_{ci+1:02d}', label=cut['label'], first_frame=start,
                   last_frame=start+cut['count'], reference_frames=[f+1 for f in cut['source']],
                   contact_frames=[(start+round(cut['count']*cut['contact'][0]),
                                    start+round(cut['count']*cut['contact'][1]))],
                   recovery_frame=start+cut['count']-3,
                   travel_blocks=round(cut['advance']*.625,6),
                   lateral_blocks=round(-(end[0]-root_start[0])*.625,6),
                   full_body_turn=bool(cut.get('spin')), root_turn_degrees=last[0]-keys[0][1])
        segments.append(seg)
        for k in range(cut['count']+1):
            if ci and k==0:
                continue
            u = k/cut['count']
            row = {n:float(v) for n,v in zip(FIELDS,curve(u))}
            ref = cut['source'][0]+(cut['source'][1]-cut['source'][0])*u
            p = sample(capture['points'], ref*2)
            shoulder = p[[11,12]].mean(axis=0)
            hip = p[[23,24]].mean(axis=0)
            measured_lean = math.degrees(math.atan2(np.linalg.norm((shoulder-hip)[:2]), (shoulder-hip)[2]))
            # Add the capture's torso rhythm, without collapsing yaw to the
            # forward hemisphere when the detector swaps occluded shoulders.
            row['lean'] += .12*np.clip(measured_lean-15.,-12.,25.)*math.sin(math.pi*u)**2
            xy = root_at(u)
            fw = dict(segment=ci+1, reference_frame=ref+1)
            for side in ('l','r'):
                pos, lift = foot_start[side].copy(), 0.
                if side in events:
                    a,b,h = events[side]
                    v = float(np.clip((u-a)/(b-a),0,1))
                    blend = smooth(v)
                    pos = pos*(1-blend)+targets[side]*blend
                    envelope = math.sin(math.pi*v)**2
                    lift = h*envelope
                    if a < u < b:
                        sign = 1 if side=='l' else -1
                        radius = .98 if ci==7 else .57
                        orbit = xy+(rotation(z=row['yaw']) @ [sign*radius,.09,0])[:2]
                        pos = pos*(1-envelope*.86)+orbit*envelope*.86
                fw[f'foot_{side}x'],fw[f'foot_{side}y'] = map(float,pos)
                fw[f'foot_{side}_lift'] = float(lift)
            # Move over the actual pivot while the free knee is up. The pivot
            # sole keeps one world-space contact throughout the full turn.
            if cut.get('spin') and ci!=4:
                free=cut['step'][0][0];support='r' if free=='l' else 'l'
                a,b,_=events[free]
                v=float(np.clip((u-a)/(b-a),0,1))
                envelope=math.sin(math.pi*v)**2
                sign=1 if support=='l' else -1
                pivot=np.array([fw[f'foot_{support}x'],fw[f'foot_{support}y']])
                balanced=pivot+(rotation(z=row['yaw']) @ [-sign*.22,-.05,0])[:2]
                xy=xy*(1-envelope*.82)+balanced*envelope*.82
            # Let the hips sink into a long step; do not contract the authored
            # foot spacing just to keep an almost upright pelvis height.
            for side,sign in (('l',1),('r',-1)):
                hipxy=xy+(rotation(z=row['yaw']) @ [sign*.2,0,0])[:2]
                footxy=np.array([fw[f'foot_{side}x'],fw[f'foot_{side}y']])
                horizontal=float(np.linalg.norm(footxy-hipxy))
                assert horizontal<1.17,('Step requires another support',ci,k,side,horizontal)
                height_limit=.10+fw[f'foot_{side}_lift']+math.sqrt(1.17**2-horizontal**2)
                row['height']=min(row['height'],height_limit)
            row.update(x=float(xy[0]),y=float(xy[1]),reference_frame=ref+1,segment=ci+1)
            controls.append(row);feet.append(fw)
        for side,pos in targets.items():
            foot_start[side]=pos.copy()
        root_start=end
        start+=cut['count']
    return controls,feet,segments


def main():
    WORK.mkdir(parents=True,exist_ok=True)
    (OUT/'capture').mkdir(parents=True,exist_ok=True)
    for name in ('capture_selected.npz','capture_report.json','selection_report.json'):
        shutil.copy2(CAPTURE/name,WORK/name)
    capture=np.load(WORK/'capture_selected.npz')
    base_path=ROOT/'output/Herobrine_Scythe_Wide_06/capture/target_motion.json'
    data,profile=load(base_path),load(PROFILE)
    rig=CombatRig(profile,data)
    rig.grasp_smoothing=.08;rig.max_grip_seeds=4;rig.grip_iterations=95
    controls,feet,segments=build_controls(capture)
    count=len(controls)
    axes=[]
    for c in controls:
        az,el=map(math.radians,(c['yaw']+c['twist']+c['shaft'],c['elevation']))
        axes.append([math.cos(el)*math.cos(az),math.cos(el)*math.sin(az),math.sin(el)])
    axes=np.array(axes)
    bases=transport_bases(axes)
    # Choose a continuous blade roll with clearance at the authored height.
    grid=np.radians(np.arange(-180,181,5))
    rs=Rotation.from_rotvec(np.c_[np.zeros((len(grid),2)),grid]).as_matrix()
    costs=[]
    for c,axis,base in zip(controls,axes,bases):
        rr=np.einsum('ij,kjl->kil',base,rs)
        offset=np.einsum('pj,kij->kpi',(rig.hull-[0,0,-3])*rig.scale,rr)[:,:,2].min(axis=1)
        wanted=rig.ground+c['height']+.6+c['hand_z']-axis[2]*1.75*rig.scale*.5
        minimum=rig.ground+.028-offset
        costs.append(25*np.maximum(0,minimum-wanted)**2+.009*grid**2)
    rolls=gaussian_filter1d(dynamic_path(np.array(costs),grid,8.,math.radians(25)),.65,mode='nearest')
    reviewed_path=WORK/'reviewed_blade_before.json'
    reviewed=load(reviewed_path)
    old_rotations=np.array(reviewed['weapon_rotations'])
    assert len(old_rotations)==count
    for i,old in enumerate(old_rotations):
        old_axis=unit(old[:,2])
        cross=np.cross(old_axis,axes[i])
        angle=math.atan2(np.linalg.norm(cross),np.dot(old_axis,axes[i]))
        bases[i]=Rotation.from_rotvec(unit(cross)*angle).as_matrix() @ old
    rolls[:]=0.
    # User-reviewed blade direction: frame 75 through the end presented the
    # reverse edge. Turn the mesh around the shaft; both grips stay fixed.
    # The transition is complete before the specified interval begins.
    blade_roll_correction=np.array([math.pi*smooth((f-61)/14)
                                    for f in range(1,count+1)])
    rolls+=blade_roll_correction
    poses,adaptations=[],[]
    for i,c in enumerate(controls):
        origin=np.array([c['x'],c['y'],rig.ground+c['height']])
        pelvis=rotation(z=c['yaw'])
        pose={'Bone.011':affine(pelvis,origin)}
        for side,sign in (('Left',1),('Right',-1)):
            hip=origin+pelvis @ [sign*.2,0,0]
            hint=hip+pelvis @ [sign*.05,-.58,-.25]
            solve_grounded_leg(rig,pose,side,feet[i],hint)
        body_r=rotation(x=c['lean']*.48,y=c['side']*.48,z=c['yaw']+c['twist']*.48)
        chest_r=rotation(x=c['lean'],y=c['side'],z=c['yaw']+c['twist'])
        pose['Body']=affine(body_r @ rig.rest['Body'][:3,:3],origin)
        chest=origin+body_r @ [0,0,.6]
        pose['Chest']=affine(chest_r @ rig.rest['Chest'][:3,:3],chest)
        # Head follows the entire unwrapped body turn instead of world yaw*.2.
        pose['Head']=affine(rotation(x=max(0.,c['lean']*.23),y=c['side']*.2,
                                    z=c['yaw']+c['twist']*.85) @ rig.rest['Head'][:3,:3],
                            chest+chest_r @ [0,0,.6])
        rsh=chest+chest_r @ [-.5,-.3,.4]
        lsh=chest+chest_r @ [.5,-.3,.4]
        previous=copy.deepcopy(rig.previous_grasp)
        errors=[];solved=None
        # Preserve the heading and attack plane. Only small upward corrections
        # are allowed when the long physical mesh would meet the floor.
        for lift in (0.,.035,.07,.12,.18):
            el=math.radians(c['elevation'])+lift
            az=math.radians(c['yaw']+c['twist']+c['shaft'])
            axis=np.array([math.cos(el)*math.cos(az),math.cos(el)*math.sin(az),math.sin(el)])
            cross=np.cross(axes[i],axis)
            delta=math.atan2(np.linalg.norm(cross),np.dot(axes[i],axis))
            rr=Rotation.from_rotvec(unit(cross)*delta).as_matrix() @ bases[i] @ rotation(z=math.degrees(rolls[i]))
            separation=rr[:,2]*1.75*rig.scale
            for reach in (c['reach'],.58,.72):
                center=chest+chest_r @ [0,-reach,c['hand_z']]
                wanted=center-separation*.5
                rig.previous_grasp=copy.deepcopy(previous)
                try:
                    hand=rig.fit_weapon(pose,rr,wanted,rsh,lsh,-3.,-1.25,i)
                    solved=(rr,separation,hand,lift,reach);break
                except RuntimeError as error:
                    errors.append(str(error))
            if solved:
                break
        if solved is None:
            write(WORK/'failure.json',dict(frame=i,control=c,errors=errors))
            raise RuntimeError(('Unsolved turning grip',i,errors[-3:]))
        rr,separation,hand,lift,reach=solved
        rig.current_frame=i
        for side,sign,target in (('Right',-1,hand),('Left',1,hand+separation)):
            # Capture elbow height supplies a restrained accent. The bend pole
            # rotates with the chest through back-facing and side-on poses.
            p=sample(capture['points'],(c['reference_frame']-1)*2)
            elbow=14 if side=='Right' else 13
            accent=float(np.clip(p[elbow,2]-p[[11,12],2].mean(),-.5,.4))*.15
            hint=chest+chest_r @ [sign*.87,-.34,-.08+accent+c['hand_z']*.25]
            palm=rig.arm(pose,side,target,chest_r,hint)
            assert np.linalg.norm(palm-target)<1e-5
        pose['Weapon:Scythe']=affine(rr*rig.scale,hand+rr[:,2]*3*rig.scale)
        poses.append(pose)
        adaptations.append(dict(frame=i+1,root_yaw_degrees=c['yaw'],
                                chest_yaw_degrees=c['yaw']+c['twist'],elevation_correction_degrees=math.degrees(lift),
                                reviewed_blade_roll_degrees=math.degrees(blade_roll_correction[i])))
        if i%24==0:
            print('TURN_RETARGET',i,'/',count,'cut',c['segment'],'body',round(c['yaw'],1),flush=True)
    stabilize_limb_roll(poses)
    notes=('Google MediaPipe GHUM Heavy capture of 0.5x BV1JH4y1r7po, reused with explicit source-frame correspondence. '
           'Visually reviewed heading keys unwrap the pelvis/chest/head turns lost under monocular occlusion. '
           'Per-cut authored blade planes and planted pivot steps adapt the source to the block skeleton; '
           '3D depth and grip cleanup are edited, not claimed as exact monocular reconstruction.')
    data.update(action='HB_Scythe_MediaPipe_Turning_07',frames=list(range(1,count+1)),
                poses=[{n:m.tolist() for n,m in row.items()} for row in poses],
                attack_segments=segments,footwork=feet,duration_seconds=(count-1)/60,
                grip_local_z=[-3.]*count,left_grip_local_z=[-1.25]*count,support_hand_weight=[1.]*count,
                release_interval_source_frames=[],method=notes,
                markers=[dict(frame=s['first_frame'],name=s['label']) for s in segments])
    data['combat_cleanup']=dict(version=VERSION,notes=notes,reference_playback_rate=.5,
                                capture_work=str(WORK),capture_origin=str(CAPTURE),
                                capture_sha256=hashlib.sha256((WORK/'capture_selected.npz').read_bytes()).hexdigest(),
                                blade_direction_correction=dict(frames=[75,count],degrees=180,transition_frames=[[61,75]]),
                                reviewed_blade_sha256=hashlib.sha256(reviewed_path.read_bytes()).hexdigest(),
                                turning_cuts=[s['name'] for s in segments if s['full_body_turn']])
    path=OUT/'capture/target_motion.json'
    write(path,data,True)
    write(OUT/'capture/turning_report.json',dict(version=VERSION,frames=count,
          motion_sha256=hashlib.sha256(path.read_bytes()).hexdigest(),notes=notes,
          capture_origin=str(CAPTURE),capture_sha256=data['combat_cleanup']['capture_sha256'],
          segments=segments,controls=controls,adaptations=adaptations,grip_records=rig.grasp_records))
    print('TURN_RETARGET_COMPLETE',count,flush=True)


if __name__=='__main__':
    main()
