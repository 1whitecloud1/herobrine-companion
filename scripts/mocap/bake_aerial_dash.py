"""Bake the new sprint action through the connected Blender MCP session."""
import os
import runpy
from pathlib import Path
import bpy

root=Path(__file__).resolve().parents[2]
out=root/'output/Herobrine_Scythe_AerialDash_08'
if bpy.data.is_dirty:
    backup=out/'capture/session_before_aerial_dash.blend'
    if not backup.exists():
        bpy.ops.wm.save_as_mainfile(filepath=str(backup),copy=True,compress=True)
os.environ['HEROBRINE_MOCAP_OUTPUT']=str(out)
runpy.run_path(str(root/'scripts/mocap/bake_grounded.py'),run_name='__main__')
for label,frame in [('蹬地起跳',9),('人镰同步空转 · 第一周',19),('第二周',38),('落地缓冲',69)]:
    bpy.context.scene.timeline_markers.new(label,frame=frame)
bpy.context.scene.frame_set(35)
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath,compress=True)
