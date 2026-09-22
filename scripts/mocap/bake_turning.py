"""Bake the turning edit via the connected Blender MCP authoring process."""
import os
import runpy
from pathlib import Path
import bpy

root=Path(__file__).resolve().parents[2]
output=root/'output/Herobrine_Scythe_Turn_07'
if bpy.data.is_dirty:
    backup=output/'capture/session_before_turn07.blend'
    if not backup.exists():
        bpy.ops.wm.save_as_mainfile(filepath=str(backup),copy=True,compress=True)
os.environ['HEROBRINE_MOCAP_OUTPUT']=str(output)
runpy.run_path(str(root/'scripts/mocap/bake_grounded.py'),run_name='__main__')
bpy.context.scene.frame_set(95)
bpy.ops.wm.save_as_mainfile(filepath=bpy.data.filepath,compress=True)
