"""Run through Blender MCP after the wide retarget and preserve a dirty session."""
import os
import runpy
from pathlib import Path
import bpy

root = Path(__file__).resolve().parents[2]
output = root/'output/Herobrine_Scythe_Wide_06'
if bpy.data.is_dirty:
    backup = output/'capture/session_before_wide06.blend'
    if not backup.exists():
        bpy.ops.wm.save_as_mainfile(filepath=str(backup), copy=True, compress=True)
os.environ['HEROBRINE_MOCAP_OUTPUT'] = str(output)
runpy.run_path(str(root/'scripts/mocap/bake_grounded.py'), run_name='__main__')
