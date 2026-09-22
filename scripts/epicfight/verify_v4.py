"""Independently decode exported EF matrices, validate FK and render the EF mesh.

Run using Blender --background --factory-startup --python this_file.py.
Uses build/epicfight-v4/biped.json extracted from the project's Epic Fight jar.
"""
import json
import math
from pathlib import Path
import sys

import bpy
import numpy as np
from mathutils import Matrix, Vector

REPO = Path(__file__).resolve().parents[2]
OUT = REPO/'build/epicfight-v4'
ASSETS = REPO/'src/main/resources/assets/herobrine_companion'
load = lambda p: json.loads(p.read_text(encoding='utf-8'))
report = load(OUT/'conversion_report.json')
arm = load(ASSETS/'animmodels/entity/hero_biped_nightfall.json')['armature']
mesh_data = load(OUT/'biped.json')
source = load(OUT/'reference_samples.json')
local, rest, parents = {}, {}, {}
def walk(nodes, parent=None):
    for node in nodes:
        n = node['name']
        local[n] = np.array(node['transform']).reshape(4,4)
        parents[n] = parent
        rest[n] = (rest[parent] if parent else np.eye(4)) @ local[n]
        walk(node.get('children',[]),n)
walk(arm['hierarchy'])


def decode(path):
    data = load(path)['animation']
    assert data[0]['name'] == 'Root'
    assert len(data) == len({d['name'] for d in data}) == 20
    times = np.array(data[0]['time'])
    assert times[0] == 0 and (np.diff(times) > 0).all()
    values = {}
    for d in data:
        assert d['name'] in rest and np.array_equal(times,d['time'])
        m = np.array(d['transform']).reshape(-1,4,4)
        assert len(m) == len(times) and np.isfinite(m).all()
        assert np.max(np.abs(m[:,3,:]-[0,0,0,1])) < 1e-7
        values[d['name']] = m
    return times, values


def compose(values, tick, runtime=False):
    world = {}
    for n in rest:
        m = values[n][tick] if n in values else local[n]
        if runtime and n in values:
            # JsonAssetLoader removes bind-local; JointTransform stores TRS;
            # JointTransform.getAnimationBoundMatrix puts bind-local back.
            delta = np.linalg.inv(local[n]) @ m
            loc, rot, scale = Matrix(delta.tolist()).decompose()
            reconstructed = np.array(Matrix.LocRotScale(loc,rot,scale))
            m = local[n] @ reconstructed
        world[n] = (world[parents[n]] if parents[n] else np.eye(4)) @ m
    return world


times, values = decode(ASSETS/'animmodels/animations/hero/hero_scythe_combo_v4.json')
assert abs(times[-1]-4.2) < 1e-7 and len(times)==505
max_seam = 0.
for i,(start,end) in enumerate(report['segments'],1):
    st, sv = decode(ASSETS/f'animmodels/animations/hero/hero_scythe_combo_v4_{i}.json')
    assert abs(st[-1]-(end-start)) < 1e-6
    first,last = round(start*120),round(end*120)
    assert len(st)==last-first+1
    for n,m in sv.items():
        full = values[n][first:last+1].copy()
        if n == 'Root':
            full[:,0,3] -= full[0,0,3]-rest[n][0,3]
            full[:,1,3] -= full[0,1,3]-rest[n][1,3]
        max_seam = max(max_seam,float(np.max(np.abs(full-m))))
assert max_seam < 2e-7

v = mesh_data['vertices']
pos = np.array(v['positions']['array']).reshape(-1,3)
weights = v['weights']['array']
indices = v['vindices']['array']
influences = []
cursor=0
for count in v['vcounts']['array']:
    group=[]
    for _ in range(count):
        group.append((mesh_data['armature']['joints'][indices[cursor]],weights[indices[cursor+1]]))
        cursor+=2
    assert abs(sum(w for _,w in group)-1) < 1e-5
    influences.append(group)


def skin(world):
    result=np.zeros_like(pos)
    transforms={n:world[n]@np.linalg.inv(rest[n]) for n in rest}
    for i,group in enumerate(influences):
        for n,w in group:
            result[i] += (transforms[n]@np.r_[pos[i],1])[:3]*w
    return result


correction=np.array(report['weapon']['model_to_socket']) @ np.linalg.inv(np.array(report['weapon']['item_render_transform']))
weapon_vertices=np.array(source['source_weapon_vertices'])
max_grip=0.
max_decompose=0.
min_floor=100.
min_weapon=100.
max_bone_length=0.
for tick in range(len(times)):
    w = compose(values,tick,runtime=True)
    exact = compose(values,tick)
    max_decompose=max(max_decompose,max(float(np.max(np.abs(w[n]-exact[n]))) for n in values))
    actual_socket=w['Tool_R']@np.linalg.inv(correction)
    palm=(w['Hand_R']@np.r_[local['Tool_R'][:3,3],1])[:3]
    max_grip=max(max_grip,float(np.linalg.norm(actual_socket[:3,3]-palm)))
    min_floor=min(min_floor,float(skin(w)[:,2].min()))
    wp=(np.c_[weapon_vertices,np.ones(len(weapon_vertices))]@actual_socket.T)[:,:3]
    min_weapon=min(min_weapon,float(wp[:,2].min()))
    for n in ('Hand_R','Hand_L','Leg_R','Leg_L'):
        max_bone_length=max(max_bone_length,abs(float(np.linalg.norm(w[n][:3,3]-w[parents[n]][:3,3])-np.linalg.norm(local[n][:3,3]))))
validation={'samples':len(times),'duration_seconds':float(times[-1]),'segment_reconstruction_max_error':max_seam,
            'runtime_trs_max_matrix_error':max_decompose,'max_grip_error_blocks':max_grip,
            'max_limb_length_error_blocks':max_bone_length,'minimum_mesh_height_blocks':min_floor,
            'minimum_weapon_height_blocks':min_weapon}
(OUT/'validation_report.json').write_text(json.dumps(validation,indent=2)+'\n',encoding='utf-8')
print('V4_VALIDATION',json.dumps(validation))
assert max_grip<.003 and max_bone_length<.0001 and max_decompose<.003
assert min_floor>=0 and min_weapon>=0

if '--no-render' in sys.argv:
    raise SystemExit(0)

# Actual Epic Fight skinned biped, using exported matrices, with the calibrated weapon.
bpy.ops.wm.read_factory_settings(use_empty=True)
scene=bpy.context.scene
scene.render.engine='BLENDER_EEVEE'
scene.render.resolution_x=640
scene.render.resolution_y=640
scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'
scene.world=bpy.data.worlds.new('World')
scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.09,.11,.15,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.5

def material(name,color):
    m=bpy.data.materials.new(name)
    m.diffuse_color=(*color,1)
    m.use_nodes=True
    m.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=(*color,1)
    return m
body_material=material('Hero',(.1,.65,.65))
body_material.use_nodes=True
bsdf=body_material.node_tree.nodes.get('Principled BSDF')
tex=body_material.node_tree.nodes.new('ShaderNodeTexImage')
tex.image=bpy.data.images.load(str(ASSETS/'textures/entity/hero.png'))
tex.interpolation='Closest'
body_material.node_tree.links.new(tex.outputs['Color'],bsdf.inputs['Base Color'])

faces=[]
uvs=[]
uv_array=np.array(v['uvs']['array']).reshape(-1,2)
for name,part in v['parts'].items():
    if name.endswith(('Sleeve','Pants')) or name in ('hat','jacket'):
        continue
    triangles=np.array(part['array']).reshape(-1,3,3)
    for tri in triangles:
        faces.append(tuple(int(x) for x in tri[:,0]))
        uvs.extend([(float(uv_array[k,0]),1-float(uv_array[k,1])) for k in tri[:,1]])
bm=bpy.data.meshes.new('Epic Fight BIPED')
bm.from_pydata(pos,[],faces)
uv=bm.uv_layers.new()
for i,co in enumerate(uvs):uv.data[i].uv=co
bo=bpy.data.objects.new('Hero - decoded Epic Fight matrices',bm)
scene.collection.objects.link(bo)
bm.materials.append(body_material)
wm=bpy.data.meshes.new('V4 scythe')
wm.from_pydata(weapon_vertices,[],source['source_weapon_faces'])
wo=bpy.data.objects.new('Scythe - calibrated Tool_R',wm)
scene.collection.objects.link(wo)
weapon_material=material('Scythe steel',(.14,.3,.36))
wtex=weapon_material.node_tree.nodes.new('ShaderNodeTexImage')
wtex.image=bpy.data.images.load(str(ASSETS/'textures/item/poem_of_the_end_geo.png'))
wtex.interpolation='Closest'
weapon_material.node_tree.links.new(wtex.outputs['Color'],weapon_material.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
wm.materials.append(weapon_material)
wuv=wm.uv_layers.new()
for i,co in enumerate(source['source_weapon_uv']):wuv.data[i].uv=co

bpy.ops.mesh.primitive_plane_add(size=200)
floor=bpy.context.object
floor.name='Ground'
floor.data.materials.append(material('Ground',(.055,.065,.085)))
for location,power,size in [((4,-2,7),1100,7),((-4,4,5),900,6)]:
    data=bpy.data.lights.new('Softbox','AREA')
    data.energy=power
    data.shape='DISK'
    data.size=size
    o=bpy.data.objects.new('Softbox',data)
    scene.collection.objects.link(o)
    o.location=location
    o.rotation_euler=(Vector((0,0,1))-o.location).to_track_quat('-Z','Y').to_euler()
camera_data=bpy.data.cameras.new('Camera')
camera=bpy.data.objects.new('Camera',camera_data)
scene.collection.objects.link(camera)
scene.camera=camera
camera_data.type='ORTHO'
camera_data.ortho_scale=6.5
views=[0,.575,.9,1.3,1.7166667,2.05,2.4,2.7333333,3.05,3.5,3.8,4.2]
for i,t in enumerate(views):
    tick=round(t*120)
    w=compose(values,tick,runtime=True)
    points=skin(w)
    for vertex,p in zip(bm.vertices,points):vertex.co=p
    bm.update()
    socket=w['Tool_R']@np.linalg.inv(correction)
    wo.matrix_world=Matrix(socket.tolist())
    target=Vector((w['Root'][0,3],w['Root'][1,3],1.05))
    camera.location=target+Vector((5,8,4.2))
    camera.rotation_euler=(target-camera.location).to_track_quat('-Z','Y').to_euler()
    scene.render.filepath=str(OUT/f'preview/{i:02d}.png')
    bpy.ops.render.render(write_still=True)
print('V4_PREVIEWS',len(views))
