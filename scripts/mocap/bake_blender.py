"""Bake the retarget through the connected Blender MCP authoring session."""
import bpy
import json
import math
from pathlib import Path
from mathutils import Matrix,Vector

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
OUT=ROOT/'output/Herobrine_Scythe_MediaPipe';OUT.mkdir(parents=True,exist_ok=True)
data=json.loads((WORK/'target_motion.json').read_text(encoding='utf-8'))
rig=bpy.data.objects['Bones:Character'];character=bpy.data.objects['Character']
weapon=bpy.data.objects['Poem of the End | 原版镰刀']
rig_world=rig.matrix_world.copy();char_world=character.matrix_world.copy()
scene=bpy.data.scenes.get('Herobrine | MediaPipe') or bpy.data.scenes.new('Herobrine | MediaPipe')
bpy.context.window.scene=scene
for obj in (rig,character,weapon):
    if obj.name not in scene.objects:scene.collection.objects.link(obj)
keep={rig,character,weapon}
for obj in list(bpy.data.objects):
    if obj not in keep:bpy.data.objects.remove(obj,do_unlink=True)
for old in list(bpy.data.scenes):
    if old!=scene:bpy.data.scenes.remove(old)
for col in list(bpy.data.collections):
    bpy.data.collections.remove(col)
for obj in keep:
    obj.hide_render=False;obj.hide_viewport=False;obj.hide_set(False)
    obj.animation_data_clear()
rig.matrix_world=rig_world;character.matrix_world=char_world
for modifier in character.modifiers:
    if modifier.type=='ARMATURE':modifier.use_deform_preserve_volume=False
character['skinning_note']='Linear skinning for consistent Blender and FBX playback; original mesh and vertex weights retained.'
for constraint in list(weapon.constraints):weapon.constraints.remove(constraint)
weapon.parent=None;weapon.matrix_world=Matrix.Identity(4)
constraint=weapon.constraints.new('COPY_TRANSFORMS');constraint.name='Baked scythe socket'
constraint.target=rig;constraint.subtarget='Weapon:Scythe'
for pb in rig.pose.bones:
    for constraint in pb.constraints:constraint.mute=True
    pb.matrix_basis=Matrix.Identity(4);pb.rotation_mode='QUATERNION'
old=bpy.data.actions.get(data['action'])
if old:bpy.data.actions.remove(old)
action=bpy.data.actions.new(data['action']);action.use_fake_user=True
rig.animation_data_create();rig.animation_data.use_nla=False;rig.animation_data.action=action
for unused_action in list(bpy.data.actions):
    if unused_action!=action:bpy.data.actions.remove(unused_action)
oldq={}
for f,row in zip(data['frames'],data['poses']):
    for pb in rig.pose.bones:
        if pb.name not in row:continue
        kwargs={}
        if pb.parent:
            kwargs={'parent_matrix':Matrix(row[pb.parent.name]),'parent_matrix_local':pb.parent.bone.matrix_local}
        basis=pb.bone.convert_local_to_pose(Matrix(row[pb.name]),pb.bone.matrix_local,invert=True,**kwargs)
        loc,quat,scale=basis.decompose()
        if pb.name in oldq and quat.dot(oldq[pb.name])<0:quat.negate()
        oldq[pb.name]=quat.copy()
        pb.location=loc;pb.rotation_quaternion=quat;pb.scale=scale
        pb.keyframe_insert('location',frame=f,group=pb.name)
        pb.keyframe_insert('rotation_quaternion',frame=f,group=pb.name)
        pb.keyframe_insert('scale',frame=f,group=pb.name)
def curves(act):
    if hasattr(act,'fcurves'):yield from act.fcurves
    else:
        for layer in act.layers:
            for strip in layer.strips:
                for bag in strip.channelbags:yield from bag.fcurves
for fc in curves(action):
    for k in fc.keyframe_points:k.interpolation='LINEAR'
action['source_url']=data['source_url'];action['capture_engine']='Google MediaPipe GHUM Heavy'
action['source_author']=data['source_author'];action['retarget_method']=data['method']
action['capture_limitations']=data['limitations'];action.use_frame_range=True
if 'combat_cleanup' in data:
    action['combat_cleanup']=data['combat_cleanup']['notes']
    rig['combat_grip']='Left hand: middle of shaft. Right hand: tail drives forward.'
action.frame_start=1;action.frame_end=data['frames'][-1]
rig['mocap_source']=data['source_url'];rig['source_fps']=30
scene.render.fps=30;scene.render.fps_base=1;scene.frame_start=1;scene.frame_end=data['frames'][-1]
scene.timeline_markers.clear()
for marker in data['markers']:scene.timeline_markers.new(marker['label'],frame=marker['frame'])
scene.unit_settings.system='METRIC'
scene['motion_notes']=data['limitations']
scene['preview_rates']='1.0x source time / 0.5x review'

def material(name,color,roughness=.8):
    m=bpy.data.materials.get(name) or bpy.data.materials.new(name);m.use_nodes=True
    bsdf=next(n for n in m.node_tree.nodes if n.type=='BSDF_PRINCIPLED')
    bsdf.inputs['Base Color'].default_value=(*color,1);bsdf.inputs['Roughness'].default_value=roughness
    return m
floor=bpy.data.objects.new('Studio | floor',bpy.data.meshes.new('Studio | floor mesh'))
floor.data.from_pydata([(-200,-200,0),(200,-200,0),(200,200,0),(-200,200,0)],[],[(0,1,2,3)])
floor.location.z=.4935753047466278;scene.collection.objects.link(floor)
floor.data.materials.append(material('Studio | charcoal',(.075,.093,.11)))
griddata=bpy.data.curves.new('Studio | half metre grid','CURVE');griddata.dimensions='3D';griddata.bevel_depth=.002
for i in range(-20,21):
    for points in [[(i*.5,-10,.497),(i*.5,10,.497)],[(-10,i*.5,.497),(10,i*.5,.497)]]:
        spl=griddata.splines.new('POLY');spl.points.add(1)
        for p,co in zip(spl.points,points):p.co=(*co,1)
grid=bpy.data.objects.new('Studio | half metre grid',griddata);scene.collection.objects.link(grid)
grid.hide_render=True
griddata.materials.append(material('Studio | grid',(.12,.15,.18)))
world=bpy.data.worlds.new('Studio | environment');world.use_nodes=True
world.node_tree.nodes['Background'].inputs['Color'].default_value=(.18,.21,.26,1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value=.65;scene.world=world
def area(name,loc,power,size,color):
    lamp=bpy.data.lights.new(name,'AREA');lamp.energy=power;lamp.shape='DISK';lamp.size=size;lamp.color=color
    obj=bpy.data.objects.new(name,lamp);scene.collection.objects.link(obj);obj.location=loc
    obj.rotation_euler=(Vector((.4,0,1.8))-obj.location).to_track_quat('-Z','Y').to_euler()
area('Studio | key',(-4,-6,8),1050,6,(.91,.96,1.))
area('Studio | fill',(5,-1,6),850,5,(.60,.78,1.))
area('Studio | rim',(1,4,7),1250,4,(1.,.85,.62))
def camera(name,loc,target,scale):
    obj=bpy.data.objects.new(name,bpy.data.cameras.new(name));scene.collection.objects.link(obj)
    obj.location=loc;obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler()
    obj.data.type='ORTHO';obj.data.ortho_scale=scale;obj.data.clip_end=150
    return obj
framing=json.loads((WORK/'camera_framing.json').read_text(encoding='utf-8'))
front=camera('Camera | reference view',(0,-20,4),(0,0,2),framing['orthographic_width'])
for f,target in enumerate(framing['camera_target'],1):
    front.location=(target[0],-20,target[2]+2)
    front.keyframe_insert('location',frame=f)
    front.data.ortho_scale=framing['width_per_frame'][f-1]
    front.data.keyframe_insert('ortho_scale',frame=f)
three=camera('Camera | three quarter',(7,-16,6),(.3,0,2.3),9.7)
side=camera('Camera | side',(17,-.5,4),(.3,0,2.5),8.8)
combat_framing=WORK/'combat_camera_framing.json'
if 'combat_cleanup' in data and combat_framing.exists():
    combat=json.loads(combat_framing.read_text(encoding='utf-8'))
    direction=Vector(combat['direction'])
    three.rotation_euler=(-direction).to_track_quat('-Z','Y').to_euler()
    for f,target in enumerate(combat['camera_target'],1):
        three.location=Vector(target)+direction*20
        three.keyframe_insert('location',frame=f)
        three.data.ortho_scale=combat['width_per_frame'][f-1]
        three.data.keyframe_insert('ortho_scale',frame=f)
    scene.camera=three
else:
    scene.camera=front
scene.render.engine='BLENDER_EEVEE';scene.eevee.taa_render_samples=16
scene.render.resolution_x=1280;scene.render.resolution_y=720;scene.render.resolution_percentage=75
scene.render.image_settings.media_type='IMAGE';scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGB'
scene.render.film_transparent=False;scene.render.use_persistent_data=True
scene.view_settings.view_transform='AgX'
for image in bpy.data.images:
    if image.source=='FILE' and image.has_data and not image.packed_file:image.pack()
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type=='VIEW_3D':
            area.spaces.active.region_3d.view_perspective='CAMERA';area.spaces.active.overlay.show_overlays=False
scene.frame_set(1);bpy.context.view_layer.update()
errors=[]
for f in [1,31,91,181,271,327,356,401,519]:
    scene.frame_set(f);bpy.context.view_layer.update()
    evaluated=rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
    wanted=data['poses'][f-1]
    errors.append(max(abs(evaluated.pose.bones[n].matrix[r][c]-wanted[n][r][c]) for n in wanted for r in range(4) for c in range(4)))
scene.frame_set(1)
for datablocks in (bpy.data.meshes,bpy.data.materials,bpy.data.images,bpy.data.worlds,bpy.data.node_groups):
    for block in list(datablocks):
        if block.users==0:datablocks.remove(block)
bpy.data.orphans_purge(do_recursive=True)
path=OUT/'Herobrine_镰刀动捕_MediaPipe.blend'
bpy.ops.wm.save_as_mainfile(filepath=str(path),compress=True)
(WORK/'blender_bake_report.json').write_text(json.dumps({'file':str(path),'action':action.name,'frames':len(data['frames']),
 'bones':len(rig.pose.bones),'max_evaluated_matrix_error':max(errors),'connection':'Blender MCP on 127.0.0.1:9877'},ensure_ascii=False,indent=2),encoding='utf-8')
print('BAKE_COMPLETE',json.dumps({'file':str(path),'max_matrix_error':max(errors),'frames':len(data['frames'])},ensure_ascii=True))
