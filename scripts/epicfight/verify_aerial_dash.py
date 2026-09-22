"""Validate the independent sprint clip and render its real Epic Fight mesh."""
import hashlib
import json
import math
import sys
from pathlib import Path

import bpy
import numpy as np
from mathutils import Matrix, Vector

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/epicfight-aerial-dash'
ART=ROOT/'output/Herobrine_Scythe_AerialDash_08'
PREVIEW=ART/'preview'
ASSETS=ROOT/'src/main/resources/assets/herobrine_companion'
load=lambda p:json.loads(p.read_text(encoding='utf-8'))
report,source,mesh=[load(OUT/n) for n in ('conversion_report.json','reference_samples.json','biped.json')]
rows=load(ASSETS/'animmodels/animations/player/poem_mediapipe/dash.json')['animation']
times=np.array(rows[0]['time'])
values={r['name']:np.array(r['transform']).reshape(-1,4,4) for r in rows}
local,rest,parents={},{},{}
def walk(nodes,parent=None):
    for node in nodes:
        n=node['name'];local[n]=np.array(node['transform']).reshape(4,4);parents[n]=parent
        rest[n]=(rest[parent] if parent else np.eye(4)) @ local[n]
        walk(node.get('children',[]),n)
walk(mesh['armature']['hierarchy'])
inverse={n:np.linalg.inv(m) for n,m in rest.items()}
assert len(values)==20 and np.all(np.diff(times)>0) and abs(times[-1]-1.5)<1e-6
components={n:[Matrix(np.linalg.inv(local[n]) @ m).decompose() for m in values[n]] for n in rest}
def compose(t):
    i=min(len(times)-1,max(0,int(np.searchsorted(times,t,side='right')-1)))
    j=min(i+1,len(times)-1);u=(t-times[i])/(times[j]-times[i]) if i!=j else 0.
    w={}
    for n in rest:
        p,q,s=components[n][i];p2,q2,s2=components[n][j]
        m=local[n] @ np.array(Matrix.LocRotScale(p.lerp(p2,u),q.slerp(q2,u),s.lerp(s2,u)))
        w[n]=(w[parents[n]] if parents[n] else np.eye(4)) @ m
    return w,i,j,u
v=mesh['vertices'];pos=np.array(v['positions']['array']).reshape(-1,3)
groups={n:[] for n in rest};cursor=0
for i,count in enumerate(v['vcounts']['array']):
    for _ in range(count):
        j,wi=v['vindices']['array'][cursor:cursor+2];cursor+=2
        groups[mesh['armature']['joints'][j]].append((i,v['weights']['array'][wi]))
groups={n:(np.array([i for i,w in rows]),np.array([w for i,w in rows])) for n,rows in groups.items() if rows}
def skin(world):
    out=np.zeros_like(pos)
    for n,(ix,weights) in groups.items():
        out[ix]+=(np.c_[pos[ix],np.ones(len(ix))] @ (world[n] @ inverse[n]).T)[:,:3]*weights[:,None]
    return out
correction=np.array(report['weapon']['model_to_socket']) @ np.linalg.inv(np.array(report['weapon']['item_render_transform']))
weapon_vertices=np.array(source['source_weapon_vertices'])
weapon_h=np.c_[weapon_vertices,np.ones(len(weapon_vertices))]
grips={'R':np.array(source['source_grips_scaled']),'L':np.array(source['source_left_grips_scaled'])}
grip_error={s:0. for s in grips};min_body=min_blade=100.;max_length=max_tilt=0.
max_airborne_clearance=0.;airborne_times=[];root_rotation=0.;previous_q=None
max_ground_gap=max_ground_drift=0.;landed={};samples=[]
hook_tip=weapon_vertices[np.argmin(weapon_vertices[:,1])]
hook_base=np.array([0.,0.,hook_tip[2]])
minimum_hook_radial_gain=100.;maximum_hook_axial_component=0.
for t in np.linspace(0.,times[-1],round(times[-1]*960)+1):
    world,i,j,u=compose(t);socket=world['Tool_R'] @ np.linalg.inv(correction)
    vertices=skin(world);blade=(weapon_h @ socket.T)[:,:3]
    body_min=float(vertices[:,2].min());blade_min=float(blade[:,2].min())
    min_body=min(min_body,body_min);min_blade=min(min_blade,blade_min)
    root_delta=world['Root'][:3,:3] @ rest['Root'][:3,:3].T
    tilt=math.degrees(math.acos(np.clip(root_delta[2,2],-1,1)));max_tilt=max(max_tilt,tilt)
    q=Matrix(root_delta).to_quaternion()
    if previous_q is not None:
        qa,qb=np.array(previous_q,dtype=float),np.array(q,dtype=float)
        dot=abs(float(np.dot(qa,qb)/(np.linalg.norm(qa)*np.linalg.norm(qb))))
        root_rotation+=math.degrees(2*math.acos(min(1.,dot)))
    previous_q=q
    if .30<=t<=.90:
        axis=root_delta[:,2]
        tip=(socket @ np.r_[hook_tip,1])[:3]
        base=(socket @ np.r_[hook_base,1])[:3]
        center=world['Chest'][:3,3]
        radial=lambda p:np.linalg.norm(p-center-axis*np.dot(p-center,axis))
        minimum_hook_radial_gain=min(minimum_hook_radial_gain,float(radial(tip)-radial(base)))
        direction=(tip-base)/np.linalg.norm(tip-base)
        maximum_hook_axial_component=max(maximum_hook_axial_component,abs(float(np.dot(direction,axis))))
    feet=[]
    for s in ('R','L'):
        palm=(world['Hand_'+s] @ np.r_[local['Tool_'+s][:3,3],1])[:3]
        grip=grips[s][i]*(1-u)+grips[s][j]*u
        grip_error[s]=max(grip_error[s],float(np.linalg.norm(palm-(socket @ np.r_[grip,1])[:3])))
        sole=np.c_[source['foot_local_points'][s],np.ones(len(source['foot_local_points'][s]))] @ world['Leg_'+s].T
        feet.append(float(sole[:,2].min()))
        if t>=1.22:
            center=(world['Leg_'+s] @ np.r_[source['foot_local_centers'][s],1])[:3]
            max_ground_gap=max(max_ground_gap,feet[-1])
            if s not in landed:landed[s]=center[:2]
            max_ground_drift=max(max_ground_drift,float(np.linalg.norm(center[:2]-landed[s])))
    if min(feet)>.12:
        airborne_times.append(float(t))
    if .30<=t<=.9:
        max_airborne_clearance=max(max_airborne_clearance,min(feet))
    for n in ('Hand_R','Hand_L','Leg_R','Leg_L'):
        length=np.linalg.norm(world[n][:3,3]-world[parents[n]][:3,3])
        max_length=max(max_length,abs(float(length-np.linalg.norm(local[n][:3,3]))))
    samples.append(dict(time=float(t),root_height=float(world['Root'][2,3]-rest['Root'][2,3]),
                        body_min=body_min,blade_min=blade_min,feet_min=min(feet),tilt=tilt))
validation=dict(sample_hz=960,grip_error_blocks=grip_error,minimum_body_height_blocks=min_body,
                minimum_blade_height_blocks=min_blade,maximum_limb_length_error_blocks=max_length,
                maximum_body_tilt_degrees=max_tilt,root_rotation_path_degrees=root_rotation,
                airborne_interval_seconds=[min(airborne_times),max(airborne_times)] if airborne_times else [],
                maximum_both_feet_clearance_blocks=max_airborne_clearance,
                maximum_landing_sole_gap_blocks=max_ground_gap,maximum_landing_drift_blocks=max_ground_drift,
                maximum_entity_rise_blocks=max(0.,max(s['root_height'] for s in samples)),
                forward_blocks=float(values['Root'][-1,1,3]-values['Root'][0,1,3]),
                max_export_floor_correction_blocks=report['max_floor_correction_blocks'],
                minimum_outward_hook_radius_gain_blocks=minimum_hook_radial_gain,
                maximum_hook_component_along_body_axis=maximum_hook_axial_component,
                live_gameplay_tested=False)
(OUT/'validation_report.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf-8')
print('AERIAL_MESH_VALIDATION',json.dumps(validation),flush=True)
if report['max_floor_correction_blocks']>.02:
    index=int(np.argmax(report['floor_correction_blocks']));w,*_=compose(times[index]);vv=skin(w);lowest=int(np.argmin(vv[:,2]))
    print('FLOOR_DIAGNOSTIC',times[index],lowest,[n for n,(ix,weights) in groups.items() if lowest in ix],flush=True)
assert max(grip_error.values())<.012,grip_error
assert min_body>-.001 and min_blade>-.001,(min_body,min_blade)
assert max_length<3e-5,max_length
assert max_tilt>60 and root_rotation>720 and max_airborne_clearance>.55,validation
assert max(airborne_times)-min(airborne_times)>.6,airborne_times
assert minimum_hook_radial_gain>.25 and maximum_hook_axial_component<.15,validation
assert max_ground_drift<.008 and max_ground_gap<.035,(max_ground_drift,max_ground_gap)
assert abs(validation['forward_blocks']-3.7)<1e-6
if '--no-render' in sys.argv:raise SystemExit(0)

bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene;scene.render.engine='BLENDER_EEVEE'
scene.render.resolution_x,scene.render.resolution_y=960,720
scene.render.resolution_percentage=100;scene.eevee.taa_render_samples=16
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGB'
scene.render.use_persistent_data=True
scene.world=bpy.data.worlds.new('World');scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.12,.15,.20,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.5
def material(name,color,texture=None):
    m=bpy.data.materials.new(name);m.use_nodes=True;m.diffuse_color=(*color,1)
    bsdf=m.node_tree.nodes['Principled BSDF'];bsdf.inputs['Base Color'].default_value=(*color,1)
    if texture:
        tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(ASSETS/texture))
        tex.interpolation='Closest';m.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])
    return m
faces,uvs=[],[];uv_array=np.array(v['uvs']['array']).reshape(-1,2)
for name,part in v['parts'].items():
    if name.endswith(('Sleeve','Pants')) or name in ('hat','jacket'):continue
    for tri in np.array(part['array']).reshape(-1,3,3):
        faces.append(tuple(int(x) for x in tri[:,0]))
        uvs.extend([(float(uv_array[k,0]),1-float(uv_array[k,1])) for k in tri[:,1]])
body=bpy.data.meshes.new('Epic Fight BIPED');body.from_pydata(pos,[],faces);uv=body.uv_layers.new()
for i,co in enumerate(uvs):uv.data[i].uv=co
body_obj=bpy.data.objects.new('Herobrine | actual game mesh',body);scene.collection.objects.link(body_obj)
body.materials.append(material('Herobrine',(.1,.65,.65),'textures/entity/herobrine.png'))
weapon=bpy.data.meshes.new('Poem of the End');weapon.from_pydata(weapon_vertices,[],source['source_weapon_faces'])
scythe=bpy.data.objects.new('Poem of the End | actual game socket',weapon);scene.collection.objects.link(scythe)
uv=weapon.uv_layers.new()
for i,co in enumerate(source['source_weapon_uv']):uv.data[i].uv=co
weapon.materials.append(material('Scythe',(.15,.3,.36),'textures/item/poem_of_the_end_geo.png'))
bpy.ops.mesh.primitive_plane_add(size=200)
ground=material('Ground',(.11,.13,.16));bpy.context.object.data.materials.append(ground)
geo=ground.node_tree.nodes.new('ShaderNodeNewGeometry');tiles=ground.node_tree.nodes.new('ShaderNodeTexChecker')
tiles.inputs['Color1'].default_value=(.10,.125,.16,1);tiles.inputs['Color2'].default_value=(.16,.185,.22,1)
tiles.inputs['Scale'].default_value=2;ground.node_tree.links.new(geo.outputs['Position'],tiles.inputs['Vector'])
ground.node_tree.links.new(tiles.outputs['Color'],ground.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
for location,power,size in [((4,3,7),1300,7),((-4,-3,5),1000,6)]:
    light=bpy.data.lights.new('Softbox','AREA');light.energy=power;light.shape='DISK';light.size=size
    lamp=bpy.data.objects.new('Softbox',light);scene.collection.objects.link(lamp);lamp.location=location
    lamp.rotation_euler=(Vector((0,1.85,1.5))-lamp.location).to_track_quat('-Z','Y').to_euler()
camera=bpy.data.objects.new('Review camera',bpy.data.cameras.new('Review camera'));scene.collection.objects.link(camera)
scene.camera=camera;camera.data.type='ORTHO'
side='--side' in sys.argv;video='--video' in sys.argv
frames=list(range(1,92,2)) if video else [1,13,21,29,37,45,53,65,75,91]
direction=Vector((10,1.2,3.4) if side else (-7,10,3.8))
basis=np.array((-direction).to_track_quat('-Z','Y').to_matrix())
scale=7.3 if side else 5.3
for f in range(1,92):
    w,*_=compose((f-1)/60);geometry=np.vstack([skin(w),(weapon_h @ (w['Tool_R'] @ np.linalg.inv(correction)).T)[:,:3]])
    target=np.array((0.,1.85,1.55) if side else (w['Root'][0,3],w['Root'][1,3]+.25,1.55))
    projected=(geometry-target) @ basis
    scale=max(scale,float(np.abs(projected[:,0]).max())*2.15,float(np.abs(projected[:,1]).max())*2.15*960/720)
camera.data.ortho_scale=scale
folder=PREVIEW/('sequence_side' if side else 'sequence_front') if video else PREVIEW/('review_side' if side else 'review_front')
folder.mkdir(parents=True,exist_ok=True)
for index,frame in enumerate(frames):
    w,*_=compose((frame-1)/60)
    for vert,p in zip(body.vertices,skin(w)):vert.co=p
    body.update();scythe.matrix_world=Matrix(w['Tool_R'] @ np.linalg.inv(correction))
    target=Vector((0.,1.85,1.55) if side else (w['Root'][0,3],w['Root'][1,3]+.25,1.55))
    camera.location=target+direction;camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
    scene.render.filepath=str(folder/f'{index if video else frame:04d}.png')
    bpy.ops.render.render(write_still=True)
print('AERIAL_GAME_MESH_PREVIEWS_COMPLETE',folder,flush=True)
