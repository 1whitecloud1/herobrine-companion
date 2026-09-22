"""Verify every body part, especially both legs, against the real Java output."""
import bpy
import json
from pathlib import Path
from mathutils import Matrix

ROOT=Path('E:/java/herobrine_companion')
OUT=ROOT/'output/Herobrine_Scythe_UnityModes_12_LegFix'
data=json.loads((ROOT/'build/poem_legfix/vanilla_mesh_preview.json').read_text('utf-8'))[0]
rig=json.loads((ROOT/'src/main/resources/assets/herobrine_companion/poem_standalone/rig.json').read_text('utf-8'))
report=json.loads((OUT/'reports/blender_preview.json').read_text('utf-8'))
rows=[]
for info in report['scenes']:
    scene=bpy.data.scenes[info['scene']]
    for window in bpy.context.window_manager.windows:window.scene=scene
    weapon=next(o for o in scene.objects if 'calibrated scythe' in o.name)
    schedule=data['meshes'][0]['poses'][info['mode']]['schedule']
    for frame in range(1,info['frames']+1):
        scene.frame_set(frame);bpy.context.view_layer.update()
        key=schedule[frame-1];a=key['tool']
        expected=Matrix([[a[c*4+r] for c in range(4)] for r in range(4)])@Matrix(rig['weapon_to_tool'])
        weapon_error=max(abs(expected[r][c]-weapon.matrix_world[r][c]) for r in range(4) for c in range(4))
        errors={}
        for part in data['meshes']:
            body=next(o for o in scene.objects if part['part'] in o.name)
            evaluated=body.evaluated_get(bpy.context.evaluated_depsgraph_get())
            mesh=evaluated.to_mesh();wanted=part['poses'][info['mode']]['frames'][frame-1]
            assert len(mesh.vertices)==len(wanted)
            errors[part['part']]=max(abs(v.co[k]-(-p[0],-p[2],1.5-p[1])[k]) for v,p in zip(mesh.vertices,wanted) for k in range(3))
            evaluated.to_mesh_clear()
        assert weapon_error<.00001,(info['mode'],frame,'weapon',weapon_error)
        # Blender evaluates the two float eval_time keys across the whole
        # sequence; its sub-frame roundoff is below one tenth of a millimetre.
        assert max(errors.values())<.0001,(info['mode'],frame,'body',errors)
        rows.append(dict(mode=info['mode'],frame=frame,weapon_error=weapon_error,body_error=max(errors.values()),parts=errors))
(OUT/'reports/preview_motion_validation.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n','utf-8')
report.update(rendered_with='Official Blender MCP; actual Java-exported vanilla vertices',all_body_parts_checked=True,
              checked_preview_frames=len(rows),saved_as_normal_project=True,live_gameplay_tested=False)
(OUT/'reports/mcp_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
print('UNITY12_MCP_ALL_VERTICES_OK',len(rows),'frames',max(r['body_error'] for r in rows),max(r['weapon_error'] for r in rows))
