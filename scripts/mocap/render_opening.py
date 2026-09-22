"""Focused review of the user-corrected opening slash."""
import bpy
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];WORK=ROOT/'build/scythe_mocap'
scene=bpy.context.scene
scene.camera=bpy.data.objects['Camera | reference view']
scene.render.resolution_x=1280;scene.render.resolution_y=720;scene.render.resolution_percentage=100
scene.eevee.taa_render_samples=20
scene.frame_start=1;scene.frame_end=60
folder=WORK/'opening_corrected';folder.mkdir(exist_ok=True)
scene.render.filepath=str(folder/'frame_')
bpy.ops.render.render(animation=True)
print('OPENING_RENDER_COMPLETE',flush=True)
