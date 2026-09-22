"""Repair support hand-over intervals before Epic Fight interpolates the legs."""
import json,math
from pathlib import Path
import numpy as np
from combat_cleanup import PROFILE,CombatRig,unit,two_bone,stabilize_limb_roll
ROOT=Path(__file__).resolve().parents[2]
PATH=ROOT/'output/Herobrine_Scythe_Recapture_05/capture/target_motion.json'
data=json.loads(PATH.read_text(encoding='utf-8'))
profile=json.loads(PROFILE.read_text(encoding='utf-8'));rig=CombatRig(profile,data)
poses=[{n:np.array(v) for n,v in row.items()} for row in data['poses']]
changed=[]
for i in range(len(poses)-2,-1,-1):
    a,b=data['footwork'][i:i+2]
    if any(a[f'foot_{s}_lift']<1e-9 and b[f'foot_{s}_lift']<1e-9 for s in 'lr'):continue
    side=min('lr',key=lambda s:b[f'foot_{s}_lift'])
    assert b[f'foot_{side}_lift']<1e-9,(i,'No next support foot')
    for axis in 'xy':a[f'foot_{side}{axis}']=b[f'foot_{side}{axis}']
    a[f'foot_{side}_lift']=0.
    changed.append((i,side))
for i,short in changed:
    side,sign=('Left',1) if short=='l' else ('Right',-1)
    pose=poses[i];origin=pose['Bone.011'][:3,3];pelvis=pose['Bone.011'][:3,:3]
    hip=origin+pelvis@[sign*.2,0,0];row=data['footwork'][i]
    goal=np.array([row[f'foot_{short}x'],row[f'foot_{short}y'],rig.ground+.12])
    hint=pose[f'Leg:{side}:Lower'][:3,3]
    for _ in range(24):
        direction=unit(goal-hip)
        bend=unit(hint-hip-direction*np.dot(hint-hip,direction),(0,-1,0))
        upper,lower,end=two_bone(hip,goal,hip+bend,.6,.6,pelvis[:,0])
        up=unit(np.array([0,0,1])-direction*direction[2],bend)
        for _anatomy in range(24):
            if lower[2,3]>=rig.ground+.24:break
            bend=unit(bend*.85+up*.15)
            upper,lower,end=two_bone(hip,goal,hip+bend,.6,.6,pelvis[:,0])
        goal[2]=rig.ground+.012+.2*(abs(lower[2,0])+abs(lower[2,2]))
    assert np.linalg.norm(end[:2]-goal[:2])<1e-6
    pose[f'Leg:{side}:Upper']=upper;pose[f'Leg:{side}:Lower']=lower
stabilize_limb_roll(poses)
for a,b in zip(data['footwork'],data['footwork'][1:]):
    assert any(a[f'foot_{s}_lift']<1e-9 and b[f'foot_{s}_lift']<1e-9 for s in 'lr')
data['poses']=[{n:m.tolist() for n,m in p.items()} for p in poses]
data['combat_cleanup']['support_handover_repairs']=[{'frame':int(i+1),'side':s} for i,s in changed]
PATH.write_text(json.dumps(data,ensure_ascii=False,separators=(',',':'),allow_nan=False)+'\n',encoding='utf-8')
print('RECAPTURE_SUPPORT_HANDOVER',changed)
