import bpy
import json
from pathlib import Path

out=Path('E:/java/herobrine_companion/output/Herobrine_Scythe_UnityModes_11_Standalone')
report=json.loads((out/'reports/mcp_validation.json').read_text('utf-8'))
target=out/'Unity11_独立玩家动画_顶点验证.blend'
assert Path(bpy.data.filepath).resolve()==target.resolve()
scene=bpy.data.scenes[report['scenes'][2]['scene']]
for window in bpy.context.window_manager.windows:window.scene=scene
for unused in list(bpy.data.scenes):
    if unused.name=='Scene' and len(unused.objects)==0 and unused!=scene:bpy.data.scenes.remove(unused)
bpy.ops.wm.save_as_mainfile(filepath=str(target),compress=True)
report['saved_as_normal_project']=True
report['selected_scene']=scene.name
(out/'reports/mcp_validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n','utf-8')
print('UNITY11_MCP_SAVED',target)
