"""Start the installed Blender MCP addon in a separate authoring process."""
import bpy
import importlib
import json
import os
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'build/scythe_mocap'
OUT = ROOT / 'output/Herobrine_Scythe_MediaPipe'
OUT.mkdir(parents=True, exist_ok=True)
source = bpy.data.filepath
sys.path.insert(0, str(Path(os.environ['APPDATA']) / 'Blender Foundation/Blender/5.0/scripts/addons'))
addon = importlib.import_module('addon')
if not hasattr(bpy.types.Scene, 'blendermcp_port'):
    addon.register()
previous = getattr(bpy.types, 'blendermcp_server', None)
if previous is not None:
    previous.stop()
bpy.types.blendermcp_server = addon.BlenderMCPServer(host='127.0.0.1', port=9877)
bpy.types.blendermcp_server.start()
bpy.context.scene.blendermcp_port = 9877
bpy.context.scene.blendermcp_server_running = bpy.types.blendermcp_server.running
bpy.context.scene.blendermcp_use_polyhaven = False
bpy.context.scene.blendermcp_use_sketchfab = False
session = {'pid': os.getpid(), 'host': '127.0.0.1', 'port': 9877,
           'source_file': source, 'running': bpy.types.blendermcp_server.running}
(WORK / 'mcp_session.json').write_text(json.dumps(session, ensure_ascii=False, indent=2), encoding='utf-8')
print('MCP_READY', json.dumps(session, ensure_ascii=True), flush=True)
