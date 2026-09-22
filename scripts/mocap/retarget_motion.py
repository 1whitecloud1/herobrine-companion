"""Retarget observed trajectories to the existing Herobrine BSS proportions."""
from pathlib import Path
import json
import math
import numpy as np
from scipy.ndimage import gaussian_filter1d, maximum_filter1d
from scipy.spatial.transform import Rotation
from scipy.optimize import least_squares
from scipy.spatial.transform import Slerp
from scipy.interpolate import PchipInterpolator
from weapon_contact import WeaponContact

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
OUT=ROOT/'output/Herobrine_Scythe_MediaPipe';OUT.mkdir(parents=True,exist_ok=True)
SOURCE=Path('E:/MCStudioDownload/work/m13525918851@163.com/Cpp/AddOn/3055269d404f439eba5592a9d4b52113/_artifacts/Herobrine_镰刀战斗改进_V6/target_profile.json')
profile=json.loads(SOURCE.read_text(encoding='utf-8'))
capture=np.load(WORK/'capture_solved.npz');p=capture['points'].copy();xy=capture['xy'].copy();N=len(p)
track=json.loads((WORK/'weapon_selection.json').read_text());wc=json.loads((WORK/'weapon_candidates.json').read_text())
rest={n:np.array(v['rest']) for n,v in profile['bones'].items()}
rw=np.array(profile['rig_world']);ground=(profile['ground']-rw[2,3])/rw[0,0]
W_SCALE=.62

def unit(v,fallback=(1,0,0)):
    d=np.linalg.norm(v)
    return np.array(v)/d if d>1e-7 else np.array(fallback,dtype=float)

def affine(r,p):
    m=np.eye(4);m[:3,:3]=r;m[:3,3]=p;return m

def basis(direction,hinge):
    y=unit(direction,(0,0,-1));x=unit(hinge-y*np.dot(hinge,y))
    z=unit(np.cross(x,y));x=np.cross(y,z)
    return np.column_stack([x,y,z])

def torso_basis(left,up):
    z=unit(up,(0,0,1));x=unit(left-z*np.dot(left,z))
    y=unit(np.cross(z,x));x=np.cross(y,z)
    return np.column_stack([x,y,z])

def smooth_rotations(mats,sigma=.85):
    q=Rotation.from_matrix(mats).as_quat()
    for i in range(1,len(q)):
        if np.dot(q[i-1],q[i])<0:q[i]*=-1
    q=gaussian_filter1d(q,sigma,axis=0,mode='nearest');q/=np.linalg.norm(q,axis=1)[:,None]
    return Rotation.from_quat(q).as_matrix()

rot=[];identity_repairs=[]
permutation=np.arange(33)
for a,b in [(1,4),(2,5),(3,6),(7,8),(9,10)]+[(j,j+1) for j in range(11,32,2)]:permutation[a],permutation[b]=b,a
for f,src in enumerate(p):
    hips=(src[23]+src[24])*.5;shoulders=(src[11]+src[12])*.5
    rr=torso_basis((src[23]-src[24])*.6+(src[11]-src[12])*.4,shoulders-hips)
    if rot:
        # A monocular detector can exchange anatomical sides when a limb is
        # occluded. Resolve its 180-degree heading ambiguity before filtering.
        flipped=rr@np.diag([-1,-1,1])
        costs=[Rotation.from_matrix(rot[-1].T@a).magnitude() for a in (rr,flipped)]
        if costs[1]+.08<costs[0]:
            rr=flipped;p[f]=src[permutation];xy[f]=xy[f,permutation]
            identity_repairs.append(f)
    rot.append(rr)
rot=smooth_rotations(np.array(rot),1.65)
p=gaussian_filter1d(p,1.05,axis=0,mode='nearest')
# User correction: the opening cut starts at the CHARACTER'S upper right
# and follows through to its lower left. Express this in the moving torso
# frame, so turns do not accidentally mirror the direction in screen space.
shaft_input=capture['shaft'].copy()
opening_frames=[0,2,4,6,8,11,14,17,20,24]
opening_angles=[-145,-135,-55,-40,-35,-5,40,105,135,135]
opening_curve=PchipInterpolator(opening_frames,np.deg2rad(opening_angles))
for f in range(29):
    a=float(opening_curve(min(f,24)))
    desired=rot[f]@unit(np.array([math.sin(a),-.30,math.cos(a)]))
    weight=1. if f<=24 else (29-f)/5
    shaft_input[f]=unit(desired*weight+shaft_input[f]*(1-weight))
direction_tracks={}
for name,a,b in [('Arm:Left:Upper',11,13),('Arm:Left:Lower',13,15),
                 ('Arm:Right:Upper',12,14),('Arm:Right:Lower',14,16),
                 ('Leg:Left:Upper',23,25),('Leg:Left:Lower',25,27),
                 ('Leg:Right:Upper',24,26),('Leg:Right:Lower',26,28)]:
    frames=[];last_x=rot[0,:,0]
    for src in p:
        m=basis(src[b]-src[a],last_x);frames.append(m);last_x=m[:,0]
    direction_tracks[name]=smooth_rotations(np.array(frames),1.25)[:,:,1]
source_leg=np.median([np.linalg.norm(src[25]-src[23])+np.linalg.norm(src[27]-src[25]) for src in p[:45]])
SCALE=1.2/source_leg
floor_px=np.array(json.loads((WORK/'floor_tracking.json').read_text(encoding='utf-8'))['floor_y'])
hipxy=capture['hipxy'];pxscale=capture['scale']
root=np.zeros((N,3))
root[:,0]=(hipxy[:,0]-hipxy[0,0])/pxscale*SCALE
root[:,2]=ground+(floor_px-hipxy[:,1])/pxscale*SCALE
root=gaussian_filter1d(root,.9,axis=0)

def arm_ik(shoulder,goal,elbow_hint):
    axis=goal-shoulder;d=np.clip(np.linalg.norm(axis),.30,.878);axis=unit(axis,(0,-1,0))
    bend=elbow_hint-shoulder-axis*np.dot(elbow_hint-shoulder,axis)
    if np.linalg.norm(bend)<.01:bend=np.cross(axis,[0,0,1])
    bend=unit(bend,(1,0,0))
    along=(.4**2-.48**2+d*d)/(2*d)
    elbow=shoulder+axis*along+bend*math.sqrt(max(0,.4**2-along*along))
    hand=shoulder+axis*d
    u,v=unit(elbow-shoulder),unit(hand-elbow)
    hinge=unit(-np.cross(u,v),np.cross(bend,axis))
    return affine(basis(u,hinge),shoulder),affine(basis(v,hinge),elbow),hand

arm_bends={}
def stable_arm_ik(side,shoulder,goal,hint):
    axis=unit(goal-shoulder,(0,-1,0))
    desired=unit(hint-shoulder-axis*np.dot(hint-shoulder,axis))
    if side in arm_bends:
        old_elbow,old_normal=arm_bends[side]
        # Select the point on the current two-bone IK circle nearest the
        # previous elbow. Transporting only the bend vector flips the elbow
        # when a folded hand crosses from one side of its shoulder to the other.
        projected=old_elbow-axis*np.dot(old_elbow,axis)
        old=unit(projected,unit(np.cross(old_normal,axis),desired))
        angle=math.atan2(np.dot(axis,np.cross(old,desired)),np.dot(old,desired))
        desired=Rotation.from_rotvec(axis*np.clip(angle,-.40,.40)).apply(old)
    pu,pl,hand=arm_ik(shoulder,goal,shoulder+desired)
    arm_bends[side]=(pl[:3,3]-shoulder,pu[:3,0])
    return pu,pl,hand

# Build skin groups once, so floor checks use the user's actual block mesh.
char_to_rig=np.linalg.inv(rw)@np.array(profile['character_world'])
vertices=np.array([v['co'] for v in profile['character_vertices']])
v_rig=(np.c_[vertices,np.ones(len(vertices))]@char_to_rig.T)[:,:3]
groups={}
for j,v in enumerate(profile['character_vertices']):
    for name,weight in v['weights'].items():
        if name in rest and weight>0:groups.setdefault(name,[]).append((j,weight))
groups={n:(np.array([a for a,b in values]),np.array([b for a,b in values])) for n,values in groups.items()}
inv_rest={n:np.linalg.inv(m) for n,m in rest.items()}
def skin(pose):
    result=np.zeros_like(v_rig)
    for n,(ix,weights) in groups.items():
        result[ix]+=((np.c_[v_rig[ix],np.ones(len(ix))]@(pose[n]@inv_rest[n]).T)[:,:3])*weights[:,None]
    return result

body=[];contacts=[]
for f,src in enumerate(p):
    R=rot[f];pos=root[f].copy();pose={}
    # Preserve the torso inversion and yaw observed in the reference.
    pose['Bone.011']=affine(R,pos)
    pose['Body']=affine(R@rest['Body'][:3,:3],pos)
    chest=pos+R@[0,0,.6]
    pose['Chest']=affine(R@rest['Chest'][:3,:3],chest)
    headpos=chest+R@[0,0,.6]
    shoulder_src=(src[11]+src[12])*.5
    headup=unit(src[0]-shoulder_src,R[:,2])
    if np.dot(headup,R[:,2])<.25:headup=R[:,2]
    headR=torso_basis(R[:,0],unit(headup*.30+R[:,2]*.70))
    tuck=np.interp(f,[260,264,269,273],[0,1,1,0]) if 260<=f<=273 else 0.
    if tuck:headR=Rotation.from_rotvec(R[:,0]*math.radians(80)*tuck).as_matrix()@headR
    pose['Head']=affine(headR@rest['Head'][:3,:3],headpos)
    feet=[];weights=[]
    for side,sign,sh,el,wr,hi,kn,an,heel,toe in [
        ('Left',1,11,13,15,23,25,27,29,31),('Right',-1,12,14,16,24,26,28,30,32)]:
        shoulder=chest+R@[sign*.6,0,.4]
        u,v=direction_tracks['Arm:'+side+':Upper'][f],direction_tracks['Arm:'+side+':Lower'][f]
        hinge=unit(-np.cross(u,v),R[:,0]);theta=math.acos(np.clip(np.dot(u,v),-1,1))
        if theta>math.radians(150):v=Rotation.from_rotvec(hinge*-math.radians(150)).apply(u)
        elbow=shoulder+u*.4
        pose['Arm:'+side+':Upper']=affine(basis(u,hinge),shoulder)
        pose['Arm:'+side+':Lower']=affine(basis(v,hinge),elbow)
        hip=pos+R@[sign*.2,0,0]
        lu,lv=direction_tracks['Leg:'+side+':Upper'][f],direction_tracks['Leg:'+side+':Lower'][f]
        lh=unit(np.cross(lu,lv),R[:,0])
        knee=hip+lu*.6;ankle=knee+lv*.6
        pose['Leg:'+side+':Upper']=affine(basis(lu,lh),hip)
        pose['Leg:'+side+':Lower']=affine(basis(lv,lh),knee)
        lower=pose['Leg:'+side+':Lower'][:3,:3]
        sole=ankle[2]-.2*(abs(lower[2,0])+abs(lower[2,2]))
        foot_y=max(xy[f,heel,1],xy[f,toe,1])
        weight=float(np.clip((foot_y-floor_px[f]+26)/16,0,1))
        if 83<=f<=96 or 188<=f<=202 or 260<=f<=375:weight=0.
        weights.append(weight);feet.append(sole)
    if max(weights)>.05:
        delta=(ground+.004-np.average(feet,weights=np.maximum(weights,.00001)))*max(weights)
        for m in pose.values():m[2,3]+=delta
    body.append(pose);contacts.append(weights)

# Keep observed planted feet on the ground after adapting the human leg
# directions to the shorter block proportions. Free feet keep their capture.
contact_weights=gaussian_filter1d(np.asarray(contacts),1.1,axis=0,mode='nearest')
for f,pose in enumerate(body):
    for k,side in enumerate(['Left','Right']):
        weight=float(contact_weights[f,k])
        if weight<.02:continue
        upper=pose[f'Leg:{side}:Upper'];lower=pose[f'Leg:{side}:Lower']
        hip=upper[:3,3].copy();hint=lower[:3,3].copy()
        ankle=hint+lower[:3,1]*.6;target=ankle.copy()
        for _ in range(4):
            height=.2*(abs(lower[2,0])+abs(lower[2,2]))
            target[2]=ankle[2]*(1-weight)+(ground+.006+height)*weight
            axis=unit(target-hip,(0,0,-1));length=np.clip(np.linalg.norm(target-hip),.10,1.196)
            bend=hint-hip-axis*np.dot(hint-hip,axis)
            bend=unit(bend,-rot[f,:,1])
            knee=hip+axis*(length*.5)+bend*math.sqrt(max(0,.36-(length*.5)**2))
            foot=hip+axis*length;u=unit(knee-hip);v=unit(foot-knee)
            hinge=unit(np.cross(u,v),rot[f,:,0])
            upper=affine(basis(u,hinge),hip);lower=affine(basis(v,hinge),knee)
        pose[f'Leg:{side}:Upper']=upper;pose[f'Leg:{side}:Lower']=lower

# The source camera follows the performer while the floor is outside the
# image. A reconstructed leap keeps the full body above the planted scythe.
jump_frames=[348,349,350,351,353,357,361,364,368,371,374,375,376]
jump_clearance=[0.,.5,2.7,3.8,4.45,4.8,4.8,4.1,2.4,1.4,.15,.03,0.]
jump_curve=PchipInterpolator(jump_frames,jump_clearance)
for f in range(349,376):
    clearance=float(jump_curve(f))
    lift=max(0,ground+clearance-skin(body[f])[:,2].min())
    for m in body[f].values():m[2,3]+=lift

lifts=np.array([max(0,ground+.004-skin(pose)[:,2].min()) for pose in body])
lifts=gaussian_filter1d(maximum_filter1d(lifts,size=5),1)
for f,pose in enumerate(body):
    for m in pose.values():m[2,3]+=lifts[f]

# Reference shaft direction fixes and sliding contact use the same source time.
weapon_points=np.array(profile['weapon_vertices'])*W_SCALE
_,ix=np.unique(np.floor(weapon_points/.13).astype(int),axis=0,return_index=True)
weapon_points=weapon_points[ix]
gripz=[]
for f in range(N):
    d=np.array([capture['shaft'][f,0],-capture['shaft'][f,2]]);d=unit(d,(1,0))
    gold=wc[f]['gold_center'];grip=capture['grip_px'][f]
    dist=np.dot(np.array(gold)-grip,d) if gold is not None else -1
    area=max([b['area'] for b in wc[f]['gold_blobs']]+[0])
    gripz.append(float(np.clip(1.69-dist/pxscale[f]*SCALE/W_SCALE,-1.7,1.0)) if dist>25 and area>10 else np.nan)
valid=np.flatnonzero(np.isfinite(gripz));gripz=np.array(gripz)
gripz=np.interp(np.arange(N),valid,gripz[valid]);gripz=gaussian_filter1d(gripz,3)

boxes=[]
for name in body[0]:
    if name=='Bone.011' or name.endswith(':Lower') and name.startswith('Arm:'):continue
    if name=='Head':center=np.array([0,.4,0]);half=np.array([.41,.41,.41])
    elif name in ('Body','Chest'):center=np.array([0,.3,0]);half=np.array([.405,.305,.205])
    elif name.startswith('Arm:'):center=np.array([0,.2,0]);half=np.array([.195,.195,.195])
    else:center=np.array([0,.3,0]);half=np.array([.195,.295,.195])
    boxes.append((name,center,half))

solver=WeaponContact(profile['weapon_vertices'],W_SCALE,ground)
observed_gripz=gripz.copy()
observed_gripz[:25]=.15
solved=[];grip_errors=[];weapon_clearance=[];support=[];support_errors=[]
RELEASE_START,RELEASE_END=349,381
support_observed=[]
for f in range(N):
    a=np.array(track['segments'][f]['a']);b=np.array(track['segments'][f]['b']);d=unit(b-a,(1,0));normal=np.array([-d[1],d[0]])
    dist=np.abs((xy[f,[15,16]]-a)@normal)
    weight=float(np.clip((18-dist.max())/10,0,1))
    if 299<=f<=348:weight=1. if 301<=f<=331 or 338<=f<=348 else 0.
    if RELEASE_START<=f<=RELEASE_END:weight=0.
    support_observed.append(weight)
support_observed=gaussian_filter1d(support_observed,1.65,mode='nearest')
for f,pose in enumerate(body):
    side='Right'
    upper=pose['Arm:Right:Upper'];lower=pose['Arm:Right:Lower']
    shoulder=upper[:3,3].copy();elbow=lower[:3,3].copy();palm=elbow+lower[:3,1]*.48
    # Put the primary palm on the observed rod, adapting only the wrist target.
    diff=(capture['grip_px'][f]-xy[f,16])/pxscale[f]*SCALE
    diff=np.clip(diff,-.45,.45)
    goal=palm+np.array([diff[0],0,-diff[1]])
    handstand=np.interp(f,[260,264,269,273],[0,1,1,0]) if 260<=f<=273 else 0.
    if handstand:goal[2]=goal[2]*(1-handstand)+(ground+.10)*handstand
    R=rot[f]
    # Keep gripping hands outside the block torso; reference human shoulders
    # are considerably narrower than this Minecraft rig.
    center=pose['Chest'][:3,3]
    local=R.T@(goal-center)
    if abs(local[0])<.52 and abs(local[1])<.40:goal-=R[:,1]*(.4+local[1])
    pu,pl,palm=arm_ik(shoulder,goal,elbow)
    pose['Arm:Right:Upper']=pu;pose['Arm:Right:Lower']=pl
    axis=unit(shaft_input[f],(0,0,1))
    y=unit(np.cross(axis,[0,-1,0]),R[:,2]);x=unit(np.cross(y,axis));y=np.cross(axis,x)
    orientation=np.column_stack([x,y,axis])
    free=RELEASE_START<=f<=RELEASE_END
    if free:
        # Filled after the held poses so the release and catch endpoints are
        # continuous. Frames with the blade outside the image are interpolated.
        pose['Weapon:Scythe']=solved[-1]['Weapon:Scythe'].copy()
        support.append(0.);weapon_clearance.append(0.)
    else:
        rotations=np.array([pose[n][:3,:3] for n,_,_ in boxes])
        centers=np.array([pose[n][:3,3]+pose[n][:3,:3]@c for n,c,_ in boxes])
        extents=np.array([h for _,_,h in boxes])
        a=np.array(track['segments'][f]['a']);b=np.array(track['segments'][f]['b']);d=unit(b-a,(1,0));normal=np.array([-d[1],d[0]])
        dist=np.abs((xy[f,[15,16]]-a)@normal)
        weight=float(support_observed[f])
        planted=299<=f<=348
        lupper,llower=pose['Arm:Left:Upper'],pose['Arm:Left:Lower']
        lp=llower[:3,3]+llower[:3,1]*.48
        orientation,translation,goal,gripz[f],target,lgrip,lerror=solver.solve(
            f,axis,palm,shoulder,lp,lupper[:3,3],observed_gripz[f],rotations,centers,extents,
            planted=planted,two_hands=planted and weight>.9,locked_axis=f<=24,
            blade_plane_hint=rot[f,:,1] if f<=24 else None)
        pu,pl,palm=arm_ik(shoulder,goal,elbow)
        pose['Arm:Right:Upper']=pu;pose['Arm:Right:Lower']=pl
        translation=palm-orientation@np.array([0,0,gripz[f]*W_SCALE])
        pose['Weapon:Scythe']=affine(orientation*W_SCALE,translation)
        weapon_clearance.append(float((np.array(profile['weapon_vertices'])@pose['Weapon:Scythe'][:3,:3].T+translation)[:,2].min()-ground))
        if not planted:weight*=float(np.clip((.94-np.linalg.norm(target-lupper[:3,3]))/.06,0,1))
        if weight>.01:
            lu,ll,lhand=arm_ik(lupper[:3,3],lp+(target-lp)*weight,llower[:3,3])
            pose['Arm:Left:Upper']=lu;pose['Arm:Left:Lower']=ll
            if weight>.99:support_errors.append(float(np.linalg.norm(lhand-target)))
        support.append(weight)
        actual=pose['Weapon:Scythe']@np.array([0,0,gripz[f],1])
        grip_errors.append(float(np.linalg.norm(actual[:3]-palm)))
    for arm_side in ['Right','Left']:
        upper=pose[f'Arm:{arm_side}:Upper'];lower=pose[f'Arm:{arm_side}:Lower']
        hand=lower[:3,3]+lower[:3,1]*.48
        pu,pl,hand=stable_arm_ik(arm_side,upper[:3,3],hand,lower[:3,3])
        pose[f'Arm:{arm_side}:Upper']=pu;pose[f'Arm:{arm_side}:Lower']=pl
    solved.append(pose)
    if f%80==0:print('RETARGET',f,'/',N,'source_time',round(f/30,2),flush=True)

# Cross products of nearly straight limbs have an arbitrary sign. Correct the
# twist around each limb's existing Y axis without moving its joints or grips.
twist_repairs=0
for side in ['Left','Right']:
    for limb in ['Arm','Leg']:
        prev_hinge=None
        for pose in solved:
            upper=pose[f'{limb}:{side}:Upper'];lower=pose[f'{limb}:{side}:Lower']
            hinge=upper[:3,0].copy()
            if prev_hinge is not None and np.dot(hinge,prev_hinge)<0:
                hinge=-hinge;twist_repairs+=1
            if prev_hinge is not None and np.linalg.norm(np.cross(upper[:3,1],lower[:3,1]))<.22:
                hinge=unit(prev_hinge-upper[:3,1]*np.dot(prev_hinge,upper[:3,1]),hinge)
            upper[:3,:3]=basis(upper[:3,1],hinge)
            lower[:3,:3]=basis(lower[:3,1],hinge)
            prev_hinge=hinge

# Recheck actual mesh after both arm IK passes. Add only necessary clearance.
final_lifts=np.array([max(0,ground+.004-skin(pose)[:,2].min()) for pose in solved])
final_lifts=gaussian_filter1d(maximum_filter1d(final_lifts,size=3),.75)
for f,pose in enumerate(solved):
    for name,m in pose.items():
        if name!='Weapon:Scythe' or not RELEASE_START<=f<=RELEASE_END:m[2,3]+=final_lifts[f]

# The source camera follows the aerial character. Only visible pole mounts
# constrain its projected travel; do not mistake a gold boot for a blade.
start=solved[RELEASE_START-1]['Weapon:Scythe'];finish=solved[RELEASE_END+1]['Weapon:Scythe']
anchor_frames=[348,350,369,372,375,377]
anchor_x=[float(capture['blade_px'][348,0]),410.,699.,753.,816.,846.]
pole_roll=solver.records[[r['source_frame'] for r in solver.records].index(348)]['roll']
release_mats=[]
for f in range(RELEASE_START,378):
    t=(f-RELEASE_START)/(377-RELEASE_START)
    rr=WeaponContact.orientation(math.radians(-83),0,pole_roll)
    blend=min(1.,(f-RELEASE_START+1)/4)
    rr=Slerp([0,1],Rotation.from_matrix([start[:3,:3]/W_SCALE,rr]))([blend]).as_matrix()[0]
    projected_x=np.interp(f,anchor_frames,anchor_x)
    translation=start[:3,3].copy()
    translation[0]+=(projected_x-anchor_x[0])/float(np.median(pxscale[340:349]))*SCALE
    translation[2]=ground+.012-(np.array(profile['weapon_vertices'])*W_SCALE@rr.T)[:,2].min()
    mat=affine(rr*W_SCALE,translation);solved[f]['Weapon:Scythe']=mat;release_mats.append(mat)
last=solved[377]['Weapon:Scythe']
return_rotation=Slerp([0,1],Rotation.from_matrix([last[:3,:3]/W_SCALE,finish[:3,:3]/W_SCALE]))
for f in range(378,RELEASE_END+1):
    t=(f-377)/(RELEASE_END+1-377);blend=t*t*(3-2*t)
    rr=return_rotation([blend]).as_matrix()[0]
    translation=last[:3,3]*(1-blend)+finish[:3,3]*blend
    translation[2]+=.3*math.sin(math.pi*t)
    floor=(np.array(profile['weapon_vertices'])*W_SCALE@rr.T)[:,2].min()+translation[2]
    translation[2]+=max(0,ground+.012-floor)
    solved[f]['Weapon:Scythe']=affine(rr*W_SCALE,translation)

markers=[(1,'起势 / opening'),(6,'连斩 / ground combo'),(82,'突进 / dash'),
 (159,'低扫 / low sweep'),(174,'腾空回旋 / aerial kicks'),(241,'扫地 / ground sweep'),
 (264,'倒立撑镰 / handstand'),(300,'落镰撑转 / pole vault'),(350,'脱手腾空 / release'),
 (379,'回镰 / return'),(383,'接镰 / catch'),(396,'蓄力斩 / finisher'),(464,'收势 / recovery'),(519,'结束 / end')]
report={'source_url':'https://www.bilibili.com/video/BV1JH4y1r7po/','source_author':'Pirate_Gn',
 'action':'HB_Scythe_MediaPipe_BV1JH4y1r7po','fps':30,'fps_base':1,'frames':list(range(1,N+1)),
 'duration_seconds':(N-1)/30,'weapon_scale':W_SCALE,'grip_local_z':gripz.tolist(),
 'support_hand_weight':support,'source_leg_scale':float(SCALE),'root_ground_local':float(ground),
 'markers':[{'frame':f,'label':label} for f,label in markers],
 'method':'Google MediaPipe observed joints, BSS fixed bone lengths, reference shaft reconstruction, constrained sliding grip IK, blade roll/depth adaptation and mesh floor correction.',
 'release_interval_source_frames':[RELEASE_START,RELEASE_END],
 'release_visible_anchor_source_frames':anchor_frames,'release_visible_anchor_x':anchor_x,
 'camera_floor_compensation':'Visible gray ground-shadow tracking. Camera-followed aerial frames 349-375 use a reconstructed clearance arc, not a measured jump height.',
 'aerial_clearance_keyframes':list(zip(jump_frames,jump_clearance)),
 'handstand_adaptation':'Head tucks to accommodate the target block head and short arms, source frames 260-273.',
 'opening_user_correction':'Opening cut is constrained from the character upper right to lower left in moving torso coordinates, not screen coordinates.',
 'opening_direction_local_keyframes_degrees':list(zip(opening_frames,opening_angles)),
 'limitations':'Single-view camera depth and occluded joints are estimated; source camera-depth root translation is fixed. Effect-obscured poses and released weapon require reconstruction.',
 'poses':[{n:m.tolist() for n,m in pose.items()} for pose in solved]}
(WORK/'target_motion.json').write_text(json.dumps(report,ensure_ascii=False,separators=(',',':')),encoding='utf-8')
grip_errors=[]
for f,pose in enumerate(solved):
    if RELEASE_START<=f<=RELEASE_END:continue
    lower=pose['Arm:Right:Lower'];palm=lower[:3,3]+lower[:3,1]*.48
    actual=pose['Weapon:Scythe']@np.array([0,0,gripz[f],1])
    grip_errors.append(float(np.linalg.norm(actual[:3]-palm)))
qa={'frames':N,'action':report['action'],'max_primary_grip_error':max(grip_errors),
 'max_full_support_grip_error':max(support_errors,default=0),
 'anatomical_identity_repair_frames':identity_repairs,'limb_twist_sign_repairs':twist_repairs,
 'body_floor_min':float(min(skin(pose)[:,2].min()-ground for pose in solved)),
 'max_floor_adaptation':float(max(lifts+final_lifts)),
 'weapon_clearance_min_before_final_lift':float(min(weapon_clearance)),
 'weapon_floor_min':float(min((np.array(profile['weapon_vertices'])@pose['Weapon:Scythe'][:3,:3].T+pose['Weapon:Scythe'][:3,3])[:,2].min()-ground for pose in solved)),
 'source_root_depth':'Unobservable from the single camera; kept at zero.',
 'bone_lengths':{'upper_arm':.4,'forearm_to_palm':.48,'thigh':.6,'shin':.6}}
(WORK/'retarget_report.json').write_text(json.dumps(qa,indent=2),encoding='utf-8')
(WORK/'weapon_contact_report.json').write_text(json.dumps(solver.records,indent=2),encoding='utf-8')
print('RETARGET_COMPLETE',json.dumps({k:v for k,v in qa.items() if k!='anatomical_identity_repair_frames'}),flush=True)
