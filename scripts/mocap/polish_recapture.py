"""Repair occluded foot transitions and blade roll in the new capture retarget.

The source intervals and independent captured swing paths remain intact. This
pass constrains support contacts, interpolates each observed step between its
own two contacts, and plans a continuous blade roll before solving the grips.
"""
import json,math,shutil
from pathlib import Path
import numpy as np
from scipy.ndimage import gaussian_filter1d,minimum_filter1d
from scipy.interpolate import PchipInterpolator
from scipy.spatial.transform import Rotation,Slerp
from combat_cleanup import CombatRig,PROFILE,affine,rotation,unit,two_bone,smoothstep,stabilize_limb_roll
from reviewed_weapon_capture import capture_axes

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_recapture_05'
OUT=ROOT/'output/Herobrine_Scythe_Recapture_05'
PATH=OUT/'capture/target_motion.json'
BASE=WORK/'unpolished_motion.json'
if not BASE.exists():shutil.copy2(PATH,BASE)
data=json.loads(BASE.read_text(encoding='utf-8'))
profile=json.loads(PROFILE.read_text(encoding='utf-8'))
rig=CombatRig(profile,data)
rig.grasp_smoothing=.20;rig.max_grip_seeds=2;rig.grip_iterations=60
old=[{n:np.array(v) for n,v in row.items()} for row in data['poses']]
# Give the dash's actual 94-98 contact (including its very fast frame 97)
# the complete hit window. Earlier motion blur is anticipation, not a slash.
dash=data['attack_segments'][4]
da,db=dash['first_frame']-1,dash['last_frame']-1
original_refs=np.array([r['reference_frame']-1 for r in data['footwork'][da:db+1]])
curve=PchipInterpolator([0,9,21,db-da],[82,94,98,110])
old_poses=old[da:db+1]
old_feet=[dict(r) for r in data['footwork'][da:db+1]]
for k in range(db-da+1):
    ref=float(curve(k));fraction=float(np.interp(ref,original_refs,np.arange(len(original_refs))))
    a=int(fraction);b=min(a+1,len(old_poses)-1);u=fraction-a
    row={}
    for name in old_poses[a]:
        scale=rig.scale if name=='Weapon:Scythe' else 1.
        ra,rb=[p[name][:3,:3]/scale for p in (old_poses[a],old_poses[b])]
        rr=Slerp([0.,1.],Rotation.from_matrix([ra,rb]))(u).as_matrix()
        row[name]=affine(rr*scale,old_poses[a][name][:3,3]*(1-u)+old_poses[b][name][:3,3]*u)
    old[da+k]=row
    for name in ['foot_lx','foot_ly','foot_rx','foot_ry','foot_l_lift','foot_r_lift']:
        data['footwork'][da+k][name]=float(old_feet[a][name]*(1-u)+old_feet[b][name]*u)
    data['footwork'][da+k]['reference_frame']=ref+1
data['combat_cleanup']['dash_contact_source_frames']=[95,99]
count=len(old)
root=np.array([p['Bone.011'][:3,3] for p in old])
cap=np.load(WORK/'capture_selected.npz')
refs=np.array([r['reference_frame']-1 for r in data['footwork']])
def sample(a,f):
    i=int(f);j=min(i+1,len(a)-1);return a[i]*(1-f%1)+a[j]*(f%1)
def ranges(mask):
    edges=np.r_[0,np.flatnonzero(mask[1:]!=mask[:-1])+1,len(mask)]
    return [(a,b,bool(mask[a])) for a,b in zip(edges,edges[1:])]
def smooth_rot(rotations,sigma):
    q=Rotation.from_matrix(rotations).as_quat()
    for i in range(1,len(q)):
        if q[i]@q[i-1]<0:q[i]*=-1
    q=gaussian_filter1d(q,sigma,axis=0,mode='nearest')
    q/=np.linalg.norm(q,axis=1)[:,None]
    return Rotation.from_quat(q).as_matrix()

# Clean each captured contact interval independently. In particular, no common
# left/right step cycle is reused across the eight clips.
feet={};steps=[]
for side,sign in [('l',1),('r',-1)]:
    lift=np.array([r[f'foot_{side}_lift'] for r in data['footwork']])
    xy=np.array([[r[f'foot_{side}x'],r[f'foot_{side}y']] for r in data['footwork']])
    air=lift>1e-8
    for a,b,on in ranges(air):
        if on and b-a<3:air[a:b]=False
    contact_ranges=[(a,b) for a,b,on in ranges(air) if not on]
    result=np.zeros_like(xy);heights=np.zeros(count)
    anchors=[]
    for a,b in contact_ranges:
        point=np.median(xy[a:b],axis=0)
        point[0]=np.clip(point[0]*.60+sign*.10,-.55,.55)
        point[1]=np.clip(point[1],root[(a+b-1)//2,1]-.65,root[(a+b-1)//2,1]+.40)
        if anchors:point[1]=min(point[1],anchors[-1][2][1])
        result[a:b]=point
        anchors.append((a,b,point))
    for a,b,on in ranges(air):
        if not on:continue
        before=next((p for aa,bb,p in reversed(anchors) if bb<=a),None)
        after=next((p for aa,bb,p in anchors if aa>=b),None)
        if before is None:
            before=xy[a].copy();before[0]=np.clip(before[0]*.6+sign*.1,-.55,.55)
        if after is None:
            after=xy[b-1].copy();after[0]=np.clip(after[0]*.6+sign*.1,-.55,.55)
        peak=float(np.clip(lift[a:b].max(),.105,.38))
        # Land one frame before the next foot can leave, giving the runtime
        # interpolator a shared supporting foot across the hand-over interval.
        start=max(0,a-1);end=min(count-1,b-1)
        for i in range(a,b):
            u=(i-start)/max(1,end-start)
            result[i]=before+(after-before)*smoothstep(u)
            heights[i]=peak*math.sin(math.pi*u)**2
        steps.append({'side':side,'first_frame':int(start+1),'last_frame':int(end+1),
                      'from':before.tolist(),'to':after.tolist(),'lift':peak})
    feet[side]={'xy':result,'lift':heights}

# A captured change of elevation drives crouch/lunge height. Smooth the noisy
# monocular depth, then respect both legs' reach without moving a planted foot.
height=[]
for f in refs:
    xy=sample(cap['xy'],f*2);scale=float(sample(cap['scale'],f*2))
    sole=max(xy[[27,29,31],1].max(),xy[[28,30,32],1].max())
    hip=xy[[23,24],1].mean()
    height.append(np.clip((sole-hip)/scale*1.60+.06,.44,1.13))
height=gaussian_filter1d(height,2.0,mode='nearest')
limit=np.full(count,1.2)
for i in range(count):
    pelvis=old[i]['Bone.011'][:3,:3]
    for side,sign in [('l',1),('r',-1)]:
        hipxy=(root[i]+pelvis@[sign*.2,0,0])[:2]
        reach=float(np.linalg.norm(feet[side]['xy'][i]-hipxy))
        if reach>=1.15:raise RuntimeError(('Footstep needs another support contact',i,side,reach))
        limit[i]=min(limit[i],.04+feet[side]['lift'][i]+math.sqrt(1.16**2-reach**2))
height=np.minimum(height,gaussian_filter1d(minimum_filter1d(limit,size=5,mode='nearest'),1.0,mode='nearest'))
root[:,2]=rig.ground+height
body_rot=smooth_rot([p['Body'][:3,:3] for p in old],1.6)
chest_rot=smooth_rot([p['Chest'][:3,:3] for p in old],1.6)
head_rot=smooth_rot([p['Head'][:3,:3] for p in old],1.6)
br=body_rot@rig.rest['Body'][:3,:3].T
cr=chest_rot@rig.rest['Chest'][:3,:3].T
chest_positions=np.array([root[i]+br[i]@[0,0,.6] for i in range(count)])

# Corrected frame-by-frame shaft observations, retaining the short fast arcs.
source_axes=capture_axes()
axes=gaussian_filter1d(np.array([sample(source_axes,f) for f in refs]),.9,axis=0,mode='nearest')
axes/=np.linalg.norm(axes,axis=1)[:,None]
bases=[]
for i,axis in enumerate(axes):
    if not i:
        yy=unit(np.array([0.,1.,0.])-axis*axis[1],(0,0,1))
    else:
        cross=np.cross(axes[i-1],axis);angle=math.atan2(np.linalg.norm(cross),np.dot(axes[i-1],axis))
        yy=Rotation.from_rotvec(unit(cross)*angle).apply(bases[-1][:,1])
        yy=unit(yy-axis*np.dot(yy,axis))
    xx=unit(np.cross(yy,axis));bases.append(np.column_stack([xx,np.cross(axis,xx),axis]))
bases=np.array(bases)
roll_angles=np.radians(np.arange(-180,180,5))
rolls=Rotation.from_rotvec(np.c_[np.zeros((len(roll_angles),2)),roll_angles]).as_matrix()
unary=np.zeros((count,len(rolls)))
for i in range(count):
    rsh=chest_positions[i]+cr[i]@[-.5,-.3,.4];lsh=chest_positions[i]+cr[i]@[.5,-.3,.4]
    sep=axes[i]*1.75*rig.scale
    rotations=np.einsum('ij,kjl->kil',bases[i],rolls)
    offsets=np.einsum('pj,kij->kpi',(rig.hull-[0,0,-3])*rig.scale,rotations)[:,:,2].min(axis=1)
    minimum=rig.ground+.028-offsets
    expected=(old[i]['Arm:Right:Lower']@[0,.48,0,1])[2]+root[i,2]-old[i]['Bone.011'][2,3]
    maximum=min(rsh[2]+.76,lsh[2]-sep[2]+.76)
    unary[i]=10*np.maximum(0,minimum-expected)**2+1000*np.maximum(0,minimum-maximum)**2+.03*roll_angles**2
delta=(roll_angles[:,None]-roll_angles[None,:]+np.pi)%(2*np.pi)-np.pi
transition=42*delta**2+np.where(abs(delta)>np.radians(25),1e5,0)
cost=unary[0]+np.where(abs(roll_angles)<1e-9,0,1e6)
backs=[]
for i in range(1,count):
    all_cost=cost[:,None]+transition
    backs.append(all_cost.argmin(axis=0));cost=unary[i]+all_cost.min(axis=0)
choice=int(cost.argmin());path=[choice]
for previous in reversed(backs):choice=int(previous[choice]);path.append(choice)
path.reverse()
roll_curve=gaussian_filter1d(np.unwrap(roll_angles[path]),1.0,mode='nearest')

poses=[];adaptations=[];previous_bends={}
for i in range(count):
    origin=root[i];pelvis=old[i]['Bone.011'][:3,:3]
    pose={'Bone.011':affine(pelvis,origin),'Body':affine(body_rot[i],origin)}
    chest=chest_positions[i]
    for side,short,sign in [('Left','l',1),('Right','r',-1)]:
        hip=origin+pelvis@[sign*.2,0,0]
        goal=np.r_[feet[short]['xy'][i],rig.ground+.12+feet[short]['lift'][i]]
        hint=hip+pelvis@[sign*.16,-.70,max(0.,.75-height[i])]
        for _ in range(18):
            direction=unit(goal-hip)
            bend=unit(hint-hip-direction*np.dot(hint-hip,direction),(0,-1,0))
            if side in previous_bends:
                prev=unit(previous_bends[side]-direction*np.dot(previous_bends[side],direction),bend)
                angle=math.atan2(np.dot(direction,np.cross(prev,bend)),np.dot(prev,bend))
                bend=Rotation.from_rotvec(direction*np.clip(angle,-.22,.22)).apply(prev)
            upper,lower,end=two_bone(hip,goal,hip+bend,.6,.6,pelvis[:,0])
            up_bend=unit(np.array([0.,0.,1.])-direction*direction[2],bend)
            for _anatomy in range(24):
                if lower[2,3]>=rig.ground+.24:break
                bend=unit(bend*.85+up_bend*.15)
                upper,lower,end=two_bone(hip,goal,hip+bend,.6,.6,pelvis[:,0])
            goal[2]=rig.ground+.012+.2*(abs(lower[2,0])+abs(lower[2,2]))+feet[short]['lift'][i]
        previous_bends[side]=lower[:3,3]-hip
        assert np.linalg.norm(end[:2]-feet[short]['xy'][i])<1e-5,(i,side,end,goal)
        pose[f'Leg:{side}:Upper']=upper;pose[f'Leg:{side}:Lower']=lower
    wanted=(old[i]['Arm:Right:Lower']@[0,.48,0,1])[:3]+(root[i]-old[i]['Bone.011'][:3,3])
    base_rr=bases[i]@rotation(z=math.degrees(roll_curve[i]))
    saved=rig.previous_grasp;solved=None;failures=[]
    candidates=[]
    for shrink in [1.,.93,.84,.74,.62]:
        axis=axes[i].copy();axis[[0,2]]*=shrink;axis[1]=-math.sqrt(max(0,1-axis[0]**2-axis[2]**2))
        diff=np.cross(axes[i],axis);angle=math.atan2(np.linalg.norm(diff),np.dot(axes[i],axis))
        rr=Rotation.from_rotvec(unit(diff)*angle).as_matrix()@base_rr
        for turn in [0,-5,5,-10,10,-20,20,-35,35,-55,55,-75,75]:
            chest_r=rotation(z=turn)@cr[i]
            sep=axis*1.75*rig.scale
            if np.linalg.norm(chest_r@[1,0,0]-sep)>1.73:continue
            score=(1-shrink)*4+(turn/55)**2
            candidates.append((score,rr,chest_r,sep,shrink,turn))
    candidates.sort(key=lambda c:c[0])
    for _,rr,chest_r,sep,shrink,turn in candidates:
        pose['Chest']=affine(chest_r@rig.rest['Chest'][:3,:3],chest)
        pose['Head']=affine(head_rot[i],chest+chest_r@[0,0,.6])
        rsh=chest+chest_r@[-.5,-.3,.4];lsh=chest+chest_r@[.5,-.3,.4]
        rig.previous_grasp=saved
        try:hand=rig.fit_weapon(pose,rr,wanted,rsh,lsh,-3.,-1.25,i)
        except RuntimeError as error:failures.append(str(error));continue
        solved=(rr,chest_r,sep,hand,shrink,turn);break
    if solved is None:
        (WORK/'polish_failure.json').write_text(json.dumps({'frame':i,'source':float(refs[i]),'failures':failures[-8:]},indent=2),encoding='utf-8')
        raise RuntimeError(('Polished grip failed',i,failures[-1:]))
    rr,chest_r,sep,hand,shrink,turn=solved
    rig.current_frame=i
    for side,sign,target in [('Right',-1,hand),('Left',1,hand+sep)]:
        # Keep the captured elbow's height/extension, with a continuous pole
        # solution to remove the same occlusion flips seen in the knees.
        old_hint=old[i][f'Arm:{side}:Lower'][:3,3]-old[i]['Chest'][:3,3]
        hint=chest+old_hint
        rig.arm(pose,side,target,chest_r,hint)
    pose['Weapon:Scythe']=affine(rr*rig.scale,hand+rr[:,2]*3*rig.scale)
    poses.append(pose)
    for short in ['l','r']:
        data['footwork'][i][f'foot_{short}x']=float(feet[short]['xy'][i,0])
        data['footwork'][i][f'foot_{short}y']=float(feet[short]['xy'][i,1])
        data['footwork'][i][f'foot_{short}_lift']=float(feet[short]['lift'][i])
    adaptations.append({'frame':i,'depth_scale':shrink,'torso_turn':turn,'planned_blade_roll':float(math.degrees(roll_curve[i]))})
    if i%16==0:print('RECAPTURE_POLISH',i,'source',round(refs[i],2),'depth',shrink,'turn',turn,flush=True)
stabilize_limb_roll(poses)
data['poses']=[{n:m.tolist() for n,m in p.items()} for p in poses]
data['combat_cleanup']['contact_polish']='Observed support intervals with interpolated swing feet; measured pelvis compression; continuous knee poles and globally planned blade roll.'
PATH.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'),allow_nan=False)+'\n',encoding='utf-8')
(OUT/'capture/footstep_polish_report.json').write_text(json.dumps({'steps':steps,'grip_adaptations':adaptations},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('RECAPTURE_POLISH_COMPLETE',len(poses),len(steps),flush=True)
