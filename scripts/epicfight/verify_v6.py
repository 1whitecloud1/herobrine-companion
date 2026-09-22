"""Decode the exported player clips, verify sliding grip/FK/TRS, and render the EF mesh."""
import json
import sys
from pathlib import Path
import bpy
import numpy as np
from mathutils import Matrix, Vector

REPO=Path(__file__).resolve().parents[2]
OUT=REPO/'build/epicfight-v6'
ASSETS=REPO/'src/main/resources/assets/herobrine_companion'
DEST=ASSETS/'animmodels/animations/player/poem_v6'
load=lambda p:json.loads(p.read_text(encoding='utf-8'))
report=load(OUT/'conversion_report.json')
source=load(OUT/'reference_samples.json')
mesh_data=load(OUT/'biped.json')
local,rest,parents={},{},{}
def walk(nodes,parent=None):
    for node in nodes:
        n=node['name']; parents[n]=parent
        local[n]=np.array(node['transform']).reshape(4,4)
        rest[n]=(rest[parent] if parent else np.eye(4))@local[n]
        walk(node.get('children',[]),n)
walk(mesh_data['armature']['hierarchy'])
def decode(name):
    rows=load(DEST/(name+'.json'))['animation']
    times=np.array(rows[0]['time']); values={}
    assert times[0]==0 and np.all(np.diff(times)>0)
    assert len(rows)==len({r['name'] for r in rows})
    for r in rows:
        assert r['name'] in rest and np.array_equal(times,r['time'])
        m=np.array(r['transform']).reshape(-1,4,4)
        assert len(m)==len(times) and np.isfinite(m).all()
        assert np.max(np.abs(m[:,3]-[0,0,0,1]))<1e-7
        values[r['name']]=m
    return times,values
def compose(values,tick,runtime=False):
    w={}
    for n in rest:
        m=values[n][tick] if n in values else local[n]
        if runtime and n in values:
            t,q,s=Matrix((np.linalg.inv(local[n])@m).tolist()).decompose()
            m=local[n]@np.array(Matrix.LocRotScale(t,q,s))
        w[n]=(w[parents[n]] if parents[n] else np.eye(4))@m
    return w
times,values=decode('full')
assert len(values)==20 and len(times)==3361 and abs(times[-1]-56.056)<1e-6
max_seam=0
last=0
for s in report['segments']:
    t,v=decode(s['name']); a,b=s['start_index'],s['end_index']
    assert a==last; last=b
    assert abs(t[-1]-s['duration'])<1e-6
    assert 0<=s['hit_start']<s['contact']<=s['recovery']<t[-1]
    for n,m in v.items():
        expected=values[n][a:b+1].copy()
        if n=='Root': expected[:,:2,3]-=expected[0,:2,3]-rest[n][:2,3]
        max_seam=max(max_seam,float(np.max(np.abs(m-expected))))
assert last==len(times)-1 and max_seam<3e-7
for s in report['specials']:
    t,v=decode(s['name'])
    assert abs(t[-1]-s['duration'])<1e-6 and 0<s['hit_start']<s['contact']<s['recovery']<t[-1]
    if s['name']=='air': assert np.max(np.abs(v['Root'][:,:2,3]-v['Root'][0,:2,3]))<1e-7
for name in ('ready','hold'):
    t,v=decode(name)
    assert all(np.array_equal(m[0],m[-1]) for m in v.values())
v=mesh_data['vertices']
pos=np.array(v['positions']['array']).reshape(-1,3)
groups={n:[] for n in rest}; cursor=0
for i,count in enumerate(v['vcounts']['array']):
    for _ in range(count):
        j,k=v['vindices']['array'][cursor:cursor+2]; cursor+=2
        groups[mesh_data['armature']['joints'][j]].append((i,v['weights']['array'][k]))
groups={n:(np.array([i for i,w in g]),np.array([w for i,w in g])) for n,g in groups.items() if g}
inv_rest={n:np.linalg.inv(m) for n,m in rest.items()}
def skin(w):
    result=np.zeros_like(pos)
    for n,(ix,weights) in groups.items():
        result[ix]+=((np.c_[pos[ix],np.ones(len(ix))]@(w[n]@inv_rest[n]).T)[:,:3])*weights[:,None]
    return result
correction=np.array(report['weapon']['model_to_socket'])@np.linalg.inv(np.array(report['weapon']['item_render_transform']))
weapon_vertices=np.array(source['source_weapon_vertices'])
weapon_h=np.c_[weapon_vertices,np.ones(len(weapon_vertices))]
max_grip=max_trs=max_limb=0.
min_floor=min_weapon=100.
for i in range(len(times)):
    w=compose(values,i,True); exact=compose(values,i)
    max_trs=max(max_trs,max(float(np.max(np.abs(w[n]-exact[n]))) for n in rest))
    socket=w['Tool_R']@np.linalg.inv(correction)
    palm=(w['Hand_R']@np.r_[local['Tool_R'][:3,3],1])[:3]
    grip=(socket@np.r_[source['source_grips_scaled'][i],1])[:3]
    max_grip=max(max_grip,float(np.linalg.norm(palm-grip)))
    min_floor=min(min_floor,float(skin(w)[:,2].min()))
    min_weapon=min(min_weapon,float((weapon_h@socket.T)[:,2].min()))
    for n in ('Hand_R','Hand_L','Leg_R','Leg_L'):
        max_limb=max(max_limb,abs(float(np.linalg.norm(w[n][:3,3]-w[parents[n]][:3,3])-np.linalg.norm(local[n][:3,3]))))
validation={'samples':len(times),'clips':23,'duration_seconds':float(times[-1]),'segment_reconstruction_max_error':max_seam,
    'runtime_trs_max_matrix_error':max_trs,'max_sliding_grip_error_blocks':max_grip,'max_limb_length_error_blocks':max_limb,
    'minimum_mesh_height_blocks':min_floor,'minimum_weapon_height_blocks':min_weapon,
    'max_support_hand_error_blocks':report['max_support_hand_error_blocks']}
(OUT/'validation_report.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf-8')
print('V6_VALIDATION',json.dumps(validation))
# Source float matrices and the EF bind frame produce sub-millimetre TRS error.
assert max_trs<.001 and max_grip<.001 and max_limb<.0001 and min_floor>=0 and min_weapon>=0
if '--no-render' in sys.argv: raise SystemExit(0)

# The V4 renderer already builds the actual EF biped mesh, texture UVs and camera.
# Reuse only its rendering section; all decoding and validation above are V6-specific.
renderer=(Path(__file__).parent/'verify_v4.py').read_text(encoding='utf-8').split('# Actual Epic Fight skinned biped,')[1]
renderer='# Actual Epic Fight skinned biped,'+renderer
renderer=renderer.replace("textures/entity/hero.png","textures/entity/herobrine.png")
renderer=renderer.replace('V4 scythe','V6 scythe').replace('V4_PREVIEWS','V6_PREVIEWS')
old='views=[0,.575,.9,1.3,1.7166667,2.05,2.4,2.7333333,3.05,3.5,3.8,4.2]'
renderer=renderer.replace(old,"views=[float(times[0])]+[float(times[s['peak_index']]) for s in report['segments']]")
renderer=renderer.replace('tick=round(t*120)','tick=int(np.argmin(np.abs(times-t)))')
renderer=renderer.replace('camera_data.ortho_scale=6.5','camera_data.ortho_scale=6.0')
exec(compile(renderer,'<shared EF mesh renderer>','exec'))
