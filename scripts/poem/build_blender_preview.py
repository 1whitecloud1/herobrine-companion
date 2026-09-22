"""Run through Blender MCP: preview the Java renderer's exported vanilla vertices."""
import bpy
import json
import math
from datetime import datetime
from pathlib import Path
from mathutils import Matrix, Vector

ROOT = Path('E:/java/herobrine_companion')
OUT = ROOT/'output/Herobrine_Scythe_UnityModes_11_Standalone'
REPORTS = OUT/'reports'
REPORTS.mkdir(parents=True, exist_ok=True)
if bpy.data.is_dirty and bpy.data.filepath:
    backup = ROOT/'build/poem_standalone'/('before_unity11_' + datetime.now().strftime('%Y%m%d_%H%M%S') + '.blend')
    bpy.ops.wm.save_as_mainfile(filepath=str(backup), copy=True)

data = json.loads((ROOT/'build/poem_standalone/vanilla_mesh_preview.json').read_text('utf-8'))
rig = json.loads((ROOT/'src/main/resources/assets/herobrine_companion/poem_standalone/rig.json').read_text('utf-8'))
weapon = json.loads((ROOT/'src/main/resources/assets/herobrine_companion/poem_standalone/weapon.json').read_text('utf-8'))
timeline = json.loads((ROOT/'src/main/resources/assets/herobrine_companion/epicfight/poem_unity09_timing.json').read_text('utf-8'))

def material(name, texture=None, color=(.1,.15,.19,1)):
    mat=bpy.data.materials.new(name);mat.use_nodes=True
    shader=mat.node_tree.nodes.get('Principled BSDF');shader.inputs['Base Color'].default_value=color
    shader.inputs['Roughness'].default_value=.72
    if texture:
        image=bpy.data.images.load(str(texture),check_existing=True);image.pack()
        tex=mat.node_tree.nodes.new('ShaderNodeTexImage');tex.image=image;tex.interpolation='Closest'
        mat.node_tree.links.new(tex.outputs['Color'],shader.inputs['Base Color'])
    return mat

skin=material('Unity11 demo skin',ROOT/'src/main/resources/assets/herobrine_companion/textures/entity/herobrine.png')
blade=material('Unity11 Poem blade',ROOT/'src/main/resources/assets/herobrine_companion/textures/item/poem_of_the_end_geo.png')
floor=material('Unity11 floor',color=(.12,.16,.20,1))

def dcc(p): return (-p[0],-p[2],1.5-p[1])
def mesh_object(scene,name,vertices,faces,uv,mat):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(vertices,[],faces);mesh.update()
    obj=bpy.data.objects.new(name,mesh);scene.collection.objects.link(obj);mesh.materials.append(mat)
    if uv:
        layer=mesh.uv_layers.new()
        for polygon in mesh.polygons:
            for loop_index in polygon.loop_indices:
                vertex=mesh.loops[loop_index].vertex_index
                layer.data[loop_index].uv=uv[vertex]
    return obj

scenes=[]; reports=[]
for mode in range(4):
    label=timeline['modes'][mode]['label']
    scene=bpy.data.scenes.new('Unity11 '+label+' - independent vanilla vertices')
    scene.render.engine='CYCLES';scene.cycles.samples=12
    scene.cycles.use_denoising=True
    scene.render.resolution_x=640;scene.render.resolution_y=640;scene.render.resolution_percentage=100
    scene.render.fps=15
    scene.world=bpy.data.worlds.new('Unity11 world '+str(mode));scene.world.use_nodes=True
    scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.13,.17,.22,1)
    scene.world.node_tree.nodes['Background'].inputs[1].default_value=.55
    scene.view_settings.view_transform='Standard'
    schedule=data[0]['meshes'][0]['poses'][mode]['schedule'];scene.frame_start=1;scene.frame_end=len(schedule)
    scene['preview_kind']='Java vanilla skinning output, not an in-game capture'
    scene['playback_speed']=.5;scene['mode']=mode
    scene['root_motion']='horizontal root removed; vanilla controls movement and damage'
    for part in data[0]['meshes']:
        frames=part['poses'][mode]['frames']
        verts=[dcc(p) for p in part['rest']];faces=[(i,i+1,i+2,i+3) for i in range(0,len(verts),4)]
        obj=mesh_object(scene,label+' '+part['part'],verts,faces,[(u,1-v) for u,v in part['uv']],skin)
        obj.shape_key_add(name='Basis');obj.data.shape_keys.use_relative=False
        for idx,points in enumerate(frames):
            key=obj.shape_key_add(name='Java frame '+str(idx+1))
            key.data.foreach_set('co',[c for p in points for c in dcc(p)])
        keys=obj.data.shape_keys
        keys.eval_time=keys.key_blocks[1].frame;keys.keyframe_insert(data_path='eval_time',frame=1)
        keys.eval_time=keys.key_blocks[-1].frame;keys.keyframe_insert(data_path='eval_time',frame=len(frames))
        for layer in keys.animation_data.action.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        for point in curve.keyframe_points:point.interpolation='LINEAR'
    obj=mesh_object(scene,label+' calibrated scythe',weapon['vertices'],weapon['faces'],weapon['uv'],blade)
    obj.rotation_mode='QUATERNION'
    for idx,key in enumerate(schedule):
        # Java/JOML exports column-major matrices. No additional yaw or blade flip is applied.
        cols=key['tool']; tool=Matrix([[cols[c*4+r] for c in range(4)] for r in range(4)])
        location,rotation,scale=(tool @ Matrix(rig['weapon_to_tool'])).decompose()
        obj.location=location;obj.rotation_quaternion=rotation;obj.scale=scale
        for path in ['location','rotation_quaternion','scale']:obj.keyframe_insert(data_path=path,frame=idx+1)
    for layer in obj.animation_data.action.layers:
        for strip in layer.strips:
            for bag in strip.channelbags:
                for curve in bag.fcurves:
                    for point in curve.keyframe_points:point.interpolation='LINEAR'
    mesh_object(scene,'floor '+str(mode),[(-10,-10,-.035),(10,-10,-.035),(10,10,-.035),(-10,10,-.035)],[(0,1,2,3)],None,floor)
    camera_data=bpy.data.cameras.new('Unity11 camera '+str(mode));camera=bpy.data.objects.new(camera_data.name,camera_data)
    scene.collection.objects.link(camera);camera.location=(5.2,9,4.8)
    camera.rotation_euler=(Vector((0,0,1.0))-camera.location).to_track_quat('-Z','Y').to_euler()
    camera_data.type='ORTHO';camera_data.ortho_scale=5.6;scene.camera=camera
    for i,(pos,energy,size) in enumerate([((2,4,7),900,5),((-4,1,4),600,4)]):
        light_data=bpy.data.lights.new('Unity11 light '+str(mode)+' '+str(i),'AREA');light_data.energy=energy;light_data.shape='DISK';light_data.size=size
        light=bpy.data.objects.new(light_data.name,light_data);scene.collection.objects.link(light);light.location=pos
        light.rotation_euler=(Vector((0,0,1))-light.location).to_track_quat('-Z','Y').to_euler()
    third=timeline['modes'][mode]['segments'][2]
    contact=third['contacts'][0];want=(contact['start']+contact['end'])/2
    frame=min((i for i,key in enumerate(schedule) if key['step']==2),key=lambda i:abs(schedule[i]['time']-want))+1
    scene.frame_set(frame)
    scene.render.image_settings.file_format='PNG';scene.render.filepath=str(REPORTS/('mode_'+str(mode)+'_pose.png'))
    reports.append({'mode':mode,'label':label,'scene':scene.name,'frames':len(schedule),'pose_frame':frame,'pose_step':3,'pose_clip_seconds':schedule[frame-1]['time']})
    scenes.append(scene)

target=OUT/'Unity11_独立玩家动画_顶点验证.blend'
bpy.data.libraries.write(str(target),set(scenes),fake_user=True,compress=True)
(REPORTS/'blender_preview.json').write_text(json.dumps({'file':str(target),'source':'Actual Java PoemSkinMesh export','live_gameplay_tested':False,'scenes':reports},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('UNITY11_PREVIEW_CREATED',target,[(r['label'],r['frames']) for r in reports])
