"""Retarget the new half-speed Google capture, preserving each cut's pose path.

The source is single-camera footage. Facing, fixed two-hand grips and world
travel are combat adaptations, while every cut uses a different captured path.
"""
import hashlib
import json
import math
from pathlib import Path
import numpy as np
from scipy.interpolate import PchipInterpolator
from scipy.ndimage import gaussian_filter1d
from scipy.spatial.transform import Rotation
from combat_cleanup import CombatRig, PROFILE, affine, rotation, smoothstep, two_bone, unit, stabilize_limb_roll
from reviewed_weapon_capture import capture_axes

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_recapture_05'
OUT=ROOT/'output/Herobrine_Scythe_Recapture_05'
FPS=60
# Label, source start/end, output intervals, source contact start/end, blocks.
# The first 0.5 seconds of overhead preparation and the later kicks/pole-vaults
# are excluded. The low cut includes the actual strike at source 164-168.
CUTS=[
    ('交叉进步·右上斜劈',15,28,28,16,20,.36),
    ('反身换步·反手挑斩',28,49,30,33,40,.34),
    ('开步转胯·前送横扫',49,65,28,52,58,.42),
    ('交步回胯·反向腰斩',65,82,28,69,76,.36),
    ('沉身跨步·突进横斩',82,110,36,92,101,.72),
    ('弓步追击·上挑回斩',110,128,30,115,123,.42),
    ('提膝踏进·纵向劈斩',128,148,30,132,141,.44),
    ('压低重心·贴地扫斩',148,170,34,164,168,.40),
]


def dump(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,allow_nan=False,separators=(',',':'))+'\n',encoding='utf-8')


def sample(array,f):
    a=int(np.floor(f));b=min(a+1,len(array)-1);u=f-a
    return array[a]*(1-u)+array[b]*u


def bounded_turn(angle):
    return np.arcsin(np.sin(angle))


def main():
    cap=np.load(WORK/'capture_selected.npz')
    captured=cap['points']
    axes=capture_axes()
    profile=json.loads(PROFILE.read_text(encoding='utf-8'))
    original=json.loads((ROOT/'output/Herobrine_Scythe_MediaPipe/capture/target_motion.json').read_text(encoding='utf-8'))
    rig=CombatRig(profile,original)
    rig.grasp_smoothing=.12
    rig.max_grip_seeds=3
    rig.grip_iterations=70
    hip_axis=captured[:,23]-captured[:,24]
    shoulder_axis=captured[:,11]-captured[:,12]
    hip_heading=gaussian_filter1d(np.unwrap(np.arctan2(hip_axis[:,1],hip_axis[:,0])),2.)
    chest_heading=gaussian_filter1d(np.unwrap(np.arctan2(shoulder_axis[:,1],shoulder_axis[:,0])),2.)
    # Work in each captured pelvis frame to retain cross-steps and knee folds
    # without copying the performer's repeated full turns around the camera.
    relative=[]
    for p,h in zip(captured,hip_heading):
        relative.append(p@rotation(z=-math.degrees(h)).T)
    relative=gaussian_filter1d(relative,1.1,axis=0)
    start_heading=chest_heading[2*CUTS[0][1]]
    twist=bounded_turn(chest_heading-hip_heading)
    chest_yaw=gaussian_filter1d(np.clip(.50*np.sin(chest_heading-start_heading)+.45*twist,-.85,.85),2.)
    pelvis_yaw=gaussian_filter1d(.30*np.sin(hip_heading-hip_heading[2*CUTS[0][1]]),2.)

    poses,footwork,segments,adaptations=[],[],[],[]
    contacts={s:None for s in ('Left','Right')}
    stepping={}
    previous_chest=None
    previous_rotation=None
    traveled=0.;start=1
    for ci,(label,first,last,count,hit_a,hit_b,blocks) in enumerate(CUTS):
        contact_a,contact_b=round(count*.26),round(count*.58)
        retime=PchipInterpolator([0,contact_a,contact_b,count],[first,hit_a,hit_b,last])
        source_frames=np.array([float(retime(t)) for t in range(count+1)])
        feet_track=np.array([sample(relative,f*2)[[27,28]] for f in source_frames])
        pace=np.linalg.norm(np.diff(feet_track[:,:,[0,1]],axis=0),axis=2).mean(axis=1)
        pace=gaussian_filter1d(pace,1.4)+.008
        pace*=np.interp(np.arange(count)+.5,[0,contact_a,contact_b,count],[.2,.9,1.,.1])
        progress=np.r_[0,np.cumsum(pace)];progress/=progress[-1]
        segments.append({'name':f'combo_{ci+1:02d}','label':label,'first_frame':start,'last_frame':start+count,
                         'contact_frames':[(start+contact_a,start+contact_b)],'recovery_frame':start+count-3,
                         'reference_frames':[first+1,last+1],'travel_blocks':blocks})
        for tick,f in enumerate(source_frames):
            if ci and tick==0:continue
            index=len(poses)
            p=sample(relative,f*2)
            up=p[[11,12]].mean(axis=0)-p[[23,24]].mean(axis=0)
            # Measured torso compression and side lean survive the facing
            # correction. Deep lunge/low-cut poses therefore remain different.
            lean=float(np.clip(math.atan2(np.linalg.norm(up[:2]),up[2]),.04,.90))
            side_lean=float(np.clip(-math.atan2(up[0],up[2]),-.22,.22))
            yaw=float(sample(chest_yaw,f*2))
            pyaw=float(sample(pelvis_yaw,f*2))
            pelvis_r=rotation(z=math.degrees(pyaw))
            body_r=rotation(x=math.degrees(lean*.45),y=math.degrees(side_lean*.45),z=math.degrees(yaw*.4))
            raw_chest_r=rotation(x=math.degrees(lean),y=math.degrees(side_lean),z=math.degrees(yaw))
            axis=unit(sample(axes,f))
            rel_feet={};knee_hints={}
            for side,hi,kn,an,sign in [('Left',23,25,27,1),('Right',24,26,28,-1)]:
                u,v=unit(p[kn]-p[hi]),unit(p[an]-p[kn])
                rel_feet[side]=pelvis_r@(np.array([sign*.2,0,0])+u*.6+v*.6)
                knee_hints[side]=pelvis_r@(np.array([sign*.2,0,0])+u*.6)
                horizontal=np.linalg.norm(rel_feet[side][:2])
                if horizontal>.94:rel_feet[side][:2]*=.94/horizontal
            lowest=min(v[2] for v in rel_feet.values())
            hip_height=float(np.clip(-lowest+.105,.43,1.14))
            origin=np.array([0.,-(traveled+blocks*progress[tick])/(10/16),rig.ground+hip_height])
            desired={s:origin+v for s,v in rel_feet.items()}
            lifts={s:float(np.clip(v[2]-lowest-.025,0.,.46)) for s,v in rel_feet.items()}
            for side in contacts:
                other='Right' if side=='Left' else 'Left'
                if other in stepping:
                    lifts[side]=0.
                if side in stepping:
                    step=stepping[side];u=(index-step['start'])/9
                    if u>=1:
                        contacts[side]=step['end'].copy();del stepping[side]
                        desired[side][:2]=contacts[side]
                    else:
                        desired[side][:2]=step['from']+(step['end']-step['from'])*smoothstep(u)
                        lifts[side]=max(lifts[side],.17*math.sin(math.pi*u)**2)
                        continue
                if lifts[side]>.035:
                    contacts[side]=None
                else:
                    lifts[side]=0.
                    if contacts[side] is None:contacts[side]=desired[side][:2].copy()
                    debt=np.linalg.norm(desired[side][:2]-contacts[side])
                    if debt>.26 and other not in stepping and lifts[other]<.035:
                        stepping[side]={'start':index,'from':contacts[side].copy(),'end':desired[side][:2].copy()}
                    desired[side][:2]=contacts[side]
            # At least one supporting foot carries the forward impulse. This
            # also adapts the blurred airborne dash to a grounded combat lunge.
            if min(lifts.values())>.001:
                support=min(lifts,key=lifts.get)
                if support in stepping:support='Right' if support=='Left' else 'Left'
                lifts[support]=0.
                if contacts[support] is None:contacts[support]=desired[support][:2].copy()
                desired[support][:2]=contacts[support]
            for side,sign in [('Left',1),('Right',-1)]:
                hip_xy=(origin+pelvis_r@[sign*.2,0,0])[:2]
                reach=np.linalg.norm(desired[side][:2]-hip_xy)
                if reach>1.12:
                    desired[side][:2]=hip_xy+(desired[side][:2]-hip_xy)*(1.12/reach)
                    contacts[side]=None;lifts[side]=max(lifts[side],.04)
                maximum=.10+lifts[side]+math.sqrt(max(.02,1.17**2-min(reach,1.12)**2))
                origin[2]=min(origin[2],rig.ground+maximum-.02)
            pose={'Bone.011':affine(pelvis_r,origin),'Body':affine(body_r@rig.rest['Body'][:3,:3],origin)}
            chest=origin+body_r@[0.,0.,.6]
            for side,sign in [('Left',1),('Right',-1)]:
                hip=origin+pelvis_r@[sign*.2,0,0]
                goal=desired[side].copy();goal[2]=rig.ground+.13+lifts[side]
                hint=origin+knee_hints[side]
                for _ in range(16):
                    upper,lower,end=two_bone(hip,goal,hint,.6,.6,pelvis_r[:,0])
                    height=.2*(abs(lower[2,0])+abs(lower[2,2]))
                    goal[2]=rig.ground+.012+height+lifts[side]
                pose['Leg:'+side+':Upper']=upper;pose['Leg:'+side+':Lower']=lower
                desired[side][:2]=end[:2]

            arm_span=np.mean([np.linalg.norm(p[13]-p[11])+np.linalg.norm(p[15]-p[13]),
                              np.linalg.norm(p[14]-p[12])+np.linalg.norm(p[16]-p[14])])
            capture_hand_delta=(p[[15,16]].mean(axis=0)-p[[11,12]].mean(axis=0))*(.88/max(.35,arm_span))
            saved_grasp=rig.previous_grasp
            solved=None;failures=[]
            # The target's long blade and wide shoulders can require extra
            # depth/torso turn. Resolve them together BEFORE fitting the grips.
            candidates=[]
            for shrink in [1.,.92,.82,.70,.58]:
                trial_axis=axis.copy();trial_axis[[0,2]]*=shrink
                trial_axis[1]=-math.sqrt(max(0.,1-trial_axis[0]**2-trial_axis[2]**2))
                separation=trial_axis*1.75*rig.scale
                for correction in range(-85,86,5):
                    cr=rotation(z=correction)@raw_chest_r
                    if np.linalg.norm(cr@[1.,0.,0.]-separation)>1.70:continue
                    actual_yaw=math.atan2(cr[1,0],cr[0,0])
                    if abs(actual_yaw)>math.radians(87):continue
                    cost=(correction/70)**2+(1-shrink)*3.
                    if previous_chest is not None:
                        cost+=(actual_yaw-previous_chest)**2*2.
                    candidates.append((cost,shrink,correction,cr,trial_axis))
            candidates.sort(key=lambda c:c[0])
            for _,shrink,correction,chest_r,trial_axis in candidates[:45]:
                pose['Chest']=affine(chest_r@rig.rest['Chest'][:3,:3],chest)
                head=chest+chest_r@[0.,0.,.6]
                pose['Head']=affine(rotation(x=6.,z=math.degrees(yaw)*.16)@rig.rest['Head'][:3,:3],head)
                right_sh=chest+chest_r@[-.50,-.30,.4]
                left_sh=chest+chest_r@[.50,-.30,.4]
                separation=trial_axis*1.75*rig.scale
                yy=unit(np.array([0.,1.,0.])-trial_axis*trial_axis[1],(0.,0.,1.))
                xx=unit(np.cross(yy,trial_axis))
                base_rotation=np.column_stack([xx,np.cross(trial_axis,xx),trial_axis])
                center=(right_sh+left_sh)/2+chest_r@capture_hand_delta
                center[0]=np.clip(center[0],origin[0]-.45,origin[0]+.45)
                center[1]=min(center[1],origin[1]-.60)
                center[2]=np.clip(center[2],chest[2]+.04,chest[2]+.70)
                wanted=center-separation/2
                rolls=[0.,30.,-30.,60.,-60.,90.,-90.,180.]
                if previous_rotation is not None:
                    rolls.sort(key=lambda roll:Rotation.from_matrix(previous_rotation.T@base_rotation@rotation(z=roll)).magnitude())
                for roll in rolls[:4]:
                    rr=base_rotation@rotation(z=roll)
                    rig.previous_grasp=saved_grasp
                    try:
                        hand=rig.fit_weapon(pose,rr,wanted,right_sh,left_sh,-3.,-1.25,index)
                    except RuntimeError as error:
                        failures.append(str(error));continue
                    solved=(rr,hand,chest_r,separation,shrink,correction,roll,wanted)
                    break
                if solved is not None:break
            if solved is None:
                dump(WORK/'failed_pose.json',{'frame':index,'source_frame':float(f),'axis':axis.tolist(),'failures':failures[-15:],
                     'pose':{n:m.tolist() for n,m in pose.items()}})
                raise RuntimeError(f'Capture grip adaptation failed at {index}, source {f}: {failures[-1:]}')
            rr,hand,chest_r,separation,shrink,correction,roll,wanted=solved
            rig.current_frame=index
            right_hint=chest+chest_r@(np.array([-.50,-.30,.4])+(p[14]-p[12])*(.4/max(.1,np.linalg.norm(p[14]-p[12]))))
            left_hint=chest+chest_r@(np.array([.50,-.30,.4])+(p[13]-p[11])*(.4/max(.1,np.linalg.norm(p[13]-p[11]))))
            palm=rig.arm(pose,'Right',hand,chest_r,right_hint)
            rig.arm(pose,'Left',palm+separation,chest_r,left_hint)
            pose['Weapon:Scythe']=affine(rr*rig.scale,palm+rr[:,2]*3.*rig.scale)
            previous_chest=math.atan2(chest_r[1,0],chest_r[0,0]);previous_rotation=rr
            poses.append(pose)
            footwork.append({'foot_lx':float(desired['Left'][0]),'foot_ly':float(desired['Left'][1]),
                             'foot_rx':float(desired['Right'][0]),'foot_ry':float(desired['Right'][1]),
                             'foot_l_lift':lifts['Left'],'foot_r_lift':lifts['Right'],'segment':ci+1,'reference_frame':float(f+1),
                             'captured_body_yaw':yaw,'chest_grip_correction_degrees':correction})
            adaptations.append({'frame':index,'source_frame':float(f),'grip_translation':float(np.linalg.norm(hand-wanted)),
                                'chest_correction_degrees':correction,'projected_shaft_scale':shrink,'blade_roll_degrees':roll})
            if index%12==0:print('RECAPTURE_RETARGET',index,'source',round(f,2),'cut',ci+1,'depth',shrink,'torso',correction,flush=True)
        traveled+=blocks;start+=count
        print('RECAPTURE_CUT_DONE',ci+1,label,flush=True)
    stabilize_limb_roll(poses)
    data={k:original[k] for k in ['source_url','source_author','weapon_scale','root_ground_local']}
    data.update(action='HB_Scythe_MediaPipe_Recapture_05',fps=FPS,fps_base=1,frames=list(range(1,len(poses)+1)),
                duration_seconds=(len(poses)-1)/FPS,grip_local_z=[-3.]*len(poses),left_grip_local_z=[-1.25]*len(poses),
                support_hand_weight=[1.]*len(poses),release_interval_source_frames=[],
                method='Fresh Google MediaPipe half-speed capture, reviewed OpenCV shaft measurements, per-cut retiming, fixed-grip IK and support-foot constraints.',
                limitations='Single-view depth, facing and blade roll need geometric adaptation. Forward world travel is authored against the visible footwork because the reference camera follows the performer.',
                combat_cleanup={'version':'mediapipe_recapture_05','notes':'Distinct captured cross-step, rise, horizontal sweep, reverse sweep, lunge, follow-up, knee-lift and low-cut paths. No normal-attack release, pole vault or acrobatic kick.',
                                'reference_review_playback_speed':.5,'capture_sha256':hashlib.sha256((WORK/'pose_half_speed_candidates.json').read_bytes()).hexdigest()},
                markers=[{'frame':s['first_frame'],'label':s['label']} for s in segments],attack_segments=segments,footwork=footwork,
                poses=[{n:m.tolist() for n,m in pose.items()} for pose in poses])
    dump(OUT/'capture/target_motion.json',data)
    dump(OUT/'capture/retarget_report.json',{'frames':len(poses),'duration':data['duration_seconds'],'total_travel_blocks':traveled,
         'segments':segments,'grip_adaptations':adaptations})
    print('RECAPTURE_RETARGET_COMPLETE',len(poses),data['duration_seconds'],flush=True)


if __name__=='__main__':main()
