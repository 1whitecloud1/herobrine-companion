"""Blender MCP: inspect old/new legs using the actual vanilla cube/UV export."""
import bpy
import json
import math
from datetime import datetime
from pathlib import Path
import numpy as np
from mathutils import Vector

ROOT=Path('E:/java/herobrine_companion')
WORK=ROOT/'build/poem_legfix'
rig=json.loads((ROOT/'src/main/resources/assets/herobrine_companion/poem_standalone/rig.json').read_text('utf-8'))
data=json.loads((WORK/'unity11_vanilla_mesh_preview.json').read_text('utf-8'))[0]
fixed=json.loads((WORK/'vanilla_mesh_preview.json').read_text('utf-8'))[0]
OUT=ROOT/'output/Herobrine_Scythe_UnityModes_12_LegFix'
(OUT/'reports').mkdir(parents=True,exist_ok=True)
if bpy.data.is_dirty and bpy.data.filepath:
    bpy.ops.wm.save_as_mainfile(filepath=str(WORK/('before_leg_review_'+datetime.now().strftime('%Y%m%d_%H%M%S')+'.blend')),copy=True)

bind={}
for joint in rig['joints']:
    m=np.array(joint['bind']).reshape(4,4)
    bind[joint['name']]=(np.eye(4) if joint['parent']<0 else bind[rig['joints'][joint['parent']]['name']])@m
inverses={n:np.linalg.inv(m) for n,m in bind.items()}
part_bones={'HEAD':('Head','Head'),'BODY':('Chest','Torso'),'RIGHT_ARM':('Arm_R','Hand_R'),
            'LEFT_ARM':('Arm_L','Hand_L'),'RIGHT_LEG':('Thigh_R','Leg_R'),'LEFT_LEG':('Thigh_L','Leg_L')}

def material(name,texture=None,color=(.12,.16,.2,1)):
    mat=bpy.data.materials.new(name);mat.use_nodes=True
    shader=mat.node_tree.nodes.get('Principled BSDF');shader.inputs['Base Color'].default_value=color
    shader.inputs['Roughness'].default_value=.75
    if texture:
        image=bpy.data.images.load(str(texture),check_existing=True);image.pack()
        tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest'
        mat.node_tree.links.new(tex.outputs['Color'],shader.inputs['Base Color'])
    return mat

skin=material('Leg review skin',ROOT/'src/main/resources/assets/herobrine_companion/textures/entity/herobrine.png')
floor=material('Leg review floor')

def mesh_object(scene,name,vertices,faces,uv,mat):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    obj=bpy.data.objects.new(name,mesh);scene.collection.objects.link(obj);mesh.materials.append(mat)
    if uv:
        layer=mesh.uv_layers.new()
        for polygon in mesh.polygons:
            for loop in polygon.loop_indices:
                vi=mesh.loops[loop].vertex_index;layer.data[loop].uv=(uv[vi][0],1-uv[vi][1])
    return obj

def world(path,t):
    channels={c['name']:c for c in json.loads(path.read_text('utf-8'))['animation']}
    result={}
    for joint in rig['joints']:
        c=channels[joint['name']];i=int(np.abs(np.array(c['time'])-t).argmin())
        m=np.array(c['transform'][i]).reshape(4,4)
        if joint['parent']<0:m[:2,3]=0
        result[joint['name']]=(np.eye(4) if joint['parent']<0 else result[rig['joints'][joint['parent']]['name']])@m
    return result

scenes=[];reports=[]
for mode,step,t,label in [('normal',1,0.,'entry'),('normal',1,.2333333,'normal_contact'),
                         ('thunder',3,.3666667,'thunder_turn'),('void_shatter',3,.3333333,'void_turn')]:
    mode_index=['normal','realm_breaker','thunder','void_shatter'].index(mode)
    schedule=data['meshes'][0]['poses'][mode_index]['schedule']
    index=min((i for i,key in enumerate(schedule) if key['step']==step-1),key=lambda i:abs(schedule[i]['time']-t))
    t=schedule[index]['time']
    rel=Path('animmodels/animations/player/poem_unity09')/mode/('combo_%02d.json'%step)
    old_path=WORK/'before/neo'/rel
    if not old_path.exists():old_path=ROOT/'src/main/resources/assets/herobrine_companion'/rel
    old=world(old_path,t);new=world(WORK/'resources'/rel,t)
    front=-old['Root'][:3,2];heading=math.atan2(front[0],front[1])
    c,s=math.cos(heading),math.sin(heading)
    # Rotate the current pelvis heading to +Y, so true leg opening is visible.
    rotate=np.array([[c,-s,0],[s,c,0],[0,0,1]])
    scene=bpy.data.scenes.new('Leg correction '+label);scene.render.engine='CYCLES';scene.cycles.samples=16
    scene.cycles.use_denoising=True;scene.render.resolution_x=1000;scene.render.resolution_y=700;scene.render.resolution_percentage=100
    scene.world=bpy.data.worlds.new(scene.name+' world');scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.13,.17,.22,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=.6;scene.view_settings.view_transform='Standard'
    for variant,export,offset in [('before',data,1.1),('after',fixed,-1.1)]:
        for part in export['meshes']:
            assert part['poses'][mode_index]['schedule']==schedule
            points=np.array(part['poses'][mode_index]['frames'][index])
            points=np.c_[-points[:,0],-points[:,2],1.5-points[:,1]]@rotate.T
            points[:,0]+=offset
            faces=[(i,i+1,i+2,i+3) for i in range(0,len(points),4)]
            mesh_object(scene,variant+' '+part['part'],points.tolist(),faces,part['uv'],skin)
    mesh_object(scene,'floor',[(-8,-8,-.035),(8,-8,-.035),(8,8,-.035),(-8,8,-.035)],[(0,1,2,3)],None,floor)
    camera_data=bpy.data.cameras.new(scene.name+' camera');camera=bpy.data.objects.new(camera_data.name,camera_data)
    scene.collection.objects.link(camera);camera.location=(.6,9,3.3)
    camera.rotation_euler=(Vector((0,0,.88))-camera.location).to_track_quat('-Z','Y').to_euler()
    camera_data.type='ORTHO';camera_data.ortho_scale=4.65;scene.camera=camera
    for i,(pos,energy,size) in enumerate([((2,4,7),900,5),((-4,1,4),600,4)]):
        light_data=bpy.data.lights.new(scene.name+' light '+str(i),'AREA');light_data.energy=energy;light_data.shape='DISK';light_data.size=size
        light=bpy.data.objects.new(light_data.name,light_data);scene.collection.objects.link(light);light.location=pos
        light.rotation_euler=(Vector((0,0,.8))-light.location).to_track_quat('-Z','Y').to_euler()
    scene['left']='Unity11 before';scene['right']='Unity12 corrected legs';scene['preview_kind']='Actual Java model vertices, not gameplay'
    scene.render.image_settings.file_format='PNG';scene.render.filepath=str(OUT/'reports'/(label+'_before_after.png'))
    scenes.append(scene);reports.append(dict(scene=scene.name,mode=mode,step=step,time=t,image=scene.render.filepath))

target=WORK/'leg_stance_review.blend'
bpy.data.libraries.write(str(target),set(scenes),fake_user=True,compress=True)
(WORK/'leg_stance_scenes.json').write_text(json.dumps(reports,ensure_ascii=False,indent=2),'utf-8')
print('LEG_STANCE_MCP_PROJECT',target)
