"""Independent numeric checks on the final retarget, using actual mesh vertices."""
import json
from pathlib import Path
import numpy as np
from scipy.ndimage import gaussian_filter1d,maximum_filter1d
from scipy.spatial.transform import Rotation

ROOT=Path(__file__).resolve().parents[2]
WORK=ROOT/'build/scythe_mocap'
SOURCE=Path('E:/MCStudioDownload/work/m13525918851@163.com/Cpp/AddOn/3055269d404f439eba5592a9d4b52113/_artifacts/Herobrine_镰刀战斗改进_V6/target_profile.json')
profile=json.loads(SOURCE.read_text(encoding='utf-8'))
data=json.loads((WORK/'target_motion.json').read_text(encoding='utf-8'))
poses=[{n:np.array(m) for n,m in row.items()} for row in data['poses']]
rest={n:np.array(b['rest']) for n,b in profile['bones'].items()}
rw=np.array(profile['rig_world']);cr=np.linalg.inv(rw)@np.array(profile['character_world'])
vertices=np.array([v['co'] for v in profile['character_vertices']])
vr=(np.c_[vertices,np.ones(len(vertices))]@cr.T)[:,:3]
groups={}
for i,v in enumerate(profile['character_vertices']):
    for name,weight in v['weights'].items():
        if name in rest and weight>0:groups.setdefault(name,[]).append((i,weight))
groups={n:(np.array([a for a,b in items]),np.array([b for a,b in items])) for n,items in groups.items()}
inverse={n:np.linalg.inv(m) for n,m in rest.items()}
weapon=np.c_[np.array(profile['weapon_vertices']),np.ones(len(profile['weapon_vertices']))]
ground=data['root_ground_local'];weapon_floor=[];body_floor=[];primary=[];secondary=[];bone_error=[];bounds=[]
release=data['release_interval_source_frames']
for f,pose in enumerate(poses):
    char=np.zeros_like(vr)
    for n,(ix,weights) in groups.items():
        char[ix]+=((np.c_[vr[ix],np.ones(len(ix))]@(pose[n]@inverse[n]).T)[:,:3])*weights[:,None]
    w=(weapon@pose['Weapon:Scythe'].T)[:,:3]
    body_floor.append(float(char[:,2].min()-ground));weapon_floor.append(float(w[:,2].min()-ground))
    all_world=np.c_[np.vstack([char,w]),np.ones(len(char)+len(w))]@rw.T
    # Projection onto the fixed reference camera axes (slightly above horizontal).
    projected=np.c_[all_world[:,0],(all_world[:,2]+.1*all_world[:,1])/np.sqrt(1.01)]
    bounds.append([projected.min(axis=0).tolist(),projected.max(axis=0).tolist()])
    for side in ['Left','Right']:
        for limb,length in [('Arm',.4),('Leg',.6)]:
            upper=pose[f'{limb}:{side}:Upper'];lower=pose[f'{limb}:{side}:Lower']
            bone_error.append(float(np.linalg.norm(upper[:3,3]+upper[:3,1]*length-lower[:3,3])))
    if not release[0]<=f<=release[1]:
        lower=pose['Arm:Right:Lower'];palm=lower[:3,3]+lower[:3,1]*.48
        contact=pose['Weapon:Scythe']@np.array([0,0,data['grip_local_z'][f],1])
        primary.append(float(np.linalg.norm(contact[:3]-palm)))
    if data['support_hand_weight'][f]>.99:
        lower=pose['Arm:Left:Lower'];palm=lower[:3,3]+lower[:3,1]*.48
        local=np.linalg.inv(pose['Weapon:Scythe'])@np.r_[palm,1]
        secondary.append({'frame':f+1,'distance':float(np.linalg.norm(local[:2])*data['weapon_scale'])})

continuity={}
for name in poses[0]:
    mats=np.array([p[name] for p in poses]);rot=mats[:,:3,:3]/np.linalg.det(mats[:,:3,:3])[:,None,None]**(1/3)
    delta=np.degrees((Rotation.from_matrix(rot[1:])*Rotation.from_matrix(rot[:-1]).inv()).magnitude())
    continuity[name]={'maximum_degrees_per_frame':float(delta.max()),'p95_degrees_per_frame':float(np.quantile(delta,.95)),
                      'largest_change_at_frame':int(delta.argmax()+2)}
report={'frames':len(poses),'fps':data['fps'],'rig_world_scale':float(rw[0,0]),
        'body_vertices_checked':len(vertices),'weapon_vertices_checked':len(weapon),
        'body_floor_min_rig_units':min(body_floor),'weapon_floor_min_rig_units':min(weapon_floor),
        'body_penetrating_frames':[i+1 for i,v in enumerate(body_floor) if v<-.001],
        'weapon_penetrating_frames':[i+1 for i,v in enumerate(weapon_floor) if v<-.001],
        'bone_connection_max_error':max(bone_error),'primary_grip_max_error':max(primary),
        'support_hand_max_distance_to_shaft':max((v['distance'] for v in secondary),default=0),
        'continuity':continuity,
        'note':'Numeric clearance and bone checks do not establish anatomical accuracy or eliminate all self-intersection.'}
opening={}
for frame,label in [(7,'upper_right_preparation'),(22,'lower_left_follow_through')]:
    pose=poses[frame-1]
    local=pose['Bone.011'][:3,:3].T@(pose['Weapon:Scythe'][:3,2]/data['weapon_scale'])
    opening[label]={'blender_frame':frame,'blade_direction_local_left_back_up':local.tolist()}
prep=np.array(opening['upper_right_preparation']['blade_direction_local_left_back_up'])
end=np.array(opening['lower_left_follow_through']['blade_direction_local_left_back_up'])
opening['passed']=bool(prep[0]<-.4 and prep[2]>.4 and end[0]>.4 and end[2]<-.4)
report['opening_character_relative_direction']=opening
assert opening['passed'],opening
(WORK/'motion_qa.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
bb=np.array(bounds);half=(bb[:,1]-bb[:,0])*.5
required=np.maximum(half[:,0]*2,half[:,1]*2*16/9)*1.16
width=np.maximum(6.7,required)
width=np.maximum(width,gaussian_filter1d(maximum_filter1d(width,size=15),4))
center=gaussian_filter1d(bb.mean(axis=1),10,axis=0,mode='nearest')
visible=np.c_[width/2,width/2*9/16]*.95
center=np.minimum(np.maximum(center,bb[:,1]-visible),bb[:,0]+visible)
framing={'orthographic_width':float(width.max()),'width_per_frame':width.tolist(),'camera_target':[[float(x),0.,float(z*np.sqrt(1.01))] for x,z in center],
         'projection':'world X and (world Z + 0.1 world Y)/sqrt(1.01)'}
(WORK/'camera_framing.json').write_text(json.dumps(framing),encoding='utf-8')
print(json.dumps({k:v for k,v in report.items() if k!='continuity'}))
print('CONTINUITY',json.dumps({n:[round(v['maximum_degrees_per_frame'],1),round(v['p95_degrees_per_frame'],1)] for n,v in continuity.items()}))
print('CAMERA_WIDTH_RANGE',float(width.min()),float(width.max()),'TARGET_RANGE',center.min(axis=0),center.max(axis=0))
