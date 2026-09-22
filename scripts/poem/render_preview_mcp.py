import bpy
import json
from pathlib import Path

out = Path('E:/java/herobrine_companion/output/Herobrine_Scythe_UnityModes_11_Standalone')
report = json.loads((out/'reports/blender_preview.json').read_text('utf-8'))
assert Path(bpy.data.filepath).resolve() == Path(report['file']).resolve()
for info in report['scenes']:
    scene = bpy.data.scenes[info['scene']]
    scene.frame_set(info['pose_frame'])
    bpy.ops.render.render(write_still=True, scene=scene.name)
    print('UNITY11_MCP_RENDERED', info['label'], scene.render.filepath)
scene = bpy.data.scenes[report['scenes'][2]['scene']]
for window in bpy.context.window_manager.windows:
    window.scene = scene
    for area in window.screen.areas:
        if area.type == 'VIEW_3D':
            area.spaces.active.region_3d.view_perspective = 'CAMERA'
            area.spaces.active.shading.type = 'MATERIAL'
report['rendered_with'] = 'Official Blender MCP, camera renders from the actual Java-exported vanilla vertices'
(out/'reports/mcp_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
