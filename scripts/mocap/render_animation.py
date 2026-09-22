"""Render the complete animation without changing the live MCP session."""
import bpy
import sys
import json,hashlib
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
scene=bpy.context.scene
scene.camera=bpy.data.objects['Camera | three quarter']
scene.render.resolution_x=1280;scene.render.resolution_y=720;scene.render.resolution_percentage=100
scene.eevee.taa_render_samples=24
scene.render.image_settings.file_format='PNG';scene.render.image_settings.color_mode='RGB'
scene.render.image_settings.compression=20
folder=WORK/'render_full';folder.mkdir(exist_ok=True)
scene.render.filepath=str(folder/'frame_')
bpy.ops.render.render(animation=True)
(WORK/'render_manifest.json').write_text(json.dumps({'completed':True,'frames':scene.frame_end-scene.frame_start+1,
    'fps':scene.render.fps,'width':scene.render.resolution_x,'height':scene.render.resolution_y,
    'camera':scene.camera.name,
    'blend_sha256':hashlib.sha256(Path(bpy.data.filepath).read_bytes()).hexdigest()},indent=2),encoding='utf-8')
print('ANIMATION_RENDER_COMPLETE',str(folder),flush=True)
