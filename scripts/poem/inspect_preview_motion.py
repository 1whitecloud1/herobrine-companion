import bpy,json
from pathlib import Path
from mathutils import Matrix
root=Path('E:/java/herobrine_companion')
out=root/'output/Herobrine_Scythe_UnityModes_11_Standalone'
data=json.loads((root/'build/poem_standalone/vanilla_mesh_preview.json').read_text('utf-8'))
rig=json.loads((root/'src/main/resources/assets/herobrine_companion/poem_standalone/rig.json').read_text('utf-8'))
report=json.loads((out/'reports/blender_preview.json').read_text('utf-8'))
rows=[]
for info in report['scenes']:
    scene=bpy.data.scenes[info['scene']]
    for window in bpy.context.window_manager.windows:window.scene=scene
    weapon=next(o for o in scene.objects if o.name.endswith('calibrated scythe'))
    part=data[0]['meshes'][0];body=next(o for o in scene.objects if o.name.endswith(part['part']))
    maximum=0;body_error=0
    schedule=part['poses'][info['mode']]['schedule']
    for frame in [1,info['pose_frame'],min(73,info['frames']),info['frames']]:
        scene.frame_set(frame);bpy.context.view_layer.update()
        key=schedule[frame-1];a=key['tool'];expected=Matrix([[a[c*4+r] for c in range(4)] for r in range(4)])@Matrix(rig['weapon_to_tool'])
        actual=weapon.matrix_world
        error=max(abs(expected[r][c]-actual[r][c]) for r in range(4) for c in range(4));maximum=max(maximum,error)
        mesh=body.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh()
        expected_verts=part['poses'][info['mode']]['frames'][frame-1]
        err=max(abs(v.co[k]-(-p[0],-p[2],1.5-p[1])[k]) for v,p in zip(mesh.vertices,expected_verts) for k in range(3))
        body_error=max(body_error,err)
        body.evaluated_get(bpy.context.evaluated_depsgraph_get()).to_mesh_clear()
        rows.append({'mode':info['mode'],'frame':frame,'step':key['step'],'clip_time':key['time'],'weapon_error':error,'body_error':err,'weapon_origin':list(expected.translation)})
(out/'reports/preview_motion_validation.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n','utf-8')
print(json.dumps(rows,ensure_ascii=False))
