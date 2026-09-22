"""Import the exported FBX into a separate empty Blender and compare geometry."""
import bpy
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap';OUT=ROOT/'output/Herobrine_Scythe_MediaPipe'
expected=json.loads((WORK/'fbx_expected_geometry.json').read_text(encoding='utf-8'))
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.scene.render.fps=30
bpy.ops.import_scene.fbx(filepath=str(OUT/'Herobrine_Scythe_MediaPipe.fbx'),use_anim=True,anim_offset=0.0)
scene=bpy.context.scene
checks=[];maximum=0.
for frame,row in expected.items():
    scene.frame_set(int(frame));bpy.context.view_layer.update();deps=bpy.context.evaluated_depsgraph_get()
    for name,wanted in row.items():
        obj=bpy.data.objects[name];evaluated=obj.evaluated_get(deps);mesh=evaluated.to_mesh()
        pts=[evaluated.matrix_world@v.co for v in mesh.vertices]
        minimum=[min(v[k] for v in pts) for k in range(3)];maximum_xyz=[max(v[k] for v in pts) for k in range(3)]
        error=max(abs(a-b) for actual,target in [(minimum,wanted['bounds_min']),(maximum_xyz,wanted['bounds_max'])] for a,b in zip(actual,target))
        checks.append({'frame':int(frame),'object':name,'vertices':len(pts),'bounds_error':error})
        maximum=max(maximum,error);evaluated.to_mesh_clear()
report={'import_successful':True,'frames_checked':len(expected),'maximum_world_bounds_error':maximum,'import_animation_offset':0.0,
        'objects':[o.name for o in scene.objects],'actions':[a.name for a in bpy.data.actions],
        'checks':checks,'passed':maximum<.005}
(WORK/'fbx_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print('FBX_VALIDATION',json.dumps({k:v for k,v in report.items() if k!='checks'},ensure_ascii=True))
if not report['passed']:raise RuntimeError(f'FBX geometry differs by {maximum}')
