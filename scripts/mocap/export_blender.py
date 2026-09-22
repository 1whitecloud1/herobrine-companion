"""Export the MCP-authored retarget and record geometry for round-trip checking."""
import bpy
import json
import re
from pathlib import Path
from mathutils import Vector

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
OUT=ROOT/'output/Herobrine_Scythe_MediaPipe'
scene=bpy.context.scene
rig=bpy.data.objects['Bones:Character'];character=bpy.data.objects['Character']
weapon=bpy.data.objects['Poem of the End | 原版镰刀']
textures=OUT/'textures';textures.mkdir(exist_ok=True)
used_images=set()
for obj in (character,weapon):
    for mat in obj.data.materials:
        if mat and mat.use_nodes:
            for node in mat.node_tree.nodes:
                if node.type=='TEX_IMAGE' and node.image:used_images.add(node.image)
saved=[]
for img in sorted(used_images,key=lambda im:im.name):
    name=re.sub(r'\.\d+$','',img.name)
    if not name.lower().endswith('.png'):name+='.png'
    path=textures/name
    img.filepath_raw=str(path);img.file_format='PNG';img.save()
    img.filepath='//textures/'+name
    if not img.packed_file:img.pack()
    saved.append(str(path))

samples={}
for frame in [1,7,14,17,22,45,81,120,181,224,268,312,327,347,371,383,401,445,519]:
    scene.frame_set(frame);bpy.context.view_layer.update();deps=bpy.context.evaluated_depsgraph_get()
    row={}
    for obj in (character,weapon):
        evaluated=obj.evaluated_get(deps);mesh=evaluated.to_mesh()
        pts=[evaluated.matrix_world@v.co for v in mesh.vertices]
        row[obj.name]={'vertices':len(pts),'bounds_min':[min(v[k] for v in pts) for k in range(3)],
                       'bounds_max':[max(v[k] for v in pts) for k in range(3)]}
        evaluated.to_mesh_clear()
    samples[str(frame)]=row
(WORK/'fbx_expected_geometry.json').write_text(json.dumps(samples,ensure_ascii=False,indent=2),encoding='utf-8')
scene.frame_set(1)
bpy.ops.object.select_all(action='DESELECT')
for obj in (rig,character,weapon):obj.select_set(True)
bpy.context.view_layer.objects.active=rig
path=OUT/'Herobrine_Scythe_MediaPipe.fbx'
bpy.ops.export_scene.fbx(filepath=str(path),use_selection=True,object_types={'ARMATURE','MESH'},
    apply_unit_scale=True,apply_scale_options='FBX_SCALE_NONE',use_mesh_modifiers=True,
    add_leaf_bones=False,use_armature_deform_only=False,
    bake_anim=True,bake_anim_use_all_bones=True,bake_anim_use_nla_strips=False,
    bake_anim_use_all_actions=False,bake_anim_force_startend_keying=True,
    bake_anim_step=1.0,bake_anim_simplify_factor=0.0,
    path_mode='COPY',embed_textures=True,axis_forward='-Z',axis_up='Y')
scene['source_attribution']='Pirate_Gn / 镰刀连击 / BV1JH4y1r7po'
scene['mocap_review']='Google MediaPipe reference capture with user-directed two-hand combat reconstruction: left hand at shaft middle, right at tail. See README_中文.md.'
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'Herobrine_镰刀动捕_MediaPipe.blend'),compress=True)
report={'fbx':str(path),'size_bytes':path.stat().st_size,'textures':saved,'sample_frames':list(samples),
        'animation':rig.animation_data.action.name,'fps':scene.render.fps,'frame_range':[scene.frame_start,scene.frame_end]}
(WORK/'export_report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('EXPORT_COMPLETE',json.dumps(report,ensure_ascii=True))
