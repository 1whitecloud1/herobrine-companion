"""Retarget distinct poses from the fresh half-speed capture, with fixed grips.

Torso, knees, arm hints and weapon directions are sampled from this capture.
No shared swing-phase curve or repeated left/right step cycle is substituted.
"""
import hashlib
import json
import math
from pathlib import Path
import numpy as np
from scipy.interpolate import PchipInterpolator
from scipy.ndimage import gaussian_filter1d
from scipy.spatial.transform import Rotation
from combat_cleanup import CombatRig, PROFILE, affine, basis, rotation, smoothstep, two_bone, unit, stabilize_limb_roll

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT/'build/scythe_recapture_05'
OUT = ROOT/'output/Herobrine_Scythe_Recapture_05'
FPS = 60
# Reviewed source frames are zero-based. Each cut retains its own anticipation,
# contact and follow-through, then gets compact playback timing.
CUTS = [
    ('交叉进步·右上斜劈', 0, 28, 34, 12, 22, .36),
    ('反身换步·反手挑斩', 28, 46, 27, 32, 40, .34),
    ('开步转胯·前送横扫', 46, 64, 30, 51, 59, .42),
    ('交步回胯·反向腰斩', 64, 82, 28, 68, 76, .36),
    ('沉身跨步·突进横斩', 82, 110, 38, 90, 102, .72),
    ('弓步追击·上挑回斩', 110, 128, 30, 114, 123, .42),
    ('提膝踏进·纵向劈斩', 128, 146, 34, 133, 142, .44),
    ('压低重心·贴地扫斩', 146, 165, 34, 151, 163, .40),
]


def dump(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, allow_nan=False, separators=(',',':'))+'\n', encoding='utf-8')


def torso(left, up):
    z = unit(up, (0,0,1))
    x = unit(left-z*np.dot(left,z))
    y = unit(np.cross(z,x))
    return np.column_stack([np.cross(y,z),y,z])


def weapon_axes(xy, points):
    observations = json.loads((WORK/'weapon_candidates.json').read_text())[:166]
    states=[]
    for f,obs in enumerate(observations):
        wrists=xy[f*2,[15,16]]
        gold=np.array(obs['gold_center']) if obs['gold_center'] is not None else None
        cs=[]
        for line in obs['candidates']:
            a,b=np.array(line['a']),np.array(line['b'])
            axis=unit(b-a)
            normal=np.array([-axis[1],axis[0]])
            distance=np.abs((wrists-a)@normal)
            base=-2*np.log(max(line['score'],1)/100)+min(distance.min(),40)/13
            for sign in (-1,1):
                d=axis*sign
                angle=math.atan2(-d[1],d[0])
                cost=base
                if gold is not None:
                    cost+=max(0.,-np.dot(gold-(a+b)/2,d))/10
                    cost+=min(40.,abs(np.dot(gold-a,normal)))/12
                cs.append({'d':d,'angle':angle,'cost':cost,'line':line})
        states.append(cs)
    costs,back=[],[]
    for f,cs in enumerate(states):
        unary=np.array([c['cost'] for c in cs])
        if f==0:
            costs.append(unary);back.append(None);continue
        trans=np.array([[(((a['angle']-b['angle']+np.pi)%(2*np.pi)-np.pi)**2)*1.1 for b in cs] for a in states[f-1]])
        total=costs[-1][:,None]+trans
        back.append(total.argmin(axis=0));costs.append(unary+total.min(axis=0))
    chosen=[];index=int(costs[-1].argmin())
    for f in range(len(states)-1,-1,-1):
        chosen.append(states[f][index])
        if f:index=int(back[f][index])
    chosen.reverse()
    angles=gaussian_filter1d(np.unwrap([c['angle'] for c in chosen]),.65)
    axes=[]
    for f,a in enumerate(angles):
        d=np.array([math.cos(a),-math.sin(a)])
        hand_delta=points[f*2,15]-points[f*2,16]
        projected=np.dot(xy[f*2,15]-xy[f*2,16],d)
        depth=float(np.clip(hand_delta[1]/max(.2,np.linalg.norm(hand_delta)),-.65,.65))*np.sign(projected) if abs(projected)>8 else 0.
        axes.append([d[0]*math.sqrt(1-depth*depth),depth,-d[1]*math.sqrt(1-depth*depth)])
    axes=gaussian_filter1d(axes,.55,axis=0)
    axes/=np.linalg.norm(axes,axis=1)[:,None]
    dump(WORK/'weapon_selected.json',{'source_frames':list(range(166)),'axes':axes.tolist(),'angles':angles.tolist()})
    return axes


def main():
    cap=np.load(WORK/'capture_selected.npz')
    points,xy=cap['points'],cap['xy']
    axes=weapon_axes(xy,points)
    profile=json.loads(PROFILE.read_text(encoding='utf-8'))
    original=json.loads((ROOT/'output/Herobrine_Scythe_MediaPipe/capture/target_motion.json').read_text(encoding='utf-8'))
    rig=CombatRig(profile,original)
    rig.grasp_smoothing=.35
    # Resolve the reference camera's heading once; source joint trajectories
    # remain independent across all eight attacks.
    initial=torso(points[0,11]-points[0,12], points[0,[11,12]].mean(axis=0))
    initial_yaw=math.atan2(initial[1,0],initial[0,0])
    camera_to_rig=rotation(z=-math.degrees(initial_yaw))
    def sample(array,f):
        a=int(np.floor(f));b=min(a+1,len(array)-1);u=f-a
        return array[a]*(1-u)+array[b]*u

    poses,footwork,segments,adjustments=[],[],[],[]
    contacts={s:None for s in ('Left','Right')}
    previous_lifts={s:0. for s in contacts}
    stepping={}
    traveled=0.;start=1
    for ci,(label,first,last,count,hit_a,hit_b,blocks) in enumerate(CUTS):
        contact_a,contact_b=round(count*.27),round(count*.62)
        retime=PchipInterpolator([0,contact_a,contact_b,count],[first,hit_a,hit_b,last])
        source_frames=np.array([float(retime(t)) for t in range(count+1)])
        # Movement timing follows the captured foot changes and the hit beat.
        feet_track=np.array([sample(points,f*2)[[27,28]] for f in source_frames])
        pace=np.linalg.norm(np.diff(feet_track[:,:,[0,1]],axis=0),axis=2).mean(axis=1)
        pace=gaussian_filter1d(pace,1.)+.004
        pace*=np.interp(np.arange(count)+.5,[0,contact_a,contact_b,count],[.2,1.,1.,.2])
        progress=np.r_[0,np.cumsum(pace)];progress/=progress[-1]
        segments.append({'name':f'combo_{ci+1:02d}','label':label,'first_frame':start,'last_frame':start+count,
                         'contact_frames':[(start+contact_a,start+contact_b)],'recovery_frame':start+count-3,
                         'reference_frames':[first+1,last+1],'travel_blocks':blocks})
        for tick,f in enumerate(source_frames):
            if ci and tick==0:continue
            index=len(poses)
            p=sample(points,f*2)@camera_to_rig.T
            up=p[[11,12]].mean(axis=0)-p[[23,24]].mean(axis=0)
            raw_chest=torso(p[11]-p[12],up)
            raw_yaw=math.atan2(raw_chest[1,0],raw_chest[0,0])
            # Single-camera back/front ambiguity is wrapped to the facing
            # hemisphere; keep the measured bend and side lean.
            yaw=(raw_yaw+math.pi/2)%math.pi-math.pi/2
            facing=rotation(z=math.degrees(np.clip(yaw,-1.15,1.15)-raw_yaw))
            p=p@facing.T
            up=p[[11,12]].mean(axis=0)-p[[23,24]].mean(axis=0)
            chest_r=torso(p[11]-p[12],up)
            body_r=torso((p[23]-p[24])*.65+(p[11]-p[12])*.35,up)
            hip_yaw=math.atan2((p[23]-p[24])[1],(p[23]-p[24])[0])
            hip_yaw=(hip_yaw+math.pi/2)%math.pi-math.pi/2
            pelvis_r=rotation(z=math.degrees(np.clip(hip_yaw,-.70,.70)))
            axis=unit(facing@camera_to_rig@sample(axes,f))
            active=contact_a<=tick<=contact_b
            if active and axis[1]>-.18:
                axis=unit(axis+[0.,-.18-axis[1],0.])
            # Adapt the long target shaft by estimating its unobserved depth;
            # do not replace its captured image-plane path with a swing template.
            rel_feet={};knee_hints={}
            for side,hi,kn,an,sign in [('Left',23,25,27,1),('Right',24,26,28,-1)]:
                u,v=unit(p[kn]-p[hi]),unit(p[an]-p[kn])
                rel_feet[side]=pelvis_r@[sign*.2,0,0]+u*.6+v*.6
                knee_hints[side]=pelvis_r@[sign*.2,0,0]+u*.6
            lowest=min(v[2] for v in rel_feet.values())
            hip_height=np.clip(-lowest+.14,.52,1.13)
            origin=np.array([0.,-(traveled+blocks*progress[tick])/(10/16),rig.ground+hip_height])
            desired={s:origin+v for s,v in rel_feet.items()}
            lifts={s:float(np.clip(v[2]-lowest,0.,.48)) for s,v in rel_feet.items()}
            # Lock observed support feet; relocate them only on a captured lift
            # or when root travel has exhausted a planted leg's reach.
            for side in contacts:
                other='Right' if side=='Left' else 'Left'
                if side in stepping:
                    step=stepping[side];u=(index-step['start'])/8
                    if u>=1:
                        contacts[side]=step['end'].copy();del stepping[side]
                        desired[side][:2]=contacts[side]
                    else:
                        desired[side][:2]=step['from']+(step['end']-step['from'])*smoothstep(u)
                        lifts[side]=max(lifts[side],.14*math.sin(math.pi*u)**2)
                        continue
                if lifts[side]>.045:
                    contacts[side]=None
                else:
                    lifts[side]=0.
                    if contacts[side] is None:
                        contacts[side]=desired[side][:2].copy()
                    debt=np.linalg.norm(desired[side][:2]-contacts[side])
                    if debt>.28 and other not in stepping and lifts[other]<.045:
                        stepping[side]={'start':index,'from':contacts[side].copy(),'end':desired[side][:2].copy()}
                    desired[side][:2]=contacts[side]
            # Support constraints can lower the captured pelvis slightly; they
            # never translate planted feet to fake a step.
            for side,sign in [('Left',1),('Right',-1)]:
                hip_xy=(origin+pelvis_r@[sign*.2,0,0])[:2]
                reach=np.linalg.norm(desired[side][:2]-hip_xy)
                if reach>1.12:
                    desired[side][:2]=hip_xy+(desired[side][:2]-hip_xy)*(1.12/reach)
                    contacts[side]=None;lifts[side]=max(lifts[side],.06)
                maximum=.13+lifts[side]+math.sqrt(max(.02,1.18**2-min(reach,1.12)**2))
                origin[2]=min(origin[2],rig.ground+maximum-.02)
            pose={'Bone.011':affine(pelvis_r,origin), 'Body':affine(body_r@rig.rest['Body'][:3,:3],origin)}
            chest=origin+body_r@[0.,0.,.6]
            # Keep the measured torso unless the fixed two-hand span needs a
            # small additional shoulder turn to remain physically reachable.
            separation=axis*((-1.25+3.)*rig.scale)
            best=None
            for correction in [0]+[s*d for d in range(5,91,5) for s in (-1,1)]:
                trial=rotation(z=correction)@chest_r
                if np.linalg.norm(trial@[1.,0.,0.]-separation)<1.72:
                    best=(correction,trial);break
            assert best is not None,(index,axis)
            chest_correction,chest_r=best
            pose['Chest']=affine(chest_r@rig.rest['Chest'][:3,:3],chest)
            head=chest+chest_r@[0.,0.,.6]
            pose['Head']=affine(rotation(x=8.,z=math.degrees(yaw)*.2)@rig.rest['Head'][:3,:3],head)
            for side,sign in [('Left',1),('Right',-1)]:
                hip=origin+pelvis_r@[sign*.2,0,0]
                goal=desired[side].copy();goal[2]=rig.ground+.13+lifts[side]
                hint=origin+knee_hints[side]
                for _ in range(12):
                    upper,lower,end=two_bone(hip,goal,hint,.6,.6,pelvis_r[:,0])
                    height=.2*(abs(lower[2,0])+abs(lower[2,2]))
                    goal[2]=rig.ground+.012+height+lifts[side]
                pose['Leg:'+side+':Upper']=upper;pose['Leg:'+side+':Lower']=lower
                desired[side][:2]=end[:2]
            right_sh=chest+chest_r@[-.50,-.30,.4]
            left_sh=chest+chest_r@[.50,-.30,.4]
            min_z=-min(.94,(min(right_sh[2],left_sh[2])-rig.ground+.50)/(4.69*rig.scale))
            if axis[2]<min_z:
                axis*=min_z/axis[2]
                axis[1]=-math.sqrt(max(0.,1-axis[0]**2-axis[2]**2))
                axis=unit(axis)
                separation=axis*((-1.25+3.)*rig.scale)
            normal=unit(facing@camera_to_rig@[0.,-1.,0.])
            normal=unit(normal-axis*np.dot(normal,axis),chest_r[:,0])
            rr=np.column_stack([normal,np.cross(axis,normal),axis])
            arm_span=np.mean([np.linalg.norm(p[13]-p[11])+np.linalg.norm(p[15]-p[13]),
                              np.linalg.norm(p[14]-p[12])+np.linalg.norm(p[16]-p[14])])
            center=(left_sh+right_sh)/2+(p[[15,16]].mean(axis=0)-p[[11,12]].mean(axis=0))*(.88/max(.35,arm_span))
            center[1]=min(center[1],origin[1]-.42)
            wanted=center-separation/2
            # Fit translation and blade roll, with both contacts fixed on one shaft.
            solved=None
            saved_grasp=rig.previous_grasp
            for roll in [0.,180.,90.,-90.]:
                attempt=rr@rotation(z=roll)
                rig.previous_grasp=saved_grasp
                try:
                    hand=rig.fit_weapon(pose,attempt,wanted,right_sh,left_sh,-3.,-1.25,index)
                    solved=(attempt,hand);break
                except RuntimeError:
                    continue
            if solved is None:
                dump(WORK/'failed_pose.json',{'frame':index,'source_frame':f,'axis':axis.tolist(),'pose':{n:m.tolist() for n,m in pose.items()}})
                raise RuntimeError(f'Capture grip adaptation failed at {index}, source {f}')
            rr,hand=solved
            rig.current_frame=index
            palm=rig.arm(pose,'Right',hand,chest_r)
            rig.arm(pose,'Left',palm+separation,chest_r)
            pose['Weapon:Scythe']=affine(rr*rig.scale,palm-rr[:,2]*(-3.)*rig.scale)
            poses.append(pose)
            footwork.append({'foot_lx':float(desired['Left'][0]),'foot_ly':float(desired['Left'][1]),
                             'foot_rx':float(desired['Right'][0]),'foot_ry':float(desired['Right'][1]),
                             'foot_l_lift':lifts['Left'],'foot_r_lift':lifts['Right'],
                             'segment':ci+1,'reference_frame':float(f+1),
                             'captured_body_yaw':float(yaw),'chest_grip_correction_degrees':chest_correction})
            adjustments.append({'frame':index,'source_frame':float(f),'grip_translation':float(np.linalg.norm(hand-wanted)),
                                'chest_correction_degrees':chest_correction})
            if index%20==0:print('RECAPTURE_RETARGET',index,'source',round(f,2),'segment',ci+1,flush=True)
        traveled+=blocks;start+=count
        print('RECAPTURE_CUT_DONE',ci+1,label,flush=True)
    stabilize_limb_roll(poses)
    data={k:original[k] for k in ['source_url','source_author','weapon_scale','root_ground_local']}
    data.update(action='HB_Scythe_MediaPipe_Recapture_05',fps=FPS,fps_base=1,frames=list(range(1,len(poses)+1)),
                duration_seconds=(len(poses)-1)/FPS,grip_local_z=[-3.]*len(poses),left_grip_local_z=[-1.25]*len(poses),
                support_hand_weight=[1.]*len(poses),release_interval_source_frames=[],
                method='Fresh Google MediaPipe half-speed capture; per-cut retiming; captured torso/limb/shaft trajectories; coupled fixed-grip IK and support-foot constraints.',
                limitations='Single-view depth and weapon roll require geometric adaptation. Forward world travel is authored to match visible steps, because the camera follows the performer.',
                combat_cleanup={'version':'mediapipe_recapture_05','notes':'Fresh half-speed capture with distinct cross-step, hip-turn, lunge, rising cut, knee-lift down-cut and low-sweep poses. No normal-attack pole vault or release.',
                                'reference_review_playback_speed':.5,'capture_sha256':hashlib.sha256((WORK/'pose_half_speed_candidates.json').read_bytes()).hexdigest()},
                markers=[{'frame':s['first_frame'],'label':s['label']} for s in segments],attack_segments=segments,footwork=footwork,
                poses=[{n:m.tolist() for n,m in pose.items()} for pose in poses])
    dump(OUT/'capture/target_motion.json',data)
    dump(OUT/'capture/retarget_report.json',{'frames':len(poses),'duration':data['duration_seconds'],'total_travel_blocks':traveled,'segments':segments,'grip_adaptations':adjustments})
    print('RECAPTURE_RETARGET_COMPLETE',len(poses),data['duration_seconds'],flush=True)


if __name__=='__main__':main()
