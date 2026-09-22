"""Execute in the dedicated Blender MCP session, leaving the old project intact."""
import os
import runpy
from pathlib import Path
root=Path(__file__).resolve().parents[2]
os.environ['HEROBRINE_MOCAP_OUTPUT']=str(root/'output/Herobrine_Scythe_Recapture_05')
runpy.run_path(str(root/'scripts/mocap/bake_grounded.py'),run_name='__main__')
