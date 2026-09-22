import bpy, json, numpy as np
from pathlib import Path
p=Path(bpy.data.filepath).parent
m=json.loads((p/'target_motion.json').read_text(encoding='utf-8'))
rig=bpy.data.objects['Bones:Character']
out={'action':rig.animation_data.action.name,'range':list(rig.animation_data.action.frame_range),'fps':bpy.context.scene.render.fps/bpy.context.scene.render.fps_base,'objects':[(o.name,o.type,list(o.scale)) for o in bpy.data.objects if o.type in ('ARMATURE','MESH')]}
errors=[]
for i in (0,200,1000,2000,3360):
 f=m['frames'][i]; bpy.context.scene.frame_set(int(f),subframe=f%1)
 r=rig.evaluated_get(bpy.context.evaluated_depsgraph_get())
 errors.append(max(float(np.abs(np.array(r.pose.bones[n].matrix)-v).max()) for n,v in m['poses'][i].items()))
out['cached_pose_errors']=errors
out['support_examples']=[(i,x) for i,x in enumerate(m['support_hand']) if x['weight']>.99][:2]
mesh=bpy.data.objects['Poem of the End | 原版镰刀'].data
dest=Path(__file__).resolve().parents[2]/'build/epicfight-v6'
dest.mkdir(parents=True,exist_ok=True)
(dest/'weapon_mesh.json').write_text(json.dumps({'vertices':[list(v.co) for v in mesh.vertices],'faces':[list(f.vertices) for f in mesh.polygons],'uv':[list(u.uv) for u in mesh.uv_layers.active.data]}),encoding='utf-8')
(dest/'probe.json').write_text(json.dumps(out,ensure_ascii=False,indent=2),encoding='utf-8')
print('V6_PROBE',json.dumps(out,ensure_ascii=True))
